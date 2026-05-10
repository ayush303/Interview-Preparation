package LLD.onlineStockBroker.solutions.commands;

import LLD.onlineStockBroker.solutions.StockExchange;
import LLD.onlineStockBroker.solutions.exceptions.InsufficientStockException;
import LLD.onlineStockBroker.solutions.models.Account;
import LLD.onlineStockBroker.solutions.models.Order;

/**
 * Concrete Command: places a sell order on the StockExchange.
 *
 * Mirrors BuyStockCommand design exactly but validates stock holdings
 * instead of cash balance before placing the order.
 *
 * Pre-validation: seller must hold at least as many shares as they intend to
 * sell.
 * This check applies to both LIMIT and MARKET sell orders (unlike buy, where
 * MARKET price is unknown — here the quantity is always known).
 */
public class SellStockCommand implements OrderCommand {
    private final Account account;
    private final Order order;
    private final StockExchange stockExchange;

    public SellStockCommand(Account account, Order order) {
        this.account = account;
        this.order = order;
        this.stockExchange = StockExchange.getInstance();
    }

    /**
     * Places the sell order after verifying the seller holds enough shares.
     *
     * Unlike BuyStockCommand, this check applies to ALL order types (LIMIT and
     * MARKET).
     * For a sell, we always know the quantity — we just don't know at what price
     * it'll fill.
     * Either way, the seller must physically hold the shares before we accept the
     * order.
     *
     * @throws InsufficientStockException if account doesn't hold enough shares.
     */
    @Override
    public void execute() {
        String symbol = order.getStock().getSymbol();
        int requiredQty = order.getRemainingQuantity();
        int availableQty = account.getStockQuantity(symbol);

        if (availableQty < requiredQty) {
            throw new InsufficientStockException(
                    "Insufficient shares to place sell order for " + order.getUser().getName(),
                    symbol, requiredQty, availableQty);
        }

        System.out.printf("[Command] Executing: %s%n", getDescription());
        stockExchange.placeSellOrder(order);
    }

    /**
     * Cancels the sell order.
     * Same delegation chain as BuyStockCommand: Command → Exchange → State Pattern.
     */
    @Override
    public void undo() {
        System.out.printf("[Command] Undoing: %s%n", getDescription());
        stockExchange.cancelOrder(order);
    }

    @Override
    public String getDescription() {
        return String.format("SELL %d x %s @₹%.2f by %s [orderId: %s]",
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
