package com.wallstreetreceipts.api.config;

import com.zaxxer.hikari.HikariDataSource;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Bound the normal API pool only in an explicitly enabled CPI reader process. */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(prefix = "app.operator-api", name = "access", havingValue = "CPI_READ_ONLY")
public class CpiReadOnlyPoolConfiguration {
    @Bean
    @ConditionalOnProperty(prefix = "app.operator-api", name = "enabled", havingValue = "true")
    static BeanPostProcessor cpiReadOnlyPoolWaitLimit() {
        return new BeanPostProcessor() {
            @Override public Object postProcessAfterInitialization(Object bean, String beanName) {
                // After Boot's datasource property binding; preserve tighter existing limits.
                // Other pools, pool capacity, validation and driver/socket settings are untouched.
                if ("dataSource".equals(beanName) && bean instanceof HikariDataSource pool) {
                    pool.setConnectionTimeout(Math.min(pool.getConnectionTimeout(), 1_000L));
                }
                return bean;
            }
        };
    }
}
