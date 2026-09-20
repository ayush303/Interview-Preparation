# Ticket Management System — Class Diagram

> Structural view of the system. Every field, method and relationship below mirrors the
> Java sources in [`../solution/`](../solution/).

---

## 1. Full Class Diagram

```mermaid
classDiagram
    direction TB

    class IncidentManager {
        -Map~String, Ticket~ ticketDb
        -List~User~ agentDb
        -RoutingStrategy routingStrategy
        -AtomicInteger ticketCounter
        -IncidentManager instance$
        -Object lock$
        +IncidentManager(RoutingStrategy routingStrategy)
        +getInstance(RoutingStrategy routingStrategy)$ IncidentManager
        +addAgent(User agent) void
        +createIncident(String title, Priority priority, User reporter) Ticket
        +autoAssign(String ticketId) void
        +getTicket(String ticketId) Ticket
    }

    class Ticket {
        -String id
        -User reporter
        -User assignee
        -String title
        -Priority priority
        -TicketState currentState
        -List~Observer~ observers
        +Ticket(String id, String title, Priority priority, User reporter)
        +assignTo(User agent) void
        +markInProgress() void
        +resolveTicket() void
        +setState(TicketState state) void
        +setAssignee(User assignee) void
        +addObserver(Observer o) void
        +notifyObservers(String message) void
        +getId() String
        +getReporter() User
        +getAssignee() User
        +getTitle() String
        +getPriority() Priority
        +getCurrentState() String
        +getObservers() List~Observer~
    }

    class User {
        -String id
        -String name
        -Role role
        +User(String id, String name, Role role)
        +getId() String
        +getName() String
        +getRole() Role
        +onUpdate(Ticket ticket, String message) void
    }

    class Subject {
        <<interface>>
        +addObserver(Observer o) void
        +notifyObservers(String message) void
    }

    class Observer {
        <<interface>>
        +onUpdate(Ticket ticket, String message) void
    }

    class TicketState {
        <<interface>>
        +assign(Ticket ticket, User agent) void
        +startProgress(Ticket ticket) void
        +resolve(Ticket ticket) void
        +getStateName() String
    }

    class NewState {
        +assign(Ticket ticket, User agent) void
        +startProgress(Ticket ticket) void
        +resolve(Ticket ticket) void
        +getStateName() String
    }

    class AssignedState {
        +assign(Ticket ticket, User agent) void
        +startProgress(Ticket ticket) void
        +resolve(Ticket ticket) void
        +getStateName() String
    }

    class InProgressState {
        +assign(Ticket ticket, User agent) void
        +startProgress(Ticket ticket) void
        +resolve(Ticket ticket) void
        +getStateName() String
    }

    class ResolvedState {
        +assign(Ticket ticket, User agent) void
        +startProgress(Ticket ticket) void
        +resolve(Ticket ticket) void
        +getStateName() String
    }

    class RoutingStrategy {
        <<interface>>
        +findAgent(List~User~ agents) User
    }

    class RoundRobinRouting {
        -AtomicInteger index
        +findAgent(List~User~ agents) User
    }

    class Priority {
        <<enumeration>>
        P1_CRITICAL
        P2_HIGH
        P3_LO
    }

    class Role {
        <<enumeration>>
        REPORTER
        AGENT
    }

    class ServiceNowSystem {
        +main(String[] args)$ void
    }

    IncidentManager *-- Ticket : owns ticketDb (composition)
    IncidentManager o-- User : registers agentDb (aggregation)
    IncidentManager --> RoutingStrategy : delegates agent choice
    IncidentManager ..> Priority : files with

    Subject <|.. Ticket : implements
    Observer <|.. User : implements
    Ticket o-- Observer : notifies watchers
    Observer ..> Ticket : reads on update

    Ticket *-- TicketState : current state (composition)
    Ticket --> User : reporter and assignee
    Ticket --> Priority : severity

    TicketState <|.. NewState : implements
    TicketState <|.. AssignedState : implements
    TicketState <|.. InProgressState : implements
    TicketState <|.. ResolvedState : implements

    NewState ..> AssignedState : creates on assign
    AssignedState ..> InProgressState : creates on startProgress
    InProgressState ..> ResolvedState : creates on resolve

    TicketState ..> IllegalStateException : throws on illegal op

    RoutingStrategy <|.. RoundRobinRouting : implements
    RoundRobinRouting ..> User : selects from pool

    User --> Role : classified as

    ServiceNowSystem ..> IncidentManager : uses
    ServiceNowSystem ..> RoundRobinRouting : creates and injects
    ServiceNowSystem ..> Ticket : drives lifecycle
```

