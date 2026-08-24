package com.wallstreetreceipts.api.domain.outcome.sectorassignment;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

import com.wallstreetreceipts.api.domain.PersistentInstant;
import com.wallstreetreceipts.api.domain.outcome.horizon.OutcomeBasis;

/** Complete request-scoped evidence for one point-in-time sector assignment. */
public record SectorAssignmentRequest(
        SectorAssignmentPolicyVersion policyVersion,
        OutcomeBasis basis,
        String assetId,
        Instant evaluationAsOf,
        String mappingSetId,
        String mappingSetVersion,
        String mappingSetDefinitionHash,
        List<SectorAssetClassificationEvidence> classificationCandidates,
        List<SectorMembershipEvidence> membershipCandidates,
        List<SectorMappingEvidence> mappingCandidates) {

    public SectorAssignmentRequest {
        Objects.requireNonNull(policyVersion, "policyVersion must not be null");
        if (policyVersion
                != SectorAssignmentPolicyVersion
                        .POINT_IN_TIME_EXPLICIT_WSR_ECONOMIC_ACTIVITY_SECTOR_ASSIGNMENT_V1) {
            throw new IllegalArgumentException(
                    "policyVersion must be the sector-assignment V1 policy");
        }
        Objects.requireNonNull(basis, "basis must not be null");
        SectorAssetClassificationEvidence.requireCanonicalText(assetId, "assetId");
        PersistentInstant.requireMicrosecondPrecision(
                evaluationAsOf, "evaluationAsOf");
        if (evaluationAsOf.isBefore(basis.eventTime())) {
            throw new IllegalArgumentException(
                    "evaluationAsOf must not precede basis.eventTime");
        }
        SectorAssetClassificationEvidence.requireCanonicalText(
                mappingSetId, "mappingSetId");
        SectorAssetClassificationEvidence.requireCanonicalText(
                mappingSetVersion, "mappingSetVersion");
        SectorAssetClassificationEvidence.requireSha256(
                mappingSetDefinitionHash, "mappingSetDefinitionHash");

        Objects.requireNonNull(classificationCandidates,
                "classificationCandidates must not be null");
        for (SectorAssetClassificationEvidence candidate
                : classificationCandidates) {
            Objects.requireNonNull(candidate,
                    "classificationCandidates must not contain null");
        }
        classificationCandidates = List.copyOf(classificationCandidates);

        Objects.requireNonNull(membershipCandidates,
                "membershipCandidates must not be null");
        for (SectorMembershipEvidence candidate : membershipCandidates) {
            Objects.requireNonNull(candidate,
                    "membershipCandidates must not contain null");
        }
        membershipCandidates = List.copyOf(membershipCandidates);

        Objects.requireNonNull(mappingCandidates,
                "mappingCandidates must not be null");
        for (SectorMappingEvidence candidate : mappingCandidates) {
            Objects.requireNonNull(candidate,
                    "mappingCandidates must not contain null");
        }
        mappingCandidates = List.copyOf(mappingCandidates);
    }
}
