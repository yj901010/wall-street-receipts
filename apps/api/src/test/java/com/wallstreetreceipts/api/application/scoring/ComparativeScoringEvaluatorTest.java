package com.wallstreetreceipts.api.application.scoring;

import java.lang.reflect.Modifier;
import java.math.BigDecimal;
import java.util.*;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;
import org.junit.jupiter.api.DynamicTest;
import com.wallstreetreceipts.api.domain.master.AssetType;
import com.wallstreetreceipts.api.domain.outcome.horizon.*;
import com.wallstreetreceipts.api.domain.outcome.targeterror.TargetErrorResult;
import com.wallstreetreceipts.api.domain.outcome.targeterrorreadiness.TargetErrorReadinessResolution;
import static org.assertj.core.api.Assertions.*;
import static com.wallstreetreceipts.api.application.scoring.EndpointScoringFixture.*;
import static com.wallstreetreceipts.api.application.scoring.ComparativeScoringFixture.input;

class ComparativeScoringEvaluatorTest {
    private final ComparativeScoringEvaluator evaluator = new ComparativeScoringEvaluator();
    static final List<String> CANDIDATES = List.of(
            "benchmark.assignment.classificationCandidates", "benchmark.assignment.assignmentCandidates",
            "benchmark.referenceIndexCandidates", "benchmark.basisLevelCandidates", "benchmark.endpointLevelCandidates",
            "benchmark.divisorContinuityCandidates", "sector.assignment.classificationCandidates",
            "sector.assignment.membershipCandidates", "sector.assignment.mappingCandidates", "sector.referenceIndexCandidates",
            "sector.basisLevelCandidates", "sector.endpointLevelCandidates", "sector.divisorContinuityCandidates");

    @Test void fiveMeaningsPreserveTheOldReceiptAndReuseOneExactEndpoint() {
        var in = input();
        var result = evaluator.evaluate(in);
        assertThat(result.input()).isSameAs(in);
        assertThat(result.dataComplete()).isFalse();
        assertThat(result.methodologyId()).isEqualTo(ComparativeScoringMethodology.ID);
        assertThat(result.methodologyVersion()).isEqualTo(ComparativeScoringMethodology.VERSION);
        assertThat(result.methodologyDefinitionHash()).isEqualTo(ComparativeScoringMethodology.definitionHash());
        var old = new EndpointScoringEvaluator().evaluate(in.endpoint());
        assertThat(result.endpoint().inputFingerprint()).isEqualTo(old.inputFingerprint());
        assertThat(result.endpoint().sharedAssetReturnAndDirectionalWin()).isEqualTo(old.sharedAssetReturnAndDirectionalWin());
        assertThat(result.endpoint().targetError()).isEqualTo(old.targetError());
        var error = (TargetErrorResult.Available) ((TargetErrorReadinessResolution.Settled)
                result.endpoint().targetError().orElseThrow()).sourceResult();
        var bp = path(leaf(result, "benchmark"), "context.referenceLevelPairResolution");
        var sp = path(leaf(result, "sector"), "context.referenceLevelPairResolution");
        assertThat(path(bp, "context.endpointPriceResolution")).isSameAs(error.context().endpointPriceResolution());
        assertThat(path(sp, "context.endpointPriceResolution")).isSameAs(error.context().endpointPriceResolution());
        assertThat(path(bp, "basisLevelObservation")).isSameAs(in.benchmark().basisLevelCandidates().getFirst());
        assertThat(path(sp, "endpointLevelObservation")).isSameAs(in.sector().endpointLevelCandidates().getFirst());
        assertThat(path(leaf(result, "benchmark"), "benchmarkReturn")).isEqualTo(new BigDecimal("0.100000000000"));
        assertThat(path(leaf(result, "sector"), "sectorReturn")).isEqualTo(new BigDecimal("-0.050000000000"));
        assertThat(Arrays.stream(ComparativeScoringEvaluator.Receipt.class.getDeclaredConstructors()))
                .allMatch(c -> Modifier.isPrivate(c.getModifiers()));
    }

