# P2 Acceptance Checks — Core UI

Current status: the methodology-registry, multiple market-map shell,
sector/industry PRICE_CHANGE treemap, dashboard evidence-composition,
institution and analyst identity directories, known-unavailable market-board
publication state, recorded S&P 500 forecast-call history, application-owned
screener known-deferred shell, Korean-default bilingual evidence-first product
UI, and coherent call-detail and analyst-call list API consumer vertical slices
are complete. The coherent call-outcome audit consumer described below is also
complete. These checks close only delivered P2 slices. Leaderboard,
full-universe map, and production market-mode work stays open; actual
historical screening remains P8 work. Presentation localization does not
change canonical evidence or any backend contract.

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

## Dashboard evidence composition slice boundary

- The public web route remains `GET /`. This slice replaces its display-ready
  hard-coded payload with a deterministic composition of the existing
  `CallsProvider`, `MarketTreemapProvider`, and `MarketBoardProvider` ports.
- `DashboardSnapshot` contains only `dataMode`, `latestCalls`, `mapPreviews`,
  `marketBoard`, `eventCalendar`, and `ranking`. There is no dashboard-global
  `asOf` or source: the call ledger and each map document retain different,
  semantically scoped timestamps and provenance.
- `latestCalls` owns `items`, `asOf`, `dataMode`, `source`, and `disclaimer`. It
  projects the canonical call ledger in `eventTime` descending, `callId`
  ascending tie order, with size three. The current exact order is `demo-call-002`,
  `demo-call-001`, then `demo-call-003`.
- `mapPreviews` contains the full canonical PRICE_CHANGE treemap snapshots for
  exactly `sp500` followed by `nasdaq100`. Each preview preserves its own
  universe, mode, as-of time, coverage, metric, provenance, disclaimer, and
  nullable cells without copying or recalculating values.
- The market board is exactly `{status: NOT_PUBLISHED, missingDisplay: NA}`.
  Immutable call-event market snapshots are not current/global quotes and are
  not projected into this section.
- The event calendar is exactly `{status: NOT_PUBLISHED, missingDisplay: NA}`.
  A schedule captured in a call-bound `EventContext` is not a global upcoming
  calendar and is not projected into this section.
- Ranking is exactly `{status: P3_DEFERRED, missingDisplay: NA}`. It has no
  metric, score, rank, row, sort order, winner, or performance claim.
- The three provider ports are constructor-injected into the fixture composer.
  The composer does not import raw fixture JSON, reach through a provider, use
  the P0 Java fixture quote seam, or synthesize a common timestamp.
- This composition adds no schema, fixture, manifest member, API endpoint,
  OpenAPI path, database migration, persistence write, network provider, market
  calculation, event-calendar aggregation, or leaderboard calculation.

## Dashboard evidence composition contract gate

| ID | Check | Expected result |
| --- | --- | --- |
| P2-D01 | Existing-provider composition | The dashboard fixture adapter receives only `CallsProvider`, `MarketTreemapProvider`, and `MarketBoardProvider`. It does not duplicate canonical calls/maps/board state in application source or add a dashboard fixture/schema. |
| P2-D02 | No false global as-of | `DashboardSnapshot` has no global `asOf`, generated-at, or source field. Latest calls retain their canonical fixture `asOf`, source, and disclaimer; each map preview retains its own canonical `asOf`, generated-at evidence, provenance, and disclaimer. |
| P2-D03 | Deterministic latest calls | The adapter requests page zero, size three, sort `eventTime`, order `desc`. Equal event times use the existing `callId` ascending tie break. Current output is exactly call 002, call 001, call 003, with canonical institution, analyst-nullability, asset, source, target-nullability, event/capture time, and DEMO mode unchanged. |
| P2-D04 | Exact map previews | The adapter requests exactly `sp500` then `nasdaq100`, requires distinct universes and exact `PRICE_CHANGE` mode, and returns the complete canonical snapshots in that order. Both remain incomplete three-cell SAMPLE evidence with synthetic classifications, changes, and market-cap proxies. |
| P2-D05 | Mode parity | Calls metadata, every selected call, and both map previews are exactly DEMO. Mixed or unsupported mode input fails closed rather than being relabelled. |
| P2-D06 | Market board not published | The market-board section has exact status `NOT_PUBLISHED`, displays `NA`, and has no rows or numeric fields. It never promotes `market-snapshots.json`, `MarketDataProvider.latestQuote`, treemap changes, or hard-coded literals to current/global quote, price, change, or market-status facts. |
| P2-D07 | Event calendar not published | The event-calendar section has exact status `NOT_PUBLISHED`, displays `NA`, and has no rows or dates. It never promotes call-bound `EventContext` schedule fields to a global/today/upcoming calendar. |
| P2-D08 | Ranking deferred | The ranking section has exact status `P3_DEFERRED`, displays `NA`, and has no rows, metrics, or ordering. Call counts, outcome placeholders, methodology versions, and MODEL_ONLY status are not translated into accuracy, score, sample confidence, rank, or recommendation. |
| P2-D09 | Null and source preservation | Nullable analyst, target, metric, and evidence fields remain null and render `NA`; no zero, currency, source URL, or display-ready number is invented. Calls and maps expose their own exact canonical source/provenance and time evidence. |
| P2-D10 | Append-safe projection | Additional canonical calls may change the top-three window only through the locked sort/page rule. Unsupported, duplicate, reordered, wrong-mode, or malformed map results fail closed; neither unavailable section silently becomes populated. |
| P2-D11 | Later-phase boundary | P3 owns ranking aggregates and performance metrics; P5 owns observed/licensed market quotes; a separately sourced global calendar owns event publication. Existing call-event snapshots and contexts remain immutable call-detail evidence. |

## Dashboard web behavior gate

| ID | Check | Expected result |
| --- | --- | --- |
| P2-DW01 | Evidence-first route | `/` is server rendered and exposes a compact DEMO dashboard with latest calls, both PRICE_CHANGE previews, and three explicit unavailable/deferred states. It has no marketing hero or claim that the fixture represents current markets. |
| P2-DW02 | Latest-call evidence | All three canonical call rows link to their existing details and show event time, institution, asset, direction, nullable target, source title, DEMO mode, and the section-local call-fixture as-of/source evidence. Call 003 preserves its null analyst/targets/currency/source-document metadata rather than borrowing another call's values. |
| P2-DW03 | Map preview evidence | S&P 500 and Nasdaq 100 previews link to their existing PRICE_CHANGE routes and preserve each document's as-of, provenance, SAMPLE/incomplete coverage, synthetic proxy/change disclosure, raw null policy, and one-sector limitation. A preview is not labelled observed, live, official, or full-universe. |
| P2-DW04 | Honest unavailable sections | Market board and event calendar render `Not published` plus `NA` with the exact point-in-time reason; ranking renders `P3 deferred` plus `NA`. None renders a placeholder row, chart, metric, rank, or fake loading success. |
| P2-DW05 | Section-local semantics | The page does not display a dashboard-global `As of` or source. Calls and both maps label their own distinct times and sources; `asOf`, event time, processing/capture time, and fixture generation are not collapsed into one timestamp. |
| P2-DW06 | Loading, error, and empty behavior | The route has explicit loading and recoverable error boundaries. An empty latest-call result, empty/malformed map result, wrong mode, duplicate universe, or provider failure does not fall back to the old hard-coded values. Contract-defined NOT_PUBLISHED/P3_DEFERRED states are data, not errors. |
| P2-DW07 | Accessibility and responsive layout | Calls, preview links, status explanations, and retry/navigation controls are semantic, keyboard reachable, and visibly focused. At 1440, 1280, and 390 pixels, dense regions remain locally contained, the page has no horizontal overflow, and console warnings, errors, and page errors are zero. |
| P2-DW08 | Regression boundary | Existing `/calls`, `/calls/{id}`, `/methodology`, both map modes/universes, and all API contracts remain unchanged. The obsolete hard-coded SPX/NDX/VIX display values and percentage literals are absent from production web source. |

## Dashboard evidence composition required tests

- Provider tests inject the three ports and prove the exact query, call order,
  complete canonical row preservation, exact two-preview order, board-state
  mapping, DEMO parity, section-local metadata, and the three closed
  unavailable/deferred states.
- Negative provider tests reject mixed mode, wrong or duplicate universe,
  non-PRICE_CHANGE previews, reordered results, and provider failures without a
  hard-coded or cross-universe fallback.
- Route/component tests cover all canonical call rows, both preview summaries,
  per-section time/source labels, call-003 nulls, honest unavailable copy,
  missing-value `NA`, loading/error/empty behavior, semantic links, and the
  absence of current-market, global-calendar, ranking, and performance claims.
- Responsive Playwright extends the shared 1440/1280/390 matrix across `/`,
  keyboard navigation, links to calls/maps/methodology, page/local overflow,
  exact NOT_PUBLISHED/P3_DEFERRED states, and zero console warnings, errors, or
  page errors.
- Existing market/call/context/methodology schema and fixture gates are not
  duplicated. Repository CI may add a focused production-source check that the
  obsolete hard-coded dashboard market literals cannot reappear.

## Dashboard evidence composition deferred work

P3 retains deterministic ranking and performance aggregates. P5 retains
observed/licensed current market quotes and coherent market-board publication.
A future calendar slice requires a separately sourced global event catalog;
call-bound context remains point-in-time call evidence. Institution and analyst
leaderboards, the screener shell, persistent dashboard read models, realtime
refresh, and personalized layout remain open.

## Institution identity directory slice boundary

- The public web route is `GET /institutions`. It is a read-only identity and
  provenance directory, not the P2 roadmap's institution leaderboard.
- The sole data source is the existing tracked
  `fixtures/v1/master-data.json`. A dedicated institution provider validates the
  complete raw master-data envelope, projects only its root metadata,
  provenance, and institution records, and never imports calls or outcomes.
- `InstitutionDirectorySnapshot` has exactly `schemaVersion`, `fixtureVersion`,
  `dataMode`, `generatedAt`, `provenance`, and `institutions`. There is no
  dashboard/global as-of, calculated timestamp, metric, count, or disclaimer
  field.
- The raw fixture input remains closed over exactly nine envelope keys:
  `schemaVersion`, `fixtureVersion`, `dataMode`, `generatedAt`, `provenance`,
  `institutions`, `analysts`, `analystEmployments`, and `assets`. The latter
  three collections remain arrays but are outside this projection.
- Provenance preserves exactly `id`, `sourceType`, `sourcePaths`, `capturedAt`,
  `synthetic`, and `licenseClass`, including source-path order. Institution rows
  preserve exactly `institutionId`, `canonicalName`, `slug`, `country`,
  `active`, `dataMode`, `effectiveAt`, `capturedAt`, and `provenanceId`.
- Institutions sort by deterministic Unicode code-point `canonicalName`
  ascending and then `institutionId` ascending. Runtime or host locale never
  changes the order, and the source array is not mutated. The strict mapper is
  append-safe: it accepts a valid empty collection or later valid identities;
  repository CI, rather than generic runtime code, locks the current fixture's
  exact two-record projection.
- The current exact projection is Goldman Sachs (`inst-gs`) followed by
  JPMorgan (`inst-jpm`). Both records are synthetic DEMO fixture identities;
  `active` is fixture record state and is not a claim about a real institution's
  present operating status.
- `master-data.json` has no disclaimer field. The UI therefore supplies
  explicit policy copy, clearly labelled as product policy rather than fixture
  evidence, stating that the directory is synthetic DEMO identity data and not
  real-world coverage, endorsement, activity, performance, or advice.
- This slice adds no schema, fixture, manifest member, API endpoint, OpenAPI
  path, database migration, persistence write, provider network call, analyst
  employment view, call projection, holding, ranking, or scoring calculation.

## Institution identity directory contract gate

| ID | Check | Expected result |
| --- | --- | --- |
| P2-I01 | Existing canonical source | The provider reads only `fixtures/v1/master-data.json`; no institution-specific fixture/schema is added and the manifest declares `master-data.json` exactly once. |
| P2-I02 | Closed source shapes | Runtime and repository CI reject a missing or extra raw envelope, provenance, or institution field. Analysts, analyst employments, and assets must be arrays but are neither normalized nor exposed by the institution projection. |
| P2-I03 | Exact directory shape | Output contains only the six locked snapshot keys, the six preserved provenance keys, and the nine preserved institution keys. No source value is renamed, defaulted, enriched, or copied from another fixture. |
| P2-I04 | Exact current identities and append-safe order | Repository CI locks the current fixture to exactly two records projecting in code-point order: Goldman Sachs (`inst-gs`, `goldman-sachs`, `US`) then JPMorgan (`inst-jpm`, `jpmorgan`, `US`). IDs, canonical names, and slugs are each unique. The generic mapper does not hardcode that count or those values and deterministically accepts valid later appends. |
| P2-I05 | DEMO and provenance parity | Envelope and every record are exactly DEMO; every record `provenanceId` equals `fixture-master-data-v1`; provenance remains exact synthetic `LOCAL_SPECIFICATION`/`INTERNAL_DEMO` evidence with the two ordered local-spec source paths. |
| P2-I06 | Point-in-time identity evidence | All instants are canonical UTC with at most microsecond precision and preserve `effectiveAt <= record.capturedAt <= provenance.capturedAt <= generatedAt <= manifest.generatedAt`. The UI does not relabel generation, capture, or effective time as a live/current as-of. |
| P2-I07 | Identifier and status semantics | Institution IDs and slugs use the same lowercase alphanumeric, single-hyphen-separated runtime grammar; country is a two-letter uppercase fixture code, and `active` is a boolean fixture field. None is inferred from display names or treated as verified current-world state. |
| P2-I08 | No call or employment projection | Provider output has no analyst, employment, role, call row, call total, target, source-document, outcome, holding, or portfolio field. A link to `/calls?institutionId=...` is navigation into the existing call ledger, not evidence embedded in this directory. |
| P2-I09 | No leaderboard claim | There is no rank, order-of-merit, accuracy, return, alpha, target-hit, score, confidence, sample count, winner, recommendation, or performance-derived sort. P3 retains all deterministic leaderboard aggregates. |
| P2-I10 | Fail-closed and later-phase boundary | Malformed, duplicate, wrong-mode, time-invalid, or provenance-divergent input fails closed; accepted source row order is normalized deterministically without mutating the source array. Institution detail, aliases, histories, analysts/employment, holdings, scoring, live providers, and search remain deferred. |

