package com.wallstreetreceipts.api.domain.outcome.targeterrorreadiness;

import java.util.Objects;

import com.wallstreetreceipts.api.domain.outcome.targeterror.TargetErrorResult;

/** Source-local readiness only; never a canonical outcome lifecycle status. */
public sealed interface TargetErrorReadinessResolution
        permits TargetErrorReadinessResolution.Settled,
        TargetErrorReadinessResolution.AwaitingEndpoint,
        TargetErrorReadinessResolution.EvidenceUnavailable {

    record ResolutionContext(
            TargetErrorReadinessPolicyVersion policyVersion,
            String policyDefinitionHash) {

        public ResolutionContext {
            Objects.requireNonNull(policyVersion,
                    "policyVersion must not be null");
            Objects.requireNonNull(policyDefinitionHash,
                    "policyDefinitionHash must not be null");
            if (policyVersion
                    != TargetErrorReadinessPolicyVersion
                            .SUPPLIED_LEAF_TARGET_ERROR_READINESS_V1) {
                throw new IllegalArgumentException(
                        "policyVersion must be the target-error readiness V1 policy");
            }
            if (!policyVersion.definitionHash().equals(policyDefinitionHash)) {
                throw new IllegalArgumentException(
                        "policyDefinitionHash must match policyVersion");
            }
        }
    }

    /** The supplied target error is available; later metrics may still be absent. */
    record Settled(
            ResolutionContext context,
            TargetErrorResult sourceResult)
            implements TargetErrorReadinessResolution {

        public Settled {
            validate(context, sourceResult,
                    TargetErrorReadinessResolver.Classification.SETTLED);
        }
    }

    /** Only the official endpoint close is not yet reached as of evaluation. */
    record AwaitingEndpoint(
            ResolutionContext context,
            TargetErrorResult sourceResult)
            implements TargetErrorReadinessResolution {

        public AwaitingEndpoint {
            validate(context, sourceResult,
                    TargetErrorReadinessResolver.Classification.AWAITING_ENDPOINT);
        }
    }

    /** Any non-temporal or compound target-error evidence unavailability. */
    record EvidenceUnavailable(
            ResolutionContext context,
            TargetErrorResult sourceResult)
            implements TargetErrorReadinessResolution {

        public EvidenceUnavailable {
            validate(context, sourceResult,
                    TargetErrorReadinessResolver.Classification.EVIDENCE_UNAVAILABLE);
        }
    }

    private static void validate(
            ResolutionContext context,
            TargetErrorResult sourceResult,
            TargetErrorReadinessResolver.Classification expected) {
        Objects.requireNonNull(context, "context must not be null");
        Objects.requireNonNull(sourceResult, "sourceResult must not be null");
        new TargetErrorReadinessRequest(context.policyVersion(), sourceResult);
        TargetErrorReadinessResolver.requireClassification(sourceResult, expected);
    }
}
