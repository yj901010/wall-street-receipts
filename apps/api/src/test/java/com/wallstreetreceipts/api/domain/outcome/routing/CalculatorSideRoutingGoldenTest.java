package com.wallstreetreceipts.api.domain.outcome.routing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.groups.Tuple.tuple;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.RecordComponent;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import com.wallstreetreceipts.api.domain.call.CallDirection;
import com.wallstreetreceipts.api.domain.outcome.calculation.DirectionalWinSide;
import com.wallstreetreceipts.api.domain.outcome.calculation.TargetHitSide;
import com.wallstreetreceipts.api.domain.outcome.direction.CallDirectionPolarityPolicyVersion;
import com.wallstreetreceipts.api.domain.outcome.direction.CallDirectionPolarityRequest;
import com.wallstreetreceipts.api.domain.outcome.direction.CallDirectionPolarityResolution;
import com.wallstreetreceipts.api.domain.outcome.direction.CallDirectionPolarityResolution.Directional;
import com.wallstreetreceipts.api.domain.outcome.direction.CallDirectionPolarityResolution.NonDirectional;
import com.wallstreetreceipts.api.domain.outcome.direction.CallDirectionPolarityResolution.NonDirectionalReason;
import com.wallstreetreceipts.api.domain.outcome.direction.CallDirectionPolarityResolver;
import com.wallstreetreceipts.api.domain.outcome.routing.CalculatorSideRouting.DirectionalRoute;
import com.wallstreetreceipts.api.domain.outcome.routing.CalculatorSideRouting.NonDirectionalRoute;
import com.wallstreetreceipts.api.domain.outcome.routing.CalculatorSideRouting.Result;

class CalculatorSideRoutingGoldenTest {

    @ParameterizedTest(name = "{0} preserves its exact polarity branch")
    @MethodSource("canonicalDirectionVectors")
    void routesEveryCanonicalDirectionWithoutReinterpretingNeutral(
            CallDirection direction,
            TargetHitSide expectedTargetHitSide,
            DirectionalWinSide expectedDirectionalWinSide) {
        CallDirectionPolarityResolution resolution = resolve(direction);

        Result first = CalculatorSideRouting.route(resolution);
        Result replay = CalculatorSideRouting.route(resolution);

        assertThat(replay).isEqualTo(first);
        if (resolution instanceof Directional directional) {
            assertThat(first).isEqualTo(new DirectionalRoute(
                    directional,
                    expectedTargetHitSide,
                    expectedDirectionalWinSide));
            assertThat(((DirectionalRoute) first).source()).isSameAs(directional);
        } else {
            NonDirectional nonDirectional = (NonDirectional) resolution;
            assertThat(first).isEqualTo(new NonDirectionalRoute(nonDirectional));
            assertThat(((NonDirectionalRoute) first).source())
                    .isSameAs(nonDirectional);
            assertThat(nonDirectional.reason())
                    .isEqualTo(NonDirectionalReason.NEUTRAL_DIRECTION);
        }
    }

    private static Stream<Arguments> canonicalDirectionVectors() {
        return Stream.of(
                Arguments.of(
                        CallDirection.STRONG_BULLISH,
                        TargetHitSide.BULLISH,
                        DirectionalWinSide.BULLISH),
                Arguments.of(
                        CallDirection.BULLISH,
                        TargetHitSide.BULLISH,
                        DirectionalWinSide.BULLISH),
                Arguments.of(CallDirection.NEUTRAL, null, null),
                Arguments.of(
                        CallDirection.BEARISH,
                        TargetHitSide.BEARISH,
                        DirectionalWinSide.BEARISH),
                Arguments.of(
                        CallDirection.STRONG_BEARISH,
                        TargetHitSide.BEARISH,
                        DirectionalWinSide.BEARISH));
    }

    @Test
    void rejectsNullResolutionBeforeRouting() {
        assertThatThrownBy(() -> CalculatorSideRouting.route(null))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("resolution");
    }

    @ParameterizedTest(name = "direct {0} route rejects both contradictory sides")
    @MethodSource("directionalConstructorVectors")
    void directDirectionalRouteConstructionRecomputesBothExpectedSides(
            CallDirection direction,
            TargetHitSide expectedTargetHitSide,
            DirectionalWinSide expectedDirectionalWinSide,
            TargetHitSide contradictoryTargetHitSide,
            DirectionalWinSide contradictoryDirectionalWinSide) {
        Directional source = (Directional) resolve(direction);

        assertThat(new DirectionalRoute(
                source, expectedTargetHitSide, expectedDirectionalWinSide))
                .extracting(
                        DirectionalRoute::source,
                        DirectionalRoute::targetHitSide,
                        DirectionalRoute::directionalWinSide)
                .containsExactly(
                        source, expectedTargetHitSide, expectedDirectionalWinSide);

        assertThatThrownBy(() -> new DirectionalRoute(
                source, contradictoryTargetHitSide, expectedDirectionalWinSide))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("targetHitSide");
        assertThatThrownBy(() -> new DirectionalRoute(
                source, expectedTargetHitSide, contradictoryDirectionalWinSide))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("directionalWinSide");
    }

