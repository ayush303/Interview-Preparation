package LLD.meetingRoomScheduler.solution.tests;

import LLD.meetingRoomScheduler.solution.MeetingScheduler;
import LLD.meetingRoomScheduler.solution.enums.RoomType;
import LLD.meetingRoomScheduler.solution.exceptions.MeetingSchedulerException;
import LLD.meetingRoomScheduler.solution.models.Meeting;
import LLD.meetingRoomScheduler.solution.models.Room;
import LLD.meetingRoomScheduler.solution.models.TimeSlot;
import LLD.meetingRoomScheduler.solution.models.User;
import LLD.meetingRoomScheduler.solution.strategies.BestFitStrategy;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * A NARRATED simulation: 500 users and 200 meeting rooms, booked concurrently by a
 * 30-thread worker pool, with every attempt logged as it happens.
 *
 *   java -cp out LLD.meetingRoomScheduler.solution.tests.ConcurrentBookingSimulation
 *   java -cp out LLD...ConcurrentBookingSimulation --summary   (report only, no log lines)
 *
 * Where ConcurrencyTestSuite *asserts* that the scheduler is thread-safe, this
 * class lets you *watch* it be thread-safe. The log lines interleave exactly as
 * the threads ran, so you can see several bookings land in different rooms at the
 * same moment, and see losing threads bounce off a room that was claimed a
 * microsecond earlier.
 *
 * Four phases, each demonstrating something different:
 *
 *   PHASE 1  Thundering herd — all 500 users stampede the same 10:00 slot. With
 *            200 rooms, exactly 200 can win and 300 must be turned away.
 *   PHASE 2  Normal day     — 1500 requests spread across 09:00-18:00.
 *            Shows high parallel throughput with little contention.
 *   PHASE 3  Cancellations  — meetings are cancelled and the freed windows are
 *            immediately re-booked by other users.
 *   PHASE 4  Verification   — the room-by-room schedule is printed and every
 *            confirmed pair is checked for overlap.
 *
 * Exits non-zero if any two confirmed meetings in one room overlap, so this
 * doubles as a stress test.
 */
public class ConcurrentBookingSimulation {

    private static final int USER_COUNT = 500;
    private static final int ROOM_COUNT = 200;
    private static final int WORKER_THREADS = 30;

    /** Requests each user makes during phase 2. */
    private static final int REQUESTS_PER_USER = 3;

    /** Meetings cancelled in phase 3. */
    private static final int CANCELLATIONS = 40;

    /** Pass --summary to suppress the per-attempt log lines and print only the report. */
    private static boolean verbose = true;

    /** The business day the simulation books against. */
    private static final LocalDateTime DAY = LocalDateTime.of(2026, 3, 16, 9, 0);
    private static final int SLOTS_IN_DAY = 9;          // 09:00 → 18:00

    private static final Object CONSOLE = new Object();
    private static final AtomicInteger sequence = new AtomicInteger();
    private static final long START_NANOS = System.nanoTime();

    private static final List<Meeting> confirmed = Collections.synchronizedList(new ArrayList<>());
    private static final AtomicInteger rejected = new AtomicInteger();
    private static final Map<String, AtomicInteger> perThread = new LinkedHashMap<>();

