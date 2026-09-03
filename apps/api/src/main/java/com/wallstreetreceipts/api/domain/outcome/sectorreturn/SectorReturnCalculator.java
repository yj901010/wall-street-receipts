package com.wallstreetreceipts.api.domain.outcome.sectorreturn;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;

import com.wallstreetreceipts.api.domain.outcome.sectorreferencepair
        .SectorReferenceLevelPairResolution;

/** Pure signed sector return calculation over one complete level-pair receipt. */
public final class SectorReturnCalculator {

    private static final int OUTPUT_SCALE = 12;
    private static final int OUTPUT_PRECISION = 38;

    private SectorReturnCalculator() {
    }

    public static SectorReturnResult calculate(SectorReturnInput input) {
        Objects.requireNonNull(input, "input must not be null");
        SectorReturnResult.CalculationContext context =
                new SectorReturnResult.CalculationContext(
                input.policyVersion(),
                input.policyVersion().definitionHash(),
                input.referenceLevelPairResolution());

        switch (input.referenceLevelPairResolution()) {
            case SectorReferenceLevelPairResolution.NotApplicable ignored -> {
                return new SectorReturnResult.NotApplicable(context);
            }
            case SectorReferenceLevelPairResolution.AssignmentUnavailable ignored -> {
                return new SectorReturnResult.AssignmentUnavailable(context);
            }
            case SectorReferenceLevelPairResolution.EndpointAnchorUnavailable ignored -> {
                return new SectorReturnResult.EndpointAnchorUnavailable(context);
            }
            case SectorReferenceLevelPairResolution.EvidenceUnavailable ignored -> {
                return new SectorReturnResult.EvidenceUnavailable(context);
            }
            case SectorReferenceLevelPairResolution.Resolved resolved -> {
                return calculateResolved(context, resolved);
            }
        }
    }

    private static SectorReturnResult calculateResolved(
            SectorReturnResult.CalculationContext context,
            SectorReferenceLevelPairResolution.Resolved resolved) {
        BigDecimal basis = resolved.basisLevelObservation().level();
        BigDecimal endpoint = resolved.endpointLevelObservation().level();
        BigDecimal sectorReturn;
        try {
            BigDecimal numerator = endpoint.subtract(basis);
            sectorReturn = numerator.divide(
                    basis, OUTPUT_SCALE, RoundingMode.HALF_EVEN);
        } catch (ArithmeticException exception) {
            return outputUnavailable(context);
        }
        if (sectorReturn.compareTo(BigDecimal.ONE.negate()) < 0
                || sectorReturn.scale() != OUTPUT_SCALE
                || sectorReturn.precision() > OUTPUT_PRECISION) {
            return outputUnavailable(context);
        }
        return new SectorReturnResult.Available(context, sectorReturn);
    }

    private static SectorReturnResult outputUnavailable(
            SectorReturnResult.CalculationContext context) {
        return new SectorReturnResult.OutputUnavailable(
                context,
                SectorReturnResult.OutputUnavailableReason
                        .OUTPUT_NOT_REPRESENTABLE);
    }
}
