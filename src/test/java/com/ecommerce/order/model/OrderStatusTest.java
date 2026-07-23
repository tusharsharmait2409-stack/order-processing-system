package com.ecommerce.order.model;

import com.ecommerce.order.model.enums.OrderStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

/** Unit tests for the order status state machine. */
class OrderStatusTest {

    @ParameterizedTest(name = "{0} -> {1} is legal")
    @CsvSource({
            "PENDING,PROCESSING",
            "PENDING,CANCELLED",
            "PROCESSING,SHIPPED",
            "SHIPPED,DELIVERED"
    })
    @DisplayName("legal transitions are allowed")
    void legalTransitions(OrderStatus from, OrderStatus to) {
        assertThat(from.canTransitionTo(to)).isTrue();
    }

    @ParameterizedTest(name = "{0} -> {1} is illegal")
    @CsvSource({
            "PENDING,SHIPPED",
            "PENDING,DELIVERED",
            "PROCESSING,PENDING",
            "PROCESSING,CANCELLED",
            "SHIPPED,PROCESSING",
            "DELIVERED,SHIPPED",
            "CANCELLED,PROCESSING"
    })
    @DisplayName("illegal transitions are rejected")
    void illegalTransitions(OrderStatus from, OrderStatus to) {
        assertThat(from.canTransitionTo(to)).isFalse();
    }

    @Test
    @DisplayName("DELIVERED and CANCELLED are terminal")
    void terminalStates() {
        assertThat(OrderStatus.DELIVERED.isTerminal()).isTrue();
        assertThat(OrderStatus.CANCELLED.isTerminal()).isTrue();
        assertThat(OrderStatus.PENDING.isTerminal()).isFalse();
    }
}
