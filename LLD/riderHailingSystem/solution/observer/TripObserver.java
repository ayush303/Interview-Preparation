package LLD.riderHailingSystem.solution.observer;

import LLD.riderHailingSystem.solution.model.Trip;

public interface TripObserver {
    void onUpdate(Trip trip, String message);
}
