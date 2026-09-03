package com.wallstreetreceipts.api.config;

import java.time.Clock;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

import com.wallstreetreceipts.api.application.filingsegment.PersistHistoricalFilingSegmentCaptureService;
import com.wallstreetreceipts.api.application.port.out.FilingCatalogCaptureRepository;
import com.wallstreetreceipts.api.application.port.out.HistoricalFilingSegmentCaptureRepository;
import com.wallstreetreceipts.api.infrastructure.provider.sec.SecEdgarHistoricalFilingSegmentProvider;
import com.wallstreetreceipts.api.infrastructure.provider.sec.SecRequestRateLimiter;
import com.wallstreetreceipts.api.infrastructure.provider.sec.SecRetryAfterPolicy;

@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(
        prefix = "app.public-data.sec",
        name = "enabled",
        havingValue = "true")
public class SecHistoricalFilingSegmentConfiguration {

    @Bean
    SecEdgarHistoricalFilingSegmentProvider secEdgarHistoricalFilingSegmentProvider(
            @Qualifier("secEdgarRestClient") RestClient restClient,
            SecEdgarProperties properties,
            Clock clock,
            SecRequestRateLimiter rateLimiter,
            SecRetryAfterPolicy retryAfterPolicy) {
        return new SecEdgarHistoricalFilingSegmentProvider(
                restClient,
                properties.baseUrl(),
                clock,
                rateLimiter,
                retryAfterPolicy);
    }

    @Bean
    PersistHistoricalFilingSegmentCaptureService
            persistHistoricalFilingSegmentCaptureService(
                    FilingCatalogCaptureRepository rootRepository,
                    SecEdgarHistoricalFilingSegmentProvider provider,
                    HistoricalFilingSegmentCaptureRepository segmentRepository) {
        return new PersistHistoricalFilingSegmentCaptureService(
                rootRepository,
                provider,
                segmentRepository);
    }
}
