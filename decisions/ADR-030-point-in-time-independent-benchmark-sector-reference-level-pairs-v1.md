# ADR-030 — Point-in-Time Independent Benchmark/Sector Reference-Level Pairs V1

- Status: Accepted
- Date: 2026-08-24

## Context

ADR-026 requires benchmark and sector return evidence to use independently
typed provider-published price-index levels over the exact asset
basis-to-endpoint UTC interval. ADR-027 and ADR-029 now resolve the two
assignments independently, but neither assignment proves an actual provider
reference identity, an exact level at either instant, or divisor continuity.
ADR-014 supplies the complete endpoint-price receipt whose context contains the
strict horizon, asset binding, currency, and evaluation cutoff; ADR-016's
asset-share price pair is a different economic contract and cannot stand in
for index evidence.

ADR-030 resolves benchmark and sector reference-level pairs independently from explicit point-in-time provider-published price-index evidence over the exact basis-event-to-asset-endpoint UTC interval.

## Decision

This slice implements two sibling policies without a shared generic,
cross-kind enum, cast, or fallback:

- `POINT_IN_TIME_EXACT_BENCHMARK_PRICE_INDEX_LEVEL_PAIR_V1` in
  `com.wallstreetreceipts.api.domain.outcome.benchmarkreferencepair`;
- `POINT_IN_TIME_EXACT_SECTOR_PRICE_INDEX_LEVEL_PAIR_V1` in
  `com.wallstreetreceipts.api.domain.outcome.sectorreferencepair`.

Each package contains its own reference-index evidence, level observation,
divisor-continuity evidence, policy version, immutable request, sealed
resolution, and selector. Matching test packages contain one source-local
golden each. The two legs share only existing upstream contracts and ordinary
JDK value types.

### Exact upstream topology and branches

Each request preserves one complete corresponding ADR-027 or ADR-029
assignment resolution and one complete ADR-014 `EndpointPriceResolution`.
There is no free-standing `OutcomeBasis`, horizon, endpoint instant, asset,
currency, venue, or evaluation-as-of input.

- Assignment basis and `evaluationAsOf` must always equal the endpoint
  context's nested strict-horizon basis and cutoff.
- Endpoint-anchor validity is derived from preserved context facts, never from
  an arbitrary `EndpointPriceResolution.Unavailable` reason label. In fixed
  order the request verifies catalog PIT visibility, exact catalog
  calendar/revision identity against the horizon, and binding PIT visibility.
- Only after those facts establish a usable anchor must assignment asset ID
  equal binding asset ID. For `Resolved` and `NotApplicable` assignments, the
  selected classification's primary venue and currency must then equal the
  endpoint binding. An assignment-unavailable variant cannot prove those two
  classification values, so no venue or currency is inferred.
- Required upstream policy identities and hashes are ADR-027
  `7318514c2f50eda16b2d7ef35bc68d00d6a8b18a0f09f77130525fca2f32da69`
  or ADR-029
  `52d9f705a3a8a965a6fca79d36bd94ed8836642f1a2c4e5f29a878d0a267311c`,
  ADR-014
  `37e37aba9302d77366cef4129f77a82b7ccb2f1937bfffc0315ea8d0bc6b1f76`,
  and nested strict-horizon
  `550087efe7ddf2ba31974c89c2740ab79df986eefef48919c32c56a3232f8dc1`.

Both sealed results have exactly five variants:

1. `Resolved(context, referenceIndexEvidence, basisLevelObservation,
   endpointLevelObservation, divisorContinuityEvidence)`;
2. `NotApplicable(context)`, preserving the exact upstream intentional
   non-applicability;
3. `AssignmentUnavailable(context)`, preserving the exact upstream
   assignment reason and receipt;
4. `EndpointAnchorUnavailable(context, reason)`, permitted only for a resolved
   assignment plus one independently derived source-local
   `EndpointAnchorUnavailableReason`:
   `CATALOG_NOT_KNOWN_AS_OF`, `CATALOG_EVIDENCE_MISMATCH`, or
   `BINDING_NOT_KNOWN_AS_OF`;
5. `EvidenceUnavailable(context, reason)`, for one exact reference-side
   reason after assignment and anchor precedence.

Assignment N/A and assignment unavailability precede endpoint-anchor
unavailability. The three anchor reasons are derived from context facts in the
order above and are not copied from the nested endpoint result. Consequently,
all sixteen possible ADR-014 unavailable labels are ignored when the supplied
context has a mature, coherent catalog/horizon/binding anchor. Once that anchor
is sound and the exact endpoint instant is reached, reference evidence is
selected independently. Before that instant, and only then,
`EvidenceUnavailable(ENDPOINT_NOT_REACHED_AS_OF)` applies.

### Exact binding and interval

The interval is exactly
`[endpoint.context.horizon.basis.eventTime,
endpoint.context.horizon.endpointSession.closesAt]`. A reference binding uses
a start-inclusive/end-exclusive interval that contains both instants; its end
is explicitly `OpenEnded` or `EndsAtExclusive`. Prior close, nearest
timestamp, interpolation, a shifted reference session, a current row, latest
revision selection, or fallback is absent.

Benchmark binding evidence links the exact selected ADR-027 assignment
evidence ID and provider-event ID, canonical benchmark asset identity/type,
and a separately supplied provider-published reference identity. Both
benchmark level observations and divisor-continuity evidence repeat and must
match that exact canonical benchmark asset ID/type. Sector binding evidence
instead links the exact selected ADR-029 mapping evidence ID and provider-event
ID, taxonomy ID/version/hash, canonical WSR node ID, and an explicit reference
asset ID whose type is exactly `INDEX`; both sector levels and continuity
evidence repeat and must match that exact reference asset ID/type.

The paths are deliberately different:

`issuer -> provider classification node -> WSR canonical economic-activity node`
is the ADR-028/029 classification path. It does not establish an index.

`resolved WSR node -> explicit provider-published sector price-index binding
-> exact two levels -> divisor-continuity attestation`
is this decision's sector-reference path. A WSR node has no index publisher,
reference index ID, level, currency, calculation venue, calendar, or divisor
meaning. The membership provider, index publisher, and level redistributor are
not assumed to be the same entity.

### Exact reference evidence

