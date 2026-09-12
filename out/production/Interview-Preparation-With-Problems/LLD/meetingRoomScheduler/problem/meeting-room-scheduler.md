# Designing a Meeting Room Scheduler

## Problem Statement

Design a meeting room scheduling system for an organization with a limited number of
physical meeting rooms of varying sizes.

Employees need to book a room for a meeting by specifying a subject, the organizer, the
list of participants, a time window, and how many people the meeting must seat. Rather
than forcing the user to hunt for a free room, the system should find the rooms that are
both large enough and free for that window, pick one according to a configurable policy,
and confirm the booking.

The central constraint is that **a room can host only one meeting at any given instant**.
Because many employees may attempt to book the same room for overlapping windows at the
same moment, the system must serialize the "find a free room and claim it" step so two
meetings can never be confirmed for the same room at overlapping times.

Once a meeting is booked or cancelled, interested parties (email, calendar integrations)
must be informed, and it should be possible to add new notification channels or new
room-selection policies without modifying the scheduler itself.

---

## Functional Requirements

1. **Room registration** — The system supports registering rooms, each with a unique id,
   a display name, a capacity, and a `RoomType`: `CONFERENCE` (6–20 people),
   `BOARD_ROOM` (10–30 people), or `HUDDLE_SPACE` (2–4 people).

2. **Schedule a meeting** — A user can schedule a meeting by providing a subject, an
   organizer, a list of participants, a `TimeSlot`, and the required capacity. On
   success the system returns the created `Meeting` with an assigned room.

3. **Room availability lookup** — The system can return all rooms that satisfy both
   conditions for a given time slot: capacity is greater than or equal to the required
   capacity, **and** the room has no existing meeting overlapping that slot.

4. **Configurable room selection** — When multiple rooms qualify, the choice is made by a
   pluggable strategy. Two policies are supported:
    - `FirstAvailableStrategy` — picks the first qualifying room.
    - `BestFitStrategy` — picks the qualifying room with the **smallest** capacity, so
      large rooms stay free for meetings that actually need them.

   The active strategy can be swapped at runtime.

5. **No double-booking** — A booking must be rejected if no room is both large enough and
   free. Two meetings must never be confirmed for the same room at overlapping times.

6. **Meeting identity** — Each meeting is assigned a system-generated unique id of the
   form `MTG-<n>`.

7. **Cancel a meeting** — A meeting can be cancelled by its id. Cancelling releases the
   room for that time window so it becomes bookable again.

8. **Meeting lifecycle** — A meeting is `SCHEDULED` on creation and can transition to
   `CANCELLED` or `COMPLETED`. Any transition from a non-`SCHEDULED` state is rejected
   with a domain error, so a cancelled meeting cannot be cancelled or completed again.

9. **Notifications** — Registered observers are notified whenever a meeting is scheduled
   or cancelled. Email and calendar notifications are supported, and observers can be
   registered or removed at runtime.

10. **Time slot validity** — A time slot's end time must be strictly after its start time;
    otherwise construction fails. Two slots overlap when
    `start1 < end2 && start2 < end1`, which means **back-to-back bookings are allowed**
    (a 10:00–11:00 meeting does not conflict with an 11:00–12:00 meeting).

---

## Non-Functional Requirements

1. **Concurrency and thread safety** — Multiple users may book concurrently. The
   check-then-act sequence (find available rooms → select one → claim it) must be atomic,
   or two threads can both observe the same room as free and double-book it. Shared state
   is held in concurrent collections, and the scheduler's mutating operations are
   serialized.

2. **Safe singleton initialization** — The scheduler is a single, globally accessible
   point of control, initialized via double-checked locking with a `volatile` instance
   field so no thread can observe a partially constructed object.

3. **Extensibility (Open/Closed)** — Adding a new room-selection policy (for example,
   "prefer rooms on the requester's floor") or a new notification channel (Slack, SMS)
   must require only a new class implementing the relevant interface — never a change to
   the scheduler.

4. **Fault isolation in notifications** — Notification is a side effect, not part of the
   booking transaction. A failing observer must not fail or roll back an otherwise valid
   booking; each observer is invoked defensively and its failure is contained.

5. **Fail-fast validation** — Invalid input and illegal state transitions (unknown meeting
   id, no room available, inverted time slot, cancelling an already-cancelled meeting)
   raise a single, specific domain exception rather than failing silently or returning
   null.

6. **Predictable performance** — Availability lookup scans registered rooms and, for each,
   its active meetings, so cost grows with (number of rooms × meetings per room).
   Cancelled meetings are removed from the room's meeting list so they neither block
   future bookings nor inflate the scan.

7. **Modularity and separation of concerns** — Domain models, lifecycle rules, selection
   policy, notification, and orchestration are kept in separate units, so each can be
   understood, tested, and changed independently.

---

## Design Patterns Used

- **Singleton** — `MeetingScheduler` provides a single point of control over rooms and bookings.
- **Strategy** — `RoomSelectionStrategy` makes the room-choice policy interchangeable at runtime.
- **Observer** — `MeetingObserver` decouples the scheduler from notification channels.

## Implementation

#### [Java Implementation](../solution/)