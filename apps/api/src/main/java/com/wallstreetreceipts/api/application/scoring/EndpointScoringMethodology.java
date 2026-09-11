package com.wallstreetreceipts.api.application.scoring;

import java.nio.charset.StandardCharsets;
import com.wallstreetreceipts.api.domain.outcome.horizon.SessionCloseHorizonPolicyVersion;
import com.wallstreetreceipts.api.domain.outcome.observation.EndpointPricePolicyVersion;
import com.wallstreetreceipts.api.domain.outcome.pricepair.AssetReturnPricePairPolicyVersion;
import com.wallstreetreceipts.api.domain.outcome.assetreturn.AssetReturnPolicyVersion;
import com.wallstreetreceipts.api.domain.outcome.direction.CallDirectionPolarityPolicyVersion;
import com.wallstreetreceipts.api.domain.outcome.directionalwinorchestration.DirectionalWinOrchestrationPolicyVersion;
import com.wallstreetreceipts.api.domain.outcome.directionalwinreadiness.DirectionalWinReadinessPolicyVersion;
import com.wallstreetreceipts.api.domain.outcome.targeterror.TargetErrorPolicyVersion;
import com.wallstreetreceipts.api.domain.outcome.targeterrorreadiness.TargetErrorReadinessPolicyVersion;

/** Versioned partial DEMO profile. Does not activate a canonical ScoringMethodology registry entry. */
public final class EndpointScoringMethodology {
    private EndpointScoringMethodology() {}
    public static final String ID = "wsr-demo-endpoint-preview";
    public static final String VERSION = "1.0.0";
    static final SessionCloseHorizonPolicyVersion HORIZON = SessionCloseHorizonPolicyVersion.STRICTLY_AFTER_BASIS_EVENT_SESSION_CLOSE_V1;
    static final EndpointPricePolicyVersion ENDPOINT = EndpointPricePolicyVersion.OFFICIAL_PRIMARY_VENUE_CLOSE_SPLIT_ADJUSTED_V1;
    static final AssetReturnPricePairPolicyVersion PAIR = AssetReturnPricePairPolicyVersion.SOURCE_RECORDED_BASIS_EVENT_TO_OFFICIAL_ENDPOINT_PRICE_PAIR_V1;
    static final AssetReturnPolicyVersion RETURN = AssetReturnPolicyVersion.SIGNED_BASIS_DENOMINATOR_SCALE_12_HALF_EVEN_V1;
    static final CallDirectionPolarityPolicyVersion POLARITY = CallDirectionPolarityPolicyVersion.COLLAPSE_STRONG_DIRECTIONS_NEUTRAL_NON_DIRECTIONAL_V1;
    static final DirectionalWinOrchestrationPolicyVersion DIRECTION = DirectionalWinOrchestrationPolicyVersion.SUPPLIED_LEAF_DIRECTIONAL_WIN_ORCHESTRATION_V1;
    static final DirectionalWinReadinessPolicyVersion SHARED = DirectionalWinReadinessPolicyVersion.SUPPLIED_LEAF_DIRECTIONAL_WIN_READINESS_V1;
    static final TargetErrorPolicyVersion ERROR = TargetErrorPolicyVersion.ACTUAL_DENOMINATOR_SCALE_12_HALF_EVEN_V1;
    static final TargetErrorReadinessPolicyVersion ERROR_READINESS = TargetErrorReadinessPolicyVersion.SUPPLIED_LEAF_TARGET_ERROR_READINESS_V1;

    private static final String DEFINITION = """
            wsr-demo-endpoint-preview/1.0.0
            mode=DEMO;scope=PARTIAL_PREVIEW;canonicalOutcome=false;dataComplete=false
            inputs=explicit-schedule,terms,catalog,binding,as-of,endpoint-candidates,basis-candidates,adjustments,optional-target
            correlation=same-basis-and-asset,PIT-visible-terms,identity-target-normalization-only
            execution=horizon,endpoint-once,price-pair,asset-return,polarity-routing,directional-win,shared-readiness,target-error,target-error-readiness
            missing-schedule=preserve-Incomplete,no-leaf-execution
            missing-price-or-target=preserve-complete-typed-leaves,no-zero-or-false-fallback
            shared-readiness=ADR-022-owns-asset-return-and-directional-win-once;neutral-is-not-a-loss
            deferred=targetHit,benchmarkReturn,sectorReturn,alpha,sectorAlpha,mfe,mae,lifecycle,persistence,API,UI
            fingerprint=wsr-endpoint-input-v1;SHA-256;type-tagged-length-prefixed-UTF-8;record-class-and-declared-field-order;ordered-lists;normalized-decimal;ISO-instant-date;currency-code;all-supplied-inputs-including-rejected-candidates;methodology-id-version-hash
            bounds=4096-per-list-and-calendar;65536-UTF16-units-per-string;1048576-encoded-bytes
            trust=synthetic-caller-attestation-only;no-provider-authentication-or-correction-ledger-membership-proof
            """
            + pin(HORIZON, HORIZON.definitionHash(), "550087efe7ddf2ba31974c89c2740ab79df986eefef48919c32c56a3232f8dc1")
            + pin(ENDPOINT, ENDPOINT.definitionHash(), "37e37aba9302d77366cef4129f77a82b7ccb2f1937bfffc0315ea8d0bc6b1f76")
            + pin(PAIR, PAIR.definitionHash(), "895e4bc97ebb3a92b80f2c58e2d28abb94440eeca963046ee755fa98825f4887")
            + pin(RETURN, RETURN.definitionHash(), "e5e61c4adcd6567bfc76f73114499578f09de2254dc39a2553f3c0e2eaf03486")
            + pin(POLARITY, POLARITY.definitionHash(), "d83eccc92fedd7ba025745be2c8e78245bc308d0ff479467fa61afe543dc8a50")
            + pin(DIRECTION, DIRECTION.definitionHash(), "51429c7601d4807162855f08c680d1e6bb7895f87fc108e141e5ad3a3ab25bcb")
            + pin(SHARED, SHARED.definitionHash(), "1eca77c5b4d43de7657281c161a8c50356cd90e1a18c6e9fd7f5b2c0142b7ec7")
            + pin(ERROR, ERROR.definitionHash(), "31ca30555549f670e3c22d98ead16f7a02bfad198f36532effaf4a4b6931d074")
            + pin(ERROR_READINESS, ERROR_READINESS.definitionHash(), "0b8bfb22dccd4a494f568c44d06163f73af36462cf929bc83cf238019811c44a");

    public static String canonicalDefinition() { return DEFINITION; }
    public static String definitionHash() { return EndpointScoringFingerprint.sha256(DEFINITION.getBytes(StandardCharsets.UTF_8)); }

    private static String pin(Enum<?> policy, String actual, String expected) {
        if (!actual.equals(expected)) throw new IllegalStateException("Scoring dependency definition changed");
        return policy.name() + "=" + expected + "\n";
    }
}
