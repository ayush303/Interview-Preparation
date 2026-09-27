package LLD.online_food_Delivery_Service.solution.models;

import LLD.online_food_Delivery_Service.solution.enums.OrderStatus;

import java.util.concurrent.atomic.AtomicBoolean;

public class DeliveryAgent extends User {

    private final AtomicBoolean available = new AtomicBoolean(true);
    private volatile Address currentLocation;

    public DeliveryAgent(String name, String phone, Address address) {
        super(name, phone);
        this.currentLocation = address;
    }

    /** Atomically marks the agent busy. Exactly one of any number of racing callers gets true. */
    public boolean claim() {
        return available.compareAndSet(true, false);
    }

    /** Frees the agent after a delivery, standing at the drop-off point. */
    public void release(Address dropOff) {
        this.currentLocation = dropOff;
        available.set(true);
    }

    /** Undoes a claim that did not turn into a delivery. The agent has not moved. */
    public void unclaim() {
        available.set(true);
    }

    public boolean isAvailable() {
        return available.get();
    }

    public Address getAddress() {
        return currentLocation;
    }

    public void setCurrentLocation(Address address) {
        this.currentLocation = address;
    }

    @Override
    public void onUpdate(Order order, OrderStatus status) {
        System.out.printf("--- Notification for Delivery Agent %s ---\n", getName());
        System.out.printf("  Order %s update: Status is %s.\n", order.getId(), status);
        System.out.println("-------------------------------------------\n");
    }
}
