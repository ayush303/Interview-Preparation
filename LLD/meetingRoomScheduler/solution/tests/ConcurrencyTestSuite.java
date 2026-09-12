package LLD.meetingRoomScheduler.solution.tests;

import LLD.meetingRoomScheduler.solution.MeetingScheduler;
import LLD.meetingRoomScheduler.solution.enums.RoomType;
import LLD.meetingRoomScheduler.solution.exceptions.MeetingSchedulerException;
import LLD.meetingRoomScheduler.solution.models.Meeting;
import LLD.meetingRoomScheduler.solution.models.Room;
import LLD.meetingRoomScheduler.solution.models.TimeSlot;
import LLD.meetingRoomScheduler.solution.models.User;
import LLD.meetingRoomScheduler.solution.observers.MeetingObserver;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.ConcurrentModificationException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static LLD.meetingRoomScheduler.solution.tests.TestHarness.*;

/**
 * Concurrency and thread-safety tests for {@link MeetingScheduler}.
 *
 * Run with:
 *   javac -d out $(find LLD/meetingRoomScheduler -name '*.java')
 *   java  -cp out LLD.meetingRoomScheduler.solution.tests.ConcurrencyTestSuite
 *
 * ─────────────────────────────────────────────────────────────────────────────
 * TWO THINGS THAT MAKE CONCURRENCY TESTS DIFFERENT FROM NORMAL TESTS
 * ─────────────────────────────────────────────────────────────────────────────
 *
 * 1. A passing run does not prove correctness. These tests can only *detect*
 *    races, never prove their absence — a broken implementation may pass by
 *    luck. Every test here is therefore built to make the race window as wide
 *    as possible: threads are parked on a CountDownLatch and released
 *    simultaneously, so they collide instead of trickling through one at a
 *    time. Without that latch, the classic double-booking test passes even on
 *    a completely unsynchronized implementation.
 *
 * 2. The scheduler is a singleton with no reset, so state leaks between tests.
 *    Two conventions keep them isolated:
 *      - every test registers its OWN rooms, with ids prefixed by the test name
 *      - tests needing "exactly one room qualifies" claim a capacity from
 *        nextExclusiveCapacity(), which grows monotonically. Because tests run
 *        sequentially, a room at the newest tier is the only one large enough
 *        at the moment its test runs. Filler rooms always stay <= 20 seats.
 *    This is itself a finding: an un-resettable singleton is hostile to
 *    testing. A package-private reset hook, or injecting the scheduler instead
 *    of reaching for a global, would remove the whole problem.
 */
public class ConcurrencyTestSuite {

    private static final User ALICE = new User("U1", "Alice", "alice@example.com");
    private static final int THREADS = 200;
    private static final int POOL_SIZE = 32;

    /** Filler rooms stay small; exclusive rooms climb above them. See the class comment. */
    private static final AtomicInteger exclusiveCapacity = new AtomicInteger(100);

    private static int nextExclusiveCapacity() {
        return exclusiveCapacity.addAndGet(100);
    }

