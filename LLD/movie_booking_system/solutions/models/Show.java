package LLD.movie_booking_system.solutions.models;

import LLD.movie_booking_system.solutions.enums.SeatType;
import LLD.movie_booking_system.solutions.strategy.pricing.PricingStrategy;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class Show {
    private final String id;
    private final Movie movie;
    private final Screen screen;
    private LocalDateTime startTime;
    private Map<SeatType, Double> seatPrices = new ConcurrentHashMap<>();
    private PricingStrategy pricingStrategy;
    private final Object lock = new Object();

    public Show(String id, Movie movie, Screen screen, LocalDateTime startTime, Map<SeatType, Double> seatPrices, PricingStrategy pricingStrategy) {
        this.id = id;
        this.movie = movie;
        this.screen = screen;
        this.startTime = startTime;
        this.seatPrices = seatPrices;
        this.pricingStrategy = pricingStrategy;
    }

    public Map<SeatType, Double> getSeatPrices() {
        return seatPrices;
    }

    public Object getLock() {
        return lock;
    }

    public Movie getMovie() {
        return movie;
    }

    public Screen getScreen() {
        return screen;
    }

    public LocalDateTime getStartTime() {
        return startTime;
    }

    public PricingStrategy getPricingStrategy() {
        return pricingStrategy;
    }

    public String getId() {
        return id;
    }
}
