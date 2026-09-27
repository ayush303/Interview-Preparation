# Online Food Delivery Service — Design Diagrams

The complete diagram set for the food delivery LLD. Everything is written in
[Mermaid](https://mermaid.js.org/), so the sources stay diffable in git and render
inline on GitHub. All 51 diagrams were render-checked with `mermaid-cli`.

**Related:** [Problem statement, requirements, patterns and "why not State"](../problems/online-food-delivery-service.md) ·
[Java implementation](../solution/)

---

## Index

| # | File | Diagram types inside |
|---|---|---|
| 01 | [UML Class Diagram](01-uml-class-diagram.md) | Full class diagram · package/layer view · design-pattern overlay · Observer close-up · Strategy close-up · Singleton · notation cheatsheet |
| 02 | [ER Diagram](02-er-diagram.md) | Conceptual (Chen) ER · logical crow's-foot ER · cardinality table · many-to-many resolution |
| 03 | [Database Model](03-database-model.md) | Physical schema diagram · PostgreSQL DDL · indexes and queries · DB-level concurrency · in-memory storage map · Java → table mapping |
| 04 | [Sequence Diagrams](04-sequence-diagrams.md) | Bootstrap · composed search · place order · advance status · illegal transition · cancel · auto-assignment · **the NPE** · agent-claim race · cancel-vs-cook race · end-to-end |
| 05 | [State Diagrams](05-state-diagrams.md) | Order lifecycle · composite phases · transition matrix · rejected transitions · agent availability · menu item and stock · observer subscriptions · singleton init · request lifecycle |
| 06 | [Activity Diagrams & Flowcharts](06-activity-flowcharts.md) | Master flow · `placeOrder` · `updateOrderStatus` · `assignDelivery` (as-is and fixed) · search fold · `cancel` · DCL · swimlane activity · adding a status · adding a policy |
| 07 | [Use Case, Component, Object & Deployment](07-usecase-component-deployment.md) | Use case diagram · component diagram with plug-in points · object diagram snapshot · deployment (today and at scale) · extension mind-map |

---

## Which diagram answers which question?

| If you are asked… | Read |
|---|---|
| "Draw the class diagram" | [01 § 1](01-uml-class-diagram.md#1-full-class-diagram) |
| "What patterns did you use, and where?" | [01 § 3](01-uml-class-diagram.md#3-design-pattern-overlay) |
| "Why didn't you use the State pattern?" | [Problem statement](../problems/online-food-delivery-service.md#why-the-state-pattern-was-not-used), [05 § 3](05-state-diagrams.md#3-transition-matrix-the-valid_transitions-table) |
| "Draw the ER diagram" | [02 § 2](02-er-diagram.md#2-logical-er-diagram-crows-foot) |
| "How would you store this in a database?" | [03 § 1–2](03-database-model.md#1-physical-schema-diagram) |
| "How do you stop two orders grabbing the same agent?" | [04 Flow 9](04-sequence-diagrams.md#flow-9--race-two-orders-ready-at-once-one-agent-free), [03 § 4](03-database-model.md#4-concurrency-at-the-database-layer) |
| "Walk me through placing an order" | [04 Flow 3](04-sequence-diagrams.md#flow-3--place-an-order) |
| "What states can an order be in?" | [05 § 1](05-state-diagrams.md#1-order-lifecycle) |
| "Can I cancel after the food is being cooked?" | [05 § 2](05-state-diagrams.md#2-order-lifecycle-grouped-by-phase-composite-states) |
| "What if no delivery agent is free?" | [06 § 4](06-activity-flowcharts.md#4-assigndeliveryorder--nearestavailableagentstrategyfindagent) |
| "Is this thread-safe?" | [03 § 5](03-database-model.md#5-in-memory-storage-map-what-the-java-code-actually-holds), [04 Flows 9–10](04-sequence-diagrams.md#flow-10--race-restaurant-starts-cooking-while-customer-cancels) |
| "How does this scale?" | [07 § 4](07-usecase-component-deployment.md#4-deployment-view--from-one-jvm-to-production) |
| "How would you add payments / ratings?" | [07 § 5](07-usecase-component-deployment.md#5-extension-points) |

---

## The three-sentence summary

`FoodDeliveryService` is a thread-safe **singleton facade** over four
`ConcurrentHashMap` registries. It enforces the order lifecycle with a **transition
table** rather than the State pattern, because orders differ per state only in which
successor is legal, not in behaviour. Restaurant search and agent selection are
**strategies** (search filters are folded into a pipeline, and the assignment policy is
swappable at runtime), and every status change is pushed to the order's
**observers**: customer, restaurant and, once assigned, the delivery agent. An atomic
compare-and-set on the agent's availability guarantees no agent is ever double-booked.

---

## A note on ⚠️ marks

> **All 15 gaps are now fixed** in [`../solution/`](../solution/) and pinned by tests
> in [`../solution/tests/`](../solution/tests/). The diagrams were drawn from the
> original code, so read each ⚠️ as "this was a bug, and the fix is in the gaps table".
> The corrected designs are already drawn where it matters: the fixed dispatch
> flowchart ([06 § 4](06-activity-flowcharts.md#4-assigndeliveryorder--nearestavailableagentstrategyfindagent)),
> atomic `transitionTo` ([04 Flow 10](04-sequence-diagrams.md#flow-10--race-restaurant-starts-cooking-while-customer-cancels)),
> the intended stock lifecycle ([05 § 6](05-state-diagrams.md#6-menu-item-availability-and-the-unused-stock-model)),
> and the intended assignment flow ([04 Flow 7](04-sequence-diagrams.md#flow-7--ready-for-pickup--automatic-assignment-intended-design)).

Wherever the original implementation diverged from the intended design, it is marked
⚠️. The full list of 15 gaps, each with its fix, is in the
[problem statement](../problems/online-food-delivery-service.md#known-gaps-and-how-they-were-fixed).
Two of them were confirmed by running the original code:

1. **`NearestAvailableAgentStrategy` throws `NullPointerException`**, because it reads the
   customer's address from `order.getDeliveryAgent()`, which is still `null`.
   ([04 Flow 8](04-sequence-diagrams.md#flow-8--what-actually-happens-today-npe-during-assignment))
2. **Keyword search is case-sensitive on one side.** `"Pizza"` matches nothing, which
   is why the demo prints an empty result for search (C) and silently skips the whole
   order-placement section, which would otherwise have hit bug 1.

---

## Viewing these diagrams

- **GitHub** renders mermaid in Markdown automatically.
- **IntelliJ IDEA**: Markdown preview with the Mermaid extension enabled.
- **VS Code**: install *Markdown Preview Mermaid Support*.
- **Export to PNG**: paste a block into [mermaid.live](https://mermaid.live).
