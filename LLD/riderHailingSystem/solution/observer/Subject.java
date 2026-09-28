package LLD.riderHailingSystem.solution.observer;

import LLD.ticketManagementSystem.solution.Observer.Observer;

import java.util.List;

public interface Subject {
    void addObserver(TripObserver observer);
    void notifyObserver(String message);
}
