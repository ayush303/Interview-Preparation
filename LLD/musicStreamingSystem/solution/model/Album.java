package LLD.musicStreamingSystem.solution.model;

import java.util.List;

public class Album implements Playable {
    private String title;
    private List<Song> tracks;

    public Album(String title) {
        this.title = title;
    }

    public void addTrack(Song song) {
        tracks.add(song);
    }

    @Override
    public List<Song> getTracks() {
        return List.copyOf(tracks);
    }

    public String getTitle() {
        return title;
    }
}
