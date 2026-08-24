package com.wallstreetreceipts.api.domain.outcome.targethitorchestration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.Currency;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.TimeZone;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;

import com.wallstreetreceipts.api.domain.call.CallDirection;
import com.wallstreetreceipts.api.domain.outcome.OutcomeHorizon;
import com.wallstreetreceipts.api.domain.outcome.calculation.TargetHitResult;
import com.wallstreetreceipts.api.domain.outcome.direction.CallDirectionPolarityPolicyVersion;
import com.wallstreetreceipts.api.domain.outcome.direction.CallDirectionPolarityRequest;
import com.wallstreetreceipts.api.domain.outcome.direction.CallDirectionPolarityResolver;
import com.wallstreetreceipts.api.domain.outcome.favorableextreme.FavorableExtremePolicyVersion;
import com.wallstreetreceipts.api.domain.outcome.favorableextreme.FavorableExtremeRequest;
import com.wallstreetreceipts.api.domain.outcome.favorableextreme.FavorableExtremeResolution;
import com.wallstreetreceipts.api.domain.outcome.favorableextreme.FavorableExtremeSelector;
import com.wallstreetreceipts.api.domain.outcome.favorableextreme.FullWindowHighLowObservation;
import com.wallstreetreceipts.api.domain.outcome.favorableextreme.FullWindowHighLowObservation.BoundaryType;
import com.wallstreetreceipts.api.domain.outcome.favorableextreme.FullWindowHighLowObservation.WindowCoverageCompleteness;
import com.wallstreetreceipts.api.domain.outcome.favorableextreme.FullWindowHighLowObservation.WindowPriceField;
import com.wallstreetreceipts.api.domain.outcome.favorableextreme.WindowPriceBinding;
import com.wallstreetreceipts.api.domain.outcome.horizon.OutcomeBasis;
import com.wallstreetreceipts.api.domain.outcome.horizon.SessionCloseHorizonPolicyVersion;
import com.wallstreetreceipts.api.domain.outcome.horizon.SessionCloseHorizonResolution;
import com.wallstreetreceipts.api.domain.outcome.horizon.TradingSession;
import com.wallstreetreceipts.api.domain.outcome.observation.CatalogPointInTimeEvidence;
import com.wallstreetreceipts.api.domain.outcome.observation.CorporateActionContinuity;
import com.wallstreetreceipts.api.domain.outcome.observation.EndpointPriceAdjustmentBasis;
import com.wallstreetreceipts.api.domain.outcome.routing.CalculatorSideRouting;
import com.wallstreetreceipts.api.domain.outcome.targeteligibility.BasisForecastTermsEvidence;
import com.wallstreetreceipts.api.domain.outcome.targeteligibility.BasisForecastTermsEvidence.TargetDisposition.Absent;
import com.wallstreetreceipts.api.domain.outcome.targeteligibility.BasisForecastTermsEvidence.TargetDisposition.Present;
import com.wallstreetreceipts.api.domain.outcome.targeteligibility.TargetEligibilityPolicyVersion;
import com.wallstreetreceipts.api.domain.outcome.targeteligibility.TargetEligibilityRequest;
import com.wallstreetreceipts.api.domain.outcome.targeteligibility.TargetEligibilityResolution;
import com.wallstreetreceipts.api.domain.outcome.targeteligibility.TargetEligibilityResolver;
import com.wallstreetreceipts.api.domain.outcome.targeterror.TargetPriceEvidence;
import com.wallstreetreceipts.api.domain.outcome.targethitorchestration.TargetHitOrchestrationResolution.Available;
import com.wallstreetreceipts.api.domain.outcome.targethitorchestration.TargetHitOrchestrationResolution.EligibilityUnavailable;
import com.wallstreetreceipts.api.domain.outcome.targethitorchestration.TargetHitOrchestrationResolution.FavorableExtremeUnavailable;
import com.wallstreetreceipts.api.domain.outcome.targethitorchestration.TargetHitOrchestrationResolution.NotApplicable;
import com.wallstreetreceipts.api.domain.outcome.targethitorchestration.TargetHitOrchestrationResolution.Pending;

class TargetHitOrchestratorGoldenTest {

    private static final String POLICY_HASH =
            "b91bf68958e42ad003b80973c74f9acc2dad8e4629f6a1905798df98aa8b5348";
    private static final String HORIZON_HASH =
            "550087efe7ddf2ba31974c89c2740ab79df986eefef48919c32c56a3232f8dc1";
    private static final String CALENDAR_ID = "calendar-xnas";
    private static final String CATALOG_REVISION = "calendar-revision-9";
    private static final String ASSET_ID = "asset-nvda";
    private static final String VENUE_ID = "venue-xnas";
    private static final String SOURCE_ID = "source-window-bars";
    private static final String SOURCE_REVISION = "source-revision-4";
    private static final Currency USD = Currency.getInstance("USD");
    private static final Instant OPEN = Instant.parse("2026-08-20T13:30:00Z");
    private static final Instant BASIS_TIME = Instant.parse("2026-08-20T15:00:00Z");
    private static final Instant CLOSE = Instant.parse("2026-08-20T20:00:00Z");
    private static final Instant AS_OF = Instant.parse("2026-08-20T20:01:00Z");
    private static final OutcomeBasis ORIGINAL =
            new OutcomeBasis.Original("call-1", BASIS_TIME);
    private static final EndpointPriceAdjustmentBasis REQUIRED_ADJUSTMENT =
            EndpointPriceAdjustmentBasis
                    .SPLIT_AND_REVERSE_SPLIT_ADJUSTED_TO_ENDPOINT_SHARE_BASIS_DIVIDEND_UNADJUSTED;

