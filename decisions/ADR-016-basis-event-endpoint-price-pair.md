# ADR-016 — Point-in-Time Basis-Event/Endpoint Price Pair

- Status: Accepted
- Date: 2026-08-24

## Context

ADR-014 selects one official primary-venue endpoint close, but an asset return
also needs a basis price that preserves what the source recorded at the
original or correction event. Substituting a prior session close, nearest
trade, interpolated value, or current quote would change the economic interval
and could introduce post-call information.

The approved V1 contract therefore pairs the exact source-recorded basis-event
price with the complete ADR-014 endpoint resolution. Because the endpoint is
split/reverse-split adjusted to the endpoint-share basis and
dividend-unadjusted, independent point-in-time adjustment evidence must bind
both observations before the pair can be consumed by a return calculator.

## Decision

The eleventh isolated P3 slice adds exactly seven production types in
`com.wallstreetreceipts.api.domain.outcome.pricepair` and one matching
`AssetReturnPricePairSelectorGoldenTest`.

- `BasisPriceObservation` preserves observation and provider-event identity,
  exact original/correction `OutcomeBasis`, asset, venue, currency, price-source
  ID/revision, provenance, field, adjustment basis, continuity, observed/
  available/captured times, and the original positive price. Price must be
  exactly `NUMERIC(38,12)`-representable; times are microsecond precision and
  satisfy `observedAt <= availableAt <= capturedAt`.
- `PricePairAdjustmentEvidence` independently preserves adjustment evidence,
  provider event, source revision, provenance, exact basis, asset, primary
  venue, currency, both selected observation/provider-event links, coverage,
  adjustment basis, continuity, and PIT timestamps. Coverage must satisfy
  `coverageStartsAt <= coverageEndsAt <= availableAt <= capturedAt`.
- `AssetReturnPricePairRequest` accepts only the V1 policy, one complete ADR-014
  endpoint-price resolution using its exact policy/hash, and immutable
  non-null copies of all basis and adjustment candidates.
- `evaluationAsOf` is inherited from the endpoint context. Basis and adjustment
  candidates are known only when both `availableAt` and `capturedAt` are not
  after it. Future exact, wrong-identity, or duplicate candidates are invisible
  to every output, context, mismatch gate, and cardinality decision.
- Null/future basis evidence composes with endpoint unavailability exactly:
  both unavailable preserves the endpoint reason as
  `BASIS_AND_ENDPOINT_PRICE_UNAVAILABLE`; basis only is
  `BASIS_PRICE_MISSING_AS_OF`; endpoint only is
  `ENDPOINT_PRICE_UNAVAILABLE` with the exact nested reason.
- Known basis candidates are checked, in order, for basis, asset, primary
  venue, currency, price-source ID/revision, exact
  `observedAt == basis.eventTime`, source-recorded basis-event field, adjustment
  basis, and continuity. Exactly one candidate may pass; duplicates are
  `BASIS_PRICE_AMBIGUOUS` with no deduplication.
- Known adjustment candidates are then checked, in order, for basis, asset,
  venue, currency, exact basis and endpoint observation/provider-event links,
  exact coverage endpoints, adjustment basis, and continuity. Exactly one may
  pass; duplicates are `ADJUSTMENT_EVIDENCE_AMBIGUOUS`.
- The common basis is exactly split/reverse-split adjusted to the endpoint-share
  basis and dividend-unadjusted. Currency is exact with no FX. Only
  `SPLIT_REVERSE_SPLIT_CONTINUOUS` resolves; merger, spin-off, delisting,
  special distribution, and unknown continuity remain unavailable.
- `AssetReturnPricePairResolution` is sealed as exactly
  `Resolved(context,basisObservation,adjustmentEvidence)` or
  `Unavailable(context,reason,endpointReason)`. Context preserves only the pair
  policy identity and complete endpoint resolution, so future candidate lists
  cannot leak into a result.

The exact unavailable-reason order is:

1. `BASIS_AND_ENDPOINT_PRICE_UNAVAILABLE`
2. `BASIS_PRICE_MISSING_AS_OF`
3. `ENDPOINT_PRICE_UNAVAILABLE`
4. `BASIS_MISMATCH`
5. `ASSET_MISMATCH`
6. `PRIMARY_VENUE_MISMATCH`
7. `CURRENCY_MISMATCH`
8. `PRICE_SOURCE_MISMATCH`
9. `OBSERVED_AT_MISMATCH`
10. `PRICE_FIELD_MISMATCH`
11. `BASIS_PRICE_ADJUSTMENT_BASIS_MISMATCH`
12. `BASIS_PRICE_CONTINUITY_UNAVAILABLE`
13. `BASIS_PRICE_AMBIGUOUS`
14. `ADJUSTMENT_EVIDENCE_MISSING_AS_OF`
15. `ADJUSTMENT_OUTCOME_BASIS_MISMATCH`
16. `ADJUSTMENT_ASSET_MISMATCH`
17. `ADJUSTMENT_PRIMARY_VENUE_MISMATCH`
18. `ADJUSTMENT_CURRENCY_MISMATCH`
19. `BASIS_OBSERVATION_LINK_MISMATCH`
20. `ENDPOINT_OBSERVATION_LINK_MISMATCH`
21. `ADJUSTMENT_COVERAGE_MISMATCH`
22. `ADJUSTMENT_PRICE_BASIS_MISMATCH`
23. `ADJUSTMENT_CONTINUITY_UNAVAILABLE`
24. `ADJUSTMENT_EVIDENCE_AMBIGUOUS`

