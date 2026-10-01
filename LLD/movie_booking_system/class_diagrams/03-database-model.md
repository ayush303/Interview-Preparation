# Movie Booking System — Database Modelling

> How the in-memory design would be persisted in a relational database, and how the
> seat-locking guarantees carry over to SQL. The conceptual model is in
> [02 — ER Diagram](02-er-diagram.md).

---

## 1. Physical Schema Diagram

```mermaid
erDiagram
    city ||--o{ cinema : ""
    cinema ||--|{ screen : ""
    screen ||--|{ seat : ""
    movie ||--o{ show : ""
    screen ||--o{ show : ""
    show ||--|{ show_seat_price : ""
    show ||--|{ show_seat : ""
    seat ||--o{ show_seat : ""
    customer ||--o{ booking : ""
    show ||--o{ booking : ""
    booking ||--o{ show_seat : ""
    booking ||--o{ payment : ""

    city {
        uuid id PK
        varchar name "NOT NULL, UNIQUE"
    }
    cinema {
        varchar id PK
        varchar name "NOT NULL"
        uuid city_id FK "NOT NULL"
    }
    screen {
        varchar id PK
        varchar cinema_id FK "NOT NULL"
    }
    seat {
        uuid id PK
        varchar screen_id FK "NOT NULL"
        smallint row_no "NOT NULL"
        smallint col_no "NOT NULL"
        seat_type type "NOT NULL"
    }
    movie {
        varchar id PK
        varchar title "NOT NULL"
        int duration_minutes "NOT NULL"
    }
    show {
        varchar id PK
        varchar movie_id FK "NOT NULL"
        varchar screen_id FK "NOT NULL"
        timestamptz start_time "NOT NULL"
        pricing_type pricing_strategy "NOT NULL"
    }
    show_seat_price {
        varchar show_id PK
        seat_type type PK
        numeric price "NOT NULL, >= 0"
    }
    show_seat {
        varchar show_id PK
        uuid seat_id PK
        seat_status status "NOT NULL"
        uuid locked_by FK "NULL"
        timestamptz lock_expires_at "NULL"
        uuid booking_id FK "NULL"
        int version "NOT NULL, optimistic lock"
    }
    customer {
        uuid id PK
        varchar name "NOT NULL"
        varchar email "NOT NULL, UNIQUE"
    }
    booking {
        uuid id PK
        uuid customer_id FK "NOT NULL"
        varchar show_id FK "NOT NULL"
        numeric total_amount "NOT NULL"
        timestamptz created_at "NOT NULL"
    }
    payment {
        uuid id PK
        uuid booking_id FK "NULL until confirmed"
        numeric amount "NOT NULL"
        varchar transaction_id "NOT NULL, UNIQUE"
        payment_status status "NOT NULL"
    }
```

---

## 2. DDL (PostgreSQL)

