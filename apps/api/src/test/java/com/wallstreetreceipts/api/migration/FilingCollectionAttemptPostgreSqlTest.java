package com.wallstreetreceipts.api.migration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

import java.net.URI;
import java.sql.SQLException;
import java.sql.Timestamp;
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

import com.wallstreetreceipts.api.application.port.out.FilingCatalogCaptureAppendResult;
import com.wallstreetreceipts.api.application.port.out.FilingHistoryCollectionManifestAppendOutcome;
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
class FilingCollectionAttemptPostgreSqlTest {

    private static final Instant ROOT_CAPTURED_AT =
            Instant.parse("2026-08-25T06:00:00.123456Z");
    private static final Instant FIRST_SEGMENT_CAPTURED_AT =
            Instant.parse("2026-08-25T06:10:00.123456Z");
    private static final Instant SECOND_SEGMENT_CAPTURED_AT =
            Instant.parse("2026-08-25T06:20:00.123456Z");
    private static final Instant REQUESTED_AT =
            Instant.parse("2026-08-25T06:15:00.123456Z");
    private static final Instant ASSEMBLED_AT =
            Instant.parse("2026-08-25T06:30:00.123456Z");
    private static final Instant COMPLETED_AT =
            Instant.parse("2026-08-25T06:31:00.123456Z");

    @Container
    private static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:17-alpine");

    @Test
    void upgradesV8AndAppliesFreshV9WithFourAttemptTables() {
        String upgradeSchema = "filing_attempt_upgrade_v9";
        Flyway throughV8 = flyway(upgradeSchema, "8");
        throughV8.migrate();
        assertThat(throughV8.info().current().getVersion().getVersion())
                .isEqualTo("8");

        Flyway upgraded = flyway(upgradeSchema, null);
        upgraded.migrate();
        assertThat(upgraded.info().current().getVersion().getVersion())
                .isEqualTo("9");
        assertThat(attemptTableCount(jdbc(scopedDataSource(upgradeSchema)), upgradeSchema))
                .isEqualTo(4);

        String freshSchema = "filing_attempt_fresh_v9";
        Flyway fresh = flyway(freshSchema, null);
        fresh.migrate();
        assertThat(fresh.info().current().getVersion().getVersion())
                .isEqualTo("9");
        assertThat(attemptTableCount(jdbc(scopedDataSource(freshSchema)), freshSchema))
                .isEqualTo(4);
    }

