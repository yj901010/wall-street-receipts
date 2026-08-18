package com.wallstreetreceipts.api.application.call;

import java.util.List;

public record AnalystCallPage(
        List<AnalystCallDetail> items,
        int number,
        int size,
        long totalElements,
        int totalPages,
        boolean first,
        boolean last,
        CallSortField sort,
        SortOrder order) {

    public AnalystCallPage {
        items = List.copyOf(items);
    }
}
