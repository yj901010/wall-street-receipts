# P2 Acceptance Checks — Core UI

Current status: the methodology-registry and multiple market-map shell vertical
slices are complete. These checks close only the delivered P2 slices;
dashboard, leaderboard, screener, full-universe map, and derived market-mode
work stays open.

The methodology registry is a read-only, fixture-backed explanation surface. It
publishes the immutable definition identities already present in
`fixtures/v1/call-outcomes.json`; it does not calculate, activate, rank, or
recommend anything.

## Methodology registry slice boundary

- The public web route is `GET /methodology`.
- The canonical source is the `methodologies` array in
  `fixtures/v1/call-outcomes.json`, validated by
  `schemas/scoring-methodology.schema.json`.
- The web provider reads the versioned root fixture through a typed adapter. It
  does not duplicate methodology records in application source.
- This slice adds no API endpoint, database migration, persistence write,
  provider network call, scoring calculator, scheduler, or ranking aggregate.
- `GET /v1/calls/{id}/outcomes` and every existing call response remain
  unchanged.

## Methodology contract and fixture gate

| ID | Check | Expected result |
| --- | --- | --- |
| P2-M01 | Closed canonical record | `scoring-methodology.schema.json` is Draft 2020-12, uses `additionalProperties: false`, requires every documented field, and accepts only `methodologyId`, `methodologyVersion`, `schemaVersion`, `definitionHash`, `status`, `effectiveAt`, `dataMode`, `capturedAt`, and `provenanceId`. |
| P2-M02 | Exact registry evidence | The fixture contains exactly `standard-call-outcome@1.0.0` followed by `standard-call-outcome@2.0.0`; no third, missing, or reordered definition is silently accepted. |
| P2-M03 | Immutable identity | `(methodologyId, methodologyVersion)` is unique. Both definitions have distinct immutable hashes; a version is never inferred from `schemaVersion`, `fixtureVersion`, or an outcome sequence. |
| P2-M04 | Definition hash | Every `definitionHash` is exactly 64 lowercase hexadecimal characters. The registry renders it as evidence and never labels it as a performance or quality score. |
| P2-M05 | Model-only status | Both records are exactly `MODEL_ONLY`. The UI must not translate that state to active, production, approved, current, scored, or recommended. |
| P2-M06 | Time and provenance bounds | Every record uses canonical UTC with at most microsecond precision and preserves `effectiveAt <= capturedAt <= fixture.provenance.capturedAt <= fixture.generatedAt`. Record `dataMode` equals the DEMO envelope, `provenanceId` equals the envelope provenance ID, and the provenance is synthetic `INTERNAL_DEMO` local-specification evidence. |
| P2-M07 | Deterministic presentation order | Provider and route output use the fixture's canonical identity order, `1.0.0` then `2.0.0`. Client locale, object-key enumeration, or status text must not change the order. |
| P2-M08 | No calculation claim | The fixture disclaimer remains explicit that no outcome metric was calculated or invented. Every DEMO outcome metric/result is JSON null, no outcome is `CALCULATED`, and no registry copy claims return, alpha, hit rate, accuracy, sample confidence, or ranking. |
| P2-M09 | Outcome linkage evidence | Every existing outcome references one of the two exact methodology identities and carries the matching `methodologyDefinitionHash`; presenting the registry does not project or mutate outcome state. |

## Methodology web behavior gate

| ID | Check | Expected result |
| --- | --- | --- |
| P2-W01 | Route and navigation | `/methodology` is server rendered and the primary Methodology navigation target resolves to that route rather than an in-page placeholder. |
| P2-W02 | Exact evidence | Both versions render their methodology ID, version, full definition hash, `MODEL_ONLY` status, effective time, captured time, DEMO mode, and provenance ID. Missing values are never invented. |
| P2-W03 | Meaningful explanation | The page explains schema version, methodology version, definition hash, effective time, and capture time without claiming the underlying formula is present in the fixture. |
| P2-W04 | Honest state | A prominent DEMO/model-only notice and the fixture disclaimer make clear that scoring is deferred to P3. There is no accuracy, return, leaderboard, active-version, or investment-performance claim. |
| P2-W05 | Source traceability | The page exposes the fixture source and capture/as-of evidence. It does not link to a fabricated external methodology document or imply live-provider data. |
| P2-W06 | Loading, error, and empty behavior | Route boundaries provide an explicit loading state, a recoverable error state, and an honest empty state if the registry has no records. Empty does not become a placeholder version. |
| P2-W07 | Accessibility and responsive layout | Evidence uses semantic headings/table or description lists, copyable hashes remain keyboard reachable, focus is visible, and any dense table scrolls locally without page-level overflow at 1440, 1280, and 390 pixels. |
| P2-W08 | Regression boundary | Existing `/`, `/calls`, and `/calls/{id}` behavior and exact API response contracts remain unchanged. |

