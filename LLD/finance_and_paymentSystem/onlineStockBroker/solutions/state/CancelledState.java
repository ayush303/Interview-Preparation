package LLD.finance_and_paymentSystem.onlineStockBroker.solutions.state;

import LLD.finance_and_paymentSystem.onlineStockBroker.solutions.models.Order;

public class CancelledState implements OrderState {

    @Override
    public void handle(Order order) {
        System.out.printf("  [State] Order %s is CANCELLED — no action possible.%n",
                order.getOrderId().substring(0, 8));
    }

    @Override
    public void cancel(Order order) {
        System.out.printf("  [State] Order %s is already CANCELLED.%n",
                order.getOrderId().substring(0, 8));
    }

    @Override
    public String getStateName() {
        return "CANCELLED";
    }
}