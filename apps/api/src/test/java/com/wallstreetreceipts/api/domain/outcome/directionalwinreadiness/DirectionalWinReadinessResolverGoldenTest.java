package com.wallstreetreceipts.api.domain.outcome.directionalwinreadiness;

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
import org.junit.jupiter.params.provider.MethodSource;

import com.wallstreetreceipts.api.domain.call.CallDirection;
import com.wallstreetreceipts.api.domain.outcome.OutcomeHorizon;
import com.wallstreetreceipts.api.domain.outcome.assetreturn.AssetReturnPolicyVersion;
import com.wallstreetreceipts.api.domain.outcome.assetreturn.AssetReturnResult;
import com.wallstreetreceipts.api.domain.outcome.direction.CallDirectionPolarityPolicyVersion;
import com.wallstreetreceipts.api.domain.outcome.direction.CallDirectionPolarityRequest;
import com.wallstreetreceipts.api.domain.outcome.direction.CallDirectionPolarityResolver;
import com.wallstreetreceipts.api.domain.outcome.directionalwinorchestration
        .DirectionalWinOrchestrationPolicyVersion;
import com.wallstreetreceipts.api.domain.outcome.directionalwinorchestration
        .DirectionalWinOrchestrationRequest;
import com.wallstreetreceipts.api.domain.outcome.directionalwinorchestration
        .DirectionalWinOrchestrationResolution;
import com.wallstreetreceipts.api.domain.outcome.directionalwinorchestration
        .DirectionalWinOrchestrator;
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
import com.wallstreetreceipts.api.domain.outcome.targeteligibility.BasisForecastTermsEvidence;
import com.wallstreetreceipts.api.domain.outcome.targeteligibility
        .BasisForecastTermsEvidence.TargetDisposition.Absent;

class DirectionalWinReadinessResolverGoldenTest {

    private static final String POLICY_HASH =
            "1eca77c5b4d43de7657281c161a8c50356cd90e1a18c6e9fd7f5b2c0142b7ec7";
    private static final String SOURCE_HASH =
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

        assertThat(bytes).hasSize(2353);
        assertThat(HexFormat.of().formatHex(
                MessageDigest.getInstance("SHA-256").digest(bytes)))
                .isEqualTo(POLICY_HASH)
                .isEqualTo(policy().definitionHash());
        assertThat(policy().canonicalDefinition())
                .contains("RESULT_CONSTRUCTORS_AND_RESOLVER_SHARE_EXACT_CLASSIFICATION_VALIDATION")
                .contains("ONLY_RESOLVE_ATTESTS_REQUEST_TO_RESULT_INVOCATION")
                .contains("EXACT_AWAITING_ENDPOINT_CHAIN")
                .contains("EVIDENCE_UNAVAILABLE_EVEN_WHEN_ENDPOINT_NOT_REACHED")
                .contains("\"retry\":\"ABSENT\"")
                .endsWith("\"publication\":\"ABSENT\"}");
        String adr = Files.readString(Path.of(
                "../../decisions/ADR-022-supplied-leaf-directional-win-readiness.md"));
        assertThat(adr)
                .contains("2353-byte")
                .contains(POLICY_HASH);
        assertThat(adr.replaceAll("\\s+", " "))
                .contains("exactly 118 test invocations");
        int canonicalStart = adr.indexOf('\n', adr.indexOf("```json")) + 1;
        int canonicalEnd = adr.indexOf("\n```", canonicalStart);
        assertThat(adr.substring(canonicalStart, canonicalEnd))
                .isEqualTo(policy().canonicalDefinition());
        assertThat(unavailableVectors().count()).isEqualTo(55);
        assertThat(unavailableVectors().filter(UnavailableVector::awaiting).count())
                .isEqualTo(1);

