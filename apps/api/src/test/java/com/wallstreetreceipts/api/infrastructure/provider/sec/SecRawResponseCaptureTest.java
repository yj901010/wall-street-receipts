package com.wallstreetreceipts.api.infrastructure.provider.sec;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.net.URI;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Arrays;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

import com.wallstreetreceipts.api.domain.source.SourceResponseReceipt.TransportContentEncoding;

class SecRawResponseCaptureTest {

    private static final URI SOURCE_URI = URI.create(
            "https://data.sec.gov/submissions/CIK0000320193.json");
    private static final Instant CAPTURED_AT =
            Instant.parse("2026-08-25T01:02:03.123456Z");

    @Test
    void hashesOwnedDecodedBytesAndCannotBeChangedThroughTheInputArray() throws Exception {
        byte[] body = "{\"cik\":\"0000320193\"}".getBytes(StandardCharsets.UTF_8);
        HttpHeaders headers = jsonHeaders();
        SecRawResponseCapture capture =
                SecRawResponseCapture.capture(SOURCE_URI, 200, headers, body, CAPTURED_AT);

        body[2] = 'X';

        assertThat(capture.receipt().decodedBodySha256())
                .isEqualTo("7823a5005ef7608b35a5af6fedf94bb9974b7e7b4d44fdc137d4be1b1052ee9d");
        assertThat(capture.receipt().decodedBodyLength()).isEqualTo(20);
        assertThat(capture.decode().cik())
                .isEqualTo("0000320193");
    }

    @Test
    void preservesOnlyWhitelistedTransportValidators() {
        HttpHeaders headers = jsonHeaders();
        headers.set(HttpHeaders.CONTENT_ENCODING, "gzip");
        headers.setETag("\"revision-1\"");
        headers.setLastModified(Instant.parse("2026-08-20T20:00:00Z"));
        headers.set("X-Secret", "do-not-retain");

        SecRawResponseCapture capture = SecRawResponseCapture.capture(
                SOURCE_URI,
                200,
                headers,
                "{}".getBytes(StandardCharsets.UTF_8),
                CAPTURED_AT);

        assertThat(capture.receipt().transportContentEncoding())
                .isEqualTo(TransportContentEncoding.GZIP);
        assertThat(capture.receipt().etag()).isEqualTo("\"revision-1\"");
        assertThat(capture.receipt().lastModified())
                .isEqualTo(Instant.parse("2026-08-20T20:00:00Z"));
        assertThat(capture.receipt().toString())
                .contains("etag=<redacted>")
                .doesNotContain("revision-1", "X-Secret", "do-not-retain");
    }

    @Test
    void rejectsEmptyBodyMissingOrNonJsonMediaTypeWithSanitizedFailure() {
        HttpHeaders missingType = new HttpHeaders();
        HttpHeaders textType = new HttpHeaders();
        textType.setContentType(MediaType.TEXT_PLAIN);

        assertThatThrownBy(() -> SecRawResponseCapture.capture(
                SOURCE_URI, 200, jsonHeaders(), new byte[0], CAPTURED_AT))
                .isInstanceOf(SecProviderException.class)
                .hasMessage("SEC submissions response could not be read")
                .hasNoCause();
        for (HttpHeaders headers : new HttpHeaders[] {missingType, textType}) {
            assertThatThrownBy(() -> SecRawResponseCapture.capture(
                    SOURCE_URI,
                    200,
                    headers,
                    "secret-body".getBytes(StandardCharsets.UTF_8),
                    CAPTURED_AT))
                    .isInstanceOf(SecProviderException.class)
                    .hasMessage("SEC submissions response could not be read")
                    .hasNoCause()
                    .message()
                    .doesNotContain("secret-body");
        }
    }

    @Test
    void rejectsAmbiguousOrMalformedReceiptHeaders() {
        HttpHeaders duplicateType = new HttpHeaders();
        duplicateType.add(HttpHeaders.CONTENT_TYPE, "application/json");
        duplicateType.add(HttpHeaders.CONTENT_TYPE, "application/json; charset=UTF-8");
        HttpHeaders invalidLastModified = jsonHeaders();
        invalidLastModified.set(HttpHeaders.LAST_MODIFIED, "secret-invalid-date");
        HttpHeaders blankContentEncoding = jsonHeaders();
        blankContentEncoding.set(HttpHeaders.CONTENT_ENCODING, " ");
        HttpHeaders invalidEtag = jsonHeaders();
        invalidEtag.set(HttpHeaders.ETAG, "secret-unquoted-etag");

        for (HttpHeaders headers : new HttpHeaders[] {
                duplicateType, invalidLastModified, blankContentEncoding, invalidEtag}) {
            assertThatThrownBy(() -> SecRawResponseCapture.capture(
                    SOURCE_URI,
                    200,
                    headers,
                    "{}".getBytes(StandardCharsets.UTF_8),
                    CAPTURED_AT))
                    .isInstanceOf(SecProviderException.class)
                    .hasMessage("SEC submissions response could not be read")
                    .hasNoCause()
                    .message()
                    .doesNotContain("secret-invalid-date");
        }
    }

    @Test
    void rejectsNonUtf8BytesBeforeCreatingTheReceipt() {
        byte[][] invalidBodies = {
                {(byte) 0xc3, (byte) 0x28},
                {(byte) 0xff, (byte) 0xfe, '{', 0, '}', 0},
                {(byte) 0xfe, (byte) 0xff, 0, '{', 0, '}'},
                {0, 0, (byte) 0xfe, (byte) 0xff, 0, 0, 0, '{'},
                "{}".getBytes(StandardCharsets.UTF_16LE),
                "{}".getBytes(StandardCharsets.UTF_16BE),
                "{}".getBytes(Charset.forName("UTF-32LE")),
                "{}".getBytes(Charset.forName("UTF-32BE"))
        };

        for (byte[] body : invalidBodies) {
            assertThatThrownBy(() -> SecRawResponseCapture.capture(
                    SOURCE_URI, 200, jsonHeaders(), body, CAPTURED_AT))
                    .isInstanceOf(SecProviderException.class)
                    .hasMessage("SEC submissions response could not be read")
                    .hasNoCause();
        }
    }

