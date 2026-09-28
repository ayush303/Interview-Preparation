package LLD.riderHailingSystem.solution.strategy.matching;

import LLD.riderHailingSystem.solution.enums.DriverStatus;
import LLD.riderHailingSystem.solution.enums.RideType;
import LLD.riderHailingSystem.solution.model.Driver;
import LLD.riderHailingSystem.solution.model.Location;

import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

public class NearestDriverMatchingStrategy implements DriverMatchingStrategy {
    private static final double MAX_DISTANCE = 5.0; // Max distance to consider a driver "nearby"

    @Override
    public List<Driver> findDrivers(List<Driver> allDrivers, Location pickup, RideType type) {
        return allDrivers.stream()
                .filter(d -> d.getDriverStatus() == DriverStatus.ONLINE)
                .filter(d -> d.getVehicle().getType() == type)
                .filter(d -> pickup.distanceTo(d.getLocation()) <= MAX_DISTANCE)
                .sorted(Comparator.comparingDouble(d -> pickup.distanceTo(d.getLocation())))
                .collect(Collectors.toList());
    }
}
