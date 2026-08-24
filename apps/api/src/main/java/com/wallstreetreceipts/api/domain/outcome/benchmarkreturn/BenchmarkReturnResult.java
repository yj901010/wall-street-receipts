package com.wallstreetreceipts.api.domain.outcome.benchmarkreturn;

import java.math.BigDecimal;
import java.util.Objects;

import com.wallstreetreceipts.api.domain.outcome.benchmarkreferencepair
        .BenchmarkReferenceLevelPairResolution;

/** A signed benchmark price-index return or an exact propagated pair branch. */
public sealed interface BenchmarkReturnResult
        permits BenchmarkReturnResult.Available,
        BenchmarkReturnResult.NotApplicable,
        BenchmarkReturnResult.AssignmentUnavailable,
        BenchmarkReturnResult.EndpointAnchorUnavailable,
        BenchmarkReturnResult.EvidenceUnavailable,
        BenchmarkReturnResult.OutputUnavailable {

    enum OutputUnavailableReason {
        OUTPUT_NOT_REPRESENTABLE
    }

    /** Policy identity plus the complete immutable benchmark level-pair receipt. */
    record CalculationContext(
            BenchmarkReturnPolicyVersion policyVersion,
            String policyDefinitionHash,
            BenchmarkReferenceLevelPairResolution referenceLevelPairResolution) {

        public CalculationContext {
            Objects.requireNonNull(policyVersion, "policyVersion must not be null");
            Objects.requireNonNull(policyDefinitionHash,
                    "policyDefinitionHash must not be null");
            Objects.requireNonNull(referenceLevelPairResolution,
                    "referenceLevelPairResolution must not be null");
            if (!policyVersion.definitionHash().equals(policyDefinitionHash)) {
                throw new IllegalArgumentException(
                        "policyDefinitionHash must match policyVersion");
            }
            new BenchmarkReturnInput(policyVersion, referenceLevelPairResolution);
        }
    }

    /** Locally representable output; only the calculator attests the formula. */
    record Available(
            CalculationContext context,
            BigDecimal benchmarkReturn) implements BenchmarkReturnResult {

        public Available {
            Objects.requireNonNull(context, "context must not be null");
            if (!(context.referenceLevelPairResolution()
                    instanceof BenchmarkReferenceLevelPairResolution.Resolved)) {
                throw new IllegalArgumentException(
                        "available benchmark return requires a resolved reference pair");
            }
            Objects.requireNonNull(benchmarkReturn,
                    "benchmarkReturn must not be null");
            if (benchmarkReturn.compareTo(BigDecimal.ONE.negate()) < 0
                    || benchmarkReturn.scale() != 12
                    || benchmarkReturn.precision() > 38) {
                throw new IllegalArgumentException(
                        "benchmarkReturn must be signed scale-12 NUMERIC(38,12) at least -1");
            }
        }
    }

    /** Exact upstream intentional non-applicability, retained without flattening. */
    record NotApplicable(CalculationContext context)
            implements BenchmarkReturnResult {

        public NotApplicable {
            Objects.requireNonNull(context, "context must not be null");
            if (!(context.referenceLevelPairResolution()
                    instanceof BenchmarkReferenceLevelPairResolution.NotApplicable)) {
                throw new IllegalArgumentException(
                        "not-applicable return requires a not-applicable reference pair");
            }
        }
    }

    /** Exact upstream assignment unavailability, retained without flattening. */
    record AssignmentUnavailable(CalculationContext context)
            implements BenchmarkReturnResult {

        public AssignmentUnavailable {
            Objects.requireNonNull(context, "context must not be null");
            if (!(context.referenceLevelPairResolution()
                    instanceof BenchmarkReferenceLevelPairResolution
                            .AssignmentUnavailable)) {
                throw new IllegalArgumentException(
                        "assignment-unavailable return requires that reference-pair branch");
            }
        }
    }

    /** Exact upstream endpoint-anchor unavailability, retained without flattening. */
    record EndpointAnchorUnavailable(CalculationContext context)
            implements BenchmarkReturnResult {

        public EndpointAnchorUnavailable {
            Objects.requireNonNull(context, "context must not be null");
            if (!(context.referenceLevelPairResolution()
                    instanceof BenchmarkReferenceLevelPairResolution
                            .EndpointAnchorUnavailable)) {
                throw new IllegalArgumentException(
                        "endpoint-anchor-unavailable return requires that reference-pair branch");
            }
        }
    }

    /** Exact upstream reference-evidence unavailability, retained without flattening. */
    record EvidenceUnavailable(CalculationContext context)
            implements BenchmarkReturnResult {

        public EvidenceUnavailable {
            Objects.requireNonNull(context, "context must not be null");
            if (!(context.referenceLevelPairResolution()
                    instanceof BenchmarkReferenceLevelPairResolution
                            .EvidenceUnavailable)) {
                throw new IllegalArgumentException(
                        "evidence-unavailable return requires that reference-pair branch");
            }
        }
    }

    /** A resolved pair whose calculated output cannot fit the closed output boundary. */
    record OutputUnavailable(
            CalculationContext context,
            OutputUnavailableReason reason) implements BenchmarkReturnResult {

        public OutputUnavailable {
            Objects.requireNonNull(context, "context must not be null");
            if (!(context.referenceLevelPairResolution()
                    instanceof BenchmarkReferenceLevelPairResolution.Resolved)) {
                throw new IllegalArgumentException(
                        "output-unavailable return requires a resolved reference pair");
            }
            Objects.requireNonNull(reason, "reason must not be null");
        }
    }
}
