package LLD.musicStreamingSystem.solution.services;

import java.util.List;

import LLD.musicStreamingSystem.solution.model.Song;
import LLD.musicStreamingSystem.solution.strategy.RecommendationStrategy;

public class RecommendationService {
    private RecommendationStrategy recommendationStrategy;

    public RecommendationService(RecommendationStrategy recommendationStrategy) {
        this.recommendationStrategy = recommendationStrategy;
    }

    public void setStrategy(RecommendationStrategy recommendationStrategy) {
        this.recommendationStrategy = recommendationStrategy;
    }

    public List<Song> generateRecommendations(List<Song> songs) {
        return recommendationStrategy.recommend(songs);
    }
}
