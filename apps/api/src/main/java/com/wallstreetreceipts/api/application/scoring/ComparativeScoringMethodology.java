package com.wallstreetreceipts.api.application.scoring;

import java.nio.charset.StandardCharsets;
import com.wallstreetreceipts.api.domain.outcome.benchmarkassignment.BenchmarkAssignmentPolicyVersion;
import com.wallstreetreceipts.api.domain.outcome.benchmarkreferencepair.BenchmarkReferenceLevelPairPolicyVersion;
import com.wallstreetreceipts.api.domain.outcome.benchmarkreturn.BenchmarkReturnPolicyVersion;
import com.wallstreetreceipts.api.domain.outcome.benchmarkreturnreadiness.BenchmarkReturnReadinessPolicyVersion;
import com.wallstreetreceipts.api.domain.outcome.sectorassignment.SectorAssignmentPolicyVersion;
import com.wallstreetreceipts.api.domain.outcome.sectorreferencepair.SectorReferenceLevelPairPolicyVersion;
import com.wallstreetreceipts.api.domain.outcome.sectorreturn.SectorReturnPolicyVersion;
import com.wallstreetreceipts.api.domain.outcome.sectorreturnreadiness.SectorReturnReadinessPolicyVersion;

/** Separate five-meaning preview; never changes or activates the old stored methodology. */
public final class ComparativeScoringMethodology {
    private ComparativeScoringMethodology() {}
    public static final String ID = "wsr-demo-comparative-preview";
    public static final String VERSION = "1.0.0";
    static final BenchmarkAssignmentPolicyVersion BENCHMARK_ASSIGNMENT = BenchmarkAssignmentPolicyVersion.POINT_IN_TIME_EXPLICIT_US_EQUITY_ASSET_SPX_ASSIGNMENT_V1;
    static final BenchmarkReferenceLevelPairPolicyVersion BENCHMARK_PAIR = BenchmarkReferenceLevelPairPolicyVersion.POINT_IN_TIME_EXACT_BENCHMARK_PRICE_INDEX_LEVEL_PAIR_V1;
    static final BenchmarkReturnPolicyVersion BENCHMARK_RETURN = BenchmarkReturnPolicyVersion.SIGNED_BENCHMARK_BASIS_LEVEL_DENOMINATOR_SCALE_12_HALF_EVEN_V1;
    static final BenchmarkReturnReadinessPolicyVersion BENCHMARK_READINESS = BenchmarkReturnReadinessPolicyVersion.SUPPLIED_LEAF_BENCHMARK_RETURN_READINESS_V1;
    static final SectorAssignmentPolicyVersion SECTOR_ASSIGNMENT = SectorAssignmentPolicyVersion.POINT_IN_TIME_EXPLICIT_WSR_ECONOMIC_ACTIVITY_SECTOR_ASSIGNMENT_V1;
    static final SectorReferenceLevelPairPolicyVersion SECTOR_PAIR = SectorReferenceLevelPairPolicyVersion.POINT_IN_TIME_EXACT_SECTOR_PRICE_INDEX_LEVEL_PAIR_V1;
    static final SectorReturnPolicyVersion SECTOR_RETURN = SectorReturnPolicyVersion.SIGNED_SECTOR_BASIS_LEVEL_DENOMINATOR_SCALE_12_HALF_EVEN_V1;
    static final SectorReturnReadinessPolicyVersion SECTOR_READINESS = SectorReturnReadinessPolicyVersion.SUPPLIED_LEAF_SECTOR_RETURN_READINESS_V1;

