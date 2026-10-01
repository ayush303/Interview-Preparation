package LLD.movie_booking_system.solutions.tests;

import LLD.movie_booking_system.solutions.BookingManager;
import LLD.movie_booking_system.solutions.MovieBookingService;
import LLD.movie_booking_system.solutions.SeatLockManager;
import LLD.movie_booking_system.solutions.enums.SeatStatus;
import LLD.movie_booking_system.solutions.models.*;
import LLD.movie_booking_system.solutions.strategy.pricing.WeekdayPricingStrategy;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static LLD.movie_booking_system.solutions.tests.Fixtures.*;
import static LLD.movie_booking_system.solutions.tests.TestHarness.*;

/**
 * Concurrency tests for seat locking and booking: 500 threads, 200 users.
 *
 * Every scenario starts {@value Fixtures#THREADS} dedicated threads, parks them on a
 * start latch and releases them together, so they genuinely collide. The threads are
 * shared out across {@value Fixtures#USERS} customers ({@code thread i → user i % 200}),
 * so most users have two or three requests in flight at once.
 *
 * A passing concurrency test cannot prove there is no race, only fail to find one.
 * So each scenario checks invariants over the whole show (no seat sold twice, no seat
 * left LOCKED, every BOOKED seat has a booking, every charge either became a booking
 * or was refunded), not just how many calls returned successfully.
 *
 * Payments use deterministic test strategies: the real CreditCardPaymentStrategy
 * fails 5% of the time at random, which would make the counts unpredictable.
 *
 * Run from the repository root:
 *   javac -d out $(find LLD/movie_booking_system/solutions -name '*.java')
 *   java  -cp out LLD.movie_booking_system.solutions.tests.ConcurrencyTests
 */
public class ConcurrencyTests {

    /** SeatLockManager.LOCK_TIMEOUT_MS; holds expire this long after lockSeats. */
    private static final long LOCK_TIMEOUT_MS = 500;

