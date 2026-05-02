package LLD.paymentGateway.solution.factory;

import LLD.paymentGateway.solution.enums.PaymentMethod;
import LLD.paymentGateway.solution.strategy.CreditCardProcessor;
import LLD.paymentGateway.solution.strategy.UPIPaymentProcessor;
import LLD.paymentGateway.solution.strategy.PaypalPaymentProcessor;
import LLD.paymentGateway.solution.strategy.PaymentProcessor;

public class PaymentProcessorFactory {

    public static PaymentProcessor getProcessor(PaymentMethod method) {
        switch (method) {
            case CREDIT_CARD:
                return new CreditCardProcessor();
            case UPI:
                return new UPIPaymentProcessor();
            case PAYPAL:
                return new PaypalPaymentProcessor();
            // case BANK_TRANSFER:
            // return new BankTransferProcessor();
            default:
                throw new IllegalArgumentException("Unsupported payment method: " + method);
        }
    }
}
