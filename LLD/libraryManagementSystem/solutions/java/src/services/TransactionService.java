package services;

import java.util.HashMap;
import java.util.Map;

import entity.BookCopy;
import entity.Loan;
import entity.Member;

public class TransactionService {

    private static final TransactionService INSTANCE = new TransactionService();
    private final Map<String, Loan> activeLoans = new HashMap<>();
    // key: BookCopy ID, value: Loan

    private TransactionService() {
    }

    public static TransactionService getInstance() {
        return INSTANCE;
    }

    public void createLoan(Member member, BookCopy copy) {
        if (activeLoans.containsKey(copy.getId())) {
            throw new IllegalStateException("This copy is already on loan.");
        }
        Loan loan = new Loan(copy, member);
        activeLoans.put(copy.getId(), loan);
        member.addLoan(loan);
    }

    public void endLoan(BookCopy copy) {
        Loan loan = activeLoans.remove(copy.getId());
        if (loan != null) {
            loan.getMember().removeLoan(loan);
        }
    }

}
