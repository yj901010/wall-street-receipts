package com.wallstreetreceipts.api.domain.filing;

import java.net.URI;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

import com.wallstreetreceipts.api.domain.PersistentInstant;
import com.wallstreetreceipts.api.domain.source.SourceResponseReceipt;

/** Immutable capture of a provider response in the provider's published order. */
public record FilingCatalog(
        String provider,
        String product,
        String cik,
        URI sourceUri,
        Instant processingTime,
        Instant capturedAt,
        SourceResponseReceipt sourceReceipt,
        List<FilingRecord> recentFilings,
        List<HistoricalFilingSegmentDescriptor> historicalSegments) {

    private static final Pattern TEN_DIGIT_CIK = Pattern.compile("[0-9]{10}");

    public FilingCatalog {
        FilingRecord.requireCanonicalText(provider, "provider");
        FilingRecord.requireCanonicalText(product, "product");
        requireCik(cik);
        FilingRecord.requireAbsoluteHttpSourceUri(sourceUri, "sourceUri");
        PersistentInstant.requireMicrosecondPrecision(processingTime, "processingTime");
        PersistentInstant.requireMicrosecondPrecision(capturedAt, "capturedAt");
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
                    "sourceReceipt must identify this exact catalog capture");
        }

        Objects.requireNonNull(recentFilings, "recentFilings must not be null");
        recentFilings = List.copyOf(recentFilings);
        Set<String> providerEventIds = new HashSet<>();
        for (FilingRecord filing : recentFilings) {
            Objects.requireNonNull(filing, "recentFilings must not contain null");
            filing.requireCatalogArchiveIdentity(cik);
            if (filing.acceptedAt().isAfter(processingTime)) {
                throw new IllegalArgumentException(
                        "processingTime must not precede filing acceptedAt");
            }
            if (!providerEventIds.add(filing.providerEventId())) {
                throw new IllegalArgumentException(
                        "providerEventId must be unique within the catalog");
            }
        }

        Objects.requireNonNull(historicalSegments, "historicalSegments must not be null");
        historicalSegments = List.copyOf(historicalSegments);
        Set<String> historicalFileNames = new HashSet<>();
        for (HistoricalFilingSegmentDescriptor segment : historicalSegments) {
            Objects.requireNonNull(
                    segment, "historicalSegments must not contain null");
            if (!historicalFileNames.add(segment.fileName())) {
                throw new IllegalArgumentException(
                        "historical fileName must be unique within the catalog");
            }
        }
    }

    public HistoricalSegmentStatus historicalSegmentStatus() {
        return historicalSegments.isEmpty()
                ? HistoricalSegmentStatus.RECENT_ONLY_NO_SEGMENTS_ADVERTISED
                : HistoricalSegmentStatus.RECENT_ONLY_SEGMENTS_ADVERTISED_NOT_FETCHED;
    }

    public boolean hasAdvertisedHistoricalDateRangeOverlap() {
        for (int left = 0; left < historicalSegments.size(); left++) {
            for (int right = left + 1; right < historicalSegments.size(); right++) {
                if (historicalSegments.get(left)
                        .overlapsAdvertisedDateRange(historicalSegments.get(right))) {
                    return true;
                }
            }
        }
        return false;
    }

    public boolean hasAdvertisedRecentHistoricalDateOverlap() {
        for (FilingRecord filing : recentFilings) {
            for (HistoricalFilingSegmentDescriptor segment : historicalSegments) {
                if (segment.containsAdvertisedDate(filing.filingDate())) {
                    return true;
                }
            }
        }
        return false;
    }

    public static void requireCik(String cik) {
        if (cik == null) {
            throw new NullPointerException("cik must not be null");
        }
        if (!TEN_DIGIT_CIK.matcher(cik).matches() || cik.chars().allMatch(character -> character == '0')) {
            throw new IllegalArgumentException("cik must be a non-zero 10-digit identifier");
        }
    }

    public enum HistoricalSegmentStatus {
        RECENT_ONLY_NO_SEGMENTS_ADVERTISED,
        RECENT_ONLY_SEGMENTS_ADVERTISED_NOT_FETCHED
    }
}
