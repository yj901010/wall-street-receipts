package com.wallstreetreceipts.api.domain.cpi;

import static org.assertj.core.api.Assertions.*;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import com.wallstreetreceipts.api.domain.cpi.CpiCollectionAttempt.*;

class CpiCollectionAttemptTest {
    private static final Instant AT = Instant.parse("2026-09-08T14:00:00.123456Z");

    @Test void incompleteEvidenceAndTypedResultsNeverInventFields() {
        var id = UUID.randomUUID();
        assertThat(new CpiCollectionAttempt(id, Trigger.MANUAL, AT, true, null).result()).isNull();
        for (var result : new Result[]{
                new Result(AT, Status.SAVED, UUID.randomUUID(), AT, null, null),
                new Result(AT, Status.FAILED, null, null, Failure.FETCH, null),
                new Result(AT, Status.FAILED, null, null, Failure.PARSE, null),
                new Result(AT, Status.RATE_LIMITED, null, null, null, AT.plusSeconds(86400))}) {
            assertThat(new CpiCollectionAttempt(id, Trigger.SCHEDULED, AT, true, result).result()).isEqualTo(result);
        }
        var skip = new Result(AT, Status.SKIPPED, null, null, null, null);
        assertThat(new CpiCollectionAttempt(id, Trigger.MANUAL, AT, false, skip).result()).isEqualTo(skip);
        assertThatThrownBy(() -> new CpiCollectionAttempt(id, Trigger.MANUAL, AT, true, skip)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test void rejectsImpossibleShapesAndSilentPrecisionLoss() {
        assertThatThrownBy(() -> new Result(AT, Status.SAVED, null, null, null, null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new Result(AT, Status.FAILED, null, null, null, null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new Result(AT, Status.RATE_LIMITED, null, null, Failure.FETCH, AT)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new Result(AT, Status.SKIPPED, UUID.randomUUID(), AT, null, null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new Result(AT, Status.SAVED, UUID.randomUUID(), AT.plusSeconds(1), null, null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new Result(AT.plusNanos(1), Status.SKIPPED, null, null, null, null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new CpiCollectionAttempt(UUID.randomUUID(), Trigger.MANUAL, AT.plusNanos(1), true, null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new CpiCollectionAttempt(UUID.randomUUID(), Trigger.MANUAL, AT, true,
                new Result(AT.minusSeconds(1), Status.FAILED, null, null, Failure.FETCH, null))).isInstanceOf(IllegalArgumentException.class);
    }
}
