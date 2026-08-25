# ADR-033 — Independent Benchmark and Sector Return Readiness

- Status: Accepted
- Date: 2026-08-25

## Context

ADR-031 and ADR-032 calculate independently typed benchmark and sector
price-index returns from their complete ADR-030 point-in-time reference-pair
receipts. Each return result preserves an available decimal, intentional
non-applicability, assignment unavailability, endpoint-anchor unavailability,
reference-evidence unavailability, or local output-representability failure.
Neither calculator establishes source-local readiness or canonical lifecycle
state.

The one temporal readiness case is narrower than a top-level unavailable
return. It exists only when the return result preserves an
`EvidenceUnavailable` reference pair whose exact reason is
`ENDPOINT_NOT_REACHED_AS_OF`. Assignment and anchor failures remain evidence
failures even when their context also contains a future endpoint. Intentional
non-applicability is already settled and must not be changed into endpoint
waiting. Every other unavailable branch is evidence-unavailable.

ADR-025 fixes a future aggregate at nine readiness ownership inputs for ten
metric meanings, with sharing only for asset return and directional win.
ADR-026 also requires benchmark and sector return contracts to remain
independently typed. Combining the two comparative leaves into one readiness
receipt would silently create a second sharing decision and permit unrelated
source objects to be correlated before the future aggregate owns that work.

ADR-033 classifies benchmark and sector return readiness independently from their complete supplied ADR-031 and ADR-032 result receipts without mapping either leaf to canonical lifecycle status.

## Decision

This disconnected P3 slice adds two sibling four-type production contracts
and one exhaustive source-local golden for each:

- `benchmarkreturnreadiness`: `BenchmarkReturnReadinessPolicyVersion`,
  `BenchmarkReturnReadinessRequest`, `BenchmarkReturnReadinessResolution`,
  `BenchmarkReturnReadinessResolver`, and
  `BenchmarkReturnReadinessResolverGoldenTest`;
- `sectorreturnreadiness`: `SectorReturnReadinessPolicyVersion`,
  `SectorReturnReadinessRequest`, `SectorReturnReadinessResolution`,
  `SectorReturnReadinessResolver`, and
  `SectorReturnReadinessResolverGoldenTest`.

There is no combined comparative request, cross-return correlation, shared
generic readiness type, cast, reflection path, alias, or delegated facade.
Each request contains exactly its own readiness policy and one complete
supplied source result. It verifies the exact corresponding ADR-031 or ADR-032
policy identity and digest without re-running a pair selector or return
calculator.

### Independent classification

Both sealed readiness results have exactly three variants with identical
source-local shape but unrelated Java types:

1. `Settled(context,sourceResult)`;
2. `AwaitingEndpoint(context,sourceResult)`; and
3. `EvidenceUnavailable(context,sourceResult)`.

Each context contains only the corresponding readiness policy version and
definition hash. Every variant preserves the exact whole supplied source
object and adds no flattened reason.

The following table is applied independently to the benchmark and sector
source result:

| Complete source-result branch | Readiness result |
| --- | --- |
| `Available` | `Settled` |
| `NotApplicable` | `Settled` |
| `EvidenceUnavailable` preserving pair `EvidenceUnavailable(ENDPOINT_NOT_REACHED_AS_OF)` | `AwaitingEndpoint` |
| `AssignmentUnavailable` | `EvidenceUnavailable` |
| `EndpointAnchorUnavailable` | `EvidenceUnavailable` |
| `EvidenceUnavailable` preserving any other pair reason | `EvidenceUnavailable` |
| `OutputUnavailable` | `EvidenceUnavailable` |

The resolver alone may inspect the one nested reference-pair reason required
to prove the exact awaiting chain. A top-level string, timestamp, assignment
context, endpoint-anchor context, or output reason cannot create awaiting
status. Public result constructors and the resolver share the exact same
classification validator so a directly constructed wrong variant fails
closed. Only `resolve(request)` attests a request-to-result invocation.

`Settled`, `AwaitingEndpoint`, and `EvidenceUnavailable` are local readiness
evidence for one metric meaning. They are not `OutcomeEvaluationStatus`, do
not set `dataComplete`, and make no claim about retry, permanence, freshness,
cancellation, scheduling, methodology activation, fingerprint, persistence,
aggregation, ranking, or publication.

## Canonical policy definitions

### Benchmark return readiness

`BenchmarkReturnReadinessPolicyVersion` contains exactly
`SUPPLIED_LEAF_BENCHMARK_RETURN_READINESS_V1`. Its definition is the following
single-line 2622-byte ASCII sequence encoded directly as UTF-8, with no BOM,
leading or trailing whitespace, or trailing newline:

```json
{"policyVersion":"SUPPLIED_LEAF_BENCHMARK_RETURN_READINESS_V1","requiredBenchmarkReturnPolicyVersion":"SIGNED_BENCHMARK_BASIS_LEVEL_DENOMINATOR_SCALE_12_HALF_EVEN_V1","requiredBenchmarkReturnPolicyDefinitionHash":"96d0aab8e8e784b80a12b16c99f6ba8c5f44eff7a342fd14c075b944a0a7de79","requestFields":["policyVersion","sourceResult"],"requestPresence":"ALL_FIELDS_NON_NULL","resolutionContextFields":["policyVersion","policyDefinitionHash"],"resultVariants":{"Settled":["context","sourceResult"],"AwaitingEndpoint":["context","sourceResult"],"EvidenceUnavailable":["context","sourceResult"]},"sourceInput":"COMPLETE_SUPPLIED_BENCHMARK_RETURN_RESULT","readinessOwnership":"BENCHMARK_RETURN_ONLY","otherComparativeReturnInput":"ABSENT","crossReturnCorrelation":"ABSENT_DEFERRED_TO_FUTURE_AGGREGATE","sharedGenericReadiness":"ABSENT","sourceAttestationBoundary":"LOCAL_SOURCE_POLICY_AND_TYPED_SHAPE_ONLY_NO_ORIGINAL_INPUT_MEMBERSHIP_PIT_FILTERING_SELECTOR_OR_CALCULATOR_INVOCATION_CLAIM","classificationValidation":"RESULT_CONSTRUCTORS_AND_RESOLVER_SHARE_EXACT_CLASSIFICATION_VALIDATION","resolverInvocationAttestation":"ONLY_RESOLVE_ATTESTS_REQUEST_TO_RESULT_INVOCATION","branchPrecedence":["Available","NotApplicable","EXACT_AWAITING_ENDPOINT_CHAIN","AssignmentUnavailable","EndpointAnchorUnavailable","OTHER_EVIDENCE_UNAVAILABLE","OutputUnavailable"],"branchMapping":{"Available":"SETTLED","NotApplicable":"SETTLED","ExactAwaitingEndpointChain":"AWAITING_ENDPOINT","AssignmentUnavailable":"EVIDENCE_UNAVAILABLE","EndpointAnchorUnavailable":"EVIDENCE_UNAVAILABLE","OtherEvidenceUnavailable":"EVIDENCE_UNAVAILABLE","OutputUnavailable":"EVIDENCE_UNAVAILABLE"},"awaitingEndpointChain":{"benchmarkReturnVariant":"EvidenceUnavailable","referenceLevelPairVariant":"EvidenceUnavailable","referenceLevelPairReason":"ENDPOINT_NOT_REACHED_AS_OF"},"notApplicableEndpointRule":"SETTLED_WITHOUT_ENDPOINT_WAIT_OR_REASON_INSPECTION","assignmentOrAnchorUnavailableRule":"EVIDENCE_UNAVAILABLE_EVEN_WHEN_ENDPOINT_NOT_REACHED","settledRule":"BENCHMARK_RETURN_AVAILABLE_OR_INTENTIONALLY_NOT_APPLICABLE","sourcePreservation":"PRESERVE_EXACT_WHOLE_BENCHMARK_RETURN_RESULT","reasonInspection":"ONLY_IN_BENCHMARK_RETURN_READINESS_RESOLVER","reasonFlattening":"ABSENT","canonicalOutcomeStatus":"ABSENT","dataCompleteClaim":"ABSENT","retry":"ABSENT","freshness":"ABSENT","cancellation":"ABSENT","scheduling":"ABSENT","producerReplay":"ABSENT","selectorInvocation":"ABSENT","calculatorInvocation":"ABSENT","methodologyActivation":"ABSENT","inputFingerprint":"ABSENT","persistence":"ABSENT","aggregation":"ABSENT","ranking":"ABSENT","publication":"ABSENT"}
```

Its lowercase SHA-256 is
`2dedaf014a149ed81e75941ee3677e3c8b77243b9987d9496709266aad721daf`.

### Sector return readiness

`SectorReturnReadinessPolicyVersion` contains exactly
`SUPPLIED_LEAF_SECTOR_RETURN_READINESS_V1`. Its definition is the following
single-line 2592-byte ASCII sequence encoded directly as UTF-8, with the same
no-BOM and no-surrounding-whitespace convention:

