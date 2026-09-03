package com.wallstreetreceipts.api.domain.outcome.directionalwinreadiness;

import java.util.Objects;

import com.wallstreetreceipts.api.domain.outcome.directionalwinorchestration
        .DirectionalWinOrchestrationResolution;

/** Source-local readiness only; never a canonical outcome lifecycle status. */
public sealed interface DirectionalWinReadinessResolution
        permits DirectionalWinReadinessResolution.Settled,
        DirectionalWinReadinessResolution.AwaitingEndpoint,
        DirectionalWinReadinessResolution.EvidenceUnavailable {

    record ResolutionContext(
            DirectionalWinReadinessPolicyVersion policyVersion,
            String policyDefinitionHash) {

        public ResolutionContext {
            Objects.requireNonNull(policyVersion,
                    "policyVersion must not be null");
            Objects.requireNonNull(policyDefinitionHash,
                    "policyDefinitionHash must not be null");
            if (policyVersion
                    != DirectionalWinReadinessPolicyVersion
                            .SUPPLIED_LEAF_DIRECTIONAL_WIN_READINESS_V1) {
                throw new IllegalArgumentException(
                        "policyVersion must be the directional-win readiness V1 policy");
            }
            if (!policyVersion.definitionHash().equals(policyDefinitionHash)) {
                throw new IllegalArgumentException(
                        "policyDefinitionHash must match policyVersion");
            }
        }
    }

    /** The asset-return leaf is available; later metrics may still be absent. */
    record Settled(
            ResolutionContext context,
            DirectionalWinOrchestrationResolution sourceResolution)
            implements DirectionalWinReadinessResolution {

        public Settled {
            validate(context, sourceResolution,
                    DirectionalWinReadinessResolver.Classification.SETTLED);
        }
    }

    /** Only the official endpoint close is not yet reached as of evaluation. */
    record AwaitingEndpoint(
            ResolutionContext context,
            DirectionalWinOrchestrationResolution sourceResolution)
            implements DirectionalWinReadinessResolution {

        public AwaitingEndpoint {
            validate(context, sourceResolution,
                    DirectionalWinReadinessResolver.Classification.AWAITING_ENDPOINT);
        }
    }

    /** Any non-temporal or compound unavailable return evidence. */
    record EvidenceUnavailable(
            ResolutionContext context,
            DirectionalWinOrchestrationResolution sourceResolution)
            implements DirectionalWinReadinessResolution {

        public EvidenceUnavailable {
            validate(context, sourceResolution,
                    DirectionalWinReadinessResolver.Classification.EVIDENCE_UNAVAILABLE);
        }
    }

    private static void validate(
            ResolutionContext context,
            DirectionalWinOrchestrationResolution sourceResolution,
            DirectionalWinReadinessResolver.Classification expected) {
        Objects.requireNonNull(context, "context must not be null");
        Objects.requireNonNull(sourceResolution,
                "sourceResolution must not be null");
        new DirectionalWinReadinessRequest(
                context.policyVersion(), sourceResolution);
        DirectionalWinReadinessResolver.requireClassification(
                sourceResolution, expected);
    }
}
