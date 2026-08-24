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

### Follow-up status

- The remaining macro/context snapshot domain was completed by the point-in-time context slice below.

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

### Follow-up status

- The remaining macro/context snapshot domain was completed by the point-in-time context slice below.

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

### Follow-up status

- The remaining macro/context snapshot domain was completed by the point-in-time context slice below.

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

### Follow-up status

- The remaining macro/context snapshot domain was completed by the point-in-time context slice below.

## P1 — Point-in-time macro and event context

Status: complete; this slice closes P1

### Scope

- Add closed, versioned `MacroObservation`, `MacroSnapshot`, `EventContext`, and `CallContext` contracts.
- Preserve original and later macro vintages in a standalone fixture pool while selecting only event-time-eligible observations into an immutable call snapshot.
- Store observed schedule timestamps without calculated proximity, flags, regimes, or causal inference.
- Expose a read-only additive context subresource while preserving every existing call, revision, and outcome response shape.
- Add a minimal evidence-first DEMO context read to the existing call-detail UI without expanding into P2 dashboard or interaction scope.

### Contract and fixture decisions

- `CallContext` is exactly `{macroSnapshot, eventContext}` with both keys required and nullable.
- Canonical macro snapshots embed six observations in a fixed series order; the fixture DTO carries ordered observation IDs so archived revisions remain independently readable.
- Vintage start/end dates are inclusive. Selection uses the call event time, never later processing time or the latest revised value.
- `call-contexts.json` contains only synthetic DEMO values and schedules, canonical source metadata/references, one populated call-001 context, and explicit call-002/call-003 known-empty markers.
- ADR-005 records the point-in-time rules and explicitly defers PositioningSnapshot, MarketRegime, computed proximity, scoring, realtime transport, and live providers.
- The existing call-detail route owns a minimal DEMO/as-of/source/`NA` context read; broader context dashboards, leaderboards, and interactions remain P2 work.

### Route

- `GET /v1/calls/{id}/context` — returns nullable macro/event context for a known call; invalid and unknown IDs reuse the closed Problem contract. No context mutation operation exists.

### Verification

- OpenAPI 3.1 parsing, exact GET-only context routing, unchanged call-detail shape, four closed Draft 2020-12 context schemas, nine canonical fixture documents, and manifest parity passed.
- The repository-contract gate verified five context evidence documents/references, seven standalone observations, the ordered six-series call-001 snapshot, the call-001 event context, and explicit call-002/call-003 known-empty coverage. It also proved that the later CPI revision remains archived but is excluded from the earlier event-time snapshot.
- Maven `verify`: 109 tests passed with zero failures, errors, or skips. PostgreSQL 17 Testcontainers executed 4 tests with no skips, covering fresh V1-V5 migration, a fully populated V4-to-V5 upgrade, immutable replay, concurrent same-call imports, 128-character evidence IDs, and raw point-in-time constraint rejection.
- MockMvc verified the exact populated and known-empty responses, closed 400/404/500 Problems, request-ID propagation, and the GET-only mutation surface.
- ESLint passed with zero warnings; Vitest passed 5 files and 17 tests; the Next.js production build completed for `/`, `/calls`, and `/calls/[id]`.
- Browser QA at 1280 px verified the populated call-001 context, both call-002 known-empty sections, keyboard-focusable table containment, no page-level horizontal overflow, and no console warnings or errors. The formal 390 px cross-browser layout matrix remains an owning P2 gate.
- Compose configuration and `git diff --check` passed. An independent review repeated the API verification and found no blocker or high-severity issue.

### Phase status

- P1 is complete. No mandatory P1 work remains.
- PositioningSnapshot, MarketRegime, broader context dashboards and interactions, deterministic scoring calculations, realtime transport, and live providers remain explicitly deferred to their owning later phases.

## P2 — Methodology registry

Status: complete for this vertical slice; the broader P2 phase remains in progress

### Scope

- Add a fixture-backed, read-only methodology registry page using the canonical definitions already present in `fixtures/v1/call-outcomes.json`.
- Preserve the exact two-version identity, definition hashes, model-only status, timestamps, DEMO mode, and provenance without introducing scoring or performance claims.
- Keep the slice outside API, migration, persistence-write, provider-network, scheduler, ranking, and P3 calculation scope.

### Contract and fixture decisions

- `schemas/scoring-methodology.schema.json` remains the closed canonical record contract; the fixture is the only P2 registry data source.
- Canonical registry order is `standard-call-outcome@1.0.0` followed by `standard-call-outcome@2.0.0`.
- Both records remain `MODEL_ONLY`, and every existing DEMO outcome metric/result remains null with no `CALCULATED` outcome.
- `quality/P2_ACCEPTANCE.md` defines the exact fixture, presentation, accessibility, regression, and deferred-scope gates.

### Route

- `GET /methodology` — server-rendered DEMO methodology identity and provenance registry.

### Verification

- Repository-contract checks parsed all canonical JSON files and the CI workflow, compiled every embedded Python gate, and verified the exact two-version methodology fixture, hashes, model-only state, provenance/time bounds, outcome linkage, and absence of calculated metrics.
- Frozen pnpm installation passed with `@playwright/test` 1.62.1 pinned in the workspace lockfile.
- ESLint passed with zero warnings; Vitest passed 7 files and 30 tests; the Next.js production build completed with `/methodology` statically rendered alongside the existing routes.
- Playwright Chromium passed 9 tests across 1440, 1280, and 390 pixels. It verified exact DEMO/model-only evidence, desktop-local table scrolling, the 390 stacked registry, primary-navigation and registry keyboard focus with visible outlines, ArrowRight scrolling, no page-level overflow, and zero browser console warnings, errors, or page errors.
- The responsive regression gate also found and fixed a pre-existing 390-pixel dashboard overflow caused by table min-content propagation through the dashboard grid; scoped grid-child containment preserves local table scrolling without widening the page.
- Maven `verify` passed 109 tests with zero failures, errors, or skips, including all 4 PostgreSQL 17 Testcontainers tests.
- Compose configuration and `git diff --check` passed. Independent review repeated the frozen install, lint, unit, build, responsive browser, and route checks and found no remaining blocker or high-severity issue.

### Remaining P2 work

- Keep actual historical screening filters, queries, features, and results in
  P8; the P2 application-owned known-deferred shell is complete.
- Keep institution/analyst leaderboard metrics and order in P3, and keep a
  coherent observed/licensed `PUBLISHED` market board in P5.
- Continue the shared 1440/1280/390 Playwright gate as each remaining P2 route lands.

## P2 — Multiple market-map shells

Status: complete for this vertical slice; the broader P2 phase remains in progress

### Scope

- Add independent fixture-backed S&P 500 and Nasdaq 100 analyst-consensus map
  shells while keeping P2 data explicitly synthetic, incomplete, and DEMO.
- Preserve the existing S&P cell order and numeric payload, then add closed
  coverage metadata and honest copy that rejects full-index, official-weight,
  observed-consensus, and canonical-call-derived interpretations.
- Append a separate canonical Nasdaq 100 known-empty fixture rather than copying
  S&P cells or inventing index membership, weights, metrics, or call counts.
- Keep the slice outside API, OpenAPI, database migration, persistence write,
  provider network, calculation, and materialized map-backend scope.

### Contract and fixture decisions

- `schemas/market-map.schema.json` is the closed Draft 2020-12 whole-document
  contract for exactly `sp500` and `nasdaq100` in P2 `ANALYST_CONSENSUS` mode.
- Both fixtures require exact coverage
  `{kind: SAMPLE, completeUniverse: false, cellCount: cells.length,
  weightBasis: SYNTHETIC_RELATIVE}`. Synthetic relative fixture weights are map
  geometry inputs, not official index weights.
- The S&P fixture remains the exact NVDA/MSFT/AAPL three-cell numeric projection;
  AAPL's missing metric remains JSON null and must render as `NA`.
- `market-map-nasdaq100.json` has zero cells and an explicit known-empty
  disclaimer. It adds no unsupported NDX identity to master data and never falls
  back to the populated S&P fixture.
- Map provenance now references the tracked canonical schema and P2 acceptance
  contract and records the 2026-08-19 recapture time so a clean clone can
  reproduce the evidence boundary without the ignored planning-doc tree. The
  manifest advances after both catalog members while cell event timestamps stay
  unchanged.
- `quality/P2_ACCEPTANCE.md` owns the exact contract, point-in-time, provenance,
  responsive, accessibility, interaction, regression, and later-phase deferral
  gates for both routes.

### Module structure

- `apps/web/src/lib/providers/market-map-provider.ts` defines the read-only port;
  `fixture-market-map-provider.ts` is the strict two-document fixture adapter
  with focused provider tests.
- `apps/web/src/lib/market-map-engine.ts` keeps deterministic presentation logic
  pure; `apps/web/src/components/market-map.tsx` is the shared evidence-first map
  surface.
- `apps/web/src/app/maps/[universe]` owns the dynamic server route plus loading,
  error, not-found, and unit-test boundaries; `apps/web/e2e/market-maps.spec.ts`
  owns the responsive browser regression matrix.
- `schemas/market-map.schema.json`, the S&P and Nasdaq fixture documents, and the
  dedicated repository-contract CI gate own the canonical root contract and
  exact evidence projection.

### Routes

- `GET /maps/sp500` — server-rendered limited three-cell DEMO SAMPLE.
- `GET /maps/nasdaq100` — server-rendered canonical known-empty DEMO SAMPLE.

### Verification

- The CI-identical Draft 2020-12 market-map gate passed against jsonschema
  4.23.0. It validated the exact two-file catalog, manifest order, closed schema,
  locked S&P numeric projection, Nasdaq empty state, master-data resolution,
  coverage/count equality, metric/null policy, UTC bounds, provenance
  consistency, deterministic order, and negative mutations.
- Root validation parsed 10 fixture JSON documents and 12 schema JSON documents,
  verified nine-file manifest parity, parsed the CI YAML, compiled all five
  embedded CI Python blocks, and passed `git diff --check`.
- Web ESLint passed with zero warnings; Vitest passed 10 files and 55 tests; the
  Next.js production build passed with `/maps/sp500` and `/maps/nasdaq100`
  statically rendered alongside the existing routes.
- Playwright Chromium passed 18 of 18 tests across 1440, 1280, and 390 pixels,
  including exact SAMPLE/DEMO evidence, canonical Nasdaq known-empty behavior,
  keyboard focus, page/local overflow containment, and zero console warnings,
  console errors, or page errors.
- Maven `verify` passed 109 of 109 tests. PostgreSQL 17 Testcontainers executed
  all four tests with zero skips, confirming that the fixture-only web slice did
  not regress the API or persistence boundary.
- Compose configuration passed with PostgreSQL as the only stateful runtime.

### Deferred boundary

- P3 retains deterministic scoring and performance calculations. P6 retains
  stock search/detail and its generic map-cell drill-down. P7 retains complete
  sourced index composition, official geometry inputs, additional map modes,
  filters, tooltips, and persistent/materialized read models. P5 retains real
  providers.

## P2 — Sector and industry PRICE_CHANGE treemap

Status: complete for this vertical slice; the broader P2 phase remains in progress

### Scope

- Add a screenshot-inspired nested sector/industry/leaf layout without copying
  the reference's values, labels, instructions, or market claims.
- Append separate S&P 500 and Nasdaq 100 PRICE_CHANGE read-model fixtures while
  preserving the completed analyst-consensus schema and fixtures unchanged.
- Size leaves with positive synthetic market-cap proxy units and color them with
  nullable raw synthetic price-change percentages. Never present either value as
  official market capitalization, observed price, index weight, or performance.
- Reuse only existing master-backed equity identities so this root-only slice
  does not expand API fixture imports or database persistence.

### Contract and fixture decisions

- `schemas/market-treemap.schema.json` is a separate closed Draft 2020-12
  contract. It owns PRICE_CHANGE, nested sector/industry geometry, raw percent
  values, and synthetic proxy units without widening `market-map.schema.json`.
- Both fixtures carry exact incomplete SAMPLE coverage with
  `SYNTHETIC_MARKET_CAP_PROXY`, three cells, and explicit DEMO provenance. Proxy
  values are integers from 1 through 1,000,000,000,000 and the contract permits
  at most 1,000 cells, keeping aggregate geometry inputs inside JavaScript's
  safe-integer range.
- Metric `scaleMinimum: -5` and `scaleMaximum: 5` are palette saturation stops,
  not stored-value bounds. Raw percentages remain nullable and exact in
  `[-100, 1_000_000]`; an out-of-scale value such as `-7.25` must display
  `-7.25%` while using the strongest negative tone.
- Both equal-as-of fixtures intentionally share NVDA `(144, 1.25)`, MSFT
  `(121, -0.75)`, and AAPL `(100, null)` with identical classification,
  timestamp, and data mode. Only universe-specific provenance differs.
- The current evidence demonstrates one Technology outer sector and three
  industries. The engine may support multiple sectors, but neither fixture nor
  UI may claim broader sector or index coverage.
- `quality/P2_ACCEPTANCE.md` closes route/query behavior, legacy replay,
  hierarchy ordering, point-in-time/provenance, raw-value/palette separation,
  accessibility, responsive layout, and later-phase deferrals.

### Module structure

- `apps/web/src/lib/providers/market-treemap-provider.ts` defines the read-only
  port; `fixture-market-treemap-provider.ts` is the strict, cross-universe
  consistent two-document adapter with focused boundary tests.
- `apps/web/src/lib/market-treemap-engine.ts` owns deterministic hierarchy,
  display, and palette presentation; `treemap-layout.ts` owns pure proportional
  rectangle layout. Both have focused unit suites.
- `apps/web/src/components/market-treemap.tsx` owns the nested geometry and the
  keyboard-openable non-geometric `Accessible evidence index` table, which keeps
  every stored cell inspectable without adding a minimum tile area.
- `apps/web/src/app/maps/[universe]` owns scalar mode parsing, default/query SSR,
  fail-closed not-found behavior, loading/error boundaries, and route tests;
  `apps/web/e2e/market-maps.spec.ts` owns responsive mode/navigation/geometry
  browser coverage.
- `schemas/market-treemap.schema.json`, the two appended fixture documents,
  manifest entries, and the dedicated repository-contract CI block own the
  canonical root contract and exact evidence projection.

### Routes

- `GET /maps/{universe}` and `?mode=price-change` — default server-rendered
  PRICE_CHANGE nested treemap.
- `GET /maps/{universe}?mode=analyst-consensus` — preserved completed
  analyst-consensus surface.
- Unknown or non-scalar mode input fails closed with not-found behavior;
  universe links preserve the active scalar mode.

### Verification

- The CI-identical Draft 2020-12 legacy-map and treemap gates passed against
  jsonschema 4.23.0. They verified legacy byte hashes, separate-schema closure,
  the exact two-file projection, unique manifest paths/order, master and
  equal-as-of cross-universe consistency, canonical hierarchy order, safe
  integer proxy math, raw percent bounds independent of palette stops,
  UTC/provenance, nullable classifications, and case-specific semantic negative
  mutations with `require_exact=False`.
- Root validation parsed 12 fixture JSON documents and 13 schema JSON documents,
  verified 11-file manifest parity, parsed the CI YAML, compiled all six embedded
  CI Python blocks, and passed `git diff --check`.
- Web ESLint and TypeScript checks passed; Vitest passed 13 files and 107 tests;
  the Next.js production build passed with `/maps/[universe]` remaining dynamic
  SSR for the default PRICE_CHANGE and preserved analyst-consensus route modes.
- Targeted market-map Playwright passed 9 of 9 tests. The full Playwright suite
  passed 18 of 18 tests across 1440, 1280, and 390 pixels, covering default and
  query modes, fail-closed input, mode-preserving navigation, proportional
  hierarchy, exact raw/NA presentation, the keyboard-openable evidence index and
  subpixel fallback, local/page overflow, and supported-route console warnings,
  console errors, and page errors at zero.
- Maven `verify` passed 109 tests with zero failures, errors, or skips.
  PostgreSQL 17 Testcontainers executed all four tests with zero skips, confirming
  no API or persistence regression from the fixture-only web read model.
- Compose configuration passed with no output or errors and PostgreSQL remained
  the only stateful runtime.

### Deferred boundary

- P3 retains scoring/performance calculations; P5 retains observed quotes and
  licensed market-cap providers; P6 retains stock detail/history; P7 retains
  sourced complete-universe membership/classification, official geometry,
  live/observed price mode, filters, rich tooltips, zoom/history, additional map
  modes, and persistent/materialized read models.

## P2 — Dashboard evidence composition

Status: complete for this vertical slice; the broader P2 phase remains in progress

### Scope

- Replace the `/` dashboard's display-ready hard-coded market and call payload
  with a deterministic composition of the existing calls and PRICE_CHANGE
  treemap provider ports.
- Populate only the latest canonical call ledger and the S&P 500/Nasdaq 100
  PRICE_CHANGE previews. Preserve each section's own timestamp, source,
  provenance, coverage, disclaimer, data mode, and null semantics.
- Publish no market board or global event calendar from call-event snapshots or
  call-bound context. Keep ranking explicitly deferred until P3 provides
  deterministic aggregates.
- Preserve all existing canonical fixtures, schemas, API responses, database
  migrations, and completed routes.

### Contract decisions

- `DashboardSnapshot` has no global as-of/source. It contains DEMO mode,
  section-local latest-call metadata, two complete canonical map previews, and
  three closed state objects: market board `NOT_PUBLISHED`, event calendar
  `NOT_PUBLISHED`, and ranking `P3_DEFERRED`, each with `NA` missing display.
- Latest calls use the existing page-zero, size-three, event-time-descending
  query with call-ID ascending tie order. The current fixture projects call 002,
  call 001, then call 003 without duplicating their fields in dashboard source.
- Map previews are exactly S&P 500 followed by Nasdaq 100 in PRICE_CHANGE mode.
  They remain incomplete, synthetic three-cell DEMO samples and retain their
  independent as-of/provenance evidence.
- Immutable market snapshots remain evidence frozen at individual call event
  times; they are not a current/global quote board. EventContext schedules stay
  bound to their owning call and are not a global upcoming calendar.
- No ranking row, metric, order, winner, call-count proxy, or outcome placeholder
  is published. P3 remains the sole owner of performance aggregation.
- The shared calls adapter now parses canonical UTC instants into integer
  microseconds and normalizes offset query bounds before comparing them. Latest
  call sorting and inclusive-`from`/exclusive-`to` range filtering therefore do
  not rely on lexicographic timestamp order or JavaScript's millisecond-only
  `Date` precision; whole-second, fractional, equivalent-fraction, and offset
  boundary regressions are covered explicitly.
- `quality/P2_ACCEPTANCE.md` owns the exact projection, claim, null, state,
  accessibility, responsive, regression, and later-phase boundaries. No new
  dashboard fixture/schema, API/OpenAPI surface, or Flyway migration is added.

### Module structure

- `apps/web/src/lib/providers/market-provider.ts` owns the dashboard projection
  port; `fixture-market-provider.ts` composes injected `CallsProvider` and
  `MarketTreemapProvider` instances without importing raw fixtures.
- `apps/web/src/lib/providers/fixture-calls-provider.ts` owns lossless UTC
  microsecond ordering and range comparison for the shared call projection;
  its focused tests lock whole-second/fractional and offset-bound behavior.
- `apps/web/src/components/dashboard-view.tsx` owns pure evidence-first
  presentation and the three unavailable/deferred states.
- `apps/web/src/app/page.tsx` remains the server-rendered route boundary;
  route-level loading/error files and focused component/provider tests own the
  fail-closed states.
