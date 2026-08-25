package com.wallstreetreceipts.api.web.operator;

import java.io.IOException;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.wallstreetreceipts.api.domain.filing.SecFilingHistoryCollectionAttempt.DescriptorAction;
import com.wallstreetreceipts.api.domain.filing.SecFilingHistoryCollectionAttempt.DescriptorActionKind;

/** Strict, endpoint-specific operator command bodies. */
public final class OperatorSecCollectionAttemptRequests {

    private OperatorSecCollectionAttemptRequests() {
    }

    @JsonDeserialize(using = CaptureRootDeserializer.class)
    public record CaptureRoot(String operatorRequestId, String cik) {
    }

    @JsonDeserialize(using = CollectExactRootDeserializer.class)
    public record CollectExactRoot(
            String operatorRequestId,
            String rootCaptureId,
            List<DescriptorActionRequest> descriptorActions) {

        public CollectExactRoot {
            descriptorActions = List.copyOf(descriptorActions);
        }

        List<DescriptorAction> toDomainActions() {
            return descriptorActions.stream()
                    .map(DescriptorActionRequest::toDomain)
                    .toList();
        }
    }

    public record DescriptorActionRequest(
            int descriptorOrdinal,
            DescriptorActionKind actionKind,
            String selectedSegmentCaptureId) {

        DescriptorAction toDomain() {
            return actionKind == DescriptorActionKind.SELECT_EXACT
                    ? DescriptorAction.selectExact(
                            descriptorOrdinal, selectedSegmentCaptureId)
                    : DescriptorAction.captureNow(descriptorOrdinal);
        }
    }

    public static final class CaptureRootDeserializer extends JsonDeserializer<CaptureRoot> {

        @Override
        public CaptureRoot deserialize(JsonParser parser, DeserializationContext context)
                throws IOException {
            requireObject(parser, "root-capture command");
            Set<String> fields = new HashSet<>();
            String operatorRequestId = null;
            String cik = null;
            while (parser.nextToken() != JsonToken.END_OBJECT) {
                String field = nextUniqueField(parser, fields);
                parser.nextToken();
                switch (field) {
                    case "operatorRequestId" -> operatorRequestId = requiredText(parser, field);
                    case "cik" -> cik = requiredText(parser, field);
                    default -> throw invalid(parser, "Unsupported root-capture command field");
                }
            }
            requireField(parser, fields, "operatorRequestId");
            requireField(parser, fields, "cik");
            requireDocumentEnd(parser);
            return new CaptureRoot(operatorRequestId, cik);
        }
    }

