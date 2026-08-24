# ADR-018 — Point-in-Time Target-Hit Input Eligibility

- Status: Accepted
- Date: 2026-08-24

## Context

ADR-011 through ADR-013 reduce one source direction and preserve its closed
calculator-side route, but that route alone does not identify the original or
correction forecast basis that supplied the direction. ADR-006 also requires a
target and one favorable full-window extreme, while ADR-015's nullable
normalized target evidence cannot distinguish a source-attested absence from
evidence that is missing or not yet known.

Before a later orchestrator may invoke target hit, the system must therefore
prove that the exact basis terms, route, normalized target, strict horizon, and
calendar evidence are mutually coherent and point-in-time visible. This policy
only decides whether those inputs are ready to seek full-window evidence. It
does not select a high or low, calculate a metric, or publish an outcome.

## Decision

The thirteenth isolated P3 slice adds exactly five production types in
`com.wallstreetreceipts.api.domain.outcome.targeteligibility` and one matching
`TargetEligibilityResolverGoldenTest`.

- `BasisForecastTermsEvidence` binds one exact original/correction
  `OutcomeBasis` and asset to the source `CallDirection`, provider-event
  identity, provenance, PIT timestamps, and a sealed target disposition.
  `TargetDisposition.Present(sourceTarget,sourceTargetCurrency,targetDate)`
  requires a positive source target exactly representable as
  `NUMERIC(38,12)` plus currency. `TargetDisposition.Absent` carries no target
  terms. Absence is source evidence, never an inference from a missing
  normalized target.
- `TargetEligibilityRequest` supplies the V1 policy, one ADR-010 strict-horizon
  resolution (resolved or incomplete), evaluation as-of, nullable basis terms,
  nullable closed ADR-013 route, nullable normalized ADR-015
  `TargetPriceEvidence`, and nullable ADR-014 calendar PIT evidence. It obtains
  none of those inputs.
- Evidence is visible only when each required `availableAt` and `capturedAt`
  is not after `evaluationAsOf`. A future terms, target, or catalog record is
  invisible before identity or reason selection and produces the same complete
  result as its absent counterpart.
- The exact forecast basis is preserved. Known terms must match the complete
  strict-horizon basis, the route must preserve the same source direction, the
  normalized target must preserve the same basis, asset, and currency, and
  catalog ID and revision must match the strict-horizon context. Call-ID-only,
  latest-revision, currency-conversion, direction reinterpretation, or fallback
  matching is absent.
- A directional present target with a known source target date does not trigger
  expiry, date-zone conversion, window shortening, or any other inferred
  semantics. Because no approved target-date methodology exists, that branch
  fails closed as `TARGET_DATE_SEMANTICS_UNSUPPORTED`. A non-directional route,
  or target absence without a visible contradictory target, reaches its
  explicit not-applicable branch first.
- A known source-attested absent target and a known neutral route are explicit
  not-applicable evidence, separately or together. They are not a miss, loss,
  false result, missing target, cancelled call, or incomplete market window.
  This branch requires no visible normalized target: if source terms attest
  absence while a normalized target is already PIT-visible, V1 preserves that
  target and fails closed as `TARGET_STATE_CONFLICT`. A future target remains
  invisible and is identical to null, so it cannot create a conflict.
- A resolved strict horizon whose endpoint close is after `evaluationAsOf` is
  `Pending(HORIZON_NOT_REACHED_AS_OF)`; equality is mature. After the earlier
  applicability and target/catalog gates pass, an incomplete ADR-010 horizon
  preserves the exact nested `FIRST_ELIGIBLE_SESSION_MISSING` or
  `HORIZON_ENDPOINT_SESSION_MISSING` reason. Readiness requires the resolved
  endpoint to have been reached and exact known catalog evidence.
- `TargetEligibilityResolution` is sealed as exactly
  `ReadyForWindowEvidence`, `Pending`, `NotApplicable`, or `Unavailable`.
  Its context preserves policy identity, the complete strict-horizon
  resolution, and `evaluationAsOf`. Branch evidence preserves only PIT-visible
  terms, route, normalized target, and catalog records permitted by that
  branch; invisible future evidence never leaks into output.

The exact pending reason is `HORIZON_NOT_REACHED_AS_OF`. The exact
not-applicable reasons are, in order:

1. `TARGET_ABSENT`
2. `NON_DIRECTIONAL`
3. `TARGET_ABSENT_AND_NON_DIRECTIONAL`

The exact unavailable reasons are, in order:

