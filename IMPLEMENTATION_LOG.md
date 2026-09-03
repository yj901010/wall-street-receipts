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

### Deferred boundary and post-ADR-025 order

- ADR-024 readiness must not be mapped directly to
  `OutcomeEvaluationStatus`. A future reviewed canonical lifecycle policy must
  consider all 10 required metrics, intentional non-applicability, cancellation,
  and latest-correction eligibility before defining Pending/Incomplete mapping,
  retry/freshness, or scheduling.
- ADR-025 now assigns asset-return and directional-win readiness to one shared
  ADR-022 receipt. A future aggregate must consume it once while accounting for
  both metric meanings; no separate asset-return readiness receipt is planned.
- ADR-026 subsequently records the received product approval for benchmark
  identity, sector taxonomy and point-in-time membership, price-return basis,
  currency, venue, and index-continuity treatment. Existing fixtures must not
  be used to invent those financial meanings.
- Following ADR-026, add benchmark/sector point-in-time evidence, return
  calculations, and readiness. Raw intraday/tick coverage semantics must precede
  MFE/MAE; alpha and sector alpha remain last, after benchmark/sector reference
  identity and index-continuity price-return policy are fixed.
- Leaf-producer receipts, request-membership proofs, canonical methodology
  activation, input fingerprinting, append-only outcome persistence,
  aggregation, ranking, API/UI publication, and production provider integration
  remain later reviewed P3/P5 work.

## P3 — Shared asset-return and directional-win readiness ownership

Status: complete. The cross-metric ownership decision, documentation,
repository guard, and full API regression are verified. This slice adds no
executable policy or production/test code.

### Scope

- ADR-022 remains the sole shared receipt for asset-return and directional-win readiness.
- Consume the exact complete `DirectionalWinReadinessResolution` once in a
  future canonical aggregate while accounting for both metric meanings.
- Preserve ADR-022's exact 2353-byte policy definition, SHA-256
  `1eca77c5b4d43de7657281c161a8c50356cd90e1a18c6e9fd7f5b2c0142b7ec7`,
  public types, branch mapping, source-local test, and result semantics.
- For directional ADR-021 `Available`, project the exact preserved asset-return
  decimal and directional-win Boolean. For neutral `NotApplicable` with an
  available return, project the exact return and intentional directional non-
  applicability without manufacturing `false`.
- A directional unavailable return leaves both values unresolved. Neutral
  `NotApplicable` with an unavailable return keeps directional win intentionally
  not applicable while asset return remains awaiting or evidence-unavailable;
  the shared receipt therefore remains unsettled.
- Add no standalone `assetreturnreadiness` package, policy, request, result,
  resolver, alias, facade, or golden test. The existing ADR-017 asset-return
  calculator/result surface remains unchanged.

### Locked ownership decisions

- ADR-025 is a documentation and ownership contract, not a new versioned
  classifier. It has no canonical policy definition, hash, resolver invocation,
  or runtime result.
- A complete future aggregate has 10 canonical metric meanings and nine
  readiness ownership inputs: one shared ADR-022 receipt covers asset return
  and directional win, while the other eight metric meanings retain distinct
  ownership. Only ADR-022 shared readiness, ADR-023 target-error readiness, and
  ADR-024 target-hit readiness exist today; the remaining inputs are deferred.
- The aggregate may inspect the preserved ADR-021 top-level variant to extract
  settled values or intentional non-applicability. It must not re-run nested
  reason classification, select another return, invoke upstream producers,
  flatten evidence, or duplicate the shared receipt as independently mutable
  inputs.
- `Settled` is necessary only for the two owned metric meanings and cannot prove
  whole-outcome completeness. `AwaitingEndpoint` cannot alone prove canonical
  pending/retry state, and `EvidenceUnavailable` cannot alone prove canonical
  incomplete/permanent state.

### Module and route boundary

- The only new artifact is
  `decisions/ADR-025-shared-asset-return-directional-win-readiness-ownership.md`
  plus updates to README, P3 acceptance, this log, and repository ownership
  validation. No Java package, test, schema, fixture, manifest, OpenAPI, Flyway,
  database, controller, provider, API, resource, or web route is added.
- Existing ADR-022 implementation and its exact 118-invocation golden remain
  unchanged. No focused golden belongs to this decision-only slice.

### Lifecycle and product/data firewall

- No direct `Settled` to `CALCULATED`, `AwaitingEndpoint` to `PENDING`, or
  `EvidenceUnavailable` to `INCOMPLETE` mapping is added. A later reviewed
  policy must compose all 10 metric meanings, intentional non-applicability,
  cancellation/latest-correction eligibility, methodology identity, input
  fingerprinting, and freshness/scheduling rules.
- No `CallOutcome` or `dataComplete` mutation, methodology activation,
  persistence, aggregation, ranking, scheduling, API/UI publication, provider
  behavior, or market-data observation is introduced.

### Verification

- Documentation consistency and repository patch hygiene: **PASS**. The exact
  ownership marker occurs once in ADR-025, README, P3 acceptance, and this log;
  `git diff --check` is clean.
- Complete API Maven regression: **PASS**. `./mvnw.cmd -B -ntp verify` ran
  1030 tests with 0 failures, 0 errors, and 0 skipped, then completed the build.
- Repository CI ownership guard and affected reverse guards: **PASS**. All 31
  embedded Python bodies compile under optimization and all 30 locally
  executable bodies pass; the final cross-stack body remains syntax-checked
  for workflow service execution.
- The existing ADR-022 118/118 golden contract is unchanged; this decision-only
  slice correctly adds no focused golden. The protected-production baseline
  remains exactly 189 files with SHA-256
  `bc251da006f897de69744ee8aec2400da5d18c38c2945aac03ec46063cc18721`,
  and the workflow remains the exact four-job structure.
- Compose configuration validation and workflow YAML parsing: **PASS**. No web,
  browser, provider, network, or live cross-stack result is claimed.

### External-data boundary

- This ownership decision requires no API key, account, paid plan, domain,
  provider license, environment secret, or network access.
- Before non-DEMO evidence enters a runtime pipeline, P5 must select entitled
  analyst-call, historical intraday or tick, official-close, exchange-calendar,
  corporate-action, and asset/venue reference providers and establish storage,
  display, derived-data, and redistribution rights. Scoped secrets may be
  introduced only through approved local/CI secret stores, never chat or Git.

### Deferred boundary

- ADR-026 subsequently records the received product approval for benchmark
  identity, sector taxonomy and point-in-time membership, price-return basis,
  currency, venue, and index-continuity treatment.
- Following ADR-026, benchmark/sector evidence, calculation, and readiness
  precede raw-window coverage and MFE/MAE. Alpha and sector alpha remain last.
- Canonical lifecycle composition, methodology activation, input
  fingerprinting, append-only persistence, aggregation, ranking, API/UI
  publication, scheduling, and production provider integration remain later
  reviewed work.

## P3 — Point-in-time comparative reference-return foundation

Status: complete. Product approval for the comparative benchmark/sector
foundation is recorded and the documentation, repository guard, and full API
regression are verified. This slice is documentation-only and adds no
executable policy, canonical definition/hash, production code, or golden test.

### Scope

- ADR-026 locks benchmark and sector returns to explicit point-in-time reference assignments.
- Keep benchmark and sector assignments independently typed and bind each to
  the exact original/correction `OutcomeBasis` at `basis.eventTime`.
- Require explicit source-revised asset classification, primary venue and
  venue-country, currency, effective-interval, PIT timestamp, provider-event,
  and provenance evidence. Current/latest membership and ticker-based matching
  are absent.
- Limit benchmark V1 to explicitly evidenced `AssetType.EQUITY`, primary-venue
  country ISO `US`, and currency ISO `USD`; require one explicit visible
  `asset-spx` / `AssetType.INDEX` provider-published price-index assignment.
  Missing expected evidence is unavailable; visible coherent out-of-scope
  classification is intentionally not applicable.
- Require a provider-neutral versioned WSR taxonomy and explicit provider
  mappings before sector assignment exists. P2 synthetic labels make no
  taxonomy, index-membership, GICS, or ICB claim.
- Lock later reference evidence to exact source-recorded price-index levels over
  the shared basis-event-to-asset-endpoint UTC interval, exact currency/no FX,
  reference-specific venue/calendar/source revisions, and explicit divisor
  continuity.

### Locked foundation decisions

- Assignment effective intervals are start-inclusive/end-exclusive and contain
  the exact basis event. Open-ended membership must be explicit. A correction
  is a separate basis; assignment never floats to the horizon-end membership.
- Evidence is visible only when both `availableAt` and `capturedAt` are not
  after `evaluationAsOf`. Future exact, invalid, or duplicate evidence is
  invisible to identity, reason, ambiguity, and output.
- Exactly one valid visible assignment may resolve. Equal duplicates remain
  ambiguous, and known invalid evidence fails closed under a later fixed
  precedence. No deduplication, fallback, nearest interval, current-row lookup,
  provider preference, or inference is permitted.
- An in-scope equity is never automatically assigned to `asset-spx`.
  `MarketSnapshot.spx`, ticker/name, current master data, P2 universe/map data,
  and DEMO presentation labels are not evidence.
- Sector return uses an assigned provider-published sector price index, not an
  ETF, current basket, market-cap proxy, treemap aggregate, or provider return.
- Reference price-index continuity receives dedicated evidence/types. The
  asset split/reverse-split share-basis enums from ADR-014/ADR-016 are not
  reused or relabelled.
- Future benchmark and sector calculators keep separate semantic input/result
  types. They must use ADR-017's exact one-subtraction, one scale-12
  `HALF_EVEN`-division signed price-return arithmetic with no intermediate or
  second rounding, and do not cast to `AssetReturnResult` or accept provider-
  calculated output.

### Module and route boundary

- The new decision artifact is
  `decisions/ADR-026-point-in-time-comparative-reference-return-foundation.md`,
  accompanied by README, P3 acceptance, this log, and repository-validation
  updates.
- This foundation adds zero Java production files and zero tests. It adds no
  package, policy enum, canonical bytes/digest, schema, canonical fixture,
  manifest member, OpenAPI path, Flyway migration, database behavior,
  controller, repository, provider adapter, API/resource behavior, or web
  source.
- Existing model-only and DEMO `benchmarkReturn`, `sectorReturn`, `alpha`, and
  `sectorAlpha` values remain null. No observed or calculated result is claimed.

### Lifecycle and external-data firewall

- Typed future assignment states distinguish available assignment, intentional
  non-applicability, and evidence unavailability. None directly establishes
  `OutcomeEvaluationStatus`, `dataComplete`, retryability, permanence,
  cancellation, latest-correction, freshness, scheduling, or publication.
- This decision requires no API key, account, paid plan, domain, provider
  license, environment secret, or network access.
- Before non-DEMO evidence is connected, P5 must select entitled asset/venue
  classification, index-level, sector taxonomy/membership, exchange-calendar,
  and continuity sources and establish storage, display, derived-data, and
  redistribution rights. Named scoped secrets may be introduced only after
  that selection through approved local/CI/deployment secret stores, never
  chat or Git.

### Active staged order

1. Implement independently typed benchmark assignment evidence, PIT selection,
   applicability, policy definition/hash, and golden tests.
2. Define/version the closed WSR taxonomy and explicit provider mappings, then
   implement basis-frozen sector assignment.
3. Add independent benchmark and sector reference-level pairs with exact UTC
   interval, price-return basis, no FX, explicit reference identities, and
   divisor-continuity proof.
4. Add separate deterministic return calculators and source-local readiness.
5. Keep DEMO outcomes null until dedicated evidence, methodology,
   fingerprinting, lineage, and completeness contracts exist. Add raw-window
   coverage before MFE/MAE; add alpha/sector alpha last; compose lifecycle only
   after all ten metric meanings have reviewed ownership.

### Verification

- Exact four-document marker parity, ADR/README/acceptance/log consistency,
  decision-only surface, forbidden-inference wording, and staged order:
  **PASS**. The exact ADR-026 marker occurs once in each required document.
- Full API regression, repository CI validation, workflow YAML parsing, Compose
  validation, and `git diff --check`: **PASS**. `./mvnw.cmd -B -ntp verify`
  ran 1030 tests with zero failures, errors, or skips and completed the build;
  all 31 embedded Python bodies compile under optimization and all 30 locally
  executable bodies pass; SnakeYAML parses the exact four jobs; Compose
  configuration is valid; and patch hygiene is clean.
- The decision-only slice correctly adds no focused golden or runtime package.
  The protected production baseline remains exactly 189 files with SHA-256
  `bc251da006f897de69744ee8aec2400da5d18c38c2945aac03ec46063cc18721`.
- The API-test plus application-owned web source/config baseline remains
  exactly 197 files with SHA-256
  `12fb3dbacd830f86ca0790284e2a2833d0314bcf56291337d7698d40720ca45d`.
  A temporary sector-index-golden plus web-taxonomy mutation pair was rejected
  with the required nonzero exit, then removed before final verification.

## P3 — Point-in-time explicit benchmark assignment V1

Status: production implementation, focused source-local verification, full API
verification, and repository CI/YAML/Compose/mutation verification are complete
for the twentieth disconnected P3 contract. The broader P3 scoring phase
remains open with no reference level, comparative return, canonical lifecycle,
persistence, provider, API, or product publication.

ADR-027 selects benchmark assignment only from explicit point-in-time evidence frozen at the outcome basis event.

### Scope

- Consume exactly the V1 policy, one complete original/correction
  `OutcomeBasis`, canonical asset ID, microsecond `evaluationAsOf`, and immutable
  complete classification and assignment candidate lists.
- Preserve provider-event/evidence identity, source/revision/provenance,
  asset/type, primary venue and sourced ISO country, ISO currency, explicit
  effective interval, PIT timestamps, and benchmark mapping semantics.
- Remove evidence with either PIT timestamp after `evaluationAsOf` before all
  identity, applicability, reason, and cardinality gates. Future invalid or
  duplicate records are indistinguishable from absence and are never echoed.
- Select classification at the exact basis event with start-inclusive/end-
  exclusive membership and an explicit sealed open-ended value. Original and
  correction bases remain independent; membership never floats to current or
  horizon-end state.
- Resolve only one coherent US/USD equity mapping to `asset-spx`, INDEX, USD,
  and provider-published price-index semantics. Preserve typed intentional
  non-applicability and fail closed on missing, conflicting, invalid, or
  ambiguous expected evidence.
- Add no provider read, current-master/ticker/UI inference, reference level,
  return calculation, canonical outcome status, persistence, schema, fixture,
  database, API, or web behavior.

### Locked contract decisions

- ADR-027 owns policy
  `POINT_IN_TIME_EXPLICIT_US_EQUITY_ASSET_SPX_ASSIGNMENT_V1`, its exact 4261
  single-line ASCII/UTF-8 bytes, and SHA-256
  `7318514c2f50eda16b2d7ef35bc68d00d6a8b18a0f09f77130525fca2f32da69`.
  Every result context echoes the digest and definition byte reads are
  defensive.
- Result variants are exactly `Resolved`, `NotApplicable`, and `Unavailable`.
  The four N/A reasons and 19 unavailable reasons follow ADR-027's exact order;
  missing assignment is N/A only for a coherent out-of-scope classification
  with no visible mapping.
- Every visible mismatch poisons the candidate set at its fixed gate. Equal
  duplicates remain ambiguous, known mismatch precedes ambiguity, candidate
  order is irrelevant, and no filter-to-valid, deduplication, current/latest
  row, provider preference, nearest interval, or fallback exists.
- Public resolved/N/A constructors validate locally decidable context/evidence
  consistency. Only `BenchmarkAssignmentSelector` attests request membership,
  PIT filtering, all-candidate precedence, and cardinality.

### Module and file boundary

- `com.wallstreetreceipts.api.domain.outcome.benchmarkassignment` contains
  exactly six production files:
  `BenchmarkAssignmentPolicyVersion.java`,
  `BenchmarkAssetClassificationEvidence.java`,
  `BenchmarkAssignmentEvidence.java`, `BenchmarkAssignmentRequest.java`,
  `BenchmarkAssignmentResolution.java`, and
  `BenchmarkAssignmentSelector.java`.
- The matching source-local test surface contains only
  `BenchmarkAssignmentSelectorGoldenTest.java`, with exactly 84 invocations
  across 31 test methods.
- ADR-027, README, P3 acceptance, this log, the dedicated CI guard, the strict-
  close `OutcomeBasis` reverse allowlist, protected baseline guards, and the API
  Surefire cardinality step own the exact boundary.

### Routes and publication

- None. No controller, application service, provider, repository, scheduler,
  API response, canonical fixture adapter, resource, or web route consumes or
  publishes the resolution.
- Existing model-only and DEMO `benchmarkReturn`, `sectorReturn`, `alpha`, and
  `sectorAlpha` remain null. Assignment evidence is neither a price level nor a
  return or methodology claim.

### Verification

- Focused source-local golden: **PASS**, exactly 84/84 tests with zero failures,
  errors, or skips.
- Complete API Maven verification: **PASS**, exactly 1114/1114 tests with zero
  failures, errors, or skips; Spring Boot repackage completed.
- Repository CI guard and all embedded Python checks: **PASS**. All 32 bodies
  compile under optimized Python, all 31 locally executable bodies pass, and
  the final cross-stack body remains syntax-checked for workflow service
  execution. The exact ADR-027 marker mutation exits nonzero and is restored.
- Workflow YAML, Compose, and patch hygiene: **PASS**. SnakeYAML parses the
  exact four jobs, Compose configuration is valid, and `git diff --check` is
  clean.
- App-owned web regression: **PASS**. ESLint passes, Vitest runs 569/569 tests
  across 42 files, and the Next production build emits the expected 12 static
  pages and routes without adding benchmark-assignment web behavior.
- The finalized source surface currently produces a 195-file protected
  production baseline at SHA-256
  `562e6402b06c4b549d518b5935d7c6525d795708d135bb4c8dd4af8c674d0640`
  and a 198-file API-test plus application-owned web baseline at SHA-256
  `0f6c5358ea2564c562159d375b42985e8aafd603b1673fcc404aab83bcf74a0e`.
  Excluding exactly the ADR-027 six production files and one golden reproduces
  the ADR-026-era 189/197 counts and both historical digests.

### External-data and next-decision boundary

- This disconnected policy requires no API key, account, paid plan, domain,
  provider license, environment secret, or network access.
- Before non-DEMO evidence enters it, P5 must select entitled classification,
  venue-country, currency, and benchmark-mapping providers and establish
  storage, display, derived-data, and redistribution rights. Named scoped
  secrets may then be introduced only through approved local/CI/deployment
  stores, never chat or Git.
- ADR-028 records the received provider-neutral WSR sector-taxonomy product
  decision: exact identity/version, canonical bytes/hash, closed node IDs, and
  provider-to-canonical mapping semantics. Basis-frozen sector assignment is
  the next separately reviewed implementation slice.

## P3 — Provider-neutral WSR Economic Activity Taxonomy V1

Status: product approval is received and the twenty-first disconnected P3
contract is implemented as a decision-only taxonomy and mapping-policy slice.
Repository verification is complete. No sector assignment, provider mapping
set, reference index, return, lifecycle, persistence, provider, API, or product
publication is introduced.

ADR-028 locks WSR Economic Activity V1 and exact point-in-time provider-node mapping semantics.

### Scope

- Define `wsr-economic-activity` version `1.0.0` as one unassignable root and
  exactly twelve ordered, assignable, single-level WSR economic-activity nodes.
  Preserve original WSR IDs, English labels, and definitions without claiming
  GICS, ICB, SIC, or NAICS equivalence.
- Keep missing, conflicting, ambiguous, future, unsupported, and unmapped
  evidence outside the taxonomy. There is no `UNKNOWN`, `OTHER`, or
  unclassified node. Diversified Operations requires affirmative evidence that
  no other V1 node represents one primary activity and is never a fallback.
- Define exact provider-node identity, mapping evidence, closed disposition,
  PIT visibility, effective interval, multiplicity, mapping-set
  canonicalization, versioning, and no-inference semantics without inventing an
  actual provider mapping row.
- Add only ADR-028, README/P3/log documentation, and CI validation. Preserve
  every Java, test, schema, fixture, manifest, OpenAPI, Flyway, resource,
  provider, database, API, and web runtime surface.

### Locked taxonomy and mapping decisions

- The exact canonical taxonomy definition is one 3824-byte single-line
  ASCII/UTF-8 sequence with SHA-256
  `820ce3ea264d67312fe4f2efe346631a81d74248e9a7f041793d65d8ef0d62ae`.
  Any node, order, label, definition, or semantic change requires a new
  taxonomy version and hash.
- Mapping policy
  `POINT_IN_TIME_EXPLICIT_PROVIDER_NODE_TO_WSR_ECONOMIC_ACTIVITY_V1` is bound
  to that exact taxonomy ID/version/hash. Its canonical definition is one
  4395-byte single-line ASCII/UTF-8 sequence with SHA-256
  `ba12a277d5ffe266af1745b98948a1e2206494ac31904f31a419d973d5067e77`.
- Provider identity is the exact provider, scheme, scheme revision, and node ID
  under case-sensitive, unnormalized Unicode code-point equality. Labels and
  definitions remain preserved evidence, not matching keys. `Mapped` requires
  a recorded provider definition and an exact closed assignable leaf target
  under the locked taxonomy hash.
- Mapping rows preserve 23 ordered fields spanning evidence/provider event,
  policy/mapping-set/taxonomy identity and hashes, provider identity and source
  label/definition, disposition, source/revision/provenance, interval, and both
  PIT timestamps. Missing or closed `NotMapped` dispositions remain evidence
  unavailable.
- Visibility requires `availableAt <= evaluationAsOf` and
  `capturedAt <= evaluationAsOf`; selection uses a start-inclusive/end-
  exclusive interval at the exact original/correction `basis.eventTime`.
  Future rows cannot affect output, reasons, conflict, or cardinality.
- Equal duplicates and overlaps are ambiguous, disagreeing targets conflict,
  and current/latest row, nearest interval, provider preference, label match,
  silent deduplication/migration, P2 groupings, and fallback are forbidden.
- A future mapping set locks exact manifest/entry orders, globally unique
  evidence IDs, canonical UTC microsecond effective starts, unnormalized
  Unicode code-point sort, exact entry-to-manifest identity/source correlation,
  and SHA-256 with every self-referential `mappingSetDefinitionHash` occurrence
  omitted before all populated copies are set to the computed digest. No
  mapping set exists now.

### Source, license, and publication boundary

- P2 sector/industry strings remain synthetic DEMO presentation groupings and
  cannot seed taxonomy, provider mapping, asset membership, or reference-index
  assignment.
- GICS and ICB remain isolated unless written S&P/MSCI or FTSE/LSEG terms grant
  the required historical classification, storage, derived-crosswalk, cache,
  display, and redistribution uses. Public methodology or attribution pages do
  not create those entitlements.
- SEC SIC may later supply public issuer-classification evidence; SEC EDGAR
  data APIs need no API key but require compliant automated access. NAICS may
  inform an independently reviewed mapping but is establishment-oriented and
  cannot directly prove issuer membership.
- Current DEMO benchmark/sector/alpha metrics remain null. A WSR node does not
  prove a provider-published sector price index, and no return or outcome status
  can be inferred from this decision.

### Module, routes, and data

- Production/test/module additions: none.
- Routes, OpenAPI, migrations, schemas, fixtures, manifest, database behavior,
  provider adapters, API resources, and web behavior: none.
- Canonical provider mapping entries and issuer memberships: none.

### Verification

- Canonical JSON parse/minimal serialization, independent UTF-8 byte/hash
  checks, exact node and mapping semantics, documentation marker parity,
  runtime firewall, and protected 195/198 baselines: **PASS**. The dedicated
  guard independently verifies the exact 3824/4395-byte definitions and both
  locked hashes.
- Complete API Maven regression, all embedded repository CI Python bodies,
  workflow YAML, Compose, canonical/marker mutation rejection, patch hygiene,
  and user-owned `apps/web/next-env.d.ts` preservation: **PASS**. Maven reports
  1114 tests with zero failures, errors, or skips. All 33 embedded Python bodies
  compile under optimization and all 32 locally executable bodies pass;
  SnakeYAML parses four jobs; Compose validates; marker, taxonomy-byte, and
  mapping-policy-byte mutations each fail closed and are restored; and
  `git diff --check` remains clean without staging the user-owned file.

### External-data and next-slice boundary

- This decision requires no API key, account, paid plan, domain, provider
  license, environment secret, or network access.
- Before real mapping or membership data enters the system, P5 must select a
  provider and document historical, storage, display, derived-crosswalk, cache,
  and redistribution rights. Only then may a reviewed adapter introduce named
  scoped credentials through untracked local/CI/deployment stores, never chat
  or Git.
- ADR-029 is the subsequent independently typed basis-frozen sector-assignment
  slice against these exact taxonomy and mapping-policy identities. Actual
  provider mapping data remains blocked until provider selection and rights
  approval.

## P3 — Point-in-time explicit WSR sector assignment V1

Status: the twenty-second disconnected P3 contract is complete with its exact
seven-file production policy surface and one 134-invocation golden. Focused and
full API, repository CI, YAML, Compose, mutation, baseline, and patch-hygiene
verification are **PASS**. No actual provider mapping set or data, reference
index, return, lifecycle, persistence, provider, API, database, or product
publication is introduced.

ADR-029 freezes WSR sector assignment to explicit point-in-time membership and mapped provider-node evidence.

### Scope

- Add an independently typed sector-assignment policy, classification evidence,
  provider membership evidence, reusable ADR-028 mapping evidence, immutable
  complete request, sealed resolution, and deterministic selector.
- Bind every request and result to one complete original/correction
  `OutcomeBasis`, canonical asset ID, microsecond `evaluationAsOf`, and
  caller-attested mapping-set ID/version/hash. Freeze membership and mapping at
  the exact `basis.eventTime`; originals and corrections never share or float
  current membership.
- Apply sector V1 only to explicitly classified equities. Preserve primary
  venue, sourced country, and currency evidence without using country/currency
  as scope. A coherent non-equity with no membership is the sole intentional
  `NON_EQUITY`; visible non-equity membership is a conflict.
- Require exact membership provider/scheme/revision/node identity and an exact
  ADR-028 mapping row using the locked taxonomy/mapping-policy hashes, a
  recorded provider definition, and one of the twelve assignable WSR leaves.
- Add no actual mapping set, issuer membership fixture, schema, manifest,
  OpenAPI path, migration, database behavior, provider adapter, API/resource,
  reference index, calculation, or web behavior.

### Locked policy decisions

- Policy
  `POINT_IN_TIME_EXPLICIT_WSR_ECONOMIC_ACTIVITY_SECTOR_ASSIGNMENT_V1` has one
  exact 9307-byte single-line ASCII/UTF-8 definition and SHA-256
  `52d9f705a3a8a965a6fca79d36bd94ed8836642f1a2c4e5f29a878d0a267311c`.
  Definition bytes are defensive and every resolution context echoes the
  digest.
- The policy requires ADR-028 taxonomy `wsr-economic-activity` version `1.0.0`
  and hash
  `820ce3ea264d67312fe4f2efe346631a81d74248e9a7f041793d65d8ef0d62ae`,
  its exact twelve ordered assignable nodes, and mapping-policy hash
  `ba12a277d5ffe266af1745b98948a1e2206494ac31904f31a419d973d5067e77`.