    @Test
    void enforcesClosedPlanOutcomeAndExactArtifactForeignKeys() {
        TestContext context = migratedContext("filing_attempt_constraints");
        Evidence evidence = persistEvidence(context);
        String attemptId = "a".repeat(64);

        insertCollectAttempt(
                context.jdbc(),
                attemptId,
                "11111111-1111-4111-8111-111111111111",
                evidence.root().captureId(),
                2);
        insertAction(
                context.jdbc(),
                attemptId,
                evidence.root().captureId(),
                0,
                "SELECT_EXACT",
                evidence.firstSegment().captureId());
        insertAction(
                context.jdbc(),
                attemptId,
                evidence.root().captureId(),
                1,
                "CAPTURE_NOW",
                null);
        insertSegmentDispatch(
                context.jdbc(), attemptId, evidence.root().captureId(), 1);
        insertSuccessfulCollectionOutcome(context.jdbc(), attemptId, evidence);

        assertThat(count(context.jdbc(), "sec_filing_collection_attempts"))
                .isEqualTo(1);
        assertThat(count(
                context.jdbc(), "sec_filing_collection_attempt_descriptor_actions"))
                .isEqualTo(2);
        assertThat(count(
                context.jdbc(), "sec_filing_collection_attempt_provider_dispatches"))
                .isEqualTo(1);
        assertThat(count(context.jdbc(), "sec_filing_collection_attempt_outcomes"))
                .isEqualTo(1);
        assertNoAttemptOrphans(context.jdbc());

        Throwable invalidProviderBudget = catchThrowable(() ->
                context.jdbc().update(
                        attemptInsertSql(),
                        attemptParameters(
                                "b".repeat(64),
                                "2".repeat(64),
                                "22222222-2222-4222-8222-222222222222",
                                "CAPTURE_ROOT",
                                "0000320193",
                                null,
                                2,
                                0)));
        assertSqlState(invalidProviderBudget, "23514");

        String duplicateCaptureAttemptId = "c".repeat(64);
        insertCollectAttempt(
                context.jdbc(),
                duplicateCaptureAttemptId,
                "33333333-3333-4333-8333-333333333333",
                evidence.root().captureId(),
                2);
        insertAction(
                context.jdbc(),
                duplicateCaptureAttemptId,
                evidence.root().captureId(),
                0,
                "CAPTURE_NOW",
                null);
        Throwable secondCaptureNow = catchThrowable(() -> insertAction(
                context.jdbc(),
                duplicateCaptureAttemptId,
                evidence.root().captureId(),
                1,
                "CAPTURE_NOW",
                null));
        assertSqlState(secondCaptureNow, "23505");

        String mismatchedSegmentAttemptId = "d".repeat(64);
        insertCollectAttempt(
                context.jdbc(),
                mismatchedSegmentAttemptId,
                "44444444-4444-4444-8444-444444444444",
                evidence.root().captureId(),
                1);
        Throwable mismatchedSegment = catchThrowable(() -> insertAction(
                context.jdbc(),
                mismatchedSegmentAttemptId,
                evidence.root().captureId(),
                1,
                "SELECT_EXACT",
                evidence.firstSegment().captureId()));
        assertSqlState(mismatchedSegment, "23503");

        String invalidOutcomeAttemptId = "e".repeat(64);
        insertRootAttempt(
                context.jdbc(),
                invalidOutcomeAttemptId,
                "55555555-5555-4555-8555-555555555555");
        insertRootDispatch(context.jdbc(), invalidOutcomeAttemptId);
        Throwable invalidHttpOutcome = catchThrowable(() ->
                context.jdbc().update(
                        outcomeInsertSql(),
                        outcomeParameters(
                                invalidOutcomeAttemptId,
                                "CAPTURE_ROOT",
                                null,
                                invalidOutcomeAttemptId,
                                "FAILED_KNOWN",
                                "ROOT_CAPTURE",
                                "PROVIDER_RESPONSE_RECEIVED",
                                "PROVIDER_HTTP_STATUS",
                                200,
                                null,
                                "NOT_APPLICABLE",
                                null,
                                null,
                                "NOT_APPLICABLE",
                                null,
                                "NOT_APPLICABLE")));
        assertSqlState(invalidHttpOutcome, "23514");

        String invalidResponseDispositionAttemptId = "4".repeat(64);
        insertRootAttempt(
                context.jdbc(),
                invalidResponseDispositionAttemptId,
                "99999999-9999-4999-8999-999999999999");
        Throwable invalidResponseDisposition = catchThrowable(() ->
                context.jdbc().update(
                        outcomeInsertSql(),
                        outcomeParameters(
                                invalidResponseDispositionAttemptId,
                                "CAPTURE_ROOT",
                                null,
                                null,
                                "FAILED_KNOWN",
                                "ROOT_CAPTURE",
                                "NO_PROVIDER_INVOCATION",
                                "PROVIDER_RESPONSE_INVALID",
                                null,
                                null,
                                "NOT_APPLICABLE",
                                null,
                                null,
                                "NOT_APPLICABLE",
                                null,
                                "NOT_APPLICABLE")));
        assertSqlState(invalidResponseDisposition, "23514");

        String invalidExactEvidenceCommandAttemptId = "ab".repeat(32);
        insertRootAttempt(
                context.jdbc(),
                invalidExactEvidenceCommandAttemptId,
                "10101010-1010-4010-8010-101010101010");
        Throwable exactEvidenceOnRootCommand = catchThrowable(() ->
                context.jdbc().update(
                        outcomeInsertSql(),
                        outcomeParameters(
                                invalidExactEvidenceCommandAttemptId,
                                "CAPTURE_ROOT",
                                null,
                                null,
                                "FAILED_KNOWN",
                                "EXACT_EVIDENCE_VALIDATION",
                                "NO_PROVIDER_INVOCATION",
                                "EXACT_EVIDENCE_VALIDATION_FAILED",
                                null,
                                null,
                                "NOT_APPLICABLE",
                                null,
                                null,
                                "NOT_APPLICABLE",
                                null,
                                "NOT_APPLICABLE")));
        assertSqlState(exactEvidenceOnRootCommand, "23514");

        String invalidProviderStageAttemptId = "bc".repeat(32);
        insertRootAttempt(
                context.jdbc(),
                invalidProviderStageAttemptId,
                "20202020-2020-4020-8020-202020202020");
        insertRootDispatch(context.jdbc(), invalidProviderStageAttemptId);
        Throwable rootCommandWithSegmentProviderStage = catchThrowable(() ->
                context.jdbc().update(
                        outcomeInsertSql(),
                        outcomeParameters(
                                invalidProviderStageAttemptId,
                                "CAPTURE_ROOT",
                                null,
                                invalidProviderStageAttemptId,
                                "FAILED_KNOWN",
                                "SEGMENT_CAPTURE",
                                "PROVIDER_RESPONSE_RECEIVED",
                                "PROVIDER_RESPONSE_INVALID",
                                null,
                                null,
                                "NOT_APPLICABLE",
                                null,
                                null,
                                "NOT_APPLICABLE",
                                null,
                                "NOT_APPLICABLE")));
        assertSqlState(rootCommandWithSegmentProviderStage, "23514");

        Throwable rootCommandWithSegmentPersistenceStage = catchThrowable(() ->
                context.jdbc().update(
                        outcomeInsertSql(),
                        outcomeParameters(
                                invalidProviderStageAttemptId,
                                "CAPTURE_ROOT",
                                null,
                                invalidProviderStageAttemptId,
                                "FAILED_KNOWN",
                                "SEGMENT_CAPTURE",
                                "PROVIDER_RESPONSE_RECEIVED",
                                "SOURCE_CAPTURE_PERSISTENCE_FAILED",
                                null,
                                null,
                                "NOT_APPLICABLE",
                                null,
                                null,
                                "NOT_APPLICABLE",
                                null,
                                "NOT_APPLICABLE")));
        assertSqlState(rootCommandWithSegmentPersistenceStage, "23514");

        String invalidManifestCommandAttemptId = "cd".repeat(32);
        insertRootAttempt(
                context.jdbc(),
                invalidManifestCommandAttemptId,
                "30303030-3030-4030-8030-303030303030");
        Throwable manifestFailureOnRootCommand = catchThrowable(() ->
                context.jdbc().update(
                        outcomeInsertSql(),
                        outcomeParameters(
                                invalidManifestCommandAttemptId,
                                "CAPTURE_ROOT",
                                null,
                                null,
                                "FAILED_KNOWN",
                                "MANIFEST_ASSEMBLY",
                                "NO_PROVIDER_INVOCATION",
                                "MANIFEST_ASSEMBLY_FAILED",
                                null,
                                null,
                                "NOT_APPLICABLE",
                                null,
                                null,
                                "NOT_APPLICABLE",
                                null,
                                "NOT_APPLICABLE")));
        assertSqlState(manifestFailureOnRootCommand, "23514");

        Throwable localNoProviderFailureOnRootCommand = catchThrowable(() ->
                context.jdbc().update(
                        outcomeInsertSql(),
                        outcomeParameters(
                                invalidManifestCommandAttemptId,
                                "CAPTURE_ROOT",
                                null,
                                null,
                                "FAILED_KNOWN",
                                "LOCAL_COMMIT",
                                "NO_PROVIDER_INVOCATION",
                                "LOCAL_PERSISTENCE_FAILED",
                                null,
                                null,
                                "NOT_APPLICABLE",
                                null,
                                null,
                                "NOT_APPLICABLE",
                                null,
                                "NOT_APPLICABLE")));
        assertSqlState(localNoProviderFailureOnRootCommand, "23514");

        String mutexRejectedAttemptId = "2".repeat(64);
        insertRootAttempt(
                context.jdbc(),
                mutexRejectedAttemptId,
                "77777777-7777-4777-8777-777777777777");
        context.jdbc().update(
                outcomeInsertSql(),
                outcomeParameters(
                        mutexRejectedAttemptId,
                        "CAPTURE_ROOT",
                        null,
                        null,
                        "FAILED_KNOWN",
                        "PROVIDER_GATE",
                        "PROVIDER_INVOCATION_NOT_STARTED",
                        "PROVIDER_GATE_CLOSED",
                        null,
                        null,
                        "NOT_APPLICABLE",
                        null,
                        null,
                        "NOT_APPLICABLE",
                        null,
                        "NOT_APPLICABLE"));

        String invalidTimeAttemptId = "3".repeat(64);
        insertRootAttempt(
                context.jdbc(),
                invalidTimeAttemptId,
                "88888888-8888-4888-8888-888888888888");
        insertRootDispatch(context.jdbc(), invalidTimeAttemptId);
        MapSqlParameterSource invalidTimeParameters = outcomeParameters(
                invalidTimeAttemptId,
                "CAPTURE_ROOT",
                null,
                invalidTimeAttemptId,
                "FAILED_KNOWN",
                "ROOT_CAPTURE",
                "PROVIDER_START_OR_RESPONSE_UNKNOWN",
                "PROVIDER_REQUEST_FAILED",
                null,
                null,
                "NOT_APPLICABLE",
                null,
                null,
                "NOT_APPLICABLE",
                null,
                "NOT_APPLICABLE")
                .addValue("completedAt", Timestamp.from(REQUESTED_AT));
        Throwable completedBeforeDispatch = catchThrowable(() ->
                context.jdbc().update(outcomeInsertSql(), invalidTimeParameters));
        assertSqlState(completedBeforeDispatch, "23514");

        assertRestrictDelete(
                context,
                "DELETE FROM sec_filing_catalog_captures WHERE capture_id = ?",
                evidence.root().captureId());
        assertRestrictDelete(
                context,
                "DELETE FROM sec_historical_filing_segment_captures "
                        + "WHERE segment_capture_id = ?",
                evidence.firstSegment().captureId());
        assertRestrictDelete(
                context,
                "DELETE FROM sec_historical_filing_segment_captures "
                        + "WHERE segment_capture_id = ?",
                evidence.secondSegment().captureId());
        assertRestrictDelete(
                context,
                "DELETE FROM sec_filing_history_collection_manifests "
                        + "WHERE manifest_id = ?",
                evidence.manifest().manifestId());
    }

