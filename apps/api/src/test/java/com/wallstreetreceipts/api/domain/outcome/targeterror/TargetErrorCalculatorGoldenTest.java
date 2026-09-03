package com.wallstreetreceipts.api.domain.outcome.targeterror;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.Currency;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;

import com.wallstreetreceipts.api.domain.outcome.OutcomeHorizon;
import com.wallstreetreceipts.api.domain.outcome.horizon.OutcomeBasis;
import com.wallstreetreceipts.api.domain.outcome.horizon.OutcomeBasis.Correction;
import com.wallstreetreceipts.api.domain.outcome.horizon.OutcomeBasis.Original;
import com.wallstreetreceipts.api.domain.outcome.horizon.SessionCloseHorizonPolicyVersion;
import com.wallstreetreceipts.api.domain.outcome.horizon.SessionCloseHorizonResolution.ResolvedSessionWindow;
import com.wallstreetreceipts.api.domain.outcome.horizon.TradingSession;
import com.wallstreetreceipts.api.domain.outcome.observation.CatalogPointInTimeEvidence;
import com.wallstreetreceipts.api.domain.outcome.observation.CorporateActionContinuity;
import com.wallstreetreceipts.api.domain.outcome.observation.EndpointPriceAdjustmentBasis;
import com.wallstreetreceipts.api.domain.outcome.observation.EndpointPriceBinding;
import com.wallstreetreceipts.api.domain.outcome.observation.EndpointPriceField;
import com.wallstreetreceipts.api.domain.outcome.observation.EndpointPriceObservation;
import com.wallstreetreceipts.api.domain.outcome.observation.EndpointPricePolicyVersion;
import com.wallstreetreceipts.api.domain.outcome.observation.EndpointPriceRequest;
import com.wallstreetreceipts.api.domain.outcome.observation.EndpointPriceResolution;
import com.wallstreetreceipts.api.domain.outcome.observation.EndpointPriceSelector;
import com.wallstreetreceipts.api.domain.outcome.targeterror.TargetErrorResult.Available;
import com.wallstreetreceipts.api.domain.outcome.targeterror.TargetErrorResult.CalculationContext;
import com.wallstreetreceipts.api.domain.outcome.targeterror.TargetErrorResult.Unavailable;
import com.wallstreetreceipts.api.domain.outcome.targeterror.TargetErrorResult.UnavailableReason;

class TargetErrorCalculatorGoldenTest {

    private static final String CALENDAR_ID = "calendar-primary-us-equity";
    private static final String CATALOG_REVISION = "calendar-revision-7";
    private static final String ASSET_ID = "asset-nvda";
    private static final String VENUE_ID = "venue-xnas";
    private static final Currency USD = Currency.getInstance("USD");
    private static final Instant BASIS_TIME = Instant.parse("2026-08-20T14:00:00Z");
    private static final Instant ENDPOINT_OPEN = Instant.parse("2026-08-21T13:30:00Z");
    private static final Instant ENDPOINT_CLOSE = Instant.parse("2026-08-21T20:00:00Z");
    private static final Instant AS_OF = Instant.parse("2026-08-21T20:01:00Z");
    private static final EndpointPriceAdjustmentBasis REQUIRED_ADJUSTMENT =
            EndpointPriceAdjustmentBasis
                    .SPLIT_AND_REVERSE_SPLIT_ADJUSTED_TO_ENDPOINT_SHARE_BASIS_DIVIDEND_UNADJUSTED;
    private static final String ENDPOINT_HASH =
            "37e37aba9302d77366cef4129f77a82b7ccb2f1937bfffc0315ea8d0bc6b1f76";
    private static final String TARGET_ERROR_HASH =
            "31ca30555549f670e3c22d98ead16f7a02bfad198f36532effaf4a4b6931d074";
    private static final String HORIZON_HASH =
            "550087efe7ddf2ba31974c89c2740ab79df986eefef48919c32c56a3232f8dc1";

