package com.wallstreetreceipts.api.domain.outcome.sectorassignment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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
import java.util.stream.Stream;

import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import com.wallstreetreceipts.api.domain.master.AssetType;
import com.wallstreetreceipts.api.domain.outcome.horizon.OutcomeBasis;
import com.wallstreetreceipts.api.domain.outcome.horizon.OutcomeBasis.Correction;
import com.wallstreetreceipts.api.domain.outcome.horizon.OutcomeBasis.Original;
import com.wallstreetreceipts.api.domain.outcome.sectorassignment.SectorAssetClassificationEvidence.EffectiveInterval;
import com.wallstreetreceipts.api.domain.outcome.sectorassignment.SectorAssetClassificationEvidence.EndsAtExclusive;
import com.wallstreetreceipts.api.domain.outcome.sectorassignment.SectorAssetClassificationEvidence.OpenEnded;
import com.wallstreetreceipts.api.domain.outcome.sectorassignment.SectorAssignmentResolution.NotApplicable;
import com.wallstreetreceipts.api.domain.outcome.sectorassignment.SectorAssignmentResolution.NotApplicableReason;
import com.wallstreetreceipts.api.domain.outcome.sectorassignment.SectorAssignmentResolution.ResolutionContext;
import com.wallstreetreceipts.api.domain.outcome.sectorassignment.SectorAssignmentResolution.Resolved;
import com.wallstreetreceipts.api.domain.outcome.sectorassignment.SectorAssignmentResolution.Unavailable;
import com.wallstreetreceipts.api.domain.outcome.sectorassignment.SectorAssignmentResolution.UnavailableReason;
import com.wallstreetreceipts.api.domain.outcome.sectorassignment.SectorMappingEvidence.Mapped;
import com.wallstreetreceipts.api.domain.outcome.sectorassignment.SectorMappingEvidence.MappingDisposition;
import com.wallstreetreceipts.api.domain.outcome.sectorassignment.SectorMappingEvidence.NotMapped;
import com.wallstreetreceipts.api.domain.outcome.sectorassignment.SectorMappingEvidence.NotMappedReason;
import com.wallstreetreceipts.api.domain.outcome.sectorassignment.SectorMappingEvidence.NotPublished;
import com.wallstreetreceipts.api.domain.outcome.sectorassignment.SectorMappingEvidence.ProviderNodeDefinition;
import com.wallstreetreceipts.api.domain.outcome.sectorassignment.SectorMappingEvidence.Recorded;

class SectorAssignmentSelectorGoldenTest {

    private static final String ASSET_ID = "asset-nvda";
    private static final String PRIMARY_VENUE_ID = "venue-xnas";
    private static final String US = "US";
    private static final String CA = "CA";
    private static final Currency USD = Currency.getInstance("USD");
    private static final Currency EUR = Currency.getInstance("EUR");
    private static final String PROVIDER_ID = "provider-synthetic";
    private static final String PROVIDER_SCHEME_ID = "scheme-economic-activity";
    private static final String PROVIDER_SCHEME_REVISION = "revision-2026";
    private static final String PROVIDER_NODE_ID = "provider-node-computing";
    private static final String PROVIDER_NODE_LABEL = "Computing";
    private static final String MAPPING_SET_ID = "mapping-set-synthetic";
    private static final String MAPPING_SET_VERSION = "1.0.0";
    private static final String MAPPING_SET_HASH =
            "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa";
    private static final String POLICY_HASH =
            "52d9f705a3a8a965a6fca79d36bd94ed8836642f1a2c4e5f29a878d0a267311c";
    private static final String MAPPING_POLICY_HASH =
            "ba12a277d5ffe266af1745b98948a1e2206494ac31904f31a419d973d5067e77";
    private static final String TAXONOMY_HASH =
            "820ce3ea264d67312fe4f2efe346631a81d74248e9a7f041793d65d8ef0d62ae";
    private static final String TAXONOMY_ID = "wsr-economic-activity";
    private static final String TAXONOMY_VERSION = "1.0.0";
    private static final String MAPPING_POLICY_VERSION =
            "POINT_IN_TIME_EXPLICIT_PROVIDER_NODE_TO_WSR_ECONOMIC_ACTIVITY_V1";
    private static final String DIGITAL_SYSTEMS = "wsr-sector-digital-systems";
    private static final List<String> CANONICAL_LEAVES = List.of(
            DIGITAL_SYSTEMS,
            "wsr-sector-connectivity-media",
            "wsr-sector-health-bioscience",
            "wsr-sector-financial-risk-services",
            "wsr-sector-consumer-essentials",
            "wsr-sector-consumer-choice-commerce",
            "wsr-sector-production-mobility",
            "wsr-sector-energy-systems",
            "wsr-sector-materials-resource-processing",
            "wsr-sector-essential-networks",
            "wsr-sector-property-built-environment",
            "wsr-sector-diversified-operations");
    private static final Instant BASIS_TIME = Instant.parse("2026-08-20T14:00:00Z");
    private static final Instant AS_OF = Instant.parse("2026-08-20T14:01:00Z");
    private static final Instant INTERVAL_START = Instant.parse("2026-01-01T00:00:00Z");
    private static final Instant INTERVAL_END = Instant.parse("2027-01-01T00:00:00Z");
    private static final OutcomeBasis ORIGINAL = new Original("call-001", BASIS_TIME);

    @Test
    void canonicalPolicyDefinitionHasExactUtf8BytesIndependentHashAndDefensiveReads()
            throws NoSuchAlgorithmException {
        String definition = policy().canonicalDefinition();
        byte[] first = policy().canonicalDefinitionUtf8();
        String independentHash = HexFormat.of().formatHex(
                MessageDigest.getInstance("SHA-256").digest(
                        definition.getBytes(StandardCharsets.UTF_8)));

        assertThat(definition).isNotBlank().startsWith("{").endsWith("}")
                .doesNotContain("\n", "\r");
        assertThat(definition.chars().allMatch(value -> value < 128)).isTrue();
        assertThat(first).hasSize(9307)
                .containsExactly(definition.getBytes(StandardCharsets.UTF_8));
        assertThat(independentHash).isEqualTo(POLICY_HASH);
        assertThat(policy().definitionHash()).isEqualTo(POLICY_HASH);
        assertThat(definition).contains(
                "\"requiredTaxonomyDefinitionHash\":\"" + TAXONOMY_HASH + "\"",
                "\"requiredMappingPolicyDefinitionHash\":\"" + MAPPING_POLICY_HASH
                        + "\"");
        CANONICAL_LEAVES.forEach(leaf -> assertThat(definition).contains(leaf));
        first[0] = (byte) '!';
        assertThat(policy().canonicalDefinitionUtf8()[0]).isEqualTo((byte) '{');
    }

    @Test
    void publicPolicyEvidenceRequestAndResultSurfacesRemainExactlyClosed() {
        assertThat(SectorAssignmentPolicyVersion.values()).containsExactly(policy());
        assertThat(AssetType.values()).containsExactly(
                AssetType.INDEX, AssetType.EQUITY, AssetType.ETF,
                AssetType.BOND, AssetType.COMMODITY, AssetType.FX);
        assertThat(NotMappedReason.values()).containsExactly(
                NotMappedReason.NO_CANONICAL_EQUIVALENT,
                NotMappedReason.PROVIDER_NODE_TOO_BROAD,
                NotMappedReason.PROVIDER_DEFINITION_UNAVAILABLE);
        assertThat(NotApplicableReason.values()).containsExactly(
                NotApplicableReason.NON_EQUITY);
        assertThat(UnavailableReason.values()).containsExactly(
                UnavailableReason.CLASSIFICATION_MISSING_AS_OF,
                UnavailableReason.CLASSIFICATION_BASIS_MISMATCH,
                UnavailableReason.CLASSIFICATION_ASSET_MISMATCH,
                UnavailableReason.CLASSIFICATION_EFFECTIVE_INTERVAL_MISMATCH,
                UnavailableReason.CLASSIFICATION_AMBIGUOUS,
                UnavailableReason.MEMBERSHIP_MISSING_AS_OF,
                UnavailableReason.MEMBERSHIP_BASIS_MISMATCH,
                UnavailableReason.MEMBERSHIP_ASSET_MISMATCH,
                UnavailableReason.MEMBERSHIP_ASSET_TYPE_MISMATCH,
                UnavailableReason.MEMBERSHIP_PRIMARY_VENUE_MISMATCH,
                UnavailableReason.MEMBERSHIP_PRIMARY_VENUE_COUNTRY_MISMATCH,
                UnavailableReason.MEMBERSHIP_CURRENCY_MISMATCH,
                UnavailableReason.MEMBERSHIP_EFFECTIVE_INTERVAL_MISMATCH,
                UnavailableReason.OUT_OF_SCOPE_MEMBERSHIP_CONFLICT,
                UnavailableReason.MEMBERSHIP_AMBIGUOUS,
                UnavailableReason.MAPPING_MISSING_AS_OF,
                UnavailableReason.MAPPING_SET_ID_MISMATCH,
                UnavailableReason.MAPPING_SET_VERSION_MISMATCH,
                UnavailableReason.MAPPING_SET_DEFINITION_HASH_MISMATCH,
                UnavailableReason.MAPPING_POLICY_VERSION_MISMATCH,
                UnavailableReason.MAPPING_POLICY_DEFINITION_HASH_MISMATCH,
                UnavailableReason.MAPPING_TAXONOMY_ID_MISMATCH,
                UnavailableReason.MAPPING_TAXONOMY_VERSION_MISMATCH,
                UnavailableReason.MAPPING_TAXONOMY_DEFINITION_HASH_MISMATCH,
                UnavailableReason.MAPPING_PROVIDER_ID_MISMATCH,
                UnavailableReason.MAPPING_PROVIDER_SCHEME_ID_MISMATCH,
                UnavailableReason.MAPPING_PROVIDER_SCHEME_REVISION_MISMATCH,
                UnavailableReason.MAPPING_PROVIDER_NODE_ID_MISMATCH,
                UnavailableReason.MAPPING_EFFECTIVE_INTERVAL_MISMATCH,
                UnavailableReason.MAPPING_MAPPED_DEFINITION_REQUIRED,
                UnavailableReason.MAPPING_CANONICAL_NODE_NOT_ASSIGNABLE,
                UnavailableReason.MAPPING_CONFLICT,
                UnavailableReason.MAPPING_AMBIGUOUS,
                UnavailableReason.MAPPING_NOT_MAPPED_NO_CANONICAL_EQUIVALENT,
                UnavailableReason.MAPPING_NOT_MAPPED_PROVIDER_NODE_TOO_BROAD,
                UnavailableReason.MAPPING_NOT_MAPPED_PROVIDER_DEFINITION_UNAVAILABLE);
        assertThat(SectorAssignmentResolution.class.getPermittedSubclasses())
                .containsExactlyInAnyOrder(Resolved.class, NotApplicable.class,
                        Unavailable.class);
        assertThat(SectorAssetClassificationEvidence.EffectiveIntervalEnd.class
                .getPermittedSubclasses())
                .containsExactlyInAnyOrder(OpenEnded.class, EndsAtExclusive.class);
        assertThat(ProviderNodeDefinition.class.getPermittedSubclasses())
                .containsExactlyInAnyOrder(Recorded.class, NotPublished.class);
        assertThat(MappingDisposition.class.getPermittedSubclasses())
                .containsExactlyInAnyOrder(Mapped.class, NotMapped.class);
        assertRecordComponents(SectorAssetClassificationEvidence.class,
                "classificationEvidenceId:String", "providerEventId:String",
                "basis:OutcomeBasis", "assetId:String", "assetType:AssetType",
                "primaryVenueId:String", "primaryVenueCountryCode:String",
                "currency:Currency", "classificationSourceId:String",
                "classificationSourceRevision:String", "provenanceId:String",
                "effectiveInterval:EffectiveInterval", "availableAt:Instant",
                "capturedAt:Instant");
        assertRecordComponents(SectorMembershipEvidence.class,
                "membershipEvidenceId:String", "providerEventId:String",
                "basis:OutcomeBasis", "assetId:String", "assetType:AssetType",
                "primaryVenueId:String", "primaryVenueCountryCode:String",
                "currency:Currency", "providerId:String", "providerSchemeId:String",
                "providerSchemeRevision:String", "providerNodeId:String",
                "providerNodeLabel:String", "membershipSourceId:String",
                "membershipSourceRevision:String", "provenanceId:String",
                "effectiveInterval:EffectiveInterval", "availableAt:Instant",
                "capturedAt:Instant");
        assertRecordComponents(SectorMappingEvidence.class,
                "mappingEvidenceId:String", "providerEventId:String",
                "mappingPolicyVersion:String", "mappingPolicyDefinitionHash:String",
                "mappingSetId:String", "mappingSetVersion:String",
                "mappingSetDefinitionHash:String", "taxonomyId:String",
                "taxonomyVersion:String", "taxonomyDefinitionHash:String",
                "providerId:String", "providerSchemeId:String",
                "providerSchemeRevision:String", "providerNodeId:String",
                "providerNodeLabel:String",
                "providerNodeDefinition:ProviderNodeDefinition",
                "mappingDisposition:MappingDisposition", "mappingSourceId:String",
                "mappingSourceRevision:String", "provenanceId:String",
                "effectiveInterval:EffectiveInterval", "availableAt:Instant",
                "capturedAt:Instant");
        assertRecordComponents(SectorAssignmentRequest.class,
                "policyVersion:SectorAssignmentPolicyVersion", "basis:OutcomeBasis",
                "assetId:String", "evaluationAsOf:Instant", "mappingSetId:String",
                "mappingSetVersion:String", "mappingSetDefinitionHash:String",
                "classificationCandidates:List", "membershipCandidates:List",
                "mappingCandidates:List");
        assertRecordComponents(ResolutionContext.class,
                "policyVersion:SectorAssignmentPolicyVersion",
                "policyDefinitionHash:String", "basis:OutcomeBasis", "assetId:String",
                "evaluationAsOf:Instant", "mappingSetId:String",
                "mappingSetVersion:String", "mappingSetDefinitionHash:String");
        assertRecordComponents(Resolved.class, "context:ResolutionContext",
                "classificationEvidence:SectorAssetClassificationEvidence",
                "membershipEvidence:SectorMembershipEvidence",
                "mappingEvidence:SectorMappingEvidence");
        assertRecordComponents(NotApplicable.class, "context:ResolutionContext",
                "classificationEvidence:SectorAssetClassificationEvidence",
                "reason:NotApplicableReason");
        assertRecordComponents(Unavailable.class, "context:ResolutionContext",
                "reason:UnavailableReason");
    }

