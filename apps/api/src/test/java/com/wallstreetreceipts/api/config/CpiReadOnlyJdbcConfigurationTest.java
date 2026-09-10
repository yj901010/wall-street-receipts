package com.wallstreetreceipts.api.config;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.util.Properties;
import javax.sql.DataSource;
import com.zaxxer.hikari.HikariDataSource;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.postgresql.Driver;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class CpiReadOnlyJdbcConfigurationTest {
    private static final String URL = "jdbc:postgresql://127.0.0.1:1/cpi_config_demo";
    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(DataSourceAutoConfiguration.class))
            .withUserConfiguration(CpiReadOnlyJdbcConfiguration.class, CpiReadOnlyPoolConfiguration.class)
            .withPropertyValues("spring.datasource.url=" + URL, "spring.datasource.driver-class-name=org.postgresql.Driver",
                    "spring.datasource.username=demo", "spring.datasource.password=DisposableConfigOnly");

    @ParameterizedTest
    @CsvSource({"DEFAULT,DEFAULT", "false,FULL", "false,CPI_READ_ONLY", "DEFAULT,CPI_READ_ONLY", "true,DEFAULT", "true,FULL"})
    void otherModesLeaveUrlPropertiesAndValidationUntouched(String enabled, String access) {
        var selected = runner.withPropertyValues("spring.datasource.hikari.validation-timeout=9000",
                "spring.datasource.hikari.data-source-properties.socketTimeout=0");
        if (!enabled.equals("DEFAULT")) selected = selected.withPropertyValues("app.operator-api.enabled=" + enabled);
        if (!access.equals("DEFAULT")) selected = selected.withPropertyValues("app.operator-api.access=" + access);
        selected.run(context -> {
            assertThat(context).hasNotFailed().doesNotHaveBean("cpiReadOnlyJdbcLimits");
            var pool = context.getBean(HikariDataSource.class);
            assertThat(pool.getJdbcUrl()).isEqualTo(URL);
            assertThat(pool.getDataSourceProperties()).containsOnlyKeys("socketTimeout");
            assertThat(pool.getDataSourceProperties().getProperty("socketTimeout")).isEqualTo("0");
            assertThat(pool.getValidationTimeout()).isEqualTo(9000);
        });
    }

    @ParameterizedTest @CsvSource({"0,2,5,1", "1,1,1,1", "2,2,2,1", "99,2,5,1"})
    void actualPropertyBindingCapsDefaultsAndPreservesShorterBudgets(int input, int connect, int socket, int cancel) {
        reader().withPropertyValues("spring.datasource.hikari.data-source-properties.connectTimeout=" + input,
                        "spring.datasource.hikari.data-source-properties.socketTimeout=" + input,
                        "spring.datasource.hikari.data-source-properties.cancelSignalTimeout=" + input,
                        "spring.datasource.hikari.maximum-pool-size=7", "spring.datasource.hikari.minimum-idle=2")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    var pool = context.getBean(HikariDataSource.class);
                    assertLimits(pool, connect, socket, cancel);
                    assertThat(pool.getConnectionTimeout()).isEqualTo(1000);
                    assertThat(pool.getValidationTimeout()).isEqualTo(750);
                    assertThat(pool.getMaximumPoolSize()).isEqualTo(7);
                    assertThat(pool.getMinimumIdle()).isEqualTo(2);
                    assertThat(pool.getPassword()).isEqualTo("DisposableConfigOnly");
                    assertThat(pool.getDataSourceProperties()).doesNotContainKey("password");
                    assertThat(pool.getHikariPoolMXBean()).isNull();
                });
    }

    @ParameterizedTest @ValueSource(longs = {250, 500, 750, 5000})
    void existingValidationTimeoutIsNeverExtended(long input) {
        reader().withPropertyValues("spring.datasource.hikari.validation-timeout=" + input).run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context.getBean(HikariDataSource.class).getValidationTimeout()).isEqualTo(Math.min(input, 750));
        });
    }

    @Test void urlPrecedenceDuplicateKeysAndEncodedValuesUseTheRealDriverParser() {
        String suffix = "?socketTimeout=99&socketTimeout=%31&connectTimeout=0&cancelSignalTimeout=0"
                + "&sslmode=verify-full&ApplicationName=demo%20reader&password=Disposable%26UrlOnly";
        reader().withPropertyValues("spring.datasource.url=" + URL + suffix,
                "spring.datasource.hikari.data-source-properties.socketTimeout=4").run(context -> {
            assertThat(context).hasNotFailed();
            var pool = context.getBean(HikariDataSource.class);
            assertLimits(pool, 2, 1, 1);
            assertThat(pool.getJdbcUrl()).startsWith(URL + suffix + "&");
            assertThat(parsed(pool)).containsEntry("sslmode", "verify-full").containsEntry("ApplicationName", "demo reader")
                    .containsEntry("password", "Disposable&UrlOnly");
        });
    }

    @Test void unlimitedUrlIsCappedAfterUrlOverridesShorterProperties() {
        reader().withPropertyValues("spring.datasource.url=" + URL + "?socketTimeout=0",
                "spring.datasource.hikari.data-source-properties.socketTimeout=1").run(context -> {
            assertThat(context).hasNotFailed();
            assertLimits(context.getBean(HikariDataSource.class), 2, 5, 1);
        });
    }

    @ParameterizedTest @ValueSource(strings = {"-1", "bad-DisposableConfigOnly", "2147483648", ""})
    void invalidEffectiveValuesFailClosedWithoutTheirContents(String invalid) {
        reader().withPropertyValues("spring.datasource.url=" + URL + "?socketTimeout=" + invalid).run(context -> {
            assertThat(context).hasFailed();
            assertThat(context.getStartupFailure()).hasRootCauseMessage("CPI read-only JDBC socketTimeout configuration is invalid");
            var trace = new java.io.StringWriter();
            context.getStartupFailure().printStackTrace(new java.io.PrintWriter(trace));
            assertThat(trace.toString()).doesNotContain("DisposableConfigOnly", URL);
        });
    }

    @Test void normalEnvironmentAliasesEnableAllCaps() {
        runner.withInitializer(new ConfigDataApplicationContextInitializer())
                .withPropertyValues("spring.config.location=classpath:/application.yml", "spring.config.import=",
                        "OPERATOR_API_ENABLED=true", "OPERATOR_API_ACCESS=CPI_READ_ONLY").run(context -> {
                    assertThat(context).hasNotFailed();
                    assertLimits(context.getBean(HikariDataSource.class), 2, 5, 1);
                });
    }

    @ParameterizedTest @ValueSource(strings = {"connectTimeout", "socketTimeout", "cancelSignalTimeout"})
    void rejectsNonStringPropertiesBeforeMutatingThePool(String name) {
        try (var pool = new HikariDataSource()) {
            pool.setJdbcUrl(URL);
            pool.addDataSourceProperty(name, 99);
            assertThatThrownBy(() -> CpiReadOnlyJdbcConfiguration.cpiReadOnlyJdbcLimits()
                    .postProcessAfterInitialization(pool, "dataSource"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("CPI read-only JDBC " + name + " configuration is invalid");
            assertThat(pool.getJdbcUrl()).isEqualTo(URL);
            assertThat(pool.getValidationTimeout()).isEqualTo(5000);
            assertThat(pool.getDataSourceProperties()).containsEntry(name, 99);
        }
    }

    @Test void unrelatedPoolsAndDriversRemainUntouched() {
        var processor = CpiReadOnlyJdbcConfiguration.cpiReadOnlyJdbcLimits();
        try (var pool = new HikariDataSource()) {
            pool.setJdbcUrl(URL);
            processor.postProcessAfterInitialization(pool, "migrationDataSource");
            assertThat(pool.getJdbcUrl()).isEqualTo(URL);
            assertThat(pool.getValidationTimeout()).isEqualTo(5000);
            pool.setDataSourceClassName("org.postgresql.ds.PGSimpleDataSource");
            processor.postProcessAfterInitialization(pool, "dataSource");
            assertThat(pool.getJdbcUrl()).isEqualTo(URL);
            assertThat(pool.getValidationTimeout()).isEqualTo(5000);
            pool.setDataSourceClassName(null);
            pool.setJdbcUrl("jdbc:h2:mem:other");
            processor.postProcessAfterInitialization(pool, "dataSource");
            assertThat(pool.getDataSourceProperties()).isEmpty();
        }
        var other = mock(DataSource.class);
        assertThat(processor.postProcessAfterInitialization(other, "dataSource")).isSameAs(other);
        verifyNoInteractions(other);
    }

    private ApplicationContextRunner reader() {
        return runner.withPropertyValues("app.operator-api.enabled=true", "app.operator-api.access=CPI_READ_ONLY");
    }
    private static Properties parsed(HikariDataSource pool) {
        var input = new Properties();
        input.putAll(pool.getDataSourceProperties());
        input.setProperty("password", "DisposableParserOnly");
        return Driver.parseURL(pool.getJdbcUrl(), input);
    }
    private static void assertLimits(HikariDataSource pool, int connect, int socket, int cancel) {
        assertThat(parsed(pool)).containsEntry("connectTimeout", "" + connect).containsEntry("socketTimeout", "" + socket)
                .containsEntry("cancelSignalTimeout", "" + cancel);
    }
}
