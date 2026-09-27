# Online Food Delivery Service — Use Case, Component, Object & Deployment Diagrams

> The outside-in views: who uses the system for what (§ 1), how the code splits into
> replaceable parts (§ 2), a snapshot of live objects (§ 3), how it would run at scale
> (§ 4), and where it extends (§ 5).

---

## 1. Use Case Diagram

Mermaid has no native use-case diagram, so actors are drawn as rounded nodes and use
cases as stadium nodes inside the system boundary.

```mermaid
flowchart LR
    Customer((👤 Customer))
    Restaurant((🏪 Restaurant))
    Agent((🛵 Delivery Agent))
    Admin((🛠 Admin / Ops))
    Sys((⚙️ System timer / scheduler))

    subgraph FDS["Online Food Delivery Service"]
        UC1([Register])
        UC2([Search restaurants])
        UC2a([by city])
        UC2b([by proximity])
        UC2c([by menu keyword])
        UC3([Browse menu])
        UC4([Place order])
        UC5([Cancel order])
        UC6([Track order / get notified])
        UC7([Manage menu items])
        UC8([Confirm / prepare / mark ready])
        UC9([Assign delivery agent])
        UC10([Mark delivered])
        UC11([Switch assignment strategy])
    end

    Customer --- UC1
    Customer --- UC2
    Customer --- UC3
    Customer --- UC4
    Customer --- UC5
    Customer --- UC6
    Restaurant --- UC1
    Restaurant --- UC7
    Restaurant --- UC8
    Restaurant --- UC6
    Agent --- UC1
    Agent --- UC10
    Agent --- UC6
    Admin --- UC11
    Sys --- UC9

    UC2 -. extends .-> UC2a
    UC2 -. extends .-> UC2b
    UC2 -. extends .-> UC2c
    UC8 -. includes .-> UC9
    UC4 -. includes .-> UC6
```

| Use case | API | Actor |
|---|---|---|
| Register | `registerCustomer / registerRestaurant / registerDeliveryAgent` | all |
| Search | `searchRestaurants(List<RestaurantSearchStrategy>)` | customer |
| Browse menu | `getRestaurantMenu(id)` | customer |
| Place order | `placeOrder(customerId, restaurantId, items)` | customer |
| Cancel | `cancel(orderId)` | customer |
| Advance order | `updateOrderStatus(id, CONFIRMED / PREPARING / READY_FOR_PICKUP)` | restaurant |
| Assign agent | `assignDelivery` (private, triggered by READY_FOR_PICKUP) | system |
| Mark delivered | `updateOrderStatus(id, DELIVERED)` | agent |
| Get notified | `Observer.onUpdate(order)` | customer, restaurant, agent |
| Change policy | `setAssignmentStrategy(...)` | admin |

---

## 2. Component Diagram

```mermaid
flowchart TB
    Client[["Client<br/>(FoodDeliveryServiceDemo / REST controller)"]]

    subgraph Core["FoodDeliveryService «Facade · Singleton»"]
        REG["Registry component<br/>customers · restaurants · agents · orders"]
        LIFE["Order lifecycle component<br/>VALID_TRANSITIONS · updateOrderStatus · cancel"]
        DISP["Dispatch component<br/>assignDelivery"]
        SRCH["Search component<br/>searchRestaurants fold"]
    end

    subgraph Domain["Domain model"]
        ORD["Order «Subject»"]
        PPL["Customer · DeliveryAgent · Restaurant «Observers»"]
        CAT["Menu · MenuItem · OrderItem · Address"]
    end

    IAssign(("DeliveryAssignmentStrategy"))
    ISearch(("RestaurantSearchStrategy"))
    IObs(("Observer"))

    Nearest["NearestAvailableAgentStrategy"]
    City["SearchByCity"]
    Prox["SearchByProximity"]
    Kw["SearchByMenuKeyword"]

    Client --> Core
    REG --> Domain
    LIFE --> ORD
    DISP --> IAssign
    SRCH --> ISearch
    ORD --> IObs
    IAssign -. provided by .- Nearest
    ISearch -. provided by .- City
    ISearch -. provided by .- Prox
    ISearch -. provided by .- Kw
    IObs -. provided by .- PPL
```

| Plug-in point (interface) | To add… | Files touched |
|---|---|---|
| `DeliveryAssignmentStrategy` | least-loaded, rating-based, batching | 1 new class |
| `RestaurantSearchStrategy` | open-now, rating, cuisine, price range | 1 new class |
| `Observer` | SMS, push, email, analytics, ETA service | 1 new class + an `addObserver` call |
| `OrderStatus` + `VALID_TRANSITIONS` | new status | enum + map ⚠️ (closed for modification only by convention) |
| Payments, ratings, promotions | new capability | ⚠️ no seam today, needs a new component |

---

## 3. Object Diagram — snapshot just after `READY_FOR_PICKUP` (intended flow)

