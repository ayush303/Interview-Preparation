package LLD.online_food_Delivery_Service.solution;

import LLD.online_food_Delivery_Service.solution.enums.OrderStatus;
import LLD.online_food_Delivery_Service.solution.models.*;
import LLD.online_food_Delivery_Service.solution.strategies.assignment.DeliveryAssignmentStrategy;
import LLD.online_food_Delivery_Service.solution.strategies.assignment.NearestAvailableAgentStrategy;
import LLD.online_food_Delivery_Service.solution.strategies.search.RestaurantSearchStrategy;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;

public class FoodDeliveryService {

    private static volatile FoodDeliveryService instance;
    private static final Object lock = new Object();
    private final Map<String, Customer> customers = new ConcurrentHashMap<>();
    private final Map<String, Restaurant> restaurants = new ConcurrentHashMap<>();
    private final Map<String, DeliveryAgent> deliveryAgents = new ConcurrentHashMap<>();
    private final Map<String, Order> orders = new ConcurrentHashMap<>();

    // Orders that reached READY_FOR_PICKUP when no agent could be claimed.
    // Retried whenever an agent is registered or freed.
    private final Queue<Order> pendingAssignments = new ConcurrentLinkedQueue<>();

    private volatile DeliveryAssignmentStrategy assignmentStrategy = new NearestAvailableAgentStrategy();

    private FoodDeliveryService() {}

    public static FoodDeliveryService getInstance() {
        if (instance == null) {
            synchronized (lock) {
                if (instance == null) {
                    instance = new FoodDeliveryService();
                }
            }
        }
        return instance;
    }

    public void setAssignmentStrategy(DeliveryAssignmentStrategy deliveryAssignmentStrategy) {
        this.assignmentStrategy = Objects.requireNonNull(deliveryAssignmentStrategy, "strategy");
    }

    // --- Registration ---
    public Customer registerCustomer(String name, String phone, Address address) {
        Customer customer = new Customer(name, phone, address);
        customers.put(customer.getId(), customer);
        return customer;
    }

    public Restaurant registerRestaurant(String name, Address address) {
        Restaurant restaurant = new Restaurant(name, address);
        restaurants.put(restaurant.getId(), restaurant);
        return restaurant;
    }

    public DeliveryAgent registerDeliveryAgent(String name, String phone, Address initialLocation) {
        DeliveryAgent deliveryAgent = new DeliveryAgent(name, phone, initialLocation);
        deliveryAgents.put(deliveryAgent.getId(), deliveryAgent);
        retryPendingAssignments();
        return deliveryAgent;
    }

    // --- Orders ---
    public Order placeOrder(String customerId, String restaurantId, List<OrderItem> items) {
        Customer customer = customers.get(customerId);
        Restaurant restaurant = restaurants.get(restaurantId);
        if (customer == null || restaurant == null) {
            throw new NoSuchElementException("Customer or Restaurant not found.");
        }

        if (items == null || items.isEmpty()) {
            throw new IllegalArgumentException("Order must contain at least one item.");
        }

        for (OrderItem orderItem : items) {
            if (orderItem.getQuantity() <= 0) {
                throw new IllegalArgumentException("Item quantity must be positive.");
            }
            if (!restaurant.sells(orderItem.getItem())) {
                throw new IllegalArgumentException(
                        orderItem.getItem().getName() + " is not on " + restaurant.getName() + "'s menu.");
            }
        }

        reserveStock(items);

        Order order = new Order(customer, restaurant, items);
        orders.put(order.getId(), order);
        customer.addToOrderHistory(order);
        System.out.printf("Order %s placed by %s at %s.\n", order.getId(), customer.getName(), restaurant.getName());
        order.notifyObservers(OrderStatus.PENDING);
        return order;
    }

    // All-or-nothing: if any line cannot be reserved, give back what was taken.
    private void reserveStock(List<OrderItem> items) {
        List<OrderItem> reserved = new ArrayList<>();
        for (OrderItem orderItem : items) {
            if (!orderItem.getItem().reserve(orderItem.getQuantity())) {
                reserved.forEach(r -> r.getItem().release(r.getQuantity()));
                throw new IllegalStateException(orderItem.getItem().getName() + " is not available.");
            }
            reserved.add(orderItem);
        }
    }

