package LLD.finance_and_paymentSystem.onlineStockBroker.solutions.state;

import LLD.finance_and_paymentSystem.onlineStockBroker.solutions.models.Order;

/**
 * State Pattern interface for order lifecycle.
 *
 * WHY State Pattern?
 * An order's behavior changes based on its current status.
 * For example:
 * - You CAN cancel an OPEN order.
 * - You CANNOT cancel a FILLED order.
 * - A PARTIALLY_FILLED order can still be cancelled (for the remainder).
 *
 * Without State Pattern, this logic would be a nest of if/else in Order itself.
 * With State Pattern, each state class encapsulates its own rules.
 */
public interface OrderState {
    void handle(Order order); // Perform state-specific action

    void cancel(Order order); // Attempt to cancel; behavior differs per state

    String getStateName();
}