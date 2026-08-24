package com.wallstreetreceipts.api.domain.outcome.targeterror;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.Objects;

import com.wallstreetreceipts.api.domain.outcome.observation.EndpointPriceResolution;
import com.wallstreetreceipts.api.domain.outcome.targeterror.TargetErrorResult.Available;
import com.wallstreetreceipts.api.domain.outcome.targeterror.TargetErrorResult.CalculationContext;
import com.wallstreetreceipts.api.domain.outcome.targeterror.TargetErrorResult.Unavailable;
import com.wallstreetreceipts.api.domain.outcome.targeterror.TargetErrorResult.UnavailableReason;

/** Pure target-error calculation over exact point-in-time target and endpoint evidence. */
public final class TargetErrorCalculator {

    private static final int OUTPUT_SCALE = 12;
    private static final int OUTPUT_PRECISION = 38;

    private TargetErrorCalculator() {
    }

    public static TargetErrorResult calculate(TargetErrorInput input) {
        Objects.requireNonNull(input, "input must not be null");
        CalculationContext context = new CalculationContext(
                input.policyVersion(),
                input.policyVersion().definitionHash(),
                input.endpointPriceResolution());
        var endpointContext = endpointContext(input.endpointPriceResolution());
        Instant evaluationAsOf = endpointContext.evaluationAsOf();
        TargetPriceEvidence targetEvidence = knownTarget(input.targetEvidence(), evaluationAsOf);

        if (targetEvidence == null
                && input.endpointPriceResolution()
                        instanceof EndpointPriceResolution.Unavailable endpointUnavailable) {
            return unavailable(
                    context,
                    UnavailableReason.TARGET_AND_ENDPOINT_PRICE_UNAVAILABLE,
                    endpointUnavailable.reason());
        }
        if (targetEvidence == null) {
            return unavailable(context, UnavailableReason.TARGET_MISSING_AS_OF, null);
        }
        if (input.endpointPriceResolution()
                instanceof EndpointPriceResolution.Unavailable endpointUnavailable) {
            return unavailable(
                    context,
                    UnavailableReason.ENDPOINT_PRICE_UNAVAILABLE,
                    endpointUnavailable.reason());
        }

        EndpointPriceResolution.Resolved endpointResolved =
                (EndpointPriceResolution.Resolved) input.endpointPriceResolution();
        var binding = endpointResolved.context().binding();
        var horizonBasis = endpointResolved.context().horizonResolution()
                .window().context().basis();
        if (!targetEvidence.basis().equals(horizonBasis)) {
            return unavailable(context, UnavailableReason.BASIS_MISMATCH, null);
        }
        if (!targetEvidence.assetId().equals(binding.assetId())) {
            return unavailable(context, UnavailableReason.ASSET_MISMATCH, null);
        }
        if (!targetEvidence.primaryVenueId().equals(binding.primaryVenueId())) {
            return unavailable(context, UnavailableReason.PRIMARY_VENUE_MISMATCH, null);
        }
        if (!targetEvidence.currency().equals(binding.currency())) {
            return unavailable(context, UnavailableReason.CURRENCY_MISMATCH, null);
        }
        if (targetEvidence.adjustmentBasis()
                != endpointResolved.observation().adjustmentBasis()) {
            return unavailable(context, UnavailableReason.ADJUSTMENT_BASIS_MISMATCH, null);
        }

        BigDecimal actual = endpointResolved.observation().price();
        BigDecimal numerator = targetEvidence.target().subtract(actual).abs();
        BigDecimal targetError;
        try {
            targetError = numerator.divide(actual, OUTPUT_SCALE, RoundingMode.HALF_EVEN);
        } catch (ArithmeticException exception) {
            return unavailable(context, UnavailableReason.OUTPUT_NOT_REPRESENTABLE, null);
        }
        if (targetError.precision() > OUTPUT_PRECISION) {
            return unavailable(context, UnavailableReason.OUTPUT_NOT_REPRESENTABLE, null);
        }
        return new Available(context, targetError);
    }

    private static TargetPriceEvidence knownTarget(
            TargetPriceEvidence targetEvidence,
            Instant evaluationAsOf) {
        if (targetEvidence == null
                || targetEvidence.availableAt().isAfter(evaluationAsOf)
                || targetEvidence.capturedAt().isAfter(evaluationAsOf)) {
            return null;
        }
        return targetEvidence;
    }

    private static EndpointPriceResolution.ResolutionContext endpointContext(
            EndpointPriceResolution endpointResolution) {
        return switch (endpointResolution) {
            case EndpointPriceResolution.Resolved resolved -> resolved.context();
            case EndpointPriceResolution.Unavailable unavailable -> unavailable.context();
        };
    }

    private static TargetErrorResult unavailable(
            CalculationContext context,
            UnavailableReason reason,
            EndpointPriceResolution.UnavailableReason endpointReason) {
        return new Unavailable(context, reason, endpointReason);
    }
}
