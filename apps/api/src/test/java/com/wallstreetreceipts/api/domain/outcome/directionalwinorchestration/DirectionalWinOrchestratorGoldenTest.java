package com.wallstreetreceipts.api.domain.outcome.directionalwinorchestration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.Arrays;
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

import com.wallstreetreceipts.api.domain.call.CallDirection;
import com.wallstreetreceipts.api.domain.outcome.OutcomeHorizon;
import com.wallstreetreceipts.api.domain.outcome.assetreturn.AssetReturnPolicyVersion;
import com.wallstreetreceipts.api.domain.outcome.assetreturn.AssetReturnResult;
import com.wallstreetreceipts.api.domain.outcome.calculation.DirectionalWinResult;
import com.wallstreetreceipts.api.domain.outcome.direction.CallDirectionPolarityPolicyVersion;
import com.wallstreetreceipts.api.domain.outcome.direction.CallDirectionPolarityRequest;
import com.wallstreetreceipts.api.domain.outcome.direction.CallDirectionPolarityResolver;
import com.wallstreetreceipts.api.domain.outcome.horizon.OutcomeBasis;
import com.wallstreetreceipts.api.domain.outcome.horizon.SessionCloseHorizonPolicyVersion;
import com.wallstreetreceipts.api.domain.outcome.horizon.SessionCloseHorizonResolution;
import com.wallstreetreceipts.api.domain.outcome.horizon.TradingSession;
import com.wallstreetreceipts.api.domain.outcome.observation.CatalogPointInTimeEvidence;
import com.wallstreetreceipts.api.domain.outcome.observation.CorporateActionContinuity;
import com.wallstreetreceipts.api.domain.outcome.observation.EndpointPriceAdjustmentBasis;
import com.wallstreetreceipts.api.domain.outcome.observation.EndpointPriceBinding;
import com.wallstreetreceipts.api.domain.outcome.observation.EndpointPriceField;
import com.wallstreetreceipts.api.domain.outcome.observation.EndpointPriceObservation;
import com.wallstreetreceipts.api.domain.outcome.observation.EndpointPricePolicyVersion;
import com.wallstreetreceipts.api.domain.outcome.observation.EndpointPriceResolution;
import com.wallstreetreceipts.api.domain.outcome.pricepair.AssetReturnPricePairPolicyVersion;
import com.wallstreetreceipts.api.domain.outcome.pricepair.AssetReturnPricePairResolution;
import com.wallstreetreceipts.api.domain.outcome.pricepair.BasisPriceField;
import com.wallstreetreceipts.api.domain.outcome.pricepair.BasisPriceObservation;
import com.wallstreetreceipts.api.domain.outcome.pricepair.PricePairAdjustmentEvidence;
import com.wallstreetreceipts.api.domain.outcome.routing.CalculatorSideRouting;
import com.wallstreetreceipts.api.domain.outcome.routing.CalculatorSideRouting.DirectionalRoute;
import com.wallstreetreceipts.api.domain.outcome.routing.CalculatorSideRouting.NonDirectionalRoute;
import com.wallstreetreceipts.api.domain.outcome.targeteligibility.BasisForecastTermsEvidence;
import com.wallstreetreceipts.api.domain.outcome.targeteligibility.BasisForecastTermsEvidence.TargetDisposition.Absent;
import com.wallstreetreceipts.api.domain.outcome.targeteligibility.BasisForecastTermsEvidence.TargetDisposition.Present;

class DirectionalWinOrchestratorGoldenTest {

    private static final String POLICY_HASH =
            "51429c7601d4807162855f08c680d1e6bb7895f87fc108e141e5ad3a3ab25bcb";
    private static final String ASSET_RETURN_HASH =
            "e5e61c4adcd6567bfc76f73114499578f09de2254dc39a2553f3c0e2eaf03486";
    private static final String PAIR_HASH =
            "895e4bc97ebb3a92b80f2c58e2d28abb94440eeca963046ee755fa98825f4887";
    private static final String ENDPOINT_HASH =
            "37e37aba9302d77366cef4129f77a82b7ccb2f1937bfffc0315ea8d0bc6b1f76";
    private static final String HORIZON_HASH =
            "550087efe7ddf2ba31974c89c2740ab79df986eefef48919c32c56a3232f8dc1";
    private static final String ASSET_ID = "asset-nvda";
    private static final String VENUE_ID = "venue-xnas";
    private static final String PRICE_SOURCE_ID = "source-official-price";
    private static final String PRICE_SOURCE_REVISION = "source-revision-3";
    private static final String CALENDAR_ID = "calendar-primary-us-equity";
    private static final String CATALOG_REVISION = "calendar-revision-7";
    private static final Currency USD = Currency.getInstance("USD");
    private static final Instant BASIS_TIME =
            Instant.parse("2026-08-20T14:00:00Z");
    private static final Instant ENDPOINT_OPEN =
            Instant.parse("2026-08-21T13:30:00Z");
    private static final Instant ENDPOINT_CLOSE =
            Instant.parse("2026-08-21T20:00:00Z");
    private static final Instant AS_OF =
            Instant.parse("2026-08-21T20:01:00Z");
    private static final OutcomeBasis ORIGINAL =
            new OutcomeBasis.Original("call-001", BASIS_TIME);
    private static final EndpointPriceAdjustmentBasis REQUIRED_ADJUSTMENT =
            EndpointPriceAdjustmentBasis
                    .SPLIT_AND_REVERSE_SPLIT_ADJUSTED_TO_ENDPOINT_SHARE_BASIS_DIVIDEND_UNADJUSTED;

