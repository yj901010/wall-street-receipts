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
leaves are also complete. Point-in-time target-hit input eligibility and the
PIT attested causal-window favorable-extreme selector are now complete; no
runtime outcome or product surface is published. The supplied-leaf target-hit
orchestration is also complete: it invokes the primitive only for exact Ready
plus Resolved while keeping every other typed leaf branch unchanged. The
supplied-leaf directional-win orchestration is now complete: it correlates all
four non-null supplied fields before branching, preserves neutral and all
unavailable return meanings, and invokes the primitive only for directional
plus available return evidence. The supplied-leaf directional-win readiness
classification is also complete: it distinguishes only the exact endpoint-
not-reached chain from every other unavailable chain and publishes no canonical
lifecycle state. The supplied-leaf target-error readiness classification is
also complete at its source-local boundary: available target error settles,
only the exact endpoint-not-reached chain awaits, and the other 38 constructible
unavailable shapes remain evidence-unavailable. It likewise publishes no
canonical lifecycle state. The supplied-leaf target-hit readiness
classification is also complete at its source-local boundary: the one
Available and three permanent NotApplicable shapes settle, the sole Pending
shape awaits its endpoint, and all 36 evidence-unavailable shapes preserve
their complete typed source. It publishes no canonical lifecycle state.

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

## Point-in-time target-hit input eligibility boundary

- The resolver consumes caller-supplied basis forecast terms, one complete
  ADR-013 calculator-side route when known, nullable normalized ADR-015 target
  evidence, one ADR-010 strict-horizon resolution, catalog PIT evidence, and an
  explicit evaluation as-of. It obtains none of them.
- A known `TargetDisposition.Present` requires target evidence. A known
  `TargetDisposition.Absent` is explicit not-applicable evidence only when no
  normalized target is PIT-visible; visible normalized target is an explicit
  conflict. Null/future source terms are missing evidence and can never be
  reinterpreted as target absence.
- Exact original/correction basis, source direction/route, normalized target
  basis/currency, horizon, and catalog identity are required. Future evidence
  is filtered before any identity, reason, or readiness decision.
- Readiness means only that a later full-window selector may seek the favorable
  high or low. This slice selects no market observation, invokes no calculator,
  and publishes no metric or outcome.

## Point-in-time target eligibility contract gate

| ID | Check | Expected result |
| --- | --- | --- |
| P3-TI01 | Exact file/package surface | Production package `com.wallstreetreceipts.api.domain.outcome.targeteligibility` contains exactly `TargetEligibilityPolicyVersion.java`, `BasisForecastTermsEvidence.java`, `TargetEligibilityRequest.java`, `TargetEligibilityResolution.java`, and `TargetEligibilityResolver.java`. Tests add exactly `TargetEligibilityResolverGoldenTest.java`; no calculator, orchestrator, provider, controller, repository, or helper is added. |
| P3-TI02 | Exact policy identity | `TargetEligibilityPolicyVersion` contains exactly `POINT_IN_TIME_TARGET_HIT_INPUT_READINESS_V1`. Its canonical definition is the exact ADR-018 single-line 3862-byte ASCII/UTF-8 sequence and fixed lowercase SHA-256 `a6b4c9f4e4d29b5f1a9b0c300e2d7b9505318c708dfb0ad0e88f71324cf65465`. It locks complete field lists, PIT rules, identity/evaluation precedence, selected evidence, branch clearing, variants, reasons, and no-inference boundaries. Returned bytes are defensive and every context echoes the digest. |
| P3-TI03 | Source terms evidence | `BasisForecastTermsEvidence` preserves exact evidence/provider-event identity, `OutcomeBasis`, asset, source `CallDirection`, sealed `TargetDisposition`, available/captured times, and provenance. `TargetDisposition.Present` contains positive exact `NUMERIC(38,12)` source target, currency, and nullable target date; `TargetDisposition.Absent` is an empty affirmative no-target record. Times are microsecond precise and ordered from basis event through availability and capture. |
| P3-TI04 | PIT invisibility | Terms, normalized target, and catalog evidence are usable only when every applicable availability/capture timestamp is `<= evaluationAsOf`. Future evidence is removed before all identity and reason gates and is absent from returned evidence. Full-result equality proves null equals future for each nullable evidence input. |
| P3-TI05 | Exact basis and route | Terms basis must equal the complete ADR-010 horizon basis. A known route must preserve the exact ADR-011 V1 source direction from the same visible terms; route absence and direction mismatch are distinct unavailable reasons. No call-ID-only match, string/ordinal mapping, default side, or route reconstruction exists. |
| P3-TI06 | Explicit target semantics | Known directional `TargetDisposition.Present` terms require known normalized `TargetPriceEvidence` with exact basis, asset, and currency. Known `TargetDisposition.Absent` plus a PIT-visible normalized target is `TARGET_STATE_CONFLICT` immediately after route identity and before all not-applicable branches; the complete conflicting target is preserved. An absent target with null/future normalized evidence becomes `TARGET_ABSENT`, never missing or conflict. A non-null source target date on the directional-present path becomes `TARGET_DATE_SEMANTICS_UNSUPPORTED`; V1 performs no expiry, timezone conversion, window shortening, or default-duration inference. Source and normalized target values are preserved separately with no numeric-equality inference, and previous target is not an input. |
| P3-TI07 | Neutral applicability | A non-directional ADR-013 route becomes `NON_DIRECTIONAL`; absent target plus non-directional route becomes `TARGET_ABSENT_AND_NON_DIRECTIONAL` only when no normalized target is PIT-visible. Visible contradictory target evidence takes `TARGET_STATE_CONFLICT` precedence. Neither not-applicable branch constructs a calculator side or Boolean, and neither is false, miss, loss, incomplete, or cancelled. |
| P3-TI08 | Horizon readiness | After earlier applicability and target/catalog gates pass, a resolved strict horizon whose endpoint close is after `evaluationAsOf` is exactly `Pending(HORIZON_NOT_REACHED_AS_OF)` and equality is ready; an incomplete strict horizon preserves exact `FIRST_ELIGIBLE_SESSION_MISSING` or `HORIZON_ENDPOINT_SESSION_MISSING` evidence. No guessed endpoint, alternate calendar, prior close, or readiness fallback exists. |
| P3-TI09 | Catalog evidence | Ready and pending branches require PIT-visible catalog evidence whose calendar ID/revision exactly matches the strict-horizon context. Missing/future and mismatched catalog evidence remain separate unavailable states; no latest revision or provider preference is inferred. |
| P3-TI10 | Closed result | `TargetEligibilityResolution` permits exactly `ReadyForWindowEvidence`, `Pending`, `NotApplicable`, and `Unavailable`. Context contains exact policy identity, complete strict-horizon resolution, and evaluation as-of. Branch evidence contains only the visible terms, route, target, and catalog selected for that branch. Direct constructors enforce only their locally decidable policy, visibility, completeness, and reason/evidence consistency; the resolver owns reason correctness for the complete request. |
| P3-TI11 | Resolver ownership and determinism | Equal requests produce equal results regardless of clock, locale, timezone, input decimal scale where valid, environment, thread, random state, or prior calls. Public constructors own local invariants only; the resolver alone attests PIT filtering, precedence, cross-evidence identity, and readiness. |
| P3-TI12 | No calculation or lifecycle inference | The slice does not invoke target-hit, directional-win, asset-return, or target-error; select a high/low/bar/price; infer previous target, target-date expiry, latest correction, cancellation, or outcome completeness; activate a methodology; fingerprint; persist; aggregate; rank; or publish. Existing schemas, fixtures, manifest, OpenAPI, Flyway, database, API/provider behavior, and web source remain unchanged. |
| P3-TI13 | External-data boundary | No key, account, paid plan, domain, license, named secret, or network access is needed. Before P5 supplies non-DEMO forecast terms, targets, calendars, or window observations, the selected vendor's historical entitlements and display/storage/derived/redistribution rights must be established; only then may a reviewed adapter define a scoped secret. |

## Required target eligibility golden and negative tests

- Lock the exact canonical policy bytes, length, independent SHA-256, fixed
  returned digest, defensive bytes, five-type file surface, record components,
  modifiers, sealed variants, and exact reason enum orders.
- Cover original and correction bases; every canonical source direction and
  exact directional/non-directional route; present and absent dispositions;
  absent plus neutral; resolved reached, resolved unreached, and both incomplete
  horizon reasons; and exact catalog identity.
- Prove full-result equality for null versus future terms, target, and catalog
  evidence using both PIT timestamps and one-microsecond boundaries. Future
  wrong or otherwise decisive evidence must not leak into context, branch
  evidence, or reason selection.
- Cross `TargetDisposition.Absent` with null, future, and PIT-visible normalized
  target evidence for both directional and neutral routes. Null/future must
  retain exact not-applicable results; visible target must preserve the complete
  conflicting record and return `TARGET_STATE_CONFLICT` before every
  not-applicable reason.
- Mutate basis, route direction, target basis, asset, currency, calendar ID/revision,
  target date, reason, nested horizon reason, and each branch component. Null,
  contradictory present/absent state, invalid identity/time, nonpositive or
  nonrepresentable source target, and wrong policy/hash must fail closed.
- Replay under changed locale/default timezone and restore globals in `finally`;
  repository CI locks source imports/reverse edges, no calculator/orchestrator
  invocation, and unchanged product/data surfaces.

## Point-in-time causal-window favorable-extreme boundary

- The selector consumes only one complete ADR-018
  `ReadyForWindowEvidence`, nullable PIT window-price binding evidence, and
  caller-supplied full-window high/low candidates. Evaluation as-of, target,
  route, strict horizon, and catalog are inherited rather than resupplied.
- The causal economic set is exactly primary-venue regular-session
  observations belonging to the ordered horizon sessions with
  `observation.time > basis.eventTime` and
  `observation.time <= endpointSession.closesAt`. The lower bound is exclusive
  and upper bound inclusive. A daily/session high containing pre-call prices is
  not equivalent, and the endpoint close is never a high/low fallback.
- One candidate attests both high and low over that exact union. Bullish selects
  the stored high; bearish selects the stored low. Original valid decimal scale
  is preserved with no rounding, rescaling, raw maximum/minimum aggregation, or
  target comparison.
- `EXACT_CAUSAL_WINDOW_SESSION_UNION` is an upstream source/provider
  completeness attestation. V1 checks its identity and metadata but does not
  independently prove raw tick/bar coverage or define no-trade, halt, auction,
  bar-straddle, or correction-sequence semantics.

## Point-in-time favorable-extreme contract gate

| ID | Check | Expected result |
| --- | --- | --- |
| P3-FE01 | Exact file/package surface | Production package `com.wallstreetreceipts.api.domain.outcome.favorableextreme` contains exactly `FavorableExtremePolicyVersion.java`, `WindowPriceBinding.java`, `FullWindowHighLowObservation.java`, `FavorableExtremeRequest.java`, `FavorableExtremeResolution.java`, and `FavorableExtremeSelector.java`. Tests add exactly `FavorableExtremeSelectorGoldenTest.java`; no raw aggregator, orchestrator, provider, controller, repository, or helper is added. |
| P3-FE02 | Exact policy identity | `FavorableExtremePolicyVersion` contains exactly `POINT_IN_TIME_ATTESTED_CAUSAL_WINDOW_HIGH_LOW_V1`. Its canonical definition is the exact ADR-019 single-line 4633-byte ASCII/UTF-8 sequence and fixed lowercase SHA-256 `e3a0e93030c8f09ae5398bf6df0f2e28eec14b0a31f5bea240fc78f2412c2463`. Returned bytes are defensive and every context echoes the digest. |
| P3-FE03 | Exact request source | Request fields are exactly policy version, one ADR-018 `ReadyForWindowEvidence` using hash `a6b4c9f4e4d29b5f1a9b0c300e2d7b9505318c708dfb0ad0e88f71324cf65465`, nullable binding, and immutable candidates. Evaluation as-of, resolved horizon, side, target, and catalog come only from readiness. Pending, not-applicable, unavailable, incomplete, or competing raw inputs cannot enter. |
| P3-FE04 | Binding PIT and identity | Binding preserves exact identity/revision, asset, primary venue, currency, price-source ID/revision, availability/capture, and provenance. It is visible only when both PIT timestamps are `<= evaluationAsOf`. After the target-adjustment-basis gate passes, null and future binding are full-result-equal `BINDING_NOT_KNOWN_AS_OF` evidence and future data is never echoed. Visible asset, venue, and currency mismatches are distinct ordered reasons. |
| P3-FE05 | Exact causal window | Each candidate must bind the exact outcome basis and named horizon, ordered strict-horizon session IDs, catalog ID/revision, `lowerBound == basis.eventTime` with `EXCLUSIVE`, and `upperBound == endpointSession.closesAt` with `INCLUSIVE`. This encodes only regular-session observations in those sessions where `t > basis.eventTime && t <= endpoint close`; off-hours, gaps, pre-call first-session values, prior/next windows, and inferred calendars are excluded. |
| P3-FE06 | Complete observation identity | Candidate preserves observation/provider-event identity, asset, venue, currency, price-source ID/revision, provenance, catalog and ordered window identity, field/completeness, adjustment/continuity, PIT timestamps, and the original high/low pair. Both prices are positive exact `NUMERIC(38,12)` values, low is `<=` high, bounds are ordered, and availability cannot precede the upper bound. |
| P3-FE07 | Candidate PIT invisibility | A candidate is known only when both availability and capture are `<= evaluationAsOf`. Future exact, wrong, incomplete, or duplicate candidates are invisible before all mismatch, output, and cardinality decisions. Empty/all-future candidates are exactly `OBSERVATION_MISSING_AS_OF`. |
| P3-FE08 | All-candidate poison rule | Every PIT-visible request candidate must pass every identity gate. One valid candidate does not hide another known invalid candidate. Mismatch checks use fixed reason precedence independent of candidate order; no candidate filtering, source preference, latest-revision choice, deduplication, or fallback exists. |
| P3-FE09 | Field, completeness, and continuity | Field must be exactly `PRIMARY_VENUE_REGULAR_SESSION_CAUSAL_WINDOW_HIGH_LOW_PAIR`; coverage must be `EXACT_CAUSAL_WINDOW_SESSION_UNION`; target and observation basis must be split/reverse-split adjusted to endpoint-share basis and dividend-unadjusted; continuity must be `SPLIT_REVERSE_SPLIT_CONTINUOUS`. Indicative/other fields, partial/unknown coverage, FX, merger, spin-off, delisting, special-distribution, unknown continuity, or endpoint-close substitution fail closed. |
| P3-FE10 | Exact cardinality and ambiguity | Exactly one PIT-visible candidate may pass all gates. More than one is `OBSERVATION_AMBIGUOUS`, including the same object twice, distinct equal records, or equal high/low values from different candidates. No record is deduplicated. A single observation with `windowHigh == windowLow` is valid. |
| P3-FE11 | Exact side selection | ADR-013 directional routing is inherited through readiness. Bullish returns `FavorableExtreme(HIGH, observation.windowHigh)` and bearish returns `FavorableExtreme(LOW, observation.windowLow)`. The exact selected `BigDecimal` object/value and valid scale are preserved; there is no rounding, rescaling, tolerance, close fallback, high/low inversion, or calculator invocation. |
| P3-FE12 | Exact result and reason order | Result variants are exactly `Resolved(context,evidence,favorableExtreme)` and `Unavailable(context,evidence,reason)`. Context preserves policy identity and complete readiness; evidence preserves only PIT-visible binding/candidates through the deciding gate. Nested public constructors validate local consistency only for their supplied evidence; only `FavorableExtremeSelector.select(request)` attests complete-request membership, PIT filtering, poisoning, and cardinality. Reasons are exactly, in order: `TARGET_ADJUSTMENT_BASIS_UNSUPPORTED`, `BINDING_NOT_KNOWN_AS_OF`, `BINDING_ASSET_MISMATCH`, `BINDING_PRIMARY_VENUE_MISMATCH`, `BINDING_CURRENCY_MISMATCH`, `OBSERVATION_MISSING_AS_OF`, `BASIS_MISMATCH`, `HORIZON_MISMATCH`, `ASSET_MISMATCH`, `PRIMARY_VENUE_MISMATCH`, `CURRENCY_MISMATCH`, `SOURCE_MISMATCH`, `CATALOG_MISMATCH`, `SESSION_WINDOW_MISMATCH`, `LOWER_BOUND_MISMATCH`, `UPPER_BOUND_MISMATCH`, `BOUNDARY_CONVENTION_MISMATCH`, `PRICE_FIELD_MISMATCH`, `WINDOW_COMPLETENESS_UNAVAILABLE`, `ADJUSTMENT_BASIS_MISMATCH`, `CORPORATE_ACTION_CONTINUITY_UNAVAILABLE`, `OBSERVATION_AMBIGUOUS`. Ambiguity runs only after all 21 earlier gates. |
| P3-FE13 | Attestation boundary | Resolved means one upstream-attested exact-window pair selected by side. It does not mean this repository aggregated per-session bars or independently proved raw coverage, no-trade, halt, bar-straddle, auction, correction-sequence, or extreme occurrence-time facts. Those claims remain unavailable until a later raw-data policy exists. |
| P3-FE14 | Purity and unchanged publication | The selector invokes no target-hit, directional-win, asset-return, or target-error calculator; activates no methodology; fingerprints, persists, aggregates, ranks, or publishes nothing; and changes no schema, fixture, manifest, OpenAPI, Flyway, database, provider/API behavior, or web source. No key/account/license/network access is needed for this source-local slice. |

## Required favorable-extreme golden and negative tests

- Lock exact canonical bytes, length 4633, independently recomputed SHA-256,
  defensive byte copies, six-type source surface, record components, nested
  enums, sealed variants, 22-reason order, and private selector constructor.
- Resolve original and correction bases, every named horizon, bullish/strong
  bullish high selection, bearish/strong bearish low selection, exact-open,
  intraday, pre-open, exact-prior-close, and strict-gap bases. Prove the lower
  bound remains the basis event rather than the first-session open.
- Prove null/future binding equality and null/all-future candidate equality at
  one-microsecond boundaries for both PIT timestamps. Future wrong and future
  duplicate evidence must not alter result, context, evidence, reason, or
  cardinality.
- Mutate every binding and candidate gate and cross multiple faults in reverse
  input order. Known invalid plus valid must return the first exact mismatch;
  same-object and distinct-equal candidates must remain ambiguous.
- Cover positive numeric scale/precision limits, low below/equal high,
  malformed boundaries/timestamps/identities/session IDs, defensive copies,
  original decimal-scale preservation, contradictory direct results, and
  deterministic replay under changed locale/timezone with restoration.

## Supplied-leaf target-hit orchestration boundary

- The request consumes only the orchestration policy and complete supplied
  ADR-018/ADR-019 resolutions. It supplies no competing as-of, horizon, side,
  target, extreme, high/low pair, or Boolean and does not rerun either leaf
  producer.
- Ready requires a non-null favorable resolution with whole-record-equal
  readiness. Every non-ready eligibility branch requires null favorable
  evidence. Missing market evidence belongs in ADR-019 Unavailable; an omitted
  ADR-019 result or stale downstream result is malformed composition.
- Pending, NotApplicable, eligibility Unavailable, and favorable-extreme
  Unavailable are preserved as their exact typed leaf records. No reason is
  inspected, mapped, flattened, or converted to a Boolean.
