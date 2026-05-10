package LLD.finance_and_paymentSystem.onlineStockBroker.solutions.strategy;

import LLD.finance_and_paymentSystem.onlineStockBroker.solutions.enums.TransactionType;
import LLD.finance_and_paymentSystem.onlineStockBroker.solutions.models.Order;

/**
 * ExecutionStrategy for LIMIT orders.
 *
 * LIMIT orders are "price setters" — they only execute at their stated price or
 * better.
 * BUY limit: will only buy if market price ≤ limit (won't overpay)
 * SELL limit: will only sell if market price ≥ limit (won't undersell)
 *
 * DESIGN: constructor keeps only TransactionType [same as original].
 * limitPrice is NOT stored here — it's read from order.getLimitPrice() when
 * needed.
 *
 * WHY not store limitPrice in the strategy?
 * Single source of truth: limitPrice lives on the Order, not duplicated in the
 * strategy.
 * The strategy is a behavior object, not a data object.
 * If an order amendment feature is added later, only Order needs updating.
 */
public class LimitOrderStrategy implements ExecutionStrategy {

    private final TransactionType type; // BUY or SELL — needed for canExecute() direction

    /**
     * [FROM ORIGINAL — unchanged constructor]
     * 
     * @param type TransactionType.BUY or SELL — determines comparison direction in
     *             canExecute()
     */
    public LimitOrderStrategy(TransactionType type) {
        this.type = type;
    }

    /**
     * [FROM ORIGINAL — unchanged logic]
     *
     * The core match gate for limit orders:
     *
     * BUY limit: can execute if proposed trade price ≤ my limit
     * "I'm willing to pay UP TO my limit price."
     * If the market is cheaper than my limit → execute.
     * If the market is MORE expensive → don't execute.
     *
     * SELL limit: can execute if proposed trade price ≥ my limit
     * "I'll only sell for AT LEAST my limit price."
     * If the market offers more than my limit → execute.
     * If the market offers less → don't execute.
     *
     * In matchOrders(), both the buy AND sell order must return true from
     * canExecute() for the trade to proceed. This cleanly enforces:
     * "Buyer won't overpay AND seller won't undersell."
     */
    @Override
    public boolean canExecute(Order order, double marketPrice) {
        if (type == TransactionType.BUY) {
            return marketPrice <= order.getLimitPrice(); // Won't pay more than limit
        } else {
            return marketPrice >= order.getLimitPrice(); // Won't accept less than limit
        }
    }

    /**
     * [NEW]
     * For heap ordering: limit price IS the effective price.
     * Higher limit buy → more aggressive buyer → sits higher in the max-heap.
     * Lower limit sell → more aggressive seller → sits lower in the min-heap.
     *
     * Gets limitPrice from Order (not stored locally — see class comment).
     */
    @Override
    public double getEffectivePrice(Order order) {
        return order.getLimitPrice(); // Limit price is both the ordering key and the price floor/ceiling
    }

    /**
     * [NEW]
     * Limit orders trade at their stated limit price.
     * currentMarketPrice is ignored — limit orders are price setters, not price
     * takers.
     * The MARKET order on the other side takes this price.
     */
    @Override
    public double getTradePrice(Order order, double currentMarketPrice) {
        return order.getLimitPrice(); // Price setter: trades at its own stated price
    }

    public TransactionType getType() {
        return type;
    }
}