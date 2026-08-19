package com.wallstreetreceipts.api.domain.context;

import java.time.Instant;
import java.util.Objects;

import com.wallstreetreceipts.api.domain.market.DataMode;

public record EventContext(
        String schemaVersion,
        String eventContextId,
        String callId,
        Instant eventTime,
        Instant processingTime,
        Instant earningsAt,
        Instant nextCpiAt,
        Instant nextFomcAt,
        Instant nextNfpAt,
        Instant optionsExpirationAt,
        String sourceReferenceId,
        DataMode dataMode,
        Instant capturedAt,
        String provenanceId) {

    public EventContext {
        ContextValidation.requireSchemaVersion(schemaVersion);
        ContextValidation.requireIdentifier(eventContextId, "eventContextId");
        ContextValidation.requireIdentifier(callId, "callId");
        ContextValidation.requirePersistentInstant(eventTime, "eventTime");
        ContextValidation.requirePersistentInstant(processingTime, "processingTime");
        ContextValidation.requireNullablePersistentInstant(earningsAt, "earningsAt");
        ContextValidation.requireNullablePersistentInstant(nextCpiAt, "nextCpiAt");
        ContextValidation.requireNullablePersistentInstant(nextFomcAt, "nextFomcAt");
        ContextValidation.requireNullablePersistentInstant(nextNfpAt, "nextNfpAt");
        ContextValidation.requireNullablePersistentInstant(optionsExpirationAt, "optionsExpirationAt");
        ContextValidation.requireIdentifier(sourceReferenceId, "sourceReferenceId");
        Objects.requireNonNull(dataMode, "dataMode must not be null");
        ContextValidation.requirePersistentInstant(capturedAt, "capturedAt");
        ContextValidation.requireIdentifier(provenanceId, "provenanceId");

        if (processingTime.isBefore(eventTime)) {
            throw new IllegalArgumentException("processingTime must not precede eventTime");
        }
        if (capturedAt.isBefore(processingTime)) {
            throw new IllegalArgumentException("capturedAt must not precede processingTime");
        }
        requireNotBeforeEvent(nextCpiAt, eventTime, "nextCpiAt");
        requireNotBeforeEvent(nextFomcAt, eventTime, "nextFomcAt");
        requireNotBeforeEvent(nextNfpAt, eventTime, "nextNfpAt");
        requireNotBeforeEvent(optionsExpirationAt, eventTime, "optionsExpirationAt");
    }

    private static void requireNotBeforeEvent(Instant value, Instant eventTime, String field) {
        if (value != null && value.isBefore(eventTime)) {
            throw new IllegalArgumentException(field + " must not precede eventTime");
        }
    }
}
