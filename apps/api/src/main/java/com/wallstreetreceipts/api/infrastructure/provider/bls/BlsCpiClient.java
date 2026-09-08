package com.wallstreetreceipts.api.infrastructure.provider.bls;

import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.ByteBuffer;
import java.time.Duration;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Flow;
import java.util.concurrent.TimeUnit;

/** One bounded request to a fixed origin, no redirects or retries. */
public final class BlsCpiClient {
    public static final URI ENDPOINT = URI.create("https://api.bls.gov/publicAPI/v2/timeseries/data/");
    private final java.util.function.Supplier<HttpClient> clients;
    public BlsCpiClient() {
        this(() -> HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5))
                .followRedirects(HttpClient.Redirect.NEVER).build());
    }
    BlsCpiClient(java.util.function.Supplier<HttpClient> clients) { this.clients = clients; }
    public byte[] fetch(String key, int year, Instant now) {
        if (key == null || !key.matches("[A-Za-z0-9]{16,128}")) {
            throw new IllegalArgumentException("BLS_REGISTRATION_KEY must be configured");
        }
        String body = "{\"seriesid\":[\"CUUR0000SA0\",\"CUUR0000SA0L1E\"],\"startyear\":\""
                + (year - 3) + "\",\"endyear\":\"" + year + "\",\"registrationkey\":\"" + key
                + "\",\"catalog\":false,\"calculations\":false,\"annualaverage\":false,\"aspects\":false}";
        try (var client = clients.get()) {
            var request = HttpRequest.newBuilder(ENDPOINT).timeout(Duration.ofSeconds(20))
                    .header("Content-Type", "application/json").header("Accept", "application/json")
                    .header("Accept-Encoding", "identity").POST(HttpRequest.BodyPublishers.ofString(body)).build();
            var future = client.sendAsync(request, info -> {
                // Honor a rate-limit header even if its body is oversized or never finishes.
                if (info.statusCode() == 429) throw new RateLimited(retryAt(info.headers().firstValue("Retry-After").orElse(""), now));
                if (info.statusCode() != 200) throw new IllegalStateException("BLS CPI HTTP response rejected");
                return new LimitedBody();
            });
            try {
                var response = future.get(20, TimeUnit.SECONDS);
                if (response.statusCode() == 429) {
                    throw new RateLimited(retryAt(response.headers().firstValue("Retry-After").orElse(""), now));
                }
                if (response.statusCode() != 200
                        || !response.headers().firstValue("Content-Type").orElse("").toLowerCase(java.util.Locale.ROOT)
                                .matches("application/json(?:\\s*;.*)?")
                        || !response.headers().firstValue("Content-Encoding").orElse("identity").equalsIgnoreCase("identity")) {
                    throw new IllegalStateException("BLS CPI HTTP response rejected");
                }
                return response.body();
            } catch (RateLimited exception) { throw exception; }
            catch (Exception exception) {
                future.cancel(true);
                if (exception instanceof java.util.concurrent.ExecutionException && exception.getCause() instanceof RateLimited limited) throw limited;
                if (exception instanceof InterruptedException) Thread.currentThread().interrupt();
                throw new IllegalStateException("BLS CPI request failed; no snapshot saved");
            }
        }
    }
    public static Instant retryAt(String header, Instant now) {
        Instant minimum = now.plus(Duration.ofDays(1));
        try {
            Instant requested = header.matches("[0-9]{1,10}") ? now.plusSeconds(Long.parseLong(header))
                    : ZonedDateTime.parse(header, DateTimeFormatter.RFC_1123_DATE_TIME).toInstant();
            return requested.isAfter(minimum) ? requested : minimum;
        } catch (Exception exception) { return minimum; }
    }
    public static final class RateLimited extends IllegalStateException {
        private final Instant retryAt;
        public RateLimited(Instant at) { super("BLS CPI rate limit reached; collection paused"); retryAt = at; }
        public Instant retryAt() { return retryAt; }
    }
    static final class LimitedBody implements HttpResponse.BodySubscriber<byte[]> {
        private final CompletableFuture<byte[]> result = new CompletableFuture<>();
        private final ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        private Flow.Subscription subscription;
        @Override public CompletionStage<byte[]> getBody() { return result; }
        @Override public void onSubscribe(Flow.Subscription value) { subscription = value; value.request(1); }
        @Override public void onNext(List<ByteBuffer> buffers) {
            for (ByteBuffer buffer : buffers) {
                if (buffer.remaining() > BlsCpiParser.MAX_BYTES - bytes.size()) {
                    subscription.cancel(); result.completeExceptionally(new IllegalStateException("BLS response too large")); return;
                }
                byte[] chunk = new byte[buffer.remaining()]; buffer.get(chunk); bytes.writeBytes(chunk);
            }
            subscription.request(1);
        }
        @Override public void onError(Throwable error) { result.completeExceptionally(new IllegalStateException("BLS body unavailable")); }
        @Override public void onComplete() { result.complete(bytes.toByteArray()); }
    }
}
