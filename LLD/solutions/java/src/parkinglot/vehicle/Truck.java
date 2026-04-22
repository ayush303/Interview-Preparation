package parkinglot.vehicle;

import parkinglot.enums.VehicleSize;

public class Truck extends Vehicle {
    public Truck(String licensePlate) {
        super(licensePlate, VehicleSize.LARGE);
    }
}
