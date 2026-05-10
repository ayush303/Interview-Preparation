package LLD.finance_and_paymentSystem.onlineStockBroker.solutions.exceptions;

/**
 * Thrown by BuyStockCommand.execute() when the buyer's account balance
 * cannot cover the estimated cost of a LIMIT buy order.
 *
 * WHY RuntimeException (unchecked)?
 * This is a business rule violation, not a recoverable I/O failure.
 * Callers (OrderInvoker) can choose to catch and log it without being
 * forced to declare "throws" everywhere.
 *
 * WHY not just return false or print?
 * Exceptions force the caller to explicitly decide what to do.
 * A silent return lets bad orders slip through if the caller forgets to check.
 */
public class InsufficientFundsException extends RuntimeException {

    private final double required;
    private final double available;

    public InsufficientFundsException(String message, double required, double available) {
        super(message);
        this.required = required;
        this.available = available;
    }

    public double getRequired() {
        return required;
    }

    public double getAvailable() {
        return available;
    }

    @Override
    public String getMessage() {
        return String.format("%s | Required: ₹%.2f | Available: ₹%.2f",
                super.getMessage(), required, available);
    }
}