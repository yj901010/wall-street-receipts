package com.wallstreetreceipts.api.support;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.LocalDate;
import java.util.HexFormat;
import java.util.List;

import com.wallstreetreceipts.api.domain.filing.FilingCatalog;
import com.wallstreetreceipts.api.domain.filing.FilingCatalogCapture;
import com.wallstreetreceipts.api.domain.filing.FilingRecord;
import com.wallstreetreceipts.api.domain.filing.HistoricalFilingSegmentDescriptor;
import com.wallstreetreceipts.api.domain.source.SourceResponseReceipt;
import com.wallstreetreceipts.api.domain.source.SourceResponseReceipt.BodyRepresentation;
import com.wallstreetreceipts.api.domain.source.SourceResponseReceipt.BodyRetention;
import com.wallstreetreceipts.api.domain.source.SourceResponseReceipt.TransportContentEncoding;

public final class SecFilingCatalogCaptureTestFixture {

    public static final String PROVIDER = "sec-edgar";
    public static final String PRODUCT = "edgar-submissions-api";
    public static final String CIK = "0000320193";
    public static final String PARSER_VERSION = "SEC_SUBMISSIONS_CATALOG_V2";
    public static final URI SOURCE_URI = URI.create(
            "https://data.sec.gov/submissions/CIK0000320193.json");

    private SecFilingCatalogCaptureTestFixture() {
    }

    public static FilingCatalogCapture capture(Instant capturedAt) {
        return capture(capturedAt, "10-Q");
    }

    public static FilingCatalogCapture capture(Instant capturedAt, String firstForm) {
        String json = json(firstForm);
        byte[] body = json.getBytes(StandardCharsets.UTF_8);
        SourceResponseReceipt receipt = new SourceResponseReceipt(
                PROVIDER,
                PRODUCT,
                SOURCE_URI,
                200,
                "application/json",
                TransportContentEncoding.GZIP,
                "\"fixture-revision\"",
                Instant.parse("2026-08-20T20:00:00Z"),
                PARSER_VERSION,
                sha256(body),
                body.length,
                capturedAt,
                BodyRepresentation.DECODED_HTTP_ENTITY_BODY,
                BodyRetention.DECODED_BODY_ATTACHED_PENDING_PERSISTENCE);
        FilingCatalog catalog = new FilingCatalog(
                PROVIDER,
                PRODUCT,
                CIK,
                SOURCE_URI,
                capturedAt,
                capturedAt,
                receipt,
                List.of(
                        new FilingRecord(
                                "0000320193-26-000001",
                                "0000320193-26-000001",
                                firstForm,
                                LocalDate.parse("2026-08-20"),
                                LocalDate.parse("2026-06-27"),
                                Instant.parse("2026-08-20T20:00:00.123456Z"),
                                URI.create("https://www.sec.gov/Archives/edgar/data/"
                                        + "320193/000032019326000001/form10q.htm")),
                        new FilingRecord(
                                "0000320193-26-000002",
                                "0000320193-26-000002",
                                "8-K",
                                LocalDate.parse("2026-08-19"),
                                null,
                                Instant.parse("2026-08-19T15:30:00Z"),
                                URI.create("https://www.sec.gov/Archives/edgar/data/"
                                        + "320193/000032019326000002/form8k.htm"))),
                List.of(
                        new HistoricalFilingSegmentDescriptor(
                                "CIK0000320193-submissions-002.json",
                                2000,
                                LocalDate.parse("2015-01-01"),
                                LocalDate.parse("2020-12-31")),
                        new HistoricalFilingSegmentDescriptor(
                                "CIK0000320193-submissions-001.json",
                                3000,
                                LocalDate.parse("2001-01-01"),
                                LocalDate.parse("2014-12-31"))));
        return new FilingCatalogCapture(catalog, body);
    }

    private static String json(String firstForm) {
        return """
                {
                  "cik": "0000320193",
                  "filings": {
                    "recent": {
                      "accessionNumber": ["0000320193-26-000001", "0000320193-26-000002"],
                      "filingDate": ["2026-08-20", "2026-08-19"],
                      "reportDate": ["2026-06-27", ""],
                      "acceptanceDateTime": ["2026-08-20T20:00:00.123456Z", "2026-08-19T15:30:00Z"],
                      "act": ["34", "34"],
                      "form": ["%s", "8-K"],
                      "fileNumber": ["001-36743", "001-36743"],
                      "filmNumber": ["261234567", "261234568"],
                      "items": ["", "2.02"],
                      "size": [123456, 234567],
                      "isXBRL": [1, 0],
                      "isInlineXBRL": [1, 0],
                      "primaryDocument": ["form10q.htm", "form8k.htm"],
                      "primaryDocDescription": ["Quarterly report", "Current report"]
                    },
                    "files": [
                      {
                        "name": "CIK0000320193-submissions-002.json",
                        "filingCount": 2000,
                        "filingFrom": "2015-01-01",
                        "filingTo": "2020-12-31"
                      },
                      {
                        "name": "CIK0000320193-submissions-001.json",
                        "filingCount": 3000,
                        "filingFrom": "2001-01-01",
                        "filingTo": "2014-12-31"
                      }
                    ]
                  }
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
