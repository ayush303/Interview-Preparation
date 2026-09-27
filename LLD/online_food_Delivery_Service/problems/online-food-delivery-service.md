# Designing an Online Food Delivery Service (Swiggy / Zomato / DoorDash-style)

## Problem Statement

Design the core of an online food delivery platform: the part that connects a hungry
**customer**, a **restaurant** that cooks, and a **delivery agent** who carries the food
between them.

A customer searches for restaurants (by city, by distance from where they are, by what
is on the menu, or by any combination of those), browses a restaurant's menu, and places
an order for one or more items. The order then moves through a fixed lifecycle:

```
PENDING → CONFIRMED → PREPARING → READY_FOR_PICKUP → OUT_FOR_DELIVERY → DELIVERED
   └──────────┴──→ CANCELLED   (only before the kitchen starts cooking)
```

The restaurant confirms and cooks the order. The moment the food is ready, the system
must **automatically find a delivery agent** and send them out. When the order is
delivered, that agent becomes free for the next order.

Three things make this more than a CRUD app.

First, **everyone involved in an order has to hear about every change to it**. The
customer wants "your food is being prepared". The restaurant wants to know an order was
cancelled. The agent wants to know the order they picked up has been marked delivered.
The `Order` must not have a hard-coded list of who to call. People who care about an
order subscribe to it, and the order broadcasts its changes to them.

Second, **how restaurants are searched and how agents are chosen are business policies
that change**. Today search means "same city" and agent selection means "nearest free
agent". Tomorrow it might be "open now", "rating ≥ 4", "least busy agent", or "agent
with an insulated bag for ice-cream orders". Adding a policy must not mean editing the
service class that uses it.

Third, **it is a concurrent system**. Hundreds of orders become ready at the same time,
and two of them must never grab the same free delivery agent.

---

## Functional Requirements

1. **Register participants.** The system registers **customers** (name, phone,
   delivery address), **restaurants** (name, address, menu) and **delivery agents**
   (name, phone, current location). Each gets a system-generated unique id.

2. **Manage menus.** A restaurant owns exactly one menu, made up of menu items (id,
   name, price, availability). Items can be added and marked available or unavailable.

3. **Search restaurants.** A customer can search restaurants with one or more
   composable filters:
   - **By city.** Exact city match.
   - **By proximity.** Within a maximum distance of a given address, nearest first.
   - **By menu keyword.** Restaurants serving at least one item whose name contains
     the keyword (case-insensitive).

   Filters are applied in sequence, so `[proximity, keyword]` means "burger places
   near me".

4. **Browse a menu.** Fetch the full menu of a restaurant by restaurant id.

5. **Place an order.** A customer orders one or more `(menuItem, quantity)` lines from
   a single restaurant. The system rejects the order if:
   - the customer or restaurant does not exist,
   - there are no items,
   - any quantity is ≤ 0,
   - any item is unavailable.

   On success the order is created in `PENDING`, added to the customer's order history,
   and its total is the sum of `price × quantity` over all lines.

6. **Order lifecycle.** An order moves only along these transitions:

   | From | Allowed next states |
   |---|---|
   | `PENDING` | `CONFIRMED`, `CANCELLED` |
   | `CONFIRMED` | `PREPARING`, `CANCELLED` |
   | `PREPARING` | `READY_FOR_PICKUP` |
   | `READY_FOR_PICKUP` | `OUT_FOR_DELIVERY` |
   | `OUT_FOR_DELIVERY` | `DELIVERED` |
   | `DELIVERED` | *(terminal)* |
   | `CANCELLED` | *(terminal)* |

   Any other transition is rejected with an error naming the illegal `from → to` pair.

7. **Cancel an order.** A customer can cancel an order only while it is `PENDING` or
   `CONFIRMED`. Once the kitchen has started (`PREPARING` and later) cancellation is
   refused and the caller is told the current status.

8. **Automatic delivery assignment.** When an order becomes `READY_FOR_PICKUP` the
   system picks a delivery agent using a pluggable **assignment strategy**, marks that
   agent busy, attaches them to the order, and moves the order to `OUT_FOR_DELIVERY`.
   The default strategy picks the available agent with the smallest
   `agent → restaurant + restaurant → customer` distance.

