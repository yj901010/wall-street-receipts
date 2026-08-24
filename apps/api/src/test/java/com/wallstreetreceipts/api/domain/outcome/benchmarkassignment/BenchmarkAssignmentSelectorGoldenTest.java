package com.wallstreetreceipts.api.domain.outcome.benchmarkassignment;

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
import com.wallstreetreceipts.api.domain.outcome.benchmarkassignment.BenchmarkAssetClassificationEvidence.EffectiveInterval;
import com.wallstreetreceipts.api.domain.outcome.benchmarkassignment.BenchmarkAssetClassificationEvidence.EndsAtExclusive;
import com.wallstreetreceipts.api.domain.outcome.benchmarkassignment.BenchmarkAssetClassificationEvidence.OpenEnded;
import com.wallstreetreceipts.api.domain.outcome.benchmarkassignment.BenchmarkAssignmentEvidence.BenchmarkReferenceKind;
import com.wallstreetreceipts.api.domain.outcome.benchmarkassignment.BenchmarkAssignmentResolution.NotApplicable;
import com.wallstreetreceipts.api.domain.outcome.benchmarkassignment.BenchmarkAssignmentResolution.NotApplicableReason;
import com.wallstreetreceipts.api.domain.outcome.benchmarkassignment.BenchmarkAssignmentResolution.ResolutionContext;
import com.wallstreetreceipts.api.domain.outcome.benchmarkassignment.BenchmarkAssignmentResolution.Resolved;
import com.wallstreetreceipts.api.domain.outcome.benchmarkassignment.BenchmarkAssignmentResolution.Unavailable;
import com.wallstreetreceipts.api.domain.outcome.benchmarkassignment.BenchmarkAssignmentResolution.UnavailableReason;
import com.wallstreetreceipts.api.domain.outcome.horizon.OutcomeBasis;
import com.wallstreetreceipts.api.domain.outcome.horizon.OutcomeBasis.Correction;
import com.wallstreetreceipts.api.domain.outcome.horizon.OutcomeBasis.Original;

class BenchmarkAssignmentSelectorGoldenTest {

    private static final String ASSET_ID = "asset-nvda";
    private static final String PRIMARY_VENUE_ID = "venue-xnas";
    private static final String US = "US";
    private static final String CA = "CA";
    private static final Currency USD = Currency.getInstance("USD");
    private static final Currency CAD = Currency.getInstance("CAD");
    private static final Currency EUR = Currency.getInstance("EUR");
    private static final Instant BASIS_TIME = Instant.parse("2026-08-20T14:00:00Z");
    private static final Instant AS_OF = Instant.parse("2026-08-20T14:01:00Z");
    private static final Instant INTERVAL_START = Instant.parse("2026-01-01T00:00:00Z");
    private static final Instant INTERVAL_END = Instant.parse("2027-01-01T00:00:00Z");
    private static final OutcomeBasis ORIGINAL = new Original("call-001", BASIS_TIME);
    private static final String POLICY_HASH =
            "7318514c2f50eda16b2d7ef35bc68d00d6a8b18a0f09f77130525fca2f32da69";
    private static final String CANONICAL_DEFINITION =
            "{\"policyVersion\":\"POINT_IN_TIME_EXPLICIT_US_EQUITY_ASSET_SPX_ASSIGNMENT_V1\","
            + "\"classificationEvidenceFields\":[\"classificationEvidenceId\",\"providerEventId\",\"basis\",\"assetId\",\"assetType\",\"primaryVenueId\",\"primaryVenueCountryCode\",\"currency\",\"classificationSourceId\",\"classificationSourceRevision\",\"provenanceId\",\"effectiveInterval\",\"availableAt\",\"capturedAt\"],"
            + "\"effectiveIntervalFields\":[\"startsAtInclusive\",\"end\"],"
            + "\"effectiveIntervalEndVariants\":{\"OpenEnded\":[],\"EndsAtExclusive\":[\"value\"]},"
            + "\"assignmentEvidenceFields\":[\"assignmentEvidenceId\",\"providerEventId\",\"basis\",\"assetId\",\"assetType\",\"primaryVenueId\",\"primaryVenueCountryCode\",\"currency\",\"assignmentSourceId\",\"assignmentSourceRevision\",\"provenanceId\",\"effectiveInterval\",\"benchmarkAssetId\",\"benchmarkAssetType\",\"benchmarkCurrency\",\"referenceKind\",\"availableAt\",\"capturedAt\"],"
            + "\"requestFields\":[\"policyVersion\",\"basis\",\"assetId\",\"evaluationAsOf\",\"classificationCandidates\",\"assignmentCandidates\"],"
            + "\"resolutionContextFields\":[\"policyVersion\",\"policyDefinitionHash\",\"basis\",\"assetId\",\"evaluationAsOf\"],"
            + "\"resultVariants\":{\"Resolved\":[\"context\",\"classificationEvidence\",\"assignmentEvidence\"],\"NotApplicable\":[\"context\",\"classificationEvidence\",\"reason\"],\"Unavailable\":[\"context\",\"reason\"]},"
            + "\"benchmarkReferenceKinds\":[\"PROVIDER_PUBLISHED_PRICE_INDEX\",\"PROVIDER_PUBLISHED_TOTAL_RETURN_INDEX\",\"NON_PROVIDER_PUBLISHED_PRICE_INDEX\",\"UNKNOWN\"],"
            + "\"basisModes\":[\"ORIGINAL\",\"CORRECTION\"],"
            + "\"cancellationBasisAllowed\":false,"
            + "\"requestTemporalRule\":\"basis.eventTime<=evaluationAsOf\","
            + "\"evidenceTemporalRule\":\"availableAt<=capturedAt\","
            + "\"pitPredicate\":\"availableAt<=evaluationAsOf&&capturedAt<=evaluationAsOf\","
            + "\"futureEvidenceRule\":\"INVISIBLE_TO_ALL_OUTPUT_AND_REASONING\","
            + "\"effectiveIntervalPredicate\":\"startsAtInclusive<=basis.eventTime&&(end==OpenEnded||basis.eventTime<end.value)\","
            + "\"effectiveIntervalBoundary\":\"START_INCLUSIVE_END_EXCLUSIVE\","
            + "\"openEndedRepresentation\":\"EXPLICIT_OPEN_ENDED_VARIANT\","
            + "\"venueCountryCodeFormat\":\"ISO_3166_1_ALPHA_2_UPPERCASE\","
            + "\"currencyRepresentation\":\"ISO_4217_CURRENCY\","
            + "\"classificationIdentity\":\"basis==request.basis&&assetId==request.assetId\","
            + "\"classificationCardinality\":\"EXACTLY_ONE_VISIBLE_VALID_RECORD\","
            + "\"inScopePredicate\":\"assetType==EQUITY&&primaryVenueCountryCode==US&&currency==USD\","
            + "\"notApplicableTruthTable\":{\"nonEquity\":\"NON_EQUITY\",\"equityNonUsUsd\":\"NON_US_PRIMARY_VENUE\",\"equityUsNonUsd\":\"NON_USD_CURRENCY\",\"equityNonUsNonUsd\":\"NON_US_PRIMARY_VENUE_AND_NON_USD_CURRENCY\"},"
            + "\"assignmentCoherence\":\"basis,assetId,assetType,primaryVenueId,primaryVenueCountryCode,currency==selectedClassification\","
            + "\"missingAssignmentTruthTable\":{\"outOfScope\":\"NOT_APPLICABLE\",\"inScope\":\"ASSIGNMENT_MISSING_AS_OF\"},"
            + "\"outOfScopeVisibleAssignmentRule\":\"OUT_OF_SCOPE_ASSIGNMENT_CONFLICT\","
            + "\"requiredBenchmarkAssetId\":\"asset-spx\","
            + "\"requiredBenchmarkAssetType\":\"INDEX\","
            + "\"requiredBenchmarkCurrency\":\"USD\","
            + "\"requiredBenchmarkReferenceKind\":\"PROVIDER_PUBLISHED_PRICE_INDEX\","
            + "\"assignmentCardinality\":\"EXACTLY_ONE_VISIBLE_VALID_RECORD_FOR_IN_SCOPE\","
            + "\"knownCandidateSetRule\":\"ANY_VISIBLE_MISMATCH_FAILS_CLOSED_BEFORE_CARDINALITY\","
            + "\"equalDuplicateRule\":\"AMBIGUOUS_NO_DEDUPLICATION\","
            + "\"candidateOrderRule\":\"ORDER_INDEPENDENT\","
            + "\"forbiddenInference\":[\"TICKER\",\"ISSUER_NAME\",\"EXCHANGE_LIKE_TEXT\",\"CURRENT_MASTER_DATA\",\"MARKET_SNAPSHOT_SPX\",\"P2_SP500_UNIVERSE\",\"MAP_OR_TREEMAP\",\"CURRENT_ROW\",\"LATEST_REVISION\",\"NEAREST_INTERVAL\",\"PROVIDER_PREFERENCE\",\"FALLBACK\"],"
            + "\"evaluationPrecedence\":[\"CLASSIFICATION_MISSING_AS_OF\",\"CLASSIFICATION_BASIS_MISMATCH\",\"CLASSIFICATION_ASSET_MISMATCH\",\"CLASSIFICATION_EFFECTIVE_INTERVAL_MISMATCH\",\"CLASSIFICATION_AMBIGUOUS\",\"ASSIGNMENT_MISSING_AS_OF_OR_NOT_APPLICABLE\",\"ASSIGNMENT_BASIS_MISMATCH\",\"ASSIGNMENT_ASSET_MISMATCH\",\"ASSIGNMENT_ASSET_TYPE_MISMATCH\",\"ASSIGNMENT_PRIMARY_VENUE_MISMATCH\",\"ASSIGNMENT_PRIMARY_VENUE_COUNTRY_MISMATCH\",\"ASSIGNMENT_CURRENCY_MISMATCH\",\"ASSIGNMENT_EFFECTIVE_INTERVAL_MISMATCH\",\"OUT_OF_SCOPE_ASSIGNMENT_CONFLICT\",\"BENCHMARK_ASSET_ID_MISMATCH\",\"BENCHMARK_ASSET_TYPE_MISMATCH\",\"BENCHMARK_CURRENCY_MISMATCH\",\"BENCHMARK_REFERENCE_KIND_MISMATCH\",\"ASSIGNMENT_AMBIGUOUS\",\"RESOLVE\"],"
            + "\"selectedEvidencePreservation\":\"EXACT_COMPLETE_RECORDS\","
            + "\"futureEvidenceOutputRule\":\"NEVER_ECHOED\","
            + "\"lifecycleMapping\":\"ABSENT\","
            + "\"calculatorInvocation\":\"ABSENT\","
            + "\"providerIntegration\":\"ABSENT\","
            + "\"fallbackBehavior\":\"ABSENT\"}";

