# ADR-024 — Supplied-Leaf Target-Hit Readiness Classification

- Status: Accepted
- Date: 2026-08-24

## Context

ADR-020 composes target eligibility and favorable-extreme evidence into one
complete target-hit orchestration result. It preserves an available Boolean,
the three permanent non-applicability meanings, the exact not-yet-reached
horizon branch, or one typed evidence-unavailability branch. It deliberately
does not promote that disconnected leaf into a canonical outcome lifecycle.

An available Boolean and a permanent non-applicability result are both settled
for this metric: neither can become more complete by waiting for the endpoint.
The sole `Pending` shape is different because its typed eligibility leaf proves
that the exact horizon endpoint has not yet been reached as of evaluation.
Eligibility or favorable-extreme unavailability is evidence failure rather
than temporal readiness. These source-local meanings must remain distinct
before a later policy considers every required metric and cancellation state.

## Decision

The next isolated P3 slice adds disconnected target-hit readiness
classification in
`com.wallstreetreceipts.api.domain.outcome.targethitreadiness`.

- `TargetHitReadinessRequest` contains exactly the readiness V1 policy and one
  complete supplied `TargetHitOrchestrationResolution`. Both fields are
  non-null. The source must carry ADR-020's exact orchestration V1 policy and
  digest.
- `Available` and every `NotApplicable` variant become `Settled`. Settled means
  only that this supplied target-hit leaf has no remaining endpoint or evidence
  work; it makes no claim about any other metric.
- The exact typed `Pending` variant becomes `AwaitingEndpoint`. Its nested
  eligibility resolution can carry only
  `PendingReason.HORIZON_NOT_REACHED_AS_OF`; the upstream record constructor
  already enforces that invariant. Classification therefore switches only on
  the sealed top-level variant and does not reinterpret or duplicate the nested
  reason.
- `EligibilityUnavailable` and `FavorableExtremeUnavailable` become
  `EvidenceUnavailable`. Their exact complete nested leaves and reasons remain
  inside the preserved source object.
- The result preserves the exact whole ADR-020 source object and adds no outer
  reason. Its sealed variants are exactly
  `Settled(context,sourceResult)`,
  `AwaitingEndpoint(context,sourceResult)`, and
  `EvidenceUnavailable(context,sourceResult)`. Context contains only the
  readiness policy version and digest.
- Public result constructors and the resolver share the same locally decidable
  classification validation. Direct construction of the wrong variant fails
  closed. Only `TargetHitReadinessResolver.resolve(request)` attests the
  request-to-result invocation; a public result record does not.

These names are source-local readiness evidence. They are not
`OutcomeEvaluationStatus`, do not construct or mutate `CallOutcome`, do not set
`dataComplete`, and make no retry, freshness, cancellation, scheduling,
provider, selector, or calculator claim.

## Canonical policy definition

`TargetHitReadinessPolicyVersion` contains exactly
`SUPPLIED_LEAF_TARGET_HIT_READINESS_V1`. Its definition is the following
single-line 2042-byte ASCII sequence encoded directly as UTF-8, with no BOM,
leading or trailing whitespace, or trailing newline:

```json
{"policyVersion":"SUPPLIED_LEAF_TARGET_HIT_READINESS_V1","requiredTargetHitOrchestrationPolicyVersion":"POINT_IN_TIME_TARGET_HIT_ORCHESTRATION_V1","requiredTargetHitOrchestrationPolicyDefinitionHash":"b91bf68958e42ad003b80973c74f9acc2dad8e4629f6a1905798df98aa8b5348","requestFields":["policyVersion","sourceResult"],"requestPresence":"ALL_FIELDS_NON_NULL","resolutionContextFields":["policyVersion","policyDefinitionHash"],"resultVariants":{"Settled":["context","sourceResult"],"AwaitingEndpoint":["context","sourceResult"],"EvidenceUnavailable":["context","sourceResult"]},"sourceInput":"COMPLETE_SUPPLIED_TARGET_HIT_ORCHESTRATION_RESOLUTION","sourceAttestationBoundary":"LOCAL_SOURCE_POLICY_AND_TYPED_SHAPE_ONLY_NO_ORIGINAL_INPUT_MEMBERSHIP_PIT_FILTERING_SELECTOR_OR_CALCULATOR_INVOCATION_CLAIM","classificationValidation":"RESULT_CONSTRUCTORS_AND_RESOLVER_SHARE_EXACT_CLASSIFICATION_VALIDATION","resolverInvocationAttestation":"ONLY_RESOLVE_ATTESTS_REQUEST_TO_RESULT_INVOCATION","branchPrecedence":["Available","NotApplicable","Pending","EligibilityUnavailable","FavorableExtremeUnavailable"],"branchMapping":{"Available":"SETTLED","NotApplicable":"SETTLED","Pending":"AWAITING_ENDPOINT","EligibilityUnavailable":"EVIDENCE_UNAVAILABLE","FavorableExtremeUnavailable":"EVIDENCE_UNAVAILABLE"},"awaitingEndpointChain":{"orchestrationVariant":"Pending","eligibilityPendingReason":"HORIZON_NOT_REACHED_AS_OF"},"typedVariantReasonRule":"NO_NESTED_REASON_REINTERPRETATION_OR_DUPLICATION","settledRule":"TARGET_HIT_AVAILABLE_OR_PERMANENTLY_NOT_APPLICABLE","sourcePreservation":"PRESERVE_EXACT_WHOLE_TARGET_HIT_ORCHESTRATION_RESOLUTION","reasonFlattening":"ABSENT","canonicalOutcomeStatus":"ABSENT","dataCompleteClaim":"ABSENT","retry":"ABSENT","freshness":"ABSENT","cancellation":"ABSENT","scheduling":"ABSENT","producerReplay":"ABSENT","selectorInvocation":"ABSENT","calculatorInvocation":"ABSENT","methodologyActivation":"ABSENT","inputFingerprint":"ABSENT","persistence":"ABSENT","aggregation":"ABSENT","ranking":"ABSENT","publication":"ABSENT"}
```

Its SHA-256 is
`8f81dee5227370d82dd91cd2fb8448797c7028eaa485dc64cf4bdc3cbf2f31a3`.
Every result context echoes this digest, and callers receive a defensive UTF-8
byte-array copy.

## Purity and verification boundary

The production surface is exactly:

- `TargetHitReadinessPolicyVersion.java`
- `TargetHitReadinessRequest.java`
- `TargetHitReadinessResolution.java`
- `TargetHitReadinessResolver.java`

The sole source-local test is `TargetHitReadinessResolverGoldenTest.java`. It
executes exactly 47 test invocations: six contract, null/shape, wrong direct
construction, equal-but-distinct replay, and locale/time-zone determinism
checks plus all 41 classification source shapes. The source matrix comprises
one Available shape, all three NotApplicable reasons, the sole Pending reason,
all 14 eligibility-unavailable reasons, and all 22 favorable-extreme
unavailable reasons. It proves exactly four settled, one awaiting, and 36
evidence-unavailable shapes, exact whole-source identity preservation, and the
absence of canonical lifecycle or runtime wiring.

No controller, application service, repository, schema, fixture, OpenAPI,
provider, clock, network, API, or web surface changes in this slice.

## External-data and deferred boundary

This supplied-leaf policy requires no API key, account, paid plan, provider
license, secret, or network access. Before non-DEMO production evidence enters
the system, P5 still must select entitled analyst-call, historical intraday or
tick, official-close, exchange-calendar, corporate-action, and asset/venue
providers and establish storage, display, derived-data, and redistribution
rights. Scoped secrets may be introduced only through approved local or CI
secret stores, never chat or Git.

Canonical lifecycle mapping across every required metric, cancellation and
latest-correction selection, retry and freshness policy, scheduling, producer
receipts, methodology activation, input fingerprinting, append-only
persistence, MFE/MAE, alpha and sector alpha, aggregation, ranking, and API/UI
publication remain later reviewed work.
