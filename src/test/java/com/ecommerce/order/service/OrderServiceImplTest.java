package com.ecommerce.order.service;

import com.ecommerce.order.exception.InvalidStatusTransitionException;
import com.ecommerce.order.exception.OrderNotFoundException;
import com.ecommerce.order.mapper.OrderMapper;
import com.ecommerce.order.metrics.OrderMetrics;
import com.ecommerce.order.model.dto.CreateOrderItemRequest;
import com.ecommerce.order.model.dto.CreateOrderRequest;
import com.ecommerce.order.model.dto.OrderResponse;
import com.ecommerce.order.model.dto.OrderStatisticsResponse;
import com.ecommerce.order.model.entity.Order;
import com.ecommerce.order.model.enums.OrderStatus;
import com.ecommerce.order.repository.OrderRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/** Unit tests for {@link OrderServiceImpl} using mocked collaborators. */
@ExtendWith(MockitoExtension.class)
class OrderServiceImplTest {

    @Mock private OrderRepository orderRepository;
    @Mock private OrderMapper orderMapper;
    @Mock private OrderMetrics orderMetrics;

    @InjectMocks private OrderServiceImpl orderService;

    private OrderResponse sampleResponse;

    @BeforeEach
    void setUp() {
        sampleResponse = new OrderResponse(1L, "ORD-20260723-ABC12345", 1001L,
                "a@b.com", "Jane", "1 St", "note", OrderStatus.PENDING,
                new BigDecimal("10.00"), List.of(), Instant.now(), Instant.now());
    }

    @Test
    @DisplayName("createOrder assigns an order number, persists, records metrics")
    @SuppressWarnings("unchecked")
    void createOrder() {
        CreateOrderRequest request = new CreateOrderRequest(1001L, "a@b.com", "Jane", "1 St", "note",
                List.of(new CreateOrderItemRequest(1L, "SKU-1", "Widget", 2, new BigDecimal("5.00"))));
        Order entity = Order.builder().customerId(1001L).status(OrderStatus.PENDING).build();

        when(orderMetrics.recordCreationLatency(any(Supplier.class)))
                .thenAnswer(inv -> ((Supplier<Object>) inv.getArgument(0)).get());
        when(orderMapper.toEntity(request)).thenReturn(entity);
        when(orderRepository.save(entity)).thenReturn(entity);
        when(orderMapper.toResponse(entity)).thenReturn(sampleResponse);

        OrderResponse result = orderService.createOrder(request);

        assertThat(result).isEqualTo(sampleResponse);
        assertThat(entity.getOrderNumber()).startsWith("ORD-");
        verify(orderRepository).save(entity);
        verify(orderMetrics).incrementCreated();
    }

    @Test
    @DisplayName("getOrder returns mapped response when found")
    void getOrderFound() {
        Order entity = Order.builder().customerId(1001L).status(OrderStatus.PENDING).build();
        when(orderRepository.findById(1L)).thenReturn(Optional.of(entity));
        when(orderMapper.toResponse(entity)).thenReturn(sampleResponse);

        assertThat(orderService.getOrder(1L)).isEqualTo(sampleResponse);
    }

