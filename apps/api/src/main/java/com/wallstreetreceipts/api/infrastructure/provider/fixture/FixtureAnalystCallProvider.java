package com.wallstreetreceipts.api.infrastructure.provider.fixture;

import java.io.IOException;
import java.io.InputStream;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wallstreetreceipts.api.application.port.out.AnalystCallDataSet;
import com.wallstreetreceipts.api.application.port.out.AnalystCallProvider;
import com.wallstreetreceipts.api.infrastructure.provider.fixture.FixtureAnalystCallDocuments.AnalystCallsDocument;
import com.wallstreetreceipts.api.infrastructure.provider.fixture.FixtureAnalystCallDocuments.AnalystCallRevisionsDocument;
import com.wallstreetreceipts.api.infrastructure.provider.fixture.FixtureAnalystCallDocuments.MasterDataDocument;
import com.wallstreetreceipts.api.infrastructure.provider.fixture.FixtureAnalystCallDocuments.MarketSnapshotsDocument;

@Component
@ConditionalOnProperty(name = "app.providers.analyst", havingValue = "fixture", matchIfMissing = true)
public final class FixtureAnalystCallProvider implements AnalystCallProvider {

    private static final String PROVIDER_NAME = "fixture";
    private static final String MASTER_DATA = "fixtures/v1/master-data.json";
    private static final String ANALYST_CALLS = "fixtures/v1/analyst-calls.json";
    private static final String ANALYST_CALL_REVISIONS = "fixtures/v1/analyst-call-revisions.json";
    private static final String MARKET_SNAPSHOTS = "fixtures/v1/market-snapshots.json";

    private final ObjectMapper objectMapper;
    private volatile AnalystCallDataSet cached;

    public FixtureAnalystCallProvider(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public AnalystCallDataSet load() {
        AnalystCallDataSet current = cached;
        if (current != null) {
            return current;
        }

        synchronized (this) {
            if (cached == null) {
                cached = FixtureAnalystCallMapper.toCanonical(
                        read(MASTER_DATA, MasterDataDocument.class),
                        read(ANALYST_CALLS, AnalystCallsDocument.class),
                        read(ANALYST_CALL_REVISIONS, AnalystCallRevisionsDocument.class),
                        read(MARKET_SNAPSHOTS, MarketSnapshotsDocument.class));
            }
            return cached;
        }
    }

    @Override
    public String providerName() {
        return PROVIDER_NAME;
    }

    private <T> T read(String path, Class<T> type) {
        ClassPathResource resource = new ClassPathResource(path);
        try (InputStream input = resource.getInputStream()) {
            return objectMapper.readValue(input, type);
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to load fixture resource: " + path, exception);
        }
    }
}
