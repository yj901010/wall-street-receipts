package com.wallstreetreceipts.api.domain.outcome.directionalwinorchestration;

import java.util.Objects;

import com.wallstreetreceipts.api.domain.outcome.assetreturn.AssetReturnPolicyVersion;
import com.wallstreetreceipts.api.domain.outcome.assetreturn.AssetReturnResult;
import com.wallstreetreceipts.api.domain.outcome.direction.CallDirectionPolarityPolicyVersion;
import com.wallstreetreceipts.api.domain.outcome.observation.EndpointPriceResolution;
import com.wallstreetreceipts.api.domain.outcome.pricepair.AssetReturnPricePairResolution;
import com.wallstreetreceipts.api.domain.outcome.routing.CalculatorSideRouting;
import com.wallstreetreceipts.api.domain.outcome.routing.CalculatorSideRouting.DirectionalRoute;
import com.wallstreetreceipts.api.domain.outcome.routing.CalculatorSideRouting.NonDirectionalRoute;
import com.wallstreetreceipts.api.domain.outcome.targeteligibility.BasisForecastTermsEvidence;

/** Complete supplied and cross-correlated leaves for directional-win composition. */
public record DirectionalWinOrchestrationRequest(
        DirectionalWinOrchestrationPolicyVersion policyVersion,
        BasisForecastTermsEvidence termsEvidence,
        CalculatorSideRouting.Result sideRouting,
        AssetReturnResult assetReturnResult) {

    private static final String REQUIRED_POLARITY_HASH =
            "d83eccc92fedd7ba025745be2c8e78245bc308d0ff479467fa61afe543dc8a50";
    private static final String REQUIRED_ASSET_RETURN_HASH =
            "e5e61c4adcd6567bfc76f73114499578f09de2254dc39a2553f3c0e2eaf03486";

    public DirectionalWinOrchestrationRequest {
        Objects.requireNonNull(policyVersion, "policyVersion must not be null");
        if (policyVersion
                != DirectionalWinOrchestrationPolicyVersion
                        .SUPPLIED_LEAF_DIRECTIONAL_WIN_ORCHESTRATION_V1) {
            throw new IllegalArgumentException(
                    "policyVersion must be the directional-win orchestration V1 policy");
        }
        Objects.requireNonNull(termsEvidence, "termsEvidence must not be null");
        Objects.requireNonNull(sideRouting, "sideRouting must not be null");
        Objects.requireNonNull(assetReturnResult,
                "assetReturnResult must not be null");

        var polarityContext = switch (sideRouting) {
            case DirectionalRoute directional -> directional.source().context();
            case NonDirectionalRoute nonDirectional ->
                nonDirectional.source().context();
        };
        if (polarityContext.policyVersion()
                != CallDirectionPolarityPolicyVersion
                        .COLLAPSE_STRONG_DIRECTIONS_NEUTRAL_NON_DIRECTIONAL_V1
                || !REQUIRED_POLARITY_HASH.equals(
                        polarityContext.policyDefinitionHash())) {
            throw new IllegalArgumentException(
                    "sideRouting must preserve the required polarity V1 policy");
        }
        if (termsEvidence.direction() != polarityContext.direction()) {
            throw new IllegalArgumentException(
                    "termsEvidence direction must match the routed source direction");
        }

        var assetReturnContext = assetReturnContext(assetReturnResult);
        if (assetReturnContext.policyVersion()
                != AssetReturnPolicyVersion
                        .SIGNED_BASIS_DENOMINATOR_SCALE_12_HALF_EVEN_V1
                || !REQUIRED_ASSET_RETURN_HASH.equals(
                        assetReturnContext.policyDefinitionHash())) {
            throw new IllegalArgumentException(
                    "assetReturnResult must use the required asset-return V1 policy");
        }
        var pairContext = pricePairContext(
                assetReturnContext.pricePairResolution());
        var endpointContext = endpointContext(
                pairContext.endpointPriceResolution());
        var horizonBasis = endpointContext.horizonResolution()
                .window().context().basis();
        if (!termsEvidence.basis().equals(horizonBasis)) {
            throw new IllegalArgumentException(
                    "termsEvidence basis must match the asset-return horizon basis");
        }
        if (!termsEvidence.assetId().equals(endpointContext.binding().assetId())) {
            throw new IllegalArgumentException(
                    "termsEvidence asset must match the asset-return binding asset");
        }
        if (termsEvidence.availableAt().isAfter(endpointContext.evaluationAsOf())
                || termsEvidence.capturedAt().isAfter(
                        endpointContext.evaluationAsOf())) {
            throw new IllegalArgumentException(
                    "termsEvidence must be known by the asset-return evaluationAsOf");
        }
    }

    private static AssetReturnResult.CalculationContext assetReturnContext(
            AssetReturnResult result) {
        return switch (result) {
            case AssetReturnResult.Available available -> available.context();
            case AssetReturnResult.Unavailable unavailable ->
                unavailable.context();
        };
    }

    private static AssetReturnPricePairResolution.ResolutionContext pricePairContext(
            AssetReturnPricePairResolution resolution) {
        return switch (resolution) {
            case AssetReturnPricePairResolution.Resolved resolved ->
                resolved.context();
            case AssetReturnPricePairResolution.Unavailable unavailable ->
                unavailable.context();
        };
    }

    private static EndpointPriceResolution.ResolutionContext endpointContext(
            EndpointPriceResolution resolution) {
        return switch (resolution) {
            case EndpointPriceResolution.Resolved resolved -> resolved.context();
            case EndpointPriceResolution.Unavailable unavailable ->
                unavailable.context();
        };
    }
}
