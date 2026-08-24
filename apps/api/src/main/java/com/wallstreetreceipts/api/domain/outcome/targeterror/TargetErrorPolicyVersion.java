package com.wallstreetreceipts.api.domain.outcome.targeterror;

import java.nio.charset.StandardCharsets;

/** Versioned target-error formula and evidence contract. */
public enum TargetErrorPolicyVersion {
    ACTUAL_DENOMINATOR_SCALE_12_HALF_EVEN_V1;

    private static final String CANONICAL_DEFINITION =
            "{\"policyVersion\":\"ACTUAL_DENOMINATOR_SCALE_12_HALF_EVEN_V1\","
            + "\"requiredEndpointPolicyVersion\":\"OFFICIAL_PRIMARY_VENUE_CLOSE_SPLIT_ADJUSTED_V1\","
            + "\"requiredEndpointPolicyDefinitionHash\":\"37e37aba9302d77366cef4129f77a82b7ccb2f1937bfffc0315ea8d0bc6b1f76\","
            + "\"endpointInput\":\"ENDPOINT_PRICE_RESOLUTION\","
            + "\"targetEvidenceFields\":[\"targetEvidenceId\",\"basis\",\"assetId\",\"primaryVenueId\",\"currency\",\"adjustmentBasis\",\"target\",\"availableAt\",\"capturedAt\",\"provenanceId\"],"
            + "\"targetPitPredicate\":\"availableAt<=endpoint.evaluationAsOf&&capturedAt<=endpoint.evaluationAsOf\","
            + "\"targetEvidenceTemporalRule\":\"basis.eventTime<=availableAt<=capturedAt\","
            + "\"futureTargetRule\":\"IDENTICAL_TO_NULL_AND_INVISIBLE_TO_OUTPUT\","
            + "\"targetMissingReason\":\"TARGET_MISSING_AS_OF\","
            + "\"missingEndpointTruthTable\":{\"targetMissingAsOf&&endpointUnavailable\":\"TARGET_AND_ENDPOINT_PRICE_UNAVAILABLE_WITH_EXACT_ENDPOINT_REASON\",\"targetMissingAsOfOnly\":\"TARGET_MISSING_AS_OF\",\"endpointUnavailableOnly\":\"ENDPOINT_PRICE_UNAVAILABLE_WITH_EXACT_ENDPOINT_REASON\"},"
            + "\"basisRule\":\"target.basis==endpoint.horizon.context.basis\","
            + "\"identityMatchPrecedence\":[\"BASIS_MISMATCH\",\"ASSET_MISMATCH\",\"PRIMARY_VENUE_MISMATCH\",\"CURRENCY_MISMATCH\",\"ADJUSTMENT_BASIS_MISMATCH\"],"
            + "\"evaluationPrecedence\":[\"TARGET_AND_ENDPOINT_PRICE_UNAVAILABLE\",\"TARGET_MISSING_AS_OF\",\"ENDPOINT_PRICE_UNAVAILABLE\",\"BASIS_MISMATCH\",\"ASSET_MISMATCH\",\"PRIMARY_VENUE_MISMATCH\",\"CURRENCY_MISMATCH\",\"ADJUSTMENT_BASIS_MISMATCH\",\"CALCULATE\",\"OUTPUT_NOT_REPRESENTABLE\"],"
            + "\"currencyRule\":\"EXACT_MATCH_NO_FX\","
            + "\"formula\":\"abs(target-actual)/actual\","
            + "\"denominator\":\"ACTUAL_ENDPOINT_PRICE\","
            + "\"inputBoundary\":\"POSITIVE_NUMERIC_38_12\","
            + "\"divisionScale\":12,"
            + "\"roundingMode\":\"HALF_EVEN\","
            + "\"divisionCount\":1,"
            + "\"outputUnits\":\"DECIMAL_RATIO\","
            + "\"outputBoundary\":\"NONNEGATIVE_NUMERIC_38_12\","
            + "\"outputOverflowReason\":\"OUTPUT_NOT_REPRESENTABLE\","
            + "\"endpointUnavailableRule\":\"PRESERVE_EXACT_ENDPOINT_REASON\","
            + "\"resultContext\":\"POLICY_IDENTITY_AND_ENDPOINT_RESOLUTION_ONLY\","
            + "\"fallbackBehavior\":\"ABSENT\"}";

    private static final String DEFINITION_HASH =
            "31ca30555549f670e3c22d98ead16f7a02bfad198f36532effaf4a4b6931d074";

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
