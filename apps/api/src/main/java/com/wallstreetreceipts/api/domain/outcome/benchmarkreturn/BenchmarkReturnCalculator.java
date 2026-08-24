package com.wallstreetreceipts.api.domain.outcome.benchmarkreturn;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;

import com.wallstreetreceipts.api.domain.outcome.benchmarkreferencepair
        .BenchmarkReferenceLevelPairResolution;

/** Pure signed benchmark return calculation over one complete level-pair receipt. */
public final class BenchmarkReturnCalculator {

    private static final int OUTPUT_SCALE = 12;
    private static final int OUTPUT_PRECISION = 38;

    private BenchmarkReturnCalculator() {
    }

    public static BenchmarkReturnResult calculate(BenchmarkReturnInput input) {
        Objects.requireNonNull(input, "input must not be null");
        BenchmarkReturnResult.CalculationContext context =
                new BenchmarkReturnResult.CalculationContext(
                input.policyVersion(),
                input.policyVersion().definitionHash(),
                input.referenceLevelPairResolution());

        switch (input.referenceLevelPairResolution()) {
            case BenchmarkReferenceLevelPairResolution.NotApplicable ignored -> {
                return new BenchmarkReturnResult.NotApplicable(context);
            }
            case BenchmarkReferenceLevelPairResolution.AssignmentUnavailable ignored -> {
                return new BenchmarkReturnResult.AssignmentUnavailable(context);
            }
            case BenchmarkReferenceLevelPairResolution.EndpointAnchorUnavailable ignored -> {
                return new BenchmarkReturnResult.EndpointAnchorUnavailable(context);
            }
            case BenchmarkReferenceLevelPairResolution.EvidenceUnavailable ignored -> {
                return new BenchmarkReturnResult.EvidenceUnavailable(context);
            }
            case BenchmarkReferenceLevelPairResolution.Resolved resolved -> {
                return calculateResolved(context, resolved);
            }
        }
    }

    private static BenchmarkReturnResult calculateResolved(
            BenchmarkReturnResult.CalculationContext context,
            BenchmarkReferenceLevelPairResolution.Resolved resolved) {
        BigDecimal basis = resolved.basisLevelObservation().level();
        BigDecimal endpoint = resolved.endpointLevelObservation().level();
        BigDecimal benchmarkReturn;
        try {
            BigDecimal numerator = endpoint.subtract(basis);
            benchmarkReturn = numerator.divide(
                    basis, OUTPUT_SCALE, RoundingMode.HALF_EVEN);
        } catch (ArithmeticException exception) {
            return outputUnavailable(context);
        }
        if (benchmarkReturn.compareTo(BigDecimal.ONE.negate()) < 0
                || benchmarkReturn.scale() != OUTPUT_SCALE
                || benchmarkReturn.precision() > OUTPUT_PRECISION) {
            return outputUnavailable(context);
        }
        return new BenchmarkReturnResult.Available(context, benchmarkReturn);
    }

    private static BenchmarkReturnResult outputUnavailable(
            BenchmarkReturnResult.CalculationContext context) {
        return new BenchmarkReturnResult.OutputUnavailable(
                context,
                BenchmarkReturnResult.OutputUnavailableReason
                        .OUTPUT_NOT_REPRESENTABLE);
    }
}
