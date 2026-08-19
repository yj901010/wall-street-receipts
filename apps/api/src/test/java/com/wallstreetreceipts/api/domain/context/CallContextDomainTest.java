package com.wallstreetreceipts.api.domain.context;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wallstreetreceipts.api.domain.market.DataMode;
import com.wallstreetreceipts.api.infrastructure.provider.fixture.FixtureAnalystCallProvider;

class CallContextDomainTest {

    private static final Instant EVENT_TIME = Instant.parse("2026-08-10T12:00:00Z");
    private static final Instant PROCESSING_TIME = Instant.parse("2026-08-10T12:03:00Z");

    @Test
    void fixtureAdapterKeepsNullableValueAndLaterRevisionOutsideEmbeddedSnapshot() {
        var contexts = new FixtureAnalystCallProvider(new ObjectMapper()).load().contexts();

        assertThat(contexts.macroObservations()).hasSize(7);
        assertThat(contexts.macroSnapshots()).singleElement().satisfies(snapshot -> {
            assertThat(snapshot.observations()).hasSize(6);
            assertThat(snapshot.observations().get(4).value()).isNull();
            assertThat(snapshot.observations())
                    .extracting(MacroObservation::macroObservationId)
                    .doesNotContain("macro-observation-demo-cpi-revision-001");
        });
    }

    @Test
    void vintageEndIsInclusiveAtUtcEventDate() {
        List<MacroObservation> observations = observations(EVENT_TIME);
        MacroObservation cpi = observations.get(2);
        observations.set(2, copy(cpi, cpi.releasedAt(), LocalDate.parse("2026-08-10")));

        assertThat(new MacroSnapshot(
                "1.0.0", "snapshot-inclusive", "call-001", EVENT_TIME, PROCESSING_TIME,
                observations, DataMode.DEMO, PROCESSING_TIME, "test-context").observations())
                .hasSize(6);
    }

    @Test
    void releaseAfterEventAndExpiredVintageAreRejected() {
        List<MacroObservation> futureRelease = observations(EVENT_TIME);
        MacroObservation cpi = futureRelease.get(2);
        futureRelease.set(2, copy(cpi, EVENT_TIME.plusSeconds(1), null));

        assertThatThrownBy(() -> snapshot(futureRelease))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("released by snapshot eventTime");

        List<MacroObservation> expired = observations(EVENT_TIME);
        expired.set(2, copy(cpi, cpi.releasedAt(), LocalDate.parse("2026-08-09")));
        assertThatThrownBy(() -> snapshot(expired))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("vintage is not active");
    }

    @Test
    void decimalScaleAndMicrosecondPrecisionAreRejectedInsteadOfRounded() {
        assertThatThrownBy(() -> observation(
                MacroSeries.CPI_YOY, new BigDecimal("1.0000000000001"), EVENT_TIME.minusSeconds(1), null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("scale");
        assertThatThrownBy(() -> new EventContext(
                "1.0.0", "event-context-001", "call-001", EVENT_TIME.plusNanos(1), PROCESSING_TIME,
                null, null, null, null, null, "source-ref-001", DataMode.DEMO,
                PROCESSING_TIME, "test-context"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("microsecond precision");
    }

    @Test
    void onlyEarningsMayPrecedeTheCallEvent() {
        assertThat(new EventContext(
                "1.0.0", "event-context-001", "call-001", EVENT_TIME, PROCESSING_TIME,
                EVENT_TIME.minusSeconds(1), null, null, null, null, "source-ref-001", DataMode.DEMO,
                PROCESSING_TIME, "test-context").earningsAt()).isBefore(EVENT_TIME);
        assertThatThrownBy(() -> new EventContext(
                "1.0.0", "event-context-002", "call-001", EVENT_TIME, PROCESSING_TIME,
                null, EVENT_TIME.minusSeconds(1), null, null, null, "source-ref-001", DataMode.DEMO,
                PROCESSING_TIME, "test-context"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("nextCpiAt");
    }

    private static MacroSnapshot snapshot(List<MacroObservation> observations) {
        return new MacroSnapshot(
                "1.0.0", "snapshot-001", "call-001", EVENT_TIME, PROCESSING_TIME,
                observations, DataMode.DEMO, PROCESSING_TIME, "test-context");
    }

    private static List<MacroObservation> observations(Instant eventTime) {
        return Arrays.stream(MacroSeries.values())
                .map(series -> observation(
                        series, series == MacroSeries.PPI_YOY ? null : BigDecimal.ONE,
                        eventTime.minusSeconds(60), null))
                .collect(java.util.stream.Collectors.toCollection(java.util.ArrayList::new));
    }

    private static MacroObservation observation(
            MacroSeries series,
            BigDecimal value,
            Instant releasedAt,
            LocalDate vintageEnd) {
        return new MacroObservation(
                "1.0.0", "observation-" + series.name().toLowerCase(), series, value, MacroUnit.PERCENT,
                LocalDate.parse("2026-07-01"), releasedAt, releasedAt,
                LocalDate.parse("2026-07-01"), vintageEnd, "source-ref-001", DataMode.DEMO,
                releasedAt, "test-context");
    }

    private static MacroObservation copy(
            MacroObservation source,
            Instant releasedAt,
            LocalDate vintageEnd) {
        return new MacroObservation(
                source.schemaVersion(), source.macroObservationId(), source.series(), source.value(), source.unit(),
                source.observationDate(), releasedAt, releasedAt, source.vintageStart(), vintageEnd,
                source.sourceReferenceId(), source.dataMode(), releasedAt, source.provenanceId());
    }
}
