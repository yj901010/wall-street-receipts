# ADR-032 — Signed Sector Reference Return

- Status: Accepted
- Date: 2026-08-24

## Context

ADR-026 requires benchmark and sector returns to remain independently typed
and to use ADR-017's exact signed-return arithmetic. ADR-030 supplies the
sector leg as one complete point-in-time resolution. Its `Resolved` branch
contains the selected provider-published sector price-index binding, exact
basis and endpoint levels, exact divisor-continuity evidence, and all upstream
assignment and endpoint receipts. Its unavailable branches preserve their
complete typed contexts and reasons. ADR-030 deliberately performs no return
calculation.

Extracting two free-standing numbers from that receipt would sever the
evidence chain and allow calculation from another policy, an unavailable
branch, a provider return field, or a reconstructed interval. Reusing or
casting the asset-return or ADR-031 benchmark-return types would also collapse
distinct economic semantics.

ADR-032 calculates a signed sector price-index return from one complete ADR-030 sector reference-level-pair receipt using the exact basis-level denominator.

## Decision

The twenty-fifth disconnected P3 slice adds exactly four production types in
`com.wallstreetreceipts.api.domain.outcome.sectorreturn` and one matching
source-local golden test:

- `SectorReturnPolicyVersion`;
- `SectorReturnInput`;
- `SectorReturnResult`;
- `SectorReturnCalculator`; and
- `SectorReturnCalculatorGoldenTest`.

`SectorReturnInput` accepts only
`SIGNED_SECTOR_BASIS_LEVEL_DENOMINATOR_SCALE_12_HALF_EVEN_V1` and one complete
`SectorReferenceLevelPairResolution` using ADR-030 policy
`POINT_IN_TIME_EXACT_SECTOR_PRICE_INDEX_LEVEL_PAIR_V1` with definition hash
`4224648ba01104fd3e96319158c7d6b42da472e9aa6f2ab22ef9fccf43da7e4a`.
There is no free-standing level, reference identity, assignment, endpoint,
horizon, basis, currency, venue, cutoff, or provider-return input.

### Exact branch preservation

`SectorReturnResult` is sealed as exactly six variants:

1. `Available(context,sectorReturn)` for a resolved pair and locally
   representable calculation;
2. `NotApplicable(context)` for exact upstream intentional non-applicability;
3. `AssignmentUnavailable(context)` for the exact upstream assignment branch;
4. `EndpointAnchorUnavailable(context)` for the exact upstream anchor branch;
5. `EvidenceUnavailable(context)` for the exact upstream reference-evidence
   branch; and
6. `OutputUnavailable(context,reason)` for a resolved pair whose output cannot
   satisfy the numeric boundary. Its only reason is
   `OUTPUT_NOT_REPRESENTABLE`.

Every context preserves the policy identity and the complete, same supplied
ADR-030 resolution. Nested assignment, anchor, and local evidence reasons stay
inside that exact typed receipt; they are not copied, mapped, duplicated, or
flattened. Branch precedence is N/A, assignment unavailable, endpoint-anchor
unavailable, evidence unavailable, calculation, then output representability.
No arithmetic is attempted for a non-resolved pair.

Public result constructors attest only the local policy/hash, exact source
variant, receipt presence, and output shape. Only
`SectorReturnCalculator.calculate` attests that the required formula and
rounding operation were actually performed.

### Exact arithmetic and numeric boundary

For a resolved receipt, the calculator reads only
`basisLevelObservation.level` and `endpointLevelObservation.level`. It computes
exactly:

```text
(endpoint - basis) / basis
```

There is exactly one exact subtraction followed by exactly one division at
scale 12 with `RoundingMode.HALF_EVEN`. The positive basis level is the sole
denominator. There is no operand normalization, intermediate or second
rounding, `MathContext`, percent conversion, tolerance, float/double
conversion, alternate denominator, provider-supplied return, or fallback.

The output is a signed decimal ratio with exact scale 12 and precision at most
38. Because both input levels are positive, the mathematical result is greater
than -1, while a rounded exact `-1.000000000000` is valid. A directly
constructed value below -1 fails closed. Arithmetic failure or a rounded value
outside signed `NUMERIC(38,12)` produces
`OutputUnavailable(OUTPUT_NOT_REPRESENTABLE)` rather than clipping, zero, or
an approximate result.

## Canonical policy definition

`SectorReturnPolicyVersion` contains only
`SIGNED_SECTOR_BASIS_LEVEL_DENOMINATOR_SCALE_12_HALF_EVEN_V1`. Its definition
is exactly the following single-line 2817-byte ASCII sequence encoded directly
as UTF-8, with no byte-order mark, surrounding whitespace, or trailing line
ending:

