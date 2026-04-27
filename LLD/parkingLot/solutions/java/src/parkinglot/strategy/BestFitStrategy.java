package parkinglot.strategy;

import java.util.List;
import java.util.Optional;

import parkinglot.entities.ParkingFloor;
import parkinglot.entities.ParkingSpot;
import parkinglot.vehicle.Vehicle;

public class BestFitStrategy implements SpotAllocationStrategy {
    @Override
    public Optional<ParkingSpot> findSpot(List<ParkingFloor> floors, Vehicle vehicle) {
        Optional<ParkingSpot> bestSpot = Optional.empty();

        for (ParkingFloor floor : floors) {
            Optional<ParkingSpot> spotOnThisFloor = floor.findAvailableSpot(vehicle);

            if (spotOnThisFloor.isPresent()) {
                if (bestSpot.isEmpty()) {
                    // If this is the first spot we've found, it's the best one so far.
                    bestSpot = spotOnThisFloor;
                } else {
                    // A smaller spot size enum ordinal means a tighter fit.
                    if (spotOnThisFloor.get().getSpotSize().ordinal() < bestSpot.get().getSpotSize().ordinal()) {
                        bestSpot = spotOnThisFloor;
                    }
                }
            }
        }
        return bestSpot;
    }
}