- Only Ready plus matching Resolved builds `TargetHitInput` from the preserved
  route side, normalized target evidence, and selected favorable value and
  invokes the existing pure calculator exactly once.
- `Available` is one disconnected target-hit metric result. It is not canonical
  outcome `CALCULATED`, `dataComplete`, methodology activation, persistence, or
  publication.

## Supplied-leaf target-hit orchestration contract gate

| ID | Check | Expected result |
| --- | --- | --- |
| P3-TO01 | Exact source surface | Package `com.wallstreetreceipts.api.domain.outcome.targethitorchestration` contains exactly the policy, request, resolution, and orchestrator production files plus exactly one source-local golden test. No controller, service, repository, provider, DTO, or helper is added. |
| P3-TO02 | Exact policy identity | Policy enum contains only `POINT_IN_TIME_TARGET_HIT_ORCHESTRATION_V1`. ADR-020 locks the exact single-line 3082-byte ASCII/UTF-8 definition and SHA-256 `b91bf68958e42ad003b80973c74f9acc2dad8e4629f6a1905798df98aa8b5348`; every context echoes it and returned bytes are defensive. |
| P3-TO03 | Exact request and leaf versions | Request fields are exactly policy, `TargetEligibilityResolution`, and conditional `FavorableExtremeResolution`. Eligibility must be ADR-018 V1/hash and favorable evidence, when permitted, ADR-019 V1/hash. No raw requests or alternate inputs exist. |
| P3-TO04 | Conditional topology | Ready requires non-null favorable evidence whose nested readiness is whole-record equal. Pending/NotApplicable/Unavailable require null favorable evidence. Ready-null, non-ready-non-null, wrong policy/hash, or mismatched readiness fails construction rather than falling back or being ignored. |
| P3-TO05 | Exact result variants | Resolution permits exactly `Available`, `Pending`, `NotApplicable`, `EligibilityUnavailable`, and `FavorableExtremeUnavailable`. Context contains only orchestration version/hash. Each non-available branch retains the original complete typed leaf; Available retains the supplied Resolved leaf and primitive Available result. No outer reason enum or duplicate calculator input is stored. |
| P3-TO06 | Branch preservation | The one pending reason, all three not-applicable reasons, all 14 eligibility unavailable reasons and nested horizon reasons, and all 22 favorable-extreme unavailable reasons remain unchanged. The orchestrator never calls `.reason()` and never creates false, loss, zero, primitive missing-input, or generic unavailable substitutes. |
| P3-TO07 | Exact calculator input | Resolved side is exactly `DirectionalRoute.targetHitSide()`, target exactly normalized `TargetPriceEvidence.target()`, and favorable extreme exactly `FavorableExtreme.value()`. Source target terms, CallDirection reinterpretation, high/low reselection, and endpoint close are absent. |
| P3-TO08 | Single inclusive comparison | `TargetHitCalculator.calculate` has exactly one production callsite outside its owner, only inside the Ready+Resolved branch. Bullish remains `>=`, bearish `<=`, equality is a hit, and no tolerance, rounding, rescaling, conversion, alternate formula, or second invocation exists. |
| P3-TO09 | Primitive invariant | Complete Ready+Resolved inputs cannot produce primitive Unavailable. If the primitive violates that contract, orchestration fails with an internal invariant violation and emits no evidence result; it never treats a programming error as market-data unavailability. |
| P3-TO10 | Attestation boundary | Supplied public leaf records prove local consistency only. Orchestration attests their policy/correlation, exact composition, calculator input, and invocation; it does not claim leaf request membership, PIT filtering, producer invocation, candidate poisoning/cardinality, or raw-data verification. |
| P3-TO11 | Determinism and replay | Whole-record equality permits equal-but-distinct reconstructed readiness. Results are independent of clock, locale, timezone, environment, prior calls, and valid decimal scale; original nested records and decimal values remain unmodified. |
| P3-TO12 | Lifecycle firewall | `Available` does not create or mutate `CallOutcome`, `OutcomeEvaluationStatus`, `dataComplete`, methodology status/definition, input fingerprint, cancellation lineage, persistence, aggregation, ranking, or scheduling. Both fixture methodologies remain `MODEL_ONLY`; four canonical outcomes remain PENDING/INCOMPLETE with all metrics null. |
| P3-TO13 | Product/data firewall | No schema, fixture, manifest, OpenAPI, Flyway, database, API/provider behavior, JSON golden, resource, or web source changes. No endpoint-price fallback or raw window aggregation is added. |
| P3-TO14 | External-data boundary | No key, account, paid plan, domain, license, named secret, or network access is needed. Before real evidence is admitted, P5 must establish historical intraday/tick, calendar, corporate-action, asset/venue, storage, display, derived-data, and redistribution entitlements and then introduce only a reviewed scoped secret. |

## Required target-hit orchestration golden and negative tests

- Lock canonical bytes/hash, four-file/one-test surfaces, record components,
  sealed variants, exact imports, one primitive callsite, and no resolver or
  selector callsite.
- Execute exactly 55 vectors: 12 general boundary/negative tests, four source
  directions, all three not-applicable reasons, all 14 eligibility unavailable
  reasons, and all 22 favorable-extreme unavailable reasons.
- Cover bullish and bearish equality/hit/miss boundaries, strong-direction
  routing, a source target deliberately different from the normalized target,
  original/correction identity, exact nested object preservation, equal-but-
  distinct readiness replay, and mismatched composition rejection.
- Prove deterministic locale/default-timezone replay with restoration in
  `finally`; full API verification and CI reverse scans lock unchanged fixtures,
  methodology/outcome lifecycle, schemas, database, API, and web surfaces.
- Reverse scans must lex Java comments, strings, and character literals as
  distinct states so comment markers inside literals cannot hide executable
  code. Runtime-cardinality mismatches must exit nonzero even when Python
  assertions are optimized away.

## Supplied-leaf directional-win orchestration boundary

- The request contains exactly four non-null fields: orchestration policy,
  complete `BasisForecastTermsEvidence`, complete
  `CalculatorSideRouting.Result`, and complete `AssetReturnResult`. The return
  leaf is mandatory even for neutral; no competing direction, basis, asset,
  evaluation time, return, or Boolean is accepted.
- Required polarity and asset-return versions/digests and exact source
  direction, whole-record basis, asset identity, and forecast availability and
  capture at or before the nested endpoint `evaluationAsOf` are validated
  before any branch selection. Strong and ordinary directions cannot be
  substituted merely because they reduce to the same calculator side.
- Neutral takes precedence over return availability and becomes
  `NotApplicable`, preserving complete terms, non-directional routing, and the
  complete return leaf without a Boolean or calculator call.
- A directional unavailable return becomes `AssetReturnUnavailable` and
  preserves the exact return, price-pair, endpoint, and typed nested reasons.
  All 55 unavailable combinations remain uninterpreted; no reason becomes
  Pending, false, loss, or a generic unavailable state.
- Only directional plus available return evidence builds
  `DirectionalWinInput` from the preserved routed side and exact signed return,
  then invokes the existing pure calculator exactly once. Bullish requires
  strictly positive, bearish strictly negative, and zero is a miss for both.
- `Available` is one disconnected metric leaf, not canonical outcome
  `CALCULATED`, `dataComplete`, methodology activation, persistence, scheduling,
  aggregation, ranking, API behavior, or publication.

## Supplied-leaf directional-win orchestration contract gate

| ID | Check | Expected result |
| --- | --- | --- |
| P3-DWO01 | Exact source surface | Package `com.wallstreetreceipts.api.domain.outcome.directionalwinorchestration` contains exactly `DirectionalWinOrchestrationPolicyVersion`, `DirectionalWinOrchestrationRequest`, `DirectionalWinOrchestrationResolution`, and `DirectionalWinOrchestrator` plus exactly one source-local golden test. No controller, service, repository, provider, DTO, or helper is added. |
| P3-DWO02 | Exact policy identity | Policy enum contains only `SUPPLIED_LEAF_DIRECTIONAL_WIN_ORCHESTRATION_V1`. ADR-021 locks the exact single-line 3699-byte ASCII/UTF-8 definition and SHA-256 `51429c7601d4807162855f08c680d1e6bb7895f87fc108e141e5ad3a3ab25bcb`; every context echoes it and returned bytes are defensive. |
| P3-DWO03 | Exact all-present request | Request components are exactly policy, `BasisForecastTermsEvidence termsEvidence`, `CalculatorSideRouting.Result sideRouting`, and `AssetReturnResult assetReturnResult`. Every field is non-null on every branch, including neutral; no conditional omission or alternate input exists. |
| P3-DWO04 | Required leaf policy and correlation | Routing carries ADR-011 V1/hash and return carries ADR-017 V1/hash. Terms direction equals the exact canonical routing source direction, terms basis equals the full nested return basis record, terms asset equals the nested endpoint binding asset, and both terms PIT timestamps are at or before the endpoint evaluation as-of. Every mismatch fails before neutral precedence. |
| P3-DWO05 | Exact result variants | Resolution permits exactly `Available`, `NotApplicable`, and `AssetReturnUnavailable`. Context contains only orchestration version/hash. Every branch preserves terms, routing, and return; only Available also carries primitive `DirectionalWinResult.Available`. No outer reason enum or duplicate primitive input is stored. |
| P3-DWO06 | Neutral precedence | `NonDirectionalRoute` plus either available or unavailable return always becomes `NotApplicable`, preserving the entire supplied return leaf. It never emits a Boolean, invokes the primitive, or reclassifies return evidence. |
| P3-DWO07 | Unavailable preservation | Directional plus any of the 55 asset-return, price-pair, and endpoint unavailable combinations becomes `AssetReturnUnavailable` with exact object identity/value and nested reasons. Production does not call `.reason()`, map endpoint-not-reached to Pending, flatten, recalculate, or fall back. |
| P3-DWO08 | Exact calculator input and invocation | Only directional plus `AssetReturnResult.Available` builds `DirectionalWinInput(DirectionalRoute.directionalWinSide(), available.assetReturn())`. `DirectionalWinCalculator.calculate` has exactly one orchestration callsite and is invoked exactly once; no polarity resolver, routing producer, return calculator, price-pair selector, or endpoint selector is called. |
| P3-DWO09 | Strict signed comparison | Bullish is true only for return `> 0`; bearish only for return `< 0`; zero is false for both. The exact supplied `BigDecimal` is preserved without rounding, rescaling, tolerance, absolute value, percentage conversion, or target-disposition use. |
| P3-DWO10 | Primitive invariant | Complete directional and available-return evidence cannot yield primitive Unavailable. If that contract changes, orchestration fails closed with an internal invariant error and emits no evidence result or fallback. |
| P3-DWO11 | Attestation and replay | Public leaf/result construction proves only local consistency. Orchestration attests supplied policy/correlation, composition, exact primitive input, and invocation, not original request membership, producer execution, PIT filtering, or candidate cardinality. Equal-but-distinct records replay equally independent of clock, locale, timezone, prior calls, and valid decimal scale. |
| P3-DWO12 | Lifecycle firewall | No unavailable reason is promoted to Pending/retry/scheduling. Available does not create or mutate `CallOutcome`, status, `dataComplete`, methodology definition/status, fingerprint, cancellation/latest-correction lineage, persistence, aggregation, or ranking. |
| P3-DWO13 | Product/data and CI firewall | No schema, canonical fixture, manifest, JSON golden, OpenAPI, Flyway, database, API/provider behavior, resource, or web source changes. Repository CI locks 177 protected production files at SHA-256 `86d2175f849a3f866858c07351fbc24137946c4a286362f0557a9e7dc6b71bbf`, exact reverse edges, the dedicated policy guard, and exact 84/84 Surefire cardinality with explicit nonzero mismatch exits. |
| P3-DWO14 | External-data boundary | No API key, account, paid plan, domain, provider license, named secret, or network access is needed. Before non-DEMO use, P5 must select analyst-call, official-close, exchange-calendar, corporate-action, and asset/venue reference providers; establish storage, display, derived-data, and redistribution rights; then introduce only reviewed scoped secrets through approved local/CI secret stores, never chat or Git. |

## Required directional-win orchestration golden and negative tests

- Execute exactly 84 vectors: 15 contract/correlation/PIT/replay checks, all
  four directional source directions, six strict sign boundaries, all 55
  unavailable asset-return/price-pair/endpoint combinations, and four neutral-
  precedence return states.
- Lock canonical bytes/hash, the four-production-file/one-test surface, record
  components, sealed variants, all-non-null request topology, exact reverse
  imports/calls, one primitive callsite, and absence of producer replay or
  reason inspection.
- Reject every wrong policy/hash, exact direction, basis, asset, and future
  terms timestamp before branching. Accept equal PIT boundaries and equal-but-
  distinct reconstructed evidence while preserving original records and
  decimal values.
- Cover positive/negative/zero on both sides, neutral with available and
  unavailable returns, original/correction bases, target-disposition
  irrelevance, null roots, malformed direct result shapes, and deterministic
  locale/default-timezone replay with restoration in `finally`.
- Full API verification reaches exactly 819/819 tests. Repository CI contains
  28 embedded Python bodies: all 28 compile under optimized Python, the 27
  locally executable bodies pass, and the final cross-stack body remains
  syntax-checked here for execution by the workflow's service jobs.

## Supplied-leaf directional-win readiness boundary

- The request consumes exactly the readiness policy and one complete supplied
  ADR-021 resolution. It supplies no competing terms, route, price, return,
  endpoint, reason, Boolean, evaluation time, status, or schedule and invokes
  no producer or calculator.
- `Settled` is limited to ADR-021 `Available`, or neutral `NotApplicable` whose
  preserved asset-return leaf is Available. It means only that the supplied
  asset-return dependency is available; it does not mean every outcome metric
  exists or that the neutral source has a directional-win Boolean.
- `AwaitingEndpoint` requires the exact nested unavailable chain
  `AssetReturn.PRICE_PAIR_UNAVAILABLE` ->
  `PricePair.ENDPOINT_PRICE_UNAVAILABLE` ->
  `Endpoint.ENDPOINT_NOT_REACHED_AS_OF`. No other missing, mismatch,
  ambiguity, adjustment, continuity, catalog, binding, observation, or numeric
  condition is treated as temporal.
- Every other unavailable chain becomes `EvidenceUnavailable` while preserving
  the complete source. `BASIS_AND_ENDPOINT_PRICE_UNAVAILABLE` remains evidence-
  unavailable even with nested endpoint-not-reached because waiting for the
  close cannot repair the unavailable basis price.
- The three names are source-local readiness evidence only. They are not
  `OutcomeEvaluationStatus`, `CallOutcome`, `dataComplete`, retry/freshness,
  cancellation, latest-correction selection, scheduling, or provider-health
  claims.

## Supplied-leaf directional-win readiness contract gate

| ID | Check | Expected result |
| --- | --- | --- |
| P3-DWR01 | Exact source surface | Package `com.wallstreetreceipts.api.domain.outcome.directionalwinreadiness` contains exactly `DirectionalWinReadinessPolicyVersion`, `DirectionalWinReadinessRequest`, `DirectionalWinReadinessResolution`, and `DirectionalWinReadinessResolver` plus exactly one source-local golden test. No controller, service, repository, provider, DTO, scheduler, or helper is added. |
| P3-DWR02 | Exact policy identity | Policy enum contains only `SUPPLIED_LEAF_DIRECTIONAL_WIN_READINESS_V1`. ADR-022 locks the exact single-line 2353-byte ASCII/UTF-8 definition and SHA-256 `1eca77c5b4d43de7657281c161a8c50356cd90e1a18c6e9fd7f5b2c0142b7ec7`; every context echoes it and returned bytes are defensive. |
| P3-DWR03 | Exact all-present request | Request components are exactly readiness policy and `DirectionalWinOrchestrationResolution sourceResolution`; both are non-null. The source uses ADR-021 V1 and digest `51429c7601d4807162855f08c680d1e6bb7895f87fc108e141e5ad3a3ab25bcb`. No raw or competing leaf input exists. |
| P3-DWR04 | Exact source extraction | The resolver extracts only the complete `AssetReturnResult` already preserved by ADR-021 `Available`, `NotApplicable`, or `AssetReturnUnavailable`. It does not reconstruct terms, routing, price pairs, endpoints, returns, or Booleans and invokes no resolver, selector, orchestrator, or calculator producer. |
| P3-DWR05 | Exact result variants | Resolution permits exactly `Settled(context,sourceResolution)`, `AwaitingEndpoint(context,sourceResolution)`, and `EvidenceUnavailable(context,sourceResolution)`. Context contains only readiness version/hash; each branch preserves the exact whole ADR-021 source with no outer reason or duplicate leaf. |
| P3-DWR06 | Settled boundary | `Settled` accepts only ADR-021 directional `Available`, or neutral `NotApplicable` with an available asset-return leaf. It does not add a Boolean to neutral, imply complete metrics, or accept any unavailable return chain. |
| P3-DWR07 | Exact awaiting chain | `AwaitingEndpoint` requires, simultaneously, asset-return reason `PRICE_PAIR_UNAVAILABLE`, price-pair reason `ENDPOINT_PRICE_UNAVAILABLE`, a typed unavailable price pair carrying that same reason, endpoint reason `ENDPOINT_NOT_REACHED_AS_OF`, and a typed unavailable endpoint carrying that same reason. |
| P3-DWR08 | Compound-evidence firewall | `BASIS_AND_ENDPOINT_PRICE_UNAVAILABLE` is always `EvidenceUnavailable`, including when it preserves `ENDPOINT_NOT_REACHED_AS_OF`. Every other one of the 55 unavailable chains is also evidence-unavailable except the single endpoint-only chain. |
| P3-DWR09 | Precedence and reason ownership | Classification order is available return, exact endpoint-only chain, then all remaining unavailable evidence. Exact nested reason inspection occurs only inside `DirectionalWinReadinessResolver`; no reason is flattened, renamed, generalized, or exposed on the outer result. |
| P3-DWR10 | Constructor and attestation boundary | Public result constructors reject a source classified for another variant by delegating to the resolver's single classification rule. They attest local source policy/shape and classification only, not original request membership, producer invocation, PIT filtering, candidate cardinality, or market truth. |
| P3-DWR11 | Whole-source preservation | Every result retains the exact supplied ADR-021 object, including terms, route, asset-return result, price-pair/endpoint evidence, nested reasons, and directional Boolean where one exists. No copy, fallback, recalculation, or evidence clearing is permitted. |
| P3-DWR12 | Determinism | Equal source records classify equally regardless of clock, locale, timezone, input decimal scale where valid, environment, thread, random state, invocation order, or prior calls. The policy accepts no clock, timeout, retry count, or current-time input. |
| P3-DWR13 | Lifecycle firewall | The slice does not construct or mutate `CallOutcome`; emit `PENDING`, `INCOMPLETE`, `CALCULATED`, or `EXCLUDED`; set `dataComplete`; decide retry, cancellation, latest correction, scheduling, methodology activation, fingerprinting, persistence, aggregation, ranking, or publication. |
| P3-DWR14 | Product/data firewall | No schema, canonical fixture, manifest, OpenAPI, Flyway, database, API/provider behavior, resource, or web source changes. No key, account, paid plan, provider license, named secret, or network access is needed for this source-local classification. |
| P3-DWR15 | Repository CI contract | Both protected-production baselines contain exactly 181 files at SHA-256 `4b295246194dfe1a60d6e37380ad398393e8951be070e6e82b7852b151909e8c`. CI locks the dedicated policy and reverse-reference guard, unchanged data/product surfaces, and exact 118/118 Surefire cardinality with an explicit nonzero mismatch exit. |

