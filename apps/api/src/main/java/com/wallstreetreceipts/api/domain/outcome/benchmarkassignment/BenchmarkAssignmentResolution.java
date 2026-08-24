package com.wallstreetreceipts.api.domain.outcome.benchmarkassignment;

import java.time.Instant;
import java.util.Currency;
import java.util.List;
import java.util.Objects;

import com.wallstreetreceipts.api.domain.PersistentInstant;
import com.wallstreetreceipts.api.domain.master.AssetType;
import com.wallstreetreceipts.api.domain.outcome.benchmarkassignment.BenchmarkAssignmentEvidence.BenchmarkReferenceKind;
import com.wallstreetreceipts.api.domain.outcome.horizon.OutcomeBasis;

/** Explicit assignment, intentional non-applicability, or evidence unavailability. */
public sealed interface BenchmarkAssignmentResolution
        permits BenchmarkAssignmentResolution.Resolved,
        BenchmarkAssignmentResolution.NotApplicable,
        BenchmarkAssignmentResolution.Unavailable {

    enum NotApplicableReason {
        NON_EQUITY,
        NON_US_PRIMARY_VENUE,
        NON_USD_CURRENCY,
        NON_US_PRIMARY_VENUE_AND_NON_USD_CURRENCY
    }

    enum UnavailableReason {
        CLASSIFICATION_MISSING_AS_OF,
        CLASSIFICATION_BASIS_MISMATCH,
        CLASSIFICATION_ASSET_MISMATCH,
        CLASSIFICATION_EFFECTIVE_INTERVAL_MISMATCH,
        CLASSIFICATION_AMBIGUOUS,
        ASSIGNMENT_MISSING_AS_OF,
        ASSIGNMENT_BASIS_MISMATCH,
        ASSIGNMENT_ASSET_MISMATCH,
        ASSIGNMENT_ASSET_TYPE_MISMATCH,
        ASSIGNMENT_PRIMARY_VENUE_MISMATCH,
        ASSIGNMENT_PRIMARY_VENUE_COUNTRY_MISMATCH,
        ASSIGNMENT_CURRENCY_MISMATCH,
        ASSIGNMENT_EFFECTIVE_INTERVAL_MISMATCH,
        OUT_OF_SCOPE_ASSIGNMENT_CONFLICT,
        BENCHMARK_ASSET_ID_MISMATCH,
        BENCHMARK_ASSET_TYPE_MISMATCH,
        BENCHMARK_CURRENCY_MISMATCH,
        BENCHMARK_REFERENCE_KIND_MISMATCH,
        ASSIGNMENT_AMBIGUOUS
    }

    /** Stable request identity echoed by every resolution. */
    record ResolutionContext(
            BenchmarkAssignmentPolicyVersion policyVersion,
            String policyDefinitionHash,
            OutcomeBasis basis,
            String assetId,
            Instant evaluationAsOf) {

        public ResolutionContext {
            Objects.requireNonNull(policyVersion,
                    "policyVersion must not be null");
            Objects.requireNonNull(policyDefinitionHash,
                    "policyDefinitionHash must not be null");
            Objects.requireNonNull(basis, "basis must not be null");
            BenchmarkAssetClassificationEvidence.requireCanonicalText(
                    assetId, "assetId");
            PersistentInstant.requireMicrosecondPrecision(
                    evaluationAsOf, "evaluationAsOf");
            if (policyVersion
                    != BenchmarkAssignmentPolicyVersion
                            .POINT_IN_TIME_EXPLICIT_US_EQUITY_ASSET_SPX_ASSIGNMENT_V1) {
                throw new IllegalArgumentException(
                        "policyVersion must be the benchmark-assignment V1 policy");
            }
            if (!policyVersion.definitionHash().equals(policyDefinitionHash)) {
                throw new IllegalArgumentException(
                        "policyDefinitionHash must match policyVersion");
            }
            new BenchmarkAssignmentRequest(
                    policyVersion, basis, assetId, evaluationAsOf,
                    List.of(), List.of());
        }
    }

    /** One exact visible classification and assignment satisfy V1. */
    record Resolved(
            ResolutionContext context,
            BenchmarkAssetClassificationEvidence classificationEvidence,
            BenchmarkAssignmentEvidence assignmentEvidence)
            implements BenchmarkAssignmentResolution {

        public Resolved {
            Objects.requireNonNull(context, "context must not be null");
            Objects.requireNonNull(classificationEvidence,
                    "classificationEvidence must not be null");
            Objects.requireNonNull(assignmentEvidence,
                    "assignmentEvidence must not be null");
            requireLocallyValidClassification(context, classificationEvidence);
            if (notApplicableReason(classificationEvidence) != null) {
                throw new IllegalArgumentException(
                        "resolved classification must be in benchmark V1 scope");
            }
            requireLocallyValidAssignment(
                    context, classificationEvidence, assignmentEvidence);
            if (!requiredBenchmarkAssetId().equals(
                    assignmentEvidence.benchmarkAssetId())
                    || assignmentEvidence.benchmarkAssetType() != AssetType.INDEX
                    || !requiredCurrency().equals(
                            assignmentEvidence.benchmarkCurrency())
                    || assignmentEvidence.referenceKind()
                            != BenchmarkReferenceKind
                                    .PROVIDER_PUBLISHED_PRICE_INDEX) {
                throw new IllegalArgumentException(
                        "assignment must use the exact benchmark V1 reference");
            }
        }
    }

    /** Visible coherent classification proves that benchmark V1 is intentional N/A. */
    record NotApplicable(
            ResolutionContext context,
            BenchmarkAssetClassificationEvidence classificationEvidence,
            NotApplicableReason reason) implements BenchmarkAssignmentResolution {

        public NotApplicable {
            Objects.requireNonNull(context, "context must not be null");
            Objects.requireNonNull(classificationEvidence,
                    "classificationEvidence must not be null");
            Objects.requireNonNull(reason, "reason must not be null");
            requireLocallyValidClassification(context, classificationEvidence);
            if (reason != notApplicableReason(classificationEvidence)) {
                throw new IllegalArgumentException(
                        "reason must match the exact out-of-scope truth table");
            }
        }
    }

    /** Only the selector attests request-wide PIT filtering and reason precedence. */
    record Unavailable(
            ResolutionContext context,
            UnavailableReason reason) implements BenchmarkAssignmentResolution {

        public Unavailable {
            Objects.requireNonNull(context, "context must not be null");
            Objects.requireNonNull(reason, "reason must not be null");
        }
    }

    private static void requireLocallyValidClassification(
            ResolutionContext context,
            BenchmarkAssetClassificationEvidence classification) {
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

    private static void requireLocallyValidAssignment(
            ResolutionContext context,
            BenchmarkAssetClassificationEvidence classification,
            BenchmarkAssignmentEvidence assignment) {
        requireVisible(assignment.availableAt(), assignment.capturedAt(), context);
        if (!assignment.basis().equals(context.basis())
                || !assignment.assetId().equals(context.assetId())
                || assignment.assetType() != classification.assetType()
                || !assignment.primaryVenueId().equals(
                        classification.primaryVenueId())
                || !assignment.primaryVenueCountryCode().equals(
                        classification.primaryVenueCountryCode())
                || !assignment.currency().equals(classification.currency())
                || !assignment.effectiveInterval()
                        .contains(context.basis().eventTime())) {
            throw new IllegalArgumentException(
                    "assignment must cohere with the selected classification");
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
            BenchmarkAssetClassificationEvidence classification) {
        if (classification.assetType() != AssetType.EQUITY) {
            return NotApplicableReason.NON_EQUITY;
        }
        boolean usVenue = "US".equals(
                classification.primaryVenueCountryCode());
        boolean usdCurrency = requiredCurrency().equals(classification.currency());
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

    private static String requiredBenchmarkAssetId() {
        return "asset-spx";
    }

    private static Currency requiredCurrency() {
        return Currency.getInstance("USD");
    }
}
