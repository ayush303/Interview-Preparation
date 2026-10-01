package LLD.movie_booking_system.solutions.strategy.pricing;

import LLD.movie_booking_system.solutions.enums.SeatType;
import LLD.movie_booking_system.solutions.models.Seat;

import java.util.List;
import java.util.Map;

public interface PricingStrategy {
    double calculatePrice(List<Seat> seats, Map<SeatType, Double> seatPrices);
}
