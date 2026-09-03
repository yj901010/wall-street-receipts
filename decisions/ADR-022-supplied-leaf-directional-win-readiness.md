# ADR-022 — Supplied-Leaf Directional-Win Readiness Classification

- Status: Accepted
- Date: 2026-08-24

## Context

ADR-021 composes a complete supplied directional-win leaf while preserving
neutral and all 55 typed asset-return unavailable chains. It deliberately does
not inspect those reasons or promote any leaf into a canonical lifecycle state.
In particular, an endpoint that has not yet closed is temporally distinct from
missing, ambiguous, mismatched, unsupported, or unrepresentable evidence.

Treating every unavailable leaf as Pending would be false when the basis price
is also absent or another evidence defect is already known. Treating every leaf
as incomplete would discard the one exact temporal condition that can be
decided from the supplied point-in-time evidence. The distinction must be
versioned before any later lifecycle policy considers canonical outcome status,
retry, or scheduling.

## Decision

The seventeenth isolated P3 slice adds disconnected directional-win readiness
classification in
`com.wallstreetreceipts.api.domain.outcome.directionalwinreadiness`.

- `DirectionalWinReadinessRequest` contains exactly the readiness V1 policy and
  one complete supplied `DirectionalWinOrchestrationResolution`. Both fields
  are non-null. The source must carry ADR-021's exact V1 policy and digest.
- The resolver extracts the exact `AssetReturnResult` already preserved by any
  ADR-021 source variant. It does not reconstruct terms, routing, prices,
  returns, or the directional-win Boolean and invokes no upstream producer or
  calculator.
- An available asset return becomes `Settled`, whether the source is
  directional `Available` or neutral `NotApplicable`. `Settled` means only that
  this supplied asset-return dependency is available; later outcome metrics
  may still be absent.
- `AwaitingEndpoint` requires this exact complete nested chain:
  `AssetReturnResult.UnavailableReason.PRICE_PAIR_UNAVAILABLE`, then
  `AssetReturnPricePairResolution.UnavailableReason.ENDPOINT_PRICE_UNAVAILABLE`,
  then
  `EndpointPriceResolution.UnavailableReason.ENDPOINT_NOT_REACHED_AS_OF`.
- `BASIS_AND_ENDPOINT_PRICE_UNAVAILABLE` remains `EvidenceUnavailable` even
  when its endpoint reason is `ENDPOINT_NOT_REACHED_AS_OF`, because waiting for
  the close does not repair the already unavailable basis price.
- Every other asset-return, price-pair, and endpoint unavailable chain becomes
  `EvidenceUnavailable`. Catalog, binding, observation, adjustment, continuity,
  ambiguity, mismatch, missing-as-of, and numeric representability meanings are
  never inferred to be temporal, retryable, or permanent.
- Classification precedence is available return, then the one exact
  endpoint-only temporal chain, then every other unavailable chain. The result
  preserves the exact whole ADR-021 source object; it adds no flattened reason
  or duplicate leaf.
- The sealed resolution contains exactly
  `Settled(context,sourceResolution)`,
  `AwaitingEndpoint(context,sourceResolution)`, and
  `EvidenceUnavailable(context,sourceResolution)`. Context contains only the
  readiness policy version and digest.
- Exact nested reason inspection exists only in
  `DirectionalWinReadinessResolver`. Public result constructors delegate their
  locally decidable classification check to that resolver, so a directly
  constructed wrong variant fails closed without duplicating reason logic.

These names are source-local readiness evidence. They are not
`OutcomeEvaluationStatus`, do not construct or mutate `CallOutcome`, do not set
`dataComplete`, and make no retry, cancellation, scheduling, or provider
freshness claim.

## Canonical policy definition

`DirectionalWinReadinessPolicyVersion` contains exactly
`SUPPLIED_LEAF_DIRECTIONAL_WIN_READINESS_V1`. Its definition is the following
single-line 2353-byte ASCII sequence encoded directly as UTF-8, with no BOM,
leading or trailing whitespace, or trailing newline:

