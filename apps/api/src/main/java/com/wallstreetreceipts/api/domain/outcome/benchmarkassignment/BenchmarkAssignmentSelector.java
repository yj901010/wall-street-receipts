package com.wallstreetreceipts.api.domain.outcome.benchmarkassignment;

import java.time.Instant;
import java.util.Currency;
import java.util.List;
import java.util.Objects;

import com.wallstreetreceipts.api.domain.master.AssetType;
import com.wallstreetreceipts.api.domain.outcome.benchmarkassignment.BenchmarkAssignmentEvidence.BenchmarkReferenceKind;
import com.wallstreetreceipts.api.domain.outcome.benchmarkassignment.BenchmarkAssignmentResolution.NotApplicableReason;
import com.wallstreetreceipts.api.domain.outcome.benchmarkassignment.BenchmarkAssignmentResolution.ResolutionContext;
import com.wallstreetreceipts.api.domain.outcome.benchmarkassignment.BenchmarkAssignmentResolution.UnavailableReason;

/** Applies the closed point-in-time benchmark-assignment V1 policy. */
public final class BenchmarkAssignmentSelector {

    private static final String REQUIRED_BENCHMARK_ASSET_ID = "asset-spx";
    private static final Currency REQUIRED_CURRENCY = Currency.getInstance("USD");

    private BenchmarkAssignmentSelector() {
    }

    public static BenchmarkAssignmentResolution select(
            BenchmarkAssignmentRequest request) {
        Objects.requireNonNull(request, "request must not be null");
        ResolutionContext context = new ResolutionContext(
                request.policyVersion(),
                request.policyVersion().definitionHash(),
                request.basis(),
                request.assetId(),
                request.evaluationAsOf());

        List<BenchmarkAssetClassificationEvidence> classifications =
                visibleClassifications(request);
        List<BenchmarkAssignmentEvidence> assignments = visibleAssignments(request);

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

        BenchmarkAssetClassificationEvidence classification =
                classifications.getFirst();
        NotApplicableReason notApplicableReason =
                notApplicableReason(classification);
        if (assignments.isEmpty()) {
            if (notApplicableReason != null) {
                return new BenchmarkAssignmentResolution.NotApplicable(
                        context, classification, notApplicableReason);
            }
            return unavailable(
                    context, UnavailableReason.ASSIGNMENT_MISSING_AS_OF);
        }

        if (assignments.stream().anyMatch(candidate ->
                !candidate.basis().equals(request.basis()))) {
            return unavailable(
                    context, UnavailableReason.ASSIGNMENT_BASIS_MISMATCH);
        }
        if (assignments.stream().anyMatch(candidate ->
                !candidate.assetId().equals(request.assetId()))) {
            return unavailable(
                    context, UnavailableReason.ASSIGNMENT_ASSET_MISMATCH);
        }
        if (assignments.stream().anyMatch(candidate ->
                candidate.assetType() != classification.assetType())) {
            return unavailable(
                    context, UnavailableReason.ASSIGNMENT_ASSET_TYPE_MISMATCH);
        }
        if (assignments.stream().anyMatch(candidate ->
                !candidate.primaryVenueId().equals(
                        classification.primaryVenueId()))) {
            return unavailable(context,
                    UnavailableReason.ASSIGNMENT_PRIMARY_VENUE_MISMATCH);
        }
        if (assignments.stream().anyMatch(candidate ->
                !candidate.primaryVenueCountryCode().equals(
                        classification.primaryVenueCountryCode()))) {
            return unavailable(context, UnavailableReason
                    .ASSIGNMENT_PRIMARY_VENUE_COUNTRY_MISMATCH);
        }
        if (assignments.stream().anyMatch(candidate ->
                !candidate.currency().equals(classification.currency()))) {
            return unavailable(
                    context, UnavailableReason.ASSIGNMENT_CURRENCY_MISMATCH);
        }
        if (assignments.stream().anyMatch(candidate ->
                !candidate.effectiveInterval().contains(
                        request.basis().eventTime()))) {
            return unavailable(context,
                    UnavailableReason.ASSIGNMENT_EFFECTIVE_INTERVAL_MISMATCH);
        }
        if (notApplicableReason != null) {
            return unavailable(context,
                    UnavailableReason.OUT_OF_SCOPE_ASSIGNMENT_CONFLICT);
        }

        if (assignments.stream().anyMatch(candidate ->
                !REQUIRED_BENCHMARK_ASSET_ID.equals(
                        candidate.benchmarkAssetId()))) {
            return unavailable(
                    context, UnavailableReason.BENCHMARK_ASSET_ID_MISMATCH);
        }
        if (assignments.stream().anyMatch(candidate ->
                candidate.benchmarkAssetType() != AssetType.INDEX)) {
            return unavailable(
                    context, UnavailableReason.BENCHMARK_ASSET_TYPE_MISMATCH);
        }
        if (assignments.stream().anyMatch(candidate ->
                !REQUIRED_CURRENCY.equals(candidate.benchmarkCurrency()))) {
            return unavailable(
                    context, UnavailableReason.BENCHMARK_CURRENCY_MISMATCH);
        }
        if (assignments.stream().anyMatch(candidate ->
                candidate.referenceKind()
                        != BenchmarkReferenceKind
                                .PROVIDER_PUBLISHED_PRICE_INDEX)) {
            return unavailable(context,
                    UnavailableReason.BENCHMARK_REFERENCE_KIND_MISMATCH);
        }
        if (assignments.size() > 1) {
            return unavailable(
                    context, UnavailableReason.ASSIGNMENT_AMBIGUOUS);
        }

        return new BenchmarkAssignmentResolution.Resolved(
                context, classification, assignments.getFirst());
    }

    private static List<BenchmarkAssetClassificationEvidence>
            visibleClassifications(BenchmarkAssignmentRequest request) {
        return request.classificationCandidates().stream()
                .filter(candidate -> known(
                        candidate.availableAt(), candidate.capturedAt(),
                        request.evaluationAsOf()))
                .toList();
    }

    private static List<BenchmarkAssignmentEvidence> visibleAssignments(
            BenchmarkAssignmentRequest request) {
        return request.assignmentCandidates().stream()
                .filter(candidate -> known(
                        candidate.availableAt(), candidate.capturedAt(),
                        request.evaluationAsOf()))
                .toList();
    }

    private static NotApplicableReason notApplicableReason(
            BenchmarkAssetClassificationEvidence classification) {
        if (classification.assetType() != AssetType.EQUITY) {
            return NotApplicableReason.NON_EQUITY;
        }
        boolean usVenue = "US".equals(
                classification.primaryVenueCountryCode());
        boolean usdCurrency = REQUIRED_CURRENCY.equals(classification.currency());
        if (!usVenue && !usdCurrency) {
            return NotApplicableReason
                    .NON_US_PRIMARY_VENUE_AND_NON_USD_CURRENCY;
        }
        if (!usVenue) {
            return NotApplicableReason.NON_US_PRIMARY_VENUE;
        }
        if (!usdCurrency) {
            return NotApplicableReason.NON_USD_CURRENCY;
        }
        return null;
    }

    private static BenchmarkAssignmentResolution.Unavailable unavailable(
            ResolutionContext context,
            UnavailableReason reason) {
        return new BenchmarkAssignmentResolution.Unavailable(context, reason);
    }

    private static boolean known(
            Instant availableAt,
            Instant capturedAt,
            Instant evaluationAsOf) {
        return !availableAt.isAfter(evaluationAsOf)
                && !capturedAt.isAfter(evaluationAsOf);
    }
}
