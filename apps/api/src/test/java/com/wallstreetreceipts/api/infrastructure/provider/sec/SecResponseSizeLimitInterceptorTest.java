package com.wallstreetreceipts.api.infrastructure.provider.sec;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.zip.DeflaterOutputStream;
import java.util.zip.GZIPOutputStream;
import java.io.ByteArrayOutputStream;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpResponse;

class SecResponseSizeLimitInterceptorTest {

    private static final HttpRequest REQUEST = mock(HttpRequest.class);

    @Test
    void acceptsAResponseAtTheExactDecompressedBoundary() throws IOException {
        byte[] payload = "12345678".getBytes(StandardCharsets.UTF_8);
        ClientHttpResponse response = new SecResponseSizeLimitInterceptor(payload.length)
                .intercept(REQUEST, new byte[0], execution(response(payload, null, payload.length)));

        try (response; InputStream body = response.getBody()) {
            assertThat(body.readAllBytes()).isEqualTo(payload);
        }
    }

    @Test
    void rejectsADeclaredResponseLengthBeforeReadingTheBody() {
        TrackingResponse response = response(
                "oversized".getBytes(StandardCharsets.UTF_8), null, 9);

        assertThatThrownBy(() -> new SecResponseSizeLimitInterceptor(8)
                .intercept(REQUEST, new byte[0], execution(response)))
                .isInstanceOf(SecProviderException.class)
                .hasMessage("SEC submissions response exceeded the size limit")
                .hasNoCause();
        assertThat(response.closed).isTrue();
    }

    @Test
    void rejectsMalformedDeclaredLengthWithoutExposingIt() {
        HttpHeaders headers = new HttpHeaders();
        headers.set(HttpHeaders.CONTENT_LENGTH, "secret-invalid-length");
        TrackingResponse response = new TrackingResponse(
                "body".getBytes(StandardCharsets.UTF_8), headers);

        assertThatThrownBy(() -> new SecResponseSizeLimitInterceptor(8)
                .intercept(REQUEST, new byte[0], execution(response)))
                .isInstanceOf(SecProviderException.class)
                .hasMessage("SEC submissions response could not be read")
                .hasNoCause()
                .message()
                .doesNotContain("secret-invalid-length");
        assertThat(response.closed).isTrue();
    }

    @Test
    void detectsAnUndeclaredStreamingOverrunWithoutRetainingThePayload() throws IOException {
        byte[] payload = "123456789".getBytes(StandardCharsets.UTF_8);
        TrackingResponse raw = response(payload, null, -1);
        ClientHttpResponse response = new SecResponseSizeLimitInterceptor(8)
                .intercept(REQUEST, new byte[0], execution(raw));

        try (response; InputStream body = response.getBody()) {
            assertThatThrownBy(body::readAllBytes)
                    .isInstanceOf(IOException.class)
                    .matches(SecResponseSizeLimitInterceptor::causedByLimitExceeded);
        }
        assertThat(raw.closed).isTrue();
    }

    @Test
    void countsSkippedBytesAgainstTheStreamingLimit() throws IOException {
        byte[] payload = "123456789".getBytes(StandardCharsets.UTF_8);
        ClientHttpResponse response = new SecResponseSizeLimitInterceptor(8)
                .intercept(REQUEST, new byte[0], execution(response(payload, null, -1)));

        try (response; InputStream body = response.getBody()) {
            assertThat(body.skip(8)).isEqualTo(8);
            assertThatThrownBy(body::read)
                    .isInstanceOf(IOException.class)
                    .matches(SecResponseSizeLimitInterceptor::causedByLimitExceeded);
        }
    }

    @Test
    void reusesOneLimitedBodySoRepeatedAccessCannotResetTheCounter() throws IOException {
        byte[] payload = "123456789".getBytes(StandardCharsets.UTF_8);
        ClientHttpResponse response = new SecResponseSizeLimitInterceptor(8)
                .intercept(REQUEST, new byte[0], execution(response(payload, null, -1)));

        try (response) {
            InputStream first = response.getBody();
            assertThat(first.readNBytes(8)).hasSize(8);

            InputStream second = response.getBody();
            assertThat(second).isSameAs(first);
            assertThatThrownBy(second::read)
                    .isInstanceOf(IOException.class)
                    .matches(SecResponseSizeLimitInterceptor::causedByLimitExceeded);
        }
    }

