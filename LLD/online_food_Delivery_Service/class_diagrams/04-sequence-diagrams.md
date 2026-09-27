# Online Food Delivery Service — Sequence Diagrams

> The **behavioural** view over time: who calls whom, in what order, for each use case.
> Flows 1–6 are the happy and validation paths. Flows 7–10 are where the design is
> tested: failures, races and the two ⚠️ bugs the demo hides.

---

## Flow 1 — Bootstrapping: singleton, strategy, registration

```mermaid
sequenceDiagram
    autonumber
    actor Admin as Client (Demo)
    participant FDS as FoodDeliveryService
    participant C as Customer
    participant R as Restaurant
    participant M as Menu
    participant DA as DeliveryAgent

    Admin->>FDS: getInstance()
    alt instance == null
        FDS->>FDS: synchronized(lock), re-check, instance = new FDS()
    end
    FDS-->>Admin: instance
    Admin->>FDS: setAssignmentStrategy(new NearestAvailableAgentStrategy())

    Admin->>FDS: registerCustomer("Alice", phone, aliceAddress)
    FDS->>C: new Customer(...) → UUID id
    FDS->>FDS: customers.put(id, customer)
    FDS-->>Admin: alice

    Admin->>FDS: registerRestaurant("Pizza Palace", pizzaAddress)
    FDS->>R: new Restaurant(...)
    R->>M: new Menu()
    FDS->>FDS: restaurants.put(id, restaurant)
    FDS-->>Admin: pizzaPalace

    Admin->>FDS: registerDeliveryAgent("Bob", phone, location)
    FDS->>DA: new DeliveryAgent(...) (isAvailable = true)
    FDS->>FDS: deliveryAgents.put(id, agent)

    Admin->>R: addToMenu(new MenuItem("P001","Margherita Pizza",12.99))
    R->>M: addItem(item)
```

---

## Flow 2 — Composed restaurant search (Strategy pipeline)

The demo's query *"burger joints near Alice"*.

```mermaid
sequenceDiagram
    autonumber
    actor Cust as Customer (client)
    participant FDS as FoodDeliveryService
    participant P as SearchByProximityStrategy
    participant K as SearchByMenuKeywordStrategy
    participant A as Address

    Cust->>FDS: searchRestaurants([Proximity(alice, 0.01), Keyword("Burger")])
    FDS->>FDS: results = new ArrayList(restaurants.values())  // [Pizza, Burger, Taco]

    FDS->>P: filter(results)
    loop each restaurant
        P->>A: alice.distanceTo(r.address)
    end
    P->>P: keep ≤ 0.01, sort ascending
    P-->>FDS: [Pizza Palace, Burger Barn]

    FDS->>K: filter([Pizza Palace, Burger Barn])
    loop each restaurant → each menu item
        K->>K: item.name.toLowerCase().contains("Burger")
        Note right of K: ⚠️ "classic burger".contains("Burger") == false<br/>keyword is never lowercased
    end
    K-->>FDS: []  ⚠️ expected [Burger Barn]
    FDS-->>Cust: []
```

The output of each filter is the input to the next: AND-composition by folding.
Putting the most selective, cheapest filter first minimises work downstream.

---

## Flow 3 — Place an order

```mermaid
sequenceDiagram
    autonumber
    actor Cust as Customer (client)
    participant FDS as FoodDeliveryService
    participant MI as MenuItem
    participant O as Order
    participant C as Customer
    participant R as Restaurant

    Cust->>FDS: placeOrder(customerId, restaurantId, [OrderItem(P001, 1)])
    FDS->>FDS: customer = customers.get(id), restaurant = restaurants.get(id)
    alt customer or restaurant missing
        FDS-->>Cust: throw NoSuchElementException
    end
    alt items null or empty
        FDS-->>Cust: throw IllegalArgumentException
    end
    loop each OrderItem
        alt quantity <= 0
            FDS-->>Cust: throw IllegalArgumentException
        end
        FDS->>MI: isAvailable()
        alt not available
            FDS-->>Cust: throw IllegalStateException("... is not available")
        end
    end
    Note over FDS,MI: ⚠️ no stock reservation, no check that the item belongs to this restaurant

    FDS->>O: new Order(customer, restaurant, items)
    activate O
    O->>O: id = UUID, status = PENDING
    O->>O: addObserver(customer), addObserver(restaurant)
    O->>C: onUpdate(order)  "Order … is now PENDING"
    O->>R: onUpdate(order)  "Order … updated to PENDING"
    deactivate O
    Note over O: ⚠️ notifying from inside the constructor lets `this` escape

    FDS->>FDS: orders.put(order.id, order)
    FDS->>C: addToOrderHistory(order)
    FDS->>O: setStatus(PENDING)
    O-->>FDS: no-op (already PENDING)
    FDS-->>Cust: order
    Cust->>O: getTotalAmount()  → Σ price × qty = 12.99
```

