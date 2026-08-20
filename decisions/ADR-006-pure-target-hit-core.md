# ADR-006 — Pure Target-Hit Comparison Core

- Status: Accepted
- Date: 2026-08-20

## Context

P3 owns deterministic outcome calculations and golden tests. The existing
canonical outcome archive establishes nullable result fields, methodology
identity, append-only lineage, and incomplete states, but it deliberately
contains no calculated metric. The current fixtures also contain no trading-
session catalog or horizon window high/low observations.

The existing scoring notes define one calculation without requiring a horizon
calendar or decimal division: a bullish target is hit when the selected window
high is greater than or equal to the target, and a bearish target is hit when
the selected window low is less than or equal to the target. The selection of
that window and the interpretation of analyst-call directions are separate,
currently undefined methodology decisions.

## Decision

The first P3 slice implements only a pure target-hit comparison primitive.

- `TargetHitInput` contains only an already interpreted side, exactly `BULLISH`
  or `BEARISH`, a nullable decimal target, and one nullable favorable extreme
  preselected by its caller. For `BULLISH`, that extreme means the selected
  window high; for `BEARISH`, it means the selected window low. The primitive
  cannot select or verify that upstream window meaning.
- When both decimals are present, `BULLISH` returns hit when
  `favorableExtreme >= target`; `BEARISH` returns hit when
  `favorableExtreme <= target`. Equality is a hit. Comparison uses the original
  `BigDecimal` values and performs no conversion, division, rounding, output
  rescaling, or binary floating-point arithmetic. A non-mutating
  `setScale(12, UNNECESSARY)`-equivalent validation probe may prove storage
  representability; it does not normalize or replace the input.
- Every provided decimal is validated as positive and exactly representable as
  `NUMERIC(38,12)`. Invalid evidence is rejected rather than hidden behind an
  unavailable result.
- `TargetHitResult` is sealed as `Available(boolean)` or
  `Unavailable(UnavailableReason)`. The exact unavailable reasons are
  `TARGET_MISSING`, `FAVORABLE_EXTREME_MISSING`, and
  `TARGET_AND_FAVORABLE_EXTREME_MISSING`. Unavailable never becomes `false`,
  zero, or a calculated result. A missing or unsupported side is invalid rather
  than inferred.
- The primitive does not select a horizon, calendar, session, price field,
  window, call, revision, snapshot, provider, or methodology. It does not map
  `STRONG_BULLISH`, `STRONG_BEARISH`, or `NEUTRAL`; those policies remain fail-
  closed until a later versioned methodology defines them.
- Production code remains a deterministic domain leaf. It has no Spring,
  repository, provider, network, `Clock`, persistence, controller, scheduler,
  JSON, or LLM dependency. Golden vectors are source-local test inputs and are
  not canonical market evidence.

This slice does not bind the primitive to either existing
`standard-call-outcome` methodology. Both stored versions remain `MODEL_ONLY`,
their formula bodies are absent from the fixture, and their definition hashes
must not be reinterpreted. It also does not create a `CALCULATED` outcome.

## Consequences

- The exact comparison can be implemented and regression-tested without
  inventing a horizon observation or mutating the canonical archive.
- The current schemas, fixtures, manifest, OpenAPI, API responses, Flyway
  migrations, database rows, and web routes remain unchanged.
- P3 still requires a versioned methodology definition and canonical input
  fingerprint specification before a calculated outcome can be persisted.
- Trading-calendar-aware horizon resolution must precede runtime target-hit
  orchestration. Target error additionally needs the exact horizon-price and
  division/rounding policy; MFE/MAE needs full-window and polarity rules; alpha
  needs benchmark/sector identity and corporate-action policy.
