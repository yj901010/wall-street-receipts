package com.wallstreetreceipts.api.domain.filing;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Immutable operator intent plus append-only dispatch and terminal facts for one bounded SEC
 * filing-history collection attempt.
 *
 * <p>A dispatch means only that the application durably authorized the provider-port handoff.
 * The process may stop before the port is invoked, and the row does not assert that an HTTP
 * request started. A dispatch without a terminal outcome is deliberately indeterminate and is
 * never interpreted as a retry instruction.
 */
public record SecFilingHistoryCollectionAttempt(
        String attemptId,
        String commandSha256,
        String operatorRequestId,
        CommandKind commandKind,
        String cik,
        String rootCaptureId,
        List<DescriptorAction> descriptorActions,
        Instant requestedAt,
        ProviderDispatch providerDispatch,
        TerminalOutcome terminalOutcome) {

    public static final String SCHEMA_VERSION = "1.0.0";
    public static final String PROVIDER = "sec-edgar";
    public static final String PRODUCT = "edgar-submissions-operator-collection-attempt";
    public static final String POLICY_VERSION =
            "SEC_OPERATOR_CONTROLLED_COLLECTION_ATTEMPT_V1";
    public static final int MAX_PROVIDER_INVOCATIONS = 1;

    private static final String COMMAND_IDENTITY_VERSION =
            "SEC_FILING_COLLECTION_ATTEMPT_COMMAND_V1";
    private static final String ATTEMPT_IDENTITY_VERSION =
            "SEC_FILING_COLLECTION_ATTEMPT_ID_V1";
    private static final Pattern SHA_256 = Pattern.compile("[0-9a-f]{64}");
    private static final Pattern CANONICAL_CIK = Pattern.compile("[0-9]{10}");
    private static final UUID NIL_UUID = new UUID(0, 0);

    public SecFilingHistoryCollectionAttempt {
        requireSha256(attemptId, "attemptId");
        requireSha256(commandSha256, "commandSha256");
        requireCanonicalOperatorRequestId(operatorRequestId);
        Objects.requireNonNull(commandKind, "commandKind must not be null");
        descriptorActions = canonicalActions(descriptorActions);
        requireMicrosecondInstant(requestedAt, "requestedAt");

        validateCommand(commandKind, cik, rootCaptureId, descriptorActions);
        String expectedCommandSha256 = commandSha256(
                commandKind, cik, rootCaptureId, descriptorActions);
        if (!expectedCommandSha256.equals(commandSha256)) {
            throw new IllegalArgumentException(
                    "commandSha256 must reproduce the canonical attempt command");
        }
        String expectedAttemptId = attemptId(operatorRequestId, commandSha256);
        if (!expectedAttemptId.equals(attemptId)) {
            throw new IllegalArgumentException(
                    "attemptId must reproduce the operator request and command identity");
        }

        if (providerDispatch != null) {
            validateDispatch(commandKind, descriptorActions, requestedAt, providerDispatch);
        }
        if (terminalOutcome != null) {
            validateTerminal(
                    commandKind,
                    descriptorActions,
                    requestedAt,
                    providerDispatch,
                    terminalOutcome);
        }
    }

    public static SecFilingHistoryCollectionAttempt planCaptureRoot(
            String operatorRequestId,
            String cik,
            Instant requestedAt) {
        String canonicalOperatorRequestId = requireCanonicalOperatorRequestId(
                operatorRequestId);
        String canonicalCik = normalizeCik(cik);
        List<DescriptorAction> actions = List.of();
        String commandSha256 = commandSha256(
                CommandKind.CAPTURE_ROOT, canonicalCik, null, actions);
        return new SecFilingHistoryCollectionAttempt(
                attemptId(canonicalOperatorRequestId, commandSha256),
                commandSha256,
                canonicalOperatorRequestId,
                CommandKind.CAPTURE_ROOT,
                canonicalCik,
                null,
                actions,
                requestedAt,
                null,
                null);
    }

    public static SecFilingHistoryCollectionAttempt planCollectExactRoot(
            String operatorRequestId,
            String rootCaptureId,
            List<DescriptorAction> descriptorActions,
            Instant requestedAt) {
        String canonicalOperatorRequestId = requireCanonicalOperatorRequestId(
                operatorRequestId);
        requireSha256(rootCaptureId, "rootCaptureId");
        List<DescriptorAction> actions = canonicalActions(descriptorActions);
        String commandSha256 = commandSha256(
                CommandKind.COLLECT_EXACT_ROOT, null, rootCaptureId, actions);
        return new SecFilingHistoryCollectionAttempt(
                attemptId(canonicalOperatorRequestId, commandSha256),
                commandSha256,
                canonicalOperatorRequestId,
                CommandKind.COLLECT_EXACT_ROOT,
                null,
                rootCaptureId,
                actions,
                requestedAt,
                null,
                null);
    }

    /** Appends the one permitted provider dispatch to a new immutable aggregate value. */
    public SecFilingHistoryCollectionAttempt withProviderDispatch(
            ProviderDispatch dispatch) {
        Objects.requireNonNull(dispatch, "dispatch must not be null");
        if (providerDispatch != null) {
            throw new IllegalStateException("provider dispatch is append-only and already exists");
        }
        if (terminalOutcome != null) {
            throw new IllegalStateException("provider dispatch cannot follow a terminal outcome");
        }
        return new SecFilingHistoryCollectionAttempt(
                attemptId,
                commandSha256,
                operatorRequestId,
                commandKind,
                cik,
                rootCaptureId,
                descriptorActions,
                requestedAt,
                dispatch,
                null);
    }

    /** Appends the one permitted terminal fact to a new immutable aggregate value. */
    public SecFilingHistoryCollectionAttempt withTerminalOutcome(
            TerminalOutcome outcome) {
        Objects.requireNonNull(outcome, "outcome must not be null");
        if (terminalOutcome != null) {
            throw new IllegalStateException("terminal outcome is append-only and already exists");
        }
        return new SecFilingHistoryCollectionAttempt(
                attemptId,
                commandSha256,
                operatorRequestId,
                commandKind,
                cik,
                rootCaptureId,
                descriptorActions,
                requestedAt,
                providerDispatch,
                outcome);
    }

    /**
     * Reconstructs only attempt facts known by the supplied cutoff.
     *
     * <p>The returned attempt never backdates a dispatch or terminal outcome. An empty result
     * means the immutable attempt header itself was not yet known.
     */
    public Optional<SecFilingHistoryCollectionAttempt> knownAt(Instant evaluationAsOf) {
        requireMicrosecondInstant(evaluationAsOf, "evaluationAsOf");
        if (requestedAt.isAfter(evaluationAsOf)) {
            return Optional.empty();
        }
        ProviderDispatch visibleDispatch = providerDispatch != null
                        && !providerDispatch.dispatchedAt().isAfter(evaluationAsOf)
                ? providerDispatch
                : null;
        TerminalOutcome visibleOutcome = terminalOutcome != null
                        && !terminalOutcome.completedAt().isAfter(evaluationAsOf)
                ? terminalOutcome
                : null;
        if (visibleDispatch == providerDispatch && visibleOutcome == terminalOutcome) {
            return Optional.of(this);
        }
        return Optional.of(new SecFilingHistoryCollectionAttempt(
                attemptId,
                commandSha256,
                operatorRequestId,
                commandKind,
                cik,
                rootCaptureId,
                descriptorActions,
                requestedAt,
                visibleDispatch,
                visibleOutcome));
    }

    public LifecycleState lifecycleState() {
        if (terminalOutcome == null) {
            return providerDispatch == null
                    ? LifecycleState.PLANNED
                    : LifecycleState.PROVIDER_DISPATCHED_INDETERMINATE;
        }
        return terminalOutcome.status() == TerminalStatus.SUCCEEDED
                ? LifecycleState.TERMINAL_SUCCEEDED
                : LifecycleState.TERMINAL_FAILED_KNOWN;
    }

    public int maxProviderInvocations() {
        return MAX_PROVIDER_INVOCATIONS;
    }

    public Optional<DescriptorAction> captureNowAction() {
        return descriptorActions.stream()
                .filter(action -> action.actionKind() == DescriptorActionKind.CAPTURE_NOW)
                .findFirst();
    }

    public boolean sameCommandAs(SecFilingHistoryCollectionAttempt other) {
        return other != null && commandSha256.equals(other.commandSha256);
    }

    private static void validateCommand(
            CommandKind commandKind,
            String cik,
            String rootCaptureId,
            List<DescriptorAction> descriptorActions) {
        if (commandKind == CommandKind.CAPTURE_ROOT) {
            requireCanonicalCik(cik);
            if (rootCaptureId != null || !descriptorActions.isEmpty()) {
                throw new IllegalArgumentException(
                        "CAPTURE_ROOT requires only one canonical CIK");
            }
            return;
        }

        if (cik != null) {
            throw new IllegalArgumentException(
                    "COLLECT_EXACT_ROOT must not contain a caller-supplied CIK");
        }
        requireSha256(rootCaptureId, "rootCaptureId");
    }

    private static void validateDispatch(
            CommandKind commandKind,
            List<DescriptorAction> descriptorActions,
            Instant requestedAt,
            ProviderDispatch dispatch) {
        if (dispatch.dispatchedAt().isBefore(requestedAt)) {
            throw new IllegalArgumentException("dispatchedAt must not precede requestedAt");
        }
        if (commandKind == CommandKind.CAPTURE_ROOT) {
            if (dispatch.operation() != ProviderOperation.CAPTURE_ROOT
                    || dispatch.descriptorOrdinal() != null) {
                throw new IllegalArgumentException(
                        "CAPTURE_ROOT dispatch must identify only the root operation");
            }
            return;
        }

        DescriptorAction captureNow = descriptorActions.stream()
                .filter(action -> action.actionKind() == DescriptorActionKind.CAPTURE_NOW)
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException(
                        "a selection-only collection cannot dispatch a provider"));
        if (dispatch.operation() != ProviderOperation.CAPTURE_HISTORICAL_SEGMENT
                || !Objects.equals(
                        captureNow.descriptorOrdinal(), dispatch.descriptorOrdinal())) {
            throw new IllegalArgumentException(
                    "segment dispatch must match the exact CAPTURE_NOW descriptor ordinal");
        }
    }

    private static void validateTerminal(
            CommandKind commandKind,
            List<DescriptorAction> descriptorActions,
            Instant requestedAt,
            ProviderDispatch dispatch,
            TerminalOutcome outcome) {
        if (outcome.completedAt().isBefore(requestedAt)) {
            throw new IllegalArgumentException("completedAt must not precede requestedAt");
        }
        if (dispatch != null && outcome.completedAt().isBefore(dispatch.dispatchedAt())) {
            throw new IllegalArgumentException("completedAt must not precede dispatchedAt");
        }

        if (outcome.requestDisposition() == RequestDisposition.NO_PROVIDER_INVOCATION
                && dispatch != null) {
            throw new IllegalArgumentException(
                    "NO_PROVIDER_INVOCATION must not contain a durable dispatch fact");
        }
        if ((outcome.requestDisposition() == RequestDisposition.PROVIDER_RESPONSE_RECEIVED
                        || outcome.requestDisposition()
                                == RequestDisposition.PROVIDER_START_OR_RESPONSE_UNKNOWN)
                && dispatch == null) {
            throw new IllegalArgumentException(
                    "the request disposition requires a durable dispatch fact");
        }

        if (outcome.status() == TerminalStatus.SUCCEEDED) {
            validateSuccessfulTerminal(commandKind, descriptorActions, dispatch, outcome);
        } else {
            validateFailedTerminal(commandKind, descriptorActions, outcome);
        }
    }

    private static void validateSuccessfulTerminal(
            CommandKind commandKind,
            List<DescriptorAction> descriptorActions,
            ProviderDispatch dispatch,
            TerminalOutcome outcome) {
        if (commandKind == CommandKind.CAPTURE_ROOT) {
            if (dispatch == null
                    || outcome.stage() != TerminalStage.ROOT_CAPTURE
                    || outcome.requestDisposition()
                            != RequestDisposition.PROVIDER_RESPONSE_RECEIVED
                    || !outcome.rootArtifact().isProduced()
                    || outcome.segmentArtifact().isProduced()
                    || outcome.manifestArtifact().isProduced()) {
                throw new IllegalArgumentException(
                        "successful CAPTURE_ROOT must persist exactly one root artifact");
            }
            return;
        }

        boolean capturesSegment = descriptorActions.stream()
                .anyMatch(action -> action.actionKind() == DescriptorActionKind.CAPTURE_NOW);
        RequestDisposition expectedDisposition = capturesSegment
                ? RequestDisposition.PROVIDER_RESPONSE_RECEIVED
                : RequestDisposition.NO_PROVIDER_INVOCATION;
        if (outcome.stage() != TerminalStage.MANIFEST_ASSEMBLY
                || outcome.requestDisposition() != expectedDisposition
                || outcome.rootArtifact().isProduced()
                || outcome.segmentArtifact().isProduced() != capturesSegment
                || !outcome.manifestArtifact().isProduced()) {
            throw new IllegalArgumentException(
                    "successful COLLECT_EXACT_ROOT must persist its exact planned artifacts");
        }
    }

    private static void validateFailedTerminal(
            CommandKind commandKind,
            List<DescriptorAction> descriptorActions,
            TerminalOutcome outcome) {
        if (outcome.rootArtifact().isProduced()
                || outcome.segmentArtifact().isProduced()
                || outcome.manifestArtifact().isProduced()) {
            throw new IllegalArgumentException(
                    "a known failed attempt cannot claim atomically committed artifacts");
        }
        boolean capturesFromProvider = commandKind == CommandKind.CAPTURE_ROOT
                || descriptorActions.stream().anyMatch(
                        action -> action.actionKind() == DescriptorActionKind.CAPTURE_NOW);
        RequestDisposition postResponseDisposition = capturesFromProvider
                ? RequestDisposition.PROVIDER_RESPONSE_RECEIVED
                : RequestDisposition.NO_PROVIDER_INVOCATION;
        TerminalStage providerCaptureStage = commandKind == CommandKind.CAPTURE_ROOT
                ? TerminalStage.ROOT_CAPTURE
                : TerminalStage.SEGMENT_CAPTURE;

        switch (outcome.failureCode()) {
            case EXACT_EVIDENCE_VALIDATION_FAILED -> {
                if (commandKind != CommandKind.COLLECT_EXACT_ROOT) {
                    throw new IllegalArgumentException(
                            "exact-evidence failure requires an exact-root collection");
                }
                requireFailureShape(
                        outcome,
                        TerminalStage.EXACT_EVIDENCE_VALIDATION,
                        RequestDisposition.NO_PROVIDER_INVOCATION);
            }
            case PROVIDER_GATE_CLOSED -> {
                if (!capturesFromProvider) {
                    throw new IllegalArgumentException(
                            "provider-gate failure requires a provider-bound command");
                }
                requireFailureShape(
                        outcome,
                        TerminalStage.PROVIDER_GATE,
                        RequestDisposition.PROVIDER_INVOCATION_NOT_STARTED);
            }
            case PROVIDER_REQUEST_FAILED -> requireProviderFailureShape(
                    capturesFromProvider,
                    outcome,
                    providerCaptureStage,
                    RequestDisposition.PROVIDER_START_OR_RESPONSE_UNKNOWN);
            case PROVIDER_HTTP_STATUS,
                    PROVIDER_RESPONSE_TOO_LARGE,
                    PROVIDER_RESPONSE_INVALID -> requireProviderFailureShape(
                    capturesFromProvider,
                    outcome,
                    providerCaptureStage,
                    RequestDisposition.PROVIDER_RESPONSE_RECEIVED);
            case PROVIDER_RESPONSE_UNREADABLE -> requireProviderFailureShape(
                    capturesFromProvider,
                    outcome,
                    providerCaptureStage,
                    RequestDisposition.PROVIDER_START_OR_RESPONSE_UNKNOWN);
            case MANIFEST_ASSEMBLY_FAILED -> {
                if (commandKind != CommandKind.COLLECT_EXACT_ROOT) {
                    throw new IllegalArgumentException(
                            "manifest failure requires an exact-root collection");
                }
                requireFailureShape(
                        outcome,
                        TerminalStage.MANIFEST_ASSEMBLY,
                        postResponseDisposition);
            }
            case LOCAL_PERSISTENCE_FAILED -> requireFailureShape(
                    outcome,
                    TerminalStage.LOCAL_COMMIT,
                    postResponseDisposition);
            case SOURCE_CAPTURE_PERSISTENCE_FAILED -> {
                if (!capturesFromProvider
                        || outcome.requestDisposition()
                                != RequestDisposition.PROVIDER_RESPONSE_RECEIVED
                        || (outcome.stage() != providerCaptureStage
                                && outcome.stage() != TerminalStage.LOCAL_COMMIT)) {
                    throw new IllegalArgumentException(
                            "source capture persistence failure has an invalid terminal shape");
                }
            }
        }
    }

    private static void requireProviderFailureShape(
            boolean capturesFromProvider,
            TerminalOutcome outcome,
            TerminalStage stage,
            RequestDisposition disposition) {
        if (!capturesFromProvider) {
            throw new IllegalArgumentException(
                    "provider failure requires a provider-bound command");
        }
        requireFailureShape(outcome, stage, disposition);
    }

    private static void requireFailureShape(
            TerminalOutcome outcome,
            TerminalStage stage,
            RequestDisposition requestDisposition) {
        if (outcome.stage() != stage || outcome.requestDisposition() != requestDisposition) {
            throw new IllegalArgumentException("terminal failure shape is inconsistent");
        }
    }

    private static List<DescriptorAction> canonicalActions(
            List<DescriptorAction> descriptorActions) {
        Objects.requireNonNull(descriptorActions, "descriptorActions must not be null");
        List<DescriptorAction> owned;
        try {
            owned = new ArrayList<>(List.copyOf(descriptorActions));
        } catch (NullPointerException exception) {
            throw new NullPointerException("descriptorActions must not contain null");
        }
        owned.sort(Comparator.comparingInt(DescriptorAction::descriptorOrdinal));

        Set<Integer> ordinals = new HashSet<>();
        Set<String> selectedCaptureIds = new HashSet<>();
        int captureNowCount = 0;
        for (DescriptorAction action : owned) {
            if (!ordinals.add(action.descriptorOrdinal())) {
                throw new IllegalArgumentException(
                        "descriptorOrdinal must be unique within one attempt");
            }
            if (action.actionKind() == DescriptorActionKind.CAPTURE_NOW) {
                captureNowCount++;
            } else if (!selectedCaptureIds.add(action.selectedSegmentCaptureId())) {
                throw new IllegalArgumentException(
                        "selectedSegmentCaptureId must be unique within one attempt");
            }
        }
        if (captureNowCount > MAX_PROVIDER_INVOCATIONS) {
            throw new IllegalArgumentException(
                    "one attempt may contain at most one CAPTURE_NOW action");
        }
        return List.copyOf(owned);
    }

    private static String commandSha256(
            CommandKind commandKind,
            String cik,
            String rootCaptureId,
            List<DescriptorAction> descriptorActions) {
        StringBuilder identity = new StringBuilder();
        appendLengthPrefixed(identity, COMMAND_IDENTITY_VERSION);
        appendLengthPrefixed(identity, SCHEMA_VERSION);
        appendLengthPrefixed(identity, PROVIDER);
        appendLengthPrefixed(identity, PRODUCT);
        appendLengthPrefixed(identity, POLICY_VERSION);
        appendLengthPrefixed(identity, commandKind.name());
        appendNullable(identity, cik);
        appendNullable(identity, rootCaptureId);
        appendLengthPrefixed(identity, Integer.toString(descriptorActions.size()));
        for (DescriptorAction action : descriptorActions) {
            appendLengthPrefixed(identity, Integer.toString(action.descriptorOrdinal()));
            appendLengthPrefixed(identity, action.actionKind().name());
            appendNullable(identity, action.selectedSegmentCaptureId());
        }
        return sha256(identity.toString());
    }

    private static String attemptId(String operatorRequestId, String commandSha256) {
        StringBuilder identity = new StringBuilder();
        appendLengthPrefixed(identity, ATTEMPT_IDENTITY_VERSION);
        appendLengthPrefixed(identity, operatorRequestId);
        appendLengthPrefixed(identity, commandSha256);
        return sha256(identity.toString());
    }

    private static String normalizeCik(String cik) {
        if (cik == null
                || !cik.matches("[0-9]{1,10}")
                || cik.chars().allMatch(character -> character == '0')) {
            throw new IllegalArgumentException("CIK must contain between 1 and 10 digits");
        }
        return "0".repeat(10 - cik.length()) + cik;
    }

    private static void requireCanonicalCik(String cik) {
        if (cik == null
                || !CANONICAL_CIK.matcher(cik).matches()
                || "0000000000".equals(cik)) {
            throw new IllegalArgumentException("cik must be a canonical nonzero ten-digit CIK");
        }
    }

    private static String requireCanonicalOperatorRequestId(String value) {
        if (value == null) {
            throw new IllegalArgumentException("operatorRequestId must be a canonical UUID");
        }
        try {
            UUID parsed = UUID.fromString(value);
            if (NIL_UUID.equals(parsed) || !parsed.toString().equals(value)) {
                throw new IllegalArgumentException(
                        "operatorRequestId must be a canonical nonzero UUID");
            }
            return value;
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException(
                    "operatorRequestId must be a canonical nonzero UUID", exception);
        }
    }

    private static void requireSha256(String value, String field) {
        if (value == null || !SHA_256.matcher(value).matches()) {
            throw new IllegalArgumentException(field + " must be lowercase SHA-256 hex");
        }
    }

    private static void requireMicrosecondInstant(Instant value, String field) {
        Objects.requireNonNull(value, field + " must not be null");
        if (!value.equals(value.truncatedTo(ChronoUnit.MICROS))) {
            throw new IllegalArgumentException(field + " must have microsecond precision");
        }
    }

    private static void appendNullable(StringBuilder identity, String value) {
        appendLengthPrefixed(identity, value == null ? "ABSENT" : "PRESENT");
        if (value != null) {
            appendLengthPrefixed(identity, value);
        }
    }

    private static void appendLengthPrefixed(StringBuilder identity, String value) {
        identity.append(value.getBytes(StandardCharsets.UTF_8).length)
                .append(':')
                .append(value);
    }

    private static String sha256(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
            return java.util.HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    public record DescriptorAction(
            int descriptorOrdinal,
            DescriptorActionKind actionKind,
            String selectedSegmentCaptureId) {

        public DescriptorAction {
            if (descriptorOrdinal < 0) {
                throw new IllegalArgumentException(
                        "descriptorOrdinal must be nonnegative");
            }
            Objects.requireNonNull(actionKind, "actionKind must not be null");
            if (actionKind == DescriptorActionKind.SELECT_EXACT) {
                requireSha256(selectedSegmentCaptureId, "selectedSegmentCaptureId");
            } else if (selectedSegmentCaptureId != null) {
                throw new IllegalArgumentException(
                        "CAPTURE_NOW must not contain a selected segment capture ID");
            }
        }

        public static DescriptorAction selectExact(
                int descriptorOrdinal,
                String selectedSegmentCaptureId) {
            return new DescriptorAction(
                    descriptorOrdinal,
                    DescriptorActionKind.SELECT_EXACT,
                    selectedSegmentCaptureId);
        }

        public static DescriptorAction captureNow(int descriptorOrdinal) {
            return new DescriptorAction(
                    descriptorOrdinal,
                    DescriptorActionKind.CAPTURE_NOW,
                    null);
        }
    }

    public record ProviderDispatch(
            ProviderOperation operation,
            Integer descriptorOrdinal,
            Instant dispatchedAt) {

        public ProviderDispatch {
            Objects.requireNonNull(operation, "operation must not be null");
            requireMicrosecondInstant(dispatchedAt, "dispatchedAt");
            if (operation == ProviderOperation.CAPTURE_ROOT) {
                if (descriptorOrdinal != null) {
                    throw new IllegalArgumentException(
                            "root dispatch must not contain a descriptor ordinal");
                }
            } else if (descriptorOrdinal == null || descriptorOrdinal < 0) {
                throw new IllegalArgumentException(
                        "segment dispatch must contain a nonnegative descriptor ordinal");
            }
        }

        public static ProviderDispatch captureRoot(Instant dispatchedAt) {
            return new ProviderDispatch(
                    ProviderOperation.CAPTURE_ROOT, null, dispatchedAt);
        }

        public static ProviderDispatch captureHistoricalSegment(
                int descriptorOrdinal,
                Instant dispatchedAt) {
            return new ProviderDispatch(
                    ProviderOperation.CAPTURE_HISTORICAL_SEGMENT,
                    descriptorOrdinal,
                    dispatchedAt);
        }
    }

    public record ArtifactAppend(
            String artifactId,
            ArtifactAppendStatus status) {

        public ArtifactAppend {
            Objects.requireNonNull(status, "status must not be null");
            if (status == ArtifactAppendStatus.NOT_APPLICABLE) {
                if (artifactId != null) {
                    throw new IllegalArgumentException(
                            "NOT_APPLICABLE artifact must not contain an ID");
                }
            } else {
                requireSha256(artifactId, "artifactId");
            }
        }

        public static ArtifactAppend notApplicable() {
            return new ArtifactAppend(null, ArtifactAppendStatus.NOT_APPLICABLE);
        }

        public static ArtifactAppend inserted(String artifactId) {
            return new ArtifactAppend(artifactId, ArtifactAppendStatus.INSERTED);
        }

        public static ArtifactAppend identicalReplay(String artifactId) {
            return new ArtifactAppend(artifactId, ArtifactAppendStatus.IDENTICAL_REPLAY);
        }

        public boolean isProduced() {
            return status != ArtifactAppendStatus.NOT_APPLICABLE;
        }
    }

    public record TerminalOutcome(
            TerminalStatus status,
            TerminalStage stage,
            RequestDisposition requestDisposition,
            FailureCode failureCode,
            Integer httpStatus,
            ArtifactAppend rootArtifact,
            ArtifactAppend segmentArtifact,
            ArtifactAppend manifestArtifact,
            Instant completedAt) {

        public TerminalOutcome {
            Objects.requireNonNull(status, "status must not be null");
            Objects.requireNonNull(stage, "stage must not be null");
            Objects.requireNonNull(
                    requestDisposition, "requestDisposition must not be null");
            Objects.requireNonNull(rootArtifact, "rootArtifact must not be null");
            Objects.requireNonNull(segmentArtifact, "segmentArtifact must not be null");
            Objects.requireNonNull(manifestArtifact, "manifestArtifact must not be null");
            requireMicrosecondInstant(completedAt, "completedAt");

            if (status == TerminalStatus.SUCCEEDED) {
                if (failureCode != null || httpStatus != null) {
                    throw new IllegalArgumentException(
                            "successful terminal outcome must not contain a failure");
                }
            } else {
                Objects.requireNonNull(failureCode, "failed outcome must contain a failureCode");
                if (httpStatus != null
                        && (failureCode != FailureCode.PROVIDER_HTTP_STATUS
                                || httpStatus < 100
                                || httpStatus > 599
                                || httpStatus == 200)) {
                    throw new IllegalArgumentException(
                            "httpStatus is valid only for a provider HTTP-status failure");
                }
                if (failureCode == FailureCode.PROVIDER_HTTP_STATUS && httpStatus == null) {
                    throw new IllegalArgumentException(
                            "provider HTTP-status failure must contain httpStatus");
                }
            }
        }

        public static TerminalOutcome succeeded(
                TerminalStage stage,
                RequestDisposition requestDisposition,
                ArtifactAppend rootArtifact,
                ArtifactAppend segmentArtifact,
                ArtifactAppend manifestArtifact,
                Instant completedAt) {
            return new TerminalOutcome(
                    TerminalStatus.SUCCEEDED,
                    stage,
                    requestDisposition,
                    null,
                    null,
                    rootArtifact,
                    segmentArtifact,
                    manifestArtifact,
                    completedAt);
        }

        public static TerminalOutcome failed(
                TerminalStage stage,
                RequestDisposition requestDisposition,
                FailureCode failureCode,
                Integer httpStatus,
                Instant completedAt) {
            ArtifactAppend notApplicable = ArtifactAppend.notApplicable();
            return new TerminalOutcome(
                    TerminalStatus.FAILED_KNOWN,
                    stage,
                    requestDisposition,
                    failureCode,
                    httpStatus,
                    notApplicable,
                    notApplicable,
                    notApplicable,
                    completedAt);
        }
    }

    public enum CommandKind {
        CAPTURE_ROOT,
        COLLECT_EXACT_ROOT
    }

    public enum DescriptorActionKind {
        SELECT_EXACT,
        CAPTURE_NOW
    }

    public enum ProviderOperation {
        CAPTURE_ROOT,
        CAPTURE_HISTORICAL_SEGMENT
    }

    public enum LifecycleState {
        PLANNED,
        PROVIDER_DISPATCHED_INDETERMINATE,
        TERMINAL_SUCCEEDED,
        TERMINAL_FAILED_KNOWN
    }

    public enum TerminalStatus {
        SUCCEEDED,
        FAILED_KNOWN
    }

    public enum TerminalStage {
        EXACT_EVIDENCE_VALIDATION,
        PROVIDER_GATE,
        ROOT_CAPTURE,
        SEGMENT_CAPTURE,
        MANIFEST_ASSEMBLY,
        LOCAL_COMMIT
    }

    public enum RequestDisposition {
        NO_PROVIDER_INVOCATION,
        PROVIDER_INVOCATION_NOT_STARTED,
        PROVIDER_RESPONSE_RECEIVED,
        PROVIDER_START_OR_RESPONSE_UNKNOWN
    }

    public enum FailureCode {
        EXACT_EVIDENCE_VALIDATION_FAILED,
        PROVIDER_GATE_CLOSED,
        PROVIDER_REQUEST_FAILED,
        PROVIDER_HTTP_STATUS,
        PROVIDER_RESPONSE_UNREADABLE,
        PROVIDER_RESPONSE_TOO_LARGE,
        PROVIDER_RESPONSE_INVALID,
        SOURCE_CAPTURE_PERSISTENCE_FAILED,
        MANIFEST_ASSEMBLY_FAILED,
        LOCAL_PERSISTENCE_FAILED
    }

    public enum ArtifactAppendStatus {
        NOT_APPLICABLE,
        INSERTED,
        IDENTICAL_REPLAY
    }
}
