# Meeting Room Scheduler — Class Diagram

> Structural view of the system. Every field, method and relationship below mirrors the
> Java sources in [`../solution/`](../solution/).

---

## 1. Full Class Diagram

```mermaid
classDiagram
    direction TB

    class MeetingScheduler {
        -MeetingScheduler instance$
        -Object lock$
        -ConcurrentHashMap~String, Room~ rooms
        -ConcurrentHashMap~String, Meeting~ meetings
        -ConcurrentHashMap~String, List~ roomMeetings
        -CopyOnWriteArrayList~MeetingObserver~ observers
        -RoomSelectionStrategy roomSelectionStrategy
        -AtomicInteger meetingCounter
        -MeetingScheduler()
        +getInstance()$ MeetingScheduler
        +addRoom(Room room) void
        +scheduleMeeting(String subject, User organizer, List~User~ participants, TimeSlot slot, int requiredCapacity) Meeting
        +cancelMeeting(String meetingId) void
        +getAvailableRooms(TimeSlot slot, int requiredCapacity) List~Room~
        +setRoomSelectionStrategy(RoomSelectionStrategy strategy) void
        +addObserver(MeetingObserver observer) void
        +removeObserver(MeetingObserver observer) void
        -notifyMeetingScheduled(Meeting meeting) void
        -notifyMeetingCancelled(Meeting meeting) void
    }

    class Meeting {
        -String id
        -String subject
        -User organizer
        -List~User~ participants
        -Room room
        -TimeSlot timeSlot
        -MeetingStatus status
        +Meeting(String id, String subject, User organizer, List~User~ participants, Room room, TimeSlot timeSlot)
        +getId() String
        +getSubject() String
        +getOrganizer() User
        +getParticipants() List~User~
        +getRoom() Room
        +getTimeSlot() TimeSlot
        +getStatus() MeetingStatus
        +cancel() void
        +complete() void
        +toString() String
    }

    class Room {
        -String id
        -String name
        -RoomType roomType
        -int capacity
        +Room(String id, String name, RoomType roomType, int capacity)
        +getId() String
        +getName() String
        +getCapacity() int
        +getRoomType() RoomType
        +toString() String
    }

    class TimeSlot {
        -LocalDateTime startTime
        -LocalDateTime endTime
        +TimeSlot(LocalDateTime startTime, LocalDateTime endTime)
        +getStartTime() LocalDateTime
        +getEndTime() LocalDateTime
        +overlaps(TimeSlot other) boolean
        +toString() String
    }

    class User {
        -String id
        -String name
        -String email
        +User(String id, String name, String email)
        +getId() String
        +getName() String
        +getEmail() String
        +toString() String
    }

    class RoomSelectionStrategy {
        <<interface>>
        +selectRoom(List~Room~ availableRooms, int requiredCapacity) Room
    }

    class FirstAvailableStrategy {
        +selectRoom(List~Room~ availableRooms, int requiredCapacity) Room
    }

    class BestFitStrategy {
        +selectRoom(List~Room~ availableRooms, int requiredCapacity) Room
    }

    class MeetingObserver {
        <<interface>>
        +onMeetingScheduled(Meeting meeting) void
        +onMeetingCancelled(Meeting meeting) void
    }

    class EmailNotificationObserver {
        +onMeetingScheduled(Meeting meeting) void
        +onMeetingCancelled(Meeting meeting) void
    }

    class CalendarNotificationObserver {
        +onMeetingScheduled(Meeting meeting) void
        +onMeetingCancelled(Meeting meeting) void
    }

    class MeetingStatus {
        <<enumeration>>
        SCHEDULED
        CANCELLED
        COMPLETED
    }

    class RoomType {
        <<enumeration>>
        CONFERENCE
        BOARD_ROOM
        HUDDLE_SPACE
    }

    class MeetingSchedulerException {
        <<RuntimeException>>
        +MeetingSchedulerException(String message)
    }

    class MeetingSchedulerDemo {
        +main(String[] args)$ void
    }

    MeetingScheduler o-- Room : registers (aggregation)
    MeetingScheduler *-- Meeting : owns bookings (composition)
    MeetingScheduler --> RoomSelectionStrategy : delegates room choice
    MeetingScheduler --> MeetingObserver : notifies
    MeetingScheduler ..> MeetingSchedulerException : throws

    RoomSelectionStrategy <|.. FirstAvailableStrategy : implements
    RoomSelectionStrategy <|.. BestFitStrategy : implements
    RoomSelectionStrategy ..> Room : selects from

    MeetingObserver <|.. EmailNotificationObserver : implements
    MeetingObserver <|.. CalendarNotificationObserver : implements
    MeetingObserver ..> Meeting : reads

    Meeting --> Room : booked in
    Meeting *-- TimeSlot : composition
    Meeting --> User : organizer and participants
    Meeting --> MeetingStatus : current state
    Meeting ..> MeetingSchedulerException : throws on illegal transition

    Room --> RoomType : classified as

    TimeSlot ..> MeetingSchedulerException : throws on inverted range

    MeetingSchedulerDemo ..> MeetingScheduler : uses singleton
    MeetingSchedulerDemo ..> FirstAvailableStrategy : creates and injects
    MeetingSchedulerDemo ..> BestFitStrategy : creates and injects
```

