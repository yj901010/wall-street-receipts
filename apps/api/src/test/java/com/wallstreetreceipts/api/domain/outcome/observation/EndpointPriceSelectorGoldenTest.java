package com.wallstreetreceipts.api.domain.outcome.observation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.ArrayList;
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
import com.wallstreetreceipts.api.domain.outcome.horizon.OutcomeBasis.Original;
import com.wallstreetreceipts.api.domain.outcome.horizon.SessionCloseHorizonPolicyVersion;
import com.wallstreetreceipts.api.domain.outcome.horizon.SessionCloseHorizonResolution.ResolvedSessionWindow;
import com.wallstreetreceipts.api.domain.outcome.horizon.TradingSession;
import com.wallstreetreceipts.api.domain.outcome.observation.EndpointPriceResolution.ResolutionContext;
import com.wallstreetreceipts.api.domain.outcome.observation.EndpointPriceResolution.Resolved;
import com.wallstreetreceipts.api.domain.outcome.observation.EndpointPriceResolution.Unavailable;
import com.wallstreetreceipts.api.domain.outcome.observation.EndpointPriceResolution.UnavailableReason;

class EndpointPriceSelectorGoldenTest {

    private static final String CALENDAR_ID = "calendar-primary-us-equity";
    private static final String CATALOG_REVISION = "calendar-revision-7";
    private static final String ASSET_ID = "asset-nvda";
    private static final String VENUE_ID = "venue-xnas";
    private static final Currency USD = Currency.getInstance("USD");
    private static final String SOURCE_ID = "source-official-close";
    private static final String SOURCE_REVISION = "source-revision-3";
    private static final Instant BASIS_TIME = Instant.parse("2026-08-20T14:00:00Z");
    private static final Instant ENDPOINT_OPEN = Instant.parse("2026-08-21T13:30:00Z");
    private static final Instant ENDPOINT_CLOSE = Instant.parse("2026-08-21T20:00:00Z");
    private static final Instant AS_OF = Instant.parse("2026-08-21T20:01:00Z");
    private static final Instant KNOWN_BEFORE = Instant.parse("2026-08-20T12:00:00Z");
    private static final Instant OBSERVATION_CAPTURED =
            Instant.parse("2026-08-21T20:00:30Z");
    private static final EndpointPriceAdjustmentBasis REQUIRED_ADJUSTMENT =
            EndpointPriceAdjustmentBasis
                    .SPLIT_AND_REVERSE_SPLIT_ADJUSTED_TO_ENDPOINT_SHARE_BASIS_DIVIDEND_UNADJUSTED;
    private static final String POLICY_HASH =
            "37e37aba9302d77366cef4129f77a82b7ccb2f1937bfffc0315ea8d0bc6b1f76";
    private static final String HORIZON_HASH =
            "550087efe7ddf2ba31974c89c2740ab79df986eefef48919c32c56a3232f8dc1";

    @Test
    void canonicalPolicyDefinitionHasStableExactUtf8BytesAndIndependentSha256()
            throws NoSuchAlgorithmException {
        EndpointPricePolicyVersion policy = policy();
        byte[] firstRead = policy.canonicalDefinitionUtf8();
        String independentHash = HexFormat.of().formatHex(
                MessageDigest.getInstance("SHA-256").digest(firstRead));

        assertThat(firstRead).hasSize(2259);
        assertThat(independentHash).isEqualTo(POLICY_HASH);
        assertThat(policy.definitionHash()).isEqualTo(POLICY_HASH);
        assertThat(policy.canonicalDefinition())
                .contains("\"requiredHorizonPolicyDefinitionHash\":\"" + HORIZON_HASH + "\"")
                .contains("\"futureCandidateRule\":\"INVISIBLE_TO_ALL_OUTPUT_AND_REASONING\"")
                .contains("\"noKnownReason\":\"OBSERVATION_MISSING_AS_OF\"")
                .contains("\"deduplication\":\"ABSENT\"")
                .contains("\"resolvedCardinality\":1");

        firstRead[0] = (byte) '!';
        assertThat(policy.canonicalDefinitionUtf8()[0]).isEqualTo((byte) '{');
    }

    @Test
    void selectsExactlyOneKnownOfficialPrimaryVenueCloseAtInclusivePitBoundaries() {
        EndpointPriceObservation candidate = exactCandidate("known-exact");
        EndpointPriceRequest request = request(AS_OF, List.of(candidate));

        Resolved result = (Resolved) EndpointPriceSelector.select(request);

        assertThat(result.observation()).isSameAs(candidate);
        assertThat(result.context()).isEqualTo(context(request));
        assertThat(result.context().horizonResolution()).isSameAs(request.horizonResolution());
        assertThat(result.context().catalogEvidence()).isSameAs(request.catalogEvidence());
        assertThat(result.context().binding()).isSameAs(request.binding());
        assertThat(result.context().policyDefinitionHash()).isEqualTo(POLICY_HASH);

        Instant exactBoundary = ENDPOINT_CLOSE;
        EndpointPriceObservation boundaryCandidate = candidate(
                "boundary", exactBoundary, exactBoundary,
                ASSET_ID, VENUE_ID, USD, SOURCE_ID, SOURCE_REVISION,
                CALENDAR_ID, CATALOG_REVISION, "session-endpoint", ENDPOINT_CLOSE,
                EndpointPriceField.OFFICIAL_REGULAR_SESSION_CLOSE,
                REQUIRED_ADJUSTMENT,
                CorporateActionContinuity.SPLIT_REVERSE_SPLIT_CONTINUOUS);
        assertThat(EndpointPriceSelector.select(request(exactBoundary, List.of(boundaryCandidate))))
                .isInstanceOf(Resolved.class);
    }

