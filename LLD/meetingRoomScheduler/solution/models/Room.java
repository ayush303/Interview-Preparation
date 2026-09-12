package LLD.meetingRoomScheduler.solution.models;

import LLD.meetingRoomScheduler.solution.enums.RoomType;

public class Room {

    private final String id;
    private final String name;
    private final RoomType roomType;
    private final int capacity;

    public Room(String id, String name, RoomType roomType, int capacity) {
        this.id = id;
        this.name = name;
        this.capacity = capacity;
        this.roomType = roomType;
    }

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public int getCapacity() {
        return capacity;
    }

    public RoomType getRoomType() {
        return roomType;
    }

    @Override
    public String toString() {
        return name + " (" + roomType + ", capacity: " + capacity + ")";
    }
}
