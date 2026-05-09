package LLD.onlineStockBroker.solutions.strategy;

import LLD.onlineStockBroker.solutions.models.Order;

public class MarketOrderStrategy implements ExecutionStrategy {
    @Override
    public boolean canExecute(Order order, double marketPrice) {
        return true; // Market orders can always execute
    }
}
