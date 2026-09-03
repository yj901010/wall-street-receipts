# ADR-029 — Point-in-Time Explicit WSR Sector Assignment V1

- Status: Accepted
- Date: 2026-08-24

## Context

ADR-026 requires benchmark and sector assignment to remain independently typed
and frozen at the exact original or correction `OutcomeBasis.eventTime`.
ADR-028 locks the provider-neutral `wsr-economic-activity` taxonomy and the
exact provider-node mapping policy, but deliberately adds no executable sector
assignment, provider mapping set, or issuer membership. The repository now
needs a disconnected selector that can resolve only supplied point-in-time
classification, provider membership, and mapping evidence without looking up
current state or claiming that real provider data exists.

ADR-029 freezes WSR sector assignment to explicit point-in-time membership and mapped provider-node evidence.

## Decision

The executable V1 policy is exactly
`POINT_IN_TIME_EXPLICIT_WSR_ECONOMIC_ACTIVITY_SECTOR_ASSIGNMENT_V1`. It adds
seven production types in
`com.wallstreetreceipts.api.domain.outcome.sectorassignment` and one matching
`SectorAssignmentSelectorGoldenTest`.

- `SectorAssetClassificationEvidence` preserves the exact basis, asset/type,
  primary venue and explicitly sourced ISO country, ISO currency, evidence and
  provider-event identity, source revision, provenance, explicit effective
  interval, and both point-in-time timestamps.
- `SectorMembershipEvidence` independently preserves the same asset
  classification identity plus exact provider, scheme, scheme revision, node
  ID and source label, membership source/revision, provenance, interval, and
  timestamps. Provider identity text is non-null and non-empty but is not
  stripped, normalized, or case-folded.
- `SectorMappingEvidence` implements ADR-028's exact 23-field mapping row,
  sealed provider-definition and mapping-disposition variants, and three
  closed not-mapped reasons. It remains reusable and does not duplicate
  `OutcomeBasis`.
- `SectorAssignmentRequest` supplies one complete original or correction
  basis, canonical asset ID, microsecond `evaluationAsOf`, a caller-attested
  mapping-set ID/version/hash, and immutable complete classification,
  membership, and mapping candidate lists.
- `SectorAssignmentResolution` is sealed as exactly
  `Resolved(context, classificationEvidence, membershipEvidence,
  mappingEvidence)`, `NotApplicable(context, classificationEvidence, reason)`,
  or `Unavailable(context, reason)`.
- `SectorAssignmentSelector` alone attests PIT filtering, all-visible-candidate
  precedence, effective membership, exact evidence coherence, multiplicity,
  and final selection. Public resolved and not-applicable constructors validate
  only locally decidable consistency.

The request's mapping-set identity is echoed and every selected row must match
it. The selector does not calculate a mapping-set manifest hash or attest full
entry-to-manifest correlation. Those remain a separate caller/provider
boundary under ADR-028, and no actual mapping set is introduced here.

## Exact scope, PIT, and selection semantics

- Sector V1 applies to an explicitly classified `AssetType.EQUITY` regardless
  of venue country or currency. Country and currency remain required preserved
  evidence but do not expand or narrow sector applicability. A coherently
  classified non-equity with no visible membership is exactly
  `NotApplicable(NON_EQUITY)`.
- A visible membership for a non-equity is
  `OUT_OF_SCOPE_MEMBERSHIP_CONFLICT`. Missing classification is unavailable;
  missing membership is unavailable for an equity and cannot infer a WSR node.
- Evidence is visible only when both `availableAt <= evaluationAsOf` and
  `capturedAt <= evaluationAsOf`. Future exact, invalid, conflicting, or
  duplicate evidence is absent from output, reason, conflict, and cardinality.
- Classification and membership carry the complete request basis. Membership
  and mapping effective intervals are start-inclusive/end-exclusive at the
  exact `basis.eventTime`; open-ended intervals use an explicit sealed value.
