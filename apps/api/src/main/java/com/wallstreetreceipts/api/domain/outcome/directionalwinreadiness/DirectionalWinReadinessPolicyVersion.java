package com.wallstreetreceipts.api.domain.outcome.directionalwinreadiness;

import java.nio.charset.StandardCharsets;

/** Versioned readiness classification of one supplied directional-win result. */
public enum DirectionalWinReadinessPolicyVersion {
    SUPPLIED_LEAF_DIRECTIONAL_WIN_READINESS_V1;

    private static final String CANONICAL_DEFINITION =
            "{\"policyVersion\":\"SUPPLIED_LEAF_DIRECTIONAL_WIN_READINESS_V1\","
            + "\"requiredDirectionalWinOrchestrationPolicyVersion\":\"SUPPLIED_LEAF_DIRECTIONAL_WIN_ORCHESTRATION_V1\","
            + "\"requiredDirectionalWinOrchestrationPolicyDefinitionHash\":\"51429c7601d4807162855f08c680d1e6bb7895f87fc108e141e5ad3a3ab25bcb\","
            + "\"requestFields\":[\"policyVersion\",\"sourceResolution\"],"
            + "\"requestPresence\":\"ALL_FIELDS_NON_NULL\","
            + "\"resolutionContextFields\":[\"policyVersion\",\"policyDefinitionHash\"],"
            + "\"resultVariants\":{\"Settled\":[\"context\",\"sourceResolution\"],\"AwaitingEndpoint\":[\"context\",\"sourceResolution\"],\"EvidenceUnavailable\":[\"context\",\"sourceResolution\"]},"
            + "\"sourceInput\":\"COMPLETE_SUPPLIED_DIRECTIONAL_WIN_ORCHESTRATION_RESOLUTION\","
            + "\"sourceAttestationBoundary\":\"LOCAL_SOURCE_POLICY_AND_TYPED_SHAPE_ONLY_NO_ORIGINAL_REQUEST_MEMBERSHIP_PIT_FILTERING_OR_PRODUCER_INVOCATION_CLAIM\","
            + "\"classificationValidation\":\"RESULT_CONSTRUCTORS_AND_RESOLVER_SHARE_EXACT_CLASSIFICATION_VALIDATION\","
            + "\"resolverInvocationAttestation\":\"ONLY_RESOLVE_ATTESTS_REQUEST_TO_RESULT_INVOCATION\","
            + "\"assetReturnSource\":{\"Available\":\"sourceResolution.assetReturnResult\",\"NotApplicable\":\"sourceResolution.assetReturnResult\",\"AssetReturnUnavailable\":\"sourceResolution.assetReturnResult\"},"
            + "\"branchPrecedence\":[\"AssetReturn.Available\",\"EXACT_AWAITING_ENDPOINT_CHAIN\",\"ALL_OTHER_ASSET_RETURN_UNAVAILABLE\"],"
            + "\"branchMapping\":{\"AnySource+AssetReturn.Available\":\"SETTLED\",\"AnySource+ExactAwaitingEndpointChain\":\"AWAITING_ENDPOINT\",\"AnySource+OtherAssetReturn.Unavailable\":\"EVIDENCE_UNAVAILABLE\"},"
            + "\"awaitingEndpointChain\":{\"assetReturnReason\":\"PRICE_PAIR_UNAVAILABLE\",\"pricePairReason\":\"ENDPOINT_PRICE_UNAVAILABLE\",\"endpointReason\":\"ENDPOINT_NOT_REACHED_AS_OF\"},"
            + "\"basisAndEndpointUnavailableRule\":\"EVIDENCE_UNAVAILABLE_EVEN_WHEN_ENDPOINT_NOT_REACHED\","
            + "\"settledRule\":\"DIRECTIONAL_AVAILABLE_OR_NEUTRAL_NOT_APPLICABLE_WITH_ASSET_RETURN_AVAILABLE\","
            + "\"sourcePreservation\":\"PRESERVE_EXACT_WHOLE_DIRECTIONAL_WIN_ORCHESTRATION_RESOLUTION\","
            + "\"reasonInspection\":\"ONLY_IN_DIRECTIONAL_WIN_READINESS_RESOLVER\","
            + "\"reasonFlattening\":\"ABSENT\","
            + "\"canonicalOutcomeStatus\":\"ABSENT\","
            + "\"dataCompleteClaim\":\"ABSENT\","
            + "\"retry\":\"ABSENT\","
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
            "1eca77c5b4d43de7657281c161a8c50356cd90e1a18c6e9fd7f5b2c0142b7ec7";

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
