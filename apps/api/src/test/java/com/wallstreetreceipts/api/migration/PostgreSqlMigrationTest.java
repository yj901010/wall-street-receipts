package com.wallstreetreceipts.api.migration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowable;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.net.URI;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wallstreetreceipts.api.application.port.out.AnalystCallDataSet;
import com.wallstreetreceipts.api.application.port.out.CallContextDataSet;
import com.wallstreetreceipts.api.application.port.out.FilingCatalogCaptureAppendResult;
import com.wallstreetreceipts.api.application.port.out.HistoricalFilingSegmentCaptureAppendResult;
import com.wallstreetreceipts.api.domain.context.EventContext;
import com.wallstreetreceipts.api.domain.call.AnalystCall;
import com.wallstreetreceipts.api.domain.call.AnalystCallRevision;
import com.wallstreetreceipts.api.domain.market.DataMode;
import com.wallstreetreceipts.api.domain.market.MarketSnapshot;
import com.wallstreetreceipts.api.domain.outcome.CallOutcome;
import com.wallstreetreceipts.api.domain.outcome.OutcomeEvaluationStatus;
import com.wallstreetreceipts.api.domain.outcome.OutcomeHorizon;
import com.wallstreetreceipts.api.domain.outcome.OutcomeReasonCode;
import com.wallstreetreceipts.api.infrastructure.persistence.JdbcCallOutcomeRepository;
import com.wallstreetreceipts.api.infrastructure.persistence.JdbcCallContextRepository;
import com.wallstreetreceipts.api.infrastructure.persistence.JdbcAnalystCallRepository;
import com.wallstreetreceipts.api.infrastructure.persistence.JdbcAnalystCallRevisionRepository;
import com.wallstreetreceipts.api.infrastructure.persistence.JdbcFilingCatalogCaptureRepository;
import com.wallstreetreceipts.api.infrastructure.persistence.JdbcHistoricalFilingSegmentCaptureRepository;
import com.wallstreetreceipts.api.infrastructure.persistence.JdbcScoringMethodologyRepository;
import com.wallstreetreceipts.api.infrastructure.provider.fixture.FixtureAnalystCallProvider;
import com.wallstreetreceipts.api.infrastructure.provider.sec.SecFilingCatalogCaptureReplayVerifier;
import com.wallstreetreceipts.api.infrastructure.provider.sec.SecHistoricalFilingSegmentCaptureReplayVerifier;
import com.wallstreetreceipts.api.domain.source.SourceDocument;
import com.wallstreetreceipts.api.domain.source.SourceReference;
import com.wallstreetreceipts.api.domain.source.SourceType;
import com.wallstreetreceipts.api.support.SecFilingCatalogCaptureTestFixture;
import com.wallstreetreceipts.api.support.SecHistoricalFilingSegmentCaptureTestFixture;

@Testcontainers(disabledWithoutDocker = true)
class PostgreSqlMigrationTest {

    @Container
    private static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:17-alpine");

