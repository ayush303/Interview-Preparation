package LLD.onlineStockBroker.solutions;

import LLD.onlineStockBroker.solutions.builder.OrderBuilder;
import LLD.onlineStockBroker.solutions.commands.BuyStockCommand;
import LLD.onlineStockBroker.solutions.commands.OrderInvoker;
import LLD.onlineStockBroker.solutions.commands.SellStockCommand;
import LLD.onlineStockBroker.solutions.enums.OrderStatus;
import LLD.onlineStockBroker.solutions.enums.OrderType;
import LLD.onlineStockBroker.solutions.models.Order;
import LLD.onlineStockBroker.solutions.models.Stock;
import LLD.onlineStockBroker.solutions.models.User;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * ================================================================
 * FACADE PATTERN — StockBrokerageSystem
 * ================================================================
 *
 * This is the single entry point for all client interactions.
 * It hides the complexity of the subsystems:
 * - OrderBuilder (how orders are constructed)
 * - BuyStockCommand (how orders become commands)
 * - OrderInvoker (how commands are queued, executed, undone)
 * - StockExchange (how orders are matched)
 *
 * WITHOUT Facade:
 * Client must know Builder + Command + Invoker + Exchange internals.
 * Every new client re-implements this wiring — duplication and bugs.
 *
 * WITH Facade:
 * Client calls: system.placeBuyOrder(alice, infy, 50, 1500.0, LIMIT)
 * One line. No internals exposed.
 *
 * ── WHERE OrderInvoker LIVES ────────────────────────────────────
 *
 * OrderInvoker is PER-USER here, not global.
 * WHY per-user?
 * - Alice's undoLast() should cancel Alice's last order — not Bob's.
 * - Each user has an independent command history and undo stack.
 * - Global invoker would let Alice undo Bob's order — a security flaw.
 *
 * userInvokers: Map<userId, OrderInvoker>
 * - Created lazily when a user places their first order.
 * - ConcurrentHashMap so multiple users can place orders simultaneously
 * without blocking each other at the Facade level.
 *
 * ── WHERE StockExchange LIVES ───────────────────────────────────
 *
 * StockExchange is SHARED (Singleton) — one matching engine for all users.
 * Alice's buy order and Bob's sell order must be in the SAME order book.
 * The Facade fetches it via StockExchange.getInstance().
 *
 * ── ALSO Singleton ──────────────────────────────────────────────
 *
 * StockBrokerageSystem itself is also a Singleton.
 * WHY? There should be one brokerage per application — all users share
 * the same registered stocks and the same underlying exchange.
 */
public class StockBrokerageSystem {

    // ---- Singleton ----
    private static volatile StockBrokerageSystem instance;

    // ---- Subsystems ----

    /**
     * The matching engine — shared across ALL users.
     * Singleton ensures one order book for the entire system.
     */
    private final StockExchange exchange;

    /**
     * Per-user invokers — each user has their own command queue and undo stack.
     * ConcurrentHashMap: multiple users can place orders without blocking each
     * other.
     */
    final Map<String, OrderInvoker> userInvokers;

    /**
     * Registered stocks on this exchange.
     * In production: fetched from a stock registry / market data service.
     */
    private final Map<String, Stock> listedStocks;

    /**
     * Registered users of the brokerage.
     * In production: fetched from user service / auth system.
     */
    private final Map<String, User> registeredUsers;