- `apps/web/e2e/dashboard.spec.ts` owns keyboard, navigation, state, responsive,
  overflow, and runtime-error browser coverage for the composed route.
- Root P2 acceptance and the focused repository source-boundary check own the
  no-hardcoded-display, no-raw-fixture-import, and no-cross-semantic-promotion
  gates without duplicating existing fixture schema validation.

### Route

- `GET /` — server-rendered DEMO evidence composition with latest calls, both
  PRICE_CHANGE previews, and honest market/calendar/ranking states.

### Verification

- The focused dashboard repository source-boundary check passed, confirming the
  injected calls/treemap composition and absence of raw dashboard fixture
  imports, call-bound snapshot/context promotion, duplicated call literals, and
  the obsolete hard-coded market display values.
- CI YAML parsing passed, all seven embedded Python blocks compiled, and
  `git diff --check` passed.
- Web ESLint and TypeScript checks passed. Vitest passed 14 files and 127 tests,
  including provider composition, malformed/mixed evidence rejection, exact
  unavailable/deferred states, UTC microsecond call ordering/ranges, and route
  presentation. The production Next.js build passed.
- The full Playwright suite passed 18 of 18 tests across 1440, 1280, and 390
  pixels, covering the dashboard alongside the existing responsive routes.
- Independent final review found no blocker, high-severity issue, or known
  false-positive in the dashboard provider, presentation, or test gates.
- Maven `verify` passed 109 of 109 tests with zero failures, errors, or skips.
  PostgreSQL 17 Testcontainers executed all four migration tests with zero
  skips.
- Compose configuration passed with no output or errors.

### Deferred boundary

- P3 retains ranking/performance calculations; P5 retains observed/licensed
  current market quotes and a coherent published market board. A future global
  calendar requires its own sourced catalog rather than call-bound context.
- Institution/analyst leaderboards, the screener shell, realtime refresh,
  personalized dashboard layout, and persistent/materialized dashboard read
  models remain open.

## P2 — Institution identity directory

Status: complete for this vertical slice; the broader P2 phase remains in progress

### Scope

- Add a server-rendered `/institutions` DEMO identity directory backed only by
  the existing canonical master-data fixture.
- Preserve exact root provenance and point-in-time identity fields while
  distinguishing fixture-active state from verified current-world status.
- Provide navigation into the existing call ledger by institution ID without
  projecting calls, totals, analysts, employment, outcomes, or performance.
- Keep the P2 leaderboard goal and every deterministic aggregate deferred to P3.

### Contract and fixture decisions

- No schema, fixture, or manifest member is added. The strict adapter validates
  the existing master document's exact nine-key envelope, exact six-key
  provenance, and exact nine-key institution records at runtime.
- `InstitutionDirectorySnapshot` projects only `schemaVersion`,
  `fixtureVersion`, `dataMode`, `generatedAt`, `provenance`, and
  `institutions`. Analysts, analyst employments, and assets remain required raw
  arrays but are not exposed.
- Institution output uses deterministic Unicode code-point canonical-name
  ascending, institution-ID ascending tie order without mutating source data.
  The current exact result is Goldman Sachs followed by JPMorgan. Repository CI
  locks that current projection, while the generic mapper remains append-safe
  for a valid empty collection or later valid identities.
- Generation, provenance capture, record capture, and effective time remain
  distinct. Every record must match the DEMO envelope and root provenance.
- Because `master-data.json` has no disclaimer, the page uses clearly labelled
  static product-policy copy; it does not invent or attribute a fixture
  disclaimer.
- No institution detail, call count/row, employment, holding, ranking, score,
  accuracy, return, recommendation, provider call, API/OpenAPI surface, or
  database migration is part of this slice.

### Module structure

- `apps/web/src/lib/providers/institution-directory-provider.ts` owns the
  read-only port and snapshot types;
  `fixture-institution-directory-provider.ts` owns strict master
  envelope/provenance/record validation, the pure projection, deterministic
  non-mutating order, and focused provider tests.
- `apps/web/src/app/institutions/institution-directory.tsx` owns pure identity
  evidence presentation, policy disclosure, empty state, and explicitly
  labelled call-ledger filter links.
- `apps/web/src/app/institutions/page.tsx`, `loading.tsx`, and `error.tsx` own
  the server-rendered route and its loading/recoverable error boundaries;
  `page.test.tsx` and `apps/web/e2e/institutions.spec.ts` own route and
  responsive browser regression coverage.
- `apps/web/src/components/site-header.tsx` and scoped global styles own the
  primary navigation/focus/responsive integration without creating a detail
  route.
- Root P2 acceptance and the dedicated repository-contract CI block own the
  exact source shape, current records, time/provenance, source isolation, and
  no-leaderboard boundary.

### Route

- `GET /institutions` — server-rendered synthetic DEMO institution identity and
  provenance directory. No institution detail route is added.

### Verification

- The CI-identical institution repository contract/source-isolation block
  passed against the current two-record fixture and append-safe web adapter.
  All eight workflow Python blocks compiled, the workflow parsed with
  SnakeYAML, and `git diff --check` passed.
- Web ESLint and `tsc --noEmit` passed. Vitest passed 16 files and 156 tests,
  including closed-shape mapping, valid empty/later append behavior,
  deterministic non-mutating order, evidence presentation, and route states.
- The production Next.js build passed with `/institutions` included.
- Targeted institution Playwright passed 6 of 6 tests. The full Playwright suite
  passed 24 of 24 tests across 1440, 1280, and 390 pixels with zero captured
  browser console warnings/errors or page errors on the supported flows.
- An in-app browser desktop visual check passed for the exact evidence, policy,
  and table presentation, locally contained horizontal scrolling, and visible
  table-region focus.
- Maven `verify` passed 109 tests with zero failures, errors, or skips;
  PostgreSQL 17.10 Testcontainers executed all four migration tests. Compose
  configuration validation passed quietly.
- Independent final review found no blocker, high-severity issue, or known
  false-positive in the institution provider, UI, contract, or tests.

### Deferred boundary

- P3 retains institution/analyst leaderboard calculations and ordering. Detail,
  aliases/history, analyst employment, holdings, and verified provider data
  require their own later contracts.
- Screener, realtime refresh, search, pagination, saved filters, and
  persistent/materialized directory read models remain open; the recorded S&P
  forecast-call history is handled by its own P2 slice below.

## P2 — Analyst identity directory

Status: complete for this vertical slice; the broader P2 phase remains in progress

### Scope

- Add a server-rendered `/analysts` DEMO identity directory backed only by the
  existing canonical master-data fixture.
- Preserve exact root provenance and point-in-time analyst identity fields while
  distinguishing captured fixture-active state from verified current-world
  employment or activity.
- Publish no employment, institution relationship, call preview/count/
  existence claim, metric, ranking, performance, or detail projection. Permit
  only explicitly labelled filter navigation into the existing call ledger.
- Keep every leaderboard aggregate and ordering decision in P3.

### Contract and fixture decisions

- No schema, fixture, manifest member, API/OpenAPI surface, migration,
  persistence write, or network provider is added.
- `AnalystDirectorySnapshot` projects only `schemaVersion`, `fixtureVersion`,
  `dataMode`, `generatedAt`, exact root `provenance`, and seven-field analyst
  identities from `master-data.json`.
- The generic strict mapper remains valid-empty and append-safe, sorts by full
  Unicode code point using canonical name then analyst ID, and never mutates
  source order. Repository CI locks the current Demo Analyst A then Demo Analyst
  B projection without moving those literals into production code.
- Raw institutions, analyst employments, and assets remain required arrays for
  closed envelope validation but do not enter the output.
- Since the master fixture has no disclaimer, presentation uses clearly
  labelled product-policy copy without attributing it to fixture evidence.

### Module structure

- `apps/web/src/lib/providers/analyst-directory-provider.ts` owns the read-only
  port and snapshot types; `fixture-analyst-directory-provider.ts` and its
  focused test own strict master mapping, validation, deterministic order, and
  malformed/empty/append/same-name regressions.
- `apps/web/src/app/analysts/analyst-directory.tsx` owns pure identity evidence,
  product-policy copy, empty presentation, and the filter-only ledger action.
  `page.tsx`, `loading.tsx`, `error.tsx`, and `page.test.tsx` own the SSR route,
  route states, exact current evidence, and relationship/performance boundary.
- `apps/web/src/components/site-header.tsx` and scoped global styles own primary
  navigation, visible focus, dense-table containment, and responsive layout.
- `apps/web/e2e/analysts.spec.ts` owns responsive, focus, overflow, evidence,
  state, and runtime-error browser coverage.
- Root P2 acceptance and the shared master-data identity CI gate own current
  fixture exactness, source isolation, semantic negatives, and the no-
  relationship/no-leaderboard boundary.

### Route

- `GET /analysts` — server-rendered synthetic DEMO analyst identity and root
  provenance directory. No analyst detail route is added.

### Verification

- The CI-identical shared master-data identity/source-isolation gate passed for
  both directory adapters, current exact records, semantic negatives, valid
  empty/later appends, source non-mutation, and same-name analyst ID tie-break.
  All eight workflow Python blocks compiled, the workflow parsed with
  SnakeYAML, and `git diff --check` passed.
- Web ESLint and `tsc --noEmit` passed. Vitest passed 18 files and 183 tests,
  including closed projection, malformed evidence, valid empty/later append,
  full Unicode order, same-name identity, and route-state coverage.
- The production Next.js build passed with `/analysts` included.
- Targeted analyst Playwright passed 6 of 6 tests. The full Playwright suite
  passed 30 of 30 tests across 1440, 1280, and 390 pixels with zero captured
  browser console warnings/errors or page errors on supported flows.
- An in-app browser desktop production-build check rendered exact provenance,
  policy, and analyst-table evidence with zero page overflow. The table kept
  its 1201/1220 horizontal extent local, exposed a visible 2 px solid keyboard
  focus outline, and the Demo Analyst A filter navigated to
  `/calls?analystId=analyst-demo-a` with the Analyst select value and `Analyst
  calls` heading intact.
- Maven `verify` passed 109 of 109 tests with zero failures, errors, or skips;
  PostgreSQL 17.10 Testcontainers executed all four migration tests with zero
  skips. Compose configuration validation passed.
- Independent final review found zero blockers, high-severity findings, or
  known false-positive gates; repeated checks and generated/temp audit were
  clean.

### Deferred boundary

- P3 retains analyst/institution leaderboard calculations, metrics, and order.
- Analyst detail, aliases/history, employment/institution relationships, call
  projections/counts, holdings, search, pagination, live providers, and
  persistent directory read models remain open.

## P2 — Market-board known-unavailable publication state

Status: complete for this vertical slice; the broader P2 phase remains in progress

### Scope

- Add a server-rendered `/market` publication-status surface backed by a new
  closed canonical DEMO document whose only valid v1 state is honestly
  `NOT_PUBLISHED` with zero quote rows.
- Keep call-event snapshots, PRICE_CHANGE treemaps, P0 fixture quote literals,
  and application display values out of the global market-board evidence
  boundary.
- Inject the dedicated board provider into dashboard composition while
  preserving the dashboard's existing exact two-field board output.
- Add no external provider, API/OpenAPI path, migration, persistence write,
  cache, stream, polling, quote calculation, or market-session inference.

### Contract and fixture decisions

- `schemas/market-board.schema.json` owns a closed Draft 2020-12 v1 contract;
  `fixtures/v1/market-board.json` owns the append-only known-unavailable DEMO
  state, exact provenance, null market as-of, zero quotes, and the no-promotion
  disclaimer.
- Fixture generation/provenance capture are publication-policy/catalog
  evidence, not a market timestamp or freshness claim. The manifest is
  recataloged after the new member and retains every prior file in order.
- A future observed/licensed `PUBLISHED` quote board requires a separately
  reviewed additive/versioned contract; it cannot widen the closed v1 state.
- Root P2 acceptance and focused repository CI own exact projection,
  schema/semantic negatives, manifest/time/source traceability, provider
  isolation, dashboard compatibility, and backend defer.

### Module structure

- `apps/web/src/lib/providers/market-board-provider.ts` owns the read-only port
  and canonical snapshot types; `fixture-market-board-provider.ts` owns the
  sole raw fixture import, strict validation, and focused adapter tests.
- `apps/web/src/app/market/market-board.tsx` owns the pure evidence-first
  known-unavailable presentation. `page.tsx`, `loading.tsx`, `error.tsx`, and
  `page.test.tsx` own the SSR route, state boundaries, and exact evidence/no-
  quote assertions; `apps/web/e2e/market.spec.ts` owns responsive route and
  transition coverage.
- `fixture-market-provider.ts` consumes the injected board port and maps its
  validated state to the unchanged dashboard `marketBoard` projection;
  dashboard provider/unit/E2E regressions lock that compatibility.
- `apps/web/src/components/site-header.tsx` and scoped global styles own the
  semantic Dashboard/Market navigation, visible focus, and dense responsive
  containment.
- The new root schema, fixture, appended manifest entry, P2 acceptance, and CI
  gate own the canonical and source-isolation boundary; the API remains
  untouched.

### Route

- `GET /market` — server-rendered synthetic DEMO global market-board
  publication status with exact provenance and no quote facts.

### Verification

- The CI-identical repository workflow passed all nine embedded Python blocks,
  parsing 14 closed schemas and validating 32 canonical fixture records. The
  focused board gate passed exact schema/fixture/provenance, structural zero-
  quote, manifest/time, semantic-negative, source-isolation, dashboard-
  injection, and API-defer checks. Root JSON/schema validation, SnakeYAML
  parsing, and `git diff --check` passed.
- Web ESLint and `tsc --noEmit` passed. Vitest passed 20 files and 213 tests,
  including exact runtime provenance/disclaimer/source paths, malformed-state
  rejection, fixture immutability, and dashboard two-field compatibility.
- The Next.js production build passed with the static `/market` route.
- Targeted market Playwright passed 6 of 6 tests. The keyboard-transition
  sequence passed 18 of 18 checks across three serial repetitions, and the full
  Playwright suite passed 36 of 36 tests across 1440, 1280, and 390 pixels.
- An in-app Browser production-build check passed with no page overflow, exact
  publication status/provenance/policy evidence, visible keyboard focus, and
  working dashboard-to-market and market-to-dashboard navigation.
- Maven `verify` completed with `BUILD SUCCESS`: 109 of 109 tests passed with
  zero failures, errors, or skips, and PostgreSQL 17.10 Testcontainers executed
  all four migration tests with zero skips. Compose configuration validation
  passed.
- Independent final review found zero blockers, high-severity findings, or
  known false-positive gates. The reviewer independently repeated ESLint,
  TypeScript, Vitest 20/213, targeted Playwright 6/6, and full Playwright 36/36;
  generated files, temporary artifacts, and development ports were clean.

### Deferred boundary

- P4 retains realtime ingestion/cache/SSE/reconnect/stale/session behavior; P5
  retains licensed providers and canonical observed quote publication; P6
  retains stock/equity history and P8 retains historical market bars plus
  operational data-quality monitoring.
- A published board, quote rows, coverage/counts, instruments beyond an
  independently sourced catalog, alerts, watchlists, and persistent/materialized
  market read models remain open.

## P2 — S&P 500 recorded forecast-call history

Status: complete for this vertical slice; the broader P2 phase remains in progress

### Scope

- Add a server-rendered `/markets/sp500` history of canonical recorded analyst-
  call events filtered to the synthetic DEMO S&P 500 identity.
- Preserve exact call, normalized identity, source evidence, nullable target,
  catalog timestamp, provenance, pagination, and data-mode semantics.
- Present the current one-row fixed-page fixture result as limited DEMO
  evidence, not a complete ledger, analyst coverage, price history, consensus,
  current market state, or forecast performance. Keep displayed items distinct
  from the filtered query `totalElements`.
- Add no calculation, chart, snapshot/outcome/context join, raw fixture import,
  external provider, schema, fixture, manifest member, API/OpenAPI path,
  migration, persistence write, or scheduler.

### Contract and projection decisions

- `Sp500HistorySnapshot` has exactly `dataMode`, `asOf`, `source`,
  `disclaimer`, `asset`, `items`, and `page`; nested values reuse the complete
  existing `AssetSummary`, `AnalystCallView`, and `PageMetadata` contracts.
- The dedicated adapter composes an injected `CallsProvider`, invokes
  `metadata()`, and issues exactly one page-zero, size-25, `asset-spx`, event-
  time-descending query. It does not import or reach through raw fixture JSON.
- The current exact projection is the `asset-spx` index identity and
  `demo-call-001`. Runtime uniquely selects only the `asset-spx` metadata entry
  and preserves its four fields rather than hard-coding its current display
  values. Generic mapping remains valid-empty/future-row safe on the fixed
  first page, deterministic by event time plus call ID, and non-mutating.
- Metadata `asOf` is call-catalog generation evidence only. Event, processing,
  publication, and capture times remain distinct; none becomes a current market
  or performance timestamp.
- Raw previous/current targets remain separate source fields. No delta, gap,
  return, alpha, hit, accuracy, score, rank, consensus, snapshot number, or
  coverage metric is derived.
- The table exposes summarized source title/publisher/verified evidence. Its
  exact call-detail `#source` link owns access to complete source document and
  reference evidence; the history page does not claim to reproduce it all.
- Root P2 acceptance and focused CI own the exact current projection, injected-
  provider/source boundary, no-new-contract gate, claim boundary, and later-
  phase defer without duplicating existing analyst-call schema validation.

### Module structure

- `apps/web/src/lib/providers/sp500-history-provider.ts` owns the read-only
  projection types and port; `fixture-sp500-history-provider.ts` owns injected
  calls composition, strict validation/order, and focused unit tests without a
  raw fixture import.
- `apps/web/src/app/markets/sp500/sp500-call-history.tsx` owns pure event-
  history evidence and honest empty presentation. Its route `page.tsx`,
  `loading.tsx`, `error.tsx`, and `page.test.tsx` own SSR and route states.
- `apps/web/src/app/markets/sp500/keyboard-scroll-region.tsx` is the scoped
  client island. Only self-focused, unmodified ArrowLeft/ArrowRight input moves
  the local region, clamped between zero and its scroll maximum; no-overflow and
  already-at-edge input is left untouched as a no-op.
- `/market`, the existing site header/current-section contract, and scoped
  global styles own semantic route discovery, keyboard focus, and responsive
  containment without adding a primary navigation item.
- `apps/web/e2e/sp500-history.spec.ts` owns Market-to-history/detail navigation,
  evidence, focus, overflow, responsive, and runtime-error browser coverage.
- Root acceptance, implementation log, and the focused repository source-
  boundary/current-projection gate own the no-schema/fixture/API boundary.

### Route

- `GET /markets/sp500` — server-rendered limited synthetic DEMO history of
  recorded S&P 500 analyst-call events with source evidence.

### Verification

- Web ESLint and `tsc --noEmit` passed. Vitest passed 22 files and 250 tests;
  the Next.js production build passed with 11 routes and statically rendered
  `/markets/sp500`.
- Targeted S&P history Playwright passed 6 of 6 tests. The full suite passed 42
  of 42 tests across 1440, 1280, and 390 pixels. The production `next-env.d.ts`
  import was restored with no diff, ports 3000 and 3011 were idle, and the
  `apps/web` diff check passed.
- Maven `verify` passed 109 of 109 tests with zero failures, errors, or skips.
  PostgreSQL 17.10 Testcontainers executed all four migration tests with zero
  skips, and Compose configuration validation passed.
- The focused CI-identical SPX projection/source-isolation gate and all 10
  embedded workflow Python blocks passed. Repository validation parsed 14
  schemas and 32 fixture records; SnakeYAML parsing and `git diff --check`
  passed.