    @Test
    void canonicalPolicyDefinitionHasStableExactUtf8BytesAndIndependentSha256()
            throws NoSuchAlgorithmException {
        TargetErrorPolicyVersion policy = policy();
        byte[] firstRead = policy.canonicalDefinitionUtf8();
        String independentHash = HexFormat.of().formatHex(
                MessageDigest.getInstance("SHA-256").digest(firstRead));

        assertThat(firstRead).hasSize(1942);
        assertThat(independentHash).isEqualTo(TARGET_ERROR_HASH);
        assertThat(policy.definitionHash()).isEqualTo(TARGET_ERROR_HASH);
        assertThat(policy.canonicalDefinition())
                .contains("\"requiredEndpointPolicyDefinitionHash\":\""
                        + ENDPOINT_HASH + "\"")
                .contains("\"formula\":\"abs(target-actual)/actual\"")
                .contains("\"denominator\":\"ACTUAL_ENDPOINT_PRICE\"")
                .contains("\"roundingMode\":\"HALF_EVEN\"")
                .contains("\"divisionCount\":1")
                .contains("TARGET_AND_ENDPOINT_PRICE_UNAVAILABLE_WITH_EXACT_ENDPOINT_REASON")
                .contains("\"futureTargetRule\":\"IDENTICAL_TO_NULL_AND_INVISIBLE_TO_OUTPUT\"");

        firstRead[0] = (byte) '!';
        assertThat(policy.canonicalDefinitionUtf8()[0]).isEqualTo((byte) '{');
    }

    @ParameterizedTest(name = "target {0}, actual {1}, error {2}")
    @MethodSource("formulaVectors")
    void calculatesAbsoluteTargetErrorWithActualDenominatorAndScaleTwelveHalfEven(
            String target,
            String actual,
            String expected) {
        EndpointPriceResolution endpoint = resolvedEndpoint(actual);
        TargetErrorInput input = input(endpoint, targetEvidence(
                endpointContext(endpoint).horizonResolution().window().context().basis(),
                target));

        Available result = (Available) TargetErrorCalculator.calculate(input);

        assertThat(result.targetError()).isEqualByComparingTo(new BigDecimal(expected));
        assertThat(result.targetError().scale()).isEqualTo(12);
        assertThat(result.context()).isEqualTo(context(endpoint));
    }

    static Stream<Arguments> formulaVectors() {
        return Stream.of(
                Arguments.of("110", "100", "0.100000000000"),
                Arguments.of("90", "100", "0.100000000000"),
                Arguments.of("120", "100", "0.200000000000"),
                Arguments.of("100", "100", "0.000000000000"),
                Arguments.of("2", "3", "0.333333333333"),
                Arguments.of("2.000000000001", "2", "0.000000000000"),
                Arguments.of("2.000000000003", "2", "0.000000000002"),
                Arguments.of(
                        "99999999999999999999999999.999999999999",
                        "1",
                        "99999999999999999999999998.999999999999"));
    }

    @Test
    void futureTargetEvidenceIsIdenticalToNullAndNeverEchoed() {
        EndpointPriceResolution endpoint = resolvedEndpoint("100");
        OutcomeBasis basis = endpointContext(endpoint)
                .horizonResolution().window().context().basis();
        TargetPriceEvidence futureWrong = new TargetPriceEvidence(
                "target-future", basis, "wrong-asset", "wrong-venue",
                Currency.getInstance("EUR"),
                EndpointPriceAdjustmentBasis.DIVIDEND_OR_TOTAL_RETURN_ADJUSTED,
                new BigDecimal("120"), AS_OF.plusNanos(1_000),
                AS_OF.plusNanos(2_000), "provenance-future-target");

        TargetErrorResult nullResult = TargetErrorCalculator.calculate(input(endpoint, null));
        TargetErrorResult futureResult = TargetErrorCalculator.calculate(
                input(endpoint, futureWrong));

        assertThat(futureResult).isEqualTo(nullResult);
        assertUnavailable(
                futureResult, UnavailableReason.TARGET_MISSING_AS_OF, null);

        TargetPriceEvidence capturedOnlyFuture = new TargetPriceEvidence(
                "target-captured-future", basis, ASSET_ID, VENUE_ID, USD,
                REQUIRED_ADJUSTMENT, new BigDecimal("120"), AS_OF,
                AS_OF.plusNanos(1_000), "provenance-captured-future-target");
        assertThat(TargetErrorCalculator.calculate(input(endpoint, capturedOnlyFuture)))
                .isEqualTo(nullResult);

        TargetPriceEvidence knownAtEquality = new TargetPriceEvidence(
                "target-at-as-of", basis, ASSET_ID, VENUE_ID, USD,
                REQUIRED_ADJUSTMENT, new BigDecimal("120"), AS_OF, AS_OF,
                "provenance-target-at-as-of");
        assertThat(TargetErrorCalculator.calculate(input(endpoint, knownAtEquality)))
                .isInstanceOf(Available.class);

        EndpointPriceResolution unavailableEndpoint = unavailableEndpoint(
                EndpointPriceResolution.UnavailableReason.OBSERVATION_MISSING_AS_OF);
        TargetErrorResult nullCombined = TargetErrorCalculator.calculate(
                input(unavailableEndpoint, null));
        TargetErrorResult futureCombined = TargetErrorCalculator.calculate(
                input(unavailableEndpoint, futureWrong));
        assertThat(futureCombined).isEqualTo(nullCombined);
        assertRecordComponents(CalculationContext.class,
                "policyVersion:TargetErrorPolicyVersion",
                "policyDefinitionHash:String",
                "endpointPriceResolution:EndpointPriceResolution");
    }

