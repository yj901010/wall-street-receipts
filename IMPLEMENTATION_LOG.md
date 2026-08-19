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

- Add the remaining macro/context snapshot domain needed by the full P1 model.
- Exercise nullable source-document metadata through a full persistence and HTTP round trip.

## P1 — Analyst Call Revision lineage

Status: complete for this vertical slice; the broader P1 phase remains in progress

### Scope

- Add a canonical append-only `AnalystCallRevision` event for corrections and cancellations.
- Preserve the original analyst-call row, source evidence, and immutable market snapshot without mutation.
- Persist deterministic sequence and supersession lineage through Flyway V3.
- Add a fixture-backed correction followed by a terminal cancellation without changing the two base calls.
- Expose a read-only audit subresource while leaving the existing list/detail response shapes unchanged.

### Technical decisions

- A correction carries complete replacement forecast terms so JSON `null` means an observed corrected absence, not an omitted patch field.
- A cancellation carries no corrected terms and terminates the lineage; no later revision is accepted.
- Sequence numbers are one-based and contiguous. Every non-root revision must supersede the immediately preceding event for the same call.
- Revision and base-call writers atomically claim a shared `(provider, provider_event_id)` registry key, preserving idempotency and preventing cross-kind races.
- Revision time is monotonic and preserves `eventTime <= processingTime <= capturedAt`; source reference, data mode, and provenance are required.
- Existing call `status` values and filters continue to describe the immutable base event. Effective lifecycle projections, outcome recalculation, and web revision UI remain separate follow-up work.
- No POST, PATCH, PUT, or DELETE revision route exists.

### Route

- `GET /v1/calls/{id}/revisions` — sequence-ascending canonical revision history; a known call without revisions returns `[]` and an unknown call returns the standard closed 404 Problem.

### Verification

- OpenAPI 3.1 parsing, exact additive revision route, Draft 2020-12 schema validation with format checks, fixture manifest parity, and closed correction→cancellation lineage checks: passed.
- Compose configuration: passed with PostgreSQL as the only stateful runtime.
- ESLint: passed with zero warnings.
- Vitest: 5 files and 11 tests passed.
- Next.js production build: passed for `/`, `/calls`, and `/calls/[id]`; the existing web contract remained unchanged.
- Maven `verify`: 61 tests passed with zero failures, errors, or skips.
- PostgreSQL 17 Testcontainers applied Flyway V1–V3 from a fresh schema, upgraded a populated V2 schema with provider-identity backfill, and verified the shared provider-event registry, two-call/two-revision idempotent fixture import, failed-batch rollback, same-call contiguous supersession, terminal cancellation, ISO-shaped currency, source provenance, base-call and snapshot preservation, and raw-SQL constraint rejection.
- MockMvc verified ordered canonical revision fields, a known empty lineage, the closed 404 Problem, request-ID propagation, the exact GET-only mapping surface, and 405 responses for POST, PUT, PATCH, and DELETE.

### Remaining P1 work

- Add the remaining macro/context snapshot domain needed by the full P1 model.
- Exercise nullable source-document metadata through a full persistence and HTTP round trip.

## P1 — Call Outcome audit lineage

Status: complete for this vertical slice; the broader P1 phase remains in progress

### Scope

- Add closed, versioned `ScoringMethodology` and `CallOutcome` contracts without claiming P3 financial calculations.
- Package deterministic DEMO methodology/outcome fixtures through the provider DTO → mapper → canonical domain boundary.
- Persist immutable calculation identity, point-in-time references, and append-only recalculation lineage through Flyway V4.
- Expose a read-only outcome audit subresource without changing the exact call list/detail response shapes.
- Keep scoring, horizon scheduling, trading calendars, corporate actions, and golden calculation tests deferred to P3.

### Technical decisions

- An outcome lineage is scoped by `(callId, basisRevisionId, horizon, methodologyId, methodologyVersion)`; changed inputs append a new sequence with a new SHA-256 `inputFingerprint`.
- A non-null basis revision must be a same-call correction. Cancellation is separate evidence required exactly for `EXCLUDED/CALL_CANCELLED`; cancellation eligibility remains a later service policy.
- Every metric key is present but nullable. The P1 DEMO fixture uses only `PENDING` and `INCOMPLETE` records and never invents a numeric or boolean result.
- Outcome decimals use exact ratio units and the PostgreSQL `NUMERIC(38,12)` boundary. Values needing scale rounding or more than 26 integer digits are rejected.
- Persisted instants are canonical UTC values with at most microsecond precision. Calls, snapshots, revisions, methodologies, and outcomes preserve processing/capture point-in-time bounds.
- Methodology rows provide deterministic PostgreSQL lock ordering. Inserts use `ON CONFLICT DO NOTHING` followed by natural-key and outcome-ID rereads, so exact replay is idempotent and conflicting races are explicit.
- Application repositories expose insert-if-absent and read operations only. Privileged direct SQL remains an administrative trust boundary; any future external writer must use restricted roles or database mutation guards.

