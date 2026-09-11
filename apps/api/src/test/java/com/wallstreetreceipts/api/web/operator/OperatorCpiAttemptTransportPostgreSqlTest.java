package com.wallstreetreceipts.api.web.operator;

import static org.assertj.core.api.Assertions.*;
import java.net.InetAddress;
import java.net.SocketTimeoutException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
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
import com.github.dockerjava.api.model.ExposedPort;
import com.github.dockerjava.api.model.PortBinding;
import com.github.dockerjava.api.model.Ports;
import com.zaxxer.hikari.HikariDataSource;
import com.wallstreetreceipts.api.WallStreetReceiptsApiApplication;
import com.wallstreetreceipts.api.application.cpi.CpiCollectionJob;

/** Actual PostgreSQL/HTTP with blackholed response bytes, never a fixture datasource or fault route. */
class OperatorCpiAttemptTransportPostgreSqlTest {
    private static final String TOKEN = "AAECAwQFBgcICQoLDA0ODxAREhMUFRYXGBkaGxwdHh8=";
    private static final String PATH = OperatorCpiAttemptController.PATH;
    private static final String SELECTED = PATH + "/00000000-0000-0000-0000-000000000001";
    private static final List<Read> READS = List.of(new Read("GET", PATH), new Read("GET", SELECTED),
            new Read("HEAD", PATH), new Read("HEAD", SELECTED));
    private final ObjectMapper mapper = new ObjectMapper();

