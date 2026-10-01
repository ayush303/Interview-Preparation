# Designing a Movie Ticket Booking System (BookMyShow / Fandango-style)

## Problem Statement

Design the core of an online movie ticket booking platform.

The platform lists **cities**, the **cinemas** in each city, the **screens** inside each
cinema, and the **seats** on each screen. **Movies** are scheduled as **shows**: one
movie, on one screen, at one start time. A customer searches for a movie in their city,
picks a show, looks at the seat map, selects one or more seats, pays, and receives a
confirmed **booking**.

The flow for a single booking is:

```
select seats → LOCK seats (with timeout) → calculate price → pay → CONFIRM (BOOKED)
                    │                                          │
                    └── seat already LOCKED/BOOKED → reject    ├── payment fails → unlock
                                                               └── lock expired  → refund
```

Three things make this more than a CRUD app.

First, **seats are a scarce, contended resource**. Hundreds of people open the same
show's seat map at the same moment, and two of them must never both end up with seat
`A5`. The "check the seat is free, then take it" step has to be atomic.

Second, **payment is slow and can fail**. A seat cannot be held forever while a user
types in card details, so it is *locked* for a limited time. If the user pays in time,
the lock turns into a booking. If they don't, or the payment fails, the seat goes back
on sale. If the lock expires *while* the payment is in flight, the system must notice
and refund rather than charge for a seat the user no longer holds.

Third, **pricing and payment methods are business policies that change**. Today it is
"weekday price" and "credit card". Tomorrow it is weekend surcharges, matinee discounts,
UPI or wallets. Adding one must not mean editing the booking flow.

---

## Functional Requirements

1. **Manage the catalogue.** The system can register cities, movies, cinemas (each in a
   city, with one or more screens), and shows. Each entity has a unique id.

2. **Screens and seats.** A screen is a list of seats. Each seat has a row, a column, a
   `SeatType` (`REGULAR`, `PREMIUM`, `RECLINER`) and a `SeatStatus`
   (`AVAILABLE`, `LOCKED`, `BOOKED`). New seats start `AVAILABLE`.

3. **Schedule a show.** A show links one movie to one screen at a start time, and
   carries its own **seat-price table** (`Map<SeatType, Double>`) and a
   **pricing strategy**. Prices are data on the show, not constants on the `SeatType`
   enum, so the same recliner can cost more on a premiere night.

4. **Register customers.** A customer has a system-generated id, a name and an email.

5. **Search shows.** Given a movie title and a city name (both case-insensitive), return
   every show of that movie in a cinema in that city.

6. **View available seats.** For a chosen show, list the seats whose status is
   `AVAILABLE`.

7. **Book tickets.** A customer books a list of seats for a show with a chosen payment
   method. The booking succeeds only if **every** requested seat can be locked:
   - **Lock.** All seats are checked and locked as one step. If any seat is not
     `AVAILABLE`, nothing is locked and the booking is rejected.
   - **Price.** The total is computed by the show's pricing strategy from its seat-price
     table.
   - **Pay.** The chosen payment strategy charges the total. If payment fails, the
     locks are released and the booking is rejected.
   - **Confirm.** The system re-checks that this customer still holds the lock on every
     seat and marks them `BOOKED`. If a lock expired during payment, the payment is
     refunded and the booking is rejected.
   - On success a `Booking` is returned with a generated id, the customer, show, seats,
     total amount and payment record.

8. **Lock expiry.** A seat lock lasts a fixed timeout (`LOCK_TIMEOUT_MS`). If the
   booking is not confirmed or released by then, the seats are automatically returned
   to `AVAILABLE`.

9. **Lock ownership.** Only the customer who holds a lock can release or confirm it. A
   `BOOKED` seat is never reverted by a late expiry or unlock.

10. **Pricing policies.** Two pricing strategies are supported:
    - `WeekdayPricingStrategy` — sum of the seat-type prices.
    - `WeekendPricingStrategy` — the same sum with a 20% surcharge.

11. **Payment methods.** Payment is pluggable through `PaymentStrategy` (`pay`,
    `refund`). `CreditCardPaymentStrategy` is provided and simulates a gateway with a
    95% success rate.

12. **Shutdown.** The service can be shut down, which stops the lock-expiry scheduler.

