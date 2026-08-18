package com.wallstreetreceipts.api.domain.call;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Currency;
import java.util.Objects;

import com.wallstreetreceipts.api.domain.market.DataMode;
import com.wallstreetreceipts.api.domain.master.Analyst;
import com.wallstreetreceipts.api.domain.master.Asset;
import com.wallstreetreceipts.api.domain.master.Institution;
import com.wallstreetreceipts.api.domain.source.SourceReference;

public record AnalystCall(
        String id,
        String provider,
        String providerEventId,
        Institution institution,
        Analyst analyst,
        Asset asset,
        Instant eventTime,
        Instant processingTime,
        CallDirection direction,
        String originalRating,
        BigDecimal previousTarget,
        BigDecimal target,
        Currency currency,
        LocalDate targetDate,
        SourceReference sourceReference,
        CallStatus status,
        DataMode dataMode,
        Instant capturedAt,
        String provenanceId) {

    public AnalystCall {
        requireText(id, "id");
        requireText(provider, "provider");
        requireText(providerEventId, "providerEventId");
        Objects.requireNonNull(institution, "institution must not be null");
        Objects.requireNonNull(asset, "asset must not be null");
        Objects.requireNonNull(eventTime, "eventTime must not be null");
        Objects.requireNonNull(processingTime, "processingTime must not be null");
        Objects.requireNonNull(direction, "direction must not be null");
        Objects.requireNonNull(sourceReference, "sourceReference must not be null");
        Objects.requireNonNull(status, "status must not be null");
        Objects.requireNonNull(dataMode, "dataMode must not be null");
        Objects.requireNonNull(capturedAt, "capturedAt must not be null");
        requireText(provenanceId, "provenanceId");

        if (processingTime.isBefore(eventTime)) {
            throw new IllegalArgumentException("processingTime must not precede eventTime");
        }
        requirePositive(previousTarget, "previousTarget");
        requirePositive(target, "target");
        if ((previousTarget != null || target != null) && currency == null) {
            throw new IllegalArgumentException("currency is required when a target is present");
        }
    }

    private static void requirePositive(BigDecimal value, String field) {
        if (value != null && value.signum() <= 0) {
            throw new IllegalArgumentException(field + " must be positive");
        }
    }

    private static void requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
    }
}
