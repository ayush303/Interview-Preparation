package LLD.finance_and_paymentSystem.paymentGateway.solution.factory;

import LLD.finance_and_paymentSystem.paymentGateway.solution.enums.PaymentMethod;
import LLD.finance_and_paymentSystem.paymentGateway.solution.strategy.CreditCardProcessor;
import LLD.finance_and_paymentSystem.paymentGateway.solution.strategy.PaymentProcessor;
import LLD.finance_and_paymentSystem.paymentGateway.solution.strategy.PaypalPaymentProcessor;
import LLD.finance_and_paymentSystem.paymentGateway.solution.strategy.UPIPaymentProcessor;

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
