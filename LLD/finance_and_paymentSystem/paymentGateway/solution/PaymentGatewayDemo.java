package LLD.finance_and_paymentSystem.paymentGateway.solution;

import java.util.Map;

import LLD.finance_and_paymentSystem.paymentGateway.solution.enums.PaymentMethod;
import LLD.finance_and_paymentSystem.paymentGateway.solution.model.PaymentRequest;
import LLD.finance_and_paymentSystem.paymentGateway.solution.observer.CustomerNotifier;
import LLD.finance_and_paymentSystem.paymentGateway.solution.observer.MerchantNotifier;

public class PaymentGatewayDemo {
    public static void main(String[] args) {
        // 1. Setup the gateway facade
        PaymentGatewayService paymentGateway = PaymentGatewayService.getInstance();

        // 2. Register observers to be notified of transaction events
        paymentGateway.addObserver(new MerchantNotifier());
        paymentGateway.addObserver(new CustomerNotifier());

        System.out.println("----------- SCENARIO 1: Successful Credit Card Payment -----------");
        // a. Merchant's backend creates a payment request
        PaymentRequest ccRequest = new PaymentRequest.Builder()
                .payerId("U-123")
                .amount(150.75)
                .currency("INR")
                .paymentMethod(PaymentMethod.CREDIT_CARD)
                .paymentDetails(Map.of("cardNumber", "1234..."))
                .build();

        // b. Merchant's backend sends it to the facade
        paymentGateway.processPayment(ccRequest);

        System.out.println("\n----------- SCENARIO 2: Successful PayPal Payment -----------");
        PaymentRequest paypalRequest = new PaymentRequest.Builder()
                .payerId("U-456")
                .amount(88.50)
                .currency("USD")
                .paymentMethod(PaymentMethod.PAYPAL)
                .paymentDetails(Map.of("email", "customer@example.com"))
                .build();

        paymentGateway.processPayment(paypalRequest);
    }
}
