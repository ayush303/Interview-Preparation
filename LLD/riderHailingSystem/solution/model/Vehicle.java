package LLD.riderHailingSystem.solution.model;

import LLD.riderHailingSystem.solution.enums.RideType;

public class Vehicle {
    private final String licenseNumber;
    private final RideType type;
    private final String model;

    public Vehicle(String licenseNumber, String model, RideType type) {
        this.licenseNumber = licenseNumber;
        this.type = type;
        this.model = model;
    }

    public String getLicenseNumber() {
        return licenseNumber;
    }

    public RideType getType() {
        return type;
    }

    public String getModel() {
        return model;
    }
}
