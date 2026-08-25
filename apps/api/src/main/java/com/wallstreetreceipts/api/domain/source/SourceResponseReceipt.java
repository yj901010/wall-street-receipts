package com.wallstreetreceipts.api.domain.source;

import java.net.URI;
import java.time.Instant;
import java.util.Objects;
import java.util.regex.Pattern;

import com.wallstreetreceipts.api.domain.PersistentInstant;

/** Immutable evidence describing the exact bytes supplied to a source parser. */
public record SourceResponseReceipt(
        String provider,
        String product,
        URI sourceUri,
        int httpStatus,
        String mediaType,
        TransportContentEncoding transportContentEncoding,
        String etag,
        Instant lastModified,
        String parserVersion,
        String decodedBodySha256,
        long decodedBodyLength,
        Instant capturedAt,
        BodyRepresentation bodyRepresentation,
        BodyRetention bodyRetention) {

    private static final Pattern SHA_256 = Pattern.compile("[0-9a-f]{64}");
    private static final Pattern ENTITY_TAG = Pattern.compile(
            "^(?:W/)?\"[\\x21\\x23-\\x7e]*\"$");

    public SourceResponseReceipt {
        requireCanonicalText(provider, "provider");
        requireCanonicalText(product, "product");
        requireAbsoluteHttpUri(sourceUri);
        if (httpStatus != 200) {
            throw new IllegalArgumentException("httpStatus must be 200");
        }
        requireCanonicalText(mediaType, "mediaType");
        Objects.requireNonNull(
                transportContentEncoding,
                "transportContentEncoding must not be null");
        requireNullableOpaqueValidator(etag);
        PersistentInstant.requireNullableMicrosecondPrecision(lastModified, "lastModified");
        requireCanonicalText(parserVersion, "parserVersion");
        if (decodedBodySha256 == null || !SHA_256.matcher(decodedBodySha256).matches()) {
            throw new IllegalArgumentException(
                    "decodedBodySha256 must be lowercase SHA-256 hex");
        }
        if (decodedBodyLength <= 0) {
            throw new IllegalArgumentException("decodedBodyLength must be positive");
        }
        PersistentInstant.requireMicrosecondPrecision(capturedAt, "capturedAt");
        Objects.requireNonNull(bodyRepresentation, "bodyRepresentation must not be null");
        Objects.requireNonNull(bodyRetention, "bodyRetention must not be null");
    }

    @Override
    public String toString() {
        return "SourceResponseReceipt[provider=" + provider
                + ", product=" + product
                + ", sourceUri=" + sourceUri
                + ", httpStatus=" + httpStatus
                + ", mediaType=" + mediaType
                + ", transportContentEncoding=" + transportContentEncoding
                + ", etag=<redacted>"
                + ", lastModified=" + lastModified
                + ", parserVersion=" + parserVersion
                + ", decodedBodySha256=" + decodedBodySha256
                + ", decodedBodyLength=" + decodedBodyLength
                + ", capturedAt=" + capturedAt
                + ", bodyRepresentation=" + bodyRepresentation
                + ", bodyRetention=" + bodyRetention + "]";
    }

    public SourceResponseReceipt withBodyRetention(BodyRetention retention) {
        return new SourceResponseReceipt(
                provider,
                product,
                sourceUri,
                httpStatus,
                mediaType,
                transportContentEncoding,
                etag,
                lastModified,
                parserVersion,
                decodedBodySha256,
                decodedBodyLength,
                capturedAt,
                bodyRepresentation,
                retention);
    }

    private static void requireAbsoluteHttpUri(URI value) {
        Objects.requireNonNull(value, "sourceUri must not be null");
        if (!value.isAbsolute()
                || !("https".equalsIgnoreCase(value.getScheme())
                || "http".equalsIgnoreCase(value.getScheme()))
                || value.getHost() == null
                || value.getHost().isBlank()
                || value.getUserInfo() != null
                || value.getQuery() != null
                || value.getFragment() != null) {
            throw new IllegalArgumentException(
                    "sourceUri must be an absolute HTTP(S) URI without credentials or suffixes");
        }
    }

    private static void requireCanonicalText(String value, String field) {
        if (value == null) {
            throw new NullPointerException(field + " must not be null");
        }
        if (value.isBlank() || !value.equals(value.strip())) {
            throw new IllegalArgumentException(field + " must be nonblank and trimmed");
        }
    }

    private static void requireNullableOpaqueValidator(String value) {
        if (value == null) {
            return;
        }
        if (value.isBlank()
                || !value.equals(value.strip())
                || value.length() > 1024
                || value.indexOf('\r') >= 0
                || value.indexOf('\n') >= 0
                || !ENTITY_TAG.matcher(value).matches()) {
            throw new IllegalArgumentException("etag must be a bounded opaque validator");
        }
    }

    public enum TransportContentEncoding {
        IDENTITY,
        GZIP,
        DEFLATE
    }

    public enum BodyRepresentation {
        DECODED_HTTP_ENTITY_BODY
    }

    public enum BodyRetention {
        RECEIPT_ONLY_BODY_NOT_RETAINED,
        DECODED_BODY_ATTACHED_PENDING_PERSISTENCE,
        DURABLE_DECODED_BODY_RETAINED
    }
}
