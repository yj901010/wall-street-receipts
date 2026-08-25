package com.wallstreetreceipts.api.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.wallstreetreceipts.api.application.filinghistory.SecFilingHistoryCollectionAttemptQueryService;
import com.wallstreetreceipts.api.application.port.out.SecFilingHistoryCollectionAttemptRepository;

@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(prefix = "app.operator-api", name = "enabled", havingValue = "true")
public class OperatorSecCollectionAttemptApiConfiguration {

    @Bean
    SecFilingHistoryCollectionAttemptQueryService secFilingHistoryCollectionAttemptQueryService(
            SecFilingHistoryCollectionAttemptRepository repository) {
        return new SecFilingHistoryCollectionAttemptQueryService(repository);
    }
}
