package LLD.paymentGateway.solution.model;

import java.time.LocalDateTime;
import java.util.UUID;

import LLD.paymentGateway.solution.enums.PaymentStatus;

public class Transaction {
    private final String id;
    private final PaymentRequest request;
    private PaymentStatus status;
    private final LocalDateTime timestamp;

    public Transaction(PaymentRequest paymentRequest) {
        this.id = UUID.randomUUID().toString();
        this.request = paymentRequest;
        this.status = PaymentStatus.INITIATED;
        this.timestamp = LocalDateTime.now();
    }

    public void setStatus(PaymentStatus status) {
        this.status = status;
    }

    public String getId() {
        return id;
    }

    public PaymentStatus getStatus() {
        return status;
    }

    public PaymentRequest getRequest() {
        return request;
    }
}
