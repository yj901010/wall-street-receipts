# ADR-027 — Point-in-Time Explicit Benchmark Assignment V1

- Status: Accepted
- Date: 2026-08-24

## Context

ADR-026 approves a narrow comparative-return foundation but deliberately adds
no executable assignment policy. The current asset master, fixture universe,
`MarketSnapshot.spx`, ticker, issuer name, and map/treemap groupings cannot
prove which benchmark mapping was known for an original or correction event.
V1 therefore needs a disconnected selector that consumes only explicit
classification and assignment evidence without looking up current state.

ADR-027 selects benchmark assignment only from explicit point-in-time evidence frozen at the outcome basis event.

## Decision

The first executable comparative-return slice adds six production types in
`com.wallstreetreceipts.api.domain.outcome.benchmarkassignment` and one matching
`BenchmarkAssignmentSelectorGoldenTest`.

- `BenchmarkAssetClassificationEvidence` preserves evidence/provider-event
  identity, the exact `OutcomeBasis`, asset/class, primary venue, explicitly
  sourced ISO country, ISO currency, source ID/revision, provenance, an
  explicit effective interval, and PIT timestamps.
- Its `EffectiveInterval` is start-inclusive/end-exclusive. Its sealed end is
  exactly `OpenEnded` or `EndsAtExclusive(value)`; null and an invented terminal
  date cannot mean open-ended.
- `BenchmarkAssignmentEvidence` independently preserves the same basis and
  classification identity plus mapping source/provenance, effective interval,
  benchmark identity/type/currency, and source-attested reference kind.
- `BenchmarkAssignmentRequest` supplies the exact V1 policy, basis, asset,
  microsecond `evaluationAsOf`, and immutable complete classification and
  assignment candidate lists. Evaluation before `basis.eventTime` is invalid.
- Evidence is visible only when both `availableAt` and `capturedAt` are not
  after `evaluationAsOf`. Future exact, invalid, or duplicate records are
  invisible to output, identity, applicability, reason, and cardinality.
- Every visible candidate is request-scoped. Any mismatch poisons selection at
  the first fixed gate; the selector does not filter toward a convenient valid
  subset. Equal duplicates remain ambiguous and candidate order is irrelevant.
- Classification must resolve to exactly one record whose effective interval
  contains `basis.eventTime`. That record remains frozen for the evaluation;
  an original and a correction are independent bases.
- A non-equity is intentional `NON_EQUITY`. An equity outside US and/or USD is
  intentional non-applicability under the exact typed truth table, but only
  when no visible mapping remains. A visible coherent mapping for an
  out-of-scope classification is an unavailable conflict.
- An in-scope asset requires exactly `AssetType.EQUITY`, venue country `US`,
  and currency `USD`. Missing assignment evidence is unavailable rather than
  an automatic mapping.
- A valid mapping must cohere exactly with the selected classification and
  name canonical `asset-spx`, `AssetType.INDEX`, USD, and
  `PROVIDER_PUBLISHED_PRICE_INDEX`. Total-return, non-provider-published, and
  unknown reference claims remain preserved evidence but cannot resolve.
- `BenchmarkAssignmentResolution` is sealed as exactly
  `Resolved(context, classificationEvidence, assignmentEvidence)`,
  `NotApplicable(context, classificationEvidence, reason)`, or
  `Unavailable(context, reason)`. Public resolved/N/A constructors validate
  local consistency; only `BenchmarkAssignmentSelector` attests request
  membership, PIT filtering, all-candidate precedence, and cardinality.

## Exact reason order

The not-applicable reason order and truth table are exactly:

1. `NON_EQUITY`
2. `NON_US_PRIMARY_VENUE`
3. `NON_USD_CURRENCY`
4. `NON_US_PRIMARY_VENUE_AND_NON_USD_CURRENCY`

Non-equity dominates country and currency. For an equity, non-US/USD together
uses the combined reason rather than discarding either known fact.

The unavailable reason order is exactly:

1. `CLASSIFICATION_MISSING_AS_OF`
2. `CLASSIFICATION_BASIS_MISMATCH`
3. `CLASSIFICATION_ASSET_MISMATCH`
4. `CLASSIFICATION_EFFECTIVE_INTERVAL_MISMATCH`
5. `CLASSIFICATION_AMBIGUOUS`
6. `ASSIGNMENT_MISSING_AS_OF`
7. `ASSIGNMENT_BASIS_MISMATCH`
8. `ASSIGNMENT_ASSET_MISMATCH`
9. `ASSIGNMENT_ASSET_TYPE_MISMATCH`
10. `ASSIGNMENT_PRIMARY_VENUE_MISMATCH`
11. `ASSIGNMENT_PRIMARY_VENUE_COUNTRY_MISMATCH`
12. `ASSIGNMENT_CURRENCY_MISMATCH`
13. `ASSIGNMENT_EFFECTIVE_INTERVAL_MISMATCH`
14. `OUT_OF_SCOPE_ASSIGNMENT_CONFLICT`
15. `BENCHMARK_ASSET_ID_MISMATCH`
16. `BENCHMARK_ASSET_TYPE_MISMATCH`
17. `BENCHMARK_CURRENCY_MISMATCH`
18. `BENCHMARK_REFERENCE_KIND_MISMATCH`
19. `ASSIGNMENT_AMBIGUOUS`

