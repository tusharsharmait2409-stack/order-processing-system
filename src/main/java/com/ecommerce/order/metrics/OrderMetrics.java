package com.ecommerce.order.metrics;

import com.ecommerce.order.model.enums.OrderStatus;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Component;

import java.util.function.Supplier;

/**
 * Application metrics for order operations (Micrometer).
 *
 * <p>Wraps the {@link MeterRegistry} so meter names and tags live in one place.
 * Exported to Prometheus via {@code /actuator/prometheus}.
 */
@Component
public class OrderMetrics {

    private final MeterRegistry registry;
    private final Counter ordersCreated;
    private final Counter ordersCancelled;
    private final Timer orderCreationTimer;

    public OrderMetrics(MeterRegistry registry) {
        this.registry = registry;
        this.ordersCreated = Counter.builder("orders.created")
                .description("Total number of orders created")
                .register(registry);
        this.ordersCancelled = Counter.builder("orders.cancelled")
                .description("Total number of orders cancelled")
                .register(registry);
        this.orderCreationTimer = Timer.builder("orders.create.latency")
                .description("Time taken to create and persist an order")
                .publishPercentiles(0.5, 0.95, 0.99)
                .register(registry);
    }

    public void incrementCreated() {
        ordersCreated.increment();
    }

    public void incrementCancelled() {
        ordersCancelled.increment();
    }

    public void recordTransition(OrderStatus from, OrderStatus to) {
        registry.counter("orders.status.transition", "from", from.name(), "to", to.name()).increment();
    }

    public void recordPendingSwept(int count) {
        registry.counter("orders.pending.swept").increment(count);
    }

    /** Time the supplied create operation and record its latency. */
    public <T> T recordCreationLatency(Supplier<T> operation) {
        return orderCreationTimer.record(operation);
    }
}
