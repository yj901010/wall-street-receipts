# ADR-009 — Pure Directional-Win Comparison Core

- Status: Accepted
- Date: 2026-08-21

## Context

P3 owns deterministic outcome calculations and golden tests. The written
scoring specification defines directional win from the sign of an already
calculated horizon return: bullish wins require a positive return and bearish
wins require a negative return. The repository does not yet define a runtime
horizon return, endpoint-price observation, corporate-action treatment, or
original-versus-correction scoring basis.

The canonical outcome archive keeps `directionalWin` nullable, and every
current DEMO outcome deliberately stores it as JSON null. A small comparison
leaf can lock the sign semantics without inventing any of those missing inputs
or promoting a model record into calculated performance.

## Decision

The next isolated P3 calculation slice implements only a pure directional-win
comparison primitive.

- `DirectionalWinInput` contains exactly an already interpreted
  `DirectionalWinSide` and one nullable, caller-supplied `assetReturn`.
  `DirectionalWinSide` is closed to exactly `BULLISH` and `BEARISH`; the
  primitive does not accept or reduce `CallDirection`, map strong directions,
  or decide neutral eligibility.
- Every provided asset return may be negative, zero, or positive, and must be
  exactly representable as signed PostgreSQL `NUMERIC(38,12)`. Validation may
  make a non-mutating scale-12 `RoundingMode.UNNECESSARY` probe, but neither the
  input nor output is rounded, rescaled, parsed, converted to binary floating
  point, or replaced.
- A complete bullish input is a win only when `assetReturn > 0`. A complete
  bearish input is a win only when `assetReturn < 0`. Exact zero is false for
  both sides. Comparison is strict and scale-insensitive, with no tolerance or
  epsilon.
- `DirectionalWinResult` is sealed as `Available(boolean directionalWin)` or
  `Unavailable(UnavailableReason reason)`. The unavailable-reason enum contains
  exactly `ASSET_RETURN_MISSING`. Missing return evidence is never converted to
  zero, false, a loss, or a calculated outcome. A missing side or a provided
  non-representable decimal is invalid and fails closed.
- The primitive receives no prices, timestamp, horizon, call, revision,
  snapshot, asset, currency, market session, provider, methodology, provenance,
  fingerprint, or completeness flag. It neither calculates nor verifies the
  supplied return and cannot attest its point-in-time availability.
- Production code remains a deterministic domain leaf with no Spring,
  repository, provider, fixture, JSON, persistence, controller, scheduler,
  network, `Clock`, locale, timezone, random, LLM, or binary floating-point
  dependency. Golden vectors are source-local Java test inputs only.

This contract is not bound to either existing `standard-call-outcome`
methodology. Both stored versions remain `MODEL_ONLY`; their definition hashes
are not reinterpreted, and no input fingerprint, sequence, or `CALCULATED`
outcome is created.

## Consequences

- Strict bullish/bearish sign behavior, zero handling, signed decimal bounds,
  and missing-return behavior can be replayed without market observations.
- Existing schemas, canonical fixtures and manifest, OpenAPI paths, Flyway
  migrations, persistence, API behavior, providers, and web routes remain
  unchanged.
- Runtime use still requires a versioned direction-reduction policy, exact
  point-in-time horizon-return input contract, calendar/window policy,
  corporate-action and currency rules, reproducible methodology definition and
  hash, and input fingerprint.
- This leaf does not publish performance, activate a methodology, complete an
  outcome, or advance leaderboard aggregation.
