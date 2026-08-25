package com.wallstreetreceipts.api.domain.outcome.benchmarkreturnreadiness;

import java.util.Objects;

import com.wallstreetreceipts.api.domain.outcome.benchmarkreferencepair
        .BenchmarkReferenceLevelPairResolution;
import com.wallstreetreceipts.api.domain.outcome.benchmarkreturn
        .BenchmarkReturnResult;
import com.wallstreetreceipts.api.domain.outcome.benchmarkreturnreadiness
        .BenchmarkReturnReadinessResolution.ResolutionContext;

/** Deterministic readiness classification over one complete supplied leaf. */
public final class BenchmarkReturnReadinessResolver {

    enum Classification {
        SETTLED,
        AWAITING_ENDPOINT,
        EVIDENCE_UNAVAILABLE
    }

    private BenchmarkReturnReadinessResolver() {
    }

    public static BenchmarkReturnReadinessResolution resolve(
            BenchmarkReturnReadinessRequest request) {
        Objects.requireNonNull(request, "request must not be null");
        ResolutionContext context = new ResolutionContext(
                request.policyVersion(), request.policyVersion().definitionHash());

        return switch (classify(request.sourceResult())) {
            case SETTLED -> new BenchmarkReturnReadinessResolution.Settled(
                    context, request.sourceResult());
            case AWAITING_ENDPOINT ->
                new BenchmarkReturnReadinessResolution.AwaitingEndpoint(
                        context, request.sourceResult());
            case EVIDENCE_UNAVAILABLE ->
                new BenchmarkReturnReadinessResolution.EvidenceUnavailable(
                        context, request.sourceResult());
        };
    }

    static void requireClassification(
            BenchmarkReturnResult sourceResult,
            Classification expected) {
        Objects.requireNonNull(expected, "expected must not be null");
        if (classify(sourceResult) != expected) {
            throw new IllegalArgumentException(
                    "sourceResult does not match the result classification");
        }
    }

    private static Classification classify(BenchmarkReturnResult sourceResult) {
        Objects.requireNonNull(sourceResult, "sourceResult must not be null");
        return switch (sourceResult) {
            case BenchmarkReturnResult.Available ignored -> Classification.SETTLED;
            case BenchmarkReturnResult.NotApplicable ignored ->
                    Classification.SETTLED;
            case BenchmarkReturnResult.EvidenceUnavailable unavailable ->
                isExactAwaitingEndpointChain(unavailable)
                        ? Classification.AWAITING_ENDPOINT
                        : Classification.EVIDENCE_UNAVAILABLE;
            case BenchmarkReturnResult.AssignmentUnavailable ignored ->
                    Classification.EVIDENCE_UNAVAILABLE;
            case BenchmarkReturnResult.EndpointAnchorUnavailable ignored ->
                    Classification.EVIDENCE_UNAVAILABLE;
            case BenchmarkReturnResult.OutputUnavailable ignored ->
                    Classification.EVIDENCE_UNAVAILABLE;
        };
    }

    private static boolean isExactAwaitingEndpointChain(
            BenchmarkReturnResult.EvidenceUnavailable sourceResult) {
        if (!(sourceResult.context().referenceLevelPairResolution()
                instanceof BenchmarkReferenceLevelPairResolution
                        .EvidenceUnavailable pair)) {
            return false;
        }
        return pair.reason()
                == BenchmarkReferenceLevelPairResolution.UnavailableReason
                        .ENDPOINT_NOT_REACHED_AS_OF;
    }
}
