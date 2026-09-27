# Online Food Delivery Service — Activity Diagrams & Flowcharts

> Control flow *inside* each operation: every branch, guard and exit. Sequence diagrams
> ([04](04-sequence-diagrams.md)) show *who* talks to whom. These show *how* a single
> method decides.

---

## 1. Master Flow — from hungry to fed

```mermaid
flowchart TD
    A([Customer opens app]) --> B[Search restaurants<br/>city / proximity / keyword]
    B --> C{Any results?}
    C -- no --> B
    C -- yes --> D[Browse menu]
    D --> E[Pick items and quantities]
    E --> F[placeOrder]
    F --> G{Valid?}
    G -- no --> E
    G -- yes --> H[/PENDING/]
    H --> I{Restaurant accepts?}
    I -- customer cancels --> X[/CANCELLED/]
    I -- yes --> J[/CONFIRMED/]
    J -- customer cancels --> X
    J --> K[/PREPARING/]
    K --> L[/READY_FOR_PICKUP/]
    L --> M{Free agent?}
    M -- no --> M2[⚠️ stuck: no retry today]
    M -- yes --> N[/OUT_FOR_DELIVERY/]
    N --> O[/DELIVERED/]
    O --> P[Agent released]
    P --> Z([Done])
    X --> Z
```

---

## 2. `placeOrder(customerId, restaurantId, items)`

```mermaid
flowchart TD
    S([placeOrder]) --> A[customer = customers.get<br/>restaurant = restaurants.get]
    A --> B{both non-null?}
    B -- no --> E1[[throw NoSuchElementException]]
    B -- yes --> C{items null or empty?}
    C -- yes --> E2[[throw IllegalArgumentException<br/>at least one item]]
    C -- no --> L{next OrderItem?}
    L -- yes --> Q{quantity > 0?}
    Q -- no --> E3[[throw IllegalArgumentException<br/>quantity must be positive]]
    Q -- yes --> AV{item.isAvailable?}
    AV -- no --> E4[[throw IllegalStateException<br/>X is not available]]
    AV -- yes --> L
    L -- done --> N[new Order: status PENDING<br/>observers = customer, restaurant<br/>notifyObservers]
    N --> P[orders.put]
    P --> H[customer.addToOrderHistory]
    H --> SS[setStatus PENDING: no-op]
    SS --> R([return order])

    style E1 fill:#fdd
    style E2 fill:#fdd
    style E3 fill:#fdd
    style E4 fill:#fdd
```

Missing checks (⚠️): the item belongs to this restaurant, stock is reserved, and the
restaurant is open.

---

## 3. `updateOrderStatus(orderId, newStatus)`

```mermaid
flowchart TD
    S([updateOrderStatus]) --> A[order = orders.get]
    A --> B{found?}
    B -- no --> E1[[throw NoSuchElementException]]
    B -- yes --> C{newStatus in<br/>VALID_TRANSITIONS of current?}
    C -- no --> E2[[throw IllegalStateException<br/>Invalid transition: X -> Y]]
    C -- yes --> D[order.setStatus newStatus<br/>synchronized]
    D --> D1{status actually changed?}
    D1 -- no --> R
    D1 -- yes --> D2{newStatus == DELIVERED<br/>and agent != null?}
    D2 -- yes --> D3[agent.setIsAvailable true]
    D2 -- no --> D4
    D3 --> D4[notifyObservers]
    D4 --> F{newStatus ==<br/>READY_FOR_PICKUP?}
    F -- yes --> G[[assignDelivery order]]
    F -- no --> R([return])
    G --> R

    style E1 fill:#fdd
    style E2 fill:#fdd
```

