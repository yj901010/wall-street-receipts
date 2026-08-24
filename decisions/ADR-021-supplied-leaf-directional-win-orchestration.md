# ADR-021 — Supplied-Leaf Point-in-Time Directional-Win Orchestration

- Status: Accepted
- Date: 2026-08-24

## Context

ADR-011 and ADR-013 preserve a caller-supplied canonical call direction as an
exact directional calculator side or an explicit neutral route. ADR-017
produces a signed scale-12 asset return or an exact unavailable result with its
complete price-pair and endpoint reason chain. ADR-006 defines the strict pure
directional-win sign comparison, but deliberately does not select or attest any
forecast, horizon, price, return, or point-in-time evidence.

These leaves must be composed without silently pairing another call, revision,
asset, or future forecast with a return; replaying any upstream producer; or
turning neutral or missing market evidence into `false` or a loss. This slice
also must not infer a canonical lifecycle state from an upstream unavailable
reason.

## Decision

The sixteenth isolated P3 slice adds disconnected directional-win composition
in `com.wallstreetreceipts.api.domain.outcome.directionalwinorchestration`.

- `DirectionalWinOrchestrationRequest` contains exactly the orchestration V1
  policy, complete `BasisForecastTermsEvidence`, a complete supplied
  `CalculatorSideRouting.Result`, and a complete supplied `AssetReturnResult`.
  All four fields are non-null, including the return leaf on a neutral route.
- The routing source must carry ADR-011's exact polarity policy and digest. Its
  canonical source direction must equal the terms direction exactly;
  `STRONG_BULLISH` is not interchangeable with `BULLISH` merely because both
  map to the same calculator side.
- The return must carry ADR-017's exact policy and digest. The terms' whole
  `OutcomeBasis` must equal the basis nested through return, price-pair,
  endpoint, and strict-horizon contexts. The terms asset must equal the nested
  endpoint binding asset. Terms availability and capture must both be at or
  before the nested endpoint `evaluationAsOf`; equality is visible.
- Every policy and correlation check runs before branch selection. A neutral
  route becomes `NotApplicable` and preserves the complete terms, route, and
  return leaf regardless of whether that return is available or unavailable.
  It never contains a Boolean and never invokes the primitive calculator.
- A directional route plus `AssetReturnResult.Unavailable` becomes
  `AssetReturnUnavailable` and preserves that exact object, including its typed
  asset-return reason, price-pair reason, endpoint resolution, and endpoint
  reason. Production does not call `.reason()`, inspect an unavailable enum, or
  reclassify `ENDPOINT_NOT_REACHED_AS_OF` as Pending.
- Only a directional route plus `AssetReturnResult.Available` constructs
  `DirectionalWinInput` from the exact preserved
  `DirectionalRoute.directionalWinSide()` and exact original
  `assetReturn()`, then invokes `DirectionalWinCalculator.calculate` once.
  Bullish requires return strictly greater than zero; bearish requires return
  strictly less than zero. Exact zero is a miss for both. No rounding,
  rescaling, tolerance, absolute value, percent conversion, or fallback exists.
- The sealed result has exactly three variants:
  `Available(context,termsEvidence,sideRouting,assetReturnResult,directionalWinResult)`,
  `NotApplicable(context,termsEvidence,sideRouting,assetReturnResult)`, and
  `AssetReturnUnavailable(context,termsEvidence,sideRouting,assetReturnResult)`.
  Context contains only orchestration policy version and digest.
- Complete directional and return evidence makes primitive
  `DirectionalWinResult.Unavailable` unreachable. If that primitive contract
  changes unexpectedly, orchestration fails closed with an internal invariant
  error and emits no fallback result.
- Public result constructors attest only locally decidable policy, correlation,
  and typed shape. Only `DirectionalWinOrchestrator.orchestrate(request)`
  attests the exact primitive input and invocation. Neither direct construction
  nor this orchestration re-attests a leaf producer's original request
  membership, PIT filtering, candidate poisoning/cardinality, or producer
  invocation.

