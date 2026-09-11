package com.wallstreetreceipts.api.application.scoring;

import java.io.*;
import java.math.BigDecimal;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.stream.Stream;
import org.junit.jupiter.api.*;
import com.wallstreetreceipts.api.domain.outcome.horizon.OutcomeBasis;
import com.wallstreetreceipts.api.domain.outcome.sectorassignment.SectorMappingEvidence;
import static org.assertj.core.api.Assertions.*;
import static com.wallstreetreceipts.api.application.scoring.EndpointScoringFixture.*;
import static com.wallstreetreceipts.api.application.scoring.ComparativeScoringFixture.input;
import static com.wallstreetreceipts.api.application.scoring.ComparativeScoringEvaluatorTest.*;

class ComparativeScoringInputCodecTest {
    @Test void pinsNewProfileAndInputWithoutChangingOldBytes() {
        assertThat(ComparativeScoringMethodology.definitionHash()).isEqualTo("6fb2d737d177662ec072277f1e345ca10a2c9447d35353bc46c1f886669ad6d2");
        assertThat(hash(input())).isEqualTo("507028d10501990999b8af3dd56835499d283544d8e8b6b739252a746edff4a0");
        assertThat(EndpointScoringInputCodec.hash(EndpointScoringInputCodec.encode(input().endpoint())))
                .isEqualTo("c538a04a0eb74074c0d3e50af94aa4ca965b025ab20ec1df182fdc22e4df760b");
        assertThat(EndpointScoringMethodology.definitionHash()).isEqualTo("91abc0fcbc986b47e505bbddba346977911c9977664621e4c88cb8a2cbf8ea27");
    }

    @Test void completeCanonicalRoundtripReplaysEveryLeaf() {
        var in = input();
        byte[] bytes = ComparativeScoringInputCodec.encode(in);
        var decoded = ComparativeScoringInputCodec.decode(bytes);
        assertThat(decoded).isEqualTo(in);
        assertThat(ComparativeScoringInputCodec.encode(decoded)).isEqualTo(bytes);
        var evaluator = new ComparativeScoringEvaluator();
        var replay = evaluator.evaluate(decoded);
        var original = evaluator.evaluate(in);
        assertThat(replay.inputFingerprint()).isEqualTo(original.inputFingerprint());
        assertThat(replay.benchmarkReturn()).isEqualTo(original.benchmarkReturn());
        assertThat(replay.sectorReturn()).isEqualTo(original.sectorReturn());
        assertThat(replay.endpoint().targetError()).isEqualTo(original.endpoint().targetError());
        bytes[0] ^= 1;
        assertThat(ComparativeScoringInputCodec.encode(in)).isNotEqualTo(bytes);
    }

    @TestFactory Stream<DynamicTest> fingerprintPreservesEveryCandidateAndItsOrder() {
        return CANDIDATES.stream().map(field -> DynamicTest.dynamicTest(field, () -> {
            var original = input();
            var record = (Record) ((List<?>) path(original, field)).getFirst();
            var changed = edit(record, "provenanceId", "demo-other-provenance");
            assertThat(hash(replace(original, field, List.of(changed)))).isNotEqualTo(hash(original));
            assertThat(hash(replace(original, field, List.of(record, changed))))
                    .isNotEqualTo(hash(replace(original, field, List.of(changed, record))));
        }));
    }

    @Test void decimalScaleIsNormalizedButDistinctValueAndAsOfAreNot() {
        var original = input();
        var level = original.benchmark().basisLevelCandidates().getFirst();
        assertThat(hash(replace(original, "benchmark.basisLevelCandidates", List.of(edit(level, "level", new BigDecimal("4000.000000000000"))))))
                .isEqualTo(hash(original));
        assertThat(hash(replace(original, "benchmark.basisLevelCandidates", List.of(edit(level, "level", new BigDecimal("4000.000000000001"))))))
                .isNotEqualTo(hash(original));
        assertThat(hash(input(edit(original.endpoint(), "evaluationAsOf", AS_OF.plusSeconds(1))))).isNotEqualTo(hash(original));
    }

    @Test void correctionBasisAndFiniteIntervalsRemainDistinctAndReplayable() {
        var old = input();
        var e = old.endpoint();
        var correction = new OutcomeBasis.Correction("demo-call", "demo-correction", BASIS);
        e = new EndpointScoringInput(e.dataMode(), edit(e.horizon(), "basis", correction), e.catalogEvidence(),
                e.binding(), e.evaluationAsOf(), edit(e.terms(), "basis", correction), e.endpointCandidates(),
                List.of(edit(e.basisCandidates().getFirst(), "basis", correction)),
                List.of(edit(e.adjustmentCandidates().getFirst(), "basis", correction)), edit(e.targetEvidence(), "basis", correction));
        var in = input(e);
        var reference = in.benchmark().referenceIndexCandidates().getFirst();
        var interval = new com.wallstreetreceipts.api.domain.outcome.benchmarkreferencepair.BenchmarkReferenceIndexEvidence.EffectiveInterval(
                BASIS, new com.wallstreetreceipts.api.domain.outcome.benchmarkreferencepair.BenchmarkReferenceIndexEvidence.EndsAtExclusive(AS_OF));
        in = replace(in, "benchmark.referenceIndexCandidates", List.of(edit(reference, "effectiveInterval", interval)));
        var decoded = ComparativeScoringInputCodec.decode(ComparativeScoringInputCodec.encode(in));
        assertThat(decoded).isEqualTo(in);
        assertThat(hash(decoded)).isNotEqualTo(hash(old));
        assertThat(leaf(new ComparativeScoringEvaluator().evaluate(decoded), "benchmark").getClass().getSimpleName()).isEqualTo("Available");
    }

