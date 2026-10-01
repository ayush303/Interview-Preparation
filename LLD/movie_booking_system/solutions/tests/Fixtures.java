package LLD.movie_booking_system.solutions.tests;

import LLD.movie_booking_system.solutions.enums.PaymentStatus;
import LLD.movie_booking_system.solutions.enums.SeatStatus;
import LLD.movie_booking_system.solutions.enums.SeatType;
import LLD.movie_booking_system.solutions.models.*;
import LLD.movie_booking_system.solutions.strategy.payment.PaymentStrategy;
import LLD.movie_booking_system.solutions.strategy.pricing.WeekdayPricingStrategy;

import java.io.OutputStream;
import java.io.PrintStream;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static LLD.movie_booking_system.solutions.tests.TestHarness.*;

/**
 * Test data, deterministic payment strategies, the race runner and the
 * system-wide invariant check shared by every concurrency scenario.
 */
final class Fixtures {

    static final int THREADS = 500;
    static final int USERS = 200;

    static final Map<SeatType, Double> PRICES = Map.of(
            SeatType.REGULAR, 50.0,
            SeatType.PREMIUM, 80.0,
            SeatType.RECLINER, 120.0);

    private static final AtomicInteger ids = new AtomicInteger();

    private Fixtures() {
    }

    // --- test data ---

    /** A show on its own screen with {@code seatCount} seats, 10 per row, mixed seat types. */
    static Show newShow(int seatCount) {
        Screen screen = new Screen("SCR-" + ids.incrementAndGet());
        for (int i = 0; i < seatCount; i++) {
            SeatType type = SeatType.values()[i % SeatType.values().length];
            screen.addSeat(new Seat("S" + i, i / 10 + 1, i % 10 + 1, type));
        }
        Movie movie = new Movie("MOV-" + ids.incrementAndGet(), "Test Movie", 120);
        return new Show("SHOW-" + ids.incrementAndGet(), movie, screen,
                LocalDateTime.now().plusHours(3), PRICES, new WeekdayPricingStrategy());
    }

