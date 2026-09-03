# ADR-028 — Provider-Neutral WSR Economic Activity Taxonomy V1

- Status: Accepted
- Date: 2026-08-24

## Context

ADR-026 requires a provider-neutral, versioned WSR sector taxonomy before any
sector-assignment code exists. ADR-027 implements only benchmark assignment.
The repository still has no canonical sector identity, provider crosswalk,
point-in-time sector membership, sector reference index, or sector return.

The P2 map and treemap labels are synthetic DEMO presentation groupings. They
carry no provider scheme, node code, definition, revision, effective interval,
provider event, or redistribution right and therefore cannot become canonical
taxonomy or membership evidence.

GICS and ICB are externally owned classification products. Publicly visible
methodology material does not grant WSR the right to copy their codes,
definitions, hierarchy, issuer assignments, or derived crosswalks. Product
approval has instead been received for the original, single-level WSR taxonomy
and provider-mapping policy below.

## Decision

ADR-028 locks WSR Economic Activity V1 and exact point-in-time provider-node mapping semantics.

This remains a decision-only slice. It creates canonical decision bytes and
digests but no Java type, selector, provider mapping set, assignment, schema,
fixture, API, database, provider adapter, reference index, return, or web
behavior.

## Canonical taxonomy identity

- Taxonomy ID is exactly `wsr-economic-activity`.
- Taxonomy version is exactly `1.0.0`.
- V1 is one provider-neutral root with exactly twelve assignable leaves. It is
  not an industry hierarchy and does not claim GICS, ICB, SIC, or NAICS
  equivalence.
- Assignment meaning is exactly
  `PRIMARY_OPERATING_ACTIVITY_AS_EXPLICITLY_EVIDENCED`.
- The root `wsr-sector-root` is organizational only and cannot be assigned.
- There is no `UNKNOWN`, `OTHER`, or `Unclassified` node. Missing, conflicting,
  ambiguous, future, or unmapped evidence is typed evidence unavailability,
  never a taxonomy member.
- `wsr-sector-diversified-operations` is not a fallback. It requires explicit
  source evidence that no other V1 node represents one primary operating
  activity.

The closed assignable leaf order and meanings are:

| Node ID | Label | Original WSR definition |
| --- | --- | --- |
| `wsr-sector-digital-systems` | Digital Systems | Organizations whose primary operating activity is computing hardware, semiconductors, software, data infrastructure, or automation technology. |
| `wsr-sector-connectivity-media` | Connectivity and Media | Organizations whose primary operating activity is communications networks, connectivity services, publishing, entertainment, advertising, or interactive media. |
| `wsr-sector-health-bioscience` | Health and Bioscience | Organizations whose primary operating activity is health care delivery, medicines, biotechnology, diagnostics, medical devices, or life-science tools. |
| `wsr-sector-financial-risk-services` | Financial and Risk Services | Organizations whose primary operating activity is banking, payments, insurance, securities, investment services, lending, or financial-market infrastructure. |
| `wsr-sector-consumer-essentials` | Consumer Essentials | Organizations whose primary operating activity is food, beverages, household or personal necessities, or recurring distribution and retail of those necessities. |
| `wsr-sector-consumer-choice-commerce` | Consumer Choice and Commerce | Organizations whose primary operating activity is discretionary goods or services, travel, leisure, restaurants, vehicles, or general commerce. |
| `wsr-sector-production-mobility` | Production and Mobility | Organizations whose primary operating activity is industrial equipment, engineering, aerospace, production services, logistics, or transportation. |
| `wsr-sector-energy-systems` | Energy Systems | Organizations whose primary operating activity is producing, processing, or transporting fuels or power-generation inputs, or supplying energy equipment and services. |
| `wsr-sector-materials-resource-processing` | Materials and Resource Processing | Organizations whose primary operating activity is extracting or processing non-energy raw materials, chemicals, construction inputs, packaging, paper, or metals. |
| `wsr-sector-essential-networks` | Essential Networks | Organizations whose primary operating activity is operating regulated or contracted electricity, gas, water, or waste networks. |
| `wsr-sector-property-built-environment` | Property and Built Environment | Organizations whose primary operating activity is owning, developing, leasing, managing, or servicing real property and the built environment. |
| `wsr-sector-diversified-operations` | Diversified Operations | Organizations explicitly evidenced as having materially diversified operating activities with no single primary activity represented by another V1 node. |