9. **Release the agent on delivery.** When an order becomes `DELIVERED`, its agent is
   marked available again.

10. **Notifications.** Every status change is pushed to all parties subscribed to that
    order: the customer and restaurant from creation, and the delivery agent from the
    moment they are assigned.

11. **Single point of control.** One `FoodDeliveryService` instance owns every registry
    (customers, restaurants, agents, orders) and the active assignment strategy. It is
    reached through a static accessor, and the strategy can be swapped at runtime.

---

## Non-Functional Requirements

1. **Open/Closed for policies.** A new search filter is a new class implementing
   `RestaurantSearchStrategy`. A new agent-selection policy is a new class implementing
   `DeliveryAssignmentStrategy`. Neither requires editing `FoodDeliveryService`.

2. **Loose coupling of notifications.** `Order` knows only the `Observer` interface.
   Adding SMS, push, email or an analytics sink means implementing `Observer`, not
   touching the order lifecycle.

3. **Thread safety.**
   - All registries are `ConcurrentHashMap`s.
   - An agent is claimed with an atomic compare-and-set (`AtomicBoolean.compareAndSet(true, false)`),
     so two orders becoming ready at the same instant can never both win the same agent.
   - `Order.setStatus` is `synchronized`, and observers live in a `CopyOnWriteArrayList`
     so a subscription added mid-broadcast cannot throw `ConcurrentModificationException`.

4. **Safe lazy singleton.** `getInstance()` uses double-checked locking on a `volatile`
   field, so the service is built once and no thread sees a half-constructed object.

5. **Fail fast on bad input.** Unknown ids, empty orders, non-positive quantities,
   unavailable items and illegal transitions raise specific exceptions
   (`NoSuchElementException`, `IllegalArgumentException`, `IllegalStateException`)
   rather than returning `null`.

6. **Single source of truth for the lifecycle.** The legal transitions live in one
   immutable `Map<OrderStatus, Set<OrderStatus>>`. It can be read at a glance, tested
   exhaustively, and changed in one place.

7. **Low latency for the hot paths.** Order lookup, restaurant lookup and agent claim
   are O(1). Search is O(R) over restaurants per filter. That is fine for an interview
   scale, and sections 3 and 7 of the diagrams show how it would move to a geo-index at
   production scale.

8. **Separation of concerns.** Domain models (`models`), lifecycle vocabulary
   (`enums`), notification contracts (`observer`), policies (`strategies/search`,
   `strategies/assignment`) and orchestration (`FoodDeliveryService`) live in separate
   packages.

---

## Core Entities and Their Relationships

| Entity | Responsibility |
|---|---|
| `FoodDeliveryService` | Singleton facade. Owns all registries, validates and drives the order lifecycle, triggers delivery assignment, runs searches. |
| `User` *(abstract)* | Common identity (`id`, `name`, `phone`) for people. Implements `Observer`. |
| `Customer` | A `User` with a delivery `Address` and an order history. |
| `DeliveryAgent` | A `User` with a current location and an atomic availability flag. |
| `Restaurant` | Has an `Address` and exactly one `Menu`. Implements `Observer` to hear about its orders. |
| `Menu` | A map of `MenuItem`s keyed by item id. |
| `MenuItem` | Id, name, price, availability (and a stock counter reserved for future use). |
| `Order` | The `Subject`. Links one customer, one restaurant, a list of `OrderItem`s, an optional `DeliveryAgent`, a status and its observers. |
| `OrderItem` | One order line: a `MenuItem` and a quantity. Computes its own subtotal. |
| `Address` | Value object: street, city, zip, lat/long, and `distanceTo`. |
| `OrderStatus` | The seven lifecycle values. |
| `RestaurantSearchStrategy` | Filter over a list of restaurants. Three implementations. |
| `DeliveryAssignmentStrategy` | Picks an agent for an order. `NearestAvailableAgentStrategy` is the default. |

