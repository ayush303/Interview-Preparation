# Meeting Room Scheduler — Use Case, Component & Activity Diagrams

> The remaining UML views that earn their place: who uses the system (**use case**),
> how it is assembled and where extensions plug in (**component**), and the
> concurrent work of a booking (**activity**).

---

## 1. Use Case Diagram

Actors and the capabilities exposed to each.

```mermaid
flowchart LR
    EMP(["👤 Employee"])
    ORG(["👤 Organizer"])
    ADMIN(["👤 Facilities Admin"])
    EMAIL_SYS(["📧 Email System"])
    CAL_SYS(["📅 Calendar System"])

    subgraph system["Meeting Room Scheduler"]
        UC1(["Schedule a meeting"])
        UC2(["Find available rooms"])
        UC3(["Cancel a meeting"])
        UC4(["Register a room"])
        UC5(["Choose selection policy"])
        UC6(["Subscribe to notifications"])
        UC7(["Validate the time slot"])
        UC8(["Reject on no room available"])
        UC9(["Enforce the state machine"])
    end

    EMP --> UC1
    EMP --> UC2
    ORG --> UC1
    ORG --> UC3
    ADMIN --> UC4
    ADMIN --> UC5
    ADMIN --> UC6

    UC1 -.->|"&lt;&lt;include&gt;&gt;"| UC2
    UC1 -.->|"&lt;&lt;include&gt;&gt;"| UC7
    UC1 -.->|"&lt;&lt;extend&gt;&gt;"| UC8
    UC3 -.->|"&lt;&lt;include&gt;&gt;"| UC9

    UC1 --> EMAIL_SYS
    UC1 --> CAL_SYS
    UC3 --> EMAIL_SYS
    UC3 --> CAL_SYS
```

| Actor | Use cases | Entry point in code |
|---|---|---|
| **Employee** | Book a room, browse availability | `scheduleMeeting`, `getAvailableRooms` |
| **Organizer** | Book, then cancel their meeting | `scheduleMeeting`, `cancelMeeting` |
| **Facilities Admin** | Register rooms, set policy, wire channels | `addRoom`, `setRoomSelectionStrategy`, `addObserver` |
| **Email / Calendar** *(secondary)* | Receive push notifications | `MeetingObserver` implementations |

---

## 2. Component Diagram

The assembled system, with the two extension seams called out explicitly.

```mermaid
flowchart TB
    subgraph app["Meeting Room Scheduler"]
        direction TB

        subgraph core["Core — MeetingScheduler (Singleton)"]
            API["Public API<br/>addRoom · scheduleMeeting<br/>cancelMeeting · getAvailableRooms"]
            STATE[("In-memory state<br/>rooms · meetings<br/>roomMeetings")]
            SYNC["Concurrency control<br/>synchronized methods<br/>+ concurrent collections"]
            API --- STATE
            API --- SYNC
        end

        subgraph seam1["🔌 Extension Seam 1 — Room Selection"]
            IRSS{{"RoomSelectionStrategy"}}
            IMPL1["FirstAvailableStrategy"]
            IMPL2["BestFitStrategy"]
            FUTURE1["SameFloorStrategy<br/>(future — no core change)"]
            IRSS -.- IMPL1
            IRSS -.- IMPL2
            IRSS -.- FUTURE1
        end

        subgraph seam2["🔌 Extension Seam 2 — Notification"]
            IMO{{"MeetingObserver"}}
            OBS1["EmailNotificationObserver"]
            OBS2["CalendarNotificationObserver"]
            FUTURE2["SlackObserver / SmsObserver<br/>(future — no core change)"]
            IMO -.- OBS1
            IMO -.- OBS2
            IMO -.- FUTURE2
        end

        subgraph model["Domain Model"]
            DM["Meeting · Room · TimeSlot · User<br/>MeetingStatus · RoomType"]
        end
    end

    CLIENT["MeetingSchedulerDemo<br/>(or any client)"] -->|calls| API
    API -->|"requires"| IRSS
    API -->|"publishes to"| IMO
    API -->|"reads / writes"| DM
    IRSS -->|"reads"| DM
    IMO -->|"reads"| DM

    style seam1 fill:#f0f8ff,stroke:#4a90d9
    style seam2 fill:#f0fff0,stroke:#5cb85c
    style core fill:#fffaf0,stroke:#d9a441
```

### Open/Closed in one table

| Change request | Files added | Files **modified** |
|---|---|---|
| "Prefer rooms on my floor" | `SameFloorStrategy.java` | none |
| "Notify Slack too" | `SlackNotificationObserver.java` | none |
| "Add a PROJECTOR room type" | — | `RoomType.java` (enum only) |
| "Require approval for board rooms" | new strategy/observer | ⚠️ likely core — this is a genuine new responsibility |

The first two rows are the design working as intended. The last row is honest about
the limit: the seams cover *policy* and *notification*, not new workflow stages.

---

## 3. Activity Diagram — Concurrent Booking

A swimlane view of two employees booking at the same instant, showing where one waits.

