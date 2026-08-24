package com.wallstreetreceipts.api.domain.outcome.benchmarkreturn;

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

import com.wallstreetreceipts.api.domain.master.AssetType;
import com.wallstreetreceipts.api.domain.outcome.OutcomeHorizon;
import com.wallstreetreceipts.api.domain.outcome.benchmarkassignment.BenchmarkAssetClassificationEvidence;
import com.wallstreetreceipts.api.domain.outcome.benchmarkassignment.BenchmarkAssignmentEvidence;
import com.wallstreetreceipts.api.domain.outcome.benchmarkassignment.BenchmarkAssignmentEvidence.BenchmarkReferenceKind;
import com.wallstreetreceipts.api.domain.outcome.benchmarkassignment.BenchmarkAssignmentPolicyVersion;
import com.wallstreetreceipts.api.domain.outcome.benchmarkassignment.BenchmarkAssignmentResolution;
import com.wallstreetreceipts.api.domain.outcome.benchmarkreferencepair.BenchmarkIndexDivisorContinuityEvidence;
import com.wallstreetreceipts.api.domain.outcome.benchmarkreferencepair.BenchmarkIndexDivisorContinuityEvidence.DivisorContinuity;
import com.wallstreetreceipts.api.domain.outcome.benchmarkreferencepair.BenchmarkReferenceIndexEvidence;
import com.wallstreetreceipts.api.domain.outcome.benchmarkreferencepair.BenchmarkReferenceIndexEvidence.EffectiveInterval;
import com.wallstreetreceipts.api.domain.outcome.benchmarkreferencepair.BenchmarkReferenceIndexEvidence.OpenEnded;
import com.wallstreetreceipts.api.domain.outcome.benchmarkreferencepair.BenchmarkReferenceIndexEvidence.ReferenceIndexKind;
import com.wallstreetreceipts.api.domain.outcome.benchmarkreferencepair.BenchmarkReferenceLevelObservation;
import com.wallstreetreceipts.api.domain.outcome.benchmarkreferencepair.BenchmarkReferenceLevelObservation.ReferenceLevelField;
import com.wallstreetreceipts.api.domain.outcome.benchmarkreferencepair.BenchmarkReferenceLevelPairPolicyVersion;
import com.wallstreetreceipts.api.domain.outcome.benchmarkreferencepair.BenchmarkReferenceLevelPairResolution;
import com.wallstreetreceipts.api.domain.outcome.benchmarkreturn.BenchmarkReturnResult.CalculationContext;
import com.wallstreetreceipts.api.domain.outcome.benchmarkreturn.BenchmarkReturnResult.OutputUnavailableReason;
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

class BenchmarkReturnCalculatorGoldenTest {

    private static final BenchmarkReturnPolicyVersion POLICY =
            BenchmarkReturnPolicyVersion
                    .SIGNED_BENCHMARK_BASIS_LEVEL_DENOMINATOR_SCALE_12_HALF_EVEN_V1;
    private static final BenchmarkReferenceLevelPairPolicyVersion PAIR_POLICY =
            BenchmarkReferenceLevelPairPolicyVersion
                    .POINT_IN_TIME_EXACT_BENCHMARK_PRICE_INDEX_LEVEL_PAIR_V1;
    private static final String POLICY_HASH =
            "96d0aab8e8e784b80a12b16c99f6ba8c5f44eff7a342fd14c075b944a0a7de79";
    private static final String PAIR_HASH =
            "2394b535c1061d32c647504a303b6f1e4ec2fe88e6017d9ff335d12087a5f73d";
    private static final String ASSIGNMENT_HASH =
            "7318514c2f50eda16b2d7ef35bc68d00d6a8b18a0f09f77130525fca2f32da69";
    private static final String ENDPOINT_HASH =
            "37e37aba9302d77366cef4129f77a82b7ccb2f1937bfffc0315ea8d0bc6b1f76";
    private static final String HORIZON_HASH =
            "550087efe7ddf2ba31974c89c2740ab79df986eefef48919c32c56a3232f8dc1";
    private static final String ASSET_ID = "asset-nvda";
    private static final String BENCHMARK_ASSET_ID = "asset-spx";
    private static final String VENUE_ID = "venue-xnas";
    private static final Currency USD = Currency.getInstance("USD");
    private static final Currency CAD = Currency.getInstance("CAD");
    private static final Instant BASIS_TIME =
            Instant.parse("2026-08-20T14:00:00Z");
    private static final Instant ENDPOINT_CLOSE =
            Instant.parse("2026-08-21T20:00:00Z");
    private static final Instant AS_OF =
            Instant.parse("2026-08-22T00:00:00Z");
    private static final OutcomeBasis BASIS =
            new OutcomeBasis.Original("call-adr031-benchmark", BASIS_TIME);
    private static final String MAX_NUMERIC =
            "99999999999999999999999999.999999999999";

