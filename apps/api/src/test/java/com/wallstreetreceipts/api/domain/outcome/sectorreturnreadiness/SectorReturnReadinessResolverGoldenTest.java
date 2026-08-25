package com.wallstreetreceipts.api.domain.outcome.sectorreturnreadiness;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
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
import com.wallstreetreceipts.api.domain.outcome.sectorassignment.SectorMembershipEvidence;
import com.wallstreetreceipts.api.domain.outcome.sectorreferencepair.SectorIndexDivisorContinuityEvidence;
import com.wallstreetreceipts.api.domain.outcome.sectorreferencepair.SectorReferenceIndexEvidence;
import com.wallstreetreceipts.api.domain.outcome.sectorreferencepair.SectorReferenceLevelObservation;
import com.wallstreetreceipts.api.domain.outcome.sectorreferencepair.SectorReferenceLevelPairPolicyVersion;
import com.wallstreetreceipts.api.domain.outcome.sectorreferencepair.SectorReferenceLevelPairResolution;
import com.wallstreetreceipts.api.domain.outcome.sectorreturn.SectorReturnPolicyVersion;
import com.wallstreetreceipts.api.domain.outcome.sectorreturn.SectorReturnResult;

class SectorReturnReadinessResolverGoldenTest {

    private static final String POLICY_HASH =
            "5737f44ebc6e65270300889dd5c2e92da0c4f3a2f04e4c6c43e4483e522187d4";
    private static final String SOURCE_HASH =
            "5aecd42c32ba69f0d21ab6e1ee1e3128cd31584724a6f46e176acd470204d0f7";
    private static final String PAIR_HASH =
            "4224648ba01104fd3e96319158c7d6b42da472e9aa6f2ab22ef9fccf43da7e4a";
    private static final String ASSIGNMENT_HASH =
            "52d9f705a3a8a965a6fca79d36bd94ed8836642f1a2c4e5f29a878d0a267311c";
    private static final String ENDPOINT_HASH =
            "37e37aba9302d77366cef4129f77a82b7ccb2f1937bfffc0315ea8d0bc6b1f76";
    private static final String HORIZON_HASH =
            "550087efe7ddf2ba31974c89c2740ab79df986eefef48919c32c56a3232f8dc1";
    private static final String TAXONOMY_HASH =
            "820ce3ea264d67312fe4f2efe346631a81d74248e9a7f041793d65d8ef0d62ae";
    private static final String MAPPING_POLICY_HASH =
            "ba12a277d5ffe266af1745b98948a1e2206494ac31904f31a419d973d5067e77";
    private static final String MAPPING_SET_HASH =
            "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa";
    private static final String ASSET_ID = "asset-nvda";
    private static final String VENUE_ID = "venue-xnas";
    private static final String CALENDAR_ID = "calendar-primary-us-equity";
    private static final String CATALOG_REVISION = "calendar-revision-7";
    private static final String PRICE_SOURCE_ID = "source-official-close";
    private static final String PRICE_SOURCE_REVISION = "source-revision-3";
    private static final String CANONICAL_NODE_ID =
            "wsr-sector-digital-systems";
    private static final String REFERENCE_ASSET_ID = "sector-index-digital";
    private static final String REFERENCE_PROVIDER_ID =
            "provider-sector-index";
    private static final String REFERENCE_INDEX_ID = "index-digital-price";
    private static final String REFERENCE_INDEX_REVISION =
            "index-definition-revision-4";
    private static final String LEVEL_SOURCE_ID = "source-sector-level";
    private static final String LEVEL_SOURCE_REVISION = "level-revision-5";
    private static final String CONTINUITY_SOURCE_ID =
            "source-sector-divisor";
    private static final String CONTINUITY_SOURCE_REVISION =
            "continuity-revision-2";
    private static final Currency USD = Currency.getInstance("USD");
    private static final Instant BASIS_TIME =
            Instant.parse("2026-08-20T14:00:00Z");
    private static final Instant ENDPOINT_OPEN =
            Instant.parse("2026-08-21T13:30:00Z");
    private static final Instant ENDPOINT_CLOSE =
            Instant.parse("2026-08-21T20:00:00Z");
    private static final Instant REACHED_AS_OF =
            Instant.parse("2026-08-21T20:01:00Z");
    private static final Instant AWAITING_AS_OF =
            Instant.parse("2026-08-21T19:59:00Z");
    private static final OutcomeBasis ORIGINAL =
            new OutcomeBasis.Original("call-sector", BASIS_TIME);
    private static final EndpointPriceAdjustmentBasis REQUIRED_ADJUSTMENT =
            EndpointPriceAdjustmentBasis
                    .SPLIT_AND_REVERSE_SPLIT_ADJUSTED_TO_ENDPOINT_SHARE_BASIS_DIVIDEND_UNADJUSTED;

