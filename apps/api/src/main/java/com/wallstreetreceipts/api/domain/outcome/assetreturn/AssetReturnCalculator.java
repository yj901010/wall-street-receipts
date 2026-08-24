package com.wallstreetreceipts.api.domain.outcome.assetreturn;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;

import com.wallstreetreceipts.api.domain.outcome.assetreturn.AssetReturnResult.Available;
import com.wallstreetreceipts.api.domain.outcome.assetreturn.AssetReturnResult.CalculationContext;
import com.wallstreetreceipts.api.domain.outcome.assetreturn.AssetReturnResult.Unavailable;
import com.wallstreetreceipts.api.domain.outcome.assetreturn.AssetReturnResult.UnavailableReason;
import com.wallstreetreceipts.api.domain.outcome.observation.EndpointPriceResolution;
import com.wallstreetreceipts.api.domain.outcome.pricepair.AssetReturnPricePairResolution;

/** Pure signed asset price-return calculation over one complete price-pair resolution. */
public final class AssetReturnCalculator {

    private static final int OUTPUT_SCALE = 12;
    private static final int OUTPUT_PRECISION = 38;

    private AssetReturnCalculator() {
    }

    public static AssetReturnResult calculate(AssetReturnInput input) {
        Objects.requireNonNull(input, "input must not be null");
        CalculationContext context = new CalculationContext(
                input.policyVersion(),
                input.policyVersion().definitionHash(),
                input.pricePairResolution());
        if (input.pricePairResolution()
                instanceof AssetReturnPricePairResolution.Unavailable pairUnavailable) {
            return new Unavailable(context, UnavailableReason.PRICE_PAIR_UNAVAILABLE,
                    pairUnavailable.reason());
        }

        AssetReturnPricePairResolution.Resolved pairResolved =
                (AssetReturnPricePairResolution.Resolved) input.pricePairResolution();
        BigDecimal basis = pairResolved.basisObservation().price();
        EndpointPriceResolution.Resolved endpointResolved =
                (EndpointPriceResolution.Resolved) pairResolved.context()
                        .endpointPriceResolution();
        BigDecimal endpoint = endpointResolved.observation().price();
        BigDecimal numerator = endpoint.subtract(basis);
        BigDecimal assetReturn;
        try {
            assetReturn = numerator.divide(basis, OUTPUT_SCALE, RoundingMode.HALF_EVEN);
        } catch (ArithmeticException exception) {
            return new Unavailable(context, UnavailableReason.OUTPUT_NOT_REPRESENTABLE,
                    null);
        }
        if (assetReturn.precision() > OUTPUT_PRECISION) {
            return new Unavailable(context, UnavailableReason.OUTPUT_NOT_REPRESENTABLE,
                    null);
        }
        return new Available(context, assetReturn);
    }
}
