# ADR-034 — Point-in-Time Raw-Window Coverage Foundation

- Status: Accepted
- Date: 2026-08-25

## Context

ADR-016 produces a complete point-in-time asset price pair with an exact basis
price, a mature strict-horizon endpoint, and split/reverse-split continuity.
ADR-019 may then consume a caller-supplied full-window high/low pair for the
target-hit path. Its `EXACT_CAUSAL_WINDOW_SESSION_UNION` value is deliberately
an upstream aggregate attestation: ADR-019 does not read raw trades or bars and
does not prove no-trade periods, halts, auctions, bar boundaries, correction
chains, or source-sequence completeness.

MFE and MAE require one complete causal-window price population. Reclassifying
the ADR-019 aggregate flag as “raw coverage” would add no evidence, couple both
metrics to a target-specific side selection, and risk presenting a supplied
claim as independently verified market data. The provider-neutral raw meaning
must be fixed before an executable resolver, fixture, adapter, or calculation
is allowed to assert completeness.

## Decision

ADR-034 freezes provider-neutral point-in-time raw-window coverage semantics before any executable raw aggregation or MFE/MAE calculation.

This is a decision-only foundation. It introduces no executable policy,
canonical definition or digest, Java package, source-local golden, market-data
fixture, persistence, API, or product behavior.

### Exact economic window

The future V1 raw population is `TRADE_TICK_ONLY_V1` and consists only of
eligible primary-venue regular-session trade ticks belonging to the exact
ordered ADR-010 strict-horizon sessions.

- Its UTC interval is exactly
  `(basis.eventTime, endpointSession.closesAt]`: the basis-event lower bound is
  exclusive and the endpoint-session close upper bound is inclusive.
- A first-session tick at or before the basis event is excluded. Off-hours,
  alternate venues, inter-session gaps, quotes, indications, and non-trade
  records are outside the population.
- An eligible opening or closing auction execution may participate only when a
  separately versioned provider-code mapping explicitly classifies that trade
  condition as primary-venue regular-session eligible. Text labels and inferred
  exchange conventions establish nothing.
- OHLC, session, and intraday bars are not V1 raw evidence. No bar may be split,
  clipped, or assumed safe when it straddles the exclusive lower bound; V1 has
  no bar-straddle inference.
- Basis price, endpoint close, prior close, nearest observation, interpolation,
  another venue, a bar-derived estimate, and ADR-019’s supplied aggregate pair
  are never fallback raw events.

### Exact future anchor

A later executable request must consume one complete ADR-016
`AssetReturnPricePairResolution.Resolved`, not ADR-018 target eligibility or an
ADR-019 selected favorable extreme. The whole pair receipt supplies the exact
outcome basis, basis price, strict horizon and endpoint, `evaluationAsOf`,
asset, primary venue, currency, source revision, calendar/catalog revision,
adjustment basis, and corporate-action continuity required by both MFE and MAE.

The raw evidence must match those identities exactly. Ticker, current asset
master data, a DEMO market snapshot, a UI universe, current calendar rows, or
call ID alone cannot repair or replace a mismatch.

### Required future raw evidence

A separately reviewed executable contract is expected under the reserved
package name
`com.wallstreetreceipts.api.domain.outcome.rawwindowcoverage`. The names
`RawWindowCoveragePolicyVersion`, `RawWindowPriceEvent`,
`RawWindowCoverageManifest`, `RawWindowCoverageRequest`,
`RawWindowCoverageResolution`, and `RawWindowCoverageResolver` are reserved for
that later slice; ADR-034 creates none of them and does not freeze their Java
record components.

Before a future result may claim coverage, its supplied evidence must preserve:

- every trade’s observation and provider-event identity, revision identity,
  event time, positive exact `NUMERIC(38,12)` price, trade-condition identity,
  price-source ID and revision, provenance, `availableAt`, and `capturedAt`;
- one manifest’s exact economic bounds, ordered session identities,
  calendar/catalog identity and revision, source sequence or watermark proof,
  correction/bust coverage, source and manifest revisions, provenance, and
  point-in-time timestamps;
