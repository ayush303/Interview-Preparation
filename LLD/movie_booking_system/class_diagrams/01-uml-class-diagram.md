# Movie Booking System — UML Class Diagrams

> The **static structure** of the Java code in [`../solutions/`](../solutions/): every
> class, its fields and methods, and how the classes relate. Behaviour over time is in
> [04 — Sequence Diagrams](04-sequence-diagrams.md).

---

## 1. Full Class Diagram

```mermaid
classDiagram
    direction TB

    class MovieBookingService {
        <<Singleton>>
        -MovieBookingService instance$
        -Map~String,City~ cities
        -Map~String,Cinema~ cinemas
        -Map~String,Movie~ movies
        -Map~String,Customer~ users
        -Map~String,Show~ shows
        -Map~Screen,Cinema~ screenToCinema
        -SeatLockManager seatLockManager
        -BookingManager bookingManager
        -MovieBookingService()
        +getInstance()$ MovieBookingService
        +addCity(name) City
        +addCinema(id, name, cityId, screens) Cinema
        +addMovie(movie) void
        +addShow(id, movie, screen, startTime, seatPrices, pricingStrategy) Show
        +createUser(name, email) Customer
        +bookTickets(userId, showId, seats, paymentStrategy) Optional~Booking~
        +findShows(movieTitle, cityName) List~Show~
        -findCinemaForShow(show) Cinema
        +shutdown() void
    }

    class BookingManager {
        -SeatLockManager seatLockManager
        +createBooking(user, show, seats, paymentStrategy) Optional~Booking~
    }

    class SeatLockManager {
        -Map~Show,Map~Seat,String~~ lockedSeats
        -Map~Show,Map~Seat,ScheduledFuture~~ expireTasks
        -ScheduledExecutorService scheduler
        -long LOCK_TIMEOUT_MS$
        +lockSeats(show, seats, userId) boolean
        +confirmSeats(show, seats, userId) boolean
        +unlockSeats(show, seats, userId) void
        -cancelExpiry(show, seat) void
        +shutdown() void
    }

    class City {
        -String id
        -String name
    }
    class Cinema {
        -String id
        -String name
        -City city
        -List~Screen~ screens
        +addScreen(screen) void
    }
    class Screen {
        -String id
        -List~Seat~ seats
        +addSeat(seat) void
    }
    class Seat {
        -String id
        -int row
        -int col
        -SeatType type
        -SeatStatus status
        +setStatus(status) void
        +setType(type) void
    }
    class Movie {
        -String id
        -String title
        -int durationInMinutes
    }
    class Show {
        -String id
        -Movie movie
        -Screen screen
        -LocalDateTime startTime
        -Map~SeatType,Double~ seatPrices
        -PricingStrategy pricingStrategy
        -Object lock
        +getLock() Object
    }
    class Customer {
        -String id
        -String name
        -String email
    }
    class Booking {
        -String id
        -Customer user
        -Show show
        -List~Seat~ seats
        -double totalAmount
        -Payment payment
        +addSeat(seat) void
    }
    class Payment {
        -String id
        -double amount
        -String transactionId
        -PaymentStatus status
    }

    class PricingStrategy {
        <<interface>>
        +calculatePrice(seats, seatPrices) double
    }
    class WeekdayPricingStrategy {
        +calculatePrice(seats, seatPrices) double
    }
    class WeekendPricingStrategy {
        -double WEEKEND_SURCHARGE$
        +calculatePrice(seats, seatPrices) double
    }
    class PaymentStrategy {
        <<interface>>
        +pay(amount) Payment
        +refund(payment) void
    }
    class CreditCardPaymentStrategy {
        -String cardNumber
        -String cvv
        +pay(amount) Payment
        +refund(payment) void
    }

    class SeatType {
        <<enumeration>>
        REGULAR
        PREMIUM
        RECLINER
    }
    class SeatStatus {
        <<enumeration>>
        AVAILABLE
        LOCKED
        BOOKED
    }
    class PaymentStatus {
        <<enumeration>>
        SUCCESS
        FAILURE
        PENDING
    }

    MovieBookingService "1" o-- "*" City
    MovieBookingService "1" o-- "*" Cinema
    MovieBookingService "1" o-- "*" Movie
    MovieBookingService "1" o-- "*" Show
    MovieBookingService "1" o-- "*" Customer
    MovieBookingService "1" *-- "1" SeatLockManager
    MovieBookingService "1" *-- "1" BookingManager
    BookingManager --> SeatLockManager
    BookingManager ..> PaymentStrategy : uses per call
    BookingManager ..> Booking : creates

    Cinema "*" --> "1" City
    Cinema "1" *-- "1..*" Screen
    Screen "1" *-- "*" Seat
    Seat --> SeatType
    Seat --> SeatStatus
    Show "*" --> "1" Movie
    Show "*" --> "1" Screen
    Show --> PricingStrategy
    Booking "*" --> "1" Customer
    Booking "*" --> "1" Show
    Booking "*" --> "1..*" Seat
    Booking "1" --> "1" Payment
    Payment --> PaymentStatus

    PricingStrategy <|.. WeekdayPricingStrategy
    PricingStrategy <|.. WeekendPricingStrategy
    PaymentStrategy <|.. CreditCardPaymentStrategy
    PaymentStrategy ..> Payment : creates
```

