package com.wallstreetreceipts.api.domain.outcome.benchmarkreferencepair;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

import com.wallstreetreceipts.api.domain.outcome.benchmarkassignment.BenchmarkAssignmentResolution;
import com.wallstreetreceipts.api.domain.outcome.benchmarkreferencepair.BenchmarkIndexDivisorContinuityEvidence.DivisorContinuity;
import com.wallstreetreceipts.api.domain.outcome.benchmarkreferencepair.BenchmarkReferenceIndexEvidence.ReferenceIndexKind;
import com.wallstreetreceipts.api.domain.outcome.benchmarkreferencepair.BenchmarkReferenceLevelObservation.ReferenceLevelField;
import com.wallstreetreceipts.api.domain.outcome.benchmarkreferencepair.BenchmarkReferenceLevelPairResolution.ResolutionContext;
import com.wallstreetreceipts.api.domain.outcome.benchmarkreferencepair.BenchmarkReferenceLevelPairResolution.UnavailableReason;

/** Pure PIT selection of one independent benchmark reference-level pair. */
public final class BenchmarkReferenceLevelPairSelector {

    private BenchmarkReferenceLevelPairSelector() {
    }

    public static BenchmarkReferenceLevelPairResolution select(
            BenchmarkReferenceLevelPairRequest request) {
        Objects.requireNonNull(request, "request must not be null");
        ResolutionContext context = new ResolutionContext(
                request.policyVersion(),
                request.policyVersion().definitionHash(),
                request.assignmentResolution(),
                request.endpointPriceResolution());

        if (request.assignmentResolution()
                instanceof BenchmarkAssignmentResolution.NotApplicable) {
            return new BenchmarkReferenceLevelPairResolution.NotApplicable(context);
        }
        if (request.assignmentResolution()
                instanceof BenchmarkAssignmentResolution.Unavailable) {
            return new BenchmarkReferenceLevelPairResolution.AssignmentUnavailable(context);
        }
        BenchmarkAssignmentResolution.Resolved assignment =
                (BenchmarkAssignmentResolution.Resolved)
                        request.assignmentResolution();
        var anchorReason = BenchmarkReferenceLevelPairRequest
                .endpointAnchorUnavailableReason(request.endpointPriceResolution());
        if (anchorReason != null) {
            return new BenchmarkReferenceLevelPairResolution
                    .EndpointAnchorUnavailable(context, anchorReason);
        }
        var endpointContext = BenchmarkReferenceLevelPairRequest.endpointContext(
                request.endpointPriceResolution());
        var basis = endpointContext.horizonResolution().window().context().basis();
        Instant endpoint = endpointContext.horizonResolution().window()
                .endpointSession().closesAt();
        Instant evaluationAsOf = endpointContext.evaluationAsOf();
        if (endpoint.isAfter(evaluationAsOf)) {
            return unavailable(context, UnavailableReason.ENDPOINT_NOT_REACHED_AS_OF);
        }

        List<BenchmarkReferenceIndexEvidence> references = known(
                request.referenceIndexCandidates(), evaluationAsOf);
        List<BenchmarkReferenceLevelObservation> basisLevels = known(
                request.basisLevelCandidates(), evaluationAsOf);
        List<BenchmarkReferenceLevelObservation> endpointLevels = known(
                request.endpointLevelCandidates(), evaluationAsOf);
        List<BenchmarkIndexDivisorContinuityEvidence> continuities = known(
                request.divisorContinuityCandidates(), evaluationAsOf);

        if (references.isEmpty()) {
            return unavailable(context,
                    UnavailableReason.REFERENCE_INDEX_MISSING_AS_OF);
        }
        var assignmentEvidence = assignment.assignmentEvidence();
        if (references.stream().anyMatch(reference ->
                !reference.assignmentEvidenceId().equals(
                        assignmentEvidence.assignmentEvidenceId())
                || !reference.assignmentProviderEventId().equals(
                        assignmentEvidence.providerEventId()))) {
            return unavailable(context,
                    UnavailableReason.REFERENCE_ASSIGNMENT_EVIDENCE_LINK_MISMATCH);
        }
        if (references.stream().anyMatch(reference ->
                !reference.benchmarkAssetId().equals(
                        assignmentEvidence.benchmarkAssetId()))) {
            return unavailable(context,
                    UnavailableReason.REFERENCE_BENCHMARK_ASSET_ID_MISMATCH);
        }
        if (references.stream().anyMatch(reference ->
                reference.benchmarkAssetType()
                        != assignmentEvidence.benchmarkAssetType())) {
            return unavailable(context,
                    UnavailableReason.REFERENCE_BENCHMARK_ASSET_TYPE_MISMATCH);
        }
        if (references.stream().anyMatch(reference ->
                !reference.currency().equals(endpointContext.binding().currency()))) {
            return unavailable(context,
                    UnavailableReason.REFERENCE_CURRENCY_MISMATCH);
        }
        if (references.stream().anyMatch(reference ->
                reference.referenceKind()
                        != ReferenceIndexKind.PROVIDER_PUBLISHED_PRICE_INDEX)) {
            return unavailable(context, UnavailableReason.REFERENCE_KIND_MISMATCH);
        }
        if (references.stream().anyMatch(reference ->
                !reference.effectiveInterval().contains(basis.eventTime())
                || !reference.effectiveInterval().contains(endpoint))) {
            return unavailable(context,
                    UnavailableReason.REFERENCE_EFFECTIVE_INTERVAL_MISMATCH);
        }
        if (references.size() > 1) {
            return unavailable(context, UnavailableReason.REFERENCE_INDEX_AMBIGUOUS);
        }
        BenchmarkReferenceIndexEvidence reference = references.getFirst();

        if (basisLevels.isEmpty()) {
            return unavailable(context, UnavailableReason.BASIS_LEVEL_MISSING_AS_OF);
        }
        UnavailableReason basisReason = levelMismatch(
                reference, basisLevels, basis.eventTime(), true);
        if (basisReason != null) {
            return unavailable(context, basisReason);
        }
        if (basisLevels.size() > 1) {
            return unavailable(context, UnavailableReason.BASIS_LEVEL_AMBIGUOUS);
        }
        BenchmarkReferenceLevelObservation basisLevel = basisLevels.getFirst();

        if (endpointLevels.isEmpty()) {
            return unavailable(context,
                    UnavailableReason.ENDPOINT_LEVEL_MISSING_AS_OF);
        }
        UnavailableReason endpointReason = levelMismatch(
                reference, endpointLevels, endpoint, false);
        if (endpointReason != null) {
            return unavailable(context, endpointReason);
        }
        if (endpointLevels.size() > 1) {
            return unavailable(context, UnavailableReason.ENDPOINT_LEVEL_AMBIGUOUS);
        }
        BenchmarkReferenceLevelObservation endpointLevel = endpointLevels.getFirst();

        if (continuities.isEmpty()) {
            return unavailable(context,
                    UnavailableReason.DIVISOR_CONTINUITY_EVIDENCE_MISSING_AS_OF);
        }
        UnavailableReason continuityReason = continuityMismatch(
                reference, basisLevel, endpointLevel, continuities,
                basis.eventTime(), endpoint);
        if (continuityReason != null) {
            return unavailable(context, continuityReason);
        }
        if (continuities.size() > 1) {
            return unavailable(context,
                    UnavailableReason.DIVISOR_CONTINUITY_EVIDENCE_AMBIGUOUS);
        }
        return new BenchmarkReferenceLevelPairResolution.Resolved(
                context, reference, basisLevel, endpointLevel,
                continuities.getFirst());
    }

