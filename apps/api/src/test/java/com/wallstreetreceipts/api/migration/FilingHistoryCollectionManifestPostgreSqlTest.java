package com.wallstreetreceipts.api.migration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowable;

import java.net.URI;
import java.sql.SQLException;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import com.wallstreetreceipts.api.application.filinghistory.SecFilingHistoryManifestAuditNotFoundException;
import com.wallstreetreceipts.api.application.filinghistory.SecFilingHistoryManifestAuditQueryService;
import com.wallstreetreceipts.api.application.port.out.FilingCatalogCaptureAppendResult;
import com.wallstreetreceipts.api.application.port.out.FilingHistoryCollectionManifestAppendOutcome;
import com.wallstreetreceipts.api.application.port.out.FilingHistoryCollectionManifestAppendOutcome.Status;
import com.wallstreetreceipts.api.application.port.out.HistoricalFilingSegmentCaptureAppendResult;
import com.wallstreetreceipts.api.domain.filing.FilingCatalogCapture;
import com.wallstreetreceipts.api.domain.filing.FilingHistoryCollectionManifest;
import com.wallstreetreceipts.api.domain.filing.HistoricalFilingRecord;
import com.wallstreetreceipts.api.domain.filing.HistoricalFilingSegmentCapture;
import com.wallstreetreceipts.api.infrastructure.persistence.JdbcFilingCatalogCaptureRepository;
import com.wallstreetreceipts.api.infrastructure.persistence.JdbcFilingHistoryCollectionManifestRepository;
import com.wallstreetreceipts.api.infrastructure.persistence.JdbcHistoricalFilingSegmentCaptureRepository;
import com.wallstreetreceipts.api.infrastructure.provider.sec.SecFilingCatalogCaptureReplayVerifier;
import com.wallstreetreceipts.api.infrastructure.provider.sec.SecHistoricalFilingSegmentCaptureReplayVerifier;
import com.wallstreetreceipts.api.support.FilingHistoryCollectionTestFixture;
import com.wallstreetreceipts.api.support.SecFilingCatalogCaptureTestFixture;

@Testcontainers(disabledWithoutDocker = true)
class FilingHistoryCollectionManifestPostgreSqlTest {

    private static final Instant ROOT_CAPTURED_AT =
            Instant.parse("2026-08-25T05:00:00.123456Z");
    private static final Instant FIRST_SEGMENT_CAPTURED_AT =
            Instant.parse("2026-08-25T05:10:00.123456Z");
    private static final Instant SECOND_SEGMENT_CAPTURED_AT =
            Instant.parse("2026-08-25T05:20:00.123456Z");
    private static final Instant FIRST_ASSEMBLED_AT =
            Instant.parse("2026-08-25T05:30:00.123456Z");

    @Container
    private static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:17-alpine");

    @Test
    void upgradesAnIsolatedV7SchemaToV8WithFourCollectionTables() {
        String schema = "history_collection_upgrade_v8";
        Flyway throughV7 = flyway(schema, "7");

        throughV7.migrate();

        assertThat(throughV7.info().current().getVersion().getVersion())
                .isEqualTo("7");

        Flyway throughV8 = flyway(schema, "8");
        throughV8.migrate();

        assertThat(throughV8.info().current().getVersion().getVersion())
                .isEqualTo("8");
        NamedParameterJdbcTemplate jdbc = jdbc(scopedDataSource(schema));
        Long collectionTableCount = jdbc.queryForObject(
                """
                        SELECT COUNT(*)
                        FROM information_schema.tables
                        WHERE table_schema = :schema
                          AND table_name IN (
                              'sec_filing_history_collection_manifests',
                              'sec_filing_history_collection_descriptors',
                              'sec_filing_history_collection_accession_groups',
                              'sec_filing_history_collection_occurrences'
                          )
                        """,
                new MapSqlParameterSource("schema", schema),
                Long.class);
        assertThat(collectionTableCount).isEqualTo(4);
    }

