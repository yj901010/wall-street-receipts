package com.wallstreetreceipts.api.infrastructure.provider.sec;

import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Objects;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpRequest;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;

/** Bounds the bytes consumed after any advertised response decompression. */
public final class SecResponseSizeLimitInterceptor implements ClientHttpRequestInterceptor {

    public static final long MAX_DECOMPRESSED_RESPONSE_BYTES = 8L * 1024L * 1024L;

    private final long maxResponseBytes;

    public SecResponseSizeLimitInterceptor() {
        this(MAX_DECOMPRESSED_RESPONSE_BYTES);
    }

    SecResponseSizeLimitInterceptor(long maxResponseBytes) {
        if (maxResponseBytes <= 0) {
            throw new IllegalArgumentException("maxResponseBytes must be positive");
        }
        this.maxResponseBytes = maxResponseBytes;
    }

    @Override
    public ClientHttpResponse intercept(
            HttpRequest request,
            byte[] body,
            ClientHttpRequestExecution execution) throws IOException {
        ClientHttpResponse response = execution.execute(request, body);
        if (!response.getStatusCode().is2xxSuccessful()) {
            return response;
        }
        long declaredLength;
        try {
            declaredLength = response.getHeaders().getContentLength();
        } catch (IllegalArgumentException exception) {
            response.close();
            throw SecProviderException.unreadableResponse();
        }
        if (isIdentityEncoded(response.getHeaders()) && declaredLength > maxResponseBytes) {
            response.close();
            throw SecProviderException.responseTooLarge();
        }
        return new SizeLimitedResponse(response, maxResponseBytes);
    }

    private static boolean isIdentityEncoded(HttpHeaders headers) {
        String contentEncoding = headers.getFirst(HttpHeaders.CONTENT_ENCODING);
        return contentEncoding == null
                || contentEncoding.isBlank()
                || "identity".equalsIgnoreCase(contentEncoding.strip());
    }

    static boolean causedByLimitExceeded(Throwable failure) {
        Throwable current = failure;
        while (current != null) {
            if (current instanceof ResponseSizeLimitExceededException) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    private static final class SizeLimitedResponse implements ClientHttpResponse {

        private final ClientHttpResponse delegate;
        private final long maxResponseBytes;
        private InputStream limitedBody;

        private SizeLimitedResponse(ClientHttpResponse delegate, long maxResponseBytes) {
            this.delegate = delegate;
            this.maxResponseBytes = maxResponseBytes;
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
            return delegate.getHeaders();
        }

        @Override
        public synchronized InputStream getBody() throws IOException {
            if (limitedBody == null) {
                limitedBody = new SizeLimitedInputStream(delegate.getBody(), maxResponseBytes);
            }
            return limitedBody;
        }

        @Override
        public synchronized void close() {
            try {
                if (limitedBody != null) {
                    limitedBody.close();
                }
            } catch (IOException ignored) {
                // ClientHttpResponse.close cannot surface an IOException; delegate close still runs.
            } finally {
                delegate.close();
            }
        }
    }

    private static final class SizeLimitedInputStream extends FilterInputStream {

        private final long maxResponseBytes;
        private long consumedBytes;

        private SizeLimitedInputStream(InputStream delegate, long maxResponseBytes) {
            super(delegate);
            this.maxResponseBytes = maxResponseBytes;
        }

        @Override
        public int read() throws IOException {
            if (consumedBytes == maxResponseBytes) {
                return requireEndOfStream(super.read());
            }
            int value = super.read();
            if (value != -1) {
                consumedBytes++;
            }
            return value;
        }

        @Override
        public int read(byte[] target, int offset, int length) throws IOException {
            Objects.checkFromIndexSize(offset, length, target.length);
            if (length == 0) {
                return 0;
            }
            if (consumedBytes == maxResponseBytes) {
                return requireEndOfStream(super.read());
            }

            int boundedLength = (int) Math.min(length, maxResponseBytes - consumedBytes);
            int read = super.read(target, offset, boundedLength);
            if (read > 0) {
                consumedBytes += read;
            }
            return read;
        }

        @Override
        public long skip(long bytesToSkip) throws IOException {
            if (bytesToSkip <= 0) {
                return 0;
            }

            byte[] discard = new byte[(int) Math.min(8192L, bytesToSkip)];
            long skipped = 0;
            while (skipped < bytesToSkip) {
                int read = read(discard, 0, (int) Math.min(discard.length, bytesToSkip - skipped));
                if (read == -1) {
                    break;
                }
                skipped += read;
            }
            return skipped;
        }

        private static int requireEndOfStream(int value) throws IOException {
            if (value == -1) {
                return -1;
            }
            throw new ResponseSizeLimitExceededException();
        }
    }

    private static final class ResponseSizeLimitExceededException extends IOException {

        private ResponseSizeLimitExceededException() {
            super("SEC response size limit exceeded");
        }
    }
}