    private static UnavailableReason levelMismatch(
            BenchmarkReferenceIndexEvidence reference,
            List<BenchmarkReferenceLevelObservation> levels,
            Instant observedAt,
            boolean basis) {
        if (levels.stream().anyMatch(level ->
                !level.referenceIndexEvidenceId().equals(
                        reference.referenceIndexEvidenceId())
                || !level.referenceIndexProviderEventId().equals(
                        reference.providerEventId()))) {
            return basis
                    ? UnavailableReason.BASIS_REFERENCE_INDEX_EVIDENCE_LINK_MISMATCH
                    : UnavailableReason.ENDPOINT_REFERENCE_INDEX_EVIDENCE_LINK_MISMATCH;
        }
        if (levels.stream().anyMatch(level ->
                !level.benchmarkAssetId().equals(reference.benchmarkAssetId())
                || level.benchmarkAssetType() != reference.benchmarkAssetType())) {
            return basis
                    ? UnavailableReason.BASIS_BENCHMARK_ASSET_MISMATCH
                    : UnavailableReason.ENDPOINT_BENCHMARK_ASSET_MISMATCH;
        }
        if (levels.stream().anyMatch(level ->
                !level.referenceProviderId().equals(reference.referenceProviderId()))) {
            return basis
                    ? UnavailableReason.BASIS_REFERENCE_PROVIDER_MISMATCH
                    : UnavailableReason.ENDPOINT_REFERENCE_PROVIDER_MISMATCH;
        }
        if (levels.stream().anyMatch(level ->
                !level.referenceIndexId().equals(reference.referenceIndexId()))) {
            return basis
                    ? UnavailableReason.BASIS_REFERENCE_INDEX_MISMATCH
                    : UnavailableReason.ENDPOINT_REFERENCE_INDEX_MISMATCH;
        }
        if (levels.stream().anyMatch(level ->
                !level.referenceIndexDefinitionRevision().equals(
                        reference.referenceIndexDefinitionRevision()))) {
            return basis
                    ? UnavailableReason
                            .BASIS_REFERENCE_INDEX_DEFINITION_REVISION_MISMATCH
                    : UnavailableReason
                            .ENDPOINT_REFERENCE_INDEX_DEFINITION_REVISION_MISMATCH;
        }
        if (levels.stream().anyMatch(level ->
                level.referenceKind() != reference.referenceKind())) {
            return basis
                    ? UnavailableReason.BASIS_REFERENCE_KIND_MISMATCH
                    : UnavailableReason.ENDPOINT_REFERENCE_KIND_MISMATCH;
        }
        if (levels.stream().anyMatch(level ->
                !level.currency().equals(reference.currency()))) {
            return basis
                    ? UnavailableReason.BASIS_CURRENCY_MISMATCH
                    : UnavailableReason.ENDPOINT_CURRENCY_MISMATCH;
        }
        if (levels.stream().anyMatch(level ->
                !level.calculationVenueId().equals(
                        reference.calculationVenueId()))) {
            return basis
                    ? UnavailableReason.BASIS_CALCULATION_VENUE_MISMATCH
                    : UnavailableReason.ENDPOINT_CALCULATION_VENUE_MISMATCH;
        }
        if (levels.stream().anyMatch(level ->
                !level.calendarId().equals(reference.calendarId())
                || !level.calendarRevision().equals(reference.calendarRevision())
                || !level.calendarSourceId().equals(reference.calendarSourceId())
                || !level.calendarSourceRevision().equals(
                        reference.calendarSourceRevision()))) {
            return basis
                    ? UnavailableReason.BASIS_CALENDAR_MISMATCH
                    : UnavailableReason.ENDPOINT_CALENDAR_MISMATCH;
        }
        if (levels.stream().anyMatch(level ->
                !level.levelSourceId().equals(reference.levelSourceId())
                || !level.levelSourceRevision().equals(
                        reference.levelSourceRevision()))) {
            return basis
                    ? UnavailableReason.BASIS_LEVEL_SOURCE_MISMATCH
                    : UnavailableReason.ENDPOINT_LEVEL_SOURCE_MISMATCH;
        }
        if (levels.stream().anyMatch(level ->
                !level.observedAt().equals(observedAt))) {
            return basis
                    ? UnavailableReason.BASIS_OBSERVED_AT_MISMATCH
                    : UnavailableReason.ENDPOINT_OBSERVED_AT_MISMATCH;
        }
        if (levels.stream().anyMatch(level ->
                level.levelField()
                        != ReferenceLevelField.PROVIDER_PUBLISHED_INDEX_LEVEL)) {
            return basis
                    ? UnavailableReason.BASIS_LEVEL_FIELD_MISMATCH
                    : UnavailableReason.ENDPOINT_LEVEL_FIELD_MISMATCH;
        }
        return null;
    }

