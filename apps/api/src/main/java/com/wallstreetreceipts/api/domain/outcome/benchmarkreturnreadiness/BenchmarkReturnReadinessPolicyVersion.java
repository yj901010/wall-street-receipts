package com.wallstreetreceipts.api.domain.outcome.benchmarkreturnreadiness;

import java.nio.charset.StandardCharsets;

/** Versioned readiness classification of one supplied benchmark-return result. */
public enum BenchmarkReturnReadinessPolicyVersion {
    SUPPLIED_LEAF_BENCHMARK_RETURN_READINESS_V1;

    private static final String CANONICAL_DEFINITION =
            "{\"policyVersion\":\"SUPPLIED_LEAF_BENCHMARK_RETURN_READINESS_V1\","
            + "\"requiredBenchmarkReturnPolicyVersion\":\"SIGNED_BENCHMARK_BASIS_LEVEL_DENOMINATOR_SCALE_12_HALF_EVEN_V1\","
            + "\"requiredBenchmarkReturnPolicyDefinitionHash\":\"96d0aab8e8e784b80a12b16c99f6ba8c5f44eff7a342fd14c075b944a0a7de79\","
            + "\"requestFields\":[\"policyVersion\",\"sourceResult\"],"
            + "\"requestPresence\":\"ALL_FIELDS_NON_NULL\","
            + "\"resolutionContextFields\":[\"policyVersion\",\"policyDefinitionHash\"],"
            + "\"resultVariants\":{\"Settled\":[\"context\",\"sourceResult\"],\"AwaitingEndpoint\":[\"context\",\"sourceResult\"],\"EvidenceUnavailable\":[\"context\",\"sourceResult\"]},"
            + "\"sourceInput\":\"COMPLETE_SUPPLIED_BENCHMARK_RETURN_RESULT\","
            + "\"readinessOwnership\":\"BENCHMARK_RETURN_ONLY\","
            + "\"otherComparativeReturnInput\":\"ABSENT\","
            + "\"crossReturnCorrelation\":\"ABSENT_DEFERRED_TO_FUTURE_AGGREGATE\","
            + "\"sharedGenericReadiness\":\"ABSENT\","
            + "\"sourceAttestationBoundary\":\"LOCAL_SOURCE_POLICY_AND_TYPED_SHAPE_ONLY_NO_ORIGINAL_INPUT_MEMBERSHIP_PIT_FILTERING_SELECTOR_OR_CALCULATOR_INVOCATION_CLAIM\","
            + "\"classificationValidation\":\"RESULT_CONSTRUCTORS_AND_RESOLVER_SHARE_EXACT_CLASSIFICATION_VALIDATION\","
            + "\"resolverInvocationAttestation\":\"ONLY_RESOLVE_ATTESTS_REQUEST_TO_RESULT_INVOCATION\","
            + "\"branchPrecedence\":[\"Available\",\"NotApplicable\",\"EXACT_AWAITING_ENDPOINT_CHAIN\",\"AssignmentUnavailable\",\"EndpointAnchorUnavailable\",\"OTHER_EVIDENCE_UNAVAILABLE\",\"OutputUnavailable\"],"
            + "\"branchMapping\":{\"Available\":\"SETTLED\",\"NotApplicable\":\"SETTLED\",\"ExactAwaitingEndpointChain\":\"AWAITING_ENDPOINT\",\"AssignmentUnavailable\":\"EVIDENCE_UNAVAILABLE\",\"EndpointAnchorUnavailable\":\"EVIDENCE_UNAVAILABLE\",\"OtherEvidenceUnavailable\":\"EVIDENCE_UNAVAILABLE\",\"OutputUnavailable\":\"EVIDENCE_UNAVAILABLE\"},"
            + "\"awaitingEndpointChain\":{\"benchmarkReturnVariant\":\"EvidenceUnavailable\",\"referenceLevelPairVariant\":\"EvidenceUnavailable\",\"referenceLevelPairReason\":\"ENDPOINT_NOT_REACHED_AS_OF\"},"
            + "\"notApplicableEndpointRule\":\"SETTLED_WITHOUT_ENDPOINT_WAIT_OR_REASON_INSPECTION\","
            + "\"assignmentOrAnchorUnavailableRule\":\"EVIDENCE_UNAVAILABLE_EVEN_WHEN_ENDPOINT_NOT_REACHED\","
            + "\"settledRule\":\"BENCHMARK_RETURN_AVAILABLE_OR_INTENTIONALLY_NOT_APPLICABLE\","
            + "\"sourcePreservation\":\"PRESERVE_EXACT_WHOLE_BENCHMARK_RETURN_RESULT\","
            + "\"reasonInspection\":\"ONLY_IN_BENCHMARK_RETURN_READINESS_RESOLVER\","
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
            "2dedaf014a149ed81e75941ee3677e3c8b77243b9987d9496709266aad721daf";

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
