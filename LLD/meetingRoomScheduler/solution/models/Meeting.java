package LLD.meetingRoomScheduler.solution.models;

import LLD.meetingRoomScheduler.solution.enums.MeetingStatus;
import LLD.meetingRoomScheduler.solution.exceptions.MeetingSchedulerException;

import java.util.List;

public class Meeting {

    private final String id;
    private final String subject;
    private final User organizer;
    private final List<User> participants;
    private final Room room;
    private final TimeSlot timeSlot;
    private MeetingStatus status;

    public Meeting(String id, String subject, User organizer, List<User> participants, Room room, TimeSlot timeSlot) {
        this.id = id;
        this.subject = subject;
        this.organizer = organizer;
        this.participants = participants;
        this.room = room;
        this.timeSlot = timeSlot;
        this.status = MeetingStatus.SCHEDULED;
    }

    public String getId() {
        return id;
    }

    public String getSubject() {
        return subject;
    }

    public User getOrganizer() {
        return organizer;
    }

    public List<User> getParticipants() {
        return participants;
    }

    public Room getRoom() {
        return room;
    }

    public TimeSlot getTimeSlot() {
        return timeSlot;
    }

    public MeetingStatus getStatus() {
        return status;
    }

    public void cancel() {
        // TODO: Move this meeting to the CANCELLED state.
        // If the current status is not SCHEDULED, raise/throw a scheduler error explaining only SCHEDULED meetings can be cancelled.
        // Otherwise set the status to CANCELLED.
        if (status != MeetingStatus.SCHEDULED) {
            throw new MeetingSchedulerException("Can only cancel SCHEDULED meetings. Current status is: " + status);
        }
        status = MeetingStatus.CANCELLED;
    }

    public void complete() {
        // TODO: Move this meeting to the COMPLETED state.
        // If the current status is not SCHEDULED, raise/throw a scheduler error explaining only SCHEDULED meetings can be completed.
        // Otherwise set the status to COMPLETED.
        if (status != MeetingStatus.SCHEDULED) {
            throw new MeetingSchedulerException("Can only complete SCHEDULED meetings. Current status is: " + status);
        }
        status = MeetingStatus.COMPLETED;
    }

    @Override
    public String toString() {
        return "Meeting{id=" + id + ", subject='" + subject + "', room="
                + room.getName() + ", time=" + timeSlot + ", status=" + status + "}";
    }
}