    @Test
    void preservesTheExactMissingAndEndpointUnavailableTruthTable() {
        EndpointPriceResolution unavailableEndpoint = unavailableEndpoint(
                EndpointPriceResolution.UnavailableReason.OBSERVATION_MISSING_AS_OF);
        OutcomeBasis basis = endpointContext(unavailableEndpoint)
                .horizonResolution().window().context().basis();
        TargetPriceEvidence knownTarget = targetEvidence(basis, "120");

        assertUnavailable(
                TargetErrorCalculator.calculate(input(unavailableEndpoint, null)),
                UnavailableReason.TARGET_AND_ENDPOINT_PRICE_UNAVAILABLE,
                EndpointPriceResolution.UnavailableReason.OBSERVATION_MISSING_AS_OF);
        assertUnavailable(
                TargetErrorCalculator.calculate(input(unavailableEndpoint, knownTarget)),
                UnavailableReason.ENDPOINT_PRICE_UNAVAILABLE,
                EndpointPriceResolution.UnavailableReason.OBSERVATION_MISSING_AS_OF);
        assertUnavailable(
                TargetErrorCalculator.calculate(input(resolvedEndpoint("100"), null)),
                UnavailableReason.TARGET_MISSING_AS_OF,
                null);
    }

    @ParameterizedTest(name = "propagates endpoint reason {0}")
    @EnumSource(EndpointPriceResolution.UnavailableReason.class)
    void propagatesEveryExactEndpointReasonForEndpointOnlyAndCombinedMissing(
            EndpointPriceResolution.UnavailableReason endpointReason) {
        EndpointPriceResolution unavailableEndpoint = unavailableEndpoint(endpointReason);
        OutcomeBasis basis = endpointContext(unavailableEndpoint)
                .horizonResolution().window().context().basis();

        assertUnavailable(
                TargetErrorCalculator.calculate(
                        input(unavailableEndpoint, targetEvidence(basis, "120"))),
                UnavailableReason.ENDPOINT_PRICE_UNAVAILABLE,
                endpointReason);
        assertUnavailable(
                TargetErrorCalculator.calculate(input(unavailableEndpoint, null)),
                UnavailableReason.TARGET_AND_ENDPOINT_PRICE_UNAVAILABLE,
                endpointReason);
    }

    @ParameterizedTest(name = "target evidence mismatch {0}")
    @MethodSource("targetMismatchVectors")
    void rejectsEveryKnownTargetMismatchWithoutFxOrBasisFallback(
            UnavailableReason expected,
            TargetPriceEvidence targetEvidence) {
        EndpointPriceResolution endpoint = resolvedEndpoint("100");

        assertUnavailable(
                TargetErrorCalculator.calculate(input(endpoint, targetEvidence)),
                expected,
                null);
    }

    static Stream<Arguments> targetMismatchVectors() {
        EndpointPriceResolution endpoint = resolvedEndpoint("100");
        OutcomeBasis basis = endpointContext(endpoint)
                .horizonResolution().window().context().basis();
        return Stream.of(
                Arguments.of(UnavailableReason.BASIS_MISMATCH,
                        targetEvidence(new Original("call-other", BASIS_TIME), "120")),
                Arguments.of(UnavailableReason.ASSET_MISMATCH,
                        targetEvidence(basis, "wrong-asset", VENUE_ID, USD,
                                REQUIRED_ADJUSTMENT, "120")),
                Arguments.of(UnavailableReason.PRIMARY_VENUE_MISMATCH,
                        targetEvidence(basis, ASSET_ID, "wrong-venue", USD,
                                REQUIRED_ADJUSTMENT, "120")),
                Arguments.of(UnavailableReason.CURRENCY_MISMATCH,
                        targetEvidence(basis, ASSET_ID, VENUE_ID,
                                Currency.getInstance("EUR"), REQUIRED_ADJUSTMENT, "120")),
                Arguments.of(UnavailableReason.ADJUSTMENT_BASIS_MISMATCH,
                        targetEvidence(basis, ASSET_ID, VENUE_ID, USD,
                                EndpointPriceAdjustmentBasis.UNADJUSTED_OR_OTHER, "120")));
    }

