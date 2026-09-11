package com.wallstreetreceipts.api.application.scoring;

import java.io.*;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.*;
import com.wallstreetreceipts.api.domain.call.CallDirection;
import com.wallstreetreceipts.api.domain.market.DataMode;
import com.wallstreetreceipts.api.domain.outcome.OutcomeHorizon;
import com.wallstreetreceipts.api.domain.outcome.horizon.*;
import com.wallstreetreceipts.api.domain.outcome.observation.*;
import com.wallstreetreceipts.api.domain.outcome.pricepair.*;
import com.wallstreetreceipts.api.domain.outcome.targeteligibility.BasisForecastTermsEvidence;
import com.wallstreetreceipts.api.domain.outcome.targeterror.TargetPriceEvidence;

/** Reads only the exact ADR-080 encoding. Never resolves a class name supplied by stored data. */
public final class EndpointScoringInputCodec {
    private EndpointScoringInputCodec() {}
    private static final Map<String, Class<?>> TYPES = new HashMap<>();
    static {
        for (var type : List.of(EndpointScoringInput.class, SessionCloseHorizonRequest.class,
                OutcomeBasis.Original.class, OutcomeBasis.Correction.class, TradingSessionCatalog.class, TradingSession.class,
                CatalogPointInTimeEvidence.class, EndpointPriceBinding.class, EndpointPriceObservation.class,
                BasisPriceObservation.class, PricePairAdjustmentEvidence.class, BasisForecastTermsEvidence.class,
                BasisForecastTermsEvidence.TargetDisposition.Present.class, BasisForecastTermsEvidence.TargetDisposition.Absent.class,
                TargetPriceEvidence.class, DataMode.class, CallDirection.class, OutcomeHorizon.class,
                SessionCloseHorizonPolicyVersion.class, EndpointPriceField.class, BasisPriceField.class,
                EndpointPriceAdjustmentBasis.class, CorporateActionContinuity.class)) TYPES.put(type.getName(), type);
    }
    public static byte[] encode(EndpointScoringInput input) { return EndpointScoringFingerprint.encode(Objects.requireNonNull(input)); }
    public static String hash(byte[] bytes) { return EndpointScoringFingerprint.sha256(bytes); }

    public static EndpointScoringInput decode(byte[] bytes) {
        Objects.requireNonNull(bytes);
        if (bytes.length == 0 || bytes.length > 1048576) throw invalid();
        try (var in = new DataInputStream(new ByteArrayInputStream(bytes))) {
            if (!"wsr-endpoint-input-v1".equals(read(in, 0))
                    || !EndpointScoringMethodology.ID.equals(read(in, 0))
                    || !EndpointScoringMethodology.VERSION.equals(read(in, 0))
                    || !EndpointScoringMethodology.definitionHash().equals(read(in, 0))) throw invalid();
            Object decoded = read(in, 0);
            if (!(decoded instanceof EndpointScoringInput input) || in.available() != 0
                    || !Arrays.equals(bytes, encode(input))) throw invalid();
            return input;
        } catch (IOException | ReflectiveOperationException | RuntimeException failure) { throw invalid(); }
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
                yield Arrays.stream(type.getEnumConstants()).filter(e -> ((Enum<?>) e).name().equals(name)).findFirst().orElseThrow(EndpointScoringInputCodec::invalid);
            }
            case 3 -> Instant.parse(text(in));
            case 4 -> LocalDate.parse(text(in));
            case 5 -> Currency.getInstance(text(in));
            case 6 -> {
                String decimal = text(in);
                if (decimal.length() > 64 || !decimal.matches("-?[0-9]+(?:\\.[0-9]+)?")) throw invalid();
                yield new BigDecimal(decimal);
            }
            case 7 -> {
                int count = in.readInt();
                if (count < 0 || count > 4096 || count > in.available()) throw invalid();
                var result = new ArrayList<>();
                for (int i = 0; i < count; i++) result.add(read(in, depth + 1));
                yield List.copyOf(result);
            }
            case 8 -> {
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
            default -> throw invalid();
        };
    }
    private static String text(DataInputStream in) throws IOException {
        int length = in.readInt();
        if (length < 0 || length > 262144 || length > in.available()) throw invalid();
        String text = StandardCharsets.UTF_8.newDecoder().decode(ByteBuffer.wrap(in.readNBytes(length))).toString();
        if (text.length() > 65536) throw invalid();
        return text;
    }
    private static IllegalArgumentException invalid() { return new IllegalArgumentException("Invalid stored scoring input"); }
}
