package com.ecommerce.order.model;

import com.ecommerce.order.exception.InvalidStatusTransitionException;
import com.ecommerce.order.model.entity.Order;
import com.ecommerce.order.model.entity.OrderItem;
import com.ecommerce.order.model.enums.OrderStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Unit tests for the rich-domain behaviour on {@link Order}. */
class OrderTest {

    private Order pendingOrder() {
        return Order.builder()
                .customerId(1001L)
                .status(OrderStatus.PENDING)
                .build();
    }

    @Test
    @DisplayName("recalculateTotal sums line totals")
    void recalculateTotal() {
        Order order = pendingOrder();
        order.addItem(OrderItem.builder().productId(1L).productSku("A").productName("A")
                .quantity(2).unitPrice(new BigDecimal("10.00")).build());
        order.addItem(OrderItem.builder().productId(2L).productSku("B").productName("B")
                .quantity(3).unitPrice(new BigDecimal("5.50")).build());

        order.recalculateTotal();

        assertThat(order.getTotalAmount()).isEqualByComparingTo("36.50");
    }

    @Test
    @DisplayName("addItem wires the back-reference to the parent order")
    void addItemSetsBackReference() {
        Order order = pendingOrder();
        OrderItem item = OrderItem.builder().productId(1L).productSku("A").productName("A")
                .quantity(1).unitPrice(BigDecimal.ONE).build();

        order.addItem(item);

        assertThat(item.getOrder()).isSameAs(order);
        assertThat(order.getItems()).containsExactly(item);
    }

    @Test
    @DisplayName("transitionTo moves through the legal path")
    void legalTransition() {
        Order order = pendingOrder();
        order.transitionTo(OrderStatus.PROCESSING);
        assertThat(order.getStatus()).isEqualTo(OrderStatus.PROCESSING);
    }

    @Test
    @DisplayName("transitionTo rejects an illegal jump")
    void illegalTransitionThrows() {
        Order order = pendingOrder();
        assertThatThrownBy(() -> order.transitionTo(OrderStatus.DELIVERED))
                .isInstanceOf(InvalidStatusTransitionException.class);
        assertThat(order.getStatus()).isEqualTo(OrderStatus.PENDING);
    }

    @Test
    @DisplayName("cancel succeeds when PENDING")
    void cancelWhenPending() {
        Order order = pendingOrder();
        order.cancel();
        assertThat(order.getStatus()).isEqualTo(OrderStatus.CANCELLED);
    }

    @Test
    @DisplayName("cancel fails when not PENDING")
    void cancelWhenNotPending() {
        Order order = pendingOrder();
        order.transitionTo(OrderStatus.PROCESSING);
        assertThatThrownBy(order::cancel)
                .isInstanceOf(InvalidStatusTransitionException.class);
        assertThat(order.getStatus()).isEqualTo(OrderStatus.PROCESSING);
    }
}
