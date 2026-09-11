package com.wallstreetreceipts.api.application.scoring;

import java.io.*;
import java.math.BigDecimal;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.*;
import com.wallstreetreceipts.api.domain.master.AssetType;
import com.wallstreetreceipts.api.domain.outcome.horizon.OutcomeBasis;
import com.wallstreetreceipts.api.domain.outcome.benchmarkassignment.*;
import com.wallstreetreceipts.api.domain.outcome.benchmarkreferencepair.*;
import com.wallstreetreceipts.api.domain.outcome.sectorassignment.*;
import com.wallstreetreceipts.api.domain.outcome.sectorreferencepair.*;

/** Closed canonical format. Never loads a class named by input, never changes ADR-080 bytes. */
public final class ComparativeScoringInputCodec {
    private ComparativeScoringInputCodec() {}
    private static final int MAX_BYTES = 1048576;
    private static final String FORMAT = "wsr-comparative-input-v1";
    private static final Map<String, Class<?>> TYPES;
    static {
        var types = new HashMap<String, Class<?>>();
        for (var type : List.of(ComparativeScoringInput.class, ComparativeScoringInput.Benchmark.class,
                ComparativeScoringInput.Sector.class, OutcomeBasis.Original.class, OutcomeBasis.Correction.class,
                BenchmarkAssignmentRequest.class, BenchmarkAssignmentPolicyVersion.class, BenchmarkAssetClassificationEvidence.class,
                BenchmarkAssetClassificationEvidence.EffectiveInterval.class, BenchmarkAssetClassificationEvidence.OpenEnded.class,
                BenchmarkAssetClassificationEvidence.EndsAtExclusive.class, BenchmarkAssignmentEvidence.class,
                BenchmarkAssignmentEvidence.BenchmarkReferenceKind.class, BenchmarkReferenceIndexEvidence.class,
                BenchmarkReferenceIndexEvidence.EffectiveInterval.class, BenchmarkReferenceIndexEvidence.OpenEnded.class,
                BenchmarkReferenceIndexEvidence.EndsAtExclusive.class, BenchmarkReferenceIndexEvidence.ReferenceIndexKind.class,
                BenchmarkReferenceLevelObservation.class, BenchmarkReferenceLevelObservation.ReferenceLevelField.class,
                BenchmarkIndexDivisorContinuityEvidence.class, BenchmarkIndexDivisorContinuityEvidence.DivisorContinuity.class,
                SectorAssignmentRequest.class, SectorAssignmentPolicyVersion.class, SectorAssetClassificationEvidence.class,
                SectorAssetClassificationEvidence.EffectiveInterval.class, SectorAssetClassificationEvidence.OpenEnded.class,
                SectorAssetClassificationEvidence.EndsAtExclusive.class, SectorMembershipEvidence.class, SectorMappingEvidence.class,
                SectorMappingEvidence.Recorded.class, SectorMappingEvidence.NotPublished.class, SectorMappingEvidence.Mapped.class,
                SectorMappingEvidence.NotMapped.class, SectorMappingEvidence.NotMappedReason.class, SectorReferenceIndexEvidence.class,
                SectorReferenceIndexEvidence.EffectiveInterval.class, SectorReferenceIndexEvidence.OpenEnded.class,
                SectorReferenceIndexEvidence.EndsAtExclusive.class, SectorReferenceIndexEvidence.ReferenceIndexKind.class,
                SectorReferenceLevelObservation.class, SectorReferenceLevelObservation.ReferenceLevelField.class,
                SectorIndexDivisorContinuityEvidence.class, SectorIndexDivisorContinuityEvidence.DivisorContinuity.class, AssetType.class)) {
            types.put(type.getName(), type);
        }
        TYPES = Map.copyOf(types);
    }

    public static String hash(byte[] bytes) { return EndpointScoringInputCodec.hash(bytes); }
    public static byte[] encode(ComparativeScoringInput input) {
        Objects.requireNonNull(input, "input");
        var bytes = new ByteArrayOutputStream();
        try (var out = new DataOutputStream(bytes)) {
            var encoder = new Encoder(bytes, out);
            for (String header : List.of(FORMAT, ComparativeScoringMethodology.ID, ComparativeScoringMethodology.VERSION,
                    ComparativeScoringMethodology.definitionHash())) encoder.value(header);
            encoder.value(input);
            return bytes.toByteArray();
        } catch (IOException | ReflectiveOperationException failure) { throw invalid(); }
    }

    public static ComparativeScoringInput decode(byte[] bytes) {
        Objects.requireNonNull(bytes, "bytes");
        if (bytes.length == 0 || bytes.length > MAX_BYTES) throw invalid();
        try (var in = new DataInputStream(new ByteArrayInputStream(bytes))) {
            for (String header : List.of(FORMAT, ComparativeScoringMethodology.ID, ComparativeScoringMethodology.VERSION,
                    ComparativeScoringMethodology.definitionHash())) if (!header.equals(read(in, 0))) throw invalid();
            Object value = read(in, 0);
            if (!(value instanceof ComparativeScoringInput input) || in.available() != 0
                    || !Arrays.equals(bytes, encode(input))) throw invalid();
            return input;
        } catch (IOException | ReflectiveOperationException | RuntimeException failure) { throw invalid(); }
    }

