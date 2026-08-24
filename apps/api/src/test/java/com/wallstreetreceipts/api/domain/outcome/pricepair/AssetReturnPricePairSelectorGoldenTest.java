package com.wallstreetreceipts.api.domain.outcome.pricepair;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Currency;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
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
import com.wallstreetreceipts.api.domain.outcome.pricepair.AssetReturnPricePairResolution.ResolutionContext;
import com.wallstreetreceipts.api.domain.outcome.pricepair.AssetReturnPricePairResolution.Resolved;
import com.wallstreetreceipts.api.domain.outcome.pricepair.AssetReturnPricePairResolution.Unavailable;
import com.wallstreetreceipts.api.domain.outcome.pricepair.AssetReturnPricePairResolution.UnavailableReason;

class AssetReturnPricePairSelectorGoldenTest {

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
    private static final String PAIR_HASH =
            "895e4bc97ebb3a92b80f2c58e2d28abb94440eeca963046ee755fa98825f4887";
    private static final String ENDPOINT_HASH =
            "37e37aba9302d77366cef4129f77a82b7ccb2f1937bfffc0315ea8d0bc6b1f76";
    private static final String HORIZON_HASH =
            "550087efe7ddf2ba31974c89c2740ab79df986eefef48919c32c56a3232f8dc1";

    @Test
    void canonicalPolicyDefinitionHasStableExactUtf8BytesAndIndependentSha256()
            throws NoSuchAlgorithmException {
        AssetReturnPricePairPolicyVersion policy = policy();
        byte[] firstRead = policy.canonicalDefinitionUtf8();
        String independentHash = HexFormat.of().formatHex(
                MessageDigest.getInstance("SHA-256").digest(firstRead));

        assertThat(firstRead).hasSize(4655);
        assertThat(independentHash).isEqualTo(PAIR_HASH);
        assertThat(policy.definitionHash()).isEqualTo(PAIR_HASH);
        assertThat(new String(firstRead, StandardCharsets.UTF_8))
                .isEqualTo(policy.canonicalDefinition());
        assertThat(policy.canonicalDefinition())
                .contains("\"requiredEndpointPolicyDefinitionHash\":\"" + ENDPOINT_HASH + "\"")
                .contains("\"futureBasisRule\":\"INVISIBLE_TO_ALL_OUTPUT_AND_REASONING\"")
                .contains("\"futureAdjustmentRule\":\"INVISIBLE_TO_ALL_OUTPUT_AND_REASONING\"")
                .contains("\"basisTemporalRule\":\"observedAt<=availableAt<=capturedAt\"")
                .contains("\"adjustmentTemporalRule\":\"coverageStartsAt<=coverageEndsAt<=availableAt<=capturedAt\"")
                .contains("\"BASIS_PRICE_AMBIGUOUS\"")
                .contains("\"ADJUSTMENT_EVIDENCE_AMBIGUOUS\"")
                .contains("\"resultContext\":\"POLICY_IDENTITY_AND_COMPLETE_ENDPOINT_PRICE_RESOLUTION_ONLY\"")
                .contains("\"fallbackBehavior\":\"ABSENT\"");

        firstRead[0] = (byte) '!';
        assertThat(policy.canonicalDefinitionUtf8()[0]).isEqualTo((byte) '{');
    }

    @Test
    void resolvesOneExactKnownPairAtInclusivePitBoundariesAndPreservesEvidence() {
        PairFixture fixture = fixture(new Original("call-001", BASIS_TIME),
                "183.4200", "235.000000000000");
        BasisPriceObservation boundaryBasis = basisCandidate(
                "boundary", fixture.basis().basis(), ASSET_ID, VENUE_ID, USD,
                PRICE_SOURCE_ID, PRICE_SOURCE_REVISION,
                BasisPriceField.SOURCE_RECORDED_BASIS_EVENT_PRICE,
                REQUIRED_ADJUSTMENT,
                CorporateActionContinuity.SPLIT_REVERSE_SPLIT_CONTINUOUS,
                BASIS_TIME, AS_OF, AS_OF, "183.4200");
        PricePairAdjustmentEvidence boundaryAdjustment = adjustmentCandidate(
                "boundary", boundaryBasis, fixture.endpoint(),
                boundaryBasis.basis(), ASSET_ID, VENUE_ID, USD,
                boundaryBasis.observationId(), boundaryBasis.providerEventId(),
                endpointObservation(fixture.endpoint()).observationId(),
                endpointObservation(fixture.endpoint()).providerEventId(),
                boundaryBasis.observedAt(), endpointObservation(fixture.endpoint()).observedAt(),
                REQUIRED_ADJUSTMENT,
                CorporateActionContinuity.SPLIT_REVERSE_SPLIT_CONTINUOUS,
                AS_OF, AS_OF);

        AssetReturnPricePairRequest request = request(
                fixture.endpoint(), List.of(boundaryBasis), List.of(boundaryAdjustment));
        Resolved result = (Resolved) AssetReturnPricePairSelector.select(request);

        assertThat(result.context()).isEqualTo(context(fixture.endpoint()));
        assertThat(result.context().endpointPriceResolution()).isSameAs(fixture.endpoint());
        assertThat(result.basisObservation()).isSameAs(boundaryBasis);
        assertThat(result.adjustmentEvidence()).isSameAs(boundaryAdjustment);
        assertThat(result.basisObservation().price()).isEqualTo(new BigDecimal("183.4200"));
        assertThat(result.basisObservation().price().scale()).isEqualTo(4);
        assertThat(result.adjustmentEvidence().adjustmentSourceId())
                .isEqualTo("source-corporate-actions");
        assertThat(result.adjustmentEvidence().adjustmentSourceRevision())
                .isEqualTo("adjustment-revision-9");
        assertThat(result.basisObservation().provenanceId())
                .isEqualTo("provenance-basis-boundary");
        assertThat(result.adjustmentEvidence().provenanceId())
                .isEqualTo("provenance-adjustment-boundary");
        assertThat(endpointObservation(fixture.endpoint()).provenanceId())
                .isEqualTo("provenance-endpoint");
    }

    @Test
    void basisCandidatesArePitFilteredBeforeAllIdentityAndCardinalityChecks() {
        PairFixture fixture = fixture();
        AssetReturnPricePairResolution baseline = select(fixture);
        BasisPriceObservation futureWrong = basisCandidate(
                "future-wrong", new Correction("wrong-call", "revision-wrong", BASIS_TIME),
                "wrong-asset", "wrong-venue", Currency.getInstance("EUR"),
                "wrong-source", "wrong-revision", BasisPriceField.INDICATIVE_OR_OTHER,
                EndpointPriceAdjustmentBasis.DIVIDEND_OR_TOTAL_RETURN_ADJUSTED,
                CorporateActionContinuity.MERGER, BASIS_TIME.plusSeconds(1),
                AS_OF.plusNanos(1_000), AS_OF.plusNanos(2_000), "1");
        BasisPriceObservation capturedFuture = basisCandidate(
                "captured-future", fixture.basis().basis(), ASSET_ID, VENUE_ID, USD,
                PRICE_SOURCE_ID, PRICE_SOURCE_REVISION,
                BasisPriceField.SOURCE_RECORDED_BASIS_EVENT_PRICE,
                REQUIRED_ADJUSTMENT,
                CorporateActionContinuity.SPLIT_REVERSE_SPLIT_CONTINUOUS,
                BASIS_TIME, AS_OF, AS_OF.plusNanos(1_000), "183.42");

        assertThat(AssetReturnPricePairSelector.select(request(
                fixture.endpoint(), List.of(fixture.basis(), futureWrong),
                List.of(fixture.adjustment())))).isEqualTo(baseline);
        assertThat(AssetReturnPricePairSelector.select(request(
                fixture.endpoint(), List.of(fixture.basis(), capturedFuture),
                List.of(fixture.adjustment())))).isEqualTo(baseline);

        AssetReturnPricePairResolution empty = AssetReturnPricePairSelector.select(request(
                fixture.endpoint(), List.of(), List.of(fixture.adjustment())));
        assertThat(AssetReturnPricePairSelector.select(request(
                fixture.endpoint(), List.of(futureWrong), List.of(fixture.adjustment()))))
                .isEqualTo(empty);
        assertThat(AssetReturnPricePairSelector.select(request(
                fixture.endpoint(), List.of(capturedFuture), List.of(fixture.adjustment()))))
                .isEqualTo(empty);
        assertThat(AssetReturnPricePairSelector.select(request(
                fixture.endpoint(), List.of(futureWrong, futureWrong),
                List.of(fixture.adjustment())))).isEqualTo(empty);
        assertUnavailable(empty, UnavailableReason.BASIS_PRICE_MISSING_AS_OF, null);

        EndpointPriceResolution unavailableEndpoint = unavailableEndpoint(
                EndpointPriceResolution.UnavailableReason.OBSERVATION_MISSING_AS_OF,
                fixture.basis().basis());
        AssetReturnPricePairResolution emptyCombined =
                AssetReturnPricePairSelector.select(request(
                        unavailableEndpoint, List.of(), List.of()));
        assertThat(AssetReturnPricePairSelector.select(request(
                unavailableEndpoint, List.of(futureWrong), List.of())))
                .isEqualTo(emptyCombined);
        assertUnavailable(emptyCombined,
                UnavailableReason.BASIS_AND_ENDPOINT_PRICE_UNAVAILABLE,
                EndpointPriceResolution.UnavailableReason.OBSERVATION_MISSING_AS_OF);

        BasisPriceObservation knownWrong = basisMismatch(UnavailableReason.ASSET_MISMATCH);
        assertUnavailable(AssetReturnPricePairSelector.select(request(
                fixture.endpoint(), List.of(knownWrong, futureWrong),
                List.of(fixture.adjustment()))), UnavailableReason.ASSET_MISMATCH, null);
    }

