# Ticket Management System — Sequence Diagrams

> The **interaction** view: who calls whom, in what order, for each significant flow.
> Eight flows, from raising an incident through to the races and failure modes the
> current code does *not* yet handle.

---

## Flow 1 — Raise an Incident (Happy Path)

Creating a ticket. Note that the reporter subscribes to their own ticket inside the
constructor — nobody has to remember to do it.

```mermaid
sequenceDiagram
    actor Charlie as Charlie (REPORTER)
    participant Demo as ServiceNowSystem
    participant M as IncidentManager
    participant C as AtomicInteger<br/>ticketCounter
    participant T as Ticket
    participant NS as NewState
    participant DB as ticketDb

    Charlie->>Demo: "Database is down", P1_CRITICAL
    Demo->>M: createIncident(title, P1_CRITICAL, charlie)
    activate M

    M->>C: getAndIncrement()
    C-->>M: 1
    M->>M: id = "INC-" + String.format("%04d", 1)
    Note over M: → "INC-0001" — zero-padded so ids<br/>sort and align in a fixed-width column

    M->>T: new Ticket("INC-0001", title, P1_CRITICAL, charlie)
    activate T
    T->>NS: new NewState()
    NS-->>T: state object
    T->>T: currentState = newState
    T->>T: addObserver(reporter)
    Note over T: the reporter watches their own<br/>ticket automatically
    T-->>M: ticket
    deactivate T

    M->>DB: put("INC-0001", ticket)
    M->>T: getCurrentState()
    T->>NS: getStateName()
    NS-->>T: "NEW"
    T-->>M: "NEW"
    M->>M: print "Created INC-0001 | Status: NEW"
    M-->>Demo: ticket
    deactivate M
    Demo-->>Charlie: INC-0001 raised
```

**Why no notification fires here.** The constructor registers the reporter *after*
setting `currentState` directly (not via `setState`), so ticket creation is silent.
That is deliberate: there is no transition to announce, and the return value already
tells the caller the ticket exists.

---

## Flow 2 — Auto-Assign via the Routing Strategy

The Strategy pattern in one picture: the manager knows *that* an agent is chosen,
never *how*.

```mermaid
sequenceDiagram
    participant Demo as ServiceNowSystem
    participant M as IncidentManager
    participant DB as ticketDb
    participant Strat as RoutingStrategy<br/>(RoundRobinRouting)
    participant Pool as agentDb
    participant T as Ticket
    participant NS as NewState
    participant AS as AssignedState
    participant Obs as Observers<br/>(Charlie)

    Demo->>M: autoAssign("INC-0001")
    activate M
    M->>DB: get("INC-0001")
    DB-->>M: ticket

    alt ticket == null
        Note over M: ⚠️ silently does nothing —<br/>should throw TicketNotFoundException
    else ticket found
        M->>Strat: findAgent(agentDb)
        activate Strat
        Strat->>Pool: isEmpty()?
        Pool-->>Strat: false
        Strat->>Strat: curr = index.getAndUpdate(i -> i + 1) % size
        Note over Strat: ⚠️ index starts at 1, so the<br/>FIRST ticket routes to agents[1] = Bob
        Strat->>Pool: get(curr)
        Pool-->>Strat: Bob
        Strat-->>M: Bob
        deactivate Strat

        M->>T: assignTo(Bob)
        activate T
        T->>NS: assign(ticket, Bob)
        activate NS
        NS->>T: setAssignee(Bob)
        NS->>T: setState(new AssignedState())
        activate T
        T->>NS: getStateName() (old)
        NS-->>T: "NEW"
        T->>T: currentState = assignedState
        T->>AS: getStateName() (new)
        AS-->>T: "ASSIGNED"
        T->>Obs: onUpdate(ticket, "moved from NEW to ASSIGNED")
        Obs-->>T: printed
        deactivate T
        NS->>T: notifyObservers("Assigned to agent: Bob")
        T->>Obs: onUpdate(ticket, "Assigned to agent: Bob")
        Obs-->>T: printed
        Note over T,Obs: ⚠️ TWO notifications per transition —<br/>one from setState, one explicit
        deactivate NS
        deactivate T
    end
    deactivate M
```

---

## Flow 3 — Start Work, Then Resolve

The two remaining forward transitions, back to back.