- Evidence is visible only when both PIT timestamps are not after
  `evaluationAsOf`. Future evidence cannot affect output, reason, conflict, or
  cardinality. All effective intervals are start-inclusive/end-exclusive at
  the exact basis event with an explicit open-ended variant.
- Exact provider identity uses case-sensitive unnormalized Unicode code-point
  equality. Provider labels and definitions are preserved evidence only;
  labels cannot match, and `Mapped` requires `Recorded` definition evidence.
- The exact result variants are `Resolved`, `NotApplicable`, and `Unavailable`.
  There is one N/A reason and exactly 36 unavailable reasons in ADR-029 order:
  five classification, ten membership, and twenty-one mapping reasons.
  Any visible known mismatch precedes multiplicity, unequal dispositions
  conflict before ambiguity, and any multiple visible rows with the same
  disposition are ambiguous—including distinct rows with the same mapped
  target or not-mapped reason. A single not-mapped row maps to its exact closed
  unavailable reason.
- The selector echoes and row-matches the caller-supplied mapping-set identity
  but does not calculate its manifest hash or attest full entry-to-manifest
  correlation. This contract therefore does not claim an actual mapping set.
- Ticker/name/current master, latest or nearest row, provider preference,
  label matching, silent deduplication, P2 labels, unknown/other/unclassified
  nodes, and fallback remain forbidden.

### Module and file boundary

`com.wallstreetreceipts.api.domain.outcome.sectorassignment` contains exactly:

- `SectorAssignmentPolicyVersion.java`
- `SectorAssetClassificationEvidence.java`
- `SectorMembershipEvidence.java`
- `SectorMappingEvidence.java`
- `SectorAssignmentRequest.java`
- `SectorAssignmentResolution.java`
- `SectorAssignmentSelector.java`

The matching source-local test surface contains only
`SectorAssignmentSelectorGoldenTest.java`, required to execute exactly 134
golden invocations. No benchmark-assignment type is imported or reused, and no
production type outside the package consumes this disconnected result.

### Routes, lifecycle, and publication

- None. No controller, application service, provider, repository, scheduler,
  schema, fixture, manifest, OpenAPI, Flyway, database, API, resource, or web
  surface consumes or publishes sector assignment.
- A resolved WSR node establishes no provider-published sector reference
  index, price level, divisor continuity, return, sector alpha, readiness,
  methodology activation, canonical outcome status, completeness, retry,
  persistence, aggregation, ranking, or publication. Existing DEMO
  comparative metrics remain null.

### Verification

- Exact canonical definition extraction, 9307-byte length, SHA-256, 36-reason
  order, seven-plus-one source surface, reverse isolation, and four-document
  marker parity: **PASS**.
- Focused `SectorAssignmentSelectorGoldenTest` 134/134 and full API Maven
  verification: **PASS** — full regression is 1248/1248 with zero failures,
  errors, or skips including Docker/PostgreSQL/Flyway integration.
- All repository CI Python bodies, workflow YAML, Compose, marker/policy/test-
  cardinality mutations, current protected baselines, ADR-028 195/198 legacy
  replay, `git diff --check`, and user-owned `apps/web/next-env.d.ts`
  preservation: **PASS** — 34/34 embedded bodies compile, 33/33 locally
  executable bodies pass, SnakeYAML parses exactly four jobs, Compose validates,
  all three mutations exit nonzero and are restored. Current production is
  exactly 202 files with SHA-256
  `b1ae60b9c550353960687cb9973e2909e965a5e3eb98bb23b39b0a7f01a2a899`;
  current API-test/web is 199 files with SHA-256
  `59726e88e5bf7d831f16beaa693689ca799990d355733a1115c07a285a7e5293`.
  Excluding the exact ADR-029 seven-plus-one surface reproduces ADR-028
  production at 195 files /
  `562e6402b06c4b549d518b5935d7c6525d795708d135bb4c8dd4af8c674d0640`
  and test/web at 198 files /
  `0f6c5358ea2564c562159d375b42985e8aafd603b1673fcc404aab83bcf74a0e`.
  The user-owned file remains unstaged and unchanged by this slice.

### External-data and next-slice boundary

- This disconnected source-local policy requires no API key, account, paid
  plan, domain, provider license, environment secret, or network access.
- No actual provider mapping set or non-DEMO membership may be created until P5
  selects a provider and documents historical classification, storage,
  display, derived-crosswalk, cache, and redistribution rights. GICS/ICB need
  express written commercial rights before credentials. Public SIC/NAICS use
  requires independent source, applicability, and crosswalk review; a future
  SEC adapter needs a compliant named `User-Agent` but no API key. Scoped
  credentials may then enter untracked local/CI/deployment stores only, never
  chat or Git.
- The next reviewed work is independent benchmark/sector reference-level pair
  evidence. A resolved WSR node alone cannot select a provider-published sector
  price index or make `sectorReturn` non-null.

## 2026-08-24 — ADR-030 point-in-time independent benchmark/sector reference-level pairs V1

Status: the production API and both canonical policy definitions are frozen for
the twenty-third disconnected P3 contract. Focused/full API, dedicated guard,
repository CI, protected current/legacy baselines, mutation, documentation,
YAML/Compose, and patch-hygiene verification are **PASS**. No provider identity,
reference-index definition, calendar, level, divisor, assignment/mapping row,
calculation, lifecycle, persistence, API, database, or web publication is
introduced.

ADR-030 resolves benchmark and sector reference-level pairs independently from explicit point-in-time provider-published price-index evidence over the exact basis-event-to-asset-endpoint UTC interval.

### Scope

- Add two fully independent, source-local policy surfaces: one consumes the
  complete ADR-027 benchmark-assignment resolution and one consumes the
  complete ADR-029 sector-assignment resolution. Each also consumes the full
  ADR-014 `EndpointPriceResolution`; neither receives or imports a direct
  `OutcomeBasis` or `SessionCloseHorizonResolution.Resolved` input.
- Select one provider-published price-index binding, exact basis and endpoint
  provider-published index-level observations, and exact divisor-continuity
  evidence for each leg. The benchmark and sector contracts do not share a
  generic abstraction, cast, selector, evidence model, or result type.
- Preserve both upstream receipts in every request and resolution context.
  Assignment basis and `evaluationAsOf` always agree with the endpoint receipt.
  Anchor validity is derived from catalog PIT, catalog-to-horizon
  calendar/revision, and binding PIT facts, never an upstream reason label.
  Only after those facts prove a usable anchor must asset agree; resolved or
  not-applicable classification venue/currency then agree with the endpoint
  binding. An unavailable assignment cannot prove classification venue or
  currency, so neither is inferred.
- Add no provider data, credentials, adapter, repository, controller, OpenAPI
  path, migration, schema, fixture, resource, database behavior, API response,
  web behavior, return calculation, readiness, or outcome lifecycle.

### Locked policy decisions

- Benchmark policy
  `POINT_IN_TIME_EXACT_BENCHMARK_PRICE_INDEX_LEVEL_PAIR_V1` has one exact
  9342-byte canonical UTF-8 definition and SHA-256
  `2394b535c1061d32c647504a303b6f1e4ec2fe88e6017d9ff335d12087a5f73d`.
- Sector policy `POINT_IN_TIME_EXACT_SECTOR_PRICE_INDEX_LEVEL_PAIR_V1` has one
  exact 9806-byte canonical UTF-8 definition and SHA-256
  `4224648ba01104fd3e96319158c7d6b42da472e9aa6f2ab22ef9fccf43da7e4a`.
  Both definitions, ordered fields, reason order, and receipt topology are
  independently versioned; a change requires a new reviewed policy version.
- Each result is exactly one of `Resolved`, `NotApplicable`,
  `AssignmentUnavailable`, `EndpointAnchorUnavailable`, or
  `EvidenceUnavailable`. Assignment disposition has precedence.
  `EndpointAnchorUnavailable(context,reason)` preserves one independent exact
  `EndpointAnchorUnavailableReason`: `CATALOG_NOT_KNOWN_AS_OF`,
  `CATALOG_EVIDENCE_MISMATCH`, or `BINDING_NOT_KNOWN_AS_OF`.
  The reason is derived in that order from catalog visibility,
  catalog/horizon identity, then binding visibility—not copied from the nested
  endpoint result. With coherent anchor facts, all sixteen ADR-014 unavailable
  labels are ignored and the pair's own ordered evidence checks decide the
  outcome. `ENDPOINT_NOT_REACHED_AS_OF` is local and applies only when the asset
  endpoint UTC instant is after `evaluationAsOf`.
- Benchmark has exactly 53 local `UnavailableReason` values and sector has
  exactly 56, in their canonical-definition order. Visible known mismatches
  fail closed before cardinality. Future evidence is invisible, while multiple
  otherwise eligible visible bindings or observations remain ambiguous; no
  latest-row, nearest-row, provider-preference, or silent-deduplication rule is
  available.
- The selected binding is start-inclusive/end-exclusive and must cover both
  the exact original/correction basis event UTC instant and the exact asset
  endpoint session-close UTC instant. The two observations must link to that
  binding, occur at those exact instants, carry positive exact
  `NUMERIC(38,12)` provider-published index levels without rounding, and use
  one exact currency. FX conversion, interpolation, prior-close substitution,
  nearest observation, or shifted sessions are forbidden.
- Benchmark binding evidence must link the exact selected benchmark-assignment
  evidence/provider event and canonical benchmark asset ID/type; benchmark
  levels and continuity repeat that exact asset. Sector binding evidence must
  link the exact selected mapping evidence/provider event plus the ADR-028
  taxonomy ID, version, hash, exact canonical WSR node, and an explicit
  reference asset ID/type `INDEX`; sector levels and continuity repeat that
  exact reference asset.
- A WSR sector node records classification meaning; it is not itself a
  provider-published sector price-index identity or binding. The membership
  provider, index publisher, and redistributor may differ, so exact evidence
  links and independently reviewed rights are required instead of inferred
  provider identity.
- Required reference kind is `PROVIDER_PUBLISHED_PRICE_INDEX`, required level
  field is `PROVIDER_PUBLISHED_INDEX_LEVEL`, and required continuity is
  `PROVIDER_PUBLISHED_INDEX_DIVISOR_CONTINUITY_ATTESTED`. Total-return indexes,
  ETFs, current baskets, market-cap proxies, provider return fields, and fuzzy
  identity are rejected.
- Binding, level, and continuity rows each preserve exact calendar ID/revision
  and calendar source ID/revision. These, along with calculation venue and
  level/continuity sources, must correlate exactly without shifting the common
  UTC interval.
- Divisor evidence must link the same reference binding and both selected
  observations and attest continuity across the exact interval. The pair
  contracts do not reuse ADR-016 `AssetReturnPricePairResolution`,
  `CorporateActionContinuity`, or `EndpointPriceAdjustmentBasis`; they select
  evidence only and calculate no benchmark or sector return.

### Module and file boundary

`com.wallstreetreceipts.api.domain.outcome.benchmarkreferencepair` contains
exactly:

- `BenchmarkReferenceLevelPairPolicyVersion.java`
- `BenchmarkReferenceIndexEvidence.java`
- `BenchmarkReferenceLevelObservation.java`
- `BenchmarkIndexDivisorContinuityEvidence.java`
- `BenchmarkReferenceLevelPairRequest.java`
- `BenchmarkReferenceLevelPairResolution.java`
- `BenchmarkReferenceLevelPairSelector.java`

`com.wallstreetreceipts.api.domain.outcome.sectorreferencepair` contains the
independent exact analogues:

- `SectorReferenceLevelPairPolicyVersion.java`
- `SectorReferenceIndexEvidence.java`
- `SectorReferenceLevelObservation.java`
- `SectorIndexDivisorContinuityEvidence.java`
- `SectorReferenceLevelPairRequest.java`
- `SectorReferenceLevelPairResolution.java`
- `SectorReferenceLevelPairSelector.java`

The intended source-local golden surface is exactly
`BenchmarkReferenceLevelPairSelectorGoldenTest.java` and
`SectorReferenceLevelPairSelectorGoldenTest.java`. Their measured invocation
cardinalities are respectively 200/200 and 220/220—420 total—and their
normalized source SHA-256 values are
`3518b66914656c8225858f8f15fdb60e25576a15b64f148270cda9881e3d8099`
and `af9ec3aa0318595027d13eb4748d41bdb587776ef3d2e5c8b3bf477fa7ba439b`:
**PASS**.

### Routes, data, lifecycle, and publication

- None. No production type outside the two new packages consumes these
  disconnected resolutions, and no route, provider, persistence, scheduler,
  resource, API, or web surface publishes them.
- No actual benchmark or sector reference-index binding, provider identity,
  historical index level, calendar revision, divisor/methodology evidence, or
  provider event has been added. Existing DEMO benchmark, sector, alpha,
  MFE/MAE, status, readiness, and comparative metrics remain unchanged and
  must not be promoted from these contracts.
- `Resolved` proves only the exact auditable evidence pair under its policy. It
  does not prove coverage, calculate a return, activate a methodology, create a
  canonical outcome, establish retry/completeness state, or authorize display
  or redistribution.

### Verification

- Exact canonical-definition extraction, independent 9342/9806-byte and
  SHA-256 checks, ordered 53/56 local-reason plus three anchor-reason checks,
  exact fourteen-plus-two source surface, reverse dependency checks, and
  four-document marker parity: **PASS**.
- Focused benchmark golden 200/200 and sector golden 220/220—420 total—and full
  API Maven verification 1668/1668 `BUILD SUCCESS` with zero failures, errors,
  or skips, including Docker/PostgreSQL/Flyway integration: **PASS**.
- The dedicated ADR-030 guard independently passes. All 35/35 workflow Python
  heredoc bodies syntax-compile; 34/34 locally executable bodies pass and the
  final cross-stack body is intentionally syntax-only. SnakeYAML 2.5 parses
  exactly four jobs and Compose config validates: **PASS**.
- Current production is exactly 216 files / SHA-256
  `45d06843fd95235221c6716a578915f40a410de8464b0b0ca3a09fff7c29436d`;
  current API-test/web is exactly 201 files / SHA-256
  `fd0e3170ba2d64aeb4bf638010915455a27d3a5aed9fe77fb2a724502d96462f`.
  Excluding the exact ADR-030 fourteen-plus-two surface reproduces ADR-029
  production at 202 files /
  `b1ae60b9c550353960687cb9973e2909e965a5e3eb98bb23b39b0a7f01a2a899`
  and test/web at 199 files /
  `59726e88e5bf7d831f16beaa693689ca799990d355733a1115c07a285a7e5293`:
  **PASS**.
- README-marker and benchmark-policy-byte mutations make the dedicated guard
  exit 1; mutating benchmark expected cardinality to 201 while actual remains
  200 makes its gate exit 1. All are restored. `git diff --check` is clean and
  the user-owned `apps/web/next-env.d.ts` remains preserved and unstaged:
  **PASS**.

### External-data and next-decision boundary

- This disconnected policy slice requires no API key, account, paid plan,
  provider license, domain, environment secret, or network access. No key
  should be requested or supplied for ADR-030 verification.
- Before real integration, P5 must choose each benchmark and sector index
  product/provider and approve exact historical index-level and revision
  access, index calendar/session and calendar-source identity/revision rights,
  divisor and methodology continuity evidence, sector-node-to-provider-index
  binding, storage/cache, product display, derived-use, redistribution, and any
  downstream publication rights.
  Assignment or classification rights alone do not grant price-index rights.
- Only after provider/product selection and written rights approval may scoped
  credentials be provisioned through untracked local, CI, and deployment secret
  stores. Credentials must never be pasted into chat or committed to Git.
- Next decisions remain separate: a benchmark reference-return calculator, a
  sector reference-return calculator, then independently gated readiness and
  lifecycle integration. Canonical evidence/methodology fingerprints and
  lineage, raw coverage and MFE/MAE paths, and sector alpha remain later work;
  alpha is last and cannot be inferred from either pair contract.

## 2026-08-24 — ADR-031 signed benchmark reference return

Status: the production contract and canonical policy definition are frozen for
the twenty-fourth disconnected P3 slice. Focused/full regression, the dedicated
guard/runtime gate, current/legacy baselines, workflow/YAML/Compose, and
independent review are **PASS**. Mutation checks, final guard, marker parity,
and Git diff hygiene are also **PASS**. No provider data, runtime wiring,
lifecycle, persistence, API, database, or web publication is added.

ADR-031 calculates a signed benchmark price-index return from one complete ADR-030 benchmark reference-level-pair receipt using the exact basis-level denominator.

### Scope

- Add an independently typed benchmark price-index return policy, complete
  input, sealed result, and deterministic calculator, plus one source-local
  golden matrix. Do not add the sector calculator in this slice.
- Accept only one complete ADR-030
  `BenchmarkReferenceLevelPairResolution`. A resolved branch preserves its
  exact assignment, endpoint, reference binding, basis/endpoint levels,
  divisor-continuity, PIT, and provenance receipt; unavailable branches
  preserve their complete typed contexts and reasons. No branch is replaced by
  extracted values.
- Calculate only a resolved pair. Preserve intentional N/A, assignment
  unavailable, endpoint-anchor unavailable, and reference-evidence unavailable
  as distinct typed branches without copying or flattening nested reasons.
- Add no selector, readiness, lifecycle, methodology activation, fingerprint,
  persistence, fixture, schema, manifest, OpenAPI, Flyway, database, provider,
  API, resource, or web behavior.

### Locked policy decisions

- Policy
  `SIGNED_BENCHMARK_BASIS_LEVEL_DENOMINATOR_SCALE_12_HALF_EVEN_V1` has one
  exact 2832-byte single-line ASCII/UTF-8 definition and SHA-256
  `96d0aab8e8e784b80a12b16c99f6ba8c5f44eff7a342fd14c075b944a0a7de79`.
  Returned definition bytes are defensive and every calculation context echoes
  that exact digest.
- Input requires ADR-030 policy
  `POINT_IN_TIME_EXACT_BENCHMARK_PRICE_INDEX_LEVEL_PAIR_V1` with exact hash
  `2394b535c1061d32c647504a303b6f1e4ec2fe88e6017d9ff335d12087a5f73d`.
  Pair-context extraction exhaustively handles all five source variants without
  a default or a reconstructed receipt.
- Results are exactly `Available(context,benchmarkReturn)`,
  `NotApplicable(context)`, `AssignmentUnavailable(context)`,
  `EndpointAnchorUnavailable(context)`, `EvidenceUnavailable(context)`, and
  `OutputUnavailable(context,reason)`. The sole local output reason is
  `OUTPUT_NOT_REPRESENTABLE`.
- Every context preserves the same complete source receipt. Upstream source
  branches precede arithmetic, and nested assignment/anchor/evidence reasons
  remain inside that receipt with no mapping, duplication, or flattening.
- Formula is exactly `(endpoint-basis)/basis`, reading only the selected
  provider-published basis and endpoint index levels. The implementation performs
  one exact `endpoint.subtract(basis)` and then one scale-12
  `RoundingMode.HALF_EVEN` division by the positive basis level.
- Output units are a signed decimal ratio. Available output has exact scale 12,
  precision at most 38, and is at least -1. Rounded exact
  `-1.000000000000` remains valid. Arithmetic failure or rounded precision
  overflow becomes `OutputUnavailable(OUTPUT_NOT_REPRESENTABLE)`.
- Operand rescale, `MathContext`, intermediate/second rounding, percent
  conversion, float/double conversion, tolerance, provider-return fields,
  alternate denominator, clipping, asset-return reuse, sector-return reuse,
  reflection/class tokens, shared generic helpers, and fallback are absent.
- Constructors enforce local policy, receipt variant, and output shape only.
  Only `BenchmarkReturnCalculator.calculate` attests the formula and required
  rounding operation.

### Module and file boundary

`com.wallstreetreceipts.api.domain.outcome.benchmarkreturn` contains exactly:

- `BenchmarkReturnPolicyVersion.java`
- `BenchmarkReturnInput.java`
- `BenchmarkReturnResult.java`
- `BenchmarkReturnCalculator.java`

The matching test surface contains only
`BenchmarkReturnCalculatorGoldenTest.java`. Production imports only JDK types
and the ADR-030 benchmark reference-level-pair contract. It has no direct or
reverse dependency through asset-return, asset price-pair, sector, assignment,
endpoint-observation, horizon, lifecycle, persistence, API, or web types.

### Routes, lifecycle, publication, and external data

- None. This leaf does not invoke the ADR-030 selector or assert original
  candidate membership; it consumes one supplied typed resolution. It does not
  decide readiness, retry, freshness, completeness, cancellation, methodology,
  fingerprint, persistence, aggregation, ranking, or product publication.
- Existing schemas, canonical fixtures, manifest membership, OpenAPI, Flyway,
  database rows, controller/repository/provider behavior, API responses, and web
  source remain unchanged. Existing DEMO benchmark, sector, alpha, and sector
  alpha values remain null.
- No API key, account, paid plan, provider license, environment secret, or
  network access is required. Before real use, P5 must approve the exact
  benchmark price-index product/feed, historical exact-time coverage,
  calendar/divisor evidence, and storage/cache, display, derived-data, and
  redistribution rights. Only then may a reviewed adapter receive a scoped
  credential through untracked local/CI/deployment secret stores, never chat or
  Git.

### Verification

- Exact policy JSON extraction, 2832-byte length/hash, result topology,
  four-plus-one source surface, dependency and reverse isolation, null DEMO
  publication, and four-document marker parity: **PASS**. Independent reviews
  report no remaining P0/P1/P2 finding after two golden-coverage gaps were
  corrected.
- Focused `BenchmarkReturnCalculatorGoldenTest`: **PASS**, exactly 95/95 with
  zero failures, errors, or skips. Normalized golden-source SHA-256 is
  `80c8e7dcdf6b4ee3daf980dc3c3d2aa54e4446620af2fc0985173fddf5ab3c90`.
- Full API Maven verification: **PASS**, exactly 1763/1763 with zero failures,
  errors, or skips and `BUILD SUCCESS`, including Testcontainers PostgreSQL
  17.10 and Flyway.
- The dedicated ADR-031 guard and runtime gate pass. All 36/36 workflow Python
  heredoc bodies syntax-compile and all 29/29 locally runnable bodies pass. Six
  `jsonschema`-dependent bodies are syntax-only because the bundled local
  runtime lacks `jsonschema`; the final cross-stack integration-log body is
  syntax-only by design. SnakeYAML 2.5 parses exactly four jobs and Compose
  config validates: **PASS**.
- Current protected production is exactly 220 files / SHA-256
  `cb8532a4020c76a9ed2fd4a61fbb5844717dc23c7f27d90510e603c0bee1f5e9`;
  current API-test/web is exactly 202 files /
  `12b03e7a48a0e6c3e676da9b335c4c270e8dc50bea2402aa25f6462db07bb273`.
  Excluding the exact ADR-031 four-plus-one surface reproduces ADR-030
  production 216 /
  `45d06843fd95235221c6716a578915f40a410de8464b0b0ca3a09fff7c29436d`
  and test/web 201 /
  `fd0e3170ba2d64aeb4bf638010915455a27d3a5aed9fe77fb2a724502d96462f`;
  downstream replay also retains ADR-029 production 202 /
  `b1ae60b9c550353960687cb9973e2909e965a5e3eb98bb23b39b0a7f01a2a899`
  and test/web 199 /
  `59726e88e5bf7d831f16beaa693689ca799990d355733a1115c07a285a7e5293`:
  **PASS**. The user-owned `apps/web/next-env.d.ts` remains preserved.
- Deliberate README-marker and canonical `percentConversion`-byte mutations
  each make the dedicated guard exit 1. Changing expected runtime count from 95
  to 94 while actual remains 95 makes its gate exit 1. All mutations are
  restored; the final dedicated guard passes, `git diff --check` is clean, and
  marker parity remains exactly one occurrence per contract document:
  **PASS**.

### Next reviewed work

- Add the independently typed sector reference-return calculator with its own
  policy, receipt, result, golden matrix, and firewall; it cannot reuse this
  benchmark result.
- Add source-local readiness only after both calculators are independently
  reviewed. Canonical evidence/methodology fingerprints and lineage, lifecycle
  composition, persistence/API/UI publication, raw-window coverage, MFE/MAE,
  alpha, and sector alpha remain later slices. Alpha remains last.

## 2026-08-24 — ADR-032 signed sector reference return

Status: the production contract and canonical policy definition are frozen for
the twenty-fifth disconnected P3 slice. Focused/full regression, the dedicated
guard/runtime gate, current/legacy baselines, workflow/YAML/Compose validation,
mutation checks, independent review, and final repository hygiene are
**PASS**. No provider data, runtime wiring, lifecycle, persistence, API,
database, or web publication is added.

ADR-032 calculates a signed sector price-index return from one complete ADR-030 sector reference-level-pair receipt using the exact basis-level denominator.

### Scope

- Add an independently typed sector price-index return policy, complete input,
  sealed result, and deterministic calculator, plus one source-local golden
  matrix. Do not add readiness or lifecycle composition in this slice.
- Accept only one complete ADR-030 `SectorReferenceLevelPairResolution`. A
  resolved branch preserves its exact assignment, endpoint, reference binding,
  basis/endpoint levels, divisor-continuity, PIT, and provenance receipt;
  unavailable branches preserve complete typed contexts and nested reasons.
- Calculate only a resolved pair. Keep intentional N/A, assignment unavailable,
  endpoint-anchor unavailable, and reference-evidence unavailable as distinct
  typed branches without copied or flattened reasons.
- Add no selector, methodology activation, fingerprint, lineage, persistence,
  fixture, schema, manifest, OpenAPI, Flyway, database, provider, API, resource,
  or web behavior.

### Locked policy decisions

- Policy `SIGNED_SECTOR_BASIS_LEVEL_DENOMINATOR_SCALE_12_HALF_EVEN_V1` has one
  exact 2817-byte single-line ASCII/UTF-8 definition and SHA-256
  `5aecd42c32ba69f0d21ab6e1ee1e3128cd31584724a6f46e176acd470204d0f7`.
  Every calculation context echoes that digest.
- Input requires ADR-030 policy
  `POINT_IN_TIME_EXACT_SECTOR_PRICE_INDEX_LEVEL_PAIR_V1` with exact hash
  `4224648ba01104fd3e96319158c7d6b42da472e9aa6f2ab22ef9fccf43da7e4a`.
  Pair-context extraction exhaustively handles all five variants without a
  default or reconstructed receipt.
- Results are exactly `Available(context,sectorReturn)`,
  `NotApplicable(context)`, `AssignmentUnavailable(context)`,
  `EndpointAnchorUnavailable(context)`, `EvidenceUnavailable(context)`, and
  `OutputUnavailable(context,reason)`. The sole local output reason is
  `OUTPUT_NOT_REPRESENTABLE`.
- Formula is exactly `(endpoint-basis)/basis`, reading only the resolved pair's
  selected provider-published basis and endpoint levels. Implementation
  performs one exact `endpoint.subtract(basis)` followed by one scale-12
  `RoundingMode.HALF_EVEN` division by the positive basis.
- Available output is a signed decimal ratio with exact scale 12, precision at
  most 38, and value at least -1. Rounded exact `-1.000000000000` remains
  valid. Arithmetic failure or rounded precision overflow becomes
  `OutputUnavailable(OUTPUT_NOT_REPRESENTABLE)`.