    @Test
    void migrationsAndP1ConstraintsApplyOnPostgreSql() throws Exception {
        Flyway flyway = Flyway.configure()
                .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .locations("classpath:db/migration")
                .load();

        flyway.migrate();

        assertThat(flyway.info().applied()).hasSize(9);

        DriverManagerDataSource dataSource = new DriverManagerDataSource(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
        NamedParameterJdbcTemplate jdbc = new NamedParameterJdbcTemplate(dataSource);
        JdbcAnalystCallRepository repository = new JdbcAnalystCallRepository(jdbc);
        JdbcAnalystCallRevisionRepository revisionRepository = new JdbcAnalystCallRevisionRepository(jdbc);
        JdbcScoringMethodologyRepository methodologyRepository = new JdbcScoringMethodologyRepository(jdbc);
        JdbcCallOutcomeRepository outcomeRepository = new JdbcCallOutcomeRepository(jdbc, methodologyRepository);
        JdbcCallContextRepository contextRepository = new JdbcCallContextRepository(jdbc);
        FixtureAnalystCallProvider provider = new FixtureAnalystCallProvider(new ObjectMapper());
        DataSourceTransactionManager transactionManager = new DataSourceTransactionManager(dataSource);
        TransactionTemplate transactions = new TransactionTemplate(transactionManager);
        var dataSet = provider.load();

        var priorCallIds = List.of("demo-call-001", "demo-call-002");
        var priorDataSet = new AnalystCallDataSet(
                dataSet.institutions(), dataSet.analysts(), dataSet.assets(),
                dataSet.calls().stream().filter(call -> priorCallIds.contains(call.id())).toList(),
                List.of(),
                dataSet.snapshots().stream().filter(snapshot -> priorCallIds.contains(snapshot.callId())).toList(),
                List.of(), List.of(), CallContextDataSet.empty());
        Integer importedPriorCalls = transactions.execute(status -> repository.importDataSet(priorDataSet));
        var callOneBeforeAppend = repository.findById("demo-call-001").orElseThrow();
        Integer appendedCalls = transactions.execute(status -> repository.importDataSet(dataSet));
        var callOneAfterAppend = repository.findById("demo-call-001").orElseThrow();
        Integer importedContexts = transactions.execute(
                status -> contextRepository.importDataSet(dataSet.contexts()));
        Integer importedRevisions = transactions.execute(status -> revisionRepository.importAll(dataSet.revisions()));
        Integer importedMethodologies = transactions.execute(
                status -> methodologyRepository.importAll(dataSet.methodologies()));
        Integer importedOutcomes = transactions.execute(status -> outcomeRepository.importAll(dataSet.outcomes()));
        Integer duplicateCalls = transactions.execute(status -> repository.importDataSet(dataSet));
        Integer duplicateRevisions = transactions.execute(status -> revisionRepository.importAll(dataSet.revisions()));
        Integer duplicateMethodologies = transactions.execute(
                status -> methodologyRepository.importAll(dataSet.methodologies()));
        Integer duplicateOutcomes = transactions.execute(status -> outcomeRepository.importAll(dataSet.outcomes()));
        Integer duplicateContexts = transactions.execute(
                status -> contextRepository.importDataSet(dataSet.contexts()));
        assertThat(importedPriorCalls).isEqualTo(2);
        assertThat(appendedCalls).isEqualTo(1);
        assertThat(callOneAfterAppend).isEqualTo(callOneBeforeAppend);
        assertThat(importedRevisions).isEqualTo(2);
        assertThat(importedMethodologies).isEqualTo(2);
        assertThat(importedOutcomes).isEqualTo(4);
        assertThat(importedContexts).isEqualTo(9);
        assertThat(duplicateCalls).isZero();
        assertThat(duplicateRevisions).isZero();
        assertThat(duplicateMethodologies).isZero();
        assertThat(duplicateOutcomes).isZero();
        assertThat(duplicateContexts).isZero();
        assertThat(repository.count()).isEqualTo(3);
        assertThat(revisionRepository.count()).isEqualTo(2);
        assertThat(methodologyRepository.count()).isEqualTo(2);
        assertThat(outcomeRepository.count()).isEqualTo(4);
        assertThat(contextRepository.observationCount()).isEqualTo(7);
        assertThat(contextRepository.macroSnapshotCount()).isEqualTo(1);
        assertThat(contextRepository.eventContextCount()).isEqualTo(1);
        assertThat(contextRepository.findMacroSnapshotByCallId("demo-call-001").orElseThrow().observations())
                .hasSize(6)
                .noneMatch(observation -> observation.macroObservationId()
                        .equals("macro-observation-demo-cpi-revision-001"));
        assertThat(contextRepository.findObservationById("macro-observation-demo-cpi-revision-001"))
                .isPresent();
        assertThat(revisionRepository.findByCallId("demo-call-002"))
                .extracting(revision -> revision.sequenceNumber())
                .containsExactly(1, 2);
        assertThat(outcomeRepository.findByCallId("demo-call-001"))
                .extracting(CallOutcome::outcomeId)
                .containsExactly(
                        "outcome-demo-call-001-d1-v1-001",
                        "outcome-demo-call-001-d1-v1-002",
                        "outcome-demo-call-001-d1-v2-001",
                        "outcome-demo-call-001-m1-v1-001");

        var nullableSourceReference = repository.findById("demo-call-003")
                .orElseThrow()
                .call()
                .sourceReference();
        var nullableSourceDocument = nullableSourceReference.document();
        assertThat(nullableSourceDocument.publisher()).isNull();
        assertThat(nullableSourceDocument.canonicalUrl()).isNull();
        assertThat(nullableSourceDocument.publishedAt()).isNull();
        assertThat(nullableSourceDocument.externalId()).isNull();
        assertThat(nullableSourceDocument.contentHash()).isNull();
        assertThat(nullableSourceDocument.id()).isEqualTo("source-demo-article-003");
        assertThat(nullableSourceDocument.title()).isEqualTo("DEMO unattributed neutral outlook");
        assertThat(nullableSourceDocument.provider()).isEqualTo("fixture");
        assertThat(nullableSourceDocument.licenseClass()).isEqualTo("INTERNAL_DEMO");
        assertThat(nullableSourceDocument.dataMode()).isEqualTo(DataMode.DEMO);
        assertThat(nullableSourceDocument.capturedAt())
                .isEqualTo(java.time.Instant.parse("2026-08-10T10:02:00Z"));
        assertThat(nullableSourceDocument.provenanceId()).isEqualTo("fixture-analyst-calls-v1");
        assertThat(nullableSourceReference.id()).isEqualTo("source-ref-demo-003");
        assertThat(nullableSourceReference.dataMode()).isEqualTo(DataMode.DEMO);
        assertThat(nullableSourceReference.capturedAt())
                .isEqualTo(java.time.Instant.parse("2026-08-10T10:02:00Z"));
        assertThat(nullableSourceReference.provenanceId()).isEqualTo("fixture-analyst-calls-v1");

        AnalystCallRevision template = dataSet.revisions().getFirst();
        AnalystCallRevision first = new AnalystCallRevision(
                "rollback-revision-001", template.schemaVersion(), "demo-call-001", null, 1,
                template.provider(), "rollback-revision-event-001", template.type(), template.eventTime(),
                template.processingTime(), template.correctedTerms(), "Rollback transaction evidence",
                template.sourceReference(), template.dataMode(), template.capturedAt(), template.provenanceId());
        AnalystCallRevision invalidGap = new AnalystCallRevision(
                "rollback-revision-003", template.schemaVersion(), "demo-call-001", first.id(), 3,
                template.provider(), "rollback-revision-event-003", template.type(), template.eventTime(),
                template.processingTime(), template.correctedTerms(), "Rejected transaction gap",
                template.sourceReference(), template.dataMode(), template.capturedAt(), template.provenanceId());
        assertThatThrownBy(() -> transactions.execute(
                status -> revisionRepository.importAll(List.of(first, invalidGap))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("latest lineage");

        CallOutcome latestOutcome = dataSet.outcomes().stream()
                .filter(outcome -> outcome.outcomeId().equals("outcome-demo-call-001-d1-v1-002"))
                .findFirst()
                .orElseThrow();
        CallOutcome appendedOutcome = copyOutcome(
                latestOutcome, "rollback-outcome-003", "7".repeat(64), 3, latestOutcome.outcomeId(), 60);
        CallOutcome invalidOutcomeGap = copyOutcome(
                latestOutcome, "rollback-outcome-005", "8".repeat(64), 5, appendedOutcome.outcomeId(), 120);
        assertThatThrownBy(() -> transactions.execute(
                status -> outcomeRepository.importAll(List.of(appendedOutcome, invalidOutcomeGap))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("latest result");

        CallOutcome concurrentReplay = copyOutcome(
                latestOutcome, "concurrent-outcome-003", "9".repeat(64), 3, latestOutcome.outcomeId(), 180);
        var ready = new CountDownLatch(2);
        var start = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var firstReplay = executor.submit(() -> {
                ready.countDown();
                start.await();
                return new TransactionTemplate(transactionManager).execute(
                        status -> outcomeRepository.saveIfAbsent(concurrentReplay));
            });
            var secondReplay = executor.submit(() -> {
                ready.countDown();
                start.await();
                return new TransactionTemplate(transactionManager).execute(
                        status -> outcomeRepository.saveIfAbsent(concurrentReplay));
            });
            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            assertThat(List.of(
                    firstReplay.get(10, TimeUnit.SECONDS),
                    secondReplay.get(10, TimeUnit.SECONDS)))
                    .containsExactlyInAnyOrder(true, false);
        }

        CallOutcome v1Template = dataSet.outcomes().getFirst();
        CallOutcome v2Template = dataSet.outcomes().stream()
                .filter(outcome -> outcome.methodologyVersion().equals("2.0.0"))
                .findFirst()
                .orElseThrow();
        CallOutcome v1Collision = pendingOutcome(
                v1Template, "cross-method-outcome-id", "demo-call-001",
                "market-snapshot-demo-001", null, "c".repeat(64), OutcomeHorizon.M6);
        CallOutcome v2Collision = pendingOutcome(
                v2Template, "cross-method-outcome-id", "demo-call-001",
                "market-snapshot-demo-001", null, "d".repeat(64), OutcomeHorizon.M6);
        var collisionReady = new CountDownLatch(2);
        var collisionStart = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var firstCollision = executor.submit(() -> concurrentSaveResult(
                    outcomeRepository, transactionManager, v1Collision, collisionReady, collisionStart));
            var secondCollision = executor.submit(() -> concurrentSaveResult(
                    outcomeRepository, transactionManager, v2Collision, collisionReady, collisionStart));
            assertThat(collisionReady.await(10, TimeUnit.SECONDS)).isTrue();
            collisionStart.countDown();
            assertThat(List.of(
                    firstCollision.get(10, TimeUnit.SECONDS),
                    secondCollision.get(10, TimeUnit.SECONDS)))
                    .anyMatch("inserted:true"::equals)
                    .anyMatch(result -> result.startsWith("conflict:conflicting call outcome outcomeId"));
        }

        AnalystCall callTemplate = dataSet.calls().getFirst();
        MarketSnapshot snapshotTemplate = dataSet.snapshots().stream()
                .filter(snapshot -> snapshot.callId().equals(callTemplate.id()))
                .findFirst()
                .orElseThrow();
        String boundaryCallId = "c".repeat(128);
        String boundarySnapshotId = "s".repeat(128);
        AnalystCall boundaryCall = copyCall(
                callTemplate, boundaryCallId, "postgres-boundary-call-event");
        MarketSnapshot boundarySnapshot = copySnapshot(
                snapshotTemplate, boundarySnapshotId, boundaryCallId);
        Boolean boundaryCallInserted = transactions.execute(
                status -> repository.saveIfAbsent(boundaryCall, boundarySnapshot));
        assertThat(boundaryCallInserted).isTrue();

        String boundaryDocumentId = "d".repeat(128);
        String boundaryReferenceId = "r".repeat(128);
        String boundaryContextId = "e".repeat(128);
        Instant boundaryEvidenceTime = boundaryCall.eventTime().minusSeconds(1);
        SourceDocument boundaryDocument = new SourceDocument(
                boundaryDocumentId, SourceType.ARTICLE, "PostgreSQL boundary publisher",
                "PostgreSQL 128-character context evidence", URI.create("https://example.invalid/context-boundary"),
                boundaryEvidenceTime, "postgres-context-test", boundaryDocumentId, null,
                "INTERNAL_DEMO", DataMode.DEMO, boundaryEvidenceTime, "postgres-context-test");
        SourceReference boundaryReference = new SourceReference(
                boundaryReferenceId, boundaryDocument, null, null, null, null, null, false,
                DataMode.DEMO, boundaryEvidenceTime, "postgres-context-test");
        EventContext boundaryContext = new EventContext(
                "1.0.0", boundaryContextId, boundaryCall.id(), boundaryCall.eventTime(),
                boundaryCall.processingTime(), null, null, null, null, null, boundaryReferenceId,
                DataMode.DEMO, boundaryCall.capturedAt(), "postgres-context-test");
        Integer boundaryContextInserted = transactions.execute(status -> contextRepository.importDataSet(
                new CallContextDataSet(
                        List.of(boundaryDocument), List.of(boundaryReference), List.of(), List.of(),
                        List.of(boundaryContext), List.of())));
        assertThat(boundaryContextInserted).isEqualTo(1);
        assertThat(contextRepository.findEventContextByCallId(boundaryCall.id()).orElseThrow().sourceReferenceId())
                .hasSize(128);

        AnalystCall callTwo = repository.findById("demo-call-002").orElseThrow().call();
        EventContext concurrentContext = new EventContext(
                "1.0.0", "event-context-concurrent-replay-002", callTwo.id(), callTwo.eventTime(),
                callTwo.processingTime(), null, null, null, null, null,
                "source-ref-demo-event-calendar-001", DataMode.DEMO, callTwo.capturedAt(),
                "postgres-context-test");
        CallContextDataSet concurrentDataSet = new CallContextDataSet(
                List.of(), List.of(), List.of(), List.of(), List.of(concurrentContext), List.of());
        var contextReady = new CountDownLatch(2);
        var contextStart = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var firstContext = executor.submit(() -> concurrentContextImport(
                    contextRepository, transactionManager, concurrentDataSet, contextReady, contextStart));
            var secondContext = executor.submit(() -> concurrentContextImport(
                    contextRepository, transactionManager, concurrentDataSet, contextReady, contextStart));
            assertThat(contextReady.await(10, TimeUnit.SECONDS)).isTrue();
            contextStart.countDown();
            assertThat(List.of(
                    firstContext.get(10, TimeUnit.SECONDS), secondContext.get(10, TimeUnit.SECONDS)))
                    .containsExactlyInAnyOrder("imported:1", "imported:0");
        }

        AnalystCall callThree = repository.findById("demo-call-003").orElseThrow().call();
        EventContext competingContextOne = new EventContext(
                "1.0.0", "event-context-concurrent-a-003", callThree.id(), callThree.eventTime(),
                callThree.processingTime(), null, null, null, null, null,
                "source-ref-demo-event-calendar-001", DataMode.DEMO, callThree.capturedAt(),
                "postgres-context-test");
        EventContext competingContextTwo = new EventContext(
                "1.0.0", "event-context-concurrent-b-003", callThree.id(), callThree.eventTime(),
                callThree.processingTime(), null, null, null, null, null,
                "source-ref-demo-event-calendar-001", DataMode.DEMO, callThree.capturedAt(),
                "postgres-context-test");
        var competingReady = new CountDownLatch(2);
        var competingStart = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var firstContext = executor.submit(() -> concurrentContextImport(
                    contextRepository, transactionManager,
                    new CallContextDataSet(
                            List.of(), List.of(), List.of(), List.of(), List.of(competingContextOne), List.of()),
                    competingReady, competingStart));
            var secondContext = executor.submit(() -> concurrentContextImport(
                    contextRepository, transactionManager,
                    new CallContextDataSet(
                            List.of(), List.of(), List.of(), List.of(), List.of(competingContextTwo), List.of()),
                    competingReady, competingStart));
            assertThat(competingReady.await(10, TimeUnit.SECONDS)).isTrue();
            competingStart.countDown();
            assertThat(List.of(
                    firstContext.get(10, TimeUnit.SECONDS), secondContext.get(10, TimeUnit.SECONDS)))
                    .anyMatch("imported:1"::equals)
                    .anyMatch(result -> result.startsWith("conflict:conflicting callId"));
        }

        CallOutcome boundaryOutcome = pendingOutcome(
                dataSet.outcomes().getFirst(), "postgres-boundary-outcome", boundaryCallId,
                boundarySnapshotId, null, "a".repeat(64), OutcomeHorizon.Y1);
        Boolean boundaryOutcomeInserted = transactions.execute(
                status -> outcomeRepository.saveIfAbsent(boundaryOutcome));
        assertThat(boundaryOutcomeInserted).isTrue();

        CallOutcome excludedOutcome = excludedOutcome(
                dataSet.outcomes().getFirst(), "postgres-excluded-outcome", "demo-call-002",
                "market-snapshot-demo-002", "demo-call-revision-002", "b".repeat(64));
        Boolean excludedOutcomeInserted = transactions.execute(
                status -> outcomeRepository.saveIfAbsent(excludedOutcome));
        assertThat(excludedOutcomeInserted).isTrue();

        try (Connection connection = POSTGRES.createConnection("")) {
            assertSqlRejected(connection, """
                    INSERT INTO macro_snapshots (
                        macro_snapshot_id, schema_version, call_id, event_time, event_date,
                        processing_time, immutable, data_mode, captured_at, provenance_id
                    ) VALUES (
                        'raw-fake-utc-date', '1.0.0', '%s', '2026-08-10T12:00:00Z', '2026-08-11',
                        '2026-08-10T12:03:00Z', TRUE, 'DEMO', '2026-08-10T12:03:00Z', 'raw-test'
                    )
                    """.formatted(boundaryCallId));
            assertPointInTimeObservationRejected(connection, boundaryCallId);
            assertThat(query(connection, """
                    SELECT COUNT(*)::text FROM provider_event_identities
                    WHERE provider_event_id IN (
                        'rollback-revision-event-001', 'rollback-revision-event-003'
                    )
                    """)).isEqualTo("0");
            assertThat(query(connection, """
                    SELECT COUNT(*)::text FROM analyst_call_revisions
                    WHERE revision_id IN ('rollback-revision-001', 'rollback-revision-003')
                    """)).isEqualTo("0");
            assertThat(query(connection, """
                    SELECT COUNT(*)::text FROM call_outcomes
                    WHERE outcome_id IN ('rollback-outcome-003', 'rollback-outcome-005')
                    """)).isEqualTo("0");
            assertThat(query(connection,
                    "SELECT metadata_value FROM platform_metadata WHERE metadata_key = 'schema_baseline'"))
                    .isEqualTo("P0");
            assertThat(query(connection, """
                    SELECT COUNT(*)::text
                    FROM information_schema.table_constraints
                    WHERE table_schema = current_schema()
                      AND table_name = 'analyst_calls'
                      AND constraint_name = 'uq_analyst_calls_provider_event'
                      AND constraint_type = 'UNIQUE'
                    """)).isEqualTo("1");
            assertThat(query(connection, """
                    SELECT COUNT(*)::text
                    FROM information_schema.check_constraints
                    WHERE constraint_schema = current_schema()
                      AND constraint_name = 'ck_market_snapshots_immutable'
                    """)).isEqualTo("1");
            assertThat(query(connection, """
                    SELECT is_nullable
                    FROM information_schema.columns
                    WHERE table_schema = current_schema()
                      AND table_name = 'market_snapshots' AND column_name = 'asset_price'
                    """)).isEqualTo("YES");
            assertThat(query(connection, """
                    SELECT COUNT(*)::text
                    FROM information_schema.table_constraints
                    WHERE table_schema = current_schema()
                      AND table_name = 'analyst_call_revisions'
                      AND constraint_name = 'uq_call_revisions_provider_event'
                      AND constraint_type = 'UNIQUE'
                    """)).isEqualTo("1");
            assertThat(query(connection, """
                    SELECT COUNT(*)::text
                    FROM information_schema.table_constraints
                    WHERE table_schema = current_schema()
                      AND table_name = 'analyst_call_revisions'
                      AND constraint_name = 'uq_call_revisions_sequence'
                      AND constraint_type = 'UNIQUE'
                    """)).isEqualTo("1");
            assertThat(query(connection, """
                    SELECT COUNT(*)::text
                    FROM information_schema.check_constraints
                    WHERE constraint_schema = current_schema()
                      AND constraint_name IN (
                        'ck_call_revisions_lineage_root',
                        'ck_call_revisions_payload',
                        'ck_call_revisions_capture_time'
                    )
                    """)).isEqualTo("3");
            assertThat(query(connection, """
                    SELECT status FROM analyst_calls WHERE call_id = 'demo-call-002'
                    """)).isEqualTo("ACTIVE");
            assertThat(query(connection, "SELECT COUNT(*)::text FROM provider_event_identities"))
                    .isEqualTo("6");
            assertThat(query(connection, """
                    SELECT COUNT(*)::text
                    FROM information_schema.table_constraints
                    WHERE table_schema = current_schema()
                      AND table_name = 'call_outcomes'
                      AND constraint_name IN (
                        'uq_call_outcomes_natural_identity',
                        'uq_call_outcomes_lineage_sequence',
                        'fk_call_outcomes_supersedes',
                        'fk_call_outcomes_basis_revision',
                        'fk_call_outcomes_cancellation_revision',
                        'fk_call_outcomes_snapshot',
                        'fk_call_outcomes_methodology'
                      )
                    """)).isEqualTo("7");
            assertThat(query(connection, """
                    SELECT COUNT(*)::text
                    FROM information_schema.check_constraints
                    WHERE constraint_schema = current_schema()
                      AND constraint_name IN (
                        'ck_call_outcomes_basis',
                        'ck_call_outcomes_sequence',
                        'ck_call_outcomes_evaluation',
                        'ck_call_outcomes_cancellation_evidence',
                        'ck_call_outcomes_excluded_metrics',
                        'ck_call_outcomes_target_error',
                        'ck_call_outcomes_time',
                        'ck_call_outcomes_capture_time'
                      )
                    """)).isEqualTo("8");
            assertThat(query(connection, """
                    SELECT COUNT(*)::text
                    FROM information_schema.check_constraints
                    WHERE constraint_schema = current_schema()
                      AND constraint_name IN (
                        'ck_analyst_calls_capture_time',
                        'ck_market_snapshots_capture_time'
                      )
                    """)).isEqualTo("2");
            assertThat(query(connection, """
                    SELECT COUNT(*)::text
                    FROM scoring_methodologies
                    WHERE methodology_id = 'standard-call-outcome'
                    """)).isEqualTo("2");
            assertThat(query(connection, """
                    SELECT COUNT(*)::text
                    FROM call_outcomes
                    WHERE asset_return IS NULL
                      AND benchmark_return IS NULL
                      AND sector_return IS NULL
                      AND alpha IS NULL
                      AND sector_alpha IS NULL
                      AND mfe IS NULL
                      AND mae IS NULL
                      AND target_hit IS NULL
                      AND directional_win IS NULL
                      AND target_error IS NULL
                      AND data_complete = FALSE
                      AND outcome_id IN (
                          'outcome-demo-call-001-d1-v1-001',
                          'outcome-demo-call-001-d1-v1-002',
                          'outcome-demo-call-001-m1-v1-001',
                          'outcome-demo-call-001-d1-v2-001'
                      )
                    """)).isEqualTo("4");
            assertThat(query(connection, """
                    SELECT COUNT(*)::text FROM call_outcomes
                    WHERE outcome_id = 'concurrent-outcome-003'
                    """)).isEqualTo("1");
            assertThat(query(connection, """
                    SELECT LENGTH(call_id)::text || ':' || LENGTH(snapshot_id)::text
                    FROM call_outcomes WHERE outcome_id = 'postgres-boundary-outcome'
                    """)).isEqualTo("128:128");
            assertThat(query(connection, """
                    SELECT cancellation_revision_id || ':'
                        || cancellation_revision_sequence_number::text || ':'
                        || cancellation_revision_type
                    FROM call_outcomes WHERE outcome_id = 'postgres-excluded-outcome'
                    """)).isEqualTo("demo-call-revision-002:2:CANCELLATION");
            assertSqlRejected(connection, """
                    INSERT INTO provider_event_identities (
                        provider, provider_event_id, event_kind, canonical_event_id
                    ) VALUES (
                        'fixture', 'fixture-call-001', 'ANALYST_CALL_REVISION', 'raw-collision'
                    )
                    """);
            assertSqlRejected(connection, """
                    INSERT INTO call_outcomes (
                        outcome_id, schema_version, call_id, horizon, basis_key, snapshot_id,
                        methodology_id, methodology_version, methodology_definition_hash,
                        input_fingerprint, sequence_number, evaluation_status, reason_code,
                        event_time, processing_time, data_complete, data_mode, captured_at, provenance_id
                    ) VALUES (
                        'raw-outcome-bad-state', '1.0.0', 'demo-call-001', 'W1',
                        'ORIGINAL:demo-call-001', 'market-snapshot-demo-001',
                        'standard-call-outcome', '1.0.0',
                        '03af803fd61c21b86e1897d006e6cf4f92f28ce627b06eda13b319ebfa8a07e2',
                        repeat('a', 64), 1, 'PENDING', 'HORIZON_DATA_MISSING',
                        '2026-08-18T00:00:00Z', '2026-08-18T00:01:00Z', FALSE,
                        'DEMO', '2026-08-18T00:01:00Z', 'raw-test'
                    )
                    """);
            assertSqlRejected(connection, """
                    INSERT INTO call_outcomes (
                        outcome_id, schema_version, call_id, horizon, basis_key, snapshot_id,
                        methodology_id, methodology_version, methodology_definition_hash,
                        input_fingerprint, sequence_number, evaluation_status, reason_code,
                        event_time, processing_time, target_error, data_complete,
                        data_mode, captured_at, provenance_id
                    ) VALUES (
                        'raw-outcome-negative-target-error', '1.0.0', 'demo-call-001', 'W1',
                        'ORIGINAL:demo-call-001', 'market-snapshot-demo-001',
                        'standard-call-outcome', '2.0.0',
                        '256056d7cb2b292a1ec0bd7b905f856134bb38851a65b8a2fceaca41489db3e8',
                        repeat('b', 64), 1, 'CALCULATED', NULL,
                        '2026-08-18T00:00:00Z', '2026-08-18T00:01:00Z', -0.01, TRUE,
                        'DEMO', '2026-08-18T00:01:00Z', 'raw-test'
                    )
                    """);
            assertSqlRejected(connection, """
                    INSERT INTO call_outcomes (
                        outcome_id, schema_version, call_id, horizon, basis_key, snapshot_id,
                        methodology_id, methodology_version, methodology_definition_hash,
                        input_fingerprint, sequence_number, evaluation_status, reason_code,
                        event_time, processing_time, data_complete, data_mode, captured_at, provenance_id
                    ) VALUES (
                        'raw-outcome-cross-snapshot', '1.0.0', 'demo-call-002', 'W1',
                        'ORIGINAL:demo-call-002', 'market-snapshot-demo-001',
                        'standard-call-outcome', '1.0.0',
                        '03af803fd61c21b86e1897d006e6cf4f92f28ce627b06eda13b319ebfa8a07e2',
                        repeat('c', 64), 1, 'PENDING', 'HORIZON_NOT_REACHED',
                        '2026-08-18T00:00:00Z', '2026-08-18T00:01:00Z', FALSE,
                        'DEMO', '2026-08-18T00:01:00Z', 'raw-test'
                    )
                    """);
            assertSqlRejected(connection, """
                    INSERT INTO call_outcomes (
                        outcome_id, schema_version, call_id, horizon,
                        basis_revision_id, basis_key, basis_revision_sequence_number, basis_revision_type,
                        snapshot_id, methodology_id, methodology_version, methodology_definition_hash,
                        input_fingerprint, sequence_number, evaluation_status, reason_code,
                        event_time, processing_time, data_complete, data_mode, captured_at, provenance_id
                    ) VALUES (
                        'raw-outcome-cancellation-basis', '1.0.0', 'demo-call-002', 'W1',
                        'demo-call-revision-002', 'REVISION:demo-call-revision-002', 2, 'CANCELLATION',
                        'market-snapshot-demo-002', 'standard-call-outcome', '1.0.0',
                        '03af803fd61c21b86e1897d006e6cf4f92f28ce627b06eda13b319ebfa8a07e2',
                        repeat('d', 64), 1, 'PENDING', 'HORIZON_NOT_REACHED',
                        '2026-08-18T00:00:00Z', '2026-08-18T00:01:00Z', FALSE,
                        'DEMO', '2026-08-18T00:01:00Z', 'raw-test'
                    )
                    """);
            assertSqlRejected(connection, """
                    INSERT INTO call_outcomes (
                        outcome_id, schema_version, call_id, horizon, basis_key, snapshot_id,
                        methodology_id, methodology_version, methodology_definition_hash,
                        input_fingerprint, sequence_number, supersedes_outcome_id,
                        supersedes_sequence_number, evaluation_status, reason_code,
                        event_time, processing_time, data_complete, data_mode, captured_at, provenance_id
                    ) VALUES (
                        'raw-outcome-lineage-gap', '1.0.0', 'demo-call-001', 'D1',
                        'ORIGINAL:demo-call-001', 'market-snapshot-demo-001',
                        'standard-call-outcome', '1.0.0',
                        '03af803fd61c21b86e1897d006e6cf4f92f28ce627b06eda13b319ebfa8a07e2',
                        repeat('e', 64), 4, 'outcome-demo-call-001-d1-v1-002', 3,
                        'INCOMPLETE', 'HORIZON_DATA_MISSING',
                        '2026-08-11T20:00:00Z', '2026-08-18T00:01:00Z', FALSE,
                        'DEMO', '2026-08-18T00:01:00Z', 'raw-test'
                    )
                    """);
            assertSqlRejected(connection, """
                    INSERT INTO call_outcomes (
                        outcome_id, schema_version, call_id, horizon, basis_key, snapshot_id,
                        methodology_id, methodology_version, methodology_definition_hash,
                        input_fingerprint, sequence_number, evaluation_status, reason_code,
                        event_time, processing_time, data_complete, data_mode, captured_at, provenance_id
                    ) VALUES (
                        'raw-excluded-without-cancellation', '1.0.0', 'demo-call-001', 'W1',
                        'ORIGINAL:demo-call-001', 'market-snapshot-demo-001',
                        'standard-call-outcome', '1.0.0',
                        '03af803fd61c21b86e1897d006e6cf4f92f28ce627b06eda13b319ebfa8a07e2',
                        repeat('f', 64), 1, 'EXCLUDED', 'CALL_CANCELLED',
                        '2026-08-18T00:00:00Z', '2026-08-18T00:01:00Z', FALSE,
                        'DEMO', '2026-08-18T00:01:00Z', 'raw-test'
                    )
                    """);
            assertSqlRejected(connection, """
                    INSERT INTO call_outcomes (
                        outcome_id, schema_version, call_id, horizon, basis_key,
                        cancellation_revision_id, cancellation_revision_sequence_number,
                        cancellation_revision_type, snapshot_id,
                        methodology_id, methodology_version, methodology_definition_hash,
                        input_fingerprint, sequence_number, evaluation_status, reason_code,
                        event_time, processing_time, data_complete, data_mode, captured_at, provenance_id
                    ) VALUES (
                        'raw-pending-with-cancellation', '1.0.0', 'demo-call-002', 'W1',
                        'ORIGINAL:demo-call-002', 'demo-call-revision-002', 2, 'CANCELLATION',
                        'market-snapshot-demo-002', 'standard-call-outcome', '1.0.0',
                        '03af803fd61c21b86e1897d006e6cf4f92f28ce627b06eda13b319ebfa8a07e2',
                        repeat('f', 64), 1, 'PENDING', 'HORIZON_NOT_REACHED',
                        '2026-08-18T00:00:00Z', '2026-08-18T00:01:00Z', FALSE,
                        'DEMO', '2026-08-18T00:01:00Z', 'raw-test'
                    )
                    """);
            assertSqlRejected(connection, """
                    INSERT INTO call_outcomes (
                        outcome_id, schema_version, call_id, horizon, basis_key,
                        cancellation_revision_id, cancellation_revision_sequence_number,
                        cancellation_revision_type, snapshot_id,
                        methodology_id, methodology_version, methodology_definition_hash,
                        input_fingerprint, sequence_number, evaluation_status, reason_code,
                        event_time, processing_time, data_complete, data_mode, captured_at, provenance_id
                    ) VALUES (
                        'raw-excluded-with-correction', '1.0.0', 'demo-call-002', 'W1',
                        'ORIGINAL:demo-call-002', 'demo-call-revision-001', 1, 'CORRECTION',
                        'market-snapshot-demo-002', 'standard-call-outcome', '1.0.0',
                        '03af803fd61c21b86e1897d006e6cf4f92f28ce627b06eda13b319ebfa8a07e2',
                        repeat('f', 64), 1, 'EXCLUDED', 'CALL_CANCELLED',
                        '2026-08-18T00:00:00Z', '2026-08-18T00:01:00Z', FALSE,
                        'DEMO', '2026-08-18T00:01:00Z', 'raw-test'
                    )
                    """);
            assertSqlRejected(connection, """
                    INSERT INTO call_outcomes (
                        outcome_id, schema_version, call_id, horizon, basis_key,
                        cancellation_revision_id, cancellation_revision_sequence_number,
                        cancellation_revision_type, snapshot_id,
                        methodology_id, methodology_version, methodology_definition_hash,
                        input_fingerprint, sequence_number, evaluation_status, reason_code,
                        event_time, processing_time, data_complete, data_mode, captured_at, provenance_id
                    ) VALUES (
                        'raw-excluded-cross-call', '1.0.0', 'demo-call-001', 'W1',
                        'ORIGINAL:demo-call-001', 'demo-call-revision-002', 2, 'CANCELLATION',
                        'market-snapshot-demo-001', 'standard-call-outcome', '1.0.0',
                        '03af803fd61c21b86e1897d006e6cf4f92f28ce627b06eda13b319ebfa8a07e2',
                        repeat('f', 64), 1, 'EXCLUDED', 'CALL_CANCELLED',
                        '2026-08-18T00:00:00Z', '2026-08-18T00:01:00Z', FALSE,
                        'DEMO', '2026-08-18T00:01:00Z', 'raw-test'
                    )
                    """);
            assertSqlRejected(connection, """
                    UPDATE analyst_calls
                    SET processing_time = '2026-08-20T00:00:00Z',
                        captured_at = '2026-08-19T00:00:00Z'
                    WHERE call_id = 'demo-call-001'
                    """);
            assertSqlRejected(connection, """
                    UPDATE market_snapshots
                    SET processing_time = '2026-08-20T00:00:00Z',
                        captured_at = '2026-08-19T00:00:00Z'
                    WHERE snapshot_id = 'market-snapshot-demo-001'
                    """);
            assertGapRejected(connection);
            assertCrossCallParentRejected(connection);
            assertRevisionRejected(connection, "raw-null-parent-event", "raw-null-parent-revision", """
                    INSERT INTO analyst_call_revisions (
                        revision_id, schema_version, call_id, supersedes_revision_id, sequence_number,
                        provider, provider_event_id, revision_type, event_time, processing_time,
                        corrected_direction, corrected_original_rating, corrected_previous_target,
                        corrected_target, corrected_currency, reason, source_reference_id,
                        data_mode, captured_at, provenance_id
                    ) VALUES (
                        'raw-null-parent-revision', '1.0.0', 'demo-call-002', 'missing-parent', 3,
                        'fixture', 'raw-null-parent-event', 'CORRECTION',
                        '2026-08-11T16:00:00Z', '2026-08-11T16:01:00Z',
                        'BULLISH', 'Raw null parent', 210, 230, 'USD', 'Rejected null parent',
                        'source-ref-demo-002', 'DEMO', '2026-08-11T16:02:00Z', 'raw-test'
                    )
                    """);
            assertRevisionRejected(connection, "raw-terminal-event", "raw-terminal-revision", """
                    INSERT INTO analyst_call_revisions (
                        revision_id, schema_version, call_id, supersedes_revision_id,
                        supersedes_sequence_number, supersedes_revision_type, sequence_number,
                        provider, provider_event_id, revision_type, event_time, processing_time,
                        corrected_direction, corrected_original_rating, corrected_previous_target,
                        corrected_target, corrected_currency, reason, source_reference_id,
                        data_mode, captured_at, provenance_id
                    ) VALUES (
                        'raw-terminal-revision', '1.0.0', 'demo-call-002', 'demo-call-revision-002',
                        2, 'CORRECTION', 3,
                        'fixture', 'raw-terminal-event', 'CANCELLATION',
                        '2026-08-11T16:00:00Z', '2026-08-11T16:01:00Z',
                        NULL, NULL, NULL, NULL, NULL, 'Rejected terminal append',
                        'source-ref-demo-002', 'DEMO', '2026-08-11T16:02:00Z', 'raw-test'
                    )
                    """);
            assertRevisionRejected(connection, "raw-currency-event", "raw-currency-revision", """
                    INSERT INTO analyst_call_revisions (
                        revision_id, schema_version, call_id, sequence_number,
                        provider, provider_event_id, revision_type, event_time, processing_time,
                        corrected_direction, corrected_original_rating, corrected_previous_target,
                        corrected_target, corrected_currency, reason, source_reference_id,
                        data_mode, captured_at, provenance_id
                    ) VALUES (
                        'raw-currency-revision', '1.0.0', 'demo-call-001', 1,
                        'fixture', 'raw-currency-event', 'CORRECTION',
                        '2026-08-11T16:00:00Z', '2026-08-11T16:01:00Z',
                        'BULLISH', 'Raw currency', 7800, 8000, '123', 'Rejected currency',
                        'source-ref-demo-001', 'DEMO', '2026-08-11T16:02:00Z', 'raw-test'
                    )
                    """);
        }
    }

    @Test
    void v3BackfillsProviderIdentityForExistingV2Calls() throws Exception {
        String schema = "upgrade_path";
        Flyway v2 = Flyway.configure()
                .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .schemas(schema)
                .defaultSchema(schema)
                .target("2")
                .locations("classpath:db/migration")
                .load();
        v2.migrate();

        try (Connection connection = POSTGRES.createConnection("");
                Statement statement = connection.createStatement()) {
            statement.execute("SET search_path TO " + schema);
            statement.executeUpdate("""
                    INSERT INTO institutions (
                        institution_id, canonical_name, slug, country, active, data_mode,
                        effective_at, captured_at, provenance_id
                    ) VALUES (
                        'upgrade-inst', 'Upgrade Institution', 'upgrade-institution', 'US', TRUE, 'DEMO',
                        '2026-08-01T00:00:00Z', '2026-08-01T00:00:00Z', 'upgrade-test'
                    )
                    """);
            statement.executeUpdate("""
                    INSERT INTO assets (
                        asset_id, asset_type, canonical_name, ticker, primary_currency, active,
                        data_mode, effective_at, captured_at, provenance_id
                    ) VALUES (
                        'upgrade-asset', 'EQUITY', 'Upgrade Asset', 'UPGD', 'USD', TRUE,
                        'DEMO', '2026-08-01T00:00:00Z', '2026-08-01T00:00:00Z', 'upgrade-test'
                    )
                    """);
            statement.executeUpdate("""
                    INSERT INTO source_documents (
                        source_document_id, source_type, title, provider, external_id,
                        license_class, data_mode, captured_at, provenance_id
                    ) VALUES (
                        'upgrade-document', 'ARTICLE', 'Upgrade evidence', 'fixture', 'upgrade-source',
                        'INTERNAL_DEMO', 'DEMO', '2026-08-01T00:00:00Z', 'upgrade-test'
                    )
                    """);
            statement.executeUpdate("""
                    INSERT INTO source_references (
                        source_reference_id, source_document_id, verified, data_mode,
                        captured_at, provenance_id
                    ) VALUES (
                        'upgrade-reference', 'upgrade-document', FALSE, 'DEMO',
                        '2026-08-01T00:00:00Z', 'upgrade-test'
                    )
                    """);
            statement.executeUpdate("""
                    INSERT INTO analyst_calls (
                        call_id, provider, provider_event_id, institution_id, asset_id,
                        event_time, processing_time, direction, source_reference_id,
                        status, data_mode, captured_at, provenance_id
                    ) VALUES (
                        'upgrade-call', 'fixture', 'upgrade-call-event', 'upgrade-inst', 'upgrade-asset',
                        '2026-08-01T00:00:00Z', '2026-08-01T00:01:00Z', 'NEUTRAL', 'upgrade-reference',
                        'ACTIVE', 'DEMO', '2026-08-01T00:01:00Z', 'upgrade-test'
                    )
                    """);
        }

        Flyway latest = Flyway.configure()
                .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .schemas(schema)
                .defaultSchema(schema)
                .locations("classpath:db/migration")
                .load();
        latest.migrate();

        try (Connection connection = POSTGRES.createConnection("");
                Statement statement = connection.createStatement()) {
            statement.execute("SET search_path TO " + schema);
            assertThat(query(connection, """
                    SELECT event_kind || ':' || canonical_event_id
                    FROM provider_event_identities
                    WHERE provider = 'fixture' AND provider_event_id = 'upgrade-call-event'
                    """)).isEqualTo("ANALYST_CALL:upgrade-call");
            assertThat(query(connection, """
                    SELECT provider_event_kind FROM analyst_calls WHERE call_id = 'upgrade-call'
                    """)).isEqualTo("ANALYST_CALL");
        }
    }

    @Test
    void v4UpgradesAPopulatedV3SchemaAndItsCompositeReferencesRemainUsable() throws Exception {
        String schema = "outcome_upgrade_path";
        Flyway v3 = Flyway.configure()
                .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .schemas(schema)
                .defaultSchema(schema)
                .target("3")
                .locations("classpath:db/migration")
                .load();
        v3.migrate();

        try (Connection connection = POSTGRES.createConnection("");
                Statement statement = connection.createStatement()) {
            statement.execute("SET search_path TO " + schema);
            statement.executeUpdate("""
                    INSERT INTO institutions (
                        institution_id, canonical_name, slug, country, active, data_mode,
                        effective_at, captured_at, provenance_id
                    ) VALUES (
                        'v3-inst', 'V3 Institution', 'v3-institution', 'US', TRUE, 'DEMO',
                        '2026-08-01T00:00:00Z', '2026-08-01T00:00:00Z', 'v3-upgrade-test'
                    )
                    """);
            statement.executeUpdate("""
                    INSERT INTO assets (
                        asset_id, asset_type, canonical_name, ticker, primary_currency, active,
                        data_mode, effective_at, captured_at, provenance_id
                    ) VALUES (
                        'v3-asset', 'EQUITY', 'V3 Asset', 'V3UP', 'USD', TRUE,
                        'DEMO', '2026-08-01T00:00:00Z', '2026-08-01T00:00:00Z', 'v3-upgrade-test'
                    )
                    """);
            statement.executeUpdate("""
                    INSERT INTO source_documents (
                        source_document_id, source_type, title, provider, external_id,
                        license_class, data_mode, captured_at, provenance_id
                    ) VALUES (
                        'v3-document', 'ARTICLE', 'V3 evidence', 'fixture', 'v3-source',
                        'INTERNAL_DEMO', 'DEMO', '2026-08-01T00:00:00Z', 'v3-upgrade-test'
                    )
                    """);
            statement.executeUpdate("""
                    INSERT INTO source_references (
                        source_reference_id, source_document_id, verified, data_mode,
                        captured_at, provenance_id
                    ) VALUES (
                        'v3-reference', 'v3-document', FALSE, 'DEMO',
                        '2026-08-01T00:00:00Z', 'v3-upgrade-test'
                    )
                    """);
            statement.executeUpdate("""
                    INSERT INTO provider_event_identities (
                        provider, provider_event_id, event_kind, canonical_event_id
                    ) VALUES
                        ('fixture', 'v3-call-event', 'ANALYST_CALL', 'v3-call'),
                        ('fixture', 'v3-revision-event', 'ANALYST_CALL_REVISION', 'v3-revision')
                    """);
            statement.executeUpdate("""
                    INSERT INTO analyst_calls (
                        call_id, provider, provider_event_id, institution_id, asset_id,
                        event_time, processing_time, direction, source_reference_id,
                        status, data_mode, captured_at, provenance_id
                    ) VALUES (
                        'v3-call', 'fixture', 'v3-call-event', 'v3-inst', 'v3-asset',
                        '2026-08-01T00:00:00Z', '2026-08-01T00:01:00Z', 'NEUTRAL', 'v3-reference',
                        'ACTIVE', 'DEMO', '2026-08-01T00:01:00Z', 'v3-upgrade-test'
                    )
                    """);
            statement.executeUpdate("""
                    INSERT INTO market_snapshots (
                        snapshot_id, call_id, asset_id, event_time, processing_time,
                        asset_price, data_mode, captured_at, provenance_id
                    ) VALUES (
                        'v3-snapshot', 'v3-call', 'v3-asset',
                        '2026-08-01T00:00:00Z', '2026-08-01T00:01:00Z',
                        100, 'DEMO', '2026-08-01T00:01:00Z', 'v3-upgrade-test'
                    )
                    """);
            statement.executeUpdate("""
                    INSERT INTO analyst_call_revisions (
                        revision_id, schema_version, call_id, sequence_number,
                        provider, provider_event_id, revision_type, event_time, processing_time,
                        corrected_direction, reason, source_reference_id,
                        data_mode, captured_at, provenance_id
                    ) VALUES (
                        'v3-revision', '1.0.0', 'v3-call', 1,
                        'fixture', 'v3-revision-event', 'CORRECTION',
                        '2026-08-01T00:02:00Z', '2026-08-01T00:03:00Z',
                        'BULLISH', 'V3 populated lineage', 'v3-reference',
                        'DEMO', '2026-08-01T00:03:00Z', 'v3-upgrade-test'
                    )
                    """);
        }

        Flyway latest = Flyway.configure()
                .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .schemas(schema)
                .defaultSchema(schema)
                .locations("classpath:db/migration")
                .load();
        latest.migrate();
        assertThat(latest.info().current().getVersion().getVersion()).isEqualTo("9");

        try (Connection connection = POSTGRES.createConnection("");
                Statement statement = connection.createStatement()) {
            statement.execute("SET search_path TO " + schema);
            assertThat(query(connection, "SELECT COUNT(*)::text FROM analyst_calls WHERE call_id = 'v3-call'"))
                    .isEqualTo("1");
            assertThat(query(connection, "SELECT COUNT(*)::text FROM market_snapshots WHERE snapshot_id = 'v3-snapshot'"))
                    .isEqualTo("1");
            assertThat(query(connection, "SELECT COUNT(*)::text FROM analyst_call_revisions WHERE revision_id = 'v3-revision'"))
                    .isEqualTo("1");
            assertThat(query(connection, """
                    SELECT COUNT(*)::text FROM information_schema.tables
                    WHERE table_schema = current_schema()
                      AND table_name IN ('scoring_methodologies', 'call_outcomes')
                    """)).isEqualTo("2");
            assertThat(query(connection, """
                    SELECT COUNT(*)::text FROM information_schema.table_constraints
                    WHERE table_schema = current_schema()
                      AND constraint_name IN (
                        'uq_market_snapshots_call_snapshot',
                        'fk_call_outcomes_basis_revision',
                        'fk_call_outcomes_snapshot',
                        'fk_call_outcomes_methodology'
                      )
                    """)).isEqualTo("4");
            assertThat(query(connection, """
                    SELECT character_maximum_length::text
                    FROM information_schema.columns
                    WHERE table_schema = current_schema()
                      AND table_name = 'analyst_calls' AND column_name = 'call_id'
                    """)).isEqualTo("128");
            assertThat(query(connection, """
                    SELECT COUNT(*)::text
                    FROM information_schema.columns
                    WHERE table_schema = current_schema()
                      AND character_maximum_length = 128
                      AND (table_name, column_name) IN (
                        ('market_snapshots', 'snapshot_id'),
                        ('market_snapshots', 'call_id'),
                        ('analyst_call_revisions', 'call_id'),
                        ('call_outcomes', 'call_id'),
                        ('call_outcomes', 'snapshot_id')
                      )
                    """)).isEqualTo("5");

            statement.executeUpdate("""
                    INSERT INTO scoring_methodologies (
                        methodology_id, methodology_version, schema_version, definition_hash,
                        status, effective_at, data_mode, captured_at, provenance_id
                    ) VALUES (
                        'v3-methodology', '1.0.0', '1.0.0', repeat('f', 64),
                        'MODEL_ONLY', '2026-08-01T00:00:00Z', 'DEMO',
                        '2026-08-01T00:00:00Z', 'v3-upgrade-test'
                    )
                    """);
            statement.executeUpdate("""
                    INSERT INTO call_outcomes (
                        outcome_id, schema_version, call_id, horizon,
                        basis_revision_id, basis_key, basis_revision_sequence_number, basis_revision_type,
                        snapshot_id, methodology_id, methodology_version, methodology_definition_hash,
                        input_fingerprint, sequence_number, evaluation_status, reason_code,
                        event_time, processing_time, data_complete, data_mode, captured_at, provenance_id
                    ) VALUES (
                        'v3-upgraded-outcome', '1.0.0', 'v3-call', 'D1',
                        'v3-revision', 'REVISION:v3-revision', 1, 'CORRECTION',
                        'v3-snapshot', 'v3-methodology', '1.0.0', repeat('f', 64),
                        repeat('e', 64), 1, 'INCOMPLETE', 'HORIZON_DATA_MISSING',
                        '2026-08-02T00:00:00Z', '2026-08-02T00:01:00Z', FALSE,
                        'DEMO', '2026-08-02T00:01:00Z', 'v3-upgrade-test'
                    )
                    """);
            assertThat(query(connection, """
                    SELECT call_id || ':' || basis_revision_id || ':' || snapshot_id
                    FROM call_outcomes WHERE outcome_id = 'v3-upgraded-outcome'
                    """)).isEqualTo("v3-call:v3-revision:v3-snapshot");
        }
    }

    @Test
    void v5UpgradesPopulatedV4ThenImportsAndReplaysContextArchive() {
        String schema = "context_upgrade_path";
        Flyway v4 = Flyway.configure()
                .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .schemas(schema)
                .defaultSchema(schema)
                .target("4")
                .locations("classpath:db/migration")
                .load();
        v4.migrate();

        String separator = POSTGRES.getJdbcUrl().contains("?") ? "&" : "?";
        String scopedUrl = POSTGRES.getJdbcUrl() + separator + "currentSchema=" + schema;
        DriverManagerDataSource dataSource = new DriverManagerDataSource(
                scopedUrl, POSTGRES.getUsername(), POSTGRES.getPassword());
        NamedParameterJdbcTemplate jdbc = new NamedParameterJdbcTemplate(dataSource);
        JdbcAnalystCallRepository callRepository = new JdbcAnalystCallRepository(jdbc);
        JdbcAnalystCallRevisionRepository revisionRepository = new JdbcAnalystCallRevisionRepository(jdbc);
        JdbcScoringMethodologyRepository methodologyRepository = new JdbcScoringMethodologyRepository(jdbc);
        JdbcCallOutcomeRepository outcomeRepository = new JdbcCallOutcomeRepository(jdbc, methodologyRepository);
        FixtureAnalystCallProvider provider = new FixtureAnalystCallProvider(new ObjectMapper());
        DataSourceTransactionManager transactionManager = new DataSourceTransactionManager(dataSource);
        TransactionTemplate transactions = new TransactionTemplate(transactionManager);
        var dataSet = provider.load();

        Integer callsImported = transactions.execute(status -> callRepository.importDataSet(dataSet));
        assertThat(callsImported).isEqualTo(3);
        Integer revisionsImported = transactions.execute(
                status -> revisionRepository.importAll(dataSet.revisions()));
        Integer methodologiesImported = transactions.execute(
                status -> methodologyRepository.importAll(dataSet.methodologies()));
        Integer outcomesImported = transactions.execute(
                status -> outcomeRepository.importAll(dataSet.outcomes()));
        assertThat(revisionsImported).isEqualTo(2);
        assertThat(methodologiesImported).isEqualTo(2);
        assertThat(outcomesImported).isEqualTo(4);
        var callBeforeMigration = callRepository.findById("demo-call-001").orElseThrow();
        var nullableCallBeforeMigration = callRepository.findById("demo-call-003").orElseThrow();
        var revisionsBeforeMigration = revisionRepository.findByCallId("demo-call-002");
        var outcomesBeforeMigration = outcomeRepository.findByCallId("demo-call-001");

        Flyway latest = Flyway.configure()
                .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .schemas(schema)
                .defaultSchema(schema)
                .locations("classpath:db/migration")
                .load();
        latest.migrate();
        assertThat(latest.info().current().getVersion().getVersion()).isEqualTo("9");

        JdbcCallContextRepository contextRepository = new JdbcCallContextRepository(jdbc);
        Integer contextsImported = transactions.execute(
                status -> contextRepository.importDataSet(dataSet.contexts()));
        assertThat(contextsImported).isEqualTo(9);
        assertThat(callRepository.findById("demo-call-001").orElseThrow()).isEqualTo(callBeforeMigration);
        assertThat(callRepository.findById("demo-call-003").orElseThrow())
                .isEqualTo(nullableCallBeforeMigration);
        assertThat(callRepository.findById("demo-call-003").orElseThrow().call().sourceReference().document())
                .satisfies(document -> {
                    assertThat(document.publisher()).isNull();
                    assertThat(document.canonicalUrl()).isNull();
                    assertThat(document.publishedAt()).isNull();
                    assertThat(document.externalId()).isNull();
                    assertThat(document.contentHash()).isNull();
                });
        assertThat(revisionRepository.findByCallId("demo-call-002"))
                .isEqualTo(revisionsBeforeMigration);
        assertThat(outcomeRepository.findByCallId("demo-call-001"))
                .isEqualTo(outcomesBeforeMigration);
        assertThat(revisionRepository.count()).isEqualTo(2);
        assertThat(methodologyRepository.count()).isEqualTo(2);
        assertThat(outcomeRepository.count()).isEqualTo(4);
        Integer contextReplay = transactions.execute(
                status -> contextRepository.importDataSet(dataSet.contexts()));
        assertThat(contextReplay).isZero();
        assertThat(contextRepository.observationCount()).isEqualTo(7);
        assertThat(contextRepository.macroSnapshotCount()).isEqualTo(1);
        assertThat(contextRepository.eventContextCount()).isEqualTo(1);
        assertThat(contextRepository.findObservationById("macro-observation-demo-cpi-revision-001"))
                .isPresent();
        assertThat(contextRepository.findMacroSnapshotByCallId("demo-call-001").orElseThrow().observations())
                .extracting(observation -> observation.macroObservationId())
                .doesNotContain("macro-observation-demo-cpi-revision-001");
    }

    @Test
    void v6AppendsAndReplaysExactSecCatalogCapturesOnPostgreSql() throws Exception {
        String schema = "sec_capture_v6";
        Flyway flyway = Flyway.configure()
                .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .schemas(schema)
                .defaultSchema(schema)
                .locations("classpath:db/migration")
                .target("6")
                .load();
        flyway.migrate();
        assertThat(flyway.info().current().getVersion().getVersion()).isEqualTo("6");

        String separator = POSTGRES.getJdbcUrl().contains("?") ? "&" : "?";
        String scopedUrl = POSTGRES.getJdbcUrl() + separator + "currentSchema=" + schema;
        DriverManagerDataSource dataSource = new DriverManagerDataSource(
                scopedUrl, POSTGRES.getUsername(), POSTGRES.getPassword());
        NamedParameterJdbcTemplate jdbc = new NamedParameterJdbcTemplate(dataSource);
        JdbcFilingCatalogCaptureRepository repository =
                new JdbcFilingCatalogCaptureRepository(
                        jdbc, new SecFilingCatalogCaptureReplayVerifier());
        TransactionTemplate transactions = new TransactionTemplate(
                new DataSourceTransactionManager(dataSource));
        Instant firstTime = Instant.parse("2026-08-25T01:02:03.123456Z");
        var first = SecFilingCatalogCaptureTestFixture.capture(firstTime);
        var later = SecFilingCatalogCaptureTestFixture.capture(firstTime.plusSeconds(60));

        FilingCatalogCaptureAppendResult firstAppend =
                transactions.execute(status -> repository.append(first));
        FilingCatalogCaptureAppendResult replay =
                transactions.execute(status -> repository.append(first));
        FilingCatalogCaptureAppendResult laterAppend =
                transactions.execute(status -> repository.append(later));
        assertThat(firstAppend)
                .isEqualTo(FilingCatalogCaptureAppendResult.INSERTED);
        assertThat(replay)
                .isEqualTo(FilingCatalogCaptureAppendResult.IDENTICAL_REPLAY);
        assertThat(laterAppend)
                .isEqualTo(FilingCatalogCaptureAppendResult.INSERTED);
        assertThat(repository.count()).isEqualTo(2);
        assertThat(jdbc.getJdbcOperations().queryForObject(
                "SELECT COUNT(*) FROM sec_decoded_response_bodies", Long.class))
                .isEqualTo(1);
        assertThat(repository.findLatestAtOrBefore(
                SecFilingCatalogCaptureTestFixture.PROVIDER,
                SecFilingCatalogCaptureTestFixture.PRODUCT,
                SecFilingCatalogCaptureTestFixture.CIK,
                firstTime.plusSeconds(30),
                SecFilingCatalogCaptureTestFixture.PARSER_VERSION))
                .get()
                .extracting(capture -> capture.captureId())
                .isEqualTo(first.captureId());

        var concurrentCapture =
                SecFilingCatalogCaptureTestFixture.capture(firstTime.plusSeconds(120));
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        var executor = Executors.newFixedThreadPool(2);
        try {
            var left = executor.submit(() -> concurrentCaptureAppend(
                    dataSource, concurrentCapture, ready, start));
            var right = executor.submit(() -> concurrentCaptureAppend(
                    dataSource, concurrentCapture, ready, start));
            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            assertThat(List.of(left.get(20, TimeUnit.SECONDS), right.get(20, TimeUnit.SECONDS)))
                    .containsExactlyInAnyOrder(
                            FilingCatalogCaptureAppendResult.INSERTED,
                            FilingCatalogCaptureAppendResult.IDENTICAL_REPLAY);
        } finally {
            executor.shutdownNow();
        }
        assertThat(repository.count()).isEqualTo(3);

        Instant conflictingTime = firstTime.plusSeconds(180);
        var conflictingLeft =
                SecFilingCatalogCaptureTestFixture.capture(conflictingTime, "10-Q/A");
        var conflictingRight =
                SecFilingCatalogCaptureTestFixture.capture(conflictingTime, "10-K");
        long bodiesBeforeConflict = jdbc.getJdbcOperations().queryForObject(
                "SELECT COUNT(*) FROM sec_decoded_response_bodies", Long.class);
        CountDownLatch conflictReady = new CountDownLatch(2);
        CountDownLatch conflictStart = new CountDownLatch(1);
        var conflictExecutor = Executors.newFixedThreadPool(2);
        List<ConcurrentCaptureAppendAttempt> conflictAttempts;
        try {
            var left = conflictExecutor.submit(() -> concurrentCaptureAppendAttempt(
                    dataSource, conflictingLeft, conflictReady, conflictStart));
            var right = conflictExecutor.submit(() -> concurrentCaptureAppendAttempt(
                    dataSource, conflictingRight, conflictReady, conflictStart));
            assertThat(conflictReady.await(10, TimeUnit.SECONDS)).isTrue();
            conflictStart.countDown();
            conflictAttempts = List.of(
                    left.get(20, TimeUnit.SECONDS),
                    right.get(20, TimeUnit.SECONDS));
        } finally {
            conflictExecutor.shutdownNow();
        }
        assertThat(conflictAttempts)
                .filteredOn(attempt -> attempt.result()
                        == FilingCatalogCaptureAppendResult.INSERTED)
                .singleElement()
                .extracting(ConcurrentCaptureAppendAttempt::conflictMessage)
                .isNull();
        assertThat(conflictAttempts)
                .filteredOn(attempt -> attempt.conflictMessage() != null)
                .singleElement()
                .extracting(ConcurrentCaptureAppendAttempt::conflictMessage)
                .asString()
                .startsWith("conflicting filing catalog capture for natural capture identity");
        String winningCaptureId = conflictAttempts.stream()
                .filter(attempt -> attempt.result()
                        == FilingCatalogCaptureAppendResult.INSERTED)
                .findFirst()
                .orElseThrow()
                .captureId();
        String losingCaptureId = conflictAttempts.stream()
                .filter(attempt -> attempt.conflictMessage() != null)
                .findFirst()
                .orElseThrow()
                .captureId();
        assertThat(repository.count()).isEqualTo(4);
        assertThat(jdbc.getJdbcOperations().queryForObject(
                "SELECT COUNT(*) FROM sec_decoded_response_bodies", Long.class))
                .isEqualTo(bodiesBeforeConflict + 1);
        assertThat(repository.findByCaptureId(losingCaptureId)).isEmpty();
        assertThat(repository.findLatestAtOrBefore(
                SecFilingCatalogCaptureTestFixture.PROVIDER,
                SecFilingCatalogCaptureTestFixture.PRODUCT,
                SecFilingCatalogCaptureTestFixture.CIK,
                conflictingTime,
                SecFilingCatalogCaptureTestFixture.PARSER_VERSION))
                .get()
                .extracting(capture -> capture.captureId())
                .isEqualTo(winningCaptureId);
        assertThat(jdbc.getJdbcOperations().queryForObject("""
                SELECT COUNT(*)
                FROM sec_decoded_response_bodies b
                LEFT JOIN sec_filing_catalog_captures c
                  ON c.decoded_body_sha256 = b.decoded_body_sha256
                 AND c.decoded_body_length = b.decoded_body_length
                WHERE c.capture_id IS NULL
                """, Long.class))
                .isZero();

        assertThatThrownBy(() -> jdbc.getJdbcOperations().update("""
                INSERT INTO sec_decoded_response_bodies (
                    decoded_body_sha256, decoded_body_length, decoded_body, immutable
                ) VALUES (
                    repeat('a', 64), 2, decode('00', 'hex'), TRUE
                )
                """))
                .isInstanceOf(RuntimeException.class);
        assertThatThrownBy(() -> jdbc.getJdbcOperations().update(
                """
                        UPDATE sec_filing_catalog_captures
                        SET historical_segment_count = 0
                        WHERE capture_id = ?
                        """,
                first.captureId()))
                .isInstanceOf(RuntimeException.class);
        assertThatThrownBy(() -> jdbc.getJdbcOperations().update(
                """
                        UPDATE sec_filing_catalog_recent_filings
                        SET accepted_at = catalog_processing_time
                            + INTERVAL '1 microsecond'
                        WHERE capture_id = ? AND ordinal = 0
                        """,
                first.captureId()))
                .isInstanceOf(RuntimeException.class);
        assertThatThrownBy(() -> jdbc.getJdbcOperations().update(
                """
                        UPDATE sec_filing_catalog_historical_segments
                        SET file_name = 'CIK0000000001-submissions-002.json'
                        WHERE capture_id = ? AND ordinal = 0
                        """,
                first.captureId()))
                .isInstanceOf(RuntimeException.class);
        assertThatThrownBy(() -> jdbc.update(
                "DELETE FROM sec_decoded_response_bodies",
                new MapSqlParameterSource()))
                .isInstanceOf(RuntimeException.class);
        assertThat(repository.count()).isEqualTo(4);
        assertThat(repository.findByCaptureId(first.captureId())).isPresent();
    }

    @Test
    void v7UpgradesV6AndAppendsHistoricalSegmentsAtomicallyOnPostgreSql()
            throws Exception {
        String schema = "sec_segment_v7";
        Flyway throughV6 = Flyway.configure()
                .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .schemas(schema)
                .defaultSchema(schema)
                .locations("classpath:db/migration")
                .target("6")
                .load();
        throughV6.migrate();
        assertThat(throughV6.info().current().getVersion().getVersion()).isEqualTo("6");

        String separator = POSTGRES.getJdbcUrl().contains("?") ? "&" : "?";
        String scopedUrl = POSTGRES.getJdbcUrl() + separator + "currentSchema=" + schema;
        DriverManagerDataSource dataSource = new DriverManagerDataSource(
                scopedUrl, POSTGRES.getUsername(), POSTGRES.getPassword());
        NamedParameterJdbcTemplate jdbc = new NamedParameterJdbcTemplate(dataSource);
        JdbcFilingCatalogCaptureRepository rootRepository =
                new JdbcFilingCatalogCaptureRepository(
                        jdbc, new SecFilingCatalogCaptureReplayVerifier());
        TransactionTemplate transactions = new TransactionTemplate(
                new DataSourceTransactionManager(dataSource));
        Instant rootTime = Instant.parse("2026-08-25T02:02:03.123456Z");
        var pendingRoot = SecFilingCatalogCaptureTestFixture.capture(rootTime);
        FilingCatalogCaptureAppendResult rootAppend =
                transactions.execute(status -> rootRepository.append(pendingRoot));
        assertThat(rootAppend)
                .isEqualTo(FilingCatalogCaptureAppendResult.INSERTED);

        Flyway latest = Flyway.configure()
                .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .schemas(schema)
                .defaultSchema(schema)
                .locations("classpath:db/migration")
                .load();
        latest.migrate();
        assertThat(latest.info().current().getVersion().getVersion()).isEqualTo("9");

        var durableRoot = rootRepository.findByCaptureId(
                pendingRoot.captureId()).orElseThrow();
        JdbcHistoricalFilingSegmentCaptureRepository segmentRepository =
                new JdbcHistoricalFilingSegmentCaptureRepository(
                        jdbc,
                        new SecHistoricalFilingSegmentCaptureReplayVerifier(),
                        rootRepository);
        Instant firstTime = rootTime.plusSeconds(600);
        var first = SecHistoricalFilingSegmentCaptureTestFixture
                .captureWithMissingPrimaryDocument(
                durableRoot, firstTime);
        var later = SecHistoricalFilingSegmentCaptureTestFixture
                .captureWithMissingPrimaryDocument(
                durableRoot, firstTime.plusSeconds(60));

        HistoricalFilingSegmentCaptureAppendResult firstAppend =
                transactions.execute(status -> segmentRepository.append(first));
        HistoricalFilingSegmentCaptureAppendResult replayAppend =
                transactions.execute(status -> segmentRepository.append(first));
        HistoricalFilingSegmentCaptureAppendResult laterAppend =
                transactions.execute(status -> segmentRepository.append(later));
        assertThat(firstAppend)
                .isEqualTo(HistoricalFilingSegmentCaptureAppendResult.INSERTED);
        assertThat(replayAppend)
                .isEqualTo(HistoricalFilingSegmentCaptureAppendResult.IDENTICAL_REPLAY);
        assertThat(laterAppend)
                .isEqualTo(HistoricalFilingSegmentCaptureAppendResult.INSERTED);
        assertThat(segmentRepository.count()).isEqualTo(2);
        assertThat(segmentRepository.findLatestAtOrBefore(
                durableRoot.captureId(),
                0,
                firstTime.plusSeconds(30),
                SecHistoricalFilingSegmentCaptureTestFixture.PARSER_VERSION))
                .get()
                .extracting(capture -> capture.captureId())
                .isEqualTo(first.captureId());
        assertThat(segmentRepository.findByCaptureId(first.captureId()))
                .get()
                .extracting(capture -> capture.segment().filings().getFirst()
                        .primaryDocumentUri())
                .isNull();
        assertThat(jdbc.getJdbcOperations().queryForObject(
                """
                        SELECT COUNT(*)
                        FROM sec_historical_filing_segment_filings
                        WHERE segment_capture_id = ?
                          AND ordinal = 0
                          AND primary_document_uri IS NULL
                        """,
                Long.class,
                first.captureId()))
                .isEqualTo(1);
        assertThat(jdbc.getJdbcOperations().queryForObject(
                "SELECT COUNT(*) FROM sec_decoded_response_bodies", Long.class))
                .isEqualTo(2);

        var concurrentCapture =
                SecHistoricalFilingSegmentCaptureTestFixture.capture(
                        durableRoot, firstTime.plusSeconds(120));
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        var executor = Executors.newFixedThreadPool(2);
        try {
            var left = executor.submit(() -> concurrentSegmentAppend(
                    dataSource, concurrentCapture, ready, start));
            var right = executor.submit(() -> concurrentSegmentAppend(
                    dataSource, concurrentCapture, ready, start));
            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            assertThat(List.of(
                    left.get(20, TimeUnit.SECONDS),
                    right.get(20, TimeUnit.SECONDS)))
                    .containsExactlyInAnyOrder(
                            HistoricalFilingSegmentCaptureAppendResult.INSERTED,
                            HistoricalFilingSegmentCaptureAppendResult.IDENTICAL_REPLAY);
        } finally {
            executor.shutdownNow();
        }
        assertThat(segmentRepository.count()).isEqualTo(3);

        Instant conflictingTime = firstTime.plusSeconds(180);
        var conflictingLeft = SecHistoricalFilingSegmentCaptureTestFixture.capture(
                durableRoot, conflictingTime, "10-K/A");
        var conflictingRight = SecHistoricalFilingSegmentCaptureTestFixture.capture(
                durableRoot, conflictingTime, "20-F");
        long bodiesBeforeConflict = jdbc.getJdbcOperations().queryForObject(
                "SELECT COUNT(*) FROM sec_decoded_response_bodies", Long.class);
        CountDownLatch conflictReady = new CountDownLatch(2);
        CountDownLatch conflictStart = new CountDownLatch(1);
        var conflictExecutor = Executors.newFixedThreadPool(2);
        List<ConcurrentSegmentAppendAttempt> conflictAttempts;
        try {
            var left = conflictExecutor.submit(() -> concurrentSegmentAppendAttempt(
                    dataSource, conflictingLeft, conflictReady, conflictStart));
            var right = conflictExecutor.submit(() -> concurrentSegmentAppendAttempt(
                    dataSource, conflictingRight, conflictReady, conflictStart));
            assertThat(conflictReady.await(10, TimeUnit.SECONDS)).isTrue();
            conflictStart.countDown();
            conflictAttempts = List.of(
                    left.get(20, TimeUnit.SECONDS),
                    right.get(20, TimeUnit.SECONDS));
        } finally {
            conflictExecutor.shutdownNow();
        }
        assertThat(conflictAttempts)
                .filteredOn(attempt -> attempt.result()
                        == HistoricalFilingSegmentCaptureAppendResult.INSERTED)
                .singleElement()
                .extracting(ConcurrentSegmentAppendAttempt::conflictMessage)
                .isNull();
        assertThat(conflictAttempts)
                .filteredOn(attempt -> attempt.conflictMessage() != null)
                .singleElement()
                .extracting(ConcurrentSegmentAppendAttempt::conflictMessage)
                .asString()
                .startsWith(
                        "conflicting historical segment capture for natural capture identity");
        String losingCaptureId = conflictAttempts.stream()
                .filter(attempt -> attempt.conflictMessage() != null)
                .findFirst()
                .orElseThrow()
                .captureId();
        assertThat(segmentRepository.findByCaptureId(losingCaptureId)).isEmpty();
        assertThat(segmentRepository.count()).isEqualTo(4);
        assertThat(jdbc.getJdbcOperations().queryForObject(
                "SELECT COUNT(*) FROM sec_decoded_response_bodies", Long.class))
                .isEqualTo(bodiesBeforeConflict + 1);
        assertThat(jdbc.getJdbcOperations().queryForObject("""
                SELECT COUNT(*)
                FROM sec_decoded_response_bodies b
                LEFT JOIN sec_filing_catalog_captures r
                  ON r.decoded_body_sha256 = b.decoded_body_sha256
                 AND r.decoded_body_length = b.decoded_body_length
                LEFT JOIN sec_historical_filing_segment_captures s
                  ON s.decoded_body_sha256 = b.decoded_body_sha256
                 AND s.decoded_body_length = b.decoded_body_length
                WHERE r.capture_id IS NULL
                  AND s.segment_capture_id IS NULL
                """, Long.class)).isZero();

        var oversizedChild = SecHistoricalFilingSegmentCaptureTestFixture.capture(
                durableRoot, firstTime.plusSeconds(240), "X".repeat(129));
        long capturesBeforeChildFailure = segmentRepository.count();
        long bodiesBeforeChildFailure = jdbc.getJdbcOperations().queryForObject(
                "SELECT COUNT(*) FROM sec_decoded_response_bodies", Long.class);
        long filingsBeforeChildFailure = jdbc.getJdbcOperations().queryForObject(
                "SELECT COUNT(*) FROM sec_historical_filing_segment_filings", Long.class);

        Throwable childFailure = catchThrowable(() -> transactions.executeWithoutResult(
                status -> segmentRepository.append(oversizedChild)));
        assertThat(childFailure).isNotNull();
        Throwable deepestChildFailure = childFailure;
        while (deepestChildFailure.getCause() != null) {
            deepestChildFailure = deepestChildFailure.getCause();
        }
        assertThat(deepestChildFailure)
                .isInstanceOfSatisfying(SQLException.class,
                        sqlFailure -> assertThat(sqlFailure.getSQLState()).isEqualTo("22001"));
        assertThat(segmentRepository.findByCaptureId(oversizedChild.captureId())).isEmpty();
        assertThat(segmentRepository.count()).isEqualTo(capturesBeforeChildFailure);
        assertThat(jdbc.getJdbcOperations().queryForObject(
                "SELECT COUNT(*) FROM sec_decoded_response_bodies", Long.class))
                .isEqualTo(bodiesBeforeChildFailure);
        assertThat(jdbc.getJdbcOperations().queryForObject(
                "SELECT COUNT(*) FROM sec_historical_filing_segment_filings", Long.class))
                .isEqualTo(filingsBeforeChildFailure);
        assertThat(jdbc.getJdbcOperations().queryForObject(
                """
                        SELECT COUNT(*)
                        FROM sec_decoded_response_bodies
                        WHERE decoded_body_sha256 = ?
                          AND decoded_body_length = ?
                        """,
                Long.class,
                oversizedChild.segment().sourceReceipt().decodedBodySha256(),
                oversizedChild.segment().sourceReceipt().decodedBodyLength()))
                .isZero();

        assertThatThrownBy(() -> jdbc.getJdbcOperations().update(
                """
                        UPDATE sec_historical_filing_segment_captures
                        SET observed_filing_count = advertised_filing_count,
                            advertised_comparison = 'COUNT_MISMATCH'
                        WHERE segment_capture_id = ?
                        """,
                first.captureId()))
                .isInstanceOf(RuntimeException.class);
        assertThatThrownBy(() -> jdbc.getJdbcOperations().update(
                """
                        DELETE FROM sec_filing_catalog_historical_segments
                        WHERE capture_id = ? AND ordinal = 0
                        """,
                durableRoot.captureId()))
                .isInstanceOf(RuntimeException.class);
        assertThatThrownBy(() -> jdbc.update(
                "DELETE FROM sec_decoded_response_bodies",
                new MapSqlParameterSource()))
                .isInstanceOf(RuntimeException.class);
        assertThat(segmentRepository.findByCaptureId(first.captureId())).isPresent();
    }

    private static FilingCatalogCaptureAppendResult concurrentCaptureAppend(
            DriverManagerDataSource dataSource,
            com.wallstreetreceipts.api.domain.filing.FilingCatalogCapture capture,
            CountDownLatch ready,
            CountDownLatch start) throws Exception {
        NamedParameterJdbcTemplate jdbc = new NamedParameterJdbcTemplate(dataSource);
        JdbcFilingCatalogCaptureRepository repository =
                new JdbcFilingCatalogCaptureRepository(
                        jdbc, new SecFilingCatalogCaptureReplayVerifier());
        TransactionTemplate transaction = new TransactionTemplate(
                new DataSourceTransactionManager(dataSource));
        ready.countDown();
        if (!start.await(10, TimeUnit.SECONDS)) {
            throw new IllegalStateException("concurrent SEC capture append did not start");
        }
        return transaction.execute(status -> repository.append(capture));
    }

    private static HistoricalFilingSegmentCaptureAppendResult concurrentSegmentAppend(
            DriverManagerDataSource dataSource,
            com.wallstreetreceipts.api.domain.filing.HistoricalFilingSegmentCapture capture,
            CountDownLatch ready,
            CountDownLatch start) throws Exception {
        NamedParameterJdbcTemplate jdbc = new NamedParameterJdbcTemplate(dataSource);
        JdbcFilingCatalogCaptureRepository rootRepository =
                new JdbcFilingCatalogCaptureRepository(
                        jdbc, new SecFilingCatalogCaptureReplayVerifier());
        JdbcHistoricalFilingSegmentCaptureRepository segmentRepository =
                new JdbcHistoricalFilingSegmentCaptureRepository(
                        jdbc,
                        new SecHistoricalFilingSegmentCaptureReplayVerifier(),
                        rootRepository);
        TransactionTemplate transaction = new TransactionTemplate(
                new DataSourceTransactionManager(dataSource));
        ready.countDown();
        if (!start.await(10, TimeUnit.SECONDS)) {
            throw new IllegalStateException(
                    "concurrent SEC historical segment append did not start");
        }
        return transaction.execute(status -> segmentRepository.append(capture));
    }

    private static ConcurrentSegmentAppendAttempt concurrentSegmentAppendAttempt(
            DriverManagerDataSource dataSource,
            com.wallstreetreceipts.api.domain.filing.HistoricalFilingSegmentCapture capture,
            CountDownLatch ready,
            CountDownLatch start) throws Exception {
        try {
            return new ConcurrentSegmentAppendAttempt(
                    capture.captureId(),
                    concurrentSegmentAppend(dataSource, capture, ready, start),
                    null);
        } catch (IllegalArgumentException exception) {
            return new ConcurrentSegmentAppendAttempt(
                    capture.captureId(), null, exception.getMessage());
        }
    }

    private static ConcurrentCaptureAppendAttempt concurrentCaptureAppendAttempt(
            DriverManagerDataSource dataSource,
            com.wallstreetreceipts.api.domain.filing.FilingCatalogCapture capture,
            CountDownLatch ready,
            CountDownLatch start) throws Exception {
        try {
            return new ConcurrentCaptureAppendAttempt(
                    capture.captureId(),
                    concurrentCaptureAppend(dataSource, capture, ready, start),
                    null);
        } catch (IllegalArgumentException exception) {
            return new ConcurrentCaptureAppendAttempt(
                    capture.captureId(), null, exception.getMessage());
        }
    }

    private static AnalystCall copyCall(AnalystCall source, String callId, String providerEventId) {
        return new AnalystCall(
                callId, source.provider(), providerEventId, source.institution(), source.analyst(), source.asset(),
                source.eventTime(), source.processingTime(), source.direction(), source.originalRating(),
                source.previousTarget(), source.target(), source.currency(), source.targetDate(),
                source.sourceReference(), source.status(), source.dataMode(), source.capturedAt(), source.provenanceId());
    }

    private static String concurrentSaveResult(
            JdbcCallOutcomeRepository repository,
            DataSourceTransactionManager transactionManager,
            CallOutcome outcome,
            CountDownLatch ready,
            CountDownLatch start) throws InterruptedException {
        ready.countDown();
        start.await();
        try {
            Boolean inserted = new TransactionTemplate(transactionManager).execute(
                    status -> repository.saveIfAbsent(outcome));
            return "inserted:" + inserted;
        } catch (IllegalArgumentException exception) {
            return "conflict:" + exception.getMessage();
        }
    }

    private record ConcurrentCaptureAppendAttempt(
            String captureId,
            FilingCatalogCaptureAppendResult result,
            String conflictMessage) {
    }

    private record ConcurrentSegmentAppendAttempt(
            String captureId,
            HistoricalFilingSegmentCaptureAppendResult result,
            String conflictMessage) {
    }

    private static String concurrentContextImport(
            JdbcCallContextRepository repository,
            DataSourceTransactionManager transactionManager,
            CallContextDataSet dataSet,
            CountDownLatch ready,
            CountDownLatch start) throws InterruptedException {
        ready.countDown();
        start.await();
        try {
            Integer imported = new TransactionTemplate(transactionManager).execute(
                    status -> repository.importDataSet(dataSet));
            return "imported:" + imported;
        } catch (IllegalArgumentException exception) {
            return "conflict:" + exception.getMessage();
        }
    }

    private static MarketSnapshot copySnapshot(MarketSnapshot source, String snapshotId, String callId) {
        return new MarketSnapshot(
                snapshotId, callId, source.assetId(), source.eventTime(), source.processingTime(), source.assetPrice(),
                source.spx(), source.ndx(), source.vix(), source.treasury2y(), source.treasury10y(),
                source.realYield(), source.dxy(), source.wti(), source.gold(), source.volatility(),
                source.distanceFrom52WeekHigh(), source.distanceFromAth(), source.dataMode(), source.capturedAt(),
                source.provenanceId());
    }

    private static CallOutcome pendingOutcome(
            CallOutcome source,
            String outcomeId,
            String callId,
            String snapshotId,
            String basisRevisionId,
            String inputFingerprint,
            OutcomeHorizon horizon) {
        return new CallOutcome(
                outcomeId, source.schemaVersion(), callId, horizon, basisRevisionId, null, snapshotId,
                source.methodologyId(), source.methodologyVersion(), source.methodologyDefinitionHash(),
                inputFingerprint, 1, null, OutcomeEvaluationStatus.PENDING,
                OutcomeReasonCode.HORIZON_NOT_REACHED, source.eventTime(), source.processingTime(),
                null, null, null, null, null, null, null, null, null, null, false, source.dataMode(),
                source.processingTime(), "postgres-test");
    }

    private static CallOutcome excludedOutcome(
            CallOutcome source,
            String outcomeId,
            String callId,
            String snapshotId,
            String cancellationRevisionId,
            String inputFingerprint) {
        return new CallOutcome(
                outcomeId, source.schemaVersion(), callId, OutcomeHorizon.Y1, null, cancellationRevisionId,
                snapshotId, source.methodologyId(), source.methodologyVersion(),
                source.methodologyDefinitionHash(), inputFingerprint, 1, null, OutcomeEvaluationStatus.EXCLUDED,
                OutcomeReasonCode.CALL_CANCELLED, java.time.Instant.parse("2026-08-18T00:00:00Z"),
                java.time.Instant.parse("2026-08-18T00:01:00Z"), null, null, null, null, null, null, null,
                null, null, null, false, DataMode.DEMO, java.time.Instant.parse("2026-08-18T00:01:00Z"),
                "postgres-test");
    }

    private static CallOutcome copyOutcome(
            CallOutcome source,
            String outcomeId,
            String inputFingerprint,
            int sequenceNumber,
            String supersedesOutcomeId,
            long secondsAfterSource) {
        return new CallOutcome(
                outcomeId, source.schemaVersion(), source.callId(), source.horizon(), source.basisRevisionId(),
                source.cancellationRevisionId(), source.snapshotId(), source.methodologyId(), source.methodologyVersion(),
                source.methodologyDefinitionHash(), inputFingerprint, sequenceNumber, supersedesOutcomeId,
                source.evaluationStatus(), source.reasonCode(), source.eventTime(),
                source.processingTime().plusSeconds(secondsAfterSource), source.assetReturn(),
                source.benchmarkReturn(), source.sectorReturn(), source.alpha(), source.sectorAlpha(),
                source.mfe(), source.mae(), source.targetHit(), source.directionalWin(), source.targetError(),
                source.dataComplete(), source.dataMode(), source.capturedAt().plusSeconds(secondsAfterSource),
                source.provenanceId());
    }

    private static void assertRevisionRejected(
            Connection connection,
            String providerEventId,
            String revisionId,
            String revisionSql) throws Exception {
        boolean autoCommit = connection.getAutoCommit();
        connection.setAutoCommit(false);
        try (Statement statement = connection.createStatement()) {
            statement.executeUpdate("""
                    INSERT INTO provider_event_identities (
                        provider, provider_event_id, event_kind, canonical_event_id
                    ) VALUES (
                        'fixture', '%s', 'ANALYST_CALL_REVISION', '%s'
                    )
                    """.formatted(providerEventId, revisionId));
            assertThatThrownBy(() -> statement.executeUpdate(revisionSql))
                    .isInstanceOf(SQLException.class);
        } finally {
            connection.rollback();
            connection.setAutoCommit(autoCommit);
        }
    }

    private static void assertGapRejected(Connection connection) throws Exception {
        boolean autoCommit = connection.getAutoCommit();
        connection.setAutoCommit(false);
        try (Statement statement = connection.createStatement()) {
            statement.executeUpdate("""
                    INSERT INTO provider_event_identities (
                        provider, provider_event_id, event_kind, canonical_event_id
                    ) VALUES
                        ('fixture', 'raw-parent-event', 'ANALYST_CALL_REVISION', 'raw-parent-revision'),
                        ('fixture', 'raw-gap-event', 'ANALYST_CALL_REVISION', 'raw-gap-revision')
                    """);
            statement.executeUpdate("""
                    INSERT INTO analyst_call_revisions (
                        revision_id, schema_version, call_id, sequence_number,
                        provider, provider_event_id, revision_type, event_time, processing_time,
                        corrected_direction, corrected_original_rating, corrected_previous_target,
                        corrected_target, corrected_currency, reason, source_reference_id,
                        data_mode, captured_at, provenance_id
                    ) VALUES (
                        'raw-parent-revision', '1.0.0', 'demo-call-001', 1,
                        'fixture', 'raw-parent-event', 'CORRECTION',
                        '2026-08-10T13:00:00Z', '2026-08-10T13:01:00Z',
                        'BULLISH', 'Raw parent', 7800, 8000, 'USD', 'Temporary parent',
                        'source-ref-demo-001', 'DEMO', '2026-08-10T13:02:00Z', 'raw-test'
                    )
                    """);
            assertThatThrownBy(() -> statement.executeUpdate("""
                    INSERT INTO analyst_call_revisions (
                        revision_id, schema_version, call_id, supersedes_revision_id,
                        supersedes_sequence_number, supersedes_revision_type, sequence_number,
                        provider, provider_event_id, revision_type, event_time, processing_time,
                        corrected_direction, corrected_original_rating, corrected_previous_target,
                        corrected_target, corrected_currency, reason, source_reference_id,
                        data_mode, captured_at, provenance_id
                    ) VALUES (
                        'raw-gap-revision', '1.0.0', 'demo-call-001', 'raw-parent-revision',
                        1, 'CORRECTION', 3,
                        'fixture', 'raw-gap-event', 'CORRECTION',
                        '2026-08-10T14:00:00Z', '2026-08-10T14:01:00Z',
                        'BULLISH', 'Raw gap', 7800, 8000, 'USD', 'Rejected gap',
                        'source-ref-demo-001', 'DEMO', '2026-08-10T14:02:00Z', 'raw-test'
                    )
                    """))
                    .isInstanceOf(SQLException.class);
        } finally {
            connection.rollback();
            connection.setAutoCommit(autoCommit);
        }
    }

    private static void assertCrossCallParentRejected(Connection connection) throws Exception {
        boolean autoCommit = connection.getAutoCommit();
        connection.setAutoCommit(false);
        try (Statement statement = connection.createStatement()) {
            statement.executeUpdate("""
                    INSERT INTO provider_event_identities (
                        provider, provider_event_id, event_kind, canonical_event_id
                    ) VALUES
                        ('fixture', 'raw-base-call-event', 'ANALYST_CALL', 'raw-base-call'),
                        ('fixture', 'raw-cross-parent-event', 'ANALYST_CALL_REVISION', 'raw-cross-parent'),
                        ('fixture', 'raw-cross-child-event', 'ANALYST_CALL_REVISION', 'raw-cross-child')
                    """);
            statement.executeUpdate("""
                    INSERT INTO analyst_calls (
                        call_id, provider, provider_event_id, institution_id, asset_id,
                        event_time, processing_time, direction, source_reference_id,
                        status, data_mode, captured_at, provenance_id
                    ) VALUES (
                        'raw-base-call', 'fixture', 'raw-base-call-event', 'inst-jpm', 'asset-spx',
                        '2026-08-10T12:00:00Z', '2026-08-10T12:01:00Z', 'NEUTRAL',
                        'source-ref-demo-001', 'ACTIVE', 'DEMO', '2026-08-10T12:01:00Z', 'raw-test'
                    )
                    """);
            statement.executeUpdate("""
                    INSERT INTO analyst_call_revisions (
                        revision_id, schema_version, call_id, sequence_number,
                        provider, provider_event_id, revision_type, event_time, processing_time,
                        corrected_direction, corrected_original_rating, corrected_previous_target,
                        corrected_target, corrected_currency, reason, source_reference_id,
                        data_mode, captured_at, provenance_id
                    ) VALUES (
                        'raw-cross-parent', '1.0.0', 'demo-call-001', 1,
                        'fixture', 'raw-cross-parent-event', 'CORRECTION',
                        '2026-08-10T13:00:00Z', '2026-08-10T13:01:00Z',
                        'BULLISH', 'Raw parent', 7800, 8000, 'USD', 'Temporary parent',
                        'source-ref-demo-001', 'DEMO', '2026-08-10T13:02:00Z', 'raw-test'
                    )
                    """);
            assertThatThrownBy(() -> statement.executeUpdate("""
                    INSERT INTO analyst_call_revisions (
                        revision_id, schema_version, call_id, supersedes_revision_id,
                        supersedes_sequence_number, supersedes_revision_type, sequence_number,
                        provider, provider_event_id, revision_type, event_time, processing_time,
                        corrected_direction, corrected_original_rating, corrected_previous_target,
                        corrected_target, corrected_currency, reason, source_reference_id,
                        data_mode, captured_at, provenance_id
                    ) VALUES (
                        'raw-cross-child', '1.0.0', 'raw-base-call', 'raw-cross-parent',
                        1, 'CORRECTION', 2,
                        'fixture', 'raw-cross-child-event', 'CORRECTION',
                        '2026-08-10T14:00:00Z', '2026-08-10T14:01:00Z',
                        'BULLISH', 'Raw child', 7800, 8000, 'USD', 'Rejected cross-call parent',
                        'source-ref-demo-001', 'DEMO', '2026-08-10T14:02:00Z', 'raw-test'
                    )
                    """))
                    .isInstanceOf(SQLException.class);
        } finally {
            connection.rollback();
            connection.setAutoCommit(autoCommit);
        }
    }

    private static void assertSqlRejected(Connection connection, String sql) throws Exception {
        boolean autoCommit = connection.getAutoCommit();
        connection.setAutoCommit(false);
        try (Statement statement = connection.createStatement()) {
            assertThatThrownBy(() -> statement.executeUpdate(sql))
                    .isInstanceOf(SQLException.class);
        } finally {
            connection.rollback();
            connection.setAutoCommit(autoCommit);
        }
    }

    private static void assertPointInTimeObservationRejected(Connection connection, String callId) throws Exception {
        boolean autoCommit = connection.getAutoCommit();
        connection.setAutoCommit(false);
        try (Statement statement = connection.createStatement()) {
            statement.executeUpdate("""
                    INSERT INTO macro_snapshots (
                        macro_snapshot_id, schema_version, call_id, event_time, event_date,
                        processing_time, immutable, data_mode, captured_at, provenance_id
                    ) VALUES (
                        'raw-point-in-time-snapshot', '1.0.0', '%s', '2026-08-10T12:00:00Z', '2026-08-10',
                        '2026-08-10T12:03:00Z', TRUE, 'DEMO', '2026-08-10T12:03:00Z', 'raw-test'
                    )
                    """.formatted(callId));
            assertThatThrownBy(() -> statement.executeUpdate("""
                    INSERT INTO macro_snapshot_observations (
                        macro_snapshot_id, ordinal, macro_observation_id, series,
                        snapshot_event_time, snapshot_event_date, snapshot_processing_time,
                        snapshot_captured_at, observation_released_at, observation_processing_time,
                        observation_captured_at, vintage_start_key, vintage_end_key, data_mode
                    )
                    SELECT ms.macro_snapshot_id, 2, mo.macro_observation_id, mo.series,
                           ms.event_time, ms.event_date, ms.processing_time, ms.captured_at,
                           mo.released_at, mo.processing_time, mo.captured_at,
                           mo.vintage_start_key, mo.vintage_end_key, ms.data_mode
                    FROM macro_snapshots ms
                    CROSS JOIN macro_observations mo
                    WHERE ms.macro_snapshot_id = 'raw-point-in-time-snapshot'
                      AND mo.macro_observation_id = 'macro-observation-demo-cpi-revision-001'
                    """))
                    .isInstanceOf(SQLException.class);
        } finally {
            connection.rollback();
            connection.setAutoCommit(autoCommit);
        }
    }

    private static String query(Connection connection, String sql) throws Exception {
        try (Statement statement = connection.createStatement();
                ResultSet result = statement.executeQuery(sql)) {
            assertThat(result.next()).isTrue();
            return result.getString(1);
        }
    }
}