- Membership-to-mapping provider identity is exact provider ID, scheme ID,
  scheme revision, and node ID under case-sensitive, unnormalized Unicode
  code-point equality. Provider labels remain preserved evidence and are never
  an identity or matching key.
- A mapping must carry the exact ADR-028 mapping-policy version/hash, taxonomy
  ID `wsr-economic-activity`, version `1.0.0`, taxonomy definition hash
  `820ce3ea264d67312fe4f2efe346631a81d74248e9a7f041793d65d8ef0d62ae`,
  and one of ADR-028's twelve closed assignable leaf IDs. The root, unknown,
  other, unclassified, or any new node cannot resolve.
- `Mapped` requires a recorded provider-node definition. A single visible
  `NotMapped` row becomes the corresponding typed unavailable reason. Unequal
  visible dispositions conflict. Any multiple visible rows with the same
  disposition remain ambiguous, including exact duplicates, distinct rows
  mapped to the same target, and distinct not-mapped rows carrying the same
  reason. Conflict precedes ambiguity, and ambiguity precedes a single-row
  not-mapped result.
- Every visible mismatch fails closed at the fixed policy gate before
  multiplicity. Candidate order cannot affect the result and no invalid row is
  filtered away to retain a convenient valid row.
- Ticker, issuer name, current master data, current/latest revision, nearest
  interval, provider preference, raw/normalized/fuzzy labels, silent
  deduplication, P2 map or treemap labels, and fallback are forbidden.

## Exact result reason order

The only not-applicable reason is `NON_EQUITY`.

The unavailable reason order and selector precedence are exactly:

1. `CLASSIFICATION_MISSING_AS_OF`
2. `CLASSIFICATION_BASIS_MISMATCH`
3. `CLASSIFICATION_ASSET_MISMATCH`
4. `CLASSIFICATION_EFFECTIVE_INTERVAL_MISMATCH`
5. `CLASSIFICATION_AMBIGUOUS`
6. `MEMBERSHIP_MISSING_AS_OF`
7. `MEMBERSHIP_BASIS_MISMATCH`
8. `MEMBERSHIP_ASSET_MISMATCH`
9. `MEMBERSHIP_ASSET_TYPE_MISMATCH`
10. `MEMBERSHIP_PRIMARY_VENUE_MISMATCH`
11. `MEMBERSHIP_PRIMARY_VENUE_COUNTRY_MISMATCH`
12. `MEMBERSHIP_CURRENCY_MISMATCH`
13. `MEMBERSHIP_EFFECTIVE_INTERVAL_MISMATCH`
14. `OUT_OF_SCOPE_MEMBERSHIP_CONFLICT`
15. `MEMBERSHIP_AMBIGUOUS`
16. `MAPPING_MISSING_AS_OF`
17. `MAPPING_SET_ID_MISMATCH`
18. `MAPPING_SET_VERSION_MISMATCH`
19. `MAPPING_SET_DEFINITION_HASH_MISMATCH`
20. `MAPPING_POLICY_VERSION_MISMATCH`
21. `MAPPING_POLICY_DEFINITION_HASH_MISMATCH`
22. `MAPPING_TAXONOMY_ID_MISMATCH`
23. `MAPPING_TAXONOMY_VERSION_MISMATCH`
24. `MAPPING_TAXONOMY_DEFINITION_HASH_MISMATCH`
25. `MAPPING_PROVIDER_ID_MISMATCH`
26. `MAPPING_PROVIDER_SCHEME_ID_MISMATCH`
27. `MAPPING_PROVIDER_SCHEME_REVISION_MISMATCH`
28. `MAPPING_PROVIDER_NODE_ID_MISMATCH`
29. `MAPPING_EFFECTIVE_INTERVAL_MISMATCH`
30. `MAPPING_MAPPED_DEFINITION_REQUIRED`
31. `MAPPING_CANONICAL_NODE_NOT_ASSIGNABLE`
32. `MAPPING_CONFLICT`
33. `MAPPING_AMBIGUOUS`
34. `MAPPING_NOT_MAPPED_NO_CANONICAL_EQUIVALENT`
35. `MAPPING_NOT_MAPPED_PROVIDER_NODE_TOO_BROAD`
36. `MAPPING_NOT_MAPPED_PROVIDER_DEFINITION_UNAVAILABLE`

