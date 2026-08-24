package com.wallstreetreceipts.api.domain.outcome.sectorreturn;

import java.util.Objects;

import com.wallstreetreceipts.api.domain.outcome.sectorreferencepair
        .SectorReferenceLevelPairPolicyVersion;
import com.wallstreetreceipts.api.domain.outcome.sectorreferencepair
        .SectorReferenceLevelPairResolution;

/** One complete sector reference-level-pair resolution supplied to the return leaf. */
public record SectorReturnInput(
        SectorReturnPolicyVersion policyVersion,
        SectorReferenceLevelPairResolution referenceLevelPairResolution) {

    private static final String REQUIRED_REFERENCE_LEVEL_PAIR_POLICY_HASH =
            "4224648ba01104fd3e96319158c7d6b42da472e9aa6f2ab22ef9fccf43da7e4a";

    public SectorReturnInput {
        Objects.requireNonNull(policyVersion, "policyVersion must not be null");
        if (policyVersion
                != SectorReturnPolicyVersion
                        .SIGNED_SECTOR_BASIS_LEVEL_DENOMINATOR_SCALE_12_HALF_EVEN_V1) {
            throw new IllegalArgumentException(
                    "policyVersion must be the sector-return V1 policy");
        }
        Objects.requireNonNull(referenceLevelPairResolution,
                "referenceLevelPairResolution must not be null");
        var pairContext = pairContext(referenceLevelPairResolution);
        if (pairContext.policyVersion()
                != SectorReferenceLevelPairPolicyVersion
                        .POINT_IN_TIME_EXACT_SECTOR_PRICE_INDEX_LEVEL_PAIR_V1
                || !REQUIRED_REFERENCE_LEVEL_PAIR_POLICY_HASH.equals(
                        pairContext.policyDefinitionHash())) {
            throw new IllegalArgumentException(
                    "referenceLevelPairResolution must use the required sector pair V1 policy");
        }
    }

    static SectorReferenceLevelPairResolution.ResolutionContext pairContext(
            SectorReferenceLevelPairResolution resolution) {
        return switch (resolution) {
            case SectorReferenceLevelPairResolution.Resolved resolved ->
                    resolved.context();
            case SectorReferenceLevelPairResolution.NotApplicable notApplicable ->
                    notApplicable.context();
            case SectorReferenceLevelPairResolution.AssignmentUnavailable unavailable ->
                    unavailable.context();
            case SectorReferenceLevelPairResolution.EndpointAnchorUnavailable unavailable ->
                    unavailable.context();
            case SectorReferenceLevelPairResolution.EvidenceUnavailable unavailable ->
                    unavailable.context();
        };
    }
}