### Canonical taxonomy definition

The following is one exact single-line ASCII sequence encoded as UTF-8, with
no byte-order mark, surrounding whitespace, or trailing line ending:

```text
{"taxonomyId":"wsr-economic-activity","taxonomyVersion":"1.0.0","taxonomyKind":"PROVIDER_NEUTRAL_SINGLE_LEVEL_ECONOMIC_ACTIVITY","assignmentCriterion":"PRIMARY_OPERATING_ACTIVITY_AS_EXPLICITLY_EVIDENCED","root":{"nodeId":"wsr-sector-root","label":"WSR Economic Activity","assignable":false},"leafNodes":[{"nodeId":"wsr-sector-digital-systems","label":"Digital Systems","definition":"Organizations whose primary operating activity is computing hardware, semiconductors, software, data infrastructure, or automation technology."},{"nodeId":"wsr-sector-connectivity-media","label":"Connectivity and Media","definition":"Organizations whose primary operating activity is communications networks, connectivity services, publishing, entertainment, advertising, or interactive media."},{"nodeId":"wsr-sector-health-bioscience","label":"Health and Bioscience","definition":"Organizations whose primary operating activity is health care delivery, medicines, biotechnology, diagnostics, medical devices, or life-science tools."},{"nodeId":"wsr-sector-financial-risk-services","label":"Financial and Risk Services","definition":"Organizations whose primary operating activity is banking, payments, insurance, securities, investment services, lending, or financial-market infrastructure."},{"nodeId":"wsr-sector-consumer-essentials","label":"Consumer Essentials","definition":"Organizations whose primary operating activity is food, beverages, household or personal necessities, or recurring distribution and retail of those necessities."},{"nodeId":"wsr-sector-consumer-choice-commerce","label":"Consumer Choice and Commerce","definition":"Organizations whose primary operating activity is discretionary goods or services, travel, leisure, restaurants, vehicles, or general commerce."},{"nodeId":"wsr-sector-production-mobility","label":"Production and Mobility","definition":"Organizations whose primary operating activity is industrial equipment, engineering, aerospace, production services, logistics, or transportation."},{"nodeId":"wsr-sector-energy-systems","label":"Energy Systems","definition":"Organizations whose primary operating activity is producing, processing, or transporting fuels or power-generation inputs, or supplying energy equipment and services."},{"nodeId":"wsr-sector-materials-resource-processing","label":"Materials and Resource Processing","definition":"Organizations whose primary operating activity is extracting or processing non-energy raw materials, chemicals, construction inputs, packaging, paper, or metals."},{"nodeId":"wsr-sector-essential-networks","label":"Essential Networks","definition":"Organizations whose primary operating activity is operating regulated or contracted electricity, gas, water, or waste networks."},{"nodeId":"wsr-sector-property-built-environment","label":"Property and Built Environment","definition":"Organizations whose primary operating activity is owning, developing, leasing, managing, or servicing real property and the built environment."},{"nodeId":"wsr-sector-diversified-operations","label":"Diversified Operations","definition":"Organizations explicitly evidenced as having materially diversified operating activities with no single primary activity represented by another V1 node."}],"closedNodeSet":true,"assignableLeafCount":12,"unknownNode":"ABSENT","otherNode":"ABSENT","unclassifiedNode":"ABSENT","missingConflictAmbiguityRule":"EVIDENCE_UNAVAILABLE_NOT_A_NODE","diversifiedOperationsRule":"EXPLICIT_SOURCE_EVIDENCE_OF_NO_SINGLE_PRIMARY_ACTIVITY_ONLY","industryHierarchy":"ABSENT_V1","gicsEquivalenceClaim":"ABSENT","icbEquivalenceClaim":"ABSENT","providerAssignment":"ABSENT","referenceIndexAssignment":"ABSENT","p2SyntheticLabels":"FORBIDDEN_AS_TAXONOMY_OR_MEMBERSHIP_EVIDENCE","changeRule":"ANY_SEMANTIC_OR_NODE_CHANGE_REQUIRES_NEW_VERSION_AND_HASH"}
```

