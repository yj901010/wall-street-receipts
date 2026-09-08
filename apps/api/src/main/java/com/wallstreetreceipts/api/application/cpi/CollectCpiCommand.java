package com.wallstreetreceipts.api.application.cpi;

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
import com.wallstreetreceipts.api.config.TimeConfiguration;
import com.wallstreetreceipts.api.infrastructure.persistence.JdbcCpiRepository;
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
    @Import({TimeConfiguration.class, JdbcCpiRepository.class, BlsCpiParser.class,
            CpiCollectorConfiguration.class, CollectCpiCommand.class})
    static class CollectorConfiguration {}
    private final CpiCollectionJob job;
    private final ConfigurableApplicationContext context;
    public CollectCpiCommand(CpiCollectionJob job, ConfigurableApplicationContext context) {
        this.job = job;
        this.context = context;
    }
    @Override public void run(ApplicationArguments arguments) {
        if (context instanceof WebServerApplicationContext) {
            throw new IllegalStateException("CPI collection requires --spring.main.web-application-type=none");
        }
        try {
            var snapshot = job.collect().orElseThrow(() -> new IllegalStateException("CPI collection cooldown is active"));
            System.out.println("BLS_CPI_CAPTURE_SAVED " + snapshot.captureId() + " " + snapshot.capturedAt());
        } finally { context.close(); }
    }
}
