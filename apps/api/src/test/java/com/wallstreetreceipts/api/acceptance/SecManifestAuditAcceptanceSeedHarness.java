package com.wallstreetreceipts.api.acceptance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.Connection;
import java.time.Clock;
import java.time.ZoneOffset;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import javax.sql.DataSource;

import com.wallstreetreceipts.api.application.filinghistory.PersistFilingHistoryCollectionManifestService;
import com.wallstreetreceipts.api.application.filinghistory.SecFilingHistoryManifestAuditNotFoundException;
import com.wallstreetreceipts.api.application.filinghistory.SecFilingHistoryManifestAuditQueryService;
import com.wallstreetreceipts.api.application.port.out.FilingCatalogCaptureAppendResult;
import com.wallstreetreceipts.api.application.port.out.FilingCatalogCaptureRepository;
import com.wallstreetreceipts.api.application.port.out.FilingHistoryCollectionManifestAppendOutcome.Status;
import com.wallstreetreceipts.api.application.port.out.FilingHistoryCollectionManifestRepository;
import com.wallstreetreceipts.api.application.port.out.HistoricalFilingSegmentCaptureAppendResult;
import com.wallstreetreceipts.api.application.port.out.HistoricalFilingSegmentCaptureRepository;
import com.wallstreetreceipts.api.domain.filing.FilingCatalogCapture;
import com.wallstreetreceipts.api.domain.filing.HistoricalFilingSegmentCapture;
import com.wallstreetreceipts.api.support.SecManifestAuditDemoFixture;

