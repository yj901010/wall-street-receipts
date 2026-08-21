package com.wallstreetreceipts.api.domain.outcome.horizon;

import java.time.Instant;

import com.wallstreetreceipts.api.domain.PersistentInstant;

/**
 * One independently evaluated original-call or correction forecast lineage.
 *
 * <p>The caller must verify that a correction belongs to the call, is a valid
 * correction rather than a cancellation, and was point-in-time available.
 * Cancellation has no subtype and therefore cannot be represented as a
 * forecast basis by this policy leaf.</p>
 */
public sealed interface OutcomeBasis
        permits OutcomeBasis.Original, OutcomeBasis.Correction {

    String callId();

    /** Null only for the immutable original-call lineage. */
    String basisRevisionId();

    Instant eventTime();

    /** The immutable original analyst-call event remains its own lineage. */
    record Original(
            String callId,
            Instant eventTime) implements OutcomeBasis {

        public Original {
            requireIdentifier(callId, "callId");
            PersistentInstant.requireMicrosecondPrecision(eventTime, "eventTime");
        }

        @Override
        public String basisRevisionId() {
            return null;
        }
    }

    /** One caller-validated correction starts a distinct forecast lineage. */
    record Correction(
            String callId,
            String basisRevisionId,
            Instant eventTime) implements OutcomeBasis {

        public Correction {
            requireIdentifier(callId, "callId");
            requireIdentifier(basisRevisionId, "basisRevisionId");
            PersistentInstant.requireMicrosecondPrecision(eventTime, "eventTime");
        }
    }

    private static void requireIdentifier(String value, String field) {
        if (value == null) {
            throw new NullPointerException(field + " must not be null");
        }
        if (value.isBlank() || !value.equals(value.strip())) {
            throw new IllegalArgumentException(field + " must be nonblank and trimmed");
        }
    }
}
