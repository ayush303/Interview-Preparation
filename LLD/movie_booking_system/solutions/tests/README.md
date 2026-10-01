# Movie Booking System — Tests

Concurrency tests for seat locking and booking. There is no build system and no JUnit:
the suite compiles with plain `javac`, prints numbered scenarios with `✔` checks, ends
with a summary, and exits non-zero if anything failed.

| File | What it is |
|---|---|
| [`ConcurrencyTests`](ConcurrencyTests.java) | 12 scenarios, each with **500 threads** shared across **200 users** |
| [`Fixtures`](Fixtures.java) | Test shows and users, deterministic payment strategies, the `race` runner, and the `assertConsistent` invariant check |
| [`TestHarness`](TestHarness.java) | Scenario runner and assertions |

## Running

From the repository root:

```bash
javac -d out $(find LLD/movie_booking_system/solutions -name '*.java')
java -cp out LLD.movie_booking_system.solutions.tests.ConcurrencyTests
```

The whole suite takes about 4 seconds.

## Scenarios

| # | Scenario | What must hold |
|---|---|---|
| 1 | 500 threads, 200 users, all want the **same seat** | exactly 1 booking; only 1 card charged; the losers never reach payment |
| 2 | Overlapping **seat pairs** (i, i+1) on a 100-seat show | pairs are all-or-nothing; no seat sold twice; no two neighbouring seats left free (no request wrongly rejected) |
| 3 | Random **1–4 seat groups** on a 60-seat show | no partial booking; BOOKED seats = seats in bookings; the rest AVAILABLE |
| 4 | 500 threads, **500 different seats** | no false contention: all 500 succeed, all 200 users get seats |
| 5 | **Half the cards declined** | declined users own nothing; their seats are released; nothing left LOCKED |
| 6 | **Payment slower than the 500 ms hold** (250 slow, 250 fast) | all 250 slow payers refunded and their seats back on sale; fast payers unaffected |
| 7 | **Expired holds resold** while the holder is still paying | 300 later buyers take the 200 expired seats; each seat sold once; every slow holder refunded |
| 8 | 499 other users try to **unlock / steal / confirm** someone's hold | nothing stolen; the real holder still confirms all 10 seats |
| 9 | Payment finishing **right at the expiry boundary** (450–550 ms) | each attempt is exactly *booked* or *refunded with the seat free*, never in between |
| 10 | 500 threads over **10 shows** sharing one `SeatLockManager` | every show consistent on its own |
| 11 | `MovieBookingService.getInstance()` from 500 threads | one instance |
| 12 | **End to end** through `MovieBookingService.bookTickets` | the public facade gives the same guarantees |

## How the races are made real

- `Fixtures.race(n, task)` starts `n` **dedicated** threads (500, not a small pool),
  waits until every one is parked on a start latch, then releases them together.
- Thread `i` acts for user `i % 200`, so most users have several requests in flight
  at once, the way a user double-clicking "Pay" or opening two tabs would.
- `race(n, beforeGo, task)` runs setup *after* the threads are parked. Scenario 8
  takes its seat hold there, so the 500 ms clock does not run out while 500 threads
  are still being created.

## What "consistent" means

`Fixtures.assertConsistent(show, bookings)` checks the whole show, not just return
values:

- no seat appears in two bookings (it names the seat and both users if one does)
- every seat in a booking is `BOOKED`, and every `BOOKED` seat is in a booking
- no seat is left `LOCKED`
- each booking's total matches the seat prices, and its payment is `SUCCESS`

## Deterministic payments

The real `CreditCardPaymentStrategy` fails 5% of the time at random, which would make
the counts unpredictable. `Fixtures.RecordingPayment` is a `PaymentStrategy` that counts
charges and refunds and can be **instant**, **declining** or **slow** (`slow(800)`
sleeps 800 ms before approving), which is how the expiry scenarios push payment past
the 500 ms hold.

## Proof the tests can fail

The suite was run against a copy of `SeatLockManager` with the
`synchronized (show.getLock())` removed from `lockSeats`. **8 of 12 scenarios failed**,
including:

```
❌ FAIL — DOUBLE BOOKING: seat a4927182-… sold to Reg-9 and Reg-10
❌ FAIL — cards charged (losers never reach payment): expected <1> but was <2>
❌ FAIL — bookings (every seat resold exactly once): expected <200> but was <226>
```

Against the real code, all 12 pass, and they passed 5 runs in a row.

## Not covered

The shared-`Seat` limitation (two shows on the **same screen** share seat status, see
[Known Limitations](../../problems/movie-booking-system.md#known-limitations)) is not
tested, because every scenario puts each show on its own screen. A test for it would
fail today by design. It belongs with the `ShowSeat` fix described in
[08 — Seat Lock Concurrency](../../class_diagrams/08-seat-lock-concurrency.md#option-a--recommended-per-show-lock--lazy-expiry-on-a-showseat).
