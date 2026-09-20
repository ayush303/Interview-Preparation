# Ticket Management System — Entity Relationship Diagram

> The **data** view: what is stored, with what attributes, and how the records relate.
> Where the class diagram shows behaviour, this shows shape — it is the diagram you
> would hand to someone asked to persist this system in a database.

---

## 1. Core ER Diagram

```mermaid
erDiagram
    USER {
        string id PK "e.g. A1, U1"
        string name "Display name"
        enum role FK "REPORTER | AGENT"
    }

    ROLE {
        string name PK "REPORTER | AGENT"
        string description "Raises incidents | Works incidents"
    }

    TICKET {
        string id PK "System generated, INC-0001"
        string title "What is broken"
        enum priority FK "P1_CRITICAL | P2_HIGH | P3_LOW"
        string reporterId FK "Who raised it — never null"
        string assigneeId FK "Agent who owns it — null until ASSIGNED"
        string currentState FK "NEW | ASSIGNED | IN_PROGRESS | RESOLVED"
    }

    PRIORITY {
        string name PK "P1_CRITICAL | P2_HIGH | P3_LOW"
        int severityRank "1 | 2 | 3 — lower is more urgent"
    }

    TICKET_STATE {
        string name PK "NEW | ASSIGNED | IN_PROGRESS | RESOLVED"
        boolean requiresAssignee "false | true | true | true"
        boolean isTerminal "true only for RESOLVED"
    }

    WATCH {
        string ticketId FK "Part of composite key"
        string userId FK "Part of composite key"
        string subscribedVia "AUTO (reporter) | MANUAL (addObserver)"
    }

    NOTIFICATION {
        string ticketId FK "Which ticket changed"
        string recipientId FK "Which watcher was told"
        string message "Human-readable change description"
        datetime sentAt "When the observer fired"
    }

    TRANSITION_LOG {
        string ticketId FK "Which ticket"
        string fromState "State before"
        string toState "State after"
        string actorId FK "Who triggered it"
        datetime occurredAt "When"
    }

    USER   ||--o{ TICKET          : "reports"
    USER   ||--o{ TICKET          : "is assigned (0..1 per ticket)"
    USER   }o--|| ROLE            : "has"
    TICKET }o--|| PRIORITY        : "is filed at"
    TICKET }o--|| TICKET_STATE    : "is currently in"
    TICKET ||--o{ WATCH           : "is watched via"
    USER   ||--o{ WATCH           : "watches via"
    TICKET ||--o{ NOTIFICATION    : "emits"
    USER   ||--o{ NOTIFICATION    : "receives"
    TICKET ||--o{ TRANSITION_LOG  : "accumulates"
```

**Reading the crow's feet**

| Relationship | Cardinality | Meaning |
|---|---|---|
| `USER \|\|--o{ TICKET` (reports) | one-to-many | One user raises zero or more tickets; each ticket has **exactly one** reporter, set at construction and never changed |
| `USER \|\|--o{ TICKET` (assigned) | one-to-many, optional | One agent owns many tickets; a ticket has **zero or one** assignee — `null` while `NEW` |
| `TICKET }o--\|\| TICKET_STATE` | many-to-one | The state is a lookup value; in code it is a *polymorphic object*, not a column — see § 3 |
| `TICKET \|\|--o{ WATCH }o--\|\| USER` | many-to-many | Resolved through a junction table: a ticket has many watchers, a user watches many tickets |
| `TICKET \|\|--o{ NOTIFICATION` | one-to-many | Every transition × every watcher produces one row |

**Rows that exist in the model but not in the code.** `NOTIFICATION` and
`TRANSITION_LOG` are shown because any real service desk persists them — an SLA clock
and an audit trail are the first two features asked for after the happy path works.
Today they are `System.out.println` calls inside `User.onUpdate` and are never stored.
`WATCH.subscribedVia` distinguishes the reporter, auto-subscribed in the `Ticket`
constructor, from anyone added later through `addObserver`.

---

## 2. In-Memory Storage Map

What actually holds state at runtime, and in which object.

```mermaid
flowchart TB
    subgraph mgr["IncidentManager — the store ⚠️ one per getInstance call"]
        direction TB
        TDB["ticketDb<br/><b>ConcurrentHashMap&lt;String, Ticket&gt;</b><br/>INC-0001 → Ticket"]
        ADB["agentDb<br/><b>synchronizedList&lt;User&gt;</b><br/>the routable pool"]
        CNT["ticketCounter<br/><b>AtomicInteger</b> = 1<br/>next INC- number"]
        STRAT["routingStrategy<br/><b>RoutingStrategy</b><br/>the active policy"]
    end

    subgraph tkt["Ticket — per-ticket state"]
        direction TB
        ID["id : String — INC-0001"]
        TITLE["title : String"]
        PRI["priority : Priority"]
        REP["reporter : User — immutable"]
        ASG["assignee : User — null until assigned"]
        CS["currentState : TicketState<br/>a live object, not an enum"]
        OBS["observers<br/><b>ArrayList&lt;Observer&gt;</b> ⚠️ not thread-safe"]
    end

    subgraph rr["RoundRobinRouting — policy state"]
        IDX["index : <b>AtomicInteger</b> = 1 ⚠️<br/>the rotation cursor"]
    end

    TDB -->|"values are"| tkt
    ADB -->|"passed to"| rr
    STRAT -.->|"is a"| rr
    CNT -->|"formats INC-%04d into"| ID
    REP -->|"auto-added to"| OBS
    ADB -->|"chosen agent becomes"| ASG
```