    public static void main(String[] args) {
        TestHarness t = new TestHarness("Movie Booking — Concurrency Tests",
                THREADS + " threads, " + USERS + " users, released together from a latch. "
                        + "Nothing in SeatLockManager or BookingManager is mocked.");
        List<Customer> users = users(USERS);

        t.scenario("500 threads, 200 users, all want the SAME seat",
                "exactly one booking wins; the other 499 are rejected before paying", () -> {
            Show show = newShow(50);
            Seat target = show.getScreen().getSeats().get(0);
            RecordingPayment pay = RecordingPayment.instant();
            Queue<Booking> bookings = new ConcurrentLinkedQueue<>();

            withBookingManager(bm -> race(THREADS, i ->
                    bm.createBooking(users.get(i % USERS), show, List.of(target), pay).ifPresent(bookings::add)));

            checkEquals(1, bookings.size(), "bookings");
            checkEquals(1, pay.charges.get(), "cards charged (losers never reach payment)");
            checkEquals(0, pay.refunds.get(), "refunds");
            checkEquals(SeatStatus.BOOKED, target.getStatus(), "seat status");
            assertConsistent(show, bookings);
        });

        t.scenario("500 threads race for overlapping seat PAIRS on a 100-seat show",
                "seat i and i+1 are claimed together or not at all; no seat is ever in two bookings", () -> {
            Show show = newShow(100);
            List<Seat> seats = show.getScreen().getSeats();
            RecordingPayment pay = RecordingPayment.instant();
            Queue<Booking> bookings = new ConcurrentLinkedQueue<>();

            withBookingManager(bm -> race(THREADS, i -> {
                int first = i % 99;
                bm.createBooking(users.get(i % USERS), show, List.of(seats.get(first), seats.get(first + 1)), pay)
                        .ifPresent(bookings::add);
            }));

            note("winning pairs: " + bookings.size() + " of " + THREADS + " attempts");
            // Every pair (i, i+1) was requested at least 5 times and a booked seat is never freed,
            // so if two neighbours were both still free, some request for them was wrongly rejected.
            long freeNeighbours = 0;
            for (int i = 0; i < 99; i++) {
                if (seats.get(i).getStatus() == SeatStatus.AVAILABLE
                        && seats.get(i + 1).getStatus() == SeatStatus.AVAILABLE) freeNeighbours++;
            }
            checkEquals(0L, freeNeighbours, "adjacent free pairs left unsold (no lost requests)");
            checkEquals((long) bookings.size() * 2, countStatus(show, SeatStatus.BOOKED), "BOOKED seats = 2 × bookings");
            checkEquals(bookings.size(), pay.charges.get(), "charges = bookings");
            assertConsistent(show, bookings);
        });

        t.scenario("500 threads book random 1–4 seat groups on a 60-seat show",
                "heavy, irregular overlap still never produces a partial or double booking", () -> {
            Show show = newShow(60);
            RecordingPayment pay = RecordingPayment.instant();
            Queue<Booking> bookings = new ConcurrentLinkedQueue<>();
            AtomicInteger rejected = new AtomicInteger();

            withBookingManager(bm -> race(THREADS, i -> {
                List<Seat> want = randomSeats(show, 1 + ThreadLocalRandom.current().nextInt(4));
                Optional<Booking> b = bm.createBooking(users.get(i % USERS), show, want, pay);
                if (b.isPresent()) bookings.add(b.get()); else rejected.incrementAndGet();
            }));

            note("bookings " + bookings.size() + ", rejected " + rejected.get());
            checkEquals(THREADS, bookings.size() + rejected.get(), "every request got an answer");
            long bookedSeats = bookings.stream().mapToLong(b -> b.getSeats().size()).sum();
            checkEquals(bookedSeats, countStatus(show, SeatStatus.BOOKED), "BOOKED seats = seats in bookings");
            checkEquals(60 - bookedSeats, countStatus(show, SeatStatus.AVAILABLE), "every other seat is AVAILABLE");
            assertConsistent(show, bookings);
        });

        t.scenario("500 threads, 500 DIFFERENT seats",
                "no false contention: when nobody overlaps, every one of the 500 bookings succeeds", () -> {
            Show show = newShow(500);
            List<Seat> seats = show.getScreen().getSeats();
            RecordingPayment pay = RecordingPayment.instant();
            Queue<Booking> bookings = new ConcurrentLinkedQueue<>();

            withBookingManager(bm -> race(THREADS, i ->
                    bm.createBooking(users.get(i % USERS), show, List.of(seats.get(i)), pay).ifPresent(bookings::add)));

            checkEquals(THREADS, bookings.size(), "bookings");
            checkEquals((long) THREADS, countStatus(show, SeatStatus.BOOKED), "BOOKED seats");
            Map<String, Long> perUser = new HashMap<>();
            bookings.forEach(b -> perUser.merge(b.getUser().getId(), 1L, Long::sum));
            checkEquals(USERS, perUser.size(), "distinct users with a booking");
            assertConsistent(show, bookings);
        });

        t.scenario("Half the users' cards are declined (500 threads, 100-seat show)",
                "a failed payment releases its seats; only paying users own seats; nothing stays LOCKED", () -> {
            Show show = newShow(100);
            RecordingPayment good = RecordingPayment.instant();
            RecordingPayment bad = RecordingPayment.declining();
            Queue<Booking> bookings = new ConcurrentLinkedQueue<>();

            withBookingManager(bm -> race(THREADS, i -> {
                int u = i % USERS;
                List<Seat> want = randomSeats(show, 1 + ThreadLocalRandom.current().nextInt(3));
                bm.createBooking(users.get(u), show, want, u % 2 == 0 ? good : bad).ifPresent(bookings::add);
            }));

            note("declined attempts that reached payment: " + bad.charges.get());
            check(bad.charges.get() > 0, "some declined users did get their seats locked and tried to pay");
            check(bookings.stream().allMatch(b -> users.indexOf(b.getUser()) % 2 == 0),
                    "every booking belongs to a user whose card works");
            checkEquals(good.charges.get(), bookings.size(), "successful charges = bookings");
            checkEquals(0, bad.refunds.get() + good.refunds.get(), "refunds (none needed)");
            assertConsistent(show, bookings);
        });

        t.scenario("Payment slower than the " + LOCK_TIMEOUT_MS + " ms hold (250 slow, 250 fast)",
                "a hold that expires during payment is refunded, not booked; fast payers are unaffected", () -> {
            Show show = newShow(500);
            List<Seat> seats = show.getScreen().getSeats();
            RecordingPayment fast = RecordingPayment.instant();
            RecordingPayment slow = RecordingPayment.slow(LOCK_TIMEOUT_MS + 300);
            Queue<Booking> bookings = new ConcurrentLinkedQueue<>();

            withBookingManager(bm -> race(THREADS, i ->
                    bm.createBooking(users.get(i % USERS), show, List.of(seats.get(i)), i % 2 == 0 ? slow : fast)
                            .ifPresent(bookings::add)));

            checkEquals(250, fast.charges.get(), "fast charges");
            checkEquals(250, bookings.size(), "bookings (all fast)");
            checkEquals(250, slow.charges.get(), "slow charges");
            checkEquals(250, slow.refunds.get(), "slow refunds (hold expired before confirm)");
            checkEquals(250L, countStatus(show, SeatStatus.AVAILABLE), "expired seats back on sale");
            assertConsistent(show, bookings);
        });

        t.scenario("Expired holds are resold while the original holder is still paying",
                "200 slow holders lose their seats to 300 later buyers; each seat is sold once and every slow payer is refunded", () -> {
            Show show = newShow(200);
            List<Seat> seats = show.getScreen().getSeats();
            RecordingPayment slow = RecordingPayment.slow(1_500);
            RecordingPayment fast = RecordingPayment.instant();
            Queue<Booking> bookings = new ConcurrentLinkedQueue<>();

            withBookingManager(bm -> race(THREADS, i -> {
                if (i < 200) {                              // first wave: hold seat i, pay very slowly
                    bm.createBooking(users.get(i), show, List.of(seats.get(i)), slow).ifPresent(bookings::add);
                } else {                                    // second wave: arrive after the holds expired
                    sleep(LOCK_TIMEOUT_MS + 400);
                    int s = (i - 200) % 200;
                    Customer other = users.get((s + 1 + i / 200) % USERS);   // never the original holder
                    bm.createBooking(other, show, List.of(seats.get(s)), fast).ifPresent(bookings::add);
                }
            }));

            checkEquals(200, slow.charges.get(), "slow holders that reached payment");
            checkEquals(200, slow.refunds.get(), "slow holders refunded");
            check(bookings.stream().noneMatch(b -> users.indexOf(b.getUser()) == seatIndex(seats, b)),
                    "no seat went to its original slow holder");
            checkEquals(200, bookings.size(), "bookings (every seat resold exactly once)");
            checkEquals(200L, countStatus(show, SeatStatus.BOOKED), "BOOKED seats");
            assertConsistent(show, bookings);
        });

        t.scenario("499 other users try to unlock or steal a held hold",
                "only the holder can release or confirm; other users' unlockSeats calls are ignored", () -> {
            Show show = newShow(50);
            List<Seat> held = show.getScreen().getSeats().subList(0, 10);
            Customer holder = users.get(0);
            SeatLockManager locks = new SeatLockManager();
            AtomicInteger stolen = new AtomicInteger();
            CountDownLatch attackersDone = new CountDownLatch(THREADS - 1);
            boolean[] confirmed = new boolean[1];
            boolean[] lockedFirst = new boolean[1];

            try {
                quietly(() -> {
                    // The hold is taken only once all 500 threads are parked, so its 500 ms clock
                    // starts at the race, not while the threads are being created.
                    race(THREADS, () -> lockedFirst[0] = locks.lockSeats(show, held, holder.getId()), i -> {
                        if (i == 0) {                       // the holder confirms once every attacker is done
                            attackersDone.await();
                            confirmed[0] = locks.confirmSeats(show, held, holder.getId());
                            return;
                        }
                        String attacker = users.get(1 + i % (USERS - 1)).getId();
                        try {
                            switch (i % 3) {
                                case 0 -> locks.unlockSeats(show, held, attacker);
                                case 1 -> { if (locks.lockSeats(show, held, attacker)) stolen.incrementAndGet(); }
                                default -> { if (locks.confirmSeats(show, held, attacker)) stolen.incrementAndGet(); }
                            }
                        } finally {
                            attackersDone.countDown();
                        }
                    });
                });
            } finally {
                quietly(locks::shutdown);
            }

            checkEquals(true, lockedFirst[0], "holder locked 10 seats");
            checkEquals(0, stolen.get(), "locks or confirms won by attackers");
            checkEquals(true, confirmed[0], "holder's confirm succeeded");
            checkEquals(10L, held.stream().filter(s -> s.getStatus() == SeatStatus.BOOKED).count(), "held seats BOOKED");
        });

        t.scenario("Payment finishing right around the hold expiry (450–550 ms)",
                "at the expiry boundary each attempt ends as exactly one of: booked, or refunded with the seat free", () -> {
            Show show = newShow(500);
            List<Seat> seats = show.getScreen().getSeats();
            RecordingPayment[] pays = new RecordingPayment[THREADS];
            for (int i = 0; i < THREADS; i++) {
                pays[i] = RecordingPayment.slow(LOCK_TIMEOUT_MS - 50 + i % 101);
            }
            Booking[] result = new Booking[THREADS];

            withBookingManager(bm -> race(THREADS, i ->
                    bm.createBooking(users.get(i % USERS), show, List.of(seats.get(i)), pays[i])
                            .ifPresent(b -> result[i] = b)));
            sleep(LOCK_TIMEOUT_MS);                          // let any remaining expiry tasks fire

            int booked = 0, refunded = 0, wrong = 0;
            for (int i = 0; i < THREADS; i++) {
                boolean hasBooking = result[i] != null;
                boolean wasRefunded = pays[i].refunds.get() == 1;
                SeatStatus status = seats.get(i).getStatus();
                if (hasBooking && !wasRefunded && status == SeatStatus.BOOKED) booked++;
                else if (!hasBooking && wasRefunded && status == SeatStatus.AVAILABLE) refunded++;
                else wrong++;
            }
            note("booked in time: " + booked + ", refunded after expiry: " + refunded);
            checkEquals(0, wrong, "attempts in an inconsistent state");
            checkEquals(THREADS, booked + refunded, "attempts accounted for");
            assertConsistent(show, Arrays.stream(result).filter(Objects::nonNull).toList());
        });

        t.scenario("500 threads spread over 10 shows sharing one SeatLockManager",
                "per-show locking keeps every show consistent independently", () -> {
            List<Show> shows = new ArrayList<>();
            for (int s = 0; s < 10; s++) shows.add(newShow(50));
            RecordingPayment pay = RecordingPayment.instant();
            Queue<Booking> bookings = new ConcurrentLinkedQueue<>();

            withBookingManager(bm -> race(THREADS, i -> {
                Show show = shows.get(i % shows.size());
                List<Seat> want = randomSeats(show, 1 + ThreadLocalRandom.current().nextInt(3));
                bm.createBooking(users.get(i % USERS), show, want, pay).ifPresent(bookings::add);
            }));

            note("bookings across 10 shows: " + bookings.size());
            for (Show show : shows) {
                quietCheckConsistent(show, bookings);
            }
            check(true, "all 10 shows consistent (no double booking, nothing LOCKED)");
        });

        t.scenario("MovieBookingService.getInstance() from 500 threads",
                "double-checked locking hands every thread the same instance", () -> {
            Set<MovieBookingService> seen = ConcurrentHashMap.newKeySet();
            race(THREADS, i -> seen.add(MovieBookingService.getInstance()));
            checkEquals(1, seen.size(), "distinct instances");
        });

        t.scenario("End to end through MovieBookingService.bookTickets: 200 registered users, 500 threads",
                "the public facade gives the same guarantees as BookingManager", () -> {
            MovieBookingService service = MovieBookingService.getInstance();
            RecordingPayment pay = RecordingPayment.instant();
            Queue<Booking> bookings = new ConcurrentLinkedQueue<>();
            Show[] showRef = new Show[1];
            List<Customer> registered = new ArrayList<>();

            quietly(() -> {
                City city = service.addCity("Concurrency City");
                Screen screen = newShow(80).getScreen();
                service.addCinema("CIN-CONC", "Race Cinema", city.getId(), List.of(screen));
                Movie movie = new Movie("MOV-CONC", "Race Condition", 110);
                service.addMovie(movie);
                showRef[0] = service.addShow("SHOW-CONC", movie, screen, LocalDateTime.now().plusHours(2),
                        PRICES, new WeekdayPricingStrategy());
                for (int u = 0; u < USERS; u++) registered.add(service.createUser("Reg-" + u, "reg" + u + "@test.com"));

                race(THREADS, i -> service.bookTickets(registered.get(i % USERS).getId(), "SHOW-CONC",
                        randomSeats(showRef[0], 1 + ThreadLocalRandom.current().nextInt(4)), pay)
                        .ifPresent(bookings::add));
            });

            checkEquals(1, service.findShows("race condition", "concurrency city").size(), "show is searchable");
            note("bookings through the facade: " + bookings.size());
            checkEquals(bookings.size(), pay.charges.get(), "charges = bookings");
            assertConsistent(showRef[0], bookings);
            quietly(service::shutdown);
        });

        t.finish();
    }

    // --- helpers ---

    @FunctionalInterface
    private interface BookingBody {
        void run(BookingManager bm) throws Exception;
    }

    /** A fresh SeatLockManager + BookingManager per scenario, output silenced, scheduler always shut down. */
    private static void withBookingManager(BookingBody body) throws Exception {
        SeatLockManager locks = new SeatLockManager();
        try {
            quietly(() -> body.run(new BookingManager(locks)));
        } finally {
            quietly(locks::shutdown);
        }
    }

    private static int seatIndex(List<Seat> seats, Booking b) {
        return seats.indexOf(b.getSeats().get(0));
    }

    private static void quietCheckConsistent(Show show, Collection<Booking> bookings) throws Exception {
        quietly(() -> assertConsistent(show, bookings));
    }
}