```sql
CREATE TYPE seat_type      AS ENUM ('REGULAR', 'PREMIUM', 'RECLINER');
CREATE TYPE seat_status    AS ENUM ('AVAILABLE', 'LOCKED', 'BOOKED');
CREATE TYPE payment_status AS ENUM ('SUCCESS', 'FAILURE', 'PENDING');
CREATE TYPE pricing_type   AS ENUM ('WEEKDAY', 'WEEKEND');

CREATE TABLE city (
    id   UUID PRIMARY KEY,
    name VARCHAR(100) NOT NULL UNIQUE
);

CREATE TABLE cinema (
    id      VARCHAR(50) PRIMARY KEY,
    name    VARCHAR(200) NOT NULL,
    city_id UUID NOT NULL REFERENCES city(id)
);

CREATE TABLE screen (
    id        VARCHAR(50) PRIMARY KEY,
    cinema_id VARCHAR(50) NOT NULL REFERENCES cinema(id) ON DELETE CASCADE
);

CREATE TABLE seat (
    id        UUID PRIMARY KEY,
    screen_id VARCHAR(50) NOT NULL REFERENCES screen(id) ON DELETE CASCADE,
    row_no    SMALLINT NOT NULL,
    col_no    SMALLINT NOT NULL,
    type      seat_type NOT NULL,
    UNIQUE (screen_id, row_no, col_no)
);

CREATE TABLE movie (
    id               VARCHAR(50) PRIMARY KEY,
    title            VARCHAR(300) NOT NULL,
    duration_minutes INT NOT NULL CHECK (duration_minutes > 0)
);

CREATE TABLE show (
    id               VARCHAR(50) PRIMARY KEY,
    movie_id         VARCHAR(50) NOT NULL REFERENCES movie(id),
    screen_id        VARCHAR(50) NOT NULL REFERENCES screen(id),
    start_time       TIMESTAMPTZ NOT NULL,
    pricing_strategy pricing_type NOT NULL
);

CREATE TABLE show_seat_price (
    show_id VARCHAR(50) NOT NULL REFERENCES show(id) ON DELETE CASCADE,
    type    seat_type   NOT NULL,
    price   NUMERIC(10,2) NOT NULL CHECK (price >= 0),
    PRIMARY KEY (show_id, type)
);

CREATE TABLE customer (
    id    UUID PRIMARY KEY,
    name  VARCHAR(200) NOT NULL,
    email VARCHAR(320) NOT NULL UNIQUE
);

CREATE TABLE booking (
    id           UUID PRIMARY KEY,
    customer_id  UUID NOT NULL REFERENCES customer(id),
    show_id      VARCHAR(50) NOT NULL REFERENCES show(id),
    total_amount NUMERIC(10,2) NOT NULL,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE show_seat (
    show_id         VARCHAR(50) NOT NULL REFERENCES show(id) ON DELETE CASCADE,
    seat_id         UUID NOT NULL REFERENCES seat(id),
    status          seat_status NOT NULL DEFAULT 'AVAILABLE',
    locked_by       UUID REFERENCES customer(id),
    lock_expires_at TIMESTAMPTZ,
    booking_id      UUID REFERENCES booking(id),
    version         INT NOT NULL DEFAULT 0,
    PRIMARY KEY (show_id, seat_id),
    CHECK ( (status = 'AVAILABLE' AND locked_by IS NULL AND booking_id IS NULL)
         OR (status = 'LOCKED'    AND locked_by IS NOT NULL AND lock_expires_at IS NOT NULL)
         OR (status = 'BOOKED'    AND booking_id IS NOT NULL) )
);

CREATE TABLE payment (
    id             UUID PRIMARY KEY,
    booking_id     UUID REFERENCES booking(id),
    amount         NUMERIC(10,2) NOT NULL,
    transaction_id VARCHAR(100) NOT NULL UNIQUE,
    status         payment_status NOT NULL
);
```