⚠️ The decision at `C` and the write at `D` are not inside the same lock (see
[04 Flow 10](04-sequence-diagrams.md#flow-10--race-restaurant-starts-cooking-while-customer-cancels)).

---

## 4. `assignDelivery(order)` + `NearestAvailableAgentStrategy.findAgent`

```mermaid
flowchart TD
    S([assignDelivery]) --> A[agents = copy of deliveryAgents.values]
    A --> B[[strategy.findAgent order, agents]]
    B --> B1[restaurantAddr = order.restaurant.address]
    B1 --> B2[customerAddr = order.deliveryAgent.address]
    B2 --> NPE{deliveryAgent null?}
    NPE -- "yes (always today)" --> X[[⚠️ NullPointerException]]
    NPE -- no --> B3[filter IsAvailable]
    B3 --> B4[min by agent→restaurant + restaurant→customer]
    B4 --> C{Optional present?}
    C -- no --> Z1([return: order stranded ⚠️])
    C -- yes --> D{agent.claim CAS?}
    D -- false: lost race --> Z1
    D -- true --> E[order.assignDeliveryAgent agent<br/>addObserver agent]
    E --> F[order.setStatus OUT_FOR_DELIVERY]
    F --> Z2([return])

    style X fill:#fdd
    style Z1 fill:#ffe9b3
```

Note that `restaurant → customer` is the same for every agent, so it cannot change the
ranking. Only `agent → restaurant` matters. The term would matter for batched
multi-drop routing.

**Corrected version:**

```mermaid
flowchart TD
    S([assignDelivery]) --> A[candidates = available agents<br/>sorted by distance to restaurant]
    A --> B{next candidate?}
    B -- no --> Q[pendingAssignments.offer order<br/>retry when any agent is released]
    B -- yes --> C{claim CAS?}
    C -- no --> B
    C -- yes --> D[assign, OUT_FOR_DELIVERY]
    Q --> R([return])
    D --> R
```

---

## 5. `searchRestaurants(strategies)`: the filter fold

```mermaid
flowchart LR
    ALL[(all restaurants)] --> F1[filter 1<br/>City = Springfield]
    F1 --> F2[filter 2<br/>within 0.01 of Alice<br/>sorted by distance]
    F2 --> F3[filter 3<br/>menu contains keyword]
    F3 --> OUT[(results)]
```

```mermaid
flowchart TD
    S([searchRestaurants]) --> A[results = list of all restaurants]
    A --> B{next strategy?}
    B -- yes --> C[results = strategy.filter results]
    C --> B
    B -- no --> R([return results])
```

An empty strategy list returns every restaurant. Order matters only for cost (and for
ordering: a proximity filter sorts, and later filters keep that order).

---

## 6. `cancel(orderId)`

```mermaid
flowchart TD
    S([cancel]) --> A[order = orders.get]
    A --> B{found?}
    B -- no --> P1[/print ERROR not found/]
    B -- yes --> C{status PENDING<br/>or CONFIRMED?}
    C -- yes --> D[setStatus CANCELLED<br/>notify]
    D --> P2[/print SUCCESS/]
    C -- no --> P3[/print FAILED, status is X/]
    P1 --> R([return])
    P2 --> R
    P3 --> R
```

⚠️ This path prints where every other path throws, and it re-states the rule instead of
using `VALID_TRANSITIONS`.

---

## 7. Double-Checked Locking in `getInstance`

```mermaid
flowchart TD
    S([getInstance]) --> A{instance == null?<br/>volatile read, no lock}
    A -- no --> R([return instance])
    A -- yes --> L[synchronized lock]
    L --> B{instance == null?<br/>re-check under lock}
    B -- no --> U[unlock] --> R
    B -- yes --> N[instance = new FoodDeliveryService<br/>volatile write publishes safely]
    N --> U
```

---

## 8. Activity Diagram with Swimlanes — one order, four actors

```mermaid
flowchart TB
    subgraph Customer
        c1([Search and browse]) --> c2[Place order]
        c9[Receive 🔔 updates]
        c10([Enjoy food])
    end
    subgraph System["FoodDeliveryService"]
        s1[Validate and create Order: PENDING]
        s2[Validate transition]
        s3[Find and claim agent]
        s4[OUT_FOR_DELIVERY]
        s5[Release agent]
    end
    subgraph Restaurant
        r1[Receive 🔔 new order]
        r2[Confirm]
        r3[Prepare]
        r4[Mark ready]
    end
    subgraph Agent["Delivery Agent"]
        a1[Receive 🔔 assignment]
        a2[Pick up and ride]
        a3[Mark delivered]
    end

    c2 --> s1 --> r1 --> r2 --> s2
    s2 --> r3 --> r4 --> s3 --> s4 --> a1 --> a2 --> a3 --> s5 --> c10
    s1 -.-> c9
    s2 -.-> c9
    s4 -.-> c9
    s5 -.-> c9
```

---

## 9. How to add a new status (`PICKED_UP`) with the table approach

```mermaid
flowchart LR
    A[Add PICKED_UP to OrderStatus enum] --> B[READY_FOR_PICKUP → OUT_FOR_DELIVERY<br/>becomes<br/>READY_FOR_PICKUP → PICKED_UP → OUT_FOR_DELIVERY]
    B --> C[Edit two VALID_TRANSITIONS entries]
    C --> D[Update the assignment hook if needed]
    D --> E[Done: no new classes]
```

Compare with the State pattern, where the same change is a new `PickedUpState` class
plus edits to `ReadyForPickupState` to point at it.

## 10. How to add a new assignment policy

```mermaid
flowchart LR
    A[class LeastLoadedAgentStrategy<br/>implements DeliveryAssignmentStrategy] --> B[service.setAssignmentStrategy<br/>new LeastLoadedAgentStrategy]
    B --> C[Done: FoodDeliveryService untouched]
```
