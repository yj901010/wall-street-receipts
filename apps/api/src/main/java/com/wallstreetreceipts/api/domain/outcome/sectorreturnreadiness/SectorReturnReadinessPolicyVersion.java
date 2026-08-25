package com.wallstreetreceipts.api.domain.outcome.sectorreturnreadiness;

import java.nio.charset.StandardCharsets;

/** Versioned readiness classification of one supplied sector-return result. */
public enum SectorReturnReadinessPolicyVersion {
    SUPPLIED_LEAF_SECTOR_RETURN_READINESS_V1;

    private static final String CANONICAL_DEFINITION =
            "{\"policyVersion\":\"SUPPLIED_LEAF_SECTOR_RETURN_READINESS_V1\","
            + "\"requiredSectorReturnPolicyVersion\":\"SIGNED_SECTOR_BASIS_LEVEL_DENOMINATOR_SCALE_12_HALF_EVEN_V1\","
            + "\"requiredSectorReturnPolicyDefinitionHash\":\"5aecd42c32ba69f0d21ab6e1ee1e3128cd31584724a6f46e176acd470204d0f7\","
            + "\"requestFields\":[\"policyVersion\",\"sourceResult\"],"
            + "\"requestPresence\":\"ALL_FIELDS_NON_NULL\","
            + "\"resolutionContextFields\":[\"policyVersion\",\"policyDefinitionHash\"],"
            + "\"resultVariants\":{\"Settled\":[\"context\",\"sourceResult\"],\"AwaitingEndpoint\":[\"context\",\"sourceResult\"],\"EvidenceUnavailable\":[\"context\",\"sourceResult\"]},"
            + "\"sourceInput\":\"COMPLETE_SUPPLIED_SECTOR_RETURN_RESULT\","
            + "\"readinessOwnership\":\"SECTOR_RETURN_ONLY\","
            + "\"otherComparativeReturnInput\":\"ABSENT\","
            + "\"crossReturnCorrelation\":\"ABSENT_DEFERRED_TO_FUTURE_AGGREGATE\","
            + "\"sharedGenericReadiness\":\"ABSENT\","
            + "\"sourceAttestationBoundary\":\"LOCAL_SOURCE_POLICY_AND_TYPED_SHAPE_ONLY_NO_ORIGINAL_INPUT_MEMBERSHIP_PIT_FILTERING_SELECTOR_OR_CALCULATOR_INVOCATION_CLAIM\","
            + "\"classificationValidation\":\"RESULT_CONSTRUCTORS_AND_RESOLVER_SHARE_EXACT_CLASSIFICATION_VALIDATION\","
            + "\"resolverInvocationAttestation\":\"ONLY_RESOLVE_ATTESTS_REQUEST_TO_RESULT_INVOCATION\","
            + "\"branchPrecedence\":[\"Available\",\"NotApplicable\",\"EXACT_AWAITING_ENDPOINT_CHAIN\",\"AssignmentUnavailable\",\"EndpointAnchorUnavailable\",\"OTHER_EVIDENCE_UNAVAILABLE\",\"OutputUnavailable\"],"
            + "\"branchMapping\":{\"Available\":\"SETTLED\",\"NotApplicable\":\"SETTLED\",\"ExactAwaitingEndpointChain\":\"AWAITING_ENDPOINT\",\"AssignmentUnavailable\":\"EVIDENCE_UNAVAILABLE\",\"EndpointAnchorUnavailable\":\"EVIDENCE_UNAVAILABLE\",\"OtherEvidenceUnavailable\":\"EVIDENCE_UNAVAILABLE\",\"OutputUnavailable\":\"EVIDENCE_UNAVAILABLE\"},"
            + "\"awaitingEndpointChain\":{\"sectorReturnVariant\":\"EvidenceUnavailable\",\"referenceLevelPairVariant\":\"EvidenceUnavailable\",\"referenceLevelPairReason\":\"ENDPOINT_NOT_REACHED_AS_OF\"},"
            + "\"notApplicableEndpointRule\":\"SETTLED_WITHOUT_ENDPOINT_WAIT_OR_REASON_INSPECTION\","
            + "\"assignmentOrAnchorUnavailableRule\":\"EVIDENCE_UNAVAILABLE_EVEN_WHEN_ENDPOINT_NOT_REACHED\","
            + "\"settledRule\":\"SECTOR_RETURN_AVAILABLE_OR_INTENTIONALLY_NOT_APPLICABLE\","
            + "\"sourcePreservation\":\"PRESERVE_EXACT_WHOLE_SECTOR_RETURN_RESULT\","
            + "\"reasonInspection\":\"ONLY_IN_SECTOR_RETURN_READINESS_RESOLVER\","
            + "\"reasonFlattening\":\"ABSENT\","
            + "\"canonicalOutcomeStatus\":\"ABSENT\","
            + "\"dataCompleteClaim\":\"ABSENT\","
            + "\"retry\":\"ABSENT\",\"freshness\":\"ABSENT\","
            + "\"cancellation\":\"ABSENT\",\"scheduling\":\"ABSENT\","
            + "\"producerReplay\":\"ABSENT\",\"selectorInvocation\":\"ABSENT\","
            + "\"calculatorInvocation\":\"ABSENT\","
            + "\"methodologyActivation\":\"ABSENT\","
            + "\"inputFingerprint\":\"ABSENT\",\"persistence\":\"ABSENT\","
            + "\"aggregation\":\"ABSENT\",\"ranking\":\"ABSENT\","
            + "\"publication\":\"ABSENT\"}";

    private static final String DEFINITION_HASH =
            "5737f44ebc6e65270300889dd5c2e92da0c4f3a2f04e4c6c43e4483e522187d4";

    public String canonicalDefinition() {
        return CANONICAL_DEFINITION;
    }

    public byte[] canonicalDefinitionUtf8() {
        return CANONICAL_DEFINITION.getBytes(StandardCharsets.UTF_8).clone();
    }

    public String definitionHash() {
        return DEFINITION_HASH;
    }
}
