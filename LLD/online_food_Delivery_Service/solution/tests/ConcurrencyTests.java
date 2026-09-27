package LLD.online_food_Delivery_Service.solution.tests;

import LLD.online_food_Delivery_Service.solution.FoodDeliveryService;
import LLD.online_food_Delivery_Service.solution.enums.OrderStatus;
import LLD.online_food_Delivery_Service.solution.models.*;
import LLD.online_food_Delivery_Service.solution.observer.Observer;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static LLD.online_food_Delivery_Service.solution.tests.Fixtures.*;
import static LLD.online_food_Delivery_Service.solution.tests.TestHarness.*;

/**
 * Concurrency tests against the real FoodDeliveryService singleton.
 *
 * A passing concurrency test cannot prove the absence of races, only fail to
 * find one. So every scenario parks its threads on a start latch and releases
 * them together, making them collide instead of trickling through one by one.
 * Each scenario then checks invariants over the whole system (agents, stock,
 * statuses, notification counts), not just how many calls returned normally.
 *
 * Run from the repository root:
 *   javac -d out $(find LLD/online_food_Delivery_Service/solution -name '*.java')
 *   java  -cp out LLD.online_food_Delivery_Service.solution.tests.ConcurrencyTests
 */
public class ConcurrencyTests {

    private static final FoodDeliveryService service = service();
    private static final ExecutorService pool = Executors.newFixedThreadPool(64);

