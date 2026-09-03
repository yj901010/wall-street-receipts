package com.wallstreetreceipts.api.support;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.LocalDate;
import java.util.HexFormat;
import java.util.List;

import com.wallstreetreceipts.api.domain.filing.FilingCatalogCapture;
import com.wallstreetreceipts.api.domain.filing.HistoricalFilingSegment;
import com.wallstreetreceipts.api.domain.filing.HistoricalFilingSegmentCapture;
import com.wallstreetreceipts.api.domain.filing.HistoricalFilingSegmentDescriptor;
import com.wallstreetreceipts.api.domain.filing.HistoricalFilingRecord;
import com.wallstreetreceipts.api.domain.source.SourceResponseReceipt;
import com.wallstreetreceipts.api.domain.source.SourceResponseReceipt.BodyRepresentation;
import com.wallstreetreceipts.api.domain.source.SourceResponseReceipt.BodyRetention;
import com.wallstreetreceipts.api.domain.source.SourceResponseReceipt.TransportContentEncoding;

public final class SecHistoricalFilingSegmentCaptureTestFixture {

    public static final String PRODUCT =
            "edgar-submissions-historical-segment-api";
    public static final String PARSER_VERSION =
            "SEC_SUBMISSIONS_HISTORICAL_SEGMENT_V1";
    public static final URI SOURCE_URI = URI.create(
            "https://data.sec.gov/submissions/"
                    + "CIK0000320193-submissions-002.json");

    private SecHistoricalFilingSegmentCaptureTestFixture() {
    }

    public static HistoricalFilingSegmentCapture capture(
            FilingCatalogCapture durableRoot,
            Instant capturedAt) {
        return capture(durableRoot, capturedAt, "10-K");
    }

    public static HistoricalFilingSegmentCapture capture(
            FilingCatalogCapture durableRoot,
            Instant capturedAt,
            String firstForm) {
        return capture(durableRoot, capturedAt, firstForm, false);
    }

    public static HistoricalFilingSegmentCapture captureWithMissingPrimaryDocument(
            FilingCatalogCapture durableRoot,
            Instant capturedAt) {
        return capture(durableRoot, capturedAt, "10-K", true);
    }

    private static HistoricalFilingSegmentCapture capture(
            FilingCatalogCapture durableRoot,
            Instant capturedAt,
            String firstForm,
            boolean firstPrimaryDocumentMissing) {
        String json = firstPrimaryDocumentMissing
                ? json(firstForm).replace(
                        "\"primaryDocument\": [\"form10k.htm\", \"form8k.htm\"]",
                        "\"primaryDocument\": [\"\", \"form8k.htm\"]")
                : json(firstForm);
        byte[] body = json.getBytes(StandardCharsets.UTF_8);
        SourceResponseReceipt receipt = new SourceResponseReceipt(
                SecFilingCatalogCaptureTestFixture.PROVIDER,
                PRODUCT,
                SOURCE_URI,
                200,
                "application/json;charset=UTF-8",
                TransportContentEncoding.GZIP,
                "\"historical-fixture-revision\"",
                Instant.parse("2026-08-25T01:10:00Z"),
                PARSER_VERSION,
                sha256(body),
                body.length,
                capturedAt,
                BodyRepresentation.DECODED_HTTP_ENTITY_BODY,
                BodyRetention.DECODED_BODY_ATTACHED_PENDING_PERSISTENCE);
        HistoricalFilingSegmentDescriptor descriptor = durableRoot.catalog()
                .historicalSegments().getFirst();
        HistoricalFilingSegment segment = new HistoricalFilingSegment(
                SecFilingCatalogCaptureTestFixture.PROVIDER,
                PRODUCT,
                durableRoot.captureId(),
                durableRoot.catalog().capturedAt(),
                0,
                SecFilingCatalogCaptureTestFixture.CIK,
                descriptor,
                SOURCE_URI,
                capturedAt,
                capturedAt,
                receipt,
                List.of(
                        new HistoricalFilingRecord(
                                "0000320193-20-000001",
                                "0000320193-20-000001",
                                firstForm,
                                LocalDate.parse("2020-12-31"),
                                LocalDate.parse("2020-09-26"),
                                Instant.parse("2020-12-31T20:00:00.123456Z"),
                                firstPrimaryDocumentMissing
                                        ? null
                                        : URI.create("https://www.sec.gov/Archives/edgar/data/"
                                                + "320193/000032019320000001/form10k.htm")),
                        new HistoricalFilingRecord(
                                "0000320193-15-000002",
                                "0000320193-15-000002",
                                "8-K",
                                LocalDate.parse("2015-01-01"),
                                null,
                                Instant.parse("2015-01-01T15:30:00Z"),
                                URI.create("https://www.sec.gov/Archives/edgar/data/"
                                        + "320193/000032019315000002/form8k.htm"))));
        return new HistoricalFilingSegmentCapture(segment, body);
    }

    public static String json(String firstForm) {
        return """
                {
                  "accessionNumber": ["0000320193-20-000001", "0000320193-15-000002"],
                  "filingDate": ["2020-12-31", "2015-01-01"],
                  "reportDate": ["2020-09-26", ""],
                  "acceptanceDateTime": ["2020-12-31T20:00:00.123456Z", "2015-01-01T15:30:00Z"],
                  "act": ["34", "34"],
                  "form": ["%s", "8-K"],
                  "fileNumber": ["001-36743", "001-36743"],
                  "filmNumber": ["201234567", "151234568"],
                  "items": ["", "2.02"],
                  "size": [123456, 234567],
                  "isXBRL": [1, 0],
                  "isInlineXBRL": [1, 0],
                  "primaryDocument": ["form10k.htm", "form8k.htm"],
                  "primaryDocDescription": ["Annual report", "Current report"]
                }
                """.formatted(firstForm);
    }

    private static String sha256(byte[] body) {
        try {
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(body));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(exception);
        }
    }
}
