package com.wallstreetreceipts.api.application.cpi;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;
import org.mockito.ArgumentCaptor;
import com.wallstreetreceipts.api.domain.cpi.CpiCollectionAttempt.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import com.wallstreetreceipts.api.infrastructure.provider.bls.BlsCpiClient;
import com.wallstreetreceipts.api.infrastructure.provider.bls.BlsCpiParser;
import com.wallstreetreceipts.api.support.CpiTestFixture;

class CpiCollectionJobTest {
    private static final Instant NOW = Instant.parse("2026-09-08T14:00:00.123456789Z");
    private static final Instant MICRO = Instant.parse("2026-09-08T14:00:00.123456Z");
    private static final String KEY = "syntheticKeyNotARealCredential";
    private final CpiRepository repository = mock(CpiRepository.class);
    private final CpiCollectionJob.Fetcher fetcher = mock(CpiCollectionJob.Fetcher.class);
    private final Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);

    private CpiCollectionJob job() {
        return new CpiCollectionJob(repository, new BlsCpiParser(), clock, KEY, fetcher);
    }

    @Test void successfulAttemptClaimsBeforeHttpAndAppendsOneImmutableReceipt() {
        var raw = CpiTestFixture.bytes();
        when(repository.beginAttempt(any(), eq(Trigger.MANUAL), eq(MICRO))).thenReturn(true);
        when(fetcher.fetch(KEY, 2026, MICRO)).thenReturn(raw);
        var saved = job().collect().orElseThrow();
        assertThat(saved.capturedAt()).isEqualTo(MICRO);
        assertThat(saved.responseSha256()).isEqualTo(BlsCpiParser.sha256(raw));
        var order = inOrder(repository, fetcher);
        var id = ArgumentCaptor.forClass(UUID.class);
        order.verify(repository).beginAttempt(id.capture(), eq(Trigger.MANUAL), eq(MICRO));
        order.verify(fetcher).fetch(KEY, 2026, MICRO);
        order.verify(repository).saveAttempt(id.getValue(), saved, raw, 2023, 2026, MICRO);
        order.verifyNoMoreInteractions();
    }

    @Test void cooldownDoesNotCallProviderOrPretendSuccess() {
        assertThat(job().collect()).isEmpty();
        var id = ArgumentCaptor.forClass(UUID.class);
        verify(repository).beginAttempt(id.capture(), eq(Trigger.MANUAL), eq(MICRO));
        verify(repository).finishAttempt(id.getValue(), new Result(MICRO, Status.SKIPPED, null, null, null, null));
        verifyNoMoreInteractions(repository);
        verifyNoInteractions(fetcher);
    }

    @Test void rateLimitPersistsProviderBackoffWithoutRetryOrAppend() {
        var until = MICRO.plusSeconds(172800);
        var limited = new BlsCpiClient.RateLimited(until);
        when(repository.beginAttempt(any(), eq(Trigger.MANUAL), eq(MICRO))).thenReturn(true);
        when(fetcher.fetch(KEY, 2026, MICRO)).thenThrow(limited);
        assertThatThrownBy(() -> job().collect()).isSameAs(limited);
        var order = inOrder(repository, fetcher);
        var id = ArgumentCaptor.forClass(UUID.class);
        order.verify(repository).beginAttempt(id.capture(), eq(Trigger.MANUAL), eq(MICRO));
        order.verify(fetcher).fetch(KEY, 2026, MICRO);
        order.verify(repository).finishAttempt(id.getValue(), new Result(MICRO, Status.RATE_LIMITED, null, null, null, until));
        order.verifyNoMoreInteractions();
    }

    @Test void transportAndParseFailureNeverAppendOrResetGate() {
        when(repository.beginAttempt(any(), eq(Trigger.MANUAL), eq(MICRO))).thenReturn(true);
        when(fetcher.fetch(KEY, 2026, MICRO)).thenThrow(new IllegalStateException("synthetic secret"));
        assertThatThrownBy(() -> job().collect()).isInstanceOf(IllegalStateException.class);
        doReturn(new byte[]{0}).when(fetcher).fetch(KEY, 2026, MICRO);
        assertThatThrownBy(() -> job().collect()).hasMessage("BLS CPI response failed validation");
        var ids = ArgumentCaptor.forClass(UUID.class);
        verify(repository, times(2)).beginAttempt(ids.capture(), eq(Trigger.MANUAL), eq(MICRO));
        assertThat(ids.getAllValues().get(0)).isNotEqualTo(ids.getAllValues().get(1));
        verify(repository).finishAttempt(ids.getAllValues().get(0), new Result(MICRO, Status.FAILED, null, null, Failure.FETCH, null));
        verify(repository).finishAttempt(ids.getAllValues().get(1), new Result(MICRO, Status.FAILED, null, null, Failure.PARSE, null));
        verifyNoMoreInteractions(repository);
        verify(fetcher, times(2)).fetch(KEY, 2026, MICRO);
        verifyNoMoreInteractions(fetcher);
    }

    @Test void databaseGateFailureCannotReachNetwork() {
        when(repository.beginAttempt(any(), any(), eq(MICRO))).thenThrow(new IllegalStateException("DB unavailable"));
        assertThatThrownBy(() -> job().collect()).isInstanceOf(IllegalStateException.class);
        verifyNoInteractions(fetcher);
    }

    @Test void appendFailureIsNotReportedAsSuccessful() {
        when(repository.beginAttempt(any(), eq(Trigger.MANUAL), eq(MICRO))).thenReturn(true);
        when(fetcher.fetch(KEY, 2026, MICRO)).thenReturn(CpiTestFixture.bytes());
        doThrow(new IllegalStateException("DB unavailable")).when(repository).saveAttempt(any(), any(), any(), eq(2023), eq(2026), eq(MICRO));
        assertThatThrownBy(() -> job().collect()).hasMessage("DB unavailable");
        verify(fetcher).fetch(KEY, 2026, MICRO);
        verify(repository, never()).postponeCollection(any());
        verify(repository, never()).finishAttempt(any(), any());
    }

    @Test void requestYearUsesUtcEvenWhenKoreanCalendarHasChangedYear() {
        var instant = Instant.parse("2026-12-31T16:00:00Z"); // Jan 1 in Korea
        when(repository.beginAttempt(any(), eq(Trigger.MANUAL), eq(instant))).thenReturn(true);
        when(fetcher.fetch(KEY, 2026, instant)).thenReturn(CpiTestFixture.bytes());
        new CpiCollectionJob(repository, new BlsCpiParser(), Clock.fixed(instant, ScheduleCpiCommand.ZONE), KEY, fetcher).collect();
        verify(fetcher).fetch(KEY, 2026, instant);
    }

    @Test void scheduledAttemptHasExplicitOriginAndMissingFinishIsNotSuccess() {
        when(repository.beginAttempt(any(), eq(Trigger.SCHEDULED), eq(MICRO))).thenReturn(false);
        doThrow(new IllegalStateException("terminal evidence unavailable")).when(repository).finishAttempt(any(), any());
        assertThatThrownBy(() -> job().collect(Trigger.SCHEDULED)).hasMessage("terminal evidence unavailable");
        verifyNoInteractions(fetcher);
        verify(repository, never()).saveAttempt(any(), any(), any(), anyInt(), anyInt(), any());
    }

    @Test void resultPersistenceFailureDoesNotRetryProviderOrInventSuccess() {
        when(repository.beginAttempt(any(), any(), eq(MICRO))).thenReturn(true);
        when(fetcher.fetch(KEY, 2026, MICRO)).thenThrow(new BlsCpiClient.RateLimited(MICRO.plusSeconds(86400)));
        doThrow(new IllegalStateException("terminal evidence unavailable")).when(repository).finishAttempt(any(), any());
        assertThatThrownBy(() -> job().collect()).hasMessage("terminal evidence unavailable");
        verify(fetcher, times(1)).fetch(KEY, 2026, MICRO);
        verify(repository, times(1)).finishAttempt(any(), any());
        verify(repository, never()).saveAttempt(any(), any(), any(), anyInt(), anyInt(), any());
    }

    @ParameterizedTest @NullAndEmptySource @ValueSource(strings = {"short", "bad secret?value", " syntheticKeyNotARealCredential"})
    void invalidKeyIsRejectedWithoutEchoOrAnyIo(String key) {
        assertThatThrownBy(() -> new CpiCollectionJob(repository, new BlsCpiParser(), clock, key, fetcher))
                .hasMessage("BLS_REGISTRATION_KEY needs attention").hasNoCause();
        verifyNoInteractions(repository, fetcher);
    }
}