## Required directional-win readiness golden and negative tests

- Execute exactly 118 vectors: six contract/null/shape/replay/determinism
  checks, all 55 typed unavailable chains in both directional and neutral
  source shapes for 110 classification vectors, and two settled source shapes.
- Lock the exact canonical bytes/hash, four-production-file/one-test surface,
  two-field request, context components, three sealed variants, exact source
  policy requirement, reverse references, and absence of producer/calculator or
  canonical lifecycle wiring.
- Prove that only the endpoint-only chain is awaiting in both source shapes and
  that the compound basis-and-endpoint case remains evidence-unavailable.
  Mutate each nested reason/type and every direct result variant; contradictory
  construction must fail closed.
- Prove exact whole-source preservation, equal-but-distinct replay, null
  rejection, defensive policy bytes, and deterministic locale/default-timezone
  replay with restoration in `finally`.
- The expected complete API suite after adding the 118 source-local invocations
  is exactly 937 tests. Repository CI must contain exactly 29 embedded Python
  bodies: all 29 compile, the 28 locally executable bodies pass, and the final
  cross-stack body remains syntax-checked here for workflow service execution.
  The dedicated policy/reverse-wiring guard and exact 118/118 Surefire
  cardinality check use explicit nonzero mismatch exits.

## Supplied-leaf target-error readiness boundary

- The request consumes exactly the readiness policy and one complete supplied
  ADR-015 `TargetErrorResult`. It supplies no competing target, endpoint,
  reason, error value, evaluation time, status, or schedule and invokes no
  selector or calculator.
- `Settled` is limited to `TargetErrorResult.Available`. It means only that the
  supplied target-error dependency is available; it does not mean the other
  nine required metrics exist or that a complete outcome can be published.
- `AwaitingEndpoint` requires the exact complete nested chain
  `TargetErrorResult.ENDPOINT_PRICE_UNAVAILABLE` ->
  `EndpointPriceResolution.ENDPOINT_NOT_REACHED_AS_OF`, with the preserved
  typed endpoint-unavailable result carrying the same reason.
- Every other constructible unavailable shape becomes `EvidenceUnavailable`.
  `TARGET_AND_ENDPOINT_PRICE_UNAVAILABLE` remains evidence-unavailable even
  when its endpoint is not reached because waiting for the close cannot repair
  the already absent target.
- The three names are source-local readiness evidence only. They are not
  `OutcomeEvaluationStatus`, `CallOutcome`, `dataComplete`, retry/freshness,
  cancellation, latest-correction selection, scheduling, or provider-health
  claims. A later canonical lifecycle policy must consider completeness across
  all 10 required metrics.

## Supplied-leaf target-error readiness contract gate

| ID | Check | Expected result |
| --- | --- | --- |
| P3-TER01 | Exact source surface | Package `com.wallstreetreceipts.api.domain.outcome.targeterrorreadiness` contains exactly `TargetErrorReadinessPolicyVersion`, `TargetErrorReadinessRequest`, `TargetErrorReadinessResolution`, and `TargetErrorReadinessResolver` plus exactly one source-local golden test. No controller, service, repository, provider, DTO, scheduler, or helper is added. |
| P3-TER02 | Exact policy identity | Policy enum contains only `SUPPLIED_LEAF_TARGET_ERROR_READINESS_V1`. ADR-023 locks the exact single-line 1979-byte ASCII/UTF-8 definition and SHA-256 `0b8bfb22dccd4a494f568c44d06163f73af36462cf929bc83cf238019811c44a`; every context echoes it and returned bytes are defensive. |
| P3-TER03 | Exact all-present request | Request components are exactly readiness policy and `TargetErrorResult sourceResult`; both are non-null. The source uses ADR-015 `ACTUAL_DENOMINATOR_SCALE_12_HALF_EVEN_V1` and digest `31ca30555549f670e3c22d98ead16f7a02bfad198f36532effaf4a4b6931d074`. No raw or competing leaf input exists. |
| P3-TER04 | Exact source extraction | The resolver consumes only the complete supplied target-error result and its already-preserved endpoint resolution. It does not reconstruct a target, endpoint observation, target-error value, or evaluation time and invokes no selector, calculator, orchestrator, or producer. |
| P3-TER05 | Exact result variants | Resolution permits exactly `Settled(context,sourceResult)`, `AwaitingEndpoint(context,sourceResult)`, and `EvidenceUnavailable(context,sourceResult)`. Context contains only readiness version/hash; every branch preserves the exact whole ADR-015 source with no outer reason or duplicate endpoint leaf. |
| P3-TER06 | Settled boundary | `Settled` accepts only `TargetErrorResult.Available`. It does not imply that another metric exists, that the overall outcome is complete, or that any unavailable target error can settle. |
| P3-TER07 | Exact awaiting chain | `AwaitingEndpoint` simultaneously requires target-error reason `ENDPOINT_PRICE_UNAVAILABLE`, echoed endpoint reason `ENDPOINT_NOT_REACHED_AS_OF`, a typed unavailable endpoint in the source context, and that endpoint carrying the same reason. |
| P3-TER08 | Compound-evidence firewall | `TARGET_AND_ENDPOINT_PRICE_UNAVAILABLE` is always `EvidenceUnavailable`, including when its endpoint reason is `ENDPOINT_NOT_REACHED_AS_OF`. Of the 39 constructible unavailable shapes, exactly one awaits and the other 38 are evidence-unavailable. |
| P3-TER09 | Precedence and reason ownership | Classification order is available target error, exact endpoint-only chain, then every other unavailable result. Exact reason inspection occurs only inside `TargetErrorReadinessResolver`; no reason is flattened, renamed, generalized, or exposed on the outer result. |
| P3-TER10 | Constructor and attestation boundary | Public result constructors reject a source classified for another variant by delegating to the resolver's single classification rule. They attest local source policy/shape and classification only, not original input membership, PIT filtering, candidate cardinality, selector/calculator invocation, or market truth. |
| P3-TER11 | Whole-source preservation | Every result retains the exact supplied ADR-015 object, including its context, endpoint evidence, reasons, and decimal target error where available. No copy, fallback, recalculation, or evidence clearing is permitted. |
| P3-TER12 | Determinism | Equal source records classify equally regardless of clock, locale, timezone, valid decimal scale, environment, thread, random state, invocation order, or prior calls. The policy accepts no clock, timeout, retry count, or current-time input. |
| P3-TER13 | Lifecycle firewall | The slice does not construct or mutate `CallOutcome`; emit `PENDING`, `INCOMPLETE`, `CALCULATED`, or `EXCLUDED`; set `dataComplete`; decide retry, freshness, cancellation, latest correction, scheduling, methodology activation, fingerprinting, persistence, aggregation, ranking, or publication. |
| P3-TER14 | Product/data firewall | No schema, canonical fixture, manifest, OpenAPI, Flyway, database, API/provider behavior, resource, or web source changes. No key, account, paid plan, provider license, named secret, or network access is needed for this source-local classification. |
| P3-TER15 | Repository CI contract | Both protected-production baselines contain exactly 185 files at SHA-256 `4a1479312db7053a476ae4b982df3f13aad570332baef4489e03d039cd49114a`. CI locks the dedicated policy and reverse-reference guard, unchanged data/product surfaces, and exact 46/46 Surefire cardinality with an explicit nonzero mismatch exit; all affected repository guards pass. |

## Required target-error readiness golden and negative tests

- Execute exactly 46 vectors: six contract, null, shape, direct-construction,
  replay, and determinism checks; all 39 constructible unavailable shapes; and
  one settled shape.
- Lock the exact canonical bytes/hash, four-production-file/one-test surface,
  two-field request, context components, three sealed variants, exact source
  policy requirement, reverse references, and absence of selector/calculator
  or canonical lifecycle wiring.
- Prove the exact 40-shape classification matrix: one settled, one awaiting,
  and 38 evidence-unavailable. The 39 unavailable shapes comprise 16 endpoint-
  only reasons, 16 compound target-and-endpoint reasons, and seven reasons that
  require a resolved endpoint.
- Prove that only endpoint-only plus endpoint-not-reached awaits and that the
  compound missing-target case remains evidence-unavailable. Contradictory
  direct variant construction must fail closed.
- Prove exact whole-source preservation, equal-but-distinct replay, null
  rejection, defensive policy bytes, and deterministic locale/default-timezone
  replay with restoration in `finally`.
- Focused source-local verification is exactly 46/46 with zero failures,
  errors, or skips. The expected complete API suite after these 46 invocations
  is exactly 983 tests; complete Maven verification passes 983/983 with zero
  failures, errors, or skips, including PostgreSQL/Flyway, H2/Spring/API, and
  Spring Boot packaging. Repository CI contains exactly 30 embedded Python
  bodies: all 30 compile, the 29 locally executable bodies pass, and the final
  cross-stack body remains syntax-checked here for workflow service execution.
  Both protected-production baselines are fixed at 185 files / SHA-256
  `4a1479312db7053a476ae4b982df3f13aad570332baef4489e03d039cd49114a`,
  and the dedicated and affected reverse guards pass.

## Supplied-leaf target-hit readiness boundary

- The request consumes exactly the readiness policy and one complete supplied
  ADR-020 `TargetHitOrchestrationResolution`. It supplies no competing
  eligibility, favorable extreme, reason, Boolean, evaluation time, status, or
  schedule and invokes no resolver, selector, orchestrator, or calculator.
- `Settled` contains exactly ADR-020 `Available` and all three permanent
  `NotApplicable` reasons. Permanent non-applicability is a finished metric
  meaning, not `false`, a loss, missing evidence, or proof that another metric
  exists.
- `AwaitingEndpoint` contains exactly ADR-020 `Pending`. Its nested eligibility
  result can carry only `HORIZON_NOT_REACHED_AS_OF` and already proves that the
  endpoint close remains future; the readiness resolver does not reinterpret
  or duplicate that reason.
- `EligibilityUnavailable` and `FavorableExtremeUnavailable` always become
  `EvidenceUnavailable`. Missing-as-of or another nested evidence reason is not
  promoted into a retry, freshness, temporal, or permanence claim.
- The three names are source-local readiness evidence only. They are not
  `OutcomeEvaluationStatus`, `CallOutcome`, `dataComplete`, retry/freshness,
  cancellation, latest-correction selection, scheduling, provider health,
  methodology activation, persistence, aggregation, ranking, or publication.
  A later canonical lifecycle policy must consider all 10 required metrics and
  intentional non-applicability.

## Supplied-leaf target-hit readiness contract gate

| ID | Check | Expected result |
| --- | --- | --- |
| P3-THR01 | Exact source surface | Package `com.wallstreetreceipts.api.domain.outcome.targethitreadiness` contains exactly `TargetHitReadinessPolicyVersion`, `TargetHitReadinessRequest`, `TargetHitReadinessResolution`, and `TargetHitReadinessResolver` plus exactly one source-local golden test. No controller, service, repository, provider, DTO, scheduler, or helper is added. |
| P3-THR02 | Exact policy identity | Policy enum contains only `SUPPLIED_LEAF_TARGET_HIT_READINESS_V1`. ADR-024 locks the exact single-line 2042-byte ASCII/UTF-8 definition and SHA-256 `8f81dee5227370d82dd91cd2fb8448797c7028eaa485dc64cf4bdc3cbf2f31a3`; every context echoes it and returned bytes are defensive. |
| P3-THR03 | Exact all-present request | Request components are exactly readiness policy and `TargetHitOrchestrationResolution sourceResult`; both are non-null. The source uses ADR-020 `POINT_IN_TIME_TARGET_HIT_ORCHESTRATION_V1` and digest `b91bf68958e42ad003b80973c74f9acc2dad8e4629f6a1905798df98aa8b5348`. No raw or competing leaf input exists. |
| P3-THR04 | Exact source consumption | The resolver consumes only the complete supplied orchestration result. It does not reconstruct eligibility, target, favorable extreme, Boolean, reason, or evaluation time and invokes no eligibility resolver, favorable-extreme selector, target-hit orchestrator, calculator, or producer. |
| P3-THR05 | Exact result variants | Resolution permits exactly `Settled(context,sourceResult)`, `AwaitingEndpoint(context,sourceResult)`, and `EvidenceUnavailable(context,sourceResult)`. Context contains only readiness version/hash; every branch preserves the exact whole ADR-020 source with no outer reason or copied nested leaf. |
| P3-THR06 | Settled boundary | `Settled` accepts exactly one `Available` shape and all three permanent `NotApplicable` reasons. Non-applicability is not converted to a Boolean and does not imply that another metric or the overall outcome is complete. |
| P3-THR07 | Exact awaiting boundary | `AwaitingEndpoint` accepts only the typed ADR-020 `Pending` variant. Its preserved nested eligibility record carries the sole `HORIZON_NOT_REACHED_AS_OF` reason and proves an unreached resolved endpoint; no unavailable branch may await. |
| P3-THR08 | Evidence-unavailable boundary | All 14 `EligibilityUnavailable` and all 22 `FavorableExtremeUnavailable` shapes are `EvidenceUnavailable`. No nested missing-as-of, mismatch, ambiguity, adjustment, continuity, catalog, binding, observation, or numeric meaning is inferred to be retryable, temporal, or permanent. |
| P3-THR09 | Exact matrix and precedence | The complete constructible source matrix is exactly 41 shapes: four settled (one Available plus three NotApplicable), one awaiting Pending, and 36 evidence-unavailable (14 eligibility plus 22 favorable extreme). Classification order is Available, NotApplicable, Pending, EligibilityUnavailable, FavorableExtremeUnavailable. |
| P3-THR10 | Constructor and attestation boundary | Public result constructors reject a source classified for another variant by delegating to the resolver's single classification rule. They attest local source policy/shape and classification only, not original request membership, PIT filtering, candidate cardinality, selector/calculator invocation, or market truth. |
| P3-THR11 | Whole-source preservation | Every result retains the exact supplied ADR-020 object, including its context, complete nested eligibility or favorable-extreme resolution, reasons, and Boolean where available. No copy, fallback, recalculation, reason flattening, or evidence clearing is permitted. |
| P3-THR12 | Determinism | Equal source records classify equally regardless of clock, locale, timezone, environment, thread, random state, invocation order, or prior calls. The policy accepts no clock, timeout, retry count, or current-time input. |
| P3-THR13 | Lifecycle firewall | The slice does not construct or mutate `CallOutcome`; emit `PENDING`, `INCOMPLETE`, `CALCULATED`, or `EXCLUDED`; set `dataComplete`; decide retry, freshness, cancellation, latest correction, scheduling, methodology activation, fingerprinting, persistence, aggregation, ranking, or publication. |
| P3-THR14 | Product/data firewall | No schema, canonical fixture, manifest, OpenAPI, Flyway, database, API/provider behavior, resource, or web source changes. No API key, account, paid plan, provider license, named secret, or network access is needed for this source-local classification. |
| P3-THR15 | Repository CI contract | Both protected-production baselines contain exactly 189 files at SHA-256 `bc251da006f897de69744ee8aec2400da5d18c38c2945aac03ec46063cc18721`. CI locks the four-production-file/one-test surface, exact policy bytes/hash, exact 47/47 Surefire cardinality with an explicit nonzero mismatch exit, reverse references, unchanged product/data surfaces, and the lifecycle firewall; all affected guards pass. |

## Required target-hit readiness golden and negative tests

- Execute exactly 47 vectors: six fixed policy/contract, null/shape, wrong
  direct-construction, equal-but-distinct replay, and locale/time-zone
  determinism checks plus all 41 constructible source shapes.
- Prove exactly four settled shapes (one Available plus all three NotApplicable
  reasons), one awaiting Pending shape, and 36 evidence-unavailable shapes (all
  14 eligibility-unavailable plus all 22 favorable-extreme-unavailable).
- Lock the exact canonical bytes/hash, four-production-file/one-test surface,
  two-field request, context components, three sealed variants, exact ADR-020
  source policy requirement, reverse references, and absence of producer,
  selector, calculator, or canonical lifecycle wiring.
- Prove exact whole-source identity preservation, equal-but-distinct replay,
  null rejection, wrong direct-variant rejection, defensive policy bytes, and
  deterministic locale/default-timezone replay with restoration in `finally`.
- Focused source-local verification passes exactly 47/47 with zero failures,
  errors, or skips. Complete Maven verification passes exactly 1030/1030 with
  zero failures, errors, or skips, including PostgreSQL/Flyway, H2/Spring/API,
  and Spring Boot packaging. Repository CI contains exactly 31 embedded Python
  bodies: all 31 compile, all 30 locally executable bodies pass, and the final
  cross-stack body remains syntax-checked for workflow service execution. Both
  protected-production baselines are fixed at 189 files / SHA-256
  `bc251da006f897de69744ee8aec2400da5d18c38c2945aac03ec46063cc18721`;
  the dedicated ADR-024, affected reverse-reference, baseline, and exact 47/47
  cardinality guards pass.

## Shared asset-return and directional-win readiness ownership boundary

ADR-022 remains the sole shared receipt for asset-return and directional-win readiness.

- ADR-025 is a decision-only ownership contract. It adds no executable policy,
  canonical definition, digest, Java package, resolver, wrapper, or golden test
  and does not rename or amend ADR-022.
- A later aggregate consumes the exact complete ADR-022 resolution once and
  accounts for two canonical metric meanings. A complete future aggregate has
  10 metric meanings and nine readiness ownership inputs. Today only shared
  ADR-022, target-error ADR-023, and target-hit ADR-024 readiness contracts
  exist; the remaining inputs are deferred.
- Directional ADR-021 `Available` contributes the exact preserved return and
  Boolean. Neutral `NotApplicable` with an available return contributes the
  exact return and typed intentional directional non-applicability, never
  `false` and never an inference from JSON or Java null.
- A directional unavailable return leaves both metric values unresolved. A
  neutral `NotApplicable` with an unavailable return keeps directional win
  intentionally not applicable while asset return remains awaiting or
  evidence-unavailable, so the shared receipt is not settled.
- The aggregate may inspect the preserved ADR-021 top-level source variant for
  settled value/applicability projection. It must not repeat ADR-022 nested
  reason classification, invoke any upstream producer, resolve the receipt
  twice, flatten evidence, or accept a separately selected return.
- No shared receipt variant directly selects a canonical outcome state. Later
  lifecycle work must compose all 10 metric meanings, intentional non-
  applicability, cancellation/latest-correction eligibility, methodology and
  fingerprint identity, plus reviewed freshness and scheduling policy.

## Shared readiness ownership contract gate