    @Test
    void futureCandidatesAreInvisibleToEveryOutputAndReasoningPath() {
        EndpointPriceObservation known = exactCandidate("known");
        EndpointPriceObservation futureWrong = candidate(
                "future-wrong", AS_OF.plusNanos(1_000), AS_OF.plusNanos(2_000),
                "wrong-asset", "wrong-venue", Currency.getInstance("EUR"),
                "wrong-source", "wrong-revision", "wrong-calendar", "wrong-catalog",
                "wrong-session", ENDPOINT_CLOSE.plusSeconds(1),
                EndpointPriceField.INDICATIVE_OR_OTHER,
                EndpointPriceAdjustmentBasis.DIVIDEND_OR_TOTAL_RETURN_ADJUSTED,
                CorporateActionContinuity.MERGER);

        EndpointPriceResolution withoutFuture =
                EndpointPriceSelector.select(request(AS_OF, List.of(known)));
        EndpointPriceResolution withFuture =
                EndpointPriceSelector.select(request(AS_OF, List.of(known, futureWrong)));
        assertThat(withFuture).isEqualTo(withoutFuture);

        EndpointPriceObservation futureExact = candidate(
                "future-exact", AS_OF.plusNanos(1_000), AS_OF.plusNanos(2_000),
                ASSET_ID, VENUE_ID, USD, SOURCE_ID, SOURCE_REVISION,
                CALENDAR_ID, CATALOG_REVISION, "session-endpoint", ENDPOINT_CLOSE,
                EndpointPriceField.OFFICIAL_REGULAR_SESSION_CLOSE,
                REQUIRED_ADJUSTMENT,
                CorporateActionContinuity.SPLIT_REVERSE_SPLIT_CONTINUOUS);
        assertThat(EndpointPriceSelector.select(
                request(AS_OF, List.of(known, futureExact))))
                .isEqualTo(withoutFuture);

        EndpointPriceResolution empty = EndpointPriceSelector.select(request(AS_OF, List.of()));
        EndpointPriceResolution onlyFuture =
                EndpointPriceSelector.select(request(AS_OF, List.of(futureWrong)));
        EndpointPriceResolution duplicatedFuture = EndpointPriceSelector.select(
                request(AS_OF, List.of(futureWrong, futureWrong)));
        assertThat(onlyFuture).isEqualTo(empty);
        assertThat(duplicatedFuture).isEqualTo(empty);
        assertUnavailable(empty, UnavailableReason.OBSERVATION_MISSING_AS_OF);

        EndpointPriceObservation knownWrongVenue = candidate(
                "known-wrong", ENDPOINT_CLOSE, OBSERVATION_CAPTURED,
                ASSET_ID, "wrong-venue", USD, SOURCE_ID, SOURCE_REVISION,
                CALENDAR_ID, CATALOG_REVISION, "session-endpoint", ENDPOINT_CLOSE,
                EndpointPriceField.OFFICIAL_REGULAR_SESSION_CLOSE,
                REQUIRED_ADJUSTMENT,
                CorporateActionContinuity.SPLIT_REVERSE_SPLIT_CONTINUOUS);
        assertUnavailable(
                EndpointPriceSelector.select(
                        request(AS_OF, List.of(knownWrongVenue, futureExact))),
                UnavailableReason.PRIMARY_VENUE_MISMATCH);

        EndpointPriceObservation capturedOnlyFuture = candidate(
                "captured-only-future", AS_OF, AS_OF.plusNanos(1_000),
                ASSET_ID, VENUE_ID, USD, SOURCE_ID, SOURCE_REVISION,
                CALENDAR_ID, CATALOG_REVISION, "session-endpoint", ENDPOINT_CLOSE,
                EndpointPriceField.OFFICIAL_REGULAR_SESSION_CLOSE,
                REQUIRED_ADJUSTMENT,
                CorporateActionContinuity.SPLIT_REVERSE_SPLIT_CONTINUOUS);
        assertThat(EndpointPriceSelector.select(
                request(AS_OF, List.of(known, capturedOnlyFuture))))
                .isEqualTo(withoutFuture);
        assertThat(EndpointPriceSelector.select(
                request(AS_OF, List.of(capturedOnlyFuture))))
                .isEqualTo(empty);
    }

    @ParameterizedTest(name = "known request-scoped candidate mismatch {0}")
    @MethodSource("knownCandidateMismatchVectors")
    void rejectsEveryKnownCandidateMismatchWithoutFxFallbackOrInference(
            UnavailableReason expected,
            EndpointPriceObservation candidate) {
        assertUnavailable(
                EndpointPriceSelector.select(request(AS_OF, List.of(candidate))),
                expected);
    }

    static Stream<Arguments> knownCandidateMismatchVectors() {
        return Stream.of(
                Arguments.of(UnavailableReason.ASSET_MISMATCH,
                        mismatch(UnavailableReason.ASSET_MISMATCH)),
                Arguments.of(UnavailableReason.PRIMARY_VENUE_MISMATCH,
                        mismatch(UnavailableReason.PRIMARY_VENUE_MISMATCH)),
                Arguments.of(UnavailableReason.CURRENCY_MISMATCH,
                        mismatch(UnavailableReason.CURRENCY_MISMATCH)),
                Arguments.of(UnavailableReason.SOURCE_MISMATCH,
                        mismatch(UnavailableReason.SOURCE_MISMATCH)),
                Arguments.of(UnavailableReason.SOURCE_MISMATCH,
                        candidate(
                                "source-revision-mismatch",
                                ENDPOINT_CLOSE, OBSERVATION_CAPTURED,
                                ASSET_ID, VENUE_ID, USD, SOURCE_ID, "source-revision-other",
                                CALENDAR_ID, CATALOG_REVISION, "session-endpoint",
                                ENDPOINT_CLOSE,
                                EndpointPriceField.OFFICIAL_REGULAR_SESSION_CLOSE,
                                REQUIRED_ADJUSTMENT,
                                CorporateActionContinuity.SPLIT_REVERSE_SPLIT_CONTINUOUS)),
                Arguments.of(UnavailableReason.CATALOG_MISMATCH,
                        mismatch(UnavailableReason.CATALOG_MISMATCH)),
                Arguments.of(UnavailableReason.CATALOG_MISMATCH,
                        candidate(
                                "catalog-revision-mismatch",
                                ENDPOINT_CLOSE, OBSERVATION_CAPTURED,
                                ASSET_ID, VENUE_ID, USD, SOURCE_ID, SOURCE_REVISION,
                                CALENDAR_ID, "catalog-revision-other", "session-endpoint",
                                ENDPOINT_CLOSE,
                                EndpointPriceField.OFFICIAL_REGULAR_SESSION_CLOSE,
                                REQUIRED_ADJUSTMENT,
                                CorporateActionContinuity.SPLIT_REVERSE_SPLIT_CONTINUOUS)),
                Arguments.of(UnavailableReason.SESSION_MISMATCH,
                        mismatch(UnavailableReason.SESSION_MISMATCH)),
                Arguments.of(UnavailableReason.OBSERVED_AT_MISMATCH,
                        mismatch(UnavailableReason.OBSERVED_AT_MISMATCH)),
                Arguments.of(UnavailableReason.PRICE_FIELD_MISMATCH,
                        mismatch(UnavailableReason.PRICE_FIELD_MISMATCH)),
                Arguments.of(UnavailableReason.ADJUSTMENT_BASIS_MISMATCH,
                        mismatch(UnavailableReason.ADJUSTMENT_BASIS_MISMATCH)),
                Arguments.of(UnavailableReason.CORPORATE_ACTION_CONTINUITY_UNAVAILABLE,
                        mismatch(UnavailableReason.CORPORATE_ACTION_CONTINUITY_UNAVAILABLE)));
    }

