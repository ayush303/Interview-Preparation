# Designing a Ticket Management System (ServiceNow-style Incident Manager)

## Problem Statement

Design an incident/ticket management system of the kind an IT service desk runs on —
the ServiceNow model.

An employee (the **reporter**) raises an incident describing something that is broken:
"Database is down", "VPN keeps dropping". The incident is filed with a severity
(**priority**) and enters the system in a `NEW` state, owned by nobody. The service desk
then routes it to an **agent** from the on-call pool according to a configurable policy,
the agent picks it up and starts work, and eventually resolves it.

Two things make this more than a CRUD form.

First, **a ticket's behaviour depends on the state it is in, not on a chain of `if`
checks**. You cannot start work on a ticket nobody owns. You cannot resolve a ticket that
was never worked on. You cannot reassign a ticket while an agent is mid-investigation,
and you cannot touch a ticket at all once it is resolved. Each of these rules belongs to
one particular state, and the set of states must be extensible — a real service desk will
want `ON_HOLD`, `REOPENED`, `CLOSED` later — without reopening and re-editing a growing
conditional in the `Ticket` class every time.

Second, **everyone watching a ticket must learn about every change to it**, without the
ticket knowing who those watchers are. The reporter who filed it, the assigned agent, an
SLA timer, a Slack channel, an audit log — all of them care that the ticket moved from
`ASSIGNED` to `IN_PROGRESS`, and none of them should require a change to the ticket's
transition logic to be added.

Finally, **how a ticket is routed is a business policy, not a law of physics**. Today it
is round-robin across all available agents; tomorrow it is least-loaded, or
skill-based, or "P1 goes to the on-call senior". Swapping that policy must not mean
editing the manager that uses it.

---

## Functional Requirements

1. **User registry and roles** — The system models users with a unique id, a name, and a
   `Role`: `REPORTER` (raises incidents) or `AGENT` (works them). Agents are registered
   with the incident manager to form the routable pool.

2. **Raise an incident** — A reporter can create an incident with a title and a
   `Priority` (`P1_CRITICAL`, `P2_HIGH`, `P3_LOW`). The system assigns a
   system-generated, zero-padded, human-readable id of the form `INC-0001`, files the
   ticket in `NEW`, and returns it.

3. **Reporter auto-subscribes** — The reporter of a ticket is automatically registered as
   a watcher of that ticket at creation time; they never have to ask to be kept informed.

4. **Configurable routing** — `autoAssign(ticketId)` picks an agent from the registered
   pool using a pluggable `RoutingStrategy` and assigns the ticket to them.
   `RoundRobinRouting` distributes work evenly by cycling through the pool. The policy
   is injected into the manager, so a new policy (least-loaded, skill-based,
   priority-aware) is a new class and nothing more.

5. **Routing on an empty pool fails loudly** — If no agents are registered, routing
   raises an error rather than returning `null` and deferring the failure.

6. **Ticket lifecycle** — A ticket moves `NEW → ASSIGNED → IN_PROGRESS → RESOLVED`.
   The three operations exposed on a ticket are `assignTo(agent)`, `markInProgress()`
   and `resolveTicket()`, and each one's meaning is decided by the state the ticket is
   currently in:

   | From | `assignTo` | `markInProgress` | `resolveTicket` |
   |---|---|---|---|
   | `NEW` | → `ASSIGNED` | ❌ rejected — nothing is assigned yet | ❌ rejected — never worked |
   | `ASSIGNED` | reassign, stay `ASSIGNED` | → `IN_PROGRESS` | ❌ rejected |
   | `IN_PROGRESS` | ❌ rejected — no reassignment mid-flight | no-op, already in progress | → `RESOLVED` |
   | `RESOLVED` | ❌ rejected — terminal | ❌ rejected — terminal | no-op, already resolved |

7. **Reassignment** — A ticket in `ASSIGNED` may be handed to a different agent. This is
   a reassignment, not a new transition: the ticket stays in `ASSIGNED` and watchers are
   told who now owns it.

8. **Illegal transitions are rejected, not silently ignored** — Every forbidden operation
   in the table above raises `IllegalStateException` with a message naming the reason.
   The two "already there" cases (`markInProgress` while in progress, `resolveTicket`
   while resolved) are idempotent no-ops rather than errors, since the caller's intent
   is already satisfied.

9. **Watcher notification** — Every state change and every reassignment pushes a message
   to all watchers of that ticket. A watcher is anything implementing the observer
   contract; `User` implements it and prints to its own channel. New channels (email,
   Slack, SLA timer, audit log) are added by implementing the same interface.

10. **Ticket lookup** — Tickets can be fetched by id from the manager.

11. **Single point of control** — The incident manager is the one object that owns the
    ticket store, the agent pool and the routing policy, reached through a static
    accessor rather than being constructed ad hoc across the codebase.

---

## Non-Functional Requirements

1. **Open/Closed on both axes** — Adding a routing policy must mean adding a class that
   implements `RoutingStrategy`, and adding a lifecycle state must mean adding a class
   that implements `TicketState`. Neither may require editing `IncidentManager` or
   `Ticket`. This is the requirement that rules out a `switch (status)` inside `Ticket`.

