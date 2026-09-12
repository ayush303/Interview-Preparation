package LLD.meetingRoomScheduler.solution;

import LLD.meetingRoomScheduler.solution.exceptions.MeetingSchedulerException;
import LLD.meetingRoomScheduler.solution.models.Meeting;
import LLD.meetingRoomScheduler.solution.models.Room;
import LLD.meetingRoomScheduler.solution.models.TimeSlot;
import LLD.meetingRoomScheduler.solution.models.User;
import LLD.meetingRoomScheduler.solution.observers.MeetingObserver;
import LLD.meetingRoomScheduler.solution.strategies.FirstAvailableStrategy;
import LLD.meetingRoomScheduler.solution.strategies.RoomSelectionStrategy;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

public class MeetingScheduler {

    private static volatile MeetingScheduler instance;
    private static final Object lock = new Object();

    private final ConcurrentHashMap<String, Room> rooms;
    private final ConcurrentHashMap<String, Meeting> meetings;
    // Maps room ID to list of active (non-cancelled) meetings in that room
    private final ConcurrentHashMap<String, List<Meeting>> roomMeetings;
    private final CopyOnWriteArrayList<MeetingObserver> observers;
    private RoomSelectionStrategy roomSelectionStrategy;
    private final AtomicInteger meetingCounter;

    private MeetingScheduler() {
        this.rooms = new ConcurrentHashMap<>();
        this.meetings = new ConcurrentHashMap<>();
        this.roomMeetings = new ConcurrentHashMap<>();
        this.observers = new CopyOnWriteArrayList<>();
        this.roomSelectionStrategy = new FirstAvailableStrategy();
        this.meetingCounter = new AtomicInteger(0);
    }

    public static MeetingScheduler getInstance() {
        if (instance == null) {
            synchronized (lock) {
                if (instance == null) {
                    instance = new MeetingScheduler();
                }
            }
        }
        return instance;
    }

    public synchronized void addRoom(Room room) {
        rooms.put(room.getId(), room);
        roomMeetings.putIfAbsent(room.getId(), new ArrayList<>());
    }

    public synchronized Meeting scheduleMeeting(String subject, User organizer,
                                                List<User> participants,
                                                TimeSlot timeSlot,
                                                int requiredCapacity) {
        // TODO: Book a room for this meeting and return the created Meeting.
        // 1. Get the rooms available for this time slot and capacity.
        List<Room> availableRooms = getAvailableRooms(timeSlot, requiredCapacity);
        // 2. Use the selection strategy to pick one room from that list.
        Room selectedRoom = roomSelectionStrategy.selectRoom(availableRooms, requiredCapacity);
        // 3. If no room was picked, raise/throw a scheduler error about no available room.
        if (selectedRoom == null) {
            throw new MeetingSchedulerException(
                    "No available room found for capacity " + requiredCapacity
                            + " during " + timeSlot);
        }
        // 4. Increment the meeting counter and build the id as "MTG-<counter>".
        String meetingId = "MTG-" + meetingCounter.incrementAndGet();
        // 5. Create the Meeting, store it in the meetings map, and append it to the room's meeting list.
        Meeting meeting = new Meeting(meetingId, subject, organizer,
                participants, selectedRoom, timeSlot);
        meetings.put(meetingId, meeting);
        roomMeetings.get(selectedRoom.getId()).add(meeting);
        // 6. Notify the scheduled observers, then return the meeting.
        notifyMeetingScheduled(meeting);

        return meeting;
    }

    public synchronized void cancelMeeting(String meetingId) {
        // TODO: Cancel the meeting with this id.
        // 1. Look up the meeting; if it does not exist, raise/throw a scheduler error.
        Meeting meeting = meetings.get(meetingId);
        if (meeting == null) {
            throw new MeetingSchedulerException(
                    "Meeting not found: " + meetingId);
        }
        // 2. Call cancel() on the meeting to enforce the state machine.
        meeting.cancel();
        // 3. Remove the meeting from its room's meeting list so the slot is freed.
        roomMeetings.get(meeting.getRoom().getId()).remove(meeting);
        // 4. Notify the cancelled observers.
        notifyMeetingCancelled(meeting);
    }

    public synchronized List<Room> getAvailableRooms(TimeSlot timeSlot, int requiredCapacity) {
        // TODO: Return every room that fits the capacity and has no conflicting meeting.
        // Start with an empty result list.
        List<Room> available = new ArrayList<>();
        // For each room: skip it if its capacity is below requiredCapacity.
        for (Room room: rooms.values()) {
            if (room.getCapacity() < requiredCapacity) {
                continue;
            }
            // Check if room has any conflicting meetings
            List<Meeting> existingMeeting = roomMeetings.getOrDefault(room.getId(), Collections.emptyList());
            // Check the room's existing meetings; skip the room if any of them overlaps `timeSlot`.
            boolean hasConflict = existingMeeting.stream()
                    .anyMatch(m -> m.getTimeSlot().overlaps(timeSlot));
            // Add the surviving rooms to the result list and return it.
            if (!hasConflict) {
                available.add(room);
            }
        }

        return available;
    }

    public synchronized void setRoomSelectionStrategy(RoomSelectionStrategy strategy) {
        this.roomSelectionStrategy = strategy;
    }

    public void addObserver(MeetingObserver observer) {
        observers.add(observer);
    }

    public void removeObserver(MeetingObserver observer) {
        observers.remove(observer);
    }

    private void notifyMeetingScheduled(Meeting meeting) {
        for (MeetingObserver observer : observers) {
            try {
                observer.onMeetingScheduled(meeting);
            } catch (Exception e) {
                System.err.println("Observer notification failed: " + e.getMessage());
            }
        }
    }

    private void notifyMeetingCancelled(Meeting meeting) {
        for (MeetingObserver observer : observers) {
            try {
                observer.onMeetingCancelled(meeting);
            } catch (Exception e) {
                System.err.println("Observer notification failed: " + e.getMessage());
            }
        }
    }
}