The empty-assignment branch yields typed non-applicability for a coherent
out-of-scope classification and `ASSIGNMENT_MISSING_AS_OF` for an in-scope
classification. Common assignment identity/effective-interval gates precede
the out-of-scope conflict. Benchmark-reference gates and ambiguity apply only
after the asset is proven in scope.

## Canonical policy definition

`BenchmarkAssignmentPolicyVersion` contains exactly
`POINT_IN_TIME_EXPLICIT_US_EQUITY_ASSET_SPX_ASSIGNMENT_V1`. Its definition is
the following single-line 4261-byte ASCII sequence encoded directly as UTF-8,
with no byte-order mark, surrounding whitespace, or trailing line ending:

```text
{"policyVersion":"POINT_IN_TIME_EXPLICIT_US_EQUITY_ASSET_SPX_ASSIGNMENT_V1","classificationEvidenceFields":["classificationEvidenceId","providerEventId","basis","assetId","assetType","primaryVenueId","primaryVenueCountryCode","currency","classificationSourceId","classificationSourceRevision","provenanceId","effectiveInterval","availableAt","capturedAt"],"effectiveIntervalFields":["startsAtInclusive","end"],"effectiveIntervalEndVariants":{"OpenEnded":[],"EndsAtExclusive":["value"]},"assignmentEvidenceFields":["assignmentEvidenceId","providerEventId","basis","assetId","assetType","primaryVenueId","primaryVenueCountryCode","currency","assignmentSourceId","assignmentSourceRevision","provenanceId","effectiveInterval","benchmarkAssetId","benchmarkAssetType","benchmarkCurrency","referenceKind","availableAt","capturedAt"],"requestFields":["policyVersion","basis","assetId","evaluationAsOf","classificationCandidates","assignmentCandidates"],"resolutionContextFields":["policyVersion","policyDefinitionHash","basis","assetId","evaluationAsOf"],"resultVariants":{"Resolved":["context","classificationEvidence","assignmentEvidence"],"NotApplicable":["context","classificationEvidence","reason"],"Unavailable":["context","reason"]},"benchmarkReferenceKinds":["PROVIDER_PUBLISHED_PRICE_INDEX","PROVIDER_PUBLISHED_TOTAL_RETURN_INDEX","NON_PROVIDER_PUBLISHED_PRICE_INDEX","UNKNOWN"],"basisModes":["ORIGINAL","CORRECTION"],"cancellationBasisAllowed":false,"requestTemporalRule":"basis.eventTime<=evaluationAsOf","evidenceTemporalRule":"availableAt<=capturedAt","pitPredicate":"availableAt<=evaluationAsOf&&capturedAt<=evaluationAsOf","futureEvidenceRule":"INVISIBLE_TO_ALL_OUTPUT_AND_REASONING","effectiveIntervalPredicate":"startsAtInclusive<=basis.eventTime&&(end==OpenEnded||basis.eventTime<end.value)","effectiveIntervalBoundary":"START_INCLUSIVE_END_EXCLUSIVE","openEndedRepresentation":"EXPLICIT_OPEN_ENDED_VARIANT","venueCountryCodeFormat":"ISO_3166_1_ALPHA_2_UPPERCASE","currencyRepresentation":"ISO_4217_CURRENCY","classificationIdentity":"basis==request.basis&&assetId==request.assetId","classificationCardinality":"EXACTLY_ONE_VISIBLE_VALID_RECORD","inScopePredicate":"assetType==EQUITY&&primaryVenueCountryCode==US&&currency==USD","notApplicableTruthTable":{"nonEquity":"NON_EQUITY","equityNonUsUsd":"NON_US_PRIMARY_VENUE","equityUsNonUsd":"NON_USD_CURRENCY","equityNonUsNonUsd":"NON_US_PRIMARY_VENUE_AND_NON_USD_CURRENCY"},"assignmentCoherence":"basis,assetId,assetType,primaryVenueId,primaryVenueCountryCode,currency==selectedClassification","missingAssignmentTruthTable":{"outOfScope":"NOT_APPLICABLE","inScope":"ASSIGNMENT_MISSING_AS_OF"},"outOfScopeVisibleAssignmentRule":"OUT_OF_SCOPE_ASSIGNMENT_CONFLICT","requiredBenchmarkAssetId":"asset-spx","requiredBenchmarkAssetType":"INDEX","requiredBenchmarkCurrency":"USD","requiredBenchmarkReferenceKind":"PROVIDER_PUBLISHED_PRICE_INDEX","assignmentCardinality":"EXACTLY_ONE_VISIBLE_VALID_RECORD_FOR_IN_SCOPE","knownCandidateSetRule":"ANY_VISIBLE_MISMATCH_FAILS_CLOSED_BEFORE_CARDINALITY","equalDuplicateRule":"AMBIGUOUS_NO_DEDUPLICATION","candidateOrderRule":"ORDER_INDEPENDENT","forbiddenInference":["TICKER","ISSUER_NAME","EXCHANGE_LIKE_TEXT","CURRENT_MASTER_DATA","MARKET_SNAPSHOT_SPX","P2_SP500_UNIVERSE","MAP_OR_TREEMAP","CURRENT_ROW","LATEST_REVISION","NEAREST_INTERVAL","PROVIDER_PREFERENCE","FALLBACK"],"evaluationPrecedence":["CLASSIFICATION_MISSING_AS_OF","CLASSIFICATION_BASIS_MISMATCH","CLASSIFICATION_ASSET_MISMATCH","CLASSIFICATION_EFFECTIVE_INTERVAL_MISMATCH","CLASSIFICATION_AMBIGUOUS","ASSIGNMENT_MISSING_AS_OF_OR_NOT_APPLICABLE","ASSIGNMENT_BASIS_MISMATCH","ASSIGNMENT_ASSET_MISMATCH","ASSIGNMENT_ASSET_TYPE_MISMATCH","ASSIGNMENT_PRIMARY_VENUE_MISMATCH","ASSIGNMENT_PRIMARY_VENUE_COUNTRY_MISMATCH","ASSIGNMENT_CURRENCY_MISMATCH","ASSIGNMENT_EFFECTIVE_INTERVAL_MISMATCH","OUT_OF_SCOPE_ASSIGNMENT_CONFLICT","BENCHMARK_ASSET_ID_MISMATCH","BENCHMARK_ASSET_TYPE_MISMATCH","BENCHMARK_CURRENCY_MISMATCH","BENCHMARK_REFERENCE_KIND_MISMATCH","ASSIGNMENT_AMBIGUOUS","RESOLVE"],"selectedEvidencePreservation":"EXACT_COMPLETE_RECORDS","futureEvidenceOutputRule":"NEVER_ECHOED","lifecycleMapping":"ABSENT","calculatorInvocation":"ABSENT","providerIntegration":"ABSENT","fallbackBehavior":"ABSENT"}
```

