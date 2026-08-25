package com.wallstreetreceipts.api.domain.filing;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.Objects;

import com.wallstreetreceipts.api.domain.source.SourceResponseReceipt;
import com.wallstreetreceipts.api.domain.source.SourceResponseReceipt.BodyRetention;

/** Immutable root catalog projection bound to the exact decoded bytes parsed for it. */
public final class FilingCatalogCapture {

    public static final String SCHEMA_VERSION = "1.0.0";

    private static final String IDENTITY_VERSION = "SEC_FILING_CATALOG_CAPTURE_ID_V1";
    private static final HexFormat LOWERCASE_HEX = HexFormat.of();

    private final String captureId;
    private final FilingCatalog catalog;
    private final byte[] decodedBody;

    public FilingCatalogCapture(FilingCatalog catalog, byte[] decodedBody) {
        this.catalog = Objects.requireNonNull(catalog, "catalog must not be null");
        Objects.requireNonNull(decodedBody, "decodedBody must not be null");
        this.decodedBody = decodedBody.clone();

        SourceResponseReceipt receipt = catalog.sourceReceipt();
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
        this.captureId = captureId(catalog);
    }

    public String captureId() {
        return captureId;
    }

    public FilingCatalog catalog() {
        return catalog;
    }

    public byte[] decodedBody() {
        return decodedBody.clone();
    }

    public FilingCatalogCapture withBodyRetention(BodyRetention retention) {
        if (retention != BodyRetention.DECODED_BODY_ATTACHED_PENDING_PERSISTENCE
                && retention != BodyRetention.DURABLE_DECODED_BODY_RETAINED) {
            throw new IllegalArgumentException(
                    "capture body retention must keep the decoded body attached");
        }
        SourceResponseReceipt retainedReceipt = catalog.sourceReceipt()
                .withBodyRetention(retention);
        FilingCatalog retainedCatalog = new FilingCatalog(
                catalog.provider(),
                catalog.product(),
                catalog.cik(),
                catalog.sourceUri(),
                catalog.processingTime(),
                catalog.capturedAt(),
                retainedReceipt,
                catalog.recentFilings(),
                catalog.historicalSegments());
        return new FilingCatalogCapture(retainedCatalog, decodedBody);
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof FilingCatalogCapture that)) {
            return false;
        }
        return captureId.equals(that.captureId)
                && catalog.equals(that.catalog)
                && Arrays.equals(decodedBody, that.decodedBody);
    }

    @Override
    public int hashCode() {
        return 31 * Objects.hash(captureId, catalog) + Arrays.hashCode(decodedBody);
    }

    @Override
    public String toString() {
        return "FilingCatalogCapture[captureId=" + captureId
                + ", catalog=" + catalog
                + ", decodedBody=<redacted>]";
    }

    private static String captureId(FilingCatalog catalog) {
        String canonicalIdentity = lengthPrefixed(IDENTITY_VERSION)
                + lengthPrefixed(catalog.provider())
                + lengthPrefixed(catalog.product())
                + lengthPrefixed(catalog.cik())
                + lengthPrefixed(catalog.sourceUri().toASCIIString())
                + lengthPrefixed(catalog.capturedAt().toString())
                + lengthPrefixed(catalog.sourceReceipt().decodedBodySha256())
                + lengthPrefixed(Long.toString(
                        catalog.sourceReceipt().decodedBodyLength()));
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
