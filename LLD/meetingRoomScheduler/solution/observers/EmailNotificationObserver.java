package LLD.meetingRoomScheduler.solution.observers;

import LLD.meetingRoomScheduler.solution.models.Meeting;

public class EmailNotificationObserver implements MeetingObserver {

    @Override
    public void onMeetingScheduled(Meeting meeting) {
        System.out.println("[Email] Meeting scheduled: \""
                + meeting.getSubject() + "\" in " + meeting.getRoom().getName()
                + " (" + meeting.getTimeSlot() + ") organized by "
                + meeting.getOrganizer());
    }

    @Override
    public void onMeetingCancelled(Meeting meeting) {
        System.out.println("[Email] Meeting cancelled: \""
                + meeting.getSubject() + "\" in " + meeting.getRoom().getName()
                + " was cancelled by " + meeting.getOrganizer());
    }
}
