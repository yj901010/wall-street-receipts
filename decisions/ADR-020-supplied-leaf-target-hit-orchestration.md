# ADR-020 — Supplied-Leaf Point-in-Time Target-Hit Orchestration

- Status: Accepted
- Date: 2026-08-24

## Context

ADR-018 produces a complete target-hit readiness branch without choosing a
window extreme. ADR-019 produces a side-selected, upstream-attested causal
window extreme or one exact unavailability branch without comparing it to the
target. ADR-006 defines the inclusive pure comparison but deliberately cannot
verify side, target, window, adjustment, currency, or point-in-time evidence.

The three contracts must be composed without turning pending, permanent
non-applicability, missing evidence, or an upstream mismatch into `false`, a
loss, or a primitive missing-input reason. The composition also cannot claim
that public leaf records prove their original request membership: ADR-018 and
ADR-019 explicitly reserve those attestations to their resolver and selector.

## Decision

The fifteenth isolated P3 slice adds a disconnected target-hit composition in
`com.wallstreetreceipts.api.domain.outcome.targethitorchestration`.

- `TargetHitOrchestrationRequest` contains exactly the orchestration V1 policy,
  one complete supplied `TargetEligibilityResolution`, and a conditional
  supplied `FavorableExtremeResolution`. It accepts no competing as-of,
  horizon, side, target, extreme, high/low pair, or Boolean.
- Pending, NotApplicable, and eligibility Unavailable require a null favorable
  resolution. Ready requires one non-null favorable resolution whose nested
  ready eligibility is whole-record equal to the supplied readiness. Structural
  equality permits deterministic replay after deserialization; object identity
  is not required.
- Missing favorable evidence is represented by ADR-019's exact sealed
  `Unavailable` branch. Omitting the entire ADR-019 result for a Ready input is
  therefore malformed orchestration, not financial-data unavailability, and
  request construction fails closed. A stale favorable result on any non-ready
  branch also fails rather than being ignored.
- The sealed result has exactly five typed variants:
  `Available(context,favorableExtremeResolution,targetHitResult)`,
  `Pending(context,eligibilityResolution)`,
  `NotApplicable(context,eligibilityResolution)`,
  `EligibilityUnavailable(context,eligibilityResolution)`, and
  `FavorableExtremeUnavailable(context,favorableExtremeResolution)`.
  Nested leaf records retain their complete context, evidence, exact reason,
  and any nested horizon reason. No outer reason mapping or flattening exists.
- Only Ready plus matching favorable Resolved constructs `TargetHitInput`.
  Side comes exactly from the preserved `DirectionalRoute.targetHitSide()`,
  target from normalized `TargetPriceEvidence.target()`, and the extreme from
  the selector's `FavorableExtreme.value()`. Source target terms are never used
  as the normalized comparison target, and strong directions are not
  reinterpreted.
- `TargetHitCalculator.calculate` is invoked exactly once on that branch.
  Bullish remains inclusive `favorableExtreme >= target`; bearish remains
  inclusive `favorableExtreme <= target`; equality is a hit. No rounding,
  rescaling, tolerance, conversion, high/low reselection, endpoint-close
  fallback, or raw aggregation is added.
- Complete upstream inputs make primitive `TargetHitResult.Unavailable`
  unreachable. If the primitive ever returns it, the orchestrator raises an
  internal invariant violation and emits no result rather than converting a
  programming error into a domain evidence state.
- Public orchestration result constructors validate policy and local typed
  branch shape only. Only `TargetHitOrchestrator.orchestrate(request)` attests
  the exact primitive input construction and invocation. Neither direct result
  construction nor orchestration attests that a leaf producer saw its original
  full request, performed PIT filtering, or proved request-wide candidate
  membership and cardinality.

`Available` means only that this disconnected target-hit metric leaf produced a
Boolean. It is not a canonical `CallOutcome` in `CALCULATED` state, does not set
`dataComplete`, and does not activate a methodology.

## Canonical policy definition

`TargetHitOrchestrationPolicyVersion` contains exactly
`POINT_IN_TIME_TARGET_HIT_ORCHESTRATION_V1`. Its definition is the following
single-line 3082-byte ASCII sequence encoded directly as UTF-8, with no byte
order mark, surrounding whitespace, or trailing line ending:

```text
{"policyVersion":"POINT_IN_TIME_TARGET_HIT_ORCHESTRATION_V1","requiredEligibilityPolicyVersion":"POINT_IN_TIME_TARGET_HIT_INPUT_READINESS_V1","requiredEligibilityPolicyDefinitionHash":"a6b4c9f4e4d29b5f1a9b0c300e2d7b9505318c708dfb0ad0e88f71324cf65465","requiredFavorableExtremePolicyVersion":"POINT_IN_TIME_ATTESTED_CAUSAL_WINDOW_HIGH_LOW_V1","requiredFavorableExtremePolicyDefinitionHash":"e3a0e93030c8f09ae5398bf6df0f2e28eec14b0a31f5bea240fc78f2412c2463","requestFields":["policyVersion","eligibilityResolution","favorableExtremeResolution"],"resolutionContextFields":["policyVersion","policyDefinitionHash"],"resultVariants":{"Available":["context","favorableExtremeResolution","targetHitResult"],"Pending":["context","eligibilityResolution"],"NotApplicable":["context","eligibilityResolution"],"EligibilityUnavailable":["context","eligibilityResolution"],"FavorableExtremeUnavailable":["context","favorableExtremeResolution"]},"eligibilityInput":"COMPLETE_SUPPLIED_TARGET_ELIGIBILITY_RESOLUTION","favorableExtremeInput":"CONDITIONAL_COMPLETE_SUPPLIED_FAVORABLE_EXTREME_RESOLUTION","leafAttestationBoundary":"LOCAL_CONSISTENCY_ONLY_NO_REQUEST_MEMBERSHIP_PIT_FILTERING_OR_SELECTOR_PRODUCTION_CLAIM","eligibilityPrecedence":["Pending","NotApplicable","Unavailable","ReadyForWindowEvidence"],"nonReadyFavorableExtremeRule":"MUST_BE_NULL_AND_NOT_EVALUATED","readyFavorableExtremeRule":"MUST_BE_NON_NULL","crossResolutionIdentity":"favorableExtreme.context.readyEligibility==eligibilityResolution_BY_WHOLE_RECORD_EQUALITY","branchMapping":{"Pending":"PRESERVE_PENDING","NotApplicable":"PRESERVE_NOT_APPLICABLE","Unavailable":"PRESERVE_ELIGIBILITY_UNAVAILABLE","ReadyForWindowEvidence+FavorableExtreme.Unavailable":"PRESERVE_FAVORABLE_EXTREME_UNAVAILABLE","ReadyForWindowEvidence+FavorableExtreme.Resolved":"INVOKE_TARGET_HIT"},"nestedReasonRule":"PRESERVE_EXACT_TYPED_LEAF_RESOLUTION_WITHOUT_REASON_MAPPING","resolvedSideSource":"eligibilityResolution.evidence.sideRouting.directionalRoute.targetHitSide","resolvedTargetSource":"eligibilityResolution.evidence.targetEvidence.target","resolvedFavorableExtremeSource":"favorableExtremeResolution.favorableExtreme.value","targetHitInputFields":["side","target","favorableExtreme"],"calculator":"TargetHitCalculator.calculate","calculatorInvocation":"EXACTLY_ONCE_ONLY_FOR_READY_AND_RESOLVED","calculatorUnavailableRule":"INVARIANT_VIOLATION_FAIL_CLOSED_WITHOUT_RESULT","comparison":{"BULLISH":"favorableExtreme.compareTo(target)>=0","BEARISH":"favorableExtreme.compareTo(target)<=0"},"equalityRule":"HIT","selectedValueRule":"PRESERVE_ORIGINAL_BIGDECIMAL_NO_ROUNDING_OR_RESCALE","strongDirectionRule":"INHERITED_FROM_DIRECTIONAL_ROUTE_NO_REINTERPRETATION","sourceTargetInput":"ABSENT","highLowReselection":"ABSENT","endpointCloseFallback":"ABSENT","eligibilityResolverInvocation":"ABSENT","favorableExtremeSelectorInvocation":"ABSENT","rawAggregation":"ABSENT","methodologyActivation":"ABSENT","inputFingerprint":"ABSENT","persistence":"ABSENT","aggregation":"ABSENT","ranking":"ABSENT","publication":"ABSENT","fallbackBehavior":"ABSENT"}
```

Its lowercase SHA-256 is exactly:

```text
b91bf68958e42ad003b80973c74f9acc2dad8e4629f6a1905798df98aa8b5348
```

`canonicalDefinitionUtf8()` returns a defensive byte-array copy and every
orchestration context echoes this digest. Any changed field, branch, source,
comparison, invocation condition, attestation boundary, or byte requires a new
policy version and digest.

## Purity and integration boundary

Production adds exactly four files:

- `TargetHitOrchestrationPolicyVersion.java`
- `TargetHitOrchestrationRequest.java`
- `TargetHitOrchestrationResolution.java`
- `TargetHitOrchestrator.java`

The matching source-local test is exactly
`TargetHitOrchestratorGoldenTest.java`. It executes 55 vectors covering four
directional routes, all three non-applicable reasons, all 14 eligibility
unavailable reasons including both nested horizon reasons, all 22 favorable
extreme unavailable reasons, exact equality/miss boundaries, normalized target
selection, correction identity, malformed compositions, and deterministic
replay.

The slice invokes no eligibility resolver or favorable-extreme selector,
selects no raw observation, and adds no schema, fixture, manifest member,
OpenAPI path, Flyway migration, database row, controller, repository,
application service, scheduler, provider adapter, network call, API behavior,
or web source. It does not fingerprint, persist, aggregate, rank, or publish a
metric.

No API key, account, paid plan, domain, provider license, named secret, or
network access is needed. Real evidence still requires a selected provider and
documented entitlements for historical intraday/tick data, exchange calendars,
corporate actions, asset/venue reference data, storage, display, derived-data
creation, and redistribution. Only after those choices exist may a reviewed P5
adapter introduce a named scoped secret; secrets must be supplied through the
approved local/CI secret store and never chat or Git.

## Consequences and deferred work

- Target-hit metric composition is deterministic and replayable without
  collapsing upstream lifecycle meanings.
- Source-local `Available` remains disconnected and unpublished.
- Leaf-producer receipts, request membership proofs, canonical methodology
  activation, input fingerprinting, append-only outcome persistence, lifecycle
  mapping, cancellation and latest-correction selection, scheduling/retry,
  raw-tick aggregation, directional-win orchestration, MFE/MAE, alpha,
  aggregation, ranking, API/UI publication, and production provider integration
  remain later reviewed P3/P5 work.
