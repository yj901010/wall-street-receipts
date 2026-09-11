package com.wallstreetreceipts.api.application.scoring;

import static org.assertj.core.api.Assertions.*;
import static com.wallstreetreceipts.api.application.scoring.EndpointScoringFixture.*;
import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import com.wallstreetreceipts.api.domain.outcome.OutcomeHorizon;

class EndpointScoringFingerprintTest {
    private final EndpointScoringEvaluator evaluator = new EndpointScoringEvaluator();

    @Test void methodologyAndCompleteInputHavePinnedGoldenIdentities() {
        assertThat(EndpointScoringMethodology.definitionHash()).isEqualTo("91abc0fcbc986b47e505bbddba346977911c9977664621e4c88cb8a2cbf8ea27");
        assertThat(EndpointScoringFingerprint.fingerprint(input())).isEqualTo("c538a04a0eb74074c0d3e50af94aa4ca965b025ab20ec1df182fdc22e4df760b");
    }

    @ParameterizedTest @ValueSource(strings = {"observationId", "providerEventId", "assetId", "venueId", "priceSourceId",
        "priceSourceRevision", "provenanceId", "calendarId", "catalogRevision", "sessionId"})
    void everyEndpointSourceIdentityParticipatesEvenWhenRejectedBySelection(String field) {
        var input = input();
        var candidate = edit(input.endpointCandidates().getFirst(), field, "different-" + field);
        changed(input, edit(input, "endpointCandidates", List.of(candidate)));
    }

    @Test void timestampsPricesBasisTargetCatalogBindingAndTermsAreFingerprintInputs() {
        var input = input();
        changed(input, edit(input, "evaluationAsOf", AS_OF.plusSeconds(1)));
        changed(input, edit(input, "endpointCandidates", List.of(edit(input.endpointCandidates().getFirst(), "price", new BigDecimal("121")))));
        changed(input, edit(input, "endpointCandidates", List.of(edit(input.endpointCandidates().getFirst(), "capturedAt", AS_OF.plusSeconds(1)))));
        changed(input, edit(input, "basisCandidates", List.of(edit(input.basisCandidates().getFirst(), "providerEventId", "another-basis-event"))));
        changed(input, edit(input, "adjustmentCandidates", List.of(edit(input.adjustmentCandidates().getFirst(), "provenanceId", "another-adjustment-provenance"))));
        changed(input, edit(input, "targetEvidence", edit(input.targetEvidence(), "provenanceId", "another-target-provenance")));
        changed(input, edit(input, "targetEvidence", null));
        changed(input, edit(input, "catalogEvidence", edit(input.catalogEvidence(), "sourceRevision", "r2")));
        changed(input, edit(input, "binding", edit(input.binding(), "bindingRevision", "r2")));
        changed(input, edit(input, "terms", edit(input.terms(), "providerEventId", "another-terms-event")));
        changed(input, edit(input, "horizon", edit(input.horizon(), "horizon", OutcomeHorizon.W1)));
        var catalog = input.horizon().catalog();
        var session = edit(catalog.orderedSessions().getFirst(), "closesAt", CLOSE.plusSeconds(1));
        changed(input, edit(input, "horizon", edit(input.horizon(), "catalog", edit(catalog, "orderedSessions", List.of(session)))));
    }

    @Test void futureCandidatesArePreservedInTheFingerprintButNeverUsedAsObservedEvidence() {
        var input = input();
        var first = input.endpointCandidates().getFirst();
        var future = edit(edit(first, "observationId", "future-candidate"), "capturedAt", AS_OF.plusSeconds(1));
        var supplied = edit(input, "endpointCandidates", List.of(first, future));
        var a = evaluator.evaluate(input); var b = evaluator.evaluate(supplied);
        assertThat(b.inputFingerprint()).isNotEqualTo(a.inputFingerprint());
        assertThat(b.sharedAssetReturnAndDirectionalWin()).isEqualTo(a.sharedAssetReturnAndDirectionalWin());
        assertThat(b.targetError()).isEqualTo(a.targetError());
        changed(supplied, edit(input, "endpointCandidates", List.of(future, first))); // Receipt identifies supplied order too.
    }

    @Test void lengthPrefixesPreventDelimiterAmbiguityAndMalformedUnicodeIsRejected() {
        var input = input();
        var one = edit(edit(input.terms(), "provider", "a|b"), "providerEventId", "c");
        var two = edit(edit(input.terms(), "provider", "a"), "providerEventId", "b|c");
        changed(edit(input, "terms", one), edit(input, "terms", two));
        assertThatThrownBy(() -> evaluator.evaluate(edit(input, "terms", edit(input.terms(), "provenanceId", "bad\uD800"))))
                .isInstanceOf(IllegalArgumentException.class).hasMessage("Scoring input cannot be canonically encoded");
    }

    @Test void explicitlyBoundedListsTextAndTotalEncodingRejectExcessiveInputs() {
        var input = input();
        assertThatThrownBy(() -> edit(input, "endpointCandidates", Collections.nCopies(4097, input.endpointCandidates().getFirst())))
                .isInstanceOf(IllegalArgumentException.class).hasMessage("Candidate list too large");
        assertThatThrownBy(() -> evaluator.evaluate(edit(input, "terms", edit(input.terms(), "provenanceId", "x".repeat(65537)))))
                .isInstanceOf(IllegalArgumentException.class).hasMessage("Scoring input text too large");
        var big = edit(input.endpointCandidates().getFirst(), "provenanceId", "x".repeat(60000));
        assertThatThrownBy(() -> evaluator.evaluate(edit(input, "endpointCandidates", Collections.nCopies(20, big))))
                .isInstanceOf(IllegalArgumentException.class).hasMessage("Encoded scoring input too large");
        assertThatThrownBy(() -> edit(input, "endpointCandidates", null)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> edit(input, "endpointCandidates", java.util.Arrays.asList((Object) null))).isInstanceOf(NullPointerException.class);
    }

    @Test void recordedIdentityMustBeSupportedBeforeReplayAndRegistryModelsAreNotActivated() {
        var input = input(); var id = EndpointScoringMethodology.ID; var version = EndpointScoringMethodology.VERSION;
        var hash = EndpointScoringMethodology.definitionHash();
        assertThat(evaluator.evaluate(id, version, hash, input).inputFingerprint()).isEqualTo(evaluator.evaluate(input).inputFingerprint());
        assertThatThrownBy(() -> evaluator.evaluate("model-only", version, hash, input)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> evaluator.evaluate(id, "2.0.0", hash, input)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> evaluator.evaluate(id, version, "0".repeat(64), input)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> evaluator.evaluate(null, version, hash, input)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> evaluator.evaluate(null)).isInstanceOf(NullPointerException.class);
        assertThat(EndpointScoringEvaluator.Receipt.class.getConstructors()).isEmpty();
        assertThat(EndpointScoringEvaluator.class.getAnnotations()).isEmpty();
    }

    private static void changed(EndpointScoringInput before, EndpointScoringInput after) {
        assertThat(EndpointScoringFingerprint.fingerprint(after)).isNotEqualTo(EndpointScoringFingerprint.fingerprint(before));
    }
}
