package com.wallstreetreceipts.api.domain.outcome.targethitreadiness;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
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
import org.junit.jupiter.params.provider.MethodSource;

import com.wallstreetreceipts.api.domain.call.CallDirection;
import com.wallstreetreceipts.api.domain.outcome.OutcomeHorizon;
import com.wallstreetreceipts.api.domain.outcome.direction
        .CallDirectionPolarityPolicyVersion;
import com.wallstreetreceipts.api.domain.outcome.direction
        .CallDirectionPolarityRequest;
import com.wallstreetreceipts.api.domain.outcome.direction
        .CallDirectionPolarityResolver;
import com.wallstreetreceipts.api.domain.outcome.favorableextreme
        .FavorableExtremePolicyVersion;
import com.wallstreetreceipts.api.domain.outcome.favorableextreme
        .FavorableExtremeRequest;
import com.wallstreetreceipts.api.domain.outcome.favorableextreme
        .FavorableExtremeResolution;
import com.wallstreetreceipts.api.domain.outcome.favorableextreme
        .FavorableExtremeSelector;
import com.wallstreetreceipts.api.domain.outcome.favorableextreme
        .FullWindowHighLowObservation;
import com.wallstreetreceipts.api.domain.outcome.favorableextreme
        .FullWindowHighLowObservation.BoundaryType;
import com.wallstreetreceipts.api.domain.outcome.favorableextreme
        .FullWindowHighLowObservation.WindowCoverageCompleteness;
import com.wallstreetreceipts.api.domain.outcome.favorableextreme
        .FullWindowHighLowObservation.WindowPriceField;
import com.wallstreetreceipts.api.domain.outcome.favorableextreme
        .WindowPriceBinding;
import com.wallstreetreceipts.api.domain.outcome.horizon.OutcomeBasis;
import com.wallstreetreceipts.api.domain.outcome.horizon
        .SessionCloseHorizonPolicyVersion;
import com.wallstreetreceipts.api.domain.outcome.horizon
        .SessionCloseHorizonResolution;
import com.wallstreetreceipts.api.domain.outcome.horizon.TradingSession;
import com.wallstreetreceipts.api.domain.outcome.observation
        .CatalogPointInTimeEvidence;
import com.wallstreetreceipts.api.domain.outcome.observation
        .CorporateActionContinuity;
import com.wallstreetreceipts.api.domain.outcome.observation
        .EndpointPriceAdjustmentBasis;
import com.wallstreetreceipts.api.domain.outcome.routing.CalculatorSideRouting;
import com.wallstreetreceipts.api.domain.outcome.targeteligibility
        .BasisForecastTermsEvidence;
import com.wallstreetreceipts.api.domain.outcome.targeteligibility
        .BasisForecastTermsEvidence.TargetDisposition.Absent;
import com.wallstreetreceipts.api.domain.outcome.targeteligibility
        .BasisForecastTermsEvidence.TargetDisposition.Present;
import com.wallstreetreceipts.api.domain.outcome.targeteligibility
        .TargetEligibilityPolicyVersion;
import com.wallstreetreceipts.api.domain.outcome.targeteligibility
        .TargetEligibilityRequest;
import com.wallstreetreceipts.api.domain.outcome.targeteligibility
        .TargetEligibilityResolution;
import com.wallstreetreceipts.api.domain.outcome.targeteligibility
        .TargetEligibilityResolver;
import com.wallstreetreceipts.api.domain.outcome.targeterror.TargetPriceEvidence;
import com.wallstreetreceipts.api.domain.outcome.targethitorchestration
        .TargetHitOrchestrationPolicyVersion;
import com.wallstreetreceipts.api.domain.outcome.targethitorchestration
        .TargetHitOrchestrationRequest;
import com.wallstreetreceipts.api.domain.outcome.targethitorchestration
        .TargetHitOrchestrationResolution;
import com.wallstreetreceipts.api.domain.outcome.targethitorchestration
        .TargetHitOrchestrator;

class TargetHitReadinessResolverGoldenTest {

