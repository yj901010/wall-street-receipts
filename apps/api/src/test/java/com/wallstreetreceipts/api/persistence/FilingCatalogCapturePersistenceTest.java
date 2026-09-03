package com.wallstreetreceipts.api.persistence;

import static com.wallstreetreceipts.api.support.SecFilingCatalogCaptureTestFixture.CIK;
import static com.wallstreetreceipts.api.support.SecFilingCatalogCaptureTestFixture.PARSER_VERSION;
import static com.wallstreetreceipts.api.support.SecFilingCatalogCaptureTestFixture.PRODUCT;
import static com.wallstreetreceipts.api.support.SecFilingCatalogCaptureTestFixture.PROVIDER;
import static com.wallstreetreceipts.api.support.SecFilingCatalogCaptureTestFixture.capture;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.lang.reflect.Modifier;
import java.net.URI;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.Arrays;
import java.util.HexFormat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import com.wallstreetreceipts.api.application.port.out.FilingCatalogCaptureAppendResult;
import com.wallstreetreceipts.api.application.port.out.FilingCatalogCaptureReplayVerifier;
import com.wallstreetreceipts.api.application.port.out.FilingCatalogCaptureRepository;
import com.wallstreetreceipts.api.domain.filing.FilingCatalog;
import com.wallstreetreceipts.api.domain.filing.FilingCatalogCapture;
import com.wallstreetreceipts.api.domain.filing.FilingRecord;
import com.wallstreetreceipts.api.domain.source.SourceResponseReceipt;
import com.wallstreetreceipts.api.domain.source.SourceResponseReceipt.BodyRetention;
import com.wallstreetreceipts.api.infrastructure.provider.sec.SecResponseSizeLimitInterceptor;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class FilingCatalogCapturePersistenceTest {

    private static final Instant FIRST_CAPTURE =
            Instant.parse("2026-08-25T01:02:03.123456Z");

    @Autowired
    private FilingCatalogCaptureRepository repository;

    @Autowired
    private FilingCatalogCaptureReplayVerifier replayVerifier;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Test
    void appendsAndReconstructsExactBodyReceiptAndProviderOrder() {
        FilingCatalogCapture pending = capture(FIRST_CAPTURE);

        assertThat(repository.append(pending))
                .isEqualTo(FilingCatalogCaptureAppendResult.INSERTED);

        FilingCatalogCapture stored = repository.findByCaptureId(
                pending.captureId()).orElseThrow();
        assertThat(stored)
                .isEqualTo(pending.withBodyRetention(
                        BodyRetention.DURABLE_DECODED_BODY_RETAINED));
        assertThat(stored.decodedBody()).containsExactly(pending.decodedBody());
        assertThat(stored.catalog().recentFilings())
                .extracting(FilingRecord::accessionNumber)
                .containsExactly(
                        "0000320193-26-000001",
                        "0000320193-26-000002");
        assertThat(stored.catalog().recentFilings().getLast().reportDate()).isNull();
        assertThat(stored.catalog().historicalSegments())
                .extracting(segment -> segment.fileName())
                .containsExactly(
                        "CIK0000320193-submissions-002.json",
                        "CIK0000320193-submissions-001.json");
    }

    @Test
    void exactReplayIsANoOpAndLaterObservationOfSameBodyIsAppended() {
        FilingCatalogCapture first = capture(FIRST_CAPTURE);
        FilingCatalogCapture later = capture(FIRST_CAPTURE.plusSeconds(60));

        assertThat(repository.append(first))
                .isEqualTo(FilingCatalogCaptureAppendResult.INSERTED);
        assertThat(repository.append(first))
                .isEqualTo(FilingCatalogCaptureAppendResult.IDENTICAL_REPLAY);
        assertThat(repository.append(later))
                .isEqualTo(FilingCatalogCaptureAppendResult.INSERTED);

        assertThat(repository.count()).isEqualTo(2);
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM sec_decoded_response_bodies", Long.class))
                .isEqualTo(1);
    }

    @Test
    void callerCannotClaimDurabilityBeforeTheRepositoryCommits() {
        FilingCatalogCapture durableClaim = capture(FIRST_CAPTURE).withBodyRetention(
                BodyRetention.DURABLE_DECODED_BODY_RETAINED);

        assertThatThrownBy(() -> repository.append(durableClaim))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("append requires a decoded body pending durable persistence");
        assertThat(repository.count()).isZero();
    }

    @Test
    void sameNaturalCaptureWithDifferentExactBodyFailsClosed() {
        FilingCatalogCapture original = capture(FIRST_CAPTURE, "10-Q");
        FilingCatalogCapture conflicting = capture(FIRST_CAPTURE, "10-Q/A");

        assertThat(repository.append(original))
                .isEqualTo(FilingCatalogCaptureAppendResult.INSERTED);
        assertThatThrownBy(() -> repository.append(conflicting))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("natural capture identity");
        assertThat(repository.count()).isEqualTo(1);
    }

    @Test
    void pointInTimeLookupNeverUsesAFutureCaptureOrAnotherParser() {
        FilingCatalogCapture first = capture(FIRST_CAPTURE);
        FilingCatalogCapture later = capture(FIRST_CAPTURE.plusSeconds(60));
        repository.append(first);
        repository.append(later);

        assertThat(repository.findLatestAtOrBefore(
                PROVIDER, PRODUCT, CIK, FIRST_CAPTURE.minusNanos(1_000), PARSER_VERSION))
                .isEmpty();
        assertThat(repository.findLatestAtOrBefore(
                PROVIDER, PRODUCT, CIK, FIRST_CAPTURE, PARSER_VERSION))
                .get()
                .extracting(FilingCatalogCapture::captureId)
                .isEqualTo(first.captureId());
        assertThat(repository.findLatestAtOrBefore(
                PROVIDER, PRODUCT, CIK, FIRST_CAPTURE.plusSeconds(30), PARSER_VERSION))
                .get()
                .extracting(FilingCatalogCapture::captureId)
                .isEqualTo(first.captureId());
        assertThat(repository.findLatestAtOrBefore(
                PROVIDER, PRODUCT, CIK, later.catalog().capturedAt(), PARSER_VERSION))
                .get()
                .extracting(FilingCatalogCapture::captureId)
                .isEqualTo(later.captureId());
        assertThat(repository.findLatestAtOrBefore(
                PROVIDER, PRODUCT, CIK, later.catalog().capturedAt(), "UNKNOWN_PARSER"))
                .isEmpty();
    }

    @Test
    void rawBodyReplayRejectsAProjectionThatWasNotParsedFromThoseBytes() {
        FilingCatalogCapture exact = capture(FIRST_CAPTURE);
        FilingCatalog catalog = exact.catalog();
        FilingRecord original = catalog.recentFilings().getFirst();
        FilingRecord altered = new FilingRecord(
                original.providerEventId(), original.accessionNumber(), "ALTERED",
                original.filingDate(), original.reportDate(), original.acceptedAt(),
                original.primaryDocumentUri());
        FilingCatalog alteredCatalog = new FilingCatalog(
                catalog.provider(), catalog.product(), catalog.cik(), catalog.sourceUri(),
                catalog.processingTime(), catalog.capturedAt(), catalog.sourceReceipt(),
                java.util.List.of(altered, catalog.recentFilings().getLast()),
                catalog.historicalSegments());
        FilingCatalogCapture inconsistent = new FilingCatalogCapture(
                alteredCatalog, exact.decodedBody());

        assertThatThrownBy(() -> repository.append(inconsistent))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("filing catalog capture does not match exact-body replay");
        assertThat(repository.count()).isZero();
    }

    @Test
    void replayRejectsInvalidSourceEnvelopesAndNonUtf8Bodies() {
        FilingCatalogCapture exact = capture(FIRST_CAPTURE);
        FilingCatalogCapture nonOfficial = withEnvelope(
                exact,
                URI.create("https://evil.example/submissions/CIK" + CIK + ".json"),
                exact.catalog().sourceReceipt().mediaType(),
                exact.decodedBody());
        FilingCatalogCapture nonJson = withEnvelope(
                exact,
                exact.catalog().sourceUri(),
                "text/plain;charset=UTF-8",
                exact.decodedBody());
        byte[] malformedUtf8 = exact.decodedBody();
        malformedUtf8[0] = (byte) 0xc3;
        malformedUtf8[1] = (byte) 0x28;
        FilingCatalogCapture nonUtf8 = withEnvelope(
                exact,
                exact.catalog().sourceUri(),
                exact.catalog().sourceReceipt().mediaType(),
                malformedUtf8);

        assertThatThrownBy(() -> replayVerifier.verify(nonOfficial))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("unsupported filing catalog capture source envelope");
        assertThatThrownBy(() -> replayVerifier.verify(nonJson))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("unsupported filing catalog capture source envelope");
        assertThatThrownBy(() -> replayVerifier.verify(nonUtf8))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("filing catalog capture could not be replayed");
    }

    @Test
    void appendRejectsAnOversizedBodyBeforeAnyDatabaseWrite() {
        FilingCatalogCapture exact = capture(FIRST_CAPTURE);
        byte[] original = exact.decodedBody();
        byte[] oversized = Arrays.copyOf(
                original,
                Math.toIntExact(
                        SecResponseSizeLimitInterceptor.MAX_DECOMPRESSED_RESPONSE_BYTES + 1));
        Arrays.fill(oversized, original.length, oversized.length, (byte) ' ');
        FilingCatalogCapture oversizedCapture = withEnvelope(
                exact,
                exact.catalog().sourceUri(),
                exact.catalog().sourceReceipt().mediaType(),
                oversized);

        assertThatThrownBy(() -> repository.append(oversizedCapture))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("unsupported filing catalog capture source envelope");
        assertThat(repository.count()).isZero();
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM sec_decoded_response_bodies", Long.class))
                .isZero();
    }

    @Test
    void readFailsClosedWhenStoredBodyBytesAreTamperedWithoutChangingLength() {
        FilingCatalogCapture capture = capture(FIRST_CAPTURE);
        repository.append(capture);
        byte[] tampered = capture.decodedBody();
        tampered[0] = (byte) (tampered[0] == '{' ? '[' : '{');
        jdbc.update(
                """
                        UPDATE sec_decoded_response_bodies
                        SET decoded_body = ?
                        WHERE decoded_body_sha256 = ?
                        """,
                tampered,
                capture.catalog().sourceReceipt().decodedBodySha256());

        assertThatThrownBy(() -> repository.findByCaptureId(capture.captureId()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("decodedBody digest must match sourceReceipt");
    }

    @Test
    void readFailsClosedWhenStoredChildCountIsTampered() {
        FilingCatalogCapture capture = capture(FIRST_CAPTURE);
        repository.append(capture);
        jdbc.update(
                """
                        UPDATE sec_filing_catalog_captures
                        SET recent_filing_count = recent_filing_count - 1
                        WHERE capture_id = ?
                        """,
                capture.captureId());

        assertThatThrownBy(() -> repository.findByCaptureId(capture.captureId()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("filing catalog capture child count does not match its root receipt");
    }

    @Test
    void failedChildInsertRollsBackBodyAndRootAtomically() {
        FilingCatalogCapture oversizedForm = capture(FIRST_CAPTURE, "X".repeat(129));
        TransactionTemplate requiresNew = new TransactionTemplate(transactionManager);
        requiresNew.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);

        assertThatThrownBy(() -> requiresNew.executeWithoutResult(
                status -> repository.append(oversizedForm)))
                .isInstanceOf(RuntimeException.class);
        assertThat(repository.count()).isZero();
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM sec_decoded_response_bodies", Long.class))
                .isZero();
    }

    @Test
    void repositoryAndCaptureExposeNoMutationSurface() {
        assertThat(Arrays.stream(FilingCatalogCaptureRepository.class.getMethods())
                .map(java.lang.reflect.Method::getName)
                .noneMatch(name -> name.startsWith("update") || name.startsWith("delete")))
                .isTrue();
        assertThat(Arrays.stream(FilingCatalogCapture.class.getDeclaredFields())
                .filter(field -> !field.isSynthetic())
                .filter(field -> !Modifier.isStatic(field.getModifiers()))
                .allMatch(field -> Modifier.isPrivate(field.getModifiers())
                        && Modifier.isFinal(field.getModifiers())))
                .isTrue();
    }

    private static FilingCatalogCapture withEnvelope(
            FilingCatalogCapture source,
            URI sourceUri,
            String mediaType,
            byte[] decodedBody) {
        FilingCatalog catalog = source.catalog();
        SourceResponseReceipt sourceReceipt = catalog.sourceReceipt();
        SourceResponseReceipt receipt = new SourceResponseReceipt(
                sourceReceipt.provider(),
                sourceReceipt.product(),
                sourceUri,
                sourceReceipt.httpStatus(),
                mediaType,
                sourceReceipt.transportContentEncoding(),
                sourceReceipt.etag(),
                sourceReceipt.lastModified(),
                sourceReceipt.parserVersion(),
                sha256(decodedBody),
                decodedBody.length,
                sourceReceipt.capturedAt(),
                sourceReceipt.bodyRepresentation(),
                sourceReceipt.bodyRetention());
        FilingCatalog alteredCatalog = new FilingCatalog(
                catalog.provider(),
                catalog.product(),
                catalog.cik(),
                sourceUri,
                catalog.processingTime(),
                catalog.capturedAt(),
                receipt,
                catalog.recentFilings(),
                catalog.historicalSegments());
        return new FilingCatalogCapture(alteredCatalog, decodedBody);
    }

    private static String sha256(byte[] body) {
        try {
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(body));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }
}
