package com.wallstreetreceipts.api.domain.outcome.sectorreturn;

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
import com.wallstreetreceipts.api.domain.outcome.sectorassignment.SectorAssetClassificationEvidence;
import com.wallstreetreceipts.api.domain.outcome.sectorassignment.SectorAssignmentPolicyVersion;
import com.wallstreetreceipts.api.domain.outcome.sectorassignment.SectorAssignmentResolution;
import com.wallstreetreceipts.api.domain.outcome.sectorassignment.SectorMappingEvidence;
import com.wallstreetreceipts.api.domain.outcome.sectorassignment.SectorMappingEvidence.Mapped;
import com.wallstreetreceipts.api.domain.outcome.sectorassignment.SectorMappingEvidence.Recorded;
import com.wallstreetreceipts.api.domain.outcome.sectorassignment.SectorMembershipEvidence;
import com.wallstreetreceipts.api.domain.outcome.sectorreferencepair.SectorIndexDivisorContinuityEvidence;
import com.wallstreetreceipts.api.domain.outcome.sectorreferencepair.SectorIndexDivisorContinuityEvidence.DivisorContinuity;
import com.wallstreetreceipts.api.domain.outcome.sectorreferencepair.SectorReferenceIndexEvidence;
import com.wallstreetreceipts.api.domain.outcome.sectorreferencepair.SectorReferenceIndexEvidence.EffectiveInterval;
import com.wallstreetreceipts.api.domain.outcome.sectorreferencepair.SectorReferenceIndexEvidence.OpenEnded;
import com.wallstreetreceipts.api.domain.outcome.sectorreferencepair.SectorReferenceIndexEvidence.ReferenceIndexKind;
import com.wallstreetreceipts.api.domain.outcome.sectorreferencepair.SectorReferenceLevelObservation;
import com.wallstreetreceipts.api.domain.outcome.sectorreferencepair.SectorReferenceLevelObservation.ReferenceLevelField;
import com.wallstreetreceipts.api.domain.outcome.sectorreferencepair.SectorReferenceLevelPairPolicyVersion;
import com.wallstreetreceipts.api.domain.outcome.sectorreferencepair.SectorReferenceLevelPairResolution;
import com.wallstreetreceipts.api.domain.outcome.sectorreturn.SectorReturnResult.CalculationContext;
import com.wallstreetreceipts.api.domain.outcome.sectorreturn.SectorReturnResult.OutputUnavailableReason;

class SectorReturnCalculatorGoldenTest {

