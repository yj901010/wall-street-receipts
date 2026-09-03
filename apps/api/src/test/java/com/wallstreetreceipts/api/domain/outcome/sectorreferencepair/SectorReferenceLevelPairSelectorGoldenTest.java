package com.wallstreetreceipts.api.domain.outcome.sectorreferencepair;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.RecordComponent;
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
import java.util.function.UnaryOperator;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import com.wallstreetreceipts.api.domain.master.AssetType;
import com.wallstreetreceipts.api.domain.outcome.OutcomeHorizon;
import com.wallstreetreceipts.api.domain.outcome.sectorassignment.SectorAssetClassificationEvidence;
import com.wallstreetreceipts.api.domain.outcome.sectorassignment.SectorAssignmentPolicyVersion;
import com.wallstreetreceipts.api.domain.outcome.sectorassignment.SectorAssignmentResolution;
import com.wallstreetreceipts.api.domain.outcome.sectorassignment.SectorMappingEvidence;
import com.wallstreetreceipts.api.domain.outcome.sectorassignment.SectorMappingEvidence.Mapped;
import com.wallstreetreceipts.api.domain.outcome.sectorassignment.SectorMappingEvidence.Recorded;
import com.wallstreetreceipts.api.domain.outcome.sectorassignment.SectorMembershipEvidence;
import com.wallstreetreceipts.api.domain.outcome.sectorreferencepair.SectorIndexDivisorContinuityEvidence.DivisorContinuity;
import com.wallstreetreceipts.api.domain.outcome.sectorreferencepair.SectorReferenceIndexEvidence.EffectiveInterval;
import com.wallstreetreceipts.api.domain.outcome.sectorreferencepair.SectorReferenceIndexEvidence.EndsAtExclusive;
import com.wallstreetreceipts.api.domain.outcome.sectorreferencepair.SectorReferenceIndexEvidence.OpenEnded;
import com.wallstreetreceipts.api.domain.outcome.sectorreferencepair.SectorReferenceIndexEvidence.ReferenceIndexKind;
import com.wallstreetreceipts.api.domain.outcome.sectorreferencepair.SectorReferenceLevelObservation.ReferenceLevelField;
import com.wallstreetreceipts.api.domain.outcome.sectorreferencepair.SectorReferenceLevelPairResolution.EvidenceUnavailable;
import com.wallstreetreceipts.api.domain.outcome.sectorreferencepair.SectorReferenceLevelPairResolution.Resolved;
import com.wallstreetreceipts.api.domain.outcome.sectorreferencepair.SectorReferenceLevelPairResolution.UnavailableReason;
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

class SectorReferenceLevelPairSelectorGoldenTest {