    private static final String POLICY_HASH =
            "8f81dee5227370d82dd91cd2fb8448797c7028eaa485dc64cf4bdc3cbf2f31a3";
    private static final String SOURCE_HASH =
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
    private static final Instant OPEN =
            Instant.parse("2026-08-20T13:30:00Z");
    private static final Instant BASIS_TIME =
            Instant.parse("2026-08-20T15:00:00Z");
    private static final Instant CLOSE =
            Instant.parse("2026-08-20T20:00:00Z");
    private static final Instant AS_OF =
            Instant.parse("2026-08-20T20:01:00Z");
    private static final OutcomeBasis ORIGINAL =
            new OutcomeBasis.Original("call-1", BASIS_TIME);
    private static final EndpointPriceAdjustmentBasis REQUIRED_ADJUSTMENT =
            EndpointPriceAdjustmentBasis
                    .SPLIT_AND_REVERSE_SPLIT_ADJUSTED_TO_ENDPOINT_SHARE_BASIS_DIVIDEND_UNADJUSTED;

    @Test
    void canonicalDefinitionHasExactBytesHashAdrAndDefensiveReads()
            throws Exception {
        byte[] bytes = policy().canonicalDefinitionUtf8();

        assertThat(bytes).hasSize(2042);
        assertThat(HexFormat.of().formatHex(
                MessageDigest.getInstance("SHA-256").digest(bytes)))
                .isEqualTo(POLICY_HASH)
                .isEqualTo(policy().definitionHash());
        assertThat(policy().canonicalDefinition())
                .contains("RESULT_CONSTRUCTORS_AND_RESOLVER_SHARE_EXACT_CLASSIFICATION_VALIDATION")
                .contains("ONLY_RESOLVE_ATTESTS_REQUEST_TO_RESULT_INVOCATION")
                .contains("HORIZON_NOT_REACHED_AS_OF")
                .contains("NO_NESTED_REASON_REINTERPRETATION_OR_DUPLICATION")
                .contains("TARGET_HIT_AVAILABLE_OR_PERMANENTLY_NOT_APPLICABLE")
                .endsWith("\"publication\":\"ABSENT\"}");
        String adr = Files.readString(Path.of(
                "../../decisions/ADR-024-supplied-leaf-target-hit-readiness.md"));
        assertThat(adr)
                .contains("2042-byte")
                .contains(POLICY_HASH);
        assertThat(adr.replaceAll("\\s+", " "))
                .contains("exactly 47 test invocations");
        int canonicalStart = adr.indexOf('\n', adr.indexOf("```json")) + 1;
        int canonicalEnd = adr.indexOf("\n```", canonicalStart);
        assertThat(adr.substring(canonicalStart, canonicalEnd))
                .isEqualTo(policy().canonicalDefinition());
        List<ClassificationVector> vectors = classificationVectors();
        assertThat(vectors).hasSize(41);
        assertThat(vectors.stream().filter(vector -> vector.expectedType()
                == TargetHitReadinessResolution.Settled.class)).hasSize(4);
        assertThat(vectors.stream().filter(vector -> vector.expectedType()
                == TargetHitReadinessResolution.AwaitingEndpoint.class)).hasSize(1);
        assertThat(vectors.stream().filter(vector -> vector.expectedType()
                == TargetHitReadinessResolution.EvidenceUnavailable.class))
                .hasSize(36);

        bytes[0] = (byte) '!';
        assertThat(policy().canonicalDefinitionUtf8()[0]).isEqualTo((byte) '{');
    }

