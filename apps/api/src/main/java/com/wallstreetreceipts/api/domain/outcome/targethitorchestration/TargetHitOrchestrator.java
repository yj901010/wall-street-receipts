package com.wallstreetreceipts.api.domain.outcome.targethitorchestration;

import java.util.Objects;

import com.wallstreetreceipts.api.domain.outcome.calculation.TargetHitCalculator;
import com.wallstreetreceipts.api.domain.outcome.calculation.TargetHitInput;
import com.wallstreetreceipts.api.domain.outcome.calculation.TargetHitResult;
import com.wallstreetreceipts.api.domain.outcome.favorableextreme.FavorableExtremeResolution;
import com.wallstreetreceipts.api.domain.outcome.routing.CalculatorSideRouting.DirectionalRoute;
import com.wallstreetreceipts.api.domain.outcome.targeteligibility.TargetEligibilityResolution;
import com.wallstreetreceipts.api.domain.outcome.targethitorchestration.TargetHitOrchestrationResolution.ResolutionContext;

/** Pure composition of supplied PIT leaf resolutions and target-hit comparison. */
public final class TargetHitOrchestrator {

    private TargetHitOrchestrator() {
    }

    public static TargetHitOrchestrationResolution orchestrate(
            TargetHitOrchestrationRequest request) {
        Objects.requireNonNull(request, "request must not be null");
        ResolutionContext context = new ResolutionContext(
                request.policyVersion(), request.policyVersion().definitionHash());

        return switch (request.eligibilityResolution()) {
            case TargetEligibilityResolution.Pending pending ->
                new TargetHitOrchestrationResolution.Pending(context, pending);
            case TargetEligibilityResolution.NotApplicable notApplicable ->
                new TargetHitOrchestrationResolution.NotApplicable(
                        context, notApplicable);
            case TargetEligibilityResolution.Unavailable unavailable ->
                new TargetHitOrchestrationResolution.EligibilityUnavailable(
                        context, unavailable);
            case TargetEligibilityResolution.ReadyForWindowEvidence ready ->
                orchestrateReady(
                        context, ready, request.favorableExtremeResolution());
        };
    }

    private static TargetHitOrchestrationResolution orchestrateReady(
            ResolutionContext context,
            TargetEligibilityResolution.ReadyForWindowEvidence ready,
            FavorableExtremeResolution favorableExtremeResolution) {
        return switch (favorableExtremeResolution) {
            case FavorableExtremeResolution.Unavailable unavailable ->
                new TargetHitOrchestrationResolution.FavorableExtremeUnavailable(
                        context, unavailable);
            case FavorableExtremeResolution.Resolved resolved -> {
                var route = (DirectionalRoute) ready.evidence().sideRouting();
                TargetHitResult result = TargetHitCalculator.calculate(
                        new TargetHitInput(
                                route.targetHitSide(),
                                ready.evidence().targetEvidence().target(),
                                resolved.favorableExtreme().value()));
                if (!(result instanceof TargetHitResult.Available available)) {
                    throw new IllegalStateException(
                            "complete upstream evidence must produce an available target hit");
                }
                yield new TargetHitOrchestrationResolution.Available(
                        context, resolved, available);
            }
        };
    }
}