```json
{"policyVersion":"SUPPLIED_LEAF_SECTOR_RETURN_READINESS_V1","requiredSectorReturnPolicyVersion":"SIGNED_SECTOR_BASIS_LEVEL_DENOMINATOR_SCALE_12_HALF_EVEN_V1","requiredSectorReturnPolicyDefinitionHash":"5aecd42c32ba69f0d21ab6e1ee1e3128cd31584724a6f46e176acd470204d0f7","requestFields":["policyVersion","sourceResult"],"requestPresence":"ALL_FIELDS_NON_NULL","resolutionContextFields":["policyVersion","policyDefinitionHash"],"resultVariants":{"Settled":["context","sourceResult"],"AwaitingEndpoint":["context","sourceResult"],"EvidenceUnavailable":["context","sourceResult"]},"sourceInput":"COMPLETE_SUPPLIED_SECTOR_RETURN_RESULT","readinessOwnership":"SECTOR_RETURN_ONLY","otherComparativeReturnInput":"ABSENT","crossReturnCorrelation":"ABSENT_DEFERRED_TO_FUTURE_AGGREGATE","sharedGenericReadiness":"ABSENT","sourceAttestationBoundary":"LOCAL_SOURCE_POLICY_AND_TYPED_SHAPE_ONLY_NO_ORIGINAL_INPUT_MEMBERSHIP_PIT_FILTERING_SELECTOR_OR_CALCULATOR_INVOCATION_CLAIM","classificationValidation":"RESULT_CONSTRUCTORS_AND_RESOLVER_SHARE_EXACT_CLASSIFICATION_VALIDATION","resolverInvocationAttestation":"ONLY_RESOLVE_ATTESTS_REQUEST_TO_RESULT_INVOCATION","branchPrecedence":["Available","NotApplicable","EXACT_AWAITING_ENDPOINT_CHAIN","AssignmentUnavailable","EndpointAnchorUnavailable","OTHER_EVIDENCE_UNAVAILABLE","OutputUnavailable"],"branchMapping":{"Available":"SETTLED","NotApplicable":"SETTLED","ExactAwaitingEndpointChain":"AWAITING_ENDPOINT","AssignmentUnavailable":"EVIDENCE_UNAVAILABLE","EndpointAnchorUnavailable":"EVIDENCE_UNAVAILABLE","OtherEvidenceUnavailable":"EVIDENCE_UNAVAILABLE","OutputUnavailable":"EVIDENCE_UNAVAILABLE"},"awaitingEndpointChain":{"sectorReturnVariant":"EvidenceUnavailable","referenceLevelPairVariant":"EvidenceUnavailable","referenceLevelPairReason":"ENDPOINT_NOT_REACHED_AS_OF"},"notApplicableEndpointRule":"SETTLED_WITHOUT_ENDPOINT_WAIT_OR_REASON_INSPECTION","assignmentOrAnchorUnavailableRule":"EVIDENCE_UNAVAILABLE_EVEN_WHEN_ENDPOINT_NOT_REACHED","settledRule":"SECTOR_RETURN_AVAILABLE_OR_INTENTIONALLY_NOT_APPLICABLE","sourcePreservation":"PRESERVE_EXACT_WHOLE_SECTOR_RETURN_RESULT","reasonInspection":"ONLY_IN_SECTOR_RETURN_READINESS_RESOLVER","reasonFlattening":"ABSENT","canonicalOutcomeStatus":"ABSENT","dataCompleteClaim":"ABSENT","retry":"ABSENT","freshness":"ABSENT","cancellation":"ABSENT","scheduling":"ABSENT","producerReplay":"ABSENT","selectorInvocation":"ABSENT","calculatorInvocation":"ABSENT","methodologyActivation":"ABSENT","inputFingerprint":"ABSENT","persistence":"ABSENT","aggregation":"ABSENT","ranking":"ABSENT","publication":"ABSENT"}
```

Its lowercase SHA-256 is
`5737f44ebc6e65270300889dd5c2e92da0c4f3a2f04e4c6c43e4483e522187d4`.
Both policies return defensive UTF-8 byte-array copies and every local context
echoes its exact digest. A changed source identity, branch, precedence rule,
ownership boundary, field, byte, or digest requires a new policy version.

## Purity and verification boundary

Benchmark readiness production imports only JDK types, `benchmarkreturn`, and
the exact benchmark reference-pair resolution needed for nested-reason
inspection. Sector readiness has the symmetric sector-only dependency edge.
The packages do not import one another, their opposite return or pair types,
asset-return types, a generic helper, lifecycle, persistence, API, or web
types. No reverse edge is introduced into either source calculator.

