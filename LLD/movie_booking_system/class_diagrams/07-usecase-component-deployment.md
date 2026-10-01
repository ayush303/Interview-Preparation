# Movie Booking System — Use Case, Component, Object & Deployment Diagrams

> The big-picture views: who uses the system, how it is split into components, a
> snapshot of live objects, and how it would run in production.

---

## 1. Use Case Diagram

```mermaid
flowchart LR
    Customer((Customer))
    Admin((Theatre admin))
    Gateway((Payment gateway))
    Clock((Scheduler / clock))

    subgraph System["Movie Booking System"]
        UC1([Search shows by movie and city])
        UC2([View seat map])
        UC3([Book tickets])
        UC4([Lock seats])
        UC5([Pay])
        UC6([Confirm seats])
        UC7([Refund])
        UC8([Expire seat lock])
        UC9([Add city / cinema / screen / seats])
        UC10([Add movie])
        UC11([Schedule show with prices and pricing rule])
        UC12([Register customer])
    end

    Customer --> UC1
    Customer --> UC2
    Customer --> UC3
    Customer --> UC12
    UC3 -.include.-> UC4
    UC3 -.include.-> UC5
    UC3 -.include.-> UC6
    UC6 -.extend: lock lost.-> UC7
    UC5 --- Gateway
    UC7 --- Gateway
    Clock --> UC8
    Admin --> UC9
    Admin --> UC10
    Admin --> UC11
```

---

## 2. Component Diagram

```mermaid
flowchart TB
    Client[Client / MovieBookingDemo]

    subgraph Core["Movie Booking Core"]
        Facade["MovieBookingService<br/>«facade, singleton»"]
        Catalog["Catalogue registries<br/>cities · cinemas · movies · shows · users"]
        Search["Show search<br/>+ screen→cinema index"]
        Booking["BookingManager"]
        Locks["SeatLockManager<br/>+ ScheduledExecutorService"]
    end

    subgraph Plugins["Plug-in points"]
        Pricing["«interface» PricingStrategy<br/>Weekday · Weekend"]
        Payment["«interface» PaymentStrategy<br/>CreditCard"]
    end

    Client --> Facade
    Facade --> Catalog
    Facade --> Search
    Facade --> Booking
    Booking --> Locks
    Booking --> Pricing
    Booking --> Payment
    Search --> Catalog
```

---

## 3. Object Diagram — snapshot right after Alice's booking (demo run)

```mermaid
flowchart LR
    nyc["nyc : City<br/>name = New York"]
    amc["cinema1 : Cinema<br/>AMC Times Square"]
    s1["S1 : Screen<br/>20 seats"]
    a3["A3 : Seat<br/>REGULAR · BOOKED"]
    a4["A4 : Seat<br/>REGULAR · BOOKED"]
    av["avengers : Movie<br/>M2 · 170 min"]
    sh["show2 : Show<br/>now + 5h<br/>Weekday"]
    alice["alice : Customer"]
    bk["booking : Booking<br/>total = 100.0"]
    pay["payment : Payment<br/>SUCCESS"]
    lock["SeatLockManager<br/>lockedSeats = {}<br/>expireTasks = {}"]

    amc --> nyc
    amc --> s1
    s1 --> a3
    s1 --> a4
    sh --> av
    sh --> s1
    bk --> alice
    bk --> sh
    bk --> a3
    bk --> a4
    bk --> pay
```

After confirmation the lock tables are empty: the seats are `BOOKED`, so no lock or
expiry task is needed. Bob's later attempt on A3/A4 is rejected at `lockSeats`.

---

## 4. Deployment View

### Today — one JVM

```mermaid
flowchart LR
    subgraph JVM
        Demo[MovieBookingDemo.main] --> MBS[MovieBookingService]
        MBS --> Maps[(ConcurrentHashMaps)]
        MBS --> SLM[SeatLockManager]
        SLM --> TP[Scheduler thread]
    end
```

### At scale

```mermaid
flowchart LR
    Users((Users)) --> CDN[CDN<br/>posters, static]
    Users --> LB[Load balancer]
    LB --> API1[Booking API pod]
    LB --> API2[Booking API pod]
    LB --> API3[Search API pod]

    API3 --> ES[(Search index<br/>shows by city+movie)]
    API1 --> R[(Redis<br/>seat locks with TTL)]
    API2 --> R
    API1 --> DB[(PostgreSQL<br/>show_seat, booking, payment)]
    API2 --> DB
    API1 --> PG[Payment gateway]
    PG -.webhook.-> API2
    API1 --> Q[[Event queue]]
    Q --> N[Notification service<br/>email / SMS ticket]
    Q --> Sweeper[Lock sweeper<br/>expire stale LOCKED rows]
    Sweeper --> DB
```

| In-JVM piece | Production replacement |
|---|---|
| `synchronized (show.getLock())` | Redis `SET NX PX` per seat, or conditional `UPDATE` on `show_seat` |
| `ScheduledExecutorService` expiry | Redis key TTL + DB sweeper |
| `ConcurrentHashMap` registries | PostgreSQL + cache |
| `findShows` linear scan | Search index keyed by city and movie |
| Synchronous `pay()` | Gateway redirect + webhook, payment `PENDING` until callback |

---

## 5. Extension Points

```mermaid
mindmap
  root((Movie Booking))
    Pricing
      Matinee discount
      Surge on premiere night
      Coupon codes
    Payment
      UPI
      Wallet
      Net banking
    Booking
      Cancel and refund
      Booking history
      Seat hold extension
    Inventory
      ShowSeat per show
      Overlapping show check
      Booking cut-off at start time
    Notifications
      Email ticket
      SMS reminder
    Search
      By language and genre
      By cinema and date
```