- Operand rescale, `MathContext`, intermediate/second rounding, percent
  conversion, float/double conversion, provider-return fields, alternate
  denominator, clipping, asset-return reuse, benchmark-return reuse,
  reflection/class tokens, shared generic helpers, and fallback are absent.
- Constructors enforce only local policy, receipt variant, and output shape.
  Only `SectorReturnCalculator.calculate` attests the formula and operation
  order.

### Module and file boundary

`com.wallstreetreceipts.api.domain.outcome.sectorreturn` contains exactly:

- `SectorReturnPolicyVersion.java`
- `SectorReturnInput.java`
- `SectorReturnResult.java`
- `SectorReturnCalculator.java`

The matching test surface contains only
`SectorReturnCalculatorGoldenTest.java`. Production imports only JDK and
ADR-030 sector reference-level-pair types. It has no direct or reverse
dependency through asset-return, benchmark-return, assignment, taxonomy,
endpoint-observation, horizon, lifecycle, persistence, API, or web types.

### Routes, lifecycle, publication, and external data

- None. This leaf consumes one supplied typed resolution; it does not invoke
  the ADR-030 selector, assert original candidate membership, establish
  readiness, decide retry/completeness, activate methodology, create a
  fingerprint or lineage receipt, persist, aggregate, rank, or publish.
- Existing schemas, canonical fixtures, manifests, OpenAPI, Flyway, database,
  controller/repository/provider behavior, API responses, and web sources are
  unchanged. Existing DEMO benchmark, sector, alpha, and sector-alpha values
  remain null.
- No API key, account, paid plan, provider license, environment secret, or
  network access is required, and this slice has no credential. Before
  non-DEMO use, P5 must approve the exact sector-index product/feed; rights to
  create and use the exact WSR canonical-node-to-selected provider-published
  sector price-index binding; exact-time historical levels and revisions;
  reference-calendar identity, revision, and source; divisor and methodology
  continuity; and storage, cache, display, derived-return, and redistribution
  rights. Publisher and redistributor require independent review when they
  differ. Assignment or classification rights alone do not grant index rights.
  Only after provider/product and written-rights approval may a reviewed
  adapter receive a scoped credential through untracked local, CI, and
  deployment secret stores, never chat or Git.

### Verification

- Exact policy extraction is 2817 bytes / SHA-256
  `5aecd42c32ba69f0d21ab6e1ee1e3128cd31584724a6f46e176acd470204d0f7`.
- Focused `SectorReturnCalculatorGoldenTest`: **PASS**, exactly 112/112 with
  zero failures, errors, or skips. Normalized golden-source SHA-256 is
  `6047b29c8c338893bf2fdeaa9a5fef83ec20cb4f5e11acb77d82b48d8752b129`.
- Current protected production is exactly 224 files / SHA-256
  `bc31bb72f14289e6a8b3c344e356f900a2d23a9fb9efd48ce935586c0e336055`;
  exact ADR-032 production exclusion reproduces ADR-031 at 220 /
  `cb8532a4020c76a9ed2fd4a61fbb5844717dc23c7f27d90510e603c0bee1f5e9`.
- Current API-test/web is exactly 203 files /
  `5f95c2b844af16224815b1b4025b52b9c25b7822d4fa53b8f8d93788805f28ce`;
  exact golden exclusion reproduces ADR-031 at 202 /
  `12b03e7a48a0e6c3e676da9b335c4c270e8dc50bea2402aa25f6462db07bb273`.
- Full API Maven verification: **PASS**, exactly 1875/1875 with zero failures,
  errors, or skips and `BUILD SUCCESS`, including Testcontainers PostgreSQL
  17.10 and Flyway.
- The dedicated ADR-032 guard and exact 112/112 runtime gate pass. All 37/37
  workflow Python heredoc bodies syntax-compile and all 30/30 locally runnable
  bodies pass. Six `jsonschema`/`referencing` bodies remain syntax-only because
  those modules are absent from the bundled runtime; the final cross-stack
  integration-log body is syntax-only by design. SnakeYAML 2.5 parses exactly
  four jobs and Compose config validates: **PASS**.
- Deliberately mutating the README marker, canonical `percentConversion` byte,
  or expected runtime count from 112 to 111 makes the dedicated guard or gate
  exit nonzero; every mutation is exactly restored and the final guard passes.
  Web lint, 569/569 Vitest tests, and production build pass. Independent
  reviews report no remaining P0/P1/P2 finding, marker parity is exactly one
  per contract document, and `git diff --check` is clean. Next.js regenerated
  `apps/web/next-env.d.ts` during verification, and its user-owned pre-build
  content was immediately restored to SHA-256
  `7ad303e40d4fddf44f156129e397511953a71481c5cfd86b1862649aaaf240cc`:
  **PASS**.

### Next reviewed work

- Add source-local comparative readiness as its own gated slice now that the
  benchmark and sector calculators are independently typed and reviewed.
- Keep lifecycle composition, methodology activation/fingerprint, lineage,
  persistence, API/UI publication, canonical evidence, raw-window coverage,
  MFE/MAE, alpha, and sector alpha separate. Alpha and sector alpha remain last.

## 2026-08-25 — ADR-033 independent benchmark/sector return readiness

Status: the two policy contracts and production packages are frozen for the
twenty-sixth disconnected P3 slice. Focused/full regression, Docker-backed
PostgreSQL integration, dedicated/static/runtime CI guards, current and legacy
repository baselines, web verification, mutation tripwires, independent
review, and final repository hygiene are **PASS**. No provider, runtime wiring,
lifecycle, persistence, API, database, fixture, or web publication is added.

ADR-033 classifies benchmark and sector return readiness independently from their complete supplied ADR-031 and ADR-032 result receipts without mapping either leaf to canonical lifecycle status.

### Scope

- Add separate four-type `benchmarkreturnreadiness` and
  `sectorreturnreadiness` packages and one exhaustive golden for each. Preserve
  ADR-025's nine-input future aggregate ownership; do not add a combined
  comparative receipt, correlation, shared generic helper, cast, or alias.
- Accept one complete matching ADR-031 or ADR-032 source result. Treat
  Available and intentional N/A as settled. Treat only the exact nested pair
  `ENDPOINT_NOT_REACHED_AS_OF` evidence chain as awaiting. Treat assignment,
  anchor, all other evidence, and output failures as evidence-unavailable.
- Preserve the exact whole supplied source result, inspect the nested reason
  only in the matching resolver, share fail-closed classification validation
  with public constructors, and make no lifecycle or completeness claim.
- Add no fixture, schema, manifest, OpenAPI, Flyway, database, controller,
  repository, provider, API, web runtime, or DEMO publication behavior.

### Frozen contracts

- Benchmark readiness policy:
  `SUPPLIED_LEAF_BENCHMARK_RETURN_READINESS_V1`, exact 2622 UTF-8 bytes,
  SHA-256
  `2dedaf014a149ed81e75941ee3677e3c8b77243b9987d9496709266aad721daf`,
  requiring ADR-031 hash
  `96d0aab8e8e784b80a12b16c99f6ba8c5f44eff7a342fd14c075b944a0a7de79`.
- Sector readiness policy:
  `SUPPLIED_LEAF_SECTOR_RETURN_READINESS_V1`, exact 2592 UTF-8 bytes,
  SHA-256
  `5737f44ebc6e65270300889dd5c2e92da0c4f3a2f04e4c6c43e4483e522187d4`,
  requiring ADR-032 hash
  `5aecd42c32ba69f0d21ab6e1ee1e3128cd31584724a6f46e176acd470204d0f7`.
- Two independent reconstructions agree on both exact byte sequences, lengths,
  hashes, first/last bytes, and no-trailing-newline convention. The selected
  definitions explicitly lock the absence of the other comparative input,
  cross-return correlation, and shared generic readiness, plus N/A and
  assignment/anchor precedence.

### Verification results

- Both independent production packages compile successfully with Java 21.
  Runtime extraction confirms the benchmark 2622-byte and sector 2592-byte
  canonical definitions and their declared hashes.
- Exhaustive focused verification passes benchmark 87/87 and sector 104/104,
  191 total, with zero failures, errors, or skips. Normalized golden hashes are
  benchmark
  `f61e82ba7766effe4954f4c96db745a49bb49a03d06de583c39c32d76e3c1b3d`
  and sector
  `1b2aa1eea5d5c8efcddd048c54d8b53be87b14cdfd96a6df43e11a7f55bc9f8c`.
- Protected production measures 232 files /
  `2cfbb3b9f9039b9e7af92ac7cbd9c35b9705ce79fda3aa58422a73f23c0d8941`;
  API-test/web measures 205 /
  `fba2656db6ef5bbf5e15288bebd894639926645e7657ac214ec1cec657cc4d75`.
  Exact eight-plus-two exclusion reproduces ADR-032 at 224 /
  `bc31bb72f14289e6a8b3c344e356f900a2d23a9fb9efd48ce935586c0e336055`
  and 203 /
  `5f95c2b844af16224815b1b4025b52b9c25b7822d4fa53b8f8d93788805f28ce`.
- Full API Maven verification passes 2066/2066 with zero failures, errors, or
  skips and `BUILD SUCCESS`, including Testcontainers PostgreSQL 17.10 and
  Flyway. The Docker Desktop process required only approved local start
  permission; no credential or provider access was used.
- The dedicated ADR-033 guard and exact 87/87 and 104/104 runtime gates pass.
  All 38/38 workflow Python heredoc bodies syntax-compile and all 31/31 locally
  runnable bodies pass. Six `jsonschema`/`referencing` bodies remain syntax-only
  because those bundled modules are absent, and the final cross-stack body is
  syntax-only by design. SnakeYAML 2.5 parses exactly four jobs and Compose
  config validates.
- Web lint, 569/569 Vitest tests, and the production build pass. This slice
  changes no API or web route and adds no responsive/UI surface. The build's
  generated `apps/web/next-env.d.ts` change was immediately restored to the
  exact user-owned SHA-256
  `7ad303e40d4fddf44f156129e397511953a71481c5cfd86b1862649aaaf240cc`.
- Deliberately mutating the README marker, a benchmark canonical policy byte,
  or benchmark expected runtime cardinality from 87 to 86 makes the dedicated
  guard or runtime gate exit nonzero. Every mutation is restored; the final
  guard and `git diff --check` pass.
- Independent review found no P0/P1/P2 production or contract issue. Its one
  actionable golden gap—explicit future-endpoint anchor branches remaining
  evidence-unavailable—was added to both legs without changing cardinality and
  reverified at 191/191. Final re-review reports only the now-corrected stale
  implementation-log status.

### External boundary and next work

- No API key, account, paid plan, provider license, named secret, or network is
  needed for this slice. Before non-DEMO use, P5 must approve the exact
  benchmark/sector-index products and feeds, exact-time history/revisions,
  reference calendars, divisor/methodology continuity, sector-node binding,
  and storage/cache/display/derived-return/redistribution rights. Publisher and
  redistributor grants are separate when the parties differ. Scoped credentials
  may follow only in untracked local, CI, and deployment secret stores, never
  chat or Git.
- Point-in-time raw-window coverage and evidence for MFE/MAE is the next
  reviewed foundation. Lifecycle, methodology/fingerprint, lineage,
  persistence, API/UI publication, alpha, and sector alpha remain separate;
  alpha stays last.

## 2026-08-25 — ADR-034 point-in-time raw-window coverage foundation

Status: complete for the provider-neutral decision-only contract, dedicated
repository guard, full regression, negative mutations, and independent closure
review.

ADR-034 freezes provider-neutral point-in-time raw-window coverage semantics before any executable raw aggregation or MFE/MAE calculation.

### Decision scope

- Treat ADR-019's `EXACT_CAUSAL_WINDOW_SESSION_UNION` only as the existing
  caller-supplied aggregate attestation. It is not a raw tick/bar verification
  receipt and cannot be cast, wrapped, or promoted into ADR-034 evidence.
- Freeze future V1 raw evidence as `TRADE_TICK_ONLY_V1` over the exact ordered
  primary-venue regular-session union and exact causal interval
  `(basis.eventTime, endpointSession.closesAt]`. Quotes, indications,
  alternate venues, off-hours, inter-session gaps, OHLC/intraday/session bars,
  bar-straddle inference, and all price fallbacks are excluded.
- Require a future executable request to anchor to one complete ADR-016
  `AssetReturnPricePairResolution.Resolved`, which supplies a mature endpoint,
  exact basis price and outcome basis, evaluation cutoff, asset/venue/currency,
  source/calendar/catalog revisions, adjustment basis, and continuity. Target-
  specific ADR-018/ADR-019 output is not an anchor.
- Require raw trade and manifest evidence to preserve provider-event and
  revision identity, event time, trade condition, exact price, ordered
  sessions, source sequence/watermark and correction/bust coverage,
  provenance, `availableAt`, and `capturedAt`. Both PIT timestamps must be at or
  before the inherited `evaluationAsOf` before evidence can affect any result.
  Raw events require
  `eventTime <= availableAt <= capturedAt <= evaluationAsOf`; manifests require
  `upperBound <= availableAt <= capturedAt <= evaluationAsOf`, preventing
  impossible evidence availability before a trade or attested window exists.
- Apply only complete visible predecessor-linked correction/bust chains.
  Future revisions remain invisible to an earlier result; later availability
  creates a later replay instead of rewriting prior point-in-time evidence.
- Distinguish a source-proven complete window with zero eligible trades from a
  missing/gapped window. Halts create no price, auction trades require an
  explicit versioned condition mapping, unknown conditions and internal source
  gaps fail closed, and silence alone proves nothing. Zero trades never invents
  zero MFE/MAE or a basis/endpoint fallback.
- Keep one future raw receipt co-identified across the source population used
  for both high and low. This is shared source evidence only; MFE and MAE retain
  separate formula, polarity, calculator, readiness, and ownership work.
  Coverage does not set `OutcomeEvaluationStatus`, `dataComplete`, retry,
  freshness, cancellation, scheduling, methodology, fingerprint, persistence,
  aggregation, ranking, or publication.

### Repository surface

- Add `decisions/ADR-034-point-in-time-raw-window-coverage-foundation.md` plus
  matching README, P3 acceptance, implementation-log, and CI guard updates.
- Add no Java production or test file and no executable policy bytes/hash,
  source-local golden, schema, canonical fixture, manifest member, OpenAPI,
  Flyway, database, repository, controller, provider adapter, resource, API, or
  web behavior. Existing DEMO MFE/MAE values remain null.
- Reserve the future `rawwindowcoverage` package and conceptual `Covered`,
  `CompleteWithoutEligibleTrade`, and `EvidenceUnavailable` meanings without
  creating their runtime types, exact reason order, or canonical policy.

### CI and verification

- Add one decision-only ADR-034 guard after ADR-033. It locks exact four-file
  marker parity, title/status/date, interval, anchor, trade-tick/PIT/correction,
  no-trade/halt/auction/bar/gap rules, ADR-019 and MFE/MAE ownership firewalls,
  absent runtime surface across the whole workflow, recursive fixture coverage,
  exact nine-file dependency/runtime configuration, unchanged null DEMO values,
  and the external-rights boundary.
- Protected production must remain 232 files / SHA-256
  `2cfbb3b9f9039b9e7af92ac7cbd9c35b9705ce79fda3aa58422a73f23c0d8941`;
  API-test/web must remain 205 files / SHA-256
  `fba2656db6ef5bbf5e15288bebd894639926645e7657ac214ec1cec657cc4d75`.
- The dedicated ADR-034 guard passes. All 39/39 embedded workflow Python bodies
  syntax-compile and all 32/32 locally runnable bodies pass. Six bodies that
  require unavailable local `jsonschema`/`referencing` modules and the final
  cross-stack runtime body remain syntax-only at their documented boundaries.
  SnakeYAML 2.5 parses exactly the four jobs `repository-contracts`, `web`,
  `call-audit-integration`, and `api`; Compose configuration validates.
- Full API `mvnw.cmd -B -ntp verify`: **PASS** — 2066/2066 tests, zero
  failures, errors, or skips, `BUILD SUCCESS`, PostgreSQL 17.10 Testcontainers,
  Flyway migrations, compilation, packaging, and Spring repackage all execute.
- Web ESLint: **PASS** with zero warnings. Vitest: **PASS**, 42/42 files and
  569/569 tests. Next 16.2.11 production build: **PASS**, including TypeScript
  and 12/12 static page-generation work items; no web source or responsive
  surface changes in this decision-only slice.
- Deliberately changing the README marker, removing the `NotApplicable`
  exclusion, weakening the raw-event causal chain, temporarily adding the
  forbidden `rawwindowcoverage/RawWindowCoveragePolicyVersion.java`, and
  adding a raw-provider line to `.env.example` each make the dedicated guard
  exit 1 at the matching parity, semantic, runtime, or configuration firewall.
  Every mutation and the temporary empty directory are removed; the final
  guard, Compose, and `git diff --check` pass. The exact nine-file normalized
  dependency/runtime configuration digest is
  `25677e07b9f511dd8899bf69fb5c435247d4996313a60361faf354002b8555bd`.
- The web build rewrote `apps/web/next-env.d.ts`; its user-owned pre-build
  content was immediately restored to exact SHA-256
  `7ad303e40d4fddf44f156129e397511953a71481c5cfd86b1862649aaaf240cc`
  and remains unstaged. Independent semantic and evidence reviews report no
  remaining P0-P3 finding after the `NotApplicable`, causal-time, recursive-
  fixture, exact configuration-digest, and whole-workflow guard gaps were
  corrected and rechecked.

### External boundary and next work

- No API key, account, paid plan, provider license, named secret, or network is
  needed for ADR-034.
- Before an executable non-DEMO resolver can be built, the user and P5 must
  approve the exact historical primary-venue trade-tick product/feed and
  written rights for history, provider event/revision identity,
  correction/bust streams, sequence/watermark semantics, trade-condition and
  auction/halt classifications, calendars, corporate actions, storage/cache,
  derived calculations, display, and redistribution. Publisher and
  redistributor grants are separate when they differ.
- Only after those approvals will the implementation request a provider-scoped
  credential and name its untracked local/CI/deployment secret location. No
  secret belongs in chat or Git.
- The executable raw coverage policy and golden matrix follow that external
  decision. Separate MFE/MAE arithmetic and readiness come afterward; bearish
  and neutral polarity, denominator, sign, rounding, and representability are
  not inferred from the existing bullish example. Alpha and sector alpha stay
  last.

## 2026-08-25 — ADR-035 SEC EDGAR public-provider foundation

Status: implementation and full repository verification complete for the
default-disabled configuration, provider-neutral filing catalog, SEC
recent-submissions adapter, and mock-only automated provider coverage.

ADR-035 establishes the default-disabled SEC EDGAR public-provider foundation.

### Scope and decisions

- Approve only the keyless SEC EDGAR Submissions API current-resource shape at
  `GET https://data.sec.gov/submissions/CIK##########.json`, with official SEC
  reuse/provenance rules and the documented aggregate 10-request/second
  ceiling. Historical segment traversal, Company Facts/XBRL, filing text and
  exhibits remain outside this slice.
- Require a server-only monitored `SEC_CONTACT_EMAIL`; send
  `User-Agent: WallStreetReceipts/0.1 (<contact>)` and never expose the contact,
  complete header, provider body, or configured URI through application
  errors. SEC requires no API key.
- Keep `SEC_PROVIDER_ENABLED=false` by default. The only base URL accepted for
  non-test use is `https://data.sec.gov`; HTTP(S) override is constrained to a
  loopback host for deterministic mock tests. Redirects are disabled and the
  client uses five-second connect and ten-second read timeouts.
- Preserve the SEC wire-format ten-digit CIK JSON string as its canonical
  identifier, rejecting Jackson scalar coercion, while
  preserving accession number independently as provider-event identity. The
  accession prefix is explicitly not required to equal the subject CIK because
  SEC permits a third-party filing agent's login CIK in that position.
- Map exact form, filing date, nullable report date, acceptance instant, and
  official primary-document URI in provider order. Validate every recent
  parallel array before mapping; invalid/missing/alignment/PIT evidence fails
  closed without fixture, zero, stale, inferred, retry, or empty-result
  fallback.
- Preserve `acceptanceDateTime` as event time and injected UTC clock time as
  processing/capture time, normalized to PostgreSQL microsecond precision.
  This slice adds no scheduler, persistence, Flyway migration, controller,
  OpenAPI contract, web consumer, or live operational/product SEC request.

### Repository surface

- Add `FilingCatalogProvider`, immutable `FilingCatalog`/`FilingRecord`, SEC
  string-CIK DTO boundary, pure mapper, conditional RestClient configuration,
  safe exceptions, decompression interceptor, and one recent-filings adapter.
- Add the `local` Spring profile for optional root `.env` import. CI and
  production do not activate it; deployments inject the same server variables
  through their secret store. Actuator environment/config-property values stay
  hidden.
- Add blank SEC names to `.env.example`, operational instructions to both
  READMEs, and the exact source/rights/PIT/remaining-gates decision in ADR-035.
  `BLS_REGISTRATION_KEY`, `BEA_USER_ID`, and `EIA_API_KEY` remain unconsumed;
  their providers need separate P5 decisions and canonical models.
- Preserve the user-owned unstaged `apps/web/next-env.d.ts` content and keep the
  actual `.env` ignored, untracked, and out of the commit. Credential presence
  was checked only as `PRESENT`/`MISSING_OR_EMPTY` before implementation. One
  rejected manual wire-check attempt echoed the configured SEC contact address
  in transient tool error output; the address is not an API key and was not
  persisted in repository files or product data.

### Verification

- Focused SEC config/HTTP, domain, and mapper suite: **PASS** — 33 tests, zero
  failures, errors, or skips. All provider responses are mocked; no real SEC
  request was made by the automated suite.
- One manual, read-only SEC wire-shape check returned HTTP 200 and confirmed a
  ten-digit JSON-string CIK plus nested `primaryDocument` paths; no response was
  stored or mapped into product data. The finding corrected the mock fixture and
  relative-path validator before the final regression run.
- Full API Maven verification with Docker Desktop 29.2.1: **PASS** — 2,099
  tests, zero failures, errors, or skips. Testcontainers 1.21.3 started Ryuk
  0.12.0 and a real `postgres:17-alpine` container; all four PostgreSQL 17.10
  migration tests ran rather than being skipped.
- Web lint: **PASS** with zero warnings. Vitest: **PASS** — 42 files and 569
  tests. Next.js production build: **PASS** — TypeScript and all 12 static-page
  generation steps completed, with all application routes emitted as
  server-rendered on demand.
- `docker compose --env-file .env.example config --quiet`: **PASS**.
- CI workflow guard verification: **PASS** — all 40 Python heredoc bodies
  syntax-compile, and all 33 bodies runnable in the local environment pass.
  Six schema-validation bodies require CI's installed `jsonschema`; one body
  requires GitHub `RUNNER_TEMP` and its preceding artifact. ADR-034/ADR-035 and
  all ten affected historical broad-baseline guards pass without changing any
  historical count or digest.
- `git diff --check`: **PASS**. The production build rewrote the user-owned
  `apps/web/next-env.d.ts`, so its original content was restored; its SHA-256
  remains `7ad303e40d4fddf44f156129e397511953a71481c5cfd86b1862649aaaf240cc`.

### Next work

- Add a bounded response-size limit, aggregate SEC rate limiter, explicit
  `Retry-After`/backoff ownership, and a manual opt-in live smoke procedure
  before any live operational claim.
- Review raw response receipts/digests, revisions, historical segment
  completeness, append-only persistence, ingestion scheduling, API contract,
  and attributed UI publication as separate gates in that order.
- Begin BLS only after its release-time, vintage/revision, series calculation,
  storage, display, and redistribution contract is approved. BEA and EIA need
  new canonical product surfaces rather than being forced into the closed
  six-series call-context snapshot.

## 2026-08-25 — ADR-036 SEC EDGAR single-process live-operation guardrails

Status: implementation and repository verification complete for the bounded
single-JVM request path and explicit one-request live smoke boundary.

ADR-036 establishes the single-process SEC live-operation safety gate.

### Scope and decisions

- Preserve the SEC-published aggregate ceiling of 10 requests/second and its
  post-limit 10-minute recovery rule as external constraints. Apply a stricter
  internal one-JVM limit of 8 requests/second with fixed 125 ms spacing and no
  accumulated idle burst. This is not aggregate coordination across replicas,
  hosts, or independent tools.
- Bound successful SEC submissions bodies at 8 MiB after decompression. Reject
  oversized identity `Content-Length` before parsing, stream-cap missing or
  chunked lengths, and count gzip/deflate expansion against the decoded limit.
  Malformed lengths and limit overruns fail closed with sanitized errors and
  close both the limited body and provider response.
- Never retry HTTP `429` automatically. Parse valid delta-seconds and RFC 1123
  `Retry-After` only to calculate a process-local cooldown. Missing, invalid,
  expired, or sub-10-minute values use the 10-minute minimum; longer valid
  values are honored without overflow. Calls during cooldown fail before
  network I/O instead of sleeping a request thread for 10 minutes.
- Keep spacing sleeps outside the shared state monitor. A concurrent 429 can
  publish cooldown while another caller waits for its spacing permit; the
  waiter rechecks monotonic progress and cooldown before any permit is issued.
  A deterministic latch test preserves this ordering.
- Add a separate `sec-live-smoke` Maven profile and
  `SEC_LIVE_SMOKE=true` environment gate. The isolated test activates the
  real production SEC configuration at exactly `https://data.sec.gov`, reads
  the existing root `.env` contact through the `local` profile, and makes one
  Apple CIK `0000320193` request. Ordinary builds do not add or compile that
  source and never run it.
- Require no new API key, account, paid plan, OAuth credential, or plugin. The
  existing monitored `SEC_CONTACT_EMAIL` remains the only operator-provided
  identity. Neither that value nor response bodies or headers are logged,
  persisted, committed, or supplied through chat.
- Continue to prohibit a scheduler, polling loop, multi-replica activation,
  database writer, raw receipt, controller, public API, and UI publication.
  Those remain separate provenance, persistence, orchestration, and product
  gates.

### Repository surface

- Add the shared rate limiter and HTTP interceptor, decoded response-size
  interceptor, and `Retry-After` cooldown policy behind the existing
  conditional SEC RestClient configuration.
- Extend the SEC provider to open cooldown on 429 before inspecting any error
  body and preserve sanitized provider failures for rate-gate, malformed-body,
  and response-size cases.
- Add deterministic tests for concurrent spacing, cooldown preemption,
  interrupt/time failure, retry-header forms and overflow, declared/chunked
  body sizes, repeat body access, stream closure, and gzip/deflate expansion.
- Add the profile-only Failsafe smoke source, ADR-036, and operator instructions
  in both READMEs. Preserve the default-disabled provider, official-origin
  restriction, existing `.env` contract, and all ADR-035 non-publication
  boundaries.
- Preserve the user-owned unstaged `apps/web/next-env.d.ts` content and keep the
  actual root `.env` ignored and untracked.

### Verification

- Focused SEC configuration/domain/provider suite: **PASS** — 66 tests, zero
  failures, errors, or skips.
- Manual opt-in SEC live smoke: **PASS** — one test and exactly one read-only
  Apple submissions request to the official origin. No response body, filing
  row, contact value, complete User-Agent, or header was printed or persisted.
- Default smoke isolation: **PASS** — default `test-compile` excluded the live
  source; the profile included exactly one Failsafe IT; profile invocation
  without the environment gate failed before context creation or network I/O.
