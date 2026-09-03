package com.wallstreetreceipts.api.domain.outcome.targeterrorreadiness;

import java.nio.charset.StandardCharsets;

/** Versioned readiness classification of one supplied target-error result. */
public enum TargetErrorReadinessPolicyVersion {
    SUPPLIED_LEAF_TARGET_ERROR_READINESS_V1;

    private static final String CANONICAL_DEFINITION =
            "{\"policyVersion\":\"SUPPLIED_LEAF_TARGET_ERROR_READINESS_V1\","
            + "\"requiredTargetErrorPolicyVersion\":\"ACTUAL_DENOMINATOR_SCALE_12_HALF_EVEN_V1\","
            + "\"requiredTargetErrorPolicyDefinitionHash\":\"31ca30555549f670e3c22d98ead16f7a02bfad198f36532effaf4a4b6931d074\","
            + "\"requestFields\":[\"policyVersion\",\"sourceResult\"],"
            + "\"requestPresence\":\"ALL_FIELDS_NON_NULL\","
            + "\"resolutionContextFields\":[\"policyVersion\",\"policyDefinitionHash\"],"
            + "\"resultVariants\":{\"Settled\":[\"context\",\"sourceResult\"],\"AwaitingEndpoint\":[\"context\",\"sourceResult\"],\"EvidenceUnavailable\":[\"context\",\"sourceResult\"]},"
            + "\"sourceInput\":\"COMPLETE_SUPPLIED_TARGET_ERROR_RESULT\","
            + "\"sourceAttestationBoundary\":\"LOCAL_SOURCE_POLICY_AND_TYPED_SHAPE_ONLY_NO_ORIGINAL_INPUT_MEMBERSHIP_PIT_FILTERING_OR_CALCULATOR_INVOCATION_CLAIM\","
            + "\"classificationValidation\":\"RESULT_CONSTRUCTORS_AND_RESOLVER_SHARE_EXACT_CLASSIFICATION_VALIDATION\","
            + "\"resolverInvocationAttestation\":\"ONLY_RESOLVE_ATTESTS_REQUEST_TO_RESULT_INVOCATION\","
            + "\"branchPrecedence\":[\"TargetErrorResult.Available\",\"EXACT_AWAITING_ENDPOINT_CHAIN\",\"ALL_OTHER_TARGET_ERROR_UNAVAILABLE\"],"
            + "\"branchMapping\":{\"TargetErrorResult.Available\":\"SETTLED\",\"ExactAwaitingEndpointChain\":\"AWAITING_ENDPOINT\",\"OtherTargetErrorResult.Unavailable\":\"EVIDENCE_UNAVAILABLE\"},"
            + "\"awaitingEndpointChain\":{\"targetErrorReason\":\"ENDPOINT_PRICE_UNAVAILABLE\",\"endpointReason\":\"ENDPOINT_NOT_REACHED_AS_OF\"},"
            + "\"targetAndEndpointUnavailableRule\":\"EVIDENCE_UNAVAILABLE_EVEN_WHEN_ENDPOINT_NOT_REACHED\","
            + "\"settledRule\":\"TARGET_ERROR_AVAILABLE\","
            + "\"sourcePreservation\":\"PRESERVE_EXACT_WHOLE_TARGET_ERROR_RESULT\","
            + "\"reasonInspection\":\"ONLY_IN_TARGET_ERROR_READINESS_RESOLVER\","
            + "\"reasonFlattening\":\"ABSENT\","
            + "\"canonicalOutcomeStatus\":\"ABSENT\","
            + "\"dataCompleteClaim\":\"ABSENT\","
            + "\"retry\":\"ABSENT\","
            + "\"freshness\":\"ABSENT\","
            + "\"cancellation\":\"ABSENT\","
            + "\"scheduling\":\"ABSENT\","
            + "\"producerReplay\":\"ABSENT\","
            + "\"calculatorInvocation\":\"ABSENT\","
            + "\"methodologyActivation\":\"ABSENT\","
            + "\"inputFingerprint\":\"ABSENT\","
            + "\"persistence\":\"ABSENT\","
            + "\"aggregation\":\"ABSENT\","
            + "\"ranking\":\"ABSENT\","
            + "\"publication\":\"ABSENT\"}";

    private static final String DEFINITION_HASH =
            "0b8bfb22dccd4a494f568c44d06163f73af36462cf929bc83cf238019811c44a";

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
