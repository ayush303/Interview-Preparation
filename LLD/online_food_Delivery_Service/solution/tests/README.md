# Food Delivery Service — Tests

Three suites, no build system and no JUnit. This repo compiles with plain `javac`,
so these do too. Each suite prints numbered scenarios with `✔` checks, ends with a
summary, and exits non-zero if anything failed.

| Suite | Scope | Scenarios |
|---|---|---|
| [`UnitTests`](UnitTests.java) | One class at a time, constructed directly: `OrderStatus` table (all 49 pairs), `Address` (Haversine), `MenuItem` stock, `OrderItem`/`Order` money and lifecycle, observers, `DeliveryAgent`, `Customer`, `Restaurant`, all four strategies | 22 |
| [`EndToEndFlowTests`](EndToEndFlowTests.java) | Complete journeys through the real `FoodDeliveryService`: search → menu → order → cook → auto-dispatch → deliver, cancellations, validation, stock rollback, sell-out, the no-agent queue, busy-agent handoff, nearest-agent choice, lookups, singleton wiring | 12 |
| [`ConcurrencyTests`](ConcurrencyTests.java) | Latch-released threads racing on the real service: agent claim, fleet dispatch, last portion, limited stock, cook-vs-cancel, duplicate confirm, slow observer, order history, subscribe-during-broadcast, singleton | 10 |

Supporting files: [`TestHarness`](TestHarness.java) (runner and assertions) and
[`Fixtures`](Fixtures.java) (test data, recording/exploding observers, and a
scoped assignment strategy).

## Running

From the repository root:

```bash
javac -d out $(find LLD/online_food_Delivery_Service/solution -name '*.java')
java -cp out LLD.online_food_Delivery_Service.solution.tests.UnitTests
java -cp out LLD.online_food_Delivery_Service.solution.tests.EndToEndFlowTests
java -cp out LLD.online_food_Delivery_Service.solution.tests.ConcurrencyTests
```

## Keeping scenarios independent

`FoodDeliveryService` is a singleton with no reset, so state leaks between
scenarios in the same JVM. Two conventions keep them isolated:

- every scenario registers its **own** customer, restaurant and menu items
  (`EndToEndFlowTests.Setup`), and
- every scenario that dispatches calls `Fixtures.onlyAgents(...)`, a
  `DeliveryAssignmentStrategy` that only sees that scenario's agents. Agents left
  over from earlier scenarios are invisible, and it doubles as a demonstration
  that the strategy seam works.

## How the concurrency tests make races happen

A concurrency test that passes proves nothing unless the threads actually
collide. `ConcurrencyTests.race(n, task)` parks every thread on a start latch and
releases them together. The cook-vs-cancel scenario also alternates which side is
submitted first, so neither always gets a head start. Across runs, the kitchen
and the customer each win about half of the 300 races. Each scenario then checks
system-wide invariants (no agent carrying two orders, stock equal to the
orders still holding it, exactly one notification per transition), not just how
many calls returned normally.

## Which gap each test pins

| Gap (see the [problem statement](../../problems/online-food-delivery-service.md#known-gaps-and-how-they-were-fixed)) | Tests |
|---|---|
| 1 NPE in nearest-agent strategy | Unit "NearestAvailableAgentStrategy…", Flow 1, 10 |
| 2 case-sensitive keyword search | Unit "SearchByMenuKeywordStrategy…", Flow 1 |
| 3 stranded READY_FOR_PICKUP orders | Flow 8, 9, Concurrency 1, 2 |
| 4 non-atomic validate-then-set | Concurrency 5, 6 |
| 5 cancel had its own rule and printed | Flow 2, 3 |
| 6 no stock control | Unit "MenuItem.reserve…", Flow 6, 7, Concurrency 3, 4 |
| 7 foreign items accepted | Unit "Restaurant.sells…", Flow 5 |
| 8 observers notified under the lock, not isolated | Unit "A throwing observer…", Concurrency 7 |
| 9 notify from constructor | Unit "Order does not notify from its constructor" |
| 10 unsafe order history | Unit "Customer order history…", Concurrency 8 |
| 11 agent released inside `Order.setStatus`, never moved | Unit "DeliveryAgent claim/release…", Flow 1 |
| 12 enum declared out of order | Unit "OrderStatus is declared in lifecycle order" |
| 13 `double` money, Euclidean distance | Unit "Order total uses BigDecimal…", "Address.distanceTo…" |
| 14 naming (`IsAvailable`, `reverse`) | compile-time: the old names no longer exist |
| 15 `removeObserver`, order lookups | Unit "removeObserver…", Flow 1, 11 |