2. **No conditional lifecycle logic in the domain object** — `Ticket` must not know which
   transitions are legal. It delegates each operation to its current state object and
   exposes a setter the state uses to move it on. The rule lives next to the state it
   constrains.

3. **Concurrency** — Many agents and reporters act on the system at once. The ticket store
   is a `ConcurrentHashMap`, the agent pool a synchronized list, and id generation uses an
   `AtomicInteger` so two concurrent `createIncident` calls can never receive the same
   `INC-` number. Round-robin advances through an `AtomicInteger` so two concurrent
   assignments cannot land on the same cursor value.

4. **Safe lazy initialization** — The manager is obtained through a static accessor using
   double-checked locking, so the cost of creating it is paid once and no caller can
   observe a half-built object. *(See the ⚠️ note below — the shipped code does not yet
   meet this requirement.)*

5. **Fail fast with specific errors** — Illegal transitions and an empty agent pool raise
   exceptions carrying the reason, rather than returning `null` or quietly doing nothing.

6. **Readable identifiers** — Ticket ids are zero-padded (`INC-0001`) so they sort
   lexicographically, fit a fixed-width column, and can be read aloud on a bridge call.

7. **Notification is a side effect, never part of the transition** — A watcher that fails
   must not roll back a state change that has already happened.
   *(See ⚠️ — not yet enforced in code.)*

8. **Separation of concerns** — Domain models, lifecycle rules, routing policy,
   notification contracts and orchestration live in separate packages
   (`models`, `State`, `Strategy`, `Observer`, and the manager at the root), so each can
   be read, tested and replaced on its own.

---

## Design Patterns Used

- **State** — `TicketState` with `NewState`, `AssignedState`, `InProgressState`,
  `ResolvedState`. Each state owns the rules for what may happen to a ticket while it is
  in that state, and performs the transition itself. `Ticket` holds a reference to its
  current state and delegates every lifecycle call to it.
- **Strategy** — `RoutingStrategy` with `RoundRobinRouting`. Makes agent selection an
  interchangeable policy injected into `IncidentManager`.
- **Observer** — `Subject` / `Observer`, with `Ticket` as the subject and `User` as a
  concrete observer. Decouples a ticket's transitions from whoever needs to hear about
  them.
- **Singleton** *(intended)* — `IncidentManager.getInstance(...)` as the one point of
  control over tickets, agents and policy.

---

## ⚠️ Known Gaps in the Current Implementation

The diagrams document the code **as it is today**, and these are marked ⚠️ wherever they
appear. They are the most likely follow-up questions in an interview, so they are listed
here rather than hidden:

| # | Gap | Where | Effect |
|---|---|---|---|
| 1 | `private static final IncidentManager instance = null;` — a `final` field pinned to `null`, so the DCL check always passes and `getInstance` returns `new IncidentManager(...)` on **every** call | `IncidentManager` | It is not a singleton. Two callers get two managers with two separate ticket stores. Fix: drop `final`, make it `volatile`, and **assign** `instance = new IncidentManager(...)` inside the lock. |
| 2 | Public constructor alongside `getInstance` | `IncidentManager` | Nothing forces callers through the accessor. Fix: make the constructor `private`. |
| 3 | `RoundRobinRouting.index` starts at **1**, not 0 | `RoundRobinRouting` | The first ticket routes to the *second* agent. Also, the counter grows without bound and `%` on a wrapped negative `int` eventually returns a negative index. Fix: start at 0 and use `Math.floorMod`, or reset the cursor modulo the pool size. |
| 4 | Every transition fires **two** notifications — one from `setState` ("moved from X to Y") and one explicit `notifyObservers(...)` in the state | `State/*` | Watchers receive duplicate-ish messages per transition. Fix: notify once, from `setState`, with the reason passed in. |
| 5 | `AssignedState.resolve` throws the message *"Cannot resolve ticket directly from NEW."* | `AssignedState` | Copy-paste; the message names the wrong state and misleads whoever reads the log. |
| 6 | `Priority` is stored but never read | `IncidentManager`, `RoutingStrategy` | A `P1_CRITICAL` outage routes exactly like a `P3_LOW` password reset. The natural next strategy is a priority-aware one. |
| 7 | `Ticket` state transitions are not atomic, and `observers` is a plain `ArrayList` | `Ticket` | Two threads transitioning the same ticket can interleave check-and-set; concurrent `addObserver` can corrupt the list. Fix: `synchronized` transition methods (or a per-ticket lock) and a `CopyOnWriteArrayList`. |
| 8 | Observer calls are not wrapped | `Ticket.notifyObservers` | One watcher throwing aborts the notification loop *after* the state has already changed, so later watchers never hear about a transition that did happen. Fix: per-observer `try/catch`. |
| 9 | `autoAssign` on an unknown id does nothing | `IncidentManager` | Silent no-op instead of a fail-fast error. |
| 10 | `Priority.P3_LO` | `enums/Priority` | Truncated name; should be `P3_LOW`. |
| 11 | No `removeObserver`, no `getAllTickets`/filtering, no `CLOSED`/`REOPENED`/`ON_HOLD` states | throughout | Natural extension points, each one a new class under the existing interfaces. |

---

## Implementation

#### [Java Implementation](../solution/)

## Diagrams

#### [Design Diagrams](../classDiagrams/)
