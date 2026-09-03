# ADR-015 — Point-in-Time Target-Error Calculation

- Status: Accepted
- Date: 2026-08-24

## Context

ADR-014 produces either one exact official endpoint close or explicit
unavailability. The existing target-hit primitive answers a different Boolean
question and does not define target error. A reproducible target-error metric
must lock target point-in-time evidence, exact identity matching, missing-state
composition, denominator, decimal scale, rounding, and overflow behavior
before any calculated value can be persisted or published.

The approved P3 V1 contract therefore adds a disconnected calculation leaf
over explicit target evidence and one complete ADR-014 endpoint-price
resolution. It neither obtains evidence nor publishes a product result.

## Decision

The tenth isolated P3 slice adds exactly five production types in
`com.wallstreetreceipts.api.domain.outcome.targeterror` and one matching
`TargetErrorCalculatorGoldenTest`.

- `TargetPriceEvidence` preserves exact evidence ID, ADR-010 original or
  correction basis, asset, primary venue, currency, ADR-014 adjustment basis,
  positive target, `availableAt`, `capturedAt`, and provenance. It requires
  `basis.eventTime <= availableAt <= capturedAt`, microsecond-precision times,
  and a target exactly representable as positive `NUMERIC(38,12)`.
- `TargetErrorInput` accepts exactly the V1 policy, one complete ADR-014
  endpoint-price resolution, and nullable target evidence. The endpoint
  resolution must use
  `OFFICIAL_PRIMARY_VENUE_CLOSE_SPLIT_ADJUSTED_V1` and exact definition hash
  `37e37aba9302d77366cef4129f77a82b7ccb2f1937bfffc0315ea8d0bc6b1f76`.
- Target evidence is known only when both `availableAt` and `capturedAt` are
  not after the endpoint context's `evaluationAsOf`. Null and future target
  evidence are identical missing-as-of input and produce equal complete
  results; future evidence cannot influence context, identity checks, or
  reason selection.
- Missing states compose exactly: missing target plus unavailable endpoint is
  `TARGET_AND_ENDPOINT_PRICE_UNAVAILABLE` with the exact nested endpoint
  reason; missing target only is `TARGET_MISSING_AS_OF`; unavailable endpoint
  only is `ENDPOINT_PRICE_UNAVAILABLE` with the exact nested endpoint reason.
- With complete evidence, identities are checked in exact order: basis, asset,
  primary venue, currency, and adjustment basis. Currency is exact with no FX.
- The sole formula is `abs(target - actual) / actual`, where `actual` is the
  resolved endpoint price. The calculation subtracts and takes the absolute
  value, then performs exactly one division by actual at scale 12 with
  `RoundingMode.HALF_EVEN`. The returned unit is a decimal ratio, not a percent.
  No intermediate or second rounding, float/double conversion, alternative
  denominator, tolerance, or fallback is permitted.
- Input prices are positive and exactly `NUMERIC(38,12)`-representable. Output
  must be nonnegative, have exact scale 12, and fit `NUMERIC(38,12)`; a rounded
  overflow returns `OUTPUT_NOT_REPRESENTABLE`, never a clipped or approximate
  number.
- `TargetErrorResult` is sealed as exactly `Available(context,targetError)` or
  `Unavailable(context,reason,endpointReason)`. Context preserves only the
  policy identity and complete endpoint resolution; target evidence is not
  echoed, which makes null and future-target results replayably equal.

The exact evaluation precedence is:

1. `TARGET_AND_ENDPOINT_PRICE_UNAVAILABLE`
2. `TARGET_MISSING_AS_OF`
3. `ENDPOINT_PRICE_UNAVAILABLE`
4. `BASIS_MISMATCH`
5. `ASSET_MISMATCH`
6. `PRIMARY_VENUE_MISMATCH`
7. `CURRENCY_MISMATCH`
8. `ADJUSTMENT_BASIS_MISMATCH`
9. calculate
10. `OUTPUT_NOT_REPRESENTABLE`

The exact unavailable-reason enum contains, in order,
`TARGET_MISSING_AS_OF`, `ENDPOINT_PRICE_UNAVAILABLE`,
`TARGET_AND_ENDPOINT_PRICE_UNAVAILABLE`, `BASIS_MISMATCH`, `ASSET_MISMATCH`,
`PRIMARY_VENUE_MISMATCH`, `CURRENCY_MISMATCH`,
`ADJUSTMENT_BASIS_MISMATCH`, and `OUTPUT_NOT_REPRESENTABLE`.

## Canonical policy definition

`TargetErrorPolicyVersion` contains exactly
`ACTUAL_DENOMINATOR_SCALE_12_HALF_EVEN_V1`. Its definition is exactly the
following single-line 1942-byte ASCII sequence encoded directly as UTF-8, with
the shown key order, punctuation, case, and values, with no byte-order mark,
surrounding whitespace, or trailing line ending:

