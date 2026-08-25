package com.wallstreetreceipts.api.domain.outcome.benchmarkreturnreadiness;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.ArrayList;
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

import com.wallstreetreceipts.api.domain.master.AssetType;
import com.wallstreetreceipts.api.domain.outcome.OutcomeHorizon;
import com.wallstreetreceipts.api.domain.outcome.benchmarkassignment
        .BenchmarkAssetClassificationEvidence;
import com.wallstreetreceipts.api.domain.outcome.benchmarkassignment
        .BenchmarkAssignmentEvidence;
import com.wallstreetreceipts.api.domain.outcome.benchmarkassignment
        .BenchmarkAssignmentEvidence.BenchmarkReferenceKind;
import com.wallstreetreceipts.api.domain.outcome.benchmarkassignment
        .BenchmarkAssignmentPolicyVersion;
import com.wallstreetreceipts.api.domain.outcome.benchmarkassignment
        .BenchmarkAssignmentResolution;
import com.wallstreetreceipts.api.domain.outcome.benchmarkreferencepair
        .BenchmarkIndexDivisorContinuityEvidence;
import com.wallstreetreceipts.api.domain.outcome.benchmarkreferencepair
        .BenchmarkIndexDivisorContinuityEvidence.DivisorContinuity;
import com.wallstreetreceipts.api.domain.outcome.benchmarkreferencepair
        .BenchmarkReferenceIndexEvidence;
import com.wallstreetreceipts.api.domain.outcome.benchmarkreferencepair
        .BenchmarkReferenceIndexEvidence.EffectiveInterval;
import com.wallstreetreceipts.api.domain.outcome.benchmarkreferencepair
        .BenchmarkReferenceIndexEvidence.OpenEnded;
import com.wallstreetreceipts.api.domain.outcome.benchmarkreferencepair
        .BenchmarkReferenceIndexEvidence.ReferenceIndexKind;
import com.wallstreetreceipts.api.domain.outcome.benchmarkreferencepair
        .BenchmarkReferenceLevelObservation;
import com.wallstreetreceipts.api.domain.outcome.benchmarkreferencepair
        .BenchmarkReferenceLevelObservation.ReferenceLevelField;
import com.wallstreetreceipts.api.domain.outcome.benchmarkreferencepair
        .BenchmarkReferenceLevelPairPolicyVersion;
import com.wallstreetreceipts.api.domain.outcome.benchmarkreferencepair
        .BenchmarkReferenceLevelPairResolution;
import com.wallstreetreceipts.api.domain.outcome.benchmarkreturn
        .BenchmarkReturnPolicyVersion;
import com.wallstreetreceipts.api.domain.outcome.benchmarkreturn
        .BenchmarkReturnResult;
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
import com.wallstreetreceipts.api.domain.outcome.observation
        .EndpointPriceBinding;
import com.wallstreetreceipts.api.domain.outcome.observation
        .EndpointPriceField;
import com.wallstreetreceipts.api.domain.outcome.observation
        .EndpointPriceObservation;
import com.wallstreetreceipts.api.domain.outcome.observation
        .EndpointPricePolicyVersion;
import com.wallstreetreceipts.api.domain.outcome.observation
        .EndpointPriceResolution;

class BenchmarkReturnReadinessResolverGoldenTest {

    private static final String POLICY_HASH =
            "2dedaf014a149ed81e75941ee3677e3c8b77243b9987d9496709266aad721daf";
    private static final String SOURCE_HASH =
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
            new OutcomeBasis.Original("call-adr033-benchmark", BASIS_TIME);

