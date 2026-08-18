package com.wallstreetreceipts.api.infrastructure.provider.fixture;

import java.net.URI;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Currency;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import com.wallstreetreceipts.api.application.port.out.AnalystCallDataSet;
import com.wallstreetreceipts.api.domain.call.AnalystCall;
import com.wallstreetreceipts.api.domain.call.AnalystCallRevision;
import com.wallstreetreceipts.api.domain.call.AnalystCallRevisionType;
import com.wallstreetreceipts.api.domain.call.CallDirection;
import com.wallstreetreceipts.api.domain.call.CallStatus;
import com.wallstreetreceipts.api.domain.call.CorrectedCallTerms;
import com.wallstreetreceipts.api.domain.market.DataMode;
import com.wallstreetreceipts.api.domain.market.MarketSnapshot;
import com.wallstreetreceipts.api.domain.master.Analyst;
import com.wallstreetreceipts.api.domain.master.Asset;
import com.wallstreetreceipts.api.domain.master.AssetType;
import com.wallstreetreceipts.api.domain.master.Institution;
import com.wallstreetreceipts.api.domain.outcome.CallOutcome;
import com.wallstreetreceipts.api.domain.outcome.MethodologyStatus;
import com.wallstreetreceipts.api.domain.outcome.OutcomeEvaluationStatus;
import com.wallstreetreceipts.api.domain.outcome.OutcomeHorizon;
import com.wallstreetreceipts.api.domain.outcome.OutcomeReasonCode;
import com.wallstreetreceipts.api.domain.outcome.ScoringMethodology;
import com.wallstreetreceipts.api.domain.source.SourceDocument;
import com.wallstreetreceipts.api.domain.source.SourceReference;
import com.wallstreetreceipts.api.domain.source.SourceType;
import com.wallstreetreceipts.api.infrastructure.provider.fixture.FixtureAnalystCallDocuments.AnalystCallsDocument;
import com.wallstreetreceipts.api.infrastructure.provider.fixture.FixtureAnalystCallDocuments.AnalystCallRevisionsDocument;
import com.wallstreetreceipts.api.infrastructure.provider.fixture.FixtureAnalystCallDocuments.CorrectedCallTermsDto;
import com.wallstreetreceipts.api.infrastructure.provider.fixture.FixtureAnalystCallDocuments.CallOutcomesDocument;
import com.wallstreetreceipts.api.infrastructure.provider.fixture.FixtureAnalystCallDocuments.MasterDataDocument;
import com.wallstreetreceipts.api.infrastructure.provider.fixture.FixtureAnalystCallDocuments.MarketSnapshotsDocument;

final class FixtureAnalystCallMapper {

    private FixtureAnalystCallMapper() {
    }

