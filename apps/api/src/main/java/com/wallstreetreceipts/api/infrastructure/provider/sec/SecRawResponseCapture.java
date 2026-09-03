package com.wallstreetreceipts.api.infrastructure.provider.sec;

import java.io.IOException;
import java.net.URI;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.regex.Pattern;

import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

import com.fasterxml.jackson.core.StreamReadFeature;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectReader;
import com.fasterxml.jackson.databind.cfg.CoercionAction;
import com.fasterxml.jackson.databind.cfg.CoercionInputShape;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.databind.type.LogicalType;
import com.wallstreetreceipts.api.domain.filing.FilingCatalog;
import com.wallstreetreceipts.api.domain.filing.FilingCatalogCapture;
import com.wallstreetreceipts.api.domain.source.SourceResponseReceipt;
import com.wallstreetreceipts.api.domain.source.SourceResponseReceipt.BodyRepresentation;
import com.wallstreetreceipts.api.domain.source.SourceResponseReceipt.BodyRetention;
import com.wallstreetreceipts.api.domain.source.SourceResponseReceipt.TransportContentEncoding;

/** Owns one defensive copy of a bounded decoded response until mapping completes. */
final class SecRawResponseCapture {

    private static final HexFormat LOWERCASE_HEX = HexFormat.of();
    private static final Pattern JSON_CONTENT_TYPE = Pattern.compile(
            "^application/json(?:\\s*;\\s*charset\\s*=\\s*(?:UTF-8|\"UTF-8\"))?$",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern ENTITY_TAG = Pattern.compile(
            "^(?:W/)?\"[\\x21\\x23-\\x7e]*\"$");
    private static final ObjectReader SUBMISSIONS_READER = strictSubmissionsReader();

    private final byte[] decodedBody;
    private final SourceResponseReceipt receipt;

    private SecRawResponseCapture(byte[] decodedBody, SourceResponseReceipt receipt) {
        this.decodedBody = decodedBody;
        this.receipt = receipt;
    }

    static SecRawResponseCapture capture(
            URI sourceUri,
            int httpStatus,
            HttpHeaders headers,
            byte[] decodedBody,
            Instant capturedAt) {
        Objects.requireNonNull(headers, "headers");
        Objects.requireNonNull(decodedBody, "decodedBody");
        if (httpStatus != 200) {
            throw SecProviderException.httpStatus(httpStatus);
        }
        if (decodedBody.length == 0) {
            throw SecProviderException.unreadableResponse();
        }
        if (decodedBody.length
                > SecResponseSizeLimitInterceptor.MAX_DECOMPRESSED_RESPONSE_BYTES) {
            throw SecProviderException.responseTooLarge();
        }

        MediaType contentType = requireJsonContentType(headers);
        TransportContentEncoding contentEncoding = contentEncoding(headers);
        String etag = etag(headers);
        Instant lastModified = lastModified(headers);
        byte[] ownedBody = decodedBody.clone();
        requireValidUtf8(ownedBody);
        String decodedBodySha256 = LOWERCASE_HEX.formatHex(sha256(ownedBody));

        SourceResponseReceipt receipt = new SourceResponseReceipt(
                SecSubmissionsMapper.PROVIDER_NAME,
                SecSubmissionsMapper.PRODUCT_NAME,
                sourceUri,
                httpStatus,
                contentType.toString(),
                contentEncoding,
                etag,
                lastModified,
                SecSubmissionsMapper.PARSER_VERSION,
                decodedBodySha256,
                ownedBody.length,
                capturedAt,
                BodyRepresentation.DECODED_HTTP_ENTITY_BODY,
                BodyRetention.RECEIPT_ONLY_BODY_NOT_RETAINED);
        return new SecRawResponseCapture(ownedBody, receipt);
    }

    SourceResponseReceipt receipt() {
        return receipt;
    }

    SecSubmissionsResponse decode() throws IOException {
        return SUBMISSIONS_READER.readValue(decodedBody);
    }

    FilingCatalog toCanonical(Instant processingTime) throws IOException {
        return SecSubmissionsMapper.toCanonical(
                decode(), receipt, processingTime);
    }

    FilingCatalogCapture toCatalogCapture(Instant processingTime) throws IOException {
        SourceResponseReceipt attachedReceipt = receipt.withBodyRetention(
                BodyRetention.DECODED_BODY_ATTACHED_PENDING_PERSISTENCE);
        FilingCatalog catalog = SecSubmissionsMapper.toCanonical(
                decode(), attachedReceipt, processingTime);
        return new FilingCatalogCapture(catalog, decodedBody);
    }

    static FilingCatalog replay(
            byte[] decodedBody,
            SourceResponseReceipt receipt,
            Instant processingTime) throws IOException {
        Objects.requireNonNull(decodedBody, "decodedBody");
        Objects.requireNonNull(receipt, "receipt");
        if (decodedBody.length == 0) {
            throw SecProviderException.unreadableResponse();
        }
        if (decodedBody.length
                > SecResponseSizeLimitInterceptor.MAX_DECOMPRESSED_RESPONSE_BYTES) {
            throw SecProviderException.responseTooLarge();
        }
        requireValidUtf8(decodedBody);
        return SecSubmissionsMapper.toCanonical(
                SUBMISSIONS_READER.readValue(decodedBody), receipt, processingTime);
    }

    private static ObjectReader strictSubmissionsReader() {
        JsonMapper mapper = JsonMapper.builder()
                .enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
                .disable(MapperFeature.ALLOW_COERCION_OF_SCALARS)
                .disable(DeserializationFeature.ACCEPT_FLOAT_AS_INT)
                .build();
        mapper.coercionConfigFor(LogicalType.Textual)
                .setCoercion(CoercionInputShape.Integer, CoercionAction.Fail)
                .setCoercion(CoercionInputShape.Float, CoercionAction.Fail)
                .setCoercion(CoercionInputShape.Boolean, CoercionAction.Fail);
        return mapper.readerFor(SecSubmissionsResponse.class)
                .with(DeserializationFeature.FAIL_ON_TRAILING_TOKENS);
    }

    private static MediaType requireJsonContentType(HttpHeaders headers) {
        try {
            List<String> values = headers.getOrEmpty(HttpHeaders.CONTENT_TYPE);
            if (values.size() != 1) {
                throw SecProviderException.unreadableResponse();
            }
            String rawContentType = values.getFirst();
            if (rawContentType == null
                    || rawContentType.length() > 128
                    || !rawContentType.equals(rawContentType.strip())
                    || !JSON_CONTENT_TYPE.matcher(rawContentType).matches()) {
                throw SecProviderException.unreadableResponse();
            }
            MediaType contentType = MediaType.parseMediaType(rawContentType);
            if (contentType == null
                    || !"application".equalsIgnoreCase(contentType.getType())
                    || !"json".equalsIgnoreCase(contentType.getSubtype())
                    || contentType.getParameters().keySet().stream()
                            .anyMatch(parameter -> !"charset".equalsIgnoreCase(parameter))
                    || (contentType.getCharset() != null
                    && !StandardCharsets.UTF_8.equals(contentType.getCharset()))) {
                throw SecProviderException.unreadableResponse();
            }
            return contentType;
        } catch (IllegalArgumentException exception) {
            throw SecProviderException.unreadableResponse();
        }
    }

    private static TransportContentEncoding contentEncoding(HttpHeaders headers) {
        List<String> values = headers.getOrEmpty(HttpHeaders.CONTENT_ENCODING);
        if (values.isEmpty()) {
            return TransportContentEncoding.IDENTITY;
        }
        if (values.size() != 1) {
            throw SecProviderException.unreadableResponse();
        }
        return switch (values.getFirst().strip().toLowerCase(Locale.ROOT)) {
            case "identity" -> TransportContentEncoding.IDENTITY;
            case "gzip", "x-gzip" -> TransportContentEncoding.GZIP;
            case "deflate" -> TransportContentEncoding.DEFLATE;
            default -> throw SecProviderException.unreadableResponse();
        };
    }

    private static void requireValidUtf8(byte[] content) {
        for (byte value : content) {
            if (value == 0) {
                throw SecProviderException.unreadableResponse();
            }
        }
        try {
            StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(content));
        } catch (CharacterCodingException exception) {
            throw SecProviderException.unreadableResponse();
        }
    }

