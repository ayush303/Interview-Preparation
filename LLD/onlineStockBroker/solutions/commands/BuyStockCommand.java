package LLD.onlineStockBroker.solutions.commands;

import LLD.onlineStockBroker.solutions.StockExchange;
import LLD.onlineStockBroker.solutions.enums.OrderType;
import LLD.onlineStockBroker.solutions.exceptions.InsufficientFundsException;
import LLD.onlineStockBroker.solutions.models.Account;
import LLD.onlineStockBroker.solutions.models.Order;

/**
 * Concrete Command: places a buy order on the StockExchange.
 *
 * DESIGN DECISIONS (merged best of both versions):
 *
 * 1. Account injected explicitly in constructor (from original).
 * WHY: Avoids the Law of Demeter violation of drilling through
 * order → user → account. The command needs the account directly
 * for pre-validation; passing it explicitly makes the dependency clear.
 *
 * 2. StockExchange injected via constructor (from optimized version).
 * WHY: Original used StockExchange.getInstance() inside the constructor —
 * a hidden dependency. Injecting it makes the class testable (you can pass
 * a mock exchange in unit tests) and honest about what it needs.
 *
 * 3. execute() throws InsufficientFundsException (from original).
 * WHY: A silent return lets bad orders slip through if the caller forgets
 * to check a return value. An exception forces the caller (OrderInvoker)
 * to explicitly decide how to handle the failure.
 * Pre-validation here is a fast-fail: we reject before touching the exchange.
 * StockExchange also does a defensive final check — belt AND suspenders.
 *
 * 4. undo() added (from optimized version).
 * WHY: Without undo(), Command Pattern adds zero value over a direct
 * exchange.placeBuyOrder() call. undo() = cancelOrder(), which delegates
 * to State Pattern — FilledState refuses; OpenState proceeds.
 *
 * 5. getRemainingQuantity() for cost estimate (from optimized version).
 * WHY: If an order was re-used or partially constructed, getQuantity()
 * (total) would overestimate the cost. getRemainingQuantity() is always
 * the accurate "how much still needs to be bought" value.
 */
public class BuyStockCommand implements OrderCommand {
    private final Account account; // Explicit dep — from original (avoids Law of Demeter)
    private final Order order;
    private final StockExchange stockExchange;

    /**
     * @param account The account of the user placing the order, needed for
     *                pre-checking funds and later
     * @param order   The fully built Order (use Order.Builder to construct)
     */
    public BuyStockCommand(Account account, Order order) {
        this.account = account;
        this.order = order;
        this.stockExchange = StockExchange.getInstance();
    }

    /**
     * Places the buy order on the exchange after pre-validating funds.
     *
     * PRE-VALIDATION STRATEGY:
     * LIMIT order: estimatedCost = limitPrice × remainingQuantity.
     * If account balance < estimatedCost → throw immediately.
     * This is exact for limit orders since price is known upfront.
     *
     * MARKET order: price is unknown until matching — we cannot pre-validate.
     * We skip the check and let StockExchange handle it.
     * In production, a margin/reserve system would handle this.
     *
     * WHY pre-validate here AND in StockExchange?
     * Command pre-check: fast fail before the order even enters the book.
     * Exchange final check: defensive guard in case account state changed
     * between command creation and execution (race condition window).
     *
     * @throws InsufficientFundsException if balance is insufficient for a LIMIT
     *                                    buy.
     */
    @Override
    public void execute() {
        // For market order, we can't pre-check funds perfectly.
        // For limit order, we can pre-authorize the amount.
        double estimatedCost = order.getRemainingQuantity() * order.getLimitPrice();
        double currentBalance = account.getBalance();
        if (order.getType() == OrderType.LIMIT && account.getBalance() < estimatedCost) {
            throw new InsufficientFundsException(
                    "Insufficient funds to place LIMIT buy order for " + order.getUser().getName(),
                    estimatedCost,
                    currentBalance);
        }
        System.out.printf("Placing BUY order %s for %d shares of %s.%n", order.getOrderId(),
                order.getRemainingQuantity(),
                order.getStock());
        stockExchange.placeBuyOrder(order);
    }

    /**
     * Cancels the buy order.
     *
     * Delegates to StockExchange.cancelOrder() which delegates to State Pattern:
     * OpenState → marks CANCELLED, removed from book on next match cycle
     * PartiallyFilledState → marks CANCELLED (unfilled remainder discarded)
     * FilledState → refuses; cannot cancel a completed trade
     * CancelledState → no-op; already cancelled
     */
    @Override
    public void undo() {
        System.out.printf("[Command] Undoing: %s%n", getDescription());
        stockExchange.cancelOrder(order);
    }

    @Override
    public String getDescription() {
        return String.format("BUY %d x %s @₹%.2f by %s [orderId: %s]",
                order.getRemainingQuantity(),
                order.getStock().getSymbol(),
                order.getLimitPrice(),
                order.getUser().getName(),
                order.getOrderId().substring(0, 8));
    }

    public Order getOrder() {
        return order;
    }

    public Account getAccount() {
        return account;
    }
}
