package com.wallstreetreceipts.api.application.scoring;

import java.lang.reflect.InvocationTargetException;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Arrays;
import java.util.Currency;
import java.util.List;
import com.wallstreetreceipts.api.domain.call.CallDirection;
import com.wallstreetreceipts.api.domain.market.DataMode;
import com.wallstreetreceipts.api.domain.outcome.OutcomeHorizon;
import com.wallstreetreceipts.api.domain.outcome.horizon.*;
import com.wallstreetreceipts.api.domain.outcome.observation.*;
import com.wallstreetreceipts.api.domain.outcome.pricepair.*;
import com.wallstreetreceipts.api.domain.outcome.targeteligibility.BasisForecastTermsEvidence;
import com.wallstreetreceipts.api.domain.outcome.targeterror.TargetPriceEvidence;

/** Entirely synthetic explicit evidence. Never loaded into the application fixture registry. */
final class EndpointScoringFixture {
    static final Instant BASIS = Instant.parse("2026-01-05T15:00:00Z");
    static final Instant CLOSE = Instant.parse("2026-01-05T21:00:00Z");
    static final Instant AS_OF = Instant.parse("2026-01-06T00:00:00Z");
    static final Currency USD = Currency.getInstance("USD");
    static final EndpointPriceAdjustmentBasis ADJUSTMENT = EndpointPriceAdjustmentBasis.SPLIT_AND_REVERSE_SPLIT_ADJUSTED_TO_ENDPOINT_SHARE_BASIS_DIVIDEND_UNADJUSTED;
    static final CorporateActionContinuity CONTINUITY = CorporateActionContinuity.SPLIT_REVERSE_SPLIT_CONTINUOUS;

    static EndpointScoringInput input() { return input(CallDirection.BULLISH, "100", "120", "150"); }
    static EndpointScoringInput input(CallDirection direction, String basisPrice, String endpointPrice, String targetPrice) {
        var basis = new OutcomeBasis.Original("demo-call", BASIS);
        var session = new TradingSession("demo-session", BASIS.minusSeconds(1800), CLOSE);
        var catalog = new TradingSessionCatalog("demo-calendar", "r1", List.of(session));
        var horizon = new SessionCloseHorizonRequest(EndpointScoringMethodology.HORIZON, basis, OutcomeHorizon.D1, catalog);
        var calendarEvidence = new CatalogPointInTimeEvidence("demo-calendar", "r1", "demo-calendar-source", "r1", BASIS, BASIS, "demo-calendar-provenance");
        var binding = new EndpointPriceBinding("demo-binding", "r1", "demo-asset", "demo-venue", USD, "demo-prices", "r1", BASIS, BASIS, "demo-binding-provenance");
        var terms = new BasisForecastTermsEvidence("demo-terms", basis, "demo-asset", direction,
                new BasisForecastTermsEvidence.TargetDisposition.Present(new BigDecimal(targetPrice), USD, null),
                "demo-provider", "demo-terms-event", BASIS, BASIS, "demo-terms-provenance");
        var endpoint = new EndpointPriceObservation("demo-endpoint", "demo-endpoint-event", "demo-asset", "demo-venue", USD,
                "demo-prices", "r1", "demo-endpoint-provenance", "demo-calendar", "r1", "demo-session",
                EndpointPriceField.OFFICIAL_REGULAR_SESSION_CLOSE, ADJUSTMENT, CONTINUITY,
                CLOSE, CLOSE, CLOSE, new BigDecimal(endpointPrice));
        var observation = new BasisPriceObservation("demo-basis", "demo-basis-event", basis, "demo-asset", "demo-venue", USD,
                "demo-prices", "r1", "demo-basis-provenance", BasisPriceField.SOURCE_RECORDED_BASIS_EVENT_PRICE,
                ADJUSTMENT, CONTINUITY, BASIS, BASIS, BASIS, new BigDecimal(basisPrice));
        var adjustment = new PricePairAdjustmentEvidence("demo-adjustment", "demo-adjustment-event", basis, "demo-asset", "demo-venue", USD,
                "demo-actions", "r1", "demo-adjustment-provenance", "demo-basis", "demo-basis-event", "demo-endpoint", "demo-endpoint-event",
                BASIS, CLOSE, ADJUSTMENT, CONTINUITY, CLOSE, CLOSE);
        var target = new TargetPriceEvidence("demo-target", basis, "demo-asset", "demo-venue", USD, ADJUSTMENT,
                new BigDecimal(targetPrice), BASIS, BASIS, "demo-target-provenance");
        return new EndpointScoringInput(DataMode.DEMO, horizon, calendarEvidence, binding, AS_OF, terms,
                List.of(endpoint), List.of(observation), List.of(adjustment), target);
    }

    @SuppressWarnings("unchecked")
    static <T extends Record> T edit(T source, String fieldName, Object replacement) {
        try {
            var fields = source.getClass().getRecordComponents();
            var values = new Object[fields.length];
            boolean found = false;
            for (int i = 0; i < fields.length; i++) {
                boolean selected = fields[i].getName().equals(fieldName);
                found |= selected;
                values[i] = selected ? replacement : fields[i].getAccessor().invoke(source);
            }
            if (!found) throw new AssertionError("Unknown test field: " + fieldName);
            return (T) source.getClass().getDeclaredConstructor(Arrays.stream(fields).map(f -> f.getType()).toArray(Class<?>[]::new)).newInstance(values);
        } catch (InvocationTargetException failure) {
            if (failure.getCause() instanceof RuntimeException runtime) throw runtime;
            throw new AssertionError(failure.getCause());
        } catch (ReflectiveOperationException failure) { throw new AssertionError(failure); }
    }
}
