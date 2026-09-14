# ADR-086 — DEMO target-hit scoring input and replay

- Status: Accepted; user approved public push after successful verification.
- Date: 2026-09-14
- Predecessor: ADR-085, PR #33 merged at
  `20477b72a822926fbcb009f770cca1bb5c44e7af`. PR CI #78 succeeded;
  merge CI #79 was running at the initial check and subsequently succeeded.

## Decision

Add a separate pure six-meaning DEMO profile,
`wsr-demo-target-hit-preview/1.0.0`. Preserve the complete ADR-083 comparative
profile and append target-hit application composition. The old calculators,
policy definitions, input streams, persisted receipts, read APIs, commands and
Web screens remain byte-identical. This is neither a canonical outcome nor
completion of P3.

Four new production types live in `application/scoring`:

- `TargetHitScoringInput`: complete comparative input, an optional explicit
  WindowPriceBinding and an ordered list of original FullWindowHighLowObservation
  candidates. Lists are immutable, non-null-member and bounded to 4096; nested
  session-ID lists are also bounded. Erased candidate types are rejected.
  Null binding/empty candidates mean missing. Do not prefilter or deduplicate.
- `TargetHitScoringMethodology`: canonical definition with five pinned
  dependencies: the entire comparative profile and the unchanged eligibility,
  favorable-extreme, target-hit orchestration and readiness policies.
- `TargetHitScoringInputCodec`: a closed, bounded canonical byte stream,
  independent of both older formats, retaining all supplied evidence.
- `TargetHitScoringEvaluator`: evaluates ADR-083 once and invokes the existing
  target eligibility, conditional window selector, orchestration and readiness
  in that order. Only this evaluator can construct its application receipt.

## Correlation, independence and evidence truth

Reuse the exact horizon object retained by the comparative endpoint receipt.
The same endpoint input supplies whole original/correction basis, forecast terms,
target evidence, catalog and evaluation-as-of. Derive direction routing from those
same terms using the already pinned polarity policy. Inputs cannot supply an
allegedly resolved eligibility, selected extreme, hit or readiness result.

Run eligibility even when the schedule is incomplete: preserve its established
precedence, including intentional N/A for a neutral or explicitly absent target.
Only ReadyForWindowEvidence invokes FavorableExtremeSelector. Pending, N/A and
eligibility-unavailable pass a null extreme to orchestration, which preserves
the full typed source. Readiness remains its own metric meaning, not canonical
lifecycle, retry, freshness or publication.

Target hit needs the supplied exact causal-window aggregate, not an asset endpoint
price, basis return or comparative index level. Missing prices/reference evidence
do not manufacture a target-hit failure; target-hit evidence failure does not
replace any of the old five meanings. Equality remains a hit: bullish selects
window high >= target, bearish selects window low <= target. No new arithmetic,
rounding, FX normalization, high/low reselection or endpoint-price fallback exists.

The original selector owns PIT filtering, source/calendar/asset/venue/currency
correlation, whole correction identity, exact ordered session union, open lower
and closed upper bounds, split continuity, invalid-candidate precedence and
ambiguity. Known invalid candidates poison selection; duplicates are ambiguous,
not deduplicated. Future candidates are absent from selected output but retained
in original input and fingerprint. Missing evidence never becomes false or zero.

A supplied DEMO aggregate is a caller's attestation. This profile does not verify
raw trades, sequence/finality, feed corrections, no-trade intervals, halt/auction
coverage or market-data licensing. It does not authenticate a provider, source
ledger membership or correction lineage. ADR-034 prerequisites for raw coverage
and MFE/MAE remain unchanged. No synthetic index or aggregate substitutes for them.

## Reproducibility and bounds

Format `wsr-target-hit-input-v1` embeds the exact ADR-083 canonical stream.
It uses tagged values, four-byte lengths, a closed record/enum allowlist, declared
field order, ordered lists, strict UTF-8, normalized decimal strings, ISO instants
and currency codes. No arbitrary class loading or Java deserialization.

The total is bounded to 1 MiB, each list to 4096, each string to 65536 UTF-16
units and decode nesting to 24. Reject unknown types/fields/enums/tags, malformed
UTF-8, excessive/truncated lengths, extra bytes and noncanonical re-encoding.
Hash identifiers attest integrity and reproducibility, not source truth.

- Definition SHA-256:
  `aaa8684e12a5d83df36eff5a15107828931526b36ccfcca71ac8f968804b1b1e`.
- Synthetic golden input SHA-256:
  `17cb039635703b3d29fde5bd0b32c7b30916d2e95f2b52ebe6fde6774142461c`.
- Existing comparative definition and golden remain
  `6fb2d737d177662ec072277f1e345ca10a2c9447d35353bc46c1f886669ad6d2` and
  `507028d10501990999b8af3dd56835499d283544d8e8b6b739252a746edff4a0`.

## Verification and custody

Three new JUnit classes and one synthetic test-only fixture cover exact
bullish/bearish/strong equality and fractional boundaries, independent evidence,
microsecond maturity, neutral/absent/missing targets, unsupported target dates,
missing schedules, explicit multi-session windows, PIT, mismatches, ambiguity,
correction identity, immutable inputs, closed format limits, canonical replay,
locale/zone determinism and unchanged comparative results.

CI custody adds exactly eight Java paths to the previous 79. Every previous
scoring path retains its exact merged bytes; no broad product allowance or
historical workflow rewrite. A dedicated regression verifies the eight additions
and all 79 previous files against the merged predecessor. Full current API,
including PostgreSQL integration and packaging, and CI Python/contract checks
are required. No Web source, route, responsive behavior or dependency changes;
the previous browser acceptance is not claimed as a newly executed check.

## Next work

Add a separately versioned append-only persistence/ledger replay and read API
for this profile, then a bounded explicit DEMO command and read-only audit view.
Do not write these bytes to either older receipt table or feed them to an older
command. Canonical outcome publication, MFE/MAE, alpha/sector alpha, ranking and
actual Ubuntu deployment remain unfinished. No live provider or real user
database is enabled or migrated by this phase.
