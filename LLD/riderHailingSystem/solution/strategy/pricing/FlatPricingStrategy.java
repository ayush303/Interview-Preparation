package LLD.riderHailingSystem.solution.strategy.pricing;

import LLD.riderHailingSystem.solution.enums.RideType;
import LLD.riderHailingSystem.solution.model.Location;

public class FlatPricingStrategy implements PricingStrategy {
    private static final double BASE_FARE = 5.0;
    private static final double FLAT_RATE = 1.5;

    @Override
    public double calculateFare(Location pickup, Location drop, RideType type) {
        double distance = pickup.distanceTo(drop);
        return BASE_FARE + distance * FLAT_RATE;
    }
}