## Institution identity directory web behavior gate

| ID | Check | Expected result |
| --- | --- | --- |
| P2-IW01 | Route and navigation | `/institutions` is server rendered and the primary Institutions navigation target resolves to the list route with an unambiguous current-page state. No `/institutions/{slug}` detail destination is fabricated. |
| P2-IW02 | Root evidence | Before the rows, the page exposes DEMO, fixture/schema version, fixture generation time, provenance ID/type/capture, synthetic status, license class, and the local specification paths with their exact semantics. It displays no global/live as-of. |
| P2-IW03 | Exact identity rows | The page renders exactly Goldman Sachs then JPMorgan with ID, slug, country, fixture-active state, effective time, captured time, DEMO mode, and provenance. The order is never presented as a rank. |
| P2-IW04 | Honest policy copy | Prominent static policy copy distinguishes product explanation from fixture fields and states that names/status are synthetic DEMO master evidence, not verified real-world coverage, endorsement, activity, performance, or investment advice. It does not fabricate a fixture disclaimer. |
| P2-IW05 | Calls navigation boundary | Each row may link to `/calls?institutionId={institutionId}` with an explicit `Filter call ledger` label. It does not render a call count/preview, imply calls exist, or label that destination as institution detail or performance history. |
| P2-IW06 | Loading, error, and empty behavior | Route boundaries provide an explicit loading state and recoverable error state. A valid empty institution collection maps and renders as an honest directory-empty state without placeholder identities; malformed or incomplete fixture evidence fails rather than becoming empty. |
| P2-IW07 | Accessibility and responsive layout | Identity evidence uses semantic headings, lists/tables, and labels; source paths are exposed as a semantic list, while interactive navigation, table region, filter, and retry targets are keyboard reachable with visible focus. At 1440, 1280, and 390 pixels, dense evidence remains locally contained, the page has no horizontal overflow, and console warnings, errors, and page errors are zero. |
| P2-IW08 | Regression boundary | Existing `/`, `/calls`, `/calls/{id}`, `/methodology`, both map modes/universes, and every API contract remain unchanged. No new API or database resource is implied by the fixture-backed directory. |

## Institution identity directory required tests

- Repository CI validates the closed raw/source projection, exact two records,
  manifest membership, unique identities/slugs/names, code-point output order,
  UTC/provenance bounds, DEMO parity, exact source paths, and focused semantic
  negative mutations without relying on exact-fixture inequality alone.
- Provider tests preserve every root/record value, prove deterministic
  non-mutating order and append-safety for valid empty/later identities, and
  reject extra/missing fields, malformed collections, duplicate identities,
  invalid identifiers/times, mixed modes, and provenance divergence. They also
  prove that calls, outcomes, analysts, employments, and assets do not enter the
  output type.
- Route/component tests cover both exact identities, all evidence fields,
  static policy copy, explicit call-ledger filter links, no detail/performance
  claims, loading/error/empty behavior, and absence of counts or rankings.
- Responsive Playwright extends the shared 1440/1280/390 matrix across the
  directory, primary navigation, keyboard focus, local/page overflow, policy
  copy, both filter links, and zero console warnings, errors, or page errors.

## Institution identity directory deferred work

P3 retains institution/analyst leaderboard metrics and ordering. Institution
detail, aliases/history, analyst employment views, and richer stock relations
require their own canonical contracts. P5 retains live/verified providers and
licensing review. Holdings/13F, search, pagination, saved filters, and persistent
directory read models are not bootstrapped by this P2 identity surface.

## Analyst identity directory slice boundary

- The public web route is `GET /analysts`. It is a read-only identity and
  provenance directory, not the P2 roadmap's analyst leaderboard.
- The sole data source is the existing tracked
  `fixtures/v1/master-data.json`. A dedicated analyst provider validates the
  complete raw master-data envelope and projects only root metadata,
  provenance, and analyst identity records.
- `AnalystDirectorySnapshot` has exactly `schemaVersion`, `fixtureVersion`,
  `dataMode`, `generatedAt`, `provenance`, and `analysts`. There is no global
  as-of, calculated timestamp, coverage/count, source alias, or disclaimer
  field.
- The raw input remains closed over the existing nine envelope keys. Provenance
  preserves its exact six keys, including source-path order. Analyst rows
  preserve exactly `analystId`, `canonicalName`, `active`, `dataMode`,
  `effectiveAt`, `capturedAt`, and `provenanceId`.
- Analysts sort by deterministic Unicode code-point `canonicalName` ascending
  and then `analystId` ascending. Input order is not mutated. The generic mapper
  accepts valid empty collections and later valid appends; repository CI alone
  locks the current fixture's exact two records.
- The current projection is Demo Analyst A (`analyst-demo-a`) followed by Demo
  Analyst B (`analyst-demo-b`). Both are synthetic DEMO identities. `active` is
  captured fixture state, not a claim of current employment or activity.
- `master-data.json` has no disclaimer. The UI supplies clearly labelled
  product-policy copy rather than inventing a fixture disclaimer or coverage
  statement.
- This slice adds no analyst employment or institution join, call row/preview
  or count, metric, rank, detail route, schema, fixture, manifest member, API or
  OpenAPI path, migration, persistence write, or provider network call. An
  explicit `/calls?analystId=...` link is navigation into the existing ledger,
  not call evidence projected into the directory.

## Analyst identity directory contract gate

| ID | Check | Expected result |
| --- | --- | --- |
| P2-A01 | Existing canonical source | The provider reads only `fixtures/v1/master-data.json`; no analyst-directory schema/fixture is added and the manifest declares `master-data.json` exactly once. |
| P2-A02 | Closed source shapes | Runtime and repository CI reject a missing or extra raw envelope, provenance, or analyst field. Institutions, analyst employments, and assets remain required arrays but are neither normalized nor exposed by the analyst projection. |
| P2-A03 | Exact projection shape | Output has only the six snapshot keys, six preserved provenance keys, and seven preserved analyst keys. No source value is renamed, defaulted, enriched, or copied from another collection or fixture. |
| P2-A04 | Current identities and append-safe order | Repository CI locks the current fixture to Demo Analyst A then Demo Analyst B in code-point order. Analyst IDs are unique; canonical names need not be because distinct people can share a name, so analyst ID is the deterministic tie-break. The generic mapper has no hard-coded count or values and accepts valid empty/later identities without mutating input. |
| P2-A05 | DEMO and provenance parity | Envelope and every analyst are DEMO; every `provenanceId` equals `fixture-master-data-v1`; provenance remains exact synthetic `LOCAL_SPECIFICATION`/`INTERNAL_DEMO` evidence with its two ordered source paths. |
| P2-A06 | Point-in-time identity evidence | Canonical UTC instants use at most microsecond precision and preserve `effectiveAt <= analyst.capturedAt <= provenance.capturedAt <= generatedAt <= manifest.generatedAt`. No time is relabelled as a live/current as-of. |
| P2-A07 | Identifier and active-state semantics | Analyst IDs use the lowercase alphanumeric, single-hyphen-separated runtime grammar; canonical names are nonblank trimmed evidence and `active` is boolean fixture state. Neither is inferred or presented as verified present-world employment/activity. |
| P2-A08 | No relationship or call projection | Output contains no institution, employment, role, call row/count, asset, source document, outcome, holding, or portfolio field. A row may expose only an explicit `Filter call ledger` link to `/calls?analystId=...`; it carries no preview, count, existence claim, or relationship evidence. |
| P2-A09 | No leaderboard claim | There is no rank, accuracy, hit rate, return, alpha, target result, score, confidence, sample count, winner, recommendation, or performance-derived sort. P3 retains all leaderboard aggregation. |
| P2-A10 | Fail-closed and later-phase boundary | Malformed, duplicate, wrong-mode, time-invalid, or provenance-divergent input fails closed; accepted source order is normalized without mutation. Detail, employment, scoring, live providers, search, and pagination remain deferred. |

## Analyst identity directory web behavior gate

| ID | Check | Expected result |
| --- | --- | --- |
| P2-AW01 | Route and navigation | `/analysts` is server rendered and primary Analysts navigation resolves to the list route with an unambiguous current-page state. No `/analysts/{id}` destination is fabricated. |
| P2-AW02 | Root evidence | Before the rows, the page exposes DEMO, fixture/schema version, generation time, provenance ID/type/capture, synthetic state, license class, and exact source paths. It displays no global/live as-of. |
| P2-AW03 | Exact current rows | The page renders Demo Analyst A then Demo Analyst B with ID, recorded-active state, effective time, captured time, DEMO mode, and provenance. Alphabetic identity order is never labelled a rank. |
| P2-AW04 | Honest policy copy | Prominent static product-policy copy says the identities and active markers are limited synthetic DEMO master evidence, not verified real-world coverage, employment, activity, endorsement, performance, or investment advice. It does not fabricate a fixture disclaimer. |
| P2-AW05 | Relationship and calls boundary | No analyst row renders an employer, role, employment interval, call preview/count/existence claim, asset, outcome, score, or detail link. The only permitted row action is an explicitly labelled `Filter call ledger` link to `/calls?analystId={analystId}`; missing relationships do not become placeholders or inferred facts. |
| P2-AW06 | Loading, error, and empty behavior | Route boundaries provide explicit loading and recoverable error states. A valid empty analyst collection renders an honest empty directory without placeholder identities; malformed/incomplete evidence fails rather than becoming empty. |
| P2-AW07 | Accessibility and responsive layout | Identity evidence uses semantic headings, table/list structures, and labels; source paths are a semantic list, while navigation, table region, and retry targets are keyboard reachable with visible focus. At 1440, 1280, and 390 pixels dense evidence remains locally contained, page overflow is absent, and console warnings/errors/page errors are zero. |
| P2-AW08 | Regression boundary | Existing dashboard, calls/detail, institution, methodology, map routes, and every API contract remain unchanged. No new backend resource is implied by the fixture-backed directory. |

## Analyst identity directory required tests

- Repository CI validates the shared closed master envelope/provenance once,
  both identity collections' current exact records, analyst unique/order/time/
  DEMO invariants, manifest membership, and focused semantic negative mutations
  without relying on exact-fixture inequality.
- Provider tests preserve every projected value, prove full Unicode code-point
  order, valid empty/later append behavior and source non-mutation, and reject
  malformed shapes, collections, identifiers, times, modes, provenance, and
  duplicates. Output typing excludes institutions, employments, calls, counts,
  metrics, and rankings.
- Route/component tests cover exact identity/root evidence, static policy copy,
  filter-only ledger navigation with no call claim, no relationships/
  performance/detail, and loading/error/empty states.
- Responsive Playwright covers `/analysts`, primary navigation, keyboard focus,
  local/page overflow, policy/evidence, and zero console warnings, errors, or
  page errors at 1440, 1280, and 390 pixels.

## Analyst identity directory deferred work

P3 retains analyst/institution leaderboard metrics and ordering. Analyst detail,
aliases/history, employment relationships, institution joins, calls/counts,
holdings, search, and pagination require later contracts. P5 retains verified
providers and licensing review. No later capability is bootstrapped by this
identity-only P2 route.

## Market-board known-unavailable slice boundary

- The public web route is `GET /market`. It is a server-rendered publication-
  status and evidence surface, not a current, latest, delayed, end-of-day, or
  session quote board.
- The sole canonical source is the new append-only
  `fixtures/v1/market-board.json`, validated by the closed Draft 2020-12
  `schemas/market-board.schema.json` contract. P2 connects no external or paid
  market provider.
- The fixture root has exactly `schemaVersion`, `fixtureVersion`, `dataMode`,
  `generatedAt`, `provenance`, `scope`, `publicationStatus`,
  `publicationReasonCode`, `marketAsOf`, `missingDisplay`, `quotes`, and
  `disclaimer`. Provenance has exactly `id`, `sourceType`, `sourcePaths`,
  `capturedAt`, `synthetic`, and `licenseClass`.
- Version 1 records only the known-unavailable state:
  `scope=GLOBAL_MARKET_OVERVIEW`, `publicationStatus=NOT_PUBLISHED`,
  `publicationReasonCode=NO_CANONICAL_GLOBAL_QUOTE_CATALOG`,
  `marketAsOf=null`, `missingDisplay=NA`, and `quotes=[]`. The empty array is
  canonical state, not a load failure or permission to supply fallback rows.
