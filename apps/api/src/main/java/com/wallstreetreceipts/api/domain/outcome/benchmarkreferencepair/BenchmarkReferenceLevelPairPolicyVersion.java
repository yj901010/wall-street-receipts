package com.wallstreetreceipts.api.domain.outcome.benchmarkreferencepair;

import java.nio.charset.StandardCharsets;

/** Versioned exact benchmark reference-level-pair policy. */
public enum BenchmarkReferenceLevelPairPolicyVersion {
    POINT_IN_TIME_EXACT_BENCHMARK_PRICE_INDEX_LEVEL_PAIR_V1;

    private static final String CANONICAL_DEFINITION =
            "{\"policyVersion\":\"POINT_IN_TIME_EXACT_BENCHMARK_PRICE_INDEX_LEVEL_PAIR_V1\","
            + "\"requiredAssignmentPolicyVersion\":\"POINT_IN_TIME_EXPLICIT_US_EQUITY_ASSET_SPX_ASSIGNMENT_V1\","
            + "\"requiredAssignmentPolicyDefinitionHash\":\"7318514c2f50eda16b2d7ef35bc68d00d6a8b18a0f09f77130525fca2f32da69\","
            + "\"requiredEndpointPricePolicyVersion\":\"OFFICIAL_PRIMARY_VENUE_CLOSE_SPLIT_ADJUSTED_V1\","
            + "\"requiredEndpointPricePolicyDefinitionHash\":\"37e37aba9302d77366cef4129f77a82b7ccb2f1937bfffc0315ea8d0bc6b1f76\","
            + "\"requiredNestedHorizonPolicyVersion\":\"STRICTLY_AFTER_BASIS_EVENT_SESSION_CLOSE_V1\","
            + "\"requiredNestedHorizonPolicyDefinitionHash\":\"550087efe7ddf2ba31974c89c2740ab79df986eefef48919c32c56a3232f8dc1\","
            + "\"referenceIndexEvidenceFields\":[\"referenceIndexEvidenceId\",\"providerEventId\",\"assignmentEvidenceId\",\"assignmentProviderEventId\",\"benchmarkAssetId\",\"benchmarkAssetType\",\"referenceProviderId\",\"referenceIndexId\",\"referenceIndexLabel\",\"referenceIndexDefinitionRevision\",\"referenceKind\",\"currency\",\"calculationVenueId\",\"calendarId\",\"calendarRevision\",\"calendarSourceId\",\"calendarSourceRevision\",\"levelSourceId\",\"levelSourceRevision\",\"continuitySourceId\",\"continuitySourceRevision\",\"bindingSourceId\",\"bindingSourceRevision\",\"provenanceId\",\"effectiveInterval\",\"availableAt\",\"capturedAt\"],"
            + "\"effectiveIntervalFields\":[\"startsAtInclusive\",\"end\"],"
            + "\"effectiveIntervalEndVariants\":{\"OpenEnded\":[],\"EndsAtExclusive\":[\"value\"]},"
            + "\"referenceLevelObservationFields\":[\"observationId\",\"providerEventId\",\"referenceIndexEvidenceId\",\"referenceIndexProviderEventId\",\"benchmarkAssetId\",\"benchmarkAssetType\",\"referenceProviderId\",\"referenceIndexId\",\"referenceIndexDefinitionRevision\",\"referenceKind\",\"currency\",\"calculationVenueId\",\"calendarId\",\"calendarRevision\",\"calendarSourceId\",\"calendarSourceRevision\",\"levelSourceId\",\"levelSourceRevision\",\"provenanceId\",\"levelField\",\"observedAt\",\"availableAt\",\"capturedAt\",\"level\"],"
            + "\"divisorContinuityEvidenceFields\":[\"continuityEvidenceId\",\"providerEventId\",\"referenceIndexEvidenceId\",\"referenceIndexProviderEventId\",\"benchmarkAssetId\",\"benchmarkAssetType\",\"referenceProviderId\",\"referenceIndexId\",\"referenceIndexDefinitionRevision\",\"referenceKind\",\"currency\",\"calculationVenueId\",\"calendarId\",\"calendarRevision\",\"calendarSourceId\",\"calendarSourceRevision\",\"continuitySourceId\",\"continuitySourceRevision\",\"provenanceId\",\"basisObservationId\",\"basisProviderEventId\",\"endpointObservationId\",\"endpointProviderEventId\",\"coverageStartsAt\",\"coverageEndsAt\",\"divisorContinuity\",\"availableAt\",\"capturedAt\"],"
            + "\"requestFields\":[\"policyVersion\",\"assignmentResolution\",\"endpointPriceResolution\",\"referenceIndexCandidates\",\"basisLevelCandidates\",\"endpointLevelCandidates\",\"divisorContinuityCandidates\"],"
            + "\"resolutionContextFields\":[\"policyVersion\",\"policyDefinitionHash\",\"assignmentResolution\",\"endpointPriceResolution\"],"
            + "\"resultVariants\":{\"Resolved\":[\"context\",\"referenceIndexEvidence\",\"basisLevelObservation\",\"endpointLevelObservation\",\"divisorContinuityEvidence\"],\"NotApplicable\":[\"context\"],\"AssignmentUnavailable\":[\"context\"],\"EndpointAnchorUnavailable\":[\"context\",\"reason\"],\"EvidenceUnavailable\":[\"context\",\"reason\"]},"
            + "\"referenceIndexKinds\":[\"PROVIDER_PUBLISHED_PRICE_INDEX\",\"PROVIDER_PUBLISHED_TOTAL_RETURN_INDEX\",\"NON_PROVIDER_PUBLISHED_PRICE_INDEX\",\"EXCHANGE_TRADED_FUND\",\"CURRENT_CONSTITUENT_BASKET\",\"MARKET_CAP_PROXY\",\"PROVIDER_RETURN_FIELD\",\"UNKNOWN\"],"
            + "\"requiredReferenceIndexKind\":\"PROVIDER_PUBLISHED_PRICE_INDEX\","
            + "\"referenceLevelFields\":[\"PROVIDER_PUBLISHED_INDEX_LEVEL\",\"PROVIDER_PUBLISHED_RETURN\",\"EXCHANGE_TRADED_FUND_MARKET_PRICE\",\"EXCHANGE_TRADED_FUND_NAV\",\"DERIVED_PROXY_LEVEL\",\"UNKNOWN\"],"
            + "\"requiredReferenceLevelField\":\"PROVIDER_PUBLISHED_INDEX_LEVEL\","
            + "\"divisorContinuityStates\":[\"PROVIDER_PUBLISHED_INDEX_DIVISOR_CONTINUITY_ATTESTED\",\"DIVISOR_DISCONTINUITY\",\"NOT_ATTESTED\",\"UNKNOWN\"],"
            + "\"requiredDivisorContinuity\":\"PROVIDER_PUBLISHED_INDEX_DIVISOR_CONTINUITY_ATTESTED\","
            + "\"endpointAnchorUnavailableReasons\":[\"CATALOG_NOT_KNOWN_AS_OF\",\"CATALOG_EVIDENCE_MISMATCH\",\"BINDING_NOT_KNOWN_AS_OF\"],"
            + "\"endpointAnchorDerivation\":\"CONTEXT_FACTS_NOT_UPSTREAM_REASON_LABEL\","
            + "\"anchorBranchTopology\":\"catalogPITThenCatalogHorizonIdentityThenBindingPIT;ResolvedAndEvidenceUnavailableRejectAnchorFailures;EndpointAnchorUnavailableRequiresExactDerivedReason\","
            + "\"endpointNotReachedReasonTopology\":\"ENDPOINT_NOT_REACHED_AS_OF_IFF_assetEndpointUtc>evaluationAsOf\","
            + "\"endpointObservationUnavailableReasonsRole\":\"IGNORED_REFERENCE_SELECTION_INDEPENDENT\","
            + "\"inputAnchor\":\"FULL_ASSIGNMENT_RESOLUTION_AND_FULL_ENDPOINT_PRICE_RESOLUTION\","
            + "\"directOutcomeBasisOrHorizonInput\":false,"
            + "\"upstreamTopology\":\"basisAndEvaluationAsOfAlwaysEqual;assetIdAndResolvedOrNotApplicableClassificationVenueCurrencyEqualEndpointBindingOnlyAfterUsableAnchor\","
            + "\"assignmentUnavailableTopologyLimitation\":\"VENUE_AND_CURRENCY_NOT_PROVABLE_FROM_UPSTREAM_VARIANT_NO_INFERENCE\","
            + "\"referenceInterval\":\"[endpoint.context.horizon.basis.eventTime,endpoint.context.horizon.endpointSession.closesAt]\","
            + "\"referenceTimestampSubstitution\":\"ABSENT_NO_PRIOR_CLOSE_NEAREST_INTERPOLATION_OR_SHIFTED_SESSION\","
            + "\"sameCurrencyRule\":\"reference.currency==endpoint.context.binding.currency\","
            + "\"fxConversion\":\"ABSENT\","
            + "\"referenceSpecificIdentity\":\"calculationVenueId,calendarId,calendarRevision,calendarSourceId,calendarSourceRevision,levelSourceId,levelSourceRevision,continuitySourceId,continuitySourceRevision\","
            + "\"bindingChain\":\"referenceIndexEvidence.assignmentEvidenceId,assignmentProviderEventId==selectedAssignmentEvidence.identity\","
            + "\"levelChain\":\"level.referenceIndexEvidenceId,referenceIndexProviderEventId,benchmarkAssetId,benchmarkAssetType==selectedReferenceIndexEvidence.identityAndCanonicalAsset\","
            + "\"continuityChain\":\"continuity.referenceIndexEvidenceId,referenceIndexProviderEventId,benchmarkAssetId,benchmarkAssetType==selectedReferenceIndexEvidence.identityAndCanonicalAsset&&basisAndEndpointObservationLinksExact\","
            + "\"bindingIntervalPredicate\":\"contains(basis.eventTime)&&contains(assetEndpointUtc)\","
            + "\"effectiveIntervalBoundary\":\"START_INCLUSIVE_END_EXCLUSIVE\","
            + "\"pitPredicate\":\"availableAt<=evaluationAsOf&&capturedAt<=evaluationAsOf\","
            + "\"futureEvidenceRule\":\"INVISIBLE_TO_ALL_OUTPUT_REASONING_AND_CARDINALITY\","
            + "\"candidateSetRule\":\"ANY_VISIBLE_MISMATCH_FAILS_CLOSED_BEFORE_CARDINALITY\","
            + "\"candidateOrderRule\":\"ORDER_INDEPENDENT\","
            + "\"equalDuplicateRule\":\"AMBIGUOUS_NO_DEDUPLICATION\","
            + "\"providerIdentityFields\":[\"referenceProviderId\",\"referenceIndexId\",\"referenceIndexDefinitionRevision\"],"
            + "\"providerIdentityComparison\":\"EXACT_CASE_SENSITIVE_UNNORMALIZED_UNICODE_CODE_POINT_EQUALITY\","
            + "\"providerIdentityValidation\":\"NON_NULL_NON_EMPTY_NO_STRIP_NORMALIZATION_OR_CASE_FOLD\","
            + "\"providerLabelRole\":\"PRESERVED_EVIDENCE_ONLY_NOT_IDENTITY_OR_MATCH_KEY\","
            + "\"localCanonicalTextValidation\":\"NONBLANK_TRIMMED\","
            + "\"instantPrecision\":\"PERSISTENT_MICROSECOND_SAFE\","
            + "\"bindingTimeline\":\"availableAt<=capturedAt\","
            + "\"levelTimeline\":\"observedAt<=availableAt<=capturedAt\","
            + "\"continuityTimeline\":\"coverageStartsAt<=coverageEndsAt<=availableAt<=capturedAt\","
            + "\"levelNumericContract\":\"POSITIVE_NUMERIC_38_12_EXACT_NO_ROUNDING\","
            + "\"unavailableReasons\":[\"ENDPOINT_NOT_REACHED_AS_OF\",\"REFERENCE_INDEX_MISSING_AS_OF\",\"REFERENCE_ASSIGNMENT_EVIDENCE_LINK_MISMATCH\",\"REFERENCE_BENCHMARK_ASSET_ID_MISMATCH\",\"REFERENCE_BENCHMARK_ASSET_TYPE_MISMATCH\",\"REFERENCE_CURRENCY_MISMATCH\",\"REFERENCE_KIND_MISMATCH\",\"REFERENCE_EFFECTIVE_INTERVAL_MISMATCH\",\"REFERENCE_INDEX_AMBIGUOUS\",\"BASIS_LEVEL_MISSING_AS_OF\",\"BASIS_REFERENCE_INDEX_EVIDENCE_LINK_MISMATCH\",\"BASIS_BENCHMARK_ASSET_MISMATCH\",\"BASIS_REFERENCE_PROVIDER_MISMATCH\",\"BASIS_REFERENCE_INDEX_MISMATCH\",\"BASIS_REFERENCE_INDEX_DEFINITION_REVISION_MISMATCH\",\"BASIS_REFERENCE_KIND_MISMATCH\",\"BASIS_CURRENCY_MISMATCH\",\"BASIS_CALCULATION_VENUE_MISMATCH\",\"BASIS_CALENDAR_MISMATCH\",\"BASIS_LEVEL_SOURCE_MISMATCH\",\"BASIS_OBSERVED_AT_MISMATCH\",\"BASIS_LEVEL_FIELD_MISMATCH\",\"BASIS_LEVEL_AMBIGUOUS\",\"ENDPOINT_LEVEL_MISSING_AS_OF\",\"ENDPOINT_REFERENCE_INDEX_EVIDENCE_LINK_MISMATCH\",\"ENDPOINT_BENCHMARK_ASSET_MISMATCH\",\"ENDPOINT_REFERENCE_PROVIDER_MISMATCH\",\"ENDPOINT_REFERENCE_INDEX_MISMATCH\",\"ENDPOINT_REFERENCE_INDEX_DEFINITION_REVISION_MISMATCH\",\"ENDPOINT_REFERENCE_KIND_MISMATCH\",\"ENDPOINT_CURRENCY_MISMATCH\",\"ENDPOINT_CALCULATION_VENUE_MISMATCH\",\"ENDPOINT_CALENDAR_MISMATCH\",\"ENDPOINT_LEVEL_SOURCE_MISMATCH\",\"ENDPOINT_OBSERVED_AT_MISMATCH\",\"ENDPOINT_LEVEL_FIELD_MISMATCH\",\"ENDPOINT_LEVEL_AMBIGUOUS\",\"DIVISOR_CONTINUITY_EVIDENCE_MISSING_AS_OF\",\"DIVISOR_REFERENCE_INDEX_EVIDENCE_LINK_MISMATCH\",\"DIVISOR_BENCHMARK_ASSET_MISMATCH\",\"DIVISOR_REFERENCE_PROVIDER_MISMATCH\",\"DIVISOR_REFERENCE_INDEX_MISMATCH\",\"DIVISOR_REFERENCE_INDEX_DEFINITION_REVISION_MISMATCH\",\"DIVISOR_REFERENCE_KIND_MISMATCH\",\"DIVISOR_CURRENCY_MISMATCH\",\"DIVISOR_CALCULATION_VENUE_MISMATCH\",\"DIVISOR_CALENDAR_MISMATCH\",\"DIVISOR_CONTINUITY_SOURCE_MISMATCH\",\"DIVISOR_BASIS_OBSERVATION_LINK_MISMATCH\",\"DIVISOR_ENDPOINT_OBSERVATION_LINK_MISMATCH\",\"DIVISOR_COVERAGE_MISMATCH\",\"DIVISOR_CONTINUITY_UNAVAILABLE\",\"DIVISOR_CONTINUITY_EVIDENCE_AMBIGUOUS\"],"
            + "\"evaluationPrecedence\":[\"ASSIGNMENT_NOT_APPLICABLE\",\"ASSIGNMENT_UNAVAILABLE\",\"ENDPOINT_ANCHOR_UNAVAILABLE\",\"LOCAL_UNAVAILABLE_REASONS_IN_DECLARED_ORDER\",\"RESOLVE\"],"
            + "\"selectedEvidencePreservation\":\"EXACT_COMPLETE_RECORDS_AND_UPSTREAM_RESOLUTIONS\","
            + "\"assetReturnPricePairReuse\":\"FORBIDDEN\","
            + "\"corporateActionTypesReuse\":\"FORBIDDEN\","
            + "\"referenceReturnCalculation\":\"ABSENT\","
            + "\"lifecycleMapping\":\"ABSENT\","
            + "\"providerIntegration\":\"ABSENT\","
            + "\"runtimePublication\":\"ABSENT\","
            + "\"fallbackBehavior\":\"ABSENT\"}";

    private static final String DEFINITION_HASH =
            "2394b535c1061d32c647504a303b6f1e4ec2fe88e6017d9ff335d12087a5f73d";

    public String canonicalDefinition() {
        return CANONICAL_DEFINITION;
    }

    public byte[] canonicalDefinitionUtf8() {
        return CANONICAL_DEFINITION.getBytes(StandardCharsets.UTF_8);
    }

    public String definitionHash() {
        return DEFINITION_HASH;
    }
}
