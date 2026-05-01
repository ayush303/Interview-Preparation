package LLD.musicStreamingSystem.solution.services;

import java.util.List;
import java.util.stream.Collectors;

import LLD.musicStreamingSystem.solution.model.Song;
import LLD.musicStreamingSystem.solution.observer.Artist;

public class SearchService {
    public List<Song> searchSongsByTitle(List<Song> songs, String query) {
        return songs.stream()
                .filter(s -> s.getTitle().toLowerCase().contains(query.toLowerCase()))
                .collect(Collectors.toList());
    }

    public List<Artist> searchArtistsByName(List<Artist> artists, String query) {
        return artists.stream()
                .filter(a -> a.getName().toLowerCase().contains(query.toLowerCase()))
                .collect(Collectors.toList());
    }
}
