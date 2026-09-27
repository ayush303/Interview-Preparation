package LLD.online_food_Delivery_Service.solution.models;

import java.math.BigDecimal;

/**
 * One order line. The unit price is copied at checkout, so a later menu price
 * change never alters the total of an order already placed.
 */
public class OrderItem {
    private final MenuItem item;
    private final int quantity;
    private final BigDecimal unitPrice;

    public OrderItem(MenuItem item, int quantity) {
        this.item = item;
        this.quantity = quantity;
        this.unitPrice = item.getPrice();
    }

    public MenuItem getItem() {
        return item;
    }

    public int getQuantity() {
        return quantity;
    }

    public BigDecimal getUnitPrice() {
        return unitPrice;
    }

    public BigDecimal getSubTotal() {
        return unitPrice.multiply(BigDecimal.valueOf(quantity));
    }
}
