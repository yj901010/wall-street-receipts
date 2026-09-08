package com.wallstreetreceipts.api.application.cpi;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.web.servlet.context.AnnotationConfigServletWebServerApplicationContext;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.scheduling.Trigger;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.scheduling.support.SimpleTriggerContext;
import com.wallstreetreceipts.api.infrastructure.provider.bls.BlsCpiClient;
import com.wallstreetreceipts.api.infrastructure.provider.bls.BlsCpiParser;
import com.wallstreetreceipts.api.support.CpiTestFixture;

@ExtendWith(OutputCaptureExtension.class)
class ScheduleCpiCommandTest {
    private static final Instant NOW = Instant.parse("2026-09-08T12:00:00Z");
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);

    @Test void dailyAt23KstRegardlessOfHostClockZone() {
        for (String zone : new String[]{"UTC", "Asia/Seoul", "America/New_York", "Europe/London"}) {
            for (String date : new String[]{"2026-01-13", "2026-03-08", "2026-09-11", "2026-11-01", "2026-12-31", "2028-02-29"}) {
                var start = Instant.parse(date + "T13:59:59Z");
                var trigger = ScheduleCpiCommand.trigger();
                var context = new SimpleTriggerContext(Clock.fixed(start, ZoneId.of(zone)));
                var next = trigger.nextExecution(context);
                assertThat(next).isEqualTo(Instant.parse(date + "T14:00:00Z"));
                assertThat(next.atZone(ScheduleCpiCommand.ZONE).getHour()).isEqualTo(23);
                context.update(next, next, next.plusSeconds(20));
                assertThat(trigger.nextExecution(context)).isEqualTo(next.plus(Duration.ofDays(1)));
            }
        }
    }

    @Test void slotIsAfter830EasternInBothDstSeasons() {
        for (String date : new String[]{"2026-01-13", "2026-09-11"}) {
            var release = LocalDate.parse(date).atTime(8, 30).atZone(ZoneId.of("America/New_York"));
            var collection = Instant.parse(date + "T14:00:00Z");
            assertThat(Duration.between(release.toInstant(), collection).toMinutes()).isIn(30L, 90L);
        }
    }

    @Test void restartAtOrAfterSlotSkipsToTomorrowWithoutCatchUp() {
        for (String instant : new String[]{"2026-09-08T14:00:00Z", "2026-09-08T14:00:01Z", "2026-09-09T01:00:00Z"}) {
            var context = new SimpleTriggerContext(Clock.fixed(Instant.parse(instant), ZoneOffset.UTC));
            assertThat(ScheduleCpiCommand.trigger().nextExecution(context)).isEqualTo("2026-09-09T14:00:00Z");
        }
        var context = new SimpleTriggerContext(CLOCK);
        context.update(NOW, NOW, NOW.plus(Duration.ofDays(5)));
        assertThat(ScheduleCpiCommand.trigger().nextExecution(context)).isEqualTo("2026-09-13T14:00:00Z");
    }

    @Test void startRegistersOneFutureTaskAndDoesNotCollectImmediately(CapturedOutput output) {
        var context = mock(ConfigurableApplicationContext.class);
        var scheduler = mock(ThreadPoolTaskScheduler.class);
        var job = mock(CpiCollectionJob.class);
        when(context.getBean(CpiCollectionJob.class)).thenReturn(job);
        when(context.getBean(Clock.class)).thenReturn(CLOCK);
        when(context.getBean(ThreadPoolTaskScheduler.class)).thenReturn(scheduler);
        doAnswer(invocation -> {
            Trigger trigger = invocation.getArgument(1);
            assertThat(trigger.nextExecution(new SimpleTriggerContext(CLOCK))).isEqualTo("2026-09-08T14:00:00Z");
            return null;
        }).when(scheduler).schedule(any(Runnable.class), any(Trigger.class));
        ScheduleCpiCommand.start(context);
        verify(scheduler).schedule(any(Runnable.class), any(Trigger.class));
        verifyNoMoreInteractions(scheduler);
        verifyNoInteractions(job);
        assertThat(output.getOut()).contains("daily=23:00_KST", "2026-09-08 23:00:00 KST", "catch_up=off", "retention=append_only");
    }

    @Test void actualExecutorUsesInjectedClockAndCancelsFutureWorkOnShutdown() {
        var scheduler = new ScheduleCpiCommand.SchedulerConfiguration().cpiTaskScheduler(CLOCK);
        scheduler.initialize();
        var task = mock(Runnable.class);
        try {
            ScheduledFuture<?> future = scheduler.schedule(task, ScheduleCpiCommand.trigger());
            assertThat(scheduler.getClock()).isSameAs(CLOCK);
            assertThat(scheduler.getPoolSize()).isEqualTo(1);
            assertThat(future.getDelay(TimeUnit.SECONDS)).isBetween(7195L, 7200L);
            verifyNoInteractions(task);
        } finally {
            scheduler.shutdown();
        }
        assertThat(scheduler.getScheduledThreadPoolExecutor().isShutdown()).isTrue();
        assertThat(scheduler.getScheduledThreadPoolExecutor().getQueue()).isEmpty();
    }

    @Test void scheduledResultsAreDistinctAndNoErrorTextIsLogged(CapturedOutput output) {
        var job = mock(CpiCollectionJob.class);
        var saved = new BlsCpiParser().parse(CpiTestFixture.bytes(), UUID.randomUUID(), NOW, 2023, 2026);
        when(job.collect()).thenReturn(Optional.of(saved)).thenReturn(Optional.empty())
                .thenThrow(new BlsCpiClient.RateLimited(NOW.plusSeconds(172800)))
                .thenThrow(new IllegalStateException("SECRET_PROVIDER_BODY", new RuntimeException("SECRET_KEY")))
                .thenReturn(Optional.of(saved));
        for (int i = 0; i < 5; i++) ScheduleCpiCommand.attempt(job, CLOCK);
        verify(job, times(5)).collect();
        verifyNoMoreInteractions(job);
        assertThat(output.getAll()).contains("BLS_CPI_CAPTURE_SAVED", saved.captureId().toString(),
                "2026-09-08 21:00:00 KST", "BLS_CPI_SCHEDULE_SKIPPED", "BLS_CPI_SCHEDULE_RATE_LIMITED",
                "2026-09-10 21:00:00 KST", "BLS_CPI_SCHEDULE_FAILED", "automatic_retry=off")
                .doesNotContain("SECRET_PROVIDER_BODY", "SECRET_KEY", "IllegalStateException");
    }

    @Test void webContextCannotStartCollectorOrScheduler() {
        var context = mock(AnnotationConfigServletWebServerApplicationContext.class);
        assertThatThrownBy(() -> ScheduleCpiCommand.start(context)).hasMessageContaining("separate headless");
        verifyNoInteractions(context);
        assertThatThrownBy(() -> new CpiCollectorConfiguration().cpiCollectionJob(null, null, CLOCK,
                new MockEnvironment(), context)).hasMessageContaining("separate headless");
    }

    @Test void ordinaryProfilesDoNotCreateCollectorOrScheduler() {
        new ApplicationContextRunner().withUserConfiguration(CpiCollectorConfiguration.class,
                ScheduleCpiCommand.SchedulerConfiguration.class, CollectCpiCommand.CollectorConfiguration.class)
                .run(context -> {
                    assertThat(context).hasNotFailed().doesNotHaveBean(CpiCollectionJob.class)
                            .doesNotHaveBean(ThreadPoolTaskScheduler.class).doesNotHaveBean(CollectCpiCommand.class);
                });
    }
}
