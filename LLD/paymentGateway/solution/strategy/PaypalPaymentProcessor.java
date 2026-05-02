package LLD.paymentGateway.solution.strategy;

import LLD.paymentGateway.solution.enums.PaymentStatus;
import LLD.paymentGateway.solution.model.PaymentRequest;
import LLD.paymentGateway.solution.model.PaymentResponse;

public class PaypalPaymentProcessor extends AbstractPaymentProcessor {
    @Override
    protected PaymentResponse doProcess(PaymentRequest request) {
        System.out.println("Redirecting to PayPal for transaction " + request.getTransactionId());
        // Simulate PayPal API interaction
        return new PaymentResponse(PaymentStatus.SUCCESSFUL, "Paypal payment successful.");
    }

}
