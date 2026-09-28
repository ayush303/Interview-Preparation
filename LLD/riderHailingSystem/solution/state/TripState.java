package LLD.riderHailingSystem.solution.state;

import LLD.riderHailingSystem.solution.enums.TripStatus;
import LLD.riderHailingSystem.solution.model.Driver;
import LLD.riderHailingSystem.solution.model.Trip;

public interface TripState {
    void assign(Trip trip, Driver driver);
    void start(Trip trip);
    void end(Trip trip);
    TripStatus getStatus();
}
