package com.wallstreetreceipts.api.web.call;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import com.wallstreetreceipts.api.domain.call.CallDirection;
import com.wallstreetreceipts.api.domain.call.CallStatus;
import com.wallstreetreceipts.api.domain.market.DataMode;
import com.wallstreetreceipts.api.domain.master.AssetType;
import com.wallstreetreceipts.api.domain.source.SourceType;

public final class AnalystCallResponses {

    private AnalystCallResponses() {
    }

    public record Call(
            String schemaVersion,
            String callId,
            String provider,
            String providerEventId,
            String institutionId,
            String analystId,
            String assetId,
            Instant eventTime,
            Instant processingTime,
            CallDirection direction,
            String originalRating,
            BigDecimal previousTarget,
            BigDecimal target,
            String currency,
            LocalDate targetDate,
            String sourceReferenceId,
            CallStatus status,
            DataMode dataMode,
            Instant capturedAt,
            String provenanceId) {
    }

    public record Institution(String institutionId, String canonicalName, String slug) {
    }

    public record Analyst(String analystId, String canonicalName) {
    }

    public record Asset(String assetId, AssetType assetType, String canonicalName, String ticker) {
    }

    public record SourceDocument(
            String schemaVersion,
            String sourceDocumentId,
            SourceType sourceType,
            String publisher,
            String title,
            String canonicalUrl,
            Instant publishedAt,
            String provider,
            String externalId,
            String contentHash,
            String licenseClass,
            DataMode dataMode,
            Instant capturedAt,
            String provenanceId) {
    }

    public record SourceReference(
            String schemaVersion,
            String sourceReferenceId,
            String sourceDocumentId,
            Integer page,
            Long startMs,
            Long endMs,
            String extractedFragment,
            BigDecimal extractionConfidence,
            boolean verified,
            DataMode dataMode,
            Instant capturedAt,
            String provenanceId) {
    }

    public record Source(SourceDocument document, SourceReference reference) {
    }

    public record Item(Call call, Institution institution, Analyst analyst, Asset asset, Source source) {
    }

    public record Snapshot(
            String schemaVersion,
            String snapshotId,
            String callId,
            String assetId,
            Instant eventTime,
            Instant processingTime,
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
            DataMode dataMode,
            Instant capturedAt,
            String provenanceId) {
    }

    public record Detail(
            Call call,
            Institution institution,
            Analyst analyst,
            Asset asset,
            Source source,
            Snapshot snapshot) {
    }

    public record Sort(String field, String order) {
    }

    public record PageMetadata(
            int number,
            int size,
            long totalElements,
            int totalPages,
            boolean first,
            boolean last,
            Sort sort) {
    }

    public record Page(List<Item> items, PageMetadata page) {

        public Page {
            items = List.copyOf(items);
        }
    }
}
