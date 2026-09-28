package LLD.riderHailingSystem.solution.state;

import LLD.riderHailingSystem.solution.enums.TripStatus;
import LLD.riderHailingSystem.solution.model.Driver;
import LLD.riderHailingSystem.solution.model.Trip;

public class InProgressState implements TripState {
    @Override
    public void assign(Trip trip, Driver driver) {
        System.out.println("Cannot assign a new driver while trip is in progress.");
    }

    @Override
    public void start(Trip trip) {
        System.out.println("Trip is already in progress.");
    }

    @Override
    public void end(Trip trip) {
        trip.setState(new CompletedState());
        trip.notifyObserver("Trip : " + trip.getId() + " status changed from " + getStatus() + " to " + " COMPLETED");
    }

    @Override
    public TripStatus getStatus() {
        return TripStatus.IN_PROGRESS;
    }
}
