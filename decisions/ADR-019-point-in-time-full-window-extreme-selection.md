# ADR-019 — Point-in-Time Attested Causal-Window Favorable Extreme

- Status: Accepted
- Date: 2026-08-24

## Context

ADR-018 can prove that one directional, present, normalized target and its
strict ADR-010 horizon are ready to seek full-window evidence. It deliberately
does not select a price. ADR-006 requires an upstream-selected window high for
a bullish target and window low for a bearish target, but cannot validate that
the supplied value came from the correct post-call economic interval.

A regular-session daily high or low is unsafe for an intraday analyst call:
the same session may contain a target-reaching price before the basis event.
Likewise, the endpoint close selected by ADR-014 is not a favorable extreme.
V1 therefore needs an exact causal-window contract without claiming that this
repository aggregates or independently verifies raw intraday observations.

## Decision

The fourteenth isolated P3 slice adds a pure point-in-time favorable-extreme
selector in
`com.wallstreetreceipts.api.domain.outcome.favorableextreme`.

- `FavorableExtremeRequest` accepts exactly the V1 policy, one complete ADR-018
  `ReadyForWindowEvidence`, a nullable point-in-time `WindowPriceBinding`, and
  an immutable copy of all caller-supplied `FullWindowHighLowObservation`
  candidates. `evaluationAsOf`, strict horizon, side, target, and catalog are
  inherited from the readiness result; the request cannot supply competing
  values.
- The economic observation set is exactly primary-venue regular-session
  observations belonging to the ordered ADR-010 horizon sessions where
  `observation.time > basis.eventTime` and
  `observation.time <= endpointSession.closesAt`. The basis-event lower bound
  is exclusive and endpoint-close upper bound inclusive. This includes a
  session open only when it is strictly after the basis event, excludes
  pre-call values from an intraday first session, excludes after-hours and
  inter-session gaps, and never substitutes an endpoint close.
- After the target-adjustment-basis gate passes, `WindowPriceBinding` preserves
  exact binding identity/revision, asset,
  primary venue, currency, price-source ID/revision, availability/capture
  timestamps, and provenance. Null or future binding evidence is identical
  `BINDING_NOT_KNOWN_AS_OF` evidence and is absent from the result.
- `FullWindowHighLowObservation` preserves observation/provider-event identity,
  exact basis and horizon, asset, venue, currency, source/revision, provenance,
  catalog ID/revision, ordered session IDs, both bound instants and boundary
  types, price-field and coverage attestations, adjustment and continuity,
  availability/capture timestamps, and both `windowHigh` and `windowLow`.
  High and low are positive, exactly `NUMERIC(38,12)`-representable, and low
  cannot exceed high. The observation cannot become available before the
  attested window ends.
- Binding and candidates are known only when both `availableAt` and
  `capturedAt` are not after the inherited `evaluationAsOf`. Future exact,
  wrong, partial, or duplicate evidence is filtered before every identity,
  reason, cardinality, and output decision and cannot leak into result
  evidence.
- All known candidates are request-scoped. Any known invalid candidate poisons
  selection at the first fixed mismatch gate; candidates are never filtered
  down to a convenient valid subset. Exactly one candidate may remain after
  every gate. Multiple fully valid candidates are ambiguous even when they are
  equal or repeated, with no deduplication or source preference.
- A resolved bullish route preserves the observation's original `windowHigh`
  as `FavorableExtreme(HIGH, value)`; a resolved bearish route preserves its
  original `windowLow` as `FavorableExtreme(LOW, value)`. The selector performs
  no rounding, rescaling, raw maximum/minimum aggregation, target comparison,
  or calculator invocation.
- Target and observation prices must use the exact
  split/reverse-split-adjusted endpoint-share, dividend-unadjusted basis.
  Observation continuity must be exactly `SPLIT_REVERSE_SPLIT_CONTINUOUS`.
  Currency, venue, source revision, corporate-action basis, or continuity is
  never inferred or converted.