- Fixture generation and provenance capture are policy/catalog metadata only.
  They are not market as-of, freshness, latency, session, or quote timestamps.
- The fixture disclaimer explicitly says no price, change, session status,
  freshness, or coverage was observed, derived, inferred, or promoted from
  immutable call-event snapshots, PRICE_CHANGE treemaps, or application
  literals.
- A dedicated `MarketBoardProvider` owns this canonical snapshot. The dashboard
  composer injects that port and maps it to its existing exact public shape
  `marketBoard={status: NOT_PUBLISHED, missingDisplay: NA}`. No field is added
  to `DashboardSnapshot`; existing dashboard consumers remain compatible.
- This slice adds no API/OpenAPI path, database migration, persistence write,
  quote cache, stream, polling loop, freshness calculation, or provider network
  call. The P0 Java `latestQuote` fixture seam is not a canonical source for
  this surface.

## Market-board known-unavailable contract gate

| ID | Check | Expected result |
| --- | --- | --- |
| P2-MB01 | Closed versioned document | `market-board.schema.json` is Draft 2020-12, has stable ID `urn:wall-street-receipts:schema:market-board:1.0.0`, closes the exact 12-field root and six-field provenance objects, and rejects every missing or extra field. |
| P2-MB02 | Exact publication state | The fixture is exactly schema `1.0.0`, fixture `v1`, DEMO, global-market-overview scope, `NOT_PUBLISHED`, reason `NO_CANONICAL_GLOBAL_QUOTE_CATALOG`, `marketAsOf=null`, missing display `NA`, and the locked disclaimer. No field is inferred or defaulted. |
| P2-MB03 | Structurally zero quotes | Version 1 constrains `quotes` to an empty array with `maxItems=0` and a false item schema. A symbol, price, change, currency, session, status, timestamp, freshness, delay, count, or coverage row cannot validate. |
| P2-MB04 | Provenance and mode | Provenance is exactly `fixture-market-board-v1`, synthetic `LOCAL_SPECIFICATION`/`INTERNAL_DEMO` evidence and preserves the two ordered tracked source paths. It never masquerades as vendor, exchange, observed, or licensed market data. |
| P2-MB05 | Point-in-time catalog bounds | Canonical UTC instants use `Z` and at most microsecond precision. They preserve fixture provenance capture `<=` fixture generation `<=` manifest provenance capture `<=` manifest generation. `marketAsOf` remains null and no catalog time is relabelled as market time. |
| P2-MB06 | Append-only manifest | `market-board.json` occurs exactly once as the final appended manifest member; prior member order is preserved, all tracked fixture paths remain a duplicate-free exact set, and the manifest cannot predate a declared document. |
| P2-MB07 | No cross-semantic promotion | The market-board adapter reads only `market-board.json`. It cannot read `market-snapshots.json`, call contexts/calls, map or treemap fixtures, P0 `MarketDataProvider.latestQuote`, or application quote literals as board evidence. |
| P2-MB08 | Dashboard compatibility | The dashboard receives `MarketBoardProvider` by constructor injection, imports no raw board JSON, validates the canonical unavailable snapshot, and still emits exactly `{status: NOT_PUBLISHED, missingDisplay: NA}` with no dashboard-global as-of/source or new field. |
| P2-MB09 | Fail closed | Missing, extra, wrong-mode, wrong-status, wrong-reason, non-null-as-of, nonempty-quote, weakened-disclaimer, provenance-divergent, time-invalid, offset, or finer-than-microsecond input fails. It never becomes an empty success synthesized by UI code. |
| P2-MB10 | Backend defer | `contracts/openapi.yaml`, Spring controllers, repositories, and Flyway migrations remain unchanged. `/market` is fixture-backed SSR; it does not imply a `/v1/market` API or persisted quote catalog. |
| P2-MB11 | Version boundary | A later observed/licensed published board must use a separately reviewed additive/versioned contract with quote identity, venue/session, as-of/freshness, licensing, and correction semantics. It may not widen the closed meaning of schema `1.0.0`. |

## Market-board web behavior gate

| ID | Check | Expected result |
| --- | --- | --- |
| P2-MBW01 | Route and navigation | `/market` is server rendered and reachable through clear semantic navigation. It is titled and described as publication status, never “markets today,” “latest quotes,” or a live terminal. |
| P2-MBW02 | Exact status evidence | The page shows DEMO, `NOT_PUBLISHED`, `NA`, the exact reason code, schema/fixture versions, global-overview scope, fixture generation, provenance ID/type/capture/synthetic/license/source paths, and the canonical disclaimer. |
| P2-MBW03 | Honest time semantics | Market as-of renders `NA`. Fixture generation and capture are explicitly labelled evidence/catalog times and are not called market time, freshness, delay, current, live, close, or session time. |
| P2-MBW04 | Zero-row presentation | `quotes=[]` renders a known-unavailable explanation, not a quote table, zero price/change, placeholder ticker, skeleton-success row, count/coverage claim, stale badge, or generic empty-search result. |
| P2-MBW05 | Dashboard integration | The dashboard market-board section derives its existing two-field unavailable state from the injected canonical provider and may link to `/market`; its exact output and existing call/map/calendar/ranking behavior remain unchanged. |
| P2-MBW06 | Loading and error boundaries | The route has explicit loading and recoverable error states. Provider or malformed-document failure does not fall back to call-event snapshots, treemap values, P0 fixture quotes, or hard-coded display data. |
| P2-MBW07 | Accessibility and responsive layout | Status/evidence uses semantic headings, descriptions, and a source-path list; navigation and retry targets are keyboard reachable with visible focus. At 1440, 1280, and 390 pixels dense evidence remains locally contained, page overflow is absent, and console warnings/errors/page errors are zero. |
| P2-MBW08 | Regression boundary | Dashboard, calls/detail/context, methodology, institutions, analysts, both map modes/universes, and all API contracts remain unchanged. No new backend resource is implied. |

## Market-board known-unavailable required tests

- Repository CI validates the schema, exact document/provenance fields and
  values, structurally empty quote catalog, disclaimer, canonical times,
  manifest membership/order/parity, tracked source paths, semantic negative
  mutations with exact-fixture comparison disabled, provider isolation, and
  API defer.
- Provider tests preserve every fixture field and null, reject malformed
  shapes/status/reason/provenance/times/nonempty quotes, and prove there is no
  fallback or mutation. Dashboard composition tests inject the port and lock
  the pre-existing two-field board output exactly.
- Route/component tests cover all publication/provenance evidence, honest time
  and no-quote copy, no quote rows/numbers/current claims, dashboard link/state,
  and loading/error behavior.
- Responsive Playwright covers `/market` and dashboard integration at 1440,
  1280, and 390 pixels, including keyboard focus, local/page overflow, exact
  known-unavailable evidence, and zero console warnings, errors, or page errors.

## Market-board deferred work

P4 retains realtime tick ingestion, caching, SSE/reconnect, stale detection,
and market-hours behavior. P5 retains vendor selection, licensing, canonical
observed quote normalization, correction/freshness semantics, and the first
coherent `PUBLISHED` board. P6 retains stock/equity history and P8 retains
historical market bars plus operational data-quality monitoring. The recorded
P2 forecast-call history slice does not shortcut any of those capabilities.

## S&P 500 recorded forecast-call history slice boundary

- The public web route is `GET /markets/sp500`. It is a history of canonical
  analyst-call events whose exact asset is the synthetic DEMO S&P 500 identity,
  not an S&P 500 price history, forecast consensus, market outlook, or
  performance chart.
- A dedicated `Sp500HistoryProvider` composes only the existing injected
  `CallsProvider`. It calls `metadata()` and issues exactly
  `list({assetId: "asset-spx", page: 0, size: 25, sort: "eventTime", order:
  "desc"})`; it imports no raw fixture JSON.
- `Sp500HistorySnapshot` has exactly `dataMode`, `asOf`, `source`,
  `disclaimer`, `asset`, `items`, and `page`. `asset` is the existing complete
  `AssetSummary`, every item is an existing complete `AnalystCallView`, and
  `page` is the existing complete `PageMetadata`. No canonical field is
  renamed, defaulted, enriched, or recalculated inside an item.
- `asOf` is the analyst-call catalog generation time exposed by
  `CallsMetadata`; it is labelled call-catalog evidence and is never presented
  as a market price, current forecast, quote, freshness, or performance as-of.
- The current exact projection contains one item, `demo-call-001`, for
  `asset-spx` / `INDEX` / `S&P 500 Index` / `SPX`. It preserves the raw event,
  institution, nullable analyst, target, rating, source document/reference,
  capture, provenance, status, and DEMO fields and links to the existing call
  detail.
- `items` is only the fixed first page returned by the exact synthetic fixture
  query; `page.totalElements` is the query total and may exceed the number of
  displayed items. Neither value is a complete-universe, complete-provider,
  all-analyst, real-world, or market-history coverage claim. A valid empty
  result or future appended rows returned on that fixed first page remain
  honest and deterministic.
- This projection does not join `market-snapshots.json`, call outcomes,
  revisions, contexts, maps/treemaps, the market board, or the P0 quote seam.
  Snapshot context remains on call detail; outcome metrics remain null/P3-owned.
- The primary header remains unchanged. `/market` exposes an explicit semantic
  link to `/markets/sp500`, and the history route retains Market as its current
  navigation section.
- This slice adds no canonical schema, fixture, manifest member, API/OpenAPI
  path, database migration, persistence write, network provider, calculation,
  or scheduled job. Existing filtered call-list and call-detail contracts are
  sufficient.

## S&P 500 recorded forecast-call history contract gate

| ID | Check | Expected result |
| --- | --- | --- |
| P2-SH01 | Existing canonical sources | The history adapter receives only `CallsProvider`; no history-specific JSON/schema is added and no raw fixture is imported. Existing master identity, analyst-call/source evidence, and list metadata remain the sole projection inputs. |
| P2-SH02 | Exact provider query | The adapter invokes `metadata()` and one list query with exactly `assetId=asset-spx`, page `0`, size `25`, sort `eventTime`, and order `desc`. It neither broadens the asset filter nor silently requests current quotes, outcomes, or contexts. |
| P2-SH03 | Exact read-model shape | Output has only the seven locked root keys, complete existing four-field asset summary, complete existing call views, and complete existing page metadata. No snapshot, outcome, aggregate, coverage, calculated timestamp, or display-ready metric field is added. |
| P2-SH04 | Current exact projection | Repository CI locks the current fixture query to one `demo-call-001` item and exact `asset-spx`/`INDEX`/`S&P 500 Index`/`SPX` identity. Runtime uniquely selects only `asset-spx` from metadata and preserves that four-field summary; it does not hard-code the current count, call ID, display name/type/ticker, target, institution, analyst, or source values. |
| P2-SH05 | Deterministic and append-safe page | Accepted first-page items are unique by call ID and ordered by full UTC `eventTime` descending with call ID ascending as the tie-break, without mutating provider arrays. A valid empty result or future valid SPX rows returned on the fixed first page are preserved. `page.totalElements` remains the filtered query total and is not relabelled as the displayed item count or complete ledger. |
| P2-SH06 | DEMO, time, and source semantics | Metadata, calls, source documents, and source references remain DEMO and retain their guarded provenance/capture values; the four-field asset summary is preserved exactly from metadata. Catalog `asOf`, event time, processing time, published time, and capture time stay distinct and are not relabelled as current market time. |
| P2-SH07 | Null and evidence preservation | Row-projected nullable analyst, rating, targets/currency/date, and publisher remain null and render `NA`; no zero, placeholder, borrowed evidence, or inferred analyst identity is introduced. Nonprojected nullable URL/publication/provider-identity, fragment, extraction-confidence, and hash fields remain unchanged in the complete call view and are available through the exact call-detail `#source` boundary rather than being summarized as row facts. |
| P2-SH08 | No result or leaderboard claim | The page contains no return, alpha, target hit/error, directional win, MFE/MAE, accuracy, score, rank, outcome/performance confidence, winner, recommendation, scoring sample count, or outcome ordering. Existing model-only/incomplete outcomes are not projected; fixed-query item and `totalElements` counts remain pagination evidence only. |
| P2-SH09 | No market-history claim | The page contains no current/latest/delayed/EOD quote, OHLCV series, index performance, market session, chart interpolation, snapshot value, derived target gap/change, consensus, completeness, or forecast-quality claim. A detail link may expose the existing call-bound immutable snapshot in its original context. |
| P2-SH10 | Fail closed | A provider failure, wrong fixed query/page contract, unresolved or divergent SPX asset join, mixed mode, non-SPX row, duplicate/reordered row, divergent guarded call/institution/analyst/source join or provenance, invalid guarded UTC chronology, or fixed page/item-count inconsistency fails rather than producing partial or fallback history. Canonical nested validation remains owned by `CallsProvider`. |
| P2-SH11 | Backend and phase boundary | Existing `GET /v1/calls?assetId=asset-spx` and `GET /v1/calls/{id}` capabilities remain unchanged; no history endpoint or persistence model is implied. P3/P4/P5/P6/P8 capabilities remain deferred. |

## S&P 500 recorded forecast-call history web behavior gate

