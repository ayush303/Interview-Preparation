# Meeting Room Scheduler — Flowcharts

> The **algorithm** view: the decision logic inside each method, branch by branch.
> Where the sequence diagrams show *who talks to whom*, these show *what the code
> decides*.

---

## 1. Master Flow — End to End

Everything the system does, in one picture.

```mermaid
flowchart TD
    START(["Application starts"]) --> INIT["MeetingScheduler.getInstance()<br/>double-checked locking"]
    INIT --> REG["addObserver(Email)<br/>addObserver(Calendar)"]
    REG --> ROOMS["addRoom(Everest, Alps, Nook)<br/>roomMeetings bucket created per room"]
    ROOMS --> STRAT["setRoomSelectionStrategy(...)<br/>default is FirstAvailable"]
    STRAT --> WAIT{"What does the<br/>caller want?"}

    WAIT -->|"Book a room"| SCHED["scheduleMeeting(...)"]
    WAIT -->|"Cancel a booking"| CANCEL["cancelMeeting(id)"]
    WAIT -->|"Just look"| LOOKUP["getAvailableRooms(slot, cap)"]
    WAIT -->|"Change policy"| SWAP["setRoomSelectionStrategy(...)"]

    SCHED --> SOK{"Room<br/>found?"}
    SOK -->|Yes| SYES["✅ Meeting MTG-n returned<br/>observers notified"]
    SOK -->|No| SNO["❌ MeetingSchedulerException"]

    CANCEL --> COK{"Exists AND<br/>SCHEDULED?"}
    COK -->|Yes| CYES["✅ status = CANCELLED<br/>room released<br/>observers notified"]
    COK -->|No| CNO["❌ MeetingSchedulerException"]

    LOOKUP --> LRES["List of qualifying rooms<br/>(may be empty)"]
    SWAP --> SWOK["Next booking uses the new policy"]

    SYES --> WAIT
    SNO --> WAIT
    CYES --> WAIT
    CNO --> WAIT
    LRES --> WAIT
    SWOK --> WAIT
```

---

## 2. `scheduleMeeting` — The Booking Algorithm

```mermaid
flowchart TD
    A(["scheduleMeeting(subject, organizer,<br/>participants, timeSlot, requiredCapacity)"]) --> LOCK["🔒 acquire the scheduler monitor<br/>(synchronized method)"]

    LOCK --> B["availableRooms =<br/>getAvailableRooms(timeSlot, requiredCapacity)"]
    B --> C["selectedRoom =<br/>strategy.selectRoom(availableRooms, requiredCapacity)"]

    C --> D{"selectedRoom<br/>== null?"}
    D -->|Yes| E["❌ throw MeetingSchedulerException<br/>'No available room found for capacity N'"]
    D -->|No| F["id = 'MTG-' + meetingCounter.incrementAndGet()"]

    F --> G["meeting = new Meeting(id, subject, organizer,<br/>participants, selectedRoom, timeSlot)<br/>status = SCHEDULED"]
    G --> H["meetings.put(id, meeting)"]
    H --> I["roomMeetings[selectedRoom.id].add(meeting)<br/>🎯 the room is now claimed"]

    I --> UNLOCK["🔓 all mutation complete"]
    UNLOCK --> J["notifyMeetingScheduled(meeting)"]
    J --> K(["return meeting"])

    E --> X(["caller sees the exception<br/>— no state was mutated"])

    style E fill:#ffe0e0,stroke:#c00
    style X fill:#ffe0e0,stroke:#c00
    style I fill:#e0ffe0,stroke:#0a0
    style K fill:#e0ffe0,stroke:#0a0
```

**The critical section** is steps B → I. If the lock were released anywhere between
"found a free room" and "claimed it", a second thread could slip in and claim the same
room. This is the classic *check-then-act* race, and a single `synchronized` on the
method closes it.

---

## 3. `getAvailableRooms` — The Two-Filter Scan

