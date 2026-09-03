package com.wallstreetreceipts.api.domain.outcome.benchmarkreturnreadiness;

import java.util.Objects;

import com.wallstreetreceipts.api.domain.outcome.benchmarkreturn
        .BenchmarkReturnResult;

/** Source-local readiness only; never a canonical outcome lifecycle status. */
public sealed interface BenchmarkReturnReadinessResolution
        permits BenchmarkReturnReadinessResolution.Settled,
        BenchmarkReturnReadinessResolution.AwaitingEndpoint,
        BenchmarkReturnReadinessResolution.EvidenceUnavailable {

    record ResolutionContext(
            BenchmarkReturnReadinessPolicyVersion policyVersion,
            String policyDefinitionHash) {

        public ResolutionContext {
            Objects.requireNonNull(policyVersion,
                    "policyVersion must not be null");
            Objects.requireNonNull(policyDefinitionHash,
                    "policyDefinitionHash must not be null");
            if (policyVersion
                    != BenchmarkReturnReadinessPolicyVersion
                            .SUPPLIED_LEAF_BENCHMARK_RETURN_READINESS_V1) {
                throw new IllegalArgumentException(
                        "policyVersion must be the benchmark-return readiness V1 policy");
            }
            if (!policyVersion.definitionHash().equals(policyDefinitionHash)) {
                throw new IllegalArgumentException(
                        "policyDefinitionHash must match policyVersion");
            }
        }
    }

    /** The supplied benchmark-return leaf is available or intentionally N/A. */
    record Settled(
            ResolutionContext context,
            BenchmarkReturnResult sourceResult)
            implements BenchmarkReturnReadinessResolution {

        public Settled {
            validate(context, sourceResult,
                    BenchmarkReturnReadinessResolver.Classification.SETTLED);
        }
    }

    /** The exact reference-level endpoint has not yet been reached. */
    record AwaitingEndpoint(
            ResolutionContext context,
            BenchmarkReturnResult sourceResult)
            implements BenchmarkReturnReadinessResolution {

        public AwaitingEndpoint {
            validate(context, sourceResult,
                    BenchmarkReturnReadinessResolver.Classification
                            .AWAITING_ENDPOINT);
        }
    }

    /** Required assignment, anchor, reference, or representable output is absent. */
    record EvidenceUnavailable(
            ResolutionContext context,
            BenchmarkReturnResult sourceResult)
            implements BenchmarkReturnReadinessResolution {

        public EvidenceUnavailable {
            validate(context, sourceResult,
                    BenchmarkReturnReadinessResolver.Classification
                            .EVIDENCE_UNAVAILABLE);
        }
    }

    private static void validate(
            ResolutionContext context,
            BenchmarkReturnResult sourceResult,
            BenchmarkReturnReadinessResolver.Classification expected) {
        Objects.requireNonNull(context, "context must not be null");
        Objects.requireNonNull(sourceResult, "sourceResult must not be null");
        new BenchmarkReturnReadinessRequest(
                context.policyVersion(), sourceResult);
        BenchmarkReturnReadinessResolver.requireClassification(
                sourceResult, expected);
    }
}
