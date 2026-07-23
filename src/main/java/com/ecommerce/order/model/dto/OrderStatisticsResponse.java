package com.ecommerce.order.model.dto;

import com.ecommerce.order.model.enums.OrderStatus;

import java.math.BigDecimal;
import java.util.Map;

/**
 * Aggregate order statistics for dashboards/monitoring:
 * total order count, a breakdown by status, and total revenue
 * (sum of totalAmount across non-cancelled orders).
 */
public record OrderStatisticsResponse(
        long totalOrders,
        Map<OrderStatus, Long> countByStatus,
        BigDecimal totalRevenue
) {
}
