package com.wallstreetreceipts.api.domain.outcome;

import java.time.Instant;
import java.util.Objects;
import java.util.regex.Pattern;

import com.wallstreetreceipts.api.domain.PersistentInstant;
import com.wallstreetreceipts.api.domain.market.DataMode;

public record ScoringMethodology(
        String methodologyId,
        String methodologyVersion,
        String schemaVersion,
        String definitionHash,
        MethodologyStatus status,
        Instant effectiveAt,
        DataMode dataMode,
        Instant capturedAt,
        String provenanceId) {

    private static final Pattern IDENTIFIER = Pattern.compile("^[A-Za-z0-9][A-Za-z0-9._:-]{0,127}$");
    private static final Pattern METHODOLOGY_VERSION = Pattern.compile("^[A-Za-z0-9][A-Za-z0-9._+-]{0,63}$");
    private static final Pattern SHA_256 = Pattern.compile("^[a-f0-9]{64}$");

    public ScoringMethodology {
        requireIdentifier(methodologyId, "methodologyId");
        requireMethodologyVersion(methodologyVersion);
        if (!"1.0.0".equals(schemaVersion)) {
            throw new IllegalArgumentException("schemaVersion must be 1.0.0");
        }
        requireHash(definitionHash, "definitionHash");
        Objects.requireNonNull(status, "status must not be null");
        Objects.requireNonNull(effectiveAt, "effectiveAt must not be null");
        Objects.requireNonNull(dataMode, "dataMode must not be null");
        Objects.requireNonNull(capturedAt, "capturedAt must not be null");
        PersistentInstant.requireMicrosecondPrecision(effectiveAt, "effectiveAt");
        PersistentInstant.requireMicrosecondPrecision(capturedAt, "capturedAt");
        requireIdentifier(provenanceId, "provenanceId");
        if (capturedAt.isBefore(effectiveAt)) {
            throw new IllegalArgumentException("capturedAt must not precede effectiveAt");
        }
    }

    static void requireIdentifier(String value, String field) {
        if (value == null || !IDENTIFIER.matcher(value).matches()) {
            throw new IllegalArgumentException(field + " is not a valid opaque identifier");
        }
    }

    static void requireHash(String value, String field) {
        if (value == null || !SHA_256.matcher(value).matches()) {
            throw new IllegalArgumentException(field + " must be a lowercase 64-character SHA-256 hash");
        }
    }

    static void requireMethodologyVersion(String value) {
        if (value == null || !METHODOLOGY_VERSION.matcher(value).matches()) {
            throw new IllegalArgumentException("methodologyVersion is not valid");
        }
    }
}
