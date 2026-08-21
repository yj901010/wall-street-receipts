package com.wallstreetreceipts.api.domain.outcome.calculation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.Locale;
import java.util.TimeZone;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import com.wallstreetreceipts.api.domain.outcome.calculation.DirectionalWinResult.Available;
import com.wallstreetreceipts.api.domain.outcome.calculation.DirectionalWinResult.Unavailable;
import com.wallstreetreceipts.api.domain.outcome.calculation.DirectionalWinResult.UnavailableReason;

class DirectionalWinCalculatorGoldenTest {

    private static final BigDecimal MAX_NUMERIC_38_12 =
            new BigDecimal("99999999999999999999999999.999999999999");

    @ParameterizedTest(name = "{0}")
    @MethodSource("availableGoldenVectors")
    void calculatesStrictDirectionalWinFromTheProvidedAssetReturn(
            String scenario,
            DirectionalWinInput input,
            boolean expectedDirectionalWin) {
        DirectionalWinInput before = new DirectionalWinInput(input.side(), input.assetReturn());

        DirectionalWinResult first = DirectionalWinCalculator.calculate(input);
        DirectionalWinResult replay = DirectionalWinCalculator.calculate(input);

        assertThat(first).as(scenario).isEqualTo(new Available(expectedDirectionalWin));
        assertThat(replay).isEqualTo(first);
        assertThat(input).isEqualTo(before);
    }

    private static Stream<Arguments> availableGoldenVectors() {
        return Stream.of(
                Arguments.of(
                        "T1 bullish positive return wins",
                        input(DirectionalWinSide.BULLISH, "0.08"),
                        true),
                Arguments.of(
                        "T2 bullish negative return loses",
                        input(DirectionalWinSide.BULLISH, "-0.05"),
                        false),
                Arguments.of(
                        "T3 bearish negative return wins",
                        input(DirectionalWinSide.BEARISH, "-0.10"),
                        true),
                Arguments.of(
                        "bearish positive return loses",
                        input(DirectionalWinSide.BEARISH, "0.01"),
                        false),
                Arguments.of(
                        "bullish exact zero is a miss",
                        input(DirectionalWinSide.BULLISH, "0.000000000000"),
                        false),
                Arguments.of(
                        "bullish scale-equivalent zero is a miss",
                        input(DirectionalWinSide.BULLISH, "0"),
                        false),
                Arguments.of(
                        "bearish exact zero is a miss",
                        input(DirectionalWinSide.BEARISH, "0"),
                        false),
                Arguments.of(
                        "bearish scale-equivalent zero is a miss",
                        input(DirectionalWinSide.BEARISH, "0.000000000000"),
                        false),
                Arguments.of(
                        "minimum positive ratio wins for bullish",
                        input(DirectionalWinSide.BULLISH, "0.000000000001"),
                        true),
                Arguments.of(
                        "minimum positive ratio loses for bearish",
                        input(DirectionalWinSide.BEARISH, "0.000000000001"),
                        false),
                Arguments.of(
                        "minimum negative ratio wins for bearish",
                        input(DirectionalWinSide.BEARISH, "-0.000000000001"),
                        true),
                Arguments.of(
                        "minimum negative ratio loses for bullish",
                        input(DirectionalWinSide.BULLISH, "-0.000000000001"),
                        false),
                Arguments.of(
                        "maximum positive NUMERIC(38,12) wins for bullish",
                        new DirectionalWinInput(DirectionalWinSide.BULLISH, MAX_NUMERIC_38_12),
                        true),
                Arguments.of(
                        "maximum negative NUMERIC(38,12) wins for bearish",
                        new DirectionalWinInput(DirectionalWinSide.BEARISH, MAX_NUMERIC_38_12.negate()),
                        true),
                Arguments.of(
                        "negative-scale positive return remains exact",
                        input(DirectionalWinSide.BULLISH, "1E+2"),
                        true),
                Arguments.of(
                        "negative-scale negative return remains exact",
                        input(DirectionalWinSide.BEARISH, "-1E+2"),
                        true),
                Arguments.of(
                        "removable trailing precision remains exact",
                        input(DirectionalWinSide.BEARISH, "-1.0000000000000"),
                        true));
    }

    @ParameterizedTest(name = "{0} missing return")
    @MethodSource("directionalWinSides")
    void reportsMissingReturnWithoutInventingFalse(DirectionalWinSide side) {
        DirectionalWinResult result = DirectionalWinCalculator.calculate(
                new DirectionalWinInput(side, null));

        assertThat(result).isEqualTo(new Unavailable(UnavailableReason.ASSET_RETURN_MISSING));
        assertThat(result).isNotEqualTo(new Available(false));
    }

