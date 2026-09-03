package com.wallstreetreceipts.api.domain.outcome.favorableextreme;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

import com.wallstreetreceipts.api.domain.outcome.calculation.TargetHitSide;
import com.wallstreetreceipts.api.domain.outcome.favorableextreme.FavorableExtremeResolution.FavorableExtreme;
import com.wallstreetreceipts.api.domain.outcome.favorableextreme.FavorableExtremeResolution.FavorableExtremeField;
import com.wallstreetreceipts.api.domain.outcome.favorableextreme.FavorableExtremeResolution.ResolutionContext;
import com.wallstreetreceipts.api.domain.outcome.favorableextreme.FavorableExtremeResolution.SelectionEvidence;
import com.wallstreetreceipts.api.domain.outcome.favorableextreme.FavorableExtremeResolution.UnavailableReason;
import com.wallstreetreceipts.api.domain.outcome.horizon.SessionCloseHorizonResolution;
import com.wallstreetreceipts.api.domain.outcome.observation.CorporateActionContinuity;
import com.wallstreetreceipts.api.domain.outcome.observation.EndpointPriceAdjustmentBasis;
import com.wallstreetreceipts.api.domain.outcome.routing.CalculatorSideRouting.DirectionalRoute;

/** Selects one side-favorable value from exact-window attested high/low evidence. */
public final class FavorableExtremeSelector {

    private FavorableExtremeSelector() {
    }