| ID | Check | Expected result |
| --- | --- | --- |
| P2-SHW01 | Route and navigation | `/markets/sp500` is server rendered, `/market` exposes an explicit recorded-forecast-history link, and the route retains Market as current navigation. No extra primary-header item is added. |
| P2-SHW02 | Honest scope | Page title and prominent copy say recorded synthetic DEMO forecast-call events and limited fixture evidence. They do not call the view price history, live/current consensus, every forecast, complete coverage, market performance, or investment advice. |
| P2-SHW03 | Root evidence | Before the rows, the page exposes DEMO, call-catalog as-of, source/provenance ID, exact fixture disclaimer, canonical SPX asset ID/type/name/ticker, and fixed first-page metadata. Copy distinguishes displayed items from `totalElements` and makes no complete-ledger claim. |
| P2-SHW04 | Exact current event | The current page renders exactly `demo-call-001` with event/processing/capture times, institution, analyst, direction, raw rating, previous/current target and currency, status, and summarized source title/publisher/verified evidence. Its exact `/calls/demo-call-001#source` link is the boundary for the complete source document/reference evidence; DEMO/provenance remain visible at the scoped root. |
| P2-SHW05 | No calculation or chart | Raw previous and current targets are displayed separately; no target delta/gap, return, hit, accuracy, rank, chart line, current price, snapshot number, outcome, or causal claim is calculated or rendered. |
| P2-SHW06 | Loading, error, and empty behavior | Route boundaries provide explicit loading and recoverable error states. A valid empty provider page renders an honest no-recorded-SPX-call state; malformed or failed evidence never falls back to hard-coded call, quote, snapshot, or chart values. |
| P2-SHW07 | Accessibility and responsive layout | Evidence uses semantic headings, table/list structures, source links, and labels; navigation, history region, detail link, and retry are keyboard reachable with visible focus. At 1440, 1280, and 390 pixels dense evidence stays locally contained, page overflow is absent, and console warnings/errors/page errors are zero. |
| P2-SHW08 | Regression boundary | Existing dashboard, `/market`, calls/detail/context, methodology, institutions, analysts, maps, and all API contracts remain unchanged. Dashboard market/calendar/ranking states and board publication semantics are not weakened. |

## S&P 500 recorded forecast-call history required tests

- Repository CI locks the current exact SPX asset/call projection, exact query
  tokens, provider-only source boundary, absence of duplicated raw fixtures or
  cross-semantic imports, and absence of a new schema/fixture/API contract
  without duplicating the existing canonical schema gates.
- Provider tests prove the exact calls query and metadata invocation, seven-key
  mapping, complete field preservation, current one-row evidence, deterministic
  UTC/tie order, source non-mutation, valid empty/future rows on the fixed first
  page, and rejection of wrong guarded identity/query/page/mode/order/
  provenance/join/chronology/provider states.
- Route/component tests cover scoped catalog evidence, displayed-item versus
  query-total copy, the exact current row, nullable rendering, raw targets,
  summarized source fields, the exact call-detail `#source` navigation,
  loading/error/empty behavior, and absence of price-history/performance/
  ranking/completeness claims.
- Responsive Playwright covers Market-to-history and history-to-call-detail
  navigation, keyboard focus, dense local containment, page overflow, and zero
  console warnings, errors, or page errors at 1440, 1280, and 390 pixels.

## S&P 500 recorded forecast-call history deferred work

P3 retains deterministic outcome/scoring metrics and aggregates. P4/P5 retain
realtime/current market transport and licensed provider data. P6 retains stock
and equity history, sector benchmarks, corporate-action-aware views, and richer
asset surfaces. P8 retains historical bars, materialized screener features, and
large-scale history. Pagination/search, consensus, a chart, snapshot joins,
outcome joins, provider-completeness claims, and persistent/materialized history
read models are not bootstrapped by this P2 fixed-page event history.

## Screener known-deferred shell boundary

- The public web route is `GET /screener`. It publishes application capability
  state only; it is not a stock screen, historical query, search result, ranked
  list, or canonical data document.
- `ScreenerShellState` has exactly `dataMode`, `scope`, `status`, `reasonCode`,
  and `missingDisplay`, in that order. Its one supported value is exactly
  `{dataMode: DEMO, scope: HISTORICAL_EQUITY_SCREENING, status: P8_DEFERRED,
  reasonCode: NO_CANONICAL_HISTORICAL_SCREENING_FEATURE_CATALOG,
  missingDisplay: NA}`.
- This five-key value is an application-owned phase policy, explicitly labelled
  `Product availability policy · not fixture evidence`. It is not assigned a
  schema version, fixture version, generation/as-of/capture time, source,
  provenance, license, or fixture disclaimer.
- The state has no filter, query, result, row, count, pagination, sort, metric,
  feature, universe, symbol, price, target, outcome, or ranking field. The page
  does not add disabled controls or a zero-result success that could imply a
  screen was executed.
- `/screener` is query-free. If `searchParams` contains any key, including an
  unknown or repeated key, the page calls Next `notFound()` before rendering
  the supported state. The custom mode-neutral unsupported-request body is
  `noindex` and contains no filter/result fallback. No raw HTTP status is
  asserted because streamed Next responses may determine it before the body.
- No canonical master identity, analyst call, outcome, market snapshot, map,
  treemap, market-board state, call context, or application literal is promoted
  into screening evidence. Links to `/calls` and `/methodology`, if present,
  are explicitly adjacent existing evidence and never screen output.
- The primary navigation adds `Screener` after Maps and before Methodology. It
  exposes an exact `/screener` target and current-page state while preserving
  locally contained keyboard navigation on narrow viewports.
- This slice adds no canonical schema, fixture, manifest member, OpenAPI path,
  Spring API/controller, Flyway migration, persistence model, provider network
  call, scheduler, saved search, or materialized feature store.

## Screener known-deferred shell contract gate

| ID | Check | Expected result |
| --- | --- | --- |
| P2-SC01 | Exact application state | `ScreenerShellState` contains only the five ordered keys and exact `DEMO` / `HISTORICAL_EQUITY_SCREENING` / `P8_DEFERRED` / `NO_CANONICAL_HISTORICAL_SCREENING_FEATURE_CATALOG` / `NA` values. No value is inferred from another provider. |
| P2-SC02 | Not fixture evidence | The state is owned by typed application code and the UI labels it product availability policy. It has no schema/fixture version, timestamp, source, provenance, license, disclaimer, or claim that manifest absence is a point-in-time provider fact. No screener JSON or schema is added. |
| P2-SC03 | Structurally no screen | State and rendered output contain no filter/query definition, result/row collection, count, pagination, sort, feature, metric, or calculation field. `NA` means the capability is not published; it never becomes zero matches, zero value, an empty successful screen, or completeness. |
| P2-SC04 | Query-free fail closed | The supported request has no search-parameter keys. Any key, including unknown or repeated input, invokes Next `notFound()` and the custom noindex unsupported-request body; no input is ignored, normalized, executed, or echoed as a filter. Raw response status is not an acceptance assertion. |
| P2-SC05 | No cross-semantic promotion | Master assets, calls, call-event snapshots/contexts, incomplete outcomes, market maps/treemaps, and market-board state are not imported, joined, counted, or relabelled as screener rows or features. Existing links do not claim a screen or match exists. |
| P2-SC06 | No numeric or ranking claim | There is no current/historical price, return, alpha, target, hit, accuracy, score, rank, market cap, valuation, volatility, regime, technical/fundamental feature, recommendation, confidence, sample count, winner, or ordering claim. |
| P2-SC07 | Honest phase boundary | `P8_DEFERRED` means P8 owns historical bars, feature/fact materialization, screening queries, and analytics. P3 still owns deterministic outcomes/ranking, and P5 still owns licensed observed providers and rights review. The P2 shell does not imply any of those capabilities. |
| P2-SC08 | No backend expansion | `contracts/openapi.yaml`, Spring controllers/repositories, Flyway, and persisted data remain unchanged. No `/v1/screener` endpoint, provider transport, network call, refresh process, export, or saved-filter operation is implied. |
| P2-SC09 | Future publication boundary | A future populated screener requires a separately reviewed versioned feature/query contract with universe identity, point-in-time inputs, null semantics, licensing, correction/replay rules, deterministic calculations, and pagination/sort behavior. It must not widen this application-only status into fake data. |

## Screener known-deferred shell web behavior gate

| ID | Check | Expected result |
| --- | --- | --- |
| P2-SCW01 | Route and navigation | `/screener` is server rendered. Primary navigation places Screener after Maps and before Methodology, uses `/screener`, and exposes its unambiguous current-page state without breaking existing destinations. |
| P2-SCW02 | Exact visible state | The page shows DEMO, `HISTORICAL_EQUITY_SCREENING`, `P8_DEFERRED`, `NO_CANONICAL_HISTORICAL_SCREENING_FEATURE_CATALOG`, and `NA` exactly, with a prominent product-policy/not-fixture-evidence label. |
| P2-SCW03 | No fabricated evidence | The page displays no fixture/schema version, generation/as-of/capture time, source/provenance/license/disclaimer, symbol/universe list, filter value, result, count, sort, metric, table, chart, badge suggesting freshness, or disabled faux screening control. |
| P2-SCW04 | Honest explanation | Copy explains that no historical screening feature catalog is published and that `NA` is capability state, not zero matches. It does not describe the shell as live, current, delayed, EOD, complete, searchable, calculated, backtested, or investment advice. |
| P2-SCW05 | Query rejection | A request with any search-parameter key renders the custom mode-neutral unsupported-request boundary, is `noindex`, and exposes no accepted filter, result fallback, state leakage, or invented row. Browser tests assert the body and metadata, not a raw streamed HTTP status. |
| P2-SCW06 | Loading and error boundaries | The route has explicit DEMO loading and recoverable error states. Neither state falls back to a call, asset, outcome, snapshot, map cell, metric, filter, result, or application numeric literal; known deferred is data, not an error or empty search. |
| P2-SCW07 | Accessibility and responsive layout | Availability evidence uses semantic headings/descriptions and status text independent of color. Navigation, policy links, and retry are keyboard reachable with visible focus. At 1440, 1280, and 390 pixels, navigation stays locally contained, page overflow is absent, and console warnings/errors/page errors are zero. |
| P2-SCW08 | Regression boundary | Dashboard, market/history, calls/detail/context, institutions, analysts, methodology, both map modes/universes, existing navigation targets, and every API contract remain unchanged apart from the additive Screener navigation item. |

## Screener known-deferred shell required tests

- Unit tests lock the five-key state and exact values and prove there is no
  filter/result/count/sort/metric field or fixture/provider dependency.
- Page/component tests cover the exact visible capability state, product-policy
  semantics, absence of controls/rows/numerics/evidence claims, loading/error,
  and adjacent links without treating them as screen output.
- Search-parameter tests cover unknown, recognized-looking, empty-value, and
  repeated keys; every nonempty key set invokes `notFound()` before state
  rendering. The custom unsupported-request body and noindex metadata are
  asserted without requiring a raw streamed response status.
- Repository CI locks the absence of a screener schema/fixture/manifest/API/
  migration expansion, the application source boundary, the required route
  files, and the append-safe non-test production-file scan without duplicating
  canonical fixture validation.
- Responsive Playwright covers supported and unsupported `/screener` requests,
  primary-navigation order/current state, keyboard focus and narrow local-nav
  containment, page overflow, and zero console warnings, errors, or page errors
  at 1440, 1280, and 390 pixels.

## Screener known-deferred shell deferred work

P8 retains historical bars, point-in-time call/outcome facts, materialized
screening features, query execution, pagination/sorting, saved screens, and
regime analytics. P3 retains reproducible outcome and leaderboard metrics. P5
retains licensed observed market/analyst providers, provider health, licensing
flags, and current rights review. No future API, feature catalog, calculation,
or provider capability is bootstrapped by this application-owned P2 status.

## Korean-default bilingual product UI boundary

- Every existing product route remains at its current URL. This slice adds no
  locale-prefixed route, locale query parameter, API endpoint, canonical
  schema, fixture, manifest member, OpenAPI path, Flyway migration, persisted
  preference, or provider/network dependency.
- Presentation locale is exactly `ko` or `en`. The server resolves the exact
  `wsr_locale` cookie value; a missing, blank, or otherwise unsupported value
  resolves to Korean. It does not infer locale from `Accept-Language`, browser
  APIs, geography, timezone, URL state, or a client-side default.
- Locale mutation is a server action accepting only exact scalar `ko` or `en`.
  Invalid mutation input is rejected rather than normalized. It writes an
  HTTP-only `wsr_locale` cookie with `Path=/`, `SameSite=Lax`, `Max-Age=31536000`
  and `Secure` in production only. The action does not redirect, change the
  current path/query/hash, or use local/session storage.
- Korean is present in the raw first server response when the cookie is absent
  or invalid; English is present in the raw first response for a valid `en`
  cookie. The document `lang` value equals the resolved locale before
  hydration. A server-set preference survives revisit and direct navigation.
- Product labels, explanations, accessible names, loading/error/empty copy,
  and mode-neutral unsupported-request copy may be translated. Canonical
  evidence never is: IDs, hashes, versions, enum/status/reason/data-mode
  tokens, tickers, entity identities, source titles/publishers, URLs,
  `sourcePaths`, fixture disclaimers, ISO timestamps, numeric strings, nulls,
  ordering, and `NA` remain byte- and meaning-preserving.
- The common root not-found boundary resolves the same server locale and
  exposes only translated route guidance plus exact `/` and `/calls` links. It
  has no DEMO badge, provider state, inferred evidence, or route fallback.
