package com.ecommerce.order.repository;

import com.ecommerce.order.model.entity.Order;
import com.ecommerce.order.model.entity.OrderItem;
import com.ecommerce.order.model.enums.OrderStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies the persistence layer against a REAL PostgreSQL (via Testcontainers).
 *
 * <p>{@code disabledWithoutDocker = true} means this test is gracefully SKIPPED
 * on machines without Docker, so {@code mvn test} stays green everywhere while
 * still running for real when Docker is available.
 */
@SpringBootTest
@Testcontainers(disabledWithoutDocker = true)
class OrderRepositoryPostgresIT {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @DynamicPropertySource
    static void datasourceProps(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "create-drop");
        registry.add("spring.cache.type", () -> "caffeine");
    }

    @Autowired
    private OrderRepository orderRepository;

    @Test
    @DisplayName("save and query by status / order number against real Postgres")
    void saveAndQuery() {
        Order order = Order.builder()
                .orderNumber("ORD-TEST-0001")
                .customerId(1001L)
                .status(OrderStatus.PENDING)
                .totalAmount(new BigDecimal("20.00"))
                .build();
        order.addItem(OrderItem.builder()
                .productId(101L).productSku("SKU-1").productName("Widget")
                .quantity(2).unitPrice(new BigDecimal("10.00")).build());

        orderRepository.save(order);

        assertThat(orderRepository.findByStatus(OrderStatus.PENDING, PageRequest.of(0, 10)))
                .isNotEmpty();
        assertThat(orderRepository.findByOrderNumber("ORD-TEST-0001")).isPresent();
        assertThat(orderRepository.countByStatus(OrderStatus.PENDING)).isGreaterThanOrEqualTo(1);
    }
}
