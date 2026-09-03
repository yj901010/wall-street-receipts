package com.wallstreetreceipts.api.domain.outcome.sectorassignment;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

import com.wallstreetreceipts.api.domain.master.AssetType;
import com.wallstreetreceipts.api.domain.outcome.sectorassignment.SectorAssignmentResolution.NotApplicableReason;
import com.wallstreetreceipts.api.domain.outcome.sectorassignment.SectorAssignmentResolution.ResolutionContext;
import com.wallstreetreceipts.api.domain.outcome.sectorassignment.SectorAssignmentResolution.UnavailableReason;
import com.wallstreetreceipts.api.domain.outcome.sectorassignment.SectorMappingEvidence.Mapped;
import com.wallstreetreceipts.api.domain.outcome.sectorassignment.SectorMappingEvidence.NotMapped;
import com.wallstreetreceipts.api.domain.outcome.sectorassignment.SectorMappingEvidence.NotMappedReason;
import com.wallstreetreceipts.api.domain.outcome.sectorassignment.SectorMappingEvidence.Recorded;

/** Applies the closed point-in-time WSR sector-assignment V1 policy. */
public final class SectorAssignmentSelector {

    private SectorAssignmentSelector() {
    }

    public static SectorAssignmentResolution select(
            SectorAssignmentRequest request) {
        Objects.requireNonNull(request, "request must not be null");
        ResolutionContext context = new ResolutionContext(
                request.policyVersion(),
                request.policyVersion().definitionHash(),
                request.basis(),
                request.assetId(),
                request.evaluationAsOf(),
                request.mappingSetId(),
                request.mappingSetVersion(),
                request.mappingSetDefinitionHash());

        List<SectorAssetClassificationEvidence> classifications =
                visibleClassifications(request);
        List<SectorMembershipEvidence> memberships = visibleMemberships(request);
        List<SectorMappingEvidence> mappings = visibleMappings(request);

        if (classifications.isEmpty()) {
            return unavailable(
                    context, UnavailableReason.CLASSIFICATION_MISSING_AS_OF);
        }
        if (classifications.stream().anyMatch(candidate ->
                !candidate.basis().equals(request.basis()))) {
            return unavailable(
                    context, UnavailableReason.CLASSIFICATION_BASIS_MISMATCH);
        }
        if (classifications.stream().anyMatch(candidate ->
                !candidate.assetId().equals(request.assetId()))) {
            return unavailable(
                    context, UnavailableReason.CLASSIFICATION_ASSET_MISMATCH);
        }
        if (classifications.stream().anyMatch(candidate ->
                !candidate.effectiveInterval().contains(
                        request.basis().eventTime()))) {
            return unavailable(context,
                    UnavailableReason.CLASSIFICATION_EFFECTIVE_INTERVAL_MISMATCH);
        }
        if (classifications.size() > 1) {
            return unavailable(
                    context, UnavailableReason.CLASSIFICATION_AMBIGUOUS);
        }

        SectorAssetClassificationEvidence classification =
                classifications.getFirst();
        NotApplicableReason notApplicableReason =
                notApplicableReason(classification);

        if (memberships.isEmpty()) {
            if (notApplicableReason != null) {
                return new SectorAssignmentResolution.NotApplicable(
                        context, classification, notApplicableReason);
            }
            return unavailable(
                    context, UnavailableReason.MEMBERSHIP_MISSING_AS_OF);
        }
        if (memberships.stream().anyMatch(candidate ->
                !candidate.basis().equals(request.basis()))) {
            return unavailable(
                    context, UnavailableReason.MEMBERSHIP_BASIS_MISMATCH);
        }
        if (memberships.stream().anyMatch(candidate ->
                !candidate.assetId().equals(request.assetId()))) {
            return unavailable(
                    context, UnavailableReason.MEMBERSHIP_ASSET_MISMATCH);
        }
        if (memberships.stream().anyMatch(candidate ->
                candidate.assetType() != classification.assetType())) {
            return unavailable(
                    context, UnavailableReason.MEMBERSHIP_ASSET_TYPE_MISMATCH);
        }
        if (memberships.stream().anyMatch(candidate ->
                !candidate.primaryVenueId().equals(
                        classification.primaryVenueId()))) {
            return unavailable(context,
                    UnavailableReason.MEMBERSHIP_PRIMARY_VENUE_MISMATCH);
        }
        if (memberships.stream().anyMatch(candidate ->
                !candidate.primaryVenueCountryCode().equals(
                        classification.primaryVenueCountryCode()))) {
            return unavailable(context, UnavailableReason
                    .MEMBERSHIP_PRIMARY_VENUE_COUNTRY_MISMATCH);
        }
        if (memberships.stream().anyMatch(candidate ->
                !candidate.currency().equals(classification.currency()))) {
            return unavailable(
                    context, UnavailableReason.MEMBERSHIP_CURRENCY_MISMATCH);
        }
        if (memberships.stream().anyMatch(candidate ->
                !candidate.effectiveInterval().contains(
                        request.basis().eventTime()))) {
            return unavailable(context,
                    UnavailableReason.MEMBERSHIP_EFFECTIVE_INTERVAL_MISMATCH);
        }
        if (notApplicableReason != null) {
            return unavailable(
                    context, UnavailableReason.OUT_OF_SCOPE_MEMBERSHIP_CONFLICT);
        }
        if (memberships.size() > 1) {
            return unavailable(
                    context, UnavailableReason.MEMBERSHIP_AMBIGUOUS);
        }

        SectorMembershipEvidence membership = memberships.getFirst();

        if (mappings.isEmpty()) {
            return unavailable(
                    context, UnavailableReason.MAPPING_MISSING_AS_OF);
        }
        if (mappings.stream().anyMatch(candidate ->
                !candidate.mappingSetId().equals(request.mappingSetId()))) {
            return unavailable(
                    context, UnavailableReason.MAPPING_SET_ID_MISMATCH);
        }
        if (mappings.stream().anyMatch(candidate ->
                !candidate.mappingSetVersion().equals(
                        request.mappingSetVersion()))) {
            return unavailable(
                    context, UnavailableReason.MAPPING_SET_VERSION_MISMATCH);
        }
        if (mappings.stream().anyMatch(candidate ->
                !candidate.mappingSetDefinitionHash().equals(
                        request.mappingSetDefinitionHash()))) {
            return unavailable(context,
                    UnavailableReason.MAPPING_SET_DEFINITION_HASH_MISMATCH);
        }
        if (mappings.stream().anyMatch(candidate ->
                !SectorAssignmentPolicyVersion.REQUIRED_MAPPING_POLICY_VERSION
                        .equals(candidate.mappingPolicyVersion()))) {
            return unavailable(
                    context, UnavailableReason.MAPPING_POLICY_VERSION_MISMATCH);
        }
        if (mappings.stream().anyMatch(candidate ->
                !SectorAssignmentPolicyVersion
                        .REQUIRED_MAPPING_POLICY_DEFINITION_HASH
                        .equals(candidate.mappingPolicyDefinitionHash()))) {
            return unavailable(context,
                    UnavailableReason.MAPPING_POLICY_DEFINITION_HASH_MISMATCH);
        }
        if (mappings.stream().anyMatch(candidate ->
                !SectorAssignmentPolicyVersion.REQUIRED_TAXONOMY_ID
                        .equals(candidate.taxonomyId()))) {
            return unavailable(
                    context, UnavailableReason.MAPPING_TAXONOMY_ID_MISMATCH);
        }
        if (mappings.stream().anyMatch(candidate ->
                !SectorAssignmentPolicyVersion.REQUIRED_TAXONOMY_VERSION
                        .equals(candidate.taxonomyVersion()))) {
            return unavailable(
                    context, UnavailableReason.MAPPING_TAXONOMY_VERSION_MISMATCH);
        }
        if (mappings.stream().anyMatch(candidate ->
                !SectorAssignmentPolicyVersion.REQUIRED_TAXONOMY_DEFINITION_HASH
                        .equals(candidate.taxonomyDefinitionHash()))) {
            return unavailable(context,
                    UnavailableReason.MAPPING_TAXONOMY_DEFINITION_HASH_MISMATCH);
        }
        if (mappings.stream().anyMatch(candidate ->
                !candidate.providerId().equals(membership.providerId()))) {
            return unavailable(
                    context, UnavailableReason.MAPPING_PROVIDER_ID_MISMATCH);
        }
        if (mappings.stream().anyMatch(candidate ->
                !candidate.providerSchemeId().equals(
                        membership.providerSchemeId()))) {
            return unavailable(context,
                    UnavailableReason.MAPPING_PROVIDER_SCHEME_ID_MISMATCH);
        }
        if (mappings.stream().anyMatch(candidate ->
                !candidate.providerSchemeRevision().equals(
                        membership.providerSchemeRevision()))) {
            return unavailable(context, UnavailableReason
                    .MAPPING_PROVIDER_SCHEME_REVISION_MISMATCH);
        }
        if (mappings.stream().anyMatch(candidate ->
                !candidate.providerNodeId().equals(
                        membership.providerNodeId()))) {
            return unavailable(
                    context, UnavailableReason.MAPPING_PROVIDER_NODE_ID_MISMATCH);
        }
        if (mappings.stream().anyMatch(candidate ->
                !candidate.effectiveInterval().contains(
                        request.basis().eventTime()))) {
            return unavailable(context,
                    UnavailableReason.MAPPING_EFFECTIVE_INTERVAL_MISMATCH);
        }
        if (mappings.stream().anyMatch(candidate ->
                candidate.mappingDisposition() instanceof Mapped
                        && !(candidate.providerNodeDefinition()
                                instanceof Recorded))) {
            return unavailable(context,
                    UnavailableReason.MAPPING_MAPPED_DEFINITION_REQUIRED);
        }
        if (mappings.stream().anyMatch(candidate ->
                candidate.mappingDisposition() instanceof Mapped mapped
                        && !SectorAssignmentPolicyVersion
                                .isAssignableCanonicalNodeId(
                                        mapped.canonicalNodeId()))) {
            return unavailable(context,
                    UnavailableReason.MAPPING_CANONICAL_NODE_NOT_ASSIGNABLE);
        }

        SectorMappingEvidence first = mappings.getFirst();
        if (mappings.stream().skip(1).anyMatch(candidate ->
                !candidate.mappingDisposition().equals(
                        first.mappingDisposition()))) {
            return unavailable(context, UnavailableReason.MAPPING_CONFLICT);
        }
        if (mappings.size() > 1) {
            return unavailable(context, UnavailableReason.MAPPING_AMBIGUOUS);
        }
        if (first.mappingDisposition() instanceof NotMapped notMapped) {
            return unavailable(context, notMappedReason(notMapped.reason()));
        }

        return new SectorAssignmentResolution.Resolved(
                context, classification, membership, first);
    }

