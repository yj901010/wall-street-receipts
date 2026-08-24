package com.wallstreetreceipts.api.domain.outcome.directionalwinorchestration;

import java.util.Objects;

import com.wallstreetreceipts.api.domain.outcome.assetreturn.AssetReturnResult;
import com.wallstreetreceipts.api.domain.outcome.calculation.DirectionalWinResult;
import com.wallstreetreceipts.api.domain.outcome.routing.CalculatorSideRouting.DirectionalRoute;
import com.wallstreetreceipts.api.domain.outcome.routing.CalculatorSideRouting.NonDirectionalRoute;
import com.wallstreetreceipts.api.domain.outcome.targeteligibility.BasisForecastTermsEvidence;

/** One correlated directional-win leaf result or an exact upstream branch. */
public sealed interface DirectionalWinOrchestrationResolution
        permits DirectionalWinOrchestrationResolution.Available,
        DirectionalWinOrchestrationResolution.NotApplicable,
        DirectionalWinOrchestrationResolution.AssetReturnUnavailable {

    /** Stable identity of this supplied-leaf orchestration contract. */
    record ResolutionContext(
            DirectionalWinOrchestrationPolicyVersion policyVersion,
            String policyDefinitionHash) {

        public ResolutionContext {
            Objects.requireNonNull(policyVersion,
                    "policyVersion must not be null");
            Objects.requireNonNull(policyDefinitionHash,
                    "policyDefinitionHash must not be null");
            if (policyVersion
                    != DirectionalWinOrchestrationPolicyVersion
                            .SUPPLIED_LEAF_DIRECTIONAL_WIN_ORCHESTRATION_V1) {
                throw new IllegalArgumentException(
                        "policyVersion must be the directional-win orchestration V1 policy");
            }
            if (!policyVersion.definitionHash().equals(policyDefinitionHash)) {
                throw new IllegalArgumentException(
                        "policyDefinitionHash must match policyVersion");
            }
        }
    }

    /** Directional result calculated from one correlated available return. */
    record Available(
            ResolutionContext context,
            BasisForecastTermsEvidence termsEvidence,
            DirectionalRoute sideRouting,
            AssetReturnResult.Available assetReturnResult,
            DirectionalWinResult.Available directionalWinResult)
            implements DirectionalWinOrchestrationResolution {

        public Available {
            Objects.requireNonNull(context, "context must not be null");
            Objects.requireNonNull(termsEvidence,
                    "termsEvidence must not be null");
            Objects.requireNonNull(sideRouting,
                    "sideRouting must not be null");
            Objects.requireNonNull(assetReturnResult,
                    "assetReturnResult must not be null");
            Objects.requireNonNull(directionalWinResult,
                    "directionalWinResult must not be null");
            new DirectionalWinOrchestrationRequest(
                    context.policyVersion(), termsEvidence, sideRouting,
                    assetReturnResult);
        }
    }

    /** Exact neutral route with its correlated return leaf and no Boolean. */
    record NotApplicable(
            ResolutionContext context,
            BasisForecastTermsEvidence termsEvidence,
            NonDirectionalRoute sideRouting,
            AssetReturnResult assetReturnResult)
            implements DirectionalWinOrchestrationResolution {

        public NotApplicable {
            Objects.requireNonNull(context, "context must not be null");
            Objects.requireNonNull(termsEvidence,
                    "termsEvidence must not be null");
            Objects.requireNonNull(sideRouting,
                    "sideRouting must not be null");
            Objects.requireNonNull(assetReturnResult,
                    "assetReturnResult must not be null");
            new DirectionalWinOrchestrationRequest(
                    context.policyVersion(), termsEvidence, sideRouting,
                    assetReturnResult);
        }
    }

    /** Exact unavailable return, including its complete nested reason chain. */
    record AssetReturnUnavailable(
            ResolutionContext context,
            BasisForecastTermsEvidence termsEvidence,
            DirectionalRoute sideRouting,
            AssetReturnResult.Unavailable assetReturnResult)
            implements DirectionalWinOrchestrationResolution {

        public AssetReturnUnavailable {
            Objects.requireNonNull(context, "context must not be null");
            Objects.requireNonNull(termsEvidence,
                    "termsEvidence must not be null");
            Objects.requireNonNull(sideRouting,
                    "sideRouting must not be null");
            Objects.requireNonNull(assetReturnResult,
                    "assetReturnResult must not be null");
            new DirectionalWinOrchestrationRequest(
                    context.policyVersion(), termsEvidence, sideRouting,
                    assetReturnResult);
        }
    }
}