    @TestFactory Stream<DynamicTest> rawMembershipAndPointInTimeMatrix() {
        return CANDIDATES.stream().flatMap(field -> Stream.of("missing", "future-available", "future-captured", "ambiguous", "future-foreign")
                .map(mode -> DynamicTest.dynamicTest(field + "/" + mode, () -> {
                    var original = input();
                    var candidate = (Record) ((List<?>) path(original, field)).getFirst();
                    var future = edit(edit(candidate, "capturedAt", AS_OF.plusSeconds(1)), "availableAt", AS_OF.plusSeconds(1));
                    List<?> replacement = switch (mode) {
                        case "missing" -> List.of();
                        case "future-available" -> List.of(future);
                        case "future-captured" -> List.of(edit(candidate, "capturedAt", AS_OF.plusSeconds(1)));
                        case "ambiguous" -> List.of(candidate, candidate);
                        default -> List.of(candidate, edit(future, "providerEventId", "demo-future-foreign"));
                    };
                    var changed = replace(original, field, replacement);
                    var result = evaluator.evaluate(changed);
                    String leg = field.split("\\.")[0];
                    String opposite = leg.equals("benchmark") ? "sector" : "benchmark";
                    assertThat(leaf(result, opposite)).isEqualTo(leaf(evaluator.evaluate(original), opposite));
                    assertThat(result.inputFingerprint()).isNotEqualTo(evaluator.evaluate(original).inputFingerprint());
                    if (mode.equals("future-foreign")) assertThat(leaf(result, leg)).isEqualTo(leaf(evaluator.evaluate(original), leg));
                    else assertThat(readiness(result, leg).getClass().getSimpleName()).isEqualTo("EvidenceUnavailable");
                    assertThat(result.dataComplete()).isFalse();
                })));
    }

    @Test void missingAssetPricesAndTargetDoNotPreventIndependentReferenceReturns() {
        var e = edit(edit(edit(EndpointScoringFixture.input(), "endpointCandidates", List.of()), "basisCandidates", List.of()), "targetEvidence", null);
        var result = evaluator.evaluate(input(e));
        assertThat(result.endpoint().targetError().orElseThrow()).isInstanceOf(TargetErrorReadinessResolution.EvidenceUnavailable.class);
        assertThat(leaf(result, "benchmark").getClass().getSimpleName()).isEqualTo("Available");
        assertThat(leaf(result, "sector").getClass().getSimpleName()).isEqualTo("Available");
    }

    @Test void missingScheduleDoesNotExecuteComparativeLeaves() {
        var e = EndpointScoringFixture.input();
        e = edit(e, "horizon", edit(e.horizon(), "horizon", com.wallstreetreceipts.api.domain.outcome.OutcomeHorizon.W1));
        var result = evaluator.evaluate(input(e));
        assertThat(result.endpoint().horizon()).isInstanceOf(SessionCloseHorizonResolution.Incomplete.class);
        assertThat(result.benchmarkReturn()).isEmpty();
        assertThat(result.sectorReturn()).isEmpty();
    }

    @Test void onlyMatureEndpointWaitingChainIsTemporalAndNotApplicablePrecedesWaiting() {
        var early = input(edit(EndpointScoringFixture.input(), "evaluationAsOf", CLOSE.minusSeconds(1)));
        var waiting = evaluator.evaluate(early);
        for (var leg : List.of("benchmark", "sector")) {
            assertThat(readiness(waiting, leg).getClass().getSimpleName()).isEqualTo("AwaitingEndpoint");
            var classes = (List<?>) path(early, leg + ".assignment.classificationCandidates");
            var nonEquity = edit((Record) classes.getFirst(), "assetType", AssetType.INDEX);
            var conflict = replace(early, leg + ".assignment.classificationCandidates", List.of(nonEquity));
            assertThat(leaf(evaluator.evaluate(conflict), leg).getClass().getSimpleName()).isEqualTo("AssignmentUnavailable");
            var notApplicable = evaluator.evaluate(replace(conflict, leg + ".assignment."
                    + (leg.equals("benchmark") ? "assignmentCandidates" : "membershipCandidates"), List.of()));
            assertThat(readiness(notApplicable, leg).getClass().getSimpleName()).isEqualTo("Settled");
            assertThat(leaf(notApplicable, leg).getClass().getSimpleName()).isEqualTo("NotApplicable");
            var missing = evaluator.evaluate(replace(early, leg + ".assignment.classificationCandidates", List.of()));
            assertThat(readiness(missing, leg).getClass().getSimpleName()).isEqualTo("EvidenceUnavailable");
            assertThat(leaf(missing, leg).getClass().getSimpleName()).isEqualTo("AssignmentUnavailable");
        }
    }

