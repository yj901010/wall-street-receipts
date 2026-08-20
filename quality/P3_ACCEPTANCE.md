# P3 Acceptance Checks — Deterministic Scoring

Current status: the pure target-hit comparison core vertical slice is complete.
It proves one closed deterministic primitive only; it does not publish a
calculated outcome or complete the broader P3 scoring phase, which remains open.

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

## Deferred work and implementation order

1. Define a versioned trading-calendar/session and horizon-window policy before
   selecting any runtime target-hit input.
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
