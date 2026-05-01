package LLD.musicStreamingSystem.solution.model;

import java.util.ArrayList;
import java.util.List;

public class Playlist implements Playable {
    private String name;
    List<Song> tracks = new ArrayList<>();

    public Playlist(String name) {
        this.name = name;
    }

    public void addTrack(Song song) {
        tracks.add(song);
    }

    @Override
    public List<Song> getTracks() {
        return List.copyOf(tracks);
    }

    public String getName() {
        return name;
    }
}
