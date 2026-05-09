package LLD.onlineStockBroker.solutions;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import LLD.onlineStockBroker.solutions.commands.BuyStockCommand;
import LLD.onlineStockBroker.solutions.commands.OrderCommand;
import LLD.onlineStockBroker.solutions.commands.SellStockCommand;
import LLD.onlineStockBroker.solutions.models.Order;
import LLD.onlineStockBroker.solutions.models.Stock;
import LLD.onlineStockBroker.solutions.models.User;

public class StockBrokerageSystem {
    private static volatile StockBrokerageSystem instance;
    private final Map<String, User> users;
    private final Map<String, Stock> stocks;

    private StockBrokerageSystem() {
        this.users = new ConcurrentHashMap<>();
        this.stocks = new ConcurrentHashMap<>();
    }

    public static StockBrokerageSystem getInstance() {
        if (instance == null) {
            synchronized (StockBrokerageSystem.class) {
                if (instance == null) {
                    instance = new StockBrokerageSystem();
                }
            }
        }
        return instance;
    }

    public User registerUser(String name, double initialAmount) {
        User user = new User(name, initialAmount);
        users.put(user.getUserId(), user);
        return user;
    }

    public Stock addStock(String symbol, double initialPrice) {
        Stock stock = new Stock(symbol, initialPrice);
        stocks.put(stock.getSymbol(), stock);
        return stock;
    }

    public void placeBuyOrder(Order order) {
        User user = order.getUser();
        OrderCommand command = new BuyStockCommand(user.getAccount(), order);
        command.execute();
    }

    public void placeSellOrder(Order order) {
        User user = order.getUser();
        OrderCommand command = new SellStockCommand(user.getAccount(), order);
        command.execute();
    }

    public void cancelOrder(Order order) {
        order.cancel();
    }
}