```mermaid
flowchart TD
    A(["getAvailableRooms(timeSlot, requiredCapacity)"]) --> B["available = new ArrayList()"]
    B --> C{"More rooms in<br/>rooms.values()?"}

    C -->|No| Z(["return available"])
    C -->|Yes| D["room = next room"]

    D --> E{"FILTER 1<br/>room.capacity >=<br/>requiredCapacity?"}
    E -->|"No — too small"| C
    E -->|Yes| F["existing = roomMeetings.getOrDefault(room.id, [])"]

    F --> G{"FILTER 2<br/>any m in existing where<br/>m.timeSlot.overlaps(timeSlot)?"}
    G -->|"Yes — busy"| C
    G -->|"No — free"| H["available.add(room)"]
    H --> C

    style E fill:#fff4d0,stroke:#c90
    style G fill:#fff4d0,stroke:#c90
    style Z fill:#e0ffe0,stroke:#0a0
```

| | Filter 1 — Capacity | Filter 2 — Conflict |
|---|---|---|
| Test | `room.getCapacity() >= requiredCapacity` | no active meeting overlaps the window |
| Cost | O(1) per room | O(active meetings in that room) |
| Cheap first? | ✅ yes — runs before the expensive scan | — |

**Total cost:** `O(rooms × active meetings per room)`. Because cancelled meetings are
*removed* from `roomMeetings` rather than filtered by status, dead bookings never
inflate this scan.

---

## 4. `cancelMeeting` — Release the Room

```mermaid
flowchart TD
    A(["cancelMeeting(meetingId)"]) --> LOCK["🔒 synchronized"]
    LOCK --> B["meeting = meetings.get(meetingId)"]

    B --> C{"meeting<br/>== null?"}
    C -->|Yes| D["❌ throw MeetingSchedulerException<br/>'Meeting not found: MTG-n'"]
    C -->|No| E["meeting.cancel()"]

    E --> F{"status ==<br/>SCHEDULED?"}
    F -->|No| G["❌ throw MeetingSchedulerException<br/>'Can only cancel SCHEDULED meetings'"]
    F -->|Yes| H["status = CANCELLED"]

    H --> I["roomMeetings[meeting.room.id].remove(meeting)<br/>🔓 window is bookable again"]
    I --> J["notifyMeetingCancelled(meeting)"]
    J --> K(["return"])

    style D fill:#ffe0e0,stroke:#c00
    style G fill:#ffe0e0,stroke:#c00
    style I fill:#e0ffe0,stroke:#0a0
    style K fill:#e0ffe0,stroke:#0a0
```

Note the meeting is **not** removed from `meetings` — only from `roomMeetings`. That is
deliberate: the record survives as an audit trail, and a second `cancelMeeting` on the
same id finds it and correctly reports "can only cancel SCHEDULED meetings" rather than
the misleading "meeting not found".

---

## 5. `TimeSlot.overlaps` — The Conflict Test

```mermaid
flowchart TD
    A(["this.overlaps(other)"]) --> B{"this.start<br/>&lt; other.end?"}
    B -->|No| F["return false<br/>this ends at or before other starts"]
    B -->|Yes| C{"other.start<br/>&lt; this.end?"}
    C -->|No| G["return false<br/>other ends at or before this starts"]
    C -->|Yes| H["return true<br/>❌ the windows intersect"]

    style H fill:#ffe0e0,stroke:#c00
    style F fill:#e0ffe0,stroke:#0a0
    style G fill:#e0ffe0,stroke:#0a0
```

Visualised on a timeline:

```mermaid
flowchart LR
    subgraph case1["① Partial overlap → CONFLICT"]
        direction LR
        A1["A: 09:00 ──── 10:00"]
        B1["B:    09:30 ──── 10:30"]
    end

    subgraph case2["② Back-to-back → ALLOWED"]
        direction LR
        A2["A: 09:00 ──── 10:00"]
        B2["B:           10:00 ──── 11:00"]
    end

    subgraph case3["③ Fully contained → CONFLICT"]
        direction LR
        A3["A: 09:00 ──────────── 12:00"]
        B3["B:      10:00 ── 11:00"]
    end

    subgraph case4["④ Disjoint → ALLOWED"]
        direction LR
        A4["A: 09:00 ─── 10:00"]
        B4["B:                11:00 ─── 12:00"]
    end
```

The comparison is **strict** (`isBefore`, not "is before or equal"), which is precisely
why case ② is allowed: a 10:00 end does not collide with a 10:00 start.

---

## 6. `TimeSlot` Construction — Fail Fast

```mermaid
flowchart TD
    A(["new TimeSlot(startTime, endTime)"]) --> B{"endTime.isAfter(startTime)?"}
    B -->|No| C["❌ throw MeetingSchedulerException<br/>'End time must be after start time'"]
    B -->|Yes| D["assign final fields"]
    D --> E(["valid, immutable TimeSlot"])

    style C fill:#ffe0e0,stroke:#c00
    style E fill:#e0ffe0,stroke:#0a0
```

