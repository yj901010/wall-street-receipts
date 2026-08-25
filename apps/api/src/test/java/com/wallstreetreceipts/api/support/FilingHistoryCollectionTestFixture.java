package com.wallstreetreceipts.api.support;

import java.net.URI;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wallstreetreceipts.api.domain.filing.FilingCatalogCapture;
import com.wallstreetreceipts.api.domain.filing.HistoricalFilingRecord;
import com.wallstreetreceipts.api.domain.filing.HistoricalFilingSegment;
import com.wallstreetreceipts.api.domain.filing.HistoricalFilingSegmentCapture;
import com.wallstreetreceipts.api.domain.filing.HistoricalFilingSegmentDescriptor;
import com.wallstreetreceipts.api.domain.source.SourceResponseReceipt;
import com.wallstreetreceipts.api.domain.source.SourceResponseReceipt.BodyRepresentation;
import com.wallstreetreceipts.api.domain.source.SourceResponseReceipt.BodyRetention;
import com.wallstreetreceipts.api.domain.source.SourceResponseReceipt.TransportContentEncoding;

/** Exact replay-compatible source captures for root-relative collection tests. */
public final class FilingHistoryCollectionTestFixture {

    private static final String PRODUCT =
            "edgar-submissions-historical-segment-api";
    private static final String PARSER_VERSION =
            "SEC_SUBMISSIONS_HISTORICAL_SEGMENT_V1";
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private FilingHistoryCollectionTestFixture() {
    }

    public static HistoricalFilingSegmentCapture segmentCapture(
            FilingCatalogCapture durableRoot,
            int descriptorOrdinal,
            Instant capturedAt,
            List<HistoricalFilingRecord> filings) {
        if (descriptorOrdinal < 0
                || descriptorOrdinal >= durableRoot.catalog().historicalSegments().size()) {
            throw new IllegalArgumentException(
                    "descriptorOrdinal must identify an advertised descriptor");
        }
        List<HistoricalFilingRecord> ownedFilings = List.copyOf(filings);
        HistoricalFilingSegmentDescriptor descriptor = durableRoot.catalog()
                .historicalSegments()
                .get(descriptorOrdinal);
        URI sourceUri = URI.create(
                "https://data.sec.gov/submissions/" + descriptor.fileName());
        byte[] decodedBody = decodedBody(ownedFilings);
        SourceResponseReceipt receipt = new SourceResponseReceipt(
                SecFilingCatalogCaptureTestFixture.PROVIDER,
                PRODUCT,
                sourceUri,
                200,
                "application/json;charset=UTF-8",
                TransportContentEncoding.GZIP,
                "\"collection-fixture-" + descriptorOrdinal + "\"",
                null,
                PARSER_VERSION,
                sha256(decodedBody),
                decodedBody.length,
                capturedAt,
                BodyRepresentation.DECODED_HTTP_ENTITY_BODY,
                BodyRetention.DECODED_BODY_ATTACHED_PENDING_PERSISTENCE);
        HistoricalFilingSegment segment = new HistoricalFilingSegment(
                SecFilingCatalogCaptureTestFixture.PROVIDER,
                PRODUCT,
                durableRoot.captureId(),
                durableRoot.catalog().capturedAt(),
                descriptorOrdinal,
                durableRoot.catalog().cik(),
                descriptor,
                sourceUri,
                capturedAt,
                capturedAt,
                receipt,
                ownedFilings);
        return new HistoricalFilingSegmentCapture(segment, decodedBody);
    }

    private static byte[] decodedBody(List<HistoricalFilingRecord> filings) {
        int size = filings.size();
        Map<String, Object> arrays = new LinkedHashMap<>();
        arrays.put("accessionNumber", filings.stream()
                .map(HistoricalFilingRecord::accessionNumber).toList());
        arrays.put("filingDate", filings.stream()
                .map(filing -> filing.filingDate().toString()).toList());
        arrays.put("reportDate", filings.stream()
                .map(filing -> filing.reportDate() == null
                        ? "" : filing.reportDate().toString()).toList());
        arrays.put("acceptanceDateTime", filings.stream()
                .map(filing -> filing.acceptedAt().toString()).toList());
        arrays.put("act", repeated(size, "34"));
        arrays.put("form", filings.stream()
                .map(HistoricalFilingRecord::form).toList());
        arrays.put("fileNumber", repeated(size, "001-36743"));
        arrays.put("filmNumber", repeated(size, "fixture-film"));
        arrays.put("items", repeated(size, ""));
        arrays.put("size", repeated(size, 1L));
        arrays.put("isXBRL", repeated(size, 0));
        arrays.put("isInlineXBRL", repeated(size, 0));
        arrays.put("primaryDocument", filings.stream()
                .map(filing -> relativeDocument(filing.primaryDocumentUri()))
                .toList());
        arrays.put("primaryDocDescription", repeated(size, "fixture document"));
        try {
            return OBJECT_MAPPER.writeValueAsBytes(arrays);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException(
                    "collection fixture JSON could not be encoded", exception);
        }
    }

    private static String relativeDocument(URI primaryDocumentUri) {
        if (primaryDocumentUri == null) {
            return "";
        }
        String path = primaryDocumentUri.getRawPath();
        return path.substring(path.lastIndexOf('/') + 1);
    }

    private static <T> List<T> repeated(int count, T value) {
        return java.util.Collections.nCopies(count, value);
    }

    private static String sha256(byte[] content) {
        try {
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(content));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }
}