    private static final String DEFINITION = """
            wsr-demo-comparative-preview/1.0.0
            mode=DEMO;scope=PARTIAL_PREVIEW;canonicalOutcome=false;dataComplete=false
            inputs=complete-ADR080-input,independent-benchmark-and-sector-assignment-requests,reference-bindings,exact-basis-and-endpoint-levels,divisor-continuity
            correlation=exact-whole-basis,asset,evaluation-as-of;no-cross-leg-classification-inference
            execution=ADR080-evaluate-once,reuse-exact-endpoint-object-from-preserved-target-error-context,each-assignment-selector,each-reference-pair-selector,each-return-calculator,each-readiness-resolver
            incomplete-horizon=preserve-ADR080-receipt,no-comparative-execution
            readiness=ADR025-shared-asset-and-direction-once,target-error,benchmark-return,sector-return;four-owners-five-meanings
            absence=empty-candidates-are-missing;never-zero,false,proxy-or-opposite-leg-fallback
            price-index-only=exact-time-levels-and-divisor-continuity;not-total-return;no-FX-or-nearest-time
            fingerprint=wsr-comparative-input-v1;SHA-256;type-tagged-length-prefixed-strict-UTF8;closed-record-types-and-declared-fields;ordered-lists;normalized-decimal;ISO-instant;currency-code;embedded-canonical-ADR080-bytes;all-supplied-candidates-including-rejected-and-future
            bounds=4096-per-list;65536-UTF16-units-per-string;1048576-total-bytes;24-decode-depth;strict-canonical-roundtrip
            trust=synthetic-caller-attestation-only;no-provider-authentication,raw-tick-coverage-or-source-ledger-membership-proof
            deferred=targetHit,mfe,mae,alpha,sectorAlpha,lifecycle,persistence,API,UI,registry-activation,aggregation,ranking
            """
            + pin(EndpointScoringMethodology.ID + "/" + EndpointScoringMethodology.VERSION, EndpointScoringMethodology.definitionHash(), "91abc0fcbc986b47e505bbddba346977911c9977664621e4c88cb8a2cbf8ea27")
            + pin(BENCHMARK_ASSIGNMENT.name(), BENCHMARK_ASSIGNMENT.definitionHash(), "7318514c2f50eda16b2d7ef35bc68d00d6a8b18a0f09f77130525fca2f32da69")
            + pin(BENCHMARK_PAIR.name(), BENCHMARK_PAIR.definitionHash(), "2394b535c1061d32c647504a303b6f1e4ec2fe88e6017d9ff335d12087a5f73d")
            + pin(BENCHMARK_RETURN.name(), BENCHMARK_RETURN.definitionHash(), "96d0aab8e8e784b80a12b16c99f6ba8c5f44eff7a342fd14c075b944a0a7de79")
            + pin(BENCHMARK_READINESS.name(), BENCHMARK_READINESS.definitionHash(), "2dedaf014a149ed81e75941ee3677e3c8b77243b9987d9496709266aad721daf")
            + pin(SECTOR_ASSIGNMENT.name(), SECTOR_ASSIGNMENT.definitionHash(), "52d9f705a3a8a965a6fca79d36bd94ed8836642f1a2c4e5f29a878d0a267311c")
            + pin(SECTOR_PAIR.name(), SECTOR_PAIR.definitionHash(), "4224648ba01104fd3e96319158c7d6b42da472e9aa6f2ab22ef9fccf43da7e4a")
            + pin(SECTOR_RETURN.name(), SECTOR_RETURN.definitionHash(), "5aecd42c32ba69f0d21ab6e1ee1e3128cd31584724a6f46e176acd470204d0f7")
            + pin(SECTOR_READINESS.name(), SECTOR_READINESS.definitionHash(), "5737f44ebc6e65270300889dd5c2e92da0c4f3a2f04e4c6c43e4483e522187d4");

    public static String canonicalDefinition() { return DEFINITION; }
    public static String definitionHash() { return EndpointScoringInputCodec.hash(DEFINITION.getBytes(StandardCharsets.UTF_8)); }
    private static String pin(String identity, String actual, String expected) {
        if (!expected.equals(actual)) throw new IllegalStateException("Comparative scoring dependency changed");
        return identity + "=" + expected + "\n";
    }
}
