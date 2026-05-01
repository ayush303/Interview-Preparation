package LLD.musicStreamingSystem.solution.model;

import java.util.ArrayList;
import java.util.List;

import LLD.musicStreamingSystem.solution.enums.PlayerStatus;
import LLD.musicStreamingSystem.solution.states.PlayerState;
import LLD.musicStreamingSystem.solution.states.StoppedState;

public class Player {
    private PlayerState state;
    private PlayerStatus status;
    private List<Song> queue = new ArrayList<>();
    private int currentIndex = -1;
    private Song currentSong;
    private User currentUser;

    public Player() {
        this.state = new StoppedState();
        this.status = PlayerStatus.STOPPED;
    }

    public void load(Playable playable, User user) {
        this.currentUser = user;
        this.queue = playable.getTracks();
        this.currentIndex = 0;
        System.out.printf("Loaded %d tracks for user %s.%n", queue.size(), user.getName());
        this.state = new StoppedState();
    }

    public void playCurrentSongInQueue() {
        if (currentIndex >= 0 && currentIndex < queue.size()) {
            Song songToPlay = queue.get(currentIndex);
            currentUser.getPlaybackStrategy().play(songToPlay, this);
        }
    }

    // Method for state transitions
    public void clickPlay() {
        state.play(this);
    }

    public void clickPause() {
        state.pause(this);
    }

    public void clickNext() {
        if (currentIndex < queue.size() - 1) {
            currentIndex++;
            playCurrentSongInQueue();
        } else {
            System.out.println("You are at the end of the queue.");
            state.stop(this);
        }
    }

    // Getters and Setters used by States
    public void changeState(PlayerState state) {
        this.state = state;
    }

    public void setStatus(PlayerStatus status) {
        this.status = status;
    }

    public void setCurrentSong(Song song) {
        this.currentSong = song;
    }

    public boolean hasQueue() {
        return !queue.isEmpty();
    }
}