- exact asset, primary venue, currency, price basis, and corporate-action
  continuity shared with the complete ADR-016 anchor; and
- one final effective event population from which both high and low are
  derived without rounding or rescaling, preserving provenance for every tied
  source event rather than silently choosing across different populations.

A vendor assertion, row count, last timestamp, current snapshot, contiguous
local database IDs, or absence of a returned row is not a completeness proof.
The selected feed’s documented sequence/watermark and correction protocol must
make omitted eligible events detectable before the future policy is accepted.

### Point-in-time and correction semantics

Every raw event, manifest, sequence proof, trade-condition mapping,
correction, and bust is visible only when both
`availableAt <= evaluationAsOf` and `capturedAt <= evaluationAsOf`. Future
evidence is invisible before identity, reason, cardinality, population, high,
or low decisions.

The causal time order is also mandatory. Every raw event must satisfy
`eventTime <= availableAt <= capturedAt <= evaluationAsOf`; every manifest
must satisfy
`upperBound <= availableAt <= capturedAt <= evaluationAsOf`. Evidence cannot
be available before its trade occurs or before its attested window closes, and
it cannot be captured before it becomes available. An impossible ordering
fails closed before completeness reasoning.

- For each provider event, only a complete predecessor-linked revision chain
  visible at the cutoff may determine the final effective event.
- A visible correction replaces its linked prior version; a visible bust
  removes the linked event. Duplicate delivery of the same provider-event
  revision does not create another economic trade, while distinct provider
  event identities remain distinct.
- Missing predecessors, broken links, competing terminal revisions, ambiguous
  identity, an unresolved correction, or an unproven correction watermark
  fails closed as evidence unavailability.
- A correction first visible after the cutoff may change a later replay but
  never rewrites the earlier as-of receipt.

### Silence, halt, auction, and gap semantics

- A halt contributes no price. Point-in-time halt evidence may explain a
  silent interval but cannot manufacture a tick, an extreme, or completeness
  outside the source proof.
- A completely proven economic window with zero eligible trade ticks is a
  distinct `CompleteWithoutEligibleTrade` meaning. It never becomes a zero
  price, zero MFE, zero MAE, basis-price fallback, or endpoint-price fallback.
- In-session silence is covered only when the manifest and source
  sequence/watermark prove that no eligible event was omitted. Otherwise the
  window is evidence-unavailable even when a halt or no-trade explanation looks
  plausible.
- Off-hours and inter-session gaps are outside the population. A source
  sequence gap inside the claimed population is evidence-unavailable unless
  the selected product’s approved completeness protocol accounts for it.
- Unknown or unmapped trade conditions fail closed. Auction inclusion is never
  inferred from time, venue, or a human-readable label.

### Future result meanings and ownership

The later executable policy must distinguish at least `Covered`,
`CompleteWithoutEligibleTrade`, and `EvidenceUnavailable`. Their exact Java
shape, reason taxonomy, precedence, canonical policy bytes, and golden vectors
remain a separate review. There is no `AwaitingEndpoint` in this foundation
because the required ADR-016 anchor already has a mature endpoint, and there is
no `NotApplicable` because raw coverage is an evidence state rather than call
direction or metric applicability.

One covered receipt must co-identify the source population used for both high
and low. Separate high-only and low-only coverage receipts may not be paired,
because they could carry different source revisions, correction cutoffs, or
economic windows.

That receipt is shared source evidence only. MFE and MAE remain two canonical
metric meanings under ADR-025. Their formulas, bullish/bearish polarity,
calculator types, output bounds, readiness receipts, and ownership inputs must
be reviewed separately; ADR-034 neither combines their readiness nor assigns a
numeric value.

No future coverage meaning maps directly to `OutcomeEvaluationStatus`,
`dataComplete`, retryability, freshness, permanence, cancellation, scheduling,
methodology activation, fingerprinting, persistence, aggregation, ranking, or
publication.

### ADR-019 compatibility firewall

ADR-019 remains the target-hit-specific path over a supplied high/low aggregate
and side-selected favorable extreme. ADR-034 does not change its six production
types, 22 reasons, policy definition, digest, or golden cardinality.