- The visual system is a white editorial financial terminal: white or quiet
  off-white canvas, dark readable type, thin neutral rules, near-square
  controls, restrained low radii, compact monospace metadata and tabular
  values, restrained green/red semantic accents, and deliberate whitespace.
  It does not introduce gradients, glass effects, neon glow, giant heroes,
  decorative cards/pills, or fabricated live, ranking, screening, market, or
  outcome values.

## Korean-default bilingual product UI contract gate

| ID | Check | Expected result |
| --- | --- | --- |
| P2-L10N01 | Closed locale set and default | `SUPPORTED_LOCALES` is exactly ordered `ko`, `en`; `DEFAULT_LOCALE` is exactly `ko`. Server parsing returns only those values and maps every missing or unsupported cookie value to `ko`. No third locale or English fallback is accepted. |
| P2-L10N02 | Exact server-owned cookie | The cookie is exactly `wsr_locale` with `HttpOnly`, `Path=/`, `SameSite=Lax`, and one-year `Max-Age=31536000`; `Secure` is true only in production. Mutation accepts only exact `ko` or `en`, rejects missing/file/unsupported input before writing, and neither redirects nor mutates URL state. |
| P2-L10N03 | SSR before hydration | Locale is read through the server cookie boundary. Raw HTML without a valid cookie is Korean with `html[lang=ko]`; raw HTML with `wsr_locale=en` is English with `html[lang=en]`. Hydration, a client effect, local storage, or an extra translation request is never required to correct the language. |
| P2-L10N04 | Revisit persistence | A keyboard-activated locale control invokes the server action, writes the exact cookie, preserves path/query/hash, and renders the selected locale on revisit and direct navigation. Invalid resolved cookie input deterministically returns to Korean. |
| P2-L10N05 | No inference or remote translation | Production localization source contains no `Accept-Language`, `navigator.language`/`languages`, `localStorage`, `sessionStorage`, geolocation, remote translation SDK/API, locale fetch, or browser-only preference store. Message catalogs are typed, colocated, version-controlled application source. |
| P2-L10N06 | Immutable canonical evidence | Locale changes presentation copy only. Canonical fixture/provider/API data, field values, source evidence, null/`NA` policy, microsecond UTC values, numeric formatting inputs, identity/order, route/query/hash semantics, and exact disclaimers are unchanged. Human-readable instants use the shared `Asia/Seoul` KST presentation in both locales while semantic `datetime` retains the source instant. No application adapter, API model, schema, fixture, manifest, OpenAPI, or Flyway contract is localized. |
| P2-L10N07 | Route and state parity | Every supported route, loading/error/empty boundary, noindex unsupported request, link target, filter value, and current-navigation state remains available in both locales. The common root not-found boundary is Korean-default/English-selectable and mode-neutral with exact `/` and `/calls` links. A translated heading or accessible label cannot become a separate data state or conceal `DEMO`, unavailable/deferred, null, or incomplete semantics. |
| P2-L10N08 | Evidence-first visual system | Shared tokens and layouts use the locked white editorial treatment, thin rules, near-square controls, compact mono evidence, tabular numerics, restrained semantic colors, and whitespace. Muted text is exactly `#70706c`; map positive/negative metric text uses the semantic positive/negative tokens, and neutral/map/treemap colored surfaces explicitly restore dark text. Color is never the only carrier of state; existing evidence hierarchy and local dense-table/treemap containment remain intact. |
| P2-L10N09 | Source and backend isolation | Locale/config/message/action/provider components do not import raw fixture JSON, canonical adapters, outcome calculators, providers, API code, or network transports. Canonical schema/fixture/manifest/OpenAPI/Flyway and Spring production sets remain unchanged by this slice. |
| P2-L10N10 | Deterministic replay | The same route, canonical evidence, and cookie yield the same locale and evidence order in raw SSR and hydrated navigation. Server locale logic has no `Clock`, randomness, host locale, timezone, or request-language dependency. |

## Korean-default bilingual product UI web behavior gate

| ID | Check | Expected result |
| --- | --- | --- |
| P2-L10NW01 | Semantic locale control | The global header exposes one labelled locale group with exact `KO` and `EN` controls, current selection through `aria-pressed`, pending/disabled semantics, and a visible focus indicator. Accessible option names are stable autonyms, exactly `한국어` and `English`, in both locales; the buttons expose exact `lang=ko` / `lang=en`, are at least 24 pixels high, and focus returns to the newly selected language after the server render. No hidden test-only label is used. |
| P2-L10NW02 | Korean default and English parity | Korean-default and English views cover the same information architecture and canonical rows. Navigation has the same eight targets/order/current item; only user-facing copy changes. Wordmark, canonical evidence, routes, and data ordering remain stable. |
| P2-L10NW03 | Complete state translation | Route headings, policy explanations, table labels, loading, recoverable error, valid empty, route-local unsupported/not-found copy, and the common root not-found boundary are Korean by default and English when selected. Canonical status/reason/data-mode tokens and mode-neutral noindex constraints remain exact in both locales. |
| P2-L10NW04 | Responsive editorial layout | At 1440, 1280, and 390 pixels, the white evidence-first layout preserves readable hierarchy, keyboard focus, locally contained navigation/tables/treemaps, and zero page overflow. Korean text may wrap without clipping, overlap, hidden evidence, or minimum-tile distortion. |
| P2-L10NW05 | Runtime integrity | Supported flows produce zero hydration mismatch, console warning/error, or page error. Switching and revisiting do not flash the wrong locale, reset filters, lose anchors, execute a screen, create a quote, or change canonical data. |

## Korean-default bilingual product UI required tests

- Unit tests cover exact locale parsing/defaulting, typed Korean/English
  catalogs, valid mutation, missing/unsupported/file mutation rejection, and
  exact development/production cookie attributes without relying on browser
  storage or a redirect.
- Layout/header tests cover raw `html[lang]`/metadata resolution, all eight
  localized navigation labels and targets, current state, exact `DEMO`
  preservation, locale-control autonym names/pressed/pending/focus-restoration
  behavior, exact per-button language attributes, and provider-required context
  so missing localization wiring fails closed.
- Common root not-found tests cover Korean and English copy, exact dashboard and
  call-ledger links, mode neutrality, and absence of invented DEMO/evidence.
- Raw-response tests request an existing SSR route with no cookie, an invalid
  cookie, `ko`, and `en`; assert language-specific server HTML and matching
  document language; then set the preference through the UI and prove revisit
  and direct navigation preserve it without changing path/query/hash.
- Existing route/component tests cover both presentation locales where copy is
  owned by the route and continue to assert exact canonical evidence values.
  Provider/domain tests remain locale-independent and unchanged.
- Responsive Playwright exercises Korean default, English toggle/revisit, direct
  navigation, keyboard focus restoration, per-language attributes and minimum
  24-pixel targets, localized unknown routes, translated loading/error/empty
  boundaries, local containment, page overflow, and zero console warnings/
  errors/page errors at 1440, 1280, and 390 pixels.
- Repository CI locks the exact locale/cookie/SSR source boundary, append-safe
  discovery of production localization files and localization tests, forbidden
  inference/storage/translation transports, canonical-source isolation, the
  visual-system invariants, and the absence of schema/fixture/manifest/OpenAPI/
  API/Flyway expansion without weakening existing contract gates.

## Korean-default bilingual product UI deferred work

Additional locales, user-account preferences, translated canonical source
documents, machine translation, locale-prefixed routing, localized API payloads,
and provider-supplied translations are not introduced. Live/current provider
data remains P5-owned, deterministic leaderboard metrics remain P3-owned, and
actual historical screening remains P8-owned. This presentation slice cannot
turn unavailable, fixture, null, incomplete, or model-only evidence into an
observed fact.

## Coherent call-detail audit API boundary

- This slice connects the existing `/calls/[id]` product route to the existing
  Spring read API without adding or changing an API, OpenAPI, schema, fixture,
  manifest, Flyway, persistence, provider-ingestion, or mutation contract.
- `CALL_AUDIT_PROVIDER` accepts only exact `fixture` or `api`; missing input
  selects the deterministic fixture implementation. The checked-in local
  environment selects `api` so the documented two-process stack exercises the
  real transport. Unsupported input fails closed. Provider selection never
  changes during one request and no API failure falls back to fixtures.
- `CallAuditProvider` returns one closed detail audit containing exactly the
  canonical call detail, a required closed call-context object whose
  `macroSnapshot` and `eventContext` members are independently nullable, and the
  ordered revision lineage. A top-level null context is malformed, not known
  empty. The route obtains all three surfaces from this one provider. It never combines
  a legacy fixture `CallsProvider` result with an API revision result.
- API mode uses only the private server-side `API_BASE_URL`. It first requests
  exact `GET /v1/calls/{encodedCallId}` to establish existence, then requests
  exact `GET /v1/calls/{encodedCallId}/context` and
  `GET /v1/calls/{encodedCallId}/revisions` from the same normalized origin.
  Every request accepts JSON and bypasses caches. It sends no body, mutation,
  browser cookie, authorization value, locale, or user-controlled forwarding
  header. `API_BASE_URL` is never a `NEXT_PUBLIC_*` setting or client fetch.
- An exact detail 404 is the only transport result mapped to product not-found.
  Missing/invalid configuration, network failure, redirect, any other non-2xx
  status, non-JSON content, invalid JSON, wrong or extra fields, invalid values,
  or a context/revision failure rejects the complete audit. Those states never
  become `[]`, null context, `NA`, a partial page, or fixture evidence.
- Runtime adaptation is closed at every object boundary. It preserves canonical
  enums, IDs, names, source text, decimal JSON values, UTC instants, order, and
  explicit nulls without filling or translating data. The detail's own source
  document/reference joins, snapshot identity, and context observation/source
  identifiers remain independently valid where the response carries both sides
  of a documented join. Every provenance ID is preserved but equality is not
  inferred between a call and its source records, a macro snapshot and its
  observations, or separate canonical document families.
- JSON numeric adaptation retains the existing web number boundary: every
  accepted value is finite, targets and non-null snapshot asset price are
  positive, and macro observation magnitude is strictly below `1e26`. The web
  adapter does not claim to recover BigDecimal lexical scale after
  `Response.json()` has produced a JavaScript number; exact stored decimal
  replay remains owned by the canonical API/persistence contract.
- This P2 transport accepts exactly `DEMO` throughout the aggregate. A
  `REALTIME`, `DELAYED`, or `EOD` detail, nested source/snapshot/context record,
  observation, or revision is rejected even when internally consistent. HTTP
  delivery does not turn the fixture-backed Spring API into a licensed or live
  provider publication boundary.
- Every revision belongs to the requested original call, has the same data mode,
  and preserves `original call eventTime <= revision eventTime <= processingTime
  <= capturedAt`. Sequence numbers are exactly contiguous from one, the root has
  a null predecessor, every later event names the immediately previous revision,
  revision event time alone is nondecreasing, and a cancellation terminates the
  lineage. Cross-row processing/capture monotonicity is deliberately not added
  because it is not part of the canonical P1 contract.
- A correction has the complete six-field replacement terms object; a
  cancellation has JSON null there. Nullable ratings, targets, currency, and
  target date stay null, not omitted or inferred. Revision reason,
  `sourceReferenceId`, and `provenanceId` remain the raw revision evidence; they
  are not required to equal the original call's source/provenance and are not
  promoted into an unverified source document.
- Revision `eventTime`, `processingTime`, and `capturedAt` render as their raw
  canonical ISO strings so zero-to-six fractional digits are not lost through a
  human date formatter. Corrected numeric targets render as raw JavaScript
  number text with the independently supplied currency in its separate field;
  they are not passed through money/percent rounding or used to calculate a
  delta. This display rule does not claim recovery of JSON lexical trailing
  zeros.
- The page presents the original event and append-only revision events as
  separate evidence. It does not overwrite or relabel the original call,
  project an effective status/direction/target, select a scoring basis, infer a
  cancellation outcome, or calculate return, alpha, target hit, accuracy, rank,
  confidence, or recommendation. Visible Korean and English copy explicitly
  says that the base `ACTIVE` value is the immutable original-event status, not
  a current or effective stance, including when the lineage ends in cancellation.

## Coherent call-detail audit API contract gate

