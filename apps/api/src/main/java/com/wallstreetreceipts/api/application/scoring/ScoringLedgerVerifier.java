package com.wallstreetreceipts.api.application.scoring;

import java.io.*;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Objects;
import org.springframework.stereotype.Component;
import com.wallstreetreceipts.api.application.port.out.AnalystCallRepository;
import com.wallstreetreceipts.api.application.port.out.AnalystCallRevisionRepository;
import com.wallstreetreceipts.api.domain.call.*;
import com.wallstreetreceipts.api.domain.market.DataMode;
import com.wallstreetreceipts.api.domain.source.SourceReference;
import com.wallstreetreceipts.api.domain.outcome.horizon.OutcomeBasis;
import com.wallstreetreceipts.api.domain.outcome.targeteligibility.BasisForecastTermsEvidence;
import com.wallstreetreceipts.api.domain.outcome.targeteligibility.BasisForecastTermsEvidence.TargetDisposition;

/** Binds supplied DEMO evidence to persisted source terms; does not attest provider prices. */
@Component
public class ScoringLedgerVerifier {
    private final AnalystCallRepository calls;
    private final AnalystCallRevisionRepository revisions;
    public ScoringLedgerVerifier(AnalystCallRepository calls, AnalystCallRevisionRepository revisions) {
        this.calls = calls; this.revisions = revisions;
    }
    public record Binding(String fingerprint, Integer revisionSequence, String snapshotProvenanceId) {}

