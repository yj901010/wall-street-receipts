package com.wallstreetreceipts.api.infrastructure.provider.fixture;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.wallstreetreceipts.api.application.port.out.AnalystCallProvider;
import com.wallstreetreceipts.api.application.port.out.AnalystCallRepository;
import com.wallstreetreceipts.api.application.port.out.AnalystCallRevisionRepository;
import com.wallstreetreceipts.api.application.port.out.CallOutcomeRepository;
import com.wallstreetreceipts.api.application.port.out.ScoringMethodologyRepository;

@Component
@ConditionalOnProperty(name = "app.providers.analyst", havingValue = "fixture", matchIfMissing = true)
class FixtureAnalystCallImporter implements ApplicationRunner {

    private final AnalystCallProvider provider;
    private final AnalystCallRepository repository;
    private final AnalystCallRevisionRepository revisionRepository;
    private final ScoringMethodologyRepository methodologyRepository;
    private final CallOutcomeRepository outcomeRepository;

    FixtureAnalystCallImporter(
            AnalystCallProvider provider,
            AnalystCallRepository repository,
            AnalystCallRevisionRepository revisionRepository,
            ScoringMethodologyRepository methodologyRepository,
            CallOutcomeRepository outcomeRepository) {
        this.provider = provider;
        this.repository = repository;
        this.revisionRepository = revisionRepository;
        this.methodologyRepository = methodologyRepository;
        this.outcomeRepository = outcomeRepository;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        var dataSet = provider.load();
        repository.importDataSet(dataSet);
        revisionRepository.importAll(dataSet.revisions());
        methodologyRepository.importAll(dataSet.methodologies());
        outcomeRepository.importAll(dataSet.outcomes());
    }
}
