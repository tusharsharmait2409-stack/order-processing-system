package com.ecommerce.order.repository;

import com.ecommerce.order.model.entity.Order;
import com.ecommerce.order.model.enums.OrderStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Spring Data JPA repository for {@link Order} (Repository pattern).
 * Spring generates the implementation at runtime; we only declare intent.
 */
@Repository
public interface OrderRepository extends JpaRepository<Order, Long> {

    Optional<Order> findByOrderNumber(String orderNumber);

    Page<Order> findByStatus(OrderStatus status, Pageable pageable);

    Page<Order> findByCustomerId(Long customerId, Pageable pageable);

    Page<Order> findByStatusAndCustomerId(OrderStatus status, Long customerId, Pageable pageable);

    long countByStatus(OrderStatus status);

    /** Sum of totalAmount across all non-cancelled orders (revenue). */
    @Query("select coalesce(sum(o.totalAmount), 0) from Order o where o.status <> :excluded")
    BigDecimal sumTotalAmountExcludingStatus(@Param("excluded") OrderStatus excluded);

    /** Page of ids in a given status — used by the background job to grab a batch. */
    @Query("select o.id from Order o where o.status = :status order by o.createdAt asc")
    List<Long> findIdsByStatus(@Param("status") OrderStatus status, Pageable pageable);

    /**
     * Bulk status promotion used by the scheduled job — one set-based UPDATE
     * rather than load-mutate-save per row. The WHERE status guard keeps it
     * idempotent and multi-instance safe.
     */
    @Modifying
    @Query("update Order o set o.status = :to, o.updatedAt = :now " +
           "where o.status = :from and o.id in :ids")
    int bulkTransition(@Param("from") OrderStatus from,
                       @Param("to") OrderStatus to,
                       @Param("ids") List<Long> ids,
                       @Param("now") Instant now);
}
