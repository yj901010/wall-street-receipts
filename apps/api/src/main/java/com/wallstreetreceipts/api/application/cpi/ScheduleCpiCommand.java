package com.wallstreetreceipts.api.application.cpi;

import java.time.Clock;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.web.context.WebServerApplicationContext;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.scheduling.support.CronTrigger;
import com.wallstreetreceipts.api.config.TimeConfiguration;
import com.wallstreetreceipts.api.infrastructure.persistence.JdbcCpiRepository;
import com.wallstreetreceipts.api.infrastructure.provider.bls.BlsCpiClient;
import com.wallstreetreceipts.api.infrastructure.provider.bls.BlsCpiParser;

/** Explicit long-lived worker. No startup fetch, catch-up loop, deletion, or web endpoint. */
public final class ScheduleCpiCommand {
    static final String CRON = "0 0 23 * * *";
    static final ZoneId ZONE = ZoneId.of("Asia/Seoul");
    private static final DateTimeFormatter KST = DateTimeFormatter
            .ofPattern("uuuu-MM-dd HH:mm:ss 'KST'").withZone(ZONE);
    private static final Logger LOG = LoggerFactory.getLogger(ScheduleCpiCommand.class);

    private ScheduleCpiCommand() {}

    public static void runCli() {
        var app = new SpringApplication(SchedulerConfiguration.class);
        app.setWebApplicationType(WebApplicationType.NONE);
        app.setAdditionalProfiles("bls-cpi-scheduler");
        var context = app.run();
        try {
            start(context);
        } catch (RuntimeException exception) {
            context.close();
            throw new IllegalStateException("CPI scheduler could not start; check worker configuration");
        }
    }

    static CronTrigger trigger() {
        // Lenient cron: restart and completion schedule only the next future slot.
        return new CronTrigger(CRON, ZONE);
    }

    static void start(ConfigurableApplicationContext context) {
        if (context instanceof WebServerApplicationContext) {
            throw new IllegalStateException("CPI scheduling requires a separate headless command");
        }
        var job = context.getBean(CpiCollectionJob.class);
        var clock = context.getBean(Clock.class);
        var scheduler = context.getBean(ThreadPoolTaskScheduler.class);
        var daily = trigger();
        scheduler.schedule(() -> attempt(job, clock), triggerContext -> {
            var next = daily.nextExecution(triggerContext);
            // Report the actual executor trigger calculation, not a separate
            // pre-registration estimate that could straddle the 23:00 boundary.
            LOG.info("BLS_CPI_SCHEDULE_WAITING next={}", KST.format(next));
            return next;
        });
        LOG.info("BLS_CPI_SCHEDULE_READY daily=23:00_KST catch_up=off retention=append_only");
    }

    static void attempt(CpiCollectionJob job, Clock clock) {
        try {
            var snapshot = job.collect();
            if (snapshot.isEmpty()) {
                LOG.info("BLS_CPI_SCHEDULE_SKIPPED reason=durable_cooldown at={}", KST.format(clock.instant()));
            } else {
                var saved = snapshot.orElseThrow();
                LOG.info("BLS_CPI_CAPTURE_SAVED id={} captured={}", saved.captureId(), KST.format(saved.capturedAt()));
            }
        } catch (BlsCpiClient.RateLimited exception) {
            LOG.warn("BLS_CPI_SCHEDULE_RATE_LIMITED retry_not_before={} automatic_retry=off",
                    KST.format(exception.retryAt()));
        } catch (RuntimeException exception) {
            // Do not log exception text/causes: SQL/provider errors can contain response bytes.
            LOG.error("BLS_CPI_SCHEDULE_FAILED at={} automatic_retry=off check=database_provider_and_key",
                    KST.format(clock.instant()));
        }
    }

    @Configuration(proxyBeanMethods = false)
    @EnableAutoConfiguration
    @Profile("bls-cpi-scheduler")
    @Import({TimeConfiguration.class, JdbcCpiRepository.class, BlsCpiParser.class, CpiCollectorConfiguration.class})
    static class SchedulerConfiguration {
        @Bean
        ThreadPoolTaskScheduler cpiTaskScheduler(Clock clock) {
            var scheduler = new ThreadPoolTaskScheduler();
            scheduler.setClock(clock);
            scheduler.setPoolSize(1);
            scheduler.setThreadNamePrefix("bls-cpi-daily-");
            scheduler.setRemoveOnCancelPolicy(true);
            scheduler.setWaitForTasksToCompleteOnShutdown(true);
            scheduler.setContinueExistingPeriodicTasksAfterShutdownPolicy(false);
            scheduler.setExecuteExistingDelayedTasksAfterShutdownPolicy(false);
            scheduler.setAwaitTerminationSeconds(30);
            scheduler.setErrorHandler(error -> LOG.error("BLS_CPI_SCHEDULER_FAILURE check=worker_health"));
            return scheduler;
        }
    }
}