- In-app Browser QA against the desktop production route confirmed the visual
  evidence layout and semantic DOM. The history region contained its wide
  content locally (`1520 > 1201`), ArrowRight changed `scrollLeft` from `0` to
  `300`, and the `/calls/demo-call-001#source` link reached the call detail's
  Source provenance. The browser tab and server were closed and port 3120 was
  idle afterward.
- Independent final review reported blocker `0`, HIGH `0`, and known false-
  positive `0`; the latest tree, log truth, generated-file state, and ports
  were clean.

### Deferred boundary

- P3 retains deterministic performance scoring/aggregation; P4/P5 retain
  realtime and licensed observed market data; P6 retains stock/equity history,
  sector benchmarks, and corporate-action-aware views; P8 retains historical
  bars and materialized analytics/screener features.
- Pagination/search, current consensus, charts, snapshot/outcome joins,
  completeness claims, and persistent/materialized history read models remain
  open.

## P2 — Screener known-deferred shell

Status: complete for this vertical slice; the broader P2 phase remains in progress

### Scope

- Add a server-rendered `/screener` capability-state shell without presenting a
  stock screen, query execution, result collection, metric, or observed fact.
- Publish the exact application-owned DEMO/P8-deferred state and distinguish it
  from canonical fixture evidence, provider availability, and an empty
  successful search.
- Reject every request containing a search-parameter key through the custom
  noindex unsupported-request boundary; do not ignore, normalize, or echo input
  as a filter.
- Add Screener to primary navigation after Maps and before Methodology while
  preserving existing destinations, current-page semantics, keyboard focus,
  and narrow local containment.
- Add no schema, fixture, manifest member, OpenAPI/API path, Flyway migration,
  persistence model, provider network call, scheduler, calculation, saved
  filter, or materialized feature catalog.

### Contract and phase decisions

- `ScreenerShellState` has exactly `dataMode`, `scope`, `status`, `reasonCode`,
  and `missingDisplay`, in order, with the sole values `DEMO`,
  `HISTORICAL_EQUITY_SCREENING`, `P8_DEFERRED`,
  `NO_CANONICAL_HISTORICAL_SCREENING_FEATURE_CATALOG`, and `NA`.
- This is typed application phase policy, labelled `Product availability policy
  · not fixture evidence`. It intentionally has no version, timestamp, source,
  provenance, license, disclaimer, filter, result, row, count, pagination,
  sort, feature, metric, universe, or symbol field.
- `NA` means the screening capability is not published. It does not mean zero
  matches, a zero numeric value, completeness, or a successful empty query.
- Any `searchParams` key, including unknown, recognized-looking, empty-value,
  or repeated input, invokes Next `notFound()` before the supported state is
  rendered. Browser acceptance owns the custom body and noindex metadata, not
  a raw status assertion under streamed response semantics.
- Calls, outcomes, event-time snapshots/contexts, master assets, maps/treemaps,
  and market-board state remain in their own semantics and are never screening
  features, candidates, matches, or proxy metrics.
- P8 owns an eventual versioned historical feature/query read model; P3 owns
  deterministic outcome/ranking calculations; P5 owns licensed observed
  providers and rights review. This P2 route does not accelerate those phases.

### Module structure

- `apps/web/src/lib/screener-shell-state.ts` owns the pure typed, frozen five-
  key application capability value without a fixture adapter, provider,
  network call, raw JSON import, or canonical-document parser; its focused unit
  test owns exact shape/value and absence checks.
- `apps/web/src/app/screener/page.tsx` owns the query-free server route and
  connects every nonempty search-parameter key directly to Next `notFound()`.
  `screener-shell.tsx` owns the pure policy presentation, while `loading.tsx`,
  `error.tsx`, `not-found.tsx`, and `page.test.tsx` own explicit route states,
  mode-neutral unsupported requests, noindex behavior, and regression tests.
- `apps/web/src/components/site-header.tsx` and scoped global styles own the
  additive Screener position/current state, visible keyboard focus, and narrow
  local-navigation containment.
- `apps/web/e2e/screener.spec.ts` owns supported/unsupported requests,
  navigation order/current state, exact evidence and absence gates, focus,
  overflow, responsive, and runtime-error browser coverage.
- Root P2 acceptance, implementation log, and the focused repository CI gate
  own the no-schema/fixture/manifest/OpenAPI/API/Flyway and append-safe
  production-source boundaries.

### Route

- `GET /screener` — server-rendered application-owned DEMO capability state;
  no screening query, controls, results, or metrics are published.

### Verification

- Web ESLint and `tsc --noEmit` passed. Vitest passed 24 files and 255 tests;
  the Next.js production build passed with `/screener` included.
- Targeted Screener Playwright passed 9 of 9 tests. The full suite passed 51 of
  51 tests across 1440, 1280, and 390 pixels. The production `next-env.d.ts`
  import was restored, generated reports and test results were removed, ports
  3000 and 3011 were idle, and the `apps/web` diff check passed.
- Maven `verify` passed 109 of 109 tests with zero failures, errors, or skips.
  PostgreSQL 17.10 Testcontainers executed all four migration tests with zero
  skips, and Compose configuration validation passed.
- The focused CI-identical Screener source/no-contract gate and all 11 embedded
  workflow Python blocks passed syntax and execution. Repository validation
  parsed 14 schemas and 32 fixture records; SnakeYAML parsing and
  `git diff --check` passed.
- In-app Browser QA against the production route confirmed clean visual and
  semantic output, the exact eight-link primary navigation, and navigation
  containment (`744 / 744`) inside the `1265`-pixel viewport. The policy region
  exposed a visible `2px solid` keyboard focus outline, with zero form, input,
  select, button, table, row, canvas, SVG, time, or metric elements.
- The unsupported-query route rendered its mode-neutral body without capability
  state leakage. The current Next 16.2.11 runtime emitted two identical
  framework-owned noindex tags; no custom metadata was configured. The browser
  tab and server were closed, port 3122 was idle, and generated artifacts were
  absent.
- Independent final review reported blocker `0`, HIGH `0`, and known false-
  positive `0`; contracts, application state, query rejection, mode-neutral
  unsupported behavior, phase boundaries, navigation, CI gates, and tests were
  aligned, while generated/status state and ports were clean.

### Deferred boundary

- P8 retains historical bars, point-in-time screening facts/features, query
  execution, pagination/sorting, saved screens, materialization, and regime
  analytics. Any populated `/v1/screener` requires its own reviewed contract.
- P3 retains outcome and leaderboard metrics; P5 retains observed provider
  integration, licensing flags, and current display/redistribution rights.
- Broader P2 remains open only for its still-undelivered surfaces; leaderboard
  computation stays P3-owned, actual historical screening stays P8-owned, and
  live/provider-backed publication stays P5-owned.

## P3 — Pure target-hit comparison core

Status: complete; no calculation result is published or persisted, and broader
P3 scoring work remains open

### Scope

- Add the smallest deterministic scoring primitive supported by the existing
  written specification: an inclusive bullish/bearish target-hit comparison
  over a target and one preselected favorable window extreme.
- Preserve missing target/extreme inputs as one explicit unavailable reason
  rather than Boolean misses, zeroes, or incomplete canonical facts.
- Exercise the primitive with source-local parameterized golden vectors while
  leaving every canonical model-only methodology and outcome record unchanged.
- Add no horizon/calendar/window selection, call-direction mapping, numeric
  return calculation, provider, scheduler, schema, fixture, manifest member,
  OpenAPI/API path, Flyway migration, persistence write, or web surface.

### Contract and phase decisions

- `ADR-006` and `quality/P3_ACCEPTANCE.md` own the exact input, comparison,
  decimal, unavailable, purity, and later-integration boundaries.
- `TargetHitInput` accepts an interpreted `BULLISH` or `BEARISH` side, nullable
  positive `BigDecimal` target, and one nullable positive favorable extreme.
  Bullish interprets it as the caller-selected window high and compares `>=`;
  bearish interprets it as the caller-selected window low and compares `<=`;
  equality is a hit.
- `TargetHitResult` is sealed `Available(boolean)` or
  `Unavailable(UnavailableReason)` with exactly `TARGET_MISSING`,
  `FAVORABLE_EXTREME_MISSING`, or
  `TARGET_AND_FAVORABLE_EXTREME_MISSING`.
- Every present decimal is exactly `NUMERIC(38,12)` representable. Validation
  may probe exact scale/precision without mutating inputs; comparison performs
  no rounding or output normalization. Side interpretation is mandatory and
  fail-closed.
- Neither existing `standard-call-outcome` definition hash is attached to this
  primitive. Both methodologies stay `MODEL_ONLY`; no `CALCULATED` outcome or
  input fingerprint is produced.

### Module structure

- `apps/api/src/main/java/.../domain/outcome/calculation` owns exactly
  `TargetHitSide.java`, `TargetHitInput.java`, `TargetHitResult.java`, and
  `TargetHitCalculator.java` without Spring or infrastructure dependencies.
- `TargetHitCalculatorGoldenTest.java` under the matching API test package owns the
  documented golden vectors, equality and near-boundary cases, explicit missing
  states, invalid values, determinism, and dependency-isolation regression.
- `decisions/ADR-006-pure-target-hit-core.md`, this P3 acceptance contract, and
  a focused repository CI source scan own the no-publication/no-expansion gate.

### Routes

- None. `GET /v1/calls/{id}/outcomes` remains the unchanged read-only P1 audit
  endpoint and does not trigger this primitive.

### Verification

- Focused `TargetHitCalculatorGoldenTest`: PASS, 31/31 tests.
- Full API Maven verification: PASS, 140/140 tests with zero failures, errors,
  or skips. PostgreSQL 17.10 Testcontainers migration coverage executed 4/4
  tests with zero skips.
- `docker compose --env-file .env.example config --quiet`: PASS.
- Focused target-hit repository gate: PASS. It proves the exact calculation
  leaf and source-local golden file, no reverse production wiring, unchanged
  model-only/all-null outcome evidence, and no schema, fixture, manifest,
  OpenAPI, Flyway, persistence, or web-source expansion.
- All embedded workflow Python blocks passed syntax and execution, 12/12;
  canonical validation covered 14 schemas and 32 fixture records.
- SnakeYAML parsing and `git diff --check`: PASS.
- Independent final review: blocker 0, HIGH 0, known false-positive 0.

### Deferred boundary

- A versioned horizon/session/window policy and a reproducible methodology
  definition must exist before the primitive receives runtime inputs or creates
  an outcome.
- Target error, return/directional calculations, MFE/MAE, alpha/sector alpha,
  outcome completeness, scheduling, persistence orchestration, and leaderboard
  aggregates remain later P3 slices. Historical bars and licensed providers
  remain in their owning later phases.

## P3 — Explicit-anchor session-offset resolver

Status: complete; schedule mechanics only, with no named horizon, market
observation, calculated outcome, persistence, API, or web publication, while
broader P3 scoring remains open

### Scope

- Add a pure resolver over a caller-supplied anchor session, positive subsequent
  session count, explicit ordered open/close catalog, and evaluation as-of.
- Return schedule-only ready, pending, or incomplete evidence without inferring
  a date, venue, timezone, holiday, session, price, bar, or completeness state.
- Exercise all mechanics with source-local Java golden schedules while leaving
  canonical methodologies, all-null outcomes, fixtures, and product surfaces
  unchanged.

### Contract and phase decisions

- `ADR-007` and `quality/P3_ACCEPTANCE.md` own
  `EXPLICIT_ANCHOR_SESSION_COUNT_V1`: anchor excluded, next N sessions included,
  Nth subsequent session as endpoint, and close-at-or-before as-of as ready.
- The caller owns anchor correctness. `SessionOffsetRequest` has no call,
  analyst-call revision, event time, `OutcomeHorizon`, market observation, provenance,
  catalog capture time, provider, or `Clock`.
- An existing future endpoint is pending with `ENDPOINT_NOT_REACHED`; a missing
  anchor or endpoint is incomplete with `ANCHOR_SESSION_MISSING` or
  `ENDPOINT_SESSION_MISSING`. Ready remains schedule evidence only.
- Results preserve exact request identity in nested
  `ResolutionContext(policyVersion, calendarId, catalogRevision,
  anchorSessionId, sessionCount, evaluationAsOf)`. A resolved window adds the
  anchor, immutable selected sessions, and endpoint; an incomplete result keeps
  context but invents no session.
- The code enum and ADR version only this isolated mechanic. Existing
  methodology hashes remain `MODEL_ONLY`; canonical methodology hashing and
  input fingerprinting wait for the named-horizon and evidence contract.

### Module structure

- `apps/api/src/main/java/.../domain/outcome/horizon` owns exactly
  `SessionOffsetPolicyVersion.java`, `TradingSession.java`,
  `TradingSessionCatalog.java`, `SessionOffsetRequest.java`,
  `SessionOffsetResolution.java`, and `SessionOffsetResolver.java`.
- `SessionOffsetResolverGoldenTest.java` in the matching test package owns
  offset/window, readiness, coverage, explicit irregular-session, immutability,
  precision, invalid-catalog, and source-boundary regressions.
- `ADR-007`, the P3 acceptance contract, and focused repository CI own the
  code-only version and no-inference/no-publication boundary.

### Routes

- None. Existing outcome audit reads remain unchanged and do not call this
  resolver.

### Verification

- Focused `SessionOffsetResolverGoldenTest`: PASS, 41/41 tests.
- Full API Maven verification: PASS, 181/181 tests with zero failures, errors,
  or skips. PostgreSQL 17 migration coverage executed 4/4 Testcontainers tests
  with zero skips.
- `docker compose --env-file .env.example config --quiet`: PASS.
- Focused session-offset repository gate: PASS. It proves the exact recursive
  six-file leaf and golden test, reverse production isolation, explicit
  count-five/Saturday/omitted-date and Locale/TimeZone replay evidence,
  unchanged model-only/all-null outcomes, and no named-horizon, schema,
  fixture, manifest, OpenAPI, Flyway, persistence, provider, or web-source
  expansion.
- All embedded workflow Python blocks passed syntax and execution, 13/13;
  canonical validation covered 14 schemas and 32 fixture records.
- SnakeYAML parsing and `git diff --check`: PASS. Generated and unexpected-file
  audit was clean.
- Independent correctness review: blocker 0, HIGH 0, known false-positive 0.

### Deferred boundary

- Named horizon counts, event/correction anchoring, calendar source/revision
  evidence, price/bar/window rules, retry/grace, corporate actions, currency,
  methodology definition serialization/hash, and input fingerprints remain
  later P3 contracts.
- Market providers, historical bars, schedulers, persistence orchestration,
  calculated outcomes, aggregate rankings, API expansion, and UI publication
  remain outside this slice.

## P3 — Policy-neutral event/session relation

Status: complete for this vertical slice; the broader P3 scoring phase remains
open, with no anchor selection, named horizon, market observation, outcome,
persistence, API, or web publication

### Scope

- Classify one caller-supplied event time against the existing explicit ordered
  open/close catalog while preserving exact boundaries, gaps, and coverage.
- Return no anchor, session offset, horizon endpoint, readiness, recommendation,
  observation, or calculated state.
- Use source-local Java golden schedules only; canonical methodologies,
  outcomes, fixtures, and product surfaces remain unchanged.

### Contract and phase decisions

- `ADR-008` and `quality/P3_ACCEPTANCE.md` own
  `EXPLICIT_SESSION_BOUNDARY_RELATION_V1` and the exact eight-result union.
- `EventSessionRelationRequest` contains only policy version, event time, and
  catalog. The caller owns whether the instant represents an original call,
  correction, or another reviewed basis.
- Relation context echoes policy, calendar label/revision, and event time only.
  It makes no claim that the catalog was point-in-time available, observed,
  complete, licensed, or source-traceable.
- Touching close/open instants preserve both sessions; gaps are not labelled as
  pre-market, after-hours, weekend, or holiday. No relation selects an anchor.
- Relation-record constructors enforce only their local component inequalities;
  the classifier alone guarantees catalog membership, first/last position,
  adjacency, and touching precedence. Direct construction proves neither
  catalog provenance nor runtime eligibility.
- The code-only version/ADR do not reinterpret either existing `MODEL_ONLY`
  methodology hash or create an input fingerprint.

### Module structure

- The existing API horizon package additively owns
  `EventSessionRelationPolicyVersion.java`,
  `EventSessionRelationRequest.java`, `EventSessionRelation.java`, and
  `EventSessionRelationClassifier.java`; the completed six session-offset files
  remain unchanged and separately guarded.
- `EventSessionRelationClassifierGoldenTest.java` in the matching test package
  owns boundary, gap, touching-session, explicit irregular-session, precision,
  immutability, closed-shape, and locale/timezone regressions.
- `ADR-008`, the P3 acceptance contract, and focused additive CI own the
  no-anchor/no-publication boundary.

### Routes

- None. Existing call/outcome routes remain unchanged and never invoke this
  classifier.

### Verification

- Focused `EventSessionRelationClassifierGoldenTest`: PASS, 42/42 tests.
- Full API `mvnw -B -ntp verify`: BUILD SUCCESS, 223/223 tests with zero
  failures, errors, or skips; PostgreSQL 17.10 Testcontainers migration tests
  executed 4/4 with zero skips.
- `docker compose --env-file .env.example config --quiet`: PASS.
- Preserved session-offset plus additive relation focused CI: 2/2 PASS. All
  embedded workflow Python blocks passed syntax and execution 14/14, validating
  14 schemas and 32 canonical fixture records.
- SnakeYAML workflow parsing, `git diff --check`, and root temporary-artifact
  audit: PASS/clean.
- No web verification was required: this slice changes no web source or route,
  and the focused source-boundary gate rejects web expansion.
- Independent final review: blocker 0, HIGH 0, known false-positive 0.

### Deferred boundary

- Relation-to-anchor rules, original-versus-correction selection, calendar
  revision point-in-time evidence, emergency closures, named horizon mapping,
  bar/window rules, retry/grace, methodology serialization/hash, and input
  fingerprinting remain later P3 contracts.
- Providers, historical observations, schedulers, persistence orchestration,
  calculated outcomes, aggregates, API changes, and UI publication remain
  outside this slice.

## P2 — Korean-default bilingual product UI and editorial visual system

Status: complete; existing routes and canonical evidence remain the product
boundary, and broader P2/P3/P5/P8 work remains open

### Scope

- Add a server-resolved Korean-default presentation locale with exact Korean
  and English application catalogs across existing product routes and states.
- Add a keyboard-operable global locale control backed by one HTTP-only cookie,
  with raw SSR and revisit behavior that never depends on hydration, browser
  inference, local storage, or a remote translation service.
- Localize the common root not-found boundary without attaching DEMO/provider
  state or inferred evidence, while retaining exact dashboard and calls links.
- Refresh the shared visual system toward the supplied white editorial
  financial-terminal references: thin neutral rules, near-square controls,
  compact mono evidence, tabular numerics, restrained semantic green/red, and
  deliberate whitespace at 1440, 1280, and 390 pixels.
- Preserve every canonical fixture/provider/API value, route/query/hash,
  ordering rule, DEMO/unavailable/null semantic, and existing backend contract.

### Contract and phase decisions

- Presentation locale is exactly `ko | en`; `ko` is the deterministic default
  for a missing or unsupported resolved cookie value. No `Accept-Language`,
  browser locale, geography, timezone, URL parameter, or host setting is used.
- `wsr_locale` is server owned and HTTP-only with `Path=/`, `SameSite=Lax`,
  `Max-Age=31536000`, and production-only `Secure`. The server action accepts
  only exact `ko` or `en`, rejects invalid input, and preserves the current URL.
