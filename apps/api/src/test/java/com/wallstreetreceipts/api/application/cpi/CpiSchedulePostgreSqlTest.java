package com.wallstreetreceipts.api.application.cpi;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.web.context.WebServerApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import com.wallstreetreceipts.api.infrastructure.persistence.JdbcCpiRepository;
import com.wallstreetreceipts.api.infrastructure.provider.bls.BlsCpiClient;
import com.wallstreetreceipts.api.infrastructure.provider.bls.BlsCpiParser;
import com.wallstreetreceipts.api.support.CpiTestFixture;

@Testcontainers
class CpiSchedulePostgreSqlTest {
    @Container static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17-alpine");

    @Test void headlessWorkerWiresWithoutFetchingAndDailyAttemptsPreserveReceiptsAndDurableBackoff() {
        var now = Instant.parse("2026-09-08T12:00:00Z");
        var clock = Clock.fixed(now, ZoneOffset.UTC);
        var noStartupCollection = mock(CpiCollectionJob.class);
        var datasource = new DriverManagerDataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
        var jdbc = new JdbcTemplate(datasource);
        new ApplicationContextRunner().withUserConfiguration(ScheduleCpiCommand.SchedulerConfiguration.class)
                .withBean("testClock", Clock.class, () -> clock, definition -> definition.setPrimary(true))
                .withBean("testJob", CpiCollectionJob.class, () -> noStartupCollection, definition -> definition.setPrimary(true))
                .withPropertyValues("spring.profiles.active=bls-cpi-scheduler",
                        "spring.datasource.url=" + POSTGRES.getJdbcUrl(),
                        "spring.datasource.username=" + POSTGRES.getUsername(),
                        "spring.datasource.password=" + POSTGRES.getPassword(),
                        "BLS_REGISTRATION_KEY=syntheticKeyNotARealCredential")
                .run(context -> {
                    assertThat(context).hasNotFailed().hasSingleBean(ThreadPoolTaskScheduler.class)
                            .doesNotHaveBean(CollectCpiCommand.class);
                    assertThat(context.getSourceApplicationContext()).isNotInstanceOf(WebServerApplicationContext.class);
                    var scheduler = context.getBean(ThreadPoolTaskScheduler.class);
                    assertThat(scheduler.getScheduledThreadPoolExecutor().getQueue()).isEmpty();
                    ScheduleCpiCommand.start(context.getSourceApplicationContext());
                    assertThat(scheduler.getScheduledThreadPoolExecutor().getQueue()).hasSize(1);
                    verifyNoInteractions(noStartupCollection);
                    assertThat(jdbc.queryForObject("SELECT count(*) FROM bls_cpi_captures", Integer.class)).isZero();
                });
        verifyNoInteractions(noStartupCollection);

        var parser = new BlsCpiParser();
        var fetcher = mock(CpiCollectionJob.Fetcher.class);
        when(fetcher.fetch(anyString(), eq(2026), any())).thenReturn(CpiTestFixture.bytes());
        var first = job(jdbc, parser, clock, fetcher).collect().orElseThrow();
        var secondClock = Clock.fixed(now.plus(Duration.ofDays(1)), ZoneOffset.UTC);
        var second = job(jdbc, parser, secondClock, fetcher).collect().orElseThrow();
        assertThat(first.captureId()).isNotEqualTo(second.captureId());
        assertThat(first.responseSha256()).isEqualTo(second.responseSha256());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM bls_cpi_captures", Integer.class)).isEqualTo(2);
        assertThat(new JdbcCpiRepository(jdbc, parser).latest(now)).contains(first);

        var limitedClock = Clock.fixed(now.plus(Duration.ofDays(2)), ZoneOffset.UTC);
        var until = limitedClock.instant().plus(Duration.ofDays(2));
        when(fetcher.fetch(anyString(), eq(2026), any())).thenThrow(new BlsCpiClient.RateLimited(until));
        assertThatThrownBy(() -> job(jdbc, parser, limitedClock, fetcher).collect()).isInstanceOf(BlsCpiClient.RateLimited.class);
        var restarted = job(new JdbcTemplate(datasource), parser,
                Clock.fixed(now.plus(Duration.ofDays(3)), ZoneOffset.UTC), fetcher);
        assertThat(restarted.collect()).isEmpty();
        verify(fetcher, times(3)).fetch(anyString(), eq(2026), any());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM bls_cpi_captures", Integer.class)).isEqualTo(2);
    }

    private CpiCollectionJob job(JdbcTemplate jdbc, BlsCpiParser parser, Clock clock, CpiCollectionJob.Fetcher fetcher) {
        return new CpiCollectionJob(new JdbcCpiRepository(jdbc, parser), parser, clock,
                "syntheticKeyNotARealCredential", fetcher);
    }
}