---

## Non-Functional Requirements

1. **No double booking (thread safety).**
   - `lockSeats`, `confirmSeats` and `unlockSeats` all run inside
     `synchronized (show.getLock())`, so for a given show the check-then-lock,
     verify-then-book and release steps are serialized. Two customers racing for the
     same seat cannot both win.
   - The lock is **per show**, not global, so bookings for different shows never wait
     on each other.
   - All registries in `MovieBookingService` and the lock tables in `SeatLockManager`
     are `ConcurrentHashMap`s.

2. **All-or-nothing seat locking.** A multi-seat request either locks every seat or
   none. A customer never ends up holding half of the seats they asked for.

3. **Bounded holds.** Locks expire through a `ScheduledExecutorService`, so an
   abandoned checkout cannot block a seat forever. When a lock is confirmed or released
   early, its expiry task is cancelled so it does not fire later.

4. **Consistency across a slow payment.** Payment happens *outside* the show lock, so a
   slow gateway never blocks other customers. Correctness is preserved by the
   re-check in `confirmSeats`: if the lock expired meanwhile, the booking is refused and
   the money refunded.

5. **Safe lazy singleton.** `MovieBookingService.getInstance()` uses double-checked
   locking on a `volatile` field, so the service is built once and no thread sees a
   half-constructed object.

6. **Open/Closed for policies.** A new pricing rule is a new `PricingStrategy`. A new
   payment method is a new `PaymentStrategy`. Neither requires editing
   `BookingManager` or `MovieBookingService`.

7. **Low latency on hot paths.** Show, customer and cinema lookups are O(1) by id.
   Finding the cinema for a show is O(1) through a `screen → cinema` index built when
   cinemas are added. Show search is O(S) over all shows, which is fine at interview
   scale and would move to an index keyed by `(city, movie)` in production.

8. **Separation of concerns.** Domain models (`models`), status vocabulary (`enums`),
   policies (`strategy/pricing`, `strategy/payment`), seat concurrency
   (`SeatLockManager`), the booking transaction (`BookingManager`) and the public API
   (`MovieBookingService`) live in separate units.

9. **Clean resource lifecycle.** `shutdown()` stops the scheduler gracefully and falls
   back to `shutdownNow()`, so the JVM can exit.

---

## Core Entities and Their Relationships

| Entity | Responsibility |
|---|---|
| `MovieBookingService` | Singleton facade. Owns the registries (cities, cinemas, movies, customers, shows), the `screen → cinema` index, and the `SeatLockManager` and `BookingManager`. Exposes catalogue setup, search and `bookTickets`. |
| `BookingManager` | Runs one booking as a transaction: lock → price → pay → confirm, with unlock on payment failure and refund on lock expiry. |
| `SeatLockManager` | Holds `show → (seat → userId)` locks and their expiry tasks. `lockSeats`, `confirmSeats` and `unlockSeats` are serialized per show. |
| `City` | A city with an id and a name. |
| `Cinema` | A cinema in one city, owning one or more screens. |
| `Screen` | A screen holding its seats. |
| `Seat` | Row, column, `SeatType` and current `SeatStatus`. |
| `Movie` | Id, title and duration. |
| `Show` | One movie on one screen at a start time, with its seat-price table, pricing strategy, and a per-show lock object. |
| `Customer` | The person booking: id, name, email. |
| `Booking` | The confirmed result: customer, show, seats, total amount and payment. |
| `Payment` | Amount, gateway transaction id and `PaymentStatus` (`SUCCESS`, `FAILURE`, `PENDING`). |
| `PricingStrategy` | Computes a total from a list of seats and a price table. Weekday and weekend implementations. |
| `PaymentStrategy` | Charges and refunds an amount. Credit card implementation. |

