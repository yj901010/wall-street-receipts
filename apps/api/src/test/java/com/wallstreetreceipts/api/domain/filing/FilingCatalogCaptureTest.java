package com.wallstreetreceipts.api.domain.filing;

import static com.wallstreetreceipts.api.support.SecFilingCatalogCaptureTestFixture.capture;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;

import org.junit.jupiter.api.Test;

import com.wallstreetreceipts.api.domain.source.SourceResponseReceipt.BodyRetention;

class FilingCatalogCaptureTest {

    private static final Instant CAPTURED_AT =
            Instant.parse("2026-08-25T01:02:03.123456Z");

    @Test
    void ownsExactBytesAndRedactsThemFromItsStringRepresentation() {
        FilingCatalogCapture capture = capture(CAPTURED_AT);
        byte[] exposed = capture.decodedBody();
        byte first = exposed[0];

        exposed[0] = 'X';

        assertThat(capture.decodedBody()[0]).isEqualTo(first);
        assertThat(capture.toString())
                .contains("decodedBody=<redacted>")
                .doesNotContain("accessionNumber\"");
        assertThat(capture.captureId()).matches("[0-9a-f]{64}");
    }

    @Test
    void changesIdentityForAnotherObservationButNotForRetentionPromotion() {
        FilingCatalogCapture original = capture(CAPTURED_AT);
        FilingCatalogCapture promoted = original.withBodyRetention(
                BodyRetention.DURABLE_DECODED_BODY_RETAINED);
        FilingCatalogCapture later = capture(CAPTURED_AT.plusSeconds(1));

        assertThat(promoted.captureId()).isEqualTo(original.captureId());
        assertThat(promoted.catalog().sourceReceipt().bodyRetention())
                .isEqualTo(BodyRetention.DURABLE_DECODED_BODY_RETAINED);
        assertThat(later.captureId()).isNotEqualTo(original.captureId());
    }

    @Test
    void rejectsAReceiptOnlyDeclarationWhenTheBodyIsAttached() {
        FilingCatalogCapture attached = capture(CAPTURED_AT);
        FilingCatalog catalog = attached.catalog();
        FilingCatalog receiptOnlyCatalog = new FilingCatalog(
                catalog.provider(), catalog.product(), catalog.cik(), catalog.sourceUri(),
                catalog.processingTime(), catalog.capturedAt(),
                catalog.sourceReceipt().withBodyRetention(
                        BodyRetention.RECEIPT_ONLY_BODY_NOT_RETAINED),
                catalog.recentFilings(), catalog.historicalSegments());

        assertThatThrownBy(() -> new FilingCatalogCapture(
                receiptOnlyCatalog, attached.decodedBody()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("sourceReceipt must declare an attached decoded body");
    }

    @Test
    void rejectsBodyLengthAndDigestMismatches() {
        FilingCatalogCapture attached = capture(CAPTURED_AT);
        byte[] truncated = attached.decodedBody();
        truncated = java.util.Arrays.copyOf(truncated, truncated.length - 1);
        byte[] finalTruncated = truncated;

        assertThatThrownBy(() -> new FilingCatalogCapture(
                attached.catalog(), finalTruncated))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("decodedBody length must match sourceReceipt");

        byte[] changed = attached.decodedBody();
        changed[0] = (byte) (changed[0] == '{' ? '[' : '{');
        assertThatThrownBy(() -> new FilingCatalogCapture(
                attached.catalog(), changed))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("decodedBody digest must match sourceReceipt");
    }
}
