package com.wallstreetreceipts.api.domain.filing;

import java.net.URI;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

import com.wallstreetreceipts.api.domain.PersistentInstant;

/** Immutable capture of a provider response in the provider's published order. */
public record FilingCatalog(
        String provider,
        String product,
        String cik,
        URI sourceUri,
        Instant processingTime,
        Instant capturedAt,
        List<FilingRecord> filings) {

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

        Objects.requireNonNull(filings, "filings must not be null");
        filings = List.copyOf(filings);
        Set<String> providerEventIds = new HashSet<>();
        for (FilingRecord filing : filings) {
            Objects.requireNonNull(filing, "filings must not contain null");
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
    }

    public static void requireCik(String cik) {
        if (cik == null) {
            throw new NullPointerException("cik must not be null");
        }
        if (!TEN_DIGIT_CIK.matcher(cik).matches() || cik.chars().allMatch(character -> character == '0')) {
            throw new IllegalArgumentException("cik must be a non-zero 10-digit identifier");
        }
    }
}