    public static void main(String[] args) throws Exception {
        for (String arg : args) {
            if ("--summary".equals(arg) || "-s".equals(arg)) {
                verbose = false;
            }
        }

        MeetingScheduler scheduler = MeetingScheduler.getInstance();
        // BestFit makes the log more interesting: you can see a 4-seat huddle room
        // being chosen for a 3-person meeting instead of a 30-seat board room.
        scheduler.setRoomSelectionStrategy(new BestFitStrategy());

        List<Room> rooms = createRooms(scheduler);
        List<User> users = createUsers();

        banner("SETUP");
        printRoomInventory(rooms);
        System.out.printf("%n  %d users created: %s, %s, %s … %s%n",
                users.size(), users.get(0).getName(), users.get(1).getName(),
                users.get(2).getName(), users.get(users.size() - 1).getName());
        System.out.printf("  worker pool: %d threads, shared by every phase%n", WORKER_THREADS);
        System.out.println("  room-selection strategy: BestFit (smallest room that fits)");

        phase1ThunderingHerd(scheduler, users);
        phase2NormalDay(scheduler, users);
        phase3Cancellations(scheduler, users);
        phase4Verification(rooms);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // PHASE 1 — every user wants the same slot at the same instant
    // ─────────────────────────────────────────────────────────────────────────

    private static void phase1ThunderingHerd(MeetingScheduler scheduler, List<User> users)
            throws Exception {
        banner("PHASE 1 — THUNDERING HERD: all " + USER_COUNT
                + " users want 10:00-11:00");
        System.out.println("  All " + USER_COUNT + " requests are queued behind a start gun, then "
                + WORKER_THREADS + " threads");
        System.out.println("  release at once against only " + ROOM_COUNT
                + " rooms, each user needing a different size.");
        System.out.println("  Expect: confirmations in DIFFERENT rooms, then rejections once");
        System.out.println("  every room big enough is taken.\n");

        TimeSlot popular = new TimeSlot(DAY.plusHours(1), DAY.plusHours(2));

        // All 500 requests are queued into the 30-thread pool and held behind a start
        // gun, so the pool is saturated the instant it opens: 30 threads hammering
        // the same time slot, with 470 more queued right behind them.
        //
        // WHY THE GATE IS SAFE HERE, when an earlier version of this phase deadlocked:
        // the latch is counted down by MAIN, never by a peer task. Tasks that wait on
        // *each other* must never share a pool smaller than their own count — the
        // first 30 would occupy every thread and wait forever for peers that can
        // never be scheduled (thread-starvation deadlock). Waiting on the submitter
        // is fine at any pool size: the 30 running tasks park, the other 470 sit in
        // the queue, and main releases everyone.
        //
        // The gate is a CountDownLatch (park-based), not the hot spin-wait used in
        // ConcurrencyTestSuite. Spin-release wins when the race window is a couple of
        // instructions wide; here the critical section scans 200 rooms, so parking is
        // both kinder to the CPU and more realistic. Different tool for a different job.
        CountDownLatch startGun = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(USER_COUNT);
        ExecutorService pool = namedPool();

        Random rnd = new Random(7);
        for (User user : users) {
            final int needed = 2 + rnd.nextInt(14);     // 2..15 attendees
            pool.submit(() -> {
                try {
                    startGun.await();
                    attempt(scheduler, user, "Standup", popular, needed);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    done.countDown();
                }
            });
        }
        startGun.countDown();                           // 🔫
        done.await(120, TimeUnit.SECONDS);
        pool.shutdown();

        System.out.printf("%n  → %d confirmed, %d rejected in this phase.%n",
                confirmed.size(), rejected.get());
        System.out.println("  Every confirmation above landed in a DIFFERENT room: "
                + "no room appears twice for 10:00-11:00.");
    }

    // ─────────────────────────────────────────────────────────────────────────
    // PHASE 2 — a normal working day
    // ─────────────────────────────────────────────────────────────────────────

    private static void phase2NormalDay(MeetingScheduler scheduler, List<User> users)
            throws Exception {
        int before = confirmed.size();
        int rejectedBefore = rejected.get();

        banner("PHASE 2 — A NORMAL DAY: users book across 09:00-18:00 concurrently");
        System.out.println("  Demand is now spread over 9 windows x 30 rooms, so most "
                + "requests succeed");
        System.out.println("  and threads proceed in parallel across different rooms.\n");

        ExecutorService pool = namedPool();
        CountDownLatch done = new CountDownLatch(USER_COUNT * REQUESTS_PER_USER);
        Random rnd = new Random(42);

        for (User user : users) {
            for (int k = 0; k < REQUESTS_PER_USER; k++) {
                final int hour = rnd.nextInt(SLOTS_IN_DAY);
                final int needed = 2 + rnd.nextInt(12);
                final String subject = SUBJECTS[rnd.nextInt(SUBJECTS.length)];
                pool.submit(() -> {
                    TimeSlot slot = new TimeSlot(DAY.plusHours(hour), DAY.plusHours(hour + 1));
                    attempt(scheduler, user, subject, slot, needed);
                    done.countDown();
                });
            }
        }
        done.await(60, TimeUnit.SECONDS);
        pool.shutdown();

        System.out.printf("%n  → %d confirmed, %d rejected in this phase.%n",
                confirmed.size() - before, rejected.get() - rejectedBefore);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // PHASE 3 — cancellations free rooms, and they are immediately re-taken
    // ─────────────────────────────────────────────────────────────────────────

    private static void phase3Cancellations(MeetingScheduler scheduler, List<User> users)
            throws Exception {
        banner("PHASE 3 — CANCELLATIONS: freed windows become bookable again");
        System.out.println("  " + CANCELLATIONS + " meetings are cancelled while other "
                + "threads race to claim the freed slots.\n");

        List<Meeting> snapshot = new ArrayList<>(confirmed);
        Collections.shuffle(snapshot, new Random(11));
        List<Meeting> toCancel = snapshot.subList(0, Math.min(CANCELLATIONS, snapshot.size()));

        ExecutorService pool = namedPool();
        CountDownLatch done = new CountDownLatch(toCancel.size() * 2);
        AtomicInteger reclaimed = new AtomicInteger();

        for (Meeting m : toCancel) {
            // canceller
            pool.submit(() -> {
                try {
                    scheduler.cancelMeeting(m.getId());
                    confirmed.remove(m);
                    log("CANCEL", m.getOrganizer().getName(),
                            String.format("%-7s", m.getTimeSlot()),
                            "🗑  RELEASED  " + m.getId() + "  " + m.getRoom().getName());
                } catch (MeetingSchedulerException e) {
                    log("CANCEL", m.getOrganizer().getName(), "", "❌ " + e.getMessage());
                } finally {
                    done.countDown();
                }
            });
            // someone else trying to grab the same window
            final User opportunist = users.get((int) (Math.random() * users.size()));
            pool.submit(() -> {
                try {
                    Thread.sleep(2);   // let the cancel land first, most of the time
                    Meeting got = scheduler.scheduleMeeting("Re-booked", opportunist,
                            List.of(), m.getTimeSlot(), m.getRoom().getCapacity());
                    confirmed.add(got);
                    reclaimed.incrementAndGet();
                    log("REBOOK", opportunist.getName(),
                            String.format("%-7s", got.getTimeSlot()),
                            "♻️  CLAIMED   " + got.getId() + "  " + got.getRoom().getName());
                } catch (Exception e) {
                    log("REBOOK", opportunist.getName(), "", "❌ could not reclaim");
                } finally {
                    done.countDown();
                }
            });
        }
        done.await(60, TimeUnit.SECONDS);
        pool.shutdown();

        System.out.printf("%n  → %d freed windows were successfully re-booked.%n",
                reclaimed.get());
    }

    // ─────────────────────────────────────────────────────────────────────────
    // PHASE 4 — print the resulting schedule and verify the core invariant
    // ─────────────────────────────────────────────────────────────────────────

    private static void phase4Verification(List<Room> rooms) {
        banner("PHASE 4 — RESULTING SCHEDULE (one row per room)");

        Map<String, List<Meeting>> byRoom = new LinkedHashMap<>();
        for (Room r : rooms) {
            byRoom.put(r.getId(), new ArrayList<>());
        }
        synchronized (confirmed) {
            for (Meeting m : confirmed) {
                byRoom.get(m.getRoom().getId()).add(m);
            }
        }

        // Occupancy grid: one column per hour of the business day.
        // ■ = booked, · = free. Reads at a glance, unlike a list of time ranges.
        StringBuilder header = new StringBuilder();
        for (int h = 0; h < SLOTS_IN_DAY; h++) {
            header.append(String.format("%3d", 9 + h));
        }
        System.out.printf("  %-17s %-13s %4s  %s   %s%n",
                "ROOM", "TYPE", "CAP", header, "BOOKED  ORGANIZERS");
        System.out.println("  " + "-".repeat(118));

        for (Room r : rooms) {
            List<Meeting> ms = byRoom.get(r.getId());
            ms.sort(Comparator.comparing(m -> m.getTimeSlot().getStartTime()));

            StringBuilder grid = new StringBuilder();
            for (int h = 0; h < SLOTS_IN_DAY; h++) {
                LocalDateTime hourStart = DAY.plusHours(h);
                boolean busy = false;
                for (Meeting m : ms) {
                    if (m.getTimeSlot().getStartTime().equals(hourStart)) {
                        busy = true;
                        break;
                    }
                }
                grid.append(busy ? "  ■" : "  ·");
            }

            // show who booked it — proof that many different users share one room
            StringBuilder who = new StringBuilder();
            int shown = 0;
            for (Meeting m : ms) {
                if (shown == 3) {
                    who.append("+").append(ms.size() - 3).append(" more");
                    break;
                }
                if (shown > 0) {
                    who.append(", ");
                }
                who.append(m.getOrganizer().getName());
                shown++;
            }

            System.out.printf("  %-17s %-13s %4d  %s   %4d    %s%n",
                    r.getName(), r.getRoomType(), r.getCapacity(),
                    grid, ms.size(), who);
        }
        System.out.println("\n  legend: ■ booked   · free   (columns are 09:00 … 17:00 starts)");

        // ---- the invariant: no two confirmed meetings in a room may overlap ----
        banner("PHASE 4 — INVARIANT CHECK");
        int overlaps = 0;
        String example = null;
        for (Map.Entry<String, List<Meeting>> e : byRoom.entrySet()) {
            List<Meeting> ms = e.getValue();
            for (int i = 0; i < ms.size(); i++) {
                for (int j = i + 1; j < ms.size(); j++) {
                    if (ms.get(i).getTimeSlot().overlaps(ms.get(j).getTimeSlot())) {
                        overlaps++;
                        if (example == null) {
                            example = ms.get(i) + "  vs  " + ms.get(j);
                        }
                    }
                }
            }
        }

        int totalAttempts = confirmed.size() + rejected.get();
        int distinctThreads;
        synchronized (perThread) {
            distinctThreads = perThread.size();
        }

        System.out.printf("  users                  : %d%n", USER_COUNT);
        System.out.printf("  rooms                  : %d%n", ROOM_COUNT);
        System.out.printf("  worker pool threads    : %d%n", WORKER_THREADS);
        System.out.printf("  threads that won a room: %d%n", distinctThreads);
        System.out.printf("  confirmed meetings     : %d%n", confirmed.size());
        System.out.printf("  rejected (no room)     : %d%n", rejected.get());
        System.out.printf("  confirmed + rejected   : %d%n", totalAttempts);
        System.out.printf("  overlapping pairs      : %d   <-- the number that matters%n", overlaps);

        System.out.println("\n  bookings per pool thread:");
        synchronized (perThread) {
            perThread.entrySet().stream()
                    .sorted(Map.Entry.comparingByKey())
                    .forEach(e -> System.out.printf("    %-12s %3d confirmed%n",
                            e.getKey(), e.getValue().get()));
        }

        System.out.println();
        if (overlaps == 0) {
            System.out.println("  ✅ PASS — no room is ever double-booked.");
            System.out.println("     " + confirmed.size() + " meetings confirmed across "
                    + ROOM_COUNT + " rooms by " + distinctThreads + " different threads racing");
            System.out.println("     against each other, and not one pair of them overlaps in "
                    + "the same room.");
        } else {
            System.out.println("  ❌ FAIL — " + overlaps + " overlapping pairs found!");
            System.out.println("     example: " + example);
        }
        System.out.println("=".repeat(108));
        System.exit(overlaps == 0 ? 0 : 1);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // booking + logging
    // ─────────────────────────────────────────────────────────────────────────

    private static void attempt(MeetingScheduler scheduler, User user,
                                String subject, TimeSlot slot, int needed) {
        try {
            Meeting m = scheduler.scheduleMeeting(subject, user, List.of(), slot, needed);
            confirmed.add(m);
            countThread();
            log("BOOK", user.getName(),
                    String.format("%-7s %2d seats", slot, needed),
                    String.format("✅ CONFIRMED %-8s in %-16s (%s, cap %d)",
                            m.getId(), m.getRoom().getName(),
                            m.getRoom().getRoomType(), m.getRoom().getCapacity()));
        } catch (MeetingSchedulerException e) {
            rejected.incrementAndGet();
            log("BOOK", user.getName(),
                    String.format("%-7s %2d seats", slot, needed),
                    "❌ REJECTED   no room free that seats " + needed);
        }
    }

    /**
     * One synchronized print per line. Without the lock the lines tear into each
     * other and the output becomes unreadable — which is itself a small lesson in
     * shared mutable state: System.out is one.
     */
    private static void log(String action, String who, String what, String outcome) {
        int seq = sequence.incrementAndGet();
        if (!verbose) {
            return;
        }
        synchronized (CONSOLE) {
            System.out.printf("  #%04d %-10s %8.1fms  %-6s %-13s %-20s %s%n",
                    seq,
                    Thread.currentThread().getName(),
                    (System.nanoTime() - START_NANOS) / 1_000_000.0,
                    action, who, what, outcome);
        }
    }

    private static void countThread() {
        String name = Thread.currentThread().getName();
        synchronized (perThread) {
            perThread.computeIfAbsent(name, k -> new AtomicInteger()).incrementAndGet();
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // setup
    // ─────────────────────────────────────────────────────────────────────────

    private static List<Room> createRooms(MeetingScheduler scheduler) {
        List<Room> rooms = new ArrayList<>();
        for (int i = 0; i < ROOM_COUNT; i++) {
            RoomType type;
            int capacity;
            int tier = i / 3;                        // varies within each type
            if (i % 3 == 0) {
                type = RoomType.HUDDLE_SPACE;
                capacity = 2 + (tier % 3);           // 2..4
            } else if (i % 3 == 1) {
                type = RoomType.CONFERENCE;
                capacity = 6 + (tier % 15);          // 6..20
            } else {
                type = RoomType.BOARD_ROOM;
                capacity = 10 + (tier % 21);         // 10..30
            }
            Room room = new Room("R" + (i + 1), roomName(i), type, capacity);
            scheduler.addRoom(room);
            rooms.add(room);
        }
        return rooms;
    }

    /**
     * 30 base names are not enough for 200 rooms, so names cycle with a floor
     * suffix: Everest-F1, Alps-F1 … Everest-F2, and so on. Still recognisable,
     * and guaranteed distinct.
     */
    private static String roomName(int index) {
        String base = ROOM_NAMES[index % ROOM_NAMES.length];
        int floor = (index / ROOM_NAMES.length) + 1;
        return base + "-F" + floor;
    }

    private static List<User> createUsers() {
        List<User> users = new ArrayList<>();
        for (int i = 0; i < USER_COUNT; i++) {
            String name = userName(i);
            users.add(new User("U" + (i + 1), name,
                    name.toLowerCase().replace('.', '_') + "@example.com"));
        }
        return users;
    }

    /** 50 base names cycled with a team number: Alice.T1 … Alice.T10. */
    private static String userName(int index) {
        String base = USER_NAMES[index % USER_NAMES.length];
        int team = (index / USER_NAMES.length) + 1;
        return base + ".T" + team;
    }

    private static ExecutorService namedPool() {
        AtomicInteger n = new AtomicInteger();
        ThreadFactory factory = r -> {
            Thread t = new Thread(r, String.format("booker-%02d", n.incrementAndGet()));
            t.setDaemon(true);
            return t;
        };
        return Executors.newFixedThreadPool(WORKER_THREADS, factory);
    }

    /**
     * At 200 rooms a full listing is noise, so summarise by type and show a sample.
     * The complete room-by-room picture is printed in phase 4 anyway.
     */
    private static void printRoomInventory(List<Room> rooms) {
        System.out.printf("  %d rooms registered:%n%n", rooms.size());
        System.out.printf("  %-14s %6s   %s%n", "TYPE", "COUNT", "CAPACITY RANGE");
        System.out.println("  " + "-".repeat(45));
        for (RoomType type : RoomType.values()) {
            int count = 0, min = Integer.MAX_VALUE, max = 0, seats = 0;
            for (Room r : rooms) {
                if (r.getRoomType() == type) {
                    count++;
                    seats += r.getCapacity();
                    min = Math.min(min, r.getCapacity());
                    max = Math.max(max, r.getCapacity());
                }
            }
            if (count > 0) {
                System.out.printf("  %-14s %6d   %d-%d seats (%d total)%n",
                        type, count, min, max, seats);
            }
        }
        int totalSeats = 0;
        for (Room r : rooms) {
            totalSeats += r.getCapacity();
        }
        System.out.printf("  %-14s %6d   %d seats across the building%n",
                "ALL", rooms.size(), totalSeats);
        System.out.printf("%n  sample: %s, %s, %s … %s%n",
                rooms.get(0).getName(), rooms.get(1).getName(),
                rooms.get(2).getName(), rooms.get(rooms.size() - 1).getName());
    }

    private static void banner(String title) {
        System.out.println();
        System.out.println("=".repeat(108));
        System.out.println("  " + title);
        System.out.println("=".repeat(108));
    }

    private static final String[] SUBJECTS = {
            "Sprint Planning", "Design Review", "1-on-1", "Retro", "Architecture",
            "Hiring Sync", "Roadmap", "Bug Triage", "Demo", "Postmortem"
    };

    private static final String[] ROOM_NAMES = {
            "Everest", "Alps", "Nook", "Banyan", "Cedar", "Denali", "Eiger", "Fuji",
            "Ganges", "Himalaya", "Indus", "Jura", "Kilimanjaro", "Lotus", "Matterhorn",
            "Nile", "Olympus", "Pamir", "Quarry", "Rockies", "Sierra", "Tahoe", "Ural",
            "Vesuvius", "Whitney", "Xanadu", "Yosemite", "Zagros", "Andes", "Baltic"
    };

    private static final String[] USER_NAMES = {
            "Alice", "Bob", "Charlie", "Diana", "Evan", "Fatima", "Gopal", "Hannah",
            "Ivan", "Jaya", "Karan", "Leela", "Mohit", "Nadia", "Omar", "Priya",
            "Quentin", "Rahul", "Sneha", "Tarun", "Uma", "Vikram", "Wendy", "Xavier",
            "Yash", "Zara", "Aditya", "Bhavna", "Chirag", "Deepa", "Esha", "Farhan",
            "Girish", "Hina", "Ishaan", "Juhi", "Kabir", "Lakshmi", "Manish", "Neha",
            "Ojas", "Pooja", "Qasim", "Ritu", "Sameer", "Tanvi", "Utkarsh", "Varsha",
            "Yogesh", "Zoya"
    };
}
