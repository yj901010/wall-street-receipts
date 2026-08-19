package com.wallstreetreceipts.api.web.call;

import java.util.List;

import com.wallstreetreceipts.api.application.call.AnalystCallDetail;
import com.wallstreetreceipts.api.application.call.AnalystCallPage;
import com.wallstreetreceipts.api.domain.call.AnalystCall;
import com.wallstreetreceipts.api.domain.call.AnalystCallRevision;
import com.wallstreetreceipts.api.domain.call.CorrectedCallTerms;
import com.wallstreetreceipts.api.domain.context.CallContext;
import com.wallstreetreceipts.api.domain.context.MacroObservation;
import com.wallstreetreceipts.api.domain.market.MarketSnapshot;
import com.wallstreetreceipts.api.domain.outcome.CallOutcome;
import com.wallstreetreceipts.api.domain.source.SourceDocument;
import com.wallstreetreceipts.api.domain.source.SourceReference;

final class AnalystCallResponseMapper {

    private static final String SCHEMA_VERSION = "1.0.0";

    private AnalystCallResponseMapper() {
    }

    static AnalystCallResponses.Page toPage(AnalystCallPage page) {
        return new AnalystCallResponses.Page(
                page.items().stream().map(AnalystCallResponseMapper::toItem).toList(),
                new AnalystCallResponses.PageMetadata(
                        page.number(), page.size(), page.totalElements(), page.totalPages(), page.first(), page.last(),
                        new AnalystCallResponses.Sort(page.sort().apiName(), page.order().apiName())));
    }

    static AnalystCallResponses.Detail toDetail(AnalystCallDetail detail) {
        AnalystCallResponses.Item item = toItem(detail);
        return new AnalystCallResponses.Detail(
                item.call(), item.institution(), item.analyst(), item.asset(), item.source(),
                toSnapshot(detail.snapshot()));
    }

    static List<AnalystCallResponses.Revision> toRevisions(List<AnalystCallRevision> revisions) {
        return revisions.stream().map(AnalystCallResponseMapper::toRevision).toList();
    }

    static List<AnalystCallResponses.Outcome> toOutcomes(List<CallOutcome> outcomes) {
        return outcomes.stream().map(AnalystCallResponseMapper::toOutcome).toList();
    }

    static AnalystCallResponses.Context toContext(CallContext context) {
        return new AnalystCallResponses.Context(
                context.macroSnapshot() == null ? null : new AnalystCallResponses.MacroSnapshot(
                        context.macroSnapshot().schemaVersion(), context.macroSnapshot().macroSnapshotId(),
                        context.macroSnapshot().callId(), context.macroSnapshot().eventTime(),
                        context.macroSnapshot().processingTime(), context.macroSnapshot().observations().stream()
                                .map(AnalystCallResponseMapper::toMacroObservation)
                                .toList(),
                        true, context.macroSnapshot().dataMode(), context.macroSnapshot().capturedAt(),
                        context.macroSnapshot().provenanceId()),
                context.eventContext() == null ? null : new AnalystCallResponses.EventContext(
                        context.eventContext().schemaVersion(), context.eventContext().eventContextId(),
                        context.eventContext().callId(), context.eventContext().eventTime(),
                        context.eventContext().processingTime(), context.eventContext().earningsAt(),
                        context.eventContext().nextCpiAt(), context.eventContext().nextFomcAt(),
                        context.eventContext().nextNfpAt(), context.eventContext().optionsExpirationAt(),
                        context.eventContext().sourceReferenceId(), true, context.eventContext().dataMode(),
                        context.eventContext().capturedAt(), context.eventContext().provenanceId()));
    }

    private static AnalystCallResponses.MacroObservation toMacroObservation(MacroObservation observation) {
        return new AnalystCallResponses.MacroObservation(
                observation.schemaVersion(), observation.macroObservationId(), observation.series(),
                observation.value(), observation.unit(), observation.observationDate(), observation.releasedAt(),
                observation.processingTime(), observation.vintageStart(), observation.vintageEnd(),
                observation.sourceReferenceId(), observation.dataMode(), observation.capturedAt(),
                observation.provenanceId());
    }

