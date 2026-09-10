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
import java.sql.Connection;
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
        "spring.datasource.hikari.maximum-pool-size=1",
        "app.providers.analyst=disabled",
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
        // Flyway needs its own migration connections; the single connection is the query API pool.
        registry.add("spring.flyway.url", POSTGRES::getJdbcUrl);
        registry.add("spring.flyway.user", POSTGRES::getUsername);
        registry.add("spring.flyway.password", POSTGRES::getPassword);
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
            assertLockedHttpReadsCancelAndRecover(client);
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
        try (var blocker = lockTable("bls_cpi_collection_results")) {
            assertThatThrownBy(readOnly::recentAttempts).isInstanceOf(org.springframework.dao.QueryTimeoutException.class);
            assertThatThrownBy(() -> readOnly.findAttempt(new UUID(0, 1)))
                    .isInstanceOf(org.springframework.dao.QueryTimeoutException.class);
            assertNoBlockedQueries(blocker);
        }
        assertThat(readOnly.recentAttempts()).hasSize(21);
        assertThat(readOnly.findAttempt(new UUID(0, 1))).isPresent();
        assertThat(inventory()).isEqualTo(before);
    }

    private void assertLockedHttpReadsCancelAndRecover(HttpClient client) throws Exception {
        var paths = List.of(PATH, PATH + "/" + new UUID(0, 1));
        var before = inventory();
        for (var table : List.of("bls_cpi_collection_attempts", "bls_cpi_collection_results")) {
            try (var blocker = lockTable(table)) {
                for (var path : paths) {
                    long started = System.nanoTime();
                    var response = request(client, "GET", path, true);
                    var elapsed = Duration.ofNanos(System.nanoTime() - started);
                    assertThat(response.statusCode()).isEqualTo(503);
                    // Scheduling tolerance, not an end-to-end SLA; SQL itself has a three-second limit.
                    assertThat(elapsed).isBetween(Duration.ofSeconds(2), Duration.ofSeconds(8));
                    assertThat(response.headers().firstValue("Cache-Control")).contains("no-store");
                    var body = mapper.readTree(response.body());
                    assertThat(body.path("code").asText()).isEqualTo("CPI_ATTEMPT_QUERY_UNAVAILABLE");
                    assertThat(response.body()).doesNotContain(TOKEN, "SELECT", "bls_cpi", "postgres", "canceling", "SQLException");
                    // A one-connection API pool must be reusable even while the lock remains held.
                    // Verify actual server-side cancellation, not just a client-side HTTP timeout.
                    assertNoBlockedQueries(blocker);
                }
                assertThat(request(client, "GET", PATH, false).statusCode()).isEqualTo(401);
                assertThat(request(client, "GET", PATH + "/invalid", true).statusCode()).isEqualTo(400);
            }
            for (var path : paths) assertThat(request(client, "GET", path, true).statusCode()).isEqualTo(200);
            assertThat(inventory()).isEqualTo(before);
        }
    }

    private Connection lockTable(String table) throws Exception {
        if (!List.of("bls_cpi_collection_attempts", "bls_cpi_collection_results").contains(table)) {
            throw new IllegalArgumentException("Unexpected disposable lock target");
        }
        var blocker = new DriverManagerDataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword()).getConnection();
        try {
            blocker.setAutoCommit(false);
            try (var statement = blocker.createStatement()) {
                statement.setQueryTimeout(5);
                statement.execute("LOCK TABLE " + table + " IN ACCESS EXCLUSIVE MODE");
            }
            return blocker; // Closing the dedicated connection rolls back/releases only this test's lock.
        } catch (Exception exception) {
            blocker.close();
            throw exception;
        }
    }

    private void assertNoBlockedQueries(Connection blocker) throws Exception {
        int pid;
        try (var statement = blocker.createStatement(); var result = statement.executeQuery("SELECT pg_backend_pid()")) {
            assertThat(result.next()).isTrue();
            pid = result.getInt(1);
        }
        assertThat(jdbc.queryForObject("SELECT count(*) FROM pg_stat_activity WHERE datname = current_database() AND ? = ANY(pg_blocking_pids(pid))",
                Integer.class, pid)).isZero();
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