The `CHECK` on `show_seat` makes the seat state machine
([05 § 1](05-state-diagrams.md#1-seat-lifecycle-per-show)) impossible to violate at the
storage layer.

---

## 3. Indexes and the Queries They Serve

| Query | Index |
|---|---|
| `findShows(title, city)` | `movie(lower(title))`, `cinema(city_id)`, `show(movie_id, start_time)` |
| Seat map for a show | PK `show_seat(show_id, seat_id)` |
| Expire stale locks | partial `show_seat(lock_expires_at) WHERE status = 'LOCKED'` |
| "My bookings" | `booking(customer_id, created_at DESC)` |
| Payment by gateway id | UNIQUE `payment(transaction_id)` |

```sql
CREATE INDEX idx_movie_title        ON movie (lower(title));
CREATE INDEX idx_cinema_city        ON cinema (city_id);
CREATE INDEX idx_show_movie_time    ON show (movie_id, start_time);
CREATE INDEX idx_show_seat_expiring ON show_seat (lock_expires_at) WHERE status = 'LOCKED';
CREATE INDEX idx_booking_customer   ON booking (customer_id, created_at DESC);
```

---

## 4. Concurrency at the Database Layer

The Java code serialises per show with `synchronized (show.getLock())`. Across many app
servers that monitor no longer exists, so the database must provide the same guarantee.

### Lock seats (all-or-nothing), conditional update

```sql
BEGIN;
UPDATE show_seat
   SET status = 'LOCKED', locked_by = :customer,
       lock_expires_at = now() + interval '10 minutes', version = version + 1
 WHERE show_id = :show
   AND seat_id = ANY(:seats)
   AND (status = 'AVAILABLE'
        OR (status = 'LOCKED' AND lock_expires_at < now()));   -- reclaim expired
-- if rows_affected <> cardinality(:seats) → ROLLBACK (someone else holds a seat)
COMMIT;
```

### Confirm after payment

```sql
UPDATE show_seat
   SET status = 'BOOKED', booking_id = :booking, locked_by = NULL, lock_expires_at = NULL
 WHERE show_id = :show AND seat_id = ANY(:seats)
   AND status = 'LOCKED' AND locked_by = :customer AND lock_expires_at >= now();
-- rows_affected <> cardinality(:seats) → ROLLBACK and refund (mirrors confirmSeats)
```

```mermaid
sequenceDiagram
    participant A as Alice (txn)
    participant DB as show_seat
    participant B as Bob (txn)
    A->>DB: UPDATE ... SET LOCKED WHERE status='AVAILABLE' (A5, A6)
    B->>DB: UPDATE ... SET LOCKED WHERE status='AVAILABLE' (A5)
    Note over DB: row lock on A5 held by Alice's txn,<br/>Bob's UPDATE waits
    DB-->>A: 2 rows
    A->>DB: COMMIT
    DB-->>B: re-evaluates WHERE: A5 is LOCKED → 0 rows
    B->>DB: ROLLBACK, "seat taken"
```

| Java mechanism | Database equivalent |
|---|---|
| `synchronized (show.getLock())` | row locks taken by the conditional `UPDATE` |
| `seat.getStatus() != AVAILABLE` check | `WHERE status = 'AVAILABLE'` |
| `lockedSeats[show][seat] = userId` | `locked_by` column |
| `ScheduledFuture` expiry | `lock_expires_at` + sweeper job, or reclaim in the `WHERE` |
| `confirmSeats` ownership re-check | `WHERE locked_by = :customer AND lock_expires_at >= now()` |

At very high contention (premiere night) the lock usually moves to Redis
(`SET seat:{show}:{seat} {customer} NX PX 600000`) with the database as the system of
record. See [07 § 4](07-usecase-component-deployment.md#4-deployment-view).

---

## 5. In-Memory Storage Map (what the Java code actually holds)

```mermaid
flowchart LR
    subgraph MBS[MovieBookingService]
        C["cities<br/>ConcurrentHashMap&lt;id, City&gt;"]
        CI["cinemas<br/>ConcurrentHashMap&lt;id, Cinema&gt;"]
        M["movies<br/>ConcurrentHashMap&lt;id, Movie&gt;"]
        U["users<br/>ConcurrentHashMap&lt;id, Customer&gt;"]
        S["shows<br/>ConcurrentHashMap&lt;id, Show&gt;"]
        IDX["screenToCinema<br/>ConcurrentHashMap&lt;Screen, Cinema&gt;"]
    end
    subgraph SLM[SeatLockManager]
        L["lockedSeats<br/>ConcurrentHashMap&lt;Show, HashMap&lt;Seat, userId&gt;&gt;"]
        E["expireTasks<br/>ConcurrentHashMap&lt;Show, HashMap&lt;Seat, ScheduledFuture&gt;&gt;"]
        SCH["scheduler<br/>ScheduledThreadPool(1)"]
    end
    S -. key .-> L
    S -. key .-> E
    SCH -- fires unlockSeats --> L
```

| Structure | Thread safety |
|---|---|
| Registries | `ConcurrentHashMap`, safe for concurrent put/get |
| Inner `HashMap`s in `SeatLockManager` | only touched inside `synchronized (show.getLock())` |
| `Seat.status` | plain field, only written inside the show lock; reads outside the lock (seat map display) may be momentarily stale |

---

## 6. Mapping Java → Tables

| Java | Table | Note |
|---|---|---|
| `City` | `city` | |
| `Cinema` | `cinema` | `screens` list → `screen.cinema_id` |
| `Screen` | `screen` | `seats` list → `seat.screen_id` |
| `Seat` (row, col, type) | `seat` | layout only |
| `Seat.status` | `show_seat.status` | moved to per-show table |
| `Movie` | `movie` | |
| `Show` | `show` | `seatPrices` → `show_seat_price`, strategy → enum column |
| `Customer` | `customer` | |
| `Booking` | `booking` | `seats` → `show_seat.booking_id` |
| `Payment` | `payment` | |
| `SeatLockManager.lockedSeats` | `show_seat.locked_by`, `lock_expires_at` | |
