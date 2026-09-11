package com.wallstreetreceipts.api.application.scoring;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.CharBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Currency;
import java.util.HexFormat;
import java.util.List;
import java.util.Set;
import com.wallstreetreceipts.api.domain.outcome.horizon.*;
import com.wallstreetreceipts.api.domain.outcome.observation.*;
import com.wallstreetreceipts.api.domain.outcome.pricepair.*;
import com.wallstreetreceipts.api.domain.outcome.targeteligibility.BasisForecastTermsEvidence;
import com.wallstreetreceipts.api.domain.outcome.targeterror.TargetPriceEvidence;

/** Closed input encoding, not Java serialization, record.toString, or mutable application JSON settings. */
final class EndpointScoringFingerprint {
    private EndpointScoringFingerprint() {}
    private static final Set<Class<?>> RECORDS = Set.of(EndpointScoringInput.class,
            SessionCloseHorizonRequest.class, OutcomeBasis.Original.class, OutcomeBasis.Correction.class,
            TradingSessionCatalog.class, TradingSession.class, CatalogPointInTimeEvidence.class,
            EndpointPriceBinding.class, EndpointPriceObservation.class, BasisPriceObservation.class,
            PricePairAdjustmentEvidence.class, BasisForecastTermsEvidence.class,
            BasisForecastTermsEvidence.TargetDisposition.Present.class,
            BasisForecastTermsEvidence.TargetDisposition.Absent.class, TargetPriceEvidence.class);

    static String fingerprint(EndpointScoringInput input) {
        return sha256(encode(input));
    }

    static byte[] encode(EndpointScoringInput input) {
        var bytes = new ByteArrayOutputStream();
        try (var output = new DataOutputStream(bytes)) {
            var encoder = new Encoder(bytes, output);
            encoder.value("wsr-endpoint-input-v1");
            encoder.value(EndpointScoringMethodology.ID);
            encoder.value(EndpointScoringMethodology.VERSION);
            encoder.value(EndpointScoringMethodology.definitionHash());
            encoder.value(input);
            return bytes.toByteArray();
        } catch (IOException | ReflectiveOperationException failure) {
            throw new IllegalArgumentException("Scoring input cannot be canonically encoded", failure);
        }
    }

    static String sha256(byte[] bytes) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)); }
        catch (NoSuchAlgorithmException impossible) { throw new IllegalStateException(impossible); }
    }

    private record Encoder(ByteArrayOutputStream bytes, DataOutputStream output) {
        void value(Object value) throws IOException, ReflectiveOperationException {
            if (value == null) output.writeByte(0);
            else if (value instanceof String text) { output.writeByte(1); text(text); }
            else if (value instanceof Enum<?> enumeration) {
                output.writeByte(2); text(enumeration.getDeclaringClass().getName()); text(enumeration.name());
            } else if (value instanceof Instant instant) { output.writeByte(3); text(instant.toString()); }
            else if (value instanceof LocalDate date) { output.writeByte(4); text(date.toString()); }
            else if (value instanceof Currency currency) { output.writeByte(5); text(currency.getCurrencyCode()); }
            else if (value instanceof BigDecimal decimal) { output.writeByte(6); text(decimal.stripTrailingZeros().toPlainString()); }
            else if (value instanceof List<?> list) {
                output.writeByte(7); output.writeInt(list.size());
                for (Object item : list) value(item);
            } else if (RECORDS.contains(value.getClass())) {
                output.writeByte(8); text(value.getClass().getName());
                var fields = value.getClass().getRecordComponents();
                output.writeInt(fields.length);
                for (var field : fields) { text(field.getName()); value(field.getAccessor().invoke(value)); }
            } else throw new IllegalArgumentException("Unsupported scoring input type");
            if (bytes.size() > 1048576) throw new IllegalArgumentException("Encoded scoring input too large");
        }

        void text(String text) throws IOException {
            if (text.length() > 65536) throw new IllegalArgumentException("Scoring input text too large");
            // REPORT malformed surrogate input; never hash replacement bytes that could alias another input.
            var encoded = StandardCharsets.UTF_8.newEncoder().encode(CharBuffer.wrap(text));
            if (bytes.size() + 4L + encoded.remaining() > 1048576) throw new IllegalArgumentException("Encoded scoring input too large");
            output.writeInt(encoded.remaining());
            output.write(encoded.array(), encoded.arrayOffset() + encoded.position(), encoded.remaining());
        }
    }
}