    @Test
    void canonicalHashAdrDefensiveReadsAndDistributionAreStable()
            throws Exception {
        byte[] bytes = policy().canonicalDefinitionUtf8();
        byte[] second = policy().canonicalDefinitionUtf8();

        assertThat(bytes).isNotSameAs(second).containsExactly(second);
        assertThat(new String(bytes, StandardCharsets.UTF_8))
                .isEqualTo(policy().canonicalDefinition());
        assertThat(policy().canonicalDefinition().chars())
                .allMatch(value -> value >= 0 && value <= 127);
        assertThat(bytes).hasSize(2622);
        assertThat(HexFormat.of().formatHex(
                MessageDigest.getInstance("SHA-256").digest(bytes)))
                .isEqualTo(POLICY_HASH)
                .isEqualTo(policy().definitionHash());
        assertThat(policy().canonicalDefinition())
                .contains("\"requiredBenchmarkReturnPolicyDefinitionHash\":\""
                        + SOURCE_HASH + "\"")
                .contains("RESULT_CONSTRUCTORS_AND_RESOLVER_SHARE_EXACT_CLASSIFICATION_VALIDATION")
                .contains("ONLY_RESOLVE_ATTESTS_REQUEST_TO_RESULT_INVOCATION")
                .contains("EXACT_AWAITING_ENDPOINT_CHAIN")
                .contains("EVIDENCE_UNAVAILABLE_EVEN_WHEN_ENDPOINT_NOT_REACHED")
                .contains("BENCHMARK_RETURN_AVAILABLE_OR_INTENTIONALLY_NOT_APPLICABLE")
                .contains("\"ranking\":\"ABSENT\"")
                .endsWith("\"publication\":\"ABSENT\"}");
        assertThat(sourcePolicy().definitionHash()).isEqualTo(SOURCE_HASH);

        String adr = Files.readString(Path.of(
                "../../decisions/ADR-033-independent-benchmark-sector-return-readiness.md"));
        assertThat(adr)
                .contains("2622-byte")
                .contains(POLICY_HASH)
                .contains("ADR-031")
                .contains(SOURCE_HASH);
        assertThat(adr.replaceAll("\\s+", " "))
                .contains("benchmark golden executes exactly 87 test invocations");
        assertThat(adrJsonBlock(adr, "SUPPLIED_LEAF_BENCHMARK_RETURN_READINESS_V1"))
                .isEqualTo(policy().canonicalDefinition());

        List<ClassificationVector> vectors = classificationVectors();
        assertThat(vectors).hasSize(81);
        assertThat(vectors.stream().filter(vector -> vector.source()
                instanceof BenchmarkReturnResult.Available)).hasSize(1);
        assertThat(vectors.stream().filter(vector -> vector.source()
                instanceof BenchmarkReturnResult.NotApplicable)).hasSize(4);
        assertThat(vectors.stream().filter(vector -> vector.source()
                instanceof BenchmarkReturnResult.AssignmentUnavailable))
                .hasSize(19);
        assertThat(vectors.stream().filter(vector -> vector.source()
                instanceof BenchmarkReturnResult.EndpointAnchorUnavailable))
                .hasSize(3);
        assertThat(vectors.stream().filter(vector -> vector.source()
                instanceof BenchmarkReturnResult.EvidenceUnavailable)).hasSize(53);
        assertThat(vectors.stream().filter(vector -> vector.source()
                instanceof BenchmarkReturnResult.OutputUnavailable)).hasSize(1);
        assertThat(vectors.stream().filter(vector -> vector.expectedType()
                == BenchmarkReturnReadinessResolution.Settled.class)).hasSize(5);
        assertThat(vectors.stream().filter(vector -> vector.expectedType()
                == BenchmarkReturnReadinessResolution.AwaitingEndpoint.class))
                .hasSize(1);
        assertThat(vectors.stream().filter(vector -> vector.expectedType()
                == BenchmarkReturnReadinessResolution.EvidenceUnavailable.class))
                .hasSize(75);
        assertExactAwaitingChain((BenchmarkReturnResult.EvidenceUnavailable)
                vectors.stream()
                        .filter(vector -> vector.expectedType()
                                == BenchmarkReturnReadinessResolution
                                        .AwaitingEndpoint.class)
                        .findFirst().orElseThrow().source());

        bytes[0] = (byte) '!';
        assertThat(policy().canonicalDefinitionUtf8()[0]).isEqualTo((byte) '{');
    }

