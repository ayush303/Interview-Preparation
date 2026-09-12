# Concurrency Tests

Twelve tests proving how `MeetingScheduler` achieves thread safety — and pinning the two
places where it does not.

No build system, no JUnit. This repo compiles with plain `javac`, so the tests do too.

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

## Files

| File | Purpose |
|---|---|
| `ConcurrencyTestSuite.java` | The tests |
| `TestHarness.java` | ~120-line runner with PASS / FAIL / XFAIL / XPASS outcomes |

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
