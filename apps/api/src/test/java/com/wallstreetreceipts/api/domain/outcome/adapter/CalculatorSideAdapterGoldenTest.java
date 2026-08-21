package com.wallstreetreceipts.api.domain.outcome.adapter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.Locale;
import java.util.TimeZone;
import java.util.stream.Stream;

import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import com.wallstreetreceipts.api.domain.outcome.calculation.DirectionalWinSide;
import com.wallstreetreceipts.api.domain.outcome.calculation.TargetHitSide;
import com.wallstreetreceipts.api.domain.outcome.direction.CallDirectionPolarityResolution.DirectionalSide;

class CalculatorSideAdapterGoldenTest {

    @ParameterizedTest(name = "{0} maps exactly into both calculator vocabularies")
    @MethodSource("calculatorSideVectors")
    void translatesBothCommonSidesToBothCalculatorVocabularies(
            DirectionalSide source,
            TargetHitSide expectedTargetHitSide,
            DirectionalWinSide expectedDirectionalWinSide) {
        TargetHitSide firstTargetHit = CalculatorSideAdapter.toTargetHitSide(source);
        DirectionalWinSide firstDirectionalWin =
                CalculatorSideAdapter.toDirectionalWinSide(source);

        assertThat(firstTargetHit).isEqualTo(expectedTargetHitSide);
        assertThat(firstDirectionalWin).isEqualTo(expectedDirectionalWinSide);
        assertThat(CalculatorSideAdapter.toTargetHitSide(source))
                .isSameAs(firstTargetHit);
        assertThat(CalculatorSideAdapter.toDirectionalWinSide(source))
                .isSameAs(firstDirectionalWin);
    }

    private static Stream<Arguments> calculatorSideVectors() {
        return Stream.of(
                Arguments.of(
                        DirectionalSide.BULLISH,
                        TargetHitSide.BULLISH,
                        DirectionalWinSide.BULLISH),
                Arguments.of(
                        DirectionalSide.BEARISH,
                        TargetHitSide.BEARISH,
                        DirectionalWinSide.BEARISH));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("nullInputVectors")
    void rejectsNullSideBeforeTranslation(
            String scenario,
            ThrowingCallable invocation) {
        assertThatThrownBy(invocation)
                .as(scenario)
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("side");
    }

    private static Stream<Arguments> nullInputVectors() {
        return Stream.of(
                Arguments.of(
                        "target-hit adapter rejects null",
                        (ThrowingCallable) () -> CalculatorSideAdapter.toTargetHitSide(null)),
                Arguments.of(
                        "directional-win adapter rejects null",
                        (ThrowingCallable) () -> CalculatorSideAdapter.toDirectionalWinSide(null)));
    }

    @Test
    void exposesOnlyTwoDirectionalSideTranslationMethodsAndNoPublicConstructor() {
        Method[] publicDeclaredMethods = Arrays.stream(
                        CalculatorSideAdapter.class.getDeclaredMethods())
                .filter(method -> Modifier.isPublic(method.getModifiers()))
                .toArray(Method[]::new);

        assertThat(CalculatorSideAdapter.class.getModifiers())
                .satisfies(modifiers -> assertThat(Modifier.isFinal(modifiers)).isTrue());
        assertThat(publicDeclaredMethods)
                .allSatisfy(method -> {
                    assertThat(Modifier.isStatic(method.getModifiers())).isTrue();
                    assertThat(method.getParameterTypes())
                            .containsExactly(DirectionalSide.class);
                });
        assertThat(publicDeclaredMethods)
                .extracting(Method::getName, Method::getReturnType)
                .containsExactlyInAnyOrder(
                        org.assertj.core.groups.Tuple.tuple(
                                "toTargetHitSide", TargetHitSide.class),
                        org.assertj.core.groups.Tuple.tuple(
                                "toDirectionalWinSide", DirectionalWinSide.class));

        assertThat(CalculatorSideAdapter.class.getDeclaredConstructors())
                .singleElement()
                .extracting(Constructor::getModifiers)
                .satisfies(modifiers -> assertThat(Modifier.isPrivate(modifiers)).isTrue());
    }

    @Test
    void translationsDoNotDependOnJvmDefaultLocaleOrTimeZone() {
        TargetHitSide targetHitBaseline =
                CalculatorSideAdapter.toTargetHitSide(DirectionalSide.BEARISH);
        DirectionalWinSide directionalWinBaseline =
                CalculatorSideAdapter.toDirectionalWinSide(DirectionalSide.BEARISH);
        Locale originalLocale = Locale.getDefault();
        TimeZone originalTimeZone = TimeZone.getDefault();

        try {
            Locale.setDefault(Locale.forLanguageTag("tr-TR"));
            TimeZone.setDefault(TimeZone.getTimeZone("Pacific/Kiritimati"));

            assertThat(CalculatorSideAdapter.toTargetHitSide(DirectionalSide.BEARISH))
                    .isSameAs(targetHitBaseline);
            assertThat(CalculatorSideAdapter.toDirectionalWinSide(DirectionalSide.BEARISH))
                    .isSameAs(directionalWinBaseline);
        } finally {
            TimeZone.setDefault(originalTimeZone);
            Locale.setDefault(originalLocale);
        }
    }
}
