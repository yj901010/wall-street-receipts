package com.wallstreetreceipts.api.application.cpi;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import com.wallstreetreceipts.api.domain.cpi.CpiCollectionAttempt;
import com.wallstreetreceipts.api.domain.cpi.CpiCollectionAttempt.*;

class CpiAttemptQueryServiceTest {
    private static final Instant NOW = Instant.parse("2026-09-08T15:02:00.123456Z");
    private final CpiAttemptReader reader = mock(CpiAttemptReader.class);
    private final CpiAttemptQueryService service = new CpiAttemptQueryService(reader, Clock.fixed(NOW, ZoneOffset.UTC));

    @Test void emptyIsEmptyNotDemoAndTwentyPlusOneOnlySignalsMore() {
        when(reader.recentAttempts()).thenReturn(List.of());
        assertThat(service.recent().attempts()).isEmpty();
        assertThat(service.recent().hasMore()).isFalse();
        var rows = IntStream.range(0, 21).mapToObj(i -> row(i + 1, NOW.minusSeconds(i))).toList();
        when(reader.recentAttempts()).thenReturn(rows);
        var batch = service.recent();
        assertThat(batch.hasMore()).isTrue();
        assertThat(batch.attempts()).containsExactlyElementsOf(rows.subList(0, 20));
        assertThat(batch.observedAt()).isEqualTo(NOW);
        when(reader.recentAttempts()).thenReturn(rows.subList(0, 20));
        assertThat(service.recent().hasMore()).isFalse();
    }

    @Test void canonicalSelectionOnlyAndNoRepositoryCallForInvalidInput() {
        for (var invalid : new String[]{"1-1-1-1-1", "SECRET", "", "00000000-0000-0000-0000-00000000000A", " 00000000-0000-0000-0000-000000000001"}) {
            assertThatThrownBy(() -> service.find(invalid)).isInstanceOf(CpiAttemptQueryService.InvalidQuery.class).hasMessage(null);
        }
        assertThatThrownBy(() -> service.find(null)).isInstanceOf(CpiAttemptQueryService.InvalidQuery.class);
        verifyNoInteractions(reader);
    }

    @Test void missingAndIncompleteAreDifferentAndNoSuccessIsBorrowedFromAnotherRow() {
        var incomplete = row(1, NOW.minusSeconds(86400));
        when(reader.findAttempt(incomplete.attemptId())).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.find(incomplete.attemptId().toString())).isInstanceOf(CpiAttemptQueryService.NotFound.class);
        when(reader.findAttempt(incomplete.attemptId())).thenReturn(Optional.of(incomplete));
        assertThat(service.find(incomplete.attemptId().toString()).attempts().getFirst().result()).isNull();
        verify(reader, times(2)).findAttempt(incomplete.attemptId());
        verifyNoMoreInteractions(reader);
    }

    @Test void repositoryFailureNeverEchoesCausesOrFallsBackToPriorData() {
        when(reader.recentAttempts()).thenThrow(new IllegalStateException("SECRET_SQL_BODY", new RuntimeException("SECRET_KEY")));
        assertThatThrownBy(service::recent).isInstanceOf(CpiAttemptQueryService.Unavailable.class).hasMessage(null).hasNoCause();
        var id = new UUID(0, 1);
        when(reader.findAttempt(id)).thenThrow(new IllegalArgumentException("SECRET_ROW"));
        assertThatThrownBy(() -> service.find(id.toString())).isInstanceOf(CpiAttemptQueryService.Unavailable.class).hasNoCause();
    }

    @Test void rejectOversizedDuplicateOutOfOrderAndFutureEvidence() {
        for (var rows : List.of(
                IntStream.range(0, 22).mapToObj(i -> row(i + 1, NOW.minusSeconds(i))).toList(),
                List.of(row(1, NOW), row(1, NOW)),
                List.of(row(1, NOW.minusSeconds(1)), row(2, NOW)),
                List.of(row(1, NOW), row(2, NOW)), // Wrong canonical-ID tie order.
                List.of(row(1, NOW.plusSeconds(1))))) {
            when(reader.recentAttempts()).thenReturn(rows);
            assertThatThrownBy(service::recent).isInstanceOf(CpiAttemptQueryService.Unavailable.class);
        }
        when(reader.recentAttempts()).thenReturn(List.of(row(2, NOW), row(1, NOW)));
        assertThat(service.recent().attempts()).hasSize(2);
    }

    @Test void futureTerminalOrWrongSelectedIdentityIsUnavailableButFutureBackoffIsAllowed() {
        var id = new UUID(0, 1);
        when(reader.findAttempt(id)).thenReturn(Optional.of(new CpiCollectionAttempt(id, Trigger.MANUAL, NOW, true,
                new Result(NOW.plusSeconds(1), Status.FAILED, null, null, Failure.FETCH, null))));
        assertThatThrownBy(() -> service.find(id.toString())).isInstanceOf(CpiAttemptQueryService.Unavailable.class);
        when(reader.findAttempt(id)).thenReturn(Optional.of(row(2, NOW)));
        assertThatThrownBy(() -> service.find(id.toString())).isInstanceOf(CpiAttemptQueryService.Unavailable.class);
        var limited = new CpiCollectionAttempt(id, Trigger.SCHEDULED, NOW, true,
                new Result(NOW, Status.RATE_LIMITED, null, null, null, NOW.plusSeconds(172800)));
        when(reader.findAttempt(id)).thenReturn(Optional.of(limited));
        assertThat(service.find(id.toString()).attempts()).containsExactly(limited);
    }

    private static CpiCollectionAttempt row(int id, Instant at) {
        return new CpiCollectionAttempt(new UUID(0, id), Trigger.MANUAL, at, true, null);
    }
}