```text
{"policyVersion":"ACTUAL_DENOMINATOR_SCALE_12_HALF_EVEN_V1","requiredEndpointPolicyVersion":"OFFICIAL_PRIMARY_VENUE_CLOSE_SPLIT_ADJUSTED_V1","requiredEndpointPolicyDefinitionHash":"37e37aba9302d77366cef4129f77a82b7ccb2f1937bfffc0315ea8d0bc6b1f76","endpointInput":"ENDPOINT_PRICE_RESOLUTION","targetEvidenceFields":["targetEvidenceId","basis","assetId","primaryVenueId","currency","adjustmentBasis","target","availableAt","capturedAt","provenanceId"],"targetPitPredicate":"availableAt<=endpoint.evaluationAsOf&&capturedAt<=endpoint.evaluationAsOf","targetEvidenceTemporalRule":"basis.eventTime<=availableAt<=capturedAt","futureTargetRule":"IDENTICAL_TO_NULL_AND_INVISIBLE_TO_OUTPUT","targetMissingReason":"TARGET_MISSING_AS_OF","missingEndpointTruthTable":{"targetMissingAsOf&&endpointUnavailable":"TARGET_AND_ENDPOINT_PRICE_UNAVAILABLE_WITH_EXACT_ENDPOINT_REASON","targetMissingAsOfOnly":"TARGET_MISSING_AS_OF","endpointUnavailableOnly":"ENDPOINT_PRICE_UNAVAILABLE_WITH_EXACT_ENDPOINT_REASON"},"basisRule":"target.basis==endpoint.horizon.context.basis","identityMatchPrecedence":["BASIS_MISMATCH","ASSET_MISMATCH","PRIMARY_VENUE_MISMATCH","CURRENCY_MISMATCH","ADJUSTMENT_BASIS_MISMATCH"],"evaluationPrecedence":["TARGET_AND_ENDPOINT_PRICE_UNAVAILABLE","TARGET_MISSING_AS_OF","ENDPOINT_PRICE_UNAVAILABLE","BASIS_MISMATCH","ASSET_MISMATCH","PRIMARY_VENUE_MISMATCH","CURRENCY_MISMATCH","ADJUSTMENT_BASIS_MISMATCH","CALCULATE","OUTPUT_NOT_REPRESENTABLE"],"currencyRule":"EXACT_MATCH_NO_FX","formula":"abs(target-actual)/actual","denominator":"ACTUAL_ENDPOINT_PRICE","inputBoundary":"POSITIVE_NUMERIC_38_12","divisionScale":12,"roundingMode":"HALF_EVEN","divisionCount":1,"outputUnits":"DECIMAL_RATIO","outputBoundary":"NONNEGATIVE_NUMERIC_38_12","outputOverflowReason":"OUTPUT_NOT_REPRESENTABLE","endpointUnavailableRule":"PRESERVE_EXACT_ENDPOINT_REASON","resultContext":"POLICY_IDENTITY_AND_ENDPOINT_RESOLUTION_ONLY","fallbackBehavior":"ABSENT"}
```

Its lowercase SHA-256 is exactly:

```text
31ca30555549f670e3c22d98ead16f7a02bfad198f36532effaf4a4b6931d074
```

`canonicalDefinitionUtf8()` returns a defensive byte-array copy, and every
calculation context echoes this exact hash. A changed denominator, unit,
rounding mode, scale, missing-state rule, identity rule, or byte requires a new
policy version and digest.

## Purity and integration boundary

Production adds exactly these five files:

- `TargetErrorCalculator.java`
- `TargetErrorInput.java`
- `TargetErrorPolicyVersion.java`
- `TargetErrorResult.java`
- `TargetPriceEvidence.java`

The calculator consumes supplied evidence only. It adds no schema, canonical
fixture, manifest member, JSON mapping, OpenAPI path, Flyway migration,
database row, controller, repository, application service, scheduler,
provider adapter, network call, API behavior, or web source. It does not invoke
target-hit or directional-win, calculate return/alpha/MFE/MAE, activate a
canonical model-only methodology, persist an outcome, or publish a metric.

No API key, account, paid plan, domain, vendor license, or network access is
needed for this disconnected contract. Real target and endpoint evidence can
enter it only after P5 provider selection and display, storage, derived-data,
primary-venue close, corporate-action, reference-data, and calendar rights are
established.

## Consequences

- The asymmetric actual-denominator formula and half-even boundary are
  replayable without percent/display ambiguity.
- Endpoint unavailability is never flattened: the exact ADR-014 nested reason
  survives both endpoint-only and combined missing states.
- Public result constructors validate only locally decidable shape and
  endpoint-reason consistency. Only `TargetErrorCalculator` attests target
  point-in-time visibility, precedence, identity matching, formula execution,
  one-division rounding, and output representability.
- Target-hit/directional-win orchestration, asset return, window metrics,
  methodology activation, fingerprints, persistence, aggregation, and product
  publication remain later reviewed work.