Its lowercase SHA-256 is exactly:

```text
7318514c2f50eda16b2d7ef35bc68d00d6a8b18a0f09f77130525fca2f32da69
```

`canonicalDefinitionUtf8()` returns a defensive byte-array copy and every
resolution context echoes this digest. Any changed field, variant, constant,
reason, precedence, inference boundary, or byte requires a new policy version
and digest.

## Purity and integration boundary

Production adds exactly:

- `BenchmarkAssignmentPolicyVersion.java`
- `BenchmarkAssetClassificationEvidence.java`
- `BenchmarkAssignmentEvidence.java`
- `BenchmarkAssignmentRequest.java`
- `BenchmarkAssignmentResolution.java`
- `BenchmarkAssignmentSelector.java`

The selector performs no current-master lookup, provider read, database query,
network request, ticker/name/country inference, endpoint selection, price or
return calculation, status mapping, persistence, aggregation, ranking, or
publication. It adds no schema, fixture, manifest member, OpenAPI path, Flyway
migration, controller, repository, adapter, API behavior, or web source.
Existing DEMO benchmark/sector/alpha fields remain null.

No API key, account, paid plan, provider license, secret, or network access is
needed for this disconnected policy. Before non-DEMO evidence enters it, P5
must select entitled classification, venue-country, currency, and benchmark
mapping sources and establish storage, display, derived-data, and
redistribution rights. Scoped provider secrets may then be introduced only
through approved local, CI, and deployment secret stores, never chat or Git.

## Consequences

- A replay cannot float from basis-event membership to current or horizon-end
  membership.
- Explicit open-ended evidence remains distinct from missing interval data.
- Missing, conflicting, invalid, future, and ambiguous evidence cannot create
  an inferred S&P 500 assignment.
- `Unavailable` intentionally carries only stable context and a typed reason;
  any later persistence or integration must retain the exact request and its
  supplied evidence set beside the result to preserve conflict provenance.
- This source-local result does not establish `OutcomeEvaluationStatus`,
  `dataComplete`, retryability, permanence, cancellation, freshness,
  scheduling, methodology activation, or publication.
- Sector taxonomy/assignment, benchmark reference-level pairs, benchmark
  return, readiness, alpha, persistence, API/UI exposure, and production
  provider integration remain separate reviewed slices.