    public static void main(String[] args) {
        TestHarness t = new TestHarness("Food Delivery — Concurrency Tests",
                "Threads are released together from a latch so they genuinely race. Nothing is mocked.");

        t.scenario("50 orders ready at once, 1 free agent",
                "the agent is claimed by exactly one order; the rest queue and are served one by one", () -> {
            EndToEndFlowTests.Setup s = new EndToEndFlowTests.Setup("OneAgent", 1_000);
            DeliveryAgent solo = service.registerDeliveryAgent("Solo", "1", address("OneAgent", 0, 0));
            service.setAssignmentStrategy(onlyAgents(solo));
            int queuedBefore = service.getPendingAssignmentCount();
            List<Order> orders = placeMany(s, 50);
            quietly(() -> forEach(orders, o -> {
                service.updateOrderStatus(o.getId(), OrderStatus.CONFIRMED);
                service.updateOrderStatus(o.getId(), OrderStatus.PREPARING);
            }));

            quietly(() -> race(orders.size(), i ->
                    service.updateOrderStatus(orders.get(i).getId(), OrderStatus.READY_FOR_PICKUP)));

            checkEquals(1L, count(orders, OrderStatus.OUT_FOR_DELIVERY), "orders out for delivery");
            checkEquals(49L, count(orders, OrderStatus.READY_FOR_PICKUP), "orders waiting");
            checkEquals(queuedBefore + 49, service.getPendingAssignmentCount(), "queue length");

            // Deliver whatever Solo is carrying; each delivery must hand the next order to Solo.
            quietly(() -> {
                for (int round = 0; round < orders.size(); round++) {
                    Order carrying = orders.stream()
                            .filter(o -> o.getOrderStatus() == OrderStatus.OUT_FOR_DELIVERY)
                            .reduce((a, b) -> { throw new AssertionError("Solo is carrying two orders"); })
                            .orElseThrow(() -> new AssertionError("nobody is out for delivery"));
                    service.updateOrderStatus(carrying.getId(), OrderStatus.DELIVERED);
                }
            });
            checkEquals(50L, count(orders, OrderStatus.DELIVERED), "orders delivered");
            check(orders.stream().allMatch(o -> o.getDeliveryAgent() == solo), "all carried by Solo");
            checkEquals(queuedBefore, service.getPendingAssignmentCount(), "queue drained");
            check(solo.isAvailable(), "Solo free at the end");
        });

        t.scenario("200 orders ready at once, 20 agents; then deliveries in parallel",
                "no agent ever carries two orders; every order is eventually delivered", () -> {
            EndToEndFlowTests.Setup s = new EndToEndFlowTests.Setup("Fleet", 1_000);
            DeliveryAgent[] fleet = new DeliveryAgent[20];
            for (int i = 0; i < fleet.length; i++) {
                fleet[i] = service.registerDeliveryAgent("Fleet-" + i, "0", address("Fleet", i * 0.001, 0));
            }
            service.setAssignmentStrategy(onlyAgents(fleet));
            int queuedBefore = service.getPendingAssignmentCount();
            List<Order> orders = placeMany(s, 200);
            quietly(() -> forEach(orders, o -> {
                service.updateOrderStatus(o.getId(), OrderStatus.CONFIRMED);
                service.updateOrderStatus(o.getId(), OrderStatus.PREPARING);
            }));

            quietly(() -> race(orders.size(), i ->
                    service.updateOrderStatus(orders.get(i).getId(), OrderStatus.READY_FOR_PICKUP)));

            checkEquals(20L, count(orders, OrderStatus.OUT_FOR_DELIVERY), "orders out for delivery");
            assertNoAgentCarriesTwo(orders);

            int[] rounds = {0};
            quietly(() -> {
                while (count(orders, OrderStatus.DELIVERED) < orders.size()) {
                    List<Order> out = orders.stream()
                            .filter(o -> o.getOrderStatus() == OrderStatus.OUT_FOR_DELIVERY).toList();
                    check(!out.isEmpty(), "work is stuck: nothing out for delivery");
                    assertNoAgentCarriesTwo(orders);
                    race(out.size(), i -> service.updateOrderStatus(out.get(i).getId(), OrderStatus.DELIVERED));
                    rounds[0]++;
                }
            });
            note("delivered in " + rounds[0] + " parallel rounds");
            checkEquals(200L, count(orders, OrderStatus.DELIVERED), "orders delivered");
            checkEquals(queuedBefore, service.getPendingAssignmentCount(), "queue drained");
            check(Arrays.stream(fleet).allMatch(DeliveryAgent::isAvailable), "whole fleet free");
        });

        t.scenario("100 customers race for the last portion",
                "stock reservation is atomic: exactly one order gets it (gap 6)", () -> {
            EndToEndFlowTests.Setup s = new EndToEndFlowTests.Setup("LastSlice", 1);
            AtomicInteger won = new AtomicInteger();
            AtomicInteger soldOut = new AtomicInteger();
            quietly(() -> race(100, i -> {
                try {
                    service.placeOrder(s.customer.getId(), s.restaurant.getId(), List.of(new OrderItem(s.dish, 1)));
                    won.incrementAndGet();
                } catch (IllegalStateException e) {
                    soldOut.incrementAndGet();
                }
            }));
            checkEquals(1, won.get(), "orders accepted");
            checkEquals(99, soldOut.get(), "orders refused");
            checkEquals(0, s.dish.getStock(), "stock");
        });

        t.scenario("200 customers race for 25 portions",
                "never oversold, never undersold", () -> {
            EndToEndFlowTests.Setup s = new EndToEndFlowTests.Setup("Limited", 25);
            AtomicInteger won = new AtomicInteger();
            quietly(() -> race(200, i -> {
                try {
                    service.placeOrder(s.customer.getId(), s.restaurant.getId(), List.of(new OrderItem(s.dish, 1)));
                    won.incrementAndGet();
                } catch (IllegalStateException ignored) {
                }
            }));
            checkEquals(25, won.get(), "orders accepted");
            checkEquals(0, s.dish.getStock(), "stock");
            checkEquals(25, s.customer.getOrderHistory().size(), "orders in history");
        });

        t.scenario("Restaurant starts cooking while the customer cancels, 300 times",
                "check-and-set is atomic: exactly one side wins each race and the result is consistent (gap 4)", () -> {
            EndToEndFlowTests.Setup s = new EndToEndFlowTests.Setup("CookVsCancel", 300);
            List<Order> orders = placeMany(s, 300);
            quietly(() -> forEach(orders, o -> service.updateOrderStatus(o.getId(), OrderStatus.CONFIRMED)));

            int[] cooked = new int[orders.size()];
            int[] cancelled = new int[orders.size()];
            quietly(() -> race(orders.size() * 2, i -> {
                Order o = orders.get(i / 2);
                // Alternate which side is submitted first, so neither always gets a head start.
                boolean kitchen = (i + i / 2) % 2 == 0;
                try {
                    if (kitchen) {
                        service.updateOrderStatus(o.getId(), OrderStatus.PREPARING);
                        cooked[i / 2]++;
                    } else {
                        service.cancel(o.getId());
                        cancelled[i / 2]++;
                    }
                } catch (IllegalStateException lost) {
                    // the other side won this order
                }
            }));

            int preparing = 0, cancelledCount = 0;
            for (int i = 0; i < orders.size(); i++) {
                check(cooked[i] + cancelled[i] == 1, "order " + i + " had " + (cooked[i] + cancelled[i]) + " winners");
                OrderStatus expected = cooked[i] == 1 ? OrderStatus.PREPARING : OrderStatus.CANCELLED;
                check(orders.get(i).getOrderStatus() == expected, "order " + i + " status disagrees with its winner");
                if (cooked[i] == 1) preparing++; else cancelledCount++;
            }
            note("kitchen won " + preparing + ", customer won " + cancelledCount);
            checkEquals(300 - preparing, s.dish.getStock(), "stock = 300 - orders still cooking");
        });

        t.scenario("100 threads confirm the same order",
                "exactly one transition succeeds and observers hear about it exactly once", () -> {
            EndToEndFlowTests.Setup s = new EndToEndFlowTests.Setup("DoubleConfirm", 5);
            Order order = EndToEndFlowTests.placeQuietly(s.customer, s.restaurant, new OrderItem(s.dish, 1));
            RecordingObserver rec = new RecordingObserver();
            order.addObserver(rec);
            AtomicInteger ok = new AtomicInteger();
            quietly(() -> race(100, i -> {
                try {
                    service.updateOrderStatus(order.getId(), OrderStatus.CONFIRMED);
                    ok.incrementAndGet();
                } catch (IllegalStateException ignored) {
                }
            }));
            checkEquals(1, ok.get(), "successful confirmations");
            checkEquals(List.of(OrderStatus.CONFIRMED), rec.seen, "notifications");
        });

        t.scenario("A slow observer does not hold the order's lock",
                "notification happens outside the lock, so other transitions are not blocked (gap 8)", () -> {
            EndToEndFlowTests.Setup s = new EndToEndFlowTests.Setup("SlowObserver", 5);
            Order order = EndToEndFlowTests.placeQuietly(s.customer, s.restaurant, new OrderItem(s.dish, 1));
            CountDownLatch insideSlowObserver = new CountDownLatch(1);
            order.addObserver(new Observer() {
                @Override
                public void onUpdate(Order o, OrderStatus status) {
                    if (status == OrderStatus.CONFIRMED) {
                        insideSlowObserver.countDown();
                        sleep(1_000);   // e.g. a slow SMS gateway
                    }
                }
            });
            PrintStreamGuard.silence();
            try {
                Future<?> confirming = pool.submit(() -> {
                    service.updateOrderStatus(order.getId(), OrderStatus.CONFIRMED);
                    return null;
                });
                check(insideSlowObserver.await(5, TimeUnit.SECONDS), "slow observer never ran");
                long start = System.nanoTime();
                service.updateOrderStatus(order.getId(), OrderStatus.PREPARING);
                long tookMs = (System.nanoTime() - start) / 1_000_000;
                confirming.get(5, TimeUnit.SECONDS);
                PrintStreamGuard.restore();
                note("PREPARING applied in " + tookMs + " ms while the slow observer was still sleeping");
                check(tookMs < 500, "second transition waited " + tookMs + " ms for the slow observer");
            } finally {
                PrintStreamGuard.restore();
            }
            checkEquals(OrderStatus.PREPARING, order.getOrderStatus(), "final status");
        });

        t.scenario("16 threads place 50 orders each for one customer",
                "order history loses no updates under concurrent writes (gap 10)", () -> {
            EndToEndFlowTests.Setup s = new EndToEndFlowTests.Setup("History", 10_000);
            quietly(() -> race(16, i -> {
                for (int k = 0; k < 50; k++) {
                    service.placeOrder(s.customer.getId(), s.restaurant.getId(), List.of(new OrderItem(s.dish, 1)));
                }
            }));
            checkEquals(800, s.customer.getOrderHistory().size(), "orders in history");
            checkEquals(800, service.getOrdersForCustomer(s.customer.getId()).size(), "orders via the service");
            checkEquals(10_000 - 800, s.dish.getStock(), "stock");
        });

        t.scenario("Observers subscribe while notifications are being broadcast",
                "no ConcurrentModificationException and no lost transitions", () -> {
            EndToEndFlowTests.Setup s = new EndToEndFlowTests.Setup("Subscribe", 500);
            List<Order> orders = placeMany(s, 200);
            quietly(() -> race(orders.size() * 2, i -> {
                Order o = orders.get(i / 2);
                if (i % 2 == 0) {
                    service.updateOrderStatus(o.getId(), OrderStatus.CONFIRMED);
                } else {
                    for (int k = 0; k < 20; k++) o.addObserver(new RecordingObserver());
                }
            }));
            checkEquals(200L, count(orders, OrderStatus.CONFIRMED), "orders confirmed");
            check(orders.stream().allMatch(o -> o.getObservers().size() == 22),
                    "every order has customer + restaurant + 20 new observers");
        });

        t.scenario("100 threads call getInstance at once",
                "every caller sees the same singleton", () -> {
            Set<FoodDeliveryService> seen = ConcurrentHashMap.newKeySet();
            race(100, i -> seen.add(FoodDeliveryService.getInstance()));
            checkEquals(1, seen.size(), "distinct instances");
        });

        pool.shutdownNow();
        t.finish();
    }