The empty-membership branch yields intentional non-applicability only for a
coherent non-equity classification. Mapping not-mapped reasons are evaluated
only after all fixed identity, interval, target, conflict, and cardinality
gates.

## Canonical policy definition

`SectorAssignmentPolicyVersion` contains exactly the V1 constant above. Its
definition is the following one-line ASCII sequence encoded directly as UTF-8,
with no byte-order mark, surrounding whitespace, or trailing line ending:

```text
{"policyVersion":"POINT_IN_TIME_EXPLICIT_WSR_ECONOMIC_ACTIVITY_SECTOR_ASSIGNMENT_V1","requiredTaxonomyId":"wsr-economic-activity","requiredTaxonomyVersion":"1.0.0","requiredTaxonomyDefinitionHash":"820ce3ea264d67312fe4f2efe346631a81d74248e9a7f041793d65d8ef0d62ae","closedAssignableCanonicalNodeIds":["wsr-sector-digital-systems","wsr-sector-connectivity-media","wsr-sector-health-bioscience","wsr-sector-financial-risk-services","wsr-sector-consumer-essentials","wsr-sector-consumer-choice-commerce","wsr-sector-production-mobility","wsr-sector-energy-systems","wsr-sector-materials-resource-processing","wsr-sector-essential-networks","wsr-sector-property-built-environment","wsr-sector-diversified-operations"],"requiredMappingPolicyVersion":"POINT_IN_TIME_EXPLICIT_PROVIDER_NODE_TO_WSR_ECONOMIC_ACTIVITY_V1","requiredMappingPolicyDefinitionHash":"ba12a277d5ffe266af1745b98948a1e2206494ac31904f31a419d973d5067e77","classificationEvidenceFields":["classificationEvidenceId","providerEventId","basis","assetId","assetType","primaryVenueId","primaryVenueCountryCode","currency","classificationSourceId","classificationSourceRevision","provenanceId","effectiveInterval","availableAt","capturedAt"],"membershipEvidenceFields":["membershipEvidenceId","providerEventId","basis","assetId","assetType","primaryVenueId","primaryVenueCountryCode","currency","providerId","providerSchemeId","providerSchemeRevision","providerNodeId","providerNodeLabel","membershipSourceId","membershipSourceRevision","provenanceId","effectiveInterval","availableAt","capturedAt"],"mappingEvidenceFields":["mappingEvidenceId","providerEventId","mappingPolicyVersion","mappingPolicyDefinitionHash","mappingSetId","mappingSetVersion","mappingSetDefinitionHash","taxonomyId","taxonomyVersion","taxonomyDefinitionHash","providerId","providerSchemeId","providerSchemeRevision","providerNodeId","providerNodeLabel","providerNodeDefinition","mappingDisposition","mappingSourceId","mappingSourceRevision","provenanceId","effectiveInterval","availableAt","capturedAt"],"providerNodeDefinitionVariants":{"Recorded":["value","languageTag"],"NotPublished":[]},"mappingDispositionVariants":{"Mapped":["canonicalNodeId"],"NotMapped":["reason"]},"notMappedReasons":["NO_CANONICAL_EQUIVALENT","PROVIDER_NODE_TOO_BROAD","PROVIDER_DEFINITION_UNAVAILABLE"],"effectiveIntervalFields":["startsAtInclusive","end"],"effectiveIntervalEndVariants":{"OpenEnded":[],"EndsAtExclusive":["value"]},"requestFields":["policyVersion","basis","assetId","evaluationAsOf","mappingSetId","mappingSetVersion","mappingSetDefinitionHash","classificationCandidates","membershipCandidates","mappingCandidates"],"resolutionContextFields":["policyVersion","policyDefinitionHash","basis","assetId","evaluationAsOf","mappingSetId","mappingSetVersion","mappingSetDefinitionHash"],"resultVariants":{"Resolved":["context","classificationEvidence","membershipEvidence","mappingEvidence"],"NotApplicable":["context","classificationEvidence","reason"],"Unavailable":["context","reason"]},"notApplicableReasons":["NON_EQUITY"],"unavailableReasons":["CLASSIFICATION_MISSING_AS_OF","CLASSIFICATION_BASIS_MISMATCH","CLASSIFICATION_ASSET_MISMATCH","CLASSIFICATION_EFFECTIVE_INTERVAL_MISMATCH","CLASSIFICATION_AMBIGUOUS","MEMBERSHIP_MISSING_AS_OF","MEMBERSHIP_BASIS_MISMATCH","MEMBERSHIP_ASSET_MISMATCH","MEMBERSHIP_ASSET_TYPE_MISMATCH","MEMBERSHIP_PRIMARY_VENUE_MISMATCH","MEMBERSHIP_PRIMARY_VENUE_COUNTRY_MISMATCH","MEMBERSHIP_CURRENCY_MISMATCH","MEMBERSHIP_EFFECTIVE_INTERVAL_MISMATCH","OUT_OF_SCOPE_MEMBERSHIP_CONFLICT","MEMBERSHIP_AMBIGUOUS","MAPPING_MISSING_AS_OF","MAPPING_SET_ID_MISMATCH","MAPPING_SET_VERSION_MISMATCH","MAPPING_SET_DEFINITION_HASH_MISMATCH","MAPPING_POLICY_VERSION_MISMATCH","MAPPING_POLICY_DEFINITION_HASH_MISMATCH","MAPPING_TAXONOMY_ID_MISMATCH","MAPPING_TAXONOMY_VERSION_MISMATCH","MAPPING_TAXONOMY_DEFINITION_HASH_MISMATCH","MAPPING_PROVIDER_ID_MISMATCH","MAPPING_PROVIDER_SCHEME_ID_MISMATCH","MAPPING_PROVIDER_SCHEME_REVISION_MISMATCH","MAPPING_PROVIDER_NODE_ID_MISMATCH","MAPPING_EFFECTIVE_INTERVAL_MISMATCH","MAPPING_MAPPED_DEFINITION_REQUIRED","MAPPING_CANONICAL_NODE_NOT_ASSIGNABLE","MAPPING_CONFLICT","MAPPING_AMBIGUOUS","MAPPING_NOT_MAPPED_NO_CANONICAL_EQUIVALENT","MAPPING_NOT_MAPPED_PROVIDER_NODE_TOO_BROAD","MAPPING_NOT_MAPPED_PROVIDER_DEFINITION_UNAVAILABLE"],"basisModes":["ORIGINAL","CORRECTION"],"cancellationBasisAllowed":false,"requestTemporalRule":"basis.eventTime<=evaluationAsOf","evidenceTemporalRule":"availableAt<=capturedAt","pitPredicate":"availableAt<=evaluationAsOf&&capturedAt<=evaluationAsOf","futureEvidenceRule":"INVISIBLE_TO_ALL_OUTPUT_REASON_CONFLICT_AND_CARDINALITY","effectiveIntervalPredicate":"startsAtInclusive<=basis.eventTime&&(end==OpenEnded||basis.eventTime<end.value)","effectiveIntervalBoundary":"START_INCLUSIVE_END_EXCLUSIVE","openEndedRepresentation":"EXPLICIT_OPEN_ENDED_VARIANT","venueCountryCodeFormat":"ISO_3166_1_ALPHA_2_UPPERCASE","currencyRepresentation":"ISO_4217_CURRENCY","providerIdentity":["providerId","providerSchemeId","providerSchemeRevision","providerNodeId"],"providerIdentityComparison":"EXACT_CASE_SENSITIVE_UNNORMALIZED_UNICODE_CODE_POINT_EQUALITY","providerIdentityValidation":"NON_NULL_NON_EMPTY_NO_STRIP_NORMALIZATION_OR_CASE_FOLD","providerLabelRole":"PRESERVED_EVIDENCE_ONLY_NOT_IDENTITY_OR_MATCH_KEY","providerDefinitionRole":"PRESERVED_EVIDENCE_ONLY_MAPPED_REQUIRES_RECORDED","localCanonicalTextValidation":"NONBLANK_TRIMMED","hashValidation":"LOWERCASE_SHA_256_HEX_64","classificationIdentity":"basis==request.basis&&assetId==request.assetId","classificationCardinality":"EXACTLY_ONE_VISIBLE_VALID_RECORD","inScopePredicate":"selectedClassification.assetType==EQUITY","countryCurrencyApplicabilityRole":"PRESERVED_EVIDENCE_ONLY_NOT_SCOPE","membershipCoherence":"basis,assetId,assetType,primaryVenueId,primaryVenueCountryCode,currency==selectedClassification","membershipCardinality":"EXACTLY_ONE_VISIBLE_VALID_RECORD_FOR_EQUITY","missingMembershipTruthTable":{"nonEquity":"NOT_APPLICABLE_NON_EQUITY","equity":"MEMBERSHIP_MISSING_AS_OF"},"outOfScopeVisibleMembershipRule":"OUT_OF_SCOPE_MEMBERSHIP_CONFLICT","mappingSetIdentityCoherence":"mappingSetId,mappingSetVersion,mappingSetDefinitionHash==request","mappingSetIdentityBoundary":"CALLER_SUPPLIED_IDENTITY_ECHOED_AND_ROW_MATCHED_SELECTOR_DOES_NOT_COMPUTE_MANIFEST_HASH_OR_ATTEST_ENTRY_MANIFEST_CORRELATION","mappingIdentityCoherence":"providerId,providerSchemeId,providerSchemeRevision,providerNodeId==selectedMembership","mappedDefinitionRequirement":"MAPPED_REQUIRES_RECORDED_PROVIDER_NODE_DEFINITION","mappedTargetRule":"MAPPED_TARGET_MUST_USE_REQUIRED_TAXONOMY_ID_VERSION_HASH_AND_ONE_CLOSED_ASSIGNABLE_LEAF_ID","mappingConflictRule":"UNEQUAL_VISIBLE_DISPOSITIONS_DIFFERENT_TARGET_MAPPED_VS_NOT_MAPPED_OR_DIFFERENT_NOT_MAPPED_REASON","mappingCardinality":"EXACTLY_ONE_VISIBLE_VALID_MAPPED_RECORD_FOR_EQUITY","missingMappingResolution":"MAPPING_MISSING_AS_OF","notMappedResolutionByReason":{"NO_CANONICAL_EQUIVALENT":"MAPPING_NOT_MAPPED_NO_CANONICAL_EQUIVALENT","PROVIDER_NODE_TOO_BROAD":"MAPPING_NOT_MAPPED_PROVIDER_NODE_TOO_BROAD","PROVIDER_DEFINITION_UNAVAILABLE":"MAPPING_NOT_MAPPED_PROVIDER_DEFINITION_UNAVAILABLE"},"equalDuplicateRule":"AMBIGUOUS_NO_DEDUPLICATION","candidateOrderRule":"ORDER_INDEPENDENT","knownCandidateSetRule":"ANY_VISIBLE_MISMATCH_FAILS_CLOSED_BEFORE_MULTIPLICITY","forbiddenInference":["TICKER","ISSUER_NAME","CURRENT_MASTER_DATA","CURRENT_ROW","LATEST_REVISION","NEAREST_INTERVAL","PROVIDER_PREFERENCE","RAW_LABEL_MATCH","NORMALIZED_LABEL_MATCH","FUZZY_LABEL_MATCH","SILENT_DEDUPLICATION","P2_MAP_OR_TREEMAP_LABEL","UNKNOWN_NODE","OTHER_NODE","UNCLASSIFIED_NODE","FALLBACK"],"evaluationPrecedence":["CLASSIFICATION_MISSING_AS_OF","CLASSIFICATION_BASIS_MISMATCH","CLASSIFICATION_ASSET_MISMATCH","CLASSIFICATION_EFFECTIVE_INTERVAL_MISMATCH","CLASSIFICATION_AMBIGUOUS","MEMBERSHIP_MISSING_AS_OF_OR_NOT_APPLICABLE","MEMBERSHIP_BASIS_MISMATCH","MEMBERSHIP_ASSET_MISMATCH","MEMBERSHIP_ASSET_TYPE_MISMATCH","MEMBERSHIP_PRIMARY_VENUE_MISMATCH","MEMBERSHIP_PRIMARY_VENUE_COUNTRY_MISMATCH","MEMBERSHIP_CURRENCY_MISMATCH","MEMBERSHIP_EFFECTIVE_INTERVAL_MISMATCH","OUT_OF_SCOPE_MEMBERSHIP_CONFLICT","MEMBERSHIP_AMBIGUOUS","MAPPING_MISSING_AS_OF","MAPPING_SET_ID_MISMATCH","MAPPING_SET_VERSION_MISMATCH","MAPPING_SET_DEFINITION_HASH_MISMATCH","MAPPING_POLICY_VERSION_MISMATCH","MAPPING_POLICY_DEFINITION_HASH_MISMATCH","MAPPING_TAXONOMY_ID_MISMATCH","MAPPING_TAXONOMY_VERSION_MISMATCH","MAPPING_TAXONOMY_DEFINITION_HASH_MISMATCH","MAPPING_PROVIDER_ID_MISMATCH","MAPPING_PROVIDER_SCHEME_ID_MISMATCH","MAPPING_PROVIDER_SCHEME_REVISION_MISMATCH","MAPPING_PROVIDER_NODE_ID_MISMATCH","MAPPING_EFFECTIVE_INTERVAL_MISMATCH","MAPPING_MAPPED_DEFINITION_REQUIRED","MAPPING_CANONICAL_NODE_NOT_ASSIGNABLE","MAPPING_CONFLICT","MAPPING_AMBIGUOUS","MAPPING_NOT_MAPPED_NO_CANONICAL_EQUIVALENT","MAPPING_NOT_MAPPED_PROVIDER_NODE_TOO_BROAD","MAPPING_NOT_MAPPED_PROVIDER_DEFINITION_UNAVAILABLE","RESOLVE"],"selectedEvidencePreservation":"EXACT_COMPLETE_RECORDS","futureEvidenceOutputRule":"NEVER_ECHOED","mappingSetManifestVerification":"ABSENT_CALLER_ATTESTED_SEPARATE_BOUNDARY","lifecycleMapping":"ABSENT","calculatorInvocation":"ABSENT","providerIntegration":"ABSENT","actualProviderMappingSet":"ABSENT","fallbackBehavior":"ABSENT"}
```

