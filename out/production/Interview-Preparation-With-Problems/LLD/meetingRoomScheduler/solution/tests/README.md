# Concurrency Tests

Two programs, no build system and no JUnit — this repo compiles with plain `javac`, so
these do too.

| Program | What it does |
|---|---|
| [`ConcurrencyTestSuite`](ConcurrencyTestSuite.java) | **Asserts** thread safety. 12 tests, pass/fail output. |
| [`ConcurrentBookingSimulation`](ConcurrentBookingSimulation.java) | **Shows** thread safety. 500 users, 200 rooms, a 30-thread pool, every booking logged live. |

Use the suite to know it is correct; use the simulation to *watch* it be correct.

---

# 1. ConcurrencyTestSuite

Twelve tests proving how `MeetingScheduler` achieves thread safety — and pinning the two
places where it does not.

## Running

From the repository root:

```bash
javac -d out $(find LLD/meetingRoomScheduler -name '*.java')
java  -cp out LLD.meetingRoomScheduler.solution.tests.ConcurrencyTestSuite
```

Expected output today:

```
[  PASS ] no double-booking when 200 threads race for one room
[  PASS ] every losing thread gets a domain exception, never a null or NPE
[  PASS ] INVARIANT: no two confirmed meetings in a room ever overlap
[  PASS ] meeting ids stay unique under contention
[  PASS ] concurrent cancel of the same meeting has exactly one winner
[  PASS ] back-to-back windows both succeed in the same room concurrently
[  PASS ] book/cancel churn never leaks a room claim
[  PASS ] getInstance() hands every thread the same instance
[  PASS ] a throwing observer does not fail the booking
[  PASS ] observers can be added/removed during notification without CME
[ XFAIL ] §8.2 Meeting.complete() is atomic across threads
[ XFAIL ] §8.1 booking throughput is not capped by observer latency

  10 passed, 0 failed, 2 known defects (expected failures), 0 unexpected passes
```

Exit code is `0` unless something in the first ten breaks. The two `XFAIL`s are
**documented defects, not test bugs** — see
[`../concurrency-and-thread-safety.md`](../concurrency-and-thread-safety.md) §8 and §9.

## Three things worth knowing

**1. A passing concurrency test proves nothing on its own.** These tests *detect* races;
they cannot prove absence. Broken code can pass by luck. That is why every test here
forces maximum collision rather than hoping for one.

**2. How threads are released decides whether a race is found at all.** The
`complete()` race was originally caught **7 times in 2000** trials using two threads and
a `CountDownLatch`. Switching to six threads released by a **hot spin-wait** found the
same bug **2491 times in 3000**. `park`/`unpark` wakes threads in a cascade tens of
microseconds apart — far too slow to hit a two-instruction window. Use latches for coarse
races, spin-release for narrow ones.

**3. `XFAIL` keeps known bugs visible without breaking the build.** Deleting a test for a
known bug hides it; failing the build on it means the test never gets committed.
`knownDefect(...)` does neither — and if the test starts passing, the harness reports
`XPASS` and tells you to promote it to `test(...)`. Applying §9 Fixes 1 and 2 flips both
`XFAIL`s to `XPASS` with all ten other tests still green.

## A note on test isolation

`MeetingScheduler` is a singleton with **no reset**, so state leaks between tests. Two
conventions work around it:

- every test registers its own rooms, ids prefixed with the test name
- tests needing "exactly one room qualifies" take a capacity from
  `nextExclusiveCapacity()`, which climbs monotonically; since tests run sequentially,
  the newest tier is always exclusive. Filler rooms stay at or below 20 seats.

That workaround is itself a finding: **an un-resettable singleton is hostile to testing.**
A package-private reset hook, or injecting the scheduler rather than reaching for a
global, would delete this whole problem.

---

# 2. ConcurrentBookingSimulation

A narrated run you can actually watch: **500 users, 200 meeting rooms, a 30-thread
worker pool**, with every booking attempt logged as it happens.

```bash
javac -d out $(find LLD/meetingRoomScheduler -name '*.java')
java  -cp out LLD.meetingRoomScheduler.solution.tests.ConcurrentBookingSimulation
```

Each log line shows the sequence number, **which thread** ran it, the elapsed time, the
user, what they asked for, and what they got:

