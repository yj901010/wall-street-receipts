# ADR-083 — DEMO Comparative Scoring Input and Replay

- Status: Accepted
- Date: 2026-09-11

## Context

ADR-080 composes three metric meanings, ADR-081 stores that exact profile, and
ADR-082 exposes an explicit local command and read-only audit view. The standalone
ADR-027/029 assignment, ADR-030 reference-pair, ADR-031/032 return and ADR-033
readiness contracts had not yet been invoked together from complete application
input. Public canonical outcomes are still incomplete, not five observed metrics.

## Decision

Add a separate pure DEMO profile, `wsr-demo-comparative-preview/1.0.0`, without
modifying the old profile, persisted bytes, API, CLI, database or Web route.

Four production types live in `application/scoring`:

- `ComparativeScoringInput`: the full existing DEMO endpoint input and separate
  typed benchmark/sector bundles. Each contains its original assignment request,
  reference-index bindings, exact basis/endpoint levels and divisor-continuity
  candidates. All thirteen comparative candidate lists are required, immutable,
  non-null-member and bounded at 4096. Empty means missing, never zero.
- `ComparativeScoringMethodology`: exact canonical definition and nine pinned
  dependencies (the entire old profile plus eight comparative policies).
- `ComparativeScoringInputCodec`: closed canonical encoding, strict decoding and
  input hashing. Neither Java serialization nor arbitrary class loading is used.
- `ComparativeScoringEvaluator`: calls the old evaluator once, preserves its
  whole three-meaning receipt and reuses the exact endpoint object retained in
  the target-error calculation context. Each comparative leg invokes its own
  assignment selector, pair selector, return calculator and readiness resolver.

Only the evaluator can construct an application receipt. Input cannot supply
an allegedly resolved assignment, selected pair, return or readiness. The
profile enforces exact whole forecast basis (including correction identity),
asset and `evaluationAsOf` across requests. Existing selectors own candidate
membership, PIT filtering, temporal/economic correlation and reason precedence.
Benchmark and sector classifications are not inferred from one another. This
does not authenticate provider assertions or verify a source correction ledger.

## Independence and incomplete evidence

A missing strict schedule preserves the old incomplete receipt and executes no
comparative leaves. Otherwise, a missing asset price or target does not itself
block valid comparative index levels: the shared endpoint provides the exact UTC
anchor, not an invented asset price. Unknown catalog/binding evidence still blocks
unsafe anchors. The exact nested `ENDPOINT_NOT_REACHED_AS_OF` reason is the only
comparative waiting case; assignment failure does not become waiting.

Each leg retains complete typed source receipts and its own readiness. Missing,
future, conflicting and ambiguous evidence remains explicit. A known non-equity
classification is intentionally N/A only where the original assignment selector
permits it; a contradictory supplied equity assignment is unavailable, not N/A.
One leg's failure never supplies values to the other. Constructor-level invalid
topology is rejected, not converted into a manufactured numeric result.

There are four readiness owners for five metric meanings: shared asset return/
directional win, target error, benchmark return and sector return. The two new
values are signed price-index returns, not dividend-inclusive total returns or
alpha. `dataComplete` is always false. No canonical CALCULATED status, methodology
registry activation, publication, aggregate, ranking, retry or freshness is implied.

## Reproducibility

The new format is `wsr-comparative-input-v1`, bounded to 1 MiB in total. It embeds
the exact canonical ADR-080 byte stream and encodes comparative input using
explicit tags, four-byte lengths, closed record/enum identities, declared field
order, ordered lists, strict UTF-8, ISO instants, currency codes and normalized
decimal text. All supplied candidates are retained, including future/rejected
ones. A fingerprint change need not mean a calculated value changed. Hashes are
integrity identifiers, not source signatures or proof of market observation.

Decode limits nesting to 24, lists to 4096, and text to 65536 UTF-16 units. Invalid
UTF-8, unknown records/enums/fields, mismatched generic evidence types, excessive
lengths, extra/truncated bytes and noncanonical re-encoding fail closed. The new
codec cannot decode the old stream as this profile, nor can the old codec decode
the new stream. Required identity checks precede evaluation.

- New definition SHA-256:
  `6fb2d737d177662ec072277f1e345ca10a2c9447d35353bc46c1f886669ad6d2`.
- Synthetic golden input SHA-256:
  `507028d10501990999b8af3dd56835499d283544d8e8b6b739252a746edff4a0`.
- Preserved old definition SHA-256:
  `91abc0fcbc986b47e505bbddba346977911c9977664621e4c88cb8a2cbf8ea27`.
- Preserved old input golden:
  `c538a04a0eb74074c0d3e50af94aa4ca965b025ab20ec1df182fdc22e4df760b`.

## Verification and custody

Three new JUnit test classes plus a synthetic, test-only fixture cover invocation
from original inputs, exact endpoint-object reuse, old result parity, independent
missing/PIT/duplicate evidence, precise index times/identities, intentional N/A,
unsafe anchors, correction lineage, codec limits, canonical replay and identity
rejection. Existing standalone calculation goldens remain byte-identical.

Scoring custody adds exactly eight Java paths (four production, four test) to its
previous forty. Every new byte and Git mode/object is pinned; no neighboring path,
old policy or prior migration receives a general exception. The existing CI job
runs the current JUnit and custody tests. No historical workflow body changes.
Final executed counts and build results are recorded in IMPLEMENTATION_LOG.md.

## Next work and external boundary

The next application slice is an explicitly versioned persistence/ledger replay
path for this new input profile, followed by its read-only API/audit representation.
Do not insert these bytes into ADR-081's endpoint-only receipt table or invoke its
endpoint-only command. Existing receipts and their two read routes are unchanged.

Target-hit integration remains a separate supplied-aggregate question. ADR-034's
raw tick coverage and rights prerequisites for MFE/MAE are not waived; no synthetic
aggregate, current snapshot or reference-index level proves raw-window coverage.
Alpha/sector-alpha arithmetic and canonical lifecycle/aggregation remain later.
Actual index feeds require exact product, historical levels/revisions, calendars,
divisor continuity, mapping and written storage/display/derived/redistribution
rights review. No credentials, external provider, real DB or Ubuntu host is used
or enabled by this delivery.