- Full API Maven verification with Docker Desktop 29.2.1: **PASS** — 2,132
  tests, zero failures, errors, or skips. Testcontainers 1.21.3 ran all four
  PostgreSQL 17.10 migration tests against `postgres:17-alpine`.
- Web lint: **PASS** with zero warnings. Vitest: **PASS** — 42 files and 569
  tests. Next.js production build: **PASS** — TypeScript and all 12 static-page
  generation steps completed; all product routes remain server-rendered on
  demand.
- `docker compose --env-file .env.example config --quiet`: **PASS**.
- CI workflow guard verification: **PASS** — all 41 embedded Python bodies
  syntax-compile. The ADR-034 historical projection, ADR-035 foundation replay,
  new ADR-036 exact 16+7+1 surface contract, and all ten affected historical
  broad-baseline exclusions execute successfully in the local environment.
- `git diff --check`: **PASS**. The production build rewrote the user-owned
  `apps/web/next-env.d.ts`, so its original content was restored; its SHA-256
  remains `7ad303e40d4fddf44f156129e397511953a71481c5cfd86b1862649aaaf240cc`.

### Next work

- Decide the immutable raw SEC response receipt, digest, capture timestamp,
  source URI, and retention boundary before any filing metadata persistence.
- Then add historical submissions-segment completeness and revision handling,
  followed by append-only persistence. Scheduling requires distributed/global
  rate and cooldown coordination first.
- Approve the attributed read API and Korean public UI only after provenance,
  point-in-time semantics, rights notices, and stale/error/empty behavior are
  explicit. No live filing value is published by this slice.

## 2026-08-25 — ADR-037 SEC EDGAR decoded-response receipt foundation

Status: in-memory receipt foundation implemented and verified.

ADR-037 establishes the in-memory SEC decoded-response receipt foundation.
It binds one accepted SEC submissions catalog to the exact decoded bytes
supplied to its versioned parser without claiming durable source retention.

### Scope and decisions

- Accept only a fully read HTTP `200 application/json` entity. After exactly
  one advertised gzip/deflate transport decode, hash the exact decoded bytes as
  lowercase SHA-256 and record their positive byte length.
- Preserve transport `Content-Encoding` for the receipt but remove the stale
  encoded `Content-Length` from the decoded downstream header view for
  gzip/x-gzip/deflate. Keep the provider headers unmodified and let the decoded
  streaming cap plus the captured decoded length govern the decoded entity.
- Do not perform charset conversion, JSON normalization, whitespace or line-end
  normalization, field reordering, or parsed-object reserialization. Use the
  same owned defensive byte copy as both digest input and parser input, and
  reject trailing JSON tokens. Validate strict UTF-8 without stripping a valid
  UTF-8 BOM or transforming the hashed bytes; reject UTF-16, UTF-32, and
  malformed UTF-8 before receipt creation. Lock the versioned reader against
  duplicate keys, scalar coercion, floating-point-to-integer coercion, and
  trailing JSON tokens.
- Record source URI, UTC-microsecond `capturedAt`, status, media type, normalized
  transport encoding, optional `ETag`/`Last-Modified`, parser version
  `SEC_SUBMISSIONS_RECENT_V1`, decoded length, and digest. Attach the receipt to
  its filing catalog with matching provider, product, URI, and capture time.
- Apply a deny-by-default response-metadata policy. Preserve only the explicit
  allowlist above; retain no arbitrary response header, request header, contact
  email, or complete `User-Agent`.
- Declare `RECEIPT_ONLY_BODY_NOT_RETAINED`: the bounded decoded body exists only
  in memory for hashing and parsing and is not part of the receipt. Treat the
  digest as a local exact-byte identifier, not an SEC signature or proof that
  SEC authored or sent the response.
- Require no new API key, account, paid plan, OAuth credential, registration,
  or plugin. Live operation continues to use only the existing monitored
  `SEC_CONTACT_EMAIL`, which is outside the receipt.

### Repository surface

- Add the provider-neutral immutable `SourceResponseReceipt` and transient
  SEC-owned decoded-response capture boundary.
- Change the SEC provider to obtain the bounded entity as bytes, capture the
  receipt after complete read, and parse that same owned byte sequence with the
  dedicated versioned strict reader.
- Carry the complete receipt through `SecSubmissionsMapper` into
  `FilingCatalog`, whose invariants reject cross-provider/product/URI/time
  attachment. Keep decode-plus-map on the package-private capture path; do not
  claim cryptographic binding against arbitrary in-process construction.
- Preserve sanitized provider failures for empty bodies, unsupported or
  ambiguous media/encoding/validator metadata, and unreadable or trailing JSON.

### Retention and non-scope

- Add no durable raw-body store, replay reader, append-only persistence,
  Flyway migration, database table or row, repository, scheduler, polling loop,
  controller, OpenAPI contract, or public API/UI publication.
- Do not claim historical completeness. Historical `filings.files` segment
  modeling and validation is the next gate; append-only persistence follows
  that gate. Durable raw-body retention and replay require their own explicit
  policy before any database write.

### Verification

- Focused SEC configuration, transport, receipt, domain, and mapper suite:
  **PASS** — 91 tests with zero failures, errors, or skips.
- Full API Maven verification: **PASS** — 2,157 tests with zero failures,
  errors, or skips. PostgreSQL 17 Testcontainers/Flyway, H2/Spring/API tests,
  and Spring Boot packaging completed successfully.
- Manual live diagnosis made four read-only Apple submissions requests without
  logging or retaining the body, response values, contact address, or complete
  User-Agent. It isolated deterministic JSON truncation to Spring consuming the
  compressed representation's 28,518-byte `Content-Length` against the decoded
  stream. After the header-boundary fix, the final fifth request passed the
  complete strict receipt/parser/catalog smoke. The failed attempts produced no
  product data or durable source artifact.
- Web regression: **PASS** — ESLint, 42 Vitest files / 569 tests, and the Next.js
  production build with 12 static-generation steps. Compose validation passed.
- Repository CI guard: **PASS** — all 42 embedded Python bodies compile and all
  35 environment-independent bodies execute successfully. Seven bodies are
  environment-only locally (six require `jsonschema`, one `RUNNER_TEMP`); no
  guard failed. ADR-034/035/036 reverse projections, ADR-037 exact-manifest and
  semantics, workflow P0/P1 review, and patch hygiene all pass.

### Next work

- Implement and review the historical submissions-segment contract, including
  safe filenames, range/cardinality coherence, and deterministic overlap,
  duplicate, revision, and recent-versus-complete-history handling.
- Then design append-only receipt and filing persistence, including durable
  raw-body retention/replay policy, idempotency, revisions, and Flyway schema.
- Keep scheduling, multi-instance rate coordination, read API, and attributed
  Korean public UI publication behind their later independent gates.

## 2026-08-25 — ADR-038 SEC EDGAR historical-segment descriptor catalog

Status: descriptor-catalog implementation and verification complete.

ADR-038 establishes the in-memory catalog of historical segment descriptors
advertised by one SEC EDGAR Submissions root response. It is not a fetched or
complete-history claim.

### Scope and decisions

- Advance the current strict parser identity to
  `SEC_SUBMISSIONS_CATALOG_V2`, while preserving ADR-037's historical V1 record.
  Parse `filings.recent` and required `filings.files` from the same exact
  decoded root bytes and carry their shared `SourceResponseReceipt` into one
  catalog.
- Require `filings.files` to be present as an array. Accept an empty array, but
  reject null members. Require each descriptor's trimmed CIK-bound filename,
  positive coercion-free integer `filingCount`, exact valid `YYYY-MM-DD`
  `filingFrom`/`filingTo`, and non-reversed inclusive advertised range.
- Restrict adapter filenames to
  `CIK##########-submissions-NNN.json`, require the embedded CIK to equal the
  root/catalog CIK, reject suffix `000`, and reject duplicate filenames. This
  is a local fail-closed allowlist, not an SEC filename guarantee.
- Add provider-neutral `HistoricalFilingSegmentDescriptor` and keep immutable
  provider-order `FilingCatalog.recentFilings` and `historicalSegments`
  separate. Preserve advertised count and inclusive range as manifest claims,
  not observed rows or verified completeness.
- Expose only `RECENT_ONLY_NO_SEGMENTS_ADVERTISED` and
  `RECENT_ONLY_SEGMENTS_ADVERTISED_NOT_FETCHED`. Empty descriptors mean no
  additional segment was advertised by that capture, not complete history.
- Flag advertised historical-range overlap and recent-date-to-advertised-range
  overlap without rejecting, sorting, clipping, merging, deduplicating,
  summing counts, or inferring duplicate accessions or completeness.
- Fail the entire root response for missing/malformed descriptors. Do not
  salvage recent rows, substitute an empty list, zero, current date, fixture,
  stale catalog, bulk archive, alternate provider, or scraper result.

### Receipt and point-in-time boundary

- The V2 root receipt binds the exact decoded root bytes and all descriptor
  metadata parsed from them. It does not bind a referenced segment's body,
  existence, actual count/range, later mutation, response metadata, or digest.
- A descriptor is known only at the root catalog `capturedAt`.
  `advertisedFilingFrom`/`advertisedFilingTo` are not event, availability,
  processing, capture, or publication timestamps and cannot backdate knowledge.
- A safe filename is a captured reference, not fetched content, an SEC digital
  signature, sender authentication, or evidence that the resource remains
  unchanged.

### Credentials, operations, and non-scope

- Require no new API key, account, paid plan, OAuth credential, EDGAR filer/user
  token, registration, plugin, environment variable, or additional HTTP call.
  Explicit live root access continues to use only the existing monitored
  `SEC_CONTACT_EMAIL`, which is not receipt data.
- Add no segment GET, segment receipt/body/parser, historical `FilingRecord`,
  actual cardinality/range verification, recent-plus-history union, accession
  duplicate policy, overlap/revision/removal reconciliation, or complete state.
- Add no durable raw-body retention, replay reader, append-only persistence,
  Flyway/DB, repository, scheduler, polling, controller, OpenAPI, public API/UI,
  nightly `submissions.zip`, filing-document, XBRL, or Company Facts behavior.

### Repository surface

- Extend the closed SEC vendor DTO with `SecHistoricalFilingFile` and the four
  selected descriptor fields.
- Extend the pure mapper with exact field/date/CIK-bound filename validation
  and parser identity V2.
- Add `HistoricalFilingSegmentDescriptor`; rename the catalog filing list to
  `recentFilings`; add `historicalSegments`, the two exact status values, and
  advertised-overlap diagnostics.
- Extend focused configuration, mapper, domain, and live-smoke shape coverage
  without adding a new runtime request path.

### Verification

- Focused descriptor/catalog/mapper suite: **PASS** — 70 tests with zero
  failures, errors, or skips.
- API main-source compilation: **PASS**.
- Full API Maven verification: **PASS** — 2,180 tests with zero failures or
  errors; Maven completed with `BUILD SUCCESS`.
- Manual SEC live smoke: **PASS** — one Apple root request, zero referenced
  segment requests, nonempty V2 historical descriptors with the explicit
  advertised-but-not-fetched status, one test, and `BUILD SUCCESS`.
- Web regression: **PASS** — lint, 42 Vitest files / 569 tests, and the Next.js
  production build completed successfully. The user-owned
  `apps/web/next-env.d.ts` was restored to its original SHA-256
  `7ad303e40d4fddf44f156129e397511953a71481c5cfd86b1862649aaaf240cc`.
- Repository CI guard: **PASS** — all 43 embedded Python bodies compile, all
  36 environment-independent bodies execute, and the ADR-034 through ADR-038
  dedicated replay guards pass. Six `jsonschema`-dependent bodies and one
  integration-artifact-dependent body remain CI-only by design.
- Patch hygiene and credential scan: **PASS** — `git diff --check`, workflow
  YAML parsing, placeholder scan, and changed-file secret/contact checks passed
  without exposing configured credential values.

### Next work

- Design append-only persistence for the root receipt, recent catalog, and
  advertised descriptors, including durable raw-body/replay policy,
  idempotency, revisions, Flyway schema, and PIT reconstruction.
- Then fetch referenced segments under the existing transport controls and add
  segment-specific receipts, actual cardinality/range validation, deterministic
  duplicate/overlap/revision handling, and recent-plus-history completeness.
- Keep scheduling/global coordination, read API, and attributed Korean public
  UI behind later independent gates. ADR-038 alone does not complete ADR-035's
  broader historical-segment gate.

## 2026-08-25 — ADR-039 SEC EDGAR append-only root-capture persistence

Status: append-only persistence and exact-byte replay implemented and verified.

ADR-039 makes one accepted SEC EDGAR Submissions root capture durably
replayable without treating advertised segment metadata as observed or
complete filing history.

### Scope and decisions

- Add an immutable `FilingCatalogCapture` that owns a defensive copy of the
  exact decoded bytes used by the strict V2 parser. Its versioned,
  length-prefixed `captureId` binds provider, product, CIK, source URI,
  `capturedAt`, body SHA-256, and decoded length.
- Keep the existing catalog-only receipt state literal. The persistence path
  enters as `DECODED_BODY_ATTACHED_PENDING_PERSISTENCE`; only a successful
  repository write and reconstructed read use
  `DURABLE_DECODED_BODY_RETAINED`. A caller-supplied durable claim is rejected.
- Add Flyway V6 tables for content-addressed exact `BYTEA` bodies, immutable
  root captures, provider-ordered recent filings, and provider-ordered
  historical descriptors. Root/body/children use restricting foreign keys,
  status/count/PIT checks, and no repository update/delete surface.
- Append body, root, and all children in one transaction, then reconstruct and
  compare the complete durable aggregate. Exact replay is idempotent; a later
  observation of the same bytes creates a new root and reuses the body; a
  mismatched natural identity or capture ID fails closed.
- Resolve PIT reads only for an exact provider/product/CIK/parser contract and
  only from captures with `capturedAt <= evaluationAsOf`. Every read checks
  digest/length, capture identity, child counts and contiguous order, status,
  and a full strict parser replay against the retained bytes.
- Revalidate the official SEC submissions URI, JSON/UTF-8 media envelope,
  decoded 8 MiB limit, and strict UTF-8 before replay. Directly constructed
  forged-origin, non-JSON, oversized, inconsistent, or corrupted aggregates
  cannot cross the persistence boundary.
- Retain exact bodies as private PostgreSQL evidence without an automatic TTL.
  Add no public raw-body endpoint and make no WORM, encryption, backup, legal
  hold, or disposal claim. Any deletion or archival policy requires a later
  reviewed decision.
- Require no new SEC API key, provider account, paid plan, OAuth credential,
  filer token, plugin, or environment variable. Explicit live capture still
  uses only `SEC_CONTACT_EMAIL`; persistence uses the existing PostgreSQL
  connection variables. No configured secret value was read, printed, or
  committed.

### Repository surface

- Add the capture provider/repository/replay ports and one-shot
  `PersistFilingCatalogCaptureService`; keep scheduler, retry, controller,
  command-line trigger, and autonomous collection outside this gate.
- Extend the SEC provider's existing one-request root path to return the exact
  decoded bytes, receipt, and catalog together for persistence while preserving
  the receipt-only catalog path.
- Add `JdbcFilingCatalogCaptureRepository`, the SEC V2 replay verifier, the
  property-gated persistence wiring, and V6 migration.
- Add deterministic domain, service, Spring wiring, H2 persistence, Flyway
  upgrade, PostgreSQL concurrency/constraint, and manual live shape coverage.
  Document the retention, credentials, PIT, concurrency, security, and
  non-scope boundary in both READMEs and ADR-039.

### Verification

- Focused capture/domain/service/configuration/persistence suite: **PASS** — 62
  tests, zero failures, errors, or skips.
- Full API Maven verification with Docker Desktop 29.2.1: **PASS** — 2,200
  tests, zero failures, errors, or skips; Spring Boot packaging completed.
- PostgreSQL 17.10/Testcontainers V6 and upgrade-path suite: **PASS** — five
  tests. It verifies exact/idempotent append, later-observation body reuse,
  concurrent identical convergence, concurrent different-body fail-closed
  conflict with loser rollback and no orphan body, raw length checks,
  restricting deletion, and migrations from prior schema versions.
- Manual opt-in SEC smoke: **PASS** — one Apple root request, one test, exact
  pending capture body/receipt/catalog shape, and no referenced-segment request.
  No body, contact value, complete User-Agent, or provider row was logged.
- Web regression: **PASS** — ESLint with zero warnings, 42 Vitest files / 569
  tests, and the Next.js production build with all 12 static-generation steps.
  The user-owned `apps/web/next-env.d.ts` was restored to SHA-256
  `7ad303e40d4fddf44f156129e397511953a71481c5cfd86b1862649aaaf240cc`.
- Repository CI guard: **PASS** — the ADR-039 exact-surface and semantic guard,
  all 44 embedded Python bodies' syntax, and all 37 environment-independent
  bodies execute successfully. Seven CI-only bodies remain local skips because
  they require `jsonschema` or the integration artifact environment. The
  workflow YAML parses with duplicate keys rejected.
- Independent correctness review: **PASS** — no release-blocking finding. The
  review's provenance-envelope, oversized replay, concurrent different-body
  loser rollback, and raw count/status/PIT/CIK constraint recommendations were
  all implemented and verified.
- Patch and credential hygiene: **PASS** — `git diff --check`, changed-file
  credential-pattern scan, exact user-file SHA, and private-body publication
  firewall all pass without reading or printing configured secret values.

### Next work

- Retrieve only captured CIK-bound historical-segment filenames under the
  existing SEC transport controls and give every segment its own exact decoded
  body, immutable receipt, versioned parser, durable replay, and root/descriptor
  binding.
- Compare observed segment rows and ranges with advertised metadata; define
  accession duplicate, overlap, replacement/removal, and complete-history
  semantics without inferring missing facts.
- Keep polling, distributed/global rate coordination, public read API,
  authentication decision, and attributed Korean UI behind later independent
  gates.

## 2026-08-25 — ADR-040 controlled SEC historical-segment capture persistence

Status: exact single-segment capture, replay, and append-only persistence
implemented and verified.

ADR-040 lets one explicitly selected descriptor from one exact durable ADR-039
root become independently attributable evidence. It does not fetch every
descriptor, merge resources, or claim complete SEC filing history.

### Scope and decisions

- Accept only an exact persisted root `captureId` and provider-order descriptor
  ordinal. Reconstruct that root, select its immutable CIK-bound descriptor, and
  derive `https://data.sec.gov/submissions/{capturedFileName}` internally.
  Invalid input makes no provider request; valid input makes at most one GET.
  Callers cannot supply a URI, filename, host, CIK, query, or fragment.
- Give the segment its own product/parser identity,
  `edgar-submissions-historical-segment-api` and
  `SEC_SUBMISSIONS_HISTORICAL_SEGMENT_V1`. Require the currently observed 14
  top-level parallel arrays, equal cardinality, strict scalar/date/timestamp
  types, unique segment-local accession identity, and no coercion, partial
  salvage, or fallback. Treat this wire contract and URI rule as versioned WSR
  V1 assumptions rather than SEC guarantees.
- Preserve provider order in segment-specific `HistoricalFilingRecord` rows.
  A live Apple segment showed that old rows can have an empty
  `primaryDocument`; preserve that absence as nullable `primaryDocumentUri`
  without inventing a path. Keep the root recent-row `FilingRecord` non-null URI
  invariant unchanged and retain strict SEC Archives/catalog-CIK validation for
  every present historical document path.
- Attach the exact decoded bytes used for hash and parse to an independent
  receipt in `DECODED_BODY_ATTACHED_PENDING_PERSISTENCE`. Only verified append
  and reconstruction promote it to `DURABLE_DECODED_BODY_RETAINED`. Keep request
  headers, contact email, complete User-Agent, arbitrary response headers, and
  body text out of logs and receipt metadata.
- Add Flyway V7 tables for immutable segment captures and contiguous
  provider-order rows. Bind each capture to the complete persisted root
  descriptor tuple, reuse the SHA-256 content-addressed exact-body table, and
  append body/capture/children in one transaction. Exact replay is idempotent;
  later observation of the same bytes appends a new capture and reuses the body;
  conflicts and partial writes fail closed and roll back.
- Preserve observed count and actual filing-date minimum/maximum separately.
  A live Apple segment also showed that advertised range endpoints need not
  equal actual extrema. `MATCHES_ADVERTISED` therefore means exact count equality
  plus inclusive containment of every observed date, not endpoint equality.
  Store count-only, range-only, and combined mismatch states as source evidence;
  an empty segment retains count zero, null extrema, and `COUNT_MISMATCH`.
- Make segment availability begin only at its own later `capturedAt`, never at
  the root capture or advertised/filing dates. PIT reads select by exact root,
  descriptor ordinal, parser, and `capturedAt <= evaluationAsOf`. Root and
  segment are not an SEC-provided atomic snapshot.
- Require no new API key, account, paid plan, OAuth credential, plugin, or
  environment variable. Explicit live access reuses only the existing monitored
  `SEC_CONTACT_EMAIL`; persistence reuses the existing PostgreSQL variables. No
  configured secret value was read, printed, or committed.

### Repository surface

- Add `PersistHistoricalFilingSegmentCaptureService` and closed provider,
  repository, append-result, and replay-verifier ports under the application
  boundary.
- Add the SEC historical raw-response envelope, strict V1 DTO/mapper, one-GET
  provider, and exact replay verifier behind the vendor DTO -> adapter ->
  canonical model -> domain boundary.
- Add `HistoricalFilingRecord`, `HistoricalFilingSegment`, and
  `HistoricalFilingSegmentCapture`, plus
  `JdbcHistoricalFilingSegmentCaptureRepository` and property-gated Spring
  wiring. No scheduler, controller, command-line trigger, OpenAPI route, public
  API, or web route was added.
- Add H2/domain/service/provider/replay coverage, PostgreSQL 17 Flyway upgrade,
  constraint, concurrency, rollback, idempotency and PIT coverage, plus a manual
  opt-in live shape check bounded to one root and its first captured descriptor.

### Verification

- Full API Maven verification with Docker Desktop 29.2.1: **PASS** — 2,233
  tests, zero failures, errors, or skips; Spring Boot packaging completed.
- PostgreSQL 17.10/Testcontainers V7 and upgrade-path suite: **PASS** — six
  tests. It covers fresh/upgrade migration, exact and later-observation body
  reuse, idempotent and conflicting append, concurrent convergence and
  fail-closed rollback, nullable document evidence, constraints, restricting
  deletion, parser-specific PIT, and complete round-trip reconstruction.
- Manual opt-in SEC smoke: **PASS** — one Apple root GET and one captured first
  descriptor GET, one test, and `BUILD SUCCESS`. It confirmed nullable historical
  document evidence and inclusive range containment without logging body,
  contact value, complete User-Agent, or provider rows.
- Web regression: **PASS** — ESLint with zero warnings, 42 Vitest files / 569
  tests, and the Next.js production build with all 12 static-generation steps.
  The user-owned `apps/web/next-env.d.ts` was restored to SHA-256
  `7ad303e40d4fddf44f156129e397511953a71481c5cfd86b1862649aaaf240cc`.
- Repository CI guard: **PASS** — all 45 embedded Python bodies compile and all
  38 environment-independent bodies execute successfully, including the
  ADR-034 through ADR-040 replay guards. Six schema bodies remain CI-only because
  they require the workflow-pinned `jsonschema`; one integration body requires
  `RUNNER_TEMP` and its preceding integration artifacts. Workflow YAML parsing
  with duplicate keys rejected and `git diff --check` both pass.
- Independent correctness review: **PASS** — no remaining P0–P3 finding. The
  review identified one H2 rollback-coverage false-positive; it was removed and
  replaced with committed-root PostgreSQL evidence for nullable URI round-trip,
  exact child SQLSTATE `22001`, full transaction rollback, and orphan-body
  absence. Direct database-administrator WORM enforcement remains explicitly
  outside the ADR-039/040 repository boundary.

### Next work

- Define an ordered collection manifest relative to one immutable root and
  explicitly select one durable capture per advertised descriptor.
- Compare every source occurrence of accessions across root recent rows and
  selected segments, then define duplicate/conflict/correction evidence without
  overwriting or inventing a complete-history state.
- Keep scheduler/global rate coordination, retry ownership, public read API,
  authentication decision, and attributed Korean UI behind later independent
  gates.

## 2026-08-25 — ADR-041 root-relative SEC filing-history collection manifest

Status: zero-network ordered collection assembly, occurrence-preserving
accession reconciliation, and append-only persistence implemented and verified.

ADR-041 combines only exact durable ADR-039/040 evidence under one immutable
root. It records which root-advertised additional JSON files were explicitly
selected and compares repeated accession projections without choosing a winner
or claiming complete SEC history.

### Scope and decisions

- Accept one exact durable root `captureId` plus an explicit finite list of
  `(descriptorOrdinal, segmentCaptureId)` selections. Resolve only those exact
  identities, reject a repository result with another identity, and never use a CIK,
  filename, current clock, or `findLatestAtOrBefore` to substitute evidence.
  Assembly performs zero SEC or other provider requests.
- Materialize every captured root descriptor in provider ordinal order as
  `NOT_SELECTED` or `SELECTED_EXACT_CAPTURE`. Preserve root recent rows first,
  then selected additional-file rows by descriptor ordinal and their original
  provider row order. This is a WSR reproducibility rule, not SEC chronology.
- Preserve every source row as a separate occurrence with source kind, exact
  capture identity, descriptor and row ordinal where applicable, selected
  canonical projection, and versioned projection fingerprint. Do not dedupe,
  merge, overwrite, infer a missing field, or select root/latest/majority truth.
- Group only by the exact stored accession. Compare provider event/accession,
  form, filing and nullable report date, accepted time, and nullable primary
  document URI by actual field equality. Classify only
  `SINGLE_SOURCE_OCCURRENCE`, `MULTIPLE_OCCURRENCES_EXACT_AGREEMENT`, or
  `MULTIPLE_OCCURRENCES_CANONICAL_CONFLICT`; retain all conflicting candidates.
- Define coverage only relative to the captured root as
  `NO_ADVERTISED_DESCRIPTORS`, `PARTIAL_ADVERTISED_DESCRIPTORS_SELECTED`, or
  `ALL_ADVERTISED_DESCRIPTORS_SELECTED`. Selecting zero from a nonempty set is
  partial. Selecting every advertised reference is not current, atomic,
  all-time, legally authoritative, or complete SEC history.
- Derive `evidenceAvailableAt` as the maximum root/selected-segment
  `capturedAt`, require microsecond `assembledAt >= evidenceAvailableAt`, and
  expose PIT lookup only by exact manifest ID plus assembly cutoff. Do not offer
  root-relative latest-manifest lookup because different partial selections do
  not supersede one another.
- Derive stable length-prefixed `selectionSha256` and `manifestId` values from
  the exact root, full selected/absent descriptor vector, schema, product, and
  reconciliation policy. Exclude attempted assembly time so concurrent or later
  same-content append converges on the first durable manifest and its original
  `assembledAt`.
- Require no new API key, account, payment, OAuth/EDGAR token, plugin, secret,
  or environment variable. Existing PostgreSQL configuration is sufficient;
  zero-network assembly does not consult `SEC_CONTACT_EMAIL`. No configured
  secret value was read, printed, or committed.

### Repository surface