```mermaid
classDiagram
    direction LR

    class MovieBookingService {
        <<Singleton>>
    }
    class PricingStrategy {
        <<interface>>
    }
    class PaymentStrategy {
        <<interface>>
    }
    class SeatType {
        <<enumeration>>
    }
    class SeatStatus {
        <<enumeration>>
    }
    class PaymentStatus {
        <<enumeration>>
    }

    MovieBookingService "1" o-- "*" City : registers
    MovieBookingService "1" o-- "*" Cinema : registers
    MovieBookingService "1" o-- "*" Movie : registers
    MovieBookingService "1" o-- "*" Show : registers
    MovieBookingService "1" o-- "*" Customer : registers
    MovieBookingService "1" *-- "1" BookingManager : owns
    MovieBookingService "1" *-- "1" SeatLockManager : owns
    BookingManager --> SeatLockManager : uses
    BookingManager ..> PaymentStrategy : per booking

    Cinema "*" --> "1" City : located in
    Cinema "1" *-- "1..*" Screen : has
    Screen "1" *-- "*" Seat : has
    Seat --> SeatType
    Seat --> SeatStatus

    Show "*" --> "1" Movie : plays
    Show "*" --> "1" Screen : on
    Show --> PricingStrategy : priced by
    PricingStrategy <|.. WeekdayPricingStrategy
    PricingStrategy <|.. WeekendPricingStrategy
    PaymentStrategy <|.. CreditCardPaymentStrategy

    Booking "*" --> "1" Customer : for
    Booking "*" --> "1" Show : of
    Booking "*" --> "1..*" Seat : reserves
    Booking "1" --> "1" Payment : paid by
    Payment --> PaymentStatus
    SeatLockManager ..> Show : locks per show
```

**Reading the arrows.** `<|..` is interface realisation, `*--` is composition (a `Seat`
belongs to its `Screen`, a `Screen` to its `Cinema`), `o--` is aggregation (the service
holds entities it did not create the identity of), `-->` is a plain association, and
`..>` is a transient dependency (the payment strategy is passed in per booking, not
stored).

---

## Design Patterns Used

| Pattern | Where | Why |
|---|---|---|
| **Singleton** | `MovieBookingService.getInstance()`, double-checked locking with a `volatile` instance and a private constructor | One owner for all registries and one `SeatLockManager`, so every caller sees the same seat locks. Two lock managers would mean two sources of truth for who holds a seat. |
| **Strategy** (pricing) | `PricingStrategy` → `WeekdayPricingStrategy`, `WeekendPricingStrategy`, set per `Show` | Pricing changes by day, time, event or promotion. Each rule is one class, chosen when the show is scheduled. |
| **Strategy** (payment) | `PaymentStrategy` → `CreditCardPaymentStrategy`, passed into `bookTickets` | The customer picks the payment method at checkout. UPI, wallet or net banking are new classes, not edits to `BookingManager`. |
| **Facade** | `MovieBookingService` | Clients call one object (`addCity`, `addCinema`, `addShow`, `createUser`, `findShows`, `bookTickets`, `shutdown`) and never touch the lock manager or booking manager directly. |
| **Pessimistic locking with timeout (lease)** | `SeatLockManager` with per-show monitors and a `ScheduledExecutorService` | Seats are held exclusively during checkout, and the hold auto-expires, so contention is resolved up front and abandoned carts free themselves. |
| **Compensating transaction** | `BookingManager.createBooking`: unlock on payment failure, `refund` when confirmation fails | The booking spans a lock and an external payment that cannot share one atomic step, so each failure path explicitly undoes what already happened. |

---

## Seat Lock Concurrency

The full write-up, with diagrams and code for each alternative, is in
[08 — Seat Lock Concurrency](../class_diagrams/08-seat-lock-concurrency.md).

### How it works today

`SeatLockManager` guards each show with its own monitor, `synchronized (show.getLock())`.
Inside that block:

- **`lockSeats`** checks that *every* requested seat is `AVAILABLE` before writing any
  of them. It then marks them `LOCKED`, records the holder in
  `lockedSeats[show][seat] = userId`, and schedules one expiry task
  (`LOCK_TIMEOUT_MS = 500`) on a `ScheduledExecutorService`. Check and write happen
  under one lock, so two users can never both see a seat as free and take it, and a
  multi-seat request is all-or-nothing.
- **`confirmSeats`** runs after payment, which happens *outside* the lock so a slow
  gateway blocks nobody. It re-checks that this user still holds every seat. If so the
  seats become `BOOKED` and the expiry task is cancelled. If the hold expired meanwhile
  it returns `false` and `BookingManager` refunds.
