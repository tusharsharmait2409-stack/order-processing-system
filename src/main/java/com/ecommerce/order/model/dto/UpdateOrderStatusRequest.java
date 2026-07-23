package com.ecommerce.order.model.dto;

import com.ecommerce.order.model.enums.OrderStatus;
import jakarta.validation.constraints.NotNull;

/** Payload for updating an order's status, with an optional audit reason. */
public record UpdateOrderStatusRequest(

        @NotNull(message = "status is required")
        OrderStatus status,

        String reason
) {
}