    @Test
    void canonicalPolicyDefinitionHasExactUtf8BytesIndependentHashAndDefensiveReads()
            throws NoSuchAlgorithmException {
        byte[] first = policy().canonicalDefinitionUtf8();
        String independentHash = HexFormat.of().formatHex(
                MessageDigest.getInstance("SHA-256").digest(
                        CANONICAL_DEFINITION.getBytes(StandardCharsets.UTF_8)));

        assertThat(policy().canonicalDefinition()).isEqualTo(CANONICAL_DEFINITION);
        assertThat(first).hasSize(4261)
                .containsExactly(CANONICAL_DEFINITION.getBytes(StandardCharsets.UTF_8));
        assertThat(independentHash).isEqualTo(POLICY_HASH);
        assertThat(policy().definitionHash()).isEqualTo(POLICY_HASH);
        first[0] = (byte) '!';
        assertThat(policy().canonicalDefinitionUtf8()[0]).isEqualTo((byte) '{');
    }

    @Test
    void publicPolicyEvidenceRequestAndResultSurfacesRemainExactlyClosed() {
        assertThat(BenchmarkAssignmentPolicyVersion.values()).containsExactly(policy());
        assertThat(AssetType.values()).containsExactly(
                AssetType.INDEX, AssetType.EQUITY, AssetType.ETF,
                AssetType.BOND, AssetType.COMMODITY, AssetType.FX);
        assertThat(BenchmarkReferenceKind.values()).containsExactly(
                BenchmarkReferenceKind.PROVIDER_PUBLISHED_PRICE_INDEX,
                BenchmarkReferenceKind.PROVIDER_PUBLISHED_TOTAL_RETURN_INDEX,
                BenchmarkReferenceKind.NON_PROVIDER_PUBLISHED_PRICE_INDEX,
                BenchmarkReferenceKind.UNKNOWN);
        assertThat(NotApplicableReason.values()).containsExactly(
                NotApplicableReason.NON_EQUITY,
                NotApplicableReason.NON_US_PRIMARY_VENUE,
                NotApplicableReason.NON_USD_CURRENCY,
                NotApplicableReason.NON_US_PRIMARY_VENUE_AND_NON_USD_CURRENCY);
        assertThat(UnavailableReason.values()).containsExactly(
                UnavailableReason.CLASSIFICATION_MISSING_AS_OF,
                UnavailableReason.CLASSIFICATION_BASIS_MISMATCH,
                UnavailableReason.CLASSIFICATION_ASSET_MISMATCH,
                UnavailableReason.CLASSIFICATION_EFFECTIVE_INTERVAL_MISMATCH,
                UnavailableReason.CLASSIFICATION_AMBIGUOUS,
                UnavailableReason.ASSIGNMENT_MISSING_AS_OF,
                UnavailableReason.ASSIGNMENT_BASIS_MISMATCH,
                UnavailableReason.ASSIGNMENT_ASSET_MISMATCH,
                UnavailableReason.ASSIGNMENT_ASSET_TYPE_MISMATCH,
                UnavailableReason.ASSIGNMENT_PRIMARY_VENUE_MISMATCH,
                UnavailableReason.ASSIGNMENT_PRIMARY_VENUE_COUNTRY_MISMATCH,
                UnavailableReason.ASSIGNMENT_CURRENCY_MISMATCH,
                UnavailableReason.ASSIGNMENT_EFFECTIVE_INTERVAL_MISMATCH,
                UnavailableReason.OUT_OF_SCOPE_ASSIGNMENT_CONFLICT,
                UnavailableReason.BENCHMARK_ASSET_ID_MISMATCH,
                UnavailableReason.BENCHMARK_ASSET_TYPE_MISMATCH,
                UnavailableReason.BENCHMARK_CURRENCY_MISMATCH,
                UnavailableReason.BENCHMARK_REFERENCE_KIND_MISMATCH,
                UnavailableReason.ASSIGNMENT_AMBIGUOUS);
        assertThat(BenchmarkAssignmentResolution.class.getPermittedSubclasses())
                .containsExactlyInAnyOrder(Resolved.class, NotApplicable.class,
                        Unavailable.class);
        assertThat(BenchmarkAssetClassificationEvidence.EffectiveIntervalEnd.class
                .getPermittedSubclasses())
                .containsExactlyInAnyOrder(OpenEnded.class, EndsAtExclusive.class);
        assertRecordComponents(BenchmarkAssetClassificationEvidence.class,
                "classificationEvidenceId:String", "providerEventId:String",
                "basis:OutcomeBasis", "assetId:String", "assetType:AssetType",
                "primaryVenueId:String", "primaryVenueCountryCode:String",
                "currency:Currency", "classificationSourceId:String",
                "classificationSourceRevision:String", "provenanceId:String",
                "effectiveInterval:EffectiveInterval", "availableAt:Instant",
                "capturedAt:Instant");
        assertRecordComponents(BenchmarkAssignmentEvidence.class,
                "assignmentEvidenceId:String", "providerEventId:String",
                "basis:OutcomeBasis", "assetId:String", "assetType:AssetType",
                "primaryVenueId:String", "primaryVenueCountryCode:String",
                "currency:Currency", "assignmentSourceId:String",
                "assignmentSourceRevision:String", "provenanceId:String",
                "effectiveInterval:EffectiveInterval", "benchmarkAssetId:String",
                "benchmarkAssetType:AssetType", "benchmarkCurrency:Currency",
                "referenceKind:BenchmarkReferenceKind", "availableAt:Instant",
                "capturedAt:Instant");
        assertRecordComponents(BenchmarkAssignmentRequest.class,
                "policyVersion:BenchmarkAssignmentPolicyVersion", "basis:OutcomeBasis",
                "assetId:String", "evaluationAsOf:Instant",
                "classificationCandidates:List", "assignmentCandidates:List");
        assertRecordComponents(ResolutionContext.class,
                "policyVersion:BenchmarkAssignmentPolicyVersion",
                "policyDefinitionHash:String", "basis:OutcomeBasis", "assetId:String",
                "evaluationAsOf:Instant");
        assertRecordComponents(Resolved.class, "context:ResolutionContext",
                "classificationEvidence:BenchmarkAssetClassificationEvidence",
                "assignmentEvidence:BenchmarkAssignmentEvidence");
        assertRecordComponents(NotApplicable.class, "context:ResolutionContext",
                "classificationEvidence:BenchmarkAssetClassificationEvidence",
                "reason:NotApplicableReason");
        assertRecordComponents(Unavailable.class, "context:ResolutionContext",
                "reason:UnavailableReason");
    }

