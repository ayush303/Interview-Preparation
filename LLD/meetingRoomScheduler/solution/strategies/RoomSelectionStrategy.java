package LLD.meetingRoomScheduler.solution.strategies;

import LLD.meetingRoomScheduler.solution.models.Room;

import java.util.List;

public interface RoomSelectionStrategy {

    Room selectRoom(List<Room> availableRooms, int requiredCapacity);

}