    @Test @Timeout(100)
    void silentDatabaseTransportFailsClosedEvictsBrokenConnectionsAndRecoversAllReadShapes() throws Exception {
        try (var postgres = new PostgreSQLContainer<>("postgres:17-alpine")
                .withDatabaseName("cpi_transport_demo").withUsername("cpi_demo_owner").withPassword("DisposableDatabaseOnly")
                .withReuse(false).withLabel("com.wallstreetreceipts.cpi-transport-test", "ADR-077")
                .withCreateContainerCmdModifier(cmd -> cmd.getHostConfig().withPortBindings(
                        new PortBinding(Ports.Binding.bindIpAndPort("127.0.0.1", 0), ExposedPort.tcp(5432))))) {
            postgres.start();
            assertThat(InetAddress.getByName(postgres.getHost()).isLoopbackAddress()).isTrue();
            var bindings = postgres.getContainerInfo().getNetworkSettings().getPorts().getBindings().get(ExposedPort.tcp(5432));
            assertThat(bindings).hasSize(1);
            assertThat(bindings[0].getHostIp()).isEqualTo("127.0.0.1");
            Flyway.configure().dataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword()).load().migrate();
            var ownerSource = new DriverManagerDataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
            var owner = new JdbcTemplate(ownerSource);
            owner.setQueryTimeout(2);
            owner.execute("CREATE ROLE cpi_transport_reader LOGIN PASSWORD 'DisposableReaderOnly'");
            owner.execute("GRANT USAGE ON SCHEMA public TO cpi_transport_reader");
            owner.execute("GRANT SELECT ON bls_cpi_collection_attempts, bls_cpi_collection_results TO cpi_transport_reader");
            CpiBrowserEvidence.seed(owner);
            var before = CpiBrowserEvidence.inventory(owner);
            try (var relay = new CpiPostgresRelay(postgres.getHost(), postgres.getMappedPort(5432));
                 var context = (ServletWebServerApplicationContext) new SpringApplicationBuilder(
                         WallStreetReceiptsApiApplication.class, FixedClock.class).run(
                         "--spring.config.location=classpath:/application.yml", "--spring.config.import=",
                         "--spring.profiles.active=cpi-transport-test", "--spring.flyway.enabled=false",
                         "--spring.datasource.url=jdbc:postgresql://127.0.0.1:" + relay.port() + "/cpi_transport_demo?socketTimeout=0&connectTimeout=0&cancelSignalTimeout=0",
                         "--spring.datasource.driver-class-name=org.postgresql.Driver",
                         "--spring.datasource.username=cpi_transport_reader", "--spring.datasource.password=DisposableReaderOnly",
                         "--spring.datasource.hikari.maximum-pool-size=1", "--spring.datasource.hikari.minimum-idle=1",
                         "--app.operator-api.enabled=true", "--app.operator-api.access=CPI_READ_ONLY",
                         "--app.operator-api.token-sha256=905f28def18eaac05ae6f12b2c3452744afaf626da1343d57b395b544e0519b6",
                         "--app.cpi.enabled=false", "--app.public-data.sec.enabled=false",
                         "--app.providers.market=fixture", "--app.providers.analyst=disabled",
                         "--server.address=0.0.0.0", "--server.port=0", "--server.shutdown=immediate");
                 var client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).followRedirects(HttpClient.Redirect.NEVER).build()) {
                var server = (TomcatWebServer) context.getWebServer();
                var protocol = (AbstractProtocol<?>) server.getTomcat().getConnector().getProtocolHandler();
                assertThat(protocol.getAddress().isLoopbackAddress()).isTrue();
                var base = new URI("http", null, protocol.getAddress().getHostAddress(), server.getPort(), null, null, null);
                assertThat(context.getBeansOfType(CpiCollectionJob.class)).isEmpty();
                assertThat(context.containsBean("fixtureAnalystCallImporter")).isFalse();
                var pool = context.getBean(HikariDataSource.class);
                assertThat(pool.getValidationTimeout()).isEqualTo(750);
                assertThat(pool.getConnectionTimeout()).isEqualTo(1000);
                // With JDBC statement cancellation disabled, the actual socket timeout still closes this transport.
                try (var connection = pool.getConnection(); var statement = connection.createStatement()) {
                    assertThat(connection.getNetworkTimeout()).isEqualTo(5000);
                    statement.setQueryTimeout(0);
                    assertThatThrownBy(() -> statement.execute("SELECT pg_sleep(15)"))
                            .isInstanceOf(java.sql.SQLException.class).hasRootCauseInstanceOf(SocketTimeoutException.class);
                    assertThat(connection.isClosed()).isTrue();
                }
                for (var read : READS) {
                    assertResponse(send(client, base, read, true), read, 200);
                    var held = ownerSource.getConnection();
                    List<CpiPostgresRelay.Link> silenced;
                    long started;
                    java.util.concurrent.CompletableFuture<HttpResponse<String>> pending;
                    try (held) {
                        held.setAutoCommit(false);
                        try (var lock = held.createStatement()) {
                            lock.setQueryTimeout(2);
                            lock.execute("LOCK TABLE bls_cpi_collection_results IN ACCESS EXCLUSIVE MODE");
                        }
                        int pid;
                        try (var sql = held.createStatement(); var result = sql.executeQuery("SELECT pg_backend_pid()")) {
                            assertThat(result.next()).isTrue();
                            pid = result.getInt(1);
                        }
                        started = System.nanoTime();
                        pending = client.sendAsync(request(base, read, true), HttpResponse.BodyHandlers.ofString());
                        long deadline = System.nanoTime() + Duration.ofSeconds(2).toNanos();
                        int blocked;
                        while ((blocked = owner.queryForObject("SELECT count(*) FROM pg_stat_activity WHERE ? = ANY(pg_blocking_pids(pid))",
                                Integer.class, pid)) != 1 && System.nanoTime() < deadline) Thread.sleep(10);
                        assertThat(blocked).isEqualTo(1); // Actual query is running, past pool connection validation.
                        silenced = relay.blackholeEstablishedResponses();
                        assertThat(silenced).hasSize(1);
                    } // Release only our table lock; the server's genuine result bytes are now discarded.
                    try {
                        assertThat(send(client, base, new Read("GET", PATH), false).statusCode()).isEqualTo(401);
                        assertThat(send(client, base, new Read("GET", PATH + "/invalid"), true).statusCode()).isEqualTo(400);
                        assertResponse(pending.get(9, TimeUnit.SECONDS), read, 503);
                        assertThat(Duration.ofNanos(System.nanoTime() - started)).isLessThan(Duration.ofSeconds(9));
                        assertThat(silenced.getFirst().discardedBytes()).isPositive();
                        // ADR-079 returns the HTTP failure before the driver's independent socket cleanup.
                        // Do not confuse a cancelled caller wait with released JDBC resources.
                        long cleanupDeadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(6);
                        while ((!silenced.getFirst().closed() || pool.getHikariPoolMXBean().getActiveConnections() != 0)
                                && System.nanoTime() < cleanupDeadline) Thread.sleep(10);
                        assertThat(pool.getHikariPoolMXBean().getActiveConnections()).isZero();
                        assertThat(pool.getHikariPoolMXBean().getThreadsAwaitingConnection()).isZero();
                        for (var recovered : READS) assertResponse(send(client, base, recovered, true), recovered, 200);
                        assertThat(silenced.getFirst().closed()).isTrue();
                        assertThat(CpiBrowserEvidence.inventory(owner)).isEqualTo(before);
                    } finally {
                        pending.get(10, TimeUnit.SECONDS); // Drain this test's request before closing its context/relay.
                    }
                }
                var reader = context.getBean(JdbcTemplate.class);
                assertThat(reader.queryForObject("SELECT current_user", String.class)).isEqualTo("cpi_transport_reader");
                assertThatThrownBy(() -> reader.update("DELETE FROM bls_cpi_collection_attempts WHERE FALSE"))
                        .isInstanceOf(org.springframework.dao.DataAccessException.class);
            }
            assertThat(CpiBrowserEvidence.inventory(owner)).isEqualTo(before);
        }
    }

    private static HttpRequest request(URI base, Read read, boolean authenticated) {
        var builder = HttpRequest.newBuilder(base.resolve(read.path())).timeout(Duration.ofSeconds(10))
                .method(read.method(), HttpRequest.BodyPublishers.noBody());
        if (authenticated) builder.header("Authorization", "Bearer " + TOKEN);
        return builder.build();
    }
    private static HttpResponse<String> send(HttpClient client, URI base, Read read, boolean authenticated) throws Exception {
        return client.send(request(base, read, authenticated), HttpResponse.BodyHandlers.ofString());
    }
    private void assertResponse(HttpResponse<String> response, Read read, int status) throws Exception {
        assertThat(response.statusCode()).isEqualTo(status);
        assertThat(response.headers().firstValue("Cache-Control")).contains("no-store");
        assertThat(response.body()).doesNotContain(TOKEN, "SELECT", "password", "cpi_transport_reader", "org.postgresql", "socketTimeout");
        if (read.method().equals("HEAD")) assertThat(response.body()).isEmpty();
        else if (status == 503) {
            var body = mapper.readTree(response.body());
            assertThat(body.path("code").asText()).isEqualTo("CPI_ATTEMPT_QUERY_UNAVAILABLE");
            assertThat(body.path("detail").asText()).isEqualTo("CPI attempt query did not return evidence.");
        } else assertThat(mapper.readTree(response.body()).path("metadata").path("dataMode").asText()).isEqualTo("UNVERIFIED");
    }
    private record Read(String method, String path) {}
    @TestConfiguration(proxyBeanMethods = false)
    static class FixedClock {
        @Bean @Primary Clock cpiTransportClock() { return Clock.fixed(Instant.parse("2026-09-08T15:02:00.123456Z"), ZoneOffset.UTC); }
    }
}
