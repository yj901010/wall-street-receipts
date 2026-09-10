package com.wallstreetreceipts.api.web.operator;

import static org.assertj.core.api.Assertions.*;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import org.apache.coyote.AbstractProtocol;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.web.embedded.tomcat.TomcatWebServer;
import org.springframework.boot.web.servlet.context.ServletWebServerApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.PostgreSQLContainer;
import com.github.dockerjava.api.model.ExposedPort;
import com.github.dockerjava.api.model.PortBinding;
import com.github.dockerjava.api.model.Ports;
import com.wallstreetreceipts.api.WallStreetReceiptsApiApplication;
import com.wallstreetreceipts.api.application.cpi.CpiCollectionJob;

/** Explicit -Dtest=CpiOperatorBrowserIT only; not an automatically skipped ordinary test. */
class CpiOperatorBrowserIT {
    private static final Instant NOW = Instant.parse("2026-09-08T15:02:00.123456789Z");

    @Test void realSpringPostgresAndProductionBrowserRemainReadOnly() throws Exception {
        assertThat(System.getProperty("wsr.cpi.browser.confirm")).isEqualTo("DISPOSABLE_DEMO_ONLY");
        Path root = Path.of("").toAbsolutePath().normalize().getParent().getParent();
        Path web = CpiBrowserProcess.verifyMirror(root, System.getProperty("wsr.cpi.browser.web"));
        String node = System.getProperty("wsr.cpi.browser.node", "node");
        Path logs = Files.createTempDirectory(root.resolve(".cache"), "adr071-evidence-");
        // Build the verified current source, not a pre-existing/stale artifact.
        CpiBrowserProcess.run(node, web, logs.resolve("build.log"), List.of("node_modules/next/dist/bin/next", "build"), java.util.Map.of(), 120);
        byte[] bytes = new byte[32]; new SecureRandom().nextBytes(bytes);
        String token = Base64.getEncoder().encodeToString(bytes);
        String digest = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(token.getBytes(java.nio.charset.StandardCharsets.US_ASCII)));
        try (var postgres = new PostgreSQLContainer<>("postgres:17-alpine")
                .withDatabaseName("cpi_browser_demo").withUsername("cpi_demo_owner").withPassword("DisposableDatabaseOnly")
                .withReuse(false).withLabel("com.wallstreetreceipts.cpi-browser-rehearsal", "ADR-071")
                .withCreateContainerCmdModifier(cmd -> cmd.getHostConfig().withPortBindings(
                        new PortBinding(Ports.Binding.bindIpAndPort("127.0.0.1", 0), ExposedPort.tcp(5432))))) {
            postgres.start();
            assertThat(InetAddress.getByName(postgres.getHost()).isLoopbackAddress()).isTrue();
            var binding = postgres.getContainerInfo().getNetworkSettings().getPorts().getBindings().get(ExposedPort.tcp(5432));
            assertThat(binding).hasSize(1);
            assertThat(binding[0].getHostIp()).isEqualTo("127.0.0.1");
            Flyway.configure().dataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword()).load().migrate();
            var owner = new JdbcTemplate(new DriverManagerDataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword()));
            owner.execute("CREATE ROLE cpi_browser_reader LOGIN PASSWORD 'DisposableReaderOnly'");
            owner.execute("GRANT USAGE ON SCHEMA public TO cpi_browser_reader");
            owner.execute("GRANT SELECT ON bls_cpi_collection_attempts, bls_cpi_collection_results TO cpi_browser_reader");
            var empty = CpiBrowserEvidence.inventory(owner);
            try (var context = (ServletWebServerApplicationContext) new SpringApplicationBuilder(
                    WallStreetReceiptsApiApplication.class, FixedClock.class).run(
                    "--spring.config.location=classpath:/application.yml", "--spring.config.import=",
                    "--spring.profiles.active=cpi-browser-rehearsal", "--spring.flyway.enabled=false",
                    "--spring.datasource.url=" + postgres.getJdbcUrl(), "--spring.datasource.driver-class-name=org.postgresql.Driver",
                    "--spring.datasource.username=cpi_browser_reader", "--spring.datasource.password=DisposableReaderOnly",
                    "--app.operator-api.enabled=true", "--app.operator-api.token-sha256=" + digest,
                    "--app.operator-api.access=CPI_READ_ONLY",
                    "--app.cpi.enabled=false", "--app.public-data.sec.enabled=false",
                    "--app.providers.market=fixture", "--app.providers.analyst=disabled",
                    "--server.address=0.0.0.0", "--server.port=0", "--server.shutdown=immediate")) {
                var server = (TomcatWebServer) context.getWebServer();
                var protocol = (AbstractProtocol<?>) server.getTomcat().getConnector().getProtocolHandler();
                assertThat(protocol.getAddress().isLoopbackAddress()).isTrue();
                assertThat(context.getBeansOfType(CpiCollectionJob.class)).isEmpty();
                assertThat(context.containsBean("fixtureAnalystCallImporter")).isFalse();
                var reader = context.getBean(JdbcTemplate.class);
                assertThat(reader.queryForObject("SELECT current_user", String.class)).isEqualTo("cpi_browser_reader");
                assertThatThrownBy(() -> reader.update("DELETE FROM bls_cpi_collection_attempts WHERE FALSE"))
                        .isInstanceOf(org.springframework.dao.DataAccessException.class);
                assertThatThrownBy(() -> reader.queryForObject("SELECT count(*) FROM bls_cpi_captures", Integer.class))
                        .isInstanceOf(org.springframework.dao.DataAccessException.class);
                browser(node, web, logs, server.getPort(), token, "empty");
                assertThat(CpiBrowserEvidence.inventory(owner)).isEqualTo(empty);
                CpiBrowserEvidence.seed(owner);
                var seeded = CpiBrowserEvidence.inventory(owner);
                browser(node, web, logs, server.getPort(), token, "seeded");
                assertThat(CpiBrowserEvidence.inventory(owner)).isEqualTo(seeded);
                // Fault only the disposable reader's permission. No product fault endpoint or data mutation.
                owner.execute("REVOKE SELECT ON bls_cpi_collection_results FROM cpi_browser_reader");
                browser(node, web, logs, server.getPort(), token, "unavailable");
                assertThat(CpiBrowserEvidence.inventory(owner)).isEqualTo(seeded);
                owner.execute("GRANT SELECT ON bls_cpi_collection_results TO cpi_browser_reader");
                browser(node, web, logs, server.getPort(), token, "recovered");
                assertThat(CpiBrowserEvidence.inventory(owner)).isEqualTo(seeded);
            }
        }
        System.out.println("ADR-071 DEMO PASS: production browser -> real Spring -> SELECT-only PostgreSQL; all CPI tables unchanged by queries; evidence " + logs);
    }

    private static void browser(String node, Path web, Path logs, int apiPort, String token, String phase) throws Exception {
        int uiPort;
        try (var socket = new ServerSocket(0, 1, InetAddress.getByName("127.0.0.1"))) { uiPort = socket.getLocalPort(); }
        // A bind race is a test failure; the launcher may never reuse an existing listener.
        CpiBrowserProcess.run(node, web, logs.resolve(phase + ".log"),
                List.of("node_modules/@playwright/test/cli.js", "test", "--config", "operator/full-stack.config.ts"),
                java.util.Map.of("CPI_OPERATOR_UI_PORT", "" + uiPort, "CPI_OPERATOR_API_PORT", "" + apiPort,
                        "WSR_CPI_BROWSER_TOKEN", token, "WSR_CPI_BROWSER_PHASE", phase), 180);
        try (var socket = new ServerSocket(uiPort, 1, InetAddress.getByName("127.0.0.1"))) {
            assertThat(socket.isBound()).isTrue(); // Owned UI listener was actually closed.
        }
    }
    @TestConfiguration(proxyBeanMethods = false)
    static class FixedClock {
        @Bean @Primary Clock cpiBrowserClock() { return Clock.fixed(NOW, ZoneOffset.UTC); }
    }
}
