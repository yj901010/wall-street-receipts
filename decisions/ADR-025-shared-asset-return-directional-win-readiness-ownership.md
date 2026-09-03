# ADR-025 — Shared Asset-Return and Directional-Win Readiness Ownership

- Status: Accepted
- Date: 2026-08-24

## Context

ADR-017 calculates one signed asset-return result from a complete point-in-time
price pair. ADR-021 then preserves that exact result while correlating it with
the canonical forecast terms and calculator-side routing needed to resolve the
directional-win leaf. ADR-022 classifies the resulting complete supplied source
as `Settled`, `AwaitingEndpoint`, or `EvidenceUnavailable` by inspecting only
the already-preserved asset-return dependency.

The canonical outcome nevertheless exposes `assetReturn` and `directionalWin`
as two distinct metric meanings. Creating a second asset-return readiness
receipt would allow those meanings to be classified from different source
objects, evaluated twice, or paired across different basis, asset, or
point-in-time evidence. Treating the existing receipt as only one metric would
instead omit the other dependency from later completeness decisions. Ownership
must therefore be explicit before the aggregate readiness and lifecycle policy
is designed.

## Decision

ADR-022 remains the sole shared receipt for asset-return and directional-win readiness.

This is an ownership decision over future canonical outcome composition, not a
new executable readiness policy.

- The exact `DirectionalWinReadinessResolution` supplied by ADR-022 owns both
  canonical metric meanings. A later aggregate consumes that receipt exactly
  once and preserves the exact whole object.
- No standalone `assetreturnreadiness` package, policy, request, resolution,
  resolver, wrapper, alias, delegated facade, or golden test is introduced.
  The existing `assetreturn` calculation package and `AssetReturnResult` remain
  unchanged; this prohibition applies only to a competing readiness receipt.
- ADR-022 keeps its existing public names, its exact 2353-byte ASCII/UTF-8
  canonical policy definition, and SHA-256
  `1eca77c5b4d43de7657281c161a8c50356cd90e1a18c6e9fd7f5b2c0142b7ec7`.
  No alias, rename, new version, new digest, or amended canonical definition is
  created by this decision.
- The shared ownership is limited to the canonical outcome-scoring path whose
  ADR-021 source already proves the required direction, basis, asset, and
  evaluation-as-of correlations. It does not classify an arbitrary detached
  `AssetReturnResult` from another use case.

## Exact shared projection

A future aggregate may project metric meaning only from the preserved ADR-021
source inside the supplied ADR-022 receipt:

| ADR-022 receipt | Preserved ADR-021 source | Asset-return meaning | Directional-win meaning |
| --- | --- | --- | --- |
| `Settled` | `Available` | Preserve the exact available decimal. | Preserve the exact available Boolean. |
| `Settled` | `NotApplicable` with an available asset return | Preserve the exact available decimal. | Preserve typed intentional non-applicability; never manufacture `false`. |
| `AwaitingEndpoint` | directional `AssetReturnUnavailable` | The asset return remains unavailable on ADR-022's one exact endpoint-only temporal chain. | The directional Boolean remains unresolved because its return dependency is unavailable. |
| `AwaitingEndpoint` | neutral `NotApplicable` with an unavailable asset return | The asset return remains unavailable on the same exact temporal chain. | Directional win is already intentionally not applicable, while the shared receipt remains unsettled because asset return is still required. |
| `EvidenceUnavailable` | directional `AssetReturnUnavailable` | The asset return remains evidence-unavailable. | The directional Boolean remains unresolved because its return dependency is unavailable. |
| `EvidenceUnavailable` | neutral `NotApplicable` with an unavailable asset return | The asset return remains evidence-unavailable. | Directional win is already intentionally not applicable, while the shared receipt remains unsettled because asset return is still required. |

The ADR-021 and ADR-022 constructors make every other receipt/source pairing
invalid. An aggregate may inspect the preserved ADR-021 top-level variant to
extract a settled value or intentional non-applicability, but it must not
repeat nested reason inspection, recalculate either metric, replace the source,
flatten a reason, or infer a Boolean from a nullable field.

## Future aggregate consumption

- The canonical outcome still has ten metric meanings: asset return, benchmark
  return, sector return, alpha, sector alpha, MFE, MAE, target hit, directional
  win, and target error.
- A complete future aggregate will have nine readiness ownership inputs: one
  shared ADR-022 receipt plus one ownership input for each of the other eight
  metric meanings. Today only shared ADR-022, target-error ADR-023, and target-
  hit ADR-024 readiness contracts exist; the remaining inputs are deferred.
  Further sharing requires another explicit architecture decision.
- The future aggregate request must carry one shared asset-return/directional-
  win receipt, not two independently supplied fields referencing separate or
  repeated resolutions. It must neither count the receipt as only one metric
  nor count the same evidence twice.
- A shared `Settled` receipt is necessary only for resolving these two metric
  meanings. It is never sufficient for aggregate completeness. In the neutral
  source branch, intentional directional non-applicability is a resolved
  meaning while the available asset return remains independently required.
- `AwaitingEndpoint` and `EvidenceUnavailable` retain their exact ADR-022
  meanings. The aggregate consumes the outer typed classification and preserved
  source; it does not call the ADR-022 resolver or any upstream producer again.

## Lifecycle firewall

No ADR-022 variant maps directly to `OutcomeEvaluationStatus`.

- `Settled` does not imply `CALCULATED` or `dataComplete=true`.
- `AwaitingEndpoint` does not by itself imply `PENDING`, retryability,
  freshness, or a schedule. Another required metric may already be evidence-
  unavailable, or cancellation/latest-correction policy may make the basis
  ineligible.
- `EvidenceUnavailable` does not by itself imply `INCOMPLETE`, permanence, or
  absence of a future correction.
- A later versioned lifecycle policy must compose all ten metric meanings,
  intentional non-applicability, cancellation and latest-correction
  eligibility, methodology identity, input fingerprinting, and any reviewed
  retry/freshness policy before constructing or persisting a `CallOutcome`.
  Existing `EXCLUDED/CALL_CANCELLED` invariants continue to require all metric
  fields to be null.

This decision constructs or mutates no outcome, activates no methodology, and
adds no fingerprinting, persistence, aggregation, ranking, scheduling, API, or
UI behavior.

## Production and verification boundary

ADR-025 adds no Java production file, test, policy enum, canonical policy byte
sequence, policy digest, schema, fixture, manifest, OpenAPI, Flyway migration,
database behavior, controller, provider adapter, or web source. Existing
ADR-022 golden cardinality and API behavior remain unchanged. Repository checks
must reject a standalone asset-return readiness surface and lock the unchanged
ADR-022 identity plus this ownership decision.

## External-data and deferred boundary

This decision requires no API key, account, paid plan, provider license,
secret, or network access. It makes no provider or market-data observation
claim. Non-DEMO integration remains subject to provider selection and storage,
display, derived-data, and redistribution rights; scoped secrets may be added
only through approved local or CI secret stores, never chat or Git.

Benchmark and sector return work remains blocked on explicit product approval
of benchmark identity, sector taxonomy and point-in-time membership, price-
versus-total-return basis, currency, venue, and corporate-action treatment.
Raw-window coverage semantics, MFE/MAE, alpha/sector alpha, aggregate lifecycle,
append-only persistence, and publication remain later reviewed work.
