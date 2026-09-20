# Ticket Management System — Use Case, Component & Activity Diagrams

> The remaining UML views that earn their place: who uses the system (**use case**),
> how it is assembled and where extensions plug in (**component**), the concurrent work
> of a ticket transition (**activity**), and what changes when this runs on more than
> one machine (**deployment**).

---

## 1. Use Case Diagram

Actors and the capabilities exposed to each.

```mermaid
flowchart LR
    REP(["👤 Reporter<br/>(employee)"])
    AGT(["👤 Agent<br/>(service desk)"])
    LEAD(["👤 Team Lead"])
    ADMIN(["⚙️ System Admin"])
    NOTIF(["📢 Notification Channel<br/>(email / Slack / SLA timer)"])

    subgraph system["Ticket Management System"]
        UC1(["Raise an incident"])
        UC2(["Watch a ticket"])
        UC3(["Auto-assign to an agent"])
        UC4(["Start work on a ticket"])
        UC5(["Resolve a ticket"])
        UC6(["Reassign a ticket"])
        UC7(["Look up a ticket by id"])
        UC8(["Register an agent"])
        UC9(["Choose a routing policy"])
        UC10(["Enforce the state machine"])
        UC11(["Reject an illegal transition"])
    end

    REP --> UC1
    REP --> UC7
    AGT --> UC4
    AGT --> UC5
    AGT --> UC7
    LEAD --> UC3
    LEAD --> UC6
    ADMIN --> UC8
    ADMIN --> UC9

    UC1 -.->|"&lt;&lt;include&gt;&gt;"| UC2
    UC3 -.->|"&lt;&lt;include&gt;&gt;"| UC9
    UC4 -.->|"&lt;&lt;include&gt;&gt;"| UC10
    UC5 -.->|"&lt;&lt;include&gt;&gt;"| UC10
    UC6 -.->|"&lt;&lt;include&gt;&gt;"| UC10
    UC10 -.->|"&lt;&lt;extend&gt;&gt;"| UC11

    UC3 --> NOTIF
    UC4 --> NOTIF
    UC5 --> NOTIF
    UC6 --> NOTIF
```

| Actor | Use cases | Entry point in code |
|---|---|---|
| **Reporter** | Raise an incident, auto-watch it, look it up | `createIncident`, `getTicket` |
| **Agent** | Start work, resolve | `Ticket.markInProgress`, `Ticket.resolveTicket` |
| **Team Lead** | Route a ticket, hand it to someone else | `autoAssign`, `Ticket.assignTo` |
| **System Admin** | Staff the pool, set the policy | `addAgent`, the `RoutingStrategy` injected into the constructor |
| **Notification channel** *(secondary)* | Receive every transition | `Observer.onUpdate` implementations |

**The missing use case.** There is no "close a ticket" and no "reopen a ticket".
`RESOLVED` is terminal, so a fix that did not work leaves the reporter with no path
back. `REOPENED` is the third state a real deployment adds, after `ON_HOLD` and
`CLOSED`.

---

## 2. Component Diagram

How the system is assembled, and where a new piece plugs in without touching the rest.

```mermaid
flowchart TB
    subgraph client["🖥️ Client"]
        DEMO["ServiceNowSystem<br/><i>main — wires and drives</i>"]
    end

    subgraph core["⚙️ Core"]
        MGR["IncidentManager<br/><i>ticket store · agent pool · policy holder</i>"]
    end

    subgraph domainpkg["📦 Domain — models / enums"]
        TKT["Ticket<br/><i>Subject; delegates to its state</i>"]
        USR["User<br/><i>Observer; has a Role</i>"]
        ENUMS["Priority · Role"]
    end

    subgraph statepkg["🔄 Lifecycle — State ◀ plug-in point"]
        TSI["TicketState<br/><i>interface</i>"]
        S1["NewState"]
        S2["AssignedState"]
        S3["InProgressState"]
        S4["ResolvedState"]
        S5["OnHoldState<br/><i>← future, no core change</i>"]
    end

    subgraph strategypkg["🎯 Policy — Strategy ◀ plug-in point"]
        RSI["RoutingStrategy<br/><i>interface</i>"]
        R1["RoundRobinRouting"]
        R2["LeastLoadedRouting<br/><i>← future, no core change</i>"]
        R3["PriorityBasedRouting<br/><i>← future, needs a wider signature</i>"]
    end

    subgraph obspkg["📢 Notification — Observer ◀ plug-in point"]
        SUBI["Subject<br/><i>interface</i>"]
        OBSI["Observer<br/><i>interface</i>"]
        O1["User"]
        O2["SlackNotifier<br/><i>← future, no core change</i>"]
        O3["SlaTimer / AuditLog<br/><i>← future, no core change</i>"]
    end

    DEMO ==> MGR
    DEMO ==> R1
    MGR ==> TKT
    MGR ==> RSI
    MGR ==> USR
    RSI -.-> R1
    RSI -.-> R2
    RSI -.-> R3
    TKT ==> TSI
    TSI -.-> S1
    TSI -.-> S2
    TSI -.-> S3
    TSI -.-> S4
    TSI -.-> S5
    TKT -.implements.-> SUBI
    TKT ==> OBSI
    OBSI -.-> O1
    OBSI -.-> O2
    OBSI -.-> O3
    O1 -.is a.-> USR
    TKT ==> ENUMS
    USR ==> ENUMS

    style S5 stroke-dasharray: 5 5
    style R2 stroke-dasharray: 5 5
    style R3 stroke-dasharray: 5 5
    style O2 stroke-dasharray: 5 5
    style O3 stroke-dasharray: 5 5
```

