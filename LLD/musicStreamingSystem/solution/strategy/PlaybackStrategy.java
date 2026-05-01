package LLD.musicStreamingSystem.solution.strategy;

import LLD.musicStreamingSystem.solution.enums.SubscriptionTier;
import LLD.musicStreamingSystem.solution.model.Player;
import LLD.musicStreamingSystem.solution.model.Song;

public interface PlaybackStrategy {
    void play(Song song, Player player);

    // Simple Factory method to get the correct strategy
    static PlaybackStrategy getStrategy(SubscriptionTier tier, int songsPlayed) {
        return tier == SubscriptionTier.PREMIUM ? new PremiumPlaybackStrategy() : new FreePlaybackStrategy(songsPlayed);
    }
}
