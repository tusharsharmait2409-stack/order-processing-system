package com.ecommerce.order.model.entity;

import com.ecommerce.order.exception.InvalidStatusTransitionException;
import com.ecommerce.order.model.enums.OrderStatus;
import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.SuperBuilder;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * Aggregate root for an order and its line items (rich domain model).
 *
 * <p>Business rules about the lifecycle live here:
 * <ul>
 *   <li>{@link #transitionTo(OrderStatus)} enforces the legal state machine.</li>
 *   <li>{@link #cancel()} enforces "cancel only when PENDING".</li>
 * </ul>
 */
@Entity
@Table(name = "orders", indexes = {
        @Index(name = "idx_orders_status", columnList = "status"),
        @Index(name = "idx_orders_customer_id", columnList = "customer_id"),
        @Index(name = "idx_orders_order_number", columnList = "order_number", unique = true),
        @Index(name = "idx_orders_created_at", columnList = "created_at")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
public class Order extends BaseEntity {

    /** Human-friendly, unique business key (e.g. ORD-20260723-A1B2C3D4). */
    @Column(name = "order_number", nullable = false, unique = true, length = 40, updatable = false)
    private String orderNumber;

    @Column(name = "customer_id", nullable = false)
    private Long customerId;

    @Column(name = "customer_email")
    private String customerEmail;

    @Column(name = "customer_name")
    private String customerName;

    @Column(name = "shipping_address", length = 512)
    private String shippingAddress;

    @Column(length = 512)
    private String notes;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private OrderStatus status;

    @Column(name = "total_amount", nullable = false, precision = 14, scale = 2)
    private BigDecimal totalAmount;

    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @Builder.Default
    private List<OrderItem> items = new ArrayList<>();

    @Override
    protected void onPrePersist() {
        if (this.status == null) {
            this.status = OrderStatus.PENDING;
        }
    }

    // ---- Domain behaviour ----

    public void addItem(OrderItem item) {
        item.setOrder(this);
        this.items.add(item);
    }

    public void recalculateTotal() {
        this.totalAmount = items.stream()
                .map(OrderItem::getLineTotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    /**
     * Move the order to {@code target}, enforcing the legal state machine.
     *
     * @throws InvalidStatusTransitionException if the transition is not allowed
     */
    public void transitionTo(OrderStatus target) {
        if (!status.canTransitionTo(target)) {
            throw new InvalidStatusTransitionException(getId(), this.status, target);
        }
        this.status = target;
    }

    /**
     * Cancel the order. Allowed only while PENDING.
     *
     * @throws InvalidStatusTransitionException if not currently PENDING
     */
    public void cancel() {
        transitionTo(OrderStatus.CANCELLED);
    }
}
