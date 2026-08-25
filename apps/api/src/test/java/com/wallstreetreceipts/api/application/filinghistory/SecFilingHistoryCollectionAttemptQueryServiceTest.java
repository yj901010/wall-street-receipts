package com.wallstreetreceipts.api.application.filinghistory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import com.wallstreetreceipts.api.application.port.out.SecFilingHistoryCollectionAttemptRepository;
import com.wallstreetreceipts.api.domain.filing.SecFilingHistoryCollectionAttempt;

class SecFilingHistoryCollectionAttemptQueryServiceTest {

    private static final String REQUEST_ID = "11111111-1111-4111-8111-111111111111";
    private static final Instant NOW = Instant.parse("2026-08-26T01:00:00.123456Z");

    private final SecFilingHistoryCollectionAttemptRepository repository =
            mock(SecFilingHistoryCollectionAttemptRepository.class);
    private final SecFilingHistoryCollectionAttemptQueryService service =
            new SecFilingHistoryCollectionAttemptQueryService(repository);

    @Test
    void returnsOnlyTheExactAttemptIdentity() {
        SecFilingHistoryCollectionAttempt attempt =
                SecFilingHistoryCollectionAttempt.planCaptureRoot(REQUEST_ID, "320193", NOW);
        when(repository.findByAttemptId(attempt.attemptId())).thenReturn(Optional.of(attempt));

        assertThat(service.findByAttemptId(attempt.attemptId())).isSameAs(attempt);
    }

    @Test
    void validUnknownIdentityIsTypedNotFound() {
        String attemptId = "f".repeat(64);
        when(repository.findByAttemptId(attemptId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.findByAttemptId(attemptId))
                .isInstanceOf(SecFilingHistoryCollectionAttemptNotFoundException.class);
    }

    @Test
    void malformedIdentityDoesNotReachTheRepository() {
        assertThatThrownBy(() -> service.findByAttemptId("ABC"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("attemptId must be lowercase SHA-256 hex");
        verifyNoInteractions(repository);
    }
}