    @TestFactory Stream<DynamicTest> exactReferenceTimesAndIdentityFailWithoutFallback() {
        return Stream.of("benchmark", "sector").flatMap(leg -> Stream.of("observedAt", "referenceIndexProviderEventId", "levelSourceRevision", "calendarRevision")
                .map(field -> DynamicTest.dynamicTest(leg + "/" + field, () -> {
                    var original = input();
                    String location = leg + ".endpointLevelCandidates";
                    var level = (Record) ((List<?>) path(original, location)).getFirst();
                    Object wrong = field.equals("observedAt") ? CLOSE.minusSeconds(1) : "demo-wrong-revision";
                    var result = evaluator.evaluate(replace(original, location, List.of(edit(level, field, wrong))));
                    assertThat(readiness(result, leg).getClass().getSimpleName()).isEqualTo("EvidenceUnavailable");
                    assertThat(leaf(result, leg).getClass().getSimpleName()).isEqualTo("EvidenceUnavailable");
                })));
    }

    @Test void unsafeSharedAnchorBlocksBothLegsButDoesNotForgeWaiting() {
        var e = EndpointScoringFixture.input();
        e = edit(e, "catalogEvidence", edit(e.catalogEvidence(), "capturedAt", AS_OF.plusSeconds(1)));
        var result = evaluator.evaluate(input(e));
        for (var leg : List.of("benchmark", "sector")) {
            assertThat(readiness(result, leg).getClass().getSimpleName()).isEqualTo("EvidenceUnavailable");
            assertThat(leaf(result, leg).getClass().getSimpleName()).isEqualTo("EndpointAnchorUnavailable");
        }
    }

    @Test void wrongMethodologyIsRejectedRatherThanReinterpretedAsOldStoredProfile() {
        var in = input();
        for (var identity : List.of(new String[]{EndpointScoringMethodology.ID, "1.0.0", ComparativeScoringMethodology.definitionHash()},
                new String[]{ComparativeScoringMethodology.ID, "2.0.0", ComparativeScoringMethodology.definitionHash()},
                new String[]{ComparativeScoringMethodology.ID, "1.0.0", "f".repeat(64)})) {
            assertThatThrownBy(() -> evaluator.evaluate(identity[0], identity[1], identity[2], in)).isInstanceOf(IllegalArgumentException.class);
        }
    }

    static Object readiness(ComparativeScoringEvaluator.Receipt result, String leg) {
        return leg.equals("benchmark") ? result.benchmarkReturn().orElseThrow() : result.sectorReturn().orElseThrow();
    }
    static Object leaf(ComparativeScoringEvaluator.Receipt result, String leg) { return path(readiness(result, leg), "sourceResult"); }
    static Object path(Object source, String path) {
        try {
            for (String part : path.split("\\.")) source = source.getClass().getMethod(part).invoke(source);
            return source;
        } catch (ReflectiveOperationException failure) { throw new AssertionError(failure); }
    }
    static <T extends Record> T replace(T source, String path, Object replacement) {
        String[] parts = path.split("\\.", 2);
        return edit(source, parts[0], parts.length == 1 ? replacement : replace((Record) path(source, parts[0]), parts[1], replacement));
    }
}
