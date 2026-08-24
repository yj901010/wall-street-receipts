package com.wallstreetreceipts.api.domain.outcome.benchmarkreferencepair;

import java.time.Instant;
import java.util.Currency;
import java.util.Objects;

import com.wallstreetreceipts.api.domain.PersistentInstant;
import com.wallstreetreceipts.api.domain.master.AssetType;
import com.wallstreetreceipts.api.domain.outcome.benchmarkreferencepair.BenchmarkReferenceIndexEvidence.ReferenceIndexKind;

/** Independent evidence binding two benchmark levels to one divisor-continuous interval. */
public record BenchmarkIndexDivisorContinuityEvidence(
        String continuityEvidenceId,
        String providerEventId,
        String referenceIndexEvidenceId,
        String referenceIndexProviderEventId,
        String benchmarkAssetId,
        AssetType benchmarkAssetType,
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

    public BenchmarkIndexDivisorContinuityEvidence {
        BenchmarkReferenceIndexEvidence.requireCanonicalText(
                continuityEvidenceId, "continuityEvidenceId");
        BenchmarkReferenceIndexEvidence.requireCanonicalText(
                providerEventId, "providerEventId");
        BenchmarkReferenceIndexEvidence.requireCanonicalText(
                referenceIndexEvidenceId, "referenceIndexEvidenceId");
        BenchmarkReferenceIndexEvidence.requireCanonicalText(
                referenceIndexProviderEventId,
                "referenceIndexProviderEventId");
        BenchmarkReferenceIndexEvidence.requireCanonicalText(
                benchmarkAssetId, "benchmarkAssetId");
        Objects.requireNonNull(benchmarkAssetType,
                "benchmarkAssetType must not be null");
        BenchmarkReferenceIndexEvidence.requireProviderIdentityText(
                referenceProviderId, "referenceProviderId");
        BenchmarkReferenceIndexEvidence.requireProviderIdentityText(
                referenceIndexId, "referenceIndexId");
        BenchmarkReferenceIndexEvidence.requireProviderIdentityText(
                referenceIndexDefinitionRevision,
                "referenceIndexDefinitionRevision");
        Objects.requireNonNull(referenceKind, "referenceKind must not be null");
        Objects.requireNonNull(currency, "currency must not be null");
        BenchmarkReferenceIndexEvidence.requireCanonicalText(
                calculationVenueId, "calculationVenueId");
        BenchmarkReferenceIndexEvidence.requireCanonicalText(calendarId, "calendarId");
        BenchmarkReferenceIndexEvidence.requireCanonicalText(
                calendarRevision, "calendarRevision");
        BenchmarkReferenceIndexEvidence.requireCanonicalText(
                calendarSourceId, "calendarSourceId");
        BenchmarkReferenceIndexEvidence.requireCanonicalText(
                calendarSourceRevision, "calendarSourceRevision");
        BenchmarkReferenceIndexEvidence.requireCanonicalText(
                continuitySourceId, "continuitySourceId");
        BenchmarkReferenceIndexEvidence.requireCanonicalText(
                continuitySourceRevision, "continuitySourceRevision");
        BenchmarkReferenceIndexEvidence.requireCanonicalText(
                provenanceId, "provenanceId");
        BenchmarkReferenceIndexEvidence.requireCanonicalText(
                basisObservationId, "basisObservationId");
        BenchmarkReferenceIndexEvidence.requireCanonicalText(
                basisProviderEventId, "basisProviderEventId");
        BenchmarkReferenceIndexEvidence.requireCanonicalText(
                endpointObservationId, "endpointObservationId");
        BenchmarkReferenceIndexEvidence.requireCanonicalText(
                endpointProviderEventId, "endpointProviderEventId");
        PersistentInstant.requireMicrosecondPrecision(
                coverageStartsAt, "coverageStartsAt");
        PersistentInstant.requireMicrosecondPrecision(
                coverageEndsAt, "coverageEndsAt");
        Objects.requireNonNull(divisorContinuity,
                "divisorContinuity must not be null");
        BenchmarkReferenceIndexEvidence.requireEvidenceTimeline(
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
