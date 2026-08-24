# ADR-031 — Signed Benchmark Reference Return

- Status: Accepted
- Date: 2026-08-24

## Context

ADR-026 requires benchmark and sector returns to remain independently typed
and to use ADR-017's exact signed-return arithmetic. ADR-030 now supplies the
benchmark leg as one complete point-in-time resolution. Its `Resolved` branch
contains the selected provider-published price-index binding, exact basis and
endpoint levels, exact divisor-continuity evidence, and all upstream assignment
and endpoint receipts; unavailable branches preserve their complete typed
contexts and reasons. ADR-030 deliberately performs no return calculation.

Extracting two free-standing numbers from that receipt would lose the evidence
chain and make it possible to calculate from a different policy, an unavailable
branch, a provider return field, or a reconstructed interval. Reusing or casting
the asset-return or future sector-return types would also collapse distinct
economic semantics.

ADR-031 calculates a signed benchmark price-index return from one complete ADR-030 benchmark reference-level-pair receipt using the exact basis-level denominator.

## Decision

The twenty-fourth disconnected P3 slice adds exactly four production types in
`com.wallstreetreceipts.api.domain.outcome.benchmarkreturn` and one matching
source-local golden test:

- `BenchmarkReturnPolicyVersion`;
- `BenchmarkReturnInput`;
- `BenchmarkReturnResult`;
- `BenchmarkReturnCalculator`; and
- `BenchmarkReturnCalculatorGoldenTest`.

`BenchmarkReturnInput` accepts only
`SIGNED_BENCHMARK_BASIS_LEVEL_DENOMINATOR_SCALE_12_HALF_EVEN_V1` and one
complete `BenchmarkReferenceLevelPairResolution` using ADR-030 policy
`POINT_IN_TIME_EXACT_BENCHMARK_PRICE_INDEX_LEVEL_PAIR_V1` with definition hash
`2394b535c1061d32c647504a303b6f1e4ec2fe88e6017d9ff335d12087a5f73d`.
There is no free-standing level, reference identity, assignment, endpoint,
horizon, basis, currency, venue, cutoff, or provider-return input.

### Exact branch preservation

`BenchmarkReturnResult` is sealed as exactly six variants:

1. `Available(context,benchmarkReturn)` for a resolved pair and locally
   representable calculation;
2. `NotApplicable(context)` for the exact upstream intentional N/A branch;
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
`BenchmarkReturnCalculator.calculate` attests that the required formula and
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

`BenchmarkReturnPolicyVersion` contains only
`SIGNED_BENCHMARK_BASIS_LEVEL_DENOMINATOR_SCALE_12_HALF_EVEN_V1`. Its
definition is exactly the following single-line 2832-byte ASCII sequence
encoded directly as UTF-8, with no byte-order mark, surrounding whitespace, or
trailing line ending:

```text
{"policyVersion":"SIGNED_BENCHMARK_BASIS_LEVEL_DENOMINATOR_SCALE_12_HALF_EVEN_V1","requiredReferenceLevelPairPolicyVersion":"POINT_IN_TIME_EXACT_BENCHMARK_PRICE_INDEX_LEVEL_PAIR_V1","requiredReferenceLevelPairPolicyDefinitionHash":"2394b535c1061d32c647504a303b6f1e4ec2fe88e6017d9ff335d12087a5f73d","referenceLevelPairInput":"BENCHMARK_REFERENCE_LEVEL_PAIR_RESOLUTION","resultContextFields":["policyVersion","policyDefinitionHash","referenceLevelPairResolution"],"resultVariants":{"Available":["context","benchmarkReturn"],"NotApplicable":["context"],"AssignmentUnavailable":["context"],"EndpointAnchorUnavailable":["context"],"EvidenceUnavailable":["context"],"OutputUnavailable":["context","reason"]},"outputUnavailableReasons":["OUTPUT_NOT_REPRESENTABLE"],"branchMapping":{"Resolved":"CALCULATE","NotApplicable":"NotApplicable","AssignmentUnavailable":"AssignmentUnavailable","EndpointAnchorUnavailable":"EndpointAnchorUnavailable","EvidenceUnavailable":"EvidenceUnavailable"},"nestedReasonRule":"PRESERVE_COMPLETE_PAIR_RECEIPT_NO_MAPPING_DUPLICATION_OR_FLATTENING","evaluationPrecedence":["REFERENCE_PAIR_NOT_APPLICABLE","REFERENCE_PAIR_ASSIGNMENT_UNAVAILABLE","REFERENCE_PAIR_ENDPOINT_ANCHOR_UNAVAILABLE","REFERENCE_PAIR_EVIDENCE_UNAVAILABLE","CALCULATE","OUTPUT_NOT_REPRESENTABLE"],"formula":"(endpoint-basis)/basis","operandSources":{"basis":"RESOLVED_BASIS_LEVEL_OBSERVATION_LEVEL","endpoint":"RESOLVED_ENDPOINT_LEVEL_OBSERVATION_LEVEL"},"numerator":"ENDPOINT_MINUS_BASIS_REFERENCE_LEVEL","denominator":"BASIS_REFERENCE_LEVEL","subtractionCount":1,"divisionCount":1,"divisionScale":12,"roundingMode":"HALF_EVEN","operationOrder":["SUBTRACT_BASIS_FROM_ENDPOINT_EXACTLY","DIVIDE_NUMERATOR_BY_BASIS_AT_SCALE_12_HALF_EVEN"],"intermediateRounding":"ABSENT","secondRounding":"ABSENT","outputUnits":"SIGNED_DECIMAL_RATIO","inputBoundary":"POSITIVE_NUMERIC_38_12_PROVIDER_PUBLISHED_PRICE_INDEX_LEVEL_PAIR","outputBoundary":"SIGNED_NUMERIC_38_12_AT_LEAST_NEGATIVE_ONE","roundedNegativeOneBoundary":"VALID","outputOverflowReason":"OUTPUT_NOT_REPRESENTABLE","percentConversion":"ABSENT","floatOrDoubleConversion":"ABSENT","providerReturnFieldUse":"ABSENT","assetReturnResultReuse":"FORBIDDEN","sectorReturnResultReuse":"FORBIDDEN","resultContext":"POLICY_IDENTITY_AND_COMPLETE_BENCHMARK_REFERENCE_LEVEL_PAIR_RESOLUTION_ONLY","constructorAttestation":"LOCAL_POLICY_PAIR_VARIANT_AND_OUTPUT_SHAPE_ONLY","sourceAttestationBoundary":"ONLY_BENCHMARK_RETURN_CALCULATOR_ATTESTS_FORMULA_AND_REQUIRED_ROUNDING","calculationAuthority":"DETERMINISTIC_SOURCE_LOCAL_PURE_LEAF","reflectionOrClassTokenUse":"ABSENT","clockLocaleTimezoneRandomEnvironmentDependence":"ABSENT","readiness":"ABSENT","lifecycleMapping":"ABSENT","methodologyPersistencePublication":"ABSENT","providerIntegration":"ABSENT","runtimeWiring":"ABSENT","fallbackBehavior":"ABSENT"}
```

Its lowercase SHA-256 is exactly
`96d0aab8e8e784b80a12b16c99f6ba8c5f44eff7a342fd14c075b944a0a7de79`.
Returned bytes are defensive, and every calculation context echoes the exact
digest. Any changed input identity, variant, operation, scale, rounding,
boundary, receipt rule, or byte requires a new policy version and digest.

## Dependency and publication boundary

Production depends only on ordinary JDK value types and the independently
typed ADR-030 benchmark reference-level-pair contract. It does not import or
cast asset-return, asset price-pair, sector reference-pair, sector-return,
assignment, endpoint-observation, horizon, lifecycle, persistence, API, or web
types. It uses no reflection, class token, shared generic return helper, clock,
locale, time zone, randomness, or environment state.

