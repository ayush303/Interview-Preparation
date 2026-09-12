package LLD.meetingRoomScheduler.solution.strategies;

import LLD.meetingRoomScheduler.solution.models.Room;

import java.util.Comparator;
import java.util.List;

public class BestFitStrategy implements RoomSelectionStrategy {

    @Override
    public Room selectRoom(List<Room> availableRooms, int requiredCapacity) {
        // TODO: Return the available room with the smallest capacity.
        // If the list is empty, return null/nullptr/None.
        // Otherwise scan the rooms and return the one whose capacity is the lowest.
        return availableRooms.stream()
                .min(Comparator.comparingInt(Room::getCapacity))
                .orElse(null);
    }

}
