package com.wallstreetreceipts.api.domain.outcome.sectorreferencepair;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.Currency;
import java.util.Objects;

import com.wallstreetreceipts.api.domain.PersistentInstant;
import com.wallstreetreceipts.api.domain.master.AssetType;
import com.wallstreetreceipts.api.domain.outcome.sectorreferencepair.SectorReferenceIndexEvidence.ReferenceIndexKind;

/** One immutable provider-identified sector reference-level observation. */
public record SectorReferenceLevelObservation(
        String observationId,
        String providerEventId,
        String referenceIndexEvidenceId,
        String referenceIndexProviderEventId,
        String referenceAssetId,
        AssetType referenceAssetType,
        String referenceProviderId,
        String referenceIndexId,
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
        String provenanceId,
        ReferenceLevelField levelField,
        Instant observedAt,
        Instant availableAt,
        Instant capturedAt,
        BigDecimal level) {

    private static final int STORAGE_SCALE = 12;
    private static final int STORAGE_PRECISION = 38;

    /** Source-preserved field semantics; only a published index level resolves. */
    public enum ReferenceLevelField {
        PROVIDER_PUBLISHED_INDEX_LEVEL,
        PROVIDER_PUBLISHED_RETURN,
        EXCHANGE_TRADED_FUND_MARKET_PRICE,
        EXCHANGE_TRADED_FUND_NAV,
        DERIVED_PROXY_LEVEL,
        UNKNOWN
    }

    public SectorReferenceLevelObservation {
        SectorReferenceIndexEvidence.requireCanonicalText(
                observationId, "observationId");
        SectorReferenceIndexEvidence.requireCanonicalText(
                providerEventId, "providerEventId");
        SectorReferenceIndexEvidence.requireCanonicalText(
                referenceIndexEvidenceId, "referenceIndexEvidenceId");
        SectorReferenceIndexEvidence.requireCanonicalText(
                referenceIndexProviderEventId,
                "referenceIndexProviderEventId");
        SectorReferenceIndexEvidence.requireCanonicalText(
                referenceAssetId, "referenceAssetId");
        Objects.requireNonNull(referenceAssetType,
                "referenceAssetType must not be null");
        SectorReferenceIndexEvidence.requireProviderIdentityText(
                referenceProviderId, "referenceProviderId");
        SectorReferenceIndexEvidence.requireProviderIdentityText(
                referenceIndexId, "referenceIndexId");
        SectorReferenceIndexEvidence.requireProviderIdentityText(
                referenceIndexDefinitionRevision,
                "referenceIndexDefinitionRevision");
        Objects.requireNonNull(referenceKind, "referenceKind must not be null");
        Objects.requireNonNull(currency, "currency must not be null");
        SectorReferenceIndexEvidence.requireCanonicalText(
                calculationVenueId, "calculationVenueId");
        SectorReferenceIndexEvidence.requireCanonicalText(calendarId, "calendarId");
        SectorReferenceIndexEvidence.requireCanonicalText(
                calendarRevision, "calendarRevision");
        SectorReferenceIndexEvidence.requireCanonicalText(
                calendarSourceId, "calendarSourceId");
        SectorReferenceIndexEvidence.requireCanonicalText(
                calendarSourceRevision, "calendarSourceRevision");
        SectorReferenceIndexEvidence.requireCanonicalText(
                levelSourceId, "levelSourceId");
        SectorReferenceIndexEvidence.requireCanonicalText(
                levelSourceRevision, "levelSourceRevision");
        SectorReferenceIndexEvidence.requireCanonicalText(
                provenanceId, "provenanceId");
        Objects.requireNonNull(levelField, "levelField must not be null");
        PersistentInstant.requireMicrosecondPrecision(observedAt, "observedAt");
        SectorReferenceIndexEvidence.requireEvidenceTimeline(
                availableAt, capturedAt);
        if (availableAt.isBefore(observedAt)) {
            throw new IllegalArgumentException(
                    "availableAt must not precede observedAt");
        }
        requirePositiveNumeric(level, "level");
    }

    private static void requirePositiveNumeric(BigDecimal value, String field) {
        Objects.requireNonNull(value, field + " must not be null");
        if (value.signum() <= 0) {
            throw new IllegalArgumentException(field + " must be positive");
        }
        try {
            BigDecimal storageValue = value.setScale(
                    STORAGE_SCALE, RoundingMode.UNNECESSARY);
            if (storageValue.precision() > STORAGE_PRECISION) {
                throw new IllegalArgumentException(
                        field + " exceeds NUMERIC(38,12) precision");
            }
        } catch (ArithmeticException exception) {
            throw new IllegalArgumentException(
                    field + " exceeds NUMERIC(38,12) scale", exception);
        }
    }
}
