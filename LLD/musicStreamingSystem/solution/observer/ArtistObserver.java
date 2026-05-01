package LLD.musicStreamingSystem.solution.observer;

import LLD.musicStreamingSystem.solution.model.Album;

public interface ArtistObserver {
    void update(Artist artist, Album album);
}
