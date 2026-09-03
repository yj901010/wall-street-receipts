package com.wallstreetreceipts.api.application.filinghistory;

import static com.wallstreetreceipts.api.support.SecFilingCatalogCaptureTestFixture.capture;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

import com.wallstreetreceipts.api.application.filinghistory.SecFilingHistoryManifestAuditQueryService.AuditPage;
import com.wallstreetreceipts.api.application.filinghistory.SecFilingHistoryManifestAuditQueryService.AuditResult;
import com.wallstreetreceipts.api.application.port.out.FilingHistoryCollectionManifestRepository;
import com.wallstreetreceipts.api.domain.filing.FilingCatalogCapture;
import com.wallstreetreceipts.api.domain.filing.FilingHistoryCollectionManifest;
import com.wallstreetreceipts.api.domain.filing.HistoricalFilingSegmentCapture;
import com.wallstreetreceipts.api.domain.source.SourceResponseReceipt.BodyRetention;
import com.wallstreetreceipts.api.support.SecHistoricalFilingSegmentCaptureTestFixture;

class SecFilingHistoryManifestAuditQueryServiceTest {

    private static final Instant ROOT_CAPTURED_AT =
            Instant.parse("2026-08-25T01:02:03.123456Z");
    private static final Instant SEGMENT_CAPTURED_AT =
            Instant.parse("2026-08-25T01:12:03.123456Z");
    private static final Instant ASSEMBLED_AT =
            Instant.parse("2026-08-25T01:22:03.123456Z");
    private static final Instant EVALUATION_AS_OF =
            Instant.parse("2026-08-25T02:00:00.123456Z");

    private final FilingHistoryCollectionManifestRepository repository =
            mock(FilingHistoryCollectionManifestRepository.class);
    private final SecFilingHistoryManifestAuditQueryService service =
            new SecFilingHistoryManifestAuditQueryService(repository);

    private FilingHistoryCollectionManifest manifest;

    @BeforeEach
    void setUpManifest() {
        FilingCatalogCapture root = capture(ROOT_CAPTURED_AT).withBodyRetention(
                BodyRetention.DURABLE_DECODED_BODY_RETAINED);
        HistoricalFilingSegmentCapture segment =
                SecHistoricalFilingSegmentCaptureTestFixture.capture(
                        root, SEGMENT_CAPTURED_AT).withBodyRetention(
                                BodyRetention.DURABLE_DECODED_BODY_RETAINED);
        manifest = FilingHistoryCollectionManifest.assemble(
                root, List.of(segment), ASSEMBLED_AT);
    }

    @Test
    void returnsOnlyTheExactManifestVisibleAtTheExplicitCutoff() {
        when(repository.findByManifestIdAtOrBefore(
                manifest.manifestId(), EVALUATION_AS_OF))
                .thenReturn(Optional.of(manifest));

        AuditResult result = service.summary(
                manifest.manifestId(), EVALUATION_AS_OF.toString());

        assertThat(result.manifest()).isSameAs(manifest);
        assertThat(result.evaluationAsOf()).isEqualTo(EVALUATION_AS_OF);
        verify(repository).findByManifestIdAtOrBefore(
                manifest.manifestId(), EVALUATION_AS_OF);
    }

