package LLD.online_food_Delivery_Service.solution.tests;

import LLD.online_food_Delivery_Service.solution.FoodDeliveryService;
import LLD.online_food_Delivery_Service.solution.enums.OrderStatus;
import LLD.online_food_Delivery_Service.solution.models.*;
import LLD.online_food_Delivery_Service.solution.strategies.search.RestaurantSearchStrategy;
import LLD.online_food_Delivery_Service.solution.strategies.search.SearchByCityStrategy;
import LLD.online_food_Delivery_Service.solution.strategies.search.SearchByMenuKeywordStrategy;
import LLD.online_food_Delivery_Service.solution.strategies.search.SearchByProximityStrategy;

import java.math.BigDecimal;
import java.util.List;
import java.util.NoSuchElementException;

import static LLD.online_food_Delivery_Service.solution.tests.Fixtures.*;
import static LLD.online_food_Delivery_Service.solution.tests.TestHarness.*;

/**
 * End-to-end flow tests: every scenario drives the real FoodDeliveryService
 * singleton through a complete user journey — register, search, browse, order,
 * cook, dispatch, deliver, cancel — and checks the state of every object
 * involved at the end, not just the return value of one call.
 *
 * Run from the repository root:
 *   javac -d out $(find LLD/online_food_Delivery_Service/solution -name '*.java')
 *   java  -cp out LLD.online_food_Delivery_Service.solution.tests.EndToEndFlowTests
 */
public class EndToEndFlowTests {

    private static final FoodDeliveryService service = service();

