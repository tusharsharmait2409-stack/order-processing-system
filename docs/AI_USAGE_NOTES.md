# AI Usage Documentation

This document details how AI tools (Claude) were used in building the Order Processing System,
including the prompt given, issues encountered, and corrections made.

## Overview

The Order Processing System was built with extensive AI assistance using Claude. The AI was
given a comprehensive requirement specification and iteratively built out all components. Every
piece of generated code was reviewed, compiled, and tested before being accepted.

## Initial Prompt

The project started with a detailed requirement specification:

```
Building an Order Processing System

Objective: Build the backend for an E-commerce Order Processing System.

Requirements:
1. Create an order with multiple items
2. Retrieve order details by order ID
3. Update order status (PENDING, PROCESSING, SHIPPED, DELIVERED)
4. Background job to auto-update PENDING → PROCESSING every 5 minutes
5. List all orders with optional status filter
6. Cancel order (only if PENDING)

Additional Requirements:
- Use design patterns and principles
- Proper database and cache schema design
- OpenAPI v3 spec documentation
- Comprehensive test cases
- Metrics, reports, coding standards
```

## Development Process

### 1. Project Structure Creation

**AI Action**: Created a complete Maven project following standard Spring Boot conventions.

**Directories Created**:
- `src/main/java/com/ecommerce/order/` - main source code
- `src/test/java/com/ecommerce/order/` - test code
- `docs/` - documentation

**What Worked**: The standard structure was generated correctly on the first attempt.

### 2. Entity Design

**AI Action**: Created JPA entities with proper annotations.

**Issue Found**: Common fields (`id`, `version`, timestamps) were moved into a `BaseEntity`
superclass. The entity used Lombok's plain `@Builder`, which only sees fields on the same
class, so `Order.builder()` could not set the inherited `id`.

**Correction Made**: Switched to `@SuperBuilder` (inheritance-aware) on both parent and child.

```java
// Before (broken)
@Builder
public class Order extends BaseEntity { ... }

// After (fixed)
@SuperBuilder
public class Order extends BaseEntity { ... }

// BaseEntity also needs it
@SuperBuilder
@NoArgsConstructor
public abstract class BaseEntity { ... }
```

### 3. Service Layer Implementation

**AI Action**: Created `OrderServiceImpl` with the business logic.

**Issue Found**: The service referenced an `OrderMetrics` class before it was created, so the
module did not compile ("cannot find symbol").

**Correction Made**: Created `OrderMetrics` first (a thin Micrometer wrapper), then injected it.

```java
@Component
public class OrderMetrics {
    private final Counter ordersCreated;
    // counters + a creation-latency timer
}
```

### 4. Scheduler Implementation

**AI Action**: Created the background job scheduler.

**Issue Found**: Used the `@SchedulerLock` annotation from the ShedLock library, which wasn't in
`pom.xml`, so it didn't compile.

**Correction Made**: Rather than add an external dependency for a guarantee this project didn't
strictly need, removed ShedLock and implemented a simpler in-process concurrency guard with
`AtomicBoolean`. Cross-instance safety is already handled by a guarded set-based SQL UPDATE.

```java
// Before (needs an external library)
@SchedulerLock(name = "processPendingOrders", lockAtMostFor = "4m")

// After (self-contained)
private final AtomicBoolean isRunning = new AtomicBoolean(false);

public void processPendingOrders() {
    if (!isRunning.compareAndSet(false, true)) {
        log.warn("Previous job still running, skipping");
        return;
    }
    try {
        // ... processing logic
    } finally {
        isRunning.set(false);
    }
}
```

### 5. Controller Implementation

**AI Action**: Created the REST controller with OpenAPI annotations.

**Issue Found**: An unused import (`HttpStatus`) was left behind after an edit and flagged by
the IDE.

**Correction Made**: Reviewed all classes and removed dead imports. (In the final code
`HttpStatus` is actually used — in `@ResponseStatus(HttpStatus.CREATED)` and the exception
handler — so no unused import remains.)

### 6. Test Implementation

**AI Action**: Created unit, controller, and integration tests.

**Issue Found**: Test code that built entities via the builder couldn't set inherited fields.

**Correction Made**: Already fixed by the `@SuperBuilder` change in the entities — the tests
compiled and passed once the entity fix was in place.

### 7. Works on the CLI but Fails in the IDE (JDK mismatch)

**Issue Found**: `mvn test` passed on the command line, but running the app in IntelliJ failed
to compile with `ExceptionInInitializerError ... TypeTag :: UNKNOWN`.

**Correction Made**: Installing Maven had pulled in JDK 26, and IntelliJ auto-selected it as
the Project SDK, while the terminal used JDK 17. Lombok 1.18.30 doesn't support JDK 26. Set the
IntelliJ Project SDK and language level to **17**. Lesson: keep the IDE's SDK aligned with
`JAVA_HOME`.

### 8. Noisy Redis Health Check in Dev

**Issue Found**: A `Connection refused: localhost:6379` stack trace appeared in dev logs.

**Correction Made**: `spring-boot-starter-data-redis` is on the classpath (Redis is the prod
cache), so Actuator's health check tried to ping Redis. Dev uses Caffeine, so disabled the
Redis health indicator in the dev profile.

## Design Decisions Made

### 1. State Pattern for Order Status

Status transitions are encoded on the enum as a state machine.

