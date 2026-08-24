# ADR-023 — Supplied-Leaf Target-Error Readiness Classification

- Status: Accepted
- Date: 2026-08-24

## Context

ADR-015 calculates one point-in-time target-error leaf and preserves every
typed endpoint-price unavailable reason. It deliberately does not promote that
leaf into a canonical lifecycle state. An endpoint that has not yet closed is
temporally distinct from a target that is absent or from missing, ambiguous,
mismatched, unsupported, or unrepresentable evidence.

Treating every unavailable target error as Pending would be false when the
target is also absent or another evidence defect is already known. Treating
every unavailable target error as incomplete would discard the one exact
temporal condition decidable from the supplied typed evidence. This distinction
must remain source-local and versioned before a later policy can consider the
complete set of outcome metrics.

## Decision

The next isolated P3 slice adds disconnected target-error readiness
classification in
`com.wallstreetreceipts.api.domain.outcome.targeterrorreadiness`.

- `TargetErrorReadinessRequest` contains exactly the readiness V1 policy and
  one complete supplied `TargetErrorResult`. Both fields are non-null. The
  source must carry ADR-015's exact target-error V1 policy and digest.
- An available target error becomes `Settled`. This means only that the
  supplied target-error leaf is available; later metrics may still be absent.
- `AwaitingEndpoint` requires this exact complete nested chain:
  `TargetErrorResult.UnavailableReason.ENDPOINT_PRICE_UNAVAILABLE`, the echoed
  `EndpointPriceResolution.UnavailableReason.ENDPOINT_NOT_REACHED_AS_OF`, and a
  typed endpoint unavailable result carrying that same reason.
- `TARGET_AND_ENDPOINT_PRICE_UNAVAILABLE` remains `EvidenceUnavailable`, even
  when its endpoint reason is `ENDPOINT_NOT_REACHED_AS_OF`, because waiting for
  the close does not repair the already absent target.
- Every other constructible target-error unavailable shape becomes
  `EvidenceUnavailable`. Missing targets, catalog, binding, observation,
  adjustment, continuity, ambiguity, mismatch, and numeric representability
  meanings are never inferred to be temporal, retryable, or permanent.
- Classification precedence is available target error, then the one exact
  endpoint-only temporal chain, then every other unavailable result. The
  result preserves the exact whole ADR-015 source object and adds no flattened
  reason or duplicated endpoint leaf.
- The sealed resolution contains exactly
  `Settled(context,sourceResult)`,
  `AwaitingEndpoint(context,sourceResult)`, and
  `EvidenceUnavailable(context,sourceResult)`. Context contains only the
  readiness policy version and digest.
- Exact reason inspection exists only in `TargetErrorReadinessResolver`.
  Public result constructors delegate their locally decidable classification
  check to that resolver, so a directly constructed wrong variant fails closed
  without duplicating reason logic.

These names are source-local readiness evidence. They are not
`OutcomeEvaluationStatus`, do not construct or mutate `CallOutcome`, do not set
`dataComplete`, and make no retry, freshness, cancellation, scheduling, or
provider claim.

## Canonical policy definition

`TargetErrorReadinessPolicyVersion` contains exactly
`SUPPLIED_LEAF_TARGET_ERROR_READINESS_V1`. Its definition is the following
single-line 1979-byte ASCII sequence encoded directly as UTF-8, with no BOM,
leading or trailing whitespace, or trailing newline:

```json
{"policyVersion":"SUPPLIED_LEAF_TARGET_ERROR_READINESS_V1","requiredTargetErrorPolicyVersion":"ACTUAL_DENOMINATOR_SCALE_12_HALF_EVEN_V1","requiredTargetErrorPolicyDefinitionHash":"31ca30555549f670e3c22d98ead16f7a02bfad198f36532effaf4a4b6931d074","requestFields":["policyVersion","sourceResult"],"requestPresence":"ALL_FIELDS_NON_NULL","resolutionContextFields":["policyVersion","policyDefinitionHash"],"resultVariants":{"Settled":["context","sourceResult"],"AwaitingEndpoint":["context","sourceResult"],"EvidenceUnavailable":["context","sourceResult"]},"sourceInput":"COMPLETE_SUPPLIED_TARGET_ERROR_RESULT","sourceAttestationBoundary":"LOCAL_SOURCE_POLICY_AND_TYPED_SHAPE_ONLY_NO_ORIGINAL_INPUT_MEMBERSHIP_PIT_FILTERING_OR_CALCULATOR_INVOCATION_CLAIM","classificationValidation":"RESULT_CONSTRUCTORS_AND_RESOLVER_SHARE_EXACT_CLASSIFICATION_VALIDATION","resolverInvocationAttestation":"ONLY_RESOLVE_ATTESTS_REQUEST_TO_RESULT_INVOCATION","branchPrecedence":["TargetErrorResult.Available","EXACT_AWAITING_ENDPOINT_CHAIN","ALL_OTHER_TARGET_ERROR_UNAVAILABLE"],"branchMapping":{"TargetErrorResult.Available":"SETTLED","ExactAwaitingEndpointChain":"AWAITING_ENDPOINT","OtherTargetErrorResult.Unavailable":"EVIDENCE_UNAVAILABLE"},"awaitingEndpointChain":{"targetErrorReason":"ENDPOINT_PRICE_UNAVAILABLE","endpointReason":"ENDPOINT_NOT_REACHED_AS_OF"},"targetAndEndpointUnavailableRule":"EVIDENCE_UNAVAILABLE_EVEN_WHEN_ENDPOINT_NOT_REACHED","settledRule":"TARGET_ERROR_AVAILABLE","sourcePreservation":"PRESERVE_EXACT_WHOLE_TARGET_ERROR_RESULT","reasonInspection":"ONLY_IN_TARGET_ERROR_READINESS_RESOLVER","reasonFlattening":"ABSENT","canonicalOutcomeStatus":"ABSENT","dataCompleteClaim":"ABSENT","retry":"ABSENT","freshness":"ABSENT","cancellation":"ABSENT","scheduling":"ABSENT","producerReplay":"ABSENT","calculatorInvocation":"ABSENT","methodologyActivation":"ABSENT","inputFingerprint":"ABSENT","persistence":"ABSENT","aggregation":"ABSENT","ranking":"ABSENT","publication":"ABSENT"}
```

Its SHA-256 is
`0b8bfb22dccd4a494f568c44d06163f73af36462cf929bc83cf238019811c44a`.
Every result context echoes this digest, and callers receive a defensive UTF-8
byte-array copy.

## Purity and verification boundary

The production surface is exactly:

- `TargetErrorReadinessPolicyVersion.java`
- `TargetErrorReadinessRequest.java`
- `TargetErrorReadinessResolution.java`
- `TargetErrorReadinessResolver.java`

The sole source-local test is
`TargetErrorReadinessResolverGoldenTest.java`. It executes exactly 46 test
invocations: six contract, null, shape, direct-construction, replay, and
determinism checks; all 39 constructible unavailable shapes; and one settled
shape. The unavailable matrix comprises 16 endpoint-only reasons, 16 compound
target-and-endpoint reasons, and seven reasons requiring a resolved endpoint.
It proves the single endpoint-only temporal chain, the compound missing-target
firewall, whole-source object preservation, exact canonical bytes and digest,
and the absence of canonical outcome or runtime wiring.

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

Canonical lifecycle mapping across all required metrics, retry and provider
freshness policy, cancellation and latest-correction selection, scheduling,
producer receipts, methodology activation, input fingerprinting, append-only
persistence, MFE/MAE, alpha and sector alpha, aggregation, ranking, and API/UI
publication remain later reviewed work.
