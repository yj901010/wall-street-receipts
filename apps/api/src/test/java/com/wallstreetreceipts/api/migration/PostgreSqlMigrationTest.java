package com.wallstreetreceipts.api.migration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wallstreetreceipts.api.domain.call.AnalystCallRevision;
import com.wallstreetreceipts.api.infrastructure.persistence.JdbcAnalystCallRepository;
import com.wallstreetreceipts.api.infrastructure.persistence.JdbcAnalystCallRevisionRepository;
import com.wallstreetreceipts.api.infrastructure.provider.fixture.FixtureAnalystCallProvider;

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

        assertThat(flyway.info().applied()).hasSize(3);

        DriverManagerDataSource dataSource = new DriverManagerDataSource(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
        JdbcAnalystCallRepository repository = new JdbcAnalystCallRepository(
                new NamedParameterJdbcTemplate(dataSource));
        JdbcAnalystCallRevisionRepository revisionRepository = new JdbcAnalystCallRevisionRepository(
                new NamedParameterJdbcTemplate(dataSource));
        FixtureAnalystCallProvider provider = new FixtureAnalystCallProvider(new ObjectMapper());
        TransactionTemplate transactions = new TransactionTemplate(new DataSourceTransactionManager(dataSource));
        var dataSet = provider.load();

        Integer importedCalls = transactions.execute(status -> repository.importDataSet(dataSet));
        Integer importedRevisions = transactions.execute(status -> revisionRepository.importAll(dataSet.revisions()));
        Integer duplicateCalls = transactions.execute(status -> repository.importDataSet(dataSet));
        Integer duplicateRevisions = transactions.execute(status -> revisionRepository.importAll(dataSet.revisions()));
        assertThat(importedCalls).isEqualTo(2);
        assertThat(importedRevisions).isEqualTo(2);
        assertThat(duplicateCalls).isZero();
        assertThat(duplicateRevisions).isZero();
        assertThat(repository.count()).isEqualTo(2);
        assertThat(revisionRepository.count()).isEqualTo(2);
        assertThat(revisionRepository.findByCallId("demo-call-002"))
                .extracting(revision -> revision.sequenceNumber())
                .containsExactly(1, 2);

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

        try (Connection connection = POSTGRES.createConnection("")) {
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
                    .isEqualTo("4");
            assertSqlRejected(connection, """
                    INSERT INTO provider_event_identities (
                        provider, provider_event_id, event_kind, canonical_event_id
                    ) VALUES (
                        'fixture', 'fixture-call-001', 'ANALYST_CALL_REVISION', 'raw-collision'
                    )
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

    private static String query(Connection connection, String sql) throws Exception {
        try (Statement statement = connection.createStatement();
                ResultSet result = statement.executeQuery(sql)) {
            assertThat(result.next()).isTrue();
            return result.getString(1);
        }
    }
}
