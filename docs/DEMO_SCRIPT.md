# Demo Script

A tight, ~7-minute walkthrough for the interview. Start the app, keep Swagger UI
(`http://localhost:8080/swagger-ui.html`) and the IntelliJ **Run** console visible.

> Setup: `mvn spring-boot:run` (or run `OrderProcessingApplication` in IntelliJ).
> App on `http://localhost:8080`. Each Swagger endpoint: expand → **Try it out** →
> edit body → **Execute** → read the **Server response**.

---

## 0. Frame it (20s)

"It's a Spring Boot 3 / Java 17 order-processing backend. Layered architecture — thin
controllers, business rules in the domain, Spring Data for persistence — with a background
job, caching, metrics, OpenAPI docs, and a full test suite. Let me walk the requirements."

## 1. Create an order — multiple items (Req 1)

`POST /api/v1/orders`

```json
{
  "customerId": 1001,
  "customerEmail": "jane@example.com",
  "customerName": "Jane Doe",
  "shippingAddress": "123 Main St",
  "items": [
    { "productId": 101, "productSku": "SKU-1", "productName": "Widget", "quantity": 2, "unitPrice": 19.99 },
    { "productId": 102, "productSku": "SKU-2", "productName": "Gadget", "quantity": 1, "unitPrice": 49.50 }
  ]
}
```

**Expect 201.** Point out: generated `orderNumber` (`ORD-…`), `status: PENDING`,
`totalAmount: 89.48` — *"computed server-side from the items; the client total is never
trusted."* **Copy the `id` and `orderNumber`.**

## 2. Show validation (extra credit, 20s)

Resend with `"quantity": 0` → **400** with `fieldErrors: [{ field: "items[0].quantity", … }]`.
*"Validation at the boundary; one consistent error shape from a global exception handler."*

## 3. Retrieve — by id and by number (Req 2)

- `GET /api/v1/orders/{id}` → **200**.
- `GET /api/v1/orders/number/{orderNumber}` → **200**. *"Numeric surrogate key for joins,
  human-friendly order number for lookups."*
- `GET /api/v1/orders/{a-bad-id}` → **404**.

## 4. Update status — the state machine (Req 3)

`PATCH /api/v1/orders/{id}/status` with `{ "status": "PROCESSING", "reason": "verified" }`
→ **200**, status `PROCESSING`.

Then try `{ "status": "DELIVERED" }` on it → **409 Conflict**.
*"Legal transitions are encoded on the OrderStatus enum — a State pattern. The domain
rejects illegal jumps; the service can't bypass it."*

## 5. List + filter + paginate (Req 5)

- `GET /api/v1/orders` → paged envelope.
- `GET /api/v1/orders?status=PROCESSING` → filter by status.
- `GET /api/v1/orders?customerId=1001` → filter by customer.

## 6. Cancel — only if PENDING (Req 6)

- On the PROCESSING order: `POST /api/v1/orders/{id}/cancel` → **409** *(correct — not PENDING)*.
- Create a fresh order, then cancel it while PENDING → **200**, `CANCELLED`.

## 7. Statistics (bonus)

`GET /api/v1/orders/statistics` → total orders, breakdown by status, total revenue.

## 8. Background job (Req 4) — explain, don't wait

*"A `@Scheduled` job runs every 5 minutes and bulk-promotes PENDING → PROCESSING in one
set-based SQL UPDATE. The interval is externalised in config. I prove it with a unit test
(`promoteBatch`) rather than waiting 5 minutes live."* Optionally open the IntelliJ console
and show the sweep log line if one has fired.

## 9. Wrap: engineering story (30s)

Pick two:
- **State pattern** as the single source of truth for the lifecycle.
- **Optimistic locking** (`@Version`) for the job-vs-manual-update race.
- **AtomicBoolean over ShedLock** — *"I removed a distributed lock when a five-line guard
  plus a guarded SQL update already covered the real risk. Matching the solution to the
  requirement."*
- **Flyway→ddl-auto tradeoff**, caching by profile (Caffeine/Redis), metrics via Micrometer.

---

## Likely follow-ups (have an answer ready)

- **"Why 409 for a bad transition/cancel?"** — It conflicts with the resource's current
  state; 400 would wrongly imply a malformed request.
- **"How do you handle concurrency?"** — `@Version` optimistic locking; the bulk update's
  `WHERE status = PENDING` guard makes promotion idempotent across instances.
- **"Multiple instances running the job?"** — Each row promotes exactly once (guarded SQL).
  For strict single-node execution I'd add ShedLock; here it wasn't warranted.
- **"Why DTOs and a mapper?"** — Decouple the API contract from JPA entities so each can
  evolve independently and lazy relations don't leak into responses.
- **"Where are transactions?"** — On service methods; reads are `readOnly`.
- **"How did you use AI?"** — See `docs/AI_USAGE_NOTES.md`: I reviewed, compiled, and tested
  everything; a few real issues (Lombok `@SuperBuilder`, JDK-26 IDE mismatch) and how I fixed
  them.
