# P3 Acceptance Checks — Deterministic Scoring

Current status: the pure target-hit and directional-win comparison cores,
explicit-anchor session-offset mechanics, and policy-neutral event/session
relation classifier vertical slices are complete. The strict session-close
named-horizon and explicit forecast-basis policy slice is also complete. The
call-direction polarity policy slice is also complete. None publishes a
calculated outcome or completes the broader P3 scoring phase, which remains
open. The mechanical calculator-side adapter slice is also complete. The
disconnected calculator-side routing-evidence slice is also complete. The
point-in-time official endpoint-price selector, target-error calculation,
basis-event/endpoint price-pair selector, and signed asset-return calculation
leaves are also complete; no runtime outcome or product surface is published.

## Pure target-hit slice boundary

- `TargetHitInput` accepts only an already interpreted `BULLISH` or `BEARISH`
  side, nullable target, and one nullable favorable extreme preselected by its
  caller. It never receives both high and low or chooses/derives a window value.
- For `BULLISH`, favorable extreme means the upstream-selected window high and
  a hit is `favorableExtreme >= target`. For `BEARISH`, it means the upstream-
  selected window low and a hit is `favorableExtreme <= target`. Equality is a
  hit on both sides. The primitive cannot verify the upstream selection.
- Every provided decimal is positive and exactly representable as
  `NUMERIC(38,12)`. Comparison uses the original values, is scale-insensitive,
  and performs no division, rounding, output rescaling, currency conversion,
  tolerance, epsilon comparison, or `double`/`float` conversion. Validation may
  probe scale 12 with `RoundingMode.UNNECESSARY` without mutating/replacing the
  input.
- `TargetHitResult` is sealed as `Available(boolean)` or
  `Unavailable(UnavailableReason)`, where reason is exactly `TARGET_MISSING`,
  `FAVORABLE_EXTREME_MISSING`, or
  `TARGET_AND_FAVORABLE_EXTREME_MISSING`. Unavailable is not a miss and is never
  converted to `false` or zero. Missing/unsupported side is rejected.
- The caller, not this primitive, owns horizon/calendar/session/window
  selection, point-in-time availability, price and currency normalization,
  corporate actions, call-direction polarity, neutral eligibility,
  methodology selection, and outcome completeness.
- Source-local golden vectors are test cases, not canonical fixtures or market
  facts. No vector is imported by production code or exposed through the API.

## Pure target-hit contract gate

| ID | Check | Expected result |
| --- | --- | --- |
| P3-TH01 | Exact input surface | `TargetHitInput` contains only interpreted side, target, and one favorable extreme. There is no call ID, direction, horizon, timestamp, asset, currency, high/low pair, snapshot, methodology, provider, or source field, and the primitive never selects or verifies the upstream window extreme. |
| P3-TH02 | Exact comparison | Complete bullish input uses `favorableExtreme.compareTo(target) >= 0`; complete bearish input uses `favorableExtreme.compareTo(target) <= 0`. Equality is a hit and no tolerance is applied. |
| P3-TH03 | Decimal safety | Every present decimal is positive and exactly representable as `NUMERIC(38,12)`. The primitive performs no arithmetic, rounding, output scale normalization, parsing, or binary floating-point conversion and does not mutate its inputs; validation may make a non-mutating exact scale/precision probe. |
| P3-TH04 | Explicit unavailable states | Result is exactly sealed `Available(boolean)` or `Unavailable(one non-null reason)`. The three exact missing-input combinations map to `TARGET_MISSING`, `FAVORABLE_EXTREME_MISSING`, or `TARGET_AND_FAVORABLE_EXTREME_MISSING`; a missing value never becomes zero or a miss. |
| P3-TH05 | Closed side semantics | Only already interpreted `BULLISH` and `BEARISH` sides are accepted. No `CallDirection`, strong-direction reduction, neutral policy, string parsing, default, or fallback is present. |
| P3-TH06 | Determinism | Identical inputs produce equal results regardless of clock, locale, timezone, JVM default zone, input decimal scale, invocation order, or prior calls. |
| P3-TH07 | Pure source boundary | Production calculation code imports no call/outcome aggregate, provider, repository, fixture, framework, controller, persistence, network, JSON, scheduler, `Clock`, random, or floating-point dependency. |
| P3-TH08 | No methodology claim | The primitive is not bound to either stored methodology/version/hash. Both canonical methodologies remain exactly `MODEL_ONLY`; no formula body, ACTIVE state, input fingerprint, sequence, or outcome is created. |
| P3-TH09 | No product publication | Existing schemas, canonical fixtures, manifest, OpenAPI, Flyway, API/controller/repository behavior, database rows, and web source remain unchanged. The existing outcome endpoint still returns only its four P1 model records. |
| P3-TH10 | Later integration boundary | Runtime target-hit evaluation requires an independently reviewed, versioned horizon/window/input contract and methodology definition. It must preserve unavailable as canonical null/incomplete evidence rather than treating the primitive alone as a complete outcome. |

## Required golden and negative tests

- Source-local parameterized vectors preserve the documented examples:
  bullish target `110` with favorable high `112` is a hit; bullish target `120`
  with favorable high `103` is a miss; bearish target `170` with favorable low
  `168` is a hit.
- Both sides cover equality as a hit, one-unit or fractional misses immediately
  across the boundary, negative-scale and scale-equivalent `BigDecimal` values,
  exact `NUMERIC(38,12)` maximums, repeat invocation, and source-value
  immutability.
- Missing target only, favorable extreme only, and both inputs assert the exact
  unavailable reason and absence of a Boolean value. Null side, zero/negative
  values, scale 13, and precision 39 fail closed; a provided invalid value fails
  even when the other decimal is missing.
- A source-boundary test or repository CI scan proves production calculation
  code has no forbidden dependency and source-local golden vectors have no
  production import path.
- Full API verification proves the canonical fixture still contains exactly
  two `MODEL_ONLY` methodologies and four PENDING/INCOMPLETE, all-null outcomes;
  the exact read-only outcome API and PostgreSQL migration suite remain intact.

## Explicit-anchor session-offset slice boundary

- The caller supplies one anchor session ID, one positive session count, one
  evaluation as-of instant, and an explicit ordered open/close session catalog.
  The resolver does not receive or derive a call event time or named horizon.
- Count `N` selects the next exactly `N` catalog entries after the anchor. The
  anchor is excluded, the selected entries and endpoint are included, and the
  Nth subsequent entry is the endpoint.
- The catalog is schedule input only. Its identity and revision do not claim a
  licensed source, provenance, point-in-time capture, observed market session,
  or complete exchange calendar.
- `Ready` means only that the endpoint close is at or before the explicit
  evaluation as-of. It never means that a price/bar exists or that any outcome
  metric is calculated or complete.

## Explicit-anchor session-offset contract gate

| ID | Check | Expected result |
| --- | --- | --- |
| P3-SO01 | Exact request | `SessionOffsetRequest` contains only `policyVersion`, `anchorSessionId`, positive `sessionCount`, `evaluationAsOf`, and `catalog`. It has no call, analyst-call revision, event time, outcome horizon, observation, price, provider, provenance, capture time, or clock. |
| P3-SO02 | Exact policy version | The code-only policy enum contains exactly `EXPLICIT_ANCHOR_SESSION_COUNT_V1`. It is not a scoring-methodology version and carries no invented definition hash. |
| P3-SO03 | Explicit catalog | A session contains exactly ID/open/close, and a catalog contains exactly calendar ID/revision/ordered sessions. Identities are non-blank; opens precede closes; UTC instants have at most microsecond precision; IDs are unique; entries are ordered and non-overlapping; construction and resolution do not mutate caller collections. |
| P3-SO04 | Count/window semantics | Count `N > 0` selects list positions `anchor + 1` through `anchor + N` inclusive. The anchor is excluded, exactly N sessions are returned in source order, and the last is the endpoint. Endpoint lookup cannot overflow. |
| P3-SO04A | Exact result | `SessionOffsetResolution` nests `ResolutionContext(policyVersion, calendarId, catalogRevision, anchorSessionId, sessionCount, evaluationAsOf)` and `ResolvedSessionWindow(context, anchorSession, immutable sessions, endpointSession)`. Variants are exactly `Ready(window)`, `Pending(window, ENDPOINT_NOT_REACHED)`, or `Incomplete(context, ANCHOR_SESSION_MISSING|ENDPOINT_SESSION_MISSING)`. |
| P3-SO05 | No calendar inference | The resolver never sorts, inserts, drops, or derives a session and uses no local date, zone, weekday, weekend, holiday, DST, duration, month, or year arithmetic. Explicit irregular gaps and weekend sessions count; omitted dates remain absent. |
| P3-SO06 | Ready boundary | An existing endpoint is `Ready` exactly when `endpoint.closesAt <= evaluationAsOf`; equality is ready. Ready exposes schedule evidence only and is not `CALCULATED`, `dataComplete`, or proof of an observed close/bar. |
| P3-SO07 | Pending boundary | An existing endpoint strictly after `evaluationAsOf` is `Pending` with exact reason `ENDPOINT_NOT_REACHED`, preserving the resolved window without consulting a clock. |
| P3-SO08 | Incomplete coverage | An absent anchor returns only `ANCHOR_SESSION_MISSING`; insufficient subsequent catalog entries return only `ENDPOINT_SESSION_MISSING`. Neither is silently changed to pending, ready, zero, a guessed date, or canonical `HORIZON_DATA_MISSING`. |
| P3-SO09 | Invalid input | Nulls, blank identities, non-positive counts, duplicate session IDs, invalid open/close bounds, non-increasing or overlapping entries, and finer-than-microsecond instants fail closed rather than returning a schedule result. |
| P3-SO10 | Pure source boundary | Production resolver code imports no call/outcome aggregate, `OutcomeHorizon`, target calculator, provider, repository, fixture, framework, controller, persistence, network, JSON, scheduler, `Clock`, locale, timezone, or floating-point dependency. No other production class wires the leaf. |
| P3-SO11 | No publication | Source-local schedules are golden inputs only. No schema, canonical fixture, manifest member, OpenAPI path, Flyway migration, database row, API behavior, provider, or web source is added or changed. Existing methodologies remain exactly `MODEL_ONLY` and all four outcome metrics remain null. |
| P3-SO12 | Later named-horizon boundary | D1/W1/M1/M3/M6/Y1 mappings, event/correction anchoring, calendar sourcing, observation completeness, retry/grace, price/window rules, methodology serialization/hash, and input fingerprinting require a later reviewed contract before runtime use. |