    public static void main(String[] args) {
        TestHarness h = new TestHarness("MeetingScheduler — Concurrency & Thread Safety");
        MeetingScheduler scheduler = MeetingScheduler.getInstance();

        // ── Group A: guarantees the implementation actually provides ──────────
        h.test("no double-booking when 200 threads race for one room",
                () -> noDoubleBookingUnderContention(scheduler));

        h.test("every losing thread gets a domain exception, never a null or NPE",
                () -> losersFailCleanly(scheduler));

        h.test("INVARIANT: no two confirmed meetings in a room ever overlap",
                () -> noConfirmedOverlapsAfterChaosWorkload(scheduler));

        h.test("meeting ids stay unique under contention",
                () -> meetingIdsAreUnique(scheduler));

        h.test("concurrent cancel of the same meeting has exactly one winner",
                () -> concurrentCancelHasOneWinner(scheduler));

        h.test("back-to-back windows both succeed in the same room concurrently",
                () -> backToBackWindowsBothSucceed(scheduler));

        h.test("book/cancel churn never leaks a room claim",
                () -> churnDoesNotLeakTheRoom(scheduler));

        h.test("getInstance() hands every thread the same instance",
                ConcurrencyTestSuite::singletonIsUnique);

        h.test("a throwing observer does not fail the booking",
                () -> failingObserverDoesNotFailBooking(scheduler));

        h.test("observers can be added/removed during notification without CME",
                () -> observerChurnDuringNotification(scheduler));

        // ── Group B: documented defects — see concurrency-and-thread-safety.md ─
        h.knownDefect("§8.2 Meeting.complete() is atomic across threads",
                () -> completeIsAtomic(scheduler));

        h.knownDefect("§8.1 booking throughput is not capped by observer latency",
                () -> throughputIsNotCappedByObserverLatency(scheduler));

        System.exit(h.report());
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Group A
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * THE test. 200 threads, all asking for an overlapping window, exactly one
     * room big enough. The synchronized critical section must let precisely one
     * through.
     */
    private static void noDoubleBookingUnderContention(MeetingScheduler s) throws Exception {
        int capacity = nextExclusiveCapacity();
        s.addRoom(new Room("dbl-1", "OnlyRoom", RoomType.CONFERENCE, capacity));

        LocalDateTime base = LocalDateTime.of(2040, 1, 1, 9, 0);
        AtomicInteger confirmed = new AtomicInteger();

        runConcurrently(THREADS, () -> {
            // every thread asks for the SAME window -> all mutually exclusive
            s.scheduleMeeting("race", ALICE, List.of(),
                    new TimeSlot(base, base.plusHours(1)), capacity);
            confirmed.incrementAndGet();
        });

        assertEquals(1, confirmed.get(),
                "exactly one of " + THREADS + " racing threads may book the only room");
    }

    /** The 199 losers must fail as domain errors, not as NPEs or corrupt state. */
    private static void losersFailCleanly(MeetingScheduler s) throws Exception {
        int capacity = nextExclusiveCapacity();
        s.addRoom(new Room("clean-1", "OnlyRoom2", RoomType.CONFERENCE, capacity));

        LocalDateTime base = LocalDateTime.of(2041, 1, 1, 9, 0);
        List<Throwable> failures = Collections.synchronizedList(new ArrayList<>());
        AtomicInteger confirmed = new AtomicInteger();

        CountDownLatch gate = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(POOL_SIZE);
        CountDownLatch done = new CountDownLatch(THREADS);
        for (int i = 0; i < THREADS; i++) {
            pool.submit(() -> {
                try {
                    gate.await();
                    s.scheduleMeeting("race", ALICE, List.of(),
                            new TimeSlot(base, base.plusHours(1)), capacity);
                    confirmed.incrementAndGet();
                } catch (Throwable t) {
                    failures.add(t);
                } finally {
                    done.countDown();
                }
            });
        }
        gate.countDown();
        done.await(30, TimeUnit.SECONDS);
        pool.shutdownNow();

        assertEquals(1, confirmed.get(), "one winner expected");
        assertEquals(THREADS - 1, failures.size(), "everyone else must fail");
        for (Throwable t : failures) {
            assertTrue(t instanceof MeetingSchedulerException,
                    "expected MeetingSchedulerException but got " + t.getClass().getName());
        }
    }

    /**
     * The strongest test in the suite.
     *
     * Instead of counting winners, it runs a chaotic mixed workload and then
     * checks the domain invariant directly: for every room, no two confirmed
     * meetings overlap. This catches double-bookings the counting tests would
     * miss, because it does not depend on knowing in advance how many bookings
     * *should* have succeeded.
     */
    private static void noConfirmedOverlapsAfterChaosWorkload(MeetingScheduler s) throws Exception {
        final int rooms = 5;
        for (int i = 0; i < rooms; i++) {
            s.addRoom(new Room("chaos-" + i, "Chaos" + i, RoomType.CONFERENCE, 10));
        }

        LocalDateTime base = LocalDateTime.of(2042, 1, 1, 9, 0);
        List<Meeting> booked = Collections.synchronizedList(new ArrayList<>());

        // 300 attempts across 12 heavily-overlapping windows and 5 rooms:
        // far more demand than supply, so contention is guaranteed.
        runConcurrentlyIgnoringFailures(300, index -> {
            Random rnd = new Random(index);          // deterministic per task
            int startOffset = rnd.nextInt(12);
            int lengthHours = 1 + rnd.nextInt(3);    // 1-3h -> lots of overlap
            TimeSlot slot = new TimeSlot(
                    base.plusHours(startOffset),
                    base.plusHours(startOffset + lengthHours));
            booked.add(s.scheduleMeeting("chaos", ALICE, List.of(), slot, 10));
        });

        // group confirmed meetings by room, then check every pair
        Map<String, List<Meeting>> byRoom = new HashMap<>();
        for (Meeting m : booked) {
            byRoom.computeIfAbsent(m.getRoom().getId(), k -> new ArrayList<>()).add(m);
        }

        int overlaps = 0;
        for (Map.Entry<String, List<Meeting>> e : byRoom.entrySet()) {
            List<Meeting> ms = e.getValue();
            for (int i = 0; i < ms.size(); i++) {
                for (int j = i + 1; j < ms.size(); j++) {
                    if (ms.get(i).getTimeSlot().overlaps(ms.get(j).getTimeSlot())) {
                        overlaps++;
                    }
                }
            }
        }

        assertTrue(!booked.isEmpty(), "the workload must actually book something");
        assertEquals(0, overlaps,
                "found " + overlaps + " overlapping pairs among " + booked.size()
                        + " confirmed meetings — the room can host only one meeting at a time");
    }

    /** MTG-n ids come from an AtomicInteger; contention must not duplicate one. */
    private static void meetingIdsAreUnique(MeetingScheduler s) throws Exception {
        final int rooms = 40;
        for (int i = 0; i < rooms; i++) {
            s.addRoom(new Room("uniq-" + i, "Uniq" + i, RoomType.CONFERENCE, 8));
        }

        LocalDateTime base = LocalDateTime.of(2043, 1, 1, 9, 0);
        List<String> ids = Collections.synchronizedList(new ArrayList<>());

        // disjoint windows so nearly everything succeeds
        runConcurrentlyIgnoringFailures(rooms, index -> {
            TimeSlot slot = new TimeSlot(base.plusHours(index), base.plusHours(index + 1));
            ids.add(s.scheduleMeeting("uniq", ALICE, List.of(), slot, 8).getId());
        });

        Set<String> distinct = new HashSet<>(ids);
        assertTrue(ids.size() > 1, "expected multiple bookings, got " + ids.size());
        assertEquals(ids.size(), distinct.size(),
                "duplicate meeting id issued — " + ids.size() + " bookings produced only "
                        + distinct.size() + " distinct ids");
    }

    /** Cancelling twice must be rejected, even when the two attempts are simultaneous. */
    private static void concurrentCancelHasOneWinner(MeetingScheduler s) throws Exception {
        int capacity = nextExclusiveCapacity();
        s.addRoom(new Room("cancel-1", "CancelRoom", RoomType.CONFERENCE, capacity));

        LocalDateTime base = LocalDateTime.of(2044, 1, 1, 9, 0);
        Meeting m = s.scheduleMeeting("to-cancel", ALICE, List.of(),
                new TimeSlot(base, base.plusHours(1)), capacity);

        AtomicInteger cancelled = new AtomicInteger();
        runConcurrently(64, () -> {
            s.cancelMeeting(m.getId());
            cancelled.incrementAndGet();
        });

        assertEquals(1, cancelled.get(),
                "a meeting must cancel exactly once even under concurrent attempts");
    }

    /**
     * Back-to-back is explicitly allowed (start == end is not an overlap).
     * Run concurrently against a single room to prove the rule survives locking.
     */
    private static void backToBackWindowsBothSucceed(MeetingScheduler s) throws Exception {
        int capacity = nextExclusiveCapacity();
        s.addRoom(new Room("b2b-1", "BackToBack", RoomType.CONFERENCE, capacity));

        LocalDateTime base = LocalDateTime.of(2045, 1, 1, 10, 0);
        AtomicReference<Meeting> first = new AtomicReference<>();
        AtomicReference<Meeting> second = new AtomicReference<>();

        CountDownLatch gate = new CountDownLatch(1);
        Thread t1 = new Thread(() -> {
            await(gate);
            first.set(s.scheduleMeeting("10-11", ALICE, List.of(),
                    new TimeSlot(base, base.plusHours(1)), capacity));
        });
        Thread t2 = new Thread(() -> {
            await(gate);
            second.set(s.scheduleMeeting("11-12", ALICE, List.of(),
                    new TimeSlot(base.plusHours(1), base.plusHours(2)), capacity));
        });
        t1.start();
        t2.start();
        gate.countDown();
        t1.join(10_000);
        t2.join(10_000);

        assertTrue(first.get() != null, "10:00-11:00 booking should succeed");
        assertTrue(second.get() != null, "11:00-12:00 booking should succeed");
        assertSame(first.get().getRoom(), second.get().getRoom(),
                "both should land in the only qualifying room");
    }

    /**
     * Hammer one room with book-then-cancel cycles. If any claim leaks — a
     * cancel that removes the wrong entry, or a booking that is never released —
     * the room ends up permanently busy and the final booking fails.
     */
    private static void churnDoesNotLeakTheRoom(MeetingScheduler s) throws Exception {
        int capacity = nextExclusiveCapacity();
        s.addRoom(new Room("churn-1", "ChurnRoom", RoomType.CONFERENCE, capacity));

        LocalDateTime base = LocalDateTime.of(2046, 1, 1, 9, 0);
        TimeSlot slot = new TimeSlot(base, base.plusHours(1));

        runConcurrentlyIgnoringFailures(100, index -> {
            for (int i = 0; i < 10; i++) {
                Meeting m = s.scheduleMeeting("churn", ALICE, List.of(), slot, capacity);
                s.cancelMeeting(m.getId());     // always release what we claimed
            }
        });

        // after all the churn the room must be free again
        Meeting finalBooking = s.scheduleMeeting("final", ALICE, List.of(), slot, capacity);
        assertTrue(finalBooking != null,
                "after book/cancel churn the room must still be bookable — a claim leaked");
    }

    /** Double-checked locking must not produce two schedulers. */
    private static void singletonIsUnique() throws Exception {
        Set<MeetingScheduler> seen = Collections.synchronizedSet(new HashSet<>());
        runConcurrently(THREADS, () -> seen.add(MeetingScheduler.getInstance()));
        assertEquals(1, seen.size(),
                "getInstance() produced " + seen.size() + " distinct instances");
    }

    /** Notification is a side effect: a broken channel must not fail the booking. */
    private static void failingObserverDoesNotFailBooking(MeetingScheduler s) throws Exception {
        int capacity = nextExclusiveCapacity();
        s.addRoom(new Room("obs-1", "ObserverRoom", RoomType.CONFERENCE, capacity));

        AtomicInteger healthyCalls = new AtomicInteger();
        MeetingObserver broken = new MeetingObserver() {
            public void onMeetingScheduled(Meeting m) { throw new RuntimeException("SMTP down"); }
            public void onMeetingCancelled(Meeting m) { throw new RuntimeException("SMTP down"); }
        };
        MeetingObserver healthy = new MeetingObserver() {
            public void onMeetingScheduled(Meeting m) { healthyCalls.incrementAndGet(); }
            public void onMeetingCancelled(Meeting m) { }
        };

        s.addObserver(broken);
        s.addObserver(healthy);
        try {
            LocalDateTime base = LocalDateTime.of(2047, 1, 1, 9, 0);
            Meeting m = s.scheduleMeeting("survives", ALICE, List.of(),
                    new TimeSlot(base, base.plusHours(1)), capacity);
            assertTrue(m != null, "booking must succeed despite the throwing observer");
            assertEquals(1, healthyCalls.get(),
                    "the healthy observer must still be notified after the broken one threw");
        } finally {
            s.removeObserver(broken);
            s.removeObserver(healthy);
        }
    }

    /**
     * observers is a CopyOnWriteArrayList, so registering during an active
     * notification must not throw ConcurrentModificationException.
     */
    private static void observerChurnDuringNotification(MeetingScheduler s) throws Exception {
        final int rooms = 30;
        for (int i = 0; i < rooms; i++) {
            s.addRoom(new Room("churn-obs-" + i, "ObsChurn" + i, RoomType.CONFERENCE, 6));
        }

        LocalDateTime base = LocalDateTime.of(2048, 1, 1, 9, 0);
        List<Throwable> escaped = Collections.synchronizedList(new ArrayList<>());
        AtomicInteger stop = new AtomicInteger(0);

        Thread churner = new Thread(() -> {
            while (stop.get() == 0) {
                MeetingObserver o = new MeetingObserver() {
                    public void onMeetingScheduled(Meeting m) { }
                    public void onMeetingCancelled(Meeting m) { }
                };
                s.addObserver(o);
                s.removeObserver(o);
            }
        });
        churner.setDaemon(true);
        churner.start();

        try {
            runConcurrentlyIgnoringFailures(rooms, index -> {
                try {
                    TimeSlot slot = new TimeSlot(base.plusHours(index), base.plusHours(index + 1));
                    s.scheduleMeeting("obs-churn", ALICE, List.of(), slot, 6);
                } catch (ConcurrentModificationException cme) {
                    escaped.add(cme);
                    throw cme;
                }
            });
        } finally {
            stop.set(1);
            churner.join(2000);
        }

        assertEquals(0, escaped.size(),
                "observer registration during notification leaked a ConcurrentModificationException");
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Group B — known defects (expected to fail until §9 fixes land)
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * §8.2 — Meeting.complete() is a check-then-act with no lock and a
     * non-volatile field, and the scheduler exposes no completeMeeting(), so it
     * is reachable with no protection at all. Two threads can both pass the
     * "is it SCHEDULED?" guard.
     *
     * Fix: make cancel()/complete() synchronized and status volatile (§9 Fix 2).
     */
    private static void completeIsAtomic(MeetingScheduler s) throws Exception {
        final int trials = 3000;
        final int racers = 8;        // more racers than 2 widens the window a lot

        // Build the Meeting directly rather than through the scheduler. The defect
        // lives in Meeting's own state machine, and going through scheduleMeeting
        // would (a) be far slower and (b) pollute the singleton with 3000 rooms,
        // making every later availability scan longer.
        Room room = new Room("cmp-room", "CmpRoom", RoomType.CONFERENCE, 10);
        LocalDateTime base = LocalDateTime.of(2049, 1, 1, 9, 0);
        TimeSlot slot = new TimeSlot(base, base.plusHours(1));

        int doubleCompleted = 0;
        int worstCase = 1;

        for (int t = 0; t < trials; t++) {
            Meeting m = new Meeting("MTG-probe-" + t, "cmp", ALICE, List.of(), room, slot);

            AtomicInteger successes = new AtomicInteger();
            AtomicInteger spinning = new AtomicInteger();
            // A hot spin-wait, not a latch or barrier. Park/unpark wakes threads in a
            // cascade tens of microseconds apart — an eternity next to the 2-3
            // instruction window between complete()'s guard and its assignment.
            // Threads already spinning on separate cores see the flag flip at
            // essentially the same moment, which is what makes the race reachable.
            AtomicBoolean go = new AtomicBoolean(false);
            Thread[] threads = new Thread[racers];

            for (int i = 0; i < racers; i++) {
                threads[i] = new Thread(() -> {
                    spinning.incrementAndGet();
                    while (!go.get()) {
                        Thread.onSpinWait();
                    }
                    try {
                        m.complete();
                        successes.incrementAndGet();
                    } catch (Exception expected) {
                        // all but one thread should land here
                    }
                });
            }
            for (Thread th : threads) th.start();

            // wait until every racer is actually spinning before firing the start gun
            while (spinning.get() < racers) {
                Thread.onSpinWait();
            }
            go.set(true);

            for (Thread th : threads) th.join();

            if (successes.get() > 1) {
                doubleCompleted++;
                worstCase = Math.max(worstCase, successes.get());
            }
        }

        assertEquals(0, doubleCompleted,
                doubleCompleted + " of " + trials + " trials had MULTIPLE threads complete the "
                        + "same meeting (worst case " + worstCase + " simultaneous winners) — "
                        + "the state machine guard is an unsynchronized check-then-act on a "
                        + "non-volatile field");
    }

    /**
     * §8.1 — notification runs inside the synchronized block, so a slow
     * observer serializes every booking in the system, even bookings in
     * completely different rooms.
     *
     * Fix: narrow synchronized to a block and notify after releasing (§9 Fix 1).
     */
    private static void throughputIsNotCappedByObserverLatency(MeetingScheduler s) throws Exception {
        final int bookings = 20;
        final long observerLatencyMs = 50;
        final int capacity = 12;

        for (int i = 0; i < bookings; i++) {
            s.addRoom(new Room("slow-" + i, "Slow" + i, RoomType.CONFERENCE, capacity));
        }

        MeetingObserver slow = new MeetingObserver() {
            public void onMeetingScheduled(Meeting m) {
                try { Thread.sleep(observerLatencyMs); } catch (InterruptedException ignored) { }
            }
            public void onMeetingCancelled(Meeting m) { }
        };
        s.addObserver(slow);

        long elapsed;
        try {
            LocalDateTime base = LocalDateTime.of(2050, 1, 1, 9, 0);
            long t0 = System.currentTimeMillis();
            // DIFFERENT rooms, DIFFERENT windows -> zero logical contention.
            // These should run fully in parallel.
            runConcurrentlyIgnoringFailures(bookings, index -> {
                TimeSlot slot = new TimeSlot(base.plusHours(index), base.plusHours(index + 1));
                s.scheduleMeeting("slow", ALICE, List.of(), slot, capacity);
            });
            elapsed = System.currentTimeMillis() - t0;
        } finally {
            s.removeObserver(slow);
        }

        long serialized = bookings * observerLatencyMs;
        long budget = serialized / 2;      // generous: half of fully-serialized
        assertTrue(elapsed < budget,
                bookings + " independent bookings took " + elapsed + "ms; fully parallel is ~"
                        + observerLatencyMs + "ms and fully serialized is ~" + serialized
                        + "ms — the scheduler monitor is held across observer I/O");
    }

    // ─────────────────────────────────────────────────────────────────────────
    // helpers
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Park every thread on a latch, then release them at once. Without this the
     * threads trickle through and the race window never opens — the single most
     * common reason a concurrency test passes against broken code.
     */
    private static void runConcurrently(int threads, ThrowingTask task) throws Exception {
        CountDownLatch gate = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(threads);
        ExecutorService pool = Executors.newFixedThreadPool(POOL_SIZE);
        for (int i = 0; i < threads; i++) {
            pool.submit(() -> {
                try {
                    gate.await();
                    task.run();
                } catch (Throwable ignored) {
                    // losers are expected; the caller asserts on the counters
                } finally {
                    done.countDown();
                }
            });
        }
        gate.countDown();
        done.await(60, TimeUnit.SECONDS);
        pool.shutdownNow();
    }

    private static void runConcurrentlyIgnoringFailures(int tasks, ThrowingIndexedTask task)
            throws Exception {
        CountDownLatch gate = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(tasks);
        ExecutorService pool = Executors.newFixedThreadPool(POOL_SIZE);
        for (int i = 0; i < tasks; i++) {
            final int index = i;
            pool.submit(() -> {
                try {
                    gate.await();
                    task.run(index);
                } catch (Throwable ignored) {
                    // contention losses are part of the workload
                } finally {
                    done.countDown();
                }
            });
        }
        gate.countDown();
        done.await(60, TimeUnit.SECONDS);
        pool.shutdownNow();
    }

    private static void await(CountDownLatch gate) {
        try {
            gate.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    @FunctionalInterface
    private interface ThrowingTask {
        void run() throws Exception;
    }

    @FunctionalInterface
    private interface ThrowingIndexedTask {
        void run(int index) throws Exception;
    }
}