    @Test
    void exactFileRecordSealedAndDisconnectedSurfacesAreStable()
            throws Exception {
        Path packagePath = Path.of(
                "src/main/java/com/wallstreetreceipts/api/domain/outcome/targethitreadiness");
        try (var files = Files.list(packagePath)) {
            assertThat(files.filter(path -> path.toString().endsWith(".java"))
                    .map(path -> path.getFileName().toString()).sorted().toList())
                    .containsExactly(
                            "TargetHitReadinessPolicyVersion.java",
                            "TargetHitReadinessRequest.java",
                            "TargetHitReadinessResolution.java",
                            "TargetHitReadinessResolver.java");
        }
        Path testPath = Path.of(
                "src/test/java/com/wallstreetreceipts/api/domain/outcome/targethitreadiness");
        try (var files = Files.list(testPath)) {
            assertThat(files.filter(path -> path.toString().endsWith(".java"))
                    .map(path -> path.getFileName().toString()).toList())
                    .containsExactly("TargetHitReadinessResolverGoldenTest.java");
        }
        assertRecordComponents(TargetHitReadinessRequest.class,
                "policyVersion:TargetHitReadinessPolicyVersion",
                "sourceResult:TargetHitOrchestrationResolution");
        assertRecordComponents(TargetHitReadinessResolution.ResolutionContext.class,
                "policyVersion:TargetHitReadinessPolicyVersion",
                "policyDefinitionHash:String");
        assertRecordComponents(TargetHitReadinessResolution.Settled.class,
                "context:ResolutionContext",
                "sourceResult:TargetHitOrchestrationResolution");
        assertRecordComponents(TargetHitReadinessResolution.AwaitingEndpoint.class,
                "context:ResolutionContext",
                "sourceResult:TargetHitOrchestrationResolution");
        assertRecordComponents(
                TargetHitReadinessResolution.EvidenceUnavailable.class,
                "context:ResolutionContext",
                "sourceResult:TargetHitOrchestrationResolution");
        assertThat(TargetHitReadinessResolution.class.isSealed()).isTrue();
        assertThat(Arrays.stream(TargetHitReadinessResolution.class
                .getPermittedSubclasses()).map(Class::getSimpleName).toList())
                .containsExactlyInAnyOrder(
                        "Settled", "AwaitingEndpoint", "EvidenceUnavailable");
        assertThat(TargetHitReadinessPolicyVersion.values())
                .containsExactly(policy());

        String request = Files.readString(packagePath.resolve(
                "TargetHitReadinessRequest.java"));
        String resolution = Files.readString(packagePath.resolve(
                "TargetHitReadinessResolution.java"));
        String resolver = Files.readString(packagePath.resolve(
                "TargetHitReadinessResolver.java"));
        assertThat(request + resolution + resolver)
                .doesNotContain(".reason()")
                .doesNotContain("PendingReason")
                .doesNotContain("NotApplicableReason")
                .doesNotContain("UnavailableReason")
                .doesNotContain("OutcomeEvaluationStatus")
                .doesNotContain("CallOutcome")
                .doesNotContain("TargetHitCalculator")
                .doesNotContain("TargetEligibilityResolver")
                .doesNotContain("FavorableExtremeSelector")
                .doesNotContain("Clock")
                .doesNotContain("@Service")
                .doesNotContain("@Repository")
                .doesNotContain("@Controller");
        assertThat(resolver)
                .contains("case TargetHitOrchestrationResolution.Pending")
                .contains("case TargetHitOrchestrationResolution.NotApplicable");
    }

