package com.wallstreetreceipts.api.domain.outcome.direction;

import java.util.Objects;

import com.wallstreetreceipts.api.domain.outcome.direction.CallDirectionPolarityResolution.Directional;
import com.wallstreetreceipts.api.domain.outcome.direction.CallDirectionPolarityResolution.DirectionalSide;
import com.wallstreetreceipts.api.domain.outcome.direction.CallDirectionPolarityResolution.NonDirectional;
import com.wallstreetreceipts.api.domain.outcome.direction.CallDirectionPolarityResolution.NonDirectionalReason;
import com.wallstreetreceipts.api.domain.outcome.direction.CallDirectionPolarityResolution.ResolutionContext;

/** Applies the closed, versioned call-direction polarity mapping. */
public final class CallDirectionPolarityResolver {

    private CallDirectionPolarityResolver() {
    }

    public static CallDirectionPolarityResolution resolve(
            CallDirectionPolarityRequest request) {
        Objects.requireNonNull(request, "request must not be null");

        ResolutionContext context = new ResolutionContext(
                request.policyVersion(),
                request.policyVersion().definitionHash(),
                request.direction());
        return switch (request.direction()) {
            case STRONG_BULLISH, BULLISH ->
                    new Directional(context, DirectionalSide.BULLISH);
            case NEUTRAL -> new NonDirectional(
                    context, NonDirectionalReason.NEUTRAL_DIRECTION);
            case BEARISH, STRONG_BEARISH ->
                    new Directional(context, DirectionalSide.BEARISH);
        };
    }
}
