package LLD.online_food_Delivery_Service.solution.models;

import java.math.BigDecimal;

/**
 * A dish on a menu.
 *
 * Two independent switches decide whether it can be ordered:
 *   available  the restaurant's manual on/off toggle ("86'd for tonight")
 *   stock      portions left; reserve() takes them, release() gives them back
 *
 * All stock changes are synchronized on the item, so reserve is a single
 * check-and-decrement and two orders can never both take the last portion.
 */
public class MenuItem {
    private final String id;
    private final String name;
    private final BigDecimal price;
    private volatile boolean available = true;
    private int stock;

    public MenuItem(String id, String name, BigDecimal price, int stock) {
        if (price == null || price.signum() < 0) {
            throw new IllegalArgumentException("Price must be non-negative.");
        }
        if (stock < 0) {
            throw new IllegalArgumentException("Stock must be non-negative.");
        }
        this.id = id;
        this.name = name;
        this.price = price;
        this.stock = stock;
    }

    /** Atomically takes {@code quantity} portions. Returns false, changing nothing, if it cannot. */
    public synchronized boolean reserve(int quantity) {
        if (quantity <= 0 || !available || stock < quantity) {
            return false;
        }
        stock -= quantity;
        return true;
    }

    /** Returns portions taken by an order that was cancelled or failed to complete. */
    public synchronized void release(int quantity) {
        if (quantity <= 0) {
            throw new IllegalArgumentException("Quantity must be positive.");
        }
        stock += quantity;
    }

    public synchronized void restock(int quantity) {
        release(quantity);
    }

    public synchronized int getStock() {
        return stock;
    }

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public BigDecimal getPrice() {
        return price;
    }

    /** Orderable right now: switched on and at least one portion left. */
    public synchronized boolean isAvailable() {
        return available && stock > 0;
    }

    public void setAvailable(boolean available) {
        this.available = available;
    }

    public String getDescription() {
        return "Name: " + name + ", Price: " + price;
    }
}
