# Meeting Room Scheduler — Design Diagrams

Complete diagram set for the Meeting Room Scheduler LLD, written in
[Mermaid](https://mermaid.js.org/) so the sources stay diffable in git and render
inline on GitHub.

**Related:** [Problem statement](../problem/meeting-room-scheduler.md) ·
[Java implementation](../solution/)

---

## Index

| # | File | Diagrams inside |
|---|---|---|
| 01 | [Class Diagram](01-class-diagram.md) | Full class diagram · layered package view · design-pattern overlay · notation cheatsheet |
| 02 | [Entity Relationship Diagram](02-entity-relationship-diagram.md) | Core ER model · in-memory storage map · the conflict rule |
| 03 | [Sequence Diagrams](03-sequence-diagrams.md) | Schedule · reject · cancel · availability lookup · **the concurrency race** · observer isolation · strategy swap |
| 04 | [State Diagrams](04-state-diagrams.md) | Meeting lifecycle · rejected transitions · room occupancy · singleton init · request lifecycle |
| 05 | [Flowcharts](05-flowcharts.md) | Master flow · `scheduleMeeting` · `getAvailableRooms` · `cancelMeeting` · `overlaps` · strategy selection · notification · DCL |
| 06 | [Use Case, Component & Activity](06-usecase-component-activity.md) | Use cases · component seams · concurrent-booking swimlanes · deployment view |

---

## Which diagram answers which question?

| If you are asked… | Read |
|---|---|
| "Draw the class diagram" | [01](01-class-diagram.md) § 1 |
| "What patterns did you use, and where?" | [01](01-class-diagram.md) § 3 |
| "How would you store this in a database?" | [02](02-entity-relationship-diagram.md) § 1 |
| "Why two collections of meetings?" | [02](02-entity-relationship-diagram.md) § 2 |
| "Walk me through booking a room" | [03](03-sequence-diagrams.md) Flow 1 |
| "How do you prevent double-booking?" | [03](03-sequence-diagrams.md) Flow 5 — **the money question** |
| "What if the email service is down?" | [03](03-sequence-diagrams.md) Flow 6 |
| "What states can a meeting be in?" | [04](04-state-diagrams.md) § 1 |
| "Why double-checked locking?" | [04](04-state-diagrams.md) § 3, [05](05-flowcharts.md) § 9 |
| "What's the time complexity?" | [05](05-flowcharts.md) § 3 |
| "When do two meetings conflict?" | [05](05-flowcharts.md) § 5 |
| "How would you add a Slack notification?" | [06](06-usecase-component-activity.md) § 2 |
| "Does this scale to multiple servers?" | [06](06-usecase-component-activity.md) § 5 |

---

## The three-sentence summary

`MeetingScheduler` is a **singleton** holding every room and booking, and its mutating
methods are `synchronized` so that the *find a free room → pick one → claim it* sequence
is atomic — without that, two threads can both see the same room as free and
double-book it. Which room gets picked is delegated to a **strategy**
(`FirstAvailableStrategy` or `BestFitStrategy`), swappable at runtime. Once a booking is
committed, registered **observers** (email, calendar) are notified in a per-observer
`try/catch`, so a failing notification channel can never roll back a valid booking.

---

## Viewing these diagrams

- **GitHub** — renders mermaid in Markdown automatically; just open the file.
- **IntelliJ IDEA** — the built-in Markdown preview renders mermaid (enable the
  Mermaid extension under *Settings → Languages & Frameworks → Markdown*).
- **VS Code** — install *Markdown Preview Mermaid Support*.
- **Export to PNG** — paste a block into [mermaid.live](https://mermaid.live) and use
  *Actions → PNG*, which is how the `.png` files in the other LLD folders were made.

---

## A note on the `FirstAvailableStrategy` ordering

Several diagrams flag this, so it is worth stating once here: `getAvailableRooms`
iterates `rooms.values()` on a `ConcurrentHashMap`, whose iteration order is
**unspecified**. "First available" therefore does not mean "first registered" — in the
bundled demo, Everest is registered first but the first booking lands in Alps.

Switching `rooms` to a `LinkedHashMap` would make the behaviour match the name, and it
is safe to do because every method touching `rooms` is already `synchronized`. The
diagrams document the code **as it is today**, with the wrinkle marked ⚠️ wherever it
is relevant.
