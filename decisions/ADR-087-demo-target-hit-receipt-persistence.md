# ADR-087 — DEMO target-hit receipt persistence and read API

- Status: Accepted; user approved the verified 26-file feature-branch push.
- Date: 2026-09-14
- Predecessor: ADR-086, PR #34 merged at
  `718b03209ee19c69c665f795a2598c8702d07ab2`. PR CI #80 succeeded;
  merge CI #81 subsequently succeeded before final local verification.

## Decision and boundaries

Store the unchanged ADR-086 six-meaning DEMO profile in a new V14 table,
`demo_target_hit_scoring_receipts`. Add insert/select repository, explicit
in-process application service, closed response projection and two call-scoped
read routes:

- `GET /v1/calls/{callId}/target-hit-scoring-receipts`
- `GET /v1/calls/{callId}/target-hit-scoring-receipts/{receiptId}`

HEAD is supported; no HTTP writer, auto-append on reads, scheduler, fixture
fallback or provider activation exists. This is PARTIAL_TARGET_HIT with
dataComplete=false, never a canonical outcome, completed P3, live observation,
aggregate or ranking. Existing endpoint and comparative profiles, byte streams,
tables, read APIs, commands, fixtures, V1-V13 migrations and Web remain unchanged.

The six production additions are TargetHitScoringReceiptRepository,
JdbcTargetHitScoringReceiptRepository, TargetHitScoringReceiptService,
TargetHitScoringReceiptController, TargetHitScoringReceiptResponse and V14 SQL.
The separate OpenAPI contract is `contracts/target-hit-scoring-receipts.openapi.yaml`.
Four new Java test classes cover API/ledger, projection, boundary and PostgreSQL.

## Storage, concurrency and replay

Preserve the full bounded canonical input bytes, exact methodology definition and
identity, input and ledger hashes, original/correction identity and sequence,
call/snapshot identifiers, evaluation-as-of and recorded-at in UTC microseconds.
A snapshot is the original call's context, not the metric's price/window source.

Append holds the original call row lock in a READ_COMMITTED transaction. Verify
the existing ScoringLedgerVerifier's persisted DEMO call, source, snapshot and
actual correction terms, then evaluate ADR-086. A unique input/ledger identity
returns the previous record, including its original UUID and timestamp. Changed
inputs append separately; no representative winner is selected. Clock is injected
and recorded time is truncated to microseconds; future evaluations are rejected.

The table enforces DEMO/partial/false scope, 1..1048576 input bytes, recorded time
not earlier than as-of, same-call snapshot, and optional actual CORRECTION revision
identity/sequence/type. Foreign keys restrict deletion; no new update/delete
repository method exists. Database owners can still modify rows: immutability
requires deployment permissions, not an assertion that SQL owners are powerless.
The rehearsal executes actual idempotent/new appends with SELECT on parent tables,
INSERT only on this receipt table and UPDATE(call_id) on analyst_calls for row
locking. Receipt UPDATE/DELETE are denied. Existing parent inventories stay equal.

Every public read uses a REPEATABLE_READ read-only transaction and re-decodes the
stored canonical bytes, validates hashes and exact method definition, rebinds the
current persisted ledger to the original evaluation information set, and reruns
the deterministic evaluator. Fail closed on corruption or ledger drift. Do not
silently drop a broken published row, recompute from a latest snapshot or write a
replacement. Noncanonical stored UUIDs are rejected, never normalized into a
different published identity. JDBC queries have three-second statement timeouts.

The page is the latest 20 verified records ordered by recordedAt DESC, receiptId
DESC, using a 21st row only as hasMore evidence. It exposes no total, offset,
cursor or canonical latest winner. An exact call-scoped UUID never falls back to
another record. All query parameters and malformed identifiers are rejected.
Empty known DEMO call, missing/non-DEMO call and unavailable verification remain
distinct. GET/HEAD responses are no-store; query/storage/transaction/replay errors
are sanitized without exposing input, credentials or SQL.

## Six metric meanings and selected window evidence

Keep the five comparative values and selected benchmark/sector evidence. Add
targetHit as a Boolean only when AVAILABLE, including a genuine false for a miss.
PENDING preserves the exact eligibility pending reason; NOT_APPLICABLE preserves
neutral/explicitly absent-target meaning. Eligibility-unavailable and
window-evidence-unavailable retain their own reason chains; neither is false.
Decimal metric values remain exact scale-12 ratio strings, not percentage points.

Only a resolved target-hit selection carries windowEvidence. It contains the
selected target, window binding and exact high/low observation, their source/
provider/provenance identities, currency, temporal bounds, ordered session IDs,
coverage/adjustment/continuity declarations and the selected high/low field/value.
All positive levels remain exact decimal strings. Future and rejected candidates
are retained only in private stored input for replay, never the public response.

The attestation scope explicitly says
CALLER_ATTESTED_DEMO_CAUSAL_WINDOW_NOT_RAW_TRADE_VERIFICATION. These are input
claims, not provider authentication, licensed observed prices, tick-sequence/
no-trade/halt/auction/finality/correction coverage proofs or MFE/MAE evidence.
Ledger binding verifies persisted forecast/source context; it does not authenticate
the supplied window high/low or comparative provider assertions. ADR-034's raw
coverage and rights prerequisites remain intact.

## Verification and migration custody

The new tests cover immutable bytes, identity/time-preserving retries, changed
inputs, corrections/cancellations, forged terms, PIT source/context, corrupt
metadata/payload/ledger, bounded pages, HTTP non-writes, sanitized acquisition/
query/commit failures, exact selected evidence, Boolean false versus null,
all prior comparative cases, missing/future/poisoned window evidence and explicit
attestation text. Actual response object fields are checked against the closed
OpenAPI contract.

Mandatory PostgreSQL acceptance starts a fresh owned loopback-only database at V13
with actual endpoint and comparative receipts, applies exactly V14 and verifies
all earlier inventories/bytes unchanged. Exercise six concurrent submissions,
restart replay, restricted-role actual appends, SELECT-only GET/HEAD, same-call
scoping, SQL constraint failures, corruption fail-closed and recovery. No real
user database or Ubuntu server is used.

CI custody has 98 exact scoring paths (11 additions). Keep 86 of the previous 87
paths byte-identical; the sole old comparative upgrade test is pinned explicitly
to its original V13 target. Six existing migration/inventory tests advance only
latest-version expectations and the exact additive V14 inventory row. Both
historical V12/V13 proofs remain, with separately pinned merged predecessors.
No neighboring-path allowance, financial golden change or workflow body rewrite.

## Next work

Add an explicit local DEMO append command and a read-only audit UI for this new
profile. Do not feed these bytes to old commands or tables. Canonical outcome
publication, MFE/MAE, alpha/sector alpha, ranking and actual Ubuntu deployment
remain unfinished. API registration adds reads only; no provider/real DB import,
production migration or public write authorization is implied.
