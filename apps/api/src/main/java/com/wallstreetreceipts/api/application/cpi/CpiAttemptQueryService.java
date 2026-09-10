package com.wallstreetreceipts.api.application.cpi;

import java.time.Clock;
import java.time.Instant;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Semaphore;
import java.util.function.Supplier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import com.wallstreetreceipts.api.domain.cpi.CpiCollectionAttempt;

@Service
@ConditionalOnProperty(prefix = "app.operator-api", name = "enabled", havingValue = "true")
public class CpiAttemptQueryService {
    private static final int MAX_CONCURRENT_READS = 4;
    private static final Comparator<CpiCollectionAttempt> ORDER = Comparator
            .comparing(CpiCollectionAttempt::startedAt).thenComparing(row -> row.attemptId().toString()).reversed();
    private final CpiAttemptReader reader;
    private final Clock clock;
    // One shared, non-queuing budget for list/selection reads in this singleton service.
    private final Semaphore reads = new Semaphore(MAX_CONCURRENT_READS);
    public CpiAttemptQueryService(CpiAttemptReader reader, Clock clock) { this.reader = reader; this.clock = clock; }

    public Batch recent() {
        var rows = read(reader::recentAttempts);
        var observed = clock.instant();
        if (rows == null || rows.size() > CpiAttemptReader.RECENT_LIMIT + 1) throw new Unavailable();
        var seen = new HashSet<UUID>();
        for (int i = 0; i < rows.size(); i++) {
            validate(rows.get(i), observed);
            if (!seen.add(rows.get(i).attemptId()) || i > 0 && ORDER.compare(rows.get(i - 1), rows.get(i)) > 0) throw new Unavailable();
        }
        return new Batch(observed, rows.stream().limit(CpiAttemptReader.RECENT_LIMIT).toList(), rows.size() > CpiAttemptReader.RECENT_LIMIT);
    }

    public Batch find(String selection) {
        // UUID.fromString alone also accepts shortened, non-canonical input.
        if (selection == null || !selection.matches("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}")) throw new InvalidQuery();
        var id = UUID.fromString(selection);
        var row = read(() -> reader.findAttempt(id)).orElseThrow(NotFound::new);
        var observed = clock.instant();
        validate(row, observed);
        if (!row.attemptId().equals(id)) throw new Unavailable();
        return new Batch(observed, List.of(row), false);
    }

    private static void validate(CpiCollectionAttempt row, Instant observed) {
        if (row == null || row.startedAt().isAfter(observed)
                || row.result() != null && row.result().completedAt().isAfter(observed)) throw new Unavailable();
    }
    private <T> T read(Supplier<T> action) {
        if (!reads.tryAcquire()) throw new Unavailable();
        try { return java.util.Objects.requireNonNull(action.get()); }
        catch (RuntimeException exception) { throw new Unavailable(); } // Never expose SQL/driver/row content.
        finally { reads.release(); }
    }
    public record Batch(Instant observedAt, List<CpiCollectionAttempt> attempts, boolean hasMore) {}
    public static final class InvalidQuery extends IllegalArgumentException {}
    public static final class NotFound extends RuntimeException {}
    public static final class Unavailable extends RuntimeException {}
}