```
  #0001 booker-01      55.6ms  BOOK   Alice.T1    10:00-11:00 12 seats ✅ CONFIRMED MTG-1 in Rockies-F7 (CONFERENCE, cap 12)
  #0002 booker-30      56.7ms  BOOK   Deepa.T1    10:00-11:00  7 seats ✅ CONFIRMED MTG-2 in Cedar-F1   (CONFERENCE, cap 7)
  #0003 booker-29      57.2ms  BOOK   Chirag.T1   10:00-11:00  2 seats ✅ CONFIRMED MTG-3 in Zagros-F1  (HUDDLE_SPACE, cap 2)
  #0190 booker-13      99.4ms  BOOK   Bhavna.T4   10:00-11:00 11 seats ❌ REJECTED   no room free that seats 11
```

The strategy is `BestFit`, so you can watch a 2-person meeting take a 2-seat huddle
room while the 30-seat board rooms stay free for people who need them.

At full verbosity this prints ~2400 lines. Pass `--summary` (or `-s`) to skip the
per-attempt lines and print only the phase totals, occupancy grid and final report
(~300 lines).

## The four phases

| Phase | What it demonstrates |
|---|---|
| **1 — Thundering herd** | All 500 users request the *same* 10:00 slot. Every request is queued behind a start gun, so the 30-thread pool is saturated the instant it opens. With 200 rooms, **exactly 200 win and 300 are turned away** — the cleanest possible demonstration of the invariant. |
| **2 — A normal day** | 1500 requests spread over 09:00–18:00. High parallel throughput, little contention. |
| **3 — Cancellations** | 40 meetings are cancelled while other threads race to claim the freed windows — you see `🗑 RELEASED` immediately followed by `♻️ CLAIMED`. |
| **4 — Verification** | Prints the occupancy grid, then checks **every** confirmed pair per room for overlap. |

## The output that matters

```
  ROOM              TYPE           CAP    9 10 11 12 13 14 15 16 17   BOOKED  ORGANIZERS
  Everest-F1        HUDDLE_SPACE     2    ■  ■  ■  ■  ■  ■  ■  ■  ■      9    Deepa.T1, Juhi.T1, Priya.T2+6 more
  Alps-F1           CONFERENCE       6    ■  ■  ■  ■  ■  ■  ■  ■  ■      9    Aditya.T1, Aditya.T1, Deepa.T1+6 more
  Nook-F1           BOARD_ROOM      10    ■  ■  ■  ■  ■  ■  ■  ■  ■      9    Leela.T2, Bhavna.T1, Sameer.T2+6 more
```

```
  users                  : 500
  rooms                  : 200
  worker pool threads    : 30
  threads that won a room: 30
  confirmed meetings     : 1523
  rejected (no room)     : 477
  overlapping pairs      : 0   <-- the number that matters

  ✅ PASS — no room is ever double-booked.
```

Exit code is `0` only when `overlapping pairs` is `0`, so the simulation doubles as a
stress test. Counts vary run to run — the interleaving is genuinely nondeterministic —
but the overlap count must always be zero.

## Three implementation notes

**Wait on the submitter, never on your peers.** All 500 phase-1 requests are queued into
the 30-thread pool behind a `CountDownLatch` that **main** releases. That is safe at any
pool size: 30 tasks park on the latch, 470 wait in the queue, and main frees them all.

An earlier version instead had each task wait until *all 500 tasks had checked in* — and
deadlocked instantly. The first 30 occupied every thread and waited forever for 470 peers
that could never be scheduled, because no thread was free to run them. That is
**thread-starvation deadlock**, and the distinction is the whole lesson: tasks waiting on
a *coordinator* are fine; tasks waiting on *each other* need at least as many threads as
there are tasks.

**Latch here, spin-wait there.** This simulation gates on a park-based `CountDownLatch`,
while `ConcurrencyTestSuite` uses a hot spin-wait. Both are start guns, chosen for
different jobs: spin-release wins when the race window is two or three instructions wide
(see note 2 in the suite section), but 500 spinning threads on a handful of cores would
just saturate the CPU, and here the critical section scans 200 rooms anyway.

**`System.out` is shared mutable state too.** Every log line goes through one
`synchronized` block. Without it the lines tear into each other and the output is
unreadable — a small demonstration of the very problem the scheduler is solving.