    static AnalystCallDataSet toCanonical(
            MasterDataDocument masterData,
            AnalystCallsDocument callData,
            AnalystCallRevisionsDocument revisionData,
            MarketSnapshotsDocument snapshotData,
            CallOutcomesDocument outcomeData) {
        List<Institution> institutions = masterData.institutions().stream()
                .map(source -> new Institution(
                        source.institutionId(), source.canonicalName(), source.slug(), source.country(),
                        source.active(), dataMode(source.dataMode()), instant(source.effectiveAt()),
                        instant(source.capturedAt()), source.provenanceId()))
                .toList();
        Map<String, Institution> institutionsById = index(institutions, Institution::id);

        List<Analyst> analysts = masterData.analysts().stream()
                .map(source -> new Analyst(
                        source.analystId(), source.canonicalName(), source.active(), dataMode(source.dataMode()),
                        instant(source.effectiveAt()), instant(source.capturedAt()), source.provenanceId()))
                .toList();
        Map<String, Analyst> analystsById = index(analysts, Analyst::id);

        List<Asset> assets = masterData.assets().stream()
                .map(source -> new Asset(
                        source.assetId(), AssetType.valueOf(source.assetType()), source.canonicalName(),
                        source.ticker(), Currency.getInstance(source.primaryCurrency()), source.active(),
                        dataMode(source.dataMode()), instant(source.effectiveAt()), instant(source.capturedAt()),
                        source.provenanceId()))
                .toList();
        Map<String, Asset> assetsById = index(assets, Asset::id);

        Map<String, SourceDocument> documentsById = callData.sourceDocuments().stream()
                .map(source -> new SourceDocument(
                        source.sourceDocumentId(), SourceType.valueOf(source.sourceType()), source.publisher(),
                        source.title(), uri(source.canonicalUrl()), nullableInstant(source.publishedAt()),
                        source.provider(), source.externalId(), source.contentHash(), source.licenseClass(),
                        dataMode(source.dataMode()), instant(source.capturedAt()), source.provenanceId()))
                .collect(Collectors.toUnmodifiableMap(SourceDocument::id, Function.identity()));

        Map<String, SourceReference> referencesById = callData.sourceReferences().stream()
                .map(source -> new SourceReference(
                        source.sourceReferenceId(), required(documentsById, source.sourceDocumentId(), "source document"),
                        source.page(), source.startMs(), source.endMs(), source.extractedFragment(),
                        source.extractionConfidence(), source.verified(), dataMode(source.dataMode()),
                        instant(source.capturedAt()), source.provenanceId()))
                .collect(Collectors.toUnmodifiableMap(SourceReference::id, Function.identity()));

        List<AnalystCall> calls = callData.calls().stream()
                .map(source -> new AnalystCall(
                        source.callId(), source.provider(), source.providerEventId(),
                        required(institutionsById, source.institutionId(), "institution"),
                        nullable(analystsById, source.analystId()),
                        required(assetsById, source.assetId(), "asset"),
                        instant(source.eventTime()), instant(source.processingTime()),
                        CallDirection.valueOf(source.direction()), source.originalRating(), source.previousTarget(),
                        source.target(), currency(source.currency()), localDate(source.targetDate()),
                        required(referencesById, source.sourceReferenceId(), "source reference"),
                        CallStatus.valueOf(source.status()), dataMode(source.dataMode()), instant(source.capturedAt()),
                        source.provenanceId()))
                .toList();
        Map<String, AnalystCall> callsById = index(calls, AnalystCall::id);

        List<AnalystCallRevision> revisions = revisionData.revisions().stream()
                .map(source -> new AnalystCallRevision(
                        source.revisionId(), source.schemaVersion(), source.callId(), source.supersedesRevisionId(),
                        source.sequenceNumber(), source.provider(), source.providerEventId(),
                        AnalystCallRevisionType.valueOf(source.revisionType()), instant(source.eventTime()),
                        instant(source.processingTime()), correctedTerms(source.correctedTerms()), source.reason(),
                        required(referencesById, source.sourceReferenceId(), "source reference"),
                        dataMode(source.dataMode()), instant(source.capturedAt()), source.provenanceId()))
                .toList();
        Map<String, AnalystCallRevision> revisionsById = index(revisions, AnalystCallRevision::id);

        List<MarketSnapshot> snapshots = snapshotData.snapshots().stream()
                .map(source -> {
                    if (!source.immutable()) {
                        throw new IllegalStateException("Fixture snapshot must be immutable: " + source.snapshotId());
                    }
                    return new MarketSnapshot(
                            source.snapshotId(), source.callId(), source.assetId(), instant(source.eventTime()),
                            instant(source.processingTime()), source.assetPrice(), source.spx(), source.ndx(), source.vix(),
                            source.treasury2y(), source.treasury10y(), source.realYield(), source.dxy(), source.wti(),
                            source.gold(), source.volatility(), source.distanceFrom52WeekHigh(), source.distanceFromAth(),
                            dataMode(source.dataMode()), instant(source.capturedAt()), source.provenanceId());
                })
                .toList();
        Map<String, MarketSnapshot> snapshotsById = index(snapshots, MarketSnapshot::id);

        List<ScoringMethodology> methodologies = outcomeData.methodologies().stream()
                .map(source -> new ScoringMethodology(
                        source.methodologyId(), source.methodologyVersion(), source.schemaVersion(),
                        source.definitionHash(), MethodologyStatus.valueOf(source.status()),
                        instant(source.effectiveAt()), dataMode(source.dataMode()), instant(source.capturedAt()),
                        source.provenanceId()))
                .toList();
        Map<MethodologyKey, ScoringMethodology> methodologiesByKey = methodologies.stream()
                .collect(Collectors.toUnmodifiableMap(
                        methodology -> new MethodologyKey(
                                methodology.methodologyId(), methodology.methodologyVersion(),
                                methodology.definitionHash()),
                        Function.identity()));

        List<CallOutcome> outcomes = outcomeData.outcomes().stream()
                .map(source -> {
                    CallOutcome outcome = new CallOutcome(
                            source.outcomeId(), source.schemaVersion(), source.callId(),
                            OutcomeHorizon.valueOf(source.horizon()), source.basisRevisionId(),
                            source.cancellationRevisionId(), source.snapshotId(),
                            source.methodologyId(), source.methodologyVersion(), source.methodologyDefinitionHash(),
                            source.inputFingerprint(), source.sequenceNumber(), source.supersedesOutcomeId(),
                            OutcomeEvaluationStatus.valueOf(source.evaluationStatus()),
                            source.reasonCode() == null ? null : OutcomeReasonCode.valueOf(source.reasonCode()),
                            instant(source.eventTime()), instant(source.processingTime()),
                            source.assetReturn(), source.benchmarkReturn(), source.sectorReturn(), source.alpha(),
                            source.sectorAlpha(), source.mfe(), source.mae(), source.targetHit(),
                            source.directionalWin(), source.targetError(), source.dataComplete(),
                            dataMode(source.dataMode()), instant(source.capturedAt()), source.provenanceId());
                    validateOutcomeReferences(
                            outcome, callsById, revisionsById, snapshotsById, methodologiesByKey);
                    return outcome;
                })
                .toList();

        return new AnalystCallDataSet(
                institutions, analysts, assets, calls, revisions, snapshots, methodologies, outcomes);
    }