- Add `PersistFilingHistoryCollectionManifestService`, an exact-selection
  command value, append outcome, and read/append-only repository port under the
  application boundary. Add unconditional internal Spring wiring because the
  service has no startup action or network side effect.
- Add immutable `FilingHistoryCollectionManifest` domain assembly with ordered
  descriptor members, source occurrences, accession groups, stable identities,
  coverage and availability facts. `sameContentAs` intentionally excludes only
  the attempted assembly time for idempotent winner replay.
- Add `JdbcFilingHistoryCollectionManifestRepository`. Every write first
  reassembles from replay-verified source repositories, appends parent and
  ordered children in one transaction, reconstructs again, and requires exact
  domain equality before returning. Reads re-run ADR-039/040 exact-byte replay
  and compare every stored summary, descriptor, group, occurrence, fingerprint,
  ordinal, nullable field, and source binding.
- Add Flyway V8 manifest, descriptor, accession-group, and occurrence tables.
  Exact composite `ON DELETE RESTRICT` FKs bind the manifest root, every
  advertised descriptor, selected segment, selected source row, and accession
  group. Closed checks cover identities, time, counts, selection/source XOR,
  group classification, and application-level immutable flags.
- Add domain, exact-ID service, H2 persistence, replay-valid fixture, and
  isolated PostgreSQL 17 upgrade/concurrency/rollback/restrict tests. Existing
  product routes, OpenAPI, controllers, browser consumers, and the manual live
  smoke are unchanged; no scheduler, CLI, startup collector, fetch-all loop, or
  autonomous trigger was added.

### Verification

- Focused ADR-041 domain/service/H2 persistence suite: **PASS** — 22 tests,
  zero failures, errors, or skips.
- Full API Maven verification with Docker Desktop 29.2.1: **PASS** — 2,259
  tests, zero failures, errors, or skips; Spring Boot packaging completed.
- PostgreSQL 17.10/Testcontainers migration suite: **PASS** — existing six plus
  four ADR-041 tests, ten total with zero failures, errors, or skips. It covers
  isolated V7→V8 upgrade, fresh V8 application, identical-selection concurrent
  winner/replay convergence, different-selection concurrency, child-failure
  rollback with no orphans, and exact source-delete restriction.
- Web regression: **PASS** — ESLint with zero warnings, 42 Vitest files / 569
  tests, and the Next.js production build with all 12 static-generation steps.
  The build-mutated user-owned `apps/web/next-env.d.ts` was restored to SHA-256
  `7ad303e40d4fddf44f156129e397511953a71481c5cfd86b1862649aaaf240cc`.
- Repository CI guard: **PASS** — all 46 embedded Python bodies compile and all
  39 environment-independent bodies execute successfully, including the
  dedicated ADR-041 contract guard and ADR-034 through ADR-040 reverse replay.
  Six schema bodies remain CI-only because they require the workflow-pinned
  `jsonschema`; one integration body requires `RUNNER_TEMP` and its preceding
  artifacts. Workflow YAML parsing with duplicate keys rejected and
  `git diff --check` both pass.
- Independent correctness review: **PASS** — no remaining P0–P3 finding.
  Application append-only storage still does not claim privileged PostgreSQL
  administration is cryptographic WORM.

### Next work

- Define an operator-controlled collection attempt that creates or selects the
  prerequisite exact captures under one aggregate SEC fair-access owner, while
  preserving per-request outcomes and the non-atomic capture window. Do not add
  autonomous scheduling until multi-replica coordination and retry ownership
  are explicit.
- Define correction/removal/amendment evidence and public conflict presentation
  separately. Do not turn ADR-041 agreement, conflict, or selected-reference
  coverage into a winner or complete-history claim.
- Keep public read API, authentication decision, freshness/source attribution,
  and Korean community UI behind a later publication gate.

## 2026-08-26 — ADR-042 SEC operator-controlled collection attempt

Status: internal bounded collection-attempt service, immutable V9 ledger, and
exact-evidence/provider-dispatch lifecycle implemented and verified offline.

ADR-042 coordinates one explicit root capture or one exact-root collection
attempt without adding an autonomous or public execution surface. It preserves
the non-atomic SEC capture window and treats every attempt outcome as evidence,
not as a complete or current filing-history claim.

### Scope and decisions

- Add only an unconditional internal application service. No controller, CLI,
  scheduler, startup hook, public/authenticated route, OpenAPI operation, or web
  consumer was added.
- Define `CAPTURE_ROOT` as at most one root provider invocation. Define
  `COLLECT_EXACT_ROOT` as one exact durable root plus zero or more
  `SELECT_EXACT` descriptor actions and at most one `CAPTURE_NOW` action.
  Selection-only and root-only manifest assembly are zero-network; every
  attempt has `maxProviderInvocations = 1`, with no retry, latest selection,
  fallback, or fetch-all behavior.
- Require a canonical nonzero lowercase UUID `operatorRequestId`. The same UUID
  and canonical command digest returns the existing attempt with zero provider
  or mutex interaction. Reusing the UUID with a changed command conflicts before
  provider use. Attempt identity excludes the attempted time so an identical
  replay converges on the original ledger.
- Treat missing exact root/selected-segment foreign-key evidence at initial
  claim as a sanitized admission rejection that creates no ledger. If the exact
  FK-bound plan was admitted but its evidence later cannot be reconstructed or
  verified, close the attempt as terminal
  `EXACT_EVIDENCE_VALIDATION_FAILED` with no provider invocation.
- Revalidate action-dependent cross-row compatibility during repository
  reconstruction and fail closed. V9 has no independent raw-SQL writer; any
  future external writer or multi-service ledger first needs an immutable action
  summary with exact foreign-key binding.
- Persist provider dispatch immediately before provider-port execution as the
  local authorization/handoff boundary. It is not proof that an HTTP request
  started or reached SEC. A dispatch without a terminal is
  `PROVIDER_DISPATCHED_INDETERMINATE` and is never automatically resumed,
  retried, or abandoned. A pre-dispatch mutex rejection may instead close as
  `PROVIDER_GATE_CLOSED` / `PROVIDER_INVOCATION_NOT_STARTED` without dispatch.
- Commit a newly `INSERTED` root/segment capture or assembled manifest with its
  succeeded terminal through the post-response local atomic committer. An
  `IDENTICAL_REPLAY` terminal references the already-durable exact artifact and
  does not claim reinsertion. Validate returned attempt identity, terminal
  shape, expected capture/manifest identities, and retained dispatch before
  trusting an adapter result. A local commit failure does not fabricate success
  or invoke the provider again.
- Serialize attempt-owned provider work with one nonblocking single-JVM mutex.
  Reuse the existing shared process-local SEC policy of 8 requests/second fixed
  spacing, 8 MiB decoded-response cap, 5-second connect timeout, 10-second read
  timeout, no automatic `429` retry, and `Retry-After` cooldown. This is not a
  distributed lock, multi-replica aggregate limiter, scheduler, or global retry
  owner.
- Keep CI and default verification disconnected from SEC. No live SEC request
  was performed for ADR-042 and no configured secret value was read, printed,
  or committed.

### Operator requirements

- Implementation and `SELECT_EXACT`-only execution need no new API key,
  provider account, payment, OAuth/EDGAR token, plugin, or secret.
- A future explicitly authorized provider-bound manual/live attempt requires
  root `.env` values `SEC_PROVIDER_ENABLED=true`,
  `SEC_BASE_URL=https://data.sec.gov`, a monitored `SEC_CONTACT_EMAIL`, and the
  existing `POSTGRES_HOST`, `POSTGRES_PORT`, `POSTGRES_DB`, `POSTGRES_USER`, and
  `POSTGRES_PASSWORD` settings. Values remain server-only and must not be put in
  chat, logs, documentation, or Git. Configuration alone starts no collection.

### Verification

- Focused ADR-042 offline domain/service/configuration/H2 persistence suite:
  **PASS** — 39 tests, zero failures, errors, or skips.
- Focused actual PostgreSQL 17 migration/persistence suite: **PASS** — 4 tests,
  zero failures, errors, or skips.
- Final full API Maven verification with actual PostgreSQL 17:
  **PASS** — 2,302 tests, zero failures, errors, or skips; Spring Boot JAR
  packaging completed with `BUILD SUCCESS`.
- Final post-review web regression: **PASS** — ESLint with zero warnings,
  42 Vitest files / 569 tests, and the Next.js production build with all 12
  static pages generated; no web implementation changed in ADR-042.
- Live SEC verification: **NOT RUN BY DESIGN** — CI and the ADR-042 validation
  path remained offline.

### Next work

- Require a separate ADR before adding an authenticated explicit operator
  trigger/status surface or indeterminate-attempt inspection and recovery
  policy. Do not reinterpret an indeterminate dispatch as safe to retry.
- Require a separate ADR before multi-replica live collection, global SEC rate
  coordination, distributed mutual exclusion, scheduler ownership, or retry
  control.
- Keep correction/removal semantics, public read API, freshness/source
  attribution, and Korean community UI behind their independent evidence and
  publication gates.

## 2026-08-26 — ADR-043 default-disabled local single-operator SEC attempt API

ADR-043 opens only the local HTTP verification boundary that ADR-042 deferred.
It keeps the public community product anonymous and read-only, keeps SEC live
access disabled, and does not treat the developer's existing domain as an
authentication, TLS, or actor-audit control.

### Scope and decisions

- Add exactly two command routes and one exact status route under
  `/internal/v1/sec/collection-attempts`: POST `root`, POST `exact-root`, and
  GET `{attemptId}`. Add no list, search, latest, retry, resume, cancel,
  abandon, resolve, scheduler, startup collector, browser consumer, or public
  OpenAPI product operation.
- Keep the complete surface absent unless `OPERATOR_API_ENABLED=true`. Require
  one valid lowercase 64-hex `OPERATOR_API_TOKEN_SHA256` when enabled and fail
  startup closed otherwise. Authenticate an opaque raw Bearer value by hashing
  it and using constant-time comparison; missing, malformed, and wrong tokens
  share one sanitized `401 OPERATOR_AUTHENTICATION_REQUIRED` result.
- Treat this token as one local authority only, not as a durable human actor.
  Store no token, digest, authorization header, raw provider payload, arbitrary
  exception, User-Agent, contact email, or database detail in a response or
  log. The canonical standard-Base64 token for exactly 32 random bytes stays in
  the invoking shell or a password
  manager; only its digest may enter the gitignored local `.env`.
- Preserve ADR-042 exactly. New and identical-replay POSTs both return the
  immutable representation with `200`, `Location`, and `Cache-Control:
  no-store`; GET returns `200` and `no-store`. A reused UUID with changed
  canonical command is `409 OPERATOR_REQUEST_CONFLICT`. Invalid command shapes
  are `400 INVALID_OPERATOR_COMMAND`. A valid unknown attempt is `404
  SEC_COLLECTION_ATTEMPT_NOT_FOUND`.
- Distinguish an initial FK admission rejection from a durable validation
  terminal. Missing/incompatible root or segment evidence that the database
  cannot admit is sanitized `422 EXACT_EVIDENCE_NOT_ADMITTED` with no ledger
  or provider request. Evidence admitted and then failing exact reconstruction
  returns `200` with the durable ADR-042
  `EXACT_EVIDENCE_VALIDATION_FAILED` terminal. Provider failure and
  `PROVIDER_DISPATCHED_INDETERMINATE` are also represented as durable `200`
  state rather than rewritten as transport failure or retry permission.
- Project only the closed command identity, lifecycle, descriptor action,
  provider dispatch, terminal disposition/failure, exact artifact identity,
  timestamp, and three explicit safety fields. `attemptIndeterminate` and
  `providerStartOrResponseUnknown` conservatively expose uncertainty, while
  `automaticRetryAllowed` is always false. Preserve unavailable facts as JSON
  `null`; do not add a replay Boolean or infer missing facts.
- Restrict this phase to one loopback/local API JVM and PostgreSQL. Keep
  the entire embedded server programmatically bound to
  `InetAddress.getLoopbackAddress()` whenever the operator boundary is enabled,
  overriding a broader configured address. Keep `SEC_PROVIDER_ENABLED=false`
  for every ADR-043 test so a provider-bound command can exercise the durable
  gate-closed result but cannot contact SEC. Default test, `verify`, and CI
  remain offline.
- Preserve Spring Security's strict HTTP firewall and adapt its pre-MVC
  rejections to the existing closed `400 INVALID_QUERY` problem contract. Use
  a constant safe instance, reflect no rejected URI/query/credential content,
  and add `no-store` for the operator prefix.
- Fix the PostgreSQL 17 equal-body insert race exposed by full verification.
  Both SEC capture repositories now use targetless `ON CONFLICT DO NOTHING` so
  either of the table's two body-identity constraints can arbitrate the race;
  the existing reread still compares the exact stored length and bytes before
  accepting an identical replay.
- Prohibit remote deployment of the static-token boundary. A future deployment
  decision must add HTTPS, managed issuer/audience-bound identity, a private
  API origin, durable append-only actor audit, CSRF/origin controls if cookies
  are introduced, secret rotation, private actuator access, and ingress
  controls. Live operation remains exactly one API replica until distributed
  rate, cooldown, and mutex ownership is reviewed.

### Operator requirements

- Local ADR-043 verification needs no domain, DNS change, Cloudflare account,
  OAuth client, SEC API key/account/payment, or monitored `SEC_CONTACT_EMAIL`.
  It needs only one locally generated raw token retained outside the repository,
  its SHA-256 digest in `OPERATOR_API_TOKEN_SHA256`, the explicit local enable
  flag, and the existing PostgreSQL configuration. The application enforces
  the loopback bind automatically while enabled.
- The exact PowerShell generation, root `.env` placement, provider-disabled
  replay/status smoke, and cleanup steps are recorded in `apps/api/README.md`.
  The operator must not send the raw token, database password, contact email,
  or any future identity-provider secret in chat.

### Verification

- ADR-043 decision, root/API README parity, safe `.env.example` defaults, exact
  route/status table, secret boundary, one-replica rule, and deployment
  prerequisites: **PASS** by documentation/static review.
- Focused ADR-043 operator properties, security, loopback, query, controller,
  and offline integration suite: **PASS** — 45 tests, zero failures/errors/skips.
- Full API `verify`: **PASS** — 2,347 tests, zero failures/errors/skips,
  including PostgreSQL 17 Testcontainers, Flyway v1-to-v9 paths, both
  equal-body concurrency regressions, authenticated offline execution/replay,
  strict-firewall handling, and a real loopback-bound Tomcat instance.
- Web regression verification: **PASS** — ESLint, 569/569 Vitest tests,
  production build, and 72/72 Playwright checks across 1440, 1280, and 390 px.
- Compose interpolation with `.env.example`: **PASS**.
- Live SEC verification: **NOT RUN BY DESIGN** — `SEC_PROVIDER_ENABLED` remains
  false and ADR-043 authorizes no live provider check.

### Next work

- Before deployment, choose the actual web/API hosting topology and managed
  identity provider, then add durable actor attribution and verify that the API
  origin cannot bypass it. The current domain alone satisfies none of those
  requirements.
- Keep any browser/BFF operator UI, multi-operator authorization, token
  rotation endpoint, indeterminate resolution, and multi-replica coordinator
  behind separate reviewed contracts.

## 2026-08-26 — ADR-044 disposable offline local operator acceptance harness

ADR-044 closes the remaining pre-deployment local evidence gap between the
separately tested HTTP, loopback-server, and PostgreSQL boundaries. It adds one
operator-owned command that exercises the packaged application over real TCP
against a disposable PostgreSQL 17 Compose project while SEC remains unable to
receive traffic.

### Scope and decisions

- Add `scripts/verify-local-operator-api.ps1` as the only executable surface.
  Add no application source, test-only runtime bean/profile, migration, route,
  provider, public OpenAPI operation, fixture, or web behavior.
- Require PowerShell 7, Java 21, a local Docker endpoint, and Compose v2, plus standard POSIX
  `sh` for the non-executable Maven wrapper checkout on macOS/Linux. Package
  through the checked-in wrapper after proving it uses the checked Java 21
  runtime, direct its output to the validated harness temp directory, start the
  repackaged Spring Boot JAR as one directly owned child process, and use
  distinct dynamically selected loopback ports. Reject a remote Docker endpoint
  before daemon contact, then pin the validated endpoint for every Docker and
  Compose operation so a later context change cannot redirect the run.
- Share one atomically created root `/.wsr-local-acceptance.lock` with ADR-045
  across package/read/run/cleanup, including path aliases and login sessions. A
  second participating command or stale lock from a hard termination fails
  before package or Compose work rather than racing build artifacts.
- Create a unique validated Compose project and therefore a unique PostgreSQL
  volume for every run. Never read or edit the ignored root `.env`, select the
  default Compose project, reuse `postgres-data`, or fall back to an existing
  developer database.
- Generate a canonical standard-Base64 32-byte Bearer token, its lowercase
  SHA-256 digest, and a disposable database password in process memory. Do not
  print or write the raw token/digest, pass them on a command line, or retain a
  token file. Redact failure logs and clear mutable credential arrays during
  cleanup.
- Remove inherited Spring, server, management, datasource/Hikari, JNDI,
  direct-provider, Flyway, Java-option, and logging namespace settings before
  applying the exact high-precedence disposable database and process allowlist.
  Force the operator boundary on, the SEC provider and live-
  smoke gate off, and the provider origin to an unavailable loopback address.
  The whole API listener remains loopback-only through ADR-043. Keep Tomcat's
  base under the validated temp directory and explicitly disable access logs.
  Fix `SPRING_CONFIG_LOCATION=classpath:/` so caller-owned application/config
  files cannot enter the child.
- Verify real health and HTTP `401`, provider-disabled `200`, exact replay,
  exact GET, `409`, and `422` responses. Require the durable failure to remain
  `PROVIDER_GATE_CLOSED` / `PROVIDER_INVOCATION_NOT_STARTED`, with null dispatch
  and automatic retry false.
- Inspect the disposable database from its own container and require exactly
  one attempt, zero provider dispatches, and one outcome. Validate cleanup
  targets before stopping the exact child PID, removing the exact Compose
  project/volume, and recursively removing only the harness-owned OS temporary
  directory.
- Keep CI deterministic by statically guarding the harness and parsing its
  PowerShell AST, while leaving composed process/container execution as the
  explicit local pre-deployment gate.

### Operator requirements

- No domain, DNS change, API key, SEC account, paid plan, OAuth client,
  `SEC_CONTACT_EMAIL`, root `.env` edit, or user-provided token is needed.
- Docker Desktop or another Docker daemon must be running, and Java 21 plus
  PowerShell 7 must be on `PATH`. The first run may download normal Maven
  dependencies or `postgres:17-alpine` if uncached; it is not authorized to
  contact SEC.
- Run from the repository root with
  `pwsh -NoProfile -File ./scripts/verify-local-operator-api.ps1`. Use
  `-SkipPackage` only when the current source has already been packaged.

### Verification

- PowerShell AST parse: **PASS**.
- Extended ADR-043/044 static guard and all nine SEC guard slices: **PASS**;
  all 48 embedded Python bodies compile, workflow YAML parses, the PowerShell
  parse-only step occurs once, and CI contains zero executable full-harness
  invocations.
- Full disposable composed acceptance run: **PASS** — packaged JAR, real
  loopback TCP, PostgreSQL 17 health/migrations, authentication, immutable
  replay/status, conflict/admission rejection, exact `1|0|1` ledger counts, and
  owned-resource cleanup all completed successfully.
- Post-ADR-045 isolation rerun: **PASS** — the Maven wrapper reported Java 21,
  all compile/repackage output stayed under the validated operator temp root,
  the full `1|0|1` contract passed, and cleanup removed that build with its
  other resources.
- Held-lock collision check: **PASS** — both ADR-044 and ADR-045 exited nonzero
  in under one second, before package/build/Compose work, while the common
  root lock file was held by a separate process.
- Immediate `-SkipPackage` replay: **PASS** with the same contract; a Docker
  inventory check found zero harness-named containers or volumes afterward.
- Startup-failure cleanup was also exercised during environment-isolation
  hardening; after the correction and final successful run, inventory again
  found zero harness-named containers or volumes.
- Final API `verify`: **PASS** — 2,347 tests, zero failures/errors/skips,
  PostgreSQL 17 Testcontainers/Flyway paths, JAR packaging, and Spring repackage
  all completed with `BUILD SUCCESS`.
- Final web regression: **PASS** — ESLint, 42 Vitest files / 569 tests,
  production build with all 12 static-generation work items, and 72/72
  Playwright checks at the existing 1440, 1280, and 390 px viewports.
- Compose interpolation through `.env.example` and final patch whitespace:
  **PASS**.
- Live SEC verification: **NOT RUN BY DESIGN** — the provider flag and live-
  smoke flag were false and the configured provider origin was closed loopback.

### Next work

- Keep this command as the final local acceptance gate before deployment work.
  The next deployment decision still must choose hosting topology, TLS, managed
  identity, private origin enforcement, durable actor audit, and secret
  lifecycle; the existing domain alone is not sufficient.

## 2026-08-26 — ADR-045 disposable offline local full-stack acceptance harness

ADR-045 adds the public production-build counterpart to ADR-044. It turns the
existing CI call-audit integration evidence into one collision-resistant local
command without changing a product route, API contract, migration, canonical
fixture, or provider.

### Scope and decisions

- Add `scripts/verify-local-full-stack.ps1`, invoked from the repository root as
  `pwsh -NoProfile -File ./scripts/verify-local-full-stack.ps1`. Require
  PowerShell 7, Java 21, Node.js 24, Docker Compose v2, installed workspace
  dependencies, and Playwright Chromium.
- Package the API beneath the validated OS temp root and copy only the required
  web source/config/package manifest plus canonical DEMO fixtures into an
  ignored, secret-free `apps/web/.wsr-local-full-stack-<run-id>/apps/web`
  mirror. Build and run Next there in explicit `CALL_AUDIT_PROVIDER=api` /
  `NEXT_PUBLIC_DATA_MODE=DEMO` mode, leaving the caller's `next-env.d.ts`,
  `tsconfig.json`, standard `.next`, and local env files untouched. Verify that
  the Maven wrapper uses the checked Java 21 runtime. `-SkipPackage` and
  `-SkipWebBuild` are explicit reuse controls, not alternate source modes.
- Share the atomically created root `/.wsr-local-acceptance.lock` with ADR-044
  for the complete package/build/run/cleanup window. Force all nine other current
  web provider selectors to `fixture` in build, production runtime, and browser
  child environments, leaving call audit as the sole API-mode selector.
- Reject remote Docker endpoints before daemon contact, pin the validated local
  endpoint for every Docker/Compose operation, allocate distinct loopback ports,
  and create one validated random Compose
  project with its own PostgreSQL 17 volume. Force Spring datasource and Flyway
  to that database; never activate `local`, read the root `.env`, select the
  default project/volume, or reuse a developer service.
- Start the packaged JAR and production Next server as directly owned child
  processes. Force SEC, SEC live smoke, and the operator API off; keep the
  disabled SEC origin at closed loopback. No provider network operation is
  authorized. Remove inherited Spring, server, management, datasource/Hikari,
  JNDI, direct-provider, Flyway, Java-option, and logging namespace settings
  before applying the exact process allowlist. Clear Node/browser proxy and
  module-injection variables, including mixed/lowercase HTTP(S)/all/no-proxy
  aliases on case-sensitive hosts, and restrict Spring config lookup to
  `classpath:/`.
- Smoke-check `/`, `/market`, `/calls`, both DEMO call-detail goldens,
  `/institutions`, `/analysts`, both map universes, `/markets/sp500`,
  `/screener`, and `/methodology` for `200 text/html` plus the shared product
  shell.
- Reuse the existing call-list, revision, and outcome specs once at
  `chromium-1280` against the externally managed production server. The three
  specs now compare browser traffic with the configured dynamic API origin
  instead of a fixed `localhost:8080` assumption.
- Add an exact Playwright external-server selector. The local production HTTP
  harness injects only the non-secret locale preference into its isolated
  context because the production app correctly marks that cookie `Secure`;
  normal browser runs retain real locale server-action coverage. Use exact
  `127.0.0.1` and Chromium `--no-proxy-server` for this harness.
- Require API-only `NOT_EXPOSED` presentation, zero browser calls to the private
  API origin, set membership for the existing 13 exact Spring access-log
  lines, and PostgreSQL counts `3 calls | 2 revisions | 4 outcomes`.
- On success or failure, stop the exact web PID and API PID, remove only the
  regex-validated Compose project/volume, redact the disposable database
  password from bounded log tails, remove only the regex-validated source mirror
  directly below `apps/web`, and recursively remove only the
  validated harness directory under the operating-system temporary root.
  Set ownership flags only after successful atomic directory creation and never
  clean a pre-existing exact-name path.

### Routes and module structure

- No product or Spring route was added or renamed. The harness composes the 12
  existing primary web routes, health, call list, and coherent
  detail/context/revisions/outcomes reads.
- `apps/web/playwright.config.ts` owns the exact externally managed server
  switch. `apps/web/e2e/runtime-assertions.ts` owns the HTTP-only locale test
  helper, while the three existing call-audit specs own private-origin
  observation and browser expectations.
- `apps/web/tsconfig.json` excludes the ephemeral mirror from concurrent editor
  type-checks. `apps/api/pom.xml` keeps normal `target` output by default while
  allowing the two local harnesses to select their validated temp build root.
- ADR-045, the root README, this log, and a dedicated parse-only workflow guard
  own the operator contract and reproducibility evidence. The existing CI
  `call-audit-integration` job remains the behavioral CI owner.

### Operator requirements

- No domain, DNS change, API key, SEC account, paid plan, OAuth client,
  `SEC_CONTACT_EMAIL`, operator token, root `.env` edit, or other user secret is
  needed. Docker must be running.
- If workspace dependencies or Chromium are absent, run
  `pnpm install --frozen-lockfile` and
  `pnpm --dir apps/web exec playwright install chromium`, then retry.

### Verification

- Default composed command: **PASS** — Maven package, API-mode Next production
  build, PostgreSQL 17/Flyway v1-to-v9, loopback Spring and production Next,
  12/12 primary route smokes, 3/3 focused Chromium scenarios, all 13 required
  Spring reads, exact `3|2|4` database counts, and owned cleanup.
- Source-mirror default rerun: **PASS** — Maven compiled/repackaged only under
  the validated OS temp root, Next built/started only from its secret-free
  harness-owned source mirror, all 12 routes and 3/3 Chromium scenarios passed,
  exact 13 reads and `3|2|4` counts passed, and both build outputs were removed.
- Shared-lock collision check: **PASS** — ADR-044 and ADR-045 each failed in
  under one second before package/build/Compose while a separate process held
  the root lock file.
- Reuse-mode composed command: **PASS** with `-SkipPackage -SkipWebBuild` and the
  same route/browser/access/database contract.
- Poisoned-parent reuse checks: **PASS** — caller-controlled `OS`, invalid
  Java/Maven/Node, lowercase `spring_application_json`, dotted/hyphen Spring,
  server, management, datasource/Hikari/JNDI, direct-provider, logging,
  mixed/lowercase proxy, all nine non-call web selectors, and public API-origin
  values were ignored, removed, or overridden. Runs with a local Docker context
  plus a conflicting remote `DOCKER_HOST` also passed through the captured local
  endpoint. Both ADR-044 and ADR-045 retained their exact acceptance contracts.
