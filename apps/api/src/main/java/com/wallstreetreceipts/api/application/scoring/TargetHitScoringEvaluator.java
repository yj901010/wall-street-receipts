package com.wallstreetreceipts.api.application.scoring;

import java.util.Objects;
import com.wallstreetreceipts.api.domain.outcome.direction.CallDirectionPolarityRequest;
import com.wallstreetreceipts.api.domain.outcome.direction.CallDirectionPolarityResolver;
import com.wallstreetreceipts.api.domain.outcome.routing.CalculatorSideRouting;
import com.wallstreetreceipts.api.domain.outcome.targeteligibility.*;
import com.wallstreetreceipts.api.domain.outcome.favorableextreme.*;
import com.wallstreetreceipts.api.domain.outcome.targethitorchestration.*;
import com.wallstreetreceipts.api.domain.outcome.targethitreadiness.*;

/** Pure six-meaning DEMO composition from original inputs, never supplied resolution objects. */
public final class TargetHitScoringEvaluator {
    public Receipt evaluate(TargetHitScoringInput input) {
        return evaluate(TargetHitScoringMethodology.ID, TargetHitScoringMethodology.VERSION,
                TargetHitScoringMethodology.definitionHash(), input);
    }

    public Receipt evaluate(String id, String version, String definitionHash, TargetHitScoringInput input) {
        if (!TargetHitScoringMethodology.ID.equals(id) || !TargetHitScoringMethodology.VERSION.equals(version)
                || !TargetHitScoringMethodology.definitionHash().equals(definitionHash)) {
            throw new IllegalArgumentException("Unsupported target-hit scoring methodology");
        }
        Objects.requireNonNull(input, "input");
        String fingerprint = TargetHitScoringInputCodec.hash(TargetHitScoringInputCodec.encode(input));
        var comparative = new ComparativeScoringEvaluator().evaluate(input.comparative());
        var endpoint = input.comparative().endpoint();
        var route = CalculatorSideRouting.route(CallDirectionPolarityResolver.resolve(
                new CallDirectionPolarityRequest(EndpointScoringMethodology.POLARITY, endpoint.terms().direction())));
        var eligibility = TargetEligibilityResolver.resolve(new TargetEligibilityRequest(
                TargetHitScoringMethodology.ELIGIBILITY, comparative.endpoint().horizon(), endpoint.terms(),
                route, endpoint.targetEvidence(), endpoint.catalogEvidence(), endpoint.evaluationAsOf()));
        // Non-ready branches must never invoke the window selector, even if candidates are supplied.
        FavorableExtremeResolution extreme = eligibility instanceof TargetEligibilityResolution.ReadyForWindowEvidence ready
                ? FavorableExtremeSelector.select(new FavorableExtremeRequest(TargetHitScoringMethodology.EXTREME,
                        ready, input.windowBinding(), input.windowCandidates()))
                : null;
        var result = TargetHitOrchestrator.orchestrate(new TargetHitOrchestrationRequest(
                TargetHitScoringMethodology.ORCHESTRATION, eligibility, extreme));
        var readiness = TargetHitReadinessResolver.resolve(new TargetHitReadinessRequest(
                TargetHitScoringMethodology.READINESS, result));
        return new Receipt(input, fingerprint, comparative, readiness);
    }

    /** Construction is private: only evaluation attests selector/calculator invocation from these inputs. */
    public static final class Receipt {
        private final TargetHitScoringInput input;
        private final String fingerprint;
        private final ComparativeScoringEvaluator.Receipt comparative;
        private final TargetHitReadinessResolution targetHit;

        private Receipt(TargetHitScoringInput input, String fingerprint,
                ComparativeScoringEvaluator.Receipt comparative, TargetHitReadinessResolution targetHit) {
            this.input = input;
            this.fingerprint = fingerprint;
            this.comparative = comparative;
            this.targetHit = targetHit;
        }
        public TargetHitScoringInput input() { return input; }
        public String inputFingerprint() { return fingerprint; }
        public String methodologyId() { return TargetHitScoringMethodology.ID; }
        public String methodologyVersion() { return TargetHitScoringMethodology.VERSION; }
        public String methodologyDefinitionHash() { return TargetHitScoringMethodology.definitionHash(); }
        public ComparativeScoringEvaluator.Receipt comparative() { return comparative; }
        public TargetHitReadinessResolution targetHit() { return targetHit; }
        public boolean dataComplete() { return false; }
    }
}