    private static Stream<DirectionalWinSide> directionalWinSides() {
        return Stream.of(DirectionalWinSide.values());
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("invalidNumericInputs")
    void rejectsNonRepresentableNumericEvidence(
            String scenario,
            BigDecimal assetReturn,
            String expectedMessage) {
        assertThatThrownBy(() -> new DirectionalWinInput(DirectionalWinSide.BULLISH, assetReturn))
                .as(scenario)
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining(expectedMessage);
    }

    private static Stream<Arguments> invalidNumericInputs() {
        return Stream.of(
                Arguments.of(
                        "positive return exceeds storage scale",
                        new BigDecimal("0.0000000000001"),
                        "assetReturn exceeds NUMERIC(38,12) scale"),
                Arguments.of(
                        "negative return exceeds storage scale",
                        new BigDecimal("-0.0000000000001"),
                        "assetReturn exceeds NUMERIC(38,12) scale"),
                Arguments.of(
                        "positive return exceeds storage precision",
                        new BigDecimal("100000000000000000000000000"),
                        "assetReturn exceeds NUMERIC(38,12) precision"),
                Arguments.of(
                        "negative return exceeds storage precision",
                        new BigDecimal("-100000000000000000000000000"),
                        "assetReturn exceeds NUMERIC(38,12) precision"));
    }

    @Test
    void representabilityValidationDoesNotReplaceOrRescaleSourceValue() {
        BigDecimal assetReturn = new BigDecimal("1E+2");
        DirectionalWinInput input = new DirectionalWinInput(
                DirectionalWinSide.BULLISH, assetReturn);

        assertThat(DirectionalWinCalculator.calculate(input)).isEqualTo(new Available(true));
        assertThat(input.assetReturn()).isSameAs(assetReturn).hasScaleOf(-2);
    }

    @Test
    void inputRequirementsAndClosedSideRemainExplicit() {
        assertThatThrownBy(() -> new DirectionalWinInput(null, BigDecimal.ONE))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("side");
        assertThatThrownBy(() -> DirectionalWinCalculator.calculate(null))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("input");
        assertThat(DirectionalWinSide.values())
                .containsExactly(DirectionalWinSide.BULLISH, DirectionalWinSide.BEARISH);
    }

    @Test
    void unavailableReasonIsRequiredAndClosed() {
        assertThatThrownBy(() -> new Unavailable(null))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("reason");
        assertThat(UnavailableReason.values()).containsExactly(UnavailableReason.ASSET_RETURN_MISSING);
    }

    @Test
    void inputAndResultSurfacesRemainClosed() {
        assertThat(Arrays.stream(DirectionalWinInput.class.getRecordComponents()))
                .extracting(component -> component.getName() + ":" + component.getType().getSimpleName())
                .containsExactly(
                        "side:DirectionalWinSide",
                        "assetReturn:BigDecimal");
        assertThat(DirectionalWinResult.class.getPermittedSubclasses())
                .containsExactlyInAnyOrder(Available.class, Unavailable.class);
        assertThat(Arrays.stream(Available.class.getRecordComponents()))
                .extracting(component -> component.getName() + ":" + component.getType().getSimpleName())
                .containsExactly("directionalWin:boolean");
        assertThat(Arrays.stream(Unavailable.class.getRecordComponents()))
                .extracting(component -> component.getName() + ":" + component.getType().getSimpleName())
                .containsExactly("reason:UnavailableReason");
    }

    @Test
    void replayIsIndependentOfLocaleTimezoneAndInvocationOrder() {
        Locale originalLocale = Locale.getDefault();
        TimeZone originalTimeZone = TimeZone.getDefault();
        DirectionalWinInput bullish = input(DirectionalWinSide.BULLISH, "0.000000000001");
        DirectionalWinInput bearish = input(DirectionalWinSide.BEARISH, "-0.000000000001");

        try {
            Locale.setDefault(Locale.KOREA);
            TimeZone.setDefault(TimeZone.getTimeZone("Asia/Seoul"));
            DirectionalWinResult bullishFirst = DirectionalWinCalculator.calculate(bullish);
            DirectionalWinResult bearishSecond = DirectionalWinCalculator.calculate(bearish);

            Locale.setDefault(Locale.US);
            TimeZone.setDefault(TimeZone.getTimeZone("America/New_York"));
            DirectionalWinResult bearishFirst = DirectionalWinCalculator.calculate(bearish);
            DirectionalWinResult bullishSecond = DirectionalWinCalculator.calculate(bullish);

            assertThat(bullishFirst).isEqualTo(new Available(true)).isEqualTo(bullishSecond);
            assertThat(bearishFirst).isEqualTo(new Available(true)).isEqualTo(bearishSecond);
        } finally {
            Locale.setDefault(originalLocale);
            TimeZone.setDefault(originalTimeZone);
        }
    }

    private static DirectionalWinInput input(DirectionalWinSide side, String assetReturn) {
        return new DirectionalWinInput(side, new BigDecimal(assetReturn));
    }
}
