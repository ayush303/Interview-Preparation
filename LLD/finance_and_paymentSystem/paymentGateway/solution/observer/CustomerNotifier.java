package LLD.finance_and_paymentSystem.paymentGateway.solution.observer;

import LLD.finance_and_paymentSystem.paymentGateway.solution.enums.PaymentStatus;
import LLD.finance_and_paymentSystem.paymentGateway.solution.model.Transaction;

public class CustomerNotifier implements PaymentObserver {
    @Override
    public void onTransactionUpdate(Transaction transaction) {
        if (transaction.getStatus() == PaymentStatus.SUCCESSFUL) {
            System.out.println("--- CUSTOMER EMAIL ---");
            System.out.println("Your payment of " + transaction.getRequest().getAmount()
                    + " was successful. Transaction ID: " + transaction.getId());
            System.out.println("----------------------");
        }
    }

}
