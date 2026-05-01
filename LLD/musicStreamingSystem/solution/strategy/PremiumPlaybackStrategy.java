package LLD.musicStreamingSystem.solution.strategy;

import LLD.musicStreamingSystem.solution.model.Player;
import LLD.musicStreamingSystem.solution.model.Song;

public class PremiumPlaybackStrategy implements PlaybackStrategy {
    @Override
    public void play(Song song, Player player) {
        player.setCurrentSong(song);
        System.out.printf("Premium User is now playing: %s%n", song);
    }

}
