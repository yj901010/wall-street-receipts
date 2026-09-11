package com.wallstreetreceipts.api.application.scoring;

import java.util.Objects;
import java.util.Optional;
import com.wallstreetreceipts.api.domain.outcome.benchmarkassignment.*;
import com.wallstreetreceipts.api.domain.outcome.benchmarkreferencepair.*;
import com.wallstreetreceipts.api.domain.outcome.benchmarkreturn.*;
import com.wallstreetreceipts.api.domain.outcome.benchmarkreturnreadiness.*;
import com.wallstreetreceipts.api.domain.outcome.sectorassignment.*;
import com.wallstreetreceipts.api.domain.outcome.sectorreferencepair.*;
import com.wallstreetreceipts.api.domain.outcome.sectorreturn.*;
import com.wallstreetreceipts.api.domain.outcome.sectorreturnreadiness.*;
import com.wallstreetreceipts.api.domain.outcome.observation.EndpointPriceResolution;
import com.wallstreetreceipts.api.domain.outcome.targeterror.TargetErrorResult;
import com.wallstreetreceipts.api.domain.outcome.targeterrorreadiness.TargetErrorReadinessResolution;

/** Pure DEMO application composition; no registry activation, persistence or public writer. */
public final class ComparativeScoringEvaluator {
    public Receipt evaluate(ComparativeScoringInput input) {
        return evaluate(ComparativeScoringMethodology.ID, ComparativeScoringMethodology.VERSION,
                ComparativeScoringMethodology.definitionHash(), input);
    }

    public Receipt evaluate(String id, String version, String hash, ComparativeScoringInput input) {
        if (!ComparativeScoringMethodology.ID.equals(id) || !ComparativeScoringMethodology.VERSION.equals(version)
                || !ComparativeScoringMethodology.definitionHash().equals(hash)) {
            throw new IllegalArgumentException("Unsupported comparative methodology identity");
        }
        Objects.requireNonNull(input, "input");
        String fingerprint = ComparativeScoringInputCodec.hash(ComparativeScoringInputCodec.encode(input));
        var endpoint = new EndpointScoringEvaluator().evaluate(input.endpoint());
        if (endpoint.targetError().isEmpty()) return new Receipt(input, fingerprint, endpoint, null, null);
        // Structural extraction only: do not classify target-error reasons or rerun its endpoint selector.
        var anchor = anchor(endpoint.targetError().orElseThrow());
        var b = input.benchmark();
        var benchmarkAssignment = BenchmarkAssignmentSelector.select(b.assignment());
        var benchmarkPair = BenchmarkReferenceLevelPairSelector.select(new BenchmarkReferenceLevelPairRequest(
                ComparativeScoringMethodology.BENCHMARK_PAIR, benchmarkAssignment, anchor, b.referenceIndexCandidates(),
                b.basisLevelCandidates(), b.endpointLevelCandidates(), b.divisorContinuityCandidates()));
        var benchmarkReturn = BenchmarkReturnCalculator.calculate(new BenchmarkReturnInput(
                ComparativeScoringMethodology.BENCHMARK_RETURN, benchmarkPair));
        var benchmark = BenchmarkReturnReadinessResolver.resolve(new BenchmarkReturnReadinessRequest(
                ComparativeScoringMethodology.BENCHMARK_READINESS, benchmarkReturn));
        var s = input.sector();
        var sectorAssignment = SectorAssignmentSelector.select(s.assignment());
        var sectorPair = SectorReferenceLevelPairSelector.select(new SectorReferenceLevelPairRequest(
                ComparativeScoringMethodology.SECTOR_PAIR, sectorAssignment, anchor, s.referenceIndexCandidates(),
                s.basisLevelCandidates(), s.endpointLevelCandidates(), s.divisorContinuityCandidates()));
        var sectorReturn = SectorReturnCalculator.calculate(new SectorReturnInput(
                ComparativeScoringMethodology.SECTOR_RETURN, sectorPair));
        var sector = SectorReturnReadinessResolver.resolve(new SectorReturnReadinessRequest(
                ComparativeScoringMethodology.SECTOR_READINESS, sectorReturn));
        return new Receipt(input, fingerprint, endpoint, benchmark, sector);
    }

    private static EndpointPriceResolution anchor(TargetErrorReadinessResolution readiness) {
        TargetErrorResult result = switch (readiness) {
            case TargetErrorReadinessResolution.Settled r -> r.sourceResult();
            case TargetErrorReadinessResolution.AwaitingEndpoint r -> r.sourceResult();
            case TargetErrorReadinessResolution.EvidenceUnavailable r -> r.sourceResult();
        };
        return switch (result) {
            case TargetErrorResult.Available r -> r.context().endpointPriceResolution();
            case TargetErrorResult.Unavailable r -> r.context().endpointPriceResolution();
        };
    }

    /** Only this evaluator can attest raw-request-to-result invocation. */
    public static final class Receipt {
        private final ComparativeScoringInput input;
        private final String fingerprint;
        private final EndpointScoringEvaluator.Receipt endpoint;
        private final BenchmarkReturnReadinessResolution benchmark;
        private final SectorReturnReadinessResolution sector;
        private Receipt(ComparativeScoringInput input, String fingerprint, EndpointScoringEvaluator.Receipt endpoint,
                BenchmarkReturnReadinessResolution benchmark, SectorReturnReadinessResolution sector) {
            this.input = input; this.fingerprint = fingerprint; this.endpoint = endpoint;
            this.benchmark = benchmark; this.sector = sector;
        }
        public ComparativeScoringInput input() { return input; }
        public String inputFingerprint() { return fingerprint; }
        public String methodologyId() { return ComparativeScoringMethodology.ID; }
        public String methodologyVersion() { return ComparativeScoringMethodology.VERSION; }
        public String methodologyDefinitionHash() { return ComparativeScoringMethodology.definitionHash(); }
        public EndpointScoringEvaluator.Receipt endpoint() { return endpoint; }
        public Optional<BenchmarkReturnReadinessResolution> benchmarkReturn() { return Optional.ofNullable(benchmark); }
        public Optional<SectorReturnReadinessResolution> sectorReturn() { return Optional.ofNullable(sector); }
        public boolean dataComplete() { return false; }
    }
}
