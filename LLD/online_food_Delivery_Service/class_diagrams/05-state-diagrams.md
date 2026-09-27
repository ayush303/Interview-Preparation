# Online Food Delivery Service — State Diagrams

> Which objects have a lifecycle, what states they move through, and what drives each
> move. The order lifecycle is enforced by the `VALID_TRANSITIONS` table, **not** by
> the State pattern. The reasons are in the
> [problem statement](../problems/online-food-delivery-service.md#why-the-state-pattern-was-not-used).

---

## 1. Order Lifecycle

```mermaid
stateDiagram-v2
    direction LR
    [*] --> PENDING : placeOrder()

    PENDING --> CONFIRMED : restaurant accepts
    PENDING --> CANCELLED : customer cancels

    CONFIRMED --> PREPARING : kitchen starts
    CONFIRMED --> CANCELLED : customer cancels

    PREPARING --> READY_FOR_PICKUP : food packed
    READY_FOR_PICKUP --> OUT_FOR_DELIVERY : system assigns agent (automatic)
    OUT_FOR_DELIVERY --> DELIVERED : agent hands over

    DELIVERED --> [*]
    CANCELLED --> [*]

    note right of READY_FOR_PICKUP
        entry / assignDelivery(order)
        ⚠️ NPE today, and no retry if no agent is free
    end note
    note right of DELIVERED
        entry / deliveryAgent.setIsAvailable(true)
    end note
```

| State | Driven by | Entry side effect | Terminal |
|---|---|---|---|
| `PENDING` | customer (`placeOrder`) | notify customer and restaurant | no |
| `CONFIRMED` | restaurant | notify | no |
| `PREPARING` | restaurant | notify, and cancellation is no longer allowed | no |
| `READY_FOR_PICKUP` | restaurant | **assign delivery agent** | no |
| `OUT_FOR_DELIVERY` | system (inside `assignDelivery`) | agent subscribed and notified | no |
| `DELIVERED` | agent | **release agent** | ✅ |
| `CANCELLED` | customer (`cancel`) or `updateOrderStatus` | notify | ✅ |

---

## 2. Order Lifecycle Grouped by Phase (composite states)

```mermaid
stateDiagram-v2
    [*] --> Kitchen

    state Kitchen {
        direction LR
        [*] --> PENDING
        PENDING --> CONFIRMED
        CONFIRMED --> PREPARING
        PREPARING --> READY_FOR_PICKUP
    }

    state Logistics {
        direction LR
        [*] --> OUT_FOR_DELIVERY
        OUT_FOR_DELIVERY --> DELIVERED
    }

    state Cancellable <<choice>>

    Kitchen --> Logistics : agent assigned
    Kitchen --> Cancellable : cancel()
    Cancellable --> CANCELLED : [status is PENDING or CONFIRMED]
    Cancellable --> Kitchen : [otherwise] refused, status unchanged
    Logistics --> [*]
    CANCELLED --> [*]
```

The **point of no return** is `PREPARING`. That boundary is exactly where the
restaurant begins spending money on the order.

---

## 3. Transition Matrix (the `VALID_TRANSITIONS` table)

Rows are the current status and columns are the requested status. ✅ means allowed,
and every blank cell throws `IllegalStateException("Invalid transition: X -> Y")`.

| from \ to | PENDING | CONFIRMED | PREPARING | READY | OUT_FOR_DEL | DELIVERED | CANCELLED |
|---|---|---|---|---|---|---|---|
| **PENDING** | | ✅ | | | | | ✅ |
| **CONFIRMED** | | | ✅ | | | | ✅ |
| **PREPARING** | | | | ✅ | | | |
| **READY_FOR_PICKUP** | | | | | ✅ | | |
| **OUT_FOR_DELIVERY** | | | | | | ✅ | |
| **DELIVERED** | | | | | | | |
| **CANCELLED** | | | | | | | |

7 legal transitions out of 49 pairs. Self-transitions (`X → X`) are rejected by the
table in `updateOrderStatus`, but `Order.setStatus(X)` on its own treats them as a
silent no-op. That is why `placeOrder`'s `setStatus(PENDING)` does nothing.

---

## 4. Rejected Transitions (what the table protects against)

```mermaid
stateDiagram-v2
    direction LR
    PENDING --> PREPARING : ❌ skip confirmation
    PREPARING --> CANCELLED : ❌ food already cooking
    CONFIRMED --> DELIVERED : ❌ skip the whole kitchen
    READY_FOR_PICKUP --> DELIVERED : ❌ nobody picked it up
    DELIVERED --> CANCELLED : ❌ terminal
    CANCELLED --> CONFIRMED : ❌ terminal, no resurrection
    OUT_FOR_DELIVERY --> PREPARING : ❌ no going back
```

---

## 5. Delivery Agent Availability

```mermaid
stateDiagram-v2
    direction LR
    [*] --> AVAILABLE : registerDeliveryAgent()
    AVAILABLE --> BUSY : claim() CAS true→false succeeds
    AVAILABLE --> AVAILABLE : claim() by a losing thread returns false
    BUSY --> BUSY : assignDeliveryAgent() sets false again (redundant)
    BUSY --> AVAILABLE : order DELIVERED, setIsAvailable(true)

    note right of BUSY
        Only one order at a time.
        ⚠️ Location is not moved to the drop-off point,
        so the next assignment measures from the
        agent's original position.
    end note
```

`AtomicBoolean.compareAndSet(true, false)` makes `AVAILABLE → BUSY` a single atomic
step, so two threads can never both win the same agent (see
[04 Flow 9](04-sequence-diagrams.md#flow-9--race-two-orders-ready-at-once-one-agent-free)).

---

## 6. Menu Item Availability (and the unused stock model)

```mermaid
stateDiagram-v2
    direction LR
    [*] --> Available : new MenuItem() (available=true, stock=0)
    Available --> Unavailable : setAvailable(false)
    Unavailable --> Available : setAvailable(true)
    Available --> Unavailable : reverse(q) brings stock to 0
    note left of Available
        ⚠️ reverse() is never called and stock starts at 0,
        so reverse(q) always returns false.
        placeOrder only checks the flag. It never
        decrements stock or re-enables on cancel.
    end note
```

**Intended stock lifecycle** (once `reserve` is wired in):

```mermaid
stateDiagram-v2
    direction LR
    [*] --> InStock : restock(n > 0)
    InStock --> InStock : reserve(q) [stock - q > 0]
    InStock --> SoldOut : reserve(q) [stock - q == 0]
    InStock --> InStock : release(q) on cancel
    SoldOut --> InStock : release(q) or restock(n)
    SoldOut --> SoldOut : reserve(q) returns false, order rejected
```

---

## 7. Observer Subscription per Order

```mermaid
stateDiagram-v2
    direction LR
    [*] --> CustomerAndRestaurant : Order constructor
    CustomerAndRestaurant --> PlusAgent : assignDeliveryAgent()
    PlusAgent --> PlusAgent : every setStatus notifies all 3
    CustomerAndRestaurant --> CustomerAndRestaurant : every setStatus notifies 2
    PlusAgent --> [*] : order terminal (no removeObserver)
    CustomerAndRestaurant --> [*] : cancelled
```

---

## 8. Singleton Initialisation

```mermaid
stateDiagram-v2
    direction LR
    [*] --> Uninitialised : class loaded (instance = null)
    Uninitialised --> Locking : getInstance(), first check sees null
    Locking --> Initialised : second check still null, construct and publish (volatile write)
    Locking --> Initialised : second check non-null (another thread won)
    Initialised --> Initialised : getInstance(), fast path with no lock
```

---

## 9. Request Lifecycle of `updateOrderStatus`

```mermaid
stateDiagram-v2
    [*] --> Lookup
    Lookup --> NotFound : orders.get == null
    NotFound --> [*] : throw NoSuchElementException
    Lookup --> Validate : found
    Validate --> Illegal : next ∉ VALID_TRANSITIONS[current]
    Illegal --> [*] : throw IllegalStateException
    Validate --> Applied : setStatus(next), observers notified
    Applied --> Assigning : next == READY_FOR_PICKUP
    Applied --> [*] : otherwise
    Assigning --> Dispatched : agent found and claimed, then OUT_FOR_DELIVERY
    Assigning --> Stranded : no agent or claim lost
    Assigning --> Crashed : ⚠️ NPE in strategy (gap 1)
    Dispatched --> [*]
    Stranded --> [*] : ⚠️ silent, order stays READY_FOR_PICKUP
    Crashed --> [*] : exception to caller, status already changed
```
