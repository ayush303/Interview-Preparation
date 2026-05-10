package LLD.onlineStockBroker.solutions.enums;

/**
 * OPEN — order is active in the order book, awaiting a match.
 * PARTIALLY_FILLED — some shares have been traded; remainder still open.
 * FILLED — all shares have been traded; order is complete.
 * CANCELLED — order was cancelled or rejected before full execution.
 *
 * PARTIALLY_FILLED is new in the optimized version to support partial fills.
 */
public enum OrderStatus {
    OPEN,
    PARTIALLY_FILLED,
    FILLED,
    CANCELLED,
    FAILED
}