---

## Flow 4 — Restaurant advances the order (validated transition + broadcast)

```mermaid
sequenceDiagram
    autonumber
    actor Rest as Restaurant (client)
    participant FDS as FoodDeliveryService
    participant VT as VALID_TRANSITIONS
    participant O as Order
    participant C as Customer
    participant R as Restaurant

    Rest->>FDS: updateOrderStatus(orderId, CONFIRMED)
    FDS->>FDS: order = orders.get(orderId)
    FDS->>VT: get(PENDING).contains(CONFIRMED)?
    VT-->>FDS: true
    FDS->>O: setStatus(CONFIRMED)  [synchronized]
    O->>C: onUpdate(order)
    O->>R: onUpdate(order)

    Rest->>FDS: updateOrderStatus(orderId, PREPARING)
    FDS->>VT: get(CONFIRMED).contains(PREPARING)?
    VT-->>FDS: true
    FDS->>O: setStatus(PREPARING)
    O->>C: onUpdate(order)
    O->>R: onUpdate(order)
```

---

## Flow 5 — Illegal transition is rejected

```mermaid
sequenceDiagram
    autonumber
    actor Client
    participant FDS as FoodDeliveryService
    participant VT as VALID_TRANSITIONS
    participant O as Order

    Client->>FDS: updateOrderStatus(orderId, DELIVERED)
    FDS->>O: getOrderStatus()
    O-->>FDS: PREPARING
    FDS->>VT: get(PREPARING).contains(DELIVERED)?
    VT-->>FDS: false  (only READY_FOR_PICKUP)
    FDS-->>Client: throw IllegalStateException("Invalid transition: PREPARING -> DELIVERED")
    Note over O: state unchanged, no notifications sent
```

---

## Flow 6 — Cancel an order

```mermaid
sequenceDiagram
    autonumber
    actor Cust as Customer (client)
    participant FDS as FoodDeliveryService
    participant O as Order
    participant C as Customer
    participant R as Restaurant

    Cust->>FDS: cancel(orderId)
    FDS->>FDS: order = orders.get(orderId)
    alt order == null
        FDS-->>Cust: print "ERROR: Order … not found"  ⚠️ no exception
    else found
        FDS->>O: cancel()
        alt status is PENDING or CONFIRMED
            Note right of O: ⚠️ rule duplicated here instead of<br/>consulting VALID_TRANSITIONS
            O->>O: setStatus(CANCELLED)
            O->>C: onUpdate(order)
            O->>R: onUpdate(order)
            O-->>FDS: true
            FDS-->>Cust: print "SUCCESS: … canceled"
        else PREPARING or later
            O-->>FDS: false
            FDS-->>Cust: print "FAILED: … status is PREPARING"
        end
    end
```

---

## Flow 7 — Ready for pickup → automatic assignment (intended design)

What the code is *meant* to do once gap #1 is fixed (customer address taken from
`order.getCustomer()`).

```mermaid
sequenceDiagram
    autonumber
    actor Rest as Restaurant (client)
    participant FDS as FoodDeliveryService
    participant S as NearestAvailableAgentStrategy
    participant DA as DeliveryAgent
    participant O as Order
    participant C as Customer
    participant R as Restaurant

    Rest->>FDS: updateOrderStatus(orderId, READY_FOR_PICKUP)
    FDS->>O: setStatus(READY_FOR_PICKUP)
    O->>C: onUpdate
    O->>R: onUpdate
    FDS->>FDS: assignDelivery(order)
    FDS->>S: findAgent(order, all agents)
    S->>S: filter(IsAvailable)
    S->>S: min(agent→restaurant + restaurant→customer)
    S-->>FDS: Optional(Bob)
    FDS->>DA: claim()  // CAS true → false
    DA-->>FDS: true
    FDS->>O: assignDeliveryAgent(Bob)
    O->>O: deliveryAgent = Bob, addObserver(Bob)
    O->>DA: setIsAvailable(false)  (redundant after claim)
    FDS->>O: setStatus(OUT_FOR_DELIVERY)
    O->>C: onUpdate
    O->>R: onUpdate
    O->>DA: onUpdate  "Order … Status is OUT_FOR_DELIVERY"

    Note over Rest,R: later, the agent hands over the food
    Rest->>FDS: updateOrderStatus(orderId, DELIVERED)
    FDS->>O: setStatus(DELIVERED)
    O->>DA: setIsAvailable(true)  // agent released
    O->>C: onUpdate
    O->>R: onUpdate
    O->>DA: onUpdate
```

---

## Flow 8 — What actually happens today: NPE during assignment

Reproduced by forcing the demo past the broken keyword search.

