# Contributing

Thanks for your interest in the Order Processing System.

## Prerequisites

- JDK 17
- Maven
- Docker (optional — only needed to run the Postgres integration test or the prod stack)

## Getting started

```bash
git clone <your-repo-url>
cd order-processing-system
mvn spring-boot:run           # runs on H2 + Caffeine, no external services
```

API: http://localhost:8080 · Swagger UI: http://localhost:8080/swagger-ui.html

## Development workflow

1. Create a feature branch: `git checkout -b feature/short-description`
2. Make your change, keeping the layered architecture (web → service → domain → repository).
3. Add or update tests (see `src/test`).
4. Run the suite: `mvn test`
5. Format / optimise imports before committing.
6. Open a pull request describing the change and how it was verified.

## Coding standards

- Constructor injection (no field injection).
- Business rules live in the domain, not the service or controller.
- DTOs are immutable records; money is `BigDecimal`.
- Schema changes go through a new Flyway migration (`src/main/resources/db/migration`);
  never rely on Hibernate `ddl-auto` to alter tables.
- Public types carry Javadoc explaining the *why*.

## Tests

- `*Test` classes run in `mvn test` (Surefire).
- `*IT` classes are integration tests (Failsafe / `mvn verify`); the Testcontainers Postgres
  test is skipped automatically when Docker is not available.