| ID | Check | Expected result |
| --- | --- | --- |
| P2-CA01 | Whole-audit provider | One `CallAuditProvider` owns detail, context, and revisions for a request. API and fixture are explicit whole-aggregate modes; the page has no second calls-provider read and no mixed-source fallback. |
| P2-CA02 | Private exact transport | API mode uses only private `API_BASE_URL`, an encoded opaque call ID, the exact existing detail/context/revision GET paths, JSON acceptance, and no-store caching. It performs no client fetch, mutation, request-body, credential forwarding, public API-base setting, retry, or alternate-origin request. |
| P2-CA03 | Existence and failure semantics | Detail is requested first. Its exact 404 maps to route not-found; every other detail failure and every context/revision failure aborts the complete audit. Valid known-empty context or `[]` lineage is distinct from failure. |
| P2-CA04 | Closed runtime shape and raw display | Detail, every nested source/snapshot record, context/observations, every 16-field revision, and every non-null six-field corrected-terms object reject missing, extra, or mistyped fields. JSON null is preserved, no missing numeric is coerced to zero, targets/non-null asset price are positive and finite, and macro magnitude is strictly below `1e26`. Revision ISO instants and corrected target numbers render without formatter rounding; the JS adapter does not claim lexical decimal-scale recovery. |
| P2-CA05 | Cross-surface joins and phase mode | Detail call/source/snapshot, context records, and revisions retain their own canonical joins and all belong to the requested call where applicable. Every aggregate record is exactly `DEMO`; `REALTIME`, `DELAYED`, and `EOD` fail closed. Separate document-family provenance/source IDs remain independent rather than being forced equal. |
| P2-CA06 | Compatible chronology | Call and nested records preserve their canonical point-in-time bounds. Revisions preserve original-event lower bound, their own event/processing/capture order, and nondecreasing event time only; the web adapter adds no unsupported cross-row processing/capture rule. |
| P2-CA07 | Exact append-only lineage | Revision IDs and `(provider, providerEventId)` identity pairs are unique, sequence is `1..N`, parent links name the immediately preceding event, correction/cancellation payload rules are exact, and cancellation is terminal. Reuse of one provider-event string by a different provider is not rejected. Source IDs and raw reasons remain revision evidence. |
| P2-CA08 | No lifecycle or scoring projection | Rendering explicitly identifies base `ACTIVE` as immutable original-event status rather than current/effective stance, even beside terminal cancellation. It does not mutate the original call or infer effective fields, outcome eligibility, result, metric, confidence, rank, or advice. Existing model-only outcomes remain disconnected. |
| P2-CA09 | Backend and canonical isolation | The five existing OpenAPI paths, 14 schema files, 13 fixture files, manifest membership, V1–V5 Flyway set, and Spring read contracts remain unchanged. This slice adds only a web consumer of the existing reads. |

## Coherent call-detail audit web behavior gate

| ID | Check | Expected result |
| --- | --- | --- |
| P2-CAW01 | Populated audit | `/calls/demo-call-002` retains original `ACTIVE`, `BULLISH`, and `$235.00` detail evidence and adds exactly the ordered `demo-call-revision-001` correction followed by terminal `demo-call-revision-002` cancellation, with canonical IDs, types, raw ISO times, reason, source reference, data mode, provenance, and nullable terms intact. Correction target `232` remains separate revision evidence. Adjacent copy explicitly says base `ACTIVE` is original-event status, not a current/effective stance. |
| P2-CAW02 | Honest empty lineage | A known call with a valid API `[]` renders an explicit no-recorded-revisions state. It does not display zero revisions as completeness, unchanged status, provider health, or proof that no later correction exists. |
| P2-CAW03 | Error and not-found distinction | Exact detail 404 uses the localized call not-found boundary. Configuration, transport, response, context, or revision validation failures use the recoverable localized route error and reveal no partial canonical record or fixture fallback. Korean and English state copy stays provider-neutral: it must not mislabel an API, configuration, transport, or validation failure as a fixture-read failure. |
| P2-CAW04 | Canonical versus presentation language | Korean-default and English views translate only labels and explanations. Revision type, reason, IDs, source/provenance, timestamps, numeric inputs, null/`NA`, and lineage order remain canonical in both locales. |
| P2-CAW05 | Evidence-first layout | Original and revision events remain visibly distinct, use semantic headings/list or table structures, and expose state without color alone. At 1440, 1280, and 390 pixels dense audit evidence is locally contained with no page overflow or hidden fields. |
| P2-CAW06 | Server-only runtime | Browser navigation causes no request to `API_BASE_URL`; Next performs the transport server-side. Populated, empty, not-found, and recoverable-error flows produce no hydration, console warning/error, or page error. |
| P2-CAW07 | Regression boundary | Calls list/dashboard and all other routes retain their current provider contracts. Existing detail/source/snapshot/context facts, locale persistence, navigation, schemas, backend responses, and deferred P3/P5/P8 boundaries are not weakened. |

## Coherent call-detail audit API required tests

- Adapter tests cover exact detail/context/revision records, all nullable fields,
  populated and known-empty contexts, populated and empty lineages, opaque ID
  encoding, closed keys, enum/date/instant/numeric validation, nested joins,
  exact all-surface `DEMO` enforcement (including otherwise-consistent
  `REALTIME`/`DELAYED`/`EOD` rejection), and the compatible chronology/lineage
  rules. Context goldens include inclusive macro-vintage bounds, start/end
  ordering, future-event lower bounds with earnings exempt, positive snapshot
  asset price, strict macro `1e26` magnitude boundaries, and distinct preserved
  provenance IDs that must not be forced equal.
- Transport tests prove detail-first ordering, exact three paths from one private
  origin, JSON/no-store options, 404-only not-found mapping, and fail-closed
  behavior for invalid configuration, redirects, network errors, every non-2xx
  stage, content-type/JSON/shape failures, and dependent endpoint divergence.
- Fixture-provider tests prove it returns the same whole-audit shape without an
  API import. Factory tests cover exact `fixture`/`api`, deterministic default,
  unsupported input, and the absence of runtime fallback.
- Page/component tests cover Korean and English populated correction and
  cancellation, known-empty lineage, not-found/error separation, canonical
  values, nullable terms, original-event preservation, and absence of effective
  lifecycle, outcome, ranking, or advice claims. Both locales assert the explicit
  base-`ACTIVE` original-event/not-current-or-effective disclosure.
- A synthetic page-provider test renders revision instants containing six
  fractional digits and a high-precision corrected target. It asserts the exact
  ISO strings and raw numeric text, the separate currency, and unchanged base
  status/direction/target, proving the UI does not silently format away evidence.
- Responsive Playwright covers populated and empty audit flows, keyboard focus,
  local containment, page overflow, external-browser request isolation, and
  zero runtime errors at 1440, 1280, and 390 pixels.
- A dedicated integration job boots PostgreSQL 17, the packaged Spring API, and
  Next in exact API mode. It exercises the real server-side detail/context/
  revision path for populated and known-empty calls and proves that the browser
  never calls the API origin directly. Route interception or a web-only mock is
  not accepted as the cross-stack proof.
- Repository CI locks the exact server-only provider structure, adapter field
  sets and transport markers, page/factory source isolation, unchanged canonical
  and backend file/path sets, and the required unit/E2E/integration test sources.

## Coherent call-detail audit API deferred work

This slice does not connect a paid or production analyst provider. The Spring
API still serves canonical DEMO fixture-backed persistence. P5 retains licensed
provider ingestion, entitlements, freshness/health, retry policy, and rights
review. P3 retains effective lifecycle/basis rules and deterministic outcomes;
P8 retains historical materialization and screening. Authentication, user-
specific visibility, cross-endpoint snapshot tokens, streaming, polling,
revision writes, source-document expansion, and list/dashboard API migration
require separate reviewed contracts.

## Coherent analyst-call list API boundary

Status: complete for this private `/calls` consumer slice. Broader P2 work,
P3 lifecycle/scoring, P5 licensed provider publication, and P8 historical
materialization remain open.

- This slice connects only the existing server-rendered `GET /calls` product
  route to the existing Spring `GET /v1/calls` read. It adds no API path,
  OpenAPI field, canonical schema, fixture, manifest member, Flyway migration,
  Spring class, persistence query, mutation, polling, or browser-side fetch.
- The list and detail routes share the exact `CALL_AUDIT_PROVIDER=fixture|api`
  selector and private `API_BASE_URL`. This prevents a documented local stack
  from silently using API detail with a fixture list or vice versa. Missing
  selector input remains deterministic fixture mode for isolated tests; the
  checked-in local example explicitly selects API mode. Unsupported input and
  every API failure fail closed without fixture fallback.
- `CallListProvider` owns one page-scoped read. API mode makes exactly one
  `GET /v1/calls` request with `Accept: application/json`, `cache: no-store`,
  redirect rejection, no credentials/body/user headers, and the exact supported
  scalar query. It always applies `dataMode=DEMO` as the explicit P2 phase
  boundary. The browser never receives or calls `API_BASE_URL`.
- The existing list API returns only the closed `{items,page}` response. It does
  not expose fixture `generatedAt`, dataset provenance, disclaimer, or complete
  filter facets. API mode therefore returns exact dataset-evidence state
  `NOT_EXPOSED / LIST_API_HAS_NO_DATASET_METADATA` with null dataset as-of,
  source, and disclaimer. The UI renders those values as `NA` and explains the
  boundary; it never imports fixture metadata or derives dataset coverage from
  one returned page.
- Returned-page evidence is separate and carries exact scope
  `RETURNED_PAGE`. It may contain only the maximum returned call's `capturedAt`
  and the sorted distinct returned call `provenanceId` values. An empty page
  yields null and an empty list. Neither is
  relabelled as dataset as-of, API response time, market freshness, full
  coverage, provider health, or publication status.
- Fixture mode may expose its exact existing dataset as-of/source/disclaimer in
  an `AVAILABLE` dataset-evidence variant, but it uses the same page contract,
  page-only evidence calculation, DEMO guard, filter semantics, and UI. API mode
  never imports or falls back to that variant.
- Because the API has no facet endpoint, asset, institution, and analyst filters
  are exact opaque-ID text inputs. Ticker remains the documented case-
  insensitive ticker input; direction and original-event status use closed
  canonical vocabularies; mode is fixed to DEMO. The UI does not present
  current-page values as a complete suggestion catalog.
- Browser date inputs accept only real Gregorian `YYYY-MM-DD` values and mean
  Korean civil dates. `from` becomes inclusive `00:00:00.000 KST`; the through
  date becomes the next Korean day's exclusive `00:00:00.000 KST`; both are
  converted to canonical UTC instants before the API read. The page does not claim to mirror every wider
  `Instant.parse` spelling accepted directly by Spring. The typed server
  provider nevertheless preserves Spring-compatible offset instants with up to
  nanosecond precision and rejects offsets outside Java's `ZoneOffset` range;
  the response adapter remains independently locked to canonical UTC `Z`
  instants at no more than microsecond precision. Recognized duplicate
  scalar inputs, invalid dates/identifiers/tickers/enums/ranges/pages/sizes, and
  a non-DEMO mode are rejected rather than dropped, normalized, or clamped.
- API item adaptation is closed at the root, item, call, identity, source,
  page, and sort boundaries. It preserves explicit nulls and raw order, checks
  canonical joins and per-record chronology, requires call/document/reference
  DEMO independently, and never folds revisions into the immutable original
  status. A list 404, redirect, network error, non-200, non-JSON, malformed JSON,
  wrong/extra field, unsafe integer, invalid page invariant, duplicate identity,
  or ordering violation is an error, never an empty result or partial page.

## Coherent analyst-call list API contract gate

| ID | Check | Expected result |
| --- | --- | --- |
| P2-CL01 | One page provider | `/calls` performs one `CallListProvider.list` read. API and fixture are explicit page-wide modes selected by the same exact selector as detail; the route imports neither transport nor raw fixture metadata. |
| P2-CL02 | Exact private transport | API mode uses only private `API_BASE_URL` and one exact GET list request with JSON acceptance, no-store caching, redirect rejection, fixed DEMO phase filter, and no browser credentials/body/retry/fallback. |
| P2-CL03 | Query fidelity | Opaque IDs are exact and case-sensitive, ticker follows the API's case-insensitive contract, enums retain exact case, filters combine with AND, `from` is inclusive, `to` is exclusive, page is zero-based, size is 1..100, and defaults are page 0/size 25/eventTime/desc. Every returned item must satisfy every effective AND filter. Invalid or duplicate recognized inputs are not silently normalized, discarded, defaulted, or clamped. |
| P2-CL04 | Closed response | Root is exactly `{items,page}`; each item has exactly call/institution/analyst/asset/source and no snapshot; all nested required keys and explicit nulls are preserved. Unknown, missing, extra, mistyped, or invalid values fail the complete page. |
| P2-CL05 | Canonical joins and DEMO guard | Every call joins its institution, nullable analyst, asset, source reference, and source document exactly. Call, source document, and source reference are independently DEMO; later real/delayed/EOD data cannot enter this P2 page through internally consistent payloads. |
| P2-CL06 | Page invariants | Response number/size/sort/order echo the request; safe integers, totals, total pages, first/last, and item cardinality agree. Empty matches and out-of-range pages remain distinct valid 200 pages, including the echoed out-of-range number and `last=true`. |
| P2-CL07 | Deterministic order | The adapter preserves server order and verifies the selected primary sort direction with `callId ASC` for equal primary values. It never resorts by translated text, target, status, revision state, or current time. |
| P2-CL08 | Honest evidence states | API dataset evidence has exact availability NOT_EXPOSED with null metadata. Fixture dataset evidence has exact availability AVAILABLE, and its dataset `asOf` cannot precede any returned call `capturedAt`. Returned-page evidence has exact scope RETURNED_PAGE and derives capture/provenance only from returned calls; empty page evidence remains null/empty. This fixture coherence check is not promoted to API metadata, completeness, or coverage. |
| P2-CL09 | Empty versus failure | A valid 200 `items:[]` renders the localized response-bounded empty state. Configuration, network, redirect, HTTP, media, JSON, shape, join, mode, page, or ordering failure renders the recoverable route error and exposes no partial rows or fixture fallback. |
| P2-CL10 | Backend and canonical isolation | Existing five OpenAPI paths, list response shape/query semantics, Spring sources, V1-V5 migrations, 14 schemas, 13 fixtures, and manifest membership remain unchanged. This is a private web consumer slice, not a new backend or provider-publication contract. |

## Coherent analyst-call list web behavior gate