    private static void validateOutcomeReferences(
            CallOutcome outcome,
            Map<String, AnalystCall> callsById,
            Map<String, AnalystCallRevision> revisionsById,
            Map<String, MarketSnapshot> snapshotsById,
            Map<MethodologyKey, ScoringMethodology> methodologiesByKey) {
        AnalystCall call = required(callsById, outcome.callId(), "analyst call");
        if (call.processingTime().isAfter(outcome.processingTime())
                || call.capturedAt().isAfter(outcome.processingTime())) {
            throw new IllegalStateException(
                    "Outcome original call must exist by processingTime: " + outcome.outcomeId());
        }
        if (outcome.basisRevisionId() != null) {
            AnalystCallRevision revision = required(
                    revisionsById, outcome.basisRevisionId(), "outcome basis revision");
            if (!revision.callId().equals(outcome.callId())
                    || revision.type() != AnalystCallRevisionType.CORRECTION) {
                throw new IllegalStateException(
                        "Outcome basis must be a correction for the same call: " + outcome.outcomeId());
            }
            if (revision.processingTime().isAfter(outcome.processingTime())
                    || revision.capturedAt().isAfter(outcome.processingTime())) {
                throw new IllegalStateException(
                        "Outcome basis must exist by processingTime: " + outcome.outcomeId());
            }
        }
        if (outcome.cancellationRevisionId() != null) {
            AnalystCallRevision revision = required(
                    revisionsById, outcome.cancellationRevisionId(), "outcome cancellation revision");
            if (!revision.callId().equals(outcome.callId())
                    || revision.type() != AnalystCallRevisionType.CANCELLATION) {
                throw new IllegalStateException(
                        "Outcome cancellation evidence must be a cancellation for the same call: "
                                + outcome.outcomeId());
            }
            if (revision.processingTime().isAfter(outcome.processingTime())
                    || revision.capturedAt().isAfter(outcome.processingTime())) {
                throw new IllegalStateException(
                        "Outcome cancellation evidence must be captured by processingTime: "
                                + outcome.outcomeId());
            }
        }
        if (outcome.snapshotId() != null) {
            MarketSnapshot snapshot = required(snapshotsById, outcome.snapshotId(), "outcome snapshot");
            if (!snapshot.callId().equals(outcome.callId())) {
                throw new IllegalStateException(
                        "Outcome snapshot must belong to the same call: " + outcome.outcomeId());
            }
            if (snapshot.processingTime().isAfter(outcome.processingTime())
                    || snapshot.capturedAt().isAfter(outcome.processingTime())) {
                throw new IllegalStateException(
                        "Outcome snapshot must exist by processingTime: " + outcome.outcomeId());
            }
        }
        ScoringMethodology methodology = required(
                methodologiesByKey,
                new MethodologyKey(
                        outcome.methodologyId(), outcome.methodologyVersion(),
                        outcome.methodologyDefinitionHash()),
                "outcome methodology");
        if (methodology.effectiveAt().isAfter(outcome.processingTime())) {
            throw new IllegalStateException(
                    "Outcome methodology must be effective by processingTime: " + outcome.outcomeId());
        }
        if (methodology.capturedAt().isAfter(outcome.processingTime())) {
            throw new IllegalStateException(
                    "Outcome methodology must be captured by processingTime: " + outcome.outcomeId());
        }
    }

    private static <T> Map<String, T> index(List<T> values, Function<T, String> keyExtractor) {
        return values.stream().collect(Collectors.toUnmodifiableMap(keyExtractor, Function.identity()));
    }

    private static <K, T> T required(Map<K, T> values, K id, String type) {
        T value = values.get(id);
        if (value == null) {
            throw new IllegalStateException("Unknown fixture " + type + ": " + id);
        }
        return value;
    }

    private static <T> T nullable(Map<String, T> values, String id) {
        return id == null ? null : required(values, id, "analyst");
    }

    private static Instant instant(String value) {
        return Instant.parse(value);
    }

    private static Instant nullableInstant(String value) {
        return value == null ? null : instant(value);
    }

    private static URI uri(String value) {
        return value == null ? null : URI.create(value);
    }

    private static LocalDate localDate(String value) {
        return value == null ? null : LocalDate.parse(value);
    }

    private static Currency currency(String value) {
        return value == null ? null : Currency.getInstance(value);
    }

    private static CorrectedCallTerms correctedTerms(CorrectedCallTermsDto source) {
        if (source == null) {
            return null;
        }
        return new CorrectedCallTerms(
                CallDirection.valueOf(source.direction()), source.originalRating(), source.previousTarget(),
                source.target(), currency(source.currency()), localDate(source.targetDate()));
    }

    private static DataMode dataMode(String value) {
        return DataMode.valueOf(value);
    }

    private record MethodologyKey(String id, String version, String definitionHash) {
    }
}
