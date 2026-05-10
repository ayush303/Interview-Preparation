package LLD.finance_and_paymentSystem.paymentGateway.solution.observer;

import LLD.finance_and_paymentSystem.paymentGateway.solution.model.Transaction;

public interface PaymentObserver {
    void onTransactionUpdate(Transaction transaction);
}
