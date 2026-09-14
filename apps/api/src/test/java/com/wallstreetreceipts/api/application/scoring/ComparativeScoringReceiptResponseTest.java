package com.wallstreetreceipts.api.application.scoring;

import java.math.BigDecimal;
import java.util.*;
import java.util.stream.Stream;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import com.wallstreetreceipts.api.application.port.out.ComparativeScoringReceiptRepository.Entry;
import com.wallstreetreceipts.api.domain.master.AssetType;
import com.wallstreetreceipts.api.web.scoring.ComparativeScoringReceiptResponse;
import static org.assertj.core.api.Assertions.*;
import static com.wallstreetreceipts.api.application.scoring.EndpointScoringFixture.*;

/** Pure projection only; storage and ledger authenticity are exercised separately against real databases. */
class ComparativeScoringReceiptResponseTest {
    static final List<String> CANDIDATES = List.of(
            "benchmark.assignment.classificationCandidates", "benchmark.assignment.assignmentCandidates",
            "benchmark.referenceIndexCandidates", "benchmark.basisLevelCandidates", "benchmark.endpointLevelCandidates",
            "benchmark.divisorContinuityCandidates", "sector.assignment.classificationCandidates",
            "sector.assignment.membershipCandidates", "sector.assignment.mappingCandidates", "sector.referenceIndexCandidates",
            "sector.basisLevelCandidates", "sector.endpointLevelCandidates", "sector.divisorContinuityCandidates");

    @TestFactory Stream<DynamicTest> allRawComparativeCandidateListsPreserveMissingAndPointInTimeStates() {
        return CANDIDATES.stream().flatMap(field -> Stream.of("missing", "future-available", "future-captured", "ambiguous", "future-foreign")
                .map(mode -> DynamicTest.dynamicTest(field + "/" + mode, () -> {
                    var original = ComparativeScoringFixture.input();
                    var candidate = (Record) ((List<?>) path(original, field)).getFirst();
                    var future = edit(edit(candidate, "capturedAt", AS_OF.plusSeconds(1)), "availableAt", AS_OF.plusSeconds(1));
                    List<?> replacements = switch (mode) {
                        case "missing" -> List.of();
                        case "future-available" -> List.of(future);
                        case "future-captured" -> List.of(edit(candidate, "capturedAt", AS_OF.plusSeconds(1)));
                        case "ambiguous" -> List.of(candidate, candidate);
                        default -> List.of(candidate, edit(future, "providerEventId", "demo-rejected-future-secret"));
                    };
                    var response = response(replace(original, field, replacements));
                    String leg = field.split("\\.")[0];
                    String opposite = leg.equals("benchmark") ? "sector" : "benchmark";
                    assertThat(path(response, opposite + "Return")).isEqualTo(path(response(original), opposite + "Return"));
                    if (mode.equals("future-foreign")) {
                        assertThat(path(response, leg + "Return")).isEqualTo(path(response(original), leg + "Return"));
                        assertThat(path(response, leg + "Evidence")).isEqualTo(path(response(original), leg + "Evidence"));
                        assertThat(response.toString()).doesNotContain("demo-rejected-future-secret");
                    } else {
                        assertThat(path(response, leg + "Return.state")).isEqualTo("UNAVAILABLE");
                        assertThat(path(response, leg + "Return.decimalValue")).isNull();
                        assertThat(path(response, leg + "Return.booleanValue")).isNull();
                        assertThat((List<?>) path(response, leg + "Return.reasons")).isNotEmpty();
                        assertThat(path(response, leg + "Evidence")).isNull();
                    }
                    assertThat(response.dataComplete()).isFalse();
                })));
    }

