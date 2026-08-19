package com.wallstreetreceipts.api.domain.context;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Objects;

import com.wallstreetreceipts.api.domain.market.DataMode;

public record MacroSnapshot(
        String schemaVersion,
        String macroSnapshotId,
        String callId,
        Instant eventTime,
        Instant processingTime,
        List<MacroObservation> observations,
        DataMode dataMode,
        Instant capturedAt,
        String provenanceId) {

    public MacroSnapshot {
        ContextValidation.requireSchemaVersion(schemaVersion);
        ContextValidation.requireIdentifier(macroSnapshotId, "macroSnapshotId");
        ContextValidation.requireIdentifier(callId, "callId");
        ContextValidation.requirePersistentInstant(eventTime, "eventTime");
        ContextValidation.requirePersistentInstant(processingTime, "processingTime");
        observations = List.copyOf(observations);
        Objects.requireNonNull(dataMode, "dataMode must not be null");
        ContextValidation.requirePersistentInstant(capturedAt, "capturedAt");
        ContextValidation.requireIdentifier(provenanceId, "provenanceId");

        if (processingTime.isBefore(eventTime)) {
            throw new IllegalArgumentException("processingTime must not precede eventTime");
        }
        if (capturedAt.isBefore(processingTime)) {
            throw new IllegalArgumentException("capturedAt must not precede processingTime");
        }
        if (observations.size() != MacroSeries.values().length) {
            throw new IllegalArgumentException("observations must contain exactly six supported series");
        }
        LocalDate eventDate = eventTime.atOffset(ZoneOffset.UTC).toLocalDate();
        for (int index = 0; index < observations.size(); index++) {
            MacroObservation observation = Objects.requireNonNull(
                    observations.get(index), "observation must not be null");
            if (observation.series() != MacroSeries.values()[index]) {
                throw new IllegalArgumentException("observations must use the canonical series order");
            }
            if (observation.dataMode() != dataMode) {
                throw new IllegalArgumentException("observation and snapshot dataMode must match");
            }
            if (observation.releasedAt().isAfter(eventTime)) {
                throw new IllegalArgumentException("observation must be released by snapshot eventTime");
            }
            if (observation.processingTime().isAfter(processingTime)) {
                throw new IllegalArgumentException("observation must be processed by snapshot processingTime");
            }
            if (observation.capturedAt().isAfter(capturedAt)) {
                throw new IllegalArgumentException("observation must be captured by snapshot capturedAt");
            }
            if (observation.vintageStart() != null && eventDate.isBefore(observation.vintageStart())) {
                throw new IllegalArgumentException("observation vintage is not active at snapshot eventTime");
            }
            if (observation.vintageEnd() != null && eventDate.isAfter(observation.vintageEnd())) {
                throw new IllegalArgumentException("observation vintage is not active at snapshot eventTime");
            }
        }
    }
}
