# ADR-085: Explicit comparative DEMO append and read-only audit screen

- Status: accepted for local implementation; public push needs separate approval.
- Date: 2026-09-14
- Predecessor: ADR-084, PR #32 merged; PR CI #76 and merge CI #77 succeeded.

## Decision and boundaries

Add a separate packaged DemoComparativeScoringReceiptCommand and server-rendered
`/calls/[id]/comparative-scoring-receipts` route. Reuse the unchanged ADR-083 input
codec/evaluator and ADR-084 persistence/read API. Do not change old command,
receipts, routes, public API contracts, V1-V13 migrations, fixtures, providers or
global runtime settings. The only old product edit is one additive DEMO link in
call detail. No automatic import, receipt creation on page load, HTTP write API,
browser upload, live provider connection, canonical outcome or full score.

The command requires exact confirmation APPEND_DEMO_COMPARATIVE_SCORING_RECEIPT,
bounded canonical comparative bytes and a snapshot identifier. Its dedicated
WSR_DEMO_COMPARATIVE_SCORING_* variables must explicitly address
127.0.0.1 on port 1024-65535 and database wsr_comparative_scoring_demo. No aliases,
URL options, credential defaults, .env loader or main API DB fallback.
It directly composes JDBC and one transaction with an injected system UTC Clock;
no Spring startup, listener, Flyway, scheduler or importer. Old endpoint bytes and
the old confirmation cannot select this writer. Success reports only UUID and
input hash. Errors are sanitized; uncertain commit acknowledgment says
UNCONFIRMED, with identical-input retry guidance, never a definite rollback claim.
Receipt UPDATE/DELETE permissions are unnecessary; parent row-lock permission is
explicit in the guide and exercised by the restricted append-role rehearsal.

The Web connection is separately disabled by default
(COMPARATIVE_SCORING_RECEIPTS_PROVIDER). Disabled does not imply empty storage.
Explicit api mode makes only server-side no-store GETs to API_BASE_URL, without
forwarding cookies, credentials, authorization or user-supplied query data.
One optional canonical UUID selects exactly one call-scoped record. Reject
duplicate/unknown/malformed parameters before access. Bound the response to 1 MiB
and the fetch to an abort timer of five seconds; no redirect, invalid UTF-8, JSON,
method/profile/scope/identity/time/metric mismatch or broken-row fallback.
An otherwise-valid oversized page is unavailable rather than partially rendered.

Preserve the five Java-calculated partial metric states and exact ratios. Use
BigInt only to validate decimal bounds and timestamp ordering, never to calculate
financial outcomes. Reference levels must be positive exact numeric strings;
selected pair times must agree with basis/as-of and source revisions must agree.
Require null evidence without a selected pair, and selected evidence for available
or output-unrepresentable results. Preserve provider identity whitespace as escaped
text, not markup or a navigable source URL. The API replays the actual canonical
input and ledger; this Web adapter checks the wire contract, not price truth.

KO/EN dense table, independent horizontal table scrolling, native GET locator,
keyboard focus, loading/noscript notice, disabled, invalid, missing, unavailable,
true empty, replay selection, correction metadata and reference panels follow
the existing restrained audit UI. Explain that partial DEMO provenance is neither
a signature nor independent provider/observed-market authentication. No alpha,
target hit, ranking, MFE/MAE or representative latest-winner score is implied.
Unexpected render errors inherit the existing calls error/retry boundary.

## Verification

New command guards, wire mutation/precision/provenance/time tests, transport
timeouts/bounds, pre-fetch query tests and KO/EN presentation tests run alongside
the unchanged full API/Web suites. The full-stack test is explicit opt-in, not an
auto-skipped integration test. On a source-identical, secret-free Web mirror it
fresh-builds Next, seeds its own loopback PostgreSQL V13, executes the current
packaged command with a restricted append role, checks duplicate identity and
unchanged parent inventories, then reads via SELECT-only Spring and the real
production UI. Cover available, pending, missing-reference, correction, empty,
query/missing, revoked-read failure/recovery, 1440/1280/390 layouts and keyboard/
locale/noscript behavior. Run all public E2E in default-disabled development mode.
Only test-owned processes/containers are cleaned up. Local logs/screenshots stay
under ignored .cache. No user DB is migrated or seeded.

CI extends exact path hashes and accepts only the exact merged old call-page blob
for precommit work. Keep the old endpoint-link proof and add a separate exact
comparative-link proof. No neighboring-path allowance, broad workflow rewrite or
old calculator/test substitution.

## Next scope

This completes the five-metric comparative DEMO input -> stored receipt -> explicit
command -> read-only audit flow. Further product work must choose a bounded
remaining metric/event-window or final canonical outcome lifecycle; ten-metric
completion, live evidence collection, rankings and deployment are not complete.
Ubuntu installation still waits for a prepared server.
