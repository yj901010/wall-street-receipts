package com.wallstreetreceipts.api.application.scoring;

import static org.assertj.core.api.Assertions.*;
import static com.wallstreetreceipts.api.application.scoring.EndpointScoringFixture.*;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import com.wallstreetreceipts.api.domain.outcome.targeteligibility.BasisForecastTermsEvidence.TargetDisposition;

class EndpointScoringInputCodecTest {
    @Test void exactCanonicalRoundTripRetainsOldGoldenHashAndMissingValues() {
        var i = input(); var encoded = EndpointScoringInputCodec.encode(i);
        assertThat(EndpointScoringInputCodec.hash(encoded)).isEqualTo("c538a04a0eb74074c0d3e50af94aa4ca965b025ab20ec1df182fdc22e4df760b");
        assertThat(EndpointScoringInputCodec.encode(EndpointScoringInputCodec.decode(encoded))).isEqualTo(encoded);
        var absent = edit(edit(i, "targetEvidence", null), "terms", edit(i.terms(), "targetDisposition", new TargetDisposition.Absent()));
        assertThat(EndpointScoringInputCodec.decode(EndpointScoringInputCodec.encode(absent)).targetEvidence()).isNull();
    }
    @ParameterizedTest @ValueSource(ints = {0,1,2,4,10,100,500,1000,2000,4000})
    void truncationFailsWithoutEchoingPayload(int length) {
        reject(Arrays.copyOf(EndpointScoringInputCodec.encode(input()), length));
    }
    @ParameterizedTest @ValueSource(ints = {-1,0,1,262145,Integer.MAX_VALUE,Integer.MIN_VALUE})
    void invalidStringLengthIsBoundedBeforeAllocation(int length) {
        var bytes = EndpointScoringInputCodec.encode(input()); ByteBuffer.wrap(bytes, 1, 4).putInt(length); reject(bytes);
    }
    @ParameterizedTest @ValueSource(strings = {"profile", "type", "enum", "tag", "utf8", "trailing", "decimal", "field", "oversize"})
    void rejectsUnknownTypesNonCanonicalValuesAndMalformedBytes(String mutation) {
        var bytes = EndpointScoringInputCodec.encode(input());
        switch (mutation) {
            case "profile" -> bytes[5] = 'x';
            case "tag" -> bytes[0] = 99;
            case "utf8" -> bytes[5] = (byte) 0xff;
            case "trailing" -> bytes = Arrays.copyOf(bytes, bytes.length + 1);
            case "oversize" -> bytes = new byte[1048577];
            case "type" -> bytes = replace(bytes, "com.wallstreetreceipts.api.application.scoring.EndpointScoringInput", "com.wallstreetreceipts.api.application.scoring.EndpointScoringInpuX");
            case "enum" -> bytes = replace(bytes, "BULLISH", "INVALID");
            case "field" -> bytes = replace(bytes, "dataMode", "dataModX");
            case "decimal" -> bytes = replace(bytes, "150", "1e2");
        }
        reject(bytes);
    }
    private static byte[] replace(byte[] bytes, String from, String to) {
        // ASCII substitutions keep encoded lengths intact and preserve non-text byte values.
        return new String(bytes, StandardCharsets.ISO_8859_1).replace(from, to).getBytes(StandardCharsets.ISO_8859_1);
    }
    private static void reject(byte[] bytes) {
        assertThatThrownBy(() -> EndpointScoringInputCodec.decode(bytes)).isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Invalid stored scoring input").hasNoCause();
    }
}
