package parkinglot.strategy;

import parkinglot.entities.ParkingTicket;

public class HourlyFeeStrategy implements FeeStrategy {
    private final double ratePerHour;

    public HourlyFeeStrategy(double ratePerHour) {
        this.ratePerHour = ratePerHour;
    }

    @Override
    public double calculateFee(ParkingTicket ticket) {
        long duration = ticket.getExitTimestamp() - ticket.getEntryTimestamp();
        return duration * ratePerHour;
    }

}