Every candidate is known only when both `availableAt` and `capturedAt` are
not after the shared `evaluationAsOf`. Future candidates are invisible to
output, mismatch reasoning, and cardinality. Any visible mismatch fails closed
at the fixed reason gate before cardinality; equal duplicates remain ambiguous
without deduplication and candidate order is irrelevant.

- The binding preserves provider event/evidence identity, exact provider and
  index identity plus definition revision, source label as non-key evidence,
  price-index kind, ISO currency, calculation venue, calendar ID/revision,
  calendar source ID/revision, level and continuity source IDs/revisions,
  binding source/revision, provenance, effective interval, and PIT timestamps.
- Provider/index identity is case-sensitive, unnormalized Unicode code-point
  equality. Labels are preserved evidence only and cannot match or repair an
  identity.
- The required reference kind is exactly
  `PROVIDER_PUBLISHED_PRICE_INDEX`. Total-return indices,
  non-provider-published indices, ETFs, current constituent baskets,
  market-cap proxies, provider-return fields, and unknown references cannot
  resolve.
- Basis and endpoint observations must link the exact binding evidence and
  provider event; match its benchmark or sector reference asset,
  provider/index/revision/kind, currency, calculation venue,
  calendar ID/revision/source ID/source revision, and level source; use
  `PROVIDER_PUBLISHED_INDEX_LEVEL`; and occur exactly at basis event time and
  asset endpoint UTC respectively.
- Each level is positive and exactly representable as `NUMERIC(38,12)`
  without rounding. Reference currency equals the asset endpoint binding
  currency; V1 performs no FX.
- Divisor evidence links the exact binding and both observation/provider-event
  identities, matches its benchmark or sector reference asset plus the
  reference/calendar/calendar-source/continuity-source identity, covers the
  exact two UTC endpoints, and carries
  `PROVIDER_PUBLISHED_INDEX_DIVISOR_CONTINUITY_ATTESTED`. Discontinuity,
  missing attestation, unknown continuity, missing evidence, mismatch, or
  duplicate evidence remains unavailable.

Index divisor continuity is not an asset split/reverse-split share-basis
claim. Neither leg imports, reuses, relabels, or casts
`AssetReturnPricePairResolution`, `CorporateActionContinuity`, or
`EndpointPriceAdjustmentBasis`.

## Exact unavailable-reason order

The benchmark leg has exactly 53 local unavailable reasons, in this order:

```text
ENDPOINT_NOT_REACHED_AS_OF
REFERENCE_INDEX_MISSING_AS_OF
REFERENCE_ASSIGNMENT_EVIDENCE_LINK_MISMATCH
REFERENCE_BENCHMARK_ASSET_ID_MISMATCH
REFERENCE_BENCHMARK_ASSET_TYPE_MISMATCH
REFERENCE_CURRENCY_MISMATCH
REFERENCE_KIND_MISMATCH
REFERENCE_EFFECTIVE_INTERVAL_MISMATCH
REFERENCE_INDEX_AMBIGUOUS
BASIS_LEVEL_MISSING_AS_OF
BASIS_REFERENCE_INDEX_EVIDENCE_LINK_MISMATCH
BASIS_BENCHMARK_ASSET_MISMATCH
BASIS_REFERENCE_PROVIDER_MISMATCH
BASIS_REFERENCE_INDEX_MISMATCH
BASIS_REFERENCE_INDEX_DEFINITION_REVISION_MISMATCH
BASIS_REFERENCE_KIND_MISMATCH
BASIS_CURRENCY_MISMATCH
BASIS_CALCULATION_VENUE_MISMATCH
BASIS_CALENDAR_MISMATCH
BASIS_LEVEL_SOURCE_MISMATCH
BASIS_OBSERVED_AT_MISMATCH
BASIS_LEVEL_FIELD_MISMATCH
BASIS_LEVEL_AMBIGUOUS
ENDPOINT_LEVEL_MISSING_AS_OF
ENDPOINT_REFERENCE_INDEX_EVIDENCE_LINK_MISMATCH
ENDPOINT_BENCHMARK_ASSET_MISMATCH
ENDPOINT_REFERENCE_PROVIDER_MISMATCH
ENDPOINT_REFERENCE_INDEX_MISMATCH
ENDPOINT_REFERENCE_INDEX_DEFINITION_REVISION_MISMATCH
ENDPOINT_REFERENCE_KIND_MISMATCH
ENDPOINT_CURRENCY_MISMATCH
ENDPOINT_CALCULATION_VENUE_MISMATCH
ENDPOINT_CALENDAR_MISMATCH
ENDPOINT_LEVEL_SOURCE_MISMATCH
ENDPOINT_OBSERVED_AT_MISMATCH
ENDPOINT_LEVEL_FIELD_MISMATCH
ENDPOINT_LEVEL_AMBIGUOUS
DIVISOR_CONTINUITY_EVIDENCE_MISSING_AS_OF
DIVISOR_REFERENCE_INDEX_EVIDENCE_LINK_MISMATCH
DIVISOR_BENCHMARK_ASSET_MISMATCH
DIVISOR_REFERENCE_PROVIDER_MISMATCH
DIVISOR_REFERENCE_INDEX_MISMATCH
DIVISOR_REFERENCE_INDEX_DEFINITION_REVISION_MISMATCH
DIVISOR_REFERENCE_KIND_MISMATCH
DIVISOR_CURRENCY_MISMATCH
DIVISOR_CALCULATION_VENUE_MISMATCH
DIVISOR_CALENDAR_MISMATCH
DIVISOR_CONTINUITY_SOURCE_MISMATCH
DIVISOR_BASIS_OBSERVATION_LINK_MISMATCH
DIVISOR_ENDPOINT_OBSERVATION_LINK_MISMATCH
DIVISOR_COVERAGE_MISMATCH
DIVISOR_CONTINUITY_UNAVAILABLE
DIVISOR_CONTINUITY_EVIDENCE_AMBIGUOUS
```

The sector leg has exactly 56 local unavailable reasons, in this order:

```text
ENDPOINT_NOT_REACHED_AS_OF
REFERENCE_INDEX_MISSING_AS_OF
REFERENCE_MAPPING_EVIDENCE_LINK_MISMATCH
REFERENCE_TAXONOMY_ID_MISMATCH
REFERENCE_TAXONOMY_VERSION_MISMATCH
REFERENCE_TAXONOMY_DEFINITION_HASH_MISMATCH
REFERENCE_CANONICAL_NODE_ID_MISMATCH
REFERENCE_ASSET_TYPE_MISMATCH
REFERENCE_CURRENCY_MISMATCH
REFERENCE_KIND_MISMATCH
REFERENCE_EFFECTIVE_INTERVAL_MISMATCH
REFERENCE_INDEX_AMBIGUOUS
BASIS_LEVEL_MISSING_AS_OF
BASIS_REFERENCE_INDEX_EVIDENCE_LINK_MISMATCH
BASIS_REFERENCE_ASSET_MISMATCH
BASIS_REFERENCE_PROVIDER_MISMATCH
BASIS_REFERENCE_INDEX_MISMATCH
BASIS_REFERENCE_INDEX_DEFINITION_REVISION_MISMATCH
BASIS_REFERENCE_KIND_MISMATCH
BASIS_CURRENCY_MISMATCH
BASIS_CALCULATION_VENUE_MISMATCH
BASIS_CALENDAR_MISMATCH
BASIS_LEVEL_SOURCE_MISMATCH
BASIS_OBSERVED_AT_MISMATCH
BASIS_LEVEL_FIELD_MISMATCH
BASIS_LEVEL_AMBIGUOUS
ENDPOINT_LEVEL_MISSING_AS_OF
ENDPOINT_REFERENCE_INDEX_EVIDENCE_LINK_MISMATCH
ENDPOINT_REFERENCE_ASSET_MISMATCH
ENDPOINT_REFERENCE_PROVIDER_MISMATCH
ENDPOINT_REFERENCE_INDEX_MISMATCH
ENDPOINT_REFERENCE_INDEX_DEFINITION_REVISION_MISMATCH
ENDPOINT_REFERENCE_KIND_MISMATCH
ENDPOINT_CURRENCY_MISMATCH
ENDPOINT_CALCULATION_VENUE_MISMATCH
ENDPOINT_CALENDAR_MISMATCH
ENDPOINT_LEVEL_SOURCE_MISMATCH
ENDPOINT_OBSERVED_AT_MISMATCH
ENDPOINT_LEVEL_FIELD_MISMATCH
ENDPOINT_LEVEL_AMBIGUOUS
DIVISOR_CONTINUITY_EVIDENCE_MISSING_AS_OF
DIVISOR_REFERENCE_INDEX_EVIDENCE_LINK_MISMATCH
DIVISOR_REFERENCE_ASSET_MISMATCH
DIVISOR_REFERENCE_PROVIDER_MISMATCH
DIVISOR_REFERENCE_INDEX_MISMATCH
DIVISOR_REFERENCE_INDEX_DEFINITION_REVISION_MISMATCH
DIVISOR_REFERENCE_KIND_MISMATCH
DIVISOR_CURRENCY_MISMATCH
DIVISOR_CALCULATION_VENUE_MISMATCH
DIVISOR_CALENDAR_MISMATCH
DIVISOR_CONTINUITY_SOURCE_MISMATCH
DIVISOR_BASIS_OBSERVATION_LINK_MISMATCH
DIVISOR_ENDPOINT_OBSERVATION_LINK_MISMATCH
DIVISOR_COVERAGE_MISMATCH
DIVISOR_CONTINUITY_UNAVAILABLE
DIVISOR_CONTINUITY_EVIDENCE_AMBIGUOUS
```

## Canonical policy definitions

### Benchmark

`BenchmarkReferenceLevelPairPolicyVersion` contains only
`POINT_IN_TIME_EXACT_BENCHMARK_PRICE_INDEX_LEVEL_PAIR_V1`. Its canonical
definition is the exact following single-line 9342-byte ASCII/UTF-8 sequence,
with no BOM, surrounding whitespace, or trailing newline:

