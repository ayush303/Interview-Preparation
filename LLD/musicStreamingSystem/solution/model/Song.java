package LLD.musicStreamingSystem.solution.model;

import java.util.Collections;
import java.util.List;

import LLD.musicStreamingSystem.solution.observer.Artist;

public class Song implements Playable {
    private String id;
    private String title;
    private Artist artist;
    private int durationInSeconds; // in seconds

    public Song(String id, String title, Artist artist, int durationInSeconds) {
        this.id = id;
        this.title = title;
        this.artist = artist;
        this.durationInSeconds = durationInSeconds;
    }

    public String getId() {
        return id;
    }

    public String getTitle() {
        return title;
    }

    public Artist getArtist() {
        return artist;
    }

    public int getDuration() {
        return durationInSeconds;
    }

    @Override
    public List<Song> getTracks() {
        return Collections.singletonList(this);
    }

    @Override
    public String toString() {
        return String.format("'%s' by %s", title, artist.getName());
    }
}
