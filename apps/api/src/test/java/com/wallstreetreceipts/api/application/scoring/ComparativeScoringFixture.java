package com.wallstreetreceipts.api.application.scoring;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import com.wallstreetreceipts.api.domain.master.AssetType;
import com.wallstreetreceipts.api.domain.outcome.benchmarkassignment.*;
import com.wallstreetreceipts.api.domain.outcome.benchmarkreferencepair.*;
import com.wallstreetreceipts.api.domain.outcome.sectorassignment.*;
import com.wallstreetreceipts.api.domain.outcome.sectorreferencepair.*;
import static com.wallstreetreceipts.api.application.scoring.EndpointScoringFixture.*;

/** Synthetic request members, not source-local preconstructed result receipts or runtime fixtures. */
final class ComparativeScoringFixture {
    static ComparativeScoringInput input() { return input(EndpointScoringFixture.input()); }
    static ComparativeScoringInput input(EndpointScoringInput endpoint) {
        return new ComparativeScoringInput(endpoint, benchmark(endpoint), sector(endpoint));
    }
    static ComparativeScoringInput.Benchmark benchmark(EndpointScoringInput e) {
        var basis = e.horizon().basis();
        var interval = new BenchmarkAssetClassificationEvidence.EffectiveInterval(BASIS.minusSeconds(1),
                new BenchmarkAssetClassificationEvidence.OpenEnded());
        var classification = new BenchmarkAssetClassificationEvidence("demo-b-class", "demo-b-class-event",
                basis, e.binding().assetId(), AssetType.EQUITY, e.binding().primaryVenueId(), "US", USD,
                "demo-class-source", "r1", "demo-b-class-provenance", interval, BASIS, BASIS);
        var assignment = new BenchmarkAssignmentEvidence("demo-b-assignment", "demo-b-assignment-event",
                basis, e.binding().assetId(), AssetType.EQUITY, e.binding().primaryVenueId(), "US", USD,
                "demo-assignment-source", "r1", "demo-b-assignment-provenance", interval, "asset-spx", AssetType.INDEX, USD,
                BenchmarkAssignmentEvidence.BenchmarkReferenceKind.PROVIDER_PUBLISHED_PRICE_INDEX, BASIS, BASIS);
        var request = new BenchmarkAssignmentRequest(ComparativeScoringMethodology.BENCHMARK_ASSIGNMENT,
                basis, e.binding().assetId(), e.evaluationAsOf(), List.of(classification), List.of(assignment));
        var reference = new BenchmarkReferenceIndexEvidence("demo-b-ref", "demo-b-ref-event",
                assignment.assignmentEvidenceId(), assignment.providerEventId(), "asset-spx", AssetType.INDEX,
                "demo-b-provider", "demo-b-index", "DEMO benchmark price index", "r1",
                BenchmarkReferenceIndexEvidence.ReferenceIndexKind.PROVIDER_PUBLISHED_PRICE_INDEX, USD,
                "demo-b-venue", "demo-b-calendar", "r1", "demo-b-calendar-source", "r1",
                "demo-b-levels", "r1", "demo-b-continuity", "r1", "demo-b-binding", "r1", "demo-b-ref-provenance",
                new BenchmarkReferenceIndexEvidence.EffectiveInterval(BASIS, new BenchmarkReferenceIndexEvidence.OpenEnded()),
                BASIS, BASIS);
        var start = benchmarkLevel("demo-b-start", reference, BASIS, "4000");
        var end = benchmarkLevel("demo-b-end", reference, CLOSE, "4400");
        return new ComparativeScoringInput.Benchmark(request, List.of(reference), List.of(start), List.of(end),
                List.of(benchmarkContinuity(reference, start, end)));
    }
    static ComparativeScoringInput.Sector sector(EndpointScoringInput e) {
        var basis = e.horizon().basis();
        var interval = new SectorAssetClassificationEvidence.EffectiveInterval(BASIS.minusSeconds(1),
                new SectorAssetClassificationEvidence.OpenEnded());
        var classification = new SectorAssetClassificationEvidence("demo-s-class", "demo-s-class-event",
                basis, e.binding().assetId(), AssetType.EQUITY, e.binding().primaryVenueId(), "US", USD,
                "demo-class-source", "r1", "demo-s-class-provenance", interval, BASIS, BASIS);
        var membership = new SectorMembershipEvidence("demo-s-member", "demo-s-member-event", basis,
                e.binding().assetId(), AssetType.EQUITY, e.binding().primaryVenueId(), "US", USD,
                "demo-sector-provider", "demo-scheme", "r1", "demo-node", "DEMO node",
                "demo-membership-source", "r1", "demo-s-member-provenance", interval, BASIS, BASIS);
        var mapping = new SectorMappingEvidence("demo-s-map", "demo-s-map-event",
                "POINT_IN_TIME_EXPLICIT_PROVIDER_NODE_TO_WSR_ECONOMIC_ACTIVITY_V1",
                "ba12a277d5ffe266af1745b98948a1e2206494ac31904f31a419d973d5067e77",
                "demo-mapping-set", "1.0.0", "a".repeat(64), "wsr-economic-activity", "1.0.0",
                "820ce3ea264d67312fe4f2efe346631a81d74248e9a7f041793d65d8ef0d62ae",
                membership.providerId(), membership.providerSchemeId(), membership.providerSchemeRevision(),
                membership.providerNodeId(), "DEMO node", new SectorMappingEvidence.Recorded("Synthetic definition", "en"),
                new SectorMappingEvidence.Mapped("wsr-sector-digital-systems"), "demo-mapping-source", "r1",
                "demo-s-map-provenance", interval, BASIS, BASIS);
        var request = new SectorAssignmentRequest(ComparativeScoringMethodology.SECTOR_ASSIGNMENT, basis,
                e.binding().assetId(), e.evaluationAsOf(), mapping.mappingSetId(), mapping.mappingSetVersion(),
                mapping.mappingSetDefinitionHash(), List.of(classification), List.of(membership), List.of(mapping));
        var reference = new SectorReferenceIndexEvidence("demo-s-ref", "demo-s-ref-event",
                mapping.mappingEvidenceId(), mapping.providerEventId(), mapping.taxonomyId(), mapping.taxonomyVersion(),
                mapping.taxonomyDefinitionHash(), ((SectorMappingEvidence.Mapped) mapping.mappingDisposition()).canonicalNodeId(),
                "demo-sector-index-asset", AssetType.INDEX, "demo-s-provider", "demo-s-index", "DEMO sector price index", "r1",
                SectorReferenceIndexEvidence.ReferenceIndexKind.PROVIDER_PUBLISHED_PRICE_INDEX, USD,
                "demo-s-venue", "demo-s-calendar", "r1", "demo-s-calendar-source", "r1",
                "demo-s-levels", "r1", "demo-s-continuity", "r1", "demo-s-binding", "r1", "demo-s-ref-provenance",
                new SectorReferenceIndexEvidence.EffectiveInterval(BASIS, new SectorReferenceIndexEvidence.OpenEnded()), BASIS, BASIS);
        var start = sectorLevel("demo-s-start", reference, BASIS, "2000");
        var end = sectorLevel("demo-s-end", reference, CLOSE, "1900");
        return new ComparativeScoringInput.Sector(request, List.of(reference), List.of(start), List.of(end),
                List.of(sectorContinuity(reference, start, end)));
    }