    private static final SectorReferenceLevelPairPolicyVersion POLICY =
            SectorReferenceLevelPairPolicyVersion
                    .POINT_IN_TIME_EXACT_SECTOR_PRICE_INDEX_LEVEL_PAIR_V1;
    private static final SectorAssignmentPolicyVersion ASSIGNMENT_POLICY =
            SectorAssignmentPolicyVersion
                    .POINT_IN_TIME_EXPLICIT_WSR_ECONOMIC_ACTIVITY_SECTOR_ASSIGNMENT_V1;
    private static final String ASSET_ID = "asset-nvda";
    private static final String CANONICAL_NODE_ID =
            "wsr-sector-digital-systems";
    private static final String REFERENCE_ASSET_ID =
            "asset-wsr-digital-systems-price-index";
    private static final String VENUE_ID = "venue-xnas";
    private static final Currency USD = Currency.getInstance("USD");
    private static final Currency CAD = Currency.getInstance("CAD");
    private static final Currency EUR = Currency.getInstance("EUR");
    private static final Instant BASIS_TIME =
            Instant.parse("2026-08-20T14:00:00Z");
    private static final Instant ENDPOINT_OPEN =
            Instant.parse("2026-08-21T13:30:00Z");
    private static final Instant ENDPOINT_CLOSE =
            Instant.parse("2026-08-21T20:00:00Z");
    private static final Instant AS_OF =
            Instant.parse("2026-08-22T00:00:00Z");
    private static final OutcomeBasis ORIGINAL =
            new OutcomeBasis.Original("call-adr030-sector", BASIS_TIME);
    private static final String ASSIGNMENT_HASH =
            "52d9f705a3a8a965a6fca79d36bd94ed8836642f1a2c4e5f29a878d0a267311c";
    private static final String ENDPOINT_HASH =
            "37e37aba9302d77366cef4129f77a82b7ccb2f1937bfffc0315ea8d0bc6b1f76";
    private static final String HORIZON_HASH =
            "550087efe7ddf2ba31974c89c2740ab79df986eefef48919c32c56a3232f8dc1";
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
    private static final List<String> CANONICAL_LEAVES = List.of(
            CANONICAL_NODE_ID,
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

    @Test
    void canonicalDefinitionIsByteStableAndHashed() {
        byte[] first = POLICY.canonicalDefinitionUtf8();
        byte[] second = POLICY.canonicalDefinitionUtf8();

        assertThat(first).isNotSameAs(second).containsExactly(second);
        assertThat(new String(first, StandardCharsets.UTF_8))
                .isEqualTo(POLICY.canonicalDefinition());
        assertThat(POLICY.canonicalDefinition().chars())
                .allMatch(value -> value >= 0 && value <= 127);
        assertThat(POLICY.definitionHash()).hasSize(64)
                .matches("[0-9a-f]{64}")
                .isEqualTo(sha256(first))
                .isEqualTo("4224648ba01104fd3e96319158c7d6b42da472e9aa6f2ab22ef9fccf43da7e4a");
        assertThat(first).hasSize(9806);
        assertThat(POLICY.canonicalDefinition())
                .contains("\"directOutcomeBasisOrHorizonInput\":false")
                .contains("\"assetReturnPricePairReuse\":\"FORBIDDEN\"")
                .contains("\"referenceReturnCalculation\":\"ABSENT\"")
                .contains("\"EndpointAnchorUnavailable\"");
    }

    @Test
    void publicShapesAndEnumsAreClosed() {
        assertThat(SectorReferenceLevelPairResolution.class
                .getPermittedSubclasses())
                .extracting(Class::getSimpleName)
                .containsExactly("Resolved", "NotApplicable",
                        "AssignmentUnavailable", "EndpointAnchorUnavailable",
                        "EvidenceUnavailable");
        assertThat(ReferenceIndexKind.values()).extracting(Enum::name)
                .containsExactly("PROVIDER_PUBLISHED_PRICE_INDEX",
                        "PROVIDER_PUBLISHED_TOTAL_RETURN_INDEX",
                        "NON_PROVIDER_PUBLISHED_PRICE_INDEX",
                        "EXCHANGE_TRADED_FUND", "CURRENT_CONSTITUENT_BASKET",
                        "MARKET_CAP_PROXY", "PROVIDER_RETURN_FIELD", "UNKNOWN");
        assertThat(ReferenceLevelField.values()).extracting(Enum::name)
                .containsExactly("PROVIDER_PUBLISHED_INDEX_LEVEL",
                        "PROVIDER_PUBLISHED_RETURN",
                        "EXCHANGE_TRADED_FUND_MARKET_PRICE",
                        "EXCHANGE_TRADED_FUND_NAV", "DERIVED_PROXY_LEVEL",
                        "UNKNOWN");
        assertThat(DivisorContinuity.values()).extracting(Enum::name)
                .containsExactly(
                        "PROVIDER_PUBLISHED_INDEX_DIVISOR_CONTINUITY_ATTESTED",
                        "DIVISOR_DISCONTINUITY", "NOT_ATTESTED", "UNKNOWN");
        assertThat(UnavailableReason.values()).hasSize(56);
    }

    @Test
    void resolvesAndPreservesExactReceipts() {
        Fixture fixture = fixture();

        Resolved result = assertResolved(fixture);

        assertThat(result.context().assignmentResolution())
                .isSameAs(fixture.assignment());
        assertThat(result.context().endpointPriceResolution())
                .isSameAs(fixture.endpoint());
        assertThat(result.referenceIndexEvidence())
                .isSameAs(fixture.references().getFirst());
        assertThat(result.basisLevelObservation())
                .isSameAs(fixture.basisLevels().getFirst());
        assertThat(result.endpointLevelObservation())
                .isSameAs(fixture.endpointLevels().getFirst());
        assertThat(result.divisorContinuityEvidence())
                .isSameAs(fixture.continuities().getFirst());
    }

    @Test
    void requestCopiesCandidateListsAndRejectsNulls() {
        Fixture fixture = fixture();
        ArrayList<SectorReferenceIndexEvidence> mutable =
                new ArrayList<>(fixture.references());
        SectorReferenceLevelPairRequest request = new SectorReferenceLevelPairRequest(
                POLICY, fixture.assignment(), fixture.endpoint(), mutable,
                fixture.basisLevels(), fixture.endpointLevels(),
                fixture.continuities());
        mutable.clear();

        assertThat(request.referenceIndexCandidates()).hasSize(1);
        assertThatThrownBy(() -> request.referenceIndexCandidates().clear())
                .isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> new SectorReferenceLevelPairRequest(
                POLICY, fixture.assignment(), fixture.endpoint(), null,
                List.of(), List.of(), List.of()))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new SectorReferenceLevelPairRequest(
                POLICY, fixture.assignment(), fixture.endpoint(),
                Arrays.asList((SectorReferenceIndexEvidence) null),
                List.of(), List.of(), List.of()))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> SectorReferenceLevelPairSelector.select(null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void evidenceConstructorsRejectInvalidTemporalAndNumericValues() {
        Fixture fixture = fixture();
        Instant future = AS_OF.plusSeconds(1);

        assertThatThrownBy(() -> withRecord(fixture.references().getFirst(),
                "capturedAt", BASIS_TIME.minusSeconds(1)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> withRecord(fixture.basisLevels().getFirst(),
                "capturedAt", BASIS_TIME))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> withRecord(fixture.endpointLevels().getFirst(),
                "availableAt", ENDPOINT_CLOSE.minusSeconds(1)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> withRecord(fixture.continuities().getFirst(),
                "availableAt", ENDPOINT_CLOSE.minusSeconds(1)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> withRecord(fixture.basisLevels().getFirst(),
                "level", BigDecimal.ZERO))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> withRecord(fixture.basisLevels().getFirst(),
                "level", new BigDecimal("1.0000000000001")))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new EffectiveInterval(future,
                new EndsAtExclusive(future)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void resultConstructorsEnforceTypedTopologyAndFutureEvidence() {
        Fixture fixture = fixture();
        var context = context(fixture.assignment(), fixture.endpoint());
        assertThat(new Resolved(context, fixture.references().getFirst(),
                fixture.basisLevels().getFirst(), fixture.endpointLevels().getFirst(),
                fixture.continuities().getFirst())).isNotNull();

        SectorReferenceIndexEvidence future = withRecord(
                fixture.references().getFirst(), "capturedAt", AS_OF.plusSeconds(1));
        future = withRecord(future, "availableAt", AS_OF.plusSeconds(1));
        SectorReferenceIndexEvidence finalFuture = future;
        assertThatThrownBy(() -> new Resolved(context, finalFuture,
                fixture.basisLevels().getFirst(), fixture.endpointLevels().getFirst(),
                fixture.continuities().getFirst()))
                .isInstanceOf(IllegalArgumentException.class);

        for (var reason : SectorReferenceLevelPairResolution
                .EndpointAnchorUnavailableReason.values()) {
            EndpointPriceResolution anchorEndpoint = factualAnchorEndpoint(
                    fixture.endpoint(), reason);
            var anchorContext = context(fixture.assignment(), anchorEndpoint);
            assertThat(new SectorReferenceLevelPairResolution
                    .EndpointAnchorUnavailable(anchorContext, reason)).isNotNull();
            SectorReferenceLevelPairResolution selected =
                    SectorReferenceLevelPairSelector.select(
                            request(fixture.withEndpoint(anchorEndpoint)));
            assertThat(selected).isEqualTo(new SectorReferenceLevelPairResolution
                    .EndpointAnchorUnavailable(anchorContext, reason));
            assertThatThrownBy(() -> new Resolved(anchorContext,
                    fixture.references().getFirst(), fixture.basisLevels().getFirst(),
                    fixture.endpointLevels().getFirst(),
                    fixture.continuities().getFirst()))
                    .isInstanceOf(IllegalArgumentException.class);
            var wrong = reason == SectorReferenceLevelPairResolution
                    .EndpointAnchorUnavailableReason.CATALOG_NOT_KNOWN_AS_OF
                            ? SectorReferenceLevelPairResolution
                                    .EndpointAnchorUnavailableReason
                                    .CATALOG_EVIDENCE_MISMATCH
                            : SectorReferenceLevelPairResolution
                                    .EndpointAnchorUnavailableReason
                                    .CATALOG_NOT_KNOWN_AS_OF;
            assertThatThrownBy(() -> new SectorReferenceLevelPairResolution
                    .EndpointAnchorUnavailable(anchorContext, wrong))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        assertThatThrownBy(() -> new SectorReferenceLevelPairResolution
                .EndpointAnchorUnavailable(context(fixture.assignment(),
                        fixture.endpoint()), SectorReferenceLevelPairResolution
                                .EndpointAnchorUnavailableReason
                                .CATALOG_NOT_KNOWN_AS_OF))
                .isInstanceOf(IllegalArgumentException.class);

        SectorAssignmentResolution.NotApplicable notApplicable =
                sectorNotApplicable(AssetType.ETF, "US", USD, VENUE_ID);
        EndpointPriceResolution matchingEndpoint = endpoint(ORIGINAL, ASSET_ID,
                VENUE_ID, USD, AS_OF, ENDPOINT_CLOSE, null);
        var notApplicableContext = context(notApplicable, matchingEndpoint);
        assertThat(new SectorReferenceLevelPairResolution.NotApplicable(
                notApplicableContext)).isNotNull();
        assertThatThrownBy(() -> new SectorReferenceLevelPairResolution
                .EndpointAnchorUnavailable(notApplicableContext,
                        SectorReferenceLevelPairResolution
                                .EndpointAnchorUnavailableReason
                                .CATALOG_NOT_KNOWN_AS_OF))
                .isInstanceOf(IllegalArgumentException.class);
        EndpointPriceResolution notApplicableAnchorEndpoint = factualAnchorEndpoint(
                matchingEndpoint, SectorReferenceLevelPairResolution
                        .EndpointAnchorUnavailableReason.BINDING_NOT_KNOWN_AS_OF);
        assertThatThrownBy(() -> new SectorReferenceLevelPairResolution
                .EndpointAnchorUnavailable(context(notApplicable,
                        notApplicableAnchorEndpoint),
                        SectorReferenceLevelPairResolution
                                .EndpointAnchorUnavailableReason
                                .BINDING_NOT_KNOWN_AS_OF))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new EvidenceUnavailable(notApplicableContext,
                UnavailableReason.REFERENCE_INDEX_MISSING_AS_OF))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void requestRequiresExactResolvedContextTopology() {
        Fixture fixture = fixture();

        assertThatThrownBy(() -> request(
                fixture.withAssignment(sectorResolved(ORIGINAL, "asset-other",
                        VENUE_ID, USD, AS_OF))))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> request(
                fixture.withAssignment(sectorResolved(ORIGINAL, ASSET_ID,
                        "venue-xnys", USD, AS_OF))))
                .isInstanceOf(IllegalArgumentException.class);
        EndpointPriceResolution eurEndpoint = endpoint(ORIGINAL, ASSET_ID,
                VENUE_ID, EUR, AS_OF, ENDPOINT_CLOSE,
                EndpointPriceResolution.UnavailableReason.OBSERVATION_MISSING_AS_OF);
        assertThatThrownBy(() -> request(fixture.withEndpoint(eurEndpoint)))
                .isInstanceOf(IllegalArgumentException.class);
        SectorAssignmentResolution.Resolved differentAsOf = sectorResolved(
                ORIGINAL, ASSET_ID, VENUE_ID, USD, AS_OF.plusSeconds(1));
        assertThatThrownBy(() -> request(
                fixture.withAssignment(differentAsOf)))
                .isInstanceOf(IllegalArgumentException.class);
        OutcomeBasis correction = new OutcomeBasis.Correction(
                ORIGINAL.callId(), "revision-mismatch", BASIS_TIME.plusSeconds(1));
        assertThatThrownBy(() -> request(fixture.withAssignment(
                sectorResolved(correction, ASSET_ID, VENUE_ID, USD, AS_OF))))
                .isInstanceOf(IllegalArgumentException.class);

        SectorAssignmentResolution.Resolved cadAssignment = sectorResolved(
                ORIGINAL, ASSET_ID, "venue-xtse", "CA", CAD, AS_OF,
                CANONICAL_NODE_ID);
        EndpointPriceResolution cadEndpoint = endpoint(ORIGINAL, ASSET_ID,
                "venue-xtse", CAD, AS_OF, ENDPOINT_CLOSE, null);
        assertThat(assertResolved(fixture(cadAssignment, cadEndpoint))
                .referenceIndexEvidence().currency()).isEqualTo(CAD);
    }

    @Test
    void assignmentUnavailableTopologyUsesOnlyProvableFields() {
        SectorAssignmentResolution.Unavailable assignment =
                new SectorAssignmentResolution.Unavailable(
                        assignmentContext(ORIGINAL, ASSET_ID, AS_OF),
                        SectorAssignmentResolution.UnavailableReason
                                .CLASSIFICATION_MISSING_AS_OF);
        EndpointPriceResolution endpoint = endpoint(ORIGINAL, ASSET_ID,
                "venue-xtse", CAD, AS_OF, ENDPOINT_CLOSE,
                EndpointPriceResolution.UnavailableReason.CURRENCY_MISMATCH);

        SectorReferenceLevelPairResolution result =
                SectorReferenceLevelPairSelector.select(
                        new SectorReferenceLevelPairRequest(POLICY, assignment,
                                endpoint, List.of(), List.of(), List.of(),
                                List.of()));

        assertThat(result).isInstanceOf(
                SectorReferenceLevelPairResolution.AssignmentUnavailable.class);
        assertThat(((SectorReferenceLevelPairResolution.AssignmentUnavailable)
                result).context().assignmentResolution()).isSameAs(assignment);
    }

    @Test
    void mixedPointInTimePredicateRequiresBothTimestamps() {
        Fixture fixture = fixture();
        Instant future = AS_OF.plusSeconds(1);

        assertUnavailable(mapReference(fixture, "capturedAt", future),
                UnavailableReason.REFERENCE_INDEX_MISSING_AS_OF);
        assertUnavailable(mapBasis(fixture, "capturedAt", future),
                UnavailableReason.BASIS_LEVEL_MISSING_AS_OF);
        assertUnavailable(mapEndpoint(fixture, "capturedAt", future),
                UnavailableReason.ENDPOINT_LEVEL_MISSING_AS_OF);
        assertUnavailable(mapContinuity(fixture, "capturedAt", future),
                UnavailableReason.DIVISOR_CONTINUITY_EVIDENCE_MISSING_AS_OF);

        assertThatThrownBy(() -> withRecord(fixture.references().getFirst(),
                "availableAt", future))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> withRecord(fixture.basisLevels().getFirst(),
                "availableAt", future))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void providerIdentityIsRawAndLabelIsNotAKey() {
        Fixture fixture = coherentProviderIdentity(fixture(),
                " provider-É\u202f", " index-ß ", " rev-İ ");
        fixture = mapReference(fixture, "referenceIndexLabel",
                "Changed label does not participate in identity");

        assertResolved(fixture);
        assertUnavailable(mapBasis(fixture, "referenceProviderId",
                "provider-é\u202f"),
                UnavailableReason.BASIS_REFERENCE_PROVIDER_MISMATCH);
        assertUnavailable(mapEndpoint(fixture, "referenceIndexId",
                " index-SS "),
                UnavailableReason.ENDPOINT_REFERENCE_INDEX_MISMATCH);
        assertUnavailable(mapContinuity(fixture,
                "referenceIndexDefinitionRevision", " rev-i "),
                UnavailableReason
                        .DIVISOR_REFERENCE_INDEX_DEFINITION_REVISION_MISMATCH);
    }

    @Test
    void selectionReplaysAcrossLocaleTimezoneAndCandidateOrder() {
        Fixture fixture = fixture();
        SectorReferenceIndexEvidence future = mapReference(fixture,
                "capturedAt", AS_OF.plusSeconds(1)).references().getFirst();
        Fixture ordered = fixture.withReferences(List.of(future,
                fixture.references().getFirst()));
        Fixture reversed = fixture.withReferences(List.of(
                fixture.references().getFirst(), future));
        Locale priorLocale = Locale.getDefault();
        TimeZone priorZone = TimeZone.getDefault();
        try {
            Locale.setDefault(Locale.forLanguageTag("tr-TR"));
            TimeZone.setDefault(TimeZone.getTimeZone("Pacific/Apia"));
            assertThat(SectorReferenceLevelPairSelector.select(request(ordered)))
                    .isEqualTo(SectorReferenceLevelPairSelector.select(
                            request(reversed)));
            assertResolved(ordered);
        } finally {
            Locale.setDefault(priorLocale);
            TimeZone.setDefault(priorZone);
        }
    }

    @Test
    void originalAndCorrectionBasisReplayIndependently() {
        OutcomeBasis correction = new OutcomeBasis.Correction(
                ORIGINAL.callId(), "correction-001", BASIS_TIME.plusSeconds(300));
        Fixture originalFixture = fixture(ORIGINAL);
        Fixture correctionFixture = fixture(correction);

        Resolved original = assertResolved(originalFixture);
        Resolved corrected = assertResolved(correctionFixture);

        assertThat(original.context().assignmentResolution()).isNotEqualTo(
                corrected.context().assignmentResolution());
        assertThat(endpointContext(corrected.context().endpointPriceResolution())
                .horizonResolution().window().context().basis())
                .isSameAs(correction);
        assertThat(corrected.basisLevelObservation().observedAt())
                .isEqualTo(correction.eventTime());
    }

    @Test
    void levelsArePreservedWithoutReturnCalculation() {
        Fixture fixture = fixture();
        SectorReferenceLevelObservation basis = withRecord(
                fixture.basisLevels().getFirst(), "level",
                new BigDecimal("4321.123456789012"));
        SectorReferenceLevelObservation endpoint = withRecord(
                fixture.endpointLevels().getFirst(), "level",
                new BigDecimal("4567.987654321098"));
        fixture = fixture.withBasisLevels(List.of(basis))
                .withEndpointLevels(List.of(endpoint));

        Resolved result = assertResolved(fixture);

        assertThat(result.basisLevelObservation().level())
                .isEqualByComparingTo("4321.123456789012");
        assertThat(result.endpointLevelObservation().level())
                .isEqualByComparingTo("4567.987654321098");
        assertThat(Stream.of(Resolved.class.getRecordComponents())
                .map(RecordComponent::getName))
                .noneMatch(name -> name.toLowerCase(Locale.ROOT)
                        .contains("return"));
    }

    @Test
    void equalDuplicatesAreAmbiguousAtEveryStage() {
        Fixture fixture = fixture();
        assertUnavailable(duplicateReferences(fixture),
                UnavailableReason.REFERENCE_INDEX_AMBIGUOUS);
        assertUnavailable(duplicateBasis(fixture),
                UnavailableReason.BASIS_LEVEL_AMBIGUOUS);
        assertUnavailable(duplicateEndpoint(fixture),
                UnavailableReason.ENDPOINT_LEVEL_AMBIGUOUS);
        assertUnavailable(duplicateContinuity(fixture),
                UnavailableReason.DIVISOR_CONTINUITY_EVIDENCE_AMBIGUOUS);
    }

    @Test
    void futureFaultsAreInvisibleAndNeverEchoed() {
        Fixture fixture = fixture();
        SectorReferenceIndexEvidence faulty = withRecord(
                fixture.references().getFirst(), "canonicalNodeId",
                "wsr-sector-bad-leaf");
        faulty = withRecord(faulty, "capturedAt", AS_OF.plusSeconds(1));
        faulty = withRecord(faulty, "availableAt", AS_OF.plusSeconds(1));
        Fixture withFutureFault = fixture.withReferences(List.of(faulty,
                fixture.references().getFirst()));

        Resolved result = assertResolved(withFutureFault);

        assertThat(result.referenceIndexEvidence()).isNotSameAs(faulty);
        assertThat(result.toString()).doesNotContain("wsr-sector-bad-leaf");
    }

    @Test
    void endpointMaturityComesFromNestedContextNotUpstreamReason() {
        Fixture fixture = fixture();
        for (EndpointPriceResolution.UnavailableReason reason : List.of(
                EndpointPriceResolution.UnavailableReason.ENDPOINT_NOT_REACHED_AS_OF,
                EndpointPriceResolution.UnavailableReason.OBSERVATION_MISSING_AS_OF,
                EndpointPriceResolution.UnavailableReason.OBSERVATION_AMBIGUOUS)) {
            EndpointPriceResolution unavailable = unavailableEndpoint(
                    endpointContext(fixture.endpoint()), reason);
            assertResolved(fixture.withEndpoint(unavailable));
        }

        EndpointPriceResolution futureEndpoint = endpoint(ORIGINAL, ASSET_ID,
                VENUE_ID, USD, AS_OF, AS_OF.plusSeconds(60),
                EndpointPriceResolution.UnavailableReason.OBSERVATION_MISSING_AS_OF);
        assertUnavailable(fixture.withEndpoint(futureEndpoint),
                UnavailableReason.ENDPOINT_NOT_REACHED_AS_OF);
    }

    @Test
    void chainLinksAreExactAndProviderEventsMatter() {
        Fixture fixture = fixture();
        assertUnavailable(mapReference(fixture, "mappingProviderEventId",
                "mapping-provider-event-other"),
                UnavailableReason.REFERENCE_MAPPING_EVIDENCE_LINK_MISMATCH);
        assertUnavailable(mapBasis(fixture, "referenceIndexProviderEventId",
                "reference-provider-event-other"),
                UnavailableReason.BASIS_REFERENCE_INDEX_EVIDENCE_LINK_MISMATCH);
        assertUnavailable(mapEndpoint(fixture, "referenceIndexEvidenceId",
                "reference-evidence-other"),
                UnavailableReason.ENDPOINT_REFERENCE_INDEX_EVIDENCE_LINK_MISMATCH);
        assertUnavailable(mapContinuity(fixture,
                "endpointProviderEventId", "level-provider-event-other"),
                UnavailableReason.DIVISOR_ENDPOINT_OBSERVATION_LINK_MISMATCH);
        assertUnavailable(mapBasis(fixture, "calendarSourceId",
                "calendar-source-other"),
                UnavailableReason.BASIS_CALENDAR_MISMATCH);
        assertUnavailable(mapEndpoint(fixture, "calendarSourceRevision",
                "calendar-source-revision-other"),
                UnavailableReason.ENDPOINT_CALENDAR_MISMATCH);
        assertUnavailable(mapContinuity(fixture, "calendarSourceId",
                "calendar-source-other"),
                UnavailableReason.DIVISOR_CALENDAR_MISMATCH);
    }

    @Test
    void allTwelveCanonicalLeavesResolveOnlyByExactMappingIdentity() {
        assertThat(CANONICAL_LEAVES).hasSize(12);
        for (String leaf : CANONICAL_LEAVES) {
            Fixture fixture = fixture(ORIGINAL, leaf);
            assertThat(assertResolved(fixture).referenceIndexEvidence()
                    .canonicalNodeId()).isEqualTo(leaf);
            assertUnavailable(mapReference(fixture, "canonicalNodeId",
                    leaf + "-other"),
                    UnavailableReason.REFERENCE_CANONICAL_NODE_ID_MISMATCH);
        }
        for (AssetType type : AssetType.values()) {
            if (type != AssetType.INDEX) {
                assertUnavailable(mapReference(fixture(),
                        "referenceAssetType", type),
                        UnavailableReason.REFERENCE_ASSET_TYPE_MISMATCH);
            }
        }
    }

    @Test
    void directRecordsExposeExactComponentOrder() {
        assertRecordComponents(SectorReferenceIndexEvidence.class,
                "referenceIndexEvidenceId", "providerEventId",
                "mappingEvidenceId", "mappingProviderEventId", "taxonomyId",
                "taxonomyVersion", "taxonomyDefinitionHash", "canonicalNodeId",
                "referenceAssetId", "referenceAssetType", "referenceProviderId",
                "referenceIndexId", "referenceIndexLabel",
                "referenceIndexDefinitionRevision", "referenceKind", "currency",
                "calculationVenueId", "calendarId", "calendarRevision",
                "calendarSourceId", "calendarSourceRevision",
                "levelSourceId", "levelSourceRevision", "continuitySourceId",
                "continuitySourceRevision", "bindingSourceId",
                "bindingSourceRevision", "provenanceId", "effectiveInterval",
                "availableAt", "capturedAt");
        assertRecordComponents(SectorReferenceLevelObservation.class,
                "observationId", "providerEventId", "referenceIndexEvidenceId",
                "referenceIndexProviderEventId", "referenceAssetId",
                "referenceAssetType", "referenceProviderId",
                "referenceIndexId", "referenceIndexDefinitionRevision",
                "referenceKind", "currency", "calculationVenueId", "calendarId",
                "calendarRevision", "calendarSourceId", "calendarSourceRevision",
                "levelSourceId", "levelSourceRevision",
                "provenanceId", "levelField", "observedAt", "availableAt",
                "capturedAt", "level");
        assertRecordComponents(SectorIndexDivisorContinuityEvidence.class,
                "continuityEvidenceId", "providerEventId",
                "referenceIndexEvidenceId", "referenceIndexProviderEventId",
                "referenceAssetId", "referenceAssetType", "referenceProviderId",
                "referenceIndexId",
                "referenceIndexDefinitionRevision", "referenceKind", "currency",
                "calculationVenueId", "calendarId", "calendarRevision",
                "calendarSourceId", "calendarSourceRevision",
                "continuitySourceId", "continuitySourceRevision", "provenanceId",
                "basisObservationId", "basisProviderEventId",
                "endpointObservationId", "endpointProviderEventId",
                "coverageStartsAt", "coverageEndsAt", "divisorContinuity",
                "availableAt", "capturedAt");
        assertRecordComponents(SectorReferenceLevelPairRequest.class,
                "policyVersion", "assignmentResolution", "endpointPriceResolution",
                "referenceIndexCandidates", "basisLevelCandidates",
                "endpointLevelCandidates", "divisorContinuityCandidates");
    }

    @Test
    void forbiddenReuseTypesAreAbsentFromPublicSurface() {
        List<Class<?>> surface = List.of(SectorReferenceIndexEvidence.class,
                SectorReferenceLevelObservation.class,
                SectorIndexDivisorContinuityEvidence.class,
                SectorReferenceLevelPairRequest.class,
                SectorReferenceLevelPairResolution.ResolutionContext.class,
                Resolved.class);
        assertThat(surface.stream()
                .flatMap(type -> Stream.of(type.getRecordComponents()))
                .map(component -> component.getGenericType().getTypeName()))
                .noneMatch(name -> name.contains("AssetReturnPricePair")
                        || name.contains("SessionCloseHorizon")
                        || name.contains("OutcomeBasis"));
        assertThat(POLICY.canonicalDefinition())
                .contains("\"runtimePublication\":\"ABSENT\"")
                .contains("\"providerIntegration\":\"ABSENT\"")
                .contains("\"fallbackBehavior\":\"ABSENT\"");
    }

    @ParameterizedTest(name = "assignment propagation: {0}")
    @MethodSource("assignmentPropagationVectors")
    void assignmentPropagationShortCircuitsBeforeReferenceTraversal(
            AssignmentVector vector) {
        SectorReferenceLevelPairResolution result =
                SectorReferenceLevelPairSelector.select(
                        new SectorReferenceLevelPairRequest(POLICY,
                                vector.assignment(), vector.endpoint(),
                                List.of(), List.of(), List.of(), List.of()));

        assertThat(result).isInstanceOf(vector.expectedType());
        assertThat(resultContext(result).assignmentResolution())
                .isSameAs(vector.assignment());
    }

    static Stream<AssignmentVector> assignmentPropagationVectors() {
        List<AssignmentVector> vectors = new ArrayList<>();
        vectors.add(notApplicableVector(AssetType.ETF, "CA", CAD,
                "venue-xtse"));
        for (SectorAssignmentResolution.UnavailableReason reason :
                SectorAssignmentResolution.UnavailableReason.values()) {
            SectorAssignmentResolution.Unavailable assignment =
                    new SectorAssignmentResolution.Unavailable(
                            assignmentContext(ORIGINAL, ASSET_ID, AS_OF), reason);
            vectors.add(new AssignmentVector(reason.name(), assignment,
                    endpoint(ORIGINAL, ASSET_ID, VENUE_ID, USD, AS_OF,
                            ENDPOINT_CLOSE, null),
                    SectorReferenceLevelPairResolution
                            .AssignmentUnavailable.class));
        }
        assertThat(vectors).hasSize(37);
        return vectors.stream();
    }

    @ParameterizedTest(name = "endpoint unavailable: {0}")
    @EnumSource(EndpointPriceResolution.UnavailableReason.class)
    void endpointUnavailableLabelsNeverReplaceIndependentFactChecks(
            EndpointPriceResolution.UnavailableReason reason) {
        Fixture fixture = fixture();
        EndpointPriceResolution endpoint = unavailableEndpoint(
                endpointContext(fixture.endpoint()), reason);

        SectorReferenceLevelPairResolution result =
                SectorReferenceLevelPairSelector.select(
                        request(fixture.withEndpoint(endpoint)));

        assertThat(result).isInstanceOf(Resolved.class);
        assertThat(resultContext(result).endpointPriceResolution())
                .isSameAs(endpoint);
    }

    @ParameterizedTest(name = "candidate PIT: {0}")
    @ValueSource(strings = {"reference", "basis", "endpoint", "continuity"})
    void candidatePitFilteringUsesAvailableAndCapturedCutoff(String stage) {
        Fixture fixture = fixture();
        Fixture availableAndCapturedFuture;
        Fixture onlyCapturedFuture;
        UnavailableReason expected;
        switch (stage) {
            case "reference" -> {
                availableAndCapturedFuture = mapReference(mapReference(fixture,
                        "capturedAt", AS_OF.plusSeconds(1)), "availableAt",
                        AS_OF.plusSeconds(1));
                onlyCapturedFuture = mapReference(fixture, "capturedAt",
                        AS_OF.plusSeconds(1));
                expected = UnavailableReason.REFERENCE_INDEX_MISSING_AS_OF;
            }
            case "basis" -> {
                availableAndCapturedFuture = mapBasis(mapBasis(fixture,
                        "capturedAt", AS_OF.plusSeconds(1)), "availableAt",
                        AS_OF.plusSeconds(1));
                onlyCapturedFuture = mapBasis(fixture, "capturedAt",
                        AS_OF.plusSeconds(1));
                expected = UnavailableReason.BASIS_LEVEL_MISSING_AS_OF;
            }
            case "endpoint" -> {
                availableAndCapturedFuture = mapEndpoint(mapEndpoint(fixture,
                        "capturedAt", AS_OF.plusSeconds(1)), "availableAt",
                        AS_OF.plusSeconds(1));
                onlyCapturedFuture = mapEndpoint(fixture, "capturedAt",
                        AS_OF.plusSeconds(1));
                expected = UnavailableReason.ENDPOINT_LEVEL_MISSING_AS_OF;
            }
            case "continuity" -> {
                availableAndCapturedFuture = mapContinuity(mapContinuity(fixture,
                        "capturedAt", AS_OF.plusSeconds(1)), "availableAt",
                        AS_OF.plusSeconds(1));
                onlyCapturedFuture = mapContinuity(fixture, "capturedAt",
                        AS_OF.plusSeconds(1));
                expected = UnavailableReason
                        .DIVISOR_CONTINUITY_EVIDENCE_MISSING_AS_OF;
            }
            default -> throw new AssertionError(stage);
        }
        assertUnavailable(availableAndCapturedFuture, expected);
        assertUnavailable(onlyCapturedFuture, expected);
        assertResolved(fixture);
    }

    @ParameterizedTest(name = "local reason: {0}")
    @MethodSource("localFaultVectors")
    void localReasonMatrixIsExhaustiveAndFailClosed(FaultVector vector) {
        assertUnavailable(vector.mutation().apply(fixture()), vector.expected());
    }

    static Stream<FaultVector> localFaultVectors() {
        List<FaultVector> vectors = baseFaultVectors();
        ReferenceIndexKind baseKind =
                ReferenceIndexKind.PROVIDER_PUBLISHED_TOTAL_RETURN_INDEX;
        for (ReferenceIndexKind kind : ReferenceIndexKind.values()) {
            if (kind != ReferenceIndexKind.PROVIDER_PUBLISHED_PRICE_INDEX
                    && kind != baseKind) {
                vectors.add(fault("reference-kind-" + kind,
                        UnavailableReason.REFERENCE_KIND_MISMATCH,
                        referenceField("referenceKind", kind)));
                vectors.add(fault("basis-kind-" + kind,
                        UnavailableReason.BASIS_REFERENCE_KIND_MISMATCH,
                        basisField("referenceKind", kind)));
                vectors.add(fault("endpoint-kind-" + kind,
                        UnavailableReason.ENDPOINT_REFERENCE_KIND_MISMATCH,
                        endpointField("referenceKind", kind)));
                vectors.add(fault("divisor-kind-" + kind,
                        UnavailableReason.DIVISOR_REFERENCE_KIND_MISMATCH,
                        continuityField("referenceKind", kind)));
            }
        }
        ReferenceLevelField baseField =
                ReferenceLevelField.PROVIDER_PUBLISHED_RETURN;
        for (ReferenceLevelField field : ReferenceLevelField.values()) {
            if (field != ReferenceLevelField.PROVIDER_PUBLISHED_INDEX_LEVEL
                    && field != baseField) {
                vectors.add(fault("basis-field-" + field,
                        UnavailableReason.BASIS_LEVEL_FIELD_MISMATCH,
                        basisField("levelField", field)));
                vectors.add(fault("endpoint-field-" + field,
                        UnavailableReason.ENDPOINT_LEVEL_FIELD_MISMATCH,
                        endpointField("levelField", field)));
            }
        }
        for (DivisorContinuity continuity : List.of(
                DivisorContinuity.NOT_ATTESTED, DivisorContinuity.UNKNOWN)) {
            vectors.add(fault("divisor-state-" + continuity,
                    UnavailableReason.DIVISOR_CONTINUITY_UNAVAILABLE,
                    continuityField("divisorContinuity", continuity)));
        }
        assertThat(vectors).hasSize(90);
        return vectors.stream();
    }

    @ParameterizedTest(name = "adjacent precedence: {0}")
    @MethodSource("adjacentPrecedenceVectors")
    void adjacentVisibleFaultsUseDeclaredReasonPrecedence(
            PrecedenceVector vector) {
        assertUnavailable(vector.mutation().apply(fixture()), vector.expected());
    }

    static Stream<PrecedenceVector> adjacentPrecedenceVectors() {
        List<FaultVector> base = baseFaultVectors();
        List<PrecedenceVector> result = new ArrayList<>();
        addAdjacent(result, base.subList(2, 12));
        addAdjacent(result, base.subList(13, 26));
        addAdjacent(result, base.subList(27, 40));
        addAdjacent(result, base.subList(41, 56));
        assertThat(result).hasSize(47);
        return result.stream();
    }

    @ParameterizedTest(name = "interval: {0}")
    @MethodSource("intervalVectors")
    void bindingIntervalMustContainBothExactReferenceInstants(
            IntervalVector vector) {
        Fixture fixture = mapReference(fixture(), "effectiveInterval",
                vector.interval());
        SectorReferenceLevelPairResolution result =
                SectorReferenceLevelPairSelector.select(request(fixture));
        if (vector.resolves()) {
            assertThat(result).isInstanceOf(Resolved.class);
        } else {
            assertThat(result).isEqualTo(new EvidenceUnavailable(
                    context(fixture.assignment(), fixture.endpoint()),
                    UnavailableReason.REFERENCE_EFFECTIVE_INTERVAL_MISMATCH));
        }
    }

    static Stream<IntervalVector> intervalVectors() {
        return Stream.of(
                new IntervalVector("open-starts-at-basis",
                        new EffectiveInterval(BASIS_TIME, new OpenEnded()), true),
                new IntervalVector("closed-after-endpoint",
                        new EffectiveInterval(BASIS_TIME,
                                new EndsAtExclusive(ENDPOINT_CLOSE.plusSeconds(1))),
                        true),
                new IntervalVector("end-equals-endpoint-exclusive",
                        new EffectiveInterval(BASIS_TIME,
                                new EndsAtExclusive(ENDPOINT_CLOSE)), false),
                new IntervalVector("start-after-basis",
                        new EffectiveInterval(BASIS_TIME.plusSeconds(1),
                                new OpenEnded()), false),
                new IntervalVector("start-equals-endpoint",
                        new EffectiveInterval(ENDPOINT_CLOSE,
                                new OpenEnded()), false),
                new IntervalVector("end-equals-basis-exclusive",
                        new EffectiveInterval(BASIS_TIME.minusSeconds(1),
                                new EndsAtExclusive(BASIS_TIME)), false));
    }

    private static List<FaultVector> baseFaultVectors() {
        List<FaultVector> vectors = new ArrayList<>();
        vectors.add(fault("endpoint-not-reached",
                UnavailableReason.ENDPOINT_NOT_REACHED_AS_OF,
                SectorReferenceLevelPairSelectorGoldenTest::futureEndpoint));
        vectors.add(fault("reference-missing",
                UnavailableReason.REFERENCE_INDEX_MISSING_AS_OF,
                fixture -> fixture.withReferences(List.of())));
        vectors.add(fault("reference-mapping-link",
                UnavailableReason.REFERENCE_MAPPING_EVIDENCE_LINK_MISMATCH,
                referenceField("mappingEvidenceId", "mapping-other")));
        vectors.add(fault("reference-taxonomy-id",
                UnavailableReason.REFERENCE_TAXONOMY_ID_MISMATCH,
                referenceField("taxonomyId", "taxonomy-other")));
        vectors.add(fault("reference-taxonomy-version",
                UnavailableReason.REFERENCE_TAXONOMY_VERSION_MISMATCH,
                referenceField("taxonomyVersion", "2.0.0")));
        vectors.add(fault("reference-taxonomy-hash",
                UnavailableReason.REFERENCE_TAXONOMY_DEFINITION_HASH_MISMATCH,
                referenceField("taxonomyDefinitionHash", "b".repeat(64))));
        vectors.add(fault("reference-canonical-node",
                UnavailableReason.REFERENCE_CANONICAL_NODE_ID_MISMATCH,
                referenceField("canonicalNodeId",
                        "wsr-sector-connectivity-media")));
        vectors.add(fault("reference-asset-type",
                UnavailableReason.REFERENCE_ASSET_TYPE_MISMATCH,
                referenceField("referenceAssetType", AssetType.ETF)));
        vectors.add(fault("reference-currency",
                UnavailableReason.REFERENCE_CURRENCY_MISMATCH,
                referenceField("currency", EUR)));
        vectors.add(fault("reference-kind",
                UnavailableReason.REFERENCE_KIND_MISMATCH,
                referenceField("referenceKind",
                        ReferenceIndexKind.PROVIDER_PUBLISHED_TOTAL_RETURN_INDEX)));
        vectors.add(fault("reference-interval",
                UnavailableReason.REFERENCE_EFFECTIVE_INTERVAL_MISMATCH,
                referenceField("effectiveInterval", new EffectiveInterval(
                        BASIS_TIME.plusSeconds(1), new OpenEnded()))));
        vectors.add(fault("reference-ambiguous",
                UnavailableReason.REFERENCE_INDEX_AMBIGUOUS,
                SectorReferenceLevelPairSelectorGoldenTest::duplicateReferences));

        vectors.add(fault("basis-missing",
                UnavailableReason.BASIS_LEVEL_MISSING_AS_OF,
                fixture -> fixture.withBasisLevels(List.of())));
        vectors.add(fault("basis-evidence-link",
                UnavailableReason.BASIS_REFERENCE_INDEX_EVIDENCE_LINK_MISMATCH,
                basisField("referenceIndexEvidenceId", "reference-other")));
        vectors.add(fault("basis-reference-asset",
                UnavailableReason.BASIS_REFERENCE_ASSET_MISMATCH,
                basisField("referenceAssetId", "asset-other")));
        vectors.add(fault("basis-provider",
                UnavailableReason.BASIS_REFERENCE_PROVIDER_MISMATCH,
                basisField("referenceProviderId", "provider-other")));
        vectors.add(fault("basis-index",
                UnavailableReason.BASIS_REFERENCE_INDEX_MISMATCH,
                basisField("referenceIndexId", "index-other")));
        vectors.add(fault("basis-revision",
                UnavailableReason.BASIS_REFERENCE_INDEX_DEFINITION_REVISION_MISMATCH,
                basisField("referenceIndexDefinitionRevision", "revision-other")));
        vectors.add(fault("basis-kind",
                UnavailableReason.BASIS_REFERENCE_KIND_MISMATCH,
                basisField("referenceKind",
                        ReferenceIndexKind.PROVIDER_PUBLISHED_TOTAL_RETURN_INDEX)));
        vectors.add(fault("basis-currency",
                UnavailableReason.BASIS_CURRENCY_MISMATCH,
                basisField("currency", EUR)));
        vectors.add(fault("basis-venue",
                UnavailableReason.BASIS_CALCULATION_VENUE_MISMATCH,
                basisField("calculationVenueId", "venue-other")));
        vectors.add(fault("basis-calendar",
                UnavailableReason.BASIS_CALENDAR_MISMATCH,
                basisField("calendarId", "calendar-other")));
        vectors.add(fault("basis-source",
                UnavailableReason.BASIS_LEVEL_SOURCE_MISMATCH,
                basisField("levelSourceId", "level-source-other")));
        vectors.add(fault("basis-observed",
                UnavailableReason.BASIS_OBSERVED_AT_MISMATCH,
                basisField("observedAt", BASIS_TIME.plusSeconds(1))));
        vectors.add(fault("basis-field",
                UnavailableReason.BASIS_LEVEL_FIELD_MISMATCH,
                basisField("levelField",
                        ReferenceLevelField.PROVIDER_PUBLISHED_RETURN)));
        vectors.add(fault("basis-ambiguous",
                UnavailableReason.BASIS_LEVEL_AMBIGUOUS,
                SectorReferenceLevelPairSelectorGoldenTest::duplicateBasis));

        vectors.add(fault("endpoint-missing",
                UnavailableReason.ENDPOINT_LEVEL_MISSING_AS_OF,
                fixture -> fixture.withEndpointLevels(List.of())));
        vectors.add(fault("endpoint-evidence-link",
                UnavailableReason.ENDPOINT_REFERENCE_INDEX_EVIDENCE_LINK_MISMATCH,
                endpointField("referenceIndexEvidenceId", "reference-other")));
        vectors.add(fault("endpoint-reference-asset",
                UnavailableReason.ENDPOINT_REFERENCE_ASSET_MISMATCH,
                endpointField("referenceAssetType", AssetType.ETF)));
        vectors.add(fault("endpoint-provider",
                UnavailableReason.ENDPOINT_REFERENCE_PROVIDER_MISMATCH,
                endpointField("referenceProviderId", "provider-other")));
        vectors.add(fault("endpoint-index",
                UnavailableReason.ENDPOINT_REFERENCE_INDEX_MISMATCH,
                endpointField("referenceIndexId", "index-other")));
        vectors.add(fault("endpoint-revision",
                UnavailableReason.ENDPOINT_REFERENCE_INDEX_DEFINITION_REVISION_MISMATCH,
                endpointField("referenceIndexDefinitionRevision", "revision-other")));
        vectors.add(fault("endpoint-kind",
                UnavailableReason.ENDPOINT_REFERENCE_KIND_MISMATCH,
                endpointField("referenceKind",
                        ReferenceIndexKind.PROVIDER_PUBLISHED_TOTAL_RETURN_INDEX)));
        vectors.add(fault("endpoint-currency",
                UnavailableReason.ENDPOINT_CURRENCY_MISMATCH,
                endpointField("currency", EUR)));
        vectors.add(fault("endpoint-venue",
                UnavailableReason.ENDPOINT_CALCULATION_VENUE_MISMATCH,
                endpointField("calculationVenueId", "venue-other")));
        vectors.add(fault("endpoint-calendar",
                UnavailableReason.ENDPOINT_CALENDAR_MISMATCH,
                endpointField("calendarRevision", "calendar-revision-other")));
        vectors.add(fault("endpoint-source",
                UnavailableReason.ENDPOINT_LEVEL_SOURCE_MISMATCH,
                endpointField("levelSourceRevision", "level-source-revision-other")));
        vectors.add(fault("endpoint-observed",
                UnavailableReason.ENDPOINT_OBSERVED_AT_MISMATCH,
                endpointField("observedAt", ENDPOINT_CLOSE.minusSeconds(1))));
        vectors.add(fault("endpoint-field",
                UnavailableReason.ENDPOINT_LEVEL_FIELD_MISMATCH,
                endpointField("levelField",
                        ReferenceLevelField.PROVIDER_PUBLISHED_RETURN)));
        vectors.add(fault("endpoint-ambiguous",
                UnavailableReason.ENDPOINT_LEVEL_AMBIGUOUS,
                SectorReferenceLevelPairSelectorGoldenTest::duplicateEndpoint));

        vectors.add(fault("divisor-missing",
                UnavailableReason.DIVISOR_CONTINUITY_EVIDENCE_MISSING_AS_OF,
                fixture -> fixture.withContinuities(List.of())));
        vectors.add(fault("divisor-evidence-link",
                UnavailableReason.DIVISOR_REFERENCE_INDEX_EVIDENCE_LINK_MISMATCH,
                continuityField("referenceIndexEvidenceId", "reference-other")));
        vectors.add(fault("divisor-reference-asset",
                UnavailableReason.DIVISOR_REFERENCE_ASSET_MISMATCH,
                continuityField("referenceAssetId", "asset-other")));
        vectors.add(fault("divisor-provider",
                UnavailableReason.DIVISOR_REFERENCE_PROVIDER_MISMATCH,
                continuityField("referenceProviderId", "provider-other")));
        vectors.add(fault("divisor-index",
                UnavailableReason.DIVISOR_REFERENCE_INDEX_MISMATCH,
                continuityField("referenceIndexId", "index-other")));
        vectors.add(fault("divisor-revision",
                UnavailableReason.DIVISOR_REFERENCE_INDEX_DEFINITION_REVISION_MISMATCH,
                continuityField("referenceIndexDefinitionRevision", "revision-other")));
        vectors.add(fault("divisor-kind",
                UnavailableReason.DIVISOR_REFERENCE_KIND_MISMATCH,
                continuityField("referenceKind",
                        ReferenceIndexKind.PROVIDER_PUBLISHED_TOTAL_RETURN_INDEX)));
        vectors.add(fault("divisor-currency",
                UnavailableReason.DIVISOR_CURRENCY_MISMATCH,
                continuityField("currency", EUR)));
        vectors.add(fault("divisor-venue",
                UnavailableReason.DIVISOR_CALCULATION_VENUE_MISMATCH,
                continuityField("calculationVenueId", "venue-other")));
        vectors.add(fault("divisor-calendar",
                UnavailableReason.DIVISOR_CALENDAR_MISMATCH,
                continuityField("calendarId", "calendar-other")));
        vectors.add(fault("divisor-source",
                UnavailableReason.DIVISOR_CONTINUITY_SOURCE_MISMATCH,
                continuityField("continuitySourceRevision",
                        "continuity-source-revision-other")));
        vectors.add(fault("divisor-basis-link",
                UnavailableReason.DIVISOR_BASIS_OBSERVATION_LINK_MISMATCH,
                continuityField("basisObservationId", "basis-other")));
        vectors.add(fault("divisor-endpoint-link",
                UnavailableReason.DIVISOR_ENDPOINT_OBSERVATION_LINK_MISMATCH,
                continuityField("endpointProviderEventId", "endpoint-other")));
        vectors.add(fault("divisor-coverage",
                UnavailableReason.DIVISOR_COVERAGE_MISMATCH,
                continuityField("coverageStartsAt", BASIS_TIME.minusSeconds(1))));
        vectors.add(fault("divisor-status",
                UnavailableReason.DIVISOR_CONTINUITY_UNAVAILABLE,
                continuityField("divisorContinuity",
                        DivisorContinuity.DIVISOR_DISCONTINUITY)));
        vectors.add(fault("divisor-ambiguous",
                UnavailableReason.DIVISOR_CONTINUITY_EVIDENCE_AMBIGUOUS,
                SectorReferenceLevelPairSelectorGoldenTest::duplicateContinuity));
        assertThat(vectors).hasSize(56);
        return vectors;
    }

    private static FaultVector fault(String label, UnavailableReason reason,
            UnaryOperator<Fixture> mutation) {
        return new FaultVector(label, reason, mutation);
    }

    private static UnaryOperator<Fixture> referenceField(String field,
            Object value) {
        return fixture -> mapReference(fixture, field, value);
    }

    private static UnaryOperator<Fixture> basisField(String field, Object value) {
        return fixture -> mapBasis(fixture, field, value);
    }

    private static UnaryOperator<Fixture> endpointField(String field,
            Object value) {
        return fixture -> mapEndpoint(fixture, field, value);
    }

    private static UnaryOperator<Fixture> continuityField(String field,
            Object value) {
        return fixture -> mapContinuity(fixture, field, value);
    }

    private static void addAdjacent(List<PrecedenceVector> output,
            List<FaultVector> ordered) {
        for (int index = 0; index + 1 < ordered.size(); index++) {
            FaultVector earlier = ordered.get(index);
            FaultVector later = ordered.get(index + 1);
            output.add(new PrecedenceVector(
                    earlier.label() + " before " + later.label(),
                    earlier.expected(), fixture -> later.mutation().apply(
                            earlier.mutation().apply(fixture))));
        }
    }

    private static Fixture fixture() {
        return fixture(ORIGINAL);
    }

    private static Fixture fixture(OutcomeBasis basis) {
        return fixture(basis, CANONICAL_NODE_ID);
    }

    private static Fixture fixture(OutcomeBasis basis, String canonicalNodeId) {
        SectorAssignmentResolution.Resolved assignment = sectorResolved(
                basis, ASSET_ID, VENUE_ID, "US", USD, AS_OF, canonicalNodeId);
        EndpointPriceResolution endpoint = endpoint(basis, ASSET_ID, VENUE_ID,
                USD, AS_OF, ENDPOINT_CLOSE, null);
        return fixture(assignment, endpoint);
    }

    private static Fixture fixture(SectorAssignmentResolution.Resolved assignment,
            EndpointPriceResolution endpoint) {
        var endpointContext = endpointContext(endpoint);
        OutcomeBasis basis = endpointContext.horizonResolution().window().context()
                .basis();
        Instant endpointInstant = endpointContext.horizonResolution().window()
                .endpointSession().closesAt();
        SectorMappingEvidence mappingEvidence = assignment.mappingEvidence();
        String canonicalNodeId = ((Mapped) mappingEvidence.mappingDisposition())
                .canonicalNodeId();
        SectorReferenceIndexEvidence reference =
                new SectorReferenceIndexEvidence(
                        "reference-evidence-sector",
                        "provider-event-reference-sector",
                        mappingEvidence.mappingEvidenceId(),
                        mappingEvidence.providerEventId(),
                        mappingEvidence.taxonomyId(), mappingEvidence.taxonomyVersion(),
                        mappingEvidence.taxonomyDefinitionHash(), canonicalNodeId,
                        REFERENCE_ASSET_ID, AssetType.INDEX,
                        "provider-sector-index", "index-sector-" + canonicalNodeId,
                        "Provider sector price index",
                        "definition-revision-2026-08",
                        ReferenceIndexKind.PROVIDER_PUBLISHED_PRICE_INDEX,
                        endpointContext.binding().currency(),
                        "calculation-venue-sector", "calendar-sector",
                        "calendar-revision-2026", "calendar-source-sector",
                        "calendar-source-revision-1", "level-source-sp",
                        "level-source-revision-1", "continuity-source-sp",
                        "continuity-source-revision-1", "binding-source-sp",
                        "binding-source-revision-1", "provenance-reference",
                        new EffectiveInterval(basis.eventTime(), new OpenEnded()),
                        basis.eventTime().plusSeconds(1),
                        basis.eventTime().plusSeconds(1));
        SectorReferenceLevelObservation basisLevel = level(
                "basis-level", "provider-event-basis-level", reference,
                basis.eventTime(), basis.eventTime().plusSeconds(2),
                new BigDecimal("4300.000000000000"));
        SectorReferenceLevelObservation endpointLevel = level(
                "endpoint-level", "provider-event-endpoint-level", reference,
                endpointInstant, endpointInstant,
                new BigDecimal("4500.000000000000"));
        SectorIndexDivisorContinuityEvidence continuity = continuity(
                reference, basisLevel, endpointLevel, basis.eventTime(),
                endpointInstant);
        return new Fixture(assignment, endpoint, List.of(reference),
                List.of(basisLevel), List.of(endpointLevel), List.of(continuity));
    }

    private static SectorReferenceLevelObservation level(String id,
            String providerEventId, SectorReferenceIndexEvidence reference,
            Instant observedAt, Instant availableAt, BigDecimal value) {
        return new SectorReferenceLevelObservation(id, providerEventId,
                reference.referenceIndexEvidenceId(), reference.providerEventId(),
                reference.referenceAssetId(), reference.referenceAssetType(),
                reference.referenceProviderId(), reference.referenceIndexId(),
                reference.referenceIndexDefinitionRevision(), reference.referenceKind(),
                reference.currency(), reference.calculationVenueId(),
                reference.calendarId(), reference.calendarRevision(),
                reference.calendarSourceId(), reference.calendarSourceRevision(),
                reference.levelSourceId(), reference.levelSourceRevision(),
                "provenance-" + id,
                ReferenceLevelField.PROVIDER_PUBLISHED_INDEX_LEVEL, observedAt,
                availableAt, availableAt, value);
    }

    private static SectorIndexDivisorContinuityEvidence continuity(
            SectorReferenceIndexEvidence reference,
            SectorReferenceLevelObservation basis,
            SectorReferenceLevelObservation endpoint,
            Instant basisInstant, Instant endpointInstant) {
        return new SectorIndexDivisorContinuityEvidence(
                "continuity-evidence", "provider-event-continuity",
                reference.referenceIndexEvidenceId(), reference.providerEventId(),
                reference.referenceAssetId(), reference.referenceAssetType(),
                reference.referenceProviderId(), reference.referenceIndexId(),
                reference.referenceIndexDefinitionRevision(), reference.referenceKind(),
                reference.currency(), reference.calculationVenueId(),
                reference.calendarId(), reference.calendarRevision(),
                reference.calendarSourceId(), reference.calendarSourceRevision(),
                reference.continuitySourceId(),
                reference.continuitySourceRevision(), "provenance-continuity",
                basis.observationId(), basis.providerEventId(),
                endpoint.observationId(), endpoint.providerEventId(), basisInstant,
                endpointInstant,
                DivisorContinuity
                        .PROVIDER_PUBLISHED_INDEX_DIVISOR_CONTINUITY_ATTESTED,
                endpointInstant, endpointInstant);
    }

    private static SectorAssignmentResolution.Resolved sectorResolved(
            OutcomeBasis basis, String assetId, String venue, Currency currency,
            Instant asOf) {
        return sectorResolved(basis, assetId, venue, "US", currency, asOf,
                CANONICAL_NODE_ID);
    }

    private static SectorAssignmentResolution.Resolved sectorResolved(
            OutcomeBasis basis, String assetId, String venue, String country,
            Currency currency, Instant asOf, String canonicalNodeId) {
        SectorAssetClassificationEvidence classification =
                sectorClassification(basis, assetId, AssetType.EQUITY, country,
                        currency, venue);
        SectorMembershipEvidence membership = new SectorMembershipEvidence(
                "sector-membership-evidence", "provider-event-membership",
                basis, assetId, AssetType.EQUITY, venue, country, currency,
                "membership-provider", "economic-activity-scheme",
                "scheme-revision-2026", "provider-node-computing",
                "Provider computing node", "membership-source",
                "membership-source-revision-1", "provenance-membership",
                sectorInterval(basis), basis.eventTime(), basis.eventTime());
        SectorMappingEvidence mapping = new SectorMappingEvidence(
                "sector-mapping-evidence", "provider-event-mapping",
                MAPPING_POLICY_VERSION, MAPPING_POLICY_HASH, MAPPING_SET_ID,
                MAPPING_SET_VERSION, MAPPING_SET_HASH, TAXONOMY_ID,
                TAXONOMY_VERSION, TAXONOMY_HASH, membership.providerId(),
                membership.providerSchemeId(), membership.providerSchemeRevision(),
                membership.providerNodeId(), "Mapping label",
                new Recorded("Provider-published definition", "en"),
                new Mapped(canonicalNodeId), "mapping-source",
                "mapping-source-revision-1", "provenance-mapping",
                sectorInterval(basis),
                basis.eventTime(), basis.eventTime());
        return new SectorAssignmentResolution.Resolved(
                assignmentContext(basis, assetId, asOf), classification,
                membership, mapping);
    }

    private static SectorAssignmentResolution.NotApplicable sectorNotApplicable(
            AssetType type, String country, Currency currency, String venue) {
        SectorAssetClassificationEvidence classification =
                sectorClassification(ORIGINAL, ASSET_ID, type, country,
                        currency, venue);
        if (type == AssetType.EQUITY) {
            throw new IllegalArgumentException(
                    "sector not-applicable fixture requires non-equity");
        }
        return new SectorAssignmentResolution.NotApplicable(
                assignmentContext(ORIGINAL, ASSET_ID, AS_OF), classification,
                SectorAssignmentResolution.NotApplicableReason.NON_EQUITY);
    }

    private static SectorAssetClassificationEvidence sectorClassification(
            OutcomeBasis basis, String assetId, AssetType type, String country,
            Currency currency, String venue) {
        return new SectorAssetClassificationEvidence(
                "classification-evidence", "provider-event-classification", basis,
                assetId, type, venue, country, currency, "classification-source",
                "classification-source-revision-1", "provenance-classification",
                sectorInterval(basis), basis.eventTime(), basis.eventTime());
    }

    private static SectorAssetClassificationEvidence.EffectiveInterval
            sectorInterval(OutcomeBasis basis) {
        return new SectorAssetClassificationEvidence.EffectiveInterval(
                basis.eventTime().minusSeconds(1),
                new SectorAssetClassificationEvidence.OpenEnded());
    }

    private static SectorAssignmentResolution.ResolutionContext assignmentContext(
            OutcomeBasis basis, String assetId, Instant asOf) {
        return new SectorAssignmentResolution.ResolutionContext(
                ASSIGNMENT_POLICY, ASSIGNMENT_HASH, basis, assetId, asOf,
                MAPPING_SET_ID, MAPPING_SET_VERSION, MAPPING_SET_HASH);
    }

    private static EndpointPriceResolution endpoint(OutcomeBasis basis,
            String assetId, String venue, Currency currency, Instant asOf,
            Instant close,
            EndpointPriceResolution.UnavailableReason unavailableReason) {
        Instant open = close.minusSeconds(6 * 60 * 60L + 30 * 60L);
        TradingSession session = new TradingSession("session-endpoint", open, close);
        var horizonContext = new SessionCloseHorizonResolution.ResolutionContext(
                SessionCloseHorizonPolicyVersion
                        .STRICTLY_AFTER_BASIS_EVENT_SESSION_CLOSE_V1,
                HORIZON_HASH, basis, OutcomeHorizon.D1, 1, "calendar-primary",
                "catalog-revision-1");
        var horizon = new SessionCloseHorizonResolution.Resolved(
                new SessionCloseHorizonResolution.ResolvedSessionWindow(
                        horizonContext, List.of(session), session));
        var context = new EndpointPriceResolution.ResolutionContext(
                EndpointPricePolicyVersion
                        .OFFICIAL_PRIMARY_VENUE_CLOSE_SPLIT_ADJUSTED_V1,
                ENDPOINT_HASH, horizon,
                new CatalogPointInTimeEvidence("calendar-primary",
                        "catalog-revision-1", "calendar-source",
                        "calendar-source-revision-1", basis.eventTime(),
                        basis.eventTime(), "provenance-calendar"),
                new EndpointPriceBinding("binding-primary", "binding-revision-1",
                        assetId, venue, currency, "price-source",
                        "price-source-revision-1", basis.eventTime(),
                        basis.eventTime(), "provenance-binding"), asOf);
        if (unavailableReason != null) {
            return new EndpointPriceResolution.Unavailable(context,
                    unavailableReason);
        }
        EndpointPriceObservation observation = new EndpointPriceObservation(
                "endpoint-price-observation", "provider-event-endpoint-price",
                assetId, venue, currency, "price-source",
                "price-source-revision-1", "provenance-endpoint-price",
                "calendar-primary", "catalog-revision-1", "session-endpoint",
                EndpointPriceField.OFFICIAL_REGULAR_SESSION_CLOSE,
                EndpointPriceAdjustmentBasis
                        .SPLIT_AND_REVERSE_SPLIT_ADJUSTED_TO_ENDPOINT_SHARE_BASIS_DIVIDEND_UNADJUSTED,
                CorporateActionContinuity.SPLIT_REVERSE_SPLIT_CONTINUOUS,
                close, close, close, new BigDecimal("100.000000000000"));
        return new EndpointPriceResolution.Resolved(context, observation);
    }

    private static EndpointPriceResolution unavailableEndpoint(
            EndpointPriceResolution.ResolutionContext context,
            EndpointPriceResolution.UnavailableReason reason) {
        return new EndpointPriceResolution.Unavailable(context, reason);
    }

    private static EndpointPriceResolution factualAnchorEndpoint(
            EndpointPriceResolution endpoint,
            SectorReferenceLevelPairResolution.EndpointAnchorUnavailableReason
                    reason) {
        EndpointPriceResolution.ResolutionContext context = endpointContext(endpoint);
        switch (reason) {
            case CATALOG_NOT_KNOWN_AS_OF -> {
                CatalogPointInTimeEvidence catalog = withRecord(
                        context.catalogEvidence(), "capturedAt", AS_OF.plusSeconds(1));
                context = withRecord(context, "catalogEvidence", catalog);
            }
            case CATALOG_EVIDENCE_MISMATCH -> {
                CatalogPointInTimeEvidence catalog = withRecord(
                        context.catalogEvidence(), "calendarId", "calendar-other");
                context = withRecord(context, "catalogEvidence", catalog);
            }
            case BINDING_NOT_KNOWN_AS_OF -> {
                EndpointPriceBinding binding = withRecord(context.binding(),
                        "capturedAt", AS_OF.plusSeconds(1));
                context = withRecord(context, "binding", binding);
            }
        }
        return new EndpointPriceResolution.Unavailable(context,
                EndpointPriceResolution.UnavailableReason
                        .OBSERVATION_MISSING_AS_OF);
    }

    private static EndpointPriceResolution.ResolutionContext endpointContext(
            EndpointPriceResolution endpoint) {
        return switch (endpoint) {
            case EndpointPriceResolution.Resolved resolved -> resolved.context();
            case EndpointPriceResolution.Unavailable unavailable ->
                unavailable.context();
        };
    }

    private static SectorReferenceLevelPairResolution.ResolutionContext context(
            SectorAssignmentResolution assignment,
            EndpointPriceResolution endpoint) {
        return new SectorReferenceLevelPairResolution.ResolutionContext(
                POLICY, POLICY.definitionHash(), assignment, endpoint);
    }

    private static SectorReferenceLevelPairResolution.ResolutionContext
            resultContext(SectorReferenceLevelPairResolution result) {
        return switch (result) {
            case Resolved resolved -> resolved.context();
            case SectorReferenceLevelPairResolution.NotApplicable value ->
                value.context();
            case SectorReferenceLevelPairResolution.AssignmentUnavailable value ->
                value.context();
            case SectorReferenceLevelPairResolution.EndpointAnchorUnavailable value ->
                value.context();
            case EvidenceUnavailable value -> value.context();
        };
    }

    private static SectorReferenceLevelPairRequest request(Fixture fixture) {
        return new SectorReferenceLevelPairRequest(POLICY, fixture.assignment(),
                fixture.endpoint(), fixture.references(), fixture.basisLevels(),
                fixture.endpointLevels(), fixture.continuities());
    }

    private static Resolved assertResolved(Fixture fixture) {
        SectorReferenceLevelPairResolution result =
                SectorReferenceLevelPairSelector.select(request(fixture));
        assertThat(result).isInstanceOf(Resolved.class);
        return (Resolved) result;
    }

    private static void assertUnavailable(Fixture fixture,
            UnavailableReason expected) {
        SectorReferenceLevelPairResolution result =
                SectorReferenceLevelPairSelector.select(request(fixture));
        assertThat(result).isEqualTo(new EvidenceUnavailable(
                context(fixture.assignment(), fixture.endpoint()), expected));
    }

    private static Fixture futureEndpoint(Fixture fixture) {
        EndpointPriceResolution endpoint = endpoint(ORIGINAL, ASSET_ID, VENUE_ID,
                USD, AS_OF, AS_OF.plusSeconds(60),
                EndpointPriceResolution.UnavailableReason
                        .OBSERVATION_MISSING_AS_OF);
        return fixture.withEndpoint(endpoint);
    }

    private static Fixture mapReference(Fixture fixture, String field,
            Object value) {
        SectorReferenceIndexEvidence updated = withRecord(
                fixture.references().getFirst(), field, value);
        ArrayList<SectorReferenceIndexEvidence> values =
                new ArrayList<>(fixture.references());
        values.set(0, updated);
        return fixture.withReferences(values);
    }

    private static Fixture mapBasis(Fixture fixture, String field, Object value) {
        SectorReferenceLevelObservation updated = withRecord(
                fixture.basisLevels().getFirst(), field, value);
        ArrayList<SectorReferenceLevelObservation> values =
                new ArrayList<>(fixture.basisLevels());
        values.set(0, updated);
        return fixture.withBasisLevels(values);
    }

    private static Fixture mapEndpoint(Fixture fixture, String field,
            Object value) {
        SectorReferenceLevelObservation updated = withRecord(
                fixture.endpointLevels().getFirst(), field, value);
        ArrayList<SectorReferenceLevelObservation> values =
                new ArrayList<>(fixture.endpointLevels());
        values.set(0, updated);
        return fixture.withEndpointLevels(values);
    }

    private static Fixture mapContinuity(Fixture fixture, String field,
            Object value) {
        SectorIndexDivisorContinuityEvidence updated = withRecord(
                fixture.continuities().getFirst(), field, value);
        ArrayList<SectorIndexDivisorContinuityEvidence> values =
                new ArrayList<>(fixture.continuities());
        values.set(0, updated);
        return fixture.withContinuities(values);
    }

    private static Fixture duplicateReferences(Fixture fixture) {
        SectorReferenceIndexEvidence value = fixture.references().getFirst();
        return fixture.withReferences(List.of(value, value));
    }

    private static Fixture duplicateBasis(Fixture fixture) {
        SectorReferenceLevelObservation value = fixture.basisLevels().getFirst();
        return fixture.withBasisLevels(List.of(value, value));
    }

    private static Fixture duplicateEndpoint(Fixture fixture) {
        SectorReferenceLevelObservation value = fixture.endpointLevels().getFirst();
        return fixture.withEndpointLevels(List.of(value, value));
    }

    private static Fixture duplicateContinuity(Fixture fixture) {
        SectorIndexDivisorContinuityEvidence value =
                fixture.continuities().getFirst();
        return fixture.withContinuities(List.of(value, value));
    }

    private static Fixture coherentProviderIdentity(Fixture fixture,
            String provider, String index, String revision) {
        fixture = mapReference(fixture, "referenceProviderId", provider);
        fixture = mapReference(fixture, "referenceIndexId", index);
        fixture = mapReference(fixture, "referenceIndexDefinitionRevision",
                revision);
        fixture = mapBasis(fixture, "referenceProviderId", provider);
        fixture = mapBasis(fixture, "referenceIndexId", index);
        fixture = mapBasis(fixture, "referenceIndexDefinitionRevision", revision);
        fixture = mapEndpoint(fixture, "referenceProviderId", provider);
        fixture = mapEndpoint(fixture, "referenceIndexId", index);
        fixture = mapEndpoint(fixture, "referenceIndexDefinitionRevision",
                revision);
        fixture = mapContinuity(fixture, "referenceProviderId", provider);
        fixture = mapContinuity(fixture, "referenceIndexId", index);
        return mapContinuity(fixture, "referenceIndexDefinitionRevision",
                revision);
    }

    @SuppressWarnings("unchecked")
    private static <T> T withRecord(T value, String componentName,
            Object replacement) {
        try {
            Class<?> type = value.getClass();
            RecordComponent[] components = type.getRecordComponents();
            Class<?>[] parameterTypes = new Class<?>[components.length];
            Object[] arguments = new Object[components.length];
            boolean found = false;
            for (int index = 0; index < components.length; index++) {
                RecordComponent component = components[index];
                parameterTypes[index] = component.getType();
                arguments[index] = component.getAccessor().invoke(value);
                if (component.getName().equals(componentName)) {
                    arguments[index] = replacement;
                    found = true;
                }
            }
            if (!found) {
                throw new AssertionError("unknown record component " + componentName);
            }
            return (T) type.getDeclaredConstructor(parameterTypes)
                    .newInstance(arguments);
        } catch (InvocationTargetException exception) {
            if (exception.getCause() instanceof RuntimeException runtime) {
                throw runtime;
            }
            throw new AssertionError(exception.getCause());
        } catch (ReflectiveOperationException exception) {
            throw new AssertionError(exception);
        }
    }

    private static void assertRecordComponents(Class<?> type,
            String... expectedNames) {
        assertThat(Stream.of(type.getRecordComponents())
                .map(RecordComponent::getName)).containsExactly(expectedNames);
    }

    private static String sha256(byte[] value) {
        try {
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(value));
        } catch (NoSuchAlgorithmException exception) {
            throw new AssertionError(exception);
        }
    }

    private static AssignmentVector notApplicableVector(AssetType type,
            String country, Currency currency, String venue) {
        return new AssignmentVector("not-applicable-" + type + "-" + country
                + "-" + currency, sectorNotApplicable(type, country, currency,
                        venue), endpoint(ORIGINAL, ASSET_ID, venue, currency, AS_OF,
                                ENDPOINT_CLOSE, null),
                SectorReferenceLevelPairResolution.NotApplicable.class);
    }

    private record Fixture(
            SectorAssignmentResolution.Resolved assignment,
            EndpointPriceResolution endpoint,
            List<SectorReferenceIndexEvidence> references,
            List<SectorReferenceLevelObservation> basisLevels,
            List<SectorReferenceLevelObservation> endpointLevels,
            List<SectorIndexDivisorContinuityEvidence> continuities) {

        Fixture withAssignment(SectorAssignmentResolution.Resolved value) {
            return new Fixture(value, endpoint, references, basisLevels,
                    endpointLevels, continuities);
        }

        Fixture withEndpoint(EndpointPriceResolution value) {
            return new Fixture(assignment, value, references, basisLevels,
                    endpointLevels, continuities);
        }

        Fixture withReferences(List<SectorReferenceIndexEvidence> values) {
            return new Fixture(assignment, endpoint, List.copyOf(values), basisLevels,
                    endpointLevels, continuities);
        }

        Fixture withBasisLevels(List<SectorReferenceLevelObservation> values) {
            return new Fixture(assignment, endpoint, references, List.copyOf(values),
                    endpointLevels, continuities);
        }

        Fixture withEndpointLevels(
                List<SectorReferenceLevelObservation> values) {
            return new Fixture(assignment, endpoint, references, basisLevels,
                    List.copyOf(values), continuities);
        }

        Fixture withContinuities(
                List<SectorIndexDivisorContinuityEvidence> values) {
            return new Fixture(assignment, endpoint, references, basisLevels,
                    endpointLevels, List.copyOf(values));
        }
    }

    private record AssignmentVector(String label,
            SectorAssignmentResolution assignment,
            EndpointPriceResolution endpoint,
            Class<? extends SectorReferenceLevelPairResolution> expectedType) {
        @Override
        public String toString() {
            return label;
        }
    }

    private record FaultVector(String label, UnavailableReason expected,
            UnaryOperator<Fixture> mutation) {
        @Override
        public String toString() {
            return label;
        }
    }

    private record PrecedenceVector(String label, UnavailableReason expected,
            UnaryOperator<Fixture> mutation) {
        @Override
        public String toString() {
            return label;
        }
    }

    private record IntervalVector(String label, EffectiveInterval interval,
            boolean resolves) {
        @Override
        public String toString() {
            return label;
        }
    }
}