- `html[lang]`, metadata, navigation, page/state copy, and accessible names use
  the server-resolved catalog before hydration. A selected locale persists on
  revisit and direct navigation without `localStorage`, `sessionStorage`, or a
  translation request.
- Locale buttons retain stable `한국어`/`English` autonyms, exact per-option
  `lang`, a minimum 24-pixel height, pending disable/announcement semantics,
  and focus restoration to the selected option after the server locale changes.
- The light system locks muted text to `#70706c`; positive/negative map metric
  text uses semantic tokens, while neutral map and colored treemap surfaces
  explicitly restore dark text for contrast without changing stored values.
- Translation is presentation-only. IDs, enums, hashes, versions, data modes,
  reason/status tokens, tickers, entity and source identities, exact fixture
  disclaimers, URLs/source paths, UTC evidence, numerics, nulls/`NA`, and order
  remain canonical.
- This slice adds no locale route, API/OpenAPI surface, canonical schema or
  fixture, manifest member, Flyway migration, persistence, provider connection,
  quote, screening result, ranking, or calculated outcome.

### Module structure

- `apps/web/src/lib/i18n/config.ts`, `messages.ts`, and `server.ts` own the
  closed locale set, typed application catalogs, exact cookie constants, and
  server resolution boundary.
- `apps/web/src/app/actions/locale.ts` owns the validated server-side cookie
  mutation. `locale-provider.tsx`, `locale-switcher.tsx`, the root layout, and
  `site-header.tsx` own client catalog access, the semantic switch, document
  language, metadata, and localized global navigation.
- The common `apps/web/src/app/not-found.tsx` reads the server locale and common
  catalog; its colocated test owns Korean/English, mode-neutral, and link
  regressions.
- Existing route-local view/state files own their product copy. Providers,
  canonical adapters, domain calculations, fixtures, and APIs remain
  locale-independent.
- Shared tokens and `globals.css` own the white editorial evidence system;
  colocated unit/page tests and responsive Playwright own locale, SSR, revisit,
  focus, containment, and canonical-value regressions.
- `quality/P2_ACCEPTANCE.md` and the focused repository CI gate own the exact
  cookie/SSR/source/design/no-contract-expansion boundary.

### Routes

- No route is added or renamed. Every existing route remains directly
  addressable in Korean-default and English presentation.
- Unknown routes use the common locale-aware mode-neutral not-found boundary;
  they do not become evidence or a new product route.

### Verification

- Web ESLint and `tsc --noEmit`: PASS. Vitest passed 32 files and 316 tests.
- Next 16.2.11 production build: PASS, including compilation, TypeScript, and
  12/12 static-generation tasks. The route table contained 11 dynamic routes,
  including the locale-aware `/_not-found` boundary.
- Targeted bilingual Playwright passed 12/12. The full suite passed 63/63 at
  1440, 1280, and 390 pixels, covering raw Korean-default/invalid/explicit-ko/
  English SSR, cookie persistence, keyboard switching and focus restoration,
  direct/revisited navigation, common unknown routes, containment, overflow,
  external-request isolation, and runtime errors.
- The focused bilingual/design and localized Screener repository gates passed.
  All 14 embedded workflow Python blocks passed syntax; SnakeYAML parsing and
  `git diff --check` passed.
- The production `next-env.d.ts` import had no diff. Playwright report and test-
  result directories were absent, and ports 3000 and 3011 had no listeners.
- No API, schema, fixture, manifest, OpenAPI, Flyway, or provider boundary was
  changed; the focused repository gate locks those unchanged sets, so no API
  execution result is claimed for this presentation-only slice.

### Deferred boundary

- Additional locales, account-synced preferences, locale-prefixed routes,
  machine/remote translation, translated canonical evidence or API payloads,
  and provider-supplied localization require separate contracts.
- Live/current data stays P5-owned, deterministic ranking/scoring stays
  P3-owned, and populated historical screening stays P8-owned. The redesign
  cannot fabricate or visually promote missing, model-only, unavailable, or
  fixture evidence.

## P2 — Coherent call-detail audit API consumer

Status: complete for this consumer vertical slice; broader P2, P3 lifecycle and
scoring, P5 licensed provider publication, and P8 history remain open

### Scope

- Connect the existing `/calls/[id]` server route to the existing Spring detail,
  context, and revision reads through one coherent call-audit provider.
- Preserve an explicit whole-aggregate fixture mode for deterministic offline
  development while making the documented local two-process stack use the real
  private API transport with no runtime fallback or mixed-source page.
- Render ordered append-only correction/cancellation evidence without mutating
  the original event or projecting a current/effective stance, outcome,
  accuracy, rank, confidence, or advice.
- Keep Korean-default and English presentation, raw canonical evidence,
  responsive containment, and all existing backend/canonical contracts intact.

### Contract and phase decisions

- `CALL_AUDIT_PROVIDER` accepts exact `fixture | api`; missing input defaults to
  fixture in code, while the checked-in local example and documented web launch
  explicitly select `api`. API mode requires private `API_BASE_URL`; neither
  configuration nor transport is exposed as `NEXT_PUBLIC_*`.
- API mode establishes detail existence first, maps only its exact 404 to
  not-found, then reads context and revisions together. Redirects, non-200
  dependent responses, non-JSON/malformed/invalid payloads, and cross-surface
  divergence fail the complete page without partial data or fixture fallback.
- The aggregate accepts exactly `DEMO` throughout this P2 slice. The Spring API
  remains backed by synthetic repository fixtures; HTTP delivery is not a
  licensed, live, delayed, or EOD provider-publication claim.
- Runtime adaptation is closed and validates each surface's own joins,
  chronology, nullability, numeric bounds, and provenance. Separate source and
  provenance families are preserved without inventing equality. Revision
  identity is `(provider, providerEventId)`; event time alone must be
  nondecreasing across the append-only lineage.
- Revision event/processing/capture instants render as raw ISO evidence,
  including microseconds. Corrected targets render as raw numeric text with a
  separate currency field. Adjacent Korean and English copy states that the
  visible base `ACTIVE` value is immutable original-event status, not a current
  or effective stance, including beside terminal cancellation.
- Server confinement uses `.server.ts` modules, explicit browser-runtime guards,
  private environment access only in the factory/transport, and a reverse
  static/dynamic/CommonJS import-graph CI fence. No new `server-only` package,
  ambient alias, browser transport, API endpoint, schema, fixture, manifest
  member, Flyway migration, persistence model, or Spring call-audit wrapper was
  added.

### Module structure

- `apps/web/src/lib/providers/call-audit-provider.ts` owns the closed
  detail/context/revisions aggregate and canonical revision presentation types.
- `call-audit-adapter.ts` owns closed response adaptation and compatible
  DEMO/point-in-time/lineage validation. `fixture-call-audit-provider.ts` owns
  the complete offline aggregate.
- `api-call-audit-provider.server.ts` owns the exact private GET transport;
  `call-audit-provider.server.ts` owns exact provider selection and is the only
  API-transport importer. The existing detail page is its only production
  consumer.
- The call-detail page, typed Korean/English call catalog, scoped shared styles,
  provider/page unit tests, and `apps/web/e2e/call-revisions.spec.ts` own raw
  audit presentation and responsive browser regressions.
- `.env.example`, this README, `quality/P2_ACCEPTANCE.md`, and the focused
  repository plus dedicated cross-stack CI jobs own configuration,
  reproducibility, source isolation, and integration evidence.

### Routes

- No product or API route was added or renamed. `/calls/[id]` now obtains its
  complete detail audit from one provider; `/calls` and dashboard provider
  boundaries remain unchanged.
- API mode consumes the existing `GET /v1/calls/{id}`,
  `GET /v1/calls/{id}/context`, and `GET /v1/calls/{id}/revisions` paths from one
  private origin.

### Verification

- Full web ESLint and `tsc --noEmit --incremental false`: PASS. Vitest passed
  36 files and 388 tests.
- Next 16.2.11 production build: PASS, including 12/12 generated page-data
  steps and 11 dynamic routes with `/calls/[id]`.
- Targeted call-revision Playwright passed 3/3 at 1440, 1280, and 390 pixels.
  The final retry-free full browser suite passed 66/66 across all three widths.
  The stabilized sequential keyboard-focus regression separately repeated
  18/18 with zero retries.
- Full API Maven verification: PASS, 223/223 tests with zero failures, errors,
  or skips; PostgreSQL 17.10 Testcontainers migration coverage executed 4/4
  with zero skips.
- Local cross-stack API mode: PASS, 1/1 Playwright test through PostgreSQL,
  packaged Spring, and Next. The unbuffered Tomcat access log recorded HTTP 200
  for exact detail/context/revisions reads of both `demo-call-002` and known-
  empty-lineage `demo-call-001`, proving all six real Spring paths were used.
- `docker compose --env-file .env.example config --quiet`, the focused coherent
  call-audit repository gate, SnakeYAML workflow parsing, and
  `git diff --check`: PASS. All 17/17 embedded workflow Python blocks passed
  syntax and execution, including canonical validation of 14 schemas and 32
  fixture records plus the six-path observed integration log. The production
  `next-env.d.ts` import had no diff, generated Playwright report/result
  directories were absent, and ports 3000, 3011, 3120, 3133, 8080, and 5432
  were idle after verification.
- Independent final contract/source/UI review: blocker 0, HIGH 0.

### Deferred boundary

- Licensed analyst/market-provider ingestion, entitlements, rights, freshness,
  health, retry policy, and real live/delayed/EOD publication remain P5 work.
- Effective lifecycle/basis selection and deterministic outcome calculations
  remain P3 work; historical materialization and screening remain P8 work.
- Authentication, user-specific visibility, cross-endpoint snapshot tokens,
  streaming/polling, writes, source-document expansion, and list/dashboard API
  migration require separate reviewed contracts.

## P2 — Coherent analyst-call list API consumer

Status: complete for the `/calls` list consumer vertical slice; broader P2,
P3 lifecycle/scoring, P5 licensed provider publication, and P8 historical
materialization remain open

### Scope

- Connected the existing server-rendered `/calls` route to the existing Spring
  `GET /v1/calls` read through a page-scoped provider without changing the API,
  canonical fixtures, persistence, or product routes.
- Shared the existing exact fixture/API selector with call detail so the
  documented local stack does not silently use different source modes between
  list and detail navigation.
- Preserved the existing filter, pagination, bilingual, evidence, empty, error,
  keyboard, and responsive behavior while removing the list route's dependency
  on fixture-only dataset metadata in API mode.

### Contract and phase decisions

- API mode owns one private no-store JSON GET and always requests exact DEMO as
  the P2 phase boundary. It has no client transport, retry, second metadata
  request, or API-to-fixture fallback.
- Spring's existing list response is only `{items,page}`. Dataset as-of, source,
  disclaimer, and complete facets therefore remain explicitly NOT_EXPOSED in
  API mode rather than being copied from fixtures or inferred from one page.
- Returned-page capture/provenance is independently labelled page-only evidence.
  It cannot become a dataset timestamp, freshness, coverage, or provider-health
  claim. In fixture mode only, the AVAILABLE dataset `asOf` must bound every
  returned call capture; that coherence check never creates API metadata.
- Exact opaque-ID inputs replace fixture facet selects; ticker retains its API
  semantics, direction/status remain closed enums, and the list mode is fixed to
  DEMO. Date inputs map to real UTC calendar-day bounds with an exclusive through
  date. The typed provider separately preserves Spring-compatible request
  instants through nanosecond precision and Java's UTC-offset boundary, while
  response evidence remains canonical UTC `Z` at no more than microsecond
  precision.
- Runtime adaptation preserves the raw API order and explicit nulls while
  validating closed response keys, joins, chronology, DEMO parity, deterministic
  sort/tie order, page metadata, and empty/out-of-range semantics.

### Module structure

- `call-list-provider.ts` owns the page-scoped snapshot and honest dataset/page
  evidence union. `call-list-query.ts` closes raw route parameters, and
  `call-list-adapter.ts` validates the exact Spring page, filters, ordering,
  identities, joins, modes, chronology, and evidence projection.
- `fixture-call-list-provider.ts`, `api-call-list-provider.server.ts`, and
  `call-list-provider.server.ts` provide explicit whole-page fixture/API modes,
  the private one-read transport, and the shared exact source selector. The API
  graph cannot reach fixture data and the browser graph cannot reach either
  server-only module.
- The existing `/calls` page, typed Korean/English messages, contained evidence
  styles, provider/query/page unit tests, and `call-list-api.spec.ts` implement
  and verify the product surface. Related analyst, institution, and S&P history
  browser checks now target the exact opaque-ID inputs.
- `quality/P2_ACCEPTANCE.md`, `.github/workflows/ci.yml`, README, and this log for
  the source boundary, local runtime, and cross-stack proof.

### Routes

- No route was added or renamed. `/calls` consumes the existing
  `GET /v1/calls`; `/calls/[id]` retains the existing coherent detail,
  context, and revision aggregate.

### Verification

- Full web ESLint and `tsc --noEmit --incremental false`: PASS. The focused
  eight-file call-list suite passed 162/162 tests, and the full Vitest suite
  passed 515/515 tests across 41 files.
- Next 16.2.11 production build: PASS, including 12/12 page-data generation and
  all 11 dynamic routes.
- Targeted call-list Playwright passed 3/3 at 1440, 1280, and 390 pixels; the
  six related legacy route checks passed 6/6. The final retry-free full browser
  suite passed 69/69 across all three widths with the sequential Tab path,
  long returned-page provenance containment, exact URLs, and zero browser calls
  to the API origin.
- Full API Maven verification passed 223/223 tests with zero failures, errors,
  or skips. PostgreSQL 17.10 migrations passed 4/4 integration tests with zero
  skips, and Compose configuration validation passed.
- The local PostgreSQL -> packaged Spring -> Next API-mode run passed 2/2
  Playwright tests. Exact Tomcat access-log line membership passed 11/11: five
  query-bearing list reads plus the six detail/context/revision reads. With the
  configured `%m %U%q %s` pattern, Tomcat's queryless `%q` field is preserved as
  `-`; the proof therefore checks the observed full lines rather than a partial
  path substring.
- All 18 embedded repository Python blocks passed syntax and execution against
  14 schemas and 32 canonical records. SnakeYAML 2.5 parsing and
  `git diff --check` passed; protected backend/canonical sets and the generated
  `next-env.d.ts` remained clean.
- Independent read-only review closed with zero blockers, zero HIGH findings,
  and zero known false-positive gates.

### Deferred boundary

- Dataset metadata/facet APIs, dashboard migration, snapshot tokens, live or
  licensed ingestion, freshness/health, polling/streaming, lifecycle projection,
  scoring, saved filters, and exports remain separate reviewed work.

## P2 — Coherent call-outcome audit API consumer

Status: complete for this vertical slice; the broader P2 phase remains open

### Scope

- Extend the existing page-scoped call audit from exact detail/context/revisions
  to exact detail/context/revisions/outcomes using the already published Spring
  `GET /v1/calls/{id}/outcomes` read.
- Keep fixture and API as explicit whole-aggregate modes under the shared exact
  `CALL_AUDIT_PROVIDER` selector. The call-detail page must not add a second
  provider read, raw fixture import, alternate origin, or fallback.
- Replace the hard-coded outcome `NA` summary with an append-only evidence view
  of every returned canonical outcome field and an explicit known-empty state.
  Do not calculate, aggregate, choose a latest result, or project lifecycle.

### Locked contract decisions

- API mode remains private and server-only. It establishes detail existence
  first, then reads context, revisions, and outcomes from the same normalized
  `API_BASE_URL` with exact JSON/no-store/redirect-error GET semantics. Only a
  detail 404 is not-found; every dependent failure rejects the whole audit.
- An outcome retains the exact 31-field canonical shape, source order, explicit
  nulls, hashes, fingerprints, opaque IDs, canonical tokens, and UTC instants.
  The adapter must not reorder or collapse the response. Fixture mode sorts a
  copied per-call group into the existing API order before adaptation without
  mutating the canonical document; API payloads are never sorted by the web.
- The P2 publication guard accepts only exact DEMO
  `PENDING/HORIZON_NOT_REACHED` and `INCOMPLETE/HORIZON_DATA_MISSING` records,
  `dataComplete=false`, and JSON null for all ten metric/result fields. Even a
  schema-valid calculated/excluded or non-DEMO record remains outside this
  consumer and fails closed.
- Outcome IDs and natural input identities remain unique. Lineages are scoped
  by call, nullable basis revision, horizon, methodology ID, and methodology
  version; sequence and supersession are contiguous and each lineage's event,
  processing, and capture times do not move backwards.
- Call, snapshot, and nullable correction-basis joins retain the P1
  point-in-time bounds. `cancellationRevisionId` remains exact null; cancellation
  relationship semantics stay deferred. Separate call/source/context/revision/
  outcome provenance values are preserved without inventing equality.
- `/calls/demo-call-001` is the populated four-record golden and
  `/calls/demo-call-002` is the known-empty outcome golden beside its existing
  terminal revision. The UI must never infer an excluded outcome from that
  cancellation or relabel the immutable base call.

### Module and file boundary

- Existing `call-audit-provider.ts`, `call-audit-adapter.ts`,
  `api-call-audit-provider.server.ts`, and `fixture-call-audit-provider.ts` own
  the extended aggregate. No second outcome provider, factory, environment
  selector, or browser transport was added.
- The existing call-detail page, typed Korean/English messages, contained dense
  evidence styles, focused adapter/API/fixture/factory/page tests, and the new
  `call-outcomes.spec.ts` own the product behavior.
- `quality/P2_ACCEPTANCE.md`, `.github/workflows/ci.yml`, README, and this log
  own the delivered merge gate and phase/deferred boundary.
- OpenAPI, canonical schemas/fixtures/manifest, Spring production source,
  Flyway, P1 domain contracts, and P3 pure scoring source remain unchanged.

### Routes

- No product or API route is added or renamed. The existing `/calls/[id]` page
  consumes the existing `/v1/calls/{id}/outcomes` subresource through its one
  coherent audit provider.

### Verification

- Web ESLint and TypeScript passed. Vitest passed 42 files and 569 tests. The
  Next production build passed with 12/12 static-generation work items and 11
  dynamic routes.
- Retry-free Playwright passed 72/72 tests across 1440, 1280, and 390 pixels;
  the focused outcome-plus-revision matrix passed 6/6. Long hashes and
  fingerprints remained untruncated, sequential keyboard focus reached the
  outcome section, populated and known-empty states remained honest, and the
  browser made no request to the private API origin.
- The real PostgreSQL 17 -> packaged Spring -> Next API-mode integration passed
  3/3 browser tests and matched all 13 exact Tomcat HTTP-200 access-log lines:
  five list queries plus eight detail/context/revision/outcome reads. Queryless
  `%q` retained Tomcat's observed `-` placeholder.
- Maven verification passed 223/223 tests with zero failures, errors, or skips.
  PostgreSQL 17.10 migration coverage passed 4/4 Testcontainers integration
  tests with zero skips. Compose configuration passed.
- All 18 embedded workflow Python blocks passed syntax and execution. Canonical
  validation covered 14 JSON Schemas and 32 fixture records. SnakeYAML parsing
  and `git diff --check` passed; generated reports were removed and
  `next-env.d.ts` retained its production import.
- In-app browser QA confirmed Korean default SSR, desktop and 390-pixel
  containment without overflow or canonical-token truncation, the explicit
  outcome empty state, and zero console warnings or errors. Independent final
  review found zero blockers, zero high-severity issues, and zero known
  false-positive gates.

### Deferred boundary

- Effective basis and cancellation eligibility, named horizons, calculated
  metrics, latest/current projection, scoring, confidence, leaderboards, and
  persistence remain P3 work.
