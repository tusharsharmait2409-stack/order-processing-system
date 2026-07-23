package com.ecommerce.order.service;

import com.ecommerce.order.config.CacheConfig;
import com.ecommerce.order.exception.OrderNotFoundException;
import com.ecommerce.order.mapper.OrderMapper;
import com.ecommerce.order.metrics.OrderMetrics;
import com.ecommerce.order.model.dto.CreateOrderRequest;
import com.ecommerce.order.model.dto.OrderResponse;
import com.ecommerce.order.model.dto.OrderStatisticsResponse;
import com.ecommerce.order.model.entity.Order;
import com.ecommerce.order.model.enums.OrderStatus;
import com.ecommerce.order.repository.OrderRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Default {@link OrderService} implementation.
 *
 * <p>Transaction boundaries live here (reads are {@code readOnly}). Single-order
 * look-ups are cached under the {@code orders} cache and evicted on any change.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class OrderServiceImpl implements OrderService {

    private static final DateTimeFormatter ORDER_NO_DATE = DateTimeFormatter.ofPattern("yyyyMMdd");

    private final OrderRepository orderRepository;
    private final OrderMapper orderMapper;
    private final OrderMetrics orderMetrics;

    @Override
    @Transactional
    public OrderResponse createOrder(CreateOrderRequest request) {
        return orderMetrics.recordCreationLatency(() -> {
            Order order = orderMapper.toEntity(request);
            order.setOrderNumber(generateOrderNumber());
            Order saved = orderRepository.save(order);
            orderMetrics.incrementCreated();
            log.info("Created order {} ({}) for customer {} with {} item(s), total {}",
                    saved.getId(), saved.getOrderNumber(), saved.getCustomerId(),
                    saved.getItems().size(), saved.getTotalAmount());
            return orderMapper.toResponse(saved);
        });
    }

    @Override
    @Transactional(readOnly = true)
    @Cacheable(cacheNames = CacheConfig.ORDERS_CACHE, key = "#id")
    public OrderResponse getOrder(Long id) {
        return orderRepository.findById(id)
                .map(orderMapper::toResponse)
                .orElseThrow(() -> new OrderNotFoundException(id));
    }

    @Override
    @Transactional(readOnly = true)
    public OrderResponse getByOrderNumber(String orderNumber) {
        return orderRepository.findByOrderNumber(orderNumber)
                .map(orderMapper::toResponse)
                .orElseThrow(() -> new OrderNotFoundException(orderNumber));
    }

    @Override
    @Transactional(readOnly = true)
    public Page<OrderResponse> listOrders(OrderStatus status, Long customerId, Pageable pageable) {
        Page<Order> page;
        if (status != null && customerId != null) {
            page = orderRepository.findByStatusAndCustomerId(status, customerId, pageable);
        } else if (status != null) {
            page = orderRepository.findByStatus(status, pageable);
        } else if (customerId != null) {
            page = orderRepository.findByCustomerId(customerId, pageable);
        } else {
            page = orderRepository.findAll(pageable);
        }
        return page.map(orderMapper::toResponse);
    }

    @Override
    @Transactional
    @CacheEvict(cacheNames = CacheConfig.ORDERS_CACHE, key = "#id")
    public OrderResponse updateStatus(Long id, OrderStatus target, String reason) {
        Order order = orderRepository.findById(id)
                .orElseThrow(() -> new OrderNotFoundException(id));
        OrderStatus from = order.getStatus();
        order.transitionTo(target); // enforces legal transitions; throws if illegal
        orderMetrics.recordTransition(from, target);
        log.info("Order {} status {} -> {}{}", id, from, target,
                reason != null ? " (reason: " + reason + ")" : "");
        return orderMapper.toResponse(order);
    }

    @Override
    @Transactional
    @CacheEvict(cacheNames = CacheConfig.ORDERS_CACHE, key = "#id")
    public OrderResponse cancelOrder(Long id) {
        Order order = orderRepository.findById(id)
                .orElseThrow(() -> new OrderNotFoundException(id));
        order.cancel(); // allowed only from PENDING; throws otherwise
        orderMetrics.incrementCancelled();
        orderMetrics.recordTransition(OrderStatus.PENDING, OrderStatus.CANCELLED);
        log.info("Order {} cancelled", id);
        return orderMapper.toResponse(order);
    }

    @Override
    @Transactional(readOnly = true)
    public OrderStatisticsResponse getStatistics() {
        Map<OrderStatus, Long> counts = new EnumMap<>(OrderStatus.class);
        for (OrderStatus s : OrderStatus.values()) {
            counts.put(s, orderRepository.countByStatus(s));
        }
        long total = counts.values().stream().mapToLong(Long::longValue).sum();
        BigDecimal revenue = orderRepository.sumTotalAmountExcludingStatus(OrderStatus.CANCELLED);
        return new OrderStatisticsResponse(total, counts, revenue);
    }

    @Override
    @Transactional
    @CacheEvict(cacheNames = CacheConfig.ORDERS_CACHE, allEntries = true)
    public int promotePendingToProcessing(int batchSize) {
        List<Long> ids = orderRepository.findIdsByStatus(
                OrderStatus.PENDING, PageRequest.of(0, batchSize));
        if (ids.isEmpty()) {
            return 0;
        }
        int promoted = orderRepository.bulkTransition(
                OrderStatus.PENDING, OrderStatus.PROCESSING, ids, Instant.now());
        orderMetrics.recordPendingSwept(promoted);
        return promoted;
    }

    /** ORD-yyyyMMdd-XXXXXXXX (8 hex chars) — unique, human-friendly business key. */
    private String generateOrderNumber() {
        String date = LocalDate.now().format(ORDER_NO_DATE);
        String suffix = UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        return "ORD-" + date + "-" + suffix;
    }
}