    @Test
    void adjustmentCandidatesArePitFilteredBeforeAllIdentityAndCardinalityChecks() {
        PairFixture fixture = fixture();
        AssetReturnPricePairResolution baseline = select(fixture);
        PricePairAdjustmentEvidence futureWrong = adjustmentCandidate(
                "future-wrong", fixture.basis(), fixture.endpoint(),
                new Correction("wrong-call", "wrong-revision", BASIS_TIME),
                "wrong-asset", "wrong-venue", Currency.getInstance("EUR"),
                "wrong-basis-id", "wrong-basis-provider", "wrong-endpoint-id",
                "wrong-endpoint-provider", BASIS_TIME.minusSeconds(1),
                ENDPOINT_CLOSE.minusSeconds(1),
                EndpointPriceAdjustmentBasis.DIVIDEND_OR_TOTAL_RETURN_ADJUSTED,
                CorporateActionContinuity.SPIN_OFF,
                AS_OF.plusNanos(1_000), AS_OF.plusNanos(2_000));
        PricePairAdjustmentEvidence capturedFuture = adjustmentCandidate(
                "captured-future", fixture.basis(), fixture.endpoint(),
                fixture.basis().basis(), ASSET_ID, VENUE_ID, USD,
                fixture.basis().observationId(), fixture.basis().providerEventId(),
                endpointObservation(fixture.endpoint()).observationId(),
                endpointObservation(fixture.endpoint()).providerEventId(),
                BASIS_TIME, ENDPOINT_CLOSE, REQUIRED_ADJUSTMENT,
                CorporateActionContinuity.SPLIT_REVERSE_SPLIT_CONTINUOUS,
                AS_OF, AS_OF.plusNanos(1_000));

        assertThat(AssetReturnPricePairSelector.select(request(
                fixture.endpoint(), List.of(fixture.basis()),
                List.of(fixture.adjustment(), futureWrong)))).isEqualTo(baseline);
        assertThat(AssetReturnPricePairSelector.select(request(
                fixture.endpoint(), List.of(fixture.basis()),
                List.of(fixture.adjustment(), capturedFuture)))).isEqualTo(baseline);

        AssetReturnPricePairResolution empty = AssetReturnPricePairSelector.select(request(
                fixture.endpoint(), List.of(fixture.basis()), List.of()));
        assertThat(AssetReturnPricePairSelector.select(request(
                fixture.endpoint(), List.of(fixture.basis()), List.of(futureWrong))))
                .isEqualTo(empty);
        assertThat(AssetReturnPricePairSelector.select(request(
                fixture.endpoint(), List.of(fixture.basis()), List.of(capturedFuture))))
                .isEqualTo(empty);
        assertThat(AssetReturnPricePairSelector.select(request(
                fixture.endpoint(), List.of(fixture.basis()),
                List.of(futureWrong, futureWrong)))).isEqualTo(empty);
        assertUnavailable(empty,
                UnavailableReason.ADJUSTMENT_EVIDENCE_MISSING_AS_OF, null);

        PricePairAdjustmentEvidence knownWrong = adjustmentMismatch(
                UnavailableReason.ADJUSTMENT_ASSET_MISMATCH);
        assertUnavailable(AssetReturnPricePairSelector.select(request(
                fixture.endpoint(), List.of(fixture.basis()),
                List.of(knownWrong, futureWrong))),
                UnavailableReason.ADJUSTMENT_ASSET_MISMATCH, null);
    }

    @ParameterizedTest(name = "endpoint missing truth table {0}")
    @MethodSource("endpointReasons")
    void composesEveryNestedEndpointReasonForBasisMissingAndEndpointOnlyStates(
            EndpointPriceResolution.UnavailableReason endpointReason) {
        OutcomeBasis basis = new Original("call-001", BASIS_TIME);
        EndpointPriceResolution unavailableEndpoint = unavailableEndpoint(
                endpointReason, basis);
        BasisPriceObservation knownBasis = exactBasis("known", basis, "183.42");

        AssetReturnPricePairResolution bothUnavailable =
                AssetReturnPricePairSelector.select(request(
                        unavailableEndpoint, List.of(), List.of()));
        assertUnavailable(bothUnavailable,
                UnavailableReason.BASIS_AND_ENDPOINT_PRICE_UNAVAILABLE,
                endpointReason);

        AssetReturnPricePairResolution endpointOnly =
                AssetReturnPricePairSelector.select(request(
                        unavailableEndpoint, List.of(knownBasis), List.of()));
        assertUnavailable(endpointOnly, UnavailableReason.ENDPOINT_PRICE_UNAVAILABLE,
                endpointReason);

        BasisPriceObservation knownWrongBasis = basisCandidate(
                "known-wrong-before-endpoint", basis, "wrong-asset", VENUE_ID, USD,
                PRICE_SOURCE_ID, PRICE_SOURCE_REVISION,
                BasisPriceField.SOURCE_RECORDED_BASIS_EVENT_PRICE,
                REQUIRED_ADJUSTMENT,
                CorporateActionContinuity.SPLIT_REVERSE_SPLIT_CONTINUOUS,
                BASIS_TIME, BASIS_TIME, BASIS_TIME, "183.42");
        assertUnavailable(AssetReturnPricePairSelector.select(request(
                unavailableEndpoint, List.of(knownWrongBasis), List.of())),
                UnavailableReason.ENDPOINT_PRICE_UNAVAILABLE, endpointReason);
    }

    static Stream<EndpointPriceResolution.UnavailableReason> endpointReasons() {
        return Arrays.stream(EndpointPriceResolution.UnavailableReason.values());
    }

    @Test
    void matchesCompleteOriginalAndCorrectionBasisIdentityWithoutEventTimeShortcut() {
        OutcomeBasis original = new Original("call-original", BASIS_TIME);
        PairFixture originalFixture = fixture(original, "100", "110");
        assertThat(select(originalFixture)).isInstanceOf(Resolved.class);

        OutcomeBasis correction = new Correction(
                "call-correction", "revision-correction-2", BASIS_TIME);
        PairFixture correctionFixture = fixture(correction, "100", "110");
        assertThat(select(correctionFixture)).isInstanceOf(Resolved.class);

        List<OutcomeBasis> wrongForCorrection = List.of(
                new Original("call-correction", BASIS_TIME),
                new Correction("wrong-call", "revision-correction-2", BASIS_TIME),
                new Correction("call-correction", "wrong-revision", BASIS_TIME),
                new Correction("call-correction", "revision-correction-2",
                        BASIS_TIME.minusNanos(1_000)));
        for (OutcomeBasis wrongBasis : wrongForCorrection) {
            BasisPriceObservation candidate = exactBasis(
                    "wrong-basis", wrongBasis, "100");
            assertUnavailable(AssetReturnPricePairSelector.select(request(
                    correctionFixture.endpoint(), List.of(candidate), List.of())),
                    UnavailableReason.BASIS_MISMATCH, null);

            EndpointPriceObservation endpoint = endpointObservation(
                    correctionFixture.endpoint());
            PricePairAdjustmentEvidence wrongAdjustment = adjustmentCandidate(
                    "wrong-correction-basis", correctionFixture.basis(),
                    correctionFixture.endpoint(), wrongBasis,
                    ASSET_ID, VENUE_ID, USD,
                    correctionFixture.basis().observationId(),
                    correctionFixture.basis().providerEventId(),
                    endpoint.observationId(), endpoint.providerEventId(),
                    BASIS_TIME, ENDPOINT_CLOSE, REQUIRED_ADJUSTMENT,
                    CorporateActionContinuity.SPLIT_REVERSE_SPLIT_CONTINUOUS,
                    ENDPOINT_CLOSE, ENDPOINT_CLOSE);
            assertUnavailable(AssetReturnPricePairSelector.select(request(
                    correctionFixture.endpoint(), List.of(correctionFixture.basis()),
                    List.of(wrongAdjustment))),
                    UnavailableReason.ADJUSTMENT_OUTCOME_BASIS_MISMATCH, null);
        }
    }

    @ParameterizedTest(name = "basis mismatch {0}")
    @MethodSource("basisMismatchVectors")
    void reportsEveryBasisMismatchWithoutFallback(
            String label,
            UnavailableReason expected,
            BasisPriceObservation candidate) {
        PairFixture fixture = fixture();
        assertUnavailable(AssetReturnPricePairSelector.select(request(
                fixture.endpoint(), List.of(candidate), List.of(fixture.adjustment()))),
                expected, null);
    }

    static Stream<Arguments> basisMismatchVectors() {
        return Stream.of(
                basisArgument("basis", UnavailableReason.BASIS_MISMATCH),
                basisArgument("asset", UnavailableReason.ASSET_MISMATCH),
                basisArgument("venue", UnavailableReason.PRIMARY_VENUE_MISMATCH),
                basisArgument("currency", UnavailableReason.CURRENCY_MISMATCH),
                basisArgument("source-id", UnavailableReason.PRICE_SOURCE_MISMATCH),
                Arguments.of("source-revision", UnavailableReason.PRICE_SOURCE_MISMATCH,
                        basisCandidate("wrong-source-revision",
                                new Original("call-001", BASIS_TIME), ASSET_ID, VENUE_ID,
                                USD, PRICE_SOURCE_ID, "wrong-revision",
                                BasisPriceField.SOURCE_RECORDED_BASIS_EVENT_PRICE,
                                REQUIRED_ADJUSTMENT,
                                CorporateActionContinuity.SPLIT_REVERSE_SPLIT_CONTINUOUS,
                                BASIS_TIME, BASIS_TIME, BASIS_TIME, "100")),
                basisArgument("observed-at", UnavailableReason.OBSERVED_AT_MISMATCH),
                basisArgument("field", UnavailableReason.PRICE_FIELD_MISMATCH),
                basisArgument("adjustment-basis",
                        UnavailableReason.BASIS_PRICE_ADJUSTMENT_BASIS_MISMATCH),
                basisArgument("continuity",
                        UnavailableReason.BASIS_PRICE_CONTINUITY_UNAVAILABLE));
    }

    private static Arguments basisArgument(String label, UnavailableReason reason) {
        return Arguments.of(label, reason, basisMismatch(reason));
    }

