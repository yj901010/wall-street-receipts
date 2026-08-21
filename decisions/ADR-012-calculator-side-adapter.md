# ADR-012 — Calculator-Side Polarity Adapter

- Status: Accepted
- Date: 2026-08-21

## Context

ADR-011 resolves the canonical five-value `CallDirection` into either an exact
common `DirectionalSide` (`BULLISH` or `BEARISH`) or explicit
`NonDirectional(NEUTRAL_DIRECTION)`. The existing target-hit and
directional-win primitives intentionally retain their own closed side enums.

The approved polarity contract permits a mechanical, disconnected adapter for
the two directional values only. The next reviewed slice must not reinterpret
neutral, invoke either calculator, select any input, or become runtime
orchestration. Because the translation is an exact one-to-one vocabulary bridge
and introduces no independent policy choice, it does not receive its own
version or definition hash.

## Decision

The seventh isolated P3 slice adds exactly one production adapter and one
source-local golden test.

- `CalculatorSideAdapter` is a public final utility class with one private
  constructor and exactly two public methods, both static:
  - `TargetHitSide toTargetHitSide(DirectionalSide side)`
  - `DirectionalWinSide toDirectionalWinSide(DirectionalSide side)`
- Both methods map exhaustively and exactly:
  - common `BULLISH` maps to the destination enum's `BULLISH`;
  - common `BEARISH` maps to the destination enum's `BEARISH`.
- A null side is invalid and is rejected before translation. There is no
  default, fallback, ordinal arithmetic, enum-name parsing, string
  normalization, reflection-based mapping, or future-value inference.
- The adapter accepts only
  `CallDirectionPolarityResolution.DirectionalSide`. It has no overload or
  parameter for `CallDirection`, `CallDirectionPolarityResolution`,
  `NonDirectional`, `NonDirectionalReason`, `ResolutionContext`, or a Boolean.
  Neutral therefore has no construction or invocation path into either method.
- The adapter returns only the destination side enum. It does not construct a
  calculator input, invoke `TargetHitCalculator` or `DirectionalWinCalculator`,
  return an available/unavailable calculation result, or decide whether a
  metric is eligible or complete.
- The existing common side and both calculator-specific side enums remain
  unchanged. The adapter has no policy-version, canonical-definition,
  definition-hash, methodology, fingerprint, or provenance surface.

## Purity and integration boundary

Production code may depend only on `Objects`, common `DirectionalSide`,
`TargetHitSide`, and `DirectionalWinSide`. It performs no decimal or
floating-point arithmetic and imports no call/revision/outcome aggregate,
horizon/calendar/window type, price, return, observation, provider, repository,
fixture, JSON, Spring, persistence, controller, scheduler, network, clock,
locale, timezone, random, or LLM dependency.

At this slice boundary no other production class referenced
`CalculatorSideAdapter`. ADR-013 later opens exactly one disconnected consumer,
`CalculatorSideRouting`; no other production class may reference the adapter.
The polarity policy itself remains calculator-neutral, and neither calculator
knows about the common side. Neither bridge is invoked by a controller,
application service, provider, repository, scheduler, API response, or web
source.

The slice adds no schema, canonical fixture, manifest member, OpenAPI path,
Flyway migration, database row, API behavior, provider, or web source.
Source-local vectors are type-translation tests only and are not analyst,
methodology, or market evidence.

## Consequences

- ADR-013's later reviewed routing leaf translates an already directional
  policy result through this adapter without duplicating or guessing enum-name
  semantics.
- Neutral cannot accidentally become bearish, false, a loss, or a calculator
  input through this adapter. ADR-013 preserves it only as the original
  non-directional polarity result.
- A side-enum change fails exhaustive compilation/tests rather than inheriting a
  fallback. A genuinely different translation policy requires a separate
  reviewed contract; this mechanical bridge must not acquire version/hash
  semantics retroactively.
- Target/return input selection, unavailable-state composition, calculator
  invocation, horizon observations, methodology activation, fingerprinting,
  persistence, aggregation, and publication remain later P3 work.
- No API key, provider account, paid plan, domain, data license, market calendar,
  price feed, or network access is required.
