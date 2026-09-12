# Meeting Room Scheduler — Sequence Diagrams

> The **interaction** view: who calls whom, in what order, for each significant flow.
> Seven flows, from the happy path through to the concurrency race the whole design
> exists to prevent.

---

## Flow 1 — Schedule a Meeting (Happy Path)

The core booking transaction. Note the `synchronized` block spans the entire
check-then-act sequence — that is the whole point.

```mermaid
sequenceDiagram
    actor Alice
    participant Demo as MeetingSchedulerDemo
    participant S as MeetingScheduler
    participant Strat as RoomSelectionStrategy
    participant R as Room
    participant TS as TimeSlot
    participant M as Meeting
    participant Obs as MeetingObserver(s)

    Alice->>Demo: wants a room, 09:00-10:00, 3 people
    Demo->>TS: new TimeSlot(09:00, 10:00)
    TS->>TS: validate endTime.isAfter(startTime)
    TS-->>Demo: slot

    Demo->>S: scheduleMeeting("Sprint Planning", alice, [bob, charlie], slot, 3)
    activate S
    Note over S: 🔒 enters synchronized method

    S->>S: getAvailableRooms(slot, 3)
    activate S
    loop for each registered Room
        S->>R: getCapacity()
        R-->>S: capacity
        alt capacity < requiredCapacity
            Note over S: skip this room
        else capacity is sufficient
            S->>S: look up roomMeetings[roomId]
            loop for each active meeting in that room
                S->>TS: existingSlot.overlaps(slot)
                TS-->>S: true / false
            end
            alt no overlap found
                Note over S: add room to available list
            end
        end
    end
    S-->>S: List~Room~ availableRooms
    deactivate S

    S->>Strat: selectRoom(availableRooms, 3)
    Strat-->>S: selectedRoom

    S->>S: meetingCounter.incrementAndGet()
    S->>M: new Meeting("MTG-1", subject, organizer, participants, room, slot)
    M->>M: status = SCHEDULED
    M-->>S: meeting

    S->>S: meetings.put("MTG-1", meeting)
    S->>S: roomMeetings[roomId].add(meeting)
    Note over S: 🔓 room is now claimed — no other thread<br/>could have observed it free

    S->>Obs: notifyMeetingScheduled(meeting)
    activate Obs
    Obs-->>S: (side effect only, failures swallowed)
    deactivate Obs

    S-->>Demo: meeting
    deactivate S
    Demo-->>Alice: "Scheduled: MTG-1 in Everest"
```

---

## Flow 2 — Schedule Rejected (No Room Available)

```mermaid
sequenceDiagram
    actor Bob
    participant S as MeetingScheduler
    participant Strat as RoomSelectionStrategy
    participant Obs as MeetingObserver(s)

    Bob->>S: scheduleMeeting(subject, bob, participants, slot, 50)
    activate S
    Note over S: 🔒 synchronized

    S->>S: getAvailableRooms(slot, 50)
    Note over S: every room fails the capacity test,<br/>or every big room already has an overlap
    S-->>S: [] (empty list)

    S->>Strat: selectRoom([], 50)
    Strat-->>S: null

    S->>S: selectedRoom == null
    Note over S: ❌ fail fast — nothing is mutated
    S--xBob: throw MeetingSchedulerException<br/>"No available room found for capacity 50 during ..."
    deactivate S

    Note over Obs: observers are NOT notified —<br/>notification happens only after a successful booking
```

**What did *not* happen:** the counter was not incremented, no `Meeting` was created,
neither map was touched, and no observer fired. The failure leaves zero residue.

---

## Flow 3 — Cancel a Meeting

```mermaid
sequenceDiagram
    actor Alice
    participant S as MeetingScheduler
    participant M as Meeting
    participant Obs as MeetingObserver(s)

    Alice->>S: cancelMeeting("MTG-1")
    activate S
    Note over S: 🔒 synchronized

    S->>S: meetings.get("MTG-1")
    alt meeting not found
        S--xAlice: throw MeetingSchedulerException<br/>"Meeting not found: MTG-1"
    else found
        S->>M: cancel()
        activate M
        alt status != SCHEDULED
            M--xS: throw MeetingSchedulerException<br/>"Can only cancel SCHEDULED meetings..."
            Note over S: exception propagates — the room is<br/>NOT released, state stays consistent
        else status == SCHEDULED
            M->>M: status = CANCELLED
            M-->>S: ok
        end
        deactivate M

        S->>S: roomMeetings[roomId].remove(meeting)
        Note over S: ✅ the window is free again —<br/>the record stays in `meetings` as an audit trail

        S->>Obs: notifyMeetingCancelled(meeting)
        S-->>Alice: void
    end
    deactivate S
```

**Ordering matters.** `cancel()` is called *before* the room is released. If the state
machine rejects the transition, the exception propagates and the `remove` never runs —
so a double-cancel cannot free a room twice.

---

## Flow 4 — Room Availability Lookup

The read-only query, shown on its own because it is the cost centre of the design.

