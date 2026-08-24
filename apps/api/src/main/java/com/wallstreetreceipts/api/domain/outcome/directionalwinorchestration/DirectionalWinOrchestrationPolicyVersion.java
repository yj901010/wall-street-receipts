package com.wallstreetreceipts.api.domain.outcome.directionalwinorchestration;

import java.nio.charset.StandardCharsets;

/** Versioned correlation and composition of supplied directional-win leaves. */
public enum DirectionalWinOrchestrationPolicyVersion {
    SUPPLIED_LEAF_DIRECTIONAL_WIN_ORCHESTRATION_V1;

    private static final String CANONICAL_DEFINITION =
            "{\"policyVersion\":\"SUPPLIED_LEAF_DIRECTIONAL_WIN_ORCHESTRATION_V1\","
            + "\"requiredPolarityPolicyVersion\":\"COLLAPSE_STRONG_DIRECTIONS_NEUTRAL_NON_DIRECTIONAL_V1\","
            + "\"requiredPolarityPolicyDefinitionHash\":\"d83eccc92fedd7ba025745be2c8e78245bc308d0ff479467fa61afe543dc8a50\","
            + "\"requiredAssetReturnPolicyVersion\":\"SIGNED_BASIS_DENOMINATOR_SCALE_12_HALF_EVEN_V1\","
            + "\"requiredAssetReturnPolicyDefinitionHash\":\"e5e61c4adcd6567bfc76f73114499578f09de2254dc39a2553f3c0e2eaf03486\","
            + "\"requestFields\":[\"policyVersion\",\"termsEvidence\",\"sideRouting\",\"assetReturnResult\"],"
            + "\"requestPresence\":\"ALL_FIELDS_NON_NULL_INCLUDING_NON_DIRECTIONAL_ASSET_RETURN\","
            + "\"resolutionContextFields\":[\"policyVersion\",\"policyDefinitionHash\"],"
            + "\"resultVariants\":{\"Available\":[\"context\",\"termsEvidence\",\"sideRouting\",\"assetReturnResult\",\"directionalWinResult\"],\"NotApplicable\":[\"context\",\"termsEvidence\",\"sideRouting\",\"assetReturnResult\"],\"AssetReturnUnavailable\":[\"context\",\"termsEvidence\",\"sideRouting\",\"assetReturnResult\"]},"
            + "\"termsInput\":\"COMPLETE_SUPPLIED_BASIS_FORECAST_TERMS_EVIDENCE\","
            + "\"routingInput\":\"COMPLETE_SUPPLIED_CALCULATOR_SIDE_ROUTING_RESULT\","
            + "\"assetReturnInput\":\"COMPLETE_SUPPLIED_ASSET_RETURN_RESULT_REQUIRED_FOR_ALL_ROUTING_BRANCHES\","
            + "\"leafAttestationBoundary\":\"LOCAL_CONSISTENCY_ONLY_NO_REQUEST_MEMBERSHIP_PIT_FILTERING_OR_PRODUCER_INVOCATION_CLAIM\","
            + "\"routingPolicyRule\":\"SOURCE_POLARITY_CONTEXT_MUST_USE_REQUIRED_POLICY_AND_HASH\","
            + "\"directionCorrelation\":\"termsEvidence.direction==sideRouting.source.context.direction_EXACT_CANONICAL_DIRECTION\","
            + "\"basisCorrelation\":\"termsEvidence.basis==assetReturnResult.context.pricePairResolution.context.endpointPriceResolution.context.horizonResolution.window.context.basis_BY_WHOLE_RECORD_EQUALITY\","
            + "\"assetCorrelation\":\"termsEvidence.assetId==assetReturnResult.context.pricePairResolution.context.endpointPriceResolution.context.binding.assetId\","
            + "\"evaluationAsOfSource\":\"assetReturnResult.context.pricePairResolution.context.endpointPriceResolution.context.evaluationAsOf\","
            + "\"termsVisibilityRule\":\"termsEvidence.availableAt<=evaluationAsOf&&termsEvidence.capturedAt<=evaluationAsOf\","
            + "\"correlationFailure\":\"REJECT_REQUEST\","
            + "\"branchPrecedence\":[\"NonDirectionalRoute\",\"DirectionalRoute+AssetReturn.Unavailable\",\"DirectionalRoute+AssetReturn.Available\"],"
            + "\"branchMapping\":{\"NonDirectionalRoute+AnyAssetReturnResult\":\"NOT_APPLICABLE_PRESERVE_ALL_SUPPLIED_LEAVES\",\"DirectionalRoute+AssetReturn.Unavailable\":\"PRESERVE_ASSET_RETURN_UNAVAILABLE\",\"DirectionalRoute+AssetReturn.Available\":\"INVOKE_DIRECTIONAL_WIN\"},"
            + "\"notApplicableReasonSource\":\"PRESERVED_NON_DIRECTIONAL_ROUTE_SOURCE_REASON\","
            + "\"nestedReasonRule\":\"PRESERVE_EXACT_TYPED_ASSET_RETURN_PRICE_PAIR_AND_ENDPOINT_RESOLUTIONS_WITHOUT_REASON_MAPPING\","
            + "\"resolvedSideSource\":\"sideRouting.directionalWinSide\","
            + "\"resolvedAssetReturnSource\":\"assetReturnResult.assetReturn\","
            + "\"directionalWinInputFields\":[\"side\",\"assetReturn\"],"
            + "\"calculator\":\"DirectionalWinCalculator.calculate\","
            + "\"calculatorInvocation\":\"EXACTLY_ONCE_ONLY_FOR_DIRECTIONAL_AND_ASSET_RETURN_AVAILABLE\","
            + "\"calculatorUnavailableRule\":\"INVARIANT_VIOLATION_FAIL_CLOSED_WITHOUT_RESULT\","
            + "\"comparison\":{\"BULLISH\":\"assetReturn.compareTo(ZERO)>0\",\"BEARISH\":\"assetReturn.compareTo(ZERO)<0\"},"
            + "\"zeroRule\":\"MISS_FOR_BOTH_SIDES\","
            + "\"selectedValueRule\":\"PRESERVE_ORIGINAL_BIGDECIMAL_NO_ROUNDING_OR_RESCALE\","
            + "\"targetDispositionUse\":\"ABSENT\","
            + "\"polarityResolverInvocation\":\"ABSENT\","
            + "\"calculatorSideRoutingInvocation\":\"ABSENT\","
            + "\"assetReturnCalculatorInvocation\":\"ABSENT\","
            + "\"pricePairSelectorInvocation\":\"ABSENT\","
            + "\"endpointSelectorInvocation\":\"ABSENT\","
            + "\"returnRecalculation\":\"ABSENT\","
            + "\"fallbackBehavior\":\"ABSENT\","
            + "\"methodologyActivation\":\"ABSENT\","
            + "\"inputFingerprint\":\"ABSENT\","
            + "\"persistence\":\"ABSENT\","
            + "\"aggregation\":\"ABSENT\","
            + "\"ranking\":\"ABSENT\","
            + "\"publication\":\"ABSENT\"}";

    private static final String DEFINITION_HASH =
            "51429c7601d4807162855f08c680d1e6bb7895f87fc108e141e5ad3a3ab25bcb";

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