1. `BASIS_TERMS_NOT_KNOWN_AS_OF`
2. `HORIZON_BASIS_MISMATCH`
3. `ROUTE_MISSING`
4. `ROUTE_DIRECTION_MISMATCH`
5. `TARGET_STATE_CONFLICT`
6. `TARGET_DATE_SEMANTICS_UNSUPPORTED`
7. `TARGET_EVIDENCE_NOT_KNOWN_AS_OF`
8. `TARGET_EVIDENCE_BASIS_MISMATCH`
9. `TARGET_ASSET_MISMATCH`
10. `TARGET_CURRENCY_MISMATCH`
11. `CATALOG_NOT_KNOWN_AS_OF`
12. `CATALOG_EVIDENCE_MISMATCH`
13. `FIRST_ELIGIBLE_SESSION_MISSING`
14. `HORIZON_ENDPOINT_SESSION_MISSING`

The resolver's exact evaluation precedence is:

1. `BASIS_TERMS_NOT_KNOWN_AS_OF`
2. `HORIZON_BASIS_MISMATCH`
3. `ROUTE_MISSING`
4. `ROUTE_DIRECTION_MISMATCH`
5. `TARGET_STATE_CONFLICT`
6. `TARGET_ABSENT_AND_NON_DIRECTIONAL`
7. `TARGET_ABSENT`
8. `NON_DIRECTIONAL`
9. `TARGET_DATE_SEMANTICS_UNSUPPORTED`
10. `TARGET_EVIDENCE_NOT_KNOWN_AS_OF`
11. `TARGET_EVIDENCE_BASIS_MISMATCH`
12. `TARGET_ASSET_MISMATCH`
13. `TARGET_CURRENCY_MISMATCH`
14. `CATALOG_NOT_KNOWN_AS_OF`
15. `CATALOG_EVIDENCE_MISMATCH`
16. `FIRST_ELIGIBLE_SESSION_MISSING`
17. `HORIZON_ENDPOINT_SESSION_MISSING`
18. `HORIZON_NOT_REACHED_AS_OF`
19. `READY_FOR_WINDOW_EVIDENCE`

## Policy and ownership boundary

`TargetEligibilityPolicyVersion` contains exactly
`POINT_IN_TIME_TARGET_HIT_INPUT_READINESS_V1`. Its definition is exactly the
following single-line 3862-byte ASCII sequence encoded directly as UTF-8, with
no byte-order mark, surrounding whitespace, or trailing line ending:

```text
{"policyVersion":"POINT_IN_TIME_TARGET_HIT_INPUT_READINESS_V1","requiredHorizonPolicyVersion":"STRICTLY_AFTER_BASIS_EVENT_SESSION_CLOSE_V1","requiredHorizonPolicyDefinitionHash":"550087efe7ddf2ba31974c89c2740ab79df986eefef48919c32c56a3232f8dc1","requiredDirectionPolicyVersion":"COLLAPSE_STRONG_DIRECTIONS_NEUTRAL_NON_DIRECTIONAL_V1","requiredDirectionPolicyDefinitionHash":"d83eccc92fedd7ba025745be2c8e78245bc308d0ff479467fa61afe543dc8a50","basisTermsEvidenceFields":["termsEvidenceId","basis","assetId","direction","targetDisposition","provider","providerEventId","availableAt","capturedAt","provenanceId"],"requestFields":["policyVersion","horizonResolution","termsEvidence","sideRouting","targetEvidence","catalogEvidence","evaluationAsOf"],"resolutionContextFields":["policyVersion","policyDefinitionHash","horizonResolution","evaluationAsOf"],"eligibilityEvidenceFields":["termsEvidence","sideRouting","targetEvidence","catalogEvidence"],"resultVariants":{"ReadyForWindowEvidence":["context","evidence"],"Pending":["context","evidence","reason"],"NotApplicable":["context","evidence","reason"],"Unavailable":["context","evidence","reason","horizonReason"]},"basisModes":["ORIGINAL","CORRECTION"],"cancellationBasisAllowed":false,"cancellationEligibility":"NOT_ATTESTED","termsPitPredicate":"availableAt<=evaluationAsOf&&capturedAt<=evaluationAsOf","futureTermsRule":"IDENTICAL_TO_NULL_AND_INVISIBLE_TO_OUTPUT","targetDispositionVariants":{"Present":["sourceTarget","sourceTargetCurrency","targetDate"],"Absent":[]},"presentTargetEvidence":"TargetPriceEvidence","sourceAndNormalizedTargetValues":"PRESERVED_SEPARATELY_NO_NUMERIC_EQUALITY_INFERENCE","targetPitPredicate":"availableAt<=evaluationAsOf&&capturedAt<=evaluationAsOf","futureTargetRule":"IDENTICAL_TO_NULL_MISSING_AS_OF_NOT_ABSENT","catalogPitPredicate":"availableAt<=evaluationAsOf&&capturedAt<=evaluationAsOf","futureCatalogRule":"IDENTICAL_TO_NULL_AND_INVISIBLE_TO_OUTPUT","catalogIdentity":"calendarId==horizon.context.calendarId&&catalogRevision==horizon.context.catalogRevision","basisIdentity":"terms.basis==horizon.context.basis&&target.basis==terms.basis","assetIdentity":"target.assetId==terms.assetId","routeDirectionIdentity":"route.source.context.direction==terms.direction","targetCurrencyIdentity":"target.currency==terms.sourceTargetCurrency","targetDateRule":"NON_NULL_UNSUPPORTED_FOR_DIRECTIONAL_PRESENT_TARGET","nonDirectionalRule":"NOT_APPLICABLE_NOT_FALSE_OR_LOSS","absentTargetRule":"NOT_APPLICABLE_NOT_MISSING","absentVisibleTargetRule":"TARGET_STATE_CONFLICT_BEFORE_NOT_APPLICABLE","notApplicableTruthTable":{"absent&&nonDirectional":"TARGET_ABSENT_AND_NON_DIRECTIONAL","absentOnly":"TARGET_ABSENT","nonDirectionalOnly":"NON_DIRECTIONAL"},"catalogRequiredOnlyAfterDirectionalPresentTargetEvidence":true,"horizonMaturity":"endpointSession.closesAt<=evaluationAsOf","maturityEquality":"READY","horizonIncompleteReason":"PRESERVE_EXACT_NESTED_REASON","readyMeaning":"READY_FOR_LATER_FULL_WINDOW_EVIDENCE_ONLY","calculatorInvocation":"ABSENT","statusVariants":["READY_FOR_WINDOW_EVIDENCE","PENDING","NOT_APPLICABLE","UNAVAILABLE"],"evaluationPrecedence":["BASIS_TERMS_NOT_KNOWN_AS_OF","HORIZON_BASIS_MISMATCH","ROUTE_MISSING","ROUTE_DIRECTION_MISMATCH","TARGET_STATE_CONFLICT","TARGET_ABSENT_AND_NON_DIRECTIONAL","TARGET_ABSENT","NON_DIRECTIONAL","TARGET_DATE_SEMANTICS_UNSUPPORTED","TARGET_EVIDENCE_NOT_KNOWN_AS_OF","TARGET_EVIDENCE_BASIS_MISMATCH","TARGET_ASSET_MISMATCH","TARGET_CURRENCY_MISMATCH","CATALOG_NOT_KNOWN_AS_OF","CATALOG_EVIDENCE_MISMATCH","FIRST_ELIGIBLE_SESSION_MISSING","HORIZON_ENDPOINT_SESSION_MISSING","HORIZON_NOT_REACHED_AS_OF","READY_FOR_WINDOW_EVIDENCE"],"selectedEvidencePreservation":"EXACT_COMPLETE_RECORDS","branchClearingRule":"EVIDENCE_AFTER_DECIDING_PRECEDENCE_GATE_IS_NULL","futureEvidenceOutputRule":"NEVER_ECHOED","fallbackBehavior":"ABSENT"}
```

