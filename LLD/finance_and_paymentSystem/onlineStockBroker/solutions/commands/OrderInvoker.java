package LLD.finance_and_paymentSystem.onlineStockBroker.solutions.commands;

import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;

import LLD.finance_and_paymentSystem.onlineStockBroker.solutions.exceptions.InsufficientFundsException;
import LLD.finance_and_paymentSystem.onlineStockBroker.solutions.exceptions.InsufficientStockException;

/**
 * The INVOKER in the Command Pattern.
 *
 * Knows only the OrderCommand interface — has zero knowledge of:
 * - Whether a command is BUY or SELL
 * - What StockExchange is or how it works
 * - What Order, Account, or Stock even are
 *
 * Responsibilities:
 * 1. Accept commands via submit()
 * 2. Execute them (executeNext / executeAll)
 * 3. Undo them in reverse order (undoLast / undoAll)
 * 4. Maintain an append-only audit log of every action
 *
 * DATA STRUCTURES:
 *
 * pendingCommands → LinkedList used as a Queue (FIFO)
 * WHY Queue? Commands execute in the order they were submitted.
 * submit() adds to tail, executeNext() removes from head.
 *
 * executedCommands → ArrayDeque used as a Stack (LIFO)
 * WHY Stack? Undo reverses the most recent action first — same as Ctrl+Z.
 * After execute() succeeds → push to top.
 * undoLast() → pop from top.
 *
 * auditLog → ArrayList (append-only)
 * WHY append-only? Audit logs must be immutable records — never edited.
 * In production this would persist to a DB for SEBI/SEC compliance.
 */
public class OrderInvoker {

    private final Queue<OrderCommand> pendingCommands = new LinkedList<>();
    private final Deque<OrderCommand> executedCommands = new ArrayDeque<>();
    private final List<String> auditLog = new ArrayList<>();

    // ----------------------------------------------------------------
    // SUBMIT
    // ----------------------------------------------------------------

    /**
     * Queue a command for future execution.
     * The command is NOT executed here — purely registration.
     *
     * WHY separate submit from execute?
     * Enables batch processing: submit 100 orders at 9:14 AM,
     * executeAll() fires them all at market open 9:15 AM simultaneously.
     */
    public void submit(OrderCommand command) {
        pendingCommands.offer(command); // adds to tail of queue — O(1)
        System.out.printf("[Invoker] Queued    : %s%n", command.getDescription());
    }

    // ----------------------------------------------------------------
    // EXECUTE
    // ----------------------------------------------------------------

    /**
     * Execute the next pending command (FIFO order).
     *
     * EXCEPTION HANDLING:
     * InsufficientFundsException / InsufficientStockException are caught here,
     * NOT re-thrown — so one bad order never aborts the rest of the batch.
     *
     * IMPORTANT: command is pushed to executedCommands (undo stack) ONLY if
     * execute() succeeds. A failed command cannot be undone — there's nothing
     * to reverse since no state was changed.
     */
    public void executeNext() {
        if (pendingCommands.isEmpty()) {
            System.out.println("[Invoker] No pending commands.");
            return;
        }

        OrderCommand command = pendingCommands.poll(); // removes from head — O(1)
        try {
            command.execute();
            executedCommands.push(command); // push to top of undo stack — O(1)
            recordAudit("EXECUTED", command);

        } catch (InsufficientFundsException e) {
            // Pre-validation failed in BuyStockCommand — order never reached exchange
            System.out.printf("[Invoker] REJECTED (insufficient funds) : %s%n", e.getMessage());
            recordAudit("REJECTED_FUNDS", command);

        } catch (InsufficientStockException e) {
            // Pre-validation failed in SellStockCommand
            System.out.printf("[Invoker] REJECTED (insufficient stock) : %s%n", e.getMessage());
            recordAudit("REJECTED_STOCK", command);
        }
    }

    /**
     * Execute ALL pending commands in FIFO order.
     *
     * Use case: pre-market order queue — collect all orders from 9:00–9:14 AM,
     * fire everything at market open 9:15 AM in submission order.
     */
    public void executeAll() {
        System.out.printf("[Invoker] Executing all %d pending commands...%n", pendingCommands.size());
        while (!pendingCommands.isEmpty()) {
            executeNext();
        }
        System.out.println("[Invoker] All commands executed.");
    }

    // ----------------------------------------------------------------
    // UNDO
    // ----------------------------------------------------------------

    /**
     * Undo the most recently executed command (LIFO — top of stack).
     *
     * Calls OrderCommand.undo() which calls StockExchange.cancelOrder(order)
     * which calls order.getState().cancel(order) ← State Pattern handles it:
     * OpenState → marks CANCELLED (succeeds)
     * PartiallyFilledState → marks CANCELLED (succeeds)
     * FilledState → refuses gracefully ("already FILLED")
     * CancelledState → no-op ("already CANCELLED")
     *
     * WHY Deque as Stack (not java.util.Stack)?
     * java.util.Stack extends Vector — synchronized on every operation,
     * unnecessary overhead here. ArrayDeque is faster and unsynchronized
     * (we don't need thread safety on the invoker itself since it's per-user).
     */
    public void undoLast() {
        if (executedCommands.isEmpty()) {
            System.out.println("[Invoker] Nothing to undo.");
            return;
        }

        OrderCommand command = executedCommands.pop(); // pop from top — O(1)
        command.undo();
        recordAudit("UNDONE", command);
    }

    /**
     * Undo ALL executed commands in reverse order (most recent first).
     *
     * Use case: end-of-session rollback — cancel all open orders placed
     * in this session before market close.
     *
     * Note: FilledState.cancel() will refuse for already-filled orders —
     * they are logged as "UNDONE" but the underlying cancel is rejected
     * gracefully by the State Pattern. No exception is thrown.
     */
    public void undoAll() {
        System.out.printf("[Invoker] Undoing all %d executed commands (LIFO)...%n",
                executedCommands.size());
        while (!executedCommands.isEmpty()) {
            undoLast();
        }
        System.out.println("[Invoker] All commands undone.");
    }

    // ----------------------------------------------------------------
    // AUDIT
    // ----------------------------------------------------------------

    /**
     * Print the full audit trail — every execute, undo, and rejection with
     * timestamps.
     *
     * In production: ship these entries to a time-series DB or append-only log
     * file.
     * Required for SEBI audit compliance — every order action must be traceable.
     */
    public void printAuditLog() {
        System.out.println("\n=========== AUDIT LOG ===========");
        if (auditLog.isEmpty()) {
            System.out.println("  (empty)");
        } else {
            auditLog.forEach(entry -> System.out.println("  " + entry));
        }
        System.out.println("=================================\n");
    }

    private void recordAudit(String action, OrderCommand command) {
        // Instant.now() gives UTC timestamp — consistent regardless of server timezone
        String entry = String.format("[%s] %-20s → %s",
                Instant.now(), action, command.getDescription());
        auditLog.add(entry);
    }

    // ----------------------------------------------------------------
    // STATE QUERIES
    // ----------------------------------------------------------------

    public int getPendingCount() {
        return pendingCommands.size();
    }

    public int getExecutedCount() {
        return executedCommands.size();
    }

    public boolean hasPending() {
        return !pendingCommands.isEmpty();
    }
}