## Methodology required tests

- Repository-contract CI validates the exact two-record projection, canonical
  order, unique identity and hash, schema/format validity, time bounds, DEMO
  provenance, model-only state, outcome hash linkage, and explicit absence of
  calculated metrics.
- Provider tests prove the exact two-version mapping and order, reject malformed
  or duplicate fixture records, and preserve all evidence fields without
  fallback values.
- Route tests cover exact evidence, explanatory/model-only copy, navigation,
  empty/error boundaries, and absence of accuracy, return, rank, or active
  claims.
- Responsive browser checks cover 1440, 1280, and 390 pixels, keyboard focus,
  local overflow containment, and zero console errors or warnings.

## Methodology deferred work

P3 owns deterministic formulas, trading-calendar horizons, corporate-action
adjustment, return/alpha/target/MFE/MAE calculations, golden tests, status
activation policy, sample confidence, and leaderboard aggregates. A future API
registry, methodology body/document store, lifecycle mutation, or production
provider requires a separate additive contract and is not implied by this P2
surface.

## Multiple market-map shell slice boundary

- The public web routes are `GET /maps/sp500` and `GET /maps/nasdaq100`.
- The canonical sources are `fixtures/v1/market-map.json` and the independently
  appended `fixtures/v1/market-map-nasdaq100.json`, both validated by the closed
  `schemas/market-map.schema.json` contract.
- `market-map.json` retains its exact three numeric cell payloads. Its additive
  coverage metadata and strengthened disclaimer identify those records as an
  incomplete synthetic DEMO sample, never a full index composition, official
  index weight set, or value derived from canonical analyst calls.
- The Nasdaq 100 document is an explicit known-empty canonical fixture with zero
  cells. No index membership, weight, metric, call count, sector, or asset
  identity is copied, inferred, or generated to make the map look populated.
- This fixture-backed P2 shell adds no API endpoint, OpenAPI path, database
  migration, persistence write, provider network call, scoring calculation, or
  materialized market-map backend.

## Market-map contract and fixture gate

| ID | Check | Expected result |
| --- | --- | --- |
| P2-MM01 | Closed canonical document | `market-map.schema.json` is Draft 2020-12, uses `additionalProperties: false` for the document and every nested object, and requires the exact envelope, metric, coverage, cell, and disclaimer fields. Both fixtures validate with canonical UTC `Z` instants at no more than microsecond precision. |
| P2-MM02 | Exact universe catalog | The fixture registry contains exactly `market-map.json` for `sp500` followed by `market-map-nasdaq100.json` for `nasdaq100`. `(universe, mode, asOf)` and provenance IDs are unique; exact replay is deterministic and a duplicate natural identity is rejected rather than overwritten. |
| P2-MM03 | Upgrade-safe S&P evidence | The existing S&P cell order and numeric payload remain exactly NVDA `(8.1, 0.82, 18)`, MSFT `(7.0, 0.71, 14)`, and AAPL `(6.5, null, 0)` at the original cell timestamp. Contract, tracked provenance, recapture time, and disclaimer metadata may advance without changing those synthetic sample values. AAPL null remains null and renders as `NA`, never zero or bearish. |
| P2-MM04 | Explicit coverage | Both documents carry exact closed coverage `{kind: SAMPLE, completeUniverse: false, cellCount: cells.length, weightBasis: SYNTHETIC_RELATIVE}`. The UI must state that fixture cell count is not universe completeness and that geometry uses synthetic relative fixture weights, not official index weights. |
| P2-MM05 | Honest Nasdaq empty state | The Nasdaq fixture has `cells: []`, coverage `cellCount: 0`, and an exact disclaimer that no full-index composition, official weight, analyst-consensus metric, or call count was observed, derived, or inferred. Missing fixture evidence never falls back to S&P or hard-coded cells. |
| P2-MM06 | Exact metric and null policy | Both maps use exactly `analystConsensus`, unit `score`, range `[-1, 1]`, and `missingDisplay: NA`. A populated cell has a strictly positive synthetic relative weight, a metric that is null or within the declared range, and a non-negative integer call count. Unknown numeric evidence is never substituted with zero. |
| P2-MM07 | Master-data identity | Every populated cell resolves to one exact `master-data.json` asset ID/ticker pair. Asset IDs and tickers are unique within a universe; an asset may appear in another explicitly sourced universe, but the empty Nasdaq shell does not assert membership or add a fabricated NDX master identity. |
| P2-MM08 | Point-in-time provenance | Every map preserves `cell.timestamp <= asOf <= provenance.capturedAt <= generatedAt`. Document and cells use DEMO mode; every cell provenance ID equals the envelope provenance ID; provenance is unique, synthetic `INTERNAL_DEMO` local-specification evidence. |
| P2-MM09 | Manifest parity | Both files are declared exactly once in `fixtures/v1/manifest.json`; the S&P entry says limited DEMO SAMPLE and the Nasdaq entry says known-empty DEMO SAMPLE. The new file is appended without renaming the existing fixture. |
| P2-MM10 | No calculated claim | Neither fixture, adapter, route, legend, tooltip, nor empty state describes the synthetic analyst-consensus values as observed calls, production consensus, accuracy, return, alpha, target gap, target revision, confidence, recommendation, or ranking. |

