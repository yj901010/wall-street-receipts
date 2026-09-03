package com.wallstreetreceipts.api.domain.outcome.sectorreturnreadiness;

import java.util.Objects;

import com.wallstreetreceipts.api.domain.outcome.sectorreturn.SectorReturnResult;

/** Source-local readiness only; never a canonical outcome lifecycle status. */
public sealed interface SectorReturnReadinessResolution
        permits SectorReturnReadinessResolution.Settled,
        SectorReturnReadinessResolution.AwaitingEndpoint,
        SectorReturnReadinessResolution.EvidenceUnavailable {

    record ResolutionContext(
            SectorReturnReadinessPolicyVersion policyVersion,
            String policyDefinitionHash) {

        public ResolutionContext {
            Objects.requireNonNull(policyVersion,
                    "policyVersion must not be null");
            Objects.requireNonNull(policyDefinitionHash,
                    "policyDefinitionHash must not be null");
            if (policyVersion
                    != SectorReturnReadinessPolicyVersion
                            .SUPPLIED_LEAF_SECTOR_RETURN_READINESS_V1) {
                throw new IllegalArgumentException(
                        "policyVersion must be the sector-return readiness V1 policy");
            }
            if (!policyVersion.definitionHash().equals(policyDefinitionHash)) {
                throw new IllegalArgumentException(
                        "policyDefinitionHash must match policyVersion");
            }
        }
    }

    /** The supplied sector-return leaf is available or intentionally not applicable. */
    record Settled(
            ResolutionContext context,
            SectorReturnResult sourceResult)
            implements SectorReturnReadinessResolution {

        public Settled {
            validate(context, sourceResult,
                    SectorReturnReadinessResolver.Classification.SETTLED);
        }
    }

    /** The exact sector-reference endpoint has not yet been reached. */
    record AwaitingEndpoint(
            ResolutionContext context,
            SectorReturnResult sourceResult)
            implements SectorReturnReadinessResolution {

        public AwaitingEndpoint {
            validate(context, sourceResult,
                    SectorReturnReadinessResolver.Classification.AWAITING_ENDPOINT);
        }
    }

    /** Required sector-return evidence is unavailable or output is unrepresentable. */
    record EvidenceUnavailable(
            ResolutionContext context,
            SectorReturnResult sourceResult)
            implements SectorReturnReadinessResolution {

        public EvidenceUnavailable {
            validate(context, sourceResult,
                    SectorReturnReadinessResolver.Classification
                            .EVIDENCE_UNAVAILABLE);
        }
    }

    private static void validate(
            ResolutionContext context,
            SectorReturnResult sourceResult,
            SectorReturnReadinessResolver.Classification expected) {
        Objects.requireNonNull(context, "context must not be null");
        Objects.requireNonNull(sourceResult, "sourceResult must not be null");
        new SectorReturnReadinessRequest(context.policyVersion(), sourceResult);
        SectorReturnReadinessResolver.requireClassification(sourceResult, expected);
    }
}
