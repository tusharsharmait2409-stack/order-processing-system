# Order Processing System - Architecture Documentation

## Table of Contents
1. [Overview](#overview)
2. [System Architecture](#system-architecture)
3. [Design Patterns](#design-patterns)
4. [Database Design](#database-design)
5. [Cache Strategy](#cache-strategy)
6. [API Design](#api-design)
7. [Error Handling](#error-handling)
8. [Metrics and Monitoring](#metrics-and-monitoring)
9. [Background Jobs](#background-jobs)
10. [Testing Strategy](#testing-strategy)
11. [Configuration Profiles](#configuration-profiles)
12. [Security Considerations](#security-considerations)
13. [Future Enhancements](#future-enhancements)

---

## Overview

The Order Processing System is a backend service built with Spring Boot that handles
e-commerce order management. It exposes RESTful APIs for creating, tracking, and managing
orders throughout their lifecycle.

### Key Features
- Order creation with multiple items (server-computed totals)
- Order status tracking with a strict state machine
- Order cancellation (only while PENDING)
- Paginated order listing with status and customer filters
- Automated background processing of pending orders (every 5 minutes)
- Metrics and monitoring via Micrometer/Prometheus
- Caching for fast order look-ups

### Tech Stack
- **Framework**: Spring Boot 3.2.0
- **Language**: Java 17
- **Database**: PostgreSQL (production) / H2 (development)
- **Cache**: Caffeine (local) / Redis (production)
- **Documentation**: OpenAPI 3.0 (Springdoc)
- **Metrics**: Micrometer + Prometheus
- **Testing**: JUnit 5, Mockito, Testcontainers
- **Coverage**: JaCoCo

---

## System Architecture

```
┌─────────────────────────────────────────────────────────────────┐
│                        Client Applications                       │
│                   (Swagger UI, Postman, curl)                    │
└─────────────────────────────────────────────────────────────────┘
                                    │  HTTP / JSON
                                    ▼
┌─────────────────────────────────────────────────────────────────┐
│                    Order Processing Service                      │
│  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐          │
│  │  Controller  │→ │   Service    │→ │  Repository  │          │
│  │    Layer     │  │    Layer     │  │  (Spring     │          │
│  │              │  │ (@Transact.) │  │   Data JPA)  │          │
│  └──────────────┘  └──────────────┘  └──────────────┘          │
│         │                 │                  │                   │
│         ▼                 ▼                  ▼                   │
│  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐          │
│  │    Mapper    │  │    Cache     │  │   Database   │          │
│  │ (hand-written)│ │(Caffeine/    │  │ (H2 / Postgres)│        │
│  │              │  │  Redis)      │  │              │          │
│  └──────────────┘  └──────────────┘  └──────────────┘          │
│                                                                  │
│  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐          │
│  │  Scheduler   │  │   Metrics    │  │  Exception   │          │
│  │ (Background) │  │ (Micrometer) │  │   Handler    │          │
│  └──────────────┘  └──────────────┘  └──────────────┘          │
└─────────────────────────────────────────────────────────────────┘
                                    │
                                    ▼
┌─────────────────────────────────────────────────────────────────┐
│                    Monitoring & Observability                    │
│              (Actuator + Prometheus scrape endpoint)             │
└─────────────────────────────────────────────────────────────────┘
```

### Layer Responsibilities

| Layer | Responsibility |
|-------|----------------|
| **Controller** | HTTP request handling, validation, response formatting |
| **Service** | Business logic orchestration, transaction management, caching |
| **Repository** | Data access, query derivation, bulk updates, aggregates |
| **Mapper** | DTO ↔ Entity conversion (hand-written) |
| **Scheduler** | Background job execution |
| **Metrics** | Application metrics collection |
| **Exception Handler** | Central translation of exceptions to HTTP responses |

---

## Design Patterns

### 1. Repository Pattern
Abstracts data access, separating business logic from persistence.

```java
@Repository
public interface OrderRepository extends JpaRepository<Order, Long> {
    Optional<Order> findByOrderNumber(String orderNumber);
    Page<Order> findByStatus(OrderStatus status, Pageable pageable);
    Page<Order> findByCustomerId(Long customerId, Pageable pageable);
}
```

### 2. Service Layer Pattern
Encapsulates use-cases and transaction boundaries behind an interface (Dependency Inversion).

```java
@Service
@RequiredArgsConstructor
public class OrderServiceImpl implements OrderService {
    // @Transactional use-cases; reads are readOnly
}
```

### 3. DTO Pattern (Data Transfer Object)
Separates the internal domain model from the API contract. DTOs are immutable records.

```
CreateOrderRequest → Order (Entity) → OrderResponse
```

### 4. Builder Pattern
Object creation via Lombok. `BaseEntity`/`Order` use `@SuperBuilder` so the builder can
populate inherited fields.

```java
Order.builder()
    .customerId(1001L)
    .customerEmail("jane@example.com")
    .status(OrderStatus.PENDING)
    .build();
```

### 5. State Pattern
Order status transitions are controlled by a state machine encoded on the enum.

```java
public boolean canTransitionTo(OrderStatus target) {
    return allowedTransitions().contains(target);
}

public Set<OrderStatus> allowedTransitions() {
    return switch (this) {
        case PENDING     -> Set.of(PROCESSING, CANCELLED);
        case PROCESSING  -> Set.of(SHIPPED);
        case SHIPPED     -> Set.of(DELIVERED);
        case DELIVERED, CANCELLED -> Set.of();  // terminal
    };
}
```

### 6. Strategy Pattern (via Cache Configuration)
The cache implementation is selected by profile (Caffeine local, Redis prod) behind Spring's
`CacheManager` abstraction — the same `@Cacheable`/`@CacheEvict` code works for both.

### 7. Template Method Pattern
`BaseEntity` centralises the id, optimistic-lock version, and audit timestamps for entities.

```java
@MappedSuperclass
@SuperBuilder
public abstract class BaseEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Version
    private Long version;

    @Column(name = "created_at", updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at")
    private Instant updatedAt;

    @PrePersist void onCreate() { /* set timestamps + hook */ }
}
```

---

## Database Design

### Entity Relationship Diagram

```
┌─────────────────────┐       ┌─────────────────────┐
│       orders        │       │    order_items      │
├─────────────────────┤       ├─────────────────────┤
│ id (PK)             │───┐   │ id (PK)             │
│ order_number (UK)   │   │   │ order_id (FK)       │──┐
│ customer_id (IDX)   │   └──→│ product_id          │  │
│ customer_email      │       │ product_sku         │  │
│ customer_name       │       │ product_name        │  │
│ shipping_address    │       │ quantity            │  │
│ notes               │       │ unit_price          │  │
│ status (IDX)        │       └─────────────────────┘  │
│ total_amount        │                                │
│ created_at (IDX)    │                                │
│ updated_at          │                                │
│ version             │                                │
└─────────────────────┘                                │
         │                                              │
         └──────────────────────────────────────────────┘
                     One-to-Many Relationship
```

> Note: an order item's `lineTotal` (unit_price × quantity) is **derived at runtime**, not
> stored, so it can never drift from its inputs. Similarly the order `total_amount` is
> computed server-side from the items.

### Indexes

| Table | Index Name | Columns | Purpose |
|-------|------------|---------|---------|
| orders | idx_orders_status | status | Filter by status |
| orders | idx_orders_customer_id | customer_id | Filter by customer |
| orders | idx_orders_order_number | order_number (unique) | Lookup by order number |
| orders | idx_orders_created_at | created_at | Sort/filter by date |
| order_items | idx_order_items_order_id | order_id | Join with orders |

### Optimistic Locking
Entities extend `BaseEntity`, which carries a `@Version` column. Concurrent updates (e.g. the
background job vs a manual status change) are detected instead of silently overwriting.

### Batch Operations
The scheduler promotes pending orders with a single set-based update rather than row-by-row:

```java
@Modifying
@Query("update Order o set o.status = :to, o.updatedAt = :now " +
       "where o.status = :from and o.id in :ids")
int bulkTransition(OrderStatus from, OrderStatus to, List<Long> ids, Instant now);
```

The `where o.status = :from` guard makes the update idempotent and safe across instances.

---

## Cache Strategy

### Cache Configuration

```yaml
Cache Name: orders
Provider:   Caffeine (dev, in-process) / Redis (prod, shared)
Dev spec:   maximumSize=1000, expireAfterWrite=5m
Prod TTL:   5 minutes
```

### Cache Operations

| Operation | Cache Behavior |
|-----------|----------------|
| Get Order by id | `@Cacheable("orders")` — cache hit returns cached value |
| Update Status | `@CacheEvict` — invalidates that order |
| Cancel Order | `@CacheEvict` — invalidates that order |
| Background sweep | `@CacheEvict(allEntries = true)` — bulk update bypasses per-entity eviction |
| Create Order | No caching (new entity) |

### Cache Key Strategy
- By ID: `orders::{id}`

---

## API Design

### RESTful Endpoints

| Method | Endpoint | Description |
|--------|----------|-------------|
| POST | `/api/v1/orders` | Create order |
| GET | `/api/v1/orders/{id}` | Get by ID |
| GET | `/api/v1/orders/number/{orderNumber}` | Get by order number |
| GET | `/api/v1/orders` | List all (paginated, filtered) |
| PATCH | `/api/v1/orders/{id}/status` | Update status |
| POST | `/api/v1/orders/{id}/cancel` | Cancel order |
| GET | `/api/v1/orders/statistics` | Get statistics |

### Response Codes

| Code | Description |
|------|-------------|
| 200 | Success |
| 201 | Created |
| 400 | Bad Request (validation errors) |
| 404 | Not Found |
| 409 | Conflict (illegal state transition / cancel when not PENDING) |
| 500 | Internal Server Error |

### Pagination Envelope
```json
{
  "content": [ /* ... */ ],
  "page": 0,
  "size": 20,
  "totalElements": 100,
  "totalPages": 5,
  "last": false
}
```

---

## Error Handling

### Global Exception Handler
Centralised handling via `@RestControllerAdvice`, so controllers stay free of try/catch and
every error returns the same shape.

### Error Response Format
```json
{
  "timestamp": "2026-07-23T13:28:08.315672Z",
  "status": 400,
  "error": "Bad Request",
  "message": "Validation failed",
  "path": "/api/v1/orders",
  "fieldErrors": [
    { "field": "items[0].quantity", "message": "quantity must be greater than 0" }
  ]
}
```

### Exception → Status Mapping
| Exception | HTTP status |
|-----------|-------------|
| `OrderNotFoundException` | 404 Not Found |
| `InvalidStatusTransitionException` | 409 Conflict |
| `ObjectOptimisticLockingFailureException` | 409 Conflict |
| `MethodArgumentNotValidException` | 400 Bad Request (with field errors) |
| any other `Exception` | 500 Internal Server Error |

---

## Metrics and Monitoring

### Prometheus Metrics

| Metric | Type | Description |
|--------|------|-------------|
| `orders.created` | Counter | Total orders created |
| `orders.cancelled` | Counter | Total orders cancelled |
| `orders.status.transition` | Counter | Status transitions (tagged `from`/`to`) |
| `orders.pending.swept` | Counter | Orders promoted by the background job |
| `orders.create.latency` | Timer | Order creation latency (p50/p95/p99) |

### Actuator Endpoints

| Endpoint | Purpose |
|----------|---------|
| `/actuator/health` | Health check |
| `/actuator/metrics` | Application metrics |
| `/actuator/prometheus` | Prometheus-format metrics |
| `/actuator/info` | Build/info |

---

## Background Jobs

### Order Processing Scheduler

**Purpose**: Automatically transition PENDING orders to PROCESSING every 5 minutes.

**Configuration**:
- `fixedDelay`: 5 minutes (300,000 ms) — waits for the previous run to finish (no self-overlap)
- Batch size: 500 orders per sweep (bounded work)
- Concurrent execution: prevented via an in-process `AtomicBoolean` guard

**Flow**:
```
1. Scheduler triggers (fixedDelay 5 min); AtomicBoolean guard prevents re-entry.
2. Fetch a bounded batch of the oldest PENDING order ids.
3. Bulk UPDATE: SET status = PROCESSING WHERE status = PENDING AND id IN (batch).
4. Record metrics (orders.pending.swept), evict cache, log results.
```

**Multi-instance safety**: the guarded set-based UPDATE promotes each row exactly once even if
several instances run concurrently. For strict single-node execution, a distributed lock
(ShedLock) could be added.

---

## Testing Strategy

### Test Pyramid

```
        ┌───────────┐
        │Integration│  ← @SpringBootTest + MockMvc, Testcontainers
        ├───────────┤
        │ Web slice │  ← @WebMvcTest (controller + validation)
        ├───────────┤
        │   Unit    │  ← Domain + Service (Mockito)
        └───────────┘
```

### Test Categories

| Category | Framework | Coverage |
|----------|-----------|----------|
| Unit — domain | JUnit 5 | State machine, entity behaviour, totals |
| Unit — service | JUnit 5 + Mockito | Use-case logic with mocked collaborators |
| Controller | `@WebMvcTest` + MockMvc | API endpoints, validation, error mapping |
| Integration | `@SpringBootTest` + MockMvc (H2) | Full request → DB flow |
| Repository | Testcontainers (PostgreSQL) | Real-DB persistence (skipped without Docker) |

### Running Tests
```bash
mvn test                    # all tests
mvn test jacoco:report      # with coverage report
```

---

## Configuration Profiles

| Profile | Database | Cache | ddl-auto | Use Case |
|---------|----------|-------|----------|----------|
| dev (default) | H2 (in-memory) | Caffeine | create-drop | Development |
| prod | PostgreSQL | Redis | update | Production |

Key tunables:

| Property | Default | Description |
|----------|---------|-------------|
| `order.processing.pending-sweep-interval-ms` | 300000 | Background job interval (5 min) |
| `order.processing.batch-size` | 500 | Max orders promoted per sweep |

---

## Security Considerations

1. **Input Validation**: all request bodies validated via Bean Validation (`@Valid`).
2. **SQL Injection**: prevented by JPA/Hibernate parameterised queries.
3. **No secrets in logs**: credentials come from environment variables (prod profile).
4. **Optimistic Locking**: `@Version` prevents lost updates under concurrency.
5. **Safe identifiers**: a separate human-friendly `orderNumber` is used for public lookups.

---

## Future Enhancements

1. **Versioned migrations**: adopt Flyway for reviewable, incremental schema changes in prod
   (instead of Hibernate `ddl-auto: update`).
2. **Distributed locking**: ShedLock for strict single-node scheduling in a cluster.
3. **Event-driven**: publish order events to a message queue (Kafka/RabbitMQ).
4. **Idempotency keys**: dedupe create-order requests on client retry.
5. **Keyset pagination**: seek-based paging for very large datasets.
6. **Audit trail**: record who changed a status and when.
7. **AuthN/AuthZ**: secure endpoints (JWT/OAuth2) and add rate limiting.