- `FavorableExtremeResolution` is sealed as exactly
  `Resolved(context, evidence, favorableExtreme)` or
  `Unavailable(context, evidence, reason)`. Context preserves the V1 policy
  identity and complete readiness result. Evidence preserves only the
  PIT-visible binding and candidates available through the deciding gate;
  evidence after that gate is cleared.
- The nested public result constructors validate consistency only for the
  evidence supplied to that constructor. Only
  `FavorableExtremeSelector.select(request)` sees the complete request and
  therefore attests request-wide candidate membership, PIT filtering,
  known-invalid poisoning, and cardinality. Downstream code must consume a
  selector-produced resolution when making those claims.

`EXACT_CAUSAL_WINDOW_SESSION_UNION` is an upstream provider/source attestation.
The selector validates the attestation's exact metadata, identity, boundaries,
field, adjustment, continuity, visibility, and cardinality. It does not prove
the claim by reading raw ticks or bars and must not be described as raw-data or
bar-level verification.

## Exact unavailable-reason order

The 22 reasons and their precedence are exactly:

1. `TARGET_ADJUSTMENT_BASIS_UNSUPPORTED`
2. `BINDING_NOT_KNOWN_AS_OF`
3. `BINDING_ASSET_MISMATCH`
4. `BINDING_PRIMARY_VENUE_MISMATCH`
5. `BINDING_CURRENCY_MISMATCH`
6. `OBSERVATION_MISSING_AS_OF`
7. `BASIS_MISMATCH`
8. `HORIZON_MISMATCH`
9. `ASSET_MISMATCH`
10. `PRIMARY_VENUE_MISMATCH`
11. `CURRENCY_MISMATCH`
12. `SOURCE_MISMATCH`
13. `CATALOG_MISMATCH`
14. `SESSION_WINDOW_MISMATCH`
15. `LOWER_BOUND_MISMATCH`
16. `UPPER_BOUND_MISMATCH`
17. `BOUNDARY_CONVENTION_MISMATCH`
18. `PRICE_FIELD_MISMATCH`
19. `WINDOW_COMPLETENESS_UNAVAILABLE`
20. `ADJUSTMENT_BASIS_MISMATCH`
21. `CORPORATE_ACTION_CONTINUITY_UNAVAILABLE`
22. `OBSERVATION_AMBIGUOUS`

The first five validate the already-known target and nullable binding before
candidate evidence. Zero PIT-visible candidates is missing. The next fifteen
candidate gates run in the shown order over all known candidates, independent
of request order. Ambiguity is evaluated only after every known candidate
passes every preceding gate.

## Canonical policy definition

`FavorableExtremePolicyVersion` contains exactly
`POINT_IN_TIME_ATTESTED_CAUSAL_WINDOW_HIGH_LOW_V1`. Its definition is exactly
the following single-line 4633-byte ASCII sequence encoded directly as UTF-8,
with no byte-order mark, surrounding whitespace, or trailing line ending:

```text
{"policyVersion":"POINT_IN_TIME_ATTESTED_CAUSAL_WINDOW_HIGH_LOW_V1","requiredEligibilityPolicyVersion":"POINT_IN_TIME_TARGET_HIT_INPUT_READINESS_V1","requiredEligibilityPolicyDefinitionHash":"a6b4c9f4e4d29b5f1a9b0c300e2d7b9505318c708dfb0ad0e88f71324cf65465","requestFields":["policyVersion","readyEligibility","binding","candidates"],"bindingFields":["bindingId","bindingRevision","assetId","primaryVenueId","currency","priceSourceId","priceSourceRevision","availableAt","capturedAt","provenanceId"],"observationFields":["observationId","providerEventId","basis","horizon","assetId","venueId","currency","priceSourceId","priceSourceRevision","provenanceId","calendarId","catalogRevision","orderedSessionIds","lowerBound","lowerBoundType","upperBound","upperBoundType","priceField","coverageCompleteness","adjustmentBasis","corporateActionContinuity","availableAt","capturedAt","windowHigh","windowLow"],"resolutionContextFields":["policyVersion","policyDefinitionHash","readyEligibility"],"selectionEvidenceFields":["binding","knownCandidates"],"resultVariants":{"Resolved":["context","evidence","favorableExtreme"],"Unavailable":["context","evidence","reason"]},"evaluationAsOfSource":"readyEligibility.context.evaluationAsOf","horizonSource":"readyEligibility.context.horizonResolution.resolved.window","sideSource":"readyEligibility.evidence.sideRouting.directionalRoute.targetHitSide","targetSource":"readyEligibility.evidence.targetEvidence","catalogSource":"readyEligibility.evidence.catalogEvidence","requiredAdjustmentBasis":"SPLIT_AND_REVERSE_SPLIT_ADJUSTED_TO_ENDPOINT_SHARE_BASIS_DIVIDEND_UNADJUSTED","bindingPitPredicate":"availableAt<=evaluationAsOf&&capturedAt<=evaluationAsOf","futureBindingRule":"IDENTICAL_TO_NULL_AND_INVISIBLE_TO_OUTPUT","bindingIdentityPrecedence":["BINDING_ASSET_MISMATCH","BINDING_PRIMARY_VENUE_MISMATCH","BINDING_CURRENCY_MISMATCH"],"candidateScope":"ALL_REQUEST_CANDIDATES","candidatePitPredicate":"availableAt<=evaluationAsOf&&capturedAt<=evaluationAsOf","futureCandidateRule":"INVISIBLE_TO_ALL_OUTPUT_AND_REASONING","economicObservationSet":"primary-venue regular-session observations belonging to horizon.window.sessions with observation.time>basis.eventTime&&observation.time<=endpointSession.closesAt","lowerBoundRule":"lowerBound==basis.eventTime&&lowerBoundType==EXCLUSIVE","upperBoundRule":"upperBound==endpointSession.closesAt&&upperBoundType==INCLUSIVE","sessionWindowRule":"orderedSessionIds==horizon.window.sessions.sessionId in exact order","coverageRule":"EXACT_CAUSAL_WINDOW_SESSION_UNION","priceFieldRule":"PRIMARY_VENUE_REGULAR_SESSION_CAUSAL_WINDOW_HIGH_LOW_PAIR","candidateIdentityPrecedence":["BASIS_MISMATCH","HORIZON_MISMATCH","ASSET_MISMATCH","PRIMARY_VENUE_MISMATCH","CURRENCY_MISMATCH","SOURCE_MISMATCH","CATALOG_MISMATCH","SESSION_WINDOW_MISMATCH","LOWER_BOUND_MISMATCH","UPPER_BOUND_MISMATCH","BOUNDARY_CONVENTION_MISMATCH","PRICE_FIELD_MISMATCH","WINDOW_COMPLETENESS_UNAVAILABLE","ADJUSTMENT_BASIS_MISMATCH","CORPORATE_ACTION_CONTINUITY_UNAVAILABLE"],"priceBoundary":"POSITIVE_NUMERIC_38_12_HIGH_AND_LOW_WITH_LOW_LESS_THAN_OR_EQUAL_TO_HIGH","continuityRule":"SPLIT_REVERSE_SPLIT_CONTINUOUS_ONLY","candidateCardinality":"EXACTLY_ONE_KNOWN_AS_OF_AFTER_MISMATCH_GATES","knownInvalidCandidateRule":"POISONS_SELECTION_BEFORE_AMBIGUITY","deduplication":"ABSENT","favorableExtremeSelection":{"BULLISH":"WINDOW_HIGH","BEARISH":"WINDOW_LOW"},"selectedValueRule":"PRESERVE_ORIGINAL_BIGDECIMAL_NO_ROUNDING_OR_RESCALE","attestationScope":"UPSTREAM_PROVIDER_SOURCE_ATTESTED_EXACT_WINDOW_HIGH_LOW_PAIR","rawAggregation":"ABSENT","rawObservationVerification":"ABSENT","deferredRawSemantics":["NO_TRADE","HALT","AUCTION","BAR_STRADDLE","CORRECTION_SEQUENCE","RAW_COVERAGE_PROOF"],"evaluationPrecedence":["TARGET_ADJUSTMENT_BASIS_UNSUPPORTED","BINDING_NOT_KNOWN_AS_OF","BINDING_ASSET_MISMATCH","BINDING_PRIMARY_VENUE_MISMATCH","BINDING_CURRENCY_MISMATCH","OBSERVATION_MISSING_AS_OF","BASIS_MISMATCH","HORIZON_MISMATCH","ASSET_MISMATCH","PRIMARY_VENUE_MISMATCH","CURRENCY_MISMATCH","SOURCE_MISMATCH","CATALOG_MISMATCH","SESSION_WINDOW_MISMATCH","LOWER_BOUND_MISMATCH","UPPER_BOUND_MISMATCH","BOUNDARY_CONVENTION_MISMATCH","PRICE_FIELD_MISMATCH","WINDOW_COMPLETENESS_UNAVAILABLE","ADJUSTMENT_BASIS_MISMATCH","CORPORATE_ACTION_CONTINUITY_UNAVAILABLE","OBSERVATION_AMBIGUOUS","RESOLVE"],"selectedEvidencePreservation":"EXACT_COMPLETE_VISIBLE_RECORDS","branchClearingRule":"EVIDENCE_AFTER_DECIDING_PRECEDENCE_GATE_IS_EMPTY","endpointPriceObservationInput":"ABSENT","endpointCloseFallback":"ABSENT","genericPriceField":"ABSENT","calculatorInvocation":"ABSENT","fallbackBehavior":"ABSENT"}
```