    @Test
    void publicSurfaceAndImportFirewallAreStable() throws Exception {
        Path packagePath = Path.of(
                "src/main/java/com/wallstreetreceipts/api/domain/outcome/benchmarkreturnreadiness");
        try (var files = Files.list(packagePath)) {
            assertThat(files.filter(path -> path.toString().endsWith(".java"))
                    .map(path -> path.getFileName().toString()).sorted().toList())
                    .containsExactly(
                            "BenchmarkReturnReadinessPolicyVersion.java",
                            "BenchmarkReturnReadinessRequest.java",
                            "BenchmarkReturnReadinessResolution.java",
                            "BenchmarkReturnReadinessResolver.java");
        }
        Path testPath = Path.of(
                "src/test/java/com/wallstreetreceipts/api/domain/outcome/benchmarkreturnreadiness");
        try (var files = Files.list(testPath)) {
            assertThat(files.filter(path -> path.toString().endsWith(".java"))
                    .map(path -> path.getFileName().toString()).toList())
                    .containsExactly(
                            "BenchmarkReturnReadinessResolverGoldenTest.java");
        }
        assertRecordComponents(BenchmarkReturnReadinessRequest.class,
                "policyVersion:BenchmarkReturnReadinessPolicyVersion",
                "sourceResult:BenchmarkReturnResult");
        assertRecordComponents(
                BenchmarkReturnReadinessResolution.ResolutionContext.class,
                "policyVersion:BenchmarkReturnReadinessPolicyVersion",
                "policyDefinitionHash:String");
        assertRecordComponents(BenchmarkReturnReadinessResolution.Settled.class,
                "context:ResolutionContext", "sourceResult:BenchmarkReturnResult");
        assertRecordComponents(
                BenchmarkReturnReadinessResolution.AwaitingEndpoint.class,
                "context:ResolutionContext", "sourceResult:BenchmarkReturnResult");
        assertRecordComponents(
                BenchmarkReturnReadinessResolution.EvidenceUnavailable.class,
                "context:ResolutionContext", "sourceResult:BenchmarkReturnResult");
        assertThat(BenchmarkReturnReadinessResolution.class.isSealed()).isTrue();
        assertThat(Arrays.stream(BenchmarkReturnReadinessResolution.class
                .getPermittedSubclasses()).map(Class::getSimpleName).toList())
                .containsExactlyInAnyOrder(
                        "Settled", "AwaitingEndpoint", "EvidenceUnavailable");
        assertThat(BenchmarkReturnReadinessPolicyVersion.values())
                .containsExactly(policy());

        String request = Files.readString(packagePath.resolve(
                "BenchmarkReturnReadinessRequest.java"));
        String resolution = Files.readString(packagePath.resolve(
                "BenchmarkReturnReadinessResolution.java"));
        String resolver = Files.readString(packagePath.resolve(
                "BenchmarkReturnReadinessResolver.java"));
        assertThat(request + resolution)
                .doesNotContain(".reason()")
                .doesNotContain("UnavailableReason");
        assertThat(count(resolver, ".reason()")).isEqualTo(1);
        assertThat(request + resolution + resolver)
                .doesNotContain("sectorreturn")
                .doesNotContain("SectorReturn")
                .doesNotContain("assetreturn")
                .doesNotContain("AssetReturn")
                .doesNotContain("directionalwin")
                .doesNotContain("Target")
                .doesNotContain("OutcomeEvaluationStatus")
                .doesNotContain("CallOutcome")
                .doesNotContain("BenchmarkReturnCalculator")
                .doesNotContain("BenchmarkReferenceLevelPairSelector")
                .doesNotContain("Clock")
                .doesNotContain("@Service")
                .doesNotContain("@Repository")
                .doesNotContain("@Controller");
    }