## Required session-offset golden and negative tests

- Count one and count five prove anchor-excluded, endpoint-included window size,
  source order, and endpoint identity without mutating the input list.
- Evaluation immediately before, exactly at, and after endpoint close proves the
  pending/ready boundary and exact `ENDPOINT_NOT_REACHED` reason.
- Empty/unknown anchor catalogs and insufficient endpoint coverage prove the two
  exact incomplete reasons; a maximum integer count remains overflow-safe.
- Explicit Saturday/irregular/early-close entries are counted, while an omitted
  weekday is never inserted. JVM timezone and locale changes do not alter the
  result.
- Nulls, blanks, count zero/negative, duplicate IDs, reversed or equal
  open/close, out-of-order/overlapping entries, and sub-microsecond instants
  fail closed. Golden schedules remain source-local Java values, not JSON.

## Event/session relation slice boundary

- The caller supplies one event time and the existing explicit ordered session
  catalog. The classifier describes their temporal relation only and never
  chooses an anchor or call/revision basis.
- Exact opens, strict interiors, exact closes, strict gaps, touching close/open
  boundaries, empty catalogs, and before/after coverage remain distinct.
- A gap is not named as pre-market, after-hours, weekend, or holiday. Those are
  later calendar/methodology interpretations that the supplied intervals alone
  cannot prove.

## Event/session relation contract gate

| ID | Check | Expected result |
| --- | --- | --- |
| P3-ER01 | Exact request | `EventSessionRelationRequest` contains only `policyVersion`, `eventTime`, and `catalog`. It has no call, analyst-call revision, basis selector, anchor, horizon, session count, evaluation as-of, observation, price, provenance, capture time, provider, or clock. |
| P3-ER02 | Exact policy version | The code-only enum contains exactly `EXPLICIT_SESSION_BOUNDARY_RELATION_V1`; it is not a scoring-methodology version and has no invented definition hash. |
| P3-ER03 | Exact context | Every result preserves exactly `RelationContext(policyVersion, calendarId, catalogRevision, eventTime)` from the supplied request/catalog without adding source or point-in-time claims. |
| P3-ER04 | Closed relations | Variants are exactly `EmptyCatalog(context)`, `BeforeCatalog(context, firstSession)`, `AtOpen(context, session)`, `InsideSession(context, session)`, `AtClose(context, session)`, `AtTouchingBoundary(context, closingSession, openingSession)`, `BetweenSessions(context, previousSession, nextSession)`, and `AfterCatalog(context, lastSession)`. |
| P3-ER05 | Exact interval boundaries | Before means event < first open; open and close equality have explicit variants; inside is open < event < close; between is previous close < event < next open; after is event > last close. No epsilon or rounding is used. |
| P3-ER06 | Touching sessions | When previous close == next open == event time, only `AtTouchingBoundary` is returned with both sessions. Neither open nor close silently wins. |
| P3-ER06A | Classifier-owned catalog relation | Public relation-record constructors enforce only locally decidable null, identity, and temporal-inequality constraints. Catalog membership, first/last position, adjacency, and touching precedence are guaranteed only by `EventSessionRelationClassifier`; direct construction attests neither catalog provenance nor runtime eligibility. |
| P3-ER07 | No calendar inference | Source order/intervals are used unchanged. The classifier never sorts, inserts, drops, or derives a session and uses no local date, zone, weekday, weekend, holiday, DST, month/year, or duration arithmetic. |
| P3-ER08 | No anchor policy | No output contains an anchor, session count, endpoint, ready/pending state, or recommendation. Original-call versus correction event selection and every relation-to-anchor mapping remain caller/later-methodology responsibilities. |
| P3-ER09 | Time/input safety | Null request/policy/event/catalog and finer-than-microsecond event time fail closed. Empty catalog is valid evidence and returns `EmptyCatalog`; caller/catalog collections remain immutable and unmodified. |
| P3-ER10 | Pure source boundary | Production relation code imports no call/revision/outcome aggregate, `OutcomeHorizon`, target calculator, session-offset resolver/request/result, provider, repository, fixture, JSON, Spring, persistence, network, scheduler, `Clock`, locale, timezone, or floating-point dependency. No product runtime class wires it. |
| P3-ER11 | No PIT claim | Calendar ID/revision are echoed labels only. No relation claims the catalog was known at event or evaluation time, observed, licensed, complete, or source-traceable. |
| P3-ER12 | No publication | No schema, canonical fixture, manifest member, OpenAPI path, Flyway migration, database row, API behavior, provider, or web source changes. Existing methodologies remain `MODEL_ONLY` and all four outcomes retain null metrics. |

## Required event/session relation golden and negative tests

- Empty, one-session, and multi-session catalogs cover before first, exact open,
  one microsecond after open, strict interior, one microsecond before close,
  exact close, one microsecond after close, exact next open, and after last.
- A Friday-to-Monday gap remains `BetweenSessions`; an explicit Saturday event
  can be `InsideSession`; irregular early closes are classified exactly as
  supplied without inserting an omitted date.
- A valid touching boundary returns both closing and opening sessions and never
  changes with list replay, locale, or default timezone. The classifier test
  proves touching precedence over the individually valid open/close records.
- Reflection or equivalent contract tests lock every record component and the
  exact eight permitted result variants. Constructor negatives cover only
  locally decidable contradictions; null/sub-microsecond input fails closed,
  and source lists/catalogs are not mutated.

## Pure directional-win slice boundary

- `DirectionalWinInput` accepts only an already interpreted `BULLISH` or
  `BEARISH` side and one nullable asset return precomputed and selected by its
  caller. It receives no prices and does not calculate or verify the return.
- A bullish result is true exactly when `assetReturn > 0`; a bearish result is
  true exactly when `assetReturn < 0`. Exact zero is false for both sides.
- Every provided return is a signed decimal exactly representable as
  `NUMERIC(38,12)`. Negative, zero, and positive values are valid; no division,
  rounding, rescaling, parsing, tolerance, epsilon, or binary floating-point
  conversion is permitted.
- A null asset return produces only explicit unavailable reason
  `ASSET_RETURN_MISSING`. It is not zero, false, a loss, or a calculated result.
- The caller owns call-direction reduction, neutral eligibility, horizon and
  observation selection, return calculation, price/currency normalization,
  corporate actions, point-in-time input identity, methodology selection, and
  outcome completeness.
- Source-local golden vectors are tests, not canonical fixtures or market
  facts. No vector is imported by production code or exposed by a product
  surface.

## Pure directional-win contract gate

| ID | Check | Expected result |
| --- | --- | --- |
| P3-DW01 | Exact input surface | `DirectionalWinInput` contains exactly `DirectionalWinSide side` and nullable `BigDecimal assetReturn`. It has no call direction, target, price, horizon, timestamp, asset, currency, snapshot, methodology, provider, source, or completeness field. |
| P3-DW02 | Closed side semantics | `DirectionalWinSide` contains exactly `BULLISH` then `BEARISH`. No `CallDirection`, strong-direction reduction, neutral policy, string parsing, default, or fallback is present. |
| P3-DW03 | Strict sign comparison | Complete bullish input returns true only for `assetReturn.compareTo(BigDecimal.ZERO) > 0`; complete bearish input returns true only for comparison `< 0`. Exact zero and scale-equivalent zero return false for both sides; no equality case is a win. |
| P3-DW04 | Signed decimal safety | Every provided negative, zero, or positive decimal is exactly representable as `NUMERIC(38,12)`. Validation may make a non-mutating exact representability probe, but the primitive performs no arithmetic, rounding, output scale normalization, parsing, or binary floating-point conversion and does not mutate its input. |
| P3-DW05 | Explicit unavailable state | `DirectionalWinResult` is exactly sealed `Available(boolean directionalWin)` or `Unavailable(ASSET_RETURN_MISSING)`. A null return never becomes zero, false, a loss, or a calculated value. Null side and provided invalid decimals fail closed rather than becoming unavailable. |
| P3-DW06 | Determinism | Identical inputs produce equal results regardless of clock, locale, default timezone, decimal scale, invocation order, or prior calls. |
| P3-DW07 | Pure source boundary | The exact calculation package contains the four existing target-hit files plus exactly `DirectionalWinSide`, `DirectionalWinInput`, `DirectionalWinResult`, and `DirectionalWinCalculator`; production calculation code imports no call/outcome aggregate, horizon package, provider, repository, fixture, framework, controller, persistence, network, JSON, scheduler, `Clock`, random, locale/timezone, or binary floating-point dependency. No production class outside the calculation leaf wires either primitive. |
| P3-DW08 | No return or methodology claim | The primitive treats `assetReturn` as caller-supplied input. It computes no endpoint price or return and is not bound to a methodology/version/hash. Both canonical methodologies remain exactly `MODEL_ONLY`; no formula body, ACTIVE state, input fingerprint, sequence, or outcome is created. |
| P3-DW09 | No product publication | Existing 14 schemas, 13 canonical fixture files, manifest membership/order, five OpenAPI paths, five Flyway migrations, API/controller/repository behavior, database rows, and web source remain unchanged. The outcome endpoint continues to expose only four P1 model records with every metric/result null. |
| P3-DW10 | Later integration boundary | Runtime directional-win evaluation requires independently reviewed direction reduction, named-horizon and observation selection, deterministic asset-return calculation, corporate-action/currency policy, point-in-time input identity, canonical methodology definition/hash, unavailable mapping, and input fingerprinting. This leaf alone cannot make an outcome complete. |

