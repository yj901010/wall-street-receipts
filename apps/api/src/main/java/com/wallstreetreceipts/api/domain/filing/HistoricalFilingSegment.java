package com.wallstreetreceipts.api.domain.filing;

import java.net.URI;
import java.time.Instant;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.wallstreetreceipts.api.domain.PersistentInstant;
import com.wallstreetreceipts.api.domain.source.SourceResponseReceipt;

/** Immutable provider-ordered filing rows observed from one captured historical segment. */
public record HistoricalFilingSegment(
        String provider,
        String product,
        String rootCaptureId,
        Instant rootCapturedAt,
        int descriptorOrdinal,
        String cik,
        HistoricalFilingSegmentDescriptor descriptor,
        URI sourceUri,
        Instant processingTime,
        Instant capturedAt,
        SourceResponseReceipt sourceReceipt,
        List<HistoricalFilingRecord> filings) {

    private static final Pattern SHA_256 = Pattern.compile("[0-9a-f]{64}");
    private static final Pattern HISTORICAL_SUBMISSIONS_FILE = Pattern.compile(
            "CIK([0-9]{10})-submissions-([0-9]{3})\\.json");

    public HistoricalFilingSegment {
        FilingRecord.requireCanonicalText(provider, "provider");
        FilingRecord.requireCanonicalText(product, "product");
        requireRootCaptureId(rootCaptureId);
        PersistentInstant.requireMicrosecondPrecision(rootCapturedAt, "rootCapturedAt");
        if (descriptorOrdinal < 0) {
            throw new IllegalArgumentException("descriptorOrdinal must be nonnegative");
        }
        FilingCatalog.requireCik(cik);
        Objects.requireNonNull(descriptor, "descriptor must not be null");
        requireCikBoundDescriptor(descriptor, cik);
        requireExactSegmentSourceUri(sourceUri, descriptor.fileName());
        PersistentInstant.requireMicrosecondPrecision(processingTime, "processingTime");
        PersistentInstant.requireMicrosecondPrecision(capturedAt, "capturedAt");
        if (processingTime.isBefore(rootCapturedAt)) {
            throw new IllegalArgumentException(
                    "processingTime must not precede rootCapturedAt");
        }
        if (capturedAt.isBefore(processingTime)) {
            throw new IllegalArgumentException(
                    "capturedAt must not precede processingTime");
        }

        Objects.requireNonNull(sourceReceipt, "sourceReceipt must not be null");
        if (!provider.equals(sourceReceipt.provider())
                || !product.equals(sourceReceipt.product())
                || !sourceUri.equals(sourceReceipt.sourceUri())
                || !capturedAt.equals(sourceReceipt.capturedAt())) {
            throw new IllegalArgumentException(
                    "sourceReceipt must identify this exact historical segment capture");
        }

        Objects.requireNonNull(filings, "filings must not be null");
        try {
            filings = List.copyOf(filings);
        } catch (NullPointerException exception) {
            throw new NullPointerException("filings must not contain null");
        }
        Set<String> providerEventIds = new HashSet<>();
        for (HistoricalFilingRecord filing : filings) {
            filing.requireCatalogArchiveIdentity(cik);
            if (filing.acceptedAt().isAfter(processingTime)) {
                throw new IllegalArgumentException(
                        "processingTime must not precede filing acceptedAt");
            }
            if (!providerEventIds.add(filing.providerEventId())) {
                throw new IllegalArgumentException(
                        "providerEventId must be unique within the historical segment");
            }
        }
    }

    public long observedFilingCount() {
        return filings.size();
    }

    public LocalDate observedFilingFrom() {
        LocalDate earliest = null;
        for (HistoricalFilingRecord filing : filings) {
            if (earliest == null || filing.filingDate().isBefore(earliest)) {
                earliest = filing.filingDate();
            }
        }
        return earliest;
    }

    public LocalDate observedFilingTo() {
        LocalDate latest = null;
        for (HistoricalFilingRecord filing : filings) {
            if (latest == null || filing.filingDate().isAfter(latest)) {
                latest = filing.filingDate();
            }
        }
        return latest;
    }

    public AdvertisedComparison advertisedComparison() {
        boolean countMatches = observedFilingCount() == descriptor.advertisedFilingCount();
        LocalDate observedFrom = observedFilingFrom();
        LocalDate observedTo = observedFilingTo();
        boolean rangeMatches = observedFrom == null
                || (!observedFrom.isBefore(descriptor.advertisedFilingFrom())
                && !observedTo.isAfter(descriptor.advertisedFilingTo()));
        if (countMatches && rangeMatches) {
            return AdvertisedComparison.MATCHES_ADVERTISED;
        }
        if (!countMatches && !rangeMatches) {
            return AdvertisedComparison.COUNT_AND_RANGE_MISMATCH;
        }
        return countMatches
                ? AdvertisedComparison.RANGE_MISMATCH
                : AdvertisedComparison.COUNT_MISMATCH;
    }

    private static void requireRootCaptureId(String value) {
        if (value == null || !SHA_256.matcher(value).matches()) {
            throw new IllegalArgumentException(
                    "rootCaptureId must be lowercase SHA-256 hex");
        }
    }

    private static void requireCikBoundDescriptor(
            HistoricalFilingSegmentDescriptor descriptor,
            String cik) {
        Matcher matcher = HISTORICAL_SUBMISSIONS_FILE.matcher(descriptor.fileName());
        if (!matcher.matches()
                || !cik.equals(matcher.group(1))
                || "000".equals(matcher.group(2))) {
            throw new IllegalArgumentException(
                    "descriptor fileName must be the CIK-bound SEC submissions fileName");
        }
    }

    private static void requireExactSegmentSourceUri(URI sourceUri, String fileName) {
        FilingRecord.requireAbsoluteHttpSourceUri(sourceUri, "sourceUri");
        String expectedPath = "/submissions/" + fileName;
        if (!expectedPath.equals(sourceUri.getRawPath())) {
            throw new IllegalArgumentException(
                    "sourceUri must use the exact historical segment submissions path");
        }
    }

    public enum AdvertisedComparison {
        MATCHES_ADVERTISED,
        COUNT_MISMATCH,
        RANGE_MISMATCH,
        COUNT_AND_RANGE_MISMATCH
    }
}
