package LLD.paymentGateway.solution.strategy;

import LLD.paymentGateway.solution.model.PaymentRequest;
import LLD.paymentGateway.solution.model.PaymentResponse;

public interface PaymentProcessor {
    PaymentResponse processPayment(PaymentRequest request);
}