## Canonical policy definition

`AssetReturnPricePairPolicyVersion` contains exactly
`SOURCE_RECORDED_BASIS_EVENT_TO_OFFICIAL_ENDPOINT_PRICE_PAIR_V1`. Its
definition is exactly the following single-line 4655-byte ASCII sequence
encoded directly as UTF-8, with no byte-order mark, surrounding whitespace, or
trailing line ending:

```text
{"policyVersion":"SOURCE_RECORDED_BASIS_EVENT_TO_OFFICIAL_ENDPOINT_PRICE_PAIR_V1","requiredEndpointPolicyVersion":"OFFICIAL_PRIMARY_VENUE_CLOSE_SPLIT_ADJUSTED_V1","requiredEndpointPolicyDefinitionHash":"37e37aba9302d77366cef4129f77a82b7ccb2f1937bfffc0315ea8d0bc6b1f76","endpointInput":"ENDPOINT_PRICE_RESOLUTION","basisObservationFields":["observationId","providerEventId","basis","assetId","venueId","currency","priceSourceId","priceSourceRevision","provenanceId","priceField","adjustmentBasis","corporateActionContinuity","observedAt","availableAt","capturedAt","price"],"adjustmentEvidenceFields":["adjustmentEvidenceId","providerEventId","basis","assetId","primaryVenueId","currency","adjustmentSourceId","adjustmentSourceRevision","provenanceId","basisObservationId","basisProviderEventId","endpointObservationId","endpointProviderEventId","coverageStartsAt","coverageEndsAt","adjustmentBasis","corporateActionContinuity","availableAt","capturedAt"],"selectedEvidenceRule":"PRESERVE_COMPLETE_BASIS_OBSERVATION_AND_ADJUSTMENT_EVIDENCE_RECORDS","evaluationAsOfSource":"endpoint.context.evaluationAsOf","basisCandidateScope":"ALL_REQUEST_BASIS_CANDIDATES","basisKnownPredicate":"availableAt<=evaluationAsOf&&capturedAt<=evaluationAsOf","basisTemporalRule":"observedAt<=availableAt<=capturedAt","futureBasisRule":"INVISIBLE_TO_ALL_OUTPUT_AND_REASONING","basisMissingEndpointTruthTable":{"basisMissingAsOf&&endpointUnavailable":"BASIS_AND_ENDPOINT_PRICE_UNAVAILABLE_WITH_EXACT_ENDPOINT_REASON","basisMissingAsOfOnly":"BASIS_PRICE_MISSING_AS_OF","endpointUnavailableOnly":"ENDPOINT_PRICE_UNAVAILABLE_WITH_EXACT_ENDPOINT_REASON"},"basisIdentityPrecedence":["BASIS_MISMATCH","ASSET_MISMATCH","PRIMARY_VENUE_MISMATCH","CURRENCY_MISMATCH","PRICE_SOURCE_MISMATCH","OBSERVED_AT_MISMATCH","PRICE_FIELD_MISMATCH","BASIS_PRICE_ADJUSTMENT_BASIS_MISMATCH","BASIS_PRICE_CONTINUITY_UNAVAILABLE"],"basisRule":"candidate.basis==endpoint.horizon.context.basis","basisObservedAtRule":"candidate.observedAt==candidate.basis.eventTime","basisPriceField":"SOURCE_RECORDED_BASIS_EVENT_PRICE","assetVenueCurrencyRule":"EXACT_ENDPOINT_BINDING_MATCH_NO_FX","priceSourceRule":"PRICE_SOURCE_ID_AND_REVISION_EXACT_ENDPOINT_BINDING_MATCH","basisPriceBoundary":"POSITIVE_NUMERIC_38_12","basisCandidateCardinality":"EXACTLY_ONE_KNOWN_AS_OF_AFTER_MISMATCH_GATES","adjustmentCandidateScope":"ALL_REQUEST_ADJUSTMENT_CANDIDATES","adjustmentKnownPredicate":"availableAt<=evaluationAsOf&&capturedAt<=evaluationAsOf","adjustmentTemporalRule":"coverageStartsAt<=coverageEndsAt<=availableAt<=capturedAt","futureAdjustmentRule":"INVISIBLE_TO_ALL_OUTPUT_AND_REASONING","adjustmentMissingReason":"ADJUSTMENT_EVIDENCE_MISSING_AS_OF","adjustmentIdentityPrecedence":["ADJUSTMENT_OUTCOME_BASIS_MISMATCH","ADJUSTMENT_ASSET_MISMATCH","ADJUSTMENT_PRIMARY_VENUE_MISMATCH","ADJUSTMENT_CURRENCY_MISMATCH","BASIS_OBSERVATION_LINK_MISMATCH","ENDPOINT_OBSERVATION_LINK_MISMATCH","ADJUSTMENT_COVERAGE_MISMATCH","ADJUSTMENT_PRICE_BASIS_MISMATCH","ADJUSTMENT_CONTINUITY_UNAVAILABLE"],"observationLinkRule":"BASIS_AND_ENDPOINT_OBSERVATION_ID_AND_PROVIDER_EVENT_ID_EXACT_MATCH","coverageRule":"coverageStartsAt==basis.observedAt&&coverageEndsAt==endpoint.observedAt","adjustmentSourceRule":"PRESERVE_ID_REVISION_PROVIDER_EVENT_AND_PROVENANCE_NO_PREFERENCE","priceBasis":"SPLIT_AND_REVERSE_SPLIT_ADJUSTED_TO_ENDPOINT_SHARE_BASIS_DIVIDEND_UNADJUSTED","continuityRule":"SPLIT_REVERSE_SPLIT_CONTINUOUS_ONLY","unsupportedActions":["MERGER","SPIN_OFF","DELISTING","SPECIAL_DISTRIBUTION","UNKNOWN"],"adjustmentCandidateCardinality":"EXACTLY_ONE_KNOWN_AS_OF_AFTER_MISMATCH_GATES","evaluationPrecedence":["BASIS_AND_ENDPOINT_PRICE_UNAVAILABLE","BASIS_PRICE_MISSING_AS_OF","ENDPOINT_PRICE_UNAVAILABLE","BASIS_MISMATCH","ASSET_MISMATCH","PRIMARY_VENUE_MISMATCH","CURRENCY_MISMATCH","PRICE_SOURCE_MISMATCH","OBSERVED_AT_MISMATCH","PRICE_FIELD_MISMATCH","BASIS_PRICE_ADJUSTMENT_BASIS_MISMATCH","BASIS_PRICE_CONTINUITY_UNAVAILABLE","BASIS_PRICE_AMBIGUOUS","ADJUSTMENT_EVIDENCE_MISSING_AS_OF","ADJUSTMENT_OUTCOME_BASIS_MISMATCH","ADJUSTMENT_ASSET_MISMATCH","ADJUSTMENT_PRIMARY_VENUE_MISMATCH","ADJUSTMENT_CURRENCY_MISMATCH","BASIS_OBSERVATION_LINK_MISMATCH","ENDPOINT_OBSERVATION_LINK_MISMATCH","ADJUSTMENT_COVERAGE_MISMATCH","ADJUSTMENT_PRICE_BASIS_MISMATCH","ADJUSTMENT_CONTINUITY_UNAVAILABLE","ADJUSTMENT_EVIDENCE_AMBIGUOUS","RESOLVE"],"resultContext":"POLICY_IDENTITY_AND_COMPLETE_ENDPOINT_PRICE_RESOLUTION_ONLY","endpointUnavailableRule":"PRESERVE_EXACT_NESTED_ENDPOINT_REASON","priorCloseBehavior":"ABSENT","nearestPriceBehavior":"ABSENT","interpolationBehavior":"ABSENT","deduplication":"ABSENT","fallbackBehavior":"ABSENT"}
```

