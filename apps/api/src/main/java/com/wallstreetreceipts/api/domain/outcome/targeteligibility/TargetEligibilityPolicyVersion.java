package com.wallstreetreceipts.api.domain.outcome.targeteligibility;

import java.nio.charset.StandardCharsets;

/** Versioned readiness policy for target-hit inputs and horizon maturity. */
public enum TargetEligibilityPolicyVersion {
    POINT_IN_TIME_TARGET_HIT_INPUT_READINESS_V1;

    private static final String CANONICAL_DEFINITION =
            "{\"policyVersion\":\"POINT_IN_TIME_TARGET_HIT_INPUT_READINESS_V1\","
            + "\"requiredHorizonPolicyVersion\":\"STRICTLY_AFTER_BASIS_EVENT_SESSION_CLOSE_V1\","
            + "\"requiredHorizonPolicyDefinitionHash\":\"550087efe7ddf2ba31974c89c2740ab79df986eefef48919c32c56a3232f8dc1\","
            + "\"requiredDirectionPolicyVersion\":\"COLLAPSE_STRONG_DIRECTIONS_NEUTRAL_NON_DIRECTIONAL_V1\","
            + "\"requiredDirectionPolicyDefinitionHash\":\"d83eccc92fedd7ba025745be2c8e78245bc308d0ff479467fa61afe543dc8a50\","
            + "\"basisTermsEvidenceFields\":[\"termsEvidenceId\",\"basis\",\"assetId\",\"direction\",\"targetDisposition\",\"provider\",\"providerEventId\",\"availableAt\",\"capturedAt\",\"provenanceId\"],"
            + "\"requestFields\":[\"policyVersion\",\"horizonResolution\",\"termsEvidence\",\"sideRouting\",\"targetEvidence\",\"catalogEvidence\",\"evaluationAsOf\"],"
            + "\"resolutionContextFields\":[\"policyVersion\",\"policyDefinitionHash\",\"horizonResolution\",\"evaluationAsOf\"],"
            + "\"eligibilityEvidenceFields\":[\"termsEvidence\",\"sideRouting\",\"targetEvidence\",\"catalogEvidence\"],"
            + "\"resultVariants\":{\"ReadyForWindowEvidence\":[\"context\",\"evidence\"],\"Pending\":[\"context\",\"evidence\",\"reason\"],\"NotApplicable\":[\"context\",\"evidence\",\"reason\"],\"Unavailable\":[\"context\",\"evidence\",\"reason\",\"horizonReason\"]},"
            + "\"basisModes\":[\"ORIGINAL\",\"CORRECTION\"],"
            + "\"cancellationBasisAllowed\":false,"
            + "\"cancellationEligibility\":\"NOT_ATTESTED\","
            + "\"termsPitPredicate\":\"availableAt<=evaluationAsOf&&capturedAt<=evaluationAsOf\","
            + "\"futureTermsRule\":\"IDENTICAL_TO_NULL_AND_INVISIBLE_TO_OUTPUT\","
            + "\"targetDispositionVariants\":{\"Present\":[\"sourceTarget\",\"sourceTargetCurrency\",\"targetDate\"],\"Absent\":[]},"
            + "\"presentTargetEvidence\":\"TargetPriceEvidence\","
            + "\"sourceAndNormalizedTargetValues\":\"PRESERVED_SEPARATELY_NO_NUMERIC_EQUALITY_INFERENCE\","
            + "\"targetPitPredicate\":\"availableAt<=evaluationAsOf&&capturedAt<=evaluationAsOf\","
            + "\"futureTargetRule\":\"IDENTICAL_TO_NULL_MISSING_AS_OF_NOT_ABSENT\","
            + "\"catalogPitPredicate\":\"availableAt<=evaluationAsOf&&capturedAt<=evaluationAsOf\","
            + "\"futureCatalogRule\":\"IDENTICAL_TO_NULL_AND_INVISIBLE_TO_OUTPUT\","
            + "\"catalogIdentity\":\"calendarId==horizon.context.calendarId&&catalogRevision==horizon.context.catalogRevision\","
            + "\"basisIdentity\":\"terms.basis==horizon.context.basis&&target.basis==terms.basis\","
            + "\"assetIdentity\":\"target.assetId==terms.assetId\","
            + "\"routeDirectionIdentity\":\"route.source.context.direction==terms.direction\","
            + "\"targetCurrencyIdentity\":\"target.currency==terms.sourceTargetCurrency\","
            + "\"targetDateRule\":\"NON_NULL_UNSUPPORTED_FOR_DIRECTIONAL_PRESENT_TARGET\","
            + "\"nonDirectionalRule\":\"NOT_APPLICABLE_NOT_FALSE_OR_LOSS\","
            + "\"absentTargetRule\":\"NOT_APPLICABLE_NOT_MISSING\","
            + "\"absentVisibleTargetRule\":\"TARGET_STATE_CONFLICT_BEFORE_NOT_APPLICABLE\","
            + "\"notApplicableTruthTable\":{\"absent&&nonDirectional\":\"TARGET_ABSENT_AND_NON_DIRECTIONAL\",\"absentOnly\":\"TARGET_ABSENT\",\"nonDirectionalOnly\":\"NON_DIRECTIONAL\"},"
            + "\"catalogRequiredOnlyAfterDirectionalPresentTargetEvidence\":true,"
            + "\"horizonMaturity\":\"endpointSession.closesAt<=evaluationAsOf\","
            + "\"maturityEquality\":\"READY\","
            + "\"horizonIncompleteReason\":\"PRESERVE_EXACT_NESTED_REASON\","
            + "\"readyMeaning\":\"READY_FOR_LATER_FULL_WINDOW_EVIDENCE_ONLY\","
            + "\"calculatorInvocation\":\"ABSENT\","
            + "\"statusVariants\":[\"READY_FOR_WINDOW_EVIDENCE\",\"PENDING\",\"NOT_APPLICABLE\",\"UNAVAILABLE\"],"
            + "\"evaluationPrecedence\":[\"BASIS_TERMS_NOT_KNOWN_AS_OF\",\"HORIZON_BASIS_MISMATCH\",\"ROUTE_MISSING\",\"ROUTE_DIRECTION_MISMATCH\",\"TARGET_STATE_CONFLICT\",\"TARGET_ABSENT_AND_NON_DIRECTIONAL\",\"TARGET_ABSENT\",\"NON_DIRECTIONAL\",\"TARGET_DATE_SEMANTICS_UNSUPPORTED\",\"TARGET_EVIDENCE_NOT_KNOWN_AS_OF\",\"TARGET_EVIDENCE_BASIS_MISMATCH\",\"TARGET_ASSET_MISMATCH\",\"TARGET_CURRENCY_MISMATCH\",\"CATALOG_NOT_KNOWN_AS_OF\",\"CATALOG_EVIDENCE_MISMATCH\",\"FIRST_ELIGIBLE_SESSION_MISSING\",\"HORIZON_ENDPOINT_SESSION_MISSING\",\"HORIZON_NOT_REACHED_AS_OF\",\"READY_FOR_WINDOW_EVIDENCE\"],"
            + "\"selectedEvidencePreservation\":\"EXACT_COMPLETE_RECORDS\","
            + "\"branchClearingRule\":\"EVIDENCE_AFTER_DECIDING_PRECEDENCE_GATE_IS_NULL\","
            + "\"futureEvidenceOutputRule\":\"NEVER_ECHOED\","
            + "\"fallbackBehavior\":\"ABSENT\"}";

    private static final String DEFINITION_HASH =
            "a6b4c9f4e4d29b5f1a9b0c300e2d7b9505318c708dfb0ad0e88f71324cf65465";

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
