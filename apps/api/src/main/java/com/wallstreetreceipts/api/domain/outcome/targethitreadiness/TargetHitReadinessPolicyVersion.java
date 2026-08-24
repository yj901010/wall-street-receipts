package com.wallstreetreceipts.api.domain.outcome.targethitreadiness;

import java.nio.charset.StandardCharsets;

/** Versioned readiness classification of one supplied target-hit result. */
public enum TargetHitReadinessPolicyVersion {
    SUPPLIED_LEAF_TARGET_HIT_READINESS_V1;

    private static final String CANONICAL_DEFINITION =
            "{\"policyVersion\":\"SUPPLIED_LEAF_TARGET_HIT_READINESS_V1\","
            + "\"requiredTargetHitOrchestrationPolicyVersion\":\"POINT_IN_TIME_TARGET_HIT_ORCHESTRATION_V1\","
            + "\"requiredTargetHitOrchestrationPolicyDefinitionHash\":\"b91bf68958e42ad003b80973c74f9acc2dad8e4629f6a1905798df98aa8b5348\","
            + "\"requestFields\":[\"policyVersion\",\"sourceResult\"],"
            + "\"requestPresence\":\"ALL_FIELDS_NON_NULL\","
            + "\"resolutionContextFields\":[\"policyVersion\",\"policyDefinitionHash\"],"
            + "\"resultVariants\":{\"Settled\":[\"context\",\"sourceResult\"],\"AwaitingEndpoint\":[\"context\",\"sourceResult\"],\"EvidenceUnavailable\":[\"context\",\"sourceResult\"]},"
            + "\"sourceInput\":\"COMPLETE_SUPPLIED_TARGET_HIT_ORCHESTRATION_RESOLUTION\","
            + "\"sourceAttestationBoundary\":\"LOCAL_SOURCE_POLICY_AND_TYPED_SHAPE_ONLY_NO_ORIGINAL_INPUT_MEMBERSHIP_PIT_FILTERING_SELECTOR_OR_CALCULATOR_INVOCATION_CLAIM\","
            + "\"classificationValidation\":\"RESULT_CONSTRUCTORS_AND_RESOLVER_SHARE_EXACT_CLASSIFICATION_VALIDATION\","
            + "\"resolverInvocationAttestation\":\"ONLY_RESOLVE_ATTESTS_REQUEST_TO_RESULT_INVOCATION\","
            + "\"branchPrecedence\":[\"Available\",\"NotApplicable\",\"Pending\",\"EligibilityUnavailable\",\"FavorableExtremeUnavailable\"],"
            + "\"branchMapping\":{\"Available\":\"SETTLED\",\"NotApplicable\":\"SETTLED\",\"Pending\":\"AWAITING_ENDPOINT\",\"EligibilityUnavailable\":\"EVIDENCE_UNAVAILABLE\",\"FavorableExtremeUnavailable\":\"EVIDENCE_UNAVAILABLE\"},"
            + "\"awaitingEndpointChain\":{\"orchestrationVariant\":\"Pending\",\"eligibilityPendingReason\":\"HORIZON_NOT_REACHED_AS_OF\"},"
            + "\"typedVariantReasonRule\":\"NO_NESTED_REASON_REINTERPRETATION_OR_DUPLICATION\","
            + "\"settledRule\":\"TARGET_HIT_AVAILABLE_OR_PERMANENTLY_NOT_APPLICABLE\","
            + "\"sourcePreservation\":\"PRESERVE_EXACT_WHOLE_TARGET_HIT_ORCHESTRATION_RESOLUTION\","
            + "\"reasonFlattening\":\"ABSENT\","
            + "\"canonicalOutcomeStatus\":\"ABSENT\","
            + "\"dataCompleteClaim\":\"ABSENT\","
            + "\"retry\":\"ABSENT\","
            + "\"freshness\":\"ABSENT\","
            + "\"cancellation\":\"ABSENT\","
            + "\"scheduling\":\"ABSENT\","
            + "\"producerReplay\":\"ABSENT\","
            + "\"selectorInvocation\":\"ABSENT\","
            + "\"calculatorInvocation\":\"ABSENT\","
            + "\"methodologyActivation\":\"ABSENT\","
            + "\"inputFingerprint\":\"ABSENT\","
            + "\"persistence\":\"ABSENT\","
            + "\"aggregation\":\"ABSENT\","
            + "\"ranking\":\"ABSENT\","
            + "\"publication\":\"ABSENT\"}";

    private static final String DEFINITION_HASH =
            "8f81dee5227370d82dd91cd2fb8448797c7028eaa485dc64cf4bdc3cbf2f31a3";

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