Its exact UTF-8 byte length and lowercase SHA-256 are calculated and locked by
repository CI:

- Bytes: `3824`
- SHA-256: `820ce3ea264d67312fe4f2efe346631a81d74248e9a7f041793d65d8ef0d62ae`

## Exact provider-node mapping policy

The mapping policy version is exactly
`POINT_IN_TIME_EXPLICIT_PROVIDER_NODE_TO_WSR_ECONOMIC_ACTIVITY_V1`.

Every future mapping row preserves these fields in this exact order:
`mappingEvidenceId`, `providerEventId`, `mappingPolicyVersion`,
`mappingPolicyDefinitionHash`, `mappingSetId`, `mappingSetVersion`,
`mappingSetDefinitionHash`, `taxonomyId`, `taxonomyVersion`,
`taxonomyDefinitionHash`, `providerId`, `providerSchemeId`,
`providerSchemeRevision`, `providerNodeId`, `providerNodeLabel`,
`providerNodeDefinition`, `mappingDisposition`, `mappingSourceId`,
`mappingSourceRevision`, `provenanceId`, `effectiveInterval`, `availableAt`,
and `capturedAt`.

### Identity and disposition

- Provider-node identity is exactly `(providerId, providerSchemeId,
  providerSchemeRevision, providerNodeId)`, compared by case-sensitive,
  unnormalized Unicode code-point equality. The recorded provider label and
  definition are evidence, not a matching key.
- Provider-node definition is either `Recorded(value, languageTag)` or
  `NotPublished`. `Mapped(canonicalNodeId)` requires a recorded definition.
- Mapping disposition is exactly `Mapped(canonicalNodeId)` or
  `NotMapped(reason)`. Closed not-mapped reasons are
  `NO_CANONICAL_EQUIVALENT`, `PROVIDER_NODE_TOO_BROAD`, and
  `PROVIDER_DEFINITION_UNAVAILABLE`.
- A mapped target must carry the exact taxonomy ID, version, and definition
  hash above, and `canonicalNodeId` must be one of the twelve closed assignable
  leaf IDs. The unassignable root and every unknown ID fail closed.
- Many provider nodes may map to one WSR node. One provider-node identity may
  have only one disposition for the applicable effective interval.
- A missing mapping or `NotMapped` is evidence unavailable. Equal duplicates
  remain ambiguous without deduplication. Different visible canonical targets
  are a conflict. Overlapping visible rows for the same identity are
  ambiguous, or conflicting when their dispositions disagree.

### Point-in-time and effective interval

- Mapping evidence must satisfy `availableAt <= capturedAt`.
- A row is visible only when `availableAt <= evaluationAsOf` and
  `capturedAt <= evaluationAsOf`. Future evidence is identical to absence and
  cannot affect output, reasons, conflicts, or cardinality.
- The selected interval is start-inclusive/end-exclusive at the exact
  `OutcomeBasis.eventTime`: `startsAtInclusive <= basis.eventTime` and either
  an explicit `OpenEnded` or `basis.eventTime < endsAtExclusive`.
- The reusable mapping row does not duplicate `OutcomeBasis`. The later sector
  assignment request and result own the complete original/correction basis and
  freeze both asset membership and mapping at its event time.