    @Test
    void nullRootsAndPolicyValidationFailClosed() {
        BenchmarkReturnResult source = availableSource();
        var context = context();

        assertThatThrownBy(() -> BenchmarkReturnReadinessResolver.resolve(null))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new BenchmarkReturnReadinessRequest(null, source))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new BenchmarkReturnReadinessRequest(policy(), null))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new BenchmarkReturnReadinessResolution
                .ResolutionContext(null, POLICY_HASH))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new BenchmarkReturnReadinessResolution
                .ResolutionContext(policy(), null))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new BenchmarkReturnReadinessResolution
                .ResolutionContext(policy(), "0".repeat(64)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new BenchmarkReturnReadinessResolution
                .Settled(null, source))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new BenchmarkReturnReadinessResolution
                .Settled(context, null))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> BenchmarkReturnReadinessResolver
                .requireClassification(source, null))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new BenchmarkReturnResult.CalculationContext(
                sourcePolicy(), "0".repeat(64), resolvedPair("100", "120")))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void directResultConstructorsShareExactFailClosedClassification() {
        BenchmarkReturnResult settled = availableSource();
        BenchmarkReturnResult awaiting = evidenceUnavailableSource(
                BenchmarkReferenceLevelPairResolution.UnavailableReason
                        .ENDPOINT_NOT_REACHED_AS_OF);
        BenchmarkReturnResult unavailable = assignmentUnavailableSource(
                BenchmarkAssignmentResolution.UnavailableReason
                        .CLASSIFICATION_MISSING_AS_OF);
        var context = context();

        assertThat(new BenchmarkReturnReadinessResolution.Settled(
                context, settled).sourceResult()).isSameAs(settled);
        assertThat(new BenchmarkReturnReadinessResolution.AwaitingEndpoint(
                context, awaiting).sourceResult()).isSameAs(awaiting);
        assertThat(new BenchmarkReturnReadinessResolution.EvidenceUnavailable(
                context, unavailable).sourceResult()).isSameAs(unavailable);
        assertWrongVariant(() -> new BenchmarkReturnReadinessResolution
                .AwaitingEndpoint(context, settled));
        assertWrongVariant(() -> new BenchmarkReturnReadinessResolution
                .EvidenceUnavailable(context, settled));
        assertWrongVariant(() -> new BenchmarkReturnReadinessResolution
                .Settled(context, awaiting));
        assertWrongVariant(() -> new BenchmarkReturnReadinessResolution
                .EvidenceUnavailable(context, awaiting));
        assertWrongVariant(() -> new BenchmarkReturnReadinessResolution
                .Settled(context, unavailable));
        assertWrongVariant(() -> new BenchmarkReturnReadinessResolution
                .AwaitingEndpoint(context, unavailable));
    }

    @Test
    void equalButDistinctWholeSourceRecordsReplayEqually() {
        BenchmarkReturnResult firstSource = evidenceUnavailableSource(
                BenchmarkReferenceLevelPairResolution.UnavailableReason
                        .ENDPOINT_NOT_REACHED_AS_OF);
        BenchmarkReturnResult secondSource = evidenceUnavailableSource(
                BenchmarkReferenceLevelPairResolution.UnavailableReason
                        .ENDPOINT_NOT_REACHED_AS_OF);

        assertThat(secondSource).isEqualTo(firstSource).isNotSameAs(firstSource);
        var first = BenchmarkReturnReadinessResolver.resolve(request(firstSource));
        var second = BenchmarkReturnReadinessResolver.resolve(request(secondSource));

        assertThat(second).isEqualTo(first).isNotSameAs(first);
        assertThat(sourceOf(first)).isSameAs(firstSource);
        assertThat(sourceOf(second)).isSameAs(secondSource);
    }

    @Test
    void classificationIsIndependentOfLocaleTimezoneAndPriorCalls() {
        BenchmarkReturnResult source = evidenceUnavailableSource(
                BenchmarkReferenceLevelPairResolution.UnavailableReason
                        .REFERENCE_INDEX_MISSING_AS_OF);
        Locale originalLocale = Locale.getDefault();
        TimeZone originalTimeZone = TimeZone.getDefault();

        try {
            Locale.setDefault(Locale.JAPAN);
            TimeZone.setDefault(TimeZone.getTimeZone("Asia/Tokyo"));
            var first = BenchmarkReturnReadinessResolver.resolve(request(source));
            BenchmarkReturnReadinessResolver.resolve(request(availableSource()));

            Locale.setDefault(Locale.GERMANY);
            TimeZone.setDefault(TimeZone.getTimeZone("America/New_York"));
            var second = BenchmarkReturnReadinessResolver.resolve(request(source));

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
            BenchmarkReturnResult source,
            Class<? extends BenchmarkReturnReadinessResolution> expectedType) {
        var result = BenchmarkReturnReadinessResolver.resolve(request(source));

        assertThat(result).as(label).isExactlyInstanceOf(expectedType);
        assertThat(sourceOf(result)).isSameAs(source);
        assertThat(contextOf(result).policyVersion()).isSameAs(policy());
        assertThat(contextOf(result).policyDefinitionHash())
                .isEqualTo(POLICY_HASH);
        if (expectedType == BenchmarkReturnReadinessResolution
                .AwaitingEndpoint.class) {
            assertExactAwaitingChain((BenchmarkReturnResult.EvidenceUnavailable)
                    source);
        }
    }

    static Stream<Arguments> classificationSourceShapes() {
        return classificationVectors().stream().map(vector -> Arguments.of(
                vector.label(), vector.source(), vector.expectedType()));
    }

    private static List<ClassificationVector> classificationVectors() {
        List<ClassificationVector> vectors = new ArrayList<>();
        vectors.add(new ClassificationVector("available", availableSource(),
                BenchmarkReturnReadinessResolution.Settled.class));
        for (BenchmarkAssignmentResolution.NotApplicableReason reason
                : BenchmarkAssignmentResolution.NotApplicableReason.values()) {
            vectors.add(new ClassificationVector(
                    "not-applicable-" + reason.name(),
                    notApplicableSource(reason),
                    BenchmarkReturnReadinessResolution.Settled.class));
        }
        for (BenchmarkAssignmentResolution.UnavailableReason reason
                : BenchmarkAssignmentResolution.UnavailableReason.values()) {
            vectors.add(new ClassificationVector(
                    "assignment-unavailable-" + reason.name(),
                    assignmentUnavailableSource(reason),
                    BenchmarkReturnReadinessResolution
                            .EvidenceUnavailable.class));
        }
        for (BenchmarkReferenceLevelPairResolution
                .EndpointAnchorUnavailableReason reason
                        : BenchmarkReferenceLevelPairResolution
                                .EndpointAnchorUnavailableReason.values()) {
            vectors.add(new ClassificationVector(
                    "endpoint-anchor-unavailable-" + reason.name(),
                    endpointAnchorUnavailableSource(reason),
                    BenchmarkReturnReadinessResolution
                            .EvidenceUnavailable.class));
        }
        for (BenchmarkReferenceLevelPairResolution.UnavailableReason reason
                : BenchmarkReferenceLevelPairResolution
                        .UnavailableReason.values()) {
            vectors.add(new ClassificationVector(
                    "evidence-unavailable-" + reason.name(),
                    evidenceUnavailableSource(reason),
                    reason == BenchmarkReferenceLevelPairResolution
                            .UnavailableReason.ENDPOINT_NOT_REACHED_AS_OF
                                    ? BenchmarkReturnReadinessResolution
                                            .AwaitingEndpoint.class
                                    : BenchmarkReturnReadinessResolution
                                            .EvidenceUnavailable.class));
        }
        vectors.add(new ClassificationVector("output-unavailable",
                outputUnavailableSource(),
                BenchmarkReturnReadinessResolution.EvidenceUnavailable.class));
        return List.copyOf(vectors);
    }

    private static BenchmarkReturnReadinessPolicyVersion policy() {
        return BenchmarkReturnReadinessPolicyVersion
                .SUPPLIED_LEAF_BENCHMARK_RETURN_READINESS_V1;
    }

    private static BenchmarkReturnReadinessResolution.ResolutionContext context() {
        return new BenchmarkReturnReadinessResolution.ResolutionContext(
                policy(), POLICY_HASH);
    }

    private static BenchmarkReturnReadinessRequest request(
            BenchmarkReturnResult source) {
        return new BenchmarkReturnReadinessRequest(policy(), source);
    }

    private static BenchmarkReturnResult.Available availableSource() {
        BenchmarkReferenceLevelPairResolution pair = resolvedPair("100", "120");
        return new BenchmarkReturnResult.Available(
                sourceContext(pair), new BigDecimal("0.200000000000"));
    }

    private static BenchmarkReturnResult.NotApplicable notApplicableSource(
            BenchmarkAssignmentResolution.NotApplicableReason reason) {
        BenchmarkAssignmentResolution.NotApplicable assignment =
                notApplicableAssignment(reason);
        BenchmarkAssetClassificationEvidence classification =
                assignment.classificationEvidence();
        BenchmarkReferenceLevelPairResolution pair =
                new BenchmarkReferenceLevelPairResolution.NotApplicable(
                        pairContext(assignment, endpoint(EndpointState.USABLE,
                                classification.primaryVenueId(),
                                classification.currency())));
        return new BenchmarkReturnResult.NotApplicable(sourceContext(pair));
    }

    private static BenchmarkReturnResult.AssignmentUnavailable
            assignmentUnavailableSource(
                    BenchmarkAssignmentResolution.UnavailableReason reason) {
        BenchmarkReferenceLevelPairResolution pair =
                new BenchmarkReferenceLevelPairResolution.AssignmentUnavailable(
                        pairContext(new BenchmarkAssignmentResolution.Unavailable(
                                assignmentContext(), reason),
                                endpoint(EndpointState.USABLE)));
        return new BenchmarkReturnResult.AssignmentUnavailable(
                sourceContext(pair));
    }

    private static BenchmarkReturnResult.EndpointAnchorUnavailable
            endpointAnchorUnavailableSource(
                    BenchmarkReferenceLevelPairResolution
                            .EndpointAnchorUnavailableReason reason) {
        EndpointState state = switch (reason) {
            case CATALOG_NOT_KNOWN_AS_OF -> EndpointState.FUTURE_CATALOG;
            case CATALOG_EVIDENCE_MISMATCH -> EndpointState.MISMATCHED_CATALOG;
            case BINDING_NOT_KNOWN_AS_OF -> EndpointState.FUTURE_BINDING;
        };
        BenchmarkReferenceLevelPairResolution pair =
                new BenchmarkReferenceLevelPairResolution
                        .EndpointAnchorUnavailable(
                                pairContext(resolvedAssignment(),
                                        endpoint(state, true)),
                                reason);
        return new BenchmarkReturnResult.EndpointAnchorUnavailable(
                sourceContext(pair));
    }

    private static BenchmarkReturnResult.EvidenceUnavailable
            evidenceUnavailableSource(
                    BenchmarkReferenceLevelPairResolution.UnavailableReason
                            reason) {
        EndpointPriceResolution endpoint = reason
                == BenchmarkReferenceLevelPairResolution.UnavailableReason
                        .ENDPOINT_NOT_REACHED_AS_OF
                                ? endpoint(EndpointState.FUTURE_ENDPOINT)
                                : endpoint(EndpointState.USABLE);
        BenchmarkReferenceLevelPairResolution pair =
                new BenchmarkReferenceLevelPairResolution.EvidenceUnavailable(
                        pairContext(resolvedAssignment(), endpoint), reason);
        return new BenchmarkReturnResult.EvidenceUnavailable(
                sourceContext(pair));
    }

    private static BenchmarkReturnResult.OutputUnavailable outputUnavailableSource() {
        BenchmarkReferenceLevelPairResolution pair = resolvedPair("100", "120");
        return new BenchmarkReturnResult.OutputUnavailable(
                sourceContext(pair),
                BenchmarkReturnResult.OutputUnavailableReason
                        .OUTPUT_NOT_REPRESENTABLE);
    }

    private static BenchmarkReturnResult.CalculationContext sourceContext(
            BenchmarkReferenceLevelPairResolution pair) {
        return new BenchmarkReturnResult.CalculationContext(
                sourcePolicy(), SOURCE_HASH, pair);
    }

    private static BenchmarkReferenceLevelPairResolution.Resolved resolvedPair(
            String basisValue, String endpointValue) {
        BenchmarkAssignmentResolution.Resolved assignment = resolvedAssignment();
        EndpointPriceResolution endpoint = endpoint(EndpointState.USABLE);
        BenchmarkAssignmentEvidence assignmentEvidence =
                assignment.assignmentEvidence();
        BenchmarkReferenceIndexEvidence reference =
                new BenchmarkReferenceIndexEvidence(
                        "reference-evidence-spx",
                        "provider-event-reference-spx",
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
                BASIS_TIME, BASIS_TIME.plusSeconds(2),
                new BigDecimal(basisValue));
        BenchmarkReferenceLevelObservation endpointLevel = level(
                "endpoint-level", "provider-event-endpoint-level", reference,
                ENDPOINT_CLOSE, ENDPOINT_CLOSE, new BigDecimal(endpointValue));
        BenchmarkIndexDivisorContinuityEvidence continuity =
                new BenchmarkIndexDivisorContinuityEvidence(
                        "continuity-evidence", "provider-event-continuity",
                        reference.referenceIndexEvidenceId(),
                        reference.providerEventId(),
                        reference.benchmarkAssetId(),
                        reference.benchmarkAssetType(),
                        reference.referenceProviderId(),
                        reference.referenceIndexId(),
                        reference.referenceIndexDefinitionRevision(),
                        reference.referenceKind(), reference.currency(),
                        reference.calculationVenueId(), reference.calendarId(),
                        reference.calendarRevision(),
                        reference.calendarSourceId(),
                        reference.calendarSourceRevision(),
                        reference.continuitySourceId(),
                        reference.continuitySourceRevision(),
                        "provenance-continuity", basis.observationId(),
                        basis.providerEventId(), endpointLevel.observationId(),
                        endpointLevel.providerEventId(), BASIS_TIME,
                        ENDPOINT_CLOSE,
                        DivisorContinuity
                                .PROVIDER_PUBLISHED_INDEX_DIVISOR_CONTINUITY_ATTESTED,
                        ENDPOINT_CLOSE, ENDPOINT_CLOSE);
        return new BenchmarkReferenceLevelPairResolution.Resolved(
                pairContext(assignment, endpoint), reference, basis, endpointLevel,
                continuity);
    }

    private static BenchmarkReferenceLevelObservation level(
            String observationId,
            String providerEventId,
            BenchmarkReferenceIndexEvidence reference,
            Instant observedAt,
            Instant availableAt,
            BigDecimal value) {
        return new BenchmarkReferenceLevelObservation(
                observationId, providerEventId,
                reference.referenceIndexEvidenceId(), reference.providerEventId(),
                reference.benchmarkAssetId(), reference.benchmarkAssetType(),
                reference.referenceProviderId(), reference.referenceIndexId(),
                reference.referenceIndexDefinitionRevision(),
                reference.referenceKind(), reference.currency(),
                reference.calculationVenueId(), reference.calendarId(),
                reference.calendarRevision(), reference.calendarSourceId(),
                reference.calendarSourceRevision(), reference.levelSourceId(),
                reference.levelSourceRevision(), "provenance-" + observationId,
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

    private static BenchmarkAssignmentResolution.NotApplicable
            notApplicableAssignment(
                    BenchmarkAssignmentResolution.NotApplicableReason reason) {
        BenchmarkAssetClassificationEvidence classification = switch (reason) {
            case NON_EQUITY -> classification(
                    AssetType.INDEX, "US", USD, VENUE_ID);
            case NON_US_PRIMARY_VENUE -> classification(
                    AssetType.EQUITY, "CA", USD, "venue-xtsx");
            case NON_USD_CURRENCY -> classification(
                    AssetType.EQUITY, "US", CAD, VENUE_ID);
            case NON_US_PRIMARY_VENUE_AND_NON_USD_CURRENCY -> classification(
                    AssetType.EQUITY, "CA", CAD, "venue-xtsx");
        };
        return new BenchmarkAssignmentResolution.NotApplicable(
                assignmentContext(), classification, reason);
    }

    private static BenchmarkAssetClassificationEvidence classification(
            AssetType assetType,
            String country,
            Currency currency,
            String venue) {
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

    private static EndpointPriceResolution endpoint(EndpointState state) {
        return endpoint(state, VENUE_ID, USD, false);
    }

    private static EndpointPriceResolution endpoint(
            EndpointState state,
            boolean futureEndpoint) {
        return endpoint(state, VENUE_ID, USD, futureEndpoint);
    }

    private static EndpointPriceResolution endpoint(
            EndpointState state,
            String venue,
            Currency currency) {
        return endpoint(state, venue, currency, false);
    }

    private static EndpointPriceResolution endpoint(
            EndpointState state,
            String venue,
            Currency currency,
            boolean futureEndpoint) {
        Instant close = futureEndpoint || state == EndpointState.FUTURE_ENDPOINT
                ? AS_OF.plusSeconds(60) : ENDPOINT_CLOSE;
        TradingSession session = new TradingSession(
                "session-endpoint", close.minusSeconds(23_400), close);
        var horizonContext = new SessionCloseHorizonResolution.ResolutionContext(
                SessionCloseHorizonPolicyVersion
                        .STRICTLY_AFTER_BASIS_EVENT_SESSION_CLOSE_V1,
                HORIZON_HASH, BASIS, OutcomeHorizon.D1, 1, "calendar-primary",
                "catalog-revision-1");
        var horizon = new SessionCloseHorizonResolution.Resolved(
                new SessionCloseHorizonResolution.ResolvedSessionWindow(
                        horizonContext, List.of(session), session));
        String catalogCalendar = state == EndpointState.MISMATCHED_CATALOG
                ? "calendar-other" : "calendar-primary";
        Instant catalogKnown = state == EndpointState.FUTURE_CATALOG
                ? AS_OF.plusSeconds(1) : BASIS_TIME;
        Instant bindingKnown = state == EndpointState.FUTURE_BINDING
                ? AS_OF.plusSeconds(1) : BASIS_TIME;
        var context = new EndpointPriceResolution.ResolutionContext(
                EndpointPricePolicyVersion
                        .OFFICIAL_PRIMARY_VENUE_CLOSE_SPLIT_ADJUSTED_V1,
                ENDPOINT_HASH, horizon,
                new CatalogPointInTimeEvidence(
                        catalogCalendar, "catalog-revision-1",
                        "calendar-source", "calendar-source-revision-1",
                        catalogKnown, catalogKnown, "provenance-calendar"),
                new EndpointPriceBinding(
                        "binding-primary", "binding-revision-1", ASSET_ID,
                        venue, currency, "price-source",
                        "price-source-revision-1", bindingKnown, bindingKnown,
                        "provenance-binding"),
                AS_OF);
        if (state != EndpointState.USABLE) {
            return new EndpointPriceResolution.Unavailable(
                    context,
                    state == EndpointState.FUTURE_ENDPOINT
                            ? EndpointPriceResolution.UnavailableReason
                                    .ENDPOINT_NOT_REACHED_AS_OF
                            : EndpointPriceResolution.UnavailableReason
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
                ENDPOINT_CLOSE, ENDPOINT_CLOSE, ENDPOINT_CLOSE,
                new BigDecimal("100.000000000000"));
        return new EndpointPriceResolution.Resolved(context, observation);
    }

    private static BenchmarkReferenceLevelPairResolution.ResolutionContext
            pairContext(
                    BenchmarkAssignmentResolution assignment,
                    EndpointPriceResolution endpoint) {
        return new BenchmarkReferenceLevelPairResolution.ResolutionContext(
                BenchmarkReferenceLevelPairPolicyVersion
                        .POINT_IN_TIME_EXACT_BENCHMARK_PRICE_INDEX_LEVEL_PAIR_V1,
                PAIR_HASH, assignment, endpoint);
    }

    private static BenchmarkReturnPolicyVersion sourcePolicy() {
        return BenchmarkReturnPolicyVersion
                .SIGNED_BENCHMARK_BASIS_LEVEL_DENOMINATOR_SCALE_12_HALF_EVEN_V1;
    }

    private static BenchmarkReturnResult sourceOf(
            BenchmarkReturnReadinessResolution resolution) {
        return switch (resolution) {
            case BenchmarkReturnReadinessResolution.Settled settled ->
                settled.sourceResult();
            case BenchmarkReturnReadinessResolution.AwaitingEndpoint awaiting ->
                awaiting.sourceResult();
            case BenchmarkReturnReadinessResolution.EvidenceUnavailable unavailable ->
                unavailable.sourceResult();
        };
    }

    private static BenchmarkReturnReadinessResolution.ResolutionContext contextOf(
            BenchmarkReturnReadinessResolution resolution) {
        return switch (resolution) {
            case BenchmarkReturnReadinessResolution.Settled settled ->
                settled.context();
            case BenchmarkReturnReadinessResolution.AwaitingEndpoint awaiting ->
                awaiting.context();
            case BenchmarkReturnReadinessResolution.EvidenceUnavailable unavailable ->
                unavailable.context();
        };
    }

    private static void assertExactAwaitingChain(
            BenchmarkReturnResult.EvidenceUnavailable source) {
        var pair = (BenchmarkReferenceLevelPairResolution.EvidenceUnavailable)
                source.context().referenceLevelPairResolution();
        assertThat(pair.reason()).isEqualTo(
                BenchmarkReferenceLevelPairResolution.UnavailableReason
                        .ENDPOINT_NOT_REACHED_AS_OF);
        var endpoint = BenchmarkReferenceLevelPairRequestAccess.endpoint(pair);
        assertThat(endpoint).isInstanceOf(EndpointPriceResolution.Unavailable.class);
        assertThat(((EndpointPriceResolution.Unavailable) endpoint).reason())
                .isEqualTo(EndpointPriceResolution.UnavailableReason
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

    private static String adrJsonBlock(String adr, String policyVersion) {
        int searchFrom = 0;
        while (true) {
            int fence = adr.indexOf("```json", searchFrom);
            if (fence < 0) {
                throw new AssertionError("missing ADR JSON block for "
                        + policyVersion);
            }
            int canonicalStart = adr.indexOf('\n', fence) + 1;
            int canonicalEnd = adr.indexOf("\n```", canonicalStart);
            String block = adr.substring(canonicalStart, canonicalEnd);
            if (block.contains("\"policyVersion\":\"" + policyVersion + "\"")) {
                return block;
            }
            searchFrom = canonicalEnd + 1;
        }
    }

    private record ClassificationVector(
            String label,
            BenchmarkReturnResult source,
            Class<? extends BenchmarkReturnReadinessResolution> expectedType) {
    }

    private enum EndpointState {
        USABLE,
        FUTURE_CATALOG,
        MISMATCHED_CATALOG,
        FUTURE_BINDING,
        FUTURE_ENDPOINT
    }

    private static final class BenchmarkReferenceLevelPairRequestAccess {

        private BenchmarkReferenceLevelPairRequestAccess() {
        }

        private static EndpointPriceResolution endpoint(
                BenchmarkReferenceLevelPairResolution.EvidenceUnavailable pair) {
            return pair.context().endpointPriceResolution();
        }
    }
}