| ID | Check | Expected result |
| --- | --- | --- |
| P2-CLW01 | Korean-default and English ledger | Both locales render the same ordered canonical rows, IDs, enums, numbers, nulls, source titles, links, totals, and page state. Human-readable instants use identical explicit KST strings backed by unchanged UTC API values and semantic `datetime`; only labels, explanations, and accessible names change. |
| P2-CLW02 | Filter form truth | Identity fields are labelled exact-ID text inputs, ticker remains text, direction/status remain canonical selects, and data mode is fixed DEMO. No full-universe facet or current/live filter claim is shown. |
| P2-CLW03 | Dataset metadata boundary | API mode visibly renders dataset as-of/source as `NA` and explains that the existing list API does not expose dataset metadata. Page-only capture/provenance is labelled as returned-page evidence, never dataset freshness or coverage. |
| P2-CLW04 | Pagination and URLs | Provider page 0 renders as human page 1. Previous/next URLs preserve only validated scalar filters and exact sorting/size values. Empty and out-of-range pages do not fabricate substitute rows. |
| P2-CLW05 | Server-only responsive runtime | At 1440, 1280, and 390 pixels populated, filtered, empty, and paginated states remain keyboard-operable and locally contained with no page overflow, hydration warning, console error, or page error. The browser makes no request to the API origin. |
| P2-CLW06 | Regression boundary | Call detail keeps its coherent detail/context/revision provider. Dashboard, S&P history, maps, and other routes retain their current providers; list migration does not turn HTTP-delivered DEMO fixtures into live, licensed, complete, or current evidence. |

## Coherent analyst-call list API verification

- Query tests cover every scalar, defaults, fixed DEMO injection, exact
  encoding/order, case-sensitive IDs, ticker case behavior, genuine leap-day and
  invalid-date conversion, exclusive through-date, duplicate recognized values,
  optional-filter empty-string omission, whitespace/malformed nonempty values,
  required-control present-empty failure, page/size bounds, enum case, and
  from/to range failure. Direct typed-provider tests separately cover valid
  offset/nanosecond bounds and invalid Java offsets without weakening canonical
  response-instant validation.
- Adapter tests cover closed positive items with every nullable branch, all
  canonical joins, one-at-a-time nested DEMO mutations, chronology/numeric/URL/
  date/enum failures, duplicate call/provider-event identities, all three sort
  fields and both orders, equal-primary `callId ASC`, no silent reordering, and
  one-at-a-time response-row mismatches for every effective ID/ticker/direction/
  status/DEMO/from/to AND filter.
- Page tests cover valid empty, out-of-range echo, unsafe integers, totals/page/
  cardinality/first/last divergence, API NOT_EXPOSED versus fixture AVAILABLE,
  AVAILABLE `asOf` versus latest returned capture, and exact empty returned-page
  evidence.
- Transport/factory tests cover exact fixture/API/default/unsupported selection,
  one private request, normalized base URL, every query field, no-store/JSON/
  redirect options, and network/400/404/500/media/JSON/shape failure without a
  fallback or second request.
- Korean/English page tests cover populated, filtered, empty, paginated, dataset-
  metadata-not-exposed, page-evidence, loading, and recoverable-error states with
  unchanged canonical finance evidence.
- Responsive Playwright exercises the list at 1440, 1280, and 390 pixels,
  keyboard focus, exact filter/pagination URLs, populated/empty containment,
  runtime errors, and browser-to-API request isolation.
- The existing PostgreSQL 17 -> packaged Spring -> Next integration job runs the
  list spec in exact API mode and proves the server made all five expected list
  GETs while the browser did not call port 8080. The existing detail/context/
  revision six-path proof remains intact.
- Repository CI locks the exact list provider/adapter/factory/page source graph,
  dataset-evidence union, test matrix, shared selector, one-fetch transport, and
  unchanged canonical/backend sets.

Observed closure on the final tree:

- ESLint and non-incremental TypeScript passed. Focused call-list Vitest passed
  162/162 across eight files; the full suite passed 515/515 across 41 files; and
  the Next 16.2.11 production build completed 12/12 page-data generation for the
  existing 11 dynamic routes.
- Targeted call-list Playwright passed 3/3 at 1440, 1280, and 390 pixels, six
  related legacy checks passed 6/6, and the retry-free full suite passed 69/69
  across the same widths.
- Maven verification passed 223/223 with no failures, errors, or skips,
  including PostgreSQL 17.10 migration coverage at 4/4 with no skips. Compose
  configuration validation passed.
- The PostgreSQL -> packaged Spring -> Next API-mode run passed 2/2 browser
  tests and exact full-line Tomcat evidence passed 11/11: five list queries and
  six detail/context/revision reads. Queryless `%q` values are the observed `-`
  placeholder; matching is exact line membership, not a path substring.
- All 18 embedded repository Python blocks passed syntax and execution against
  14 schemas and 32 canonical records. SnakeYAML 2.5, `git diff --check`, the
  protected source sets, and generated-file cleanup passed. Independent review
  reported zero blockers, zero HIGH findings, and zero known false positives.

## Coherent analyst-call list API deferred work

Dataset metadata/facet endpoints, cross-request snapshot tokens, dashboard/API
migration, commercial provider ingestion, entitlements, licensing, rights,
freshness/health, polling/streaming, saved filters, exports, user preferences,
and current/effective lifecycle projection remain separate reviewed work. This
slice is still synthetic DEMO evidence delivered through Spring/PostgreSQL, not
a live or production market-data connection.

## Coherent call-outcome audit API boundary

Status: complete for this vertical slice; the broader P2 phase remains open.
The results below close only the synthetic DEMO audit-consumer boundary.

- This slice consumes the existing `GET /v1/calls/{id}/outcomes` subresource
  without adding or changing an OpenAPI path, canonical schema, fixture,
  manifest member, Flyway migration, Spring class, persistence query, mutation,
  calculation, scheduler, or provider-ingestion boundary.
- The existing page-scoped `CallAuditProvider` becomes one exact
  `{detail, context, revisions, outcomes}` aggregate. The page may not obtain an
  outcome from a second provider, a raw fixture import, another origin, or a
  fallback. Fixture and API remain explicit whole-aggregate modes selected by
  the same exact `CALL_AUDIT_PROVIDER` value used by the call list and detail.
- API mode establishes existence with the exact encoded detail GET first. Only
  after a non-null detail does it request context, revisions, and outcomes from
  the same normalized private `API_BASE_URL`. An exact detail 404 remains the
  only not-found result. Every dependent 404 or other transport/validation
  failure rejects the complete audit rather than returning a partial page,
  `[]`, `NA`, or fixture evidence.
- A known call with an exact outcome `[]` is valid recorded-empty evidence. It
  does not prove that a horizon was evaluated, that no future outcome will be
  appended, or that the provider/data set is complete.
- The web adapter accepts the closed 31-field canonical outcome shape and
  preserves source array order, explicit nulls, raw canonical tokens, hashes,
  fingerprints, IDs, and UTC instants. It never selects one record as current,
  effective, latest, or authoritative and never folds an append-only lineage
  into the immutable base call.
- The fixture envelope groups records by call and sorts a copied group into the
  existing server order before adaptation because its canonical document order
  is not the API order. It never mutates the fixture. The shared adapter only
  validates and preserves the array it receives; API payloads are never sorted.
- This P2 publication boundary remains exact DEMO and null-only. The outcomes
  API does not expose methodology status. Returned outcomes are accepted only
  as `PENDING/HORIZON_NOT_REACHED` or
  `INCOMPLETE/HORIZON_DATA_MISSING`, with `dataComplete=false` and all ten
  metric/result fields JSON null. A coherent `CALCULATED` or `EXCLUDED` record,
  a non-null metric/result, or any non-DEMO mode requires its separately
  reviewed P3/P5 boundary and therefore fails closed here.
- A nullable `basisRevisionId` remains recorded outcome evidence, not a basis
  selected by the UI. If present, it must resolve to a same-call correction
  available by outcome processing time. `cancellationRevisionId` is exact JSON
  null in this boundary. Cancellation-reference relationship semantics remain
  deferred with `EXCLUDED/CALL_CANCELLED`; the UI never infers either from a
  terminal revision.

## Coherent call-outcome audit API contract gate

| ID | Check | Expected result |
| --- | --- | --- |
| P2-OA01 | One coherent aggregate | `CallAuditProvider` returns exactly detail, required closed context, ordered revisions, and ordered outcomes from one selected mode. The page has one provider read and no mixed API/fixture source. |
| P2-OA02 | Private exact transport | API mode uses the encoded opaque call ID and exact existing detail, context, revisions, and outcomes GET paths on one private normalized `API_BASE_URL`, with JSON acceptance, no-store caching, redirect rejection, no body/credentials/browser fetch, no retry, and no alternate origin. |
| P2-OA03 | Existence and failure semantics | Detail is requested first; its exact 404 maps to route not-found. Every other detail failure and every context/revision/outcome failure aborts the whole aggregate. A known call plus exact outcome `[]` remains distinct from failure. |
| P2-OA04 | Closed outcome shape | Every outcome has exactly `outcomeId`, `schemaVersion`, `callId`, `horizon`, `basisRevisionId`, `cancellationRevisionId`, `snapshotId`, `methodologyId`, `methodologyVersion`, `methodologyDefinitionHash`, `inputFingerprint`, `sequenceNumber`, `supersedesOutcomeId`, `evaluationStatus`, `reasonCode`, `eventTime`, `processingTime`, `assetReturn`, `benchmarkReturn`, `sectorReturn`, `alpha`, `sectorAlpha`, `mfe`, `mae`, `targetHit`, `directionalWin`, `targetError`, `dataComplete`, `dataMode`, `capturedAt`, and `provenanceId`; missing, extra, omitted-null, or mistyped fields fail closed. |
| P2-OA05 | Canonical scalar boundary | Schema version is exact `1.0.0`; opaque IDs, methodology versions, lower-case SHA-256 hashes/fingerprints, horizons, statuses, reasons, `dataComplete`, and UTC `Z` instants with zero-to-six fractional digits retain their closed canonical rules. This P2 consumer accepts no metric number or result Boolean, makes no JavaScript-number precision claim, and defers BigDecimal lexical/scale support until calculated outcomes have a reviewed P3 transport boundary. |
| P2-OA06 | Exact P2 phase guard | Every returned outcome is exact `DEMO`, is either `PENDING/HORIZON_NOT_REACHED` or `INCOMPLETE/HORIZON_DATA_MISSING`, has `dataComplete=false`, and has exact JSON null for asset, benchmark, sector, alpha, sector alpha, MFE, MAE, target-hit, directional-win, and target-error values. `CALCULATED`, `EXCLUDED`, `REALTIME`, `DELAYED`, `EOD`, or any number/Boolean/other non-null metric/result is rejected even when otherwise schema-coherent. |
| P2-OA07 | Identity and natural-key uniqueness | Every outcome ID is unique. The natural input identity `(callId, basisRevisionId, horizon, methodologyId, methodologyVersion, inputFingerprint)` is unique, while the same fingerprint may validly appear under a different methodology version. One `(methodologyId, methodologyVersion)` never carries conflicting definition hashes. |
| P2-OA08 | Append-only lineage | A lineage is scoped exactly by `(callId, basisRevisionId, horizon, methodologyId, methodologyVersion)`. It starts at sequence one with null supersession, remains contiguous, and every later record names the immediately preceding outcome. Event, processing, and capture times are independently nondecreasing within one lineage; unrelated lineages gain no invented cross-time constraint. |
| P2-OA09 | Point-in-time joins | Every outcome belongs to the requested/base call and preserves `base eventTime <= outcome eventTime <= processingTime <= capturedAt`; base processing/capture are no later than outcome processing. A non-null snapshot matches the detail snapshot and was processed/captured by outcome processing. A non-null basis is a same-call correction whose event/processing/capture was available by outcome processing. Cancellation reference joins are not interpreted because this P2 boundary requires that field to be exact null. |
| P2-OA10 | Exact response order | The adapter preserves and validates the current API order without sorting: horizon `D1`, `W1`, `M1`, `M3`, `M6`, `Y1`, followed by raw methodology ID, methodology version, sequence number, and outcome ID ascending. Methodology versions are not reinterpreted as semantic versions. Lineage validation remains scoped correctly if distinct basis lineages interleave. |
| P2-OA11 | Provenance independence | Outcome provenance is preserved as raw evidence and need not equal call, snapshot, context, revision, or source-document provenance. The page does not fabricate a methodology source document or claim that the outcome endpoint exposes methodology activation status. |
| P2-OA12 | No calculation or lifecycle projection | The page renders the immutable base event, revisions, and each outcome record separately. It never infers an excluded outcome from cancellation, chooses an effective basis, collapses to a latest result, or publishes return, target hit, win/loss, score, accuracy, rank, confidence, recommendation, or advice. |
| P2-OA13 | Backend and canonical isolation | The five OpenAPI paths, 14 schema files, 13 fixture files, V1-V5 migrations, Spring production/read behavior, and P1/P3 domain source remain unchanged. This slice is a web consumer of the existing read only. |

## Coherent call-outcome audit web behavior gate

