package LLD.online_food_Delivery_Service.solution.models;

import LLD.online_food_Delivery_Service.solution.enums.OrderStatus;
import LLD.online_food_Delivery_Service.solution.observer.Observer;

import java.util.UUID;

public class Restaurant implements Observer {

    private final String id;
    private final String name;
    private final Address address;
    private final Menu menu;

    public Restaurant(String name, Address address) {
        this.id = UUID.randomUUID().toString();
        this.name = name;
        this.address = address;
        this.menu = new Menu();
    }

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public Address getAddress() {
        return address;
    }

    public Menu getMenu() {
        return menu;
    }

    public void addToMenu(MenuItem item) {
        menu.addItem(item);
    }

    /** True only if this exact item object is on this restaurant's menu. */
    public boolean sells(MenuItem item) {
        return item != null && menu.getItem(item.getId()) == item;
    }

    @Override
    public void onUpdate(Order order, OrderStatus status) {
        System.out.printf("--- Notification for Restaurant %s ---\n", getName());
        System.out.printf("  Order %s has been updated to %s.\n", order.getId(), status);
        System.out.println("---------------------------------------\n");
    }
}