- Failure cleanup was exercised while hardening health negotiation, process
  variable naming, and local production HTTP cookie handling. Every failed and
  successful run removed its exact web/API processes, Compose project, volume,
  and temporary browser reports.
- The default source-mirror harness never wrote caller-owned
  `apps/web/next-env.d.ts`, `apps/web/tsconfig.json`, or standard `.next`.
  A separate ordinary production-build regression confirmed that Next can
  rewrite `next-env.d.ts`; that one generated line was restored immediately and
  its exact SHA-256 returned to
  `7ad303e40d4fddf44f156129e397511953a71481c5cfd86b1862649aaaf240cc`.
  The retry-free full Playwright run retained that hash. Final inventories found
  zero harness source mirrors, `wsr-fullstack-*` containers, or volumes.
- Final web regression: **PASS** — installed ESLint, 42 Vitest files / 569
  tests, production build, targeted 18/18 standard-localhost browser checks,
  and retry-free 72/72 Playwright checks at 1440, 1280, and 390 px.
- Final API `verify`: **PASS** — 2,347 tests, zero failures/errors/skips,
  PostgreSQL 17 Testcontainers/Flyway paths, and `BUILD SUCCESS`.
- CI/static verification: **PASS** — both ADR-043/044 and ADR-045 guards,
  PowerShell AST parsing, all 49 embedded workflow Python bodies, four-job YAML
  parsing, Compose interpolation, and `git diff --check`.
- Live SEC verification: **NOT RUN BY DESIGN** — provider and live-smoke gates
  were false and the provider origin was closed loopback.

### Next work

- Keep ADR-044 for the specialized local operator boundary and ADR-045 for the
  public production full stack. Run both before deployment changes.
- The next deployment decision still requires hosting topology, TLS, managed
  identity, private API origin enforcement, actor audit, secret lifecycle, and
  licensed-provider publication policy. The existing domain alone supplies
  none of these controls.

## 2026-08-26 — ADR-046 Ubuntu home-server deployment foundation

ADR-046 selects a direct, cloud-free Ubuntu home-server topology for the
public DEMO site and adds an independent production Compose boundary. It does
not install software, change ports, firewall, DNS, or router state on the
current development computer.

### Scope and decisions

- Add multi-stage Java 21 and Node.js 24 application images, a derived Caddy
  2.11.4 image, and PostgreSQL 17 in an independent
  `deploy/home-server/compose.yaml`. Release images carry the selected Git SHA;
  runtime processes are non-root, read-only where applicable, capability-
  dropped, resource-bounded, health-checked, and log-rotated.
- Publish only production Caddy on TCP 80/443. PostgreSQL, Spring, and Next
  publish no host ports. Keep `edge-internal`, `app-internal`, and
  `db-internal` isolated. The two operator-selected ingress services join the
  standard `public-egress` bridge; the production wrapper and rehearsal harness
  select only their intended profile. Caddy has no membership in the API or
  database networks, and Next is the sole bridge between edge and API.
- Force the public stack to explicit DEMO mode, fixture market/analyst
  providers, API-backed call audit, disabled SEC, and disabled operator API.
  Spring reads the PostgreSQL password from a Compose secret/config tree;
  neither Compose environment nor web/Caddy receives its value.
- Add automatic HTTPS and HTTP-to-HTTPS handling through Caddy while retaining
  only HTTP/1.1 and HTTP/2 for the first release. Bound request bodies and
  headers, reject methods other than GET/HEAD/POST, strip server disclosure,
  and add the initial response-security headers. Caddy uses the validated
  public domain as the production default SNI. HSTS stays deferred until
  external HTTPS is proven stable.
- Add a private-TLS rehearsal profile using the shared production Caddyfile at
  `https://127.0.0.1:8443`. The host publishes one preflighted loopback port in
  `18080-18179`; Caddy uses an ephemeral local CA and a rehearsal-only default
  SNI of `127.0.0.1`, so production `Secure` cookie behavior is exercised
  without public ACME, host trust installation, or a bind on 80/443. Its script
  rejects remote Docker endpoints and hostile inherited Docker/Compose/WSR
  overrides, creates a random secret and uniquely tagged project, and removes
  only its owned resources on success or failure.
- Add a read-only Ubuntu preflight with `host`, reusable `contract`, and fail-
  closed `publish` modes.
  Publication requires supported Ubuntu/architecture, at least two logical
  CPUs, 4 GiB RAM, 50 GiB free storage, a local Docker daemon, Compose 2.20.0+,
  exact domain/email/full Git SHA, a clean checkout, an absolute non-symlink
  single-link mode-400 secret with no symlinked parent, bounded non-empty size,
  and numeric UID/GID `10001:10001` ownership inside a root-owned traversal-
  only mode-711 directory, a rootful non-userns-remapped Docker daemon, exact A/AAAA-to-
  attested-address matching, a static
  direct-ingress policy, and free 80/443. It still reports
  `PENDING_EXTERNAL_INGRESS` because router/ISP reachability needs an external
  test. `compose-production.sh` pins the exact env file and sanitized local
  Docker boundary, exposes only six fixed no-extra-argument lifecycle actions,
  and reruns the complete env/source/secret/DNS contract immediately before
  each production Compose command. CPU and memory envelopes are fixed in
  Compose rather than accepted as unbounded operator inputs.
- Recommend Ubuntu Server 24.04 LTS, 4 logical cores and 8 GiB RAM for the
  initial approximately 100 readers; allow 26.04 LTS and amd64/arm64. Treat the
  planned 1 TB disk as capacity, not as an off-device backup.
- Record exact operator inputs only for cutover:
  `deploy/home-server/.env.production`, a monitored ACME email, domain, Git
  SHA, confirmed public ingress/address policy, and the locally generated
  `/etc/wall-street-receipts/secrets/postgres_password`. No API key, account,
  domain password, router password, public IP value, or database secret is
  requested in chat.

### Routes and module structure

- No product route, Spring endpoint, migration, schema, or canonical fixture
  changed. The rehearsal composes the existing 12 primary web routes through
  Caddy and the existing Spring/PostgreSQL fixture boundary.
- `deploy/home-server/` owns three Dockerfiles and their deny-all build
  contexts, one shared Caddyfile, the Compose model, non-secret env template,
  read-only host preflight, sanitized production wrapper, and operator runbook.
  ADR-046 owns the topology decision.
- `scripts/verify-home-server-deployment.py` owns the rendered-model semantic
  guard and 15-case negative mutation matrix. The PowerShell script owns local
  build/runtime/browser evidence. Playwright configuration and evidence tests
  support private production HTTPS, require zero retries for this rehearsal,
  assert `Secure` locale persistence, and retain exact 404 and anchor evidence
  across HTTP/2 and responsive layout timing. CI runs the semantic guard, Bash
  syntax check, and PowerShell AST parse without starting the deployment.

### Verification

- Semantic Compose/negative-matrix guard: **PASS** — exact five-service/four-
  network model, profiles, only production TCP 80/443, numeric-loopback private
  TLS rehearsal, non-root hardening, exact mounts/environments/health gates,
  secret flow, provider gates, and all 15 rejected mutations.
- Disposable Docker plus browser rehearsal: **PASS** — PostgreSQL 17 with
  Flyway v1-to-v9, Spring, production Next, and non-root Caddy reached healthy
  through `https://127.0.0.1:<random 18080-18179 port>`. All 12 public routes
  returned `200 text/html`; DEMO/source/timestamp surfaces, response headers,
  POST allowance, PUT 405, no backend host bindings, writable non-root Caddy
  state, and exact `3|2|4` database evidence passed. Playwright passed **72/72
  with zero retries** at 1440, 1280, and 390 px through the same endpoint,
  including real production `Secure` locale cookies. The private CA was trusted
  only by scoped clients and was never installed. Cleanup removed the exact
  containers, networks, volume, images, temporary secret, and browser output.
- Regression suites: **PASS** — web ESLint, 42 Vitest files / 569 tests, and
  API Maven `verify` with 2,347 tests and zero failures/errors/skips. The
  production web image build generated all 12 expected application routes.
- Static/tooling checks: **PASS** — shared Caddyfile format/runtime validation,
  PowerShell AST parse, Bash `-n`, four-job workflow YAML plus 49 embedded
  Python bodies, exact fixed CPU/RAM envelopes, execution-boundary contract
  revalidation, and rejection of `-p`, `-pNAME`, `run --publish`,
  `down --volumes`, and caller-supplied `--build-arg`,
  `git diff --check`, exact zero cleanup inventory, and the caller-owned
  `apps/web/next-env.d.ts` SHA-256
  `7ad303e40d4fddf44f156129e397511953a71481c5cfd86b1862649aaaf240cc`.
- Live/public deployment: **NOT RUN BY DESIGN**. Domain DNS, public/static or
  IPv6 ingress, CGNAT status, router forwarding, and external TLS remain facts
  the future server operator must supply or prove at cutover.

### Next work

- ADR-047 now adds the logical PostgreSQL backup, separate-device contract,
  empty-target restore rehearsal, read-only retention plan, and release-image
  evidence foundation. Activation still requires the actual HDD facts and does
  not admit non-DEMO or irreplaceable data while `PENDING_BACKUP_DEVICE` or the
  reduced-assurance `none-demo-only` encryption choice remains.
- Actual publication remains blocked until the future Ubuntu server exists and
  the operator confirms domain/subdomain, monitored ACME email, Git SHA,
  CGNAT/public-address/static policy, router forwarding, and external HTTPS.

## 2026-08-26 — ADR-047 separate-device backup and recovery rehearsal

ADR-047 adds a manual, fail-closed PostgreSQL recovery boundary for the future
Ubuntu home server. It does not format, partition, encrypt, unlock, mount, or
schedule a device on this development computer, and it does not expose an
in-place production restore or automatic deletion action.

### Scope and decisions

- Add a fixed, non-secret `/etc/wall-street-receipts/backup.conf` contract for
  one exact mount path, filesystem UUID, and explicit `luks2` or reduced-
  assurance `none-demo-only` choice. The preflight requires an ext4/xfs mount
  with `rw,nodev,nosuid,noexec`, a versioned store identity, bounded ownership,
  a simple allowlisted block graph, and Docker storage on a distinct directly
  observed SATA/USB/NVMe transport-plus-serial identity. Ambiguous LVM, RAID,
  mapper, virtual, network, multi-disk, and nested-mount layouts fail closed.
- Keep hardware facts honest: transport/serial hashing cannot see through a
  hidden hardware RAID controller or VM backing store, so actual cutover also
  requires operator attestation from the physical server. The repository never
  treats the development machine or an ordinary temporary directory as proof
  of `PENDING_BACKUP_DEVICE` completion.
- Add five fixed actions only: `preflight`, `create`, `status`,
  `rehearse-latest`, and read-only `retention-plan`. The wrapper pins the local
  Docker endpoint and production Compose project, accepts no caller-selected
  path, recovery-point ID, Docker option, destructive retention flag, or
  production-volume target.
- Capture exactly one healthy, label-bound PostgreSQL container with no host
  port and exactly its writable named data volume plus read-only password bind.
  Stream `pg_dump --format=custom --compress=6 --no-owner --no-privileges
  --no-password` to host-only same-filesystem staging; the backup HDD is never
  mounted into a container.
- Publish exactly four single-link members — `database.dump`,
  `database.dump.sha256`, `database.inventory`, and canonical K/V `manifest` —
  only after archive-list, byte length, SHA-256, file mode/owner, directory, and
  fsync checks. A unique UTC-second allocator and atomic no-clobber rename keep
  partial or same-second work out of newest-point and retention selection.
- Record database and release-image evidence separately. The manifest binds the
  observed PostgreSQL/API/web/Caddy references, exact image IDs, OCI revisions,
  one derived Git SHA when available, and the canonical dump options without
  copying env files, the PostgreSQL password, or Caddy private state. Missing
  stateless-image facts do not invalidate a sound database dump and never
  become an invented rollback claim.
- Verify a complete bundle before allocating a fresh label-owned volume and
  container. The restore target uses the recorded PostgreSQL image, no port,
  `network=none`, one writable non-production volume, an empty pre-restore
  public schema, and fail-fast single-transaction/no-owner/no-ACL restore. Its
  ID, labels, image, live network set, port set, and exact mount set are checked
  before start, after health, and immediately before evidence capture.
- Generate read-only evidence from the restored database. The production
  parser requires canonical singleton fields, unique successful Flyway ranks,
  platform metadata, a complete public-table inventory, and matching observed
  counts for the key call tables. Those dynamic values are hash-bound to the
  restore manifest; production success is not hard-coded to the DEMO fixture's
  current Flyway or row counts.
- Plan, but never apply, the union of the newest 14 UTC daily, 8 ISO-weekly,
  and 12 UTC monthly points plus the newest image-evidence-ready point. Unknown,
  partial, malformed, and unverified entries are excluded and reported. A full
  disk blocks new publication instead of deleting the only known-good point.
- Keep `PENDING_OFFSITE_COPY` explicit. An additional HDD in the same server
  improves device-failure recovery but is not off-site, offline, air-gapped, or
  protected from whole-host incidents and privileged deletion.
- Never emit `rollback-ready`. Matching database and release-image evidence can
  report only `image-evidence-ready` with schema compatibility still required;
  exact Git-object resolution, Flyway compatibility, fresh-volume promotion,
  and previous-volume preservation remain later gates.

### Routes and module structure

- No product route, Spring endpoint, OpenAPI contract, migration, schema,
  canonical fixture, or provider integration changed. No API key, provider
  account, domain credential, cloud account, SEC contact email, or new user
  secret is required for this local implementation and rehearsal.
- `deploy/home-server/backup.conf.example`, `recovery-common.sh`,
  `recovery-preflight.sh`, `recovery-production.sh`, and
  `database-evidence.sql` own the production configuration, storage/runtime
  invariants, fixed command surface, and restored-database evidence query.
  `compose.yaml` adds the exact source-database identity and data-volume labels.
- `scripts/verify-home-server-recovery.py` owns the source-level contract and
  mutation matrix. `verify-home-server-database-evidence.sh` executes dynamic
  evidence parser fixtures, `verify-home-server-retention.sh` executes the
  fixed UTC policy, and `verify-home-server-recovery.ps1` composes the full
  disposable Docker rehearsal through the hardened ADR-046 harness.
- ADR-047 records the recovery decision; the root and home-server READMEs carry
  operator commands, limitations, and the exact future hardware inputs.

### Verification

- ADR-047 semantic/source guard: **PASS** — exact configuration, mount/store
  identity, source database, four-member bundle, custom dump options, fsync and
  publication order, fresh restore, dynamic evidence, read-only retention,
  image evidence, rollback blocking, and **109 negative cases**, including 40
  real shell-source and 6 local-rehearsal mutations.
- ADR-046 deployment guard and both PowerShell AST parses: **PASS** after the
  recovery harness gained exact source/target mount sets, live network checks,
  evidence-time revalidation, anonymous-volume cleanup, and image tag-to-ID
  ownership checks.
- Bash execution fixtures: **PASS** — a non-DEMO `17|11|23` restored-count
  sample was accepted while five mismatch/duplicate/missing/unknown variants
  were rejected; the fixed UTC planner retained exact 14 daily, 8 weekly, 12
  monthly, and image-evidence selections. Bash syntax and ShellCheck passed for
  the recovery scripts and fixtures.
- Disposable Docker recovery rehearsal: **PASS** — real PostgreSQL 17 custom
  dump and exact four-member bundle, byte-flip rejection, truncated-and-
  rehashed full-restore rejection, fresh `network=none`/no-port/one-volume
  restore, Flyway v1-to-v9 plus DEMO `3|2|4` evidence, all 12 public route
  smokes through private Caddy, and exact owned container, volume, network,
  image-tag, temporary-secret, and temporary-directory cleanup.
- Live Ubuntu/HDD validation: **NOT RUN BY DESIGN**. The future physical server,
  backup disk, mount, UUID, filesystem, capacity, direct connection, encryption
  choice, reboot/unlock behavior, schedule, and external/offline copy do not yet
  exist as verified facts.

### Next work

- When the HDD is selected, collect on the Ubuntu server — not in chat from
  this development PC — its exact `/dev/...` path, filesystem UUID, ext4/xfs
  type, capacity, SATA/USB/NVMe transport, and simple block ancestry. Decide
  `luks2` versus `none-demo-only`; keep any passphrase and recovery key local.
- Review the actual Ubuntu storage layout before installation. Default LVM,
  RAID, virtualization, or shared-controller topology needs a later explicitly
  tested decision rather than weakening this fail-closed contract.
- Choose a backup schedule/time zone and recovery-point objective only after
  the mount and any LUKS unlock behavior survive a real server reboot.
- Design one off-site or offline copy and its credential/rotation boundary;
  until then `PENDING_OFFSITE_COPY` remains.
- Add exact release Git-object/Flyway schema compatibility and fresh-volume
  promotion/rollback rehearsal before any command can claim rollback readiness
  or switch production data generations.

## 2026-08-26 — ADR-048 exact release-schema compatibility gate

ADR-048 closes the schema-compatibility portion of ADR-047 without adding a
production-volume switch or a rollback-ready claim. The gate compares the exact
Git commit, packaged API migration resources, and restored Flyway history that
were recorded for one verified backup. It never substitutes the current
working tree, fetches a missing object, pulls a missing image, or connects the
application inspector to PostgreSQL.

### Scope and decisions

- Upgrade new restored-database observations to evidence v2. Each successful
  Flyway row now binds contiguous installed rank and version, UTF-8-hex
  description and script, exact `SQL` type, signed checksum, and success state.
  Legacy v1 evidence remains identifiable restore proof but cannot satisfy the
  exact compatibility gate; the operator must run a new restore rehearsal.
- Pin Flyway `11.7.2` and add the reserved offline API command
  `--wsr-release-schema-inventory`. It runs before Spring Boot, scans the
  migrations actually packaged on the runtime classpath, rejects empty,
  malformed UTF-8, BOM, nested, repeatable, noncanonical, duplicate, gapped,
  oversized, or unsupported resources, and calculates checksums with the exact
  packaged Flyway implementation rather than a reimplementation.
- Add fixed action `schema-check-latest`. It accepts no caller-selected SHA,
  ref, path, image, backup ID, or Docker option. It revalidates the latest
  completed backup and immutable restore evidence before doing any comparison.
- Resolve only the full lowercase SHA-1 commit recorded in the backup. Inherited
  `GIT_*` overrides, replacement objects, optional lazy fetching, partial or
  promisor repositories, included repository configuration, and alternate
  object stores are rejected or disabled. Missing commits and blobs remain
  missing; the action does not read `HEAD` or invoke a Git worktree mutation.
- Inspect the exact locally present API image ID with `--pull never` in one
  random label-owned container. The inspector has `network=none`, a read-only
  root, no mounts or ports, all capabilities dropped, no-new-privileges, 384
  MiB memory with no swap, one CPU, 128 PIDs, no Docker log driver, a 30-second
  timeout, and a 1 MiB output ceiling. Exact cleanup is required for success.
- Require exact equality across all three views: Git blob SHA-256 and byte
  count, image filename/resource/checksum tuple, and restored Flyway
  rank/version/description/type/script/checksum tuple. Count, order, rename,
  byte, checksum, type, or history drift fails closed with a stable reason.
- Preserve the recovery boundary. Exact schema compatibility can emit only
  `compatible-exact-recorded-release`; rollback readiness remains blocked on
  fresh-volume promotion, previous-volume preservation, durable artifact
  custody, and the still-pending off-site/offline copy.

### Routes and module structure

- No product route, Spring HTTP endpoint, OpenAPI operation, database migration,
  fixture, provider integration, or UI surface changed. No API key, provider
  account, domain, ACME email, router credential, HDD fact, or new secret was
  needed for this implementation or local rehearsal.
- `ReleaseSchemaInventoryCommand` owns the bounded runtime inventory producer;
  its focused JUnit suite owns exact V1-through-V9 output and malformed-resource
  cases. `WallStreetReceiptsApiApplication` dispatches the one reserved command
  before Spring startup.
- `database-evidence.sql` and `recovery-production.sh` own evidence v2, strict
  legacy/v2 parsing, validated restore-evidence discovery, and the new fixed
  action. `schema-compatibility.sh` owns Git isolation, image inspection, the
  three-way comparator, blocked reasons, and exact cleanup.
- `verify-home-server-database-evidence.sh` and
  `verify-home-server-schema-compatibility.sh` exercise parser and comparator
  drift. The PowerShell Docker rehearsal now compares the restored v2 tuples
  with the packaged image inventory. CI runs the new Bash syntax and comparator
  fixtures. ADR-048 and both deployment READMEs record the operator contract.

### Verification

- API Maven `verify`: **PASS** — 2,357 tests with zero failures, errors, or
  skips. The focused inventory suite passed 10/10. The packaged executable JAR
  emitted only the canonical Flyway 11.7.2 V1-through-V9 inventory before
  Spring startup; executable-JAR injections of a nested migration and a
  repeatable migration both failed closed with exit 70.
- Bash execution fixtures: **PASS** — syntax checks, strict v2 evidence,
  continued identification of legacy v1, seven malformed-evidence rejections,
  exact real Git commit/blob comparison, missing and short object rejection,
  a real promisor clone rejection, linked-worktree common-directory alternate
  rejection, image-resource drift, inventory-shape drift, and Flyway history
  drift all passed.
- Semantic/source guard: **PASS** — ADR-046 remained exact and ADR-047/048
  reached a **111-case negative matrix**, including 40 real shell-source and 8
  local-rehearsal mutations. PowerShell AST parsing, CI YAML parsing, and
  `git diff --check` passed.
- Disposable Docker recovery rehearsal at commit
  `96314051e48ee84b34fab5d02fb480caf4137eb0`: **PASS** — isolated API, web,
  Caddy, and PostgreSQL images built; all services became healthy; a real
  custom archive survived corruption rejection and restored into a fresh
  `network=none` volume; evidence v2, nine Flyway tuples, the exact API-image
  inventory, DEMO `3|2|4` counts, 12 public routes, and exact owned-resource
  cleanup passed.
- The first full run exposed that the local PowerShell harness did not use the
  production `psql -X -q -A -t` evidence mode; commit `c576496` fixed it and
  added a negative mutation. The next run exposed Compose-only
  `JAVA_TOOL_OPTIONS` noise in the harness's extra inventory JVM; commit
  `9631405` removed all three standard JVM option variables for that local
  command and added another negative mutation. The final full run passed.
- The caller-owned `apps/web/next-env.d.ts` remained uncommitted and retained
  SHA-256
  `7ad303e40d4fddf44f156129e397511953a71481c5cfd86b1862649aaaf240cc`.
- Live Ubuntu/HDD validation: **NOT RUN BY DESIGN**. This development computer
  is not the future home server, and no production database, backup disk,
  router, DNS, TLS, port 80/443, or external/offline copy was changed.

### Next work

- Design ADR-049 as a fresh-volume promotion rehearsal with an atomic,
  explicitly reversible generation switch and verified preservation of the
  previous production volume. It must consume only an ADR-048-compatible
  recovery point and must fail closed before changing live ownership.
- Define durable custody and authenticity for the exact release Git objects and
  OCI images. A local image ID proves identity only while that object remains
  present; it is not an archive, signature, or independent copy.
- Select and validate the actual Ubuntu backup HDD before enabling production
  backup actions. Record its device path, UUID, filesystem, capacity, direct
  transport/serial identity, encryption choice, reboot behavior, and schedule
  on that server. Keep `PENDING_OFFSITE_COPY` until an offline or off-site copy
  is independently rehearsed.

## 2026-08-26 — ADR-049 hash-bound read-only generation promotion plan

ADR-049 deliberately stops before a live database-volume transition. It adds a
fixed plan which proves that one ADR-048-compatible recovery point belongs to
the exact release currently running, records the future crash contract, and
keeps every activation and rollback-readiness claim blocked. No candidate,
selector, journal, service, network, or production volume is created or
changed.

### Scope and decisions

- Add fixed action `promotion-plan-latest`. It accepts no caller backup ID,
  generation, volume, SHA, image, path, Compose option, Docker option, or
  approval token. It always reruns the latest ADR-048 comparison first.
- Require exactly one healthy Compose-labeled PostgreSQL, API, web, and
  production Caddy container. Match the PostgreSQL release label and each
  running reference, full image ID, and OCI revision against the immutable
  backup manifest; missing, duplicate, stopped, unhealthy, truncated, stale,
  or changed identities fail closed.
- Emit 41 canonical LF records and their SHA-256. The plan binds complete backup
  and restore-evidence IDs, backup/archive/restore-manifest/database-evidence
  digests, Git/Flyway facts, observation interval, current full container/image
  identities, legacy source volume, deterministic planned candidate name, and
  every unimplemented activation prerequisite. The candidate state remains
  `not-created-by-this-command`.
- Define a pure 16-state controlled-downtime contract. Target health must be
  verified before probation; reviewed pre-downtime aborts return to the source,
  and every post-source-stop state has an explicit rollback path which never
  needs to enter probation. Every skipped, replayed, wrong-branch, terminal,
  unknown, or empty transition is rejected. Ambiguous post-stop states require
  an operator and never auto-select or delete a generation.
- Bind a first and final observation. Before success, reselect and strictly
  revalidate the latest backup/evidence, rehash its content, and reinspect all
  exact container IDs. Drift visible between observations blocks the plan; the
  interval is evidence, cannot close the residual post-read race, and is not a
  substitute for the future shared lock.
- Explicitly prohibit promotion of the ADR-047 restore target because it is a
  disposable, `network=none`, trust-auth rehearsal resource. A future candidate
  must be freshly initialized with the production password-file/SCRAM contract.
- Defer manifest v2 only because the legacy volume remains authoritative in
  this read-only slice. Generation binding, strict legacy-v1/generation-v2
  validators, protected external-volume indirection, a primary-host shared
  lock, fsynced journal, artifact custody, and operator decisions are mandatory
  before the first real switch.
- Keep full API/web/Caddy runtime topology and two-generation capacity as
  explicit hash-bound activation prerequisites. This slice proves exact release
  images, labels, and health but does not infer env/network/mount/port equality.

### Routes and module structure

- No product route, Spring endpoint, OpenAPI operation, database migration,
  fixture, provider, Compose topology, or UI surface changed. No API key,
  provider account, domain, ACME email, router credential, HDD fact, or new
  secret was required.
- `deploy/home-server/generation-promotion.sh` owns the live identity comparator,
  canonical plan renderer/hash, pure transition table, conservative crash
  directives, and blocked operator output. `recovery-production.sh` exposes the
  sole fixed action.
- `scripts/verify-home-server-generation-promotion.sh` exhaustively executes the
  state-event Cartesian product, all interruption directives, canonical plan
  ordering/LF/hash determinism and identity sensitivity, required blockers,
  blocked output, and missing-evidence rejection without Docker, network, or
  host writes.
- `scripts/verify-home-server-recovery.py` adds a closed helper call graph,
  exact transition-arm parser, separate source contract, and 31 generation-plan
  mutations. CI parses both new Bash files and runs the pure
  verifier. ADR-049 plus the root and deployment READMEs record the boundary.

### Verification

- ADR-046 deployment source guard: **PASS**.
- ADR-047/048/049 recovery source guard: **PASS** — **142 negative cases**, with
  40 recovery shell-source, 31 generation-plan, and 8 local-rehearsal
  mutations.
