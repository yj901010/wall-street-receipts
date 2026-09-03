package com.wallstreetreceipts.api.domain.filing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.Test;

class HistoricalFilingSegmentDescriptorTest {

    private static final LocalDate FROM = LocalDate.parse("2020-01-01");
    private static final LocalDate TO = LocalDate.parse("2020-12-31");

    @Test
    void preservesAdvertisedMetadataAndTreatsDateBoundariesAsInclusive() {
        HistoricalFilingSegmentDescriptor descriptor = descriptor(FROM, TO);

        assertThat(descriptor.fileName())
                .isEqualTo("CIK0000320193-submissions-001.json");
        assertThat(descriptor.advertisedFilingCount()).isEqualTo(2_000);
        assertThat(descriptor.advertisedFilingFrom()).isEqualTo(FROM);
        assertThat(descriptor.advertisedFilingTo()).isEqualTo(TO);
        assertThat(descriptor.containsAdvertisedDate(FROM)).isTrue();
        assertThat(descriptor.containsAdvertisedDate(TO)).isTrue();
        assertThat(descriptor.containsAdvertisedDate(FROM.minusDays(1))).isFalse();
        assertThat(descriptor.containsAdvertisedDate(TO.plusDays(1))).isFalse();
    }

    @Test
    void detectsInclusiveRangeOverlapWithoutInferringSegmentContents() {
        HistoricalFilingSegmentDescriptor descriptor = descriptor(FROM, TO);
        HistoricalFilingSegmentDescriptor touchesBoundary = new HistoricalFilingSegmentDescriptor(
                "CIK0000320193-submissions-002.json",
                1,
                TO,
                TO.plusYears(1));
        HistoricalFilingSegmentDescriptor disjoint = new HistoricalFilingSegmentDescriptor(
                "CIK0000320193-submissions-003.json",
                1,
                TO.plusDays(1),
                TO.plusYears(1));

        assertThat(descriptor.overlapsAdvertisedDateRange(touchesBoundary)).isTrue();
        assertThat(touchesBoundary.overlapsAdvertisedDateRange(descriptor)).isTrue();
        assertThat(descriptor.overlapsAdvertisedDateRange(disjoint)).isFalse();
        assertThatThrownBy(() -> descriptor.overlapsAdvertisedDateRange(null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("other must not be null");
        assertThatThrownBy(() -> descriptor.containsAdvertisedDate(null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("date must not be null");
    }

    @Test
    void acceptsASingleDayAdvertisedRange() {
        HistoricalFilingSegmentDescriptor descriptor = descriptor(FROM, FROM);

        assertThat(descriptor.containsAdvertisedDate(FROM)).isTrue();
    }

    @Test
    void rejectsMissingBlankUntrimmedUnsafeAndUnboundedFileNames() {
        assertThatThrownBy(() -> new HistoricalFilingSegmentDescriptor(
                null, 1, FROM, TO))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("fileName must not be null");

        for (String fileName : List.of(
                "",
                " ",
                " CIK0000320193-submissions-001.json",
                "CIK0000320193-submissions-001.json ")) {
            assertThatThrownBy(() -> new HistoricalFilingSegmentDescriptor(
                    fileName, 1, FROM, TO))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("fileName must be nonblank and trimmed");
        }

        for (String fileName : List.of(
                "../CIK0000320193-submissions-001.json",
                "dir/CIK0000320193-submissions-001.json",
                "dir\\CIK0000320193-submissions-001.json",
                "CIK0000320193-submissions-001.json?x=1",
                "CIK0000320193-submissions-001.json#fragment",
                "%2e%2e-CIK0000320193-submissions-001.json",
                "A".repeat(129))) {
            assertThatThrownBy(() -> new HistoricalFilingSegmentDescriptor(
                    fileName, 1, FROM, TO))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("fileName must be a bounded canonical path segment");
        }
    }

    @Test
    void rejectsNonPositiveCountsMissingDatesAndReversedRanges() {
        for (long count : List.of(0L, -1L, Long.MIN_VALUE)) {
            assertThatThrownBy(() -> new HistoricalFilingSegmentDescriptor(
                    "CIK0000320193-submissions-001.json", count, FROM, TO))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("advertisedFilingCount must be positive");
        }

        assertThatThrownBy(() -> new HistoricalFilingSegmentDescriptor(
                "CIK0000320193-submissions-001.json", 1, null, TO))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("advertisedFilingFrom must not be null");
        assertThatThrownBy(() -> new HistoricalFilingSegmentDescriptor(
                "CIK0000320193-submissions-001.json", 1, FROM, null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("advertisedFilingTo must not be null");
        assertThatThrownBy(() -> new HistoricalFilingSegmentDescriptor(
                "CIK0000320193-submissions-001.json", 1, TO, FROM))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("advertisedFilingTo must not precede advertisedFilingFrom");
    }

    private static HistoricalFilingSegmentDescriptor descriptor(
            LocalDate filingFrom,
            LocalDate filingTo) {
        return new HistoricalFilingSegmentDescriptor(
                "CIK0000320193-submissions-001.json",
                2_000,
                filingFrom,
                filingTo);
    }
}
