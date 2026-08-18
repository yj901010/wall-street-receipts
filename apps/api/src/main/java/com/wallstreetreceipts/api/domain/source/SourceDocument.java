package com.wallstreetreceipts.api.domain.source;

import java.net.URI;
import java.time.Instant;
import java.util.Objects;

import com.wallstreetreceipts.api.domain.PersistentInstant;
import com.wallstreetreceipts.api.domain.market.DataMode;

public record SourceDocument(
        String id,
        SourceType type,
        String publisher,
        String title,
        URI canonicalUrl,
        Instant publishedAt,
        String provider,
        String externalId,
        String contentHash,
        String licenseClass,
        DataMode dataMode,
        Instant capturedAt,
        String provenanceId) {

    public SourceDocument {
        requireText(id, "id");
        Objects.requireNonNull(type, "type must not be null");
        requireText(title, "title");
        requireText(provider, "provider");
        requireText(licenseClass, "licenseClass");
        Objects.requireNonNull(dataMode, "dataMode must not be null");
        Objects.requireNonNull(capturedAt, "capturedAt must not be null");
        PersistentInstant.requireNullableMicrosecondPrecision(publishedAt, "publishedAt");
        PersistentInstant.requireMicrosecondPrecision(capturedAt, "capturedAt");
        requireText(provenanceId, "provenanceId");
    }

    private static void requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
    }
}
