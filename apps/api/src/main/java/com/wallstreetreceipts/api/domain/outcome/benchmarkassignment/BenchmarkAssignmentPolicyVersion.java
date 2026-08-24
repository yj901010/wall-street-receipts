package com.wallstreetreceipts.api.domain.outcome.benchmarkassignment;

import java.nio.charset.StandardCharsets;

/** Versioned point-in-time benchmark-assignment policy. */
public enum BenchmarkAssignmentPolicyVersion {
    POINT_IN_TIME_EXPLICIT_US_EQUITY_ASSET_SPX_ASSIGNMENT_V1;

    private static final String CANONICAL_DEFINITION = """
            {"policyVersion":"POINT_IN_TIME_EXPLICIT_US_EQUITY_ASSET_SPX_ASSIGNMENT_V1","classificationEvidenceFields":["classificationEvidenceId","providerEventId","basis","assetId","assetType","primaryVenueId","primaryVenueCountryCode","currency","classificationSourceId","classificationSourceRevision","provenanceId","effectiveInterval","availableAt","capturedAt"],"effectiveIntervalFields":["startsAtInclusive","end"],"effectiveIntervalEndVariants":{"OpenEnded":[],"EndsAtExclusive":["value"]},"assignmentEvidenceFields":["assignmentEvidenceId","providerEventId","basis","assetId","assetType","primaryVenueId","primaryVenueCountryCode","currency","assignmentSourceId","assignmentSourceRevision","provenanceId","effectiveInterval","benchmarkAssetId","benchmarkAssetType","benchmarkCurrency","referenceKind","availableAt","capturedAt"],"requestFields":["policyVersion","basis","assetId","evaluationAsOf","classificationCandidates","assignmentCandidates"],"resolutionContextFields":["policyVersion","policyDefinitionHash","basis","assetId","evaluationAsOf"],"resultVariants":{"Resolved":["context","classificationEvidence","assignmentEvidence"],"NotApplicable":["context","classificationEvidence","reason"],"Unavailable":["context","reason"]},"benchmarkReferenceKinds":["PROVIDER_PUBLISHED_PRICE_INDEX","PROVIDER_PUBLISHED_TOTAL_RETURN_INDEX","NON_PROVIDER_PUBLISHED_PRICE_INDEX","UNKNOWN"],"basisModes":["ORIGINAL","CORRECTION"],"cancellationBasisAllowed":false,"requestTemporalRule":"basis.eventTime<=evaluationAsOf","evidenceTemporalRule":"availableAt<=capturedAt","pitPredicate":"availableAt<=evaluationAsOf&&capturedAt<=evaluationAsOf","futureEvidenceRule":"INVISIBLE_TO_ALL_OUTPUT_AND_REASONING","effectiveIntervalPredicate":"startsAtInclusive<=basis.eventTime&&(end==OpenEnded||basis.eventTime<end.value)","effectiveIntervalBoundary":"START_INCLUSIVE_END_EXCLUSIVE","openEndedRepresentation":"EXPLICIT_OPEN_ENDED_VARIANT","venueCountryCodeFormat":"ISO_3166_1_ALPHA_2_UPPERCASE","currencyRepresentation":"ISO_4217_CURRENCY","classificationIdentity":"basis==request.basis&&assetId==request.assetId","classificationCardinality":"EXACTLY_ONE_VISIBLE_VALID_RECORD","inScopePredicate":"assetType==EQUITY&&primaryVenueCountryCode==US&&currency==USD","notApplicableTruthTable":{"nonEquity":"NON_EQUITY","equityNonUsUsd":"NON_US_PRIMARY_VENUE","equityUsNonUsd":"NON_USD_CURRENCY","equityNonUsNonUsd":"NON_US_PRIMARY_VENUE_AND_NON_USD_CURRENCY"},"assignmentCoherence":"basis,assetId,assetType,primaryVenueId,primaryVenueCountryCode,currency==selectedClassification","missingAssignmentTruthTable":{"outOfScope":"NOT_APPLICABLE","inScope":"ASSIGNMENT_MISSING_AS_OF"},"outOfScopeVisibleAssignmentRule":"OUT_OF_SCOPE_ASSIGNMENT_CONFLICT","requiredBenchmarkAssetId":"asset-spx","requiredBenchmarkAssetType":"INDEX","requiredBenchmarkCurrency":"USD","requiredBenchmarkReferenceKind":"PROVIDER_PUBLISHED_PRICE_INDEX","assignmentCardinality":"EXACTLY_ONE_VISIBLE_VALID_RECORD_FOR_IN_SCOPE","knownCandidateSetRule":"ANY_VISIBLE_MISMATCH_FAILS_CLOSED_BEFORE_CARDINALITY","equalDuplicateRule":"AMBIGUOUS_NO_DEDUPLICATION","candidateOrderRule":"ORDER_INDEPENDENT","forbiddenInference":["TICKER","ISSUER_NAME","EXCHANGE_LIKE_TEXT","CURRENT_MASTER_DATA","MARKET_SNAPSHOT_SPX","P2_SP500_UNIVERSE","MAP_OR_TREEMAP","CURRENT_ROW","LATEST_REVISION","NEAREST_INTERVAL","PROVIDER_PREFERENCE","FALLBACK"],"evaluationPrecedence":["CLASSIFICATION_MISSING_AS_OF","CLASSIFICATION_BASIS_MISMATCH","CLASSIFICATION_ASSET_MISMATCH","CLASSIFICATION_EFFECTIVE_INTERVAL_MISMATCH","CLASSIFICATION_AMBIGUOUS","ASSIGNMENT_MISSING_AS_OF_OR_NOT_APPLICABLE","ASSIGNMENT_BASIS_MISMATCH","ASSIGNMENT_ASSET_MISMATCH","ASSIGNMENT_ASSET_TYPE_MISMATCH","ASSIGNMENT_PRIMARY_VENUE_MISMATCH","ASSIGNMENT_PRIMARY_VENUE_COUNTRY_MISMATCH","ASSIGNMENT_CURRENCY_MISMATCH","ASSIGNMENT_EFFECTIVE_INTERVAL_MISMATCH","OUT_OF_SCOPE_ASSIGNMENT_CONFLICT","BENCHMARK_ASSET_ID_MISMATCH","BENCHMARK_ASSET_TYPE_MISMATCH","BENCHMARK_CURRENCY_MISMATCH","BENCHMARK_REFERENCE_KIND_MISMATCH","ASSIGNMENT_AMBIGUOUS","RESOLVE"],"selectedEvidencePreservation":"EXACT_COMPLETE_RECORDS","futureEvidenceOutputRule":"NEVER_ECHOED","lifecycleMapping":"ABSENT","calculatorInvocation":"ABSENT","providerIntegration":"ABSENT","fallbackBehavior":"ABSENT"}\
            """;

    private static final String DEFINITION_HASH =
            "7318514c2f50eda16b2d7ef35bc68d00d6a8b18a0f09f77130525fca2f32da69";

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
