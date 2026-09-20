# Ticket Management System — Flowcharts

> The **algorithm** view: one flowchart per method that makes a decision. Where the
> sequence diagrams show *who* talks to *whom*, these show the branches taken inside a
> single call.

---

## 1. Master Flow — End to End

```mermaid
flowchart TD
    START([Start]) --> WIRE["new RoundRobinRouting()"]
    WIRE --> GET["IncidentManager.getInstance(strategy)"]
    GET --> AGENTS["addAgent(Alice)<br/>addAgent(Bob)"]
    AGENTS --> CREATE["createIncident('Database is down',<br/>P1_CRITICAL, Charlie)"]
    CREATE --> ID["id = INC-0001<br/>state = NEW<br/>reporter auto-watches"]
    ID --> ASSIGN{"autoAssign(INC-0001)"}

    ASSIGN -->|"ticket not found"| NOOP["⚠️ silent return"]
    NOOP --> END1([End])

    ASSIGN -->|"ticket found"| FIND{"strategy.findAgent(pool)"}
    FIND -->|"pool empty"| THROW1["RuntimeException<br/>'No agents available'"]
    THROW1 --> END1

    FIND -->|"agent chosen"| DOASSIGN["NewState.assign()<br/>→ ASSIGNED"]
    DOASSIGN --> NOTIFY1["notify watchers ×2"]
    NOTIFY1 --> PROG["markInProgress()"]
    PROG --> INPROG["AssignedState.startProgress()<br/>→ IN_PROGRESS"]
    INPROG --> NOTIFY2["notify watchers ×2"]
    NOTIFY2 --> RES["resolveTicket()"]
    RES --> RESOLVED["InProgressState.resolve()<br/>→ RESOLVED"]
    RESOLVED --> NOTIFY3["notify watchers ×2"]
    NOTIFY3 --> ILLEGAL["markInProgress() again"]
    ILLEGAL --> CATCH["ResolvedState throws<br/>IllegalStateException"]
    CATCH --> END2([End])

    style THROW1 fill:#ffebee,stroke:#c62828
    style NOOP fill:#fff8e1,stroke:#f9a825
    style CATCH fill:#fff8e1,stroke:#f9a825
```

---

## 2. `createIncident` — Filing a Ticket

```mermaid
flowchart TD
    A([createIncident title, priority, reporter]) --> B["n = ticketCounter.getAndIncrement()"]
    B --> C["id = 'INC-' + String.format('%04d', n)"]
    C --> D["new Ticket(id, title, priority, reporter)"]
    D --> E["currentState = new NewState()"]
    E --> F["addObserver(reporter)"]
    F --> G["ticketDb.put(id, ticket)"]
    G --> H["print 'Created INC-0001 | Status: NEW'"]
    H --> I([return ticket])

    style B fill:#e8f5e9,stroke:#2e7d32
    style G fill:#e8f5e9,stroke:#2e7d32
```

| Step | Cost | Thread safety |
|---|---|---|
| `getAndIncrement` | O(1) | ✅ CAS — two threads never get the same `n` |
| `String.format` | O(1) | ✅ no shared state |
| `new Ticket` | O(1) | ✅ nothing shared until published |
| `ticketDb.put` | O(1) avg | ✅ `ConcurrentHashMap` |

**No branches at all** — creation cannot fail. There is no validation of `title`
(empty and `null` are both accepted) and no null check on `reporter`, though a `null`
reporter would be added to `observers` and NPE on the *first* transition rather than
here. Fail-fast validation is the obvious hardening.

---

## 3. `autoAssign` — Route and Assign

```mermaid
flowchart TD
    A([autoAssign ticketId]) --> B["ticket = ticketDb.get(ticketId)"]
    B --> C{"ticket != null?"}
    C -->|"no"| D["⚠️ return silently —<br/>caller cannot tell the id was bad"]
    D --> Z([return])
    C -->|"yes"| E["bestAgent = routingStrategy.findAgent(agentDb)"]
    E --> F{"pool empty?"}
    F -->|"yes"| G["throw RuntimeException<br/>'No agents available'"]
    F -->|"no"| H["ticket.assignTo(bestAgent)"]
    H --> I["currentState.assign(ticket, agent)"]
    I --> J{"which state?"}
    J -->|"NEW"| K["setAssignee + setState(ASSIGNED)<br/>notify ×2"]
    J -->|"ASSIGNED"| L["setAssignee only<br/>notify ×1 (reassignment)"]
    J -->|"IN_PROGRESS"| M["throw 'Cannot reassign while in progress.'"]
    J -->|"RESOLVED"| N["throw 'Ticket resolved.'"]
    K --> Z
    L --> Z
    M --> Z
    N --> Z

    style D fill:#fff8e1,stroke:#f9a825
    style G fill:#ffebee,stroke:#c62828
    style M fill:#ffebee,stroke:#c62828
    style N fill:#ffebee,stroke:#c62828
```

