package com.wallstreetreceipts.api.config;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Properties;
import com.zaxxer.hikari.HikariDataSource;
import org.postgresql.Driver;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Transport policy for the normal PostgreSQL pool in an explicitly enabled CPI reader process. */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(prefix = "app.operator-api", name = "access", havingValue = "CPI_READ_ONLY")
public class CpiReadOnlyJdbcConfiguration {
    private static final Map<String, Integer> LIMITS = Map.of(
            "connectTimeout", 2, "socketTimeout", 5, "cancelSignalTimeout", 1);

    @Bean
    @ConditionalOnProperty(prefix = "app.operator-api", name = "enabled", havingValue = "true")
    static BeanPostProcessor cpiReadOnlyJdbcLimits() {
        return new BeanPostProcessor() {
            @Override public Object postProcessAfterInitialization(Object bean, String beanName) {
                if ("dataSource".equals(beanName) && bean instanceof HikariDataSource pool
                        && pool.getDataSource() == null && pool.getDataSourceClassName() == null
                        && pool.getJdbcUrl() != null && pool.getJdbcUrl().startsWith("jdbc:postgresql:")) {
                    bound(pool);
                }
                return bean;
            }
        };
    }

    private static void bound(HikariDataSource pool) {
        var supplied = pool.getDataSourceProperties();
        var input = new Properties();
        for (String name : supplied.stringPropertyNames()) input.setProperty(name, supplied.getProperty(name));
        // Parse without opening a connection or implicitly looking up .pgpass; never apply this placeholder.
        input.setProperty("password", "");
        String original = pool.getJdbcUrl();
        var effective = Driver.parseURL(original, input);
        if (effective == null) throw invalid("URL");
        var bounded = new LinkedHashMap<String, String>();
        for (String name : new String[] {"connectTimeout", "socketTimeout", "cancelSignalTimeout"}) {
            if (supplied.containsKey(name) && !(supplied.get(name) instanceof String)) throw invalid(name);
            bounded.put(name, Integer.toString(seconds(effective.getProperty(name), LIMITS.get(name), name)));
        }
        // pgJDBC gives URL values priority over properties; the last duplicate URL key wins.
        // Keep every original byte (including TLS/host options) and append only these fixed keys.
        var url = new StringBuilder(original).append(original.contains("?") ? '&' : '?');
        bounded.forEach((name, value) -> url.append(name).append('=').append(value).append('&'));
        url.setLength(url.length() - 1);
        pool.setJdbcUrl(url.toString());
        bounded.forEach(pool::addDataSourceProperty);
        // Hikari temporarily replaces the driver's socket timeout during connection validation.
        pool.setValidationTimeout(Math.min(pool.getValidationTimeout(), 750L));
    }

    private static int seconds(String value, int maximum, String name) {
        if (value == null) return maximum;
        try {
            int configured = Integer.parseInt(value);
            if (configured < 0) throw invalid(name);
            return configured == 0 ? maximum : Math.min(configured, maximum);
        } catch (NumberFormatException ignored) {
            throw invalid(name); // Do not include the URL, supplied value, credentials or parser cause.
        }
    }

    private static IllegalArgumentException invalid(String setting) {
        return new IllegalArgumentException("CPI read-only JDBC " + setting + " configuration is invalid");
    }
}
