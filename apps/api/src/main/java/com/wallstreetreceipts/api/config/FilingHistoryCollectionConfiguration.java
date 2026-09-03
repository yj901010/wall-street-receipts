package com.wallstreetreceipts.api.config;

import java.time.Clock;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.wallstreetreceipts.api.application.filinghistory.PersistFilingHistoryCollectionManifestService;
import com.wallstreetreceipts.api.application.filinghistory.SecFilingHistoryManifestAuditQueryService;
import com.wallstreetreceipts.api.application.port.out.FilingCatalogCaptureRepository;
import com.wallstreetreceipts.api.application.port.out.FilingHistoryCollectionManifestRepository;
import com.wallstreetreceipts.api.application.port.out.HistoricalFilingSegmentCaptureRepository;

@Configuration(proxyBeanMethods = false)
public class FilingHistoryCollectionConfiguration {

    @Bean
    SecFilingHistoryManifestAuditQueryService
            secFilingHistoryManifestAuditQueryService(
                    FilingHistoryCollectionManifestRepository manifestRepository) {
        return new SecFilingHistoryManifestAuditQueryService(manifestRepository);
    }

    @Bean
    PersistFilingHistoryCollectionManifestService
            persistFilingHistoryCollectionManifestService(
                    FilingCatalogCaptureRepository rootRepository,
                    HistoricalFilingSegmentCaptureRepository segmentRepository,
                    FilingHistoryCollectionManifestRepository manifestRepository,
                    Clock clock) {
        return new PersistFilingHistoryCollectionManifestService(
                rootRepository,
                segmentRepository,
                manifestRepository,
                clock);
    }
}
