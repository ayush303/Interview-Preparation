package LLD.finance_and_paymentSystem.onlineStockBroker.solutions.observer;

import LLD.finance_and_paymentSystem.onlineStockBroker.solutions.models.Stock;

public interface StockObserver {
    void update(Stock stock);
}