    @ParameterizedTest @ValueSource(strings = {"benchmark", "sector"})
    void waitingNotApplicableConflictAndOutputOverflowRetainDistinctMeaning(String leg) {
        var early = ComparativeScoringFixture.input(edit(input(), "evaluationAsOf", CLOSE.minusSeconds(1)));
        assertThat(path(response(early), leg + "Return.state")).isEqualTo("PENDING");
        assertThat(path(response(early), leg + "Evidence")).isNull();
        var classification = (Record) ((List<?>) path(early, leg + ".assignment.classificationCandidates")).getFirst();
        var conflict = replace(early, leg + ".assignment.classificationCandidates", List.of(edit(classification, "assetType", AssetType.INDEX)));
        assertThat(path(response(conflict), leg + "Return.state")).isEqualTo("UNAVAILABLE");
        var notApplicable = replace(conflict, leg + ".assignment." + (leg.equals("benchmark") ? "assignmentCandidates" : "membershipCandidates"), List.of());
        assertThat(path(response(notApplicable), leg + "Return.state")).isEqualTo("NOT_APPLICABLE");
        assertThat(path(response(notApplicable), leg + "Return.decimalValue")).isNull();
        assertThat(path(response(notApplicable), leg + "Evidence")).isNull();
        var original = ComparativeScoringFixture.input();
        var start = (Record) ((List<?>) path(original, leg + ".basisLevelCandidates")).getFirst();
        var end = (Record) ((List<?>) path(original, leg + ".endpointLevelCandidates")).getFirst();
        var overflow = replace(replace(original, leg + ".basisLevelCandidates", List.of(edit(start, "level", new BigDecimal("0.1")))),
                leg + ".endpointLevelCandidates", List.of(edit(end, "level", new BigDecimal("99999999999999999999999999.999999999999"))));
        var projected = response(overflow);
        assertThat(path(projected, leg + "Return.state")).isEqualTo("UNAVAILABLE");
        assertThat(path(projected, leg + "Return.decimalValue")).isNull();
        assertThat(path(projected, leg + "Return.reasons")).isEqualTo(List.of("OUTPUT_NOT_REPRESENTABLE"));
        assertThat(path(projected, leg + "Evidence")).isNotNull(); // selected pair still explains the unavailable output
    }

    @Test void missingScheduleAndUnsafeAnchorDoNotCreateEvidenceOrFakeWaiting() {
        var e = input();
        var incomplete = edit(e, "horizon", edit(e.horizon(), "catalog", edit(e.horizon().catalog(), "orderedSessions", List.of())));
        var unsafe = edit(e, "catalogEvidence", edit(e.catalogEvidence(), "capturedAt", AS_OF.plusSeconds(1)));
        for (var endpoint : List.of(incomplete, unsafe)) {
            var result = response(ComparativeScoringFixture.input(endpoint));
            for (var leg : List.of("benchmark", "sector")) {
                assertThat(path(result, leg + "Return.state")).isEqualTo("UNAVAILABLE");
                assertThat(path(result, leg + "Return.decimalValue")).isNull();
                assertThat(path(result, leg + "Evidence")).isNull();
            }
        }
    }

    @Test void absentAssetPriceAndTargetDoNotEraseIndependentComparativeEvidence() {
        var e = edit(edit(edit(input(), "basisCandidates", List.of()), "endpointCandidates", List.of()), "targetEvidence", null);
        var result = response(ComparativeScoringFixture.input(e));
        assertThat(result.assetReturn().decimalValue()).isNull();
        assertThat(result.targetError().decimalValue()).isNull();
        assertThat(result.benchmarkReturn().decimalValue()).isEqualTo("0.100000000000");
        assertThat(result.sectorReturn().decimalValue()).isEqualTo("-0.050000000000");
        assertThat(result.benchmarkEvidence()).isNotNull();
        assertThat(result.sectorEvidence()).isNotNull();
    }

    private static ComparativeScoringReceiptResponse response(ComparativeScoringInput input) {
        var evaluated = new ComparativeScoringEvaluator().evaluate(input);
        var basis = input.endpoint().horizon().basis();
        var row = new Entry(UUID.randomUUID(), basis.callId(), "demo-projection-only", basis.basisRevisionId(), null,
                evaluated.methodologyId(), evaluated.methodologyVersion(), evaluated.methodologyDefinitionHash(),
                ComparativeScoringMethodology.canonicalDefinition(), evaluated.inputFingerprint(), "a".repeat(64),
                input.endpoint().evaluationAsOf(), AS_OF, ComparativeScoringInputCodec.encode(input));
        return ComparativeScoringReceiptResponse.from(new ComparativeScoringReceiptService.Verified(row, evaluated, "demo-projection-only"));
    }
    private static Object path(Object source, String location) {
        try {
            for (String part : location.split("\\.")) source = source.getClass().getMethod(part).invoke(source);
            return source;
        } catch (ReflectiveOperationException failure) { throw new AssertionError(failure); }
    }
    private static <T extends Record> T replace(T source, String location, Object replacement) {
        String[] parts = location.split("\\.", 2);
        return edit(source, parts[0], parts.length == 1 ? replacement : replace((Record) path(source, parts[0]), parts[1], replacement));
    }
}
