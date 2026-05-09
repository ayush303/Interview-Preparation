package LLD.onlineStockBroker.solutions.strategy;

import LLD.onlineStockBroker.solutions.models.Order;

public interface ExecutionStrategy {
    boolean canExecute(Order order, double marketPrice);
}