### Open/Closed in one table

| New requirement | What you write | What you edit | Verdict |
|---|---|---|---|
| Slack notifications on every transition | `class SlackNotifier implements Observer` | one `addObserver` call | ✅ closed |
| SLA breach timer | `class SlaTimer implements Observer` | one `addObserver` call | ✅ closed |
| Least-loaded routing | `class LeastLoadedRouting implements RoutingStrategy` | one constructor argument | ✅ closed |
| An `ON_HOLD` state | `class OnHoldState implements TicketState` + a `hold()` entry path | `TicketState` + its 4 implementations | ✅ closed to the core, open at the interface |
| **Priority-aware routing** | `class PriorityBasedRouting` | ⚠️ `RoutingStrategy.findAgent` signature, and every implementation | ⚠️ **not closed** — the interface is too narrow |
| **Unsubscribe a watcher** | — | ⚠️ `Subject` needs `removeObserver` | ⚠️ **not closed** — a gap in the interface |

The last two rows are the honest answer to "where does your design break?" — both are
missing *parameters* and *methods* on the extension interfaces, not missing classes.

---

## 3. Activity Diagram — A Ticket Transition, With Watchers

The work inside one `markInProgress()` call, showing which parts are the transition and
which are side effects.

```mermaid
flowchart TD
    START([Agent calls markInProgress]) --> A["Ticket.markInProgress()"]
    A --> B["currentState.startProgress(this)"]
    B --> C{"which concrete state?"}

    C -->|"NewState"| D["throw IllegalStateException<br/>'Cannot start progress on unassigned ticket.'"]
    D --> ERR([state unchanged · no watcher fired])

    C -->|"ResolvedState"| E["throw IllegalStateException<br/>'Ticket resolved.'"]
    E --> ERR

    C -->|"InProgressState"| F["print 'Already in progress.'"]
    F --> DONE([no-op · no watcher fired])

    C -->|"AssignedState"| G["new InProgressState()"]
    G --> H["ticket.setState(inProgress)"]
    H --> I["capture oldState = 'ASSIGNED'"]
    I --> J["currentState = inProgress<br/><b>◀ the transition commits HERE</b>"]
    J --> K["notifyObservers('moved from ASSIGNED to IN_PROGRESS')"]

    K --> L{"for each watcher"}
    L --> M["watcher.onUpdate(ticket, msg)"]
    M --> N{"threw?"}
    N -->|"yes ⚠️"| O["loop aborts · later watchers never told ·<br/>exception escapes — but the state HAS changed"]
    N -->|"no"| L
    L -->|"all done"| P["back in AssignedState.startProgress"]
    P --> Q["ticket.notifyObservers('Work has started.') ⚠️ second message"]
    Q --> R([transition complete])
    O --> S([inconsistent: state moved, watchers half-told])

    style J fill:#e8f5e9,stroke:#2e7d32,stroke-width:2px
    style O fill:#ffebee,stroke:#c62828
    style S fill:#ffebee,stroke:#c62828
    style Q fill:#fff8e1,stroke:#f9a825
```

**The line that matters is `J`.** Everything above it can still abort cleanly;
everything below it is running after the fact is already true. That is why observer
failures must be contained rather than propagated — there is nothing left to roll back.

---

## 4. Activity Diagram — Concurrent Work, With Swimlanes

Two agents and a reporter acting at once, showing which lanes are safe and which are not.

