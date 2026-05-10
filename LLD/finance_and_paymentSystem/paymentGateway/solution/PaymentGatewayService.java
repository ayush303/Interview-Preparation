package LLD.finance_and_paymentSystem.paymentGateway.solution;

import java.util.ArrayList;
import java.util.List;

import LLD.finance_and_paymentSystem.paymentGateway.solution.enums.PaymentStatus;
import LLD.finance_and_paymentSystem.paymentGateway.solution.factory.PaymentProcessorFactory;
import LLD.finance_and_paymentSystem.paymentGateway.solution.model.PaymentRequest;
import LLD.finance_and_paymentSystem.paymentGateway.solution.model.PaymentResponse;
import LLD.finance_and_paymentSystem.paymentGateway.solution.model.Transaction;
import LLD.finance_and_paymentSystem.paymentGateway.solution.observer.PaymentObserver;
import LLD.finance_and_paymentSystem.paymentGateway.solution.strategy.PaymentProcessor;

public class PaymentGatewayService {
    private static PaymentGatewayService instance;
    private final List<PaymentObserver> observers = new ArrayList<>();

    private PaymentGatewayService() {
    }

    public static synchronized PaymentGatewayService getInstance() {
        if (instance == null) {
            instance = new PaymentGatewayService();
        }
        return instance;
    }

    public void addObserver(PaymentObserver observer) {
        observers.add(observer);
    }

    public void removeObserver(PaymentObserver observer) {
        observers.remove(observer);
    }

    private void notifyObservers(Transaction transaction) {
        observers.forEach(o -> o.onTransactionUpdate(transaction));
    }

    public Transaction processPayment(PaymentRequest request) {
        Transaction transaction = new Transaction(request);
        try {
            PaymentProcessor processor = PaymentProcessorFactory.getProcessor(request.getPaymentMethod());
            PaymentResponse response = processor.processPayment(request);
            transaction.setStatus(response.getStatus());
        } catch (Exception e) {
            System.err.println("Payment processing failed: " + e.getMessage());
            transaction.setStatus(PaymentStatus.FAILED);
        }
        notifyObservers(transaction);
        return transaction;
    }
}
