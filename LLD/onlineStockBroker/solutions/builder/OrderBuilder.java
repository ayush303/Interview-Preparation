package LLD.onlineStockBroker.solutions.builder;

import java.util.UUID;

import LLD.onlineStockBroker.solutions.enums.OrderType;
import LLD.onlineStockBroker.solutions.enums.TransactionType;
import LLD.onlineStockBroker.solutions.models.Order;
import LLD.onlineStockBroker.solutions.models.Stock;
import LLD.onlineStockBroker.solutions.models.User;
import LLD.onlineStockBroker.solutions.strategy.LimitOrderStrategy;
import LLD.onlineStockBroker.solutions.strategy.MarketOrderStrategy;

public class OrderBuilder {
    private User user;
    private Stock stock;
    private OrderType type;
    private TransactionType transactionType;
    private int quantity;
    private double price;

    public OrderBuilder forUser(User user) {
        this.user = user;
        return this;
    }

    public OrderBuilder withStock(Stock stock) {
        this.stock = stock;
        return this;
    }

    public OrderBuilder buy(int quantity) {
        this.transactionType = TransactionType.BUY;
        this.quantity = quantity;
        return this;
    }

    public OrderBuilder sell(int quantity) {
        this.transactionType = TransactionType.SELL;
        this.quantity = quantity;
        return this;
    }

    public OrderBuilder atMarketPrice() {
        this.type = OrderType.MARKET;
        this.price = 0; // Not needed for market order
        return this;
    }

    public OrderBuilder withLimit(double limitPrice) {
        this.type = OrderType.LIMIT;
        this.price = limitPrice;
        return this;
    }

    public Order build() {
        return new Order(
                UUID.randomUUID().toString(),
                user,
                stock,
                type,
                quantity,
                price,
                type == OrderType.MARKET ? new MarketOrderStrategy() : new LimitOrderStrategy(transactionType),
                user);
    }
}