    @Test
    void canonicalDefinitionHasExactBytesHashAdrAndDefensiveReads()
            throws Exception {
        byte[] bytes = policy().canonicalDefinitionUtf8();

        assertThat(bytes).hasSize(3699);
        assertThat(HexFormat.of().formatHex(
                MessageDigest.getInstance("SHA-256").digest(bytes)))
                .isEqualTo(POLICY_HASH)
                .isEqualTo(policy().definitionHash());
        assertThat(policy().canonicalDefinition())
                .contains("ALL_FIELDS_NON_NULL_INCLUDING_NON_DIRECTIONAL")
                .contains("PRESERVE_EXACT_TYPED_ASSET_RETURN_PRICE_PAIR")
                .contains("EXACTLY_ONCE_ONLY_FOR_DIRECTIONAL_AND_ASSET_RETURN_AVAILABLE")
                .contains("\"targetDispositionUse\":\"ABSENT\"")
                .contains("\"endpointSelectorInvocation\":\"ABSENT\"")
                .endsWith("\"publication\":\"ABSENT\"}");
        assertThat(Files.readString(Path.of(
                "../../decisions/ADR-021-supplied-leaf-directional-win-orchestration.md")))
                .contains("3699-byte")
                .contains(POLICY_HASH);

        bytes[0] = (byte) '!';
        assertThat(policy().canonicalDefinitionUtf8()[0]).isEqualTo((byte) '{');
    }

    @Test
    void exactFileRecordSealedAndDisconnectedSurfacesAreStable()
            throws Exception {
        Path packagePath = Path.of(
                "src/main/java/com/wallstreetreceipts/api/domain/outcome/directionalwinorchestration");
        try (var files = Files.list(packagePath)) {
            assertThat(files.filter(path -> path.toString().endsWith(".java"))
                    .map(path -> path.getFileName().toString()).sorted().toList())
                    .containsExactly(
                            "DirectionalWinOrchestrationPolicyVersion.java",
                            "DirectionalWinOrchestrationRequest.java",
                            "DirectionalWinOrchestrationResolution.java",
                            "DirectionalWinOrchestrator.java");
        }
        assertRecordComponents(DirectionalWinOrchestrationRequest.class,
                "policyVersion:DirectionalWinOrchestrationPolicyVersion",
                "termsEvidence:BasisForecastTermsEvidence",
                "sideRouting:Result", "assetReturnResult:AssetReturnResult");
        assertRecordComponents(
                DirectionalWinOrchestrationResolution.ResolutionContext.class,
                "policyVersion:DirectionalWinOrchestrationPolicyVersion",
                "policyDefinitionHash:String");
        assertRecordComponents(
                DirectionalWinOrchestrationResolution.Available.class,
                "context:ResolutionContext",
                "termsEvidence:BasisForecastTermsEvidence",
                "sideRouting:DirectionalRoute",
                "assetReturnResult:Available",
                "directionalWinResult:Available");
        assertRecordComponents(
                DirectionalWinOrchestrationResolution.NotApplicable.class,
                "context:ResolutionContext",
                "termsEvidence:BasisForecastTermsEvidence",
                "sideRouting:NonDirectionalRoute",
                "assetReturnResult:AssetReturnResult");
        assertRecordComponents(
                DirectionalWinOrchestrationResolution.AssetReturnUnavailable.class,
                "context:ResolutionContext",
                "termsEvidence:BasisForecastTermsEvidence",
                "sideRouting:DirectionalRoute",
                "assetReturnResult:Unavailable");
        assertThat(DirectionalWinOrchestrationResolution.class.isSealed()).isTrue();
        assertThat(Arrays.stream(DirectionalWinOrchestrationResolution.class
                .getPermittedSubclasses()).map(Class::getSimpleName).toList())
                .containsExactlyInAnyOrder(
                        "Available", "NotApplicable", "AssetReturnUnavailable");
        assertThat(DirectionalWinOrchestrationPolicyVersion.values())
                .containsExactly(policy());

        String orchestrator = Files.readString(packagePath.resolve(
                "DirectionalWinOrchestrator.java"));
        assertThat(count(orchestrator, "DirectionalWinCalculator.calculate("))
                .isEqualTo(1);
        assertThat(orchestrator)
                .doesNotContain(".reason()")
                .doesNotContain("UnavailableReason")
                .doesNotContain("CallDirectionPolarityResolver")
                .doesNotContain("CalculatorSideRouting.route(")
                .doesNotContain("AssetReturnCalculator")
                .doesNotContain("AssetReturnPricePairSelector")
                .doesNotContain("EndpointPriceSelector")
                .doesNotContain("compareTo(")
                .doesNotContain("signum(")
                .doesNotContain("@Service")
                .doesNotContain("@Repository")
                .doesNotContain("@Controller");
    }

