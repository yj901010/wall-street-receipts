package com.wallstreetreceipts.api.domain.outcome.assetreturn;

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
import org.junit.jupiter.params.provider.MethodSource;

import com.wallstreetreceipts.api.domain.outcome.OutcomeHorizon;
import com.wallstreetreceipts.api.domain.outcome.assetreturn.AssetReturnResult.Available;
import com.wallstreetreceipts.api.domain.outcome.assetreturn.AssetReturnResult.CalculationContext;
import com.wallstreetreceipts.api.domain.outcome.assetreturn.AssetReturnResult.Unavailable;
import com.wallstreetreceipts.api.domain.outcome.assetreturn.AssetReturnResult.UnavailableReason;
import com.wallstreetreceipts.api.domain.outcome.horizon.OutcomeBasis;
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
import com.wallstreetreceipts.api.domain.outcome.pricepair.AssetReturnPricePairPolicyVersion;
import com.wallstreetreceipts.api.domain.outcome.pricepair.AssetReturnPricePairRequest;
import com.wallstreetreceipts.api.domain.outcome.pricepair.AssetReturnPricePairResolution;
import com.wallstreetreceipts.api.domain.outcome.pricepair.AssetReturnPricePairSelector;
import com.wallstreetreceipts.api.domain.outcome.pricepair.BasisPriceField;
import com.wallstreetreceipts.api.domain.outcome.pricepair.BasisPriceObservation;
import com.wallstreetreceipts.api.domain.outcome.pricepair.PricePairAdjustmentEvidence;

class AssetReturnCalculatorGoldenTest {

    private static final String ASSET_ID = "asset-nvda";
    private static final String VENUE_ID = "venue-xnas";
    private static final Currency USD = Currency.getInstance("USD");
    private static final String PRICE_SOURCE_ID = "source-official-price";
    private static final String PRICE_SOURCE_REVISION = "source-revision-3";
    private static final String CALENDAR_ID = "calendar-primary-us-equity";
    private static final String CATALOG_REVISION = "calendar-revision-7";
    private static final Instant BASIS_TIME = Instant.parse("2026-08-20T14:00:00Z");
    private static final Instant ENDPOINT_OPEN = Instant.parse("2026-08-21T13:30:00Z");
    private static final Instant ENDPOINT_CLOSE = Instant.parse("2026-08-21T20:00:00Z");
    private static final Instant AS_OF = Instant.parse("2026-08-21T20:01:00Z");
    private static final EndpointPriceAdjustmentBasis REQUIRED_ADJUSTMENT =
            EndpointPriceAdjustmentBasis
                    .SPLIT_AND_REVERSE_SPLIT_ADJUSTED_TO_ENDPOINT_SHARE_BASIS_DIVIDEND_UNADJUSTED;
    private static final String ASSET_RETURN_HASH =
            "e5e61c4adcd6567bfc76f73114499578f09de2254dc39a2553f3c0e2eaf03486";
    private static final String PAIR_HASH =
            "895e4bc97ebb3a92b80f2c58e2d28abb94440eeca963046ee755fa98825f4887";
    private static final String ENDPOINT_HASH =
            "37e37aba9302d77366cef4129f77a82b7ccb2f1937bfffc0315ea8d0bc6b1f76";
    private static final String HORIZON_HASH =
            "550087efe7ddf2ba31974c89c2740ab79df986eefef48919c32c56a3232f8dc1";
    private static final String MAX_NUMERIC =
            "99999999999999999999999999.999999999999";

    @Test
    void canonicalPolicyDefinitionHasStableExactUtf8BytesAndIndependentSha256()
            throws NoSuchAlgorithmException {
        AssetReturnPolicyVersion policy = policy();
        byte[] firstRead = policy.canonicalDefinitionUtf8();
        String independentHash = HexFormat.of().formatHex(
                MessageDigest.getInstance("SHA-256").digest(firstRead));

        assertThat(firstRead).hasSize(1011);
        assertThat(independentHash).isEqualTo(ASSET_RETURN_HASH);
        assertThat(policy.definitionHash()).isEqualTo(ASSET_RETURN_HASH);
        assertThat(new String(firstRead, StandardCharsets.UTF_8))
                .isEqualTo(policy.canonicalDefinition());
        assertThat(policy.canonicalDefinition())
                .contains("\"requiredPricePairPolicyDefinitionHash\":\"" + PAIR_HASH + "\"")
                .contains("\"formula\":\"(endpoint-basis)/basis\"")
                .contains("\"denominator\":\"BASIS_PRICE\"")
                .contains("\"divisionCount\":1")
                .contains("\"divisionScale\":12")
                .contains("\"roundingMode\":\"HALF_EVEN\"")
                .contains("\"outputBoundary\":\"SIGNED_NUMERIC_38_12_AT_LEAST_NEGATIVE_ONE\"")
                .contains("\"intermediateRounding\":\"ABSENT\"")
                .contains("\"fallbackBehavior\":\"ABSENT\"");

        firstRead[0] = (byte) '!';
        assertThat(policy.canonicalDefinitionUtf8()[0]).isEqualTo((byte) '{');
    }

