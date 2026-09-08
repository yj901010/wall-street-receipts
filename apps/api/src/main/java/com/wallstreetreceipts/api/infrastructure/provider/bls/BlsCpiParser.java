package com.wallstreetreceipts.api.infrastructure.provider.bls;

import java.nio.ByteBuffer;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.math.BigDecimal;

import com.fasterxml.jackson.core.StreamReadConstraints;
import com.fasterxml.jackson.core.StreamReadFeature;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.json.JsonMapper;
import org.springframework.stereotype.Component;
import com.wallstreetreceipts.api.domain.cpi.CpiSnapshot;
import com.wallstreetreceipts.api.domain.cpi.CpiSnapshot.Observation;
import com.wallstreetreceipts.api.domain.cpi.CpiSnapshot.Series;

/** Vendor JSON stays inside this adapter; only canonical monthly observations leave it. */
@Component
public final class BlsCpiParser {
    public static final int MAX_BYTES = 1_048_576;
    private final JsonMapper mapper = JsonMapper.builder()
            .enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
            .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS).build();

    public BlsCpiParser() {
        mapper.getFactory().setStreamReadConstraints(StreamReadConstraints.builder()
                .maxNestingDepth(12).maxStringLength(4096).maxNumberLength(32).build());
    }

    public CpiSnapshot parse(byte[] bytes, UUID id, Instant at, int start, int end) {
        try {
            require(bytes.length > 0 && bytes.length <= MAX_BYTES && start >= 1900 && end - start == 3);
            require(end == at.atZone(ZoneOffset.UTC).getYear());
            String text = StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString();
            JsonNode root = mapper.readTree(text);
            keys(root, Set.of("status", "responseTime", "message", "Results"));
            require("REQUEST_SUCCEEDED".equals(string(root, "status")));
            require(root.path("message").isArray() && root.path("message").isEmpty());
            JsonNode results = root.path("Results");
            keys(results, Set.of("series"));
            JsonNode list = results.path("series");
            require(list.isArray() && list.size() == 2);
            var canonical = new HashMap<String, Series>();
            for (JsonNode vendorSeries : list) {
                keys(vendorSeries, Set.of("seriesID", "data"));
                String seriesId = string(vendorSeries, "seriesID");
                require(CpiSnapshot.SERIES_IDS.contains(seriesId) && !canonical.containsKey(seriesId));
                JsonNode data = vendorSeries.path("data");
                require(data.isArray() && !data.isEmpty() && data.size() <= 48);
                var seen = new HashSet<YearMonth>();
                var observations = new ArrayList<Observation>();
                for (JsonNode row : data) {
                    keys(row, Set.of("year", "period", "periodName", "value", "footnotes", "latest"));
                    String year = string(row, "year"), period = string(row, "period");
                    require(year.matches("[0-9]{4}") && period.matches("M(0[1-9]|1[0-2])"));
                    YearMonth month = YearMonth.of(Integer.parseInt(year), Integer.parseInt(period.substring(1)));
                    require(month.getYear() >= start && month.getYear() <= end && seen.add(month));
                    require(month.isBefore(YearMonth.from(at.atZone(ZoneOffset.UTC))));
                    String value = string(row, "value");
                    BigDecimal index = null;
                    if (!value.equals("-")) {
                        require(value.matches("[0-9]{1,6}(\\.[0-9]{1,3})?"));
                        index = new BigDecimal(value);
                        require(index.signum() > 0);
                    }
                    JsonNode notes = row.path("footnotes");
                    require(notes.isArray() && notes.size() <= 8);
                    var footnotes = new ArrayList<String>();
                    for (JsonNode note : notes) {
                        keys(note, Set.of("code", "text"));
                        if (note.isEmpty()) continue;
                        String code = string(note, "code"), noteText = string(note, "text");
                        require(code.length() <= 16 && noteText.length() <= 2048);
                        footnotes.add(code + ": " + noteText);
                    }
                    observations.add(new Observation(month, index, footnotes));
                }
                observations.sort(Comparator.comparing(Observation::month).reversed());
                canonical.put(seriesId, new Series(seriesId, observations));
            }
            return new CpiSnapshot(id, at, sha256(bytes), CpiSnapshot.SERIES_IDS.stream().map(canonical::get).toList());
        } catch (Exception exception) {
            // Never propagate vendor content, parser excerpts, or request credentials.
            throw new IllegalArgumentException("BLS CPI response failed validation");
        }
    }
    public static String sha256(byte[] bytes) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)); }
        catch (Exception exception) { throw new IllegalStateException("SHA-256 unavailable"); }
    }
    private static String string(JsonNode node, String field) {
        require(node.path(field).isTextual());
        return node.path(field).textValue();
    }
    private static void keys(JsonNode node, Set<String> allowed) {
        require(node.isObject());
        node.fieldNames().forEachRemaining(key -> require(allowed.contains(key)));
    }
    private static void require(boolean valid) { if (!valid) throw new IllegalArgumentException(); }
}
