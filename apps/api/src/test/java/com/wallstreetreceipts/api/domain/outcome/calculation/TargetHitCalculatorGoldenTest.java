package com.wallstreetreceipts.api.domain.outcome.calculation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.stream.Stream;

import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import com.wallstreetreceipts.api.domain.outcome.calculation.TargetHitResult.Available;
import com.wallstreetreceipts.api.domain.outcome.calculation.TargetHitResult.Unavailable;
import com.wallstreetreceipts.api.domain.outcome.calculation.TargetHitResult.UnavailableReason;

class TargetHitCalculatorGoldenTest {

    private static final BigDecimal MAX_NUMERIC_38_12 =
            new BigDecimal("99999999999999999999999999.999999999999");

    @ParameterizedTest(name = "{0}")
    @MethodSource("availableGoldenVectors")
    void calculatesInclusiveTargetHitFromThePreselectedFavorableExtreme(
            String scenario,
            TargetHitInput input,
            boolean expectedTargetHit) {
        TargetHitInput before = new TargetHitInput(
                input.side(), input.target(), input.favorableExtreme());

        TargetHitResult first = TargetHitCalculator.calculate(input);
        TargetHitResult replay = TargetHitCalculator.calculate(input);

        assertThat(first).as(scenario).isEqualTo(new Available(expectedTargetHit));
        assertThat(replay).isEqualTo(first);
        assertThat(input).isEqualTo(before);
    }

