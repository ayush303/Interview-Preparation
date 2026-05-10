package LLD.finance_and_paymentSystem.paymentGateway.solution.strategy;

import LLD.finance_and_paymentSystem.paymentGateway.solution.enums.PaymentStatus;
import LLD.finance_and_paymentSystem.paymentGateway.solution.model.PaymentRequest;
import LLD.finance_and_paymentSystem.paymentGateway.solution.model.PaymentResponse;

public class UPIPaymentProcessor extends AbstractPaymentProcessor {
    @Override
    protected PaymentResponse doProcess(PaymentRequest request) {
        System.out.println("Processing UPI payment of " + request.getAmount() + " " + request.getCurrency());
        return new PaymentResponse(PaymentStatus.SUCCESSFUL, "UPI payment successful.");
    }

}