    @Test
    void nullRootsAndRequiredFieldsFailClosed() {
        var terms = terms(CallDirection.BULLISH, ORIGINAL, ASSET_ID,
                new Absent(), BASIS_TIME, BASIS_TIME);
        var route = route(CallDirection.BULLISH);
        var assetReturn = availableReturn(ORIGINAL, ASSET_ID,
                "0.100000000000");

        assertThatThrownBy(() -> DirectionalWinOrchestrator.orchestrate(null))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new DirectionalWinOrchestrationRequest(
                null, terms, route, assetReturn))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new DirectionalWinOrchestrationRequest(
                policy(), null, route, assetReturn))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new DirectionalWinOrchestrationRequest(
                policy(), terms, null, assetReturn))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new DirectionalWinOrchestrationRequest(
                policy(), terms, route, null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void exactCanonicalDirectionMustMatchNotOnlyItsPolarity() {
        var terms = terms(CallDirection.STRONG_BULLISH, ORIGINAL, ASSET_ID,
                new Absent(), BASIS_TIME, BASIS_TIME);

        assertThatThrownBy(() -> request(terms, route(CallDirection.BULLISH),
                availableReturn(ORIGINAL, ASSET_ID, "0.100000000000")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("direction");
    }

    @Test
    void wholeBasisRevisionIdentityMustMatch() {
        OutcomeBasis termsBasis = new OutcomeBasis.Correction(
                "call-001", "revision-2", BASIS_TIME);
        OutcomeBasis leafBasis = new OutcomeBasis.Correction(
                "call-001", "revision-3", BASIS_TIME);
        var terms = terms(CallDirection.BULLISH, termsBasis, ASSET_ID,
                new Absent(), BASIS_TIME, BASIS_TIME);

        assertThatThrownBy(() -> request(terms, route(CallDirection.BULLISH),
                availableReturn(leafBasis, ASSET_ID, "0.100000000000")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("basis");
    }

    @Test
    void wholeBasisCallIdentityMustMatch() {
        OutcomeBasis leafBasis = new OutcomeBasis.Original(
                "call-002", BASIS_TIME);
        var terms = defaultTerms(CallDirection.BULLISH);

        assertThatThrownBy(() -> request(terms, route(CallDirection.BULLISH),
                availableReturn(leafBasis, ASSET_ID, "0.100000000000")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("basis");
    }

    @Test
    void exactAssetIdentityMustMatch() {
        var terms = defaultTerms(CallDirection.BULLISH);

        assertThatThrownBy(() -> request(terms, route(CallDirection.BULLISH),
                availableReturn(ORIGINAL, "asset-amd", "0.100000000000")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("asset");
    }

    @Test
    void futureTermsAvailabilityIsRejectedBeforeBranching() {
        var terms = terms(CallDirection.NEUTRAL, ORIGINAL, ASSET_ID,
                new Absent(), AS_OF.plusSeconds(1), AS_OF.plusSeconds(1));

        assertThatThrownBy(() -> request(terms, route(CallDirection.NEUTRAL),
                availableReturn(ORIGINAL, ASSET_ID, "0.100000000000")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("known");
    }

    @Test
    void futureTermsCaptureIsRejectedBeforeBranching() {
        var terms = terms(CallDirection.NEUTRAL, ORIGINAL, ASSET_ID,
                new Absent(), AS_OF, AS_OF.plusSeconds(1));

        assertThatThrownBy(() -> request(terms, route(CallDirection.NEUTRAL),
                unavailableOutput(ORIGINAL, ASSET_ID)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("known");
    }

    @Test
    void visibilityEqualityAtEvaluationAsOfIsAccepted() {
        var terms = terms(CallDirection.BULLISH, ORIGINAL, ASSET_ID,
                new Absent(), AS_OF, AS_OF);
        var result = DirectionalWinOrchestrator.orchestrate(request(
                terms, route(CallDirection.BULLISH),
                availableReturn(ORIGINAL, ASSET_ID, "0.100000000000")));

        assertThat(result)
                .isInstanceOf(DirectionalWinOrchestrationResolution.Available.class);
    }

    @Test
    void equalButDistinctReplayRecordsAreAccepted() {
        var firstTerms = defaultTerms(CallDirection.BULLISH);
        var replayTerms = defaultTerms(CallDirection.BULLISH);
        var firstReturn = availableReturn(
                ORIGINAL, ASSET_ID, "0.100000000000");
        var replayReturn = availableReturn(
                ORIGINAL, ASSET_ID, "0.100000000000");

        assertThat(replayTerms).isEqualTo(firstTerms).isNotSameAs(firstTerms);
        assertThat(replayReturn).isEqualTo(firstReturn).isNotSameAs(firstReturn);
        assertThat(DirectionalWinOrchestrator.orchestrate(request(
                replayTerms, route(CallDirection.BULLISH), replayReturn)))
                .isEqualTo(DirectionalWinOrchestrator.orchestrate(request(
                        firstTerms, route(CallDirection.BULLISH), firstReturn)));
    }

    @Test
    void directResultConstructorsEnforceOnlyLocalPolicyAndTypedShape() {
        var terms = defaultTerms(CallDirection.BULLISH);
        var route = directionalRoute(CallDirection.BULLISH);
        var assetReturn = availableReturn(
                ORIGINAL, ASSET_ID, "0.100000000000");
        var context = new DirectionalWinOrchestrationResolution.ResolutionContext(
                policy(), POLICY_HASH);
        var directlyConstructed = new DirectionalWinOrchestrationResolution.Available(
                context, terms, route, assetReturn,
                new DirectionalWinResult.Available(false));

        assertThat(directlyConstructed.directionalWinResult().directionalWin())
                .isFalse();
        assertThatThrownBy(() -> new DirectionalWinOrchestrationResolution
                .ResolutionContext(policy(), "wrong-hash"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new DirectionalWinOrchestrationResolution.Available(
                context, terms, route, assetReturn, null))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new DirectionalWinOrchestrationResolution
                .AssetReturnUnavailable(context, terms, route, null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void originalAndCorrectionBasesComposeIndependently() {
        OutcomeBasis correction = new OutcomeBasis.Correction(
                "call-001", "revision-2", BASIS_TIME.plusSeconds(60));
        var original = available(defaultTerms(CallDirection.BULLISH),
                directionalRoute(CallDirection.BULLISH),
                availableReturn(ORIGINAL, ASSET_ID, "0.100000000000"));
        var correctionTerms = terms(CallDirection.BEARISH, correction, ASSET_ID,
                new Absent(), correction.eventTime(), correction.eventTime());
        var corrected = available(correctionTerms,
                directionalRoute(CallDirection.BEARISH),
                availableReturn(correction, ASSET_ID, "-0.100000000000"));

        assertThat(original.directionalWinResult().directionalWin()).isTrue();
        assertThat(corrected.directionalWinResult().directionalWin()).isTrue();
        assertThat(nestedBasis(corrected.assetReturnResult())).isSameAs(correction);
    }

    @Test
    void sourceTargetDispositionIsIgnoredAndPreserved() {
        Present target = new Present(new BigDecimal("999"), USD, null);
        var terms = terms(CallDirection.BULLISH, ORIGINAL, ASSET_ID,
                target, BASIS_TIME, BASIS_TIME);
        var result = available(terms, directionalRoute(CallDirection.BULLISH),
                availableReturn(ORIGINAL, ASSET_ID, "0.100000000000"));

        assertThat(result.termsEvidence()).isSameAs(terms);
        assertThat(result.termsEvidence().targetDisposition()).isSameAs(target);
        assertThat(result.directionalWinResult().directionalWin()).isTrue();
    }

    @Test
    void compositionIsIndependentOfLocaleTimezoneAndPriorCalls() {
        var request = request(defaultTerms(CallDirection.STRONG_BEARISH),
                route(CallDirection.STRONG_BEARISH),
                availableReturn(ORIGINAL, ASSET_ID, "-0.100000000000"));
        var expected = DirectionalWinOrchestrator.orchestrate(request);
        Locale originalLocale = Locale.getDefault();
        TimeZone originalZone = TimeZone.getDefault();
        try {
            Locale.setDefault(Locale.JAPAN);
            TimeZone.setDefault(TimeZone.getTimeZone("Pacific/Honolulu"));
            available(defaultTerms(CallDirection.BULLISH),
                    directionalRoute(CallDirection.BULLISH),
                    availableReturn(ORIGINAL, ASSET_ID, "0.100000000000"));
            assertThat(DirectionalWinOrchestrator.orchestrate(request))
                    .isEqualTo(expected);
        } finally {
            Locale.setDefault(originalLocale);
            TimeZone.setDefault(originalZone);
        }
    }

    @ParameterizedTest
    @EnumSource(value = CallDirection.class, names = {
            "STRONG_BULLISH", "BULLISH", "BEARISH", "STRONG_BEARISH"})
    void allCanonicalDirectionalRoutesUseTheirExactPreservedSide(
            CallDirection direction) {
        boolean bullish = direction == CallDirection.STRONG_BULLISH
                || direction == CallDirection.BULLISH;
        BigDecimal selectedReturn = new BigDecimal(
                bullish ? "0.250000000000" : "-0.250000000000");
        var terms = defaultTerms(direction);
        var route = directionalRoute(direction);
        var assetReturn = availableReturn(ORIGINAL, ASSET_ID, selectedReturn);

        var result = available(terms, route, assetReturn);

        assertThat(route.source().context().direction()).isEqualTo(direction);
        assertThat(result.termsEvidence()).isSameAs(terms);
        assertThat(result.sideRouting()).isSameAs(route);
        assertThat(result.assetReturnResult()).isSameAs(assetReturn);
        assertThat(result.assetReturnResult().assetReturn())
                .isSameAs(selectedReturn);
        assertThat(result.directionalWinResult().directionalWin()).isTrue();
    }

    @ParameterizedTest(name = "strict sign {0}")
    @MethodSource("strictSignVectors")
    void strictSignComparisonMakesExactZeroAMissForBothSides(
            String label,
            CallDirection direction,
            String assetReturn,
            boolean expected) {
        var result = available(defaultTerms(direction),
                directionalRoute(direction),
                availableReturn(ORIGINAL, ASSET_ID, assetReturn));

        assertThat(result.directionalWinResult().directionalWin())
                .as(label)
                .isEqualTo(expected);
    }

    static Stream<Arguments> strictSignVectors() {
        return Stream.of(
                Arguments.of("bullish-positive", CallDirection.BULLISH,
                        "0.000000000001", true),
                Arguments.of("bullish-zero", CallDirection.BULLISH,
                        "0.000000000000", false),
                Arguments.of("bullish-negative", CallDirection.BULLISH,
                        "-0.000000000001", false),
                Arguments.of("bearish-positive", CallDirection.BEARISH,
                        "0.000000000001", false),
                Arguments.of("bearish-zero", CallDirection.BEARISH,
                        "0.000000000000", false),
                Arguments.of("bearish-negative", CallDirection.BEARISH,
                        "-0.000000000001", true));
    }

    @ParameterizedTest(name = "unavailable leaf {0}")
    @MethodSource("allUnavailableLeaves")
    void everyNestedUnavailableLeafIsPreservedWithoutReasonMapping(
            String label,
            AssetReturnResult.Unavailable assetReturn) {
        var terms = defaultTerms(CallDirection.BULLISH);
        var route = directionalRoute(CallDirection.BULLISH);

        var result = DirectionalWinOrchestrator.orchestrate(
                request(terms, route, assetReturn));

        assertThat(result).isInstanceOfSatisfying(
                DirectionalWinOrchestrationResolution
                        .AssetReturnUnavailable.class,
                unavailable -> {
                    assertThat(unavailable.termsEvidence()).isSameAs(terms);
                    assertThat(unavailable.sideRouting()).isSameAs(route);
                    assertThat(unavailable.assetReturnResult())
                            .as(label)
                            .isSameAs(assetReturn);
                    assertUnavailableReasonChainIsExact(assetReturn);
                });
    }

    static Stream<Arguments> allUnavailableLeaves() {
        Stream<Arguments> pairOnly = Arrays.stream(
                AssetReturnPricePairResolution.UnavailableReason.values())
                .filter(reason -> !carriesEndpointReason(reason))
                .map(reason -> Arguments.of(
                        reason.name(), unavailablePair(
                                ORIGINAL, ASSET_ID, reason, null)));
        Stream<Arguments> endpointCarrying = Stream.of(
                AssetReturnPricePairResolution.UnavailableReason
                        .BASIS_AND_ENDPOINT_PRICE_UNAVAILABLE,
                AssetReturnPricePairResolution.UnavailableReason
                        .ENDPOINT_PRICE_UNAVAILABLE)
                .flatMap(pairReason -> Arrays.stream(
                        EndpointPriceResolution.UnavailableReason.values())
                        .map(endpointReason -> Arguments.of(
                                pairReason.name() + "+" + endpointReason.name(),
                                unavailablePair(
                                        ORIGINAL, ASSET_ID, pairReason,
                                        endpointReason))));
        Stream<Arguments> output = Stream.of(Arguments.of(
                "OUTPUT_NOT_REPRESENTABLE",
                unavailableOutput(ORIGINAL, ASSET_ID)));
        return Stream.concat(Stream.concat(pairOnly, endpointCarrying), output);
    }

    @ParameterizedTest(name = "neutral precedence {0}")
    @MethodSource("neutralAssetReturnLeaves")
    void neutralPrecedencePreservesAnyCorrelatedReturnWithoutABoolean(
            String label,
            AssetReturnResult assetReturn) {
        var terms = defaultTerms(CallDirection.NEUTRAL);
        var route = (NonDirectionalRoute) route(CallDirection.NEUTRAL);

        var result = DirectionalWinOrchestrator.orchestrate(
                request(terms, route, assetReturn));

        assertThat(result).isInstanceOfSatisfying(
                DirectionalWinOrchestrationResolution.NotApplicable.class,
                notApplicable -> {
                    assertThat(notApplicable.termsEvidence()).isSameAs(terms);
                    assertThat(notApplicable.sideRouting()).isSameAs(route);
                    assertThat(notApplicable.assetReturnResult())
                            .as(label)
                            .isSameAs(assetReturn);
                    assertThat(notApplicable.sideRouting().source().reason())
                            .isEqualTo(com.wallstreetreceipts.api.domain.outcome
                                    .direction.CallDirectionPolarityResolution
                                    .NonDirectionalReason.NEUTRAL_DIRECTION);
                });
    }

    static Stream<Arguments> neutralAssetReturnLeaves() {
        return Stream.of(
                Arguments.of("available", availableReturn(
                        ORIGINAL, ASSET_ID, "0.100000000000")),
                Arguments.of("endpoint-not-reached", unavailablePair(
                        ORIGINAL, ASSET_ID,
                        AssetReturnPricePairResolution.UnavailableReason
                                .ENDPOINT_PRICE_UNAVAILABLE,
                        EndpointPriceResolution.UnavailableReason
                                .ENDPOINT_NOT_REACHED_AS_OF)),
                Arguments.of("basis-and-endpoint-not-reached", unavailablePair(
                        ORIGINAL, ASSET_ID,
                        AssetReturnPricePairResolution.UnavailableReason
                                .BASIS_AND_ENDPOINT_PRICE_UNAVAILABLE,
                        EndpointPriceResolution.UnavailableReason
                                .ENDPOINT_NOT_REACHED_AS_OF)),
                Arguments.of("output-not-representable",
                        unavailableOutput(ORIGINAL, ASSET_ID)));
    }

    private static DirectionalWinOrchestrationPolicyVersion policy() {
        return DirectionalWinOrchestrationPolicyVersion
                .SUPPLIED_LEAF_DIRECTIONAL_WIN_ORCHESTRATION_V1;
    }

    private static DirectionalWinOrchestrationRequest request(
            BasisForecastTermsEvidence terms,
            CalculatorSideRouting.Result route,
            AssetReturnResult assetReturn) {
        return new DirectionalWinOrchestrationRequest(
                policy(), terms, route, assetReturn);
    }

    private static DirectionalWinOrchestrationResolution.Available available(
            BasisForecastTermsEvidence terms,
            DirectionalRoute route,
            AssetReturnResult.Available assetReturn) {
        var result = DirectionalWinOrchestrator.orchestrate(
                request(terms, route, assetReturn));
        assertThat(result)
                .isInstanceOf(DirectionalWinOrchestrationResolution.Available.class);
        return (DirectionalWinOrchestrationResolution.Available) result;
    }

    private static BasisForecastTermsEvidence defaultTerms(
            CallDirection direction) {
        return terms(direction, ORIGINAL, ASSET_ID, new Absent(),
                BASIS_TIME, BASIS_TIME);
    }

    private static BasisForecastTermsEvidence terms(
            CallDirection direction,
            OutcomeBasis basis,
            String assetId,
            BasisForecastTermsEvidence.TargetDisposition targetDisposition,
            Instant availableAt,
            Instant capturedAt) {
        return new BasisForecastTermsEvidence(
                "terms-evidence-1", basis, assetId, direction,
                targetDisposition, "provider-research", "provider-event-1",
                availableAt, capturedAt, "provenance-terms-1");
    }

    private static CalculatorSideRouting.Result route(CallDirection direction) {
        return CalculatorSideRouting.route(CallDirectionPolarityResolver.resolve(
                new CallDirectionPolarityRequest(
                        CallDirectionPolarityPolicyVersion
                                .COLLAPSE_STRONG_DIRECTIONS_NEUTRAL_NON_DIRECTIONAL_V1,
                        direction)));
    }

    private static DirectionalRoute directionalRoute(CallDirection direction) {
        return (DirectionalRoute) route(direction);
    }

    private static AssetReturnResult.Available availableReturn(
            OutcomeBasis basis,
            String assetId,
            String assetReturn) {
        return availableReturn(basis, assetId, new BigDecimal(assetReturn));
    }

    private static AssetReturnResult.Available availableReturn(
            OutcomeBasis basis,
            String assetId,
            BigDecimal assetReturn) {
        var pair = resolvedPair(basis, assetId, "100", "120");
        return new AssetReturnResult.Available(
                assetReturnContext(pair), assetReturn);
    }

    private static AssetReturnResult.Unavailable unavailablePair(
            OutcomeBasis basis,
            String assetId,
            AssetReturnPricePairResolution.UnavailableReason pairReason,
            EndpointPriceResolution.UnavailableReason endpointReason) {
        EndpointPriceResolution endpoint = carriesEndpointReason(pairReason)
                ? new EndpointPriceResolution.Unavailable(
                        endpointContext(basis, assetId), endpointReason)
                : resolvedEndpoint(basis, assetId, "120");
        var pair = new AssetReturnPricePairResolution.Unavailable(
                pairContext(endpoint), pairReason, endpointReason);
        return new AssetReturnResult.Unavailable(
                assetReturnContext(pair),
                AssetReturnResult.UnavailableReason.PRICE_PAIR_UNAVAILABLE,
                pairReason);
    }

    private static AssetReturnResult.Unavailable unavailableOutput(
            OutcomeBasis basis,
            String assetId) {
        return new AssetReturnResult.Unavailable(
                assetReturnContext(resolvedPair(basis, assetId, "100", "120")),
                AssetReturnResult.UnavailableReason.OUTPUT_NOT_REPRESENTABLE,
                null);
    }

    private static boolean carriesEndpointReason(
            AssetReturnPricePairResolution.UnavailableReason reason) {
        return reason == AssetReturnPricePairResolution.UnavailableReason
                .BASIS_AND_ENDPOINT_PRICE_UNAVAILABLE
                || reason == AssetReturnPricePairResolution.UnavailableReason
                        .ENDPOINT_PRICE_UNAVAILABLE;
    }

    private static AssetReturnResult.CalculationContext assetReturnContext(
            AssetReturnPricePairResolution pair) {
        return new AssetReturnResult.CalculationContext(
                AssetReturnPolicyVersion
                        .SIGNED_BASIS_DENOMINATOR_SCALE_12_HALF_EVEN_V1,
                ASSET_RETURN_HASH, pair);
    }

    private static AssetReturnPricePairResolution.ResolutionContext pairContext(
            EndpointPriceResolution endpoint) {
        return new AssetReturnPricePairResolution.ResolutionContext(
                AssetReturnPricePairPolicyVersion
                        .SOURCE_RECORDED_BASIS_EVENT_TO_OFFICIAL_ENDPOINT_PRICE_PAIR_V1,
                PAIR_HASH, endpoint);
    }

    private static AssetReturnPricePairResolution.Resolved resolvedPair(
            OutcomeBasis basis,
            String assetId,
            String basisPrice,
            String endpointPrice) {
        EndpointPriceResolution.Resolved endpoint = resolvedEndpoint(
                basis, assetId, endpointPrice);
        BasisPriceObservation basisObservation = new BasisPriceObservation(
                "basis-observation", "basis-provider-event", basis,
                assetId, VENUE_ID, USD, PRICE_SOURCE_ID, PRICE_SOURCE_REVISION,
                "provenance-basis",
                BasisPriceField.SOURCE_RECORDED_BASIS_EVENT_PRICE,
                REQUIRED_ADJUSTMENT,
                CorporateActionContinuity.SPLIT_REVERSE_SPLIT_CONTINUOUS,
                basis.eventTime(), basis.eventTime(), basis.eventTime(),
                new BigDecimal(basisPrice));
        EndpointPriceObservation endpointObservation = endpoint.observation();
        PricePairAdjustmentEvidence adjustment = new PricePairAdjustmentEvidence(
                "adjustment-evidence", "adjustment-provider-event", basis,
                assetId, VENUE_ID, USD,
                "source-corporate-actions", "adjustment-revision-9",
                "provenance-adjustment",
                basisObservation.observationId(),
                basisObservation.providerEventId(),
                endpointObservation.observationId(),
                endpointObservation.providerEventId(),
                basis.eventTime(), ENDPOINT_CLOSE, REQUIRED_ADJUSTMENT,
                CorporateActionContinuity.SPLIT_REVERSE_SPLIT_CONTINUOUS,
                ENDPOINT_CLOSE, ENDPOINT_CLOSE);
        return new AssetReturnPricePairResolution.Resolved(
                pairContext(endpoint), basisObservation, adjustment);
    }

    private static EndpointPriceResolution.Resolved resolvedEndpoint(
            OutcomeBasis basis,
            String assetId,
            String price) {
        EndpointPriceObservation observation = new EndpointPriceObservation(
                "endpoint-observation", "endpoint-provider-event",
                assetId, VENUE_ID, USD, PRICE_SOURCE_ID, PRICE_SOURCE_REVISION,
                "provenance-endpoint", CALENDAR_ID, CATALOG_REVISION,
                "session-endpoint",
                EndpointPriceField.OFFICIAL_REGULAR_SESSION_CLOSE,
                REQUIRED_ADJUSTMENT,
                CorporateActionContinuity.SPLIT_REVERSE_SPLIT_CONTINUOUS,
                ENDPOINT_CLOSE, ENDPOINT_CLOSE, ENDPOINT_CLOSE,
                new BigDecimal(price));
        return new EndpointPriceResolution.Resolved(
                endpointContext(basis, assetId), observation);
    }

    private static EndpointPriceResolution.ResolutionContext endpointContext(
            OutcomeBasis basis,
            String assetId) {
        return new EndpointPriceResolution.ResolutionContext(
                EndpointPricePolicyVersion
                        .OFFICIAL_PRIMARY_VENUE_CLOSE_SPLIT_ADJUSTED_V1,
                ENDPOINT_HASH, horizonResolution(basis), catalogEvidence(),
                binding(assetId), AS_OF);
    }

    private static SessionCloseHorizonResolution.Resolved horizonResolution(
            OutcomeBasis basis) {
        TradingSession endpoint = new TradingSession(
                "session-endpoint", ENDPOINT_OPEN, ENDPOINT_CLOSE);
        var context = new SessionCloseHorizonResolution.ResolutionContext(
                SessionCloseHorizonPolicyVersion
                        .STRICTLY_AFTER_BASIS_EVENT_SESSION_CLOSE_V1,
                HORIZON_HASH, basis, OutcomeHorizon.D1, 1,
                CALENDAR_ID, CATALOG_REVISION);
        return new SessionCloseHorizonResolution.Resolved(
                new SessionCloseHorizonResolution.ResolvedSessionWindow(
                        context, List.of(endpoint), endpoint));
    }

    private static CatalogPointInTimeEvidence catalogEvidence() {
        return new CatalogPointInTimeEvidence(
                CALENDAR_ID, CATALOG_REVISION,
                "source-calendar", "calendar-source-revision-1",
                BASIS_TIME, BASIS_TIME, "provenance-calendar");
    }

    private static EndpointPriceBinding binding(String assetId) {
        return new EndpointPriceBinding(
                "binding-asset-xnas", "binding-revision-1",
                assetId, VENUE_ID, USD, PRICE_SOURCE_ID, PRICE_SOURCE_REVISION,
                BASIS_TIME, BASIS_TIME, "provenance-binding");
    }

    private static OutcomeBasis nestedBasis(AssetReturnResult result) {
        AssetReturnPricePairResolution pair = switch (result) {
            case AssetReturnResult.Available available ->
                available.context().pricePairResolution();
            case AssetReturnResult.Unavailable unavailable ->
                unavailable.context().pricePairResolution();
        };
        EndpointPriceResolution endpoint = switch (pair) {
            case AssetReturnPricePairResolution.Resolved resolved ->
                resolved.context().endpointPriceResolution();
            case AssetReturnPricePairResolution.Unavailable unavailable ->
                unavailable.context().endpointPriceResolution();
        };
        var context = switch (endpoint) {
            case EndpointPriceResolution.Resolved resolved -> resolved.context();
            case EndpointPriceResolution.Unavailable unavailable ->
                unavailable.context();
        };
        return context.horizonResolution().window().context().basis();
    }

    private static void assertUnavailableReasonChainIsExact(
            AssetReturnResult.Unavailable assetReturn) {
        if (assetReturn.reason()
                == AssetReturnResult.UnavailableReason.OUTPUT_NOT_REPRESENTABLE) {
            assertThat(assetReturn.pricePairReason()).isNull();
            assertThat(assetReturn.context().pricePairResolution())
                    .isInstanceOf(AssetReturnPricePairResolution.Resolved.class);
            return;
        }
        var pair = (AssetReturnPricePairResolution.Unavailable)
                assetReturn.context().pricePairResolution();
        assertThat(assetReturn.pricePairReason()).isSameAs(pair.reason());
        assertThat(pair.endpointReason()).isEqualTo(
                pair.context().endpointPriceResolution()
                        instanceof EndpointPriceResolution.Unavailable unavailable
                                ? unavailable.reason()
                                : null);
    }

    private static void assertRecordComponents(
            Class<?> type,
            String... expected) {
        assertThat(Stream.of(type.getRecordComponents())
                .map(component -> component.getName() + ":"
                        + component.getType().getSimpleName()))
                .containsExactly(expected);
    }

    private static int count(String value, String token) {
        return (value.length() - value.replace(token, "").length())
                / token.length();
    }
}
