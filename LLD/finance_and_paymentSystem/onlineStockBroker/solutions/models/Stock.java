package LLD.finance_and_paymentSystem.onlineStockBroker.solutions.models;

import java.util.ArrayList;
import java.util.List;

import LLD.finance_and_paymentSystem.onlineStockBroker.solutions.observer.StockObserver;

/**
 * Represents a publicly traded stock.
 * price is volatile so that the Last Traded Price (LTP) updated in one thread
 * is always visible to other threads reading it (no stale cache).
 */

public class Stock {
    private final String symbol;
    private double price;
    private final List<StockObserver> observers = new ArrayList<>();

    public Stock(String symbol, double initialPrice) {
        this.symbol = symbol;
        this.price = initialPrice;
    }

    public String getSymbol() {
        return symbol;
    }

    public double getPrice() {
        return price;
    }

    /**
     * Updated after every successful trade execution to reflect the LTP.
     * volatile write ensures other threads immediately see the new price.
     */
    public void setPrice(double newPrice) {
        if (this.price != newPrice) {
            this.price = newPrice;
            notifyObservers();
        }
    }

    public void addObserver(StockObserver observer) {
        observers.add(observer);
    }

    public void removeObserver(StockObserver observer) {
        observers.remove(observer);
    }

    private void notifyObservers() {
        for (StockObserver observer : observers) {
            observer.update(this);
        }
    }
}