    @Test
    void appliesFixedMismatchPrecedenceAcrossTheWholeKnownSetBeforeAmbiguity() {
        EndpointPriceObservation laterMismatch = mismatch(UnavailableReason.SESSION_MISMATCH);
        EndpointPriceObservation earlierMismatch = mismatch(UnavailableReason.ASSET_MISMATCH);

        EndpointPriceResolution firstOrder = EndpointPriceSelector.select(
                request(AS_OF, List.of(laterMismatch, earlierMismatch)));
        EndpointPriceResolution reversedOrder = EndpointPriceSelector.select(
                request(AS_OF, List.of(earlierMismatch, laterMismatch)));

        assertUnavailable(firstOrder, UnavailableReason.ASSET_MISMATCH);
        assertUnavailable(reversedOrder, UnavailableReason.ASSET_MISMATCH);

        EndpointPriceObservation exact = exactCandidate("duplicate-exact");
        assertUnavailable(
                EndpointPriceSelector.select(request(AS_OF, List.of(exact, exact))),
                UnavailableReason.OBSERVATION_AMBIGUOUS);
        assertUnavailable(
                EndpointPriceSelector.select(request(
                        AS_OF, List.of(exact, exactCandidate("second-exact")))),
                UnavailableReason.OBSERVATION_AMBIGUOUS);
    }

    @Test
    void enforcesCatalogBindingAndEndpointPitGatePrecedence() {
        CatalogPointInTimeEvidence futureWrongCatalog = catalogEvidence(
                "wrong-calendar", "wrong-revision",
                AS_OF.plusNanos(1_000), AS_OF.plusNanos(2_000));
        EndpointPriceRequest futureWrongRequest = new EndpointPriceRequest(
                policy(), horizonResolution(), futureWrongCatalog, binding(), AS_OF,
                List.of(exactCandidate("exact")));
        assertUnavailable(
                EndpointPriceSelector.select(futureWrongRequest),
                UnavailableReason.CATALOG_NOT_KNOWN_AS_OF);
        CatalogPointInTimeEvidence capturedFutureCatalog = catalogEvidence(
                CALENDAR_ID, CATALOG_REVISION, AS_OF, AS_OF.plusNanos(1_000));
        assertUnavailable(
                EndpointPriceSelector.select(new EndpointPriceRequest(
                        policy(), horizonResolution(), capturedFutureCatalog, binding(), AS_OF,
                        List.of(exactCandidate("exact")))),
                UnavailableReason.CATALOG_NOT_KNOWN_AS_OF);

        CatalogPointInTimeEvidence knownWrongCatalog = catalogEvidence(
                "wrong-calendar", "wrong-revision", KNOWN_BEFORE, KNOWN_BEFORE);
        EndpointPriceRequest knownWrongRequest = new EndpointPriceRequest(
                policy(), horizonResolution(), knownWrongCatalog, binding(), AS_OF,
                List.of(exactCandidate("exact")));
        assertUnavailable(
                EndpointPriceSelector.select(knownWrongRequest),
                UnavailableReason.CATALOG_EVIDENCE_MISMATCH);

        EndpointPriceBinding futureBinding = binding(
                AS_OF.plusNanos(1_000), AS_OF.plusNanos(2_000));
        EndpointPriceRequest futureBindingRequest = new EndpointPriceRequest(
                policy(), horizonResolution(), catalogEvidence(), futureBinding, AS_OF,
                List.of(exactCandidate("exact")));
        assertUnavailable(
                EndpointPriceSelector.select(futureBindingRequest),
                UnavailableReason.BINDING_NOT_KNOWN_AS_OF);
        EndpointPriceBinding capturedFutureBinding = binding(
                AS_OF, AS_OF.plusNanos(1_000));
        assertUnavailable(
                EndpointPriceSelector.select(new EndpointPriceRequest(
                        policy(), horizonResolution(), catalogEvidence(),
                        capturedFutureBinding, AS_OF, List.of(exactCandidate("exact")))),
                UnavailableReason.BINDING_NOT_KNOWN_AS_OF);

        CatalogPointInTimeEvidence catalogAtAsOf = catalogEvidence(
                CALENDAR_ID, CATALOG_REVISION, AS_OF, AS_OF);
        EndpointPriceBinding bindingAtAsOf = binding(AS_OF, AS_OF);
        assertThat(EndpointPriceSelector.select(new EndpointPriceRequest(
                policy(), horizonResolution(), catalogAtAsOf, bindingAtAsOf, AS_OF,
                List.of(exactCandidate("at-as-of")))))
                .isInstanceOf(Resolved.class);

        assertUnavailable(
                EndpointPriceSelector.select(request(
                        ENDPOINT_CLOSE.minusNanos(1_000), List.of())),
                UnavailableReason.ENDPOINT_NOT_REACHED_AS_OF);
    }

    @Test
    void preservesIndependentCatalogBindingAndObservationProvenance() {
        EndpointPriceRequest request = request(AS_OF, List.of(exactCandidate("provenance")));
        Resolved result = (Resolved) EndpointPriceSelector.select(request);

        assertThat(result.context().catalogEvidence().provenanceId())
                .isEqualTo("provenance-calendar");
        assertThat(result.context().binding().provenanceId())
                .isEqualTo("provenance-binding");
        assertThat(result.observation().provenanceId())
                .isEqualTo("provenance-observation-provenance");
        assertThat(result.observation().providerEventId())
                .isEqualTo("provider-event-provenance");
    }

