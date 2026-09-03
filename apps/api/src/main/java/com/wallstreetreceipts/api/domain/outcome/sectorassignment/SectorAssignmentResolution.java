package com.wallstreetreceipts.api.domain.outcome.sectorassignment;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

import com.wallstreetreceipts.api.domain.PersistentInstant;
import com.wallstreetreceipts.api.domain.master.AssetType;
import com.wallstreetreceipts.api.domain.outcome.horizon.OutcomeBasis;
import com.wallstreetreceipts.api.domain.outcome.sectorassignment.SectorMappingEvidence.Mapped;
import com.wallstreetreceipts.api.domain.outcome.sectorassignment.SectorMappingEvidence.Recorded;

/** Explicit assignment, intentional non-applicability, or evidence unavailability. */
public sealed interface SectorAssignmentResolution
        permits SectorAssignmentResolution.Resolved,
        SectorAssignmentResolution.NotApplicable,
        SectorAssignmentResolution.Unavailable {

    enum NotApplicableReason {
        NON_EQUITY
    }

    enum UnavailableReason {
        CLASSIFICATION_MISSING_AS_OF,
        CLASSIFICATION_BASIS_MISMATCH,
        CLASSIFICATION_ASSET_MISMATCH,
        CLASSIFICATION_EFFECTIVE_INTERVAL_MISMATCH,
        CLASSIFICATION_AMBIGUOUS,
        MEMBERSHIP_MISSING_AS_OF,
        MEMBERSHIP_BASIS_MISMATCH,
        MEMBERSHIP_ASSET_MISMATCH,
        MEMBERSHIP_ASSET_TYPE_MISMATCH,
        MEMBERSHIP_PRIMARY_VENUE_MISMATCH,
        MEMBERSHIP_PRIMARY_VENUE_COUNTRY_MISMATCH,
        MEMBERSHIP_CURRENCY_MISMATCH,
        MEMBERSHIP_EFFECTIVE_INTERVAL_MISMATCH,
        OUT_OF_SCOPE_MEMBERSHIP_CONFLICT,
        MEMBERSHIP_AMBIGUOUS,
        MAPPING_MISSING_AS_OF,
        MAPPING_SET_ID_MISMATCH,
        MAPPING_SET_VERSION_MISMATCH,
        MAPPING_SET_DEFINITION_HASH_MISMATCH,
        MAPPING_POLICY_VERSION_MISMATCH,
        MAPPING_POLICY_DEFINITION_HASH_MISMATCH,
        MAPPING_TAXONOMY_ID_MISMATCH,
        MAPPING_TAXONOMY_VERSION_MISMATCH,
        MAPPING_TAXONOMY_DEFINITION_HASH_MISMATCH,
        MAPPING_PROVIDER_ID_MISMATCH,
        MAPPING_PROVIDER_SCHEME_ID_MISMATCH,
        MAPPING_PROVIDER_SCHEME_REVISION_MISMATCH,
        MAPPING_PROVIDER_NODE_ID_MISMATCH,
        MAPPING_EFFECTIVE_INTERVAL_MISMATCH,
        MAPPING_MAPPED_DEFINITION_REQUIRED,
        MAPPING_CANONICAL_NODE_NOT_ASSIGNABLE,
        MAPPING_CONFLICT,
        MAPPING_AMBIGUOUS,
        MAPPING_NOT_MAPPED_NO_CANONICAL_EQUIVALENT,
        MAPPING_NOT_MAPPED_PROVIDER_NODE_TOO_BROAD,
        MAPPING_NOT_MAPPED_PROVIDER_DEFINITION_UNAVAILABLE
    }

    /** Stable request and caller-attested mapping-set identity for every result. */
    record ResolutionContext(
            SectorAssignmentPolicyVersion policyVersion,
            String policyDefinitionHash,
            OutcomeBasis basis,
            String assetId,
            Instant evaluationAsOf,
            String mappingSetId,
            String mappingSetVersion,
            String mappingSetDefinitionHash) {

        public ResolutionContext {
            Objects.requireNonNull(policyVersion,
                    "policyVersion must not be null");
            Objects.requireNonNull(policyDefinitionHash,
                    "policyDefinitionHash must not be null");
            Objects.requireNonNull(basis, "basis must not be null");
            SectorAssetClassificationEvidence.requireCanonicalText(
                    assetId, "assetId");
            PersistentInstant.requireMicrosecondPrecision(
                    evaluationAsOf, "evaluationAsOf");
            SectorAssetClassificationEvidence.requireCanonicalText(
                    mappingSetId, "mappingSetId");
            SectorAssetClassificationEvidence.requireCanonicalText(
                    mappingSetVersion, "mappingSetVersion");
            SectorAssetClassificationEvidence.requireSha256(
                    mappingSetDefinitionHash, "mappingSetDefinitionHash");
            if (policyVersion
                    != SectorAssignmentPolicyVersion
                            .POINT_IN_TIME_EXPLICIT_WSR_ECONOMIC_ACTIVITY_SECTOR_ASSIGNMENT_V1) {
                throw new IllegalArgumentException(
                        "policyVersion must be the sector-assignment V1 policy");
            }
            if (!policyVersion.definitionHash().equals(policyDefinitionHash)) {
                throw new IllegalArgumentException(
                        "policyDefinitionHash must match policyVersion");
            }
            new SectorAssignmentRequest(
                    policyVersion, basis, assetId, evaluationAsOf,
                    mappingSetId, mappingSetVersion, mappingSetDefinitionHash,
                    List.of(), List.of(), List.of());
        }
    }

    /** One classification, membership, and mapped canonical leaf satisfy V1. */
    record Resolved(
            ResolutionContext context,
            SectorAssetClassificationEvidence classificationEvidence,
            SectorMembershipEvidence membershipEvidence,
            SectorMappingEvidence mappingEvidence)
            implements SectorAssignmentResolution {

        public Resolved {
            Objects.requireNonNull(context, "context must not be null");
            Objects.requireNonNull(classificationEvidence,
                    "classificationEvidence must not be null");
            Objects.requireNonNull(membershipEvidence,
                    "membershipEvidence must not be null");
            Objects.requireNonNull(mappingEvidence,
                    "mappingEvidence must not be null");
            requireLocallyValidClassification(context, classificationEvidence);
            if (notApplicableReason(classificationEvidence) != null) {
                throw new IllegalArgumentException(
                        "resolved classification must be an equity");
            }
            requireLocallyValidMembership(
                    context, classificationEvidence, membershipEvidence);
            requireLocallyValidMapping(context, membershipEvidence, mappingEvidence);
        }
    }

    /** Visible coherent classification proves intentional sector non-applicability. */
    record NotApplicable(
            ResolutionContext context,
            SectorAssetClassificationEvidence classificationEvidence,
            NotApplicableReason reason) implements SectorAssignmentResolution {

        public NotApplicable {
            Objects.requireNonNull(context, "context must not be null");
            Objects.requireNonNull(classificationEvidence,
                    "classificationEvidence must not be null");
            Objects.requireNonNull(reason, "reason must not be null");
            requireLocallyValidClassification(context, classificationEvidence);
            if (reason != notApplicableReason(classificationEvidence)) {
                throw new IllegalArgumentException(
                        "reason must match the exact sector V1 scope");
            }
        }
    }

    /** Only the selector attests PIT filtering, precedence, and cardinality. */
    record Unavailable(
            ResolutionContext context,
            UnavailableReason reason) implements SectorAssignmentResolution {

        public Unavailable {
            Objects.requireNonNull(context, "context must not be null");
            Objects.requireNonNull(reason, "reason must not be null");
        }
    }

    private static void requireLocallyValidClassification(
            ResolutionContext context,
            SectorAssetClassificationEvidence classification) {
        requireVisible(
                classification.availableAt(), classification.capturedAt(), context);
        if (!classification.basis().equals(context.basis())
                || !classification.assetId().equals(context.assetId())
                || !classification.effectiveInterval()
                        .contains(context.basis().eventTime())) {
            throw new IllegalArgumentException(
                    "classification must match the context and contain its basis event");
        }
    }

    private static void requireLocallyValidMembership(
            ResolutionContext context,
            SectorAssetClassificationEvidence classification,
            SectorMembershipEvidence membership) {
        requireVisible(membership.availableAt(), membership.capturedAt(), context);
        if (!membership.basis().equals(context.basis())
                || !membership.assetId().equals(context.assetId())
                || membership.assetType() != classification.assetType()
                || !membership.primaryVenueId().equals(
                        classification.primaryVenueId())
                || !membership.primaryVenueCountryCode().equals(
                        classification.primaryVenueCountryCode())
                || !membership.currency().equals(classification.currency())
                || !membership.effectiveInterval()
                        .contains(context.basis().eventTime())) {
            throw new IllegalArgumentException(
                    "membership must cohere with the selected classification");
        }
    }

    private static void requireLocallyValidMapping(
            ResolutionContext context,
            SectorMembershipEvidence membership,
            SectorMappingEvidence mapping) {
        requireVisible(mapping.availableAt(), mapping.capturedAt(), context);
        if (!mapping.mappingSetId().equals(context.mappingSetId())
                || !mapping.mappingSetVersion().equals(
                        context.mappingSetVersion())
                || !mapping.mappingSetDefinitionHash().equals(
                        context.mappingSetDefinitionHash())) {
            throw new IllegalArgumentException(
                    "mapping must match the caller-attested mapping-set identity");
        }
        if (!SectorAssignmentPolicyVersion.REQUIRED_MAPPING_POLICY_VERSION.equals(
                    mapping.mappingPolicyVersion())
                || !SectorAssignmentPolicyVersion
                        .REQUIRED_MAPPING_POLICY_DEFINITION_HASH.equals(
                                mapping.mappingPolicyDefinitionHash())
                || !SectorAssignmentPolicyVersion.REQUIRED_TAXONOMY_ID.equals(
                        mapping.taxonomyId())
                || !SectorAssignmentPolicyVersion.REQUIRED_TAXONOMY_VERSION.equals(
                        mapping.taxonomyVersion())
                || !SectorAssignmentPolicyVersion
                        .REQUIRED_TAXONOMY_DEFINITION_HASH.equals(
                                mapping.taxonomyDefinitionHash())) {
            throw new IllegalArgumentException(
                    "mapping must use the exact ADR-028 policy and taxonomy");
        }
        if (!mapping.providerId().equals(membership.providerId())
                || !mapping.providerSchemeId().equals(
                        membership.providerSchemeId())
                || !mapping.providerSchemeRevision().equals(
                        membership.providerSchemeRevision())
                || !mapping.providerNodeId().equals(membership.providerNodeId())
                || !mapping.effectiveInterval()
                        .contains(context.basis().eventTime())) {
            throw new IllegalArgumentException(
                    "mapping must match the membership provider identity and basis event");
        }
        if (!(mapping.mappingDisposition() instanceof Mapped mapped)
                || !(mapping.providerNodeDefinition() instanceof Recorded)
                || !SectorAssignmentPolicyVersion.isAssignableCanonicalNodeId(
                        mapped.canonicalNodeId())) {
            throw new IllegalArgumentException(
                    "mapping must be one recorded-definition closed-leaf mapping");
        }
    }

    private static void requireVisible(
            Instant availableAt,
            Instant capturedAt,
            ResolutionContext context) {
        if (availableAt.isAfter(context.evaluationAsOf())
                || capturedAt.isAfter(context.evaluationAsOf())) {
            throw new IllegalArgumentException(
                    "resolved evidence must be known by evaluationAsOf");
        }
    }

    private static NotApplicableReason notApplicableReason(
            SectorAssetClassificationEvidence classification) {
        return classification.assetType() == AssetType.EQUITY
                ? null : NotApplicableReason.NON_EQUITY;
    }
}
