# Meeting Room Scheduler — Entity Relationship Diagram

> The **data** view: what is stored, with what attributes, and how the records relate.
> Where the class diagram shows behaviour, this shows shape — it is the diagram you
> would hand to someone asked to persist this system in a database.

---

## 1. Core ER Diagram

```mermaid
erDiagram
    USER {
        string id PK "e.g. U1"
        string name "Display name"
        string email "Notification address"
    }

    ROOM {
        string id PK "e.g. R1 — unique room id"
        string name "Display name, e.g. Everest"
        enum roomType "CONFERENCE | BOARD_ROOM | HUDDLE_SPACE"
        int capacity "Max seats — compared against requiredCapacity"
    }

    MEETING {
        string id PK "System generated, MTG-n"
        string subject "What the meeting is about"
        string organizerId FK "The User who booked it"
        string roomId FK "The Room assigned by the strategy"
        enum status "SCHEDULED | CANCELLED | COMPLETED"
    }

    TIME_SLOT {
        datetime startTime "Inclusive start"
        datetime endTime "Exclusive end — must be after startTime"
    }

    PARTICIPATION {
        string meetingId FK "Part of composite key"
        string userId FK "Part of composite key"
    }

    ROOM_TYPE {
        string name PK "CONFERENCE | BOARD_ROOM | HUDDLE_SPACE"
        int minCapacity "6 | 10 | 2"
        int maxCapacity "20 | 30 | 4"
    }

    MEETING_STATUS {
        string name PK "SCHEDULED | CANCELLED | COMPLETED"
        boolean blocksRoom "true only while SCHEDULED"
    }

    USER      ||--o{ MEETING       : "organizes"
    USER      ||--o{ PARTICIPATION : "is invited via"
    MEETING   ||--o{ PARTICIPATION : "has attendees via"
    ROOM      ||--o{ MEETING       : "hosts (never overlapping)"
    MEETING   ||--|| TIME_SLOT     : "occupies exactly one"
    ROOM      }o--|| ROOM_TYPE     : "is classified as"
    MEETING   }o--|| MEETING_STATUS: "is currently in"
```

**Reading the crow's feet**

| Relationship | Cardinality | Meaning |
|---|---|---|
| `USER ||--o{ MEETING` | one-to-many | One user organizes zero or more meetings; each meeting has exactly one organizer |
| `ROOM ||--o{ MEETING` | one-to-many | One room hosts many meetings **over time** — but never two that overlap |
| `MEETING ||--|| TIME_SLOT` | one-to-one | `TimeSlot` is a value object owned by the meeting; it is `final` and never shared |
| `MEETING ||--o{ PARTICIPATION }o--|| USER` | many-to-many | Resolved through a junction table, since a meeting has many participants and a user attends many meetings |

---

## 2. In-Memory Storage Map

The ER diagram above is the *logical* model. This is how the running Java program
actually holds it — three `ConcurrentHashMap`s inside the singleton.

```mermaid
erDiagram
    MEETING_SCHEDULER {
        map rooms "ConcurrentHashMap roomId to Room"
        map meetings "ConcurrentHashMap meetingId to Meeting"
        map roomMeetings "ConcurrentHashMap roomId to List of active Meeting"
        list observers "CopyOnWriteArrayList of MeetingObserver"
        object roomSelectionStrategy "The active RoomSelectionStrategy"
        int meetingCounter "AtomicInteger — source of the MTG-n suffix"
    }

    ROOMS_MAP {
        string key PK "roomId"
        object value "Room"
    }

    MEETINGS_MAP {
        string key PK "meetingId"
        object value "Meeting — includes CANCELLED and COMPLETED"
    }

    ROOM_MEETINGS_MAP {
        string key PK "roomId"
        list value "Active meetings only — the conflict-check index"
    }

    MEETING_SCHEDULER ||--|| ROOMS_MAP         : "rooms"
    MEETING_SCHEDULER ||--|| MEETINGS_MAP      : "meetings"
    MEETING_SCHEDULER ||--|| ROOM_MEETINGS_MAP : "roomMeetings"
    ROOMS_MAP         ||--o{ ROOM_MEETINGS_MAP : "one bucket per registered room"
    MEETINGS_MAP      ||--o{ ROOM_MEETINGS_MAP : "an active meeting appears in both"
```

### The key invariant

There are **two** collections of meetings, and they deliberately disagree:

| | `meetings` | `roomMeetings` |
|---|---|---|
| Keyed by | meeting id | room id |
| Holds | **every** meeting ever created | only **active** (`SCHEDULED`) meetings |
| Purpose | lookup by id for `cancelMeeting` | the index scanned by `getAvailableRooms` |
| On cancel | record **stays** (audit trail) | entry is **removed** (frees the slot) |

That split is what makes requirement 7 work: cancelling removes the meeting from
`roomMeetings` so the window becomes bookable again, while `meetings` retains the
cancelled record so `cancelMeeting` on the same id can still find it and correctly
reject the second attempt.

---

## 3. Conflict Rule — The Constraint Behind the Model

No database constraint can express "no overlapping bookings per room" with a simple
`UNIQUE`; it needs an exclusion constraint over a range. The in-memory equivalent is
the `overlaps` scan:

```mermaid
flowchart LR
    A["Two meetings<br/>in the SAME room"] --> B{"start1 &lt; end2<br/>AND<br/>start2 &lt; end1"}
    B -- true --> C["❌ CONFLICT<br/>room is not available"]
    B -- false --> D["✅ COMPATIBLE<br/>back-to-back is allowed"]
```

| Meeting A | Meeting B | `overlaps`? | Verdict |
|---|---|---|---|
| 09:00–10:00 | 09:30–10:30 | `09:00 < 10:30 && 09:30 < 10:00` → true | Conflict |
| 09:00–10:00 | 10:00–11:00 | `09:00 < 11:00 && 10:00 < 10:00` → **false** | Back-to-back, allowed |
| 09:00–10:00 | 11:00–12:00 | `09:00 < 12:00 && 11:00 < 10:00` → false | No conflict |
| 09:00–12:00 | 10:00–11:00 | `09:00 < 11:00 && 10:00 < 12:00` → true | Conflict (fully contained) |

Because the comparison is strict (`<`, not `<=`), an end time that equals the next
start time is **not** an overlap — which is exactly the "back-to-back bookings are
allowed" rule from the problem statement.
