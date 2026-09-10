package com.wallstreetreceipts.api.web.operator;

import static org.assertj.core.api.Assertions.*;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.sql.Timestamp;
import java.util.List;
import java.util.UUID;
import org.apache.coyote.AbstractProtocol;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.web.embedded.tomcat.TomcatWebServer;
import org.springframework.boot.web.servlet.context.ServletWebServerApplicationContext;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wallstreetreceipts.api.application.cpi.CpiCollectionJob;
import com.wallstreetreceipts.api.infrastructure.persistence.JdbcCpiRepository;
import com.wallstreetreceipts.api.infrastructure.provider.bls.BlsCpiParser;
import com.wallstreetreceipts.api.support.CpiTestFixture;

/** Real loopback HTTP + PostgreSQL, but only disposable DEMO records and a synthetic token. */
@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "app.operator-api.enabled=true",
        "OPERATOR_API_ACCESS=CPI_READ_ONLY",
        "app.operator-api.token-sha256=905f28def18eaac05ae6f12b2c3452744afaf626da1343d57b395b544e0519b6",
        "app.cpi.enabled=false", "server.address=0.0.0.0"})
@ActiveProfiles("test")
@Import(OperatorCpiAttemptPostgreSqlTest.FixedClock.class)
class OperatorCpiAttemptPostgreSqlTest {
    @Container static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17-alpine");
    private static final Instant NOW = Instant.parse("2026-09-08T15:02:00.123456Z");
    private static final String PATH = OperatorCpiAttemptController.PATH;
    private static final String TOKEN = "AAECAwQFBgcICQoLDA0ODxAREhMUFRYXGBkaGxwdHh8=";
    @Autowired JdbcTemplate jdbc;
    @Autowired ObjectMapper mapper;
    @Autowired ServletWebServerApplicationContext web;
    @Autowired ApplicationContext context;

    @DynamicPropertySource static void database(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
    }

    @Test void boundedActualHttpReadsPersistedEvidenceWithoutWritesOrCollectorAndWorksWithSelectOnlyRole() throws Exception {
        var server = (TomcatWebServer) web.getWebServer();
        var protocol = (AbstractProtocol<?>) server.getTomcat().getConnector().getProtocolHandler();
        assertThat(protocol.getAddress().isLoopbackAddress()).isTrue();
        assertThat(context.getBeansOfType(CpiCollectionJob.class)).isEmpty();
        var parser = new BlsCpiParser();
        var repository = new JdbcCpiRepository(jdbc, parser);
        for (int i = 0; i < 24; i++) {
            var id = new UUID(0, i + 1);
            var at = NOW.minusSeconds(100 + i / 2); // Ties must use canonical UUID descending order.
            jdbc.update("INSERT INTO bls_cpi_collection_attempts (attempt_id, trigger_kind, started_at, permitted) VALUES (?, 'MANUAL', ?, ?)",
                    id.toString(), Timestamp.from(at), i != 4);
            if (i == 0) {
                var receipt = parser.parse(CpiTestFixture.bytes(), new UUID(0, 1000), at, 2023, 2026);
                repository.append(receipt, CpiTestFixture.bytes(), 2023, 2026);
                jdbc.update("INSERT INTO bls_cpi_collection_results (attempt_id, started_at, permitted, completed_at, status, capture_id, captured_at) VALUES (?, ?, TRUE, ?, 'SAVED', ?, ?)",
                        id.toString(), Timestamp.from(at), Timestamp.from(at), receipt.captureId().toString(), Timestamp.from(at));
            } else if (i >= 2 && i <= 4) {
                jdbc.update("INSERT INTO bls_cpi_collection_results (attempt_id, started_at, permitted, completed_at, status, failure_code, retry_not_before) VALUES (?, ?, ?, ?, ?, ?, ?)",
                        id.toString(), Timestamp.from(at), i != 4, Timestamp.from(at),
                        i == 2 ? "RATE_LIMITED" : i == 3 ? "FAILED" : "SKIPPED", i == 3 ? "FETCH" : null,
                        i == 2 ? Timestamp.from(NOW.plusSeconds(172800)) : null);
            }
        }
        var before = inventory();
        try (var client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).followRedirects(HttpClient.Redirect.NEVER).build()) {
            var recent = request(client, "GET", PATH, true);
            assertThat(recent.statusCode()).isEqualTo(200);
            assertThat(recent.headers().firstValue("Cache-Control")).contains("no-store");
            assertThat(recent.body().length()).isLessThan(32768);
            JsonNode body = mapper.readTree(recent.body());
            assertThat(body.path("metadata").path("dataMode").asText()).isEqualTo("UNVERIFIED");
            assertThat(body.path("metadata").path("observedAtKst").asText()).isEqualTo("2026-09-09T00:02:00.123456+09:00");
            assertThat(body.path("attempts").size()).isEqualTo(20);
            assertThat(body.path("hasMore").asBoolean()).isTrue();
            assertThat(body.path("attempts").get(0).path("attemptId").asText()).isEqualTo(new UUID(0, 2).toString());
            assertThat(body.path("attempts").get(0).path("status").asText()).isEqualTo("UNKNOWN");
            assertThat(body.path("attempts").get(0).path("terminal").isNull()).isTrue();
            assertThat(body.path("attempts").get(1).path("status").asText()).isEqualTo("SAVED");
            assertThat(recent.body()).doesNotContain(TOKEN, "response_json", "registrationkey", "footnotes");
            var head = request(client, "HEAD", PATH, true);
            assertThat(head.statusCode()).isEqualTo(200);
            assertThat(head.body()).isEmpty();
            var exact = request(client, "GET", PATH + "/" + new UUID(0, 24), true);
            assertThat(exact.statusCode()).isEqualTo(200); // Explicit selection works outside the recent window.
            assertThat(mapper.readTree(exact.body()).path("attempt").path("attemptId").asText()).isEqualTo(new UUID(0, 24).toString());
            assertThat(request(client, "GET", PATH + "/" + UUID.randomUUID(), true).statusCode()).isEqualTo(404);
            assertThat(request(client, "GET", PATH, false).statusCode()).isEqualTo(401);
            assertThat(request(client, "POST", PATH, true).statusCode()).isEqualTo(403);
            for (var secPath : List.of("/internal/v1/sec/collection-attempts/root",
                    "/internal/v1/sec/collection-attempts/exact-root")) {
                assertThat(request(client, "POST", secPath, true).statusCode()).isEqualTo(403);
            }
            assertThat(request(client, "GET", "/internal/v1/sec/collection-attempts/" + new UUID(0, 1), true)
                    .statusCode()).isEqualTo(403);
            assertThat(request(client, "GET", PATH + "?limit=1000", true).statusCode()).isEqualTo(400);
        }
        assertThat(inventory()).isEqualTo(before);

