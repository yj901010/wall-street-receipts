package com.wallstreetreceipts.api.application.scoring;

import java.math.BigDecimal;
import java.util.List;
import com.wallstreetreceipts.api.domain.outcome.favorableextreme.*;
import com.wallstreetreceipts.api.domain.outcome.targethitorchestration.TargetHitOrchestrationResolution;
import com.wallstreetreceipts.api.domain.outcome.targethitreadiness.TargetHitReadinessResolution;
import static com.wallstreetreceipts.api.application.scoring.EndpointScoringFixture.*;

/** Synthetic test inputs only. No canonical fixture or independently verified raw-trade coverage. */
final class TargetHitScoringFixture {
    static TargetHitScoringInput input() { return input(EndpointScoringFixture.input(), "160", "80"); }
    static TargetHitScoringInput input(EndpointScoringInput e, String high, String low) {
        var b = e.binding();
        var binding = new WindowPriceBinding("demo-window-binding", "r1", b.assetId(), b.primaryVenueId(), b.currency(),
                "demo-window-source", "r1", BASIS, BASIS, "demo-window-binding-provenance");
        var observation = new FullWindowHighLowObservation("demo-window", "demo-window-event", e.horizon().basis(),
                e.horizon().horizon(), b.assetId(), b.primaryVenueId(), b.currency(), binding.priceSourceId(),
                binding.priceSourceRevision(), "demo-window-provenance", e.horizon().catalog().calendarId(),
                e.horizon().catalog().revision(), List.of("demo-session"), e.horizon().basis().eventTime(),
                FullWindowHighLowObservation.BoundaryType.EXCLUSIVE, CLOSE,
                FullWindowHighLowObservation.BoundaryType.INCLUSIVE,
                FullWindowHighLowObservation.WindowPriceField.PRIMARY_VENUE_REGULAR_SESSION_CAUSAL_WINDOW_HIGH_LOW_PAIR,
                FullWindowHighLowObservation.WindowCoverageCompleteness.EXACT_CAUSAL_WINDOW_SESSION_UNION,
                ADJUSTMENT, CONTINUITY, CLOSE, CLOSE, new BigDecimal(high), new BigDecimal(low));
        return new TargetHitScoringInput(ComparativeScoringFixture.input(e), binding, List.of(observation));
    }
    static TargetHitScoringInput withEndpoint(TargetHitScoringInput input, EndpointScoringInput endpoint) {
        return new TargetHitScoringInput(ComparativeScoringFixture.input(endpoint), input.windowBinding(), input.windowCandidates());
    }
    static TargetHitOrchestrationResolution source(TargetHitScoringEvaluator.Receipt receipt) {
        return switch (receipt.targetHit()) {
            case TargetHitReadinessResolution.Settled r -> r.sourceResult();
            case TargetHitReadinessResolution.AwaitingEndpoint r -> r.sourceResult();
            case TargetHitReadinessResolution.EvidenceUnavailable r -> r.sourceResult();
        };
    }
}
