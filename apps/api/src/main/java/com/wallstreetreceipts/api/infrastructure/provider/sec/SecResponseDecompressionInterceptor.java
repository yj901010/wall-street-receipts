package com.wallstreetreceipts.api.infrastructure.provider.sec;

import java.io.IOException;
import java.io.InputStream;
import java.util.Locale;
import java.util.zip.GZIPInputStream;
import java.util.zip.InflaterInputStream;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpRequest;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;

/** Decodes only the response encodings explicitly advertised by the SEC client. */
public final class SecResponseDecompressionInterceptor implements ClientHttpRequestInterceptor {

    @Override
    public ClientHttpResponse intercept(
            HttpRequest request,
            byte[] body,
            ClientHttpRequestExecution execution) throws IOException {
        return new DecompressingResponse(execution.execute(request, body));
    }

    private static final class DecompressingResponse implements ClientHttpResponse {

        private final ClientHttpResponse delegate;
        private final HttpHeaders headers;

        private DecompressingResponse(ClientHttpResponse delegate) {
            this.delegate = delegate;
            HttpHeaders decodedHeaders = new HttpHeaders();
            decodedHeaders.addAll(delegate.getHeaders());
            String contentEncoding = decodedHeaders.getFirst(HttpHeaders.CONTENT_ENCODING);
            if (isSupportedCompression(contentEncoding)) {
                decodedHeaders.remove(HttpHeaders.CONTENT_LENGTH);
            }
            this.headers = HttpHeaders.readOnlyHttpHeaders(decodedHeaders);
        }

        private static boolean isSupportedCompression(String contentEncoding) {
            if (contentEncoding == null || contentEncoding.isBlank()) {
                return false;
            }
            return switch (contentEncoding.strip().toLowerCase(Locale.ROOT)) {
                case "gzip", "x-gzip", "deflate" -> true;
                default -> false;
            };
        }

        @Override
        public org.springframework.http.HttpStatusCode getStatusCode() throws IOException {
            return delegate.getStatusCode();
        }

        @Override
        public String getStatusText() throws IOException {
            return delegate.getStatusText();
        }

        @Override
        public HttpHeaders getHeaders() {
            return headers;
        }

        @Override
        public InputStream getBody() throws IOException {
            InputStream responseBody = delegate.getBody();
            String contentEncoding = delegate.getHeaders().getFirst(HttpHeaders.CONTENT_ENCODING);
            if (contentEncoding == null || contentEncoding.isBlank()
                    || "identity".equalsIgnoreCase(contentEncoding)) {
                return responseBody;
            }

            return switch (contentEncoding.strip().toLowerCase(Locale.ROOT)) {
                case "gzip", "x-gzip" -> new GZIPInputStream(responseBody);
                case "deflate" -> new InflaterInputStream(responseBody);
                default -> throw new IOException("Unsupported SEC response content encoding");
            };
        }

        @Override
        public void close() {
            delegate.close();
        }
    }
}
