# ADR-014 — Point-in-Time Official Endpoint-Price Selection

- Status: Accepted
- Date: 2026-08-24

## Context

ADR-010 identifies a strict named-horizon endpoint from a caller-supplied
session catalog, but a resolved schedule window is not an observed market
price. It does not prove that a catalog, asset/primary-venue binding, or price
observation was known at an evaluation instant, nor does it define the price
field, currency, source revision, or corporate-action basis.

The approved P3 V1 contract needs one deterministic, point-in-time endpoint
close before target error or return calculations can consume an `actual`
price. This remains a source-local domain contract: it receives explicit
evidence and performs no provider lookup, database read, runtime publication,
or licensed-data claim.

## Decision

The ninth isolated P3 slice adds exactly ten production types in
`com.wallstreetreceipts.api.domain.outcome.observation` and one matching
`EndpointPriceSelectorGoldenTest`.

- `EndpointPriceRequest` accepts exactly the V1 policy, an ADR-010
  `SessionCloseHorizonResolution.Resolved`, catalog point-in-time evidence, an
  asset/primary-venue/source binding, a microsecond-precision
  `evaluationAsOf`, and an immutable copy of all caller-supplied candidates.
- The horizon must use
  `STRICTLY_AFTER_BASIS_EVENT_SESSION_CLOSE_V1` and definition hash
  `550087efe7ddf2ba31974c89c2740ab79df986eefef48919c32c56a3232f8dc1`.
  An incomplete horizon is not silently treated as a price request.
- Catalog and binding evidence are known only when both `availableAt` and
  `capturedAt` are not after `evaluationAsOf`. Their exact catalog/window
  identity is checked, and the endpoint close must have occurred by
  `evaluationAsOf`.
- Candidate observations are filtered for point-in-time visibility before any
  identity or mismatch test. A candidate is known only when both
  `availableAt <= evaluationAsOf` and `capturedAt <= evaluationAsOf`. Future
  exact, wrong-identity, or duplicate candidates are invisible to every
  output, context, and reason. No known candidate returns only
  `OBSERVATION_MISSING_AS_OF`.
- Known candidates are checked in exact order for asset, primary venue,
  currency, source ID/revision, catalog ID/revision, endpoint session,
  `observedAt == endpointSession.closesAt`, official regular-session close,
  approved adjustment basis, and split/reverse-split continuity.
- Exactly one fully valid known candidate resolves. More than one resolves to
  `OBSERVATION_AMBIGUOUS`; there is no deduplication, fallback, guessed
  observation, alternative venue, foreign-exchange conversion, or provider
  preference.
- Price is strictly positive and exactly representable as
  `NUMERIC(38,12)`. It is the official primary-venue regular-session close,
  adjusted for splits and reverse splits to the endpoint-share basis and
  unadjusted for dividends. Merger, spin-off, delisting, special distribution,
  or unknown continuity is unavailable rather than inferred.
- `EndpointPriceResolution` is sealed as exactly `Resolved(context,
  observation)` or `Unavailable(context, reason)`. Context preserves the
  policy identity plus the complete supplied horizon, catalog, binding, and
  evaluation-as-of evidence. Catalog, binding, and observation provenance stay
  distinct.

The exact unavailable-reason order is:

1. `CATALOG_NOT_KNOWN_AS_OF`
2. `CATALOG_EVIDENCE_MISMATCH`
3. `BINDING_NOT_KNOWN_AS_OF`
4. `ENDPOINT_NOT_REACHED_AS_OF`
5. `OBSERVATION_MISSING_AS_OF`
6. `ASSET_MISMATCH`
7. `PRIMARY_VENUE_MISMATCH`
8. `CURRENCY_MISMATCH`
9. `SOURCE_MISMATCH`
10. `CATALOG_MISMATCH`
11. `SESSION_MISMATCH`
12. `OBSERVED_AT_MISMATCH`
13. `PRICE_FIELD_MISMATCH`
14. `ADJUSTMENT_BASIS_MISMATCH`
15. `CORPORATE_ACTION_CONTINUITY_UNAVAILABLE`
16. `OBSERVATION_AMBIGUOUS`

The first five are gate precedence. The next ten inspect known candidates in
the shown order, independent of candidate-list order. Ambiguity is considered
only after all known candidates pass every mismatch gate.

## Canonical policy definition

`EndpointPricePolicyVersion` contains exactly
`OFFICIAL_PRIMARY_VENUE_CLOSE_SPLIT_ADJUSTED_V1`. Its definition is exactly the
following single-line 2259-byte ASCII sequence encoded directly as UTF-8, with
the shown key order, punctuation, case, and values, with no byte-order mark,
surrounding whitespace, or trailing line ending:

