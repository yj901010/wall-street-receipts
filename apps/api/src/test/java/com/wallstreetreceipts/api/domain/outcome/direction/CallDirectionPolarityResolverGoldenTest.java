package com.wallstreetreceipts.api.domain.outcome.direction;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.Locale;
import java.util.TimeZone;
import java.util.stream.Stream;

import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import com.wallstreetreceipts.api.domain.call.CallDirection;
import com.wallstreetreceipts.api.domain.outcome.direction.CallDirectionPolarityResolution.Directional;
import com.wallstreetreceipts.api.domain.outcome.direction.CallDirectionPolarityResolution.DirectionalSide;
import com.wallstreetreceipts.api.domain.outcome.direction.CallDirectionPolarityResolution.NonDirectional;
import com.wallstreetreceipts.api.domain.outcome.direction.CallDirectionPolarityResolution.NonDirectionalReason;
import com.wallstreetreceipts.api.domain.outcome.direction.CallDirectionPolarityResolution.ResolutionContext;

class CallDirectionPolarityResolverGoldenTest {

    private static final String CANONICAL_DEFINITION =
            "{\"policyVersion\":"
            + "\"COLLAPSE_STRONG_DIRECTIONS_NEUTRAL_NON_DIRECTIONAL_V1\","
            + "\"inputType\":\"CallDirection\","
            + "\"mappings\":{\"STRONG_BULLISH\":\"BULLISH\","
            + "\"BULLISH\":\"BULLISH\",\"NEUTRAL\":\"NON_DIRECTIONAL\","
            + "\"BEARISH\":\"BEARISH\",\"STRONG_BEARISH\":\"BEARISH\"},"
            + "\"resultVariants\":[\"DIRECTIONAL\",\"NON_DIRECTIONAL\"],"
            + "\"directionalSides\":[\"BULLISH\",\"BEARISH\"],"
            + "\"nonDirectionalReason\":\"NEUTRAL_DIRECTION\","
            + "\"directResultConsistency\":\"DIRECTION_MUST_MATCH_MAPPING\","
            + "\"nullDirectionBehavior\":\"REJECT\","
            + "\"fallbackBehavior\":\"ABSENT\"}";
    private static final String DEFINITION_HASH =
            "d83eccc92fedd7ba025745be2c8e78245bc308d0ff479467fa61afe543dc8a50";

    @ParameterizedTest(name = "{0} resolves to the exact approved result")
    @MethodSource("directionalMappingVectors")
    void resolvesEveryCanonicalDirectionInExactSourceOrderWithoutFallback(
            CallDirection inputDirection,
            CallDirectionPolarityResolution expectedResult) {
        CallDirectionPolarityRequest request = request(inputDirection);

        CallDirectionPolarityResolution first =
                CallDirectionPolarityResolver.resolve(request);
        CallDirectionPolarityResolution replay =
                CallDirectionPolarityResolver.resolve(request);

        assertThat(first).isEqualTo(expectedResult);
        assertThat(replay).isEqualTo(first);
        assertThat(contextOf(first).direction()).isEqualTo(inputDirection);
        assertThat(contextOf(first).policyDefinitionHash())
                .isEqualTo(DEFINITION_HASH);
    }

    private static Stream<Arguments> directionalMappingVectors() {
        return Stream.of(
                Arguments.of(
                        CallDirection.STRONG_BULLISH,
                        new Directional(
                                context(CallDirection.STRONG_BULLISH),
                                DirectionalSide.BULLISH)),
                Arguments.of(
                        CallDirection.BULLISH,
                        new Directional(
                                context(CallDirection.BULLISH),
                                DirectionalSide.BULLISH)),
                Arguments.of(
                        CallDirection.NEUTRAL,
                        new NonDirectional(
                                context(CallDirection.NEUTRAL),
                                NonDirectionalReason.NEUTRAL_DIRECTION)),
                Arguments.of(
                        CallDirection.BEARISH,
                        new Directional(
                                context(CallDirection.BEARISH),
                                DirectionalSide.BEARISH)),
                Arguments.of(
                        CallDirection.STRONG_BEARISH,
                        new Directional(
                                context(CallDirection.STRONG_BEARISH),
                                DirectionalSide.BEARISH)));
    }

