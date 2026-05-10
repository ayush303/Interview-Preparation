package LLD.onlineStockBroker.solutions.state;

import LLD.onlineStockBroker.solutions.enums.OrderStatus;
import LLD.onlineStockBroker.solutions.models.Order;

public class OpenState implements OrderState {

    @Override
    public void handle(Order order) {
        System.out.printf("  [State] Order %s is OPEN — awaiting match in order book.%n",
                order.getOrderId().substring(0, 8));
    }

    @Override
    public void cancel(Order order) {
        // An OPEN order can always be cancelled
        order.setStatus(OrderStatus.CANCELLED);
        order.setState(new CancelledState());
        System.out.printf("  [State] Order %s has been CANCELLED (was OPEN).%n",
                order.getOrderId().substring(0, 8));
    }

    @Override
    public String getStateName() {
        return "OPEN";
    }
}