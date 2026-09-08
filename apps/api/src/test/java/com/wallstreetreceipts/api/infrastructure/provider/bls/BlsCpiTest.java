package com.wallstreetreceipts.api.infrastructure.provider.bls;

import static org.assertj.core.api.Assertions.*;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Flow;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import com.wallstreetreceipts.api.domain.cpi.CpiSnapshot;
import com.wallstreetreceipts.api.support.CpiTestFixture;

class BlsCpiTest {
    private final BlsCpiParser parser = new BlsCpiParser();
    private static final Instant AT = Instant.parse("2026-09-08T01:00:00Z");
    private CpiSnapshot parse(String raw) { return parser.parse(raw.getBytes(StandardCharsets.UTF_8), UUID.randomUUID(), AT, 2023, 2026); }
    @Test void preservesMissingValuesFootnotesAndRoundsOnce() {
        var snapshot = parse(CpiTestFixture.json());
        var series = snapshot.series().getFirst();
        assertThat(snapshot.responseSha256()).isEqualTo(BlsCpiParser.sha256(CpiTestFixture.bytes()));
        assertThat(CpiSnapshot.yearOverYear(series, series.observations().getFirst())).isEqualTo("3.1");
        assertThat(CpiSnapshot.yearOverYear(series, series.observations().get(1))).isNull();
        assertThat(CpiSnapshot.yearOverYear(series, series.observations().get(2))).isNull();
        assertThat(series.observations().get(1).index()).isNull();
        assertThat(series.observations().get(1).footnotes()).containsExactly("X: Synthetic missing value");
        assertThatThrownBy(() -> series.observations().clear()).isInstanceOf(UnsupportedOperationException.class);
    }
    @ParameterizedTest @ValueSource(strings = {"0", "-1", "NaN", "1e3", "", "100.1234", " 100", "1000000"})
    void rejectsInvalidNumbers(String value) {
        assertThatThrownBy(() -> parse(CpiTestFixture.json().replace("103.050", value)))
                .hasMessage("BLS CPI response failed validation").hasNoCause();
    }
    @Test void rejectsAmbiguousAndPartialResponses() {
        var raw = CpiTestFixture.json();
        for (String mutated : List.of(raw + "{}", raw.replace("\"message\":[]", "\"message\":[\"secret\"]"),
                raw.replace("REQUEST_SUCCEEDED", "REQUEST_FAILED"), raw.replace("M07", "M13"),
                raw.replace("M07", "M09"), raw.replace("M06", "M07"), raw.replace("CUUR0000SA0L1E", "OTHER"),
                raw.replace("CUUR0000SA0L1E", "CUUR0000SA0"), raw.replace("\"value\":\"103.050\"", "\"value\":\"103.050\",\"value\":\"1\""),
                raw.replace("\"responseTime\":1", "\"secret\":\"not printed\""))) {
            assertThatThrownBy(() -> parse(mutated)).hasMessage("BLS CPI response failed validation").hasNoCause();
        }
    }
    @Test void rejectsEncodingSizeAndWrongRequestWindow() {
        for (byte[] bytes : List.of(new byte[]{(byte)0xff}, new byte[0], new byte[BlsCpiParser.MAX_BYTES + 1])) {
            assertThatThrownBy(() -> parser.parse(bytes, UUID.randomUUID(), AT, 2023, 2026)).hasMessage("BLS CPI response failed validation");
        }
        assertThatThrownBy(() -> parser.parse(CpiTestFixture.bytes(), UUID.randomUUID(), AT, 2024, 2026)).isInstanceOf(IllegalArgumentException.class);
    }
    @Test void respectsRetryAfterWithConservativeDailyMinimum() {
        for (String value : List.of("", "junk", "60", "-1")) assertThat(BlsCpiClient.retryAt(value, AT)).isEqualTo(AT.plusSeconds(86400));
        assertThat(BlsCpiClient.retryAt("172800", AT)).isEqualTo(AT.plusSeconds(172800));
        assertThat(BlsCpiClient.retryAt("Thu, 10 Sep 2026 01:00:00 GMT", AT)).isEqualTo(AT.plusSeconds(172800));
    }
    @Test void streamingBodyEnforcesBoundaryAndCancels() {
        var body = new BlsCpiClient.LimitedBody(); var subscription = new Subscription(); body.onSubscribe(subscription);
        body.onNext(List.of(ByteBuffer.wrap(new byte[BlsCpiParser.MAX_BYTES])));
        body.onComplete();
        assertThat(body.getBody().toCompletableFuture().join()).hasSize(BlsCpiParser.MAX_BYTES);
        var large = new BlsCpiClient.LimitedBody(); var second = new Subscription(); large.onSubscribe(second);
        large.onNext(List.of(ByteBuffer.wrap(new byte[BlsCpiParser.MAX_BYTES]), ByteBuffer.wrap(new byte[1])));
        assertThat(second.cancelled).isTrue();
        assertThat(large.getBody().toCompletableFuture()).isCompletedExceptionally();
    }
    @Test void invalidKeyFailsBeforeNetworkAndIsNotEchoed() {
        assertThatThrownBy(() -> new BlsCpiClient().fetch("secret?invalid", 2026, AT)).hasMessage("BLS_REGISTRATION_KEY must be configured");
    }
    @Test void rateLimitHeadersFailBeforeBodyAndPreserveBackoff() {
        var client = org.mockito.Mockito.mock(java.net.http.HttpClient.class);
        org.mockito.Mockito.when(client.sendAsync(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.<java.net.http.HttpResponse.BodyHandler<byte[]>>any()))
                .thenAnswer(invocation -> {
                    java.net.http.HttpRequest request = invocation.getArgument(0);
                    assertThat(request.uri()).isEqualTo(BlsCpiClient.ENDPOINT);
                    assertThat(request.method()).isEqualTo("POST");
                    assertThat(request.timeout()).contains(java.time.Duration.ofSeconds(20));
                    java.net.http.HttpResponse.BodyHandler<byte[]> handler = invocation.getArgument(1);
                    var info = org.mockito.Mockito.mock(java.net.http.HttpResponse.ResponseInfo.class);
                    org.mockito.Mockito.when(info.statusCode()).thenReturn(429);
                    org.mockito.Mockito.when(info.headers()).thenReturn(java.net.http.HttpHeaders.of(java.util.Map.of("Retry-After", List.of("172800")), (a,b) -> true));
                    try { handler.apply(info); throw new AssertionError("429 body must not be read"); }
                    catch (BlsCpiClient.RateLimited error) { return java.util.concurrent.CompletableFuture.failedFuture(error); }
                });
        assertThatThrownBy(() -> new BlsCpiClient(() -> client).fetch("syntheticTestKeyOnly", 2026, AT))
                .isInstanceOfSatisfying(BlsCpiClient.RateLimited.class, error -> assertThat(error.retryAt()).isEqualTo(AT.plusSeconds(172800)));
        org.mockito.Mockito.verify(client).close();
    }
    private static class Subscription implements Flow.Subscription {
        boolean cancelled;
        public void request(long n) {}
        public void cancel() { cancelled = true; }
    }
}
