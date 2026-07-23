# Interview Prep — Order Processing System

A plain-English guide to the whole project: what each part does, **why** it's built that way,
and the questions an interviewer is likely to ask (with model answers). Read it tonight; we'll
drill against it tomorrow.

---

## 1. The 30-second elevator pitch

> "It's a Spring Boot 3 / Java 17 backend for e-commerce order processing. It exposes a REST
> API to create orders with multiple items, retrieve them by id or order number, move them
> through a strict status lifecycle, list and filter them with pagination, and cancel them —
> only while PENDING. A scheduled job auto-advances PENDING orders to PROCESSING every 5
> minutes. It's layered — thin controllers, business rules in the domain, Spring Data for
> persistence — with caching, Micrometer metrics, OpenAPI docs, and a full test suite."

Memorise the shape of that. Everything else is detail you can drill into on demand.

---

## 2. The request flow (know this cold)

For **create order**:

```
HTTP POST /api/v1/orders
  → OrderController          validates the JSON (@Valid)
  → OrderService             opens a transaction, times the operation
  → OrderMapper.toEntity     builds the Order + items, computes the total
  → assign orderNumber       ORD-yyyyMMdd-XXXXXXXX
  → OrderRepository.save     INSERT via Spring Data / Hibernate
  → OrderMetrics             increments the "created" counter
  → OrderMapper.toResponse   returns a DTO → 201 + Location header
```

If you can narrate that, you understand the whole architecture. Every other endpoint is a
simpler version of the same path.

---

## 3. Layer-by-layer — what and why

### Controller (`controller/OrderController`)
- **What:** maps HTTP to service calls; 7 endpoints.
- **Why thin:** Single Responsibility. It shouldn't hold business logic — that makes it hard
  to test and easy to duplicate rules. Validation is at the boundary (`@Valid`); rules live
  deeper.
- **Likely Q — "Why return DTOs, not entities?"** So the API contract is decoupled from the
  database. Entities can gain columns or lazy relations without breaking clients, and you
  don't accidentally serialize a lazy-loaded collection and get an error.

### DTOs (`model/dto`)
- **What:** immutable Java **records** for requests/responses, plus a paged envelope, an error
  envelope, and a statistics response.
- **Why records:** concise, immutable, no boilerplate. Immutability means a response object
  can't be mutated after it's built.

### Service (`service/OrderService` + `OrderServiceImpl`)
- **What:** the use-cases; owns transactions and caching.
- **Why an interface + impl:** Dependency Inversion — controllers/scheduler depend on the
  interface, so they're trivially unit-testable with a mock, and the implementation can change
  without touching callers.
- **Transactions here, not the controller:** each use-case is one atomic unit of work. Reads
  are `@Transactional(readOnly = true)` for a lighter DB path.

### Domain (`model/entity/Order`, `OrderItem`, `BaseEntity`) + (`model/enums/OrderStatus`)
- **What:** the entities *and* the business behaviour. `Order.transitionTo()`, `Order.cancel()`,
  `Order.recalculateTotal()`.
- **Why "rich domain model":** the rules live with the data, not scattered across services.
  You can't create an `Order` in an illegal state through the domain API.
- **`OrderStatus` is a State machine:** legal transitions are encoded on the enum
  (`canTransitionTo`). This is the single source of truth for the lifecycle.

### Repository (`repository/OrderRepository`)
- **What:** Spring Data JPA interface — query derivation (`findByStatus`, `findByOrderNumber`,
  `findByCustomerId`), aggregates (`countByStatus`, revenue sum), and a bulk update for the job.
- **Why an interface with no code:** Spring generates the implementation at runtime from the
  method names / `@Query`.

### Scheduler (`scheduler/PendingOrderScheduler`)
- **What:** `@Scheduled` job, every 5 minutes, promotes PENDING → PROCESSING.
- **Why it's just a trigger:** the real work is a service method, so it's testable without a
  scheduler and reuses the same transaction/metrics.

### Metrics (`metrics/OrderMetrics`)
- **What:** a thin Micrometer wrapper — counters (created, cancelled, transitions, swept) and a
  latency timer — exported to Prometheus.
- **Why wrap the registry:** meter names/tags live in one place, not scattered across services.

### Exception handling (`exception/GlobalExceptionHandler`)
- **What:** one `@RestControllerAdvice` translating exceptions to a consistent `ApiError` with
  the right status (404 / 409 / 400 / 500).
- **Why central:** controllers stay free of try/catch; every error has the same shape.

### Config (`config/`)
- `CacheConfig` (cache name + `@EnableCaching`), `OpenApiConfig` (Swagger metadata).

---

## 4. The design decisions to have ready (your "wow" answers)

**State pattern for status.** "I encoded legal transitions on the `OrderStatus` enum rather
than writing `if/else` in the service. It's the single source of truth — the domain rejects
illegal transitions like SHIPPED→PENDING, and 'cancel only if PENDING' falls out for free
because CANCELLED is only reachable from PENDING."

**Optimistic locking (`@Version`).** "The background job and a manual status update could hit
the same order at once. With a version column, one commit wins and the other gets an optimistic
lock exception instead of a silent overwrite. I chose optimistic over pessimistic because
conflicts are rare and I didn't want to hold DB locks."