**The asymmetry to notice.** An unknown ticket id returns silently; an empty agent pool
throws. Both are caller errors, and they should be reported the same way — a
`TicketNotFoundException` on the first. As written, a typo'd id looks like success.

---

## 4. `RoundRobinRouting.findAgent` — The Policy

```mermaid
flowchart TD
    A([findAgent agents]) --> B{"agents.isEmpty()?"}
    B -->|"yes"| C["throw RuntimeException<br/>'No agents available'"]
    B -->|"no"| D["raw = index.getAndUpdate(i -> i + 1)"]
    D --> E["curr = raw % agents.size()"]
    E --> F{"curr >= 0?"}
    F -->|"no ⚠️"| G["IndexOutOfBoundsException<br/>after int overflow"]
    F -->|"yes"| H["return agents.get(curr)"]

    style C fill:#ffebee,stroke:#c62828
    style G fill:#ffebee,stroke:#c62828
    style D fill:#e8f5e9,stroke:#2e7d32
```

**Walkthrough with two agents `[Alice, Bob]`, cursor starting at 1:**

| Call | `raw` | `raw % 2` | Agent | Expected |
|---|---|---|---|---|
| 1st | 1 | 1 | **Bob** | Alice ⚠️ |
| 2nd | 2 | 0 | Alice | Bob |
| 3rd | 3 | 1 | Bob | Alice |
| 4th | 4 | 0 | Alice | Bob |

The *distribution* is still perfectly even — every agent gets an equal share. Only the
starting offset is wrong, which is why the demo prints Bob for `INC-0001`.

**The hardened version:**

```java
@Override
public User findAgent(List<User> agents) {
    if (agents.isEmpty()) {
        throw new IllegalStateException("No agents available");
    }
    synchronized (agents) {                                 // size + get as one unit
        int curr = Math.floorMod(index.getAndIncrement(), agents.size());
        return agents.get(curr);
    }
}
```

`Math.floorMod` is the whole fix for overflow: it returns a non-negative result for
negative inputs, where `%` does not.

---

## 5. State Delegation — The One-Line Methods

The three entry points on `Ticket`, and what makes them interesting is what they
*don't* contain.

```mermaid
flowchart LR
    subgraph ticket["Ticket — zero decisions"]
        A1["assignTo(agent)"] --> B1["currentState.assign(this, agent)"]
        A2["markInProgress()"] --> B2["currentState.startProgress(this)"]
        A3["resolveTicket()"] --> B3["currentState.resolve(this)"]
    end

    subgraph states["The decision lives here"]
        B1 --> C{"polymorphic dispatch"}
        B2 --> C
        B3 --> C
        C --> N["NewState"]
        C --> AS["AssignedState"]
        C --> IP["InProgressState"]
        C --> R["ResolvedState"]
    end

    style C fill:#e8f0fe,stroke:#4285f4,stroke-width:2px
```

> **The test for whether you got State right:** grep the context class for `if` and
> `switch`. `Ticket` has neither in any lifecycle method. The twelve rules
> (3 operations × 4 states) are distributed across four classes that never mention
> each other's names except to construct a successor.

---

## 6. `setState` — Transition and Announce

```mermaid
flowchart TD
    A([setState newState]) --> B["oldState = currentState.getStateName()"]
    B --> C["currentState = newState"]
    C --> D["notifyObservers('Ticket has been moved from ' +<br/>oldState + ' to ' + newState.getStateName())"]
    D --> E{"for each observer"}
    E --> F["o.onUpdate(ticket, message)"]
    F --> G{"observer threw?"}
    G -->|"yes ⚠️"| H["loop aborts —<br/>remaining observers never called,<br/>exception escapes to caller"]
    G -->|"no"| E
    E -->|"done"| I([return])
    H --> J([propagates])

    style C fill:#e8f5e9,stroke:#2e7d32
    style H fill:#ffebee,stroke:#c62828
```

