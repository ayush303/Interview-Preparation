package LLD.meetingRoomScheduler.solution.tests;

import LLD.meetingRoomScheduler.solution.MeetingScheduler;
import LLD.meetingRoomScheduler.solution.enums.RoomType;
import LLD.meetingRoomScheduler.solution.exceptions.MeetingSchedulerException;
import LLD.meetingRoomScheduler.solution.models.Meeting;
import LLD.meetingRoomScheduler.solution.models.Room;
import LLD.meetingRoomScheduler.solution.models.TimeSlot;
import LLD.meetingRoomScheduler.solution.models.User;
import LLD.meetingRoomScheduler.solution.observers.CalendarNotificationObserver;
import LLD.meetingRoomScheduler.solution.observers.EmailNotificationObserver;
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
 * Concurrency and thread-safety scenarios for {@link MeetingScheduler}.
 *
 * Run with:
 *   javac -d out $(find LLD/meetingRoomScheduler -name '*.java')
 *   java  -cp out LLD.meetingRoomScheduler.solution.tests.ConcurrencyTestSuite
 *
 * Output is narrated in the same style as MeetingSchedulerDemo — numbered
 * scenarios, real [Email] and [Calendar] observer lines, and the actual
 * Meeting.toString() of what got booked — so the run reads as a story and each
 * scenario states plainly what it is proving.
 *
 * ─────────────────────────────────────────────────────────────────────────────
 * TWO THINGS THAT MAKE CONCURRENCY TESTS DIFFERENT FROM NORMAL TESTS
 * ─────────────────────────────────────────────────────────────────────────────
 *
 * 1. A passing run does not prove correctness. These scenarios can only *detect*
 *    races, never prove their absence — a broken implementation may pass by
 *    luck. Every scenario is therefore built to make the race window as wide as
 *    possible: threads are parked on a latch (or spun on a flag, where the
 *    window is only a couple of instructions) and released together, so they
 *    collide instead of trickling through one at a time. Without that, the
 *    classic double-booking scenario passes even on a completely
 *    unsynchronized implementation.
 *
 * 2. The scheduler is a singleton with no reset, so state leaks between
 *    scenarios. Two conventions keep them isolated:
 *      - every scenario registers its OWN rooms, ids prefixed by the scenario
 *      - scenarios needing "exactly one room qualifies" claim a capacity from
 *        nextExclusiveCapacity(), which grows monotonically. Because scenarios
 *        run sequentially, a room at the newest tier is the only one large
 *        enough at the moment its scenario runs. Filler rooms stay <= 20 seats.
 *    This is itself a finding: an un-resettable singleton is hostile to
 *    testing. A package-private reset hook, or injecting the scheduler instead
 *    of reaching for a global, would remove the whole problem.
 */
public class ConcurrencyTestSuite {

    private static final User ALICE = new User("U1", "Alice", "alice@example.com");
    private static final User BOB = new User("U2", "Bob", "bob@example.com");
    private static final int THREADS = 200;
    private static final int POOL_SIZE = 32;

    /** Filler rooms stay small; exclusive rooms climb above them. See the class comment. */
    private static final AtomicInteger exclusiveCapacity = new AtomicInteger(100);

    /** The real notification channels, registered so their output appears in the log. */
    private static final MeetingObserver EMAIL = new EmailNotificationObserver();
    private static final MeetingObserver CALENDAR = new CalendarNotificationObserver();

    private static int nextExclusiveCapacity() {
        return exclusiveCapacity.addAndGet(100);
    }

