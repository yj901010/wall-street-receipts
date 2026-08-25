package com.wallstreetreceipts.api.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.wallstreetreceipts.api.application.filing.PersistFilingCatalogCaptureService;
import com.wallstreetreceipts.api.application.port.out.FilingCatalogCaptureRepository;
import com.wallstreetreceipts.api.infrastructure.provider.sec.SecEdgarFilingCatalogProvider;

@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(
        prefix = "app.public-data.sec",
        name = "enabled",
        havingValue = "true")
public class SecFilingCatalogPersistenceConfiguration {

    @Bean
    PersistFilingCatalogCaptureService persistFilingCatalogCaptureService(
            SecEdgarFilingCatalogProvider provider,
            FilingCatalogCaptureRepository repository) {
        return new PersistFilingCatalogCaptureService(provider, repository);
    }
}
