package com.wallstreetreceipts.api.domain.outcome.assetreturn;

import java.nio.charset.StandardCharsets;

/** Versioned signed asset price-return formula and output policy. */
public enum AssetReturnPolicyVersion {
    SIGNED_BASIS_DENOMINATOR_SCALE_12_HALF_EVEN_V1;

    private static final String CANONICAL_DEFINITION =
            "{\"policyVersion\":\"SIGNED_BASIS_DENOMINATOR_SCALE_12_HALF_EVEN_V1\","
            + "\"requiredPricePairPolicyVersion\":\"SOURCE_RECORDED_BASIS_EVENT_TO_OFFICIAL_ENDPOINT_PRICE_PAIR_V1\","
            + "\"requiredPricePairPolicyDefinitionHash\":\"895e4bc97ebb3a92b80f2c58e2d28abb94440eeca963046ee755fa98825f4887\","
            + "\"pricePairInput\":\"ASSET_RETURN_PRICE_PAIR_RESOLUTION\","
            + "\"unavailableRule\":\"PRESERVE_EXACT_PRICE_PAIR_REASON\","
            + "\"evaluationPrecedence\":[\"PRICE_PAIR_UNAVAILABLE\",\"CALCULATE\","
            + "\"OUTPUT_NOT_REPRESENTABLE\"],"
            + "\"formula\":\"(endpoint-basis)/basis\",\"denominator\":\"BASIS_PRICE\","
            + "\"subtractionCount\":1,\"divisionCount\":1,\"divisionScale\":12,"
            + "\"roundingMode\":\"HALF_EVEN\",\"outputUnits\":\"SIGNED_DECIMAL_RATIO\","
            + "\"inputBoundary\":\"POSITIVE_NUMERIC_38_12_PRICE_PAIR\","
            + "\"outputBoundary\":\"SIGNED_NUMERIC_38_12_AT_LEAST_NEGATIVE_ONE\","
            + "\"outputOverflowReason\":\"OUTPUT_NOT_REPRESENTABLE\","
            + "\"intermediateRounding\":\"ABSENT\",\"secondRounding\":\"ABSENT\","
            + "\"floatOrDoubleConversion\":\"ABSENT\","
            + "\"resultContext\":\"POLICY_IDENTITY_AND_COMPLETE_PRICE_PAIR_RESOLUTION_ONLY\","
            + "\"fallbackBehavior\":\"ABSENT\"}";

    private static final String DEFINITION_HASH =
            "e5e61c4adcd6567bfc76f73114499578f09de2254dc39a2553f3c0e2eaf03486";

    public String canonicalDefinition() {
        return CANONICAL_DEFINITION;
    }

    public byte[] canonicalDefinitionUtf8() {
        return CANONICAL_DEFINITION.getBytes(StandardCharsets.UTF_8);
    }

    public String definitionHash() {
        return DEFINITION_HASH;
    }
}
