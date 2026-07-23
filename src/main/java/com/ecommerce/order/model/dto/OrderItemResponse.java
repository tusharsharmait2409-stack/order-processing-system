package com.ecommerce.order.model.dto;

import java.io.Serializable;
import java.math.BigDecimal;

/** Read model for a single order line item. */
public record OrderItemResponse(
        Long id,
        Long productId,
        String productSku,
        String productName,
        int quantity,
        BigDecimal unitPrice,
        BigDecimal lineTotal
) implements Serializable {
}
