# Movie Booking System — Sequence Diagrams

> How the objects talk to each other over time, one flow per diagram. Every flow is
> traced from the code in [`../solutions/`](../solutions/).

---

## Flow 1 — Bootstrapping: singleton, catalogue setup

```mermaid
sequenceDiagram
    actor Admin
    participant MBS as MovieBookingService
    participant SLM as SeatLockManager
    participant BM as BookingManager

    Admin->>MBS: getInstance()
    alt first call
        MBS->>MBS: synchronized(class), re-check null
        MBS->>SLM: new SeatLockManager()
        MBS->>BM: new BookingManager(seatLockManager)
    end
    MBS-->>Admin: instance

    Admin->>MBS: addCity("New York")
    MBS-->>Admin: City(uuid)
    Admin->>MBS: addMovie(Movie M2)
    Admin->>Admin: new Screen("S1") + addSeat(...) ×20
    Admin->>MBS: addCinema("cinema1", name, cityId, [S1])
    MBS->>MBS: cinemas.put, screenToCinema.put(S1, cinema)
    Admin->>MBS: addShow("show2", M2, S1, time, seatPrices, WeekdayPricingStrategy)
    MBS->>MBS: shows.put(show2)
    MBS-->>Admin: Show
```

---

## Flow 2 — Search shows by movie and city

```mermaid
sequenceDiagram
    actor Customer
    participant MBS as MovieBookingService
    participant Show
    participant Idx as screenToCinema

    Customer->>MBS: findShows("Avengers: Endgame", "New York")
    loop each show in shows.values()
        MBS->>Show: getMovie().getTitle()
        alt title matches (ignore case)
            MBS->>Idx: get(show.getScreen())
            Idx-->>MBS: Cinema
            MBS->>MBS: cinema.getCity().getName() equalsIgnoreCase city?
        end
    end
    MBS-->>Customer: List<Show>
```

---

## Flow 3 — Book tickets, happy path

```mermaid
sequenceDiagram
    actor Customer
    participant MBS as MovieBookingService
    participant BM as BookingManager
    participant SLM as SeatLockManager
    participant Show
    participant PS as PricingStrategy
    participant Pay as PaymentStrategy
    participant Sch as Scheduler

    Customer->>MBS: bookTickets(userId, showId, seats, creditCard)
    MBS->>BM: createBooking(user, show, seats, creditCard)

    BM->>SLM: lockSeats(show, seats, userId)
    activate SLM
    SLM->>Show: synchronized(show.getLock())
    SLM->>SLM: every seat AVAILABLE? yes
    SLM->>SLM: seat.status = LOCKED, lockedSeats[show][seat] = userId
    SLM->>Sch: schedule(unlockSeats, 500 ms)
    Sch-->>SLM: ScheduledFuture → expireTasks
    SLM-->>BM: true
    deactivate SLM

    BM->>Show: getPricingStrategy(), getSeatPrices()
    BM->>PS: calculatePrice(seats, seatPrices)
    PS-->>BM: total

    BM->>Pay: pay(total)
    Pay-->>BM: Payment(SUCCESS)

    BM->>SLM: confirmSeats(show, seats, userId)
    activate SLM
    SLM->>Show: synchronized(show.getLock())
    SLM->>SLM: lockedSeats[show][seat] == userId for all? yes
    SLM->>SLM: seat.status = BOOKED, remove lock
    SLM->>Sch: cancel expiry task
    SLM-->>BM: true
    deactivate SLM

    BM->>BM: new Booking(user, show, seats, total, payment)
    BM-->>MBS: Optional.of(booking)
    MBS-->>Customer: Optional.of(booking)
```

---

## Flow 4 — Seat already taken

```mermaid
sequenceDiagram
    actor Bob
    participant BM as BookingManager
    participant SLM as SeatLockManager

    Bob->>BM: createBooking(bob, show, [A5, A6], card)
    BM->>SLM: lockSeats(show, [A5, A6], bob)
    SLM->>SLM: synchronized(show lock)
    SLM->>SLM: A5.status == BOOKED → not AVAILABLE
    SLM-->>BM: false (nothing locked)
    BM-->>Bob: Optional.empty()
    Note over BM: Payment is never attempted
```

---

## Flow 5 — Payment fails

