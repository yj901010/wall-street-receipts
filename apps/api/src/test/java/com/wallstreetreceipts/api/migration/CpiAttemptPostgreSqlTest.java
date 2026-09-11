package com.wallstreetreceipts.api.migration;

import static org.assertj.core.api.Assertions.*;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.sql.Timestamp;
import java.util.UUID;
import java.util.concurrent.Executors;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import com.wallstreetreceipts.api.application.cpi.CpiCollectionJob;
import com.wallstreetreceipts.api.domain.cpi.CpiCollectionAttempt.*;
import com.wallstreetreceipts.api.infrastructure.persistence.JdbcCpiRepository;
import com.wallstreetreceipts.api.infrastructure.provider.bls.BlsCpiClient;
import com.wallstreetreceipts.api.infrastructure.provider.bls.BlsCpiParser;
import com.wallstreetreceipts.api.support.CpiTestFixture;

/** Synthetic receipts only, in disposable Testcontainers databases; no provider requests. */
@Testcontainers
class CpiAttemptPostgreSqlTest {
    @Container static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17-alpine");
    private static final Instant NOW = Instant.parse("2026-09-08T14:00:00.123456Z");
    private DriverManagerDataSource datasource;
    private JdbcTemplate jdbc;
    private JdbcCpiRepository repo;
    private final BlsCpiParser parser = new BlsCpiParser();

    @BeforeEach void migrateIsolatedSchema() {
        var schema = "cpi_" + UUID.randomUUID().toString().replace("-", "");
        datasource = new DriverManagerDataSource(POSTGRES.getJdbcUrl() + "&currentSchema=" + schema,
                POSTGRES.getUsername(), POSTGRES.getPassword());
        Flyway.configure().dataSource(datasource).schemas(schema).defaultSchema(schema).load().migrate();
        jdbc = new JdbcTemplate(datasource);
        repo = new JdbcCpiRepository(jdbc, parser);
    }

    @Test void v10UpgradePreservesExistingReceiptsWithoutInventingAttemptHistory() {
        var schema = "upgrade_" + UUID.randomUUID().toString().replace("-", "");
        var ds = new DriverManagerDataSource(POSTGRES.getJdbcUrl() + "&currentSchema=" + schema,
                POSTGRES.getUsername(), POSTGRES.getPassword());
        Flyway.configure().dataSource(ds).schemas(schema).defaultSchema(schema).target("10").load().migrate();
        var old = new JdbcCpiRepository(new JdbcTemplate(ds), parser);
        var receipt = parser.parse(CpiTestFixture.bytes(), UUID.randomUUID(), NOW, 2023, 2026);
        old.append(receipt, CpiTestFixture.bytes(), 2023, 2026);
        old.postponeCollection(NOW.plusSeconds(86400));
        var latest = Flyway.configure().dataSource(ds).schemas(schema).defaultSchema(schema).load();
        latest.migrate();
        latest.validate();
        assertThat(latest.info().current().getVersion().getVersion()).isEqualTo("12");
        assertThat(old.latest(NOW)).contains(receipt);
        assertThat(new JdbcTemplate(ds).queryForObject("SELECT count(*) FROM bls_cpi_collection_attempts", Integer.class)).isZero();
        assertThat(old.claimCollection(NOW)).isFalse();
    }

    @Test void concurrentStartsShareGateAndCommitBeforeHttpEvenInsideRolledBackCaller() throws Exception {
        var firstId = UUID.randomUUID(); var secondId = UUID.randomUUID();
        try (var executor = Executors.newFixedThreadPool(2)) {
            var first = executor.submit(() -> repo.beginAttempt(firstId, Trigger.MANUAL, NOW));
            var second = executor.submit(() -> repo.beginAttempt(secondId, Trigger.SCHEDULED, NOW));
            assertThat(first.get() ^ second.get()).isTrue();
        }
        assertThat(count("bls_cpi_collection_attempts")).isEqualTo(2);
        assertThat(count("bls_cpi_collection_results")).isZero();
        var third = UUID.randomUUID();
        var caller = new TransactionTemplate(new DataSourceTransactionManager(datasource));
        caller.executeWithoutResult(transaction -> {
            assertThat(repo.beginAttempt(third, Trigger.MANUAL, NOW.plusSeconds(900))).isTrue();
            var independentlyRead = new JdbcCpiRepository(new JdbcTemplate(datasource), parser).findAttempt(third).orElseThrow();
            assertThat(independentlyRead.result()).isNull();
            transaction.setRollbackOnly();
        });
        assertThat(repo.findAttempt(third)).isPresent();
        assertThat(repo.claimCollection(NOW.plusSeconds(901))).isFalse();
        assertThat(repo.findAttempt(UUID.randomUUID())).isEmpty();
    }

