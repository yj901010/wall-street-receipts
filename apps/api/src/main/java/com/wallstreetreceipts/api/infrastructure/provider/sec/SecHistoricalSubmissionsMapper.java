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
import com.wallstreetreceipts.api.domain.filing.FilingCatalogCapture;
import com.wallstreetreceipts.api.domain.filing.HistoricalFilingRecord;
import com.wallstreetreceipts.api.domain.filing.HistoricalFilingSegment;
import com.wallstreetreceipts.api.domain.filing.HistoricalFilingSegmentDescriptor;
import com.wallstreetreceipts.api.domain.source.SourceResponseReceipt;
import com.wallstreetreceipts.api.domain.source.SourceResponseReceipt.BodyRetention;

/** Pure mapping of one SEC historical submissions segment into canonical rows. */
final class SecHistoricalSubmissionsMapper {

    static final String PROVIDER_NAME = "sec-edgar";
    static final String PRODUCT_NAME = "edgar-submissions-historical-segment-api";
    static final String PARSER_VERSION = "SEC_SUBMISSIONS_HISTORICAL_SEGMENT_V1";

    private static final String ARCHIVES_BASE_URI =
            "https://www.sec.gov/Archives/edgar/data/";
    private static final Pattern PRIMARY_DOCUMENT_SEGMENT =
            Pattern.compile("[A-Za-z0-9][A-Za-z0-9._-]*");

    private SecHistoricalSubmissionsMapper() {
    }

    static HistoricalFilingSegment toCanonical(
            SecHistoricalSubmissionsResponse source,
            FilingCatalogCapture rootCapture,
            int descriptorOrdinal,
            SourceResponseReceipt sourceReceipt,
            Instant processingTime) {
        Objects.requireNonNull(source, "source must not be null");
        Objects.requireNonNull(rootCapture, "rootCapture must not be null");
        Objects.requireNonNull(sourceReceipt, "sourceReceipt must not be null");
        FilingCatalog root = rootCapture.catalog();
        requireDurableRoot(root, descriptorOrdinal);
        HistoricalFilingSegmentDescriptor descriptor =
                root.historicalSegments().get(descriptorOrdinal);
        requireReceipt(root, descriptor, sourceReceipt);
        PersistentInstant.requireMicrosecondPrecision(processingTime, "processingTime");

        int count = requireParallelArrays(source);
        List<HistoricalFilingRecord> filings = new ArrayList<>(count);
        for (int index = 0; index < count; index++) {
            String accession = requiredText(
                    source.accessionNumber().get(index), "accessionNumber", index);
            String primaryDocument = nullablePrimaryDocument(
                    source.primaryDocument().get(index), index);
            if (primaryDocument != null
                    && !isCanonicalRelativeDocumentPath(primaryDocument)) {
                throw invalid(
                        "primaryDocument",
                        index,
                        "must be a canonical relative SEC Archives path");
            }
            filings.add(new HistoricalFilingRecord(
                    accession,
                    accession,
                    requiredText(source.form().get(index), "form", index),
                    parseRequiredDate(source.filingDate().get(index), "filingDate", index),
                    parseNullableDate(source.reportDate().get(index), "reportDate", index),
                    parseRequiredInstant(
                            source.acceptanceDateTime().get(index),
                            "acceptanceDateTime",
                            index),
                    primaryDocument == null
                            ? null
                            : canonicalPrimaryDocumentUri(
                                    root.cik(), accession, primaryDocument)));
        }

        return new HistoricalFilingSegment(
                PROVIDER_NAME,
                PRODUCT_NAME,
                rootCapture.captureId(),
                root.capturedAt(),
                descriptorOrdinal,
                root.cik(),
                descriptor,
                sourceReceipt.sourceUri(),
                processingTime,
                sourceReceipt.capturedAt(),
                sourceReceipt,
                filings);
    }

    private static void requireDurableRoot(FilingCatalog root, int descriptorOrdinal) {
        if (!SecSubmissionsMapper.PROVIDER_NAME.equals(root.provider())
                || !SecSubmissionsMapper.PRODUCT_NAME.equals(root.product())
                || !SecSubmissionsMapper.PARSER_VERSION.equals(
                        root.sourceReceipt().parserVersion())
                || root.sourceReceipt().bodyRetention()
                        != BodyRetention.DURABLE_DECODED_BODY_RETAINED) {
            throw new IllegalArgumentException(
                    "rootCapture must be a durable SEC submissions catalog capture");
        }
        if (descriptorOrdinal < 0
                || descriptorOrdinal >= root.historicalSegments().size()) {
            throw new IllegalArgumentException(
                    "descriptorOrdinal must identify a captured historical descriptor");
        }
    }