Both degenerate cases are rejected here: `end < start` (inverted) and `end == start`
(zero-length). Because the check lives in the constructor and the fields are `final`,
**an invalid `TimeSlot` object cannot exist** — no downstream code needs to re-validate.

---

## 7. Strategy Selection — The Two Policies

```mermaid
flowchart TD
    A(["selectRoom(availableRooms, requiredCapacity)"]) --> B{"Which strategy<br/>is active?"}

    B -->|FirstAvailable| C{"list empty?"}
    C -->|Yes| D["return null"]
    C -->|No| E["return availableRooms.get(0)<br/>⚠️ order follows ConcurrentHashMap<br/>iteration, which is unspecified"]

    B -->|BestFit| F{"list empty?"}
    F -->|Yes| G["return null"]
    F -->|No| H["return min by Room::getCapacity<br/>🎯 the tightest fit"]

    D --> I["scheduler sees null<br/>→ throws 'No available room found'"]
    G --> I

    style E fill:#fff4d0,stroke:#c90
    style H fill:#e0ffe0,stroke:#0a0
```

Worked example — rooms `Everest(10)`, `Alps(20)`, `Nook(4)`, all free, asking for 2:

| Strategy | Picks | Consequence |
|---|---|---|
| `FirstAvailableStrategy` | whichever room the map iterates first | A 2-person sync may occupy the 20-seat board room |
| `BestFitStrategy` | `Nook(4)` | Large rooms stay free for meetings that need them |

> ⚠️ **Known wrinkle.** `getAvailableRooms` iterates `rooms.values()` on a
> `ConcurrentHashMap`, whose iteration order is unspecified — so "first available" is
> *not* registration order. In the bundled demo, Everest is registered first but the
> first booking lands in Alps. Switching `rooms` to a `LinkedHashMap` (already safe,
> since every accessor is `synchronized`) would make the policy match its name.

---

## 8. Observer Notification — Fault Isolation

```mermaid
flowchart TD
    A(["notifyMeetingScheduled(meeting)<br/>/ notifyMeetingCancelled(meeting)"]) --> B{"More observers in the<br/>CopyOnWriteArrayList?"}
    B -->|No| Z(["done — booking already committed"])
    B -->|Yes| C["observer = next"]

    C --> D["try: observer.onMeetingScheduled(meeting)"]
    D --> E{"threw?"}
    E -->|"Yes"| F["catch: log to stderr<br/>'Observer notification failed: ...'"]
    E -->|No| G["✅ delivered"]

    F --> B
    G --> B

    style F fill:#fff4d0,stroke:#c90
    style Z fill:#e0ffe0,stroke:#0a0
```

Three properties fall out of this loop:

1. **Isolation** — each observer is wrapped in its own `try/catch`, so a throw never
   escapes to the caller and never aborts the booking.
2. **Progress** — the loop continues, so observer #2 still fires after observer #1
   fails. A `try` around the *whole loop* would starve the survivors.
3. **Safe iteration** — `CopyOnWriteArrayList` means an observer may call
   `removeObserver` from inside its own callback without a
   `ConcurrentModificationException`.

---

## 9. Singleton Initialization — Double-Checked Locking

```mermaid
flowchart TD
    A(["getInstance()"]) --> B{"instance == null?<br/>(volatile read, no lock)"}
    B -->|"No — the common case"| Z(["return instance<br/>⚡ fast path"])
    B -->|Yes| C["🔒 synchronized(lock)"]

    C --> D{"instance == null?<br/>(check AGAIN)"}
    D -->|"No — another thread<br/>built it while we waited"| E["🔓 release"]
    D -->|Yes| F["instance = new MeetingScheduler()<br/>volatile write publishes it safely"]

    F --> E
    E --> Z

    style Z fill:#e0ffe0,stroke:#0a0
    style D fill:#fff4d0,stroke:#c90
```

| Remove this | And you get |
|---|---|
| The **first** check | Every call pays for lock acquisition, forever |
| The **second** check | Two threads both pass check 1 → **two instances** |
| `volatile` | A reference can be published before the constructor's writes are visible — another thread sees a half-built object with null maps |
