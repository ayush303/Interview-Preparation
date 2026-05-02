package LLD.paymentGateway.solution.observer;

import LLD.paymentGateway.solution.model.Transaction;

public interface PaymentObserver {
    void onTransactionUpdate(Transaction transaction);
}
