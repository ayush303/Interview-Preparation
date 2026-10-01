package LLD.movie_booking_system.solutions.strategy.payment;

import LLD.movie_booking_system.solutions.models.Payment;

public interface PaymentStrategy {
    Payment pay(double amount);
    void refund(Payment payment);
}
