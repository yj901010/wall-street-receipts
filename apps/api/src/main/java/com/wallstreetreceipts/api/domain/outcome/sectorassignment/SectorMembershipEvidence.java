package com.wallstreetreceipts.api.domain.outcome.sectorassignment;

import java.time.Instant;
import java.util.Currency;
import java.util.Objects;

import com.wallstreetreceipts.api.domain.master.AssetType;
import com.wallstreetreceipts.api.domain.outcome.horizon.OutcomeBasis;
import com.wallstreetreceipts.api.domain.outcome.sectorassignment.SectorAssetClassificationEvidence.EffectiveInterval;

/** One explicit provider-node membership for an asset and forecast basis. */
public record SectorMembershipEvidence(
        String membershipEvidenceId,
        String providerEventId,
        OutcomeBasis basis,
        String assetId,
        AssetType assetType,
        String primaryVenueId,
        String primaryVenueCountryCode,
        Currency currency,
        String providerId,
        String providerSchemeId,
        String providerSchemeRevision,
        String providerNodeId,
        String providerNodeLabel,
        String membershipSourceId,
        String membershipSourceRevision,
        String provenanceId,
        EffectiveInterval effectiveInterval,
        Instant availableAt,
        Instant capturedAt) {

    public SectorMembershipEvidence {
        SectorAssetClassificationEvidence.requireCanonicalText(
                membershipEvidenceId, "membershipEvidenceId");
        SectorAssetClassificationEvidence.requireCanonicalText(
                providerEventId, "providerEventId");
        Objects.requireNonNull(basis, "basis must not be null");
        SectorAssetClassificationEvidence.requireCanonicalText(assetId, "assetId");
        Objects.requireNonNull(assetType, "assetType must not be null");
        SectorAssetClassificationEvidence.requireCanonicalText(
                primaryVenueId, "primaryVenueId");
        SectorAssetClassificationEvidence.requireIsoAlpha2Country(
                primaryVenueCountryCode, "primaryVenueCountryCode");
        Objects.requireNonNull(currency, "currency must not be null");
        SectorAssetClassificationEvidence.requireProviderIdentityText(
                providerId, "providerId");
        SectorAssetClassificationEvidence.requireProviderIdentityText(
                providerSchemeId, "providerSchemeId");
        SectorAssetClassificationEvidence.requireProviderIdentityText(
                providerSchemeRevision, "providerSchemeRevision");
        SectorAssetClassificationEvidence.requireProviderIdentityText(
                providerNodeId, "providerNodeId");
        SectorAssetClassificationEvidence.requireProviderEvidenceText(
                providerNodeLabel, "providerNodeLabel");
        SectorAssetClassificationEvidence.requireCanonicalText(
                membershipSourceId, "membershipSourceId");
        SectorAssetClassificationEvidence.requireCanonicalText(
                membershipSourceRevision, "membershipSourceRevision");
        SectorAssetClassificationEvidence.requireCanonicalText(
                provenanceId, "provenanceId");
        Objects.requireNonNull(effectiveInterval,
                "effectiveInterval must not be null");
        SectorAssetClassificationEvidence.requireEvidenceTimeline(
                availableAt, capturedAt);
    }
}