    @Test
    void knownMismatchPrecedenceStartsWithBasisThenAssetVenueCurrencyAndAdjustment() {
        EndpointPriceResolution endpoint = resolvedEndpoint("100");
        TargetPriceEvidence allWrong = targetEvidence(
                new Original("call-other", BASIS_TIME),
                "wrong-asset", "wrong-venue", Currency.getInstance("EUR"),
                EndpointPriceAdjustmentBasis.UNADJUSTED_OR_OTHER, "120");

        assertUnavailable(
                TargetErrorCalculator.calculate(input(endpoint, allWrong)),
                UnavailableReason.BASIS_MISMATCH,
                null);

        OutcomeBasis matchingBasis = endpointContext(endpoint)
                .horizonResolution().window().context().basis();
        assertUnavailable(
                TargetErrorCalculator.calculate(input(endpoint, targetEvidence(
                        matchingBasis, "wrong-asset", "wrong-venue",
                        Currency.getInstance("EUR"),
                        EndpointPriceAdjustmentBasis.UNADJUSTED_OR_OTHER, "120"))),
                UnavailableReason.ASSET_MISMATCH,
                null);
        assertUnavailable(
                TargetErrorCalculator.calculate(input(endpoint, targetEvidence(
                        matchingBasis, ASSET_ID, "wrong-venue",
                        Currency.getInstance("EUR"),
                        EndpointPriceAdjustmentBasis.UNADJUSTED_OR_OTHER, "120"))),
                UnavailableReason.PRIMARY_VENUE_MISMATCH,
                null);
        assertUnavailable(
                TargetErrorCalculator.calculate(input(endpoint, targetEvidence(
                        matchingBasis, ASSET_ID, VENUE_ID,
                        Currency.getInstance("EUR"),
                        EndpointPriceAdjustmentBasis.UNADJUSTED_OR_OTHER, "120"))),
                UnavailableReason.CURRENCY_MISMATCH,
                null);
    }

    @Test
    void outputOverflowIsUnavailableInsteadOfRoundedIntoStorage() {
        EndpointPriceResolution endpoint = resolvedEndpoint("0.000000000001");
        OutcomeBasis basis = endpointContext(endpoint)
                .horizonResolution().window().context().basis();
        TargetPriceEvidence adjacentMaximum = targetEvidence(
                basis, "100000000000000.000000000000");
        Available adjacentResult = (Available) TargetErrorCalculator.calculate(
                input(endpoint, adjacentMaximum));
        assertThat(adjacentResult.targetError()).isEqualTo(
                new BigDecimal("99999999999999999999999999.000000000000"));
        assertThat(adjacentResult.targetError().precision()).isEqualTo(38);
        assertThat(adjacentResult.targetError().scale()).isEqualTo(12);

        TargetPriceEvidence precisionThirtyNine = targetEvidence(
                basis, "100000000000000.000000000001");
        assertUnavailable(
                TargetErrorCalculator.calculate(input(endpoint, precisionThirtyNine)),
                UnavailableReason.OUTPUT_NOT_REPRESENTABLE,
                null);

        TargetPriceEvidence maximumTarget = targetEvidence(
                basis, "99999999999999999999999999.999999999999");

        assertUnavailable(
                TargetErrorCalculator.calculate(input(endpoint, maximumTarget)),
                UnavailableReason.OUTPUT_NOT_REPRESENTABLE,
                null);
    }