Its lowercase SHA-256 is exactly
`895e4bc97ebb3a92b80f2c58e2d28abb94440eeca963046ee755fa98825f4887`.
Returned bytes are defensive, and every context echoes this exact digest. Any
changed rule, reason, precedence, identity, or byte requires a new version and
digest.

## Purity and external-data boundary

Production adds exactly `BasisPriceField.java`, `BasisPriceObservation.java`,
`PricePairAdjustmentEvidence.java`, `AssetReturnPricePairPolicyVersion.java`,
`AssetReturnPricePairRequest.java`, `AssetReturnPricePairResolution.java`, and
`AssetReturnPricePairSelector.java`. The selector performs no provider lookup,
database read, calendar inference, price interpolation, return calculation, or
product publication.

No API key, account, paid plan, domain, provider license, named secret, or
network access is needed for this disconnected contract. Before P5 supplies
non-DEMO evidence, a selected vendor must grant historical event-time/intraday
price entitlement plus calendar, asset/venue/reference, and corporate-action
rights, including display, storage, derived-data, and redistribution terms.
Only then may a reviewed adapter and scoped secret name be introduced.

## Consequences

- The basis is the source-recorded event price, not a session close, so the
  measured interval starts at the forecast event without prior-close leakage.
- Future evidence cannot change a replay, and exact duplicate evidence remains
  ambiguous rather than silently deduplicated.
- Public constructors validate only locally decidable consistency. Only
  `AssetReturnPricePairSelector` attests request membership, PIT filtering,
  precedence, and cardinality.
- ADR-017 may calculate a signed price return from one complete pair. Runtime
  orchestration, fingerprints, persistence, aggregation, and publication remain
  later reviewed work.
