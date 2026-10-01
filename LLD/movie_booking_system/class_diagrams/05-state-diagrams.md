# Movie Booking System — State Diagrams

> Which objects have a lifecycle, what states they move through, and what drives each
> move. Seat status is the heart of the system. Every transition is made by
> `SeatLockManager` while holding the show's lock.

---

## 1. Seat Lifecycle (per show)

```mermaid
stateDiagram-v2
    direction LR
    [*] --> AVAILABLE : seat created

    AVAILABLE --> LOCKED : lockSeats() — all requested seats free
    LOCKED --> AVAILABLE : unlockSeats() — payment failed
    LOCKED --> AVAILABLE : expiry task fires (LOCK_TIMEOUT_MS)
    LOCKED --> BOOKED : confirmSeats() — same user still holds lock

    BOOKED --> [*]

    note right of LOCKED
        entry / record holder userId
        entry / schedule expiry task
        exit / cancel expiry task
    end note
    note right of BOOKED
        terminal: unlock and expiry
        never revert a BOOKED seat
    end note
```

| State | Entered by | Who can leave it | Terminal |
|---|---|---|---|
| `AVAILABLE` | creation, unlock, expiry | any customer, via `lockSeats` | no |
| `LOCKED` | `lockSeats` | only the lock holder (`confirmSeats` / `unlockSeats`) or the scheduler | no |
| `BOOKED` | `confirmSeats` | nobody (no cancellation yet) | yes |

> ⚠️ In the code, `status` is a field on `Seat`, so this machine is shared by **every
> show on the screen**. Conceptually it should be one machine per `(show, seat)`. See
> [02 § 4](02-er-diagram.md#4-resolving-seat--show-the-many-to-many).

---

## 2. Seat Lifecycle with Cancellation (future)

```mermaid
stateDiagram-v2
    direction LR
    [*] --> AVAILABLE
    AVAILABLE --> LOCKED : lock
    LOCKED --> AVAILABLE : release / expire
    LOCKED --> BOOKED : confirm
    BOOKED --> AVAILABLE : cancelBooking() + refund
    BOOKED --> [*] : show started
```

---

## 3. Booking Attempt (inside `BookingManager.createBooking`)

```mermaid
stateDiagram-v2
    [*] --> Locking
    Locking --> Rejected : a seat not AVAILABLE
    Locking --> Pricing : all seats locked
    Pricing --> Paying
    Paying --> Releasing : payment FAILURE
    Releasing --> Rejected
    Paying --> Confirming : payment SUCCESS
    Confirming --> Refunding : lock lost (expired)
    Refunding --> Rejected
    Confirming --> Confirmed : seats BOOKED
    Confirmed --> [*] : Optional.of(booking)
    Rejected --> [*] : Optional.empty()
```

There is no `Booking` object until the `Confirmed` state, so a `Booking` that exists is
always a successful one.

---

## 4. Payment Status

```mermaid
stateDiagram-v2
    direction LR
    [*] --> PENDING : request sent
    PENDING --> SUCCESS : gateway approves
    PENDING --> FAILURE : gateway declines
    SUCCESS --> Refunded : refund() after lost lock
    SUCCESS --> [*]
    FAILURE --> [*]
    Refunded --> [*]
```

`CreditCardPaymentStrategy` returns `SUCCESS` or `FAILURE` directly. `PENDING` exists
in the enum for asynchronous gateways (UPI, net banking). `Refunded` is not an enum
value today; `refund()` only logs.

---

## 5. Lock Entry in `SeatLockManager`

```mermaid
stateDiagram-v2
    direction LR
    [*] --> Absent
    Absent --> Held : lockSeats puts (seat → userId) + expiry task
    Held --> Absent : confirmSeats (seat BOOKED, task cancelled)
    Held --> Absent : unlockSeats by holder (task cancelled)
    Held --> Absent : expiry fires → unlockSeats
    Held --> Held : unlockSeats by another user (ignored)
```

When the last seat of a show is removed, the show's inner map is dropped, so the lock
tables do not grow with finished shows.

---

## 6. Singleton Initialisation

```mermaid
stateDiagram-v2
    direction LR
    [*] --> Uninitialised
    Uninitialised --> Constructing : first getInstance() wins class lock
    Constructing --> Ready : volatile write of instance
    Ready --> Ready : getInstance() (no locking)
    Ready --> ShutDown : shutdown() stops scheduler
```

---

## 7. Show Timeline (domain view)

```mermaid
stateDiagram-v2
    direction LR
    [*] --> Scheduled : addShow()
    Scheduled --> OnSale : bookings open
    OnSale --> SoldOut : no AVAILABLE seats
    SoldOut --> OnSale : lock expired / cancellation
    OnSale --> Running : startTime reached
    SoldOut --> Running : startTime reached
    Running --> Finished : startTime + duration
    Finished --> [*]
```

The Java `Show` has no status field. This view shows the rules a production system
would enforce, such as "no bookings after `startTime`".
