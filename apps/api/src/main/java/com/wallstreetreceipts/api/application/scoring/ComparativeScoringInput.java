package com.wallstreetreceipts.api.application.scoring;

import java.util.List;
import java.util.Objects;
import com.wallstreetreceipts.api.domain.outcome.benchmarkassignment.BenchmarkAssignmentRequest;
import com.wallstreetreceipts.api.domain.outcome.benchmarkreferencepair.*;
import com.wallstreetreceipts.api.domain.outcome.sectorassignment.SectorAssignmentRequest;
import com.wallstreetreceipts.api.domain.outcome.sectorreferencepair.*;

/** Explicit synthetic evidence only. No supplied calculation/selection result is accepted. */
public record ComparativeScoringInput(EndpointScoringInput endpoint, Benchmark benchmark, Sector sector) {
    public ComparativeScoringInput {
        Objects.requireNonNull(endpoint, "endpoint"); // ADR-080 enforces DEMO and its own bounds.
        Objects.requireNonNull(benchmark, "benchmark");
        Objects.requireNonNull(sector, "sector");
        var b = benchmark.assignment();
        var s = sector.assignment();
        if (!endpoint.horizon().basis().equals(b.basis()) || !b.basis().equals(s.basis())
                || !endpoint.binding().assetId().equals(b.assetId()) || !b.assetId().equals(s.assetId())
                || !endpoint.evaluationAsOf().equals(b.evaluationAsOf()) || !b.evaluationAsOf().equals(s.evaluationAsOf())) {
            throw new IllegalArgumentException("Comparative requests must share the endpoint basis, asset and as-of");
        }
    }

    public record Benchmark(BenchmarkAssignmentRequest assignment,
            List<BenchmarkReferenceIndexEvidence> referenceIndexCandidates,
            List<BenchmarkReferenceLevelObservation> basisLevelCandidates,
            List<BenchmarkReferenceLevelObservation> endpointLevelCandidates,
            List<BenchmarkIndexDivisorContinuityEvidence> divisorContinuityCandidates) {
        public Benchmark {
            Objects.requireNonNull(assignment, "assignment");
            bounded(assignment.classificationCandidates());
            bounded(assignment.assignmentCandidates());
            referenceIndexCandidates = typed(referenceIndexCandidates, BenchmarkReferenceIndexEvidence.class);
            basisLevelCandidates = typed(basisLevelCandidates, BenchmarkReferenceLevelObservation.class);
            endpointLevelCandidates = typed(endpointLevelCandidates, BenchmarkReferenceLevelObservation.class);
            divisorContinuityCandidates = typed(divisorContinuityCandidates, BenchmarkIndexDivisorContinuityEvidence.class);
        }
    }

    public record Sector(SectorAssignmentRequest assignment,
            List<SectorReferenceIndexEvidence> referenceIndexCandidates,
            List<SectorReferenceLevelObservation> basisLevelCandidates,
            List<SectorReferenceLevelObservation> endpointLevelCandidates,
            List<SectorIndexDivisorContinuityEvidence> divisorContinuityCandidates) {
        public Sector {
            Objects.requireNonNull(assignment, "assignment");
            bounded(assignment.classificationCandidates());
            bounded(assignment.membershipCandidates());
            bounded(assignment.mappingCandidates());
            referenceIndexCandidates = typed(referenceIndexCandidates, SectorReferenceIndexEvidence.class);
            basisLevelCandidates = typed(basisLevelCandidates, SectorReferenceLevelObservation.class);
            endpointLevelCandidates = typed(endpointLevelCandidates, SectorReferenceLevelObservation.class);
            divisorContinuityCandidates = typed(divisorContinuityCandidates, SectorIndexDivisorContinuityEvidence.class);
        }
    }

    private static <T> List<T> typed(List<T> values, Class<T> type) {
        var copy = bounded(values);
        if (copy.stream().anyMatch(value -> !type.isInstance(value))) {
            throw new IllegalArgumentException("Wrong comparative candidate type");
        }
        return copy;
    }

    private static <T> List<T> bounded(List<T> values) {
        Objects.requireNonNull(values, "candidates");
        if (values.size() > 4096) throw new IllegalArgumentException("Too many comparative candidates");
        return List.copyOf(values);
    }
}
