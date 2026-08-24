package com.wallstreetreceipts.api.domain.outcome.targeteligibility;

import java.time.Instant;
import java.util.Objects;

import com.wallstreetreceipts.api.domain.PersistentInstant;
import com.wallstreetreceipts.api.domain.outcome.horizon.SessionCloseHorizonPolicyVersion;
import com.wallstreetreceipts.api.domain.outcome.horizon.SessionCloseHorizonResolution;
import com.wallstreetreceipts.api.domain.outcome.observation.CatalogPointInTimeEvidence;
import com.wallstreetreceipts.api.domain.outcome.routing.CalculatorSideRouting;
import com.wallstreetreceipts.api.domain.outcome.targeterror.TargetPriceEvidence;

/** Explicit supplied evidence for one target-hit input-readiness decision. */
public record TargetEligibilityRequest(
        TargetEligibilityPolicyVersion policyVersion,
        SessionCloseHorizonResolution horizonResolution,
        BasisForecastTermsEvidence termsEvidence,
        CalculatorSideRouting.Result sideRouting,
        TargetPriceEvidence targetEvidence,
        CatalogPointInTimeEvidence catalogEvidence,
        Instant evaluationAsOf) {

    private static final String REQUIRED_HORIZON_POLICY_HASH =
            "550087efe7ddf2ba31974c89c2740ab79df986eefef48919c32c56a3232f8dc1";

    public TargetEligibilityRequest {
        Objects.requireNonNull(policyVersion, "policyVersion must not be null");
        Objects.requireNonNull(horizonResolution,
                "horizonResolution must not be null");
        PersistentInstant.requireMicrosecondPrecision(
                evaluationAsOf, "evaluationAsOf");
        if (policyVersion
                != TargetEligibilityPolicyVersion
                        .POINT_IN_TIME_TARGET_HIT_INPUT_READINESS_V1) {
            throw new IllegalArgumentException(
                    "policyVersion must be the target-eligibility V1 policy");
        }
        var context = horizonContext(horizonResolution);
        if (context.policyVersion()
                != SessionCloseHorizonPolicyVersion
                        .STRICTLY_AFTER_BASIS_EVENT_SESSION_CLOSE_V1
                || !REQUIRED_HORIZON_POLICY_HASH.equals(
                        context.policyDefinitionHash())) {
            throw new IllegalArgumentException(
                    "horizonResolution must use the required strict-close V1 policy");
        }
    }

    static SessionCloseHorizonResolution.ResolutionContext horizonContext(
            SessionCloseHorizonResolution resolution) {
        return switch (resolution) {
            case SessionCloseHorizonResolution.Resolved resolved ->
                    resolved.window().context();
            case SessionCloseHorizonResolution.Incomplete incomplete ->
                    incomplete.context();
        };
    }
}
