package com.wallstreetreceipts.api.domain.filing;

import java.time.LocalDate;
import java.util.Objects;
import java.util.regex.Pattern;

/** Provider-advertised historical segment metadata; the segment is not fetched. */
public record HistoricalFilingSegmentDescriptor(
        String fileName,
        long advertisedFilingCount,
        LocalDate advertisedFilingFrom,
        LocalDate advertisedFilingTo) {

    private static final int MAX_FILE_NAME_LENGTH = 128;
    private static final Pattern SAFE_FILE_NAME =
            Pattern.compile("[A-Za-z0-9][A-Za-z0-9._-]*");

    public HistoricalFilingSegmentDescriptor {
        FilingRecord.requireCanonicalText(fileName, "fileName");
        if (fileName.length() > MAX_FILE_NAME_LENGTH
                || !SAFE_FILE_NAME.matcher(fileName).matches()) {
            throw new IllegalArgumentException(
                    "fileName must be a bounded canonical path segment");
        }
        if (advertisedFilingCount <= 0) {
            throw new IllegalArgumentException("advertisedFilingCount must be positive");
        }
        Objects.requireNonNull(
                advertisedFilingFrom, "advertisedFilingFrom must not be null");
        Objects.requireNonNull(
                advertisedFilingTo, "advertisedFilingTo must not be null");
        if (advertisedFilingTo.isBefore(advertisedFilingFrom)) {
            throw new IllegalArgumentException(
                    "advertisedFilingTo must not precede advertisedFilingFrom");
        }
    }

    public boolean containsAdvertisedDate(LocalDate date) {
        Objects.requireNonNull(date, "date must not be null");
        return !date.isBefore(advertisedFilingFrom) && !date.isAfter(advertisedFilingTo);
    }

    public boolean overlapsAdvertisedDateRange(HistoricalFilingSegmentDescriptor other) {
        Objects.requireNonNull(other, "other must not be null");
        return !advertisedFilingTo.isBefore(other.advertisedFilingFrom)
                && !other.advertisedFilingTo.isBefore(advertisedFilingFrom);
    }
}
