package LLD.onlineStockBroker.solutions.state;

import LLD.onlineStockBroker.solutions.models.Order;

public interface OrderState {
    void handle(Order order);

    void cancel(Order order);
}
