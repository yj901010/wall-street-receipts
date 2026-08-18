package com.wallstreetreceipts.api.migration;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wallstreetreceipts.api.infrastructure.persistence.JdbcAnalystCallRepository;
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

        assertThat(flyway.info().applied()).hasSize(2);

        DriverManagerDataSource dataSource = new DriverManagerDataSource(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
        JdbcAnalystCallRepository repository = new JdbcAnalystCallRepository(
                new NamedParameterJdbcTemplate(dataSource));
        FixtureAnalystCallProvider provider = new FixtureAnalystCallProvider(new ObjectMapper());

        assertThat(repository.importDataSet(provider.load())).isEqualTo(2);
        assertThat(repository.importDataSet(provider.load())).isZero();
        assertThat(repository.count()).isEqualTo(2);

        try (Connection connection = POSTGRES.createConnection("")) {
            assertThat(query(connection,
                    "SELECT metadata_value FROM platform_metadata WHERE metadata_key = 'schema_baseline'"))
                    .isEqualTo("P0");
            assertThat(query(connection, """
                    SELECT COUNT(*)::text
                    FROM information_schema.table_constraints
                    WHERE table_name = 'analyst_calls'
                      AND constraint_name = 'uq_analyst_calls_provider_event'
                      AND constraint_type = 'UNIQUE'
                    """)).isEqualTo("1");
            assertThat(query(connection, """
                    SELECT COUNT(*)::text
                    FROM information_schema.check_constraints
                    WHERE constraint_name = 'ck_market_snapshots_immutable'
                    """)).isEqualTo("1");
            assertThat(query(connection, """
                    SELECT is_nullable
                    FROM information_schema.columns
                    WHERE table_name = 'market_snapshots' AND column_name = 'asset_price'
                    """)).isEqualTo("YES");
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
