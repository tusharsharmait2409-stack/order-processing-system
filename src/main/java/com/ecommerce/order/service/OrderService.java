package com.ecommerce.order.service;

import com.ecommerce.order.model.dto.CreateOrderRequest;
import com.ecommerce.order.model.dto.OrderResponse;
import com.ecommerce.order.model.dto.OrderStatisticsResponse;
import com.ecommerce.order.model.enums.OrderStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

/**
 * Application service for order use-cases. Programming to this interface
 * (Dependency Inversion) keeps controllers and the scheduler decoupled from the
 * concrete implementation and easy to unit-test.
 */
public interface OrderService {

    OrderResponse createOrder(CreateOrderRequest request);

    OrderResponse getOrder(Long id);

    OrderResponse getByOrderNumber(String orderNumber);

    Page<OrderResponse> listOrders(OrderStatus status, Long customerId, Pageable pageable);

    OrderResponse updateStatus(Long id, OrderStatus target, String reason);

    OrderResponse cancelOrder(Long id);

    OrderStatisticsResponse getStatistics();

    /** Promote up to {@code batchSize} PENDING orders to PROCESSING. */
    int promotePendingToProcessing(int batchSize);
}