    private static void requireReceipt(
            FilingCatalog root,
            HistoricalFilingSegmentDescriptor descriptor,
            SourceResponseReceipt receipt) {
        if (!PROVIDER_NAME.equals(receipt.provider())
                || !PRODUCT_NAME.equals(receipt.product())
                || !PARSER_VERSION.equals(receipt.parserVersion())) {
            throw new IllegalArgumentException(
                    "sourceReceipt must use the SEC historical segment parser identity");
        }
        String expectedFileNamePrefix = "CIK" + root.cik() + "-submissions-";
        if (!descriptor.fileName().startsWith(expectedFileNamePrefix)
                || !descriptor.fileName().matches(
                        "CIK[0-9]{10}-submissions-(?!000)[0-9]{3}\\.json")) {
            throw new IllegalArgumentException(
                    "descriptor must use the exact catalog CIK-bound SEC filename");
        }
        URI sourceUri = receipt.sourceUri();
        String expectedPath = "/submissions/" + descriptor.fileName();
        if (!sourceUri.isAbsolute()
                || !("https".equalsIgnoreCase(sourceUri.getScheme())
                || "http".equalsIgnoreCase(sourceUri.getScheme()))
                || sourceUri.getHost() == null
                || sourceUri.getUserInfo() != null
                || sourceUri.getQuery() != null
                || sourceUri.getFragment() != null
                || !expectedPath.equals(sourceUri.getPath())) {
            throw new IllegalArgumentException(
                    "sourceUri must preserve the captured historical segment path");
        }
    }

    private static int requireParallelArrays(SecHistoricalSubmissionsResponse source) {
        List<?>[] arrays = {
                source.accessionNumber(), source.filingDate(), source.reportDate(),
                source.acceptanceDateTime(), source.act(), source.form(),
                source.fileNumber(), source.filmNumber(), source.items(), source.size(),
                source.isXBRL(), source.isInlineXBRL(), source.primaryDocument(),
                source.primaryDocDescription()
        };
        String[] fields = {
                "accessionNumber", "filingDate", "reportDate", "acceptanceDateTime",
                "act", "form", "fileNumber", "filmNumber", "items", "size",
                "isXBRL", "isInlineXBRL", "primaryDocument", "primaryDocDescription"
        };
        int expected = -1;
        for (int arrayIndex = 0; arrayIndex < arrays.length; arrayIndex++) {
            List<?> values = arrays[arrayIndex];
            if (values == null) {
                throw new IllegalArgumentException(
                        fields[arrayIndex] + " must be present");
            }
            if (expected < 0) {
                expected = values.size();
            } else if (values.size() != expected) {
                throw new IllegalArgumentException(
                        "SEC historical filing arrays must have identical lengths: "
                                + fields[arrayIndex] + " differs");
            }
            for (int valueIndex = 0; valueIndex < values.size(); valueIndex++) {
                if (values.get(valueIndex) == null
                        && !"reportDate".equals(fields[arrayIndex])
                        && !"primaryDocument".equals(fields[arrayIndex])) {
                    throw invalid(fields[arrayIndex], valueIndex, "must not be null");
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

    private static String nullablePrimaryDocument(String value, int index) {
        if (value == null || value.isEmpty()) {
            return null;
        }
        if (value.isBlank() || !value.equals(value.strip())) {
            throw invalid(
                    "primaryDocument", index, "must be empty or nonblank and trimmed");
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

    private static Instant parseRequiredInstant(String value, String field, int index) {
        String canonical = requiredText(value, field, index);
        try {
            Instant result = Instant.parse(canonical);
            PersistentInstant.requireMicrosecondPrecision(
                    result, field + "[" + index + "]");
            return result;
        } catch (DateTimeException | IllegalArgumentException exception) {
            throw invalid(
                    field,
                    index,
                    "must be an ISO-8601 UTC instant at microsecond precision",
                    exception);
        }
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

    private static URI canonicalPrimaryDocumentUri(
            String cik,
            String accession,
            String primaryDocument) {
        String unpaddedCik = cik.replaceFirst("^0+(?!$)", "");
        return URI.create(ARCHIVES_BASE_URI
                + unpaddedCik + "/" + accession.replace("-", "") + "/"
                + primaryDocument);
    }

    private static IllegalArgumentException invalid(
            String field,
            int index,
            String detail) {
        return new IllegalArgumentException(
                field + "[" + index + "] " + detail);
    }

    private static IllegalArgumentException invalid(
            String field,
            int index,
            String detail,
            Exception cause) {
        return new IllegalArgumentException(
                field + "[" + index + "] " + detail,
                cause);
    }
}
