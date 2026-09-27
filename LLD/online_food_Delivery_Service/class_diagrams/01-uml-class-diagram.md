# Online Food Delivery Service — UML Class Diagrams

> The structural view. Every field, method and relationship below mirrors the Java
> sources in [`../solution/`](../solution/). ⚠️ marks code that diverges from the
> intended design. See the
> [known gaps](../problems/online-food-delivery-service.md#known-gaps-and-how-they-were-fixed).

---

## 1. Full Class Diagram

```mermaid
classDiagram
    direction TB

    class FoodDeliveryService {
        <<Singleton>>
        -FoodDeliveryService instance$
        -Object lock$
        -Map~OrderStatus, Set~ VALID_TRANSITIONS$
        -Map~String, Customer~ customers
        -Map~String, Restaurant~ restaurants
        -Map~String, DeliveryAgent~ deliveryAgents
        -Map~String, Order~ orders
        -DeliveryAssignmentStrategy assignmentStrategy
        -FoodDeliveryService()
        +getInstance()$ FoodDeliveryService
        +setAssignmentStrategy(DeliveryAssignmentStrategy s) void
        +registerCustomer(String name, String phone, Address address) Customer
        +registerRestaurant(String name, Address address) Restaurant
        +registerDeliveryAgent(String name, String phone, Address loc) DeliveryAgent
        +placeOrder(String customerId, String restaurantId, List~OrderItem~ items) Order
        +updateOrderStatus(String orderId, OrderStatus newStatus) void
        +cancel(String orderId) void
        -assignDelivery(Order order) void
        +searchRestaurants(List~RestaurantSearchStrategy~ strategies) List~Restaurant~
        +getRestaurantMenu(String restaurantId) Menu
    }

    class User {
        <<abstract>>
        -String id
        -String name
        -String phone
        +User(String name, String phone)
        +getId() String
        +getName() String
        +getPhone() String
    }

    class Customer {
        -Address address
        -List~Order~ orderHistory
        +Customer(String name, String phone, Address address)
        +getAddress() Address
        +getOrderHistory() List~Order~
        +addToOrderHistory(Order order) void
        +onUpdate(Order order) void
    }

    class DeliveryAgent {
        -AtomicBoolean isAvailable
        -Address currentLocation
        +DeliveryAgent(String name, String phone, Address address)
        +claim() boolean
        +setIsAvailable(boolean available) void
        +IsAvailable() boolean
        +getAddress() Address
        +setCurrentLocation(Address address) void
        +onUpdate(Order order) void
    }

    class Restaurant {
        -String id
        -String name
        -Address address
        -Menu menu
        +Restaurant(String name, Address address)
        +getId() String
        +getName() String
        +getAddress() Address
        +getMenu() Menu
        +addToMenu(MenuItem item) void
        +onUpdate(Order order) void
    }

    class Menu {
        -Map~String, MenuItem~ items
        +addItem(MenuItem item) void
        +getItem(String id) MenuItem
        +getItems() Map~String, MenuItem~
    }

    class MenuItem {
        -String id
        -String name
        -double price
        -AtomicBoolean available
        -int stock
        +MenuItem(String id, String name, double price)
        +reverse(int quantity) boolean
        +getId() String
        +getName() String
        +getPrice() double
        +isAvailable() boolean
        +setAvailable(boolean available) void
        +getDescription() String
    }

    class Order {
        -String id
        -Customer customer
        -Restaurant restaurant
        -List~OrderItem~ items
        -OrderStatus orderStatus
        -List~Observer~ observers
        -DeliveryAgent deliveryAgent
        +Order(Customer c, Restaurant r, List~OrderItem~ items)
        +setStatus(OrderStatus newStatus) void
        +cancel() boolean
        +assignDeliveryAgent(DeliveryAgent agent) void
        +getTotalAmount() double
        +addObserver(Observer o) void
        +notifyObservers() void
        +getId() String
        +getCustomer() Customer
        +getRestaurant() Restaurant
        +getItems() List~OrderItem~
        +getOrderStatus() OrderStatus
        +getDeliveryAgent() DeliveryAgent
    }

    class OrderItem {
        -MenuItem item
        -int quantity
        +OrderItem(MenuItem item, int quantity)
        +getItem() MenuItem
        +getQuantity() int
        +getSubTotal() double
    }

    class Address {
        -String street
        -String city
        -String zipCode
        -double latitude
        -double longitude
        +Address(String street, String city, String zip, double lat, double lng)
        +getCity() String
        +getStreet() String
        +distanceTo(Address other) double
        +toString() String
    }

    class OrderStatus {
        <<enumeration>>
        CONFIRMED
        PENDING
        PREPARING
        READY_FOR_PICKUP
        OUT_FOR_DELIVERY
        DELIVERED
        CANCELLED
    }

    class Subject {
        <<interface>>
        +addObserver(Observer o) void
        +notifyObservers() void
    }

    class Observer {
        <<interface>>
        +onUpdate(Order order) void
    }

    class DeliveryAssignmentStrategy {
        <<interface>>
        +findAgent(Order order, List~DeliveryAgent~ agents) Optional~DeliveryAgent~
    }

    class NearestAvailableAgentStrategy {
        +findAgent(Order order, List~DeliveryAgent~ agents) Optional~DeliveryAgent~
        -calculateTotalDistance(DeliveryAgent a, Address rest, Address cust) double
    }

    class RestaurantSearchStrategy {
        <<interface>>
        +filter(List~Restaurant~ all) List~Restaurant~
    }

    class SearchByCityStrategy {
        -String city
        +filter(List~Restaurant~ all) List~Restaurant~
    }

    class SearchByProximityStrategy {
        -Address userLocation
        -double maxDistance
        +filter(List~Restaurant~ all) List~Restaurant~
    }

    class SearchByMenuKeywordStrategy {
        -String keyword
        +filter(List~Restaurant~ all) List~Restaurant~
    }

    %% inheritance / realisation
    User <|-- Customer
    User <|-- DeliveryAgent
    Observer <|.. User
    Observer <|.. Restaurant
    Subject <|.. Order
    DeliveryAssignmentStrategy <|.. NearestAvailableAgentStrategy
    RestaurantSearchStrategy <|.. SearchByCityStrategy
    RestaurantSearchStrategy <|.. SearchByProximityStrategy
    RestaurantSearchStrategy <|.. SearchByMenuKeywordStrategy

    %% service ownership
    FoodDeliveryService "1" o-- "*" Customer
    FoodDeliveryService "1" o-- "*" Restaurant
    FoodDeliveryService "1" o-- "*" DeliveryAgent
    FoodDeliveryService "1" o-- "*" Order
    FoodDeliveryService "1" --> "1" DeliveryAssignmentStrategy
    FoodDeliveryService ..> RestaurantSearchStrategy : per call

    %% domain
    Restaurant "1" *-- "1" Menu
    Menu "1" *-- "*" MenuItem
    Order "1" *-- "1..*" OrderItem
    OrderItem "*" --> "1" MenuItem
    Order "*" --> "1" Customer
    Order "*" --> "1" Restaurant
    Order "*" --> "0..1" DeliveryAgent
    Order --> OrderStatus
    Order "1" o-- "*" Observer : observers
    Customer "1" --> "*" Order : orderHistory
    Customer --> Address
    Restaurant --> Address
    DeliveryAgent --> Address
    SearchByProximityStrategy --> Address
```

> **Note.** `Customer ↔ Order` is a **bidirectional** association: the order points to
> its customer and the customer keeps the order in `orderHistory`. It is the only cycle
> in the model. It makes "my orders" O(1) but means both sides must be kept in sync.

---

## 2. Package / Layer View

```mermaid
flowchart TB
    subgraph root["solution (orchestration)"]
        FDS["FoodDeliveryService<br/>«Singleton · Facade»"]
        Demo["FoodDeliveryServiceDemo<br/>(client)"]
    end

    subgraph models["models (domain)"]
        U["User «abstract»"]
        C[Customer]
        DA[DeliveryAgent]
        R[Restaurant]
        M[Menu]
        MI[MenuItem]
        O[Order]
        OI[OrderItem]
        A[Address]
    end

    subgraph enums["enums"]
        OS[OrderStatus]
    end

    subgraph observer["observer (contracts)"]
        SUB["Subject «interface»"]
        OBS["Observer «interface»"]
    end

    subgraph strategies["strategies"]
        subgraph assign["assignment"]
            DAS["DeliveryAssignmentStrategy «interface»"]
            NAAS[NearestAvailableAgentStrategy]
        end
        subgraph search["search"]
            RSS["RestaurantSearchStrategy «interface»"]
            SC[SearchByCityStrategy]
            SP[SearchByProximityStrategy]
            SK[SearchByMenuKeywordStrategy]
        end
    end

    Demo --> FDS
    FDS --> models
    FDS --> enums
    FDS --> DAS
    FDS --> RSS
    models --> observer
    models --> enums
    observer -.->|"Observer.onUpdate(Order)"| O
    strategies --> models
```

> The `observer` package depends on `models.Order` (`onUpdate(Order)`) and `models`
> depends on `observer`. That is a **package cycle**. A generic
> `Observer<T>` or an `OrderEvent` DTO in the `observer` package would break it.

---

## 3. Design-Pattern Overlay

```mermaid
classDiagram
    direction LR

    namespace Singleton_Facade {
        class FoodDeliveryService {
            <<Singleton>>
            -volatile instance$
            +getInstance()$
        }
    }

    namespace Strategy_Assignment {
        class DeliveryAssignmentStrategy {
            <<interface>>
            +findAgent(order, agents)
        }
        class NearestAvailableAgentStrategy
    }

    namespace Strategy_Search {
        class RestaurantSearchStrategy {
            <<interface>>
            +filter(list)
        }
        class SearchByCityStrategy
        class SearchByProximityStrategy
        class SearchByMenuKeywordStrategy
    }

    namespace Observer_Pattern {
        class Subject {
            <<interface>>
        }
        class Observer {
            <<interface>>
        }
        class Order
        class Customer
        class Restaurant
        class DeliveryAgent
    }

    FoodDeliveryService --> DeliveryAssignmentStrategy : context
    FoodDeliveryService ..> RestaurantSearchStrategy : folds list
    DeliveryAssignmentStrategy <|.. NearestAvailableAgentStrategy
    RestaurantSearchStrategy <|.. SearchByCityStrategy
    RestaurantSearchStrategy <|.. SearchByProximityStrategy
    RestaurantSearchStrategy <|.. SearchByMenuKeywordStrategy
    Subject <|.. Order : ConcreteSubject
    Observer <|.. Customer : ConcreteObserver
    Observer <|.. Restaurant : ConcreteObserver
    Observer <|.. DeliveryAgent : ConcreteObserver
    Order o-- Observer
```

| Pattern | Role → Class |
|---|---|
| Singleton | Instance holder → `FoodDeliveryService` |
| Facade | Facade → `FoodDeliveryService`. Subsystems → registries, strategies, `Order` |
| Strategy (assignment) | Context → `FoodDeliveryService`. Strategy → `DeliveryAssignmentStrategy`. Concrete → `NearestAvailableAgentStrategy` |
| Strategy (search) | Context → `searchRestaurants`. Strategy → `RestaurantSearchStrategy`. Concrete → City / Proximity / MenuKeyword |
| Observer | Subject → `Subject`. ConcreteSubject → `Order`. Observer → `Observer`. ConcreteObservers → `Customer`, `DeliveryAgent`, `Restaurant` |

---

## 4. Observer Pattern Close-up

```mermaid
classDiagram
    direction TB
    class Subject {
        <<interface>>
        +addObserver(Observer o) void
        +notifyObservers() void
    }
    class Observer {
        <<interface>>
        +onUpdate(Order order) void
    }
    class Order {
        -CopyOnWriteArrayList~Observer~ observers
        +setStatus(OrderStatus s) void
        +assignDeliveryAgent(DeliveryAgent a) void
    }
    class User {
        <<abstract>>
    }
    class Customer {
        +onUpdate(Order order) void
    }
    class DeliveryAgent {
        +onUpdate(Order order) void
    }
    class Restaurant {
        +onUpdate(Order order) void
    }
    class SmsNotifier {
        <<future>>
        +onUpdate(Order order) void
    }
    class AnalyticsSink {
        <<future>>
        +onUpdate(Order order) void
    }

    Subject <|.. Order
    Observer <|.. User
    User <|-- Customer
    User <|-- DeliveryAgent
    Observer <|.. Restaurant
    Observer <|.. SmsNotifier
    Observer <|.. AnalyticsSink
    Order o-- "*" Observer
```

| Subscribed when | Observer |
|---|---|
| `Order` constructor | `Customer`, `Restaurant` |
| `Order.assignDeliveryAgent` | `DeliveryAgent` |
| never removed | ⚠️ there is no `removeObserver`, so an agent keeps receiving updates for a delivered order forever (harmless today because `DELIVERED` is terminal) |

This is the **push-the-subject** variant: observers receive the whole `Order` and pull
what they need (`getId()`, `getOrderStatus()`).

---

## 5. Strategy Pattern Close-up

```mermaid
classDiagram
    direction LR
    class DeliveryAssignmentStrategy {
        <<interface>>
        +findAgent(Order, List~DeliveryAgent~) Optional~DeliveryAgent~
    }
    class NearestAvailableAgentStrategy {
        +findAgent(...) Optional~DeliveryAgent~
        -calculateTotalDistance(agent, restaurant, customer) double
    }
    class LeastLoadedAgentStrategy {
        <<future>>
    }
    class HighestRatedAgentStrategy {
        <<future>>
    }
    DeliveryAssignmentStrategy <|.. NearestAvailableAgentStrategy
    DeliveryAssignmentStrategy <|.. LeastLoadedAgentStrategy
    DeliveryAssignmentStrategy <|.. HighestRatedAgentStrategy

    class RestaurantSearchStrategy {
        <<interface>>
        +filter(List~Restaurant~) List~Restaurant~
    }
    class SearchByCityStrategy {
        -String city
    }
    class SearchByProximityStrategy {
        -Address userLocation
        -double maxDistance
    }
    class SearchByMenuKeywordStrategy {
        -String keyword
    }
    class SearchByRatingStrategy {
        <<future>>
    }
    class SearchOpenNowStrategy {
        <<future>>
    }
    RestaurantSearchStrategy <|.. SearchByCityStrategy
    RestaurantSearchStrategy <|.. SearchByProximityStrategy
    RestaurantSearchStrategy <|.. SearchByMenuKeywordStrategy
    RestaurantSearchStrategy <|.. SearchByRatingStrategy
    RestaurantSearchStrategy <|.. SearchOpenNowStrategy
```

- **Assignment** strategy is *held* (a field, swappable with `setAssignmentStrategy`).
  ⚠️ The field is not `volatile`, so a swap on one thread is not guaranteed to be
  visible to another.
- **Search** strategies are *passed per call* as a list and folded left-to-right, which
  gives AND-composition for free. Putting the cheapest, most selective filter first
  (city) shrinks the input for the expensive ones (menu scan).

---

## 6. The Singleton (correctly implemented)

```mermaid
classDiagram
    class FoodDeliveryService {
        <<Singleton>>
        -static volatile FoodDeliveryService instance
        -static final Object lock
        -FoodDeliveryService()
        +static getInstance() FoodDeliveryService
    }
    note for FoodDeliveryService "if (instance == null)\n  synchronized (lock)\n    if (instance == null)\n      instance = new FoodDeliveryService()\nreturn instance"
```

Unlike the ticket system in this repo, this one is right: the field is `volatile` (so
the constructor's writes cannot be reordered after the reference is published), it is
actually **assigned** inside the lock, and the constructor is `private`. A simpler
alternative with the same guarantees is the *initialization-on-demand holder* idiom:

```java
private static class Holder { static final FoodDeliveryService I = new FoodDeliveryService(); }
public static FoodDeliveryService getInstance() { return Holder.I; }
```

---

## 7. Notation Cheatsheet

| Mermaid | UML meaning | Example here |
|---|---|---|
| `A <\|-- B` | B **extends** A | `User <\|-- Customer` |
| `A <\|.. B` | B **implements** A | `Observer <\|.. Restaurant` |
| `A *-- B` | **Composition**: B's lifetime is bound to A | `Restaurant *-- Menu` |
| `A o-- B` | **Aggregation**: A holds B, B lives independently | `FoodDeliveryService o-- Customer` |
| `A --> B` | **Association**: A has a field of type B | `Order --> Restaurant` |
| `A ..> B` | **Dependency**: A uses B transiently | `FoodDeliveryService ..> RestaurantSearchStrategy` |
| `+` / `-` / `#` | public / private / protected | |
| `$` suffix | static member | `getInstance()$` |
| `~T~` | generic type parameter | `List~Order~` |
