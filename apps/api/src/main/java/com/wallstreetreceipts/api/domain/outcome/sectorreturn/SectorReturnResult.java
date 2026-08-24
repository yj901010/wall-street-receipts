package com.wallstreetreceipts.api.domain.outcome.sectorreturn;

import java.math.BigDecimal;
import java.util.Objects;

import com.wallstreetreceipts.api.domain.outcome.sectorreferencepair
        .SectorReferenceLevelPairResolution;

/** A signed sector price-index return or an exact propagated pair branch. */
public sealed interface SectorReturnResult
        permits SectorReturnResult.Available,
        SectorReturnResult.NotApplicable,
        SectorReturnResult.AssignmentUnavailable,
        SectorReturnResult.EndpointAnchorUnavailable,
        SectorReturnResult.EvidenceUnavailable,
        SectorReturnResult.OutputUnavailable {

    enum OutputUnavailableReason {
        OUTPUT_NOT_REPRESENTABLE
    }

    /** Policy identity plus the complete immutable sector level-pair receipt. */
    record CalculationContext(
            SectorReturnPolicyVersion policyVersion,
            String policyDefinitionHash,
            SectorReferenceLevelPairResolution referenceLevelPairResolution) {

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
            new SectorReturnInput(policyVersion, referenceLevelPairResolution);
        }
    }

    /** Locally representable output; only the calculator attests the formula. */
    record Available(
            CalculationContext context,
            BigDecimal sectorReturn) implements SectorReturnResult {

        public Available {
            Objects.requireNonNull(context, "context must not be null");
            if (!(context.referenceLevelPairResolution()
                    instanceof SectorReferenceLevelPairResolution.Resolved)) {
                throw new IllegalArgumentException(
                        "available sector return requires a resolved reference pair");
            }
            Objects.requireNonNull(sectorReturn, "sectorReturn must not be null");
            if (sectorReturn.compareTo(BigDecimal.ONE.negate()) < 0
                    || sectorReturn.scale() != 12
                    || sectorReturn.precision() > 38) {
                throw new IllegalArgumentException(
                        "sectorReturn must be signed scale-12 NUMERIC(38,12) at least -1");
            }
        }
    }

    /** Exact upstream intentional non-applicability, retained without flattening. */
    record NotApplicable(CalculationContext context) implements SectorReturnResult {

        public NotApplicable {
            Objects.requireNonNull(context, "context must not be null");
            if (!(context.referenceLevelPairResolution()
                    instanceof SectorReferenceLevelPairResolution.NotApplicable)) {
                throw new IllegalArgumentException(
                        "not-applicable return requires a not-applicable reference pair");
            }
        }
    }

    /** Exact upstream assignment unavailability, retained without flattening. */
    record AssignmentUnavailable(CalculationContext context)
            implements SectorReturnResult {

        public AssignmentUnavailable {
            Objects.requireNonNull(context, "context must not be null");
            if (!(context.referenceLevelPairResolution()
                    instanceof SectorReferenceLevelPairResolution
                            .AssignmentUnavailable)) {
                throw new IllegalArgumentException(
                        "assignment-unavailable return requires that reference-pair branch");
            }
        }
    }

    /** Exact upstream endpoint-anchor unavailability, retained without flattening. */
    record EndpointAnchorUnavailable(CalculationContext context)
            implements SectorReturnResult {

        public EndpointAnchorUnavailable {
            Objects.requireNonNull(context, "context must not be null");
            if (!(context.referenceLevelPairResolution()
                    instanceof SectorReferenceLevelPairResolution
                            .EndpointAnchorUnavailable)) {
                throw new IllegalArgumentException(
                        "endpoint-anchor-unavailable return requires that reference-pair branch");
            }
        }
    }

    /** Exact upstream reference-evidence unavailability, retained without flattening. */
    record EvidenceUnavailable(CalculationContext context)
            implements SectorReturnResult {

        public EvidenceUnavailable {
            Objects.requireNonNull(context, "context must not be null");
            if (!(context.referenceLevelPairResolution()
                    instanceof SectorReferenceLevelPairResolution
                            .EvidenceUnavailable)) {
                throw new IllegalArgumentException(
                        "evidence-unavailable return requires that reference-pair branch");
            }
        }
    }

    /** A resolved pair whose calculated output cannot fit the closed output boundary. */
    record OutputUnavailable(
            CalculationContext context,
            OutputUnavailableReason reason) implements SectorReturnResult {

        public OutputUnavailable {
            Objects.requireNonNull(context, "context must not be null");
            if (!(context.referenceLevelPairResolution()
                    instanceof SectorReferenceLevelPairResolution.Resolved)) {
                throw new IllegalArgumentException(
                        "output-unavailable return requires a resolved reference pair");
            }
            Objects.requireNonNull(reason, "reason must not be null");
        }
    }
}
