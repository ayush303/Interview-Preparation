package LLD.finance_and_paymentSystem.onlineStockBroker.solutions.commands;

/**
 * Command Pattern interface for stock exchange operations.
 *
 * WHY Command Pattern here?
 *
 * Without it:
 * Client → StockExchange.placeBuyOrder(order) ← tight coupling
 * Client → StockExchange.placeSellOrder(order) ← client knows exchange
 * internals
 *
 * With it:
 * Client → OrderInvoker.submit(new BuyStockCommand(...))
 * Invoker → command.execute() ← invoker is completely decoupled
 *
 * This unlocks:
 * ✓ Command queuing (batch execution, rate limiting)
 * ✓ Audit logging (log every execute() call with timestamp, user, order
 * details)
 * ✓ Undo (cancel the order via undo())
 * ✓ Replay (re-run all commands for disaster recovery or testing)
 * ✓ Scheduling (execute at market open, execute after N milliseconds)
 *
 * undo() is the critical addition over a simple Runnable.
 * For a stock exchange, undo() = cancel the order in the exchange.
 */

public interface OrderCommand {
    /**
     * Execute the command — place the order in the exchange.
     */
    void execute();

    /**
     * Undo the command — cancel the order in the exchange.
     * Only meaningful if the order is still OPEN or PARTIALLY_FILLED.
     * FilledState.cancel() will refuse gracefully.
     */
    void undo();

    /**
     * Human-readable description of this command for audit logging.
     */
    String getDescription();
}