    @Test
    void nullRootsAndRequiredSourceShapeFailClosed() {
        var source = availableSource();
        var context = context();

        assertThatThrownBy(() -> TargetHitReadinessResolver.resolve(null))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new TargetHitReadinessRequest(null, source))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new TargetHitReadinessRequest(policy(), null))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new TargetHitReadinessResolution
                .ResolutionContext(null, POLICY_HASH))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new TargetHitReadinessResolution
                .ResolutionContext(policy(), null))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new TargetHitReadinessResolution
                .ResolutionContext(policy(), "0".repeat(64)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new TargetHitReadinessResolution
                .Settled(null, source))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new TargetHitReadinessResolution
                .Settled(context, null))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> TargetHitReadinessResolver
                .requireClassification(source, null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void directResultConstructorsShareExactFailClosedClassification() {
        var settled = availableSource();
        var awaiting = pendingSource();
        var unavailable = eligibilityUnavailableSource(
                TargetEligibilityResolution.UnavailableReason.ROUTE_MISSING);
        var context = context();

        assertThat(new TargetHitReadinessResolution.Settled(
                context, settled).sourceResult()).isSameAs(settled);
        assertThat(new TargetHitReadinessResolution.AwaitingEndpoint(
                context, awaiting).sourceResult()).isSameAs(awaiting);
        assertThat(new TargetHitReadinessResolution.EvidenceUnavailable(
                context, unavailable).sourceResult()).isSameAs(unavailable);
        assertWrongVariant(() -> new TargetHitReadinessResolution
                .AwaitingEndpoint(context, settled));
        assertWrongVariant(() -> new TargetHitReadinessResolution
                .EvidenceUnavailable(context, settled));
        assertWrongVariant(() -> new TargetHitReadinessResolution
                .Settled(context, awaiting));
        assertWrongVariant(() -> new TargetHitReadinessResolution
                .EvidenceUnavailable(context, awaiting));
        assertWrongVariant(() -> new TargetHitReadinessResolution
                .Settled(context, unavailable));
        assertWrongVariant(() -> new TargetHitReadinessResolution
                .AwaitingEndpoint(context, unavailable));
    }

    @Test
    void equalButDistinctWholeSourceRecordsReplayEqually() {
        var firstSource = pendingSource();
        var secondSource = pendingSource();

        assertThat(secondSource).isEqualTo(firstSource).isNotSameAs(firstSource);
        var first = TargetHitReadinessResolver.resolve(request(firstSource));
        var second = TargetHitReadinessResolver.resolve(request(secondSource));

        assertThat(second).isEqualTo(first).isNotSameAs(first);
        assertThat(sourceOf(first)).isSameAs(firstSource);
        assertThat(sourceOf(second)).isSameAs(secondSource);
    }

    @Test
    void classificationIsIndependentOfLocaleTimezoneAndPriorCalls() {
        var source = favorableUnavailableSource(
                FavorableExtremeResolution.UnavailableReason
                        .OBSERVATION_MISSING_AS_OF);
        Locale originalLocale = Locale.getDefault();
        TimeZone originalTimeZone = TimeZone.getDefault();

        try {
            Locale.setDefault(Locale.JAPAN);
            TimeZone.setDefault(TimeZone.getTimeZone("Asia/Tokyo"));
            var first = TargetHitReadinessResolver.resolve(request(source));
            TargetHitReadinessResolver.resolve(request(availableSource()));

            Locale.setDefault(Locale.GERMANY);
            TimeZone.setDefault(TimeZone.getTimeZone("America/New_York"));
            var second = TargetHitReadinessResolver.resolve(request(source));

            assertThat(first).isEqualTo(second);
            assertThat(sourceOf(first)).isSameAs(source);
            assertThat(sourceOf(second)).isSameAs(source);
        } finally {
            Locale.setDefault(originalLocale);
            TimeZone.setDefault(originalTimeZone);
        }
    }

    @ParameterizedTest(name = "source shape {0}")
    @MethodSource("classificationSourceShapes")
    void everyConstructibleSourceShapeIsClassified(
            String label,
            TargetHitOrchestrationResolution source,
            Class<? extends TargetHitReadinessResolution> expectedType) {
        var result = TargetHitReadinessResolver.resolve(request(source));

        assertThat(result).as(label).isExactlyInstanceOf(expectedType);
        assertThat(sourceOf(result)).isSameAs(source);
        assertThat(contextOf(result).policyVersion()).isSameAs(policy());
        assertThat(contextOf(result).policyDefinitionHash())
                .isEqualTo(POLICY_HASH);
    }

    static Stream<Arguments> classificationSourceShapes() {
        return classificationVectors().stream().map(vector -> Arguments.of(
                vector.label(), vector.source(), vector.expectedType()));
    }

    private static List<ClassificationVector> classificationVectors() {
        List<ClassificationVector> vectors = new ArrayList<>();
        vectors.add(new ClassificationVector(
                "available", availableSource(),
                TargetHitReadinessResolution.Settled.class));
        for (TargetEligibilityResolution.NotApplicableReason reason
                : TargetEligibilityResolution.NotApplicableReason.values()) {
            vectors.add(new ClassificationVector(
                    "not-applicable-" + reason.name(),
                    notApplicableSource(reason),
                    TargetHitReadinessResolution.Settled.class));
        }
        vectors.add(new ClassificationVector(
                "pending-HORIZON_NOT_REACHED_AS_OF", pendingSource(),
                TargetHitReadinessResolution.AwaitingEndpoint.class));
        for (TargetEligibilityResolution.UnavailableReason reason
                : TargetEligibilityResolution.UnavailableReason.values()) {
            vectors.add(new ClassificationVector(
                    "eligibility-unavailable-" + reason.name(),
                    eligibilityUnavailableSource(reason),
                    TargetHitReadinessResolution.EvidenceUnavailable.class));
        }
        for (FavorableExtremeResolution.UnavailableReason reason
                : FavorableExtremeResolution.UnavailableReason.values()) {
            vectors.add(new ClassificationVector(
                    "favorable-extreme-unavailable-" + reason.name(),
                    favorableUnavailableSource(reason),
                    TargetHitReadinessResolution.EvidenceUnavailable.class));
        }
        return List.copyOf(vectors);
    }

    private static TargetHitReadinessPolicyVersion policy() {
        return TargetHitReadinessPolicyVersion
                .SUPPLIED_LEAF_TARGET_HIT_READINESS_V1;
    }

    private static TargetHitReadinessResolution.ResolutionContext context() {
        return new TargetHitReadinessResolution.ResolutionContext(
                policy(), POLICY_HASH);
    }

    private static TargetHitReadinessRequest request(
            TargetHitOrchestrationResolution source) {
        return new TargetHitReadinessRequest(policy(), source);
    }

    private static TargetHitOrchestrationResolution.Available availableSource() {
        var ready = defaultReady(CallDirection.BULLISH);
        var resolved = resolvedExtreme(
                ready, new BigDecimal("240.5000"), new BigDecimal("170.250"));
        return (TargetHitOrchestrationResolution.Available) orchestrate(
                ready, resolved);
    }

    private static TargetHitOrchestrationResolution.NotApplicable
            notApplicableSource(
                    TargetEligibilityResolution.NotApplicableReason reason) {
        TargetEligibilityResolution.NotApplicable eligibility = switch (reason) {
            case TARGET_ABSENT -> notApplicable(
                    CallDirection.BULLISH, new Absent());
            case NON_DIRECTIONAL -> notApplicable(
                    CallDirection.NEUTRAL,
                    new Present(new BigDecimal("235"), USD, null));
            case TARGET_ABSENT_AND_NON_DIRECTIONAL -> notApplicable(
                    CallDirection.NEUTRAL, new Absent());
        };
        assertThat(eligibility.reason()).isSameAs(reason);
        return (TargetHitOrchestrationResolution.NotApplicable) orchestrate(
                eligibility, null);
    }

    private static TargetHitOrchestrationResolution.Pending pendingSource() {
        var eligibility = (TargetEligibilityResolution.Pending)
                completeEligibility(
                        ORIGINAL, CallDirection.BULLISH,
                        new Present(new BigDecimal("235"), USD, null),
                        target(ORIGINAL, ASSET_ID, USD, new BigDecimal("235")),
                        exactCatalog(), resolvedHorizon(ORIGINAL),
                        CLOSE.minusNanos(1_000));
        assertThat(eligibility.reason()).isEqualTo(
                TargetEligibilityResolution.PendingReason
                        .HORIZON_NOT_REACHED_AS_OF);
        return (TargetHitOrchestrationResolution.Pending) orchestrate(
                eligibility, null);
    }

    private static TargetHitOrchestrationResolution.EligibilityUnavailable
            eligibilityUnavailableSource(
                    TargetEligibilityResolution.UnavailableReason reason) {
        TargetEligibilityResolution.Unavailable eligibility =
                eligibilityUnavailable(reason);
        return (TargetHitOrchestrationResolution.EligibilityUnavailable)
                orchestrate(eligibility, null);
    }

    private static TargetHitOrchestrationResolution.FavorableExtremeUnavailable
            favorableUnavailableSource(
                    FavorableExtremeResolution.UnavailableReason reason) {
        FavorableExtremeResolution.Unavailable favorable =
                favorableUnavailable(reason);
        return (TargetHitOrchestrationResolution.FavorableExtremeUnavailable)
                orchestrate(favorable.context().readyEligibility(), favorable);
    }

    private static TargetHitOrchestrationResolution orchestrate(
            TargetEligibilityResolution eligibility,
            FavorableExtremeResolution favorable) {
        return TargetHitOrchestrator.orchestrate(
                new TargetHitOrchestrationRequest(sourcePolicy(), eligibility,
                        favorable));
    }

    private static TargetEligibilityResolution.NotApplicable notApplicable(
            CallDirection direction,
            BasisForecastTermsEvidence.TargetDisposition disposition) {
        return (TargetEligibilityResolution.NotApplicable) completeEligibility(
                ORIGINAL, direction, disposition, null, null,
                resolvedHorizon(ORIGINAL), AS_OF);
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
                    ORIGINAL,
                    terms(ORIGINAL, CallDirection.BULLISH, new Absent()),
                    route(CallDirection.BULLISH),
                    target(ORIGINAL, ASSET_ID, USD, new BigDecimal("235")),
                    null, resolvedHorizon(ORIGINAL), AS_OF);
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
                        target(wrong, ASSET_ID, USD, new BigDecimal("235")),
                        null, resolvedHorizon(ORIGINAL), AS_OF);
            }
            case TARGET_ASSET_MISMATCH -> resolveEligibility(
                    ORIGINAL, terms(ORIGINAL, CallDirection.BULLISH, present),
                    route(CallDirection.BULLISH),
                    target(ORIGINAL, "wrong-asset", USD,
                            new BigDecimal("235")),
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
                            "wrong-calendar", "wrong-revision",
                            "source-calendar", "revision-9", BASIS_TIME,
                            BASIS_TIME, "provenance-calendar"),
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
        assertThat(unavailable.reason()).isSameAs(reason);
        return unavailable;
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
        assertThat(unavailable.reason()).isSameAs(reason);
        return unavailable;
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
                "terms-" + basis.callId(), basis, ASSET_ID, direction,
                disposition, "provider-analyst", "provider-event-terms",
                basis.eventTime(), basis.eventTime(), "provenance-terms");
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
        return (FavorableExtremeResolution.Resolved)
                FavorableExtremeSelector.select(new FavorableExtremeRequest(
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
                : window.sessions().stream()
                        .map(TradingSession::sessionId).toList();
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
                        ? "wrong-source-revision"
                        : binding.priceSourceRevision(),
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
                        : CorporateActionContinuity
                                .SPLIT_REVERSE_SPLIT_CONTINUOUS,
                endpoint, endpoint.plusSeconds(30), high, low);
    }

    private static TargetHitOrchestrationPolicyVersion sourcePolicy() {
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

    private static TargetHitOrchestrationResolution sourceOf(
            TargetHitReadinessResolution resolution) {
        return switch (resolution) {
            case TargetHitReadinessResolution.Settled settled ->
                    settled.sourceResult();
            case TargetHitReadinessResolution.AwaitingEndpoint awaiting ->
                    awaiting.sourceResult();
            case TargetHitReadinessResolution.EvidenceUnavailable unavailable ->
                    unavailable.sourceResult();
        };
    }

    private static TargetHitReadinessResolution.ResolutionContext contextOf(
            TargetHitReadinessResolution resolution) {
        return switch (resolution) {
            case TargetHitReadinessResolution.Settled settled ->
                    settled.context();
            case TargetHitReadinessResolution.AwaitingEndpoint awaiting ->
                    awaiting.context();
            case TargetHitReadinessResolution.EvidenceUnavailable unavailable ->
                    unavailable.context();
        };
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

    private record ClassificationVector(
            String label,
            TargetHitOrchestrationResolution source,
            Class<? extends TargetHitReadinessResolution> expectedType) {
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
        VENUE(FavorableExtremeResolution.UnavailableReason
                .PRIMARY_VENUE_MISMATCH),
        CURRENCY(FavorableExtremeResolution.UnavailableReason.CURRENCY_MISMATCH),
        SOURCE(FavorableExtremeResolution.UnavailableReason.SOURCE_MISMATCH),
        CATALOG(FavorableExtremeResolution.UnavailableReason.CATALOG_MISMATCH),
        SESSION(FavorableExtremeResolution.UnavailableReason
                .SESSION_WINDOW_MISMATCH),
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
