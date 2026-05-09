package LLD.onlineStockBroker.solutions.state;

import LLD.onlineStockBroker.solutions.enums.OrderStatus;
import LLD.onlineStockBroker.solutions.models.Order;

public class OpenState implements OrderState {

    @Override
    public void handle(Order order) {
        System.out.println("Order is open and waiting for execution.");
    }

    @Override
    public void cancel(Order order) {
        order.setStatus(OrderStatus.CANCELLED);
        order.setState(new CancelledState());
        System.out.println("Order " + order.getOrderId() + " has been cancelled.");
    }

}