The benchmark golden executes exactly 87 test invocations: six fixed contract,
constructor, identity-replay, and determinism checks plus all 81 source shapes.
The matrix comprises one available, four intentional N/A, all 19 assignment-
unavailable, all three anchor-unavailable, all 53 pair-evidence reasons, and
one output-unavailable shape. It proves five settled, one awaiting, and 75
evidence-unavailable classifications.

The sector golden executes exactly 104 test invocations: the same six fixed
checks plus all 98 sector source shapes. Its matrix comprises one available,
one intentional N/A, all 36 assignment-unavailable, all three anchor-
unavailable, all 56 pair-evidence reasons, and one output-unavailable shape. It
proves two settled, one awaiting, and 95 evidence-unavailable classifications.

No schema, fixture, manifest member, OpenAPI path, Flyway migration, database
behavior, controller, repository, provider adapter, scheduler, resource, API
behavior, or web source is added. Existing DEMO benchmark return, sector
return, alpha, and sector-alpha values remain null.

## External-data boundary

This disconnected classification requires no API key, account, paid plan,
provider license, named secret, network access, or credential. Before non-DEMO
integration, P5 must approve the exact benchmark and sector-index products and
feeds; exact-time historical levels and revisions; reference calendars;
divisor and methodology-continuity evidence; the sector-node-to-index binding;
and storage, caching, display, derived-return, and redistribution rights. If
publisher and redistributor differ, each product and rights grant requires
independent review. Only after product, coverage, and written-rights approval
may reviewed adapters introduce scoped credentials through untracked local,
CI, and deployment secret stores, never chat or Git.

## Verification

- Exact policy extraction passes at benchmark 2622 bytes /
  `2dedaf014a149ed81e75941ee3677e3c8b77243b9987d9496709266aad721daf`
  and sector 2592 bytes /
  `5737f44ebc6e65270300889dd5c2e92da0c4f3a2f04e4c6c43e4483e522187d4`.
  The independent 8+2 surface, source policies, sealed shapes, exact temporal
  chains, whole-source identity, import/reverse/lifecycle/product firewalls,
  and four-document marker parity pass.
- Focused goldens pass benchmark 87/87 and sector 104/104—191 total—with zero
  failures, errors, or skips. Normalized source hashes are benchmark
  `f61e82ba7766effe4954f4c96db745a49bb49a03d06de583c39c32d76e3c1b3d`
  and sector
  `1b2aa1eea5d5c8efcddd048c54d8b53be87b14cdfd96a6df43e11a7f55bc9f8c`.
  Full API Maven verification passes 2066/2066 with zero failures, errors, or
  skips, including Testcontainers PostgreSQL 17.10 and Flyway.
- Protected production is 232 /
  `2cfbb3b9f9039b9e7af92ac7cbd9c35b9705ce79fda3aa58422a73f23c0d8941`;
  API-test/web is 205 /
  `fba2656db6ef5bbf5e15288bebd894639926645e7657ac214ec1cec657cc4d75`.
  Exact ADR-033 exclusion reproduces ADR-032 at 224 /
  `bc31bb72f14289e6a8b3c344e356f900a2d23a9fb9efd48ce935586c0e336055`
  and 203 /
  `5f95c2b844af16224815b1b4025b52b9c25b7822d4fa53b8f8d93788805f28ce`;
  downstream ADR-031/030/029 replays also pass.
- The dedicated guard and exact 87/87 and 104/104 runtime gates pass. Workflow
  Python bodies syntax-compile 38/38 and locally runnable bodies pass 31/31;
  SnakeYAML 2.5 parses four jobs and Compose validates. Web lint, 569/569
  Vitest tests, and production build pass.
- README-marker, benchmark canonical policy-byte, and expected runtime
  87-to-86 mutations each exit nonzero and are restored. Independent review
  has no remaining P0/P1/P2 finding after explicit future-endpoint anchor
  coverage was added to both legs. `git diff --check` passes and the user-owned
  `apps/web/next-env.d.ts` is restored to its exact pre-build hash.

## Consequences and next work

- Benchmark and sector return meanings now have separate deterministic
  source-local readiness receipts suitable for later aggregate composition.
- This slice does not correlate those receipts or establish canonical evidence,
  lifecycle, completeness, methodology activation, fingerprint, lineage,
  persistence, API/UI publication, aggregation, or ranking.
- Point-in-time raw-window coverage and evidence for MFE/MAE is the next
  independent foundation. Alpha and sector alpha remain last.
