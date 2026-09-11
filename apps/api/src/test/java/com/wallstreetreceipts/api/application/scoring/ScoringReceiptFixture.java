package com.wallstreetreceipts.api.application.scoring;

import static com.wallstreetreceipts.api.application.scoring.EndpointScoringFixture.*;
import java.math.BigDecimal;
import com.wallstreetreceipts.api.application.port.out.*;
import com.wallstreetreceipts.api.domain.call.*;
import com.wallstreetreceipts.api.domain.market.*;
import com.wallstreetreceipts.api.domain.source.*;
import com.wallstreetreceipts.api.domain.outcome.horizon.*;
import com.wallstreetreceipts.api.domain.outcome.targeteligibility.BasisForecastTermsEvidence;

/** Isolated test-only ledger. No production fixture registry or user database writes. */
final class ScoringReceiptFixture {
    static AnalystCall seed(AnalystCallProvider provider, AnalystCallRepository calls) {
        var template = provider.load().calls().getFirst();
        var document = new SourceDocument("receipt-doc", SourceType.ARTICLE, "DEMO", "DEMO receipt test", null, BASIS,
                "demo-provider", "receipt-document", "demo-content-hash", "INTERNAL_DEMO", DataMode.DEMO, BASIS, "receipt-doc-prov");
        var source = new SourceReference("receipt-ref", document, null, null, null, "DEMO source terms", null, false, DataMode.DEMO, BASIS, "receipt-ref-prov");
        var call = new AnalystCall("demo-call", "demo-provider", "receipt-call-event", template.institution(), template.analyst(), template.asset(),
                BASIS, BASIS, CallDirection.BULLISH, "DEMO", null, new BigDecimal("150"), USD, null, source, CallStatus.ACTIVE, DataMode.DEMO, BASIS, "receipt-call-prov");
        var snapshot = new MarketSnapshot("receipt-snapshot", call.id(), call.asset().id(), BASIS, BASIS,
                new BigDecimal("999"), null, null, null, null, null, null, null, null, null, null, null, null, DataMode.DEMO, BASIS, "receipt-snapshot-prov");
        calls.saveIfAbsent(call, snapshot);
        return call;
    }
    static EndpointScoringInput input(AnalystCall call) {
        var i = EndpointScoringFixture.input(); var asset = call.asset().id();
        return new EndpointScoringInput(i.dataMode(), i.horizon(), i.catalogEvidence(), edit(i.binding(), "assetId", asset), i.evaluationAsOf(),
                new BasisForecastTermsEvidence(call.id(), i.horizon().basis(), asset, call.direction(), i.terms().targetDisposition(),
                        call.provider(), call.providerEventId(), call.processingTime(), call.capturedAt(), call.provenanceId()),
                i.endpointCandidates().stream().map(e -> edit(e, "assetId", asset)).toList(),
                i.basisCandidates().stream().map(e -> edit(e, "assetId", asset)).toList(),
                i.adjustmentCandidates().stream().map(e -> edit(e, "assetId", asset)).toList(), edit(i.targetEvidence(), "assetId", asset));
    }
    static AnalystCallRevision correction(AnalystCall call) {
        return new AnalystCallRevision("receipt-correction", "1.0.0", call.id(), null, 1, call.provider(), "receipt-correction-event",
                AnalystCallRevisionType.CORRECTION, BASIS, BASIS,
                new CorrectedCallTerms(CallDirection.BULLISH, "DEMO corrected", null, call.target(), USD, null), "DEMO correction",
                call.sourceReference(), DataMode.DEMO, BASIS, "receipt-correction-prov");
    }
    static EndpointScoringInput corrected(EndpointScoringInput i, AnalystCallRevision r) {
        var basis = new OutcomeBasis.Correction(r.callId(), r.id(), r.eventTime());
        var terms = new BasisForecastTermsEvidence(r.id(), basis, i.binding().assetId(), r.correctedTerms().direction(), i.terms().targetDisposition(),
                r.provider(), r.providerEventId(), r.processingTime(), r.capturedAt(), r.provenanceId());
        return new EndpointScoringInput(i.dataMode(), edit(i.horizon(), "basis", basis), i.catalogEvidence(), i.binding(), i.evaluationAsOf(), terms,
                i.endpointCandidates(), i.basisCandidates().stream().map(e -> edit(e, "basis", basis)).toList(),
                i.adjustmentCandidates().stream().map(e -> edit(e, "basis", basis)).toList(), edit(i.targetEvidence(), "basis", basis));
    }
}
