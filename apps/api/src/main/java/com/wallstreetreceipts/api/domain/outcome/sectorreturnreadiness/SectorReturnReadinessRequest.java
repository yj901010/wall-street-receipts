package com.wallstreetreceipts.api.domain.outcome.sectorreturnreadiness;

import java.util.Objects;

import com.wallstreetreceipts.api.domain.outcome.sectorreturn.SectorReturnPolicyVersion;
import com.wallstreetreceipts.api.domain.outcome.sectorreturn.SectorReturnResult;

/** One complete supplied sector-return result to classify for readiness. */
public record SectorReturnReadinessRequest(
        SectorReturnReadinessPolicyVersion policyVersion,
        SectorReturnResult sourceResult) {

    private static final String REQUIRED_SOURCE_HASH =
            "5aecd42c32ba69f0d21ab6e1ee1e3128cd31584724a6f46e176acd470204d0f7";

    public SectorReturnReadinessRequest {
        Objects.requireNonNull(policyVersion, "policyVersion must not be null");
        if (policyVersion
                != SectorReturnReadinessPolicyVersion
                        .SUPPLIED_LEAF_SECTOR_RETURN_READINESS_V1) {
            throw new IllegalArgumentException(
                    "policyVersion must be the sector-return readiness V1 policy");
        }
        Objects.requireNonNull(sourceResult, "sourceResult must not be null");
        var sourceContext = switch (sourceResult) {
            case SectorReturnResult.Available available -> available.context();
            case SectorReturnResult.NotApplicable notApplicable ->
                    notApplicable.context();
            case SectorReturnResult.AssignmentUnavailable unavailable ->
                    unavailable.context();
            case SectorReturnResult.EndpointAnchorUnavailable unavailable ->
                    unavailable.context();
            case SectorReturnResult.EvidenceUnavailable unavailable ->
                    unavailable.context();
            case SectorReturnResult.OutputUnavailable unavailable ->
                    unavailable.context();
        };
        if (sourceContext.policyVersion()
                != SectorReturnPolicyVersion
                        .SIGNED_SECTOR_BASIS_LEVEL_DENOMINATOR_SCALE_12_HALF_EVEN_V1
                || !REQUIRED_SOURCE_HASH.equals(
                        sourceContext.policyDefinitionHash())) {
            throw new IllegalArgumentException(
                    "sourceResult must use the required sector-return V1 policy");
        }
    }
}
