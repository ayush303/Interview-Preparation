package LLD.paymentGateway.solution.strategy;

import LLD.paymentGateway.solution.enums.PaymentStatus;
import LLD.paymentGateway.solution.model.PaymentRequest;
import LLD.paymentGateway.solution.model.PaymentResponse;

public class UPIPaymentProcessor extends AbstractPaymentProcessor {
    @Override
    protected PaymentResponse doProcess(PaymentRequest request) {
        System.out.println("Processing UPI payment of " + request.getAmount() + " " + request.getCurrency());
        return new PaymentResponse(PaymentStatus.SUCCESSFUL, "UPI payment successful.");
    }

}
