package com.wallstreetreceipts.api.domain.outcome.favorableextreme;

import java.nio.charset.StandardCharsets;

/** Versioned selection of a PIT-visible favorable full-window extreme. */
public enum FavorableExtremePolicyVersion {
    POINT_IN_TIME_ATTESTED_CAUSAL_WINDOW_HIGH_LOW_V1;

    private static final String CANONICAL_DEFINITION =
            "{\"policyVersion\":\"POINT_IN_TIME_ATTESTED_CAUSAL_WINDOW_HIGH_LOW_V1\","
            + "\"requiredEligibilityPolicyVersion\":\"POINT_IN_TIME_TARGET_HIT_INPUT_READINESS_V1\","
            + "\"requiredEligibilityPolicyDefinitionHash\":\"a6b4c9f4e4d29b5f1a9b0c300e2d7b9505318c708dfb0ad0e88f71324cf65465\","
            + "\"requestFields\":[\"policyVersion\",\"readyEligibility\",\"binding\",\"candidates\"],"
            + "\"bindingFields\":[\"bindingId\",\"bindingRevision\",\"assetId\",\"primaryVenueId\",\"currency\",\"priceSourceId\",\"priceSourceRevision\",\"availableAt\",\"capturedAt\",\"provenanceId\"],"
            + "\"observationFields\":[\"observationId\",\"providerEventId\",\"basis\",\"horizon\",\"assetId\",\"venueId\",\"currency\",\"priceSourceId\",\"priceSourceRevision\",\"provenanceId\",\"calendarId\",\"catalogRevision\",\"orderedSessionIds\",\"lowerBound\",\"lowerBoundType\",\"upperBound\",\"upperBoundType\",\"priceField\",\"coverageCompleteness\",\"adjustmentBasis\",\"corporateActionContinuity\",\"availableAt\",\"capturedAt\",\"windowHigh\",\"windowLow\"],"
            + "\"resolutionContextFields\":[\"policyVersion\",\"policyDefinitionHash\",\"readyEligibility\"],"
            + "\"selectionEvidenceFields\":[\"binding\",\"knownCandidates\"],"
            + "\"resultVariants\":{\"Resolved\":[\"context\",\"evidence\",\"favorableExtreme\"],\"Unavailable\":[\"context\",\"evidence\",\"reason\"]},"
            + "\"evaluationAsOfSource\":\"readyEligibility.context.evaluationAsOf\","
            + "\"horizonSource\":\"readyEligibility.context.horizonResolution.resolved.window\","
            + "\"sideSource\":\"readyEligibility.evidence.sideRouting.directionalRoute.targetHitSide\","
            + "\"targetSource\":\"readyEligibility.evidence.targetEvidence\","
            + "\"catalogSource\":\"readyEligibility.evidence.catalogEvidence\","
            + "\"requiredAdjustmentBasis\":\"SPLIT_AND_REVERSE_SPLIT_ADJUSTED_TO_ENDPOINT_SHARE_BASIS_DIVIDEND_UNADJUSTED\","
            + "\"bindingPitPredicate\":\"availableAt<=evaluationAsOf&&capturedAt<=evaluationAsOf\","
            + "\"futureBindingRule\":\"IDENTICAL_TO_NULL_AND_INVISIBLE_TO_OUTPUT\","
            + "\"bindingIdentityPrecedence\":[\"BINDING_ASSET_MISMATCH\",\"BINDING_PRIMARY_VENUE_MISMATCH\",\"BINDING_CURRENCY_MISMATCH\"],"
            + "\"candidateScope\":\"ALL_REQUEST_CANDIDATES\","
            + "\"candidatePitPredicate\":\"availableAt<=evaluationAsOf&&capturedAt<=evaluationAsOf\","
            + "\"futureCandidateRule\":\"INVISIBLE_TO_ALL_OUTPUT_AND_REASONING\","
            + "\"economicObservationSet\":\"primary-venue regular-session observations belonging to horizon.window.sessions with observation.time>basis.eventTime&&observation.time<=endpointSession.closesAt\","
            + "\"lowerBoundRule\":\"lowerBound==basis.eventTime&&lowerBoundType==EXCLUSIVE\","
            + "\"upperBoundRule\":\"upperBound==endpointSession.closesAt&&upperBoundType==INCLUSIVE\","
            + "\"sessionWindowRule\":\"orderedSessionIds==horizon.window.sessions.sessionId in exact order\","
            + "\"coverageRule\":\"EXACT_CAUSAL_WINDOW_SESSION_UNION\","
            + "\"priceFieldRule\":\"PRIMARY_VENUE_REGULAR_SESSION_CAUSAL_WINDOW_HIGH_LOW_PAIR\","
            + "\"candidateIdentityPrecedence\":[\"BASIS_MISMATCH\",\"HORIZON_MISMATCH\",\"ASSET_MISMATCH\",\"PRIMARY_VENUE_MISMATCH\",\"CURRENCY_MISMATCH\",\"SOURCE_MISMATCH\",\"CATALOG_MISMATCH\",\"SESSION_WINDOW_MISMATCH\",\"LOWER_BOUND_MISMATCH\",\"UPPER_BOUND_MISMATCH\",\"BOUNDARY_CONVENTION_MISMATCH\",\"PRICE_FIELD_MISMATCH\",\"WINDOW_COMPLETENESS_UNAVAILABLE\",\"ADJUSTMENT_BASIS_MISMATCH\",\"CORPORATE_ACTION_CONTINUITY_UNAVAILABLE\"],"
            + "\"priceBoundary\":\"POSITIVE_NUMERIC_38_12_HIGH_AND_LOW_WITH_LOW_LESS_THAN_OR_EQUAL_TO_HIGH\","
            + "\"continuityRule\":\"SPLIT_REVERSE_SPLIT_CONTINUOUS_ONLY\","
            + "\"candidateCardinality\":\"EXACTLY_ONE_KNOWN_AS_OF_AFTER_MISMATCH_GATES\","
            + "\"knownInvalidCandidateRule\":\"POISONS_SELECTION_BEFORE_AMBIGUITY\","
            + "\"deduplication\":\"ABSENT\","
            + "\"favorableExtremeSelection\":{\"BULLISH\":\"WINDOW_HIGH\",\"BEARISH\":\"WINDOW_LOW\"},"
            + "\"selectedValueRule\":\"PRESERVE_ORIGINAL_BIGDECIMAL_NO_ROUNDING_OR_RESCALE\","
            + "\"attestationScope\":\"UPSTREAM_PROVIDER_SOURCE_ATTESTED_EXACT_WINDOW_HIGH_LOW_PAIR\","
            + "\"rawAggregation\":\"ABSENT\","
            + "\"rawObservationVerification\":\"ABSENT\","
            + "\"deferredRawSemantics\":[\"NO_TRADE\",\"HALT\",\"AUCTION\",\"BAR_STRADDLE\",\"CORRECTION_SEQUENCE\",\"RAW_COVERAGE_PROOF\"],"
            + "\"evaluationPrecedence\":[\"TARGET_ADJUSTMENT_BASIS_UNSUPPORTED\",\"BINDING_NOT_KNOWN_AS_OF\",\"BINDING_ASSET_MISMATCH\",\"BINDING_PRIMARY_VENUE_MISMATCH\",\"BINDING_CURRENCY_MISMATCH\",\"OBSERVATION_MISSING_AS_OF\",\"BASIS_MISMATCH\",\"HORIZON_MISMATCH\",\"ASSET_MISMATCH\",\"PRIMARY_VENUE_MISMATCH\",\"CURRENCY_MISMATCH\",\"SOURCE_MISMATCH\",\"CATALOG_MISMATCH\",\"SESSION_WINDOW_MISMATCH\",\"LOWER_BOUND_MISMATCH\",\"UPPER_BOUND_MISMATCH\",\"BOUNDARY_CONVENTION_MISMATCH\",\"PRICE_FIELD_MISMATCH\",\"WINDOW_COMPLETENESS_UNAVAILABLE\",\"ADJUSTMENT_BASIS_MISMATCH\",\"CORPORATE_ACTION_CONTINUITY_UNAVAILABLE\",\"OBSERVATION_AMBIGUOUS\",\"RESOLVE\"],"
            + "\"selectedEvidencePreservation\":\"EXACT_COMPLETE_VISIBLE_RECORDS\","
            + "\"branchClearingRule\":\"EVIDENCE_AFTER_DECIDING_PRECEDENCE_GATE_IS_EMPTY\","
            + "\"endpointPriceObservationInput\":\"ABSENT\","
            + "\"endpointCloseFallback\":\"ABSENT\","
            + "\"genericPriceField\":\"ABSENT\","
            + "\"calculatorInvocation\":\"ABSENT\","
            + "\"fallbackBehavior\":\"ABSENT\"}";

    private static final String DEFINITION_HASH =
            "e3a0e93030c8f09ae5398bf6df0f2e28eec14b0a31f5bea240fc78f2412c2463";

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