This pure leaf invokes no selector, readiness resolver, methodology activation,
fingerprint, lineage, outcome status, persistence, aggregation, ranking, or
publication. No schema, fixture, manifest member, JSON mapping, OpenAPI path,
Flyway migration, database behavior, controller, repository, provider adapter,
scheduler, resource, API behavior, or web source is added. Existing DEMO
`benchmarkReturn`, `sectorReturn`, `alpha`, and `sectorAlpha` values remain
null.

## External-data boundary

No API key, account, paid plan, provider license, named secret, or network
access is needed for this disconnected deterministic calculation. A credential
would prove access only, not permitted use. Non-DEMO use remains blocked on
ADR-030's approved benchmark price-index product/feed, exact-time historical
levels, calendar and divisor evidence, and storage/cache, display, derived-data,
and redistribution rights. Only after provider, product, coverage, and written
rights approval may a reviewed adapter introduce a scoped credential through
untracked local, CI, and deployment secret stores, never chat or Git.

## Verification

- Exact four-plus-one source surface, canonical 2832 bytes/hash, sealed result
  topology, dependency firewall, reverse isolation, null DEMO publication, and
  four-document marker parity: **PASS**. Independent reviews report no
  remaining P0/P1/P2 finding after two golden-coverage gaps were corrected.
- Focused `BenchmarkReturnCalculatorGoldenTest` passes 95/95 with zero
  failures, errors, or skips. Its normalized source SHA-256 is
  `80c8e7dcdf6b4ee3daf980dc3c3d2aa54e4446620af2fc0985173fddf5ab3c90`.
  Full API Maven verification passes 1763/1763 with zero failures, errors, or
  skips and `BUILD SUCCESS`, including Testcontainers PostgreSQL 17.10 and
  Flyway: **PASS**.
- The dedicated ADR-031 guard and runtime gate pass. All 36/36 workflow Python
  heredoc bodies syntax-compile; all 29/29 locally runnable bodies pass. Six
  `jsonschema`-dependent bodies remain syntax-only because the bundled local
  runtime lacks `jsonschema`, and the final cross-stack integration-log body is
  syntax-only by design. SnakeYAML 2.5 parses exactly four jobs and Compose
  config validates: **PASS**.
- Current protected production is 220 files / SHA-256
  `cb8532a4020c76a9ed2fd4a61fbb5844717dc23c7f27d90510e603c0bee1f5e9`;
  current API-test/web is 202 files /
  `12b03e7a48a0e6c3e676da9b335c4c270e8dc50bea2402aa25f6462db07bb273`.
  Exact ADR-031 four-plus-one exclusion reproduces ADR-030 production 216 /
  `45d06843fd95235221c6716a578915f40a410de8464b0b0ca3a09fff7c29436d`
  and test/web 201 /
  `fd0e3170ba2d64aeb4bf638010915455a27d3a5aed9fe77fb2a724502d96462f`;
  downstream replay also reproduces ADR-029 production 202 /
  `b1ae60b9c550353960687cb9973e2909e965a5e3eb98bb23b39b0a7f01a2a899`
  and test/web 199 /
  `59726e88e5bf7d831f16beaa693689ca799990d355733a1115c07a285a7e5293`:
  **PASS**. The user-owned `apps/web/next-env.d.ts` remains preserved.
- Deliberately mutating the README marker, the canonical
  `percentConversion` policy byte, or expected runtime count from 95 to 94
  makes the dedicated guard or runtime gate exit 1; all mutations are restored.
  The final dedicated guard passes, `git diff --check` is clean, and marker
  parity remains exactly one occurrence in each contract document: **PASS**.

## Consequences and next work

- A resolved benchmark reference-level pair can now produce one deterministic,
  replayable price-return leaf without weakening its source receipt.
- The independent sector reference-return calculator is next. It must own its
  own policy, input, result, calculator, and golden matrix; it cannot cast or
  reuse this benchmark type. Source-local readiness is a subsequent,
  independently gated slice only after both calculators are reviewed.
- Canonical evidence fixtures, methodology activation, fingerprint, lineage,
  lifecycle composition, persistence, API/UI publication, MFE/MAE, alpha, and
  sector alpha remain later reviewed work. Alpha remains last.
