package com.wallstreetreceipts.api.domain.outcome.targeterrorreadiness;

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
import com.wallstreetreceipts.api.domain.outcome.targeterror.TargetErrorPolicyVersion;
import com.wallstreetreceipts.api.domain.outcome.targeterror.TargetErrorResult;

class TargetErrorReadinessResolverGoldenTest {

    private static final String POLICY_HASH =
            "0b8bfb22dccd4a494f568c44d06163f73af36462cf929bc83cf238019811c44a";
    private static final String SOURCE_HASH =
            "31ca30555549f670e3c22d98ead16f7a02bfad198f36532effaf4a4b6931d074";
    private static final String ENDPOINT_HASH =
            "37e37aba9302d77366cef4129f77a82b7ccb2f1937bfffc0315ea8d0bc6b1f76";
    private static final String HORIZON_HASH =
            "550087efe7ddf2ba31974c89c2740ab79df986eefef48919c32c56a3232f8dc1";
    private static final String CALENDAR_ID = "calendar-primary-us-equity";
    private static final String CATALOG_REVISION = "calendar-revision-7";
    private static final String ASSET_ID = "asset-nvda";
    private static final String VENUE_ID = "venue-xnas";
    private static final String PRICE_SOURCE_ID = "source-official-close";
    private static final String PRICE_SOURCE_REVISION = "source-revision-3";
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
            new OutcomeBasis.Original("call-endpoint", BASIS_TIME);
    private static final EndpointPriceAdjustmentBasis REQUIRED_ADJUSTMENT =
            EndpointPriceAdjustmentBasis
                    .SPLIT_AND_REVERSE_SPLIT_ADJUSTED_TO_ENDPOINT_SHARE_BASIS_DIVIDEND_UNADJUSTED;

    @Test
    void canonicalDefinitionHasExactBytesHashAdrAndDefensiveReads()
            throws Exception {
        byte[] bytes = policy().canonicalDefinitionUtf8();

        assertThat(bytes).hasSize(1979);
        assertThat(HexFormat.of().formatHex(
                MessageDigest.getInstance("SHA-256").digest(bytes)))
                .isEqualTo(POLICY_HASH)
                .isEqualTo(policy().definitionHash());
        assertThat(policy().canonicalDefinition())
                .contains("RESULT_CONSTRUCTORS_AND_RESOLVER_SHARE_EXACT_CLASSIFICATION_VALIDATION")
                .contains("ONLY_RESOLVE_ATTESTS_REQUEST_TO_RESULT_INVOCATION")
                .contains("EXACT_AWAITING_ENDPOINT_CHAIN")
                .contains("EVIDENCE_UNAVAILABLE_EVEN_WHEN_ENDPOINT_NOT_REACHED")
                .contains("\"freshness\":\"ABSENT\"")
                .endsWith("\"publication\":\"ABSENT\"}");
        String adr = Files.readString(Path.of(
                "../../decisions/ADR-023-supplied-leaf-target-error-readiness.md"));
        assertThat(adr)
                .contains("1979-byte")
                .contains(POLICY_HASH);
        assertThat(adr.replaceAll("\\s+", " "))
                .contains("exactly 46 test invocations");
        int canonicalStart = adr.indexOf('\n', adr.indexOf("```json")) + 1;
        int canonicalEnd = adr.indexOf("\n```", canonicalStart);
        assertThat(adr.substring(canonicalStart, canonicalEnd))
                .isEqualTo(policy().canonicalDefinition());
        assertThat(unavailableVectors().count()).isEqualTo(39);
        assertThat(unavailableVectors().filter(UnavailableVector::awaiting).count())
                .isEqualTo(1);

        bytes[0] = (byte) '!';
        assertThat(policy().canonicalDefinitionUtf8()[0]).isEqualTo((byte) '{');
    }

