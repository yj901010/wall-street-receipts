package com.wallstreetreceipts.api.domain.outcome.targethitorchestration;

import java.nio.charset.StandardCharsets;

/** Versioned composition of PIT target readiness, evidence, and target hit. */
public enum TargetHitOrchestrationPolicyVersion {
    POINT_IN_TIME_TARGET_HIT_ORCHESTRATION_V1;

    private static final String CANONICAL_DEFINITION =
            "{\"policyVersion\":\"POINT_IN_TIME_TARGET_HIT_ORCHESTRATION_V1\","
            + "\"requiredEligibilityPolicyVersion\":\"POINT_IN_TIME_TARGET_HIT_INPUT_READINESS_V1\","
            + "\"requiredEligibilityPolicyDefinitionHash\":\"a6b4c9f4e4d29b5f1a9b0c300e2d7b9505318c708dfb0ad0e88f71324cf65465\","
            + "\"requiredFavorableExtremePolicyVersion\":\"POINT_IN_TIME_ATTESTED_CAUSAL_WINDOW_HIGH_LOW_V1\","
            + "\"requiredFavorableExtremePolicyDefinitionHash\":\"e3a0e93030c8f09ae5398bf6df0f2e28eec14b0a31f5bea240fc78f2412c2463\","
            + "\"requestFields\":[\"policyVersion\",\"eligibilityResolution\",\"favorableExtremeResolution\"],"
            + "\"resolutionContextFields\":[\"policyVersion\",\"policyDefinitionHash\"],"
            + "\"resultVariants\":{\"Available\":[\"context\",\"favorableExtremeResolution\",\"targetHitResult\"],\"Pending\":[\"context\",\"eligibilityResolution\"],\"NotApplicable\":[\"context\",\"eligibilityResolution\"],\"EligibilityUnavailable\":[\"context\",\"eligibilityResolution\"],\"FavorableExtremeUnavailable\":[\"context\",\"favorableExtremeResolution\"]},"
            + "\"eligibilityInput\":\"COMPLETE_SUPPLIED_TARGET_ELIGIBILITY_RESOLUTION\","
            + "\"favorableExtremeInput\":\"CONDITIONAL_COMPLETE_SUPPLIED_FAVORABLE_EXTREME_RESOLUTION\","
            + "\"leafAttestationBoundary\":\"LOCAL_CONSISTENCY_ONLY_NO_REQUEST_MEMBERSHIP_PIT_FILTERING_OR_SELECTOR_PRODUCTION_CLAIM\","
            + "\"eligibilityPrecedence\":[\"Pending\",\"NotApplicable\",\"Unavailable\",\"ReadyForWindowEvidence\"],"
            + "\"nonReadyFavorableExtremeRule\":\"MUST_BE_NULL_AND_NOT_EVALUATED\","
            + "\"readyFavorableExtremeRule\":\"MUST_BE_NON_NULL\","
            + "\"crossResolutionIdentity\":\"favorableExtreme.context.readyEligibility==eligibilityResolution_BY_WHOLE_RECORD_EQUALITY\","
            + "\"branchMapping\":{\"Pending\":\"PRESERVE_PENDING\",\"NotApplicable\":\"PRESERVE_NOT_APPLICABLE\",\"Unavailable\":\"PRESERVE_ELIGIBILITY_UNAVAILABLE\",\"ReadyForWindowEvidence+FavorableExtreme.Unavailable\":\"PRESERVE_FAVORABLE_EXTREME_UNAVAILABLE\",\"ReadyForWindowEvidence+FavorableExtreme.Resolved\":\"INVOKE_TARGET_HIT\"},"
            + "\"nestedReasonRule\":\"PRESERVE_EXACT_TYPED_LEAF_RESOLUTION_WITHOUT_REASON_MAPPING\","
            + "\"resolvedSideSource\":\"eligibilityResolution.evidence.sideRouting.directionalRoute.targetHitSide\","
            + "\"resolvedTargetSource\":\"eligibilityResolution.evidence.targetEvidence.target\","
            + "\"resolvedFavorableExtremeSource\":\"favorableExtremeResolution.favorableExtreme.value\","
            + "\"targetHitInputFields\":[\"side\",\"target\",\"favorableExtreme\"],"
            + "\"calculator\":\"TargetHitCalculator.calculate\","
            + "\"calculatorInvocation\":\"EXACTLY_ONCE_ONLY_FOR_READY_AND_RESOLVED\","
            + "\"calculatorUnavailableRule\":\"INVARIANT_VIOLATION_FAIL_CLOSED_WITHOUT_RESULT\","
            + "\"comparison\":{\"BULLISH\":\"favorableExtreme.compareTo(target)>=0\",\"BEARISH\":\"favorableExtreme.compareTo(target)<=0\"},"
            + "\"equalityRule\":\"HIT\","
            + "\"selectedValueRule\":\"PRESERVE_ORIGINAL_BIGDECIMAL_NO_ROUNDING_OR_RESCALE\","
            + "\"strongDirectionRule\":\"INHERITED_FROM_DIRECTIONAL_ROUTE_NO_REINTERPRETATION\","
            + "\"sourceTargetInput\":\"ABSENT\","
            + "\"highLowReselection\":\"ABSENT\","
            + "\"endpointCloseFallback\":\"ABSENT\","
            + "\"eligibilityResolverInvocation\":\"ABSENT\","
            + "\"favorableExtremeSelectorInvocation\":\"ABSENT\","
            + "\"rawAggregation\":\"ABSENT\","
            + "\"methodologyActivation\":\"ABSENT\","
            + "\"inputFingerprint\":\"ABSENT\","
            + "\"persistence\":\"ABSENT\","
            + "\"aggregation\":\"ABSENT\","
            + "\"ranking\":\"ABSENT\","
            + "\"publication\":\"ABSENT\","
            + "\"fallbackBehavior\":\"ABSENT\"}";

    private static final String DEFINITION_HASH =
            "b91bf68958e42ad003b80973c74f9acc2dad8e4629f6a1905798df98aa8b5348";

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
