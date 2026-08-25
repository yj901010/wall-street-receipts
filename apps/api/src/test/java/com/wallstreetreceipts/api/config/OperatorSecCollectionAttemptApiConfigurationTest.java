package com.wallstreetreceipts.api.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;

import com.wallstreetreceipts.api.application.filinghistory.ExecuteSecFilingHistoryCollectionAttemptService;
import com.wallstreetreceipts.api.application.filinghistory.SecFilingHistoryCollectionAttemptQueryService;
import com.wallstreetreceipts.api.application.port.out.SecFilingHistoryCollectionAttemptRepository;
import com.wallstreetreceipts.api.web.operator.OperatorSecCollectionAttemptController;

class OperatorSecCollectionAttemptApiConfigurationTest {

    private final WebApplicationContextRunner contextRunner =
            new WebApplicationContextRunner()
                    .withUserConfiguration(
                            OperatorSecCollectionAttemptApiConfiguration.class,
                            OperatorSecCollectionAttemptController.class)
                    .withBean(
                            SecFilingHistoryCollectionAttemptRepository.class,
                            () -> mock(SecFilingHistoryCollectionAttemptRepository.class))
                    .withBean(
                            ExecuteSecFilingHistoryCollectionAttemptService.class,
                            () -> mock(ExecuteSecFilingHistoryCollectionAttemptService.class));

    @Test
    void operatorHttpSurfaceIsAbsentByDefault() {
        contextRunner.run(context -> {
            assertThat(context).doesNotHaveBean(
                    SecFilingHistoryCollectionAttemptQueryService.class);
            assertThat(context).doesNotHaveBean(
                    OperatorSecCollectionAttemptController.class);
        });
    }

    @Test
    void explicitEnabledFlagCreatesTheQueryAndControllerBoundary() {
        contextRunner
                .withPropertyValues("app.operator-api.enabled=true")
                .run(context -> {
                    assertThat(context).hasSingleBean(
                            SecFilingHistoryCollectionAttemptQueryService.class);
                    assertThat(context).hasSingleBean(
                            OperatorSecCollectionAttemptController.class);
                });
    }
}
