package LLD.online_food_Delivery_Service.solution.models;

import LLD.online_food_Delivery_Service.solution.enums.OrderStatus;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public class Customer extends User {

    private final Address address;
    private final List<Order> orderHistory = new CopyOnWriteArrayList<>();

    public Customer(String name, String phone, Address address) {
        super(name, phone);
        this.address = address;
    }

    public Address getAddress() {
        return address;
    }

    public List<Order> getOrderHistory() {
        return Collections.unmodifiableList(orderHistory);
    }

    public void addToOrderHistory(Order order) {
        orderHistory.add(order);
    }

    @Override
    public void onUpdate(Order order, OrderStatus status) {
        System.out.printf("--- Notification for Customer %s ---\n", getName());
        System.out.printf("  Order %s is now %s.\n", order.getId(), status);
        System.out.println("-------------------------------------\n");
    }
}
