package com.wallstreetreceipts.api.web.filinghistory;

import java.net.URI;
import java.time.Instant;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.wallstreetreceipts.api.application.filinghistory.SecFilingHistoryManifestAuditQueryService.AuditPage;
import com.wallstreetreceipts.api.application.filinghistory.SecFilingHistoryManifestAuditQueryService.AuditResult;
import com.wallstreetreceipts.api.domain.filing.FilingHistoryCollectionManifest;
import com.wallstreetreceipts.api.domain.filing.FilingHistoryCollectionManifest.AccessionComparison;
import com.wallstreetreceipts.api.domain.filing.FilingHistoryCollectionManifest.DescriptorSelectionState;
import com.wallstreetreceipts.api.domain.filing.FilingHistoryCollectionManifest.OccurrenceSourceKind;
import com.wallstreetreceipts.api.domain.filing.FilingHistoryCollectionManifest.SelectionCoverage;
import com.wallstreetreceipts.api.domain.filing.HistoricalFilingSegmentDescriptor;

/** Closed response records for the exact SEC filing-history manifest audit API. */
public final class SecFilingHistoryManifestAuditResponses {

    public static final String AUDIT_SCHEMA_VERSION = "1.0.0";
    public static final String AUDIT_POLICY_VERSION =
            "SEC_EXACT_MANIFEST_AUDIT_V1";

    private SecFilingHistoryManifestAuditResponses() {
    }

    static Summary summary(AuditResult audit) {
        FilingHistoryCollectionManifest manifest = audit.manifest();
        return new Summary(
                AUDIT_SCHEMA_VERSION,
                AUDIT_POLICY_VERSION,
                audit.evaluationAsOf(),
                manifest.manifestId(),
                manifest.schemaVersion(),
                manifest.provider(),
                manifest.product(),
                manifest.policyVersion(),
                manifest.selectionSha256(),
                manifest.rootCaptureId(),
                manifest.rootCapturedAt(),
                manifest.cik(),
                manifest.evidenceAvailableAt(),
                manifest.assembledAt(),
                manifest.selectionCoverage(),
                manifest.advertisedDescriptorCount(),
                manifest.selectedDescriptorCount(),
                manifest.advertisedDescriptorCount()
                        - manifest.selectedDescriptorCount(),
                manifest.sourceOccurrenceCount(),
                manifest.distinctAccessionCount(),
                manifest.singleAccessionCount(),
                manifest.agreeingAccessionCount(),
                manifest.conflictingAccessionCount(),
                true,
                Disclosure.exactManifestBoundary());
    }

    static Page<Descriptor> descriptors(
            AuditPage<FilingHistoryCollectionManifest.DescriptorMember> page) {
        List<Descriptor> items = page.items().stream()
                .map(member -> {
                    HistoricalFilingSegmentDescriptor descriptor = member.descriptor();
                    return new Descriptor(
                            member.descriptorOrdinal(),
                            descriptor.fileName(),
                            descriptor.advertisedFilingCount(),
                            descriptor.advertisedFilingFrom(),
                            descriptor.advertisedFilingTo(),
                            member.selectionState(),
                            member.selectedSegmentCaptureId());
                })
                .toList();
        return page(page, items);
    }

    static Page<Accession> accessions(
            AuditPage<FilingHistoryCollectionManifest.AccessionGroup> page) {
        List<Accession> items = page.items().stream()
                .map(group -> new Accession(
                        group.groupOrdinal(),
                        group.accessionNumber(),
                        group.occurrenceCount(),
                        group.distinctProjectionCount(),
                        group.comparison()))
                .toList();
        return page(page, items);
    }

    static Page<Occurrence> occurrences(
            AuditPage<FilingHistoryCollectionManifest.FilingOccurrence> page) {
        FilingHistoryCollectionManifest manifest = page.audit().manifest();
        Map<String, Integer> groupOrdinalByAccession = new LinkedHashMap<>();
        for (FilingHistoryCollectionManifest.AccessionGroup group
                : manifest.accessionGroups()) {
            Integer previous = groupOrdinalByAccession.put(
                    group.accessionNumber(), group.groupOrdinal());
            if (previous != null) {
                throw new IllegalStateException(
                        "Verified manifest contains duplicate accession groups");
            }
        }
        List<Occurrence> items = page.items().stream()
                .map(occurrence -> occurrence(
                        manifest, occurrence, groupOrdinalByAccession))
                .toList();
        return page(page, items);
    }

