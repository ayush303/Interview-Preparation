package LLD.movie_booking_system.solutions.models;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class Screen {
    private final String id;
    private List<Seat> seats = new ArrayList<>();

    public Screen(String id) {
        this.id = id;
    }

    public String getId() {
        return id;
    }

    public List<Seat> getSeats() {
        return seats;
    }

    public void addSeat(Seat seat) {
        seats.add(seat);
    }
}
