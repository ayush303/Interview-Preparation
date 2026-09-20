# Ticket Management System — State Diagrams

> The **lifecycle** view. This is the heart of the design: the State pattern exists
> because the rules below are too many, and too likely to grow, to live as conditionals
> inside `Ticket`.

---

## 1. Ticket Lifecycle

The four states and the three operations, exactly as `State/` implements them.

```mermaid
stateDiagram-v2
    [*] --> NEW : createIncident(title, priority, reporter)

    NEW --> ASSIGNED : assignTo(agent)<br/>setAssignee + setState

    ASSIGNED --> ASSIGNED : assignTo(otherAgent)<br/>reassignment — state unchanged
    ASSIGNED --> IN_PROGRESS : markInProgress()

    IN_PROGRESS --> IN_PROGRESS : markInProgress()<br/>no-op, "Already in progress."
    IN_PROGRESS --> RESOLVED : resolveTicket()

    RESOLVED --> RESOLVED : resolveTicket()<br/>no-op, "Already resolved."
    RESOLVED --> [*] : terminal

    note right of NEW
        No assignee yet.
        Reporter is already
        a watcher.
    end note

    note right of ASSIGNED
        Has an assignee.
        The only state that
        allows reassignment.
    end note

    note right of IN_PROGRESS
        Locked to its agent —
        no reassignment
        mid-investigation.
    end note

    note right of RESOLVED
        Terminal. Every
        mutating call throws.
    end note
```

Each box is one class implementing `TicketState`; each labelled arrow is a method on
that class that calls `ticket.setState(...)`.

---

## 2. Rejected Transitions

The same machine with every *illegal* call drawn in, since those are what the design is
really for.

```mermaid
stateDiagram-v2
    [*] --> NEW

    NEW --> ASSIGNED : ✅ assignTo(agent)
    NEW --> NEW_ERR1 : ❌ markInProgress()
    NEW --> NEW_ERR2 : ❌ resolveTicket()

    ASSIGNED --> IN_PROGRESS : ✅ markInProgress()
    ASSIGNED --> ASSIGNED : ✅ assignTo(other)
    ASSIGNED --> ASG_ERR : ❌ resolveTicket()

    IN_PROGRESS --> RESOLVED : ✅ resolveTicket()
    IN_PROGRESS --> IN_PROGRESS : ⚪ markInProgress()
    IN_PROGRESS --> IP_ERR : ❌ assignTo(other)

    RESOLVED --> RESOLVED : ⚪ resolveTicket()
    RESOLVED --> RES_ERR1 : ❌ assignTo(other)
    RESOLVED --> RES_ERR2 : ❌ markInProgress()

    state NEW_ERR1 <<choice>>
    state NEW_ERR2 <<choice>>
    state ASG_ERR  <<choice>>
    state IP_ERR   <<choice>>
    state RES_ERR1 <<choice>>
    state RES_ERR2 <<choice>>

    NEW_ERR1 --> NEW : IllegalStateException<br/>"Cannot start progress on unassigned ticket."
    NEW_ERR2 --> NEW : IllegalStateException<br/>"Cannot resolve ticket directly from NEW."
    ASG_ERR  --> ASSIGNED : IllegalStateException<br/>"Cannot resolve ticket directly from NEW." ⚠️
    IP_ERR   --> IN_PROGRESS : IllegalStateException<br/>"Cannot reassign while in progress."
    RES_ERR1 --> RESOLVED : IllegalStateException<br/>"Ticket resolved."
    RES_ERR2 --> RESOLVED : IllegalStateException<br/>"Ticket resolved."
```

**Every rejected call leaves the state untouched.** The exception is thrown *before*
`setState` is reached, so a caught `IllegalStateException` means the ticket is exactly
where it was — and no watcher was notified of anything.

⚠️ `AssignedState.resolve` throws the message *"Cannot resolve ticket directly from
NEW."* — copy-pasted from `NewState`. The behaviour is right; the text names the wrong
state and will mislead whoever reads the log at 3am.