    @ParameterizedTest(name = "canonical leaf {0}")
    @MethodSource("canonicalLeaves")
    void resolvesEveryClosedCanonicalLeaf(String canonicalNodeId) {
        SectorAssetClassificationEvidence classification = exactClassification(
                ORIGINAL, "classification-" + canonicalNodeId);
        SectorMembershipEvidence membership = exactMembership(
                classification, "membership-" + canonicalNodeId);
        SectorMappingEvidence mapping = exactMapping(
                membership, canonicalNodeId, "mapping-" + canonicalNodeId);

        Resolved result = assertResolved(select(
                ORIGINAL, List.of(classification), List.of(membership),
                List.of(mapping)));
        assertThat(((Mapped) result.mappingEvidence().mappingDisposition())
                .canonicalNodeId()).isEqualTo(canonicalNodeId);
    }

    static Stream<String> canonicalLeaves() {
        return CANONICAL_LEAVES.stream();
    }

    @Test
    void resolvesOneExactOriginalAssignmentAtInclusivePitAndIntervalBoundaries() {
        SectorAssetClassificationEvidence classification = classification(
                ORIGINAL, ASSET_ID, AssetType.EQUITY, PRIMARY_VENUE_ID, US, USD,
                bounded(BASIS_TIME, INTERVAL_END), AS_OF, AS_OF, "exact");
        SectorMembershipEvidence membership = membership(
                classification, ORIGINAL, ASSET_ID, AssetType.EQUITY,
                PRIMARY_VENUE_ID, US, USD, PROVIDER_ID, PROVIDER_SCHEME_ID,
                PROVIDER_SCHEME_REVISION, PROVIDER_NODE_ID, PROVIDER_NODE_LABEL,
                bounded(BASIS_TIME, INTERVAL_END), AS_OF, AS_OF, "exact");
        SectorMappingEvidence mapping = mapping(
                membership, bounded(BASIS_TIME, INTERVAL_END),
                new Recorded("Computing definition", "en"),
                new Mapped(DIGITAL_SYSTEMS), AS_OF, AS_OF, "exact");

        Resolved result = assertResolved(select(
                ORIGINAL, List.of(classification), List.of(membership),
                List.of(mapping)));
        assertThat(result.context()).isEqualTo(context(ORIGINAL));
        assertThat(result.classificationEvidence()).isSameAs(classification);
        assertThat(result.membershipEvidence()).isSameAs(membership);
        assertThat(result.mappingEvidence()).isSameAs(mapping);

        Currency cad = Currency.getInstance("CAD");
        SectorAssetClassificationEvidence nonUsNonUsdClassification = classification(
                ORIGINAL, ASSET_ID, AssetType.EQUITY, PRIMARY_VENUE_ID, CA, cad,
                exactInterval(), AS_OF, AS_OF, "non-us-non-usd");
        SectorMembershipEvidence nonUsNonUsdMembership = membership(
                nonUsNonUsdClassification, ORIGINAL, ASSET_ID, AssetType.EQUITY,
                PRIMARY_VENUE_ID, CA, cad, PROVIDER_ID, PROVIDER_SCHEME_ID,
                PROVIDER_SCHEME_REVISION, PROVIDER_NODE_ID, PROVIDER_NODE_LABEL,
                exactInterval(), AS_OF, AS_OF, "non-us-non-usd");
        SectorMappingEvidence nonUsNonUsdMapping = exactMapping(
                nonUsNonUsdMembership, DIGITAL_SYSTEMS, "non-us-non-usd");
        Resolved nonUsNonUsdResult = assertResolved(select(
                ORIGINAL, List.of(nonUsNonUsdClassification),
                List.of(nonUsNonUsdMembership), List.of(nonUsNonUsdMapping)));
        assertThat(nonUsNonUsdResult.classificationEvidence()
                .primaryVenueCountryCode()).isEqualTo(CA);
        assertThat(nonUsNonUsdResult.classificationEvidence().currency())
                .isEqualTo(cad);
    }

    @Test
    void correctionBasisIsAnIndependentCompleteIdentityNotAnEventTimeShortcut() {
        OutcomeBasis correction = new Correction("call-001", "correction-2", BASIS_TIME);
        SectorAssetClassificationEvidence classification = exactClassification(
                correction, "correction");
        SectorMembershipEvidence membership = exactMembership(
                classification, "correction");
        SectorMappingEvidence mapping = exactMapping(
                membership, DIGITAL_SYSTEMS, "correction");
        assertResolved(select(correction, List.of(classification), List.of(membership),
                List.of(mapping)));

        SectorAssetClassificationEvidence originalAtSameTime = exactClassification(
                ORIGINAL, "original");
        assertUnavailable(select(correction, List.of(originalAtSameTime), List.of(),
                List.of()), UnavailableReason.CLASSIFICATION_BASIS_MISMATCH);
    }

    @ParameterizedTest(name = "effective interval boundary {0}")
    @MethodSource("effectiveIntervalBoundaryVectors")
    void effectiveIntervalsAreStartInclusiveEndExclusiveAndExplicitlyOpenEnded(
            String scenario,
            EffectiveInterval classificationInterval,
            EffectiveInterval membershipInterval,
            EffectiveInterval mappingInterval,
            UnavailableReason expectedReason) {
        SectorAssetClassificationEvidence classification = classification(
                ORIGINAL, ASSET_ID, AssetType.EQUITY, PRIMARY_VENUE_ID, US, USD,
                classificationInterval, AS_OF, AS_OF, scenario);
        SectorMembershipEvidence membership = membership(
                classification, ORIGINAL, ASSET_ID, AssetType.EQUITY,
                PRIMARY_VENUE_ID, US, USD, PROVIDER_ID, PROVIDER_SCHEME_ID,
                PROVIDER_SCHEME_REVISION, PROVIDER_NODE_ID, PROVIDER_NODE_LABEL,
                membershipInterval, AS_OF, AS_OF, scenario);
        SectorMappingEvidence mapping = mapping(
                membership, mappingInterval, recorded(), new Mapped(DIGITAL_SYSTEMS),
                AS_OF, AS_OF, scenario);

        SectorAssignmentResolution result = select(
                ORIGINAL, List.of(classification), List.of(membership),
                List.of(mapping));
        if (expectedReason == null) {
            assertResolved(result);
        } else {
            assertUnavailable(result, expectedReason);
        }
    }

    static Stream<Arguments> effectiveIntervalBoundaryVectors() {
        return Stream.of(
                Arguments.of("bounded start equality",
                        bounded(BASIS_TIME, INTERVAL_END),
                        bounded(BASIS_TIME, INTERVAL_END),
                        bounded(BASIS_TIME, INTERVAL_END), null),
                Arguments.of("bounded interior", exactInterval(), exactInterval(),
                        exactInterval(), null),
                Arguments.of("classification end equality",
                        bounded(INTERVAL_START, BASIS_TIME), exactInterval(),
                        exactInterval(),
                        UnavailableReason.CLASSIFICATION_EFFECTIVE_INTERVAL_MISMATCH),
                Arguments.of("membership end equality", exactInterval(),
                        bounded(INTERVAL_START, BASIS_TIME), exactInterval(),
                        UnavailableReason.MEMBERSHIP_EFFECTIVE_INTERVAL_MISMATCH),
                Arguments.of("mapping end equality", exactInterval(), exactInterval(),
                        bounded(INTERVAL_START, BASIS_TIME),
                        UnavailableReason.MAPPING_EFFECTIVE_INTERVAL_MISMATCH),
                Arguments.of("open start equality", open(BASIS_TIME),
                        open(BASIS_TIME), open(BASIS_TIME), null),
                Arguments.of("open interior", open(INTERVAL_START),
                        open(INTERVAL_START), open(INTERVAL_START), null),
                Arguments.of("mixed explicit ends", open(INTERVAL_START),
                        exactInterval(), open(INTERVAL_START), null));
    }

