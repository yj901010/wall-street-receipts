package com.wallstreetreceipts.api.application.scoring;

import java.util.List;
import java.util.Objects;
import com.wallstreetreceipts.api.domain.outcome.favorableextreme.FullWindowHighLowObservation;
import com.wallstreetreceipts.api.domain.outcome.favorableextreme.WindowPriceBinding;

/** DEMO supplied aggregates, not raw-trade coverage or authenticated provider evidence. */
public record TargetHitScoringInput(
        ComparativeScoringInput comparative,
        WindowPriceBinding windowBinding,
        List<FullWindowHighLowObservation> windowCandidates) {
    public TargetHitScoringInput {
        Objects.requireNonNull(comparative, "comparative");
        Objects.requireNonNull(windowCandidates, "windowCandidates");
        if (windowCandidates.size() > 4096) throw new IllegalArgumentException("Too many window candidates");
        // Validate erased callers as well as wire input; no preselection or deduplication.
        for (Object value : windowCandidates) {
            if (!(value instanceof FullWindowHighLowObservation observation)) {
                throw new IllegalArgumentException("Invalid window candidate type");
            }
            if (observation.orderedSessionIds().size() > 4096) {
                throw new IllegalArgumentException("Too many window sessions");
            }
        }
        windowCandidates = List.copyOf(windowCandidates);
        // Null binding and empty candidates mean missing; the original selector owns PIT/correlation reasons.
    }
}