    @Test
    void canonicalDefinitionHasExactBytesHashAndDefensiveReads() throws Exception {
        byte[] bytes = policy().canonicalDefinitionUtf8();

        assertThat(bytes).hasSize(3082);
        assertThat(HexFormat.of().formatHex(
                MessageDigest.getInstance("SHA-256").digest(bytes)))
                .isEqualTo(POLICY_HASH)
                .isEqualTo(policy().definitionHash());
        assertThat(policy().canonicalDefinition())
                .contains("EXACTLY_ONCE_ONLY_FOR_READY_AND_RESOLVED")
                .contains("PRESERVE_EXACT_TYPED_LEAF_RESOLUTION_WITHOUT_REASON_MAPPING")
                .contains("LOCAL_CONSISTENCY_ONLY_NO_REQUEST_MEMBERSHIP")
                .contains("\"eligibilityResolverInvocation\":\"ABSENT\"")
                .contains("\"favorableExtremeSelectorInvocation\":\"ABSENT\"")
                .endsWith("\"fallbackBehavior\":\"ABSENT\"}");

        bytes[0] = (byte) '!';
        assertThat(policy().canonicalDefinitionUtf8()[0]).isEqualTo((byte) '{');
    }

    @Test
    void exactFileRecordSealedAndDisconnectedSurfacesAreStable() throws Exception {
        Path packagePath = Path.of(
                "src/main/java/com/wallstreetreceipts/api/domain/outcome/targethitorchestration");
        try (var files = Files.list(packagePath)) {
            assertThat(files.filter(path -> path.toString().endsWith(".java"))
                    .map(path -> path.getFileName().toString()).sorted().toList())
                    .containsExactly(
                            "TargetHitOrchestrationPolicyVersion.java",
                            "TargetHitOrchestrationRequest.java",
                            "TargetHitOrchestrationResolution.java",
                            "TargetHitOrchestrator.java");
        }
        assertRecordComponents(TargetHitOrchestrationRequest.class,
                "policyVersion:TargetHitOrchestrationPolicyVersion",
                "eligibilityResolution:TargetEligibilityResolution",
                "favorableExtremeResolution:FavorableExtremeResolution");
        assertRecordComponents(TargetHitOrchestrationResolution.ResolutionContext.class,
                "policyVersion:TargetHitOrchestrationPolicyVersion",
                "policyDefinitionHash:String");
        assertRecordComponents(Available.class, "context:ResolutionContext",
                "favorableExtremeResolution:Resolved",
                "targetHitResult:Available");
        assertRecordComponents(Pending.class, "context:ResolutionContext",
                "eligibilityResolution:Pending");
        assertRecordComponents(NotApplicable.class, "context:ResolutionContext",
                "eligibilityResolution:NotApplicable");
        assertRecordComponents(EligibilityUnavailable.class,
                "context:ResolutionContext", "eligibilityResolution:Unavailable");
        assertRecordComponents(FavorableExtremeUnavailable.class,
                "context:ResolutionContext",
                "favorableExtremeResolution:Unavailable");
        assertThat(TargetHitOrchestrationResolution.class.isSealed()).isTrue();
        assertThat(Arrays.stream(TargetHitOrchestrationResolution.class
                .getPermittedSubclasses()).map(Class::getSimpleName).toList())
                .containsExactlyInAnyOrder("Available", "Pending", "NotApplicable",
                        "EligibilityUnavailable", "FavorableExtremeUnavailable");
        assertThat(TargetHitOrchestrationPolicyVersion.values())
                .containsExactly(policy());

        String orchestrator = Files.readString(packagePath.resolve(
                "TargetHitOrchestrator.java"));
        assertThat(count(orchestrator, "TargetHitCalculator.calculate("))
                .isEqualTo(1);
        String combined;
        try (var files = Files.walk(packagePath)) {
            combined = files.filter(path -> path.toString().endsWith(".java"))
                    .map(TargetHitOrchestratorGoldenTest::readText)
                    .reduce("", (left, right) -> left + right);
        }
        assertThat(combined)
                .doesNotContain("TargetEligibilityResolver")
                .doesNotContain("FavorableExtremeSelector")
                .doesNotContain("EndpointPriceResolution")
                .doesNotContain("CallOutcome")
                .doesNotContain("ScoringMethodology")
                .doesNotContain("@Service")
                .doesNotContain("@Repository")
                .doesNotContain("@Controller");
    }

    @Test
    void bullishAndBearishMissesUseNoToleranceOrFallback() {
        var bullish = ready(CallDirection.BULLISH, ORIGINAL,
                new BigDecimal("235"), new BigDecimal("235"), REQUIRED_ADJUSTMENT,
                AS_OF);
        var bullishExtreme = resolvedExtreme(
                bullish, new BigDecimal("234.999999999999"), new BigDecimal("100"));
        var bearish = ready(CallDirection.BEARISH, ORIGINAL,
                new BigDecimal("235"), new BigDecimal("235"), REQUIRED_ADJUSTMENT,
                AS_OF);
        var bearishExtreme = resolvedExtreme(
                bearish, new BigDecimal("300"), new BigDecimal("235.000000000001"));

        assertThat(available(bullish, bullishExtreme).targetHitResult().targetHit())
                .isFalse();
        assertThat(available(bearish, bearishExtreme).targetHitResult().targetHit())
                .isFalse();
    }

