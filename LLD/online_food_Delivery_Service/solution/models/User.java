package LLD.online_food_Delivery_Service.solution.models;

import LLD.online_food_Delivery_Service.solution.observer.Observer;

import java.util.UUID;

public abstract class User implements Observer {
    private final String id;
    private String name;
    private String phone;

    public User(String name, String phone) {
        this.id = UUID.randomUUID().toString();
        this.name = name;
        this.phone = phone;
    }

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getPhone() {
        return phone;
    }
}
