package entity;

import state.AvailableState;
import state.ItemState;

public class BookCopy {
    private String id;
    private final LibraryItem item;
    private ItemState currentState;

    public BookCopy(String id, LibraryItem item) {
        this.id = id;
        this.item = item;
        this.currentState = new AvailableState();
        item.addCopy(this);
    }

    public void checkout(Member member) {
        currentState.checkout(this, member);
    }

    public void returnCopy() {
        currentState.returnItem(this);
    }

    public void placeHold(Member member) {
        currentState.placeHold(this, member);
    }

    public String getId() {
        return id;
    }

    public LibraryItem getItem() {
        return item;
    }

    public void setState(ItemState newState) {
        this.currentState = newState;
    }

    public ItemState getState() {
        return currentState;
    }

    public boolean isAvailable() {
        return currentState instanceof AvailableState;
    }
}
