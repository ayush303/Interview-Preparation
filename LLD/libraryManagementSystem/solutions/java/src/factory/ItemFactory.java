package factory;

import entity.Book;
import entity.LibraryItem;
import entity.Magazine;
import enums.ItemType;

public class ItemFactory {

    public static LibraryItem createItem(ItemType type, String id, String title, String author) {
        switch (type) {
            case BOOK:
                return new Book(id, title, author);
            case MAGAZINE:
                return new Magazine(id, title, author);
            default:
                throw new IllegalArgumentException("Invalid item type: " + type);
        }
    }

}
