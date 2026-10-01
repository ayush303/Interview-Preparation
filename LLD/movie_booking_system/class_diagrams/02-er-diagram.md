# Movie Booking System — Entity Relationship Diagrams

> The **conceptual data** view: which things exist, what describes them, and how they
> relate. There are no Java types or methods here, only entities, attributes and
> cardinalities. The physical tables, column types and indexes are in
> [03 — Database Model](03-database-model.md).

---

## 1. Conceptual ER Diagram (Chen-style, entities and relationships only)

```mermaid
flowchart LR
    CITY[CITY]
    CIN[CINEMA]
    SCR[SCREEN]
    SEAT[SEAT]
    MOV[MOVIE]
    SHOW[SHOW]
    SS[SHOW_SEAT]
    CUST[CUSTOMER]
    BOOK[BOOKING]
    PAY[PAYMENT]

    in{in}
    has_scr{has}
    has_seat{has}
    plays{plays}
    on{on}
    inv{inventory of}
    for_seat{for}
    makes{makes}
    of{of}
    reserves{reserves}
    paid{paid by}

    CIN ---|N| in ---|1| CITY
    CIN ---|1| has_scr ---|N| SCR
    SCR ---|1| has_seat ---|N| SEAT
    SHOW ---|N| plays ---|1| MOV
    SHOW ---|N| on ---|1| SCR
    SHOW ---|1| inv ---|N| SS
    SS ---|N| for_seat ---|1| SEAT
    CUST ---|1| makes ---|N| BOOK
    BOOK ---|N| of ---|1| SHOW
    BOOK ---|1| reserves ---|1..N| SS
    BOOK ---|1| paid ---|1..N| PAY
```

Rectangles are entities and diamonds are relationships. The labels on each edge are the
cardinality at that end.

> **`SHOW_SEAT` is not a class in the Java code.** Today seat status lives on `Seat`,
> which is shared by every show on the screen (see
> [Known Limitations](../problems/movie-booking-system.md#known-limitations)). The data
> model fixes that: a seat's *layout* belongs to the screen, a seat's *availability*
> belongs to the show.

---

## 2. Logical ER Diagram (crow's foot)

```mermaid
erDiagram
    CITY ||--o{ CINEMA : "contains"
    CINEMA ||--|{ SCREEN : "has"
    SCREEN ||--|{ SEAT : "has"
    MOVIE ||--o{ SHOW : "scheduled as"
    SCREEN ||--o{ SHOW : "hosts"
    SHOW ||--|{ SHOW_SEAT : "has inventory"
    SEAT ||--o{ SHOW_SEAT : "appears in"
    CUSTOMER ||--o{ BOOKING : "makes"
    SHOW ||--o{ BOOKING : "sold in"
    BOOKING ||--|{ SHOW_SEAT : "reserves"
    BOOKING ||--|{ PAYMENT : "paid by"
    SHOW ||--|{ SHOW_SEAT_PRICE : "prices"

    CITY {
        string id PK
        string name
    }
    CINEMA {
        string id PK
        string name
        string city_id FK
    }
    SCREEN {
        string id PK
        string cinema_id FK
    }
    SEAT {
        string id PK
        string screen_id FK
        int row_no
        int col_no
        string seat_type "REGULAR | PREMIUM | RECLINER"
    }
    MOVIE {
        string id PK
        string title
        int duration_minutes
    }
    SHOW {
        string id PK
        string movie_id FK
        string screen_id FK
        datetime start_time
        string pricing_strategy "WEEKDAY | WEEKEND"
    }
    SHOW_SEAT_PRICE {
        string show_id PK, FK
        string seat_type PK
        decimal price
    }
    SHOW_SEAT {
        string show_id PK, FK
        string seat_id PK, FK
        string status "AVAILABLE | LOCKED | BOOKED"
        string locked_by FK "customer, nullable"
        datetime lock_expires_at "nullable"
        string booking_id FK "nullable"
    }
    CUSTOMER {
        string id PK
        string name
        string email UK
    }
    BOOKING {
        string id PK
        string customer_id FK
        string show_id FK
        decimal total_amount
        datetime created_at
    }
    PAYMENT {
        string id PK
        string booking_id FK
        decimal amount
        string transaction_id UK
        string status "SUCCESS | FAILURE | PENDING"
    }
```

### Reading the crow's foot

| Symbol | Meaning |
|---|---|
| `\|\|` | exactly one |
| `o\|` | zero or one |
| `\|{` | one or more |
| `o{` | zero or more |

---

## 3. Relationship & Cardinality Table

| Relationship | Cardinality | Notes |
|---|---|---|
| City – Cinema | 1 : N | A cinema is in exactly one city. |
| Cinema – Screen | 1 : N | Composition: a screen does not outlive its cinema. |
| Screen – Seat | 1 : N | The physical layout. Fixed once the screen is built. |
| Movie – Show | 1 : N | A movie runs many shows. |
| Screen – Show | 1 : N | Shows on the same screen must not overlap in time. |
| Show – ShowSeat | 1 : N | One row per seat of the show's screen, created with the show. |
| Seat – ShowSeat | 1 : N | The same physical seat, once per show. |
| Customer – Booking | 1 : N | Booking history. |
| Show – Booking | 1 : N | All bookings for a show. |
| Booking – ShowSeat | 1 : N | A ShowSeat belongs to at most one booking. |
| Booking – Payment | 1 : N | Usually one; more if a retry or refund is recorded. |

---

## 4. Resolving Seat ↔ Show (the many-to-many)

A seat is used by many shows, and a show uses many seats. Status, lock holder and
booking cannot live on either side, so they live on the association.

```mermaid
flowchart LR
    subgraph Before["Java code today"]
        S1[Show 2 pm] --> SCR1[Screen 1]
        S2[Show 8 pm] --> SCR1
        SCR1 --> A5["Seat A5<br/>status = BOOKED"]
    end
    subgraph After["Data model"]
        T1[Show 2 pm] --> SS1["SHOW_SEAT<br/>(2 pm, A5)<br/>BOOKED"]
        T2[Show 8 pm] --> SS2["SHOW_SEAT<br/>(8 pm, A5)<br/>AVAILABLE"]
        SS1 --> B5[Seat A5<br/>layout only]
        SS2 --> B5
    end
```

On the left, booking A5 for 2 pm also blocks it for 8 pm. On the right, each show has
its own copy of the seat's availability.
