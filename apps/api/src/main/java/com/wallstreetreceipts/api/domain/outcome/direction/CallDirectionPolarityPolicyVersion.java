package com.wallstreetreceipts.api.domain.outcome.direction;

import java.nio.charset.StandardCharsets;

/** Versioned reduction from canonical call direction to forecast polarity. */
public enum CallDirectionPolarityPolicyVersion {
    COLLAPSE_STRONG_DIRECTIONS_NEUTRAL_NON_DIRECTIONAL_V1;

    private static final String CANONICAL_DEFINITION =
            "{\"policyVersion\":"
            + "\"COLLAPSE_STRONG_DIRECTIONS_NEUTRAL_NON_DIRECTIONAL_V1\","
            + "\"inputType\":\"CallDirection\","
            + "\"mappings\":{\"STRONG_BULLISH\":\"BULLISH\","
            + "\"BULLISH\":\"BULLISH\",\"NEUTRAL\":\"NON_DIRECTIONAL\","
            + "\"BEARISH\":\"BEARISH\",\"STRONG_BEARISH\":\"BEARISH\"},"
            + "\"resultVariants\":[\"DIRECTIONAL\",\"NON_DIRECTIONAL\"],"
            + "\"directionalSides\":[\"BULLISH\",\"BEARISH\"],"
            + "\"nonDirectionalReason\":\"NEUTRAL_DIRECTION\","
            + "\"directResultConsistency\":\"DIRECTION_MUST_MATCH_MAPPING\","
            + "\"nullDirectionBehavior\":\"REJECT\","
            + "\"fallbackBehavior\":\"ABSENT\"}";

    private static final String DEFINITION_HASH =
            "d83eccc92fedd7ba025745be2c8e78245bc308d0ff479467fa61afe543dc8a50";

    /** Exact compact definition whose UTF-8 bytes identify this policy. */
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
}
