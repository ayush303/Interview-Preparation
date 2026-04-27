package parkinglot.vehicle;

import parkinglot.enums.VehicleSize;

public abstract class Vehicle {
    private final String licenseNumber;
    private final VehicleSize size;

    protected Vehicle(String licenseNumber, VehicleSize size) {
        if (licenseNumber == null || licenseNumber.trim().isEmpty()) {
            throw new IllegalArgumentException("License number cannot be null or empty");
        }
        this.licenseNumber = licenseNumber;
        this.size = size;
    }

    public String getLicenseNumber() {
        return licenseNumber;
    }

    public VehicleSize getSize() {
        return size;
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + "[" + licenseNumber + "]";
    }
}