    @Test void duplicateStartRollsBackGateExtensionAndDoesNotReplaceEvidence() {
        var id = begin();
        assertThatThrownBy(() -> repo.beginAttempt(id, Trigger.SCHEDULED, NOW.plusSeconds(900))).isInstanceOf(RuntimeException.class);
        assertThat(repo.findAttempt(id).orElseThrow().trigger()).isEqualTo(Trigger.MANUAL);
        assertThat(repo.claimCollection(NOW.plusSeconds(900))).isTrue();
    }

    @Test void missingGateNeverBecomesCooldownEvidenceOrProviderTraffic() {
        jdbc.update("DELETE FROM bls_cpi_collection_gate"); // Disposable test schema only.
        assertThatThrownBy(() -> job(NOW, (key, year, at) -> { throw new AssertionError("Missing gate reached HTTP"); }).collect())
                .hasMessage("CPI collection gate unavailable");
        assertThat(count("bls_cpi_collection_attempts")).isZero();
        assertThat(count("bls_cpi_collection_results")).isZero();
    }

    @Test void missingBackoffGateRollsBackRateLimitResultInsteadOfClaimingDurableCompletion() {
        var id = begin();
        jdbc.update("DELETE FROM bls_cpi_collection_gate"); // Disposable test schema only.
        assertThatThrownBy(() -> repo.finishAttempt(id,
                new Result(NOW, Status.RATE_LIMITED, null, null, null, NOW.plusSeconds(86400))))
                .hasMessage("CPI collection gate unavailable");
        assertThat(repo.findAttempt(id).orElseThrow().result()).isNull();
    }

    @Test void actualJobPersistsSavedSkippedRateLimitAndParseFailureAcrossRepositoryRestart() {
        var saved = job(NOW, (key, year, at) -> CpiTestFixture.bytes()).collect(Trigger.SCHEDULED).orElseThrow();
        assertThat(job(NOW.plusSeconds(1), (key, year, at) -> { throw new AssertionError("Cooldown reached HTTP"); }).collect()).isEmpty();
        assertThatThrownBy(() -> job(NOW.plusSeconds(900), (key, year, at) -> {
            throw new BlsCpiClient.RateLimited(NOW.plusSeconds(172800));
        }).collect()).isInstanceOf(BlsCpiClient.RateLimited.class);
        assertThatThrownBy(() -> job(NOW.plusSeconds(172800), (key, year, at) -> new byte[]{0}).collect())
                .hasMessage("BLS CPI response failed validation");
        var rows = jdbc.queryForList("SELECT a.attempt_id, r.status FROM bls_cpi_collection_attempts a JOIN bls_cpi_collection_results r USING (attempt_id) ORDER BY a.started_at");
        assertThat(rows).extracting(row -> row.get("status")).containsExactly("SAVED", "SKIPPED", "RATE_LIMITED", "FAILED");
        var restarted = new JdbcCpiRepository(new JdbcTemplate(datasource), parser);
        var first = restarted.findAttempt(UUID.fromString((String) rows.getFirst().get("attempt_id"))).orElseThrow();
        assertThat(first.trigger()).isEqualTo(Trigger.SCHEDULED);
        assertThat(first.result().captureId()).isEqualTo(saved.captureId());
        assertThat(first.result().capturedAt()).isEqualTo(saved.capturedAt());
        assertThat(first.startedAt()).isEqualTo(NOW);
        assertThat(restarted.latest(NOW.plusSeconds(172800))).contains(saved);
        assertThat(count("bls_cpi_captures")).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT failure_code FROM bls_cpi_collection_results WHERE status = 'FAILED'", String.class)).isEqualTo("PARSE");
    }

    @Test void incompleteStartRemainsUnknownAfterRestartAndLaterSuccessNeverCompletesIt() {
        var missing = begin(); // Simulate process loss after committed start, without fabricating a terminal event.
        var restarted = new JdbcCpiRepository(new JdbcTemplate(datasource), parser);
        assertThat(restarted.findAttempt(missing).orElseThrow().result()).isNull();
        assertThat(job(NOW.plusSeconds(900), (key, year, at) -> CpiTestFixture.bytes()).collect()).isPresent();
        assertThat(restarted.findAttempt(missing).orElseThrow().result()).isNull();
        assertThat(count("bls_cpi_collection_attempts")).isEqualTo(2);
        assertThat(count("bls_cpi_collection_results")).isEqualTo(1);
    }