## Required directional-win golden and negative tests

- Source-local parameterized vectors cover bullish positive as true, bullish
  negative as false, bearish negative as true, and bearish positive as false.
  Both sides cover exact and scale-equivalent zero as false, the smallest
  positive and negative scale-12 values, negative-scale inputs, and exact
  positive and negative `NUMERIC(38,12)` boundaries.
- Null return on both permitted sides asserts `ASSET_RETURN_MISSING` and absence
  of a Boolean value. Null side, positive and negative scale-13 values, and
  positive and negative precision-39 values fail closed; invalid provided
  evidence is never hidden as unavailable.
- Repeated invocation, changed JVM locale/default timezone, scale-equivalent
  values, and source-value inspection prove deterministic comparison and input
  immutability.
- Reflection or an equivalent exact-shape test locks both record components,
  the two sides, result variants, and sole unavailable reason. A source-boundary
  test or repository CI scan proves no reverse production wiring and no
  canonical JSON golden or product publication.
- Full API verification must preserve exactly two `MODEL_ONLY` methodologies,
  four PENDING/INCOMPLETE all-null outcomes, the read-only outcome endpoint,
  and PostgreSQL migration coverage.

## Strict session-close horizon and forecast-basis slice boundary

- `OutcomeBasis` is supplied explicitly as either an original call with null
  revision identity or one already-validated correction with non-null revision
  identity. Each variant owns its own event time; a correction never borrows the
  original event time or rewrites the original schedule lineage.
- The approved named counts are exactly `D1=1`, `W1=5`, `M1=21`, `M3=63`,
  `M6=126`, and `Y1=252`. A candidate session is eligible only when its supplied
  `closesAt` is strictly after the basis event time. The Nth eligible supplied
  session is the named-horizon endpoint.
- This is a direct close-selection policy, not a predecessor-anchor or
  evaluation-readiness policy. It has no evaluation-as-of and returns no
  ready/pending state. Resolved proves only that a schedule endpoint can be
  identified from the supplied catalog.
- The exact 633-byte UTF-8 canonical definition and fixed lowercase SHA-256
  `550087efe7ddf2ba31974c89c2740ab79df986eefef48919c32c56a3232f8dc1`
  are locked by ADR-010, code, and source-local golden tests. The digest is a
  schedule-policy identity, not a scoring-methodology hash.
- The caller still owns call/revision existence and validity, cancellation
  eligibility, asset/venue/calendar selection, catalog provenance and point-in-
  time availability, observed prices, currency/corporate actions, methodology
  selection, input fingerprinting, and outcome completeness.

## Strict session-close horizon contract gate

| ID | Check | Expected result |
| --- | --- | --- |
| P3-SC01 | Exact request | `SessionCloseHorizonRequest` contains exactly `SessionCloseHorizonPolicyVersion policyVersion`, `OutcomeBasis basis`, `OutcomeHorizon horizon`, and `TradingSessionCatalog catalog`. It has no anchor, evaluation-as-of, price, observation, snapshot, provider, provenance, methodology, fingerprint, clock, or persistence field. |
| P3-SC02 | Closed basis | `OutcomeBasis` permits exactly `Original(callId,eventTime)` and `Correction(callId,basisRevisionId,eventTime)`. Both expose call ID, nullable basis revision ID, and event time; original returns exact null revision identity and correction requires one. Cancellation is not a basis. Null, blank/untrimmed identity, null time, or finer-than-microsecond time fails closed. |
| P3-SC03 | Exact policy identity | `SessionCloseHorizonPolicyVersion` contains exactly `STRICTLY_AFTER_BASIS_EVENT_SESSION_CLOSE_V1`. Its canonical definition is the exact ADR-010 single-line 633-byte ASCII/UTF-8 sequence with no BOM, surrounding whitespace, or line ending; key order explicitly fixes supplied-catalog order, first-N window choice, Nth endpoint, both incomplete reasons, absent readiness, and nested D1/W1/M1/M3/M6/Y1 counts. Its fixed lowercase SHA-256 is `550087efe7ddf2ba31974c89c2740ab79df986eefef48919c32c56a3232f8dc1`. Returned byte arrays are defensive copies. |
| P3-SC04 | Closed named counts | `sessionCount` maps exactly `D1→1`, `W1→5`, `M1→21`, `M3→63`, `M6→126`, and `Y1→252`; null or an unsupported horizon cannot default. Counts are fixed integers, not calendar duration arithmetic. |
| P3-SC05 | Strict eligible predicate | The resolver preserves catalog source order and selects only entries satisfying `session.closesAt > basis.eventTime`. It takes the first exactly N eligible sessions and never sorts, inserts, drops, derives, or mutates a session. |
| P3-SC06 | Exact temporal boundaries | Before-first, exact-open, and strict-interior events may resolve the current/first session; exact close skips it; a touching close/open selects the opening session; a strict gap selects the following session. Comparison is exact with no epsilon, local date, timezone, weekday, holiday, or duration inference. |
| P3-SC07 | Exact result | `SessionCloseHorizonResolution` is sealed as exactly `Resolved(window)` or `Incomplete(context,reason)`. Context contains exactly policy version, policy definition hash, basis, horizon, session count, calendar ID, and catalog revision. A resolved window contains exactly context, immutable selected sessions, and endpoint session, with exactly N unique source-ordered sessions and endpoint equal to the last. |
| P3-SC07A | Resolver-owned catalog claim | Public context/window constructors enforce only locally decidable hash/count, exact-size, strict-first-close, unique/chronological/nonoverlapping, and endpoint-last invariants. Because a direct window carries no catalog, it cannot attest catalog membership, adjacency, or first-N eligibility; a direct `Incomplete` cannot attest reason correctness. Only `SessionCloseHorizonResolver` guarantees catalog-derived first-N selection and the matching incomplete reason. |
| P3-SC08 | Explicit missing coverage | Empty catalog or no session with close strictly after the basis event returns only `FIRST_ELIGIBLE_SESSION_MISSING`. A first eligible session with fewer than N total eligible sessions returns only `HORIZON_ENDPOINT_SESSION_MISSING`. Missing coverage is never ready, pending, resolved, zero, a guessed endpoint, or canonical `HORIZON_DATA_MISSING`. |
| P3-SC09 | Independent correction clock | An original uses its own call event time and null revision identity. Every caller-validated correction uses its own correction event time and non-null basis revision identity, producing an independent context/window without replacing the original. The primitive does not load a call/revision or decide validity/supersession. |
| P3-SC10 | Determinism and definition replay | Identical request/catalog input produces equal results regardless of clock, locale, default timezone, invocation order, prior calls, or mutation attempts against returned definition bytes/window lists. Policy definition bytes recompute the fixed digest. |
| P3-SC11 | Pure source boundary | Production policy code may depend only on deterministic JDK types, `PersistentInstant`, `OutcomeHorizon`, and existing horizon-package types. It imports no call/revision/outcome aggregate beyond the closed horizon enum, calculation primitive, provider, repository, fixture, JSON mapper, framework, controller, persistence, network, scheduler, `Clock`, local-calendar type, random, or floating-point dependency. No product runtime class wires the policy. |
| P3-SC12 | No observation or PIT claim | Catalog ID/revision and basis identity/time are caller evidence echoed in context. `Resolved` claims no licensed/complete calendar, point-in-time availability, venue association, observed close/bar/price, corporate-action adjustment, asset return, target hit, directional win, readiness, or data completeness. |
| P3-SC13 | No product publication | Existing 14 schemas, 13 canonical fixture files, manifest membership/order, five OpenAPI paths, five Flyway migrations, API/controller/repository/database behavior, canonical methodology/outcome rows, and web source remain unchanged. No API key, account, paid plan, domain, data license, or network access is required. |
| P3-SC14 | Later integration boundary | Endpoint observation, deterministic return calculation, calendar provenance, cancellation eligibility, methodology definition/activation, point-in-time input identity, input fingerprinting, append-only outcome orchestration, aggregation, and UI publication require later reviewed contracts. |

## Required strict session-close golden and negative tests

- The canonical policy string is asserted byte-for-byte in exact key order;
  UTF-8 length is 633, recomputed SHA-256 equals the fixed digest, code returns
  that digest, and mutating one returned byte array cannot affect a later call.
- Every `OutcomeHorizon` asserts its exact count and resolves with exactly N
  eligible source-local sessions. The N-minus-one matrix is exact: D1 with zero
  eligible entries returns `FIRST_ELIGIBLE_SESSION_MISSING`; W1/M1/M3/M6/Y1
  with N-minus-one entries and at least one eligible entry return
  `HORIZON_ENDPOINT_SESSION_MISSING`. No test demands the impossible D1
  endpoint-shortage state.
- One-microsecond boundary vectors cover before first open, exact open, strict
  interior, one microsecond before close, exact close, one microsecond after
  close in a gap, exact touching close/open, one microsecond after touching,
  a strict weekend-like gap, an explicitly supplied Saturday session, and an
  irregular early close before and exactly at its close.
  The labels describe tests only; the resolver infers none of them.
