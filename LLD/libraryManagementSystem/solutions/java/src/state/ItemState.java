package state;

import entity.BookCopy;
import entity.Member;

public interface ItemState {

    void checkout(BookCopy copy, Member member);

    void returnItem(BookCopy copy);

    void placeHold(BookCopy copy, Member member);

}