- Raw, normalized, or fuzzy label matching, ticker/name inference, current or
  latest rows, nearest intervals, provider preference, silent deduplication,
  automatic migration, P2 labels, and fallback are forbidden.

### Future mapping-set canonicalization

No provider or actual mapping set is selected in this decision. A future
provider mapping set must use a unique ID and version, preserve its source and
rights, and hash canonical single-line UTF-8 JSON without a BOM, surrounding
whitespace, or trailing line ending. The canonical manifest fields are exactly
mapping-set ID/version/hash, mapping-policy version/hash, taxonomy
ID/version/hash, provider/scheme/revision, mapping source/revision, provenance,
and entries. Manifest fields and entry fields use their declared order.
Entries sort by case-sensitive, unnormalized Unicode code-point lexicographic
order over provider ID, provider scheme ID, provider scheme revision, provider
node ID, canonical UTC microsecond effective start, and mapping evidence ID.
Every mapping evidence ID is unique within the set, so the final sort key cannot
tie. Every occurrence of `mappingSetDefinitionHash` is omitted from the
manifest and entries while calculating SHA-256, avoiding a self-reference.
Each entry must equal its manifest for mapping-set ID/version, mapping-policy
version/hash, taxonomy ID/version/hash, provider/scheme/revision, mapping
source/revision, and provenance. After hashing, every populated
`mappingSetDefinitionHash` occurrence must equal the computed digest. Any other
mapping byte change requires a new mapping-set version and hash; mappings never
migrate silently.

### Canonical mapping-policy definition

The following is one exact single-line ASCII sequence encoded as UTF-8, with
no byte-order mark, surrounding whitespace, or trailing line ending:

```text
{"mappingPolicyVersion":"POINT_IN_TIME_EXPLICIT_PROVIDER_NODE_TO_WSR_ECONOMIC_ACTIVITY_V1","taxonomyId":"wsr-economic-activity","taxonomyVersion":"1.0.0","requiredTaxonomyDefinitionHash":"820ce3ea264d67312fe4f2efe346631a81d74248e9a7f041793d65d8ef0d62ae","canonicalTargetIdentity":["taxonomyId","taxonomyVersion","taxonomyDefinitionHash","canonicalNodeId"],"canonicalNodeRule":"MAPPED_TARGET_MUST_BE_ONE_CLOSED_ASSIGNABLE_LEAF_ID","mappingEvidenceFields":["mappingEvidenceId","providerEventId","mappingPolicyVersion","mappingPolicyDefinitionHash","mappingSetId","mappingSetVersion","mappingSetDefinitionHash","taxonomyId","taxonomyVersion","taxonomyDefinitionHash","providerId","providerSchemeId","providerSchemeRevision","providerNodeId","providerNodeLabel","providerNodeDefinition","mappingDisposition","mappingSourceId","mappingSourceRevision","provenanceId","effectiveInterval","availableAt","capturedAt"],"providerNodeDefinitionVariants":{"Recorded":["value","languageTag"],"NotPublished":[]},"mappingDispositionVariants":{"Mapped":["canonicalNodeId"],"NotMapped":["reason"]},"notMappedReasons":["NO_CANONICAL_EQUIVALENT","PROVIDER_NODE_TOO_BROAD","PROVIDER_DEFINITION_UNAVAILABLE"],"effectiveIntervalFields":["startsAtInclusive","end"],"effectiveIntervalEndVariants":{"OpenEnded":[],"EndsAtExclusive":["value"]},"mappingIdentity":["providerId","providerSchemeId","providerSchemeRevision","providerNodeId"],"mappingIdentityComparison":"EXACT_CASE_SENSITIVE_UNNORMALIZED_UNICODE_CODE_POINT_EQUALITY","providerLabelRole":"PRESERVED_EVIDENCE_ONLY_NOT_IDENTITY_OR_MATCH_KEY","mappedDefinitionRequirement":"RECORDED_PROVIDER_NODE_DEFINITION","mappingCardinality":"MANY_PROVIDER_NODES_TO_ONE_CANONICAL_NODE_ALLOWED_ONE_DISPOSITION_PER_IDENTITY_AND_EFFECTIVE_INTERVAL","evidenceTemporalRule":"availableAt<=capturedAt","pitPredicate":"availableAt<=evaluationAsOf&&capturedAt<=evaluationAsOf","futureEvidenceRule":"INVISIBLE_TO_OUTPUT_REASON_CONFLICT_AND_CARDINALITY","effectiveIntervalPredicate":"startsAtInclusive<=basis.eventTime&&(end==OpenEnded||basis.eventTime<end.value)","effectiveIntervalBoundary":"START_INCLUSIVE_END_EXCLUSIVE","openEndedRepresentation":"EXPLICIT_OPEN_ENDED_VARIANT","outcomeBasisOwnership":"SECTOR_ASSIGNMENT_REQUEST_AND_RESULT_ONLY","missingMappingResolution":"EVIDENCE_UNAVAILABLE","notMappedResolution":"EVIDENCE_UNAVAILABLE","equalDuplicateRule":"MAPPING_AMBIGUOUS_NO_DEDUPLICATION","differentTargetRule":"MAPPING_CONFLICT","overlappingVisibleRowsRule":"MAPPING_AMBIGUOUS_UNLESS_DISPOSITIONS_DISAGREE_THEN_MAPPING_CONFLICT","mappingSetManifestFields":["mappingSetId","mappingSetVersion","mappingSetDefinitionHash","mappingPolicyVersion","mappingPolicyDefinitionHash","taxonomyId","taxonomyVersion","taxonomyDefinitionHash","providerId","providerSchemeId","providerSchemeRevision","mappingSourceId","mappingSourceRevision","provenanceId","entries"],"mappingEvidenceIdRule":"GLOBALLY_UNIQUE_WITHIN_MAPPING_SET","mappingSetEntrySort":["providerId","providerSchemeId","providerSchemeRevision","providerNodeId","effectiveInterval.startsAtInclusive","mappingEvidenceId"],"mappingSetSortComparison":"EXACT_CASE_SENSITIVE_UNNORMALIZED_UNICODE_CODE_POINT_LEXICOGRAPHIC_ORDER","effectiveStartRepresentation":"CANONICAL_UTC_INSTANT_MICROSECOND_PRECISION","mappingSetCanonicalization":"SINGLE_LINE_UTF8_JSON_NO_BOM_NO_SURROUNDING_WHITESPACE_NO_TRAILING_LINE_ENDING_OBJECT_FIELDS_IN_DECLARED_ORDER","mappingSetHashAlgorithm":"SHA-256","mappingSetHashInput":"MANIFEST_AND_ENTRIES_WITH_EVERY_MAPPING_SET_DEFINITION_HASH_FIELD_OMITTED","mappingSetEntryManifestCorrelation":"EACH_ENTRY_MAPPING_SET_ID_VERSION_POLICY_VERSION_HASH_TAXONOMY_ID_VERSION_HASH_PROVIDER_ID_SCHEME_ID_REVISION_MAPPING_SOURCE_ID_REVISION_AND_PROVENANCE_ID_MUST_EQUAL_MANIFEST","mappingSetDefinitionHashRule":"EVERY_POPULATED_OCCURRENCE_MUST_EQUAL_COMPUTED_SHA_256","mappingSetChangeRule":"ANY_OTHER_MAPPING_BYTE_CHANGE_REQUIRES_NEW_VERSION_AND_HASH_NO_SILENT_MIGRATION","forbiddenInference":["RAW_LABEL_MATCH","NORMALIZED_LABEL_MATCH","FUZZY_LABEL_MATCH","TICKER","ISSUER_NAME","CURRENT_ROW","LATEST_REVISION","NEAREST_INTERVAL","PROVIDER_PREFERENCE","SILENT_DEDUPLICATION","P2_MAP_OR_TREEMAP_LABEL","FALLBACK"],"providerMappingSet":"ABSENT_UNTIL_PROVIDER_SELECTION_AND_RIGHTS_APPROVAL","sectorAssignment":"ABSENT","providerIntegration":"ABSENT","referenceIndexAssignment":"ABSENT","lifecycleMapping":"ABSENT"}
```

