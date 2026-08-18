package com.wallstreetreceipts.api.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.lang.reflect.Modifier;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Arrays;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import com.wallstreetreceipts.api.application.port.out.AnalystCallProvider;
import com.wallstreetreceipts.api.application.port.out.AnalystCallRepository;
import com.wallstreetreceipts.api.application.port.out.AnalystCallRevisionRepository;
import com.wallstreetreceipts.api.application.port.out.CallOutcomeRepository;
import com.wallstreetreceipts.api.application.port.out.ScoringMethodologyRepository;
import com.wallstreetreceipts.api.domain.call.AnalystCall;
import com.wallstreetreceipts.api.domain.market.DataMode;
import com.wallstreetreceipts.api.domain.market.MarketSnapshot;
import com.wallstreetreceipts.api.domain.outcome.CallOutcome;
import com.wallstreetreceipts.api.domain.outcome.MethodologyStatus;
import com.wallstreetreceipts.api.domain.outcome.OutcomeEvaluationStatus;
import com.wallstreetreceipts.api.domain.outcome.OutcomeHorizon;
import com.wallstreetreceipts.api.domain.outcome.OutcomeReasonCode;
import com.wallstreetreceipts.api.domain.outcome.ScoringMethodology;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class CallOutcomePersistenceTest {

    @Autowired
    private AnalystCallProvider provider;

    @Autowired
    private AnalystCallRepository callRepository;

    @Autowired
    private AnalystCallRevisionRepository revisionRepository;

    @Autowired
    private ScoringMethodologyRepository methodologyRepository;

    @Autowired
    private CallOutcomeRepository outcomeRepository;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Test
    void fixtureMethodologiesAndOutcomesArePackagedMappedAndPersistedWithoutInventedMetrics() {
        assertThat(new ClassPathResource("fixtures/v1/call-outcomes.json").exists()).isTrue();
        var dataSet = provider.load();

        assertThat(dataSet.methodologies()).hasSize(2);
        assertThat(dataSet.outcomes()).hasSize(4);
        assertThat(methodologyRepository.count()).isEqualTo(2);
        assertThat(outcomeRepository.count()).isEqualTo(4);
        assertThat(dataSet.outcomes()).allSatisfy(outcome -> {
            assertThat(outcome.assetReturn()).isNull();
            assertThat(outcome.benchmarkReturn()).isNull();
            assertThat(outcome.sectorReturn()).isNull();
            assertThat(outcome.alpha()).isNull();
            assertThat(outcome.sectorAlpha()).isNull();
            assertThat(outcome.mfe()).isNull();
            assertThat(outcome.mae()).isNull();
            assertThat(outcome.targetHit()).isNull();
            assertThat(outcome.directionalWin()).isNull();
            assertThat(outcome.targetError()).isNull();
            assertThat(outcome.dataComplete()).isFalse();
        });

        assertThat(outcomeRepository.findByCallId("demo-call-001"))
                .extracting(CallOutcome::outcomeId)
                .containsExactly(
                        "outcome-demo-call-001-d1-v1-001",
                        "outcome-demo-call-001-d1-v1-002",
                        "outcome-demo-call-001-d1-v2-001",
                        "outcome-demo-call-001-m1-v1-001");
    }

    @Test
    void exactReplayIsIdempotentAndDivergentNaturalIdentityIsRejected() {
        ScoringMethodology methodology = provider.load().methodologies().getFirst();
        CallOutcome outcome = provider.load().outcomes().getFirst();
        var originalBefore = callRepository.findById(outcome.callId()).orElseThrow();
        var methodologyBefore = methodologyRepository.findByIdAndVersion(
                outcome.methodologyId(), outcome.methodologyVersion()).orElseThrow();
        CallOutcome priorBefore = outcomeRepository.findByCallId(outcome.callId()).stream()
                .filter(candidate -> candidate.outcomeId().equals(outcome.outcomeId()))
                .findFirst()
                .orElseThrow();

        assertThat(methodologyRepository.saveIfAbsent(methodology)).isFalse();
        assertThat(outcomeRepository.saveIfAbsent(outcome)).isFalse();
        assertReferencesUnchanged(originalBefore, methodologyBefore, priorBefore);

        ScoringMethodology changedDefinition = new ScoringMethodology(
                methodology.methodologyId(), methodology.methodologyVersion(), methodology.schemaVersion(),
                methodology.definitionHash(), MethodologyStatus.ACTIVE, methodology.effectiveAt(),
                methodology.dataMode(), methodology.capturedAt(), methodology.provenanceId());
        assertThatThrownBy(() -> methodologyRepository.saveIfAbsent(changedDefinition))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("conflicting scoring methodology identity");
        ScoringMethodology changedHash = new ScoringMethodology(
                methodology.methodologyId(), methodology.methodologyVersion(), methodology.schemaVersion(),
                "f".repeat(64), methodology.status(), methodology.effectiveAt(),
                methodology.dataMode(), methodology.capturedAt(), methodology.provenanceId());
        assertThatThrownBy(() -> methodologyRepository.saveIfAbsent(changedHash))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("conflicting scoring methodology identity");

        CallOutcome divergent = copy(
                outcome, outcome.outcomeId(), outcome.inputFingerprint(), outcome.sequenceNumber(),
                outcome.supersedesOutcomeId(), outcome.basisRevisionId(), null,
                outcome.eventTime(), outcome.processingTime(), outcome.capturedAt());
        assertThatThrownBy(() -> outcomeRepository.saveIfAbsent(divergent))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("conflicting call outcome natural identity");
        assertThat(outcomeRepository.count()).isEqualTo(4);
    }

    @Test
    void aNewInputFingerprintAppendsTheNextOutcomeInTheSameLineage() {
        CallOutcome latest = provider.load().outcomes().stream()
                .filter(outcome -> outcome.outcomeId().equals("outcome-demo-call-001-d1-v1-002"))
                .findFirst()
                .orElseThrow();
        var originalBefore = callRepository.findById(latest.callId()).orElseThrow();
        var methodologyBefore = methodologyRepository.findByIdAndVersion(
                latest.methodologyId(), latest.methodologyVersion()).orElseThrow();
        CallOutcome appended = copy(
                latest, "test-outcome-d1-v1-003", "c".repeat(64), 3, latest.outcomeId(),
                latest.basisRevisionId(), latest.snapshotId(), latest.eventTime(),
                latest.processingTime().plusSeconds(60), latest.capturedAt().plusSeconds(60));

        assertThat(outcomeRepository.saveIfAbsent(appended)).isTrue();
        assertThat(outcomeRepository.findByCallId("demo-call-001"))
                .extracting(CallOutcome::outcomeId)
                .contains("test-outcome-d1-v1-003");
        assertThat(outcomeRepository.count()).isEqualTo(5);
        assertReferencesUnchanged(originalBefore, methodologyBefore, latest);
    }

    @Test
    void outcomeBatchImportRollsBackAnEarlierAppendWhenALaterRecordIsInvalid() {
        CallOutcome latest = provider.load().outcomes().stream()
                .filter(outcome -> outcome.outcomeId().equals("outcome-demo-call-001-d1-v1-002"))
                .findFirst()
                .orElseThrow();
        CallOutcome appended = copy(
                latest, "test-rollback-outcome-003", "8".repeat(64), 3, latest.outcomeId(), null,
                latest.snapshotId(), latest.eventTime(), latest.processingTime().plusSeconds(60),
                latest.capturedAt().plusSeconds(60));
        CallOutcome invalidGap = copy(
                latest, "test-rollback-outcome-005", "9".repeat(64), 5, appended.outcomeId(), null,
                latest.snapshotId(), latest.eventTime(), latest.processingTime().plusSeconds(120),
                latest.capturedAt().plusSeconds(120));
        TransactionTemplate transaction = new TransactionTemplate(transactionManager);
        transaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);

        assertThatThrownBy(() -> transaction.executeWithoutResult(
                status -> outcomeRepository.importAll(java.util.List.of(appended, invalidGap))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("latest result");

        assertThat(jdbc.queryForObject("""
                SELECT COUNT(*) FROM call_outcomes
                WHERE outcome_id IN ('test-rollback-outcome-003', 'test-rollback-outcome-005')
                """, Integer.class)).isZero();
    }

    @Test
    void correctionBasisMustBelongToTheSameCallAndCancellationCannotBeABasis() {
        CallOutcome correctionBased = outcomeForCallTwo(
                "test-correction-outcome", "demo-call-revision-001", "d".repeat(64));
        var correctionBefore = revisionRepository.findByCallId("demo-call-002").stream()
                .filter(revision -> revision.id().equals("demo-call-revision-001"))
                .findFirst()
                .orElseThrow();
        assertThat(outcomeRepository.saveIfAbsent(correctionBased)).isTrue();
        assertRevisionUnchanged(correctionBefore);

        CallOutcome cancellationBased = outcomeForCallTwo(
                "test-cancellation-outcome", "demo-call-revision-002", "e".repeat(64));
        assertThatThrownBy(() -> outcomeRepository.saveIfAbsent(cancellationBased))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("cancellation");

        CallOutcome crossCall = copy(
                provider.load().outcomes().getFirst(), "test-cross-call-outcome", "f".repeat(64), 1, null,
                "demo-call-revision-001", "market-snapshot-demo-001",
                Instant.parse("2026-08-12T00:00:00Z"), Instant.parse("2026-08-12T00:01:00Z"),
                Instant.parse("2026-08-12T00:01:00Z"));
        assertThatThrownBy(() -> outcomeRepository.saveIfAbsent(crossCall))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("same call");

        jdbc.update(
                "UPDATE analyst_call_revisions SET captured_at = ? WHERE revision_id = ?",
                java.time.OffsetDateTime.parse("2026-08-20T00:00:00Z"),
                "demo-call-revision-001");
        CallOutcome correctionNotCaptured = outcomeForCallTwo(
                "test-correction-not-captured", "demo-call-revision-001", "a".repeat(64));
        assertThatThrownBy(() -> outcomeRepository.saveIfAbsent(correctionNotCaptured))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("basis revision must be captured");

        jdbc.update(
                "UPDATE analyst_call_revisions SET processing_time = ?, captured_at = ? WHERE revision_id = ?",
                java.time.OffsetDateTime.parse("2026-08-20T00:00:00Z"),
                java.time.OffsetDateTime.parse("2026-08-20T00:00:00Z"),
                "demo-call-revision-001");
        CallOutcome correctionNotProcessed = outcomeForCallTwo(
                "test-correction-not-processed", "demo-call-revision-001", "b".repeat(64));
        assertThatThrownBy(() -> outcomeRepository.saveIfAbsent(correctionNotProcessed))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("basis revision must be processed");
    }

    @Test
    void excludedOutcomeRequiresTraceableSameCallCancellationEvidenceCapturedInTime() {
        CallOutcome excluded = excludedForCallTwo(
                "test-excluded-outcome", "demo-call-revision-002", "0".repeat(64), OutcomeHorizon.W1);
        var cancellationBefore = revisionRepository.findByCallId("demo-call-002").stream()
                .filter(revision -> revision.id().equals("demo-call-revision-002"))
                .findFirst()
                .orElseThrow();
        assertThat(outcomeRepository.saveIfAbsent(excluded)).isTrue();
        assertThat(outcomeRepository.findByCallId("demo-call-002").stream()
                .filter(outcome -> outcome.outcomeId().equals(excluded.outcomeId()))
                .findFirst())
                .contains(excluded);
        assertRevisionUnchanged(cancellationBefore);

        CallOutcome correctionEvidence = excludedForCallTwo(
                "test-correction-evidence", "demo-call-revision-001", "1".repeat(64), OutcomeHorizon.M1);
        assertThatThrownBy(() -> outcomeRepository.saveIfAbsent(correctionEvidence))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("must be a cancellation");

        CallOutcome crossCallEvidence = copyCancellation(
                excludedForCallTwo(
                        "test-cross-call-cancellation", "demo-call-revision-002", "2".repeat(64),
                        OutcomeHorizon.M3),
                "demo-call-001", "market-snapshot-demo-001");
        assertThatThrownBy(() -> outcomeRepository.saveIfAbsent(crossCallEvidence))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("same call");

        jdbc.update(
                "UPDATE analyst_call_revisions SET captured_at = ? WHERE revision_id = ?",
                java.time.OffsetDateTime.parse("2026-08-20T00:00:00Z"),
                "demo-call-revision-002");
        CallOutcome futureCapturedEvidence = excludedForCallTwo(
                "test-future-cancellation", "demo-call-revision-002", "3".repeat(64), OutcomeHorizon.M6);
        assertThatThrownBy(() -> outcomeRepository.saveIfAbsent(futureCapturedEvidence))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("captured by processingTime");

        jdbc.update(
                "UPDATE analyst_call_revisions SET processing_time = ?, captured_at = ? WHERE revision_id = ?",
                java.time.OffsetDateTime.parse("2026-08-20T00:00:00Z"),
                java.time.OffsetDateTime.parse("2026-08-20T00:00:00Z"),
                "demo-call-revision-002");
        CallOutcome futureProcessedEvidence = excludedForCallTwo(
                "test-future-cancellation-processing", "demo-call-revision-002", "4".repeat(64),
                OutcomeHorizon.Y1);
        assertThatThrownBy(() -> outcomeRepository.saveIfAbsent(futureProcessedEvidence))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("processed by processingTime");
    }

    @Test
    void snapshotAndMethodologyMustExistAtOutcomeProcessingTime() {
        CallOutcome template = provider.load().outcomes().stream()
                .filter(outcome -> outcome.methodologyVersion().equals("2.0.0"))
                .findFirst()
                .orElseThrow();
        CallOutcome wrongSnapshot = copy(
                template, "test-wrong-snapshot", "1".repeat(64), 1, null, null,
                "market-snapshot-demo-002", template.eventTime(), template.processingTime(), template.capturedAt());
        assertThatThrownBy(() -> outcomeRepository.saveIfAbsent(wrongSnapshot))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("snapshot must belong");

        CallOutcome unknownMethodology = copyMethodology(
                template, "test-unknown-methodology", "unknown-methodology", "1.0.0",
                "7".repeat(64), "7".repeat(64), DataMode.DEMO);
        assertThatThrownBy(() -> outcomeRepository.saveIfAbsent(unknownMethodology))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("unknown scoring methodology");

        CallOutcome wrongDefinitionHash = copyMethodology(
                template, "test-wrong-methodology-hash", template.methodologyId(),
                template.methodologyVersion(), "8".repeat(64), "8".repeat(64), DataMode.DEMO);
        assertThatThrownBy(() -> outcomeRepository.saveIfAbsent(wrongDefinitionHash))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("definition hash");

        CallOutcome wrongDataMode = copyMethodology(
                template, "test-wrong-methodology-mode", template.methodologyId(),
                template.methodologyVersion(), template.methodologyDefinitionHash(),
                "9".repeat(64), DataMode.REALTIME);
        assertThatThrownBy(() -> outcomeRepository.saveIfAbsent(wrongDataMode))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("dataMode");

        CallOutcome earlyTemplate = provider.load().outcomes().getFirst();
        jdbc.update(
                "UPDATE market_snapshots SET captured_at = ? WHERE snapshot_id = ?",
                java.time.OffsetDateTime.parse("2026-08-10T12:04:00Z"),
                "market-snapshot-demo-001");
        CallOutcome snapshotNotCaptured = copy(
                earlyTemplate, "test-early-snapshot-outcome", "2".repeat(64), 1, null, null,
                "market-snapshot-demo-001", Instant.parse("2026-08-10T12:01:00Z"),
                Instant.parse("2026-08-10T12:03:30Z"), Instant.parse("2026-08-10T12:03:30Z"));
        assertThatThrownBy(() -> outcomeRepository.saveIfAbsent(snapshotNotCaptured))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("snapshot must be captured");

        ScoringMethodology future = new ScoringMethodology(
                "future-methodology", "1.0.0", "1.0.0", "3".repeat(64),
                MethodologyStatus.MODEL_ONLY, Instant.parse("2026-08-20T00:00:00Z"), DataMode.DEMO,
                Instant.parse("2026-08-20T00:00:00Z"), "test-provenance");
        assertThat(methodologyRepository.saveIfAbsent(future)).isTrue();
        CallOutcome premature = new CallOutcome(
                "test-premature-methodology", "1.0.0", "demo-call-001", OutcomeHorizon.W1,
                null, null, "market-snapshot-demo-001", future.methodologyId(), future.methodologyVersion(),
                future.definitionHash(), "4".repeat(64), 1, null, OutcomeEvaluationStatus.PENDING,
                OutcomeReasonCode.HORIZON_NOT_REACHED, Instant.parse("2026-08-18T00:00:00Z"),
                Instant.parse("2026-08-18T00:01:00Z"), null, null, null, null, null, null, null,
                null, null, null, false, DataMode.DEMO, Instant.parse("2026-08-18T00:01:00Z"),
                "test-provenance");
        assertThatThrownBy(() -> outcomeRepository.saveIfAbsent(premature))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("not effective");

        ScoringMethodology notCaptured = new ScoringMethodology(
                "not-captured-methodology", "1.0.0", "1.0.0", "a".repeat(64),
                MethodologyStatus.MODEL_ONLY, Instant.parse("2026-08-10T00:00:00Z"), DataMode.DEMO,
                Instant.parse("2026-08-20T00:00:00Z"), "test-provenance");
        assertThat(methodologyRepository.saveIfAbsent(notCaptured)).isTrue();
        CallOutcome methodologyNotCaptured = copyMethodology(
                template, "test-methodology-not-captured", notCaptured.methodologyId(),
                notCaptured.methodologyVersion(), notCaptured.definitionHash(), "b".repeat(64), DataMode.DEMO);
        assertThatThrownBy(() -> outcomeRepository.saveIfAbsent(methodologyNotCaptured))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("methodology must be captured");
    }

    @Test
    void originalCallAndSnapshotMustBothBeProcessedAndCapturedByOutcomeProcessingTime() {
        CallOutcome template = provider.load().outcomes().stream()
                .filter(outcome -> outcome.methodologyVersion().equals("2.0.0"))
                .findFirst()
                .orElseThrow();
        jdbc.update("""
                UPDATE market_snapshots
                SET processing_time = ?, captured_at = ?
                WHERE snapshot_id = ?
                """,
                java.time.OffsetDateTime.parse("2026-08-19T00:00:00Z"),
                java.time.OffsetDateTime.parse("2026-08-19T00:00:00Z"),
                "market-snapshot-demo-001");
        CallOutcome snapshotNotProcessed = copy(
                template, "test-snapshot-not-processed", "4".repeat(64), 1, null, null,
                template.snapshotId(), template.eventTime(), template.processingTime(), template.capturedAt());
        assertThatThrownBy(() -> outcomeRepository.saveIfAbsent(snapshotNotProcessed))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("snapshot must be processed");

        jdbc.update("""
                UPDATE market_snapshots
                SET processing_time = event_time, captured_at = event_time
                WHERE snapshot_id = ?
                """, "market-snapshot-demo-001");
        jdbc.update("""
                UPDATE analyst_calls
                SET captured_at = ?
                WHERE call_id = ?
                """,
                java.time.OffsetDateTime.parse("2026-08-19T00:00:00Z"),
                "demo-call-001");
        CallOutcome callNotCaptured = copy(
                template, "test-call-not-captured", "5".repeat(64), 1, null, null,
                template.snapshotId(), template.eventTime(), template.processingTime(), template.capturedAt());
        assertThatThrownBy(() -> outcomeRepository.saveIfAbsent(callNotCaptured))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("original call must be captured");

        jdbc.update("""
                UPDATE analyst_calls
                SET processing_time = ?, captured_at = ?
                WHERE call_id = ?
                """,
                java.time.OffsetDateTime.parse("2026-08-19T00:00:00Z"),
                java.time.OffsetDateTime.parse("2026-08-19T00:00:00Z"),
                "demo-call-001");
        CallOutcome callNotProcessed = copy(
                template, "test-call-not-processed", "6".repeat(64), 1, null, null,
                template.snapshotId(), template.eventTime(), template.processingTime(), template.capturedAt());
        assertThatThrownBy(() -> outcomeRepository.saveIfAbsent(callNotProcessed))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("original call must be processed");
    }

    @Test
    void exactNumericBoundaryRoundTripsAndReplaysWithoutDatabaseRounding() {
        ScoringMethodology methodology = provider.load().methodologies().getFirst();
        CallOutcome boundary = new CallOutcome(
                "test-numeric-boundary", "1.0.0", "demo-call-001", OutcomeHorizon.W1,
                null, null, "market-snapshot-demo-001", methodology.methodologyId(),
                methodology.methodologyVersion(), methodology.definitionHash(), "6".repeat(64), 1, null,
                OutcomeEvaluationStatus.CALCULATED, null, Instant.parse("2026-08-18T00:00:00Z"),
                Instant.parse("2026-08-18T00:01:00Z"),
                new BigDecimal("99999999999999999999999999.999999999999"),
                new BigDecimal("0.1234567890120"), BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                BigDecimal.ZERO, BigDecimal.ZERO, true, true, new BigDecimal("0.0100000000000"), true,
                DataMode.DEMO, Instant.parse("2026-08-18T00:01:00Z"), "test-provenance");

        assertThat(outcomeRepository.saveIfAbsent(boundary)).isTrue();
        CallOutcome stored = outcomeRepository.findByCallId(boundary.callId()).stream()
                .filter(outcome -> outcome.outcomeId().equals(boundary.outcomeId()))
                .findFirst()
                .orElseThrow();
        assertThat(stored).isEqualTo(boundary);
        assertThat(stored.assetReturn()).isEqualByComparingTo(
                "99999999999999999999999999.999999999999");
        assertThat(outcomeRepository.saveIfAbsent(boundary)).isFalse();
    }

    @Test
    void opaqueCallAndSnapshotIdentifiersSupportTheCanonical128CharacterBoundary() {
        AnalystCall callTemplate = provider.load().calls().getFirst();
        MarketSnapshot snapshotTemplate = provider.load().snapshots().stream()
                .filter(snapshot -> snapshot.callId().equals(callTemplate.id()))
                .findFirst()
                .orElseThrow();
        String callId = "c".repeat(128);
        String snapshotId = "s".repeat(128);
        AnalystCall boundaryCall = new AnalystCall(
                callId, callTemplate.provider(), "boundary-call-event", callTemplate.institution(),
                callTemplate.analyst(), callTemplate.asset(), callTemplate.eventTime(), callTemplate.processingTime(),
                callTemplate.direction(), callTemplate.originalRating(), callTemplate.previousTarget(),
                callTemplate.target(), callTemplate.currency(), callTemplate.targetDate(),
                callTemplate.sourceReference(), callTemplate.status(), callTemplate.dataMode(),
                callTemplate.capturedAt(), callTemplate.provenanceId());
        MarketSnapshot boundarySnapshot = new MarketSnapshot(
                snapshotId, callId, snapshotTemplate.assetId(), snapshotTemplate.eventTime(),
                snapshotTemplate.processingTime(), snapshotTemplate.assetPrice(), snapshotTemplate.spx(),
                snapshotTemplate.ndx(), snapshotTemplate.vix(), snapshotTemplate.treasury2y(),
                snapshotTemplate.treasury10y(), snapshotTemplate.realYield(), snapshotTemplate.dxy(),
                snapshotTemplate.wti(), snapshotTemplate.gold(), snapshotTemplate.volatility(),
                snapshotTemplate.distanceFrom52WeekHigh(), snapshotTemplate.distanceFromAth(),
                snapshotTemplate.dataMode(), snapshotTemplate.capturedAt(), snapshotTemplate.provenanceId());
        assertThat(callRepository.saveIfAbsent(boundaryCall, boundarySnapshot)).isTrue();

        ScoringMethodology methodology = provider.load().methodologies().getFirst();
        CallOutcome outcome = new CallOutcome(
                "test-128-id-outcome", "1.0.0", callId, OutcomeHorizon.Y1, null, null, snapshotId,
                methodology.methodologyId(), methodology.methodologyVersion(), methodology.definitionHash(),
                "7".repeat(64), 1, null, OutcomeEvaluationStatus.PENDING,
                OutcomeReasonCode.HORIZON_NOT_REACHED, Instant.parse("2026-08-18T00:00:00Z"),
                Instant.parse("2026-08-18T00:01:00Z"), null, null, null, null, null, null, null,
                null, null, null, false, DataMode.DEMO, Instant.parse("2026-08-18T00:01:00Z"),
                "test-provenance");
        assertThat(outcomeRepository.saveIfAbsent(outcome)).isTrue();
        assertThat(outcomeRepository.findByCallId(callId)).containsExactly(outcome);
    }

    @Test
    void lineageEventAndProcessingTimesCannotMoveBackwards() {
        CallOutcome latest = provider.load().outcomes().stream()
                .filter(outcome -> outcome.outcomeId().equals("outcome-demo-call-001-d1-v1-002"))
                .findFirst()
                .orElseThrow();
        CallOutcome eventBackwards = copy(
                latest, "test-event-backwards-outcome", "5".repeat(64), 3, latest.outcomeId(), null,
                latest.snapshotId(), latest.eventTime().minusSeconds(1),
                latest.processingTime().plusSeconds(1), latest.capturedAt().plusSeconds(1));
        CallOutcome processingBackwards = copy(
                latest, "test-processing-backwards-outcome", "6".repeat(64), 3, latest.outcomeId(), null,
                latest.snapshotId(), latest.eventTime(), latest.processingTime().minusSeconds(1),
                latest.capturedAt().plusSeconds(1));

        assertThatThrownBy(() -> outcomeRepository.saveIfAbsent(eventBackwards))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("timestamps must not move backwards");
        assertThatThrownBy(() -> outcomeRepository.saveIfAbsent(processingBackwards))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("timestamps must not move backwards");
    }

    @Test
    void lineageCapturedTimeCannotMoveBackwardsIndependentlyOfProcessingTime() {
        CallOutcome latest = provider.load().outcomes().stream()
                .filter(outcome -> outcome.outcomeId().equals("outcome-demo-call-001-d1-v1-002"))
                .findFirst()
                .orElseThrow();
        CallOutcome longCapture = copy(
                latest, "test-long-capture-outcome", "a".repeat(64), 3, latest.outcomeId(), null,
                latest.snapshotId(), latest.eventTime(), latest.processingTime().plusSeconds(60),
                latest.capturedAt().plusSeconds(600));
        assertThat(outcomeRepository.saveIfAbsent(longCapture)).isTrue();

        CallOutcome capturedBackwards = copy(
                longCapture, "test-capture-backwards-outcome", "b".repeat(64), 4, longCapture.outcomeId(), null,
                longCapture.snapshotId(), longCapture.eventTime(), latest.processingTime().plusSeconds(120),
                latest.capturedAt().plusSeconds(180));
        assertThat(capturedBackwards.capturedAt()).isAfter(capturedBackwards.processingTime());

        assertThatThrownBy(() -> outcomeRepository.saveIfAbsent(capturedBackwards))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("timestamps must not move backwards");
    }

    @Test
    void outcomeDomainAndRepositoriesExposeNoMutationSurface() {
        assertThat(CallOutcome.class.isRecord()).isTrue();
        assertThat(Arrays.stream(CallOutcome.class.getDeclaredFields())
                .filter(field -> !field.isSynthetic())
                .allMatch(field -> Modifier.isPrivate(field.getModifiers()) && Modifier.isFinal(field.getModifiers())))
                .isTrue();
        assertThat(Arrays.stream(CallOutcomeRepository.class.getMethods())
                .map(java.lang.reflect.Method::getName)
                .noneMatch(name -> name.startsWith("update") || name.startsWith("delete")))
                .isTrue();
        assertThat(Arrays.stream(ScoringMethodologyRepository.class.getMethods())
                .map(java.lang.reflect.Method::getName)
                .noneMatch(name -> name.startsWith("update") || name.startsWith("delete")))
                .isTrue();
    }

    private CallOutcome outcomeForCallTwo(String outcomeId, String basisRevisionId, String fingerprint) {
        ScoringMethodology methodology = provider.load().methodologies().getFirst();
        return new CallOutcome(
                outcomeId, "1.0.0", "demo-call-002", OutcomeHorizon.D1, basisRevisionId,
                null, "market-snapshot-demo-002", methodology.methodologyId(), methodology.methodologyVersion(),
                methodology.definitionHash(), fingerprint, 1, null, OutcomeEvaluationStatus.INCOMPLETE,
                OutcomeReasonCode.HORIZON_DATA_MISSING, Instant.parse("2026-08-12T20:00:00Z"),
                Instant.parse("2026-08-12T20:01:00Z"), null, null, null, null, null, null, null,
                null, null, null, false, DataMode.DEMO, Instant.parse("2026-08-12T20:01:00Z"),
                "test-provenance");
    }

    private CallOutcome excludedForCallTwo(
            String outcomeId,
            String cancellationRevisionId,
            String fingerprint,
            OutcomeHorizon horizon) {
        ScoringMethodology methodology = provider.load().methodologies().getFirst();
        return new CallOutcome(
                outcomeId, "1.0.0", "demo-call-002", horizon, null, cancellationRevisionId,
                "market-snapshot-demo-002", methodology.methodologyId(), methodology.methodologyVersion(),
                methodology.definitionHash(), fingerprint, 1, null, OutcomeEvaluationStatus.EXCLUDED,
                OutcomeReasonCode.CALL_CANCELLED, Instant.parse("2026-08-12T20:00:00Z"),
                Instant.parse("2026-08-12T20:01:00Z"), null, null, null, null, null, null, null,
                null, null, null, false, DataMode.DEMO, Instant.parse("2026-08-12T20:01:00Z"),
                "test-provenance");
    }

    private static CallOutcome copyCancellation(
            CallOutcome source,
            String callId,
            String snapshotId) {
        return new CallOutcome(
                source.outcomeId(), source.schemaVersion(), callId, source.horizon(), source.basisRevisionId(),
                source.cancellationRevisionId(), snapshotId, source.methodologyId(), source.methodologyVersion(),
                source.methodologyDefinitionHash(), source.inputFingerprint(), source.sequenceNumber(),
                source.supersedesOutcomeId(), source.evaluationStatus(), source.reasonCode(), source.eventTime(),
                source.processingTime(), source.assetReturn(), source.benchmarkReturn(), source.sectorReturn(),
                source.alpha(), source.sectorAlpha(), source.mfe(), source.mae(), source.targetHit(),
                source.directionalWin(), source.targetError(), source.dataComplete(), source.dataMode(),
                source.capturedAt(), source.provenanceId());
    }

    private static CallOutcome copyMethodology(
            CallOutcome source,
            String outcomeId,
            String methodologyId,
            String methodologyVersion,
            String definitionHash,
            String inputFingerprint,
            DataMode dataMode) {
        return new CallOutcome(
                outcomeId, source.schemaVersion(), source.callId(), source.horizon(), source.basisRevisionId(),
                source.cancellationRevisionId(), source.snapshotId(), methodologyId, methodologyVersion,
                definitionHash, inputFingerprint, source.sequenceNumber(), source.supersedesOutcomeId(),
                source.evaluationStatus(), source.reasonCode(), source.eventTime(), source.processingTime(),
                source.assetReturn(), source.benchmarkReturn(), source.sectorReturn(), source.alpha(),
                source.sectorAlpha(), source.mfe(), source.mae(), source.targetHit(), source.directionalWin(),
                source.targetError(), source.dataComplete(), dataMode, source.capturedAt(), source.provenanceId());
    }

    private void assertReferencesUnchanged(
            com.wallstreetreceipts.api.application.call.AnalystCallDetail originalBefore,
            ScoringMethodology methodologyBefore,
            CallOutcome priorBefore) {
        var originalAfter = callRepository.findById(originalBefore.call().id()).orElseThrow();
        assertThat(originalAfter.call()).isEqualTo(originalBefore.call());
        assertThat(originalAfter.snapshot()).isEqualTo(originalBefore.snapshot());
        assertThat(methodologyRepository.findByIdAndVersion(
                methodologyBefore.methodologyId(), methodologyBefore.methodologyVersion()))
                .contains(methodologyBefore);
        assertThat(outcomeRepository.findByCallId(priorBefore.callId()).stream()
                .filter(candidate -> candidate.outcomeId().equals(priorBefore.outcomeId()))
                .findFirst())
                .contains(priorBefore);
    }

    private void assertRevisionUnchanged(
            com.wallstreetreceipts.api.domain.call.AnalystCallRevision revisionBefore) {
        assertThat(revisionRepository.findByCallId(revisionBefore.callId()).stream()
                .filter(revision -> revision.id().equals(revisionBefore.id()))
                .findFirst())
                .contains(revisionBefore);
    }

    private static CallOutcome copy(
            CallOutcome source,
            String outcomeId,
            String inputFingerprint,
            int sequenceNumber,
            String supersedesOutcomeId,
            String basisRevisionId,
            String snapshotId,
            Instant eventTime,
            Instant processingTime,
            Instant capturedAt) {
        return new CallOutcome(
                outcomeId, source.schemaVersion(), source.callId(), source.horizon(), basisRevisionId,
                source.cancellationRevisionId(), snapshotId,
                source.methodologyId(), source.methodologyVersion(), source.methodologyDefinitionHash(),
                inputFingerprint, sequenceNumber, supersedesOutcomeId, source.evaluationStatus(), source.reasonCode(),
                eventTime, processingTime, source.assetReturn(), source.benchmarkReturn(), source.sectorReturn(),
                source.alpha(), source.sectorAlpha(), source.mfe(), source.mae(), source.targetHit(),
                source.directionalWin(), source.targetError(), source.dataComplete(), source.dataMode(), capturedAt,
                source.provenanceId());
    }
}