    @Test
    void absentAndFutureInvisibleManifestUseTheSameTypedFailure() {
        Instant beforeAssembly = ASSEMBLED_AT.minusNanos(1_000);
        String absentId = "f".repeat(64);
        when(repository.findByManifestIdAtOrBefore(absentId, EVALUATION_AS_OF))
                .thenReturn(Optional.empty());
        when(repository.findByManifestIdAtOrBefore(
                manifest.manifestId(), beforeAssembly))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.summary(
                absentId, EVALUATION_AS_OF.toString()))
                .isExactlyInstanceOf(
                        SecFilingHistoryManifestAuditNotFoundException.class)
                .hasMessage("SEC filing-history manifest was not found at the evaluation cutoff");
        assertThatThrownBy(() -> service.summary(
                manifest.manifestId(), beforeAssembly.toString()))
                .isExactlyInstanceOf(
                        SecFilingHistoryManifestAuditNotFoundException.class)
                .hasMessage("SEC filing-history manifest was not found at the evaluation cutoff");
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {
        "ABC",
        "FFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFF"
    })
    void malformedManifestIdDoesNotReachTheRepository(String manifestId) {
        assertThatThrownBy(() -> service.summary(
                manifestId, EVALUATION_AS_OF.toString()))
                .isExactlyInstanceOf(
                        InvalidSecFilingHistoryManifestAuditQueryException.class)
                .hasMessage("manifestId must be lowercase SHA-256 hex");

        verifyNoInteractions(repository);
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {
        "",
        "2026-08-25T02:00:00+00:00",
        "2026-08-25T02:00:00.1234567Z",
        "2026-08-25T02:00:00z",
        "2026-08-25T02:00Z",
        "2026-08-25T24:00:00Z",
        "2026-08-25T23:60:00Z",
        "2026-08-25T23:59:60Z",
        "2026-13-25T02:00:00Z"
    })
    void malformedEvaluationCutoffDoesNotReachTheRepository(String evaluationAsOf) {
        assertThatThrownBy(() -> service.summary(
                manifest.manifestId(), evaluationAsOf))
                .isExactlyInstanceOf(
                        InvalidSecFilingHistoryManifestAuditQueryException.class)
                .hasMessageContaining("evaluationAsOf");

        verifyNoInteractions(repository);
    }

    @Test
    void repositoryArgumentFailureAfterValidationIsAnInternalFailure() {
        when(repository.findByManifestIdAtOrBefore(
                manifest.manifestId(), EVALUATION_AS_OF))
                .thenThrow(new IllegalArgumentException("repository detail"));

        assertThatThrownBy(() -> service.summary(
                manifest.manifestId(), EVALUATION_AS_OF.toString()))
                .isExactlyInstanceOf(IllegalStateException.class)
                .hasMessage("Manifest repository rejected a validated audit lookup")
                .hasCauseInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void repositoryCannotSubstituteAnotherIdentityOrFutureEvidence() {
        FilingCatalogCapture otherRoot = capture(ROOT_CAPTURED_AT.plusSeconds(1))
                .withBodyRetention(BodyRetention.DURABLE_DECODED_BODY_RETAINED);
        FilingHistoryCollectionManifest otherManifest =
                FilingHistoryCollectionManifest.assemble(
                        otherRoot, List.of(), ASSEMBLED_AT.plusSeconds(1));
        when(repository.findByManifestIdAtOrBefore(
                manifest.manifestId(), EVALUATION_AS_OF))
                .thenReturn(Optional.of(otherManifest));

        assertThatThrownBy(() -> service.summary(
                manifest.manifestId(), EVALUATION_AS_OF.toString()))
                .isExactlyInstanceOf(IllegalStateException.class)
                .hasMessage(
                        "Manifest repository returned evidence outside the exact audit lookup");

        when(repository.findByManifestIdAtOrBefore(
                manifest.manifestId(), ASSEMBLED_AT.minusNanos(1_000)))
                .thenReturn(Optional.of(manifest));
        assertThatThrownBy(() -> service.summary(
                manifest.manifestId(),
                ASSEMBLED_AT.minusNanos(1_000).toString()))
                .isExactlyInstanceOf(IllegalStateException.class)
                .hasMessage(
                        "Manifest repository returned evidence outside the exact audit lookup");
    }

    @Test
    void descriptorPagingUsesBoundedDefaultsAndFixedOrdinalOrder() {
        when(repository.findByManifestIdAtOrBefore(
                manifest.manifestId(), EVALUATION_AS_OF))
                .thenReturn(Optional.of(manifest));

        AuditPage<FilingHistoryCollectionManifest.DescriptorMember> page =
                service.descriptors(
                        manifest.manifestId(),
                        EVALUATION_AS_OF.toString(),
                        null,
                        null);

        assertThat(page.items()).containsExactlyElementsOf(manifest.descriptors());
        assertThat(page.number()).isZero();
        assertThat(page.size()).isEqualTo(25);
        assertThat(page.totalElements()).isEqualTo(2);
        assertThat(page.totalPages()).isEqualTo(1);
        assertThat(page.first()).isTrue();
        assertThat(page.last()).isTrue();
        assertThat(page.orderField()).isEqualTo("descriptorOrdinal");
    }

    @Test
    void allChildCollectionsUseFixedOrderAndSupportMaxAndBeyondEndPages() {
        when(repository.findByManifestIdAtOrBefore(
                manifest.manifestId(), EVALUATION_AS_OF))
                .thenReturn(Optional.of(manifest));

        AuditPage<FilingHistoryCollectionManifest.AccessionGroup> accessions =
                service.accessions(
                        manifest.manifestId(),
                        EVALUATION_AS_OF.toString(),
                        "0",
                        "100");
        AuditPage<FilingHistoryCollectionManifest.FilingOccurrence> occurrences =
                service.occurrences(
                        manifest.manifestId(),
                        EVALUATION_AS_OF.toString(),
                        "4",
                        "1");

        assertThat(accessions.items())
                .containsExactlyElementsOf(manifest.accessionGroups());
        assertThat(accessions.size()).isEqualTo(100);
        assertThat(accessions.orderField()).isEqualTo("groupOrdinal");
        assertThat(occurrences.items()).isEmpty();
        assertThat(occurrences.totalElements()).isEqualTo(4);
        assertThat(occurrences.totalPages()).isEqualTo(4);
        assertThat(occurrences.first()).isFalse();
        assertThat(occurrences.last()).isTrue();
        assertThat(occurrences.orderField()).isEqualTo("occurrenceOrdinal");
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "+1", "01", "-1", "2147483648"})
    void noncanonicalOrUnsupportedPageDoesNotReachTheRepository(String page) {
        assertThatThrownBy(() -> service.descriptors(
                manifest.manifestId(),
                EVALUATION_AS_OF.toString(),
                page,
                null))
                .isExactlyInstanceOf(
                        InvalidSecFilingHistoryManifestAuditQueryException.class)
                .hasMessageContaining("page");

        verifyNoInteractions(repository);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "+1", "01", "0", "101", "2147483648"})
    void noncanonicalOrOutOfBoundsSizeDoesNotReachTheRepository(String size) {
        assertThatThrownBy(() -> service.descriptors(
                manifest.manifestId(),
                EVALUATION_AS_OF.toString(),
                null,
                size))
                .isExactlyInstanceOf(
                        InvalidSecFilingHistoryManifestAuditQueryException.class)
                .hasMessageContaining("size");

        verifyNoInteractions(repository);
    }
}
