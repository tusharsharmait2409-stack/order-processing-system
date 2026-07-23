# AI Usage & Issues Log

This file tracks where AI assistance (ChatGPT / Cursor-style tooling) was used during
development, the concrete issues that surfaced, and how they were corrected. It feeds
the assignment's required "what did you use AI for / what issues / how corrected" write-up.

---

## Issue #1 — Lombok `@Builder` could not set inherited `id`

**Context:** Common fields (`id`, `version`, `createdAt`, `updatedAt`) were extracted
into a `BaseEntity` `@MappedSuperclass`, with `Order` extending it.

**Problem:** `Order` was annotated with Lombok's plain `@Builder`. A plain `@Builder`
only sees fields declared *on the same class*, so `Order.builder()` could not populate
the inherited `id` (and other `BaseEntity`) fields. Builds that tried to set those
fields failed / the fields silently stayed null.

**Root cause:** `@Builder` is not inheritance-aware.

**Fix:** Switched both the parent (`BaseEntity`) and child (`Order`) to Lombok's
`@SuperBuilder`, which generates an inheritance-aware builder chain so subclasses can
set superclass fields.

**Verification:** `Order.builder().customerId(...).status(...).build()` now compiles and
the inherited audit/version fields are populated.

---

## Issue #2 — Referenced `OrderMetrics` before it existed

**Context:** `OrderServiceImpl` was wired to record metrics (orders created, status
transitions, cancellations) by injecting an `OrderMetrics` collaborator.

**Problem:** The service referenced `OrderMetrics` (import + constructor field +
method calls) before the class was actually written, so the module did not compile
("cannot find symbol: class OrderMetrics").

**Root cause:** Introduced the dependency at the call site before creating the
dependency itself.

**Fix:** Created the `OrderMetrics` component first — a thin Micrometer wrapper
exposing `incrementCreated()`, `incrementCancelled()`, and `recordTransition(from, to)`
— then injected it into `OrderServiceImpl` via constructor injection.

**Lesson / prevention:** Build a collaborator (or at least a stub) before referencing
it, or let the IDE generate the missing class. Constructor injection also makes the
missing dependency obvious immediately rather than failing later at runtime.

---

## Issue #3 — Used `@SchedulerLock` without the ShedLock dependency

**Context:** To make the scheduled PENDING -> PROCESSING sweep safe to run on
multiple instances, the scheduler method was annotated with `@SchedulerLock` so
only one node executes each tick.

**Problem:** `@SchedulerLock` comes from the ShedLock library, which was not on the
classpath — `pom.xml` had no ShedLock dependency. The annotation didn't resolve
("package net.javacrumbs.shedlock... does not exist") and, even once imported,
would have been a no-op without a configured `LockProvider`.

**Root cause:** Added the annotation before adding the library and its required
supporting configuration.

**Initial fix (later reverted):** Added `shedlock-spring` + `shedlock-provider-jdbc-template`,
a `shedlock` Flyway table, a `JdbcTemplateLockProvider` config, and `@SchedulerLock`.

**Final decision — removed ShedLock, used an `AtomicBoolean` guard instead.**
ShedLock solves *strict single-node* scheduling, but for this assignment it was
over-engineered: it pulled in an external library, an extra DB table, and config for
a guarantee we didn't strictly need. The actual risk we care about — a slow sweep
overlapping itself — is handled by a simple in-process `AtomicBoolean`
(`compareAndSet(false, true)` at the start, reset in a `finally`). Cross-instance
row safety is already guaranteed by the guarded, set-based SQL UPDATE in the service
(each row is promoted exactly once). If strict single-node execution were ever
required, ShedLock could be reintroduced.

**Lesson / prevention:** (1) An annotation is only active if its library is on the
classpath *and* the supporting config is enabled. (2) Match the solution to the actual
requirement — prefer the simplest mechanism (AtomicBoolean) over heavier infrastructure
(distributed lock) unless the requirement genuinely calls for it.

---

## Issue #4 — Unused imports (dead code) flagged by the IDE

**Context:** AI-generated code sometimes leaves imports behind after logic is edited
(e.g. an `HttpStatus` import kept after switching a return style).

**Problem:** Unused imports are dead code — they add noise, can hide real
dependencies, and some builds/linters treat them as warnings or errors.

**Fix / practice:** Reviewed all classes for unused imports and removed any dead ones.
A full scan of the current source tree shows **no unused imports** (`HttpStatus` is used
in `@ResponseStatus(HttpStatus.CREATED)` and across `GlobalExceptionHandler`).

**Lesson / prevention:** Always run "Optimize Imports" (IntelliJ) / rely on the IDE's
unused-import inspection after AI edits, and keep it part of the review pass before commit.

---

## Issue #5 — Compiles with Maven but fails in IntelliJ (Lombok vs JDK 26)

**Context:** `mvn clean compile` / `mvn test` passed on the command line, but running the
app inside IntelliJ failed at compile time with:
`java.lang.ExceptionInInitializerError ... com.sun.tools.javac.code.TypeTag :: UNKNOWN`.

**Problem:** Installing Maven via Homebrew pulled in OpenJDK 26 as a dependency. IntelliJ
auto-selected that newest JDK (26) as the Project SDK, while the terminal used
`JAVA_HOME=17`. Lombok 1.18.30 (managed by Spring Boot 3.2) hooks into the compiler's
internal API and does not support JDK 26, so its annotation processor crashed — but only
in the IDE, which used JDK 26.

**Root cause:** Two different JDKs — terminal on 17, IntelliJ Project SDK on 26 — plus
Lombok's tight coupling to `com.sun.tools.javac` internals.

**Fix:** In IntelliJ *Project Structure*, set **Project SDK → JDK 17** and **Language level
→ 17** (module SDK inherits Project SDK). Rebuilt; the app compiles and runs in the IDE.

**Lesson / prevention:** Keep the IDE's Project SDK aligned with `JAVA_HOME`. "Works on the
command line but not in the IDE" is almost always a JDK/toolchain mismatch. Optionally pin
the build JDK with a Maven toolchains file.

---

## Issue #6 — Noisy Redis "Connection refused" trace in dev

**Context:** After the app booted cleanly in the IDE, the console logged a Lettuce/Netty
`Connection refused: localhost:6379` stack trace.

**Problem:** `spring-boot-starter-data-redis` is on the classpath (Redis is the prod cache).
IntelliJ's Spring Boot dashboard polls `/actuator/health`, which invokes the Redis health
indicator; since dev uses Caffeine and runs no Redis, the health ping failed and logged a
scary (but non-fatal) trace.

**Root cause:** An Actuator health indicator was active for a dependency that isn't running
in the dev profile.

**Fix:** Disabled the Redis health check in `application-dev.yml`
(`management.health.redis.enabled: false`). Prod keeps it enabled. Logs are clean.

**Lesson / prevention:** Health indicators auto-activate for any dependency on the
classpath. Disable the ones whose backing service isn't present in a given profile.