    static BenchmarkReferenceLevelObservation benchmarkLevel(String id, BenchmarkReferenceIndexEvidence r, Instant time, String value) {
        return new BenchmarkReferenceLevelObservation(id, id + "-event", r.referenceIndexEvidenceId(), r.providerEventId(),
                r.benchmarkAssetId(), r.benchmarkAssetType(), r.referenceProviderId(), r.referenceIndexId(),
                r.referenceIndexDefinitionRevision(), r.referenceKind(), r.currency(), r.calculationVenueId(),
                r.calendarId(), r.calendarRevision(), r.calendarSourceId(), r.calendarSourceRevision(),
                r.levelSourceId(), r.levelSourceRevision(), id + "-provenance",
                BenchmarkReferenceLevelObservation.ReferenceLevelField.PROVIDER_PUBLISHED_INDEX_LEVEL,
                time, time, time, new BigDecimal(value));
    }
    static BenchmarkIndexDivisorContinuityEvidence benchmarkContinuity(BenchmarkReferenceIndexEvidence r,
            BenchmarkReferenceLevelObservation start, BenchmarkReferenceLevelObservation end) {
        return new BenchmarkIndexDivisorContinuityEvidence("demo-benchmark-continuity", "demo-benchmark-continuity-event",
                r.referenceIndexEvidenceId(), r.providerEventId(), r.benchmarkAssetId(), r.benchmarkAssetType(),
                r.referenceProviderId(), r.referenceIndexId(), r.referenceIndexDefinitionRevision(), r.referenceKind(),
                r.currency(), r.calculationVenueId(), r.calendarId(), r.calendarRevision(), r.calendarSourceId(),
                r.calendarSourceRevision(), r.continuitySourceId(), r.continuitySourceRevision(), "demo-benchmark-continuity-provenance",
                start.observationId(), start.providerEventId(), end.observationId(), end.providerEventId(),
                start.observedAt(), end.observedAt(),
                BenchmarkIndexDivisorContinuityEvidence.DivisorContinuity.PROVIDER_PUBLISHED_INDEX_DIVISOR_CONTINUITY_ATTESTED,
                end.observedAt(), end.observedAt());
    }

