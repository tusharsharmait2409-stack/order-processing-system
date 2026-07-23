package com.ecommerce.order.scheduler;

import com.ecommerce.order.service.OrderService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Background job that promotes PENDING orders to PROCESSING every 5 minutes
 * (requirement). The heavy lifting lives in
 * {@link OrderService#promotePendingToProcessing(int)}; this class is just the
 * trigger, so the logic stays testable and reuses the same transaction/metrics.
 *
 * <p>{@code fixedDelay} prevents a slow sweep from overlapping itself, and an
 * {@link AtomicBoolean} guard is a lightweight, no-dependency safety net against
 * re-entry. Cross-instance row safety is guaranteed by the guarded, set-based
 * SQL UPDATE in the service (each row is promoted exactly once).
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class PendingOrderScheduler {

    private final OrderService orderService;

    @Value("${order.processing.batch-size:500}")
    private int batchSize;

    private final AtomicBoolean isRunning = new AtomicBoolean(false);

    @Scheduled(fixedDelayString = "${order.processing.pending-sweep-interval-ms:300000}")
    public void processPendingOrders() {
        if (!isRunning.compareAndSet(false, true)) {
            log.warn("Previous job still running, skipping");
            return;
        }
        try {
            int promoted = orderService.promotePendingToProcessing(batchSize);
            if (promoted > 0) {
                log.info("Background sweep promoted {} order(s) PENDING -> PROCESSING", promoted);
            } else {
                log.debug("Background sweep found no PENDING orders to promote");
            }
        } catch (Exception ex) {
            log.error("Background sweep failed; will retry on next interval", ex);
        } finally {
            isRunning.set(false);
        }
    }
}