```mermaid
classDiagram
    direction LR

    class FoodDeliveryService {
        <<Singleton>>
    }
    class User {
        <<abstract>>
    }
    class Observer {
        <<interface>>
    }
    class Subject {
        <<interface>>
    }
    class RestaurantSearchStrategy {
        <<interface>>
    }
    class DeliveryAssignmentStrategy {
        <<interface>>
    }
    class OrderStatus {
        <<enumeration>>
    }

    User <|-- Customer
    User <|-- DeliveryAgent
    Observer <|.. User
    Observer <|.. Restaurant
    Subject <|.. Order

    FoodDeliveryService "1" o-- "*" Customer : registers
    FoodDeliveryService "1" o-- "*" Restaurant : registers
    FoodDeliveryService "1" o-- "*" DeliveryAgent : registers
    FoodDeliveryService "1" o-- "*" Order : tracks
    FoodDeliveryService --> DeliveryAssignmentStrategy : uses
    FoodDeliveryService ..> RestaurantSearchStrategy : applies

    Restaurant "1" *-- "1" Menu : owns
    Menu "1" *-- "*" MenuItem : contains
    Restaurant --> Address : located at
    Customer --> Address : delivers to
    DeliveryAgent --> Address : currently at

    Customer "1" --> "*" Order : history
    Order "*" --> "1" Customer : placed by
    Order "*" --> "1" Restaurant : placed at
    Order "*" --> "0..1" DeliveryAgent : delivered by
    Order "1" *-- "1..*" OrderItem : lines
    OrderItem "*" --> "1" MenuItem : refers to
    Order --> OrderStatus : status
    Order "1" o-- "*" Observer : notifies
```

**Reading the arrows.** `<|--` is inheritance, `<|..` is interface realisation, `*--`
is composition (the part cannot exist without the whole: a `Menu` dies with its
`Restaurant`, an `OrderItem` with its `Order`), `o--` is aggregation (the service holds
customers but did not give them their identity), `-->` is a plain association, and
`..>` is a transient dependency (search strategies are passed in per call, not stored).

---

## Design Patterns Used

| Pattern | Where | Why |
|---|---|---|
| **Singleton** | `FoodDeliveryService.getInstance()`, double-checked locking with a `volatile` instance and a private constructor | One owner for all registries, so an order placed through one caller is visible to every other caller. |
| **Strategy** (assignment) | `DeliveryAssignmentStrategy` → `NearestAvailableAgentStrategy`, injected with `setAssignmentStrategy` | Agent selection is a business policy that can be swapped at runtime (nearest, least-loaded, highest-rated, round-robin) without editing the service. |
| **Strategy** (search), composed like a pipeline | `RestaurantSearchStrategy` → `SearchByCity`, `SearchByProximity`, `SearchByMenuKeyword` | Each filter is a small class. `searchRestaurants` folds a list of them over the restaurant set, so combined searches need no new code. It works like a Chain of Responsibility / Specification pattern without the ceremony. |
| **Observer** | `Order` implements `Subject`. `Customer`, `DeliveryAgent` (via `User`) and `Restaurant` implement `Observer` | The order broadcasts status changes without knowing who is listening. A new notification channel is a new `Observer`. |
| **Facade** | `FoodDeliveryService` | Clients call one object with a handful of methods (`register*`, `placeOrder`, `updateOrderStatus`, `cancel`, `searchRestaurants`, `getRestaurantMenu`) and never touch registries or strategies directly. |
| **Table-driven state machine** | `VALID_TRANSITIONS: Map<OrderStatus, Set<OrderStatus>>` | The order lifecycle is enforced as data, not as a class per state. See the next section for why. |

---

## Why the State Pattern Was *Not* Used

The order clearly *has* states, so the obvious question in an interview is: "Why not a
`PendingState`, `ConfirmedState` and so on, as in the ticket management system in this
same repo?" Here is the answer.

