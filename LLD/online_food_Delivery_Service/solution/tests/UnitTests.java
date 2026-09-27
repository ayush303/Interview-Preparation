package LLD.online_food_Delivery_Service.solution.tests;

import LLD.online_food_Delivery_Service.solution.enums.OrderStatus;
import LLD.online_food_Delivery_Service.solution.models.*;
import LLD.online_food_Delivery_Service.solution.strategies.assignment.NearestAvailableAgentStrategy;
import LLD.online_food_Delivery_Service.solution.strategies.search.SearchByCityStrategy;
import LLD.online_food_Delivery_Service.solution.strategies.search.SearchByMenuKeywordStrategy;
import LLD.online_food_Delivery_Service.solution.strategies.search.SearchByProximityStrategy;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static LLD.online_food_Delivery_Service.solution.tests.Fixtures.*;
import static LLD.online_food_Delivery_Service.solution.tests.TestHarness.*;

/**
 * Unit tests: each class on its own, constructed directly, no singleton involved.
 *
 * Run from the repository root:
 *   javac -d out $(find LLD/online_food_Delivery_Service/solution -name '*.java')
 *   java  -cp out LLD.online_food_Delivery_Service.solution.tests.UnitTests
 */
public class UnitTests {

    public static void main(String[] args) {
        TestHarness t = new TestHarness("Food Delivery — Unit Tests",
                "Models, the transition table and the strategies, each tested in isolation.");

        // ---------------- OrderStatus ----------------

        t.scenario("OrderStatus is declared in lifecycle order",
                "ordinal() and compareTo follow the lifecycle (gap 12)", () -> {
            checkEquals(List.of(OrderStatus.PENDING, OrderStatus.CONFIRMED, OrderStatus.PREPARING,
                            OrderStatus.READY_FOR_PICKUP, OrderStatus.OUT_FOR_DELIVERY,
                            OrderStatus.DELIVERED, OrderStatus.CANCELLED),
                    List.of(OrderStatus.values()), "declaration order");
        });

        t.scenario("Transition table allows exactly the 7 documented moves",
                "all 49 from→to pairs are checked, not just the happy path", () -> {
            Set<String> expected = Set.of(
                    "PENDING->CONFIRMED", "PENDING->CANCELLED",
                    "CONFIRMED->PREPARING", "CONFIRMED->CANCELLED",
                    "PREPARING->READY_FOR_PICKUP",
                    "READY_FOR_PICKUP->OUT_FOR_DELIVERY",
                    "OUT_FOR_DELIVERY->DELIVERED");
            int legal = 0;
            for (OrderStatus from : OrderStatus.values()) {
                for (OrderStatus to : OrderStatus.values()) {
                    boolean allowed = from.canTransitionTo(to);
                    check(allowed == expected.contains(from + "->" + to),
                            from + " -> " + to + " should be " + (allowed ? "rejected" : "allowed"));
                    if (allowed) legal++;
                }
            }
            checkEquals(7, legal, "legal transitions out of 49");
        });

        t.scenario("Only DELIVERED and CANCELLED are terminal",
                "isTerminal() matches the table", () -> {
            Set<OrderStatus> terminal = EnumSet.noneOf(OrderStatus.class);
            for (OrderStatus s : OrderStatus.values()) if (s.isTerminal()) terminal.add(s);
            checkEquals(EnumSet.of(OrderStatus.DELIVERED, OrderStatus.CANCELLED), terminal, "terminal states");
        });

        // ---------------- Address ----------------

        t.scenario("Address.distanceTo is Haversine, in kilometres",
                "distances are real-world km, not degrees (gap 13)", () -> {
            Address equator0 = new Address("a", "c", "z", 0, 0);
            Address equator1 = new Address("b", "c", "z", 0, 1);
            Address north0 = new Address("c", "c", "z", 60, 0);
            Address north1 = new Address("d", "c", "z", 60, 1);
            double atEquator = equator0.distanceTo(equator1);
            double at60 = north0.distanceTo(north1);
            note(String.format("1° of longitude at the equator = %.1f km, at 60°N = %.1f km", atEquator, at60));
            check(Math.abs(atEquator - 111.19) < 0.1, "1° at the equator should be ~111.19 km");
            check(Math.abs(at60 - atEquator / 2) < 0.2, "1° at 60°N should be about half of that");
            checkEquals(0.0, equator0.distanceTo(equator0), "distance to self");
        });

        // ---------------- MenuItem ----------------

        t.scenario("MenuItem.reserve takes stock and refuses when short",
                "stock is tracked and reserve is all-or-nothing per call (gap 6)", () -> {
            MenuItem pizza = item("Pizza", "10.00", 3);
            check(pizza.reserve(2), "reserve(2) of 3 should succeed");
            checkEquals(1, pizza.getStock(), "stock after reserving 2");
            check(!pizza.reserve(2), "reserve(2) of 1 should fail");
            checkEquals(1, pizza.getStock(), "stock unchanged by a failed reserve");
            check(pizza.reserve(1), "reserve(1) of 1 should succeed");
            check(!pizza.isAvailable(), "sold out item should not be available");
            pizza.release(1);
            check(pizza.isAvailable(), "released stock makes it available again");
        });

        t.scenario("MenuItem manual toggle overrides stock",
                "setAvailable(false) blocks orders even with stock left", () -> {
            MenuItem soup = item("Soup", "5.00", 10);
            soup.setAvailable(false);
            check(!soup.isAvailable(), "switched-off item is unavailable");
            check(!soup.reserve(1), "switched-off item cannot be reserved");
            checkEquals(10, soup.getStock(), "stock untouched");
        });

        t.scenario("MenuItem rejects negative price, negative stock and bad quantities",
                "invalid values fail fast at the boundary", () -> {
            checkThrows(IllegalArgumentException.class,
                    () -> new MenuItem("X", "Bad", new BigDecimal("-1"), 1), "negative price");
            checkThrows(IllegalArgumentException.class,
                    () -> new MenuItem("X", "Bad", BigDecimal.ONE, -1), "negative stock");
            MenuItem ok = item("Ok", "1.00", 1);
            check(!ok.reserve(0), "reserve(0) is refused");
            checkThrows(IllegalArgumentException.class, () -> ok.release(0), "release(0)");
        });

        // ---------------- OrderItem / Order ----------------

        t.scenario("Order total uses BigDecimal and exact cents",
                "0.10 × 3 is exactly 0.30, which double cannot represent (gap 13)", () -> {
            MenuItem cheap = item("Mint", "0.10", 10);
            Order order = new Order(customer(), restaurantSelling(cheap), List.of(new OrderItem(cheap, 3)));
            checkEquals(new BigDecimal("0.30"), order.getTotalAmount(), "total");
        });

        t.scenario("OrderItem snapshots the unit price at checkout",
                "the line keeps the price it was ordered at", () -> {
            MenuItem burger = item("Burger", "8.99", 5);
            OrderItem line = new OrderItem(burger, 2);
            checkEquals(new BigDecimal("8.99"), line.getUnitPrice(), "unit price");
            checkEquals(new BigDecimal("17.98"), line.getSubTotal(), "subtotal");
        });

        t.scenario("Order copies its item list",
                "changing the caller's list after checkout cannot change the order", () -> {
            MenuItem a = item("A", "1.00", 5);
            List<OrderItem> lines = new ArrayList<>(List.of(new OrderItem(a, 1)));
            Order order = new Order(customer(), restaurantSelling(a), lines);
            lines.add(new OrderItem(a, 4));
            checkEquals(1, order.getItems().size(), "lines on the order");
        });

        t.scenario("Order.transitionTo follows the table and notifies with the new status",
                "illegal moves throw and change nothing; legal ones notify each observer once", () -> {
            MenuItem a = item("A", "1.00", 5);
            Order order = new Order(customer(), restaurantSelling(a), List.of(new OrderItem(a, 1)));
            RecordingObserver rec = new RecordingObserver();
            order.addObserver(rec);

            checkThrows(IllegalStateException.class,
                    () -> order.transitionTo(OrderStatus.DELIVERED), "PENDING -> DELIVERED");
            checkEquals(OrderStatus.PENDING, order.getOrderStatus(), "status after illegal move");
            check(rec.seen.isEmpty(), "no notification for a rejected move");

            quietly(() -> order.transitionTo(OrderStatus.CONFIRMED));
            checkEquals(List.of(OrderStatus.CONFIRMED), rec.seen, "notifications");
        });

        t.scenario("Order does not notify from its constructor",
                "`this` never escapes a half-built order (gap 9)", () -> {
            RecordingObserver rec = new RecordingObserver();
            MenuItem a = item("A", "1.00", 5);
            Order order = new Order(customer(), restaurantSelling(a), List.of(new OrderItem(a, 1)));
            order.addObserver(rec);
            check(rec.seen.isEmpty(), "nothing was broadcast during construction");
        });

        t.scenario("A throwing observer does not stop the others",
                "each observer is isolated in its own try/catch (gap 8)", () -> {
            MenuItem a = item("A", "1.00", 5);
            Order order = new Order(customer(), restaurantSelling(a), List.of(new OrderItem(a, 1)));
            RecordingObserver before = new RecordingObserver();
            RecordingObserver after = new RecordingObserver();
            order.addObserver(before);
            order.addObserver(new ExplodingObserver());
            order.addObserver(after);
            quietly(() -> order.transitionTo(OrderStatus.CONFIRMED));
            checkEquals(List.of(OrderStatus.CONFIRMED), before.seen, "observer before the bad one");
            checkEquals(List.of(OrderStatus.CONFIRMED), after.seen, "observer after the bad one");
            checkEquals(OrderStatus.CONFIRMED, order.getOrderStatus(), "status committed");
        });

        t.scenario("removeObserver stops notifications",
                "Subject supports unsubscribing (gap 15)", () -> {
            MenuItem a = item("A", "1.00", 5);
            Order order = new Order(customer(), restaurantSelling(a), List.of(new OrderItem(a, 1)));
            RecordingObserver rec = new RecordingObserver();
            order.addObserver(rec);
            order.removeObserver(rec);
            quietly(() -> order.transitionTo(OrderStatus.CONFIRMED));
            check(rec.seen.isEmpty(), "removed observer heard nothing");
        });

        t.scenario("Order.dispatch attaches the agent only from READY_FOR_PICKUP",
                "an order is never OUT_FOR_DELIVERY without an agent", () -> {
            MenuItem a = item("A", "1.00", 5);
            Order order = new Order(customer(), restaurantSelling(a), List.of(new OrderItem(a, 1)));
            DeliveryAgent agent = new DeliveryAgent("Dan", "1", address("C", 0, 0));
            check(!order.dispatch(agent), "dispatch from PENDING is refused");
            check(order.getDeliveryAgent() == null, "no agent attached");
            quietly(() -> {
                order.transitionTo(OrderStatus.CONFIRMED);
                order.transitionTo(OrderStatus.PREPARING);
                order.transitionTo(OrderStatus.READY_FOR_PICKUP);
                check(order.dispatch(agent), "dispatch from READY_FOR_PICKUP succeeds");
            });
            checkEquals(OrderStatus.OUT_FOR_DELIVERY, order.getOrderStatus(), "status");
            check(order.getDeliveryAgent() == agent, "agent attached");
            check(order.getObservers().contains(agent), "agent subscribed");
        });

        // ---------------- DeliveryAgent / Customer / Restaurant ----------------

        t.scenario("DeliveryAgent claim/release/unclaim",
                "claim is one-shot until released; release moves the agent (gap 11, 14)", () -> {
            Address start = address("C", 0, 0);
            Address dropOff = address("C", 1, 1);
            DeliveryAgent agent = new DeliveryAgent("Eve", "2", start);
            check(agent.claim(), "first claim wins");
            check(!agent.claim(), "second claim loses");
            check(!agent.isAvailable(), "busy after claim");
            agent.unclaim();
            check(agent.isAvailable() && agent.getAddress() == start, "unclaim frees without moving");
            agent.claim();
            agent.release(dropOff);
            check(agent.isAvailable(), "free after release");
            check(agent.getAddress() == dropOff, "now standing at the drop-off point");
        });

        t.scenario("Customer order history is read-only to callers",
                "history can only grow through addToOrderHistory (gap 10)", () -> {
            Customer c = customer();
            checkThrows(UnsupportedOperationException.class,
                    () -> c.getOrderHistory().add(null), "external add");
        });

        t.scenario("Restaurant.sells recognises only its own menu items",
                "an item with the same id from another restaurant is not accepted (gap 7)", () -> {
            MenuItem mine = new MenuItem("SAME", "Mine", BigDecimal.ONE, 1);
            MenuItem theirs = new MenuItem("SAME", "Theirs", BigDecimal.ONE, 1);
            Restaurant r = restaurantSelling(mine);
            check(r.sells(mine), "sells its own item");
            check(!r.sells(theirs), "does not sell a look-alike");
            check(!r.sells(null), "does not sell null");
        });

        // ---------------- Strategies ----------------

        t.scenario("SearchByMenuKeywordStrategy is case-insensitive on both sides",
                "'Pizza', 'PIZZA' and 'pizza' all match 'Margherita Pizza' (gap 2)", () -> {
            Restaurant pizza = restaurantSelling(item("Margherita Pizza", "12.99", 1));
            Restaurant burger = restaurantSelling(item("Classic Burger", "8.99", 1));
            for (String kw : List.of("Pizza", "PIZZA", "pizza")) {
                List<Restaurant> hits = new SearchByMenuKeywordStrategy(kw).filter(List.of(pizza, burger));
                checkEquals(List.of(pizza), hits, "matches for '" + kw + "'");
            }
        });

        t.scenario("SearchByCityStrategy keeps exact city matches",
                "filtering by city", () -> {
            Restaurant a = new Restaurant("A", address("Springfield", 0, 0));
            Restaurant b = new Restaurant("B", address("Shelbyville", 0, 0));
            checkEquals(List.of(a), new SearchByCityStrategy("Springfield").filter(List.of(a, b)), "Springfield");
        });

        t.scenario("SearchByProximityStrategy filters by km radius and sorts nearest first",
                "radius is kilometres, results ordered by distance", () -> {
            Address me = address("C", 40.7128, -74.0060);
            Restaurant far = new Restaurant("Far", address("C", 40.7228, -74.0060));   // ~1.1 km
            Restaurant near = new Restaurant("Near", address("C", 40.7138, -74.0060)); // ~0.1 km
            Restaurant mid = new Restaurant("Mid", address("C", 40.7178, -74.0060));   // ~0.6 km
            List<Restaurant> hits = new SearchByProximityStrategy(me, 1.0).filter(List.of(far, near, mid));
            checkEquals(List.of("Near", "Mid"), hits.stream().map(Restaurant::getName).toList(), "within 1 km");
        });

        t.scenario("NearestAvailableAgentStrategy uses the customer's address and skips busy agents",
                "no NullPointerException before an agent is assigned (gap 1)", () -> {
            MenuItem a = item("A", "1.00", 5);
            Restaurant r = new Restaurant("R", address("C", 10.0, 10.0));
            r.addToMenu(a);
            Order order = new Order(new Customer("Cy", "0", address("C", 10.01, 10.0)), r,
                    List.of(new OrderItem(a, 1)));
            DeliveryAgent close = new DeliveryAgent("Close", "1", address("C", 10.001, 10.0));
            DeliveryAgent far = new DeliveryAgent("Far", "2", address("C", 10.2, 10.0));
            NearestAvailableAgentStrategy s = new NearestAvailableAgentStrategy();

            checkEquals(Optional.of(close), s.findAgent(order, List.of(far, close)), "nearest");
            close.claim();
            checkEquals(Optional.of(far), s.findAgent(order, List.of(far, close)), "nearest free");
            far.claim();
            checkEquals(Optional.empty(), s.findAgent(order, List.of(far, close)), "nobody free");
        });

        t.finish();
    }

    private static Customer customer() {
        return new Customer("Cust", "0", address("C", 0, 0));
    }

    private static Restaurant restaurantSelling(MenuItem... items) {
        Restaurant r = new Restaurant("R", address("C", 0, 0));
        for (MenuItem i : items) r.addToMenu(i);
        return r;
    }
}