    @Test void explicitUnmappedAndDefinitionAbsenceRoundtripWithoutInventingEvidence() {
        var in = input();
        var mapping = in.sector().assignment().mappingCandidates().getFirst();
        mapping = edit(mapping, "providerNodeDefinition", new SectorMappingEvidence.NotPublished());
        mapping = edit(mapping, "mappingDisposition", new SectorMappingEvidence.NotMapped(SectorMappingEvidence.NotMappedReason.PROVIDER_DEFINITION_UNAVAILABLE));
        in = replace(in, "sector.assignment.mappingCandidates", List.of(mapping));
        assertThat(ComparativeScoringInputCodec.decode(ComparativeScoringInputCodec.encode(in))).isEqualTo(in);
        assertThat(readiness(new ComparativeScoringEvaluator().evaluate(in), "sector").getClass().getSimpleName()).isEqualTo("EvidenceUnavailable");
    }

    @Test void rejectsWrongHeaderTypeFieldUtf8NonCanonicalDecimalAndTruncatedData() {
        byte[] valid = ComparativeScoringInputCodec.encode(input());
        for (byte[] broken : List.of(new byte[0], new byte[1048577], Arrays.copyOf(valid, valid.length + 1),
                replaceText(valid, ComparativeScoringMethodology.ID, EndpointScoringMethodology.ID),
                replaceText(valid, ComparativeScoringMethodology.VERSION, "9.0.0"),
                replaceText(valid, ComparativeScoringMethodology.definitionHash(), "f".repeat(64)),
                replaceText(valid, ComparativeScoringInput.class.getName(), "java.lang.Runtime"),
                replaceText(valid, "benchmark", "sector"), replaceText(valid, "4000", "4000.0"),
                EndpointScoringInputCodec.encode(input().endpoint()))) assertInvalid(broken);
        for (int length : List.of(1, 4, 17, 200, valid.length / 2, valid.length - 1)) assertInvalid(Arrays.copyOf(valid, length));
        byte[] badUtf8 = valid.clone();
        badUtf8[8] = (byte) 0xff;
        assertInvalid(badUtf8);
        assertThatThrownBy(() -> EndpointScoringInputCodec.decode(valid)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test void textTotalSizeAndNestedDecodeAreBounded() throws Exception {
        var in = input();
        var ref = in.benchmark().referenceIndexCandidates().getFirst();
        assertThatThrownBy(() -> ComparativeScoringInputCodec.encode(replace(in, "benchmark.referenceIndexCandidates",
                List.of(edit(ref, "referenceIndexLabel", "x".repeat(65537)))))).isInstanceOf(IllegalArgumentException.class);
        var big = edit(ref, "referenceIndexLabel", "x".repeat(65536));
        assertThatThrownBy(() -> ComparativeScoringInputCodec.encode(replace(in, "benchmark.referenceIndexCandidates",
                Collections.nCopies(17, big)))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> ComparativeScoringInputCodec.encode(replace(in, "benchmark.referenceIndexCandidates",
                List.of(edit(ref, "referenceIndexLabel", "bad-\uD800"))))).isInstanceOf(IllegalArgumentException.class);
        var output = new ByteArrayOutputStream();
        var out = new DataOutputStream(output);
        for (String header : List.of("wsr-comparative-input-v1", ComparativeScoringMethodology.ID,
                ComparativeScoringMethodology.VERSION, ComparativeScoringMethodology.definitionHash())) {
            out.writeByte(1); byte[] text = header.getBytes(StandardCharsets.UTF_8); out.writeInt(text.length); out.write(text);
        }
        for (int i = 0; i < 26; i++) { out.writeByte(6); out.writeInt(1); }
        out.writeByte(0);
        assertInvalid(output.toByteArray());
    }

    static String hash(ComparativeScoringInput in) { return ComparativeScoringInputCodec.hash(ComparativeScoringInputCodec.encode(in)); }
    private static void assertInvalid(byte[] bytes) {
        assertThatThrownBy(() -> ComparativeScoringInputCodec.decode(bytes)).isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Invalid comparative scoring input encoding");
    }
    private static byte[] replaceText(byte[] bytes, String old, String replacement) throws RuntimeException {
        byte[] needle = old.getBytes(StandardCharsets.UTF_8), text = replacement.getBytes(StandardCharsets.UTF_8);
        for (int i = 4; i <= bytes.length - needle.length; i++) {
            if (ByteBuffer.wrap(bytes, i - 4, 4).getInt() == needle.length && Arrays.equals(bytes, i, i + needle.length, needle, 0, needle.length)) {
                var result = new ByteArrayOutputStream();
                result.writeBytes(Arrays.copyOfRange(bytes, 0, i - 4));
                result.writeBytes(ByteBuffer.allocate(4).putInt(text.length).array()); result.writeBytes(text);
                result.writeBytes(Arrays.copyOfRange(bytes, i + needle.length, bytes.length));
                return result.toByteArray();
            }
        }
        throw new AssertionError("Test token missing");
    }
}
