package LLD.riderHailingSystem.solution.model;

import LLD.riderHailingSystem.solution.enums.TripStatus;
import LLD.riderHailingSystem.solution.observer.Subject;
import LLD.riderHailingSystem.solution.observer.TripObserver;
import LLD.riderHailingSystem.solution.state.RequestedState;
import LLD.riderHailingSystem.solution.state.TripState;
import LLD.ticketManagementSystem.solution.Observer.Observer;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

public class Trip implements Subject {

    private final String id;
    private final Rider rider;
    private Driver driver;
    private TripState currentState;
    private final double fare;
    private final Location pickupLocation;
    private final Location dropLocation;
    private final List<TripObserver> observers = new CopyOnWriteArrayList<>();

    public Trip(TripBuilder builder) {
        this.id = builder.id;
        this.rider = builder.rider;
        this.dropLocation = builder.dropLocation;
        this.pickupLocation = builder.pickupLocation;
        this.fare = builder.fare;
        this.currentState = new RequestedState(); // Initial State;
        addObserver(this.rider);
    }

    public void setDriver(Driver driver) {
        this.driver = driver;
    }

    public void setState(TripState currentState) {
        this.currentState = currentState;
    }

    public String getId() {
        return id;
    }

    public Rider getRider() {
        return rider;
    }

    public Driver getDriver() {
        return driver;
    }

    public TripStatus getStatus() {
        return currentState.getStatus();
    }

    public double getFare() {
        return fare;
    }

    public Location getPickupLocation() {
        return pickupLocation;
    }

    public Location getDropLocation() {
        return dropLocation;
    }

    public List<TripObserver> getObservers() {
        return observers;
    }

    public synchronized void assignDriver(Driver driver) {
        TripState previouState = currentState;
        currentState.assign(this, driver);
        if (currentState != previouState) {
            addObserver(driver);
            notifyObserver("Driver for this " + this.getId() + " is " + driver.getName());
        }
    }

    public synchronized void startTrip() {
        TripState previouState = currentState;
        currentState.start(this);
        if (currentState != previouState) {
            notifyObserver("Trip has started");
        }
    }

    public synchronized void endTrip() {
        TripState previouState = currentState;
        currentState.end(this);
        if (currentState != previouState) {
            notifyObserver("Trip has ended");
        }
    }

    @Override
    public void addObserver(TripObserver observer) {
        observers.add(observer);
    }

    @Override
    public void notifyObserver(String message) {
        System.out.printf("[TRIP %s] %s%n", id, message);
        // iterates over a stable snapshot, never throws on concurrent add
        observers.forEach(o -> o.onUpdate(this, message));
    }

    // --- Builder Pattern ---
    public static class TripBuilder {
        private final String id;
        private Rider rider;
        private Location pickupLocation;
        private Location dropLocation;
        private double fare;

        public TripBuilder() {
            this.id = UUID.randomUUID().toString();
        }

        public TripBuilder withRider(Rider rider) {
            this.rider = rider;
            return this;
        }

        public TripBuilder withPickupLocation(Location pickupLocation) {
            this.pickupLocation = pickupLocation;
            return this;
        }

        public TripBuilder withDropLocation(Location dropLocation) {
            this.dropLocation = dropLocation;
            return this;
        }

        public TripBuilder withFare(double fare) {
            this.fare = fare;
            return this;
        }

        public Trip build() {
            if (rider == null || pickupLocation == null || dropLocation == null) {
                throw new IllegalStateException("Rider, pickup, and dropoff locations are required to build a trip.");
            }
            return new Trip(this);
        }
    }

    @Override
    public String toString() {
        return "Trip [id=" + id + ", status=" + getStatus() + ", fare=$" + String.format("%.2f", fare) + "]";
    }
}
