package LLD.movie_booking_system.solutions.strategy.pricing;

import LLD.movie_booking_system.solutions.enums.SeatType;
import LLD.movie_booking_system.solutions.models.Seat;

import java.util.List;
import java.util.Map;

public class WeekendPricingStrategy implements PricingStrategy {
    private static final double WEEKEND_SURCHARGE = 1.2; // 20% surcharge
    @Override
    public double calculatePrice(List<Seat> seats, Map<SeatType, Double> seatPrices) {
        double basePrice = seats.stream()
                .mapToDouble(seat -> seatPrices.get(seat.getType()))
                .sum();
        return basePrice * WEEKEND_SURCHARGE;
    }
}