**AtomicBoolean instead of ShedLock (my favourite).** "I first reached for ShedLock — the
standard distributed scheduler lock. Then I stepped back: it added a library, a DB table, and
config to guarantee single-node execution I didn't strictly need. The real risk — a slow sweep
overlapping itself — is handled by a five-line `AtomicBoolean` guard, and cross-instance row
safety is already guaranteed by the guarded SQL UPDATE (`WHERE status = PENDING` promotes each
row exactly once). So I removed ShedLock. Matching the solution to the actual requirement."

**Bulk update in the job.** "Instead of loading 500 PENDING orders and saving each, the job
does one set-based SQL UPDATE. Far fewer round-trips, and the `WHERE status = PENDING` guard
makes it idempotent and multi-instance safe."

**DTOs + Mapper.** "Keeps the web contract independent of the persistence model."

**Caching by profile.** "Same `@Cacheable`/`@CacheEvict` code; Caffeine in dev (in-process),
Redis in prod (shared across instances). Evict-on-write so it never serves stale data."

**Schema management.** "Hibernate `ddl-auto` manages the schema — `create-drop` in dev for a
fresh in-memory DB each run, `update` in prod." *(If asked about migrations: "For a real
production system I'd use Flyway for versioned, reviewable migrations rather than letting
Hibernate alter tables — I actually built it that way first.")*

---

## 5. "What if…" variation questions (rehearse these)

- **"Add a SHIPPED→RETURNED status."** Add the enum value and one line in
  `allowedTransitions()`. That's the payoff of the state machine — one place to change.
- **"What if two users cancel the same order at once?"** The domain check + `@Version` handle
  it: one succeeds, the other gets a 409 (illegal transition) or an optimistic-lock 409.
- **"How would you scale the background job to 10 instances?"** Today the guarded SQL UPDATE
  keeps rows safe. For strict "run on one node," add ShedLock. For throughput, shard by order
  id ranges or use a queue.
- **"How do you make order creation idempotent?"** Accept a client-supplied idempotency key,
  store it unique, and return the existing order on a duplicate.
- **"Why UUID vs Long id?"** This version uses a numeric surrogate `id` plus a human-friendly
  unique `orderNumber`. Long is compact and fast for joins; the order number is the safe public
  identifier. (UUID would avoid sequential enumeration if I didn't want ids guessable.)
- **"How would you paginate a million orders efficiently?"** Keyset/seek pagination (WHERE
  created_at < last_seen) instead of large OFFSETs; indexes on the filter/sort columns (already
  present).
- **"Where would this break under load?"** The 5-min sweep's batch size caps work per run; the
  cache reduces read pressure; the DB is the bottleneck — I'd add read replicas and connection
  pooling (HikariCP is already configured).
- **"How do you test the scheduler without waiting 5 minutes?"** The promotion logic is a
  service method with its own unit test (`promoteBatch`); the scheduler just calls it.

---

## 6. Testing story

"A test pyramid: fast unit tests for the domain (`OrderStatusTest`, `OrderTest`) and service
(`OrderServiceImplTest` with Mockito), a web-slice test (`OrderControllerTest` with `@WebMvcTest`),
a full integration test (`@SpringBootTest` + MockMvc over H2 including the error translations),
and a Testcontainers test against real PostgreSQL that's skipped gracefully when Docker isn't
present. 41 tests, plus JaCoCo coverage."

---

## 7. Real problems I hit and fixed (great honesty signals)

These are in `docs/AI_USAGE_NOTES.md`. Interviewers love "what went wrong."

1. **Lombok `@Builder` couldn't set inherited fields** → switched to `@SuperBuilder` on parent
   and child.
2. **Referenced `OrderMetrics` before creating it** → build the collaborator first.
3. **`@SchedulerLock` without the ShedLock library** → then decided to drop it for the simpler
   AtomicBoolean.
4. **Unused imports** → IDE inspection / optimise imports.
5. **Compiled with Maven but failed in IntelliJ** → the IDE's Project SDK was JDK 26 (which
   Lombok doesn't support) while the terminal used JDK 17. Set the Project SDK to 17. "Works on
   the CLI but not the IDE = toolchain mismatch."
6. **Noisy Redis health-check trace in dev** → disabled the Redis health indicator in the dev
   profile (dev uses Caffeine, not Redis).

---

## 8. Rapid-fire facts to memorise

- Java 17, Spring Boot 3.2, Maven.
- 7 endpoints under `/api/v1/orders`.
- Statuses: PENDING → PROCESSING → SHIPPED → DELIVERED, plus CANCELLED (only from PENDING).
- Job interval: 5 min (`order.processing.pending-sweep-interval-ms=300000`), batch size 500.
- Money is `BigDecimal`, never `double`.
- HTTP codes: 201 create, 200 read/update, 400 validation, 404 not found, 409 illegal
  transition / cancel-not-pending, 500 unexpected.
- Docs live at `/swagger-ui.html` and `/v3/api-docs`.

---

## 9. If you blank on a question

Say: *"Let me reason about it out loud."* Then walk from the requirement → the layer that owns
it → the trade-off. Interviewers score reasoning, not memorised trivia. And it's fine to say
*"I'd verify that, but my expectation is…"* — honesty beats bluffing.
