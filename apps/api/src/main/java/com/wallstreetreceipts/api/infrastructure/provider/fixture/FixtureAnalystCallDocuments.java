package com.wallstreetreceipts.api.infrastructure.provider.fixture;

import java.math.BigDecimal;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

final class FixtureAnalystCallDocuments {

    private FixtureAnalystCallDocuments() {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record MasterDataDocument(
            List<InstitutionDto> institutions,
            List<AnalystDto> analysts,
            List<AssetDto> assets) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record AnalystCallsDocument(
            List<SourceDocumentDto> sourceDocuments,
            List<SourceReferenceDto> sourceReferences,
            List<AnalystCallDto> calls) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record AnalystCallRevisionsDocument(List<AnalystCallRevisionDto> revisions) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record MarketSnapshotsDocument(List<MarketSnapshotDto> snapshots) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record CallOutcomesDocument(
            List<ScoringMethodologyDto> methodologies,
            List<CallOutcomeDto> outcomes) {
    }

    record InstitutionDto(
            String institutionId,
            String canonicalName,
            String slug,
            String country,
            boolean active,
            String dataMode,
            String effectiveAt,
            String capturedAt,
            String provenanceId) {
    }

    record AnalystDto(
            String analystId,
            String canonicalName,
            boolean active,
            String dataMode,
            String effectiveAt,
            String capturedAt,
            String provenanceId) {
    }

    record AssetDto(
            String assetId,
            String assetType,
            String canonicalName,
            String ticker,
            String primaryCurrency,
            boolean active,
            String dataMode,
            String effectiveAt,
            String capturedAt,
            String provenanceId) {
    }

    record SourceDocumentDto(
            String sourceDocumentId,
            String sourceType,
            String publisher,
            String title,
            String canonicalUrl,
            String publishedAt,
            String provider,
            String externalId,
            String contentHash,
            String licenseClass,
            String dataMode,
            String capturedAt,
            String provenanceId) {
    }

    record SourceReferenceDto(
            String sourceReferenceId,
            String sourceDocumentId,
            Integer page,
            Long startMs,
            Long endMs,
            String extractedFragment,
            BigDecimal extractionConfidence,
            boolean verified,
            String dataMode,
            String capturedAt,
            String provenanceId) {
    }

    record AnalystCallDto(
            String callId,
            String provider,
            String providerEventId,
            String institutionId,
            String analystId,
            String assetId,
            String eventTime,
            String processingTime,
            String direction,
            String originalRating,
            BigDecimal previousTarget,
            BigDecimal target,
            String currency,
            String targetDate,
            String sourceReferenceId,
            String status,
            String dataMode,
            String capturedAt,
            String provenanceId) {
    }

    record AnalystCallRevisionDto(
            String revisionId,
            String schemaVersion,
            String callId,
            String supersedesRevisionId,
            int sequenceNumber,
            String provider,
            String providerEventId,
            String revisionType,
            String eventTime,
            String processingTime,
            CorrectedCallTermsDto correctedTerms,
            String reason,
            String sourceReferenceId,
            String dataMode,
            String capturedAt,
            String provenanceId) {
    }

    record CorrectedCallTermsDto(
            String direction,
            String originalRating,
            BigDecimal previousTarget,
            BigDecimal target,
            String currency,
            String targetDate) {
    }

    record MarketSnapshotDto(
            String snapshotId,
            String callId,
            String assetId,
            String eventTime,
            String processingTime,
            BigDecimal assetPrice,
            BigDecimal spx,
            BigDecimal ndx,
            BigDecimal vix,
            BigDecimal treasury2y,
            BigDecimal treasury10y,
            BigDecimal realYield,
            BigDecimal dxy,
            BigDecimal wti,
            BigDecimal gold,
            BigDecimal volatility,
            BigDecimal distanceFrom52WeekHigh,
            BigDecimal distanceFromAth,
            boolean immutable,
            String dataMode,
            String capturedAt,
            String provenanceId) {
    }

    record ScoringMethodologyDto(
            String methodologyId,
            String methodologyVersion,
            String schemaVersion,
            String definitionHash,
            String status,
            String effectiveAt,
            String dataMode,
            String capturedAt,
            String provenanceId) {
    }

    record CallOutcomeDto(
            String outcomeId,
            String schemaVersion,
            String callId,
            String horizon,
            String basisRevisionId,
            String cancellationRevisionId,
            String snapshotId,
            String methodologyId,
            String methodologyVersion,
            String methodologyDefinitionHash,
            String inputFingerprint,
            int sequenceNumber,
            String supersedesOutcomeId,
            String evaluationStatus,
            String reasonCode,
            String eventTime,
            String processingTime,
            BigDecimal assetReturn,
            BigDecimal benchmarkReturn,
            BigDecimal sectorReturn,
            BigDecimal alpha,
            BigDecimal sectorAlpha,
            BigDecimal mfe,
            BigDecimal mae,
            Boolean targetHit,
            Boolean directionalWin,
            BigDecimal targetError,
            boolean dataComplete,
            String dataMode,
            String capturedAt,
            String provenanceId) {
    }
}
