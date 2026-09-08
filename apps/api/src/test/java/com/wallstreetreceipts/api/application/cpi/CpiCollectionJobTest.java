package com.wallstreetreceipts.api.application.cpi;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
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
        when(repository.claimCollection(MICRO)).thenReturn(true);
        when(fetcher.fetch(KEY, 2026, MICRO)).thenReturn(raw);
        var saved = job().collect().orElseThrow();
        assertThat(saved.capturedAt()).isEqualTo(MICRO);
        assertThat(saved.responseSha256()).isEqualTo(BlsCpiParser.sha256(raw));
        var order = inOrder(repository, fetcher);
        order.verify(repository).claimCollection(MICRO);
        order.verify(fetcher).fetch(KEY, 2026, MICRO);
        order.verify(repository).append(saved, raw, 2023, 2026);
        order.verifyNoMoreInteractions();
    }

    @Test void cooldownDoesNotCallProviderOrPretendSuccess() {
        assertThat(job().collect()).isEmpty();
        verify(repository).claimCollection(MICRO);
        verifyNoMoreInteractions(repository);
        verifyNoInteractions(fetcher);
    }

    @Test void rateLimitPersistsProviderBackoffWithoutRetryOrAppend() {
        var until = MICRO.plusSeconds(172800);
        var limited = new BlsCpiClient.RateLimited(until);
        when(repository.claimCollection(MICRO)).thenReturn(true);
        when(fetcher.fetch(KEY, 2026, MICRO)).thenThrow(limited);
        assertThatThrownBy(() -> job().collect()).isSameAs(limited);
        var order = inOrder(repository, fetcher);
        order.verify(repository).claimCollection(MICRO);
        order.verify(fetcher).fetch(KEY, 2026, MICRO);
        order.verify(repository).postponeCollection(until);
        order.verifyNoMoreInteractions();
    }

    @Test void transportAndParseFailureNeverAppendOrResetGate() {
        when(repository.claimCollection(MICRO)).thenReturn(true);
        when(fetcher.fetch(KEY, 2026, MICRO)).thenThrow(new IllegalStateException("synthetic secret"));
        assertThatThrownBy(() -> job().collect()).isInstanceOf(IllegalStateException.class);
        doReturn(new byte[]{0}).when(fetcher).fetch(KEY, 2026, MICRO);
        assertThatThrownBy(() -> job().collect()).hasMessage("BLS CPI response failed validation");
        verify(repository, times(2)).claimCollection(MICRO);
        verifyNoMoreInteractions(repository);
        verify(fetcher, times(2)).fetch(KEY, 2026, MICRO);
        verifyNoMoreInteractions(fetcher);
    }

    @Test void databaseGateFailureCannotReachNetwork() {
        when(repository.claimCollection(MICRO)).thenThrow(new IllegalStateException("DB unavailable"));
        assertThatThrownBy(() -> job().collect()).isInstanceOf(IllegalStateException.class);
        verifyNoInteractions(fetcher);
    }

    @Test void appendFailureIsNotReportedAsSuccessful() {
        when(repository.claimCollection(MICRO)).thenReturn(true);
        when(fetcher.fetch(KEY, 2026, MICRO)).thenReturn(CpiTestFixture.bytes());
        doThrow(new IllegalStateException("DB unavailable")).when(repository).append(any(), any(), eq(2023), eq(2026));
        assertThatThrownBy(() -> job().collect()).hasMessage("DB unavailable");
        verify(fetcher).fetch(KEY, 2026, MICRO);
        verify(repository, never()).postponeCollection(any());
    }

    @Test void requestYearUsesUtcEvenWhenKoreanCalendarHasChangedYear() {
        var instant = Instant.parse("2026-12-31T16:00:00Z"); // Jan 1 in Korea
        when(repository.claimCollection(instant)).thenReturn(true);
        when(fetcher.fetch(KEY, 2026, instant)).thenReturn(CpiTestFixture.bytes());
        new CpiCollectionJob(repository, new BlsCpiParser(), Clock.fixed(instant, ScheduleCpiCommand.ZONE), KEY, fetcher).collect();
        verify(fetcher).fetch(KEY, 2026, instant);
    }

    @ParameterizedTest @NullAndEmptySource @ValueSource(strings = {"short", "bad secret?value", " syntheticKeyNotARealCredential"})
    void invalidKeyIsRejectedWithoutEchoOrAnyIo(String key) {
        assertThatThrownBy(() -> new CpiCollectionJob(repository, new BlsCpiParser(), clock, key, fetcher))
                .hasMessage("BLS_REGISTRATION_KEY needs attention").hasNoCause();
        verifyNoInteractions(repository, fetcher);
    }
}
