package LLD.onlineStockBroker.solutions.strategy;

import LLD.onlineStockBroker.solutions.models.Order;

/**
 * Strategy Pattern: encapsulates how an order determines its price.
 *
 * WHY Strategy Pattern here instead of inline if/else on Order?
 *
 * Without strategy, Order.getEffectivePrice() looks like:
 * if (type == MARKET && side == BUY) return Double.MAX_VALUE;
 * if (type == MARKET && side == SELL) return 0.0;
 * return limitPrice;
 *
 * This means Order knows about:
 * - Its own type (MARKET/LIMIT)
 * - Its own side (BUY/SELL)
 * - Price sentinel logic for the PriorityQueue
 * - Trade execution price logic
 *
 * That's too many reasons to change. Strategy Pattern extracts price logic
 * into dedicated classes — Single Responsibility Principle.
 *
 * Two concerns this strategy handles:
 *
 * 1. getEffectivePrice() — used by PriorityQueue comparator for ordering.
 * MARKET BUY → Double.MAX_VALUE (always tops the max-heap)
 * MARKET SELL → 0.0 (always tops the min-heap)
 * LIMIT → the specified limitPrice
 *
 * 2. getTradePrice(marketPrice) — the actual price at which a trade executes.
 * MARKET → current stock LTP (last traded price)
 * LIMIT → the specified limitPrice
 *
 * These two concerns are different:
 * - effective price is for heap ordering (who matches first)
 * - trade price is for accounting (how much money changes hands)
 * Having one strategy per order type handles both cleanly.
 */
public interface ExecutionStrategy {

    /**
     * [FROM ORIGINAL — unchanged signature]
     *
     * Returns true if this order CAN execute at the given market price.
     *
     * MARKET → always true (no price restriction)
     * LIMIT BUY → true only if marketPrice <= order.getLimitPrice()
     * (buyer won't pay MORE than their stated limit)
     * LIMIT SELL → true only if marketPrice >= order.getLimitPrice()
     * (seller won't accept LESS than their stated limit)
     *
     * @param order       the order being evaluated
     * @param marketPrice the proposed execution price (from determineTradePrice)
     */
    boolean canExecute(Order order, double marketPrice);

    /**
     * [NEW — needed by PriorityQueue comparator in optimized StockExchange]
     *
     * Returns the effective price used for heap ordering.
     * This is NOT the trade price — it's purely for "who goes first" in the book.
     *
     * @param order the order whose position in the heap is being determined
     */
    double getEffectivePrice(Order order);

    /**
     * [NEW — simplifies determineTradePrice() in StockExchange]
     *
     * Returns the actual price at which this order will trade.
     *
     * @param order              the order being executed
     * @param currentMarketPrice the stock's LTP at time of execution
     */
    double getTradePrice(Order order, double currentMarketPrice);
}