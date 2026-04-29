package startegy;

import java.util.List;
import entity.LibraryItem;

public interface SearchStrategy {
    List<LibraryItem> search(String query, List<LibraryItem> items);
}