    @ParameterizedTest(name = "asset return {0}")
    @MethodSource("primaryFormulaVectors")
    void calculatesSignedPositiveNegativeAndZeroReturnsWithBasisDenominator(
            String label,
            String basis,
            String endpoint,
            String expected) {
        Available result = calculateAvailable(basis, endpoint);

        assertThat(result.assetReturn()).isEqualTo(new BigDecimal(expected));
        assertThat(result.assetReturn().scale()).isEqualTo(12);
        assertThat(result.context().policyDefinitionHash()).isEqualTo(ASSET_RETURN_HASH);
        assertThat(result.context().pricePairResolution())
                .isInstanceOf(AssetReturnPricePairResolution.Resolved.class);
    }

    static Stream<Arguments> primaryFormulaVectors() {
        return Stream.of(
                Arguments.of("positive", "100", "120", "0.200000000000"),
                Arguments.of("negative", "120", "100", "-0.166666666667"),
                Arguments.of("zero", "100", "100", "0.000000000000"),
                Arguments.of("asymmetric-forward", "2", "3", "0.500000000000"),
                Arguments.of("asymmetric-reverse", "3", "2", "-0.333333333333"));
    }

    @Test
    void scaleEquivalentPricesProduceEqualExactScaleTwelveResults() {
        Available scaleZero = calculateAvailable("100", "120");
        Available scaleTwelve = calculateAvailable(
                "100.000000000000", "120.000000000000");

        assertThat(scaleTwelve.assetReturn()).isEqualTo(scaleZero.assetReturn());
        assertThat(scaleZero.assetReturn()).isEqualTo(new BigDecimal("0.200000000000"));
        assertThat(scaleZero.assetReturn().scale()).isEqualTo(12);
        assertThat(scaleTwelve.assetReturn().scale()).isEqualTo(12);
    }

    @ParameterizedTest(name = "half-even tie {0}")
    @MethodSource("halfEvenTieVectors")
    void roundsPositiveAndNegativeExactTiesHalfEvenWithOneDivision(
            String label,
            String endpoint,
            String expected) {
        Available result = calculateAvailable("2.000000000000", endpoint);
        assertThat(result.assetReturn()).isEqualTo(new BigDecimal(expected));
    }

    static Stream<Arguments> halfEvenTieVectors() {
        return Stream.of(
                Arguments.of("positive-odd-to-even", "2.000000000003",
                        "0.000000000002"),
                Arguments.of("negative-odd-to-even", "1.999999999997",
                        "-0.000000000002"));
    }

    @Test
    void allowsRoundedNegativeOneForPositiveInputsWhoseExactReturnExceedsNegativeOne() {
        Available result = calculateAvailable(MAX_NUMERIC, "0.000000000001");

        assertThat(result.assetReturn()).isEqualTo(new BigDecimal("-1.000000000000"));
        assertThat(result.assetReturn().scale()).isEqualTo(12);
        assertThat(new Available(result.context(), new BigDecimal("-1.000000000000")))
                .isEqualTo(result);
    }

    @Test
    void acceptsExactPrecision38OutputAndReturnsUnavailableForRoundedPrecision39() {
        Available maximumOutput = calculateAvailable("1", MAX_NUMERIC);
        assertThat(maximumOutput.assetReturn())
                .isEqualTo(new BigDecimal(
                        "99999999999999999999999998.999999999999"));
        assertThat(maximumOutput.assetReturn().precision()).isEqualTo(38);

        AssetReturnPricePairResolution pair = resolvedPair("0.1", MAX_NUMERIC);
        AssetReturnResult overflow = AssetReturnCalculator.calculate(input(pair));
        assertUnavailable(overflow, UnavailableReason.OUTPUT_NOT_REPRESENTABLE, null);
    }