    @Test
    void canonicalDefinitionHasExactBytesHashAdrDefensiveReadsAndDistribution()
            throws Exception {
        byte[] bytes = policy().canonicalDefinitionUtf8();

        assertThat(bytes).hasSize(2592);
        assertThat(HexFormat.of().formatHex(
                MessageDigest.getInstance("SHA-256").digest(bytes)))
                .isEqualTo(POLICY_HASH)
                .isEqualTo(policy().definitionHash());
        assertThat(policy().canonicalDefinition())
                .contains("COMPLETE_SUPPLIED_SECTOR_RETURN_RESULT")
                .contains("RESULT_CONSTRUCTORS_AND_RESOLVER_SHARE_EXACT_CLASSIFICATION_VALIDATION")
                .contains("ONLY_RESOLVE_ATTESTS_REQUEST_TO_RESULT_INVOCATION")
                .contains("EXACT_AWAITING_ENDPOINT_CHAIN")
                .contains("EVIDENCE_UNAVAILABLE_EVEN_WHEN_ENDPOINT_NOT_REACHED")
                .contains("\"requiredSectorReturnPolicyDefinitionHash\":\""
                        + SOURCE_HASH + "\"")
                .endsWith("\"publication\":\"ABSENT\"}");
        String adr = Files.readString(Path.of(
                "../../decisions/ADR-033-independent-benchmark-sector-return-readiness.md"));
        assertThat(adr)
                .contains("2592-byte")
                .contains(POLICY_HASH)
                .contains(SOURCE_HASH);
        assertThat(adr.replaceAll("\\s+", " "))
                .contains("sector golden executes exactly 104 test invocations");
        assertThat(sectorCanonicalJsonBlock(adr))
                .isEqualTo(policy().canonicalDefinition());
        List<ClassificationVector> vectors = classificationVectors();
        assertThat(vectors).hasSize(98);
        assertThat(vectors.stream().filter(vector -> vector.expectedType()
                == SectorReturnReadinessResolution.Settled.class)).hasSize(2);
        assertThat(vectors.stream().filter(vector -> vector.expectedType()
                == SectorReturnReadinessResolution.AwaitingEndpoint.class))
                .hasSize(1);
        assertThat(vectors.stream().filter(vector -> vector.expectedType()
                == SectorReturnReadinessResolution.EvidenceUnavailable.class))
                .hasSize(95);
        assertThat(vectors.stream().filter(vector -> vector.source()
                instanceof SectorReturnResult.AssignmentUnavailable))
                .hasSize(36);
        assertThat(vectors.stream().filter(vector -> vector.source()
                instanceof SectorReturnResult.EndpointAnchorUnavailable))
                .hasSize(3);
        assertThat(vectors.stream().filter(vector -> vector.source()
                instanceof SectorReturnResult.EvidenceUnavailable))
                .hasSize(56);

        bytes[0] = (byte) '!';
        assertThat(policy().canonicalDefinitionUtf8()[0]).isEqualTo((byte) '{');
    }

    @Test
    void publicSurfaceImportFirewallAndDisconnectedFilesAreStable()
            throws Exception {
        Path packagePath = Path.of(
                "src/main/java/com/wallstreetreceipts/api/domain/outcome/sectorreturnreadiness");
        try (var files = Files.list(packagePath)) {
            assertThat(files.filter(path -> path.toString().endsWith(".java"))
                    .map(path -> path.getFileName().toString()).sorted().toList())
                    .containsExactly(
                            "SectorReturnReadinessPolicyVersion.java",
                            "SectorReturnReadinessRequest.java",
                            "SectorReturnReadinessResolution.java",
                            "SectorReturnReadinessResolver.java");
        }
        Path testPath = Path.of(
                "src/test/java/com/wallstreetreceipts/api/domain/outcome/sectorreturnreadiness");
        try (var files = Files.list(testPath)) {
            assertThat(files.filter(path -> path.toString().endsWith(".java"))
                    .map(path -> path.getFileName().toString()).toList())
                    .containsExactly("SectorReturnReadinessResolverGoldenTest.java");
        }
        assertRecordComponents(SectorReturnReadinessRequest.class,
                "policyVersion:SectorReturnReadinessPolicyVersion",
                "sourceResult:SectorReturnResult");
        assertRecordComponents(
                SectorReturnReadinessResolution.ResolutionContext.class,
                "policyVersion:SectorReturnReadinessPolicyVersion",
                "policyDefinitionHash:String");
        assertRecordComponents(SectorReturnReadinessResolution.Settled.class,
                "context:ResolutionContext", "sourceResult:SectorReturnResult");
        assertRecordComponents(
                SectorReturnReadinessResolution.AwaitingEndpoint.class,
                "context:ResolutionContext", "sourceResult:SectorReturnResult");
        assertRecordComponents(
                SectorReturnReadinessResolution.EvidenceUnavailable.class,
                "context:ResolutionContext", "sourceResult:SectorReturnResult");
        assertThat(SectorReturnReadinessResolution.class.isSealed()).isTrue();
        assertThat(Arrays.stream(SectorReturnReadinessResolution.class
                .getPermittedSubclasses()).map(Class::getSimpleName).toList())
                .containsExactlyInAnyOrder(
                        "Settled", "AwaitingEndpoint", "EvidenceUnavailable");
        assertThat(SectorReturnReadinessPolicyVersion.values())
                .containsExactly(policy());

        String request = Files.readString(packagePath.resolve(
                "SectorReturnReadinessRequest.java"));
        String resolution = Files.readString(packagePath.resolve(
                "SectorReturnReadinessResolution.java"));
        String resolver = Files.readString(packagePath.resolve(
                "SectorReturnReadinessResolver.java"));
        assertThat(request + resolution)
                .doesNotContain(".reason()")
                .doesNotContain("UnavailableReason")
                .doesNotContain("OutputUnavailableReason");
        assertThat(count(resolver, ".reason()")).isEqualTo(1);
        assertThat(resolver)
                .contains("ENDPOINT_NOT_REACHED_AS_OF")
                .contains("case SectorReturnResult.Available")
                .contains("case SectorReturnResult.NotApplicable");
        assertThat(request + resolution + resolver)
                .doesNotContain("Benchmark")
                .doesNotContain("benchmarkreturn")
                .doesNotContain("AssetReturn")
                .doesNotContain("DirectionalWin")
                .doesNotContain("OutcomeEvaluationStatus")
                .doesNotContain("CallOutcome")
                .doesNotContain("SectorReturnCalculator.calculate(")
                .doesNotContain("SectorReferenceLevelPairSelector")
                .doesNotContain("Clock")
                .doesNotContain("@Service")
                .doesNotContain("@Repository")
                .doesNotContain("@Controller");
    }

