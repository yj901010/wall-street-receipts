package com.wallstreetreceipts.api.infrastructure.persistence;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import javax.sql.DataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import com.wallstreetreceipts.api.domain.cpi.CpiCollectionAttempt.*;
import com.wallstreetreceipts.api.infrastructure.provider.bls.BlsCpiParser;
import com.wallstreetreceipts.api.support.CpiTestFixture;

class JdbcCpiAttemptReadTimeoutTest {
    private final DataSource datasource = mock(DataSource.class);
    private final Connection connection = mock(Connection.class);
    private final PreparedStatement statement = mock(PreparedStatement.class);
    private final AtomicInteger timeout = new AtomicInteger();
    private final AtomicInteger executionTimeout = new AtomicInteger(-1);
    private final UUID id = new UUID(0, 1);
    private JdbcTemplate jdbc;
    private JdbcCpiRepository repository;

    @BeforeEach void setup() throws Exception {
        when(datasource.getConnection()).thenReturn(connection);
        when(connection.getAutoCommit()).thenReturn(true);
        when(connection.prepareStatement(anyString())).thenReturn(statement);
        when(statement.getQueryTimeout()).thenAnswer(call -> timeout.get());
        doAnswer(call -> { timeout.set(call.getArgument(0)); return null; }).when(statement).setQueryTimeout(anyInt());
        when(statement.executeQuery()).thenAnswer(call -> {
            executionTimeout.set(timeout.get());
            return mock(ResultSet.class); // Empty result; no domain or SQL parsing mock.
        });
        jdbc = new JdbcTemplate(datasource);
        repository = new JdbcCpiRepository(jdbc, new BlsCpiParser());
    }

    @ParameterizedTest
    @CsvSource({"-1,3", "0,3", "1,1", "2,2", "3,3", "10,3"})
    void bothReadStatementsCapTheirOwnTimeoutWithoutChangingTheTemplate(int configured, int expected) throws Exception {
        jdbc.setQueryTimeout(configured);
        assertThat(repository.recentAttempts()).isEmpty();
        assertThat(executionTimeout.get()).isEqualTo(expected);
        assertThat(repository.findAttempt(id)).isEmpty();
        assertThat(executionTimeout.get()).isEqualTo(expected);
        assertThat(jdbc.getQueryTimeout()).isEqualTo(configured);
        verify(statement).setString(1, id.toString());
        verify(statement, times(2)).close();
        verify(connection, times(2)).close();
    }

    @ParameterizedTest
    @CsvSource({"1,1", "10,3"})
    void ambientTransactionCannotExtendReadLimitAndShorterBudgetIsPreserved(int seconds, int expected) {
        var transaction = new TransactionTemplate(new DataSourceTransactionManager(datasource));
        transaction.setTimeout(seconds);
        transaction.executeWithoutResult(status -> {
            repository.recentAttempts();
            assertThat(executionTimeout.get()).isEqualTo(expected);
            repository.findAttempt(id);
            assertThat(executionTimeout.get()).isEqualTo(expected);
        });
        assertThat(jdbc.getQueryTimeout()).isEqualTo(-1);
    }

    @Test void internalTerminalValidationRetainsWriteTransactionBudget() throws Exception {
        var result = new Result(Instant.parse("2026-09-08T15:00:00Z"), Status.FAILED, null, null, Failure.FETCH, null);
        assertThatThrownBy(() -> repository.finishAttempt(id, result)).isInstanceOf(IllegalArgumentException.class);
        assertThat(executionTimeout.get()).isEqualTo(10);
        verify(statement, never()).setQueryTimeout(3);
        verify(connection).rollback();
    }

    @Test void internalReceiptValidationRetainsWriteTransactionBudget() throws Exception {
        var at = Instant.parse("2026-09-08T15:00:00Z");
        var raw = CpiTestFixture.bytes();
        var receipt = new BlsCpiParser().parse(raw, id, at, 2023, 2026);
        assertThatThrownBy(() -> repository.saveAttempt(id, receipt, raw, 2023, 2026, at))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(executionTimeout.get()).isEqualTo(10);
        verify(statement, never()).setQueryTimeout(3);
        verify(connection).rollback();
    }

    @Test void inabilityToApplyTimeoutFailsClosedBeforeExecutingAndClosesResources() throws Exception {
        doThrow(new SQLException("synthetic driver failure", "HY000")).when(statement).setQueryTimeout(3);
        assertThatThrownBy(repository::recentAttempts).isInstanceOf(DataAccessException.class);
        assertThatThrownBy(() -> repository.findAttempt(id)).isInstanceOf(DataAccessException.class);
        verify(statement, never()).executeQuery();
        verify(statement, times(2)).close();
        verify(connection, times(2)).close();
    }
}
