package com.wallstreetreceipts.api.web.operator;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import com.wallstreetreceipts.api.application.cpi.CpiAttemptReader;
import com.wallstreetreceipts.api.application.cpi.CpiDeadlineReader;
import com.wallstreetreceipts.api.application.cpi.CpiRepository;
import com.wallstreetreceipts.api.domain.cpi.CpiCollectionAttempt;

/** Real HTTP/security/configuration with a deliberately uninterruptible test reader, not PostgreSQL evidence. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
    "app.operator-api.enabled=true", "app.operator-api.access=CPI_READ_ONLY",
    "app.operator-api.token-sha256=905f28def18eaac05ae6f12b2c3452744afaf626da1343d57b395b544e0519b6"})
@ActiveProfiles("test")
class OperatorCpiAttemptDeadlineTest {
    private static final String TOKEN = "AAECAwQFBgcICQoLDA0ODxAREhMUFRYXGBkaGxwdHh8=";
    private static final String PATH = "/internal/v1/cpi/collection-attempts";
    private static final String ID = "00000000-0000-0000-0000-000000000001";
    private static final List<Read> READS = List.of(new Read("GET", PATH), new Read("HEAD", PATH),
            new Read("GET", PATH + "/" + ID), new Read("HEAD", PATH + "/" + ID));
    @LocalServerPort int port;
    @MockitoBean CpiRepository repository;
    @Autowired CpiAttemptReader reader;

    @Test @Timeout(25)
    void allReadShapesTimeOutWhileStubbornWorkStaysCappedAndAuthRemainsAvailable() throws Exception {
        assertThat(reader).isInstanceOf(CpiDeadlineReader.class);
        var entered = new CountDownLatch(4);
        var release = new CountDownLatch(1);
        var calls = new AtomicInteger();
        var active = new AtomicInteger();
        var row = new CpiCollectionAttempt(UUID.fromString(ID), CpiCollectionAttempt.Trigger.MANUAL,
                java.time.Instant.parse("2020-01-01T00:00:00Z"), true, null);
        Runnable stalled = () -> {
            calls.incrementAndGet(); active.incrementAndGet(); entered.countDown();
            try {
                while (true) {
                    try { if (release.await(12, TimeUnit.SECONDS)) return; else throw new AssertionError("owned gate expired"); }
                    catch (InterruptedException ignored) { /* Deliberately model a driver ignoring interruption. */ }
                }
            } finally { active.decrementAndGet(); }
        };
        when(repository.recentAttempts()).thenAnswer(invocation -> { stalled.run(); return List.of(row); });
        when(repository.findAttempt(any())).thenAnswer(invocation -> { stalled.run(); return Optional.of(row); });
        try (var client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(1)).build()) {
            long started = System.nanoTime();
            var pending = READS.stream().map(read -> client.sendAsync(request(read, true), HttpResponse.BodyHandlers.ofString())).toList();
            try {
                assertThat(entered.await(2, TimeUnit.SECONDS)).isTrue();
                for (int i = 0; i < READS.size(); i++) assertResponse(pending.get(i).get(6, TimeUnit.SECONDS), READS.get(i), 503);
                assertThat(Duration.ofNanos(System.nanoTime() - started)).isBetween(Duration.ofMillis(3500), Duration.ofSeconds(6));
                assertThat(active.get()).isEqualTo(4);
                for (var read : READS) {
                    var rejected = client.sendAsync(request(read, true), HttpResponse.BodyHandlers.ofString()).get(1, TimeUnit.SECONDS);
                    assertResponse(rejected, read, 503);
                }
                assertThat(calls.get()).isEqualTo(4);
                assertResponse(client.send(request(new Read("GET", PATH + "/invalid"), true), HttpResponse.BodyHandlers.ofString()), new Read("GET", PATH), 400);
                assertResponse(client.send(request(new Read("GET", PATH), false), HttpResponse.BodyHandlers.ofString()), new Read("GET", PATH), 401);
            } finally { release.countDown(); }
            long recovery = System.nanoTime() + TimeUnit.SECONDS.toNanos(2);
            while (active.get() != 0 && System.nanoTime() < recovery) Thread.sleep(5);
            assertThat(active.get()).isZero();
            for (var read : READS) assertResponse(client.send(request(read, true), HttpResponse.BodyHandlers.ofString()), read, 200);
            assertThat(calls.get()).isEqualTo(8); // No deferred execution of overflow requests.
        } finally { release.countDown(); }
    }

    private HttpRequest request(Read read, boolean auth) {
        var builder = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + read.path()))
                .timeout(Duration.ofSeconds(7)).method(read.method(), HttpRequest.BodyPublishers.noBody());
        if (auth) builder.header("Authorization", "Bearer " + TOKEN);
        return builder.build();
    }
    private void assertResponse(HttpResponse<String> response, Read read, int status) throws Exception {
        assertThat(response.statusCode()).isEqualTo(status);
        assertThat(response.headers().firstValue("cache-control")).contains("no-store");
        assertThat(response.body()).doesNotContain(TOKEN, "synthetic", "java.", "SQL", "password");
        if (read.method().equals("HEAD")) assertThat(response.body()).isEmpty();
        else if (status == 503) assertThat(new com.fasterxml.jackson.databind.ObjectMapper().readTree(response.body()).path("code").asText())
                .isEqualTo("CPI_ATTEMPT_QUERY_UNAVAILABLE");
    }
    private record Read(String method, String path) {}
}
