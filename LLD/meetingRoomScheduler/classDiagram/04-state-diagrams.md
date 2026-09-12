# Meeting Room Scheduler — State Diagrams

> The **lifecycle** view: every legal state, every legal transition, and — just as
> importantly — the transitions the code actively rejects.

---

## 1. Meeting Lifecycle

The state machine enforced by `Meeting.cancel()` and `Meeting.complete()`.

```mermaid
stateDiagram-v2
    [*] --> SCHEDULED : new Meeting(...)<br/>room claimed, id MTG-n assigned

    SCHEDULED --> CANCELLED : cancel()<br/>releases the room
    SCHEDULED --> COMPLETED : complete()<br/>meeting time has passed

    CANCELLED --> [*]
    COMPLETED --> [*]

    note right of SCHEDULED
        The ONLY state that blocks a room.
        While here, the meeting sits in
        roomMeetings[roomId] and causes
        overlap conflicts.
    end note

    note right of CANCELLED
        Terminal. Removed from roomMeetings
        so the window is bookable again,
        but retained in `meetings` as an
        audit record.
    end note

    note right of COMPLETED
        Terminal. Reached only via
        Meeting.complete() — the scheduler
        exposes no completeMeeting(id) today.
    end note
```

### Rejected transitions

Every arrow **not** drawn above throws `MeetingSchedulerException`. Both guards are
the same shape: *if the current status is not `SCHEDULED`, refuse.*

```mermaid
stateDiagram-v2
    direction LR

    state "SCHEDULED" as S
    state "CANCELLED" as C
    state "COMPLETED" as D
    state "❌ MeetingSchedulerException" as E

    S --> C : cancel() ✅
    S --> D : complete() ✅

    C --> E : cancel() again
    C --> E : complete()
    D --> E : cancel()
    D --> E : complete() again

    note left of E
        "Can only cancel SCHEDULED meetings.
         Current status is: CANCELLED"

        Terminal states are absorbing —
        nothing leaves them.
    end note
```

| From | Action | Result |
|---|---|---|
| `SCHEDULED` | `cancel()` | → `CANCELLED`, room released |
| `SCHEDULED` | `complete()` | → `COMPLETED` |
| `CANCELLED` | `cancel()` | ❌ `MeetingSchedulerException` |
| `CANCELLED` | `complete()` | ❌ `MeetingSchedulerException` |
| `COMPLETED` | `cancel()` | ❌ `MeetingSchedulerException` |
| `COMPLETED` | `complete()` | ❌ `MeetingSchedulerException` |

**Why fail loudly instead of silently ignoring?** A silent no-op on double-cancel would
let the caller believe it freed a room it never held, and would fire a second
`onMeetingCancelled` notification to every observer. Fail-fast keeps the notification
stream truthful.

---

## 2. Room Occupancy (per time slot)

A room has no `status` field — its availability is *derived* by scanning
`roomMeetings`. This is that derived state machine, evaluated per requested window.

```mermaid
stateDiagram-v2
    [*] --> UNREGISTERED

    UNREGISTERED --> FREE : addRoom(room)<br/>roomMeetings[id] = []

    FREE --> TOO_SMALL : query arrives with<br/>requiredCapacity > capacity
    TOO_SMALL --> FREE : a later query with<br/>a capacity it can satisfy

    FREE --> BOOKED : scheduleMeeting<br/>selects this room
    BOOKED --> FREE : cancelMeeting<br/>removes the overlapping meeting

    BOOKED --> BOOKED : another booking in a<br/>NON-overlapping window

    note right of FREE
        Qualifies: capacity >= required
        AND no active meeting overlaps
        the requested slot.
    end note

    note right of BOOKED
        For THIS slot only. The same room
        can be simultaneously BOOKED for
        09:00-10:00 and FREE for 11:00-12:00.
    end note

    note right of TOO_SMALL
        Not a stored state — just a
        capacity filter miss. Included
        because it is the other way a
        room drops out of the result.
    end note
```

The important subtlety: **availability is a function of `(room, timeSlot)`, not of the
room alone.** A room is never globally "busy"; it is busy *for a window*.

---

## 3. Singleton Initialization

The double-checked locking sequence, as a state machine over the `volatile instance`
field.

```mermaid
stateDiagram-v2
    [*] --> NULL : class loaded<br/>instance = null

    NULL --> CHECKING : getInstance() called

    CHECKING --> READY : instance != null<br/>(fast path — no lock taken)
    CHECKING --> LOCKING : instance == null

    LOCKING --> CONSTRUCTING : monitor on `lock` acquired<br/>AND second check still null
    LOCKING --> READY : second check found non-null<br/>(another thread won the race)

    CONSTRUCTING --> READY : new MeetingScheduler()<br/>volatile write publishes it safely

    READY --> [*] : instance returned

    note right of CONSTRUCTING
        Initializes: rooms, meetings,
        roomMeetings, observers,
        meetingCounter, and the default
        FirstAvailableStrategy.
    end note

    note right of READY
        `volatile` guarantees no thread can
        observe a partially constructed
        object — without it, the reference
        can be published before the
        constructor's writes are visible.
    end note
```

### Why both checks?

| Check | Purpose | Cost |
|---|---|---|
| **First** (outside `synchronized`) | Fast path for the 99.99% of calls after init | No lock — just a volatile read |
| **Second** (inside `synchronized`) | Correctness: two threads can both pass the first check | Lock held, but only during the rare init |

Dropping the first check makes every call pay for a lock. Dropping the second allows
two instances. Dropping `volatile` allows a half-built object to escape.

---

## 4. Booking Request Lifecycle

One request's journey through the system, from the caller's point of view.

```mermaid
stateDiagram-v2
    [*] --> VALIDATING : scheduleMeeting(...)

    VALIDATING --> REJECTED_BAD_SLOT : TimeSlot ctor —<br/>endTime not after startTime
    VALIDATING --> SEARCHING : slot is valid

    SEARCHING --> NO_CANDIDATES : every room too small<br/>or already overlapping
    SEARCHING --> SELECTING : one or more rooms qualify

    NO_CANDIDATES --> REJECTED_NO_ROOM : strategy returns null

    SELECTING --> CLAIMING : strategy picked a room

    CLAIMING --> CONFIRMED : id assigned, both maps updated

    CONFIRMED --> NOTIFYING : notifyMeetingScheduled
    NOTIFYING --> CONFIRMED : observer failures<br/>caught and logged

    CONFIRMED --> [*] : Meeting returned
    REJECTED_BAD_SLOT --> [*] : MeetingSchedulerException
    REJECTED_NO_ROOM --> [*] : MeetingSchedulerException

    note right of CLAIMING
        SEARCHING → SELECTING → CLAIMING all
        run inside ONE synchronized block.
        Splitting them reopens the
        double-booking race.
    end note

    note right of NOTIFYING
        Past the point of no return. The
        booking is already committed; a
        failing observer cannot undo it.
    end note
```