- Dashboard/API migration and dataset catalogs need separate contracts. Live or
  licensed providers, rights, freshness/health, polling/streaming, and non-DEMO
  publication remain P5 work. This slice cannot create an observed performance
  claim from the current synthetic null-only audit records, and the shared API
  view cannot infer a methodology activation status that the endpoint omits.

## P3 — Pure directional-win comparison core

Status: complete for this pure comparison slice; no calculation result is
published or persisted, and broader P3 scoring remains open

### Scope

- Add a pure comparison leaf over an already interpreted bullish/bearish side
  and one nullable, caller-supplied signed asset return.
- Lock strict bullish `> 0` and bearish `< 0` semantics, with exact zero false
  for both sides and null preserved as explicit unavailable evidence.
- Validate every provided return as exactly representable by signed
  `NUMERIC(38,12)` without rounding, rescaling, parsing, arithmetic, or binary
  floating-point conversion.
- Add source-local Java golden vectors only. Add no return calculation, price,
  horizon, calendar, call-direction mapping, provider, scheduler, persistence,
  schema, fixture, manifest member, OpenAPI/API path, Flyway migration, or web
  surface.

### Locked contract decisions

- ADR-009 and `quality/P3_ACCEPTANCE.md` own the exact input, side, comparison,
  signed-decimal, unavailable, purity, and later-integration boundaries.
- The side enum is exactly `BULLISH` and `BEARISH`. This primitive does not
  accept `CallDirection`, reduce strong directions, or decide neutral
  eligibility.
- `DirectionalWinResult` is a closed `Available(boolean directionalWin)` or
  `Unavailable(ASSET_RETURN_MISSING)` result. Missing is not false or zero;
  structurally invalid supplied evidence fails closed.
- The caller owns the meaning and point-in-time validity of `assetReturn`.
  Existing `MODEL_ONLY` methodology hashes are not reinterpreted and the
  canonical outcome archive keeps every metric/result field null.

### Module and file boundary

- The existing API calculation package is extended append-only with
  `DirectionalWinSide.java`, `DirectionalWinInput.java`,
  `DirectionalWinResult.java`, and `DirectionalWinCalculator.java`; the four
  target-hit files retain their existing contract.
- `DirectionalWinCalculatorGoldenTest.java` owns source-local signed-boundary,
  strict-sign, zero, missing, invalid-input, and determinism coverage beside the
  unchanged target-hit golden test. The CI gate owns source isolation and
  reverse-wiring rejection.
- ADR-009, the P3 acceptance contract, README, this log, and the extended
  calculation CI gate own the no-runtime/no-publication boundary.

### Routes

- None. Existing call/outcome routes remain unchanged and do not invoke either
  calculation primitive.

### Verification

- Focused `DirectionalWinCalculatorGoldenTest`: PASS, 28/28 tests.
- Full API Maven verification: PASS, 251/251 tests with zero failures, errors,
  or skips. `PostgreSqlMigrationTest` executed 4/4 PostgreSQL Testcontainers
  tests with zero skips.
- `docker compose --env-file .env.example config --quiet`: PASS.
- All 18 embedded workflow Python blocks compiled. Blocks 1–17 executed 17/17;
  the canonical contract block validated 14 schemas and 32 fixture records,
  and the combined pure-calculation guard passed. Block 18 is the existing
  cross-stack access-log verifier and was intentionally not executed because
  this disconnected API-domain slice launched no Spring or web service.
- SnakeYAML 2.5 workflow parsing and `git diff --check`: PASS. No web or
  cross-stack test is reported as executed for this slice.
- Independent final review found zero blockers and zero high-severity issues.

### Deferred boundary

- Direction reduction, neutral eligibility, horizon return calculation,
  session/window selection, endpoint prices, corporate actions, currency,
  methodology serialization/hash, input fingerprinting, persistence
  orchestration, calculated outcomes, aggregates, and UI publication remain
  later reviewed P3 work.
- Historical bars and live/licensed market providers remain in their later
  owning phases; this slice requires no provider credential or network access.

## P3 — Strict session-close named-horizon and forecast-basis policy

Status: complete for this disconnected schedule-policy slice; broader P3 scoring
remains open, with no observed price, return, calculated outcome, provider,
persistence, API, or product publication

### Scope

- Lock the approved `D1/W1/M1/M3/M6/Y1` session counts as exactly
  `1/5/21/63/126/252` over one caller-supplied explicit session catalog.
- Select the first N catalog entries whose close is strictly after the supplied
  basis event time, preserving catalog order and returning the Nth as the
  schedule endpoint.
- Model an original and each already-validated correction as independent basis
  lineages. A correction uses its own revision ID and event time; the original
  basis remains separate and immutable. Cancellation is not a basis.
- Add source-local Java golden schedules only. Add no evaluation-as-of,
  readiness, observation, endpoint price, return calculation, calendar
  provider, account/key, scheduler, schema, fixture, manifest member, OpenAPI
  path, Flyway migration, database row, API behavior, or web surface.

### Locked contract decisions

- ADR-010 and `quality/P3_ACCEPTANCE.md` own the exact basis, horizon-count,
  strict-close boundary, missing-coverage, canonical-definition, purity, and
  deferred-integration contracts.
- `OutcomeBasis` is sealed as `Original(callId,eventTime)` and
  `Correction(callId,basisRevisionId,eventTime)`. The caller attests that a
  correction belongs to the call and is valid; this leaf loads no aggregate and
  performs no revision supersession or cancellation decision.
- `STRICTLY_AFTER_BASIS_EVENT_SESSION_CLOSE_V1` uses only
  `session.closesAt > basis.eventTime`. Before/open/interior events may select
  the current/first session; exact close skips it; touching selects the opening
  session; gaps select the following supplied session. No local date, timezone,
  weekday, holiday, duration, or missing session is inferred.
- The exact single-line canonical definition is 633 ASCII/UTF-8 bytes with
  fixed lowercase SHA-256
  `550087efe7ddf2ba31974c89c2740ab79df986eefef48919c32c56a3232f8dc1`.
  Definition bytes are defensively returned and every resolution context echoes
  the digest. This identity is not either existing model-only scoring-
  methodology hash.
- Results are only `Resolved(window)` or
  `Incomplete(FIRST_ELIGIBLE_SESSION_MISSING|HORIZON_ENDPOINT_SESSION_MISSING)`.
  Resolved means endpoint schedule identification, not readiness, a known bar,
  `CALCULATED`, `dataComplete`, or observed performance.
- Public context/window constructors validate only locally decidable policy
  hash/count, strict-first-close, size, uniqueness/order, and endpoint-last
  invariants. They cannot attest membership or first-N selection against a
  catalog they do not carry, and a directly built incomplete record cannot
  attest its reason. Those claims belong only to the resolver.

### Module and file boundary

- The existing API horizon package is extended append-only with
  `OutcomeBasis.java`, `SessionCloseHorizonPolicyVersion.java`,
  `SessionCloseHorizonRequest.java`, `SessionCloseHorizonResolution.java`, and
  `SessionCloseHorizonResolver.java`. Existing session-offset and event/session
  relation contracts remain unchanged.
- `SessionCloseHorizonResolverGoldenTest.java` owns source-local definition,
  count, temporal-boundary, original/correction, missing-coverage, invalid-input,
  immutable-window, and default-environment replay coverage.
- ADR-010, the P3 acceptance contract, README, this log, and a mutation-sensitive
  repository CI extension own exact-shape, reverse-wiring, unchanged-canonical-
  surface, and no-provider boundaries.

### Routes

- None. The policy is not invoked by a controller, application service,
  repository, fixture adapter, API response, scheduler, or web route.

### Verification

- Focused `SessionCloseHorizonResolverGoldenTest`: PASS, 47/47 tests.
- Full API Maven verification: PASS, 20 Surefire suites and 298/298 tests with
  zero failures, errors, or skips; the application JAR was packaged.
- PostgreSQL Testcontainers migration coverage executed 4/4 tests with zero
  skips. Compose environment-file validation also passed.
- The new strict-close contract guard passed against the final 633-byte policy
  definition and fixed digest. Both pre-existing horizon guards also passed
  after their exact source/test file sets admitted the new isolated leaf.
- All 19 embedded workflow Python blocks compiled. Repository-local blocks
  1–18 executed 18/18, including 14 schema/32 fixture-record validation and both
  protected P2 consumer baselines. Block 19 is the cross-stack log verifier and
  was intentionally not run because no Spring/Next services were launched; no
  cross-stack result is claimed.
- SnakeYAML 2.5 parsed the workflow directly in Java source-file mode, and
  `git diff --check` passed. Final production/test/CI review reported zero
  blocker and zero high-severity findings.
- Web, cross-stack, and browser verification are not required unless this slice
  changes a web/runtime surface; the locked boundary rejects such a change.

### Deferred boundary

- Point-in-time calendar provenance, asset/venue association, observed endpoint
  closes, corporate-action and currency policy, asset-return calculation,
  target error, target-hit/directional-win orchestration, cancellation
  eligibility, methodology activation, input fingerprinting, persistence,
  aggregates, and UI publication remain later reviewed P3 work.
- Real calendar and market providers, display/storage/derived-data rights,
  freshness/health, credentials, and non-DEMO publication remain P5 work. This
  disconnected slice requires no API key, account, paid plan, domain, license,
  or network access.

## P3 — Call-direction polarity policy

Status: complete for this disconnected direction-reduction policy slice;
broader P3 scoring remains open, with no calculator invocation, calculated
outcome, provider, persistence, API, or product publication

### Scope

- Lock the product-owner-approved canonical mapping exactly:
  `STRONG_BULLISH→BULLISH`, `BULLISH→BULLISH`,
  `NEUTRAL→NON_DIRECTIONAL`, `BEARISH→BEARISH`, and
  `STRONG_BEARISH→BEARISH`.
- Preserve the original five-value `CallDirection` in every resolution context
  while returning only common directional `BULLISH`/`BEARISH` or explicit
  `NonDirectional(NEUTRAL_DIRECTION)`.
- Treat neutral as a complete non-directional classification, never a false
  Boolean, loss, miss, bearish side, unavailable input, excluded/incomplete
  outcome, null, or default.
- Add a disconnected resolver and source-local Java goldens only. Add no
  horizon, calendar, price, return, target, calculator adapter, observation,
  provider/account/key, scheduler, schema, fixture, manifest member, OpenAPI
  path, Flyway migration, database row, API behavior, or web source.

### Locked contract decisions

- ADR-011 and `quality/P3_ACCEPTANCE.md` own the exact request, mapping, result,
  constructor consistency, canonical-definition, purity, and deferred-
  integration contracts.
- `COLLAPSE_STRONG_DIRECTIONS_NEUTRAL_NON_DIRECTIONAL_V1` uses an exhaustive
  enum switch over `CallDirection`; no ordinal, enum-name string, alias, case
  normalization, default, or fallback can infer a polarity.
- Results are sealed as only `Directional(context,side)` or
  `NonDirectional(context,reason)`. Directional side is exactly
  `BULLISH|BEARISH`; non-directional reason is exactly `NEUTRAL_DIRECTION`.
- Public result constructors validate the source-direction/result compatibility
  available from their own context and fail closed for a wrong side, neutral/
  directional mismatch, wrong hash, or null component.
- The exact single-line canonical definition is 489 ASCII/UTF-8 bytes with
  fixed lowercase SHA-256
  `d83eccc92fedd7ba025745be2c8e78245bc308d0ff479467fa61afe543dc8a50`.
  Definition bytes are defensively returned and every resolution context echoes
  the digest. This identity is neither the strict-close policy digest nor either
  existing model-only scoring-methodology hash.

### Module and file boundary

- The isolated API package
  `com.wallstreetreceipts.api.domain.outcome.direction` contains exactly
  `CallDirectionPolarityPolicyVersion.java`,
  `CallDirectionPolarityRequest.java`,
  `CallDirectionPolarityResolution.java`, and
  `CallDirectionPolarityResolver.java` only.
- `CallDirectionPolarityResolverGoldenTest.java` owns source-local exhaustive
  mapping, neutral semantics, invalid request/result, constructor consistency,
  canonical bytes/hash, defensive-copy, exact-shape, and default-environment
  replay coverage.
- ADR-011, the P3 acceptance contract, README, this log, and a mutation-sensitive
  repository CI extension own exact-shape, reverse-wiring, unchanged-canonical-
  surface, and no-provider boundaries.

### Routes

- None. The policy is not invoked by a calculator, controller, application
  service, repository, fixture adapter, API response, scheduler, or web route.

### Verification

- Focused `CallDirectionPolarityResolverGoldenTest`: PASS, 30/30 tests.
- Full API Maven verification: PASS, 21 Surefire suites and 328/328 tests with
  zero failures, errors, or skips; the application JAR was packaged.
- PostgreSQL Testcontainers migration coverage executed 4/4 tests. Compose
  environment-file validation also passed.
- Repository-local embedded workflow Python blocks 1–19 executed 19/19,
  including 14 schema/32 fixture-record validation, both protected P2 consumer
  baselines, every pre-existing P3 guard, and the new polarity guard. All 20
  embedded Python blocks compiled.
- Block 20 is the cross-stack log verifier and was intentionally not run because
  this disconnected leaf launched no Spring or Next service and changed no
  runtime surface; no cross-stack result is claimed.
- SnakeYAML 2.5 parsed the workflow, and `git diff --check` passed. Final
  production/test/CI review reported zero blocker and zero high-severity
  findings.
- No web, browser, or cross-stack test is reported as executed for this slice.

### Deferred boundary

- Calculator-side enum adaptation, target eligibility, horizon observations and
  returns, cancellation eligibility, methodology activation, point-in-time
  input identity, fingerprinting, persistence, calculated outcomes, aggregates,
  and UI publication remain later reviewed P3 work.
- Real calendar and market providers, display/storage/derived-data rights,
  freshness/health, credentials, and non-DEMO publication remain P5 work. This
  disconnected policy requires no API key, account, paid plan, domain, license,
  or network access.

## P3 — Calculator-side polarity adapter

Status: complete for this disconnected adapter slice; the broader P3 scoring
phase remains open, with no neutral entry point, calculator invocation, market
input, provider, persistence, API, or product publication

### Scope

- Translate the already resolved common `DirectionalSide.BULLISH` and
  `DirectionalSide.BEARISH` one-to-one into both existing calculator-specific
  side enums.
- Add exactly two public static methods on one final utility class; reject null,
  use exhaustive enum switches, and expose no overload for the full polarity
  resolution or any non-directional type.
- Add no policy/methodology version, definition/hash, target, price, return,
  horizon, calendar, observation, calculator input/result/invocation,
  provider/account/key, scheduler, schema, fixture, manifest member, OpenAPI
  path, Flyway migration, database row, API behavior, or web source.

### Locked contract decisions

- ADR-012 and `quality/P3_ACCEPTANCE.md` own the exact file/class/method surface,
  two-side mappings, null rejection, purity, reverse-wiring, and deferred-
  orchestration contracts.
- `CalculatorSideAdapter.toTargetHitSide(DirectionalSide)` maps only
  `BULLISH→TargetHitSide.BULLISH` and `BEARISH→TargetHitSide.BEARISH`.
- `CalculatorSideAdapter.toDirectionalWinSide(DirectionalSide)` maps only
  `BULLISH→DirectionalWinSide.BULLISH` and
  `BEARISH→DirectionalWinSide.BEARISH`.
- The adapter accepts neither `NonDirectional` nor the full polarity resolution;
  neutral cannot become false, loss, bearish, unavailable, or a calculator
  input. Null fails before translation rather than serving as neutral.
- This one-to-one vocabulary bridge introduces no independent methodology
  choice, so it has no policy version, canonical definition, hash, fingerprint,
  provenance, or state.

### Module and file boundary

- Production adds exactly
  `apps/api/src/main/java/com/wallstreetreceipts/api/domain/outcome/adapter/CalculatorSideAdapter.java`.
- Tests add exactly
  `apps/api/src/test/java/com/wallstreetreceipts/api/domain/outcome/adapter/CalculatorSideAdapterGoldenTest.java`.
- ADR-012, the P3 acceptance contract, README, this log, and mutation-sensitive
  repository CI own exact shape, sole-bridge imports, null/neutral closure,
  unchanged canonical surfaces, and no-provider boundaries.

### Routes

- None. No calculator, controller, application service, provider, repository,
  scheduler, API response, or web route invokes the adapter.

### Verification

- Focused `CalculatorSideAdapterGoldenTest`: PASS, 6/6 tests.
- Full API Maven verification: PASS, 22 Surefire suites and 334/334 tests with
  zero failures, errors, or skips; PostgreSQL Testcontainers migration coverage
  executed 4/4 tests and the application JAR was packaged.
- Repository-local workflow runtime blocks 1–20 passed 20/20, and embedded
  Python blocks 1–21 passed syntax compilation 21/21. Block 21 is the
  cross-stack verifier and was intentionally not run; no Spring or Next service
  was launched for it.
- SnakeYAML workflow parsing, Compose configuration validation, and
  `git diff --check` passed.
- Production/test/CI review reported zero blocker, zero high-severity, and zero
  known false-positive findings. No web, browser, or cross-stack result is
  claimed.

### Deferred boundary

- ADR-013's disconnected routing slice now owns exact extraction and preservation
  of directional/non-directional branches. Choosing target/return inputs,
  composing unavailable states, invoking calculators, methodology activation,
  fingerprinting, persistence, calculated outcomes, aggregates, and UI
  publication remain later reviewed P3 work.
- Real calendar and market providers, display/storage/derived-data rights,
  freshness/health, credentials, and non-DEMO publication remain P5 work. This
  adapter requires no API key, account, paid plan, domain, market calendar,
  price feed, license, or network access.

## P3 — Calculator-side routing evidence

Status: complete for this disconnected routing-evidence slice; the broader P3
scoring phase remains open, with no calculator input/invocation, market input,
provider, persistence, API, or product publication

### Scope

- Route one complete `CallDirectionPolarityResolution` into exactly preserved
  directional calculator-side evidence or preserved non-directional evidence.
- Keep the exact original polarity result record. Derive calculator-specific
  sides only for a `Directional` source through `CalculatorSideAdapter`; give a
  `NonDirectional` source no side or Boolean.
- Add no target, favorable extreme, price, return, horizon, calendar,
  observation, calculator input/result/invocation, provider/account/key,
  methodology version/hash, scheduler, schema, fixture, manifest member,
  OpenAPI path, Flyway migration, database row, API behavior, or web source.

### Locked contract decisions

- ADR-013 and `quality/P3_ACCEPTANCE.md` own the exact package/file/class/method,
  sealed result, constructor consistency, source preservation, purity,
  reverse-wiring, and deferred-calculation contracts.
- `CalculatorSideRouting.route(CallDirectionPolarityResolution)` is the sole
  public static routing method and uses an exhaustive sealed-pattern switch.
- `DirectionalRoute(Directional source, TargetHitSide targetHitSide,
  DirectionalWinSide directionalWinSide)` preserves the exact source and
  recomputes both expected adapter translations before accepting direct
  construction.
- `NonDirectionalRoute(NonDirectional source)` preserves the exact source only.
  Its `NEUTRAL_DIRECTION` evidence is not false, a miss/loss, bearish,
  unavailable/missing, excluded, or a calculator input.
- Routing mechanically composes ADR-011 and ADR-012, so it adds no policy or
  methodology identity and does not rewrite the preserved source context/hash.

### Module and file boundary

- Production adds exactly
  `apps/api/src/main/java/com/wallstreetreceipts/api/domain/outcome/routing/CalculatorSideRouting.java`.
