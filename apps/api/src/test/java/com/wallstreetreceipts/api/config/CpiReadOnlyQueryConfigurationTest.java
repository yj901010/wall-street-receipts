package com.wallstreetreceipts.api.config;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import com.wallstreetreceipts.api.application.cpi.CpiAttemptReader;
import com.wallstreetreceipts.api.application.cpi.CpiDeadlineReader;
import com.wallstreetreceipts.api.application.cpi.CpiRepository;

class CpiReadOnlyQueryConfigurationTest {
    private final CpiRepository repository = mock(CpiRepository.class);
    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(CpiReadOnlyQueryConfiguration.class).withBean(CpiRepository.class, () -> repository);

    @ParameterizedTest @CsvSource({"DEFAULT,DEFAULT", "false,FULL", "false,CPI_READ_ONLY", "DEFAULT,CPI_READ_ONLY", "true,DEFAULT", "true,FULL"})
    void unrelatedModesHaveNoExecutorOrReaderReplacement(String enabled, String access) {
        var selected = runner;
        if (!enabled.equals("DEFAULT")) selected = selected.withPropertyValues("app.operator-api.enabled=" + enabled);
        if (!access.equals("DEFAULT")) selected = selected.withPropertyValues("app.operator-api.access=" + access);
        selected.run(context -> {
            assertThat(context).hasNotFailed().doesNotHaveBean(CpiDeadlineReader.class);
            assertThat(context.getBean(CpiAttemptReader.class)).isSameAs(repository);
            verifyNoInteractions(repository);
        });
    }

    @Test void explicitReadOnlyModeDecoratesOnlyTheQueryPortAndClosesWithContext() {
        when(repository.recentAttempts()).thenReturn(List.of());
        var reader = new CpiDeadlineReader[1];
        runner.withPropertyValues("app.operator-api.enabled=true", "app.operator-api.access=CPI_READ_ONLY").run(context -> {
            assertThat(context).hasSingleBean(CpiDeadlineReader.class);
            reader[0] = context.getBean(CpiDeadlineReader.class);
            assertThat(context.getBean(CpiAttemptReader.class)).isSameAs(reader[0]);
            assertThat(context.getBean(CpiRepository.class)).isSameAs(repository);
            assertThat(reader[0].recentAttempts()).isEmpty();
        });
        assertThatThrownBy(reader[0]::recentAttempts).isExactlyInstanceOf(com.wallstreetreceipts.api.application.cpi.CpiAttemptQueryService.Unavailable.class);
        verify(repository, times(1)).recentAttempts();
    }
}
