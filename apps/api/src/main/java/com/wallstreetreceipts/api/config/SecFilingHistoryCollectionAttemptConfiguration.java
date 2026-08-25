package com.wallstreetreceipts.api.config;

import java.time.Clock;
import java.util.Optional;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.wallstreetreceipts.api.application.filinghistory.ExecuteSecFilingHistoryCollectionAttemptService;
import com.wallstreetreceipts.api.application.filinghistory.SingleJvmSecFilingHistoryCollectionAttemptMutex;
import com.wallstreetreceipts.api.application.port.out.FilingCatalogCaptureProvider;
import com.wallstreetreceipts.api.application.port.out.FilingCatalogCaptureRepository;
import com.wallstreetreceipts.api.application.port.out.HistoricalFilingSegmentCaptureProvider;
import com.wallstreetreceipts.api.application.port.out.HistoricalFilingSegmentCaptureRepository;
import com.wallstreetreceipts.api.application.port.out.SecFilingHistoryCollectionAttemptCommitter;
import com.wallstreetreceipts.api.application.port.out.SecFilingHistoryCollectionAttemptRepository;

@Configuration(proxyBeanMethods = false)
public class SecFilingHistoryCollectionAttemptConfiguration {

    @Bean
    SingleJvmSecFilingHistoryCollectionAttemptMutex
            singleJvmSecFilingHistoryCollectionAttemptMutex() {
        return new SingleJvmSecFilingHistoryCollectionAttemptMutex();
    }

    @Bean
    @ConditionalOnBean({
        SecFilingHistoryCollectionAttemptRepository.class,
        SecFilingHistoryCollectionAttemptCommitter.class
    })
    ExecuteSecFilingHistoryCollectionAttemptService
            executeSecFilingHistoryCollectionAttemptService(
                    SecFilingHistoryCollectionAttemptRepository attemptRepository,
                    SecFilingHistoryCollectionAttemptCommitter committer,
                    FilingCatalogCaptureRepository rootRepository,
                    HistoricalFilingSegmentCaptureRepository segmentRepository,
                    ObjectProvider<FilingCatalogCaptureProvider> rootProvider,
                    ObjectProvider<HistoricalFilingSegmentCaptureProvider> segmentProvider,
                    SingleJvmSecFilingHistoryCollectionAttemptMutex mutex,
                    Clock clock) {
        return new ExecuteSecFilingHistoryCollectionAttemptService(
                attemptRepository,
                committer,
                rootRepository,
                segmentRepository,
                Optional.ofNullable(rootProvider.getIfUnique()),
                Optional.ofNullable(segmentProvider.getIfUnique()),
                mutex,
                clock);
    }
}
