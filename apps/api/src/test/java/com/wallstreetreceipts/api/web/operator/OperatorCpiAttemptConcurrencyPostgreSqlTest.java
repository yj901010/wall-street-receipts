package com.wallstreetreceipts.api.web.operator;

import static org.assertj.core.api.Assertions.*;
import java.net.InetAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.sql.Connection;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import org.apache.coyote.AbstractProtocol;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.web.embedded.tomcat.TomcatWebServer;
import org.springframework.boot.web.servlet.context.ServletWebServerApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.PostgreSQLContainer;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zaxxer.hikari.HikariDataSource;
import com.github.dockerjava.api.model.ExposedPort;
import com.github.dockerjava.api.model.PortBinding;
import com.github.dockerjava.api.model.Ports;
import com.wallstreetreceipts.api.WallStreetReceiptsApiApplication;
import com.wallstreetreceipts.api.application.cpi.CpiCollectionJob;

/** Real HTTP admission and SQL lock/cancel evidence, using only an owned disposable DEMO database. */
class OperatorCpiAttemptConcurrencyPostgreSqlTest {
    private static final Instant NOW = Instant.parse("2026-09-08T15:02:00.123456Z");
    private static final String TOKEN = "AAECAwQFBgcICQoLDA0ODxAREhMUFRYXGBkaGxwdHh8=";
    private static final String PATH = OperatorCpiAttemptController.PATH;
    private static final String SELECTED = PATH + "/00000000-0000-0000-0000-000000000001";
    private static final List<Read> READS = List.of(new Read("GET", PATH), new Read("GET", SELECTED),
            new Read("HEAD", PATH), new Read("HEAD", SELECTED));
    private final ObjectMapper mapper = new ObjectMapper();

