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
import com.wallstreetreceipts.api.domain.filing.FilingCatalogCapture;
import com.wallstreetreceipts.api.domain.filing.HistoricalFilingSegment;
import com.wallstreetreceipts.api.domain.filing.HistoricalFilingSegmentCapture;
import com.wallstreetreceipts.api.domain.source.SourceResponseReceipt;
import com.wallstreetreceipts.api.domain.source.SourceResponseReceipt.BodyRepresentation;
import com.wallstreetreceipts.api.domain.source.SourceResponseReceipt.BodyRetention;
import com.wallstreetreceipts.api.domain.source.SourceResponseReceipt.TransportContentEncoding;

/** Owns one bounded decoded historical-segment response until persistence. */
final class SecHistoricalRawResponseCapture {

    private static final HexFormat LOWERCASE_HEX = HexFormat.of();
    private static final Pattern JSON_CONTENT_TYPE = Pattern.compile(
            "^application/json(?:\\s*;\\s*charset\\s*=\\s*(?:UTF-8|\"UTF-8\"))?$",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern ENTITY_TAG = Pattern.compile(
            "^(?:W/)?\"[\\x21\\x23-\\x7e]*\"$");
    private static final ObjectReader READER = strictReader();

    private final byte[] decodedBody;
    private final SourceResponseReceipt receipt;

    private SecHistoricalRawResponseCapture(
            byte[] decodedBody,
            SourceResponseReceipt receipt) {
        this.decodedBody = decodedBody;
        this.receipt = receipt;
    }

    static SecHistoricalRawResponseCapture capture(
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
        requireBoundedBody(decodedBody);

        MediaType contentType = requireJsonContentType(headers);
        byte[] ownedBody = decodedBody.clone();
        requireValidUtf8(ownedBody);
        SourceResponseReceipt receipt = new SourceResponseReceipt(
                SecHistoricalSubmissionsMapper.PROVIDER_NAME,
                SecHistoricalSubmissionsMapper.PRODUCT_NAME,
                sourceUri,
                httpStatus,
                contentType.toString(),
                contentEncoding(headers),
                etag(headers),
                lastModified(headers),
                SecHistoricalSubmissionsMapper.PARSER_VERSION,
                LOWERCASE_HEX.formatHex(sha256(ownedBody)),
                ownedBody.length,
                capturedAt,
                BodyRepresentation.DECODED_HTTP_ENTITY_BODY,
                BodyRetention.RECEIPT_ONLY_BODY_NOT_RETAINED);
        return new SecHistoricalRawResponseCapture(ownedBody, receipt);
    }

    SourceResponseReceipt receipt() {
        return receipt;
    }

    HistoricalFilingSegmentCapture toCapture(
            FilingCatalogCapture rootCapture,
            int descriptorOrdinal,
            Instant processingTime) throws IOException {
        SourceResponseReceipt attachedReceipt = receipt.withBodyRetention(
                BodyRetention.DECODED_BODY_ATTACHED_PENDING_PERSISTENCE);
        HistoricalFilingSegment segment = SecHistoricalSubmissionsMapper.toCanonical(
                decode(), rootCapture, descriptorOrdinal, attachedReceipt, processingTime);
        return new HistoricalFilingSegmentCapture(segment, decodedBody);
    }

    static HistoricalFilingSegment replay(
            byte[] decodedBody,
            FilingCatalogCapture rootCapture,
            int descriptorOrdinal,
            SourceResponseReceipt receipt,
            Instant processingTime) throws IOException {
        Objects.requireNonNull(decodedBody, "decodedBody");
        Objects.requireNonNull(receipt, "receipt");
        requireBoundedBody(decodedBody);
        requireValidUtf8(decodedBody);
        return SecHistoricalSubmissionsMapper.toCanonical(
                READER.readValue(decodedBody),
                rootCapture,
                descriptorOrdinal,
                receipt,
                processingTime);
    }

    private SecHistoricalSubmissionsResponse decode() throws IOException {
        return READER.readValue(decodedBody);
    }

    private static ObjectReader strictReader() {
        JsonMapper mapper = JsonMapper.builder()
                .enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
                .disable(MapperFeature.ALLOW_COERCION_OF_SCALARS)
                .disable(DeserializationFeature.ACCEPT_FLOAT_AS_INT)
                .build();
        mapper.coercionConfigFor(LogicalType.Textual)
                .setCoercion(CoercionInputShape.Integer, CoercionAction.Fail)
                .setCoercion(CoercionInputShape.Float, CoercionAction.Fail)
                .setCoercion(CoercionInputShape.Boolean, CoercionAction.Fail);
        return mapper.readerFor(SecHistoricalSubmissionsResponse.class)
                .with(DeserializationFeature.FAIL_ON_TRAILING_TOKENS);
    }

    private static void requireBoundedBody(byte[] body) {
        if (body.length == 0) {
            throw SecProviderException.unreadableResponse();
        }
        if (body.length > SecResponseSizeLimitInterceptor.MAX_DECOMPRESSED_RESPONSE_BYTES) {
            throw SecProviderException.responseTooLarge();
        }
    }

    private static MediaType requireJsonContentType(HttpHeaders headers) {
        try {
            List<String> values = headers.getOrEmpty(HttpHeaders.CONTENT_TYPE);
            if (values.size() != 1) {
                throw SecProviderException.unreadableResponse();
            }
            String raw = values.getFirst();
            if (raw == null
                    || raw.length() > 128
                    || !raw.equals(raw.strip())
                    || !JSON_CONTENT_TYPE.matcher(raw).matches()) {
                throw SecProviderException.unreadableResponse();
            }
            MediaType mediaType = MediaType.parseMediaType(raw);
            if (!"application".equalsIgnoreCase(mediaType.getType())
                    || !"json".equalsIgnoreCase(mediaType.getSubtype())
                    || mediaType.getParameters().keySet().stream()
                            .anyMatch(name -> !"charset".equalsIgnoreCase(name))
                    || (mediaType.getCharset() != null
                    && !StandardCharsets.UTF_8.equals(mediaType.getCharset()))) {
                throw SecProviderException.unreadableResponse();
            }
            return mediaType;
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

    private static byte[] sha256(byte[] content) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(content);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }
}