| ID | Check | Expected result |
| --- | --- | --- |
| P3-SRO01 | Decision-only surface | ADR-025 is the sole new architecture decision for this slice. It adds no Java production/test file, executable readiness policy, canonical policy definition/hash, schema, fixture, manifest, OpenAPI, Flyway, database, controller, provider, resource, or web source. |
| P3-SRO02 | Unchanged ADR-022 identity | `DirectionalWinReadinessPolicyVersion` remains exactly `SUPPLIED_LEAF_DIRECTIONAL_WIN_READINESS_V1` with the unchanged 2353-byte ASCII/UTF-8 definition and SHA-256 `1eca77c5b4d43de7657281c161a8c50356cd90e1a18c6e9fd7f5b2c0142b7ec7`. Public names, source policy, result variants, branch mapping, and 118-vector golden contract do not change. |
| P3-SRO03 | No competing receipt | No `assetreturnreadiness` package and no `AssetReturnReadiness` policy, request, resolution, resolver, alias, wrapper, delegated facade, or golden test exists. The ADR-017 `assetreturn` calculator/result package remains valid and unchanged. |
| P3-SRO04 | One shared ownership input | Future aggregate composition accepts the exact complete ADR-022 resolution once as the single asset-return/directional-win ownership input. It may not accept two independently selected receipts, resolve the same source twice, or pair an ADR-021 directional result with another asset-return result. |
| P3-SRO05 | Exact source co-identity | Both metric meanings derive from the one ADR-021 source already preserved by ADR-022, including its exact terms, route, basis, asset, evaluation-as-of, return, directional result or non-applicability, and nested evidence. No copy, fallback, cross-source pairing, or recalculation is permitted. |
| P3-SRO06 | Directional settled projection | `Settled` preserving ADR-021 `Available` projects the exact `AssetReturnResult.Available.assetReturn()` and exact `DirectionalWinResult.Available.directionalWin()`. Neither value is rounded, rescaled, recomputed, or inferred. |
| P3-SRO07 | Neutral settled projection | `Settled` preserving ADR-021 neutral `NotApplicable` projects the exact available asset return and typed intentional directional non-applicability. It never manufactures `false`, treats a null Boolean alone as proof, or omits the asset-return requirement. |
| P3-SRO08 | Unavailable projection | Directional `AssetReturnUnavailable` leaves both values unresolved. Neutral `NotApplicable` with an unavailable return keeps directional win intentionally not applicable but leaves asset return unresolved; the shared outer receipt remains `AwaitingEndpoint` or `EvidenceUnavailable` exactly as ADR-022 classified it. |
| P3-SRO09 | Classification ownership | Future consumers trust the supplied ADR-022 outer variant and may inspect only the preserved ADR-021 top-level variant for value/applicability projection. Exact nested reason inspection remains solely in `DirectionalWinReadinessResolver`; consumers add no flattening, alternate temporal rule, retry rule, or permanence inference. |
| P3-SRO10 | Ten meanings and nine future inputs | A complete future aggregate evaluates exactly 10 canonical metric meanings through nine readiness ownership inputs: one shared ADR-022 receipt plus one input for each other eight meanings. Today only shared ADR-022, target-error ADR-023, and target-hit ADR-024 readiness contracts exist; all remaining inputs are deferred. Further sharing requires another accepted decision. |
| P3-SRO11 | No double counting | The shared receipt must discharge both distinct metric checks without being duplicated as two mutable inputs or counted as only one meaning. `Settled` is necessary only for these two meanings and is never sufficient for whole-outcome completeness. |
| P3-SRO12 | Lifecycle and product firewall | No direct `Settled`→`CALCULATED`, `AwaitingEndpoint`→`PENDING`, or `EvidenceUnavailable`→`INCOMPLETE` mapping exists. The decision sets no `dataComplete`, retry, freshness, cancellation, latest-correction, scheduling, methodology, fingerprint, persistence, aggregation, ranking, API/UI, provider-health, or publication behavior and requires no key, account, license, secret, or network. |
| P3-SRO13 | Repository ownership guard | Repository validation locks the ADR-025 ownership marker, unchanged ADR-022 bytes/hash and source/test surface, absence of a standalone asset-return readiness surface, unchanged product/data files, and unchanged existing golden/API cardinalities with explicit nonzero mismatch exits. The protected baseline remains exactly 189 files with SHA-256 `bc251da006f897de69744ee8aec2400da5d18c38c2945aac03ec46063cc18721`. |

## Required ownership documentation and negative checks

- Confirm the ADR-025 decision, README summary, this gate, and implementation
  log use the same sole-shared-receipt marker exactly once per document.
- Reject any `assetreturnreadiness` path or `AssetReturnReadiness` symbol and
  any change to ADR-022's 2353 canonical bytes, fixed digest, four production
  files, one golden file, or exact 118/118 golden cardinality.
- Confirm this docs-only ownership decision adds no production/test code and
  leaves the protected production baseline and complete API test cardinality
  unchanged.
- Complete API Maven regression: **PASS** — `./mvnw.cmd -B -ntp verify` ran
  1030 tests with 0 failures, 0 errors, and 0 skipped, then completed the build.
- Repository CI ownership, reverse-reference, product/data-firewall, and
  cardinality validation: **PASS** — all 31 embedded Python bodies compile
  under optimization, all 30 locally executable bodies pass, and the final
  cross-stack body remains syntax-checked for workflow service execution. The
  ownership guard preserves the exact 118/118 ADR-022 golden contract and the
  protected 189-file baseline/hash.

## Comparative reference-return foundation boundary

ADR-026 locks benchmark and sector returns to explicit point-in-time reference assignments.

- ADR-026 records the received product approval as a decision-only foundation.
  It adds no executable assignment, reference-evidence, calculation, or
  readiness policy and no canonical definition or digest.
- Benchmark and sector assignments remain independently typed. Each freezes an
  explicit source-revised assignment at the exact original/correction basis
  event; a correction is evaluated independently and current/latest membership
  is never inferred.
- V1 benchmark applicability requires visible coherent evidence of an equity,
  a United States primary venue, and USD. Such an asset maps to `asset-spx`
  only through exactly one explicit visible assignment. Known out-of-scope
  assets are typed not-applicable; expected missing, conflicting, or ambiguous
  evidence is typed unavailable.
- A future WSR sector taxonomy must be provider-neutral, versioned, closed, and
  explicitly mapped from preserved provider classifications. Synthetic P2 map
  labels are not evidence and no GICS, ICB, ETF, basket, or treemap semantics
  are inferred.
- Benchmark and sector price-return evidence must cover the exact common
  basis-event-to-asset-endpoint UTC interval, match currency without FX,
  preserve reference-specific venue/calendar/source revisions, and attest
  provider-published index divisor continuity with reference-specific types.

## Comparative reference-return foundation contract gate

| ID | Check | Expected result |
| --- | --- | --- |
| P3-CRF01 | Decision-only surface | ADR-026, README, this gate, the implementation log, and repository validation are the only intended changes. No Java production/test file, executable policy, canonical definition/hash, schema, fixture, manifest, OpenAPI, Flyway, database, provider, API, resource, or web source is added. |
| P3-CRF02 | Independent assignment ownership | Benchmark and sector assignment evidence, selection, results, and later readiness remain distinct contracts. No generic nullable reference, implicit shared receipt, cross-cast, or cross-kind fallback is allowed. |
| P3-CRF03 | Exact basis freeze | Assignment membership is evaluated at the exact `OutcomeBasis.eventTime` using a start-inclusive/end-exclusive effective interval and remains frozen through the horizon. Original and correction bases are independent; latest/current membership inference is absent. |
| P3-CRF04 | PIT visibility | Assignment, classification, taxonomy mapping, and later reference evidence are visible only when both `availableAt` and `capturedAt` are not after the exact `evaluationAsOf`. Future evidence is identical to absent evidence and cannot affect output or reasons. |
| P3-CRF05 | Required evidence identity | Later evidence preserves provider-event/evidence identity, source ID/revision, provenance, asset/class, primary venue and explicitly sourced venue country, currency, effective interval, and PIT timestamps. Open-ended membership must be represented explicitly. |
| P3-CRF06 | Benchmark V1 scope | Only an explicitly evidenced `AssetType.EQUITY` with primary-venue country exactly ISO 3166-1 alpha-2 `US` and currency exactly ISO 4217 `USD` enters V1 benchmark scope. Ticker, name, current master state, fixture universe, map inclusion, and exchange-like text establish nothing. |
| P3-CRF07 | Exact benchmark assignment | An in-scope asset is assigned to canonical `asset-spx` with `AssetType.INDEX` only by exactly one valid visible mapping that attests a USD provider-published price-index reference. The existing DEMO identity alone is not assignment or level evidence. |
| P3-CRF08 | Typed N/A versus unavailable | Visible coherent non-equity, index, non-US, or non-USD classification is intentional `NotApplicable`. Missing mapping for an in-scope asset, missing/conflicting classification, visible out-of-scope mapping conflict, invalid mapping, or duplicate mapping is `EvidenceUnavailable`; none becomes zero, false, loss, or automatic SPX assignment. |
| P3-CRF09 | No inference or fallback | `SPX`, `S&P 500`, `MarketSnapshot.spx`, P2 `sp500`, treemap/map data, ticker, latest row, nearest interval, provider preference, deduplication, and fallback cannot create or repair an assignment. |
| P3-CRF10 | Provider-neutral taxonomy | Before sector code exists, a later decision locks WSR taxonomy ID/version, canonical bytes/hash, closed node IDs, and exact provider-to-canonical mapping evidence. Provider labels are preserved but do not imply GICS, ICB, or another licensed taxonomy. |
| P3-CRF11 | Exact sector reference | Sector return uses the explicitly assigned provider-published sector price index. ETF, current basket, constituent inference, market-cap proxy, treemap aggregate, and provider-return fields are excluded. |
| P3-CRF12 | Exact common UTC interval | Reference evidence binds the exact source-recorded level at `basis.eventTime` and the exact level at the asset endpoint UTC. Prior close, nearest timestamp, interpolation, or shifted-session substitution is absent. |
| P3-CRF13 | Return and currency basis | V1 is price return, not total return. Asset and reference currency match exactly with no FX. Reference-specific calculation venue, calendar, source, and revisions are preserved without shifting the common UTC interval. |
| P3-CRF14 | Divisor continuity and type separation | Provider-published index evidence attests divisor continuity with reference-specific types. ADR-014/ADR-016 split/reverse-split share-basis enums and evidence are not reused, cast, or relabelled for an index. |
| P3-CRF15 | Separate deterministic calculators | Future benchmark and sector calculators have separate semantic input/result types and must use ADR-017's exact `(endpoint-basis)/basis` arithmetic: one subtraction, one scale-12 `HALF_EVEN` division, no intermediate or second rounding, decimal-ratio units, and fail-closed representability. They do not cast to `AssetReturnResult` or trust a provider-calculated return. |
| P3-CRF16 | Null DEMO and lifecycle firewall | Current DEMO benchmark/sector/alpha fields remain null. This foundation sets no outcome status, `dataComplete`, retry, permanence, cancellation, latest-correction, freshness, scheduling, methodology activation, fingerprint, persistence, aggregation, ranking, API/UI, or publication behavior. |
| P3-CRF17 | Staged delivery and external boundary | Benchmark assignment precedes sector taxonomy/assignment, reference-level pairs, independent calculators, and source-local readiness. No key, account, paid plan, license, secret, or network is needed now; P5 must establish provider entitlements and storage/display/derived/redistribution rights before non-DEMO evidence or scoped secrets exist. |

## Required comparative-foundation documentation and negative checks

- Documentation marker parity, decision-only surface, forbidden-inference
  wording, staged-order consistency, and protected production/test baseline:
  **PASS** — the exact ADR-026 marker occurs once in each of the four required
  documents, no premature runtime surface exists, and the protected baseline
  remains exactly 189 files with SHA-256
  `bc251da006f897de69744ee8aec2400da5d18c38c2945aac03ec46063cc18721`.
  The API-test plus application-owned web source/config baseline is exactly
  197 files with SHA-256
  `12fb3dbacd830f86ca0790284e2a2833d0314bcf56291337d7698d40720ca45d`;
  a temporary sector-index-test plus web-taxonomy mutation pair produces the
  required nonzero guard exit.
- Complete API regression, repository CI bodies, workflow YAML parsing, Compose
  validation, and patch hygiene: **PASS** — Maven ran 1030 tests with zero
  failures, errors, or skips and completed the build; all 31 embedded Python
  bodies compile under optimization, all 30 locally executable bodies pass,
  SnakeYAML parses the exact four jobs, Compose configuration is valid, and
  `git diff --check` is clean.

## Deferred work and implementation order

1. ADR-025 remains the sole asset-return/directional-win shared-readiness
   ownership decision; do not add a separate asset-return readiness receipt.
2. ADR-026 records the received comparative-return approval and exact
   assignment, PIT, applicability, price-index, currency, continuity, and
   no-inference foundation without adding executable policy.
3. ADR-027 implements the independently typed benchmark assignment evidence,
   exact PIT selector, policy definition/hash, and golden matrix.
4. ADR-028 defines and versions the provider-neutral WSR sector taxonomy and
   exact provider-mapping policy without claiming a provider mapping set. Add
   basis-frozen sector assignment evidence next; create actual mappings only
   after provider selection and rights approval.
5. Add independent benchmark/sector reference-level pair evidence, calculators,
   and source-local readiness in that order. Keep DEMO outcome values null
   until dedicated canonical evidence and reproducibility contracts exist.
6. Add provider-side raw intraday/tick aggregation only after versioning
   no-trade, halt, auction, bar-straddle, correction-sequence, and raw-coverage
   proof semantics. P5 must first establish entitled historical intraday/tick,
   calendar, corporate-action, asset/venue data and storage, display,
   derived-data, and redistribution rights.
7. Add MFE/MAE after raw full-window completeness and bullish/bearish sign rules;
   add alpha/sector alpha last, after benchmark/sector reference identity and
   index-continuity price-return policy exist.
8. Add a separate canonical lifecycle policy across all 10 required metrics,
   including intentional non-applicability, before mapping source readiness to
   Pending/Incomplete, retry/freshness, cancellation, latest correction, or
   scheduling. ADR-022, ADR-023, and ADR-024 make none of those inferences.
9. Persist or expose a non-null metric only with a canonical versioned input
   fixture, reproducible methodology definition/hash, input fingerprint, golden
   test, append-only lineage, and schema/domain/database completeness matrix.

Leaderboard aggregates, sample confidence, ranking publication, schedulers,
provider integration, historical bars, and real/licensed market data remain
outside this slice.

## Point-in-time explicit benchmark assignment V1 boundary

ADR-027 selects benchmark assignment only from explicit point-in-time evidence frozen at the outcome basis event.

- The selector consumes only one exact V1 policy, `OutcomeBasis`, asset ID,
  `evaluationAsOf`, and complete immutable classification and assignment
  candidate lists. It performs no current-state, provider, repository, ticker,
  name, venue, universe, map, or treemap lookup.
- Evidence is visible only when both `availableAt` and `capturedAt` are not
  after `evaluationAsOf`. Future candidates are identical to absent evidence
  before identity, applicability, reason, or cardinality is evaluated.
- One visible classification must match the request basis and asset and contain
  `basis.eventTime` in an explicit start-inclusive/end-exclusive interval.
  Open-ended membership is a sealed value, never null or an invented date.
- Non-equity and known non-US/USD equities are intentional typed
  non-applicability only without a visible assignment. In-scope US/USD equity
  requires exactly one coherent `asset-spx`/INDEX/USD/provider-published price-
  index assignment; every visible mismatch fails closed before ambiguity.
- The source-local result is assignment evidence only. It establishes no
  price level, return, alpha, methodology activation, canonical lifecycle,
  persistence, retry, provider health, API response, or publication.

## Point-in-time explicit benchmark assignment V1 contract gate

| ID | Check | Expected result |
| --- | --- | --- |
| P3-BA01 | Exact source surface | Package `com.wallstreetreceipts.api.domain.outcome.benchmarkassignment` contains exactly `BenchmarkAssignmentPolicyVersion`, `BenchmarkAssetClassificationEvidence`, `BenchmarkAssignmentEvidence`, `BenchmarkAssignmentRequest`, `BenchmarkAssignmentResolution`, and `BenchmarkAssignmentSelector`, plus exactly one source-local `BenchmarkAssignmentSelectorGoldenTest`. No helper, service, controller, repository, provider, scheduler, resource, or web file is added. |
| P3-BA02 | Exact policy identity | Policy enum contains only `POINT_IN_TIME_EXPLICIT_US_EQUITY_ASSET_SPX_ASSIGNMENT_V1`. ADR-027 locks the exact single-line 4261-byte ASCII/UTF-8 definition and SHA-256 `7318514c2f50eda16b2d7ef35bc68d00d6a8b18a0f09f77130525fca2f32da69`; returned definition bytes are defensive and every result context echoes the digest. |
| P3-BA03 | Exact request identity | Request fields are exactly policy, complete `OutcomeBasis`, canonical asset ID, microsecond `evaluationAsOf`, and immutable non-null classification/assignment candidate lists. `evaluationAsOf < basis.eventTime` and any null list/member fail closed. Original and correction bases remain independent. |
| P3-BA04 | Complete classification evidence | Classification preserves evidence/provider-event identity, basis, asset/type, primary venue, sourced uppercase ISO 3166-1 alpha-2 country, ISO currency, source ID/revision, provenance, explicit effective interval, `availableAt`, and `capturedAt`. Canonical text is nonblank and trimmed; `availableAt <= capturedAt`. |
| P3-BA05 | Complete assignment evidence | Assignment independently preserves the same basis/classification identity plus mapping source/revision/provenance, explicit interval, benchmark ID/type/currency, the closed four-value reference kind, and PIT timestamps. It is not inferred from or collapsed into classification evidence. |
| P3-BA06 | Explicit interval | Membership is exactly start-inclusive/end-exclusive at `basis.eventTime`. End is sealed as `OpenEnded` or `EndsAtExclusive(value)`; finite end must strictly follow start, equality with start is invalid, equality with end is outside, and all instants are microsecond-safe. |
| P3-BA07 | PIT filter-first | Both `availableAt <= evaluationAsOf` and `capturedAt <= evaluationAsOf` are required. Future exact, invalid, conflicting, or duplicate candidates are invisible to output and all reasoning; PIT equality is visible. No clock, processing time, current time, or freshness inference exists. |
| P3-BA08 | Classification selection | Visible candidates are checked in fixed order for missing, basis mismatch, asset mismatch, effective-interval mismatch, then ambiguity. Any visible mismatch poisons the candidate set; equal duplicates are ambiguous and input order cannot affect the result. |
| P3-BA09 | Closed applicability | Non-equity maps to `NON_EQUITY`. Equity country/currency truth is exactly `NON_US_PRIMARY_VENUE`, `NON_USD_CURRENCY`, or `NON_US_PRIMARY_VENUE_AND_NON_USD_CURRENCY`; non-equity dominates country/currency. Missing/conflicting classification is unavailable, never intentional N/A. |
| P3-BA10 | Exact assignment scope | In scope means exactly `AssetType.EQUITY`, country `US`, and currency USD. With no visible assignment it returns `ASSIGNMENT_MISSING_AS_OF`; an out-of-scope classification with no assignment is N/A. Visible assignments first pass the common basis/asset/type/venue/country/currency/interval coherence gates; any coherent visible assignment for an out-of-scope classification is `OUT_OF_SCOPE_ASSIGNMENT_CONFLICT`. |
| P3-BA11 | Exact reference | A resolved mapping coheres with classification basis, asset/type, primary venue/country, currency, and interval and names exactly `asset-spx`, `AssetType.INDEX`, USD, and `PROVIDER_PUBLISHED_PRICE_INDEX`. Total-return, non-provider-published, and unknown kinds cannot resolve. |
| P3-BA12 | Fixed assignment precedence | The 14 assignment gates run in ADR-027 order from missing/coherence through out-of-scope conflict, benchmark ID/type/currency/kind, and ambiguity. Known visible mismatch precedes duplicate ambiguity; no deduplication, filtering toward valid evidence, latest revision, provider preference, or fallback exists. |
| P3-BA13 | Exact result | Resolution is sealed as exactly `Resolved(context,classificationEvidence,assignmentEvidence)`, `NotApplicable(context,classificationEvidence,reason)`, or `Unavailable(context,reason)`. Selected evidence objects are preserved exactly; unavailable adds no guessed evidence. Public resolved/N/A constructors enforce local consistency, while only the selector attests PIT membership, whole-candidate precedence, and cardinality. |
| P3-BA14 | No inference | Ticker, issuer name, exchange-like text, current master data, `MarketSnapshot.spx`, P2 `sp500`, maps/treemaps, current/latest row, nearest interval, provider preference, and fallback cannot create or repair an assignment. |
| P3-BA15 | Pure reverse boundary | Production imports only required Java value types, `PersistentInstant`, `AssetType`, and exact `OutcomeBasis`. Only classification evidence, assignment evidence, request, and resolution may reference `OutcomeBasis`; no session/horizon selector, price, calculator, framework, JSON, persistence, repository, provider, network, scheduler, `Clock`, locale-dependent decision, random, or floating-point dependency is permitted. No production file outside the six-file package references the new types. |
| P3-BA16 | Product and lifecycle firewall | Schemas, canonical fixtures, manifest, OpenAPI, Flyway, database, API/provider behavior, resources, and web source remain unchanged. Existing DEMO benchmark/sector/alpha metrics remain null. No result maps directly to `OutcomeEvaluationStatus`, `dataComplete`, retry, permanence, cancellation, freshness, scheduling, methodology activation, aggregation, ranking, or publication. |
| P3-BA17 | External boundary | The disconnected policy needs no API key, account, paid plan, provider license, secret, or network. Before non-DEMO evidence is supplied, P5 must select entitled classification/venue/currency/benchmark-mapping sources and establish storage, display, derived-data, and redistribution rights. |
| P3-BA18 | Repository CI contract | CI locks exact ADR/README/acceptance/log marker parity, the six-production-file/one-test surface, policy bytes/hash, reason orders, PIT/interval/precedence boundaries, exact 84/84 golden cardinality with nonzero mismatch exit, reverse isolation, null DEMO publication, 195-file protected baseline SHA-256 `562e6402b06c4b549d518b5935d7c6525d795708d135bb4c8dd4af8c674d0640`, and 198-file test/web baseline SHA-256 `0f6c5358ea2564c562159d375b42985e8aafd603b1673fcc404aab83bcf74a0e`. The ADR-026-era baselines remain independently reproducible by excluding exactly the ADR-027 six-plus-one files. |

