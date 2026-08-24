package com.wallstreetreceipts.api.domain.outcome.sectorreturn;

import java.nio.charset.StandardCharsets;

/** Versioned signed sector price-index return formula and output policy. */
public enum SectorReturnPolicyVersion {
    SIGNED_SECTOR_BASIS_LEVEL_DENOMINATOR_SCALE_12_HALF_EVEN_V1;

    private static final String CANONICAL_DEFINITION =
            "{\"policyVersion\":\"SIGNED_SECTOR_BASIS_LEVEL_DENOMINATOR_SCALE_12_HALF_EVEN_V1\","
            + "\"requiredReferenceLevelPairPolicyVersion\":\"POINT_IN_TIME_EXACT_SECTOR_PRICE_INDEX_LEVEL_PAIR_V1\","
            + "\"requiredReferenceLevelPairPolicyDefinitionHash\":\"4224648ba01104fd3e96319158c7d6b42da472e9aa6f2ab22ef9fccf43da7e4a\","
            + "\"referenceLevelPairInput\":\"SECTOR_REFERENCE_LEVEL_PAIR_RESOLUTION\","
            + "\"resultContextFields\":[\"policyVersion\",\"policyDefinitionHash\",\"referenceLevelPairResolution\"],"
            + "\"resultVariants\":{\"Available\":[\"context\",\"sectorReturn\"],\"NotApplicable\":[\"context\"],\"AssignmentUnavailable\":[\"context\"],\"EndpointAnchorUnavailable\":[\"context\"],\"EvidenceUnavailable\":[\"context\"],\"OutputUnavailable\":[\"context\",\"reason\"]},"
            + "\"outputUnavailableReasons\":[\"OUTPUT_NOT_REPRESENTABLE\"],"
            + "\"branchMapping\":{\"Resolved\":\"CALCULATE\",\"NotApplicable\":\"NotApplicable\",\"AssignmentUnavailable\":\"AssignmentUnavailable\",\"EndpointAnchorUnavailable\":\"EndpointAnchorUnavailable\",\"EvidenceUnavailable\":\"EvidenceUnavailable\"},"
            + "\"nestedReasonRule\":\"PRESERVE_COMPLETE_PAIR_RECEIPT_NO_MAPPING_DUPLICATION_OR_FLATTENING\","
            + "\"evaluationPrecedence\":[\"REFERENCE_PAIR_NOT_APPLICABLE\",\"REFERENCE_PAIR_ASSIGNMENT_UNAVAILABLE\",\"REFERENCE_PAIR_ENDPOINT_ANCHOR_UNAVAILABLE\",\"REFERENCE_PAIR_EVIDENCE_UNAVAILABLE\",\"CALCULATE\",\"OUTPUT_NOT_REPRESENTABLE\"],"
            + "\"formula\":\"(endpoint-basis)/basis\","
            + "\"operandSources\":{\"basis\":\"RESOLVED_BASIS_LEVEL_OBSERVATION_LEVEL\",\"endpoint\":\"RESOLVED_ENDPOINT_LEVEL_OBSERVATION_LEVEL\"},"
            + "\"numerator\":\"ENDPOINT_MINUS_BASIS_REFERENCE_LEVEL\","
            + "\"denominator\":\"BASIS_REFERENCE_LEVEL\","
            + "\"subtractionCount\":1,\"divisionCount\":1,"
            + "\"divisionScale\":12,\"roundingMode\":\"HALF_EVEN\","
            + "\"operationOrder\":[\"SUBTRACT_BASIS_FROM_ENDPOINT_EXACTLY\",\"DIVIDE_NUMERATOR_BY_BASIS_AT_SCALE_12_HALF_EVEN\"],"
            + "\"intermediateRounding\":\"ABSENT\","
            + "\"secondRounding\":\"ABSENT\","
            + "\"outputUnits\":\"SIGNED_DECIMAL_RATIO\","
            + "\"inputBoundary\":\"POSITIVE_NUMERIC_38_12_PROVIDER_PUBLISHED_PRICE_INDEX_LEVEL_PAIR\","
            + "\"outputBoundary\":\"SIGNED_NUMERIC_38_12_AT_LEAST_NEGATIVE_ONE\","
            + "\"roundedNegativeOneBoundary\":\"VALID\","
            + "\"outputOverflowReason\":\"OUTPUT_NOT_REPRESENTABLE\","
            + "\"percentConversion\":\"ABSENT\","
            + "\"floatOrDoubleConversion\":\"ABSENT\","
            + "\"providerReturnFieldUse\":\"ABSENT\","
            + "\"assetReturnResultReuse\":\"FORBIDDEN\","
            + "\"benchmarkReturnResultReuse\":\"FORBIDDEN\","
            + "\"resultContext\":\"POLICY_IDENTITY_AND_COMPLETE_SECTOR_REFERENCE_LEVEL_PAIR_RESOLUTION_ONLY\","
            + "\"constructorAttestation\":\"LOCAL_POLICY_PAIR_VARIANT_AND_OUTPUT_SHAPE_ONLY\","
            + "\"sourceAttestationBoundary\":\"ONLY_SECTOR_RETURN_CALCULATOR_ATTESTS_FORMULA_AND_REQUIRED_ROUNDING\","
            + "\"calculationAuthority\":\"DETERMINISTIC_SOURCE_LOCAL_PURE_LEAF\","
            + "\"reflectionOrClassTokenUse\":\"ABSENT\","
            + "\"clockLocaleTimezoneRandomEnvironmentDependence\":\"ABSENT\","
            + "\"readiness\":\"ABSENT\",\"lifecycleMapping\":\"ABSENT\","
            + "\"methodologyPersistencePublication\":\"ABSENT\","
            + "\"providerIntegration\":\"ABSENT\",\"runtimeWiring\":\"ABSENT\","
            + "\"fallbackBehavior\":\"ABSENT\"}";

    private static final String DEFINITION_HASH =
            "5aecd42c32ba69f0d21ab6e1ee1e3128cd31584724a6f46e176acd470204d0f7";

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
