package LLD.movie_booking_system.solutions;

import LLD.movie_booking_system.solutions.enums.PaymentStatus;
import LLD.movie_booking_system.solutions.models.*;
import LLD.movie_booking_system.solutions.strategy.payment.PaymentStrategy;

import java.util.List;
import java.util.Optional;

public class BookingManager {

    private final SeatLockManager seatLockManager;

    public BookingManager(SeatLockManager seatLockManager) {
        this.seatLockManager = seatLockManager;
    }

    public Optional<Booking> createBooking(Customer user, Show show, List<Seat> seats, PaymentStrategy paymentStrategy) {
        // 1. Lock the seats. Abort immediately if any of them is unavailable.
        if (!seatLockManager.lockSeats(show, seats, user.getId())) {
            System.out.println("Could not lock the selected seats. They may already be taken.");
            return Optional.empty();
        }

        // 2. Calculate the total price
        double totalAmount = show.getPricingStrategy().calculatePrice(seats, show.getSeatPrices());

        // 3. Process payment. If it fails, release the locks and stop.
        Payment payment = paymentStrategy.pay(totalAmount);
        if (payment.getStatus() != PaymentStatus.SUCCESS) {
            System.out.println("Payment failed. Releasing seats.");
            seatLockManager.unlockSeats(show, seats, user.getId());
            return Optional.empty();
        }

        // 4. Re-verify the lock and mark the seats BOOKED atomically. The lock may have
        //    expired while the payment was in flight, so confirm before charging the seat.
        if (!seatLockManager.confirmSeats(show, seats, user.getId())) {
            System.out.println("Seat lock expired before payment completed. Refunding payment.");
            paymentStrategy.refund(payment);
            return Optional.empty();
        }

        // 5. Build the confirmed booking
        Booking booking = new Booking(user, show, seats, totalAmount, payment);
        return Optional.of(booking);
    }
}
