package com.wallstreetreceipts.api.domain.outcome.targethitorchestration;

import java.util.Objects;

import com.wallstreetreceipts.api.domain.outcome.calculation.TargetHitResult;
import com.wallstreetreceipts.api.domain.outcome.favorableextreme.FavorableExtremeResolution;
import com.wallstreetreceipts.api.domain.outcome.targeteligibility.TargetEligibilityResolution;

/**
 * One composed target-hit leaf result or an exact upstream lifecycle branch.
 *
 * <p>Public result constructors validate local branch shape only. Only
 * {@link TargetHitOrchestrator#orchestrate(TargetHitOrchestrationRequest)}
 * attests that the stored target-hit value came from the existing primitive.
 * Neither path re-attests upstream request membership, PIT filtering, or
 * favorable-extreme cardinality.</p>
 */
public sealed interface TargetHitOrchestrationResolution
        permits TargetHitOrchestrationResolution.Available,
        TargetHitOrchestrationResolution.Pending,
        TargetHitOrchestrationResolution.NotApplicable,
        TargetHitOrchestrationResolution.EligibilityUnavailable,
        TargetHitOrchestrationResolution.FavorableExtremeUnavailable {

    record ResolutionContext(
            TargetHitOrchestrationPolicyVersion policyVersion,
            String policyDefinitionHash) {

        public ResolutionContext {
            Objects.requireNonNull(policyVersion,
                    "policyVersion must not be null");
            Objects.requireNonNull(policyDefinitionHash,
                    "policyDefinitionHash must not be null");
            if (policyVersion
                    != TargetHitOrchestrationPolicyVersion
                            .POINT_IN_TIME_TARGET_HIT_ORCHESTRATION_V1) {
                throw new IllegalArgumentException(
                        "policyVersion must be the target-hit orchestration V1 policy");
            }
            if (!policyVersion.definitionHash().equals(policyDefinitionHash)) {
                throw new IllegalArgumentException(
                        "policyDefinitionHash must match policyVersion");
            }
        }
    }

    /** A target-hit value calculated from one supplied ready/resolved pair. */
    record Available(
            ResolutionContext context,
            FavorableExtremeResolution.Resolved favorableExtremeResolution,
            TargetHitResult.Available targetHitResult)
            implements TargetHitOrchestrationResolution {

        public Available {
            Objects.requireNonNull(context, "context must not be null");
            Objects.requireNonNull(favorableExtremeResolution,
                    "favorableExtremeResolution must not be null");
            Objects.requireNonNull(targetHitResult,
                    "targetHitResult must not be null");
            new TargetHitOrchestrationRequest(
                    context.policyVersion(),
                    favorableExtremeResolution.context().readyEligibility(),
                    favorableExtremeResolution);
        }
    }

    /** Exact pending eligibility, including its unchanged nested reason. */
    record Pending(
            ResolutionContext context,
            TargetEligibilityResolution.Pending eligibilityResolution)
            implements TargetHitOrchestrationResolution {

        public Pending {
            Objects.requireNonNull(context, "context must not be null");
            Objects.requireNonNull(eligibilityResolution,
                    "eligibilityResolution must not be null");
            new TargetHitOrchestrationRequest(
                    context.policyVersion(), eligibilityResolution, null);
        }
    }

    /** Exact permanent non-applicability without converting it to false. */
    record NotApplicable(
            ResolutionContext context,
            TargetEligibilityResolution.NotApplicable eligibilityResolution)
            implements TargetHitOrchestrationResolution {

        public NotApplicable {
            Objects.requireNonNull(context, "context must not be null");
            Objects.requireNonNull(eligibilityResolution,
                    "eligibilityResolution must not be null");
            new TargetHitOrchestrationRequest(
                    context.policyVersion(), eligibilityResolution, null);
        }
    }

    /** Exact target-eligibility unavailability and any nested horizon reason. */
    record EligibilityUnavailable(
            ResolutionContext context,
            TargetEligibilityResolution.Unavailable eligibilityResolution)
            implements TargetHitOrchestrationResolution {

        public EligibilityUnavailable {
            Objects.requireNonNull(context, "context must not be null");
            Objects.requireNonNull(eligibilityResolution,
                    "eligibilityResolution must not be null");
            new TargetHitOrchestrationRequest(
                    context.policyVersion(), eligibilityResolution, null);
        }
    }

    /** Exact favorable-extreme unavailability and its unchanged nested reason. */
    record FavorableExtremeUnavailable(
            ResolutionContext context,
            FavorableExtremeResolution.Unavailable favorableExtremeResolution)
            implements TargetHitOrchestrationResolution {

        public FavorableExtremeUnavailable {
            Objects.requireNonNull(context, "context must not be null");
            Objects.requireNonNull(favorableExtremeResolution,
                    "favorableExtremeResolution must not be null");
            new TargetHitOrchestrationRequest(
                    context.policyVersion(),
                    favorableExtremeResolution.context().readyEligibility(),
                    favorableExtremeResolution);
        }
    }
}
