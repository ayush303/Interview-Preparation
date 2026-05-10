package LLD.finance_and_paymentSystem.paymentGateway.solution.strategy;

import LLD.finance_and_paymentSystem.paymentGateway.solution.model.PaymentRequest;
import LLD.finance_and_paymentSystem.paymentGateway.solution.model.PaymentResponse;

public interface PaymentProcessor {
    PaymentResponse processPayment(PaymentRequest request);
}
