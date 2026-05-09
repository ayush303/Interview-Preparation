package LLD.onlineStockBroker.solutions.state;

import LLD.onlineStockBroker.solutions.models.Order;

public class CancelledState implements OrderState {

    @Override
    public void handle(Order order) {
        System.out.println("Order is cancelled.");
    }

    @Override
    public void cancel(Order order) {
        System.out.println("Order is already cancelled.");
    }
    
}
