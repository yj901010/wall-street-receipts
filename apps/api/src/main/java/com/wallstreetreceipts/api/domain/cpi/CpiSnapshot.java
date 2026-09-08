package com.wallstreetreceipts.api.domain.cpi;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.YearMonth;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/** A retrieval vintage, never an original-release or historical PIT assertion. */
public record CpiSnapshot(UUID captureId, Instant capturedAt, String responseSha256,
                          List<Series> series) {
    public static final List<String> SERIES_IDS = List.of("CUUR0000SA0", "CUUR0000SA0L1E");
    public static final String POLICY = "BLS_CPI_RETRIEVAL_V1";
    public CpiSnapshot {
        Objects.requireNonNull(captureId);
        Objects.requireNonNull(capturedAt);
        if (responseSha256 == null || !responseSha256.matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException("Invalid CPI receipt identity");
        }
        series = List.copyOf(series);
        if (!series.stream().map(Series::id).toList().equals(SERIES_IDS)) {
            throw new IllegalArgumentException("CPI requires the two exact NSA series");
        }
    }
    public record Series(String id, List<Observation> observations) {
        public Series {
            observations = List.copyOf(observations);
            if (!SERIES_IDS.contains(id) || observations.isEmpty() || observations.size() > 48) throw new IllegalArgumentException("Invalid CPI series");
            YearMonth previous = YearMonth.of(9999, 12);
            for (Observation row : observations) {
                if (!row.month().isBefore(previous)) throw new IllegalArgumentException("CPI months must be unique and descending");
                previous = row.month();
            }
        }
    }
    public record Observation(YearMonth month, BigDecimal index, List<String> footnotes) {
        public Observation {
            Objects.requireNonNull(month);
            if (index != null && (index.signum() <= 0 || index.scale() > 3 || index.compareTo(new BigDecimal("1000000")) >= 0)) throw new IllegalArgumentException("Invalid CPI index");
            footnotes = List.copyOf(footnotes);
            if (footnotes.size() > 8 || footnotes.stream().anyMatch(note -> note.length() > 2066)) throw new IllegalArgumentException("Invalid CPI footnotes");
        }
    }
    /** Exact decimal numerator, one final rounding to a displayed percentage. */
    public static String yearOverYear(Series series, Observation current) {
        var prior = series.observations().stream()
                .filter(row -> row.month().equals(current.month().minusYears(1))).findFirst();
        if (current.index() == null || prior.isEmpty() || prior.get().index() == null) return null;
        BigDecimal basis = prior.get().index();
        if (basis.signum() <= 0) throw new IllegalArgumentException("Invalid CPI basis");
        return current.index().subtract(basis).multiply(new BigDecimal("100"))
                .divide(basis, 1, RoundingMode.HALF_UP).toPlainString();
    }
}
