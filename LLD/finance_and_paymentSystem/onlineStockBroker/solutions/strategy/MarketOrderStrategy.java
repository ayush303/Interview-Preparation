package LLD.finance_and_paymentSystem.onlineStockBroker.solutions.strategy;

import LLD.finance_and_paymentSystem.onlineStockBroker.solutions.enums.TransactionType;
import LLD.finance_and_paymentSystem.onlineStockBroker.solutions.models.Order;

/**
 * ExecutionStrategy for MARKET orders.
 *
 * MARKET orders are "price takers" — they accept whatever price the market
 * offers.
 * They should ALWAYS be matched before LIMIT orders of the same side.
 *
 * DESIGN CHANGE from my previous OrderPricingStrategy version:
 * Before: MarketOrderStrategy(TransactionType) — stored TransactionType in
 * constructor.
 * Now: MarketOrderStrategy() — NO constructor args. STATELESS.
 *
 * WHY stateless?
 * Original MarketOrderStrategy had no constructor args. Stateless strategies
 * can be shared (flyweight) — one instance per JVM is enough.
 * TransactionType is accessible via order.getTransactionType() when needed.
 * This avoids storing data the strategy can already get from the Order.
 *
 * USAGE: new MarketOrderStrategy() — same for BUY and SELL market orders.
 * The Order itself carries the TransactionType; strategy reads it when needed.
 */
public class MarketOrderStrategy implements ExecutionStrategy {

    /**
     * [FROM ORIGINAL — unchanged]
     * Market orders execute at any price — always return true.
     */
    @Override
    public boolean canExecute(Order order, double marketPrice) {
        return true; // Market orders have no price restriction
    }

    /**
     * [NEW]
     * Sentinel values for PriorityQueue ordering.
     *
     * Instead of storing TransactionType in the constructor, we read it from
     * the Order at call time. This keeps the strategy stateless.
     *
     * MARKET BUY → Double.MAX_VALUE: tops the max-heap, matched before all limit
     * buys
     * MARKET SELL → 0.0: tops the min-heap, matched before all limit sells
     */
    @Override
    public double getEffectivePrice(Order order) {
        return order.getTransactionType() == TransactionType.BUY
                ? Double.MAX_VALUE
                : 0.0;
    }

    /**
     * [NEW]
     * Market orders trade at the current Last Traded Price.
     * The price is unknown at order placement — only known at match time.
     * That's why we accept currentMarketPrice as a parameter (not stored).
     */
    @Override
    public double getTradePrice(Order order, double currentMarketPrice) {
        return currentMarketPrice; // Price taker: accepts whatever the market offers
    }
}