    @Test
    void exactFileRecordSealedAndDisconnectedSurfacesAreStable()
            throws Exception {
        Path packagePath = Path.of(
                "src/main/java/com/wallstreetreceipts/api/domain/outcome/targeterrorreadiness");
        try (var files = Files.list(packagePath)) {
            assertThat(files.filter(path -> path.toString().endsWith(".java"))
                    .map(path -> path.getFileName().toString()).sorted().toList())
                    .containsExactly(
                            "TargetErrorReadinessPolicyVersion.java",
                            "TargetErrorReadinessRequest.java",
                            "TargetErrorReadinessResolution.java",
                            "TargetErrorReadinessResolver.java");
        }
        Path testPath = Path.of(
                "src/test/java/com/wallstreetreceipts/api/domain/outcome/targeterrorreadiness");
        try (var files = Files.list(testPath)) {
            assertThat(files.filter(path -> path.toString().endsWith(".java"))
                    .map(path -> path.getFileName().toString()).toList())
                    .containsExactly("TargetErrorReadinessResolverGoldenTest.java");
        }
        assertRecordComponents(TargetErrorReadinessRequest.class,
                "policyVersion:TargetErrorReadinessPolicyVersion",
                "sourceResult:TargetErrorResult");
        assertRecordComponents(TargetErrorReadinessResolution.ResolutionContext.class,
                "policyVersion:TargetErrorReadinessPolicyVersion",
                "policyDefinitionHash:String");
        assertRecordComponents(TargetErrorReadinessResolution.Settled.class,
                "context:ResolutionContext", "sourceResult:TargetErrorResult");
        assertRecordComponents(TargetErrorReadinessResolution.AwaitingEndpoint.class,
                "context:ResolutionContext", "sourceResult:TargetErrorResult");
        assertRecordComponents(
                TargetErrorReadinessResolution.EvidenceUnavailable.class,
                "context:ResolutionContext", "sourceResult:TargetErrorResult");
        assertThat(TargetErrorReadinessResolution.class.isSealed()).isTrue();
        assertThat(Arrays.stream(TargetErrorReadinessResolution.class
                .getPermittedSubclasses()).map(Class::getSimpleName).toList())
                .containsExactlyInAnyOrder(
                        "Settled", "AwaitingEndpoint", "EvidenceUnavailable");
        assertThat(TargetErrorReadinessPolicyVersion.values())
                .containsExactly(policy());

        String request = Files.readString(packagePath.resolve(
                "TargetErrorReadinessRequest.java"));
        String resolution = Files.readString(packagePath.resolve(
                "TargetErrorReadinessResolution.java"));
        String resolver = Files.readString(packagePath.resolve(
                "TargetErrorReadinessResolver.java"));
        assertThat(request + resolution)
                .doesNotContain(".reason()")
                .doesNotContain("UnavailableReason");
        assertThat(count(resolver, ".reason()")).isEqualTo(2);
        assertThat(resolver)
                .contains("ENDPOINT_PRICE_UNAVAILABLE")
                .contains("ENDPOINT_NOT_REACHED_AS_OF")
                .doesNotContain("TARGET_AND_ENDPOINT_PRICE_UNAVAILABLE");
        assertThat(request + resolution + resolver)
                .doesNotContain("OutcomeEvaluationStatus")
                .doesNotContain("CallOutcome")
                .doesNotContain("TargetErrorCalculator.calculate(")
                .doesNotContain("EndpointPriceSelector")
                .doesNotContain("Clock")
                .doesNotContain("@Service")
                .doesNotContain("@Repository")
                .doesNotContain("@Controller");
    }