    @Test
    void canonicalPolicyDefinitionHasStableExactUtf8BytesAndIndependentSha256()
            throws NoSuchAlgorithmException {
        byte[] first = POLICY.canonicalDefinitionUtf8();
        byte[] second = POLICY.canonicalDefinitionUtf8();
        String independentHash = HexFormat.of().formatHex(
                MessageDigest.getInstance("SHA-256").digest(first));

        assertThat(first).isNotSameAs(second).containsExactly(second);
        assertThat(new String(first, StandardCharsets.UTF_8))
                .isEqualTo(POLICY.canonicalDefinition());
        assertThat(POLICY.canonicalDefinition().chars())
                .allMatch(value -> value >= 0 && value <= 127);
        assertThat(first).hasSize(2832);
        assertThat(independentHash).isEqualTo(POLICY_HASH);
        assertThat(POLICY.definitionHash()).isEqualTo(POLICY_HASH);
        assertThat(POLICY.canonicalDefinition())
                .contains("\"requiredReferenceLevelPairPolicyDefinitionHash\":\""
                        + PAIR_HASH + "\"")
                .contains("\"formula\":\"(endpoint-basis)/basis\"")
                .contains("\"numerator\":\"ENDPOINT_MINUS_BASIS_REFERENCE_LEVEL\"")
                .contains("\"denominator\":\"BASIS_REFERENCE_LEVEL\"")
                .contains("\"subtractionCount\":1")
                .contains("\"divisionCount\":1")
                .contains("\"divisionScale\":12")
                .contains("\"roundingMode\":\"HALF_EVEN\"")
                .contains("\"operationOrder\":[\"SUBTRACT_BASIS_FROM_ENDPOINT_EXACTLY\",\"DIVIDE_NUMERATOR_BY_BASIS_AT_SCALE_12_HALF_EVEN\"]")
                .contains("\"intermediateRounding\":\"ABSENT\"")
                .contains("\"secondRounding\":\"ABSENT\"")
                .contains("\"outputBoundary\":\"SIGNED_NUMERIC_38_12_AT_LEAST_NEGATIVE_ONE\"")
                .contains("\"providerReturnFieldUse\":\"ABSENT\"")
                .contains("\"assetReturnResultReuse\":\"FORBIDDEN\"");

        first[0] = (byte) '!';
        assertThat(POLICY.canonicalDefinitionUtf8()[0]).isEqualTo((byte) '{');
    }

