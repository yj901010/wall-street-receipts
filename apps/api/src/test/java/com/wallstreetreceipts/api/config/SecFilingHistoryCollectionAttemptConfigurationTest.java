package com.wallstreetreceipts.api.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import java.time.Clock;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import com.wallstreetreceipts.api.application.filinghistory.ExecuteSecFilingHistoryCollectionAttemptService;
import com.wallstreetreceipts.api.application.filinghistory.SingleJvmSecFilingHistoryCollectionAttemptMutex;
import com.wallstreetreceipts.api.application.port.out.FilingCatalogCaptureRepository;
import com.wallstreetreceipts.api.application.port.out.HistoricalFilingSegmentCaptureRepository;
import com.wallstreetreceipts.api.application.port.out.SecFilingHistoryCollectionAttemptCommitter;
import com.wallstreetreceipts.api.application.port.out.SecFilingHistoryCollectionAttemptRepository;

class SecFilingHistoryCollectionAttemptConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(SecFilingHistoryCollectionAttemptConfiguration.class)
            .withBean(Clock.class, Clock::systemUTC)
            .withBean(
                    SecFilingHistoryCollectionAttemptRepository.class,
                    () -> mock(SecFilingHistoryCollectionAttemptRepository.class))
            .withBean(
                    SecFilingHistoryCollectionAttemptCommitter.class,
                    () -> mock(SecFilingHistoryCollectionAttemptCommitter.class))
            .withBean(
                    FilingCatalogCaptureRepository.class,
                    () -> mock(FilingCatalogCaptureRepository.class))
            .withBean(
                    HistoricalFilingSegmentCaptureRepository.class,
                    () -> mock(HistoricalFilingSegmentCaptureRepository.class));

    @Test
    void createsSelectionCapableServiceWithoutSecProviderBeansOrEnabledFlag() {
        contextRunner.run(context -> {
            assertThat(context).hasSingleBean(
                    ExecuteSecFilingHistoryCollectionAttemptService.class);
            assertThat(context).hasSingleBean(
                    SingleJvmSecFilingHistoryCollectionAttemptMutex.class);
            assertThat(context).doesNotHaveBean(
                    com.wallstreetreceipts.api.application.port.out
                            .FilingCatalogCaptureProvider.class);
            assertThat(context).doesNotHaveBean(
                    com.wallstreetreceipts.api.application.port.out
                            .HistoricalFilingSegmentCaptureProvider.class);
        });
    }
}