    private static Stream<Arguments> directionalConstructorVectors() {
        return Stream.of(
                Arguments.of(
                        CallDirection.BULLISH,
                        TargetHitSide.BULLISH,
                        DirectionalWinSide.BULLISH,
                        TargetHitSide.BEARISH,
                        DirectionalWinSide.BEARISH),
                Arguments.of(
                        CallDirection.BEARISH,
                        TargetHitSide.BEARISH,
                        DirectionalWinSide.BEARISH,
                        TargetHitSide.BULLISH,
                        DirectionalWinSide.BULLISH));
    }

    @Test
    void directRouteConstructionRejectsMissingEvidence() {
        Directional bearish = (Directional) resolve(CallDirection.BEARISH);
        NonDirectional neutral = (NonDirectional) resolve(CallDirection.NEUTRAL);

        assertThatThrownBy(() -> new DirectionalRoute(
                null, TargetHitSide.BEARISH, DirectionalWinSide.BEARISH))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("source");
        assertThatThrownBy(() -> new DirectionalRoute(
                bearish, null, DirectionalWinSide.BEARISH))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("targetHitSide");
        assertThatThrownBy(() -> new DirectionalRoute(
                bearish, TargetHitSide.BEARISH, null))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("directionalWinSide");
        assertThat(new NonDirectionalRoute(neutral).source()).isSameAs(neutral);
        assertThatThrownBy(() -> new NonDirectionalRoute(null))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("source");
    }

    @Test
    void exposesOneRoutingMethodAndExactlyTwoClosedResultRecords() {
        Method[] declaredMethods = CalculatorSideRouting.class.getDeclaredMethods();

        int outerModifiers = CalculatorSideRouting.class.getModifiers();
        assertThat(Modifier.isPublic(outerModifiers)).isTrue();
        assertThat(Modifier.isFinal(outerModifiers)).isTrue();
        assertThat(CalculatorSideRouting.class.getDeclaredFields()).isEmpty();
        assertThat(declaredMethods)
                .singleElement()
                .satisfies(method -> {
                    assertThat(method.getName()).isEqualTo("route");
                    assertThat(Modifier.isPublic(method.getModifiers())).isTrue();
                    assertThat(Modifier.isStatic(method.getModifiers())).isTrue();
                    assertThat(method.getParameterTypes())
                            .containsExactly(CallDirectionPolarityResolution.class);
                    assertThat(method.getReturnType()).isEqualTo(Result.class);
                    assertThat(method.getTypeParameters()).isEmpty();
                    assertThat(method.getExceptionTypes()).isEmpty();
                });
        assertThat(CalculatorSideRouting.class.getDeclaredConstructors())
                .singleElement()
                .satisfies(constructor -> {
                    assertThat(Modifier.isPrivate(constructor.getModifiers())).isTrue();
                    assertThat(constructor.getParameterTypes()).isEmpty();
                });

        int resultModifiers = Result.class.getModifiers();
        assertThat(Modifier.isPublic(resultModifiers)).isTrue();
        assertThat(Modifier.isStatic(resultModifiers)).isTrue();
        assertThat(Result.class.isInterface()).isTrue();
        assertThat(Result.class.isSealed()).isTrue();
        assertThat(Result.class.getDeclaredFields()).isEmpty();
        assertThat(Result.class.getDeclaredMethods()).isEmpty();
        assertThat(Result.class.getPermittedSubclasses())
                .containsExactlyInAnyOrder(
                        DirectionalRoute.class,
                        NonDirectionalRoute.class);
        assertThat(CalculatorSideRouting.class.getDeclaredClasses())
                .containsExactlyInAnyOrder(
                        Result.class,
                        DirectionalRoute.class,
                        NonDirectionalRoute.class);
        assertRecordComponents(
                DirectionalRoute.class,
                tuple("source", Directional.class),
                tuple("targetHitSide", TargetHitSide.class),
                tuple("directionalWinSide", DirectionalWinSide.class));
        assertRecordComponents(
                NonDirectionalRoute.class,
                tuple("source", NonDirectional.class));
        assertClosedRecordSurface(
                DirectionalRoute.class,
                tuple("equals", List.of(Object.class), boolean.class),
                tuple("toString", List.of(), String.class),
                tuple("hashCode", List.of(), int.class),
                tuple("source", List.of(), Directional.class),
                tuple("targetHitSide", List.of(), TargetHitSide.class),
                tuple("directionalWinSide", List.of(), DirectionalWinSide.class));
        assertClosedRecordSurface(
                NonDirectionalRoute.class,
                tuple("equals", List.of(Object.class), boolean.class),
                tuple("toString", List.of(), String.class),
                tuple("hashCode", List.of(), int.class),
                tuple("source", List.of(), NonDirectional.class));
    }