    private static final SectorReturnPolicyVersion POLICY =
            SectorReturnPolicyVersion
                    .SIGNED_SECTOR_BASIS_LEVEL_DENOMINATOR_SCALE_12_HALF_EVEN_V1;
    private static final SectorReferenceLevelPairPolicyVersion PAIR_POLICY =
            SectorReferenceLevelPairPolicyVersion
                    .POINT_IN_TIME_EXACT_SECTOR_PRICE_INDEX_LEVEL_PAIR_V1;
    private static final SectorAssignmentPolicyVersion ASSIGNMENT_POLICY =
            SectorAssignmentPolicyVersion
                    .POINT_IN_TIME_EXPLICIT_WSR_ECONOMIC_ACTIVITY_SECTOR_ASSIGNMENT_V1;
    private static final String POLICY_HASH =
            "5aecd42c32ba69f0d21ab6e1ee1e3128cd31584724a6f46e176acd470204d0f7";
    private static final String PAIR_HASH =
            "4224648ba01104fd3e96319158c7d6b42da472e9aa6f2ab22ef9fccf43da7e4a";
    private static final String ASSIGNMENT_HASH =
            "52d9f705a3a8a965a6fca79d36bd94ed8836642f1a2c4e5f29a878d0a267311c";
    private static final String ENDPOINT_HASH =
            "37e37aba9302d77366cef4129f77a82b7ccb2f1937bfffc0315ea8d0bc6b1f76";
    private static final String HORIZON_HASH =
            "550087efe7ddf2ba31974c89c2740ab79df986eefef48919c32c56a3232f8dc1";
    private static final String ASSET_ID = "asset-nvda";
    private static final String CANONICAL_NODE_ID =
            "wsr-sector-digital-systems";
    private static final String REFERENCE_ASSET_ID =
            "asset-wsr-digital-systems-price-index";
    private static final String VENUE_ID = "venue-xnas";
    private static final Currency USD = Currency.getInstance("USD");
    private static final Instant BASIS_TIME =
            Instant.parse("2026-08-20T14:00:00Z");
    private static final Instant ENDPOINT_CLOSE =
            Instant.parse("2026-08-21T20:00:00Z");
    private static final Instant AS_OF =
            Instant.parse("2026-08-22T00:00:00Z");
    private static final OutcomeBasis BASIS =
            new OutcomeBasis.Original("call-adr032-sector", BASIS_TIME);
    private static final String MAPPING_SET_ID = "mapping-set-synthetic";
    private static final String MAPPING_SET_VERSION = "1.0.0";
    private static final String MAPPING_SET_HASH = "a".repeat(64);
    private static final String MAPPING_POLICY_VERSION =
            "POINT_IN_TIME_EXPLICIT_PROVIDER_NODE_TO_WSR_ECONOMIC_ACTIVITY_V1";
    private static final String MAPPING_POLICY_HASH =
            "ba12a277d5ffe266af1745b98948a1e2206494ac31904f31a419d973d5067e77";
    private static final String TAXONOMY_ID = "wsr-economic-activity";
    private static final String TAXONOMY_VERSION = "1.0.0";
    private static final String TAXONOMY_HASH =
            "820ce3ea264d67312fe4f2efe346631a81d74248e9a7f041793d65d8ef0d62ae";
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
        assertThat(first).hasSize(2817);
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
                .contains("\"assetReturnResultReuse\":\"FORBIDDEN\"")
                .contains("\"benchmarkReturnResultReuse\":\"FORBIDDEN\"");