```text
{"policyVersion":"SIGNED_SECTOR_BASIS_LEVEL_DENOMINATOR_SCALE_12_HALF_EVEN_V1","requiredReferenceLevelPairPolicyVersion":"POINT_IN_TIME_EXACT_SECTOR_PRICE_INDEX_LEVEL_PAIR_V1","requiredReferenceLevelPairPolicyDefinitionHash":"4224648ba01104fd3e96319158c7d6b42da472e9aa6f2ab22ef9fccf43da7e4a","referenceLevelPairInput":"SECTOR_REFERENCE_LEVEL_PAIR_RESOLUTION","resultContextFields":["policyVersion","policyDefinitionHash","referenceLevelPairResolution"],"resultVariants":{"Available":["context","sectorReturn"],"NotApplicable":["context"],"AssignmentUnavailable":["context"],"EndpointAnchorUnavailable":["context"],"EvidenceUnavailable":["context"],"OutputUnavailable":["context","reason"]},"outputUnavailableReasons":["OUTPUT_NOT_REPRESENTABLE"],"branchMapping":{"Resolved":"CALCULATE","NotApplicable":"NotApplicable","AssignmentUnavailable":"AssignmentUnavailable","EndpointAnchorUnavailable":"EndpointAnchorUnavailable","EvidenceUnavailable":"EvidenceUnavailable"},"nestedReasonRule":"PRESERVE_COMPLETE_PAIR_RECEIPT_NO_MAPPING_DUPLICATION_OR_FLATTENING","evaluationPrecedence":["REFERENCE_PAIR_NOT_APPLICABLE","REFERENCE_PAIR_ASSIGNMENT_UNAVAILABLE","REFERENCE_PAIR_ENDPOINT_ANCHOR_UNAVAILABLE","REFERENCE_PAIR_EVIDENCE_UNAVAILABLE","CALCULATE","OUTPUT_NOT_REPRESENTABLE"],"formula":"(endpoint-basis)/basis","operandSources":{"basis":"RESOLVED_BASIS_LEVEL_OBSERVATION_LEVEL","endpoint":"RESOLVED_ENDPOINT_LEVEL_OBSERVATION_LEVEL"},"numerator":"ENDPOINT_MINUS_BASIS_REFERENCE_LEVEL","denominator":"BASIS_REFERENCE_LEVEL","subtractionCount":1,"divisionCount":1,"divisionScale":12,"roundingMode":"HALF_EVEN","operationOrder":["SUBTRACT_BASIS_FROM_ENDPOINT_EXACTLY","DIVIDE_NUMERATOR_BY_BASIS_AT_SCALE_12_HALF_EVEN"],"intermediateRounding":"ABSENT","secondRounding":"ABSENT","outputUnits":"SIGNED_DECIMAL_RATIO","inputBoundary":"POSITIVE_NUMERIC_38_12_PROVIDER_PUBLISHED_PRICE_INDEX_LEVEL_PAIR","outputBoundary":"SIGNED_NUMERIC_38_12_AT_LEAST_NEGATIVE_ONE","roundedNegativeOneBoundary":"VALID","outputOverflowReason":"OUTPUT_NOT_REPRESENTABLE","percentConversion":"ABSENT","floatOrDoubleConversion":"ABSENT","providerReturnFieldUse":"ABSENT","assetReturnResultReuse":"FORBIDDEN","benchmarkReturnResultReuse":"FORBIDDEN","resultContext":"POLICY_IDENTITY_AND_COMPLETE_SECTOR_REFERENCE_LEVEL_PAIR_RESOLUTION_ONLY","constructorAttestation":"LOCAL_POLICY_PAIR_VARIANT_AND_OUTPUT_SHAPE_ONLY","sourceAttestationBoundary":"ONLY_SECTOR_RETURN_CALCULATOR_ATTESTS_FORMULA_AND_REQUIRED_ROUNDING","calculationAuthority":"DETERMINISTIC_SOURCE_LOCAL_PURE_LEAF","reflectionOrClassTokenUse":"ABSENT","clockLocaleTimezoneRandomEnvironmentDependence":"ABSENT","readiness":"ABSENT","lifecycleMapping":"ABSENT","methodologyPersistencePublication":"ABSENT","providerIntegration":"ABSENT","runtimeWiring":"ABSENT","fallbackBehavior":"ABSENT"}
```

Its lowercase SHA-256 is exactly
`5aecd42c32ba69f0d21ab6e1ee1e3128cd31584724a6f46e176acd470204d0f7`.
Returned bytes are defensive, and every calculation context echoes the exact
digest. Any changed input identity, variant, operation, scale, rounding,
boundary, receipt rule, or byte requires a new policy version and digest.

