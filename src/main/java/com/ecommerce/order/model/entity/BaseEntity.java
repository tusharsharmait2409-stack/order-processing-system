package com.ecommerce.order.model.entity;

import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.SuperBuilder;

import java.time.Instant;

/**
 * Shared base for persistent entities: surrogate id, optimistic-lock version,
 * and audit timestamps. Declared {@code @MappedSuperclass} so its columns fold
 * into each subclass table.
 *
 * <p><b>Builder note:</b> uses {@link SuperBuilder} (not plain {@code @Builder})
 * so subclass builders can populate inherited fields such as {@code id}.
 */
@MappedSuperclass
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
public abstract class BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Optimistic-lock guard against concurrent updates. */
    @Version
    private Long version;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    void onCreate() {
        Instant now = Instant.now();
        this.createdAt = now;
        this.updatedAt = now;
        onPrePersist();
    }

    @PreUpdate
    void onUpdate() {
        this.updatedAt = Instant.now();
    }

    /** Hook for subclasses to add pre-persist logic. */
    protected void onPrePersist() {
        // no-op by default
    }
}