    @Test
    void exactAuditQueryUsesTheInclusivePostgreSqlMicrosecondVisibilityBoundary() {
        TestContext context = migratedContext("history_collection_audit_pit_read");
        FilingCatalogCapture root = persistRoot(context);
        HistoricalFilingSegmentCapture selected = persistSegment(
                context,
                root,
                0,
                FIRST_SEGMENT_CAPTURED_AT,
                List.of(record(
                        "0000320193-20-000099",
                        LocalDate.parse("2020-12-31"))));
        FilingHistoryCollectionManifest manifest = FilingHistoryCollectionManifest.assemble(
                root, List.of(selected), FIRST_ASSEMBLED_AT);
        FilingHistoryCollectionManifestAppendOutcome append = context.transactions()
                .execute(status -> context.manifestRepository().append(manifest));
        assertThat(append).isNotNull();
        assertThat(append.status()).isEqualTo(Status.INSERTED);

        SecFilingHistoryManifestAuditQueryService queryService =
                new SecFilingHistoryManifestAuditQueryService(
                        context.manifestRepository());

        assertThatThrownBy(() -> queryService.summary(
                manifest.manifestId(),
                FIRST_ASSEMBLED_AT.minusNanos(1_000).toString()))
                .isInstanceOf(SecFilingHistoryManifestAuditNotFoundException.class);
        assertThat(queryService.summary(
                manifest.manifestId(), FIRST_ASSEMBLED_AT.toString()).manifest())
                .isEqualTo(manifest);
        assertThat(queryService.summary(
                manifest.manifestId(), FIRST_ASSEMBLED_AT.plusNanos(1_000).toString())
                .manifest())
                .isEqualTo(manifest);
    }

    @Test
    void concurrentIdenticalSelectionReturnsOneWinnerObservationToBothCallers()
            throws Exception {
        TestContext context = migratedContext("history_collection_same_selection");
        FilingCatalogCapture root = persistRoot(context);
        HistoricalFilingSegmentCapture selected = persistSegment(
                context,
                root,
                0,
                FIRST_SEGMENT_CAPTURED_AT,
                List.of(record(
                        "0000320193-20-000101",
                        LocalDate.parse("2020-12-30"))));
        FilingHistoryCollectionManifest first = FilingHistoryCollectionManifest.assemble(
                root, List.of(selected), FIRST_ASSEMBLED_AT);
        FilingHistoryCollectionManifest later = FilingHistoryCollectionManifest.assemble(
                root, List.of(selected), FIRST_ASSEMBLED_AT.plusSeconds(300));

        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        List<FilingHistoryCollectionManifestAppendOutcome> outcomes;
        try (var executor = Executors.newFixedThreadPool(2)) {
            var left = executor.submit(() -> concurrentAppend(
                    context.dataSource(), first, ready, start));
            var right = executor.submit(() -> concurrentAppend(
                    context.dataSource(), later, ready, start));
            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            outcomes = List.of(
                    left.get(20, TimeUnit.SECONDS),
                    right.get(20, TimeUnit.SECONDS));
        }

        assertThat(outcomes)
                .extracting(FilingHistoryCollectionManifestAppendOutcome::status)
                .containsExactlyInAnyOrder(Status.INSERTED, Status.IDENTICAL_REPLAY);
        FilingHistoryCollectionManifest winner = outcomes.stream()
                .filter(outcome -> outcome.status() == Status.INSERTED)
                .findFirst()
                .orElseThrow()
                .manifest();
        assertThat(outcomes)
                .extracting(FilingHistoryCollectionManifestAppendOutcome::manifest)
                .containsOnly(winner);
        assertThat(winner.assembledAt())
                .isIn(first.assembledAt(), later.assembledAt());
        assertThat(context.manifestRepository().count()).isEqualTo(1);
        assertThat(count(context.jdbc(),
                "sec_filing_history_collection_descriptors")).isEqualTo(2);
        assertThat(count(context.jdbc(),
                "sec_filing_history_collection_accession_groups")).isEqualTo(3);
        assertThat(count(context.jdbc(),
                "sec_filing_history_collection_occurrences")).isEqualTo(3);
        assertNoCollectionOrphans(context.jdbc());
    }