    @Test
    void targetEvidenceCannotPredateEitherOriginalOrCorrectionBasis() {
        Original original = new Original("call-original", BASIS_TIME);
        Correction correction = new Correction(
                "call-correction", "revision-1", BASIS_TIME.plusSeconds(60));

        assertThatThrownBy(() -> targetEvidenceAt(
                original, BASIS_TIME.minusNanos(1_000), BASIS_TIME, "120"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("basis.eventTime");
        assertThatThrownBy(() -> targetEvidenceAt(
                correction, correction.eventTime().minusNanos(1_000),
                correction.eventTime(), "120"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("basis.eventTime");
        assertThat(targetEvidenceAt(
                original, BASIS_TIME, BASIS_TIME, "120").basis()).isSameAs(original);
        assertThat(targetEvidenceAt(
                correction, correction.eventTime(), correction.eventTime(), "120")
                .basis()).isSameAs(correction);
    }

    @Test
    void calculationRequiresTheCompleteCorrectionBasisIdentity() {
        Correction exactBasis = new Correction(
                "call-correction", "revision-7", BASIS_TIME.plusSeconds(60));
        EndpointPriceResolution endpoint = resolvedEndpoint("100", exactBasis);

        assertThat(TargetErrorCalculator.calculate(
                input(endpoint, targetEvidence(exactBasis, "120"))))
                .isInstanceOf(Available.class);

        Correction differentRevision = new Correction(
                exactBasis.callId(), "revision-8", exactBasis.eventTime());
        assertUnavailable(
                TargetErrorCalculator.calculate(
                        input(endpoint, targetEvidence(differentRevision, "120"))),
                UnavailableReason.BASIS_MISMATCH,
                null);

        Correction differentEventTime = new Correction(
                exactBasis.callId(), exactBasis.basisRevisionId(),
                exactBasis.eventTime().plusSeconds(1));
        assertUnavailable(
                TargetErrorCalculator.calculate(
                        input(endpoint, targetEvidence(differentEventTime, "120"))),
                UnavailableReason.BASIS_MISMATCH,
                null);
    }

    @Test
    void scaleEquivalentTargetAndActualInputsProduceTheSameCanonicalOutput() {
        EndpointPriceResolution scaleZeroEndpoint = resolvedEndpoint("100");
        EndpointPriceResolution scaleTwelveEndpoint =
                resolvedEndpoint("100.000000000000");
        OutcomeBasis scaleZeroBasis = endpointContext(scaleZeroEndpoint)
                .horizonResolution().window().context().basis();
        OutcomeBasis scaleTwelveBasis = endpointContext(scaleTwelveEndpoint)
                .horizonResolution().window().context().basis();

        Available scaleZero = (Available) TargetErrorCalculator.calculate(
                input(scaleZeroEndpoint, targetEvidence(scaleZeroBasis, "120")));
        Available scaleTwelve = (Available) TargetErrorCalculator.calculate(
                input(scaleTwelveEndpoint,
                        targetEvidence(scaleTwelveBasis, "120.000000000000")));

        assertThat(scaleZero.targetError()).isEqualTo(scaleTwelve.targetError());
        assertThat(scaleZero.targetError()).isEqualTo(new BigDecimal("0.200000000000"));
        assertThat(scaleZero.targetError().scale()).isEqualTo(12);
    }

    @Test
    void rejectsMalformedTargetsAndContradictoryDirectResults() {
        OutcomeBasis basis = horizonResolution().window().context().basis();
        assertThatThrownBy(() -> targetEvidence(basis, "0"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("positive");
        assertThatThrownBy(() -> targetEvidence(basis, "-1"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("positive");
        assertThatThrownBy(() -> targetEvidence(basis, "1.0000000000001"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("scale");
        assertThatThrownBy(() -> targetEvidence(
                basis, "100000000000000000000000000.000000000000"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("precision");
        assertThatThrownBy(() -> TargetErrorCalculator.calculate(null))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("input");

        EndpointPriceResolution resolved = resolvedEndpoint("100");
        CalculationContext context = context(resolved);
        assertThatThrownBy(() -> new CalculationContext(
                policy(), "0".repeat(64), resolved))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("policyDefinitionHash");
        assertThatThrownBy(() -> new Available(context, new BigDecimal("0.1")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("scale-12");
        assertThatThrownBy(() -> new Available(
                context, new BigDecimal("-0.000000000001")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("nonnegative");
        assertThatThrownBy(() -> new Available(
                context,
                new BigDecimal("100000000000000000000000000.000000000000")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("NUMERIC(38,12)");
        assertThatThrownBy(() -> new Unavailable(
                context, UnavailableReason.ENDPOINT_PRICE_UNAVAILABLE, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("endpointReason");
        assertThatThrownBy(() -> new Unavailable(
                context, UnavailableReason.TARGET_MISSING_AS_OF,
                EndpointPriceResolution.UnavailableReason.OBSERVATION_MISSING_AS_OF))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("endpointReason");

        EndpointPriceResolution unavailableEndpoint = unavailableEndpoint(
                EndpointPriceResolution.UnavailableReason.OBSERVATION_MISSING_AS_OF);
        CalculationContext unavailableContext = context(unavailableEndpoint);
        assertThatThrownBy(() -> new Available(
                unavailableContext, new BigDecimal("0.000000000000")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("resolved endpoint price");
        assertThatThrownBy(() -> new Unavailable(
                unavailableContext,
                UnavailableReason.ENDPOINT_PRICE_UNAVAILABLE,
                EndpointPriceResolution.UnavailableReason.ASSET_MISMATCH))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("must match endpointPriceResolution");
    }

    @Test
    void targetConstructorsRejectRepresentativeNullBlankAndTimeOrderMutations() {
        OutcomeBasis basis = horizonResolution().window().context().basis();
        for (String component : List.of(
                "targetEvidenceId", "assetId", "primaryVenueId", "provenanceId")) {
            for (String invalid : java.util.Arrays.asList(
                    null, " ", " untrimmed ")) {
                assertThatThrownBy(() -> targetWithTextMutation(
                        component, invalid, basis))
                        .isInstanceOfAny(
                                NullPointerException.class,
                                IllegalArgumentException.class);
            }
        }

        EndpointPriceResolution resolved = resolvedEndpoint("100");
        List<Runnable> invalidConstructors = List.of(
                () -> targetWithNullableAndTimes(
                        null, USD, REQUIRED_ADJUSTMENT, new BigDecimal("120"),
                        basis.eventTime(), basis.eventTime()),
                () -> targetWithNullableAndTimes(
                        basis, null, REQUIRED_ADJUSTMENT, new BigDecimal("120"),
                        basis.eventTime(), basis.eventTime()),
                () -> targetWithNullableAndTimes(
                        basis, USD, null, new BigDecimal("120"),
                        basis.eventTime(), basis.eventTime()),
                () -> targetWithNullableAndTimes(
                        basis, USD, REQUIRED_ADJUSTMENT, null,
                        basis.eventTime(), basis.eventTime()),
                () -> targetWithNullableAndTimes(
                        basis, USD, REQUIRED_ADJUSTMENT, new BigDecimal("120"),
                        null, basis.eventTime()),
                () -> targetWithNullableAndTimes(
                        basis, USD, REQUIRED_ADJUSTMENT, new BigDecimal("120"),
                        basis.eventTime(), null),
                () -> targetWithNullableAndTimes(
                        basis, USD, REQUIRED_ADJUSTMENT, new BigDecimal("120"),
                        basis.eventTime().plusNanos(1),
                        basis.eventTime().plusSeconds(1)),
                () -> targetWithNullableAndTimes(
                        basis, USD, REQUIRED_ADJUSTMENT, new BigDecimal("120"),
                        basis.eventTime(), basis.eventTime().plusNanos(1)),
                () -> targetWithNullableAndTimes(
                        basis, USD, REQUIRED_ADJUSTMENT, new BigDecimal("120"),
                        basis.eventTime().minusNanos(1_000), basis.eventTime()),
                () -> targetWithNullableAndTimes(
                        basis, USD, REQUIRED_ADJUSTMENT, new BigDecimal("120"),
                        basis.eventTime().plusNanos(1_000), basis.eventTime()),
                () -> new TargetErrorInput(
                        null, resolved, targetEvidence(basis, "120")),
                () -> new TargetErrorInput(
                        policy(), null, targetEvidence(basis, "120")),
                () -> new CalculationContext(
                        null, TARGET_ERROR_HASH, resolved),
                () -> new CalculationContext(
                        policy(), null, resolved),
                () -> new CalculationContext(
                        policy(), TARGET_ERROR_HASH, null),
                () -> new Available(null, new BigDecimal("0.000000000000")),
                () -> new Available(context(resolved), null),
                () -> new Unavailable(
                        null, UnavailableReason.TARGET_MISSING_AS_OF, null),
                () -> new Unavailable(context(resolved), null, null));

        assertThat(invalidConstructors).hasSize(19);
        for (Runnable invalidConstructor : invalidConstructors) {
            assertThatThrownBy(invalidConstructor::run)
                    .isInstanceOfAny(
                            NullPointerException.class,
                            IllegalArgumentException.class);
        }
    }

    @Test
    void resultPolicyAndEvidenceSurfacesStayClosedAndReplayIgnoresJvmDefaults() {
        assertThat(TargetErrorPolicyVersion.values()).containsExactly(policy());
        assertThat(TargetErrorResult.class.getPermittedSubclasses())
                .containsExactlyInAnyOrder(Available.class, Unavailable.class);
        assertThat(UnavailableReason.values()).containsExactly(
                UnavailableReason.TARGET_MISSING_AS_OF,
                UnavailableReason.ENDPOINT_PRICE_UNAVAILABLE,
                UnavailableReason.TARGET_AND_ENDPOINT_PRICE_UNAVAILABLE,
                UnavailableReason.BASIS_MISMATCH,
                UnavailableReason.ASSET_MISMATCH,
                UnavailableReason.PRIMARY_VENUE_MISMATCH,
                UnavailableReason.CURRENCY_MISMATCH,
                UnavailableReason.ADJUSTMENT_BASIS_MISMATCH,
                UnavailableReason.OUTPUT_NOT_REPRESENTABLE);
        assertRecordComponents(TargetErrorInput.class,
                "policyVersion:TargetErrorPolicyVersion",
                "endpointPriceResolution:EndpointPriceResolution",
                "targetEvidence:TargetPriceEvidence");
        assertRecordComponents(TargetPriceEvidence.class,
                "targetEvidenceId:String", "basis:OutcomeBasis", "assetId:String",
                "primaryVenueId:String", "currency:Currency",
                "adjustmentBasis:EndpointPriceAdjustmentBasis", "target:BigDecimal",
                "availableAt:Instant", "capturedAt:Instant", "provenanceId:String");
        assertRecordComponents(Available.class,
                "context:CalculationContext", "targetError:BigDecimal");
        assertRecordComponents(Unavailable.class,
                "context:CalculationContext", "reason:UnavailableReason",
                "endpointReason:UnavailableReason");

        EndpointPriceResolution endpoint = resolvedEndpoint("100");
        TargetErrorInput input = input(endpoint, targetEvidence(
                endpointContext(endpoint).horizonResolution().window().context().basis(),
                "120"));
        Locale originalLocale = Locale.getDefault();
        TimeZone originalTimeZone = TimeZone.getDefault();
        try {
            TargetErrorResult expected = TargetErrorCalculator.calculate(input);
            Locale.setDefault(Locale.JAPAN);
            TimeZone.setDefault(TimeZone.getTimeZone("America/New_York"));
            assertThat(TargetErrorCalculator.calculate(input)).isEqualTo(expected);
        } finally {
            Locale.setDefault(originalLocale);
            TimeZone.setDefault(originalTimeZone);
        }
    }

    private static TargetErrorPolicyVersion policy() {
        return TargetErrorPolicyVersion.ACTUAL_DENOMINATOR_SCALE_12_HALF_EVEN_V1;
    }

    private static TargetErrorInput input(
            EndpointPriceResolution endpoint,
            TargetPriceEvidence targetEvidence) {
        return new TargetErrorInput(policy(), endpoint, targetEvidence);
    }

    private static CalculationContext context(EndpointPriceResolution endpoint) {
        return new CalculationContext(policy(), TARGET_ERROR_HASH, endpoint);
    }

    private static EndpointPriceResolution resolvedEndpoint(String actual) {
        return resolvedEndpoint(actual, new Original("call-endpoint", BASIS_TIME));
    }

    private static EndpointPriceResolution resolvedEndpoint(
            String actual,
            OutcomeBasis basis) {
        EndpointPriceObservation observation = observation(actual);
        EndpointPriceRequest request = new EndpointPriceRequest(
                EndpointPricePolicyVersion.OFFICIAL_PRIMARY_VENUE_CLOSE_SPLIT_ADJUSTED_V1,
                horizonResolution(basis), catalogEvidence(), binding(), AS_OF,
                List.of(observation));
        return EndpointPriceSelector.select(request);
    }

    private static EndpointPriceResolution unavailableEndpoint(
            EndpointPriceResolution.UnavailableReason reason) {
        return new EndpointPriceResolution.Unavailable(endpointContext(), reason);
    }

    private static EndpointPriceResolution.ResolutionContext endpointContext() {
        return new EndpointPriceResolution.ResolutionContext(
                EndpointPricePolicyVersion.OFFICIAL_PRIMARY_VENUE_CLOSE_SPLIT_ADJUSTED_V1,
                ENDPOINT_HASH,
                horizonResolution(),
                catalogEvidence(),
                binding(),
                AS_OF);
    }

    private static EndpointPriceResolution.ResolutionContext endpointContext(
            EndpointPriceResolution resolution) {
        return switch (resolution) {
            case EndpointPriceResolution.Resolved resolved -> resolved.context();
            case EndpointPriceResolution.Unavailable unavailable -> unavailable.context();
        };
    }

    private static com.wallstreetreceipts.api.domain.outcome.horizon
            .SessionCloseHorizonResolution.Resolved horizonResolution() {
        return horizonResolution(new Original("call-endpoint", BASIS_TIME));
    }

    private static com.wallstreetreceipts.api.domain.outcome.horizon
            .SessionCloseHorizonResolution.Resolved horizonResolution(OutcomeBasis basis) {
        TradingSession endpoint = new TradingSession(
                "session-endpoint", ENDPOINT_OPEN, ENDPOINT_CLOSE);
        var horizonContext = new com.wallstreetreceipts.api.domain.outcome.horizon
                .SessionCloseHorizonResolution.ResolutionContext(
                        SessionCloseHorizonPolicyVersion
                                .STRICTLY_AFTER_BASIS_EVENT_SESSION_CLOSE_V1,
                        HORIZON_HASH,
                        basis,
                        OutcomeHorizon.D1,
                        1,
                        CALENDAR_ID,
                        CATALOG_REVISION);
        return new com.wallstreetreceipts.api.domain.outcome.horizon
                .SessionCloseHorizonResolution.Resolved(
                        new ResolvedSessionWindow(
                                horizonContext, List.of(endpoint), endpoint));
    }

    private static CatalogPointInTimeEvidence catalogEvidence() {
        return new CatalogPointInTimeEvidence(
                CALENDAR_ID, CATALOG_REVISION,
                "source-calendar", "calendar-source-revision-1",
                BASIS_TIME, BASIS_TIME, "provenance-calendar");
    }

    private static EndpointPriceBinding binding() {
        return new EndpointPriceBinding(
                "binding-nvda-xnas", "binding-revision-1", ASSET_ID, VENUE_ID, USD,
                "source-official-close", "source-revision-3",
                BASIS_TIME, BASIS_TIME, "provenance-binding");
    }

    private static EndpointPriceObservation observation(String actual) {
        return new EndpointPriceObservation(
                "observation-endpoint", "provider-event-endpoint",
                ASSET_ID, VENUE_ID, USD,
                "source-official-close", "source-revision-3",
                "provenance-observation", CALENDAR_ID, CATALOG_REVISION,
                "session-endpoint", EndpointPriceField.OFFICIAL_REGULAR_SESSION_CLOSE,
                REQUIRED_ADJUSTMENT,
                CorporateActionContinuity.SPLIT_REVERSE_SPLIT_CONTINUOUS,
                ENDPOINT_CLOSE, ENDPOINT_CLOSE, ENDPOINT_CLOSE,
                new BigDecimal(actual));
    }

    private static TargetPriceEvidence targetEvidence(OutcomeBasis basis, String target) {
        return targetEvidence(
                basis, ASSET_ID, VENUE_ID, USD, REQUIRED_ADJUSTMENT, target);
    }

    private static TargetPriceEvidence targetEvidence(
            OutcomeBasis basis,
            String assetId,
            String venueId,
            Currency currency,
            EndpointPriceAdjustmentBasis adjustmentBasis,
            String target) {
        return targetEvidenceAt(
                basis, basis.eventTime(), basis.eventTime(), assetId, venueId,
                currency, adjustmentBasis, target);
    }

    private static TargetPriceEvidence targetEvidenceAt(
            OutcomeBasis basis,
            Instant availableAt,
            Instant capturedAt,
            String target) {
        return targetEvidenceAt(
                basis, availableAt, capturedAt, ASSET_ID, VENUE_ID, USD,
                REQUIRED_ADJUSTMENT, target);
    }

    private static TargetPriceEvidence targetEvidenceAt(
            OutcomeBasis basis,
            Instant availableAt,
            Instant capturedAt,
            String assetId,
            String venueId,
            Currency currency,
            EndpointPriceAdjustmentBasis adjustmentBasis,
            String target) {
        return new TargetPriceEvidence(
                "target-evidence", basis, assetId, venueId, currency,
                adjustmentBasis, new BigDecimal(target), availableAt, capturedAt,
                "provenance-target");
    }

    private static TargetPriceEvidence targetWithTextMutation(
            String component,
            String value,
            OutcomeBasis basis,
            Instant availableAt,
            Instant capturedAt) {
        return new TargetPriceEvidence(
                "targetEvidenceId".equals(component) ? value : "target-evidence",
                basis,
                "assetId".equals(component) ? value : ASSET_ID,
                "primaryVenueId".equals(component) ? value : VENUE_ID,
                USD,
                REQUIRED_ADJUSTMENT, new BigDecimal("120"), availableAt, capturedAt,
                "provenanceId".equals(component) ? value : "provenance-target");
    }

    private static TargetPriceEvidence targetWithTextMutation(
            String component,
            String value,
            OutcomeBasis basis) {
        return targetWithTextMutation(
                component, value, basis, basis.eventTime(), basis.eventTime());
    }

    private static TargetPriceEvidence targetWithNullableAndTimes(
            OutcomeBasis basis,
            Currency currency,
            EndpointPriceAdjustmentBasis adjustmentBasis,
            BigDecimal target,
            Instant availableAt,
            Instant capturedAt) {
        return new TargetPriceEvidence(
                "target-evidence", basis, ASSET_ID, VENUE_ID, currency,
                adjustmentBasis, target, availableAt, capturedAt,
                "provenance-target");
    }

    private static void assertUnavailable(
            TargetErrorResult result,
            UnavailableReason reason,
            EndpointPriceResolution.UnavailableReason endpointReason) {
        assertThat(result).isEqualTo(new Unavailable(
                ((Unavailable) result).context(), reason, endpointReason));
    }

    private static void assertRecordComponents(Class<?> type, String... expected) {
        assertThat(Stream.of(type.getRecordComponents())
                .map(component -> component.getName() + ":"
                        + component.getType().getSimpleName()))
                .containsExactly(expected);
    }
}