```mermaid
sequenceDiagram
    actor User
    participant S as MeetingScheduler
    participant R as Room
    participant TS as TimeSlot

    User->>S: getAvailableRooms(slot, requiredCapacity)
    activate S
    Note over S: 🔒 synchronized — a consistent snapshot

    loop for each room in rooms.values()
        S->>R: getCapacity()
        R-->>S: capacity

        alt capacity < requiredCapacity
            Note over S: ⏭️ skip — too small
        else
            S->>S: roomMeetings.getOrDefault(roomId, [])
            loop for each active meeting
                S->>TS: overlaps(slot)
                TS-->>S: boolean
            end
            alt any overlap
                Note over S: ⏭️ skip — busy
            else no overlap
                Note over S: ✅ add to result
            end
        end
    end

    S-->>User: List~Room~
    deactivate S

    Note over S,TS: Cost = O(rooms × active meetings per room).<br/>Cancelled meetings are removed from roomMeetings,<br/>so they never inflate this scan.
```

---

## Flow 5 — The Concurrency Race (Why `synchronized` Is Not Optional)

Two employees hit "book" at the same instant, and only one room qualifies.

### ❌ Without the lock — the bug

```mermaid
sequenceDiagram
    participant T1 as Thread 1 (Alice)
    participant S as MeetingScheduler
    participant T2 as Thread 2 (Bob)

    T1->>S: getAvailableRooms(slot, 8)
    S-->>T1: [Everest]
    Note over T1,T2: ⚠️ interleaving point

    T2->>S: getAvailableRooms(slot, 8)
    S-->>T2: [Everest]
    Note over T2: Everest still looks free —<br/>Thread 1 has not claimed it yet

    T1->>S: claim Everest → MTG-1
    T2->>S: claim Everest → MTG-2

    Note over S: 💥 DOUBLE BOOKING —<br/>two confirmed meetings, same room,<br/>overlapping windows
```

### ✅ With `synchronized scheduleMeeting` — the fix

```mermaid
sequenceDiagram
    participant T1 as Thread 1 (Alice)
    participant S as MeetingScheduler
    participant T2 as Thread 2 (Bob)

    T1->>S: scheduleMeeting(..., slot, 8)
    activate S
    Note over S: 🔒 monitor acquired by Thread 1

    T2->>S: scheduleMeeting(..., slot, 8)
    Note over T2: ⏸️ BLOCKED — waiting on the monitor

    S->>S: getAvailableRooms → [Everest]
    S->>S: selectRoom → Everest
    S->>S: roomMeetings[Everest].add(MTG-1)
    S-->>T1: MTG-1
    Note over S: 🔓 monitor released
    deactivate S

    activate S
    Note over S: 🔒 monitor acquired by Thread 2
    S->>S: getAvailableRooms → []
    Note over S: Everest now has an overlapping meeting
    S->>S: selectRoom([]) → null
    S--xT2: throw MeetingSchedulerException<br/>"No available room found"
    Note over S: 🔓 monitor released
    deactivate S

    Note over T1,T2: ✅ Exactly one booking succeeds.<br/>The loser gets a clean domain error, not corrupt state.
```

The critical section is **find → select → claim**, all three. Locking only the `add`
would not help: both threads would already be holding a stale "Everest is free" answer.

---

## Flow 6 — Observer Failure Isolation

A broken notification channel must not roll back a valid booking.

```mermaid
sequenceDiagram
    participant S as MeetingScheduler
    participant O1 as EmailObserver (throws)
    participant O2 as CalendarObserver (healthy)
    actor Alice

    Note over S: booking already committed to both maps

    S->>S: notifyMeetingScheduled(meeting)
    activate S

    loop for each observer in CopyOnWriteArrayList
        S->>O1: onMeetingScheduled(meeting)
        activate O1
        O1--xS: RuntimeException("SMTP down")
        deactivate O1
        Note over S: caught — logged to stderr,<br/>loop continues

        S->>O2: onMeetingScheduled(meeting)
        activate O2
        O2-->>S: ok — calendar entry created
        deactivate O2
    end

    deactivate S
    S-->>Alice: ✅ meeting returned successfully

    Note over S,Alice: Notification is a side effect, not part of<br/>the booking transaction. One bad channel<br/>cannot fail the booking or starve the others.
```

Iteration is over a `CopyOnWriteArrayList`, so an observer that calls `removeObserver`
from inside its own callback cannot cause a `ConcurrentModificationException`.

---

## Flow 7 — Runtime Strategy Swap

```mermaid
sequenceDiagram
    actor Admin
    participant S as MeetingScheduler
    participant FA as FirstAvailableStrategy
    participant BF as BestFitStrategy

    Note over S: default wired in the private constructor
    S->>FA: (held as roomSelectionStrategy)

    Admin->>S: scheduleMeeting(..., requiredCapacity=2)
    S->>FA: selectRoom([Everest(10), Alps(20), Nook(4)], 2)
    FA-->>S: Everest — simply the first element
    Note over S: a 10-seat room burned on a 2-person sync

    Admin->>S: setRoomSelectionStrategy(new BestFitStrategy())
    S->>S: roomSelectionStrategy = BF
    Note over S: 🔄 swapped at runtime — no restart,<br/>no change to MeetingScheduler's code

    Admin->>S: scheduleMeeting(..., requiredCapacity=2)
    S->>BF: selectRoom([Everest(10), Alps(20), Nook(4)], 2)
    BF->>BF: min by capacity
    BF-->>S: Nook(4) — the tightest fit
    Note over S: ✅ large rooms stay free for meetings<br/>that actually need them
```

Adding a third policy — say `SameFloorStrategy` — means writing one new class. The
scheduler is never edited. That is the Open/Closed Principle paying rent.
