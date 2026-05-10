package LLD.finance_and_paymentSystem.onlineStockBroker.solutions.state;

import LLD.finance_and_paymentSystem.onlineStockBroker.solutions.models.Order;

public class FilledState implements OrderState {

    @Override
    public void handle(Order order) {
        System.out.printf("  [State] Order %s is fully FILLED — %d/%d shares traded.%n",
                order.getOrderId().substring(0, 8), order.getTotalQuantity(), order.getTotalQuantity());
    }

    @Override
    public void cancel(Order order) {
        // Cannot cancel what is already done
        System.out.printf("  [State] Cannot cancel order %s — already FILLED.%n",
                order.getOrderId().substring(0, 8));
    }

    @Override
    public String getStateName() {
        return "FILLED";
    }
}