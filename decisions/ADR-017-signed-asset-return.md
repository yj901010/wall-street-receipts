# ADR-017 — Signed Asset Return

- Status: Accepted
- Date: 2026-08-24

## Context

ADR-016 produces either one point-in-time coherent basis/endpoint price pair or
explicit unavailability. The existing directional-win primitive intentionally
accepts a caller-supplied return and does not define its formula. A reproducible
asset return must lock denominator, sign, decimal unit, scale, rounding, and
overflow before it can feed directional evaluation or publication.

## Decision

The twelfth isolated P3 slice adds exactly four production types in
`com.wallstreetreceipts.api.domain.outcome.assetreturn` and one matching
`AssetReturnCalculatorGoldenTest`.

- `AssetReturnInput` accepts exactly the V1 policy and one ADR-016 price-pair
  resolution using the exact required version and definition hash.
- Pair unavailability maps to `PRICE_PAIR_UNAVAILABLE` while preserving the
  exact nested pair reason. It is never converted to zero, false, or a loss.
- A resolved pair computes exactly `(endpoint - basis) / basis`: one
  subtraction, the positive basis price as denominator, and exactly one
  division at scale 12 with `RoundingMode.HALF_EVEN`. No intermediate or second
  rounding, percent conversion, tolerance, float/double conversion, alternate
  denominator, or fallback is permitted.
- Output units are a signed decimal ratio. Exact `-1.000000000000` is valid;
  no positive endpoint can mathematically produce less than -1, and a directly
  constructed or otherwise invalid value below -1 fails closed. Output must
  have exact scale 12 and precision at most 38; rounded overflow is explicit
  `OUTPUT_NOT_REPRESENTABLE`.
- `AssetReturnResult` is sealed as exactly
  `Available(context,assetReturn)` or
  `Unavailable(context,reason,pricePairReason)`. Context preserves only the
  policy identity and complete ADR-016 resolution.

The exact unavailable-reason enum is, in order,
`PRICE_PAIR_UNAVAILABLE` and `OUTPUT_NOT_REPRESENTABLE`. Evaluation order is
price-pair unavailability, calculation, then output representability.

## Canonical policy definition

`AssetReturnPolicyVersion` contains exactly
`SIGNED_BASIS_DENOMINATOR_SCALE_12_HALF_EVEN_V1`. Its definition is exactly the
following single-line 1011-byte ASCII sequence encoded directly as UTF-8, with
no byte-order mark, surrounding whitespace, or trailing line ending:

```text
{"policyVersion":"SIGNED_BASIS_DENOMINATOR_SCALE_12_HALF_EVEN_V1","requiredPricePairPolicyVersion":"SOURCE_RECORDED_BASIS_EVENT_TO_OFFICIAL_ENDPOINT_PRICE_PAIR_V1","requiredPricePairPolicyDefinitionHash":"895e4bc97ebb3a92b80f2c58e2d28abb94440eeca963046ee755fa98825f4887","pricePairInput":"ASSET_RETURN_PRICE_PAIR_RESOLUTION","unavailableRule":"PRESERVE_EXACT_PRICE_PAIR_REASON","evaluationPrecedence":["PRICE_PAIR_UNAVAILABLE","CALCULATE","OUTPUT_NOT_REPRESENTABLE"],"formula":"(endpoint-basis)/basis","denominator":"BASIS_PRICE","subtractionCount":1,"divisionCount":1,"divisionScale":12,"roundingMode":"HALF_EVEN","outputUnits":"SIGNED_DECIMAL_RATIO","inputBoundary":"POSITIVE_NUMERIC_38_12_PRICE_PAIR","outputBoundary":"SIGNED_NUMERIC_38_12_AT_LEAST_NEGATIVE_ONE","outputOverflowReason":"OUTPUT_NOT_REPRESENTABLE","intermediateRounding":"ABSENT","secondRounding":"ABSENT","floatOrDoubleConversion":"ABSENT","resultContext":"POLICY_IDENTITY_AND_COMPLETE_PRICE_PAIR_RESOLUTION_ONLY","fallbackBehavior":"ABSENT"}
```

Its lowercase SHA-256 is exactly
`e5e61c4adcd6567bfc76f73114499578f09de2254dc39a2553f3c0e2eaf03486`.
Returned bytes are defensive, and every calculation context echoes the digest.
A changed formula, denominator, unit, rounding, scale, boundary, unavailable
rule, or byte requires a new version and digest.

## Purity and integration boundary

Production adds exactly `AssetReturnPolicyVersion.java`,
`AssetReturnInput.java`, `AssetReturnResult.java`, and
`AssetReturnCalculator.java`. The calculator consumes supplied domain evidence
only. It does not invoke directional-win, target-hit, target-error, select an
observation, activate a canonical methodology, persist an outcome, or publish
a metric. No schema, fixture, manifest member, JSON mapping, OpenAPI path,
Flyway migration, provider, repository, controller, scheduler, API behavior, or
web source is added.

No API key, account, paid plan, domain, provider license, named secret, or
network access is needed for this pure leaf. Its eventual P5 evidence boundary
is the licensed provider and rights set described by ADR-016; this decision does
not invent a vendor or environment-variable name.

## Consequences

- Positive, zero, and negative price returns share one replayable signed-ratio
  meaning. Directional-win may consume the result only through a later reviewed
  orchestrator that preserves unavailability and neutral-direction semantics.
- Public result constructors validate only local shape, version/hash, pair
  state, nested reason, scale, precision, and lower bound. Only
  `AssetReturnCalculator` attests the formula and one permitted rounding step.
- Methodology activation, fingerprints, append-only persistence, MFE/MAE,
  alpha, aggregation, ranking, and product publication remain later work.
