package LLD.finance_and_paymentSystem.onlineStockBroker.solutions.models;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Represents a user's brokerage account.
 * All public methods are synchronized to ensure thread safety when
 * multiple trades involving the same user execute concurrently.
 *
 * KEY FIX over original: debit() and removeStock() validate BEFORE modifying
 * state
 * and return false on failure instead of silently going negative.
 */
public class Account {
    private double balance;
    private final Map<String, Integer> portfolio; // Stock symbol -> quantity

    public Account(double initialCash) {
        this.balance = initialCash;
        this.portfolio = new ConcurrentHashMap<>();
    }

    // --- Balance Operations ---

    /**
     * Check without modifying — used for pre-validation before placing an order.
     */
    public synchronized boolean hasSufficientBalance(double amount) {
        return balance >= amount;
    }

    /**
     * Debit fails safely: returns false if insufficient funds.
     * This prevents the account going negative if concurrent trades race.
     * Original code called debit() directly without checking, risking negative
     * balance.
     */
    public synchronized boolean debit(double amount) {
        if (balance < amount) {
            System.out.printf("  [Account] Debit FAILED — balance: ₹%.2f, required: ₹%.2f%n", balance, amount);
            return false;
        }
        balance -= amount;
        return true;
    }

    public synchronized void credit(double amount) {
        balance += amount;
    }

    // --- Portfolio Operations ---

    /**
     * Check without modifying — used for pre-validation before placing a sell
     * order.
     */
    public synchronized boolean hasSufficientStock(String symbol, int quantity) {
        return portfolio.getOrDefault(symbol, 0) >= quantity;
    }

    public synchronized void addStock(String symbol, int quantity) {
        // merge: if key exists, add quantity; if not, insert quantity
        portfolio.merge(symbol, quantity, Integer::sum);
    }

    /**
     * Returns false if seller doesn't have enough stock.
     * Defensive check even after pre-validation, because state can change
     * between validation and execution in a concurrent system.
     */
    public synchronized boolean removeStock(String symbol, int quantity) {
        int current = portfolio.getOrDefault(symbol, 0);
        if (current < quantity) {
            System.out.printf("  [Account] Remove FAILED — %s held: %d, required: %d%n", symbol, current, quantity);
            return false;
        }
        int remaining = current - quantity;
        if (remaining == 0) {
            portfolio.remove(symbol); // Clean up zero-quantity entries
        } else {
            portfolio.put(symbol, remaining);
        }
        return true;
    }

    public synchronized int getStockQuantity(String symbol) {
        return portfolio.getOrDefault(symbol, 0);
    }

    @Override
    public synchronized String toString() {
        return String.format("Balance: ₹%.2f | Portfolio: %s", balance, portfolio);
    }

    public double getBalance() {
        return balance;
    }

    public Map<String, Integer> getPortfolio() {
        return portfolio;
    }
}