    private static Occurrence occurrence(
            FilingHistoryCollectionManifest manifest,
            FilingHistoryCollectionManifest.FilingOccurrence occurrence,
            Map<String, Integer> groupOrdinalByAccession) {
        FilingHistoryCollectionManifest.OccurrenceProjection projection =
                occurrence.projection();
        Integer groupOrdinal = groupOrdinalByAccession.get(
                projection.accessionNumber());
        if (groupOrdinal == null) {
            throw new IllegalStateException(
                    "Verified manifest occurrence has no accession group");
        }
        String sourceCaptureId = switch (occurrence.sourceKind()) {
            case ROOT_RECENT -> manifest.rootCaptureId();
            case HISTORICAL_SEGMENT -> occurrence.segmentCaptureId();
        };
        return new Occurrence(
                occurrence.occurrenceOrdinal(),
                groupOrdinal,
                occurrence.sourceKind(),
                sourceCaptureId,
                occurrence.descriptorOrdinal(),
                occurrence.sourceOrdinal(),
                occurrence.projectionSha256(),
                projection.providerEventId(),
                projection.accessionNumber(),
                projection.form(),
                projection.filingDate(),
                projection.reportDate(),
                projection.acceptedAt(),
                projection.primaryDocumentUri());
    }

    private static <S, T> Page<T> page(AuditPage<S> source, List<T> items) {
        AuditResult audit = source.audit();
        return new Page<>(
                AUDIT_SCHEMA_VERSION,
                AUDIT_POLICY_VERSION,
                audit.manifest().manifestId(),
                audit.evaluationAsOf(),
                items,
                new PageMetadata(
                        source.number(),
                        source.size(),
                        source.totalElements(),
                        source.totalPages(),
                        source.first(),
                        source.last(),
                        new Order(source.orderField(), Direction.ASC)));
    }

    public record Summary(
            String auditSchemaVersion,
            String auditPolicyVersion,
            Instant evaluationAsOf,
            String manifestId,
            String manifestSchemaVersion,
            String provider,
            String product,
            String policyVersion,
            String selectionSha256,
            String rootCaptureId,
            Instant rootCapturedAt,
            String cik,
            Instant evidenceAvailableAt,
            Instant assembledAt,
            SelectionCoverage selectionCoverage,
            int advertisedDescriptorCount,
            int selectedDescriptorCount,
            int omittedDescriptorCount,
            long sourceOccurrenceCount,
            long distinctAccessionCount,
            long singleSourceAccessionCount,
            long exactAgreementAccessionCount,
            long canonicalConflictAccessionCount,
            boolean immutable,
            Disclosure disclosure) {
    }

    public record Disclosure(
            CoverageScope coverageScope,
            AtomicSecSnapshotClaim atomicSecSnapshotClaim,
            ResolutionStatus currentHistoryStatus,
            ResolutionStatus correctionRemovalStatus,
            ResolutionStatus amendmentLinkageStatus,
            LegalAuthorityStatus legalAuthorityStatus) {

        private static Disclosure exactManifestBoundary() {
            return new Disclosure(
                    CoverageScope.ROOT_RELATIVE_SELECTED_REFERENCES_ONLY,
                    AtomicSecSnapshotClaim.NOT_MADE,
                    ResolutionStatus.NOT_RESOLVED,
                    ResolutionStatus.NOT_RESOLVED,
                    ResolutionStatus.NOT_RESOLVED,
                    LegalAuthorityStatus.NOT_CLAIMED);
        }
    }

    public record Page<T>(
            String auditSchemaVersion,
            String auditPolicyVersion,
            String manifestId,
            Instant evaluationAsOf,
            List<T> items,
            PageMetadata page) {
    }

    public record PageMetadata(
            int number,
            int size,
            long totalElements,
            int totalPages,
            boolean first,
            boolean last,
            Order order) {
    }

    public record Order(String field, Direction direction) {
    }

    public record Descriptor(
            int descriptorOrdinal,
            String fileName,
            long advertisedFilingCount,
            LocalDate advertisedFilingFrom,
            LocalDate advertisedFilingTo,
            DescriptorSelectionState selectionState,
            String selectedSegmentCaptureId) {
    }

    public record Accession(
            int groupOrdinal,
            String accessionNumber,
            long occurrenceCount,
            long distinctProjectionCount,
            AccessionComparison comparison) {
    }

    public record Occurrence(
            int occurrenceOrdinal,
            int groupOrdinal,
            OccurrenceSourceKind sourceKind,
            String sourceCaptureId,
            Integer descriptorOrdinal,
            int sourceRowOrdinal,
            String projectionSha256,
            String providerEventId,
            String accessionNumber,
            String form,
            LocalDate filingDate,
            LocalDate reportDate,
            Instant acceptedAt,
            URI primaryDocumentUri) {
    }

    public enum CoverageScope {
        ROOT_RELATIVE_SELECTED_REFERENCES_ONLY
    }

    public enum AtomicSecSnapshotClaim {
        NOT_MADE
    }

    public enum ResolutionStatus {
        NOT_RESOLVED
    }

    public enum LegalAuthorityStatus {
        NOT_CLAIMED
    }

    public enum Direction {
        ASC
    }
}
