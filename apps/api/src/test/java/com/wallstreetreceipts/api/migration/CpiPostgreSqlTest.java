package com.wallstreetreceipts.api.migration;

import static org.assertj.core.api.Assertions.*;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.Executors;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import com.wallstreetreceipts.api.infrastructure.persistence.JdbcCpiRepository;
import com.wallstreetreceipts.api.infrastructure.provider.bls.BlsCpiParser;
import com.wallstreetreceipts.api.support.CpiTestFixture;

@Testcontainers
class CpiPostgreSqlTest {
    @Container static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17-alpine");
    @Test void upgradesV9AndPersistsReplayableReceiptsWithDurableAtomicCooldown() throws Exception {
        var ds = new DriverManagerDataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
        Flyway.configure().dataSource(ds).target("9").load().migrate();
        var flyway = Flyway.configure().dataSource(ds).load(); flyway.migrate();
        assertThat(flyway.info().current().getVersion().getVersion()).isEqualTo("12");
        var jdbc = new JdbcTemplate(ds); var parser = new BlsCpiParser(); var repo = new JdbcCpiRepository(jdbc, parser);
        var at = Instant.parse("2026-09-08T01:00:00.123456Z");
        assertThat(repo.latest(at)).isEmpty();
        try (var executor = Executors.newFixedThreadPool(2)) {
            var first = executor.submit(() -> repo.claimCollection(at)); var second = executor.submit(() -> repo.claimCollection(at));
            assertThat(first.get() ^ second.get()).isTrue();
        }
        assertThat(new JdbcCpiRepository(new JdbcTemplate(ds), parser).claimCollection(at.plusSeconds(899))).isFalse();
        assertThat(repo.claimCollection(at.plusSeconds(900))).isTrue();
        repo.postponeCollection(at.plusSeconds(86400)); repo.postponeCollection(at.plusSeconds(1000));
        assertThat(repo.claimCollection(at.plusSeconds(86399))).isFalse();
        var snapshot = parser.parse(CpiTestFixture.bytes(), UUID.randomUUID(), at, 2023, 2026);
        repo.append(snapshot, CpiTestFixture.bytes(), 2023, 2026);
        assertThat(repo.latest(at.minusNanos(1000))).isEmpty();
        assertThat(repo.latest(at)).contains(snapshot);
        assertThatThrownBy(() -> repo.append(snapshot, CpiTestFixture.bytes(), 2023, 2026)).isInstanceOf(org.springframework.dao.DuplicateKeyException.class);
        jdbc.update("UPDATE bls_cpi_captures SET response_sha256 = ?", "0".repeat(64));
        assertThatThrownBy(() -> repo.latest(at)).hasMessage("Stored CPI receipt failed integrity verification");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM bls_cpi_captures", Integer.class)).isEqualTo(1);
    }
}