    @Test
    void preservesNeutralAsExplicitNonDirectionalEvidenceRatherThanFalseOrLoss() {
        CallDirectionPolarityRequest request = request(CallDirection.NEUTRAL);

        CallDirectionPolarityResolution result =
                CallDirectionPolarityResolver.resolve(request);

        assertThat(result).isEqualTo(new NonDirectional(
                context(CallDirection.NEUTRAL),
                NonDirectionalReason.NEUTRAL_DIRECTION));
        assertThat(result).isInstanceOf(NonDirectional.class)
                .isNotInstanceOf(Directional.class);
        NonDirectional nonDirectional = (NonDirectional) result;
        assertThat(nonDirectional.reason())
                .isEqualTo(NonDirectionalReason.NEUTRAL_DIRECTION);
        assertThat(nonDirectional.context().direction()).isEqualTo(CallDirection.NEUTRAL);
    }

    @Test
    void canonicalPolicyDefinitionHasStableExactUtf8BytesAndIndependentSha256()
            throws NoSuchAlgorithmException {
        CallDirectionPolarityPolicyVersion policy = policy();

        byte[] firstRead = policy.canonicalDefinitionUtf8();
        String independentlyCalculatedHash = HexFormat.of().formatHex(
                MessageDigest.getInstance("SHA-256")
                        .digest(CANONICAL_DEFINITION.getBytes(StandardCharsets.UTF_8)));

        assertThat(policy.canonicalDefinition()).isEqualTo(CANONICAL_DEFINITION);
        assertThat(firstRead)
                .hasSize(489)
                .containsExactly(CANONICAL_DEFINITION.getBytes(StandardCharsets.UTF_8));
        assertThat(independentlyCalculatedHash).isEqualTo(DEFINITION_HASH);
        assertThat(policy.definitionHash()).isEqualTo(DEFINITION_HASH);

        firstRead[0] = (byte) '!';
        assertThat(policy.canonicalDefinitionUtf8())
                .containsExactly(CANONICAL_DEFINITION.getBytes(StandardCharsets.UTF_8));
    }

