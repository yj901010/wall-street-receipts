package com.wallstreetreceipts.api.application.scoring;

import static org.assertj.core.api.Assertions.*;
import static com.wallstreetreceipts.api.application.scoring.EndpointScoringFixture.*;
import java.net.*;
import java.net.http.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.*;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.web.servlet.context.ServletWebServerApplicationContext;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.*;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.datasource.*;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import com.github.dockerjava.api.model.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wallstreetreceipts.api.WallStreetReceiptsApiApplication;
import com.wallstreetreceipts.api.application.port.out.*;
import com.wallstreetreceipts.api.infrastructure.persistence.JdbcAnalystCallRepository;
import com.wallstreetreceipts.api.infrastructure.provider.fixture.FixtureAnalystCallProvider;

/** Mandatory real PostgreSQL migration, concurrent append, restart and SELECT-only HTTP acceptance. */
class ScoringReceiptPostgreSqlTest {
    @Test @Timeout(90)
    void upgradeConcurrencyRestartRestrictedReadsAndFailClosedIntegrity() throws Exception {
        try (var db = new PostgreSQLContainer<>("postgres:17-alpine").withDatabaseName("scoring_receipt_demo")
                .withUsername("receipt_demo_owner").withPassword("DisposableDatabaseOnly").withReuse(false)
                .withLabel("com.wallstreetreceipts.scoring-test", "ADR-081")
                .withCreateContainerCmdModifier(cmd -> cmd.getHostConfig().withPortBindings(
                        new PortBinding(Ports.Binding.bindIpAndPort("127.0.0.1", 0), ExposedPort.tcp(5432))))) {
            db.start();
            assertThat(InetAddress.getByName(db.getHost()).isLoopbackAddress()).isTrue();
            assertThat(db.getContainerInfo().getNetworkSettings().getPorts().getBindings().get(ExposedPort.tcp(5432))[0].getHostIp()).isEqualTo("127.0.0.1");
            Flyway.configure().dataSource(db.getJdbcUrl(), db.getUsername(), db.getPassword()).target("11").load().migrate();
            var ds = new DriverManagerDataSource(db.getJdbcUrl(), db.getUsername(), db.getPassword());
            var owner = new JdbcTemplate(ds);
            var calls = new JdbcAnalystCallRepository(new NamedParameterJdbcTemplate(ds));
            var provider = new FixtureAnalystCallProvider(new ObjectMapper());
            var transactions = new TransactionTemplate(new DataSourceTransactionManager(ds));
            var call = transactions.execute(s -> { calls.importDataSet(provider.load()); return ScoringReceiptFixture.seed(provider, calls); });
            var beforeUpgrade = inventory(owner);
            var flyway = Flyway.configure().dataSource(db.getJdbcUrl(), db.getUsername(), db.getPassword()).load();
            assertThat(flyway.migrate().migrationsExecuted).isEqualTo(1);
            assertThat(flyway.info().applied()).hasSize(12);
            var afterUpgrade = inventory(owner); afterUpgrade.remove("demo_scoring_receipts");
            assertThat(afterUpgrade).isEqualTo(beforeUpgrade);
            assertThat(flyway.migrate().migrationsExecuted).isZero();
            var input = ScoringReceiptFixture.input(call);
            UUID id; byte[] originalBytes;
            try (var context = start(db, db.getUsername(), db.getPassword()); var pool = Executors.newFixedThreadPool(6)) {
                var service = context.getBean(ScoringReceiptService.class);
                var gate = new CountDownLatch(1);
                var tasks = new ArrayList<Future<ScoringReceiptService.Verified>>();
                for (int n = 0; n < 6; n++) tasks.add(pool.submit(() -> { gate.await(); return service.append(input, "receipt-snapshot"); }));
                gate.countDown();
                var saved = tasks.getFirst().get(10, TimeUnit.SECONDS).stored();
                id = saved.receiptId(); originalBytes = saved.inputBytes();
                for (var task : tasks) {
                    var entry = task.get(10, TimeUnit.SECONDS).stored();
                    assertThat(entry.receiptId()).isEqualTo(id); assertThat(entry.recordedAt()).isEqualTo(saved.recordedAt());
                }
                assertThat(owner.queryForObject("SELECT COUNT(*) FROM demo_scoring_receipts", Integer.class)).isEqualTo(1);
                service.append(edit(input, "evaluationAsOf", AS_OF.plusSeconds(1)), "receipt-snapshot");
                assertThat(service.find(call.id(), id).stored().inputBytes()).isEqualTo(originalBytes);
                var revision = ScoringReceiptFixture.correction(call);
                context.getBean(AnalystCallRevisionRepository.class).saveIfAbsent(revision);
                var corrected = service.append(ScoringReceiptFixture.corrected(input, revision), "receipt-snapshot");
                assertThat(corrected.stored().basisRevisionSequence()).isEqualTo(1);
                for (var assignment : List.of("data_complete=TRUE", "data_mode='LIVE'", "receipt_scope='FULL'",
                        "recorded_at=evaluation_as_of - INTERVAL '1 second'", "snapshot_id='demo-snapshot-001'",
                        "basis_revision_id='receipt-correction',basis_revision_sequence=2,basis_revision_type='CORRECTION'",
                        "basis_revision_id='receipt-correction',basis_revision_sequence=1,basis_revision_type='CANCELLATION'",
                        "basis_revision_sequence=1", "input_bytes=decode('', 'hex')", "input_bytes=decode(repeat('00',1048577),'hex')")) {
                    assertThatThrownBy(() -> owner.update("UPDATE demo_scoring_receipts SET " + assignment + " WHERE receipt_id=?", id.toString()))
                            .isInstanceOf(DataAccessException.class);
                }
            }
            owner.execute("CREATE ROLE receipt_demo_reader LOGIN PASSWORD 'DisposableReaderOnly'");
            owner.execute("GRANT USAGE ON SCHEMA public TO receipt_demo_reader");
            owner.execute("GRANT SELECT ON ALL TABLES IN SCHEMA public TO receipt_demo_reader");
            owner.execute("CREATE ROLE receipt_demo_append LOGIN PASSWORD 'DisposableAppendOnly'");
            owner.execute("GRANT USAGE ON SCHEMA public TO receipt_demo_append");
            owner.execute("GRANT SELECT,INSERT ON demo_scoring_receipts TO receipt_demo_append");
            var appendOnly = new JdbcTemplate(new DriverManagerDataSource(db.getJdbcUrl(), "receipt_demo_append", "DisposableAppendOnly"));
            assertThat(appendOnly.queryForObject("SELECT has_table_privilege(current_user,'demo_scoring_receipts','INSERT')", Boolean.class)).isTrue();
            for (var sql : List.of("UPDATE demo_scoring_receipts SET data_complete=FALSE WHERE FALSE", "DELETE FROM demo_scoring_receipts WHERE FALSE"))
                assertThatThrownBy(() -> appendOnly.update(sql)).isInstanceOf(DataAccessException.class);
            try (var context = start(db, "receipt_demo_reader", "DisposableReaderOnly");
                 var client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).followRedirects(HttpClient.Redirect.NEVER).build()) {
                var reader = context.getBean(JdbcTemplate.class);
                assertThat(reader.queryForObject("SELECT current_user", String.class)).isEqualTo("receipt_demo_reader");
                assertThatThrownBy(() -> reader.update("DELETE FROM demo_scoring_receipts WHERE FALSE")).isInstanceOf(DataAccessException.class);
                var base = URI.create("http://127.0.0.1:" + context.getWebServer().getPort());
                var path = "/v1/calls/demo-call/scoring-receipts";
                var beforeReads = inventory(owner);
                for (var method : List.of("GET", "HEAD")) {
                    for (var route : List.of(path, path + "/" + id)) {
                        var response = send(client, base.resolve(route), method);
                        assertThat(response.statusCode()).isEqualTo(200);
                        assertThat(response.headers().firstValue("cache-control")).contains("no-store");
                        if (method.equals("HEAD")) assertThat(response.body()).isEmpty();
                        else assertThat(response.body()).contains("PARTIAL_ENDPOINT", "0.200000000000", "PERSISTED_DEMO_INPUT_REPLAY").doesNotContain("inputBytes");
                    }
                }
                assertThat(send(client, base.resolve(path), "POST").statusCode()).isEqualTo(405);
                assertThat(send(client, base.resolve("/v1/calls/demo-call-002/scoring-receipts/" + id), "GET").statusCode()).isEqualTo(404);
                assertThat(inventory(owner)).isEqualTo(beforeReads);
                owner.update("UPDATE demo_scoring_receipts SET input_bytes=? WHERE receipt_id=?", new byte[]{1,2,3}, id.toString());
                var corrupted = inventory(owner);
                for (var route : List.of(path, path + "/" + id)) {
                    var response = send(client, base.resolve(route), "GET");
                    assertThat(response.statusCode()).isEqualTo(503);
                    assertThat(response.body()).isEqualTo("{\"code\":\"SCORING_RECEIPT_UNAVAILABLE\",\"dataMode\":\"DEMO\"}");
                }
                assertThat(inventory(owner)).isEqualTo(corrupted);
                owner.update("UPDATE demo_scoring_receipts SET input_bytes=? WHERE receipt_id=?", originalBytes, id.toString());
                assertThat(send(client, base.resolve(path + "/" + id), "GET").statusCode()).isEqualTo(200);
                owner.update("UPDATE market_snapshots SET asset_price=998 WHERE call_id='demo-call'");
                assertThat(send(client, base.resolve(path + "/" + id), "GET").statusCode()).isEqualTo(503);
            }
        }
    }
    private static ServletWebServerApplicationContext start(PostgreSQLContainer<?> db, String user, String password) {
        return (ServletWebServerApplicationContext) new SpringApplicationBuilder(WallStreetReceiptsApiApplication.class).run(
                "--spring.config.location=classpath:/application.yml", "--spring.config.import=", "--spring.profiles.active=scoring-receipt-test",
                "--spring.flyway.enabled=false", "--spring.datasource.url=" + db.getJdbcUrl(), "--spring.datasource.driver-class-name=org.postgresql.Driver",
                "--spring.datasource.username=" + user, "--spring.datasource.password=" + password, "--spring.datasource.hikari.maximum-pool-size=8",
                "--app.operator-api.enabled=false", "--app.cpi.enabled=false", "--app.public-data.sec.enabled=false",
                "--app.providers.market=fixture", "--app.providers.analyst=disabled", "--server.address=127.0.0.1", "--server.port=0", "--server.shutdown=immediate");
    }
    private static HttpResponse<String> send(HttpClient client, URI uri, String method) throws Exception {
        return client.send(HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(5)).method(method, HttpRequest.BodyPublishers.noBody()).build(), HttpResponse.BodyHandlers.ofString());
    }
    private static Map<String, String> inventory(JdbcTemplate jdbc) {
        var result = new TreeMap<String, String>();
        for (var table : jdbc.queryForList("SELECT tablename FROM pg_tables WHERE schemaname='public' AND tablename<>'flyway_schema_history' ORDER BY tablename", String.class)) {
            assertThat(table).matches("[a-z_]+");
            result.put(table, jdbc.queryForObject("SELECT COALESCE(string_agg(row_to_json(t)::text, E'\\n' ORDER BY row_to_json(t)::text), '') FROM " + table + " t", String.class));
        }
        return result;
    }
}