    private StockBrokerageSystem() {
        this.exchange = StockExchange.getInstance(); // shared Singleton
        this.userInvokers = new ConcurrentHashMap<>();
        this.listedStocks = new ConcurrentHashMap<>();
        this.registeredUsers = new ConcurrentHashMap<>();
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

    // ----------------------------------------------------------------
    // Registration
    // ----------------------------------------------------------------

    /**
     * Register a user with the brokerage.
     * Creates their personal OrderInvoker at registration time.
     *
     * WHY create invoker at registration (not lazily on first order)?
     * Eager creation means the invoker is always ready — no null checks
     * scattered across placeBuyOrder / placeSellOrder / cancelLast.
     * Fails fast: if a user places an order before registering, they get
     * a clear IllegalArgumentException, not a NullPointerException.
     */
    public void registerUser(User user) {
        registeredUsers.put(user.getUserId(), user);
        userInvokers.put(user.getUserId(), new OrderInvoker()); // one invoker per user
        System.out.printf("[System] User registered: %s (balance: ₹%.2f)%n",
                user.getName(), user.getAccount().getBalance());
    }

    /**
     * List a stock on the exchange.
     * Only listed stocks can be traded.
     */
    public void listStock(Stock stock) {
        listedStocks.put(stock.getSymbol(), stock);
        System.out.printf("[System] Stock listed: %s @ ₹%.2f%n",
                stock.getSymbol(), stock.getPrice());
    }

    // ----------------------------------------------------------------
    // Order Placement — the core Facade methods
    // ----------------------------------------------------------------

    /**
     * Place a buy order. Internally:
     * 1. Validates user is registered + stock is listed
     * 2. Builds the Order via OrderBuilder ← Builder Pattern
     * 3. Wraps in BuyStockCommand ← Command Pattern
     * 4. Submits + executes via user's OrderInvoker ← Command Pattern (Invoker)
     *
     * The caller never touches OrderBuilder, BuyStockCommand, or OrderInvoker.
     *
     * @param user      the buyer
     * @param stock     the stock to buy
     * @param quantity  number of shares
     * @param price     limit price (ignored for MARKET orders)
     * @param orderType LIMIT or MARKET
     * @return the created Order (for reference, e.g., to display order ID to user)
     */
    public Order placeBuyOrder(User user, Stock stock, int quantity,
            double price, OrderType orderType) {
        validateUserAndStock(user, stock);

        // Step 1: Build the order — Builder Pattern
        OrderBuilder builder = new OrderBuilder()
                .forUser(user)
                .withStock(stock)
                .buy(quantity);

        Order order = (orderType == OrderType.MARKET)
                ? builder.atMarketPrice().build()
                : builder.withLimit(price).build();

        // Step 2: Wrap in command — Command Pattern
        BuyStockCommand command = new BuyStockCommand(
                user.getAccount(), order);

        // Step 3: Submit and immediately execute via this user's invoker
        OrderInvoker invoker = userInvokers.get(user.getUserId());
        invoker.submit(command);
        invoker.executeNext(); // immediate execution (not batch)

        return order;
    }

    /**
     * Place a sell order. Mirrors placeBuyOrder exactly but for the sell side.
     */
    public Order placeSellOrder(User user, Stock stock, int quantity,
            double price, OrderType orderType) {
        validateUserAndStock(user, stock);

        OrderBuilder builder = new OrderBuilder()
                .forUser(user)
                .withStock(stock)
                .sell(quantity);

        Order order = (orderType == OrderType.MARKET)
                ? builder.atMarketPrice().build()
                : builder.withLimit(price).build();

        SellStockCommand command = new SellStockCommand(
                user.getAccount(), order);

        OrderInvoker invoker = userInvokers.get(user.getUserId());
        invoker.submit(command);
        invoker.executeNext();

        return order;
    }

    // ----------------------------------------------------------------
    // Batch Placement — submit many, execute at once (e.g., pre-market)
    // ----------------------------------------------------------------

    /**
     * Submit a buy order to the queue WITHOUT executing it yet.
     * Used for pre-market order batching.
     * Call executePendingOrders(user) when ready to fire all at once.
     */
    public Order queueBuyOrder(User user, Stock stock, int quantity,
            double price, OrderType orderType) {
        validateUserAndStock(user, stock);

        OrderBuilder builder = new OrderBuilder().forUser(user).withStock(stock).buy(quantity);
        Order order = (orderType == OrderType.MARKET)
                ? builder.atMarketPrice().build()
                : builder.withLimit(price).build();

        userInvokers.get(user.getUserId())
                .submit(new BuyStockCommand(user.getAccount(), order));

        return order;
    }

    public Order queueSellOrder(User user, Stock stock, int quantity,
            double price, OrderType orderType) {
        validateUserAndStock(user, stock);

        OrderBuilder builder = new OrderBuilder().forUser(user).withStock(stock).sell(quantity);
        Order order = (orderType == OrderType.MARKET)
                ? builder.atMarketPrice().build()
                : builder.withLimit(price).build();

        userInvokers.get(user.getUserId())
                .submit(new SellStockCommand(user.getAccount(), order));

        return order;
    }

    /**
     * Execute ALL pending (queued) orders for a user in FIFO order.
     * Use case: fire all pre-market orders at 9:15 AM market open.
     */
    public void executePendingOrders(User user) {
        validateUser(user);
        System.out.printf("[System] Executing all pending orders for %s...%n", user.getName());
        userInvokers.get(user.getUserId()).executeAll();
    }

    // ----------------------------------------------------------------
    // Undo / Cancel
    // ----------------------------------------------------------------

    /**
     * Cancel the user's most recently placed order (LIFO undo).
     *
     * Delegates: Invoker → Command.undo() → Exchange.cancelOrder()
     * → order.getState().cancel() ← State Pattern
     *
     * If the order is FILLED → State Pattern refuses gracefully.
     * If OPEN or PARTIALLY_FILLED → marked CANCELLED.
     */
    public void cancelLastOrder(User user) {
        validateUser(user);
        System.out.printf("[System] Cancelling last order for %s...%n", user.getName());
        userInvokers.get(user.getUserId()).undoLast();
    }

    /**
     * Cancel ALL of this user's open/partially-filled orders.
     * Already-filled orders are refused gracefully by FilledState.
     */
    public void cancelAllOrders(User user) {
        validateUser(user);
        System.out.printf("[System] Cancelling all orders for %s...%n", user.getName());
        userInvokers.get(user.getUserId()).undoAll();
    }

    // ----------------------------------------------------------------
    // Reporting
    // ----------------------------------------------------------------

    public void printAuditLog(User user) {
        validateUser(user);
        System.out.printf("[System] Audit log for %s:%n", user.getName());
        userInvokers.get(user.getUserId()).printAuditLog();
    }

    public void printPortfolio(User user) {
        validateUser(user);
        System.out.printf("[System] Portfolio for %s: %s%n",
                user.getName(), user.getAccount());
    }

    public Stock getStock(String symbol) {
        Stock stock = listedStocks.get(symbol);
        if (stock == null)
            throw new IllegalArgumentException("Stock not listed: " + symbol);
        return stock;
    }

    // ----------------------------------------------------------------
    // Validation helpers
    // ----------------------------------------------------------------

    private void validateUser(User user) {
        if (!registeredUsers.containsKey(user.getUserId())) {
            throw new IllegalArgumentException(
                    "User not registered: " + user.getName() + ". Call registerUser() first.");
        }
    }

    private void validateUserAndStock(User user, Stock stock) {
        validateUser(user);
        if (!listedStocks.containsKey(stock.getSymbol())) {
            throw new IllegalArgumentException(
                    "Stock not listed on this exchange: " + stock.getSymbol());
        }
    }
}