package com.wallstreetreceipts.api.application.scoring;

import java.math.BigDecimal;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static com.wallstreetreceipts.api.application.scoring.EndpointScoringFixture.*;
import static org.junit.jupiter.api.Assertions.*;

class TargetHitScoringInputCodecTest {
    @Test void pinsMethodologyAndSyntheticInputWithoutChangingOldIdentities() {
        var input = TargetHitScoringFixture.input();
        var bytes = TargetHitScoringInputCodec.encode(input);
        assertEquals("6fb2d737d177662ec072277f1e345ca10a2c9447d35353bc46c1f886669ad6d2", ComparativeScoringMethodology.definitionHash());
        assertEquals("507028d10501990999b8af3dd56835499d283544d8e8b6b739252a746edff4a0",
                ComparativeScoringInputCodec.hash(ComparativeScoringInputCodec.encode(input.comparative())));
        assertAll(
            () -> assertEquals("aaa8684e12a5d83df36eff5a15107828931526b36ccfcca71ac8f968804b1b1e", TargetHitScoringMethodology.definitionHash()),
            () -> assertEquals("17cb039635703b3d29fde5bd0b32c7b30916d2e95f2b52ebe6fde6774142461c", TargetHitScoringInputCodec.hash(bytes)));
        assertEquals(5, TargetHitScoringMethodology.canonicalDefinition().lines().filter(l -> l.matches(".+=[a-f0-9]{64}")).count());
    }

    @Test void roundTripRetainsEveryInputAndReplaysSameReceipt() {
        var original = TargetHitScoringFixture.input();
        byte[] bytes = TargetHitScoringInputCodec.encode(original);
        var decoded = TargetHitScoringInputCodec.decode(bytes);
        assertEquals(original, decoded);
        assertArrayEquals(bytes, TargetHitScoringInputCodec.encode(decoded));
        var evaluator = new TargetHitScoringEvaluator();
        assertEquals(evaluator.evaluate(original).targetHit(), evaluator.evaluate(decoded).targetHit());
        assertEquals(evaluator.evaluate(original).inputFingerprint(), evaluator.evaluate(decoded).inputFingerprint());
        bytes[0] ^= 1;
        assertArrayEquals(TargetHitScoringInputCodec.encode(original), TargetHitScoringInputCodec.encode(decoded));
    }

    @Test void optionalMissingBindingAndEmptyWindowRoundTripWithoutDefaults() {
        var input = TargetHitScoringFixture.input();
        input = new TargetHitScoringInput(input.comparative(), null, List.of());
        assertEquals(input, TargetHitScoringInputCodec.decode(TargetHitScoringInputCodec.encode(input)));
    }

    @Test void decimalScaleDoesNotChangeCanonicalBytesAndNoValueIsRounded() {
        var input = TargetHitScoringFixture.input();
        var observation = edit(input.windowCandidates().getFirst(), "windowHigh", new BigDecimal("160.000000000000"));
        var scaled = edit(input, "windowCandidates", List.of(observation));
        assertArrayEquals(TargetHitScoringInputCodec.encode(input), TargetHitScoringInputCodec.encode(scaled));
        var changed = edit(input, "windowCandidates", List.of(edit(observation, "windowHigh", new BigDecimal("160.000000000001"))));
        assertNotEquals(hash(input), hash(changed));
    }

    @Test void futureRejectedMetadataAndOrderingAreFingerprintInputs() {
        var input = TargetHitScoringFixture.input();
        var original = input.windowCandidates().getFirst();
        var future = edit(edit(original, "observationId", "demo-future"), "capturedAt", AS_OF.plusNanos(1000));
        var supplied = edit(input, "windowCandidates", List.of(original, future));
        assertNotEquals(hash(input), hash(supplied));
        assertNotEquals(hash(supplied), hash(edit(input, "windowCandidates", List.of(future, original))));
        assertNotEquals(hash(input), hash(edit(input, "windowCandidates",
                List.of(edit(original, "provenanceId", "different-synthetic-claim")))));
        assertEquals(supplied, TargetHitScoringInputCodec.decode(TargetHitScoringInputCodec.encode(supplied)));
    }

    @Test void profilesCannotDecodeOneAnother() {
        var input = TargetHitScoringFixture.input();
        var bytes = TargetHitScoringInputCodec.encode(input);
        assertThrows(IllegalArgumentException.class, () -> ComparativeScoringInputCodec.decode(bytes));
        assertThrows(IllegalArgumentException.class, () -> EndpointScoringInputCodec.decode(bytes));
        assertThrows(IllegalArgumentException.class, () -> TargetHitScoringInputCodec.decode(ComparativeScoringInputCodec.encode(input.comparative())));
        assertThrows(IllegalArgumentException.class, () -> TargetHitScoringInputCodec.decode(EndpointScoringInputCodec.encode(input.comparative().endpoint())));
    }

    @Test void unknownRecordEnumFieldHeaderAndNoncanonicalDecimalFailClosed() {
        var bytes = TargetHitScoringInputCodec.encode(TargetHitScoringFixture.input());
        for (var pair : List.of(
                List.of("wsr-target-hit-input-v1", "wsr-target-hit-input-v2"),
                List.of("windowCandidates", "windowCandidatez"),
                List.of("windowHigh", "windowHIGH"),
                List.of("EXCLUSIVE", "UNKNOWN_ENUM"),
                List.of("com.wallstreetreceipts.api.domain.outcome.favorableextreme.FullWindowHighLowObservation", "java.lang.Runtime"),
                List.of("160", "160.0"))) {
            byte[] changed = replaceText(bytes, pair.getFirst(), pair.getLast());
            assertThrows(IllegalArgumentException.class, () -> TargetHitScoringInputCodec.decode(changed), pair.toString());
        }
    }

