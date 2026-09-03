package com.wallstreetreceipts.api.domain.outcome.benchmarkreferencepair;

import java.time.Instant;
import java.util.Currency;
import java.util.Objects;

import com.wallstreetreceipts.api.domain.PersistentInstant;
import com.wallstreetreceipts.api.domain.master.AssetType;

/** One reusable benchmark identity to provider-published reference-index binding. */
public record BenchmarkReferenceIndexEvidence(
        String referenceIndexEvidenceId,
        String providerEventId,
        String assignmentEvidenceId,
        String assignmentProviderEventId,
        String benchmarkAssetId,
        AssetType benchmarkAssetType,
        String referenceProviderId,
        String referenceIndexId,
        String referenceIndexLabel,
        String referenceIndexDefinitionRevision,
        ReferenceIndexKind referenceKind,
        Currency currency,
        String calculationVenueId,
        String calendarId,
        String calendarRevision,
        String calendarSourceId,
        String calendarSourceRevision,
        String levelSourceId,
        String levelSourceRevision,
        String continuitySourceId,
        String continuitySourceRevision,
        String bindingSourceId,
        String bindingSourceRevision,
        String provenanceId,
        EffectiveInterval effectiveInterval,
        Instant availableAt,
        Instant capturedAt) {

    /** Source-preserved reference semantics; only the price-index kind resolves. */
    public enum ReferenceIndexKind {
        PROVIDER_PUBLISHED_PRICE_INDEX,
        PROVIDER_PUBLISHED_TOTAL_RETURN_INDEX,
        NON_PROVIDER_PUBLISHED_PRICE_INDEX,
        EXCHANGE_TRADED_FUND,
        CURRENT_CONSTITUENT_BASKET,
        MARKET_CAP_PROXY,
        PROVIDER_RETURN_FIELD,
        UNKNOWN
    }

    /** Explicit start-inclusive/end-exclusive interval end. */
    public sealed interface EffectiveIntervalEnd
            permits OpenEnded, EndsAtExclusive {
    }

    /** Explicit open-ended binding interval. */
    public record OpenEnded() implements EffectiveIntervalEnd {
    }

    /** Explicit exclusive binding interval end. */
    public record EndsAtExclusive(Instant value) implements EffectiveIntervalEnd {

        public EndsAtExclusive {
            PersistentInstant.requireMicrosecondPrecision(value, "value");
        }
    }

    /** Reference binding interval selected at the frozen forecast-basis instant. */
    public record EffectiveInterval(
            Instant startsAtInclusive,
            EffectiveIntervalEnd end) {

        public EffectiveInterval {
            PersistentInstant.requireMicrosecondPrecision(
                    startsAtInclusive, "startsAtInclusive");
            Objects.requireNonNull(end, "end must not be null");
            if (end instanceof EndsAtExclusive closed
                    && !closed.value().isAfter(startsAtInclusive)) {
                throw new IllegalArgumentException(
                        "exclusive interval end must follow its start");
            }
        }

        public boolean contains(Instant value) {
            PersistentInstant.requireMicrosecondPrecision(value, "value");
            if (value.isBefore(startsAtInclusive)) {
                return false;
            }
            return end instanceof OpenEnded
                    || value.isBefore(((EndsAtExclusive) end).value());
        }
    }

    public BenchmarkReferenceIndexEvidence {
        requireCanonicalText(referenceIndexEvidenceId,
                "referenceIndexEvidenceId");
        requireCanonicalText(providerEventId, "providerEventId");
        requireCanonicalText(assignmentEvidenceId, "assignmentEvidenceId");
        requireCanonicalText(assignmentProviderEventId,
                "assignmentProviderEventId");
        requireCanonicalText(benchmarkAssetId, "benchmarkAssetId");
        Objects.requireNonNull(benchmarkAssetType,
                "benchmarkAssetType must not be null");
        requireProviderIdentityText(referenceProviderId, "referenceProviderId");
        requireProviderIdentityText(referenceIndexId, "referenceIndexId");
        requireProviderEvidenceText(referenceIndexLabel, "referenceIndexLabel");
        requireProviderIdentityText(referenceIndexDefinitionRevision,
                "referenceIndexDefinitionRevision");
        Objects.requireNonNull(referenceKind, "referenceKind must not be null");
        Objects.requireNonNull(currency, "currency must not be null");
        requireCanonicalText(calculationVenueId, "calculationVenueId");
        requireCanonicalText(calendarId, "calendarId");
        requireCanonicalText(calendarRevision, "calendarRevision");
        requireCanonicalText(calendarSourceId, "calendarSourceId");
        requireCanonicalText(calendarSourceRevision, "calendarSourceRevision");
        requireCanonicalText(levelSourceId, "levelSourceId");
        requireCanonicalText(levelSourceRevision, "levelSourceRevision");
        requireCanonicalText(continuitySourceId, "continuitySourceId");
        requireCanonicalText(continuitySourceRevision,
                "continuitySourceRevision");
        requireCanonicalText(bindingSourceId, "bindingSourceId");
        requireCanonicalText(bindingSourceRevision, "bindingSourceRevision");
        requireCanonicalText(provenanceId, "provenanceId");
        Objects.requireNonNull(effectiveInterval,
                "effectiveInterval must not be null");
        requireEvidenceTimeline(availableAt, capturedAt);
    }

    static void requireCanonicalText(String value, String field) {
        if (value == null) {
            throw new NullPointerException(field + " must not be null");
        }
        if (value.isBlank() || !value.equals(value.strip())) {
            throw new IllegalArgumentException(
                    field + " must be nonblank and trimmed");
        }
    }

    static void requireProviderIdentityText(String value, String field) {
        if (value == null) {
            throw new NullPointerException(field + " must not be null");
        }
        if (value.isEmpty()) {
            throw new IllegalArgumentException(field + " must not be empty");
        }
    }

    static void requireProviderEvidenceText(String value, String field) {
        requireProviderIdentityText(value, field);
    }

    static void requireEvidenceTimeline(Instant availableAt, Instant capturedAt) {
        PersistentInstant.requireMicrosecondPrecision(availableAt, "availableAt");
        PersistentInstant.requireMicrosecondPrecision(capturedAt, "capturedAt");
        if (capturedAt.isBefore(availableAt)) {
            throw new IllegalArgumentException(
                    "capturedAt must not precede availableAt");
        }
    }
}