    @ParameterizedTest(name = "benchmark return {0}")
    @MethodSource("primaryFormulaVectors")
    void calculatesSignedPositiveNegativeAndZeroReturnsWithBasisLevelDenominator(
            String label, String basis, String endpoint, String expected) {
        BenchmarkReturnResult.Available result = calculateAvailable(basis, endpoint);

        assertThat(result.benchmarkReturn()).isEqualTo(new BigDecimal(expected));
        assertThat(result.benchmarkReturn().scale()).isEqualTo(12);
        assertThat(result.context().referenceLevelPairResolution())
                .isInstanceOf(BenchmarkReferenceLevelPairResolution.Resolved.class);
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
    void scaleEquivalentLevelsProduceEqualExactScaleTwelveResults() {
        BenchmarkReturnResult.Available scaleZero = calculateAvailable("100", "120");
        BenchmarkReturnResult.Available scaleTwelve = calculateAvailable(
                "100.000000000000", "120.000000000000");

        assertThat(scaleTwelve.benchmarkReturn())
                .isEqualTo(scaleZero.benchmarkReturn())
                .isEqualTo(new BigDecimal("0.200000000000"));
        assertThat(scaleZero.benchmarkReturn().scale()).isEqualTo(12);
        assertThat(scaleTwelve.benchmarkReturn().scale()).isEqualTo(12);
    }

    @ParameterizedTest(name = "half-even tie {0}")
    @MethodSource("halfEvenTieVectors")
    void roundsTheSingleDivisionAtPositiveAndNegativeExactHalfEvenTies(
            String label, String endpoint, String expected) {
        BenchmarkReturnResult.Available result = calculateAvailable(
                "2.000000000000", endpoint);

        assertThat(result.benchmarkReturn()).isEqualTo(new BigDecimal(expected));
    }

    static Stream<Arguments> halfEvenTieVectors() {
        return Stream.of(
                Arguments.of("positive-even-stays-even", "2.000000000001",
                        "0.000000000000"),
                Arguments.of("positive-odd-to-even", "2.000000000003",
                        "0.000000000002"),
                Arguments.of("negative-even-stays-even", "1.999999999999",
                        "0.000000000000"),
                Arguments.of("negative-odd-to-even", "1.999999999997",
                        "-0.000000000002"));
    }

    @Test
    void allowsRoundedNegativeOneForPositiveProviderPublishedLevels() {
        BenchmarkReturnResult.Available result = calculateAvailable(
                MAX_NUMERIC, "0.000000000001");

        assertThat(result.benchmarkReturn())
                .isEqualTo(new BigDecimal("-1.000000000000"));
        assertThat(result.benchmarkReturn().scale()).isEqualTo(12);
    }

    @Test
    void acceptsExactPrecision38OutputAndTypesRoundedPrecision39AsUnavailable() {
        BenchmarkReturnResult.Available maximum = calculateAvailable("1", MAX_NUMERIC);
        assertThat(maximum.benchmarkReturn()).isEqualTo(new BigDecimal(
                "99999999999999999999999998.999999999999"));
        assertThat(maximum.benchmarkReturn().precision()).isEqualTo(38);

        BenchmarkReferenceLevelPairResolution pair = resolvedPair("0.1", MAX_NUMERIC);
        BenchmarkReturnResult overflow = calculate(pair);
        assertThat(overflow).isEqualTo(new BenchmarkReturnResult.OutputUnavailable(
                context(pair), OutputUnavailableReason.OUTPUT_NOT_REPRESENTABLE));
        assertThat(resultContext(overflow).referenceLevelPairResolution())
                .isSameAs(pair);
    }

    @ParameterizedTest(name = "evidence reason {0}")
    @EnumSource(BenchmarkReferenceLevelPairResolution.UnavailableReason.class)
    void propagatesEveryEvidenceReasonOnlyInsideTheCompletePairReceipt(
            BenchmarkReferenceLevelPairResolution.UnavailableReason reason) {
        EndpointPriceResolution endpoint = reason
                == BenchmarkReferenceLevelPairResolution.UnavailableReason
                        .ENDPOINT_NOT_REACHED_AS_OF
                ? endpoint(VENUE_ID, USD, AnchorState.USABLE,
                        AS_OF.plusSeconds(60))
                : endpoint(VENUE_ID, USD, AnchorState.USABLE);
        BenchmarkReferenceLevelPairResolution pair =
                new BenchmarkReferenceLevelPairResolution.EvidenceUnavailable(
                        pairContext(resolvedAssignment(), endpoint), reason);

        BenchmarkReturnResult result = calculate(pair);

        assertThat(result).isEqualTo(
                new BenchmarkReturnResult.EvidenceUnavailable(context(pair)));
        assertThat(resultContext(result).referenceLevelPairResolution()).isSameAs(pair);
    }

    @ParameterizedTest(name = "anchor reason {0}")
    @EnumSource(BenchmarkReferenceLevelPairResolution.EndpointAnchorUnavailableReason.class)
    void propagatesEveryEndpointAnchorReasonOnlyInsideTheCompletePairReceipt(
            BenchmarkReferenceLevelPairResolution.EndpointAnchorUnavailableReason reason) {
        AnchorState state = switch (reason) {
            case CATALOG_NOT_KNOWN_AS_OF -> AnchorState.FUTURE_CATALOG;
            case CATALOG_EVIDENCE_MISMATCH -> AnchorState.MISMATCHED_CATALOG;
            case BINDING_NOT_KNOWN_AS_OF -> AnchorState.FUTURE_BINDING;
        };
        BenchmarkReferenceLevelPairResolution pair =
                new BenchmarkReferenceLevelPairResolution.EndpointAnchorUnavailable(
                        pairContext(resolvedAssignment(), endpoint(VENUE_ID, USD, state)),
                        reason);

        BenchmarkReturnResult result = calculate(pair);

        assertThat(result).isEqualTo(
                new BenchmarkReturnResult.EndpointAnchorUnavailable(context(pair)));
        assertThat(resultContext(result).referenceLevelPairResolution()).isSameAs(pair);
    }

    @ParameterizedTest(name = "assignment reason {0}")
    @EnumSource(BenchmarkAssignmentResolution.UnavailableReason.class)
    void propagatesEveryAssignmentReasonOnlyInsideTheCompletePairReceipt(
            BenchmarkAssignmentResolution.UnavailableReason reason) {
        BenchmarkAssignmentResolution assignment =
                new BenchmarkAssignmentResolution.Unavailable(
                        assignmentContext(), reason);
        BenchmarkReferenceLevelPairResolution pair =
                new BenchmarkReferenceLevelPairResolution.AssignmentUnavailable(
                        pairContext(assignment, endpoint(
                                VENUE_ID, USD, AnchorState.USABLE)));

        BenchmarkReturnResult result = calculate(pair);

        assertThat(result).isEqualTo(
                new BenchmarkReturnResult.AssignmentUnavailable(context(pair)));
        assertThat(resultContext(result).referenceLevelPairResolution()).isSameAs(pair);
    }

    @ParameterizedTest(name = "not applicable {0}")
    @MethodSource("notApplicableVectors")
    void propagatesEveryNotApplicableTruthTableBranchOnlyInsideThePairReceipt(
            String label, AssetType assetType, String country, Currency currency,
            String venue, BenchmarkAssignmentResolution.NotApplicableReason reason) {
        BenchmarkAssetClassificationEvidence classification = classification(
                assetType, country, currency, venue);
        BenchmarkAssignmentResolution assignment =
                new BenchmarkAssignmentResolution.NotApplicable(
                        assignmentContext(), classification, reason);
        BenchmarkReferenceLevelPairResolution pair =
                new BenchmarkReferenceLevelPairResolution.NotApplicable(
                        pairContext(assignment, endpoint(
                                venue, currency, AnchorState.USABLE)));

        BenchmarkReturnResult result = calculate(pair);

        assertThat(result).isEqualTo(
                new BenchmarkReturnResult.NotApplicable(context(pair)));
        assertThat(resultContext(result).referenceLevelPairResolution()).isSameAs(pair);
    }

    static Stream<Arguments> notApplicableVectors() {
        return Stream.of(
                Arguments.of("non-equity", AssetType.INDEX, "US", USD, VENUE_ID,
                        BenchmarkAssignmentResolution.NotApplicableReason.NON_EQUITY),
                Arguments.of("non-US venue", AssetType.EQUITY, "CA", USD,
                        "venue-xtsx", BenchmarkAssignmentResolution.NotApplicableReason
                                .NON_US_PRIMARY_VENUE),
                Arguments.of("non-USD currency", AssetType.EQUITY, "US", CAD,
                        VENUE_ID, BenchmarkAssignmentResolution.NotApplicableReason
                                .NON_USD_CURRENCY),
                Arguments.of("non-US venue and non-USD currency", AssetType.EQUITY,
                        "CA", CAD, "venue-xtsx",
                        BenchmarkAssignmentResolution.NotApplicableReason
                                .NON_US_PRIMARY_VENUE_AND_NON_USD_CURRENCY));
    }

    @Test
    void directResultConstructorsEnforceExactPairBranchAndOutputBoundary() {
        BenchmarkReferenceLevelPairResolution resolved = resolvedPair("100", "120");
        CalculationContext resolvedContext = context(resolved);
        assertThat(new BenchmarkReturnResult.Available(
                resolvedContext, new BigDecimal("0.200000000000")))
                .isNotNull();
        assertThat(new BenchmarkReturnResult.OutputUnavailable(
                resolvedContext, OutputUnavailableReason.OUTPUT_NOT_REPRESENTABLE))
                .isNotNull();

        BenchmarkReferenceLevelPairResolution evidence =
                new BenchmarkReferenceLevelPairResolution.EvidenceUnavailable(
                        pairContext(resolvedAssignment(), endpoint(
                                VENUE_ID, USD, AnchorState.USABLE)),
                        BenchmarkReferenceLevelPairResolution.UnavailableReason
                                .REFERENCE_INDEX_MISSING_AS_OF);
        CalculationContext evidenceContext = context(evidence);
        assertThatThrownBy(() -> new BenchmarkReturnResult.Available(
                evidenceContext, new BigDecimal("0.000000000000")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("resolved reference pair");
        assertThatThrownBy(() -> new BenchmarkReturnResult.EvidenceUnavailable(
                resolvedContext))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("evidence-unavailable");
        assertThatThrownBy(() -> new BenchmarkReturnResult.NotApplicable(
                resolvedContext))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("not-applicable");
        assertThatThrownBy(() -> new BenchmarkReturnResult.AssignmentUnavailable(
                resolvedContext))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("assignment-unavailable");
        assertThatThrownBy(() -> new BenchmarkReturnResult.EndpointAnchorUnavailable(
                resolvedContext))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("endpoint-anchor-unavailable");
        assertThatThrownBy(() -> new BenchmarkReturnResult.OutputUnavailable(
                evidenceContext, OutputUnavailableReason.OUTPUT_NOT_REPRESENTABLE))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("resolved reference pair");
        assertThatThrownBy(() -> new BenchmarkReturnResult.Available(
                resolvedContext, new BigDecimal("0.2")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("scale-12");
        assertThatThrownBy(() -> new BenchmarkReturnResult.Available(
                resolvedContext, new BigDecimal("-1.000000000001")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("at least -1");
        assertThatThrownBy(() -> new BenchmarkReturnResult.Available(
                resolvedContext,
                new BigDecimal("100000000000000000000000000.000000000000")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("NUMERIC(38,12)");
        assertThatThrownBy(() -> new CalculationContext(
                POLICY, "0".repeat(64), resolved))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("policyDefinitionHash");
    }

    @Test
    void rejectsEveryNullInputContextAndResultComponent() {
        BenchmarkReferenceLevelPairResolution resolved = resolvedPair("100", "120");
        CalculationContext context = context(resolved);
        List<Runnable> invalid = List.of(
                () -> new BenchmarkReturnInput(null, resolved),
                () -> new BenchmarkReturnInput(POLICY, null),
                () -> new CalculationContext(null, POLICY.definitionHash(), resolved),
                () -> new CalculationContext(POLICY, null, resolved),
                () -> new CalculationContext(POLICY, POLICY.definitionHash(), null),
                () -> new BenchmarkReturnResult.Available(null,
                        new BigDecimal("0.000000000000")),
                () -> new BenchmarkReturnResult.Available(context, null),
                () -> new BenchmarkReturnResult.NotApplicable(null),
                () -> new BenchmarkReturnResult.AssignmentUnavailable(null),
                () -> new BenchmarkReturnResult.EndpointAnchorUnavailable(null),
                () -> new BenchmarkReturnResult.EvidenceUnavailable(null),
                () -> new BenchmarkReturnResult.OutputUnavailable(null,
                        OutputUnavailableReason.OUTPUT_NOT_REPRESENTABLE),
                () -> new BenchmarkReturnResult.OutputUnavailable(context, null));

        assertThat(invalid).hasSize(13);
        for (Runnable mutation : invalid) {
            assertThatThrownBy(mutation::run)
                    .isInstanceOfAny(NullPointerException.class,
                            IllegalArgumentException.class);
        }
        assertThatThrownBy(() -> BenchmarkReturnCalculator.calculate(null))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("input");
    }

    @Test
    void closesPublicSurfaceAndReplaysWithoutLocaleOrTimezoneDependence() {
        assertThat(BenchmarkReturnPolicyVersion.values()).containsExactly(POLICY);
        assertThat(BenchmarkReturnResult.class.getPermittedSubclasses())
                .containsExactlyInAnyOrder(
                        BenchmarkReturnResult.Available.class,
                        BenchmarkReturnResult.NotApplicable.class,
                        BenchmarkReturnResult.AssignmentUnavailable.class,
                        BenchmarkReturnResult.EndpointAnchorUnavailable.class,
                        BenchmarkReturnResult.EvidenceUnavailable.class,
                        BenchmarkReturnResult.OutputUnavailable.class);
        assertThat(OutputUnavailableReason.values()).containsExactly(
                OutputUnavailableReason.OUTPUT_NOT_REPRESENTABLE);
        assertRecordComponents(BenchmarkReturnInput.class,
                "policyVersion:BenchmarkReturnPolicyVersion",
                "referenceLevelPairResolution:BenchmarkReferenceLevelPairResolution");
        assertRecordComponents(CalculationContext.class,
                "policyVersion:BenchmarkReturnPolicyVersion",
                "policyDefinitionHash:String",
                "referenceLevelPairResolution:BenchmarkReferenceLevelPairResolution");
        assertRecordComponents(BenchmarkReturnResult.Available.class,
                "context:CalculationContext", "benchmarkReturn:BigDecimal");
        assertRecordComponents(BenchmarkReturnResult.NotApplicable.class,
                "context:CalculationContext");
        assertRecordComponents(BenchmarkReturnResult.AssignmentUnavailable.class,
                "context:CalculationContext");
        assertRecordComponents(BenchmarkReturnResult.EndpointAnchorUnavailable.class,
                "context:CalculationContext");
        assertRecordComponents(BenchmarkReturnResult.EvidenceUnavailable.class,
                "context:CalculationContext");
        assertRecordComponents(BenchmarkReturnResult.OutputUnavailable.class,
                "context:CalculationContext", "reason:OutputUnavailableReason");

        BenchmarkReturnInput input = input(resolvedPair("100", "120"));
        Locale originalLocale = Locale.getDefault();
        TimeZone originalTimeZone = TimeZone.getDefault();
        try {
            BenchmarkReturnResult expected = BenchmarkReturnCalculator.calculate(input);
            Locale.setDefault(Locale.JAPAN);
            TimeZone.setDefault(TimeZone.getTimeZone("America/New_York"));
            assertThat(BenchmarkReturnCalculator.calculate(input)).isEqualTo(expected);
        } finally {
            Locale.setDefault(originalLocale);
            TimeZone.setDefault(originalTimeZone);
        }
    }

    private static BenchmarkReturnResult.Available calculateAvailable(
            String basis, String endpoint) {
        BenchmarkReferenceLevelPairResolution pair = resolvedPair(basis, endpoint);
        BenchmarkReturnResult result = calculate(pair);
        assertThat(result).isInstanceOf(BenchmarkReturnResult.Available.class);
        assertThat(resultContext(result).referenceLevelPairResolution()).isSameAs(pair);
        return (BenchmarkReturnResult.Available) result;
    }

    private static BenchmarkReturnResult calculate(
            BenchmarkReferenceLevelPairResolution pair) {
        return BenchmarkReturnCalculator.calculate(input(pair));
    }

    private static BenchmarkReturnInput input(
            BenchmarkReferenceLevelPairResolution pair) {
        return new BenchmarkReturnInput(POLICY, pair);
    }

    private static CalculationContext context(
            BenchmarkReferenceLevelPairResolution pair) {
        return new CalculationContext(POLICY, POLICY.definitionHash(), pair);
    }

    private static CalculationContext resultContext(BenchmarkReturnResult result) {
        return switch (result) {
            case BenchmarkReturnResult.Available value -> value.context();
            case BenchmarkReturnResult.NotApplicable value -> value.context();
            case BenchmarkReturnResult.AssignmentUnavailable value -> value.context();
            case BenchmarkReturnResult.EndpointAnchorUnavailable value ->
                    value.context();
            case BenchmarkReturnResult.EvidenceUnavailable value -> value.context();
            case BenchmarkReturnResult.OutputUnavailable value -> value.context();
        };
    }

    private static BenchmarkReferenceLevelPairResolution resolvedPair(
            String basisValue, String endpointValue) {
        BenchmarkAssignmentResolution.Resolved assignment = resolvedAssignment();
        EndpointPriceResolution endpoint = endpoint(
                VENUE_ID, USD, AnchorState.USABLE);
        BenchmarkAssignmentEvidence assignmentEvidence = assignment.assignmentEvidence();
        BenchmarkReferenceIndexEvidence reference = new BenchmarkReferenceIndexEvidence(
                "reference-evidence-spx", "provider-event-reference-spx",
                assignmentEvidence.assignmentEvidenceId(),
                assignmentEvidence.providerEventId(), BENCHMARK_ASSET_ID,
                AssetType.INDEX, "provider-sp", "index-spx",
                "S&P 500 Price Index", "definition-revision-2026-08",
                ReferenceIndexKind.PROVIDER_PUBLISHED_PRICE_INDEX, USD,
                "calculation-venue-sp", "calendar-spx",
                "calendar-revision-2026", "calendar-source-sp",
                "calendar-source-revision-1", "level-source-sp",
                "level-source-revision-1", "continuity-source-sp",
                "continuity-source-revision-1", "binding-source-sp",
                "binding-source-revision-1", "provenance-reference",
                new EffectiveInterval(BASIS_TIME, new OpenEnded()),
                BASIS_TIME.plusSeconds(1), BASIS_TIME.plusSeconds(1));
        BenchmarkReferenceLevelObservation basis = level(
                "basis-level", "provider-event-basis-level", reference,
                BASIS_TIME, BASIS_TIME.plusSeconds(2), new BigDecimal(basisValue));
        BenchmarkReferenceLevelObservation endpointLevel = level(
                "endpoint-level", "provider-event-endpoint-level", reference,
                ENDPOINT_CLOSE, ENDPOINT_CLOSE, new BigDecimal(endpointValue));
        BenchmarkIndexDivisorContinuityEvidence continuity =
                new BenchmarkIndexDivisorContinuityEvidence(
                        "continuity-evidence", "provider-event-continuity",
                        reference.referenceIndexEvidenceId(),
                        reference.providerEventId(), reference.benchmarkAssetId(),
                        reference.benchmarkAssetType(), reference.referenceProviderId(),
                        reference.referenceIndexId(),
                        reference.referenceIndexDefinitionRevision(),
                        reference.referenceKind(), reference.currency(),
                        reference.calculationVenueId(), reference.calendarId(),
                        reference.calendarRevision(), reference.calendarSourceId(),
                        reference.calendarSourceRevision(),
                        reference.continuitySourceId(),
                        reference.continuitySourceRevision(),
                        "provenance-continuity", basis.observationId(),
                        basis.providerEventId(), endpointLevel.observationId(),
                        endpointLevel.providerEventId(), BASIS_TIME, ENDPOINT_CLOSE,
                        DivisorContinuity
                                .PROVIDER_PUBLISHED_INDEX_DIVISOR_CONTINUITY_ATTESTED,
                        ENDPOINT_CLOSE, ENDPOINT_CLOSE);
        return new BenchmarkReferenceLevelPairResolution.Resolved(
                pairContext(assignment, endpoint), reference, basis, endpointLevel,
                continuity);
    }

    private static BenchmarkReferenceLevelObservation level(
            String observationId, String providerEventId,
            BenchmarkReferenceIndexEvidence reference, Instant observedAt,
            Instant availableAt, BigDecimal value) {
        return new BenchmarkReferenceLevelObservation(
                observationId, providerEventId,
                reference.referenceIndexEvidenceId(), reference.providerEventId(),
                reference.benchmarkAssetId(), reference.benchmarkAssetType(),
                reference.referenceProviderId(), reference.referenceIndexId(),
                reference.referenceIndexDefinitionRevision(), reference.referenceKind(),
                reference.currency(), reference.calculationVenueId(),
                reference.calendarId(), reference.calendarRevision(),
                reference.calendarSourceId(), reference.calendarSourceRevision(),
                reference.levelSourceId(), reference.levelSourceRevision(),
                "provenance-" + observationId,
                ReferenceLevelField.PROVIDER_PUBLISHED_INDEX_LEVEL, observedAt,
                availableAt, availableAt, value);
    }

    private static BenchmarkAssignmentResolution.Resolved resolvedAssignment() {
        BenchmarkAssetClassificationEvidence classification = classification(
                AssetType.EQUITY, "US", USD, VENUE_ID);
        BenchmarkAssignmentEvidence assignment = new BenchmarkAssignmentEvidence(
                "benchmark-assignment-evidence", "provider-event-assignment",
                BASIS, ASSET_ID, AssetType.EQUITY, VENUE_ID, "US", USD,
                "assignment-source", "assignment-source-revision-1",
                "provenance-assignment", assignmentInterval(), BENCHMARK_ASSET_ID,
                AssetType.INDEX, USD,
                BenchmarkReferenceKind.PROVIDER_PUBLISHED_PRICE_INDEX,
                BASIS_TIME, BASIS_TIME);
        return new BenchmarkAssignmentResolution.Resolved(
                assignmentContext(), classification, assignment);
    }

    private static BenchmarkAssetClassificationEvidence classification(
            AssetType assetType, String country, Currency currency, String venue) {
        return new BenchmarkAssetClassificationEvidence(
                "classification-evidence", "provider-event-classification", BASIS,
                ASSET_ID, assetType, venue, country, currency,
                "classification-source", "classification-source-revision-1",
                "provenance-classification", assignmentInterval(),
                BASIS_TIME, BASIS_TIME);
    }

    private static BenchmarkAssetClassificationEvidence.EffectiveInterval
            assignmentInterval() {
        return new BenchmarkAssetClassificationEvidence.EffectiveInterval(
                BASIS_TIME.minusSeconds(1),
                new BenchmarkAssetClassificationEvidence.OpenEnded());
    }

    private static BenchmarkAssignmentResolution.ResolutionContext
            assignmentContext() {
        return new BenchmarkAssignmentResolution.ResolutionContext(
                BenchmarkAssignmentPolicyVersion
                        .POINT_IN_TIME_EXPLICIT_US_EQUITY_ASSET_SPX_ASSIGNMENT_V1,
                ASSIGNMENT_HASH, BASIS, ASSET_ID, AS_OF);
    }

    private static EndpointPriceResolution endpoint(
            String venue, Currency currency, AnchorState anchorState) {
        return endpoint(venue, currency, anchorState, ENDPOINT_CLOSE);
    }

    private static EndpointPriceResolution endpoint(
            String venue, Currency currency, AnchorState anchorState,
            Instant endpointClose) {
        TradingSession session = new TradingSession(
                "session-endpoint", endpointClose.minusSeconds(23_400),
                endpointClose);
        var horizonContext = new SessionCloseHorizonResolution.ResolutionContext(
                SessionCloseHorizonPolicyVersion
                        .STRICTLY_AFTER_BASIS_EVENT_SESSION_CLOSE_V1,
                HORIZON_HASH, BASIS, OutcomeHorizon.D1, 1, "calendar-primary",
                "catalog-revision-1");
        var horizon = new SessionCloseHorizonResolution.Resolved(
                new SessionCloseHorizonResolution.ResolvedSessionWindow(
                        horizonContext, List.of(session), session));
        String catalogCalendar = anchorState == AnchorState.MISMATCHED_CATALOG
                ? "calendar-other" : "calendar-primary";
        Instant catalogKnown = anchorState == AnchorState.FUTURE_CATALOG
                ? AS_OF.plusSeconds(1) : BASIS_TIME;
        Instant bindingKnown = anchorState == AnchorState.FUTURE_BINDING
                ? AS_OF.plusSeconds(1) : BASIS_TIME;
        var endpointContext = new EndpointPriceResolution.ResolutionContext(
                EndpointPricePolicyVersion
                        .OFFICIAL_PRIMARY_VENUE_CLOSE_SPLIT_ADJUSTED_V1,
                ENDPOINT_HASH, horizon,
                new CatalogPointInTimeEvidence(catalogCalendar,
                        "catalog-revision-1", "calendar-source",
                        "calendar-source-revision-1", catalogKnown, catalogKnown,
                        "provenance-calendar"),
                new EndpointPriceBinding("binding-primary", "binding-revision-1",
                        ASSET_ID, venue, currency, "price-source",
                        "price-source-revision-1", bindingKnown, bindingKnown,
                        "provenance-binding"), AS_OF);
        if (anchorState != AnchorState.USABLE || endpointClose.isAfter(AS_OF)) {
            return new EndpointPriceResolution.Unavailable(
                    endpointContext,
                    EndpointPriceResolution.UnavailableReason
                            .OBSERVATION_MISSING_AS_OF);
        }
        EndpointPriceObservation observation = new EndpointPriceObservation(
                "endpoint-price-observation", "provider-event-endpoint-price",
                ASSET_ID, venue, currency, "price-source",
                "price-source-revision-1", "provenance-endpoint-price",
                "calendar-primary", "catalog-revision-1", "session-endpoint",
                EndpointPriceField.OFFICIAL_REGULAR_SESSION_CLOSE,
                EndpointPriceAdjustmentBasis
                        .SPLIT_AND_REVERSE_SPLIT_ADJUSTED_TO_ENDPOINT_SHARE_BASIS_DIVIDEND_UNADJUSTED,
                CorporateActionContinuity.SPLIT_REVERSE_SPLIT_CONTINUOUS,
                endpointClose, endpointClose, endpointClose,
                new BigDecimal("100.000000000000"));
        return new EndpointPriceResolution.Resolved(endpointContext, observation);
    }

    private static BenchmarkReferenceLevelPairResolution.ResolutionContext pairContext(
            BenchmarkAssignmentResolution assignment,
            EndpointPriceResolution endpoint) {
        return new BenchmarkReferenceLevelPairResolution.ResolutionContext(
                PAIR_POLICY, PAIR_HASH, assignment, endpoint);
    }

    private static void assertRecordComponents(Class<?> type, String... expected) {
        assertThat(Stream.of(type.getRecordComponents())
                .map(component -> component.getName() + ":"
                        + component.getType().getSimpleName()))
                .containsExactly(expected);
    }

    private enum AnchorState {
        USABLE,
        FUTURE_CATALOG,
        MISMATCHED_CATALOG,
        FUTURE_BINDING
    }
}