```text
{"policyVersion":"POINT_IN_TIME_EXACT_BENCHMARK_PRICE_INDEX_LEVEL_PAIR_V1","requiredAssignmentPolicyVersion":"POINT_IN_TIME_EXPLICIT_US_EQUITY_ASSET_SPX_ASSIGNMENT_V1","requiredAssignmentPolicyDefinitionHash":"7318514c2f50eda16b2d7ef35bc68d00d6a8b18a0f09f77130525fca2f32da69","requiredEndpointPricePolicyVersion":"OFFICIAL_PRIMARY_VENUE_CLOSE_SPLIT_ADJUSTED_V1","requiredEndpointPricePolicyDefinitionHash":"37e37aba9302d77366cef4129f77a82b7ccb2f1937bfffc0315ea8d0bc6b1f76","requiredNestedHorizonPolicyVersion":"STRICTLY_AFTER_BASIS_EVENT_SESSION_CLOSE_V1","requiredNestedHorizonPolicyDefinitionHash":"550087efe7ddf2ba31974c89c2740ab79df986eefef48919c32c56a3232f8dc1","referenceIndexEvidenceFields":["referenceIndexEvidenceId","providerEventId","assignmentEvidenceId","assignmentProviderEventId","benchmarkAssetId","benchmarkAssetType","referenceProviderId","referenceIndexId","referenceIndexLabel","referenceIndexDefinitionRevision","referenceKind","currency","calculationVenueId","calendarId","calendarRevision","calendarSourceId","calendarSourceRevision","levelSourceId","levelSourceRevision","continuitySourceId","continuitySourceRevision","bindingSourceId","bindingSourceRevision","provenanceId","effectiveInterval","availableAt","capturedAt"],"effectiveIntervalFields":["startsAtInclusive","end"],"effectiveIntervalEndVariants":{"OpenEnded":[],"EndsAtExclusive":["value"]},"referenceLevelObservationFields":["observationId","providerEventId","referenceIndexEvidenceId","referenceIndexProviderEventId","benchmarkAssetId","benchmarkAssetType","referenceProviderId","referenceIndexId","referenceIndexDefinitionRevision","referenceKind","currency","calculationVenueId","calendarId","calendarRevision","calendarSourceId","calendarSourceRevision","levelSourceId","levelSourceRevision","provenanceId","levelField","observedAt","availableAt","capturedAt","level"],"divisorContinuityEvidenceFields":["continuityEvidenceId","providerEventId","referenceIndexEvidenceId","referenceIndexProviderEventId","benchmarkAssetId","benchmarkAssetType","referenceProviderId","referenceIndexId","referenceIndexDefinitionRevision","referenceKind","currency","calculationVenueId","calendarId","calendarRevision","calendarSourceId","calendarSourceRevision","continuitySourceId","continuitySourceRevision","provenanceId","basisObservationId","basisProviderEventId","endpointObservationId","endpointProviderEventId","coverageStartsAt","coverageEndsAt","divisorContinuity","availableAt","capturedAt"],"requestFields":["policyVersion","assignmentResolution","endpointPriceResolution","referenceIndexCandidates","basisLevelCandidates","endpointLevelCandidates","divisorContinuityCandidates"],"resolutionContextFields":["policyVersion","policyDefinitionHash","assignmentResolution","endpointPriceResolution"],"resultVariants":{"Resolved":["context","referenceIndexEvidence","basisLevelObservation","endpointLevelObservation","divisorContinuityEvidence"],"NotApplicable":["context"],"AssignmentUnavailable":["context"],"EndpointAnchorUnavailable":["context","reason"],"EvidenceUnavailable":["context","reason"]},"referenceIndexKinds":["PROVIDER_PUBLISHED_PRICE_INDEX","PROVIDER_PUBLISHED_TOTAL_RETURN_INDEX","NON_PROVIDER_PUBLISHED_PRICE_INDEX","EXCHANGE_TRADED_FUND","CURRENT_CONSTITUENT_BASKET","MARKET_CAP_PROXY","PROVIDER_RETURN_FIELD","UNKNOWN"],"requiredReferenceIndexKind":"PROVIDER_PUBLISHED_PRICE_INDEX","referenceLevelFields":["PROVIDER_PUBLISHED_INDEX_LEVEL","PROVIDER_PUBLISHED_RETURN","EXCHANGE_TRADED_FUND_MARKET_PRICE","EXCHANGE_TRADED_FUND_NAV","DERIVED_PROXY_LEVEL","UNKNOWN"],"requiredReferenceLevelField":"PROVIDER_PUBLISHED_INDEX_LEVEL","divisorContinuityStates":["PROVIDER_PUBLISHED_INDEX_DIVISOR_CONTINUITY_ATTESTED","DIVISOR_DISCONTINUITY","NOT_ATTESTED","UNKNOWN"],"requiredDivisorContinuity":"PROVIDER_PUBLISHED_INDEX_DIVISOR_CONTINUITY_ATTESTED","endpointAnchorUnavailableReasons":["CATALOG_NOT_KNOWN_AS_OF","CATALOG_EVIDENCE_MISMATCH","BINDING_NOT_KNOWN_AS_OF"],"endpointAnchorDerivation":"CONTEXT_FACTS_NOT_UPSTREAM_REASON_LABEL","anchorBranchTopology":"catalogPITThenCatalogHorizonIdentityThenBindingPIT;ResolvedAndEvidenceUnavailableRejectAnchorFailures;EndpointAnchorUnavailableRequiresExactDerivedReason","endpointNotReachedReasonTopology":"ENDPOINT_NOT_REACHED_AS_OF_IFF_assetEndpointUtc>evaluationAsOf","endpointObservationUnavailableReasonsRole":"IGNORED_REFERENCE_SELECTION_INDEPENDENT","inputAnchor":"FULL_ASSIGNMENT_RESOLUTION_AND_FULL_ENDPOINT_PRICE_RESOLUTION","directOutcomeBasisOrHorizonInput":false,"upstreamTopology":"basisAndEvaluationAsOfAlwaysEqual;assetIdAndResolvedOrNotApplicableClassificationVenueCurrencyEqualEndpointBindingOnlyAfterUsableAnchor","assignmentUnavailableTopologyLimitation":"VENUE_AND_CURRENCY_NOT_PROVABLE_FROM_UPSTREAM_VARIANT_NO_INFERENCE","referenceInterval":"[endpoint.context.horizon.basis.eventTime,endpoint.context.horizon.endpointSession.closesAt]","referenceTimestampSubstitution":"ABSENT_NO_PRIOR_CLOSE_NEAREST_INTERPOLATION_OR_SHIFTED_SESSION","sameCurrencyRule":"reference.currency==endpoint.context.binding.currency","fxConversion":"ABSENT","referenceSpecificIdentity":"calculationVenueId,calendarId,calendarRevision,calendarSourceId,calendarSourceRevision,levelSourceId,levelSourceRevision,continuitySourceId,continuitySourceRevision","bindingChain":"referenceIndexEvidence.assignmentEvidenceId,assignmentProviderEventId==selectedAssignmentEvidence.identity","levelChain":"level.referenceIndexEvidenceId,referenceIndexProviderEventId,benchmarkAssetId,benchmarkAssetType==selectedReferenceIndexEvidence.identityAndCanonicalAsset","continuityChain":"continuity.referenceIndexEvidenceId,referenceIndexProviderEventId,benchmarkAssetId,benchmarkAssetType==selectedReferenceIndexEvidence.identityAndCanonicalAsset&&basisAndEndpointObservationLinksExact","bindingIntervalPredicate":"contains(basis.eventTime)&&contains(assetEndpointUtc)","effectiveIntervalBoundary":"START_INCLUSIVE_END_EXCLUSIVE","pitPredicate":"availableAt<=evaluationAsOf&&capturedAt<=evaluationAsOf","futureEvidenceRule":"INVISIBLE_TO_ALL_OUTPUT_REASONING_AND_CARDINALITY","candidateSetRule":"ANY_VISIBLE_MISMATCH_FAILS_CLOSED_BEFORE_CARDINALITY","candidateOrderRule":"ORDER_INDEPENDENT","equalDuplicateRule":"AMBIGUOUS_NO_DEDUPLICATION","providerIdentityFields":["referenceProviderId","referenceIndexId","referenceIndexDefinitionRevision"],"providerIdentityComparison":"EXACT_CASE_SENSITIVE_UNNORMALIZED_UNICODE_CODE_POINT_EQUALITY","providerIdentityValidation":"NON_NULL_NON_EMPTY_NO_STRIP_NORMALIZATION_OR_CASE_FOLD","providerLabelRole":"PRESERVED_EVIDENCE_ONLY_NOT_IDENTITY_OR_MATCH_KEY","localCanonicalTextValidation":"NONBLANK_TRIMMED","instantPrecision":"PERSISTENT_MICROSECOND_SAFE","bindingTimeline":"availableAt<=capturedAt","levelTimeline":"observedAt<=availableAt<=capturedAt","continuityTimeline":"coverageStartsAt<=coverageEndsAt<=availableAt<=capturedAt","levelNumericContract":"POSITIVE_NUMERIC_38_12_EXACT_NO_ROUNDING","unavailableReasons":["ENDPOINT_NOT_REACHED_AS_OF","REFERENCE_INDEX_MISSING_AS_OF","REFERENCE_ASSIGNMENT_EVIDENCE_LINK_MISMATCH","REFERENCE_BENCHMARK_ASSET_ID_MISMATCH","REFERENCE_BENCHMARK_ASSET_TYPE_MISMATCH","REFERENCE_CURRENCY_MISMATCH","REFERENCE_KIND_MISMATCH","REFERENCE_EFFECTIVE_INTERVAL_MISMATCH","REFERENCE_INDEX_AMBIGUOUS","BASIS_LEVEL_MISSING_AS_OF","BASIS_REFERENCE_INDEX_EVIDENCE_LINK_MISMATCH","BASIS_BENCHMARK_ASSET_MISMATCH","BASIS_REFERENCE_PROVIDER_MISMATCH","BASIS_REFERENCE_INDEX_MISMATCH","BASIS_REFERENCE_INDEX_DEFINITION_REVISION_MISMATCH","BASIS_REFERENCE_KIND_MISMATCH","BASIS_CURRENCY_MISMATCH","BASIS_CALCULATION_VENUE_MISMATCH","BASIS_CALENDAR_MISMATCH","BASIS_LEVEL_SOURCE_MISMATCH","BASIS_OBSERVED_AT_MISMATCH","BASIS_LEVEL_FIELD_MISMATCH","BASIS_LEVEL_AMBIGUOUS","ENDPOINT_LEVEL_MISSING_AS_OF","ENDPOINT_REFERENCE_INDEX_EVIDENCE_LINK_MISMATCH","ENDPOINT_BENCHMARK_ASSET_MISMATCH","ENDPOINT_REFERENCE_PROVIDER_MISMATCH","ENDPOINT_REFERENCE_INDEX_MISMATCH","ENDPOINT_REFERENCE_INDEX_DEFINITION_REVISION_MISMATCH","ENDPOINT_REFERENCE_KIND_MISMATCH","ENDPOINT_CURRENCY_MISMATCH","ENDPOINT_CALCULATION_VENUE_MISMATCH","ENDPOINT_CALENDAR_MISMATCH","ENDPOINT_LEVEL_SOURCE_MISMATCH","ENDPOINT_OBSERVED_AT_MISMATCH","ENDPOINT_LEVEL_FIELD_MISMATCH","ENDPOINT_LEVEL_AMBIGUOUS","DIVISOR_CONTINUITY_EVIDENCE_MISSING_AS_OF","DIVISOR_REFERENCE_INDEX_EVIDENCE_LINK_MISMATCH","DIVISOR_BENCHMARK_ASSET_MISMATCH","DIVISOR_REFERENCE_PROVIDER_MISMATCH","DIVISOR_REFERENCE_INDEX_MISMATCH","DIVISOR_REFERENCE_INDEX_DEFINITION_REVISION_MISMATCH","DIVISOR_REFERENCE_KIND_MISMATCH","DIVISOR_CURRENCY_MISMATCH","DIVISOR_CALCULATION_VENUE_MISMATCH","DIVISOR_CALENDAR_MISMATCH","DIVISOR_CONTINUITY_SOURCE_MISMATCH","DIVISOR_BASIS_OBSERVATION_LINK_MISMATCH","DIVISOR_ENDPOINT_OBSERVATION_LINK_MISMATCH","DIVISOR_COVERAGE_MISMATCH","DIVISOR_CONTINUITY_UNAVAILABLE","DIVISOR_CONTINUITY_EVIDENCE_AMBIGUOUS"],"evaluationPrecedence":["ASSIGNMENT_NOT_APPLICABLE","ASSIGNMENT_UNAVAILABLE","ENDPOINT_ANCHOR_UNAVAILABLE","LOCAL_UNAVAILABLE_REASONS_IN_DECLARED_ORDER","RESOLVE"],"selectedEvidencePreservation":"EXACT_COMPLETE_RECORDS_AND_UPSTREAM_RESOLUTIONS","assetReturnPricePairReuse":"FORBIDDEN","corporateActionTypesReuse":"FORBIDDEN","referenceReturnCalculation":"ABSENT","lifecycleMapping":"ABSENT","providerIntegration":"ABSENT","runtimePublication":"ABSENT","fallbackBehavior":"ABSENT"}
```