- Empty, exact final close, after final close, no-first-eligible, and insufficient
  D1/W1/M1/M3/M6/Y1 coverage assert the two exact incomplete reasons and no
  resolved window. A valid first eligible session distinguishes endpoint
  shortage from first-eligible absence.
- Original and correction bases with the same call ID but different event times
  prove separate revision identity, schedule clocks, contexts, and endpoints.
  A second correction remains separate; cancellation has no construction path.
- Null request/policy/basis/horizon/catalog, null or malformed identities, null
  or sub-microsecond basis time, invalid public result construction, wrong
  context hash/count, `sessionCount(null)`, duplicate/out-of-order window
  sessions, first-window close not strictly after basis time, and endpoint-not-
  last fail closed. Direct-constructor tests claim only local invariants; resolver
  tests separately own catalog membership, first-N choice, and reason mapping.
- Reflection or equivalent exact-shape tests lock every new enum, interface,
  record component, result variant, and reason. Replay under changed locale and
  default timezone restores global state in `finally`; source-boundary and
  repository CI prove no runtime wiring, canonical JSON, or product publication.

## Call-direction polarity policy slice boundary

- The canonical five-value `CallDirection` is the only input vocabulary.
  `STRONG_BULLISH` and `BULLISH` reduce to directional `BULLISH`; `BEARISH` and
  `STRONG_BEARISH` reduce to directional `BEARISH`; `NEUTRAL` reduces only to
  explicit non-directional reason `NEUTRAL_DIRECTION`.
- Neutral is a complete policy classification, not missing evidence. It is not
  `Available(false)`, a loss, miss, bearish side, excluded/incomplete outcome,
  null, or a fallback for an unknown value.
- Every resolution preserves the original source direction and echoes the exact
  versioned policy-definition hash. Strong directions are not rewritten in the
  context even though their directional side is collapsed.
- The polarity leaf itself performs no side-enum adapter wiring, target or
  return calculation, horizon/observation selection, methodology activation,
  persistence, provider read, or product publication. ADR-012 separately owns
  the sole mechanical common-side adapter; ADR-013 separately owns the sole
  disconnected full-result routing outside the direction package.

## Call-direction polarity policy contract gate

| ID | Check | Expected result |
| --- | --- | --- |
| P3-CP01 | Exact request | `CallDirectionPolarityRequest` contains exactly `CallDirectionPolarityPolicyVersion policyVersion` and `CallDirection direction`. It has no call/revision aggregate, target, price, return, horizon, timestamp, snapshot, provider, methodology, fingerprint, completeness, or fallback field. |
| P3-CP02 | Exact source vocabulary | The canonical `CallDirection` remains exactly, and in order, `STRONG_BULLISH`, `BULLISH`, `NEUTRAL`, `BEARISH`, `STRONG_BEARISH`. The policy accepts the enum directly and performs no string parsing, aliasing, case normalization, ordinal arithmetic, default, or unknown-value inference. |
| P3-CP03 | Exact policy identity | `CallDirectionPolarityPolicyVersion` contains exactly `COLLAPSE_STRONG_DIRECTIONS_NEUTRAL_NON_DIRECTIONAL_V1`. Its canonical definition is the exact ADR-011 single-line 489-byte ASCII/UTF-8 sequence with no BOM, surrounding whitespace, or line ending and fixed lowercase SHA-256 `d83eccc92fedd7ba025745be2c8e78245bc308d0ff479467fa61afe543dc8a50`. Returned byte arrays are defensive copies. |
| P3-CP04 | Exact five-direction mapping | The resolver exhaustively maps `STRONG_BULLISH→BULLISH`, `BULLISH→BULLISH`, `NEUTRAL→NON_DIRECTIONAL`, `BEARISH→BEARISH`, and `STRONG_BEARISH→BEARISH`. No mapping is scale-, locale-, order-, history-, call-, target-, or market-dependent. |
| P3-CP05 | Closed result | `CallDirectionPolarityResolution` is sealed as exactly `Directional(context,side)` or `NonDirectional(context,reason)`. `DirectionalSide` is exactly `BULLISH`, `BEARISH`; `NonDirectionalReason` is exactly `NEUTRAL_DIRECTION`; context is exactly policy version, policy-definition hash, and original direction. |
| P3-CP06 | Neutral is not false/loss | `NEUTRAL` returns only `NonDirectional(context,NEUTRAL_DIRECTION)`. It never returns a Boolean, `Directional`, unavailable/missing, miss/loss, exclusion, incomplete state, zero, null, or exception for a valid request. |
| P3-CP07 | Constructor consistency | Public context/result construction fails closed for a wrong hash, directional result whose source direction maps to the other side or neutral, non-directional result whose source direction is bullish/bearish, null component, or any reason other than the sole closed neutral reason. Direct results preserve the source direction and can attest this locally decidable mapping. |
| P3-CP08 | Fail-closed request | Null request, policy version, or direction is invalid. The resolver has no fallback branch, default side, exception-to-neutral conversion, or behavior based on enum ordinal/name text. |
| P3-CP09 | Determinism | Identical requests produce equal results regardless of clock, locale, default timezone, invocation order, prior calls, or attempted mutation of returned policy bytes. |
| P3-CP10 | Pure source boundary | Production policy code imports only canonical `CallDirection`, its own package types, and exact deterministic JDK null/UTF-8 support. It imports no call/revision/outcome aggregate, calculator, horizon/calendar/window type, decimal/floating arithmetic, price/return/observation, provider, repository, fixture, JSON, Spring, persistence, network, scheduler, clock, locale, timezone, random, or LLM dependency. |
| P3-CP11 | Closed reverse wiring | Outside the owning direction package, ADR-012's exact `CalculatorSideAdapter` may import only nested `DirectionalSide`, and ADR-013's exact `CalculatorSideRouting` may consume only the full resolution plus its `Directional`/`NonDirectional` variants. No other production class references the polarity types. Routing preserves the original result and invokes no calculator, controller, application service, provider, repository, scheduler, or web source. |
| P3-CP12 | No publication | Existing 14 schemas, 13 canonical fixture files, manifest membership/order, five OpenAPI paths, five Flyway migrations, API/controller/repository/database behavior, canonical `CallDirection`, two model-only methodologies, four all-null outcomes, and web source remain unchanged. No API key, account, paid plan, domain, data license, or network access is required. |
| P3-CP13 | Later integration boundary | Beyond ADR-012's mechanical side translation and ADR-013's closed result routing, target eligibility, calculator invocation, horizon observations and returns, cancellation eligibility, methodology definition/activation, point-in-time input identity, fingerprinting, outcome persistence, aggregation, and UI publication require later reviewed contracts. |

## Required call-direction polarity golden and negative tests

- One exhaustive source-order vector covers all five `CallDirection` values and
  asserts the exact closed result, preserved source direction, policy version,
  and definition hash for each. The two strong directions must distinguish
  collapsed result side from preserved context direction.
- Neutral asserts exact `NonDirectional(NEUTRAL_DIRECTION)` and absence of a
  directional side or Boolean. Direct-constructor negatives reject neutral as
  either directional side and reject every directional source as non-directional.
- Wrong-side direct construction is rejected for both ordinary and strong
  bullish/bearish sources. Wrong hash and every null request/context/result
  component fail closed rather than selecting a default.
- The canonical policy string is asserted byte-for-byte in exact key order;
  UTF-8 length is 489, independently recomputed SHA-256 equals the fixed digest,
  code returns that digest, and mutating one returned byte array cannot affect a
  later call.
- Reflection or equivalent exact-shape tests lock the one policy constant, two
  request components, context components, result variants, two sides, and sole
  non-directional reason. Replay under changed locale/default timezone restores
  global state in `finally`.
- Source-boundary and repository CI reject ordinal/name/string/default mapping,
  any reverse wiring beyond ADR-012's exact nested-side import and ADR-013's
  exact full-result routing, calculator invocation, canonical JSON changes, and
  product publication. Source-local vectors are tests only, not analyst or
  market facts.

## Calculator-side polarity adapter slice boundary

- `CalculatorSideAdapter` translates an already resolved common
  `DirectionalSide` into each existing calculator's side vocabulary only.
  Common `BULLISH` maps to both destination `BULLISH` values, and common
  `BEARISH` maps to both destination `BEARISH` values.
- It accepts no `NonDirectional`, full polarity resolution, context, policy,
  canonical direction, string, ordinal, Boolean, target, return, or calculator
  input. Neutral therefore has no adapter invocation path and cannot become
  false, a loss, bearish, or an unavailable calculation.
- This is a mechanical type bridge with no independent methodology choice. It
  owns no version, definition, hash, provenance, fingerprint, or data evidence.
- Both calculators remain uninvoked and unchanged. Source-local vectors prove
  type translation only and publish no result.

## Calculator-side polarity adapter contract gate

