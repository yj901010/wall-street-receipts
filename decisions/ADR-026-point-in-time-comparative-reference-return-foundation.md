# ADR-026 — Point-in-Time Comparative Reference-Return Foundation

- Status: Accepted
- Date: 2026-08-24

## Context

ADR-017 gives the asset leg of a future comparative return one exact signed
price-return meaning. The canonical outcome also reserves benchmark return,
sector return, alpha, and sector alpha fields, but the repository has no
point-in-time benchmark assignment, canonical sector taxonomy, sector
membership, reference-level evidence, or index-continuity contract.

The existing `Asset` master record does not prove a primary venue, venue
country, source revision, or point-in-time membership. The bare nullable `spx`
and `ndx` values in `MarketSnapshot` do not identify a reference observation,
calendar, source revision, return basis, or divisor continuity. P2 map and
treemap labels are explicitly synthetic DEMO presentation data. None of those
surfaces can be promoted into comparative-return evidence.

Product approval has now been received for the narrow V1 foundation below.
Exact executable assignment, reference-evidence, calculation, and readiness
policies remain separate reviewed slices.

This received approval satisfies only ADR-025's deferred comparative-return
product-approval blocker. It does not relax ADR-025's ownership, lifecycle,
evidence, provider, or publication boundaries.

## Decision

ADR-026 locks benchmark and sector returns to explicit point-in-time reference assignments.

This is a decision-only foundation. It introduces no executable policy or
claim that benchmark or sector evidence currently exists.

### Independent assignment ownership

- Benchmark assignment and sector assignment are distinct versioned contracts
  with independently typed evidence and results. They are not collapsed into
  one generic assignment, one nullable reference ID, or one shared readiness
  receipt.
- Every assignment is bound to the exact original or correction
  `OutcomeBasis`. Membership is selected at `basis.eventTime` and remains
  frozen through that named-horizon evaluation. A correction is a separate
  basis and is evaluated independently; there is no latest-revision inference.
- Assignment and classification records must preserve provider event/evidence
  identity, source ID and revision, provenance, asset ID and class, primary
  venue and explicitly sourced venue country, currency, an explicit effective
  interval, `availableAt`, and `capturedAt`.
- Effective membership uses an explicit start-inclusive/end-exclusive interval
  containing `basis.eventTime`. Open-ended membership must be represented
  explicitly by the later contract, not by substituting the evaluation time or
  an invented terminal date.
- A supplied record is point-in-time visible only when both `availableAt` and
  `capturedAt` are not after the exact `evaluationAsOf`. Future records are
  indistinguishable from absent evidence and cannot affect identity checks,
  reason selection, ambiguity, or output.
- An executable selector must require exactly one valid visible assignment
  after fixed fail-closed identity and effective-interval gates. Equal
  duplicates remain ambiguous. There is no deduplication, current-row lookup,
  ticker matching, nearest interval, provider preference, or fallback.

### Benchmark V1 scope

- The V1 benchmark scope is an asset explicitly evidenced as
  `AssetType.EQUITY`, with primary-venue country exactly ISO 3166-1 alpha-2
  `US` and currency exactly ISO 4217 `USD`. Eligibility never follows from
  ticker spelling, issuer name, exchange-like text, current master data, or a
  UI universe.
- An in-scope asset is assigned only by one explicit visible mapping to the
  canonical internal benchmark identity `asset-spx` with
  `AssetType.INDEX`. The mapping must also attest that the reference is a USD
  provider-published price index. The existing DEMO master identity does not
  itself prove membership or supply a level observation.
- A missing mapping for an otherwise in-scope asset is typed evidence
  unavailability. It is never an automatic `asset-spx` assignment, zero
  return, or non-applicability.
- A non-equity asset, including an index, or an equity outside the explicit
  United States/USD V1 scope is typed intentional non-applicability when its
  classification evidence is visible and coherent. Missing or conflicting
  classification remains evidence-unavailable. A visible out-of-scope mapping
  is a fail-closed conflict rather than evidence that expands V1.
- `SPX`, `S&P 500`, `MarketSnapshot.spx`, P2 `sp500` universe membership, and
  map/treemap inclusion are never assignment evidence. No inference path may
  synthesize the required mapping.

### Provider-neutral sector foundation

- Wall Street Receipts owns a provider-neutral, versioned canonical sector
  taxonomy. Before sector assignment code exists, a later decision must lock
  its taxonomy ID, version, canonical definition and hash, closed canonical
  node IDs, and exact provider-to-canonical mapping evidence.
