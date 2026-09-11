# DEMO scoring receipt creation and audit

This is partial DEMO evidence, not live prices, a canonical outcome or a complete
score. Nothing here automatically seeds a user's database or creates a receipt
when a page is loaded. The browser never calculates financial metrics.

## Read the audit screen

Open `/calls/{callId}/scoring-receipts`, or follow **DEMO scoring receipts** from a
DEMO call's detail. The default is **connection disabled**, not an invented empty
database or a sample result. To query your API, configure the **Web server**:

```text
SCORING_RECEIPTS_PROVIDER=api
API_BASE_URL=http://127.0.0.1:8080
```

Only server-side GETs are sent; URLs may not contain credentials, query or fragment.
Do not use NEXT_PUBLIC variables for API configuration. No cookies, browser
credentials, bearer token or query input is forwarded to the API. The transport
has a five-second abort timer and a 256 KiB streamed response limit. Failure,
invalid input, missing record, true empty and disabled are separate states; none
falls back to fixture values. The Web validator checks shape, identities, timestamps,
partial scope and metric/null coherence, not the underlying price truth. Actual
replay and ledger verification are performed by Java's ADR-081 service.

Leave receipt UUID blank for the latest 20, or enter one full lowercase UUID for an
exact call-scoped read. Ties use descending UUID after the recording timestamp,
including microseconds. There is no canonical winner, aggregate or total count.
Selected receipts show original/correction identity, source/snapshot provenance,
UTC/KST event/as-of/recording times and method/input/ledger hashes. Decimal strings
retain all twelve places and represent ratios, not percentage points. A snapshot
is original-call context, not an inferred basis-price input. Hashes are not signatures.

The existing Next streaming boundary needs JavaScript to reveal the returned page.
GET forms do not calculate metrics, but JavaScript-disabled full UI operation is
not supported; a bilingual noscript notice explains this instead of claiming that
a receipt has been displayed. This phase does not change global rendering settings.

## Explicit local append command

Prerequisites are intentionally manual:

1. Prepare a **separate disposable DEMO PostgreSQL database** named exactly
   `wsr_scoring_demo`, bound to `127.0.0.1` on an explicit port (1024–65535).
   Apply the existing migrations through V12 using your normal approved database
   setup. The command itself never runs Flyway or imports call fixtures.
2. Persist the explicit DEMO call/source/original snapshot and, if used, same-call
   correction evidence first. Record IDs and source terms must match the input.
3. Supply a bounded canonical `.wsr` input file produced by
   `EndpointScoringInputCodec.encode(EndpointScoringInput)`. This is the exact
   versioned ADR-080 binary format, not Java serialization, JSON or arbitrary
   uploaded provider data. Every supplied price must be explicit DEMO evidence;
   the command does not generate prices or derive them from a market snapshot.

Set these variables in the **command process**, not the Web environment:

```text
WSR_DEMO_SCORING_JDBC_URL=jdbc:postgresql://127.0.0.1:55432/wsr_scoring_demo
WSR_DEMO_SCORING_USER=<dedicated local DEMO database role>
WSR_DEMO_SCORING_PASSWORD=<local password supplied privately>
```

There are no credential defaults, URL query options, environment-file loader or
fallback to POSTGRES_* / the normal API datasource. The role needs SELECT on
referenced ledger tables, SELECT/INSERT on demo_scoring_receipts and a suitable
UPDATE privilege on analyst_calls for PostgreSQL's SELECT FOR UPDATE row lock.
Use least privilege; do not grant receipt UPDATE/DELETE. This command does not
provision roles. A database owner remains an administrative trust boundary.

Run the packaged class explicitly (replace paths/IDs; retain argument order):

```text
java -Dloader.main=com.wallstreetreceipts.api.application.scoring.DemoScoringReceiptCommand -cp /absolute/path/api.jar org.springframework.boot.loader.launch.PropertiesLauncher --confirm=APPEND_DEMO_SCORING_RECEIPT --input=/absolute/path/explicit-demo-input.wsr --snapshot=receipt-snapshot
```

There is no Spring application startup, HTTP listener, scheduler or provider call.
The success line contains the receipt UUID and input fingerprint only. Identical
input/ledger submissions return the same UUID and original recording time.
Changed input/as-of creates a separate record, never updates the original.

- Exit 0: acknowledged DEMO receipt; use its UUID in the audit screen.
- Exit 64: invalid arguments/input or missing/unsafe configuration; no DB connection.
- Exit 69: append **not confirmed**. A lost commit acknowledgment is not proof of
  rollback. Inspect or retry the **identical** input; do not invent a new as-of or
  issue cleanup DELETEs. Errors do not echo credentials, file content or driver text.

## Disposable end-to-end rehearsal

`ScoringReceiptBrowserIT` is explicitly opt-in after packaging the current API.
Use `-Dtest=ScoringReceiptBrowserIT`, `-Dwsr.scoring.browser.confirm=DISPOSABLE_DEMO_ONLY`,
`-Dwsr.scoring.browser.web=<secret-free current Web mirror under repository .cache>`,
and `-Dwsr.scoring.browser.jar=<fresh packaged API under repository .cache>`.
The mirror must include current src/public/operator/scoring/e2e, Web build config,
fixtures and installed dependencies, without .env files. The test makes a fresh
Next production build, creates its own loopback PostgreSQL, seeds synthetic parents,
exports explicit binary inputs and executes the packaged command. It then reads
through a separate SELECT-only Spring API and the real production audit page.
It verifies duplicate retry, available/pending/unavailable/correction records,
disabled-write behavior, empty/failure/recovery and 1440/1280/390 layouts including
keyboard, native GET forms, English and the JavaScript-disabled limitation notice,
then the full public browser suite in default-disabled development mode (matching
standard CI and testing real locale actions) on the same verified source mirror.
The receipt-specific API browser checks use the production build. Parent tables are unchanged
by appends and all tables unchanged by reads. Owned processes/DB are removed;
ignored `.cache/adr082-evidence-*` logs/inputs and screenshots remain for inspection.
No user database, production provider or paid API is needed.