| ID | Check | Expected result |
| --- | --- | --- |
| P3-CSA01 | Exact file surface | Production adds exactly `apps/api/src/main/java/com/wallstreetreceipts/api/domain/outcome/adapter/CalculatorSideAdapter.java`; tests add exactly the matching `CalculatorSideAdapterGoldenTest.java`. No second adapter/helper/mapper/result/version file exists. |
| P3-CSA02 | Exact class surface | `CalculatorSideAdapter` is public and final, has exactly one private zero-argument constructor, and declares exactly two public methods. Both are static; no public constructor, instance method, field, nested public type, generic API, or overload exists. |
| P3-CSA03 | Exact target-hit method | The exact signature is `public static TargetHitSide toTargetHitSide(DirectionalSide side)`. `BULLISH→TargetHitSide.BULLISH`, `BEARISH→TargetHitSide.BEARISH`, and null is rejected before translation. |
| P3-CSA04 | Exact directional-win method | The exact signature is `public static DirectionalWinSide toDirectionalWinSide(DirectionalSide side)`. `BULLISH→DirectionalWinSide.BULLISH`, `BEARISH→DirectionalWinSide.BEARISH`, and null is rejected before translation. |
| P3-CSA05 | Exhaustive enum translation | Each method uses an exhaustive enum switch with no default, ordinal/name/text parsing, reflection, alias, case normalization, map lookup, fallback, or future-value inference. Source-order goldens cover common `BULLISH` then `BEARISH` and both destination values together. |
| P3-CSA06 | No neutral entry point | No public method accepts `CallDirection`, `CallDirectionPolarityResolution`, `NonDirectional`, `NonDirectionalReason`, `ResolutionContext`, policy version, nullable wrapper, or Boolean. The adapter neither creates nor consumes neutral evidence. |
| P3-CSA07 | No calculator invocation | Production imports only the three side enums and `Objects`. It does not reference calculator/input/result classes, call `calculate`, create a target/return input, or produce available/unavailable metric evidence. |
| P3-CSA08 | No new policy identity | The adapter contains no policy/methodology version, canonical definition, UTF-8 definition bytes, hash, fingerprint, provenance, clock, or state. ADR-011 remains the only source-direction reduction policy identity. |
| P3-CSA09 | Determinism | Repeated identical translations return the same enum constants regardless of clock, locale, default timezone, invocation order, or prior calls. |
| P3-CSA10 | Pure/closed reverse wiring | Production depends on no aggregate, horizon/calendar/window, decimal/floating arithmetic, price/return/observation, provider, repository, fixture, JSON, Spring, persistence, network, scheduler, clock, locale, timezone, random, or LLM type. Outside the adapter itself, only ADR-013's exact `CalculatorSideRouting` references it, solely to derive the two side enums; no other production class references the adapter. |
| P3-CSA11 | No publication | Existing 14 schemas, 13 canonical fixture files, manifest membership/order, five OpenAPI paths, five Flyway migrations, API/controller/repository/database behavior, two model-only methodologies, four all-null outcomes, and web source remain unchanged. No API key, account, market calendar, price feed, paid plan, domain, data license, or network access is required. |
| P3-CSA12 | Later orchestration boundary | ADR-013 owns only extracting and preserving the two closed polarity branches. Choosing metric inputs, invoking calculators, composing unavailable states, activating methodology, fingerprinting, persisting, aggregating, and publishing remain later reviewed contracts. |

## Required calculator-side adapter golden and negative tests

- One source-order parameterized matrix contains exactly common `BULLISH` and
  `BEARISH`; each row asserts both exact destination enum constants, repeated
  translation, and no cross-polarity mapping.
- Both public methods reject null with no fallback. No test obtains neutral by
  null, a string, reflection, or a fabricated enum.
- Reflection or equivalent exact-shape tests prove one final class, one private
  constructor, exactly two public static methods, one `DirectionalSide`
  parameter each, and the two exact return types/names. No overload accepts a
  full resolution or non-directional type.
- Replay under changed locale/default timezone restores global state in
  `finally`. Source-boundary and repository CI prove no calculator invocation,
  reverse runtime wiring, version/hash, canonical JSON, or product publication.

## Calculator-side routing evidence slice boundary

- `CalculatorSideRouting` receives one complete
  `CallDirectionPolarityResolution` and preserves its exact original record in
  one of two closed evidence branches.
- A `Directional` source becomes
  `DirectionalRoute(source,targetHitSide,directionalWinSide)`. Both destination
  sides are derived only through `CalculatorSideAdapter`; the route does not
  reconstruct or replace the source policy context.
- A `NonDirectional` source becomes `NonDirectionalRoute(source)` and retains
  exact `NEUTRAL_DIRECTION` evidence. It has no target-hit side,
  directional-win side, Boolean, unavailable state, metric, or fallback.
- This is a mechanical composition of ADR-011 and ADR-012. It owns no new
  policy/methodology version, definition, hash, fingerprint, provenance, state,
  eligibility rule, or market evidence.
- It constructs no calculator input and invokes no calculator. Source-local
  vectors prove routing only and publish no result.

## Calculator-side routing evidence contract gate

| ID | Check | Expected result |
| --- | --- | --- |
| P3-CSR01 | Exact file/package surface | Production adds exactly `apps/api/src/main/java/com/wallstreetreceipts/api/domain/outcome/routing/CalculatorSideRouting.java`; tests add exactly the matching `CalculatorSideRoutingGoldenTest.java`. The separate routing package contains no second router/helper/request/result/version file, and ADR-012's adapter package remains exact-one-file. |
| P3-CSR02 | Exact outer surface | `CalculatorSideRouting` is public and final with exactly one private zero-argument constructor and exactly one public static method, `Result route(CallDirectionPolarityResolution resolution)`. It has no field, instance method, overload, generic API, or other public member. |
| P3-CSR03 | Exact closed result | Nested public sealed `Result` permits exactly public static final records `DirectionalRoute` and `NonDirectionalRoute` and declares no methods. `DirectionalRoute` components are exactly `Directional source`, `TargetHitSide targetHitSide`, and `DirectionalWinSide directionalWinSide`; `NonDirectionalRoute` contains exactly `NonDirectional source`. |
| P3-CSR04 | Directional preservation | Routing either bullish source preserves that exact `Directional` record and derives both exact destination `BULLISH` sides through `CalculatorSideAdapter`; routing either bearish source preserves that exact record and derives both exact destination `BEARISH` sides. Strong source directions remain unchanged in the nested source context. |
| P3-CSR05 | Neutral preservation | Routing the sole neutral policy result returns only `NonDirectionalRoute` containing the exact original `NonDirectional(NEUTRAL_DIRECTION)` record. No side, Boolean, false/loss, bearish mapping, unavailable/missing state, exclusion, or metric is created. |
| P3-CSR06 | Constructor consistency | Null route input fails before switching. Direct `DirectionalRoute` construction rejects null source/either side and recomputes both expected adapter translations for both common sides, rejecting either mismatch. Direct `NonDirectionalRoute` rejects null source; its typed source already owns ADR-011 neutral/context/hash consistency. |
| P3-CSR07 | Exhaustive routing | `route` uses an exhaustive sealed-pattern switch over exact `Directional` and `NonDirectional` variants with no default, ordinal/name/text parsing, reflection, alias, exception-to-neutral conversion, fallback, or future-variant inference. |
| P3-CSR08 | Identity and determinism | Every route preserves the exact source object and therefore its original direction, policy version, and definition hash. Repeated routing returns equal evidence regardless of clock, locale, default timezone, invocation order, prior calls, environment, process, thread, or random state. |
| P3-CSR09 | Pure composition | Production imports only `Objects`, `CalculatorSideAdapter`, the two calculator-side enums, full polarity resolution, and its exact two variants. It imports/references no calculator/input/result class, `calculate`, aggregate, horizon/calendar/window, decimal/floating arithmetic, price/return/observation, provider, repository, fixture, JSON, Spring, persistence, network, scheduler, clock, locale, timezone, environment/process/thread/random, reflection, or LLM type. |
| P3-CSR10 | Exact reverse wiring | Outside the owning direction package, only this exact class consumes full polarity resolution; outside `CalculatorSideAdapter` itself, only this exact class consumes that adapter. No other production class references the routing class/package, and neither calculation primitive references routing or direction types. |
| P3-CSR11 | No new policy/publication | Routing has no version/definition/hash/fingerprint/provenance/state and adds no schema, canonical fixture, manifest member, JSON golden, OpenAPI path, Flyway migration, database row, API/controller/provider/repository behavior, or web source. Both methodologies remain `MODEL_ONLY`; all four outcome metrics/results remain null. No API key, account, paid plan, domain, market calendar, price feed, data license, or network access is required. |
| P3-CSR12 | Later calculation boundary | Target eligibility, target/return selection, horizon/window observation identity, unavailable-state composition, calculator invocation, methodology activation, fingerprinting, persistence, aggregation, and publication remain later reviewed contracts. A route is evidence only and cannot make an outcome calculated or complete. |

## Required calculator-side routing golden and negative tests

- One exact source-order matrix routes all five canonical directions through
  the actual ADR-011 resolver. Strong/ordinary bullish and bearish rows assert
  both exact destination sides; neutral asserts only the exact non-directional
  source. Every route preserves the exact source object and repeated routing is
  equal.
- Direct `DirectionalRoute` construction covers both bullish and bearish
  sources, both correct side pairs, each independently wrong target-hit side,
  and each independently wrong directional-win side. Null route input and all
  four null record components fail closed; direct neutral routing preserves its
  exact source.
- Reflection or equivalent exact-shape tests lock the final outer class,
  private zero-argument constructor, sole public static route method, sealed
  zero-method `Result`, exact permitted nested records/modifiers, and exact
  record component names/types.
- Replay under changed locale/default timezone restores global state in
  `finally`. Source-boundary and repository CI prove exact imports, exhaustive
  switching, sole reverse wiring, no calculator invocation, no policy identity,
  no canonical JSON changes, and no product publication.

## Point-in-time endpoint-price selection slice boundary

- The selector consumes one ADR-010 strict-close `Resolved` window plus
  explicit catalog, asset/primary-venue/source binding, evaluation-as-of, and
  observation-candidate evidence. It never obtains any of those inputs.
