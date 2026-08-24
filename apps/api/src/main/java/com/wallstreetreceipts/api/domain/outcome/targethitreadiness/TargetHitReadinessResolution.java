package com.wallstreetreceipts.api.domain.outcome.targethitreadiness;

import java.util.Objects;

import com.wallstreetreceipts.api.domain.outcome.targethitorchestration
        .TargetHitOrchestrationResolution;

/** Source-local readiness only; never a canonical outcome lifecycle status. */
public sealed interface TargetHitReadinessResolution
        permits TargetHitReadinessResolution.Settled,
        TargetHitReadinessResolution.AwaitingEndpoint,
        TargetHitReadinessResolution.EvidenceUnavailable {

    record ResolutionContext(
            TargetHitReadinessPolicyVersion policyVersion,
            String policyDefinitionHash) {

        public ResolutionContext {
            Objects.requireNonNull(policyVersion,
                    "policyVersion must not be null");
            Objects.requireNonNull(policyDefinitionHash,
                    "policyDefinitionHash must not be null");
            if (policyVersion
                    != TargetHitReadinessPolicyVersion
                            .SUPPLIED_LEAF_TARGET_HIT_READINESS_V1) {
                throw new IllegalArgumentException(
                        "policyVersion must be the target-hit readiness V1 policy");
            }
            if (!policyVersion.definitionHash().equals(policyDefinitionHash)) {
                throw new IllegalArgumentException(
                        "policyDefinitionHash must match policyVersion");
            }
        }
    }

    /** The supplied target-hit leaf is available or permanently not applicable. */
    record Settled(
            ResolutionContext context,
            TargetHitOrchestrationResolution sourceResult)
            implements TargetHitReadinessResolution {

        public Settled {
            validate(context, sourceResult,
                    TargetHitReadinessResolver.Classification.SETTLED);
        }
    }

    /** The exact target-hit endpoint close has not yet been reached. */
    record AwaitingEndpoint(
            ResolutionContext context,
            TargetHitOrchestrationResolution sourceResult)
            implements TargetHitReadinessResolution {

        public AwaitingEndpoint {
            validate(context, sourceResult,
                    TargetHitReadinessResolver.Classification.AWAITING_ENDPOINT);
        }
    }

    /** Required eligibility or favorable-extreme evidence is unavailable. */
    record EvidenceUnavailable(
            ResolutionContext context,
            TargetHitOrchestrationResolution sourceResult)
            implements TargetHitReadinessResolution {

        public EvidenceUnavailable {
            validate(context, sourceResult,
                    TargetHitReadinessResolver.Classification.EVIDENCE_UNAVAILABLE);
        }
    }

    private static void validate(
            ResolutionContext context,
            TargetHitOrchestrationResolution sourceResult,
            TargetHitReadinessResolver.Classification expected) {
        Objects.requireNonNull(context, "context must not be null");
        Objects.requireNonNull(sourceResult, "sourceResult must not be null");
        new TargetHitReadinessRequest(context.policyVersion(), sourceResult);
        TargetHitReadinessResolver.requireClassification(sourceResult, expected);
    }
}
