package com.wallstreetreceipts.api.domain.outcome.sectorreferencepair;

import java.util.List;
import java.util.Objects;

import com.wallstreetreceipts.api.domain.outcome.observation.EndpointPricePolicyVersion;
import com.wallstreetreceipts.api.domain.outcome.observation.EndpointPriceResolution;
import com.wallstreetreceipts.api.domain.outcome.sectorassignment.SectorAssetClassificationEvidence;
import com.wallstreetreceipts.api.domain.outcome.sectorassignment.SectorAssignmentPolicyVersion;
import com.wallstreetreceipts.api.domain.outcome.sectorassignment.SectorAssignmentResolution;

/** Complete supplied upstream resolutions and reference candidates for one pair. */
public record SectorReferenceLevelPairRequest(
        SectorReferenceLevelPairPolicyVersion policyVersion,
        SectorAssignmentResolution assignmentResolution,
        EndpointPriceResolution endpointPriceResolution,
        List<SectorReferenceIndexEvidence> referenceIndexCandidates,
        List<SectorReferenceLevelObservation> basisLevelCandidates,
        List<SectorReferenceLevelObservation> endpointLevelCandidates,
        List<SectorIndexDivisorContinuityEvidence> divisorContinuityCandidates) {

    static final String REQUIRED_ASSIGNMENT_HASH =
            "52d9f705a3a8a965a6fca79d36bd94ed8836642f1a2c4e5f29a878d0a267311c";
    static final String REQUIRED_ENDPOINT_PRICE_HASH =
            "37e37aba9302d77366cef4129f77a82b7ccb2f1937bfffc0315ea8d0bc6b1f76";
    static final String REQUIRED_HORIZON_HASH =
            "550087efe7ddf2ba31974c89c2740ab79df986eefef48919c32c56a3232f8dc1";

    public SectorReferenceLevelPairRequest {
        Objects.requireNonNull(policyVersion, "policyVersion must not be null");
        if (policyVersion
                != SectorReferenceLevelPairPolicyVersion
                        .POINT_IN_TIME_EXACT_SECTOR_PRICE_INDEX_LEVEL_PAIR_V1) {
            throw new IllegalArgumentException(
                    "policyVersion must be the sector reference-level-pair V1 policy");
        }
        Objects.requireNonNull(assignmentResolution,
                "assignmentResolution must not be null");
        Objects.requireNonNull(endpointPriceResolution,
                "endpointPriceResolution must not be null");
        validateUpstreamTopology(assignmentResolution, endpointPriceResolution);
        referenceIndexCandidates = immutableNonNullCopy(
                referenceIndexCandidates, "referenceIndexCandidates");
        basisLevelCandidates = immutableNonNullCopy(
                basisLevelCandidates, "basisLevelCandidates");
        endpointLevelCandidates = immutableNonNullCopy(
                endpointLevelCandidates, "endpointLevelCandidates");
        divisorContinuityCandidates = immutableNonNullCopy(
                divisorContinuityCandidates, "divisorContinuityCandidates");
    }

    static void validateUpstreamTopology(
            SectorAssignmentResolution assignmentResolution,
            EndpointPriceResolution endpointPriceResolution) {
        var assignmentContext = assignmentContext(assignmentResolution);
        if (assignmentContext.policyVersion()
                != SectorAssignmentPolicyVersion
                        .POINT_IN_TIME_EXPLICIT_WSR_ECONOMIC_ACTIVITY_SECTOR_ASSIGNMENT_V1
                || !REQUIRED_ASSIGNMENT_HASH.equals(
                        assignmentContext.policyDefinitionHash())) {
            throw new IllegalArgumentException(
                    "assignmentResolution must use the required sector-assignment V1 policy");
        }

        var endpointContext = endpointContext(endpointPriceResolution);
        if (endpointContext.policyVersion()
                != EndpointPricePolicyVersion
                        .OFFICIAL_PRIMARY_VENUE_CLOSE_SPLIT_ADJUSTED_V1
                || !REQUIRED_ENDPOINT_PRICE_HASH.equals(
                        endpointContext.policyDefinitionHash())) {
            throw new IllegalArgumentException(
                    "endpointPriceResolution must use the required endpoint-price V1 policy");
        }
        var horizonContext = endpointContext.horizonResolution().window().context();
        if (!REQUIRED_HORIZON_HASH.equals(horizonContext.policyDefinitionHash())) {
            throw new IllegalArgumentException(
                    "endpointPriceResolution must retain the required strict-horizon V1 policy");
        }
        if (!assignmentContext.basis().equals(horizonContext.basis())
                || !assignmentContext.evaluationAsOf().equals(
                        endpointContext.evaluationAsOf())) {
            throw new IllegalArgumentException(
                    "assignment and endpoint contexts must match basis and evaluationAsOf");
        }
        if (endpointAnchorUnavailableReason(endpointPriceResolution) != null) {
            return;
        }
        if (!assignmentContext.assetId().equals(
                endpointContext.binding().assetId())) {
            throw new IllegalArgumentException(
                    "assignment and usable endpoint contexts must match asset");
        }

        SectorAssetClassificationEvidence classification = switch (
                assignmentResolution) {
            case SectorAssignmentResolution.Resolved resolved ->
                resolved.classificationEvidence();
            case SectorAssignmentResolution.NotApplicable notApplicable ->
                notApplicable.classificationEvidence();
            case SectorAssignmentResolution.Unavailable unavailable -> null;
        };
        if (classification != null
                && (!classification.primaryVenueId().equals(
                        endpointContext.binding().primaryVenueId())
                || !classification.currency().equals(
                        endpointContext.binding().currency()))) {
            throw new IllegalArgumentException(
                    "classified assignment and endpoint binding must match venue and currency");
        }
    }

    static SectorAssignmentResolution.ResolutionContext assignmentContext(
            SectorAssignmentResolution resolution) {
        return switch (resolution) {
            case SectorAssignmentResolution.Resolved resolved -> resolved.context();
            case SectorAssignmentResolution.NotApplicable notApplicable ->
                notApplicable.context();
            case SectorAssignmentResolution.Unavailable unavailable ->
                unavailable.context();
        };
    }

    static EndpointPriceResolution.ResolutionContext endpointContext(
            EndpointPriceResolution resolution) {
        return switch (resolution) {
            case EndpointPriceResolution.Resolved resolved -> resolved.context();
            case EndpointPriceResolution.Unavailable unavailable ->
                unavailable.context();
        };
    }

    static SectorReferenceLevelPairResolution.EndpointAnchorUnavailableReason
            endpointAnchorUnavailableReason(EndpointPriceResolution resolution) {
        var context = endpointContext(resolution);
        var catalog = context.catalogEvidence();
        var horizon = context.horizonResolution().window().context();
        if (catalog.availableAt().isAfter(context.evaluationAsOf())
                || catalog.capturedAt().isAfter(context.evaluationAsOf())) {
            return SectorReferenceLevelPairResolution
                    .EndpointAnchorUnavailableReason.CATALOG_NOT_KNOWN_AS_OF;
        }
        if (!catalog.calendarId().equals(horizon.calendarId())
                || !catalog.catalogRevision().equals(horizon.catalogRevision())) {
            return SectorReferenceLevelPairResolution
                    .EndpointAnchorUnavailableReason.CATALOG_EVIDENCE_MISMATCH;
        }
        var binding = context.binding();
        if (binding.availableAt().isAfter(context.evaluationAsOf())
                || binding.capturedAt().isAfter(context.evaluationAsOf())) {
            return SectorReferenceLevelPairResolution
                    .EndpointAnchorUnavailableReason.BINDING_NOT_KNOWN_AS_OF;
        }
        return null;
    }

    private static <T> List<T> immutableNonNullCopy(
            List<T> values, String field) {
        Objects.requireNonNull(values, field + " must not be null");
        for (T value : values) {
            Objects.requireNonNull(value, field + " must not contain null");
        }
        return List.copyOf(values);
    }
}