- V1 means the official primary-venue regular-session endpoint close in the
  binding currency, with no FX or fallback, split/reverse-split adjusted to the
  endpoint-share basis and dividend-unadjusted.
- Catalog, binding, and every candidate carry separate point-in-time and
  provenance evidence. Both `availableAt` and `capturedAt` must be known by
  `evaluationAsOf`; future candidates are filtered before any identity test.
- Only exactly one fully valid known candidate resolves. Zero known candidates,
  ordered mismatch gates, continuity failure, and multiple valid known
  candidates remain explicit unavailable evidence rather than a guessed price.
- The selector is disconnected from canonical fixtures, persistence, API,
  providers, and web publication. Its goldens are evidence vectors, not market
  facts.

## Point-in-time endpoint-price contract gate

| ID | Check | Expected result |
| --- | --- | --- |
| P3-EP01 | Exact file/package surface | Production package `com.wallstreetreceipts.api.domain.outcome.observation` contains exactly `CatalogPointInTimeEvidence.java`, `CorporateActionContinuity.java`, `EndpointPriceAdjustmentBasis.java`, `EndpointPriceBinding.java`, `EndpointPriceField.java`, `EndpointPriceObservation.java`, `EndpointPricePolicyVersion.java`, `EndpointPriceRequest.java`, `EndpointPriceResolution.java`, and `EndpointPriceSelector.java`. Tests add exactly matching `EndpointPriceSelectorGoldenTest.java`; no provider/controller/repository/helper file is added. |
| P3-EP02 | Exact policy identity | `EndpointPricePolicyVersion` contains exactly `OFFICIAL_PRIMARY_VENUE_CLOSE_SPLIT_ADJUSTED_V1`. Its canonical definition is the exact ADR-014 single-line 2259-byte ASCII/UTF-8 sequence and its fixed lowercase SHA-256 is `37e37aba9302d77366cef4129f77a82b7ccb2f1937bfffc0315ea8d0bc6b1f76`. Returned bytes are defensive and every result context echoes the digest. |
| P3-EP03 | Strict-horizon input | Request accepts only `SessionCloseHorizonResolution.Resolved` using `STRICTLY_AFTER_BASIS_EVENT_SESSION_CLOSE_V1` and exact hash `550087efe7ddf2ba31974c89c2740ab79df986eefef48919c32c56a3232f8dc1`. An incomplete/directly inferred horizon, different version/hash, null component, or sub-microsecond evaluation instant fails closed. |
| P3-EP04 | Exact PIT evidence | Catalog and binding require canonical trimmed identities, separate provenance, microsecond times, and `capturedAt >= availableAt`. Both timestamps must be `<= evaluationAsOf`; catalog ID/revision must exactly match the resolved horizon context. The endpoint close must be `<= evaluationAsOf`. Gate precedence is exactly catalog not known, catalog mismatch, binding not known, endpoint not reached, then no known observation. |
| P3-EP05 | Future invisibility | Candidate filtering evaluates `availableAt <= evaluationAsOf && capturedAt <= evaluationAsOf` before every identity/mismatch/cardinality check. Empty and all-future lists return equal complete `OBSERVATION_MISSING_AS_OF` results. Adding future exact, wrong-identity, or duplicate rows to one known valid row leaves the full resolved result equal, including context and selected observation. |
| P3-EP06 | Exact known-candidate precedence | Known candidates are checked in exact order for asset, primary venue, currency, price-source ID/revision, catalog ID/revision, session ID, exact endpoint-close `observedAt`, price field, adjustment basis, and continuity. Multi-mismatch vectors return the first reason independent of candidate-list order; no string parsing, normalization, tolerance, implicit source preference, or later mismatch may override it. |
| P3-EP07 | Exact price semantics | The only accepted field is `OFFICIAL_REGULAR_SESSION_CLOSE`; venue is exact primary venue, currency is exact with no FX, and source ID/revision is exact. Price is positive and exactly `NUMERIC(38,12)`-representable. Adjustment is exactly split/reverse-split to endpoint-share basis and dividend-unadjusted; only `SPLIT_REVERSE_SPLIT_CONTINUOUS` is available. |
| P3-EP08 | Cardinality and identity | Exactly one fully valid known candidate resolves and preserves observation ID, provider event ID, source ID/revision, provenance, catalog/session identity, field, basis, continuity, times, original decimal value, and scale. Two valid known rows—including the same object repeated—return `OBSERVATION_AMBIGUOUS`; no dedupe or fallback exists. |
| P3-EP09 | Closed result and constructor ownership | `EndpointPriceResolution` permits exactly `Resolved(context,observation)` and `Unavailable(context,reason)` with the 16 ADR-014 reasons in exact order. Context contains exact policy identity, complete horizon resolution, catalog evidence, binding, and evaluation-as-of. Public constructors validate locally decidable consistency only; only `EndpointPriceSelector` attests request membership, PIT filtering, gate precedence, and cardinality. |
| P3-EP10 | Determinism and purity | Equal requests return equal results regardless of clock, locale, default timezone, input order where precedence is invariant, prior calls, environment, thread, or random state. Production uses deterministic Java/domain types only; no `Clock`, local calendar inference, float/double, rounding, JSON, framework, repository, network, provider, scheduler, reflection, environment, or mutable global state is allowed. |
| P3-EP11 | Exact reverse graph | Outside ADR-010's owning horizon package, only the exact endpoint request/resolution types consume their required strict-close types. The selector is the sole production consumer that attests endpoint request candidates. Target-error may consume only the exact endpoint policy/resolution/adjustment-basis types it requires; no other production package consumes raw observation, selector, or request types. |
| P3-EP12 | No product publication | Existing 14 schemas, 13 canonical fixture files, manifest membership/order, five OpenAPI paths, five Flyway migrations, API/controller/repository/database behavior, canonical methodology/outcome rows, and web source remain unchanged. No API key, account, paid plan, domain, provider license, or network access is required. P5 owns real primary-venue close, calendar, corporate-action, reference-data, display/storage/derived-data rights, and credentials. |

## Required endpoint-price golden and negative tests

- Exact canonical string bytes, length 2259, independently recomputed SHA-256,
  fixed returned digest, and defensive byte copies are asserted. Reflection or
  equivalent exact-shape checks lock all ten production types, enum order,
  request/context/result components, sealed variants, and 16 reason values.
- Gate vectors independently cover catalog/binding `availableAt` and
  `capturedAt` immediately before, at, and one microsecond after
  `evaluationAsOf`; catalog identity mismatch; endpoint close before/at/after
  evaluation; empty candidates; and candidates future by either timestamp.
- Full-result equality—not reason-only equality—proves empty equals all-future
  wrong candidates, and one known exact candidate equals the same request plus
  future exact, future wrong, and future duplicate candidates.
- Every known-candidate mismatch reason is mutation-sensitive. Multi-mismatch
  rows and reversed list order lock precedence. One valid known row resolves;
  two distinct valid known rows and the same known row repeated both produce
  exact ambiguity.
- Exact source/catalog/session/event/provenance preservation, scale-equivalent
  positive price, exact `NUMERIC(38,12)` maximum, and all invalid decimal/time/
  identity constructors are covered. Each disallowed price field, adjustment
  basis, and corporate-action continuity value fails with the exact reason.
- Locale/default-timezone replay restores globals in `finally`; repository CI
  locks exact files/imports, narrow ADR-010/ADR-015 reverse edges, no provider
  or runtime wiring, and unchanged canonical product surfaces.

## Point-in-time target-error slice boundary

- The calculator consumes one complete ADR-014 endpoint-price resolution and
  nullable target evidence. It never selects a target or endpoint observation.
- A target is known only if both of its PIT timestamps are not after the
  endpoint context's `evaluationAsOf`; null and future target evidence are
  intentionally indistinguishable and invisible to the output.
- Formula is exactly `abs(target-actual)/actual`, using actual endpoint price as
  denominator and exactly one scale-12 `HALF_EVEN` division. Output is a decimal
  ratio, not a display percent.
- Missing target and endpoint states compose without flattening the exact
  nested endpoint reason. Complete evidence must match basis, asset, primary
  venue, currency, and adjustment basis before calculation.
- This disconnected leaf does not invoke target-hit/directional-win, activate a
  canonical scoring methodology, persist an outcome, or publish a metric.

## Point-in-time target-error contract gate