    @Test
    void resolvesOneExactOriginalAssignmentAtInclusivePitAndIntervalBoundaries() {
        BenchmarkAssetClassificationEvidence classification = classification(
                ORIGINAL, ASSET_ID, AssetType.EQUITY, PRIMARY_VENUE_ID, US, USD,
                bounded(BASIS_TIME, INTERVAL_END), AS_OF, AS_OF, "exact");
        BenchmarkAssignmentEvidence assignment = assignment(
                classification, bounded(BASIS_TIME, INTERVAL_END), AS_OF, AS_OF,
                "exact");

        Resolved result = assertResolved(select(
                ORIGINAL, List.of(classification), List.of(assignment)));

        assertThat(result.context()).isEqualTo(context(ORIGINAL));
        assertThat(result.classificationEvidence()).isSameAs(classification);
        assertThat(result.assignmentEvidence()).isSameAs(assignment);
        assertThat(result.assignmentEvidence().benchmarkAssetId()).isEqualTo("asset-spx");
        assertThat(result.assignmentEvidence().benchmarkAssetType())
                .isEqualTo(AssetType.INDEX);
        assertThat(result.assignmentEvidence().referenceKind())
                .isEqualTo(BenchmarkReferenceKind.PROVIDER_PUBLISHED_PRICE_INDEX);
    }

    @Test
    void correctionBasisIsAnIndependentCompleteIdentityNotAnEventTimeShortcut() {
        OutcomeBasis correction = new Correction(
                "call-001", "correction-2", BASIS_TIME);
        BenchmarkAssetClassificationEvidence classification = exactClassification(
                correction, "correction");
        BenchmarkAssignmentEvidence assignment = exactAssignment(
                classification, "correction");
        assertResolved(select(correction, List.of(classification), List.of(assignment)));

        BenchmarkAssetClassificationEvidence originalAtSameTime = exactClassification(
                ORIGINAL, "original");
        assertUnavailable(select(correction, List.of(originalAtSameTime), List.of()),
                UnavailableReason.CLASSIFICATION_BASIS_MISMATCH);
    }

