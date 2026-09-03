package com.wallstreetreceipts.api.infrastructure.provider.sec;

import java.net.URI;
import java.nio.charset.StandardCharsets;

import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;

import com.wallstreetreceipts.api.application.port.out.HistoricalFilingSegmentCaptureReplayVerifier;
import com.wallstreetreceipts.api.domain.filing.FilingCatalog;
import com.wallstreetreceipts.api.domain.filing.FilingCatalogCapture;
import com.wallstreetreceipts.api.domain.filing.HistoricalFilingSegment;
import com.wallstreetreceipts.api.domain.filing.HistoricalFilingSegmentCapture;
import com.wallstreetreceipts.api.domain.filing.HistoricalFilingSegmentDescriptor;

@Component
public final class SecHistoricalFilingSegmentCaptureReplayVerifier
        implements HistoricalFilingSegmentCaptureReplayVerifier {

    private static final String OFFICIAL_ORIGIN = "https://data.sec.gov";

    @Override
    public HistoricalFilingSegmentCapture verify(
            HistoricalFilingSegmentCapture capture,
            FilingCatalogCapture rootCapture) {
        HistoricalFilingSegment segment = capture.segment();
        requireContract(segment, rootCapture);
        requireOfficialEnvelope(segment);
        try {
            HistoricalFilingSegment replayed = SecHistoricalRawResponseCapture.replay(
                    capture.decodedBody(),
                    rootCapture,
                    segment.descriptorOrdinal(),
                    segment.sourceReceipt(),
                    segment.processingTime());
            if (!segment.equals(replayed)) {
                throw new IllegalArgumentException(
                        "historical segment capture does not match exact-body replay");
            }
            return capture;
        } catch (java.io.IOException | RuntimeException exception) {
            if (exception instanceof IllegalArgumentException illegalArgumentException
                    && illegalArgumentException.getMessage() != null
                    && illegalArgumentException.getMessage()
                            .startsWith("historical segment capture")) {
                throw illegalArgumentException;
            }
            throw new IllegalArgumentException(
                    "historical segment capture could not be replayed");
        }
    }

    private static void requireContract(
            HistoricalFilingSegment segment,
            FilingCatalogCapture rootCapture) {
        FilingCatalog root = rootCapture.catalog();
        if (!SecHistoricalSubmissionsMapper.PROVIDER_NAME.equals(segment.provider())
                || !SecHistoricalSubmissionsMapper.PRODUCT_NAME.equals(segment.product())
                || !SecHistoricalSubmissionsMapper.PARSER_VERSION.equals(
                        segment.sourceReceipt().parserVersion())
                || !rootCapture.captureId().equals(segment.rootCaptureId())
                || !root.cik().equals(segment.cik())
                || !root.capturedAt().equals(segment.rootCapturedAt())
                || segment.descriptorOrdinal() < 0
                || segment.descriptorOrdinal() >= root.historicalSegments().size()) {
            throw new IllegalArgumentException(
                    "unsupported historical segment capture parser contract");
        }
        HistoricalFilingSegmentDescriptor exactDescriptor = root.historicalSegments()
                .get(segment.descriptorOrdinal());
        if (!exactDescriptor.equals(segment.descriptor())) {
            throw new IllegalArgumentException(
                    "historical segment capture is not bound to its root descriptor");
        }
    }

    private static void requireOfficialEnvelope(HistoricalFilingSegment segment) {
        URI sourceUri = segment.sourceUri();
        String expected = OFFICIAL_ORIGIN + "/submissions/"
                + segment.descriptor().fileName();
        if (!expected.equals(sourceUri.toASCIIString())
                || !isJsonUtf8(segment.sourceReceipt().mediaType())
                || segment.sourceReceipt().decodedBodyLength()
                        > SecResponseSizeLimitInterceptor.MAX_DECOMPRESSED_RESPONSE_BYTES) {
            throw new IllegalArgumentException(
                    "unsupported historical segment capture source envelope");
        }
    }

    private static boolean isJsonUtf8(String value) {
        try {
            MediaType mediaType = MediaType.parseMediaType(value);
            return "application".equalsIgnoreCase(mediaType.getType())
                    && "json".equalsIgnoreCase(mediaType.getSubtype())
                    && mediaType.getParameters().keySet().stream()
                            .allMatch(name -> "charset".equalsIgnoreCase(name))
                    && (mediaType.getCharset() == null
                    || StandardCharsets.UTF_8.equals(mediaType.getCharset()));
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }
}
