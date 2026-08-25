package com.wallstreetreceipts.api.domain.filing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.wallstreetreceipts.api.domain.source.SourceResponseReceipt;
import com.wallstreetreceipts.api.domain.source.SourceResponseReceipt.BodyRepresentation;
import com.wallstreetreceipts.api.domain.source.SourceResponseReceipt.BodyRetention;
import com.wallstreetreceipts.api.domain.source.SourceResponseReceipt.TransportContentEncoding;

class HistoricalFilingSegmentCaptureTest {

    private static final Instant ROOT_CAPTURED_AT =
            Instant.parse("2026-08-25T01:02:03.123456Z");
    private static final Instant CAPTURED_AT =
            Instant.parse("2026-08-25T01:03:03.654321Z");
    private static final byte[] BODY = "{\"accessionNumber\":[]}".getBytes(StandardCharsets.UTF_8);

    @Test
    void ownsExactBytesAndRedactsThemFromItsStringRepresentation() {
        HistoricalFilingSegmentCapture capture = capture(CAPTURED_AT);
        byte[] exposed = capture.decodedBody();
        byte first = exposed[0];

        exposed[0] = 'X';

        assertThat(capture.decodedBody()[0]).isEqualTo(first);
        assertThat(capture.toString())
                .contains("decodedBody=<redacted>")
                .doesNotContain("{\"accessionNumber\":[]}");
        assertThat(capture.captureId()).matches("[0-9a-f]{64}");
    }

    @Test
    void changesIdentityForAnotherObservationButNotForRetentionPromotion() {
        HistoricalFilingSegmentCapture original = capture(CAPTURED_AT);
        HistoricalFilingSegmentCapture promoted = original.withBodyRetention(
                BodyRetention.DURABLE_DECODED_BODY_RETAINED);
        HistoricalFilingSegmentCapture later = capture(CAPTURED_AT.plusSeconds(1));

        assertThat(promoted.captureId()).isEqualTo(original.captureId());
        assertThat(promoted.segment().sourceReceipt().bodyRetention())
                .isEqualTo(BodyRetention.DURABLE_DECODED_BODY_RETAINED);
        assertThat(later.captureId()).isNotEqualTo(original.captureId());
    }

    @Test
    void rejectsReceiptOnlyLengthDigestAndDetachedRetention() {
        HistoricalFilingSegmentCapture pending = capture(CAPTURED_AT);
        HistoricalFilingSegment segment = pending.segment();
        HistoricalFilingSegment receiptOnly = withReceipt(segment,
                segment.sourceReceipt().withBodyRetention(
                        BodyRetention.RECEIPT_ONLY_BODY_NOT_RETAINED));

        assertThatThrownBy(() -> new HistoricalFilingSegmentCapture(receiptOnly, BODY))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("sourceReceipt must declare an attached decoded body");

        byte[] truncated = Arrays.copyOf(BODY, BODY.length - 1);
        assertThatThrownBy(() -> new HistoricalFilingSegmentCapture(segment, truncated))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("decodedBody length must match sourceReceipt");

        byte[] changed = BODY.clone();
        changed[0] = '[';
        assertThatThrownBy(() -> new HistoricalFilingSegmentCapture(segment, changed))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("decodedBody digest must match sourceReceipt");

        assertThatThrownBy(() -> pending.withBodyRetention(
                BodyRetention.RECEIPT_ONLY_BODY_NOT_RETAINED))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("capture body retention must keep the decoded body attached");
    }

    private static HistoricalFilingSegmentCapture capture(Instant capturedAt) {
        URI sourceUri = URI.create(
                "https://data.sec.gov/submissions/CIK0000320193-submissions-001.json");
        SourceResponseReceipt receipt = new SourceResponseReceipt(
                "sec-edgar",
                "edgar-submissions-historical-segment-api",
                sourceUri,
                200,
                "application/json",
                TransportContentEncoding.GZIP,
                "\"historical-fixture\"",
                Instant.parse("2026-08-25T01:03:00Z"),
                "SEC_SUBMISSIONS_HISTORICAL_SEGMENT_V1",
                sha256(BODY),
                BODY.length,
                capturedAt,
                BodyRepresentation.DECODED_HTTP_ENTITY_BODY,
                BodyRetention.DECODED_BODY_ATTACHED_PENDING_PERSISTENCE);
        HistoricalFilingSegment segment = new HistoricalFilingSegment(
                "sec-edgar",
                "edgar-submissions-historical-segment-api",
                "a".repeat(64),
                ROOT_CAPTURED_AT,
                0,
                "0000320193",
                new HistoricalFilingSegmentDescriptor(
                        "CIK0000320193-submissions-001.json",
                        1,
                        LocalDate.parse("2015-01-01"),
                        LocalDate.parse("2015-01-01")),
                sourceUri,
                ROOT_CAPTURED_AT.plusSeconds(30),
                capturedAt,
                receipt,
                List.of());
        return new HistoricalFilingSegmentCapture(segment, BODY);
    }

    private static HistoricalFilingSegment withReceipt(
            HistoricalFilingSegment segment,
            SourceResponseReceipt receipt) {
        return new HistoricalFilingSegment(
                segment.provider(), segment.product(), segment.rootCaptureId(),
                segment.rootCapturedAt(), segment.descriptorOrdinal(), segment.cik(),
                segment.descriptor(), segment.sourceUri(), segment.processingTime(),
                segment.capturedAt(), receipt, segment.filings());
    }

    private static String sha256(byte[] body) {
        try {
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(body));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(exception);
        }
    }
}