> **Note on `ticketDb` / `agentDb`** — their true types are
> `ConcurrentHashMap<String, Ticket>` and
> `Collections.synchronizedList(new ArrayList<User>())`. The boxes show the declared
> interface types, since that is what the rest of the code programs against.

> ⚠️ **Note on `instance`** — declared `private static final IncidentManager instance = null;`.
> Because it is `final` and initialised to `null`, it can never be assigned, so
> `getInstance` returns a **brand-new manager on every call**. See
> [§ 5](#5-the-singleton-that-isnt) and
> [04 § 4](04-state-diagrams.md#4-singleton-initialisation--intended-vs-actual).

---

## 2. Layered / Package View

How the classes group into packages, and which direction the dependencies point.

```mermaid
flowchart TB
    subgraph client["Client Layer"]
        DEMO["ServiceNowSystem<br/>(main)"]
    end

    subgraph orchestration["Orchestration Layer — solution"]
        MGR["IncidentManager<br/>(Singleton, intended)"]
    end

    subgraph policy["Policy Layer — Strategy"]
        RS["RoutingStrategy<br/>(interface)"]
        RR["RoundRobinRouting"]
    end

    subgraph lifecycle["Lifecycle Layer — State"]
        TS["TicketState<br/>(interface)"]
        NEW["NewState"]
        ASG["AssignedState"]
        IPS["InProgressState"]
        RES["ResolvedState"]
    end

    subgraph notify["Notification Layer — Observer"]
        SUB["Subject<br/>(interface)"]
        OBS["Observer<br/>(interface)"]
    end

    subgraph domain["Domain Layer — models / enums"]
        TKT["Ticket"]
        USR["User"]
        PRI["Priority"]
        ROL["Role"]
    end

    DEMO --> MGR
    DEMO --> RR
    MGR --> RS
    MGR --> TKT
    MGR --> USR
    RS -.-> RR
    RR --> USR

    TKT --> TS
    TS -.-> NEW
    TS -.-> ASG
    TS -.-> IPS
    TS -.-> RES
    NEW --> TKT
    ASG --> TKT
    IPS --> TKT
    RES --> TKT

    TKT -.implements.-> SUB
    USR -.implements.-> OBS
    TKT --> OBS
    OBS --> TKT

    TKT --> PRI
    TKT --> USR
    USR --> ROL
```

**The one cycle worth naming.** `models` ↔ `State` is bidirectional: `Ticket` holds a
`TicketState`, and every state takes a `Ticket` back as a parameter. That is inherent to
the State pattern — the state must be able to move its context on — and is why
`Ticket.setState` and `Ticket.setAssignee` are public rather than private.

---

## 3. Design-Pattern Overlay

The same classes, grouped by the pattern each one plays a role in.

```mermaid
flowchart LR
    subgraph state["① STATE"]
        direction TB
        ST1["Context:<br/>Ticket"]
        ST2["State:<br/>TicketState"]
        ST3["Concrete:<br/>New / Assigned /<br/>InProgress / Resolved"]
        ST1 --> ST2
        ST2 --> ST3
    end

    subgraph strategy["② STRATEGY"]
        direction TB
        SG1["Context:<br/>IncidentManager"]
        SG2["Strategy:<br/>RoutingStrategy"]
        SG3["Concrete:<br/>RoundRobinRouting"]
        SG1 --> SG2
        SG2 --> SG3
    end

    subgraph observer["③ OBSERVER"]
        direction TB
        O1["Subject:<br/>Ticket"]
        O2["Observer:<br/>Observer"]
        O3["Concrete:<br/>User"]
        O1 --> O2
        O2 --> O3
    end

    subgraph singleton["④ SINGLETON ⚠️"]
        direction TB
        S1["IncidentManager<br/>.getInstance()"]
        S2["static instance + DCL<br/>⚠️ final null — never assigned"]
        S1 --- S2
    end

    singleton -.->|"owns the pool + policy"| strategy
    singleton -.->|"creates and stores"| state
    state -.->|"every transition fires"| observer
```

| Pattern | Role in this system | Extend without touching existing code? |
|---|---|---|
| **State** | Each lifecycle state owns the rules for what may happen in it | ✅ add a class implementing `TicketState` |
| **Strategy** | Makes agent routing an interchangeable policy | ✅ add a class implementing `RoutingStrategy` |
| **Observer** | Decouples a ticket's transitions from who hears about them | ✅ add a class implementing `Observer` |
| **Singleton** | One point of control over tickets, agents and policy | ⚠️ currently broken — see § 5 |

---

## 4. Why State, and not a `status` field

The alternative every first draft reaches for:

```java
// ❌ What the State pattern replaces
public void markInProgress() {
    if (status == NEW)              throw new IllegalStateException("...");
    else if (status == ASSIGNED)    status = IN_PROGRESS;
    else if (status == IN_PROGRESS) System.out.println("Already in progress.");
    else if (status == RESOLVED)    throw new IllegalStateException("...");
}
```

```mermaid
flowchart LR
    subgraph bad["❌ Enum + conditionals"]
        B1["Ticket"]
        B2["if/else or switch<br/>in every method"]
        B3["Adding ON_HOLD means<br/>editing assignTo,<br/>markInProgress, resolveTicket"]
        B1 --> B2 --> B3
    end

    subgraph good["✅ State objects"]
        G1["Ticket delegates"]
        G2["currentState.startProgress(this)"]
        G3["Adding ON_HOLD means<br/>one new class.<br/>Nothing else changes."]
        G1 --> G2 --> G3
    end

    bad -.->|"refactor"| good
```

Three operations × four states = twelve rules. As conditionals they are one tangle
spread across three methods; as state objects they are four small classes of four
methods each, and each rule sits next to the state it constrains.

---

## 5. The Singleton That Isn't

```java
private static final IncidentManager instance = null;   // ⚠️ final, pinned to null
private static Object lock = new Object();              // ⚠️ not final

public IncidentManager(RoutingStrategy routingStrategy) { ... }   // ⚠️ public

public static IncidentManager getInstance(RoutingStrategy s) {
    if (instance == null) {                    // always true — final null
        synchronized (lock) {
            if (instance == null) {            // always true
                return new IncidentManager(s); // ⚠️ returns, never assigns
            }
        }
    }
    return instance;                           // unreachable; would return null
}
```

Every call allocates a fresh manager with its own empty `ticketDb`, its own `agentDb`
and its own counter — so two callers' tickets are invisible to each other, and both
issue `INC-0001`.

The corrected shape:

```java
private static volatile IncidentManager instance;   // volatile, not final
private static final Object lock = new Object();

private IncidentManager(RoutingStrategy routingStrategy) { ... }  // private

public static IncidentManager getInstance(RoutingStrategy s) {
    if (instance == null) {
        synchronized (lock) {
            if (instance == null) {
                instance = new IncidentManager(s);  // assign, then fall through
            }
        }
    }
    return instance;
}
```

`volatile` is what makes the *first* (unlocked) check safe: without it, a second thread
can see a non-null reference to an object whose fields are not yet visible to it.

---

## 6. Relationship Notation Cheatsheet

| Mermaid | UML meaning | Example here | Why |
|---|---|---|---|
| `*--` | Composition — the part dies with the whole | `Ticket *-- TicketState` | A state object is created by and for one ticket; it has no life outside it |
| `o--` | Aggregation — the part outlives the whole | `IncidentManager o-- User` | Agents are people; they exist whether or not the manager does |
| `-->` | Association — holds a reference | `Ticket --> User` | A ticket points at its reporter and assignee |
| `<\|..` | Realization — implements an interface | `TicketState <\|.. NewState` | State / Strategy / Observer implementations |
| `..>` | Dependency — uses transiently | `NewState ..> AssignedState` | Constructed and handed off, never stored by the creator |
