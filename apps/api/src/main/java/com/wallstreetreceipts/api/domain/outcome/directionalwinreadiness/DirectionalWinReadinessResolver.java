package com.wallstreetreceipts.api.domain.outcome.directionalwinreadiness;

import java.util.Objects;

import com.wallstreetreceipts.api.domain.outcome.assetreturn.AssetReturnResult;
import com.wallstreetreceipts.api.domain.outcome.directionalwinorchestration
        .DirectionalWinOrchestrationResolution;
import com.wallstreetreceipts.api.domain.outcome.directionalwinreadiness
        .DirectionalWinReadinessResolution.ResolutionContext;
import com.wallstreetreceipts.api.domain.outcome.observation.EndpointPriceResolution;
import com.wallstreetreceipts.api.domain.outcome.pricepair.AssetReturnPricePairResolution;

/** Deterministic readiness classification over one complete supplied leaf. */
public final class DirectionalWinReadinessResolver {

    enum Classification {
        SETTLED,
        AWAITING_ENDPOINT,
        EVIDENCE_UNAVAILABLE
    }

    private DirectionalWinReadinessResolver() {
    }

    public static DirectionalWinReadinessResolution resolve(
            DirectionalWinReadinessRequest request) {
        Objects.requireNonNull(request, "request must not be null");
        ResolutionContext context = new ResolutionContext(
                request.policyVersion(), request.policyVersion().definitionHash());

        return switch (classify(request.sourceResolution())) {
            case SETTLED -> new DirectionalWinReadinessResolution.Settled(
                    context, request.sourceResolution());
            case AWAITING_ENDPOINT ->
                new DirectionalWinReadinessResolution.AwaitingEndpoint(
                        context, request.sourceResolution());
            case EVIDENCE_UNAVAILABLE ->
                new DirectionalWinReadinessResolution.EvidenceUnavailable(
                        context, request.sourceResolution());
        };
    }

    static void requireClassification(
            DirectionalWinOrchestrationResolution sourceResolution,
            Classification expected) {
        Objects.requireNonNull(expected, "expected must not be null");
        if (classify(sourceResolution) != expected) {
            throw new IllegalArgumentException(
                    "sourceResolution does not match the result classification");
        }
    }

    private static Classification classify(
            DirectionalWinOrchestrationResolution sourceResolution) {
        Objects.requireNonNull(sourceResolution,
                "sourceResolution must not be null");
        AssetReturnResult assetReturn = switch (sourceResolution) {
            case DirectionalWinOrchestrationResolution.Available available ->
                available.assetReturnResult();
            case DirectionalWinOrchestrationResolution.NotApplicable notApplicable ->
                notApplicable.assetReturnResult();
            case DirectionalWinOrchestrationResolution.AssetReturnUnavailable unavailable ->
                unavailable.assetReturnResult();
        };
        return switch (assetReturn) {
            case AssetReturnResult.Available ignored -> Classification.SETTLED;
            case AssetReturnResult.Unavailable unavailable ->
                isExactAwaitingEndpointChain(unavailable)
                        ? Classification.AWAITING_ENDPOINT
                        : Classification.EVIDENCE_UNAVAILABLE;
        };
    }

    private static boolean isExactAwaitingEndpointChain(
            AssetReturnResult.Unavailable assetReturn) {
        if (assetReturn.reason()
                != AssetReturnResult.UnavailableReason.PRICE_PAIR_UNAVAILABLE
                || assetReturn.pricePairReason()
                        != AssetReturnPricePairResolution.UnavailableReason
                                .ENDPOINT_PRICE_UNAVAILABLE
                || !(assetReturn.context().pricePairResolution()
                        instanceof AssetReturnPricePairResolution.Unavailable pair)
                || pair.reason()
                        != AssetReturnPricePairResolution.UnavailableReason
                                .ENDPOINT_PRICE_UNAVAILABLE
                || pair.endpointReason()
                        != EndpointPriceResolution.UnavailableReason
                                .ENDPOINT_NOT_REACHED_AS_OF
                || !(pair.context().endpointPriceResolution()
                        instanceof EndpointPriceResolution.Unavailable endpoint)) {
            return false;
        }
        return endpoint.reason()
                == EndpointPriceResolution.UnavailableReason
                        .ENDPOINT_NOT_REACHED_AS_OF;
    }
}