    public static void main(String[] args) {
        TestHarness h = new TestHarness("MeetingScheduler — Concurrency & Thread Safety");
        MeetingScheduler scheduler = MeetingScheduler.getInstance();

        // Registered up front so real notification output appears in the scenarios.
        // High-volume scenarios detach them via quietly(...) to keep the log readable.
        scheduler.addObserver(EMAIL);
        scheduler.addObserver(CALENDAR);

        // ── Guarantees the implementation actually provides ───────────────────
        h.scenario("No Double-Booking Under Contention",
                "the find → select → claim sequence is atomic, so only one thread can win",
                THREADS + " threads, ONE qualifying room, all requesting the SAME window",
                () -> noDoubleBookingUnderContention(scheduler));

        h.scenario("Losing Threads Fail Cleanly",
                "a rejected booking is a domain error, never a null or an NPE",
                THREADS + " threads race for one room; every failure is inspected",
                () -> losersFailCleanly(scheduler));

        h.scenario("The Core Invariant Under a Chaos Workload",
                "no two confirmed meetings in the same room ever overlap",
                "300 competing bookings across 5 rooms and 12 overlapping windows",
                () -> noConfirmedOverlapsAfterChaosWorkload(scheduler));

        h.scenario("Unique Meeting Ids Under Contention",
                "the MTG-<n> counter never issues the same id twice",
                "40 rooms, 40 concurrent bookings in disjoint windows",
                () -> meetingIdsAreUnique(scheduler));

        h.scenario("Concurrent Cancellation Has One Winner",
                "the meeting state machine rejects every cancel after the first",
                "one booked meeting, 64 threads all calling cancelMeeting() at once",
                () -> concurrentCancelHasOneWinner(scheduler));

        h.scenario("Back-to-Back Bookings Are Allowed",
                "start == end is not an overlap, even under concurrency",
                "one room; two threads book 10:00-11:00 and 11:00-12:00 simultaneously",
                () -> backToBackWindowsBothSucceed(scheduler));

        h.scenario("Book/Cancel Churn Never Leaks a Room",
                "cancel always releases exactly the meeting it cancelled",
                "1000 book-then-cancel cycles on a single room across 100 tasks",
                () -> churnDoesNotLeakTheRoom(scheduler));

        h.scenario("Singleton Identity Across Threads",
                "double-checked locking hands every thread the same instance",
                THREADS + " threads calling getInstance() simultaneously",
                ConcurrencyTestSuite::singletonIsUnique);

        h.scenario("Observer Fault Isolation",
                "notification is a side effect — a broken channel cannot fail a booking",
                "a deliberately throwing observer registered alongside the healthy ones",
                () -> failingObserverDoesNotFailBooking(scheduler));

        h.scenario("Observer Registration During Notification",
                "the CopyOnWriteArrayList survives mutation while being iterated",
                "30 bookings while another thread adds/removes observers non-stop",
                () -> observerChurnDuringNotification(scheduler));

        // ── Documented defects — see concurrency-and-thread-safety.md §8 ───────
        h.knownDefect("Meeting.complete() Atomicity  [§8.2]",
                "only one thread should be able to complete a meeting",
                "3000 trials, 6 threads racing on complete() released by a spin-wait",
                () -> completeIsAtomic(scheduler));

        h.knownDefect("Booking Throughput vs Observer Latency  [§8.1]",
                "a slow notification channel should not serialize unrelated bookings",
                "20 bookings in 20 DIFFERENT rooms with a 50ms observer attached",
                () -> throughputIsNotCappedByObserverLatency(scheduler));

        System.exit(h.report());
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Scenarios
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * THE scenario. 200 threads, all asking for an overlapping window, exactly one
     * room big enough. The synchronized critical section must let precisely one
     * through — and because only one booking succeeds, the real [Email] and
     * [Calendar] observers fire exactly once, visibly.
     */
    private static void noDoubleBookingUnderContention(MeetingScheduler s) throws Exception {
        int capacity = nextExclusiveCapacity();
        Room only = new Room("dbl-1", "OnlyRoom", RoomType.CONFERENCE, capacity);
        s.addRoom(only);
        say("Registered room: " + only);

        LocalDateTime base = LocalDateTime.of(2040, 1, 1, 9, 0);
        TimeSlot slot = new TimeSlot(base, base.plusHours(1));
        AtomicInteger confirmed = new AtomicInteger();
        AtomicReference<Meeting> winner = new AtomicReference<>();

        say("Releasing %d threads at %s, all needing %d seats...", THREADS, slot, capacity);
        System.out.println();

        runConcurrently(THREADS, () -> {
            Meeting m = s.scheduleMeeting("Sprint Planning", ALICE, List.of(), slot, capacity);
            winner.set(m);
            confirmed.incrementAndGet();
        });

        System.out.println();
        say("Scheduled: " + winner.get());
        fact("threads that raced", THREADS);
        fact("bookings confirmed", confirmed.get());
        fact("rejected with exception", THREADS - confirmed.get());
        say("");
        say("Note the observer output above fired exactly ONCE — proof that only");
        say("one booking was ever committed, not merely that one counter won.");

        assertEquals(1, confirmed.get(),
                "exactly one of " + THREADS + " racing threads may book the only room");
    }

    /** The 199 losers must fail as domain errors, not as NPEs or corrupt state. */
    private static void losersFailCleanly(MeetingScheduler s) throws Exception {
        int capacity = nextExclusiveCapacity();
        Room only = new Room("clean-1", "SoleRoom", RoomType.BOARD_ROOM, capacity);
        s.addRoom(only);
        say("Registered room: " + only);

        LocalDateTime base = LocalDateTime.of(2041, 1, 1, 9, 0);
        TimeSlot slot = new TimeSlot(base, base.plusHours(1));
        List<Throwable> failures = Collections.synchronizedList(new ArrayList<>());
        AtomicInteger confirmed = new AtomicInteger();

        quietly(s, () -> {
            CountDownLatch gate = new CountDownLatch(1);
            ExecutorService pool = Executors.newFixedThreadPool(POOL_SIZE);
            CountDownLatch done = new CountDownLatch(THREADS);
            for (int i = 0; i < THREADS; i++) {
                pool.submit(() -> {
                    try {
                        gate.await();
                        s.scheduleMeeting("Design Review", BOB, List.of(), slot, capacity);
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
        });

        Set<String> types = new HashSet<>();
        for (Throwable t : failures) {
            types.add(t.getClass().getSimpleName());
        }

        fact("confirmed", confirmed.get());
        fact("failed", failures.size());
        fact("distinct exception types", types);
        if (!failures.isEmpty()) {
            fact("example message", failures.get(0).getMessage());
        }

        assertEquals(1, confirmed.get(), "one winner expected");
        assertEquals(THREADS - 1, failures.size(), "everyone else must fail");
        for (Throwable t : failures) {
            assertTrue(t instanceof MeetingSchedulerException,
                    "expected MeetingSchedulerException but got " + t.getClass().getName());
        }
        say("");
        say("Every loser got a MeetingSchedulerException with an actionable message.");
        say("No NPEs, no nulls, no half-written meetings.");
    }

    /**
     * The strongest scenario in the suite.
     *
     * Instead of counting winners, it runs a chaotic mixed workload and then
     * checks the domain invariant directly: for every room, no two confirmed
     * meetings overlap. This catches double-bookings the counting scenarios would
     * miss, because it does not depend on knowing in advance how many bookings
     * *should* have succeeded.
     */
    private static void noConfirmedOverlapsAfterChaosWorkload(MeetingScheduler s) throws Exception {
        final int rooms = 5;
        for (int i = 0; i < rooms; i++) {
            s.addRoom(new Room("chaos-" + i, "Chaos" + i, RoomType.CONFERENCE, 10));
        }
        say("Registered %d rooms, each seating 10.", rooms);
        say("Firing 300 bookings over 12 heavily-overlapping windows —");
        say("far more demand than supply, so contention is guaranteed.");

        LocalDateTime base = LocalDateTime.of(2042, 1, 1, 9, 0);
        List<Meeting> booked = Collections.synchronizedList(new ArrayList<>());

        quietly(s, () -> runConcurrentlyIgnoringFailures(300, index -> {
            Random rnd = new Random(index);          // deterministic per task
            int startOffset = rnd.nextInt(12);
            int lengthHours = 1 + rnd.nextInt(3);    // 1-3h -> lots of overlap
            TimeSlot slot = new TimeSlot(
                    base.plusHours(startOffset),
                    base.plusHours(startOffset + lengthHours));
            booked.add(s.scheduleMeeting("Chaos", ALICE, List.of(), slot, 10));
        }));

        // group confirmed meetings by room, then check every pair
        Map<String, List<Meeting>> byRoom = new HashMap<>();
        for (Meeting m : booked) {
            byRoom.computeIfAbsent(m.getRoom().getId(), k -> new ArrayList<>()).add(m);
        }

        int overlaps = 0;
        int pairsChecked = 0;
        for (Map.Entry<String, List<Meeting>> e : byRoom.entrySet()) {
            List<Meeting> ms = e.getValue();
            for (int i = 0; i < ms.size(); i++) {
                for (int j = i + 1; j < ms.size(); j++) {
                    pairsChecked++;
                    if (ms.get(i).getTimeSlot().overlaps(ms.get(j).getTimeSlot())) {
                        overlaps++;
                    }
                }
            }
        }

        fact("attempts", 300);
        fact("confirmed", booked.size());
        fact("rejected (no room free)", 300 - booked.size());
        fact("pairs checked for overlap", pairsChecked);
        fact("OVERLAPPING PAIRS", overlaps + "   <-- must be 0");

        say("");
        say("Per-room schedules produced. Rooms from earlier scenarios appear here");
        say("too — they seat 10+, so they legitimately qualify. That is the");
        say("un-resettable singleton leaking state between scenarios.");
        for (Map.Entry<String, List<Meeting>> e : byRoom.entrySet()) {
            List<Meeting> ms = new ArrayList<>(e.getValue());
            ms.sort((a, b) -> a.getTimeSlot().getStartTime().compareTo(b.getTimeSlot().getStartTime()));
            StringBuilder sb = new StringBuilder();
            for (Meeting m : ms) {
                sb.append(m.getTimeSlot()).append(' ');
            }
            say("  %-8s %s", e.getKey(), sb.toString().trim());
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

        quietly(s, () -> runConcurrentlyIgnoringFailures(rooms, index -> {
            TimeSlot slot = new TimeSlot(base.plusHours(index), base.plusHours(index + 1));
            ids.add(s.scheduleMeeting("Bug Triage", ALICE, List.of(), slot, 8).getId());
        }));

        Set<String> distinct = new HashSet<>(ids);
        fact("bookings confirmed", ids.size());
        fact("distinct ids", distinct.size());
        List<String> sample = new ArrayList<>(ids).subList(0, Math.min(5, ids.size()));
        fact("sample", String.join(", ", sample));

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
        Meeting m = s.scheduleMeeting("Retrospective", ALICE, List.of(),
                new TimeSlot(base, base.plusHours(1)), capacity);
        say("Scheduled: " + m);
        System.out.println();
        say("Now 64 threads all call cancelMeeting(\"%s\") at once...", m.getId());
        System.out.println();

        AtomicInteger cancelled = new AtomicInteger();
        runConcurrently(64, () -> {
            s.cancelMeeting(m.getId());
            cancelled.incrementAndGet();
        });

        System.out.println();
        fact("threads attempting cancel", 64);
        fact("cancels that succeeded", cancelled.get());
        fact("final status", m.getStatus());
        say("");
        say("The [Email]/[Calendar] cancellation notice appears exactly once —");
        say("the other 63 threads were rejected by the state machine.");

        assertEquals(1, cancelled.get(),
                "a meeting must cancel exactly once even under concurrent attempts");
    }

    /**
     * Back-to-back is explicitly allowed (start == end is not an overlap).
     * Run concurrently against a single room to prove the rule survives locking.
     */
    private static void backToBackWindowsBothSucceed(MeetingScheduler s) throws Exception {
        int capacity = nextExclusiveCapacity();
        Room room = new Room("b2b-1", "BackToBack", RoomType.CONFERENCE, capacity);
        s.addRoom(room);
        say("Registered room: " + room);
        say("Two threads book adjacent windows simultaneously. Overlap test is");
        say("start1 < end2 && start2 < end1 — strict, so 11:00 does not clash with 11:00.");
        System.out.println();

        LocalDateTime base = LocalDateTime.of(2045, 1, 1, 10, 0);
        AtomicReference<Meeting> first = new AtomicReference<>();
        AtomicReference<Meeting> second = new AtomicReference<>();

        CountDownLatch gate = new CountDownLatch(1);
        Thread t1 = new Thread(() -> {
            await(gate);
            first.set(s.scheduleMeeting("Standup", ALICE, List.of(),
                    new TimeSlot(base, base.plusHours(1)), capacity));
        });
        Thread t2 = new Thread(() -> {
            await(gate);
            second.set(s.scheduleMeeting("1-on-1 Sync", BOB, List.of(),
                    new TimeSlot(base.plusHours(1), base.plusHours(2)), capacity));
        });
        t1.start();
        t2.start();
        gate.countDown();
        t1.join(10_000);
        t2.join(10_000);

        System.out.println();
        say("Scheduled: " + first.get());
        say("Scheduled: " + second.get());

        assertTrue(first.get() != null, "10:00-11:00 booking should succeed");
        assertTrue(second.get() != null, "11:00-12:00 booking should succeed");
        assertSame(first.get().getRoom(), second.get().getRoom(),
                "both should land in the only qualifying room");
        say("");
        say("Both succeeded, in the SAME room — back-to-back is not a conflict.");
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
        AtomicInteger cycles = new AtomicInteger();

        AtomicInteger contended = new AtomicInteger();
        say("100 tasks x 10 attempts, all fighting over the SAME window in one room.");
        say("Only one task can hold it at a time, so most attempts are rejected —");
        say("that contention is the point: every winner must release cleanly.");

        quietly(s, () -> runConcurrentlyIgnoringFailures(100, index -> {
            for (int i = 0; i < 10; i++) {
                // Catch per attempt, not per task: a rejected attempt is the expected
                // outcome under contention and must not abandon the remaining cycles.
                try {
                    Meeting m = s.scheduleMeeting("Churn", ALICE, List.of(), slot, capacity);
                    s.cancelMeeting(m.getId());   // always release what we claimed
                    cycles.incrementAndGet();
                } catch (MeetingSchedulerException busy) {
                    contended.incrementAndGet();
                }
            }
        }));

        fact("attempts", 1000);
        fact("completed book/cancel cycles", cycles.get());
        fact("rejected (room busy)", contended.get());
        say("");
        say("If any claim leaked, the room would now be permanently busy.");
        say("Final check — book the same window one more time:");
        System.out.println();

        Meeting finalBooking = s.scheduleMeeting("Roadmap", BOB, List.of(), slot, capacity);

        System.out.println();
        say("Scheduled: " + finalBooking);
        assertTrue(finalBooking != null,
                "after book/cancel churn the room must still be bookable — a claim leaked");
    }

    /** Double-checked locking must not produce two schedulers. */
    private static void singletonIsUnique() throws Exception {
        Set<MeetingScheduler> seen = Collections.synchronizedSet(new HashSet<>());
        runConcurrently(THREADS, () -> seen.add(MeetingScheduler.getInstance()));

        fact("threads calling getInstance", THREADS);
        fact("distinct instances observed", seen.size());
        say("");
        say("volatile + double-checked locking: the second null-check prevents two");
        say("instances, and volatile prevents publishing a half-constructed object.");

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

        say("Registering a BROKEN observer that throws on every notification,");
        say("alongside the real Email/Calendar channels and a healthy counter.");
        say("Expect a 'notification failed' line on stderr, then normal delivery.");
        System.out.println();

        s.addObserver(broken);
        s.addObserver(healthy);
        try {
            LocalDateTime base = LocalDateTime.of(2047, 1, 1, 9, 0);
            Meeting m = s.scheduleMeeting("Postmortem", ALICE, List.of(),
                    new TimeSlot(base, base.plusHours(1)), capacity);

            System.out.println();
            say("Scheduled: " + m);
            fact("booking succeeded", m != null);
            fact("healthy observer notified", healthyCalls.get() == 1);

            assertTrue(m != null, "booking must succeed despite the throwing observer");
            assertEquals(1, healthyCalls.get(),
                    "the healthy observer must still be notified after the broken one threw");
        } finally {
            s.removeObserver(broken);
            s.removeObserver(healthy);
        }
        say("");
        say("The throw was caught per-observer, so it neither failed the booking");
        say("nor starved the channels registered after it.");
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
        AtomicInteger churns = new AtomicInteger();

        say("One thread adds/removes observers in a tight loop while %d bookings", rooms);
        say("are notified concurrently.");

        Thread churner = new Thread(() -> {
            while (stop.get() == 0) {
                MeetingObserver o = new MeetingObserver() {
                    public void onMeetingScheduled(Meeting m) { }
                    public void onMeetingCancelled(Meeting m) { }
                };
                s.addObserver(o);
                s.removeObserver(o);
                churns.incrementAndGet();
            }
        });
        churner.setDaemon(true);
        churner.start();

        try {
            quietly(s, () -> runConcurrentlyIgnoringFailures(rooms, index -> {
                try {
                    TimeSlot slot = new TimeSlot(base.plusHours(index), base.plusHours(index + 1));
                    s.scheduleMeeting("Demo", ALICE, List.of(), slot, 6);
                } catch (ConcurrentModificationException cme) {
                    escaped.add(cme);
                    throw cme;
                }
            }));
        } finally {
            stop.set(1);
            churner.join(2000);
        }

        fact("observer add/remove cycles", churns.get());
        fact("CME leaked", escaped.size() + "   <-- must be 0");

        assertEquals(0, escaped.size(),
                "observer registration during notification leaked a ConcurrentModificationException");
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Known defects (expected to fail until §9 fixes land)
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * §8.2 — Meeting.complete() is a check-then-act with no lock and a
     * non-volatile field, and the scheduler exposes no completeMeeting(), so it
     * is reachable with no protection at all. Multiple threads can all pass the
     * "is it SCHEDULED?" guard.
     *
     * Fix: make cancel()/complete() synchronized and status volatile (§9 Fix 2).
     */
    private static void completeIsAtomic(MeetingScheduler s) throws Exception {
        final int trials = 3000;
        final int racers = 6;

        // Build the Meeting directly rather than through the scheduler. The defect
        // lives in Meeting's own state machine, and going through scheduleMeeting
        // would (a) be far slower and (b) pollute the singleton with 3000 rooms,
        // making every later availability scan longer.
        Room room = new Room("cmp-room", "CmpRoom", RoomType.CONFERENCE, 10);
        LocalDateTime base = LocalDateTime.of(2049, 1, 1, 9, 0);
        TimeSlot slot = new TimeSlot(base, base.plusHours(1));

        say("Each trial builds a fresh SCHEDULED meeting, then releases %d threads", racers);
        say("into complete() at the same instant. Only one should succeed.");

        int doubleCompleted = 0;
        int worstCase = 1;

        for (int t = 0; t < trials; t++) {
            Meeting m = new Meeting("MTG-probe-" + t, "Demo", ALICE, List.of(), room, slot);

            AtomicInteger successes = new AtomicInteger();
            AtomicInteger spinning = new AtomicInteger();
            // A hot spin-wait, not a latch. park/unpark wakes threads in a cascade
            // tens of microseconds apart — an eternity next to the 2-3 instruction
            // window between complete()'s guard and its assignment. Threads already
            // spinning on separate cores see the flag flip at essentially the same
            // moment, which is what makes the race reachable at all.
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

        fact("trials", trials);
        fact("trials with MULTIPLE winners", doubleCompleted);
        fact("worst case winners at once", worstCase);
        say("");
        say("complete() is: if (status != SCHEDULED) throw;  status = COMPLETED;");
        say("Unsynchronized, on a non-volatile field — a textbook check-then-act.");
        say("Fix: synchronized cancel()/complete() + volatile status (§9 Fix 2).");

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

        say("%d bookings in %d DIFFERENT rooms and DIFFERENT windows — zero logical",
                bookings, bookings);
        say("contention, so they should run fully in parallel. A %dms observer is attached.",
                observerLatencyMs);

        long elapsed;
        s.addObserver(slow);
        try {
            LocalDateTime base = LocalDateTime.of(2050, 1, 1, 9, 0);
            long t0 = System.currentTimeMillis();
            elapsed = 0;
            quietly(s, () -> runConcurrentlyIgnoringFailures(bookings, index -> {
                TimeSlot slot = new TimeSlot(base.plusHours(index), base.plusHours(index + 1));
                s.scheduleMeeting("Hiring Sync", ALICE, List.of(), slot, capacity);
            }));
            elapsed = System.currentTimeMillis() - t0;
        } finally {
            s.removeObserver(slow);
        }

        long serialized = bookings * observerLatencyMs;
        long budget = serialized / 2;      // generous: half of fully-serialized

        fact("ideal (fully parallel)", "~" + observerLatencyMs + " ms");
        fact("fully serialized would be", "~" + serialized + " ms");
        fact("ACTUAL elapsed", elapsed + " ms");
        fact("pass budget", "< " + budget + " ms");
        say("");
        say("notifyMeetingScheduled() is called INSIDE the synchronized method, so");
        say("the global scheduler monitor is held across every observer's I/O.");
        say("Fix: notify after releasing the lock (§9 Fix 1) — measured 1074ms -> 56ms.");

        assertTrue(elapsed < budget,
                bookings + " independent bookings took " + elapsed + "ms; fully parallel is ~"
                        + observerLatencyMs + "ms and fully serialized is ~" + serialized
                        + "ms — the scheduler monitor is held across observer I/O");
    }

    // ─────────────────────────────────────────────────────────────────────────
    // helpers
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Runs a body with the Email/Calendar observers detached.
     *
     * High-volume scenarios confirm hundreds of bookings; leaving the real
     * channels attached would bury the narration under thousands of lines. The
     * low-volume scenarios keep them on, which is where seeing the actual
     * notification output is worth something.
     */
    private static void quietly(MeetingScheduler s, ThrowingRunnable body) throws Exception {
        s.removeObserver(EMAIL);
        s.removeObserver(CALENDAR);
        try {
            body.run();
        } finally {
            s.addObserver(EMAIL);
            s.addObserver(CALENDAR);
        }
    }

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