**1. The State pattern pays off when behaviour differs per state. Here only legality
differs.** In the ticket system, `Ticket` exposes three different operations
(`assignTo`, `markInProgress`, `resolveTicket`), and each one *means something
different* in each state: reassign here, no-op there, reject somewhere else. That is
3 × 4 = 12 behaviours, and a state object per row is the clean way to hold them.
`Order` exposes effectively one operation, "move to status X", and the only question
every state answers is *"is X in my allowed set?"*. That is a lookup, not polymorphism.
Seven state classes that each contain `return allowed.contains(next)` would be
boilerplate hiding a table.

**2. The lifecycle is a linear pipeline with one side exit.** Six forward steps and a
single `CANCELLED` branch available from two states. Every rule fits in the seven-line
`VALID_TRANSITIONS` map, which you can read in five seconds and print as a table (see
FR 6). With the State pattern the same information is spread across seven files, and
answering "which states can be cancelled?" means opening all of them.

**3. Transitions are driven from outside by different actors, through one API.** The
restaurant moves `CONFIRMED → PREPARING → READY_FOR_PICKUP`, the system moves
`READY_FOR_PICKUP → OUT_FOR_DELIVERY`, the agent moves `→ DELIVERED`, and the customer
moves `→ CANCELLED`. All of them go through `updateOrderStatus(orderId, newStatus)`.
The State pattern is strongest when the *object itself* decides where it goes next in
response to method calls. Here an external actor names the target and the system only
has to validate it.

**4. Side effects are few and attached to a target status, not to a source state.**
There are exactly two: *entering* `READY_FOR_PICKUP` triggers agent assignment, and
*entering* `DELIVERED` frees the agent. Two `if (newStatus == …)` hooks express that
directly. With the State pattern they would become `onEnter()` methods on two of the
seven classes, and the other five would be empty.

**5. Data-driven rules are easier to test, persist and change.** One exhaustive
parameterised test over the 7 × 7 status pairs covers the whole lifecycle. The same map
can be loaded from config or stored as a `order_status_transition` table (see the
database model), which a class hierarchy cannot. Adding a status such as
`PICKED_UP` between `READY_FOR_PICKUP` and `OUT_FOR_DELIVERY` is one enum constant and
two map entries.

**6. No class explosion, no extra indirection.** The enum is also what gets
serialised, logged, shown in the UI and stored in the database. With the State pattern
you would still need that enum for persistence *plus* a mapping from enum to state
object, which is two representations of the same fact.

### When I would switch to the State pattern

The trade-off flips as soon as states start behaving differently rather than merely
allowing different successors. Signals to watch for:

- **Cancellation cost depends on the state.** Free in `PENDING`, restaurant fee in
  `CONFIRMED`, partial refund in `PREPARING`, not allowed after pickup.
- **States gain timers or entry/exit work.** Auto-cancel a `PENDING` order the
  restaurant ignores for 5 minutes, start live GPS tracking on `OUT_FOR_DELIVERY` and
  stop it on exit, escalate a `READY_FOR_PICKUP` order that no agent accepts.
- **More operations per order.** `modifyItems()`, `addTip()`, `changeAddress()`,
  `reportIssue()`, each allowed or behaving differently depending on the stage.

At that point every new operation would add another `switch (status)` across the
service. That growing conditional is exactly the smell the State pattern removes, and
`Order` should then delegate to an `OrderState` object the way `Ticket` does.

### The cost of the table approach, visible in this code

One discipline comes with a table: **every** transition must go through it. The current
`Order.cancel()` re-implements the rule inline (`PENDING || CONFIRMED`) instead of
consulting `VALID_TRANSITIONS`, so the lifecycle now has two sources of truth that can
drift apart. The fix is to route `cancel` through `updateOrderStatus(id, CANCELLED)`.

---

## ⚠️ Known Gaps in the Current Implementation

The diagrams document the code **as it is today**. Divergences are marked ⚠️ wherever
they appear. The first two were confirmed by compiling and running the demo.