    @Test
    void replayDoesNotDependOnJvmDefaultLocaleOrTimeZone() {
        CallDirectionPolarityResolution directional =
                resolve(CallDirection.STRONG_BEARISH);
        CallDirectionPolarityResolution nonDirectional = resolve(CallDirection.NEUTRAL);
        Result directionalBaseline = CalculatorSideRouting.route(directional);
        Result nonDirectionalBaseline = CalculatorSideRouting.route(nonDirectional);
        Locale originalLocale = Locale.getDefault();
        TimeZone originalTimeZone = TimeZone.getDefault();

        try {
            Locale.setDefault(Locale.forLanguageTag("tr-TR"));
            TimeZone.setDefault(TimeZone.getTimeZone("Pacific/Kiritimati"));

            assertThat(CalculatorSideRouting.route(directional))
                    .isEqualTo(directionalBaseline);
            assertThat(CalculatorSideRouting.route(nonDirectional))
                    .isEqualTo(nonDirectionalBaseline);
        } finally {
            TimeZone.setDefault(originalTimeZone);
            Locale.setDefault(originalLocale);
        }
    }

    private static CallDirectionPolarityResolution resolve(CallDirection direction) {
        return CallDirectionPolarityResolver.resolve(new CallDirectionPolarityRequest(
                CallDirectionPolarityPolicyVersion
                        .COLLAPSE_STRONG_DIRECTIONS_NEUTRAL_NON_DIRECTIONAL_V1,
                direction));
    }

    private static void assertRecordComponents(
            Class<?> recordType,
            org.assertj.core.groups.Tuple... expected) {
        assertThat(recordType.isRecord()).isTrue();
        assertThat(recordType.getRecordComponents())
                .extracting(RecordComponent::getName, RecordComponent::getType)
                .containsExactly(expected);
    }

    private static void assertClosedRecordSurface(
            Class<?> recordType,
            org.assertj.core.groups.Tuple... expectedMethods) {
        int modifiers = recordType.getModifiers();
        assertThat(recordType.isRecord()).isTrue();
        assertThat(Modifier.isPublic(modifiers)).isTrue();
        assertThat(Modifier.isStatic(modifiers)).isTrue();
        assertThat(Modifier.isFinal(modifiers)).isTrue();

        RecordComponent[] components = recordType.getRecordComponents();
        Class<?>[] componentTypes = Arrays.stream(components)
                .map(RecordComponent::getType)
                .toArray(Class<?>[]::new);
        assertThat(recordType.getDeclaredConstructors())
                .singleElement()
                .satisfies(constructor -> {
                    assertThat(Modifier.isPublic(constructor.getModifiers())).isTrue();
                    assertThat(constructor.getParameterTypes())
                            .containsExactly(componentTypes);
                });
        assertThat(recordType.getDeclaredFields())
                .hasSize(components.length)
                .allSatisfy(field -> {
                    assertThat(Modifier.isPrivate(field.getModifiers())).isTrue();
                    assertThat(Modifier.isFinal(field.getModifiers())).isTrue();
                    assertThat(Modifier.isStatic(field.getModifiers())).isFalse();
                    assertThat(Arrays.stream(components))
                            .anySatisfy(component -> {
                                assertThat(component.getName()).isEqualTo(field.getName());
                                assertThat(component.getType()).isEqualTo(field.getType());
                            });
                });
        assertThat(recordType.getDeclaredMethods())
                .allSatisfy(method -> {
                    assertThat(Modifier.isPublic(method.getModifiers())).isTrue();
                    assertThat(Modifier.isStatic(method.getModifiers())).isFalse();
                    assertThat(method.getTypeParameters()).isEmpty();
                    assertThat(method.getExceptionTypes()).isEmpty();
                })
                .extracting(
                        Method::getName,
                        method -> Arrays.asList(method.getParameterTypes()),
                        Method::getReturnType)
                .containsExactlyInAnyOrder(expectedMethods);
    }
}
