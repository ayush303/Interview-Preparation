package LLD.movie_booking_system.solutions.strategy.payment;

import LLD.movie_booking_system.solutions.enums.PaymentStatus;
import LLD.movie_booking_system.solutions.models.Payment;

import java.util.UUID;

public class CreditCardPaymentStrategy implements PaymentStrategy {
    private final String cardNumber;
    private final String cvv;

    public CreditCardPaymentStrategy(String cardNumber, String cvv) {
        this.cardNumber = cardNumber;
        this.cvv = cvv;
    }

    @Override
    public Payment pay(double amount) {
        System.out.printf("Processing credit card payment of $%.2f%n", amount);
        // Simulate payment gateway interaction
        boolean paymentSuccess = Math.random() > 0.05; // 95% success rate;

        return new Payment(amount, "TXN-" + UUID.randomUUID(), paymentSuccess ? PaymentStatus.SUCCESS : PaymentStatus.FAILURE);
    }

    @Override
    public void refund(Payment payment) {
        System.out.printf("Refunding credit card payment of $%.2f (txn %s)%n",
                payment.getAmount(), payment.getTransactionId());
    }
}
