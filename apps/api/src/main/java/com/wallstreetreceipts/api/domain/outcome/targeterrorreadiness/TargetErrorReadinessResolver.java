package com.wallstreetreceipts.api.domain.outcome.targeterrorreadiness;

import java.util.Objects;

import com.wallstreetreceipts.api.domain.outcome.observation.EndpointPriceResolution;
import com.wallstreetreceipts.api.domain.outcome.targeterror.TargetErrorResult;
import com.wallstreetreceipts.api.domain.outcome.targeterrorreadiness
        .TargetErrorReadinessResolution.ResolutionContext;

/** Deterministic readiness classification over one complete supplied leaf. */
public final class TargetErrorReadinessResolver {

    enum Classification {
        SETTLED,
        AWAITING_ENDPOINT,
        EVIDENCE_UNAVAILABLE
    }

    private TargetErrorReadinessResolver() {
    }

    public static TargetErrorReadinessResolution resolve(
            TargetErrorReadinessRequest request) {
        Objects.requireNonNull(request, "request must not be null");
        ResolutionContext context = new ResolutionContext(
                request.policyVersion(), request.policyVersion().definitionHash());

        return switch (classify(request.sourceResult())) {
            case SETTLED -> new TargetErrorReadinessResolution.Settled(
                    context, request.sourceResult());
            case AWAITING_ENDPOINT ->
                new TargetErrorReadinessResolution.AwaitingEndpoint(
                        context, request.sourceResult());
            case EVIDENCE_UNAVAILABLE ->
                new TargetErrorReadinessResolution.EvidenceUnavailable(
                        context, request.sourceResult());
        };
    }

    static void requireClassification(
            TargetErrorResult sourceResult,
            Classification expected) {
        Objects.requireNonNull(expected, "expected must not be null");
        if (classify(sourceResult) != expected) {
            throw new IllegalArgumentException(
                    "sourceResult does not match the result classification");
        }
    }

    private static Classification classify(TargetErrorResult sourceResult) {
        Objects.requireNonNull(sourceResult, "sourceResult must not be null");
        return switch (sourceResult) {
            case TargetErrorResult.Available ignored -> Classification.SETTLED;
            case TargetErrorResult.Unavailable unavailable ->
                isExactAwaitingEndpointChain(unavailable)
                        ? Classification.AWAITING_ENDPOINT
                        : Classification.EVIDENCE_UNAVAILABLE;
        };
    }

    private static boolean isExactAwaitingEndpointChain(
            TargetErrorResult.Unavailable unavailable) {
        if (unavailable.reason()
                != TargetErrorResult.UnavailableReason
                        .ENDPOINT_PRICE_UNAVAILABLE
                || unavailable.endpointReason()
                        != EndpointPriceResolution.UnavailableReason
                                .ENDPOINT_NOT_REACHED_AS_OF
                || !(unavailable.context().endpointPriceResolution()
                        instanceof EndpointPriceResolution.Unavailable endpoint)) {
            return false;
        }
        return endpoint.reason()
                == EndpointPriceResolution.UnavailableReason
                        .ENDPOINT_NOT_REACHED_AS_OF;
    }
}
