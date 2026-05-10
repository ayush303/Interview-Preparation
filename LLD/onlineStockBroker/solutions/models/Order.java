package LLD.onlineStockBroker.solutions.models;

import LLD.onlineStockBroker.solutions.enums.TransactionType;
import LLD.onlineStockBroker.solutions.enums.OrderStatus;
import LLD.onlineStockBroker.solutions.enums.OrderType;
import LLD.onlineStockBroker.solutions.state.OpenState;
import LLD.onlineStockBroker.solutions.state.OrderState;
import LLD.onlineStockBroker.solutions.strategy.ExecutionStrategy;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Represents a single buy or sell order placed by a user.
 *
 * KEY ADDITIONS over original:
 * 1. OrderSide (BUY/SELL) — needed to compute effective price for MARKET
 * orders.
 * 2. remainingQuantity (AtomicInteger) — enables partial fills.
 * 3. getEffectivePrice() — core fix that makes MARKET orders work correctly in
 * PriorityQueue.
 * 4. fill() — decrements remaining qty and auto-updates status
 * (PARTIALLY_FILLED or FILLED).
 * 5. timestamp — enables FIFO ordering when two orders have the same price.
 */
public class Order {

    private final String orderId;
    private final User user;
    private final Stock stock;
    private final TransactionType side; // BUY or SELL
    private final OrderType type; // MARKET or LIMIT
    private final double limitPrice; // Only meaningful for LIMIT orders (0 for MARKET)
    private final int totalQuantity; // Original quantity requested — never changes
    private final AtomicInteger remainingQuantity; // Decremented on each partial/full fill
    private volatile OrderStatus status; // volatile: read by multiple threads during matching
    private OrderState state; // State Pattern: tracks current lifecycle stage
    private final long timestamp; // Nanosecond timestamp for FIFO tie-breaking
    /**
     * The execution strategy injected by OrderBuilder.
     * Handles canExecute(), getEffectivePrice(), getTradePrice() for this order's
     * type.
     */
    private final ExecutionStrategy executionStrategy;

    public Order(User user, Stock stock, TransactionType side, OrderType type, double limitPrice, int quantity,
            ExecutionStrategy executionStrategy) {
        this.orderId = UUID.randomUUID().toString();
        this.user = user;
        this.stock = stock;
        this.side = side;
        this.type = type;
        this.limitPrice = limitPrice;
        this.totalQuantity = quantity;
        this.remainingQuantity = new AtomicInteger(quantity);
        this.status = OrderStatus.OPEN;
        this.state = new OpenState();
        this.timestamp = System.nanoTime(); // Nanos for high-resolution FIFO ordering
        this.executionStrategy = executionStrategy;
    }

    /**
     * CHANGED: delegates to strategy instead of inline sentinel logic.
     *
     * Before (my version — inline):
     * if (type == MARKET) return transactionType == BUY ? MAX_VALUE : 0.0;
     * return limitPrice;
     *
     * After (strategy delegation — Order has no price logic):
     * return executionStrategy.getEffectivePrice(this);
     *
     * Adding a new order type (STOP_LIMIT, IOC, FOK) only requires a new strategy
     * class.
     * Order.java itself never changes. Open/Closed Principle.
     */
    public double getEffectivePrice() {
        return executionStrategy.getEffectivePrice(this);
    }

    /**
     * NEW: convenience method so StockExchange doesn't need to call
     * order.getExecutionStrategy().canExecute(order, price) every time.
     * Hides the double-dispatch behind a clean single-call API.
     */
    public boolean canExecute(double marketPrice) {
        return executionStrategy.canExecute(this, marketPrice);
    }

    /**
     * Fill this order (partially or fully) with the given quantity.
     *
     * PARTIAL FILL EXAMPLE:
     * Order: 100 shares. fill(60) called.
     * → remainingQuantity = 40, status = PARTIALLY_FILLED.
     * Order stays in the book for future matching.
     *
     * Next fill(40) called.
     * → remainingQuantity = 0, status = FILLED.
     * Order is removed from the book.
     *
     * AtomicInteger.addAndGet() is used for thread-safe decrement,
     * though in practice matchOrders() holds the symbol lock when calling this.
     *
     * @param quantityToFill shares to fill in this execution
     * @return actual quantity filled (may be less than requested if not enough
     *         remaining)
     */
    public int fill(int quantityToFill) {
        int actualFill = Math.min(quantityToFill, remainingQuantity.get());
        int newRemaining = remainingQuantity.addAndGet(-actualFill);

        // Status auto-update based on remaining quantity
        if (newRemaining == 0) {
            this.status = OrderStatus.FILLED;
        } else {
            this.status = OrderStatus.PARTIALLY_FILLED;
        }

        return actualFill;
    }

    // --- Getters ---
    public String getOrderId() {
        return orderId;
    }

    public User getUser() {
        return user;
    }

    public Stock getStock() {
        return stock;
    }

    public TransactionType getTransactionType() {
        return side;
    }

    public OrderType getType() {
        return type;
    }

    /**
     * getLimitPrice() — needed by LimitOrderStrategy (strategy reads price from
     * Order)
     * Maps to the internal 'price' field (0 for MARKET orders, limit price for
     * LIMIT orders).
     */
    public double getLimitPrice() {
        return limitPrice;
    }

    public int getTotalQuantity() {
        return totalQuantity;
    }

    public int getRemainingQuantity() {
        return remainingQuantity.get();
    }

    public OrderStatus getStatus() {
        return status;
    }

    public void setStatus(OrderStatus s) {
        this.status = s;
    }

    public OrderState getState() {
        return state;
    }

    public void setState(OrderState s) {
        this.state = s;
    }

    public long getTimestamp() {
        return timestamp;
    }

    // State pattern methods
    public void cancel() {
        state.cancel(this);
    }

    @Override
    public String toString() {
        return String.format("[%s] %s %s %d shares @₹%.2f | filled: %d | status: %s",
                orderId.substring(0, 8),
                side, stock.getSymbol(),
                totalQuantity, limitPrice,
                totalQuantity - remainingQuantity.get(),
                status);
    }

    /**
     * NEW: convenience method for StockExchange.determineTradePrice().
     */
    public double getTradePrice(double currentMarketPrice) {
        return executionStrategy.getTradePrice(this, currentMarketPrice);
    }

    public ExecutionStrategy getExecutionStrategy() {
        return executionStrategy;
    }
}