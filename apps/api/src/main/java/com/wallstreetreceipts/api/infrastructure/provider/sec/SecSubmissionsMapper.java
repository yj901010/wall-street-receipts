package com.wallstreetreceipts.api.infrastructure.provider.sec;

import java.net.URI;
import java.time.DateTimeException;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.regex.Pattern;

import com.wallstreetreceipts.api.domain.PersistentInstant;
import com.wallstreetreceipts.api.domain.filing.FilingCatalog;
import com.wallstreetreceipts.api.domain.filing.FilingRecord;
import com.wallstreetreceipts.api.domain.source.SourceResponseReceipt;
import com.wallstreetreceipts.api.infrastructure.provider.sec.SecSubmissionsResponse.SecRecentFilings;

/** Pure SEC vendor-to-canonical mapping with no I/O or clock access. */
public final class SecSubmissionsMapper {

    public static final String PROVIDER_NAME = "sec-edgar";
    public static final String PRODUCT_NAME = "edgar-submissions-api";
    public static final String PARSER_VERSION = "SEC_SUBMISSIONS_RECENT_V1";

    private static final String ARCHIVES_BASE_URI =
            "https://www.sec.gov/Archives/edgar/data/";
    private static final Pattern PRIMARY_DOCUMENT_SEGMENT =
            Pattern.compile("[A-Za-z0-9][A-Za-z0-9._-]*");

    private SecSubmissionsMapper() {
    }

    static FilingCatalog toCanonical(
            SecSubmissionsResponse source,
            SourceResponseReceipt sourceReceipt,
            Instant processingTime) {
        Objects.requireNonNull(source, "source must not be null");
        Objects.requireNonNull(sourceReceipt, "sourceReceipt must not be null");
        URI sourceUri = sourceReceipt.sourceUri();
        Instant capturedAt = sourceReceipt.capturedAt();
        if (!PROVIDER_NAME.equals(sourceReceipt.provider())
                || !PRODUCT_NAME.equals(sourceReceipt.product())
                || !PARSER_VERSION.equals(sourceReceipt.parserVersion())) {
            throw new IllegalArgumentException(
                    "sourceReceipt must use the SEC submissions parser identity");
        }
        String cik = canonicalCik(source.cik());
        requireCanonicalSourceUri(cik, sourceUri);
        PersistentInstant.requireMicrosecondPrecision(processingTime, "processingTime");
        PersistentInstant.requireMicrosecondPrecision(capturedAt, "capturedAt");
        if (capturedAt.isBefore(processingTime)) {
            throw new IllegalArgumentException(
                    "capturedAt must not precede processingTime");
        }

        if (source.filings() == null) {
            throw new IllegalArgumentException("filings must be present");
        }
        SecRecentFilings recent = source.filings().recent();
        if (recent == null) {
            throw new IllegalArgumentException("filings.recent must be present");
        }

        int count = requireParallelArrays(recent);
        List<FilingRecord> canonical = new ArrayList<>(count);
        for (int index = 0; index < count; index++) {
            String accessionNumber = requiredText(
                    recent.accessionNumber().get(index), "accessionNumber", index);
            String primaryDocument = requiredText(
                    recent.primaryDocument().get(index), "primaryDocument", index);
            if (!isCanonicalRelativeDocumentPath(primaryDocument)) {
                throw invalid("primaryDocument", index,
                        "must be a canonical relative SEC Archives path");
            }

            LocalDate filingDate = parseRequiredDate(
                    recent.filingDate().get(index), "filingDate", index);
            LocalDate reportDate = parseNullableDate(
                    recent.reportDate().get(index), "reportDate", index);
            Instant acceptedAt = parseRequiredInstant(
                    recent.acceptanceDateTime().get(index),
                    "acceptanceDateTime", index);

            canonical.add(new FilingRecord(
                    accessionNumber,
                    accessionNumber,
                    requiredText(recent.form().get(index), "form", index),
                    filingDate,
                    reportDate,
                    acceptedAt,
                    canonicalPrimaryDocumentUri(cik, accessionNumber, primaryDocument)));
        }

        return new FilingCatalog(
                PROVIDER_NAME,
                PRODUCT_NAME,
                cik,
                sourceUri,
                processingTime,
                capturedAt,
                sourceReceipt,
                canonical);
    }