---

## 2. Package / Layer View

```mermaid
flowchart TB
    subgraph API["solutions (orchestration)"]
        MBS[MovieBookingService<br/>facade + singleton]
        BM[BookingManager<br/>booking transaction]
        SLM[SeatLockManager<br/>seat concurrency]
        DEMO[MovieBookingDemo]
    end
    subgraph MODELS["solutions.models"]
        M1[City · Cinema · Screen · Seat]
        M2[Movie · Show]
        M3[Customer · Booking · Payment]
    end
    subgraph ENUMS["solutions.enums"]
        E[SeatType · SeatStatus · PaymentStatus]
    end
    subgraph STRAT["solutions.strategy"]
        P[pricing<br/>PricingStrategy · Weekday · Weekend]
        PAY[payment<br/>PaymentStrategy · CreditCard]
    end

    DEMO --> MBS
    MBS --> BM --> SLM
    MBS --> MODELS
    BM --> MODELS
    BM --> PAY
    SLM --> MODELS
    MODELS --> ENUMS
    M2 --> P
    STRAT --> MODELS
```

Dependencies point downward only: orchestration depends on models and strategies,
models depend on enums. Nothing in `models` knows about `SeatLockManager`.

---

## 3. Design-Pattern Overlay

```mermaid
classDiagram
    direction LR

    class MovieBookingService {
        <<Singleton · Facade>>
        +getInstance()$
        +bookTickets()
        +findShows()
    }
    class BookingManager {
        <<Compensating transaction>>
        +createBooking()
    }
    class SeatLockManager {
        <<Lease-based pessimistic lock>>
        +lockSeats()
        +confirmSeats()
        +unlockSeats()
    }
    class PricingStrategy {
        <<Strategy>>
    }
    class PaymentStrategy {
        <<Strategy>>
    }
    class Show {
        <<Context for pricing>>
    }

    MovieBookingService --> BookingManager
    BookingManager --> SeatLockManager
    BookingManager ..> PaymentStrategy
    Show --> PricingStrategy
    PricingStrategy <|.. WeekdayPricingStrategy
    PricingStrategy <|.. WeekendPricingStrategy
    PaymentStrategy <|.. CreditCardPaymentStrategy
```

| Pattern | Participant(s) | Role |
|---|---|---|
| Singleton | `MovieBookingService` | One instance, one `SeatLockManager`, one source of truth for locks |
| Facade | `MovieBookingService` | Single entry point hiding `BookingManager` and `SeatLockManager` |
| Strategy | `PricingStrategy`, `PaymentStrategy` | Swappable pricing rules and payment methods |
| Lease / pessimistic lock | `SeatLockManager` | Exclusive seat hold that expires on its own |
| Compensating transaction | `BookingManager` | Unlock on payment failure, refund on lost lock |

---

