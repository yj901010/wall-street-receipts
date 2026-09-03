package com.wallstreetreceipts.api.domain.outcome.benchmarkassignment;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

import com.wallstreetreceipts.api.domain.PersistentInstant;
import com.wallstreetreceipts.api.domain.outcome.horizon.OutcomeBasis;

/** Explicit request-scoped evidence for one point-in-time benchmark assignment. */
public record BenchmarkAssignmentRequest(
        BenchmarkAssignmentPolicyVersion policyVersion,
        OutcomeBasis basis,
        String assetId,
        Instant evaluationAsOf,
        List<BenchmarkAssetClassificationEvidence> classificationCandidates,
        List<BenchmarkAssignmentEvidence> assignmentCandidates) {

    public BenchmarkAssignmentRequest {
        Objects.requireNonNull(policyVersion, "policyVersion must not be null");
        if (policyVersion
                != BenchmarkAssignmentPolicyVersion
                        .POINT_IN_TIME_EXPLICIT_US_EQUITY_ASSET_SPX_ASSIGNMENT_V1) {
            throw new IllegalArgumentException(
                    "policyVersion must be the benchmark-assignment V1 policy");
        }
        Objects.requireNonNull(basis, "basis must not be null");
        BenchmarkAssetClassificationEvidence.requireCanonicalText(assetId, "assetId");
        PersistentInstant.requireMicrosecondPrecision(
                evaluationAsOf, "evaluationAsOf");
        if (evaluationAsOf.isBefore(basis.eventTime())) {
            throw new IllegalArgumentException(
                    "evaluationAsOf must not precede basis.eventTime");
        }
        Objects.requireNonNull(classificationCandidates,
                "classificationCandidates must not be null");
        for (BenchmarkAssetClassificationEvidence candidate
                : classificationCandidates) {
            Objects.requireNonNull(candidate,
                    "classificationCandidates must not contain null");
        }
        classificationCandidates = List.copyOf(classificationCandidates);
        Objects.requireNonNull(assignmentCandidates,
                "assignmentCandidates must not be null");
        for (BenchmarkAssignmentEvidence candidate : assignmentCandidates) {
            Objects.requireNonNull(candidate,
                    "assignmentCandidates must not contain null");
        }
        assignmentCandidates = List.copyOf(assignmentCandidates);
    }
}