    public static FavorableExtremeResolution select(
            FavorableExtremeRequest request) {
        Objects.requireNonNull(request, "request must not be null");
        ResolutionContext context = new ResolutionContext(
                request.policyVersion(),
                request.policyVersion().definitionHash(),
                request.readyEligibility());
        var target = request.readyEligibility().evidence().targetEvidence();
        if (target.adjustmentBasis() != requiredAdjustmentBasis()) {
            return unavailable(context, null, List.of(),
                    UnavailableReason.TARGET_ADJUSTMENT_BASIS_UNSUPPORTED);
        }

        WindowPriceBinding binding = visibleBinding(request);
        if (binding == null) {
            return unavailable(context, null, List.of(),
                    UnavailableReason.BINDING_NOT_KNOWN_AS_OF);
        }
        if (!binding.assetId().equals(target.assetId())) {
            return unavailable(context, binding, List.of(),
                    UnavailableReason.BINDING_ASSET_MISMATCH);
        }
        if (!binding.primaryVenueId().equals(target.primaryVenueId())) {
            return unavailable(context, binding, List.of(),
                    UnavailableReason.BINDING_PRIMARY_VENUE_MISMATCH);
        }
        if (!binding.currency().equals(target.currency())) {
            return unavailable(context, binding, List.of(),
                    UnavailableReason.BINDING_CURRENCY_MISMATCH);
        }

        List<FullWindowHighLowObservation> knownCandidates = visibleCandidates(request);
        if (knownCandidates.isEmpty()) {
            return unavailable(context, binding, knownCandidates,
                    UnavailableReason.OBSERVATION_MISSING_AS_OF);
        }

        var window = resolvedWindow(request);
        var horizonContext = window.context();
        var catalog = request.readyEligibility().evidence().catalogEvidence();
        List<String> expectedSessionIds = window.sessions().stream()
                .map(session -> session.sessionId())
                .toList();
        if (knownCandidates.stream().anyMatch(candidate ->
                !candidate.basis().equals(horizonContext.basis()))) {
            return unavailable(context, binding, knownCandidates,
                    UnavailableReason.BASIS_MISMATCH);
        }
        if (knownCandidates.stream().anyMatch(candidate ->
                candidate.horizon() != horizonContext.horizon())) {
            return unavailable(context, binding, knownCandidates,
                    UnavailableReason.HORIZON_MISMATCH);
        }
        if (knownCandidates.stream().anyMatch(candidate ->
                !candidate.assetId().equals(binding.assetId()))) {
            return unavailable(context, binding, knownCandidates,
                    UnavailableReason.ASSET_MISMATCH);
        }
        if (knownCandidates.stream().anyMatch(candidate ->
                !candidate.venueId().equals(binding.primaryVenueId()))) {
            return unavailable(context, binding, knownCandidates,
                    UnavailableReason.PRIMARY_VENUE_MISMATCH);
        }
        if (knownCandidates.stream().anyMatch(candidate ->
                !candidate.currency().equals(binding.currency()))) {
            return unavailable(context, binding, knownCandidates,
                    UnavailableReason.CURRENCY_MISMATCH);
        }
        if (knownCandidates.stream().anyMatch(candidate ->
                !candidate.priceSourceId().equals(binding.priceSourceId())
                || !candidate.priceSourceRevision()
                        .equals(binding.priceSourceRevision()))) {
            return unavailable(context, binding, knownCandidates,
                    UnavailableReason.SOURCE_MISMATCH);
        }
        if (knownCandidates.stream().anyMatch(candidate ->
                !candidate.calendarId().equals(catalog.calendarId())
                || !candidate.catalogRevision().equals(catalog.catalogRevision()))) {
            return unavailable(context, binding, knownCandidates,
                    UnavailableReason.CATALOG_MISMATCH);
        }
        if (knownCandidates.stream().anyMatch(candidate ->
                !candidate.orderedSessionIds().equals(expectedSessionIds))) {
            return unavailable(context, binding, knownCandidates,
                    UnavailableReason.SESSION_WINDOW_MISMATCH);
        }
        if (knownCandidates.stream().anyMatch(candidate ->
                !candidate.lowerBound().equals(horizonContext.basis().eventTime()))) {
            return unavailable(context, binding, knownCandidates,
                    UnavailableReason.LOWER_BOUND_MISMATCH);
        }
        if (knownCandidates.stream().anyMatch(candidate ->
                !candidate.upperBound().equals(window.endpointSession().closesAt()))) {
            return unavailable(context, binding, knownCandidates,
                    UnavailableReason.UPPER_BOUND_MISMATCH);
        }
        if (knownCandidates.stream().anyMatch(candidate ->
                candidate.lowerBoundType()
                        != FullWindowHighLowObservation.BoundaryType.EXCLUSIVE
                || candidate.upperBoundType()
                        != FullWindowHighLowObservation.BoundaryType.INCLUSIVE)) {
            return unavailable(context, binding, knownCandidates,
                    UnavailableReason.BOUNDARY_CONVENTION_MISMATCH);
        }
        if (knownCandidates.stream().anyMatch(candidate ->
                candidate.priceField()
                        != FullWindowHighLowObservation.WindowPriceField
                                .PRIMARY_VENUE_REGULAR_SESSION_CAUSAL_WINDOW_HIGH_LOW_PAIR)) {
            return unavailable(context, binding, knownCandidates,
                    UnavailableReason.PRICE_FIELD_MISMATCH);
        }
        if (knownCandidates.stream().anyMatch(candidate ->
                candidate.coverageCompleteness()
                        != FullWindowHighLowObservation.WindowCoverageCompleteness
                                .EXACT_CAUSAL_WINDOW_SESSION_UNION)) {
            return unavailable(context, binding, knownCandidates,
                    UnavailableReason.WINDOW_COMPLETENESS_UNAVAILABLE);
        }
        if (knownCandidates.stream().anyMatch(candidate ->
                candidate.adjustmentBasis() != requiredAdjustmentBasis())) {
            return unavailable(context, binding, knownCandidates,
                    UnavailableReason.ADJUSTMENT_BASIS_MISMATCH);
        }
        if (knownCandidates.stream().anyMatch(candidate ->
                candidate.corporateActionContinuity()
                        != CorporateActionContinuity.SPLIT_REVERSE_SPLIT_CONTINUOUS)) {
            return unavailable(context, binding, knownCandidates,
                    UnavailableReason.CORPORATE_ACTION_CONTINUITY_UNAVAILABLE);
        }
        if (knownCandidates.size() > 1) {
            return unavailable(context, binding, knownCandidates,
                    UnavailableReason.OBSERVATION_AMBIGUOUS);
        }

        FullWindowHighLowObservation observation = knownCandidates.getFirst();
        DirectionalRoute route = (DirectionalRoute)
                request.readyEligibility().evidence().sideRouting();
        FavorableExtreme favorableExtreme = route.targetHitSide()
                == TargetHitSide.BULLISH
                ? new FavorableExtreme(
                        FavorableExtremeField.HIGH, observation.windowHigh())
                : new FavorableExtreme(
                        FavorableExtremeField.LOW, observation.windowLow());
        return new FavorableExtremeResolution.Resolved(
                context,
                new SelectionEvidence(binding, knownCandidates),
                favorableExtreme);
    }

    private static FavorableExtremeResolution unavailable(
            ResolutionContext context,
            WindowPriceBinding binding,
            List<FullWindowHighLowObservation> candidates,
            UnavailableReason reason) {
        return new FavorableExtremeResolution.Unavailable(
                context, new SelectionEvidence(binding, candidates), reason);
    }

    private static WindowPriceBinding visibleBinding(
            FavorableExtremeRequest request) {
        WindowPriceBinding binding = request.binding();
        Instant evaluationAsOf = request.readyEligibility()
                .context().evaluationAsOf();
        return binding != null
                && known(binding.availableAt(), binding.capturedAt(), evaluationAsOf)
                        ? binding : null;
    }

    private static List<FullWindowHighLowObservation> visibleCandidates(
            FavorableExtremeRequest request) {
        Instant evaluationAsOf = request.readyEligibility()
                .context().evaluationAsOf();
        return request.candidates().stream()
                .filter(candidate -> known(
                        candidate.availableAt(), candidate.capturedAt(), evaluationAsOf))
                .toList();
    }

    private static SessionCloseHorizonResolution.ResolvedSessionWindow resolvedWindow(
            FavorableExtremeRequest request) {
        var horizon = (SessionCloseHorizonResolution.Resolved)
                request.readyEligibility().context().horizonResolution();
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