Its lowercase SHA-256 is exactly
`2394b535c1061d32c647504a303b6f1e4ec2fe88e6017d9ff335d12087a5f73d`.

### Sector

`SectorReferenceLevelPairPolicyVersion` contains only
`POINT_IN_TIME_EXACT_SECTOR_PRICE_INDEX_LEVEL_PAIR_V1`. Its canonical
definition is the exact following single-line 9806-byte ASCII/UTF-8 sequence,
with no BOM, surrounding whitespace, or trailing newline:

```text
{"policyVersion":"POINT_IN_TIME_EXACT_SECTOR_PRICE_INDEX_LEVEL_PAIR_V1","requiredAssignmentPolicyVersion":"POINT_IN_TIME_EXPLICIT_WSR_ECONOMIC_ACTIVITY_SECTOR_ASSIGNMENT_V1","requiredAssignmentPolicyDefinitionHash":"52d9f705a3a8a965a6fca79d36bd94ed8836642f1a2c4e5f29a878d0a267311c","requiredEndpointPricePolicyVersion":"OFFICIAL_PRIMARY_VENUE_CLOSE_SPLIT_ADJUSTED_V1","requiredEndpointPricePolicyDefinitionHash":"37e37aba9302d77366cef4129f77a82b7ccb2f1937bfffc0315ea8d0bc6b1f76","requiredNestedHorizonPolicyVersion":"STRICTLY_AFTER_BASIS_EVENT_SESSION_CLOSE_V1","requiredNestedHorizonPolicyDefinitionHash":"550087efe7ddf2ba31974c89c2740ab79df986eefef48919c32c56a3232f8dc1","requiredTaxonomyId":"wsr-economic-activity","requiredTaxonomyVersion":"1.0.0","requiredTaxonomyDefinitionHash":"820ce3ea264d67312fe4f2efe346631a81d74248e9a7f041793d65d8ef0d62ae","referenceIndexEvidenceFields":["referenceIndexEvidenceId","providerEventId","mappingEvidenceId","mappingProviderEventId","taxonomyId","taxonomyVersion","taxonomyDefinitionHash","canonicalNodeId","referenceAssetId","referenceAssetType","referenceProviderId","referenceIndexId","referenceIndexLabel","referenceIndexDefinitionRevision","referenceKind","currency","calculationVenueId","calendarId","calendarRevision","calendarSourceId","calendarSourceRevision","levelSourceId","levelSourceRevision","continuitySourceId","continuitySourceRevision","bindingSourceId","bindingSourceRevision","provenanceId","effectiveInterval","availableAt","capturedAt"],"effectiveIntervalFields":["startsAtInclusive","end"],"effectiveIntervalEndVariants":{"OpenEnded":[],"EndsAtExclusive":["value"]},"referenceLevelObservationFields":["observationId","providerEventId","referenceIndexEvidenceId","referenceIndexProviderEventId","referenceAssetId","referenceAssetType","referenceProviderId","referenceIndexId","referenceIndexDefinitionRevision","referenceKind","currency","calculationVenueId","calendarId","calendarRevision","calendarSourceId","calendarSourceRevision","levelSourceId","levelSourceRevision","provenanceId","levelField","observedAt","availableAt","capturedAt","level"],"divisorContinuityEvidenceFields":["continuityEvidenceId","providerEventId","referenceIndexEvidenceId","referenceIndexProviderEventId","referenceAssetId","referenceAssetType","referenceProviderId","referenceIndexId","referenceIndexDefinitionRevision","referenceKind","currency","calculationVenueId","calendarId","calendarRevision","calendarSourceId","calendarSourceRevision","continuitySourceId","continuitySourceRevision","provenanceId","basisObservationId","basisProviderEventId","endpointObservationId","endpointProviderEventId","coverageStartsAt","coverageEndsAt","divisorContinuity","availableAt","capturedAt"],"requestFields":["policyVersion","assignmentResolution","endpointPriceResolution","referenceIndexCandidates","basisLevelCandidates","endpointLevelCandidates","divisorContinuityCandidates"],"resolutionContextFields":["policyVersion","policyDefinitionHash","assignmentResolution","endpointPriceResolution"],"resultVariants":{"Resolved":["context","referenceIndexEvidence","basisLevelObservation","endpointLevelObservation","divisorContinuityEvidence"],"NotApplicable":["context"],"AssignmentUnavailable":["context"],"EndpointAnchorUnavailable":["context","reason"],"EvidenceUnavailable":["context","reason"]},"referenceIndexKinds":["PROVIDER_PUBLISHED_PRICE_INDEX","PROVIDER_PUBLISHED_TOTAL_RETURN_INDEX","NON_PROVIDER_PUBLISHED_PRICE_INDEX","EXCHANGE_TRADED_FUND","CURRENT_CONSTITUENT_BASKET","MARKET_CAP_PROXY","PROVIDER_RETURN_FIELD","UNKNOWN"],"requiredReferenceIndexKind":"PROVIDER_PUBLISHED_PRICE_INDEX","requiredReferenceAssetType":"INDEX","referenceLevelFields":["PROVIDER_PUBLISHED_INDEX_LEVEL","PROVIDER_PUBLISHED_RETURN","EXCHANGE_TRADED_FUND_MARKET_PRICE","EXCHANGE_TRADED_FUND_NAV","DERIVED_PROXY_LEVEL","UNKNOWN"],"requiredReferenceLevelField":"PROVIDER_PUBLISHED_INDEX_LEVEL","divisorContinuityStates":["PROVIDER_PUBLISHED_INDEX_DIVISOR_CONTINUITY_ATTESTED","DIVISOR_DISCONTINUITY","NOT_ATTESTED","UNKNOWN"],"requiredDivisorContinuity":"PROVIDER_PUBLISHED_INDEX_DIVISOR_CONTINUITY_ATTESTED","endpointAnchorUnavailableReasons":["CATALOG_NOT_KNOWN_AS_OF","CATALOG_EVIDENCE_MISMATCH","BINDING_NOT_KNOWN_AS_OF"],"endpointAnchorDerivation":"CONTEXT_FACTS_NOT_UPSTREAM_REASON_LABEL","anchorBranchTopology":"catalogPITThenCatalogHorizonIdentityThenBindingPIT;ResolvedAndEvidenceUnavailableRejectAnchorFailures;EndpointAnchorUnavailableRequiresExactDerivedReason","endpointNotReachedReasonTopology":"ENDPOINT_NOT_REACHED_AS_OF_IFF_assetEndpointUtc>evaluationAsOf","endpointObservationUnavailableReasonsRole":"IGNORED_REFERENCE_SELECTION_INDEPENDENT","inputAnchor":"FULL_ASSIGNMENT_RESOLUTION_AND_FULL_ENDPOINT_PRICE_RESOLUTION","directOutcomeBasisOrHorizonInput":false,"upstreamTopology":"basisAndEvaluationAsOfAlwaysEqual;assetIdAndResolvedOrNotApplicableClassificationVenueCurrencyEqualEndpointBindingOnlyAfterUsableAnchor","assignmentUnavailableTopologyLimitation":"VENUE_AND_CURRENCY_NOT_PROVABLE_FROM_UPSTREAM_VARIANT_NO_INFERENCE","referenceInterval":"[endpoint.context.horizon.basis.eventTime,endpoint.context.horizon.endpointSession.closesAt]","referenceTimestampSubstitution":"ABSENT_NO_PRIOR_CLOSE_NEAREST_INTERPOLATION_OR_SHIFTED_SESSION","sameCurrencyRule":"reference.currency==endpoint.context.binding.currency","fxConversion":"ABSENT","referenceSpecificIdentity":"referenceAssetId,referenceAssetType,calculationVenueId,calendarId,calendarRevision,calendarSourceId,calendarSourceRevision,levelSourceId,levelSourceRevision,continuitySourceId,continuitySourceRevision","bindingChain":"referenceIndexEvidence.mappingEvidenceId,mappingProviderEventId==selectedMappingEvidence.identity","levelChain":"level.referenceIndexEvidenceId,referenceIndexProviderEventId,referenceAssetId,referenceAssetType==selectedReferenceIndexEvidence.identityAndCanonicalAsset","continuityChain":"continuity.referenceIndexEvidenceId,referenceIndexProviderEventId,referenceAssetId,referenceAssetType==selectedReferenceIndexEvidence.identityAndCanonicalAsset&&basisAndEndpointObservationLinksExact","bindingIntervalPredicate":"contains(basis.eventTime)&&contains(assetEndpointUtc)","effectiveIntervalBoundary":"START_INCLUSIVE_END_EXCLUSIVE","pitPredicate":"availableAt<=evaluationAsOf&&capturedAt<=evaluationAsOf","futureEvidenceRule":"INVISIBLE_TO_ALL_OUTPUT_REASONING_AND_CARDINALITY","candidateSetRule":"ANY_VISIBLE_MISMATCH_FAILS_CLOSED_BEFORE_CARDINALITY","candidateOrderRule":"ORDER_INDEPENDENT","equalDuplicateRule":"AMBIGUOUS_NO_DEDUPLICATION","providerIdentityFields":["referenceProviderId","referenceIndexId","referenceIndexDefinitionRevision"],"providerIdentityComparison":"EXACT_CASE_SENSITIVE_UNNORMALIZED_UNICODE_CODE_POINT_EQUALITY","providerIdentityValidation":"NON_NULL_NON_EMPTY_NO_STRIP_NORMALIZATION_OR_CASE_FOLD","providerLabelRole":"PRESERVED_EVIDENCE_ONLY_NOT_IDENTITY_OR_MATCH_KEY","localCanonicalTextValidation":"NONBLANK_TRIMMED","hashValidation":"LOWERCASE_SHA_256_HEX_64","instantPrecision":"PERSISTENT_MICROSECOND_SAFE","bindingTimeline":"availableAt<=capturedAt","levelTimeline":"observedAt<=availableAt<=capturedAt","continuityTimeline":"coverageStartsAt<=coverageEndsAt<=availableAt<=capturedAt","levelNumericContract":"POSITIVE_NUMERIC_38_12_EXACT_NO_ROUNDING","unavailableReasons":["ENDPOINT_NOT_REACHED_AS_OF","REFERENCE_INDEX_MISSING_AS_OF","REFERENCE_MAPPING_EVIDENCE_LINK_MISMATCH","REFERENCE_TAXONOMY_ID_MISMATCH","REFERENCE_TAXONOMY_VERSION_MISMATCH","REFERENCE_TAXONOMY_DEFINITION_HASH_MISMATCH","REFERENCE_CANONICAL_NODE_ID_MISMATCH","REFERENCE_ASSET_TYPE_MISMATCH","REFERENCE_CURRENCY_MISMATCH","REFERENCE_KIND_MISMATCH","REFERENCE_EFFECTIVE_INTERVAL_MISMATCH","REFERENCE_INDEX_AMBIGUOUS","BASIS_LEVEL_MISSING_AS_OF","BASIS_REFERENCE_INDEX_EVIDENCE_LINK_MISMATCH","BASIS_REFERENCE_ASSET_MISMATCH","BASIS_REFERENCE_PROVIDER_MISMATCH","BASIS_REFERENCE_INDEX_MISMATCH","BASIS_REFERENCE_INDEX_DEFINITION_REVISION_MISMATCH","BASIS_REFERENCE_KIND_MISMATCH","BASIS_CURRENCY_MISMATCH","BASIS_CALCULATION_VENUE_MISMATCH","BASIS_CALENDAR_MISMATCH","BASIS_LEVEL_SOURCE_MISMATCH","BASIS_OBSERVED_AT_MISMATCH","BASIS_LEVEL_FIELD_MISMATCH","BASIS_LEVEL_AMBIGUOUS","ENDPOINT_LEVEL_MISSING_AS_OF","ENDPOINT_REFERENCE_INDEX_EVIDENCE_LINK_MISMATCH","ENDPOINT_REFERENCE_ASSET_MISMATCH","ENDPOINT_REFERENCE_PROVIDER_MISMATCH","ENDPOINT_REFERENCE_INDEX_MISMATCH","ENDPOINT_REFERENCE_INDEX_DEFINITION_REVISION_MISMATCH","ENDPOINT_REFERENCE_KIND_MISMATCH","ENDPOINT_CURRENCY_MISMATCH","ENDPOINT_CALCULATION_VENUE_MISMATCH","ENDPOINT_CALENDAR_MISMATCH","ENDPOINT_LEVEL_SOURCE_MISMATCH","ENDPOINT_OBSERVED_AT_MISMATCH","ENDPOINT_LEVEL_FIELD_MISMATCH","ENDPOINT_LEVEL_AMBIGUOUS","DIVISOR_CONTINUITY_EVIDENCE_MISSING_AS_OF","DIVISOR_REFERENCE_INDEX_EVIDENCE_LINK_MISMATCH","DIVISOR_REFERENCE_ASSET_MISMATCH","DIVISOR_REFERENCE_PROVIDER_MISMATCH","DIVISOR_REFERENCE_INDEX_MISMATCH","DIVISOR_REFERENCE_INDEX_DEFINITION_REVISION_MISMATCH","DIVISOR_REFERENCE_KIND_MISMATCH","DIVISOR_CURRENCY_MISMATCH","DIVISOR_CALCULATION_VENUE_MISMATCH","DIVISOR_CALENDAR_MISMATCH","DIVISOR_CONTINUITY_SOURCE_MISMATCH","DIVISOR_BASIS_OBSERVATION_LINK_MISMATCH","DIVISOR_ENDPOINT_OBSERVATION_LINK_MISMATCH","DIVISOR_COVERAGE_MISMATCH","DIVISOR_CONTINUITY_UNAVAILABLE","DIVISOR_CONTINUITY_EVIDENCE_AMBIGUOUS"],"evaluationPrecedence":["ASSIGNMENT_NOT_APPLICABLE","ASSIGNMENT_UNAVAILABLE","ENDPOINT_ANCHOR_UNAVAILABLE","LOCAL_UNAVAILABLE_REASONS_IN_DECLARED_ORDER","RESOLVE"],"selectedEvidencePreservation":"EXACT_COMPLETE_RECORDS_AND_UPSTREAM_RESOLUTIONS","assetReturnPricePairReuse":"FORBIDDEN","corporateActionTypesReuse":"FORBIDDEN","referenceReturnCalculation":"ABSENT","lifecycleMapping":"ABSENT","providerIntegration":"ABSENT","runtimePublication":"ABSENT","fallbackBehavior":"ABSENT"}
```