### Route

- `GET /v1/calls/{id}/outcomes` — deterministic horizon/methodology/sequence-ordered canonical history; a known call without outcomes returns `[]`, and invalid or unknown IDs use the existing closed Problem contracts.

### Verification

- OpenAPI 3.1 parsing, exact GET-only outcome route, external closed schema wiring, unchanged call detail shape, and exact 400/404/500 response references: passed.
- Seven Draft 2020-12 schemas and canonical fixture instances passed format validation, including UTC `Z`/microsecond precision and nullable source publication time.
- The fixture gate verified exactly two methodologies, four outcomes, three append-only lineages, manifest parity, hashes, natural identities, cross-references, point-in-time bounds, and null metric semantics.
- Maven `verify`: 91 tests passed with zero failures, errors, or skips.
- PostgreSQL 17 Testcontainers applied V1–V4 from a fresh schema and upgraded a populated V3 schema. It verified 128-character call/snapshot IDs, exact decimal and timestamp replay, raw reference/state/lineage constraints, transactional fixture import, and concurrent natural-key/outcome-ID races.
- MockMvc verified the exact canonical array, deterministic order, known-empty response, closed 400/404/500 Problems, request-ID propagation, and 405 responses for POST, PUT, PATCH, and DELETE.
- Existing web regression remained green: ESLint passed with zero warnings, Vitest passed 5 files and 11 tests, and the Next.js production build completed for `/`, `/calls`, and `/calls/[id]`.
- Compose configuration passed with PostgreSQL as the only stateful runtime.

### Remaining P1 work

- Add the remaining macro/context snapshot domain needed by the full P1 model.
- Exercise nullable source-document metadata through a full persistence and HTTP round trip.

## P1 — Nullable source-document evidence

Status: complete for this vertical slice; the broader P1 phase remains in progress

### Scope

- Make the optional source-document metadata boundary observable with a deterministic DEMO record rather than relying only on schema nullability.
- Preserve every existing fixture identity and payload, then append a new call/source/reference chain so populated databases receive the evidence safely on upgrade.
- Preserve the existing populated source records as positive controls.
- Require the fixture, provider adapter, PostgreSQL repository, and call-detail HTTP response to preserve missing metadata as null without inventing fallback values.

### Fixture and contract evidence

- `demo-call-003` → `source-ref-demo-003` → `source-demo-article-003` is an append-only fixture chain whose document explicitly sets `publisher`, `canonicalUrl`, `publishedAt`, `externalId`, and `contentHash` to JSON null.
- `source-demo-article-001`, referenced by `demo-call-001`, retains its original populated metadata so an existing insert-only database is never left with a stale payload.
- `source-demo-video-002`, referenced by `demo-call-002`, retains its populated publisher, URL, publication time, and external ID.
- The repository-contract CI script requires the new nullable chain and both preserved positive cases; the P1 acceptance gate names the fresh/upgrade persistence and HTTP round trip.

### Verification

- Eight fixture JSON files parsed successfully, fixture manifest parity passed, and the repository-contract gate verified the append-only explicit-null chain plus the preserved populated controls.
- Compose configuration passed with PostgreSQL as the only stateful runtime.
- Maven `verify`: 94 tests passed with zero failures, errors, or skips. PostgreSQL 17 Testcontainers verified the nullable document through fixture import and repository read with 3 migration tests executed and no skips.
- MockMvc verified that all five optional metadata fields remain present as explicit JSON null while required evidence and provenance remain populated.
- ESLint passed with zero warnings; Vitest passed 5 files and 13 tests; the Next.js production build completed for `/`, `/calls`, and `/calls/[id]`.
- The fixture-backed call-003 detail renders each unavailable value as `NA`, omits the canonical-source anchor when its URL is null, and preserves the populated call-002 link as a positive control.
- No production code, database migration, or API schema change was required because the existing nullable boundary behaved as designed.

### Remaining P1 work

- Add the remaining macro/context snapshot domain needed by the full P1 model.
