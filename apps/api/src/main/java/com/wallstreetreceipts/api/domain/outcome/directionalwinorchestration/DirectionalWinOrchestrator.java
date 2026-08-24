package com.wallstreetreceipts.api.domain.outcome.directionalwinorchestration;

import java.util.Objects;

import com.wallstreetreceipts.api.domain.outcome.assetreturn.AssetReturnResult;
import com.wallstreetreceipts.api.domain.outcome.calculation.DirectionalWinCalculator;
import com.wallstreetreceipts.api.domain.outcome.calculation.DirectionalWinInput;
import com.wallstreetreceipts.api.domain.outcome.calculation.DirectionalWinResult;
import com.wallstreetreceipts.api.domain.outcome.directionalwinorchestration
        .DirectionalWinOrchestrationResolution.ResolutionContext;
import com.wallstreetreceipts.api.domain.outcome.routing.CalculatorSideRouting.DirectionalRoute;
import com.wallstreetreceipts.api.domain.outcome.routing.CalculatorSideRouting.NonDirectionalRoute;

/** Pure composition of supplied correlated leaves and directional-win sign. */
public final class DirectionalWinOrchestrator {

    private DirectionalWinOrchestrator() {
    }

    public static DirectionalWinOrchestrationResolution orchestrate(
            DirectionalWinOrchestrationRequest request) {
        Objects.requireNonNull(request, "request must not be null");
        ResolutionContext context = new ResolutionContext(
                request.policyVersion(), request.policyVersion().definitionHash());

        return switch (request.sideRouting()) {
            case NonDirectionalRoute nonDirectional ->
                new DirectionalWinOrchestrationResolution.NotApplicable(
                        context, request.termsEvidence(), nonDirectional,
                        request.assetReturnResult());
            case DirectionalRoute directional -> orchestrateDirectional(
                    context, request, directional);
        };
    }

    private static DirectionalWinOrchestrationResolution orchestrateDirectional(
            ResolutionContext context,
            DirectionalWinOrchestrationRequest request,
            DirectionalRoute directional) {
        return switch (request.assetReturnResult()) {
            case AssetReturnResult.Unavailable unavailable ->
                new DirectionalWinOrchestrationResolution.AssetReturnUnavailable(
                        context, request.termsEvidence(), directional, unavailable);
            case AssetReturnResult.Available available -> {
                DirectionalWinResult result = DirectionalWinCalculator.calculate(
                        new DirectionalWinInput(
                                directional.directionalWinSide(),
                                available.assetReturn()));
                if (!(result instanceof DirectionalWinResult.Available directionalWin)) {
                    throw new IllegalStateException(
                            "complete upstream evidence must produce an available directional win");
                }
                yield new DirectionalWinOrchestrationResolution.Available(
                        context, request.termsEvidence(), directional, available,
                        directionalWin);
            }
        };
    }
}