    public static void main(String[] args) {
        TestHarness t = new TestHarness("Food Delivery — End-to-End Flow Tests",
                "Complete journeys through the real FoodDeliveryService singleton. Nothing is mocked.");

        t.scenario("Happy path: search → menu → order → cook → auto-dispatch → deliver",
                "every step of the lifecycle works together and leaves all objects consistent", () -> {
            Address home = address("Metro", 12.9716, 77.5946);
            Customer alice = service.registerCustomer("Alice", "111", home);
            Restaurant pizzeria = service.registerRestaurant("Metro Pizzeria", address("Metro", 12.9726, 77.5956));
            MenuItem margherita = item("Margherita Pizza", "12.99", 10);
            MenuItem garlicBread = item("Garlic Bread", "4.50", 10);
            pizzeria.addToMenu(margherita);
            pizzeria.addToMenu(garlicBread);
            DeliveryAgent bob = service.registerDeliveryAgent("Bob", "222", address("Metro", 12.9700, 77.5900));
            service.setAssignmentStrategy(onlyAgents(bob));

            List<RestaurantSearchStrategy> query = List.of(
                    new SearchByCityStrategy("Metro"),
                    new SearchByProximityStrategy(home, 1.0),
                    new SearchByMenuKeywordStrategy("pizza"));
            checkEquals(List.of(pizzeria), service.searchRestaurants(query), "search results");
            checkEquals(2, service.getRestaurantMenu(pizzeria.getId()).getItems().size(), "menu size");

            Order order = placeQuietly(alice, pizzeria,
                    new OrderItem(margherita, 2), new OrderItem(garlicBread, 1));
            RecordingObserver customerView = new RecordingObserver();
            order.addObserver(customerView);
            checkEquals(OrderStatus.PENDING, order.getOrderStatus(), "status after placing");
            checkEquals(new BigDecimal("30.48"), order.getTotalAmount(), "total (2×12.99 + 4.50)");
            checkEquals(8, margherita.getStock(), "margherita stock reserved");

            quietly(() -> {
                service.updateOrderStatus(order.getId(), OrderStatus.CONFIRMED);
                service.updateOrderStatus(order.getId(), OrderStatus.PREPARING);
                service.updateOrderStatus(order.getId(), OrderStatus.READY_FOR_PICKUP);
            });
            checkEquals(OrderStatus.OUT_FOR_DELIVERY, order.getOrderStatus(), "status after READY_FOR_PICKUP");
            check(order.getDeliveryAgent() == bob, "Bob was assigned");
            check(!bob.isAvailable(), "Bob is busy");

            quietly(() -> service.updateOrderStatus(order.getId(), OrderStatus.DELIVERED));
            checkEquals(OrderStatus.DELIVERED, order.getOrderStatus(), "final status");
            check(bob.isAvailable(), "Bob is free again");
            check(bob.getAddress() == home, "Bob is now at Alice's address");
            check(!order.getObservers().contains(bob), "Bob unsubscribed after delivery");
            checkEquals(List.of(OrderStatus.CONFIRMED, OrderStatus.PREPARING, OrderStatus.READY_FOR_PICKUP,
                    OrderStatus.OUT_FOR_DELIVERY, OrderStatus.DELIVERED), customerView.seen, "notifications, in order");
            checkEquals(List.of(order), service.getOrdersForCustomer(alice.getId()), "Alice's order history");
            check(service.getOrder(order.getId()) == order, "order retrievable by id");
        });

        t.scenario("Cancel while PENDING releases the reserved stock",
                "cancellation goes through the table and gives stock back (gaps 5, 6)", () -> {
            Setup s = new Setup("Cancel-1", 5);
            Order order = placeQuietly(s.customer, s.restaurant, new OrderItem(s.dish, 3));
            checkEquals(2, s.dish.getStock(), "stock after ordering 3 of 5");
            RecordingObserver rec = new RecordingObserver();
            order.addObserver(rec);
            quietly(() -> service.cancel(order.getId()));
            checkEquals(OrderStatus.CANCELLED, order.getOrderStatus(), "status");
            checkEquals(5, s.dish.getStock(), "stock restored");
            checkEquals(List.of(OrderStatus.CANCELLED), rec.seen, "notification");
        });

        t.scenario("Cancel while CONFIRMED works; once PREPARING it is refused",
                "the point of no return is PREPARING, and a refusal throws instead of printing (gap 5)", () -> {
            Setup s = new Setup("Cancel-2", 5);
            Order early = placeQuietly(s.customer, s.restaurant, new OrderItem(s.dish, 1));
            quietly(() -> {
                service.updateOrderStatus(early.getId(), OrderStatus.CONFIRMED);
                service.cancel(early.getId());
            });
            checkEquals(OrderStatus.CANCELLED, early.getOrderStatus(), "cancelled from CONFIRMED");

            Order late = placeQuietly(s.customer, s.restaurant, new OrderItem(s.dish, 1));
            quietly(() -> {
                service.updateOrderStatus(late.getId(), OrderStatus.CONFIRMED);
                service.updateOrderStatus(late.getId(), OrderStatus.PREPARING);
            });
            checkThrows(IllegalStateException.class, () -> service.cancel(late.getId()), "cancel while PREPARING");
            checkEquals(OrderStatus.PREPARING, late.getOrderStatus(), "status unchanged");
            checkEquals(4, s.dish.getStock(), "stock still held by the cooking order");
            checkThrows(NoSuchElementException.class, () -> service.cancel("no-such-order"), "cancel unknown order");
        });

        t.scenario("Illegal and system-only transitions are rejected through the service",
                "skipping steps, leaving terminal states and hand-setting OUT_FOR_DELIVERY all fail", () -> {
            Setup s = new Setup("Illegal", 5);
            Order order = placeQuietly(s.customer, s.restaurant, new OrderItem(s.dish, 1));
            checkThrows(IllegalStateException.class,
                    () -> service.updateOrderStatus(order.getId(), OrderStatus.PREPARING), "PENDING -> PREPARING");
            checkThrows(IllegalArgumentException.class,
                    () -> service.updateOrderStatus(order.getId(), OrderStatus.OUT_FOR_DELIVERY),
                    "manual OUT_FOR_DELIVERY");
            quietly(() -> service.cancel(order.getId()));
            checkThrows(IllegalStateException.class,
                    () -> service.updateOrderStatus(order.getId(), OrderStatus.CONFIRMED), "CANCELLED -> CONFIRMED");
            checkThrows(NoSuchElementException.class,
                    () -> service.updateOrderStatus("missing", OrderStatus.CONFIRMED), "unknown order");
        });

        t.scenario("placeOrder validation rejects every bad request",
                "unknown ids, empty orders, bad quantities, foreign and unavailable items (gap 7)", () -> {
            Setup s = new Setup("Validate", 5);
            Setup other = new Setup("Validate-Other", 5);
            checkThrows(NoSuchElementException.class,
                    () -> service.placeOrder("nobody", s.restaurant.getId(), List.of(new OrderItem(s.dish, 1))),
                    "unknown customer");
            checkThrows(NoSuchElementException.class,
                    () -> service.placeOrder(s.customer.getId(), "nowhere", List.of(new OrderItem(s.dish, 1))),
                    "unknown restaurant");
            checkThrows(IllegalArgumentException.class,
                    () -> service.placeOrder(s.customer.getId(), s.restaurant.getId(), List.of()), "empty order");
            checkThrows(IllegalArgumentException.class,
                    () -> service.placeOrder(s.customer.getId(), s.restaurant.getId(), List.of(new OrderItem(s.dish, 0))),
                    "zero quantity");
            checkThrows(IllegalArgumentException.class,
                    () -> service.placeOrder(s.customer.getId(), s.restaurant.getId(), List.of(new OrderItem(other.dish, 1))),
                    "item from another restaurant");
            s.dish.setAvailable(false);
            checkThrows(IllegalStateException.class,
                    () -> service.placeOrder(s.customer.getId(), s.restaurant.getId(), List.of(new OrderItem(s.dish, 1))),
                    "switched-off item");
            checkEquals(0, s.customer.getOrderHistory().size(), "no order was recorded");
        });

        t.scenario("A multi-line order short on one line reserves nothing",
                "stock reservation is all-or-nothing across lines (gap 6)", () -> {
            Setup s = new Setup("Rollback", 5);
            MenuItem scarce = item("Truffle Fries", "9.00", 1);
            s.restaurant.addToMenu(scarce);
            checkThrows(IllegalStateException.class,
                    () -> service.placeOrder(s.customer.getId(), s.restaurant.getId(),
                            List.of(new OrderItem(s.dish, 2), new OrderItem(scarce, 2))),
                    "second line short");
            checkEquals(5, s.dish.getStock(), "first line's stock was given back");
            checkEquals(1, scarce.getStock(), "scarce item untouched");
        });

        t.scenario("Selling out, then cancelling, frees the stock for the next customer",
                "stock flows correctly across several orders", () -> {
            Setup s = new Setup("SellOut", 2);
            Order first = placeQuietly(s.customer, s.restaurant, new OrderItem(s.dish, 2));
            check(!s.dish.isAvailable(), "sold out");
            checkThrows(IllegalStateException.class,
                    () -> service.placeOrder(s.customer.getId(), s.restaurant.getId(), List.of(new OrderItem(s.dish, 1))),
                    "order while sold out");
            quietly(() -> service.cancel(first.getId()));
            Order second = placeQuietly(s.customer, s.restaurant, new OrderItem(s.dish, 1));
            checkEquals(OrderStatus.PENDING, second.getOrderStatus(), "next order accepted");
            checkEquals(1, s.dish.getStock(), "stock");
        });

        t.scenario("No agent free: the order is queued, then dispatched when an agent registers",
                "an unassignable order is never stranded (gap 3)", () -> {
            Setup s = new Setup("Queue", 5);
            service.setAssignmentStrategy(onlyAgents());   // nobody eligible yet
            int queuedBefore = service.getPendingAssignmentCount();
            Order order = placeQuietly(s.customer, s.restaurant, new OrderItem(s.dish, 1));
            readyForPickup(order);
            checkEquals(OrderStatus.READY_FOR_PICKUP, order.getOrderStatus(), "waiting for an agent");
            checkEquals(queuedBefore + 1, service.getPendingAssignmentCount(), "queue length");

            // Registration is what triggers the retry, so the strategy has to accept
            // the new agent before it exists: match it by its (unique) name.
            service.setAssignmentStrategy((o, candidates) -> candidates.stream()
                    .filter(a -> a.getName().equals("Carol-Queue") && a.isAvailable()).findFirst());
            DeliveryAgent[] carol = new DeliveryAgent[1];
            quietly(() -> carol[0] = service.registerDeliveryAgent("Carol-Queue", "333", address("Queue", 0, 0)));
            checkEquals(OrderStatus.OUT_FOR_DELIVERY, order.getOrderStatus(), "dispatched on registration");
            check(order.getDeliveryAgent() == carol[0], "Carol has it");
            checkEquals(queuedBefore, service.getPendingAssignmentCount(), "queue drained");
            quietly(() -> service.updateOrderStatus(order.getId(), OrderStatus.DELIVERED));
        });

        t.scenario("Busy agent: the second order waits and is dispatched the moment the first is delivered",
                "a freed agent immediately picks up queued work", () -> {
            Setup s = new Setup("Handoff", 5);
            DeliveryAgent dave = service.registerDeliveryAgent("Dave", "444", address("Handoff", 0, 0));
            service.setAssignmentStrategy(onlyAgents(dave));
            Order first = placeQuietly(s.customer, s.restaurant, new OrderItem(s.dish, 1));
            Order second = placeQuietly(s.customer, s.restaurant, new OrderItem(s.dish, 1));
            readyForPickup(first);
            readyForPickup(second);
            check(first.getDeliveryAgent() == dave, "Dave took the first order");
            checkEquals(OrderStatus.READY_FOR_PICKUP, second.getOrderStatus(), "second order waiting");

            quietly(() -> service.updateOrderStatus(first.getId(), OrderStatus.DELIVERED));
            checkEquals(OrderStatus.OUT_FOR_DELIVERY, second.getOrderStatus(), "second order dispatched");
            check(second.getDeliveryAgent() == dave, "to Dave, now free again");
            quietly(() -> service.updateOrderStatus(second.getId(), OrderStatus.DELIVERED));
            check(dave.isAvailable(), "Dave free at the end");
        });

        t.scenario("The nearest of several free agents is chosen",
                "NearestAvailableAgentStrategy ranks by real distance to the restaurant", () -> {
            Address shop = address("Nearest", 51.5074, -0.1278);
            Restaurant r = service.registerRestaurant("Nearest Diner", shop);
            MenuItem dish = item("Fish and Chips", "11.00", 5);
            r.addToMenu(dish);
            Customer c = service.registerCustomer("Nia", "555", address("Nearest", 51.5100, -0.1278));
            DeliveryAgent far = service.registerDeliveryAgent("Far", "1", address("Nearest", 51.6000, -0.1278));
            DeliveryAgent near = service.registerDeliveryAgent("Near", "2", address("Nearest", 51.5080, -0.1278));
            DeliveryAgent mid = service.registerDeliveryAgent("Mid", "3", address("Nearest", 51.5300, -0.1278));
            service.setAssignmentStrategy(onlyAgents(far, near, mid));
            Order order = placeQuietly(c, r, new OrderItem(dish, 1));
            readyForPickup(order);
            checkEquals("Near", order.getDeliveryAgent().getName(), "assigned agent");
            quietly(() -> service.updateOrderStatus(order.getId(), OrderStatus.DELIVERED));
        });

        t.scenario("Lookups fail fast on unknown ids",
                "getOrder, getRestaurantMenu and getOrdersForCustomer throw NoSuchElementException", () -> {
            checkThrows(NoSuchElementException.class, () -> service.getOrder("x"), "getOrder");
            checkThrows(NoSuchElementException.class, () -> service.getRestaurantMenu("x"), "getRestaurantMenu");
            checkThrows(NoSuchElementException.class, () -> service.getOrdersForCustomer("x"), "getOrdersForCustomer");
        });

        t.scenario("Singleton and strategy wiring",
                "one instance everywhere; a null strategy is refused", () -> {
            check(FoodDeliveryService.getInstance() == service, "getInstance returns the same object");
            checkThrows(NullPointerException.class, () -> service.setAssignmentStrategy(null), "null strategy");
        });

        t.finish();
    }

    /** A customer, a restaurant and one dish, all unique to the calling scenario. */
    static final class Setup {
        final Customer customer;
        final Restaurant restaurant;
        final MenuItem dish;

        Setup(String tag, int stock) {
            customer = service.registerCustomer("Cust-" + tag, "0", address(tag, 0, 0));
            restaurant = service.registerRestaurant("Rest-" + tag, address(tag, 0.001, 0));
            dish = item("Dish-" + tag, "10.00", stock);
            restaurant.addToMenu(dish);
        }
    }

    static Order placeQuietly(Customer c, Restaurant r, OrderItem... lines) throws Exception {
        Order[] out = new Order[1];
        quietly(() -> out[0] = service.placeOrder(c.getId(), r.getId(), List.of(lines)));
        return out[0];
    }

    static void readyForPickup(Order order) throws Exception {
        quietly(() -> {
            service.updateOrderStatus(order.getId(), OrderStatus.CONFIRMED);
            service.updateOrderStatus(order.getId(), OrderStatus.PREPARING);
            service.updateOrderStatus(order.getId(), OrderStatus.READY_FOR_PICKUP);
        });
    }
}