## 4. Strategy Pattern Close-up

```mermaid
classDiagram
    direction LR
    class Show {
        -PricingStrategy pricingStrategy
        -Map~SeatType,Double~ seatPrices
        +getPricingStrategy()
        +getSeatPrices()
    }
    class PricingStrategy {
        <<interface>>
        +calculatePrice(List~Seat~, Map~SeatType,Double~) double
    }
    class WeekdayPricingStrategy {
        sum of seatPrices[seat.type]
    }
    class WeekendPricingStrategy {
        sum × 1.2
    }
    class MatineePricingStrategy {
        <<future>>
        sum × 0.8
    }

    class BookingManager {
        +createBooking(..., PaymentStrategy)
    }
    class PaymentStrategy {
        <<interface>>
        +pay(double) Payment
        +refund(Payment) void
    }
    class CreditCardPaymentStrategy
    class UpiPaymentStrategy {
        <<future>>
    }
    class WalletPaymentStrategy {
        <<future>>
    }

    Show o-- PricingStrategy
    PricingStrategy <|.. WeekdayPricingStrategy
    PricingStrategy <|.. WeekendPricingStrategy
    PricingStrategy <|.. MatineePricingStrategy
    BookingManager ..> PaymentStrategy
    PaymentStrategy <|.. CreditCardPaymentStrategy
    PaymentStrategy <|.. UpiPaymentStrategy
    PaymentStrategy <|.. WalletPaymentStrategy
```

The two strategies are bound at different times. **Pricing** is fixed when the show is
scheduled (`addShow`), because it is a property of the show. **Payment** is chosen at
checkout (`bookTickets`), because it is a choice of the customer. `<<future>>` classes
are not in the code; they show where new policies plug in.

---

## 5. `SeatLockManager` Close-up

```mermaid
classDiagram
    direction LR
    class SeatLockManager {
        -ConcurrentHashMap~Show, HashMap~Seat,String~~ lockedSeats
        -ConcurrentHashMap~Show, HashMap~Seat,ScheduledFuture~~ expireTasks
        -ScheduledExecutorService scheduler
        -LOCK_TIMEOUT_MS = 500$
        +lockSeats(show, seats, userId) boolean
        +confirmSeats(show, seats, userId) boolean
        +unlockSeats(show, seats, userId) void
        -cancelExpiry(show, seat) void
        +shutdown() void
    }
    class Show {
        -Object lock
        +getLock() Object
    }
    class Seat {
        -SeatStatus status
    }
    class ScheduledFuture {
        <<java.util.concurrent>>
        +cancel(boolean)
    }
    SeatLockManager ..> Show : synchronized(show.getLock())
    SeatLockManager ..> Seat : AVAILABLE ↔ LOCKED → BOOKED
    SeatLockManager --> ScheduledFuture : one expiry task per lock batch
```

The inner `HashMap`s are not thread-safe on their own, which is fine: they are only
read or written while holding `show.getLock()` for the show that keys them.

---

## 6. The Singleton

```mermaid
classDiagram
    class MovieBookingService {
        <<Singleton>>
        -volatile MovieBookingService instance$
        -MovieBookingService()
        +getInstance()$ MovieBookingService
    }
    note for MovieBookingService "if (instance == null)\n  synchronized (MovieBookingService.class)\n    if (instance == null)\n      instance = new MovieBookingService()\nreturn instance"
```

`volatile` stops another thread from seeing the reference before the constructor has
finished, and the second `null` check stops two threads that both passed the first
check from building two instances.

---

## 7. Notation Cheatsheet

| Arrow | Meaning | Example here |
|---|---|---|
| `<\|..` | implements | `WeekdayPricingStrategy` implements `PricingStrategy` |
| `*--` | composition (part dies with whole) | `Screen *-- Seat` |
| `o--` | aggregation (held, not owned) | `MovieBookingService o-- Show` |
| `-->` | association (field reference) | `Show --> Movie` |
| `..>` | dependency (parameter / local) | `BookingManager ..> PaymentStrategy` |
| `$` | static member | `getInstance()$` |
