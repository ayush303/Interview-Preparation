package LLD.riderHailingSystem.solution;

import LLD.riderHailingSystem.solution.enums.DriverStatus;
import LLD.riderHailingSystem.solution.enums.RideType;
import LLD.riderHailingSystem.solution.enums.TripStatus;
import LLD.riderHailingSystem.solution.model.*;
import LLD.riderHailingSystem.solution.state.TripState;
import LLD.riderHailingSystem.solution.strategy.matching.DriverMatchingStrategy;
import LLD.riderHailingSystem.solution.strategy.matching.NearestDriverMatchingStrategy;
import LLD.riderHailingSystem.solution.strategy.pricing.PricingStrategy;
import LLD.riderHailingSystem.solution.strategy.pricing.VehicleBasedPricingStrategy;

import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.concurrent.ConcurrentHashMap;

public class RideHailingService {
    private static volatile RideHailingService instance;
    private static Object lock = new Object();
    private final Map<String, Rider> riders = new ConcurrentHashMap<>();
    private final Map<String, Driver> drivers = new ConcurrentHashMap<>();
    private final Map<String, Trip> trips = new ConcurrentHashMap<>();
    private PricingStrategy pricingStrategy;
    private DriverMatchingStrategy driverMatchingStrategy;

    public RideHailingService() {
        this.pricingStrategy = new VehicleBasedPricingStrategy();
        this.driverMatchingStrategy = new NearestDriverMatchingStrategy();
    }

    public static RideHailingService getInstance() {
        if (instance == null) {
            synchronized (lock) {
                if (instance == null) {
                    return new RideHailingService();
                }
            }
        }
        return instance;
    }

    public void setPricingStrategy(PricingStrategy pricingStrategy) {
        this.pricingStrategy = pricingStrategy;
    }

    public void setDriverMatchingStrategy(DriverMatchingStrategy driverMatchingStrategy) {
        this.driverMatchingStrategy = driverMatchingStrategy;
    }

    public Rider registerRider(String name, String contact) {
        Rider rider = new Rider(name, contact);
        riders.put(rider.getId(), rider);
        return rider;
    }

    public Driver registerDriver(String name, String contact, Vehicle vehicle, Location initialLocation) {
        Driver driver = new Driver(name, contact, vehicle, initialLocation);
        drivers.put(driver.getId(), driver);
        return driver;
    }

    public void goOnline(String driverId) {
        Driver driver = drivers.get(driverId);
        if (driver == null) {
            throw new NoSuchElementException("Driver not found");
        }
        driver.setDriverStatus(DriverStatus.ONLINE);
    }

    public void goOffline(String driverId) {
        Driver driver = drivers.get(driverId);
        if (driver == null) {
            throw new NoSuchElementException("Driver not found");
        }
        driver.setDriverStatus(DriverStatus.OFFLINE);
    }

    public Trip requestRide(String riderId, Location pickup, Location drop, RideType type) {
        Rider rider = riders.get(riderId);
        if (rider == null) {
            throw new NoSuchElementException("Rider not found");
        }
        System.out.println("\n--- New Ride Request from " + rider.getName() + " ---");

        List<Driver> availableDrivers = driverMatchingStrategy.findDrivers(List.copyOf(drivers.values()), pickup, type);

        if (availableDrivers.isEmpty()) {
            System.out.println("No drivers available for your request. Please try again later.");
            return null;
        }

        System.out.println("Found " + availableDrivers.size() + " available driver(s).");

        double fare = pricingStrategy.calculateFare(pickup, drop, type);
        System.out.printf("Estimated fare: $%.2f%n", fare);

        Trip trip = new Trip.TripBuilder()
                .withRider(rider)
                .withFare(fare)
                .withPickupLocation(pickup)
                .withDropLocation(drop)
                .build();

        trips.put(trip.getId(), trip);

        System.out.println("Notifying nearby drivers of the new ride request...");
        for (Driver driver: availableDrivers) {
            System.out.println(" > Notifying " + driver.getName() + " at " + driver.getLocation());
            driver.onUpdate(trip, "New Trip requested came");
        }
        return trip;
    }

    public void acceptRide(String driverId, String tripId) {
        Driver driver = drivers.get(driverId);
        Trip trip = trips.get(tripId);
        if (driver == null || trip == null)
            throw new NoSuchElementException("Driver or Trip not found");
        if (!driver.tryClaimForTrip()) {
            System.out.println("Driver no longer available, finding another...");
            return;
        }
        System.out.println("\n--- Driver " + driver.getName() + " accepted the ride ---");
        trip.assignDriver(driver);
        if (trip.getDriver() == driver) {
            driver.setDriverStatus(DriverStatus.IN_TRIP);
        }
    }

    public void startTrip(String tripId) {
        Trip trip = trips.get(tripId);
        if (trip == null)
            throw new NoSuchElementException("Trip not found");
        System.out.println("\n--- Trip " + trip.getId() + " is starting ---");
        trip.startTrip();
    }

    public void endTrip(String tripId) {
        Trip trip = trips.get(tripId);
        if (trip == null)
            throw new NoSuchElementException("Trip not found");
        System.out.println("\n--- Trip " + trip.getId() + " is ending ---");
        trip.endTrip();

        if (trip.getStatus() != TripStatus.COMPLETED) {
            return;
        }

        Driver driver = trip.getDriver();
        driver.setDriverStatus(DriverStatus.ONLINE);
        driver.setLocation(trip.getDropLocation());

        Rider rider = trip.getRider();
        rider.addTripToHistory(trip);
        driver.addTripToHistory(trip);

        System.out.println("Driver " + driver.getName() + " is now back online at " + driver.getLocation());
    }
}