/**
 * Explicit local-acceptance seeder. Its name intentionally does not match the
 * default Surefire test patterns and it refuses every non-isolated datasource.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@ActiveProfiles("acceptance")
@ContextConfiguration(initializers =
        SecManifestAuditAcceptanceSeedHarness.IsolatedTargetGuard.class)
@Import(SecManifestAuditAcceptanceSeedHarness.FixedAcceptanceClock.class)
class SecManifestAuditAcceptanceSeedHarness {

    private static final String ENABLE_PROPERTY =
            "wsr.sec-manifest-acceptance-seed";

    @Autowired
    private FilingCatalogCaptureRepository rootRepository;

    @Autowired
    private HistoricalFilingSegmentCaptureRepository segmentRepository;

    @Autowired
    private FilingHistoryCollectionManifestRepository manifestRepository;

    @Autowired
    private PersistFilingHistoryCollectionManifestService persistService;

    @Autowired
    private SecFilingHistoryManifestAuditQueryService queryService;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Autowired
    private DataSource dataSource;

    @Test
    void seedsOneExactSyntheticManifestThroughProductionRepositories() throws Exception {
        try (Connection connection = dataSource.getConnection()) {
            assertThat(connection.getMetaData().getURL())
                    .matches("jdbc:postgresql://127\\.0\\.0\\.1:[0-9]{4,5}/"
                            + "wsr_full_stack_acceptance");
            assertThat(connection.getCatalog()).isEqualTo("wsr_full_stack_acceptance");
            assertThat(connection.getMetaData().getUserName())
                    .isEqualTo("wsr_full_stack_acceptance");
        }
        TransactionTemplate transactions = new TransactionTemplate(transactionManager);
        transactions.executeWithoutResult(status -> {
            assertThat(rootRepository.count()).isZero();
            assertThat(segmentRepository.count()).isZero();
            assertThat(manifestRepository.count()).isZero();

            FilingCatalogCapture pendingRoot = SecManifestAuditDemoFixture.pendingRoot();
            assertThat(rootRepository.append(pendingRoot))
                    .isEqualTo(FilingCatalogCaptureAppendResult.INSERTED);
            FilingCatalogCapture durableRoot = rootRepository
                    .findByCaptureId(pendingRoot.captureId())
                    .orElseThrow();

            List<HistoricalFilingSegmentCapture> pendingSegments =
                    SecManifestAuditDemoFixture.pendingSegments(durableRoot);
            for (HistoricalFilingSegmentCapture pendingSegment : pendingSegments) {
                assertThat(segmentRepository.append(pendingSegment))
                        .isEqualTo(HistoricalFilingSegmentCaptureAppendResult.INSERTED);
            }
            List<HistoricalFilingSegmentCapture> durableSegments = pendingSegments.stream()
                    .map(segment -> segmentRepository
                            .findByCaptureId(segment.captureId())
                            .orElseThrow())
                    .toList();

            var append = persistService.persist(
                    durableRoot.captureId(),
                    SecManifestAuditDemoFixture.selections(durableSegments));
            assertThat(append.status()).isEqualTo(Status.INSERTED);
            SecManifestAuditDemoFixture.requireExpectedManifest(append.manifest());

            assertThat(rootRepository.count()).isEqualTo(1);
            assertThat(segmentRepository.count()).isEqualTo(2);
            assertThat(manifestRepository.count()).isEqualTo(1);
            assertThat(queryService.summary(
                    SecManifestAuditDemoFixture.MANIFEST_ID,
                    SecManifestAuditDemoFixture.ASSEMBLED_AT.toString()).manifest())
                    .isEqualTo(append.manifest());
            assertThatThrownBy(() -> queryService.summary(
                    SecManifestAuditDemoFixture.MANIFEST_ID,
                    SecManifestAuditDemoFixture.ASSEMBLED_AT
                            .minusNanos(1_000)
                            .toString()))
                    .isInstanceOf(SecFilingHistoryManifestAuditNotFoundException.class);
        });

        System.out.println(
                "SEC_MANIFEST_ACCEPTANCE_SEED|"
                        + SecManifestAuditDemoFixture.MANIFEST_ID
                        + "|"
                        + SecManifestAuditDemoFixture.ASSEMBLED_AT
                        + "|2|4|6");
    }

    static final class IsolatedTargetGuard
            implements ApplicationContextInitializer<ConfigurableApplicationContext> {

        private static final Pattern URL = Pattern.compile(
                "jdbc:postgresql://127\\.0\\.0\\.1:([0-9]{4,5})/"
                        + "wsr_full_stack_acceptance");
        private static final Pattern PASSWORD = Pattern.compile("[a-f0-9]{32}");

        @Override
        public void initialize(ConfigurableApplicationContext context) {
            require("true".equals(System.getProperty(ENABLE_PROPERTY)));
            String datasourceUrl = System.getenv("SPRING_DATASOURCE_URL");
            String flywayUrl = System.getenv("SPRING_FLYWAY_URL");
            Matcher url = URL.matcher(value(datasourceUrl));
            require(url.matches());
            int port = Integer.parseInt(url.group(1));
            require(port >= 1024 && port <= 65535);
            require(value(datasourceUrl).equals(value(flywayUrl)));
            require("wsr_full_stack_acceptance".equals(
                    System.getenv("SPRING_DATASOURCE_USERNAME")));
            require("wsr_full_stack_acceptance".equals(
                    System.getenv("SPRING_FLYWAY_USER")));
            require(PASSWORD.matcher(value(
                    System.getenv("SPRING_DATASOURCE_PASSWORD"))).matches());
            require(value(System.getenv("SPRING_DATASOURCE_PASSWORD")).equals(
                    value(System.getenv("SPRING_FLYWAY_PASSWORD"))));
            require("false".equals(System.getenv("SEC_PROVIDER_ENABLED")));
            require("false".equals(System.getenv("OPERATOR_API_ENABLED")));
            require("http://127.0.0.1:1".equals(System.getenv("SEC_BASE_URL")));
            require(value(System.getenv("SEC_CONTACT_EMAIL")).isEmpty());

            var environment = context.getEnvironment();
            require(value(datasourceUrl).equals(
                    environment.getProperty("spring.datasource.url")));
            require(value(flywayUrl).equals(
                    environment.getProperty("spring.flyway.url")));
            require("wsr_full_stack_acceptance".equals(
                    environment.getProperty("spring.datasource.username")));
            require("wsr_full_stack_acceptance".equals(
                    environment.getProperty("spring.flyway.user")));
            require(value(System.getenv("SPRING_DATASOURCE_PASSWORD")).equals(
                    environment.getProperty("spring.datasource.password")));
            require(value(System.getenv("SPRING_FLYWAY_PASSWORD")).equals(
                    environment.getProperty("spring.flyway.password")));
            require("false".equals(
                    environment.getProperty("app.public-data.sec.enabled")));
            require("http://127.0.0.1:1".equals(
                    environment.getProperty("app.public-data.sec.base-url")));
            require(value(environment.getProperty(
                    "app.public-data.sec.contact-email")).isEmpty());
            require("false".equals(
                    environment.getProperty("app.operator-api.enabled")));
        }

        private static String value(String value) {
            return value == null ? "" : value;
        }

        private static void require(boolean condition) {
            if (!condition) {
                throw new IllegalStateException(
                        "SEC manifest acceptance seeder refused the non-isolated target");
            }
        }
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class FixedAcceptanceClock {

        @Bean
        @Primary
        Clock acceptanceClock() {
            return Clock.fixed(
                    SecManifestAuditDemoFixture.ASSEMBLED_AT,
                    ZoneOffset.UTC);
        }
    }
}
