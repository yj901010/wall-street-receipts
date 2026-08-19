package com.wallstreetreceipts.api.domain.context;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;

import com.wallstreetreceipts.api.domain.market.DataMode;

public record MacroObservation(
        String schemaVersion,
        String macroObservationId,
        MacroSeries series,
        BigDecimal value,
        MacroUnit unit,
        LocalDate observationDate,
        Instant releasedAt,
        Instant processingTime,
        LocalDate vintageStart,
        LocalDate vintageEnd,
        String sourceReferenceId,
        DataMode dataMode,
        Instant capturedAt,
        String provenanceId) {

    public MacroObservation {
        ContextValidation.requireSchemaVersion(schemaVersion);
        ContextValidation.requireIdentifier(macroObservationId, "macroObservationId");
        Objects.requireNonNull(series, "series must not be null");
        value = ContextValidation.canonicalDecimal(value, "value");
        Objects.requireNonNull(unit, "unit must not be null");
        Objects.requireNonNull(observationDate, "observationDate must not be null");
        ContextValidation.requirePersistentInstant(releasedAt, "releasedAt");
        ContextValidation.requirePersistentInstant(processingTime, "processingTime");
        ContextValidation.requireIdentifier(sourceReferenceId, "sourceReferenceId");
        Objects.requireNonNull(dataMode, "dataMode must not be null");
        ContextValidation.requirePersistentInstant(capturedAt, "capturedAt");
        ContextValidation.requireIdentifier(provenanceId, "provenanceId");

        if (processingTime.isBefore(releasedAt)) {
            throw new IllegalArgumentException("processingTime must not precede releasedAt");
        }
        if (capturedAt.isBefore(processingTime)) {
            throw new IllegalArgumentException("capturedAt must not precede processingTime");
        }
        if (vintageStart != null && vintageEnd != null && vintageEnd.isBefore(vintageStart)) {
            throw new IllegalArgumentException("vintageEnd must not precede vintageStart");
        }
    }
}
