package com.wallstreetreceipts.api.application.scoring;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static com.wallstreetreceipts.api.application.scoring.EndpointScoringFixture.*;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.*;
import org.springframework.context.annotation.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import com.wallstreetreceipts.api.application.port.out.*;
import com.wallstreetreceipts.api.domain.call.*;
import com.wallstreetreceipts.api.web.scoring.ComparativeScoringReceiptResponse;

@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:comparative-scoring-receipts;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1")
@ActiveProfiles("test") @AutoConfigureMockMvc @Transactional
@Import(ComparativeScoringReceiptApiTest.FixedClock.class)
class ComparativeScoringReceiptApiTest {
    static final String PATH = "/v1/calls/demo-call/comparative-scoring-receipts";
    @Autowired ComparativeScoringReceiptService service;
    @Autowired ComparativeScoringReceiptRepository receipts;
    @Autowired AnalystCallRepository calls;
    @Autowired AnalystCallRevisionRepository revisions;
    @Autowired AnalystCallProvider provider;
    @Autowired ScoringLedgerVerifier ledger;
    @Autowired JdbcTemplate jdbc;
    @Autowired MockMvc mvc;
    AnalystCall call; EndpointScoringInput input;
    @BeforeEach void seed() { call = ScoringReceiptFixture.seed(provider, calls); input = ScoringReceiptFixture.input(call); }
    @TestConfiguration static class FixedClock {
        @Bean @Primary Clock receiptClock() { return Clock.fixed(AS_OF.plusSeconds(60), ZoneOffset.UTC); }
    }
    @Test void appendReplaysAndRetainsOriginalBytesTimeAndIdentityWithoutTreatingSnapshotAsPrice() throws Exception {
        var saved = append(input, "receipt-snapshot");
        assertThat(append(input, "receipt-snapshot").stored().receiptId()).isEqualTo(saved.stored().receiptId());
        var replay = service.find(call.id(), saved.stored().receiptId());
        assertThat(replay.stored().inputBytes()).isEqualTo(ComparativeScoringInputCodec.encode(ComparativeScoringFixture.input(input)));
        var bytes = replay.stored().inputBytes(); bytes[0] = 0;
        assertThat(replay.stored().inputBytes()[0]).isEqualTo((byte) 1);
        var response = ComparativeScoringReceiptResponse.from(replay);
        assertThat(response.assetReturn().decimalValue()).isEqualTo("0.200000000000"); // snapshot price is 999, input basis price is 100
        assertThat(response.targetError().decimalValue()).isEqualTo("0.250000000000");
        assertThat(response.directionalWin().booleanValue()).isTrue();
        assertThat(response.benchmarkReturn().decimalValue()).isEqualTo("0.100000000000");
        assertThat(response.sectorReturn().decimalValue()).isEqualTo("-0.050000000000");
        assertThat(response.benchmarkEvidence().sourceBindingRole()).isEqualTo("BENCHMARK_ASSIGNMENT");
        assertThat(response.sectorEvidence().sourceBindingRole()).isEqualTo("SECTOR_MAPPING");
        assertThat(response.benchmarkEvidence().basisLevel().value()).isEqualTo("4000");
        assertThat(response.sectorEvidence().endpointLevel().value()).isEqualTo("1900");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM demo_scoring_receipts", Integer.class)).isZero();
        mvc.perform(get(PATH + "/" + saved.stored().receiptId())).andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.dataMode").value("DEMO")).andExpect(jsonPath("$.dataComplete").value(false))
                .andExpect(jsonPath("$.recordedAtUtc").value("2026-01-06T00:01:00Z"))
                .andExpect(jsonPath("$.evaluationAsOfKst").value("2026-01-06T09:00+09:00"))
                .andExpect(jsonPath("$.assetReturn.decimalValue").value("0.200000000000"))
                .andExpect(jsonPath("$.inputBytes").doesNotExist());
        mvc.perform(head(PATH)).andExpect(status().isOk()).andExpect(header().string("Cache-Control", "no-store"));
        assertThat(receipts.recent(call.id())).hasSize(1);
    }
    @Test void changedInputAppendsNewReceiptAndRecentIsBoundedWithDeterministicTies() {
        for (int n = 0; n < 23; n++) append(edit(input, "evaluationAsOf", AS_OF.plusSeconds(n)), "receipt-snapshot");
        var page = service.recent(call.id());
        assertThat(page.items()).hasSize(20); assertThat(page.hasMore()).isTrue();
        assertThat(page.items().stream().map(v -> v.stored().receiptId().toString()).toList()).isSortedAccordingTo(Comparator.reverseOrder());
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM demo_comparative_scoring_receipts", Integer.class)).isEqualTo(23);
    }
    @Test void correctionRequiresActualSameCallRevisionAndRoundTripsItsSequence() {
        var r = ScoringReceiptFixture.correction(call);
        var corrected = ScoringReceiptFixture.corrected(input, r);
        assertThatThrownBy(() -> append(corrected, "receipt-snapshot")).isInstanceOf(IllegalArgumentException.class);
        assertThat(revisions.saveIfAbsent(r)).isTrue();
        var stored = append(corrected, "receipt-snapshot").stored();
        assertThat(stored.basisRevisionSequence()).isEqualTo(1);
        assertThat(service.find(call.id(), stored.receiptId()).evaluation().input().endpoint().horizon().basis().basisRevisionId()).isEqualTo(r.id());
        assertThat(ComparativeScoringInputCodec.encode(ComparativeScoringInputCodec.decode(stored.inputBytes()))).isEqualTo(stored.inputBytes());
    }
    @Test void knownCancellationRejectsButFutureCancellationDoesNotRewriteOldInformationSet() {
        var r = ScoringReceiptFixture.correction(call);
        var cancellation = new AnalystCallRevision(r.id(), r.schemaVersion(), r.callId(), null, 1, r.provider(), r.providerEventId(),
                AnalystCallRevisionType.CANCELLATION, BASIS, AS_OF.plusSeconds(1), null, "DEMO cancellation", r.sourceReference(), r.dataMode(), AS_OF.plusSeconds(1), r.provenanceId());
        revisions.saveIfAbsent(cancellation);
        append(input, "receipt-snapshot");
        assertThatThrownBy(() -> append(edit(input, "evaluationAsOf", AS_OF.plusSeconds(2)), "receipt-snapshot"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(service.recent(call.id()).items()).hasSize(1);
    }
    @ParameterizedTest @ValueSource(strings = {"provider", "providerEventId", "provenanceId", "termsEvidenceId"})
    void suppliedTermsCannotForgePersistedIdentity(String field) {
        assertThatThrownBy(() -> append(edit(input, "terms", edit(input.terms(), field, "forged")), "receipt-snapshot"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(receipts.recent(call.id())).isEmpty();
    }
    @Test void wrongSnapshotFutureEvaluationAndForgedDirectionAreRejected() {
        assertThatThrownBy(() -> append(input, "demo-snapshot-001")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> append(edit(input, "evaluationAsOf", AS_OF.plusSeconds(61)), "receipt-snapshot")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> append(edit(input, "terms", edit(input.terms(), "direction", CallDirection.BEARISH)), "receipt-snapshot")).isInstanceOf(IllegalArgumentException.class);
    }
    @ParameterizedTest @ValueSource(strings = {"call-time", "snapshot-time", "source-time", "document-time", "document-publication", "call-mode", "snapshot-mode", "source-mode", "document-mode", "cancelled", "event"})
    void originalLedgerMustBeDemoAndPointInTimeVisible(String mutation) {
        var future = java.sql.Timestamp.from(AS_OF.plusSeconds(1));
        switch (mutation) {
            case "call-time" -> jdbc.update("UPDATE analyst_calls SET captured_at=? WHERE call_id=?", future, call.id());
            case "snapshot-time" -> jdbc.update("UPDATE market_snapshots SET captured_at=? WHERE call_id=?", future, call.id());
            case "source-time" -> jdbc.update("UPDATE source_references SET captured_at=? WHERE source_reference_id='receipt-ref'", future);
            case "document-time" -> jdbc.update("UPDATE source_documents SET captured_at=? WHERE source_document_id='receipt-doc'", future);
            case "document-publication" -> jdbc.update("UPDATE source_documents SET published_at=? WHERE source_document_id='receipt-doc'", future);
            case "call-mode" -> jdbc.update("UPDATE analyst_calls SET data_mode='REALTIME' WHERE call_id=?", call.id());
            case "snapshot-mode" -> jdbc.update("UPDATE market_snapshots SET data_mode='REALTIME' WHERE call_id=?", call.id());
            case "source-mode" -> jdbc.update("UPDATE source_references SET data_mode='REALTIME' WHERE source_reference_id='receipt-ref'");
            case "document-mode" -> jdbc.update("UPDATE source_documents SET data_mode='REALTIME' WHERE source_document_id='receipt-doc'");
            case "cancelled" -> jdbc.update("UPDATE analyst_calls SET status='CANCELLED' WHERE call_id=?", call.id());
            case "event" -> jdbc.update("UPDATE analyst_calls SET event_time=? WHERE call_id=?", java.sql.Timestamp.from(BASIS.minusSeconds(1)), call.id());
        }
        assertThatThrownBy(() -> append(input, "receipt-snapshot")).isInstanceOf(IllegalArgumentException.class);
        assertThat(receipts.recent(call.id())).isEmpty();
    }
    @Test void missingTargetStaysMissingAndCorrectionCaptureAfterAsOfIsRejected() {
        jdbc.update("UPDATE analyst_calls SET target=NULL WHERE call_id=?", call.id());
        var absent = edit(edit(input, "targetEvidence", null), "terms", edit(input.terms(), "targetDisposition",
                new com.wallstreetreceipts.api.domain.outcome.targeteligibility.BasisForecastTermsEvidence.TargetDisposition.Absent()));
        var response = ComparativeScoringReceiptResponse.from(append(absent, "receipt-snapshot"));
        assertThat(response.targetError().decimalValue()).isNull();
        assertThat(response.targetError().reasons()).contains("TARGET_MISSING_AS_OF");
        var revision = edit(ScoringReceiptFixture.correction(call), "capturedAt", AS_OF.plusSeconds(1));
        revisions.saveIfAbsent(revision);
        var basisInput = ScoringReceiptFixture.corrected(input, ScoringReceiptFixture.correction(call));
        assertThatThrownBy(() -> append(basisInput, "receipt-snapshot")).isInstanceOf(IllegalArgumentException.class);
    }
    @ParameterizedTest @ValueSource(strings = {"methodology_id", "methodology_version", "methodology_definition_hash", "methodology_definition", "input_fingerprint", "ledger_fingerprint"})
    void metadataDriftFailsClosedWithoutEchoOrListFallback(String column) throws Exception {
        append(input, "receipt-snapshot");
        jdbc.update("UPDATE demo_comparative_scoring_receipts SET " + column + "=?", "x");
        unavailable();
    }
    @ParameterizedTest @ValueSource(strings = {"bytes", "snapshot", "source", "call", "asof", "sequence"})
    void payloadAndLedgerDriftAreNeverPublished(String field) throws Exception {
        append(input, "receipt-snapshot");
        switch (field) {
            case "bytes" -> jdbc.update("UPDATE demo_comparative_scoring_receipts SET input_bytes=?", new byte[]{1,2,3});
            case "snapshot" -> jdbc.update("UPDATE market_snapshots SET asset_price=998 WHERE call_id=?", call.id());
            case "source" -> jdbc.update("UPDATE source_references SET extracted_fragment='tampered' WHERE source_reference_id=?", call.sourceReference().id());
            case "call" -> jdbc.update("UPDATE analyst_calls SET original_rating='tampered' WHERE call_id=?", call.id());
            case "asof" -> jdbc.update("UPDATE demo_comparative_scoring_receipts SET evaluation_as_of=?", java.sql.Timestamp.from(AS_OF.minusSeconds(1)));
            case "sequence" -> { // A valid correction receipt must retain the exact sequence through the FK and replay.
                var r = ScoringReceiptFixture.correction(call); revisions.saveIfAbsent(r);
                append(ScoringReceiptFixture.corrected(input, r), "receipt-snapshot");
                jdbc.update("UPDATE analyst_call_revisions SET reason='tampered' WHERE revision_id=?", r.id());
            }
        }
        unavailable();
    }
    private ComparativeScoringReceiptService.Verified append(EndpointScoringInput endpoint, String snapshot) {
        return service.append(ComparativeScoringFixture.input(endpoint), snapshot);
    }
    private void unavailable() throws Exception {
        mvc.perform(get(PATH)).andExpect(status().isServiceUnavailable()).andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(content().json("{\"code\":\"COMPARATIVE_SCORING_RECEIPT_UNAVAILABLE\",\"dataMode\":\"DEMO\"}", true));
    }
    @Test void emptyMissingCrossCallInvalidQueriesAndNoHttpWriter() throws Exception {
        mvc.perform(get(PATH)).andExpect(status().isOk()).andExpect(jsonPath("$.items").isEmpty());
        var id = append(input, "receipt-snapshot").stored().receiptId();
        mvc.perform(get("/v1/calls/demo-call-002/comparative-scoring-receipts/" + id)).andExpect(status().isNotFound());
        mvc.perform(get(PATH + "/" + UUID.randomUUID())).andExpect(status().isNotFound());
        mvc.perform(get(PATH + "/1-1-1-1-1")).andExpect(status().isBadRequest());
        mvc.perform(get(PATH).queryParam("secret", "DO_NOT_ECHO")).andExpect(status().isBadRequest())
                .andExpect(content().json("{\"code\":\"INVALID_COMPARATIVE_SCORING_QUERY\",\"dataMode\":\"DEMO\"}", true));
        for (var request : List.of(post(PATH), put(PATH), patch(PATH), delete(PATH))) mvc.perform(request).andExpect(status().isMethodNotAllowed());
        assertThat(receipts.recent(call.id())).hasSize(1);
    }
    @Test void nullEvidenceIsNotZeroAndNeutralRemainsNotApplicableWhileReturnIsPending() {
        jdbc.update("UPDATE analyst_calls SET direction='NEUTRAL' WHERE call_id=?", call.id());
        var neutral = edit(input, "terms", edit(input.terms(), "direction", CallDirection.NEUTRAL));
        var pending = edit(neutral, "evaluationAsOf", BASIS.plusSeconds(1));
        var response = ComparativeScoringReceiptResponse.from(append(pending, "receipt-snapshot"));
        assertThat(response.assetReturn().state()).isEqualTo("PENDING");
        assertThat(response.assetReturn().decimalValue()).isNull();
        assertThat(response.directionalWin().state()).isEqualTo("NOT_APPLICABLE");
        assertThat(response.directionalWin().booleanValue()).isNull();
        var incomplete = edit(neutral, "horizon", edit(neutral.horizon(), "catalog", edit(neutral.horizon().catalog(), "orderedSessions", List.of())));
        assertThat(ComparativeScoringReceiptResponse.from(append(incomplete, "receipt-snapshot")).assetReturn().state()).isEqualTo("UNAVAILABLE");
    }

    @Test void changedComparativeEvidenceAppendsWithoutRewritingEitherProfileAndTamperingFails() throws Exception {
        var original = ComparativeScoringFixture.input(input);
        var first = service.append(original, "receipt-snapshot");
        var changed = edit(original, "benchmark", edit(original.benchmark(), "endpointLevelCandidates", List.of()));
        var second = service.append(changed, "receipt-snapshot");
        assertThat(second.stored().receiptId()).isNotEqualTo(first.stored().receiptId());
        assertThat(second.stored().ledgerFingerprint()).isEqualTo(first.stored().ledgerFingerprint());
        assertThat(second.stored().inputFingerprint()).isNotEqualTo(first.stored().inputFingerprint());
        var replay = ComparativeScoringReceiptResponse.from(service.find(call.id(), second.stored().receiptId()));
        assertThat(replay.benchmarkReturn().state()).isEqualTo("UNAVAILABLE");
        assertThat(replay.benchmarkReturn().decimalValue()).isNull();
        assertThat(replay.benchmarkEvidence()).isNull();
        assertThat(replay.sectorReturn().decimalValue()).isEqualTo("-0.050000000000");
        assertThat(service.find(call.id(), first.stored().receiptId()).stored().inputBytes()).isEqualTo(first.stored().inputBytes());
        jdbc.update("UPDATE demo_comparative_scoring_receipts SET input_bytes=? WHERE receipt_id=?",
                second.stored().inputBytes(), first.stored().receiptId().toString());
        unavailable();
        mvc.perform(get(PATH + "/" + first.stored().receiptId())).andExpect(status().isServiceUnavailable());
        mvc.perform(get(PATH + "/" + second.stored().receiptId())).andExpect(status().isOk());
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM demo_scoring_receipts", Integer.class)).isZero();
    }
}
