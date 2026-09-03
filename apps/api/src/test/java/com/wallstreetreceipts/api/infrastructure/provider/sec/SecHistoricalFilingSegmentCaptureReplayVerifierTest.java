package com.wallstreetreceipts.api.infrastructure.provider.sec;

import static com.wallstreetreceipts.api.support.SecFilingCatalogCaptureTestFixture.capture;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

import com.wallstreetreceipts.api.domain.filing.FilingCatalogCapture;
import com.wallstreetreceipts.api.domain.filing.HistoricalFilingSegment;
import com.wallstreetreceipts.api.domain.filing.HistoricalFilingSegmentCapture;
import com.wallstreetreceipts.api.domain.source.SourceResponseReceipt;
import com.wallstreetreceipts.api.domain.source.SourceResponseReceipt.BodyRetention;

class SecHistoricalFilingSegmentCaptureReplayVerifierTest {

    private static final Instant ROOT_CAPTURED_AT =
            Instant.parse("2026-08-25T01:00:00.123456Z");
    private static final Instant CAPTURED_AT =
            Instant.parse("2026-08-25T02:00:00.654321Z");
    private static final URI SOURCE_URI = URI.create(
            "https://data.sec.gov/submissions/CIK0000320193-submissions-002.json");
    private static final byte[] BODY = validSegmentJson().getBytes(StandardCharsets.UTF_8);

    private final SecHistoricalFilingSegmentCaptureReplayVerifier verifier =
            new SecHistoricalFilingSegmentCaptureReplayVerifier();

    @Test
    void verifiesTheExactOfficialBodyProjectionAgainstItsDurableRoot() throws Exception {
        FilingCatalogCapture root = durableRoot(ROOT_CAPTURED_AT);
        HistoricalFilingSegmentCapture exact = exactCapture(root);

        assertThat(verifier.verify(exact, root)).isSameAs(exact);
        assertThat(verifier.verify(
                exact.withBodyRetention(BodyRetention.DURABLE_DECODED_BODY_RETAINED),
                root).segment().sourceReceipt().bodyRetention())
                .isEqualTo(BodyRetention.DURABLE_DECODED_BODY_RETAINED);
    }

    @Test
    void rejectsAnotherRootAndAProjectionNotParsedFromTheExactBody() throws Exception {
        FilingCatalogCapture root = durableRoot(ROOT_CAPTURED_AT);
        HistoricalFilingSegmentCapture exact = exactCapture(root);
        FilingCatalogCapture anotherRoot = durableRoot(ROOT_CAPTURED_AT.plusSeconds(1));

        assertThatThrownBy(() -> verifier.verify(exact, anotherRoot))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("unsupported historical segment capture parser contract");

        HistoricalFilingSegment segment = exact.segment();
        HistoricalFilingSegment altered = new HistoricalFilingSegment(
                segment.provider(), segment.product(), segment.rootCaptureId(),
                segment.rootCapturedAt(), segment.descriptorOrdinal(), segment.cik(),
                segment.descriptor(), segment.sourceUri(), segment.processingTime(),
                segment.capturedAt(), segment.sourceReceipt(),
                List.of(segment.filings().getFirst()));
        HistoricalFilingSegmentCapture forged = new HistoricalFilingSegmentCapture(
                altered, exact.decodedBody());

        assertThatThrownBy(() -> verifier.verify(forged, root))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("historical segment capture does not match exact-body replay");
    }

    @Test
    void rejectsForgedOriginAndNonUtf8ExactBodies() throws Exception {
        FilingCatalogCapture root = durableRoot(ROOT_CAPTURED_AT);
        HistoricalFilingSegmentCapture exact = exactCapture(root);
        URI evilUri = URI.create(
                "https://evil.example/submissions/CIK0000320193-submissions-002.json");
        HistoricalFilingSegmentCapture forgedOrigin = withEnvelope(
                exact, evilUri, exact.decodedBody());

        assertThatThrownBy(() -> verifier.verify(forgedOrigin, root))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("unsupported historical segment capture source envelope");

        byte[] malformedUtf8 = {(byte) 0xc3, (byte) 0x28};
        HistoricalFilingSegmentCapture nonUtf8 = withEnvelope(
                exact, SOURCE_URI, malformedUtf8);
        assertThatThrownBy(() -> verifier.verify(nonUtf8, root))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("historical segment capture could not be replayed");
    }

    private static FilingCatalogCapture durableRoot(Instant capturedAt) {
        return capture(capturedAt).withBodyRetention(
                BodyRetention.DURABLE_DECODED_BODY_RETAINED);
    }

    private static HistoricalFilingSegmentCapture exactCapture(FilingCatalogCapture root)
            throws Exception {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        return SecHistoricalRawResponseCapture.capture(
                SOURCE_URI, 200, headers, BODY, CAPTURED_AT)
                .toCapture(root, 0, CAPTURED_AT);
    }

    private static HistoricalFilingSegmentCapture withEnvelope(
            HistoricalFilingSegmentCapture original,
            URI sourceUri,
            byte[] body) {
        HistoricalFilingSegment segment = original.segment();
        SourceResponseReceipt receipt = new SourceResponseReceipt(
                segment.sourceReceipt().provider(),
                segment.sourceReceipt().product(),
                sourceUri,
                segment.sourceReceipt().httpStatus(),
                segment.sourceReceipt().mediaType(),
                segment.sourceReceipt().transportContentEncoding(),
                segment.sourceReceipt().etag(),
                segment.sourceReceipt().lastModified(),
                segment.sourceReceipt().parserVersion(),
                sha256(body),
                body.length,
                segment.sourceReceipt().capturedAt(),
                segment.sourceReceipt().bodyRepresentation(),
                segment.sourceReceipt().bodyRetention());
        HistoricalFilingSegment changed = new HistoricalFilingSegment(
                segment.provider(), segment.product(), segment.rootCaptureId(),
                segment.rootCapturedAt(), segment.descriptorOrdinal(), segment.cik(),
                segment.descriptor(), sourceUri, segment.processingTime(),
                segment.capturedAt(), receipt, segment.filings());
        return new HistoricalFilingSegmentCapture(changed, body);
    }

    private static String sha256(byte[] body) {
        try {
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(body));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(exception);
        }
    }

    private static String validSegmentJson() {
        return """
                {
                  "accessionNumber": ["0000320193-20-000002", "0000320193-15-000001"],
                  "filingDate": ["2020-12-31", "2015-01-01"],
                  "reportDate": ["2020-09-26", ""],
                  "acceptanceDateTime": ["2020-12-31T20:00:00.123456Z", "2015-01-01T12:00:00Z"],
                  "act": ["34", "34"],
                  "form": ["10-K", "10-K"],
                  "fileNumber": ["001-36743", "001-36743"],
                  "filmNumber": ["201234567", "151234568"],
                  "items": ["", ""],
                  "size": [123456, 234567],
                  "isXBRL": [1, 1],
                  "isInlineXBRL": [1, 0],
                  "primaryDocument": ["form2020.htm", "form2015.htm"],
                  "primaryDocDescription": ["Annual report", "Annual report"]
                }
                """;
    }
}