```mermaid
flowchart TB
    subgraph reporter["🧑 Reporter thread"]
        R1["createIncident('VPN down', P2_HIGH)"]
        R2["ticketCounter.getAndIncrement()"]
        R3["ticketDb.put(INC-0002, ticket)"]
        R1 --> R2 --> R3
    end

    subgraph lead["🧑‍💼 Lead thread"]
        L1["autoAssign('INC-0002')"]
        L2["ticketDb.get(INC-0002)"]
        L3["strategy.findAgent(pool)"]
        L4["ticket.assignTo(agent)"]
        L1 --> L2 --> L3 --> L4
    end

    subgraph agent["🧑‍🔧 Agent thread"]
        A1["ticket.markInProgress()"]
        A2["read currentState"]
        A3["setState(InProgressState)"]
        A1 --> A2 --> A3
    end

    subgraph shared["🔗 Shared mutable state"]
        SH1["ticketCounter : AtomicInteger<br/>✅ CAS — safe"]
        SH2["ticketDb : ConcurrentHashMap<br/>✅ safe"]
        SH3["agentDb : synchronizedList<br/>⚠️ safe per-call, NOT across size()+get()"]
        SH4["RoundRobinRouting.index : AtomicInteger<br/>✅ safe"]
        SH5["Ticket.currentState<br/>❌ UNGUARDED — read-then-write race"]
        SH6["Ticket.observers : ArrayList<br/>❌ UNGUARDED — concurrent add corrupts"]
    end

    R2 --> SH1
    R3 --> SH2
    L2 --> SH2
    L3 --> SH3
    L3 --> SH4
    L4 --> SH5
    A2 --> SH5
    A3 --> SH5
    L4 --> SH6
    A3 --> SH6

    style SH1 fill:#e8f5e9,stroke:#2e7d32
    style SH2 fill:#e8f5e9,stroke:#2e7d32
    style SH4 fill:#e8f5e9,stroke:#2e7d32
    style SH3 fill:#fff8e1,stroke:#f9a825
    style SH5 fill:#ffebee,stroke:#c62828
    style SH6 fill:#ffebee,stroke:#c62828
```

**Read this diagram as a scorecard.** The *manager's* state is defensively typed and
holds up; the *ticket's* state is plain fields and does not. Since every interesting
mutation happens on a ticket, the concurrency story is only half written — which is
exactly the gap [03 Flow 6](03-sequence-diagrams.md#flow-6--the-concurrency-race-on-a-single-ticket)
closes with three `synchronized` keywords and a `CopyOnWriteArrayList`.

---

## 5. Deployment / Scale-Out View

What breaks when this stops being one JVM.

```mermaid
flowchart TB
    subgraph single["Today — single JVM"]
        direction TB
        J1["IncidentManager<br/>(⚠️ actually N instances)"]
        H1["ticketDb — heap"]
        H2["agentDb — heap"]
        J1 --> H1
        J1 --> H2
    end

    subgraph scaled["Scaled out — N app servers"]
        direction TB
        subgraph n1["Node 1"]
            M1["IncidentManager"]
        end
        subgraph n2["Node 2"]
            M2["IncidentManager"]
        end
        DB[("PostgreSQL<br/>tickets · users<br/>ticket_events")]
        Q[["Kafka / outbox<br/>ticket.transitioned"]]
        W1["Notification workers<br/>email · Slack · SLA"]

        M1 --> DB
        M2 --> DB
        M1 --> Q
        M2 --> Q
        Q --> W1
    end

    single -->|"scale out"| scaled
```

| Concern | Single JVM (today) | Multi-node |
|---|---|---|
| Unique ticket ids | `AtomicInteger` per manager ⚠️ | DB sequence, or `INC-<node>-<n>` |
| One manager instance | Broken DCL | Statelessness — the manager holds nothing; the DB is the state |
| Serialising a transition | `synchronized` on the ticket | Optimistic locking: `UPDATE ... WHERE id=? AND status=? AND version=?`, assert 1 row |
| Watcher list | In-heap `ArrayList` per ticket | `ticket_watchers` rows |
| Notification delivery | In-process observer call | Transactional outbox → queue → workers, so a failed send retries without touching the ticket |
| Routing fairness | In-heap round-robin cursor | Shared cursor (Redis `INCR`), or route by `hash(ticketId) % agents` |

**The pattern that survives the move.** State and Strategy are unaffected by
distribution — they are pure in-process polymorphism over a ticket loaded from a row.
Observer is the one that changes shape: an in-process listener list becomes a queue,
because a notification that must survive a node restart cannot live on a heap.
