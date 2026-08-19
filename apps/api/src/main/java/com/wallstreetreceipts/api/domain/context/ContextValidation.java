package com.wallstreetreceipts.api.domain.context;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.regex.Pattern;

import com.wallstreetreceipts.api.domain.PersistentInstant;

final class ContextValidation {

    static final String SCHEMA_VERSION = "1.0.0";
    private static final Pattern IDENTIFIER = Pattern.compile("^[A-Za-z0-9][A-Za-z0-9._:-]{0,127}$");

    private ContextValidation() {
    }

    static void requireSchemaVersion(String value) {
        if (!SCHEMA_VERSION.equals(value)) {
            throw new IllegalArgumentException("schemaVersion must be 1.0.0");
        }
    }

    static void requireIdentifier(String value, String field) {
        if (value == null || !IDENTIFIER.matcher(value).matches()) {
            throw new IllegalArgumentException(field + " is not a valid opaque identifier");
        }
    }

    static void requirePersistentInstant(Instant value, String field) {
        PersistentInstant.requireMicrosecondPrecision(value, field);
    }

    static void requireNullablePersistentInstant(Instant value, String field) {
        PersistentInstant.requireNullableMicrosecondPrecision(value, field);
    }

    static BigDecimal canonicalDecimal(BigDecimal value, String field) {
        if (value == null) {
            return null;
        }
        BigDecimal normalized = value.stripTrailingZeros();
        if (normalized.signum() == 0) {
            return BigDecimal.ZERO;
        }
        try {
            BigDecimal storageValue = normalized.setScale(12, RoundingMode.UNNECESSARY);
            if (storageValue.precision() > 38) {
                throw new IllegalArgumentException(field + " exceeds NUMERIC(38,12) precision");
            }
        } catch (ArithmeticException exception) {
            throw new IllegalArgumentException(field + " exceeds NUMERIC(38,12) scale", exception);
        }
        return normalized.scale() < 0 ? normalized.setScale(0) : normalized;
    }
}
