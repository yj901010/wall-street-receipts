package com.wallstreetreceipts.api.infrastructure.provider.fixture;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import com.wallstreetreceipts.api.application.port.out.AnalystCallProvider;
import com.wallstreetreceipts.api.application.port.out.AnalystCallRepository;
import com.wallstreetreceipts.api.application.port.out.AnalystCallRevisionRepository;

@Component
@ConditionalOnProperty(name = "app.providers.analyst", havingValue = "fixture", matchIfMissing = true)
class FixtureAnalystCallImporter implements ApplicationRunner {

    private final AnalystCallProvider provider;
    private final AnalystCallRepository repository;
    private final AnalystCallRevisionRepository revisionRepository;

    FixtureAnalystCallImporter(
            AnalystCallProvider provider,
            AnalystCallRepository repository,
            AnalystCallRevisionRepository revisionRepository) {
        this.provider = provider;
        this.repository = repository;
        this.revisionRepository = revisionRepository;
    }

    @Override
    public void run(ApplicationArguments args) {
        var dataSet = provider.load();
        repository.importDataSet(dataSet);
        revisionRepository.importAll(dataSet.revisions());
    }
}
