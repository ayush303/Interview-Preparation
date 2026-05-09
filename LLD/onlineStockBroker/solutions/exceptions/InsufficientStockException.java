package LLD.onlineStockBroker.solutions.exceptions;

public class InsufficientStockException extends RuntimeException {
    public InsufficientStockException(String message) {
        super(message);
    }
}