    @Test
    void concurrentDifferentSelectionsAppendAsSeparateManifests() throws Exception {
        TestContext context = migratedContext("history_collection_distinct_selection");
        FilingCatalogCapture root = persistRoot(context);
        HistoricalFilingSegmentCapture firstSelected = persistSegment(
                context,
                root,
                0,
                FIRST_SEGMENT_CAPTURED_AT,
                List.of(record(
                        "0000320193-20-000201",
                        LocalDate.parse("2020-12-29"))));
        HistoricalFilingSegmentCapture secondSelected = persistSegment(
                context,
                root,
                1,
                SECOND_SEGMENT_CAPTURED_AT,
                List.of(record(
                        "0000320193-14-000202",
                        LocalDate.parse("2014-12-29"))));
        FilingHistoryCollectionManifest firstSelection =
                FilingHistoryCollectionManifest.assemble(
                        root, List.of(firstSelected), FIRST_ASSEMBLED_AT);
        FilingHistoryCollectionManifest secondSelection =
                FilingHistoryCollectionManifest.assemble(
                        root, List.of(secondSelected), FIRST_ASSEMBLED_AT);

        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        List<FilingHistoryCollectionManifestAppendOutcome> outcomes;
        try (var executor = Executors.newFixedThreadPool(2)) {
            var left = executor.submit(() -> concurrentAppend(
                    context.dataSource(), firstSelection, ready, start));
            var right = executor.submit(() -> concurrentAppend(
                    context.dataSource(), secondSelection, ready, start));
            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            outcomes = List.of(
                    left.get(20, TimeUnit.SECONDS),
                    right.get(20, TimeUnit.SECONDS));
        }

        assertThat(outcomes)
                .extracting(FilingHistoryCollectionManifestAppendOutcome::status)
                .containsOnly(Status.INSERTED);
        assertThat(outcomes)
                .extracting(outcome -> outcome.manifest().manifestId())
                .doesNotHaveDuplicates();
        assertThat(context.manifestRepository().count()).isEqualTo(2);
        assertThat(count(context.jdbc(),
                "sec_filing_history_collection_descriptors")).isEqualTo(4);
        assertThat(count(context.jdbc(),
                "sec_filing_history_collection_accession_groups")).isEqualTo(6);
        assertThat(count(context.jdbc(),
                "sec_filing_history_collection_occurrences")).isEqualTo(6);
        assertNoCollectionOrphans(context.jdbc());
    }

    @Test
    void childConstraintFailureRollsBackManifestAndExactSourceDeletesAreRestricted() {
        TestContext context = migratedContext("history_collection_atomic_failure");
        FilingCatalogCapture root = persistRoot(context);
        HistoricalFilingSegmentCapture selected = persistSegment(
                context,
                root,
                0,
                FIRST_SEGMENT_CAPTURED_AT,
                List.of());
        FilingHistoryCollectionManifest manifest = FilingHistoryCollectionManifest.assemble(
                root, List.of(selected), FIRST_ASSEMBLED_AT);
        context.jdbc().getJdbcOperations().execute("""
                ALTER TABLE sec_filing_history_collection_descriptors
                ADD CONSTRAINT ck_forced_collection_child_failure
                CHECK (descriptor_ordinal < 0)
                """);

        Throwable appendFailure = catchThrowable(() ->
                context.transactions().executeWithoutResult(
                        status -> context.manifestRepository().append(manifest)));

        assertSqlState(appendFailure, "23514");
        assertThat(context.manifestRepository().count()).isZero();
        assertThat(count(context.jdbc(),
                "sec_filing_history_collection_descriptors")).isZero();
        assertThat(count(context.jdbc(),
                "sec_filing_history_collection_accession_groups")).isZero();
        assertThat(count(context.jdbc(),
                "sec_filing_history_collection_occurrences")).isZero();
        assertNoCollectionOrphans(context.jdbc());

        context.jdbc().getJdbcOperations().execute("""
                ALTER TABLE sec_filing_history_collection_descriptors
                DROP CONSTRAINT ck_forced_collection_child_failure
                """);
        FilingHistoryCollectionManifestAppendOutcome inserted = context.transactions()
                .execute(status -> context.manifestRepository().append(manifest));
        assertThat(inserted).isNotNull();
        assertThat(inserted.status()).isEqualTo(Status.INSERTED);

        Throwable segmentDeleteFailure = catchThrowable(() ->
                context.jdbc().getJdbcOperations().update(
                        """
                                DELETE FROM sec_historical_filing_segment_captures
                                WHERE segment_capture_id = ?
                                """,
                        selected.captureId()));
        Throwable rootDeleteFailure = catchThrowable(() ->
                context.jdbc().getJdbcOperations().update(
                        """
                                DELETE FROM sec_filing_catalog_captures
                                WHERE capture_id = ?
                                """,
                        root.captureId()));
        assertSqlState(segmentDeleteFailure, "23503");
        assertSqlState(rootDeleteFailure, "23503");
        assertThat(context.manifestRepository().findByManifestId(manifest.manifestId()))
                .contains(inserted.manifest());
    }