### The key invariant

> **A ticket's state is an object, not a value.** `currentState` holds a live
> `NewState` / `AssignedState` / `InProgressState` / `ResolvedState` instance. Reading it
> means calling `getStateName()`. Changing it means *replacing the object*. This is the
> single structural difference between this model and a `status` column, and everything
> in [04](04-state-diagrams.md) follows from it.

Three consequences worth stating out loud:

1. **Persisting is asymmetric.** Writing a ticket to a database stores
   `getCurrentState()` — the `String` name. Reading it back requires a factory that maps
   `"IN_PROGRESS"` → `new InProgressState()`. The object graph does not round-trip on
   its own.
2. **State objects are stateless.** None of the four hold a field, so they *could* be
   shared singletons rather than allocated per transition. The code allocates a new one
   each time (`new AssignedState()`), which is harmless but avoidable.
3. **`observers` is per-ticket, not global.** The reporter of `INC-0001` hears nothing
   about `INC-0002`. That is the Observer pattern applied at the right granularity — the
   subject is the ticket, not the manager.

---

## 3. What a Relational Schema Would Look Like

If this were backed by Postgres rather than hash maps:

```mermaid
erDiagram
    users {
        varchar id PK
        varchar name
        varchar role "CHECK IN (REPORTER, AGENT)"
    }
    tickets {
        varchar id PK "INC-0001"
        varchar title
        varchar priority "CHECK IN (P1_CRITICAL, P2_HIGH, P3_LOW)"
        varchar status "CHECK IN (NEW, ASSIGNED, IN_PROGRESS, RESOLVED)"
        varchar reporter_id FK "NOT NULL"
        varchar assignee_id FK "NULL until assigned"
        timestamp created_at
        timestamp updated_at
        int version "optimistic lock — see below"
    }
    ticket_watchers {
        varchar ticket_id FK
        varchar user_id FK
    }
    ticket_events {
        bigint id PK
        varchar ticket_id FK
        varchar from_status
        varchar to_status
        varchar actor_id FK
        timestamp occurred_at
    }

    users   ||--o{ tickets         : "reporter_id"
    users   ||--o{ tickets         : "assignee_id"
    tickets ||--o{ ticket_watchers : ""
    users   ||--o{ ticket_watchers : ""
    tickets ||--o{ ticket_events   : ""
```

**The constraints that encode the lifecycle rules**

| Rule from the problem statement | How the schema enforces it |
|---|---|
| A ticket in `NEW` has no assignee | `CHECK (status <> 'NEW' OR assignee_id IS NULL)` |
| Every non-`NEW` ticket has an assignee | `CHECK (status = 'NEW' OR assignee_id IS NOT NULL)` |
| `RESOLVED` is terminal | Enforced in the transition query, not the schema: `UPDATE ... WHERE status = 'IN_PROGRESS'` and assert one row affected |
| Two threads must not transition the same ticket at once | `version` column + `WHERE version = :expected`, or `SELECT ... FOR UPDATE` |
| The reporter always watches their ticket | Insert into `ticket_watchers` in the same transaction as the ticket insert |

That last row is the distributed-system answer to the in-memory concern raised in
[03 § 6](03-sequence-diagrams.md#flow-6--the-concurrency-race-on-a-single-ticket):
a single JVM serialises transitions with a lock; several JVMs serialise them with a
row version.

---

## 4. Cardinality at a Glance

```mermaid
flowchart LR
    U1["User<br/>(REPORTER)"] -->|"1 : N"| T["Ticket"]
    T -->|"N : 1"| U2["User<br/>(AGENT)<br/>0..1 per ticket"]
    T -->|"1 : 1"| S["TicketState<br/>exactly one, always"]
    T -->|"N : 1"| P["Priority<br/>exactly one, immutable"]
    T -->|"1 : N"| O["Observer<br/>≥ 1 — reporter is auto-added"]
    U3["User"] -->|"implements"| O

    style T fill:#e8f0fe,stroke:#4285f4,stroke-width:2px
```

| Entity pair | Min | Max | Enforced where |
|---|---|---|---|
| Ticket → reporter | 1 | 1 | Constructor parameter; field never reassigned |
| Ticket → assignee | 0 | 1 | `setAssignee`, called only from `NewState.assign` / `AssignedState.assign` |
| Ticket → state | 1 | 1 | Constructor sets `new NewState()`; `setState` always replaces |
| Ticket → observers | 1 | N | Constructor auto-adds the reporter, so the list is never empty |
| Ticket → priority | 1 | 1 | Constructor parameter; no setter exists |
| Manager → tickets | 0 | N | `ticketDb`, keyed by generated id |
| Manager → agents | 0 | N | `agentDb` — ⚠️ **may be empty**, which is what makes `RoundRobinRouting` throw |