    static List<Customer> users(int n) {
        List<Customer> users = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            users.add(new Customer("User-" + i, "user" + i + "@test.com"));
        }
        return users;
    }

    /** {@code k} distinct seats picked at random from the show's screen. */
    static List<Seat> randomSeats(Show show, int k) {
        List<Seat> all = new ArrayList<>(show.getScreen().getSeats());
        Collections.shuffle(all, ThreadLocalRandom.current());
        return new ArrayList<>(all.subList(0, k));
    }

    static double expectedPrice(List<Seat> seats) {
        return seats.stream().mapToDouble(s -> PRICES.get(s.getType())).sum();
    }

    // --- payment strategies (the real CreditCardPaymentStrategy fails 5% at random) ---

    /** Records every charge and refund; pays successfully after an optional delay. */
    static class RecordingPayment implements PaymentStrategy {
        final AtomicInteger charges = new AtomicInteger();
        final AtomicInteger refunds = new AtomicInteger();
        private final long delayMs;
        private final boolean succeed;

        RecordingPayment(long delayMs, boolean succeed) {
            this.delayMs = delayMs;
            this.succeed = succeed;
        }

        static RecordingPayment instant() {
            return new RecordingPayment(0, true);
        }

        static RecordingPayment declining() {
            return new RecordingPayment(0, false);
        }

        static RecordingPayment slow(long delayMs) {
            return new RecordingPayment(delayMs, true);
        }

        @Override
        public Payment pay(double amount) {
            charges.incrementAndGet();
            if (delayMs > 0) {
                sleep(delayMs);
            }
            return new Payment(amount, "TXN-" + UUID.randomUUID(),
                    succeed ? PaymentStatus.SUCCESS : PaymentStatus.FAILURE);
        }

        @Override
        public void refund(Payment payment) {
            refunds.incrementAndGet();
        }
    }

    // --- race runner ---

    @FunctionalInterface
    interface Task {
        void run(int index) throws Exception;
    }

    /**
     * Runs {@code n} tasks on {@code n} dedicated threads. Every thread parks on a
     * start latch and they are released together, so they genuinely collide.
     */
    static void race(int n, Task task) throws Exception {
        race(n, () -> { }, task);
    }

    /**
     * As {@link #race(int, Task)}, but runs {@code beforeGo} after every thread is
     * parked and just before they are released. Used when setup starts a clock
     * (a seat hold) that must not tick while 500 threads are being created.
     */
    static void race(int n, TestHarness.ThrowingRunnable beforeGo, Task task) throws Exception {
        CountDownLatch ready = new CountDownLatch(n);
        CountDownLatch go = new CountDownLatch(1);
        ExecutorService racers = Executors.newFixedThreadPool(n);
        List<Future<?>> futures = new ArrayList<>();
        try {
            for (int i = 0; i < n; i++) {
                int index = i;
                futures.add(racers.submit(() -> {
                    ready.countDown();
                    go.await();
                    task.run(index);
                    return null;
                }));
            }
            if (!ready.await(30, TimeUnit.SECONDS)) {
                throw new AssertionError("racers did not all start");
            }
            beforeGo.run();
            go.countDown();
            for (Future<?> f : futures) {
                try {
                    f.get(60, TimeUnit.SECONDS);
                } catch (ExecutionException e) {
                    Throwable cause = e.getCause();
                    if (cause instanceof Exception ex) throw ex;
                    if (cause instanceof Error err) throw err;
                    throw e;
                }
            }
        } finally {
            racers.shutdownNow();
        }
    }

    /** The production classes log every lock and payment; 500 threads of that drowns the report. */
    static void quietly(TestHarness.ThrowingRunnable body) throws Exception {
        PrintStream real = System.out;
        System.setOut(new PrintStream(OutputStream.nullOutputStream()));
        try {
            body.run();
        } finally {
            System.setOut(real);
        }
    }

    static void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException(e);
        }
    }

    // --- invariants ---

    /**
     * The no-double-booking contract, checked over the whole show:
     * no seat is in two bookings, every booked seat is BOOKED and vice versa,
     * no seat is left LOCKED, and every booking was charged the right amount.
     */
    static void assertConsistent(Show show, Collection<Booking> bookings) {
        Map<Seat, Booking> owner = new IdentityHashMap<>();
        for (Booking b : bookings) {
            if (b.getShow() != show) continue;
            for (Seat s : b.getSeats()) {
                Booking prev = owner.put(s, b);
                if (prev != null) {
                    throw new AssertionError("DOUBLE BOOKING: seat " + s.getId()
                            + " sold to " + prev.getUser().getName() + " and " + b.getUser().getName());
                }
            }
            if (Math.abs(b.getTotalAmount() - expectedPrice(b.getSeats())) > 1e-9) {
                throw new AssertionError("booking " + b.getId() + " charged " + b.getTotalAmount());
            }
            if (b.getPayment().getStatus() != PaymentStatus.SUCCESS) {
                throw new AssertionError("booking " + b.getId() + " exists without a successful payment");
            }
        }
        long locked = 0, bookedWithoutOwner = 0, ownedNotBooked = 0;
        for (Seat s : show.getScreen().getSeats()) {
            if (s.getStatus() == SeatStatus.LOCKED) locked++;
            if (s.getStatus() == SeatStatus.BOOKED && !owner.containsKey(s)) bookedWithoutOwner++;
            if (owner.containsKey(s) && s.getStatus() != SeatStatus.BOOKED) ownedNotBooked++;
        }
        check(true, "no seat sold twice (" + owner.size() + " seats across "
                + bookings.stream().filter(b -> b.getShow() == show).count() + " bookings)");
        checkEquals(0L, locked, "seats left LOCKED");
        checkEquals(0L, bookedWithoutOwner, "BOOKED seats with no booking");
        checkEquals(0L, ownedNotBooked, "booked seats not marked BOOKED");
    }

    static long countStatus(Show show, SeatStatus status) {
        return show.getScreen().getSeats().stream().filter(s -> s.getStatus() == status).count();
    }
}