    @ParameterizedTest(name = "pair unavailable {0}")
    @MethodSource("pairReasons")
    void preservesEveryExactPricePairUnavailableReason(
            AssetReturnPricePairResolution.UnavailableReason pairReason) {
        AssetReturnPricePairResolution pair = unavailablePair(pairReason);
        AssetReturnResult result = AssetReturnCalculator.calculate(input(pair));

        assertUnavailable(result, UnavailableReason.PRICE_PAIR_UNAVAILABLE, pairReason);
        assertThat(((Unavailable) result).context().pricePairResolution()).isSameAs(pair);
    }

    static Stream<AssetReturnPricePairResolution.UnavailableReason> pairReasons() {
        return Stream.of(AssetReturnPricePairResolution.UnavailableReason.values());
    }

    @Test
    void directResultConstructorsEnforceLocalPolicyPairAndReasonConsistency() {
        AssetReturnPricePairResolution resolvedPair = resolvedPair("100", "120");
        CalculationContext context = context(resolvedPair);
        assertThat(new Available(context, new BigDecimal("0.200000000000"))
                .assetReturn()).isEqualTo(new BigDecimal("0.200000000000"));

        assertThatThrownBy(() -> new CalculationContext(
                policy(), "0".repeat(64), resolvedPair))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("policyDefinitionHash");
        assertThatThrownBy(() -> new Available(context, new BigDecimal("0.2")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("scale-12");
        assertThatThrownBy(() -> new Available(
                context, new BigDecimal("-1.000000000001")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("at least -1");
        assertThatThrownBy(() -> new Available(
                context,
                new BigDecimal("100000000000000000000000000.000000000000")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("NUMERIC(38,12)");

        AssetReturnPricePairResolution unavailablePair = unavailablePair(
                AssetReturnPricePairResolution.UnavailableReason
                        .ADJUSTMENT_EVIDENCE_MISSING_AS_OF);
        CalculationContext unavailableContext = context(unavailablePair);
        assertThatThrownBy(() -> new Available(
                unavailableContext, new BigDecimal("0.000000000000")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("resolved price pair");
        assertThatThrownBy(() -> new Unavailable(
                context, UnavailableReason.PRICE_PAIR_UNAVAILABLE,
                AssetReturnPricePairResolution.UnavailableReason
                        .ADJUSTMENT_EVIDENCE_MISSING_AS_OF))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("unavailable price pair");
        assertThatThrownBy(() -> new Unavailable(
                unavailableContext, UnavailableReason.PRICE_PAIR_UNAVAILABLE,
                AssetReturnPricePairResolution.UnavailableReason.ASSET_MISMATCH))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("exact price-pair reason");
        assertThatThrownBy(() -> new Unavailable(
                context, UnavailableReason.OUTPUT_NOT_REPRESENTABLE,
                AssetReturnPricePairResolution.UnavailableReason.ASSET_MISMATCH))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("null pricePairReason");
        assertThatThrownBy(() -> new Unavailable(
                unavailableContext, UnavailableReason.OUTPUT_NOT_REPRESENTABLE, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("resolved price pair");
    }

    @Test
    void rejectsEveryNullInputContextAndResultComponent() {
        AssetReturnPricePairResolution resolvedPair = resolvedPair("100", "120");
        CalculationContext context = context(resolvedPair);
        List<Runnable> invalid = List.of(
                () -> new AssetReturnInput(null, resolvedPair),
                () -> new AssetReturnInput(policy(), null),
                () -> new CalculationContext(null, ASSET_RETURN_HASH, resolvedPair),
                () -> new CalculationContext(policy(), null, resolvedPair),
                () -> new CalculationContext(policy(), ASSET_RETURN_HASH, null),
                () -> new Available(null, new BigDecimal("0.000000000000")),
                () -> new Available(context, null),
                () -> new Unavailable(null, UnavailableReason.OUTPUT_NOT_REPRESENTABLE, null),
                () -> new Unavailable(context, null, null));

        assertThat(invalid).hasSize(9);
        for (Runnable mutation : invalid) {
            assertThatThrownBy(mutation::run)
                    .isInstanceOfAny(
                            NullPointerException.class, IllegalArgumentException.class);
        }
        assertThatThrownBy(() -> AssetReturnCalculator.calculate(null))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("input");
    }

    @Test
    void closesPublicSurfaceReasonOrderAndReplayAgainstJvmDefaults() {
        assertThat(AssetReturnPolicyVersion.values()).containsExactly(policy());
        assertThat(AssetReturnResult.class.getPermittedSubclasses())
                .containsExactlyInAnyOrder(Available.class, Unavailable.class);
        assertThat(UnavailableReason.values()).containsExactly(
                UnavailableReason.PRICE_PAIR_UNAVAILABLE,
                UnavailableReason.OUTPUT_NOT_REPRESENTABLE);
        assertRecordComponents(AssetReturnInput.class,
                "policyVersion:AssetReturnPolicyVersion",
                "pricePairResolution:AssetReturnPricePairResolution");
        assertRecordComponents(CalculationContext.class,
                "policyVersion:AssetReturnPolicyVersion",
                "policyDefinitionHash:String",
                "pricePairResolution:AssetReturnPricePairResolution");
        assertRecordComponents(Available.class,
                "context:CalculationContext", "assetReturn:BigDecimal");
        assertRecordComponents(Unavailable.class,
                "context:CalculationContext", "reason:UnavailableReason",
                "pricePairReason:UnavailableReason");

        AssetReturnInput input = input(resolvedPair("100", "120"));
        Locale originalLocale = Locale.getDefault();
        TimeZone originalTimeZone = TimeZone.getDefault();
        try {
            AssetReturnResult expected = AssetReturnCalculator.calculate(input);
            Locale.setDefault(Locale.JAPAN);
            TimeZone.setDefault(TimeZone.getTimeZone("America/New_York"));
            assertThat(AssetReturnCalculator.calculate(input)).isEqualTo(expected);
        } finally {
            Locale.setDefault(originalLocale);
            TimeZone.setDefault(originalTimeZone);
        }
    }

    private static AssetReturnPolicyVersion policy() {
        return AssetReturnPolicyVersion.SIGNED_BASIS_DENOMINATOR_SCALE_12_HALF_EVEN_V1;
    }

    private static AssetReturnInput input(AssetReturnPricePairResolution pair) {
        return new AssetReturnInput(policy(), pair);
    }

    private static CalculationContext context(AssetReturnPricePairResolution pair) {
        return new CalculationContext(policy(), ASSET_RETURN_HASH, pair);
    }

    private static Available calculateAvailable(String basis, String endpoint) {
        AssetReturnResult result = AssetReturnCalculator.calculate(
                input(resolvedPair(basis, endpoint)));
        assertThat(result).isInstanceOf(Available.class);
        return (Available) result;
    }

    private static AssetReturnPricePairResolution resolvedPair(
            String basisPrice, String endpointPrice) {
        OutcomeBasis basis = new Original("call-001", BASIS_TIME);
        EndpointPriceResolution.Resolved endpoint = resolvedEndpoint(endpointPrice, basis);
        BasisPriceObservation basisObservation = new BasisPriceObservation(
                "basis-observation", "basis-provider-event", basis,
                ASSET_ID, VENUE_ID, USD, PRICE_SOURCE_ID, PRICE_SOURCE_REVISION,
                "provenance-basis", BasisPriceField.SOURCE_RECORDED_BASIS_EVENT_PRICE,
                REQUIRED_ADJUSTMENT,
                CorporateActionContinuity.SPLIT_REVERSE_SPLIT_CONTINUOUS,
                BASIS_TIME, BASIS_TIME, BASIS_TIME, new BigDecimal(basisPrice));
        EndpointPriceObservation endpointObservation = endpoint.observation();
        PricePairAdjustmentEvidence adjustment = new PricePairAdjustmentEvidence(
                "adjustment-evidence", "adjustment-provider-event", basis,
                ASSET_ID, VENUE_ID, USD,
                "source-corporate-actions", "adjustment-revision-9",
                "provenance-adjustment",
                basisObservation.observationId(), basisObservation.providerEventId(),
                endpointObservation.observationId(), endpointObservation.providerEventId(),
                BASIS_TIME, ENDPOINT_CLOSE, REQUIRED_ADJUSTMENT,
                CorporateActionContinuity.SPLIT_REVERSE_SPLIT_CONTINUOUS,
                ENDPOINT_CLOSE, ENDPOINT_CLOSE);
        return AssetReturnPricePairSelector.select(new AssetReturnPricePairRequest(
                AssetReturnPricePairPolicyVersion
                        .SOURCE_RECORDED_BASIS_EVENT_TO_OFFICIAL_ENDPOINT_PRICE_PAIR_V1,
                endpoint, List.of(basisObservation), List.of(adjustment)));
    }

    private static AssetReturnPricePairResolution unavailablePair(
            AssetReturnPricePairResolution.UnavailableReason reason) {
        OutcomeBasis basis = new Original("call-001", BASIS_TIME);
        boolean endpointRelated = reason
                == AssetReturnPricePairResolution.UnavailableReason
                        .BASIS_AND_ENDPOINT_PRICE_UNAVAILABLE
                || reason == AssetReturnPricePairResolution.UnavailableReason
                        .ENDPOINT_PRICE_UNAVAILABLE;
        EndpointPriceResolution endpoint;
        EndpointPriceResolution.UnavailableReason endpointReason = null;
        if (endpointRelated) {
            endpointReason = EndpointPriceResolution.UnavailableReason
                    .OBSERVATION_MISSING_AS_OF;
            endpoint = new EndpointPriceResolution.Unavailable(
                    endpointContext(basis), endpointReason);
        } else {
            endpoint = resolvedEndpoint("120", basis);
        }
        var pairContext = new AssetReturnPricePairResolution.ResolutionContext(
                AssetReturnPricePairPolicyVersion
                        .SOURCE_RECORDED_BASIS_EVENT_TO_OFFICIAL_ENDPOINT_PRICE_PAIR_V1,
                PAIR_HASH, endpoint);
        return new AssetReturnPricePairResolution.Unavailable(
                pairContext, reason, endpointReason);
    }

    private static EndpointPriceResolution.Resolved resolvedEndpoint(
            String price, OutcomeBasis basis) {
        EndpointPriceObservation observation = new EndpointPriceObservation(
                "endpoint-observation", "endpoint-provider-event",
                ASSET_ID, VENUE_ID, USD, PRICE_SOURCE_ID, PRICE_SOURCE_REVISION,
                "provenance-endpoint", CALENDAR_ID, CATALOG_REVISION,
                "session-endpoint", EndpointPriceField.OFFICIAL_REGULAR_SESSION_CLOSE,
                REQUIRED_ADJUSTMENT,
                CorporateActionContinuity.SPLIT_REVERSE_SPLIT_CONTINUOUS,
                ENDPOINT_CLOSE, ENDPOINT_CLOSE, ENDPOINT_CLOSE,
                new BigDecimal(price));
        return (EndpointPriceResolution.Resolved) EndpointPriceSelector.select(
                new EndpointPriceRequest(
                        EndpointPricePolicyVersion
                                .OFFICIAL_PRIMARY_VENUE_CLOSE_SPLIT_ADJUSTED_V1,
                        horizonResolution(basis), catalogEvidence(), binding(), AS_OF,
                        List.of(observation)));
    }

    private static EndpointPriceResolution.ResolutionContext endpointContext(
            OutcomeBasis basis) {
        return new EndpointPriceResolution.ResolutionContext(
                EndpointPricePolicyVersion
                        .OFFICIAL_PRIMARY_VENUE_CLOSE_SPLIT_ADJUSTED_V1,
                ENDPOINT_HASH, horizonResolution(basis), catalogEvidence(), binding(), AS_OF);
    }

    private static com.wallstreetreceipts.api.domain.outcome.horizon
            .SessionCloseHorizonResolution.Resolved horizonResolution(OutcomeBasis basis) {
        TradingSession endpoint = new TradingSession(
                "session-endpoint", ENDPOINT_OPEN, ENDPOINT_CLOSE);
        var horizonContext = new com.wallstreetreceipts.api.domain.outcome.horizon
                .SessionCloseHorizonResolution.ResolutionContext(
                        SessionCloseHorizonPolicyVersion
                                .STRICTLY_AFTER_BASIS_EVENT_SESSION_CLOSE_V1,
                        HORIZON_HASH, basis, OutcomeHorizon.D1, 1,
                        CALENDAR_ID, CATALOG_REVISION);
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
                PRICE_SOURCE_ID, PRICE_SOURCE_REVISION,
                BASIS_TIME, BASIS_TIME, "provenance-binding");
    }

    private static void assertUnavailable(
            AssetReturnResult result,
            UnavailableReason reason,
            AssetReturnPricePairResolution.UnavailableReason pairReason) {
        assertThat(result).isInstanceOf(Unavailable.class);
        Unavailable unavailable = (Unavailable) result;
        assertThat(unavailable.reason()).isEqualTo(reason);
        assertThat(unavailable.pricePairReason()).isEqualTo(pairReason);
        assertThat(unavailable).isEqualTo(
                new Unavailable(unavailable.context(), reason, pairReason));
    }

    private static void assertRecordComponents(Class<?> type, String... expected) {
        assertThat(Stream.of(type.getRecordComponents())
                .map(component -> component.getName() + ":"
                        + component.getType().getSimpleName()))
                .containsExactly(expected);
    }
}