    /**
     * Moves an order to {@code newStatus}. The check against the transition table
     * and the write happen atomically inside Order.transitionTo.
     *
     * OUT_FOR_DELIVERY cannot be requested: only the dispatcher sets it, at the
     * same moment it attaches an agent.
     */
    public void updateOrderStatus(String orderId, OrderStatus newStatus) {
        Order order = getOrder(orderId);
        if (newStatus == OrderStatus.OUT_FOR_DELIVERY) {
            throw new IllegalArgumentException("OUT_FOR_DELIVERY is set by the system when an agent is assigned.");
        }

        order.transitionTo(newStatus);

        switch (newStatus) {
            case READY_FOR_PICKUP -> assignOrQueue(order);
            case DELIVERED -> completeDelivery(order);
            case CANCELLED -> releaseStock(order);
            default -> { }
        }
    }

    /**
     * Cancels an order. Goes through the same transition table as every other
     * change, so it is allowed only from PENDING or CONFIRMED.
     *
     * @throws NoSuchElementException if the order does not exist
     * @throws IllegalStateException  if the order can no longer be cancelled
     */
    public void cancel(String orderId) {
        updateOrderStatus(orderId, OrderStatus.CANCELLED);
    }

    private void releaseStock(Order order) {
        order.getItems().forEach(i -> i.getItem().release(i.getQuantity()));
    }

    private void completeDelivery(Order order) {
        DeliveryAgent agent = order.getDeliveryAgent();
        order.removeObserver(agent);
        agent.release(order.getCustomer().getAddress());
        retryPendingAssignments();
    }

    private void assignOrQueue(Order order) {
        if (!tryAssign(order)) {
            pendingAssignments.add(order);
            System.out.printf("No delivery agent free for order %s; queued for assignment.\n", order.getId());
            // An agent may have been freed between the failed attempt and the enqueue.
            retryPendingAssignments();
        }
    }

    /**
     * Claims the best agent the strategy offers. If another thread wins that
     * agent first, the next best is tried, so losing a race never strands the order.
     *
     * @return true if the order no longer needs an agent
     */
    private boolean tryAssign(Order order) {
        List<DeliveryAgent> candidates = new ArrayList<>(deliveryAgents.values());
        while (true) {
            Optional<DeliveryAgent> best = assignmentStrategy.findAgent(order, candidates);
            if (best.isEmpty()) {
                return false;
            }
            DeliveryAgent agent = best.get();
            if (agent.claim()) {
                if (!order.dispatch(agent)) {
                    agent.unclaim();
                }
                return true;
            }
            candidates.remove(agent);
        }
    }

    private void retryPendingAssignments() {
        for (Order order : pendingAssignments) {
            if (tryAssign(order)) {
                pendingAssignments.remove(order);
            }
        }
    }

    // --- Queries ---
    public Order getOrder(String orderId) {
        Order order = orders.get(orderId);
        if (order == null) {
            throw new NoSuchElementException("Order " + orderId + " not found.");
        }
        return order;
    }

    public List<Order> getOrdersForCustomer(String customerId) {
        Customer customer = customers.get(customerId);
        if (customer == null) {
            throw new NoSuchElementException("Customer " + customerId + " not found.");
        }
        return customer.getOrderHistory();
    }

    public int getPendingAssignmentCount() {
        return pendingAssignments.size();
    }

    public List<Restaurant> searchRestaurants(List<RestaurantSearchStrategy> strategies) {
        List<Restaurant> results = new ArrayList<>(restaurants.values());

        for (RestaurantSearchStrategy strategy : strategies) {
            results = strategy.filter(results);
        }
        return results;
    }

    public Menu getRestaurantMenu(String restaurantId) {
        Restaurant restaurant = restaurants.get(restaurantId);
        if (restaurant == null) {
            throw new NoSuchElementException("Restaurant with ID " + restaurantId + " not found.");
        }
        return restaurant.getMenu();
    }

}