The exact UTF-8 byte length and lowercase SHA-256 are:

- Bytes: `9307`
- SHA-256: `52d9f705a3a8a965a6fca79d36bd94ed8836642f1a2c4e5f29a878d0a267311c`

`canonicalDefinitionUtf8()` returns a defensive byte-array copy and every
resolution context echoes this digest. Any changed field, variant, reason,
precedence, inference boundary, or byte requires a new policy version and
digest.

## Source, runtime, and publication boundary

Production adds exactly:

- `SectorAssignmentPolicyVersion.java`
- `SectorAssetClassificationEvidence.java`
- `SectorMembershipEvidence.java`
- `SectorMappingEvidence.java`
- `SectorAssignmentRequest.java`
- `SectorAssignmentResolution.java`
- `SectorAssignmentSelector.java`

The matching source-local test surface contains exactly
`SectorAssignmentSelectorGoldenTest.java` with exactly 134 golden invocations.
Test-only synthetic evidence is not a provider mapping set, canonical fixture,
issuer membership claim, or product data.

No schema, canonical fixture, manifest member, OpenAPI path, Flyway migration,
database behavior, controller, repository, provider adapter, scheduler,
resource, API behavior, or web source is added. The selector performs no
current-state lookup, network read, reference-index assignment, level
selection, return calculation, methodology activation, lifecycle mapping,
persistence, aggregation, ranking, or publication. Existing DEMO benchmark,
sector, alpha, and sector-alpha values remain null.

