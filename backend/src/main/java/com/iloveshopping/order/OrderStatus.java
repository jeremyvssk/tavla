// The order's statuses, and the only moves allowed between them and who may make each one.
package com.iloveshopping.order;

import java.util.EnumMap;
import java.util.Map;
import java.util.Set;

/**
 * claude-docs/ORDER_STATES.md is this table in words. A move that isn't listed here is refused by
 * {@link Order#moveTo}, which is the only code that changes an order's status.
 */
public enum OrderStatus {
    PENDING_PAYMENT,
    PAYMENT_SUCCESSFUL,
    PAYMENT_FAILED,
    PROCESSING,
    SHIPPED,
    DELIVERED,
    CANCELLED,
    REFUNDED;

    private static final Map<OrderStatus, Map<OrderStatus, Set<OrderActor>>> MOVES = new EnumMap<>(OrderStatus.class);

    static {
        // Stripe's webhook decides paid or failed; the timer releases an unpaid order once its hold runs out.
        MOVES.put(PENDING_PAYMENT, Map.of(
                PAYMENT_SUCCESSFUL, Set.of(OrderActor.STRIPE),
                PAYMENT_FAILED, Set.of(OrderActor.STRIPE),
                CANCELLED, Set.of(OrderActor.TIMER)));
        // A failed order can be paid again with another card while the hold lasts. A success that lands
        // here (a late webhook for an earlier attempt) still counts: the money moved.
        MOVES.put(PAYMENT_FAILED, Map.of(
                PENDING_PAYMENT, Set.of(OrderActor.SHOPPER),
                PAYMENT_SUCCESSFUL, Set.of(OrderActor.STRIPE),
                CANCELLED, Set.of(OrderActor.TIMER)));
        // The shopper can cancel until packing starts. Staff can still cancel while it is being packed (Processing),
        // before it leaves; after that the way back is a refund, not a cancellation.
        MOVES.put(PAYMENT_SUCCESSFUL, Map.of(
                PROCESSING, Set.of(OrderActor.ADMIN),
                CANCELLED, Set.of(OrderActor.SHOPPER, OrderActor.ADMIN)));
        MOVES.put(PROCESSING, Map.of(
                SHIPPED, Set.of(OrderActor.ADMIN),
                CANCELLED, Set.of(OrderActor.ADMIN)));
        MOVES.put(SHIPPED, Map.of(DELIVERED, Set.of(OrderActor.ADMIN)));
        // A cancelled order that was paid becomes Refunded when Stripe confirms the refund (step 7).
        MOVES.put(CANCELLED, Map.of(REFUNDED, Set.of(OrderActor.STRIPE)));
        MOVES.put(DELIVERED, Map.of());
        MOVES.put(REFUNDED, Map.of());
    }

    public boolean allows(OrderStatus next, OrderActor actor) {
        return MOVES.get(this).getOrDefault(next, Set.of()).contains(actor);
    }

    /** Placed but not paid: the stock is held for it until {@code hold_until}. */
    public boolean awaitsPayment() {
        return this == PENDING_PAYMENT || this == PAYMENT_FAILED;
    }
}