- Provider labels and historical provider classifications remain preserved as
  source evidence; they become a canonical assignment only through an explicit
  versioned mapping. The product makes no unsupported claim that the taxonomy
  or a provider label is GICS, ICB, or another licensed taxonomy.
- P2 synthetic sector and industry labels are not taxonomy or membership
  evidence and cannot seed the canonical mapping.
- A sector return uses the price-index reference explicitly assigned to the
  selected canonical sector. It does not use an ETF, a current constituent
  basket, a market-cap proxy, a treemap aggregate, or a provider return field.

### Reference-level and return semantics

- Benchmark and sector reference evidence preserve the exact source-recorded
  reference level at the asset basis-event instant and the exact reference
  level at the asset endpoint instant. The comparison interval is the same
  `[basis.eventTime, asset endpoint UTC]` interval for both asset and
  reference. Prior close, nearest timestamp, interpolation, or shifted-session
  substitution is absent.
- V1 computes price return, not total return. The asset and reference currency
  must match exactly; V1 performs no FX conversion.
- A reference may use its own reference-specific calculation venue, exchange
  calendar, and source, but those identities and revisions must be explicit
  and point-in-time preserved. They do not alter the shared UTC interval.
- A provider-published price index requires explicit divisor-continuity
  attestation over the interval. Index continuity is not a split/reverse-split
  share-basis claim, so `AssetReturnPricePairResolution`,
  `CorporateActionContinuity`, and `EndpointPriceAdjustmentBasis` must not be
  reused, relabelled, or cast for reference evidence.
- Future `BenchmarkReturnCalculator` and `SectorReturnCalculator` contracts use
  separate semantic types for their own inputs and results. Each must use
  ADR-017's exact arithmetic: `(endpoint - basis) / basis`, exactly one
  subtraction, exactly one scale-12 `HALF_EVEN` division, decimal-ratio units,
  no intermediate or second rounding, and fail-closed representability.
  Neither may cast to `AssetReturnResult` or trust a provider-supplied return
  number.
- Alpha and sector alpha remain deferred until the corresponding asset,
  benchmark, and sector returns have independent evidence, calculation, and
  readiness contracts.

## Typed applicability and lifecycle firewall

Later assignment and return policies must distinguish available evidence,
intentional non-applicability, and evidence unavailability with sealed typed
variants. Null alone is never proof of non-applicability, and missing evidence
never becomes zero, `false`, an inferred loss, or a calculated return.

No future assignment or return-readiness variant maps directly to
`OutcomeEvaluationStatus`, `dataComplete`, retryability, permanence,
cancellation, latest-correction selection, freshness, or scheduling. A later
canonical lifecycle policy must compose all ten canonical metric meanings and
their intentional non-applicability before constructing a complete outcome.

## Implementation order

1. Add the benchmark assignment evidence, exact PIT selector, typed
   applicability result, canonical policy definition/hash, and golden matrix.
2. Define and version the WSR canonical sector taxonomy, then add explicit
   provider mapping and basis-frozen sector assignment evidence.
3. Add independently typed benchmark and sector reference-level pairs with
   exact UTC interval, reference calendar/source/venue, price-index basis,
   same-currency, and divisor-continuity proof.
4. Add separate deterministic benchmark-return and sector-return calculators,
   followed by their source-local readiness contracts.
5. Keep current DEMO outcome values null until a dedicated canonical evidence
   fixture, methodology version/hash, fingerprint, lineage, and completeness
   matrix have been reviewed. Add raw-window coverage and MFE/MAE after these
   comparative leaves; add alpha and sector alpha last.

## Production, data, and external boundary

ADR-026 adds no Java production file, test, package, policy enum, canonical
policy definition, digest, schema, fixture, manifest, OpenAPI path, Flyway
migration, database behavior, controller, repository, provider adapter,
resource, API behavior, or web source. Existing model-only benchmark/sector
outcomes remain null in DEMO responses and unchanged.

No API key, account, paid plan, provider license, secret, or network access is
needed for this foundation. Before non-DEMO assignment or reference-level
evidence enters the system, P5 must select entitled asset/venue/classification,
index-level, sector-taxonomy/membership, exchange-calendar, and continuity
sources and establish storage, display, derived-data, and redistribution
rights. Only then may reviewed adapters introduce named scoped secrets through
approved local, CI, and deployment secret stores, never chat or Git.