```mermaid
flowchart TD
    subgraph lane1["🧑 Thread 1 — Alice"]
        A1(["Build TimeSlot 09:00-10:00"])
        A2["Call scheduleMeeting(cap=8)"]
        A3["🔒 ACQUIRES the monitor"]
        A4["Scan rooms → Everest qualifies"]
        A5["Strategy picks Everest"]
        A6["Assign MTG-1<br/>add to meetings + roomMeetings"]
        A7["🔓 RELEASES the monitor"]
        A8["Notify observers"]
        A9(["✅ Returns MTG-1"])
        A1 --> A2 --> A3 --> A4 --> A5 --> A6 --> A7 --> A8 --> A9
    end

    subgraph lane2["🧑 Thread 2 — Bob"]
        B1(["Build TimeSlot 09:30-10:30"])
        B2["Call scheduleMeeting(cap=8)"]
        B3["⏸️ BLOCKS — monitor is held"]
        B4["🔒 ACQUIRES the monitor"]
        B5["Scan rooms → Everest now<br/>has an overlapping meeting"]
        B6["availableRooms is empty"]
        B7["Strategy returns null"]
        B8["🔓 RELEASES the monitor"]
        B9(["❌ MeetingSchedulerException<br/>'No available room found'"])
        B1 --> B2 --> B3 --> B4 --> B5 --> B6 --> B7 --> B8 --> B9
    end

    A3 -.->|"Thread 2 must wait"| B3
    A7 -.->|"monitor handed over"| B4
    A6 -.->|"this write is what<br/>Thread 2 now observes"| B5

    style A9 fill:#e0ffe0,stroke:#0a0
    style B9 fill:#ffe0e0,stroke:#c00
    style B3 fill:#fff4d0,stroke:#c90
```

**The guarantee:** exactly one booking is confirmed. The loser is not silently dropped
and does not corrupt state — it receives a specific domain exception it can act on
(retry a different window, or widen the search).

---

## 4. Activity Diagram — Full Booking with Swimlanes

The same booking, but split by *which object does the work*.

```mermaid
flowchart TD
    subgraph client["Client"]
        C1(["Gather subject, organizer,<br/>participants, window, capacity"])
        C2(["Receive Meeting or exception"])
    end

    subgraph slot["TimeSlot"]
        T1{"endTime after<br/>startTime?"}
        T2["❌ throw — invalid slot"]
        T3["✅ immutable slot created"]
    end

    subgraph sched["MeetingScheduler"]
        S1["🔒 enter synchronized"]
        S2["Filter by capacity"]
        S3["Filter by overlap"]
        S4{"Any room<br/>qualifies?"}
        S5["❌ throw — no room available"]
        S6["Delegate to strategy"]
        S7["Generate MTG-n"]
        S8["Commit to meetings + roomMeetings"]
        S9["🔓 exit synchronized"]
    end

    subgraph strat["Strategy"]
        P1["Apply policy:<br/>first element, or min capacity"]
    end

    subgraph obs["Observers"]
        O1["Email: send invite"]
        O2["Calendar: create entry"]
        O3["Failures caught per-observer"]
    end

    C1 --> T1
    T1 -->|No| T2 --> C2
    T1 -->|Yes| T3 --> S1
    S1 --> S2 --> S3 --> S4
    S4 -->|No| S5 --> C2
    S4 -->|Yes| S6 --> P1
    P1 --> S7 --> S8 --> S9
    S9 --> O1
    S9 --> O2
    O1 --> O3
    O2 --> O3
    S9 --> C2

    style T2 fill:#ffe0e0,stroke:#c00
    style S5 fill:#ffe0e0,stroke:#c00
    style S8 fill:#e0ffe0,stroke:#0a0
```

Read the lanes as responsibilities: the client only *asks*, `TimeSlot` guards its own
validity, the scheduler owns the transaction, the strategy owns the choice, and the
observers own the side effects. No lane reaches into another's job — which is the
"modularity and separation of concerns" requirement made visible.

---

## 5. Deployment / Extension View

Where this design sits today, and what changes when it leaves a single JVM.

```mermaid
flowchart TB
    subgraph jvm["Single JVM — the current implementation"]
        SINGLETON["MeetingScheduler singleton"]
        MAPS[("In-memory ConcurrentHashMaps")]
        MONITOR["Object monitor —<br/>synchronized methods"]
        SINGLETON --- MAPS
        SINGLETON --- MONITOR
    end

    subgraph future["What a multi-node deployment would need"]
        DB[("Shared database<br/>rooms · meetings")]
        LOCK["Distributed lock or<br/>DB exclusion constraint"]
        QUEUE["Message queue<br/>for notifications"]
    end

    jvm -.->|"scale out"| future

    NOTE["The singleton's monitor only serializes<br/>threads in ONE process. Across nodes,<br/>the check-then-act race returns and needs<br/>a DB-level guarantee."]
    future --- NOTE

    style jvm fill:#f0f8ff,stroke:#4a90d9
    style future fill:#faf0f0,stroke:#d98080
    style NOTE fill:#fffce0,stroke:#c90
```

| Concern | Single JVM (today) | Multi-node (what would change) |
|---|---|---|
| Mutual exclusion | `synchronized` on the scheduler | Distributed lock, or a range-exclusion constraint in the DB |
| State | `ConcurrentHashMap` | Shared relational store |
| Singleton | One instance per JVM | One instance *per node* — no longer globally unique |
| Notifications | In-process observer loop | Durable queue, so a crash does not lose the event |

This is the honest answer to "does your design scale?" — the in-process lock is correct
for the stated problem, and the table names exactly what breaks if the requirement
changes.
