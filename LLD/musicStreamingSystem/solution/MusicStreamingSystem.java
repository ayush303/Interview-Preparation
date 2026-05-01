package LLD.musicStreamingSystem.solution;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import LLD.musicStreamingSystem.solution.model.Player;
import LLD.musicStreamingSystem.solution.model.Song;
import LLD.musicStreamingSystem.solution.model.User;
import LLD.musicStreamingSystem.solution.observer.Artist;
import LLD.musicStreamingSystem.solution.services.RecommendationService;
import LLD.musicStreamingSystem.solution.services.SearchService;
import LLD.musicStreamingSystem.solution.strategy.GenreBasedRecommendationStrategy;

public class MusicStreamingSystem {
    private static volatile MusicStreamingSystem instance;

    private final Map<String, Song> songs = new HashMap<>();
    private final Map<String, Artist> artists = new HashMap<>();
    private final Map<String, User> users = new HashMap<>();

    private final Player player;
    private final SearchService searchService;
    private RecommendationService recommendationService;

    public MusicStreamingSystem() {
        this.player = new Player();
        this.searchService = new SearchService();
        this.recommendationService = new RecommendationService(new GenreBasedRecommendationStrategy());
    }

    public static MusicStreamingSystem getInstance() {
        if (instance == null) {
            synchronized (MusicStreamingSystem.class) {
                if (instance == null) {
                    instance = new MusicStreamingSystem();
                }
            }
        }
        return instance;
    }

    public void registerUser(User user) {
        users.put(user.getId(), user);
    }

    public Song addSong(String id, String title, String artistId, int duration) {
        Song song = new Song(id, title, artists.get(artistId), duration);
        songs.put(song.getId(), song);
        return song;
    }

    public void addArtist(Artist artist) {
        artists.put(artist.getId(), artist);
    }

    public List<Song> searchSongsByTitle(String title) {
        return searchService.searchSongsByTitle(new ArrayList<>(songs.values()), title);
    }

    public List<Song> getSongRecommendations() {
        return recommendationService.generateRecommendations(new ArrayList<>(songs.values()));
    }

    public Player getPlayer() {
        return player;
    }

}