- **`unlockSeats`** (called on payment failure, and by the expiry task) releases only
  seats this user holds, and only reverts seats that are still `LOCKED`. A late expiry
  can therefore never undo a booking.

Because the lock is per show, bookings for different shows never wait on each other.

### Gaps in that approach

- The lock is per **show**, but `Seat` (and its status) is shared by every show on the
  **screen**. Two shows on one screen use different monitors on the same seat objects,
  so they are not serialised against each other.
- A timer thread plus two bookkeeping maps exist only to answer "is this hold older
  than 500 ms?".
- `synchronized` has no timeout, and it only works inside one JVM.

### Better ways, easiest first

| Approach | Idea | When |
|---|---|---|
| **Global `synchronized`** | Mark `lockSeats` / `confirmSeats` / `unlockSeats` `synchronized` on the singleton | Easiest correct answer; does not scale |
| **Per-show lock + lazy expiry** *(recommended)* | A `ShowSeat` per show holds `status`, `lockedBy`, `lockedUntil`. An expired hold simply counts as free. No scheduler, no expiry map | Default single-JVM design |
| **CAS per seat** | `AtomicReference<Hold>` per `ShowSeat`, `compareAndSet` to lock; multi-seat locks in sorted order and rolls back on failure | Lock-free, when the per-show lock is a measured hot spot |
| **`ReentrantLock.tryLock(timeout)`** | Like `synchronized` but can give up, can be fair | When callers must fail fast |
| **SQL conditional `UPDATE`** | `UPDATE show_seat SET LOCKED … WHERE status='AVAILABLE' OR lock_expires_at < now()` and check the row count | Any multi-server deployment |
| **Redis `SET NX PX`** | Atomic set-if-absent with a TTL; Lua for multi-seat; DB stays the system of record | Very high traffic (premieres) |

---

## Tests

[`solutions/tests/`](../solutions/tests/) holds 12 concurrency scenarios, each with
**500 threads across 200 users** released together from a latch. They cover a single
seat contested by everyone, overlapping pairs, random groups, declined cards, payments
slower than the hold, holds resold after expiry, users trying to steal someone else's
hold, payments finishing right at the expiry boundary, many shows at once, the
singleton, and the full `bookTickets` facade. After each race the whole show is checked:
no seat sold twice, nothing left `LOCKED`, every `BOOKED` seat has a booking.

```bash
javac -d out $(find LLD/movie_booking_system/solutions -name '*.java')
java -cp out LLD.movie_booking_system.solutions.tests.ConcurrencyTests
```

Removing the `synchronized` from `lockSeats` makes 8 of the 12 scenarios fail with real
double bookings, so the suite does detect the bug it is there for. Details are in the
[tests README](../solutions/tests/README.md).

---

## Known Limitations

These are gaps in the current code worth raising in an interview.

1. **Seat status is shared across shows.** `SeatStatus` lives on `Seat`, and seats
   belong to the `Screen`. Every show on that screen sees the same `Seat` objects, so
   booking `A5` for the 2 pm show also marks `A5` as `BOOKED` for the 8 pm show. The fix
   is a per-show seat inventory, e.g. a `ShowSeat(show, seat, status)` or a
   `Map<Seat, SeatStatus>` on `Show`, leaving `Seat` as pure layout.
2. **The seat id passed to `Seat` is ignored.** The constructor generates a UUID
   instead, so ids like `"A5"` never appear in output.
3. **No validation of ids.** `bookTickets` with an unknown user or show id passes
   `null` into `BookingManager`, and `addCinema` with an unknown city id creates a cinema
   with a `null` city, which later breaks `findShows`. These should fail fast with a
   clear exception.
4. **Seats are not checked against the show's screen.** A caller could lock seats from
   a different screen.
5. **Money as `double`.** Prices and totals should be `BigDecimal`.
6. **No cancellation or booking history.** There is no `cancelBooking` (release seats
   and refund) and no "my bookings" lookup.
7. **Errors are printed, not returned.** Failures return `Optional.empty()` and print a
   message, so the caller cannot tell "seat taken" from "payment failed" from "lock
   expired".

---

## Implementation

#### [Java Implementation](../solutions/)

## Diagrams

#### [Design Diagrams](../class_diagrams/)
