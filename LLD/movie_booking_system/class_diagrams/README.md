# Movie Booking System — Design Diagrams

The complete diagram set for the movie ticket booking LLD. Everything is written in
[Mermaid](https://mermaid.js.org/), so the sources stay diffable in git and render
inline on GitHub.

**Related:** [Problem statement, requirements and patterns](../problems/movie-booking-system.md) ·
[Java implementation](../solutions/)

---

## Index

| # | File | Diagram types inside |
|---|---|---|
| 01 | [UML Class Diagram](01-uml-class-diagram.md) | Full class diagram · package/layer view · design-pattern overlay · Strategy close-up · `SeatLockManager` close-up · Singleton · notation cheatsheet |
| 02 | [ER Diagram](02-er-diagram.md) | Conceptual (Chen) ER · logical crow's-foot ER · cardinality table · seat ↔ show resolution |
| 03 | [Database Model](03-database-model.md) | Physical schema · PostgreSQL DDL · indexes · DB-level seat locking · in-memory storage map · Java → table mapping |
| 04 | [Sequence Diagrams](04-sequence-diagrams.md) | Bootstrap · search · happy-path booking · seat taken · payment fails · lock expires mid-payment · lock race · expiry-vs-confirm race · shutdown · end-to-end |
| 05 | [State Diagrams](05-state-diagrams.md) | Seat lifecycle · with cancellation · booking attempt · payment status · lock entry · singleton · show timeline |
| 06 | [Activity Diagrams & Flowcharts](06-activity-flowcharts.md) | Master flow · `createBooking` · `lockSeats` · `confirmSeats` · `unlockSeats` · `findShows` · pricing · DCL · swimlanes · adding a pricing rule / payment method |
| 07 | [Use Case, Component, Object & Deployment](07-usecase-component-deployment.md) | Use case · component · object snapshot · deployment (today and at scale) · extension mind-map |

---

## Which diagram answers which question?

| If you are asked… | Read |
|---|---|
| "Draw the class diagram" | [01 § 1](01-uml-class-diagram.md#1-full-class-diagram) |
| "What patterns did you use?" | [01 § 3](01-uml-class-diagram.md#3-design-pattern-overlay) |
| "Draw the ER diagram" | [02 § 2](02-er-diagram.md#2-logical-er-diagram-crows-foot) |
| "How would you store this?" | [03 § 1–2](03-database-model.md#1-physical-schema-diagram) |
| "How do you stop two people booking the same seat?" | [04 Flow 7](04-sequence-diagrams.md#flow-7--race-two-customers-lock-the-same-seat-at-once), [03 § 4](03-database-model.md#4-concurrency-at-the-database-layer) |
| "What if payment takes too long?" | [04 Flow 6](04-sequence-diagrams.md#flow-6--lock-expires-during-a-slow-payment-refund) |
| "Walk me through a booking" | [04 Flow 3](04-sequence-diagrams.md#flow-3--book-tickets-happy-path), [06 § 2](06-activity-flowcharts.md#2-bookingmanagercreatebooking) |
| "What states can a seat be in?" | [05 § 1](05-state-diagrams.md#1-seat-lifecycle-per-show) |
| "How does this scale?" | [07 § 4](07-usecase-component-deployment.md#4-deployment-view) |

---

## The three-sentence summary

`MovieBookingService` is a thread-safe **singleton facade** over `ConcurrentHashMap`
registries, delegating each booking to `BookingManager`. `BookingManager` runs a
**lock → price → pay → confirm** sequence, with unlock on payment failure and refund if
the seat lock expired mid-payment. `SeatLockManager` makes seat locking all-or-nothing
and race-free by serialising per show, and expires abandoned holds on a timer, while
pricing and payment are pluggable **strategies**.

---

## A note on ⚠️ marks

Wherever the code differs from the intended design, it is marked ⚠️. The main one: seat
status lives on `Seat` and is shared by every show on the screen. The data model in
[02](02-er-diagram.md) and [03](03-database-model.md) uses a per-show `SHOW_SEAT` table
to fix it. All gaps are listed under
[Known Limitations](../problems/movie-booking-system.md#known-limitations).

---

## Viewing these diagrams

- **GitHub** renders mermaid in Markdown automatically.
- **IntelliJ IDEA**: Markdown preview with the Mermaid extension enabled.
- **VS Code**: install *Markdown Preview Mermaid Support*.
- **Export to PNG**: paste a block into [mermaid.live](https://mermaid.live).
