# Movie Booking System — Activity Diagrams & Flowcharts

> Step-by-step control flow of each operation, with every branch the code takes.

---

## 1. Master Flow — from "what's on?" to a ticket

```mermaid
flowchart TD
    A([Customer opens app]) --> B[Pick city and movie]
    B --> C[findShows]
    C --> D{Any shows?}
    D -- no --> E([Show 'no shows found'])
    D -- yes --> F[Pick a show]
    F --> G[View AVAILABLE seats]
    G --> H[Select seats + payment method]
    H --> I[bookTickets]
    I --> J{Booking returned?}
    J -- yes --> K([Show booking id, seats, amount])
    J -- no --> L[Seats taken / payment failed / lock expired]
    L --> G
```

---

## 2. `BookingManager.createBooking`

```mermaid
flowchart TD
    S([createBooking user, show, seats, payment]) --> L{lockSeats ok?}
    L -- no --> R1([return empty: seats unavailable])
    L -- yes --> P[total = show.pricingStrategy.calculatePrice<br/>seats, show.seatPrices]
    P --> PAY[payment = paymentStrategy.pay total]
    PAY --> PS{status == SUCCESS?}
    PS -- no --> UL[unlockSeats show, seats, userId]
    UL --> R2([return empty: payment failed])
    PS -- yes --> CF{confirmSeats ok?}
    CF -- no --> RF[paymentStrategy.refund payment]
    RF --> R3([return empty: lock expired])
    CF -- yes --> B[new Booking user, show, seats, total, payment]
    B --> R4([return Optional.of booking])
```

---

## 3. `SeatLockManager.lockSeats`

```mermaid
flowchart TD
    S([lockSeats show, seats, userId]) --> M[enter synchronized show.lock]
    M --> LOOP{for each seat:<br/>status == AVAILABLE?}
    LOOP -- any no --> X[exit lock] --> F([return false — nothing changed])
    LOOP -- all yes --> SET[seat.status = LOCKED for all]
    SET --> REC[lockedSeats show seat = userId]
    REC --> SCH[schedule unlockSeats after LOCK_TIMEOUT_MS]
    SCH --> EXP[expireTasks show seat = task]
    EXP --> X2[exit lock] --> T([return true])
```

The check loop runs completely before anything is written, which is what makes the
lock all-or-nothing.

---

## 4. `SeatLockManager.confirmSeats`

```mermaid
flowchart TD
    S([confirmSeats show, seats, userId]) --> M[enter show.lock]
    M --> N{lockedSeats show exists?}
    N -- no --> F([false])
    N -- yes --> O{every seat held by userId?}
    O -- no --> F
    O -- yes --> B[for each seat:<br/>status = BOOKED<br/>remove lock<br/>cancelExpiry]
    B --> E{show map empty?}
    E -- yes --> RM[remove show entry] --> T([true])
    E -- no --> T
```

---

## 5. `SeatLockManager.unlockSeats` (also the expiry task)

```mermaid
flowchart TD
    S([unlockSeats show, seats, userId]) --> M[enter show.lock]
    M --> N{lockedSeats show exists?}
    N -- no --> R([return])
    N -- yes --> LOOP[for each seat]
    LOOP --> H{holder == userId?}
    H -- no --> NEXT[skip]
    H -- yes --> REM[remove lock + cancelExpiry]
    REM --> LK{status == LOCKED?}
    LK -- yes --> AV[status = AVAILABLE]
    LK -- no, BOOKED --> NEXT
    AV --> NEXT
    NEXT --> MORE{more seats?}
    MORE -- yes --> LOOP
    MORE -- no --> E{show map empty?}
    E -- yes --> RM[remove show entry] --> R
    E -- no --> R
```

---

## 6. `MovieBookingService.findShows`

```mermaid
flowchart LR
    A([findShows title, city]) --> B[stream shows.values]
    B --> C{title equalsIgnoreCase?}
    C -- no --> D[drop]
    C -- yes --> E[cinema = screenToCinema.get show.screen]
    E --> F{cinema != null AND<br/>city name equalsIgnoreCase?}
    F -- no --> D
    F -- yes --> G[add to result]
    G --> H([return list])
    D --> H
```

---

## 7. Pricing Strategies

```mermaid
flowchart LR
    A([calculatePrice seats, prices]) --> B[sum prices seat.type for each seat]
    B --> C{strategy}
    C -- Weekday --> D([return sum])
    C -- Weekend --> E([return sum × 1.2])
```

Example: 2 × `REGULAR` at 50 = 100 on a weekday, 120 on a weekend.

---

## 8. Double-Checked Locking in `getInstance`

```mermaid
flowchart TD
    A([getInstance]) --> B{instance == null?}
    B -- no --> R([return instance])
    B -- yes --> C[synchronized MovieBookingService.class]
    C --> D{instance == null?}
    D -- no --> X[exit lock] --> R
    D -- yes --> E[instance = new MovieBookingService]
    E --> X
```

---

## 9. Activity Diagram with Swimlanes — one booking, four actors

```mermaid
flowchart LR
    subgraph Customer
        C1[Select seats] --> C2[Submit payment details]
        C9[Receive booking]
        C10[See 'try again']
    end
    subgraph System["MovieBookingService / BookingManager"]
        S1[lockSeats] --> S2{locked?}
        S3[calculate price]
        S5{paid?}
        S6[confirmSeats]
        S7{still held?}
        S8[create Booking]
    end
    subgraph Gateway["Payment gateway"]
        G1[charge card]
        G2[refund]
    end
    subgraph Scheduler
        T1[expire lock after timeout]
    end

    C2 --> S1
    S2 -- no --> C10
    S2 -- yes --> S3 --> G1 --> S5
    S1 -.starts timer.-> T1
    S5 -- no --> C10
    S5 -- yes --> S6 --> S7
    T1 -.may release.-> S7
    S7 -- yes --> S8 --> C9
    S7 -- no --> G2 --> C10
```

---

## 10. How to add a new pricing rule

```mermaid
flowchart LR
    A[Create MatineePricingStrategy<br/>implements PricingStrategy] --> B[Implement calculatePrice]
    B --> C[Pass it to addShow for morning shows]
    C --> D([No change to BookingManager<br/>or MovieBookingService])
```

## 11. How to add a new payment method

```mermaid
flowchart LR
    A[Create UpiPaymentStrategy<br/>implements PaymentStrategy] --> B[Implement pay and refund]
    B --> C[Customer passes it to bookTickets]
    C --> D([No change to BookingManager])
```
