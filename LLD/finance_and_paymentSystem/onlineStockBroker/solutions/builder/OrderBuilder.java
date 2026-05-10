package LLD.finance_and_paymentSystem.onlineStockBroker.solutions.builder;

import LLD.finance_and_paymentSystem.onlineStockBroker.solutions.enums.OrderType;
import LLD.finance_and_paymentSystem.onlineStockBroker.solutions.enums.TransactionType;
import LLD.finance_and_paymentSystem.onlineStockBroker.solutions.models.Order;
import LLD.finance_and_paymentSystem.onlineStockBroker.solutions.models.Stock;
import LLD.finance_and_paymentSystem.onlineStockBroker.solutions.models.User;
import LLD.finance_and_paymentSystem.onlineStockBroker.solutions.strategy.ExecutionStrategy;
import LLD.finance_and_paymentSystem.onlineStockBroker.solutions.strategy.LimitOrderStrategy;
import LLD.finance_and_paymentSystem.onlineStockBroker.solutions.strategy.MarketOrderStrategy;

/**
 * ================================================================
 * BUILDER PATTERN — Standalone OrderBuilder class
 * ================================================================
 *
 * WHY standalone class instead of inner Order.Builder?
 *
 * Inner static builder (my version):
 * new
 * Order.Builder().user(alice).stock(infy).side(BUY).type(LIMIT).limitPrice(1500).quantity(50).build()
 *
 * Standalone builder (this version):
 * new
 * OrderBuilder().forUser(alice).withStock(infy).buy(50).withLimit(1500).build()
 *
 * The standalone version reads like an English sentence:
 * "for user Alice, with stock INFY, buy 50 shares with limit 1500"
 *
 * Additionally, .buy(qty) and .sell(qty) combine TransactionType + quantity
 * into a single meaningful call. The inner builder needed two separate calls
 * (.side(BUY) and .quantity(50)) — more calls, less readable.
 *
 * Standalone also follows the classic GoF Builder pattern more faithfully,
 * and is easier to find in a large codebase (it's a top-level file, not
 * buried inside Order.java).
 *
 * ADDITIONS over original:
 * 1. Validation in build() — catches missing fields and invalid combinations
 * 2. Strategy is created inside build() with full context (TransactionType +
 * price)
 * Original created strategy before price was always set; now build() owns this.
 * 3. UUID generated here (was in build() in original too — kept as is)
 */
public class OrderBuilder {

    // --- Fields set by fluent methods ---
    private User user;
    private Stock stock;
    private OrderType type;
    private TransactionType transactionType;
    private int quantity;
    private double price;

    // --- Fluent setters ---

    /**
     * Set the user placing the order.
     * Named "forUser" (not "user") — reads naturally: "for user Alice".
     */
    public OrderBuilder forUser(User user) {
        this.user = user;
        return this;
    }

    /**
     * Set the stock to trade.
     * Named "withStock" — reads: "with stock INFY".
     */
    public OrderBuilder withStock(Stock stock) {
        this.stock = stock;
        return this;
    }

    /**
     * Set as a BUY order for the given quantity.
     *
     * WHY combine side + quantity in one method?
     * .buy(50) encodes two facts in one readable call.
     * Separating them (.side(BUY).quantity(50)) requires remembering to call both
     * and allows inconsistent state (side set but quantity not, or vice versa).
     */
    public OrderBuilder buy(int quantity) {
        this.transactionType = TransactionType.BUY;
        this.quantity = quantity;
        return this;
    }

    /**
     * Set as a SELL order for the given quantity.
     */
    public OrderBuilder sell(int quantity) {
        this.transactionType = TransactionType.SELL;
        this.quantity = quantity;
        return this;
    }

    /**
     * Set as a MARKET order (execute immediately at best available price).
     * price field intentionally left at 0 — not needed for market orders.
     * The strategy will return the stock's LTP at execution time instead.
     */
    public OrderBuilder atMarketPrice() {
        this.type = OrderType.MARKET;
        this.price = 0.0; // Explicitly 0 — MARKET price is determined at match time
        return this;
    }

    /**
     * Set as a LIMIT order at the specified price.
     * For BUY: maximum price willing to pay.
     * For SELL: minimum price willing to accept.
     */
    public OrderBuilder withLimit(double limitPrice) {
        this.type = OrderType.LIMIT;
        this.price = limitPrice;
        return this;
    }

    /**
     * Validate all fields and build the Order.
     *
     * VALIDATION ADDITIONS over original (which had none):
     * 1. Required fields: user, stock, type, transactionType must all be set.
     * 2. quantity must be > 0.
     * 3. LIMIT orders must have price > 0.
     * (MARKET orders legitimately have price = 0 — allowed.)
     * 4. Must call buy() or sell() before atMarketPrice() or withLimit()
     * — enforced by checking transactionType != null.
     *
     * WHY validate here and not in Order's constructor?
     * Builder is the last checkpoint before object creation.
     * Validating in the constructor would require the constructor to be public
     * and would duplicate validation logic if the constructor is called elsewhere.
     * Build-time validation keeps Order's constructor simple and private.
     *
     * STRATEGY CREATION:
     * Strategy is created inside build() (not in buy/sell/atMarketPrice/withLimit)
     * because it needs BOTH transactionType AND price to be fully set.
     * Creating it earlier risks constructing a strategy with incomplete state.
     */
    public Order build() {
        // ---- Required field validation ----
        if (user == null) {
            throw new IllegalStateException("OrderBuilder: user is required. Call forUser(user).");
        }
        if (stock == null) {
            throw new IllegalStateException("OrderBuilder: stock is required. Call withStock(stock).");
        }
        if (transactionType == null) {
            throw new IllegalStateException(
                    "OrderBuilder: transaction type is required. Call buy(qty) or sell(qty).");
        }
        if (type == null) {
            throw new IllegalStateException(
                    "OrderBuilder: order type is required. Call atMarketPrice() or withLimit(price).");
        }
        if (quantity <= 0) {
            throw new IllegalStateException(
                    "OrderBuilder: quantity must be > 0. Got: " + quantity);
        }

        // ---- Business rule validation ----
        if (type == OrderType.LIMIT && price <= 0) {
            throw new IllegalStateException(
                    "OrderBuilder: limitPrice must be > 0 for LIMIT orders. " +
                            "For MARKET orders, use atMarketPrice() instead of withLimit().");
        }

        /**
         * CHANGED: was OrderPricingStrategy, now ExecutionStrategy.
         *
         * MarketOrderStrategy() — no args (stateless, reads TransactionType from Order)
         * LimitOrderStrategy(transactionType) — keeps original constructor, no
         * limitPrice stored
         *
         * Both are created in build() so all state (type, transactionType, price) is
         * finalized.
         */
        ExecutionStrategy strategy = (type == OrderType.MARKET)
                ? new MarketOrderStrategy() // stateless — no TransactionType needed here
                : new LimitOrderStrategy(transactionType); // keeps original constructor signature

        return new Order(
                user,
                stock,
                transactionType,
                type,
                price,
                quantity,
                strategy);
    }
}