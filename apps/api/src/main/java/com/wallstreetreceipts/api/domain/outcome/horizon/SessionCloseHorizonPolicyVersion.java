package com.wallstreetreceipts.api.domain.outcome.horizon;

import java.nio.charset.StandardCharsets;
import java.util.Objects;

import com.wallstreetreceipts.api.domain.outcome.OutcomeHorizon;

/** Versioned named-horizon policy over explicit trading-session closes. */
public enum SessionCloseHorizonPolicyVersion {
    STRICTLY_AFTER_BASIS_EVENT_SESSION_CLOSE_V1;

    private static final String CANONICAL_DEFINITION =
            "{\"policyVersion\":\"STRICTLY_AFTER_BASIS_EVENT_SESSION_CLOSE_V1\","
            + "\"lineageMode\":\"ORIGINAL_AND_EACH_VALID_CORRECTION\","
            + "\"originalEventField\":\"call.eventTime\","
            + "\"correctionEventField\":\"correction.eventTime\","
            + "\"cancellationBasisAllowed\":false,"
            + "\"eligibleSessionPredicate\":\"session.closesAt>basis.eventTime\","
            + "\"eligibleSessionOrder\":\"SUPPLIED_CATALOG_ORDER\","
            + "\"windowSelection\":\"FIRST_N_ELIGIBLE\","
            + "\"endpointSelection\":\"NTH_ELIGIBLE\","
            + "\"firstEligibleMissingReason\":\"FIRST_ELIGIBLE_SESSION_MISSING\","
            + "\"horizonEndpointMissingReason\":"
            + "\"HORIZON_ENDPOINT_SESSION_MISSING\","
            + "\"readinessState\":\"ABSENT\","
            + "\"sessionCounts\":{\"D1\":1,\"W1\":5,\"M1\":21,"
            + "\"M3\":63,\"M6\":126,\"Y1\":252}}";

    private static final String DEFINITION_HASH =
            "550087efe7ddf2ba31974c89c2740ab79df986eefef48919c32c56a3232f8dc1";

    /** Exact compact definition whose UTF-8 bytes are hashed below. */
    public String canonicalDefinition() {
        return CANONICAL_DEFINITION;
    }

    /** A defensive copy of the canonical definition's exact UTF-8 bytes. */
    public byte[] canonicalDefinitionUtf8() {
        return CANONICAL_DEFINITION.getBytes(StandardCharsets.UTF_8);
    }

    /** Fixed lowercase SHA-256 of {@link #canonicalDefinitionUtf8()}. */
    public String definitionHash() {
        return DEFINITION_HASH;
    }

    /** Exact number of eligible session closes from D1 through this horizon. */
    public int sessionCount(OutcomeHorizon horizon) {
        Objects.requireNonNull(horizon, "horizon must not be null");
        return switch (horizon) {
            case D1 -> 1;
            case W1 -> 5;
            case M1 -> 21;
            case M3 -> 63;
            case M6 -> 126;
            case Y1 -> 252;
        };
    }
}
