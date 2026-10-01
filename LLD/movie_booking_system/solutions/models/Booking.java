package LLD.movie_booking_system.solutions.models;

import java.util.List;
import java.util.UUID;

public class Booking {
    private final String id;
    private final Customer user;
    private final Show show;
    private List<Seat> seats;
    private final double totalAmount;
    private final Payment payment;


    public Booking(Customer user, Show show, List<Seat> seats, double totalAmount, Payment payment) {
        this.id = UUID.randomUUID().toString();
        this.user = user;
        this.show = show;
        this.seats = seats;
        this.totalAmount = totalAmount;
        this.payment = payment;
    }

    public String getId() {
        return id;
    }

    public Customer getUser() {
        return user;
    }

    public Show getShow() {
        return show;
    }

    public List<Seat> getSeats() {
        return seats;
    }

    public double getTotalAmount() {
        return totalAmount;
    }

    public Payment getPayment() {
        return payment;
    }

    public void addSeat(Seat seat) {
        seats.add(seat);
    }
}