```mermaid
sequenceDiagram
    actor Bob as Bob (AGENT)
    participant T as Ticket
    participant AS as AssignedState
    participant IPS as InProgressState
    participant RS as ResolvedState
    participant Obs as Observers

    Bob->>T: markInProgress()
    activate T
    T->>AS: startProgress(ticket)
    activate AS
    AS->>IPS: new InProgressState()
    AS->>T: setState(inProgress)
    T->>Obs: "moved from ASSIGNED to IN_PROGRESS"
    AS->>T: notifyObservers("Work has started.")
    T->>Obs: "Work has started."
    deactivate AS
    deactivate T

    Note over Bob,Obs: ── investigation happens ──

    Bob->>T: resolveTicket()
    activate T
    T->>IPS: resolve(ticket)
    activate IPS
    IPS->>RS: new ResolvedState()
    IPS->>T: setState(resolved)
    T->>Obs: "moved from IN_PROGRESS to RESOLVED"
    IPS->>T: notifyObservers("Ticket has been RESOLVED.")
    T->>Obs: "Ticket has been RESOLVED."
    deactivate IPS
    deactivate T
```

**The delegation is total.** `Ticket.markInProgress()` is one line —
`currentState.startProgress(this)`. It contains no `if`, no `switch`, and no knowledge
of which states permit the call. Adding `ON_HOLD` would not change this method.

---

## Flow 4 — Illegal Transition Rejected

What the demo exercises last: calling `markInProgress()` on a resolved ticket.

```mermaid
sequenceDiagram
    participant Demo as ServiceNowSystem
    participant T as Ticket
    participant RS as ResolvedState
    participant Obs as Observers

    Demo->>T: markInProgress()
    activate T
    T->>RS: startProgress(ticket)
    activate RS
    RS--xT: throw IllegalStateException<br/>("Ticket resolved.")
    deactivate RS
    Note over T,Obs: state unchanged, NO observer fired —<br/>the exception aborts before setState
    T--xDemo: IllegalStateException
    deactivate T
    Demo->>Demo: catch (IllegalStateException e)
    Demo->>Demo: print "Caught Expected Error: Ticket resolved."
```

**The full rejection matrix** — every throw in `State/`:

| Current state | `assignTo` | `markInProgress` | `resolveTicket` |
|---|---|---|---|
| `NewState` | ✅ → `ASSIGNED` | ❌ *"Cannot start progress on unassigned ticket."* | ❌ *"Cannot resolve ticket directly from NEW."* |
| `AssignedState` | ✅ reassign, stays `ASSIGNED` | ✅ → `IN_PROGRESS` | ❌ *"Cannot resolve ticket directly from NEW."* ⚠️ **wrong message — copy-paste from `NewState`** |
| `InProgressState` | ❌ *"Cannot reassign while in progress."* | ⚪ no-op, prints *"Already in progress."* | ✅ → `RESOLVED` |
| `ResolvedState` | ❌ *"Ticket resolved."* | ❌ *"Ticket resolved."* | ⚪ no-op, prints *"Already resolved."* |

✅ transition · ❌ `IllegalStateException` · ⚪ idempotent no-op

The two ⚪ cells are a deliberate design choice, not an oversight: the caller's intent
("make it in progress", "make it resolved") is already satisfied, so throwing would
punish a harmless retry.

---

## Flow 5 — Reassignment Inside `ASSIGNED`

Reassignment is the one operation that fires an observer without changing state.

```mermaid
sequenceDiagram
    actor Lead as Team Lead
    participant T as Ticket
    participant AS as AssignedState
    participant Obs as Observers

    Lead->>T: assignTo(Alice)
    activate T
    T->>AS: assign(ticket, Alice)
    activate AS
    AS->>T: setAssignee(Alice)
    Note over AS: NO setState call —<br/>the ticket is already ASSIGNED
    AS->>T: notifyObservers("Re-assigned to agent: Alice")
    T->>Obs: onUpdate(...)
    deactivate AS
    deactivate T
    Note over T,Obs: exactly ONE notification here,<br/>because setState was never called
```

Contrast with Flow 2, where the same `assignTo` call produced *two* notifications
because `NewState.assign` does call `setState`. The inconsistency is the symptom of
notifying from two places; centralising it in `setState` fixes both.

---

## Flow 6 — The Concurrency Race on a Single Ticket

Two agents acting on the same ticket at the same instant. `Ticket`'s methods are not
synchronized, so the check (which state am I in?) and the act (replace the state) can
interleave.

### ❌ What can happen today

```mermaid
sequenceDiagram
    participant T1 as Thread 1 (Bob)
    participant T as Ticket<br/>currentState = AssignedState
    participant T2 as Thread 2 (Alice)

    T1->>T: markInProgress()
    T1->>T: read currentState → AssignedState
    T2->>T: markInProgress()
    T2->>T: read currentState → AssignedState
    Note over T1,T2: 💥 both read the SAME state object

    T1->>T: AssignedState.startProgress(ticket)
    T1->>T: setState(new InProgressState A)
    T1->>T: notify "moved from ASSIGNED to IN_PROGRESS"

    T2->>T: AssignedState.startProgress(ticket)
    T2->>T: setState(new InProgressState B)
    T2->>T: notify "moved from ASSIGNED to IN_PROGRESS"

    Note over T,T2: ⚠️ the transition fired TWICE.<br/>Watchers see it twice, an SLA clock<br/>started twice, an audit log has two rows<br/>for one real event.
```

