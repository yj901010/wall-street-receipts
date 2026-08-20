# ADR-007 — Explicit-Anchor Session-Offset Resolution

- Status: Accepted
- Date: 2026-08-20

## Context

P3 requires trading-calendar-aware horizons, but the repository currently
defines only the closed labels `D1`, `W1`, `M1`, `M3`, `M6`, and `Y1`. It does
not define whether those labels mean fixed session counts, calendar periods,
month ends, or another roll convention. The canonical fixtures contain no
session catalog, and their `PENDING` and `INCOMPLETE` outcome timestamps are
model-only audit examples rather than evidence for a horizon policy.

ADR-006 deliberately accepts a favorable extreme preselected by its caller.
The next smallest deterministic step follows the same boundary: prove the
mechanics of moving a positive count through an explicit ordered session
catalog without selecting a call event, named horizon, venue, calendar, or
market observation.

## Decision

The second P3 slice implements only a pure explicit-anchor session-offset
resolver.

- `SessionOffsetPolicyVersion` contains exactly
  `EXPLICIT_ANCHOR_SESSION_COUNT_V1`. The version fixes the mechanics below; it
  is not a scoring-methodology version and is not attached to either existing
  `MODEL_ONLY` definition hash.
- `TradingSession` contains only `sessionId`, `opensAt`, and `closesAt`.
  `TradingSessionCatalog` contains only `calendarId`, `revision`, and the
  explicitly ordered session list. Identity strings are non-blank. Instants
  are UTC `Instant` values with at most microsecond precision, every open is
  strictly before its close, session IDs are unique, and adjacent entries are
  ordered and non-overlapping. The catalog is defensively copied and is never
  sorted or mutated by the resolver.
- `SessionOffsetRequest` contains only the policy version, caller-supplied
  `anchorSessionId`, positive `sessionCount`, explicit `evaluationAsOf`, and
  catalog. It contains no call, analyst-call revision, `eventTime`,
  `OutcomeHorizon`, venue, timezone, observation, price, provider, provenance,
  capture time, or clock.
- `sessionCount = N` means the next exactly `N` entries after the anchor in the
  supplied catalog. The anchor is excluded, the following `N` sessions are
  included, and the `N`th subsequent session is the endpoint. No weekday,
  weekend, holiday, DST, early-close, halt, or missing-session rule is inferred;
  an explicitly supplied session counts and an omitted session does not exist
  to this primitive.
- `SessionOffsetResolution` nests exact
  `ResolutionContext(policyVersion, calendarId, catalogRevision,
  anchorSessionId, sessionCount, evaluationAsOf)` and
  `ResolvedSessionWindow(context, anchorSession, immutable sessions,
  endpointSession)` records. `Ready(window)` means
  only that `endpoint.closesAt <= evaluationAsOf`. Equality is ready.
  `Pending(window, ENDPOINT_NOT_REACHED)` means the endpoint exists but its
  close is after `evaluationAsOf`.
  `Incomplete(context, ANCHOR_SESSION_MISSING)` means the supplied anchor is
  absent; `Incomplete(context, ENDPOINT_SESSION_MISSING)` means the catalog
  lacks `N` subsequent entries. No result invents a session.
- Structurally invalid requests or catalogs fail closed. Null values, blank
  identities, non-positive counts, duplicate sessions, non-increasing or
  overlapping entries, invalid open/close bounds, and finer-than-microsecond
  instants are rejected. Endpoint lookup is overflow-safe.
- The resolver is a deterministic domain leaf. It has no `Clock`, locale,
  default timezone, provider, network, fixture, JSON, Spring, persistence,
  controller, scheduler, or LLM dependency. Its session schedules and golden
  cases are source-local test inputs, not canonical market or calendar facts.

`Ready` is deliberately not `CALCULATED` and does not mean that a close, bar,
price, favorable extreme, or other market observation exists. Resolver-local
pending and incomplete reasons are not written to `CallOutcome` in this slice.

## Versioning and later integration

The code enum and this ADR are sufficient for an unconnected mechanics leaf.
No canonical policy definition or SHA-256 definition hash is introduced yet.
Before an `OutcomeHorizon` is mapped or an outcome is evaluated, a separately
reviewed canonical methodology definition and serialization must lock:

- named horizon counts or calendar-period rules;
- original-call versus correction anchoring and event-to-session selection;
- calendar ownership, revision evidence, and point-in-time availability;
- endpoint and high/low window inclusivity, price field, and bar completeness;
- retry/grace and terminal incomplete policy;
- corporate-action and currency treatment; and
- the exact fields and byte representation included in the input fingerprint.

Changing those rules starts a new methodology version and definition hash.
Changing the supplied calendar or market evidence under unchanged rules changes
the later input fingerprint and appends outcome lineage; it never rewrites an
older result.

## Consequences

- Ordered-session mechanics, readiness boundaries, and missing coverage can be
  golden-tested without inventing named horizon meanings or market data.
- Explicit Saturday sessions, omitted weekdays, irregular UTC gaps, and early
  closes are handled only as supplied catalog entries, so host calendar and
  timezone behavior cannot affect replay.
- The current schemas, fixtures, manifest, OpenAPI, Flyway migrations, database,
  API behavior, web routes, providers, methodologies, and outcome records remain
  unchanged.
- Named horizons, event anchoring, observed prices, target-hit orchestration,
  outcome completeness, and publication remain later P3 work.
