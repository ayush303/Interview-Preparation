package LLD.online_food_Delivery_Service.solution.observer;

import LLD.online_food_Delivery_Service.solution.enums.OrderStatus;
import LLD.online_food_Delivery_Service.solution.models.Order;

public interface Observer {
    /**
     * @param status the status this notification is about. Notifications are sent
     *               after the order's lock is released, so by the time an observer
     *               runs the order may already have moved on; read this, not
     *               order.getOrderStatus().
     */
    void onUpdate(Order order, OrderStatus status);
}