- Tests add exactly
  `apps/api/src/test/java/com/wallstreetreceipts/api/domain/outcome/routing/CalculatorSideRoutingGoldenTest.java`.
- ADR-013, the P3 acceptance contract, README, this log, and mutation-sensitive
  repository CI own the exact sole routing exception and unchanged product/data
  boundaries.

### Routes

- None. No calculator, controller, application service, provider, repository,
  scheduler, API response, or web route invokes the routing leaf.

### Verification

- Focused `CalculatorSideRoutingGoldenTest`: PASS, 11/11 tests.
- Full API Maven verification: PASS, 23 Surefire suites and 345/345 tests with
  zero failures, errors, or skips; PostgreSQL Testcontainers migration coverage
  executed 4/4 tests and the application JAR was packaged.
- Repository-local workflow runtime blocks 1–21 passed 21/21. All embedded
  Python blocks passed syntax compilation 22/22. Block 22 is the cross-stack
  access-log verifier and was intentionally not run; no Spring or Next service
  was launched for it.
- SnakeYAML 2.5 workflow parsing, Compose configuration validation, and
  `git diff --check` passed. No web, browser, or cross-stack result is claimed.

### Deferred boundary

- Target eligibility, target/return selection, horizon/window observation
  identity, unavailable-state composition, calculator invocation, methodology
  activation, fingerprinting, persistence, calculated outcomes, aggregates,
  and UI publication remain later reviewed P3 work.
- Before endpoint-price, asset-return, or target-error work, the exact price
  basis, corporate-action/currency treatment, and decimal output policy require
  reviewed versioned decisions. Real provider selection, credentials, and
  display/storage/derived-data rights remain P5 work. This routing leaf requires
  no API key, account, paid plan, domain, market calendar, price feed, license,
  or network access.

## P3 — Point-in-time endpoint price and target error

Status: complete for two disconnected source-local policy leaves; the
broader P3 scoring phase remains open, with no runtime outcome, persistence,
provider, API, or product publication

### Scope

- Select exactly one official primary-venue regular-session endpoint close from
  a strict ADR-010 resolved window and explicit point-in-time catalog, binding,
  and observation evidence.
- Require exact asset, venue, currency, source, catalog, session, observation
  time, price-field, adjustment-basis, and corporate-action-continuity identity.
  Future candidates are invisible before mismatch/cardinality evaluation.
- Preserve explicit endpoint unavailability and ambiguity. Use only positive
  exact `NUMERIC(38,12)` price evidence, split/reverse-split adjusted to the
  endpoint-share basis and dividend-unadjusted, with no FX or fallback.
- Calculate target error from one complete endpoint resolution and nullable
  point-in-time target evidence as exactly `abs(target-actual)/actual`, with
  actual endpoint price as denominator and one scale-12 `HALF_EVEN` division.
- Compose missing target/endpoint states while preserving the exact nested
  endpoint reason; reject identity mismatches and return explicit rounded-output
  overflow rather than zero, clipping, or another scale.
- Add no asset-return policy, window metric, target-hit/directional-win
  invocation, methodology activation, fingerprint, schema, fixture, manifest
  member, OpenAPI path, Flyway migration, database row, provider adapter,
  controller, repository, scheduler, API behavior, or web source.

### Locked contract decisions

- ADR-014 owns
  `OFFICIAL_PRIMARY_VENUE_CLOSE_SPLIT_ADJUSTED_V1`, its exact 2259 UTF-8 bytes,
  SHA-256
  `37e37aba9302d77366cef4129f77a82b7ccb2f1937bfffc0315ea8d0bc6b1f76`,
  point-in-time gates, future invisibility, exact mismatch precedence,
  cardinality, price/adjustment semantics, and 16 unavailable reasons.
- ADR-015 owns `ACTUAL_DENOMINATOR_SCALE_12_HALF_EVEN_V1`, its exact 1942
  UTF-8 bytes, SHA-256
  `31ca30555549f670e3c22d98ead16f7a02bfad198f36532effaf4a4b6931d074`,
  target PIT visibility, missing-state truth table, identity precedence,
  actual-denominator formula, one-division half-even rounding, scale-12 output,
  and nine unavailable reasons.
- A V1 endpoint request requires the exact strict-close V1 policy and hash.
  A V1 target-error input requires the exact endpoint V1 policy and hash. No
  later policy version may be silently accepted as equivalent.
- Public result constructors own only locally decidable consistency. Only
  `EndpointPriceSelector` attests request-candidate membership, PIT filtering,
  precedence, and cardinality; only `TargetErrorCalculator` attests target PIT,
  missing-state composition, identity precedence, formula, and output boundary.

### Module and file boundary

- `com.wallstreetreceipts.api.domain.outcome.observation` adds exactly ten
  production files: `CatalogPointInTimeEvidence.java`,
  `CorporateActionContinuity.java`, `EndpointPriceAdjustmentBasis.java`,
  `EndpointPriceBinding.java`, `EndpointPriceField.java`,
  `EndpointPriceObservation.java`, `EndpointPricePolicyVersion.java`,
  `EndpointPriceRequest.java`, `EndpointPriceResolution.java`, and
  `EndpointPriceSelector.java`.
- `com.wallstreetreceipts.api.domain.outcome.targeterror` adds exactly five
  production files: `TargetErrorCalculator.java`, `TargetErrorInput.java`,
  `TargetErrorPolicyVersion.java`, `TargetErrorResult.java`, and
  `TargetPriceEvidence.java`.
- Source-local tests are exactly `EndpointPriceSelectorGoldenTest.java` and
  `TargetErrorCalculatorGoldenTest.java` in their matching packages.
- ADR-014, ADR-015, `quality/P3_ACCEPTANCE.md`, README, this log, and a
  mutation-sensitive repository CI extension own canonical bytes/hashes,
  exact-shape, precedence/equality, decimal, reverse-graph, and unchanged-
  publication boundaries.

### Routes

- None. No controller, application service, provider, repository, scheduler,
  API response, canonical fixture adapter, or web route invokes either leaf.

### Verification

- Focused source-local goldens: PASS, 63/63 tests (`EndpointPriceSelectorGoldenTest`
  23/23 and `TargetErrorCalculatorGoldenTest` 40/40), with zero failures,
  errors, or skips. API test compilation and `git diff --check` also passed.
- Clean full API Maven verification: PASS, 25 Surefire suites and 408/408 tests
  with zero failures, errors, or skips. PostgreSQL Testcontainers migration
  coverage passed 4/4, and the application JAR packaging/repackage completed
  successfully.
- Repository-local workflow runtime blocks 1–22 passed 22/22, including the
  dedicated endpoint/target guard and every affected reverse guard. Block 23
  is the existing cross-stack Spring/Next access-log verifier and was
  intentionally not run because neither service was launched for this
  disconnected slice.
- All embedded workflow Python blocks passed syntax compilation 23/23.
  SnakeYAML 2.5 parsed the four-job workflow, Compose configuration validation
  passed, and `git diff --check` passed.
- No web, browser, provider, or cross-stack result is claimed for this
  disconnected implementation.

### External-data boundary

- This source-local implementation requires no API key, account, paid plan,
  domain, vendor license, environment secret, or network access.
- P5 owns provider selection and the exact credential/environment boundary.
  Before non-DEMO values enter these contracts, primary-venue close, exchange
  calendar, corporate-action, asset/venue/reference, target-evidence, storage,
  display, and derived-data rights must be established. This slice does not
  invent a vendor or secret name.

### Deferred boundary

- Asset return, full-window high/low evidence, MFE/MAE, alpha/sector alpha,
  cancellation eligibility, calculator orchestration, canonical methodology
  activation, input fingerprints, append-only persistence, aggregation,
  ranking, and UI publication remain later reviewed P3/P5 work.

## P3 — Point-in-time basis/endpoint price pair and signed asset return

Status: complete for these two disconnected policy leaves; the broader P3
scoring phase remains open, with no runtime outcome, persistence, provider,
API, or product publication

### Scope

- Select one source-recorded price at the exact original/correction basis event
  and bind it to one complete ADR-014 official endpoint-price resolution.
- Filter basis-price and independent adjustment evidence by both `availableAt`
  and `capturedAt` against the endpoint context's `evaluationAsOf` before any
  identity, mismatch, or cardinality reasoning.
- Require exact basis, asset, primary venue, currency, price source/revision,
  basis and endpoint observation/provider-event links, coverage endpoints, and
  split/reverse-split endpoint-share/dividend-unadjusted semantics. Preserve
  explicit missing, mismatch, continuity, ambiguity, and nested endpoint
  unavailability instead of prior close, nearest price, interpolation, dedupe,
  FX, or fallback.
- Calculate signed asset return from one complete pair as exactly
  `(endpoint-basis)/basis`, with one subtraction and one scale-12 `HALF_EVEN`
  division. Preserve exact pair unavailability and return explicit output
  overflow rather than clipping, another scale, or zero.
- Add no calculator orchestration, directional-win/target-hit invocation,
  methodology activation, fingerprint, schema, fixture, manifest member,
  OpenAPI path, Flyway migration, database row, provider adapter, controller,
  repository, scheduler, API behavior, or web source.

### Locked contract decisions

- ADR-016 owns
  `SOURCE_RECORDED_BASIS_EVENT_TO_OFFICIAL_ENDPOINT_PRICE_PAIR_V1`, its exact
  4655 UTF-8 bytes, SHA-256
  `895e4bc97ebb3a92b80f2c58e2d28abb94440eeca963046ee755fa98825f4887`,
  source-recorded basis-event semantics, PIT filtering, missing truth table,
  fixed two-stage mismatch/cardinality precedence, exact observation links and
  coverage, price/action basis, and 24 unavailable reasons.
- ADR-017 owns `SIGNED_BASIS_DENOMINATOR_SCALE_12_HALF_EVEN_V1`, its exact 1011
  UTF-8 bytes, SHA-256
  `e5e61c4adcd6567bfc76f73114499578f09de2254dc39a2553f3c0e2eaf03486`,
  signed basis-denominator formula, one-division half-even rounding, scale-12
  output, exact -1 lower boundary, overflow behavior, and two unavailable
  reasons.
- The pair request requires the exact ADR-014 endpoint V1 policy/hash. The
  return input requires the exact ADR-016 pair V1 policy/hash. No later version
  may be silently accepted as equivalent.
- Public constructors own only locally decidable consistency. Only
  `AssetReturnPricePairSelector` attests request membership, PIT filtering,
  precedence, and cardinality; only `AssetReturnCalculator` attests the signed
  formula and one permitted rounding step.

### Module and file boundary

- `com.wallstreetreceipts.api.domain.outcome.pricepair` adds exactly seven
  production files: `BasisPriceField.java`, `BasisPriceObservation.java`,
  `PricePairAdjustmentEvidence.java`,
  `AssetReturnPricePairPolicyVersion.java`,
  `AssetReturnPricePairRequest.java`,
  `AssetReturnPricePairResolution.java`, and
  `AssetReturnPricePairSelector.java`.
- `com.wallstreetreceipts.api.domain.outcome.assetreturn` adds exactly four
  production files: `AssetReturnPolicyVersion.java`, `AssetReturnInput.java`,
  `AssetReturnResult.java`, and `AssetReturnCalculator.java`.
- Source-local tests are exactly `AssetReturnPricePairSelectorGoldenTest.java`
  and `AssetReturnCalculatorGoldenTest.java` in their matching packages.
- ADR-016, ADR-017, `quality/P3_ACCEPTANCE.md`, README, this log, and a
  mutation-sensitive repository CI extension own canonical bytes/hashes,
  exact-shape, PIT/precedence/equality, formula/decimal, reverse-graph, and
  unchanged-publication boundaries.

### Routes

- None. No controller, application service, provider, repository, scheduler,
  API response, canonical fixture adapter, or web route invokes either leaf.

### Verification

- Focused source-local goldens: PASS, 191/191 tests
  (`AssetReturnPricePairSelectorGoldenTest` 153/153 and
  `AssetReturnCalculatorGoldenTest` 38/38), with zero failures, errors, or
  skips.
- The dedicated pair/return guard and the affected explicit-anchor,
  strict-close, and endpoint/target reverse guards passed. All 24 embedded
  workflow Python blocks passed syntax compilation.
- SnakeYAML 2.5 parsed the four-job workflow, Compose configuration validation
  passed, and `git diff --check` passed.
- Full API Maven verification: PASS, 599/599 tests with zero failures, errors,
  or skips. `PostgreSqlMigrationTest` passed 4/4 through Testcontainers, and
  application JAR repackage completed successfully.
- Repository-local workflow runtime blocks 1–23 passed 23/23 after loading the
  workflow-pinned `jsonschema[format-nongpl]==4.23.0` into a temporary local
  dependency path. Block 24 is the existing cross-stack Spring/Next access-log
  verifier and was intentionally not run because neither service was launched
  for this disconnected slice; no cross-stack result is claimed.
- No web, browser, provider, or cross-stack result is claimed for this
  disconnected implementation.

### External-data boundary

- This source-local implementation requires no API key, account, paid plan,
  domain, vendor license, environment secret, or network access.
- P5 owns provider selection. Before non-DEMO evidence enters these contracts,
  the selected vendor must grant historical event-time/intraday price
  entitlement; exchange-calendar, asset/venue/reference, and corporate-action
  rights; and explicit display, storage, derived-data, and redistribution
  terms. Only then may a reviewed adapter define one named, scoped secret. This
  P3 slice invents neither vendor nor environment-variable name.

### Deferred boundary

- Calculator orchestration and target eligibility, full-window high/low
  evidence, MFE/MAE, alpha/sector alpha, cancellation eligibility, canonical
  methodology activation, input fingerprints, append-only persistence,
  aggregation, ranking, and UI publication remain later reviewed P3/P5 work.

## P3 — Point-in-time target-hit input eligibility

Status: complete for one disconnected readiness policy; the broader P3 scoring
phase remains open, with no calculator, orchestrator, persistence, provider,
API, or product publication

### Scope

- Bind one exact original/correction basis to source-attested forecast terms,
  the preserved ADR-013 route, nullable normalized target evidence, one ADR-010
  strict-horizon resolution, catalog PIT evidence, and evaluation as-of.
- Distinguish source-attested target absence, neutral direction, an unreached
  horizon, missing/mismatched evidence, and full readiness to seek window
  evidence. None becomes a Boolean miss/loss or an inferred cancellation.
- Filter future terms, target, and catalog evidence before identity or reason
  selection. Require exact basis, asset, source direction/route, normalized
  target basis/asset/currency, and catalog ID/revision without latest-revision,
  FX, or fallback behavior.
- Fail closed for a non-null target date on the directional-present path because
  V1 defines no target-date expiry or window semantics. Target absence without
  PIT-visible normalized target evidence, or a non-directional route with
  present source terms, reaches not-applicable first. Do not consume previous
  target or infer current terms from a later correction.
- Add no window high/low selector, target-hit or other calculator invocation,
  calculator orchestration, methodology activation, fingerprint, schema,
  fixture, manifest member, OpenAPI path, Flyway migration, database row,
  provider adapter, controller, repository, scheduler, API behavior, or web
  source.

### Locked contract decisions

- ADR-018 owns `POINT_IN_TIME_TARGET_HIT_INPUT_READINESS_V1`, its exact 3862
  UTF-8 bytes, SHA-256
  `a6b4c9f4e4d29b5f1a9b0c300e2d7b9505318c708dfb0ad0e88f71324cf65465`,
  source terms/target disposition semantics, PIT invisibility, exact
  basis/route/target/catalog identities, readiness and
  no-inference boundaries, four result branches, and exact reason order.
- `BasisForecastTermsEvidence.TargetDisposition.Present` contains a positive
  exact `NUMERIC(38,12)` source target, currency, and nullable target date.
  Empty `TargetDisposition.Absent` is affirmative source evidence rather than
  a substitute for null/future normalized target data. Source and normalized
  target values remain separate with no numeric-equality inference. A visible
  normalized target contradicting source-attested absence becomes
  `TARGET_STATE_CONFLICT` after route identity and before not-applicable; the
  conflicting record is preserved. Future target evidence remains invisible
  and null-equivalent, so it cannot create a conflict.
- `ReadyForWindowEvidence` means only that a later policy may select an exact
  full-window favorable extreme. `Pending` preserves
  `HORIZON_NOT_REACHED_AS_OF`; `NotApplicable` distinguishes target absence,
  non-directional route, and their combination; `Unavailable` preserves exact
  evidence and nested ADR-010 horizon failures.
- Target date is preserved but unsupported when present. Previous target,
  latest correction, cancellation eligibility, window completeness, and
  product outcome status are not inputs and are never inferred.
- Public constructors own local consistency only. Only
  `TargetEligibilityResolver` attests PIT filtering, precedence, cross-evidence
  identity, endpoint readiness, and the returned branch.

### Module and file boundary

- `com.wallstreetreceipts.api.domain.outcome.targeteligibility` adds exactly
  five production files: `TargetEligibilityPolicyVersion.java`,
  `BasisForecastTermsEvidence.java`, `TargetEligibilityRequest.java`,
  `TargetEligibilityResolution.java`, and `TargetEligibilityResolver.java`.
- The source-local test is exactly `TargetEligibilityResolverGoldenTest.java`
  in the matching package.
- ADR-018, `quality/P3_ACCEPTANCE.md`, README, this log, and the repository CI
  extension own canonical identity, exact shape, PIT/precedence/equality,
  reverse-graph, and unchanged-publication boundaries.

### Routes

- None. No controller, application service, calculator orchestrator, provider,
  repository, scheduler, API response, canonical fixture adapter, or web route
  consumes the eligibility result.

### Verification

- Focused source-local golden: PASS, 39/39
  `TargetEligibilityResolverGoldenTest` tests with zero failures, errors, or
  skips.
- Full API Maven verification: PASS, 638/638 tests with zero failures, errors,
  or skips; PostgreSQL Testcontainers/Flyway migration coverage passed and the
  Spring Boot jar was repackaged successfully.
- Repository workflow verification: PASS, all 24 locally executable embedded
  Python guard bodies passed with the CI-pinned `jsonschema==4.23.0`; all 25
  embedded Python bodies compile. The remaining cross-stack body was syntax
  checked but not executed in this disconnected slice.
- Compose configuration and `git diff --check`: PASS.
- No web, browser, provider, or cross-stack result is claimed for this
  disconnected implementation.

### External-data boundary

- This source-local policy requires no API key, account, paid plan, domain,
  vendor license, environment secret, or network access.
- P5 owns provider selection. Before non-DEMO terms, targets, calendars, or
  window observations enter the contracts, historical entitlements and
  display, storage, derived-data, and redistribution rights must be established.
  Only then may a reviewed adapter define a named, scoped secret.

### Deferred boundary

- The exact PIT full-window high/low selector described here is completed by
  the following P3 slice with causal bounds, source/revision, adjustment,
  continuity, missing/ambiguity precedence, and side selection locked.
- A reviewed orchestrator comes next. It may invoke target hit and the already
  completed leaves only while preserving not-applicable, pending, unavailable,
  and nested reasons without recalculation or fallback.
- MFE/MAE, alpha/sector alpha, cancellation eligibility, canonical methodology
  activation, input fingerprints, append-only persistence, aggregation,
  ranking, and UI publication remain later reviewed P3/P5 work.

## P3 — Point-in-time attested causal-window favorable extreme

Status: complete for one disconnected source-attested selection policy; the
broader P3 scoring phase remains open, with no calculator orchestration,
persistence, provider, API, or product publication

### Scope

- Consume one complete ADR-018 `ReadyForWindowEvidence`, nullable window-price
  binding evidence, and caller-supplied full-window high/low observations.
  Inherit evaluation as-of, resolved horizon, directional side, target, and
  catalog rather than accepting competing values.
