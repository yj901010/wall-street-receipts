# P2 Acceptance Checks — Core UI

Current status: the methodology-registry and multiple market-map shell vertical
slices and the sector/industry PRICE_CHANGE treemap slice are complete. These
checks close only delivered P2 slices; dashboard, leaderboard, screener,
full-universe map, and production market-mode work stays open.

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
S&P 500/Nasdaq 100 composition, official geometry inputs, observed/live price
mode, target/revision/contrarian modes, filters, rich tooltips, and a persistent
or materialized market-map read model. P5 owns real provider data. None of those
facts or capabilities is implied by these P2 DEMO shells.

## Sector and industry PRICE_CHANGE treemap slice boundary

- `GET /maps/{universe}` and `?mode=price-change` select the fixture-backed
  PRICE_CHANGE nested treemap by default. `?mode=analyst-consensus` preserves the
  completed analyst-consensus read model. Mode controls are semantic links,
  universe links preserve the active mode, and unknown or non-scalar mode input
  fails closed with not-found behavior.
- The new canonical sources are the independently appended
  `fixtures/v1/market-treemap-sp500.json` and
  `fixtures/v1/market-treemap-nasdaq100.json`, validated by the separately
  versioned `schemas/market-treemap.schema.json` contract.
- The existing `schemas/market-map.schema.json`, `market-map.json`, and
  `market-map-nasdaq100.json` remain byte- and semantic-stable; PRICE_CHANGE is
  not added to that analyst-consensus contract.
- Both new fixtures reuse only the three existing master-backed equity
  identities. They are illustrative cross-universe DEMO samples and make no
  official index-membership, composition, classification, market-capitalization,
  or observed-price claim.
- The visual reference informs only the nested sector/industry/tile layout. No
  value, classification, ticker set, instruction, or claim is copied from it.
- This P2 read model adds no API endpoint, OpenAPI path, database migration,
  provider network call, official market-cap feed, observed quote, or persistent
  aggregation.

## PRICE_CHANGE treemap contract and fixture gate

| ID | Check | Expected result |
| --- | --- | --- |
| P2-TM01 | Separately versioned closed contract | `market-treemap.schema.json` is a closed Draft 2020-12 document with ID `urn:wall-street-receipts:schema:market-treemap:1.0.0`. The legacy market-map schema and fixtures remain unchanged and still reject PRICE_CHANGE. |
| P2-TM02 | Exact append-only catalog | The treemap registry contains exactly `market-treemap-sp500.json` followed by `market-treemap-nasdaq100.json`; each has a unique `(universe, mode, asOf)` and provenance ID. Both are appended in the manifest without renaming or overwriting either analyst-consensus fixture. |
| P2-TM03 | Raw percent versus palette scale | Mode is exactly `PRICE_CHANGE`; metric is `priceChangePercent`, unit `percent`, palette saturation stops `[-5, 5]`, and missing display `NA`. Raw values are nullable finite JSON numbers in `[-100, 1_000_000]`, remain exact outside the palette range, and are never clamped or rewritten to a saturation stop. |
| P2-TM04 | Explicit nested geometry | Geometry is exactly `NESTED_TREEMAP`, grouping is sector then industry, missing classification renders `Unclassified`, area field is `syntheticMarketCapProxy`, and area unit is `relative`. Every proxy is an integer from 1 through 1,000,000,000,000; at most 1,000 cells keep the aggregate inside JavaScript's safe-integer range. |
| P2-TM05 | Honest incomplete coverage | Both documents carry exact coverage `{kind: SAMPLE, completeUniverse: false, cellCount: cells.length, weightBasis: SYNTHETIC_MARKET_CAP_PROXY}`. The proxy controls relative tile area only and is never labelled dollars, official market cap, index weight, or a provider observation. |
| P2-TM06 | Classification semantics and limitation | `sector` and `industry` are required string-or-null fields; a null sector requires null industry, and null maps to the reserved `Unclassified` label. The locked fixture honestly demonstrates one `Technology` outer sector with three industries—Semiconductors, Software, and Consumer Electronics—and does not claim broader sector coverage merely because the engine supports it. |
| P2-TM07 | Locked synthetic evidence | In canonical order the shared cells are NVDA `(Technology, Semiconductors, 144, 1.25)`, MSFT `(Technology, Software, 121, -0.75)`, and AAPL `(Technology, Consumer Electronics, 100, null)`. AAPL remains `NA`, never zero or neutral performance. |
| P2-TM08 | Master and cross-universe consistency | Every cell resolves to the exact existing master asset ID/ticker and no master asset is added. At equal as-of time, a shared asset has identical ticker, classification, proxy, raw change, timestamp, and data mode across both universe fixtures; only universe-specific provenance differs. Cross-universe reuse does not assert official membership. |
| P2-TM09 | Point-in-time provenance | Every cell preserves `timestamp <= asOf <= provenance.capturedAt <= generatedAt <= manifest.generatedAt`. Document and cells use DEMO; cell provenance equals its envelope; all map/treemap provenance and natural identities are globally unique; source paths are tracked. |
| P2-TM10 | Deterministic hierarchy order | Sectors sort by aggregate proxy descending then label, industries within a sector by aggregate proxy descending then label, and cells within an industry by proxy descending then asset ID. Reordered, duplicate, orphaned, or divergent cells fail closed. |
| P2-TM11 | Screenshot and claim boundary | Fixture and UI copy explicitly identify every grouping label, proxy, and non-null change as synthetic. They do not repeat screenshot values or imply actual S&P 500/Nasdaq 100 membership, official sector taxonomy, market capitalization, current quote, recommendation, or investment performance. |

