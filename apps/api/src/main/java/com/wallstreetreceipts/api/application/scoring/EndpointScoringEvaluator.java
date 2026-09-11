package com.wallstreetreceipts.api.application.scoring;

import java.util.Objects;
import java.util.Optional;
import com.wallstreetreceipts.api.domain.outcome.horizon.*;
import com.wallstreetreceipts.api.domain.outcome.observation.*;
import com.wallstreetreceipts.api.domain.outcome.pricepair.*;
import com.wallstreetreceipts.api.domain.outcome.assetreturn.*;
import com.wallstreetreceipts.api.domain.outcome.direction.*;
import com.wallstreetreceipts.api.domain.outcome.routing.CalculatorSideRouting;
import com.wallstreetreceipts.api.domain.outcome.directionalwinorchestration.*;
import com.wallstreetreceipts.api.domain.outcome.directionalwinreadiness.*;
import com.wallstreetreceipts.api.domain.outcome.targeterror.*;
import com.wallstreetreceipts.api.domain.outcome.targeterrorreadiness.*;

/** Side-effect-free application composition. No Spring bean, clock read, database, provider or publication. */
public final class EndpointScoringEvaluator {
    public Receipt evaluate(EndpointScoringInput input) {
        return evaluate(EndpointScoringMethodology.ID, EndpointScoringMethodology.VERSION,
                EndpointScoringMethodology.definitionHash(), input);
    }

    public Receipt evaluate(String methodologyId, String methodologyVersion, String definitionHash, EndpointScoringInput input) {
        if (!EndpointScoringMethodology.ID.equals(methodologyId) || !EndpointScoringMethodology.VERSION.equals(methodologyVersion)
                || !EndpointScoringMethodology.definitionHash().equals(definitionHash)) {
            throw new IllegalArgumentException("Unsupported scoring methodology identity");
        }
        Objects.requireNonNull(input, "input");
        String fingerprint = EndpointScoringFingerprint.fingerprint(input);
        var horizon = SessionCloseHorizonResolver.resolve(input.horizon());
        if (horizon instanceof SessionCloseHorizonResolution.Incomplete) {
            return new Receipt(input, fingerprint, horizon, null, null);
        }
        var endpoint = EndpointPriceSelector.select(new EndpointPriceRequest(EndpointScoringMethodology.ENDPOINT,
                (SessionCloseHorizonResolution.Resolved) horizon, input.catalogEvidence(), input.binding(),
                input.evaluationAsOf(), input.endpointCandidates()));
        var pair = AssetReturnPricePairSelector.select(new AssetReturnPricePairRequest(EndpointScoringMethodology.PAIR,
                endpoint, input.basisCandidates(), input.adjustmentCandidates()));
        var assetReturn = AssetReturnCalculator.calculate(new AssetReturnInput(EndpointScoringMethodology.RETURN, pair));
        var route = CalculatorSideRouting.route(CallDirectionPolarityResolver.resolve(
                new CallDirectionPolarityRequest(EndpointScoringMethodology.POLARITY, input.terms().direction())));
        var direction = DirectionalWinOrchestrator.orchestrate(new DirectionalWinOrchestrationRequest(
                EndpointScoringMethodology.DIRECTION, input.terms(), route, assetReturn));
        var shared = DirectionalWinReadinessResolver.resolve(new DirectionalWinReadinessRequest(
                EndpointScoringMethodology.SHARED, direction));
        var targetError = TargetErrorCalculator.calculate(new TargetErrorInput(EndpointScoringMethodology.ERROR,
                endpoint, input.targetEvidence()));
        var errorReadiness = TargetErrorReadinessResolver.resolve(new TargetErrorReadinessRequest(
                EndpointScoringMethodology.ERROR_READINESS, targetError));
        return new Receipt(input, fingerprint, horizon, shared, errorReadiness);
    }

    /** Only evaluate can construct receipts; preserved inputs allow independent replay and audit. */
    public static final class Receipt {
        private final EndpointScoringInput input;
        private final String fingerprint;
        private final SessionCloseHorizonResolution horizon;
        private final DirectionalWinReadinessResolution shared;
        private final TargetErrorReadinessResolution targetError;

        private Receipt(EndpointScoringInput input, String fingerprint, SessionCloseHorizonResolution horizon,
                DirectionalWinReadinessResolution shared, TargetErrorReadinessResolution targetError) {
            this.input = input; this.fingerprint = fingerprint; this.horizon = horizon;
            this.shared = shared; this.targetError = targetError;
        }

        public EndpointScoringInput input() { return input; }
        public String methodologyId() { return EndpointScoringMethodology.ID; }
        public String methodologyVersion() { return EndpointScoringMethodology.VERSION; }
        public String methodologyDefinitionHash() { return EndpointScoringMethodology.definitionHash(); }
        public String inputFingerprint() { return fingerprint; }
        public SessionCloseHorizonResolution horizon() { return horizon; }
        public Optional<DirectionalWinReadinessResolution> sharedAssetReturnAndDirectionalWin() { return Optional.ofNullable(shared); }
        public Optional<TargetErrorReadinessResolution> targetError() { return Optional.ofNullable(targetError); }
        public boolean dataComplete() { return false; } // Three metric meanings are never the ten-metric canonical outcome.
    }
}