---

## 3. The State Objects Behind the Machine

The lifecycle above, redrawn as what it actually is at runtime: a reference being
replaced.

```mermaid
flowchart LR
    subgraph t0["t₀ — after createIncident"]
        T0["Ticket<br/>currentState ──▶"] --> N0["NewState"]
    end

    subgraph t1["t₁ — after assignTo"]
        T1["Ticket<br/>currentState ──▶"] --> A1["AssignedState"]
        N1["NewState<br/><i>garbage</i>"]
    end

    subgraph t2["t₂ — after markInProgress"]
        T2["Ticket<br/>currentState ──▶"] --> I2["InProgressState"]
        A2["AssignedState<br/><i>garbage</i>"]
    end

    subgraph t3["t₃ — after resolveTicket"]
        T3["Ticket<br/>currentState ──▶"] --> R3["ResolvedState"]
        I3["InProgressState<br/><i>garbage</i>"]
    end

    t0 -->|"NewState.assign()<br/>creates AssignedState"| t1
    t1 -->|"AssignedState.startProgress()<br/>creates InProgressState"| t2
    t2 -->|"InProgressState.resolve()<br/>creates ResolvedState"| t3

    style N0 fill:#e3f2fd,stroke:#1565c0
    style A1 fill:#fff3e0,stroke:#ef6c00
    style I2 fill:#f3e5f5,stroke:#6a1b9a
    style R3 fill:#e8f5e9,stroke:#2e7d32
```

**Who performs the transition matters.** The old state constructs its successor and
calls `setState` on the ticket. `Ticket` never decides where it goes next — that
knowledge lives in the state being left. This is what makes adding `ON_HOLD` a purely
additive change: `InProgressState` gains a `hold()` path, and nothing else moves.

*(Note: the four state classes hold no fields, so they could be shared immutable
singletons instead of being allocated per transition. The code allocates; the cost is
trivial and the diagram shows it honestly.)*

---

## 4. Singleton Initialisation — Intended vs Actual

```mermaid
stateDiagram-v2
    direction TB

    state "INTENDED (correct DCL)" as intended {
        [*] --> Uninitialised
        Uninitialised --> Check1 : getInstance()
        Check1 --> ReturnExisting : instance != null<br/>(fast path, no lock)
        Check1 --> AcquireLock : instance == null
        AcquireLock --> Check2 : 🔒 synchronized(lock)
        Check2 --> Construct : still null — I am first
        Check2 --> ReleaseLock : another thread won the race
        Construct --> Assign : instance = new IncidentManager(s)
        Assign --> ReleaseLock
        ReleaseLock --> ReturnExisting : 🔓
        ReturnExisting --> [*] : the ONE instance
    }

    state "ACTUAL (as written) ⚠️" as actual {
        [*] --> Check1b : getInstance()
        Check1b --> AcquireLockb : instance == null<br/>ALWAYS — field is final null
        AcquireLockb --> Check2b : 🔒
        Check2b --> ConstructNew : still null, ALWAYS
        ConstructNew --> ReturnNew : return new IncidentManager(s)<br/>⚠️ returns without assigning
        ReturnNew --> [*] : a DIFFERENT instance every call
    }

    intended --> actual : what the code does today
```

### Why both checks exist (in the correct version)

| Check | Runs | Purpose |
|---|---|---|
| **First**, outside the lock | Every call | Fast path — once initialised, no thread ever pays for the monitor |
| **Second**, inside the lock | Only when the first saw `null` | Correctness — two threads can both pass check 1; only one may construct |

`volatile` on the field is what makes the first check *safe*. Without it, a thread can
observe a non-null reference to an object whose constructor has not finished publishing
its fields — it would get a manager with a null `ticketDb`.

### Why the shipped version fails