```mermaid
flowchart LR
    fds["<u>service : FoodDeliveryService</u>"]
    alice["<u>alice : Customer</u><br/>name = Alice<br/>address = 123 Maple St"]
    pizza["<u>pizzaPalace : Restaurant</u><br/>name = Pizza Palace"]
    menu["<u>menu : Menu</u>"]
    p1["<u>p001 : MenuItem</u><br/>Margherita Pizza · 12.99<br/>available = true"]
    p2["<u>p002 : MenuItem</u><br/>Veggie Pizza · 11.99"]
    bob["<u>bob : DeliveryAgent</u><br/>isAvailable = false"]
    ord["<u>o1 : Order</u><br/>status = OUT_FOR_DELIVERY<br/>total = 12.99"]
    line["<u>line1 : OrderItem</u><br/>quantity = 1"]
    strat["<u>: NearestAvailableAgentStrategy</u>"]

    fds -- customers --> alice
    fds -- restaurants --> pizza
    fds -- deliveryAgents --> bob
    fds -- orders --> ord
    fds -- assignmentStrategy --> strat
    pizza -- menu --> menu
    menu --> p1
    menu --> p2
    ord -- customer --> alice
    ord -- restaurant --> pizza
    ord -- deliveryAgent --> bob
    ord -- items --> line
    line -- item --> p1
    alice -- orderHistory --> ord
    ord -. observers .-> alice
    ord -. observers .-> pizza
    ord -. observers .-> bob
```

---

## 4. Deployment View — from one JVM to production

**Today:** everything lives in one JVM process, in-memory, with the singleton as the
only store.

```mermaid
flowchart LR
    subgraph JVM["Single JVM"]
        Demo --> FDS[FoodDeliveryService singleton]
        FDS --> Maps[(ConcurrentHashMaps)]
    end
```

**At scale:** the singleton becomes a set of stateless services. Each pattern maps onto
a piece of infrastructure.

```mermaid
flowchart TB
    Apps["📱 Customer app · 🏪 Restaurant app · 🛵 Agent app"]
    GW[API Gateway / LB]
    Apps --> GW

    subgraph Services["Stateless services (N replicas each)"]
        OS["Order Service<br/>(lifecycle + transition table)"]
        SS["Search Service<br/>(search strategies)"]
        DS["Dispatch Service<br/>(assignment strategy)"]
        NS["Notification Service<br/>(observers)"]
        CS["Catalog Service<br/>(restaurants, menus)"]
    end

    GW --> OS
    GW --> SS
    GW --> CS

    PG[("PostgreSQL<br/>orders · order_items · status history")]
    ES[("Elasticsearch / PostGIS<br/>geo + full-text restaurant index")]
    RD[("Redis GEO<br/>live agent locations + availability")]
    MQ{{"Kafka<br/>order-events topic"}}

    OS --> PG
    CS --> PG
    CS -. CDC .-> ES
    SS --> ES
    OS -- "OrderStatusChanged" --> MQ
    MQ --> DS
    MQ --> NS
    DS --> RD
    DS -- "assign agent (CAS / SKIP LOCKED)" --> OS
    NS --> Push["FCM / APNs / SMS"]
```

| In-process design | Distributed equivalent |
|---|---|
| `FoodDeliveryService` singleton | stateless replicas behind a load balancer. "One instance" becomes "one source of truth" (the DB) |
| `ConcurrentHashMap` registries | PostgreSQL tables ([03](03-database-model.md)) |
| `synchronized setStatus` | conditional `UPDATE … WHERE status = :from` / optimistic `version` |
| Observer list on `Order` | Kafka topic `order-events`. Each consumer group is an "observer", with retries and isolation for free |
| `AtomicBoolean.claim()` | Redis `SET agent:{id}:busy NX` or `SELECT … FOR UPDATE SKIP LOCKED` |
| Linear `SearchByProximity` | geo index (Redis GEO / PostGIS / Elasticsearch `geo_distance`) |
| Stuck `READY_FOR_PICKUP` order | Dispatch consumer retries with back-off, and escalates after N attempts |

---

## 5. Extension Points

```mermaid
mindmap
  root((Food Delivery LLD))
    Payments
      PaymentStrategy
        Card
        UPI
        Wallet
        Cash on delivery
      Refund rules per status
    Pricing
      Delivery fee strategy
      Surge multiplier
      Coupons via Decorator
    Ratings
      Rate restaurant
      Rate agent
      SearchByRatingStrategy
    Dispatch
      LeastLoadedAgentStrategy
      Batching multi-drop
      Pending-assignment queue
    Lifecycle
      PICKED_UP status
      Auto-cancel timeout
      Switch to State pattern when per-state behaviour grows
    Notifications
      SMS and push observers
      Async event bus
      removeObserver
    Inventory
      reserve and release stock
      Restaurant open hours
```
