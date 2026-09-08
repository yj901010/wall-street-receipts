package com.wallstreetreceipts.api.application.cpi;

import java.time.Clock;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.web.context.WebServerApplicationContext;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Profile;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.core.env.Environment;
import com.wallstreetreceipts.api.config.TimeConfiguration;
import com.wallstreetreceipts.api.infrastructure.persistence.JdbcCpiRepository;
import com.wallstreetreceipts.api.infrastructure.provider.bls.BlsCpiClient;
import com.wallstreetreceipts.api.infrastructure.provider.bls.BlsCpiParser;

/** Explicit offline-HTTP-server command. Normal application startup never collects. */
public class CollectCpiCommand implements ApplicationRunner {
    public static void runCli() {
        var app = new SpringApplication(CollectorConfiguration.class);
        app.setWebApplicationType(WebApplicationType.NONE);
        app.setAdditionalProfiles("bls-cpi-cli");
        app.run();
    }
    @Configuration(proxyBeanMethods = false)
    @EnableAutoConfiguration
    @Profile("bls-cpi-cli")
    @Import({TimeConfiguration.class, JdbcCpiRepository.class, BlsCpiParser.class, CollectCpiCommand.class})
    static class CollectorConfiguration {}
    private final CpiRepository repository;
    private final BlsCpiParser parser;
    private final Clock clock;
    private final Environment environment;
    private final ConfigurableApplicationContext context;
    public CollectCpiCommand(CpiRepository repository, BlsCpiParser parser, Clock clock,
                             Environment environment, ConfigurableApplicationContext context) {
        this.repository = repository; this.parser = parser; this.clock = clock;
        this.environment = environment; this.context = context;
    }
    @Override public void run(ApplicationArguments arguments) {
        if (context instanceof WebServerApplicationContext) {
            throw new IllegalStateException("CPI collection requires --spring.main.web-application-type=none");
        }
        String key = environment.getProperty("BLS_REGISTRATION_KEY", "");
        if (!key.matches("[A-Za-z0-9]{16,128}")) throw new IllegalArgumentException("BLS_REGISTRATION_KEY needs attention");
        var started = clock.instant().truncatedTo(ChronoUnit.MICROS);
        int year = started.atZone(java.time.ZoneOffset.UTC).getYear();
        if (!repository.claimCollection(started)) throw new IllegalStateException("CPI collection cooldown is active");
        try {
            byte[] raw = new BlsCpiClient().fetch(key, year, started);
            var snapshot = parser.parse(raw, UUID.randomUUID(), clock.instant().truncatedTo(ChronoUnit.MICROS), year - 3, year);
            repository.append(snapshot, raw, year - 3, year);
            System.out.println("BLS_CPI_CAPTURE_SAVED " + snapshot.captureId() + " " + snapshot.capturedAt());
        } catch (BlsCpiClient.RateLimited exception) {
            repository.postponeCollection(exception.retryAt());
            throw exception;
        } finally { context.close(); }
    }
}
