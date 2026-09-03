package com.wallstreetreceipts.api.domain.outcome.sectorreferencepair;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

import com.wallstreetreceipts.api.domain.master.AssetType;
import com.wallstreetreceipts.api.domain.outcome.observation.EndpointPriceResolution;
import com.wallstreetreceipts.api.domain.outcome.sectorassignment.SectorAssignmentResolution;
import com.wallstreetreceipts.api.domain.outcome.sectorassignment.SectorMappingEvidence.Mapped;
import com.wallstreetreceipts.api.domain.outcome.sectorreferencepair.SectorIndexDivisorContinuityEvidence.DivisorContinuity;
import com.wallstreetreceipts.api.domain.outcome.sectorreferencepair.SectorReferenceIndexEvidence.ReferenceIndexKind;
import com.wallstreetreceipts.api.domain.outcome.sectorreferencepair.SectorReferenceLevelObservation.ReferenceLevelField;

/** One sector reference-level pair or an exact typed upstream/local branch. */
public sealed interface SectorReferenceLevelPairResolution
        permits SectorReferenceLevelPairResolution.Resolved,
        SectorReferenceLevelPairResolution.NotApplicable,
        SectorReferenceLevelPairResolution.AssignmentUnavailable,
        SectorReferenceLevelPairResolution.EndpointAnchorUnavailable,
        SectorReferenceLevelPairResolution.EvidenceUnavailable {

    enum UnavailableReason {
        ENDPOINT_NOT_REACHED_AS_OF,
        REFERENCE_INDEX_MISSING_AS_OF,
        REFERENCE_MAPPING_EVIDENCE_LINK_MISMATCH,
        REFERENCE_TAXONOMY_ID_MISMATCH,
        REFERENCE_TAXONOMY_VERSION_MISMATCH,
        REFERENCE_TAXONOMY_DEFINITION_HASH_MISMATCH,
        REFERENCE_CANONICAL_NODE_ID_MISMATCH,
        REFERENCE_ASSET_TYPE_MISMATCH,
        REFERENCE_CURRENCY_MISMATCH,
        REFERENCE_KIND_MISMATCH,
        REFERENCE_EFFECTIVE_INTERVAL_MISMATCH,
        REFERENCE_INDEX_AMBIGUOUS,
        BASIS_LEVEL_MISSING_AS_OF,
        BASIS_REFERENCE_INDEX_EVIDENCE_LINK_MISMATCH,
        BASIS_REFERENCE_ASSET_MISMATCH,
        BASIS_REFERENCE_PROVIDER_MISMATCH,
        BASIS_REFERENCE_INDEX_MISMATCH,
        BASIS_REFERENCE_INDEX_DEFINITION_REVISION_MISMATCH,
        BASIS_REFERENCE_KIND_MISMATCH,
        BASIS_CURRENCY_MISMATCH,
        BASIS_CALCULATION_VENUE_MISMATCH,
        BASIS_CALENDAR_MISMATCH,
        BASIS_LEVEL_SOURCE_MISMATCH,
        BASIS_OBSERVED_AT_MISMATCH,
        BASIS_LEVEL_FIELD_MISMATCH,
        BASIS_LEVEL_AMBIGUOUS,
        ENDPOINT_LEVEL_MISSING_AS_OF,
        ENDPOINT_REFERENCE_INDEX_EVIDENCE_LINK_MISMATCH,
        ENDPOINT_REFERENCE_ASSET_MISMATCH,
        ENDPOINT_REFERENCE_PROVIDER_MISMATCH,
        ENDPOINT_REFERENCE_INDEX_MISMATCH,
        ENDPOINT_REFERENCE_INDEX_DEFINITION_REVISION_MISMATCH,
        ENDPOINT_REFERENCE_KIND_MISMATCH,
        ENDPOINT_CURRENCY_MISMATCH,
        ENDPOINT_CALCULATION_VENUE_MISMATCH,
        ENDPOINT_CALENDAR_MISMATCH,
        ENDPOINT_LEVEL_SOURCE_MISMATCH,
        ENDPOINT_OBSERVED_AT_MISMATCH,
        ENDPOINT_LEVEL_FIELD_MISMATCH,
        ENDPOINT_LEVEL_AMBIGUOUS,
        DIVISOR_CONTINUITY_EVIDENCE_MISSING_AS_OF,
        DIVISOR_REFERENCE_INDEX_EVIDENCE_LINK_MISMATCH,
        DIVISOR_REFERENCE_ASSET_MISMATCH,
        DIVISOR_REFERENCE_PROVIDER_MISMATCH,
        DIVISOR_REFERENCE_INDEX_MISMATCH,
        DIVISOR_REFERENCE_INDEX_DEFINITION_REVISION_MISMATCH,
        DIVISOR_REFERENCE_KIND_MISMATCH,
        DIVISOR_CURRENCY_MISMATCH,
        DIVISOR_CALCULATION_VENUE_MISMATCH,
        DIVISOR_CALENDAR_MISMATCH,
        DIVISOR_CONTINUITY_SOURCE_MISMATCH,
        DIVISOR_BASIS_OBSERVATION_LINK_MISMATCH,
        DIVISOR_ENDPOINT_OBSERVATION_LINK_MISMATCH,
        DIVISOR_COVERAGE_MISMATCH,
        DIVISOR_CONTINUITY_UNAVAILABLE,
        DIVISOR_CONTINUITY_EVIDENCE_AMBIGUOUS
    }

    enum EndpointAnchorUnavailableReason {
        CATALOG_NOT_KNOWN_AS_OF,
        CATALOG_EVIDENCE_MISMATCH,
        BINDING_NOT_KNOWN_AS_OF
    }

    /** Exact policy plus complete upstream assignment and endpoint-price receipts. */
    record ResolutionContext(
            SectorReferenceLevelPairPolicyVersion policyVersion,
            String policyDefinitionHash,
            SectorAssignmentResolution assignmentResolution,
            EndpointPriceResolution endpointPriceResolution) {

        public ResolutionContext {
            Objects.requireNonNull(policyVersion, "policyVersion must not be null");
            Objects.requireNonNull(policyDefinitionHash,
                    "policyDefinitionHash must not be null");
            if (!policyVersion.definitionHash().equals(policyDefinitionHash)) {
                throw new IllegalArgumentException(
                        "policyDefinitionHash must match policyVersion");
            }
            new SectorReferenceLevelPairRequest(
                    policyVersion, assignmentResolution, endpointPriceResolution,
                    List.of(), List.of(), List.of(), List.of());
        }
    }

    /** One exact binding, two exact levels, and one exact continuity attestation. */
    record Resolved(
            ResolutionContext context,
            SectorReferenceIndexEvidence referenceIndexEvidence,
            SectorReferenceLevelObservation basisLevelObservation,
            SectorReferenceLevelObservation endpointLevelObservation,
            SectorIndexDivisorContinuityEvidence divisorContinuityEvidence)
            implements SectorReferenceLevelPairResolution {

        public Resolved {
            Objects.requireNonNull(context, "context must not be null");
            Objects.requireNonNull(referenceIndexEvidence,
                    "referenceIndexEvidence must not be null");
            Objects.requireNonNull(basisLevelObservation,
                    "basisLevelObservation must not be null");
            Objects.requireNonNull(endpointLevelObservation,
                    "endpointLevelObservation must not be null");
            Objects.requireNonNull(divisorContinuityEvidence,
                    "divisorContinuityEvidence must not be null");
            if (!(context.assignmentResolution()
                    instanceof SectorAssignmentResolution.Resolved assignment)) {
                throw new IllegalArgumentException(
                        "resolved pair requires a resolved sector assignment");
            }
            validateResolvedEvidence(context, assignment, referenceIndexEvidence,
                    basisLevelObservation, endpointLevelObservation,
                    divisorContinuityEvidence);
        }
    }

    /** Exact upstream intentional non-applicability. */
    record NotApplicable(ResolutionContext context)
            implements SectorReferenceLevelPairResolution {

        public NotApplicable {
            Objects.requireNonNull(context, "context must not be null");
            if (!(context.assignmentResolution()
                    instanceof SectorAssignmentResolution.NotApplicable)) {
                throw new IllegalArgumentException(
                        "not-applicable pair requires a not-applicable assignment");
            }
        }
    }

    /** Exact upstream assignment evidence unavailability. */
    record AssignmentUnavailable(ResolutionContext context)
            implements SectorReferenceLevelPairResolution {

        public AssignmentUnavailable {
            Objects.requireNonNull(context, "context must not be null");
            if (!(context.assignmentResolution()
                    instanceof SectorAssignmentResolution.Unavailable)) {
                throw new IllegalArgumentException(
                        "assignment-unavailable pair requires an unavailable assignment");
            }
        }
    }

    /** Exact upstream catalog/binding failure that makes the UTC anchor unsafe. */
    record EndpointAnchorUnavailable(
            ResolutionContext context,
            EndpointAnchorUnavailableReason reason)
            implements SectorReferenceLevelPairResolution {

        public EndpointAnchorUnavailable {
            Objects.requireNonNull(context, "context must not be null");
            Objects.requireNonNull(reason, "reason must not be null");
            if (!(context.assignmentResolution()
                    instanceof SectorAssignmentResolution.Resolved)
                    || reason != SectorReferenceLevelPairRequest
                            .endpointAnchorUnavailableReason(
                                    context.endpointPriceResolution())) {
                throw new IllegalArgumentException(
                        "endpoint-anchor reason must match the exact catalog/binding facts");
            }
        }
    }

    /** Reference-side evidence unavailability after a resolved assignment. */
    record EvidenceUnavailable(
            ResolutionContext context,
            UnavailableReason reason) implements SectorReferenceLevelPairResolution {

        public EvidenceUnavailable {
            Objects.requireNonNull(context, "context must not be null");
            Objects.requireNonNull(reason, "reason must not be null");
            if (!(context.assignmentResolution()
                    instanceof SectorAssignmentResolution.Resolved)) {
                throw new IllegalArgumentException(
                        "reference evidence unavailability requires a resolved assignment");
            }
            requireNoEndpointAnchorFailure(context.endpointPriceResolution());
            var endpointContext = SectorReferenceLevelPairRequest.endpointContext(
                    context.endpointPriceResolution());
            boolean endpointNotReached = endpointContext.horizonResolution().window()
                    .endpointSession().closesAt()
                    .isAfter(endpointContext.evaluationAsOf());
            if ((reason == UnavailableReason.ENDPOINT_NOT_REACHED_AS_OF)
                    != endpointNotReached) {
                throw new IllegalArgumentException(
                        "endpoint-not-reached reason must exactly match the context cutoff");
            }
        }
    }

    private static void validateResolvedEvidence(
            ResolutionContext context,
            SectorAssignmentResolution.Resolved assignment,
            SectorReferenceIndexEvidence reference,
            SectorReferenceLevelObservation basisLevel,
            SectorReferenceLevelObservation endpointLevel,
            SectorIndexDivisorContinuityEvidence continuity) {
        requireNoEndpointAnchorFailure(context.endpointPriceResolution());
        var endpointContext = SectorReferenceLevelPairRequest.endpointContext(
                context.endpointPriceResolution());
        var basis = endpointContext.horizonResolution().window().context().basis();
        Instant endpoint = endpointContext.horizonResolution().window()
                .endpointSession().closesAt();
        Instant evaluationAsOf = endpointContext.evaluationAsOf();
        if (endpoint.isAfter(evaluationAsOf)) {
            throw new IllegalArgumentException(
                    "resolved reference pair requires a reached endpoint");
        }
        requireVisible(reference.availableAt(), reference.capturedAt(), evaluationAsOf);
        requireVisible(basisLevel.availableAt(), basisLevel.capturedAt(), evaluationAsOf);
        requireVisible(endpointLevel.availableAt(), endpointLevel.capturedAt(),
                evaluationAsOf);
        requireVisible(continuity.availableAt(), continuity.capturedAt(), evaluationAsOf);

        var mappingEvidence = assignment.mappingEvidence();
        String canonicalNodeId = ((Mapped) mappingEvidence.mappingDisposition())
                .canonicalNodeId();
        if (!reference.mappingEvidenceId().equals(
                    mappingEvidence.mappingEvidenceId())
                || !reference.mappingProviderEventId().equals(
                        mappingEvidence.providerEventId())
                || !reference.taxonomyId().equals(mappingEvidence.taxonomyId())
                || !reference.taxonomyVersion().equals(
                        mappingEvidence.taxonomyVersion())
                || !reference.taxonomyDefinitionHash().equals(
                        mappingEvidence.taxonomyDefinitionHash())
                || !reference.canonicalNodeId().equals(canonicalNodeId)
                || reference.referenceAssetType() != AssetType.INDEX
                || !reference.currency().equals(endpointContext.binding().currency())
                || reference.referenceKind()
                        != ReferenceIndexKind.PROVIDER_PUBLISHED_PRICE_INDEX
                || !reference.effectiveInterval().contains(basis.eventTime())
                || !reference.effectiveInterval().contains(endpoint)) {
            throw new IllegalArgumentException(
                    "referenceIndexEvidence must bind the exact mapping and interval");
        }
        requireLevelMatches(reference, basisLevel, basis.eventTime());
        requireLevelMatches(reference, endpointLevel, endpoint);
        if (!continuity.referenceIndexEvidenceId().equals(
                    reference.referenceIndexEvidenceId())
                || !continuity.referenceIndexProviderEventId().equals(
                        reference.providerEventId())
                || !sameReference(reference, continuity)
                || !continuity.basisObservationId().equals(
                        basisLevel.observationId())
                || !continuity.basisProviderEventId().equals(
                        basisLevel.providerEventId())
                || !continuity.endpointObservationId().equals(
                        endpointLevel.observationId())
                || !continuity.endpointProviderEventId().equals(
                        endpointLevel.providerEventId())
                || !continuity.coverageStartsAt().equals(basis.eventTime())
                || !continuity.coverageEndsAt().equals(endpoint)
                || continuity.divisorContinuity()
                        != DivisorContinuity
                                .PROVIDER_PUBLISHED_INDEX_DIVISOR_CONTINUITY_ATTESTED) {
            throw new IllegalArgumentException(
                    "divisor continuity must bind the exact selected reference levels");
        }
    }

    private static void requireLevelMatches(
            SectorReferenceIndexEvidence reference,
            SectorReferenceLevelObservation observation,
            Instant observedAt) {
        if (!observation.referenceIndexEvidenceId().equals(
                    reference.referenceIndexEvidenceId())
                || !observation.referenceIndexProviderEventId().equals(
                        reference.providerEventId())
                || !observation.referenceAssetId().equals(
                        reference.referenceAssetId())
                || observation.referenceAssetType()
                        != reference.referenceAssetType()
                || !observation.referenceProviderId().equals(
                        reference.referenceProviderId())
                || !observation.referenceIndexId().equals(
                        reference.referenceIndexId())
                || !observation.referenceIndexDefinitionRevision().equals(
                        reference.referenceIndexDefinitionRevision())
                || observation.referenceKind() != reference.referenceKind()
                || !observation.currency().equals(reference.currency())
                || !observation.calculationVenueId().equals(
                        reference.calculationVenueId())
                || !observation.calendarId().equals(reference.calendarId())
                || !observation.calendarRevision().equals(
                        reference.calendarRevision())
                || !observation.calendarSourceId().equals(
                        reference.calendarSourceId())
                || !observation.calendarSourceRevision().equals(
                        reference.calendarSourceRevision())
                || !observation.levelSourceId().equals(reference.levelSourceId())
                || !observation.levelSourceRevision().equals(
                        reference.levelSourceRevision())
                || !observation.observedAt().equals(observedAt)
                || observation.levelField()
                        != ReferenceLevelField.PROVIDER_PUBLISHED_INDEX_LEVEL) {
            throw new IllegalArgumentException(
                    "reference level must match the selected binding and exact UTC instant");
        }
    }

    private static boolean sameReference(
            SectorReferenceIndexEvidence reference,
            SectorIndexDivisorContinuityEvidence continuity) {
        return continuity.referenceAssetId().equals(reference.referenceAssetId())
                && continuity.referenceAssetType()
                        == reference.referenceAssetType()
                && continuity.referenceProviderId().equals(
                        reference.referenceProviderId())
                && continuity.referenceIndexId().equals(reference.referenceIndexId())
                && continuity.referenceIndexDefinitionRevision().equals(
                        reference.referenceIndexDefinitionRevision())
                && continuity.referenceKind() == reference.referenceKind()
                && continuity.currency().equals(reference.currency())
                && continuity.calculationVenueId().equals(
                        reference.calculationVenueId())
                && continuity.calendarId().equals(reference.calendarId())
                && continuity.calendarRevision().equals(reference.calendarRevision())
                && continuity.calendarSourceId().equals(
                        reference.calendarSourceId())
                && continuity.calendarSourceRevision().equals(
                        reference.calendarSourceRevision())
                && continuity.continuitySourceId().equals(
                        reference.continuitySourceId())
                && continuity.continuitySourceRevision().equals(
                        reference.continuitySourceRevision());
    }

    private static void requireVisible(
            Instant availableAt, Instant capturedAt, Instant evaluationAsOf) {
        if (availableAt.isAfter(evaluationAsOf)
                || capturedAt.isAfter(evaluationAsOf)) {
            throw new IllegalArgumentException(
                    "resolved reference evidence must be known by evaluationAsOf");
        }
    }

    private static void requireNoEndpointAnchorFailure(
            EndpointPriceResolution resolution) {
        if (SectorReferenceLevelPairRequest
                .endpointAnchorUnavailableReason(resolution) != null) {
            throw new IllegalArgumentException(
                    "catalog/binding anchor failure requires EndpointAnchorUnavailable");
        }
    }
}
