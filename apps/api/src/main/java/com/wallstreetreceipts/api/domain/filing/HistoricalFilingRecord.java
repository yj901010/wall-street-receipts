package com.wallstreetreceipts.api.domain.filing;

import java.net.URI;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;
import java.util.regex.Pattern;

import com.wallstreetreceipts.api.domain.PersistentInstant;

/** One historical SEC filing event, preserving an observed missing primary document as null. */
public record HistoricalFilingRecord(
        String providerEventId,
        String accessionNumber,
        String form,
        LocalDate filingDate,
        LocalDate reportDate,
        Instant acceptedAt,
        URI primaryDocumentUri) {

    private static final Pattern SEC_ACCESSION_NUMBER =
            Pattern.compile("[0-9]{10}-[0-9]{2}-[0-9]{6}");

    public HistoricalFilingRecord {
        FilingRecord.requireCanonicalText(providerEventId, "providerEventId");
        FilingRecord.requireCanonicalText(accessionNumber, "accessionNumber");
        FilingRecord.requireCanonicalText(form, "form");
        Objects.requireNonNull(filingDate, "filingDate must not be null");
        PersistentInstant.requireMicrosecondPrecision(acceptedAt, "acceptedAt");
        if (!SEC_ACCESSION_NUMBER.matcher(accessionNumber).matches()) {
            throw new IllegalArgumentException(
                    "accessionNumber must use the SEC 10-2-6 digit format");
        }
        if (!providerEventId.equals(accessionNumber)) {
            throw new IllegalArgumentException(
                    "providerEventId must equal the exact SEC accessionNumber");
        }
        if (primaryDocumentUri != null) {
            requireCanonicalDocument(
                    providerEventId,
                    accessionNumber,
                    form,
                    filingDate,
                    reportDate,
                    acceptedAt,
                    primaryDocumentUri);
        }
    }

    void requireCatalogArchiveIdentity(String cik) {
        FilingCatalog.requireCik(cik);
        if (primaryDocumentUri == null) {
            return;
        }
        requireCanonicalDocument(
                providerEventId,
                accessionNumber,
                form,
                filingDate,
                reportDate,
                acceptedAt,
                primaryDocumentUri)
                .requireCatalogArchiveIdentity(cik);
    }

    private static FilingRecord requireCanonicalDocument(
            String providerEventId,
            String accessionNumber,
            String form,
            LocalDate filingDate,
            LocalDate reportDate,
            Instant acceptedAt,
            URI primaryDocumentUri) {
        return new FilingRecord(
                providerEventId,
                accessionNumber,
                form,
                filingDate,
                reportDate,
                acceptedAt,
                primaryDocumentUri);
    }
}
