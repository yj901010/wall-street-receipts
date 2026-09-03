# ADR-011 — Call-Direction Polarity Policy

- Status: Accepted
- Date: 2026-08-21

## Context

The pure target-hit and directional-win calculators deliberately accept only an
already interpreted bullish or bearish side. They do not reduce the canonical
five-value `CallDirection` enum, and they do not decide whether `NEUTRAL` is a
loss, unavailable input, exclusion, or a separate non-directional state.

The product owner approved one explicit reduction policy: strong directions
collapse to their ordinary polarity, ordinary directions preserve their
polarity, and neutral remains explicitly non-directional. This decision can be
locked as a disconnected domain policy without selecting a horizon, obtaining a
price, calculating a return, invoking either comparison calculator, or
publishing an outcome.

## Decision

The sixth isolated P3 slice implements a pure call-direction polarity resolver.

- `CallDirectionPolarityRequest` contains exactly `policyVersion` and one
  canonical `CallDirection direction`. A null request, policy, or direction is
  invalid and fails closed.
- `CallDirectionPolarityPolicyVersion` contains exactly
  `COLLAPSE_STRONG_DIRECTIONS_NEUTRAL_NON_DIRECTIONAL_V1`.
- The mapping is exhaustive and exact:
  - `STRONG_BULLISH` maps to directional `BULLISH`.
  - `BULLISH` maps to directional `BULLISH`.
  - `NEUTRAL` maps to non-directional reason `NEUTRAL_DIRECTION`.
  - `BEARISH` maps to directional `BEARISH`.
  - `STRONG_BEARISH` maps to directional `BEARISH`.
- `CallDirectionPolarityResolution` is sealed as exactly
  `Directional(context, side)` or `NonDirectional(context, reason)`. Its nested
  `DirectionalSide` contains exactly `BULLISH` and `BEARISH`; its nested
  `NonDirectionalReason` contains exactly `NEUTRAL_DIRECTION`.
- `ResolutionContext` contains exactly policy version, policy-definition hash,
  and the original `CallDirection`. Every result preserves that source
  direction rather than replacing `STRONG_BULLISH` or `STRONG_BEARISH` with the
  collapsed side.
- Public result constructors fail closed unless the context direction matches
  the exact directional side or non-directional reason. A directly constructed
  result therefore cannot relabel a neutral direction as bearish, represent a
  strong direction with the wrong side, or attach `NEUTRAL_DIRECTION` to a
  directional source.
- Neutral is not `Available(false)`, a miss, a loss, bearish, excluded,
  incomplete, unavailable evidence, null, zero, or a calculated outcome. It is
  an explicit policy result proving only that the source direction has no
  directional polarity under this version.
- The resolver uses an exhaustive enum switch. It performs no ordinal, enum-name
  parsing, string normalization, map lookup with a default, fallback, or
  inference for an unknown future direction.

## Canonical policy definition

The definition is exactly the following single-line 489-byte ASCII sequence,
encoded directly as UTF-8 with the shown key order, punctuation, case, and
values. It has no byte-order mark, leading/trailing whitespace, or trailing
line ending:

```text
{"policyVersion":"COLLAPSE_STRONG_DIRECTIONS_NEUTRAL_NON_DIRECTIONAL_V1","inputType":"CallDirection","mappings":{"STRONG_BULLISH":"BULLISH","BULLISH":"BULLISH","NEUTRAL":"NON_DIRECTIONAL","BEARISH":"BEARISH","STRONG_BEARISH":"BEARISH"},"resultVariants":["DIRECTIONAL","NON_DIRECTIONAL"],"directionalSides":["BULLISH","BEARISH"],"nonDirectionalReason":"NEUTRAL_DIRECTION","directResultConsistency":"DIRECTION_MUST_MATCH_MAPPING","nullDirectionBehavior":"REJECT","fallbackBehavior":"ABSENT"}
```

Its lowercase SHA-256 is exactly:

```text
d83eccc92fedd7ba025745be2c8e78245bc308d0ff479467fa61afe543dc8a50
```

`canonicalDefinition()` returns the exact character sequence,
`canonicalDefinitionUtf8()` returns a defensive byte-array copy, and
`definitionHash()` returns the fixed digest. Every resolution context echoes
that digest. The policy identity is independent of the strict-close horizon
policy and both existing model-only scoring-methodology hashes.

## Purity and evidence boundary

Production code may depend only on the canonical `CallDirection`, its own
direction-package types, and deterministic JDK null/UTF-8 support. It does not
depend on a call or revision aggregate, target-hit or directional-win
calculator, horizon/window/calendar policy, price, return, observation,
methodology, fingerprint, provider, repository, fixture, JSON, Spring,
persistence, controller, scheduler, network, clock, locale, timezone, random,
LLM, or binary floating-point type.

The policy is not wired into any product runtime. It does not mutate the
canonical `CallDirection` enum, schemas, fixtures, manifest, OpenAPI, Flyway,
database rows, API behavior, provider, or web source. Source-local golden
vectors are tests only and are not market or analyst evidence.

## Consequences

- The canonical five directions now have one replayable polarity reduction
  without silently treating neutral as a failed directional call.
- Downstream orchestration may later translate directional sides to the
  calculator-specific side enums, but this policy does not perform that wiring
  or claim the calculators were invoked.
- A mapping, result-shape, reason, key-order, or byte change requires a new
  policy version and digest. A future `CallDirection` enum value cannot inherit
  a default polarity.
- Horizon/observation selection, return calculation, target eligibility,
  cancellation eligibility, methodology activation, input fingerprinting,
  outcome persistence, aggregation, and publication remain later reviewed P3
  work.
- No API key, provider account, paid plan, domain, data license, or network
  access is required. Real calendar and price rights remain P5-owned provider
  work.