    private record Encoder(ByteArrayOutputStream bytes, DataOutputStream out) {
        void value(Object value) throws IOException, ReflectiveOperationException {
            if (value == null) out.writeByte(0);
            else if (value instanceof String text) { out.writeByte(1); text(text); }
            else if (value instanceof Enum<?> e && TYPES.containsValue(e.getDeclaringClass())) {
                out.writeByte(2); text(e.getDeclaringClass().getName()); text(e.name());
            } else if (value instanceof Instant instant) { out.writeByte(3); text(instant.toString()); }
            else if (value instanceof Currency currency) { out.writeByte(4); text(currency.getCurrencyCode()); }
            else if (value instanceof BigDecimal decimal) { out.writeByte(5); text(decimal.stripTrailingZeros().toPlainString()); }
            else if (value instanceof List<?> list) {
                if (list.size() > 4096) throw invalid();
                out.writeByte(6); out.writeInt(list.size());
                for (Object item : list) value(item);
            } else if (value instanceof EndpointScoringInput endpoint) {
                out.writeByte(8);
                byte[] encoded = EndpointScoringInputCodec.encode(endpoint);
                if (bytes.size() + 4L + encoded.length > MAX_BYTES) throw invalid();
                out.writeInt(encoded.length); out.write(encoded);
            } else if (TYPES.containsValue(value.getClass()) && value.getClass().isRecord()) {
                out.writeByte(7); text(value.getClass().getName());
                var fields = value.getClass().getRecordComponents();
                out.writeInt(fields.length);
                for (var field : fields) { text(field.getName()); value(field.getAccessor().invoke(value)); }
            } else throw invalid();
            if (bytes.size() > MAX_BYTES) throw invalid();
        }
        void text(String value) throws IOException {
            if (value.length() > 65536) throw invalid();
            var encoded = StandardCharsets.UTF_8.newEncoder().encode(CharBuffer.wrap(value));
            if (bytes.size() + 4L + encoded.remaining() > MAX_BYTES) throw invalid();
            out.writeInt(encoded.remaining());
            out.write(encoded.array(), encoded.arrayOffset() + encoded.position(), encoded.remaining());
        }
    }

    private static Object read(DataInputStream in, int depth) throws IOException, ReflectiveOperationException {
        if (depth > 24) throw invalid();
        return switch (in.readUnsignedByte()) {
            case 0 -> null;
            case 1 -> text(in);
            case 2 -> {
                Class<?> type = TYPES.get(text(in));
                String name = text(in);
                if (type == null || !type.isEnum()) throw invalid();
                yield Arrays.stream(type.getEnumConstants()).filter(e -> ((Enum<?>) e).name().equals(name))
                        .findFirst().orElseThrow(ComparativeScoringInputCodec::invalid);
            }
            case 3 -> Instant.parse(text(in));
            case 4 -> Currency.getInstance(text(in));
            case 5 -> {
                String decimal = text(in);
                if (decimal.length() > 64 || !decimal.matches("-?[0-9]+(?:\\.[0-9]+)?")) throw invalid();
                yield new BigDecimal(decimal);
            }
            case 6 -> {
                int count = in.readInt();
                if (count < 0 || count > 4096 || count > in.available()) throw invalid();
                var list = new ArrayList<>();
                for (int i = 0; i < count; i++) list.add(read(in, depth + 1));
                yield List.copyOf(list);
            }
            case 7 -> {
                Class<?> type = TYPES.get(text(in));
                if (type == null || !type.isRecord()) throw invalid();
                var fields = type.getRecordComponents();
                if (in.readInt() != fields.length) throw invalid();
                var values = new Object[fields.length];
                for (int i = 0; i < fields.length; i++) {
                    if (!fields[i].getName().equals(text(in))) throw invalid();
                    values[i] = read(in, depth + 1);
                }
                yield type.getDeclaredConstructor(Arrays.stream(fields).map(f -> f.getType()).toArray(Class<?>[]::new)).newInstance(values);
            }
            case 8 -> {
                int length = in.readInt();
                if (length <= 0 || length > MAX_BYTES || length > in.available()) throw invalid();
                yield EndpointScoringInputCodec.decode(in.readNBytes(length));
            }
            default -> throw invalid();
        };
    }
    private static String text(DataInputStream in) throws IOException {
        int length = in.readInt();
        if (length < 0 || length > 262144 || length > in.available()) throw invalid();
        String value = StandardCharsets.UTF_8.newDecoder().decode(ByteBuffer.wrap(in.readNBytes(length))).toString();
        if (value.length() > 65536) throw invalid();
        return value;
    }
    private static IllegalArgumentException invalid() { return new IllegalArgumentException("Invalid comparative scoring input encoding"); }
}