    // --- helpers ---

    @FunctionalInterface
    interface Task {
        void run(int index) throws Exception;
    }

    /** Starts n tasks, releases them together from a latch, waits for all, and rethrows the first failure. */
    static void race(int n, Task task) throws Exception {
        CountDownLatch ready = new CountDownLatch(n);
        CountDownLatch go = new CountDownLatch(1);
        List<Future<?>> futures = new ArrayList<>();
        ExecutorService racers = Executors.newFixedThreadPool(Math.min(n, 64));
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
            // With a bounded pool not every task can be parked at once; wait briefly, then fire.
            ready.await(Math.min(n, 64) >= n ? 10 : 0, TimeUnit.SECONDS);
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

    static void forEach(List<Order> orders, ThrowingConsumer<Order> body) throws Exception {
        for (Order o : orders) body.accept(o);
    }

    @FunctionalInterface
    interface ThrowingConsumer<T> {
        void accept(T t) throws Exception;
    }

    static List<Order> placeMany(EndToEndFlowTests.Setup s, int n) throws Exception {
        List<Order> orders = new ArrayList<>();
        quietly(() -> {
            for (int i = 0; i < n; i++) {
                orders.add(service.placeOrder(s.customer.getId(), s.restaurant.getId(),
                        List.of(new OrderItem(s.dish, 1))));
            }
        });
        return orders;
    }

    static long count(List<Order> orders, OrderStatus status) {
        return orders.stream().filter(o -> o.getOrderStatus() == status).count();
    }

    static void assertNoAgentCarriesTwo(List<Order> orders) {
        Map<DeliveryAgent, Long> perAgent = new IdentityHashMap<>();
        for (Order o : orders) {
            if (o.getOrderStatus() == OrderStatus.OUT_FOR_DELIVERY) {
                perAgent.merge(o.getDeliveryAgent(), 1L, Long::sum);
            }
        }
        perAgent.forEach((agent, n) ->
                check(n == 1, agent.getName() + " is carrying " + n + " orders at once"));
    }

    static void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /** Silences System.out across threads for the one scenario that cannot use quietly(). */
    static final class PrintStreamGuard {
        private static java.io.PrintStream saved;

        static synchronized void silence() {
            if (saved == null) {
                saved = System.out;
                System.setOut(new java.io.PrintStream(java.io.OutputStream.nullOutputStream()));
            }
        }

        static synchronized void restore() {
            if (saved != null) {
                System.setOut(saved);
                saved = null;
            }
        }
    }
}