    public static final class CollectExactRootDeserializer
            extends JsonDeserializer<CollectExactRoot> {

        @Override
        public CollectExactRoot deserialize(JsonParser parser, DeserializationContext context)
                throws IOException {
            requireObject(parser, "exact-root collection command");
            Set<String> fields = new HashSet<>();
            String operatorRequestId = null;
            String rootCaptureId = null;
            List<DescriptorActionRequest> descriptorActions = null;
            while (parser.nextToken() != JsonToken.END_OBJECT) {
                String field = nextUniqueField(parser, fields);
                parser.nextToken();
                switch (field) {
                    case "operatorRequestId" -> operatorRequestId = requiredText(parser, field);
                    case "rootCaptureId" -> rootCaptureId = requiredText(parser, field);
                    case "descriptorActions" -> descriptorActions = actions(parser);
                    default -> throw invalid(parser, "Unsupported exact-root command field");
                }
            }
            requireField(parser, fields, "operatorRequestId");
            requireField(parser, fields, "rootCaptureId");
            requireField(parser, fields, "descriptorActions");
            requireDocumentEnd(parser);
            return new CollectExactRoot(operatorRequestId, rootCaptureId, descriptorActions);
        }

        private static List<DescriptorActionRequest> actions(JsonParser parser)
                throws IOException {
            if (parser.currentToken() != JsonToken.START_ARRAY) {
                throw invalid(parser, "descriptorActions must be an array");
            }
            List<DescriptorActionRequest> actions = new ArrayList<>();
            while (parser.nextToken() != JsonToken.END_ARRAY) {
                actions.add(action(parser));
            }
            return List.copyOf(actions);
        }

        private static DescriptorActionRequest action(JsonParser parser) throws IOException {
            requireObject(parser, "descriptor action");
            Set<String> fields = new HashSet<>();
            Integer descriptorOrdinal = null;
            DescriptorActionKind actionKind = null;
            String selectedSegmentCaptureId = null;
            while (parser.nextToken() != JsonToken.END_OBJECT) {
                String field = nextUniqueField(parser, fields);
                parser.nextToken();
                switch (field) {
                    case "descriptorOrdinal" -> descriptorOrdinal = requiredInteger(parser, field);
                    case "actionKind" -> actionKind = actionKind(parser);
                    case "selectedSegmentCaptureId" ->
                            selectedSegmentCaptureId = requiredText(parser, field);
                    default -> throw invalid(parser, "Unsupported descriptor-action field");
                }
            }
            requireField(parser, fields, "descriptorOrdinal");
            requireField(parser, fields, "actionKind");
            if (actionKind == DescriptorActionKind.SELECT_EXACT) {
                requireField(parser, fields, "selectedSegmentCaptureId");
            } else if (fields.contains("selectedSegmentCaptureId")) {
                throw invalid(
                        parser,
                        "CAPTURE_NOW must not contain selectedSegmentCaptureId");
            }
            return new DescriptorActionRequest(
                    descriptorOrdinal, actionKind, selectedSegmentCaptureId);
        }
    }

    private static void requireObject(JsonParser parser, String value) throws JsonMappingException {
        if (parser.currentToken() != JsonToken.START_OBJECT) {
            throw invalid(parser, value + " must be a JSON object");
        }
    }

    private static String nextUniqueField(JsonParser parser, Set<String> fields)
            throws IOException {
        if (parser.currentToken() != JsonToken.FIELD_NAME) {
            throw invalid(parser, "Expected a JSON object field");
        }
        String field = parser.currentName();
        if (!fields.add(field)) {
            throw invalid(parser, "Duplicate operator command field");
        }
        return field;
    }

    private static String requiredText(JsonParser parser, String field)
            throws IOException {
        if (parser.currentToken() != JsonToken.VALUE_STRING
                || parser.getText().isEmpty()) {
            throw invalid(parser, field + " must be a nonempty JSON string");
        }
        return parser.getText();
    }

    private static int requiredInteger(JsonParser parser, String field)
            throws IOException {
        if (parser.currentToken() != JsonToken.VALUE_NUMBER_INT) {
            throw invalid(parser, field + " must be a JSON integer");
        }
        BigInteger value = parser.getBigIntegerValue();
        if (value.compareTo(BigInteger.valueOf(Integer.MIN_VALUE)) < 0
                || value.compareTo(BigInteger.valueOf(Integer.MAX_VALUE)) > 0) {
            throw invalid(parser, field + " must fit a 32-bit integer");
        }
        return value.intValueExact();
    }

    private static DescriptorActionKind actionKind(JsonParser parser)
            throws IOException {
        String value = requiredText(parser, "actionKind");
        try {
            return DescriptorActionKind.valueOf(value);
        } catch (IllegalArgumentException exception) {
            throw invalid(parser, "Unsupported descriptor action kind");
        }
    }

    private static void requireField(JsonParser parser, Set<String> fields, String field)
            throws JsonMappingException {
        if (!fields.contains(field)) {
            throw invalid(parser, "Missing required operator command field");
        }
    }

    private static void requireDocumentEnd(JsonParser parser) throws IOException {
        if (parser.nextToken() != null) {
            throw invalid(parser, "Trailing content is not permitted");
        }
    }

    private static JsonMappingException invalid(JsonParser parser, String message) {
        return JsonMappingException.from(parser, message);
    }
}
