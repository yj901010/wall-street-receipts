# ADR-084: Separate DEMO comparative receipt persistence and replay reads

- Status: accepted for local implementation; public push requires separate approval.
- Date: 2026-09-14
- Predecessor: ADR-083, merged PR #31 and both PR/merge CI succeeded.

## Decision

Store the ADR-083 canonical comparative input in an additive
`demo_comparative_scoring_receipts` table (Flyway V13). Keep the existing V12
endpoint receipt table, writer, API and audit page byte-compatible. Do not
reinterpret old inputs or activate `scoring_methodologies`/`call_outcomes`.
The profile stays `wsr-demo-comparative-preview/1.0.0`, definition hash
`6fb2d737d177662ec072277f1e345ca10a2c9447d35353bc46c1f886669ad6d2`.
Every receipt is `DEMO`, `PARTIAL_COMPARATIVE`, `dataComplete=false`.

The new application port/JDBC adapter exposes insert and bounded, call-scoped
reads, with no update/delete. An explicit in-process service append locks the
actual call in a READ_COMMITTED transaction, verifies the endpoint terms against
the persisted call/source/document/original snapshot and selected correction via
the unchanged ScoringLedgerVerifier, and evaluates all five metrics through the
unchanged comparative evaluator. The snapshot is original call context only,
never a price substitute. A unique full-input/ledger fingerprint pair and lock
make duplicate concurrent submission return the original UUID and recorded time.
Changed raw comparative membership, as-of or ledger binding creates a new record.

Preserve exact canonical bytes (1 byte to 1 MiB), input and ledger fingerprints,
method identity/full canonical definition/hash, basis correction identity/sequence,
snapshot identity, evaluation as-of and injected-clock recorded-at (UTC microseconds).
V13 uses restrictive composite FKs and rejects non-DEMO/full scope, incomplete
correction tuples, non-correction revision types, impossible times and empty or
oversized bytes. No numeric output is authoritative storage; reads recompute it.

## Read API and evidence boundary

- `GET|HEAD /v1/calls/{callId}/comparative-scoring-receipts`
- `GET|HEAD /v1/calls/{callId}/comparative-scoring-receipts/{receiptId}`

There is no HTTP writer, automatic job, provider connection, command, or Web
integration in this step. Existing security defaults are unchanged; these are
public explicit-DEMO reads. Canonical lower-case UUIDs and bounded call IDs only;
all query parameters are rejected. Results/errors are no-store. 400/404/503
responses never echo input, credentials, SQL, driver or replay exception details.

Reads use read-only REPEATABLE_READ transactions, decode the closed ADR-083 codec,
verify metadata, canonical definition and complete input hash, then reconstruct
the call ledger binding and recompute under the exact stored method version.
Any published row failure rejects the response, without filtering it out, silently
refreshing data, falling back to another record or writing a repair. Recent reads
fetch at most 21 rows and publish at most 20, ordered by recorded time then UUID
descending. The 21st row only signals hasMore; no cursor or automatic winner.
The JDBC adapter has a per-query 3-second timeout; this is not a total HTTP SLA.

The response preserves the old three partial metric meanings and adds independent
benchmark/sector states: AVAILABLE, PENDING, NOT_APPLICABLE or UNAVAILABLE.
Only available metrics have values (scale-12 ratio strings, Boolean for direction).
Missing, conflicting, future or unrepresentable evidence never becomes zero.
Selected resolved reference pairs expose binding/provenance identifiers,
benchmark assignment or sector mapping identity, index provider/definition/calendar,
both original level values/times/source revisions, and continuity provenance.
No pair means null evidence; a selected pair whose ratio overflows retains its
evidence with OUTPUT_NOT_REPRESENTABLE. Rejected/future candidates are not dumped.

Crucially, these comparative reference claims originate from preserved DEMO input,
not an independent persisted provider ledger. Replay/hash verification detects
inconsistent storage, not a coordinated malicious rewrite of payload plus hashes,
nor authenticates a real provider or certifies observed financial facts. No alpha,
ranking, target hit, MFE/MAE, canonical CALCULATED outcome or complete score is added.

## Acceptance and custody

H2 API tests cover append/replay, immutable bytes, changed comparative input,
missing and correction/cancellation/PIT cases, metadata/payload/parent-ledger drift,
bounded pages, sanitized errors, call scoping and absent HTTP writes. A separate
projection matrix checks all 13 raw comparative candidate lists, future/ambiguous/
missing states, independent legs, neutral/waiting/NA distinctions, shared anchor
failure and output overflow without publishing rejected evidence.

Owned disposable PostgreSQL 17 binds random ports to 127.0.0.1 (no reuse). Seed a
populated V12 database including an actual old endpoint receipt, migrate V13 once,
compare every old table's complete row inventory, run six concurrent submissions,
restart the application and perform real GET/HEAD with a SELECT-only role.
Assert old receipt bytes/API shape unchanged, reads do not mutate inventory,
constraints hold, update/delete privileges are absent from the example append
role, and corruption yields 503 without repair. The test append role demonstrates
receipt-table privilege separation, not a deployable production role recipe.

The old PostgreSQL acceptance now explicitly targets its V11->V12 step, with
every assertion unchanged. Six existing migration/release tests change only
latest-version/count assertions and the additive exact V13 inventory entry.
CI pins exact previous blobs and exact new bytes, tests these closed deltas and
retains the historical V11->V12 delta proof. New paths get explicit hashes;
there is no general scoring/product-path allowlist or workflow-body modification.

## Next bounded step

Add an explicit opt-in comparative DEMO append command and read-only audit UI,
reusing this separate storage/API contract. Keep old receipts and routes intact,
no fixtures masquerading as saved records, and no production data activation.
Actual Ubuntu installation/deployment waits for a prepared server.
