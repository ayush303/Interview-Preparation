package LLD.musicStreamingSystem.solution.strategy;

import java.util.List;

import LLD.musicStreamingSystem.solution.model.Song;

public interface RecommendationStrategy {
    List<Song> recommend(List<Song> allSongs);
}
