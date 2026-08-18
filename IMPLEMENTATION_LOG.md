# Implementation Log

## P0 — Foundation

Status: complete

### Scope

- Establish the Git Flow branches `main`, `develop`, and `feature/p0-foundation`.
- Scaffold a pnpm monorepo with a Next.js web application.
- Scaffold a Java 21 and Spring Boot 3.x API with PostgreSQL and Flyway.
- Add design tokens, fixture provider boundaries, CI, and deterministic boot tests.
- Require no vendor credentials or growth-phase infrastructure.

### Technical decisions

- The local `docs/` specification bundle remains untracked and is ignored by Git.
- Root `AGENTS.md` is the versioned engineering-policy entry point.
- Node.js 24 LTS is the web runtime baseline.
- pnpm 11.19 provides the JavaScript workspace and frozen lockfile.
- Next.js 16.2.11 Active LTS and React 19.2 provide the web runtime.
- Dependency build scripts are denied by default in pnpm; the current P0 surface does not require Sharp image processing or native resolver post-install scripts.
- Spring Boot 3.5.16 is retained because the product architecture explicitly requires the 3.x line.
- Maven 3.9.15 is supplied through the checked-in wrapper.
- PostgreSQL is the only stateful P0 runtime; other platform components remain extension points.
- Canonical DEMO fixtures are versioned under `fixtures/v1` with envelope metadata, provenance, UTC timestamps, immutable snapshot markers, and explicit null handling.
- H2 is limited to the no-credential application-context test; Flyway is also verified against PostgreSQL 17 with Testcontainers.

### Routes

- `GET /` — fixture-backed Next.js dashboard foundation with market board, latest call ledger, provenance, timestamp, data mode, and missing-value presentation.
- `GET /actuator/health` — Spring Boot application and dependency health.
- `GET /actuator/info` — P0 application metadata and configured data mode.

### Module structure

- `apps/web` — Next.js App Router, design tokens, fixture provider port/adapter, Dashboard route, and Vitest smoke test.
- `apps/api` — Spring Boot application, market provider output port, canonical `MarketQuote`, fixture DTO mapper/adapter, UTC clock configuration, Flyway baseline, JUnit, and Testcontainers.
- `fixtures/v1` — canonical master data, analyst calls, source provenance, point-in-time market snapshots, market-map shell, and evidence-linked timeline.
- `.github/workflows/ci.yml` — repository contract, web, and API jobs.
- `compose.yaml` — PostgreSQL 17 only.

### Verification

- `pnpm install --frozen-lockfile`: passed.
- ESLint: passed with zero warnings.
- Vitest: 1 test passed.
- Next.js production build: passed; `/` and `/_not-found` generated through the App Router.
- Web runtime smoke: `/` returned HTTP 200 and included the product heading and `DEMO` marker.
- Maven `verify`: 4 tests passed, including application boot, UTC/provider boundary checks, Actuator health, and a PostgreSQL 17 Testcontainers Flyway migration.
- API runtime smoke: Spring Boot started against Compose PostgreSQL 17, applied Flyway V1, and returned HTTP 200 from `/actuator/health`.
- Compose configuration: passed; the default service set contains PostgreSQL only.
- Fixture validation: 6 JSON files parsed, common envelopes passed, and call/source/snapshot/asset/timeline references were consistent.

### Next phase

After the P0 gate passes, begin P1 with the analyst-call list/detail vertical slice, canonical provider-event identity, immutable point-in-time snapshots, and source provenance.

## P1 — Analyst Calls vertical slice

Status: complete for this vertical slice; the broader P1 phase remains in progress

### Scope

- Add the canonical institution, analyst, asset, analyst-call, source, and point-in-time snapshot models.
- Preserve the provider DTO → adapter → canonical domain boundary with fixture mode as the only runtime provider.
- Persist canonical calls and evidence through Flyway V2 and a PostgreSQL-backed repository.
- Expose paginated list and detail APIs with deterministic filtering, stable sorting, source traceability, and closed Problem responses.
- Add fixture-backed `/calls` and `/calls/[id]` routes with loading, error, empty, and not-found states.
- Version OpenAPI 3.1 and Draft 2020-12 JSON Schemas for the implemented read surface.

### Technical decisions

- The list response is exactly `{items, page}`; page numbers are zero-based and sort metadata is exactly `{field, order}`.
- Canonical identifiers are opaque strings. Provider idempotency is keyed by `(provider, provider_event_id)` and uses PostgreSQL `ON CONFLICT` atomically.
- Event time, processing time, and capture time stay distinct UTC instants; decimal financial fields use `BigDecimal` in Java.
- Market snapshots are insert-only records tied to the call and asset, and their event time must equal the call event time. No update or delete surface exists.
- Missing analyst, ticker, source metadata, snapshot, targets, or market measures remain `null`; the web layer renders `NA` and does not infer USD or other values.
- Root `fixtures/v1` remains the canonical fixture source and is copied into the API artifact at build time rather than duplicated under the application.
- Request IDs are propagated through `X-Request-Id`; 400, 404, and 500 errors use a closed `application/problem+json` shape.
- Native Git commands remain sufficient for local Git Flow. No GitHub CLI dependency was added.

### Routes

- `GET /v1/calls` — filterable, deterministic, paginated canonical analyst-call list.
- `GET /v1/calls/{id}` — canonical call detail with source evidence and nullable immutable snapshot.
- `GET /calls` — responsive DEMO analyst-call ledger with filters, sorting, pagination, and explicit empty state.
- `GET /calls/[id]` — evidence, event/processing times, target change, snapshot context, and unavailable outcome state.

### Verification

- OpenAPI 3.1, external schema references, exact filters, and closed response keys: passed.
- Four Draft 2020-12 JSON Schemas and six versioned fixture files parsed successfully.
- Compose configuration: passed with PostgreSQL as the only stateful runtime.
- ESLint: passed with zero warnings.
- Vitest: 5 files and 11 tests passed.
- Next.js production build: passed for `/`, `/calls`, and `/calls/[id]`.
- Browser QA: `/calls`, `/calls/demo-call-002`, and the not-found state rendered at 1440 px and 390 px without page-level horizontal overflow or console warnings/errors.
- Maven `verify`: 48 tests passed with zero failures, errors, or skips, including PostgreSQL 17 Testcontainers, both Flyway migrations, provider-event idempotency, nullable analyst/snapshot behavior, strict filters, source traceability, and snapshot invariants.
- Runtime smoke: the packaged Spring Boot API started against Compose PostgreSQL 17, migrated the existing schema from V1 to V2, and returned HTTP 200 from `/actuator/health`, `/v1/calls?page=0&size=1`, and `/v1/calls/demo-call-002` with the expected request ID, canonical call, source, and snapshot.

### Remaining P1 work

- Add explicit `AnalystCallRevision` lineage so corrections and cancellations link to, but never overwrite, the original event.
- Add the versioned outcome model and deterministic recalculation boundary; scoring itself remains deferred to P3.
- Add the remaining macro/context snapshot domain needed by the full P1 model.
- Exercise nullable source-document metadata through a full persistence and HTTP round trip.
