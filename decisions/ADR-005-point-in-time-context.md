# ADR-005 — Point-in-Time Macro and Event Context

- Status: Accepted
- Date: 2026-08-19

## Context

The P1 roadmap requires an immutable macro snapshot in addition to the existing
market snapshot. The product must reconstruct only information available at an
analyst call's event time, preserve later macro revisions without rewriting the
past, and retain source evidence. The domain also names event context, but P1
must not prematurely encode computed proximity, market regimes, positioning,
scoring, or provider infrastructure.

The existing analyst-call detail response is closed and already exposes the
market snapshot. Changing that shape would create avoidable compatibility risk.

## Decision

### Additive read contract

P1 adds `GET /v1/calls/{id}/context`. Its closed response is exactly:

```text
CallContext {
  macroSnapshot: MacroSnapshot | null,
  eventContext: EventContext | null
}
```

A known call without context returns both keys with JSON null. Invalid and
unknown identifiers use the existing closed Problem responses. The context
prefix is GET-only; there is no mutation endpoint. The exact call list/detail,
revision, and outcome shapes remain unchanged.

### Macro observations and snapshot selection

`MacroObservation` retains series, nullable decimal value, unit, observation
date, release time, processing time, nullable vintage bounds, source reference,
data mode, capture time, and provenance. The supported P1 series are:

1. `FED_FUNDS_LOWER`
2. `FED_FUNDS_UPPER`
3. `CPI_YOY`
4. `CORE_CPI_YOY`
5. `PPI_YOY`
6. `UNEMPLOYMENT_RATE`

`MacroSnapshot` is an immutable one-per-call aggregate. Its canonical
`observations` array contains exactly one record per supported series in the
order above. Missing numeric values remain explicit nulls.

The fixture DTO retains a standalone observation pool and ordered observation
identifiers. The adapter resolves those identifiers into the embedded canonical
array. Keeping the pool separate is intentional: later revisions can be stored
and audited without becoming eligible for a historical snapshot.

An observation is eligible only when all of these rules hold:

- the snapshot `eventTime` equals its call's `eventTime`;
- `releasedAt <= observation.processingTime <= observation.capturedAt`;
- `releasedAt <= snapshot.eventTime`;
- observation processing/capture are no later than snapshot processing/capture;
- when supplied, `vintageStart <= UTC(snapshot.eventTime).date <= vintageEnd`.

Vintage bounds are inclusive. A null start or end is an open bound. The DEMO
fixture stores an original CPI vintage whose interval contains the call-001
event date and a later revision whose interval begins afterward. The later
revision remains persisted and source-linked but is never substituted into the
call-001 snapshot.

### Event context

`EventContext` stores only the schedule timestamps observed for the call:
`earningsAt`, `nextCpiAt`, `nextFomcAt`, `nextNfpAt`, and
`optionsExpirationAt`. Every field is present and nullable. Non-null `next*`
and options-expiration timestamps cannot precede the context event time;
`earningsAt` may precede or follow it because the retained context may describe
proximity to a recent or upcoming earnings event.

No duration, proximity score, boolean event flag, regime classification, or
causal claim is stored in P1. Time-dependent derived features, if later added,
must be deterministic and versioned rather than based on the current clock.

### Evidence and phase boundary

Every selected or archived observation and every event context references a
canonical source reference and document metadata record. Full articles,
reports, and other copyrighted payloads remain outside the fixture and serving
contract.

P1 uses synthetic DEMO fixtures and PostgreSQL only. The existing call-detail
page receives a minimal evidence-first context section that exposes DEMO mode,
as-of/capture times, source evidence, explicit `NA`, and known-empty state. It
does not render derived proximity, flags, regimes, scores, or causal claims.
Broader context dashboards, leaderboards, and interactions belong to P2;
outcome calculations and golden tests belong to P3; realtime transport belongs
to P4; and real macro providers belong to P5. PositioningSnapshot and
MarketRegime remain deferred to later analytics/data-provider phases.

## Consequences

- Historical macro context remains reproducible when a series is revised.
- Known-empty context is distinguishable from an unknown call and from a zero
  value.
- Consumers gain a focused read endpoint without breaking existing call detail
  clients.
- Persistence must retain standalone observations and explicit snapshot order,
  and must enforce cross-record timing, vintage, identity, and evidence rules
  that JSON Schema alone cannot prove.
