package com.wallstreetreceipts.api.application.call;

import java.time.Instant;

import com.wallstreetreceipts.api.domain.call.CallDirection;
import com.wallstreetreceipts.api.domain.call.CallStatus;
import com.wallstreetreceipts.api.domain.market.DataMode;

public record AnalystCallFilter(
        String assetId,
        String ticker,
        String institutionId,
        String analystId,
        CallDirection direction,
        CallStatus status,
        DataMode dataMode,
        Instant from,
        Instant to,
        int page,
        int size,
        CallSortField sort,
        SortOrder order) {

    public AnalystCallFilter {
        if (page < 0) {
            throw new IllegalArgumentException("page must be zero or greater");
        }
        if (size < 1 || size > 100) {
            throw new IllegalArgumentException("size must be between 1 and 100");
        }
        if (from != null && to != null && !from.isBefore(to)) {
            throw new IllegalArgumentException("to must be later than from");
        }
        if (sort == null || order == null) {
            throw new IllegalArgumentException("sort and order must not be null");
        }
    }
}