```json
{"policyVersion":"SUPPLIED_LEAF_DIRECTIONAL_WIN_READINESS_V1","requiredDirectionalWinOrchestrationPolicyVersion":"SUPPLIED_LEAF_DIRECTIONAL_WIN_ORCHESTRATION_V1","requiredDirectionalWinOrchestrationPolicyDefinitionHash":"51429c7601d4807162855f08c680d1e6bb7895f87fc108e141e5ad3a3ab25bcb","requestFields":["policyVersion","sourceResolution"],"requestPresence":"ALL_FIELDS_NON_NULL","resolutionContextFields":["policyVersion","policyDefinitionHash"],"resultVariants":{"Settled":["context","sourceResolution"],"AwaitingEndpoint":["context","sourceResolution"],"EvidenceUnavailable":["context","sourceResolution"]},"sourceInput":"COMPLETE_SUPPLIED_DIRECTIONAL_WIN_ORCHESTRATION_RESOLUTION","sourceAttestationBoundary":"LOCAL_SOURCE_POLICY_AND_TYPED_SHAPE_ONLY_NO_ORIGINAL_REQUEST_MEMBERSHIP_PIT_FILTERING_OR_PRODUCER_INVOCATION_CLAIM","classificationValidation":"RESULT_CONSTRUCTORS_AND_RESOLVER_SHARE_EXACT_CLASSIFICATION_VALIDATION","resolverInvocationAttestation":"ONLY_RESOLVE_ATTESTS_REQUEST_TO_RESULT_INVOCATION","assetReturnSource":{"Available":"sourceResolution.assetReturnResult","NotApplicable":"sourceResolution.assetReturnResult","AssetReturnUnavailable":"sourceResolution.assetReturnResult"},"branchPrecedence":["AssetReturn.Available","EXACT_AWAITING_ENDPOINT_CHAIN","ALL_OTHER_ASSET_RETURN_UNAVAILABLE"],"branchMapping":{"AnySource+AssetReturn.Available":"SETTLED","AnySource+ExactAwaitingEndpointChain":"AWAITING_ENDPOINT","AnySource+OtherAssetReturn.Unavailable":"EVIDENCE_UNAVAILABLE"},"awaitingEndpointChain":{"assetReturnReason":"PRICE_PAIR_UNAVAILABLE","pricePairReason":"ENDPOINT_PRICE_UNAVAILABLE","endpointReason":"ENDPOINT_NOT_REACHED_AS_OF"},"basisAndEndpointUnavailableRule":"EVIDENCE_UNAVAILABLE_EVEN_WHEN_ENDPOINT_NOT_REACHED","settledRule":"DIRECTIONAL_AVAILABLE_OR_NEUTRAL_NOT_APPLICABLE_WITH_ASSET_RETURN_AVAILABLE","sourcePreservation":"PRESERVE_EXACT_WHOLE_DIRECTIONAL_WIN_ORCHESTRATION_RESOLUTION","reasonInspection":"ONLY_IN_DIRECTIONAL_WIN_READINESS_RESOLVER","reasonFlattening":"ABSENT","canonicalOutcomeStatus":"ABSENT","dataCompleteClaim":"ABSENT","retry":"ABSENT","cancellation":"ABSENT","scheduling":"ABSENT","producerReplay":"ABSENT","calculatorInvocation":"ABSENT","methodologyActivation":"ABSENT","inputFingerprint":"ABSENT","persistence":"ABSENT","aggregation":"ABSENT","ranking":"ABSENT","publication":"ABSENT"}
```

Its SHA-256 is
`1eca77c5b4d43de7657281c161a8c50356cd90e1a18c6e9fd7f5b2c0142b7ec7`.
Every result context echoes this digest, and callers receive a defensive UTF-8
byte-array copy.

## Purity and verification boundary

The production surface is exactly:

- `DirectionalWinReadinessPolicyVersion.java`
- `DirectionalWinReadinessRequest.java`
- `DirectionalWinReadinessResolution.java`
- `DirectionalWinReadinessResolver.java`

The sole source-local test is
`DirectionalWinReadinessResolverGoldenTest.java`. It executes exactly 118 test
invocations: six contract/null/shape/replay/determinism checks, all 55 typed
unavailable chains in both directional and neutral source shapes for 110
classification vectors, and two settled source shapes. The matrix proves the
single endpoint-only temporal chain, the compound basis-and-endpoint firewall,
whole-source object preservation, exact canonical bytes/hash, and the absence
of canonical outcome or runtime wiring.

No controller, application service, repository, schema, fixture, OpenAPI,
provider, clock, network, API, or web surface changes in this slice.

## External-data and deferred boundary

This supplied-leaf policy requires no API key, account, paid plan, provider
license, secret, or network access. Before non-DEMO production evidence enters
the system, P5 still must select entitled analyst-call, official-close,
exchange-calendar, corporate-action, and asset/venue providers and establish
storage, display, derived-data, and redistribution rights. Scoped secrets may
be introduced only through approved local or CI secret stores, never chat or
Git.

Canonical lifecycle mapping, retry/freshness policy, cancellation and latest-
correction selection, scheduling, producer receipts, methodology activation,
input fingerprinting, append-only persistence, MFE/MAE, alpha/sector alpha,
aggregation, ranking, and API/UI publication remain later reviewed work.
