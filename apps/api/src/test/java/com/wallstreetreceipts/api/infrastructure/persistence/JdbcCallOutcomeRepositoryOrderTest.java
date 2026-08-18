package com.wallstreetreceipts.api.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.Collections;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wallstreetreceipts.api.infrastructure.provider.fixture.FixtureAnalystCallProvider;

class JdbcCallOutcomeRepositoryOrderTest {

    @Test
    void batchOrderAcquiresMethodologyLocksInOneGlobalOrderBeforeLineageOrder() {
        var outcomes = new ArrayList<>(new FixtureAnalystCallProvider(new ObjectMapper()).load().outcomes());
        Collections.reverse(outcomes);

        outcomes.sort(JdbcCallOutcomeRepository.IMPORT_ORDER);

        assertThat(outcomes)
                .extracting(outcome -> outcome.methodologyVersion() + ":" + outcome.horizon()
                        + ":" + outcome.sequenceNumber())
                .containsExactly("1.0.0:D1:1", "1.0.0:D1:2", "1.0.0:M1:1", "2.0.0:D1:1");
    }
}