## External-data and rights boundary

This disconnected policy requires no API key, account, paid plan, domain,
provider license, environment secret, or network access. No actual mapping set
or non-DEMO membership may be created from the synthetic P2 labels or from
unlicensed GICS/ICB, unreviewed SIC/NAICS, ticker, name, or current-profile
data.

Before real mapping or membership evidence enters the system, P5 must select a
provider and document historical classification, storage, display,
derived-crosswalk, cache, and redistribution rights. GICS or ICB use requires
express written commercial rights before any credential is requested. SEC
EDGAR requires no API key, but a future adapter must use a compliant named
`User-Agent` and independently review any SIC-to-WSR mapping. Only after source
and rights approval may scoped credentials be introduced through untracked
local, CI, and deployment secret stores, never chat or Git.

## Verification

- Exact canonical definition extraction, UTF-8 byte length, SHA-256, reason
  order, source surface, marker parity, and repository guard: **PASS**.
- Focused 134-invocation golden and complete API Maven regression:
  **PASS** — 134/134 focused and 1248/1248 full API, with zero failures,
  errors, or skips including Docker/PostgreSQL/Flyway integration.
- Workflow Python bodies, YAML parsing, Compose validation, mutation rejection,
  protected and legacy baseline replay, patch hygiene, and preservation of the
  user-owned `apps/web/next-env.d.ts`: **PASS** — 34/34 embedded Python bodies
  compile, 33/33 locally executable bodies pass, SnakeYAML retains four jobs,
  Compose validates, marker/policy-byte/runtime-cardinality mutations each exit
  nonzero and are restored. The current protected production baseline is
  exactly 202 files with SHA-256
  `b1ae60b9c550353960687cb9973e2909e965a5e3eb98bb23b39b0a7f01a2a899`;
  the current API-test/web baseline is exactly 199 files with SHA-256
  `59726e88e5bf7d831f16beaa693689ca799990d355733a1115c07a285a7e5293`.
  Excluding the exact ADR-029 seven-plus-one surface reproduces ADR-028's
  195-file production baseline
  `562e6402b06c4b549d518b5935d7c6525d795708d135bb4c8dd4af8c674d0640`
  and 198-file test/web baseline
  `0f6c5358ea2564c562159d375b42985e8aafd603b1673fcc404aab83bcf74a0e`.
  The user-owned file remains unstaged and unchanged by this slice.

## Consequences and next work

- Original and correction outcomes can replay independently without floating
  membership or mapping to current or horizon-end state.
- Missing, future, invalid, unsupported, conflicting, ambiguous, and not-mapped
  evidence cannot become a taxonomy node, zero, fallback, or calculated return.
- A resolved WSR node does not prove a provider-published sector price index.
  Independent benchmark/sector reference-level pairs, divisor continuity,
  exact same-currency price-return calculators, readiness, alpha, persistence,
  API/UI exposure, and provider integration remain separately reviewed work.