    @Test void invalidTerminalRollsBackReceiptAndKeepsStartUnknown() {
        var id = begin();
        var receipt = parser.parse(CpiTestFixture.bytes(), UUID.randomUUID(), NOW, 2023, 2026);
        // Result constructor is valid, but terminal time precedes the persisted start.
        assertThatThrownBy(() -> repo.finishAttempt(id, new Result(NOW, Status.SAVED, receipt.captureId(), NOW, null, null)))
                .hasMessage("SAVED requires atomic receipt persistence");
        var before = parser.parse(CpiTestFixture.bytes(), UUID.randomUUID(), NOW.minusSeconds(1), 2023, 2026);
        assertThatThrownBy(() -> repo.saveAttempt(id, before, CpiTestFixture.bytes(), 2023, 2026, NOW.minusSeconds(1)))
                .hasMessage("Invalid CPI attempt chronology or gate result");
        assertThat(count("bls_cpi_captures")).isZero();
        assertThat(repo.findAttempt(id).orElseThrow().result()).isNull();
        repo.saveAttempt(id, receipt, CpiTestFixture.bytes(), 2023, 2026, NOW);
        var other = UUID.randomUUID();
        assertThat(repo.beginAttempt(other, Trigger.MANUAL, NOW.plusSeconds(900))).isTrue();
        assertThatThrownBy(() -> repo.saveAttempt(other, receipt, CpiTestFixture.bytes(), 2023, 2026, NOW.plusSeconds(900)))
                .isInstanceOf(RuntimeException.class);
        assertThat(repo.findAttempt(other).orElseThrow().result()).isNull();
        assertThat(count("bls_cpi_captures")).isEqualTo(1);
    }

    @Test void resultsAreSingleAssignmentAndBackoffCannotBeExtendedByRejectedDuplicate() throws Exception {
        var id = begin();
        var failed = new Result(NOW, Status.FAILED, null, null, Failure.FETCH, null);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var first = executor.submit(() -> catchThrowable(() -> repo.finishAttempt(id, failed)));
            var second = executor.submit(() -> catchThrowable(() -> repo.finishAttempt(id, failed)));
            assertThat((first.get() == null) ^ (second.get() == null)).isTrue();
        }
        assertThat(count("bls_cpi_collection_results")).isEqualTo(1);
        assertThatThrownBy(() -> repo.finishAttempt(id, new Result(NOW, Status.RATE_LIMITED, null, null, null, NOW.plusSeconds(86400))))
                .hasMessage("CPI attempt already completed");
        assertThat(repo.claimCollection(NOW.plusSeconds(900))).isTrue();
        assertThat(repo.findAttempt(id).orElseThrow().result()).isEqualTo(failed);
    }

    @Test void rateLimitResultAndBackoffCommitTogetherAndNeverShortenGate() {
        var id = begin();
        repo.postponeCollection(NOW.plusSeconds(172800));
        var limited = new Result(NOW.plusSeconds(1), Status.RATE_LIMITED, null, null, null, NOW.plusSeconds(86400));
        repo.finishAttempt(id, limited);
        assertThat(repo.findAttempt(id).orElseThrow().result()).isEqualTo(limited);
        assertThat(repo.claimCollection(NOW.plusSeconds(172799))).isFalse();
        assertThat(repo.claimCollection(NOW.plusSeconds(172800))).isTrue();
    }

    @Test void schemaRejectsMissingFailureInvalidGateChronologyAndFalseCaptureLink() {
        var id = begin();
        String sql = "INSERT INTO bls_cpi_collection_results (attempt_id, started_at, permitted, completed_at, status, capture_id, captured_at, failure_code) VALUES (?, ?, ?, ?, ?, ?, ?, ?)";
        Object[][] invalid = {
                {true, NOW, "FAILED", null, null, null},
                {true, NOW, "FAILED", null, null, "secret-free-unknown"},
                {true, NOW.minusSeconds(1), "FAILED", null, null, "FETCH"},
                {true, NOW, "SKIPPED", null, null, null},
                {false, NOW, "SKIPPED", null, null, null},
                {true, NOW, "SAVED", UUID.randomUUID().toString(), Timestamp.from(NOW), null}
        };
        for (var row : invalid) {
            assertThatThrownBy(() -> jdbc.update(sql, id.toString(), Timestamp.from(NOW), row[0], Timestamp.from((Instant) row[1]), row[2], row[3], row[4], row[5]))
                    .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
        }
        var receipt = parser.parse(CpiTestFixture.bytes(), UUID.randomUUID(), NOW, 2023, 2026);
        repo.append(receipt, CpiTestFixture.bytes(), 2023, 2026);
        assertThatThrownBy(() -> jdbc.update(sql, id.toString(), Timestamp.from(NOW), true, Timestamp.from(NOW.plusSeconds(1)), "SAVED",
                receipt.captureId().toString(), Timestamp.from(NOW.plusSeconds(1)), null))
                .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
        assertThat(count("bls_cpi_collection_results")).isZero();
    }

    private UUID begin() {
        var id = UUID.randomUUID();
        assertThat(repo.beginAttempt(id, Trigger.MANUAL, NOW)).isTrue();
        return id;
    }
    private int count(String table) { return jdbc.queryForObject("SELECT count(*) FROM " + table, Integer.class); }
    private CpiCollectionJob job(Instant at, CpiCollectionJob.Fetcher fetcher) {
        return new CpiCollectionJob(repo, parser, Clock.fixed(at, ZoneOffset.UTC), "syntheticKeyNotARealCredential", fetcher);
    }
}
