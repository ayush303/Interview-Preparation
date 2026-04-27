package parkinglot.vehicle;

import parkinglot.enums.VehicleSize;

public class Car extends Vehicle {
    public Car(String licensePlate) {
        super(licensePlate, VehicleSize.MEDIUM);
    }
}