`Available` means only that this disconnected directional-win metric leaf
produced a Boolean. It is not a canonical `CallOutcome`, does not set
`dataComplete`, and does not activate or publish a methodology.

## Canonical policy definition

`DirectionalWinOrchestrationPolicyVersion` contains exactly
`SUPPLIED_LEAF_DIRECTIONAL_WIN_ORCHESTRATION_V1`. Its definition is the
following single-line 3699-byte ASCII sequence encoded directly as UTF-8, with
no byte order mark, surrounding whitespace, or trailing line ending:

```text
{"policyVersion":"SUPPLIED_LEAF_DIRECTIONAL_WIN_ORCHESTRATION_V1","requiredPolarityPolicyVersion":"COLLAPSE_STRONG_DIRECTIONS_NEUTRAL_NON_DIRECTIONAL_V1","requiredPolarityPolicyDefinitionHash":"d83eccc92fedd7ba025745be2c8e78245bc308d0ff479467fa61afe543dc8a50","requiredAssetReturnPolicyVersion":"SIGNED_BASIS_DENOMINATOR_SCALE_12_HALF_EVEN_V1","requiredAssetReturnPolicyDefinitionHash":"e5e61c4adcd6567bfc76f73114499578f09de2254dc39a2553f3c0e2eaf03486","requestFields":["policyVersion","termsEvidence","sideRouting","assetReturnResult"],"requestPresence":"ALL_FIELDS_NON_NULL_INCLUDING_NON_DIRECTIONAL_ASSET_RETURN","resolutionContextFields":["policyVersion","policyDefinitionHash"],"resultVariants":{"Available":["context","termsEvidence","sideRouting","assetReturnResult","directionalWinResult"],"NotApplicable":["context","termsEvidence","sideRouting","assetReturnResult"],"AssetReturnUnavailable":["context","termsEvidence","sideRouting","assetReturnResult"]},"termsInput":"COMPLETE_SUPPLIED_BASIS_FORECAST_TERMS_EVIDENCE","routingInput":"COMPLETE_SUPPLIED_CALCULATOR_SIDE_ROUTING_RESULT","assetReturnInput":"COMPLETE_SUPPLIED_ASSET_RETURN_RESULT_REQUIRED_FOR_ALL_ROUTING_BRANCHES","leafAttestationBoundary":"LOCAL_CONSISTENCY_ONLY_NO_REQUEST_MEMBERSHIP_PIT_FILTERING_OR_PRODUCER_INVOCATION_CLAIM","routingPolicyRule":"SOURCE_POLARITY_CONTEXT_MUST_USE_REQUIRED_POLICY_AND_HASH","directionCorrelation":"termsEvidence.direction==sideRouting.source.context.direction_EXACT_CANONICAL_DIRECTION","basisCorrelation":"termsEvidence.basis==assetReturnResult.context.pricePairResolution.context.endpointPriceResolution.context.horizonResolution.window.context.basis_BY_WHOLE_RECORD_EQUALITY","assetCorrelation":"termsEvidence.assetId==assetReturnResult.context.pricePairResolution.context.endpointPriceResolution.context.binding.assetId","evaluationAsOfSource":"assetReturnResult.context.pricePairResolution.context.endpointPriceResolution.context.evaluationAsOf","termsVisibilityRule":"termsEvidence.availableAt<=evaluationAsOf&&termsEvidence.capturedAt<=evaluationAsOf","correlationFailure":"REJECT_REQUEST","branchPrecedence":["NonDirectionalRoute","DirectionalRoute+AssetReturn.Unavailable","DirectionalRoute+AssetReturn.Available"],"branchMapping":{"NonDirectionalRoute+AnyAssetReturnResult":"NOT_APPLICABLE_PRESERVE_ALL_SUPPLIED_LEAVES","DirectionalRoute+AssetReturn.Unavailable":"PRESERVE_ASSET_RETURN_UNAVAILABLE","DirectionalRoute+AssetReturn.Available":"INVOKE_DIRECTIONAL_WIN"},"notApplicableReasonSource":"PRESERVED_NON_DIRECTIONAL_ROUTE_SOURCE_REASON","nestedReasonRule":"PRESERVE_EXACT_TYPED_ASSET_RETURN_PRICE_PAIR_AND_ENDPOINT_RESOLUTIONS_WITHOUT_REASON_MAPPING","resolvedSideSource":"sideRouting.directionalWinSide","resolvedAssetReturnSource":"assetReturnResult.assetReturn","directionalWinInputFields":["side","assetReturn"],"calculator":"DirectionalWinCalculator.calculate","calculatorInvocation":"EXACTLY_ONCE_ONLY_FOR_DIRECTIONAL_AND_ASSET_RETURN_AVAILABLE","calculatorUnavailableRule":"INVARIANT_VIOLATION_FAIL_CLOSED_WITHOUT_RESULT","comparison":{"BULLISH":"assetReturn.compareTo(ZERO)>0","BEARISH":"assetReturn.compareTo(ZERO)<0"},"zeroRule":"MISS_FOR_BOTH_SIDES","selectedValueRule":"PRESERVE_ORIGINAL_BIGDECIMAL_NO_ROUNDING_OR_RESCALE","targetDispositionUse":"ABSENT","polarityResolverInvocation":"ABSENT","calculatorSideRoutingInvocation":"ABSENT","assetReturnCalculatorInvocation":"ABSENT","pricePairSelectorInvocation":"ABSENT","endpointSelectorInvocation":"ABSENT","returnRecalculation":"ABSENT","fallbackBehavior":"ABSENT","methodologyActivation":"ABSENT","inputFingerprint":"ABSENT","persistence":"ABSENT","aggregation":"ABSENT","ranking":"ABSENT","publication":"ABSENT"}
```

