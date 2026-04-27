package parkinglot.vehicle;

import parkinglot.enums.VehicleSize;

public class Bike extends Vehicle {
    public Bike(String licensePlate) {
        super(licensePlate, VehicleSize.SMALL);
    }
}