    private static List<SectorAssetClassificationEvidence>
            visibleClassifications(SectorAssignmentRequest request) {
        return request.classificationCandidates().stream()
                .filter(candidate -> known(
                        candidate.availableAt(), candidate.capturedAt(),
                        request.evaluationAsOf()))
                .toList();
    }

    private static List<SectorMembershipEvidence> visibleMemberships(
            SectorAssignmentRequest request) {
        return request.membershipCandidates().stream()
                .filter(candidate -> known(
                        candidate.availableAt(), candidate.capturedAt(),
                        request.evaluationAsOf()))
                .toList();
    }

    private static List<SectorMappingEvidence> visibleMappings(
            SectorAssignmentRequest request) {
        return request.mappingCandidates().stream()
                .filter(candidate -> known(
                        candidate.availableAt(), candidate.capturedAt(),
                        request.evaluationAsOf()))
                .toList();
    }

    private static NotApplicableReason notApplicableReason(
            SectorAssetClassificationEvidence classification) {
        return classification.assetType() == AssetType.EQUITY
                ? null : NotApplicableReason.NON_EQUITY;
    }

    private static UnavailableReason notMappedReason(NotMappedReason reason) {
        return switch (reason) {
            case NO_CANONICAL_EQUIVALENT ->
                UnavailableReason.MAPPING_NOT_MAPPED_NO_CANONICAL_EQUIVALENT;
            case PROVIDER_NODE_TOO_BROAD ->
                UnavailableReason.MAPPING_NOT_MAPPED_PROVIDER_NODE_TOO_BROAD;
            case PROVIDER_DEFINITION_UNAVAILABLE ->
                UnavailableReason
                        .MAPPING_NOT_MAPPED_PROVIDER_DEFINITION_UNAVAILABLE;
        };
    }

    private static SectorAssignmentResolution.Unavailable unavailable(
            ResolutionContext context,
            UnavailableReason reason) {
        return new SectorAssignmentResolution.Unavailable(context, reason);
    }

    private static boolean known(
            Instant availableAt,
            Instant capturedAt,
            Instant evaluationAsOf) {
        return !availableAt.isAfter(evaluationAsOf)
                && !capturedAt.isAfter(evaluationAsOf);
    }
}
