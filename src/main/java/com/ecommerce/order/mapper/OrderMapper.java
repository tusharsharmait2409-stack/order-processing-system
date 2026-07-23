package com.ecommerce.order.mapper;

import com.ecommerce.order.model.dto.CreateOrderRequest;
import com.ecommerce.order.model.dto.OrderItemResponse;
import com.ecommerce.order.model.dto.OrderResponse;
import com.ecommerce.order.model.entity.Order;
import com.ecommerce.order.model.entity.OrderItem;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Translates between API DTOs and domain entities (Mapper pattern).
 *
 * <p>Written by hand so the create path can also run the domain logic — wiring
 * items to their parent and computing the total. The {@code orderNumber} is
 * assigned by the service, not here.
 */
@Component
public class OrderMapper {

    /** Build a new {@link Order} aggregate from a create request. */
    public Order toEntity(CreateOrderRequest request) {
        Order order = Order.builder()
                .customerId(request.customerId())
                .customerEmail(request.customerEmail())
                .customerName(request.customerName())
                .shippingAddress(request.shippingAddress())
                .notes(request.notes())
                .build();

        request.items().forEach(itemReq -> order.addItem(
                OrderItem.builder()
                        .productId(itemReq.productId())
                        .productSku(itemReq.productSku())
                        .productName(itemReq.productName())
                        .quantity(itemReq.quantity())
                        .unitPrice(itemReq.unitPrice())
                        .build()
        ));

        order.recalculateTotal();
        return order;
    }

    /** Map an {@link Order} entity to its API read model. */
    public OrderResponse toResponse(Order order) {
        List<OrderItemResponse> items = order.getItems().stream()
                .map(this::toItemResponse)
                .toList();

        return new OrderResponse(
                order.getId(),
                order.getOrderNumber(),
                order.getCustomerId(),
                order.getCustomerEmail(),
                order.getCustomerName(),
                order.getShippingAddress(),
                order.getNotes(),
                order.getStatus(),
                order.getTotalAmount(),
                items,
                order.getCreatedAt(),
                order.getUpdatedAt()
        );
    }

    private OrderItemResponse toItemResponse(OrderItem item) {
        return new OrderItemResponse(
                item.getId(),
                item.getProductId(),
                item.getProductSku(),
                item.getProductName(),
                item.getQuantity(),
                item.getUnitPrice(),
                item.getLineTotal()
        );
    }
}