## Market-map web behavior gate

| ID | Check | Expected result |
| --- | --- | --- |
| P2-MW01 | Routes and universe navigation | `/maps/sp500` and `/maps/nasdaq100` are server rendered from the two canonical fixture documents. Primary navigation and in-page universe controls reach both routes with an unambiguous current-universe state. |
| P2-MW02 | Evidence-first header | Each route renders DEMO, universe, `Analyst consensus`, unit `score`, canonical as-of time, source/provenance, fixture cell count, incomplete SAMPLE coverage, and synthetic-relative weight basis before any map cells. |
| P2-MW03 | S&P sample rendering | The S&P route renders exactly the three canonical cells in fixture order, labels weights as fixture-relative rather than official index weights, shows non-null metric values without recalculation, and displays AAPL metric as `NA`. It never claims 500 constituents are present. |
| P2-MW04 | Nasdaq known-empty rendering | The Nasdaq route renders the canonical known-empty disclaimer and zero placeholder cells. It does not reuse S&P records, show a misleading treemap rectangle, or imply that no real Nasdaq 100 constituents exist. |
| P2-MW05 | Loading, error, and empty behavior | Route boundaries provide explicit loading and recoverable error states. Known-empty is data, not a load failure; malformed or unsupported universe input is rejected and never replaced with another map. |
| P2-MW06 | Accessibility and responsive layout | Universe controls, any cell interaction, and retry/navigation targets are keyboard reachable with visible focus. At 1440, 1280, and 390 pixels, the page has no horizontal overflow; dense evidence or cells remain locally contained; browser console warnings, errors, and page errors are zero. |
| P2-MW07 | Honest interaction boundary | This P2 shell does not claim a stock-detail destination. A cell may be non-interactive or use an explicitly labelled call-ledger link to the existing `/calls?ticker=...` surface; it must not fabricate `/stocks/{ticker}` or label a call filter as stock detail. |
| P2-MW08 | Existing-route regression | Existing `/`, `/calls`, `/calls/{id}`, and `/methodology` behavior and every API response contract remain unchanged. No P2 map resource is added to the API artifact. |

## Market-map required tests

- Repository-contract CI validates the exact two-file catalog, closed schema,
  manifest parity, unchanged S&P numeric projection, explicit Nasdaq empty state,
  master-data identity, stable order, metric/null bounds, coverage/count equality,
  point-in-time bounds, DEMO/provenance consistency, and negative mutations.
- Provider tests map both fixtures without fallback, preserve exact document and
  cell order, reject duplicate universe identities and malformed coverage, keep
  nullable metrics intact, and return the Nasdaq fixture as known-empty data.
- Route/component tests cover both universes, exact evidence and disclaimer copy,
  S&P `NA`, Nasdaq empty, loading/error boundaries, navigation, and the absence of
  official-weight, full-universe, observed-consensus, scoring, and performance
  claims.
- Responsive Playwright checks extend the shared 1440/1280/390 matrix across
  both routes, keyboard focus, page/local overflow, canonical known-empty state,
  navigation, and zero console warnings, errors, or page errors.

## Market-map deferred work

P3 owns deterministic scoring, accuracy, target, return, alpha, sample-confidence,
and leaderboard calculations. P6 owns stock search/detail, analyst history,
sector benchmarks, timelines, and corporate-action handling, including the
generic map-cell-to-stock-detail acceptance target. P7 owns complete and sourced
S&P 500/Nasdaq 100 composition, official geometry inputs, price/target/revision/
contrarian modes, filters, tooltips, and a persistent or materialized market-map
read model. P5 owns real provider data. None of those facts or capabilities is
implied by these P2 DEMO shells.

## Local gate

Run from a clean feature branch before integration:

```powershell
docker compose --env-file .env.example config --quiet
pnpm --dir apps/web lint
pnpm --dir apps/web test
pnpm --dir apps/web build
pnpm --dir apps/web exec playwright install --with-deps chromium
pnpm --dir apps/web test:e2e
Set-Location apps/api
.\mvnw.cmd -B -ntp verify
```

Record only commands that were actually executed, their results, the delivered
route, and remaining P2 work in `IMPLEMENTATION_LOG.md`.