        // Prove this query requires only these two tables, not raw CPI receipts or the mutable gate.
        jdbc.execute("CREATE ROLE cpi_query_readonly LOGIN PASSWORD 'disposable-query-test'");
        jdbc.execute("GRANT USAGE ON SCHEMA public TO cpi_query_readonly");
        jdbc.execute("GRANT SELECT ON bls_cpi_collection_attempts, bls_cpi_collection_results TO cpi_query_readonly");
        var readJdbc = new JdbcTemplate(new DriverManagerDataSource(POSTGRES.getJdbcUrl(), "cpi_query_readonly", "disposable-query-test"));
        var readOnly = new JdbcCpiRepository(readJdbc, parser);
        assertThat(readOnly.recentAttempts()).hasSize(21);
        assertThat(readOnly.findAttempt(new UUID(0, 1))).isPresent();
        assertThatThrownBy(() -> readJdbc.queryForObject("SELECT count(*) FROM bls_cpi_captures", Integer.class))
                .isInstanceOf(org.springframework.dao.DataAccessException.class);
        assertThat(inventory()).isEqualTo(before);
    }

    private HttpResponse<String> request(HttpClient client, String method, String pathAndQuery, boolean authenticated) throws Exception {
        var server = (TomcatWebServer) web.getWebServer();
        var protocol = (AbstractProtocol<?>) server.getTomcat().getConnector().getProtocolHandler();
        var base = new URI("http", null, protocol.getAddress().getHostAddress(), server.getPort(), null, null, null);
        var builder = HttpRequest.newBuilder(base.resolve(pathAndQuery)).timeout(Duration.ofSeconds(10))
                .method(method, HttpRequest.BodyPublishers.noBody());
        if (authenticated) builder.header("Authorization", "Bearer " + TOKEN);
        return client.send(builder.build(), HttpResponse.BodyHandlers.ofString());
    }
    private List<?> inventory() {
        return List.of(jdbc.queryForList("SELECT * FROM bls_cpi_collection_attempts ORDER BY attempt_id"),
                jdbc.queryForList("SELECT * FROM bls_cpi_collection_results ORDER BY attempt_id"),
                jdbc.queryForList("SELECT * FROM bls_cpi_captures ORDER BY capture_id"),
                jdbc.queryForList("SELECT * FROM bls_cpi_collection_gate"));
    }
    @TestConfiguration(proxyBeanMethods = false)
    static class FixedClock {
        @Bean @Primary Clock cpiQueryPostgresClock() { return Clock.fixed(NOW, ZoneOffset.UTC); }
    }
}
