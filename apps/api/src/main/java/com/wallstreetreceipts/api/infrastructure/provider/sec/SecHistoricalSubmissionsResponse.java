package com.wallstreetreceipts.api.infrastructure.provider.sec;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/** Vendor DTO for one SEC historical submissions segment. */
@JsonIgnoreProperties(ignoreUnknown = true)
record SecHistoricalSubmissionsResponse(
        List<String> accessionNumber,
        List<String> filingDate,
        List<String> reportDate,
        List<String> acceptanceDateTime,
        List<String> act,
        List<String> form,
        List<String> fileNumber,
        List<String> filmNumber,
        List<String> items,
        List<Long> size,
        List<Integer> isXBRL,
        List<Integer> isInlineXBRL,
        List<String> primaryDocument,
        List<String> primaryDocDescription) {
}