## Dependency and publication boundary

Production depends only on ordinary JDK value types and the independently
typed ADR-030 sector reference-level-pair contract. It does not import or cast
asset-return, asset price-pair, benchmark reference-pair, benchmark-return,
assignment, taxonomy, endpoint-observation, horizon, lifecycle, persistence,
API, or web types. It uses no reflection, class token, shared generic return
helper, clock, locale, time zone, randomness, or environment state.

This pure leaf invokes no selector, readiness resolver, methodology activation,
fingerprint, lineage, outcome status, persistence, aggregation, ranking, or
publication. No schema, fixture, manifest member, JSON mapping, OpenAPI path,
Flyway migration, database behavior, controller, repository, provider adapter,
scheduler, resource, API behavior, or web source is added. Existing DEMO
`benchmarkReturn`, `sectorReturn`, `alpha`, and `sectorAlpha` values remain
null.

## External-data boundary

No API key, account, paid plan, provider license, named secret, or network
access is needed for this disconnected deterministic calculation, and no
credential exists for this slice. A credential would prove access only, not
permitted use. Before non-DEMO use, P5 must approve the exact sector-index
product/feed; rights to create and use the exact WSR canonical-node-to-selected
provider-published sector price-index binding; exact-time historical levels and
revisions; reference-calendar identity, revision, and source; divisor and
methodology continuity evidence; and storage, cache, display, derived-return,
and redistribution rights. If publisher and redistributor differ, both parties'
products and rights require independent review. Assignment or classification
rights alone do not grant sector-index rights. Only after provider/product and
written-rights approval may a reviewed adapter introduce a scoped credential
through untracked local, CI, and deployment secret stores, never chat or Git.

## Verification

- Exact policy extraction is 2817 UTF-8 bytes with SHA-256
  `5aecd42c32ba69f0d21ab6e1ee1e3128cd31584724a6f46e176acd470204d0f7`.
  The protected production baseline is 224 files / SHA-256
  `bc31bb72f14289e6a8b3c344e356f900a2d23a9fb9efd48ce935586c0e336055`.
  Excluding the exact four ADR-032 production files reproduces ADR-031 at 220
  files / `cb8532a4020c76a9ed2fd4a61fbb5844717dc23c7f27d90510e603c0bee1f5e9`.
- Focused `SectorReturnCalculatorGoldenTest` passes 112/112 with zero failures,
  errors, or skips; its normalized source SHA-256 is
  `6047b29c8c338893bf2fdeaa9a5fef83ec20cb4f5e11acb77d82b48d8752b129`.
  API-test/web is 203 files /
  `5f95c2b844af16224815b1b4025b52b9c25b7822d4fa53b8f8d93788805f28ce`;
  exact golden exclusion reproduces ADR-031 at 202 /
  `12b03e7a48a0e6c3e676da9b335c4c270e8dc50bea2402aa25f6462db07bb273`:
  **PASS**.
- Full API Maven verification passes 1875/1875 with zero failures, errors, or
  skips and `BUILD SUCCESS`, including Testcontainers PostgreSQL 17.10 and
  Flyway: **PASS**.
- The dedicated ADR-032 guard and 112/112 runtime gate pass. All 37/37 workflow
  Python heredoc bodies syntax-compile and all 30/30 locally runnable bodies
  pass. Six `jsonschema`/`referencing` bodies remain syntax-only because those
  modules are absent from the bundled runtime; the final cross-stack body is
  syntax-only by design. SnakeYAML 2.5 parses exactly four jobs and Compose
  config validates: **PASS**.
- Deliberate README-marker, canonical `percentConversion`, and runtime expected
  count 112-to-111 mutations each fail their guard and are exactly restored.
  Web lint, 569/569 Vitest tests, and the production build pass. Independent
  reviews report no remaining P0/P1/P2 finding, marker parity is one per
  document, `git diff --check` is clean, and the user-owned
  `apps/web/next-env.d.ts` is restored to its exact pre-build SHA-256
  `7ad303e40d4fddf44f156129e397511953a71481c5cfd86b1862649aaaf240cc`:
  **PASS**.

## Consequences and next work

- A resolved sector reference-level pair can produce one deterministic,
  replayable price-return leaf without weakening its source receipt.
- Source-local comparative readiness is the next independent slice now that
  both return calculators exist. It must not be inferred from either return
  result alone and remains separately gated.
- Lifecycle composition, methodology activation and fingerprint, lineage,
  persistence, API/UI publication, canonical evidence, raw-window coverage,
  MFE/MAE, alpha, and sector alpha remain later reviewed work. Alpha and sector
  alpha remain last.
