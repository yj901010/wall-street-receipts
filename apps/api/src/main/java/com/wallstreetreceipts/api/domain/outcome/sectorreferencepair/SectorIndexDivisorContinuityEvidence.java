package com.wallstreetreceipts.api.domain.outcome.sectorreferencepair;

import java.time.Instant;
import java.util.Currency;
import java.util.Objects;

import com.wallstreetreceipts.api.domain.PersistentInstant;
import com.wallstreetreceipts.api.domain.master.AssetType;
import com.wallstreetreceipts.api.domain.outcome.sectorreferencepair.SectorReferenceIndexEvidence.ReferenceIndexKind;

/** Independent evidence binding two sector levels to one divisor-continuous interval. */
public record SectorIndexDivisorContinuityEvidence(
        String continuityEvidenceId,
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
        String continuitySourceId,
        String continuitySourceRevision,
        String provenanceId,
        String basisObservationId,
        String basisProviderEventId,
        String endpointObservationId,
        String endpointProviderEventId,
        Instant coverageStartsAt,
        Instant coverageEndsAt,
        DivisorContinuity divisorContinuity,
        Instant availableAt,
        Instant capturedAt) {

    /** Source-preserved continuity claim; only explicit attestation resolves. */
    public enum DivisorContinuity {
        PROVIDER_PUBLISHED_INDEX_DIVISOR_CONTINUITY_ATTESTED,
        DIVISOR_DISCONTINUITY,
        NOT_ATTESTED,
        UNKNOWN
    }

    public SectorIndexDivisorContinuityEvidence {
        SectorReferenceIndexEvidence.requireCanonicalText(
                continuityEvidenceId, "continuityEvidenceId");
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
                continuitySourceId, "continuitySourceId");
        SectorReferenceIndexEvidence.requireCanonicalText(
                continuitySourceRevision, "continuitySourceRevision");
        SectorReferenceIndexEvidence.requireCanonicalText(
                provenanceId, "provenanceId");
        SectorReferenceIndexEvidence.requireCanonicalText(
                basisObservationId, "basisObservationId");
        SectorReferenceIndexEvidence.requireCanonicalText(
                basisProviderEventId, "basisProviderEventId");
        SectorReferenceIndexEvidence.requireCanonicalText(
                endpointObservationId, "endpointObservationId");
        SectorReferenceIndexEvidence.requireCanonicalText(
                endpointProviderEventId, "endpointProviderEventId");
        PersistentInstant.requireMicrosecondPrecision(
                coverageStartsAt, "coverageStartsAt");
        PersistentInstant.requireMicrosecondPrecision(
                coverageEndsAt, "coverageEndsAt");
        Objects.requireNonNull(divisorContinuity,
                "divisorContinuity must not be null");
        SectorReferenceIndexEvidence.requireEvidenceTimeline(
                availableAt, capturedAt);
        if (coverageEndsAt.isBefore(coverageStartsAt)) {
            throw new IllegalArgumentException(
                    "coverageEndsAt must not precede coverageStartsAt");
        }
        if (availableAt.isBefore(coverageEndsAt)) {
            throw new IllegalArgumentException(
                    "availableAt must not precede coverageEndsAt");
        }
    }
}