`private static final IncidentManager instance = null;` — `final` means the field can
never be assigned after class initialisation, and it was initialised to `null`. So
`instance == null` is a compile-time-constant truth, both checks always pass, and the
method returns a freshly constructed manager without ever populating `instance`. See
[01 § 5](01-class-diagram.md#5-the-singleton-that-isnt) for the corrected code.

---

## 5. Assignee Slot Lifecycle

A smaller machine, worth drawing because it is the invariant a database `CHECK`
constraint would encode ([02 § 3](02-entity-relationship-diagram.md#3-what-a-relational-schema-would-look-like)).

```mermaid
stateDiagram-v2
    [*] --> Unassigned : Ticket constructed<br/>assignee = null

    Unassigned --> Assigned : NewState.assign()<br/>setAssignee(agent)
    Assigned --> Assigned : AssignedState.assign()<br/>setAssignee(otherAgent)
    Assigned --> Locked : markInProgress()

    Locked --> Frozen : resolveTicket()

    note right of Unassigned
        The ONLY state in which
        assignee is null.
        Invariant: state == NEW
        ⟺ assignee == null
    end note

    note right of Locked
        IN_PROGRESS —
        assignTo() throws.
        The agent who started
        work owns it.
    end note

    note right of Frozen
        RESOLVED — assignee is
        a permanent record of
        who fixed it.
    end note
```

---

## 6. Round-Robin Cursor

The routing policy's own tiny state machine, and the two bugs hiding in it.

```mermaid
stateDiagram-v2
    [*] --> Idx1 : new RoundRobinRouting()<br/>index = 1 ⚠️ not 0

    Idx1 --> Idx2 : findAgent() → 1 % 2 = agents[1] = Bob
    Idx2 --> Idx3 : findAgent() → 2 % 2 = agents[0] = Alice
    Idx3 --> Idx4 : findAgent() → 3 % 2 = agents[1] = Bob
    Idx4 --> IdxN : ... cursor grows without bound
    IdxN --> Overflow : after 2³¹ calls, int wraps negative ⚠️
    Overflow --> Crash : negative % size → negative index<br/>IndexOutOfBoundsException

    [*] --> Empty : agentDb is empty
    Empty --> Throw : RuntimeException("No agents available")

    note right of Idx1
        Starting at 1 means the
        FIRST ticket routes to the
        SECOND agent. Alice never
        gets INC-0001.
    end note
```

| Defect | Consequence | Fix |
|---|---|---|
| `index` starts at `1` | First ticket goes to `agents[1]`, not `agents[0]` | Initialise to `0` |
| Unbounded growth, then `int` overflow | `%` on a negative value yields a negative index → `IndexOutOfBoundsException` | `Math.floorMod(index.getAndIncrement(), size)` |
| Pool read without holding its lock | `agents.size()` and `agents.get(curr)` are two separate operations on a `synchronizedList`; a concurrent `addAgent` between them can shift the list | Snapshot the pool, or wrap the read in `synchronized (agents)` |

---

## 7. Request Lifecycle — End to End

Everything above, in the order the demo drives it.

```mermaid
stateDiagram-v2
    [*] --> Configuring
    Configuring --> PoolReady : addAgent(Alice), addAgent(Bob)
    PoolReady --> Raised : createIncident(...) → INC-0001, NEW

    Raised --> Routing : autoAssign("INC-0001")
    Routing --> NoAgents : pool empty
    NoAgents --> [*] : RuntimeException("No agents available")
    Routing --> AgentPicked : strategy.findAgent(pool)

    AgentPicked --> AssignedOk : NewState.assign()
    AssignedOk --> Notified1 : watchers told ×2 ⚠️

    Notified1 --> Working : markInProgress()
    Working --> Notified2 : watchers told ×2 ⚠️

    Notified2 --> Done : resolveTicket()
    Done --> Notified3 : watchers told ×2 ⚠️

    Notified3 --> Rejected : any further mutating call
    Rejected --> Notified3 : IllegalStateException — state unchanged
    Notified3 --> [*] : ticket closed
```