Its lowercase SHA-256 is exactly:

```text
51429c7601d4807162855f08c680d1e6bb7895f87fc108e141e5ad3a3ab25bcb
```

`canonicalDefinitionUtf8()` returns a defensive byte-array copy and every
orchestration context echoes this digest. Any changed field, branch, source,
correlation, comparison, invocation condition, attestation boundary, or byte
requires a new policy version and digest.

## Purity and integration boundary

Production adds exactly four files:

- `DirectionalWinOrchestrationPolicyVersion.java`
- `DirectionalWinOrchestrationRequest.java`
- `DirectionalWinOrchestrationResolution.java`
- `DirectionalWinOrchestrator.java`

The matching source-local test is exactly
`DirectionalWinOrchestratorGoldenTest.java`. It executes 84 vectors: 15
contract/correlation/PIT/replay checks, all four directional source directions,
six strict sign boundaries, all 55 unavailable return/price-pair/endpoint
combinations, and four neutral-precedence return states.

The slice invokes no polarity resolver, routing producer, asset-return
calculator, price-pair selector, or endpoint selector. It adds no schema,
fixture, manifest member, OpenAPI path, Flyway migration, database row,
controller, repository, application service, scheduler, provider adapter,
network call, API behavior, or web source. It does not fingerprint, persist,
aggregate, rank, schedule, or publish a metric.

No API key, account, paid plan, domain, provider license, named secret, or
network access is needed. Real evidence still requires selected analyst-call,
official-close, exchange-calendar, corporate-action, and asset/venue reference
providers plus documented storage, display, derived-data, and redistribution
rights. Only after those choices exist may a reviewed P5 adapter introduce a
named scoped secret through approved local/CI secret stores; secrets must never
be supplied in chat or committed to Git.

## Consequences and deferred work

- Directional-win composition is deterministic and replayable without
  collapsing neutral or unavailable meanings into a loss.
- `AssetReturnUnavailable` is a source-local composition branch, not a
  canonical lifecycle state. Pending promotion, retry, and scheduling require
  a later explicit lifecycle policy.
- Request membership, latest-correction/cancellation selection, producer
  receipts, canonical methodology activation, input fingerprinting,
  append-only persistence, MFE/MAE, alpha and sector alpha, aggregation,
  ranking, API/UI publication, and production provider integration remain
  later reviewed P3/P5 work.