Its lowercase SHA-256 is exactly
`a6b4c9f4e4d29b5f1a9b0c300e2d7b9505318c708dfb0ad0e88f71324cf65465`.
Returned bytes are defensive, and every context echoes this exact digest. The
definition binds complete evidence fields, PIT predicates, identity and
precedence rules, branch clearing, exact selected-record preservation, closed
result variants, and all no-inference boundaries. Any changed field, reason,
precedence, temporal rule, or byte requires a new policy version and digest.

Public records enforce only locally decidable null, shape, policy, identity,
temporal, and branch-consistency invariants. Only
`TargetEligibilityResolver` attests PIT filtering, evaluation precedence,
route/basis/target/catalog coherence, endpoint readiness, and the selected
branch. Direct record construction does not attest request membership or
reason correctness.

## Purity and external-data boundary

This is a disconnected source-local policy. It does not invoke
`TargetHitCalculator`, `DirectionalWinCalculator`, `AssetReturnCalculator`, or
`TargetErrorCalculator`; select a session, price, bar, high, or low; infer a
latest correction or cancellation; activate a methodology; fingerprint,
persist, aggregate, or publish an outcome; or change schemas, fixtures,
OpenAPI, Flyway, database, providers, API behavior, or web source.

No API key, account, paid plan, domain, provider license, named secret, or
network access is needed. P5 still owns the provider and rights required for
non-DEMO forecast terms, targets, calendars, historical windows, storage,
display, derived data, and redistribution before an adapter or scoped secret
may be introduced.

## Consequences

- Known target absence, neutral direction, pending horizon, missing evidence,
  and a fully ready target-hit input have distinct replayable meanings.
- Original and each valid correction remain independent forecast lineages;
  latest-correction and cancellation eligibility are not inferred.
- `ReadyForWindowEvidence` is not a target hit and does not attest any bar or
  full-window completeness. The next reviewed slice must select exact
  point-in-time full-window high/low evidence with inclusivity, identity,
  adjustment, continuity, and ambiguity rules.
- Only after that selector exists may a later reviewed orchestrator invoke the
  calculators. Methodology activation, input fingerprints, persistence,
  aggregation, ranking, and product publication remain deferred.