The damage here is duplicated side effects rather than a corrupted state — both threads
happen to install an equivalent `InProgressState`. Swap one of them for
`resolveTicket()` and it gets worse: a ticket can land in `RESOLVED` while a second
thread is still mid-`startProgress`, and the losing transition's notification describes
a move that no longer reflects reality.

### ✅ The fix

```mermaid
sequenceDiagram
    participant T1 as Thread 1 (Bob)
    participant T as Ticket<br/>synchronized methods
    participant T2 as Thread 2 (Alice)

    T1->>T: markInProgress()
    activate T
    Note over T: 🔒 monitor acquired
    T->>T: read currentState → AssignedState
    T->>T: startProgress → setState(InProgressState)
    T->>T: notify watchers
    Note over T: 🔓 released
    deactivate T
    T-->>T1: ok

    T2->>T: markInProgress()
    activate T
    Note over T: 🔒 monitor acquired
    T->>T: read currentState → InProgressState
    T->>T: InProgressState.startProgress → prints "Already in progress."
    Note over T: 🔓 released
    deactivate T
    T-->>T2: ok — correctly a no-op
```

```java
// Ticket — make check-then-act atomic
public synchronized void assignTo(User agent)  { currentState.assign(this, agent); }
public synchronized void markInProgress()      { currentState.startProgress(this); }
public synchronized void resolveTicket()       { currentState.resolve(this); }
```

Locking the *ticket* rather than the *manager* is the right granularity: two threads
working on two different tickets never contend.

---

## Flow 7 — Concurrent Incident Creation (This One Is Safe)

By contrast, id generation is already correct, and the diagram shows why.

```mermaid
sequenceDiagram
    participant T1 as Thread 1
    participant C as AtomicInteger<br/>ticketCounter
    participant T2 as Thread 2
    participant DB as ConcurrentHashMap<br/>ticketDb

    par both create at once
        T1->>C: getAndIncrement()
        and
        T2->>C: getAndIncrement()
    end
    Note over C: CAS guarantees the two calls<br/>return DIFFERENT values
    C-->>T1: 1
    C-->>T2: 2
    T1->>T1: id = "INC-0001"
    T2->>T2: id = "INC-0002"
    T1->>DB: put("INC-0001", ticket)
    T2->>DB: put("INC-0002", ticket)
    Note over DB: distinct keys, and the map is<br/>concurrent — no lost write
```

⚠️ **But only within one manager.** Because `getInstance` returns a *new*
`IncidentManager` every call ([01 § 5](01-class-diagram.md#5-the-singleton-that-isnt)),
two callers that each fetched "the" manager have two counters, and both mint `INC-0001`.
The atomicity is real; the uniqueness guarantee is not.

---

## Flow 8 — Observer Failure Is Not Isolated

A watcher that throws takes the rest of the notification loop down with it — *after*
the state has already changed.

```mermaid
sequenceDiagram
    participant T as Ticket
    participant O1 as Observer 1<br/>(Charlie — ok)
    participant O2 as Observer 2<br/>(SlackNotifier — down)
    participant O3 as Observer 3<br/>(AuditLog)

    T->>T: setState(new ResolvedState())
    Note over T: ✅ state ALREADY changed —<br/>this is committed and irreversible

    T->>T: notifyObservers("moved to RESOLVED")
    activate T
    T->>O1: onUpdate(...)
    O1-->>T: ok
    T->>O2: onUpdate(...)
    O2--xT: 💥 RuntimeException (Slack timeout)
    Note over T,O3: loop aborts — O3 is NEVER called
    T--xT: exception propagates to the caller
    deactivate T

    Note over T,O3: ⚠️ The ticket IS resolved, but the caller sees<br/>a failure and the audit log never heard about it.<br/>State and notifications are now inconsistent.
```

```java
// Ticket.notifyObservers — contain each observer's failure
@Override
public void notifyObservers(String message) {
    for (Observer o : observers) {          // CopyOnWriteArrayList
        try {
            o.onUpdate(this, message);
        } catch (RuntimeException e) {
            System.err.println("Observer failed for " + id + ": " + e.getMessage());
        }
    }
}
```

**The principle.** Notification is a side effect that happens *after* the transition
commits. It can never fail the transition, because the transition is already done —
so the only honest options are to contain the failure or to move notification out of
the transaction entirely (an outbox row, drained by a separate worker).
