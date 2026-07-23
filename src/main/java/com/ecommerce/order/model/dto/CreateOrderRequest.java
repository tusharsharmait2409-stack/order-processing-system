package com.ecommerce.order.model.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;

/**
 * Payload for creating an order. Validated at the controller boundary with
 * {@code @Valid}; invalid requests never reach the service or the domain.
 */
public record CreateOrderRequest(

        @NotNull(message = "customerId is required")
        Long customerId,

        @Email(message = "customerEmail must be a valid email")
        String customerEmail,

        String customerName,

        String shippingAddress,

        String notes,

        @NotEmpty(message = "an order must contain at least one item")
        @Valid
        List<CreateOrderItemRequest> items
) {
}
