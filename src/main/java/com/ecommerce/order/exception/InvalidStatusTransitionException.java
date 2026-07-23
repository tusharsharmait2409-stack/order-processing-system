package com.ecommerce.order.exception;

import com.ecommerce.order.model.enums.OrderStatus;

/**
 * Thrown when a caller attempts an illegal order status change, e.g.
 * SHIPPED -> PENDING, or cancelling an order that is no longer PENDING.
 * Maps to HTTP 409 Conflict.
 */
public class InvalidStatusTransitionException extends RuntimeException {

    public InvalidStatusTransitionException(Long orderId, OrderStatus from, OrderStatus to) {
        super("Order %d cannot transition from %s to %s".formatted(orderId, from, to));
    }
}
