package com.wallstreetreceipts.api.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import com.wallstreetreceipts.api.application.cpi.CpiDeadlineReader;
import com.wallstreetreceipts.api.application.cpi.CpiRepository;

/** Only the dedicated read-only CPI query port is decorated; collection and public repository calls are not. */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(prefix = "app.operator-api", name = "access", havingValue = "CPI_READ_ONLY")
public class CpiReadOnlyQueryConfiguration {
    @Bean(destroyMethod = "close") @Primary
    @ConditionalOnProperty(prefix = "app.operator-api", name = "enabled", havingValue = "true")
    CpiDeadlineReader cpiDeadlineReader(CpiRepository repository) { return new CpiDeadlineReader(repository); }
}
