package com.wallstreetreceipts.api.domain.outcome.targethitreadiness;

import java.util.Objects;

import com.wallstreetreceipts.api.domain.outcome.targethitorchestration
        .TargetHitOrchestrationResolution;
import com.wallstreetreceipts.api.domain.outcome.targethitreadiness
        .TargetHitReadinessResolution.ResolutionContext;

/** Deterministic readiness classification over one complete supplied leaf. */
public final class TargetHitReadinessResolver {

    enum Classification {
        SETTLED,
        AWAITING_ENDPOINT,
        EVIDENCE_UNAVAILABLE
    }

    private TargetHitReadinessResolver() {
    }

    public static TargetHitReadinessResolution resolve(
            TargetHitReadinessRequest request) {
        Objects.requireNonNull(request, "request must not be null");
        ResolutionContext context = new ResolutionContext(
                request.policyVersion(), request.policyVersion().definitionHash());

        return switch (classify(request.sourceResult())) {
            case SETTLED -> new TargetHitReadinessResolution.Settled(
                    context, request.sourceResult());
            case AWAITING_ENDPOINT ->
                new TargetHitReadinessResolution.AwaitingEndpoint(
                        context, request.sourceResult());
            case EVIDENCE_UNAVAILABLE ->
                new TargetHitReadinessResolution.EvidenceUnavailable(
                        context, request.sourceResult());
        };
    }

    static void requireClassification(
            TargetHitOrchestrationResolution sourceResult,
            Classification expected) {
        Objects.requireNonNull(expected, "expected must not be null");
        if (classify(sourceResult) != expected) {
            throw new IllegalArgumentException(
                    "sourceResult does not match the result classification");
        }
    }

    private static Classification classify(
            TargetHitOrchestrationResolution sourceResult) {
        Objects.requireNonNull(sourceResult, "sourceResult must not be null");
        return switch (sourceResult) {
            case TargetHitOrchestrationResolution.Available ignored ->
                    Classification.SETTLED;
            case TargetHitOrchestrationResolution.NotApplicable ignored ->
                    Classification.SETTLED;
            case TargetHitOrchestrationResolution.Pending ignored ->
                    Classification.AWAITING_ENDPOINT;
            case TargetHitOrchestrationResolution.EligibilityUnavailable ignored ->
                    Classification.EVIDENCE_UNAVAILABLE;
            case TargetHitOrchestrationResolution.FavorableExtremeUnavailable ignored ->
                    Classification.EVIDENCE_UNAVAILABLE;
        };
    }
}
