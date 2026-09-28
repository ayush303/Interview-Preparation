package LLD.riderHailingSystem.solution.state;

import LLD.riderHailingSystem.solution.enums.TripStatus;
import LLD.riderHailingSystem.solution.model.Driver;
import LLD.riderHailingSystem.solution.model.Trip;

public class AssignedState implements TripState {
    @Override
    public void assign(Trip trip, Driver driver) {
        trip.setDriver(driver); // Reassignment
    }

    @Override
    public void start(Trip trip) {
        trip.setState(new InProgressState());
        trip.notifyObserver("Trip : " + trip.getId() + " status changed from " + getStatus() + " to " + " IN_PROGRESS");
    }

    @Override
    public void end(Trip trip) {
        System.out.println("Cannot end a trip that has not started.");
    }

    @Override
    public TripStatus getStatus() {
        return TripStatus.ASSIGNED;
    }
}
