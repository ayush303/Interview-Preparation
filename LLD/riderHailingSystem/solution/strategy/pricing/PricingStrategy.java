package LLD.riderHailingSystem.solution.strategy.pricing;

import LLD.riderHailingSystem.solution.enums.RideType;
import LLD.riderHailingSystem.solution.model.Location;

public interface PricingStrategy {
    double calculateFare(Location pickup, Location drop, RideType type);
}
