# ADR-088: Explicit target-hit DEMO append and read-only audit

- Status: accepted; user approved verified 27-file feature-branch push.
- Date: 2026-09-14
- Predecessor: PR #35 `feat(scoring): persist demo target-hit receipts` merged at
  `9b80fe0985933e3752687074ae2ca1f2b6707fb5`. PR CI #82/run 34804737070 and
  merge CI #83/run 34805669176 both succeeded.

## Decision

Complete the separate six-metric DEMO input -> stored receipt -> explicit command
-> audit flow. Add DemoTargetHitScoringReceiptCommand and server-rendered
`/calls/[id]/target-hit-scoring-receipts`, reusing unchanged ADR-086 bytes/evaluator
and ADR-087 persistence/read APIs. The only old product edit is a DEMO-only link
on call detail. No old commands, calculators, contracts, fixtures, V1-V14 migrations
or global Web configuration change. No live provider, HTTP writer, browser upload,
scheduler, automatic append or canonical score.

## Explicit command

Require APPEND_DEMO_TARGET_HIT_SCORING_RECEIPT confirmation, bounded canonical
TargetHitScoringInputCodec bytes (1 MiB) and snapshot ID. Independent variables
WSR_DEMO_TARGET_HIT_SCORING_* select exactly 127.0.0.1:1024-65535 and database
wsr_target_hit_scoring_demo. No aliases, URL options, credential defaults, .env
loading or API database fallback. Reject old endpoint/comparative bytes and
confirmations. Compose JDBC and a bounded transaction directly; no Spring startup,
Flyway, listener, importer or provider. Retries retain UUID/time; changed input
appends. Success prints UUID/hash only; exit 64 rejects before connecting; exit 69
means UNCONFIRMED, not proof of rollback, with identical-input retry guidance.
Manual DEMO prerequisites and restricted permissions are in TARGET_HIT_SCORING_RECEIPTS.md.

## Read-only screen

TARGET_HIT_SCORING_RECEIPTS_PROVIDER defaults disabled, distinct from empty storage.
Explicit api mode sends server-only no-store GETs to API_BASE_URL with five-second
abort and 1 MiB response bounds. No cookies, auth/query forwarding, redirects or
fallback. Reject malformed/duplicate/unknown query parameters before fetching;
one optional canonical UUID selects an exact call-scoped record.

The closed validator requires pinned methodology/hash, DEMO/PARTIAL_TARGET_HIT/false
scope, identity, microsecond order, correction metadata and six metric/null states.
Preserve exact ratio and positive level strings. Java alone calculates outcomes;
BigInt compares numeric bounds and wire consistency, never recalculates a target hit.
Available hit/miss requires selected target/binding/window observation evidence.
Other states require null evidence, never false. Validate field/value, high >= low,
currency/asset/venue/source revisions, temporal bounds and PIT availability/capture,
unique bounded sessions and coverage/adjustment/continuity tags. These checks are
not price truth or independent raw-trade verification. Preserve source text as
escaped text, not HTML/links; do not publish raw bytes or future/rejected candidates.

KO/EN dense table, local horizontal scrolling, native GET locator, keyboard focus,
loading, disabled/invalid/missing/unavailable/empty/selected states and existing
calls error/retry boundary follow the audit UI. Selected details include UTC/KST,
fingerprints, target/window evidence and unchanged comparative evidence. Show
CALLER_ATTESTED_DEMO_CAUSAL_WINDOW_NOT_RAW_TRADE_VERIFICATION and explain that
halt/no-trade/auction/finality/correction completeness is not established.
Missing is not zero/miss; latest is not a winner. Existing Next streaming still
requires JavaScript, with an explicit noscript notice.

## Verification and custody

Command guards, wire mutations, transport/query and KO/EN view tests run alongside
full API/Web suites. Explicit opt-in TargetHitScoringReceiptBrowserIT validates a
secret-free source-identical Web mirror, fresh-builds Next, starts owned loopback
PostgreSQL V14, executes the packaged command with restricted append permissions,
then reads via SELECT-only Spring and production UI. Cover retry, hit/miss/pending/
missing-window/missing-reference/correction, empty, failure/recovery, 1440/1280/390,
keyboard, locale and noscript; then all public E2E in disabled development mode.
No user database is migrated/seeded. Only owned processes/containers are cleaned up.

CI adds 20 exact custody paths (118 total), pins the old call-page predecessor and
keeps all 97 other previous paths unchanged. Historical endpoint/comparative link
and V12/V13/V14 proofs remain; a new exact target-hit link proof is separate.
No neighboring-path allowance or historical body/workflow rewrite.

## Remaining scope

This completes partial six-metric DEMO audit flow, not P3 or a production release.
MFE/MAE raw-coverage prerequisites, alpha/sector alpha, canonical outcome lifecycle,
ranking and actual Ubuntu deployment remain unfinished.
