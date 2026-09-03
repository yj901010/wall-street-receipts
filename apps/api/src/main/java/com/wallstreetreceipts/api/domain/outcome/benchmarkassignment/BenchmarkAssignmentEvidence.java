package com.wallstreetreceipts.api.domain.outcome.benchmarkassignment;

import java.time.Instant;
import java.util.Currency;
import java.util.Objects;

import com.wallstreetreceipts.api.domain.master.AssetType;
import com.wallstreetreceipts.api.domain.outcome.benchmarkassignment.BenchmarkAssetClassificationEvidence.EffectiveInterval;
import com.wallstreetreceipts.api.domain.outcome.horizon.OutcomeBasis;

/** One explicit provider-identified benchmark mapping for a forecast basis. */
public record BenchmarkAssignmentEvidence(
        String assignmentEvidenceId,
        String providerEventId,
        OutcomeBasis basis,
        String assetId,
        AssetType assetType,
        String primaryVenueId,
        String primaryVenueCountryCode,
        Currency currency,
        String assignmentSourceId,
        String assignmentSourceRevision,
        String provenanceId,
        EffectiveInterval effectiveInterval,
        String benchmarkAssetId,
        AssetType benchmarkAssetType,
        Currency benchmarkCurrency,
        BenchmarkReferenceKind referenceKind,
        Instant availableAt,
        Instant capturedAt) {

    /** Source-preserved reference semantics; only the price-index kind is V1-valid. */
    public enum BenchmarkReferenceKind {
        PROVIDER_PUBLISHED_PRICE_INDEX,
        PROVIDER_PUBLISHED_TOTAL_RETURN_INDEX,
        NON_PROVIDER_PUBLISHED_PRICE_INDEX,
        UNKNOWN
    }

    public BenchmarkAssignmentEvidence {
        BenchmarkAssetClassificationEvidence.requireCanonicalText(
                assignmentEvidenceId, "assignmentEvidenceId");
        BenchmarkAssetClassificationEvidence.requireCanonicalText(
                providerEventId, "providerEventId");
        Objects.requireNonNull(basis, "basis must not be null");
        BenchmarkAssetClassificationEvidence.requireCanonicalText(assetId, "assetId");
        Objects.requireNonNull(assetType, "assetType must not be null");
        BenchmarkAssetClassificationEvidence.requireCanonicalText(
                primaryVenueId, "primaryVenueId");
        BenchmarkAssetClassificationEvidence.requireIsoAlpha2Country(
                primaryVenueCountryCode, "primaryVenueCountryCode");
        Objects.requireNonNull(currency, "currency must not be null");
        BenchmarkAssetClassificationEvidence.requireCanonicalText(
                assignmentSourceId, "assignmentSourceId");
        BenchmarkAssetClassificationEvidence.requireCanonicalText(
                assignmentSourceRevision, "assignmentSourceRevision");
        BenchmarkAssetClassificationEvidence.requireCanonicalText(
                provenanceId, "provenanceId");
        Objects.requireNonNull(effectiveInterval,
                "effectiveInterval must not be null");
        BenchmarkAssetClassificationEvidence.requireCanonicalText(
                benchmarkAssetId, "benchmarkAssetId");
        Objects.requireNonNull(benchmarkAssetType,
                "benchmarkAssetType must not be null");
        Objects.requireNonNull(benchmarkCurrency,
                "benchmarkCurrency must not be null");
        Objects.requireNonNull(referenceKind, "referenceKind must not be null");
        BenchmarkAssetClassificationEvidence.requireEvidenceTimeline(
                availableAt, capturedAt);
    }
}
