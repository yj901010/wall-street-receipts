package com.wallstreetreceipts.api.domain.source;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.net.URI;
import java.time.Instant;

import org.junit.jupiter.api.Test;

import com.wallstreetreceipts.api.domain.source.SourceResponseReceipt.BodyRepresentation;
import com.wallstreetreceipts.api.domain.source.SourceResponseReceipt.BodyRetention;
import com.wallstreetreceipts.api.domain.source.SourceResponseReceipt.TransportContentEncoding;

class SourceResponseReceiptTest {

    private static final URI SOURCE_URI = URI.create(
            "https://data.sec.gov/submissions/CIK0000320193.json");
    private static final Instant CAPTURED_AT =
            Instant.parse("2026-08-25T01:02:03.123456Z");

    @Test
    void rejectsNonCanonicalDigestStatusAndLength() {
        assertThatThrownBy(() -> receipt(201, "0".repeat(64), 1, CAPTURED_AT))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("httpStatus must be 200");
        assertThatThrownBy(() -> receipt(200, "A".repeat(64), 1, CAPTURED_AT))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("decodedBodySha256 must be lowercase SHA-256 hex");
        assertThatThrownBy(() -> receipt(200, "0".repeat(64), 0, CAPTURED_AT))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("decodedBodyLength must be positive");
    }

    @Test
    void rejectsUnsafeSourceUriAndSubMicrosecondCaptureTime() {
        assertThatThrownBy(() -> new SourceResponseReceipt(
                "sec-edgar",
                "edgar-submissions-api",
                URI.create("https://user@example.test/source?secret=value"),
                200,
                "application/json",
                TransportContentEncoding.IDENTITY,
                null,
                null,
                "SEC_SUBMISSIONS_RECENT_V1",
                "0".repeat(64),
                1,
                CAPTURED_AT,
                BodyRepresentation.DECODED_HTTP_ENTITY_BODY,
                BodyRetention.RECEIPT_ONLY_BODY_NOT_RETAINED))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("without credentials or suffixes");
        assertThatThrownBy(() -> receipt(
                200, "0".repeat(64), 1, CAPTURED_AT.plusNanos(1)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("microsecond precision");
    }

    @Test
    void rejectsUnsafeOpaqueValidator() {
        assertThatThrownBy(() -> new SourceResponseReceipt(
                "sec-edgar",
                "edgar-submissions-api",
                SOURCE_URI,
                200,
                "application/json",
                TransportContentEncoding.IDENTITY,
                "\"safe\"\r\nsecret: value",
                null,
                "SEC_SUBMISSIONS_RECENT_V1",
                "0".repeat(64),
                1,
                CAPTURED_AT,
                BodyRepresentation.DECODED_HTTP_ENTITY_BODY,
                BodyRetention.RECEIPT_ONLY_BODY_NOT_RETAINED))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("etag must be a bounded opaque validator");
    }

    private static SourceResponseReceipt receipt(
            int httpStatus,
            String digest,
            long length,
            Instant capturedAt) {
        return new SourceResponseReceipt(
                "sec-edgar",
                "edgar-submissions-api",
                SOURCE_URI,
                httpStatus,
                "application/json",
                TransportContentEncoding.IDENTITY,
                null,
                null,
                "SEC_SUBMISSIONS_RECENT_V1",
                digest,
                length,
                capturedAt,
                BodyRepresentation.DECODED_HTTP_ENTITY_BODY,
                BodyRetention.RECEIPT_ONLY_BODY_NOT_RETAINED);
    }
}
