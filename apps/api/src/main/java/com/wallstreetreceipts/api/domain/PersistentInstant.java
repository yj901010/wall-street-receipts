package com.wallstreetreceipts.api.domain;

import java.time.Instant;
import java.util.Objects;

/** PostgreSQL TIMESTAMP(6) boundary shared by every persisted domain timestamp. */
public final class PersistentInstant {

    private PersistentInstant() {
    }

    public static void requireMicrosecondPrecision(Instant value, String field) {
        Objects.requireNonNull(value, field + " must not be null");
        if (value.getNano() % 1_000 != 0) {
            throw new IllegalArgumentException(field + " must not exceed microsecond precision");
        }
    }

    public static void requireNullableMicrosecondPrecision(Instant value, String field) {
        if (value != null) {
            requireMicrosecondPrecision(value, field);
        }
    }
}
