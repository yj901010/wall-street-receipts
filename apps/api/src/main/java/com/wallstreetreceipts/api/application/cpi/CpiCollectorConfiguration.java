package com.wallstreetreceipts.api.application.cpi;

import java.time.Clock;
import org.springframework.boot.web.context.WebServerApplicationContext;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.Environment;
import com.wallstreetreceipts.api.infrastructure.provider.bls.BlsCpiClient;
import com.wallstreetreceipts.api.infrastructure.provider.bls.BlsCpiParser;

@Configuration(proxyBeanMethods = false)
@Profile({"bls-cpi-cli", "bls-cpi-scheduler"})
class CpiCollectorConfiguration {
    @Bean
    CpiCollectionJob cpiCollectionJob(CpiRepository repository, BlsCpiParser parser,
                                     Clock clock, Environment environment,
                                     ConfigurableApplicationContext context) {
        if (context instanceof WebServerApplicationContext) {
            throw new IllegalStateException("CPI collection requires a separate headless command");
        }
        return new CpiCollectionJob(repository, parser, clock,
                environment.getProperty("BLS_REGISTRATION_KEY", ""), new BlsCpiClient()::fetch);
    }
}
