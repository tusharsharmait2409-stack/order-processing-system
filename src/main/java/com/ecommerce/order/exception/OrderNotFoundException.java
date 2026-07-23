package com.ecommerce.order.exception;

/** Thrown when an order id or order number does not exist. Maps to HTTP 404. */
public class OrderNotFoundException extends RuntimeException {

    public OrderNotFoundException(Long orderId) {
        super("Order not found: " + orderId);
    }

    public OrderNotFoundException(String orderNumber) {
        super("Order not found: " + orderNumber);
    }
}