Its lowercase SHA-256 is exactly
`4224648ba01104fd3e96319158c7d6b42da472e9aa6f2ab22ef9fccf43da7e4a`.

Both byte accessors return defensive copies and every result context echoes
its leg's exact digest. Any changed field, variant, reason, precedence,
identity, inference boundary, or byte requires a new policy version and hash.

## Source, runtime, and publication boundary

Production adds exactly fourteen files: seven under
`benchmarkreferencepair` and seven independently named sector analogues under
`sectorreferencepair`. The source-local test surface adds exactly
`BenchmarkReferenceLevelPairSelectorGoldenTest.java` and
`SectorReferenceLevelPairSelectorGoldenTest.java`.

No actual provider identity, provider index binding, index level, calendar,
divisor record, canonical fixture, schema, manifest member, OpenAPI path,
Flyway migration, database behavior, controller, repository, provider adapter,
scheduler, resource, API behavior, or web source is added. Tests may use only
obviously synthetic caller-supplied evidence. Existing DEMO benchmark, sector,
alpha, and sector-alpha values remain null.

These selectors choose evidence only. They perform no benchmark or sector
return calculation, invoke no ADR-017 arithmetic, establish no readiness,
methodology, fingerprint, lineage, completeness, outcome lifecycle,
persistence, aggregation, ranking, or publication.

