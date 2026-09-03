package com.wallstreetreceipts.api.support;

import java.net.URI;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import com.wallstreetreceipts.api.application.filinghistory.PersistFilingHistoryCollectionManifestService.DescriptorCaptureSelection;
import com.wallstreetreceipts.api.domain.filing.FilingCatalogCapture;
import com.wallstreetreceipts.api.domain.filing.FilingHistoryCollectionManifest;
import com.wallstreetreceipts.api.domain.filing.HistoricalFilingRecord;
import com.wallstreetreceipts.api.domain.filing.HistoricalFilingSegmentCapture;
import com.wallstreetreceipts.api.domain.source.SourceResponseReceipt.BodyRetention;

/** Fixed synthetic evidence shared by Java parity and local acceptance only. */
public final class SecManifestAuditDemoFixture {

    public static final Instant ROOT_CAPTURED_AT =
            Instant.parse("2026-08-25T03:00:00.123456Z");
    public static final Instant FIRST_SEGMENT_CAPTURED_AT =
            Instant.parse("2026-08-25T03:10:00.123456Z");
    public static final Instant SECOND_SEGMENT_CAPTURED_AT =
            Instant.parse("2026-08-25T03:20:00.123456Z");
    public static final Instant ASSEMBLED_AT =
            Instant.parse("2026-08-25T03:30:00.123456Z");

    public static final String ROOT_CAPTURE_ID =
            "c9bfc935b27e059397531a4dda1a1a0222e98528c33e85b886c91ca6b74f2fa8";
    public static final String FIRST_SEGMENT_CAPTURE_ID =
            "b3c60bfac0a3e79a886ba795467a6ac2dfa8ac70266ec2913ec1a34e81bd55de";
    public static final String SECOND_SEGMENT_CAPTURE_ID =
            "5cf0f3062c34d4b4bf080c828483b4470d8202c6593c4b5591d7567aa2e53bc9";
    public static final String SELECTION_SHA256 =
            "eadb0c3bf6efb9b3323be1342d0b17e63631b706f088b23fa78e784e1b547acd";
    public static final String MANIFEST_ID =
            "cda6762d385d4e889294d0fec1f7a2a7b20c5157cf67c832b7d7f4857550a1cd";

    private SecManifestAuditDemoFixture() {
    }

    public static FilingCatalogCapture pendingRoot() {
        FilingCatalogCapture root =
                SecFilingCatalogCaptureTestFixture.capture(ROOT_CAPTURED_AT);
        requireIdentity(ROOT_CAPTURE_ID, root.captureId(), "root capture");
        return root;
    }

    public static FilingCatalogCapture durableRoot() {
        return pendingRoot().withBodyRetention(
                BodyRetention.DURABLE_DECODED_BODY_RETAINED);
    }

    public static List<HistoricalFilingSegmentCapture> pendingSegments(
            FilingCatalogCapture durableRoot) {
        List<HistoricalFilingSegmentCapture> segments = List.of(
                FilingHistoryCollectionTestFixture.segmentCapture(
                        durableRoot,
                        0,
                        FIRST_SEGMENT_CAPTURED_AT,
                        List.of(agreement(), conflictWithoutDocument())),
                FilingHistoryCollectionTestFixture.segmentCapture(
                        durableRoot,
                        1,
                        SECOND_SEGMENT_CAPTURED_AT,
                        List.of(agreement(), conflictWithDocument())));
        requireIdentity(
                FIRST_SEGMENT_CAPTURE_ID,
                segments.get(0).captureId(),
                "first segment capture");
        requireIdentity(
                SECOND_SEGMENT_CAPTURE_ID,
                segments.get(1).captureId(),
                "second segment capture");
        return segments;
    }

    public static List<HistoricalFilingSegmentCapture> durableSegments(
            FilingCatalogCapture durableRoot) {
        return pendingSegments(durableRoot).stream()
                .map(segment -> segment.withBodyRetention(
                        BodyRetention.DURABLE_DECODED_BODY_RETAINED))
                .toList();
    }

    public static List<DescriptorCaptureSelection> selections(
            List<HistoricalFilingSegmentCapture> durableSegments) {
        if (durableSegments.size() != 2) {
            throw new IllegalArgumentException(
                    "SEC manifest DEMO fixture requires exactly two segments");
        }
        return List.of(
                new DescriptorCaptureSelection(0, durableSegments.get(0).captureId()),
                new DescriptorCaptureSelection(1, durableSegments.get(1).captureId()));
    }

    public static FilingHistoryCollectionManifest assembledManifest() {
        FilingCatalogCapture root = durableRoot();
        FilingHistoryCollectionManifest manifest = FilingHistoryCollectionManifest
                .assemble(root, durableSegments(root), ASSEMBLED_AT);
        requireIdentity(SELECTION_SHA256, manifest.selectionSha256(), "selection");
        requireIdentity(MANIFEST_ID, manifest.manifestId(), "manifest");
        return manifest;
    }

    public static void requireExpectedManifest(FilingHistoryCollectionManifest manifest) {
        requireIdentity(ROOT_CAPTURE_ID, manifest.rootCaptureId(), "root capture");
        requireIdentity(SELECTION_SHA256, manifest.selectionSha256(), "selection");
        requireIdentity(MANIFEST_ID, manifest.manifestId(), "manifest");
        if (!ASSEMBLED_AT.equals(manifest.assembledAt())
                || manifest.descriptors().size() != 2
                || manifest.accessionGroups().size() != 4
                || manifest.occurrences().size() != 6) {
            throw new IllegalStateException(
                    "SEC manifest DEMO fixture summary identity changed");
        }
    }

    private static HistoricalFilingRecord agreement() {
        return historical(
                "0000320193-14-000101",
                "10-K",
                "2014-12-31",
                "2014-09-27",
                "2014-12-31T20:00:00.123456Z",
                null);
    }

    private static HistoricalFilingRecord conflictWithoutDocument() {
        return historical(
                "0000320193-14-000102",
                "8-K",
                "2014-12-30",
                null,
                "2014-12-30T15:30:00.123456Z",
                null);
    }

    private static HistoricalFilingRecord conflictWithDocument() {
        return historical(
                "0000320193-14-000102",
                "8-K",
                "2014-12-30",
                null,
                "2014-12-30T15:30:00.123456Z",
                "https://www.sec.gov/Archives/edgar/data/320193/"
                        + "000032019314000102/conflict8k.htm");
    }

    private static HistoricalFilingRecord historical(
            String accession,
            String form,
            String filingDate,
            String reportDate,
            String acceptedAt,
            String primaryDocumentUri) {
        return new HistoricalFilingRecord(
                accession,
                accession,
                form,
                LocalDate.parse(filingDate),
                reportDate == null ? null : LocalDate.parse(reportDate),
                Instant.parse(acceptedAt),
                primaryDocumentUri == null ? null : URI.create(primaryDocumentUri));
    }

    private static void requireIdentity(String expected, String actual, String subject) {
        if (!expected.equals(actual)) {
            throw new IllegalStateException(
                    "SEC manifest DEMO " + subject + " identity changed");
        }
    }
}