    private static Flyway flyway(String schema, String target) {
        var configuration = Flyway.configure()
                .dataSource(
                        POSTGRES.getJdbcUrl(),
                        POSTGRES.getUsername(),
                        POSTGRES.getPassword())
                .schemas(schema)
                .defaultSchema(schema)
                .locations("classpath:db/migration");
        if (target != null) {
            configuration.target(target);
        }
        return configuration.load();
    }

    private static TestContext migratedContext(String schema) {
        Flyway latest = flyway(schema, null);
        latest.migrate();
        assertThat(latest.info().current().getVersion().getVersion())
                .isEqualTo("9");
        DriverManagerDataSource dataSource = scopedDataSource(schema);
        NamedParameterJdbcTemplate jdbc = jdbc(dataSource);
        JdbcFilingCatalogCaptureRepository rootRepository =
                new JdbcFilingCatalogCaptureRepository(
                        jdbc, new SecFilingCatalogCaptureReplayVerifier());
        JdbcHistoricalFilingSegmentCaptureRepository segmentRepository =
                new JdbcHistoricalFilingSegmentCaptureRepository(
                        jdbc,
                        new SecHistoricalFilingSegmentCaptureReplayVerifier(),
                        rootRepository);
        JdbcFilingHistoryCollectionManifestRepository manifestRepository =
                new JdbcFilingHistoryCollectionManifestRepository(
                        jdbc, rootRepository, segmentRepository);
        return new TestContext(
                dataSource,
                jdbc,
                new TransactionTemplate(new DataSourceTransactionManager(dataSource)),
                rootRepository,
                segmentRepository,
                manifestRepository);
    }

    private static DriverManagerDataSource scopedDataSource(String schema) {
        String separator = POSTGRES.getJdbcUrl().contains("?") ? "&" : "?";
        return new DriverManagerDataSource(
                POSTGRES.getJdbcUrl() + separator + "currentSchema=" + schema,
                POSTGRES.getUsername(),
                POSTGRES.getPassword());
    }

    private static NamedParameterJdbcTemplate jdbc(DriverManagerDataSource dataSource) {
        return new NamedParameterJdbcTemplate(dataSource);
    }

    private static FilingCatalogCapture persistRoot(TestContext context) {
        FilingCatalogCapture pending =
                SecFilingCatalogCaptureTestFixture.capture(ROOT_CAPTURED_AT);
        FilingCatalogCaptureAppendResult result = context.transactions()
                .execute(status -> context.rootRepository().append(pending));
        assertThat(result).isEqualTo(FilingCatalogCaptureAppendResult.INSERTED);
        return context.rootRepository().findByCaptureId(pending.captureId())
                .orElseThrow();
    }

    private static HistoricalFilingSegmentCapture persistSegment(
            TestContext context,
            FilingCatalogCapture root,
            int descriptorOrdinal,
            Instant capturedAt,
            List<HistoricalFilingRecord> filings) {
        HistoricalFilingSegmentCapture pending =
                FilingHistoryCollectionTestFixture.segmentCapture(
                        root, descriptorOrdinal, capturedAt, filings);
        HistoricalFilingSegmentCaptureAppendResult result = context.transactions()
                .execute(status -> context.segmentRepository().append(pending));
        assertThat(result)
                .isEqualTo(HistoricalFilingSegmentCaptureAppendResult.INSERTED);
        return context.segmentRepository().findByCaptureId(pending.captureId())
                .orElseThrow();
    }

