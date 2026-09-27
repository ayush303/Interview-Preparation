package LLD.online_food_Delivery_Service.solution.models;

import LLD.online_food_Delivery_Service.solution.enums.OrderStatus;
import LLD.online_food_Delivery_Service.solution.observer.Observer;
import LLD.online_food_Delivery_Service.solution.observer.Subject;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * An order and its lifecycle.
 *
 * Every status change goes through transitionTo or dispatch, which validate and
 * write under the order's lock as one step, so two threads can never both act
 * on the same "from" status. Observers are notified after the lock is released,
 * each in its own try/catch: a slow observer never blocks the order, and a
 * failing one never stops the others hearing about a change that happened.
 */
public class Order implements Subject {

    private final String id;
    private final Customer customer;
    private final Restaurant restaurant;
    private final List<OrderItem> items;
    private final List<Observer> observers = new CopyOnWriteArrayList<>();
    private volatile OrderStatus orderStatus;
    private volatile DeliveryAgent deliveryAgent;

    public Order(Customer customer, Restaurant restaurant, List<OrderItem> items) {
        this.id = UUID.randomUUID().toString();
        this.customer = customer;
        this.restaurant = restaurant;
        this.items = List.copyOf(items);
        this.orderStatus = OrderStatus.PENDING;
        addObserver(customer);
        addObserver(restaurant);
    }

    public String getId() {
        return id;
    }

    public Customer getCustomer() {
        return customer;
    }

    public Restaurant getRestaurant() {
        return restaurant;
    }

    public List<OrderItem> getItems() {
        return items;
    }

    public OrderStatus getOrderStatus() {
        return orderStatus;
    }

    public List<Observer> getObservers() {
        return List.copyOf(observers);
    }

    public DeliveryAgent getDeliveryAgent() {
        return deliveryAgent;
    }

    /**
     * Validates against OrderStatus's transition table and applies the change
     * atomically, then notifies observers.
     *
     * @throws IllegalStateException if {@code next} is not a legal successor
     */
    public void transitionTo(OrderStatus next) {
        synchronized (this) {
            if (!orderStatus.canTransitionTo(next)) {
                throw new IllegalStateException("Invalid transition: " + orderStatus + " -> " + next);
            }
            orderStatus = next;
        }
        notifyObservers(next);
    }

    /**
     * Hands the order to an agent the caller has already claimed. Attaching the
     * agent and moving to OUT_FOR_DELIVERY happen under one lock, so an order is
     * never out for delivery without an agent.
     *
     * @return false, changing nothing, if the order is no longer READY_FOR_PICKUP
     */
    public boolean dispatch(DeliveryAgent agent) {
        synchronized (this) {
            if (orderStatus != OrderStatus.READY_FOR_PICKUP) {
                return false;
            }
            deliveryAgent = agent;
            addObserver(agent);
            orderStatus = OrderStatus.OUT_FOR_DELIVERY;
        }
        notifyObservers(OrderStatus.OUT_FOR_DELIVERY);
        return true;
    }

    public BigDecimal getTotalAmount() {
        return items.stream().map(OrderItem::getSubTotal).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    @Override
    public void addObserver(Observer observer) {
        observers.add(observer);
    }

    @Override
    public void removeObserver(Observer observer) {
        observers.remove(observer);
    }

    @Override
    public void notifyObservers(OrderStatus status) {
        for (Observer observer : observers) {
            try {
                observer.onUpdate(this, status);
            } catch (RuntimeException e) {
                System.err.printf("Observer %s failed on order %s (%s): %s%n",
                        observer.getClass().getSimpleName(), id, status, e);
            }
        }
    }
}