    private static AnalystCallResponses.Outcome toOutcome(CallOutcome outcome) {
        return new AnalystCallResponses.Outcome(
                outcome.outcomeId(), outcome.schemaVersion(), outcome.callId(), outcome.horizon(),
                outcome.basisRevisionId(), outcome.cancellationRevisionId(), outcome.snapshotId(), outcome.methodologyId(),
                outcome.methodologyVersion(), outcome.methodologyDefinitionHash(), outcome.inputFingerprint(),
                outcome.sequenceNumber(), outcome.supersedesOutcomeId(), outcome.evaluationStatus(),
                outcome.reasonCode(), outcome.eventTime(), outcome.processingTime(), outcome.assetReturn(),
                outcome.benchmarkReturn(), outcome.sectorReturn(), outcome.alpha(), outcome.sectorAlpha(),
                outcome.mfe(), outcome.mae(), outcome.targetHit(), outcome.directionalWin(), outcome.targetError(),
                outcome.dataComplete(), outcome.dataMode(), outcome.capturedAt(), outcome.provenanceId());
    }

    private static AnalystCallResponses.Revision toRevision(AnalystCallRevision revision) {
        return new AnalystCallResponses.Revision(
                revision.id(), revision.schemaVersion(), revision.callId(), revision.supersedesRevisionId(),
                revision.sequenceNumber(), revision.provider(), revision.providerEventId(), revision.type(),
                revision.eventTime(), revision.processingTime(), toCorrectedTerms(revision.correctedTerms()),
                revision.reason(), revision.sourceReference().id(), revision.dataMode(), revision.capturedAt(),
                revision.provenanceId());
    }

    private static AnalystCallResponses.CorrectedTerms toCorrectedTerms(CorrectedCallTerms terms) {
        if (terms == null) {
            return null;
        }
        return new AnalystCallResponses.CorrectedTerms(
                terms.direction(), terms.originalRating(), terms.previousTarget(), terms.target(),
                terms.currency() == null ? null : terms.currency().getCurrencyCode(), terms.targetDate());
    }

    private static AnalystCallResponses.Item toItem(AnalystCallDetail detail) {
        AnalystCall call = detail.call();
        return new AnalystCallResponses.Item(
                toCall(call),
                new AnalystCallResponses.Institution(
                        call.institution().id(), call.institution().canonicalName(), call.institution().slug()),
                call.analyst() == null ? null : new AnalystCallResponses.Analyst(
                        call.analyst().id(), call.analyst().canonicalName()),
                new AnalystCallResponses.Asset(
                        call.asset().id(), call.asset().type(), call.asset().canonicalName(), call.asset().ticker()),
                toSource(call.sourceReference()));
    }

    private static AnalystCallResponses.Call toCall(AnalystCall call) {
        return new AnalystCallResponses.Call(
                SCHEMA_VERSION, call.id(), call.provider(), call.providerEventId(), call.institution().id(),
                call.analyst() == null ? null : call.analyst().id(), call.asset().id(), call.eventTime(),
                call.processingTime(), call.direction(), call.originalRating(), call.previousTarget(), call.target(),
                call.currency() == null ? null : call.currency().getCurrencyCode(), call.targetDate(),
                call.sourceReference().id(), call.status(), call.dataMode(), call.capturedAt(), call.provenanceId());
    }

    private static AnalystCallResponses.Source toSource(SourceReference reference) {
        SourceDocument document = reference.document();
        return new AnalystCallResponses.Source(
                new AnalystCallResponses.SourceDocument(
                        SCHEMA_VERSION, document.id(), document.type(), document.publisher(), document.title(),
                        document.canonicalUrl() == null ? null : document.canonicalUrl().toString(),
                        document.publishedAt(), document.provider(), document.externalId(), document.contentHash(),
                        document.licenseClass(), document.dataMode(), document.capturedAt(), document.provenanceId()),
                new AnalystCallResponses.SourceReference(
                        SCHEMA_VERSION, reference.id(), document.id(), reference.page(), reference.startMs(),
                        reference.endMs(), reference.extractedFragment(), reference.extractionConfidence(),
                        reference.verified(), reference.dataMode(), reference.capturedAt(), reference.provenanceId()));
    }

    private static AnalystCallResponses.Snapshot toSnapshot(MarketSnapshot snapshot) {
        if (snapshot == null) {
            return null;
        }
        return new AnalystCallResponses.Snapshot(
                SCHEMA_VERSION, snapshot.id(), snapshot.callId(), snapshot.assetId(), snapshot.eventTime(),
                snapshot.processingTime(), snapshot.assetPrice(), snapshot.spx(), snapshot.ndx(), snapshot.vix(),
                snapshot.treasury2y(), snapshot.treasury10y(), snapshot.realYield(), snapshot.dxy(), snapshot.wti(),
                snapshot.gold(), snapshot.volatility(), snapshot.distanceFrom52WeekHigh(), snapshot.distanceFromAth(),
                true, snapshot.dataMode(), snapshot.capturedAt(), snapshot.provenanceId());
    }
}
