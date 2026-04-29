package startegy;

import java.util.ArrayList;
import java.util.List;

import entity.Book;
import entity.LibraryItem;

public class SearchByAuthorStrategy implements SearchStrategy {

    @Override
    public List<LibraryItem> search(String query, List<LibraryItem> items) {
        List<LibraryItem> results = new ArrayList<>();
        items.stream()
                .filter(item -> item.getAuthorOrPublisher().toLowerCase().contains(query.toLowerCase()))
                .forEach(results::add);
        return results;
    }

}
