
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

import entity.BookCopy;
import entity.LibraryItem;
import entity.Member;
import enums.ItemType;
import factory.ItemFactory;
import startegy.SearchStrategy;

public class LibraryManagementSystem {
    private static final LibraryManagementSystem INSTANCE = new LibraryManagementSystem();
    private final ConcurrentHashMap<String, LibraryItem> catalog = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Member> members = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, BookCopy> copies = new ConcurrentHashMap<>();

    private LibraryManagementSystem() {
    }

    public static LibraryManagementSystem getInstance() {
        return INSTANCE;
    }

    // --- Item Management ---
    public List<BookCopy> addItem(ItemType type, String title, String author, String id, int numCopies) {
        List<BookCopy> bookCopies = new ArrayList<>();
        LibraryItem item = ItemFactory.createItem(type, title, author, id);
        catalog.put(id, item);

        for (int i = 0; i < numCopies; i++) {
            String copyId = id + "-c" + (i + 1);
            BookCopy copy = new BookCopy(copyId, item);
            copies.put(copyId, copy);
            bookCopies.add(copy);
        }
        System.out.println("Added " + numCopies + " copies of '" + title + "'");
        return bookCopies;
    }

    // --- User Management ---
    public Member addMember(String id, String name) {
        Member member = new Member(id, name);
        members.put(id, member);
        return member;
    }

    // --- Core Actions ---
    public void checkout(String memberId, String copyId) {
        Member member = members.get(memberId);
        BookCopy copy = copies.get(copyId);
        if (member != null && copy != null) {
            copy.checkout(member);
        } else {
            System.out.println("Error: Invalid member or copy ID.");
        }
    }

    public void returnItem(String copyId) {
        BookCopy copy = copies.get(copyId);
        if (copy != null) {
            copy.returnCopy();
        } else {
            System.out.println("Error: Invalid copy ID.");
        }
    }

    public void placeHold(String memberId, String itemId) {
        Member member = members.get(memberId);
        LibraryItem item = catalog.get(itemId);
        if (member != null && item != null) {
            // Place hold on any copy that is checked out
            item.getCopies().stream()
                    .filter(c -> !c.isAvailable())
                    .findFirst()
                    .ifPresent(copy -> copy.placeHold(member));
        }
    }

    // --- Search (Using Strategy Pattern) ---
    public List<LibraryItem> search(String query, SearchStrategy strategy) {
        return strategy.search(query, new ArrayList<>(catalog.values()));
    }

    public void printCatalog() {
        System.out.println("\n--- Library Catalog ---");
        catalog.values().forEach(item -> System.out.printf("ID: %s, Title: %s, Author/Publisher: %s, Available: %d\n",
                item.getId(), item.getTitle(), item.getAuthorOrPublisher(), item.getAvailableCopyCount()));
        System.out.println("-----------------------\n");
    }
}
