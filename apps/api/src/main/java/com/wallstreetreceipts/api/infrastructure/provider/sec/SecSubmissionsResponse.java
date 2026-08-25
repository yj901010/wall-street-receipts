package com.wallstreetreceipts.api.infrastructure.provider.sec;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;

/** Vendor DTO matching the SEC submissions response; it does not cross the adapter boundary. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record SecSubmissionsResponse(
        @JsonDeserialize(using = SecStringCikDeserializer.class) String cik,
        SecFilings filings) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record SecFilings(
            SecRecentFilings recent) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record SecRecentFilings(
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
}