    @Test
    void nullRootsAndRequiredFieldsFailClosed() {
        var source = availableSource();
        var context = context();

        assertThatThrownBy(() -> TargetErrorReadinessResolver.resolve(null))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new TargetErrorReadinessRequest(null, source))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new TargetErrorReadinessRequest(policy(), null))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new TargetErrorReadinessResolution
                .ResolutionContext(null, POLICY_HASH))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new TargetErrorReadinessResolution
                .ResolutionContext(policy(), null))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new TargetErrorReadinessResolution
                .ResolutionContext(policy(), "0".repeat(64)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new TargetErrorReadinessResolution
                .Settled(null, source))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new TargetErrorReadinessResolution
                .Settled(context, null))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> TargetErrorReadinessResolver
                .requireClassification(source, null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void directResultConstructorsShareExactFailClosedClassification() {
        var settled = availableSource();
        var awaiting = unavailableSource(
                TargetErrorResult.UnavailableReason.ENDPOINT_PRICE_UNAVAILABLE,
                EndpointPriceResolution.UnavailableReason
                        .ENDPOINT_NOT_REACHED_AS_OF);
        var unavailable = unavailableSource(
                TargetErrorResult.UnavailableReason
                        .TARGET_AND_ENDPOINT_PRICE_UNAVAILABLE,
                EndpointPriceResolution.UnavailableReason
                        .ENDPOINT_NOT_REACHED_AS_OF);
        var context = context();

        assertThat(new TargetErrorReadinessResolution.Settled(
                context, settled).sourceResult()).isSameAs(settled);
        assertThat(new TargetErrorReadinessResolution.AwaitingEndpoint(
                context, awaiting).sourceResult()).isSameAs(awaiting);
        assertThat(new TargetErrorReadinessResolution.EvidenceUnavailable(
                context, unavailable).sourceResult()).isSameAs(unavailable);
        assertWrongVariant(() -> new TargetErrorReadinessResolution
                .AwaitingEndpoint(context, settled));
        assertWrongVariant(() -> new TargetErrorReadinessResolution
                .EvidenceUnavailable(context, settled));
        assertWrongVariant(() -> new TargetErrorReadinessResolution
                .Settled(context, awaiting));
        assertWrongVariant(() -> new TargetErrorReadinessResolution
                .EvidenceUnavailable(context, awaiting));
        assertWrongVariant(() -> new TargetErrorReadinessResolution
                .Settled(context, unavailable));
        assertWrongVariant(() -> new TargetErrorReadinessResolution
                .AwaitingEndpoint(context, unavailable));
    }

    @Test
    void equalButDistinctWholeSourceRecordsReplayEqually() {
        var firstSource = unavailableSource(
                TargetErrorResult.UnavailableReason.ENDPOINT_PRICE_UNAVAILABLE,
                EndpointPriceResolution.UnavailableReason
                        .ENDPOINT_NOT_REACHED_AS_OF);
        var secondSource = unavailableSource(
                TargetErrorResult.UnavailableReason.ENDPOINT_PRICE_UNAVAILABLE,
                EndpointPriceResolution.UnavailableReason
                        .ENDPOINT_NOT_REACHED_AS_OF);

        assertThat(secondSource).isEqualTo(firstSource).isNotSameAs(firstSource);
        var first = TargetErrorReadinessResolver.resolve(request(firstSource));
        var second = TargetErrorReadinessResolver.resolve(request(secondSource));

        assertThat(second).isEqualTo(first).isNotSameAs(first);
        assertThat(sourceOf(first)).isSameAs(firstSource);
        assertThat(sourceOf(second)).isSameAs(secondSource);
    }

    @Test
    void classificationIsIndependentOfLocaleTimezoneAndPriorCalls() {
        var source = unavailableSource(
                TargetErrorResult.UnavailableReason.OUTPUT_NOT_REPRESENTABLE,
                null);
        Locale originalLocale = Locale.getDefault();
        TimeZone originalTimeZone = TimeZone.getDefault();

        try {
            Locale.setDefault(Locale.JAPAN);
            TimeZone.setDefault(TimeZone.getTimeZone("Asia/Tokyo"));
            var first = TargetErrorReadinessResolver.resolve(request(source));
            TargetErrorReadinessResolver.resolve(request(availableSource()));

            Locale.setDefault(Locale.GERMANY);
            TimeZone.setDefault(TimeZone.getTimeZone("America/New_York"));
            var second = TargetErrorReadinessResolver.resolve(request(source));

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
    void everyConstructibleUnavailableShapeIsClassified(
            String label,
            TargetErrorResult.Unavailable source,
            Class<? extends TargetErrorReadinessResolution> expectedType) {
        var result = TargetErrorReadinessResolver.resolve(request(source));

        assertThat(result).as(label).isExactlyInstanceOf(expectedType);
        assertThat(sourceOf(result)).isSameAs(source);
        assertThat(result).satisfies(value -> {
            if (expectedType
                    == TargetErrorReadinessResolution.AwaitingEndpoint.class) {
                assertExactAwaitingChain(source);
            }
        });
    }

    static Stream<Arguments> unavailableSourceShapes() {
        return unavailableVectors().map(vector -> Arguments.of(
                vector.label(), vector.source(), expectedType(vector)));
    }

    @ParameterizedTest(name = "settled source {0}")
    @MethodSource("settledSourceShapes")
    void availableTargetErrorSettles(String label, TargetErrorResult source) {
        var result = TargetErrorReadinessResolver.resolve(request(source));

        assertThat(result).as(label)
                .isExactlyInstanceOf(TargetErrorReadinessResolution.Settled.class);
        assertThat(sourceOf(result)).isSameAs(source);
        assertThat(source).isInstanceOf(TargetErrorResult.Available.class);
    }

    static Stream<Arguments> settledSourceShapes() {
        return Stream.of(Arguments.of("available-target-error", availableSource()));
    }

    private static Stream<UnavailableVector> unavailableVectors() {
        Stream<UnavailableVector> resolvedEndpointReasons = Arrays.stream(
                TargetErrorResult.UnavailableReason.values())
                .filter(reason -> !carriesEndpointReason(reason))
                .map(reason -> new UnavailableVector(
                        reason.name(), unavailableSource(reason, null), false));
        Stream<UnavailableVector> endpointReasons = Stream.of(
                TargetErrorResult.UnavailableReason
                        .ENDPOINT_PRICE_UNAVAILABLE,
                TargetErrorResult.UnavailableReason
                        .TARGET_AND_ENDPOINT_PRICE_UNAVAILABLE)
                .flatMap(reason -> Arrays.stream(
                        EndpointPriceResolution.UnavailableReason.values())
                        .map(endpointReason -> new UnavailableVector(
                                reason.name() + "+" + endpointReason.name(),
                                unavailableSource(reason, endpointReason),
                                reason == TargetErrorResult.UnavailableReason
                                                .ENDPOINT_PRICE_UNAVAILABLE
                                        && endpointReason
                                                == EndpointPriceResolution
                                                        .UnavailableReason
                                                        .ENDPOINT_NOT_REACHED_AS_OF)));
        return Stream.concat(resolvedEndpointReasons, endpointReasons);
    }

    private static Class<? extends TargetErrorReadinessResolution> expectedType(
            UnavailableVector vector) {
        return vector.awaiting()
                ? TargetErrorReadinessResolution.AwaitingEndpoint.class
                : TargetErrorReadinessResolution.EvidenceUnavailable.class;
    }

    private static boolean carriesEndpointReason(
            TargetErrorResult.UnavailableReason reason) {
        return reason == TargetErrorResult.UnavailableReason
                .ENDPOINT_PRICE_UNAVAILABLE
                || reason == TargetErrorResult.UnavailableReason
                        .TARGET_AND_ENDPOINT_PRICE_UNAVAILABLE;
    }

    private static TargetErrorReadinessPolicyVersion policy() {
        return TargetErrorReadinessPolicyVersion
                .SUPPLIED_LEAF_TARGET_ERROR_READINESS_V1;
    }

    private static TargetErrorReadinessResolution.ResolutionContext context() {
        return new TargetErrorReadinessResolution.ResolutionContext(
                policy(), POLICY_HASH);
    }

    private static TargetErrorReadinessRequest request(TargetErrorResult source) {
        return new TargetErrorReadinessRequest(policy(), source);
    }

    private static TargetErrorResult.Available availableSource() {
        return new TargetErrorResult.Available(
                sourceContext(resolvedEndpoint()),
                new BigDecimal("0.200000000000"));
    }

    private static TargetErrorResult.Unavailable unavailableSource(
            TargetErrorResult.UnavailableReason reason,
            EndpointPriceResolution.UnavailableReason endpointReason) {
        EndpointPriceResolution endpoint = carriesEndpointReason(reason)
                ? unavailableEndpoint(endpointReason)
                : resolvedEndpoint();
        return new TargetErrorResult.Unavailable(
                sourceContext(endpoint), reason, endpointReason);
    }

    private static TargetErrorResult.CalculationContext sourceContext(
            EndpointPriceResolution endpoint) {
        return new TargetErrorResult.CalculationContext(
                TargetErrorPolicyVersion
                        .ACTUAL_DENOMINATOR_SCALE_12_HALF_EVEN_V1,
                SOURCE_HASH, endpoint);
    }

    private static EndpointPriceResolution.Resolved resolvedEndpoint() {
        EndpointPriceObservation observation = new EndpointPriceObservation(
                "observation-endpoint", "provider-event-endpoint",
                ASSET_ID, VENUE_ID, USD,
                PRICE_SOURCE_ID, PRICE_SOURCE_REVISION,
                "provenance-observation", CALENDAR_ID, CATALOG_REVISION,
                "session-endpoint",
                EndpointPriceField.OFFICIAL_REGULAR_SESSION_CLOSE,
                REQUIRED_ADJUSTMENT,
                CorporateActionContinuity.SPLIT_REVERSE_SPLIT_CONTINUOUS,
                ENDPOINT_CLOSE, ENDPOINT_CLOSE, ENDPOINT_CLOSE,
                new BigDecimal("100"));
        return new EndpointPriceResolution.Resolved(
                endpointContext(), observation);
    }

    private static EndpointPriceResolution.Unavailable unavailableEndpoint(
            EndpointPriceResolution.UnavailableReason reason) {
        return new EndpointPriceResolution.Unavailable(endpointContext(), reason);
    }

    private static EndpointPriceResolution.ResolutionContext endpointContext() {
        return new EndpointPriceResolution.ResolutionContext(
                EndpointPricePolicyVersion
                        .OFFICIAL_PRIMARY_VENUE_CLOSE_SPLIT_ADJUSTED_V1,
                ENDPOINT_HASH, horizonResolution(), catalogEvidence(), binding(),
                AS_OF);
    }

    private static SessionCloseHorizonResolution.Resolved horizonResolution() {
        TradingSession endpoint = new TradingSession(
                "session-endpoint", ENDPOINT_OPEN, ENDPOINT_CLOSE);
        var horizonContext = new SessionCloseHorizonResolution.ResolutionContext(
                SessionCloseHorizonPolicyVersion
                        .STRICTLY_AFTER_BASIS_EVENT_SESSION_CLOSE_V1,
                HORIZON_HASH, ORIGINAL, OutcomeHorizon.D1, 1,
                CALENDAR_ID, CATALOG_REVISION);
        return new SessionCloseHorizonResolution.Resolved(
                new SessionCloseHorizonResolution.ResolvedSessionWindow(
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
                "binding-nvda-xnas", "binding-revision-1",
                ASSET_ID, VENUE_ID, USD,
                PRICE_SOURCE_ID, PRICE_SOURCE_REVISION,
                BASIS_TIME, BASIS_TIME, "provenance-binding");
    }

    private static TargetErrorResult sourceOf(
            TargetErrorReadinessResolution resolution) {
        return switch (resolution) {
            case TargetErrorReadinessResolution.Settled settled ->
                settled.sourceResult();
            case TargetErrorReadinessResolution.AwaitingEndpoint awaiting ->
                awaiting.sourceResult();
            case TargetErrorReadinessResolution.EvidenceUnavailable unavailable ->
                unavailable.sourceResult();
        };
    }

    private static void assertExactAwaitingChain(
            TargetErrorResult.Unavailable source) {
        assertThat(source.reason()).isEqualTo(
                TargetErrorResult.UnavailableReason.ENDPOINT_PRICE_UNAVAILABLE);
        assertThat(source.endpointReason()).isEqualTo(
                EndpointPriceResolution.UnavailableReason
                        .ENDPOINT_NOT_REACHED_AS_OF);
        var endpoint = (EndpointPriceResolution.Unavailable)
                source.context().endpointPriceResolution();
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
            TargetErrorResult.Unavailable source,
            boolean awaiting) {
    }
}