    @ParameterizedTest(name = "basis precedence {0}")
    @MethodSource("basisPrecedenceVectors")
    void appliesEveryBasisMismatchGateBeforeTheNextGateRegardlessOfInputOrder(
            UnavailableReason expected,
            BasisPriceObservation earlier,
            BasisPriceObservation later) {
        PairFixture fixture = fixture();
        for (List<BasisPriceObservation> order : List.of(
                List.of(earlier, later), List.of(later, earlier))) {
            assertUnavailable(AssetReturnPricePairSelector.select(request(
                    fixture.endpoint(), order, List.of(fixture.adjustment()))),
                    expected, null);
        }
    }

    static Stream<Arguments> basisPrecedenceVectors() {
        List<UnavailableReason> reasons = basisMismatchReasons();
        return IntStream.range(0, reasons.size() - 1)
                .mapToObj(index -> Arguments.of(
                        reasons.get(index),
                        basisMismatch(reasons.get(index)),
                        basisMismatch(reasons.get(index + 1))));
    }

    @Test
    void basisMismatchGatesPrecedeAmbiguityAndKnownDuplicatesAreNeverDeduplicated() {
        PairFixture fixture = fixture();
        BasisPriceObservation continuityMismatch = basisMismatch(
                UnavailableReason.BASIS_PRICE_CONTINUITY_UNAVAILABLE);
        assertUnavailable(AssetReturnPricePairSelector.select(request(
                fixture.endpoint(), List.of(fixture.basis(), continuityMismatch),
                List.of(fixture.adjustment()))),
                UnavailableReason.BASIS_PRICE_CONTINUITY_UNAVAILABLE, null);
        assertUnavailable(AssetReturnPricePairSelector.select(request(
                fixture.endpoint(), List.of(fixture.basis(), fixture.basis()),
                List.of(fixture.adjustment()))),
                UnavailableReason.BASIS_PRICE_AMBIGUOUS, null);
        assertUnavailable(AssetReturnPricePairSelector.select(request(
                fixture.endpoint(),
                List.of(fixture.basis(), exactBasis("second", fixture.basis().basis(), "100")),
                List.of(fixture.adjustment()))),
                UnavailableReason.BASIS_PRICE_AMBIGUOUS, null);
    }

    @ParameterizedTest(name = "adjustment mismatch {0}")
    @MethodSource("adjustmentMismatchVectors")
    void reportsEveryAdjustmentMismatchAndExactObservationLinks(
            String label,
            UnavailableReason expected,
            PricePairAdjustmentEvidence candidate) {
        PairFixture fixture = fixture();
        assertUnavailable(AssetReturnPricePairSelector.select(request(
                fixture.endpoint(), List.of(fixture.basis()), List.of(candidate))),
                expected, null);
    }

    static Stream<Arguments> adjustmentMismatchVectors() {
        return Stream.of(
                adjustmentArgument("basis", UnavailableReason.ADJUSTMENT_OUTCOME_BASIS_MISMATCH),
                adjustmentArgument("asset", UnavailableReason.ADJUSTMENT_ASSET_MISMATCH),
                adjustmentArgument("venue",
                        UnavailableReason.ADJUSTMENT_PRIMARY_VENUE_MISMATCH),
                adjustmentArgument("currency",
                        UnavailableReason.ADJUSTMENT_CURRENCY_MISMATCH),
                adjustmentArgument("basis-observation-id",
                        UnavailableReason.BASIS_OBSERVATION_LINK_MISMATCH),
                Arguments.of("basis-provider-event-id",
                        UnavailableReason.BASIS_OBSERVATION_LINK_MISMATCH,
                        adjustmentLinkMismatch(false, true, false, false)),
                adjustmentArgument("endpoint-observation-id",
                        UnavailableReason.ENDPOINT_OBSERVATION_LINK_MISMATCH),
                Arguments.of("endpoint-provider-event-id",
                        UnavailableReason.ENDPOINT_OBSERVATION_LINK_MISMATCH,
                        adjustmentLinkMismatch(false, false, false, true)),
                adjustmentArgument("coverage-start",
                        UnavailableReason.ADJUSTMENT_COVERAGE_MISMATCH),
                Arguments.of("coverage-end",
                        UnavailableReason.ADJUSTMENT_COVERAGE_MISMATCH,
                        adjustmentCoverageMismatch(false, true)),
                adjustmentArgument("price-basis",
                        UnavailableReason.ADJUSTMENT_PRICE_BASIS_MISMATCH),
                adjustmentArgument("continuity",
                        UnavailableReason.ADJUSTMENT_CONTINUITY_UNAVAILABLE));
    }

    private static Arguments adjustmentArgument(String label, UnavailableReason reason) {
        return Arguments.of(label, reason, adjustmentMismatch(reason));
    }

    @ParameterizedTest(name = "adjustment precedence {0}")
    @MethodSource("adjustmentPrecedenceVectors")
    void appliesEveryAdjustmentMismatchGateBeforeTheNextGateRegardlessOfInputOrder(
            UnavailableReason expected,
            PricePairAdjustmentEvidence earlier,
            PricePairAdjustmentEvidence later) {
        PairFixture fixture = fixture();
        for (List<PricePairAdjustmentEvidence> order : List.of(
                List.of(earlier, later), List.of(later, earlier))) {
            assertUnavailable(AssetReturnPricePairSelector.select(request(
                    fixture.endpoint(), List.of(fixture.basis()), order)), expected, null);
        }
    }

    static Stream<Arguments> adjustmentPrecedenceVectors() {
        List<UnavailableReason> reasons = adjustmentMismatchReasons();
        return IntStream.range(0, reasons.size() - 1)
                .mapToObj(index -> Arguments.of(
                        reasons.get(index),
                        adjustmentMismatch(reasons.get(index)),
                        adjustmentMismatch(reasons.get(index + 1))));
    }

    @Test
    void adjustmentMismatchGatesPrecedeAmbiguityAndKnownDuplicatesAreNeverDeduplicated() {
        PairFixture fixture = fixture();
        PricePairAdjustmentEvidence continuityMismatch = adjustmentMismatch(
                UnavailableReason.ADJUSTMENT_CONTINUITY_UNAVAILABLE);
        assertUnavailable(AssetReturnPricePairSelector.select(request(
                fixture.endpoint(), List.of(fixture.basis()),
                List.of(fixture.adjustment(), continuityMismatch))),
                UnavailableReason.ADJUSTMENT_CONTINUITY_UNAVAILABLE, null);
        assertUnavailable(AssetReturnPricePairSelector.select(request(
                fixture.endpoint(), List.of(fixture.basis()),
                List.of(fixture.adjustment(), fixture.adjustment()))),
                UnavailableReason.ADJUSTMENT_EVIDENCE_AMBIGUOUS, null);
        PricePairAdjustmentEvidence second = exactAdjustment(
                "second", fixture.basis(), fixture.endpoint());
        assertUnavailable(AssetReturnPricePairSelector.select(request(
                fixture.endpoint(), List.of(fixture.basis()),
                List.of(fixture.adjustment(), second))),
                UnavailableReason.ADJUSTMENT_EVIDENCE_AMBIGUOUS, null);
    }