    @Test
    void closesTheLimitedBodyAndResponseDelegate() throws IOException {
        TrackingResponse raw = response(
                "1234".getBytes(StandardCharsets.UTF_8), null, -1);
        ClientHttpResponse response = new SecResponseSizeLimitInterceptor(8)
                .intercept(REQUEST, new byte[0], execution(raw));
        response.getBody();

        response.close();

        assertThat(raw.bodyClosed).isTrue();
        assertThat(raw.closed).isTrue();
    }

    @Test
    void appliesTheLimitAfterGzipDecompression() throws IOException {
        byte[] compressed = gzip("123456789");
        ClientHttpResponse raw = response(compressed, "gzip", compressed.length);
        SecResponseDecompressionInterceptor decompression =
                new SecResponseDecompressionInterceptor();
        SecResponseSizeLimitInterceptor sizeLimit = new SecResponseSizeLimitInterceptor(8);

        ClientHttpResponse response = sizeLimit.intercept(
                REQUEST,
                new byte[0],
                (request, body) -> decompression.intercept(
                        request, body, execution(raw)));

        try (response; InputStream body = response.getBody()) {
            assertThatThrownBy(body::readAllBytes)
                    .isInstanceOf(IOException.class)
                    .matches(SecResponseSizeLimitInterceptor::causedByLimitExceeded);
        }
    }

    @Test
    void appliesTheLimitAfterDeflateDecompression() throws IOException {
        byte[] compressed = deflate("123456789");
        ClientHttpResponse raw = response(compressed, "deflate", compressed.length);
        SecResponseDecompressionInterceptor decompression =
                new SecResponseDecompressionInterceptor();
        SecResponseSizeLimitInterceptor sizeLimit = new SecResponseSizeLimitInterceptor(8);

        ClientHttpResponse response = sizeLimit.intercept(
                REQUEST,
                new byte[0],
                (request, body) -> decompression.intercept(
                        request, body, execution(raw)));

        try (response; InputStream body = response.getBody()) {
            assertThatThrownBy(body::readAllBytes)
                    .isInstanceOf(IOException.class)
                    .matches(SecResponseSizeLimitInterceptor::causedByLimitExceeded);
        }
    }

    private static ClientHttpRequestExecution execution(ClientHttpResponse response) {
        return (request, body) -> response;
    }

    private static TrackingResponse response(
            byte[] body,
            String contentEncoding,
            long contentLength) {
        HttpHeaders headers = new HttpHeaders();
        if (contentEncoding != null) {
            headers.set(HttpHeaders.CONTENT_ENCODING, contentEncoding);
        }
        if (contentLength >= 0) {
            headers.setContentLength(contentLength);
        }
        return new TrackingResponse(body, headers);
    }

    private static byte[] gzip(String value) throws IOException {
        ByteArrayOutputStream compressed = new ByteArrayOutputStream();
        try (GZIPOutputStream gzip = new GZIPOutputStream(compressed)) {
            gzip.write(value.getBytes(StandardCharsets.UTF_8));
        }
        return compressed.toByteArray();
    }

    private static byte[] deflate(String value) throws IOException {
        ByteArrayOutputStream compressed = new ByteArrayOutputStream();
        try (DeflaterOutputStream deflate = new DeflaterOutputStream(compressed)) {
            deflate.write(value.getBytes(StandardCharsets.UTF_8));
        }
        return compressed.toByteArray();
    }

    private static final class TrackingResponse implements ClientHttpResponse {

        private final TrackingInputStream body;
        private final HttpHeaders headers;
        private boolean closed;
        private boolean bodyClosed;

        private TrackingResponse(byte[] body, HttpHeaders headers) {
            this.body = new TrackingInputStream(body, this);
            this.headers = headers;
        }

        @Override
        public HttpStatus getStatusCode() {
            return HttpStatus.OK;
        }

        @Override
        public String getStatusText() {
            return HttpStatus.OK.getReasonPhrase();
        }

        @Override
        public HttpHeaders getHeaders() {
            return headers;
        }

        @Override
        public InputStream getBody() {
            return body;
        }

        @Override
        public void close() {
            closed = true;
        }
    }

    private static final class TrackingInputStream extends ByteArrayInputStream {

        private final TrackingResponse owner;

        private TrackingInputStream(byte[] body, TrackingResponse owner) {
            super(body);
            this.owner = owner;
        }

        @Override
        public void close() throws IOException {
            owner.bodyClosed = true;
            super.close();
        }
    }
}