    @Test void malformedAndOversizedInputIsBounded() {
        var bytes = TargetHitScoringInputCodec.encode(TargetHitScoringFixture.input());
        assertThrows(NullPointerException.class, () -> TargetHitScoringInputCodec.decode(null));
        assertThrows(IllegalArgumentException.class, () -> TargetHitScoringInputCodec.decode(new byte[0]));
        assertThrows(IllegalArgumentException.class, () -> TargetHitScoringInputCodec.decode(new byte[1048577]));
        assertThrows(IllegalArgumentException.class, () -> TargetHitScoringInputCodec.decode(Arrays.copyOf(bytes, bytes.length + 1)));
        for (int length = 0; length < bytes.length; length += 137) {
            byte[] truncated = Arrays.copyOf(bytes, length);
            assertThrows(IllegalArgumentException.class, () -> TargetHitScoringInputCodec.decode(truncated));
        }
        for (int size : new int[] {-1, Integer.MAX_VALUE, 262145}) {
            byte[] invalid = bytes.clone();
            ByteBuffer.wrap(invalid).putInt(1, size);
            assertThrows(IllegalArgumentException.class, () -> TargetHitScoringInputCodec.decode(invalid));
        }
        byte[] utf8 = bytes.clone();
        utf8[5] = (byte) 0xff;
        assertThrows(IllegalArgumentException.class, () -> TargetHitScoringInputCodec.decode(utf8));
        assertThrows(IllegalArgumentException.class, () -> TargetHitScoringInputCodec.encode(
                edit(TargetHitScoringFixture.input(), "windowCandidates",
                        Collections.nCopies(4096, TargetHitScoringFixture.input().windowCandidates().getFirst()))));
    }

    @Test void rejectsDeepNestingUnknownTagsAndNoncanonicalInstant() {
        byte[] bytes = TargetHitScoringInputCodec.encode(TargetHitScoringFixture.input());
        int headerEnd = 0;
        for (int i = 0; i < 4; i++) headerEnd += 5 + ByteBuffer.wrap(bytes).getInt(headerEnd + 1);
        var nested = ByteBuffer.allocate(headerEnd + 26 * 5 + 1);
        nested.put(bytes, 0, headerEnd);
        for (int i = 0; i < 26; i++) nested.put((byte) 6).putInt(1);
        nested.put((byte) 0);
        assertThrows(IllegalArgumentException.class, () -> TargetHitScoringInputCodec.decode(nested.array()));
        byte[] unknown = bytes.clone();
        unknown[headerEnd] = (byte) 99;
        assertThrows(IllegalArgumentException.class, () -> TargetHitScoringInputCodec.decode(unknown));
        assertThrows(IllegalArgumentException.class, () -> TargetHitScoringInputCodec.decode(
                replaceText(bytes, "2026-01-05T15:00:00Z", "2026-01-05T15:00:00+00:00")));
    }

    @Test void textBoundsAndInvalidUtf16AreRejectedWithoutReplacement() {
        var input = TargetHitScoringFixture.input();
        for (String value : List.of("x".repeat(65537), "\uD800")) {
            var candidate = edit(input.windowCandidates().getFirst(), "provenanceId", value);
            assertThrows(IllegalArgumentException.class, () -> TargetHitScoringInputCodec.encode(edit(input, "windowCandidates", List.of(candidate))));
        }
        var atLimit = edit(input.windowCandidates().getFirst(), "provenanceId", "x".repeat(65536));
        var valid = edit(input, "windowCandidates", List.of(atLimit));
        assertEquals(valid, TargetHitScoringInputCodec.decode(TargetHitScoringInputCodec.encode(valid)));
    }

    @ParameterizedTest @ValueSource(strings = {"ko-KR", "tr-TR", "ar-EG"})
    void canonicalBytesDoNotDependOnLocaleOrDefaultZone(String tag) {
        Locale previous = Locale.getDefault();
        TimeZone zone = TimeZone.getDefault();
        var input = TargetHitScoringFixture.input();
        byte[] expected = TargetHitScoringInputCodec.encode(input);
        try {
            Locale.setDefault(Locale.forLanguageTag(tag));
            TimeZone.setDefault(TimeZone.getTimeZone("Pacific/Apia"));
            assertArrayEquals(expected, TargetHitScoringInputCodec.encode(input));
            assertEquals(input, TargetHitScoringInputCodec.decode(expected));
        } finally { Locale.setDefault(previous); TimeZone.setDefault(zone); }
    }

    private static String hash(TargetHitScoringInput input) {
        return TargetHitScoringInputCodec.hash(TargetHitScoringInputCodec.encode(input));
    }
    private static byte[] replaceText(byte[] bytes, String before, String after) {
        byte[] old = before.getBytes(StandardCharsets.UTF_8);
        byte[] replacement = after.getBytes(StandardCharsets.UTF_8);
        for (int i = 4; i <= bytes.length - old.length; i++) {
            if (ByteBuffer.wrap(bytes).getInt(i - 4) == old.length
                    && Arrays.equals(old, Arrays.copyOfRange(bytes, i, i + old.length))) {
                var buffer = ByteBuffer.allocate(bytes.length - old.length + replacement.length);
                buffer.put(bytes, 0, i - 4).putInt(replacement.length).put(replacement)
                        .put(bytes, i + old.length, bytes.length - i - old.length);
                return buffer.array();
            }
        }
        throw new AssertionError("Text field not present: " + before);
    }
}
