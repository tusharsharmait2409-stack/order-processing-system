package com.ecommerce.order.model.enums;

import java.util.Set;

/**
 * Lifecycle states of an order.
 *
 * <p>Legal transitions are encoded on the enum itself (State pattern) so the
 * rules live in exactly one place:
 *
 * <pre>
 *   PENDING ──▶ PROCESSING ──▶ SHIPPED ──▶ DELIVERED
 *      │
 *      └──▶ CANCELLED        (only from PENDING)
 * </pre>
 *
 * DELIVERED and CANCELLED are terminal states.
 */
public enum OrderStatus {

    PENDING,
    PROCESSING,
    SHIPPED,
    DELIVERED,
    CANCELLED;

    /** Statuses this status is allowed to move to. */
    public Set<OrderStatus> allowedTransitions() {
        return switch (this) {
            case PENDING -> Set.of(PROCESSING, CANCELLED);
            case PROCESSING -> Set.of(SHIPPED);
            case SHIPPED -> Set.of(DELIVERED);
            case DELIVERED, CANCELLED -> Set.of();
        };
    }

    /** @return true if moving from this status to {@code target} is permitted. */
    public boolean canTransitionTo(OrderStatus target) {
        return allowedTransitions().contains(target);
    }

    /** @return true if no further transitions are possible from this status. */
    public boolean isTerminal() {
        return allowedTransitions().isEmpty();
    }
}