| ID | Check | Expected result |
| --- | --- | --- |
| P2-OAW01 | Populated audit | `/calls/demo-call-001` renders all four existing outcome records in the exact API order, including the two-record D1 v1 lineage, the separate D1 v2 root, and the M1 root. Every canonical field, ID, nullable reference, methodology identity/hash, fingerprint, status/reason, KST-presented instant, data flag/mode, provenance, and null metric remains visible; exact source instants remain machine-readable in `datetime`. |
| P2-OAW02 | Honest empty audit | `/calls/demo-call-002` renders an explicit no-recorded-outcomes state while retaining its correction/cancellation lineage. It does not infer `EXCLUDED`, unchanged eligibility, provider completeness, or absence of a future outcome. |
| P2-OAW03 | Original and revision preservation | Adding outcome rows does not change the immutable base `ACTIVE`, direction, target, source/snapshot/context facts, or append-only revision values and order. Outcome status never becomes call status or an effective stance. |
| P2-OAW04 | Canonical versus presentation language | Korean-default and English views translate only labels and explanatory copy. Horizon/status/reason tokens, IDs, hashes, fingerprints, booleans, null/`NA`, data mode, provenance, and response order remain canonical. Both locales present each instant through the same explicit KST formatter while preserving its raw UTC value in `datetime`. Copy does not call a methodology active/inactive because this endpoint does not expose that state. |
| P2-OAW05 | Error and not-found truth | Exact detail 404 retains the localized not-found boundary. Configuration, transport, media, JSON, or validation failure on any of four resources uses the provider-neutral recoverable error boundary and reveals no partial audit, recorded-empty substitute, or fallback. |
| P2-OAW06 | Accessibility and responsive containment | Outcome evidence uses semantic headings and table/list/description structures, has a sequential keyboard path and visible focus, and keeps long hashes/fingerprints and dense fields locally contained without truncating canonical text or creating page overflow at 1440, 1280, and 390 pixels. |
| P2-OAW07 | Server-only runtime | Browser navigation produces no request to `API_BASE_URL`; Next performs all four reads server-side. Populated and empty outcome states produce no hydration, console warning/error, or page error. |

## Coherent call-outcome audit required tests

- Adapter tests must cover the exact 31-key row, every permitted nullable branch,
  all six horizons, identity/version/hash/instant bounds, missing/extra/mistyped
  fields, and explicit preservation of nulls and source order. Every metric
  number and result Boolean is a negative phase test; numeric precision/scale
  support is not claimed or tested by this consumer.
- One-at-a-time phase mutations must reject every non-DEMO mode, coherent
  calculated and excluded records, each wrong status/reason/completeness pair,
  and each of the ten non-null metric/result fields.
- Cross-record tests must cover requested-call mismatch, duplicate outcome and
  natural-input identities, methodology hash conflicts, same fingerprint under
  a different methodology version, lineage root/gap/predecessor/sequence and
  each nondecreasing timestamp, unrelated-lineage independence, exact API order,
  call/snapshot/correction joins and timing, exact-null cancellation reference,
  and distinct valid provenance across document families.
- Transport tests must prove exact detail-first four-path behavior, opaque-ID
  encoding, one private origin, JSON/no-store/redirect options, known-empty
  `[]`, and fail-whole behavior for invalid configuration, network, every
  dependent non-200 including 404, media type, malformed JSON, and response
  shape without retry or fallback.
- Fixture tests must prove the same aggregate shape from the existing outcome
  envelope, methodology/hash and call/revision/snapshot joins, populated and
  known-empty calls, invalid envelope/reference failure, and no API dependency.
  Factory tests retain the exact shared fixture/API selector and no fallback.
- Korean/English page tests must lock the four-row populated audit, explicit
  empty state, every raw evidence class, base/revision preservation, microsecond
  display, and absence of current/effective/latest/score/win/rank/advice or
  inferred cancellation-outcome claims.
- Responsive Playwright must exercise populated call 001 and empty call 002 at
  1440, 1280, and 390 pixels, sequential keyboard reachability, local overflow
  containment, zero browser-to-API requests, and zero runtime errors.
- The existing PostgreSQL 17 -> packaged Spring -> Next API-mode job must run
  the outcome E2E and prove exact full-line Tomcat access for both outcomes
  paths in addition to the existing five list and six detail/context/revision
  reads. With `%m %U%q %s`, queryless rows retain Tomcat's observed `-` value;
  the required set therefore contains 13 exact HTTP-200 lines.
- Repository CI must lock the exact aggregate/provider graph, 31-field type,
  phase and lineage markers, fixture/API reverse isolation, page/test/E2E
  sources, and the unchanged protected backend/canonical digest.

## Coherent call-outcome audit observed verification

- Web ESLint and TypeScript both passed. Vitest passed all 42 files and 569
  tests. The Next production build passed, including 12/12 static-generation
  work items and 11 dynamic routes.
- Retry-free Playwright passed all 72 tests across 1440, 1280, and 390 pixels;
  the focused outcome-plus-revision matrix passed 6/6. It covered populated and
  known-empty evidence, sequential keyboard focus, long hash/fingerprint
  containment, and browser-to-API isolation.
- The real PostgreSQL 17 -> packaged Spring -> Next API-mode gate passed 3/3
  browser tests and matched all 13 exact Tomcat access-log lines: five list
  queries plus eight populated/known-empty detail, context, revision, and
  outcome reads.
- Maven verification passed 223/223 tests with zero failures, errors, or skips.
  PostgreSQL 17.10 migration coverage passed 4/4 Testcontainers integration
  tests with zero skips; Compose configuration also passed.
- All 18 embedded workflow Python blocks passed syntax and execution. Canonical
  validation covered 14 JSON Schemas and 32 fixture records; SnakeYAML parsing
  and `git diff --check` passed. Generated reports were removed and
  `next-env.d.ts` retained its production import.
- In-app browser QA confirmed Korean default SSR, desktop and 390-pixel
  containment without overflow or canonical-token truncation, the honest empty
  state, and zero console warnings or errors. Independent final review found
  zero blockers, zero high-severity issues, and zero known false-positive gates.

## Coherent call-outcome audit deferred work

Effective original/correction/cancellation basis selection, named-horizon
policy, calculated metrics, current/latest projection, scoring, aggregation,
confidence, ranking, and persistence remain P3 work. Dashboard/API migration,
dataset catalog metadata, and cross-request snapshot tokens require separate
contracts. Live/licensed providers, entitlements, rights, freshness/health,
retry/polling/streaming, and non-DEMO publication remain P5 work. This consumer
does not turn the four synthetic model records into observed performance.

## Exact SEC manifest-audit API-mode full-stack acceptance boundary

Status: implemented and observed PASS in the 2026-08-31 ADR-055 manual local
gate recorded in `IMPLEMENTATION_LOG.md`. Repository CI guards this contract but
does not execute the Docker/Chromium run.

ADR-055 extends the existing disposable ADR-045 production-stack command. It
does not change an OpenAPI route, Flyway migration, production repository,
provider adapter, manifest assembly rule, or public mutation boundary. Its only
write entry is an explicitly named JUnit acceptance harness outside the default
Surefire class patterns. The harness is invoked only with the exact opt-in
property and persists the shared synthetic ADR-053 parity evidence through
production repositories and the production manifest persistence service.

The seed refuses a non-isolated target both before context creation and through
effective JDBC metadata. Only loopback PostgreSQL, database and user
`wsr_full_stack_acceptance`, matching datasource/Flyway URLs and credentials,
a 32-lowercase-hex per-run password, empty root/segment/manifest repositories,
disabled SEC provider, disabled operator API, empty contact email, and the closed
`http://127.0.0.1:1` SEC base URL are accepted. The live provider is never
contacted, and ordinary Maven tests, verification, packaging, and application
startup do not discover the seed class.

The API-mode synthetic manifest remains data-mode evidence rather than a
transport inference. A server-only acceptance setting names exactly its
lowercase SHA-256 manifest identity. Only that matching API response receives
the existing DEMO badge and synthetic-not-actual-SEC disclosure. Other API
manifests are not called DEMO, LIVE, REALTIME, DELAYED, or EOD, and ADR-052's
response contract remains unchanged.

### ADR-055 contract gate

| ID | Check | Expected result |
| --- | --- | --- |
| P2-SFA01 | Explicit non-default seed | `SecManifestAuditAcceptanceSeedHarness` does not match default Surefire discovery and runs only when the command names the exact class and supplies `-Dwsr.sec-manifest-acceptance-seed=true`. No raw SQL, runtime endpoint, startup importer, scheduler, or operator command seeds evidence. |
| P2-SFA02 | Strict effective database guard | Before any write, both environment and effective Spring/JDBC state identify only `jdbc:postgresql://127.0.0.1:<1024..65535>/wsr_full_stack_acceptance`, user `wsr_full_stack_acceptance`, identical datasource/Flyway settings, and the per-run password. Existing root, segment, or manifest rows fail the seed. |
| P2-SFA03 | No external/provider authority | `SEC_PROVIDER_ENABLED=false`, `OPERATOR_API_ENABLED=false`, empty `SEC_CONTACT_EMAIL`, and `SEC_BASE_URL=http://127.0.0.1:1` are mandatory. No API key, SEC account, paid plan, domain, home-server access, OAuth credential, user/operator token, or monitored email is needed. |
| P2-SFA04 | Production persistence path | One shared synthetic fixture is appended and reloaded through `FilingCatalogCaptureRepository` and `HistoricalFilingSegmentCaptureRepository`, then persisted through `PersistFilingHistoryCollectionManifestService` and read through the audit query service. It has one root, two selected historical captures, one manifest, two descriptors, four accession groups, and six occurrences. |
| P2-SFA05 | Exact identity and PIT absence | The manifest is exact `cda6762d385d4e889294d0fec1f7a2a7b20c5157cf67c832b7d7f4857550a1cd`, selection hash `eadb0c3bf6efb9b3323be1342d0b17e63631b706f088b23fa78e784e1b547acd`, root capture `c9bfc935b27e059397531a4dda1a1a0222e98528c33e85b886c91ca6b74f2fa8`, and assembled time `2026-08-25T03:30:00.123456Z`. The assembly cutoff succeeds and one microsecond earlier remains sanitized 404. |
| P2-SFA06 | Explicit SEC API mode and DEMO truth | Build, production Next runtime, and browser children select `SEC_MANIFEST_AUDIT_PROVIDER=api`. The exact acceptance identity is separately pinned as synthetic and remains visibly DEMO; the browser never calls Spring directly, the API payload gains no invented mode, and no fixture fallback occurs. |
| P2-SFA07 | Route and browser matrix | The production stack smoke-checks 13 primary routes including `/research/sec/filing-history`. Five retry-free Chromium tests cover the call list, revisions, outcomes, and two SEC cases; the SEC success case visits summary, descriptors, accessions, and occurrences in Korean and English with KST visible values and exact UTC `datetime` evidence. |
| P2-SFA08 | Exact access evidence | The harness requires 18 exact full Tomcat access-log lines: the prior 13 call reads plus four successful SEC resources and the pre-assembly SEC 404. Substring matches and inferred totals are insufficient. |
| P2-SFA09 | Correct KST day boundary | The exact filtered call-list line uses inclusive `from=2026-08-10T15%3A00%3A00.000Z` and exclusive `to=2026-08-11T15%3A00%3A00.000Z`, preserving ADR-054's `2026-08-11` Korean civil-day meaning. |
| P2-SFA10 | Exact PostgreSQL tuple | The no-whitespace SQL result equals the complete tuple below, including zero operator-ledger rows and exact manifest/selection/root/assembly identities. A cardinality-only subset is insufficient. |
| P2-SFA11 | Owned cleanup | Success and failure remove only the exact run-owned API/web processes, Compose project/volume, source mirror, temporary build, reports, logs, and lock. Root `.env`, default database/volume, normal `.next`, `apps/web/next-env.d.ts`, and `apps/web/tsconfig.json` remain untouched. |
| P2-SFA12 | CI/manual separation | Repository CI parses and guards ADR-055 source, markers, expected identity, and nested historical projection, but does not run the Docker/Chromium harness. Only an actual manual local run may be recorded as runtime PASS. |

The required database tuple is:

```text
3|2|4|3|1|2|2|2|4|1|2|4|6|0|0|0|0|cda6762d385d4e889294d0fec1f7a2a7b20c5157cf67c832b7d7f4857550a1cd|eadb0c3bf6efb9b3323be1342d0b17e63631b706f088b23fa78e784e1b547acd|c9bfc935b27e059397531a4dda1a1a0222e98528c33e85b886c91ca6b74f2fa8|2026-08-25T03:30:00.123456Z
```

In order, it contains call/revision/outcome counts `3|2|4`; decoded body,
root capture, recent row, root descriptor, segment capture, segment row,
manifest, selected descriptor, accession-group, and occurrence counts
`3|1|2|2|2|4|1|2|4|6`; operator attempt/action/dispatch/outcome counts
`0|0|0|0`; and the exact manifest, selection, root, and assembly identities.

### ADR-055 required manual verification

Run from the repository root only after the documented PowerShell 7, Java 21,
Node.js 24, installed dependency, Playwright Chromium, and local-Docker
prerequisites are available:

```powershell
pwsh -NoProfile -File ./scripts/verify-local-full-stack.ps1
```

Acceptance requires all 13 routes, five Chromium tests, 18 exact HTTP lines,
the complete database tuple, server-only private API isolation, disabled live
SEC/operator boundaries, and owned cleanup to pass in one run. The 2026-08-31
manual run met the complete contract; subsequent release candidates must rerun
the same command rather than inheriting that result.

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
