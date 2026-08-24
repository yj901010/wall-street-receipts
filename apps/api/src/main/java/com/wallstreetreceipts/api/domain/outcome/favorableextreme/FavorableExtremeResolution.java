package com.wallstreetreceipts.api.domain.outcome.favorableextreme;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.List;
import java.util.Objects;

import com.wallstreetreceipts.api.domain.outcome.calculation.TargetHitSide;
import com.wallstreetreceipts.api.domain.outcome.horizon.SessionCloseHorizonResolution;
import com.wallstreetreceipts.api.domain.outcome.observation.CorporateActionContinuity;
import com.wallstreetreceipts.api.domain.outcome.observation.EndpointPriceAdjustmentBasis;
import com.wallstreetreceipts.api.domain.outcome.routing.CalculatorSideRouting.DirectionalRoute;
import com.wallstreetreceipts.api.domain.outcome.targeteligibility.TargetEligibilityResolution;

/**
 * A selected causal-window high/low or explicit point-in-time unavailability.
 *
 * <p>Public result constructors validate consistency of only the supplied
 * evidence. Only {@link FavorableExtremeSelector#select(FavorableExtremeRequest)}
 * attests PIT filtering, all-request candidate membership, poison precedence,
 * and request-wide cardinality.</p>
 */
public sealed interface FavorableExtremeResolution
        permits FavorableExtremeResolution.Resolved,
        FavorableExtremeResolution.Unavailable {

    enum UnavailableReason {
        TARGET_ADJUSTMENT_BASIS_UNSUPPORTED,
        BINDING_NOT_KNOWN_AS_OF,
        BINDING_ASSET_MISMATCH,
        BINDING_PRIMARY_VENUE_MISMATCH,
        BINDING_CURRENCY_MISMATCH,
        OBSERVATION_MISSING_AS_OF,
        BASIS_MISMATCH,
        HORIZON_MISMATCH,
        ASSET_MISMATCH,
        PRIMARY_VENUE_MISMATCH,
        CURRENCY_MISMATCH,
        SOURCE_MISMATCH,
        CATALOG_MISMATCH,
        SESSION_WINDOW_MISMATCH,
        LOWER_BOUND_MISMATCH,
        UPPER_BOUND_MISMATCH,
        BOUNDARY_CONVENTION_MISMATCH,
        PRICE_FIELD_MISMATCH,
        WINDOW_COMPLETENESS_UNAVAILABLE,
        ADJUSTMENT_BASIS_MISMATCH,
        CORPORATE_ACTION_CONTINUITY_UNAVAILABLE,
        OBSERVATION_AMBIGUOUS
    }

    enum FavorableExtremeField {
        HIGH,
        LOW
    }

    record FavorableExtreme(
            FavorableExtremeField field,
            BigDecimal value) {

        private static final int STORAGE_SCALE = 12;
        private static final int STORAGE_PRECISION = 38;

        public FavorableExtreme {
            Objects.requireNonNull(field, "field must not be null");
            requirePositiveNumeric(value, "value");
        }

        private static void requirePositiveNumeric(
                BigDecimal value, String fieldName) {
            Objects.requireNonNull(value, fieldName + " must not be null");
            if (value.signum() <= 0) {
                throw new IllegalArgumentException(
                        fieldName + " must be positive");
            }
            try {
                BigDecimal storageValue = value.setScale(
                        STORAGE_SCALE, RoundingMode.UNNECESSARY);
                if (storageValue.precision() > STORAGE_PRECISION) {
                    throw new IllegalArgumentException(
                            fieldName + " exceeds NUMERIC(38,12) precision");
                }
            } catch (ArithmeticException exception) {
                throw new IllegalArgumentException(
                        fieldName + " exceeds NUMERIC(38,12) scale", exception);
            }
        }
    }

    record ResolutionContext(
            FavorableExtremePolicyVersion policyVersion,
            String policyDefinitionHash,
            TargetEligibilityResolution.ReadyForWindowEvidence readyEligibility) {

        public ResolutionContext {
            Objects.requireNonNull(policyVersion,
                    "policyVersion must not be null");
            Objects.requireNonNull(policyDefinitionHash,
                    "policyDefinitionHash must not be null");
            Objects.requireNonNull(readyEligibility,
                    "readyEligibility must not be null");
            if (policyVersion
                    != FavorableExtremePolicyVersion
                            .POINT_IN_TIME_ATTESTED_CAUSAL_WINDOW_HIGH_LOW_V1) {
                throw new IllegalArgumentException(
                        "policyVersion must be the favorable-extreme V1 policy");
            }
            if (!policyVersion.definitionHash().equals(policyDefinitionHash)) {
                throw new IllegalArgumentException(
                        "policyDefinitionHash must match policyVersion");
            }
            new FavorableExtremeRequest(
                    policyVersion, readyEligibility, null, List.of());
        }
    }

    /** Only PIT-visible binding and candidates retained after the deciding gate. */
    record SelectionEvidence(
            WindowPriceBinding binding,
            List<FullWindowHighLowObservation> knownCandidates) {

        public SelectionEvidence {
            Objects.requireNonNull(knownCandidates,
                    "knownCandidates must not be null");
            for (FullWindowHighLowObservation candidate : knownCandidates) {
                Objects.requireNonNull(candidate,
                        "knownCandidates must not contain null");
            }
            knownCandidates = List.copyOf(knownCandidates);
        }
    }

    /** Exactly one complete attested pair supplied the side-selected value. */
    record Resolved(
            ResolutionContext context,
            SelectionEvidence evidence,
            FavorableExtreme favorableExtreme)
            implements FavorableExtremeResolution {

        public Resolved {
            Objects.requireNonNull(context, "context must not be null");
            Objects.requireNonNull(evidence, "evidence must not be null");
            Objects.requireNonNull(favorableExtreme,
                    "favorableExtreme must not be null");
            requireVisibleOutputEvidence(context, evidence);
            if (firstUnavailableReason(context, evidence) != null) {
                throw new IllegalArgumentException(
                        "resolved evidence must pass every selection gate");
            }
            if (evidence.knownCandidates().size() != 1) {
                throw new IllegalArgumentException(
                        "resolved evidence requires exactly one known candidate");
            }
            var observation = evidence.knownCandidates().getFirst();
            var expectedField = expectedField(context);
            var expectedValue = expectedField == FavorableExtremeField.HIGH
                    ? observation.windowHigh() : observation.windowLow();
            if (favorableExtreme.field() != expectedField
                    || !favorableExtreme.value().equals(expectedValue)) {
                throw new IllegalArgumentException(
                        "favorableExtreme must preserve the side-selected pair value");
            }
        }
    }

    /** Missing, mismatched, incomplete, discontinuous, or ambiguous evidence. */
    record Unavailable(
            ResolutionContext context,
            SelectionEvidence evidence,
            UnavailableReason reason) implements FavorableExtremeResolution {

        public Unavailable {
            Objects.requireNonNull(context, "context must not be null");
            Objects.requireNonNull(evidence, "evidence must not be null");
            Objects.requireNonNull(reason, "reason must not be null");
            requireVisibleOutputEvidence(context, evidence);
            UnavailableReason expected = firstUnavailableReason(context, evidence);
            if (reason != expected) {
                throw new IllegalArgumentException(
                        "reason must follow the exact selection precedence");
            }
            requireBranchClearing(evidence, reason);
        }
    }

    private static UnavailableReason firstUnavailableReason(
            ResolutionContext context,
            SelectionEvidence evidence) {
        var target = context.readyEligibility().evidence().targetEvidence();
        if (target.adjustmentBasis() != requiredAdjustmentBasis()) {
            return UnavailableReason.TARGET_ADJUSTMENT_BASIS_UNSUPPORTED;
        }
        WindowPriceBinding binding = evidence.binding();
        if (binding == null) {
            return UnavailableReason.BINDING_NOT_KNOWN_AS_OF;
        }
        if (!binding.assetId().equals(target.assetId())) {
            return UnavailableReason.BINDING_ASSET_MISMATCH;
        }
        if (!binding.primaryVenueId().equals(target.primaryVenueId())) {
            return UnavailableReason.BINDING_PRIMARY_VENUE_MISMATCH;
        }
        if (!binding.currency().equals(target.currency())) {
            return UnavailableReason.BINDING_CURRENCY_MISMATCH;
        }

        List<FullWindowHighLowObservation> candidates = evidence.knownCandidates();
        if (candidates.isEmpty()) {
            return UnavailableReason.OBSERVATION_MISSING_AS_OF;
        }
        var window = resolvedWindow(context);
        var horizonContext = window.context();
        var catalog = context.readyEligibility().evidence().catalogEvidence();
        List<String> expectedSessionIds = window.sessions().stream()
                .map(session -> session.sessionId())
                .toList();

        if (candidates.stream().anyMatch(candidate ->
                !candidate.basis().equals(horizonContext.basis()))) {
            return UnavailableReason.BASIS_MISMATCH;
        }
        if (candidates.stream().anyMatch(candidate ->
                candidate.horizon() != horizonContext.horizon())) {
            return UnavailableReason.HORIZON_MISMATCH;
        }
        if (candidates.stream().anyMatch(candidate ->
                !candidate.assetId().equals(binding.assetId()))) {
            return UnavailableReason.ASSET_MISMATCH;
        }
        if (candidates.stream().anyMatch(candidate ->
                !candidate.venueId().equals(binding.primaryVenueId()))) {
            return UnavailableReason.PRIMARY_VENUE_MISMATCH;
        }
        if (candidates.stream().anyMatch(candidate ->
                !candidate.currency().equals(binding.currency()))) {
            return UnavailableReason.CURRENCY_MISMATCH;
        }
        if (candidates.stream().anyMatch(candidate ->
                !candidate.priceSourceId().equals(binding.priceSourceId())
                || !candidate.priceSourceRevision()
                        .equals(binding.priceSourceRevision()))) {
            return UnavailableReason.SOURCE_MISMATCH;
        }
        if (candidates.stream().anyMatch(candidate ->
                !candidate.calendarId().equals(catalog.calendarId())
                || !candidate.catalogRevision().equals(catalog.catalogRevision()))) {
            return UnavailableReason.CATALOG_MISMATCH;
        }
        if (candidates.stream().anyMatch(candidate ->
                !candidate.orderedSessionIds().equals(expectedSessionIds))) {
            return UnavailableReason.SESSION_WINDOW_MISMATCH;
        }
        if (candidates.stream().anyMatch(candidate ->
                !candidate.lowerBound().equals(horizonContext.basis().eventTime()))) {
            return UnavailableReason.LOWER_BOUND_MISMATCH;
        }
        if (candidates.stream().anyMatch(candidate ->
                !candidate.upperBound().equals(window.endpointSession().closesAt()))) {
            return UnavailableReason.UPPER_BOUND_MISMATCH;
        }
        if (candidates.stream().anyMatch(candidate ->
                candidate.lowerBoundType()
                        != FullWindowHighLowObservation.BoundaryType.EXCLUSIVE
                || candidate.upperBoundType()
                        != FullWindowHighLowObservation.BoundaryType.INCLUSIVE)) {
            return UnavailableReason.BOUNDARY_CONVENTION_MISMATCH;
        }
        if (candidates.stream().anyMatch(candidate ->
                candidate.priceField()
                        != FullWindowHighLowObservation.WindowPriceField
                                .PRIMARY_VENUE_REGULAR_SESSION_CAUSAL_WINDOW_HIGH_LOW_PAIR)) {
            return UnavailableReason.PRICE_FIELD_MISMATCH;
        }
        if (candidates.stream().anyMatch(candidate ->
                candidate.coverageCompleteness()
                        != FullWindowHighLowObservation.WindowCoverageCompleteness
                                .EXACT_CAUSAL_WINDOW_SESSION_UNION)) {
            return UnavailableReason.WINDOW_COMPLETENESS_UNAVAILABLE;
        }
        if (candidates.stream().anyMatch(candidate ->
                candidate.adjustmentBasis() != requiredAdjustmentBasis())) {
            return UnavailableReason.ADJUSTMENT_BASIS_MISMATCH;
        }
        if (candidates.stream().anyMatch(candidate ->
                candidate.corporateActionContinuity()
                        != CorporateActionContinuity.SPLIT_REVERSE_SPLIT_CONTINUOUS)) {
            return UnavailableReason.CORPORATE_ACTION_CONTINUITY_UNAVAILABLE;
        }
        if (candidates.size() > 1) {
            return UnavailableReason.OBSERVATION_AMBIGUOUS;
        }
        return null;
    }

    private static void requireVisibleOutputEvidence(
            ResolutionContext context,
            SelectionEvidence evidence) {
        Instant evaluationAsOf = context.readyEligibility()
                .context().evaluationAsOf();
        if (evidence.binding() != null
                && (!known(evidence.binding().availableAt(),
                        evidence.binding().capturedAt(), evaluationAsOf))) {
            throw new IllegalArgumentException(
                    "output binding must be known by evaluationAsOf");
        }
        if (evidence.knownCandidates().stream().anyMatch(candidate ->
                !known(candidate.availableAt(), candidate.capturedAt(),
                        evaluationAsOf))) {
            throw new IllegalArgumentException(
                    "output candidates must be known by evaluationAsOf");
        }
    }

    private static void requireBranchClearing(
            SelectionEvidence evidence, UnavailableReason reason) {
        switch (reason) {
            case TARGET_ADJUSTMENT_BASIS_UNSUPPORTED,
                    BINDING_NOT_KNOWN_AS_OF -> {
                if (evidence.binding() != null
                        || !evidence.knownCandidates().isEmpty()) {
                    throw new IllegalArgumentException(
                            "evidence after the deciding gate must be cleared");
                }
            }
            case BINDING_ASSET_MISMATCH,
                    BINDING_PRIMARY_VENUE_MISMATCH,
                    BINDING_CURRENCY_MISMATCH -> {
                Objects.requireNonNull(evidence.binding(),
                        "known binding must be preserved");
                if (!evidence.knownCandidates().isEmpty()) {
                    throw new IllegalArgumentException(
                            "candidate evidence must be cleared after a binding gate");
                }
            }
            case OBSERVATION_MISSING_AS_OF -> {
                Objects.requireNonNull(evidence.binding(),
                        "matching binding must be preserved");
                if (!evidence.knownCandidates().isEmpty()) {
                    throw new IllegalArgumentException(
                            "missing observation requires no known candidates");
                }
            }
            default -> {
                Objects.requireNonNull(evidence.binding(),
                        "matching binding must be preserved");
                if (evidence.knownCandidates().isEmpty()) {
                    throw new IllegalArgumentException(
                            "candidate reason requires known candidate evidence");
                }
            }
        }
    }

    private static FavorableExtremeField expectedField(ResolutionContext context) {
        var route = (DirectionalRoute) context.readyEligibility()
                .evidence().sideRouting();
        return route.targetHitSide() == TargetHitSide.BULLISH
                ? FavorableExtremeField.HIGH : FavorableExtremeField.LOW;
    }

    private static SessionCloseHorizonResolution.ResolvedSessionWindow resolvedWindow(
            ResolutionContext context) {
        var horizon = (SessionCloseHorizonResolution.Resolved)
                context.readyEligibility().context().horizonResolution();
        return horizon.window();
    }

    private static EndpointPriceAdjustmentBasis requiredAdjustmentBasis() {
        return EndpointPriceAdjustmentBasis
                .SPLIT_AND_REVERSE_SPLIT_ADJUSTED_TO_ENDPOINT_SHARE_BASIS_DIVIDEND_UNADJUSTED;
    }

    private static boolean known(
            Instant availableAt, Instant capturedAt, Instant evaluationAsOf) {
        return !availableAt.isAfter(evaluationAsOf)
                && !capturedAt.isAfter(evaluationAsOf);
    }
}