## Required benchmark-assignment golden and verification checks

- Exercise exactly 84 `BenchmarkAssignmentSelectorGoldenTest` invocations
  across 31 test methods:
  canonical policy bytes/hash/defensive reads, closed public surfaces, original
  and correction identity, finite/open interval boundaries, PIT equality and
  future invisibility, every classification and assignment reason/precedence
  branch, scope truth table, ambiguity, defensive lists, constructor
  rejection, evidence preservation, and locale/time-zone/input-order replay.
- Focused source-local golden: **PASS** — Surefire reports exactly 84 tests,
  zero failures, zero errors, and zero skipped.
- Complete API Maven regression: **PASS** — full `verify` reports exactly
  1114 tests with zero failures, zero errors, and zero skipped, and Spring Boot
  repackage completes.
- Repository CI Python execution, workflow YAML parsing, Compose validation,
  mutation rejection, and patch hygiene: **PASS**. All 32 embedded Python
  bodies compile under optimization, all 31 locally executable bodies pass,
  and the final cross-stack body remains syntax-checked for service execution.
  SnakeYAML parses the exact four jobs, Compose configuration is valid, and
  `git diff --check` is clean. A temporary README marker mutation produces the
  required nonzero ADR-027 guard exit and is removed before final validation.
- ADR-028 now owns the sector taxonomy identity/version, canonical node
  bytes/hash, and provider-to-canonical mapping-policy decision. No sector
  assignment code, actual provider mapping set, or reference index is part of
  the benchmark-assignment slice.

## Provider-neutral WSR Economic Activity V1 boundary

ADR-028 locks WSR Economic Activity V1 and exact point-in-time provider-node mapping semantics.

- `wsr-economic-activity` version `1.0.0` is an original WSR single-level
  taxonomy with one unassignable root and exactly twelve closed assignable
  leaves. It makes no GICS, ICB, SIC, or NAICS equivalence claim.
- Assignment means the primary operating activity as explicitly evidenced.
  `UNKNOWN`, `OTHER`, and unclassified nodes do not exist. Diversified
  Operations requires affirmative no-single-primary-activity evidence and is
  never a missing/conflict fallback.
- Provider-node identity is exact provider, scheme, scheme revision, and node
  ID. Labels and definitions remain preserved evidence; raw, normalized, and
  fuzzy text matching are forbidden.
- Mapping evidence is PIT-visible only when both timestamps are not after
  `evaluationAsOf`, and its start-inclusive/end-exclusive interval must contain
  the exact original/correction basis event. Future evidence is absent for all
  reasons, conflict, and cardinality decisions.
- Missing and explicit not-mapped dispositions are unavailable. Equal
  duplicates and overlapping visible rows are ambiguous; disagreeing targets
  conflict. No current/latest row, nearest interval, provider preference,
  silent deduplication, automatic migration, P2 label, or fallback may repair
  a mapping.
- No provider, provider mapping set, issuer membership, sector reference index,
  sector assignment, return, readiness, API, or product publication is added.

## Provider-neutral WSR Economic Activity V1 contract gate

| ID | Check | Expected result |
| --- | --- | --- |
| P3-ST01 | Exact decision-only surface | ADR-028, README, this acceptance gate, implementation log, and repository CI are the only intended changes. No Java production/test file, package, schema, fixture, manifest entry, OpenAPI, Flyway, database, provider, resource, API, or web behavior is added. |
| P3-ST02 | Exact taxonomy identity | Taxonomy ID is `wsr-economic-activity`, version is `1.0.0`, kind is provider-neutral single-level economic activity, and assignment criterion is `PRIMARY_OPERATING_ACTIVITY_AS_EXPLICITLY_EVIDENCED`. |
| P3-ST03 | Exact closed node set | Root `wsr-sector-root` is unassignable. The twelve leaf IDs, labels, definitions, and order are exactly ADR-028; IDs and labels are unique. V1 adds no industry hierarchy. |
| P3-ST04 | No missing-value node | `UNKNOWN`, `OTHER`, and unclassified members are absent. Missing, conflicting, ambiguous, future, unsupported, or unmapped evidence is unavailable rather than a taxonomy node, zero, or inferred value. |
| P3-ST05 | Diversified is affirmative | `wsr-sector-diversified-operations` requires explicit source evidence of materially diversified operations with no single primary activity represented by another V1 node. It is never a catch-all or fallback. |
| P3-ST06 | Exact taxonomy bytes | ADR-028 contains exactly one canonical single-line 3824-byte ASCII/UTF-8 taxonomy definition with SHA-256 `820ce3ea264d67312fe4f2efe346631a81d74248e9a7f041793d65d8ef0d62ae`. Any semantic, node, order, label, or definition change requires a new version and hash. |
| P3-ST07 | Exact mapping-policy identity | Policy is exactly `POINT_IN_TIME_EXPLICIT_PROVIDER_NODE_TO_WSR_ECONOMIC_ACTIVITY_V1`, bound to taxonomy `wsr-economic-activity` `1.0.0` and definition hash `820ce3ea264d67312fe4f2efe346631a81d74248e9a7f041793d65d8ef0d62ae`. Its canonical 4395-byte ASCII/UTF-8 definition has SHA-256 `ba12a277d5ffe266af1745b98948a1e2206494ac31904f31a419d973d5067e77`. |
| P3-ST08 | Complete mapping evidence | The exact 23-field order preserves mapping/provider event identity, policy and mapping-set versions/hashes, taxonomy version/hash, provider scheme revision/node label/definition, disposition, source/revision/provenance, effective interval, `availableAt`, and `capturedAt`. |
| P3-ST09 | Exact provider and target identity | Mapping identity is `(providerId, providerSchemeId, providerSchemeRevision, providerNodeId)` with case-sensitive unnormalized Unicode code-point equality. Labels are preserved evidence only and cannot be an identity, raw/normalized/fuzzy matching key, or inferred equivalence. `Mapped` requires a recorded provider-node definition and an exact taxonomy ID/version/hash plus one closed assignable leaf ID; root or unknown IDs fail closed. |
| P3-ST10 | Closed disposition | Disposition is exactly `Mapped(canonicalNodeId)` or `NotMapped(reason)`. Not-mapped reasons are exactly `NO_CANONICAL_EQUIVALENT`, `PROVIDER_NODE_TOO_BROAD`, and `PROVIDER_DEFINITION_UNAVAILABLE`; missing and not-mapped both remain unavailable. |
| P3-ST11 | Exact PIT and interval | Mapping evidence requires `availableAt <= capturedAt`; visibility requires both timestamps `<= evaluationAsOf`. The effective interval is start-inclusive/end-exclusive at `basis.eventTime` with an explicit open-ended variant. Future evidence cannot affect output, reason, conflict, or cardinality. |
| P3-ST12 | Fail-closed multiplicity | Many provider nodes may map to one WSR node, but one identity has one applicable disposition. Equal duplicates are ambiguous without deduplication; disagreeing targets conflict; overlapping visible rows are ambiguous or conflicting. |
| P3-ST13 | Versioned mapping set | Future mapping sets use the exact manifest/entry field orders, globally unique evidence IDs, canonical UTC microsecond effective starts, and case-sensitive unnormalized Unicode code-point sort. Entries must correlate exactly to manifest identity/source fields. SHA-256 omits every `mappingSetDefinitionHash` occurrence to avoid self-reference, then every populated occurrence must equal the digest. Any other byte change requires a new mapping-set version/hash; silent migration is forbidden. No actual mapping set is claimed now. |
| P3-ST14 | No synthetic or proprietary inference | P2 map/treemap labels, ticker, issuer name, current/latest row, nearest interval, provider preference, label matching, and fallback cannot create or repair a mapping. GICS/ICB code, hierarchy, definition, membership, or equivalence is absent without expressly entitled provider evidence. |
| P3-ST15 | Reference and lifecycle firewall | A canonical node does not prove a provider-published sector price index. Sector assignment, reference evidence, return calculation, readiness, outcome lifecycle, persistence, retry, aggregation, ranking, and publication remain later independently reviewed contracts; DEMO comparative metrics remain null. |
| P3-ST16 | External boundary | This decision needs no API key, account, paid plan, license, secret, or network. P5 must select a provider and establish historical, storage, display, derived-crosswalk, cache, and redistribution rights before real mappings or scoped credentials exist. |
| P3-ST17 | Repository CI contract | CI locks four-document marker parity, exact canonical JSON bytes/hashes and semantic fields, absence of runtime/provider data, and unchanged production and test/web baselines: 195 files / `562e6402b06c4b549d518b5935d7c6525d795708d135bb4c8dd4af8c674d0640` and 198 files / `0f6c5358ea2564c562159d375b42985e8aafd603b1673fcc404aab83bcf74a0e`. |

## Required WSR taxonomy documentation and negative checks

- Canonical taxonomy/mapping-policy JSON parse, minimal serialization, exact
  ASCII/UTF-8 byte lengths and SHA-256 values, node/field/reason order, PIT and
  interval rules, source-license boundaries, four-document marker parity, and
  unchanged production/test/web baselines: **PASS**. The dedicated ADR-028
  guard independently verifies the exact 3824/4395-byte definitions and both
  locked hashes while preserving the 195/198 protected file baselines.
- Complete API regression, all repository CI Python bodies, workflow YAML,
  Compose configuration, marker/definition mutation rejection, patch hygiene,
  and user-owned `apps/web/next-env.d.ts` preservation: **PASS**. Maven reports
  1114 tests with zero failures, errors, or skips; all 33 embedded Python bodies
  compile under optimization and all 32 locally executable bodies pass;
  SnakeYAML retains four jobs; Compose validates; marker, taxonomy-byte, and
  mapping-policy-byte mutations each exit nonzero and are restored; and
  `git diff --check` is clean without staging the user-owned file.
- ADR-029 now implements the separately reviewed basis-frozen sector-assignment
  policy. An actual provider mapping set remains blocked until provider
  selection and historical, storage, display, derived-crosswalk, cache, and
  redistribution rights are documented.

## Point-in-time explicit WSR sector assignment V1 boundary

ADR-029 freezes WSR sector assignment to explicit point-in-time membership and mapped provider-node evidence.

- The selector consumes only the exact V1 policy, one complete
  original/correction `OutcomeBasis`, canonical asset ID, microsecond
  `evaluationAsOf`, a caller-attested mapping-set ID/version/hash, and complete
  immutable classification, membership, and mapping candidate lists.
- Classification, membership, and mapping evidence are independently typed.
  Membership preserves exact provider/scheme/revision/node identity and the
  mapping row preserves all 23 ADR-028 fields; no benchmark-assignment type is
  shared, imported, cast, or reused.
- Both PIT timestamps must be visible before any identity, reason, conflict,
  or cardinality decision. Membership and mapping are frozen at the exact basis
  event under start-inclusive/end-exclusive intervals with explicit open ends.
- Equity is the exact sector V1 scope. Country and currency remain required
  evidence but are not scope gates. A coherent non-equity with no membership
  is intentional `NON_EQUITY`; any visible non-equity membership conflicts.
- Resolution requires one coherent membership and one exact mapped ADR-028
  row using the locked taxonomy and mapping-policy hashes and one of the twelve
  assignable WSR leaves. Missing, not-mapped, invalid, future, duplicate, or
  conflicting evidence remains typed unavailable.
- The request/result echoes and row-matches a caller-attested mapping-set
  identity. The selector neither computes its manifest digest nor attests full
  entry-to-manifest correlation; no real provider mapping set exists here.
- The exact executable policy has 36 unavailable reasons in ADR-029 order and
  a 9307-byte canonical definition with SHA-256
  `52d9f705a3a8a965a6fca79d36bd94ed8836642f1a2c4e5f29a878d0a267311c`.
- This source-local result is assignment evidence only. It proves no sector
  reference index, level, return, alpha, readiness, lifecycle state,
  persistence, API response, or product publication.

## Point-in-time explicit WSR sector assignment V1 contract gate

