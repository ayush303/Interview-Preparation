package LLD.onlineStockBroker.solutions.observer;

import LLD.onlineStockBroker.solutions.models.Stock;

public interface StockObserver {
    void update(Stock stock);
}