    public Binding verify(EndpointScoringInput input, String snapshotId) {
        var basis = input.horizon().basis();
        var detail = calls.findById(basis.callId()).orElseThrow(ScoringLedgerVerifier::invalid);
        var call = detail.call();
        var snapshot = detail.snapshot();
        var asOf = input.evaluationAsOf();
        require(call.dataMode() == DataMode.DEMO && call.status() != CallStatus.CANCELLED);
        require(call.id().equals(basis.callId()) && call.asset().id().equals(input.binding().assetId()));
        visible(call.processingTime(), asOf); visible(call.capturedAt(), asOf);
        source(call.sourceReference(), asOf);
        require(snapshot != null && snapshot.id().equals(snapshotId) && snapshot.dataMode() == DataMode.DEMO);
        require(snapshot.callId().equals(call.id()) && snapshot.assetId().equals(call.asset().id())
                && snapshot.eventTime().equals(call.eventTime()));
        visible(snapshot.processingTime(), asOf); visible(snapshot.capturedAt(), asOf);

        var history = revisions.findByCallId(call.id());
        require(history.stream().allMatch(r -> r.callId().equals(call.id())));
        // Later or late-captured revisions cannot rewrite this evaluation's information set.
        require(history.stream().noneMatch(r -> r.type() == AnalystCallRevisionType.CANCELLATION
                && !r.processingTime().isAfter(asOf) && !r.capturedAt().isAfter(asOf)));
        AnalystCallRevision revision = null;
        BasisForecastTermsEvidence actual;
        if (basis instanceof OutcomeBasis.Original) {
            require(basis.eventTime().equals(call.eventTime()));
            actual = new BasisForecastTermsEvidence(call.id(), basis, call.asset().id(), call.direction(),
                    target(call.target(), call.currency(), call.targetDate()), call.provider(), call.providerEventId(),
                    call.processingTime(), call.capturedAt(), call.provenanceId());
        } else {
            revision = history.stream().filter(r -> r.id().equals(basis.basisRevisionId())).findFirst().orElseThrow(ScoringLedgerVerifier::invalid);
            require(revision.type() == AnalystCallRevisionType.CORRECTION && revision.dataMode() == DataMode.DEMO
                    && basis.eventTime().equals(revision.eventTime()));
            visible(revision.processingTime(), asOf); visible(revision.capturedAt(), asOf);
            source(revision.sourceReference(), asOf);
            var terms = revision.correctedTerms();
            actual = new BasisForecastTermsEvidence(revision.id(), basis, call.asset().id(), terms.direction(),
                    target(terms.target(), terms.currency(), terms.targetDate()), revision.provider(), revision.providerEventId(),
                    revision.processingTime(), revision.capturedAt(), revision.provenanceId());
        }
        var authoritative = new EndpointScoringInput(input.dataMode(), input.horizon(), input.catalogEvidence(), input.binding(), asOf,
                actual, input.endpointCandidates(), input.basisCandidates(), input.adjustmentCandidates(), input.targetEvidence());
        // Canonical numeric equality ignores JDBC's presentation scale, never value differences.
        require(EndpointScoringFingerprint.fingerprint(authoritative).equals(EndpointScoringFingerprint.fingerprint(input)));
        var ledger = new Ledger();
        ledger.fields("wsr-demo-scoring-ledger-v1", call.id(), call.provider(), call.providerEventId(), call.institution().id(),
                call.analyst() == null ? null : call.analyst().id(), call.asset().id(), call.eventTime(), call.processingTime(),
                call.direction(), call.originalRating(), call.previousTarget(), call.target(), call.currency(), call.targetDate(),
                call.status(), call.dataMode(), call.capturedAt(), call.provenanceId());
        ledger.source(call.sourceReference());
        ledger.fields(snapshot.id(), snapshot.callId(), snapshot.assetId(), snapshot.eventTime(), snapshot.processingTime(),
                snapshot.assetPrice(), snapshot.spx(), snapshot.ndx(), snapshot.vix(), snapshot.treasury2y(), snapshot.treasury10y(),
                snapshot.realYield(), snapshot.dxy(), snapshot.wti(), snapshot.gold(), snapshot.volatility(),
                snapshot.distanceFrom52WeekHigh(), snapshot.distanceFromAth(), snapshot.dataMode(), snapshot.capturedAt(), snapshot.provenanceId());
        ledger.fields(revision == null ? null : revision.id());
        if (revision != null) {
            var t = revision.correctedTerms();
            ledger.fields(revision.schemaVersion(), revision.callId(), revision.supersedesRevisionId(), revision.sequenceNumber(),
                    revision.provider(), revision.providerEventId(), revision.type(), revision.eventTime(), revision.processingTime(),
                    t.direction(), t.originalRating(), t.previousTarget(), t.target(), t.currency(), t.targetDate(), revision.reason(),
                    revision.dataMode(), revision.capturedAt(), revision.provenanceId());
            ledger.source(revision.sourceReference());
        }
        return new Binding(ledger.hash(), revision == null ? null : revision.sequenceNumber(), snapshot.provenanceId());
    }
    private static TargetDisposition target(BigDecimal price, java.util.Currency currency, java.time.LocalDate date) {
        return price == null ? new TargetDisposition.Absent() : new TargetDisposition.Present(price, currency, date);
    }
    private static void source(SourceReference s, Instant asOf) {
        require(s.dataMode() == DataMode.DEMO && s.document().dataMode() == DataMode.DEMO);
        visible(s.capturedAt(), asOf); visible(s.document().capturedAt(), asOf);
        if (s.document().publishedAt() != null) visible(s.document().publishedAt(), asOf);
    }
    private static void visible(Instant time, Instant asOf) { require(!time.isAfter(asOf)); }
    private static void require(boolean valid) { if (!valid) throw invalid(); }
    private static IllegalArgumentException invalid() { return new IllegalArgumentException("Scoring ledger binding is invalid"); }

    private static final class Ledger {
        private final ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        private final DataOutputStream out = new DataOutputStream(bytes);
        void fields(Object... values) {
            try {
                for (Object value : values) {
                    if (value == null) { out.writeInt(-1); continue; }
                    String text = value instanceof BigDecimal decimal ? decimal.stripTrailingZeros().toPlainString() : Objects.toString(value);
                    var encoded = StandardCharsets.UTF_8.newEncoder().encode(java.nio.CharBuffer.wrap(text));
                    var raw = new byte[encoded.remaining()]; encoded.get(raw);
                    out.writeInt(raw.length); out.write(raw);
                }
            } catch (IOException failure) { throw invalid(); }
        }
        void source(SourceReference s) {
            var d = s.document();
            fields(s.id(), s.page(), s.startMs(), s.endMs(), s.extractedFragment(), s.extractionConfidence(), s.verified(),
                    s.dataMode(), s.capturedAt(), s.provenanceId(), d.id(), d.type(), d.publisher(), d.title(), d.canonicalUrl(),
                    d.publishedAt(), d.provider(), d.externalId(), d.contentHash(), d.licenseClass(), d.dataMode(), d.capturedAt(), d.provenanceId());
        }
        String hash() { return EndpointScoringInputCodec.hash(bytes.toByteArray()); }
    }
}