| ID | Check | Expected result |
| --- | --- | --- |
| P3-SA01 | Exact source surface | Package `com.wallstreetreceipts.api.domain.outcome.sectorassignment` contains exactly `SectorAssignmentPolicyVersion`, `SectorAssetClassificationEvidence`, `SectorMembershipEvidence`, `SectorMappingEvidence`, `SectorAssignmentRequest`, `SectorAssignmentResolution`, and `SectorAssignmentSelector`, plus exactly one source-local `SectorAssignmentSelectorGoldenTest`. No helper, service, controller, repository, provider, scheduler, resource, or web file is added. |
| P3-SA02 | Exact policy identity | The policy enum contains only `POINT_IN_TIME_EXPLICIT_WSR_ECONOMIC_ACTIVITY_SECTOR_ASSIGNMENT_V1`. Its canonical definition is exactly 9307 single-line ASCII/UTF-8 bytes with SHA-256 `52d9f705a3a8a965a6fca79d36bd94ed8836642f1a2c4e5f29a878d0a267311c`; returned bytes are defensive and every result context echoes the digest. |
| P3-SA03 | Exact ADR-028 binding | The policy requires taxonomy `wsr-economic-activity` version `1.0.0`, definition hash `820ce3ea264d67312fe4f2efe346631a81d74248e9a7f041793d65d8ef0d62ae`, the exact twelve ordered assignable leaves, and mapping policy `POINT_IN_TIME_EXPLICIT_PROVIDER_NODE_TO_WSR_ECONOMIC_ACTIVITY_V1` with hash `ba12a277d5ffe266af1745b98948a1e2206494ac31904f31a419d973d5067e77`. Root, unknown, other, unclassified, and new IDs fail closed. |
| P3-SA04 | Exact request identity | Request fields are exactly policy, complete basis, asset ID, microsecond `evaluationAsOf`, mapping-set ID/version/hash, and immutable non-null classification, membership, and mapping candidate lists. Evaluation before `basis.eventTime`, null lists/members, noncanonical mapping-set text, and non-lowercase-64-hex hash fail locally. |
| P3-SA05 | Complete classification evidence | Classification preserves evidence/provider-event identity, basis, asset/type, primary venue, sourced uppercase ISO country, ISO currency, source/revision/provenance, explicit interval, `availableAt`, and `capturedAt`. It remains independent of benchmark evidence. |
| P3-SA06 | Complete membership evidence | Membership preserves the classification coherence fields plus exact provider, scheme, scheme revision, node ID and source label, membership source/revision/provenance, explicit interval, and both PIT timestamps. Provider identity is non-null/non-empty with no stripping, normalization, or case folding; label is preserved evidence only. |
| P3-SA07 | Exact mapping evidence | Mapping preserves ADR-028's exact 23 ordered fields, `Recorded(value, languageTag)` or `NotPublished`, and `Mapped(canonicalNodeId)` or `NotMapped(reason)`. Not-mapped reasons remain exactly `NO_CANONICAL_EQUIVALENT`, `PROVIDER_NODE_TOO_BROAD`, and `PROVIDER_DEFINITION_UNAVAILABLE`. |
| P3-SA08 | Explicit intervals | Classification, membership, and mapping intervals are start-inclusive/end-exclusive at `basis.eventTime`; end is exactly `OpenEnded` or `EndsAtExclusive(value)`. Finite end follows start, equality with the start is invalid, equality with the end is outside, and all instants are microsecond-safe. |
| P3-SA09 | PIT filter first | Visibility requires both `availableAt <= evaluationAsOf` and `capturedAt <= evaluationAsOf`. Future exact, invalid, conflicting, and duplicate rows are invisible to output and every reason, conflict, and cardinality gate. PIT equality is visible. |
| P3-SA10 | Classification selection | Visible classification candidates are checked in fixed order for missing, basis mismatch, asset mismatch, interval mismatch, then ambiguity. Any mismatch poisons the set, equal duplicates remain ambiguous, and input order cannot change the result. |
| P3-SA11 | Exact applicability | Only `AssetType.EQUITY` is in scope. Venue country and currency remain required preserved evidence but do not affect scope. With no membership, a coherent non-equity yields only `NotApplicable(NON_EQUITY)` and an equity yields `MEMBERSHIP_MISSING_AS_OF`; a visible non-equity membership is `OUT_OF_SCOPE_MEMBERSHIP_CONFLICT`. |
| P3-SA12 | Membership selection | Every visible membership must cohere exactly with selected classification basis, asset/type, primary venue/country, currency, and interval before the non-equity conflict and ambiguity gates. Exactly one membership may proceed; no filter-to-valid, deduplication, latest row, or provider preference exists. |
| P3-SA13 | Caller-attested mapping-set boundary | Request and context own mapping-set ID/version/hash and every visible mapping row must match them. The selector does not calculate a manifest hash, attest global evidence-ID uniqueness, verify sorting, or prove entry-to-manifest correlation; those remain an ADR-028 caller/provider boundary. Test-only values do not create a real mapping set. |
| P3-SA14 | Exact provider mapping | Mapping provider ID, scheme ID, scheme revision, and node ID must exactly equal selected membership under case-sensitive unnormalized Unicode code-point equality. Raw, normalized, or fuzzy labels cannot match or repair identity. |
| P3-SA15 | Exact mapped target | A resolved row carries the required mapping-policy and taxonomy identities/hashes, contains the basis event, has a `Recorded` provider definition, and maps to exactly one closed assignable leaf. Missing mapping and invalid policy, set, taxonomy, provider, interval, definition, or target evidence fail closed. |
| P3-SA16 | Conflict, ambiguity, and not-mapped | Unequal visible dispositions—including different mapped targets, mapped versus not-mapped, or different not-mapped reasons—produce `MAPPING_CONFLICT`. Any multiple visible rows with the same disposition produce `MAPPING_AMBIGUOUS`, including exact duplicates, distinct rows mapped to the same target, and distinct not-mapped rows carrying the same reason. Conflict precedes ambiguity, which precedes the three single-row not-mapped unavailable outcomes. |
| P3-SA17 | Exact 36 unavailable reasons | `UnavailableReason` order is exactly ADR-029 and the canonical definition: five classification reasons, ten membership reasons, and twenty-one mapping reasons from `CLASSIFICATION_MISSING_AS_OF` through `MAPPING_NOT_MAPPED_PROVIDER_DEFINITION_UNAVAILABLE`. Selector evaluation follows the matching fixed sequence before `RESOLVE`; enum ordinal/name parsing is absent. |
| P3-SA18 | Exact result | Resolution variants are exactly `Resolved(context,classificationEvidence,membershipEvidence,mappingEvidence)`, `NotApplicable(context,classificationEvidence,reason)`, and `Unavailable(context,reason)`. Selected records are preserved exactly; unavailable adds no guessed evidence. Only the selector attests request membership, PIT filtering, whole-set precedence, conflict, and cardinality. |
| P3-SA19 | No inference or fallback | Ticker, issuer name, current master data, current/latest row, nearest interval, provider preference, raw/normalized/fuzzy label matching, silent deduplication, P2 map/treemap labels, unknown/other/unclassified nodes, and fallback cannot create or repair an assignment. |
| P3-SA20 | Pure reverse boundary | Production imports only required deterministic JDK value types, `PersistentInstant`, `AssetType`, and exact `OutcomeBasis`. No benchmark package, price, calculator, JSON mapper, Spring, persistence, repository, provider, network, scheduler, `Clock`, locale-dependent decision, random, or floating-point dependency is allowed. No production file outside the seven-file package references the new types. |
| P3-SA21 | Product and lifecycle firewall | Schemas, canonical fixtures, manifest, OpenAPI, Flyway, database, API/provider behavior, resources, and web source remain unchanged. DEMO comparative metrics remain null. No result maps directly to outcome status, completeness, retry, permanence, cancellation, freshness, scheduling, methodology activation, aggregation, ranking, or publication. |
| P3-SA22 | External boundary | The disconnected policy needs no API key, account, paid plan, domain, provider license, secret, or network. Actual mappings, membership evidence, or adapters remain blocked until provider selection and historical, storage, display, derived-crosswalk, cache, and redistribution rights are documented. GICS/ICB require written commercial rights. Public SIC/NAICS use requires independent source, applicability, and crosswalk review; a future SEC adapter needs a compliant named `User-Agent` but no API key. |
| P3-SA23 | Repository CI contract | CI must lock four-document marker parity, exact seven-production-file/one-test surface, policy JSON bytes/hash and field/reason order, 134/134 golden cardinality, reverse isolation, null DEMO publication, current production at exactly 202 files / SHA-256 `b1ae60b9c550353960687cb9973e2909e965a5e3eb98bb23b39b0a7f01a2a899`, current API-test/web at 199 files / `59726e88e5bf7d831f16beaa693689ca799990d355733a1115c07a285a7e5293`, and independent replay of ADR-028 production at 195 files / `562e6402b06c4b549d518b5935d7c6525d795708d135bb4c8dd4af8c674d0640` and test/web at 198 files / `0f6c5358ea2564c562159d375b42985e8aafd603b1673fcc404aab83bcf74a0e` by excluding exactly the new seven-plus-one files. |

## Required sector-assignment golden and verification checks

- Exercise exactly 134 `SectorAssignmentSelectorGoldenTest` invocations:
  canonical bytes/hash/defensive reads, closed public surfaces, original and
  correction identity, finite/open interval boundaries, PIT equality and
  future invisibility, equity/non-equity truth, every classification,
  membership, and mapping reason/precedence branch, exact provider identity,
  all twelve leaves, conflict/ambiguity/not-mapped behavior, mapping-set echo,
  evidence preservation, constructor rejection, defensive lists, and
  locale/time-zone/input-order replay: **PASS** — exactly 134 invocations with
  zero failures, errors, or skips.
- Focused source-local golden and complete API Maven verification:
  **PASS** — focused 134/134 and full API 1248/1248, with zero failures,
  errors, or skips including Docker/PostgreSQL/Flyway integration.
- Repository CI Python execution, workflow YAML parsing, Compose validation,
  marker/policy/cardinality mutation rejection, current and ADR-028 legacy
  baseline replay, patch hygiene, and preservation of the user-owned
  `apps/web/next-env.d.ts`: **PASS** — all 34 embedded Python bodies compile,
  all 33 locally executable bodies pass, SnakeYAML retains four jobs, Compose
  validates, all three mutations exit nonzero and are restored, both baseline
  families replay, and the user-owned file remains unstaged and unchanged by
  this slice. Current production is 202 files /
  `b1ae60b9c550353960687cb9973e2909e965a5e3eb98bb23b39b0a7f01a2a899`;
  current API-test/web is 199 files /
  `59726e88e5bf7d831f16beaa693689ca799990d355733a1115c07a285a7e5293`.
  Excluding the exact ADR-029 seven-plus-one surface reproduces ADR-028
  production at 195 files /
  `562e6402b06c4b549d518b5935d7c6525d795708d135bb4c8dd4af8c674d0640`
  and test/web at 198 files /
  `0f6c5358ea2564c562159d375b42985e8aafd603b1673fcc404aab83bcf74a0e`.
- No actual provider mapping set, non-DEMO membership, canonical fixture,
  schema, API, database, provider adapter, reference index, return, or web
  behavior may appear in this slice. Provider selection and rights approval
  remain prerequisites for real data.

## Point-in-time independent benchmark/sector reference-level pairs V1 boundary

ADR-030 resolves benchmark and sector reference-level pairs independently from explicit point-in-time provider-published price-index evidence over the exact basis-event-to-asset-endpoint UTC interval.

- The slice contains two independently typed seven-file production packages
  and two independently typed selector goldens. It has no shared generic pair,
  cross-kind policy/evidence/result, cast, or fallback.
- Each leg consumes its complete ADR-027 or ADR-029 assignment resolution and
  complete ADR-014 endpoint-price resolution. No free-standing basis, horizon,
  endpoint instant, asset, venue, currency, or evaluation cutoff is accepted.
- Assignment basis and cutoff always match the endpoint context. Anchor
  validity is derived from catalog PIT, catalog-to-horizon identity, and
  binding PIT facts—not an endpoint unavailable label. Asset, then resolved or
  not-applicable classification venue/currency, match the endpoint binding only
  after the anchor is usable; unavailable assignment venue/currency are not
  inferred.
- The exact five result variants are `Resolved`, `NotApplicable`,
  `AssignmentUnavailable`, `EndpointAnchorUnavailable`, and
  `EvidenceUnavailable`. Assignment branches precede the exact three
  independently derived catalog/binding anchor failures. The anchor variant
  carries its own exact three-value reason enum; all sixteen upstream endpoint
  unavailable labels are ignored when the preserved anchor facts are coherent.
- The binding interval contains both exact UTC endpoints. Each resolved leg
  preserves one explicit provider-published price-index binding, one exact
  source-recorded level at each endpoint, and one exact divisor-continuity
  attestation linked to both observations. Calendar identity and its source
  identity remain explicit on every row.
- Benchmark binding links the selected assignment evidence. Sector binding
  links selected mapping evidence, exact WSR taxonomy identity, and exact
  canonical node. Benchmark levels/continuity retain the exact canonical
  benchmark asset; sector binding/levels/continuity retain an explicit
  reference asset whose type is `INDEX`. A WSR node alone is not a provider
  index, reference asset, level, calendar, currency, or divisor claim.
- Reference/index identity is exact and unnormalized. Currency matches the
  asset endpoint binding with no FX; calculation venue, calendar/revision,
  calendar source/revision, level source/revision, and continuity
  source/revision stay explicit without shifting the common UTC interval.
- Every visible mismatch fails closed before cardinality, future evidence is
  invisible, and equal duplicates remain ambiguous. No prior close, nearest
  timestamp, interpolation, shifted session, total-return index, ETF, current
  basket, market-cap proxy, provider-return field, provider preference,
  deduplication, or fallback may repair evidence.

## Independent reference-level-pair contract gate

| ID | Check | Expected result |
| --- | --- | --- |
| P3-RLP01 | Exact source surface | Package `benchmarkreferencepair` contains exactly `BenchmarkReferenceLevelPairPolicyVersion`, `BenchmarkReferenceIndexEvidence`, `BenchmarkReferenceLevelObservation`, `BenchmarkIndexDivisorContinuityEvidence`, `BenchmarkReferenceLevelPairRequest`, `BenchmarkReferenceLevelPairResolution`, and `BenchmarkReferenceLevelPairSelector`; `sectorreferencepair` contains the exact seven Sector analogues. Test surface contains only the two corresponding `SelectorGoldenTest` files. |
| P3-RLP02 | Independent ownership | Benchmark and sector have separate policy definitions/hashes, evidence, requests, resolutions, selectors, reasons, and goldens. No generic reference-pair type, nullable kind, shared result, cross-cast, or cross-leg fallback exists. |
| P3-RLP03 | Exact policy identities | Benchmark policy is only `POINT_IN_TIME_EXACT_BENCHMARK_PRICE_INDEX_LEVEL_PAIR_V1`, exact 9342 bytes, SHA-256 `2394b535c1061d32c647504a303b6f1e4ec2fe88e6017d9ff335d12087a5f73d`. Sector is only `POINT_IN_TIME_EXACT_SECTOR_PRICE_INDEX_LEVEL_PAIR_V1`, exact 9806 bytes, SHA-256 `4224648ba01104fd3e96319158c7d6b42da472e9aa6f2ab22ef9fccf43da7e4a`. Bytes are defensive and contexts echo their exact digest. |
| P3-RLP04 | Exact upstream policies | Benchmark requires ADR-027 hash `7318514c2f50eda16b2d7ef35bc68d00d6a8b18a0f09f77130525fca2f32da69`; sector requires ADR-029 hash `52d9f705a3a8a965a6fca79d36bd94ed8836642f1a2c4e5f29a878d0a267311c`. Both require ADR-014 hash `37e37aba9302d77366cef4129f77a82b7ccb2f1937bfffc0315ea8d0bc6b1f76` and nested strict-horizon hash `550087efe7ddf2ba31974c89c2740ab79df986eefef48919c32c56a3232f8dc1`. |
| P3-RLP05 | Full upstream receipts | Each request preserves the complete assignment resolution, complete endpoint-price resolution, and four immutable non-null candidate lists; each resolution context preserves the two complete upstream resolutions. There is no direct `OutcomeBasis` or horizon input/import. |
| P3-RLP06 | Exact upstream topology | Basis and evaluation-as-of always cohere. Asset co-identity is enforced only after context facts prove a usable endpoint anchor; resolved/N/A classification venue and currency then cohere with endpoint binding. Assignment unavailable has no provable classification venue/currency, so neither is invented. |
| P3-RLP07 | Five exact branches | Results are exactly `Resolved(context,referenceIndexEvidence,basisLevelObservation,endpointLevelObservation,divisorContinuityEvidence)`, `NotApplicable(context)`, `AssignmentUnavailable(context)`, `EndpointAnchorUnavailable(context,reason)`, and `EvidenceUnavailable(context,reason)`. Anchor and local evidence reasons use independent enums; complete upstream receipts remain in context. |
| P3-RLP08 | Exact branch precedence | Assignment N/A, assignment unavailable, endpoint-anchor unavailable, local unavailable reasons in declared order, then resolve. N/A or missing evidence never becomes zero, false, loss, or another branch. |
| P3-RLP09 | Exact endpoint anchor | The selector never trusts `EndpointPriceResolution.Unavailable.reason`. It derives an independent `EndpointAnchorUnavailableReason` in exact precedence from catalog PIT visibility, catalog calendar/revision equality with the horizon, then binding PIT visibility: `CATALOG_NOT_KNOWN_AS_OF`, `CATALOG_EVIDENCE_MISMATCH`, or `BINDING_NOT_KNOWN_AS_OF`. `EndpointAnchorUnavailable(context,reason)` requires a resolved assignment and exact fact-derived reason; resolved/local-unavailable constructors reject factual anchor failures. |
| P3-RLP10 | Endpoint-observation independence | All sixteen possible ADR-014 unavailable labels are ignored when the retained context has a mature, coherent catalog/horizon/binding anchor. `ENDPOINT_NOT_REACHED_AS_OF` is local if and only if the exact asset endpoint UTC is after evaluation-as-of; once reached, independently complete reference evidence may resolve. |
| P3-RLP11 | Exact UTC interval | The interval is exactly nested horizon basis event time through nested horizon endpoint-session close. Binding effective interval is start-inclusive/end-exclusive and contains both instants with an explicit open or finite end. Prior close, nearest, interpolation, or shifted-session substitution is absent. |
| P3-RLP12 | PIT filter and multiplicity | Visibility is exactly `availableAt <= evaluationAsOf && capturedAt <= evaluationAsOf`. Future evidence affects no output, reason, or cardinality. Any visible mismatch fails before cardinality; duplicates remain ambiguous and order-independent. |
| P3-RLP13 | Exact benchmark binding | Binding links selected assignment evidence ID/provider-event ID, benchmark asset identity/type, and one separately supplied reference provider/index/definition revision. Both level rows and continuity evidence repeat and exactly match that canonical benchmark asset ID/type. Canonical `asset-spx` assignment alone is not provider index-level evidence. |
| P3-RLP14 | Exact sector binding | Binding links selected mapping evidence ID/provider-event ID, taxonomy `wsr-economic-activity` version `1.0.0` and hash `820ce3ea264d67312fe4f2efe346631a81d74248e9a7f041793d65d8ef0d62ae`, exact mapped canonical node, and an explicit reference asset ID/type `INDEX`. Both level rows and continuity repeat that reference asset. Provider membership-to-WSR mapping and WSR-node-to-provider-index binding remain distinct. |
| P3-RLP15 | Exact provider identity | `referenceProviderId`, `referenceIndexId`, and definition revision use case-sensitive unnormalized Unicode code-point equality and non-null/non-empty validation. Provider label is preserved evidence only, not an identity or match key. |
| P3-RLP16 | Exact price-index semantics | Only `PROVIDER_PUBLISHED_PRICE_INDEX` and `PROVIDER_PUBLISHED_INDEX_LEVEL` resolve. Total-return/non-provider indices, ETFs, ETF price/NAV, current baskets, market-cap/derived proxies, provider returns, and unknown values fail closed. |
| P3-RLP17 | Exact level observations | Basis and endpoint observations link the selected binding ID/provider event and match the leg-specific canonical/reference asset, provider/index/revision/kind, currency, calculation venue, calendar/revision, calendar source/revision, and level source/revision. `observedAt` equals the exact basis or endpoint instant. Level is positive exact `NUMERIC(38,12)` with no rounding. |
| P3-RLP18 | Same currency, explicit reference context | Reference currency equals endpoint binding currency with no FX. Reference calculation venue/calendar/source may differ from the asset's but remains explicit and cannot alter the UTC interval. |
| P3-RLP19 | Exact divisor continuity | One evidence record links selected binding plus both observation/provider-event identities, repeats the leg-specific canonical/reference asset, matches reference, calendar source, and continuity source, covers the exact interval, and attests `PROVIDER_PUBLISHED_INDEX_DIVISOR_CONTINUITY_ATTESTED`. Missing, mismatched, discontinuous, unattested, unknown, or duplicate evidence is unavailable. |
| P3-RLP20 | Exact reason vocabularies | Benchmark has exactly 53 local unavailable reasons and sector exactly 56, in ADR-030 and canonical-definition order. Each leg's independent anchor enum has exactly three ordered fact-derived reasons. Selector gates follow those orders; enum ordinal/name parsing is absent. |
| P3-RLP21 | Asset-pair type firewall | Neither package imports, reuses, relabels, or casts `AssetReturnPricePairResolution`, `CorporateActionContinuity`, or `EndpointPriceAdjustmentBasis`. No benchmark/sector result is an `AssetReturnResult`. |
| P3-RLP22 | Product and lifecycle firewall | No actual provider/index/binding/level/calendar/divisor data, canonical fixture, schema, manifest, OpenAPI, Flyway, DB, provider adapter, API, resource, or web behavior is added. DEMO comparative metrics remain null. No return calculator, readiness, lifecycle, methodology, persistence, aggregation, ranking, or publication is invoked. |
| P3-RLP23 | External boundary | No API key, account, plan, license, secret, or network is needed now. Before non-DEMO evidence, P5 must approve exact index products/feeds, exact-time historical index-level coverage, calendar identity/revision/source rights, divisor/methodology and sector-binding rights, plus storage/cache, display, derived-data, and redistribution rights. Credentials follow rights approval and never enter chat or Git. |
| P3-RLP24 | Repository CI contract | CI locks four-document marker parity, exact fourteen-plus-two surface, both policy definitions/hashes and field/reason order, benchmark 200 and sector 220 golden invocations, reverse isolation, null DEMO publication, current production 216 / `45d06843fd95235221c6716a578915f40a410de8464b0b0ca3a09fff7c29436d`, current test/web 201 / `fd0e3170ba2d64aeb4bf638010915455a27d3a5aed9fe77fb2a724502d96462f`, and ADR-029 replay after exact exclusion at production 202 / `b1ae60b9c550353960687cb9973e2909e965a5e3eb98bb23b39b0a7f01a2a899` and test/web 199 / `59726e88e5bf7d831f16beaa693689ca799990d355733a1115c07a285a7e5293`. |

## Required independent reference-level-pair verification checks

- Exact two-policy canonical JSON extraction, 9342/9806-byte lengths, SHA-256
  values, field/variant order, 53/56 local reasons plus each three-reason anchor
  enum, fourteen-plus-two source surface, reverse isolation, and four-document
  marker parity: **PASS**.
