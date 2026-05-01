package LLD.musicStreamingSystem.solution.model;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import LLD.musicStreamingSystem.solution.enums.SubscriptionTier;
import LLD.musicStreamingSystem.solution.observer.Artist;
import LLD.musicStreamingSystem.solution.observer.ArtistObserver;
import LLD.musicStreamingSystem.solution.strategy.PlaybackStrategy;

public class User implements ArtistObserver {
    private final String id;
    private final String name;
    private final PlaybackStrategy playbackStrategy;
    private final Set<Artist> followedArtists = new HashSet<>();

    private User(String id, String name, PlaybackStrategy playbackStrategy) {
        this.id = id;
        this.name = name;
        this.playbackStrategy = playbackStrategy;
    }

    public void followArtist(Artist artist) {
        followedArtists.add(artist);
        artist.addObserver(this);
    }

    @Override
    public void update(Artist artist, Album newAlbum) {
        System.out.printf("[Notification for %s] Your followed artist %s just released a new album: %s!%n",
                this.name, artist.getName(), newAlbum.getTitle());
    }

    public PlaybackStrategy getPlaybackStrategy() {
        return playbackStrategy;
    }

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    // Builder Pattern

    public static class Builder {
        private final String id;
        private final String name;
        private PlaybackStrategy playbackStrategy;

        public Builder(String name) {
            this.id = UUID.randomUUID().toString();
            this.name = name;
        }

        public Builder withSubscription(SubscriptionTier tier, int songPlayed) {
            this.playbackStrategy = PlaybackStrategy.getStrategy(tier, songPlayed);
            return this;
        }

        public User build() {
            return new User(id, name, playbackStrategy);
        }
    }

}