> **Note on `roomMeetings`** — its true type is
> `ConcurrentHashMap<String, List<Meeting>>`. Mermaid's `~...~` generic syntax does not
> nest, so the inner `<Meeting>` is elided in the box above.

---

## 2. Layered / Package View

How the classes group into packages, and which direction the dependencies point.
Note that every arrow points **inward or downward** — nothing in `models` knows about
the scheduler, which is what keeps the domain testable in isolation.

```mermaid
flowchart TB
    subgraph client["Client Layer"]
        DEMO["MeetingSchedulerDemo"]
    end

    subgraph orchestration["Orchestration Layer — solution"]
        SCHED["MeetingScheduler<br/>(Singleton)"]
    end

    subgraph policy["Policy Layer — strategies"]
        RSS["RoomSelectionStrategy<br/>(interface)"]
        FAS["FirstAvailableStrategy"]
        BFS["BestFitStrategy"]
    end

    subgraph notify["Notification Layer — observers"]
        MO["MeetingObserver<br/>(interface)"]
        EMAIL["EmailNotificationObserver"]
        CAL["CalendarNotificationObserver"]
    end

    subgraph domain["Domain Layer — models / enums"]
        MEET["Meeting"]
        ROOM["Room"]
        SLOT["TimeSlot"]
        USER["User"]
        MS["MeetingStatus"]
        RT["RoomType"]
    end

    subgraph errors["Error Layer — exceptions"]
        EX["MeetingSchedulerException"]
    end

    DEMO --> SCHED
    SCHED --> RSS
    SCHED --> MO
    SCHED --> MEET
    SCHED --> ROOM
    RSS -.-> FAS
    RSS -.-> BFS
    MO -.-> EMAIL
    MO -.-> CAL
    FAS --> ROOM
    BFS --> ROOM
    EMAIL --> MEET
    CAL --> MEET
    MEET --> ROOM
    MEET --> SLOT
    MEET --> USER
    MEET --> MS
    ROOM --> RT
    SCHED -.throws.-> EX
    MEET -.throws.-> EX
    SLOT -.throws.-> EX
```

---

## 3. Design-Pattern Overlay

The same classes, grouped by the pattern each one plays a role in.

```mermaid
flowchart LR
    subgraph singleton["① SINGLETON"]
        direction TB
        S1["MeetingScheduler"]
        S2["private constructor<br/>volatile instance<br/>double-checked locking"]
        S1 --- S2
    end

    subgraph strategy["② STRATEGY"]
        direction TB
        ST1["Context:<br/>MeetingScheduler"]
        ST2["Strategy:<br/>RoomSelectionStrategy"]
        ST3["Concrete:<br/>FirstAvailable / BestFit"]
        ST1 --> ST2
        ST2 --> ST3
    end

    subgraph observer["③ OBSERVER"]
        direction TB
        O1["Subject:<br/>MeetingScheduler"]
        O2["Observer:<br/>MeetingObserver"]
        O3["Concrete:<br/>Email / Calendar"]
        O1 --> O2
        O2 --> O3
    end

    singleton -.->|"one instance owns"| strategy
    singleton -.->|"one instance notifies"| observer
```

| Pattern | Role in this system | Swap without touching the scheduler? |
|---|---|---|
| **Singleton** | One global point of control over rooms + bookings | — |
| **Strategy** | Makes the room-choice policy interchangeable at runtime | ✅ add a class implementing `RoomSelectionStrategy` |
| **Observer** | Decouples booking from notification channels | ✅ add a class implementing `MeetingObserver` |

---

## 4. Relationship Notation Cheatsheet

| Mermaid | UML meaning | Example here | Why |
|---|---|---|---|
| `*--` | Composition — the part dies with the whole | `Meeting *-- TimeSlot` | A `TimeSlot` has no identity outside its meeting |
| `o--` | Aggregation — the part outlives the whole | `MeetingScheduler o-- Room` | Rooms are physical; they exist whether or not the scheduler does |
| `-->` | Association — holds a reference | `Meeting --> Room` | A meeting points at the room it booked |
| `<\|..` | Realization — implements an interface | `RoomSelectionStrategy <\|.. BestFitStrategy` | Strategy / Observer implementations |
| `..>` | Dependency — uses transiently | `MeetingScheduler ..> MeetingSchedulerException` | Thrown, never stored |
