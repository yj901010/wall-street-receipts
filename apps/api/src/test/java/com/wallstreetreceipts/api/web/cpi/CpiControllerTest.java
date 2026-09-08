package com.wallstreetreceipts.api.web.cpi;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import com.wallstreetreceipts.api.application.cpi.CpiRepository;
import com.wallstreetreceipts.api.infrastructure.provider.bls.BlsCpiParser;
import com.wallstreetreceipts.api.support.CpiTestFixture;

class CpiControllerTest {
    @Test void readOnlyStoredDataHasNoStoreAndExplicitNonPitMetadata() throws Exception {
        var repo = mock(CpiRepository.class); var now = Instant.parse("2026-09-08T01:00:00Z");
        var snapshot = new BlsCpiParser().parse(CpiTestFixture.bytes(), UUID.randomUUID(), now, 2023, 2026);
        when(repo.latest(now)).thenReturn(Optional.of(snapshot));
        var mvc = MockMvcBuilders.standaloneSetup(new CpiController(repo, Clock.fixed(now, ZoneOffset.UTC))).build();
        mvc.perform(get("/v1/macro/cpi")).andExpect(status().isOk()).andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.dataMode").value("OBSERVED_MONTHLY"))
                .andExpect(jsonPath("$.vintageStatus").value("RETRIEVAL_VINTAGE_ONLY"))
                .andExpect(jsonPath("$.releaseTimeStatus").value("NOT_PROVIDED"))
                .andExpect(jsonPath("$.series[0].observations[0].yearOverYearPct").value("3.1"))
                .andExpect(jsonPath("$.series[0].observations[1].index").isEmpty());
        verify(repo).latest(now); verifyNoMoreInteractions(repo);
        reset(repo);
        mvc.perform(get("/v1/macro/cpi?refresh=true")).andExpect(status().isBadRequest());
        mvc.perform(post("/v1/macro/cpi")).andExpect(status().isMethodNotAllowed());
        verifyNoInteractions(repo);
        when(repo.latest(now)).thenReturn(Optional.empty());
        mvc.perform(get("/v1/macro/cpi")).andExpect(status().isNotFound());
    }
}