    @Test @Timeout(90)
    void onlyFourReadsReachPostgresAndOverflowNeverQueuesWhileAllSlotsRecover() throws Exception {
        try (var postgres = new PostgreSQLContainer<>("postgres:17-alpine")
                .withDatabaseName("cpi_concurrency_demo").withUsername("cpi_demo_owner").withPassword("DisposableDatabaseOnly")
                .withReuse(false).withLabel("com.wallstreetreceipts.cpi-concurrency-test", "ADR-075")
                .withCreateContainerCmdModifier(cmd -> cmd.getHostConfig().withPortBindings(
                        new PortBinding(Ports.Binding.bindIpAndPort("127.0.0.1", 0), ExposedPort.tcp(5432))))) {
            postgres.start();
            assertThat(InetAddress.getByName(postgres.getHost()).isLoopbackAddress()).isTrue();
            var bindings = postgres.getContainerInfo().getNetworkSettings().getPorts().getBindings().get(ExposedPort.tcp(5432));
            assertThat(bindings).hasSize(1);
            assertThat(bindings[0].getHostIp()).isEqualTo("127.0.0.1");
            Flyway.configure().dataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword()).load().migrate();
            var datasource = new DriverManagerDataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
            var owner = new JdbcTemplate(datasource);
            owner.setQueryTimeout(1);
            owner.execute("CREATE ROLE cpi_concurrency_reader LOGIN PASSWORD 'DisposableReaderOnly'");
            owner.execute("GRANT USAGE ON SCHEMA public TO cpi_concurrency_reader");
            owner.execute("GRANT SELECT ON bls_cpi_collection_attempts, bls_cpi_collection_results TO cpi_concurrency_reader");
            CpiBrowserEvidence.seed(owner);
            var before = CpiBrowserEvidence.inventory(owner);
            try (var context = (ServletWebServerApplicationContext) new SpringApplicationBuilder(
                    WallStreetReceiptsApiApplication.class, FixedClock.class).run(
                    "--spring.config.location=classpath:/application.yml", "--spring.config.import=",
                    "--spring.profiles.active=cpi-concurrency-test", "--spring.flyway.enabled=false",
                    "--spring.datasource.url=" + postgres.getJdbcUrl(), "--spring.datasource.driver-class-name=org.postgresql.Driver",
                    "--spring.datasource.username=cpi_concurrency_reader", "--spring.datasource.password=DisposableReaderOnly",
                    "--spring.datasource.hikari.maximum-pool-size=4", "--spring.datasource.hikari.minimum-idle=4",
                    "--spring.datasource.hikari.connection-timeout=9000",
                    "--app.operator-api.enabled=true", "--app.operator-api.access=CPI_READ_ONLY",
                    "--app.operator-api.token-sha256=905f28def18eaac05ae6f12b2c3452744afaf626da1343d57b395b544e0519b6",
                    "--app.cpi.enabled=false", "--app.public-data.sec.enabled=false",
                    "--app.providers.market=fixture", "--app.providers.analyst=disabled",
                    "--server.address=0.0.0.0", "--server.port=0", "--server.shutdown=immediate");
                 var client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).followRedirects(HttpClient.Redirect.NEVER).build()) {
                var server = (TomcatWebServer) context.getWebServer();
                var protocol = (AbstractProtocol<?>) server.getTomcat().getConnector().getProtocolHandler();
                assertThat(protocol.getAddress().isLoopbackAddress()).isTrue();
                assertThat(context.getBeansOfType(CpiCollectionJob.class)).isEmpty();
                assertThat(context.containsBean("fixtureAnalystCallImporter")).isFalse();
                var reader = context.getBean(JdbcTemplate.class);
                assertThat(reader.queryForObject("SELECT current_user", String.class)).isEqualTo("cpi_concurrency_reader");
                assertThatThrownBy(() -> reader.update("DELETE FROM bls_cpi_collection_attempts WHERE FALSE"))
                        .isInstanceOf(org.springframework.dao.DataAccessException.class);
                var base = new URI("http", null, protocol.getAddress().getHostAddress(), server.getPort(), null, null, null);
                for (var read : READS) assertResponse(send(client, base, read, 5, true), read, 200);
                var pool = context.getBean(HikariDataSource.class);
                assertThat(pool.getConnectionTimeout()).isEqualTo(1000);
                exhaustedPool(client, base, pool);
                for (var read : READS) assertResponse(send(client, base, read, 5, true), read, 200);
                assertThat(CpiBrowserEvidence.inventory(owner)).isEqualTo(before);
                // Normal completion, actual SQL cancellation, then full-capacity recovery after cancellation.
                for (boolean cancel : List.of(false, true, false)) {
                    wave(client, base, datasource, owner, cancel);
                    for (var read : READS) assertResponse(send(client, base, read, 5, true), read, 200);
                    assertThat(CpiBrowserEvidence.inventory(owner)).isEqualTo(before);
                }
            }
            assertThat(CpiBrowserEvidence.inventory(owner)).isEqualTo(before);
        }
    }

    private void exhaustedPool(HttpClient client, URI base, HikariDataSource pool) throws Exception {
        // Real leases exhaust the API's own pool; no SQL lock, fake datasource or product fault endpoint.
        try (var first = pool.getConnection(); var second = pool.getConnection();
             var third = pool.getConnection(); var fourth = pool.getConnection()) {
            assertThat(pool.getHikariPoolMXBean().getActiveConnections()).isEqualTo(4);
            assertThat(pool.getHikariPoolMXBean().getIdleConnections()).isZero();
            for (var read : READS) {
                long start = System.nanoTime();
                assertResponse(send(client, base, read, 4, true), read, 503);
                assertThat(Duration.ofNanos(System.nanoTime() - start))
                        .isBetween(Duration.ofMillis(500), Duration.ofSeconds(3)); // Test tolerance, not an HTTP SLA.
                assertThat(pool.getHikariPoolMXBean().getThreadsAwaitingConnection()).isZero();
                assertThat(pool.getHikariPoolMXBean().getActiveConnections()).isEqualTo(4);
            }
            assertThat(send(client, base, new Read("GET", PATH), 1, false).statusCode()).isEqualTo(401);
            assertThat(send(client, base, new Read("GET", PATH + "/invalid"), 1, true).statusCode()).isEqualTo(400);
        }
        assertThat(pool.getHikariPoolMXBean().getActiveConnections()).isZero();
        assertThat(pool.getHikariPoolMXBean().getThreadsAwaitingConnection()).isZero();
    }

    private void wave(HttpClient client, URI base, DriverManagerDataSource datasource, JdbcTemplate owner, boolean cancel) throws Exception {
        var held = new ArrayList<CompletableFuture<HttpResponse<String>>>();
        try (var blocker = datasource.getConnection()) {
            blocker.setAutoCommit(false);
            try (var statement = blocker.createStatement()) {
                statement.setQueryTimeout(2);
                statement.execute("LOCK TABLE bls_cpi_collection_results IN ACCESS EXCLUSIVE MODE");
            }
            int pid = backendPid(blocker);
            for (var read : READS) held.add(client.sendAsync(request(base, read, 10, true), HttpResponse.BodyHandlers.ofString()));
            awaitBlocked(owner, pid, 4);
            for (var read : READS) {
                // Overflow must complete while the first four SQL statements are still blocked, not wait for their timeout.
                assertResponse(send(client, base, read, 1, true), read, 503);
                assertThat(blocked(owner, pid)).isEqualTo(4);
            }
            assertThat(send(client, base, new Read("GET", PATH), 1, false).statusCode()).isEqualTo(401);
            assertThat(send(client, base, new Read("GET", PATH + "/invalid"), 1, true).statusCode()).isEqualTo(400);
            assertThat(send(client, base, new Read("GET", PATH + "?limit=1"), 1, true).statusCode()).isEqualTo(400);
            assertThat(send(client, base, new Read("POST", "/internal/v1/sec/collection-attempts/root"), 1, true).statusCode()).isEqualTo(403);
            assertThat(blocked(owner, pid)).isEqualTo(4);
            if (cancel) {
                for (int i = 0; i < held.size(); i++) assertResponse(held.get(i).get(6, TimeUnit.SECONDS), READS.get(i), 503);
                awaitBlocked(owner, pid, 0);
            }
            // Connection close rolls back only this test's lock; the admitted non-cancelled wave then reads normally.
        } finally {
            // Also drain owned requests if an assertion failed, after releasing the lock.
            CompletableFuture.allOf(held.toArray(CompletableFuture[]::new)).get(12, TimeUnit.SECONDS);
        }
        for (int i = 0; i < held.size(); i++) assertResponse(held.get(i).get(), READS.get(i), cancel ? 503 : 200);
    }

    private static int backendPid(Connection connection) throws Exception {
        try (var statement = connection.createStatement(); var result = statement.executeQuery("SELECT pg_backend_pid()")) {
            assertThat(result.next()).isTrue();
            return result.getInt(1);
        }
    }
    private static int blocked(JdbcTemplate owner, int pid) {
        return owner.queryForObject("SELECT count(*) FROM pg_stat_activity WHERE datname = current_database() AND ? = ANY(pg_blocking_pids(pid))",
                Integer.class, pid);
    }
    private static void awaitBlocked(JdbcTemplate owner, int pid, int expected) throws Exception {
        long deadline = System.nanoTime() + Duration.ofSeconds(2).toNanos();
        int count;
        while ((count = blocked(owner, pid)) != expected && System.nanoTime() < deadline) Thread.sleep(10);
        assertThat(count).as("actual blocked PostgreSQL statements").isEqualTo(expected);
    }
    private static HttpRequest request(URI base, Read read, int seconds, boolean authenticated) {
        var request = HttpRequest.newBuilder(base.resolve(read.path())).timeout(Duration.ofSeconds(seconds))
                .method(read.method(), HttpRequest.BodyPublishers.noBody());
        if (authenticated) request.header("Authorization", "Bearer " + TOKEN);
        return request.build();
    }
    private static HttpResponse<String> send(HttpClient client, URI base, Read read, int seconds, boolean authenticated) throws Exception {
        return client.send(request(base, read, seconds, authenticated), HttpResponse.BodyHandlers.ofString());
    }
    private void assertResponse(HttpResponse<String> response, Read read, int expected) throws Exception {
        assertThat(response.statusCode()).isEqualTo(expected);
        assertThat(response.headers().firstValue("Cache-Control")).contains("no-store");
        assertThat(response.body()).doesNotContain(TOKEN, "SELECT", "password", "cpi_concurrency_reader", "org.postgresql");
        if (read.method().equals("HEAD")) assertThat(response.body()).isEmpty();
        else if (expected == 503) {
            var body = mapper.readTree(response.body());
            assertThat(body.path("code").asText()).isEqualTo("CPI_ATTEMPT_QUERY_UNAVAILABLE");
            assertThat(body.path("detail").asText()).isEqualTo("CPI attempt query did not return evidence.");
        } else {
            var body = mapper.readTree(response.body());
            assertThat(body.path("metadata").path("dataMode").asText()).isEqualTo("UNVERIFIED");
            if (read.path().equals(PATH)) assertThat(body.path("attempts").size()).isEqualTo(20);
            else assertThat(body.path("attempt").path("attemptId").asText()).isEqualTo(SELECTED.substring(PATH.length() + 1));
        }
    }
    private record Read(String method, String path) {}
    @TestConfiguration(proxyBeanMethods = false)
    static class FixedClock {
        @Bean @Primary Clock cpiConcurrencyClock() { return Clock.fixed(NOW, ZoneOffset.UTC); }
    }
}
