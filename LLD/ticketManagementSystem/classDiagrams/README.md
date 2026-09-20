# Ticket Management System — Design Diagrams

Complete diagram set for the ServiceNow-style Incident Manager LLD, written in
[Mermaid](https://mermaid.js.org/) so the sources stay diffable in git and render
inline on GitHub.

**Related:** [Problem statement](../problem/ticket-management-system.md) ·
[Java implementation](../solution/)

---

## Index

| # | File | Diagrams inside |
|---|---|---|
| 01 | [Class Diagram](01-class-diagram.md) | Full class diagram · layered package view · design-pattern overlay · why State beats a `status` field · the broken singleton · notation cheatsheet |
| 02 | [Entity Relationship Diagram](02-entity-relationship-diagram.md) | Core ER model · in-memory storage map · a relational schema for it · cardinality table |
| 03 | [Sequence Diagrams](03-sequence-diagrams.md) | Raise · auto-assign · work & resolve · illegal transition · reassignment · **the transition race** · concurrent creation · observer failure |
| 04 | [State Diagrams](04-state-diagrams.md) | Ticket lifecycle · rejected transitions · state objects over time · singleton init (intended vs actual) · assignee slot · round-robin cursor · request lifecycle |
| 05 | [Flowcharts](05-flowcharts.md) | Master flow · `createIncident` · `autoAssign` · `findAgent` · state delegation · `setState` · double-notification · DCL · adding a state · adding a policy |
| 06 | [Use Case, Component & Activity](06-usecase-component-activity.md) | Use cases · component seams and plug-in points · transition activity · concurrency swimlanes · deployment view |

---

## Which diagram answers which question?

| If you are asked… | Read |
|---|---|
| "Draw the class diagram" | [01](01-class-diagram.md) § 1 |
| "What patterns did you use, and where?" | [01](01-class-diagram.md) § 3 |
| "Why not just a `status` enum and some `if`s?" | [01](01-class-diagram.md) § 4 — **the core question** |
| "Is your singleton correct?" | [01](01-class-diagram.md) § 5, [04](04-state-diagrams.md) § 4 — it is not, and here is why |
| "How would you store this in a database?" | [02](02-entity-relationship-diagram.md) § 1 and § 3 |
| "What holds state at runtime?" | [02](02-entity-relationship-diagram.md) § 2 |
| "Walk me through raising and assigning a ticket" | [03](03-sequence-diagrams.md) Flows 1–2 |
| "What happens on an illegal transition?" | [03](03-sequence-diagrams.md) Flow 4, [04](04-state-diagrams.md) § 2 |
| "Two agents click Resolve at the same time — what happens?" | [03](03-sequence-diagrams.md) Flow 6 — **the money question** |
| "What if the Slack notifier is down?" | [03](03-sequence-diagrams.md) Flow 8 |
| "What states can a ticket be in?" | [04](04-state-diagrams.md) § 1 |
| "How does round-robin actually behave?" | [04](04-state-diagrams.md) § 6, [05](05-flowcharts.md) § 4 |
| "How do you add an ON_HOLD state?" | [05](05-flowcharts.md) § 9 |
| "How do you add priority-based routing?" | [05](05-flowcharts.md) § 10 — and why the interface blocks it |
| "Where does your design NOT extend cleanly?" | [06](06-usecase-component-activity.md) § 2, last two rows |
| "Is this thread-safe?" | [06](06-usecase-component-activity.md) § 4 — the scorecard |
| "Does this scale to multiple servers?" | [06](06-usecase-component-activity.md) § 5 |

---

## The three-sentence summary

A `Ticket` holds its lifecycle as a **live state object** rather than a status enum, so
`assignTo` / `markInProgress` / `resolveTicket` are one-line delegations and each of the
twelve legality rules (3 operations × 4 states) lives in the state it constrains — adding
`ON_HOLD` is one new class, not an edit to three growing `switch` statements. Which agent
receives a ticket is a **strategy** (`RoundRobinRouting`) injected into
`IncidentManager`, so routing policy changes without the manager changing. Every
transition pushes a message to the ticket's **observers**, a list the reporter is added
to automatically at creation, so notification channels are added by implementing an
interface rather than by editing the transition logic.

---

## Reading order

**If you have 10 minutes** — [01 § 1](01-class-diagram.md#1-full-class-diagram) for the
shape, [04 § 1](04-state-diagrams.md#1-ticket-lifecycle) for the lifecycle,
[03 Flow 2](03-sequence-diagrams.md#flow-2--auto-assign-via-the-routing-strategy) for how
the three patterns interlock in a single call.

**If you are preparing to defend this design** — add
[01 § 4](01-class-diagram.md#4-why-state-and-not-a-status-field) (why State),
[03 Flow 6](03-sequence-diagrams.md#flow-6--the-concurrency-race-on-a-single-ticket)
(the race), and
[06 § 2](06-usecase-component-activity.md#2-component-diagram) (where it stops being
open/closed).

---

## Viewing these diagrams

- **GitHub** — renders mermaid in Markdown automatically; just open the file.
- **IntelliJ IDEA** — the built-in Markdown preview renders mermaid (enable the
  Mermaid extension under *Settings → Languages & Frameworks → Markdown*).
- **VS Code** — install *Markdown Preview Mermaid Support*.
- **Export to PNG** — paste a block into [mermaid.live](https://mermaid.live) and use
  *Actions → PNG*.

---

## A note on ⚠️ marks

These diagrams document the code **as it is today**, not an idealised version of it.
Wherever the implementation diverges from what the design intends, the divergence is
marked ⚠️ and the corrected code is shown next to it. The full list lives in the
[problem statement](../problem/ticket-management-system.md#️-known-gaps-in-the-current-implementation);
the four that come up most are:

1. **`getInstance` is not a singleton** — `instance` is `final null`, so every call
   returns a new manager with its own empty ticket store.
   ([01 § 5](01-class-diagram.md#5-the-singleton-that-isnt))
2. **Round-robin starts at index 1** — the first ticket routes to the *second* agent,
   which is why the demo prints Bob for `INC-0001`.
   ([05 § 4](05-flowcharts.md#4-roundrobinroutingfindagent--the-policy))
3. **Every transition notifies twice** — once from `setState`, once explicitly from the
   state class. ([05 § 7](05-flowcharts.md#7-the-double-notification-path))
4. **`Ticket` is not thread-safe** — `currentState` is read-then-written without a lock
   and `observers` is a plain `ArrayList`.
   ([03 Flow 6](03-sequence-diagrams.md#flow-6--the-concurrency-race-on-a-single-ticket))

Knowing where your own design leaks is worth more in an interview than a diagram that
pretends it does not.
