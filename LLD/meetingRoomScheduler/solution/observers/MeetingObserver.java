package LLD.meetingRoomScheduler.solution.observers;

import LLD.meetingRoomScheduler.solution.models.Meeting;

public interface MeetingObserver {
    void onMeetingScheduled(Meeting meeting);
    void onMeetingCancelled(Meeting meeting);
}