## External-data and rights boundary

This disconnected contract needs no API key, account, paid plan, domain,
provider license, named secret, or network access. An API credential would
prove access only, not permitted use.

Before non-DEMO evidence enters either boundary, P5 must select the exact
benchmark and sector price-index products/feeds and document:

- historical point-in-time index-level entitlement and sufficient exact-time
  coverage/granularity at both UTC instants;
- reference calculation-calendar identity/revision and calendar-source
  identity/revision rights;
- index methodology and divisor-continuity evidence rights;
- for sector, the right to create and use the explicit WSR-node-to-provider
  price-index binding;
- storage and cache rights for source evidence;
- display rights for any permitted raw or derived value;
- derived-data rights to calculate benchmark and sector returns; and
- redistribution rights for every exposed output.

A daily-close-only or nearest-timestamp feed cannot satisfy this V1. Index
publisher and redistributor rights must both be reviewed when they differ.
Only after provider, product/feed, coverage, and written rights approval may a
reviewed adapter introduce named scoped credentials through untracked local,
CI, and deployment secret stores, never chat or Git.

## Verification

- Exact fourteen-plus-two source surface, independent canonical extraction,
  9342/9806 bytes and both policy hashes, field/variant order, 53/56 local
  reasons plus each three-reason anchor enum, reverse isolation, and exact
  four-document marker parity: **PASS**.
