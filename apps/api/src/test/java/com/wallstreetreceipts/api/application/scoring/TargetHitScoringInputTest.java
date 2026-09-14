package com.wallstreetreceipts.api.application.scoring;

import java.util.*;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import com.wallstreetreceipts.api.domain.market.DataMode;
import com.wallstreetreceipts.api.domain.outcome.favorableextreme.FullWindowHighLowObservation;
import static com.wallstreetreceipts.api.application.scoring.EndpointScoringFixture.edit;
import static org.junit.jupiter.api.Assertions.*;

class TargetHitScoringInputTest {
    @Test void missingWindowIsExplicitAndNeverInvented() {
        var input = TargetHitScoringFixture.input();
        var missing = new TargetHitScoringInput(input.comparative(), null, List.of());
        assertNull(missing.windowBinding());
        assertTrue(missing.windowCandidates().isEmpty());
    }
    @Test void copiesListsButPreservesAllOriginalCandidateObjectsAndOrder() {
        var input = TargetHitScoringFixture.input();
        var candidate = input.windowCandidates().getFirst();
        var second = edit(candidate, "observationId", "demo-second");
        var list = new ArrayList<>(List.of(candidate, second, candidate));
        var copy = new TargetHitScoringInput(input.comparative(), input.windowBinding(), list);
        list.clear();
        assertEquals(List.of(candidate, second, candidate), copy.windowCandidates());
        assertSame(candidate, copy.windowCandidates().getFirst());
        assertThrows(UnsupportedOperationException.class, () -> copy.windowCandidates().clear());
    }
    @Test void requiresOriginalComparativeInputAndCandidateList() {
        var input = TargetHitScoringFixture.input();
        assertThrows(NullPointerException.class, () -> edit(input, "comparative", null));
        assertThrows(NullPointerException.class, () -> edit(input, "windowCandidates", null));
        assertThrows(IllegalArgumentException.class, () -> edit(input, "windowCandidates", Arrays.asList((Object) null)));
        assertThrows(IllegalArgumentException.class, () -> edit(input.comparative().endpoint(), "dataMode", DataMode.EOD));
    }
    @Test void rejectsErasedCandidateTypeIncludingSuppliedResults() {
        var input = TargetHitScoringFixture.input();
        assertThrows(IllegalArgumentException.class, () -> edit(input, "windowCandidates", List.of(input.windowBinding())));
        assertThrows(IllegalArgumentException.class, () -> edit(input, "windowCandidates",
                List.of(new TargetHitScoringEvaluator().evaluate(input).targetHit())));
    }
    @Test void boundsCandidateAndNestedSessionCounts() {
        var input = TargetHitScoringFixture.input();
        var candidate = input.windowCandidates().getFirst();
        assertEquals(4096, edit(input, "windowCandidates", Collections.nCopies(4096, candidate)).windowCandidates().size());
        assertThrows(IllegalArgumentException.class, () -> edit(input, "windowCandidates", Collections.nCopies(4097, candidate)));
        var ids = IntStream.range(0, 4097).mapToObj(i -> "demo-session-" + i).toList();
        var oversized = edit(candidate, "orderedSessionIds", ids);
        assertThrows(IllegalArgumentException.class, () -> edit(input, "windowCandidates", List.of(oversized)));
        assertEquals(4096, edit(candidate, "orderedSessionIds", ids.subList(0, 4096)).orderedSessionIds().size());
    }
    @Test void leavesCorrelationAndPitFailuresForOriginalSelector() {
        var input = TargetHitScoringFixture.input();
        var wrong = edit(input.windowCandidates().getFirst(), "assetId", "demo-other-asset");
        assertSame(wrong, edit(input, "windowCandidates", List.of(wrong)).windowCandidates().getFirst());
        assertSame(input.comparative(), new TargetHitScoringInput(input.comparative(),
                edit(input.windowBinding(), "assetId", "demo-other-asset"), List.of()).comparative());
    }
    @Test void receiptCannotBeConstructedByPublicCallers() {
        assertEquals(0, TargetHitScoringEvaluator.Receipt.class.getConstructors().length);
        assertEquals(List.of("comparative", "windowBinding", "windowCandidates"),
                Arrays.stream(TargetHitScoringInput.class.getRecordComponents()).map(f -> f.getName()).toList());
        assertEquals(FullWindowHighLowObservation.class,
                TargetHitScoringFixture.input().windowCandidates().getFirst().getClass());
    }
}