    static SectorReferenceLevelObservation sectorLevel(String id, SectorReferenceIndexEvidence r, Instant time, String value) {
        return new SectorReferenceLevelObservation(id, id + "-event", r.referenceIndexEvidenceId(), r.providerEventId(),
                r.referenceAssetId(), r.referenceAssetType(), r.referenceProviderId(), r.referenceIndexId(),
                r.referenceIndexDefinitionRevision(), r.referenceKind(), r.currency(), r.calculationVenueId(),
                r.calendarId(), r.calendarRevision(), r.calendarSourceId(), r.calendarSourceRevision(),
                r.levelSourceId(), r.levelSourceRevision(), id + "-provenance",
                SectorReferenceLevelObservation.ReferenceLevelField.PROVIDER_PUBLISHED_INDEX_LEVEL,
                time, time, time, new BigDecimal(value));
    }
    static SectorIndexDivisorContinuityEvidence sectorContinuity(SectorReferenceIndexEvidence r,
            SectorReferenceLevelObservation start, SectorReferenceLevelObservation end) {
        return new SectorIndexDivisorContinuityEvidence("demo-sector-continuity", "demo-sector-continuity-event",
                r.referenceIndexEvidenceId(), r.providerEventId(), r.referenceAssetId(), r.referenceAssetType(),
                r.referenceProviderId(), r.referenceIndexId(), r.referenceIndexDefinitionRevision(), r.referenceKind(),
                r.currency(), r.calculationVenueId(), r.calendarId(), r.calendarRevision(), r.calendarSourceId(),
                r.calendarSourceRevision(), r.continuitySourceId(), r.continuitySourceRevision(), "demo-sector-continuity-provenance",
                start.observationId(), start.providerEventId(), end.observationId(), end.providerEventId(),
                start.observedAt(), end.observedAt(),
                SectorIndexDivisorContinuityEvidence.DivisorContinuity.PROVIDER_PUBLISHED_INDEX_DIVISOR_CONTINUITY_ATTESTED,
                end.observedAt(), end.observedAt());
    }
}