- Focused benchmark golden 200/200 and sector golden 220/220—420 total—and full
  API Maven regression 1668/1668 `BUILD SUCCESS` with zero failures, errors, or
  skips, including Docker/PostgreSQL/Flyway integration: **PASS**. Normalized
  golden-source SHA-256 values are benchmark
  `3518b66914656c8225858f8f15fdb60e25576a15b64f148270cda9881e3d8099`
  and sector
  `af9ec3aa0318595027d13eb4748d41bdb587776ef3d2e5c8b3bf477fa7ba439b`.
- The dedicated ADR-030 guard independently passes. All 35/35 workflow Python
  heredoc bodies syntax-compile; all 34/34 locally executable bodies pass, with
  the final cross-stack body intentionally syntax-only. SnakeYAML 2.5 parses
  exactly four jobs and Compose config validates: **PASS**.
- Current production is exactly 216 files / SHA-256
  `45d06843fd95235221c6716a578915f40a410de8464b0b0ca3a09fff7c29436d`;
  current API-test/web is exactly 201 files / SHA-256
  `fd0e3170ba2d64aeb4bf638010915455a27d3a5aed9fe77fb2a724502d96462f`.
  Excluding the exact ADR-030 fourteen-plus-two surface reproduces ADR-029
  production at 202 files /
  `b1ae60b9c550353960687cb9973e2909e965a5e3eb98bb23b39b0a7f01a2a899`
  and test/web at 199 files /
  `59726e88e5bf7d831f16beaa693689ca799990d355733a1115c07a285a7e5293`:
  **PASS**.
- README-marker and benchmark-policy-byte mutations make the dedicated guard
  exit 1; changing the benchmark expected cardinality to 201 while the actual
  remains 200 makes its gate exit 1. All mutations are restored.
  `git diff --check` is clean, and the user-owned
  `apps/web/next-env.d.ts` change remains preserved and unstaged: **PASS**.

## Consequences and next work

- A resolved benchmark or sector assignment can now be paired with exact
  provider-published price-index evidence without converting missing evidence
  into zero, N/A, a proxy, or a provider-calculated return.
- Benchmark and sector return calculators remain separate next slices. Each
  will own independent input/result types and ADR-017's exact one-subtraction,
  one scale-12 `HALF_EVEN` division; neither may cast to
  `AssetReturnResult`.
- Independent source-local readiness follows each calculator. Canonical
  evidence fixtures, methodology activation, fingerprint, lineage,
  completeness, persistence, API/UI, and production adapters remain later
  reviewed work. Raw-window coverage and MFE/MAE follow comparative returns;
  alpha and sector alpha remain last.