    private static int requireParallelArrays(SecRecentFilings recent) {
        List<?>[] arrays = {
                recent.accessionNumber(), recent.filingDate(), recent.reportDate(),
                recent.acceptanceDateTime(), recent.act(), recent.form(), recent.fileNumber(),
                recent.filmNumber(), recent.items(), recent.size(), recent.isXBRL(),
                recent.isInlineXBRL(), recent.primaryDocument(), recent.primaryDocDescription()
        };
        String[] fields = {
                "accessionNumber", "filingDate", "reportDate", "acceptanceDateTime",
                "act", "form", "fileNumber", "filmNumber", "items", "size", "isXBRL",
                "isInlineXBRL", "primaryDocument", "primaryDocDescription"
        };

        int expected = -1;
        for (int index = 0; index < arrays.length; index++) {
            List<?> values = arrays[index];
            if (values == null) {
                throw new IllegalArgumentException(
                        "filings.recent." + fields[index] + " must be present");
            }
            if (expected < 0) {
                expected = values.size();
            } else if (values.size() != expected) {
                throw new IllegalArgumentException(
                        "SEC recent filing arrays must have identical lengths: "
                                + fields[index] + " differs");
            }
            for (int valueIndex = 0; valueIndex < values.size(); valueIndex++) {
                if (values.get(valueIndex) == null && !fields[index].equals("reportDate")) {
                    throw invalid(fields[index], valueIndex, "must not be null");
                }
            }
        }
        return expected;
    }

    private static String requiredText(String value, String field, int index) {
        if (value == null || value.isBlank() || !value.equals(value.strip())) {
            throw invalid(field, index, "must be nonblank and trimmed");
        }
        return value;
    }

    private static LocalDate parseRequiredDate(String value, String field, int index) {
        String canonical = requiredText(value, field, index);
        try {
            return LocalDate.parse(canonical);
        } catch (DateTimeException exception) {
            throw invalid(field, index, "must be an ISO-8601 calendar date", exception);
        }
    }

    private static LocalDate parseNullableDate(String value, String field, int index) {
        if (value == null || value.isEmpty()) {
            return null;
        }
        return parseRequiredDate(value, field, index);
    }

    private static String canonicalCik(String value) {
        if (value == null) {
            throw new IllegalArgumentException("cik must be present");
        }
        if (!value.matches("[0-9]{10}")
                || value.chars().allMatch(character -> character == '0')) {
            throw new IllegalArgumentException(
                    "cik must be a non-zero 10-digit JSON string");
        }
        return value;
    }

    private static boolean isCanonicalRelativeDocumentPath(String value) {
        if (value.startsWith("/") || value.endsWith("/") || value.indexOf('\\') >= 0
                || value.indexOf('?') >= 0 || value.indexOf('#') >= 0
                || value.indexOf('%') >= 0) {
            return false;
        }
        String[] segments = value.split("/", -1);
        for (String segment : segments) {
            if (segment.isEmpty() || segment.equals(".") || segment.equals("..")
                    || !PRIMARY_DOCUMENT_SEGMENT.matcher(segment).matches()) {
                return false;
            }
        }
        return true;
    }

    private static Instant parseRequiredInstant(String value, String field, int index) {
        String canonical = requiredText(value, field, index);
        try {
            Instant result = Instant.parse(canonical);
            PersistentInstant.requireMicrosecondPrecision(result, field + "[" + index + "]");
            return result;
        } catch (DateTimeException | IllegalArgumentException exception) {
            throw invalid(field, index,
                    "must be an ISO-8601 UTC instant at microsecond precision", exception);
        }
    }

    private static URI canonicalPrimaryDocumentUri(
            String cik,
            String accessionNumber,
            String primaryDocument) {
        String unpaddedCik = cik.replaceFirst("^0+(?!$)", "");
        String accessionPath = accessionNumber.replace("-", "");
        return URI.create(ARCHIVES_BASE_URI
                + unpaddedCik + "/" + accessionPath + "/" + primaryDocument);
    }

    private static void requireCanonicalSourceUri(String cik, URI sourceUri) {
        Objects.requireNonNull(sourceUri, "sourceUri must not be null");
        String expectedPathSuffix = "/submissions/CIK" + cik + ".json";
        if (!sourceUri.isAbsolute()
                || !("https".equalsIgnoreCase(sourceUri.getScheme())
                || "http".equalsIgnoreCase(sourceUri.getScheme()))
                || sourceUri.getHost() == null
                || sourceUri.getUserInfo() != null
                || sourceUri.getQuery() != null
                || sourceUri.getFragment() != null
                || sourceUri.getPath() == null
                || !sourceUri.getPath().endsWith(expectedPathSuffix)) {
            throw new IllegalArgumentException(
                    "sourceUri must preserve the requested SEC submissions path for cik");
        }
    }

    private static IllegalArgumentException invalid(
            String field,
            int index,
            String detail) {
        return new IllegalArgumentException(
                "filings.recent." + field + "[" + index + "] " + detail);
    }

    private static IllegalArgumentException invalid(
            String field,
            int index,
            String detail,
            Exception cause) {
        return new IllegalArgumentException(
                "filings.recent." + field + "[" + index + "] " + detail,
                cause);
    }
}
