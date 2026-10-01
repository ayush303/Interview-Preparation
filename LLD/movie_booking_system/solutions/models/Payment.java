package LLD.movie_booking_system.solutions.models;

import LLD.movie_booking_system.solutions.enums.PaymentStatus;

import java.util.UUID;

public class Payment {
    private final String id;
    private final double amount;
    private final String transactionId;
    private PaymentStatus status;

    public Payment(double amount, String transactionId, PaymentStatus status) {
        this.id = UUID.randomUUID().toString();
        this.amount = amount;
        this.transactionId = transactionId;
        this.status = status;
    }

    public String getId() {
        return id;
    }

    public double getAmount() {
        return amount;
    }

    public String getTransactionId() {
        return transactionId;
    }

    public PaymentStatus getStatus() {
        return status;
    }
}