An ADR-019 observation marked `EXACT_CAUSAL_WINDOW_SESSION_UNION` cannot be
promoted, cast, wrapped, or backfilled into future ADR-034 raw coverage. A later
raw resolver may independently produce evidence suitable for a future
aggregate-high/low adapter only after its own reviewed contract; it must not
retroactively claim that ADR-019 verified raw observations.

## Production and verification boundary

ADR-034 adds only this decision, README and acceptance/log documentation, and a
dedicated repository guard. It adds zero Java production files, Java tests,
JSON schemas, canonical fixtures, manifest members, OpenAPI paths, Flyway
migrations, database rows, repositories, controllers, provider adapters,
resources, web sources, runtime gates, or product routes.

Existing DEMO MFE and MAE values remain null. No source-local synthetic vector
is market evidence, and no existing fixture or `MarketSnapshot` can be used as
raw coverage.

The repository guard locks the exact decision-only surface, marker parity,
economic interval, trade-tick-only rule, PIT and correction requirements,
silence/halt/auction/gap handling, ADR-016/ADR-019 boundaries, ownership
firewalls, and unchanged protected production/test-web baselines. A later
executable contract must receive a new ADR, canonical policy identity, golden
matrix, and independent review rather than weakening this guard.

## Verification

- The dedicated ADR-034 guard passes. The exact marker occurs once in this ADR,
  README, P3 acceptance, and the implementation log. Protected production
  remains exactly 232 files and API-test/web remains exactly 205 files, with
  both digests matching the locked ADR-033 baselines.
- All 39/39 workflow Python bodies syntax-compile and all 32/32 locally
  runnable bodies pass. SnakeYAML parses exactly four jobs and Compose
  configuration validates. Existing legacy guard lines remain unchanged.
- Full API verification passes 2066/2066 with no failures, errors, or skips,
  including PostgreSQL 17.10 Testcontainers and Flyway. Web lint, 569/569
  Vitest tests, and the 12-page production build pass.
- README marker, `NotApplicable` exclusion, raw-event causal-chain, temporary
  forbidden `rawwindowcoverage/RawWindowCoveragePolicyVersion.java`, and
  `.env.example` provider-surface mutations each make the guard exit nonzero.
  All are removed; the final guard and `git diff --check` pass. The user-owned
  `apps/web/next-env.d.ts` is restored to its exact pre-build content.
- Independent closure review reports no remaining P0-P3 finding after the
  `NotApplicable`, causal-time, recursive-fixture, configuration-digest, and
  whole-workflow firewall gaps were corrected.

## External-data and rights boundary

No API key, account, paid plan, provider license, named secret, or network
access is needed for ADR-034.

Before executable non-DEMO raw evidence may enter the repository, P5 and the
user must approve the exact historical primary-venue trade-tick product/feed
and obtain written rights covering the required history, provider-event
identities and revisions, correction/bust stream, source sequence/watermark
semantics, trade-condition and auction classification, halt/reference data,
exchange calendars, corporate actions, storage, cache, derived calculations,
display, and redistribution. Publisher and redistributor grants require
separate review when they differ.

Only after the exact product and written rights are approved may a scoped
credential be requested. It must be supplied through an untracked local secret
file or approved CI/deployment secret store under a reviewed name, never pasted
into chat or committed to Git.

## Consequences and next work

- The project now has a provider-neutral truth boundary that prevents a
  supplied aggregate flag, sparse snapshot, or missing row from masquerading
  as complete raw evidence.
- The next executable raw-window slice remains blocked on the exact feed’s
  sequence, correction, trade-condition, auction, halt, and rights evidence.
- After that reviewed resolver exists, add separate deterministic MFE and MAE
  arithmetic/polarity contracts, followed by their independent readiness
  ownership decisions. Alpha and sector alpha remain last.
- Canonical methodology activation, input fingerprinting, append-only lineage,
  aggregate lifecycle, retry/freshness, persistence, scheduling, ranking, and
  API/UI publication remain later reviewed work.