```mermaid
sequenceDiagram
    autonumber
    actor Rest as Restaurant (client)
    participant FDS as FoodDeliveryService
    participant S as NearestAvailableAgentStrategy
    participant O as Order

    Rest->>FDS: updateOrderStatus(orderId, READY_FOR_PICKUP)
    FDS->>O: setStatus(READY_FOR_PICKUP)
    Note over O: status changed and broadcast ✔
    FDS->>FDS: assignDelivery(order)
    FDS->>S: findAgent(order, agents)
    S->>O: getRestaurant().getAddress()
    O-->>S: pizzaAddress
    S->>O: getDeliveryAgent()
    O-->>S: null  (no agent yet: that is the point of this call)
    S->>S: null.getAddress()
    S--xFDS: NullPointerException
    FDS--xRest: NullPointerException propagates
    Note over O: order stuck in READY_FOR_PICKUP forever<br/>no agent, no retry, customer already told "ready"
```

**Two lessons.** (1) The bug: `order.getDeliveryAgent()` should be
`order.getCustomer()`. (2) The design flaw it exposes: the status is committed and
broadcast *before* the dependent step, with no compensation or retry. Even with the
bug fixed, "no agent free" leads to the same stuck order.

---

## Flow 9 — Race: two orders ready at once, one agent free

```mermaid
sequenceDiagram
    autonumber
    participant T1 as Thread 1 (order A ready)
    participant T2 as Thread 2 (order B ready)
    participant S as Strategy
    participant Bob as DeliveryAgent Bob

    par
        T1->>S: findAgent(A, agents)
        S-->>T1: Bob
    and
        T2->>S: findAgent(B, agents)
        S-->>T2: Bob
    end
    Note over T1,T2: both saw Bob as available (read is not a lock)
    T1->>Bob: claim()  CAS(true→false)
    Bob-->>T1: true ✔
    T2->>Bob: claim()  CAS(true→false)
    Bob-->>T2: false ✘
    T1->>T1: A.assignDeliveryAgent(Bob), A → OUT_FOR_DELIVERY
    T2->>T2: agent == null path: does nothing
    Note over T2: ✅ Bob is never double-booked (CAS works)<br/>⚠️ but order B silently stays READY_FOR_PICKUP.<br/>Fix: loop to the next-best agent, or enqueue B for retry
```

---

## Flow 10 — Race: restaurant starts cooking while customer cancels

```mermaid
sequenceDiagram
    autonumber
    participant T1 as Thread 1 (restaurant)
    participant T2 as Thread 2 (customer)
    participant FDS as FoodDeliveryService
    participant O as Order (status = CONFIRMED)

    T1->>FDS: updateOrderStatus(id, PREPARING)
    T2->>FDS: cancel(id)
    FDS->>O: getOrderStatus() → CONFIRMED  (T1, unlocked read)
    FDS->>FDS: CONFIRMED → PREPARING valid ✔ (T1)
    O->>O: cancel(): status == CONFIRMED ✔ (T2, unlocked read)
    O->>O: setStatus(CANCELLED)  (T2 takes lock)
    O->>O: setStatus(PREPARING)  (T1 takes lock)
    Note over O: ⚠️ final status PREPARING, but the customer was told<br/>"SUCCESS: canceled" and CANCELLED was broadcast.<br/>A terminal state was left, which the table forbids.
```

**Fix.** Make check-and-set one atomic step inside `Order`:

```java
public synchronized void transitionTo(OrderStatus next) {
    if (!VALID_TRANSITIONS.get(orderStatus).contains(next))
        throw new IllegalStateException(orderStatus + " -> " + next);
    orderStatus = next;
}   // then notify observers *outside* the lock
```

---

## Flow 11 — End-to-end happy path (overview)

```mermaid
sequenceDiagram
    autonumber
    actor Cu as Customer
    participant App as FoodDeliveryService
    actor Re as Restaurant
    actor Ag as DeliveryAgent

    Cu->>App: searchRestaurants(filters)
    App-->>Cu: restaurants
    Cu->>App: getRestaurantMenu(id)
    App-->>Cu: menu
    Cu->>App: placeOrder(items)
    App-->>Cu: 🔔 PENDING
    App-->>Re: 🔔 PENDING (new order)
    Re->>App: updateOrderStatus(CONFIRMED)
    App-->>Cu: 🔔 CONFIRMED
    Re->>App: updateOrderStatus(PREPARING)
    App-->>Cu: 🔔 PREPARING
    Re->>App: updateOrderStatus(READY_FOR_PICKUP)
    App->>App: assignDelivery → nearest free agent
    App-->>Ag: 🔔 OUT_FOR_DELIVERY (you've got an order)
    App-->>Cu: 🔔 OUT_FOR_DELIVERY
    Ag->>App: updateOrderStatus(DELIVERED)
    App-->>Cu: 🔔 DELIVERED
    App-->>Re: 🔔 DELIVERED
    App->>App: agent.isAvailable = true
```
