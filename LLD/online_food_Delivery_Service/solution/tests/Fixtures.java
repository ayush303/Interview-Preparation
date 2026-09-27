package LLD.online_food_Delivery_Service.solution.tests;

import LLD.online_food_Delivery_Service.solution.FoodDeliveryService;
import LLD.online_food_Delivery_Service.solution.enums.OrderStatus;
import LLD.online_food_Delivery_Service.solution.models.*;
import LLD.online_food_Delivery_Service.solution.observer.Observer;
import LLD.online_food_Delivery_Service.solution.strategies.assignment.DeliveryAssignmentStrategy;
import LLD.online_food_Delivery_Service.solution.strategies.assignment.NearestAvailableAgentStrategy;

import java.io.OutputStream;
import java.io.PrintStream;
import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Shared building blocks for the service-level suites.
 *
 * FoodDeliveryService is a singleton with no reset, so every scenario registers
 * its own customers, restaurants and agents, and calls {@link #onlyAgents} so the
 * dispatcher can only pick that scenario's agents. Agents left over from earlier
 * scenarios are invisible to it, which keeps scenarios independent.
 */
public final class Fixtures {

    private static final AtomicInteger seq = new AtomicInteger();

    private Fixtures() {}

    public static FoodDeliveryService service() {
        return FoodDeliveryService.getInstance();
    }

    public static Address address(String city, double lat, double lng) {
        return new Address(seq.incrementAndGet() + " Test St", city, "00000", lat, lng);
    }

    public static MenuItem item(String name, String price, int stock) {
        return new MenuItem("I" + seq.incrementAndGet(), name, new BigDecimal(price), stock);
    }

    /** Restricts dispatch to the given agents, choosing the nearest of them. */
    public static DeliveryAssignmentStrategy onlyAgents(DeliveryAgent... agents) {
        Set<DeliveryAgent> allowed = Collections.newSetFromMap(new IdentityHashMap<>());
        allowed.addAll(Arrays.asList(agents));
        NearestAvailableAgentStrategy nearest = new NearestAvailableAgentStrategy();
        return (order, candidates) -> nearest.findAgent(order,
                candidates.stream().filter(allowed::contains).toList());
    }

    /** Runs {@code body} with System.out discarded, for scenarios that fire thousands of notifications. */
    public static void quietly(TestHarness.ThrowingRunnable body) throws Exception {
        PrintStream original = System.out;
        System.setOut(new PrintStream(OutputStream.nullOutputStream()));
        try {
            body.run();
        } finally {
            System.setOut(original);
        }
    }

    /** An observer that records every status it is told about, in order. */
    public static final class RecordingObserver implements Observer {
        public final List<OrderStatus> seen = new CopyOnWriteArrayList<>();

        @Override
        public void onUpdate(Order order, OrderStatus status) {
            seen.add(status);
        }
    }

    /** An observer that always throws, to prove one bad listener cannot break the others. */
    public static final class ExplodingObserver implements Observer {
        @Override
        public void onUpdate(Order order, OrderStatus status) {
            throw new RuntimeException("SMS gateway down");
        }
    }
}
