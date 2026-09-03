package com.wallstreetreceipts.api.domain.outcome.observation;

import java.nio.charset.StandardCharsets;

/** Versioned selection of one point-in-time official endpoint close. */
public enum EndpointPricePolicyVersion {
    OFFICIAL_PRIMARY_VENUE_CLOSE_SPLIT_ADJUSTED_V1;

    private static final String CANONICAL_DEFINITION =
            "{\"policyVersion\":\"OFFICIAL_PRIMARY_VENUE_CLOSE_SPLIT_ADJUSTED_V1\","
            + "\"horizonInput\":\"STRICT_SESSION_CLOSE_RESOLVED_WINDOW\","
            + "\"requiredHorizonPolicyVersion\":\"STRICTLY_AFTER_BASIS_EVENT_SESSION_CLOSE_V1\","
            + "\"requiredHorizonPolicyDefinitionHash\":\"550087efe7ddf2ba31974c89c2740ab79df986eefef48919c32c56a3232f8dc1\","
            + "\"catalogWindowIdentity\":\"catalog.calendarId==horizon.context.calendarId&&catalog.catalogRevision==horizon.context.catalogRevision\","
            + "\"catalogPitPredicate\":\"availableAt<=evaluationAsOf&&capturedAt<=evaluationAsOf\","
            + "\"bindingPitPredicate\":\"availableAt<=evaluationAsOf&&capturedAt<=evaluationAsOf\","
            + "\"endpointMaturityPredicate\":\"endpointSession.closesAt<=evaluationAsOf\","
            + "\"candidateScope\":\"ALL_REQUEST_CANDIDATES\","
            + "\"knownCandidatePredicate\":\"candidate.availableAt<=evaluationAsOf&&candidate.capturedAt<=evaluationAsOf\","
            + "\"futureCandidateRule\":\"INVISIBLE_TO_ALL_OUTPUT_AND_REASONING\","
            + "\"noKnownReason\":\"OBSERVATION_MISSING_AS_OF\","
            + "\"priceField\":\"OFFICIAL_REGULAR_SESSION_CLOSE\","
            + "\"venueRule\":\"PRIMARY_VENUE_EXACT_MATCH\","
            + "\"currencyRule\":\"EXACT_MATCH_NO_FX\","
            + "\"bindingCurrencyRole\":\"REQUIRED_SCORING_AND_TARGET_CURRENCY\","
            + "\"sourceRule\":\"PRICE_SOURCE_ID_AND_REVISION_EXACT_MATCH\","
            + "\"observationTimeRule\":\"observedAt==endpointSession.closesAt\","
            + "\"provenanceRule\":\"CATALOG_BINDING_OBSERVATION_PROVENANCE_PRESERVED_INDEPENDENTLY\","
            + "\"selectedObservationIdentity\":[\"observationId\",\"priceSourceId\",\"providerEventId\"],"
            + "\"selectedObservationEvidence\":[\"priceSourceRevision\",\"provenanceId\"],"
            + "\"deduplication\":\"ABSENT\","
            + "\"candidateCardinality\":\"EXACTLY_ONE_KNOWN_AS_OF\","
            + "\"priceBoundary\":\"POSITIVE_NUMERIC_38_12\","
            + "\"adjustmentBasis\":\"SPLIT_AND_REVERSE_SPLIT_ADJUSTED_TO_ENDPOINT_SHARE_BASIS_DIVIDEND_UNADJUSTED\","
            + "\"continuityRule\":\"SPLIT_REVERSE_SPLIT_CONTINUOUS_ONLY\","
            + "\"gatePrecedence\":[\"CATALOG_NOT_KNOWN_AS_OF\",\"CATALOG_EVIDENCE_MISMATCH\",\"BINDING_NOT_KNOWN_AS_OF\",\"ENDPOINT_NOT_REACHED_AS_OF\",\"OBSERVATION_MISSING_AS_OF\"],"
            + "\"knownCandidateMismatchPrecedence\":[\"ASSET_MISMATCH\",\"PRIMARY_VENUE_MISMATCH\",\"CURRENCY_MISMATCH\",\"SOURCE_MISMATCH\",\"CATALOG_MISMATCH\",\"SESSION_MISMATCH\",\"OBSERVED_AT_MISMATCH\",\"PRICE_FIELD_MISMATCH\",\"ADJUSTMENT_BASIS_MISMATCH\",\"CORPORATE_ACTION_CONTINUITY_UNAVAILABLE\"],"
            + "\"ambiguityRule\":\"AFTER_ALL_KNOWN_CANDIDATES_PASS_MISMATCH_GATES\","
            + "\"resolvedCardinality\":1,"
            + "\"fallbackBehavior\":\"ABSENT\"}";

    // Updated only with the exact canonical definition above.
    private static final String DEFINITION_HASH =
            "37e37aba9302d77366cef4129f77a82b7ccb2f1937bfffc0315ea8d0bc6b1f76";

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