## PRICE_CHANGE treemap web behavior gate

| ID | Check | Expected result |
| --- | --- | --- |
| P2-TW01 | Exact route and mode behavior | `/maps/sp500` and `/maps/nasdaq100` default to PRICE_CHANGE. `?mode=price-change` is equivalent; `?mode=analyst-consensus` preserves the prior surface. Universe links preserve the active scalar mode and unsupported or repeated mode values produce not-found rather than fallback data. |
| P2-TW02 | Evidence-first metadata | Before the plot, each route shows universe, PRICE_CHANGE, percent unit, raw-value semantics, `[-5%, +5%]` palette saturation, as-of, DEMO, provenance/source, SAMPLE/incomplete coverage, three fixture cells, and synthetic market-cap-proxy geometry. |
| P2-TW03 | Nested hierarchy | The plot renders one labelled Technology sector container, three labelled industry groups, and the three ticker leaves from canonical fixture order. Copy states that this is a one-sector illustrative sample, not a broad or complete sector map. |
| P2-TW04 | Proportional geometry | Leaf area is derived only from positive integer `syntheticMarketCapProxy`; group and leaf layout areas follow descendant proxy proportions within deterministic floating-point precision. No minimum visual area, equal-weight fallback, viewport rounding, or null metric changes the stored proxy or its proportional layout input. |
| P2-TW05 | Raw display and saturated color | Positive, negative, zero, and null use accessible non-color text. Values beyond `[-5, 5]` retain their exact displayed percentage while color alone saturates at the nearest palette stop; for example `-7.25` renders `-7.25%`, never `-5%`. Null renders `NA` with neutral styling, never zero. |
| P2-TW06 | Honest state | The fixture disclaimer and coverage copy make clear that membership, grouping, proxy, and price change are synthetic DEMO evidence. The UI does not use live/current/official market-cap, heatmap-performance, gain/loss recommendation, or full-index language. |
| P2-TW07 | Accessibility and responsive layout | Mode/universe controls and the `Accessible evidence index` disclosure/scroll region are semantic and keyboard reachable with visible focus. Its table preserves asset ID, ticker, sector, industry, exact raw change or `NA`, synthetic proxy, timestamp, mode, and provenance for every canonical cell without color. A contract-extreme proportional leaf may become visually subpixel and is not required to expose a visible tile outline; the non-geometric index preserves inspection without changing or imposing a minimum area. At 1440, 1280, and 390 pixels, visible labels remain contained, local regions do not widen the page, and browser console warnings, errors, and page errors are zero. |
| P2-TW08 | Loading, errors, and regression | Loading/error boundaries are mode-neutral. Unsupported input fails closed. Existing analyst-consensus mode, `/`, `/calls`, `/calls/{id}`, `/methodology`, and all API contracts remain unchanged. |
| P2-TW09 | Interaction boundary | A leaf is non-interactive or uses an explicitly labelled call-ledger link; it does not fabricate stock detail, hover history, live tooltips, zoom claims, or P6/P7 functionality from the screenshot. |

## PRICE_CHANGE treemap required tests

- Repository-contract CI validates the exact schema and two-file projection,
  legacy byte/semantic stability, manifest order, master resolution, shared-cell
  equality, canonical hierarchy order, integer/safe-sum geometry, raw percent
  bounds independent of palette stops, UTC/provenance, and focused negative
  mutations without relying on exact-fixture inequality as a false positive.
- Provider tests reject malformed, duplicate, unsafe, divergent, non-finite, or
  unsupported fixture evidence; preserve raw percent/null values; and prove exact
  lower/upper bounds plus an out-of-scale value such as `-7.25`.
- Pure layout tests prove deterministic hierarchy, descendant sums, proportional
  leaf inputs, non-mutating palette clamp, `Unclassified`, zero-sized viewport
  safety, and no source-array mutation.
- Route/component and Playwright tests cover both universes and both modes,
  default/query/fail-closed routing, single-sector disclosure, exact evidence,
  raw out-of-scale display, NA neutrality, the keyboard-openable evidence index
  and all canonical rows (including a subpixel proxy), 1440/1280/390 containment,
  and zero console warnings, errors, or page errors.

## PRICE_CHANGE treemap deferred work

P3 retains deterministic scoring and performance claims. P5 retains observed
quotes and licensed market-cap providers. P6 retains stock detail and history.
P7 retains sourced complete-universe membership/classification, official market
capitalization or index-weight geometry, live/observed price mode, filters, rich
tooltips, zoom/history, additional map modes, and persistent/materialized map
read models. The P2 fixture does not bootstrap or imply those capabilities.

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
