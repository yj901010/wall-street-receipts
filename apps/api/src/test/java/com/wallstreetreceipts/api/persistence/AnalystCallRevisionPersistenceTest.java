package com.wallstreetreceipts.api.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.lang.reflect.Modifier;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.ClassPathResource;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import com.wallstreetreceipts.api.application.port.out.AnalystCallDataSet;
import com.wallstreetreceipts.api.application.port.out.AnalystCallProvider;
import com.wallstreetreceipts.api.application.port.out.AnalystCallRepository;
import com.wallstreetreceipts.api.application.port.out.AnalystCallRevisionRepository;
import com.wallstreetreceipts.api.domain.call.AnalystCall;
import com.wallstreetreceipts.api.domain.call.AnalystCallRevision;
import com.wallstreetreceipts.api.domain.call.AnalystCallRevisionType;
import com.wallstreetreceipts.api.domain.call.CorrectedCallTerms;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class AnalystCallRevisionPersistenceTest {

    @Autowired
    private AnalystCallProvider provider;

    @Autowired
    private AnalystCallRepository callRepository;

    @Autowired
    private AnalystCallRevisionRepository revisionRepository;

    @Test
    void fixtureChainIsCanonicalTraceableAndLeavesBaseEventsUnchanged() {
        assertThat(new ClassPathResource("fixtures/v1/analyst-call-revisions.json").exists()).isTrue();

        AnalystCallDataSet dataSet = provider.load();
        AnalystCall canonicalBase = dataSet.calls().stream()
                .filter(call -> call.id().equals("demo-call-002"))
                .findFirst()
                .orElseThrow();

        assertThat(dataSet.calls()).hasSize(2);
        assertThat(dataSet.revisions()).hasSize(2);
        assertThat(callRepository.count()).isEqualTo(2);
        assertThat(revisionRepository.count()).isEqualTo(2);
        assertThat(callRepository.findById("demo-call-002").orElseThrow().call())
                .usingRecursiveComparison()
                .withComparatorForType(BigDecimal::compareTo, BigDecimal.class)
                .isEqualTo(canonicalBase);

        var lineage = revisionRepository.findByCallId("demo-call-002");
        assertThat(lineage).extracting(AnalystCallRevision::sequenceNumber).containsExactly(1, 2);
        assertThat(lineage).extracting(AnalystCallRevision::type)
                .containsExactly(AnalystCallRevisionType.CORRECTION, AnalystCallRevisionType.CANCELLATION);
        assertThat(lineage.getFirst().correctedTerms().target()).isEqualByComparingTo("232.0");
        assertThat(lineage.getFirst().sourceReference().id()).isEqualTo("source-ref-demo-002");
        assertThat(lineage.getFirst().sourceReference().document().id()).isEqualTo("source-demo-video-002");
        assertThat(lineage.getLast().supersedesRevisionId()).isEqualTo(lineage.getFirst().id());
        assertThat(lineage.getLast().correctedTerms()).isNull();

        AnalystCall persistedBase = callRepository.findById("demo-call-002").orElseThrow().call();
        assertThat(persistedBase)
                .usingRecursiveComparison()
                .withComparatorForType(BigDecimal::compareTo, BigDecimal.class)
                .isEqualTo(canonicalBase);
        assertThat(persistedBase.target()).isEqualByComparingTo("235.0");
        assertThat(persistedBase.status().name()).isEqualTo("ACTIVE");
    }

    @Test
    void duplicateRevisionProviderEventIsIdempotentEvenWithAnotherRevisionId() {
        AnalystCallRevision existing = provider.load().revisions().getFirst();
        AnalystCallRevision duplicate = new AnalystCallRevision(
                "another-revision-id", existing.schemaVersion(), existing.callId(),
                existing.supersedesRevisionId(), existing.sequenceNumber(), existing.provider(),
                existing.providerEventId(), existing.type(), existing.eventTime(), existing.processingTime(),
                existing.correctedTerms(), existing.reason(), existing.sourceReference(), existing.dataMode(),
                existing.capturedAt(), existing.provenanceId());

        assertThat(revisionRepository.saveIfAbsent(duplicate)).isFalse();
        assertThat(revisionRepository.count()).isEqualTo(2);
    }

    @Test
    void correctionAndCancellationAppendWithoutOverwritingOriginalAndCancellationIsTerminal() {
        var originalDetail = callRepository.findById("demo-call-001").orElseThrow();
        AnalystCall original = originalDetail.call();
        var originalSnapshot = originalDetail.snapshot();
        assertThat(originalSnapshot).isNotNull();
        long baseCount = callRepository.count();
        Instant correctionTime = original.eventTime().plus(1, ChronoUnit.HOURS);
        AnalystCallRevision correction = correction(
                original, "test-revision-001", "test-revision-event-001", null, 1, correctionTime);
        AnalystCallRevision cancellation = cancellation(
                original, "test-revision-002", "test-revision-event-002", correction.id(), 2,
                correctionTime.plus(1, ChronoUnit.HOURS));

        assertThat(revisionRepository.saveIfAbsent(correction)).isTrue();
        assertThat(revisionRepository.saveIfAbsent(cancellation)).isTrue();
        var persistedDetail = callRepository.findById(original.id()).orElseThrow();
        assertThat(persistedDetail.call()).isEqualTo(original);
        assertThat(persistedDetail.snapshot())
                .usingRecursiveComparison()
                .withComparatorForType(BigDecimal::compareTo, BigDecimal.class)
                .isEqualTo(originalSnapshot);
        assertThat(callRepository.count()).isEqualTo(baseCount);

        AnalystCallRevision forbidden = correction(
                original, "test-revision-003", "test-revision-event-003", cancellation.id(), 3,
                cancellation.eventTime().plus(1, ChronoUnit.HOURS));
        assertThatThrownBy(() -> revisionRepository.saveIfAbsent(forbidden))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("cancelled");
    }

    @Test
    void lineageMustSupersedeTheLatestRevisionWithoutGapsOrBranches() {
        AnalystCall original = callRepository.findById("demo-call-001").orElseThrow().call();
        Instant eventTime = original.eventTime().plus(1, ChronoUnit.HOURS);
        AnalystCallRevision first = correction(
                original, "lineage-revision-001", "lineage-event-001", null, 1, eventTime);
        assertThat(revisionRepository.saveIfAbsent(first)).isTrue();

        AnalystCallRevision branch = correction(
                original, "lineage-revision-002", "lineage-event-002", "not-the-latest", 2,
                eventTime.plus(1, ChronoUnit.HOURS));
        assertThatThrownBy(() -> revisionRepository.saveIfAbsent(branch))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("latest lineage");
    }

    @Test
    void revisionProviderIdentityCannotCollideWithAnOriginalCallEvent() {
        AnalystCall original = callRepository.findById("demo-call-001").orElseThrow().call();
        AnalystCallRevision collision = correction(
                original, "identity-collision-revision", original.providerEventId(), null, 1,
                original.eventTime().plus(1, ChronoUnit.HOURS));

        assertThatThrownBy(() -> revisionRepository.saveIfAbsent(collision))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("already claimed by ANALYST_CALL");
    }

    @Test
    void originalCallProviderIdentityCannotCollideWithAnExistingRevisionEvent() {
        AnalystCall original = callRepository.findById("demo-call-001").orElseThrow().call();
        AnalystCallRevision revision = correction(
                original, "reverse-collision-revision", "shared-revision-event", null, 1,
                original.eventTime().plus(1, ChronoUnit.HOURS));
        assertThat(revisionRepository.saveIfAbsent(revision)).isTrue();

        AnalystCall collidingCall = new AnalystCall(
                "reverse-collision-call", original.provider(), revision.providerEventId(), original.institution(),
                original.analyst(), original.asset(), original.eventTime(), original.processingTime(),
                original.direction(), original.originalRating(), original.previousTarget(), original.target(),
                original.currency(), original.targetDate(), original.sourceReference(), original.status(),
                original.dataMode(), original.capturedAt(), original.provenanceId());

        assertThatThrownBy(() -> callRepository.saveIfAbsent(collidingCall, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("already claimed by ANALYST_CALL_REVISION");
    }

    @Test
    void revisionTimesAreMonotonicAndCannotPredateTheOriginalCall() {
        AnalystCall original = callRepository.findById("demo-call-001").orElseThrow().call();
        AnalystCallRevision predatesCall = correction(
                original, "predating-revision", "predating-revision-event", null, 1,
                original.eventTime().minus(1, ChronoUnit.SECONDS));

        assertThatThrownBy(() -> revisionRepository.saveIfAbsent(predatesCall))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("original call eventTime");

        Instant eventTime = original.eventTime().plus(1, ChronoUnit.HOURS);
        assertThatThrownBy(() -> new AnalystCallRevision(
                "bad-capture-revision", "1.0.0", original.id(), null, 1, "fixture", "bad-capture-event",
                AnalystCallRevisionType.CORRECTION, eventTime, eventTime.plusSeconds(2),
                correctedTerms(original), "Bad capture ordering", original.sourceReference(), original.dataMode(),
                eventTime.plusSeconds(1), "test-provenance"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("capturedAt");
    }

    @Test
    void revisionRepositoryAndDomainExposeNoMutationSurface() {
        assertThat(AnalystCallRevision.class.isRecord()).isTrue();
        assertThat(Arrays.stream(AnalystCallRevision.class.getDeclaredFields())
                .filter(field -> !field.isSynthetic())
                .allMatch(field -> Modifier.isPrivate(field.getModifiers()) && Modifier.isFinal(field.getModifiers())))
                .isTrue();
        assertThat(Arrays.stream(AnalystCallRevisionRepository.class.getMethods())
                .map(java.lang.reflect.Method::getName)
                .noneMatch(name -> name.startsWith("update") || name.startsWith("delete")))
                .isTrue();
        assertThat(Arrays.stream(AnalystCallRepository.class.getMethods())
                .map(java.lang.reflect.Method::getName)
                .noneMatch(name -> name.startsWith("update") || name.startsWith("delete")))
                .isTrue();
    }

    private static AnalystCallRevision correction(
            AnalystCall call,
            String revisionId,
            String providerEventId,
            String supersedesRevisionId,
            int sequenceNumber,
            Instant eventTime) {
        return new AnalystCallRevision(
                revisionId, "1.0.0", call.id(), supersedesRevisionId, sequenceNumber,
                call.provider(), providerEventId, AnalystCallRevisionType.CORRECTION,
                eventTime, eventTime.plusSeconds(30), correctedTerms(call), "Deterministic test correction",
                call.sourceReference(), call.dataMode(), eventTime.plusSeconds(60), "test-revision-provenance");
    }

    private static AnalystCallRevision cancellation(
            AnalystCall call,
            String revisionId,
            String providerEventId,
            String supersedesRevisionId,
            int sequenceNumber,
            Instant eventTime) {
        return new AnalystCallRevision(
                revisionId, "1.0.0", call.id(), supersedesRevisionId, sequenceNumber,
                call.provider(), providerEventId, AnalystCallRevisionType.CANCELLATION,
                eventTime, eventTime.plusSeconds(30), null, "Deterministic test cancellation",
                call.sourceReference(), call.dataMode(), eventTime.plusSeconds(60), "test-revision-provenance");
    }

    private static CorrectedCallTerms correctedTerms(AnalystCall call) {
        return new CorrectedCallTerms(
                call.direction(), call.originalRating(), call.previousTarget(), call.target(),
                call.currency(), call.targetDate());
    }
}
