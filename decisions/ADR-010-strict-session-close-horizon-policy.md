# ADR-010 — Strict Session-Close Horizon and Forecast-Basis Policy

- Status: Accepted
- Date: 2026-08-21

## Context

ADR-007 proves next-N mechanics only after a caller supplies an anchor, and
ADR-008 classifies an event against explicit session boundaries without choosing
an anchor. Neither decision assigns the canonical `D1`, `W1`, `M1`, `M3`, `M6`,
or `Y1` labels, selects an original-call or correction basis, or identifies a
horizon endpoint.

The product owner approved a deterministic session-close policy: evaluate an
original forecast and each already-validated correction as separate immutable
lineages, start each lineage at its own event time, and count explicit session
closes strictly after that basis event. The repository still has no licensed
calendar, observed bar, endpoint price, or runtime outcome orchestration. The
policy therefore remains a disconnected schedule-selection leaf over a caller-
supplied catalog.

## Decision

The fifth isolated P3 slice implements a pure strict session-close horizon
resolver.

- `OutcomeBasis` is sealed as exactly `Original(callId, eventTime)` and
  `Correction(callId, basisRevisionId, eventTime)`. `Original.basisRevisionId()`
  is exactly null; `Correction.basisRevisionId()` is required. Both expose their
  own UTC, microsecond-precision event time. The caller, not this primitive,
  proves that an original or correction belongs to the call and is valid.
- An original and every caller-validated correction are independent schedule
  lineages. A correction uses `correction.eventTime`, never the original call
  time, and never rewrites or suppresses the original lineage. Cancellation is
  not a permitted basis and cancellation eligibility remains deferred.
- `SessionCloseHorizonPolicyVersion` contains exactly
  `STRICTLY_AFTER_BASIS_EVENT_SESSION_CLOSE_V1`. Its named-horizon counts are
  fixed as `D1=1`, `W1=5`, `M1=21`, `M3=63`, `M6=126`, and `Y1=252`.
- Eligible sessions are exactly the supplied catalog entries whose
  `closesAt > basis.eventTime`, in supplied order. The first eligible close is
  D1; the Nth eligible close is the endpoint for a horizon with count N. The
  returned session list contains exactly the first N eligible entries and the
  endpoint is its final entry.
- Consequently, an event before the first supplied open selects the first
  session; an event at open or inside a session may select that session; an
  event exactly at close skips that session; a touching close/open selects the
  opening session; and an event in a strict gap selects the following session.
  Empty catalogs, events at or after the final close, and otherwise absent first
  eligible entries return `FIRST_ELIGIBLE_SESSION_MISSING`. A first eligible
  entry with fewer than N eligible sessions returns
  `HORIZON_ENDPOINT_SESSION_MISSING`. No date, timezone, weekday, holiday, or
  session is inferred.
- `SessionCloseHorizonRequest` contains exactly `policyVersion`, `basis`,
  `horizon`, and `catalog`. It has no evaluation-as-of, clock, price,
  observation, provider, provenance, methodology selector, or persistence
  instruction.
- `SessionCloseHorizonResolution` is sealed as `Resolved(window)` or
  `Incomplete(context, reason)`. Its context contains exactly policy version,
  policy definition hash, basis, horizon, session count, calendar ID, and
  catalog revision. A resolved window contains exactly context, immutable
  selected sessions, and the final endpoint session. Resolved means schedule
  identification only; it is not ready, observed, calculated, or complete.
- Public `ResolutionContext` and `ResolvedSessionWindow` constructors enforce
  only facts decidable from their own components: matching policy hash/count,
  exact window size, a first close strictly after the basis event, unique and
  chronological nonoverlapping entries, and endpoint-last equality. A public
  window contains no catalog, so direct construction cannot attest catalog
  membership, adjacency, or that its entries are the catalog's first N eligible
  sessions. Likewise, a public `Incomplete` value cannot prove that its reason
  matches catalog coverage. Those catalog-membership, first-N selection, and
  incomplete-reason guarantees belong only to `SessionCloseHorizonResolver`.

## Canonical policy definition

The definition is exactly the following single-line 633-byte ASCII sequence,
encoded directly as UTF-8 with the shown key order, punctuation, case, and
values. It has no byte-order mark, leading/trailing whitespace, or trailing
line ending:

```text
{"policyVersion":"STRICTLY_AFTER_BASIS_EVENT_SESSION_CLOSE_V1","lineageMode":"ORIGINAL_AND_EACH_VALID_CORRECTION","originalEventField":"call.eventTime","correctionEventField":"correction.eventTime","cancellationBasisAllowed":false,"eligibleSessionPredicate":"session.closesAt>basis.eventTime","eligibleSessionOrder":"SUPPLIED_CATALOG_ORDER","windowSelection":"FIRST_N_ELIGIBLE","endpointSelection":"NTH_ELIGIBLE","firstEligibleMissingReason":"FIRST_ELIGIBLE_SESSION_MISSING","horizonEndpointMissingReason":"HORIZON_ENDPOINT_SESSION_MISSING","readinessState":"ABSENT","sessionCounts":{"D1":1,"W1":5,"M1":21,"M3":63,"M6":126,"Y1":252}}
```

Its lowercase SHA-256 is exactly:

```text
550087efe7ddf2ba31974c89c2740ab79df986eefef48919c32c56a3232f8dc1
```

`canonicalDefinition()` returns the exact character sequence,
`canonicalDefinitionUtf8()` returns a defensive byte-array copy, and
`definitionHash()` returns the fixed digest. Every resolution context echoes
that digest. The definition is locked in code, this ADR, and source-local golden
tests; it is not either existing `standard-call-outcome` methodology definition
and does not activate or reinterpret either `MODEL_ONLY` hash.

## Purity and evidence boundary

The resolver may use `OutcomeHorizon`, the existing explicit
`TradingSessionCatalog`, and deterministic JDK hashing/UTF-8 support required to
verify its own closed definition. It does not call the earlier relation
classifier or session-offset resolver, because this policy scans closes
directly and has neither an anchor nor evaluation-as-of state.

The catalog ID and revision remain caller labels. A resolved endpoint does not
prove that the schedule was licensed, complete, captured at a point in time, or
associated with the call's asset or venue. The slice adds no calendar fixture,
provider, network call, account, credential, schema, manifest member, OpenAPI
path, Flyway migration, database row, API behavior, scheduler, or web surface.

## Consequences

- The approved named horizons, exact-close boundary, and independent correction
  clock can be replayed without inventing prices or observed performance.
- Consumers that require policy resolution must use the resolver rather than
  treating directly constructed public result records as catalog-derived
  evidence.
- A policy-rule change, count change, key-order change, or byte change requires
  a new policy version and digest. A different supplied catalog revision is
  preserved in the resolution context and later input fingerprinting.
- Endpoint price selection, deterministic return calculation, point-in-time
  calendar evidence, corporate actions, currency treatment, cancellation
  eligibility, canonical scoring-methodology activation, input fingerprinting,
  outcome persistence, aggregation, and publication remain later work.
- No external API key, account, paid plan, domain, or data license is required
  for this disconnected DEMO/test schedule policy. Real calendar and price
  rights remain P5-owned provider work.
