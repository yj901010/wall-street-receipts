# ADR-082: Explicit DEMO receipt append and read-only audit screen

Status: Accepted for implementation; live deployment remains separate.

## Decision

Expose ADR-081 receipts in a server-rendered nested call audit route and add a
standalone packaged local append command. Keep the public API read-only and retain
all prior evaluator, storage, migration, provider and fixture semantics unchanged.
No creation button, browser upload, automatic seed or live numeric inference.

The command requires exact confirmation, a canonical bounded input file, explicit
snapshot identity and credentials for 127.0.0.1 / wsr_scoring_demo only. It directly
composes JDBC repositories and a transaction around the existing service; it never
boots Spring, Flyway, an importer or a scheduler. Identical retry reuses the receipt;
uncertain commit acknowledgment is reported as unconfirmed, never as definite
rollback. No connection string, path, input or exception text is printed on failure.

`/calls/[id]/scoring-receipts` links from DEMO call detail and queries only when the
server explicitly selects SCORING_RECEIPTS_PROVIDER=api. Disabled is not empty.
One optional UUID selects exactly one call-scoped record; malformed, duplicate or
unknown parameters are rejected before fetching. No-store GETs have bounded body
size, strict JSON/UTF-8 and no redirects. Wire identity/partial/method/time/decimal/
null/order checks fail closed. The API, not the browser, verifies canonical input
and recalculates metrics. JS numeric conversion is not used for financial values.

Display all three partial metric states/reasons without missing-to-zero/false.
Show original versus correction, exact hashes/provenance, timestamps and explicit
snapshot context-only and non-signature limitations. KST display reuses the site's
existing timestamp formatter. Native GET forms, focus, horizontal table-only
scrolling, KO/EN, loading, disabled, empty, missing, error and recovery are supported.
Existing Next streaming requires JavaScript to reveal the completed page; a bilingual
noscript notice documents this limitation. No JavaScript-free UI operation is claimed
and global rendering settings remain unchanged.
Changing a locator clears its prior form value; no latest-winner inference.

## Verification boundary

Unit tests cover command guards, adapter mutation/precision/time/scope checks,
transport errors/bounds, and presentation. Ordinary E2E verifies the truthful
default-disabled route and link. Opt-in PostgreSQL/full-stack rehearsal executes
the actual packaged append class and real production Next UI with a SELECT-only
API, not a synthetic HTTP server. Source mirror identity is checked before a
fresh build; containers/processes are test-owned and cleaned up. See
IMPLEMENTATION_LOG.md for completed checks, including any environment limits.
The separate full public suite uses CI-equivalent development mode and actual
locale actions; receipt-specific database/browser acceptance uses production Next.

Current CI adds exact source/test/document custody and one closed existing-call
page link delta; no old calculation/migration or historical workflow changes.
Usage and prerequisites: `SCORING_RECEIPTS.md`.

## Remaining scope

This is usable partial DEMO auditing, not ten-metric completeness, a canonical
outcome lifecycle, ranking/aggregate publication, observed provider price data or
Ubuntu deployment. Further work must explicitly scope remaining metrics, event
windows/source prerequisites and final outcome publication; existing pending
infrastructure requirements are not completed by this UI.
