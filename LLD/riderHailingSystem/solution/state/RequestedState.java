package LLD.riderHailingSystem.solution.state;

import LLD.riderHailingSystem.solution.enums.TripStatus;
import LLD.riderHailingSystem.solution.model.Driver;
import LLD.riderHailingSystem.solution.model.Trip;

public class RequestedState implements TripState {
    @Override
    public void assign(Trip trip, Driver driver) {
        trip.setDriver(driver);
        trip.setState(new AssignedState());
        trip.notifyObserver("Trip : " + trip.getId() + " status changed from " + getStatus() + " to " + " ASSIGNED");
    }

    @Override
    public void start(Trip trip) {
        System.out.println("Cannot start a trip that has not been assigned a driver.");
    }

    @Override
    public void end(Trip trip) {
        System.out.println("Cannot end a trip that has not been assigned a driver.");
    }

    @Override
    public TripStatus getStatus() {
        return TripStatus.REQUESTED;
    }
}