    @Test
    void concurrentIdenticalOperatorRequestClaimConvergesToOneAttempt() throws Exception {
        TestContext context = migratedContext("filing_attempt_concurrent_claim");
        String attemptId = "6".repeat(64);
        String operatorRequestId = "66666666-6666-4666-8666-666666666666";
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        List<Integer> insertedCounts;
        try (var executor = Executors.newFixedThreadPool(2)) {
            var left = executor.submit(() -> concurrentClaim(
                    context.dataSource(),
                    attemptId,
                    operatorRequestId,
                    ready,
                    start));
            var right = executor.submit(() -> concurrentClaim(
                    context.dataSource(),
                    attemptId,
                    operatorRequestId,
                    ready,
                    start));
            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            insertedCounts = List.of(
                    left.get(20, TimeUnit.SECONDS),
                    right.get(20, TimeUnit.SECONDS));
        }

        assertThat(insertedCounts).containsExactlyInAnyOrder(0, 1);
        assertThat(count(context.jdbc(), "sec_filing_collection_attempts"))
                .isEqualTo(1);

        Throwable reusedOperatorRequest = catchThrowable(() ->
                context.jdbc().update(
                        attemptInsertSql(),
                        attemptParameters(
                                "7".repeat(64),
                                "8".repeat(64),
                                operatorRequestId,
                                "CAPTURE_ROOT",
                                "0000320193",
                                null,
                                1,
                                0)));
        assertSqlState(reusedOperatorRequest, "23505");
    }