```text
{"policyVersion":"OFFICIAL_PRIMARY_VENUE_CLOSE_SPLIT_ADJUSTED_V1","horizonInput":"STRICT_SESSION_CLOSE_RESOLVED_WINDOW","requiredHorizonPolicyVersion":"STRICTLY_AFTER_BASIS_EVENT_SESSION_CLOSE_V1","requiredHorizonPolicyDefinitionHash":"550087efe7ddf2ba31974c89c2740ab79df986eefef48919c32c56a3232f8dc1","catalogWindowIdentity":"catalog.calendarId==horizon.context.calendarId&&catalog.catalogRevision==horizon.context.catalogRevision","catalogPitPredicate":"availableAt<=evaluationAsOf&&capturedAt<=evaluationAsOf","bindingPitPredicate":"availableAt<=evaluationAsOf&&capturedAt<=evaluationAsOf","endpointMaturityPredicate":"endpointSession.closesAt<=evaluationAsOf","candidateScope":"ALL_REQUEST_CANDIDATES","knownCandidatePredicate":"candidate.availableAt<=evaluationAsOf&&candidate.capturedAt<=evaluationAsOf","futureCandidateRule":"INVISIBLE_TO_ALL_OUTPUT_AND_REASONING","noKnownReason":"OBSERVATION_MISSING_AS_OF","priceField":"OFFICIAL_REGULAR_SESSION_CLOSE","venueRule":"PRIMARY_VENUE_EXACT_MATCH","currencyRule":"EXACT_MATCH_NO_FX","bindingCurrencyRole":"REQUIRED_SCORING_AND_TARGET_CURRENCY","sourceRule":"PRICE_SOURCE_ID_AND_REVISION_EXACT_MATCH","observationTimeRule":"observedAt==endpointSession.closesAt","provenanceRule":"CATALOG_BINDING_OBSERVATION_PROVENANCE_PRESERVED_INDEPENDENTLY","selectedObservationIdentity":["observationId","priceSourceId","providerEventId"],"selectedObservationEvidence":["priceSourceRevision","provenanceId"],"deduplication":"ABSENT","candidateCardinality":"EXACTLY_ONE_KNOWN_AS_OF","priceBoundary":"POSITIVE_NUMERIC_38_12","adjustmentBasis":"SPLIT_AND_REVERSE_SPLIT_ADJUSTED_TO_ENDPOINT_SHARE_BASIS_DIVIDEND_UNADJUSTED","continuityRule":"SPLIT_REVERSE_SPLIT_CONTINUOUS_ONLY","gatePrecedence":["CATALOG_NOT_KNOWN_AS_OF","CATALOG_EVIDENCE_MISMATCH","BINDING_NOT_KNOWN_AS_OF","ENDPOINT_NOT_REACHED_AS_OF","OBSERVATION_MISSING_AS_OF"],"knownCandidateMismatchPrecedence":["ASSET_MISMATCH","PRIMARY_VENUE_MISMATCH","CURRENCY_MISMATCH","SOURCE_MISMATCH","CATALOG_MISMATCH","SESSION_MISMATCH","OBSERVED_AT_MISMATCH","PRICE_FIELD_MISMATCH","ADJUSTMENT_BASIS_MISMATCH","CORPORATE_ACTION_CONTINUITY_UNAVAILABLE"],"ambiguityRule":"AFTER_ALL_KNOWN_CANDIDATES_PASS_MISMATCH_GATES","resolvedCardinality":1,"fallbackBehavior":"ABSENT"}
```

Its lowercase SHA-256 is exactly:

```text
37e37aba9302d77366cef4129f77a82b7ccb2f1937bfffc0315ea8d0bc6b1f76
```

`canonicalDefinitionUtf8()` returns a defensive byte-array copy, and every
resolution context echoes this exact hash. A change to any rule, reason,
precedence, identity, or byte requires a new policy version and digest.

## Purity and integration boundary

Production adds exactly these ten files:

- `CatalogPointInTimeEvidence.java`
- `CorporateActionContinuity.java`
- `EndpointPriceAdjustmentBasis.java`
- `EndpointPriceBinding.java`
- `EndpointPriceField.java`
- `EndpointPriceObservation.java`
- `EndpointPricePolicyVersion.java`
- `EndpointPriceRequest.java`
- `EndpointPriceResolution.java`
- `EndpointPriceSelector.java`

The selector consumes supplied evidence only. It adds no schema, canonical
fixture, manifest member, JSON mapping, OpenAPI path, Flyway migration,
database row, controller, repository, application service, scheduler,
provider adapter, network call, API behavior, or web source. It does not
calculate return, target error, target hit, directional win, alpha, MFE, or
MAE, and it does not activate or publish either canonical model-only outcome
methodology.

No API key, account, paid plan, domain, vendor license, or network access is
needed for this disconnected contract. P5 must select a provider and establish
display, storage, derived-data, primary-venue close, corporate-action,
reference-data, and calendar rights before real evidence can enter this
boundary.

## Consequences

- Endpoint-price evidence now has one replayable point-in-time meaning without
  presenting a future, inferred, adjusted-by-dividend, converted, or fallback
  price as observed fact.
- A known duplicate remains ambiguous even when the records are equal; future
  records remain invisible even when they would otherwise cause mismatch or
  ambiguity.
- Public result constructors can enforce only locally decidable evidence
  consistency. Only `EndpointPriceSelector` attests candidate membership,
  point-in-time filtering, exact precedence, and cardinality.
- ADR-015 may consume this exact resolution for target error. Asset-return and
  full-window metric policies, orchestration, fingerprints, persistence,
  aggregation, and publication remain later reviewed work.
