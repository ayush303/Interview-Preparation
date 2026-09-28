package LLD.riderHailingSystem.solution.strategy.pricing;

import LLD.riderHailingSystem.solution.enums.RideType;
import LLD.riderHailingSystem.solution.model.Location;
import LLD.riderHailingSystem.solution.model.Rider;

import java.util.Map;

public class VehicleBasedPricingStrategy implements PricingStrategy {
    private static final double BASE_FARE = 2.50;
    private static final Map<RideType, Double> RATE_PER_KM = Map.of(
            RideType.SEDAN, 1.50,
            RideType.SUV, 2.00,
            RideType.AUTO, 1.00
    );


    @Override
    public double calculateFare(Location pickup, Location drop, RideType type) {
        double distance = pickup.distanceTo(drop);
        return BASE_FARE + RATE_PER_KM.get(type) * distance;
    }
}
