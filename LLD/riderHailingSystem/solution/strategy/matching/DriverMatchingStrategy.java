package LLD.riderHailingSystem.solution.strategy.matching;

import LLD.riderHailingSystem.solution.enums.RideType;
import LLD.riderHailingSystem.solution.model.Driver;
import LLD.riderHailingSystem.solution.model.Location;

import java.util.List;

public interface DriverMatchingStrategy {
    List<Driver> findDrivers(List<Driver> allDrivers, Location pickup, RideType type);
}
