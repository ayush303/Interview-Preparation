package LLD.paymentGateway.solution.strategy;

import LLD.paymentGateway.solution.enums.PaymentStatus;
import LLD.paymentGateway.solution.model.PaymentRequest;
import LLD.paymentGateway.solution.model.PaymentResponse;

public class CreditCardProcessor extends AbstractPaymentProcessor {

    @Override
    protected PaymentResponse doProcess(PaymentRequest request) {
        System.out.println(
                "Processing credit card payment of amount " + request.getAmount() + " " + request.getCurrency());
        // Simulate interaction with Visa/Mastercard network
        return new PaymentResponse(PaymentStatus.SUCCESSFUL, "Credit Card payment successful.");
    }

}
