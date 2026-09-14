package com.wallstreetreceipts.api.application.scoring;

import java.nio.charset.StandardCharsets;
import com.wallstreetreceipts.api.domain.outcome.targeteligibility.TargetEligibilityPolicyVersion;
import com.wallstreetreceipts.api.domain.outcome.favorableextreme.FavorableExtremePolicyVersion;
import com.wallstreetreceipts.api.domain.outcome.targethitorchestration.TargetHitOrchestrationPolicyVersion;
import com.wallstreetreceipts.api.domain.outcome.targethitreadiness.TargetHitReadinessPolicyVersion;

/** Separately versioned supplied-aggregate preview; does not activate any registry or stored profile. */
public final class TargetHitScoringMethodology {
    private TargetHitScoringMethodology() {}
    public static final String ID = "wsr-demo-target-hit-preview";
    public static final String VERSION = "1.0.0";
    static final TargetEligibilityPolicyVersion ELIGIBILITY = TargetEligibilityPolicyVersion.POINT_IN_TIME_TARGET_HIT_INPUT_READINESS_V1;
    static final FavorableExtremePolicyVersion EXTREME = FavorableExtremePolicyVersion.POINT_IN_TIME_ATTESTED_CAUSAL_WINDOW_HIGH_LOW_V1;
    static final TargetHitOrchestrationPolicyVersion ORCHESTRATION = TargetHitOrchestrationPolicyVersion.POINT_IN_TIME_TARGET_HIT_ORCHESTRATION_V1;
    static final TargetHitReadinessPolicyVersion READINESS = TargetHitReadinessPolicyVersion.SUPPLIED_LEAF_TARGET_HIT_READINESS_V1;

    private static final String DEFINITION = """
            wsr-demo-target-hit-preview/1.0.0
            mode=DEMO;scope=PARTIAL_PREVIEW;canonicalOutcome=false;dataComplete=false
            inputs=complete-ADR083-input,optional-window-binding,ordered-full-window-high-low-candidates
            shared-context=exact-preserved-ADR080-horizon,terms,target,catalog,evaluation-as-of;derive-route-from-same-terms-and-pinned-polarity
            execution=ADR083-evaluate-once,ADR018-eligibility,ADR019-selector-only-for-ready,ADR020-orchestration,ADR024-readiness
            incomplete-horizon=preserve-ADR083-receipt,execute-eligibility-and-preserve-its-exact-precedence;no-window-selection-unless-ready
            readiness=ADR025-shared-asset-and-direction-once,target-error,benchmark-return,sector-return,target-hit;five-owners-six-meanings
            independence=no-asset-endpoint-or-reference-level-prerequisite-for-target-hit;no-target-hit-fallback-for-other-metrics
            window=(basis-event,endpoint-close];exact-ordered-session-union;primary-venue-regular-session;PIT-available-and-captured;explicit-split-continuity
            comparison=ADR020-unchanged;bullish-high>=target;bearish-low<=target;equality-is-hit;no-arithmetic-rounding-or-reselection
            absence=missing-is-unavailable;not-applicable-is-not-false;only-eligibility-pending-awaits;no-deduplication-or-endpoint-price-fallback
            fingerprint=wsr-target-hit-input-v1;SHA-256;type-tagged-length-prefixed-strict-UTF8;closed-record-types-and-declared-fields;ordered-lists;normalized-decimal;ISO-instant;currency-code;embedded-canonical-ADR083-bytes;all-supplied-candidates-including-rejected-and-future
            bounds=4096-per-list;65536-UTF16-units-per-string;1048576-total-bytes;24-decode-depth;strict-canonical-roundtrip
            trust=synthetic-caller-attestation-only;no-provider-authentication,raw-tick-coverage-or-source-ledger-membership-proof
            deferred=mfe,mae,alpha,sectorAlpha,lifecycle,persistence,API,UI,command,registry-activation,aggregation,ranking
            """
            + pin(ComparativeScoringMethodology.ID + "/" + ComparativeScoringMethodology.VERSION, ComparativeScoringMethodology.definitionHash(), "6fb2d737d177662ec072277f1e345ca10a2c9447d35353bc46c1f886669ad6d2")
            + pin(ELIGIBILITY.name(), ELIGIBILITY.definitionHash(), "a6b4c9f4e4d29b5f1a9b0c300e2d7b9505318c708dfb0ad0e88f71324cf65465")
            + pin(EXTREME.name(), EXTREME.definitionHash(), "e3a0e93030c8f09ae5398bf6df0f2e28eec14b0a31f5bea240fc78f2412c2463")
            + pin(ORCHESTRATION.name(), ORCHESTRATION.definitionHash(), "b91bf68958e42ad003b80973c74f9acc2dad8e4629f6a1905798df98aa8b5348")
            + pin(READINESS.name(), READINESS.definitionHash(), "8f81dee5227370d82dd91cd2fb8448797c7028eaa485dc64cf4bdc3cbf2f31a3");

    public static String canonicalDefinition() { return DEFINITION; }
    public static String definitionHash() { return EndpointScoringInputCodec.hash(DEFINITION.getBytes(StandardCharsets.UTF_8)); }
    private static String pin(String identity, String actual, String expected) {
        if (!expected.equals(actual)) throw new IllegalStateException("Target-hit scoring dependency changed");
        return identity + "=" + expected + "\n";
    }
}
