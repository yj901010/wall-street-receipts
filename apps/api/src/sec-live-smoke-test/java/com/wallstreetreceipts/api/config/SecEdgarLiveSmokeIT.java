package com.wallstreetreceipts.api.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.net.URI;
import java.time.Clock;
import java.util.Arrays;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

import com.wallstreetreceipts.api.domain.filing.FilingCatalog;
import com.wallstreetreceipts.api.infrastructure.provider.sec.SecEdgarFilingCatalogProvider;

class SecEdgarLiveSmokeIT {

    private static final String PROFILE_MARKER_PROPERTY = "sec.live-smoke.profile";
    private static final String OPT_IN_ENVIRONMENT_VARIABLE = "SEC_LIVE_SMOKE";
    private static final String APPLE_CIK = "0000320193";
    private static final URI OFFICIAL_SOURCE_URI =
            URI.create("https://data.sec.gov/submissions/CIK0000320193.json");

    @Test
    void loadsOneOfficialAppleCatalogOnlyAfterBothExplicitOptIns() {
        requireExplicitOptIn();

        new ApplicationContextRunner()
                .withInitializer(new ConfigDataApplicationContextInitializer())
                .withUserConfiguration(SecEdgarConfiguration.class, LiveSmokeDependencies.class)
                .withPropertyValues(
                        "app.public-data.sec.enabled=true",
                        "app.public-data.sec.base-url=https://data.sec.gov")
                .run(context -> {
                    if (context.getStartupFailure() != null) {
                        fail("SEC live smoke context could not start; verify the local SEC contact configuration");
                    }
                    assertTrue(
                            Arrays.asList(context.getEnvironment().getActiveProfiles()).contains("local"),
                            "SEC live smoke requires the local Spring profile");

                    SecEdgarFilingCatalogProvider provider =
                            context.getBean(SecEdgarFilingCatalogProvider.class);
                    FilingCatalog catalog = provider.loadRecentFilings(APPLE_CIK);

                    assertTrue(
                            APPLE_CIK.equals(catalog.cik()),
                            "SEC returned an unexpected canonical CIK");
                    assertTrue(
                            OFFICIAL_SOURCE_URI.equals(catalog.sourceUri()),
                            "SEC live smoke must use only the official submissions source");
                    assertTrue(
                            "https".equals(catalog.sourceUri().getScheme()),
                            "SEC source must use HTTPS");
                    assertTrue(
                            "data.sec.gov".equals(catalog.sourceUri().getHost()),
                            "SEC source must use the official data host");
                    assertTrue(
                            catalog.sourceUri().getPort() == -1,
                            "SEC source must use the default HTTPS port");
                    assertTrue(
                            catalog.sourceUri().getUserInfo() == null,
                            "SEC source must not contain user information");
                    assertTrue(
                            catalog.sourceUri().getQuery() == null,
                            "SEC source must not contain a query");
                    assertTrue(
                            catalog.sourceUri().getFragment() == null,
                            "SEC source must not contain a fragment");
                    assertFalse(
                            catalog.recentFilings().isEmpty(),
                            "SEC Apple catalog must contain recent filings");
                    assertFalse(
                            catalog.historicalSegments().isEmpty(),
                            "SEC Apple catalog must advertise historical segments");
                    assertEquals(
                            "SEC_SUBMISSIONS_CATALOG_V2",
                            catalog.sourceReceipt().parserVersion(),
                            "SEC live smoke must use the V2 catalog parser");
                    assertEquals(
                            FilingCatalog.HistoricalSegmentStatus
                                    .RECENT_ONLY_SEGMENTS_ADVERTISED_NOT_FETCHED,
                            catalog.historicalSegmentStatus(),
                            "SEC live smoke must not claim fetched or complete history");
                });
    }

    private static void requireExplicitOptIn() {
        assertEquals(
                "true",
                System.getProperty(PROFILE_MARKER_PROPERTY),
                "SEC live smoke requires the sec-live-smoke Maven profile");
        assertTrue(
                "true".equalsIgnoreCase(System.getenv(OPT_IN_ENVIRONMENT_VARIABLE)),
                "SEC live smoke requires SEC_LIVE_SMOKE=true");
    }

    @Configuration(proxyBeanMethods = false)
    static class LiveSmokeDependencies {

        @Bean
        RestClient.Builder restClientBuilder() {
            return RestClient.builder();
        }

        @Bean
        Clock clock() {
            return Clock.systemUTC();
        }
    }
}