- ADR-049 pure Bash contract: **PASS** — complete normal/abort/rollback state
  tables, pre-probation recovery graph, conservative interruption handling,
  exact 41-row canonical plan and SHA-256, content/identity hash sensitivity,
  live identity mismatch doubles, action gate ordering, explicit blockers, and
  no readiness claim.
- Existing Bash recovery fixtures: **PASS** — exact retention, strict v2 and
  identifiable v1 database evidence, and Git/image/Flyway schema drift
  rejection. New and connected Bash files passed `bash -n`.
- Python bytecode parsing and `git diff --check`: **PASS**. CI YAML received the
  new syntax and execution steps.
- Full Docker recovery rehearsal was **NOT RERUN BY DESIGN**. ADR-048's last
  disposable full rehearsal remains green; this slice changes no image,
  Compose resource, database, route, or Docker mutation path. The new runtime
  action requires the future rootful Ubuntu host, verified backup HDD, and
  production Caddy profile, while its pure policy is locally covered.
- The caller-owned `apps/web/next-env.d.ts` remained uncommitted and retained
  SHA-256
  `7ad303e40d4fddf44f156129e397511953a71481c5cfd86b1862649aaaf240cc`.
- Live Ubuntu/HDD validation: **NOT RUN BY DESIGN**. This development computer
  is not the future home server; no production volume, server, router, DNS,
  TLS, port 80/443, backup disk, or external/offline copy was changed.

### Next work and required operator inputs

- Implement generation-aware backup manifest v2, strict v1/v2 production
  validators, protected external-volume selection, a host-persistent shared
  lock and fsynced transition journal, and exact artifact custody before any
  candidate can be prepared or activated.
- Before that live implementation or Ubuntu integration rehearsal, ask the
  operator for the actual server/DockerRootDir/backup-HDD paths and storage
  facts, capacity for two full generations plus restore headroom, acceptable
  maintenance downtime, probation duration, and write-freeze/RPO policy. Do
  not infer them and do not request secret values in chat.
- Preserve both source and candidate through probation and require separate,
  hash-bound confirmation for activation and later finalization/retirement.
  Keep `PENDING_OFFSITE_COPY` until an independent offline or off-site copy has
  been designed and successfully rehearsed.

## 2026-08-27 — ADR-050 generation-control contract foundation

ADR-050 adds the coordination and evidence format required before a future
database-generation switch, but deliberately performs no switch. Production
deployment and recovery entry points now participate in one fixed primary-host
lock; canonical selector, generation, backup-binding, and journal contracts
can be verified without inventing state on this development computer.

### Scope and decisions

- Reserve the preprovisioned root-only control root
  `/var/lib/wall-street-receipts/generation-control` and permanent
  `operation.lock`. The source validates owner, mode, link count, non-symlink
  paths, and path/FD device-inode identity before and after non-blocking
  `flock`. It never creates, truncates, unlinks, renames, or replaces the lock.
- Use shared locking for deployment contract/publish preflight, `ps`, `logs`,
  recovery production preflight, `preflight`, `status`, and `retention-plan`.
  Use exclusive locking for deployment `build`, `up`, `stop`, `down`, and
  recovery `create`, `rehearse-latest`, `schema-check-latest`, and
  `promotion-plan-latest`. The primary-host lock is acquired before the older
  backup-device lock. Host-only discovery remains usable before provisioning.
- Keep the Compose wrapper alive as the lock-owning parent while its Docker
  Compose child completes. A Linux test disproved reliance on dynamic FD
  inheritance through `exec`, so the wrapper no longer replaces itself at that
  boundary.
- Because production wrappers now run as root for the permanent lock, bind Git
  checks to this exact checkout with per-command `-c safe.directory=...`.
  This supports a non-root-owned deployment checkout without mutating global
  Git configuration or trusting every repository.
- Define exact, ordered, BOM-free LF contracts for active selector v1,
  immutable generation manifest v1, backup generation binding v2, and
  immutable hash-chained journal v1. Every parser rejects unknown, missing,
  duplicate, reordered, oversized, noncanonical, or invalid values and hashes
  exact canonical bytes.
- Validate caller-supplied associative-map and indexed-array names and types
  before creating any Bash nameref, and close the generic parser over exactly
  four schema/validator/renderer tuples.
- Preserve missing historic legacy evidence as literal `unavailable` and mark
  only a verified legacy import as `observed-active-at-import`; never claim the
  active legacy database was sealed offline. Restored candidates require every
  exact backup, restore-evidence, and plan identity plus `sealed-offline`.
- Require distinct journal source/target generations, exact intent/completion
  pairs, contiguous sequence/hash evidence, nonreused operation UUIDs,
  nonregressing observation time, continuous selector evidence, and exact
  one-revision selector changes only for `start-target` and
  `restore-source-selector`. Pending intent produces a conservative manual
  directive and never an automatic generation choice.
- Add low-level durable publication primitives only. They require an exclusive
  held lock, a destination below that lock's control root, same-directory and
  same-filesystem staging, mode-0400 single-link metadata, a staged byte hash,
  `sync FILE`, atomic rename, destination inode proof, parent-directory sync,
  and a final metadata/hash reread. No production entry point calls a writer.
- Correct the existing recovery durability helper from filesystem-wide
  `sync -f FILE` (`syncfs`) to per-path `sync FILE` (`fsync`). No Compose
  external volume, selector, manifest, journal, candidate, activation,
  rollback, automatic recovery, or volume deletion action was added. Backup
  and restore-evidence staging directories now enter final mode 0500 before
  their final pre-rename directory sync, preventing a crash from publishing a
  directory whose sealed permission metadata was not durably ordered.

### Routes and module structure

- No product route, Spring endpoint, OpenAPI operation, database migration,
  fixture, provider, UI surface, image, Compose topology, or named-volume
  declaration changed. No API key, account, domain, ACME email, router fact,
  server fact, HDD fact, or new secret was required.
- `deploy/home-server/generation-state.sh` is source-only and owns the strict
  documents, relationship validators, exact ADR-049 state graph, conservative
  journal classifier, permanent-lock validation, and durable writer
  primitives. Direct execution exits 64 and the module contains no Docker call
  or operator action.
- `scripts/verify-home-server-generation-state.sh` executes canonical and
  malformed document cases, selector/manifest/binding relationships, the full
  16-state by 17-event matrix, journal replay/drift/interruption cases, and on
  Linux the real lock and durable-publication boundary.
- `scripts/verify-home-server-generation-state.py` guards the static call and
  ordering contract and runs a 61-case mutation self-test. CI parses both new
  Bash files and runs both the Python and Linux Bash verifiers. ADR-050 plus the
  root and home-server READMEs record provisioning, threat, and activation
  boundaries.

### Verification

- ADR-046 deployment source guard: **PASS**.
- ADR-047/048/049 recovery source guard: **PASS** — **145 negative cases**,
  including 43 recovery shell-source mutations.
- ADR-050 static source/mutation guard: **PASS** — **61 mutations**.
- ADR-050 Windows Git Bash contract: **PASS** — **18 grouped pure checks**;
  util-linux `flock` and `/proc` runtime checks were explicitly skipped because
  they are Linux-only.
- ADR-050 installed Ubuntu WSL contract: **PASS** — **19 grouped checks**,
  including real shared/exclusive `flock`, path/FD inode validation, secure
  metadata, same-filesystem publication, rename, and file/directory sync.
- Existing retention, database-evidence, schema-compatibility, and ADR-049
  generation-promotion Bash fixtures: **PASS**. All connected Bash files
  passed `bash -n`; Python bytecode parsing, PowerShell parsing, CI/static
  checks, and `git diff --check` passed.
- Full isolated Docker recovery rehearsal: **PASS**. A random owned Compose
  project built the API/web/Caddy images, started healthy PostgreSQL/API/web/
  loopback Caddy services, captured and deliberately corrupted recovery
  artifacts to prove rejection, restored into a fresh owned volume, validated
  Flyway/database evidence, and exercised 12 public routes. Its exact owned
  containers, networks, volume, image tags/IDs, and temporary directory were
  removed after success. The browser suite was not requested; Linux-specific
  host-lock behavior was exercised separately in installed Ubuntu WSL.
- The caller-owned `apps/web/next-env.d.ts` remained uncommitted and retained
  SHA-256
  `7ad303e40d4fddf44f156129e397511953a71481c5cfd86b1862649aaaf240cc`.
- Live Ubuntu/HDD validation: **NOT RUN BY DESIGN**. No home server, production
  volume, backup disk, router, DNS, TLS, port 80/443, or external copy was
  changed.

### Next work and required operator inputs

- The next safe slice is a read-only ADR-051 Ubuntu server-fact collector and
  live-bootstrap gate. Before any selector provisioning, external-volume
  indirection, candidate creation, or transition rehearsal, collect the actual
  Ubuntu release, Docker `DockerRootDir`, control-root and Docker-volume
  filesystem/mount options, exact legacy volume identity, database and free
  capacity, and intended separate-backup-disk identity from the real server.
- The operator must also choose acceptable maintenance downtime, probation
  duration, and write-freeze/RPO policy. These facts change the transition
  design and must not be inferred. No secret value or API key should be sent in
  chat; the collector must redact secrets and emit only bounded infrastructure
  facts.
- Keep `restart: unless-stopped`, automatic boot recovery, exact runtime
  topology, durable Git/OCI artifact custody, two-generation capacity, and
  off-site/offline backup as explicit blockers until each is designed and
  rehearsed on the actual Ubuntu host.

## 2026-08-27 — ADR-051 read-only Ubuntu server-fact handoff

The future home-server laptop is not this development computer and its only
current operator-supplied fact is an expected Ubuntu 24.04 LTS installation.
ADR-051 adds one bounded, read-only collector so the later design can use
observed server/Docker/storage facts without asking for broad or secret-bearing
shell output. It does not provision, deploy, or mutate the target server.

### Scope and decisions

- Add the closed command surface
  `server-facts.sh [--backup-mount ABS_PATH] [--output stdout]`. No argument is
  valid, duplicate or unknown options and relative paths return 64, and stdout
  is the only report destination. A missing backup choice is recorded as
  `not-provided`.
- Pin production command lookup to trusted Ubuntu system paths. Every observed
  child runs through an empty environment with fixed locale/time zone, isolated
  Docker CLI configuration, and only the local
  `unix:///var/run/docker.sock` endpoint. Imported command-name functions are
  removed. No inherited Docker/Compose/WSR/proxy value is inspected or printed.
- Allow only bounded read operations for Ubuntu release, kernel/CPU/RAM,
  Docker/Compose/RootDir/cgroup/ownership boundary, relevant filesystem and
  capacity facts, the fixed legacy Compose PostgreSQL volume, TCP 80/443
  listener classes, systemd state, and the four fixed production container
  restart policies. There is no public-IP lookup, arbitrary Docker enumeration,
  package operation, host write, service change, or Docker mutation.
- Support the standard Ubuntu `/etc/os-release` symlink only through the exact
  regular `/usr/lib/os-release` fallback. Other symlinks are not followed as
  observed facts.
- Require an optional backup path to be the exact active mount before reading
  its filesystem or capacity. A normal directory whose parent is the root or
  Docker filesystem cannot be reported as backup storage.
- Emit exactly 125 fixed-order `key=value` records. Each value is nonempty
  printable ASCII without `=`, at most 256 bytes; every child has a four-second
  timeout and 128 KiB capture ceiling; the final LF-terminated report is at most
  32 KiB. Missing, denied, timed-out, malformed, or sanitized facts remain
  explicit and make `collection_status=partial` rather than becoming zero.
- Reduce listener addresses to `wildcard`, `loopback`, or redacted scope and
  process owners to a small class allowlist. Do not query host/current-user
  identity or print addresses, MACs, PIDs, raw filesystem UUIDs or disk
  serials, environment values, or secret contents. Exact infrastructure paths
  remain visible planning facts and must be reviewed before sharing.
- Keep `restart_policy_gate` and `bootstrap_gate` fixed at `REVIEW_REQUIRED`.
  A complete report is planning evidence, not deployment, promotion, or
  rollback approval.

### Routes and module structure

- No product route, Spring endpoint, OpenAPI operation, database migration,
  fixture, provider, UI surface, image, Compose resource, port publication,
  volume, selector, generation manifest, or journal changed. No API key,
  account, domain, ACME email, router credential, public IP, server login,
  password, or new secret was required.
- `deploy/home-server/server-facts.sh` owns the collector and never sources an
  env/config/secret file. `scripts/verify-home-server-server-facts.py` owns the
  cross-platform static allowlist, exact schema, raw-byte SHA-256 review lock,
  and mutation guard.
  `scripts/verify-home-server-server-facts.sh` owns the pure hostile-command
  fixtures and never selects a live Docker daemon.
- CI parses both Bash files, runs the 45-mutation static guard, and executes the
  18-scenario pure Bash fixture. ADR-051 plus the root and deployment READMEs
  record the handoff and remaining operator decisions.

### Verification

- ADR-046 deployment source guard: **PASS**.
- ADR-047/048/049 recovery source guard: **PASS** — **145 negative cases**.
- ADR-050 generation-control source guard: **PASS** — **61 mutations**.
- ADR-051 static source/mutation guard: **PASS** — exact 125-field schema and
  exact collector-byte review lock with **45 rejected mutations** covering
  network, privilege/package/host/Docker/
  systemd mutations, remote Docker, output-bound weakening, false readiness,
  unsafe direct, wrapped, quoted, or expanded command heads, trusted-path
  weakening, backup parent fallback, and an unreviewed byte change.
- ADR-051 Windows Git Bash pure fixtures: **PASS** — **18 executions** covering
  normal, deterministic replay, all commands unavailable, oversized/hostile
  output, standard Ubuntu OS symlink, partial memory, unavailable listener-owner
  metadata, closed Docker values, exact and nested backup paths, and
  invalid/duplicate CLI cases.
- ADR-051 Ubuntu WSL pure fixtures: **PASS** — the same **18 executions** with
  no live host, Docker daemon, network, secret, or mutable command. ADR-050's
  real Linux `flock`/publication verifier also remained **PASS** at 19 grouped
  checks.
- Bash parsing, CI YAML parsing, Python bytecode execution, and
  `git diff --check`: **PASS**. Full Docker recovery, browser, API, and web
  suites were not rerun because this slice changes no application image,
  Compose topology, database, route, or UI behavior.
- The caller-owned `apps/web/next-env.d.ts` remained uncommitted and retained
  SHA-256
  `7ad303e40d4fddf44f156129e397511953a71481c5cfd86b1862649aaaf240cc`.
- Live Ubuntu/home-server collection: **NOT RUN BY DESIGN**. This workstation
  and its WSL environment are not the target server, and no production disk,
  volume, router, DNS, TLS, port, or external copy was observed or changed.

### Next work and required operator inputs

- Continue local product/release work without inventing server facts. When the
  actual Ubuntu laptop is available, run the exact release checkout's
  `server-facts.sh --output stdout`; add `--backup-mount` only after the separate
  filesystem is genuinely mounted. Share the bounded report, not secrets or raw
  IP/device credentials.
- Before live generation/boot design, ask the operator for acceptable maximum
  downtime, probation duration, writer-freeze behavior and RPO, power-return/
  Docker auto-start policy, UPS fact, source-volume preservation, selected
  backup topology, and offline/off-site copy policy. Do not infer these choices.
- Ubuntu's guided-install LVM may remain incompatible with ADR-047's current
  direct block-device allowlist. Decide the actual storage layout first or add
  a separate reviewed LVM support decision; never weaken the existing recovery
  preflight merely to make an unknown server pass.

## 2026-08-27 — ADR-052 exact SEC filing-history manifest audit API

ADR-052 adds a bounded public read contract over one exact, already-persisted
SEC filing-history collection manifest. It exposes immutable evidence without
selecting a latest manifest, inventing company history, contacting SEC, or
changing the future home-server deployment topology.

### Scope and decisions

- Require one exact 64-character lowercase SHA-256 `manifestId` and an explicit
  `evaluationAsOf` on every evidence GET/HEAD request. The cutoff accepts only
  a strict UTC `Z` timestamp with at most microsecond precision; normalized
  offsets, invalid clock fields, excess precision, missing, blank, duplicate,
  and unknown query
  parameters fail closed as a sanitized 400.
- Resolve only through
  `findByManifestIdAtOrBefore(manifestId, evaluationAsOf)`. There is no
  unrestricted lookup, latest/current selector, CIK search, alternate root or
  manifest, provider request, or fallback. An absent identifier and a manifest
  assembled after the cutoff return the same sanitized 404 contract.
- Reconstruct and verify the complete manifest, root capture, selected segment
  captures, descriptor members, accession groups, occurrences, counts, hashes,
  and ordinals before applying HTTP pagination. Repository or integrity failure
  is a sanitized no-store 500, never a 400, empty page, partial response, or
  fallback result.
- Keep child responses bounded with `page=0`, `size=25`, a maximum size of 100,
  canonical unsigned-decimal pagination, and no caller-selected sort. Fixed
  order is `descriptorOrdinal ASC`, `groupOrdinal ASC`, and
  `occurrenceOrdinal ASC`; a page beyond the end returns an empty item list with
  truthful totals.
- Publish audit schema `1.0.0`, policy
  `SEC_EXACT_MANIFEST_AUDIT_V1`, exact selection and reconciliation evidence,
  explicit nullable fields, and the closed disclosure values
  `ROOT_RELATIVE_SELECTED_REFERENCES_ONLY`, `NOT_MADE`, `NOT_RESOLVED`, and
  `NOT_CLAIMED`. No winner, currentness, completeness, correction/removal,
  amendment linkage, legal authority, ticker, issuer name, invented data mode,
  raw body, headers, contact identity, credential, or operator state is exposed.
- Permit anonymous GET with Spring's implicit HEAD behavior. Success and handled
  audit problems, including MVC 400/404/405/500 and firewall-rejected requests,
  preserve `X-Request-Id` and `Cache-Control: no-store`. The private operator
  bearer-token boundary under `/internal/v1/sec/**` remains separate.
- Accept the existing whole-manifest replay cost for this first audit slice.
  The 100-item limit bounds response size, not database reconstruction work;
  cache, concurrency, and rate controls remain separate future decisions.

### Routes and module structure

- Add exactly four read operations:
  `GET /v1/sec/filing-history/manifests/{manifestId}`,
  `GET /v1/sec/filing-history/manifests/{manifestId}/descriptors`,
  `GET /v1/sec/filing-history/manifests/{manifestId}/accessions`, and
  `GET /v1/sec/filing-history/manifests/{manifestId}/occurrences`. There is no
  collection, latest/current, CIK, or write route.
- `SecFilingHistoryManifestAuditQueryService` owns strict input validation,
  exact point-in-time repository selection, post-lookup identity/cutoff checks,
  and deterministic paging. Dedicated invalid-query and not-found exceptions
  prevent repository corruption from being misclassified as caller input.
- `SecFilingHistoryManifestAuditController` owns the closed query grammar.
  `SecFilingHistoryManifestAuditResponses` owns the exact summary, disclosure,
  descriptor, accession, occurrence, and page shapes. Scoped audit exception
  advice and the method-not-allowed handler own sanitized no-store problems;
  the shared request-firewall handler recognizes the audit namespace.
- `FilingHistoryCollectionConfiguration` wires the query service to the existing
  fully reconstructing repository. No Flyway migration, database table,
  canonical fixture, provider DTO, outbound client, Caddy route, Compose
  resource, port, or web UI changed.
- `contracts/openapi.yaml` owns the four operations and closed schemas at API
  version 0.5.0. CI preserves the original five analyst-call operations as a
  historical projection while guarding the exact current nine-operation
  surface and rejecting SEC list/latest/write expansion.

### Verification

- Full API Maven verify: **PASS — 2,403 tests, 0 failures, 0 errors, 15
  skipped**. The skipped set is the existing Docker-gated Testcontainers set;
  the packaged Spring Boot JAR was built successfully.
- Focused application, HTTP, and security regression set: **PASS — 55/55**.
  This covers strict manifest/time/page grammar, exact inclusive cutoff
  semantics, absent/future-identical 404 behavior, full replay before paging,
  fixed orders and page bounds, partial descriptor selection, exact agreement
  and conflict preservation, explicit nulls, closed field sets, anonymous
  access, GET-only mappings, implicit HEAD 200/400/404 status and headers,
  mutation 405s, firewall rejection, request-ID propagation, no-store, provider
  absence, and sanitized corruption failure.
- Current CI contract validation: **PASS** — workflow YAML parsed as four
  jobs with 75 repository-contract steps and 21 API steps; the exact
  ADR-052 OpenAPI guard resolved all 175 references and retained the closed
  nine-path API surface.
- Local sequential replay of the workflow's embedded historical guards:
  **PASS — 44/44**. The replay used nested exact-byte projections for
  pre-ADR-052, pre-ADR-048, and pre-ADR-045 views, restored the nine-, four-,
  and thirteen-file custody sets in LIFO order, and verified every restored
  hash. This is local workflow-contract evidence, not a claim that a hosted
  GitHub Actions run occurred.
- Final rerun of the exact CI SEC audit test selection: **PASS — 50 tests,
  0 failures, 0 errors, 5 skipped** (45 executed successfully). The five
  skips are the same Docker-unavailable PostgreSQL Testcontainers class.
- `FilingHistoryCollectionManifestPostgreSqlTest`: **5 tests skipped because
  Docker was unavailable**, including the PostgreSQL microsecond cutoff
  regression. The deterministic H2-backed HTTP path passed; this skip is not a
  claim that the PostgreSQL Testcontainers boundary ran.
- Tests ran with SEC provider beans disabled and made no SEC or other external
  network request. No API key, SEC account, paid provider plan, domain, server
  login, home-server fact, `SEC_CONTACT_EMAIL`, operator token, or new secret
  was required.
- The caller-owned `apps/web/next-env.d.ts` remained outside this work and
  retained SHA-256
  `7ad303e40d4fddf44f156129e397511953a71481c5cfd86b1862649aaaf240cc`.

### Next work and required operator inputs

- Build the Korean same-origin evidence UI against these four exact-ID routes.
  Keep Spring private behind the existing Next/Caddy boundary, show the exact
  manifest and evaluation cutoff, preserve disclosure/conflict/partial-selection
  states, and add accessible loading, empty, error, mobile, and pagination
  behavior without introducing a latest or complete-history claim.
- Before a useful public company-filings view, separately decide how a user
  selects a known manifest, how ticker/CIK identity is sourced point in time,
  and how freshness, source attribution, currentness, amendments, corrections,
  removals, caching, concurrency, and anonymous rate limits are represented.
  Those facts cannot be inferred from ADR-052.
- Before live SEC collection, ask for the monitored SEC contact email through
  an untracked server secret and confirm the provider enablement, retention,
  request-budget, and operating policy. Do not request the secret value in chat.
  Any later market-data provider may additionally require the operator's chosen
  account and API key; ADR-052 itself requires neither.
- Before Internet publication, obtain the actual Ubuntu server-fact report and
  operator choices for hardware/capacity, backup disk, downtime/RPO, Docker
  startup, domain/DNS, router exposure, ports 80/443, TLS/ACME contact, and
  same-origin reverse proxy. This development computer is not the deployment
  host, so no live-server readiness or reachability is claimed here.

## 2026-08-31 — ADR-053 exact SEC manifest audit web consumer

Status: implemented and locally validated. Hosted CI, public deployment, and
target-Ubuntu readiness are not claimed by these local results.

ADR-053 consumes the four ADR-052 exact-ID resources through one Korean-default
same-origin Next route. It does not add a backend selector, contact SEC, expose
Spring to the browser, or convert the immutable audit response into a latest,
current, complete, corrected, or legally authoritative company-filings view.

### Scope and decisions

- Add `/research/sec/filing-history`. A parameter-free request is only an exact
  locator and performs no provider read. A complete request carries exact
  `manifestId` and `evaluationAsOf` in the same-origin query and selects one
  `summary|descriptors|accessions|occurrences` view; omission selects summary.
- Keep a closed five-key URL grammar: `manifestId`, `evaluationAsOf`, `view`,
  `page`, and `size`. The ID is lowercase 64-hex; the cutoff is a real-calendar
  UTC `Z` instant with at most six fractional digits and is never defaulted to
  the current clock. Summary forbids paging. Child views use canonical
  unsigned page `0..2147483647`, size `1..100`, and defaults `0`/`25`. Unknown,
  duplicate, blank, padded, signed, leading-zero, and malformed input reaches a
  local invalid state without provider interaction.
- Read only the active resource on each navigation. Canonical links preserve
  exact identity and cutoff, reset a newly selected view to page zero, and do
  not offer sorting. Fixed descriptor, accession, and occurrence ordinal order
  stays visible as response evidence rather than being relabeled SEC
  chronology.
- Keep `SEC_MANIFEST_AUDIT_PROVIDER` server-only and exact. `fixture` and `api`
  are explicit whole-resource modes; a missing selector defaults to fixture
  for isolated local development, while unknown values fail. API mode requires
  private `API_BASE_URL`, performs a no-store redirect-rejecting JSON GET, and
  never falls back to fixture data, another manifest, or an empty page.
- Use one closed adapter for both modes. It preserves exact request identity,
  raw microsecond times, explicit nulls, descriptor selection, all
  selection-coverage states, accession agreement/conflict, every source
  occurrence, count/time invariants, fixed page identity, and disclosure.
  Additive/missing fields, unsafe integers, malformed SEC identities, ordering
  drift, and cross-field inconsistency fail closed.
- Render a bilingual evidence-first page with Korean as the default. Canonical
  tokens are not translated. The page distinguishes the request cutoff,
  captured/evidence-available/assembled times, provider-advertised descriptor
  ranges, conflicts without a winner, nullable `NA`, and manifest source order.
  It makes no data-mode, issuer/ticker, currentness, correction/removal,
  amendment, legal-authority, or completeness inference.
- Keep loading, invalid, not-found, error, known-empty, out-of-range,
  pagination, keyboard-focus, long-identifier, desktop, and mobile states
  explicit. Not found does not distinguish absent from future-invisible
  evidence; other failures publish no partial resource.

### Fixture provenance and parity design

- Commit one synthetic DEMO artifact with an explicit fixture envelope. Every
  fixture presentation labels it synthetic and not observed SEC data. API mode
  does not use `DEMO`, `LIVE`, `REALTIME`, or another invented data mode because
  ADR-052 exposes none.
- Add a Java parity test that assembles the exact root and historical segments
  through the real domain and serializes summary/descriptors/accessions/
  occurrences through `SecFilingHistoryManifestAuditResponses`. The generated
  JSON tree must equal the committed web fixture exactly.
- The fixture provider uses that same artifact and adapter, returns not found
  for another ID or a pre-assembly cutoff, preserves the caller's allowed
  cutoff, and applies truthful bounded child pagination. It is not a source of
  production evidence.

### Runtime and deployment boundary

- Add non-secret `SEC_MANIFEST_AUDIT_PROVIDER`, server-only `API_BASE_URL`, and
  `SITE_ORIGIN`. The first chooses exact fixture/API mode. `API_BASE_URL` is
  required only for API mode and accepts an absolute HTTP(S) base without
  credentials, query, or fragment. `SITE_ORIGIN` accepts one exact absolute
  HTTP(S) origin without credentials, path, query, or fragment and owns
  canonical/social metadata, not data provenance.
