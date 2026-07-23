# Architecture

## Overview

The Order Processing System is a Spring Boot backend organised as a classic layered
architecture. Each layer has one responsibility and depends only on the layer beneath it,
which keeps the domain rules isolated from HTTP and persistence concerns.

```
┌──────────────────────────────────────────────┐
│ controller/   REST endpoints, HTTP mapping     │
├──────────────────────────────────────────────┤
│ service/      use-cases, transactions, caching │
├──────────────────────────────────────────────┤
│ model/entity  aggregate root + state machine   │  ← business rules live here
│ model/enums   OrderStatus (legal transitions)  │
├──────────────────────────────────────────────┤
│ repository/   Spring Data JPA                   │
├──────────────────────────────────────────────┤
│ Database (Hibernate-managed) + Cache            │
└──────────────────────────────────────────────┘
        exception/  mapper/  metrics/  scheduler/  config/  (cross-cutting)
```

## Package responsibilities

| Package | Responsibility |
|---|---|
| `controller` | REST controllers; validate input, delegate to the service, shape HTTP responses. |
| `service` | Application use-cases; own transactions and caching; orchestrate domain + repository. |
| `model.entity` | JPA entities and the rich domain behaviour (`Order.transitionTo`, `cancel`). |
| `model.enums` | `OrderStatus` state machine (legal transitions encoded on the enum). |
| `model.dto` | Immutable request/response records, paged + error + statistics envelopes. |
| `repository` | Spring Data JPA repository (query derivation + a bulk update + aggregates). |
| `mapper` | Translate DTO ↔ entity (Mapper pattern), decoupling API from persistence. |
| `metrics` | Micrometer counters/timer wrapper. |
| `scheduler` | The 5-minute PENDING → PROCESSING background job. |
| `exception` | Domain exceptions + a single `@RestControllerAdvice`. |
| `config` | Cache and OpenAPI configuration. |

## Key design decisions

**State pattern for the order lifecycle.** Legal transitions live on the `OrderStatus`
enum (`PENDING → PROCESSING → SHIPPED → DELIVERED`, and `PENDING → CANCELLED`). The domain
rejects any illegal transition, so no service can accidentally allow one. "Cancel only when
PENDING" falls out of this for free — `CANCELLED` is only reachable from `PENDING`.

**Rich domain model.** `Order` owns its behaviour (`transitionTo`, `cancel`, `recalculateTotal`)
rather than being an anemic data bag mutated by the service.

**Optimistic locking.** A `@Version` column prevents concurrent updates (e.g. the background
job vs a manual status change) from silently overwriting one another.

**Business key + surrogate key.** Orders have a numeric surrogate `id` (fast joins, simple
URLs) and a human-friendly, unique `orderNumber` (`ORD-yyyyMMdd-XXXXXXXX`) for lookups and
customer communication.

**Caching.** Single-order look-ups are `@Cacheable`; any change `@CacheEvict`s the entry, so
the cache never serves stale data. Caffeine backs dev (in-process); Redis backs prod (shared).

**Schema management.** Hibernate `ddl-auto` manages the schema (`create-drop` in dev for a
fresh in-memory database each run, `update` in prod). Indexes are declared on the entities
(`status`, `customer_id`, `order_number`, `created_at`).

**Background job safety.** The sweep uses `fixedDelay` (no self-overlap) plus an in-process
`AtomicBoolean` guard. Cross-instance safety comes from a single guarded, set-based SQL
UPDATE (`WHERE status = PENDING`), which promotes each row exactly once even if several
instances run concurrently.

## Request lifecycle (create order)

```
POST /api/v1/orders
  → OrderController.createOrder            (validate @Valid body)
  → OrderService.createOrder               (open transaction, start latency timer)
  → OrderMapper.toEntity                   (build Order + items, compute total)
  → assign orderNumber
  → OrderRepository.save                   (INSERT order + items)
  → OrderMetrics.incrementCreated
  → OrderMapper.toResponse                 (201 + Location header)
```

## Error handling

`GlobalExceptionHandler` maps exceptions to a consistent `ApiError` body:

| Exception | HTTP status |
|---|---|
| `OrderNotFoundException` | 404 Not Found |
| `InvalidStatusTransitionException` | 409 Conflict |
| `ObjectOptimisticLockingFailureException` | 409 Conflict |
| `MethodArgumentNotValidException` | 400 Bad Request (+ field errors) |
| anything else | 500 Internal Server Error |

## Observability

Micrometer meters exported to Prometheus at `/actuator/prometheus`:
`orders.created`, `orders.cancelled`, `orders.status.transition{from,to}`,
`orders.pending.swept`, and the `orders.create.latency` timer (p50/p95/p99).