    private static String etag(HttpHeaders headers) {
        List<String> values = headers.getOrEmpty(HttpHeaders.ETAG);
        if (values.isEmpty()) {
            return null;
        }
        if (values.size() != 1) {
            throw SecProviderException.unreadableResponse();
        }
        String value = values.getFirst();
        if (value == null
                || value.isBlank()
                || !value.equals(value.strip())
                || value.length() > 1024
                || value.indexOf('\r') >= 0
                || value.indexOf('\n') >= 0
                || !ENTITY_TAG.matcher(value).matches()) {
            throw SecProviderException.unreadableResponse();
        }
        return value;
    }

    private static Instant lastModified(HttpHeaders headers) {
        try {
            List<String> values = headers.getOrEmpty(HttpHeaders.LAST_MODIFIED);
            if (values.isEmpty()) {
                return null;
            }
            if (values.size() != 1) {
                throw SecProviderException.unreadableResponse();
            }
            long epochMillis = headers.getLastModified();
            if (epochMillis < 0) {
                throw SecProviderException.unreadableResponse();
            }
            return Instant.ofEpochMilli(epochMillis);
        } catch (IllegalArgumentException exception) {
            throw SecProviderException.unreadableResponse();
        }
    }

    private static byte[] sha256(byte[] content) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(content);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }
}