    @Test
    void childFailureRollsBackAttemptWithoutOrphans() {
        TestContext context = migratedContext("filing_attempt_atomic_failure");
        FilingCatalogCapture root = persistRoot(context);
        String attemptId = "9".repeat(64);

        Throwable failure = catchThrowable(() ->
                context.transactions().executeWithoutResult(status -> {
                    insertCollectAttempt(
                            context.jdbc(),
                            attemptId,
                            "99999999-9999-4999-8999-999999999999",
                            root.captureId(),
                            1);
                    insertAction(
                            context.jdbc(),
                            attemptId,
                            root.captureId(),
                            0,
                            "SELECT_EXACT",
                            "f".repeat(64));
                }));

        assertSqlState(failure, "23503");
        assertThat(count(context.jdbc(), "sec_filing_collection_attempts"))
                .isZero();
        assertThat(count(
                context.jdbc(), "sec_filing_collection_attempt_descriptor_actions"))
                .isZero();
        assertThat(count(
                context.jdbc(), "sec_filing_collection_attempt_provider_dispatches"))
                .isZero();
        assertThat(count(context.jdbc(), "sec_filing_collection_attempt_outcomes"))
                .isZero();
        assertNoAttemptOrphans(context.jdbc());
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

    private static long attemptTableCount(
            NamedParameterJdbcTemplate jdbc,
            String schema) {
        Long value = jdbc.queryForObject(
                """
                        SELECT COUNT(*)
                        FROM information_schema.tables
                        WHERE table_schema = :schema
                          AND table_name IN (
                              'sec_filing_collection_attempts',
                              'sec_filing_collection_attempt_descriptor_actions',
                              'sec_filing_collection_attempt_provider_dispatches',
                              'sec_filing_collection_attempt_outcomes'
                          )
                        """,
                new MapSqlParameterSource("schema", schema),
                Long.class);
        return value == null ? 0 : value;
    }

    private static Evidence persistEvidence(TestContext context) {
        FilingCatalogCapture root = persistRoot(context);
        HistoricalFilingSegmentCapture first = persistSegment(
                context,
                root,
                0,
                FIRST_SEGMENT_CAPTURED_AT,
                "0000320193-20-000301",
                LocalDate.parse("2020-12-28"));
        HistoricalFilingSegmentCapture second = persistSegment(
                context,
                root,
                1,
                SECOND_SEGMENT_CAPTURED_AT,
                "0000320193-14-000302",
                LocalDate.parse("2014-12-28"));
        FilingHistoryCollectionManifest pending = FilingHistoryCollectionManifest.assemble(
                root, List.of(first, second), ASSEMBLED_AT);
        FilingHistoryCollectionManifestAppendOutcome outcome = context.transactions()
                .execute(status -> context.manifestRepository().append(pending));
        assertThat(outcome).isNotNull();
        assertThat(outcome.status())
                .isEqualTo(FilingHistoryCollectionManifestAppendOutcome.Status.INSERTED);
        return new Evidence(root, first, second, outcome.manifest());
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
            String accessionNumber,
            LocalDate filingDate) {
        HistoricalFilingSegmentCapture pending =
                FilingHistoryCollectionTestFixture.segmentCapture(
                        root,
                        descriptorOrdinal,
                        capturedAt,
                        List.of(record(accessionNumber, filingDate)));
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
        return new HistoricalFilingRecord(
                accessionNumber,
                accessionNumber,
                "8-K",
                filingDate,
                null,
                filingDate.atTime(12, 0).toInstant(java.time.ZoneOffset.UTC),
                URI.create("https://www.sec.gov/Archives/edgar/data/320193/"
                        + accessionNumber.replace("-", "") + "/filing.htm"));
    }

    private static void insertRootAttempt(
            NamedParameterJdbcTemplate jdbc,
            String attemptId,
            String operatorRequestId) {
        jdbc.update(
                attemptInsertSql(),
                attemptParameters(
                        attemptId,
                        "1".repeat(64),
                        operatorRequestId,
                        "CAPTURE_ROOT",
                        "0000320193",
                        null,
                        1,
                        0));
    }

    private static void insertCollectAttempt(
            NamedParameterJdbcTemplate jdbc,
            String attemptId,
            String operatorRequestId,
            String rootCaptureId,
            int actionCount) {
        jdbc.update(
                attemptInsertSql(),
                attemptParameters(
                        attemptId,
                        "0".repeat(64),
                        operatorRequestId,
                        "COLLECT_EXACT_ROOT",
                        null,
                        rootCaptureId,
                        1,
                        actionCount));
    }

    private static String attemptInsertSql() {
        return """
                INSERT INTO sec_filing_collection_attempts (
                    attempt_id,
                    schema_version,
                    provider,
                    product,
                    policy_version,
                    command_sha256,
                    operator_request_id,
                    command_kind,
                    cik,
                    root_capture_id,
                    requested_at,
                    max_provider_invocations,
                    descriptor_action_count,
                    immutable
                ) VALUES (
                    :attemptId,
                    '1.0.0',
                    'sec-edgar',
                    'edgar-submissions-operator-collection-attempt',
                    'SEC_OPERATOR_CONTROLLED_COLLECTION_ATTEMPT_V1',
                    :commandSha256,
                    :operatorRequestId,
                    :commandKind,
                    :cik,
                    :rootCaptureId,
                    :requestedAt,
                    :maxProviderInvocations,
                    :descriptorActionCount,
                    TRUE
                )
                """;
    }

    private static MapSqlParameterSource attemptParameters(
            String attemptId,
            String commandSha256,
            String operatorRequestId,
            String commandKind,
            String cik,
            String rootCaptureId,
            int maxProviderInvocations,
            int descriptorActionCount) {
        return new MapSqlParameterSource()
                .addValue("attemptId", attemptId)
                .addValue("commandSha256", commandSha256)
                .addValue("operatorRequestId", operatorRequestId)
                .addValue("commandKind", commandKind)
                .addValue("cik", cik)
                .addValue("rootCaptureId", rootCaptureId)
                .addValue("requestedAt", Timestamp.from(REQUESTED_AT))
                .addValue("maxProviderInvocations", maxProviderInvocations)
                .addValue("descriptorActionCount", descriptorActionCount);
    }

    private static void insertAction(
            NamedParameterJdbcTemplate jdbc,
            String attemptId,
            String rootCaptureId,
            int descriptorOrdinal,
            String actionKind,
            String selectedSegmentCaptureId) {
        jdbc.update(
                """
                        INSERT INTO sec_filing_collection_attempt_descriptor_actions (
                            attempt_id,
                            command_kind,
                            root_capture_id,
                            descriptor_ordinal,
                            action_kind,
                            selected_segment_capture_id,
                            capture_now_slot
                        ) VALUES (
                            :attemptId,
                            'COLLECT_EXACT_ROOT',
                            :rootCaptureId,
                            :descriptorOrdinal,
                            :actionKind,
                            :selectedSegmentCaptureId,
                            :captureNowSlot
                        )
                        """,
                new MapSqlParameterSource()
                        .addValue("attemptId", attemptId)
                        .addValue("rootCaptureId", rootCaptureId)
                        .addValue("descriptorOrdinal", descriptorOrdinal)
                        .addValue("actionKind", actionKind)
                        .addValue("selectedSegmentCaptureId", selectedSegmentCaptureId)
                        .addValue(
                                "captureNowSlot",
                                "CAPTURE_NOW".equals(actionKind) ? 1 : null));
    }

    private static void insertRootDispatch(
            NamedParameterJdbcTemplate jdbc,
            String attemptId) {
        insertDispatch(
                jdbc,
                attemptId,
                "CAPTURE_ROOT",
                null,
                null,
                null,
                "CAPTURE_ROOT");
    }

    private static void insertSegmentDispatch(
            NamedParameterJdbcTemplate jdbc,
            String attemptId,
            String rootCaptureId,
            int descriptorOrdinal) {
        insertDispatch(
                jdbc,
                attemptId,
                "COLLECT_EXACT_ROOT",
                rootCaptureId,
                descriptorOrdinal,
                "CAPTURE_NOW",
                "CAPTURE_HISTORICAL_SEGMENT");
    }

    private static void insertDispatch(
            NamedParameterJdbcTemplate jdbc,
            String attemptId,
            String commandKind,
            String rootCaptureId,
            Integer descriptorOrdinal,
            String actionKind,
            String providerOperation) {
        jdbc.update(
                """
                        INSERT INTO sec_filing_collection_attempt_provider_dispatches (
                            attempt_id,
                            command_kind,
                            root_capture_id,
                            descriptor_ordinal,
                            action_kind,
                            provider_operation,
                            attempt_requested_at,
                            dispatched_at
                        ) VALUES (
                            :attemptId,
                            :commandKind,
                            :rootCaptureId,
                            :descriptorOrdinal,
                            :actionKind,
                            :providerOperation,
                            :attemptRequestedAt,
                            :dispatchedAt
                        )
                        """,
                new MapSqlParameterSource()
                        .addValue("attemptId", attemptId)
                        .addValue("commandKind", commandKind)
                        .addValue("rootCaptureId", rootCaptureId)
                        .addValue("descriptorOrdinal", descriptorOrdinal)
                        .addValue("actionKind", actionKind)
                        .addValue("providerOperation", providerOperation)
                        .addValue("attemptRequestedAt", Timestamp.from(REQUESTED_AT))
                        .addValue(
                                "dispatchedAt",
                                Timestamp.from(REQUESTED_AT.plusSeconds(1))));
    }

    private static void insertSuccessfulCollectionOutcome(
            NamedParameterJdbcTemplate jdbc,
            String attemptId,
            Evidence evidence) {
        jdbc.update(
                outcomeInsertSql(),
                outcomeParameters(
                        attemptId,
                        "COLLECT_EXACT_ROOT",
                        evidence.root().captureId(),
                        attemptId,
                        "SUCCEEDED",
                        "MANIFEST_ASSEMBLY",
                        "PROVIDER_RESPONSE_RECEIVED",
                        null,
                        null,
                        null,
                        "NOT_APPLICABLE",
                        evidence.secondSegment().captureId(),
                        1,
                        "INSERTED",
                        evidence.manifest().manifestId(),
                        "INSERTED"));
    }

    private static String outcomeInsertSql() {
        return """
                INSERT INTO sec_filing_collection_attempt_outcomes (
                    attempt_id,
                    command_kind,
                    root_capture_id,
                    provider_dispatch_attempt_id,
                    provider_dispatched_at,
                    terminal_status,
                    terminal_stage,
                    request_disposition,
                    failure_code,
                    http_status,
                    root_capture_artifact_id,
                    root_append_status,
                    segment_capture_artifact_id,
                    segment_descriptor_ordinal,
                    segment_append_status,
                    manifest_artifact_id,
                    manifest_append_status,
                    attempt_requested_at,
                    completed_at
                ) VALUES (
                    :attemptId,
                    :commandKind,
                    :rootCaptureId,
                    :providerDispatchAttemptId,
                    :providerDispatchedAt,
                    :terminalStatus,
                    :terminalStage,
                    :requestDisposition,
                    :failureCode,
                    :httpStatus,
                    :rootCaptureArtifactId,
                    :rootAppendStatus,
                    :segmentCaptureArtifactId,
                    :segmentDescriptorOrdinal,
                    :segmentAppendStatus,
                    :manifestArtifactId,
                    :manifestAppendStatus,
                    :attemptRequestedAt,
                    :completedAt
                )
                """;
    }

    private static MapSqlParameterSource outcomeParameters(
            String attemptId,
            String commandKind,
            String rootCaptureId,
            String providerDispatchAttemptId,
            String terminalStatus,
            String terminalStage,
            String requestDisposition,
            String failureCode,
            Integer httpStatus,
            String rootCaptureArtifactId,
            String rootAppendStatus,
            String segmentCaptureArtifactId,
            Integer segmentDescriptorOrdinal,
            String segmentAppendStatus,
            String manifestArtifactId,
            String manifestAppendStatus) {
        return new MapSqlParameterSource()
                .addValue("attemptId", attemptId)
                .addValue("commandKind", commandKind)
                .addValue("rootCaptureId", rootCaptureId)
                .addValue("providerDispatchAttemptId", providerDispatchAttemptId)
                .addValue(
                        "providerDispatchedAt",
                        providerDispatchAttemptId == null
                                ? null
                                : Timestamp.from(REQUESTED_AT.plusSeconds(1)))
                .addValue("terminalStatus", terminalStatus)
                .addValue("terminalStage", terminalStage)
                .addValue("requestDisposition", requestDisposition)
                .addValue("failureCode", failureCode)
                .addValue("httpStatus", httpStatus)
                .addValue("rootCaptureArtifactId", rootCaptureArtifactId)
                .addValue("rootAppendStatus", rootAppendStatus)
                .addValue("segmentCaptureArtifactId", segmentCaptureArtifactId)
                .addValue("segmentDescriptorOrdinal", segmentDescriptorOrdinal)
                .addValue("segmentAppendStatus", segmentAppendStatus)
                .addValue("manifestArtifactId", manifestArtifactId)
                .addValue("manifestAppendStatus", manifestAppendStatus)
                .addValue("attemptRequestedAt", Timestamp.from(REQUESTED_AT))
                .addValue("completedAt", Timestamp.from(COMPLETED_AT));
    }

    private static int concurrentClaim(
            DriverManagerDataSource dataSource,
            String attemptId,
            String operatorRequestId,
            CountDownLatch ready,
            CountDownLatch start) throws Exception {
        ready.countDown();
        if (!start.await(10, TimeUnit.SECONDS)) {
            throw new IllegalStateException("concurrent attempt claim did not start");
        }
        return jdbc(dataSource).update(
                attemptInsertSql() + " ON CONFLICT DO NOTHING",
                attemptParameters(
                        attemptId,
                        "6".repeat(64),
                        operatorRequestId,
                        "CAPTURE_ROOT",
                        "0000320193",
                        null,
                        1,
                        0));
    }

    private static void assertRestrictDelete(
            TestContext context,
            String sql,
            String identity) {
        Throwable failure = catchThrowable(() ->
                context.jdbc().getJdbcOperations().update(sql, identity));
        assertSqlState(failure, "23503");
    }

    private static long count(
            NamedParameterJdbcTemplate jdbc,
            String tableName) {
        Long value = jdbc.getJdbcOperations().queryForObject(
                "SELECT COUNT(*) FROM " + tableName,
                Long.class);
        return value == null ? 0 : value;
    }

    private static void assertNoAttemptOrphans(NamedParameterJdbcTemplate jdbc) {
        assertThat(jdbc.getJdbcOperations().queryForObject("""
                SELECT COUNT(*)
                FROM sec_filing_collection_attempt_descriptor_actions a
                LEFT JOIN sec_filing_collection_attempts h
                  ON h.attempt_id = a.attempt_id
                WHERE h.attempt_id IS NULL
                """, Long.class)).isZero();
        assertThat(jdbc.getJdbcOperations().queryForObject("""
                SELECT COUNT(*)
                FROM sec_filing_collection_attempt_provider_dispatches d
                LEFT JOIN sec_filing_collection_attempts h
                  ON h.attempt_id = d.attempt_id
                WHERE h.attempt_id IS NULL
                """, Long.class)).isZero();
        assertThat(jdbc.getJdbcOperations().queryForObject("""
                SELECT COUNT(*)
                FROM sec_filing_collection_attempt_outcomes o
                LEFT JOIN sec_filing_collection_attempts h
                  ON h.attempt_id = o.attempt_id
                LEFT JOIN sec_filing_collection_attempt_provider_dispatches d
                  ON d.attempt_id = o.provider_dispatch_attempt_id
                WHERE h.attempt_id IS NULL
                   OR (
                       o.provider_dispatch_attempt_id IS NOT NULL
                       AND d.attempt_id IS NULL
                   )
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

    private record Evidence(
            FilingCatalogCapture root,
            HistoricalFilingSegmentCapture firstSegment,
            HistoricalFilingSegmentCapture secondSegment,
            FilingHistoryCollectionManifest manifest) {
    }
}