```mermaid
sequenceDiagram
    participant BM as BookingManager
    participant SLM as SeatLockManager
    participant Pay as PaymentStrategy
    participant Sch as Scheduler

    BM->>SLM: lockSeats(...) 
    SLM-->>BM: true
    BM->>Pay: pay(total)
    Pay-->>BM: Payment(FAILURE)
    BM->>SLM: unlockSeats(show, seats, userId)
    SLM->>SLM: holder == userId → remove lock
    SLM->>Sch: cancel expiry task
    SLM->>SLM: LOCKED → AVAILABLE
    BM-->>BM: Optional.empty()
```

---

## Flow 6 — Lock expires during a slow payment (refund)

```mermaid
sequenceDiagram
    participant BM as BookingManager
    participant SLM as SeatLockManager
    participant Pay as PaymentStrategy
    participant Sch as Scheduler

    BM->>SLM: lockSeats(show, seats, alice)
    SLM->>Sch: schedule(unlockSeats, 500 ms)
    BM->>Pay: pay(total)
    Note over Pay: gateway is slow...
    Sch->>SLM: (500 ms) unlockSeats(show, seats, alice)
    SLM->>SLM: LOCKED → AVAILABLE, lock removed
    Pay-->>BM: Payment(SUCCESS)
    BM->>SLM: confirmSeats(show, seats, alice)
    SLM->>SLM: lockedSeats[show] is empty → not alice's
    SLM-->>BM: false
    BM->>Pay: refund(payment)
    BM-->>BM: Optional.empty()
```

Without the re-check in `confirmSeats`, Alice would be charged and the seat could
meanwhile be sold to someone else.

---

## Flow 7 — Race: two customers lock the same seat at once

```mermaid
sequenceDiagram
    participant A as Alice thread
    participant SLM as SeatLockManager
    participant L as show.lock (monitor)
    participant B as Bob thread

    par
        A->>SLM: lockSeats(show, [A5], alice)
    and
        B->>SLM: lockSeats(show, [A5], bob)
    end
    A->>L: enter monitor
    B->>L: enter monitor (blocks)
    SLM->>SLM: A5 AVAILABLE → LOCKED (alice)
    A->>L: exit
    SLM-->>A: true
    L-->>B: acquired
    SLM->>SLM: A5 is LOCKED → reject
    B->>L: exit
    SLM-->>B: false
```

Different shows have different monitors, so a race on show 1 never delays show 2.

---

## Flow 8 — Race: expiry fires while confirm is running

```mermaid
sequenceDiagram
    participant BM as BookingManager (alice)
    participant SLM as SeatLockManager
    participant Sch as Scheduler thread

    BM->>SLM: confirmSeats(show, seats, alice)
    SLM->>SLM: enter show lock
    Sch->>SLM: unlockSeats(show, seats, alice) (blocks on show lock)
    SLM->>SLM: seats → BOOKED, lock removed, task.cancel(false)
    SLM->>SLM: exit show lock
    SLM-->>BM: true
    SLM->>SLM: unlockSeats enters: no lock held by alice → no-op
    Note over SLM: A BOOKED seat is never reverted,<br/>because unlock only reverts LOCKED seats
```

---

## Flow 9 — Shutdown

```mermaid
sequenceDiagram
    actor Admin
    participant MBS as MovieBookingService
    participant SLM as SeatLockManager
    participant Sch as ScheduledExecutorService

    Admin->>MBS: shutdown()
    MBS->>SLM: shutdown()
    SLM->>Sch: shutdown()
    SLM->>Sch: awaitTermination(5 ms)
    alt not terminated or interrupted
        SLM->>Sch: shutdownNow()
    end
```

---

## Flow 10 — End-to-end (overview)

```mermaid
sequenceDiagram
    actor C as Customer
    participant App as MovieBookingService
    participant Lock as SeatLockManager
    participant GW as Payment gateway

    C->>App: findShows(movie, city)
    App-->>C: shows
    C->>App: view seat map (AVAILABLE seats)
    C->>App: bookTickets(seats, card)
    App->>Lock: lock (all-or-nothing, 500 ms lease)
    App->>App: price via show's PricingStrategy
    App->>GW: pay
    GW-->>App: SUCCESS
    App->>Lock: confirm (still mine?) → BOOKED
    App-->>C: Booking
```