- Focused benchmark selector golden 200/200 and sector selector golden
  220/220—420 total—and complete API Maven regression 1668/1668
  `BUILD SUCCESS` with zero failures, errors, or skips, including
  Docker/PostgreSQL/Flyway integration: **PASS**. Normalized golden-source
  SHA-256 values are benchmark
  `3518b66914656c8225858f8f15fdb60e25576a15b64f148270cda9881e3d8099`
  and sector
  `af9ec3aa0318595027d13eb4748d41bdb587776ef3d2e5c8b3bf477fa7ba439b`.
- The dedicated ADR-030 guard independently passes; 35/35 workflow Python
  heredoc bodies syntax-compile, 34/34 locally executable bodies pass, and the
  final cross-stack body remains intentionally syntax-only. SnakeYAML 2.5
  parses exactly four jobs and Compose config validates: **PASS**.
- Current production 216 / `45d06843fd95235221c6716a578915f40a410de8464b0b0ca3a09fff7c29436d`
  and test/web 201 / `fd0e3170ba2d64aeb4bf638010915455a27d3a5aed9fe77fb2a724502d96462f`
  pass. Excluding the exact fourteen-plus-two surface reproduces ADR-029
  production 202 / `b1ae60b9c550353960687cb9973e2909e965a5e3eb98bb23b39b0a7f01a2a899`
  and test/web 199 / `59726e88e5bf7d831f16beaa693689ca799990d355733a1115c07a285a7e5293`:
  **PASS**.
- README-marker and benchmark-policy-byte mutations make the dedicated guard
  exit 1; a benchmark expected-cardinality mutation to 201 observes actual 200
  and makes its gate exit 1. All are restored. `git diff --check` is clean and
  the user-owned `apps/web/next-env.d.ts` remains preserved and unstaged:
  **PASS**.
- No actual provider identity, provider-published binding, non-DEMO index
  level/calendar/divisor data, schema, API, database, adapter, return
  calculator, readiness, or web behavior may appear in this slice. Provider
  selection and written rights approval remain prerequisites for real data.

## Signed benchmark reference return V1 boundary

ADR-031 calculates a signed benchmark price-index return from one complete ADR-030 benchmark reference-level-pair receipt using the exact basis-level denominator.

- The input accepts only the exact ADR-031 policy and one complete ADR-030
  benchmark reference-level-pair resolution using policy hash
  `2394b535c1061d32c647504a303b6f1e4ec2fe88e6017d9ff335d12087a5f73d`.
  It accepts no extracted level, provider return, basis, endpoint, assignment,
  horizon, venue, currency, or cutoff.
- Every result context preserves that exact complete receipt. The six result
  variants mirror resolved, intentional N/A, assignment unavailable,
  endpoint-anchor unavailable, evidence unavailable, and local output
  unavailability without mapping or duplicating nested reasons.
- Only a resolved pair is calculated. The exact arithmetic is one
  `endpoint.subtract(basis)` followed by one scale-12 `HALF_EVEN` division by
  the positive basis level. Intermediate/second rounding, percent conversion,
  float/double conversion, provider return fields, alternate denominators,
  shared generic helpers, asset/sector return reuse, and fallback are absent.
- Output is a signed decimal ratio with exact scale 12, precision at most 38,
  and lower boundary -1 inclusive after rounding. Nonrepresentable output uses
  only `OUTPUT_NOT_REPRESENTABLE`; missing evidence never becomes zero, false,
  loss, or N/A.
- This is a deterministic disconnected leaf. It does not select evidence,
  establish readiness or lifecycle, mutate an outcome, publish a metric, or
  add provider, persistence, API, database, fixture, schema, or web behavior.

## Signed benchmark reference return V1 contract gate

| ID | Check | Expected result |
| --- | --- | --- |
| P3-BRR01 | Exact source surface | Package `com.wallstreetreceipts.api.domain.outcome.benchmarkreturn` contains exactly `BenchmarkReturnPolicyVersion`, `BenchmarkReturnInput`, `BenchmarkReturnResult`, and `BenchmarkReturnCalculator`, plus exactly one source-local `BenchmarkReturnCalculatorGoldenTest`. No helper, selector, readiness, service, controller, repository, provider, scheduler, resource, or web file is added. |
| P3-BRR02 | Exact policy identity | The enum contains only `SIGNED_BENCHMARK_BASIS_LEVEL_DENOMINATOR_SCALE_12_HALF_EVEN_V1`. Its canonical definition is exactly 2832 single-line ASCII/UTF-8 bytes with SHA-256 `96d0aab8e8e784b80a12b16c99f6ba8c5f44eff7a342fd14c075b944a0a7de79`; returned bytes are defensive and every result context echoes the digest. |
| P3-BRR03 | Exact upstream contract | Input accepts one complete `BenchmarkReferenceLevelPairResolution` using only `POINT_IN_TIME_EXACT_BENCHMARK_PRICE_INDEX_LEVEL_PAIR_V1` and definition hash `2394b535c1061d32c647504a303b6f1e4ec2fe88e6017d9ff335d12087a5f73d`. All five upstream variants are handled exhaustively without a default. |
| P3-BRR04 | Complete receipt context | Input and `CalculationContext` fields are exactly policy identity plus the complete benchmark reference-level-pair resolution. Results retain the same supplied receipt; no level-only, direct basis/horizon, endpoint, assignment, currency, venue, provider-return, or cutoff input exists. |
| P3-BRR05 | Six exact result variants | Results are exactly `Available(context,benchmarkReturn)`, `NotApplicable(context)`, `AssignmentUnavailable(context)`, `EndpointAnchorUnavailable(context)`, `EvidenceUnavailable(context)`, and `OutputUnavailable(context,reason)`. Output reason enum contains only `OUTPUT_NOT_REPRESENTABLE`. |
| P3-BRR06 | Exact branch propagation | Pair N/A, assignment unavailable, endpoint-anchor unavailable, and evidence unavailable propagate to their same-named result variant before calculation. Their complete nested receipts and reasons remain in context with no copied reason field, mapping, duplication, or flattening. Only a resolved pair may produce `Available` or `OutputUnavailable`. |
| P3-BRR07 | Exact operands and formula | Operands come only from the resolved pair's selected `basisLevelObservation.level` and `endpointLevelObservation.level`. Formula is exactly `(endpoint-basis)/basis`: one exact subtraction first, followed by one division using the positive basis reference level. Provider-return fields and reconstructed/free-standing levels are forbidden. |
| P3-BRR08 | Exact rounding and units | The sole division uses scale 12 and `RoundingMode.HALF_EVEN`. Output unit is a signed decimal ratio. There is no `MathContext`, operand rescale, intermediate or second rounding, percent conversion, tolerance, float/double conversion, alternate denominator, or fallback. |
| P3-BRR09 | Numeric boundary | Upstream levels are positive exact `NUMERIC(38,12)`. Available output has exact scale 12, precision at most 38, and is at least -1; rounded exact `-1.000000000000` is valid. Arithmetic or rounded precision overflow is typed `OutputUnavailable(OUTPUT_NOT_REPRESENTABLE)` rather than clipped or approximated. |
| P3-BRR10 | Constructor attestation | Public constructors enforce null safety, exact policy/hash, exact source variant, closed output reason, scale, precision, and lower bound. They do not claim calculator invocation or recompute the formula; only `BenchmarkReturnCalculator.calculate` attests the required operation sequence. |
| P3-BRR11 | Independent type/import firewall | Production imports only JDK types and ADR-030 `benchmarkreferencepair` types. It does not import, cast, reflect on, or reuse asset-return, asset price-pair, sector reference-pair/return, assignment, endpoint-observation, horizon, lifecycle, persistence, API, or web types. No reflection, class token, shared generic return helper, or reverse wiring exists. |
| P3-BRR12 | Determinism and golden matrix | Formula signs, asymmetric basis denominator, scale-equivalent levels, positive/negative half-even ties, rounded -1, precision 38/39 boundary, every 53 evidence reason, all three anchor reasons, all 19 assignment-unavailable reasons, all four N/A truth-table branches, constructors, nulls, exact public shape, same-receipt identity, locale/time-zone replay, canonical bytes, and policy hash are covered. Focused verification passes 95/95 with zero failures, errors, or skips; normalized golden-source SHA-256 is `80c8e7dcdf6b4ee3daf980dc3c3d2aa54e4446620af2fc0985173fddf5ab3c90`. |
| P3-BRR13 | Lifecycle and product firewall | The leaf invokes no pair selector, readiness, methodology, fingerprint, lineage, status/completeness, retry, persistence, aggregation, ranking, or publication. Schemas, canonical fixtures, manifest, OpenAPI, Flyway, database, controller, repository, provider adapter, API, and web behavior remain unchanged; DEMO `benchmarkReturn`, `sectorReturn`, `alpha`, and `sectorAlpha` remain null. |
| P3-BRR14 | External boundary | No API key, account, plan, license, secret, or network is needed. Before non-DEMO use, P5 must approve the exact benchmark index product/feed, exact-time historical level coverage, calendar/divisor evidence, and storage/cache, display, derived-data, and redistribution rights. Scoped credentials follow written approval and remain outside chat and Git. |
| P3-BRR15 | Repository CI contract | The dedicated guard/runtime gate locks four-document marker parity, exact four-plus-one surface, exact policy bytes/hash/fields/variants, operation order, closed reason, runtime golden cardinality, source/import/reverse isolation, and null DEMO publication. Current protected production is 220 / `cb8532a4020c76a9ed2fd4a61fbb5844717dc23c7f27d90510e603c0bee1f5e9`; test/web is 202 / `12b03e7a48a0e6c3e676da9b335c4c270e8dc50bea2402aa25f6462db07bb273`. Exact ADR-031 exclusion replays ADR-030 production 216 / `45d06843fd95235221c6716a578915f40a410de8464b0b0ca3a09fff7c29436d` and test/web 201 / `fd0e3170ba2d64aeb4bf638010915455a27d3a5aed9fe77fb2a724502d96462f`; downstream replay retains ADR-029 production 202 / `b1ae60b9c550353960687cb9973e2909e965a5e3eb98bb23b39b0a7f01a2a899` and test/web 199 / `59726e88e5bf7d831f16beaa693689ca799990d355733a1115c07a285a7e5293`. README-marker and canonical `percentConversion`-byte mutations each make the guard exit 1; expected runtime 94 against actual 95 makes its gate exit 1. All mutations are restored, final guard passes, `git diff --check` is clean, and marker parity remains exactly one per document. |

## Required signed benchmark reference return verification checks

- Exact 2832-byte canonical extraction/hash, four-plus-one source surface,
  field/variant/reason order, operation-order and dependency firewalls, reverse
  isolation, null DEMO publication, and four-document marker parity:
  **PASS**. Independent reviews report no remaining P0/P1/P2 finding after two
  golden-coverage gaps were corrected.
- Focused `BenchmarkReturnCalculatorGoldenTest` passes 95/95 with zero
  failures, errors, or skips and normalized source SHA-256
  `80c8e7dcdf6b4ee3daf980dc3c3d2aa54e4446620af2fc0985173fddf5ab3c90`.
  Full API Maven verification passes 1763/1763 with zero failures, errors, or
  skips and `BUILD SUCCESS`, including Testcontainers PostgreSQL 17.10 and
  Flyway: **PASS**.
- The dedicated ADR-031 guard/runtime gate passes. All 36/36 workflow Python
  heredoc bodies syntax-compile and all 29/29 locally runnable bodies pass. Six
  `jsonschema`-dependent bodies are syntax-only because the bundled local
  runtime lacks `jsonschema`; the final cross-stack integration-log body is
  syntax-only by design. SnakeYAML 2.5 parses exactly four jobs and Compose
  config validates: **PASS**.
- Current protected production 220 /
  `cb8532a4020c76a9ed2fd4a61fbb5844717dc23c7f27d90510e603c0bee1f5e9`
  and test/web 202 /
  `12b03e7a48a0e6c3e676da9b335c4c270e8dc50bea2402aa25f6462db07bb273`
  pass. Exact ADR-031 exclusion replays ADR-030 production 216 /
  `45d06843fd95235221c6716a578915f40a410de8464b0b0ca3a09fff7c29436d`
  and test/web 201 /
  `fd0e3170ba2d64aeb4bf638010915455a27d3a5aed9fe77fb2a724502d96462f`;
  downstream replay retains ADR-029 production 202 /
  `b1ae60b9c550353960687cb9973e2909e965a5e3eb98bb23b39b0a7f01a2a899`
  and test/web 199 /
  `59726e88e5bf7d831f16beaa693689ca799990d355733a1115c07a285a7e5293`:
  **PASS**. The user-owned `apps/web/next-env.d.ts` remains preserved.
- Deliberate README-marker and canonical `percentConversion`-byte mutations
  each make the dedicated guard exit 1; expected runtime count 94 against
  actual 95 makes its gate exit 1. All mutations are restored. The final guard
  passes, final `git diff --check` is clean, and marker parity remains exactly
  one per contract document: **PASS**.
- ADR-032 now delivers the independently typed sector reference-return
  calculator. Comparative readiness is the next reviewed slice; lifecycle
  integration, canonical publication, MFE/MAE, alpha, and sector alpha remain
  later work.

## Signed sector reference return V1 boundary

ADR-032 calculates a signed sector price-index return from one complete ADR-030 sector reference-level-pair receipt using the exact basis-level denominator.

- Input accepts only the exact ADR-032 policy and one complete ADR-030 sector
  reference-level-pair resolution using policy hash
  `4224648ba01104fd3e96319158c7d6b42da472e9aa6f2ab22ef9fccf43da7e4a`.
  It accepts no extracted level, provider return, basis, endpoint, assignment,
  mapping, taxonomy, horizon, venue, currency, or cutoff.
- Every result context preserves that exact complete receipt. Six result
  variants mirror resolved, intentional N/A, assignment unavailable,
  endpoint-anchor unavailable, evidence unavailable, and local output
  unavailability without mapping or duplicating nested reasons.
- Only a resolved pair is calculated. Exact arithmetic is one
  `endpoint.subtract(basis)` followed by one scale-12 `HALF_EVEN` division by
  the positive basis level. Intermediate/second rounding, percent conversion,
  float/double conversion, provider return fields, alternate denominators,
  shared generic helpers, asset/benchmark return reuse, and fallback are absent.
- Output is a signed decimal ratio with exact scale 12, precision at most 38,
  and lower boundary -1 inclusive after rounding. Nonrepresentable output uses
  only `OUTPUT_NOT_REPRESENTABLE`; missing evidence never becomes zero, false,
  loss, or N/A.
- This is a deterministic disconnected leaf. It does not select evidence,
  establish readiness or lifecycle, activate methodology, create a fingerprint
  or lineage record, mutate an outcome, publish a metric, or add provider,
  persistence, API, database, fixture, schema, or web behavior.

## Signed sector reference return V1 contract gate

| ID | Check | Expected result |
| --- | --- | --- |
| P3-SRR01 | Exact source surface | Package `com.wallstreetreceipts.api.domain.outcome.sectorreturn` contains exactly `SectorReturnPolicyVersion`, `SectorReturnInput`, `SectorReturnResult`, and `SectorReturnCalculator`, plus exactly one source-local `SectorReturnCalculatorGoldenTest`. No helper, selector, readiness, service, controller, repository, provider, scheduler, resource, or web file is added. |
| P3-SRR02 | Exact policy identity | The enum contains only `SIGNED_SECTOR_BASIS_LEVEL_DENOMINATOR_SCALE_12_HALF_EVEN_V1`. Its canonical definition is exactly 2817 single-line ASCII/UTF-8 bytes with SHA-256 `5aecd42c32ba69f0d21ab6e1ee1e3128cd31584724a6f46e176acd470204d0f7`; returned bytes are defensive and every result context echoes the digest. |
| P3-SRR03 | Exact upstream contract | Input accepts one complete `SectorReferenceLevelPairResolution` using only `POINT_IN_TIME_EXACT_SECTOR_PRICE_INDEX_LEVEL_PAIR_V1` and definition hash `4224648ba01104fd3e96319158c7d6b42da472e9aa6f2ab22ef9fccf43da7e4a`. All five upstream variants are handled exhaustively without a default. |
| P3-SRR04 | Complete receipt context | Input and `CalculationContext` fields are exactly policy identity plus the complete sector reference-level-pair resolution. Results retain the same supplied receipt; no level-only, direct basis/horizon, endpoint, assignment, mapping, taxonomy, currency, venue, provider-return, or cutoff input exists. |
| P3-SRR05 | Six exact result variants | Results are exactly `Available(context,sectorReturn)`, `NotApplicable(context)`, `AssignmentUnavailable(context)`, `EndpointAnchorUnavailable(context)`, `EvidenceUnavailable(context)`, and `OutputUnavailable(context,reason)`. Output reason enum contains only `OUTPUT_NOT_REPRESENTABLE`. |
| P3-SRR06 | Exact branch propagation | Pair N/A, assignment unavailable, endpoint-anchor unavailable, and evidence unavailable propagate to their same-named result variant before calculation. Their complete nested receipts and reasons remain in context with no copied reason field, mapping, duplication, or flattening. Only a resolved pair may produce `Available` or `OutputUnavailable`. |
| P3-SRR07 | Exact operands and formula | Operands come only from the resolved pair's selected `basisLevelObservation.level` and `endpointLevelObservation.level`. Formula is exactly `(endpoint-basis)/basis`: one exact subtraction first, followed by one division using the positive basis reference level. Provider-return fields and reconstructed/free-standing levels are forbidden. |
| P3-SRR08 | Exact rounding and units | The sole division uses scale 12 and `RoundingMode.HALF_EVEN`. Output unit is a signed decimal ratio. There is no `MathContext`, operand rescale, intermediate or second rounding, percent conversion, tolerance, float/double conversion, alternate denominator, or fallback. |
| P3-SRR09 | Numeric boundary | Upstream levels are positive exact `NUMERIC(38,12)`. Available output has exact scale 12, precision at most 38, and is at least -1; rounded exact `-1.000000000000` is valid. Arithmetic or rounded precision overflow is typed `OutputUnavailable(OUTPUT_NOT_REPRESENTABLE)` rather than clipped or approximated. |
| P3-SRR10 | Constructor attestation | Public constructors enforce null safety, exact policy/hash, exact source variant, closed output reason, scale, precision, and lower bound. They do not claim calculator invocation or recompute the formula; only `SectorReturnCalculator.calculate` attests the required operation sequence. |
| P3-SRR11 | Independent type/import firewall | Production imports only JDK and ADR-030 `sectorreferencepair` types. It does not import, cast, reflect on, or reuse asset-return, asset price-pair, benchmark reference-pair/return, assignment, taxonomy, endpoint-observation, horizon, lifecycle, persistence, API, or web types. No reflection, class token, shared generic return helper, or reverse wiring exists. |
| P3-SRR12 | Determinism and golden matrix | Formula signs, asymmetric basis denominator, scale-equivalent levels, positive/negative half-even ties, rounded -1, precision 38/39 boundary, every 56 evidence reason, all three anchor reasons, all 36 assignment-unavailable reasons, the intentional N/A branch, constructors, nulls, exact public shape, same-receipt identity, locale/time-zone replay, canonical bytes, and policy hash are covered. Focused verification passes 112/112 with zero failures, errors, or skips; normalized golden-source SHA-256 is `6047b29c8c338893bf2fdeaa9a5fef83ec20cb4f5e11acb77d82b48d8752b129`. |
| P3-SRR13 | Lifecycle and product firewall | The leaf invokes no pair selector, readiness, methodology, fingerprint, lineage, status/completeness, retry, persistence, aggregation, ranking, or publication. Schemas, canonical fixtures, manifest, OpenAPI, Flyway, database, controller, repository, provider adapter, API, and web behavior remain unchanged; DEMO `benchmarkReturn`, `sectorReturn`, `alpha`, and `sectorAlpha` remain null. |
| P3-SRR14 | External boundary | No API key, account, plan, license, secret, or network is needed, and this slice has no credential. Before non-DEMO use, P5 must approve the exact sector-index product/feed; rights to create and use the exact WSR canonical-node-to-selected provider-published sector price-index binding; exact-time historical levels and revisions; reference-calendar identity/revision/source; divisor and methodology continuity; and storage/cache/display/derived-return/redistribution rights. Publisher and redistributor require independent review when they differ. Assignment or classification rights alone do not grant index rights. A scoped credential follows provider/product and written-rights approval and stays outside chat and Git. |
| P3-SRR15 | Repository CI contract | The dedicated guard/runtime gate locks four-document marker parity, exact four-plus-one surface, exact policy bytes/hash/fields/variants, operation order, closed reason, runtime golden cardinality, source/import/reverse isolation, and null DEMO publication. Protected production is 224 / `bc31bb72f14289e6a8b3c344e356f900a2d23a9fb9efd48ce935586c0e336055`; exact production exclusion replays ADR-031 at 220 / `cb8532a4020c76a9ed2fd4a61fbb5844717dc23c7f27d90510e603c0bee1f5e9`. API-test/web is 203 / `5f95c2b844af16224815b1b4025b52b9c25b7822d4fa53b8f8d93788805f28ce`; exact golden exclusion replays ADR-031 at 202 / `12b03e7a48a0e6c3e676da9b335c4c270e8dc50bea2402aa25f6462db07bb273`. Dedicated/static/runtime guards, 37/37 workflow syntax, 30/30 local execution, four-job YAML, Compose, mutation tripwires, marker parity, and final hygiene pass. |