- Define the economic interval as exact primary-venue regular-session
  observations in the ordered horizon sessions where
  `observation.time > basis.eventTime` and
  `observation.time <= endpointSession.closesAt`. This prevents an intraday
  first session's pre-call high or low from becoming post-call evidence while
  retaining the endpoint-close boundary.
- Select the stored high for bullish or stored low for bearish from exactly one
  PIT-visible, fully matching, upstream-attested pair. Add no endpoint-close
  fallback, raw bar/tick aggregation, target comparison, calculator invocation,
  methodology activation, schema, fixture, persistence, provider, API, or web
  behavior.

### Locked contract decisions

- ADR-019 owns
  `POINT_IN_TIME_ATTESTED_CAUSAL_WINDOW_HIGH_LOW_V1`, its exact 4633 UTF-8
  bytes, SHA-256
  `e3a0e93030c8f09ae5398bf6df0f2e28eec14b0a31f5bea240fc78f2412c2463`,
  exact input/result fields, causal boundaries, PIT invisibility, binding and
  candidate identities, reason precedence, side selection, attestation scope,
  and no-fallback boundary.
- `WindowPriceBinding` preserves exact binding identity/revision, asset,
  primary venue, currency, price-source ID/revision, PIT timestamps, and
  provenance. Null and future binding evidence are indistinguishable and
  absent from output; binding identity failures clear all candidate evidence.
- `FullWindowHighLowObservation` preserves exact observation/provider-event,
  basis/horizon, asset/venue/currency/source, provenance, catalog, ordered
  sessions, bounds and boundary types, field/completeness, adjustment,
  continuity, PIT timestamps, and original high/low evidence. Values are
  positive exact `NUMERIC(38,12)` inputs with low `<=` high; availability cannot
  precede the upper bound.
- `EXACT_CAUSAL_WINDOW_SESSION_UNION` is an upstream provider/source
  attestation, not a claim that this selector independently checked raw ticks or
  bars. No-trade, halt, bar-straddle, correction-sequence, auction, and raw-
  coverage proof semantics remain deferred.
- Future candidates are filtered before every identity, reason, cardinality,
  and output decision. Every known candidate is in scope; any known invalid
  candidate poisons selection at the first fixed gate. Multiple fully valid
  candidates remain ambiguous even when equal or repeated, with no
  deduplication or provider preference.
- Public result constructors validate local consistency only for the evidence
  supplied to them. Only `FavorableExtremeSelector.select(request)` attests
  complete-request membership, PIT filtering, poisoning, and cardinality;
  downstream request-wide claims must use a selector-produced resolution.
- The exact 22 unavailable reasons are, in order:
  `TARGET_ADJUSTMENT_BASIS_UNSUPPORTED`, `BINDING_NOT_KNOWN_AS_OF`,
  `BINDING_ASSET_MISMATCH`, `BINDING_PRIMARY_VENUE_MISMATCH`,
  `BINDING_CURRENCY_MISMATCH`, `OBSERVATION_MISSING_AS_OF`, `BASIS_MISMATCH`,
  `HORIZON_MISMATCH`, `ASSET_MISMATCH`, `PRIMARY_VENUE_MISMATCH`,
  `CURRENCY_MISMATCH`, `SOURCE_MISMATCH`, `CATALOG_MISMATCH`,
  `SESSION_WINDOW_MISMATCH`, `LOWER_BOUND_MISMATCH`, `UPPER_BOUND_MISMATCH`,
  `BOUNDARY_CONVENTION_MISMATCH`, `PRICE_FIELD_MISMATCH`,
  `WINDOW_COMPLETENESS_UNAVAILABLE`, `ADJUSTMENT_BASIS_MISMATCH`,
  `CORPORATE_ACTION_CONTINUITY_UNAVAILABLE`, and `OBSERVATION_AMBIGUOUS`.
- A resolved bullish route returns the exact original `windowHigh` as field
  `HIGH`; a bearish route returns the exact original `windowLow` as field
  `LOW`. The selector does not round, rescale, aggregate, compare to target, or
  invoke the pure target-hit calculator.

### Module and file boundary

- `com.wallstreetreceipts.api.domain.outcome.favorableextreme` adds exactly six
  production files: `FavorableExtremePolicyVersion.java`,
  `WindowPriceBinding.java`, `FullWindowHighLowObservation.java`,
  `FavorableExtremeRequest.java`, `FavorableExtremeResolution.java`, and
  `FavorableExtremeSelector.java`.
- The source-local test is exactly `FavorableExtremeSelectorGoldenTest.java` in
  the matching package.
- ADR-019, `quality/P3_ACCEPTANCE.md`, README, this log, and the repository CI
  extension own canonical identity, exact shape, PIT/precedence/equality,
  reverse-graph, and unchanged-publication boundaries.

### Routes

- None. No controller, application service, calculator orchestrator, provider,
  repository, scheduler, API response, canonical fixture adapter, or web route
  consumes the favorable-extreme result.

### Verification

- Focused source-local golden: PASS, 42/42
  `FavorableExtremeSelectorGoldenTest` tests with zero failures, errors, or
  skips.
- Full API Maven verification: PASS, 680/680 tests with zero failures, errors,
  or skips. PostgreSQL Testcontainers/Flyway migration coverage passed and the
  Spring Boot jar was repackaged successfully.
- Repository CI contract validation: PASS. All 26 embedded Python bodies
  compiled, all 25 locally runnable bodies passed, and the final Tomcat
  cross-stack body was syntax-checked but intentionally not executed without
  its CI-produced runtime logs. The dedicated ADR-019 guard, both protected
  production baselines at 169 files / SHA-256
  `9db95ad810fb908be4ba82ea27841cd69d7f5f2cf188fed8e2476f7fa4782352`,
  the API job's exact 42/42 Surefire XML cardinality check, SnakeYAML 2.5
  four-job parsing, Compose configuration, and `git diff --check` all passed.
- No web, browser, provider, or cross-stack result is claimed for this
  disconnected implementation.

### External-data boundary

- This source-local policy requires no API key, account, paid plan, domain,
  vendor license, environment secret, or network access.
- Before a non-DEMO attestation enters this contract, P5 must select a provider
  and establish entitled historical intraday/tick data, exchange calendars,
  corporate actions, asset/venue reference data, and explicit storage, display,
  derived-data, and redistribution rights. Only then may a reviewed adapter
  define a named, scoped secret; this slice invents neither vendor nor secret
  name.

### Deferred boundary

- A reviewed calculator orchestrator comes next. It must consume eligibility
  and favorable-extreme resolutions, invoke target hit only for the exact
  resolved branch, and preserve every pending, not-applicable, unavailable, and
  nested reason without recalculation or fallback.
- Raw intraday/tick aggregation remains later provider work. It must version
  no-trade, halt, auction, bar-straddle, correction-sequence, and raw-coverage
  proof semantics before it can produce the completeness attestation.
- MFE/MAE, alpha/sector alpha, cancellation eligibility, canonical methodology
  activation, input fingerprints, append-only persistence, aggregation,
  ranking, scheduling, and UI publication remain later reviewed P3/P5 work.

## P3 — Supplied-leaf point-in-time target-hit orchestration

Status: complete for one disconnected target-hit metric composition; the
broader P3 scoring phase remains open, with no canonical calculated outcome,
methodology activation, persistence, provider, API, or product publication

### Scope

- Consume one complete supplied ADR-018 eligibility resolution and, only for
  Ready, one complete supplied ADR-019 favorable-extreme resolution. Accept no
  competing as-of, horizon, side, target, high/low, extreme, or Boolean.
- Preserve Pending, NotApplicable, eligibility Unavailable, and favorable-
  extreme Unavailable as exact typed leaf records. Build the primitive input
  and invoke target hit exactly once only for whole-record-matching Ready plus
  Resolved.
- Add no resolver/selector replay, source-target substitution, direction
  reinterpretation, high/low reselection, endpoint-close fallback, rounding,
  raw aggregation, schema, fixture, persistence, provider, API, or web behavior.

### Locked contract decisions

- ADR-020 owns `POINT_IN_TIME_TARGET_HIT_ORCHESTRATION_V1`, its exact 3082
  UTF-8 bytes, SHA-256
  `b91bf68958e42ad003b80973c74f9acc2dad8e4629f6a1905798df98aa8b5348`,
  exact three-field request, five typed result variants, required ADR-018/019
  versions and hashes, conditional evidence topology, whole-record equality,
  branch preservation, exact primitive input, single invocation, invariant-
  failure, attestation, and no-publication boundaries.
- Ready requires one non-null favorable resolution; the nested readiness must
  equal the supplied eligibility record. Every non-ready branch requires a
  null favorable result. ADR-019 Unavailable owns missing market evidence, so
  omission of the complete selector result is malformed composition rather
  than a new financial-data reason.
- Result variants are exactly `Available`, `Pending`, `NotApplicable`,
  `EligibilityUnavailable`, and `FavorableExtremeUnavailable`. The four non-
  calculation meanings preserve the supplied typed leaf object directly; no
  reason inspection, outer enum, mapping, flattening, false, loss, or primitive
  missing-input substitute exists.
- Available input uses only `DirectionalRoute.targetHitSide()`, normalized
  `TargetPriceEvidence.target()`, and the selector's exact
  `FavorableExtreme.value()`. Source terms can carry a numerically different
  source target without changing the calculation target.
- `TargetHitCalculator.calculate` has one new production callsite and runs once
  only inside Ready plus Resolved. Equality remains a hit on both sides. A
  primitive Unavailable from complete inputs is an internal invariant failure
  that emits no orchestration result.
- Public leaf and orchestration result constructors attest local consistency
  only. This contract attests supplied policy/correlation, exact composition,
  primitive input, and invocation; it does not re-attest leaf request
  membership, PIT filtering, producer invocation, candidate poisoning or
  cardinality, or raw-data completeness.
- `Available` is a disconnected target-hit metric leaf, not canonical
  `CallOutcome.CALCULATED`, `dataComplete`, an active methodology, fingerprint,
  persisted record, aggregate, ranking input, schedule, or product value.

### Module and file boundary

- `com.wallstreetreceipts.api.domain.outcome.targethitorchestration` adds
  exactly four production files:
  `TargetHitOrchestrationPolicyVersion.java`,
  `TargetHitOrchestrationRequest.java`,
  `TargetHitOrchestrationResolution.java`, and `TargetHitOrchestrator.java`.
- The source-local test is exactly `TargetHitOrchestratorGoldenTest.java` in the
  matching package.
- ADR-020, `quality/P3_ACCEPTANCE.md`, README, this log, the repository CI
  reverse allowlists, dedicated guard, and API runtime-cardinality check own
  the exact boundary.

### Routes

- None. No controller, application service, provider, repository, scheduler,
  API response, canonical fixture adapter, or web route consumes or publishes
  the orchestration result.

### Verification

- Focused source-local golden: PASS, 55/55
  `TargetHitOrchestratorGoldenTest` tests with zero failures, errors, or skips.
- Full API Maven verification: PASS, 735/735 tests with zero failures, errors,
  or skips. PostgreSQL Testcontainers/Flyway migration coverage passed and the
  Spring Boot jar was repackaged successfully.
- Repository CI contract validation: PASS. All 27 embedded Python bodies
  compiled, all 26 locally runnable bodies passed, and the final Tomcat cross-
  stack body was syntax-checked but intentionally not executed without its CI-
  produced runtime logs. The dedicated ADR-020 guard, both protected
  production baselines at 173 files / SHA-256
  `c3f6e8579d70c71b57ce855728e89513c9b0ba6f713ce120dfe9aee6469f4731`,
  and the API job's exact 55/55 Surefire XML cardinality check passed.
- Java-aware lexical scanners now remove comments and literals in one stateful
  pass, so comment markers inside strings cannot hide executable calculator or
  runtime tokens from the orchestration reverse allowlists. An adversarial
  `"https://..."` followed by calculator/runtime code remained visible.
- Surefire cardinality gates use explicit mismatch exits rather than Python
  assertions. Both 42/42 and 55/55 checks passed under `python -O`, and an
  intentional mismatched count still exited nonzero in optimized mode.
- SnakeYAML 2.5 parsed the exact four workflow jobs; Compose configuration and
  `git diff --check` passed.
- No web, browser, provider, or cross-stack result is claimed for this
  disconnected implementation.

### External-data boundary

- This source-local composition requires no API key, account, paid plan,
  domain, vendor license, environment secret, or network access.
- Before non-DEMO leaf evidence enters a runtime pipeline, P5 must select a
  provider and establish entitlements for historical intraday/tick data,
  exchange calendars, corporate actions, asset/venue reference data, storage,
  display, derived-data creation, and redistribution. Only then may a reviewed
  adapter introduce a named scoped secret through approved local/CI secret
  stores; secrets must never enter chat or Git.

### Deferred boundary

- The recommended next source-local slice is parallel directional-win
  orchestration over the completed polarity/routing and signed asset-return
  leaves, preserving neutral and nested price-pair/return unavailability with
  no canonical lifecycle publication. It also requires no provider credential.
- Leaf-producer receipts, request-membership proofs, raw intraday aggregation,
  canonical methodology activation, input fingerprinting, append-only outcome
  persistence, lifecycle/cancellation/latest-correction selection, scheduling,
  MFE/MAE, alpha/sector alpha, aggregation, ranking, API/UI publication, and
  production provider integration remain later reviewed P3/P5 work.

## P3 — Supplied-leaf point-in-time directional-win orchestration

Status: implementation and repository verification complete for one
disconnected directional-win metric composition; the broader P3 scoring phase
remains open with no canonical calculated outcome, methodology activation,
persistence, provider, API, or product publication

### Scope

- Consume exactly four non-null fields: the orchestration policy, complete
  `BasisForecastTermsEvidence`, complete `CalculatorSideRouting.Result`, and
  complete `AssetReturnResult`. Keep the return leaf mandatory for neutral.
- Validate the required polarity/asset-return policies and exact source
  direction, whole-record basis, asset identity, and forecast availability and
  capture at or before the nested endpoint evaluation as-of before selecting a
  branch.
- Preserve neutral as `NotApplicable` regardless of return availability and
  preserve directional unavailable return evidence, including all 55 typed
  return/price-pair/endpoint combinations, without inspecting or mapping a
  reason.
- Build the primitive input and invoke directional win exactly once only for a
  directional route plus an available return. Add no producer replay, return
  recalculation, target use, fallback, lifecycle inference, schema, fixture,
  persistence, provider, API, or web behavior.

### Locked contract decisions

- ADR-021 owns `SUPPLIED_LEAF_DIRECTIONAL_WIN_ORCHESTRATION_V1`, its exact 3699
  UTF-8 bytes, SHA-256
  `51429c7601d4807162855f08c680d1e6bb7895f87fc108e141e5ad3a3ab25bcb`,
  exact four-field all-non-null request, three typed result variants, required
  ADR-011/017 versions and hashes, cross-leaf correlation, branch precedence,
  exact primitive input, single invocation, invariant failure, attestation,
  and no-publication boundaries.
- Direction correlation uses the exact canonical source direction, so strong
  and ordinary directions cannot substitute for one another merely because
  they reduce to the same side. Basis correlation uses whole-record equality,
  asset correlation uses the nested endpoint binding, and both forecast PIT
  timestamps must be no later than the nested endpoint `evaluationAsOf`.
- All policy and correlation checks happen before neutral branching. Neutral
  takes precedence over available/unavailable return state and preserves the
  complete terms, non-directional route, and complete return leaf without a
  Boolean or calculator call.
- Result variants are exactly `Available`, `NotApplicable`, and
  `AssetReturnUnavailable`. Every result preserves supplied terms, routing, and
  return records; Available additionally preserves the primitive Available
  result. No outer reason enum, duplicate calculator input, or lifecycle state
  is stored.
- A directional unavailable return is preserved unchanged, including its typed
  asset-return, price-pair, endpoint resolution, and endpoint reason chain.
  Production never calls `.reason()`, maps endpoint-not-reached to Pending,
  flattens, recalculates, or substitutes false/loss/generic unavailable.
- Available input uses only `DirectionalRoute.directionalWinSide()` and the
  exact original `AssetReturnResult.Available.assetReturn()`. The calculator is
  invoked once: bullish requires `> 0`, bearish requires `< 0`, and zero is a
  miss for both, with no rounding, rescaling, tolerance, absolute value,
  percentage conversion, or target-disposition use.
- A primitive Unavailable from complete directional/available inputs is an
  internal invariant failure that emits no orchestration result. Public leaf
  and result constructors attest only locally decidable policy, correlation,
  and shape; orchestration does not re-attest producer request membership, PIT
  filtering, candidate poisoning/cardinality, or producer invocation.
- `Available` is a disconnected metric leaf, not canonical
  `CallOutcome.CALCULATED`, `dataComplete`, an active methodology, input
  fingerprint, persisted record, aggregate, ranking input, schedule, or product
  value.

### Module and file boundary

- `com.wallstreetreceipts.api.domain.outcome.directionalwinorchestration` adds
  exactly four production files:
  `DirectionalWinOrchestrationPolicyVersion.java`,
  `DirectionalWinOrchestrationRequest.java`,
  `DirectionalWinOrchestrationResolution.java`, and
  `DirectionalWinOrchestrator.java`.
- The source-local test is exactly `DirectionalWinOrchestratorGoldenTest.java`
  in the matching package and contains exactly 84 runtime vectors: 15 general
  contract/correlation/PIT/replay checks, four directional source directions,
  six strict sign boundaries, all 55 unavailable combinations, and four
  neutral-precedence return states.
- ADR-021, `quality/P3_ACCEPTANCE.md`, README, this log, repository CI reverse
  allowlists, the dedicated guard, and API Surefire cardinality check own the
  exact boundary.

### Routes

- None. No controller, application service, provider, repository, scheduler,
  API response, canonical fixture adapter, or web route consumes or publishes
  the orchestration result.

### Verification

- Focused source-local golden: PASS, 84/84
  `DirectionalWinOrchestratorGoldenTest` vectors with zero failures, errors, or
  skips.
- Complete API Maven verification: PASS, 819/819 tests with zero failures,
  errors, or skips. PostgreSQL Testcontainers/Flyway, H2/Spring/API tests, and
  Spring Boot packaging all completed successfully.
- Repository CI contract validation: PASS. The workflow contains exactly 28
  embedded Python bodies; all 28 compile with optimized Python, all 27 locally
  executable bodies pass, and the final cross-stack body remains syntax-
  checked for execution by CI service jobs. The dedicated guard locks exact
  reverse edges, 84/84 Surefire cardinality with explicit nonzero mismatch
  exits, and both protected production baselines at 177 files / SHA-256
  `86d2175f849a3f866858c07351fbc24137946c4a286362f0557a9e7dc6b71bbf`.
- The six updated legacy reverse guards and the new dedicated guard pass. The
  three affected Java scanners preserve comment-like text inside string and
  character literals; their executable golden hashes are locked to the new
  scanner output.
- Workflow structure: PASS. SnakeYAML 2.5 parses exactly the four expected jobs
  (`repository-contracts`, `web`, `call-audit-integration`, and `api`).
- Compose configuration: PASS via `docker compose config --quiet`.
- Repository patch hygiene: PASS via `git diff --check` after the final edits.
- No web, browser, provider, or cross-stack result is claimed for this
  disconnected implementation.

### External-data boundary

- This source-local composition requires no API key, account, paid plan,
  domain, provider license, environment secret, or network access.
