package com.wallstreetreceipts.api.domain.outcome.benchmarkassignment;

import java.time.Instant;
import java.util.Currency;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

import com.wallstreetreceipts.api.domain.PersistentInstant;
import com.wallstreetreceipts.api.domain.master.AssetType;
import com.wallstreetreceipts.api.domain.outcome.horizon.OutcomeBasis;

/** Point-in-time asset and primary-venue classification for one forecast basis. */
public record BenchmarkAssetClassificationEvidence(
        String classificationEvidenceId,
        String providerEventId,
        OutcomeBasis basis,
        String assetId,
        AssetType assetType,
        String primaryVenueId,
        String primaryVenueCountryCode,
        Currency currency,
        String classificationSourceId,
        String classificationSourceRevision,
        String provenanceId,
        EffectiveInterval effectiveInterval,
        Instant availableAt,
        Instant capturedAt) {

    private static final Set<String> ISO_ALPHA_2_COUNTRY_CODES =
            Set.of(Locale.getISOCountries());

    /** An explicit end state; open-ended membership is never represented by null. */
    public sealed interface EffectiveIntervalEnd
            permits OpenEnded, EndsAtExclusive {
    }

    /** Membership has no source-recorded terminal instant. */
    public record OpenEnded() implements EffectiveIntervalEnd {
    }

    /** Membership stops immediately before the supplied terminal instant. */
    public record EndsAtExclusive(Instant value) implements EffectiveIntervalEnd {

        public EndsAtExclusive {
            PersistentInstant.requireMicrosecondPrecision(value, "value");
        }
    }

    /** Start-inclusive/end-exclusive effective membership. */
    public record EffectiveInterval(
            Instant startsAtInclusive,
            EffectiveIntervalEnd end) {

        public EffectiveInterval {
            PersistentInstant.requireMicrosecondPrecision(
                    startsAtInclusive, "startsAtInclusive");
            Objects.requireNonNull(end, "end must not be null");
            if (end instanceof EndsAtExclusive finite
                    && !finite.value().isAfter(startsAtInclusive)) {
                throw new IllegalArgumentException(
                        "finite effective interval end must follow its start");
            }
        }

        public boolean contains(Instant instant) {
            PersistentInstant.requireMicrosecondPrecision(instant, "instant");
            return !instant.isBefore(startsAtInclusive)
                    && (end instanceof OpenEnded
                    || instant.isBefore(((EndsAtExclusive) end).value()));
        }
    }

    public BenchmarkAssetClassificationEvidence {
        requireCanonicalText(classificationEvidenceId, "classificationEvidenceId");
        requireCanonicalText(providerEventId, "providerEventId");
        Objects.requireNonNull(basis, "basis must not be null");
        requireCanonicalText(assetId, "assetId");
        Objects.requireNonNull(assetType, "assetType must not be null");
        requireCanonicalText(primaryVenueId, "primaryVenueId");
        requireIsoAlpha2Country(primaryVenueCountryCode,
                "primaryVenueCountryCode");
        Objects.requireNonNull(currency, "currency must not be null");
        requireCanonicalText(classificationSourceId, "classificationSourceId");
        requireCanonicalText(
                classificationSourceRevision, "classificationSourceRevision");
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

    static void requireIsoAlpha2Country(String value, String field) {
        requireCanonicalText(value, field);
        if (!ISO_ALPHA_2_COUNTRY_CODES.contains(value)) {
            throw new IllegalArgumentException(
                    field + " must be an uppercase ISO 3166-1 alpha-2 code");
        }
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
