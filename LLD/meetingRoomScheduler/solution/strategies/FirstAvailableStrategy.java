package LLD.meetingRoomScheduler.solution.strategies;

import LLD.meetingRoomScheduler.solution.models.Room;

import java.util.List;

public class FirstAvailableStrategy implements RoomSelectionStrategy {

    @Override
    public Room selectRoom(List<Room> availableRooms, int requiredCapacity) {
        // TODO: Return the first room from `availableRooms`.
        // If the list is empty, return null/nullptr/None.
        // Otherwise return the element at index 0.
        return availableRooms.isEmpty() ? null : availableRooms.get(0);
    }
}