```java
public enum OrderStatus {
    PENDING, PROCESSING, SHIPPED, DELIVERED, CANCELLED;

    public boolean canTransitionTo(OrderStatus target) {
        return allowedTransitions().contains(target);
    }

    public Set<OrderStatus> allowedTransitions() {
        return switch (this) {
            case PENDING    -> Set.of(PROCESSING, CANCELLED);
            case PROCESSING -> Set.of(SHIPPED);
            case SHIPPED    -> Set.of(DELIVERED);
            case DELIVERED, CANCELLED -> Set.of();
        };
    }
}
```

**Rationale**: Keeps the transition rules in one place and makes illegal transitions impossible.
"Cancel only if PENDING" falls out for free, since CANCELLED is only reachable from PENDING.

### 2. Optimistic Locking

A `@Version` field guards against concurrent modification.

```java
@Version
private Long version;
```

**Rationale**: The background job and a manual status update might touch the same order at once.
Optimistic locking detects the conflict instead of silently overwriting. It was chosen over
pessimistic locking because conflicts are rare and it avoids holding database locks.

### 3. Batch Updates for the Scheduler

The scheduler promotes pending orders with one set-based query, not row-by-row.

```java
@Modifying
@Query("update Order o set o.status = :to, o.updatedAt = :now " +
       "where o.status = :from and o.id in :ids")
int bulkTransition(OrderStatus from, OrderStatus to, List<Long> ids, Instant now);
```

**Rationale**: Loading hundreds of orders to update them one by one is inefficient. The
`where o.status = :from` guard also makes the update idempotent and safe across instances.

### 4. Hand-written Mapper (instead of MapStruct)

DTO ↔ entity conversion is done by a small hand-written `OrderMapper` component.

**Rationale**: The create path also runs domain logic (wiring items to the parent, computing the
total), which is clearer in explicit code than in generated mappings. It also avoids an extra
annotation processor. MapStruct would be a fine choice if mappings grew large.

## API Design Decisions

### 1. Dual Access Pattern

Orders can be fetched by numeric ID or by order number.

```
GET /api/v1/orders/{id}
GET /api/v1/orders/number/{orderNumber}
```

**Rationale**: Internal systems use the numeric ID (fast joins); customers reference the
human-friendly order number (`ORD-yyyyMMdd-XXXXXXXX`) shown on their receipt.

### 2. POST for Cancel Instead of DELETE

```
POST /api/v1/orders/{id}/cancel
```

**Rationale**: Cancellation is a state change, not a deletion. The record remains for audit.

### 3. PATCH for Status Update

```
PATCH /api/v1/orders/{id}/status
```

**Rationale**: PATCH indicates a partial update — only the status changes, not the whole order.

## Code Quality Measures

### 1. Validation
All request DTOs use Bean Validation annotations.

```java
@NotNull(message = "customerId is required")
private Long customerId;

@Email(message = "customerEmail must be a valid email")
private String customerEmail;
```

### 2. Exception Handling
Centralised handling via `@RestControllerAdvice`:
- custom exceptions for business rules
- one consistent `ApiError` response shape (timestamp, status, error, message, path, fieldErrors)

### 3. Logging
- **INFO**: business operations (order created, status changed, cancelled)
- **DEBUG**: quiet details (e.g. sweep found nothing)
- **WARN**: non-fatal issues (job skipped because previous run still in flight)
- **ERROR**: unexpected failures

### 4. Metrics
Prometheus-compatible metrics via Micrometer:
- `orders.created`, `orders.cancelled` (counters)
- `orders.status.transition` (counter, tagged with from/to)
- `orders.pending.swept` (counter)
- `orders.create.latency` (timer, p50/p95/p99)

## Testing Strategy

AI generated tests at multiple levels:

**Unit Tests** — domain (state machine, entity behaviour) and service (Mockito, mocked
collaborators).

**Controller Tests** — `@WebMvcTest` + MockMvc for endpoints, validation, and error mapping.

**Integration Tests** — full Spring context + MockMvc over H2, end-to-end flow including the
error translations.

**Repository Tests** — Testcontainers against real PostgreSQL, skipped gracefully when Docker
isn't available.

## What AI Did Well

1. **Comprehensive code generation** — a complete, working application from the requirements.
2. **Design patterns** — correctly applied Repository, Service Layer, DTO, Builder, State, and
   Template Method patterns.
3. **Spring Boot best practices** — proper annotations, profiles, and conventions.
4. **Documentation** — generated the OpenAPI spec, architecture docs, and README.
5. **Testing** — meaningful tests covering happy paths and edge cases.

## What Required Human Correction

1. **Missing dependencies** — generated code using a library not in `pom.xml` (ShedLock).
2. **Lombok inheritance** — the initial builder didn't work with a superclass (`@SuperBuilder`).
3. **Referencing code before creating it** — `OrderMetrics` used before it existed.
4. **Toolchain mismatch** — IDE used JDK 26 (unsupported by Lombok) while the CLI used JDK 17.
5. **Environment config** — a noisy Redis health check in dev that needed disabling.
6. **Minor cleanup** — unused imports.

## Recommendations for AI-Assisted Development

1. **Iterate** — build and verify incrementally rather than generating everything at once.
2. **Use IDE feedback** — catch compilation errors and unused imports early.
3. **Check dependencies** — make sure every import has a matching entry in the build file.
4. **Test frequently** — run the suite often to catch regressions.
5. **Review generated code** — for correctness, security, performance, and maintainability;
   and be willing to *remove* complexity (like ShedLock) when a simpler solution fits.
