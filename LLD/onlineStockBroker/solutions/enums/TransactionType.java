package LLD.onlineStockBroker.solutions.enums;

/**
 * Replaces OrderSide from the optimized version.
 * Kept as TransactionType to match the original codebase naming.
 *
 * Used by:
 * - OrderBuilder.buy() / sell() → sets the transaction direction
 * - MarketOrderStrategy → determines effective price sentinel
 * - LimitOrderStrategy → determines effective price (same as limitPrice)
 * - BuyStockCommand / SellStockCommand → pre-validation logic
 */
public enum TransactionType {
    BUY,
    SELL
}