    @Test
    void directResultsEnforceLocalEvidenceAndNestedReasonConsistencyOnly() {
        PairFixture fixture = fixture();
        ResolutionContext context = context(fixture.endpoint());
        assertThat(new Resolved(context, fixture.basis(), fixture.adjustment())
                .basisObservation()).isSameAs(fixture.basis());

        assertThatThrownBy(() -> new Resolved(
                context, basisMismatch(UnavailableReason.CURRENCY_MISMATCH),
                fixture.adjustment()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("basisObservation");
        assertThatThrownBy(() -> new Resolved(
                context, fixture.basis(),
                adjustmentMismatch(UnavailableReason.ADJUSTMENT_COVERAGE_MISMATCH)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("adjustmentEvidence");
        BasisPriceObservation futureBasis = basisCandidate(
                "future-direct", fixture.basis().basis(), ASSET_ID, VENUE_ID, USD,
                PRICE_SOURCE_ID, PRICE_SOURCE_REVISION,
                BasisPriceField.SOURCE_RECORDED_BASIS_EVENT_PRICE,
                REQUIRED_ADJUSTMENT,
                CorporateActionContinuity.SPLIT_REVERSE_SPLIT_CONTINUOUS,
                BASIS_TIME, AS_OF.plusNanos(1_000), AS_OF.plusNanos(1_000), "100");
        assertThatThrownBy(() -> new Resolved(
                context, futureBasis, fixture.adjustment()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("known by evaluationAsOf");
        PricePairAdjustmentEvidence futureAdjustment = adjustmentCandidate(
                "future-direct", fixture.basis(), fixture.endpoint(),
                fixture.basis().basis(), ASSET_ID, VENUE_ID, USD,
                fixture.basis().observationId(), fixture.basis().providerEventId(),
                endpointObservation(fixture.endpoint()).observationId(),
                endpointObservation(fixture.endpoint()).providerEventId(),
                BASIS_TIME, ENDPOINT_CLOSE, REQUIRED_ADJUSTMENT,
                CorporateActionContinuity.SPLIT_REVERSE_SPLIT_CONTINUOUS,
                AS_OF, AS_OF.plusNanos(1_000));
        assertThatThrownBy(() -> new Resolved(
                context, fixture.basis(), futureAdjustment))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("known by evaluationAsOf");
        assertThatThrownBy(() -> new ResolutionContext(
                policy(), "0".repeat(64), fixture.endpoint()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("policyDefinitionHash");

        EndpointPriceResolution unavailableEndpoint = unavailableEndpoint(
                EndpointPriceResolution.UnavailableReason.OBSERVATION_MISSING_AS_OF,
                fixture.basis().basis());
        ResolutionContext unavailableContext = context(unavailableEndpoint);
        assertThatThrownBy(() -> new Resolved(
                unavailableContext, fixture.basis(), fixture.adjustment()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("resolved endpoint price");
        assertThatThrownBy(() -> new Unavailable(
                context, UnavailableReason.ENDPOINT_PRICE_UNAVAILABLE,
                EndpointPriceResolution.UnavailableReason.OBSERVATION_MISSING_AS_OF))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("unavailable endpoint price");
        assertThatThrownBy(() -> new Unavailable(
                unavailableContext, UnavailableReason.ENDPOINT_PRICE_UNAVAILABLE,
                EndpointPriceResolution.UnavailableReason.ASSET_MISMATCH))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("exact endpoint reason");
        assertThatThrownBy(() -> new Unavailable(
                context, UnavailableReason.BASIS_PRICE_MISSING_AS_OF,
                EndpointPriceResolution.UnavailableReason.OBSERVATION_MISSING_AS_OF))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("null endpointReason");
    }

    @Test
    void requestDefensivelyCopiesBothCandidateListsAndRejectsNulls() {
        PairFixture fixture = fixture();
        List<BasisPriceObservation> basisCandidates = new ArrayList<>();
        basisCandidates.add(fixture.basis());
        List<PricePairAdjustmentEvidence> adjustmentCandidates = new ArrayList<>();
        adjustmentCandidates.add(fixture.adjustment());
        AssetReturnPricePairRequest request = request(
                fixture.endpoint(), basisCandidates, adjustmentCandidates);
        basisCandidates.clear();
        adjustmentCandidates.clear();

        assertThat(request.basisCandidates()).containsExactly(fixture.basis());
        assertThat(request.adjustmentCandidates()).containsExactly(fixture.adjustment());
        assertThatThrownBy(() -> request.basisCandidates().add(fixture.basis()))
                .isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> request.adjustmentCandidates().add(fixture.adjustment()))
                .isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> new AssetReturnPricePairRequest(
                policy(), fixture.endpoint(), null, List.of()))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new AssetReturnPricePairRequest(
                policy(), fixture.endpoint(), List.of(), null))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new AssetReturnPricePairRequest(
                policy(), fixture.endpoint(), Arrays.asList((BasisPriceObservation) null),
                List.of()))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new AssetReturnPricePairRequest(
                policy(), fixture.endpoint(), List.of(),
                Arrays.asList((PricePairAdjustmentEvidence) null)))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new AssetReturnPricePairRequest(
                null, fixture.endpoint(), List.of(), List.of()))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new AssetReturnPricePairRequest(
                policy(), null, List.of(), List.of()))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void resultConstructorsRejectEveryNullPublicComponent() {
        PairFixture fixture = fixture();
        ResolutionContext context = context(fixture.endpoint());
        List<Runnable> invalid = List.of(
                () -> new ResolutionContext(null, PAIR_HASH, fixture.endpoint()),
                () -> new ResolutionContext(policy(), null, fixture.endpoint()),
                () -> new ResolutionContext(policy(), PAIR_HASH, null),
                () -> new Resolved(null, fixture.basis(), fixture.adjustment()),
                () -> new Resolved(context, null, fixture.adjustment()),
                () -> new Resolved(context, fixture.basis(), null),
                () -> new Unavailable(null,
                        UnavailableReason.BASIS_PRICE_MISSING_AS_OF, null),
                () -> new Unavailable(context, null, null));
        assertThat(invalid).hasSize(8);
        for (Runnable mutation : invalid) {
            assertThatThrownBy(mutation::run)
                    .isInstanceOfAny(
                            NullPointerException.class, IllegalArgumentException.class);
        }
    }

    @ParameterizedTest(name = "basis canonical text {0}={1}")
    @MethodSource("basisTextMutations")
    void basisObservationRejectsEveryNullBlankAndUntrimmedCanonicalText(
            String component, String invalid) {
        assertThatThrownBy(() -> basisWithTextMutation(component, invalid))
                .isInstanceOfAny(NullPointerException.class, IllegalArgumentException.class);
    }

    static Stream<Arguments> basisTextMutations() {
        return textMutations(List.of(
                "observationId", "providerEventId", "assetId", "venueId",
                "priceSourceId", "priceSourceRevision", "provenanceId"));
    }

    @ParameterizedTest(name = "adjustment canonical text {0}={1}")
    @MethodSource("adjustmentTextMutations")
    void adjustmentEvidenceRejectsEveryNullBlankAndUntrimmedCanonicalText(
            String component, String invalid) {
        assertThatThrownBy(() -> adjustmentWithTextMutation(component, invalid))
                .isInstanceOfAny(NullPointerException.class, IllegalArgumentException.class);
    }

    static Stream<Arguments> adjustmentTextMutations() {
        return textMutations(List.of(
                "adjustmentEvidenceId", "providerEventId", "assetId",
                "primaryVenueId", "adjustmentSourceId", "adjustmentSourceRevision",
                "provenanceId", "basisObservationId", "basisProviderEventId",
                "endpointObservationId", "endpointProviderEventId"));
    }

    private static Stream<Arguments> textMutations(List<String> components) {
        return components.stream().flatMap(component ->
                Stream.of(null, " ", " untrimmed ")
                        .map(value -> Arguments.of(component, value)));
    }

    @ParameterizedTest(name = "basis constructor mutation {0}")
    @MethodSource("basisConstructorMutations")
    void basisObservationRejectsEveryNullableTimeOrderAndDecimalMutation(
            String label, Runnable mutation) {
        assertThatThrownBy(mutation::run)
                .isInstanceOfAny(NullPointerException.class, IllegalArgumentException.class);
    }

    static Stream<Arguments> basisConstructorMutations() {
        OutcomeBasis basis = new Original("call-001", BASIS_TIME);
        return Stream.of(
                Arguments.of("null basis", (Runnable) () -> basisWithNullable(
                        null, USD, BasisPriceField.SOURCE_RECORDED_BASIS_EVENT_PRICE,
                        REQUIRED_ADJUSTMENT,
                        CorporateActionContinuity.SPLIT_REVERSE_SPLIT_CONTINUOUS,
                        BASIS_TIME, BASIS_TIME, BASIS_TIME, new BigDecimal("100"))),
                Arguments.of("null currency", (Runnable) () -> basisWithNullable(
                        basis, null, BasisPriceField.SOURCE_RECORDED_BASIS_EVENT_PRICE,
                        REQUIRED_ADJUSTMENT,
                        CorporateActionContinuity.SPLIT_REVERSE_SPLIT_CONTINUOUS,
                        BASIS_TIME, BASIS_TIME, BASIS_TIME, new BigDecimal("100"))),
                Arguments.of("null field", (Runnable) () -> basisWithNullable(
                        basis, USD, null, REQUIRED_ADJUSTMENT,
                        CorporateActionContinuity.SPLIT_REVERSE_SPLIT_CONTINUOUS,
                        BASIS_TIME, BASIS_TIME, BASIS_TIME, new BigDecimal("100"))),
                Arguments.of("null adjustment", (Runnable) () -> basisWithNullable(
                        basis, USD, BasisPriceField.SOURCE_RECORDED_BASIS_EVENT_PRICE,
                        null, CorporateActionContinuity.SPLIT_REVERSE_SPLIT_CONTINUOUS,
                        BASIS_TIME, BASIS_TIME, BASIS_TIME, new BigDecimal("100"))),
                Arguments.of("null continuity", (Runnable) () -> basisWithNullable(
                        basis, USD, BasisPriceField.SOURCE_RECORDED_BASIS_EVENT_PRICE,
                        REQUIRED_ADJUSTMENT, null,
                        BASIS_TIME, BASIS_TIME, BASIS_TIME, new BigDecimal("100"))),
                Arguments.of("null observedAt", (Runnable) () -> basisWithNullable(
                        basis, USD, BasisPriceField.SOURCE_RECORDED_BASIS_EVENT_PRICE,
                        REQUIRED_ADJUSTMENT,
                        CorporateActionContinuity.SPLIT_REVERSE_SPLIT_CONTINUOUS,
                        null, BASIS_TIME, BASIS_TIME, new BigDecimal("100"))),
                Arguments.of("null availableAt", (Runnable) () -> basisWithNullable(
                        basis, USD, BasisPriceField.SOURCE_RECORDED_BASIS_EVENT_PRICE,
                        REQUIRED_ADJUSTMENT,
                        CorporateActionContinuity.SPLIT_REVERSE_SPLIT_CONTINUOUS,
                        BASIS_TIME, null, BASIS_TIME, new BigDecimal("100"))),
                Arguments.of("null capturedAt", (Runnable) () -> basisWithNullable(
                        basis, USD, BasisPriceField.SOURCE_RECORDED_BASIS_EVENT_PRICE,
                        REQUIRED_ADJUSTMENT,
                        CorporateActionContinuity.SPLIT_REVERSE_SPLIT_CONTINUOUS,
                        BASIS_TIME, BASIS_TIME, null, new BigDecimal("100"))),
                Arguments.of("sub-micro observedAt", (Runnable) () -> basisWithNullable(
                        basis, USD, BasisPriceField.SOURCE_RECORDED_BASIS_EVENT_PRICE,
                        REQUIRED_ADJUSTMENT,
                        CorporateActionContinuity.SPLIT_REVERSE_SPLIT_CONTINUOUS,
                        BASIS_TIME.plusNanos(1), BASIS_TIME.plusSeconds(1),
                        BASIS_TIME.plusSeconds(1), new BigDecimal("100"))),
                Arguments.of("sub-micro availableAt", (Runnable) () -> basisWithNullable(
                        basis, USD, BasisPriceField.SOURCE_RECORDED_BASIS_EVENT_PRICE,
                        REQUIRED_ADJUSTMENT,
                        CorporateActionContinuity.SPLIT_REVERSE_SPLIT_CONTINUOUS,
                        BASIS_TIME, BASIS_TIME.plusNanos(1), BASIS_TIME.plusSeconds(1),
                        new BigDecimal("100"))),
                Arguments.of("sub-micro capturedAt", (Runnable) () -> basisWithNullable(
                        basis, USD, BasisPriceField.SOURCE_RECORDED_BASIS_EVENT_PRICE,
                        REQUIRED_ADJUSTMENT,
                        CorporateActionContinuity.SPLIT_REVERSE_SPLIT_CONTINUOUS,
                        BASIS_TIME, BASIS_TIME, BASIS_TIME.plusNanos(1),
                        new BigDecimal("100"))),
                Arguments.of("available before observed", (Runnable) () -> basisWithNullable(
                        basis, USD, BasisPriceField.SOURCE_RECORDED_BASIS_EVENT_PRICE,
                        REQUIRED_ADJUSTMENT,
                        CorporateActionContinuity.SPLIT_REVERSE_SPLIT_CONTINUOUS,
                        BASIS_TIME, BASIS_TIME.minusNanos(1_000), BASIS_TIME,
                        new BigDecimal("100"))),
                Arguments.of("captured before available", (Runnable) () -> basisWithNullable(
                        basis, USD, BasisPriceField.SOURCE_RECORDED_BASIS_EVENT_PRICE,
                        REQUIRED_ADJUSTMENT,
                        CorporateActionContinuity.SPLIT_REVERSE_SPLIT_CONTINUOUS,
                        BASIS_TIME, BASIS_TIME.plusNanos(1_000), BASIS_TIME,
                        new BigDecimal("100"))),
                Arguments.of("null price", (Runnable) () -> basisWithNullable(
                        basis, USD, BasisPriceField.SOURCE_RECORDED_BASIS_EVENT_PRICE,
                        REQUIRED_ADJUSTMENT,
                        CorporateActionContinuity.SPLIT_REVERSE_SPLIT_CONTINUOUS,
                        BASIS_TIME, BASIS_TIME, BASIS_TIME, null)),
                Arguments.of("zero price", (Runnable) () -> basisWithNullable(
                        basis, USD, BasisPriceField.SOURCE_RECORDED_BASIS_EVENT_PRICE,
                        REQUIRED_ADJUSTMENT,
                        CorporateActionContinuity.SPLIT_REVERSE_SPLIT_CONTINUOUS,
                        BASIS_TIME, BASIS_TIME, BASIS_TIME, BigDecimal.ZERO)),
                Arguments.of("negative price", (Runnable) () -> basisWithNullable(
                        basis, USD, BasisPriceField.SOURCE_RECORDED_BASIS_EVENT_PRICE,
                        REQUIRED_ADJUSTMENT,
                        CorporateActionContinuity.SPLIT_REVERSE_SPLIT_CONTINUOUS,
                        BASIS_TIME, BASIS_TIME, BASIS_TIME, new BigDecimal("-1"))),
                Arguments.of("scale 13", (Runnable) () -> basisWithNullable(
                        basis, USD, BasisPriceField.SOURCE_RECORDED_BASIS_EVENT_PRICE,
                        REQUIRED_ADJUSTMENT,
                        CorporateActionContinuity.SPLIT_REVERSE_SPLIT_CONTINUOUS,
                        BASIS_TIME, BASIS_TIME, BASIS_TIME,
                        new BigDecimal("1.0000000000001"))),
                Arguments.of("precision 39", (Runnable) () -> basisWithNullable(
                        basis, USD, BasisPriceField.SOURCE_RECORDED_BASIS_EVENT_PRICE,
                        REQUIRED_ADJUSTMENT,
                        CorporateActionContinuity.SPLIT_REVERSE_SPLIT_CONTINUOUS,
                        BASIS_TIME, BASIS_TIME, BASIS_TIME,
                        new BigDecimal("100000000000000000000000000.000000000000"))));
    }

    @ParameterizedTest(name = "adjustment constructor mutation {0}")
    @MethodSource("adjustmentConstructorMutations")
    void adjustmentEvidenceRejectsEveryNullableTimeAndOrderMutation(
            String label, Runnable mutation) {
        assertThatThrownBy(mutation::run)
                .isInstanceOfAny(NullPointerException.class, IllegalArgumentException.class);
    }

    static Stream<Arguments> adjustmentConstructorMutations() {
        PairFixture fixture = fixture();
        OutcomeBasis basis = fixture.basis().basis();
        return Stream.of(
                Arguments.of("null basis", (Runnable) () -> adjustmentWithNullable(
                        null, USD, REQUIRED_ADJUSTMENT,
                        CorporateActionContinuity.SPLIT_REVERSE_SPLIT_CONTINUOUS,
                        BASIS_TIME, ENDPOINT_CLOSE, ENDPOINT_CLOSE, ENDPOINT_CLOSE)),
                Arguments.of("null currency", (Runnable) () -> adjustmentWithNullable(
                        basis, null, REQUIRED_ADJUSTMENT,
                        CorporateActionContinuity.SPLIT_REVERSE_SPLIT_CONTINUOUS,
                        BASIS_TIME, ENDPOINT_CLOSE, ENDPOINT_CLOSE, ENDPOINT_CLOSE)),
                Arguments.of("null adjustment", (Runnable) () -> adjustmentWithNullable(
                        basis, USD, null,
                        CorporateActionContinuity.SPLIT_REVERSE_SPLIT_CONTINUOUS,
                        BASIS_TIME, ENDPOINT_CLOSE, ENDPOINT_CLOSE, ENDPOINT_CLOSE)),
                Arguments.of("null continuity", (Runnable) () -> adjustmentWithNullable(
                        basis, USD, REQUIRED_ADJUSTMENT, null,
                        BASIS_TIME, ENDPOINT_CLOSE, ENDPOINT_CLOSE, ENDPOINT_CLOSE)),
                Arguments.of("null coverageStartsAt", (Runnable) () -> adjustmentWithNullable(
                        basis, USD, REQUIRED_ADJUSTMENT,
                        CorporateActionContinuity.SPLIT_REVERSE_SPLIT_CONTINUOUS,
                        null, ENDPOINT_CLOSE, ENDPOINT_CLOSE, ENDPOINT_CLOSE)),
                Arguments.of("null coverageEndsAt", (Runnable) () -> adjustmentWithNullable(
                        basis, USD, REQUIRED_ADJUSTMENT,
                        CorporateActionContinuity.SPLIT_REVERSE_SPLIT_CONTINUOUS,
                        BASIS_TIME, null, ENDPOINT_CLOSE, ENDPOINT_CLOSE)),
                Arguments.of("null availableAt", (Runnable) () -> adjustmentWithNullable(
                        basis, USD, REQUIRED_ADJUSTMENT,
                        CorporateActionContinuity.SPLIT_REVERSE_SPLIT_CONTINUOUS,
                        BASIS_TIME, ENDPOINT_CLOSE, null, ENDPOINT_CLOSE)),
                Arguments.of("null capturedAt", (Runnable) () -> adjustmentWithNullable(
                        basis, USD, REQUIRED_ADJUSTMENT,
                        CorporateActionContinuity.SPLIT_REVERSE_SPLIT_CONTINUOUS,
                        BASIS_TIME, ENDPOINT_CLOSE, ENDPOINT_CLOSE, null)),
                Arguments.of("sub-micro coverageStartsAt", (Runnable) () ->
                        adjustmentWithNullable(basis, USD, REQUIRED_ADJUSTMENT,
                                CorporateActionContinuity.SPLIT_REVERSE_SPLIT_CONTINUOUS,
                                BASIS_TIME.plusNanos(1), ENDPOINT_CLOSE,
                                ENDPOINT_CLOSE, ENDPOINT_CLOSE)),
                Arguments.of("sub-micro coverageEndsAt", (Runnable) () ->
                        adjustmentWithNullable(basis, USD, REQUIRED_ADJUSTMENT,
                                CorporateActionContinuity.SPLIT_REVERSE_SPLIT_CONTINUOUS,
                                BASIS_TIME, ENDPOINT_CLOSE.plusNanos(1),
                                ENDPOINT_CLOSE.plusSeconds(1),
                                ENDPOINT_CLOSE.plusSeconds(1))),
                Arguments.of("sub-micro availableAt", (Runnable) () ->
                        adjustmentWithNullable(basis, USD, REQUIRED_ADJUSTMENT,
                                CorporateActionContinuity.SPLIT_REVERSE_SPLIT_CONTINUOUS,
                                BASIS_TIME, ENDPOINT_CLOSE,
                                ENDPOINT_CLOSE.plusNanos(1),
                                ENDPOINT_CLOSE.plusSeconds(1))),
                Arguments.of("sub-micro capturedAt", (Runnable) () ->
                        adjustmentWithNullable(basis, USD, REQUIRED_ADJUSTMENT,
                                CorporateActionContinuity.SPLIT_REVERSE_SPLIT_CONTINUOUS,
                                BASIS_TIME, ENDPOINT_CLOSE, ENDPOINT_CLOSE,
                                ENDPOINT_CLOSE.plusNanos(1))),
                Arguments.of("coverage reversed", (Runnable) () ->
                        adjustmentWithNullable(basis, USD, REQUIRED_ADJUSTMENT,
                                CorporateActionContinuity.SPLIT_REVERSE_SPLIT_CONTINUOUS,
                                ENDPOINT_CLOSE, BASIS_TIME, ENDPOINT_CLOSE, ENDPOINT_CLOSE)),
                Arguments.of("available before coverage end", (Runnable) () ->
                        adjustmentWithNullable(basis, USD, REQUIRED_ADJUSTMENT,
                                CorporateActionContinuity.SPLIT_REVERSE_SPLIT_CONTINUOUS,
                                BASIS_TIME, ENDPOINT_CLOSE,
                                ENDPOINT_CLOSE.minusNanos(1_000), ENDPOINT_CLOSE)),
                Arguments.of("captured before available", (Runnable) () ->
                        adjustmentWithNullable(basis, USD, REQUIRED_ADJUSTMENT,
                                CorporateActionContinuity.SPLIT_REVERSE_SPLIT_CONTINUOUS,
                                BASIS_TIME, ENDPOINT_CLOSE,
                                ENDPOINT_CLOSE.plusNanos(1_000), ENDPOINT_CLOSE)));
    }

    @Test
    void acceptsExactMaximumNumericBasisWithoutChangingItsRepresentation() {
        String maximum = "99999999999999999999999999.999999999999";
        BasisPriceObservation observation = exactBasis(
                "maximum", new Original("call-001", BASIS_TIME), maximum);
        assertThat(observation.price()).isEqualTo(new BigDecimal(maximum));
        assertThat(observation.price().scale()).isEqualTo(12);
        assertThat(observation.price().precision()).isEqualTo(38);
    }

    @Test
    void closesPublicRecordsEnumsAndReplayAgainstJvmDefaultsAndInputOrder() {
        assertThat(AssetReturnPricePairPolicyVersion.values()).containsExactly(policy());
        assertThat(BasisPriceField.values()).containsExactly(
                BasisPriceField.SOURCE_RECORDED_BASIS_EVENT_PRICE,
                BasisPriceField.INDICATIVE_OR_OTHER);
        assertThat(AssetReturnPricePairResolution.class.getPermittedSubclasses())
                .containsExactlyInAnyOrder(Resolved.class, Unavailable.class);
        assertThat(UnavailableReason.values()).containsExactly(
                UnavailableReason.BASIS_AND_ENDPOINT_PRICE_UNAVAILABLE,
                UnavailableReason.BASIS_PRICE_MISSING_AS_OF,
                UnavailableReason.ENDPOINT_PRICE_UNAVAILABLE,
                UnavailableReason.BASIS_MISMATCH,
                UnavailableReason.ASSET_MISMATCH,
                UnavailableReason.PRIMARY_VENUE_MISMATCH,
                UnavailableReason.CURRENCY_MISMATCH,
                UnavailableReason.PRICE_SOURCE_MISMATCH,
                UnavailableReason.OBSERVED_AT_MISMATCH,
                UnavailableReason.PRICE_FIELD_MISMATCH,
                UnavailableReason.BASIS_PRICE_ADJUSTMENT_BASIS_MISMATCH,
                UnavailableReason.BASIS_PRICE_CONTINUITY_UNAVAILABLE,
                UnavailableReason.BASIS_PRICE_AMBIGUOUS,
                UnavailableReason.ADJUSTMENT_EVIDENCE_MISSING_AS_OF,
                UnavailableReason.ADJUSTMENT_OUTCOME_BASIS_MISMATCH,
                UnavailableReason.ADJUSTMENT_ASSET_MISMATCH,
                UnavailableReason.ADJUSTMENT_PRIMARY_VENUE_MISMATCH,
                UnavailableReason.ADJUSTMENT_CURRENCY_MISMATCH,
                UnavailableReason.BASIS_OBSERVATION_LINK_MISMATCH,
                UnavailableReason.ENDPOINT_OBSERVATION_LINK_MISMATCH,
                UnavailableReason.ADJUSTMENT_COVERAGE_MISMATCH,
                UnavailableReason.ADJUSTMENT_PRICE_BASIS_MISMATCH,
                UnavailableReason.ADJUSTMENT_CONTINUITY_UNAVAILABLE,
                UnavailableReason.ADJUSTMENT_EVIDENCE_AMBIGUOUS);
        assertRecordComponents(BasisPriceObservation.class,
                "observationId:String", "providerEventId:String", "basis:OutcomeBasis",
                "assetId:String", "venueId:String", "currency:Currency",
                "priceSourceId:String", "priceSourceRevision:String",
                "provenanceId:String", "priceField:BasisPriceField",
                "adjustmentBasis:EndpointPriceAdjustmentBasis",
                "corporateActionContinuity:CorporateActionContinuity",
                "observedAt:Instant", "availableAt:Instant", "capturedAt:Instant",
                "price:BigDecimal");
        assertRecordComponents(PricePairAdjustmentEvidence.class,
                "adjustmentEvidenceId:String", "providerEventId:String",
                "basis:OutcomeBasis", "assetId:String", "primaryVenueId:String",
                "currency:Currency", "adjustmentSourceId:String",
                "adjustmentSourceRevision:String", "provenanceId:String",
                "basisObservationId:String", "basisProviderEventId:String",
                "endpointObservationId:String", "endpointProviderEventId:String",
                "coverageStartsAt:Instant", "coverageEndsAt:Instant",
                "adjustmentBasis:EndpointPriceAdjustmentBasis",
                "corporateActionContinuity:CorporateActionContinuity",
                "availableAt:Instant", "capturedAt:Instant");
        assertRecordComponents(AssetReturnPricePairRequest.class,
                "policyVersion:AssetReturnPricePairPolicyVersion",
                "endpointPriceResolution:EndpointPriceResolution",
                "basisCandidates:List", "adjustmentCandidates:List");
        assertRecordComponents(ResolutionContext.class,
                "policyVersion:AssetReturnPricePairPolicyVersion",
                "policyDefinitionHash:String",
                "endpointPriceResolution:EndpointPriceResolution");
        assertRecordComponents(Resolved.class,
                "context:ResolutionContext", "basisObservation:BasisPriceObservation",
                "adjustmentEvidence:PricePairAdjustmentEvidence");
        assertRecordComponents(Unavailable.class,
                "context:ResolutionContext", "reason:UnavailableReason",
                "endpointReason:UnavailableReason");

        PairFixture fixture = fixture();
        AssetReturnPricePairRequest exactRequest = request(
                fixture.endpoint(), List.of(fixture.basis()),
                List.of(fixture.adjustment()));
        Locale originalLocale = Locale.getDefault();
        TimeZone originalTimeZone = TimeZone.getDefault();
        try {
            AssetReturnPricePairResolution expected =
                    AssetReturnPricePairSelector.select(exactRequest);
            Locale.setDefault(Locale.JAPAN);
            TimeZone.setDefault(TimeZone.getTimeZone("America/New_York"));
            assertThat(AssetReturnPricePairSelector.select(exactRequest)).isEqualTo(expected);
        } finally {
            Locale.setDefault(originalLocale);
            TimeZone.setDefault(originalTimeZone);
        }
        assertThatThrownBy(() -> AssetReturnPricePairSelector.select(null))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("request");
    }

    private static AssetReturnPricePairPolicyVersion policy() {
        return AssetReturnPricePairPolicyVersion
                .SOURCE_RECORDED_BASIS_EVENT_TO_OFFICIAL_ENDPOINT_PRICE_PAIR_V1;
    }

    private static AssetReturnPricePairRequest request(
            EndpointPriceResolution endpoint,
            List<BasisPriceObservation> basisCandidates,
            List<PricePairAdjustmentEvidence> adjustmentCandidates) {
        return new AssetReturnPricePairRequest(
                policy(), endpoint, basisCandidates, adjustmentCandidates);
    }

    private static ResolutionContext context(EndpointPriceResolution endpoint) {
        return new ResolutionContext(policy(), PAIR_HASH, endpoint);
    }

    private static AssetReturnPricePairResolution select(PairFixture fixture) {
        return AssetReturnPricePairSelector.select(request(
                fixture.endpoint(), List.of(fixture.basis()),
                List.of(fixture.adjustment())));
    }

    private static PairFixture fixture() {
        return fixture(new Original("call-001", BASIS_TIME), "100", "120");
    }

    private static PairFixture fixture(
            OutcomeBasis basis, String basisPrice, String endpointPrice) {
        EndpointPriceResolution.Resolved endpoint = resolvedEndpoint(endpointPrice, basis);
        BasisPriceObservation basisObservation = exactBasis(
                "exact", basis, basisPrice);
        PricePairAdjustmentEvidence adjustment = exactAdjustment(
                "exact", basisObservation, endpoint);
        return new PairFixture(endpoint, basisObservation, adjustment);
    }

    private static BasisPriceObservation exactBasis(
            String suffix, OutcomeBasis basis, String price) {
        return basisCandidate(
                suffix, basis, ASSET_ID, VENUE_ID, USD,
                PRICE_SOURCE_ID, PRICE_SOURCE_REVISION,
                BasisPriceField.SOURCE_RECORDED_BASIS_EVENT_PRICE,
                REQUIRED_ADJUSTMENT,
                CorporateActionContinuity.SPLIT_REVERSE_SPLIT_CONTINUOUS,
                basis.eventTime(), basis.eventTime(), basis.eventTime(), price);
    }

    private static BasisPriceObservation basisCandidate(
            String suffix,
            OutcomeBasis basis,
            String assetId,
            String venueId,
            Currency currency,
            String sourceId,
            String sourceRevision,
            BasisPriceField field,
            EndpointPriceAdjustmentBasis adjustmentBasis,
            CorporateActionContinuity continuity,
            Instant observedAt,
            Instant availableAt,
            Instant capturedAt,
            String price) {
        return new BasisPriceObservation(
                "basis-observation-" + suffix,
                "basis-provider-event-" + suffix,
                basis, assetId, venueId, currency, sourceId, sourceRevision,
                "provenance-basis-" + suffix, field, adjustmentBasis, continuity,
                observedAt, availableAt, capturedAt, new BigDecimal(price));
    }

    private static BasisPriceObservation basisMismatch(UnavailableReason reason) {
        OutcomeBasis basis = new Original("call-001", BASIS_TIME);
        return switch (reason) {
            case BASIS_MISMATCH -> basisCandidate(
                    "wrong-basis", new Original("wrong-call", BASIS_TIME),
                    ASSET_ID, VENUE_ID, USD, PRICE_SOURCE_ID, PRICE_SOURCE_REVISION,
                    BasisPriceField.SOURCE_RECORDED_BASIS_EVENT_PRICE,
                    REQUIRED_ADJUSTMENT,
                    CorporateActionContinuity.SPLIT_REVERSE_SPLIT_CONTINUOUS,
                    BASIS_TIME, BASIS_TIME, BASIS_TIME, "100");
            case ASSET_MISMATCH -> basisCandidate(
                    "wrong-asset", basis, "wrong-asset", VENUE_ID, USD,
                    PRICE_SOURCE_ID, PRICE_SOURCE_REVISION,
                    BasisPriceField.SOURCE_RECORDED_BASIS_EVENT_PRICE,
                    REQUIRED_ADJUSTMENT,
                    CorporateActionContinuity.SPLIT_REVERSE_SPLIT_CONTINUOUS,
                    BASIS_TIME, BASIS_TIME, BASIS_TIME, "100");
            case PRIMARY_VENUE_MISMATCH -> basisCandidate(
                    "wrong-venue", basis, ASSET_ID, "wrong-venue", USD,
                    PRICE_SOURCE_ID, PRICE_SOURCE_REVISION,
                    BasisPriceField.SOURCE_RECORDED_BASIS_EVENT_PRICE,
                    REQUIRED_ADJUSTMENT,
                    CorporateActionContinuity.SPLIT_REVERSE_SPLIT_CONTINUOUS,
                    BASIS_TIME, BASIS_TIME, BASIS_TIME, "100");
            case CURRENCY_MISMATCH -> basisCandidate(
                    "wrong-currency", basis, ASSET_ID, VENUE_ID,
                    Currency.getInstance("EUR"), PRICE_SOURCE_ID, PRICE_SOURCE_REVISION,
                    BasisPriceField.SOURCE_RECORDED_BASIS_EVENT_PRICE,
                    REQUIRED_ADJUSTMENT,
                    CorporateActionContinuity.SPLIT_REVERSE_SPLIT_CONTINUOUS,
                    BASIS_TIME, BASIS_TIME, BASIS_TIME, "100");
            case PRICE_SOURCE_MISMATCH -> basisCandidate(
                    "wrong-source", basis, ASSET_ID, VENUE_ID, USD,
                    "wrong-source", PRICE_SOURCE_REVISION,
                    BasisPriceField.SOURCE_RECORDED_BASIS_EVENT_PRICE,
                    REQUIRED_ADJUSTMENT,
                    CorporateActionContinuity.SPLIT_REVERSE_SPLIT_CONTINUOUS,
                    BASIS_TIME, BASIS_TIME, BASIS_TIME, "100");
            case OBSERVED_AT_MISMATCH -> basisCandidate(
                    "wrong-observed-at", basis, ASSET_ID, VENUE_ID, USD,
                    PRICE_SOURCE_ID, PRICE_SOURCE_REVISION,
                    BasisPriceField.SOURCE_RECORDED_BASIS_EVENT_PRICE,
                    REQUIRED_ADJUSTMENT,
                    CorporateActionContinuity.SPLIT_REVERSE_SPLIT_CONTINUOUS,
                    BASIS_TIME.plusSeconds(1), BASIS_TIME.plusSeconds(1),
                    BASIS_TIME.plusSeconds(1), "100");
            case PRICE_FIELD_MISMATCH -> basisCandidate(
                    "wrong-field", basis, ASSET_ID, VENUE_ID, USD,
                    PRICE_SOURCE_ID, PRICE_SOURCE_REVISION,
                    BasisPriceField.INDICATIVE_OR_OTHER, REQUIRED_ADJUSTMENT,
                    CorporateActionContinuity.SPLIT_REVERSE_SPLIT_CONTINUOUS,
                    BASIS_TIME, BASIS_TIME, BASIS_TIME, "100");
            case BASIS_PRICE_ADJUSTMENT_BASIS_MISMATCH -> basisCandidate(
                    "wrong-adjustment", basis, ASSET_ID, VENUE_ID, USD,
                    PRICE_SOURCE_ID, PRICE_SOURCE_REVISION,
                    BasisPriceField.SOURCE_RECORDED_BASIS_EVENT_PRICE,
                    EndpointPriceAdjustmentBasis.DIVIDEND_OR_TOTAL_RETURN_ADJUSTED,
                    CorporateActionContinuity.SPLIT_REVERSE_SPLIT_CONTINUOUS,
                    BASIS_TIME, BASIS_TIME, BASIS_TIME, "100");
            case BASIS_PRICE_CONTINUITY_UNAVAILABLE -> basisCandidate(
                    "wrong-continuity", basis, ASSET_ID, VENUE_ID, USD,
                    PRICE_SOURCE_ID, PRICE_SOURCE_REVISION,
                    BasisPriceField.SOURCE_RECORDED_BASIS_EVENT_PRICE,
                    REQUIRED_ADJUSTMENT, CorporateActionContinuity.MERGER,
                    BASIS_TIME, BASIS_TIME, BASIS_TIME, "100");
            default -> throw new IllegalArgumentException("not a basis mismatch: " + reason);
        };
    }

    private static List<UnavailableReason> basisMismatchReasons() {
        return List.of(
                UnavailableReason.BASIS_MISMATCH,
                UnavailableReason.ASSET_MISMATCH,
                UnavailableReason.PRIMARY_VENUE_MISMATCH,
                UnavailableReason.CURRENCY_MISMATCH,
                UnavailableReason.PRICE_SOURCE_MISMATCH,
                UnavailableReason.OBSERVED_AT_MISMATCH,
                UnavailableReason.PRICE_FIELD_MISMATCH,
                UnavailableReason.BASIS_PRICE_ADJUSTMENT_BASIS_MISMATCH,
                UnavailableReason.BASIS_PRICE_CONTINUITY_UNAVAILABLE);
    }

    private static PricePairAdjustmentEvidence exactAdjustment(
            String suffix,
            BasisPriceObservation basis,
            EndpointPriceResolution.Resolved endpoint) {
        EndpointPriceObservation endpointObservation = endpointObservation(endpoint);
        return adjustmentCandidate(
                suffix, basis, endpoint, basis.basis(), ASSET_ID, VENUE_ID, USD,
                basis.observationId(), basis.providerEventId(),
                endpointObservation.observationId(), endpointObservation.providerEventId(),
                basis.observedAt(), endpointObservation.observedAt(), REQUIRED_ADJUSTMENT,
                CorporateActionContinuity.SPLIT_REVERSE_SPLIT_CONTINUOUS,
                endpointObservation.observedAt(), endpointObservation.observedAt());
    }

    private static PricePairAdjustmentEvidence adjustmentCandidate(
            String suffix,
            BasisPriceObservation basisObservation,
            EndpointPriceResolution.Resolved endpoint,
            OutcomeBasis basis,
            String assetId,
            String venueId,
            Currency currency,
            String basisObservationId,
            String basisProviderEventId,
            String endpointObservationId,
            String endpointProviderEventId,
            Instant coverageStartsAt,
            Instant coverageEndsAt,
            EndpointPriceAdjustmentBasis adjustmentBasis,
            CorporateActionContinuity continuity,
            Instant availableAt,
            Instant capturedAt) {
        return new PricePairAdjustmentEvidence(
                "adjustment-evidence-" + suffix,
                "adjustment-provider-event-" + suffix,
                basis, assetId, venueId, currency,
                "source-corporate-actions", "adjustment-revision-9",
                "provenance-adjustment-" + suffix,
                basisObservationId, basisProviderEventId,
                endpointObservationId, endpointProviderEventId,
                coverageStartsAt, coverageEndsAt, adjustmentBasis, continuity,
                availableAt, capturedAt);
    }

    private static PricePairAdjustmentEvidence adjustmentMismatch(
            UnavailableReason reason) {
        PairFixture fixture = fixture();
        OutcomeBasis basis = fixture.basis().basis();
        EndpointPriceObservation endpoint = endpointObservation(fixture.endpoint());
        return switch (reason) {
            case ADJUSTMENT_OUTCOME_BASIS_MISMATCH -> adjustmentCandidate(
                    "wrong-basis", fixture.basis(), fixture.endpoint(),
                    new Original("wrong-call", BASIS_TIME), ASSET_ID, VENUE_ID, USD,
                    fixture.basis().observationId(), fixture.basis().providerEventId(),
                    endpoint.observationId(), endpoint.providerEventId(),
                    BASIS_TIME, ENDPOINT_CLOSE, REQUIRED_ADJUSTMENT,
                    CorporateActionContinuity.SPLIT_REVERSE_SPLIT_CONTINUOUS,
                    ENDPOINT_CLOSE, ENDPOINT_CLOSE);
            case ADJUSTMENT_ASSET_MISMATCH -> adjustmentCandidate(
                    "wrong-asset", fixture.basis(), fixture.endpoint(), basis,
                    "wrong-asset", VENUE_ID, USD,
                    fixture.basis().observationId(), fixture.basis().providerEventId(),
                    endpoint.observationId(), endpoint.providerEventId(),
                    BASIS_TIME, ENDPOINT_CLOSE, REQUIRED_ADJUSTMENT,
                    CorporateActionContinuity.SPLIT_REVERSE_SPLIT_CONTINUOUS,
                    ENDPOINT_CLOSE, ENDPOINT_CLOSE);
            case ADJUSTMENT_PRIMARY_VENUE_MISMATCH -> adjustmentCandidate(
                    "wrong-venue", fixture.basis(), fixture.endpoint(), basis,
                    ASSET_ID, "wrong-venue", USD,
                    fixture.basis().observationId(), fixture.basis().providerEventId(),
                    endpoint.observationId(), endpoint.providerEventId(),
                    BASIS_TIME, ENDPOINT_CLOSE, REQUIRED_ADJUSTMENT,
                    CorporateActionContinuity.SPLIT_REVERSE_SPLIT_CONTINUOUS,
                    ENDPOINT_CLOSE, ENDPOINT_CLOSE);
            case ADJUSTMENT_CURRENCY_MISMATCH -> adjustmentCandidate(
                    "wrong-currency", fixture.basis(), fixture.endpoint(), basis,
                    ASSET_ID, VENUE_ID, Currency.getInstance("EUR"),
                    fixture.basis().observationId(), fixture.basis().providerEventId(),
                    endpoint.observationId(), endpoint.providerEventId(),
                    BASIS_TIME, ENDPOINT_CLOSE, REQUIRED_ADJUSTMENT,
                    CorporateActionContinuity.SPLIT_REVERSE_SPLIT_CONTINUOUS,
                    ENDPOINT_CLOSE, ENDPOINT_CLOSE);
            case BASIS_OBSERVATION_LINK_MISMATCH ->
                    adjustmentLinkMismatch(true, false, false, false);
            case ENDPOINT_OBSERVATION_LINK_MISMATCH ->
                    adjustmentLinkMismatch(false, false, true, false);
            case ADJUSTMENT_COVERAGE_MISMATCH ->
                    adjustmentCoverageMismatch(true, false);
            case ADJUSTMENT_PRICE_BASIS_MISMATCH -> adjustmentCandidate(
                    "wrong-price-basis", fixture.basis(), fixture.endpoint(), basis,
                    ASSET_ID, VENUE_ID, USD,
                    fixture.basis().observationId(), fixture.basis().providerEventId(),
                    endpoint.observationId(), endpoint.providerEventId(),
                    BASIS_TIME, ENDPOINT_CLOSE,
                    EndpointPriceAdjustmentBasis.DIVIDEND_OR_TOTAL_RETURN_ADJUSTED,
                    CorporateActionContinuity.SPLIT_REVERSE_SPLIT_CONTINUOUS,
                    ENDPOINT_CLOSE, ENDPOINT_CLOSE);
            case ADJUSTMENT_CONTINUITY_UNAVAILABLE -> adjustmentCandidate(
                    "wrong-continuity", fixture.basis(), fixture.endpoint(), basis,
                    ASSET_ID, VENUE_ID, USD,
                    fixture.basis().observationId(), fixture.basis().providerEventId(),
                    endpoint.observationId(), endpoint.providerEventId(),
                    BASIS_TIME, ENDPOINT_CLOSE, REQUIRED_ADJUSTMENT,
                    CorporateActionContinuity.SPECIAL_DISTRIBUTION,
                    ENDPOINT_CLOSE, ENDPOINT_CLOSE);
            default -> throw new IllegalArgumentException(
                    "not an adjustment mismatch: " + reason);
        };
    }

    private static PricePairAdjustmentEvidence adjustmentLinkMismatch(
            boolean wrongBasisObservation,
            boolean wrongBasisProvider,
            boolean wrongEndpointObservation,
            boolean wrongEndpointProvider) {
        PairFixture fixture = fixture();
        EndpointPriceObservation endpoint = endpointObservation(fixture.endpoint());
        return adjustmentCandidate(
                "wrong-link", fixture.basis(), fixture.endpoint(), fixture.basis().basis(),
                ASSET_ID, VENUE_ID, USD,
                wrongBasisObservation ? "wrong-basis-id" : fixture.basis().observationId(),
                wrongBasisProvider ? "wrong-basis-provider" : fixture.basis().providerEventId(),
                wrongEndpointObservation ? "wrong-endpoint-id" : endpoint.observationId(),
                wrongEndpointProvider ? "wrong-endpoint-provider" : endpoint.providerEventId(),
                BASIS_TIME, ENDPOINT_CLOSE, REQUIRED_ADJUSTMENT,
                CorporateActionContinuity.SPLIT_REVERSE_SPLIT_CONTINUOUS,
                ENDPOINT_CLOSE, ENDPOINT_CLOSE);
    }

    private static PricePairAdjustmentEvidence adjustmentCoverageMismatch(
            boolean wrongStart, boolean wrongEnd) {
        PairFixture fixture = fixture();
        EndpointPriceObservation endpoint = endpointObservation(fixture.endpoint());
        return adjustmentCandidate(
                "wrong-coverage", fixture.basis(), fixture.endpoint(), fixture.basis().basis(),
                ASSET_ID, VENUE_ID, USD,
                fixture.basis().observationId(), fixture.basis().providerEventId(),
                endpoint.observationId(), endpoint.providerEventId(),
                wrongStart ? BASIS_TIME.minusNanos(1_000) : BASIS_TIME,
                wrongEnd ? ENDPOINT_CLOSE.minusNanos(1_000) : ENDPOINT_CLOSE,
                REQUIRED_ADJUSTMENT,
                CorporateActionContinuity.SPLIT_REVERSE_SPLIT_CONTINUOUS,
                ENDPOINT_CLOSE, ENDPOINT_CLOSE);
    }

    private static List<UnavailableReason> adjustmentMismatchReasons() {
        return List.of(
                UnavailableReason.ADJUSTMENT_OUTCOME_BASIS_MISMATCH,
                UnavailableReason.ADJUSTMENT_ASSET_MISMATCH,
                UnavailableReason.ADJUSTMENT_PRIMARY_VENUE_MISMATCH,
                UnavailableReason.ADJUSTMENT_CURRENCY_MISMATCH,
                UnavailableReason.BASIS_OBSERVATION_LINK_MISMATCH,
                UnavailableReason.ENDPOINT_OBSERVATION_LINK_MISMATCH,
                UnavailableReason.ADJUSTMENT_COVERAGE_MISMATCH,
                UnavailableReason.ADJUSTMENT_PRICE_BASIS_MISMATCH,
                UnavailableReason.ADJUSTMENT_CONTINUITY_UNAVAILABLE);
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

    private static EndpointPriceResolution unavailableEndpoint(
            EndpointPriceResolution.UnavailableReason reason,
            OutcomeBasis basis) {
        return new EndpointPriceResolution.Unavailable(endpointContext(basis), reason);
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

    private static EndpointPriceObservation endpointObservation(
            EndpointPriceResolution.Resolved endpoint) {
        return endpoint.observation();
    }

    private static BasisPriceObservation basisWithTextMutation(
            String component, String value) {
        return new BasisPriceObservation(
                "observationId".equals(component) ? value : "basis-observation",
                "providerEventId".equals(component) ? value : "basis-provider-event",
                new Original("call-001", BASIS_TIME),
                "assetId".equals(component) ? value : ASSET_ID,
                "venueId".equals(component) ? value : VENUE_ID,
                USD,
                "priceSourceId".equals(component) ? value : PRICE_SOURCE_ID,
                "priceSourceRevision".equals(component) ? value : PRICE_SOURCE_REVISION,
                "provenanceId".equals(component) ? value : "provenance-basis",
                BasisPriceField.SOURCE_RECORDED_BASIS_EVENT_PRICE,
                REQUIRED_ADJUSTMENT,
                CorporateActionContinuity.SPLIT_REVERSE_SPLIT_CONTINUOUS,
                BASIS_TIME, BASIS_TIME, BASIS_TIME, new BigDecimal("100"));
    }

    private static BasisPriceObservation basisWithNullable(
            OutcomeBasis basis,
            Currency currency,
            BasisPriceField field,
            EndpointPriceAdjustmentBasis adjustmentBasis,
            CorporateActionContinuity continuity,
            Instant observedAt,
            Instant availableAt,
            Instant capturedAt,
            BigDecimal price) {
        return new BasisPriceObservation(
                "basis-observation", "basis-provider-event", basis,
                ASSET_ID, VENUE_ID, currency, PRICE_SOURCE_ID, PRICE_SOURCE_REVISION,
                "provenance-basis", field, adjustmentBasis, continuity,
                observedAt, availableAt, capturedAt, price);
    }

    private static PricePairAdjustmentEvidence adjustmentWithTextMutation(
            String component, String value) {
        return new PricePairAdjustmentEvidence(
                "adjustmentEvidenceId".equals(component) ? value : "adjustment-evidence",
                "providerEventId".equals(component) ? value : "adjustment-provider-event",
                new Original("call-001", BASIS_TIME),
                "assetId".equals(component) ? value : ASSET_ID,
                "primaryVenueId".equals(component) ? value : VENUE_ID,
                USD,
                "adjustmentSourceId".equals(component) ? value : "source-actions",
                "adjustmentSourceRevision".equals(component)
                        ? value : "source-actions-revision",
                "provenanceId".equals(component) ? value : "provenance-adjustment",
                "basisObservationId".equals(component) ? value : "basis-observation",
                "basisProviderEventId".equals(component)
                        ? value : "basis-provider-event",
                "endpointObservationId".equals(component) ? value : "endpoint-observation",
                "endpointProviderEventId".equals(component)
                        ? value : "endpoint-provider-event",
                BASIS_TIME, ENDPOINT_CLOSE, REQUIRED_ADJUSTMENT,
                CorporateActionContinuity.SPLIT_REVERSE_SPLIT_CONTINUOUS,
                ENDPOINT_CLOSE, ENDPOINT_CLOSE);
    }

    private static PricePairAdjustmentEvidence adjustmentWithNullable(
            OutcomeBasis basis,
            Currency currency,
            EndpointPriceAdjustmentBasis adjustmentBasis,
            CorporateActionContinuity continuity,
            Instant coverageStartsAt,
            Instant coverageEndsAt,
            Instant availableAt,
            Instant capturedAt) {
        return new PricePairAdjustmentEvidence(
                "adjustment-evidence", "adjustment-provider-event", basis,
                ASSET_ID, VENUE_ID, currency,
                "source-actions", "source-actions-revision", "provenance-adjustment",
                "basis-observation", "basis-provider-event",
                "endpoint-observation", "endpoint-provider-event",
                coverageStartsAt, coverageEndsAt, adjustmentBasis, continuity,
                availableAt, capturedAt);
    }

    private static void assertUnavailable(
            AssetReturnPricePairResolution result,
            UnavailableReason reason,
            EndpointPriceResolution.UnavailableReason endpointReason) {
        assertThat(result).isInstanceOf(Unavailable.class);
        Unavailable unavailable = (Unavailable) result;
        assertThat(unavailable.reason()).isEqualTo(reason);
        assertThat(unavailable.endpointReason()).isEqualTo(endpointReason);
        assertThat(unavailable).isEqualTo(
                new Unavailable(unavailable.context(), reason, endpointReason));
    }

    private static void assertRecordComponents(Class<?> type, String... expected) {
        assertThat(Stream.of(type.getRecordComponents())
                .map(component -> component.getName() + ":"
                        + component.getType().getSimpleName()))
                .containsExactly(expected);
    }

    private record PairFixture(
            EndpointPriceResolution.Resolved endpoint,
            BasisPriceObservation basis,
            PricePairAdjustmentEvidence adjustment) {
    }
}