    private static Stream<Arguments> availableGoldenVectors() {
        return Stream.of(
                Arguments.of(
                        "T1 bullish window high crosses target",
                        input(TargetHitSide.BULLISH, "110", "112"),
                        true),
                Arguments.of(
                        "T2 bullish window high remains below target",
                        input(TargetHitSide.BULLISH, "120", "103"),
                        false),
                Arguments.of(
                        "bullish equality is inclusive",
                        input(TargetHitSide.BULLISH, "110", "110.000000000000"),
                        true),
                Arguments.of(
                        "bullish fractional value immediately below target is a miss",
                        input(TargetHitSide.BULLISH, "110", "109.999999999999"),
                        false),
                Arguments.of(
                        "T3 bearish window low crosses target",
                        input(TargetHitSide.BEARISH, "170", "168"),
                        true),
                Arguments.of(
                        "bearish window low remains above target",
                        input(TargetHitSide.BEARISH, "170", "170.000000000001"),
                        false),
                Arguments.of(
                        "bearish equality is inclusive",
                        input(TargetHitSide.BEARISH, "170.000000000000", "170"),
                        true),
                Arguments.of(
                        "minimum positive NUMERIC(38,12) value is exact",
                        input(TargetHitSide.BULLISH, "0.000000000001", "0.000000000001"),
                        true),
                Arguments.of(
                        "maximum positive NUMERIC(38,12) value is exact",
                        new TargetHitInput(TargetHitSide.BEARISH, MAX_NUMERIC_38_12, MAX_NUMERIC_38_12),
                        true),
                Arguments.of(
                        "negative-scale and scale-equivalent values compare exactly",
                        input(TargetHitSide.BULLISH, "1E+2", "100.000000000000"),
                        true),
                Arguments.of(
                        "exact removable trailing precision is representable",
                        input(TargetHitSide.BULLISH, "1.0000000000000", "1.0000000000000"),
                        true));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("unavailableGoldenVectors")
    void reportsTheExactMissingInputReasonWithoutInventingFalse(
            String scenario,
            TargetHitInput input,
            UnavailableReason expectedReason) {
        TargetHitResult result = TargetHitCalculator.calculate(input);

        assertThat(result).as(scenario).isEqualTo(new Unavailable(expectedReason));
        assertThat(result).isNotEqualTo(new Available(false));
    }

    private static Stream<Arguments> unavailableGoldenVectors() {
        return Stream.of(
                Arguments.of(
                        "bullish target is missing",
                        input(TargetHitSide.BULLISH, null, "112"),
                        UnavailableReason.TARGET_MISSING),
                Arguments.of(
                        "bearish target is missing",
                        input(TargetHitSide.BEARISH, null, "168"),
                        UnavailableReason.TARGET_MISSING),
                Arguments.of(
                        "bullish favorable extreme is missing",
                        input(TargetHitSide.BULLISH, "110", null),
                        UnavailableReason.FAVORABLE_EXTREME_MISSING),
                Arguments.of(
                        "bearish favorable extreme is missing",
                        input(TargetHitSide.BEARISH, "170", null),
                        UnavailableReason.FAVORABLE_EXTREME_MISSING),
                Arguments.of(
                        "bullish target and favorable extreme are missing",
                        input(TargetHitSide.BULLISH, null, null),
                        UnavailableReason.TARGET_AND_FAVORABLE_EXTREME_MISSING),
                Arguments.of(
                        "bearish target and favorable extreme are missing",
                        input(TargetHitSide.BEARISH, null, null),
                        UnavailableReason.TARGET_AND_FAVORABLE_EXTREME_MISSING));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("invalidNumericInputs")
    void rejectsNonPositiveOrNonRepresentableNumericEvidence(
            String scenario,
            ThrowingCallable construction,
            String expectedMessage) {
        assertThatThrownBy(construction)
                .as(scenario)
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining(expectedMessage);
    }

    private static Stream<Arguments> invalidNumericInputs() {
        String tooPrecise = "0.0000000000001";
        String tooLarge = "100000000000000000000000000";
        return Stream.of(
                invalid("zero target", () -> input(TargetHitSide.BULLISH, "0", "1"),
                        "target must be positive"),
                invalid("negative target", () -> input(TargetHitSide.BULLISH, "-1", "1"),
                        "target must be positive"),
                invalid("zero favorable extreme", () -> input(TargetHitSide.BULLISH, "1", "0"),
                        "favorableExtreme must be positive"),
                invalid("negative favorable extreme", () -> input(TargetHitSide.BEARISH, "1", "-1"),
                        "favorableExtreme must be positive"),
                invalid("target exceeds storage scale", () -> input(
                        TargetHitSide.BULLISH, tooPrecise, "1"),
                        "target exceeds NUMERIC(38,12) scale"),
                invalid("favorable extreme exceeds storage scale", () -> input(
                        TargetHitSide.BEARISH, "1", tooPrecise),
                        "favorableExtreme exceeds NUMERIC(38,12) scale"),
                invalid("target exceeds storage precision", () -> input(
                        TargetHitSide.BULLISH, tooLarge, "1"),
                        "target exceeds NUMERIC(38,12) precision"),
                invalid("favorable extreme exceeds storage precision", () -> input(
                        TargetHitSide.BEARISH, "1", tooLarge),
                        "favorableExtreme exceeds NUMERIC(38,12) precision"),
                invalid("invalid target is rejected when favorable extreme is missing", () -> input(
                        TargetHitSide.BULLISH, tooPrecise, null),
                        "target exceeds NUMERIC(38,12) scale"),
                invalid("invalid favorable extreme is rejected when target is missing", () -> input(
                        TargetHitSide.BEARISH, null, tooPrecise),
                        "favorableExtreme exceeds NUMERIC(38,12) scale"));
    }

    @Test
    void representabilityValidationDoesNotReplaceOrRescaleSourceValues() {
        BigDecimal target = new BigDecimal("1E+2");
        BigDecimal favorableExtreme = new BigDecimal("100.000000000000");
        TargetHitInput input = new TargetHitInput(TargetHitSide.BULLISH, target, favorableExtreme);

        assertThat(TargetHitCalculator.calculate(input)).isEqualTo(new Available(true));
        assertThat(input.target()).isSameAs(target).hasScaleOf(-2);
        assertThat(input.favorableExtreme()).isSameAs(favorableExtreme).hasScaleOf(12);
    }

    @Test
    void sideAndCalculatorInputAreRequiredInstructions() {
        assertThatThrownBy(() -> new TargetHitInput(null, BigDecimal.ONE, BigDecimal.ONE))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("side");
        assertThatThrownBy(() -> TargetHitCalculator.calculate(null))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("input");
        assertThat(TargetHitSide.values()).containsExactly(TargetHitSide.BULLISH, TargetHitSide.BEARISH);
    }

    @Test
    void unavailableReasonIsRequiredAndTheContractHasOnlyTheThreeClosedReasons() {
        assertThatThrownBy(() -> new Unavailable(null))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("reason");
        assertThat(UnavailableReason.values()).containsExactly(
                UnavailableReason.TARGET_MISSING,
                UnavailableReason.FAVORABLE_EXTREME_MISSING,
                UnavailableReason.TARGET_AND_FAVORABLE_EXTREME_MISSING);
    }

    @Test
    void inputAndResultSurfacesRemainClosed() {
        assertThat(Arrays.stream(TargetHitInput.class.getRecordComponents()))
                .extracting(component -> component.getName() + ":" + component.getType().getSimpleName())
                .containsExactly(
                        "side:TargetHitSide",
                        "target:BigDecimal",
                        "favorableExtreme:BigDecimal");
        assertThat(TargetHitResult.class.getPermittedSubclasses())
                .containsExactlyInAnyOrder(Available.class, Unavailable.class);
        assertThat(Arrays.stream(Available.class.getRecordComponents()))
                .extracting(component -> component.getName() + ":" + component.getType().getSimpleName())
                .containsExactly("targetHit:boolean");
        assertThat(Arrays.stream(Unavailable.class.getRecordComponents()))
                .extracting(component -> component.getName() + ":" + component.getType().getSimpleName())
                .containsExactly("reason:UnavailableReason");
    }

    private static TargetHitInput input(
            TargetHitSide side,
            String target,
            String favorableExtreme) {
        return new TargetHitInput(side, decimal(target), decimal(favorableExtreme));
    }

    private static BigDecimal decimal(String value) {
        return value == null ? null : new BigDecimal(value);
    }

    private static Arguments invalid(
            String scenario,
            ThrowingCallable construction,
            String expectedMessage) {
        return Arguments.of(scenario, construction, expectedMessage);
    }
}
