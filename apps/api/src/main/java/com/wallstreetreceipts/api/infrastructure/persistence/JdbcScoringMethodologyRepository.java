package com.wallstreetreceipts.api.infrastructure.persistence;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import com.wallstreetreceipts.api.application.port.out.ScoringMethodologyRepository;
import com.wallstreetreceipts.api.domain.market.DataMode;
import com.wallstreetreceipts.api.domain.outcome.MethodologyStatus;
import com.wallstreetreceipts.api.domain.outcome.ScoringMethodology;

@Repository
public class JdbcScoringMethodologyRepository implements ScoringMethodologyRepository {

    private static final String SELECT_METHODOLOGY = """
            SELECT methodology_id, methodology_version, schema_version, definition_hash,
                   status, effective_at, data_mode, captured_at, provenance_id
            FROM scoring_methodologies
            """;

    private final NamedParameterJdbcTemplate jdbc;
    private volatile Boolean postgreSql;

    public JdbcScoringMethodologyRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    @Transactional
    public int importAll(List<ScoringMethodology> methodologies) {
        int imported = 0;
        for (ScoringMethodology methodology : methodologies.stream()
                .sorted(Comparator.comparing(ScoringMethodology::methodologyId)
                        .thenComparing(ScoringMethodology::methodologyVersion))
                .toList()) {
            if (saveIfAbsentInternal(methodology)) {
                imported++;
            }
        }
        return imported;
    }

    @Override
    @Transactional
    public boolean saveIfAbsent(ScoringMethodology methodology) {
        return saveIfAbsentInternal(methodology);
    }

    private boolean saveIfAbsentInternal(ScoringMethodology methodology) {
        Optional<ScoringMethodology> existing = findByIdAndVersion(
                methodology.methodologyId(), methodology.methodologyVersion());
        if (existing.isPresent()) {
            return replayOrConflict(existing.orElseThrow(), methodology);
        }

        String insert = """
                            INSERT INTO scoring_methodologies (
                                methodology_id, methodology_version, schema_version, definition_hash,
                                status, effective_at, data_mode, captured_at, provenance_id
                            ) VALUES (
                                :methodologyId, :methodologyVersion, :schemaVersion, :definitionHash,
                                :status, :effectiveAt, :dataMode, :capturedAt, :provenanceId
                            )
                            """;
        if (isPostgreSql()) {
            insert += " ON CONFLICT (methodology_id, methodology_version) DO NOTHING";
        }
        int inserted = jdbc.update(insert, parameters(methodology));
        if (inserted == 1) {
            return true;
        }
        Optional<ScoringMethodology> raced = findByIdAndVersion(
                methodology.methodologyId(), methodology.methodologyVersion());
        if (raced.isPresent()) {
            return replayOrConflict(raced.orElseThrow(), methodology);
        }
        throw new IllegalStateException("methodology insert returned no row without an identity conflict");
    }

    @Override
    public Optional<ScoringMethodology> findByIdAndVersion(String methodologyId, String methodologyVersion) {
        List<ScoringMethodology> values = jdbc.query(
                SELECT_METHODOLOGY
                        + " WHERE methodology_id = :methodologyId AND methodology_version = :methodologyVersion",
                new MapSqlParameterSource()
                        .addValue("methodologyId", methodologyId)
                        .addValue("methodologyVersion", methodologyVersion),
                this::mapMethodology);
        return values.stream().findFirst();
    }

    @Override
    public long count() {
        Long count = jdbc.getJdbcOperations().queryForObject(
                "SELECT COUNT(*) FROM scoring_methodologies", Long.class);
        return count == null ? 0 : count;
    }

    private static boolean replayOrConflict(
            ScoringMethodology existing,
            ScoringMethodology candidate) {
        if (existing.equals(candidate)) {
            return false;
        }
        throw new IllegalArgumentException(
                "conflicting scoring methodology identity: "
                        + candidate.methodologyId() + ":" + candidate.methodologyVersion());
    }

    private static MapSqlParameterSource parameters(ScoringMethodology methodology) {
        return new MapSqlParameterSource()
                .addValue("methodologyId", methodology.methodologyId())
                .addValue("methodologyVersion", methodology.methodologyVersion())
                .addValue("schemaVersion", methodology.schemaVersion())
                .addValue("definitionHash", methodology.definitionHash())
                .addValue("status", methodology.status().name())
                .addValue("effectiveAt", utc(methodology.effectiveAt()))
                .addValue("dataMode", methodology.dataMode().name())
                .addValue("capturedAt", utc(methodology.capturedAt()))
                .addValue("provenanceId", methodology.provenanceId());
    }

    private ScoringMethodology mapMethodology(ResultSet result, int rowNumber) throws SQLException {
        return new ScoringMethodology(
                result.getString("methodology_id"), result.getString("methodology_version"),
                result.getString("schema_version"), result.getString("definition_hash"),
                MethodologyStatus.valueOf(result.getString("status")), instant(result, "effective_at"),
                DataMode.valueOf(result.getString("data_mode")), instant(result, "captured_at"),
                result.getString("provenance_id"));
    }

    private boolean isPostgreSql() {
        Boolean current = postgreSql;
        if (current != null) {
            return current;
        }
        Boolean detected = jdbc.getJdbcOperations().execute((ConnectionCallback<Boolean>) connection ->
                "PostgreSQL".equals(connection.getMetaData().getDatabaseProductName()));
        postgreSql = Boolean.TRUE.equals(detected);
        return postgreSql;
    }

    private static Instant instant(ResultSet result, String column) throws SQLException {
        Object value = result.getObject(column);
        if (value instanceof OffsetDateTime offsetDateTime) {
            return offsetDateTime.toInstant();
        }
        if (value instanceof Instant instant) {
            return instant;
        }
        if (value instanceof Timestamp timestamp) {
            return timestamp.toInstant();
        }
        throw new SQLException("Unsupported timestamp value for " + column + ": " + value);
    }

    private static OffsetDateTime utc(Instant instant) {
        return OffsetDateTime.ofInstant(instant, ZoneOffset.UTC);
    }
}
