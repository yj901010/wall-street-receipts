# ADR-081: Persist and replay partial DEMO scoring receipts

Status: Accepted for local implementation; release/host activation is separate.

## Decision

Preserve ADR-080's exact canonical input bytes, method definition/identity, as-of,
recording time and separate ledger fingerprint in `demo_scoring_receipts` (V12).
There are no stored metric values to trust: reads decode, re-encode byte-for-byte,
check the input hash, verify persisted source lineage and recompute with the pinned
method. This is `DEMO / PARTIAL_ENDPOINT / dataComplete=false`, never CallOutcome,
an ACTIVE methodology registry entry, target-hit/alpha/ranking, or live evidence.

`ScoringReceiptService.append` is an explicit transactional in-process seam, not a
public HTTP writer, command, importer, scheduler or automatic fixture seed. A
per-call row lock precedes duplicate lookup; `(input_fingerprint, ledger_fingerprint)`
is unique. Identical submissions return the original UUID/time. New input or
as-of appends a new record. Recording uses injected Clock, truncated to UTC
microseconds, and cannot precede evaluation. No source calls are changed.

## Evidence boundary

- Original basis must match the actual call event. Correction basis must match a
  persisted same-call CORRECTION event and sequence; missing, foreign or
  cancellation revisions cannot serve as forecast bases. Terms evidence identity
  is the call ID or selected revision ID. Direction, target disposition/value/
  currency/date, provider/event identity, processing/capture times and provenance
  must equal the actual original/corrected terms (decimal presentation scale aside).
- Call, source reference/document and original snapshot must be explicit DEMO and
  visible by evaluation-as-of. Selected corrections must also be visible. A known
  cancellation rejects evaluation; later/late-captured cancellations do not enter
  the earlier information set. Backfilled history or administrative parent edits
  can invalidate verification; the API fails closed, never silently republishes a
  changed historical result. This is not the full cancellation/outcome lifecycle.
- Snapshot must belong to the original call/asset at its event time. Its full
  context is hashed, including nulls and provenance. It is marked
  `ORIGINAL_CALL_CONTEXT_ONLY_NOT_PRICE_SOURCE`, also for a correction. No missing
  price, price adjustment, currency conversion or return basis is inferred from it.
  Separate supplied DEMO price evidence remains synthetic, not provider-attested.
- Ledger hash V1 uses ordered, UTF-8 length-prefixed fields, explicit nulls and
  normalized decimals over call/source/snapshot/selected-revision content. Only
  master identity IDs are bound; mutable master display fields are not a PIT
  market-data source. Input hashes include rejected candidates and ordered metadata.
  Neither hash is a signature or a defense against a DBA rewriting every input,
  parent and hash. Valid administrative timestamp changes are not authenticated.

## Storage and read API

V12 adds same-call snapshot and correction tuple foreign keys, UTC microsecond
timestamps, input-size/time/DEMO/partial checks, unique submission identity and a
call/time/UUID index. Existing V1-V11 migrations and data remain intact. Repository
port has insert/select/row-lock only, no update/delete operation. Restricted SQL
roles can enforce append-only access; role provisioning is not added to deployment
here. Existing owner credentials remain a privileged administrative trust boundary.

`EndpointScoringInputCodec` admits only the exact ADR-080 record/enum allowlist,
never Class.forName or Java deserialization. It bounds bytes (1 MiB), nesting,
list count and text before allocation, rejects malformed Unicode and noncanonical
decimal/text representations, validates record constructors, EOF and byte replay.

Public additive reads (also HEAD):

- `GET /v1/calls/{callId}/scoring-receipts`: newest 20, deterministic recorded-at/
  UUID descending order, `hasMore`; no implied canonical winner or cursor.
- `GET /v1/calls/{callId}/scoring-receipts/{receiptId}`: exact scoped record.

Read transactions are REPEATABLE_READ. Verify all published rows before emitting
a list; never skip a corrupt row or select another record as fallback. Strict
opaque identifiers/lowercase full UUIDs, no query parameters. Known DEMO/no rows
is empty; unknown/non-DEMO/cross-call is 404; invalid query is 400; storage,
transaction or integrity failure is sanitized 503. Successful reads and these
errors use no-store. Mutation methods have no write handler (405). Raw input
bytes/fragments are not public response fields. Private JdbcTemplate's 3-second
statement timeout is not a full HTTP deadline or bounded CPU/admission SLA.

Projection separates AVAILABLE/PENDING/UNAVAILABLE/NOT_APPLICABLE and exact nested
reasons. Ratios are scale-12 decimal strings; unavailable values are null, never
zero. Neutral direction stays NOT_APPLICABLE when the shared return is pending.
UTC/KST timestamps, method/input/ledger fingerprints and terms/snapshot provenance
are explicit. Additive contract: `contracts/scoring-receipts.openapi.yaml`.

## Verification and follow-up

Tests cover canonical/golden codec, corruption/limits, source/PIT/DEMO binding,
correction membership, cancellation, null/neutral readiness, idempotency/bounds,
scoped HTTP/error behavior, real V11-to-V12 PostgreSQL preservation, six concurrent
appends, restart replay, SELECT-only HTTP and SQL constraints/permissions. Temporary
PostgreSQL binds only 127.0.0.1, uses synthetic data and is removed on completion.
See IMPLEMENTATION_LOG.md for actual final regression results (not inferred CI).

Keep all old scoring/calculator bytes pinned. Add exact custody for new paths;
six existing migration/release tests update exact latest-version assertions and
append the V12 packaged inventory entry, with reviewed hashes updated. Frozen
historical CI remains historical, while current API tests exercise V12.
No dependency, old fixture/contract, UI or deployment
changes. Next: separately scoped DEMO audit screen and explicit receipt creation
workflow; remaining metrics/lifecycle/aggregates and real data prerequisites stay
separate. No real score or receipt is automatically published by this release.
