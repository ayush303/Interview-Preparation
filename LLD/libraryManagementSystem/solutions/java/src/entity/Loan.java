package entity;

import java.time.LocalDate;

public class Loan {

    private final BookCopy bookCopy;
    private final Member member;
    private final LocalDate checkoutDate;

    public Loan(BookCopy bookCopy, Member member) {
        this.bookCopy = bookCopy;
        this.member = member;
        this.checkoutDate = LocalDate.now();
    }

    public BookCopy getBookCopy() {
        return bookCopy;
    }

    public Member getMember() {
        return member;
    }

    public LocalDate getCheckoutDate() {
        return checkoutDate;
    }

}
