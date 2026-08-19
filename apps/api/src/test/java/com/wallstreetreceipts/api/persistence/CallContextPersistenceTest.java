package com.wallstreetreceipts.api.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.net.URI;
import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.ClassPathResource;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import com.wallstreetreceipts.api.application.port.out.AnalystCallProvider;
import com.wallstreetreceipts.api.application.port.out.AnalystCallRepository;
import com.wallstreetreceipts.api.application.port.out.CallContextDataSet;
import com.wallstreetreceipts.api.application.port.out.CallContextRepository;
import com.wallstreetreceipts.api.domain.context.EventContext;
import com.wallstreetreceipts.api.domain.market.DataMode;
import com.wallstreetreceipts.api.domain.source.SourceDocument;
import com.wallstreetreceipts.api.domain.source.SourceReference;
import com.wallstreetreceipts.api.domain.source.SourceType;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class CallContextPersistenceTest {

    @Autowired
    private AnalystCallProvider provider;

    @Autowired
    private AnalystCallRepository callRepository;

    @Autowired
    private CallContextRepository contextRepository;

    @Test
    void packagedFixturePersistsStandaloneArchiveAndSelectedPointInTimeSnapshotExactly() {
        assertThat(new ClassPathResource("fixtures/v1/call-contexts.json").exists()).isTrue();
        var contexts = provider.load().contexts();

        assertThat(contexts.macroObservations()).hasSize(7);
        assertThat(contexts.knownEmptyCallIds()).containsExactly("demo-call-002", "demo-call-003");
        assertThat(contextRepository.observationCount()).isEqualTo(7);
        assertThat(contextRepository.macroSnapshotCount()).isEqualTo(1);
        assertThat(contextRepository.eventContextCount()).isEqualTo(1);

        var snapshot = contextRepository.findMacroSnapshotByCallId("demo-call-001").orElseThrow();
        assertThat(snapshot.observations())
                .extracting(observation -> observation.series().name())
                .containsExactly(
                        "FED_FUNDS_LOWER", "FED_FUNDS_UPPER", "CPI_YOY",
                        "CORE_CPI_YOY", "PPI_YOY", "UNEMPLOYMENT_RATE");
        assertThat(snapshot.observations().get(2).value()).isEqualByComparingTo("3.10");
        assertThat(snapshot.observations().get(4).value()).isNull();
        assertThat(snapshot.observations())
                .extracting(observation -> observation.macroObservationId())
                .doesNotContain("macro-observation-demo-cpi-revision-001");
        assertThat(contextRepository.findObservationById("macro-observation-demo-cpi-revision-001"))
                .isPresent();
        assertThat(contextRepository.findMacroSnapshotByCallId("demo-call-002")).isEmpty();
        assertThat(contextRepository.findEventContextByCallId("demo-call-003")).isEmpty();
    }

    @Test
    void exactReplayIsIdempotentAndDifferentContextForSameCallIsRejected() {
        assertThat(contextRepository.importDataSet(provider.load().contexts())).isZero();
        EventContext original = provider.load().contexts().eventContexts().getFirst();
        EventContext conflicting = new EventContext(
                original.schemaVersion(), "event-context-conflict-001", original.callId(), original.eventTime(),
                original.processingTime(), original.earningsAt(), original.nextCpiAt(), original.nextFomcAt(),
                original.nextNfpAt(), original.optionsExpirationAt(), original.sourceReferenceId(),
                original.dataMode(), original.capturedAt(), original.provenanceId());

        assertThatThrownBy(() -> contextRepository.importDataSet(new CallContextDataSet(
                List.of(), List.of(), List.of(), List.of(), List.of(conflicting), List.of())))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("conflicting callId");
    }

    @Test
    void eventCalendarEvidenceCapturedAfterCallEventIsRejected() {
        var call = callRepository.findById("demo-call-002").orElseThrow().call();
        Instant afterEvent = call.eventTime().plusSeconds(1);
        SourceDocument document = document("source-context-late-001", afterEvent);
        SourceReference reference = reference("source-ref-context-late-001", document, afterEvent);
        EventContext context = new EventContext(
                "1.0.0", "event-context-late-evidence-001", call.id(), call.eventTime(),
                call.processingTime().plusSeconds(60), null, null, null, null, null,
                reference.id(), DataMode.DEMO, call.processingTime().plusSeconds(60), "test-context");

        assertThatThrownBy(() -> contextRepository.importDataSet(new CallContextDataSet(
                List.of(document), List.of(reference), List.of(), List.of(), List.of(context), List.of())))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("not available in time");
    }

    @Test
    void valid128CharacterSourceIdentifiersRoundTripWithoutTruncation() {
        var call = callRepository.findById("demo-call-002").orElseThrow().call();
        String documentId = "d".repeat(128);
        String referenceId = "r".repeat(128);
        String contextId = "e".repeat(128);
        SourceDocument document = document(documentId, call.eventTime().minusSeconds(2));
        SourceReference reference = reference(referenceId, document, call.eventTime().minusSeconds(1));
        EventContext context = new EventContext(
                "1.0.0", contextId, call.id(), call.eventTime(), call.processingTime().plusSeconds(60),
                null, null, null, null, null, referenceId, DataMode.DEMO,
                call.processingTime().plusSeconds(60), "test-context");

        assertThat(contextRepository.importDataSet(new CallContextDataSet(
                List.of(document), List.of(reference), List.of(), List.of(), List.of(context), List.of())))
                .isEqualTo(1);
        assertThat(contextRepository.findEventContextByCallId(call.id()).orElseThrow().sourceReferenceId())
                .isEqualTo(referenceId);
    }

    private static SourceDocument document(String id, Instant capturedAt) {
        return new SourceDocument(
                id, SourceType.ARTICLE, "test publisher", "test context evidence",
                URI.create("https://example.invalid/" + id.charAt(0)), capturedAt, "test-provider",
                id, null, "INTERNAL_DEMO", DataMode.DEMO, capturedAt, "test-context");
    }

    private static SourceReference reference(String id, SourceDocument document, Instant capturedAt) {
        return new SourceReference(
                id, document, null, null, null, null, null, false,
                DataMode.DEMO, capturedAt, "test-context");
    }
}
