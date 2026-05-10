package LLD.finance_and_paymentSystem.paymentGateway.solution.observer;

import LLD.finance_and_paymentSystem.paymentGateway.solution.model.Transaction;

public class MerchantNotifier implements PaymentObserver {
    @Override
    public void onTransactionUpdate(Transaction transaction) {
        System.out.println("--- MERCHANT NOTIFICATION ---");
        System.out.println("Transaction " + transaction.getId() + " status updated to: " + transaction.getStatus());
        System.out.println("-----------------------------");
    }

}