## Required signed sector reference return verification checks

- Exact 2817-byte canonical extraction/hash, four-plus-one source surface,
  field/variant/reason order, operation-order and dependency firewalls, reverse
  isolation, null DEMO publication, and four-document marker parity are the
  dedicated ADR-032 contract boundary.
- Focused `SectorReturnCalculatorGoldenTest` passes 112/112 with zero failures,
  errors, or skips and normalized source SHA-256
  `6047b29c8c338893bf2fdeaa9a5fef83ec20cb4f5e11acb77d82b48d8752b129`.
- Full API Maven verification passes 1875/1875 with zero failures, errors, or
  skips and `BUILD SUCCESS`, including Testcontainers PostgreSQL 17.10 and
  Flyway: **PASS**.
- Protected production 224 /
  `bc31bb72f14289e6a8b3c344e356f900a2d23a9fb9efd48ce935586c0e336055`
  and API-test/web 203 /
  `5f95c2b844af16224815b1b4025b52b9c25b7822d4fa53b8f8d93788805f28ce`
  are measured. Exact ADR-032 exclusions replay ADR-031 production 220 /
  `cb8532a4020c76a9ed2fd4a61fbb5844717dc23c7f27d90510e603c0bee1f5e9`
  and test/web 202 /
  `12b03e7a48a0e6c3e676da9b335c4c270e8dc50bea2402aa25f6462db07bb273`.
- The dedicated ADR-032 guard and 112/112 runtime gate pass. Workflow Python
  bodies syntax-compile 37/37 and locally runnable bodies pass 30/30; six
  `jsonschema`/`referencing` bodies plus the final cross-stack body remain
  syntax-only for their documented local boundary. SnakeYAML parses four jobs
  and Compose validates: **PASS**.
- README-marker, canonical policy-byte, and expected runtime 112-to-111
  mutations each exit nonzero and are exactly restored. Web lint, 569/569
  Vitest tests, production build, marker parity, independent review, and final
  diff hygiene pass; the user-owned `next-env.d.ts` remains preserved:
  **PASS**.
- Source-local comparative readiness is the next reviewed slice. Lifecycle,
  methodology/fingerprint, lineage, persistence, API/UI publication,
  raw-window coverage, MFE/MAE, alpha, and sector alpha remain later work.

## Independent comparative-return readiness boundary

ADR-033 classifies benchmark and sector return readiness independently from their complete supplied ADR-031 and ADR-032 result receipts without mapping either leaf to canonical lifecycle status.

- Two unrelated four-type packages consume one complete matching return result
  each. There is no combined request, correlation, shared generic readiness,
  cast, alias, reflection path, or cross-kind dependency.
- `Available` and intentional `NotApplicable` are `Settled`. Only the exact
  nested return/pair evidence chain ending in `ENDPOINT_NOT_REACHED_AS_OF` is
  `AwaitingEndpoint`. Assignment, endpoint-anchor, other reference evidence,
  and output-representability branches are `EvidenceUnavailable`.
- Each resolution preserves the exact whole supplied return result. Context
  contains only its readiness policy and digest; no reason is copied or
  flattened. Constructors and resolvers share the exact classification check.
- These variants remain source-local evidence. They do not establish outcome
  status, `dataComplete`, retry, permanence, freshness, cancellation,
  scheduling, methodology, fingerprint, persistence, publication, or
  cross-metric completeness.
- No fixture, schema, manifest, OpenAPI, Flyway, database, controller,
  repository, adapter, API, or web behavior is added. DEMO comparative values
  remain null.

## Independent comparative-return readiness contract gate

| ID | Check | Expected result |
| --- | --- | --- |
| P3-CRR01 | Independent source surfaces | `benchmarkreturnreadiness` and `sectorreturnreadiness` each contain exactly PolicyVersion, Request, sealed Resolution, Resolver, and one matching golden; no combined or generic package exists. |
| P3-CRR02 | Exact policy identities | Benchmark contains only `SUPPLIED_LEAF_BENCHMARK_RETURN_READINESS_V1`; sector contains only `SUPPLIED_LEAF_SECTOR_RETURN_READINESS_V1`. |
| P3-CRR03 | Exact canonical bytes | Benchmark is 2622 bytes / `2dedaf014a149ed81e75941ee3677e3c8b77243b9987d9496709266aad721daf`; sector is 2592 bytes / `5737f44ebc6e65270300889dd5c2e92da0c4f3a2f04e4c6c43e4483e522187d4`; bytes are defensive. |
| P3-CRR04 | Exact source policies | Requests accept only complete ADR-031 hash `96d0aab8e8e784b80a12b16c99f6ba8c5f44eff7a342fd14c075b944a0a7de79` or ADR-032 hash `5aecd42c32ba69f0d21ab6e1ee1e3128cd31584724a6f46e176acd470204d0f7`, respectively. |
| P3-CRR05 | Sealed result shape | Each result has exactly `Settled(context,sourceResult)`, `AwaitingEndpoint(context,sourceResult)`, and `EvidenceUnavailable(context,sourceResult)`; context has only policy and hash. |
| P3-CRR06 | Settled rule | Available and every intentional N/A source are settled without endpoint waiting or nested-reason inspection. |
| P3-CRR07 | Exact awaiting chain | Awaiting requires top-level return `EvidenceUnavailable`, preserved pair `EvidenceUnavailable`, and pair reason exactly `ENDPOINT_NOT_REACHED_AS_OF`. |
| P3-CRR08 | Evidence firewall | Assignment and anchor branches remain evidence-unavailable even with a future endpoint; all other pair reasons and output-unavailable remain evidence-unavailable. |
| P3-CRR09 | Source preservation | Every resolution retains the exact supplied whole source object and no outer reason; equal-but-distinct inputs replay equally while preserving their own identity. |
| P3-CRR10 | Constructor and invocation boundary | Direct wrong variant construction fails closed through shared classification validation; only `resolve` attests request-to-result invocation. |
| P3-CRR11 | Import and ownership firewall | Benchmark imports only JDK, benchmark return, and benchmark pair; sector is symmetric. No cross package, asset-return, generic helper, lifecycle, persistence, API, or web edge exists. ADR-025's nine-input future ownership remains unchanged. |
| P3-CRR12 | Exhaustive benchmark golden | Exactly 81 source vectors plus six fixed tests execute 87 invocations: 5 settled, 1 awaiting, 75 evidence-unavailable. |
| P3-CRR13 | Exhaustive sector golden | Exactly 98 source vectors plus six fixed tests execute 104 invocations: 2 settled, 1 awaiting, 95 evidence-unavailable. |
| P3-CRR14 | Determinism | Locale, time zone, prior calls, source identity, and environment state cannot affect either resolver. |
| P3-CRR15 | Product firewall | No publication or runtime wiring occurs and all DEMO comparative fields remain null. |
| P3-CRR16 | External boundary | No key, account, plan, license, secret, or network is needed. P5 must approve exact products/feeds, history/revisions, calendars, continuity, sector binding, and storage/cache/display/derived/redistribution rights before scoped credentials are placed only in untracked local/CI/deployment secret stores. |

## Required ADR-033 verification checks

- Both focused goldens pass at exactly 87/87 and 104/104 with zero failures,
  errors, or skips. Normalized source hashes are benchmark
  `f61e82ba7766effe4954f4c96db745a49bb49a03d06de583c39c32d76e3c1b3d`
  and sector
  `1b2aa1eea5d5c8efcddd048c54d8b53be87b14cdfd96a6df43e11a7f55bc9f8c`.
- Protected production is 232 /
  `2cfbb3b9f9039b9e7af92ac7cbd9c35b9705ce79fda3aa58422a73f23c0d8941`
  and API-test/web is 205 /
  `fba2656db6ef5bbf5e15288bebd894639926645e7657ac214ec1cec657cc4d75`.
  Exact ADR-033 8+2 exclusion reproduces ADR-032 production 224 /
  `bc31bb72f14289e6a8b3c344e356f900a2d23a9fb9efd48ce935586c0e336055`
  and test/web 203 /
  `5f95c2b844af16224815b1b4025b52b9c25b7822d4fa53b8f8d93788805f28ce`.
- Full API verification passes exactly 2066/2066—191 above the ADR-032
  baseline—with zero failures, errors, or skips and `BUILD SUCCESS`, including
  Testcontainers PostgreSQL 17.10 and Flyway.
- The dedicated guard locks four-document marker parity, exact policy
  bytes/hashes and 8+2 surface, classification rules, imports and reverse
  isolation, runtime cardinality, current/exclusion baselines, and null DEMO
  publication. Exact 87/87 and 104/104 runtime gates pass; workflow Python
  syntax is 38/38 and local execution is 31/31; SnakeYAML parses four jobs and
  Compose validates.
- Web lint, 569/569 Vitest, and production build pass. Marker, canonical-byte,
  and runtime 87-to-86 mutations fail and are restored. Independent review has
  no remaining P0/P1/P2 finding after future-endpoint anchor coverage was
  corrected. `git diff --check` passes and the user-owned `next-env.d.ts` is
  restored to SHA-256
  `7ad303e40d4fddf44f156129e397511953a71481c5cfd86b1862649aaaf240cc`.

## Point-in-time raw-window coverage foundation boundary

ADR-034 freezes provider-neutral point-in-time raw-window coverage semantics before any executable raw aggregation or MFE/MAE calculation.

ADR-034 is decision-only. It prevents ADR-019's supplied aggregate
completeness flag, current snapshots, sparse fixtures, or missing rows from
being presented as verified raw coverage. No executable result is produced in
this slice.

## Raw-window coverage foundation contract gate

| ID | Check | Expected result |
| --- | --- | --- |
| P3-RWF01 | Decision-only surface | Only ADR-034, README, this acceptance gate, the implementation log, and the dedicated CI guard change. No Java production/test type, policy bytes/hash, runtime golden, schema, fixture, manifest member, OpenAPI, Flyway, database, provider, API, resource, or web surface is added. |
| P3-RWF02 | Exact future anchor | A future executable request consumes one complete ADR-016 `AssetReturnPricePairResolution.Resolved` and inherits its exact basis price, strict horizon/endpoint, `evaluationAsOf`, asset, venue, currency, source, calendar/catalog, adjustment, and continuity identities. ADR-018/ADR-019 and product snapshots are not anchors. |
| P3-RWF03 | Exact economic interval | The population is the ordered primary-venue regular-session session union over `(basis.eventTime, endpointSession.closesAt]`, with the lower bound exclusive and upper bound inclusive. Pre-call, off-hours, alternate-venue, and inter-session observations are excluded. |
| P3-RWF04 | Trade-tick-only V1 | `TRADE_TICK_ONLY_V1` admits eligible trade ticks only. Quotes, indications, OHLC/session/intraday bars, bar splitting, bar-straddle inference, basis/endpoint/prior-close/nearest/interpolated values, and ADR-019 aggregate fallback are absent. |
| P3-RWF05 | Required identities | Future events preserve observation/provider-event/revision identity, event time, exact positive `NUMERIC(38,12)` price, trade condition, source/revision, provenance, `availableAt`, and `capturedAt`. One manifest preserves bounds, ordered sessions, calendar/catalog revision, source sequence/watermark, correction/bust coverage, revision, provenance, and PIT timestamps. |
| P3-RWF06 | PIT-first visibility and causality | Every event, manifest, completeness proof, condition mapping, correction, and bust is visible only at the inherited cutoff. Raw events require `eventTime <= availableAt <= capturedAt <= evaluationAsOf`; manifests require `upperBound <= availableAt <= capturedAt <= evaluationAsOf`. Future or impossibly early evidence cannot affect identity, reason, cardinality, population, high, or low. |
| P3-RWF07 | Correction and replay | Only a complete visible predecessor-linked correction/bust chain determines an effective event. A visible correction replaces its predecessor and a visible bust removes it. Missing/broken/competing chains and an unproven correction watermark fail closed; later evidence creates a later replay and never mutates an earlier as-of receipt. |
| P3-RWF08 | Duplicate identity | Repeated delivery of the same provider-event revision does not create another economic trade. Distinct provider-event identities remain distinct, and ambiguous revision identity fails closed; no current/latest-row guess is allowed. |
| P3-RWF09 | No-trade truth | In-session silence is covered only by an approved manifest/sequence proof that no eligible event was omitted. Absence of returned rows, a count, or a last timestamp is not proof. A completely proven zero-eligible-trade window is `CompleteWithoutEligibleTrade`, never zero price/MFE/MAE or a fallback. |
| P3-RWF10 | Halt semantics | A halt contributes no price. PIT-visible halt evidence may explain silence but cannot manufacture continuity, completeness outside the source proof, or an extreme. |
| P3-RWF11 | Auction semantics | Auction executions count only through a separately versioned provider-code mapping that explicitly makes the condition primary-venue regular-session eligible. Unknown codes, labels, timing, and venue inference fail closed. |
| P3-RWF12 | Gap semantics | Off-hours and inter-session gaps are outside the population. An internal source-sequence gap is evidence-unavailable unless the approved product's completeness protocol accounts for it. |
| P3-RWF13 | Co-identified population | One covered receipt preserves the same final effective event population for both high and low, with no rounding/rescaling and provenance for ties. High-only and low-only receipts from different sources, revisions, or cutoffs cannot be paired. |
| P3-RWF14 | Future result meanings | A later executable review must distinguish at least `Covered`, `CompleteWithoutEligibleTrade`, and `EvidenceUnavailable`. Exact Java shape, reasons, precedence, policy bytes/hash, and golden vectors are not created by ADR-034. There is no endpoint-wait or applicability inference here. |
| P3-RWF15 | ADR-019 firewall | `EXACT_CAUSAL_WINDOW_SESSION_UNION` remains a supplied aggregate attestation for target-hit selection. It cannot be cast, wrapped, backfilled, or described as ADR-034 raw verification, and ADR-019's source/policy/golden remain unchanged. |
| P3-RWF16 | MFE/MAE ownership and lifecycle firewall | One raw receipt is shared source evidence only. MFE and MAE remain separate metric meanings with separately reviewed formula, polarity, calculator, readiness, and ownership contracts. No lifecycle status, `dataComplete`, retry, scheduling, methodology, fingerprint, persistence, aggregation, ranking, or publication is inferred. |
| P3-RWF17 | External rights boundary | No key, account, paid plan, license, secret, or network is needed. Before executable non-DEMO work, P5 and the user must approve the exact historical trade-tick product and written history, event/revision, correction/bust, sequence/watermark, auction/halt, calendar/corporate-action, storage/cache/derived/display/redistribution rights. Credentials follow only through untracked local/CI/deployment secret stores. |

## Required ADR-034 documentation and negative checks

- The exact ADR-034 marker must occur once in ADR-034, README, this acceptance
  file, and the implementation log.
- The dedicated guard must lock the accepted title/date, decision-only surface,
  exact interval and trade-tick rule, PIT/correction/manifest requirements,
  no-trade/halt/auction/bar/gap semantics, ADR-016 anchor, ADR-019 firewall,
  separate MFE/MAE ownership, null DEMO values, and external-rights boundary.
- Protected production must remain exactly 232 files / SHA-256
  `2cfbb3b9f9039b9e7af92ac7cbd9c35b9705ce79fda3aa58422a73f23c0d8941`;
  API-test/web must remain exactly 205 files / SHA-256
  `fba2656db6ef5bbf5e15288bebd894639926645e7657ac214ec1cec657cc4d75`.
- No ADR-034 Java/runtime cardinality gate exists. The dedicated guard passes;
  all 39/39 embedded Python bodies syntax-compile and all 32/32 locally
  runnable bodies pass. SnakeYAML parses four jobs and Compose validates.
- Full API verification passes 2066/2066 with zero failures, errors, or skips,
  including PostgreSQL 17.10 Testcontainers and Flyway. Web lint, 42/42 Vitest
  files with 569/569 tests, and the 12-page production build pass.
- README marker, `NotApplicable` exclusion, raw-event causal-chain, temporary
  forbidden `rawwindowcoverage/RawWindowCoveragePolicyVersion.java`, and
  `.env.example` provider-surface mutations each make the guard exit nonzero
  and are fully removed. The final guard and `git diff --check` pass; the exact
  nine-file dependency/runtime digest is
  `25677e07b9f511dd8899bf69fb5c435247d4996313a60361faf354002b8555bd`,
  and the user-owned `next-env.d.ts` is restored to SHA-256
  `7ad303e40d4fddf44f156129e397511953a71481c5cfd86b1862649aaaf240cc`.
- Independent semantic and evidence closure reviews report no remaining
  P0-P3 finding after the `NotApplicable`, causal-time, recursive-fixture,
  configuration-digest, and whole-workflow guard gaps were corrected.

## Deferred executable work

- The next raw-window implementation requires the selected feed's documented
  sequence, watermark/finality, correction/bust, trade-condition, auction,
  halt, and rights evidence plus a separately reviewed executable policy and
  golden matrix.
- Only after a verified covered event population exists may separate MFE and
  MAE arithmetic/polarity and readiness contracts be added. Bearish/neutral
  rules, denominator, sign, rounding, and representability remain undecided and
  must not be inferred from the one bullish scenario example.
- Canonical methodology activation, fingerprint, append-only lineage,
  aggregate lifecycle, persistence, scheduling, ranking, API/UI publication,
  alpha, and sector alpha remain later work; alpha stays last.
