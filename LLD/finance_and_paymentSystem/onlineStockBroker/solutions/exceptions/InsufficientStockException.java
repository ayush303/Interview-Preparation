package LLD.finance_and_paymentSystem.onlineStockBroker.solutions.exceptions;

/**
 * Thrown by SellStockCommand.execute() when the seller doesn't hold
 * enough shares of the stock they're trying to sell.
 *
 * Symmetric counterpart to InsufficientFundsException for the sell side.
 */
public class InsufficientStockException extends RuntimeException {

    private final String symbol;
    private final int required;
    private final int available;

    public InsufficientStockException(String message, String symbol, int required, int available) {
        super(message);
        this.symbol = symbol;
        this.required = required;
        this.available = available;
    }

    public String getSymbol() {
        return symbol;
    }

    public int getRequired() {
        return required;
    }

    public int getAvailable() {
        return available;
    }

    @Override
    public String getMessage() {
        return String.format("%s | Stock: %s | Required: %d | Available: %d",
                super.getMessage(), symbol, required, available);
    }
}