    @ParameterizedTest(name = "effective interval boundary {0}")
    @MethodSource("effectiveIntervalBoundaryVectors")
    void effectiveIntervalsAreStartInclusiveEndExclusiveAndExplicitlyOpenEnded(
            String scenario,
            EffectiveInterval classificationInterval,
            EffectiveInterval assignmentInterval,
            UnavailableReason expectedReason) {
        BenchmarkAssetClassificationEvidence classification = classification(
                ORIGINAL, ASSET_ID, AssetType.EQUITY, PRIMARY_VENUE_ID, US, USD,
                classificationInterval, AS_OF, AS_OF, scenario);
        BenchmarkAssignmentEvidence assignment = assignment(
                classification, assignmentInterval, AS_OF, AS_OF, scenario);

        BenchmarkAssignmentResolution result = select(
                ORIGINAL, List.of(classification), List.of(assignment));
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
                        bounded(BASIS_TIME, INTERVAL_END), null),
                Arguments.of("bounded interior",
                        bounded(INTERVAL_START, INTERVAL_END),
                        bounded(INTERVAL_START, INTERVAL_END), null),
                Arguments.of("classification end equality excluded",
                        bounded(INTERVAL_START, BASIS_TIME),
                        bounded(INTERVAL_START, INTERVAL_END),
                        UnavailableReason.CLASSIFICATION_EFFECTIVE_INTERVAL_MISMATCH),
                Arguments.of("assignment end equality excluded",
                        bounded(INTERVAL_START, INTERVAL_END),
                        bounded(INTERVAL_START, BASIS_TIME),
                        UnavailableReason.ASSIGNMENT_EFFECTIVE_INTERVAL_MISMATCH),
                Arguments.of("open start equality", open(BASIS_TIME),
                        open(BASIS_TIME), null),
                Arguments.of("open interior", open(INTERVAL_START),
                        open(INTERVAL_START), null));
    }

    @Test
    void futureClassificationCandidatesAreIdenticalToAbsentAndInvisibleToReasoning() {
        BenchmarkAssetClassificationEvidence exact = exactClassification(ORIGINAL, "exact");
        BenchmarkAssignmentEvidence assignment = exactAssignment(exact, "exact");
        BenchmarkAssignmentResolution baseline = select(
                ORIGINAL, List.of(exact), List.of(assignment));
        BenchmarkAssetClassificationEvidence futureWrong = classification(
                new Correction("wrong-call", "wrong-revision", BASIS_TIME),
                "wrong-asset", AssetType.INDEX, "wrong-venue", CA, CAD,
                bounded(INTERVAL_START, BASIS_TIME),
                AS_OF.plusNanos(1_000), AS_OF.plusNanos(1_000), "future-wrong");
        BenchmarkAssetClassificationEvidence capturedFuture = classification(
                ORIGINAL, ASSET_ID, AssetType.EQUITY, PRIMARY_VENUE_ID, US, USD,
                exactInterval(), AS_OF, AS_OF.plusNanos(1_000), "captured-future");

        assertThat(select(ORIGINAL,
                List.of(exact, futureWrong, futureWrong, capturedFuture),
                List.of(assignment))).isEqualTo(baseline);
        assertUnavailable(select(ORIGINAL,
                List.of(futureWrong, futureWrong, capturedFuture), List.of()),
                UnavailableReason.CLASSIFICATION_MISSING_AS_OF);
        assertUnavailable(select(ORIGINAL,
                List.of(futureWrong, futureWrong, capturedFuture),
                List.of(assignment)),
                UnavailableReason.CLASSIFICATION_MISSING_AS_OF);
    }

    @Test
    void futureAssignmentCandidatesAreIdenticalToAbsentAndInvisibleToReasoning() {
        BenchmarkAssetClassificationEvidence exact = exactClassification(ORIGINAL, "exact");
        BenchmarkAssignmentEvidence assignment = exactAssignment(exact, "exact");
        BenchmarkAssignmentResolution baseline = select(
                ORIGINAL, List.of(exact), List.of(assignment));
        BenchmarkAssignmentEvidence futureWrong = assignment(
                classification(new Correction("wrong-call", "wrong-revision", BASIS_TIME),
                        "wrong-asset", AssetType.INDEX, "wrong-venue", CA, CAD,
                        exactInterval(), AS_OF, AS_OF, "future-source"),
                bounded(INTERVAL_START, BASIS_TIME), AS_OF.plusNanos(1_000),
                AS_OF.plusNanos(1_000), "future-wrong");
        BenchmarkAssignmentEvidence capturedFuture = assignment(
                exact, exactInterval(), AS_OF, AS_OF.plusNanos(1_000),
                "captured-future");

        assertThat(select(ORIGINAL, List.of(exact),
                List.of(assignment, futureWrong, futureWrong, capturedFuture)))
                .isEqualTo(baseline);
        assertUnavailable(select(ORIGINAL, List.of(exact),
                List.of(futureWrong, futureWrong, capturedFuture)),
                UnavailableReason.ASSIGNMENT_MISSING_AS_OF);

        BenchmarkAssetClassificationEvidence outOfScope = classification(
                ORIGINAL, ASSET_ID, AssetType.EQUITY, PRIMARY_VENUE_ID, CA, USD,
                exactInterval(), AS_OF, AS_OF, "out-of-scope");
        assertNotApplicable(select(ORIGINAL, List.of(outOfScope),
                List.of(futureWrong)), NotApplicableReason.NON_US_PRIMARY_VENUE);
    }

    @Test
    void exactPitTimestampEqualityIsVisibleForBothEvidenceKinds() {
        BenchmarkAssetClassificationEvidence classification = exactClassification(
                ORIGINAL, "pit-equality");
        BenchmarkAssignmentEvidence assignment = exactAssignment(
                classification, "pit-equality");
        assertThat(classification.availableAt()).isEqualTo(AS_OF);
        assertThat(classification.capturedAt()).isEqualTo(AS_OF);
        assertThat(assignment.availableAt()).isEqualTo(AS_OF);
        assertThat(assignment.capturedAt()).isEqualTo(AS_OF);
        assertResolved(select(ORIGINAL, List.of(classification), List.of(assignment)));
    }

    @ParameterizedTest(name = "classification mismatch {0}")
    @EnumSource(ClassificationFault.class)
    void everyVisibleClassificationMismatchUsesItsExactReason(
            ClassificationFault fault) {
        BenchmarkAssetClassificationEvidence candidate = classificationFault(fault);
        assertUnavailable(select(ORIGINAL, List.of(candidate), List.of()), fault.reason);
    }

    @ParameterizedTest(name = "classification precedence pair {0}")
    @MethodSource("classificationPrecedencePairs")
    void classificationMismatchPrecedenceIsInputOrderIndependent(
            ClassificationFault earlier, ClassificationFault later) {
        BenchmarkAssetClassificationEvidence first = classificationFault(earlier);
        BenchmarkAssetClassificationEvidence second = classificationFault(later);
        assertUnavailable(select(ORIGINAL, List.of(first, second), List.of()),
                earlier.reason);
        assertUnavailable(select(ORIGINAL, List.of(second, first), List.of()),
                earlier.reason);
    }

    static Stream<Arguments> classificationPrecedencePairs() {
        return Stream.of(
                Arguments.of(ClassificationFault.BASIS, ClassificationFault.ASSET),
                Arguments.of(ClassificationFault.ASSET, ClassificationFault.INTERVAL));
    }

    @Test
    void exactVisibleClassificationDuplicatesAreAmbiguousWithoutDeduplication() {
        BenchmarkAssetClassificationEvidence exact = exactClassification(ORIGINAL, "exact");
        BenchmarkAssignmentEvidence laterFault = assignmentFault(
                exact, AssignmentFault.CURRENCY);
        assertUnavailable(select(ORIGINAL, List.of(exact, exact),
                List.of(laterFault)),
                UnavailableReason.CLASSIFICATION_AMBIGUOUS);
    }

    @ParameterizedTest(name = "non-equity {0} is intentionally not applicable")
    @EnumSource(value = AssetType.class, names = "EQUITY", mode = EnumSource.Mode.EXCLUDE)
    void everyNonEquityAssetTypeIsNotApplicableWithoutAnAssignment(AssetType type) {
        BenchmarkAssetClassificationEvidence classification = classification(
                ORIGINAL, ASSET_ID, type, PRIMARY_VENUE_ID, US, USD,
                exactInterval(), AS_OF, AS_OF, type.name());
        NotApplicable result = assertNotApplicable(select(
                ORIGINAL, List.of(classification), List.of()),
                NotApplicableReason.NON_EQUITY);
        assertThat(result.classificationEvidence()).isSameAs(classification);
    }

    @ParameterizedTest(name = "equity scope truth table {0}")
    @MethodSource("equityScopeVectors")
    void equityOutsideV1ScopeUsesTheExactThreeWayNotApplicableTruthTable(
            String country,
            Currency currency,
            NotApplicableReason reason) {
        BenchmarkAssetClassificationEvidence classification = classification(
                ORIGINAL, ASSET_ID, AssetType.EQUITY, PRIMARY_VENUE_ID,
                country, currency, exactInterval(), AS_OF, AS_OF, reason.name());
        assertNotApplicable(select(ORIGINAL, List.of(classification), List.of()), reason);
    }

    static Stream<Arguments> equityScopeVectors() {
        return Stream.of(
                Arguments.of(CA, USD, NotApplicableReason.NON_US_PRIMARY_VENUE),
                Arguments.of(US, EUR, NotApplicableReason.NON_USD_CURRENCY),
                Arguments.of(CA, CAD,
                        NotApplicableReason
                                .NON_US_PRIMARY_VENUE_AND_NON_USD_CURRENCY));
    }

    @ParameterizedTest(name = "out-of-scope coherent assignment conflicts for {0}")
    @MethodSource("outOfScopeVectors")
    void everyOutOfScopeStateWithAVisibleCoherentAssignmentFailsClosed(
            AssetType type,
            String country,
            Currency currency) {
        BenchmarkAssetClassificationEvidence classification = classification(
                ORIGINAL, ASSET_ID, type, PRIMARY_VENUE_ID, country, currency,
                exactInterval(), AS_OF, AS_OF, "out-of-scope");
        BenchmarkAssignmentEvidence assignment = exactAssignment(
                classification, "out-of-scope");
        assertUnavailable(select(ORIGINAL, List.of(classification), List.of(assignment)),
                UnavailableReason.OUT_OF_SCOPE_ASSIGNMENT_CONFLICT);
        assertUnavailable(select(ORIGINAL, List.of(classification),
                List.of(assignmentFault(classification, AssignmentFault.BASIS))),
                UnavailableReason.ASSIGNMENT_BASIS_MISMATCH);
        for (AssignmentFault targetFault : List.of(
                AssignmentFault.BENCHMARK_ASSET,
                AssignmentFault.BENCHMARK_TYPE,
                AssignmentFault.BENCHMARK_CURRENCY,
                AssignmentFault.REFERENCE_KIND)) {
            assertUnavailable(select(ORIGINAL, List.of(classification),
                    List.of(assignmentFault(classification, targetFault))),
                    UnavailableReason.OUT_OF_SCOPE_ASSIGNMENT_CONFLICT);
        }
    }

    static Stream<Arguments> outOfScopeVectors() {
        return Stream.of(
                Arguments.of(AssetType.INDEX, US, USD),
                Arguments.of(AssetType.EQUITY, CA, USD),
                Arguments.of(AssetType.EQUITY, US, EUR),
                Arguments.of(AssetType.EQUITY, CA, CAD));
    }

    @ParameterizedTest(name = "missing assignment scope branch inScope={0}")
    @ValueSource(booleans = {true, false})
    void missingVisibleAssignmentDistinguishesExpectedEvidenceFromIntentionalScope(
            boolean inScope) {
        BenchmarkAssetClassificationEvidence classification = classification(
                ORIGINAL, ASSET_ID, AssetType.EQUITY, PRIMARY_VENUE_ID,
                inScope ? US : CA, USD, exactInterval(), AS_OF, AS_OF, "missing");
        BenchmarkAssignmentResolution result = select(
                ORIGINAL, List.of(classification), List.of());
        if (inScope) {
            assertUnavailable(result, UnavailableReason.ASSIGNMENT_MISSING_AS_OF);
        } else {
            assertNotApplicable(result, NotApplicableReason.NON_US_PRIMARY_VENUE);
        }
    }

    @ParameterizedTest(name = "assignment coherence mismatch {0}")
    @EnumSource(value = AssignmentFault.class, names = {
            "BASIS", "ASSET", "ASSET_TYPE", "VENUE", "COUNTRY", "CURRENCY",
            "INTERVAL"})
    void everyVisibleAssignmentCoherenceMismatchUsesItsExactReason(
            AssignmentFault fault) {
        BenchmarkAssetClassificationEvidence classification = exactClassification(
                ORIGINAL, "classification");
        assertUnavailable(select(ORIGINAL, List.of(classification),
                List.of(assignmentFault(classification, fault))), fault.reason);
    }

    @ParameterizedTest(name = "benchmark target mismatch {0}")
    @EnumSource(value = AssignmentFault.class, names = {
            "BENCHMARK_ASSET", "BENCHMARK_TYPE", "BENCHMARK_CURRENCY",
            "REFERENCE_KIND"})
    void everyVisibleBenchmarkTargetMismatchUsesItsExactReason(
            AssignmentFault fault) {
        BenchmarkAssetClassificationEvidence classification = exactClassification(
                ORIGINAL, "classification");
        if (fault == AssignmentFault.REFERENCE_KIND) {
            for (BenchmarkReferenceKind invalid : List.of(
                    BenchmarkReferenceKind.PROVIDER_PUBLISHED_TOTAL_RETURN_INDEX,
                    BenchmarkReferenceKind.NON_PROVIDER_PUBLISHED_PRICE_INDEX,
                    BenchmarkReferenceKind.UNKNOWN)) {
                BenchmarkAssignmentEvidence assignment = assignmentWith(
                        classification, ORIGINAL, ASSET_ID, AssetType.EQUITY,
                        PRIMARY_VENUE_ID, US, USD, exactInterval(), "asset-spx",
                        AssetType.INDEX, USD, invalid, AS_OF, AS_OF,
                        invalid.name());
                assertUnavailable(select(ORIGINAL, List.of(classification),
                        List.of(assignment)), fault.reason);
            }
        } else {
            assertUnavailable(select(ORIGINAL, List.of(classification),
                    List.of(assignmentFault(classification, fault))), fault.reason);
        }
    }

    @ParameterizedTest(name = "assignment precedence pair {0}")
    @MethodSource("assignmentPrecedencePairs")
    void assignmentMismatchPrecedenceIsInputOrderIndependent(
            AssignmentFault earlier, AssignmentFault later) {
        BenchmarkAssetClassificationEvidence classification = exactClassification(
                ORIGINAL, "classification");
        BenchmarkAssignmentEvidence first = assignmentFault(classification, earlier);
        BenchmarkAssignmentEvidence second = assignmentFault(classification, later);
        assertUnavailable(select(ORIGINAL, List.of(classification),
                List.of(first, second)), earlier.reason);
        assertUnavailable(select(ORIGINAL, List.of(classification),
                List.of(second, first)), earlier.reason);
    }

    static Stream<Arguments> assignmentPrecedencePairs() {
        AssignmentFault[] faults = AssignmentFault.values();
        return java.util.stream.IntStream.range(0, faults.length - 1)
                .mapToObj(index -> Arguments.of(faults[index], faults[index + 1]));
    }

    @Test
    void exactVisibleAssignmentDuplicatesAreAmbiguousWithoutDeduplication() {
        BenchmarkAssetClassificationEvidence classification = exactClassification(
                ORIGINAL, "classification");
        BenchmarkAssignmentEvidence assignment = exactAssignment(
                classification, "assignment");
        assertUnavailable(select(ORIGINAL, List.of(classification),
                List.of(assignment, assignment)),
                UnavailableReason.ASSIGNMENT_AMBIGUOUS);
    }

    @ParameterizedTest(name = "classification mismatch {0} precedes ambiguity")
    @EnumSource(ClassificationFault.class)
    void everyClassificationMismatchPoisonsValidDuplicatesBeforeAmbiguity(
            ClassificationFault fault) {
        BenchmarkAssetClassificationEvidence classification = exactClassification(
                ORIGINAL, "classification");
        BenchmarkAssignmentEvidence assignment = exactAssignment(
                classification, "assignment");
        BenchmarkAssetClassificationEvidence wrong = classificationFault(fault);

        assertUnavailable(select(ORIGINAL,
                List.of(classification, classification, wrong), List.of(assignment)),
                fault.reason);
        assertUnavailable(select(ORIGINAL,
                List.of(wrong, classification, classification), List.of(assignment)),
                fault.reason);
    }

    @ParameterizedTest(name = "assignment mismatch {0} precedes ambiguity")
    @EnumSource(AssignmentFault.class)
    void everyAssignmentMismatchPoisonsValidDuplicatesBeforeAmbiguity(
            AssignmentFault fault) {
        BenchmarkAssetClassificationEvidence classification = exactClassification(
                ORIGINAL, "classification");
        BenchmarkAssignmentEvidence assignment = exactAssignment(
                classification, "assignment");
        BenchmarkAssignmentEvidence wrong = assignmentFault(classification, fault);

        assertUnavailable(select(ORIGINAL, List.of(classification),
                List.of(assignment, assignment, wrong)), fault.reason);
        assertUnavailable(select(ORIGINAL, List.of(classification),
                List.of(wrong, assignment, assignment)), fault.reason);
    }

    @Test
    void requestDefensivelyCopiesListsAndRejectsEveryMissingPublicInput() {
        BenchmarkAssetClassificationEvidence classification = exactClassification(
                ORIGINAL, "classification");
        BenchmarkAssignmentEvidence assignment = exactAssignment(
                classification, "assignment");
        List<BenchmarkAssetClassificationEvidence> classifications =
                new ArrayList<>(List.of(classification));
        List<BenchmarkAssignmentEvidence> assignments =
                new ArrayList<>(List.of(assignment));
        BenchmarkAssignmentRequest request = new BenchmarkAssignmentRequest(
                policy(), ORIGINAL, ASSET_ID, AS_OF, classifications, assignments);
        classifications.clear();
        assignments.clear();
        assertThat(request.classificationCandidates()).containsExactly(classification);
        assertThat(request.assignmentCandidates()).containsExactly(assignment);
        assertThatThrownBy(() -> request.classificationCandidates().clear())
                .isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> request.assignmentCandidates().clear())
                .isInstanceOf(UnsupportedOperationException.class);

        List<ThrowingCallable> invalid = List.of(
                () -> new BenchmarkAssignmentRequest(null, ORIGINAL, ASSET_ID, AS_OF,
                        List.of(), List.of()),
                () -> new BenchmarkAssignmentRequest(policy(), null, ASSET_ID, AS_OF,
                        List.of(), List.of()),
                () -> new BenchmarkAssignmentRequest(policy(), ORIGINAL, null, AS_OF,
                        List.of(), List.of()),
                () -> new BenchmarkAssignmentRequest(policy(), ORIGINAL, ASSET_ID, null,
                        List.of(), List.of()),
                () -> new BenchmarkAssignmentRequest(policy(), ORIGINAL, ASSET_ID, AS_OF,
                        null, List.of()),
                () -> new BenchmarkAssignmentRequest(policy(), ORIGINAL, ASSET_ID, AS_OF,
                        Arrays.asList((BenchmarkAssetClassificationEvidence) null),
                        List.of()),
                () -> new BenchmarkAssignmentRequest(policy(), ORIGINAL, ASSET_ID, AS_OF,
                        List.of(), null),
                () -> new BenchmarkAssignmentRequest(policy(), ORIGINAL, ASSET_ID, AS_OF,
                        List.of(), Arrays.asList((BenchmarkAssignmentEvidence) null)),
                () -> BenchmarkAssignmentSelector.select(null));
        invalid.forEach(callable -> assertThatThrownBy(callable)
                .isInstanceOf(NullPointerException.class));
    }

    @Test
    void requestRejectsPreBasisAndSubMicrosecondEvaluationInstants() {
        assertThatThrownBy(() -> new BenchmarkAssignmentRequest(
                policy(), ORIGINAL, ASSET_ID, BASIS_TIME.minusNanos(1_000),
                List.of(), List.of())).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new BenchmarkAssignmentRequest(
                policy(), ORIGINAL, ASSET_ID, AS_OF.plusNanos(1),
                List.of(), List.of())).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void classificationEvidenceRejectsEveryNullBlankAndUntrimmedCanonicalText() {
        List<String> fields = List.of(
                "classificationEvidenceId", "providerEventId", "assetId",
                "primaryVenueId", "classificationSourceId",
                "classificationSourceRevision", "provenanceId");
        for (String field : fields) {
            for (String invalid : Arrays.asList(null, "", " ", " value ")) {
                assertThatThrownBy(() -> classificationWithText(field, invalid))
                        .as(field + "=" + invalid)
                        .isInstanceOfAny(NullPointerException.class,
                                IllegalArgumentException.class);
            }
        }
    }

    @Test
    void assignmentEvidenceRejectsEveryNullBlankAndUntrimmedCanonicalText() {
        List<String> fields = List.of(
                "assignmentEvidenceId", "providerEventId", "assetId",
                "primaryVenueId", "assignmentSourceId",
                "assignmentSourceRevision", "provenanceId", "benchmarkAssetId");
        for (String field : fields) {
            for (String invalid : Arrays.asList(null, "", " ", " value ")) {
                assertThatThrownBy(() -> assignmentWithText(field, invalid))
                        .as(field + "=" + invalid)
                        .isInstanceOfAny(NullPointerException.class,
                                IllegalArgumentException.class);
            }
        }
    }

    @ParameterizedTest(name = "invalid ISO country code [{0}]")
    @ValueSource(strings = {"", " ", "us", "USA", "U1", "ZZ"})
    void bothEvidenceRecordsRejectInvalidVenueCountryCodes(String invalid) {
        assertThatThrownBy(() -> classification(
                ORIGINAL, ASSET_ID, AssetType.EQUITY, PRIMARY_VENUE_ID, invalid, USD,
                exactInterval(), AS_OF, AS_OF, "invalid-country"))
                .isInstanceOf(IllegalArgumentException.class);
        BenchmarkAssetClassificationEvidence classification = exactClassification(
                ORIGINAL, "classification");
        assertThatThrownBy(() -> assignmentWith(
                classification, classification.basis(), classification.assetId(),
                classification.assetType(), classification.primaryVenueId(), invalid,
                classification.currency(), classification.effectiveInterval(),
                "asset-spx", AssetType.INDEX, USD,
                BenchmarkReferenceKind.PROVIDER_PUBLISHED_PRICE_INDEX,
                AS_OF, AS_OF, "invalid-country"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void classificationConstructorRejectsEveryMissingOrMalformedStructuralComponent() {
        List<ThrowingCallable> invalid = List.of(
                () -> classification(null, ASSET_ID, AssetType.EQUITY,
                        PRIMARY_VENUE_ID, US, USD, exactInterval(), AS_OF, AS_OF, "x"),
                () -> classification(ORIGINAL, ASSET_ID, null,
                        PRIMARY_VENUE_ID, US, USD, exactInterval(), AS_OF, AS_OF, "x"),
                () -> classification(ORIGINAL, ASSET_ID, AssetType.EQUITY,
                        PRIMARY_VENUE_ID, US, null, exactInterval(), AS_OF, AS_OF, "x"),
                () -> classification(ORIGINAL, ASSET_ID, AssetType.EQUITY,
                        PRIMARY_VENUE_ID, null, USD, exactInterval(), AS_OF, AS_OF, "x"),
                () -> classification(ORIGINAL, ASSET_ID, AssetType.EQUITY,
                        PRIMARY_VENUE_ID, US, USD, null, AS_OF, AS_OF, "x"),
                () -> classification(ORIGINAL, ASSET_ID, AssetType.EQUITY,
                        PRIMARY_VENUE_ID, US, USD, exactInterval(), null, AS_OF, "x"),
                () -> classification(ORIGINAL, ASSET_ID, AssetType.EQUITY,
                        PRIMARY_VENUE_ID, US, USD, exactInterval(), AS_OF, null, "x"),
                () -> classification(ORIGINAL, ASSET_ID, AssetType.EQUITY,
                        PRIMARY_VENUE_ID, US, USD, exactInterval(),
                        AS_OF.plusNanos(1), AS_OF.plusNanos(1), "x"),
                () -> classification(ORIGINAL, ASSET_ID, AssetType.EQUITY,
                        PRIMARY_VENUE_ID, US, USD, exactInterval(),
                        AS_OF, AS_OF.plusNanos(1), "x"),
                () -> classification(ORIGINAL, ASSET_ID, AssetType.EQUITY,
                        PRIMARY_VENUE_ID, US, USD, exactInterval(),
                        AS_OF, AS_OF.minusNanos(1_000), "x"));
        invalid.forEach(callable -> assertThatThrownBy(callable)
                .isInstanceOfAny(NullPointerException.class,
                        IllegalArgumentException.class));
    }

    @Test
    void assignmentConstructorRejectsEveryMissingOrMalformedStructuralComponent() {
        BenchmarkAssetClassificationEvidence classification = exactClassification(
                ORIGINAL, "classification");
        List<ThrowingCallable> invalid = List.of(
                () -> assignmentWith(classification, null, ASSET_ID, AssetType.EQUITY,
                        PRIMARY_VENUE_ID, US, USD, exactInterval(), "asset-spx",
                        AssetType.INDEX, USD,
                        BenchmarkReferenceKind.PROVIDER_PUBLISHED_PRICE_INDEX,
                        AS_OF, AS_OF, "x"),
                () -> assignmentWith(classification, ORIGINAL, ASSET_ID, null,
                        PRIMARY_VENUE_ID, US, USD, exactInterval(), "asset-spx",
                        AssetType.INDEX, USD,
                        BenchmarkReferenceKind.PROVIDER_PUBLISHED_PRICE_INDEX,
                        AS_OF, AS_OF, "x"),
                () -> assignmentWith(classification, ORIGINAL, ASSET_ID,
                        AssetType.EQUITY, PRIMARY_VENUE_ID, US, null, exactInterval(),
                        "asset-spx", AssetType.INDEX, USD,
                        BenchmarkReferenceKind.PROVIDER_PUBLISHED_PRICE_INDEX,
                        AS_OF, AS_OF, "x"),
                () -> assignmentWith(classification, ORIGINAL, ASSET_ID,
                        AssetType.EQUITY, PRIMARY_VENUE_ID, null, USD, exactInterval(),
                        "asset-spx", AssetType.INDEX, USD,
                        BenchmarkReferenceKind.PROVIDER_PUBLISHED_PRICE_INDEX,
                        AS_OF, AS_OF, "x"),
                () -> assignmentWith(classification, ORIGINAL, ASSET_ID,
                        AssetType.EQUITY, PRIMARY_VENUE_ID, US, USD, null,
                        "asset-spx", AssetType.INDEX, USD,
                        BenchmarkReferenceKind.PROVIDER_PUBLISHED_PRICE_INDEX,
                        AS_OF, AS_OF, "x"),
                () -> assignmentWith(classification, ORIGINAL, ASSET_ID,
                        AssetType.EQUITY, PRIMARY_VENUE_ID, US, USD, exactInterval(),
                        "asset-spx", null, USD,
                        BenchmarkReferenceKind.PROVIDER_PUBLISHED_PRICE_INDEX,
                        AS_OF, AS_OF, "x"),
                () -> assignmentWith(classification, ORIGINAL, ASSET_ID,
                        AssetType.EQUITY, PRIMARY_VENUE_ID, US, USD, exactInterval(),
                        "asset-spx", AssetType.INDEX, null,
                        BenchmarkReferenceKind.PROVIDER_PUBLISHED_PRICE_INDEX,
                        AS_OF, AS_OF, "x"),
                () -> assignmentWith(classification, ORIGINAL, ASSET_ID,
                        AssetType.EQUITY, PRIMARY_VENUE_ID, US, USD, exactInterval(),
                        "asset-spx", AssetType.INDEX, USD, null, AS_OF, AS_OF, "x"),
                () -> assignmentWith(classification, ORIGINAL, ASSET_ID,
                        AssetType.EQUITY, PRIMARY_VENUE_ID, US, USD, exactInterval(),
                        "asset-spx", AssetType.INDEX, USD,
                        BenchmarkReferenceKind.PROVIDER_PUBLISHED_PRICE_INDEX,
                        null, AS_OF, "x"),
                () -> assignmentWith(classification, ORIGINAL, ASSET_ID,
                        AssetType.EQUITY, PRIMARY_VENUE_ID, US, USD, exactInterval(),
                        "asset-spx", AssetType.INDEX, USD,
                        BenchmarkReferenceKind.PROVIDER_PUBLISHED_PRICE_INDEX,
                        AS_OF, null, "x"),
                () -> assignmentWith(classification, ORIGINAL, ASSET_ID,
                        AssetType.EQUITY, PRIMARY_VENUE_ID, US, USD, exactInterval(),
                        "asset-spx", AssetType.INDEX, USD,
                        BenchmarkReferenceKind.PROVIDER_PUBLISHED_PRICE_INDEX,
                        AS_OF.plusNanos(1), AS_OF.plusNanos(1), "x"),
                () -> assignmentWith(classification, ORIGINAL, ASSET_ID,
                        AssetType.EQUITY, PRIMARY_VENUE_ID, US, USD, exactInterval(),
                        "asset-spx", AssetType.INDEX, USD,
                        BenchmarkReferenceKind.PROVIDER_PUBLISHED_PRICE_INDEX,
                        AS_OF, AS_OF.plusNanos(1), "x"),
                () -> assignmentWith(classification, ORIGINAL, ASSET_ID,
                        AssetType.EQUITY, PRIMARY_VENUE_ID, US, USD, exactInterval(),
                        "asset-spx", AssetType.INDEX, USD,
                        BenchmarkReferenceKind.PROVIDER_PUBLISHED_PRICE_INDEX,
                        AS_OF, AS_OF.minusNanos(1_000), "x"));
        invalid.forEach(callable -> assertThatThrownBy(callable)
                .isInstanceOfAny(NullPointerException.class,
                        IllegalArgumentException.class));
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
        BenchmarkAssetClassificationEvidence classification = exactClassification(
                ORIGINAL, "classification");
        BenchmarkAssignmentEvidence assignment = exactAssignment(
                classification, "assignment");
        ResolutionContext context = context(ORIGINAL);
        List<ThrowingCallable> invalidContext = List.of(
                () -> new ResolutionContext(null, POLICY_HASH, ORIGINAL, ASSET_ID, AS_OF),
                () -> new ResolutionContext(policy(), null, ORIGINAL, ASSET_ID, AS_OF),
                () -> new ResolutionContext(policy(), "0".repeat(64), ORIGINAL,
                        ASSET_ID, AS_OF),
                () -> new ResolutionContext(policy(), POLICY_HASH, null, ASSET_ID, AS_OF),
                () -> new ResolutionContext(policy(), POLICY_HASH, ORIGINAL, null, AS_OF),
                () -> new ResolutionContext(policy(), POLICY_HASH, ORIGINAL, ASSET_ID, null),
                () -> new ResolutionContext(policy(), POLICY_HASH, ORIGINAL, ASSET_ID,
                        BASIS_TIME.minusNanos(1_000)),
                () -> new ResolutionContext(policy(), POLICY_HASH, ORIGINAL, ASSET_ID,
                        AS_OF.plusNanos(1)));
        invalidContext.forEach(callable -> assertThatThrownBy(callable)
                .isInstanceOfAny(NullPointerException.class,
                        IllegalArgumentException.class));

        assertThatThrownBy(() -> new Resolved(null, classification, assignment))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new Resolved(context, null, assignment))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new Resolved(context, classification, null))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new Resolved(context,
                classificationFault(ClassificationFault.ASSET), assignment))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new Resolved(context, classification,
                assignmentFault(classification, AssignmentFault.BENCHMARK_TYPE)))
                .isInstanceOf(IllegalArgumentException.class);
        BenchmarkAssetClassificationEvidence futureClassification = classification(
                ORIGINAL, ASSET_ID, AssetType.EQUITY, PRIMARY_VENUE_ID, US, USD,
                exactInterval(), AS_OF.plusNanos(1_000), AS_OF.plusNanos(1_000),
                "future-classification");
        BenchmarkAssignmentEvidence futureAssignment = assignment(
                classification, exactInterval(), AS_OF.plusNanos(1_000),
                AS_OF.plusNanos(1_000), "future-assignment");
        assertThatThrownBy(() -> new Resolved(
                context, futureClassification, assignment))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new Resolved(
                context, classification, futureAssignment))
                .isInstanceOf(IllegalArgumentException.class);

        BenchmarkAssetClassificationEvidence nonUs = classification(
                ORIGINAL, ASSET_ID, AssetType.EQUITY, PRIMARY_VENUE_ID, CA, USD,
                exactInterval(), AS_OF, AS_OF, "non-us");
        assertThatThrownBy(() -> new NotApplicable(null, nonUs,
                NotApplicableReason.NON_US_PRIMARY_VENUE))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new NotApplicable(context, null,
                NotApplicableReason.NON_US_PRIMARY_VENUE))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new NotApplicable(context, nonUs, null))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new NotApplicable(context, classification,
                NotApplicableReason.NON_US_PRIMARY_VENUE))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new NotApplicable(context, nonUs,
                NotApplicableReason.NON_USD_CURRENCY))
                .isInstanceOf(IllegalArgumentException.class);
        BenchmarkAssetClassificationEvidence futureNonUs = classification(
                ORIGINAL, ASSET_ID, AssetType.EQUITY, PRIMARY_VENUE_ID, CA, USD,
                exactInterval(), AS_OF.plusNanos(1_000), AS_OF.plusNanos(1_000),
                "future-non-us");
        assertThatThrownBy(() -> new NotApplicable(context, futureNonUs,
                NotApplicableReason.NON_US_PRIMARY_VENUE))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new Unavailable(null,
                UnavailableReason.CLASSIFICATION_MISSING_AS_OF))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new Unavailable(context, null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void replayIsIndependentOfInputOrderJvmDefaultsAndPriorCalls() {
        BenchmarkAssetClassificationEvidence classification = exactClassification(
                ORIGINAL, "classification");
        BenchmarkAssignmentEvidence currencyFault = assignmentFault(
                classification, AssignmentFault.CURRENCY);
        BenchmarkAssignmentEvidence laterFault = assignmentFault(
                classification, AssignmentFault.BENCHMARK_ASSET);
        BenchmarkAssignmentResolution baseline = select(ORIGINAL,
                List.of(classification), List.of(currencyFault, laterFault));
        assertThat(select(ORIGINAL, List.of(classification),
                List.of(laterFault, currencyFault))).isEqualTo(baseline);
        assertNotApplicable(select(ORIGINAL, List.of(classification(
                ORIGINAL, ASSET_ID, AssetType.INDEX, PRIMARY_VENUE_ID, US, USD,
                exactInterval(), AS_OF, AS_OF, "prior")), List.of()),
                NotApplicableReason.NON_EQUITY);

        Locale originalLocale = Locale.getDefault();
        TimeZone originalTimeZone = TimeZone.getDefault();
        try {
            Locale.setDefault(Locale.forLanguageTag("tr-TR"));
            TimeZone.setDefault(TimeZone.getTimeZone("Pacific/Kiritimati"));
            assertThat(select(ORIGINAL, List.of(classification),
                    List.of(laterFault, currencyFault))).isEqualTo(baseline);
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

    private enum AssignmentFault {
        BASIS(UnavailableReason.ASSIGNMENT_BASIS_MISMATCH),
        ASSET(UnavailableReason.ASSIGNMENT_ASSET_MISMATCH),
        ASSET_TYPE(UnavailableReason.ASSIGNMENT_ASSET_TYPE_MISMATCH),
        VENUE(UnavailableReason.ASSIGNMENT_PRIMARY_VENUE_MISMATCH),
        COUNTRY(UnavailableReason.ASSIGNMENT_PRIMARY_VENUE_COUNTRY_MISMATCH),
        CURRENCY(UnavailableReason.ASSIGNMENT_CURRENCY_MISMATCH),
        INTERVAL(UnavailableReason.ASSIGNMENT_EFFECTIVE_INTERVAL_MISMATCH),
        BENCHMARK_ASSET(UnavailableReason.BENCHMARK_ASSET_ID_MISMATCH),
        BENCHMARK_TYPE(UnavailableReason.BENCHMARK_ASSET_TYPE_MISMATCH),
        BENCHMARK_CURRENCY(UnavailableReason.BENCHMARK_CURRENCY_MISMATCH),
        REFERENCE_KIND(UnavailableReason.BENCHMARK_REFERENCE_KIND_MISMATCH);

        private final UnavailableReason reason;

        AssignmentFault(UnavailableReason reason) {
            this.reason = reason;
        }
    }

    private static BenchmarkAssetClassificationEvidence classificationFault(
            ClassificationFault fault) {
        return switch (fault) {
            case BASIS -> classification(
                    new Correction("call-001", "wrong-revision", BASIS_TIME),
                    ASSET_ID, AssetType.EQUITY, PRIMARY_VENUE_ID, US, USD,
                    exactInterval(), AS_OF, AS_OF, "basis-fault");
            case ASSET -> classification(ORIGINAL, "wrong-asset", AssetType.EQUITY,
                    PRIMARY_VENUE_ID, US, USD, exactInterval(), AS_OF, AS_OF,
                    "asset-fault");
            case INTERVAL -> classification(ORIGINAL, ASSET_ID, AssetType.EQUITY,
                    PRIMARY_VENUE_ID, US, USD,
                    bounded(BASIS_TIME.plusSeconds(1), INTERVAL_END),
                    AS_OF, AS_OF, "interval-fault");
        };
    }

    private static BenchmarkAssignmentEvidence assignmentFault(
            BenchmarkAssetClassificationEvidence classification,
            AssignmentFault fault) {
        OutcomeBasis basis = fault == AssignmentFault.BASIS
                ? new Correction("call-001", "wrong-revision", BASIS_TIME)
                : classification.basis();
        String assetId = fault == AssignmentFault.ASSET
                ? "wrong-asset" : classification.assetId();
        AssetType assetType = fault == AssignmentFault.ASSET_TYPE
                ? AssetType.ETF : classification.assetType();
        String venue = fault == AssignmentFault.VENUE
                ? "wrong-venue" : classification.primaryVenueId();
        String country = fault == AssignmentFault.COUNTRY
                ? CA : classification.primaryVenueCountryCode();
        Currency currency = fault == AssignmentFault.CURRENCY
                ? EUR : classification.currency();
        EffectiveInterval interval = fault == AssignmentFault.INTERVAL
                ? bounded(BASIS_TIME.plusSeconds(1), INTERVAL_END)
                : classification.effectiveInterval();
        String benchmarkAsset = fault == AssignmentFault.BENCHMARK_ASSET
                ? "wrong-benchmark" : "asset-spx";
        AssetType benchmarkType = fault == AssignmentFault.BENCHMARK_TYPE
                ? AssetType.ETF : AssetType.INDEX;
        Currency benchmarkCurrency = fault == AssignmentFault.BENCHMARK_CURRENCY
                ? EUR : USD;
        BenchmarkReferenceKind referenceKind = fault == AssignmentFault.REFERENCE_KIND
                ? BenchmarkReferenceKind.PROVIDER_PUBLISHED_TOTAL_RETURN_INDEX
                : BenchmarkReferenceKind.PROVIDER_PUBLISHED_PRICE_INDEX;
        return assignmentWith(classification, basis, assetId, assetType, venue, country,
                currency, interval, benchmarkAsset, benchmarkType, benchmarkCurrency,
                referenceKind, AS_OF, AS_OF, fault.name());
    }

    private static BenchmarkAssetClassificationEvidence exactClassification(
            OutcomeBasis basis, String suffix) {
        return classification(basis, ASSET_ID, AssetType.EQUITY, PRIMARY_VENUE_ID,
                US, USD, exactInterval(), AS_OF, AS_OF, suffix);
    }

    private static BenchmarkAssignmentEvidence exactAssignment(
            BenchmarkAssetClassificationEvidence classification, String suffix) {
        return assignment(classification, classification.effectiveInterval(),
                AS_OF, AS_OF, suffix);
    }

    private static BenchmarkAssetClassificationEvidence classification(
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
        return new BenchmarkAssetClassificationEvidence(
                "classification-" + suffix, "classification-provider-event-" + suffix,
                basis, assetId, assetType, venue, country, currency,
                "classification-source", "classification-revision-3",
                "classification-provenance-" + suffix, interval,
                availableAt, capturedAt);
    }

    private static BenchmarkAssignmentEvidence assignment(
            BenchmarkAssetClassificationEvidence classification,
            EffectiveInterval interval,
            Instant availableAt,
            Instant capturedAt,
            String suffix) {
        return assignmentWith(classification, classification.basis(),
                classification.assetId(), classification.assetType(),
                classification.primaryVenueId(),
                classification.primaryVenueCountryCode(), classification.currency(),
                interval, "asset-spx", AssetType.INDEX, USD,
                BenchmarkReferenceKind.PROVIDER_PUBLISHED_PRICE_INDEX,
                availableAt, capturedAt, suffix);
    }

    private static BenchmarkAssignmentEvidence assignmentWith(
            BenchmarkAssetClassificationEvidence source,
            OutcomeBasis basis,
            String assetId,
            AssetType assetType,
            String venue,
            String country,
            Currency currency,
            EffectiveInterval interval,
            String benchmarkAssetId,
            AssetType benchmarkAssetType,
            Currency benchmarkCurrency,
            BenchmarkReferenceKind referenceKind,
            Instant availableAt,
            Instant capturedAt,
            String suffix) {
        return new BenchmarkAssignmentEvidence(
                "assignment-" + suffix, "assignment-provider-event-" + suffix,
                basis, assetId, assetType, venue, country, currency,
                "assignment-source", "assignment-revision-5",
                "assignment-provenance-" + suffix, interval,
                benchmarkAssetId, benchmarkAssetType, benchmarkCurrency, referenceKind,
                availableAt, capturedAt);
    }

    private static BenchmarkAssetClassificationEvidence classificationWithText(
            String field, String value) {
        return new BenchmarkAssetClassificationEvidence(
                field.equals("classificationEvidenceId") ? value : "classification-text",
                field.equals("providerEventId") ? value : "provider-event-text",
                ORIGINAL,
                field.equals("assetId") ? value : ASSET_ID,
                AssetType.EQUITY,
                field.equals("primaryVenueId") ? value : PRIMARY_VENUE_ID,
                US, USD,
                field.equals("classificationSourceId") ? value : "source-text",
                field.equals("classificationSourceRevision") ? value : "revision-text",
                field.equals("provenanceId") ? value : "provenance-text",
                exactInterval(), AS_OF, AS_OF);
    }

    private static BenchmarkAssignmentEvidence assignmentWithText(
            String field, String value) {
        return new BenchmarkAssignmentEvidence(
                field.equals("assignmentEvidenceId") ? value : "assignment-text",
                field.equals("providerEventId") ? value : "provider-event-text",
                ORIGINAL,
                field.equals("assetId") ? value : ASSET_ID,
                AssetType.EQUITY,
                field.equals("primaryVenueId") ? value : PRIMARY_VENUE_ID,
                US, USD,
                field.equals("assignmentSourceId") ? value : "source-text",
                field.equals("assignmentSourceRevision") ? value : "revision-text",
                field.equals("provenanceId") ? value : "provenance-text",
                exactInterval(),
                field.equals("benchmarkAssetId") ? value : "asset-spx",
                AssetType.INDEX, USD,
                BenchmarkReferenceKind.PROVIDER_PUBLISHED_PRICE_INDEX,
                AS_OF, AS_OF);
    }

    private static BenchmarkAssignmentResolution select(
            OutcomeBasis basis,
            List<BenchmarkAssetClassificationEvidence> classifications,
            List<BenchmarkAssignmentEvidence> assignments) {
        return BenchmarkAssignmentSelector.select(new BenchmarkAssignmentRequest(
                policy(), basis, ASSET_ID, AS_OF, classifications, assignments));
    }

    private static Resolved assertResolved(BenchmarkAssignmentResolution result) {
        assertThat(result).isInstanceOf(Resolved.class);
        return (Resolved) result;
    }

    private static NotApplicable assertNotApplicable(
            BenchmarkAssignmentResolution result,
            NotApplicableReason reason) {
        assertThat(result).isInstanceOfSatisfying(NotApplicable.class,
                value -> assertThat(value.reason()).isEqualTo(reason));
        return (NotApplicable) result;
    }

    private static void assertUnavailable(
            BenchmarkAssignmentResolution result,
            UnavailableReason reason) {
        assertThat(result).isInstanceOfSatisfying(Unavailable.class,
                value -> {
                    assertThat(value.reason()).isEqualTo(reason);
                    assertThat(value.context().policyDefinitionHash())
                            .isEqualTo(POLICY_HASH);
                });
    }

    private static ResolutionContext context(OutcomeBasis basis) {
        return new ResolutionContext(policy(), POLICY_HASH, basis, ASSET_ID, AS_OF);
    }

    private static BenchmarkAssignmentPolicyVersion policy() {
        return BenchmarkAssignmentPolicyVersion
                .POINT_IN_TIME_EXPLICIT_US_EQUITY_ASSET_SPX_ASSIGNMENT_V1;
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

    private static void assertRecordComponents(Class<?> type, String... expected) {
        assertThat(type.isRecord()).as(type.getSimpleName()).isTrue();
        assertThat(Arrays.stream(type.getRecordComponents())
                .map(component -> component.getName() + ":"
                        + component.getType().getSimpleName())
                .toList()).containsExactly(expected);
    }
}
