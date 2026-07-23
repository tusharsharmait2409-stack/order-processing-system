package com.ecommerce.order.model.dto;

import com.ecommerce.order.model.enums.OrderStatus;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * Read model returned by the API. Kept separate from the {@code Order} entity so
 * the public contract doesn't leak persistence details. Implements
 * {@link Serializable} so it can be cached in Redis.
 */
public record OrderResponse(
        Long id,
        String orderNumber,
        Long customerId,
        String customerEmail,
        String customerName,
        String shippingAddress,
        String notes,
        OrderStatus status,
        BigDecimal totalAmount,
        List<OrderItemResponse> items,
        Instant createdAt,
        Instant updatedAt
) implements Serializable {
}
