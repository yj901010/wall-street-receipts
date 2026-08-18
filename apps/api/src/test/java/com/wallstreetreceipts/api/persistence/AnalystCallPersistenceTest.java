package com.wallstreetreceipts.api.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.lang.reflect.Modifier;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.ClassPathResource;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import com.wallstreetreceipts.api.application.port.out.AnalystCallDataSet;
import com.wallstreetreceipts.api.application.port.out.AnalystCallProvider;
import com.wallstreetreceipts.api.application.port.out.AnalystCallRepository;
import com.wallstreetreceipts.api.domain.call.AnalystCall;
import com.wallstreetreceipts.api.domain.market.MarketSnapshot;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class AnalystCallPersistenceTest {

    @Autowired
    private AnalystCallProvider provider;

    @Autowired
    private AnalystCallRepository repository;

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void rootFixturesArePackagedAndMappedToCanonicalModels() {
        assertThat(new ClassPathResource("fixtures/v1/master-data.json").exists()).isTrue();
        assertThat(new ClassPathResource("fixtures/v1/analyst-calls.json").exists()).isTrue();
        assertThat(new ClassPathResource("fixtures/v1/analyst-call-revisions.json").exists()).isTrue();
        assertThat(new ClassPathResource("fixtures/v1/market-snapshots.json").exists()).isTrue();

        AnalystCallDataSet dataSet = provider.load();
        assertThat(dataSet.institutions()).hasSize(2);
        assertThat(dataSet.analysts()).hasSize(2);
        assertThat(dataSet.assets()).hasSize(4);
        assertThat(dataSet.calls()).hasSize(2);
        assertThat(dataSet.revisions()).hasSize(2);
        assertThat(dataSet.snapshots()).hasSize(2);
        assertThat(dataSet.calls()).allMatch(call -> call.target() == null || call.target().scale() >= 1);
    }

    @Test
    void duplicateProviderEventIsIdempotentEvenWithAnotherCanonicalId() {
        AnalystCall original = provider.load().calls().getFirst();
        AnalystCall duplicate = copyWithId(original, "another-call-id");
        long before = repository.count();

        assertThat(repository.saveIfAbsent(duplicate, null)).isFalse();
        assertThat(repository.count()).isEqualTo(before);
        assertThat(repository.findById("another-call-id")).isEmpty();
    }

    @Test
    void sourceReferenceRemainsTraceableToItsDocumentAndProvenance() {
        AnalystCall call = repository.findById("demo-call-002").orElseThrow().call();

        assertThat(call.sourceReference().id()).isEqualTo("source-ref-demo-002");
        assertThat(call.sourceReference().document().id()).isEqualTo("source-demo-video-002");
        assertThat(call.sourceReference().document().canonicalUrl().toString())
                .isEqualTo("https://example.invalid/demo-call-002");
        assertThat(call.sourceReference().provenanceId()).isEqualTo("fixture-analyst-calls-v1");
    }

    @Test
    void snapshotIsARecordAndRepositoryExposesNoMutationOperation() {
        assertThat(MarketSnapshot.class.isRecord()).isTrue();
        assertThat(Arrays.stream(MarketSnapshot.class.getDeclaredFields())
                .filter(field -> !field.isSynthetic())
                .allMatch(field -> Modifier.isPrivate(field.getModifiers()) && Modifier.isFinal(field.getModifiers())))
                .isTrue();
        assertThat(Arrays.stream(AnalystCallRepository.class.getMethods())
                .map(java.lang.reflect.Method::getName)
                .noneMatch(name -> name.startsWith("updateSnapshot") || name.startsWith("deleteSnapshot")))
                .isTrue();
    }

    @Test
    void snapshotCannotBeMarkedMutableOrReplacedForTheSameCall() {
        assertThatThrownBy(() -> jdbc.update(
                "UPDATE market_snapshots SET immutable = FALSE WHERE snapshot_id = ?",
                "market-snapshot-demo-002"))
                .isInstanceOf(DataIntegrityViolationException.class);

        assertThatThrownBy(() -> jdbc.update("""
                INSERT INTO market_snapshots (
                    snapshot_id, call_id, asset_id, event_time, processing_time, asset_price,
                    spx, ndx, vix, treasury_2y, treasury_10y, real_yield, dxy, wti, gold,
                    volatility, distance_from_52w_high, distance_from_ath, immutable,
                    data_mode, captured_at, provenance_id
                )
                SELECT
                    'replacement-snapshot', call_id, asset_id, event_time, processing_time, asset_price,
                    spx, ndx, vix, treasury_2y, treasury_10y, real_yield, dxy, wti, gold,
                    volatility, distance_from_52w_high, distance_from_ath, TRUE,
                    data_mode, captured_at, provenance_id
                FROM market_snapshots WHERE snapshot_id = 'market-snapshot-demo-002'
                """))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void snapshotEventTimeMustMatchTheCall() {
        AnalystCall call = provider.load().calls().getFirst();
        MarketSnapshot original = provider.load().snapshots().stream()
                .filter(snapshot -> snapshot.callId().equals(call.id()))
                .findFirst()
                .orElseThrow();
        MarketSnapshot mismatched = new MarketSnapshot(
                "mismatched-snapshot", original.callId(), original.assetId(),
                original.eventTime().plus(1, ChronoUnit.SECONDS), original.processingTime(), original.assetPrice(),
                original.spx(), original.ndx(), original.vix(), original.treasury2y(), original.treasury10y(),
                original.realYield(), original.dxy(), original.wti(), original.gold(), original.volatility(),
                original.distanceFrom52WeekHigh(), original.distanceFromAth(), original.dataMode(),
                original.capturedAt(), original.provenanceId());

        assertThatThrownBy(() -> repository.saveIfAbsent(call, mismatched))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("eventTime");
    }

    @Test
    void schemaAllowsMissingAssetPriceWithoutInventingZero() {
        String nullable = jdbc.queryForObject("""
                SELECT is_nullable
                FROM information_schema.columns
                WHERE table_name = 'market_snapshots' AND column_name = 'asset_price'
                """, String.class);

        assertThat(nullable).isEqualToIgnoringCase("YES");
        MarketSnapshot fixture = provider.load().snapshots().getFirst();
        MarketSnapshot incomplete = new MarketSnapshot(
                "incomplete-snapshot", "incomplete-call", fixture.assetId(), fixture.eventTime(),
                fixture.processingTime(), null, null, null, null, null, null, null, null, null, null, null,
                null, null, fixture.dataMode(), fixture.capturedAt(), fixture.provenanceId());
        assertThat(incomplete.assetPrice()).isNull();
    }

    @Test
    void callAndSnapshotCaptureCannotPrecedeTheirProcessingTime() {
        AnalystCall call = provider.load().calls().getFirst();
        assertThatThrownBy(() -> new AnalystCall(
                "capture-before-processing-call", call.provider(), "capture-before-processing-event",
                call.institution(), call.analyst(), call.asset(), call.eventTime(), call.processingTime(),
                call.direction(), call.originalRating(), call.previousTarget(), call.target(), call.currency(),
                call.targetDate(), call.sourceReference(), call.status(), call.dataMode(),
                call.processingTime().minusSeconds(1), call.provenanceId()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("capturedAt");

        MarketSnapshot snapshot = provider.load().snapshots().getFirst();
        assertThatThrownBy(() -> new MarketSnapshot(
                "capture-before-processing-snapshot", snapshot.callId(), snapshot.assetId(), snapshot.eventTime(),
                snapshot.processingTime(), snapshot.assetPrice(), snapshot.spx(), snapshot.ndx(), snapshot.vix(),
                snapshot.treasury2y(), snapshot.treasury10y(), snapshot.realYield(), snapshot.dxy(), snapshot.wti(),
                snapshot.gold(), snapshot.volatility(), snapshot.distanceFrom52WeekHigh(), snapshot.distanceFromAth(),
                snapshot.dataMode(), snapshot.processingTime().minusSeconds(1), snapshot.provenanceId()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("capturedAt");
    }

    private static AnalystCall copyWithId(AnalystCall source, String id) {
        return new AnalystCall(
                id, source.provider(), source.providerEventId(), source.institution(), source.analyst(), source.asset(),
                source.eventTime(), source.processingTime(), source.direction(), source.originalRating(),
                source.previousTarget(), source.target(), source.currency(), source.targetDate(),
                source.sourceReference(), source.status(), source.dataMode(), source.capturedAt(), source.provenanceId());
    }
}