    @Test
    void equalityIsAHitForBothSidesAndResolvedLeafIsPreserved() {
        var bullish = defaultReady(CallDirection.BULLISH);
        var bullishExtreme = resolvedExtreme(
                bullish, new BigDecimal("235.0000"), new BigDecimal("200"));
        var bearish = defaultReady(CallDirection.BEARISH);
        var bearishExtreme = resolvedExtreme(
                bearish, new BigDecimal("260"), new BigDecimal("235.00"));

        Available bullishResult = available(bullish, bullishExtreme);
        Available bearishResult = available(bearish, bearishExtreme);
        assertThat(bullishResult.targetHitResult().targetHit()).isTrue();
        assertThat(bearishResult.targetHitResult().targetHit()).isTrue();
        assertThat(bullishResult.favorableExtremeResolution())
                .isSameAs(bullishExtreme);
        assertThat(bearishResult.favorableExtremeResolution())
                .isSameAs(bearishExtreme);
    }

    @Test
    void pendingEligibilityIsPreservedWithoutBoolean() {
        var pending = (TargetEligibilityResolution.Pending) completeEligibility(
                ORIGINAL, CallDirection.BULLISH, new Present(
                        new BigDecimal("235"), USD, null),
                target(ORIGINAL, ASSET_ID, USD, new BigDecimal("235")),
                exactCatalog(), resolvedHorizon(ORIGINAL), CLOSE.minusNanos(1_000));

        var result = TargetHitOrchestrator.orchestrate(request(pending, null));

        assertThat(result).isInstanceOfSatisfying(Pending.class, value -> {
            assertThat(value.eligibilityResolution()).isSameAs(pending);
            assertThat(value.eligibilityResolution().reason()).isEqualTo(
                    TargetEligibilityResolution.PendingReason
                            .HORIZON_NOT_REACHED_AS_OF);
        });
    }

