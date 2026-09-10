package com.wallstreetreceipts.api.config;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import javax.sql.DataSource;
import com.zaxxer.hikari.HikariDataSource;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

class CpiReadOnlyPoolConfigurationTest {
    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(DataSourceAutoConfiguration.class))
            .withUserConfiguration(CpiReadOnlyPoolConfiguration.class, PropertiesConfiguration.class)
            .withPropertyValues("spring.datasource.url=jdbc:h2:mem:cpi-pool-config", "spring.datasource.username=sa",
                    "spring.datasource.driver-class-name=org.h2.Driver",
                    "app.operator-api.token-sha256=" + "1".repeat(64));

    @ParameterizedTest
    @CsvSource({"DEFAULT,DEFAULT", "false,FULL", "false,CPI_READ_ONLY", "DEFAULT,CPI_READ_ONLY", "true,DEFAULT", "true,FULL"})
    void disabledOrFullApiRetainsTheConfiguredPoolBudget(String enabled, String access) {
        var selected = runner.withPropertyValues("spring.datasource.hikari.connection-timeout=9000");
        if (!enabled.equals("DEFAULT")) selected = selected.withPropertyValues("app.operator-api.enabled=" + enabled);
        if (!access.equals("DEFAULT")) selected = selected.withPropertyValues("app.operator-api.access=" + access);
        selected.run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).doesNotHaveBean("cpiReadOnlyPoolWaitLimit");
            assertThat(context.getBean(HikariDataSource.class).getConnectionTimeout()).isEqualTo(9000);
        });
    }

    @ParameterizedTest @ValueSource(longs = {0, 250, 500, 1000, 9000, 30000})
    void enabledReaderCapsAfterBindingWithoutExtendingShorterLimitsOrChangingOtherSettings(long configured) {
        runner.withPropertyValues("app.operator-api.enabled=true", "app.operator-api.access=CPI_READ_ONLY",
                        "spring.datasource.hikari.connection-timeout=" + configured,
                        "spring.datasource.hikari.validation-timeout=750",
                        "spring.datasource.hikari.maximum-pool-size=7", "spring.datasource.hikari.minimum-idle=2")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    var pool = context.getBean(HikariDataSource.class);
                    assertThat(pool.getConnectionTimeout()).isEqualTo(configured == 0 ? 1000 : Math.min(configured, 1000));
                    assertThat(pool.getValidationTimeout()).isEqualTo(750);
                    assertThat(pool.getMaximumPoolSize()).isEqualTo(7);
                    assertThat(pool.getMinimumIdle()).isEqualTo(2);
                    assertThat(pool.getDataSourceProperties()).isEmpty();
                    assertThat(pool.getHikariPoolMXBean()).isNull(); // No connection opened to configure the cap.
                });
    }

    @Test void environmentAliasesInTheRealApplicationConfigurationEnableTheCap() {
        runner.withInitializer(new ConfigDataApplicationContextInitializer())
                .withPropertyValues("spring.config.location=classpath:/application.yml", "spring.config.import=",
                        "OPERATOR_API_ENABLED=true", "OPERATOR_API_ACCESS=CPI_READ_ONLY")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context.getBean(OperatorApiProperties.class).cpiReadOnly()).isTrue();
                    assertThat(context.getBean(HikariDataSource.class).getConnectionTimeout()).isEqualTo(1000);
                });
    }

    @Test void nonDefaultPoolsAndNonHikariDataSourcesAreNotReconfigured() {
        var processor = CpiReadOnlyPoolConfiguration.cpiReadOnlyPoolWaitLimit();
        try (var other = new HikariDataSource()) {
            other.setConnectionTimeout(9000);
            assertThat(processor.postProcessAfterInitialization(other, "migrationDataSource")).isSameAs(other);
            assertThat(other.getConnectionTimeout()).isEqualTo(9000);
        }
        var custom = mock(DataSource.class);
        assertThat(processor.postProcessAfterInitialization(custom, "dataSource")).isSameAs(custom);
        verifyNoInteractions(custom);
    }

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(OperatorApiProperties.class)
    static class PropertiesConfiguration {}
}
