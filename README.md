# Order Processing System

A production-ready E-commerce Order Processing backend built with Spring Boot 3 and Java 17.

## Features

- **Order Management**: Create, retrieve, update, and cancel orders
- **Status Tracking**: Full lifecycle (PENDING → PROCESSING → SHIPPED → DELIVERED)
- **Background Processing**: Scheduled job auto-advances pending orders every 5 minutes
- **Caching**: Caffeine (dev) / Redis (prod) for fast order look-ups
- **Metrics**: Prometheus-compatible metrics via Micrometer
- **API Documentation**: OpenAPI 3.0 with Swagger UI
- **Testing**: Unit, controller, integration, and Testcontainers tests

## Tech Stack

| Component | Technology |
|-----------|------------|
| Framework | Spring Boot 3.2.0 |
| Language | Java 17 |
| Build Tool | Maven |
| Database | PostgreSQL / H2 |
| Cache | Caffeine / Redis |
| Documentation | SpringDoc OpenAPI |
| Metrics | Micrometer + Prometheus |
| Testing | JUnit 5, Mockito, Testcontainers |
| Coverage | JaCoCo |

## Getting Started

### Prerequisites

- Java 17+
- Maven 3.8+
- PostgreSQL 15+ (for production)

### Running Locally

```bash
# Clone the repository
git clone https://github.com/tusharsharmait2409-stack/order-processing-system.git
cd order-processing-system

# Build the project
mvn clean install

# Run with H2 database (development)
mvn spring-boot:run

# Run with PostgreSQL (production)
mvn spring-boot:run -Dspring-boot.run.profiles=prod
```

### Running Tests

```bash
# Run all tests
mvn test

# Run with coverage report
mvn test jacoco:report

# View coverage report
open target/site/jacoco/index.html
```

## API Endpoints

| Method | Endpoint | Description |
|--------|----------|-------------|
| POST | `/api/v1/orders` | Create a new order |
| GET | `/api/v1/orders/{id}` | Get order by ID |
| GET | `/api/v1/orders/number/{orderNumber}` | Get order by order number |
| GET | `/api/v1/orders` | List all orders (paginated) |
| PATCH | `/api/v1/orders/{id}/status` | Update order status |
| POST | `/api/v1/orders/{id}/cancel` | Cancel an order |
| GET | `/api/v1/orders/statistics` | Get order statistics |

### API Documentation

- **Swagger UI**: http://localhost:8080/swagger-ui.html
- **OpenAPI Spec**: http://localhost:8080/v3/api-docs

## Order Status Flow

```
PENDING ──────────────────→ PROCESSING ───→ SHIPPED ───→ DELIVERED
    │
    └──→ CANCELLED   (only allowed while PENDING)
```

## Example Usage

### Create an Order

```bash
curl -X POST http://localhost:8080/api/v1/orders \
  -H "Content-Type: application/json" \
  -d '{
    "customerId": 1001,
    "customerEmail": "jane@example.com",
    "customerName": "Jane Doe",
    "shippingAddress": "123 Main St",
    "notes": "Leave at the door",
    "items": [
      {
        "productId": 101,
        "productName": "Widget",
        "productSku": "SKU-1",
        "quantity": 2,
        "unitPrice": 19.99
      }
    ]
  }'
```

### Get Order by ID

```bash
curl http://localhost:8080/api/v1/orders/1
```

### Update Order Status

```bash
curl -X PATCH http://localhost:8080/api/v1/orders/1/status \
  -H "Content-Type: application/json" \
  -d '{ "status": "PROCESSING", "reason": "Order verified" }'
```

### Cancel an Order

```bash
curl -X POST http://localhost:8080/api/v1/orders/1/cancel
```

### List Orders with Filtering

```bash
# All orders
curl "http://localhost:8080/api/v1/orders"

# Filter by status
curl "http://localhost:8080/api/v1/orders?status=PENDING"

# Filter by customer
curl "http://localhost:8080/api/v1/orders?customerId=1001"

# Paginated with sorting
curl "http://localhost:8080/api/v1/orders?page=0&size=20&sortBy=createdAt&sortDir=desc"
```

## Monitoring

```bash
# Health check
curl http://localhost:8080/actuator/health

# Prometheus metrics
curl http://localhost:8080/actuator/prometheus
```

## Project Structure

```
src/
├── main/
│   ├── java/com/ecommerce/order/
│   │   ├── config/          # Configuration classes
│   │   ├── controller/      # REST controllers
│   │   ├── exception/       # Custom exceptions & handler
│   │   ├── mapper/          # DTO <-> entity mapping
│   │   ├── metrics/         # Micrometer metrics
│   │   ├── model/
│   │   │   ├── dto/         # Data Transfer Objects
│   │   │   ├── entity/      # JPA entities
│   │   │   └── enums/       # Enumerations
│   │   ├── repository/      # Data access layer
│   │   ├── scheduler/       # Background jobs
│   │   ├── service/         # Business logic
│   │   └── OrderProcessingApplication.java
│   └── resources/
│       ├── application.yml
│       ├── application-dev.yml
│       └── application-prod.yml
├── test/
│   └── java/com/ecommerce/order/
│       ├── controller/      # Controller tests
│       ├── integration/     # Integration tests
│       ├── model/           # Domain/enum tests
│       ├── repository/      # Repository tests
│       └── service/         # Service tests
└── docs/
    ├── ARCHITECTURE.md      # Architecture documentation
    └── openapi.yaml         # OpenAPI specification
```

## Configuration

| Property | Default | Description |
|----------|---------|-------------|
| `server.port` | 8080 | Server port |
| `spring.jpa.hibernate.ddl-auto` | create-drop (dev) / update (prod) | Schema mode |
| `spring.cache.type` | caffeine (dev) / redis (prod) | Cache provider |
| `order.processing.pending-sweep-interval-ms` | 300000 | Background job interval (5 min) |
| `order.processing.batch-size` | 500 | Max orders promoted per sweep |

### Environment Variables (Production)

| Variable | Description |
|----------|-------------|
| `DB_URL` | Database URL |
| `DB_USERNAME` | Database username |
| `DB_PASSWORD` | Database password |
| `REDIS_HOST` | Redis host |
| `REDIS_PORT` | Redis port |

## Design Patterns Used

1. **Repository Pattern** – Data access abstraction
2. **Service Layer Pattern** – Business logic encapsulation
3. **DTO Pattern** – API contract separation
4. **Builder Pattern** – Object construction
5. **State Pattern** – Order status transitions
6. **Template Method** – Base entity behavior

## Documentation

- [Architecture Documentation](docs/ARCHITECTURE.md)
- [OpenAPI Specification](docs/openapi.yaml)

## License

MIT License
