package LLD.movie_booking_system.solutions.models;

import LLD.movie_booking_system.solutions.enums.SeatStatus;
import LLD.movie_booking_system.solutions.enums.SeatType;

import java.util.UUID;

public class Seat {
    private final String id;
    private final int row;
    private final int col;
    private SeatType type;
    private SeatStatus status;

    public Seat(String id, int row, int col, SeatType type) {
        this.id = UUID.randomUUID().toString();
        this.row = row;
        this.col = col;
        this.type = type;
        this.status = SeatStatus.AVAILABLE;
    }

    public void setType(SeatType type) {
        this.type = type;
    }

    public void setStatus(SeatStatus status) {
        this.status = status;
    }

    public String getId() {
        return id;
    }

    public int getRow() {
        return row;
    }

    public int getCol() {
        return col;
    }

    public SeatType getType() {
        return type;
    }

    public SeatStatus getStatus() {
        return status;
    }
}
