# P2 Acceptance Checks — Core UI

Current status: the methodology-registry, multiple market-map shell,
sector/industry PRICE_CHANGE treemap, dashboard evidence-composition, and
institution and analyst identity directory, and known-unavailable market-board
publication-state vertical slices are complete. These checks close only
delivered P2 slices; leaderboard, screener, full-universe map, and production
market-mode work stays open.

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
  `CallsProvider` and `MarketTreemapProvider` ports.
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
- The two provider ports are constructor-injected into the fixture composer.
  The composer does not import raw fixture JSON, reach through a provider, use
  the P0 Java fixture quote seam, or synthesize a common timestamp.
- This composition adds no schema, fixture, manifest member, API endpoint,
  OpenAPI path, database migration, persistence write, network provider, market
  calculation, event-calendar aggregation, or leaderboard calculation.

## Dashboard evidence composition contract gate

| ID | Check | Expected result |
| --- | --- | --- |
| P2-D01 | Existing-provider composition | The dashboard fixture adapter receives only `CallsProvider` and `MarketTreemapProvider`. It does not duplicate canonical calls/maps in application source or add a dashboard fixture/schema. |
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

- Provider tests inject the two ports and prove the exact query, call order,
  complete canonical row preservation, exact two-preview order, DEMO parity,
  section-local metadata, and the three closed unavailable/deferred states.
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
leaderboards, the screener shell, S&P history, persistent dashboard read models,
realtime refresh, and personalized layout remain open.

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
coherent `PUBLISHED` board. P6 retains history and P8 retains operational data-
quality monitoring. This P2 status document must not be used as a shortcut to
any of those capabilities.

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