        first[0] = (byte) '!';
        assertThat(POLICY.canonicalDefinitionUtf8()[0]).isEqualTo((byte) '{');
    }

    @ParameterizedTest(name = "sector return {0}")
    @MethodSource("primaryFormulaVectors")
    void calculatesSignedPositiveNegativeAndZeroReturnsWithBasisLevelDenominator(
            String label, String basis, String endpoint, String expected) {
        SectorReturnResult.Available result = calculateAvailable(basis, endpoint);

        assertThat(result.sectorReturn()).isEqualTo(new BigDecimal(expected));
        assertThat(result.sectorReturn().scale()).isEqualTo(12);
        assertThat(result.context().referenceLevelPairResolution())
                .isInstanceOf(SectorReferenceLevelPairResolution.Resolved.class);
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
        SectorReturnResult.Available scaleZero = calculateAvailable("100", "120");
        SectorReturnResult.Available scaleTwelve = calculateAvailable(
                "100.000000000000", "120.000000000000");

        assertThat(scaleTwelve.sectorReturn())
                .isEqualTo(scaleZero.sectorReturn())
                .isEqualTo(new BigDecimal("0.200000000000"));
        assertThat(scaleZero.sectorReturn().scale()).isEqualTo(12);
        assertThat(scaleTwelve.sectorReturn().scale()).isEqualTo(12);
    }

    @ParameterizedTest(name = "half-even tie {0}")
    @MethodSource("halfEvenTieVectors")
    void roundsTheSingleDivisionAtPositiveAndNegativeExactHalfEvenTies(
            String label, String endpoint, String expected) {
        SectorReturnResult.Available result = calculateAvailable(
                "2.000000000000", endpoint);

        assertThat(result.sectorReturn()).isEqualTo(new BigDecimal(expected));
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
        SectorReturnResult.Available result = calculateAvailable(
                MAX_NUMERIC, "0.000000000001");

        assertThat(result.sectorReturn())
                .isEqualTo(new BigDecimal("-1.000000000000"));
        assertThat(result.sectorReturn().scale()).isEqualTo(12);
    }

    @Test
    void acceptsExactPrecision38OutputAndTypesRoundedPrecision39AsUnavailable() {
        SectorReturnResult.Available maximum = calculateAvailable("1", MAX_NUMERIC);
        assertThat(maximum.sectorReturn()).isEqualTo(new BigDecimal(
                "99999999999999999999999998.999999999999"));
        assertThat(maximum.sectorReturn().precision()).isEqualTo(38);

        SectorReferenceLevelPairResolution pair = resolvedPair("0.1", MAX_NUMERIC);
        SectorReturnResult overflow = calculate(pair);
        assertThat(overflow).isEqualTo(new SectorReturnResult.OutputUnavailable(
                context(pair), OutputUnavailableReason.OUTPUT_NOT_REPRESENTABLE));
        assertThat(resultContext(overflow).referenceLevelPairResolution())
                .isSameAs(pair);
    }

    @ParameterizedTest(name = "evidence reason {0}")
    @EnumSource(SectorReferenceLevelPairResolution.UnavailableReason.class)
    void propagatesEveryEvidenceReasonOnlyInsideTheCompletePairReceipt(
            SectorReferenceLevelPairResolution.UnavailableReason reason) {
        EndpointPriceResolution endpoint = reason
                == SectorReferenceLevelPairResolution.UnavailableReason
                        .ENDPOINT_NOT_REACHED_AS_OF
                ? endpoint(VENUE_ID, USD, AnchorState.USABLE,
                        AS_OF.plusSeconds(60))
                : endpoint(VENUE_ID, USD, AnchorState.USABLE);
        SectorReferenceLevelPairResolution pair =
                new SectorReferenceLevelPairResolution.EvidenceUnavailable(
                        pairContext(resolvedAssignment(), endpoint), reason);

        SectorReturnResult result = calculate(pair);

        assertThat(result).isEqualTo(
                new SectorReturnResult.EvidenceUnavailable(context(pair)));
        assertThat(resultContext(result).referenceLevelPairResolution()).isSameAs(pair);
    }

    @ParameterizedTest(name = "anchor reason {0}")
    @EnumSource(SectorReferenceLevelPairResolution.EndpointAnchorUnavailableReason.class)
    void propagatesEveryEndpointAnchorReasonOnlyInsideTheCompletePairReceipt(
            SectorReferenceLevelPairResolution.EndpointAnchorUnavailableReason reason) {
        AnchorState state = switch (reason) {
            case CATALOG_NOT_KNOWN_AS_OF -> AnchorState.FUTURE_CATALOG;
            case CATALOG_EVIDENCE_MISMATCH -> AnchorState.MISMATCHED_CATALOG;
            case BINDING_NOT_KNOWN_AS_OF -> AnchorState.FUTURE_BINDING;
        };
        SectorReferenceLevelPairResolution pair =
                new SectorReferenceLevelPairResolution.EndpointAnchorUnavailable(
                        pairContext(resolvedAssignment(), endpoint(VENUE_ID, USD, state)),
                        reason);

        SectorReturnResult result = calculate(pair);

        assertThat(result).isEqualTo(
                new SectorReturnResult.EndpointAnchorUnavailable(context(pair)));
        assertThat(resultContext(result).referenceLevelPairResolution()).isSameAs(pair);
    }

    @ParameterizedTest(name = "assignment reason {0}")
    @EnumSource(SectorAssignmentResolution.UnavailableReason.class)
    void propagatesEveryAssignmentReasonOnlyInsideTheCompletePairReceipt(
            SectorAssignmentResolution.UnavailableReason reason) {
        SectorAssignmentResolution assignment =
                new SectorAssignmentResolution.Unavailable(
                        assignmentContext(), reason);
        SectorReferenceLevelPairResolution pair =
                new SectorReferenceLevelPairResolution.AssignmentUnavailable(
                        pairContext(assignment, endpoint(
                                VENUE_ID, USD, AnchorState.USABLE)));

        SectorReturnResult result = calculate(pair);

        assertThat(result).isEqualTo(
                new SectorReturnResult.AssignmentUnavailable(context(pair)));
        assertThat(resultContext(result).referenceLevelPairResolution()).isSameAs(pair);
    }

    @ParameterizedTest(name = "not applicable {0}")
    @EnumSource(SectorAssignmentResolution.NotApplicableReason.class)
    void propagatesEveryNotApplicableBranchOnlyInsideTheCompletePairReceipt(
            SectorAssignmentResolution.NotApplicableReason reason) {
        SectorAssetClassificationEvidence classification = classification(
                AssetType.INDEX, "US", USD, VENUE_ID);
        SectorAssignmentResolution assignment =
                new SectorAssignmentResolution.NotApplicable(
                        assignmentContext(), classification, reason);
        SectorReferenceLevelPairResolution pair =
                new SectorReferenceLevelPairResolution.NotApplicable(
                        pairContext(assignment, endpoint(
                                VENUE_ID, USD, AnchorState.USABLE)));

        SectorReturnResult result = calculate(pair);

        assertThat(result).isEqualTo(
                new SectorReturnResult.NotApplicable(context(pair)));
        assertThat(resultContext(result).referenceLevelPairResolution()).isSameAs(pair);
    }

    @Test
    void directResultConstructorsEnforceExactPairBranchAndOutputBoundary() {
        SectorReferenceLevelPairResolution resolved = resolvedPair("100", "120");
        CalculationContext resolvedContext = context(resolved);
        assertThat(new SectorReturnResult.Available(
                resolvedContext, new BigDecimal("0.200000000000")))
                .isNotNull();
        assertThat(new SectorReturnResult.OutputUnavailable(
                resolvedContext, OutputUnavailableReason.OUTPUT_NOT_REPRESENTABLE))
                .isNotNull();

        SectorReferenceLevelPairResolution evidence =
                new SectorReferenceLevelPairResolution.EvidenceUnavailable(
                        pairContext(resolvedAssignment(), endpoint(
                                VENUE_ID, USD, AnchorState.USABLE)),
                        SectorReferenceLevelPairResolution.UnavailableReason
                                .REFERENCE_INDEX_MISSING_AS_OF);
        CalculationContext evidenceContext = context(evidence);
        assertThatThrownBy(() -> new SectorReturnResult.Available(
                evidenceContext, new BigDecimal("0.000000000000")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("resolved reference pair");
        assertThatThrownBy(() -> new SectorReturnResult.EvidenceUnavailable(
                resolvedContext))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("evidence-unavailable");
        assertThatThrownBy(() -> new SectorReturnResult.NotApplicable(
                resolvedContext))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("not-applicable");
        assertThatThrownBy(() -> new SectorReturnResult.AssignmentUnavailable(
                resolvedContext))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("assignment-unavailable");
        assertThatThrownBy(() -> new SectorReturnResult.EndpointAnchorUnavailable(
                resolvedContext))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("endpoint-anchor-unavailable");
        assertThatThrownBy(() -> new SectorReturnResult.OutputUnavailable(
                evidenceContext, OutputUnavailableReason.OUTPUT_NOT_REPRESENTABLE))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("resolved reference pair");
        assertThatThrownBy(() -> new SectorReturnResult.Available(
                resolvedContext, new BigDecimal("0.2")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("scale-12");
        assertThatThrownBy(() -> new SectorReturnResult.Available(
                resolvedContext, new BigDecimal("-1.000000000001")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("at least -1");
        assertThatThrownBy(() -> new SectorReturnResult.Available(
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
        SectorReferenceLevelPairResolution resolved = resolvedPair("100", "120");
        CalculationContext context = context(resolved);
        List<Runnable> invalid = List.of(
                () -> new SectorReturnInput(null, resolved),
                () -> new SectorReturnInput(POLICY, null),
                () -> new CalculationContext(null, POLICY.definitionHash(), resolved),
                () -> new CalculationContext(POLICY, null, resolved),
                () -> new CalculationContext(POLICY, POLICY.definitionHash(), null),
                () -> new SectorReturnResult.Available(null,
                        new BigDecimal("0.000000000000")),
                () -> new SectorReturnResult.Available(context, null),
                () -> new SectorReturnResult.NotApplicable(null),
                () -> new SectorReturnResult.AssignmentUnavailable(null),
                () -> new SectorReturnResult.EndpointAnchorUnavailable(null),
                () -> new SectorReturnResult.EvidenceUnavailable(null),
                () -> new SectorReturnResult.OutputUnavailable(null,
                        OutputUnavailableReason.OUTPUT_NOT_REPRESENTABLE),
                () -> new SectorReturnResult.OutputUnavailable(context, null));

        assertThat(invalid).hasSize(13);
        for (Runnable mutation : invalid) {
            assertThatThrownBy(mutation::run)
                    .isInstanceOfAny(NullPointerException.class,
                            IllegalArgumentException.class);
        }
        assertThatThrownBy(() -> SectorReturnCalculator.calculate(null))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("input");
    }

    @Test
    void closesPublicSurfaceAndReplaysWithoutLocaleOrTimezoneDependence() {
        assertThat(SectorReturnPolicyVersion.values()).containsExactly(POLICY);
        assertThat(SectorReturnResult.class.getPermittedSubclasses())
                .containsExactlyInAnyOrder(
                        SectorReturnResult.Available.class,
                        SectorReturnResult.NotApplicable.class,
                        SectorReturnResult.AssignmentUnavailable.class,
                        SectorReturnResult.EndpointAnchorUnavailable.class,
                        SectorReturnResult.EvidenceUnavailable.class,
                        SectorReturnResult.OutputUnavailable.class);
        assertThat(OutputUnavailableReason.values()).containsExactly(
                OutputUnavailableReason.OUTPUT_NOT_REPRESENTABLE);
        assertRecordComponents(SectorReturnInput.class,
                "policyVersion:SectorReturnPolicyVersion",
                "referenceLevelPairResolution:SectorReferenceLevelPairResolution");
        assertRecordComponents(CalculationContext.class,
                "policyVersion:SectorReturnPolicyVersion",
                "policyDefinitionHash:String",
                "referenceLevelPairResolution:SectorReferenceLevelPairResolution");
        assertRecordComponents(SectorReturnResult.Available.class,
                "context:CalculationContext", "sectorReturn:BigDecimal");
        assertRecordComponents(SectorReturnResult.NotApplicable.class,
                "context:CalculationContext");
        assertRecordComponents(SectorReturnResult.AssignmentUnavailable.class,
                "context:CalculationContext");
        assertRecordComponents(SectorReturnResult.EndpointAnchorUnavailable.class,
                "context:CalculationContext");
        assertRecordComponents(SectorReturnResult.EvidenceUnavailable.class,
                "context:CalculationContext");
        assertRecordComponents(SectorReturnResult.OutputUnavailable.class,
                "context:CalculationContext", "reason:OutputUnavailableReason");

        SectorReturnInput input = input(resolvedPair("100", "120"));
        Locale originalLocale = Locale.getDefault();
        TimeZone originalTimeZone = TimeZone.getDefault();
        try {
            SectorReturnResult expected = SectorReturnCalculator.calculate(input);
            Locale.setDefault(Locale.JAPAN);
            TimeZone.setDefault(TimeZone.getTimeZone("America/New_York"));
            assertThat(SectorReturnCalculator.calculate(input)).isEqualTo(expected);
        } finally {
            Locale.setDefault(originalLocale);
            TimeZone.setDefault(originalTimeZone);
        }
    }

    private static SectorReturnResult.Available calculateAvailable(
            String basis, String endpoint) {
        SectorReferenceLevelPairResolution pair = resolvedPair(basis, endpoint);
        SectorReturnResult result = calculate(pair);
        assertThat(result).isInstanceOf(SectorReturnResult.Available.class);
        assertThat(resultContext(result).referenceLevelPairResolution()).isSameAs(pair);
        return (SectorReturnResult.Available) result;
    }

    private static SectorReturnResult calculate(
            SectorReferenceLevelPairResolution pair) {
        return SectorReturnCalculator.calculate(input(pair));
    }

    private static SectorReturnInput input(
            SectorReferenceLevelPairResolution pair) {
        return new SectorReturnInput(POLICY, pair);
    }

    private static CalculationContext context(
            SectorReferenceLevelPairResolution pair) {
        return new CalculationContext(POLICY, POLICY.definitionHash(), pair);
    }

    private static CalculationContext resultContext(SectorReturnResult result) {
        return switch (result) {
            case SectorReturnResult.Available value -> value.context();
            case SectorReturnResult.NotApplicable value -> value.context();
            case SectorReturnResult.AssignmentUnavailable value -> value.context();
            case SectorReturnResult.EndpointAnchorUnavailable value ->
                    value.context();
            case SectorReturnResult.EvidenceUnavailable value -> value.context();
            case SectorReturnResult.OutputUnavailable value -> value.context();
        };
    }

    private static SectorReferenceLevelPairResolution resolvedPair(
            String basisValue, String endpointValue) {
        SectorAssignmentResolution.Resolved assignment = resolvedAssignment();
        EndpointPriceResolution endpoint = endpoint(
                VENUE_ID, USD, AnchorState.USABLE);
        SectorMappingEvidence mappingEvidence = assignment.mappingEvidence();
        String canonicalNodeId = ((Mapped) mappingEvidence.mappingDisposition())
                .canonicalNodeId();
        SectorReferenceIndexEvidence reference = new SectorReferenceIndexEvidence(
                "reference-evidence-sector",
                "provider-event-reference-sector",
                mappingEvidence.mappingEvidenceId(),
                mappingEvidence.providerEventId(), mappingEvidence.taxonomyId(),
                mappingEvidence.taxonomyVersion(),
                mappingEvidence.taxonomyDefinitionHash(), canonicalNodeId,
                REFERENCE_ASSET_ID, AssetType.INDEX, "provider-sector-index",
                "index-sector-" + canonicalNodeId,
                "Provider sector price index", "definition-revision-2026-08",
                ReferenceIndexKind.PROVIDER_PUBLISHED_PRICE_INDEX, USD,
                "calculation-venue-sector", "calendar-sector",
                "calendar-revision-2026", "calendar-source-sector",
                "calendar-source-revision-1", "level-source-sector",
                "level-source-revision-1", "continuity-source-sector",
                "continuity-source-revision-1", "binding-source-sector",
                "binding-source-revision-1", "provenance-reference",
                new EffectiveInterval(BASIS_TIME, new OpenEnded()),
                BASIS_TIME.plusSeconds(1), BASIS_TIME.plusSeconds(1));
        SectorReferenceLevelObservation basis = level(
                "basis-level", "provider-event-basis-level", reference,
                BASIS_TIME, BASIS_TIME.plusSeconds(2), new BigDecimal(basisValue));
        SectorReferenceLevelObservation endpointLevel = level(
                "endpoint-level", "provider-event-endpoint-level", reference,
                ENDPOINT_CLOSE, ENDPOINT_CLOSE, new BigDecimal(endpointValue));
        SectorIndexDivisorContinuityEvidence continuity =
                new SectorIndexDivisorContinuityEvidence(
                        "continuity-evidence", "provider-event-continuity",
                        reference.referenceIndexEvidenceId(),
                        reference.providerEventId(), reference.referenceAssetId(),
                        reference.referenceAssetType(),
                        reference.referenceProviderId(), reference.referenceIndexId(),
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
        return new SectorReferenceLevelPairResolution.Resolved(
                pairContext(assignment, endpoint), reference, basis, endpointLevel,
                continuity);
    }

    private static SectorReferenceLevelObservation level(
            String observationId, String providerEventId,
            SectorReferenceIndexEvidence reference, Instant observedAt,
            Instant availableAt, BigDecimal value) {
        return new SectorReferenceLevelObservation(
                observationId, providerEventId,
                reference.referenceIndexEvidenceId(), reference.providerEventId(),
                reference.referenceAssetId(), reference.referenceAssetType(),
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

    private static SectorAssignmentResolution.Resolved resolvedAssignment() {
        SectorAssetClassificationEvidence classification = classification(
                AssetType.EQUITY, "US", USD, VENUE_ID);
        SectorMembershipEvidence membership = new SectorMembershipEvidence(
                "sector-membership-evidence", "provider-event-membership",
                BASIS, ASSET_ID, AssetType.EQUITY, VENUE_ID, "US", USD,
                "membership-provider", "economic-activity-scheme",
                "scheme-revision-2026", "provider-node-computing",
                "Provider computing node", "membership-source",
                "membership-source-revision-1", "provenance-membership",
                assignmentInterval(), BASIS_TIME, BASIS_TIME);
        SectorMappingEvidence mapping = new SectorMappingEvidence(
                "sector-mapping-evidence", "provider-event-mapping",
                MAPPING_POLICY_VERSION, MAPPING_POLICY_HASH, MAPPING_SET_ID,
                MAPPING_SET_VERSION, MAPPING_SET_HASH, TAXONOMY_ID,
                TAXONOMY_VERSION, TAXONOMY_HASH, membership.providerId(),
                membership.providerSchemeId(), membership.providerSchemeRevision(),
                membership.providerNodeId(), "Mapping label",
                new Recorded("Provider-published definition", "en"),
                new Mapped(CANONICAL_NODE_ID), "mapping-source",
                "mapping-source-revision-1", "provenance-mapping",
                assignmentInterval(), BASIS_TIME, BASIS_TIME);
        return new SectorAssignmentResolution.Resolved(
                assignmentContext(), classification, membership, mapping);
    }

    private static SectorAssetClassificationEvidence classification(
            AssetType assetType, String country, Currency currency, String venue) {
        return new SectorAssetClassificationEvidence(
                "classification-evidence", "provider-event-classification", BASIS,
                ASSET_ID, assetType, venue, country, currency,
                "classification-source", "classification-source-revision-1",
                "provenance-classification", assignmentInterval(),
                BASIS_TIME, BASIS_TIME);
    }

    private static SectorAssetClassificationEvidence.EffectiveInterval
            assignmentInterval() {
        return new SectorAssetClassificationEvidence.EffectiveInterval(
                BASIS_TIME.minusSeconds(1),
                new SectorAssetClassificationEvidence.OpenEnded());
    }

    private static SectorAssignmentResolution.ResolutionContext
            assignmentContext() {
        return new SectorAssignmentResolution.ResolutionContext(
                ASSIGNMENT_POLICY, ASSIGNMENT_HASH, BASIS, ASSET_ID, AS_OF,
                MAPPING_SET_ID, MAPPING_SET_VERSION, MAPPING_SET_HASH);
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

    private static SectorReferenceLevelPairResolution.ResolutionContext pairContext(
            SectorAssignmentResolution assignment,
            EndpointPriceResolution endpoint) {
        return new SectorReferenceLevelPairResolution.ResolutionContext(
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
