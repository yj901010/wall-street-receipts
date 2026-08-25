package com.wallstreetreceipts.api.domain.filing;

import java.net.URI;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;
import java.util.regex.Pattern;

import com.wallstreetreceipts.api.domain.PersistentInstant;

/** One provider-published filing event without inferred or substituted values. */
public record FilingRecord(
        String providerEventId,
        String accessionNumber,
        String form,
        LocalDate filingDate,
        LocalDate reportDate,
        Instant acceptedAt,
        URI primaryDocumentUri) {

    private static final Pattern SEC_ACCESSION_NUMBER =
            Pattern.compile("[0-9]{10}-[0-9]{2}-[0-9]{6}");
    private static final Pattern SEC_DOCUMENT_SEGMENT =
            Pattern.compile("[A-Za-z0-9][A-Za-z0-9._-]*");

    public FilingRecord {
        requireCanonicalText(providerEventId, "providerEventId");
        requireCanonicalText(accessionNumber, "accessionNumber");
        requireCanonicalText(form, "form");
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
        requireCanonicalSecArchivesUri(primaryDocumentUri, accessionNumber);
    }

    static void requireCanonicalText(String value, String field) {
        if (value == null) {
            throw new NullPointerException(field + " must not be null");
        }
        if (value.isBlank() || !value.equals(value.strip())) {
            throw new IllegalArgumentException(field + " must be nonblank and trimmed");
        }
    }

    private static void requireCanonicalSecArchivesUri(
            URI value,
            String accessionNumber) {
        Objects.requireNonNull(value, "primaryDocumentUri must not be null");
        String accessionPath = accessionNumber.replace("-", "");
        String archivesPrefix = "/Archives/edgar/data/";
        String accessionSuffix = "/" + accessionPath + "/";
        String rawPath = value.getRawPath();
        int accessionSuffixStart = rawPath == null ? -1 : rawPath.indexOf(accessionSuffix);
        String archiveCik = rawPath != null
                && rawPath.startsWith(archivesPrefix)
                && accessionSuffixStart > archivesPrefix.length()
                ? rawPath.substring(archivesPrefix.length(), accessionSuffixStart)
                : "";
        String documentPath = accessionSuffixStart >= 0
                ? rawPath.substring(accessionSuffixStart + accessionSuffix.length())
                : "";
        if (!value.isAbsolute()
                || !"https".equals(value.getScheme())
                || !"www.sec.gov".equals(value.getHost())
                || value.getPort() != -1
                || value.getUserInfo() != null
                || value.getQuery() != null
                || value.getFragment() != null
                || !archiveCik.matches("[1-9][0-9]*")
                || !isCanonicalDocumentPath(documentPath)) {
            throw new IllegalArgumentException(
                    "primaryDocumentUri must be the canonical SEC Archives URI for accessionNumber");
        }
    }

    private static boolean isCanonicalDocumentPath(String value) {
        if (value.isEmpty() || value.startsWith("/") || value.endsWith("/")
                || value.indexOf('\\') >= 0 || value.indexOf('%') >= 0) {
            return false;
        }
        String[] segments = value.split("/", -1);
        for (String segment : segments) {
            if (segment.isEmpty() || segment.equals(".") || segment.equals("..")
                    || !SEC_DOCUMENT_SEGMENT.matcher(segment).matches()) {
                return false;
            }
        }
        return true;
    }

    void requireCatalogArchiveIdentity(String cik) {
        String unpaddedCik = cik.replaceFirst("^0+(?!$)", "");
        String accessionPath = accessionNumber.replace("-", "");
        String expectedPathPrefix = "/Archives/edgar/data/"
                + unpaddedCik + "/" + accessionPath + "/";
        if (!primaryDocumentUri.getPath().startsWith(expectedPathPrefix)) {
            throw new IllegalArgumentException(
                    "primaryDocumentUri must use the catalog cik and accessionNumber");
        }
    }

    static void requireAbsoluteHttpSourceUri(URI value, String field) {
        Objects.requireNonNull(value, field + " must not be null");
        if (!value.isAbsolute()
                || !("https".equalsIgnoreCase(value.getScheme())
                || "http".equalsIgnoreCase(value.getScheme()))
                || value.getHost() == null
                || value.getHost().isBlank()
                || value.getUserInfo() != null
                || value.getQuery() != null
                || value.getFragment() != null) {
            throw new IllegalArgumentException(
                    field + " must be an absolute HTTP(S) source URI");
        }
    }
}