Its lowercase SHA-256 is exactly:

```text
e3a0e93030c8f09ae5398bf6df0f2e28eec14b0a31f5bea240fc78f2412c2463
```

`canonicalDefinitionUtf8()` returns a defensive byte-array copy and every
resolution context echoes this digest. A change to any field, boundary,
attestation, side selection, reason, precedence, or byte requires a new policy
version and digest.

## Purity and integration boundary

Production adds exactly six files:

- `FavorableExtremePolicyVersion.java`
- `WindowPriceBinding.java`
- `FullWindowHighLowObservation.java`
- `FavorableExtremeRequest.java`
- `FavorableExtremeResolution.java`
- `FavorableExtremeSelector.java`

The matching source-local test is exactly
`FavorableExtremeSelectorGoldenTest.java`. The selector consumes supplied
evidence only. It adds no raw bar/trade aggregator, endpoint-price input,
schema, canonical fixture, manifest member, OpenAPI path, Flyway migration,
database row, controller, repository, application service, scheduler, provider
adapter, network call, API behavior, or web source. It invokes no target-hit,
directional-win, asset-return, or target-error calculator and does not activate,
persist, aggregate, rank, or publish a methodology result.

No API key, account, paid plan, domain, vendor license, named secret, or network
access is needed for this disconnected source-local contract. Before real
evidence enters it, P5 must select a provider and establish entitled historical
intraday/tick data, exchange calendars, corporate actions, asset/venue
reference data, and explicit storage, display, derived-data, and redistribution
rights. Only then may a reviewed adapter introduce a named, scoped secret.

## Consequences and deferred work

- A later orchestrator may now consume the exact resolved favorable extreme and
  invoke the pure target-hit calculator while preserving all readiness and
  selector branches without fallback or recalculation.
- Raw intraday/tick ingestion and aggregation remain deferred. They must define
  no-trade, halt, bar-straddle, correction-sequence, auction, and raw-coverage
  proof semantics before producing the completeness attestation.
- Provider acquisition and licensing, methodology activation, input
  fingerprinting, append-only persistence, aggregation, ranking, MFE/MAE,
  alpha/sector alpha, cancellation eligibility, API/UI publication, and
  production scheduling remain later P3/P5 work.
