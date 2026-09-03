package com.wallstreetreceipts.api.domain.outcome.benchmarkreturn;

import java.util.Objects;

import com.wallstreetreceipts.api.domain.outcome.benchmarkreferencepair
        .BenchmarkReferenceLevelPairPolicyVersion;
import com.wallstreetreceipts.api.domain.outcome.benchmarkreferencepair
        .BenchmarkReferenceLevelPairResolution;

/** One complete benchmark reference-level-pair resolution supplied to the return leaf. */
public record BenchmarkReturnInput(
        BenchmarkReturnPolicyVersion policyVersion,
        BenchmarkReferenceLevelPairResolution referenceLevelPairResolution) {

    private static final String REQUIRED_REFERENCE_LEVEL_PAIR_POLICY_HASH =
            "2394b535c1061d32c647504a303b6f1e4ec2fe88e6017d9ff335d12087a5f73d";

    public BenchmarkReturnInput {
        Objects.requireNonNull(policyVersion, "policyVersion must not be null");
        if (policyVersion
                != BenchmarkReturnPolicyVersion
                        .SIGNED_BENCHMARK_BASIS_LEVEL_DENOMINATOR_SCALE_12_HALF_EVEN_V1) {
            throw new IllegalArgumentException(
                    "policyVersion must be the benchmark-return V1 policy");
        }
        Objects.requireNonNull(referenceLevelPairResolution,
                "referenceLevelPairResolution must not be null");
        var pairContext = pairContext(referenceLevelPairResolution);
        if (pairContext.policyVersion()
                != BenchmarkReferenceLevelPairPolicyVersion
                        .POINT_IN_TIME_EXACT_BENCHMARK_PRICE_INDEX_LEVEL_PAIR_V1
                || !REQUIRED_REFERENCE_LEVEL_PAIR_POLICY_HASH.equals(
                        pairContext.policyDefinitionHash())) {
            throw new IllegalArgumentException(
                    "referenceLevelPairResolution must use the required benchmark pair V1 policy");
        }
    }

    static BenchmarkReferenceLevelPairResolution.ResolutionContext pairContext(
            BenchmarkReferenceLevelPairResolution resolution) {
        return switch (resolution) {
            case BenchmarkReferenceLevelPairResolution.Resolved resolved ->
                    resolved.context();
            case BenchmarkReferenceLevelPairResolution.NotApplicable notApplicable ->
                    notApplicable.context();
            case BenchmarkReferenceLevelPairResolution.AssignmentUnavailable unavailable ->
                    unavailable.context();
            case BenchmarkReferenceLevelPairResolution.EndpointAnchorUnavailable unavailable ->
                    unavailable.context();
            case BenchmarkReferenceLevelPairResolution.EvidenceUnavailable unavailable ->
                    unavailable.context();
        };
    }
}