- Keep the browser on the Next origin. The home-server web container pins
  `SEC_MANIFEST_AUDIT_PROVIDER=api`, uses `http://api:8080` only on the internal
  application network, derives the public `SITE_ORIGIN` from the reviewed
  domain, and keeps `SEC_PROVIDER_ENABLED=false`. Production never uses the
  fixture as fallback.
- This phase needs no API key, SEC account, paid plan, domain, server login,
  operator token, or `SEC_CONTACT_EMAIL`. A successful API-backed read does
  require an exact manifest already persisted in PostgreSQL and a cutoff at or
  after assembly. The production domain is required only at actual
  publication. A monitored SEC contact email is required only when a later
  separately approved operation starts live collection and must be stored in
  an untracked server secret environment, not chat or Git.

### Verification status

- Web lint: **PASS**.
- Full Vitest suite: **PASS — 48 files / 621 tests**. Coverage includes the
  closed query grammar, exact hrefs, fixture/API selectors, no-fallback
  transport, response adapters and page invariants, localized route states,
  metadata origin validation, and the valid
  `NO_ADVERTISED_DESCRIPTORS` response state.
- Playwright: **PASS — 78/78** across 1440, 1280, and 390 pixel projects. The
  suite covers Korean/English presentation, locator and all four views,
  keyboard focus, long-value containment, pagination, explicit state
  boundaries, and absence of browser requests to the private API origin. These
  browser successes use the synthetic fixture; an API-mode success run is not
  claimed without a genuinely stored manifest and its allowed exact cutoff.
- Focused API-mode browser failure boundary: **PASS — 1/1** with the private
  base URL deliberately pointed at an unavailable loopback port. The Next
  server attempted the private request, rendered the localized fail-closed
  boundary, emitted no browser-to-API request, and showed no DEMO fallback.
  This is transport/error-boundary evidence, not an API-backed success claim.
- Production Next build: **PASS**. The build reports
  `/research/sec/filing-history` as a dynamic route.
- Full API Maven verify: **PASS — 2,404 tests, 0 failures, 0 errors, 15
  skipped; BUILD SUCCESS**. The Java domain/ADR-052 response-mapper fixture
  parity test passed within this run. The 15 skipped tests are the existing
  Docker-gated set; this is not a claim that those Testcontainers checks ran.
- Home-server deployment verifier: **PASS** with the production web provider
  pinned to private API mode and the reviewed `SITE_ORIGIN`/internal API
  boundary.
- These results do not infer that a target Ubuntu server, stored production
  manifest, public DNS/TLS/router path, separate backup device, or off-site
  copy was observed or ready.

### Next work and required operator inputs

- Keep the validated query, adapter, parity, accessibility, responsive,
  no-fallback, build, and deployment-source checks in the release gate; do not
  weaken them to admit a convenient selector or production fixture fallback.
- For an API-backed success demonstration, provide or create through a
  separately authorized workflow a genuinely persisted manifest, then use its
  exact ID and an allowed cutoff. Do not invent one or enable live collection
  merely to make the page nonempty.
- Before Internet publication, obtain the real production domain and existing
  Ubuntu server/DNS/TLS/router/capacity/backup facts. Before live SEC
  collection, separately approve its retention and request policy and place a
  monitored contact email in the server secret environment.
- A later useful company-filings product still needs its own point-in-time
  ticker/CIK identity and selection decision, freshness/source-attribution
  policy, amendment/correction/removal treatment, cache/concurrency/rate
  controls, and public operating review.

## 2026-08-31 — ADR-054 site-wide KST display-time policy

Status: implemented and locally validated. Hosted CI, public deployment, and
target-Ubuntu runtime behavior are not claimed by these local results.

ADR-054 standardizes every human-readable web instant on explicit
`Asia/Seoul` presentation without changing canonical UTC evidence. It is a web
presentation and civil-date-filter slice, not a database, API, fixture,
provider, operating-system, or deployment timezone migration.

### Scope and decisions

- Add one deterministic `formatKstInstant` boundary and one semantic
  `KstTimestamp` component. Visible values use
  `YYYY-MM-DD HH:mm:ss[.source-fraction] KST`, an explicit Gregorian calendar,
  Latin digits, and the IANA `Asia/Seoul` zone. Source fractional precision is
  retained through nine digits. The four-digit-year contract rejects source
  year `0000` and any KST conversion outside years 0001–9999. Invalid non-null
  instants throw rather than rendering `Invalid Date`, raw text, or `NA`.
- Keep exact source RFC 3339 values in `<time datetime>`. PostgreSQL, fixtures,
  Java `Instant`, DTOs, adapters, JSON/OpenAPI, event/processing/capture
  distinctions, sorting, hashes, and point-in-time comparison remain UTC and
  unchanged.
- Route dashboard, maps/treemaps, market publication, institution and analyst
  directories, methodology, call list/detail/context/revision/outcome, S&P 500
  history, and SEC manifest audit instants through the shared KST component.
  Remove every page-local UTC formatter and raw `<time>` renderer.
- Preserve calendar-only source facts without timezone conversion: target,
  observation, vintage, filing, report, and provider-advertised range dates.
  Durations, horizons, processing delays, and media offsets also remain
  unchanged.
- Interpret `/calls` date controls as Korean civil days. The selected start is
  inclusive `00:00 KST`; the through date becomes the following day's
  exclusive `00:00 KST`. Convert those bounds to UTC before the existing API
  read while retaining `YYYY-MM-DD` URL values. For example, 2026-08-11 maps to
  `2026-08-10T15:00:00.000Z` through
  `2026-08-11T15:00:00.000Z` exclusive.
- Preserve ADR-053's exact SEC `evaluationAsOf` UTC `Z` query key and canonical
  URLs. Label the form field as an original API lookup key and state that
  human-readable results are KST. Request evidence, summary provenance, and
  occurrence acceptance instants display KST with raw UTC in `datetime`.
- Add a production-source regression guard that rejects a page-local
  `timeZone: "UTC"`, a legacy `format*Utc` helper, or direct semantic `<time>`
  markup outside the shared component.

### Verification status

- Web lint: **PASS**.
- Full Vitest suite: **PASS — 50 files / 641 tests**. This includes UTC-to-KST
  day rollover, 1–9 digit fraction retention, RFC 3339 offsets, four-digit-year
  boundaries, malformed instant rejection, leap dates, exact Korean-day UTC
  API bounds, semantic `datetime` preservation, Korean/English parity, and the
  production-source KST boundary guard.
- Production Next build: **PASS**. TypeScript and all 12 route builds completed;
  every route remains dynamically server-rendered on demand.
- Full Playwright suite: **PASS — 78/78** across 1440, 1280, and 390 pixel
  projects in 3.3 minutes. The run covers bilingual KST evidence, raw
  `datetime`, exact query preservation, keyboard focus, mobile/desktop
  containment, absence of browser-to-private-API requests, and runtime-error
  checks.
- Focused SEC API-mode failure boundary: **PASS — 1/1** at 1440 pixels with an
  intentionally unavailable private API base. The route kept the exact UTC
  lookup key contract, rendered the localized fail-closed boundary, emitted no
  browser-to-private-API request, and exposed no synthetic DEMO fallback.
- ADR-054 workflow custody replay: **PASS — 54/54 exact file surfaces** in an
  isolated temporary clone. The outer projection replaced 49 modified files
  and removed five added files at exact base `8fc3732`, ran ahead of the locked
  ADR-053/052/048/045 historical chain, and restored every current byte. The
  workflow parsed successfully, both embedded ADR-054 Python programs compiled,
  ADR-053's locked hashes stayed unchanged, and `apps/web/next-env.d.ts` stayed
  outside custody.
- No Java, Flyway, OpenAPI, database, provider fixture, Compose runtime, or
  backend source changed, so this slice did not rerun or make a new claim about
  Docker-gated API/Testcontainers execution.
- The caller-owned `apps/web/next-env.d.ts` remained excluded. Next build's
  generated production import was restored to the pre-existing development
  import, retaining SHA-256
  `7ad303e40d4fddf44f156129e397511953a71481c5cfd86b1862649aaaf240cc`.

### External inputs and next work

- This slice required no API key, provider account, paid plan, domain, home-
  server access, deployment machine, operator token, `SEC_CONTACT_EMAIL`, or
  new secret.
- An API-backed SEC success still needs an actually persisted manifest ID and
  allowed UTC cutoff. Internet publication still needs the actual domain and
  target Ubuntu/DNS/TLS/router/capacity/backup facts. Live SEC collection still
  needs a separately approved request policy and monitored contact email in an
  untracked server environment.
- New web instants must use `KstTimestamp`. New civil-date filters must name
  their calendar zone and convert to canonical API instants at the server
  boundary rather than depending on host or browser timezone.

## 2026-08-31 — ADR-055 disposable offline SEC manifest-audit API-mode full-stack acceptance

Status: implemented and locally validated. The Docker/Chromium full-stack gate
passed on this development computer. The repository workflow's ADR-055 custody,
nested historical projection, and current-tree guard also passed isolated local
validation; a hosted GitHub Actions run is not claimed by these results.

ADR-055 closes the local success-path gap between the exact ADR-052 manifest
audit API and ADR-053 same-origin web consumer. It extends the existing ADR-045
disposable production-stack command rather than adding another orchestrator,
and it does not add a production seed, raw-SQL fixture, mutation route,
scheduler, startup importer, or live provider activation.

### Scope and decisions

- Add `SecManifestAuditAcceptanceSeedHarness` as an exact opt-in JUnit class
  whose name is outside default Surefire discovery. It runs only when Maven
  names that class and supplies
  `-Dwsr.sec-manifest-acceptance-seed=true`; ordinary `test`, `verify`, package,
  and application startup do not execute it.
- Reuse one shared Java synthetic fixture for the committed ADR-053 JSON parity
  tree and the disposable database seed. Append and reload the root and two
  historical captures through production repositories, then persist one
  manifest through `PersistFilingHistoryCollectionManifestService` and verify
  it through the production audit query service. No SQL shortcut or runtime
  backdoor is introduced.
- Refuse the seed unless both supplied and effective JDBC state identify exact
  loopback PostgreSQL, database/user `wsr_full_stack_acceptance`, matching
  datasource/Flyway settings, and the per-run 32-hex password. Also require
  empty root/segment/manifest repositories, `SEC_PROVIDER_ENABLED=false`,
  `OPERATOR_API_ENABLED=false`, an empty SEC contact email, and closed base URL
  `http://127.0.0.1:1` before any write.
- Pin `SEC_MANIFEST_AUDIT_PROVIDER=api` in the secret-free Next build,
  production runtime, and browser child. A separate server-only acceptance
  setting names only the exact synthetic manifest, so that API response retains
  the visible `DEMO` badge and synthetic-not-actual-SEC disclosure. It does not
  add `dataMode` to ADR-052 or infer a mode for any other API manifest.
- Preserve ADR-054: the filtered `2026-08-11` Korean civil day is the exact UTC
  interval from inclusive `2026-08-10T15:00:00.000Z` to exclusive
  `2026-08-11T15:00:00.000Z`. SEC result instants remain visibly KST while their
  semantic `datetime` values and exact `evaluationAsOf` lookup identity remain
  canonical UTC.
- Extend the production route smoke from 12 to 13 routes by including
  `/research/sec/filing-history`. Extend the focused browser matrix to five
  retry-free Chromium checks: call list, revisions, outcomes, and two SEC audit
  tests covering all four views, malformed input, exact pre-assembly absence,
  bilingual evidence, and zero browser requests to private Spring.
- Require 18 exact full Tomcat access-log lines: the prior 13 call reads, four
  HTTP-200 manifest resources, and one HTTP-404 summary at the cutoff one
  microsecond before assembly. Require the complete database count/identity
  tuple rather than a cardinality subset:

```text
3|2|4|3|1|2|2|2|4|1|2|4|6|0|0|0|0|cda6762d385d4e889294d0fec1f7a2a7b20c5157cf67c832b7d7f4857550a1cd|eadb0c3bf6efb9b3323be1342d0b17e63631b706f088b23fa78e784e1b547acd|c9bfc935b27e059397531a4dda1a1a0222e98528c33e85b886c91ca6b74f2fa8|2026-08-25T03:30:00.123456Z
```

The tuple proves call/revision/outcome `3|2|4`; decoded body, root, recent row,
descriptor, historical capture, historical row, manifest, selected descriptor,
accession group, and occurrence counts `3|1|2|2|2|4|1|2|4|6`; zero operator
attempt/action/dispatch/outcome rows; and the exact manifest, selection, root,
and assembly identities.

### Verification status

- The local Docker daemon was initially stopped and was started on this
  development computer before the disposable run. No remote Docker endpoint
  was used.
- The first seed attempt failed safely before persistence because
  `WebEnvironment.NONE` did not create the `HttpSecurity` dependency required
  by the production application context. The acceptance test was changed to
  `WebEnvironment.MOCK`; the strict datasource/provider/operator guards stayed
  intact, and the complete clean rerun passed.
- `pwsh -NoProfile -File ./scripts/verify-local-full-stack.ps1`: **PASS**.
  Production Next built successfully inside the secret-free harness mirror; 13
  production routes rendered; focused Chromium passed **5/5**; all **18 exact
  Spring reads** were observed; the complete call+SEC PostgreSQL tuple matched;
  live SEC collection and the operator boundary remained disabled; and no
  external provider was contacted.
- Harness cleanup: **PASS**. The exact API/web processes, Compose project and
  volume, source mirror, temporary build output, reports, logs, and shared lock
  owned by the run were removed. The root `.env`, default database/volume,
  normal web build, `apps/web/next-env.d.ts`, and `apps/web/tsconfig.json`
  remained outside the cleanup boundary.
- `.\mvnw.cmd -B -ntp verify` from `apps/api`: **PASS — 2,404 tests; BUILD
  SUCCESS**.
  The shared synthetic fixture/domain/ADR-052 response-mapper parity test also
  passed.
- Full web Vitest suite: **PASS — 50 files / 643 tests**.
- Web lint: **PASS**.
- Production Next build: **PASS** within the disposable full-stack harness.
- Standalone fixture-mode SEC Chromium acceptance: **PASS — 2/2**. This
  supplements the harness's successful API-mode SEC branch without changing
  the recorded full-stack result.
- PowerShell parser validation for the extended harness: **PASS**.
- The first post-commit nested workflow projection failed safely inside its
  disposable clone before ADR-054 mutated evidence: ADR-055 had projected the
  `a121eb1` files but left Git `HEAD` at the new commit, so ADR-054 correctly
  rejected the enlarged `8fc3732..HEAD` inventory. The outer guard was changed
  to custody and temporarily detach `HEAD` at `a121eb1`, then restore the exact
  original commit/ref state and current bytes after all historical guards.
- ADR-055 repository-workflow custody and historical projection: **PASS** in an
  isolated local clone. Exact pre-ADR-055 bytes were projected before ADR-054's
  own historical guard, the current 18-file delta was restored byte-for-byte,
  and the focused current-tree markers were then checked. This is workflow
  implementation validation, not a hosted GitHub Actions run and not a second
  Docker/Chromium execution.
- `git diff --check`: **PASS**.

### External inputs and next work

- This acceptance required no API key, SEC account, paid plan, domain,
  home-server access, user/operator token, OAuth credential,
  `SEC_CONTACT_EMAIL`, or other external credential. All SEC evidence was
  synthetic and isolated; live collection remained off.
- Repository CI now parses and guards the ADR-055 source graph, safety markers,
  exact identities, and nested historical projection. Its implementation and
  isolated projection passed locally; the hosted workflow still has to run on
  the eventual remote branch. CI does not execute this Docker/Chromium harness.
- Git/release handoff and the later Ubuntu home-server rehearsal remain separate
  work. Live SEC collection still requires separate approval and a monitored
  contact email placed in an untracked server secret environment rather than
  chat or Git.

## 2026-08-31 — ADR-056 disposable offline Git Flow release-source handoff rehearsal

Status: implemented and observed in a disposable exact-candidate clone. The
successful run remained local and ended with `NOT_RELEASED` and
`REMOTE_NOT_CONTACTED`; it did not create a real release or contact the
configured origin or any network endpoint.

### Objective

- Prove on this development computer that the exact committed product tree can
  pass through the repository's required feature/release Git Flow shape, an
  annotated rehearsal tag, a complete tag-only Git bundle, and an offline fresh
  import without using this Codex conversation as deployment state.
- Keep the exercise disposable and local. Its terminal status remains
  `NOT_RELEASED` and `REMOTE_NOT_CONTACTED`; it must not fetch from or push to
  the configured origin, open a pull request, create a real release branch/tag,
  contact GitHub, or deploy Ubuntu. File-protocol pushes exist only inside the
  owned simulated remote.

### Scope and decisions

- Add `scripts/verify-local-release-handoff.ps1` as the single no-input local
  command. It records source commit/ref/status and user-owned
  `apps/web/next-env.d.ts` custody, including source config/index/refs digests,
  performs all graph and artifact work under a harness-owned temporary root,
  and revalidates unchanged source state after cleanup. The source must be a
  complete symbolic `feature/*` checkout and clean except for that one exact
  unstaged Next declaration.
- Strip Git/hosting/SSH/proxy overrides, disable credentials and hooks, inject
  `gc.auto=0`, `maintenance.auto=0`, and `core.fsmonitor=false`, allow only the
  local file protocol, and set `GIT_NO_LAZY_FETCH=1` and
  `GIT_OPTIONAL_LOCKS=0`. Give every Git child 120 seconds; after timeout kill
  its process tree and require exit within five more seconds. Read both streams
  to completion, then reject either captured string above 1,048,576 characters;
  this is an accepted-output limit, not a streaming memory cap. Reject shallow,
  partial, promisor,
  partial-clone-filter, alternate, HTTP-alternate, graft, replacement, or
  strict-`fsck`-incomplete source/imported repositories, plus local executable
  `core.fsmonitor`, `uploadpack.packobjectshook`, or
  `filter.*.(clean|smudge|process)` configuration; resolve repository
  metadata through `git rev-parse --git-path` so linked worktrees do not bypass
  the gate.
- Require local `main` to equal cached `origin/main`, cached `origin/develop` to
  be an ancestor of local `develop`, and local `develop` to be an ancestor of a
  feature candidate at least one commit ahead. Seed only those cached origin
  refs in the temporary bare remote, fast-forward its `develop` backlog, merge
  the exact feature through `--no-ff`, create an empty rehearsal-only release
  stabilization commit, merge that release through `--no-ff` into both
  temporary `main` and `develop`, and create one annotated
  `v0.0.0-rehearsal.<24-hex>` tag. Every generated commit tree must equal the
  exact source feature `HEAD` tree; temporary commit/tag identity never becomes
  a real release identity.
- Export only the annotated temporary tag through a complete bundle with no
  prerequisites. The bundle is named from `mainReleaseCommit`; a canonical
  printable-ASCII, one-line, final-LF `manifest.json` has exactly 22 ordered
  fields: `schemaVersion`, `featureAheadCount`, `bundleBytes`, and
  `bundlePrerequisiteCount` are JSON Number/int64 values, while the other 18
  are JSON Strings. Its adjacent GNU-style SHA-256 receipt names and hashes only
  that bundle, not `manifest.json` or the complete three-file set. The manifest
  binds source/cached/local identities, feature distance, four generated commits,
  tag/ref/object, bundle bytes/digest, `NOT_RELEASED`, and
  `REMOTE_NOT_CONTACTED` during the same in-memory rehearsal.
- Reject a byte-flipped bundle with unchanged metadata and a 64-byte-truncated
  bundle even after its manifest and receipt are recomputed. The bundle receipt
  detects bundle-byte corruption only. A later retained-artifact import does not
  recompute every manifest-only Git Flow identity or `featureAheadCount`, so a
  syntactically valid change to those fields can pass without an independently
  reviewed manifest/commit digest or signature. Require each negative to match
  its exact digest or structural-import failure pattern.
- Import through a fresh local-only Git repository, prove annotated-tag type,
  exact release/tree identity, clean checkout, complete non-shallow/non-
  promisor/non-alternate/non-replacement object graph with strict no-dangling
  `fsck`, detached `HEAD`, zero remotes, and the exact 13 required source/
  deployment paths. Artifact acceptance itself also unbundles into a new bare
  verifier, restores only the recorded tag, peels it to the exact commit/tree,
  and runs strict `fsck` before the server-checkout exercise.
- Preserve the user-owned source `apps/web/next-env.d.ts` bytes while excluding
  them from the release candidate. The imported tree contains only the version
  committed in the exact source `HEAD`.
- Own cleanup through an exact system-temp parent, fixed 24-hex directory name,
  and separate flushed 48-hex owner marker. Refuse a reparse point, changed or
  absent marker, wrong parent/name, or malformed token before recursive removal.
- Keep Docker images, image signing/custody, reproducible binary claims,
  dependency caches, database data, secrets, server provisioning, and actual
  release publication outside ADR-056.

### Verification status

- The first run from the committed feature checkout failed safely while
  deleting a Codex-owned auxiliary `refs/codex/turn-diffs/...` ref copied by
  `clone --mirror`; its length exceeded the Windows lock-path boundary inside
  the disposable bare repository. Cleanup and complete source custody still
  passed. The seed was changed to initialize an empty bare repository and
  require a zero-ref inventory before fetching only cached `origin/main` and
  `origin/develop` through two non-forcing explicit refspecs with tags,
  `FETCH_HEAD`, and submodules disabled. A focused local seed/fetch/strict-
  `fsck` probe then passed with exactly those two refs. The harness also
  rechecks `HEAD` as `develop`, requires zero persisted remotes, and runs its
  complete-repository/strict-`fsck` gate before cloning the seed.
- `pwsh -NoProfile -File ./scripts/verify-local-release-handoff.ps1` from the
  actual committed feature checkout at `54c5884e59d3ab8139ce5865f24d57d19454a5b7`:
  **PASS**. The exact source tree was
  `903a11683d6a85422b2f5c5bdb6a068e473023c4`, the feature was seven commits
  ahead of local `develop`, the run ended with `NOT_RELEASED` and
  `REMOTE_NOT_CONTACTED`, and the only remaining worktree change was the
  pre-existing user-owned Next declaration with SHA-256
  `7ad303e40d4fddf44f156129e397511953a71481c5cfd86b1862649aaaf240cc`.
- `pwsh -NoProfile -File ./scripts/verify-local-release-handoff.ps1`:
  **PASS** in a disposable clone containing the exact six-file ADR-056
  candidate and the excluded user-owned Next declaration. The run completed
  feature/release Git Flow, tag-only bundle creation, artifact rejection, and
  detached offline import, then removed both its owned harness root and the
  enclosing test clone.
- PowerShell parser, canonical JSON/receipt validation, two corrupt-bundle
  negative cases, offline tag-only import, full-object `git fsck`, source-
  custody comparison, and owned cleanup:
  **PASS**. The byte-flipped bundle failed only at the expected SHA-256 check;
  the truncated-and-rehashed bundle failed only at structural verification or
  full import. The source `HEAD`, symbolic ref, status, config, index, refs, and
  user-owned `apps/web/next-env.d.ts` bytes were unchanged.
- Documentation marker parity and `git diff --check`:
  **PASS**. The focused CI guard, PowerShell parse, embedded-Python compile,
  workflow YAML parse, and documentation checks also passed locally.
- Hosted GitHub Actions, remote feature branch, pull request, release branch,
  `main`/`develop` merge, annotated release tag, and Ubuntu import:
  **NOT RUN / NOT CREATED**.

### External inputs and next work

- The disposable rehearsal needs no API key, domain, home-server access, Docker
  daemon, GitHub login, network authorization, repository-visibility decision,
  release version, token, private key, or new secret.
- Actual remote work later requires explicit network authorization, a fresh
  fetch and divergence review, a public/private repository decision, a version
  decision (`v0.1.0-rc.1` recommended), and GitHub authentication configured
  locally. Tokens and SSH private keys must never enter chat or Git.
- ADR-056 performs no actual feature push/PR, release branch/tag, or merges into
  `main` and `develop`. Persistent offline artifact export, source
  authentication/signing, Docker image custody, and the real Ubuntu server-fact
  and deployment rehearsal remain later steps.

## ADR-057 — Size-bounded CI and isolated historical contract execution

### Scope and implementation

- Follow-up to the explicitly approved feature push and draft
  [PR #7](https://github.com/yj901010/wall-street-receipts/pull/7) into `develop`.
  No merge, release, provider activation, or home-server deployment is included.
- Fix the Actions entry point that previously measured 2,438,119 bytes and had
  36 `run` scalars over 21,000 characters (largest: 96,535). Extract all 84
  repository-contract bodies to `scripts/ci/legacy/` with original 86-step
  metadata in `legacy_steps.json`.
- Add `validate_limits.py`, `run_contracts.py`, focused unittest modules,
  CI-only pinned requirements, operational README, and ADR-057. No product
  routes or runtime modules change. KST display and UTC storage remain intact.
- Preserve current web/API/call-audit integration job semantics. Run historical
  guards in an independent complete local-only checkout pinned to `3792100`;
  require current product path/mode/type/blob parity outside a closed CI/doc
  allowlist. Preserve all seven always-restores and byte-level source custody.
- Keep the pre-existing unstaged `apps/web/next-env.d.ts` out of commits and out
  of the historical clone. Never read or publish `.env` contents.

### Verification status

- Mechanical extraction: all 84 bodies and all 86 original step metadata
  entries match the digest-pinned workflow. Bash syntax (80 scripts) and
  PowerShell AST parsing (4 scripts): PASS.
- Workflow size guard: PASS, 30,427 bytes; largest current `run`: 1,650
  characters. Original app-job semantic parity and current product-tree parity:
  PASS.
- Combined platform-limit and contract-runner unit tests: 71 total, 70 PASS,
  one Windows symlink-capability skip. Coverage includes extraction/body/step
  tampering, product additions/deletions/modes/types, failure at every historical
  step, all required restores, custody, read-only cleanup, timeout and
  cancellation.
- Real local isolated preparation: PASS. A source-change negative correctly
  rejected final custody. Its cleanup exposed read-only Git objects on Windows;
  bounded owned-directory cleanup was fixed and the repeated cleanup passed.
- Separate local historical projection integration: all 14 projection/restore
  bodies (27-33, then 63 and 75-80) PASS in 14.6 seconds. Detached baseline
  HEAD, clean checkout, all seven restored markers, source Next declaration,
  and removal of the owned temporary checkout were verified. This is a focused
  restoration probe, not a full 84-step run.
- Local Docker Desktop Linux daemon is not running. Full hosted Ubuntu
  execution, rather than local static checks alone, is required before claiming
  end-to-end CI success.
- CI refactor committed locally as `8323aad`. Upload of this new public
  payload was denied by the execution approval boundary pending explicit user
  permission. No workaround or alternate upload was attempted. Hosted Actions
  for this refactor: NOT RUN; PR #7 remains unchanged and draft. The prior
  public upload approval covered the earlier accumulated commits, not this
  newly prepared CI payload according to that boundary.

### External inputs and next phase

- No new API key, server fact, domain, or paid service is needed for this CI
  refactor. Existing GitHub authentication is used only for the approved feature
  branch and PR.
- Keep PR #7 draft until verification is reviewed; do not merge automatically.
  Actual home-server facts and deployment remain deferred until that machine
  is available.
- Before any further product change, migrate affected legacy guards to current
  source contracts and mutation tests; do not extend the CI-only allowlist to
  admit unverified feature changes.
