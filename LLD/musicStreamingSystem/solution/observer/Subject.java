package LLD.musicStreamingSystem.solution.observer;

import java.util.ArrayList;
import java.util.List;

import LLD.musicStreamingSystem.solution.model.Album;

public abstract class Subject {
    private final List<ArtistObserver> observers = new ArrayList<>();

    public void addObserver(ArtistObserver observer) {
        observers.add(observer);
    }

    public void removeObserver(ArtistObserver observer) {
        observers.remove(observer);
    }

    public void notifyObservers(Artist artist, Album album) {
        for (ArtistObserver observer : observers) {
            observer.update(artist, album);
        }
    }
}
