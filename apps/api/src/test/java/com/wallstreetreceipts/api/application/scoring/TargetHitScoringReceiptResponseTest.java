package com.wallstreetreceipts.api.application.scoring;

import java.math.BigDecimal;
import java.util.*;
import java.util.stream.Stream;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
import com.wallstreetreceipts.api.domain.call.CallDirection;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wallstreetreceipts.api.application.port.out.TargetHitScoringReceiptRepository.Entry;
import com.wallstreetreceipts.api.domain.master.AssetType;
import com.wallstreetreceipts.api.web.scoring.TargetHitScoringReceiptResponse;
import static org.assertj.core.api.Assertions.*;
import static com.wallstreetreceipts.api.application.scoring.EndpointScoringFixture.*;

/** Pure projection only; storage and ledger authenticity are exercised separately against real databases. */
class TargetHitScoringReceiptResponseTest {
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

    private static TargetHitScoringReceiptResponse response(ComparativeScoringInput input) {
        var window = TargetHitScoringFixture.input(input.endpoint(), "160", "80");
        return response(new TargetHitScoringInput(input, window.windowBinding(), window.windowCandidates()));
    }
    private static TargetHitScoringReceiptResponse response(TargetHitScoringInput input) {
        var evaluated = new TargetHitScoringEvaluator().evaluate(input);
        var endpoint = input.comparative().endpoint();
        var basis = endpoint.horizon().basis();
        var row = new Entry(UUID.randomUUID(), basis.callId(), "demo-projection-only", basis.basisRevisionId(), null,
                evaluated.methodologyId(), evaluated.methodologyVersion(), evaluated.methodologyDefinitionHash(),
                TargetHitScoringMethodology.canonicalDefinition(), evaluated.inputFingerprint(), "a".repeat(64),
                endpoint.evaluationAsOf(), AS_OF, TargetHitScoringInputCodec.encode(input));
        return TargetHitScoringReceiptResponse.from(new TargetHitScoringReceiptService.Verified(row, evaluated, "demo-projection-only"));
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

    @ParameterizedTest @CsvSource({"BULLISH,150,160,80,true,HIGH,160", "BULLISH,150,149,80,false,HIGH,149",
            "BEARISH,90,160,80,true,LOW,80", "BEARISH,90,160,91,false,LOW,91",
            "STRONG_BULLISH,150,150,80,true,HIGH,150", "STRONG_BEARISH,90,160,90,true,LOW,90"})
    void availableTargetHitHasOnlyBooleanAndExactSelectedEvidence(CallDirection direction, String target,
            String high, String low, boolean expected, String field, String value) throws Exception {
        var input = TargetHitScoringFixture.input(EndpointScoringFixture.input(direction, "100", "120", target), high, low);
        var result = response(input);
        assertThat(result.targetHit().state()).isEqualTo("AVAILABLE");
        assertThat(result.targetHit().booleanValue()).isEqualTo(expected);
        assertThat(result.targetHit().decimalValue()).isNull();
        assertThat(result.targetHit().reasons()).isEmpty();
        assertThat(result.windowEvidence().attestationScope()).isEqualTo("CALLER_ATTESTED_DEMO_CAUSAL_WINDOW_NOT_RAW_TRADE_VERIFICATION");
        assertThat(result.windowEvidence().selectedField()).isEqualTo(field);
        assertThat(result.windowEvidence().selectedValue()).isEqualTo(value);
        assertThat(result.windowEvidence().target().value()).isEqualTo(target);
        assertThat(result.windowEvidence().binding().priceSourceId()).isEqualTo("demo-window-source");
        assertThat(result.windowEvidence().observation().windowHigh()).isEqualTo(high);
        assertThat(result.windowEvidence().observation().windowLow()).isEqualTo(low);
        assertThat(result.windowEvidence().observation().orderedSessionIds()).containsExactly("demo-session");
        assertThat(result.scope()).isEqualTo("PARTIAL_TARGET_HIT");
        assertThat(result.dataComplete()).isFalse();
        var json = new ObjectMapper().readTree(new ObjectMapper().writeValueAsString(result));
        assertThat(json.path("targetHit").path("booleanValue").isBoolean()).isTrue();
        assertThat(json.path("windowEvidence").path("selectedValue").isTextual()).isTrue();
        assertThat(json.has("inputBytes")).isFalse();
        assertThat(json.has("windowCandidates")).isFalse();
    }

    @ParameterizedTest @MethodSource("windowFailures")
    void windowFailureStatesRetainReasonsButNeverExposeRejectedEvidence(String field, Object value, String reason) {
        var input = TargetHitScoringFixture.input();
        var observation = edit(input.windowCandidates().getFirst(), field, value);
        var result = response(edit(input, "windowCandidates", List.of(observation)));
        assertThat(result.targetHit().state()).isEqualTo("UNAVAILABLE");
        assertThat(result.targetHit().reasons()).containsExactly("WINDOW_EVIDENCE_UNAVAILABLE", reason);
        assertThat(result.targetHit().booleanValue()).isNull();
        assertThat(result.targetHit().decimalValue()).isNull();
        assertThat(result.windowEvidence()).isNull();
        assertThat(result.benchmarkReturn().state()).isEqualTo("AVAILABLE");
    }
    static Stream<Arguments> windowFailures() { return TargetHitScoringEvaluatorTest.invalidCandidates(); }

    @Test void missingPendingNeutralAndAbsentTargetNeverBecomeFalse() {
        var original = TargetHitScoringFixture.input();
        var missing = response(edit(original, "windowCandidates", List.of()));
        assertThat(missing.targetHit().state()).isEqualTo("UNAVAILABLE");
        assertThat(missing.windowEvidence()).isNull();
        var early = response(TargetHitScoringFixture.input(edit(original.comparative().endpoint(), "evaluationAsOf", CLOSE.minusNanos(1000)), "160", "80"));
        assertThat(early.targetHit().state()).isEqualTo("PENDING");
        assertThat(early.targetHit().reasons()).containsExactly("HORIZON_NOT_REACHED_AS_OF");
        var neutral = response(TargetHitScoringFixture.input(EndpointScoringFixture.input(CallDirection.NEUTRAL, "100", "120", "150"), "160", "80"));
        assertThat(neutral.targetHit().state()).isEqualTo("NOT_APPLICABLE");
        assertThat(neutral.targetHit().reasons()).containsExactly("NON_DIRECTIONAL");
        for (var result : List.of(missing, early, neutral)) {
            assertThat(result.targetHit().booleanValue()).isNull();
            assertThat(result.targetHit().decimalValue()).isNull();
            assertThat(result.windowEvidence()).isNull();
        }
        var e = edit(original.comparative().endpoint(), "targetEvidence", null);
        var unavailable = response(TargetHitScoringFixture.input(e, "160", "80"));
        assertThat(unavailable.targetHit().reasons()).containsExactly("ELIGIBILITY_UNAVAILABLE", "TARGET_EVIDENCE_NOT_KNOWN_AS_OF");
        assertThat(unavailable.targetHit().booleanValue()).isNull();
    }

    @Test void actualSelectedJsonHasExactlyTheClosedContractFields() throws Exception {
        var root = java.nio.file.Path.of(System.getProperty("user.dir")).toAbsolutePath();
        while (root != null && !java.nio.file.Files.isRegularFile(root.resolve("contracts/target-hit-scoring-receipts.openapi.yaml"))) root = root.getParent();
        assertThat(root).isNotNull();
        Map<?, ?> contract = new org.yaml.snakeyaml.Yaml().load(java.nio.file.Files.readString(root.resolve("contracts/target-hit-scoring-receipts.openapi.yaml")));
        var schemas = (Map<?, ?>) ((Map<?, ?>) contract.get("components")).get("schemas");
        var json = new ObjectMapper().readTree(new ObjectMapper().writeValueAsString(response(TargetHitScoringFixture.input())));
        var locations = Map.of("", "Receipt", "/targetHit", "Metric", "/windowEvidence", "WindowEvidence",
                "/windowEvidence/target", "TargetEvidence", "/windowEvidence/binding", "WindowBinding",
                "/windowEvidence/observation", "WindowObservation", "/benchmarkEvidence", "ReferenceEvidence",
                "/sectorEvidence", "ReferenceEvidence", "/benchmarkEvidence/basisLevel", "Level");
        for (var entry : locations.entrySet()) {
            var schema = (Map<?, ?>) schemas.get(entry.getValue());
            var actual = new HashSet<String>();
            json.at(entry.getKey()).fieldNames().forEachRemaining(actual::add);
            assertThat(actual).containsExactlyInAnyOrderElementsOf(((Map<?, ?>) schema.get("properties")).keySet().stream().map(Object::toString).toList());
            assertThat(actual).containsExactlyInAnyOrderElementsOf(((List<?>) schema.get("required")).stream().map(Object::toString).toList());
            assertThat(schema.get("additionalProperties")).isEqualTo(false);
        }
    }

    @Test void selectedEvidenceExcludesFutureSecretAndCannotBeMutated() throws Exception {
        var original = TargetHitScoringFixture.input();
        var future = edit(edit(original.windowCandidates().getFirst(), "providerEventId", "DO_NOT_PUBLISH_FUTURE"),
                "capturedAt", AS_OF.plusNanos(1000));
        var selected = response(edit(original, "windowCandidates", List.of(original.windowCandidates().getFirst(), future)));
        assertThat(new ObjectMapper().writeValueAsString(selected)).doesNotContain("DO_NOT_PUBLISH_FUTURE", "windowCandidates", "inputBytes");
        assertThatThrownBy(() -> selected.windowEvidence().observation().orderedSessionIds().clear()).isInstanceOf(UnsupportedOperationException.class);
        assertThat(selected.windowEvidence()).isEqualTo(response(original).windowEvidence());
        var duplicate = response(edit(original, "windowCandidates", List.of(future, future)));
        assertThat(duplicate.windowEvidence()).isNull();
        assertThat(duplicate.targetHit().booleanValue()).isNull();
    }
}