    @Test
    void futureClassificationCandidatesAreIdenticalToAbsentAndInvisibleToReasoning() {
        SectorAssetClassificationEvidence classification = exactClassification(
                ORIGINAL, "exact");
        SectorMembershipEvidence membership = exactMembership(classification, "exact");
        SectorMappingEvidence mapping = exactMapping(
                membership, DIGITAL_SYSTEMS, "exact");
        SectorAssetClassificationEvidence futureWrong = classification(
                new Correction("wrong", "wrong", BASIS_TIME), "wrong-asset",
                AssetType.INDEX, "wrong-venue", CA, EUR, exactInterval(),
                AS_OF.plusSeconds(1), AS_OF.plusSeconds(1), "future");
        SectorAssetClassificationEvidence capturedAfterCutoff = classification(
                new Correction("wrong", "wrong", BASIS_TIME), "wrong-asset",
                AssetType.INDEX, "wrong-venue", CA, EUR, exactInterval(), AS_OF,
                AS_OF.plusSeconds(1), "captured-after-cutoff");

        assertThat(select(ORIGINAL,
                List.of(classification, futureWrong, capturedAfterCutoff),
                List.of(membership), List.of(mapping)))
                .isEqualTo(select(ORIGINAL, List.of(classification),
                        List.of(membership), List.of(mapping)));
        assertUnavailable(select(ORIGINAL, List.of(futureWrong), List.of(), List.of()),
                UnavailableReason.CLASSIFICATION_MISSING_AS_OF);
        assertUnavailable(select(ORIGINAL, List.of(capturedAfterCutoff),
                List.of(), List.of()), UnavailableReason.CLASSIFICATION_MISSING_AS_OF);
        assertThatThrownBy(() -> classification(
                ORIGINAL, ASSET_ID, AssetType.EQUITY, PRIMARY_VENUE_ID, US, USD,
                exactInterval(), AS_OF.plusSeconds(1), AS_OF,
                "available-after-capture"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void futureMembershipCandidatesAreIdenticalToAbsentAndInvisibleToReasoning() {
        SectorAssetClassificationEvidence classification = exactClassification(
                ORIGINAL, "exact");
        SectorMembershipEvidence membership = exactMembership(classification, "exact");
        SectorMappingEvidence mapping = exactMapping(
                membership, DIGITAL_SYSTEMS, "exact");
        SectorMembershipEvidence futureWrong = membership(
                classification, new Correction("wrong", "wrong", BASIS_TIME),
                "wrong-asset", AssetType.INDEX, "wrong-venue", CA, EUR,
                "wrong-provider", "wrong-scheme", "wrong-revision", "wrong-node",
                "Wrong", exactInterval(), AS_OF.plusSeconds(1),
                AS_OF.plusSeconds(1), "future");
        SectorMembershipEvidence capturedAfterCutoff = membership(
                classification, new Correction("wrong", "wrong", BASIS_TIME),
                "wrong-asset", AssetType.INDEX, "wrong-venue", CA, EUR,
                "wrong-provider", "wrong-scheme", "wrong-revision", "wrong-node",
                "Wrong", exactInterval(), AS_OF, AS_OF.plusSeconds(1),
                "captured-after-cutoff");

        assertThat(select(ORIGINAL, List.of(classification),
                List.of(membership, futureWrong, capturedAfterCutoff),
                List.of(mapping)))
                .isEqualTo(select(ORIGINAL, List.of(classification),
                        List.of(membership), List.of(mapping)));
        assertUnavailable(select(ORIGINAL, List.of(classification),
                List.of(futureWrong), List.of()),
                UnavailableReason.MEMBERSHIP_MISSING_AS_OF);
        assertUnavailable(select(ORIGINAL, List.of(classification),
                List.of(capturedAfterCutoff), List.of()),
                UnavailableReason.MEMBERSHIP_MISSING_AS_OF);
        assertThatThrownBy(() -> membership(
                classification, ORIGINAL, ASSET_ID, AssetType.EQUITY,
                PRIMARY_VENUE_ID, US, USD, PROVIDER_ID, PROVIDER_SCHEME_ID,
                PROVIDER_SCHEME_REVISION, PROVIDER_NODE_ID, PROVIDER_NODE_LABEL,
                exactInterval(), AS_OF.plusSeconds(1), AS_OF,
                "available-after-capture"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void futureMappingCandidatesAreIdenticalToAbsentAndInvisibleToReasoning() {
        SectorAssetClassificationEvidence classification = exactClassification(
                ORIGINAL, "exact");
        SectorMembershipEvidence membership = exactMembership(classification, "exact");
        SectorMappingEvidence mapping = exactMapping(
                membership, DIGITAL_SYSTEMS, "exact");
        SectorMappingEvidence futureWrong = mappingWith(
                membership, "wrong-set", "wrong-version", "b".repeat(64),
                "wrong-policy", "c".repeat(64), "wrong-taxonomy", "2.0.0",
                "d".repeat(64), "wrong-provider", "wrong-scheme", "wrong-revision",
                "wrong-node", "Wrong", exactInterval(), new NotPublished(),
                new Mapped("wsr-sector-root"), AS_OF.plusSeconds(1),
                AS_OF.plusSeconds(1), "future");
        SectorMappingEvidence capturedAfterCutoff = mappingWith(
                membership, "wrong-set", "wrong-version", "b".repeat(64),
                "wrong-policy", "c".repeat(64), "wrong-taxonomy", "2.0.0",
                "d".repeat(64), "wrong-provider", "wrong-scheme", "wrong-revision",
                "wrong-node", "Wrong", exactInterval(), new NotPublished(),
                new Mapped("wsr-sector-root"), AS_OF, AS_OF.plusSeconds(1),
                "captured-after-cutoff");

        assertThat(select(ORIGINAL, List.of(classification), List.of(membership),
                List.of(mapping, futureWrong, capturedAfterCutoff)))
                .isEqualTo(select(ORIGINAL, List.of(classification),
                        List.of(membership), List.of(mapping)));
        assertUnavailable(select(ORIGINAL, List.of(classification),
                List.of(membership), List.of(futureWrong)),
                UnavailableReason.MAPPING_MISSING_AS_OF);
        assertUnavailable(select(ORIGINAL, List.of(classification),
                List.of(membership), List.of(capturedAfterCutoff)),
                UnavailableReason.MAPPING_MISSING_AS_OF);
        assertThatThrownBy(() -> mapping(
                membership, exactInterval(), recorded(),
                new Mapped(DIGITAL_SYSTEMS), AS_OF.plusSeconds(1), AS_OF,
                "available-after-capture"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void exactPitTimestampEqualityIsVisibleForAllThreeEvidenceKinds() {
        SectorAssetClassificationEvidence classification = exactClassification(
                ORIGINAL, "pit-equality");
        SectorMembershipEvidence membership = exactMembership(
                classification, "pit-equality");
        SectorMappingEvidence mapping = exactMapping(
                membership, DIGITAL_SYSTEMS, "pit-equality");
        assertThat(List.of(classification.availableAt(), classification.capturedAt(),
                membership.availableAt(), membership.capturedAt(), mapping.availableAt(),
                mapping.capturedAt())).containsOnly(AS_OF);
        assertResolved(select(ORIGINAL, List.of(classification), List.of(membership),
                List.of(mapping)));
    }

    @ParameterizedTest(name = "classification mismatch {0}")
    @EnumSource(ClassificationFault.class)
    void everyVisibleClassificationMismatchUsesItsExactReason(
            ClassificationFault fault) {
        assertUnavailable(select(ORIGINAL, List.of(classificationFault(fault)),
                List.of(), List.of()), fault.reason);
    }

    @ParameterizedTest(name = "classification precedence {0}")
    @MethodSource("classificationPrecedencePairs")
    void classificationMismatchPrecedenceIsInputOrderIndependent(
            ClassificationFault earlier,
            ClassificationFault later) {
        SectorAssetClassificationEvidence first = classificationFault(earlier);
        SectorAssetClassificationEvidence second = classificationFault(later);
        assertUnavailable(select(ORIGINAL, List.of(first, second), List.of(), List.of()),
                earlier.reason);
        assertUnavailable(select(ORIGINAL, List.of(second, first), List.of(), List.of()),
                earlier.reason);
    }

    static Stream<Arguments> classificationPrecedencePairs() {
        return Stream.of(
                Arguments.of(ClassificationFault.BASIS, ClassificationFault.ASSET),
                Arguments.of(ClassificationFault.ASSET, ClassificationFault.INTERVAL));
    }

    @Test
    void exactVisibleClassificationDuplicatesAreAmbiguousWithoutDeduplication() {
        SectorAssetClassificationEvidence classification = exactClassification(
                ORIGINAL, "classification");
        assertUnavailable(select(ORIGINAL, List.of(classification, classification),
                List.of(), List.of()), UnavailableReason.CLASSIFICATION_AMBIGUOUS);
    }

    @ParameterizedTest(name = "non-equity {0}")
    @EnumSource(value = AssetType.class, names = "EQUITY", mode = EnumSource.Mode.EXCLUDE)
    void everyNonEquityAssetTypeIsNotApplicableWithoutMembership(AssetType type) {
        SectorAssetClassificationEvidence classification = classification(
                ORIGINAL, ASSET_ID, type, PRIMARY_VENUE_ID, US, USD,
                exactInterval(), AS_OF, AS_OF, type.name());
        NotApplicable result = assertNotApplicable(select(
                ORIGINAL, List.of(classification), List.of(), List.of()),
                NotApplicableReason.NON_EQUITY);
        assertThat(result.classificationEvidence()).isSameAs(classification);
    }

    @ParameterizedTest(name = "missing membership scope inScope={0}")
    @ValueSource(booleans = {true, false})
    void missingVisibleMembershipDistinguishesEquityFromIntentionalScope(
            boolean inScope) {
        SectorAssetClassificationEvidence classification = classification(
                ORIGINAL, ASSET_ID, inScope ? AssetType.EQUITY : AssetType.INDEX,
                PRIMARY_VENUE_ID, US, USD, exactInterval(), AS_OF, AS_OF, "missing");
        SectorAssignmentResolution result = select(
                ORIGINAL, List.of(classification), List.of(), List.of());
        if (inScope) {
            assertUnavailable(result, UnavailableReason.MEMBERSHIP_MISSING_AS_OF);
        } else {
            assertNotApplicable(result, NotApplicableReason.NON_EQUITY);
        }
    }

    @ParameterizedTest(name = "membership mismatch {0}")
    @EnumSource(MembershipFault.class)
    void everyVisibleMembershipMismatchUsesItsExactReason(MembershipFault fault) {
        SectorAssetClassificationEvidence classification = exactClassification(
                ORIGINAL, "classification");
        assertUnavailable(select(ORIGINAL, List.of(classification),
                List.of(membershipFault(classification, fault)), List.of()),
                fault.reason);
    }

    @ParameterizedTest(name = "out-of-scope membership conflict {0}")
    @EnumSource(value = AssetType.class, names = "EQUITY", mode = EnumSource.Mode.EXCLUDE)
    void everyNonEquityVisibleMembershipFailsClosedBeforeMapping(AssetType type) {
        SectorAssetClassificationEvidence classification = classification(
                ORIGINAL, ASSET_ID, type, PRIMARY_VENUE_ID, US, USD,
                exactInterval(), AS_OF, AS_OF, "out-of-scope");
        SectorMembershipEvidence membership = membership(
                classification, ORIGINAL, ASSET_ID, type, PRIMARY_VENUE_ID, US, USD,
                PROVIDER_ID, PROVIDER_SCHEME_ID, PROVIDER_SCHEME_REVISION,
                PROVIDER_NODE_ID, PROVIDER_NODE_LABEL, exactInterval(), AS_OF, AS_OF,
                "out-of-scope");
        assertUnavailable(select(ORIGINAL, List.of(classification),
                List.of(membership), List.of()),
                UnavailableReason.OUT_OF_SCOPE_MEMBERSHIP_CONFLICT);
        assertUnavailable(select(ORIGINAL, List.of(classification),
                List.of(membership, membership), List.of()),
                UnavailableReason.OUT_OF_SCOPE_MEMBERSHIP_CONFLICT);
        for (MembershipFault fault : MembershipFault.values()) {
            SectorMembershipEvidence mismatched = membership(
                    classification,
                    fault == MembershipFault.BASIS
                            ? new Correction("wrong", "wrong", BASIS_TIME) : ORIGINAL,
                    fault == MembershipFault.ASSET ? "wrong-asset" : ASSET_ID,
                    fault == MembershipFault.ASSET_TYPE ? AssetType.EQUITY : type,
                    fault == MembershipFault.VENUE
                            ? "wrong-venue" : PRIMARY_VENUE_ID,
                    fault == MembershipFault.COUNTRY ? CA : US,
                    fault == MembershipFault.CURRENCY ? EUR : USD,
                    PROVIDER_ID, PROVIDER_SCHEME_ID, PROVIDER_SCHEME_REVISION,
                    PROVIDER_NODE_ID, PROVIDER_NODE_LABEL,
                    fault == MembershipFault.INTERVAL
                            ? bounded(INTERVAL_START, BASIS_TIME) : exactInterval(),
                    AS_OF, AS_OF, "out-of-scope-" + fault.name());
            assertUnavailable(select(ORIGINAL, List.of(classification),
                    List.of(mismatched), List.of()), fault.reason);
        }
    }

    @ParameterizedTest(name = "membership precedence {0}")
    @MethodSource("membershipPrecedencePairs")
    void membershipMismatchPrecedenceIsInputOrderIndependent(
            MembershipFault earlier,
            MembershipFault later) {
        SectorAssetClassificationEvidence classification = exactClassification(
                ORIGINAL, "classification");
        SectorMembershipEvidence first = membershipFault(classification, earlier);
        SectorMembershipEvidence second = membershipFault(classification, later);
        assertUnavailable(select(ORIGINAL, List.of(classification),
                List.of(first, second), List.of()), earlier.reason);
        assertUnavailable(select(ORIGINAL, List.of(classification),
                List.of(second, first), List.of()), earlier.reason);
    }

    static Stream<Arguments> membershipPrecedencePairs() {
        MembershipFault[] faults = MembershipFault.values();
        return java.util.stream.IntStream.range(0, faults.length - 1)
                .mapToObj(index -> Arguments.of(faults[index], faults[index + 1]));
    }

    @Test
    void exactVisibleMembershipDuplicatesAreAmbiguousWithoutDeduplication() {
        SectorAssetClassificationEvidence classification = exactClassification(
                ORIGINAL, "classification");
        SectorMembershipEvidence membership = exactMembership(
                classification, "membership");
        assertUnavailable(select(ORIGINAL, List.of(classification),
                List.of(membership, membership), List.of()),
                UnavailableReason.MEMBERSHIP_AMBIGUOUS);
    }

    @ParameterizedTest(name = "mapping mismatch {0}")
    @EnumSource(value = MappingFault.class, names = "CANONICAL_NODE",
            mode = EnumSource.Mode.EXCLUDE)
    void everyVisibleCommonMappingMismatchUsesItsExactReason(MappingFault fault) {
        SectorAssetClassificationEvidence classification = exactClassification(
                ORIGINAL, "classification");
        SectorMembershipEvidence membership = exactMembership(
                classification, "membership");
        assertUnavailable(select(ORIGINAL, List.of(classification),
                List.of(membership), List.of(mappingFault(membership, fault))),
                fault.reason);
    }

    @ParameterizedTest(name = "closed node rejection {0}")
    @ValueSource(strings = {
            "wsr-sector-root", "wsr-sector-unknown", "WSR-sector-digital-systems"
    })
    void rootUnknownAndCaseMutatedNodesAreNeverAssignable(String invalidNodeId) {
        SectorAssetClassificationEvidence classification = exactClassification(
                ORIGINAL, "classification");
        SectorMembershipEvidence membership = exactMembership(
                classification, "membership");
        SectorMappingEvidence mapping = exactMapping(
                membership, invalidNodeId, "invalid-node");
        assertUnavailable(select(ORIGINAL, List.of(classification),
                List.of(membership), List.of(mapping)),
                UnavailableReason.MAPPING_CANONICAL_NODE_NOT_ASSIGNABLE);
    }

    @ParameterizedTest(name = "not mapped {0}")
    @EnumSource(NotMappedReason.class)
    void everySingleNotMappedDispositionUsesItsExactUnavailableReason(
            NotMappedReason reason) {
        SectorAssetClassificationEvidence classification = exactClassification(
                ORIGINAL, "classification");
        SectorMembershipEvidence membership = exactMembership(
                classification, "membership");
        SectorMappingEvidence mapping = mapping(
                membership, exactInterval(),
                reason == NotMappedReason.PROVIDER_DEFINITION_UNAVAILABLE
                        ? new NotPublished() : recorded(),
                new NotMapped(reason), AS_OF, AS_OF, reason.name());
        UnavailableReason expected = switch (reason) {
            case NO_CANONICAL_EQUIVALENT ->
                    UnavailableReason.MAPPING_NOT_MAPPED_NO_CANONICAL_EQUIVALENT;
            case PROVIDER_NODE_TOO_BROAD ->
                    UnavailableReason.MAPPING_NOT_MAPPED_PROVIDER_NODE_TOO_BROAD;
            case PROVIDER_DEFINITION_UNAVAILABLE -> UnavailableReason
                    .MAPPING_NOT_MAPPED_PROVIDER_DEFINITION_UNAVAILABLE;
        };
        assertUnavailable(select(ORIGINAL, List.of(classification),
                List.of(membership), List.of(mapping)), expected);
    }

    @ParameterizedTest(name = "mapping precedence {0}")
    @MethodSource("mappingPrecedencePairs")
    void mappingMismatchPrecedenceIsInputOrderIndependent(
            MappingFault earlier,
            MappingFault later) {
        SectorAssetClassificationEvidence classification = exactClassification(
                ORIGINAL, "classification");
        SectorMembershipEvidence membership = exactMembership(
                classification, "membership");
        SectorMappingEvidence first = mappingFault(membership, earlier);
        SectorMappingEvidence second = mappingFault(membership, later);
        assertUnavailable(select(ORIGINAL, List.of(classification),
                List.of(membership), List.of(first, second)), earlier.reason);
        assertUnavailable(select(ORIGINAL, List.of(classification),
                List.of(membership), List.of(second, first)), earlier.reason);
    }

    static Stream<Arguments> mappingPrecedencePairs() {
        MappingFault[] faults = MappingFault.values();
        return java.util.stream.IntStream.range(0, faults.length - 1)
                .mapToObj(index -> Arguments.of(faults[index], faults[index + 1]));
    }

    @Test
    void unequalVisibleMappingDispositionsAreConflictsBeforeAmbiguity() {
        SectorAssetClassificationEvidence classification = exactClassification(
                ORIGINAL, "classification");
        SectorMembershipEvidence membership = exactMembership(
                classification, "membership");
        SectorMappingEvidence first = exactMapping(
                membership, DIGITAL_SYSTEMS, "first");
        SectorMappingEvidence differentTarget = exactMapping(
                membership, CANONICAL_LEAVES.get(1), "different-target");
        SectorMappingEvidence notMapped = mapping(
                membership, exactInterval(), recorded(),
                new NotMapped(NotMappedReason.NO_CANONICAL_EQUIVALENT),
                AS_OF, AS_OF, "not-mapped");
        SectorMappingEvidence differentNotMapped = mapping(
                membership, exactInterval(), recorded(),
                new NotMapped(NotMappedReason.PROVIDER_NODE_TOO_BROAD),
                AS_OF, AS_OF, "different-not-mapped");
        for (List<SectorMappingEvidence> conflict : List.of(
                List.of(first, differentTarget),
                List.of(first, notMapped),
                List.of(notMapped, differentNotMapped))) {
            assertUnavailable(select(ORIGINAL, List.of(classification),
                    List.of(membership), conflict), UnavailableReason.MAPPING_CONFLICT);
        }
    }

    @Test
    void equalAndSameDispositionVisibleMappingRowsAreAmbiguousWithoutDeduplication() {
        SectorAssetClassificationEvidence classification = exactClassification(
                ORIGINAL, "classification");
        SectorMembershipEvidence membership = exactMembership(
                classification, "membership");
        SectorMappingEvidence exact = exactMapping(membership, DIGITAL_SYSTEMS, "exact");
        SectorMappingEvidence sameTarget = exactMapping(
                membership, DIGITAL_SYSTEMS, "same-target-distinct-evidence");
        SectorMappingEvidence notMapped = mapping(
                membership, exactInterval(), recorded(),
                new NotMapped(NotMappedReason.NO_CANONICAL_EQUIVALENT),
                AS_OF, AS_OF, "not-mapped");
        SectorMappingEvidence sameNotMapped = mapping(
                membership, open(INTERVAL_START), recorded(),
                new NotMapped(NotMappedReason.NO_CANONICAL_EQUIVALENT),
                AS_OF, AS_OF, "same-not-mapped");
        for (List<SectorMappingEvidence> ambiguous : List.of(
                List.of(exact, exact), List.of(exact, sameTarget),
                List.of(notMapped, sameNotMapped))) {
            assertUnavailable(select(ORIGINAL, List.of(classification),
                    List.of(membership), ambiguous),
                    UnavailableReason.MAPPING_AMBIGUOUS);
        }
    }

    @ParameterizedTest(name = "classification mismatch before ambiguity {0}")
    @EnumSource(ClassificationFault.class)
    void everyClassificationMismatchPoisonsValidDuplicatesBeforeAmbiguity(
            ClassificationFault fault) {
        SectorAssetClassificationEvidence exact = exactClassification(
                ORIGINAL, "exact");
        SectorAssetClassificationEvidence wrong = classificationFault(fault);
        assertUnavailable(select(ORIGINAL, List.of(exact, exact, wrong),
                List.of(), List.of()), fault.reason);
        assertUnavailable(select(ORIGINAL, List.of(wrong, exact, exact),
                List.of(), List.of()), fault.reason);
    }

    @ParameterizedTest(name = "membership mismatch before ambiguity {0}")
    @EnumSource(MembershipFault.class)
    void everyMembershipMismatchPoisonsValidDuplicatesBeforeAmbiguity(
            MembershipFault fault) {
        SectorAssetClassificationEvidence classification = exactClassification(
                ORIGINAL, "classification");
        SectorMembershipEvidence exact = exactMembership(classification, "exact");
        SectorMembershipEvidence wrong = membershipFault(classification, fault);
        assertUnavailable(select(ORIGINAL, List.of(classification),
                List.of(exact, exact, wrong), List.of()), fault.reason);
        assertUnavailable(select(ORIGINAL, List.of(classification),
                List.of(wrong, exact, exact), List.of()), fault.reason);
    }

    @ParameterizedTest(name = "mapping mismatch before ambiguity {0}")
    @EnumSource(MappingFault.class)
    void everyMappingMismatchPoisonsValidDuplicatesBeforeMultiplicity(
            MappingFault fault) {
        SectorAssetClassificationEvidence classification = exactClassification(
                ORIGINAL, "classification");
        SectorMembershipEvidence membership = exactMembership(
                classification, "membership");
        SectorMappingEvidence exact = exactMapping(membership, DIGITAL_SYSTEMS, "exact");
        SectorMappingEvidence wrong = mappingFault(membership, fault);
        assertUnavailable(select(ORIGINAL, List.of(classification),
                List.of(membership), List.of(exact, exact, wrong)), fault.reason);
        assertUnavailable(select(ORIGINAL, List.of(classification),
                List.of(membership), List.of(wrong, exact, exact)), fault.reason);
    }

    @Test
    void providerIdentityIsExactRawUnicodeWhileLabelsRemainPreservedNonKeys() {
        SectorAssetClassificationEvidence classification = exactClassification(
                ORIGINAL, "classification");
        SectorMembershipEvidence rawMembership = membership(
                classification, ORIGINAL, ASSET_ID, AssetType.EQUITY,
                PRIMARY_VENUE_ID, US, USD, " Provider-\u00e9 ", "Scheme-A",
                "Revision-A", "Node-\u00e9", "Membership label", exactInterval(),
                AS_OF, AS_OF, "raw");
        SectorMappingEvidence exactRaw = mappingWith(
                rawMembership, MAPPING_SET_ID, MAPPING_SET_VERSION, MAPPING_SET_HASH,
                MAPPING_POLICY_VERSION, MAPPING_POLICY_HASH, TAXONOMY_ID,
                TAXONOMY_VERSION, TAXONOMY_HASH, " Provider-\u00e9 ", "Scheme-A",
                "Revision-A", "Node-\u00e9", "Different mapping label",
                exactInterval(), new Recorded(" Different definition ", "en"),
                new Mapped(DIGITAL_SYSTEMS), AS_OF, AS_OF, "raw");
        assertResolved(select(ORIGINAL, List.of(classification),
                List.of(rawMembership), List.of(exactRaw)));

        SectorMappingEvidence decomposed = mappingWith(
                rawMembership, MAPPING_SET_ID, MAPPING_SET_VERSION, MAPPING_SET_HASH,
                MAPPING_POLICY_VERSION, MAPPING_POLICY_HASH, TAXONOMY_ID,
                TAXONOMY_VERSION, TAXONOMY_HASH, " Provider-e\u0301 ", "Scheme-A",
                "Revision-A", "Node-\u00e9", "Different", exactInterval(), recorded(),
                new Mapped(DIGITAL_SYSTEMS), AS_OF, AS_OF, "decomposed");
        assertUnavailable(select(ORIGINAL, List.of(classification),
                List.of(rawMembership), List.of(decomposed)),
                UnavailableReason.MAPPING_PROVIDER_ID_MISMATCH);

        SectorMappingEvidence caseChanged = mappingWith(
                rawMembership, MAPPING_SET_ID, MAPPING_SET_VERSION, MAPPING_SET_HASH,
                MAPPING_POLICY_VERSION, MAPPING_POLICY_HASH, TAXONOMY_ID,
                TAXONOMY_VERSION, TAXONOMY_HASH, " Provider-\u00e9 ", "scheme-A",
                "Revision-A", "Node-\u00e9", "Different", exactInterval(), recorded(),
                new Mapped(DIGITAL_SYSTEMS), AS_OF, AS_OF, "case");
        assertUnavailable(select(ORIGINAL, List.of(classification),
                List.of(rawMembership), List.of(caseChanged)),
                UnavailableReason.MAPPING_PROVIDER_SCHEME_ID_MISMATCH);
    }

    @Test
    void callerAttestedMappingSetIdentityIsEchoedButItsDigestIsNotRecomputed() {
        String callerHash = "1234567890abcdef".repeat(4);
        SectorAssetClassificationEvidence classification = exactClassification(
                ORIGINAL, "classification");
        SectorMembershipEvidence membership = exactMembership(
                classification, "membership");
        SectorMappingEvidence mapping = mappingWith(
                membership, "caller-set", "version-caller", callerHash,
                MAPPING_POLICY_VERSION, MAPPING_POLICY_HASH, TAXONOMY_ID,
                TAXONOMY_VERSION, TAXONOMY_HASH, membership.providerId(),
                membership.providerSchemeId(), membership.providerSchemeRevision(),
                membership.providerNodeId(), "Unrelated label", exactInterval(),
                recorded(), new Mapped(DIGITAL_SYSTEMS), AS_OF, AS_OF, "caller");
        SectorAssignmentRequest request = request(
                ORIGINAL, AS_OF, "caller-set", "version-caller", callerHash,
                List.of(classification), List.of(membership), List.of(mapping));

        Resolved result = assertResolved(SectorAssignmentSelector.select(request));
        assertThat(result.context().mappingSetId()).isEqualTo("caller-set");
        assertThat(result.context().mappingSetVersion()).isEqualTo("version-caller");
        assertThat(result.context().mappingSetDefinitionHash()).isEqualTo(callerHash);
        assertThat(result.mappingEvidence()).isSameAs(mapping);
    }

    @Test
    void mappingSetAndEvidenceHashesRejectEveryNonLowercaseSha256Shape() {
        List<String> invalidHashes = Arrays.asList(
                null, "", "a".repeat(63), "a".repeat(65), "A".repeat(64),
                "g".repeat(64));
        for (String invalid : invalidHashes) {
            assertThatThrownBy(() -> request(
                    ORIGINAL, AS_OF, MAPPING_SET_ID, MAPPING_SET_VERSION, invalid,
                    List.of(), List.of(), List.of()))
                    .isInstanceOfAny(NullPointerException.class,
                            IllegalArgumentException.class);
        }
        SectorAssetClassificationEvidence classification = exactClassification(
                ORIGINAL, "classification");
        SectorMembershipEvidence membership = exactMembership(
                classification, "membership");
        for (String invalid : invalidHashes) {
            for (String field : List.of("mappingPolicyDefinitionHash",
                    "mappingSetDefinitionHash", "taxonomyDefinitionHash")) {
                assertThatThrownBy(() -> mappingWithInvalidHash(
                        membership, field, invalid))
                        .as(field + "=" + invalid)
                        .isInstanceOfAny(NullPointerException.class,
                                IllegalArgumentException.class);
            }
        }
    }

    @Test
    void requestDefensivelyCopiesListsAndRejectsEveryMissingPublicInput() {
        SectorAssetClassificationEvidence classification = exactClassification(
                ORIGINAL, "classification");
        SectorMembershipEvidence membership = exactMembership(
                classification, "membership");
        SectorMappingEvidence mapping = exactMapping(
                membership, DIGITAL_SYSTEMS, "mapping");
        List<SectorAssetClassificationEvidence> classifications =
                new ArrayList<>(List.of(classification));
        List<SectorMembershipEvidence> memberships =
                new ArrayList<>(List.of(membership));
        List<SectorMappingEvidence> mappings = new ArrayList<>(List.of(mapping));
        SectorAssignmentRequest request = request(
                ORIGINAL, AS_OF, MAPPING_SET_ID, MAPPING_SET_VERSION, MAPPING_SET_HASH,
                classifications, memberships, mappings);
        classifications.clear();
        memberships.clear();
        mappings.clear();
        assertThat(request.classificationCandidates()).containsExactly(classification);
        assertThat(request.membershipCandidates()).containsExactly(membership);
        assertThat(request.mappingCandidates()).containsExactly(mapping);
        assertThatThrownBy(() -> request.classificationCandidates().clear())
                .isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> request.membershipCandidates().clear())
                .isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> request.mappingCandidates().clear())
                .isInstanceOf(UnsupportedOperationException.class);

        List<ThrowingCallable> invalid = List.of(
                () -> new SectorAssignmentRequest(null, ORIGINAL, ASSET_ID, AS_OF,
                        MAPPING_SET_ID, MAPPING_SET_VERSION, MAPPING_SET_HASH,
                        List.of(), List.of(), List.of()),
                () -> new SectorAssignmentRequest(policy(), null, ASSET_ID, AS_OF,
                        MAPPING_SET_ID, MAPPING_SET_VERSION, MAPPING_SET_HASH,
                        List.of(), List.of(), List.of()),
                () -> new SectorAssignmentRequest(policy(), ORIGINAL, null, AS_OF,
                        MAPPING_SET_ID, MAPPING_SET_VERSION, MAPPING_SET_HASH,
                        List.of(), List.of(), List.of()),
                () -> new SectorAssignmentRequest(policy(), ORIGINAL, ASSET_ID, null,
                        MAPPING_SET_ID, MAPPING_SET_VERSION, MAPPING_SET_HASH,
                        List.of(), List.of(), List.of()),
                () -> new SectorAssignmentRequest(policy(), ORIGINAL, ASSET_ID, AS_OF,
                        null, MAPPING_SET_VERSION, MAPPING_SET_HASH,
                        List.of(), List.of(), List.of()),
                () -> new SectorAssignmentRequest(policy(), ORIGINAL, ASSET_ID, AS_OF,
                        MAPPING_SET_ID, null, MAPPING_SET_HASH,
                        List.of(), List.of(), List.of()),
                () -> new SectorAssignmentRequest(policy(), ORIGINAL, ASSET_ID, AS_OF,
                        MAPPING_SET_ID, MAPPING_SET_VERSION, null,
                        List.of(), List.of(), List.of()),
                () -> new SectorAssignmentRequest(policy(), ORIGINAL, ASSET_ID, AS_OF,
                        MAPPING_SET_ID, MAPPING_SET_VERSION, MAPPING_SET_HASH,
                        null, List.of(), List.of()),
                () -> new SectorAssignmentRequest(policy(), ORIGINAL, ASSET_ID, AS_OF,
                        MAPPING_SET_ID, MAPPING_SET_VERSION, MAPPING_SET_HASH,
                        Arrays.asList((SectorAssetClassificationEvidence) null),
                        List.of(), List.of()),
                () -> new SectorAssignmentRequest(policy(), ORIGINAL, ASSET_ID, AS_OF,
                        MAPPING_SET_ID, MAPPING_SET_VERSION, MAPPING_SET_HASH,
                        List.of(), null, List.of()),
                () -> new SectorAssignmentRequest(policy(), ORIGINAL, ASSET_ID, AS_OF,
                        MAPPING_SET_ID, MAPPING_SET_VERSION, MAPPING_SET_HASH,
                        List.of(), Arrays.asList((SectorMembershipEvidence) null),
                        List.of()),
                () -> new SectorAssignmentRequest(policy(), ORIGINAL, ASSET_ID, AS_OF,
                        MAPPING_SET_ID, MAPPING_SET_VERSION, MAPPING_SET_HASH,
                        List.of(), List.of(), null),
                () -> new SectorAssignmentRequest(policy(), ORIGINAL, ASSET_ID, AS_OF,
                        MAPPING_SET_ID, MAPPING_SET_VERSION, MAPPING_SET_HASH,
                        List.of(), List.of(), Arrays.asList((SectorMappingEvidence) null)),
                () -> SectorAssignmentSelector.select(null));
        invalid.forEach(callable -> assertThatThrownBy(callable)
                .isInstanceOf(NullPointerException.class));
    }

    @Test
    void requestRejectsPreBasisAndSubMicrosecondEvaluationInstants() {
        assertThatThrownBy(() -> request(
                ORIGINAL, BASIS_TIME.minusNanos(1_000), MAPPING_SET_ID,
                MAPPING_SET_VERSION, MAPPING_SET_HASH, List.of(), List.of(), List.of()))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> request(
                ORIGINAL, AS_OF.plusNanos(1), MAPPING_SET_ID, MAPPING_SET_VERSION,
                MAPPING_SET_HASH, List.of(), List.of(), List.of()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void classificationEvidenceRejectsInvalidTextCountryAndStructuralComponents() {
        for (String invalid : Arrays.asList(null, "", " ", " value ")) {
            assertThatThrownBy(() -> classificationWithText(
                    "classificationEvidenceId", invalid))
                    .isInstanceOfAny(NullPointerException.class,
                            IllegalArgumentException.class);
        }
        for (String invalid : List.of("", " ", "us", "USA", "U1", "ZZ")) {
            assertThatThrownBy(() -> classification(
                    ORIGINAL, ASSET_ID, AssetType.EQUITY, PRIMARY_VENUE_ID, invalid,
                    USD, exactInterval(), AS_OF, AS_OF, "country"))
                    .isInstanceOf(IllegalArgumentException.class);
        }
        List<ThrowingCallable> invalid = List.of(
                () -> classification(null, ASSET_ID, AssetType.EQUITY,
                        PRIMARY_VENUE_ID, US, USD, exactInterval(), AS_OF, AS_OF, "x"),
                () -> classification(ORIGINAL, ASSET_ID, null,
                        PRIMARY_VENUE_ID, US, USD, exactInterval(), AS_OF, AS_OF, "x"),
                () -> classification(ORIGINAL, ASSET_ID, AssetType.EQUITY,
                        PRIMARY_VENUE_ID, US, null, exactInterval(), AS_OF, AS_OF, "x"),
                () -> classification(ORIGINAL, ASSET_ID, AssetType.EQUITY,
                        PRIMARY_VENUE_ID, US, USD, null, AS_OF, AS_OF, "x"),
                () -> classification(ORIGINAL, ASSET_ID, AssetType.EQUITY,
                        PRIMARY_VENUE_ID, US, USD, exactInterval(), null, AS_OF, "x"),
                () -> classification(ORIGINAL, ASSET_ID, AssetType.EQUITY,
                        PRIMARY_VENUE_ID, US, USD, exactInterval(), AS_OF, null, "x"),
                () -> classification(ORIGINAL, ASSET_ID, AssetType.EQUITY,
                        PRIMARY_VENUE_ID, US, USD, exactInterval(),
                        AS_OF, AS_OF.minusNanos(1_000), "x"));
        invalid.forEach(callable -> assertThatThrownBy(callable)
                .isInstanceOfAny(NullPointerException.class,
                        IllegalArgumentException.class));
    }

    @Test
    void membershipEvidenceRejectsInvalidCanonicalAndMissingStructuralComponents() {
        SectorAssetClassificationEvidence classification = exactClassification(
                ORIGINAL, "classification");
        for (String invalid : Arrays.asList(null, "", " ", " value ")) {
            assertThatThrownBy(() -> membershipWithText(
                    classification, "membershipEvidenceId", invalid))
                    .isInstanceOfAny(NullPointerException.class,
                            IllegalArgumentException.class);
        }
        for (String invalid : Arrays.asList(null, "")) {
            assertThatThrownBy(() -> membershipWithText(
                    classification, "providerId", invalid))
                    .isInstanceOfAny(NullPointerException.class,
                            IllegalArgumentException.class);
        }
        List<ThrowingCallable> invalid = List.of(
                () -> membership(classification, null, ASSET_ID, AssetType.EQUITY,
                        PRIMARY_VENUE_ID, US, USD, PROVIDER_ID, PROVIDER_SCHEME_ID,
                        PROVIDER_SCHEME_REVISION, PROVIDER_NODE_ID, PROVIDER_NODE_LABEL,
                        exactInterval(), AS_OF, AS_OF, "x"),
                () -> membership(classification, ORIGINAL, ASSET_ID, null,
                        PRIMARY_VENUE_ID, US, USD, PROVIDER_ID, PROVIDER_SCHEME_ID,
                        PROVIDER_SCHEME_REVISION, PROVIDER_NODE_ID, PROVIDER_NODE_LABEL,
                        exactInterval(), AS_OF, AS_OF, "x"),
                () -> membership(classification, ORIGINAL, ASSET_ID, AssetType.EQUITY,
                        PRIMARY_VENUE_ID, US, null, PROVIDER_ID, PROVIDER_SCHEME_ID,
                        PROVIDER_SCHEME_REVISION, PROVIDER_NODE_ID, PROVIDER_NODE_LABEL,
                        exactInterval(), AS_OF, AS_OF, "x"),
                () -> membership(classification, ORIGINAL, ASSET_ID, AssetType.EQUITY,
                        PRIMARY_VENUE_ID, US, USD, PROVIDER_ID, PROVIDER_SCHEME_ID,
                        PROVIDER_SCHEME_REVISION, PROVIDER_NODE_ID, PROVIDER_NODE_LABEL,
                        null, AS_OF, AS_OF, "x"),
                () -> membership(classification, ORIGINAL, ASSET_ID, AssetType.EQUITY,
                        PRIMARY_VENUE_ID, US, USD, PROVIDER_ID, PROVIDER_SCHEME_ID,
                        PROVIDER_SCHEME_REVISION, PROVIDER_NODE_ID, PROVIDER_NODE_LABEL,
                        exactInterval(), AS_OF, AS_OF.minusNanos(1_000), "x"));
        invalid.forEach(callable -> assertThatThrownBy(callable)
                .isInstanceOfAny(NullPointerException.class,
                        IllegalArgumentException.class));
    }

    @Test
    void mappingEvidenceRejectsInvalidCanonicalAndMissingStructuralComponents() {
        SectorAssetClassificationEvidence classification = exactClassification(
                ORIGINAL, "classification");
        SectorMembershipEvidence membership = exactMembership(
                classification, "membership");
        for (String invalid : Arrays.asList(null, "", " ", " value ")) {
            assertThatThrownBy(() -> mappingWithText(
                    membership, "mappingEvidenceId", invalid))
                    .isInstanceOfAny(NullPointerException.class,
                            IllegalArgumentException.class);
        }
        for (String invalid : Arrays.asList(null, "")) {
            assertThatThrownBy(() -> mappingWithText(
                    membership, "providerId", invalid))
                    .isInstanceOfAny(NullPointerException.class,
                            IllegalArgumentException.class);
        }
        assertThatThrownBy(() -> mappingWith(
                membership, MAPPING_SET_ID, MAPPING_SET_VERSION, MAPPING_SET_HASH,
                MAPPING_POLICY_VERSION, MAPPING_POLICY_HASH, TAXONOMY_ID,
                TAXONOMY_VERSION, TAXONOMY_HASH, PROVIDER_ID, PROVIDER_SCHEME_ID,
                PROVIDER_SCHEME_REVISION, PROVIDER_NODE_ID, PROVIDER_NODE_LABEL,
                null, recorded(), new Mapped(DIGITAL_SYSTEMS), AS_OF, AS_OF, "x"))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> mappingWith(
                membership, MAPPING_SET_ID, MAPPING_SET_VERSION, MAPPING_SET_HASH,
                MAPPING_POLICY_VERSION, MAPPING_POLICY_HASH, TAXONOMY_ID,
                TAXONOMY_VERSION, TAXONOMY_HASH, PROVIDER_ID, PROVIDER_SCHEME_ID,
                PROVIDER_SCHEME_REVISION, PROVIDER_NODE_ID, PROVIDER_NODE_LABEL,
                exactInterval(), null, new Mapped(DIGITAL_SYSTEMS), AS_OF, AS_OF, "x"))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> mappingWith(
                membership, MAPPING_SET_ID, MAPPING_SET_VERSION, MAPPING_SET_HASH,
                MAPPING_POLICY_VERSION, MAPPING_POLICY_HASH, TAXONOMY_ID,
                TAXONOMY_VERSION, TAXONOMY_HASH, PROVIDER_ID, PROVIDER_SCHEME_ID,
                PROVIDER_SCHEME_REVISION, PROVIDER_NODE_ID, PROVIDER_NODE_LABEL,
                exactInterval(), recorded(), null, AS_OF, AS_OF, "x"))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void providerDefinitionAndDispositionConstructorsRemainClosedAndValidated() {
        assertThat(new Recorded(" definition ", "en").value())
                .isEqualTo(" definition ");
        assertThat(new NotPublished()).isEqualTo(new NotPublished());
        assertThatThrownBy(() -> new Recorded(null, "en"))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new Recorded("", "en"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new Recorded("definition", " en "))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new Mapped(null))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new Mapped(" root "))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new NotMapped(null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void effectiveIntervalConstructorsRejectNullFinerAndNonIncreasingBounds() {
        List<ThrowingCallable> invalid = List.of(
                () -> new EffectiveInterval(null, new OpenEnded()),
                () -> new EffectiveInterval(INTERVAL_START, null),
                () -> new EndsAtExclusive(null),
                () -> new EffectiveInterval(INTERVAL_START.plusNanos(1),
                        new OpenEnded()),
                () -> new EndsAtExclusive(INTERVAL_END.plusNanos(1)),
                () -> bounded(INTERVAL_START, INTERVAL_START),
                () -> bounded(INTERVAL_START, INTERVAL_START.minusNanos(1_000)));
        invalid.forEach(callable -> assertThatThrownBy(callable)
                .isInstanceOfAny(NullPointerException.class,
                        IllegalArgumentException.class));
        assertThatThrownBy(() -> exactInterval().contains(BASIS_TIME.plusNanos(1)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void directResultConstructorsRejectMissingFutureAndContradictoryComponents() {
        SectorAssetClassificationEvidence classification = exactClassification(
                ORIGINAL, "classification");
        SectorMembershipEvidence membership = exactMembership(
                classification, "membership");
        SectorMappingEvidence mapping = exactMapping(
                membership, DIGITAL_SYSTEMS, "mapping");
        ResolutionContext context = context(ORIGINAL);
        List<ThrowingCallable> invalidContext = List.of(
                () -> new ResolutionContext(null, POLICY_HASH, ORIGINAL, ASSET_ID,
                        AS_OF, MAPPING_SET_ID, MAPPING_SET_VERSION, MAPPING_SET_HASH),
                () -> new ResolutionContext(policy(), null, ORIGINAL, ASSET_ID,
                        AS_OF, MAPPING_SET_ID, MAPPING_SET_VERSION, MAPPING_SET_HASH),
                () -> new ResolutionContext(policy(), "0".repeat(64), ORIGINAL,
                        ASSET_ID, AS_OF, MAPPING_SET_ID, MAPPING_SET_VERSION,
                        MAPPING_SET_HASH),
                () -> new ResolutionContext(policy(), POLICY_HASH, null, ASSET_ID,
                        AS_OF, MAPPING_SET_ID, MAPPING_SET_VERSION, MAPPING_SET_HASH),
                () -> new ResolutionContext(policy(), POLICY_HASH, ORIGINAL, null,
                        AS_OF, MAPPING_SET_ID, MAPPING_SET_VERSION, MAPPING_SET_HASH),
                () -> new ResolutionContext(policy(), POLICY_HASH, ORIGINAL, ASSET_ID,
                        null, MAPPING_SET_ID, MAPPING_SET_VERSION, MAPPING_SET_HASH),
                () -> new ResolutionContext(policy(), POLICY_HASH, ORIGINAL, ASSET_ID,
                        AS_OF, null, MAPPING_SET_VERSION, MAPPING_SET_HASH));
        invalidContext.forEach(callable -> assertThatThrownBy(callable)
                .isInstanceOfAny(NullPointerException.class,
                        IllegalArgumentException.class));

        assertThatThrownBy(() -> new Resolved(null, classification, membership, mapping))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new Resolved(context, null, membership, mapping))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new Resolved(context, classification, null, mapping))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new Resolved(context, classification, membership, null))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new Resolved(context,
                classificationFault(ClassificationFault.ASSET), membership, mapping))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new Resolved(context, classification,
                membershipFault(classification, MembershipFault.ASSET), mapping))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new Resolved(context, classification, membership,
                mappingFault(membership, MappingFault.CANONICAL_NODE)))
                .isInstanceOf(IllegalArgumentException.class);
        SectorAssetClassificationEvidence futureClassification = classification(
                ORIGINAL, ASSET_ID, AssetType.EQUITY, PRIMARY_VENUE_ID, US, USD,
                exactInterval(), AS_OF, AS_OF.plusSeconds(1),
                "future-constructor");
        SectorMembershipEvidence futureMembership = membership(
                classification, ORIGINAL, ASSET_ID, AssetType.EQUITY,
                PRIMARY_VENUE_ID, US, USD, PROVIDER_ID, PROVIDER_SCHEME_ID,
                PROVIDER_SCHEME_REVISION, PROVIDER_NODE_ID, PROVIDER_NODE_LABEL,
                exactInterval(), AS_OF, AS_OF.plusSeconds(1),
                "future-constructor");
        SectorMappingEvidence futureMapping = mapping(
                membership, exactInterval(), recorded(),
                new Mapped(DIGITAL_SYSTEMS), AS_OF, AS_OF.plusSeconds(1),
                "future-constructor");
        assertThatThrownBy(() -> new Resolved(
                context, futureClassification, membership, mapping))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new Resolved(
                context, classification, futureMembership, mapping))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new Resolved(
                context, classification, membership, futureMapping))
                .isInstanceOf(IllegalArgumentException.class);

        SectorAssetClassificationEvidence nonEquity = classification(
                ORIGINAL, ASSET_ID, AssetType.INDEX, PRIMARY_VENUE_ID, US, USD,
                exactInterval(), AS_OF, AS_OF, "non-equity");
        assertThatThrownBy(() -> new NotApplicable(null, nonEquity,
                NotApplicableReason.NON_EQUITY)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new NotApplicable(context, null,
                NotApplicableReason.NON_EQUITY)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new NotApplicable(context, nonEquity, null))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new NotApplicable(context, classification,
                NotApplicableReason.NON_EQUITY))
                .isInstanceOf(IllegalArgumentException.class);
        SectorAssetClassificationEvidence futureNonEquity = classification(
                ORIGINAL, ASSET_ID, AssetType.INDEX, PRIMARY_VENUE_ID, US, USD,
                exactInterval(), AS_OF, AS_OF.plusSeconds(1),
                "future-non-equity-constructor");
        assertThatThrownBy(() -> new NotApplicable(
                context, futureNonEquity, NotApplicableReason.NON_EQUITY))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new Unavailable(null,
                UnavailableReason.CLASSIFICATION_MISSING_AS_OF))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new Unavailable(context, null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void selectedEvidenceIsPreservedExactlyAndUnavailableInventsNoEvidence() {
        SectorAssetClassificationEvidence classification = exactClassification(
                ORIGINAL, "classification");
        SectorMembershipEvidence membership = exactMembership(
                classification, "membership");
        SectorMappingEvidence mapping = exactMapping(
                membership, DIGITAL_SYSTEMS, "mapping");
        Resolved resolved = assertResolved(select(
                ORIGINAL, List.of(classification), List.of(membership),
                List.of(mapping)));
        assertThat(resolved.classificationEvidence()).isSameAs(classification);
        assertThat(resolved.membershipEvidence()).isSameAs(membership);
        assertThat(resolved.mappingEvidence()).isSameAs(mapping);
        Unavailable unavailable = assertUnavailable(select(
                ORIGINAL, List.of(), List.of(), List.of()),
                UnavailableReason.CLASSIFICATION_MISSING_AS_OF);
        assertRecordComponents(unavailable.getClass(), "context:ResolutionContext",
                "reason:UnavailableReason");
    }

    @Test
    void replayIsIndependentOfInputOrderJvmDefaultsAndPriorCalls() {
        SectorAssetClassificationEvidence classification = exactClassification(
                ORIGINAL, "classification");
        SectorMembershipEvidence membership = exactMembership(
                classification, "membership");
        SectorMappingEvidence earlier = mappingFault(
                membership, MappingFault.PROVIDER_ID);
        SectorMappingEvidence later = mappingFault(
                membership, MappingFault.CANONICAL_NODE);
        SectorAssignmentResolution baseline = select(
                ORIGINAL, List.of(classification), List.of(membership),
                List.of(earlier, later));
        assertThat(select(ORIGINAL, List.of(classification), List.of(membership),
                List.of(later, earlier))).isEqualTo(baseline);
        assertNotApplicable(select(ORIGINAL, List.of(classification(
                ORIGINAL, ASSET_ID, AssetType.INDEX, PRIMARY_VENUE_ID, US, USD,
                exactInterval(), AS_OF, AS_OF, "prior")), List.of(), List.of()),
                NotApplicableReason.NON_EQUITY);

        Locale originalLocale = Locale.getDefault();
        TimeZone originalTimeZone = TimeZone.getDefault();
        try {
            Locale.setDefault(Locale.forLanguageTag("tr-TR"));
            TimeZone.setDefault(TimeZone.getTimeZone("Pacific/Kiritimati"));
            assertThat(select(ORIGINAL, List.of(classification), List.of(membership),
                    List.of(later, earlier))).isEqualTo(baseline);
        } finally {
            TimeZone.setDefault(originalTimeZone);
            Locale.setDefault(originalLocale);
        }
    }

    private enum ClassificationFault {
        BASIS(UnavailableReason.CLASSIFICATION_BASIS_MISMATCH),
        ASSET(UnavailableReason.CLASSIFICATION_ASSET_MISMATCH),
        INTERVAL(UnavailableReason.CLASSIFICATION_EFFECTIVE_INTERVAL_MISMATCH);

        private final UnavailableReason reason;

        ClassificationFault(UnavailableReason reason) {
            this.reason = reason;
        }
    }

    private enum MembershipFault {
        BASIS(UnavailableReason.MEMBERSHIP_BASIS_MISMATCH),
        ASSET(UnavailableReason.MEMBERSHIP_ASSET_MISMATCH),
        ASSET_TYPE(UnavailableReason.MEMBERSHIP_ASSET_TYPE_MISMATCH),
        VENUE(UnavailableReason.MEMBERSHIP_PRIMARY_VENUE_MISMATCH),
        COUNTRY(UnavailableReason.MEMBERSHIP_PRIMARY_VENUE_COUNTRY_MISMATCH),
        CURRENCY(UnavailableReason.MEMBERSHIP_CURRENCY_MISMATCH),
        INTERVAL(UnavailableReason.MEMBERSHIP_EFFECTIVE_INTERVAL_MISMATCH);

        private final UnavailableReason reason;

        MembershipFault(UnavailableReason reason) {
            this.reason = reason;
        }
    }

    private enum MappingFault {
        SET_ID(UnavailableReason.MAPPING_SET_ID_MISMATCH),
        SET_VERSION(UnavailableReason.MAPPING_SET_VERSION_MISMATCH),
        SET_HASH(UnavailableReason.MAPPING_SET_DEFINITION_HASH_MISMATCH),
        POLICY_VERSION(UnavailableReason.MAPPING_POLICY_VERSION_MISMATCH),
        POLICY_HASH(UnavailableReason.MAPPING_POLICY_DEFINITION_HASH_MISMATCH),
        TAXONOMY_ID(UnavailableReason.MAPPING_TAXONOMY_ID_MISMATCH),
        TAXONOMY_VERSION(UnavailableReason.MAPPING_TAXONOMY_VERSION_MISMATCH),
        TAXONOMY_HASH(UnavailableReason.MAPPING_TAXONOMY_DEFINITION_HASH_MISMATCH),
        PROVIDER_ID(UnavailableReason.MAPPING_PROVIDER_ID_MISMATCH),
        PROVIDER_SCHEME_ID(UnavailableReason.MAPPING_PROVIDER_SCHEME_ID_MISMATCH),
        PROVIDER_SCHEME_REVISION(
                UnavailableReason.MAPPING_PROVIDER_SCHEME_REVISION_MISMATCH),
        PROVIDER_NODE_ID(UnavailableReason.MAPPING_PROVIDER_NODE_ID_MISMATCH),
        INTERVAL(UnavailableReason.MAPPING_EFFECTIVE_INTERVAL_MISMATCH),
        MAPPED_DEFINITION(UnavailableReason.MAPPING_MAPPED_DEFINITION_REQUIRED),
        CANONICAL_NODE(UnavailableReason.MAPPING_CANONICAL_NODE_NOT_ASSIGNABLE);

        private final UnavailableReason reason;

        MappingFault(UnavailableReason reason) {
            this.reason = reason;
        }
    }

    private static SectorAssignmentPolicyVersion policy() {
        return SectorAssignmentPolicyVersion
                .POINT_IN_TIME_EXPLICIT_WSR_ECONOMIC_ACTIVITY_SECTOR_ASSIGNMENT_V1;
    }

    private static Recorded recorded() {
        return new Recorded("Provider definition", "en");
    }

    private static EffectiveInterval exactInterval() {
        return bounded(INTERVAL_START, INTERVAL_END);
    }

    private static EffectiveInterval bounded(Instant start, Instant end) {
        return new EffectiveInterval(start, new EndsAtExclusive(end));
    }

    private static EffectiveInterval open(Instant start) {
        return new EffectiveInterval(start, new OpenEnded());
    }

    private static SectorAssetClassificationEvidence exactClassification(
            OutcomeBasis basis,
            String suffix) {
        return classification(basis, ASSET_ID, AssetType.EQUITY, PRIMARY_VENUE_ID,
                US, USD, exactInterval(), AS_OF, AS_OF, suffix);
    }

    private static SectorAssetClassificationEvidence classification(
            OutcomeBasis basis,
            String assetId,
            AssetType assetType,
            String venue,
            String country,
            Currency currency,
            EffectiveInterval interval,
            Instant availableAt,
            Instant capturedAt,
            String suffix) {
        return new SectorAssetClassificationEvidence(
                "classification-" + suffix, "classification-event-" + suffix,
                basis, assetId, assetType, venue, country, currency,
                "classification-source", "classification-revision", "provenance",
                interval, availableAt, capturedAt);
    }

    private static SectorMembershipEvidence exactMembership(
            SectorAssetClassificationEvidence classification,
            String suffix) {
        return membership(classification, classification.basis(),
                classification.assetId(), classification.assetType(),
                classification.primaryVenueId(),
                classification.primaryVenueCountryCode(), classification.currency(),
                PROVIDER_ID, PROVIDER_SCHEME_ID, PROVIDER_SCHEME_REVISION,
                PROVIDER_NODE_ID, PROVIDER_NODE_LABEL, exactInterval(), AS_OF, AS_OF,
                suffix);
    }

    private static SectorMembershipEvidence membership(
            SectorAssetClassificationEvidence classification,
            OutcomeBasis basis,
            String assetId,
            AssetType assetType,
            String venue,
            String country,
            Currency currency,
            String providerId,
            String providerSchemeId,
            String providerSchemeRevision,
            String providerNodeId,
            String providerNodeLabel,
            EffectiveInterval interval,
            Instant availableAt,
            Instant capturedAt,
            String suffix) {
        return new SectorMembershipEvidence(
                "membership-" + suffix, "membership-event-" + suffix,
                basis, assetId, assetType, venue, country, currency,
                providerId, providerSchemeId, providerSchemeRevision,
                providerNodeId, providerNodeLabel, "membership-source",
                "membership-revision", "provenance", interval, availableAt,
                capturedAt);
    }

    private static SectorMappingEvidence exactMapping(
            SectorMembershipEvidence membership,
            String canonicalNodeId,
            String suffix) {
        return mapping(membership, exactInterval(), recorded(),
                new Mapped(canonicalNodeId), AS_OF, AS_OF, suffix);
    }

    private static SectorMappingEvidence mapping(
            SectorMembershipEvidence membership,
            EffectiveInterval interval,
            ProviderNodeDefinition definition,
            MappingDisposition disposition,
            Instant availableAt,
            Instant capturedAt,
            String suffix) {
        return mappingWith(membership, MAPPING_SET_ID, MAPPING_SET_VERSION,
                MAPPING_SET_HASH, MAPPING_POLICY_VERSION, MAPPING_POLICY_HASH,
                TAXONOMY_ID, TAXONOMY_VERSION, TAXONOMY_HASH,
                membership.providerId(), membership.providerSchemeId(),
                membership.providerSchemeRevision(), membership.providerNodeId(),
                "Mapping label " + suffix, interval, definition, disposition,
                availableAt, capturedAt, suffix);
    }

    private static SectorMappingEvidence mappingWith(
            SectorMembershipEvidence membership,
            String mappingSetId,
            String mappingSetVersion,
            String mappingSetHash,
            String mappingPolicyVersion,
            String mappingPolicyHash,
            String taxonomyId,
            String taxonomyVersion,
            String taxonomyHash,
            String providerId,
            String providerSchemeId,
            String providerSchemeRevision,
            String providerNodeId,
            String providerNodeLabel,
            EffectiveInterval interval,
            ProviderNodeDefinition definition,
            MappingDisposition disposition,
            Instant availableAt,
            Instant capturedAt,
            String suffix) {
        return new SectorMappingEvidence(
                "mapping-" + suffix, "mapping-event-" + suffix,
                mappingPolicyVersion, mappingPolicyHash, mappingSetId,
                mappingSetVersion, mappingSetHash, taxonomyId, taxonomyVersion,
                taxonomyHash, providerId, providerSchemeId, providerSchemeRevision,
                providerNodeId, providerNodeLabel, definition, disposition,
                "mapping-source", "mapping-revision", "provenance", interval,
                availableAt, capturedAt);
    }

    private static SectorAssetClassificationEvidence classificationFault(
            ClassificationFault fault) {
        return switch (fault) {
            case BASIS -> classification(
                    new Correction("wrong", "wrong", BASIS_TIME), ASSET_ID,
                    AssetType.EQUITY, PRIMARY_VENUE_ID, US, USD, exactInterval(),
                    AS_OF, AS_OF, "basis-fault");
            case ASSET -> classification(
                    ORIGINAL, "wrong-asset", AssetType.EQUITY, PRIMARY_VENUE_ID,
                    US, USD, exactInterval(), AS_OF, AS_OF, "asset-fault");
            case INTERVAL -> classification(
                    ORIGINAL, ASSET_ID, AssetType.EQUITY, PRIMARY_VENUE_ID,
                    US, USD, bounded(INTERVAL_START, BASIS_TIME), AS_OF, AS_OF,
                    "interval-fault");
        };
    }

    private static SectorMembershipEvidence membershipFault(
            SectorAssetClassificationEvidence classification,
            MembershipFault fault) {
        OutcomeBasis basis = fault == MembershipFault.BASIS
                ? new Correction("wrong", "wrong", BASIS_TIME)
                : ORIGINAL;
        return membership(
                classification,
                basis,
                fault == MembershipFault.ASSET ? "wrong-asset" : ASSET_ID,
                fault == MembershipFault.ASSET_TYPE ? AssetType.INDEX : AssetType.EQUITY,
                fault == MembershipFault.VENUE ? "wrong-venue" : PRIMARY_VENUE_ID,
                fault == MembershipFault.COUNTRY ? CA : US,
                fault == MembershipFault.CURRENCY ? EUR : USD,
                PROVIDER_ID, PROVIDER_SCHEME_ID, PROVIDER_SCHEME_REVISION,
                PROVIDER_NODE_ID, PROVIDER_NODE_LABEL,
                fault == MembershipFault.INTERVAL
                        ? bounded(INTERVAL_START, BASIS_TIME) : exactInterval(),
                AS_OF, AS_OF, fault.name());
    }

    private static SectorMappingEvidence mappingFault(
            SectorMembershipEvidence membership,
            MappingFault fault) {
        return mappingWith(
                membership,
                fault == MappingFault.SET_ID ? "wrong-set" : MAPPING_SET_ID,
                fault == MappingFault.SET_VERSION ? "wrong-version" : MAPPING_SET_VERSION,
                fault == MappingFault.SET_HASH ? "b".repeat(64) : MAPPING_SET_HASH,
                fault == MappingFault.POLICY_VERSION
                        ? "wrong-policy" : MAPPING_POLICY_VERSION,
                fault == MappingFault.POLICY_HASH
                        ? "c".repeat(64) : MAPPING_POLICY_HASH,
                fault == MappingFault.TAXONOMY_ID ? "wrong-taxonomy" : TAXONOMY_ID,
                fault == MappingFault.TAXONOMY_VERSION
                        ? "2.0.0" : TAXONOMY_VERSION,
                fault == MappingFault.TAXONOMY_HASH
                        ? "d".repeat(64) : TAXONOMY_HASH,
                fault == MappingFault.PROVIDER_ID
                        ? "wrong-provider" : membership.providerId(),
                fault == MappingFault.PROVIDER_SCHEME_ID
                        ? "wrong-scheme" : membership.providerSchemeId(),
                fault == MappingFault.PROVIDER_SCHEME_REVISION
                        ? "wrong-revision" : membership.providerSchemeRevision(),
                fault == MappingFault.PROVIDER_NODE_ID
                        ? "wrong-node" : membership.providerNodeId(),
                "Mapping label",
                fault == MappingFault.INTERVAL
                        ? bounded(INTERVAL_START, BASIS_TIME) : exactInterval(),
                fault == MappingFault.MAPPED_DEFINITION
                        ? new NotPublished() : recorded(),
                new Mapped(fault == MappingFault.CANONICAL_NODE
                        ? "wsr-sector-root" : DIGITAL_SYSTEMS),
                AS_OF, AS_OF, fault.name());
    }

    private static SectorMappingEvidence mappingWithInvalidHash(
            SectorMembershipEvidence membership,
            String field,
            String invalid) {
        return mappingWith(
                membership, MAPPING_SET_ID, MAPPING_SET_VERSION,
                field.equals("mappingSetDefinitionHash") ? invalid : MAPPING_SET_HASH,
                MAPPING_POLICY_VERSION,
                field.equals("mappingPolicyDefinitionHash")
                        ? invalid : MAPPING_POLICY_HASH,
                TAXONOMY_ID, TAXONOMY_VERSION,
                field.equals("taxonomyDefinitionHash") ? invalid : TAXONOMY_HASH,
                membership.providerId(), membership.providerSchemeId(),
                membership.providerSchemeRevision(), membership.providerNodeId(),
                PROVIDER_NODE_LABEL, exactInterval(), recorded(),
                new Mapped(DIGITAL_SYSTEMS), AS_OF, AS_OF, "invalid-hash");
    }

    private static SectorAssetClassificationEvidence classificationWithText(
            String field,
            String value) {
        return new SectorAssetClassificationEvidence(
                field.equals("classificationEvidenceId") ? value : "classification",
                "event", ORIGINAL, ASSET_ID, AssetType.EQUITY, PRIMARY_VENUE_ID,
                US, USD, "source", "revision", "provenance", exactInterval(),
                AS_OF, AS_OF);
    }

    private static SectorMembershipEvidence membershipWithText(
            SectorAssetClassificationEvidence classification,
            String field,
            String value) {
        return new SectorMembershipEvidence(
                field.equals("membershipEvidenceId") ? value : "membership",
                "event", ORIGINAL, ASSET_ID, AssetType.EQUITY, PRIMARY_VENUE_ID,
                US, USD, field.equals("providerId") ? value : PROVIDER_ID,
                PROVIDER_SCHEME_ID, PROVIDER_SCHEME_REVISION, PROVIDER_NODE_ID,
                PROVIDER_NODE_LABEL, "source", "revision", "provenance",
                exactInterval(), AS_OF, AS_OF);
    }

    private static SectorMappingEvidence mappingWithText(
            SectorMembershipEvidence membership,
            String field,
            String value) {
        return new SectorMappingEvidence(
                field.equals("mappingEvidenceId") ? value : "mapping", "event",
                MAPPING_POLICY_VERSION, MAPPING_POLICY_HASH, MAPPING_SET_ID,
                MAPPING_SET_VERSION, MAPPING_SET_HASH, TAXONOMY_ID,
                TAXONOMY_VERSION, TAXONOMY_HASH,
                field.equals("providerId") ? value : membership.providerId(),
                membership.providerSchemeId(), membership.providerSchemeRevision(),
                membership.providerNodeId(), PROVIDER_NODE_LABEL, recorded(),
                new Mapped(DIGITAL_SYSTEMS), "source", "revision", "provenance",
                exactInterval(), AS_OF, AS_OF);
    }

    private static SectorAssignmentRequest request(
            OutcomeBasis basis,
            Instant evaluationAsOf,
            String mappingSetId,
            String mappingSetVersion,
            String mappingSetHash,
            List<SectorAssetClassificationEvidence> classifications,
            List<SectorMembershipEvidence> memberships,
            List<SectorMappingEvidence> mappings) {
        return new SectorAssignmentRequest(
                policy(), basis, ASSET_ID, evaluationAsOf, mappingSetId,
                mappingSetVersion, mappingSetHash, classifications, memberships,
                mappings);
    }

    private static SectorAssignmentResolution select(
            OutcomeBasis basis,
            List<SectorAssetClassificationEvidence> classifications,
            List<SectorMembershipEvidence> memberships,
            List<SectorMappingEvidence> mappings) {
        return SectorAssignmentSelector.select(request(
                basis, AS_OF, MAPPING_SET_ID, MAPPING_SET_VERSION, MAPPING_SET_HASH,
                classifications, memberships, mappings));
    }

    private static ResolutionContext context(OutcomeBasis basis) {
        return new ResolutionContext(
                policy(), POLICY_HASH, basis, ASSET_ID, AS_OF,
                MAPPING_SET_ID, MAPPING_SET_VERSION, MAPPING_SET_HASH);
    }

    private static Resolved assertResolved(SectorAssignmentResolution result) {
        assertThat(result).isInstanceOf(Resolved.class);
        return (Resolved) result;
    }

    private static NotApplicable assertNotApplicable(
            SectorAssignmentResolution result,
            NotApplicableReason reason) {
        assertThat(result).isInstanceOf(NotApplicable.class);
        NotApplicable notApplicable = (NotApplicable) result;
        assertThat(notApplicable.reason()).isEqualTo(reason);
        return notApplicable;
    }

    private static Unavailable assertUnavailable(
            SectorAssignmentResolution result,
            UnavailableReason reason) {
        assertThat(result).isInstanceOf(Unavailable.class);
        Unavailable unavailable = (Unavailable) result;
        assertThat(unavailable.reason()).isEqualTo(reason);
        return unavailable;
    }

    private static void assertRecordComponents(
            Class<?> type,
            String... expected) {
        assertThat(type.getRecordComponents())
                .extracting(component -> component.getName() + ":"
                        + component.getType().getSimpleName())
                .containsExactly(expected);
    }
}