    @Test
    @DisplayName("getOrder throws when missing")
    void getOrderNotFound() {
        when(orderRepository.findById(99L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> orderService.getOrder(99L))
                .isInstanceOf(OrderNotFoundException.class);
    }

    @Test
    @DisplayName("getByOrderNumber throws when missing")
    void getByOrderNumberNotFound() {
        when(orderRepository.findByOrderNumber("nope")).thenReturn(Optional.empty());
        assertThatThrownBy(() -> orderService.getByOrderNumber("nope"))
                .isInstanceOf(OrderNotFoundException.class);
    }

    @Test
    @DisplayName("listOrders with no filters uses findAll")
    void listOrdersNoFilter() {
        Pageable pageable = PageRequest.of(0, 20);
        Order entity = Order.builder().customerId(1001L).status(OrderStatus.PENDING).build();
        when(orderRepository.findAll(pageable)).thenReturn(new PageImpl<>(List.of(entity), pageable, 1));
        when(orderMapper.toResponse(any())).thenReturn(sampleResponse);

        Page<OrderResponse> result = orderService.listOrders(null, null, pageable);

        assertThat(result.getContent()).hasSize(1);
        verify(orderRepository).findAll(pageable);
    }

    @Test
    @DisplayName("listOrders with status + customer uses the combined query")
    void listOrdersBothFilters() {
        Pageable pageable = PageRequest.of(0, 20);
        when(orderRepository.findByStatusAndCustomerId(OrderStatus.PENDING, 1001L, pageable))
                .thenReturn(new PageImpl<>(List.of(), pageable, 0));

        orderService.listOrders(OrderStatus.PENDING, 1001L, pageable);

        verify(orderRepository).findByStatusAndCustomerId(OrderStatus.PENDING, 1001L, pageable);
    }

    @Test
    @DisplayName("updateStatus applies a legal transition and records it")
    void updateStatusLegal() {
        Order entity = Order.builder().customerId(1001L).status(OrderStatus.PENDING).build();
        when(orderRepository.findById(1L)).thenReturn(Optional.of(entity));
        when(orderMapper.toResponse(entity)).thenReturn(sampleResponse);

        orderService.updateStatus(1L, OrderStatus.PROCESSING, "verified");

        assertThat(entity.getStatus()).isEqualTo(OrderStatus.PROCESSING);
        verify(orderMetrics).recordTransition(OrderStatus.PENDING, OrderStatus.PROCESSING);
    }

    @Test
    @DisplayName("updateStatus rejects an illegal transition")
    void updateStatusIllegal() {
        Order entity = Order.builder().customerId(1001L).status(OrderStatus.SHIPPED).build();
        when(orderRepository.findById(1L)).thenReturn(Optional.of(entity));

        assertThatThrownBy(() -> orderService.updateStatus(1L, OrderStatus.PENDING, null))
                .isInstanceOf(InvalidStatusTransitionException.class);
    }

    @Test
    @DisplayName("cancelOrder cancels a PENDING order")
    void cancelOrder() {
        Order entity = Order.builder().customerId(1001L).status(OrderStatus.PENDING).build();
        when(orderRepository.findById(1L)).thenReturn(Optional.of(entity));
        when(orderMapper.toResponse(entity)).thenReturn(sampleResponse);

        orderService.cancelOrder(1L);

        assertThat(entity.getStatus()).isEqualTo(OrderStatus.CANCELLED);
        verify(orderMetrics).incrementCancelled();
    }

    @Test
    @DisplayName("getStatistics aggregates counts and revenue")
    void getStatistics() {
        when(orderRepository.countByStatus(any())).thenReturn(2L);
        when(orderRepository.sumTotalAmountExcludingStatus(OrderStatus.CANCELLED))
                .thenReturn(new BigDecimal("500.00"));

        OrderStatisticsResponse stats = orderService.getStatistics();

        assertThat(stats.totalOrders()).isEqualTo(10L); // 5 statuses x 2
        assertThat(stats.totalRevenue()).isEqualByComparingTo("500.00");
        assertThat(stats.countByStatus()).containsKey(OrderStatus.PENDING);
    }

    @Test
    @DisplayName("promotePendingToProcessing returns 0 when nothing to do")
    void promoteNothing() {
        when(orderRepository.findIdsByStatus(eq(OrderStatus.PENDING), any(Pageable.class)))
                .thenReturn(List.of());
        assertThat(orderService.promotePendingToProcessing(100)).isZero();
        verify(orderRepository, never()).bulkTransition(any(), any(), any(), any());
    }

    @Test
    @DisplayName("promotePendingToProcessing bulk-transitions a batch")
    void promoteBatch() {
        List<Long> ids = List.of(1L, 2L);
        when(orderRepository.findIdsByStatus(eq(OrderStatus.PENDING), any(Pageable.class)))
                .thenReturn(ids);
        when(orderRepository.bulkTransition(eq(OrderStatus.PENDING), eq(OrderStatus.PROCESSING),
                eq(ids), any(Instant.class))).thenReturn(2);

        assertThat(orderService.promotePendingToProcessing(100)).isEqualTo(2);
        verify(orderMetrics).recordPendingSwept(2);
    }
}