    private static UnavailableReason continuityMismatch(
            BenchmarkReferenceIndexEvidence reference,
            BenchmarkReferenceLevelObservation basisLevel,
            BenchmarkReferenceLevelObservation endpointLevel,
            List<BenchmarkIndexDivisorContinuityEvidence> continuities,
            Instant basisInstant,
            Instant endpointInstant) {
        if (continuities.stream().anyMatch(continuity ->
                !continuity.referenceIndexEvidenceId().equals(
                        reference.referenceIndexEvidenceId())
                || !continuity.referenceIndexProviderEventId().equals(
                        reference.providerEventId()))) {
            return UnavailableReason
                    .DIVISOR_REFERENCE_INDEX_EVIDENCE_LINK_MISMATCH;
        }
        if (continuities.stream().anyMatch(continuity ->
                !continuity.benchmarkAssetId().equals(reference.benchmarkAssetId())
                || continuity.benchmarkAssetType()
                        != reference.benchmarkAssetType())) {
            return UnavailableReason.DIVISOR_BENCHMARK_ASSET_MISMATCH;
        }
        if (continuities.stream().anyMatch(continuity ->
                !continuity.referenceProviderId().equals(
                        reference.referenceProviderId()))) {
            return UnavailableReason.DIVISOR_REFERENCE_PROVIDER_MISMATCH;
        }
        if (continuities.stream().anyMatch(continuity ->
                !continuity.referenceIndexId().equals(reference.referenceIndexId()))) {
            return UnavailableReason.DIVISOR_REFERENCE_INDEX_MISMATCH;
        }
        if (continuities.stream().anyMatch(continuity ->
                !continuity.referenceIndexDefinitionRevision().equals(
                        reference.referenceIndexDefinitionRevision()))) {
            return UnavailableReason
                    .DIVISOR_REFERENCE_INDEX_DEFINITION_REVISION_MISMATCH;
        }
        if (continuities.stream().anyMatch(continuity ->
                continuity.referenceKind() != reference.referenceKind())) {
            return UnavailableReason.DIVISOR_REFERENCE_KIND_MISMATCH;
        }
        if (continuities.stream().anyMatch(continuity ->
                !continuity.currency().equals(reference.currency()))) {
            return UnavailableReason.DIVISOR_CURRENCY_MISMATCH;
        }
        if (continuities.stream().anyMatch(continuity ->
                !continuity.calculationVenueId().equals(
                        reference.calculationVenueId()))) {
            return UnavailableReason.DIVISOR_CALCULATION_VENUE_MISMATCH;
        }
        if (continuities.stream().anyMatch(continuity ->
                !continuity.calendarId().equals(reference.calendarId())
                || !continuity.calendarRevision().equals(
                        reference.calendarRevision())
                || !continuity.calendarSourceId().equals(
                        reference.calendarSourceId())
                || !continuity.calendarSourceRevision().equals(
                        reference.calendarSourceRevision()))) {
            return UnavailableReason.DIVISOR_CALENDAR_MISMATCH;
        }
        if (continuities.stream().anyMatch(continuity ->
                !continuity.continuitySourceId().equals(
                        reference.continuitySourceId())
                || !continuity.continuitySourceRevision().equals(
                        reference.continuitySourceRevision()))) {
            return UnavailableReason.DIVISOR_CONTINUITY_SOURCE_MISMATCH;
        }
        if (continuities.stream().anyMatch(continuity ->
                !continuity.basisObservationId().equals(basisLevel.observationId())
                || !continuity.basisProviderEventId().equals(
                        basisLevel.providerEventId()))) {
            return UnavailableReason.DIVISOR_BASIS_OBSERVATION_LINK_MISMATCH;
        }
        if (continuities.stream().anyMatch(continuity ->
                !continuity.endpointObservationId().equals(
                        endpointLevel.observationId())
                || !continuity.endpointProviderEventId().equals(
                        endpointLevel.providerEventId()))) {
            return UnavailableReason.DIVISOR_ENDPOINT_OBSERVATION_LINK_MISMATCH;
        }
        if (continuities.stream().anyMatch(continuity ->
                !continuity.coverageStartsAt().equals(basisInstant)
                || !continuity.coverageEndsAt().equals(endpointInstant))) {
            return UnavailableReason.DIVISOR_COVERAGE_MISMATCH;
        }
        if (continuities.stream().anyMatch(continuity ->
                continuity.divisorContinuity()
                        != DivisorContinuity
                                .PROVIDER_PUBLISHED_INDEX_DIVISOR_CONTINUITY_ATTESTED)) {
            return UnavailableReason.DIVISOR_CONTINUITY_UNAVAILABLE;
        }
        return null;
    }

    private static <T> List<T> known(List<T> candidates, Instant evaluationAsOf) {
        return candidates.stream()
                .filter(candidate -> known(candidate, evaluationAsOf))
                .toList();
    }

    private static boolean known(Object candidate, Instant evaluationAsOf) {
        Instant availableAt;
        Instant capturedAt;
        if (candidate instanceof BenchmarkReferenceIndexEvidence reference) {
            availableAt = reference.availableAt();
            capturedAt = reference.capturedAt();
        } else if (candidate instanceof BenchmarkReferenceLevelObservation level) {
            availableAt = level.availableAt();
            capturedAt = level.capturedAt();
        } else if (candidate
                instanceof BenchmarkIndexDivisorContinuityEvidence continuity) {
            availableAt = continuity.availableAt();
            capturedAt = continuity.capturedAt();
        } else {
            throw new IllegalArgumentException("unsupported reference candidate type");
        }
        return !availableAt.isAfter(evaluationAsOf)
                && !capturedAt.isAfter(evaluationAsOf);
    }

    private static BenchmarkReferenceLevelPairResolution unavailable(
            ResolutionContext context, UnavailableReason reason) {
        return new BenchmarkReferenceLevelPairResolution.EvidenceUnavailable(
                context, reason);
    }
}
