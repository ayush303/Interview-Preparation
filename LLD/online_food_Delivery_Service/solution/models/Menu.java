package LLD.online_food_Delivery_Service.solution.models;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class Menu {
    private final Map<String, MenuItem> items = new ConcurrentHashMap<>();

    public void addItem(MenuItem item) {
        items.put(item.getId(), item);
    }

    public MenuItem getItem(String id) {
        return items.get(id);
    }

    public Map<String, MenuItem> getItems() {
        return items;
    }
}