| ID | Check | Expected result |
| --- | --- | --- |
| P3-TE01 | Exact file/package surface | Production package `com.wallstreetreceipts.api.domain.outcome.targeterror` contains exactly `TargetErrorCalculator.java`, `TargetErrorInput.java`, `TargetErrorPolicyVersion.java`, `TargetErrorResult.java`, and `TargetPriceEvidence.java`. Tests add exactly matching `TargetErrorCalculatorGoldenTest.java`; no orchestrator/provider/controller/repository/helper file is added. |
| P3-TE02 | Exact policy identity | `TargetErrorPolicyVersion` contains exactly `ACTUAL_DENOMINATOR_SCALE_12_HALF_EVEN_V1`. Its canonical definition is the exact ADR-015 single-line 1942-byte ASCII/UTF-8 sequence and fixed lowercase SHA-256 `31ca30555549f670e3c22d98ead16f7a02bfad198f36532effaf4a4b6931d074`. Returned bytes are defensive and every calculation context echoes the digest. |
| P3-TE03 | Exact endpoint input | Input accepts exactly one ADR-014 endpoint resolution using `OFFICIAL_PRIMARY_VENUE_CLOSE_SPLIT_ADJUSTED_V1` and hash `37e37aba9302d77366cef4129f77a82b7ccb2f1937bfffc0315ea8d0bc6b1f76`, plus nullable target evidence. No raw price, favorable extreme, return, side, call aggregate, provider, clock, or fallback field exists. |
| P3-TE04 | Target evidence and PIT | Target evidence contains exact evidence ID, `OutcomeBasis`, asset, primary venue, currency, approved adjustment basis, target, available/captured times, and provenance. It requires `basis.eventTime <= availableAt <= capturedAt`, microsecond times, canonical identities, and positive exact `NUMERIC(38,12)`. Known-as-of requires both timestamps `<= endpoint.evaluationAsOf`; null and future target produce equal full results. |
| P3-TE05 | Missing-state truth table | Missing target plus unavailable endpoint yields `TARGET_AND_ENDPOINT_PRICE_UNAVAILABLE` with exact nested endpoint reason; missing target with resolved endpoint yields `TARGET_MISSING_AS_OF` and null nested reason; known target with unavailable endpoint yields `ENDPOINT_PRICE_UNAVAILABLE` with exact nested reason. Future target is missing before identity checks, and no endpoint reason is dropped or replaced. |
| P3-TE06 | Identity precedence | With known target and resolved endpoint, exact precedence is basis, asset, primary venue, currency, then adjustment basis. Currency must match exactly with no FX. Multi-mismatch vectors return the first exact reason; no normalization, alias, fallback, or alternate evidence is inferred. |
| P3-TE07 | Exact formula | Complete matching input computes exactly `target.subtract(actual).abs().divide(actual, 12, HALF_EVEN)`: actual endpoint price is the sole denominator, absolute target difference is the numerator, exactly one division/rounding occurs, and output is a decimal ratio. Asymmetric actual-denominator vectors, exact equality, and two opposite half-even tie parities lock the rule. |
| P3-TE08 | Decimal/output boundary | Inputs are positive and exact `NUMERIC(38,12)`. Available output is nonnegative, exact scale 12, and precision at most 38. A value whose one permitted rounding produces precision 39 returns `OUTPUT_NOT_REPRESENTABLE`; it is never clipped, rescaled again, returned at another scale, or converted through float/double. |
| P3-TE09 | Closed result and constructor ownership | `TargetErrorResult` permits exactly `Available(context,targetError)` and `Unavailable(context,reason,endpointReason)` with the nine ADR-015 reasons in exact enum order. Context contains exact target-error policy identity and the complete endpoint resolution only. Public constructors validate local shape/hash/endpoint-reason consistency; only `TargetErrorCalculator` attests target PIT visibility, precedence, identity, formula, and representability. |
| P3-TE10 | Determinism and purity | Equal input returns equal output regardless of clock, locale, default timezone, original decimal scale where valid, prior calls, environment, thread, or random state. Production imports only deterministic Java/domain types; no target-hit/directional-win invocation, return/window calculation, JSON, Spring, persistence, repository, network, provider, scheduler, reflection, environment, or mutable global state exists. |
| P3-TE11 | Exact reverse graph | Only the five target-error production types consume this new policy/result/evidence surface. They may reference only ADR-010 `OutcomeBasis` and the exact ADR-014 endpoint policy/resolution/adjustment-basis types required by their components and consistency checks. No existing product runtime class invokes the calculator or consumes its result. |
| P3-TE12 | No product publication | No schema, canonical fixture, manifest member, JSON golden, OpenAPI path, Flyway migration, database row, API/controller/repository/provider behavior, methodology activation, input fingerprint, outcome mutation, or web source is added. No API key/account/vendor/license/network access is needed for the leaf; P5 owns real target and endpoint data acquisition and rights. |

## Required target-error golden and negative tests

- Exact canonical string bytes, length 1942, independently recomputed SHA-256,
  fixed returned digest, and defensive byte copies are asserted. Reflection or
  equivalent exact-shape checks lock all five production types, request/result
  records, enum order, sealed variants, and nine reason values.
- The exact three-row missing-state truth table is crossed with all 16 endpoint
  reasons where applicable, preserving each nested reason. Full-result equality
  proves null target equals a target future by available time and one future by
  captured time; future mismatches must never leak into output/context.
- Basis, asset, venue, currency, and adjustment-basis mismatches are asserted
  independently and in multi-mismatch/precedence vectors. Original/correction
  basis identity and event time are exact; no matching by call ID alone exists.
- Formula vectors cover zero error, target above actual, target below actual,
  asymmetric actual denominators, scale-equivalent inputs, exact maximums,
  exactly one division, output scale 12, and two half-even ties whose retained
  digit is respectively even and odd.
- Rounded output overflow—not merely invalid input—returns exact
  `OUTPUT_NOT_REPRESENTABLE`. Null/malformed identity/time/policy/hash, target
  before basis event, nonpositive/scale-13/precision-39 target, invalid direct
  result shape, and wrong nested endpoint reason fail closed.
- Locale/default-timezone replay restores globals in `finally`; repository CI
  locks exact files/imports, formula/rounding markers, reverse isolation, no
  runtime invocation, and unchanged canonical product surfaces.

## Point-in-time basis-event/endpoint price-pair slice boundary

- The selector consumes one complete ADR-014 endpoint-price resolution plus
  caller-supplied basis-price and independent adjustment-evidence candidates.
  It obtains no price, calendar, action, reference, or provider data itself.
- Basis price means the exact source-recorded price at the original/correction
  `OutcomeBasis.eventTime`, not prior close, nearest price, interpolation, or a
  derived session price. Endpoint remains the official ADR-014 close.
- Both prices share exact asset, primary venue, currency, source/revision, and
  split/reverse-split endpoint-share/dividend-unadjusted basis. Independent
  adjustment evidence binds the two observation/provider-event identities and
  exact time coverage.
- Basis and adjustment evidence are filtered by both `availableAt` and
  `capturedAt` before identity, mismatch, or cardinality reasoning. Future
  evidence cannot alter a result.
- The leaf resolves exactly one known basis and one known adjustment record or
  returns explicit unavailable evidence. It calculates no return and publishes
  no fixture, API, database, provider, or web value.

## Point-in-time price-pair contract gate

| ID | Check | Expected result |
| --- | --- | --- |
| P3-PP01 | Exact file/package surface | Production package `com.wallstreetreceipts.api.domain.outcome.pricepair` contains exactly `BasisPriceField.java`, `BasisPriceObservation.java`, `PricePairAdjustmentEvidence.java`, `AssetReturnPricePairPolicyVersion.java`, `AssetReturnPricePairRequest.java`, `AssetReturnPricePairResolution.java`, and `AssetReturnPricePairSelector.java`. Tests add exactly matching `AssetReturnPricePairSelectorGoldenTest.java`; no provider/controller/repository/helper file is added. |
| P3-PP02 | Exact policy identity | `AssetReturnPricePairPolicyVersion` contains exactly `SOURCE_RECORDED_BASIS_EVENT_TO_OFFICIAL_ENDPOINT_PRICE_PAIR_V1`. Its canonical definition is the exact ADR-016 single-line 4655-byte ASCII/UTF-8 sequence and fixed lowercase SHA-256 `895e4bc97ebb3a92b80f2c58e2d28abb94440eeca963046ee755fa98825f4887`. It locks the complete basis-observation and adjustment-evidence field lists plus selected full-record preservation. Returned bytes are defensive and every context echoes the digest. |
| P3-PP03 | Exact endpoint input | Request accepts only one complete ADR-014 endpoint resolution using `OFFICIAL_PRIMARY_VENUE_CLOSE_SPLIT_ADJUSTED_V1` and hash `37e37aba9302d77366cef4129f77a82b7ccb2f1937bfffc0315ea8d0bc6b1f76`, plus immutable non-null copies of both candidate lists. No raw endpoint, horizon inference, provider, clock, or fallback field exists. |
| P3-PP04 | Basis evidence | `BasisPriceObservation` contains exact observation/provider-event identity, `OutcomeBasis`, asset, venue, currency, price-source ID/revision, provenance, `BasisPriceField`, adjustment basis, continuity, observed/available/captured times, and price. Identities are canonical, times are microsecond precision with `observedAt <= availableAt <= capturedAt`, and price is positive exact `NUMERIC(38,12)` without replacing or rescaling the stored value. |
| P3-PP05 | Adjustment evidence | `PricePairAdjustmentEvidence` contains exact evidence/provider-event identity, basis, asset, primary venue, currency, adjustment-source ID/revision, provenance, both observation/provider-event links, coverage bounds, adjustment basis, continuity, available/captured times. It requires `coverageStartsAt <= coverageEndsAt <= availableAt <= capturedAt`. |
| P3-PP06 | PIT and missing truth table | `evaluationAsOf` is inherited from the endpoint context. Each candidate is known only when both PIT timestamps are `<= evaluationAsOf`, with future rows filtered first. Missing basis plus unavailable endpoint yields `BASIS_AND_ENDPOINT_PRICE_UNAVAILABLE` and exact nested endpoint reason; basis-only missing yields `BASIS_PRICE_MISSING_AS_OF`; endpoint-only missing yields `ENDPOINT_PRICE_UNAVAILABLE` and the exact reason. Full-result equality proves empty equals all-future and known exact evidence is unchanged by future exact/wrong/duplicate evidence. |
| P3-PP07 | Basis precedence/cardinality | Known basis candidates are checked in exact order for basis, asset, primary venue, currency, source ID/revision, exact basis-event time, exact source-recorded field, adjustment basis, and continuity. All known rows participate in gates; exactly one valid row resolves and repeated/same or distinct valid rows are `BASIS_PRICE_AMBIGUOUS`. No dedupe, alias, tolerance, preference, or fallback exists. |
| P3-PP08 | Adjustment precedence/cardinality | After one basis resolves, known adjustment candidates are checked in exact order for basis, asset, venue, currency, basis observation/provider-event link, endpoint observation/provider-event link, exact coverage, adjustment basis, and continuity. Exactly one resolves; zero is `ADJUSTMENT_EVIDENCE_MISSING_AS_OF`, and same/distinct duplicates are `ADJUSTMENT_EVIDENCE_AMBIGUOUS`. |
| P3-PP09 | Exact price/identity semantics | Basis field is exactly `SOURCE_RECORDED_BASIS_EVENT_PRICE`; `INDICATIVE_OR_OTHER` is unavailable. Asset/primary venue/currency and endpoint binding price source/revision match exactly with no FX. Adjustment is exactly split/reverse-split to endpoint-share basis and dividend-unadjusted; only continuous split/reverse-split lineage is accepted. Observation and provider-event links plus coverage endpoints match exactly. Prior close, nearest price, interpolation, unsupported-action inference, and fallback are absent. |
| P3-PP10 | Closed result and constructor ownership | `AssetReturnPricePairResolution` permits exactly `Resolved(context,basisObservation,adjustmentEvidence)` and `Unavailable(context,reason,endpointReason)` with the 24 ADR-016 reasons in exact order. Context contains only exact policy identity and the complete endpoint resolution. Public constructors validate locally decidable consistency; only `AssetReturnPricePairSelector` attests request membership, PIT filtering, precedence, and cardinality. |
| P3-PP11 | Determinism, purity, and reverse graph | Equal requests replay equally regardless of clock, locale, timezone, input order where precedence is invariant, environment, thread, or random state. Production imports only deterministic Java and exact horizon/observation domain types. Only the seven pair types consume raw basis/adjustment candidates or invoke `select`; only the later four ADR-017 types consume the pair resolution. No target/directional calculator, provider, repository, framework, JSON, reflection, network, scheduler, or mutable global state is present. |
| P3-PP12 | No product publication/external credential | Existing 14 schemas, 13 fixture files and manifest order, five OpenAPI paths, five Flyway migrations, methodology/outcome rows, API/controller/repository/database behavior, and web source remain unchanged. No API key, account, paid plan, vendor license, named secret, or network access is required. P5 owns the chosen vendor's historical event-time/intraday price entitlement, calendar/reference/corporate-action rights, display/storage/derived/redistribution terms, adapter, and only then a scoped secret. |