    @Test
    void nullRootsAndPolicyValidationFailClosed() {
        var source = availableSource();
        var context = context();

        assertThatThrownBy(() -> SectorReturnReadinessResolver.resolve(null))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new SectorReturnReadinessRequest(null, source))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new SectorReturnReadinessRequest(policy(), null))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new SectorReturnReadinessResolution
                .ResolutionContext(null, POLICY_HASH))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new SectorReturnReadinessResolution
                .ResolutionContext(policy(), null))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new SectorReturnReadinessResolution
                .ResolutionContext(policy(), "0".repeat(64)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new SectorReturnResult.CalculationContext(
                sectorPolicy(), "0".repeat(64), resolvedPair()))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new SectorReturnReadinessResolution
                .Settled(null, source))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new SectorReturnReadinessResolution
                .Settled(context, null))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> SectorReturnReadinessResolver
                .requireClassification(source, null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void directResultConstructorsShareExactFailClosedClassification() {
        var settled = availableSource();
        var awaiting = evidenceUnavailableSource(
                SectorReferenceLevelPairResolution.UnavailableReason
                        .ENDPOINT_NOT_REACHED_AS_OF);
        var unavailable = assignmentUnavailableSource(
                SectorAssignmentResolution.UnavailableReason
                        .CLASSIFICATION_MISSING_AS_OF);
        var context = context();

        assertThat(new SectorReturnReadinessResolution.Settled(
                context, settled).sourceResult()).isSameAs(settled);
        assertThat(new SectorReturnReadinessResolution.AwaitingEndpoint(
                context, awaiting).sourceResult()).isSameAs(awaiting);
        assertThat(new SectorReturnReadinessResolution.EvidenceUnavailable(
                context, unavailable).sourceResult()).isSameAs(unavailable);
        assertWrongVariant(() -> new SectorReturnReadinessResolution
                .AwaitingEndpoint(context, settled));
        assertWrongVariant(() -> new SectorReturnReadinessResolution
                .EvidenceUnavailable(context, settled));
        assertWrongVariant(() -> new SectorReturnReadinessResolution
                .Settled(context, awaiting));
        assertWrongVariant(() -> new SectorReturnReadinessResolution
                .EvidenceUnavailable(context, awaiting));
        assertWrongVariant(() -> new SectorReturnReadinessResolution
                .Settled(context, unavailable));
        assertWrongVariant(() -> new SectorReturnReadinessResolution
                .AwaitingEndpoint(context, unavailable));
    }

    @Test
    void equalButDistinctWholeSourceRecordsReplayEquallyAndPreserveIdentity() {
        var firstSource = evidenceUnavailableSource(
                SectorReferenceLevelPairResolution.UnavailableReason
                        .ENDPOINT_NOT_REACHED_AS_OF);
        var secondSource = evidenceUnavailableSource(
                SectorReferenceLevelPairResolution.UnavailableReason
                        .ENDPOINT_NOT_REACHED_AS_OF);

        assertThat(secondSource).isEqualTo(firstSource).isNotSameAs(firstSource);
        var first = SectorReturnReadinessResolver.resolve(request(firstSource));
        var second = SectorReturnReadinessResolver.resolve(request(secondSource));

        assertThat(second).isEqualTo(first).isNotSameAs(first);
        assertThat(sourceOf(first)).isSameAs(firstSource);
        assertThat(sourceOf(second)).isSameAs(secondSource);
    }

    @Test
    void classificationIsIndependentOfLocaleTimezoneAndPriorCalls() {
        var source = outputUnavailableSource();
        Locale originalLocale = Locale.getDefault();
        TimeZone originalTimeZone = TimeZone.getDefault();

        try {
            Locale.setDefault(Locale.JAPAN);
            TimeZone.setDefault(TimeZone.getTimeZone("Asia/Tokyo"));
            var first = SectorReturnReadinessResolver.resolve(request(source));
            SectorReturnReadinessResolver.resolve(request(availableSource()));

            Locale.setDefault(Locale.GERMANY);
            TimeZone.setDefault(TimeZone.getTimeZone("America/New_York"));
            var second = SectorReturnReadinessResolver.resolve(request(source));

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
    void everySectorReturnSourceShapeIsClassified(
            String label,
            SectorReturnResult source,
            Class<? extends SectorReturnReadinessResolution> expectedType) {
        var result = SectorReturnReadinessResolver.resolve(request(source));

        assertThat(result).as(label).isExactlyInstanceOf(expectedType);
        assertThat(sourceOf(result)).isSameAs(source);
        assertThat(contextOf(result).policyVersion()).isSameAs(policy());
        assertThat(contextOf(result).policyDefinitionHash())
                .isEqualTo(POLICY_HASH);
        if (expectedType
                == SectorReturnReadinessResolution.AwaitingEndpoint.class) {
            assertExactAwaitingChain(source);
        }
    }

    static Stream<Arguments> classificationSourceShapes() {
        return classificationVectors().stream().map(vector -> Arguments.of(
                vector.label(), vector.source(), vector.expectedType()));
    }

    private static List<ClassificationVector> classificationVectors() {
        List<ClassificationVector> vectors = new ArrayList<>();
        vectors.add(new ClassificationVector(
                "available", availableSource(),
                SectorReturnReadinessResolution.Settled.class));
        vectors.add(new ClassificationVector(
                "not-applicable-NON_EQUITY", notApplicableSource(),
                SectorReturnReadinessResolution.Settled.class));
        for (SectorAssignmentResolution.UnavailableReason reason
                : SectorAssignmentResolution.UnavailableReason.values()) {
            vectors.add(new ClassificationVector(
                    "assignment-unavailable-" + reason.name(),
                    assignmentUnavailableSource(reason),
                    SectorReturnReadinessResolution.EvidenceUnavailable.class));
        }
        for (SectorReferenceLevelPairResolution.EndpointAnchorUnavailableReason reason
                : SectorReferenceLevelPairResolution
                        .EndpointAnchorUnavailableReason.values()) {
            vectors.add(new ClassificationVector(
                    "endpoint-anchor-unavailable-" + reason.name(),
                    endpointAnchorUnavailableSource(reason),
                    SectorReturnReadinessResolution.EvidenceUnavailable.class));
        }
        for (SectorReferenceLevelPairResolution.UnavailableReason reason
                : SectorReferenceLevelPairResolution.UnavailableReason.values()) {
            vectors.add(new ClassificationVector(
                    "evidence-unavailable-" + reason.name(),
                    evidenceUnavailableSource(reason),
                    reason == SectorReferenceLevelPairResolution
                            .UnavailableReason.ENDPOINT_NOT_REACHED_AS_OF
                                    ? SectorReturnReadinessResolution
                                            .AwaitingEndpoint.class
                                    : SectorReturnReadinessResolution
                                            .EvidenceUnavailable.class));
        }
        vectors.add(new ClassificationVector(
                "output-unavailable-OUTPUT_NOT_REPRESENTABLE",
                outputUnavailableSource(),
                SectorReturnReadinessResolution.EvidenceUnavailable.class));
        return List.copyOf(vectors);
    }

    private static SectorReturnReadinessPolicyVersion policy() {
        return SectorReturnReadinessPolicyVersion
                .SUPPLIED_LEAF_SECTOR_RETURN_READINESS_V1;
    }

    private static SectorReturnPolicyVersion sectorPolicy() {
        return SectorReturnPolicyVersion
                .SIGNED_SECTOR_BASIS_LEVEL_DENOMINATOR_SCALE_12_HALF_EVEN_V1;
    }

    private static SectorReturnReadinessResolution.ResolutionContext context() {
        return new SectorReturnReadinessResolution.ResolutionContext(
                policy(), POLICY_HASH);
    }

    private static SectorReturnReadinessRequest request(
            SectorReturnResult source) {
        return new SectorReturnReadinessRequest(policy(), source);
    }

    private static SectorReturnResult.Available availableSource() {
        return new SectorReturnResult.Available(
                sourceContext(resolvedPair()), new BigDecimal("0.250000000000"));
    }

    private static SectorReturnResult.NotApplicable notApplicableSource() {
        return new SectorReturnResult.NotApplicable(
                sourceContext(new SectorReferenceLevelPairResolution
                        .NotApplicable(pairContext(
                                notApplicableAssignment(), resolvedEndpoint()))));
    }

    private static SectorReturnResult.AssignmentUnavailable
            assignmentUnavailableSource(
                    SectorAssignmentResolution.UnavailableReason reason) {
        return new SectorReturnResult.AssignmentUnavailable(sourceContext(
                new SectorReferenceLevelPairResolution.AssignmentUnavailable(
                        pairContext(unavailableAssignment(reason),
                                endpointUnavailable(
                                        EndpointPriceResolution.UnavailableReason
                                                .ENDPOINT_NOT_REACHED_AS_OF,
                                        AWAITING_AS_OF,
                                        AnchorFault.NONE)))));
    }

    private static SectorReturnResult.EndpointAnchorUnavailable
            endpointAnchorUnavailableSource(
                    SectorReferenceLevelPairResolution
                            .EndpointAnchorUnavailableReason reason) {
        return new SectorReturnResult.EndpointAnchorUnavailable(sourceContext(
                new SectorReferenceLevelPairResolution.EndpointAnchorUnavailable(
                        pairContext(resolvedAssignment(AWAITING_AS_OF),
                                endpointUnavailable(endpointReason(reason),
                                        AWAITING_AS_OF, anchorFault(reason))),
                        reason)));
    }

    private static SectorReturnResult.EvidenceUnavailable
            evidenceUnavailableSource(
                    SectorReferenceLevelPairResolution.UnavailableReason reason) {
        Instant asOf = reason == SectorReferenceLevelPairResolution
                .UnavailableReason.ENDPOINT_NOT_REACHED_AS_OF
                        ? AWAITING_AS_OF : REACHED_AS_OF;
        EndpointPriceResolution.UnavailableReason endpointReason =
                reason == SectorReferenceLevelPairResolution.UnavailableReason
                        .ENDPOINT_NOT_REACHED_AS_OF
                                ? EndpointPriceResolution.UnavailableReason
                                        .ENDPOINT_NOT_REACHED_AS_OF
                                : EndpointPriceResolution.UnavailableReason
                                        .OBSERVATION_MISSING_AS_OF;
        return new SectorReturnResult.EvidenceUnavailable(sourceContext(
                new SectorReferenceLevelPairResolution.EvidenceUnavailable(
                        pairContext(resolvedAssignment(asOf),
                                endpointUnavailable(
                                        endpointReason, asOf, AnchorFault.NONE)),
                        reason)));
    }

    private static SectorReturnResult.OutputUnavailable outputUnavailableSource() {
        return new SectorReturnResult.OutputUnavailable(
                sourceContext(resolvedPair()),
                SectorReturnResult.OutputUnavailableReason
                        .OUTPUT_NOT_REPRESENTABLE);
    }

    private static SectorReturnResult.CalculationContext sourceContext(
            SectorReferenceLevelPairResolution pair) {
        return new SectorReturnResult.CalculationContext(
                sectorPolicy(), SOURCE_HASH, pair);
    }

    private static SectorReferenceLevelPairResolution.Resolved resolvedPair() {
        var endpoint = resolvedEndpoint();
        var context = pairContext(resolvedAssignment(REACHED_AS_OF), endpoint);
        var reference = referenceIndex();
        var basis = level(reference, "basis-level", BASIS_TIME,
                new BigDecimal("100.000000000000"));
        var endpointLevel = level(reference, "endpoint-level", ENDPOINT_CLOSE,
                new BigDecimal("125.000000000000"));
        var continuity = continuity(reference, basis, endpointLevel);
        return new SectorReferenceLevelPairResolution.Resolved(
                context, reference, basis, endpointLevel, continuity);
    }

    private static SectorReferenceLevelPairResolution.ResolutionContext
            pairContext(
                    SectorAssignmentResolution assignment,
                    EndpointPriceResolution endpoint) {
        return new SectorReferenceLevelPairResolution.ResolutionContext(
                SectorReferenceLevelPairPolicyVersion
                        .POINT_IN_TIME_EXACT_SECTOR_PRICE_INDEX_LEVEL_PAIR_V1,
                PAIR_HASH, assignment, endpoint);
    }

    private static SectorAssignmentResolution.Resolved resolvedAssignment(
            Instant evaluationAsOf) {
        var context = assignmentContext(evaluationAsOf);
        var classification = classification(AssetType.EQUITY);
        var membership = membership();
        var mapping = mapping();
        return new SectorAssignmentResolution.Resolved(
                context, classification, membership, mapping);
    }

    private static SectorAssignmentResolution.NotApplicable
            notApplicableAssignment() {
        return new SectorAssignmentResolution.NotApplicable(
                assignmentContext(REACHED_AS_OF), classification(AssetType.ETF),
                SectorAssignmentResolution.NotApplicableReason.NON_EQUITY);
    }

    private static SectorAssignmentResolution.Unavailable unavailableAssignment(
            SectorAssignmentResolution.UnavailableReason reason) {
        return new SectorAssignmentResolution.Unavailable(
                assignmentContext(AWAITING_AS_OF), reason);
    }

    private static SectorAssignmentResolution.ResolutionContext assignmentContext(
            Instant evaluationAsOf) {
        return new SectorAssignmentResolution.ResolutionContext(
                SectorAssignmentPolicyVersion
                        .POINT_IN_TIME_EXPLICIT_WSR_ECONOMIC_ACTIVITY_SECTOR_ASSIGNMENT_V1,
                ASSIGNMENT_HASH, ORIGINAL, ASSET_ID, evaluationAsOf,
                "mapping-set-wsr", "mapping-set-version-1", MAPPING_SET_HASH);
    }

    private static SectorAssetClassificationEvidence classification(
            AssetType assetType) {
        return new SectorAssetClassificationEvidence(
                "classification-evidence", "provider-event-classification",
                ORIGINAL, ASSET_ID, assetType, VENUE_ID, "US", USD,
                "source-classification", "classification-revision-1",
                "provenance-classification", assignmentInterval(),
                BASIS_TIME, BASIS_TIME);
    }

    private static SectorMembershipEvidence membership() {
        return new SectorMembershipEvidence(
                "membership-evidence", "provider-event-membership",
                ORIGINAL, ASSET_ID, AssetType.EQUITY, VENUE_ID, "US", USD,
                "provider-sector", "scheme-sector", "scheme-revision-1",
                "provider-node-digital", "Digital Systems",
                "source-membership", "membership-revision-1",
                "provenance-membership", assignmentInterval(),
                BASIS_TIME, BASIS_TIME);
    }

    private static SectorMappingEvidence mapping() {
        return new SectorMappingEvidence(
                "mapping-evidence", "provider-event-mapping",
                "POINT_IN_TIME_EXPLICIT_PROVIDER_NODE_TO_WSR_ECONOMIC_ACTIVITY_V1",
                MAPPING_POLICY_HASH, "mapping-set-wsr",
                "mapping-set-version-1", MAPPING_SET_HASH,
                "wsr-economic-activity", "1.0.0", TAXONOMY_HASH,
                "provider-sector", "scheme-sector", "scheme-revision-1",
                "provider-node-digital", "Digital Systems",
                new SectorMappingEvidence.Recorded(
                        "Companies primarily providing digital systems.",
                        "en-US"),
                new SectorMappingEvidence.Mapped(CANONICAL_NODE_ID),
                "source-mapping", "mapping-revision-1",
                "provenance-mapping", assignmentInterval(),
                BASIS_TIME, BASIS_TIME);
    }

    private static SectorAssetClassificationEvidence.EffectiveInterval
            assignmentInterval() {
        return new SectorAssetClassificationEvidence.EffectiveInterval(
                BASIS_TIME.minusSeconds(86_400),
                new SectorAssetClassificationEvidence.OpenEnded());
    }

    private static EndpointPriceResolution.Resolved resolvedEndpoint() {
        return new EndpointPriceResolution.Resolved(
                endpointContext(REACHED_AS_OF, AnchorFault.NONE),
                endpointObservation());
    }

    private static EndpointPriceResolution.Unavailable endpointUnavailable(
            EndpointPriceResolution.UnavailableReason reason,
            Instant evaluationAsOf,
            AnchorFault anchorFault) {
        return new EndpointPriceResolution.Unavailable(
                endpointContext(evaluationAsOf, anchorFault), reason);
    }

    private static EndpointPriceResolution.ResolutionContext endpointContext(
            Instant evaluationAsOf,
            AnchorFault anchorFault) {
        return new EndpointPriceResolution.ResolutionContext(
                EndpointPricePolicyVersion
                        .OFFICIAL_PRIMARY_VENUE_CLOSE_SPLIT_ADJUSTED_V1,
                ENDPOINT_HASH, horizonResolution(), catalog(anchorFault),
                binding(anchorFault), evaluationAsOf);
    }

    private static EndpointPriceObservation endpointObservation() {
        return new EndpointPriceObservation(
                "endpoint-observation", "provider-event-endpoint",
                ASSET_ID, VENUE_ID, USD, PRICE_SOURCE_ID,
                PRICE_SOURCE_REVISION, "provenance-endpoint",
                CALENDAR_ID, CATALOG_REVISION, "session-endpoint",
                EndpointPriceField.OFFICIAL_REGULAR_SESSION_CLOSE,
                REQUIRED_ADJUSTMENT,
                CorporateActionContinuity.SPLIT_REVERSE_SPLIT_CONTINUOUS,
                ENDPOINT_CLOSE, ENDPOINT_CLOSE, ENDPOINT_CLOSE,
                new BigDecimal("500.000000000000"));
    }

    private static CatalogPointInTimeEvidence catalog(AnchorFault fault) {
        String calendarId = fault == AnchorFault.CATALOG_MISMATCH
                ? "wrong-calendar" : CALENDAR_ID;
        String revision = fault == AnchorFault.CATALOG_MISMATCH
                ? "wrong-revision" : CATALOG_REVISION;
        Instant visibleAt = fault == AnchorFault.CATALOG_FUTURE
                ? REACHED_AS_OF.plusSeconds(60) : BASIS_TIME;
        return new CatalogPointInTimeEvidence(
                calendarId, revision, "source-calendar",
                "calendar-source-revision-1", visibleAt, visibleAt,
                "provenance-calendar");
    }

    private static EndpointPriceBinding binding(AnchorFault fault) {
        Instant visibleAt = fault == AnchorFault.BINDING_FUTURE
                ? REACHED_AS_OF.plusSeconds(60) : BASIS_TIME;
        return new EndpointPriceBinding(
                "binding-sector", "binding-revision-1",
                ASSET_ID, VENUE_ID, USD, PRICE_SOURCE_ID,
                PRICE_SOURCE_REVISION, visibleAt, visibleAt,
                "provenance-binding");
    }

    private static SessionCloseHorizonResolution.Resolved horizonResolution() {
        TradingSession endpoint = new TradingSession(
                "session-endpoint", ENDPOINT_OPEN, ENDPOINT_CLOSE);
        var context = new SessionCloseHorizonResolution.ResolutionContext(
                SessionCloseHorizonPolicyVersion
                        .STRICTLY_AFTER_BASIS_EVENT_SESSION_CLOSE_V1,
                HORIZON_HASH, ORIGINAL, OutcomeHorizon.D1, 1,
                CALENDAR_ID, CATALOG_REVISION);
        return new SessionCloseHorizonResolution.Resolved(
                new SessionCloseHorizonResolution.ResolvedSessionWindow(
                        context, List.of(endpoint), endpoint));
    }

    private static SectorReferenceIndexEvidence referenceIndex() {
        var mapping = mapping();
        return new SectorReferenceIndexEvidence(
                "reference-index-evidence", "provider-event-reference-index",
                mapping.mappingEvidenceId(), mapping.providerEventId(),
                "wsr-economic-activity", "1.0.0", TAXONOMY_HASH,
                CANONICAL_NODE_ID, REFERENCE_ASSET_ID, AssetType.INDEX,
                REFERENCE_PROVIDER_ID, REFERENCE_INDEX_ID,
                "Digital Systems Price Index", REFERENCE_INDEX_REVISION,
                SectorReferenceIndexEvidence.ReferenceIndexKind
                        .PROVIDER_PUBLISHED_PRICE_INDEX,
                USD, VENUE_ID, CALENDAR_ID, CATALOG_REVISION,
                "source-calendar", "calendar-source-revision-1",
                LEVEL_SOURCE_ID, LEVEL_SOURCE_REVISION,
                CONTINUITY_SOURCE_ID, CONTINUITY_SOURCE_REVISION,
                "source-reference-binding", "binding-revision-2",
                "provenance-reference-index", referenceInterval(),
                BASIS_TIME, BASIS_TIME);
    }

    private static SectorReferenceIndexEvidence.EffectiveInterval
            referenceInterval() {
        return new SectorReferenceIndexEvidence.EffectiveInterval(
                BASIS_TIME.minusSeconds(86_400),
                new SectorReferenceIndexEvidence.OpenEnded());
    }

    private static SectorReferenceLevelObservation level(
            SectorReferenceIndexEvidence reference,
            String id,
            Instant observedAt,
            BigDecimal level) {
        return new SectorReferenceLevelObservation(
                id, "provider-event-" + id,
                reference.referenceIndexEvidenceId(), reference.providerEventId(),
                reference.referenceAssetId(), reference.referenceAssetType(),
                reference.referenceProviderId(), reference.referenceIndexId(),
                reference.referenceIndexDefinitionRevision(),
                reference.referenceKind(), reference.currency(),
                reference.calculationVenueId(), reference.calendarId(),
                reference.calendarRevision(), reference.calendarSourceId(),
                reference.calendarSourceRevision(), reference.levelSourceId(),
                reference.levelSourceRevision(), "provenance-" + id,
                SectorReferenceLevelObservation.ReferenceLevelField
                        .PROVIDER_PUBLISHED_INDEX_LEVEL,
                observedAt, observedAt, observedAt, level);
    }

    private static SectorIndexDivisorContinuityEvidence continuity(
            SectorReferenceIndexEvidence reference,
            SectorReferenceLevelObservation basis,
            SectorReferenceLevelObservation endpoint) {
        return new SectorIndexDivisorContinuityEvidence(
                "divisor-continuity", "provider-event-continuity",
                reference.referenceIndexEvidenceId(), reference.providerEventId(),
                reference.referenceAssetId(), reference.referenceAssetType(),
                reference.referenceProviderId(), reference.referenceIndexId(),
                reference.referenceIndexDefinitionRevision(),
                reference.referenceKind(), reference.currency(),
                reference.calculationVenueId(), reference.calendarId(),
                reference.calendarRevision(), reference.calendarSourceId(),
                reference.calendarSourceRevision(),
                reference.continuitySourceId(),
                reference.continuitySourceRevision(),
                "provenance-continuity", basis.observationId(),
                basis.providerEventId(), endpoint.observationId(),
                endpoint.providerEventId(), BASIS_TIME, ENDPOINT_CLOSE,
                SectorIndexDivisorContinuityEvidence.DivisorContinuity
                        .PROVIDER_PUBLISHED_INDEX_DIVISOR_CONTINUITY_ATTESTED,
                ENDPOINT_CLOSE, ENDPOINT_CLOSE);
    }

    private static EndpointPriceResolution.UnavailableReason endpointReason(
            SectorReferenceLevelPairResolution
                    .EndpointAnchorUnavailableReason reason) {
        return switch (reason) {
            case CATALOG_NOT_KNOWN_AS_OF ->
                    EndpointPriceResolution.UnavailableReason
                            .CATALOG_NOT_KNOWN_AS_OF;
            case CATALOG_EVIDENCE_MISMATCH ->
                    EndpointPriceResolution.UnavailableReason
                            .CATALOG_EVIDENCE_MISMATCH;
            case BINDING_NOT_KNOWN_AS_OF ->
                    EndpointPriceResolution.UnavailableReason
                            .BINDING_NOT_KNOWN_AS_OF;
        };
    }

    private static AnchorFault anchorFault(
            SectorReferenceLevelPairResolution
                    .EndpointAnchorUnavailableReason reason) {
        return switch (reason) {
            case CATALOG_NOT_KNOWN_AS_OF -> AnchorFault.CATALOG_FUTURE;
            case CATALOG_EVIDENCE_MISMATCH -> AnchorFault.CATALOG_MISMATCH;
            case BINDING_NOT_KNOWN_AS_OF -> AnchorFault.BINDING_FUTURE;
        };
    }

    private static String sectorCanonicalJsonBlock(String adr) {
        int searchFrom = 0;
        while (true) {
            int fence = adr.indexOf("```json", searchFrom);
            if (fence < 0) {
                throw new IllegalStateException("sector JSON block not found");
            }
            int canonicalStart = adr.indexOf('\n', fence) + 1;
            int canonicalEnd = adr.indexOf("\n```", canonicalStart);
            String candidate = adr.substring(canonicalStart, canonicalEnd);
            if (candidate.contains(
                    "\"policyVersion\":\"SUPPLIED_LEAF_SECTOR_RETURN_READINESS_V1\"")) {
                return candidate;
            }
            searchFrom = canonicalEnd + 1;
        }
    }

    private static SectorReturnResult sourceOf(
            SectorReturnReadinessResolution resolution) {
        return switch (resolution) {
            case SectorReturnReadinessResolution.Settled settled ->
                    settled.sourceResult();
            case SectorReturnReadinessResolution.AwaitingEndpoint awaiting ->
                    awaiting.sourceResult();
            case SectorReturnReadinessResolution.EvidenceUnavailable unavailable ->
                    unavailable.sourceResult();
        };
    }

    private static SectorReturnReadinessResolution.ResolutionContext contextOf(
            SectorReturnReadinessResolution resolution) {
        return switch (resolution) {
            case SectorReturnReadinessResolution.Settled settled ->
                    settled.context();
            case SectorReturnReadinessResolution.AwaitingEndpoint awaiting ->
                    awaiting.context();
            case SectorReturnReadinessResolution.EvidenceUnavailable unavailable ->
                    unavailable.context();
        };
    }

    private static void assertExactAwaitingChain(SectorReturnResult source) {
        assertThat(source).isInstanceOf(SectorReturnResult.EvidenceUnavailable.class);
        var sourceUnavailable = (SectorReturnResult.EvidenceUnavailable) source;
        var pair = (SectorReferenceLevelPairResolution.EvidenceUnavailable)
                sourceUnavailable.context().referenceLevelPairResolution();
        assertThat(pair.reason()).isEqualTo(
                SectorReferenceLevelPairResolution.UnavailableReason
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

    private record ClassificationVector(
            String label,
            SectorReturnResult source,
            Class<? extends SectorReturnReadinessResolution> expectedType) {
    }

    private enum AnchorFault {
        NONE,
        CATALOG_FUTURE,
        CATALOG_MISMATCH,
        BINDING_FUTURE
    }
}
