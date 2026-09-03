package com.wallstreetreceipts.api.domain.outcome.pricepair;

import java.nio.charset.StandardCharsets;

/** Versioned point-in-time basis/endpoint price-pair selection policy. */
public enum AssetReturnPricePairPolicyVersion {
    SOURCE_RECORDED_BASIS_EVENT_TO_OFFICIAL_ENDPOINT_PRICE_PAIR_V1;

    private static final String CANONICAL_DEFINITION =
            "{\"policyVersion\":\"SOURCE_RECORDED_BASIS_EVENT_TO_OFFICIAL_ENDPOINT_PRICE_PAIR_V1\","
            + "\"requiredEndpointPolicyVersion\":\"OFFICIAL_PRIMARY_VENUE_CLOSE_SPLIT_ADJUSTED_V1\","
            + "\"requiredEndpointPolicyDefinitionHash\":\"37e37aba9302d77366cef4129f77a82b7ccb2f1937bfffc0315ea8d0bc6b1f76\","
            + "\"endpointInput\":\"ENDPOINT_PRICE_RESOLUTION\","
            + "\"basisObservationFields\":[\"observationId\",\"providerEventId\","
            + "\"basis\",\"assetId\",\"venueId\",\"currency\",\"priceSourceId\","
            + "\"priceSourceRevision\",\"provenanceId\",\"priceField\","
            + "\"adjustmentBasis\",\"corporateActionContinuity\",\"observedAt\","
            + "\"availableAt\",\"capturedAt\",\"price\"],"
            + "\"adjustmentEvidenceFields\":[\"adjustmentEvidenceId\","
            + "\"providerEventId\",\"basis\",\"assetId\",\"primaryVenueId\","
            + "\"currency\",\"adjustmentSourceId\",\"adjustmentSourceRevision\","
            + "\"provenanceId\",\"basisObservationId\",\"basisProviderEventId\","
            + "\"endpointObservationId\",\"endpointProviderEventId\","
            + "\"coverageStartsAt\",\"coverageEndsAt\",\"adjustmentBasis\","
            + "\"corporateActionContinuity\",\"availableAt\",\"capturedAt\"],"
            + "\"selectedEvidenceRule\":\"PRESERVE_COMPLETE_BASIS_OBSERVATION_AND_ADJUSTMENT_EVIDENCE_RECORDS\","
            + "\"evaluationAsOfSource\":\"endpoint.context.evaluationAsOf\","
            + "\"basisCandidateScope\":\"ALL_REQUEST_BASIS_CANDIDATES\","
            + "\"basisKnownPredicate\":\"availableAt<=evaluationAsOf&&capturedAt<=evaluationAsOf\","
            + "\"basisTemporalRule\":\"observedAt<=availableAt<=capturedAt\","
            + "\"futureBasisRule\":\"INVISIBLE_TO_ALL_OUTPUT_AND_REASONING\","
            + "\"basisMissingEndpointTruthTable\":{"
            + "\"basisMissingAsOf&&endpointUnavailable\":\"BASIS_AND_ENDPOINT_PRICE_UNAVAILABLE_WITH_EXACT_ENDPOINT_REASON\","
            + "\"basisMissingAsOfOnly\":\"BASIS_PRICE_MISSING_AS_OF\","
            + "\"endpointUnavailableOnly\":\"ENDPOINT_PRICE_UNAVAILABLE_WITH_EXACT_ENDPOINT_REASON\"},"
            + "\"basisIdentityPrecedence\":[\"BASIS_MISMATCH\",\"ASSET_MISMATCH\","
            + "\"PRIMARY_VENUE_MISMATCH\",\"CURRENCY_MISMATCH\","
            + "\"PRICE_SOURCE_MISMATCH\",\"OBSERVED_AT_MISMATCH\","
            + "\"PRICE_FIELD_MISMATCH\",\"BASIS_PRICE_ADJUSTMENT_BASIS_MISMATCH\","
            + "\"BASIS_PRICE_CONTINUITY_UNAVAILABLE\"],"
            + "\"basisRule\":\"candidate.basis==endpoint.horizon.context.basis\","
            + "\"basisObservedAtRule\":\"candidate.observedAt==candidate.basis.eventTime\","
            + "\"basisPriceField\":\"SOURCE_RECORDED_BASIS_EVENT_PRICE\","
            + "\"assetVenueCurrencyRule\":\"EXACT_ENDPOINT_BINDING_MATCH_NO_FX\","
            + "\"priceSourceRule\":\"PRICE_SOURCE_ID_AND_REVISION_EXACT_ENDPOINT_BINDING_MATCH\","
            + "\"basisPriceBoundary\":\"POSITIVE_NUMERIC_38_12\","
            + "\"basisCandidateCardinality\":\"EXACTLY_ONE_KNOWN_AS_OF_AFTER_MISMATCH_GATES\","
            + "\"adjustmentCandidateScope\":\"ALL_REQUEST_ADJUSTMENT_CANDIDATES\","
            + "\"adjustmentKnownPredicate\":\"availableAt<=evaluationAsOf&&capturedAt<=evaluationAsOf\","
            + "\"adjustmentTemporalRule\":\"coverageStartsAt<=coverageEndsAt<=availableAt<=capturedAt\","
            + "\"futureAdjustmentRule\":\"INVISIBLE_TO_ALL_OUTPUT_AND_REASONING\","
            + "\"adjustmentMissingReason\":\"ADJUSTMENT_EVIDENCE_MISSING_AS_OF\","
            + "\"adjustmentIdentityPrecedence\":[\"ADJUSTMENT_OUTCOME_BASIS_MISMATCH\","
            + "\"ADJUSTMENT_ASSET_MISMATCH\",\"ADJUSTMENT_PRIMARY_VENUE_MISMATCH\","
            + "\"ADJUSTMENT_CURRENCY_MISMATCH\",\"BASIS_OBSERVATION_LINK_MISMATCH\","
            + "\"ENDPOINT_OBSERVATION_LINK_MISMATCH\",\"ADJUSTMENT_COVERAGE_MISMATCH\","
            + "\"ADJUSTMENT_PRICE_BASIS_MISMATCH\",\"ADJUSTMENT_CONTINUITY_UNAVAILABLE\"],"
            + "\"observationLinkRule\":\"BASIS_AND_ENDPOINT_OBSERVATION_ID_AND_PROVIDER_EVENT_ID_EXACT_MATCH\","
            + "\"coverageRule\":\"coverageStartsAt==basis.observedAt&&coverageEndsAt==endpoint.observedAt\","
            + "\"adjustmentSourceRule\":\"PRESERVE_ID_REVISION_PROVIDER_EVENT_AND_PROVENANCE_NO_PREFERENCE\","
            + "\"priceBasis\":\"SPLIT_AND_REVERSE_SPLIT_ADJUSTED_TO_ENDPOINT_SHARE_BASIS_DIVIDEND_UNADJUSTED\","
            + "\"continuityRule\":\"SPLIT_REVERSE_SPLIT_CONTINUOUS_ONLY\","
            + "\"unsupportedActions\":[\"MERGER\",\"SPIN_OFF\",\"DELISTING\","
            + "\"SPECIAL_DISTRIBUTION\",\"UNKNOWN\"],"
            + "\"adjustmentCandidateCardinality\":\"EXACTLY_ONE_KNOWN_AS_OF_AFTER_MISMATCH_GATES\","
            + "\"evaluationPrecedence\":[\"BASIS_AND_ENDPOINT_PRICE_UNAVAILABLE\","
            + "\"BASIS_PRICE_MISSING_AS_OF\",\"ENDPOINT_PRICE_UNAVAILABLE\","
            + "\"BASIS_MISMATCH\",\"ASSET_MISMATCH\",\"PRIMARY_VENUE_MISMATCH\","
            + "\"CURRENCY_MISMATCH\",\"PRICE_SOURCE_MISMATCH\","
            + "\"OBSERVED_AT_MISMATCH\",\"PRICE_FIELD_MISMATCH\","
            + "\"BASIS_PRICE_ADJUSTMENT_BASIS_MISMATCH\","
            + "\"BASIS_PRICE_CONTINUITY_UNAVAILABLE\",\"BASIS_PRICE_AMBIGUOUS\","
            + "\"ADJUSTMENT_EVIDENCE_MISSING_AS_OF\","
            + "\"ADJUSTMENT_OUTCOME_BASIS_MISMATCH\",\"ADJUSTMENT_ASSET_MISMATCH\","
            + "\"ADJUSTMENT_PRIMARY_VENUE_MISMATCH\","
            + "\"ADJUSTMENT_CURRENCY_MISMATCH\",\"BASIS_OBSERVATION_LINK_MISMATCH\","
            + "\"ENDPOINT_OBSERVATION_LINK_MISMATCH\","
            + "\"ADJUSTMENT_COVERAGE_MISMATCH\",\"ADJUSTMENT_PRICE_BASIS_MISMATCH\","
            + "\"ADJUSTMENT_CONTINUITY_UNAVAILABLE\","
            + "\"ADJUSTMENT_EVIDENCE_AMBIGUOUS\",\"RESOLVE\"],"
            + "\"resultContext\":\"POLICY_IDENTITY_AND_COMPLETE_ENDPOINT_PRICE_RESOLUTION_ONLY\","
            + "\"endpointUnavailableRule\":\"PRESERVE_EXACT_NESTED_ENDPOINT_REASON\","
            + "\"priorCloseBehavior\":\"ABSENT\",\"nearestPriceBehavior\":\"ABSENT\","
            + "\"interpolationBehavior\":\"ABSENT\",\"deduplication\":\"ABSENT\","
            + "\"fallbackBehavior\":\"ABSENT\"}";

    private static final String DEFINITION_HASH =
            "895e4bc97ebb3a92b80f2c58e2d28abb94440eeca963046ee755fa98825f4887";

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