        bytes[0] = (byte) '!';
        assertThat(policy().canonicalDefinitionUtf8()[0]).isEqualTo((byte) '{');
    }

    @Test
    void exactFileRecordSealedAndDisconnectedSurfacesAreStable()
            throws Exception {
        Path packagePath = Path.of(
                "src/main/java/com/wallstreetreceipts/api/domain/outcome/directionalwinreadiness");
        try (var files = Files.list(packagePath)) {
            assertThat(files.filter(path -> path.toString().endsWith(".java"))
                    .map(path -> path.getFileName().toString()).sorted().toList())
                    .containsExactly(
                            "DirectionalWinReadinessPolicyVersion.java",
                            "DirectionalWinReadinessRequest.java",
                            "DirectionalWinReadinessResolution.java",
                            "DirectionalWinReadinessResolver.java");
        }
        Path testPath = Path.of(
                "src/test/java/com/wallstreetreceipts/api/domain/outcome/directionalwinreadiness");
        try (var files = Files.list(testPath)) {
            assertThat(files.filter(path -> path.toString().endsWith(".java"))
                    .map(path -> path.getFileName().toString()).toList())
                    .containsExactly("DirectionalWinReadinessResolverGoldenTest.java");
        }
        assertRecordComponents(DirectionalWinReadinessRequest.class,
                "policyVersion:DirectionalWinReadinessPolicyVersion",
                "sourceResolution:DirectionalWinOrchestrationResolution");
        assertRecordComponents(
                DirectionalWinReadinessResolution.ResolutionContext.class,
                "policyVersion:DirectionalWinReadinessPolicyVersion",
                "policyDefinitionHash:String");
        assertRecordComponents(DirectionalWinReadinessResolution.Settled.class,
                "context:ResolutionContext",
                "sourceResolution:DirectionalWinOrchestrationResolution");
        assertRecordComponents(
                DirectionalWinReadinessResolution.AwaitingEndpoint.class,
                "context:ResolutionContext",
                "sourceResolution:DirectionalWinOrchestrationResolution");
        assertRecordComponents(
                DirectionalWinReadinessResolution.EvidenceUnavailable.class,
                "context:ResolutionContext",
                "sourceResolution:DirectionalWinOrchestrationResolution");
        assertThat(DirectionalWinReadinessResolution.class.isSealed()).isTrue();
        assertThat(Arrays.stream(DirectionalWinReadinessResolution.class
                .getPermittedSubclasses()).map(Class::getSimpleName).toList())
                .containsExactlyInAnyOrder(
                        "Settled", "AwaitingEndpoint", "EvidenceUnavailable");
        assertThat(DirectionalWinReadinessPolicyVersion.values())
                .containsExactly(policy());

        String request = Files.readString(packagePath.resolve(
                "DirectionalWinReadinessRequest.java"));
        String resolution = Files.readString(packagePath.resolve(
                "DirectionalWinReadinessResolution.java"));
        String resolver = Files.readString(packagePath.resolve(
                "DirectionalWinReadinessResolver.java"));
        assertThat(request + resolution)
                .doesNotContain(".reason()")
                .doesNotContain("UnavailableReason");
        assertThat(count(resolver, ".reason()")).isEqualTo(3);
        assertThat(resolver)
                .contains("ENDPOINT_PRICE_UNAVAILABLE")
                .contains("ENDPOINT_NOT_REACHED_AS_OF")
                .contains("PRICE_PAIR_UNAVAILABLE")
                .doesNotContain("BASIS_AND_ENDPOINT_PRICE_UNAVAILABLE");
        assertThat(request + resolution + resolver)
                .doesNotContain("OutcomeEvaluationStatus")
                .doesNotContain("CallOutcome")
                .doesNotContain("DirectionalWinOrchestrator.orchestrate(")
                .doesNotContain("AssetReturnCalculator")
                .doesNotContain("AssetReturnPricePairSelector")
                .doesNotContain("EndpointPriceSelector")
                .doesNotContain("DirectionalWinCalculator")
                .doesNotContain("Clock")
                .doesNotContain("@Service")
                .doesNotContain("@Repository")
                .doesNotContain("@Controller");
    }

    @Test
    void nullRootsAndRequiredFieldsFailClosed() {
        var source = source(CallDirection.BULLISH,
                availableReturn(ORIGINAL, ASSET_ID));
        var context = context();

        assertThatThrownBy(() -> DirectionalWinReadinessResolver.resolve(null))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new DirectionalWinReadinessRequest(null, source))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new DirectionalWinReadinessRequest(policy(), null))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new DirectionalWinReadinessResolution
                .ResolutionContext(null, POLICY_HASH))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new DirectionalWinReadinessResolution
                .ResolutionContext(policy(), null))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new DirectionalWinReadinessResolution
                .ResolutionContext(policy(), "0".repeat(64)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new DirectionalWinReadinessResolution
                .Settled(null, source))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new DirectionalWinReadinessResolution
                .Settled(context, null))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> DirectionalWinReadinessResolver
                .requireClassification(source, null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void directResultConstructorsShareExactFailClosedClassification() {
        var settled = source(CallDirection.BULLISH,
                availableReturn(ORIGINAL, ASSET_ID));
        var awaiting = source(CallDirection.BULLISH, unavailablePair(
                ORIGINAL, ASSET_ID,
                AssetReturnPricePairResolution.UnavailableReason
                        .ENDPOINT_PRICE_UNAVAILABLE,
                EndpointPriceResolution.UnavailableReason
                        .ENDPOINT_NOT_REACHED_AS_OF));
        var unavailable = source(CallDirection.BULLISH, unavailablePair(
                ORIGINAL, ASSET_ID,
                AssetReturnPricePairResolution.UnavailableReason
                        .BASIS_AND_ENDPOINT_PRICE_UNAVAILABLE,
                EndpointPriceResolution.UnavailableReason
                        .ENDPOINT_NOT_REACHED_AS_OF));
        var context = context();

        assertThat(new DirectionalWinReadinessResolution.Settled(
                context, settled).sourceResolution()).isSameAs(settled);
        assertThat(new DirectionalWinReadinessResolution.AwaitingEndpoint(
                context, awaiting).sourceResolution()).isSameAs(awaiting);
        assertThat(new DirectionalWinReadinessResolution.EvidenceUnavailable(
                context, unavailable).sourceResolution()).isSameAs(unavailable);
        assertWrongVariant(() -> new DirectionalWinReadinessResolution
                .AwaitingEndpoint(context, settled));
        assertWrongVariant(() -> new DirectionalWinReadinessResolution
                .EvidenceUnavailable(context, settled));
        assertWrongVariant(() -> new DirectionalWinReadinessResolution
                .Settled(context, awaiting));
        assertWrongVariant(() -> new DirectionalWinReadinessResolution
                .EvidenceUnavailable(context, awaiting));
        assertWrongVariant(() -> new DirectionalWinReadinessResolution
                .Settled(context, unavailable));
        assertWrongVariant(() -> new DirectionalWinReadinessResolution
                .AwaitingEndpoint(context, unavailable));
    }

    @Test
    void equalButDistinctWholeSourceRecordsReplayEqually() {
        var firstSource = source(CallDirection.NEUTRAL, unavailablePair(
                ORIGINAL, ASSET_ID,
                AssetReturnPricePairResolution.UnavailableReason
                        .ENDPOINT_PRICE_UNAVAILABLE,
                EndpointPriceResolution.UnavailableReason
                        .ENDPOINT_NOT_REACHED_AS_OF));
        var secondSource = source(CallDirection.NEUTRAL, unavailablePair(
                ORIGINAL, ASSET_ID,
                AssetReturnPricePairResolution.UnavailableReason
                        .ENDPOINT_PRICE_UNAVAILABLE,
                EndpointPriceResolution.UnavailableReason
                        .ENDPOINT_NOT_REACHED_AS_OF));

        assertThat(secondSource).isEqualTo(firstSource).isNotSameAs(firstSource);
        var first = DirectionalWinReadinessResolver.resolve(request(firstSource));
        var second = DirectionalWinReadinessResolver.resolve(request(secondSource));

        assertThat(second).isEqualTo(first).isNotSameAs(first);
        assertThat(sourceOf(first)).isSameAs(firstSource);
        assertThat(sourceOf(second)).isSameAs(secondSource);
    }

    @Test
    void classificationIsIndependentOfLocaleTimezoneAndPriorCalls() {
        var source = source(CallDirection.BULLISH, unavailableOutput(
                ORIGINAL, ASSET_ID));
        Locale originalLocale = Locale.getDefault();
        TimeZone originalTimeZone = TimeZone.getDefault();

        try {
            Locale.setDefault(Locale.JAPAN);
            TimeZone.setDefault(TimeZone.getTimeZone("Asia/Tokyo"));
            var first = DirectionalWinReadinessResolver.resolve(request(source));
            DirectionalWinReadinessResolver.resolve(request(source(
                    CallDirection.NEUTRAL,
                    availableReturn(ORIGINAL, ASSET_ID))));

            Locale.setDefault(Locale.GERMANY);
            TimeZone.setDefault(TimeZone.getTimeZone("America/New_York"));
            var second = DirectionalWinReadinessResolver.resolve(request(source));

            assertThat(first).isEqualTo(second);
            assertThat(sourceOf(first)).isSameAs(source);
            assertThat(sourceOf(second)).isSameAs(source);
        } finally {
            Locale.setDefault(originalLocale);
            TimeZone.setDefault(originalTimeZone);
        }
    }

    @ParameterizedTest(name = "unavailable source {0}")
    @MethodSource("unavailableSourceShapes")
    void everyUnavailableChainIsClassifiedForDirectionalAndNeutralSources(
            String label,
            DirectionalWinOrchestrationResolution source,
            AssetReturnResult.Unavailable assetReturn,
            Class<? extends DirectionalWinReadinessResolution> expectedType) {
        var result = DirectionalWinReadinessResolver.resolve(request(source));

        assertThat(result).as(label).isExactlyInstanceOf(expectedType);
        assertThat(sourceOf(result)).isSameAs(source);
        assertThat(assetReturnOf(source)).isSameAs(assetReturn);
        assertThat(result).satisfies(value -> {
            if (expectedType
                    == DirectionalWinReadinessResolution.AwaitingEndpoint.class) {
                assertExactAwaitingChain(assetReturn);
            }
        });
    }

    static Stream<Arguments> unavailableSourceShapes() {
        return unavailableVectors().flatMap(vector -> Stream.of(
                Arguments.of(vector.label() + "+directional",
                        source(CallDirection.BULLISH, vector.assetReturn()),
                        vector.assetReturn(), expectedType(vector)),
                Arguments.of(vector.label() + "+neutral",
                        source(CallDirection.NEUTRAL, vector.assetReturn()),
                        vector.assetReturn(), expectedType(vector))));
    }

    @ParameterizedTest(name = "settled source {0}")
    @MethodSource("settledSourceShapes")
    void availableReturnSettlesDirectionalAndNeutralSources(
            String label,
            DirectionalWinOrchestrationResolution source) {
        var result = DirectionalWinReadinessResolver.resolve(request(source));

        assertThat(result).as(label)
                .isExactlyInstanceOf(DirectionalWinReadinessResolution.Settled.class);
        assertThat(sourceOf(result)).isSameAs(source);
        assertThat(assetReturnOf(source))
                .isInstanceOf(AssetReturnResult.Available.class);
    }

    static Stream<Arguments> settledSourceShapes() {
        return Stream.of(
                Arguments.of("directional-available", source(
                        CallDirection.BULLISH,
                        availableReturn(ORIGINAL, ASSET_ID))),
                Arguments.of("neutral-not-applicable", source(
                        CallDirection.NEUTRAL,
                        availableReturn(ORIGINAL, ASSET_ID))));
    }

    private static Stream<UnavailableVector> unavailableVectors() {
        Stream<UnavailableVector> pairOnly = Arrays.stream(
                AssetReturnPricePairResolution.UnavailableReason.values())
                .filter(reason -> !carriesEndpointReason(reason))
                .map(reason -> new UnavailableVector(
                        reason.name(), unavailablePair(
                                ORIGINAL, ASSET_ID, reason, null), false));
        Stream<UnavailableVector> endpointCarrying = Stream.of(
                AssetReturnPricePairResolution.UnavailableReason
                        .BASIS_AND_ENDPOINT_PRICE_UNAVAILABLE,
                AssetReturnPricePairResolution.UnavailableReason
                        .ENDPOINT_PRICE_UNAVAILABLE)
                .flatMap(pairReason -> Arrays.stream(
                        EndpointPriceResolution.UnavailableReason.values())
                        .map(endpointReason -> new UnavailableVector(
                                pairReason.name() + "+" + endpointReason.name(),
                                unavailablePair(
                                        ORIGINAL, ASSET_ID, pairReason,
                                        endpointReason),
                                pairReason
                                        == AssetReturnPricePairResolution
                                                .UnavailableReason
                                                .ENDPOINT_PRICE_UNAVAILABLE
                                        && endpointReason
                                                == EndpointPriceResolution
                                                        .UnavailableReason
                                                        .ENDPOINT_NOT_REACHED_AS_OF)));
        Stream<UnavailableVector> output = Stream.of(new UnavailableVector(
                "OUTPUT_NOT_REPRESENTABLE",
                unavailableOutput(ORIGINAL, ASSET_ID), false));
        return Stream.concat(Stream.concat(pairOnly, endpointCarrying), output);
    }

    private static Class<? extends DirectionalWinReadinessResolution> expectedType(
            UnavailableVector vector) {
        return vector.awaiting()
                ? DirectionalWinReadinessResolution.AwaitingEndpoint.class
                : DirectionalWinReadinessResolution.EvidenceUnavailable.class;
    }

    private static DirectionalWinReadinessPolicyVersion policy() {
        return DirectionalWinReadinessPolicyVersion
                .SUPPLIED_LEAF_DIRECTIONAL_WIN_READINESS_V1;
    }

    private static DirectionalWinReadinessResolution.ResolutionContext context() {
        return new DirectionalWinReadinessResolution.ResolutionContext(
                policy(), POLICY_HASH);
    }

    private static DirectionalWinReadinessRequest request(
            DirectionalWinOrchestrationResolution source) {
        return new DirectionalWinReadinessRequest(policy(), source);
    }

    private static DirectionalWinOrchestrationResolution source(
            CallDirection direction,
            AssetReturnResult assetReturn) {
        var terms = new BasisForecastTermsEvidence(
                "terms-evidence-1", ORIGINAL, ASSET_ID, direction,
                new Absent(), "provider-research", "provider-event-1",
                BASIS_TIME, BASIS_TIME, "provenance-terms-1");
        CalculatorSideRouting.Result route = CalculatorSideRouting.route(
                CallDirectionPolarityResolver.resolve(
                        new CallDirectionPolarityRequest(
                                CallDirectionPolarityPolicyVersion
                                        .COLLAPSE_STRONG_DIRECTIONS_NEUTRAL_NON_DIRECTIONAL_V1,
                                direction)));
        return DirectionalWinOrchestrator.orchestrate(
                new DirectionalWinOrchestrationRequest(
                        DirectionalWinOrchestrationPolicyVersion
                                .SUPPLIED_LEAF_DIRECTIONAL_WIN_ORCHESTRATION_V1,
                        terms, route, assetReturn));
    }

    private static AssetReturnResult.Available availableReturn(
            OutcomeBasis basis,
            String assetId) {
        return new AssetReturnResult.Available(
                assetReturnContext(resolvedPair(basis, assetId, "100", "120")),
                new BigDecimal("0.200000000000"));
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

    private static AssetReturnResult assetReturnOf(
            DirectionalWinOrchestrationResolution source) {
        return switch (source) {
            case DirectionalWinOrchestrationResolution.Available available ->
                available.assetReturnResult();
            case DirectionalWinOrchestrationResolution.NotApplicable notApplicable ->
                notApplicable.assetReturnResult();
            case DirectionalWinOrchestrationResolution.AssetReturnUnavailable unavailable ->
                unavailable.assetReturnResult();
        };
    }

    private static DirectionalWinOrchestrationResolution sourceOf(
            DirectionalWinReadinessResolution resolution) {
        return switch (resolution) {
            case DirectionalWinReadinessResolution.Settled settled ->
                settled.sourceResolution();
            case DirectionalWinReadinessResolution.AwaitingEndpoint awaiting ->
                awaiting.sourceResolution();
            case DirectionalWinReadinessResolution.EvidenceUnavailable unavailable ->
                unavailable.sourceResolution();
        };
    }

    private static void assertExactAwaitingChain(
            AssetReturnResult.Unavailable assetReturn) {
        assertThat(assetReturn.reason()).isEqualTo(
                AssetReturnResult.UnavailableReason.PRICE_PAIR_UNAVAILABLE);
        assertThat(assetReturn.pricePairReason()).isEqualTo(
                AssetReturnPricePairResolution.UnavailableReason
                        .ENDPOINT_PRICE_UNAVAILABLE);
        var pair = (AssetReturnPricePairResolution.Unavailable)
                assetReturn.context().pricePairResolution();
        assertThat(pair.reason()).isEqualTo(
                AssetReturnPricePairResolution.UnavailableReason
                        .ENDPOINT_PRICE_UNAVAILABLE);
        assertThat(pair.endpointReason()).isEqualTo(
                EndpointPriceResolution.UnavailableReason
                        .ENDPOINT_NOT_REACHED_AS_OF);
        var endpoint = (EndpointPriceResolution.Unavailable)
                pair.context().endpointPriceResolution();
        assertThat(endpoint.reason()).isEqualTo(
                EndpointPriceResolution.UnavailableReason
                        .ENDPOINT_NOT_REACHED_AS_OF);
    }

    private static void assertWrongVariant(Runnable construction) {
        assertThatThrownBy(construction::run)
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("result classification");
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

    private record UnavailableVector(
            String label,
            AssetReturnResult.Unavailable assetReturn,
            boolean awaiting) {
    }
}
