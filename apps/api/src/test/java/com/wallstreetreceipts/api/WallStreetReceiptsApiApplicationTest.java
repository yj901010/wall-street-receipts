package com.wallstreetreceipts.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.ZoneOffset;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import com.wallstreetreceipts.api.application.port.out.MarketDataProvider;
import com.wallstreetreceipts.api.domain.market.DataMode;
import com.wallstreetreceipts.api.domain.market.MarketQuote;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class WallStreetReceiptsApiApplicationTest {

    @Autowired
    private Clock clock;

    @Autowired
    private MarketDataProvider marketDataProvider;

    @Autowired
    private MockMvc mockMvc;

    @Test
    void contextBootsWithoutVendorCredentials() {
        assertThat(clock.getZone()).isEqualTo(ZoneOffset.UTC);
        assertThat(marketDataProvider.providerName()).isEqualTo("fixture");
    }

    @Test
    void fixtureProviderMapsProviderDtoToCanonicalQuoteUsingBigDecimal() {
        MarketQuote quote = marketDataProvider.latestQuote("nvda").orElseThrow();

        assertThat(quote.price()).isEqualByComparingTo(new BigDecimal("183.42"));
        assertThat(quote.dataMode()).isEqualTo(DataMode.DEMO);
        assertThat(quote.processingTime()).isAfterOrEqualTo(quote.eventTime());
    }

    @Test
    void actuatorHealthEndpointIsAvailable() throws Exception {
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
    }
}
