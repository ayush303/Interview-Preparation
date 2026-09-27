# Online Food Delivery Service — Entity Relationship Diagrams

> The **conceptual data** view: which things exist, what describes them, and how they
> relate. There are no Java types or methods here, only entities, attributes and
> cardinalities. The physical tables, column types and indexes are in
> [03 — Database Model](03-database-model.md).

---

## 1. Conceptual ER Diagram (Chen-style, entities and relationships only)

```mermaid
flowchart LR
    CUST[CUSTOMER]
    REST[RESTAURANT]
    AGENT[DELIVERY_AGENT]
    MENU[MENU]
    ITEM[MENU_ITEM]
    ORD[ORDER]
    LINE[ORDER_ITEM]
    ADDR[ADDRESS]

    places{places}
    receives{receives}
    owns{owns}
    contains{contains}
    delivers{delivers}
    has_line{has}
    refers{refers to}
    located{located at}
    lives{delivers to}
    at{currently at}

    CUST ---|1| places ---|N| ORD
    REST ---|1| receives ---|N| ORD
    REST ---|1| owns ---|1| MENU
    MENU ---|1| contains ---|N| ITEM
    AGENT ---|0..1| delivers ---|N| ORD
    ORD ---|1| has_line ---|1..N| LINE
    LINE ---|N| refers ---|1| ITEM
    REST ---|N| located ---|1| ADDR
    CUST ---|N| lives ---|1| ADDR
    AGENT ---|N| at ---|1| ADDR
```

Rectangles are entities and diamonds are relationships. The labels on each edge are the
cardinality at that end.

---

## 2. Logical ER Diagram (crow's foot)

```mermaid
erDiagram
    CUSTOMER ||--o{ ORDER : "places"
    RESTAURANT ||--o{ ORDER : "receives"
    DELIVERY_AGENT |o--o{ ORDER : "delivers"
    RESTAURANT ||--|| MENU : "owns"
    MENU ||--o{ MENU_ITEM : "contains"
    ORDER ||--|{ ORDER_ITEM : "has lines"
    MENU_ITEM ||--o{ ORDER_ITEM : "ordered as"
    ADDRESS ||--o{ CUSTOMER : "home of"
    ADDRESS ||--o{ RESTAURANT : "location of"
    ADDRESS ||--o{ DELIVERY_AGENT : "current position of"
    ORDER_STATUS ||--o{ ORDER : "classifies"
    ORDER ||--o{ ORDER_OBSERVER : "notifies"

    CUSTOMER {
        string id PK "UUID"
        string name
        string phone
        string addressId FK
    }

    RESTAURANT {
        string id PK "UUID"
        string name
        string addressId FK
    }

    DELIVERY_AGENT {
        string id PK "UUID"
        string name
        string phone
        boolean isAvailable "flipped atomically by claim()"
        string currentAddressId FK
    }

    MENU {
        string restaurantId PK "1:1 with restaurant, so it shares the key"
    }

    MENU_ITEM {
        string id PK "caller-supplied, e.g. P001"
        string restaurantId FK
        string name
        double price
        boolean available
        int stock "unused today"
    }

    ORDER {
        string id PK "UUID"
        string customerId FK
        string restaurantId FK
        string deliveryAgentId FK "null until OUT_FOR_DELIVERY"
        string status FK
    }

    ORDER_ITEM {
        string orderId FK
        string menuItemId FK
        int quantity "must be > 0"
    }

    ADDRESS {
        string street
        string city
        string zipCode
        double latitude
        double longitude
    }

    ORDER_STATUS {
        string code PK "PENDING ... CANCELLED"
    }

    ORDER_OBSERVER {
        string orderId FK
        string observerId "customer, restaurant or agent id"
        string observerType
    }
```

### Reading the crow's foot

| Symbol | Meaning |
|---|---|
| `\|\|` | exactly one |
| `\|o` | zero or one |
| `\|{` | one or more |
| `o{` | zero or more |

---

## 3. Relationship & Cardinality Table

| Relationship | Cardinality | Mandatory? | Where it lives in Java |
|---|---|---|---|
| Customer places Order | 1 : N | Order → Customer mandatory | `Order.customer` (final) and `Customer.orderHistory` |
| Restaurant receives Order | 1 : N | Order → Restaurant mandatory | `Order.restaurant` (final) |
| DeliveryAgent delivers Order | 0..1 : N | optional until pickup | `Order.deliveryAgent` (mutable, set once) |
| Restaurant owns Menu | 1 : 1 | yes, created in the constructor | `Restaurant.menu` (final) |
| Menu contains MenuItem | 1 : N | a menu may be empty | `Menu.items` map |
| Order has OrderItem | 1 : 1..N | at least one line (validated) | `Order.items` (final list) |
| OrderItem refers to MenuItem | N : 1 | yes | `OrderItem.item` (final) |
| Customer / Restaurant / Agent → Address | N : 1 | yes | embedded value object |
| Order notifies Observer | 1 : N | customer and restaurant always, agent later | `Order.observers` |

### Is `ADDRESS` an entity or a value object?

In Java `Address` has **no id**. Two equal addresses are interchangeable, and it is
shared by reference (the demo passes `aliceAddress` to both the customer and a search
strategy). That makes it a **value object**. The logical ER diagram draws it as a
separate box for clarity. The physical model in [03](03-database-model.md) embeds it
as columns for restaurants and agents, and gives customers an `address` table because
a real customer has several saved addresses (Home, Work).

---

## 4. Many-to-Many Resolution

```mermaid
erDiagram
    ORDER }o--o{ MENU_ITEM : "logically many-to-many"
```

`ORDER ↔ MENU_ITEM` is conceptually many-to-many (an order has many items, and an item
appears in many orders). `ORDER_ITEM` is the **associative entity** that resolves it
and carries the relationship's own attributes (`quantity`, and in the physical model
`unit_price`):

```mermaid
erDiagram
    ORDER ||--|{ ORDER_ITEM : ""
    MENU_ITEM ||--o{ ORDER_ITEM : ""
    ORDER_ITEM {
        string orderId PK, FK
        string menuItemId PK, FK
        int quantity
        decimal unitPrice "snapshot at order time"
    }
```

> **Why snapshot the price?** `OrderItem.getSubTotal()` reads `item.getPrice()` live.
> The price is `final` today so it is safe, but the moment a restaurant can edit prices,
> an old order's total would silently change. Persisted orders must copy the price at
> checkout.