- Before non-DEMO evidence enters a runtime pipeline, P5 must select analyst-
  call, official-close, exchange-calendar, corporate-action, and asset/venue
  reference providers and establish storage, display, derived-data, and
  redistribution rights. Only then may a reviewed adapter introduce named,
  scoped secrets through approved local/CI secret stores; secrets must never be
  supplied in chat or committed to Git.

### Deferred boundary

- ADR-022 now adds only source-local directional-win readiness. A separate
  canonical lifecycle policy must still precede any promotion to Pending or
  Incomplete and any retry, cancellation, freshness, or scheduling behavior.
- Leaf-producer receipts, request-membership proofs, raw intraday aggregation,
  latest-correction/cancellation selection, canonical methodology activation,
  input fingerprinting, append-only outcome persistence, MFE/MAE, alpha/sector
  alpha, aggregation, ranking, scheduling, API/UI publication, and production
  provider integration remain later reviewed P3/P5 work.

## P3 — Supplied-leaf directional-win readiness classification

Status: implementation, API verification, and repository CI verification are
complete for one disconnected source-local readiness policy. The broader P3
scoring phase remains open with no canonical outcome lifecycle, retry,
scheduling, persistence, provider, API, or product publication.

### Scope

- Consume exactly two non-null fields: the readiness policy and one complete
  supplied ADR-021 `DirectionalWinOrchestrationResolution` using the exact
  required version and digest.
- Extract only the `AssetReturnResult` already preserved by the source. Invoke
  no polarity, routing, endpoint, price-pair, return, orchestration, or
  directional-win producer and construct no competing leaf evidence.
- Classify only ADR-021 `Available`, or neutral `NotApplicable` with an
  available asset-return leaf, as `Settled`.
- Classify only the exact nested chain `PRICE_PAIR_UNAVAILABLE` ->
  `ENDPOINT_PRICE_UNAVAILABLE` -> `ENDPOINT_NOT_REACHED_AS_OF` as
  `AwaitingEndpoint`.
- Classify every other unavailable chain as `EvidenceUnavailable`, including
  `BASIS_AND_ENDPOINT_PRICE_UNAVAILABLE` with a nested endpoint-not-reached
  reason. Preserve the complete source object on every branch.
- Add no canonical `CallOutcome` status, `dataComplete`, retry, freshness,
  cancellation, latest-correction, scheduling, methodology, fingerprint,
  persistence, aggregation, ranking, provider, API, or web behavior.

### Locked contract decisions

- ADR-022 owns `SUPPLIED_LEAF_DIRECTIONAL_WIN_READINESS_V1`, its exact 2353
  UTF-8 bytes, SHA-256
  `1eca77c5b4d43de7657281c161a8c50356cd90e1a18c6e9fd7f5b2c0142b7ec7`,
  exact two-field all-non-null request, three result variants, required ADR-021
  version/hash, branch precedence, nested endpoint-only chain, whole-source
  preservation, single classification owner, and no-lifecycle boundaries.
- Results are exactly `Settled(context,sourceResolution)`,
  `AwaitingEndpoint(context,sourceResolution)`, and
  `EvidenceUnavailable(context,sourceResolution)`. Context contains only the
  readiness policy identity; no branch stores a flattened reason or copied
  leaf.
- Settled means only that this supplied asset-return dependency is available.
  It does not claim that all outcome metrics exist, give neutral a directional-
  win Boolean, or make a canonical calculated/data-complete outcome.
- Awaiting requires all nested types and reasons to match the endpoint-only
  chain. Waiting for an endpoint cannot repair a missing basis price, so the
  compound basis-and-endpoint unavailable chain remains evidence-unavailable.
- Exact nested reason inspection is owned only by
  `DirectionalWinReadinessResolver`. Public result constructors delegate their
  locally decidable classification check to the same resolver rule so direct
  contradictory construction fails closed without duplicate decision logic.
- Equal supplied records replay equally, but this policy does not attest the
  original orchestration request, upstream producer execution, PIT candidate
  membership/filtering, market truth, provider freshness, retryability, or
  permanence.

### Module and file boundary

- `com.wallstreetreceipts.api.domain.outcome.directionalwinreadiness` adds
  exactly four production files:
  `DirectionalWinReadinessPolicyVersion.java`,
  `DirectionalWinReadinessRequest.java`,
  `DirectionalWinReadinessResolution.java`, and
  `DirectionalWinReadinessResolver.java`.
- The sole source-local test is exactly
  `DirectionalWinReadinessResolverGoldenTest.java` in the matching package and
  is required to execute exactly 118 invocations: six
  contract/null/shape/replay/determinism checks, all 55 unavailable chains in
  directional and neutral source shapes for 110 vectors, and two settled
  source shapes.
- ADR-022, `quality/P3_ACCEPTANCE.md`, README, this log, repository CI reverse
  allowlists, the dedicated guard, and the API Surefire cardinality step own
  the exact boundary.

### Routes

- None. No controller, application service, provider, repository, scheduler,
  API response, canonical fixture adapter, or web route consumes or publishes
  the readiness result.

### Verification

- Focused source-local golden: PASS, exactly 118/118
  `DirectionalWinReadinessResolverGoldenTest` invocations with zero failures,
  errors, or skips.
- Complete API Maven verification: PASS, exactly 937/937 tests with zero
  failures, errors, or skips. PostgreSQL Testcontainers/Flyway, H2/Spring/API
  tests, and Spring Boot packaging completed successfully.
- Repository CI contract validation: PASS. All 29 embedded Python bodies
  compile under optimized Python, all 28 locally executable bodies pass, and
  the final cross-stack body remains syntax-checked for workflow service
  execution. The dedicated readiness guard, both protected-production
  baselines at 181 files / SHA-256
  `4b295246194dfe1a60d6e37380ad398393e8951be070e6e82b7852b151909e8c`,
  six affected baseline/reverse/dependency guards, and the exact 118/118
  Surefire XML cardinality check pass.
- Workflow structure parsing: PASS via SnakeYAML 2.5 with exactly
  `repository-contracts`, `web`, `call-audit-integration`, and `api` jobs.
  `docker compose --env-file .env.example config --quiet` and final
  `git diff --check` also pass.
- No web, browser, provider, or cross-stack result is claimed for this
  disconnected implementation.

### External-data boundary

- This source-local classification requires no API key, account, paid plan,
  domain, provider license, environment secret, or network access.
- Before non-DEMO evidence enters a runtime pipeline, P5 must select analyst-
  call, official-close, exchange-calendar, corporate-action, and asset/venue
  reference providers and establish storage, display, derived-data, and
  redistribution rights. Only then may a reviewed adapter introduce named,
  scoped secrets through approved local/CI secret stores; secrets must never be
  supplied in chat or committed to Git.

### Deferred boundary

- ADR-022 readiness must not be mapped directly to
  `OutcomeEvaluationStatus`. A separate reviewed canonical lifecycle policy
  must define metric completeness, Pending/Incomplete mapping, retry/freshness,
  cancellation and latest-correction eligibility, and scheduling.
- Leaf-producer receipts, request-membership proofs, raw intraday aggregation,
  canonical methodology activation, input fingerprinting, append-only outcome
  persistence, MFE/MAE, benchmark/sector return evidence, alpha/sector alpha,
  aggregation, ranking, API/UI publication, and production provider
  integration remain later reviewed P3/P5 work.

## P3 — Supplied-leaf target-error readiness classification

Status: production implementation, focused source-local verification, complete
API verification, and repository CI verification are complete for the
eighteenth disconnected P3 contract. The broader P3 scoring phase remains open
with no canonical outcome lifecycle, retry, scheduling,
persistence, provider, API, or product publication.

### Scope

- Consume exactly two non-null fields: the readiness policy and one complete
  supplied ADR-015 `TargetErrorResult` using the exact required version and
  digest.
- Classify `TargetErrorResult.Available` as `Settled` without inferring that
  any other required metric exists.
- Classify only the exact nested chain `ENDPOINT_PRICE_UNAVAILABLE` ->
  `ENDPOINT_NOT_REACHED_AS_OF`, including the typed unavailable endpoint
  carrying the same reason, as `AwaitingEndpoint`.
- Classify every other constructible unavailable shape as
  `EvidenceUnavailable`. This includes
  `TARGET_AND_ENDPOINT_PRICE_UNAVAILABLE` with endpoint-not-reached because
  waiting for the close cannot repair the missing target.
- Preserve the exact complete source object on every branch. Add no flattened
  reason, reconstructed endpoint evidence, producer replay, or calculator
  invocation.
- Add no canonical `CallOutcome` status, `dataComplete`, retry, freshness,
  cancellation, latest-correction, scheduling, methodology, fingerprint,
  persistence, aggregation, ranking, provider, API, or web behavior.

### Locked contract decisions

- ADR-023 owns `SUPPLIED_LEAF_TARGET_ERROR_READINESS_V1`, its exact 1979 UTF-8
  bytes, SHA-256
  `0b8bfb22dccd4a494f568c44d06163f73af36462cf929bc83cf238019811c44a`,
  exact two-field all-non-null request, three result variants, required ADR-015
  version/hash, branch precedence, exact endpoint-only chain, whole-source
  preservation, single classification owner, and no-lifecycle boundaries.
- Results are exactly `Settled(context,sourceResult)`,
  `AwaitingEndpoint(context,sourceResult)`, and
  `EvidenceUnavailable(context,sourceResult)`. Context contains only the
  readiness policy identity; no branch stores a flattened reason or copied
  endpoint leaf.
- The complete constructible matrix contains exactly 40 shapes: one settled,
  one awaiting, and 38 evidence-unavailable. The 39 unavailable shapes comprise
  16 endpoint-only reasons, 16 compound target-and-endpoint reasons, and seven
  reasons requiring a resolved endpoint.
- Exact reason inspection is owned only by `TargetErrorReadinessResolver`.
  Public result constructors delegate their locally decidable classification
  check to the same resolver rule so directly constructed contradictory
  variants fail closed without duplicated reason logic.
- Equal supplied records replay equally, but this policy does not attest the
  original target-error input, PIT candidate membership/filtering, selector or
  calculator execution, market truth, provider freshness, retryability, or
  permanence.

### Module and file boundary

- `com.wallstreetreceipts.api.domain.outcome.targeterrorreadiness` adds exactly
  four production files: `TargetErrorReadinessPolicyVersion.java`,
  `TargetErrorReadinessRequest.java`,
  `TargetErrorReadinessResolution.java`, and
  `TargetErrorReadinessResolver.java`.
- The sole source-local test is exactly
  `TargetErrorReadinessResolverGoldenTest.java` in the matching package and is
  required to execute exactly 46 invocations: six contract/null/shape/direct-
  construction/replay/determinism checks, all 39 unavailable shapes, and one
  settled shape.
- ADR-023, `quality/P3_ACCEPTANCE.md`, README, this log, repository CI reverse
  allowlists, the dedicated guard, and the API Surefire cardinality step own the
  exact boundary.

### Routes

- None. No controller, application service, provider, repository, scheduler,
  API response, canonical fixture adapter, or web route consumes or publishes
  the readiness result.

### Verification

- Focused source-local golden: PASS, exactly 46/46
  `TargetErrorReadinessResolverGoldenTest` invocations with zero failures,
  errors, or skips.
- Complete API Maven verification: PASS, exactly 983/983 tests with zero
  failures, errors, or skips. PostgreSQL Testcontainers/Flyway, H2/Spring/API
  tests, and Spring Boot packaging completed successfully.
- Repository CI contract validation: PASS. The finalized workflow contains 30
  embedded Python bodies; all 30 compile under optimized Python and all 29
  locally executable bodies pass. The dedicated ADR-023 guard, affected
  endpoint/target reverse guards, exact 46/46 Surefire XML cardinality check,
  and both protected-production baselines at exactly 185 files / SHA-256
  `4a1479312db7053a476ae4b982df3f13aad570332baef4489e03d039cd49114a`;
  all pass.
- No web, browser, provider, network, or cross-stack result is claimed for this
  disconnected implementation.

### External-data boundary

- This source-local classification requires no API key, account, paid plan,
  domain, provider license, environment secret, or network access.
- Before non-DEMO evidence enters a runtime pipeline, P5 must select analyst-
  call, official-close, exchange-calendar, corporate-action, and asset/venue
  reference providers and establish storage, display, derived-data, and
  redistribution rights. Only then may a reviewed adapter introduce named,
  scoped secrets through approved local/CI secret stores; secrets must never be
  supplied in chat or committed to Git.

### Deferred boundary

- ADR-023 readiness must not be mapped directly to
  `OutcomeEvaluationStatus`. A future reviewed canonical lifecycle policy must
  consider completeness across all 10 required metrics before defining
  Pending/Incomplete mapping, retry/freshness, cancellation and latest-
  correction eligibility, or scheduling.
- Leaf-producer receipts, request-membership proofs, raw intraday aggregation,
  canonical methodology activation, input fingerprinting, append-only outcome
  persistence, MFE/MAE, benchmark/sector return evidence, alpha/sector alpha,
  aggregation, ranking, API/UI publication, and production provider
  integration remain later reviewed P3/P5 work.

## P3 — Supplied-leaf target-hit readiness classification

Status: production implementation, focused source-local verification, complete
API verification, and repository CI verification are complete for the
nineteenth disconnected P3 contract. The broader P3 scoring phase remains open
with no canonical outcome lifecycle, retry, scheduling, persistence, provider,
API, or product publication.

### Scope

- Consume exactly two non-null fields: the readiness policy and one complete
  supplied ADR-020 `TargetHitOrchestrationResolution` using its exact required
  policy version and digest.
- Classify the single `Available` shape and all three permanent
  `NotApplicable` reasons as `Settled`. Non-applicability remains an intentional
  finished metric meaning; it is not converted to `false`, a loss, or missing
  evidence and does not imply that another metric exists.
- Classify only the typed ADR-020 `Pending` branch as `AwaitingEndpoint`. Its
  preserved target-eligibility leaf can carry only
  `HORIZON_NOT_REACHED_AS_OF` and already enforces the unreached endpoint;
  readiness does not duplicate or reinterpret that reason.
- Classify all 14 `EligibilityUnavailable` and all 22
  `FavorableExtremeUnavailable` shapes as `EvidenceUnavailable`, without
  inferring retryability, temporality, permanence, or provider health from a
  nested reason.
- Preserve the exact complete ADR-020 source object on every branch. Add no
  flattened reason, copied leaf, reconstructed evidence, producer replay,
  eligibility resolver, favorable-extreme selector, target-hit orchestrator,
  or calculator invocation.
- Add no canonical `CallOutcome` status, `dataComplete`, retry, freshness,
  cancellation, latest-correction, scheduling, methodology, fingerprint,
  persistence, aggregation, ranking, provider, API, or web behavior.

### Locked contract decisions

- ADR-024 owns `SUPPLIED_LEAF_TARGET_HIT_READINESS_V1`, its exact 2042 UTF-8
  bytes, SHA-256
  `8f81dee5227370d82dd91cd2fb8448797c7028eaa485dc64cf4bdc3cbf2f31a3`,
  exact two-field all-non-null request, three result variants, required ADR-020
  version/hash, branch precedence, whole-source preservation, shared
  classification validation, and no-lifecycle boundaries.
- Results are exactly `Settled(context,sourceResult)`,
  `AwaitingEndpoint(context,sourceResult)`, and
  `EvidenceUnavailable(context,sourceResult)`. Context contains only the
  readiness policy identity; no branch stores an outer reason or duplicate
  nested leaf.
- The complete constructible matrix contains exactly 41 shapes: four settled
  (one Available plus three NotApplicable), one awaiting Pending, and 36
  evidence-unavailable (14 eligibility plus 22 favorable extreme).
- Classification switches only on ADR-020's sealed top-level variants in this
  exact order: Available, NotApplicable, Pending, EligibilityUnavailable, and
  FavorableExtremeUnavailable. Public result constructors delegate their
  locally decidable check to the same resolver rule so contradictory direct
  variants fail closed.
- Equal supplied records replay equally, but this policy does not attest the
  original orchestration request, PIT candidate membership/filtering, selector
  or calculator execution, market truth, provider freshness, retryability, or
  permanence.

### Module and file boundary

- `com.wallstreetreceipts.api.domain.outcome.targethitreadiness` adds exactly
  four production files: `TargetHitReadinessPolicyVersion.java`,
  `TargetHitReadinessRequest.java`,
  `TargetHitReadinessResolution.java`, and
  `TargetHitReadinessResolver.java`.
- The sole source-local test is
  `TargetHitReadinessResolverGoldenTest.java` in the matching package and must
  execute exactly 47 invocations: six fixed policy/contract/null/shape/direct-
  construction/replay/determinism checks plus all 41 classification shapes.
- ADR-024, `quality/P3_ACCEPTANCE.md`, README, this log, repository CI reverse
  allowlists, the dedicated guard, and the API Surefire cardinality step own the
  exact boundary.

### Routes

- None. No controller, application service, provider, repository, scheduler,
  API response, canonical fixture adapter, or web route consumes or publishes
  the readiness result.

### Verification

- Focused source-local golden: PASS, exactly 47/47
  `TargetHitReadinessResolverGoldenTest` invocations with zero failures, errors,
  or skips.
- Complete API Maven verification: PASS, exactly 1030/1030 tests with zero
  failures, errors, or skips. PostgreSQL Testcontainers/Flyway, H2/Spring/API
  tests, and Spring Boot packaging completed successfully.
- Repository CI contract validation: PASS. The finalized workflow contains 31
  embedded Python bodies; all 31 compile under optimized Python and all 30
  locally executable bodies pass. The dedicated ADR-024 guard, affected
  target-hit orchestration reverse guard, exact 47/47 Surefire XML cardinality
  check, and both protected-production baseline guards at exactly 189 files /
  SHA-256
  `bc251da006f897de69744ee8aec2400da5d18c38c2945aac03ec46063cc18721`
  all pass.
- No web, browser, provider, network, or cross-stack result is claimed for this
  disconnected implementation.

### External-data boundary

- This source-local classification requires no API key, account, paid plan,
  domain, provider license, environment secret, or network access.
- Before non-DEMO evidence enters a runtime pipeline, P5 must select entitled
  analyst-call, historical intraday or tick, official-close, exchange-calendar,
  corporate-action, and asset/venue reference providers and establish storage,
  display, derived-data, and redistribution rights. Only then may a reviewed
  adapter introduce named, scoped secrets through approved local/CI secret
  stores; secrets must never be supplied in chat or committed to Git.

### Deferred boundary and post-ADR-024 order

- ADR-024 readiness must not be mapped directly to
  `OutcomeEvaluationStatus`. A future reviewed canonical lifecycle policy must
  consider all 10 required metrics, intentional non-applicability, cancellation,
  and latest-correction eligibility before defining Pending/Incomplete mapping,
  retry/freshness, or scheduling.
- First decide explicitly whether ADR-022 is the combined asset-return and
  directional-win readiness receipt or whether a separate asset-return receipt
  is required. ADR-024 makes no cross-metric ownership inference.
- Before benchmark or sector return contracts are implemented, request and
  receive explicit product approval for benchmark identity, sector taxonomy and
  point-in-time membership, price-versus-total-return basis, currency, venue,
  and corporate-action treatment. Existing fixtures must not be used to invent
  those financial meanings.
- After that approval, add benchmark/sector point-in-time evidence, return
  calculations, and readiness. Raw intraday/tick coverage semantics must precede
  MFE/MAE; alpha and sector alpha remain last, after benchmark/sector identity
  and corporate-action-adjusted return policy are fixed.
- Leaf-producer receipts, request-membership proofs, canonical methodology
  activation, input fingerprinting, append-only outcome persistence,
  aggregation, ranking, API/UI publication, and production provider integration
  remain later reviewed P3/P5 work.
