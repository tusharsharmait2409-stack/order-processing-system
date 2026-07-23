# Order Processing System

A backend service for an e-commerce **Order Processing System**, built with Spring Boot 3 and
Java 17. It exposes a RESTful API to create orders, track them through their lifecycle, list
and filter them, and cancel them — with a background job that automatically advances pending
orders, caching, metrics, and a full test suite.

## Features

1. **Create an order** with multiple line items — total computed server-side.
2. **Retrieve an order** by numeric id *or* by human-friendly order number.
3. **Update order status** through a strict lifecycle
   `PENDING → PROCESSING → SHIPPED → DELIVERED`; illegal transitions are rejected.
4. **Background job** advancing `PENDING` orders to `PROCESSING` **every 5 minutes**.
5. **List orders** with optional status and customer filters, sorting, and pagination.
6. **Cancel an order**, allowed **only while it is still `PENDING`**.
7. **Order statistics** — totals, a breakdown by status, and total revenue.

Plus the additional requirements: design patterns, a proper DB + cache schema, OpenAPI v3
documentation, comprehensive tests, and metrics/monitoring.

## Tech stack

| Concern | Choice |
|---|---|
| Framework | Spring Boot 3.2 |
| Language | Java 17 |
| Persistence | Spring Data JPA / Hibernate |
| Database | H2 (dev/test) · PostgreSQL (prod) |
| Cache | Caffeine (dev) · Redis (prod) |
| API docs | springdoc-openapi (OpenAPI 3) |
| Metrics | Micrometer + Prometheus |
| Testing | JUnit 5, Mockito, Spring Boot Test (MockMvc), Testcontainers |
| Coverage | JaCoCo |
| Build | Maven |
| Boilerplate | Lombok |

## Getting started

### Prerequisites

- JDK 17
- Maven
- Docker (optional — only for the Postgres integration test or the prod stack)

### Run locally (dev, zero setup)

Runs on in-memory **H2** + **Caffeine** — no database or Redis required.

```bash
mvn spring-boot:run
```

- API: http://localhost:8080
- Swagger UI: http://localhost:8080/swagger-ui.html
- OpenAPI JSON: http://localhost:8080/v3/api-docs
- H2 console: http://localhost:8080/h2-console (JDBC `jdbc:h2:mem:orders`, user `sa`)
- Health / Prometheus: http://localhost:8080/actuator/health · /actuator/prometheus

### Run with PostgreSQL + Redis (prod)

```bash
docker compose up -d      # PostgreSQL + Redis (see docker-compose.yml / .env.example)
mvn spring-boot:run -Dspring-boot.run.profiles=prod
```

### Tests + coverage

```bash
mvn test                  # runs unit + web-slice + integration tests
mvn test jacoco:report    # coverage at target/site/jacoco/index.html
```

## API reference

Base path: `/api/v1/orders`

| Method | Path | Description |
|---|---|---|
| `POST` | `/api/v1/orders` | Create a new order (201) |
| `GET` | `/api/v1/orders/{id}` | Get order by id |
| `GET` | `/api/v1/orders/number/{orderNumber}` | Get order by order number |
| `GET` | `/api/v1/orders` | List orders (`status`, `customerId`, `page`, `size`, `sortBy`, `sortDir`) |
| `PATCH` | `/api/v1/orders/{id}/status` | Update status (body: `status`, optional `reason`) |
| `POST` | `/api/v1/orders/{id}/cancel` | Cancel (only if PENDING) |
| `GET` | `/api/v1/orders/statistics` | Totals, breakdown by status, revenue |

### Example — create an order

```bash
curl -X POST http://localhost:8080/api/v1/orders \
  -H 'Content-Type: application/json' \
  -d '{
    "customerId": 1001,
    "customerEmail": "jane@example.com",
    "customerName": "Jane Doe",
    "shippingAddress": "123 Main St",
    "items": [
      { "productId": 101, "productSku": "SKU-1", "productName": "Widget", "quantity": 2, "unitPrice": 19.99 }
    ]
  }'
```

Returns `201` with the order (`status: "PENDING"`, `orderNumber: "ORD-…"`, `totalAmount: 39.98`).

### Error shape

Every failure returns a consistent envelope:

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

## Order status flow

```
PENDING ──▶ PROCESSING ──▶ SHIPPED ──▶ DELIVERED
   │
   └──▶ CANCELLED        (only from PENDING)
```

## Design patterns and principles

- **State pattern** — legal transitions encoded on the `OrderStatus` enum.
- **Rich domain model** — behaviour (`transitionTo`, `cancel`) lives on `Order`.
- **Repository pattern** — Spring Data JPA.
- **DTO + Mapper** — API records decoupled from JPA entities.
- **Dependency inversion** — controllers/scheduler depend on the `OrderService` interface.
- **Chain of responsibility** — one `@RestControllerAdvice` for all error translation.
- **Template method** — `BaseEntity` centralises id, `@Version`, and audit timestamps
  (`@SuperBuilder` keeps the builder inheritance-aware).
- **Optimistic locking** — `@Version` guards concurrent updates.

See [`docs/ARCHITECTURE.md`](docs/ARCHITECTURE.md) for the full architecture.

## Project structure

```
src/main/java/com/ecommerce/order/
├── config/         OpenAPI + cache configuration
├── controller/     REST controllers
├── exception/      custom exceptions + global handler
├── mapper/         DTO <-> entity mapping
├── metrics/        Micrometer meters
├── model/
│   ├── dto/        request/response records
│   ├── entity/     JPA entities + BaseEntity
│   └── enums/      OrderStatus (state machine)
├── repository/     Spring Data JPA
├── scheduler/      background job
├── service/        business logic
└── OrderProcessingApplication.java
src/test/java/com/ecommerce/order/
├── controller/     web-slice tests
├── integration/    full @SpringBootTest + MockMvc
├── model/          domain/enum unit tests
├── repository/     Testcontainers Postgres test
└── service/        service unit tests
docs/               ARCHITECTURE.md, openapi.yaml, AI_USAGE_NOTES.md
postman/            Postman collection
```

## Configuration

| Property | Default | Meaning |
|---|---|---|
| `order.processing.pending-sweep-interval-ms` | `300000` (5 min) | Background sweep interval |
| `order.processing.batch-size` | `500` | Max orders promoted per sweep |
| `spring.jpa.hibernate.ddl-auto` | `update` (prod) / `create-drop` (dev) | Schema management |
| `spring.profiles.active` | `dev` | Active profile |

## Metrics

Exposed via Actuator, scrapeable at `/actuator/prometheus`:
`orders.created`, `orders.cancelled`, `orders.status.transition{from,to}`,
`orders.pending.swept`, and the `orders.create.latency` timer (p50/p95/p99).

## Testing with Postman

Import `postman/OrderProcessingSystem.postman_collection.json`. It covers all endpoints
(plus an invalid-order example that triggers a 400). Set `baseUrl`, and after creating an
order copy its id into the `orderId` variable.

## Use of AI during development

AI assistance was used throughout (scaffolding, drafting entities/DTOs/tests, config).
Generated code was always reviewed, compiled, and tested. The concrete issues that surfaced
and how each was corrected are documented in
[`docs/AI_USAGE_NOTES.md`](docs/AI_USAGE_NOTES.md).

## License

[MIT](LICENSE) © 2026 Tushar Sharma
