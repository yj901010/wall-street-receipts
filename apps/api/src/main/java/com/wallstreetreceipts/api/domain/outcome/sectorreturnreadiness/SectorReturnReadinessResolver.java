package com.wallstreetreceipts.api.domain.outcome.sectorreturnreadiness;

import java.util.Objects;

import com.wallstreetreceipts.api.domain.outcome.sectorreferencepair
        .SectorReferenceLevelPairResolution;
import com.wallstreetreceipts.api.domain.outcome.sectorreturn.SectorReturnResult;
import com.wallstreetreceipts.api.domain.outcome.sectorreturnreadiness
        .SectorReturnReadinessResolution.ResolutionContext;

/** Deterministic readiness classification over one complete supplied leaf. */
public final class SectorReturnReadinessResolver {

    enum Classification {
        SETTLED,
        AWAITING_ENDPOINT,
        EVIDENCE_UNAVAILABLE
    }

    private SectorReturnReadinessResolver() {
    }

    public static SectorReturnReadinessResolution resolve(
            SectorReturnReadinessRequest request) {
        Objects.requireNonNull(request, "request must not be null");
        ResolutionContext context = new ResolutionContext(
                request.policyVersion(), request.policyVersion().definitionHash());

        return switch (classify(request.sourceResult())) {
            case SETTLED -> new SectorReturnReadinessResolution.Settled(
                    context, request.sourceResult());
            case AWAITING_ENDPOINT ->
                new SectorReturnReadinessResolution.AwaitingEndpoint(
                        context, request.sourceResult());
            case EVIDENCE_UNAVAILABLE ->
                new SectorReturnReadinessResolution.EvidenceUnavailable(
                        context, request.sourceResult());
        };
    }

    static void requireClassification(
            SectorReturnResult sourceResult,
            Classification expected) {
        Objects.requireNonNull(expected, "expected must not be null");
        if (classify(sourceResult) != expected) {
            throw new IllegalArgumentException(
                    "sourceResult does not match the result classification");
        }
    }

    private static Classification classify(SectorReturnResult sourceResult) {
        Objects.requireNonNull(sourceResult, "sourceResult must not be null");
        return switch (sourceResult) {
            case SectorReturnResult.Available ignored -> Classification.SETTLED;
            case SectorReturnResult.NotApplicable ignored -> Classification.SETTLED;
            case SectorReturnResult.EvidenceUnavailable unavailable ->
                    isExactAwaitingEndpointChain(unavailable)
                            ? Classification.AWAITING_ENDPOINT
                            : Classification.EVIDENCE_UNAVAILABLE;
            case SectorReturnResult.AssignmentUnavailable ignored ->
                    Classification.EVIDENCE_UNAVAILABLE;
            case SectorReturnResult.EndpointAnchorUnavailable ignored ->
                    Classification.EVIDENCE_UNAVAILABLE;
            case SectorReturnResult.OutputUnavailable ignored ->
                    Classification.EVIDENCE_UNAVAILABLE;
        };
    }

    private static boolean isExactAwaitingEndpointChain(
            SectorReturnResult.EvidenceUnavailable sourceResult) {
        if (!(sourceResult.context().referenceLevelPairResolution()
                instanceof SectorReferenceLevelPairResolution
                        .EvidenceUnavailable pair)) {
            return false;
        }
        return pair.reason()
                == SectorReferenceLevelPairResolution.UnavailableReason
                        .ENDPOINT_NOT_REACHED_AS_OF;
    }
}