    @Test
    void requestRejectsMissingReadyEvidenceAndEvidenceOnNonReadyBranches() {
        var ready = defaultReady(CallDirection.BULLISH);
        var pending = (TargetEligibilityResolution.Pending) completeEligibility(
                ORIGINAL, CallDirection.BULLISH, new Present(
                        new BigDecimal("235"), USD, null),
                target(ORIGINAL, ASSET_ID, USD, new BigDecimal("235")),
                exactCatalog(), resolvedHorizon(ORIGINAL), CLOSE.minusNanos(1_000));
        var resolved = resolvedExtreme(ready,
                new BigDecimal("240"), new BigDecimal("200"));

        assertThatThrownBy(() -> request(ready, null))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> request(pending, resolved))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void wholeRecordEqualityAllowsReplayButRejectsAnotherReadyContext() {
        var first = defaultReady(CallDirection.BULLISH);
        var equalReplay = defaultReady(CallDirection.BULLISH);
        var resolved = resolvedExtreme(first,
                new BigDecimal("240"), new BigDecimal("200"));
        assertThat(equalReplay).isEqualTo(first).isNotSameAs(first);

        assertThat(TargetHitOrchestrator.orchestrate(
                request(equalReplay, resolved))).isInstanceOf(Available.class);

        var different = defaultReady(CallDirection.BEARISH);
        assertThatThrownBy(() -> request(different, resolved))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void directResultConstructorsEnforceLocalPolicyAndBranchShape() {
        var ready = defaultReady(CallDirection.BULLISH);
        var resolved = resolvedExtreme(ready,
                new BigDecimal("240"), new BigDecimal("200"));
        var context = new TargetHitOrchestrationResolution.ResolutionContext(
                policy(), POLICY_HASH);

        assertThatThrownBy(() -> new TargetHitOrchestrationResolution
                .ResolutionContext(policy(), "wrong-hash"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new Available(context, resolved, null))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new FavorableExtremeUnavailable(context, null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void normalizedTargetEvidenceWinsOverDifferentSourceTarget() {
        var ready = ready(CallDirection.BULLISH, ORIGINAL,
                new BigDecimal("999"), new BigDecimal("235.0000"),
                REQUIRED_ADJUSTMENT, AS_OF);
        var resolved = resolvedExtreme(ready,
                new BigDecimal("235.0000"), new BigDecimal("100"));

        Available result = available(ready, resolved);

        assertThat(ready.evidence().termsEvidence().targetDisposition())
                .isEqualTo(new Present(new BigDecimal("999"), USD, null));
        assertThat(ready.evidence().targetEvidence().target())
                .isEqualTo(new BigDecimal("235.0000"));
        assertThat(result.targetHitResult().targetHit()).isTrue();
    }

    @Test
    void correctionBasisIsComposedIndependentlyWithoutLatestInference() {
        OutcomeBasis correction = new OutcomeBasis.Correction(
                "call-1", "revision-2", BASIS_TIME.plusSeconds(60));
        var ready = ready(CallDirection.BEARISH, correction,
                new BigDecimal("235"), new BigDecimal("235"),
                REQUIRED_ADJUSTMENT, AS_OF);
        var resolved = resolvedExtreme(ready,
                new BigDecimal("250"), new BigDecimal("220"));

        Available result = available(ready, resolved);

        assertThat(result.targetHitResult().targetHit()).isTrue();
        var horizon = (SessionCloseHorizonResolution.Resolved) result
                .favorableExtremeResolution().context().readyEligibility()
                .context().horizonResolution();
        assertThat(horizon.window().context().basis()).isSameAs(correction);
    }

    @Test
    void compositionIsIndependentOfLocaleTimezoneAndPriorInvocations() {
        var ready = defaultReady(CallDirection.STRONG_BEARISH);
        var resolved = resolvedExtreme(ready,
                new BigDecimal("250"), new BigDecimal("220"));
        var request = request(ready, resolved);
        var expected = TargetHitOrchestrator.orchestrate(request);
        Locale originalLocale = Locale.getDefault();
        TimeZone originalZone = TimeZone.getDefault();
        try {
            Locale.setDefault(Locale.JAPAN);
            TimeZone.setDefault(TimeZone.getTimeZone("Pacific/Honolulu"));
            available(defaultReady(CallDirection.BULLISH), resolvedExtreme(
                    defaultReady(CallDirection.BULLISH),
                    new BigDecimal("240"), new BigDecimal("200")));
            assertThat(TargetHitOrchestrator.orchestrate(request))
                    .isEqualTo(expected);
        } finally {
            Locale.setDefault(originalLocale);
            TimeZone.setDefault(originalZone);
        }
    }

    @Test
    void nullRootsAndCompetingPolicyInputsFailClosed() {
        var ready = defaultReady(CallDirection.BULLISH);
        var resolved = resolvedExtreme(ready,
                new BigDecimal("240"), new BigDecimal("200"));

        assertThatThrownBy(() -> TargetHitOrchestrator.orchestrate(null))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new TargetHitOrchestrationRequest(
                null, ready, resolved)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new TargetHitOrchestrationRequest(
                policy(), null, null)).isInstanceOf(NullPointerException.class);
    }

    @ParameterizedTest
    @EnumSource(value = CallDirection.class, names = {
            "STRONG_BULLISH", "BULLISH", "BEARISH", "STRONG_BEARISH"})
    void allDirectionalRoutesUseOnlyTheirPreservedSideAndSelectedField(
            CallDirection direction) {
        var ready = defaultReady(direction);
        var resolved = resolvedExtreme(ready,
                new BigDecimal("240.5000"), new BigDecimal("170.250"));

        Available result = available(ready, resolved);

        boolean bullish = direction == CallDirection.STRONG_BULLISH
                || direction == CallDirection.BULLISH;
        var route = (CalculatorSideRouting.DirectionalRoute)
                ready.evidence().sideRouting();
        assertThat(route.source().context().direction()).isEqualTo(direction);
        assertThat(route.targetHitSide().name())
                .isEqualTo(bullish ? "BULLISH" : "BEARISH");
        assertThat(resolved.favorableExtreme().field().name())
                .isEqualTo(bullish ? "HIGH" : "LOW");
        assertThat(result.targetHitResult().targetHit()).isTrue();
        assertThat(result.favorableExtremeResolution()).isSameAs(resolved);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("notApplicableScenarios")
    void everyNotApplicableReasonPreservesTheExactTypedLeaf(
            TargetEligibilityResolution.NotApplicable leaf) {
        var result = TargetHitOrchestrator.orchestrate(request(leaf, null));

        assertThat(result).isInstanceOfSatisfying(NotApplicable.class,
                value -> assertThat(value.eligibilityResolution()).isSameAs(leaf));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("eligibilityUnavailableScenarios")
    void everyEligibilityUnavailableReasonAndNestedHorizonReasonIsPreserved(
            TargetEligibilityResolution.Unavailable leaf) {
        var result = TargetHitOrchestrator.orchestrate(request(leaf, null));

        assertThat(result).isInstanceOfSatisfying(EligibilityUnavailable.class,
                value -> {
                    assertThat(value.eligibilityResolution()).isSameAs(leaf);
                    assertThat(value.eligibilityResolution().reason())
                            .isSameAs(leaf.reason());
                    assertThat(value.eligibilityResolution().horizonReason())
                            .isSameAs(leaf.horizonReason());
                });
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("favorableUnavailableScenarios")
    void everyFavorableExtremeUnavailableReasonPreservesTheExactTypedLeaf(
            FavorableExtremeResolution.Unavailable leaf) {
        var ready = leaf.context().readyEligibility();
        var result = TargetHitOrchestrator.orchestrate(request(ready, leaf));

        assertThat(result).isInstanceOfSatisfying(
                FavorableExtremeUnavailable.class,
                value -> {
                    assertThat(value.favorableExtremeResolution()).isSameAs(leaf);
                    assertThat(value.favorableExtremeResolution().reason())
                            .isSameAs(leaf.reason());
                });
    }

    private static Stream<TargetEligibilityResolution.NotApplicable>
            notApplicableScenarios() {
        return Stream.of(
                notApplicable(CallDirection.BULLISH, new Absent()),
                notApplicable(CallDirection.NEUTRAL,
                        new Present(new BigDecimal("235"), USD, null)),
                notApplicable(CallDirection.NEUTRAL, new Absent()));
    }

    private static TargetEligibilityResolution.NotApplicable notApplicable(
            CallDirection direction,
            BasisForecastTermsEvidence.TargetDisposition disposition) {
        return (TargetEligibilityResolution.NotApplicable) completeEligibility(
                ORIGINAL, direction, disposition, null, null,
                resolvedHorizon(ORIGINAL), AS_OF);
    }

    private static Stream<TargetEligibilityResolution.Unavailable>
            eligibilityUnavailableScenarios() {
        return Arrays.stream(TargetEligibilityResolution.UnavailableReason.values())
                .map(TargetHitOrchestratorGoldenTest::eligibilityUnavailable);
    }

    private static TargetEligibilityResolution.Unavailable eligibilityUnavailable(
            TargetEligibilityResolution.UnavailableReason reason) {
        var present = new Present(new BigDecimal("235"), USD, null);
        TargetEligibilityResolution result = switch (reason) {
            case BASIS_TERMS_NOT_KNOWN_AS_OF -> resolveEligibility(
                    ORIGINAL, null, route(CallDirection.BULLISH), null, null,
                    resolvedHorizon(ORIGINAL), AS_OF);
            case HORIZON_BASIS_MISMATCH -> {
                var wrong = new OutcomeBasis.Original("wrong-call", BASIS_TIME);
                yield resolveEligibility(ORIGINAL,
                        terms(wrong, CallDirection.BULLISH, present),
                        route(CallDirection.BULLISH), null, null,
                        resolvedHorizon(ORIGINAL), AS_OF);
            }
            case ROUTE_MISSING -> resolveEligibility(
                    ORIGINAL, terms(ORIGINAL, CallDirection.BULLISH, present),
                    null, null, null, resolvedHorizon(ORIGINAL), AS_OF);
            case ROUTE_DIRECTION_MISMATCH -> resolveEligibility(
                    ORIGINAL, terms(ORIGINAL, CallDirection.BULLISH, present),
                    route(CallDirection.BEARISH), null, null,
                    resolvedHorizon(ORIGINAL), AS_OF);
            case TARGET_STATE_CONFLICT -> resolveEligibility(
                    ORIGINAL, terms(ORIGINAL, CallDirection.BULLISH, new Absent()),
                    route(CallDirection.BULLISH),
                    target(ORIGINAL, ASSET_ID, USD, new BigDecimal("235")), null,
                    resolvedHorizon(ORIGINAL), AS_OF);
            case TARGET_DATE_SEMANTICS_UNSUPPORTED -> resolveEligibility(
                    ORIGINAL, terms(ORIGINAL, CallDirection.BULLISH,
                            new Present(new BigDecimal("235"), USD,
                                    LocalDate.of(2027, 1, 1))),
                    route(CallDirection.BULLISH), null, null,
                    resolvedHorizon(ORIGINAL), AS_OF);
            case TARGET_EVIDENCE_NOT_KNOWN_AS_OF -> resolveEligibility(
                    ORIGINAL, terms(ORIGINAL, CallDirection.BULLISH, present),
                    route(CallDirection.BULLISH), null, null,
                    resolvedHorizon(ORIGINAL), AS_OF);
            case TARGET_EVIDENCE_BASIS_MISMATCH -> {
                var wrong = new OutcomeBasis.Original("wrong-call", BASIS_TIME);
                yield resolveEligibility(ORIGINAL,
                        terms(ORIGINAL, CallDirection.BULLISH, present),
                        route(CallDirection.BULLISH),
                        target(wrong, ASSET_ID, USD, new BigDecimal("235")), null,
                        resolvedHorizon(ORIGINAL), AS_OF);
            }
            case TARGET_ASSET_MISMATCH -> resolveEligibility(
                    ORIGINAL, terms(ORIGINAL, CallDirection.BULLISH, present),
                    route(CallDirection.BULLISH),
                    target(ORIGINAL, "wrong-asset", USD, new BigDecimal("235")),
                    null, resolvedHorizon(ORIGINAL), AS_OF);
            case TARGET_CURRENCY_MISMATCH -> resolveEligibility(
                    ORIGINAL, terms(ORIGINAL, CallDirection.BULLISH, present),
                    route(CallDirection.BULLISH),
                    target(ORIGINAL, ASSET_ID, Currency.getInstance("EUR"),
                            new BigDecimal("235")),
                    null, resolvedHorizon(ORIGINAL), AS_OF);
            case CATALOG_NOT_KNOWN_AS_OF -> completeEligibility(
                    ORIGINAL, CallDirection.BULLISH, present,
                    target(ORIGINAL, ASSET_ID, USD, new BigDecimal("235")),
                    null, resolvedHorizon(ORIGINAL), AS_OF);
            case CATALOG_EVIDENCE_MISMATCH -> completeEligibility(
                    ORIGINAL, CallDirection.BULLISH, present,
                    target(ORIGINAL, ASSET_ID, USD, new BigDecimal("235")),
                    new CatalogPointInTimeEvidence(
                            "wrong-calendar", "wrong-revision", "source-calendar",
                            "revision-9", BASIS_TIME, BASIS_TIME,
                            "provenance-calendar"),
                    resolvedHorizon(ORIGINAL), AS_OF);
            case FIRST_ELIGIBLE_SESSION_MISSING -> completeEligibility(
                    ORIGINAL, CallDirection.BULLISH, present,
                    target(ORIGINAL, ASSET_ID, USD, new BigDecimal("235")),
                    exactCatalog(), incompleteHorizon(ORIGINAL,
                            SessionCloseHorizonResolution.IncompleteReason
                                    .FIRST_ELIGIBLE_SESSION_MISSING), AS_OF);
            case HORIZON_ENDPOINT_SESSION_MISSING -> completeEligibility(
                    ORIGINAL, CallDirection.BULLISH, present,
                    target(ORIGINAL, ASSET_ID, USD, new BigDecimal("235")),
                    exactCatalog(), incompleteHorizon(ORIGINAL,
                            SessionCloseHorizonResolution.IncompleteReason
                                    .HORIZON_ENDPOINT_SESSION_MISSING), AS_OF);
        };
        var unavailable = (TargetEligibilityResolution.Unavailable) result;
        assertThat(unavailable.reason()).isEqualTo(reason);
        return unavailable;
    }

    private static Stream<FavorableExtremeResolution.Unavailable>
            favorableUnavailableScenarios() {
        return Arrays.stream(FavorableExtremeResolution.UnavailableReason.values())
                .map(TargetHitOrchestratorGoldenTest::favorableUnavailable);
    }

    private static FavorableExtremeResolution.Unavailable favorableUnavailable(
            FavorableExtremeResolution.UnavailableReason reason) {
        var ready = reason == FavorableExtremeResolution.UnavailableReason
                .TARGET_ADJUSTMENT_BASIS_UNSUPPORTED
                        ? ready(CallDirection.BULLISH, ORIGINAL,
                                new BigDecimal("235"), new BigDecimal("235"),
                                EndpointPriceAdjustmentBasis
                                        .DIVIDEND_OR_TOTAL_RETURN_ADJUSTED,
                                AS_OF)
                        : defaultReady(CallDirection.BULLISH);
        WindowPriceBinding binding = exactBinding(ready);
        List<FullWindowHighLowObservation> candidates = List.of(
                exactCandidate(ready, "exact"));
        switch (reason) {
            case TARGET_ADJUSTMENT_BASIS_UNSUPPORTED,
                    BINDING_NOT_KNOWN_AS_OF -> {
                binding = null;
                candidates = List.of();
            }
            case BINDING_ASSET_MISMATCH -> {
                binding = binding(ready, BindingFault.ASSET);
                candidates = List.of();
            }
            case BINDING_PRIMARY_VENUE_MISMATCH -> {
                binding = binding(ready, BindingFault.VENUE);
                candidates = List.of();
            }
            case BINDING_CURRENCY_MISMATCH -> {
                binding = binding(ready, BindingFault.CURRENCY);
                candidates = List.of();
            }
            case OBSERVATION_MISSING_AS_OF -> candidates = List.of();
            case OBSERVATION_AMBIGUOUS -> {
                var exact = exactCandidate(ready, "ambiguous");
                candidates = List.of(exact, exact);
            }
            default -> candidates = List.of(candidate(
                    ready, "fault-" + reason.name().toLowerCase(Locale.ROOT),
                    CandidateFault.forReason(reason), new BigDecimal("240.5000"),
                    new BigDecimal("170.250")));
        }
        var unavailable = (FavorableExtremeResolution.Unavailable)
                FavorableExtremeSelector.select(new FavorableExtremeRequest(
                        favorablePolicy(), ready, binding, candidates));
        assertThat(unavailable.reason()).isEqualTo(reason);
        return unavailable;
    }

    private static TargetHitOrchestrationRequest request(
            TargetEligibilityResolution eligibility,
            FavorableExtremeResolution favorable) {
        return new TargetHitOrchestrationRequest(policy(), eligibility, favorable);
    }

    private static Available available(
            TargetEligibilityResolution.ReadyForWindowEvidence ready,
            FavorableExtremeResolution.Resolved resolved) {
        return (Available) TargetHitOrchestrator.orchestrate(
                request(ready, resolved));
    }

    private static TargetEligibilityResolution.ReadyForWindowEvidence
            defaultReady(CallDirection direction) {
        return ready(direction, ORIGINAL, new BigDecimal("235"),
                new BigDecimal("235"), REQUIRED_ADJUSTMENT, AS_OF);
    }

    private static TargetEligibilityResolution.ReadyForWindowEvidence ready(
            CallDirection direction,
            OutcomeBasis basis,
            BigDecimal sourceTarget,
            BigDecimal normalizedTarget,
            EndpointPriceAdjustmentBasis adjustmentBasis,
            Instant evaluationAsOf) {
        return (TargetEligibilityResolution.ReadyForWindowEvidence)
                completeEligibility(
                        basis, direction,
                        new Present(sourceTarget, USD, null),
                        target(basis, ASSET_ID, USD, normalizedTarget,
                                adjustmentBasis),
                        exactCatalog(), resolvedHorizon(basis), evaluationAsOf);
    }

    private static TargetEligibilityResolution completeEligibility(
            OutcomeBasis basis,
            CallDirection direction,
            BasisForecastTermsEvidence.TargetDisposition disposition,
            TargetPriceEvidence target,
            CatalogPointInTimeEvidence catalog,
            SessionCloseHorizonResolution horizon,
            Instant evaluationAsOf) {
        return resolveEligibility(basis, terms(basis, direction, disposition),
                route(direction), target, catalog, horizon, evaluationAsOf);
    }

    private static TargetEligibilityResolution resolveEligibility(
            OutcomeBasis basis,
            BasisForecastTermsEvidence terms,
            CalculatorSideRouting.Result route,
            TargetPriceEvidence target,
            CatalogPointInTimeEvidence catalog,
            SessionCloseHorizonResolution horizon,
            Instant evaluationAsOf) {
        return TargetEligibilityResolver.resolve(new TargetEligibilityRequest(
                eligibilityPolicy(), horizon, terms, route, target, catalog,
                evaluationAsOf));
    }

    private static BasisForecastTermsEvidence terms(
            OutcomeBasis basis,
            CallDirection direction,
            BasisForecastTermsEvidence.TargetDisposition disposition) {
        return new BasisForecastTermsEvidence(
                "terms-" + basis.callId(), basis, ASSET_ID, direction, disposition,
                "provider-analyst", "provider-event-terms", basis.eventTime(),
                basis.eventTime(), "provenance-terms");
    }

    private static CalculatorSideRouting.Result route(CallDirection direction) {
        return CalculatorSideRouting.route(CallDirectionPolarityResolver.resolve(
                new CallDirectionPolarityRequest(
                        CallDirectionPolarityPolicyVersion
                                .COLLAPSE_STRONG_DIRECTIONS_NEUTRAL_NON_DIRECTIONAL_V1,
                        direction)));
    }

    private static TargetPriceEvidence target(
            OutcomeBasis basis,
            String assetId,
            Currency currency,
            BigDecimal value) {
        return target(basis, assetId, currency, value, REQUIRED_ADJUSTMENT);
    }

    private static TargetPriceEvidence target(
            OutcomeBasis basis,
            String assetId,
            Currency currency,
            BigDecimal value,
            EndpointPriceAdjustmentBasis adjustmentBasis) {
        return new TargetPriceEvidence(
                "target-" + basis.callId(), basis, assetId, VENUE_ID, currency,
                adjustmentBasis, value, basis.eventTime(), basis.eventTime(),
                "provenance-target");
    }

    private static CatalogPointInTimeEvidence exactCatalog() {
        return new CatalogPointInTimeEvidence(
                CALENDAR_ID, CATALOG_REVISION, "source-calendar", "revision-9",
                BASIS_TIME, BASIS_TIME, "provenance-calendar");
    }

    private static SessionCloseHorizonResolution resolvedHorizon(
            OutcomeBasis basis) {
        var context = horizonContext(basis);
        var session = new TradingSession("session-1", OPEN, CLOSE);
        return new SessionCloseHorizonResolution.Resolved(
                new SessionCloseHorizonResolution.ResolvedSessionWindow(
                        context, List.of(session), session));
    }

    private static SessionCloseHorizonResolution incompleteHorizon(
            OutcomeBasis basis,
            SessionCloseHorizonResolution.IncompleteReason reason) {
        return new SessionCloseHorizonResolution.Incomplete(
                horizonContext(basis), reason);
    }

    private static SessionCloseHorizonResolution.ResolutionContext horizonContext(
            OutcomeBasis basis) {
        return new SessionCloseHorizonResolution.ResolutionContext(
                SessionCloseHorizonPolicyVersion
                        .STRICTLY_AFTER_BASIS_EVENT_SESSION_CLOSE_V1,
                HORIZON_HASH, basis, OutcomeHorizon.D1, 1,
                CALENDAR_ID, CATALOG_REVISION);
    }

    private static FavorableExtremeResolution.Resolved resolvedExtreme(
            TargetEligibilityResolution.ReadyForWindowEvidence ready,
            BigDecimal high,
            BigDecimal low) {
        return (FavorableExtremeResolution.Resolved) FavorableExtremeSelector.select(
                new FavorableExtremeRequest(
                        favorablePolicy(), ready, exactBinding(ready),
                        List.of(candidate(ready, "resolved", null, high, low))));
    }

    private static WindowPriceBinding exactBinding(
            TargetEligibilityResolution.ReadyForWindowEvidence ready) {
        return binding(ready, BindingFault.NONE);
    }

    private static WindowPriceBinding binding(
            TargetEligibilityResolution.ReadyForWindowEvidence ready,
            BindingFault fault) {
        var target = ready.evidence().targetEvidence();
        return new WindowPriceBinding(
                "binding-window", "binding-revision-1",
                fault == BindingFault.ASSET ? "wrong-asset" : target.assetId(),
                fault == BindingFault.VENUE ? "wrong-venue"
                        : target.primaryVenueId(),
                fault == BindingFault.CURRENCY ? Currency.getInstance("EUR")
                        : target.currency(),
                SOURCE_ID, SOURCE_REVISION, BASIS_TIME, BASIS_TIME,
                "provenance-binding");
    }

    private static FullWindowHighLowObservation exactCandidate(
            TargetEligibilityResolution.ReadyForWindowEvidence ready,
            String observationId) {
        return candidate(ready, observationId, null,
                new BigDecimal("240.5000"), new BigDecimal("170.250"));
    }

    private static FullWindowHighLowObservation candidate(
            TargetEligibilityResolution.ReadyForWindowEvidence ready,
            String observationId,
            CandidateFault fault,
            BigDecimal high,
            BigDecimal low) {
        var window = ((SessionCloseHorizonResolution.Resolved)
                ready.context().horizonResolution()).window();
        var context = window.context();
        var binding = exactBinding(ready);
        Instant endpoint = window.endpointSession().closesAt();
        Set<CandidateFault> faults = fault == null ? Set.of() : Set.of(fault);
        OutcomeHorizon wrongHorizon = context.horizon() == OutcomeHorizon.D1
                ? OutcomeHorizon.W1 : OutcomeHorizon.D1;
        List<String> sessionIds = faults.contains(CandidateFault.SESSION)
                ? List.of("wrong-session")
                : window.sessions().stream().map(TradingSession::sessionId).toList();
        return new FullWindowHighLowObservation(
                observationId, "provider-event-" + observationId,
                faults.contains(CandidateFault.BASIS)
                        ? new OutcomeBasis.Original(
                                "wrong-call", context.basis().eventTime())
                        : context.basis(),
                faults.contains(CandidateFault.HORIZON)
                        ? wrongHorizon : context.horizon(),
                faults.contains(CandidateFault.ASSET)
                        ? "wrong-asset" : binding.assetId(),
                faults.contains(CandidateFault.VENUE)
                        ? "wrong-venue" : binding.primaryVenueId(),
                faults.contains(CandidateFault.CURRENCY)
                        ? Currency.getInstance("EUR") : binding.currency(),
                faults.contains(CandidateFault.SOURCE)
                        ? "wrong-source" : binding.priceSourceId(),
                faults.contains(CandidateFault.SOURCE)
                        ? "wrong-source-revision" : binding.priceSourceRevision(),
                "provenance-window-" + observationId,
                faults.contains(CandidateFault.CATALOG)
                        ? "wrong-calendar" : CALENDAR_ID,
                faults.contains(CandidateFault.CATALOG)
                        ? "wrong-catalog" : CATALOG_REVISION,
                sessionIds,
                faults.contains(CandidateFault.LOWER)
                        ? context.basis().eventTime().plusNanos(1_000)
                        : context.basis().eventTime(),
                faults.contains(CandidateFault.BOUNDARY)
                        ? BoundaryType.INCLUSIVE : BoundaryType.EXCLUSIVE,
                faults.contains(CandidateFault.UPPER)
                        ? endpoint.minusNanos(1_000) : endpoint,
                BoundaryType.INCLUSIVE,
                faults.contains(CandidateFault.FIELD)
                        ? WindowPriceField.INDICATIVE_OR_OTHER
                        : WindowPriceField
                                .PRIMARY_VENUE_REGULAR_SESSION_CAUSAL_WINDOW_HIGH_LOW_PAIR,
                faults.contains(CandidateFault.COMPLETENESS)
                        ? WindowCoverageCompleteness.PARTIAL_OR_UNKNOWN
                        : WindowCoverageCompleteness
                                .EXACT_CAUSAL_WINDOW_SESSION_UNION,
                faults.contains(CandidateFault.ADJUSTMENT)
                        ? EndpointPriceAdjustmentBasis.UNADJUSTED_OR_OTHER
                        : REQUIRED_ADJUSTMENT,
                faults.contains(CandidateFault.CONTINUITY)
                        ? CorporateActionContinuity.MERGER
                        : CorporateActionContinuity.SPLIT_REVERSE_SPLIT_CONTINUOUS,
                endpoint, endpoint.plusSeconds(30), high, low);
    }

    private static TargetHitOrchestrationPolicyVersion policy() {
        return TargetHitOrchestrationPolicyVersion
                .POINT_IN_TIME_TARGET_HIT_ORCHESTRATION_V1;
    }

    private static TargetEligibilityPolicyVersion eligibilityPolicy() {
        return TargetEligibilityPolicyVersion
                .POINT_IN_TIME_TARGET_HIT_INPUT_READINESS_V1;
    }

    private static FavorableExtremePolicyVersion favorablePolicy() {
        return FavorableExtremePolicyVersion
                .POINT_IN_TIME_ATTESTED_CAUSAL_WINDOW_HIGH_LOW_V1;
    }

    private static void assertRecordComponents(
            Class<?> type, String... expected) {
        assertThat(type.isRecord()).as(type.getSimpleName()).isTrue();
        assertThat(Arrays.stream(type.getRecordComponents())
                .map(component -> component.getName() + ":"
                        + component.getType().getSimpleName())
                .toList()).containsExactly(expected);
    }

    private static String readText(Path path) {
        try {
            return Files.readString(path);
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
    }

    private static int count(String value, String needle) {
        return (value.length() - value.replace(needle, "").length())
                / needle.length();
    }

    private enum BindingFault {
        NONE,
        ASSET,
        VENUE,
        CURRENCY
    }

    private enum CandidateFault {
        BASIS(FavorableExtremeResolution.UnavailableReason.BASIS_MISMATCH),
        HORIZON(FavorableExtremeResolution.UnavailableReason.HORIZON_MISMATCH),
        ASSET(FavorableExtremeResolution.UnavailableReason.ASSET_MISMATCH),
        VENUE(FavorableExtremeResolution.UnavailableReason.PRIMARY_VENUE_MISMATCH),
        CURRENCY(FavorableExtremeResolution.UnavailableReason.CURRENCY_MISMATCH),
        SOURCE(FavorableExtremeResolution.UnavailableReason.SOURCE_MISMATCH),
        CATALOG(FavorableExtremeResolution.UnavailableReason.CATALOG_MISMATCH),
        SESSION(FavorableExtremeResolution.UnavailableReason.SESSION_WINDOW_MISMATCH),
        LOWER(FavorableExtremeResolution.UnavailableReason.LOWER_BOUND_MISMATCH),
        UPPER(FavorableExtremeResolution.UnavailableReason.UPPER_BOUND_MISMATCH),
        BOUNDARY(FavorableExtremeResolution.UnavailableReason
                .BOUNDARY_CONVENTION_MISMATCH),
        FIELD(FavorableExtremeResolution.UnavailableReason.PRICE_FIELD_MISMATCH),
        COMPLETENESS(FavorableExtremeResolution.UnavailableReason
                .WINDOW_COMPLETENESS_UNAVAILABLE),
        ADJUSTMENT(FavorableExtremeResolution.UnavailableReason
                .ADJUSTMENT_BASIS_MISMATCH),
        CONTINUITY(FavorableExtremeResolution.UnavailableReason
                .CORPORATE_ACTION_CONTINUITY_UNAVAILABLE);

        private final FavorableExtremeResolution.UnavailableReason reason;

        CandidateFault(FavorableExtremeResolution.UnavailableReason reason) {
            this.reason = reason;
        }

        private static CandidateFault forReason(
                FavorableExtremeResolution.UnavailableReason reason) {
            return Arrays.stream(values())
                    .filter(fault -> fault.reason == reason)
                    .findFirst()
                    .orElseThrow();
        }
    }
}