    @Test
    void resultDoesNotDependOnJvmDefaultLocaleOrTimeZone() {
        CallDirectionPolarityRequest request = request(CallDirection.STRONG_BEARISH);
        CallDirectionPolarityResolution baseline =
                CallDirectionPolarityResolver.resolve(request);
        Locale originalLocale = Locale.getDefault();
        TimeZone originalTimeZone = TimeZone.getDefault();

        try {
            Locale.setDefault(Locale.forLanguageTag("tr-TR"));
            TimeZone.setDefault(TimeZone.getTimeZone("Pacific/Kiritimati"));

            assertThat(CallDirectionPolarityResolver.resolve(request))
                    .isEqualTo(baseline);
        } finally {
            TimeZone.setDefault(originalTimeZone);
            Locale.setDefault(originalLocale);
        }
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("invalidRequestVectors")
    void rejectsMissingResolutionInput(
            String scenario,
            ThrowingCallable construction,
            String expectedMessage) {
        assertThatThrownBy(construction)
                .as(scenario)
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining(expectedMessage);
    }

    private static Stream<Arguments> invalidRequestVectors() {
        return Stream.of(
                invalid(
                        "null policy",
                        () -> new CallDirectionPolarityRequest(
                                null, CallDirection.BULLISH),
                        "policyVersion"),
                invalid(
                        "null direction",
                        () -> new CallDirectionPolarityRequest(policy(), null),
                        "direction"),
                invalid(
                        "null resolver request",
                        () -> CallDirectionPolarityResolver.resolve(null),
                        "request"));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("contradictoryResolutionVectors")
    void publicResolutionConstructorsRejectContradictoryDirectionEvidence(
            String scenario,
            ThrowingCallable construction,
            String expectedMessage) {
        assertThatThrownBy(construction)
                .as(scenario)
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining(expectedMessage);
    }

    private static Stream<Arguments> contradictoryResolutionVectors() {
        return Stream.of(
                invalid(
                        "neutral cannot be directly constructed as bullish",
                        () -> new Directional(
                                context(CallDirection.NEUTRAL),
                                DirectionalSide.BULLISH),
                        "neutral direction"),
                invalid(
                        "neutral cannot be directly constructed as bearish",
                        () -> new Directional(
                                context(CallDirection.NEUTRAL),
                                DirectionalSide.BEARISH),
                        "neutral direction"),
                invalid(
                        "strong bullish cannot be directly constructed as non-directional",
                        () -> new NonDirectional(
                                context(CallDirection.STRONG_BULLISH),
                                NonDirectionalReason.NEUTRAL_DIRECTION),
                        "only a neutral direction"),
                invalid(
                        "bullish cannot be directly constructed as non-directional",
                        () -> new NonDirectional(
                                context(CallDirection.BULLISH),
                                NonDirectionalReason.NEUTRAL_DIRECTION),
                        "only a neutral direction"),
                invalid(
                        "bearish cannot be directly constructed as non-directional",
                        () -> new NonDirectional(
                                context(CallDirection.BEARISH),
                                NonDirectionalReason.NEUTRAL_DIRECTION),
                        "only a neutral direction"),
                invalid(
                        "strong bearish cannot be directly constructed as non-directional",
                        () -> new NonDirectional(
                                context(CallDirection.STRONG_BEARISH),
                                NonDirectionalReason.NEUTRAL_DIRECTION),
                        "only a neutral direction"),
                invalid(
                        "strong bullish cannot be directly constructed as bearish",
                        () -> new Directional(
                                context(CallDirection.STRONG_BULLISH),
                                DirectionalSide.BEARISH),
                        "canonical direction polarity"),
                invalid(
                        "bullish cannot be directly constructed as bearish",
                        () -> new Directional(
                                context(CallDirection.BULLISH),
                                DirectionalSide.BEARISH),
                        "canonical direction polarity"),
                invalid(
                        "bearish cannot be directly constructed as bullish",
                        () -> new Directional(
                                context(CallDirection.BEARISH),
                                DirectionalSide.BULLISH),
                        "canonical direction polarity"),
                invalid(
                        "strong bearish cannot be directly constructed as bullish",
                        () -> new Directional(
                                context(CallDirection.STRONG_BEARISH),
                                DirectionalSide.BULLISH),
                        "canonical direction polarity"));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("invalidPublicResultVectors")
    void publicResolutionConstructorsRejectMissingOrMismatchedComponents(
            String scenario,
            ThrowingCallable construction,
            Class<? extends Throwable> expectedType,
            String expectedMessage) {
        assertThatThrownBy(construction)
                .as(scenario)
                .isInstanceOf(expectedType)
                .hasMessageContaining(expectedMessage);
    }

    private static Stream<Arguments> invalidPublicResultVectors() {
        ResolutionContext bullish = context(CallDirection.BULLISH);
        ResolutionContext neutral = context(CallDirection.NEUTRAL);
        return Stream.of(
                invalidResult(
                        "null context policy",
                        () -> new ResolutionContext(
                                null, DEFINITION_HASH, CallDirection.BULLISH),
                        NullPointerException.class,
                        "policyVersion"),
                invalidResult(
                        "null context hash",
                        () -> new ResolutionContext(
                                policy(), null, CallDirection.BULLISH),
                        NullPointerException.class,
                        "policyDefinitionHash"),
                invalidResult(
                        "wrong context hash",
                        () -> new ResolutionContext(
                                policy(), "0".repeat(64), CallDirection.BULLISH),
                        IllegalArgumentException.class,
                        "policyDefinitionHash"),
                invalidResult(
                        "null context direction",
                        () -> new ResolutionContext(policy(), DEFINITION_HASH, null),
                        NullPointerException.class,
                        "direction"),
                invalidResult(
                        "null directional context",
                        () -> new Directional(null, DirectionalSide.BULLISH),
                        NullPointerException.class,
                        "context"),
                invalidResult(
                        "null directional side",
                        () -> new Directional(bullish, null),
                        NullPointerException.class,
                        "side"),
                invalidResult(
                        "null non-directional context",
                        () -> new NonDirectional(
                                null, NonDirectionalReason.NEUTRAL_DIRECTION),
                        NullPointerException.class,
                        "context"),
                invalidResult(
                        "null non-directional reason",
                        () -> new NonDirectional(neutral, null),
                        NullPointerException.class,
                        "reason"));
    }

    @Test
    void policyRequestAndResultSurfacesRemainExactlyClosed() {
        assertThat(CallDirectionPolarityPolicyVersion.values()).containsExactly(policy());
        assertThat(CallDirection.values()).containsExactly(
                CallDirection.STRONG_BULLISH,
                CallDirection.BULLISH,
                CallDirection.NEUTRAL,
                CallDirection.BEARISH,
                CallDirection.STRONG_BEARISH);
        assertThat(DirectionalSide.values()).containsExactly(
                DirectionalSide.BULLISH,
                DirectionalSide.BEARISH);
        assertThat(NonDirectionalReason.values()).containsExactly(
                NonDirectionalReason.NEUTRAL_DIRECTION);
        assertThat(CallDirectionPolarityResolution.class.getPermittedSubclasses())
                .containsExactlyInAnyOrder(Directional.class, NonDirectional.class);
        assertRecordComponents(CallDirectionPolarityRequest.class,
                "policyVersion:CallDirectionPolarityPolicyVersion",
                "direction:CallDirection");
        assertRecordComponents(ResolutionContext.class,
                "policyVersion:CallDirectionPolarityPolicyVersion",
                "policyDefinitionHash:String",
                "direction:CallDirection");
        assertRecordComponents(Directional.class,
                "context:ResolutionContext",
                "side:DirectionalSide");
        assertRecordComponents(NonDirectional.class,
                "context:ResolutionContext",
                "reason:NonDirectionalReason");
        assertThat(Arrays.stream(NonDirectional.class.getRecordComponents()))
                .extracting(component -> component.getType().getSimpleName())
                .doesNotContain("boolean", "Boolean");
    }

    private static CallDirectionPolarityPolicyVersion policy() {
        return CallDirectionPolarityPolicyVersion
                .COLLAPSE_STRONG_DIRECTIONS_NEUTRAL_NON_DIRECTIONAL_V1;
    }

    private static CallDirectionPolarityRequest request(CallDirection direction) {
        return new CallDirectionPolarityRequest(policy(), direction);
    }

    private static ResolutionContext context(CallDirection direction) {
        return new ResolutionContext(policy(), DEFINITION_HASH, direction);
    }

    private static ResolutionContext contextOf(CallDirectionPolarityResolution result) {
        return switch (result) {
            case Directional directional -> directional.context();
            case NonDirectional nonDirectional -> nonDirectional.context();
        };
    }

    private static void assertRecordComponents(Class<?> recordType, String... expected) {
        assertThat(Arrays.stream(recordType.getRecordComponents()))
                .extracting(component -> component.getName()
                        + ":" + component.getType().getSimpleName())
                .containsExactly(expected);
    }

    private static Arguments invalid(
            String scenario,
            ThrowingCallable construction,
            String expectedMessage) {
        return Arguments.of(scenario, construction, expectedMessage);
    }

    private static Arguments invalidResult(
            String scenario,
            ThrowingCallable construction,
            Class<? extends Throwable> expectedType,
            String expectedMessage) {
        return Arguments.of(scenario, construction, expectedType, expectedMessage);
    }
}
