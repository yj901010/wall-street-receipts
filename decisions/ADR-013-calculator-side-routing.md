# ADR-013 — Calculator-Side Routing Evidence

- Status: Accepted
- Date: 2026-08-21

## Context

ADR-011 produces one closed `CallDirectionPolarityResolution`: either a
`Directional` record that preserves its original policy context and common
side, or a `NonDirectional(NEUTRAL_DIRECTION)` record. ADR-012 translates only
an already directional common side into the two existing calculator-specific
side enums. Neither contract routes the full polarity result, and neutral must
not be passed to a calculator or reinterpreted as false, a miss, a loss,
bearish, unavailable input, or missing evidence.

The approved polarity and adapter contracts permit one disconnected mechanical
composition. It introduces no new direction reduction, eligibility policy,
financial calculation, methodology identity, or market-data decision.

## Decision

The eighth isolated P3 slice adds exactly one production routing class and one
source-local golden test in a separate routing package.

- `CalculatorSideRouting` is public and final, has one private zero-argument
  constructor, and exposes exactly one public static method:
  `Result route(CallDirectionPolarityResolution resolution)`.
- Null resolution is invalid. The method uses an exhaustive sealed-pattern
  switch with no default, string parsing, ordinal logic, reflection, fallback,
  or future-variant inference.
- `Result` is a public sealed nested interface permitting exactly
  `DirectionalRoute` and `NonDirectionalRoute` and declaring no methods.
- A `Directional` source produces
  `DirectionalRoute(source, targetHitSide, directionalWinSide)`. The exact
  original `Directional` record is preserved. Both destination sides are
  derived only through `CalculatorSideAdapter`, so common `BULLISH` becomes
  both destination `BULLISH` values and common `BEARISH` becomes both
  destination `BEARISH` values.
- A `NonDirectional` source produces `NonDirectionalRoute(source)`. The exact
  original record, including its policy context and `NEUTRAL_DIRECTION`, is
  preserved. This branch contains no calculator side, Boolean, unavailable
  state, metric, or alternative reason.
- Public `DirectionalRoute` construction rejects null components and
  recomputes both expected adapter translations, rejecting either mismatched
  side. Public `NonDirectionalRoute` construction rejects null source. The
  source records' own ADR-011 constructors remain responsible for direction,
  side, reason, and policy-hash consistency.

The routing class receives no target, favorable extreme, price, return,
horizon, session, observation, call/revision aggregate, methodology,
fingerprint, completeness, or cancellation input. It does not construct a
calculator input, invoke a calculator, or return a calculator result.

## Identity, purity, and integration boundary

This mechanical composition receives no new policy or methodology version,
canonical definition, hash, fingerprint, provenance, or state. The preserved
source already carries ADR-011's exact original direction, policy version, and
definition hash; routing neither copies nor rewrites that context.

Production code may depend only on `Objects`, `CalculatorSideAdapter`, the two
calculator-side enums, `CallDirectionPolarityResolution`, and its exact
`Directional` and `NonDirectional` variants. It performs no decimal or binary
floating-point arithmetic and imports no calculator/input/result class,
horizon/calendar/window type, price/return/observation type, provider,
repository, fixture, JSON, Spring, persistence, controller, scheduler, network,
clock, locale, timezone, environment/process/thread/random, LLM, or reflection
dependency.

Outside the owning `domain.outcome.direction` package, only this exact routing
class may consume the full polarity resolution. Outside the adapter itself,
only this routing class may consume `CalculatorSideAdapter`. No other production
class references the routing class or package. Neither calculator, controller,
application service, provider, repository, scheduler, API response, nor web
source invokes it.

The slice adds no schema, canonical fixture, manifest member, OpenAPI path,
Flyway migration, database row, API behavior, provider, or web source.
Source-local vectors are contract tests only, not analyst, methodology, or
market evidence.

## Consequences

- All five canonical directions can traverse one closed evidence route without
  duplicating polarity or side-enum mapping logic.
- Strong directions retain their original source direction in the preserved
  context; neutral retains explicit non-directional evidence and has no
  calculator-side construction path.
- Adding a polarity-result variant, route-result variant, public method,
  fallback, or mismatched direct route fails compilation, construction,
  reflection goldens, or repository CI.
- Target eligibility, target/return input selection, horizon observations,
  unavailable-state composition, calculator invocation, methodology activation,
  fingerprinting, persistence, aggregation, and publication remain later P3
  work.
- No API key, provider account, paid plan, domain, data license, market
  calendar, price feed, or network access is required. Real provider selection
  and rights review remain P5 work.
