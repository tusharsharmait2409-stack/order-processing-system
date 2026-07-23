package com.ecommerce.order.model.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;

/** A single line item in a create-order request. */
public record CreateOrderItemRequest(

        @NotNull(message = "productId is required")
        Long productId,

        @NotBlank(message = "productSku is required")
        String productSku,

        @NotBlank(message = "productName is required")
        String productName,

        @Positive(message = "quantity must be greater than 0")
        int quantity,

        @NotNull(message = "unitPrice is required")
        @DecimalMin(value = "0.0", message = "unitPrice must not be negative")
        BigDecimal unitPrice
) {
}