Its exact UTF-8 byte length and lowercase SHA-256 are calculated and locked by
repository CI:

- Bytes: `4395`
- SHA-256: `ba12a277d5ffe266af1745b98948a1e2206494ac31904f31a419d973d5067e77`

## Source and licensing boundary

- GICS remains isolated unless an S&P/MSCI agreement expressly permits the
  required historical classification, storage, derived crosswalk, display,
  cache, and redistribution uses. Public terms do not establish those rights:
  <https://www.spglobal.com/en/terms-of-use> and
  <https://www.msci.com/legal/terms-of-use>.
- ICB remains isolated unless a FTSE/LSEG agreement grants the same uses. Its
  official attribution terms state that ICB rights belong to FTSE or its
  licensors and prohibit further distribution without express written
  consent: <https://www.lseg.com/content/dam/ftse-russell/en_us/documents/other/ftse-icb-attribution.pdf>.
- SEC SIC is a possible future public issuer-classification evidence source,
  not a mapping already present here. The official list explains its EDGAR
  business-type role: <https://www.sec.gov/search-filings/standard-industrial-classification-sic-code-list>.
  SEC data APIs require no authentication or API key, but automated use must
  follow SEC access policy: <https://www.sec.gov/search-filings/edgar-application-programming-interfaces>.
- NAICS may inform an independently reviewed mapping but does not directly
  prove issuer membership because its stated purpose is establishment-based
  statistical classification. Version and many-to-many concordance ambiguity
  must remain explicit: <https://www.census.gov/naics/> and
  <https://www.census.gov/naics/concordances/concordances.html>.

## Runtime, publication, and external-data boundary

- No canonical provider mapping row, mapping-set manifest, issuer sector
  membership, or provider-published sector price index exists in this slice.
- No provider label, current profile, UI hierarchy, ticker, SIC, NAICS, GICS,
  or ICB value assigns an asset merely because its text resembles a WSR label.
- The current DEMO `sectorReturn`, `sectorAlpha`, benchmark/alpha siblings, and
  all P2 synthetic presentation data remain unchanged and non-observed.
- A later sector-assignment slice must add independently typed membership and
  mapping evidence, exact PIT selection, typed applicability/unavailability,
  a canonical executable policy/hash, and a golden matrix. It must not combine
  benchmark and sector assignment.
- A WSR node alone does not prove a usable sector reference. A later return
  still requires an explicitly assigned provider-published sector price index;
  absent exact reference coverage leaves the sector return unavailable.

This decision needs no API key, account, paid plan, domain, provider license,
environment secret, or network access. Before real provider data is connected,
P5 must select the provider and document historical-data, storage, display,
derived-crosswalk, cache, and redistribution rights. Only then may a reviewed
adapter introduce a named scoped credential through untracked local, CI, and
deployment secret stores, never chat or Git.

## Implementation order

1. Lock this ADR, README, P3 acceptance, implementation log, and repository CI
   while preserving every production/test/schema/fixture/web baseline.
2. In a separate reviewed slice, implement basis-frozen sector assignment
   against these exact taxonomy and mapping-policy identities.
3. Select and license or approve an actual provider before creating any
   provider mapping set or non-DEMO membership evidence.
4. Add benchmark and sector reference-level pairs, deterministic return
   calculators, and source-local readiness in the ADR-026 order. Keep alpha and
   sector alpha deferred until their prerequisite returns are independently
   available.
