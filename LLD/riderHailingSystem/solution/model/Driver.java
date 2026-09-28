package LLD.riderHailingSystem.solution.model;

import LLD.riderHailingSystem.solution.enums.DriverStatus;
import LLD.riderHailingSystem.solution.enums.TripStatus;

public class Driver extends User {
    private Vehicle vehicle;
    private Location location;
    private DriverStatus driverStatus;
    public Driver(String name, String contact, Vehicle vehicle, Location initialLocation) {
        super(name, contact);
        this.vehicle = vehicle;
        this.location = initialLocation;
        this.driverStatus = DriverStatus.OFFLINE;
    }

    public Vehicle getVehicle() {
        return vehicle;
    }

    public Location getLocation() {
        return location;
    }

    public DriverStatus getDriverStatus() {
        return driverStatus;
    }

    public void setLocation(Location location) {
        this.location = location;
    }

    public void setDriverStatus(DriverStatus status) {
        this.driverStatus = status;
        System.out.println("Driver " + getName() + " is now " + status);
    }

    // Atomically claim this driver only if still ONLINE
    public synchronized boolean tryClaimForTrip() {
        if (driverStatus != DriverStatus.ONLINE) {
            return false;
        }
        setDriverStatus(DriverStatus.IN_TRIP);
        return true;
    }

    @Override
    public void onUpdate(Trip trip, String message) {
        System.out.printf("--- Notification for Driver %s ---\n", getName());
        System.out.printf("  Trip %s status: %s.\n", trip.getId(), trip.getStatus());
        if (trip.getStatus() == TripStatus.REQUESTED) {
            System.out.println("  A new ride is available for you to accept.");
        }
        System.out.println("--------------------------------\n");
    }
}
