package com.wallstreetreceipts.api.infrastructure.provider.sec;

import java.net.URI;
import java.nio.charset.StandardCharsets;

import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;

import com.wallstreetreceipts.api.application.port.out.FilingCatalogCaptureReplayVerifier;
import com.wallstreetreceipts.api.domain.filing.FilingCatalog;
import com.wallstreetreceipts.api.domain.filing.FilingCatalogCapture;

@Component
public final class SecFilingCatalogCaptureReplayVerifier
        implements FilingCatalogCaptureReplayVerifier {

    private static final String OFFICIAL_SOURCE_ORIGIN = "https://data.sec.gov";

    @Override
    public FilingCatalogCapture verify(FilingCatalogCapture capture) {
        FilingCatalog catalog = capture.catalog();
        if (!SecSubmissionsMapper.PROVIDER_NAME.equals(catalog.provider())
                || !SecSubmissionsMapper.PRODUCT_NAME.equals(catalog.product())
                || !SecSubmissionsMapper.PARSER_VERSION.equals(
                        catalog.sourceReceipt().parserVersion())) {
            throw new IllegalArgumentException(
                    "unsupported filing catalog capture parser contract");
        }
        requireOfficialSourceEnvelope(catalog);

        try {
            FilingCatalog replayed = SecRawResponseCapture.replay(
                    capture.decodedBody(),
                    catalog.sourceReceipt(),
                    catalog.processingTime());
            if (!catalog.equals(replayed)) {
                throw new IllegalArgumentException(
                        "filing catalog capture does not match exact-body replay");
            }
            return capture;
        } catch (java.io.IOException | RuntimeException exception) {
            if (exception instanceof IllegalArgumentException illegalArgumentException
                    && illegalArgumentException.getMessage() != null
                    && illegalArgumentException.getMessage().startsWith("filing catalog capture")) {
                throw illegalArgumentException;
            }
            throw new IllegalArgumentException(
                    "filing catalog capture could not be replayed");
        }
    }

    private static void requireOfficialSourceEnvelope(FilingCatalog catalog) {
        URI sourceUri = catalog.sourceUri();
        String expectedSource = OFFICIAL_SOURCE_ORIGIN
                + "/submissions/CIK" + catalog.cik() + ".json";
        if (!expectedSource.equals(sourceUri.toASCIIString())
                || !isJsonUtf8(catalog.sourceReceipt().mediaType())
                || catalog.sourceReceipt().decodedBodyLength()
                        > SecResponseSizeLimitInterceptor.MAX_DECOMPRESSED_RESPONSE_BYTES) {
            throw new IllegalArgumentException(
                    "unsupported filing catalog capture source envelope");
        }
    }

    private static boolean isJsonUtf8(String mediaTypeValue) {
        try {
            MediaType mediaType = MediaType.parseMediaType(mediaTypeValue);
            return "application".equalsIgnoreCase(mediaType.getType())
                    && "json".equalsIgnoreCase(mediaType.getSubtype())
                    && mediaType.getParameters().keySet().stream()
                            .allMatch(parameter -> "charset".equalsIgnoreCase(parameter))
                    && (mediaType.getCharset() == null
                    || StandardCharsets.UTF_8.equals(mediaType.getCharset()));
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }
}
