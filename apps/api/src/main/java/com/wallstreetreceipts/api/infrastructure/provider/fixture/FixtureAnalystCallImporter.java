package com.wallstreetreceipts.api.infrastructure.provider.fixture;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import com.wallstreetreceipts.api.application.port.out.AnalystCallProvider;
import com.wallstreetreceipts.api.application.port.out.AnalystCallRepository;

@Component
@ConditionalOnProperty(name = "app.providers.analyst", havingValue = "fixture", matchIfMissing = true)
class FixtureAnalystCallImporter implements ApplicationRunner {

    private final AnalystCallProvider provider;
    private final AnalystCallRepository repository;

    FixtureAnalystCallImporter(AnalystCallProvider provider, AnalystCallRepository repository) {
        this.provider = provider;
        this.repository = repository;
    }

    @Override
    public void run(ApplicationArguments args) {
        repository.importDataSet(provider.load());
    }
}
