package LLD.online_food_Delivery_Service.solution.enums;

import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * Order lifecycle, declared in lifecycle order so ordinal() and compareTo follow it.
 *
 * The legal transitions live here, in one immutable table, and nowhere else.
 * Order.transitionTo is the only caller, so cancel and every status update
 * are validated by the same rule.
 */
public enum OrderStatus {
    PENDING,
    CONFIRMED,
    PREPARING,
    READY_FOR_PICKUP,
    OUT_FOR_DELIVERY,
    DELIVERED,
    CANCELLED;

    private static final Map<OrderStatus, Set<OrderStatus>> VALID_TRANSITIONS = Map.of(
            PENDING, EnumSet.of(CONFIRMED, CANCELLED),
            CONFIRMED, EnumSet.of(PREPARING, CANCELLED),
            PREPARING, EnumSet.of(READY_FOR_PICKUP),
            READY_FOR_PICKUP, EnumSet.of(OUT_FOR_DELIVERY),
            OUT_FOR_DELIVERY, EnumSet.of(DELIVERED),
            DELIVERED, EnumSet.noneOf(OrderStatus.class),
            CANCELLED, EnumSet.noneOf(OrderStatus.class));

    public boolean canTransitionTo(OrderStatus next) {
        return VALID_TRANSITIONS.get(this).contains(next);
    }

    public boolean isTerminal() {
        return VALID_TRANSITIONS.get(this).isEmpty();
    }
}