    @Test
    void rejectsNonUtf8DeclarationAndTrailingJsonWithoutChangingTheReceiptDigest() {
        HttpHeaders nonUtf8 = new HttpHeaders();
        nonUtf8.setContentType(new MediaType("application", "json", StandardCharsets.UTF_16));
        assertThatThrownBy(() -> SecRawResponseCapture.capture(
                SOURCE_URI,
                200,
                nonUtf8,
                "{}".getBytes(StandardCharsets.UTF_8),
                CAPTURED_AT))
                .isInstanceOf(SecProviderException.class)
                .hasMessage("SEC submissions response could not be read")
                .hasNoCause();

        HttpHeaders unexpectedParameter = new HttpHeaders();
        unexpectedParameter.set(
                HttpHeaders.CONTENT_TYPE,
                "application/json; secret=do-not-retain");
        assertThatThrownBy(() -> SecRawResponseCapture.capture(
                SOURCE_URI,
                200,
                unexpectedParameter,
                "{}".getBytes(StandardCharsets.UTF_8),
                CAPTURED_AT))
                .isInstanceOf(SecProviderException.class)
                .hasMessage("SEC submissions response could not be read")
                .hasNoCause()
                .message()
                .doesNotContain("do-not-retain");

        for (String ambiguous : new String[] {
                "application/json; charset=UTF-16; charset=UTF-8",
                "application/json; charset=UTF-8; charset=UTF-16"
        }) {
            HttpHeaders duplicateCharset = new HttpHeaders();
            duplicateCharset.set(HttpHeaders.CONTENT_TYPE, ambiguous);
            assertThatThrownBy(() -> SecRawResponseCapture.capture(
                    SOURCE_URI,
                    200,
                    duplicateCharset,
                    "{}".getBytes(StandardCharsets.UTF_8),
                    CAPTURED_AT))
                    .isInstanceOf(SecProviderException.class)
                    .hasMessage("SEC submissions response could not be read")
                    .hasNoCause()
                    .message()
                    .doesNotContain(ambiguous);
        }

        byte[] trailing = "{\"cik\":\"0000320193\"} {}".getBytes(StandardCharsets.UTF_8);
        SecRawResponseCapture capture = SecRawResponseCapture.capture(
                SOURCE_URI, 200, jsonHeaders(), trailing, CAPTURED_AT);

        assertThat(capture.receipt().decodedBodyLength()).isEqualTo(trailing.length);
        assertThatThrownBy(capture::decode)
                .isInstanceOf(java.io.IOException.class);
    }

    @Test
    void hashesWhitespaceAndUtf8BomAsExactBytesWithoutNormalization() {
        byte[] compact = "{}".getBytes(StandardCharsets.UTF_8);
        byte[] spaced = "{ }".getBytes(StandardCharsets.UTF_8);
        byte[] bom = {(byte) 0xef, (byte) 0xbb, (byte) 0xbf, '{', '}'};

        SecRawResponseCapture compactCapture = SecRawResponseCapture.capture(
                SOURCE_URI, 200, jsonHeaders(), compact, CAPTURED_AT);
        SecRawResponseCapture spacedCapture = SecRawResponseCapture.capture(
                SOURCE_URI, 200, jsonHeaders(), spaced, CAPTURED_AT);
        SecRawResponseCapture bomCapture = SecRawResponseCapture.capture(
                SOURCE_URI, 200, jsonHeaders(), bom, CAPTURED_AT);

        assertThat(compactCapture.receipt().decodedBodySha256())
                .isNotEqualTo(spacedCapture.receipt().decodedBodySha256())
                .isNotEqualTo(bomCapture.receipt().decodedBodySha256());
        assertThat(bomCapture.receipt().decodedBodyLength()).isEqualTo(5);
    }

    @Test
    void acceptsTheExactDecodedLimitAndRejectsOneByteMoreBeforeHashing() {
        byte[] exact = new byte[(int) SecResponseSizeLimitInterceptor
                .MAX_DECOMPRESSED_RESPONSE_BYTES];
        Arrays.fill(exact, (byte) ' ');
        exact[0] = '{';
        exact[exact.length - 1] = '}';

        SecRawResponseCapture capture = SecRawResponseCapture.capture(
                SOURCE_URI, 200, jsonHeaders(), exact, CAPTURED_AT);

        assertThat(capture.receipt().decodedBodyLength()).isEqualTo(exact.length);

        byte[] oversized = new byte[exact.length + 1];
        assertThatThrownBy(() -> SecRawResponseCapture.capture(
                SOURCE_URI, 200, jsonHeaders(), oversized, CAPTURED_AT))
                .isInstanceOf(SecProviderException.class)
                .hasMessage("SEC submissions response exceeded the size limit")
                .hasNoCause();
    }

    @Test
    void rejectsNon200BeforeCreatingAReceipt() {
        assertThatThrownBy(() -> SecRawResponseCapture.capture(
                SOURCE_URI,
                206,
                jsonHeaders(),
                "secret-body".getBytes(StandardCharsets.UTF_8),
                CAPTURED_AT))
                .isInstanceOf(SecProviderException.class)
                .hasMessage("SEC submissions request failed with HTTP 206")
                .hasNoCause()
                .message()
                .doesNotContain("secret-body");
    }

    private static HttpHeaders jsonHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        return headers;
    }
}