    @Test
    void directResolvedConstructionEnforcesLocalContextConsistencyOnly() {
        EndpointPriceRequest request = request(AS_OF, List.of(exactCandidate("direct")));
        ResolutionContext context = context(request);
        EndpointPriceObservation exact = exactCandidate("direct");
        assertThat(new Resolved(context, exact).observation()).isSameAs(exact);

        assertThatThrownBy(() -> new Resolved(context,
                mismatch(UnavailableReason.CURRENCY_MISMATCH)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("exactly match");
        EndpointPriceObservation future = candidate(
                "future-direct", AS_OF.plusNanos(1_000), AS_OF.plusNanos(2_000),
                ASSET_ID, VENUE_ID, USD, SOURCE_ID, SOURCE_REVISION,
                CALENDAR_ID, CATALOG_REVISION, "session-endpoint", ENDPOINT_CLOSE,
                EndpointPriceField.OFFICIAL_REGULAR_SESSION_CLOSE,
                REQUIRED_ADJUSTMENT,
                CorporateActionContinuity.SPLIT_REVERSE_SPLIT_CONTINUOUS);
        assertThatThrownBy(() -> new Resolved(context, future))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("known and mature");
        assertThatThrownBy(() -> new ResolutionContext(
                policy(), "0".repeat(64), request.horizonResolution(),
                request.catalogEvidence(), request.binding(), AS_OF))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("policyDefinitionHash");
    }

    @Test
    void validatesImmutableRequestsLocalTimesAndPositiveExactDecimals() {
        List<EndpointPriceObservation> mutable = new ArrayList<>();
        mutable.add(exactCandidate("immutable"));
        EndpointPriceRequest request = request(AS_OF, mutable);
        mutable.clear();
        assertThat(request.candidates()).hasSize(1);
        assertThatThrownBy(() -> request.candidates().add(exactCandidate("mutate")))
                .isInstanceOf(UnsupportedOperationException.class);

        assertThatThrownBy(() -> candidateWithPrice("0"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("positive");
        assertThatThrownBy(() -> candidateWithPrice("-1"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("positive");
        assertThatThrownBy(() -> candidateWithPrice("1.0000000000001"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("scale");
        assertThatThrownBy(() -> candidateWithPrice(
                "100000000000000000000000000.000000000000"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("precision");
        assertThatThrownBy(() -> candidate(
                "sub-micro", ENDPOINT_CLOSE,
                Instant.parse("2026-08-21T20:00:30.000000001Z"),
                ASSET_ID, VENUE_ID, USD, SOURCE_ID, SOURCE_REVISION,
                CALENDAR_ID, CATALOG_REVISION, "session-endpoint", ENDPOINT_CLOSE,
                EndpointPriceField.OFFICIAL_REGULAR_SESSION_CLOSE,
                REQUIRED_ADJUSTMENT,
                CorporateActionContinuity.SPLIT_REVERSE_SPLIT_CONTINUOUS))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("microsecond precision");
        assertThatThrownBy(() -> EndpointPriceSelector.select(null))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("request");
    }

    @Test
    void acceptsTheExactNumericMaximumAndPreservesOriginalDecimalRepresentation() {
        BigDecimal maximum =
                new BigDecimal("99999999999999999999999999.999999999999");
        EndpointPriceObservation maximumCandidate = candidateWithPrice(maximum);
        Resolved maximumResult = (Resolved) EndpointPriceSelector.select(
                request(AS_OF, List.of(maximumCandidate)));

        assertThat(maximum.precision()).isEqualTo(38);
        assertThat(maximum.scale()).isEqualTo(12);
        assertThat(maximumResult.observation().price()).isSameAs(maximum);

        BigDecimal scaleZero = new BigDecimal("100");
        BigDecimal scaleEquivalent = new BigDecimal("100.000000000000");
        Resolved scaleZeroResult = (Resolved) EndpointPriceSelector.select(
                request(AS_OF, List.of(candidateWithPrice(scaleZero))));
        Resolved scaleEquivalentResult = (Resolved) EndpointPriceSelector.select(
                request(AS_OF, List.of(candidateWithPrice(scaleEquivalent))));

        assertThat(scaleZeroResult.observation().price()).isSameAs(scaleZero);
        assertThat(scaleZeroResult.observation().price().scale()).isZero();
        assertThat(scaleEquivalentResult.observation().price())
                .isSameAs(scaleEquivalent);
        assertThat(scaleEquivalentResult.observation().price().scale()).isEqualTo(12);
        assertThat(scaleZeroResult.observation().price())
                .isEqualByComparingTo(scaleEquivalentResult.observation().price());
    }

    @Test
    void evidenceConstructorsRejectRepresentativeNullBlankAndTimeOrderMutations() {
        for (String component : List.of(
                "calendarId", "catalogRevision", "sourceId", "sourceRevision",
                "provenanceId")) {
            for (String invalid : java.util.Arrays.asList(
                    null, " ", " untrimmed ")) {
                assertThatThrownBy(() -> catalogWithTextMutation(component, invalid))
                        .isInstanceOfAny(
                                NullPointerException.class,
                                IllegalArgumentException.class);
            }
        }
        for (String component : List.of(
                "bindingId", "bindingRevision", "assetId", "primaryVenueId",
                "priceSourceId", "priceSourceRevision", "provenanceId")) {
            for (String invalid : java.util.Arrays.asList(
                    null, " ", " untrimmed ")) {
                assertThatThrownBy(() -> bindingWithTextMutation(component, invalid))
                        .isInstanceOfAny(
                                NullPointerException.class,
                                IllegalArgumentException.class);
            }
        }
        for (String component : List.of(
                "observationId", "providerEventId", "assetId", "venueId",
                "priceSourceId", "priceSourceRevision", "provenanceId",
                "calendarId", "catalogRevision", "sessionId")) {
            for (String invalid : java.util.Arrays.asList(
                    null, " ", " untrimmed ")) {
                assertThatThrownBy(() -> observationWithTextMutation(component, invalid))
                        .isInstanceOfAny(
                                NullPointerException.class,
                                IllegalArgumentException.class);
            }
        }

        EndpointPriceObservation validObservation = exactCandidate("constructor-valid");
        EndpointPriceRequest validRequest = request(AS_OF, List.of(validObservation));
        ResolutionContext validContext = context(validRequest);
        List<Runnable> invalidConstructors = List.of(
                () -> catalogWithTimes(null, KNOWN_BEFORE),
                () -> catalogWithTimes(KNOWN_BEFORE, null),
                () -> catalogWithTimes(
                        KNOWN_BEFORE.plusNanos(1), KNOWN_BEFORE.plusSeconds(1)),
                () -> catalogWithTimes(KNOWN_BEFORE, KNOWN_BEFORE.plusNanos(1)),
                () -> catalogWithTimes(
                        KNOWN_BEFORE, KNOWN_BEFORE.minusNanos(1_000)),
                () -> bindingWithNullableAndTimes(
                        null, KNOWN_BEFORE, KNOWN_BEFORE),
                () -> bindingWithNullableAndTimes(USD, null, KNOWN_BEFORE),
                () -> bindingWithNullableAndTimes(USD, KNOWN_BEFORE, null),
                () -> bindingWithNullableAndTimes(
                        USD, KNOWN_BEFORE.plusNanos(1), KNOWN_BEFORE.plusSeconds(1)),
                () -> bindingWithNullableAndTimes(
                        USD, KNOWN_BEFORE, KNOWN_BEFORE.plusNanos(1)),
                () -> bindingWithNullableAndTimes(
                        USD, KNOWN_BEFORE, KNOWN_BEFORE.minusNanos(1_000)),
                () -> observationWithNullableAndTimes(
                        null, EndpointPriceField.OFFICIAL_REGULAR_SESSION_CLOSE,
                        REQUIRED_ADJUSTMENT,
                        CorporateActionContinuity.SPLIT_REVERSE_SPLIT_CONTINUOUS,
                        ENDPOINT_CLOSE, ENDPOINT_CLOSE, OBSERVATION_CAPTURED,
                        new BigDecimal("100")),
                () -> observationWithNullableAndTimes(
                        USD, null, REQUIRED_ADJUSTMENT,
                        CorporateActionContinuity.SPLIT_REVERSE_SPLIT_CONTINUOUS,
                        ENDPOINT_CLOSE, ENDPOINT_CLOSE, OBSERVATION_CAPTURED,
                        new BigDecimal("100")),
                () -> observationWithNullableAndTimes(
                        USD, EndpointPriceField.OFFICIAL_REGULAR_SESSION_CLOSE, null,
                        CorporateActionContinuity.SPLIT_REVERSE_SPLIT_CONTINUOUS,
                        ENDPOINT_CLOSE, ENDPOINT_CLOSE, OBSERVATION_CAPTURED,
                        new BigDecimal("100")),
                () -> observationWithNullableAndTimes(
                        USD, EndpointPriceField.OFFICIAL_REGULAR_SESSION_CLOSE,
                        REQUIRED_ADJUSTMENT, null,
                        ENDPOINT_CLOSE, ENDPOINT_CLOSE, OBSERVATION_CAPTURED,
                        new BigDecimal("100")),
                () -> observationWithNullableAndTimes(
                        USD, EndpointPriceField.OFFICIAL_REGULAR_SESSION_CLOSE,
                        REQUIRED_ADJUSTMENT,
                        CorporateActionContinuity.SPLIT_REVERSE_SPLIT_CONTINUOUS,
                        null, ENDPOINT_CLOSE, OBSERVATION_CAPTURED,
                        new BigDecimal("100")),
                () -> observationWithNullableAndTimes(
                        USD, EndpointPriceField.OFFICIAL_REGULAR_SESSION_CLOSE,
                        REQUIRED_ADJUSTMENT,
                        CorporateActionContinuity.SPLIT_REVERSE_SPLIT_CONTINUOUS,
                        ENDPOINT_CLOSE, null, OBSERVATION_CAPTURED,
                        new BigDecimal("100")),
                () -> observationWithNullableAndTimes(
                        USD, EndpointPriceField.OFFICIAL_REGULAR_SESSION_CLOSE,
                        REQUIRED_ADJUSTMENT,
                        CorporateActionContinuity.SPLIT_REVERSE_SPLIT_CONTINUOUS,
                        ENDPOINT_CLOSE, ENDPOINT_CLOSE, null,
                        new BigDecimal("100")),
                () -> observationWithNullableAndTimes(
                        USD, EndpointPriceField.OFFICIAL_REGULAR_SESSION_CLOSE,
                        REQUIRED_ADJUSTMENT,
                        CorporateActionContinuity.SPLIT_REVERSE_SPLIT_CONTINUOUS,
                        ENDPOINT_CLOSE.minusSeconds(1).plusNanos(1),
                        ENDPOINT_CLOSE, OBSERVATION_CAPTURED,
                        new BigDecimal("100")),
                () -> observationWithNullableAndTimes(
                        USD, EndpointPriceField.OFFICIAL_REGULAR_SESSION_CLOSE,
                        REQUIRED_ADJUSTMENT,
                        CorporateActionContinuity.SPLIT_REVERSE_SPLIT_CONTINUOUS,
                        ENDPOINT_CLOSE, ENDPOINT_CLOSE.plusNanos(1),
                        OBSERVATION_CAPTURED, new BigDecimal("100")),
                () -> observationWithNullableAndTimes(
                        USD, EndpointPriceField.OFFICIAL_REGULAR_SESSION_CLOSE,
                        REQUIRED_ADJUSTMENT,
                        CorporateActionContinuity.SPLIT_REVERSE_SPLIT_CONTINUOUS,
                        ENDPOINT_CLOSE, ENDPOINT_CLOSE,
                        OBSERVATION_CAPTURED.plusNanos(1), new BigDecimal("100")),
                () -> observationWithNullableAndTimes(
                        USD, EndpointPriceField.OFFICIAL_REGULAR_SESSION_CLOSE,
                        REQUIRED_ADJUSTMENT,
                        CorporateActionContinuity.SPLIT_REVERSE_SPLIT_CONTINUOUS,
                        ENDPOINT_CLOSE, ENDPOINT_CLOSE.minusNanos(1_000),
                        OBSERVATION_CAPTURED, new BigDecimal("100")),
                () -> observationWithNullableAndTimes(
                        USD, EndpointPriceField.OFFICIAL_REGULAR_SESSION_CLOSE,
                        REQUIRED_ADJUSTMENT,
                        CorporateActionContinuity.SPLIT_REVERSE_SPLIT_CONTINUOUS,
                        ENDPOINT_CLOSE, ENDPOINT_CLOSE.plusSeconds(1),
                        ENDPOINT_CLOSE.plusSeconds(1).minusNanos(1_000),
                        new BigDecimal("100")),
                () -> observationWithNullableAndTimes(
                        USD, EndpointPriceField.OFFICIAL_REGULAR_SESSION_CLOSE,
                        REQUIRED_ADJUSTMENT,
                        CorporateActionContinuity.SPLIT_REVERSE_SPLIT_CONTINUOUS,
                        ENDPOINT_CLOSE, ENDPOINT_CLOSE, OBSERVATION_CAPTURED, null),
                () -> new EndpointPriceRequest(
                        null, horizonResolution(), catalogEvidence(), binding(), AS_OF,
                        List.of(exactCandidate("exact"))),
                () -> new EndpointPriceRequest(
                        policy(), null, catalogEvidence(), binding(), AS_OF,
                        List.of(exactCandidate("exact"))),
                () -> new EndpointPriceRequest(
                        policy(), horizonResolution(), null, binding(), AS_OF,
                        List.of(exactCandidate("exact"))),
                () -> new EndpointPriceRequest(
                        policy(), horizonResolution(), catalogEvidence(), null, AS_OF,
                        List.of(exactCandidate("exact"))),
                () -> new EndpointPriceRequest(
                        policy(), horizonResolution(), catalogEvidence(), binding(), null,
                        List.of(exactCandidate("exact"))),
                () -> new EndpointPriceRequest(
                        policy(), horizonResolution(), catalogEvidence(), binding(), AS_OF,
                        null),
                () -> new EndpointPriceRequest(
                        policy(), horizonResolution(), catalogEvidence(), binding(), AS_OF,
                        java.util.Arrays.asList(exactCandidate("exact"), null)),
                () -> new EndpointPriceRequest(
                        policy(), horizonResolution(), catalogEvidence(), binding(),
                        AS_OF.plusNanos(1), List.of(validObservation)),
                () -> new ResolutionContext(
                        null, POLICY_HASH, horizonResolution(), catalogEvidence(), binding(),
                        AS_OF),
                () -> new ResolutionContext(
                        policy(), null, horizonResolution(), catalogEvidence(), binding(),
                        AS_OF),
                () -> new ResolutionContext(
                        policy(), POLICY_HASH, null, catalogEvidence(), binding(), AS_OF),
                () -> new ResolutionContext(
                        policy(), POLICY_HASH, horizonResolution(), null, binding(), AS_OF),
                () -> new ResolutionContext(
                        policy(), POLICY_HASH, horizonResolution(), catalogEvidence(), null,
                        AS_OF),
                () -> new ResolutionContext(
                        policy(), POLICY_HASH, horizonResolution(), catalogEvidence(),
                        binding(), null),
                () -> new ResolutionContext(
                        policy(), POLICY_HASH, horizonResolution(), catalogEvidence(),
                        binding(), AS_OF.plusNanos(1)),
                () -> new Resolved(null, validObservation),
                () -> new Resolved(validContext, null),
                () -> new Unavailable(null, UnavailableReason.OBSERVATION_MISSING_AS_OF),
                () -> new Unavailable(validContext, null));

        assertThat(invalidConstructors).hasSize(43);
        for (Runnable invalidConstructor : invalidConstructors) {
            assertThatThrownBy(invalidConstructor::run)
                    .isInstanceOfAny(
                            NullPointerException.class,
                            IllegalArgumentException.class);
        }
    }

    @Test
    void resultAndEvidenceSurfacesRemainClosedAndReplayIgnoresJvmDefaults() {
        assertThat(EndpointPricePolicyVersion.values()).containsExactly(policy());
        assertThat(EndpointPriceResolution.class.getPermittedSubclasses())
                .containsExactlyInAnyOrder(Resolved.class, Unavailable.class);
        assertThat(UnavailableReason.values()).containsExactly(
                UnavailableReason.CATALOG_NOT_KNOWN_AS_OF,
                UnavailableReason.CATALOG_EVIDENCE_MISMATCH,
                UnavailableReason.BINDING_NOT_KNOWN_AS_OF,
                UnavailableReason.ENDPOINT_NOT_REACHED_AS_OF,
                UnavailableReason.OBSERVATION_MISSING_AS_OF,
                UnavailableReason.ASSET_MISMATCH,
                UnavailableReason.PRIMARY_VENUE_MISMATCH,
                UnavailableReason.CURRENCY_MISMATCH,
                UnavailableReason.SOURCE_MISMATCH,
                UnavailableReason.CATALOG_MISMATCH,
                UnavailableReason.SESSION_MISMATCH,
                UnavailableReason.OBSERVED_AT_MISMATCH,
                UnavailableReason.PRICE_FIELD_MISMATCH,
                UnavailableReason.ADJUSTMENT_BASIS_MISMATCH,
                UnavailableReason.CORPORATE_ACTION_CONTINUITY_UNAVAILABLE,
                UnavailableReason.OBSERVATION_AMBIGUOUS);
        assertRecordComponents(EndpointPriceRequest.class,
                "policyVersion:EndpointPricePolicyVersion",
                "horizonResolution:Resolved",
                "catalogEvidence:CatalogPointInTimeEvidence",
                "binding:EndpointPriceBinding",
                "evaluationAsOf:Instant",
                "candidates:List");
        assertRecordComponents(CatalogPointInTimeEvidence.class,
                "calendarId:String", "catalogRevision:String", "sourceId:String",
                "sourceRevision:String", "availableAt:Instant", "capturedAt:Instant",
                "provenanceId:String");
        assertRecordComponents(EndpointPriceBinding.class,
                "bindingId:String", "bindingRevision:String", "assetId:String",
                "primaryVenueId:String", "currency:Currency", "priceSourceId:String",
                "priceSourceRevision:String", "availableAt:Instant", "capturedAt:Instant",
                "provenanceId:String");
        assertRecordComponents(EndpointPriceObservation.class,
                "observationId:String", "providerEventId:String", "assetId:String",
                "venueId:String", "currency:Currency", "priceSourceId:String",
                "priceSourceRevision:String", "provenanceId:String", "calendarId:String",
                "catalogRevision:String", "sessionId:String", "priceField:EndpointPriceField",
                "adjustmentBasis:EndpointPriceAdjustmentBasis",
                "corporateActionContinuity:CorporateActionContinuity", "observedAt:Instant",
                "availableAt:Instant", "capturedAt:Instant", "price:BigDecimal");
        assertRecordComponents(ResolutionContext.class,
                "policyVersion:EndpointPricePolicyVersion", "policyDefinitionHash:String",
                "horizonResolution:Resolved", "catalogEvidence:CatalogPointInTimeEvidence",
                "binding:EndpointPriceBinding", "evaluationAsOf:Instant");
        assertRecordComponents(Resolved.class,
                "context:ResolutionContext", "observation:EndpointPriceObservation");
        assertRecordComponents(Unavailable.class,
                "context:ResolutionContext", "reason:UnavailableReason");
        assertThat(EndpointPriceField.values()).containsExactly(
                EndpointPriceField.OFFICIAL_REGULAR_SESSION_CLOSE,
                EndpointPriceField.INDICATIVE_OR_OTHER);
        assertThat(EndpointPriceAdjustmentBasis.values()).containsExactly(
                REQUIRED_ADJUSTMENT,
                EndpointPriceAdjustmentBasis.UNADJUSTED_OR_OTHER,
                EndpointPriceAdjustmentBasis.DIVIDEND_OR_TOTAL_RETURN_ADJUSTED);
        assertThat(CorporateActionContinuity.values()).containsExactly(
                CorporateActionContinuity.SPLIT_REVERSE_SPLIT_CONTINUOUS,
                CorporateActionContinuity.MERGER,
                CorporateActionContinuity.SPIN_OFF,
                CorporateActionContinuity.DELISTING,
                CorporateActionContinuity.SPECIAL_DISTRIBUTION,
                CorporateActionContinuity.UNKNOWN);

        Locale originalLocale = Locale.getDefault();
        TimeZone originalTimeZone = TimeZone.getDefault();
        try {
            EndpointPriceRequest request = request(AS_OF, List.of(exactCandidate("replay")));
            EndpointPriceResolution expected = EndpointPriceSelector.select(request);
            Locale.setDefault(Locale.KOREA);
            TimeZone.setDefault(TimeZone.getTimeZone("Pacific/Kiritimati"));
            assertThat(EndpointPriceSelector.select(request)).isEqualTo(expected);
        } finally {
            Locale.setDefault(originalLocale);
            TimeZone.setDefault(originalTimeZone);
        }
    }

    private static EndpointPricePolicyVersion policy() {
        return EndpointPricePolicyVersion.OFFICIAL_PRIMARY_VENUE_CLOSE_SPLIT_ADJUSTED_V1;
    }

    private static EndpointPriceRequest request(
            Instant evaluationAsOf,
            List<EndpointPriceObservation> candidates) {
        return new EndpointPriceRequest(
                policy(), horizonResolution(), catalogEvidence(), binding(),
                evaluationAsOf, candidates);
    }

    private static ResolutionContext context(EndpointPriceRequest request) {
        return new ResolutionContext(
                request.policyVersion(), POLICY_HASH, request.horizonResolution(),
                request.catalogEvidence(), request.binding(), request.evaluationAsOf());
    }

    private static com.wallstreetreceipts.api.domain.outcome.horizon
            .SessionCloseHorizonResolution.Resolved horizonResolution() {
        Original basis = new Original("call-endpoint", BASIS_TIME);
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
        return catalogEvidence(CALENDAR_ID, CATALOG_REVISION, KNOWN_BEFORE, KNOWN_BEFORE);
    }

    private static CatalogPointInTimeEvidence catalogEvidence(
            String calendarId,
            String revision,
            Instant availableAt,
            Instant capturedAt) {
        return new CatalogPointInTimeEvidence(
                calendarId, revision, "source-calendar", "calendar-source-revision-1",
                availableAt, capturedAt, "provenance-calendar");
    }

    private static EndpointPriceBinding binding() {
        return binding(KNOWN_BEFORE, KNOWN_BEFORE);
    }

    private static EndpointPriceBinding binding(Instant availableAt, Instant capturedAt) {
        return new EndpointPriceBinding(
                "binding-nvda-xnas", "binding-revision-1", ASSET_ID, VENUE_ID, USD,
                SOURCE_ID, SOURCE_REVISION, availableAt, capturedAt,
                "provenance-binding");
    }

    private static EndpointPriceObservation exactCandidate(String id) {
        return candidate(
                id, ENDPOINT_CLOSE, OBSERVATION_CAPTURED,
                ASSET_ID, VENUE_ID, USD, SOURCE_ID, SOURCE_REVISION,
                CALENDAR_ID, CATALOG_REVISION, "session-endpoint", ENDPOINT_CLOSE,
                EndpointPriceField.OFFICIAL_REGULAR_SESSION_CLOSE,
                REQUIRED_ADJUSTMENT,
                CorporateActionContinuity.SPLIT_REVERSE_SPLIT_CONTINUOUS);
    }

    private static EndpointPriceObservation mismatch(UnavailableReason reason) {
        return candidate(
                "mismatch-" + reason.name().toLowerCase(Locale.ROOT),
                ENDPOINT_CLOSE, OBSERVATION_CAPTURED,
                reason == UnavailableReason.ASSET_MISMATCH ? "asset-other" : ASSET_ID,
                reason == UnavailableReason.PRIMARY_VENUE_MISMATCH ? "venue-other" : VENUE_ID,
                reason == UnavailableReason.CURRENCY_MISMATCH
                        ? Currency.getInstance("EUR") : USD,
                reason == UnavailableReason.SOURCE_MISMATCH ? "source-other" : SOURCE_ID,
                SOURCE_REVISION,
                reason == UnavailableReason.CATALOG_MISMATCH
                        ? "calendar-other" : CALENDAR_ID,
                CATALOG_REVISION,
                reason == UnavailableReason.SESSION_MISMATCH
                        ? "session-other" : "session-endpoint",
                reason == UnavailableReason.OBSERVED_AT_MISMATCH
                        ? ENDPOINT_CLOSE.minusSeconds(1) : ENDPOINT_CLOSE,
                reason == UnavailableReason.PRICE_FIELD_MISMATCH
                        ? EndpointPriceField.INDICATIVE_OR_OTHER
                        : EndpointPriceField.OFFICIAL_REGULAR_SESSION_CLOSE,
                reason == UnavailableReason.ADJUSTMENT_BASIS_MISMATCH
                        ? EndpointPriceAdjustmentBasis.DIVIDEND_OR_TOTAL_RETURN_ADJUSTED
                        : REQUIRED_ADJUSTMENT,
                reason == UnavailableReason.CORPORATE_ACTION_CONTINUITY_UNAVAILABLE
                        ? CorporateActionContinuity.SPIN_OFF
                        : CorporateActionContinuity.SPLIT_REVERSE_SPLIT_CONTINUOUS);
    }

    private static EndpointPriceObservation candidateWithPrice(String price) {
        return candidateWithPrice(new BigDecimal(price));
    }

    private static EndpointPriceObservation candidateWithPrice(BigDecimal price) {
        return new EndpointPriceObservation(
                "observation-price", "provider-event-price", ASSET_ID, VENUE_ID, USD,
                SOURCE_ID, SOURCE_REVISION, "provenance-observation-price",
                CALENDAR_ID, CATALOG_REVISION, "session-endpoint",
                EndpointPriceField.OFFICIAL_REGULAR_SESSION_CLOSE,
                REQUIRED_ADJUSTMENT,
                CorporateActionContinuity.SPLIT_REVERSE_SPLIT_CONTINUOUS,
                ENDPOINT_CLOSE, ENDPOINT_CLOSE, OBSERVATION_CAPTURED,
                price);
    }

    private static CatalogPointInTimeEvidence catalogWithTextMutation(
            String component,
            String value) {
        return new CatalogPointInTimeEvidence(
                "calendarId".equals(component) ? value : CALENDAR_ID,
                "catalogRevision".equals(component) ? value : CATALOG_REVISION,
                "sourceId".equals(component) ? value : "source-calendar",
                "sourceRevision".equals(component) ? value : "calendar-source-revision-1",
                KNOWN_BEFORE, KNOWN_BEFORE,
                "provenanceId".equals(component) ? value : "provenance-calendar");
    }

    private static CatalogPointInTimeEvidence catalogWithTimes(
            Instant availableAt,
            Instant capturedAt) {
        return new CatalogPointInTimeEvidence(
                CALENDAR_ID, CATALOG_REVISION, "source-calendar",
                "calendar-source-revision-1", availableAt, capturedAt,
                "provenance-calendar");
    }

    private static EndpointPriceBinding bindingWithTextMutation(
            String component,
            String value) {
        return new EndpointPriceBinding(
                "bindingId".equals(component) ? value : "binding-nvda-xnas",
                "bindingRevision".equals(component) ? value : "binding-revision-1",
                "assetId".equals(component) ? value : ASSET_ID,
                "primaryVenueId".equals(component) ? value : VENUE_ID,
                USD,
                "priceSourceId".equals(component) ? value : SOURCE_ID,
                "priceSourceRevision".equals(component) ? value : SOURCE_REVISION,
                KNOWN_BEFORE, KNOWN_BEFORE,
                "provenanceId".equals(component) ? value : "provenance-binding");
    }

    private static EndpointPriceBinding bindingWithNullableAndTimes(
            Currency currency,
            Instant availableAt,
            Instant capturedAt) {
        return new EndpointPriceBinding(
                "binding-nvda-xnas", "binding-revision-1", ASSET_ID, VENUE_ID,
                currency, SOURCE_ID, SOURCE_REVISION, availableAt, capturedAt,
                "provenance-binding");
    }

    private static EndpointPriceObservation observationWithTextMutation(
            String component,
            String value) {
        return new EndpointPriceObservation(
                "observationId".equals(component) ? value : "observation",
                "providerEventId".equals(component) ? value : "provider-event",
                "assetId".equals(component) ? value : ASSET_ID,
                "venueId".equals(component) ? value : VENUE_ID,
                USD,
                "priceSourceId".equals(component) ? value : SOURCE_ID,
                "priceSourceRevision".equals(component) ? value : SOURCE_REVISION,
                "provenanceId".equals(component) ? value : "provenance-observation",
                "calendarId".equals(component) ? value : CALENDAR_ID,
                "catalogRevision".equals(component) ? value : CATALOG_REVISION,
                "sessionId".equals(component) ? value : "session-endpoint",
                EndpointPriceField.OFFICIAL_REGULAR_SESSION_CLOSE,
                REQUIRED_ADJUSTMENT,
                CorporateActionContinuity.SPLIT_REVERSE_SPLIT_CONTINUOUS,
                ENDPOINT_CLOSE, ENDPOINT_CLOSE, OBSERVATION_CAPTURED,
                new BigDecimal("100"));
    }

    private static EndpointPriceObservation observationWithNullableAndTimes(
            Currency currency,
            EndpointPriceField priceField,
            EndpointPriceAdjustmentBasis adjustmentBasis,
            CorporateActionContinuity continuity,
            Instant observedAt,
            Instant availableAt,
            Instant capturedAt,
            BigDecimal price) {
        return new EndpointPriceObservation(
                "observation", "provider-event", ASSET_ID, VENUE_ID, currency,
                SOURCE_ID, SOURCE_REVISION, "provenance-observation",
                CALENDAR_ID, CATALOG_REVISION, "session-endpoint",
                priceField, adjustmentBasis, continuity,
                observedAt, availableAt, capturedAt,
                price);
    }

    private static EndpointPriceObservation candidate(
            String id,
            Instant availableAt,
            Instant capturedAt,
            String assetId,
            String venueId,
            Currency currency,
            String sourceId,
            String sourceRevision,
            String calendarId,
            String catalogRevision,
            String sessionId,
            Instant observedAt,
            EndpointPriceField priceField,
            EndpointPriceAdjustmentBasis adjustmentBasis,
            CorporateActionContinuity continuity) {
        return new EndpointPriceObservation(
                "observation-" + id,
                "provider-event-" + id,
                assetId,
                venueId,
                currency,
                sourceId,
                sourceRevision,
                "provenance-observation-" + id,
                calendarId,
                catalogRevision,
                sessionId,
                priceField,
                adjustmentBasis,
                continuity,
                observedAt,
                availableAt,
                capturedAt,
                new BigDecimal("100.000000000000"));
    }

    private static void assertUnavailable(
            EndpointPriceResolution resolution,
            UnavailableReason reason) {
        assertThat(resolution).isEqualTo(new Unavailable(
                ((Unavailable) resolution).context(), reason));
    }

    private static void assertRecordComponents(Class<?> type, String... expected) {
        assertThat(Stream.of(type.getRecordComponents())
                .map(component -> component.getName() + ":"
                        + component.getType().getSimpleName()))
                .containsExactly(expected);
    }
}