## Required price-pair golden and negative tests

- Assert exact canonical bytes, length 4655, independently recomputed SHA-256,
  fixed digest, defensive byte copies, exact seven-type surface, enum order,
  record components, sealed variants, and all 24 reason values.
- Cross basis/endpoint missing states and preserve every nested ADR-014 reason.
  Full-result equality—not reason-only equality—proves empty/all-future equality
  and that future exact, wrong, and duplicate basis or adjustment candidates
  cannot affect a known result.
- Mutation-sensitive vectors cover every basis and adjustment mismatch in exact
  precedence, multi-mismatch rows in reversed list order, missing candidates,
  same-object and distinct ambiguity, and exact selected evidence preservation.
- Cover original and correction basis identity/event time, one-microsecond PIT
  boundaries for both timestamps, exact observation/provider links and coverage,
  every disallowed basis field/adjustment/continuity value, positive numeric
  maximum, invalid scale/precision/nonpositive price, canonical identities,
  time order, immutable copies, and contradictory direct result construction.
- Replay under changed locale/default timezone restores state in `finally`;
  repository CI locks exact imports/reverse edges, no inference/provider/runtime
  wiring, and unchanged product surfaces.

## Signed asset-return slice boundary

- The calculator consumes one complete ADR-016 price-pair resolution and never
  selects a price, adjustment record, calendar, horizon, or source.
- Formula is exactly `(endpoint-basis)/basis`, using basis price as denominator
  and exactly one scale-12 `HALF_EVEN` division. Output is a signed decimal
  ratio, not a percent.
- Pair unavailability preserves the exact nested reason. Output overflow is
  explicit unavailable evidence; exact -1 is valid and below -1 is invalid.
- The disconnected leaf invokes no directional/target calculator, methodology,
  persistence, API, provider, or web publication.

## Signed asset-return contract gate

| ID | Check | Expected result |
| --- | --- | --- |
| P3-AR01 | Exact file/package surface | Production package `com.wallstreetreceipts.api.domain.outcome.assetreturn` contains exactly `AssetReturnPolicyVersion.java`, `AssetReturnInput.java`, `AssetReturnResult.java`, and `AssetReturnCalculator.java`. Tests add exactly matching `AssetReturnCalculatorGoldenTest.java`; no orchestrator/provider/controller/repository/helper file is added. |
| P3-AR02 | Exact policy identity | `AssetReturnPolicyVersion` contains exactly `SIGNED_BASIS_DENOMINATOR_SCALE_12_HALF_EVEN_V1`. Its canonical definition is the exact ADR-017 single-line 1011-byte ASCII/UTF-8 sequence and fixed lowercase SHA-256 `e5e61c4adcd6567bfc76f73114499578f09de2254dc39a2553f3c0e2eaf03486`. Returned bytes are defensive and every calculation context echoes the digest. |
| P3-AR03 | Exact pair input | Input contains only policy version and one ADR-016 pair resolution using `SOURCE_RECORDED_BASIS_EVENT_TO_OFFICIAL_ENDPOINT_PRICE_PAIR_V1` and exact hash `895e4bc97ebb3a92b80f2c58e2d28abb94440eeca963046ee755fa98825f4887`. It has no raw price, side, call, target, horizon, provider, clock, or fallback field. |
| P3-AR04 | Unavailability composition | Any unavailable pair returns `PRICE_PAIR_UNAVAILABLE` with the exact nested pair reason. A missing/ambiguous/mismatched pair is never zero, -1, false, loss, or `OUTPUT_NOT_REPRESENTABLE`; a resolved pair never carries a pair reason. |
| P3-AR05 | Exact formula | Complete pair computes exactly `endpoint.subtract(basis).divide(basis, 12, HALF_EVEN)`: one subtraction, basis as the sole denominator, and exactly one division/rounding. There is no absolute value, target/endpoint denominator, percent conversion, intermediate or second rounding, tolerance, float/double, or fallback. |
| P3-AR06 | Signed decimal boundary | Available output has exact scale 12, precision at most 38, and value at least -1. Exact -1, zero, positive and negative values, scale-equivalent inputs, and both half-even tie parities are locked. Rounded precision-39 output becomes `OUTPUT_NOT_REPRESENTABLE`; it is not clipped or rescaled. |
| P3-AR07 | Closed result and constructor ownership | `AssetReturnResult` permits exactly `Available(context,assetReturn)` and `Unavailable(context,reason,pricePairReason)`, with reasons exactly `PRICE_PAIR_UNAVAILABLE`, `OUTPUT_NOT_REPRESENTABLE` in order. Context contains only exact policy identity and complete pair resolution. Public constructors enforce local state/hash/reason/decimal invariants; only the calculator attests formula execution. |
| P3-AR08 | Determinism and purity | Equal input returns equal output regardless of clock, locale, timezone, original valid decimal scale, environment, thread, random state, or prior calls. Production imports only BigDecimal/rounding/Objects and exact price-pair/endpoint types; no directional-win, target-hit/error, provider, repository, JSON, framework, persistence, reflection, network, scheduler, or mutable global state exists. |
| P3-AR09 | Exact reverse graph | Only the four asset-return production types consume the ADR-017 policy/result/input surface. They may consume only exact ADR-016 policy/resolution plus the nested endpoint resolution needed to read a resolved endpoint price. No existing calculator or product runtime invokes `AssetReturnCalculator.calculate` or consumes `AssetReturnResult`. |
| P3-AR10 | No publication/external credential | No schema, fixture, manifest, JSON golden, OpenAPI, Flyway, database, API/controller/repository/provider behavior, methodology activation, fingerprint, outcome mutation, or web source is added. The leaf needs no key/account/license/network access; P5 owns real evidence acquisition and rights before runtime integration. |

## Required signed asset-return golden and negative tests

- Assert exact canonical bytes, length 1011, independently recomputed SHA-256,
  fixed digest, defensive byte copies, exact four-type surface, enum/reason order,
  record components, sealed variants, modifiers, and private calculator
  constructor.
- Propagate every one of the 24 pair unavailable reasons with full nested-reason
  equality. Direct constructors reject contradictory resolved/unavailable state,
  wrong hash/version, missing components, wrong nested reason, scale other than
  12, precision above 38, and value below -1.
- Formula vectors cover endpoint above/equal/below basis, exact -1 boundary,
  scale-equivalent inputs, asymmetric denominator evidence, exact one divide,
  scale 12, both half-even tie parities, precision-38 adjacent success, and a
  rounded precision-39 unavailable result.
- Locale/default-timezone replay restores globals in `finally`; repository CI
  locks formula/imports/reverse isolation, no downstream calculator invocation,
  and unchanged product/data surfaces.

## Deferred work and implementation order

1. Invoke calculators only through a later orchestrator consuming the closed
   routing evidence, after target eligibility, window inclusivity, point-in-time
   input identity, and unavailable-state composition are locked.
2. Add MFE/MAE after full-window completeness and bullish/bearish sign rules;
   add alpha/sector alpha last, after benchmark/sector identity and corporate-
   action-adjusted return policy exist.
3. Persist or expose a non-null metric only with a canonical versioned input
   fixture, reproducible methodology definition/hash, input fingerprint, golden
   test, append-only lineage, and schema/domain/database completeness matrix.

Leaderboard aggregates, sample confidence, ranking publication, schedulers,
provider integration, historical bars, and real/licensed market data remain
outside this slice.
