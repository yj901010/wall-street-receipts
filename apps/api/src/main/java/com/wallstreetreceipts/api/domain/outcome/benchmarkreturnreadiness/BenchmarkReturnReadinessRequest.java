package com.wallstreetreceipts.api.domain.outcome.benchmarkreturnreadiness;

import java.util.Objects;

import com.wallstreetreceipts.api.domain.outcome.benchmarkreturn
        .BenchmarkReturnPolicyVersion;
import com.wallstreetreceipts.api.domain.outcome.benchmarkreturn
        .BenchmarkReturnResult;

/** One complete supplied benchmark-return result to classify. */
public record BenchmarkReturnReadinessRequest(
        BenchmarkReturnReadinessPolicyVersion policyVersion,
        BenchmarkReturnResult sourceResult) {

    private static final String REQUIRED_SOURCE_HASH =
            "96d0aab8e8e784b80a12b16c99f6ba8c5f44eff7a342fd14c075b944a0a7de79";

    public BenchmarkReturnReadinessRequest {
        Objects.requireNonNull(policyVersion, "policyVersion must not be null");
        if (policyVersion
                != BenchmarkReturnReadinessPolicyVersion
                        .SUPPLIED_LEAF_BENCHMARK_RETURN_READINESS_V1) {
            throw new IllegalArgumentException(
                    "policyVersion must be the benchmark-return readiness V1 policy");
        }
        Objects.requireNonNull(sourceResult, "sourceResult must not be null");
        var sourceContext = switch (sourceResult) {
            case BenchmarkReturnResult.Available available -> available.context();
            case BenchmarkReturnResult.NotApplicable notApplicable ->
                    notApplicable.context();
            case BenchmarkReturnResult.AssignmentUnavailable unavailable ->
                    unavailable.context();
            case BenchmarkReturnResult.EndpointAnchorUnavailable unavailable ->
                    unavailable.context();
            case BenchmarkReturnResult.EvidenceUnavailable unavailable ->
                    unavailable.context();
            case BenchmarkReturnResult.OutputUnavailable unavailable ->
                    unavailable.context();
        };
        if (sourceContext.policyVersion()
                != BenchmarkReturnPolicyVersion
                        .SIGNED_BENCHMARK_BASIS_LEVEL_DENOMINATOR_SCALE_12_HALF_EVEN_V1
                || !REQUIRED_SOURCE_HASH.equals(
                        sourceContext.policyDefinitionHash())) {
            throw new IllegalArgumentException(
                    "sourceResult must use the required benchmark-return V1 policy");
        }
    }
}
