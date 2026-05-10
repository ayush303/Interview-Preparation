package LLD.onlineStockBroker.solutions.state;

import LLD.onlineStockBroker.solutions.enums.OrderStatus;
import LLD.onlineStockBroker.solutions.models.Order;

public class PartiallyFilledState implements OrderState {

    @Override
    public void handle(Order order) {
        System.out.printf("  [State] Order %s is PARTIALLY FILLED — %d/%d shares filled, %d remaining.%n",
                order.getOrderId().substring(0, 8),
                order.getTotalQuantity() - order.getRemainingQuantity(),
                order.getTotalQuantity(),
                order.getRemainingQuantity());
    }

    @Override
    public void cancel(Order order) {
        // Can still cancel the unfilled remainder of a partially filled order
        order.setStatus(OrderStatus.CANCELLED);
        order.setState(new CancelledState());
        System.out.printf("  [State] Order %s CANCELLED (was PARTIALLY FILLED — %d shares filled).%n",
                order.getOrderId().substring(0, 8),
                order.getTotalQuantity() - order.getRemainingQuantity());
    }

    @Override
    public String getStateName() {
        return "PARTIALLY_FILLED";
    }
}