package com.wallstreetreceipts.api.domain.filing;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.Objects;

import com.wallstreetreceipts.api.domain.source.SourceResponseReceipt;
import com.wallstreetreceipts.api.domain.source.SourceResponseReceipt.BodyRetention;

/** Immutable historical-segment projection bound to the exact decoded bytes parsed for it. */
public final class HistoricalFilingSegmentCapture {

    public static final String SCHEMA_VERSION = "1.0.0";

    private static final String IDENTITY_VERSION =
            "SEC_HISTORICAL_FILING_SEGMENT_CAPTURE_ID_V1";
    private static final HexFormat LOWERCASE_HEX = HexFormat.of();

    private final String captureId;
    private final HistoricalFilingSegment segment;
    private final byte[] decodedBody;

    public HistoricalFilingSegmentCapture(
            HistoricalFilingSegment segment,
            byte[] decodedBody) {
        this.segment = Objects.requireNonNull(segment, "segment must not be null");
        Objects.requireNonNull(decodedBody, "decodedBody must not be null");
        this.decodedBody = decodedBody.clone();

        SourceResponseReceipt receipt = segment.sourceReceipt();
        if (receipt.bodyRetention() != BodyRetention.DECODED_BODY_ATTACHED_PENDING_PERSISTENCE
                && receipt.bodyRetention() != BodyRetention.DURABLE_DECODED_BODY_RETAINED) {
            throw new IllegalArgumentException(
                    "sourceReceipt must declare an attached decoded body");
        }
        if (receipt.decodedBodyLength() != this.decodedBody.length) {
            throw new IllegalArgumentException(
                    "decodedBody length must match sourceReceipt");
        }
        String digest = sha256(this.decodedBody);
        if (!receipt.decodedBodySha256().equals(digest)) {
            throw new IllegalArgumentException(
                    "decodedBody digest must match sourceReceipt");
        }
        this.captureId = captureId(segment);
    }

    public String captureId() {
        return captureId;
    }

    public HistoricalFilingSegment segment() {
        return segment;
    }

    public byte[] decodedBody() {
        return decodedBody.clone();
    }

    public HistoricalFilingSegmentCapture withBodyRetention(BodyRetention retention) {
        if (retention != BodyRetention.DECODED_BODY_ATTACHED_PENDING_PERSISTENCE
                && retention != BodyRetention.DURABLE_DECODED_BODY_RETAINED) {
            throw new IllegalArgumentException(
                    "capture body retention must keep the decoded body attached");
        }
        HistoricalFilingSegment retainedSegment = new HistoricalFilingSegment(
                segment.provider(),
                segment.product(),
                segment.rootCaptureId(),
                segment.rootCapturedAt(),
                segment.descriptorOrdinal(),
                segment.cik(),
                segment.descriptor(),
                segment.sourceUri(),
                segment.processingTime(),
                segment.capturedAt(),
                segment.sourceReceipt().withBodyRetention(retention),
                segment.filings());
        return new HistoricalFilingSegmentCapture(retainedSegment, decodedBody);
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof HistoricalFilingSegmentCapture that)) {
            return false;
        }
        return captureId.equals(that.captureId)
                && segment.equals(that.segment)
                && Arrays.equals(decodedBody, that.decodedBody);
    }

    @Override
    public int hashCode() {
        return 31 * Objects.hash(captureId, segment) + Arrays.hashCode(decodedBody);
    }

    @Override
    public String toString() {
        return "HistoricalFilingSegmentCapture[captureId=" + captureId
                + ", segment=" + segment
                + ", decodedBody=<redacted>]";
    }

    private static String captureId(HistoricalFilingSegment segment) {
        HistoricalFilingSegmentDescriptor descriptor = segment.descriptor();
        String canonicalIdentity = lengthPrefixed(IDENTITY_VERSION)
                + lengthPrefixed(segment.provider())
                + lengthPrefixed(segment.product())
                + lengthPrefixed(segment.rootCaptureId())
                + lengthPrefixed(segment.rootCapturedAt().toString())
                + lengthPrefixed(Integer.toString(segment.descriptorOrdinal()))
                + lengthPrefixed(segment.cik())
                + lengthPrefixed(descriptor.fileName())
                + lengthPrefixed(Long.toString(descriptor.advertisedFilingCount()))
                + lengthPrefixed(descriptor.advertisedFilingFrom().toString())
                + lengthPrefixed(descriptor.advertisedFilingTo().toString())
                + lengthPrefixed(segment.sourceUri().toASCIIString())
                + lengthPrefixed(segment.processingTime().toString())
                + lengthPrefixed(segment.capturedAt().toString())
                + lengthPrefixed(segment.sourceReceipt().decodedBodySha256())
                + lengthPrefixed(Long.toString(
                        segment.sourceReceipt().decodedBodyLength()));
        return sha256(canonicalIdentity.getBytes(StandardCharsets.UTF_8));
    }

    private static String lengthPrefixed(String value) {
        byte[] encoded = value.getBytes(StandardCharsets.UTF_8);
        return encoded.length + ":" + value;
    }

    private static String sha256(byte[] content) {
        try {
            return LOWERCASE_HEX.formatHex(
                    MessageDigest.getInstance("SHA-256").digest(content));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }
}
