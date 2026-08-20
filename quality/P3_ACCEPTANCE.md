# P3 Acceptance Checks — Deterministic Scoring

Current status: the pure target-hit comparison core and explicit-anchor
session-offset mechanics vertical slices are complete. Neither publishes a
calculated outcome or completes the broader P3 scoring phase, which remains
open.

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

## Deferred work and implementation order

1. With the explicit-anchor session-offset mechanics complete, define the
   versioned event-to-anchor and named-horizon policy before selecting any
   runtime target-hit input.
2. Add endpoint-price/asset-return support, then target error after the exact
   `actual` observation, positivity, output scale, and rounding policy are
   versioned. Target error precedes window metrics because it needs one resolved
   close rather than a complete high/low path.
3. Wire target hit only after its side reduction, target eligibility, window
   inclusivity, point-in-time input identity, and unavailable mapping are locked.
4. Add MFE/MAE after full-window completeness and bullish/bearish sign rules;
   add alpha/sector alpha last, after benchmark/sector identity and corporate-
   action-adjusted return policy exist.
5. Persist or expose a non-null metric only with a canonical versioned input
   fixture, reproducible methodology definition/hash, input fingerprint, golden
   test, append-only lineage, and schema/domain/database completeness matrix.

Leaderboard aggregates, sample confidence, ranking publication, schedulers,
provider integration, historical bars, and real/licensed market data remain
outside this slice.