| # | Gap | Where | Effect | Fix |
|---|---|---|---|---|
| 1 | Customer address is read from `order.getDeliveryAgent().getAddress()`, but no agent is assigned yet | `NearestAvailableAgentStrategy.findAgent` | **`NullPointerException` on every assignment.** The status has already been set to `READY_FOR_PICKUP` and broadcast, so the order is stuck with no agent. | Use `order.getCustomer().getAddress()`. |
| 2 | Item name is lowercased but the keyword is not | `SearchByMenuKeywordStrategy` | Searching `"Pizza"` returns nothing. In the demo this silently skips the entire order-placement section. | `keyword.toLowerCase()` in the constructor. |
| 3 | Status is set and broadcast *before* assignment, and nothing handles "no agent found" | `updateOrderStatus` / `assignDelivery` | If assignment fails or no agent is free, the order stays in `READY_FOR_PICKUP` forever with no retry. | Put unassigned orders on a pending-assignment queue and retry when an agent is freed. |
| 4 | Validate-then-set is not atomic: the transition check runs in the service, outside the order's lock | `updateOrderStatus`, `Order.cancel` | Two threads (for example restaurant → `PREPARING` and customer → `CANCELLED`, both from `CONFIRMED`) can both pass the check, and whichever writes last wins. | Move the check inside a `synchronized` `Order.transitionTo(next)`, or use CAS on an `AtomicReference<OrderStatus>`. |
| 5 | `cancel()` has its own rule and prints instead of throwing | `Order.cancel`, `FoodDeliveryService.cancel` | Two sources of lifecycle truth. Inconsistent error model (exceptions vs `System.out`). | Route through `VALID_TRANSITIONS` and throw like every other operation. |
| 6 | `stock` defaults to 0 and `reverse()` (sic, *reserve*) is never called | `MenuItem`, `placeOrder` | No inventory control. The availability check is also check-then-act. Calling `reverse(1)` on a new item returns `false`. | Reserve stock atomically in `placeOrder` and release it on cancel. |
| 7 | Items are not checked to belong to the chosen restaurant | `placeOrder` | You can order a Burger Barn item "from" Pizza Palace. | Verify `restaurant.getMenu().getItem(id) == item`. |
| 8 | `notifyObservers` runs inside the `synchronized` `setStatus`, with no per-observer `try/catch` | `Order` | A slow observer (SMS gateway) holds the order lock. A throwing observer aborts the loop, so later observers never hear about a change that already happened. | Snapshot the status, release the lock, notify each observer in its own `try/catch` (or publish to an async event bus). |
| 9 | `Order` notifies from its constructor, and `placeOrder` then calls `setStatus(PENDING)`, which is a no-op | `Order`, `placeOrder` | `this` escapes during construction. The extra call is dead code. | Construct first, then publish an `ORDER_PLACED` event from the service. |
| 10 | `orderHistory` is a plain `ArrayList` | `Customer` | Concurrent orders by the same customer can corrupt it. | `CopyOnWriteArrayList` or `Collections.synchronizedList`. |
| 11 | `claim()` is followed by a redundant `setIsAvailable(false)`, and the agent is released from *inside* `Order.setStatus` | `assignDelivery`, `Order` | One aggregate mutates another as a hidden side effect of a setter. The agent's location is not updated to the drop-off point either. | Release the agent in the service (`onDelivered`) and move them to the customer's address. |
| 12 | `OrderStatus` declares `CONFIRMED` before `PENDING` | `enums/OrderStatus` | `ordinal()` and `compareTo` do not follow the lifecycle, which bites if the value is persisted by ordinal or sorted. | Declare in lifecycle order. |
| 13 | Money is `double`. Distance is Euclidean on raw lat/long degrees | `MenuItem`, `Address` | Rounding errors in totals. Distances are wrong away from the equator. | `BigDecimal` for money, Haversine for distance. |
| 14 | Naming: `IsAvailable()`, `reverse()` | `DeliveryAgent`, `MenuItem` | Violates Java conventions and misleads readers. | `isAvailable()`, `reserve()`. |
| 15 | No payments, ratings, ETA, `removeObserver`, or order lookup by customer | throughout | Natural extension points. | See [07 § 5](../class_diagrams/07-usecase-component-deployment.md#5-extension-points). |

---

## Implementation

#### [Java Implementation](../solution/)

## Diagrams

#### [Design Diagrams](../class_diagrams/)