    private static HistoricalFilingRecord record(
            String accessionNumber,
            LocalDate filingDate) {
        String accessionPath = accessionNumber.replace("-", "");
        return new HistoricalFilingRecord(
                accessionNumber,
                accessionNumber,
                "8-K",
                filingDate,
                null,
                filingDate.atTime(12, 0)
                        .toInstant(java.time.ZoneOffset.UTC),
                URI.create("https://www.sec.gov/Archives/edgar/data/320193/"
                        + accessionPath + "/filing.htm"));
    }

    private static FilingHistoryCollectionManifestAppendOutcome concurrentAppend(
            DriverManagerDataSource dataSource,
            FilingHistoryCollectionManifest manifest,
            CountDownLatch ready,
            CountDownLatch start) throws Exception {
        NamedParameterJdbcTemplate jdbc = jdbc(dataSource);
        JdbcFilingCatalogCaptureRepository rootRepository =
                new JdbcFilingCatalogCaptureRepository(
                        jdbc, new SecFilingCatalogCaptureReplayVerifier());
        JdbcHistoricalFilingSegmentCaptureRepository segmentRepository =
                new JdbcHistoricalFilingSegmentCaptureRepository(
                        jdbc,
                        new SecHistoricalFilingSegmentCaptureReplayVerifier(),
                        rootRepository);
        JdbcFilingHistoryCollectionManifestRepository manifestRepository =
                new JdbcFilingHistoryCollectionManifestRepository(
                        jdbc, rootRepository, segmentRepository);
        TransactionTemplate transactions = new TransactionTemplate(
                new DataSourceTransactionManager(dataSource));
        ready.countDown();
        if (!start.await(10, TimeUnit.SECONDS)) {
            throw new IllegalStateException(
                    "concurrent collection append did not start");
        }
        FilingHistoryCollectionManifestAppendOutcome outcome = transactions.execute(
                status -> manifestRepository.append(manifest));
        if (outcome == null) {
            throw new IllegalStateException(
                    "concurrent collection append returned no outcome");
        }
        return outcome;
    }

    private static long count(
            NamedParameterJdbcTemplate jdbc,
            String tableName) {
        Long count = jdbc.getJdbcOperations().queryForObject(
                "SELECT COUNT(*) FROM " + tableName,
                Long.class);
        return count == null ? 0 : count;
    }

    private static void assertNoCollectionOrphans(
            NamedParameterJdbcTemplate jdbc) {
        assertThat(jdbc.getJdbcOperations().queryForObject("""
                SELECT COUNT(*)
                FROM sec_filing_history_collection_descriptors d
                LEFT JOIN sec_filing_history_collection_manifests m
                  ON m.manifest_id = d.manifest_id
                WHERE m.manifest_id IS NULL
                """, Long.class)).isZero();
        assertThat(jdbc.getJdbcOperations().queryForObject("""
                SELECT COUNT(*)
                FROM sec_filing_history_collection_accession_groups g
                LEFT JOIN sec_filing_history_collection_manifests m
                  ON m.manifest_id = g.manifest_id
                WHERE m.manifest_id IS NULL
                """, Long.class)).isZero();
        assertThat(jdbc.getJdbcOperations().queryForObject("""
                SELECT COUNT(*)
                FROM sec_filing_history_collection_occurrences o
                LEFT JOIN sec_filing_history_collection_manifests m
                  ON m.manifest_id = o.manifest_id
                LEFT JOIN sec_filing_history_collection_accession_groups g
                  ON g.manifest_id = o.manifest_id
                 AND g.accession_number = o.accession_number
                WHERE m.manifest_id IS NULL
                   OR g.manifest_id IS NULL
                """, Long.class)).isZero();
    }

    private static void assertSqlState(Throwable failure, String expected) {
        assertThat(failure).isNotNull();
        Throwable current = failure;
        while (current.getCause() != null) {
            current = current.getCause();
        }
        assertThat(current)
                .isInstanceOfSatisfying(
                        SQLException.class,
                        sqlException -> assertThat(sqlException.getSQLState())
                                .isEqualTo(expected));
    }

    private record TestContext(
            DriverManagerDataSource dataSource,
            NamedParameterJdbcTemplate jdbc,
            TransactionTemplate transactions,
            JdbcFilingCatalogCaptureRepository rootRepository,
            JdbcHistoricalFilingSegmentCaptureRepository segmentRepository,
            JdbcFilingHistoryCollectionManifestRepository manifestRepository) {
    }
}