**The ordering is correct and worth defending:** `oldState` is captured *before* the
assignment, so the message reads "moved from NEW to ASSIGNED" rather than
"from ASSIGNED to ASSIGNED". The failure containment is the part that is missing —
see [03 Flow 8](03-sequence-diagrams.md#flow-8--observer-failure-is-not-isolated).

---

## 7. The Double-Notification Path

Why every transition emits two messages, and how to collapse it to one.

```mermaid
flowchart TD
    subgraph current["❌ Today — two call sites"]
        A["NewState.assign()"] --> B["ticket.setState(new AssignedState())"]
        B --> C["notifyObservers('moved from NEW to ASSIGNED')"]
        A --> D["ticket.notifyObservers('Assigned to agent: Bob')"]
        C --> E["watcher sees 2 messages"]
        D --> E
    end

    subgraph fixed["✅ Fixed — one call site"]
        F["NewState.assign()"] --> G["ticket.setState(new AssignedState(),<br/>'Assigned to agent: Bob')"]
        G --> H["notifyObservers('NEW → ASSIGNED: Assigned to agent: Bob')"]
        H --> I["watcher sees 1 message"]
    end

    current -.->|"refactor: pass the reason<br/>into setState"| fixed

    style E fill:#fff8e1,stroke:#f9a825
    style I fill:#e8f5e9,stroke:#2e7d32
```

```java
// Ticket — one transition, one announcement
public void setState(TicketState next, String reason) {
    String from = currentState.getStateName();
    this.currentState = next;
    notifyObservers(from + " → " + next.getStateName() + ": " + reason);
}
```

This also fixes the inconsistency where reassignment (which does not call `setState`)
emits one message while a real transition emits two.

---

## 8. `getInstance` — Double-Checked Locking

```mermaid
flowchart TD
    A([getInstance strategy]) --> B{"instance == null?"}
    B -->|"false"| C["return instance — fast path, no lock"]
    B -->|"true"| D["🔒 synchronized(lock)"]
    D --> E{"instance == null? (again)"}
    E -->|"false"| F["another thread won —<br/>fall through"]
    E -->|"true"| G["construct"]
    G --> H{"assign to instance?"}
    H -->|"✅ correct"| I["instance = new IncidentManager(s)"]
    H -->|"⚠️ as written"| J["return new IncidentManager(s)<br/>— never assigned"]
    I --> K["🔓 release → return instance"]
    F --> K
    J --> L["🔓 release → a NEW manager<br/>on every single call"]
    C --> M([one shared instance])
    K --> M
    L --> N([N managers, N ticket stores])

    style J fill:#ffebee,stroke:#c62828
    style N fill:#ffebee,stroke:#c62828
    style I fill:#e8f5e9,stroke:#2e7d32
```

| | Correct DCL | This code |
|---|---|---|
| Field | `private static volatile IncidentManager instance;` | `private static final IncidentManager instance = null;` ⚠️ |
| Lock | `private static final Object lock` | `private static Object lock` — non-final |
| Constructor | `private` | `public` ⚠️ |
| Inside the lock | **assigns** then returns the field | **returns** a new object, assigns nothing ⚠️ |
| Result | Exactly one manager, forever | One manager per call |

---

## 9. Adding a New State — The Open/Closed Test

What it takes to add `ON_HOLD`, the feature every service desk asks for second.

```mermaid
flowchart TD
    A(["Requirement: an agent can park a ticket<br/>waiting on a third party"]) --> B["1. Create OnHoldState<br/>implements TicketState"]
    B --> C["2. Implement its four methods:<br/>assign / startProgress / resolve / getStateName"]
    C --> D["3. Add the entry path:<br/>InProgressState gains hold(ticket)"]
    D --> E["4. Add the exit path:<br/>OnHoldState.startProgress → InProgressState"]
    E --> F["5. Add Ticket.putOnHold()<br/>→ currentState.hold(this)"]
    F --> G{"Did IncidentManager change?"}
    G -->|"No"| H["✅ Open/Closed satisfied"]
    G --> I{"Did existing states' logic change?"}
    I -->|"Only InProgressState gained a method"| H

    style H fill:#e8f5e9,stroke:#2e7d32,stroke-width:2px
```

Compare with the enum-and-conditional design, where `ON_HOLD` means editing every
`switch` in `Ticket` and hoping none was missed. Here the compiler enforces
completeness: adding `hold` to `TicketState` breaks every implementation until each one
states what it does.

---

## 10. Adding a New Routing Policy

```mermaid
flowchart LR
    A["Need: P1 goes to the<br/>least-loaded senior agent"] --> B["class LeastLoadedRouting<br/>implements RoutingStrategy"]
    B --> C["findAgent(List~User~ agents)"]
    C --> D["Inject: IncidentManager.getInstance(new LeastLoadedRouting())"]
    D --> E{"IncidentManager edited?"}
    E -->|"No — one line at the call site"| F["✅ Open/Closed satisfied"]

    G["⚠️ But findAgent(List~User~)<br/>receives only the pool"] -.-> H["It cannot see the ticket's<br/>priority or an agent's load"]
    H -.-> I["Signature change needed for<br/>anything smarter than round-robin:<br/>findAgent(List~User~, Ticket)"]

    style F fill:#e8f5e9,stroke:#2e7d32
    style I fill:#fff8e1,stroke:#f9a825
```

**The design limit worth naming in an interview.** `RoutingStrategy.findAgent` takes
only the agent pool. Any policy that depends on the *ticket* — priority-based routing,
skill matching, "the reporter's own team first" — cannot be expressed without widening
the interface to `findAgent(List<User> agents, Ticket ticket)`. That is the change
`Priority` is waiting for: it is stored on every ticket today and read by nothing.
