# ADR-055: Disposable offline SEC manifest-audit API-mode full-stack acceptance

- Status: Accepted
- Date: 2026-08-31
- Depends on: ADR-041, ADR-045, ADR-052, ADR-053, ADR-054

## Context

ADR-045 proves the production Next-to-Spring call-audit path against an isolated
PostgreSQL database. ADR-052 and ADR-053 separately prove an exact SEC manifest
audit API and its same-origin web consumer, but their browser acceptance did not
prove a successful API-mode path from durable PostgreSQL evidence through the
packaged Spring application and production Next server. API-mode browser tests
could prove only honest failure, while fixture mode supplied the successful UI
case.

That gap matters for the planned home-server configuration because production
pins `SEC_MANIFEST_AUDIT_PROVIDER=api` and forbids fixture fallback. A useful
local gate therefore needs one deterministic persisted manifest without
contacting SEC, enabling the operator API, importing the developer's root
`.env`, or adding a production seed endpoint.

## Decision

### Extend the existing disposable full-stack gate

Extend `scripts/verify-local-full-stack.ps1` instead of introducing a second
full-stack orchestrator. One run now:

1. checks the existing Java 21, Node.js 24, local Docker, dependency, and
   repository prerequisites;
2. packages Spring into a harness-owned output directory;
3. builds production Next from a secret-free source mirror with both call and
   SEC audit selectors fixed to `api`;
4. starts a uniquely named PostgreSQL 17 Compose project on loopback;
5. inserts one exact synthetic SEC manifest through a test-only Java harness;
6. starts the packaged loopback Spring application with live SEC and the
   operator API disabled;
7. starts production Next and smoke-checks 13 primary routes; and
8. runs five focused Chromium tests, checks 18 exact Tomcat access-log lines,
   and validates one exact PostgreSQL identity tuple.

Success or failure retains ADR-045 cleanup ownership: only the exact API/web
processes, Compose project and volume, source mirror, temporary build output,
browser reports, logs, and atomic shared lock owned by this run may be removed.
The root `.env`, default Compose project and database volume, normal web build,
`apps/web/next-env.d.ts`, and `apps/web/tsconfig.json` are outside that cleanup
boundary.

### Test-only seed through production repositories

`SecManifestAuditAcceptanceSeedHarness` is a JUnit class whose name
intentionally does not match default Surefire discovery patterns. It runs only
when Maven names that exact class and supplies the exact opt-in system property
`-Dwsr.sec-manifest-acceptance-seed=true`. Ordinary `test`, `verify`, packaged
application startup, and production runtime do not execute it.

The harness does not use raw SQL or a public/private HTTP mutation. It writes
the shared synthetic evidence through the production
`FilingCatalogCaptureRepository`,
`HistoricalFilingSegmentCaptureRepository`, and
`PersistFilingHistoryCollectionManifestService`, reloads the durable captures,
and reads the result through `SecFilingHistoryManifestAuditQueryService`. The
same Java test fixture constructs the committed ADR-053 JSON parity artifact,
so the API-mode and fixture-mode success cases cannot silently diverge.

Before Spring test context creation and again through the effective datasource,
the seeder requires all of the following:

- the exact database and user name `wsr_full_stack_acceptance`;
- a `jdbc:postgresql://127.0.0.1:{1024..65535}/wsr_full_stack_acceptance`
  datasource URL, identical Flyway URL, and non-default per-run 32-hex password;
- `SEC_PROVIDER_ENABLED=false`, `OPERATOR_API_ENABLED=false`, an empty SEC
  contact email, and the closed loopback SEC base URL `http://127.0.0.1:1`; and
- initially empty root-capture, historical-segment, and manifest repositories.

Any mismatch refuses the seed before evidence is written. A fixed injected UTC
clock assembles exactly one manifest at `2026-08-25T03:30:00.123456Z`; the
result must have manifest ID
`cda6762d385d4e889294d0fec1f7a2a7b20c5157cf67c832b7d7f4857550a1cd`,
two selected descriptors, four accession groups, and six occurrences. A cutoff
one microsecond earlier must remain not found.

### Synthetic identity remains visibly DEMO

The persisted rows are synthetic acceptance evidence, even though they travel
through the real API path. The harness therefore pins the exact synthetic
manifest identity in the server-only
`SEC_MANIFEST_AUDIT_SYNTHETIC_DEMO_MANIFEST_ID` setting for its Next build,
runtime, and browser child. Only a response whose requested manifest ID equals
that exact lowercase SHA-256 identity receives the existing `DEMO` badge and
"synthetic, not actual SEC data" disclosure. The setting does not alter the
API response, synthesize `dataMode`, or label any other API manifest DEMO.

This setting is acceptance metadata, not a data-provider credential or a
production live-data selector. It stays server-only and is never exposed as a
browser-to-Spring request or a public API field.

### Exact full-stack evidence

The gate smoke-checks the previous 12 primary product routes plus
`/research/sec/filing-history`. Five retry-free Chromium checks cover the call
list, call revisions, call outcomes, and the two SEC manifest tests. The SEC
success path visits summary, descriptors, accessions, and occurrences, keeps
KST visible timestamps with exact UTC `datetime` values, retains bilingual
evidence, shows the synthetic API identity as DEMO, and observes no browser
request to the private Spring origin. The second SEC test proves malformed
local rejection and the same sanitized 404 for a cutoff one microsecond before
assembly.

The required Tomcat set remains exact full-line membership. It contains the 13
existing call reads plus five manifest reads: four HTTP 200 resources and the
pre-assembly HTTP 404. ADR-054's Korean calendar-day call filter is corrected
to the exact UTC interval:

```text
from=2026-08-10T15%3A00%3A00.000Z
to=2026-08-11T15%3A00%3A00.000Z
```

The PostgreSQL check compares this complete no-whitespace tuple, not only table
cardinalities:

```text
3|2|4|3|1|2|2|2|4|1|2|4|6|0|0|0|0|cda6762d385d4e889294d0fec1f7a2a7b20c5157cf67c832b7d7f4857550a1cd|eadb0c3bf6efb9b3323be1342d0b17e63631b706f088b23fa78e784e1b547acd|c9bfc935b27e059397531a4dda1a1a0222e98528c33e85b886c91ca6b74f2fa8|2026-08-25T03:30:00.123456Z
```

In order, those values prove three calls, two revisions, four outcomes, three
decoded bodies, one root capture, two root recent rows, two root descriptors,
two historical captures, four historical rows, one manifest, two selected
descriptor rows, four accession groups, six occurrences, zero attempt/action/
dispatch/outcome operator rows, and the exact manifest, selection, root, and
assembly identities.

### External requirements and CI boundary

This local acceptance phase needs no API key, SEC account, paid plan, domain,
home-server access, operator token, user token, OAuth credential, or monitored
email. It uses only synthetic evidence and an isolated local PostgreSQL
database. Live SEC collection and the operator HTTP boundary remain disabled,
so no SEC request is permitted. A monitored `SEC_CONTACT_EMAIL` is still
required only for a later separately approved live collection operation and
must then be supplied through an untracked server secret, never chat or Git.

Repository CI parses and guards the ADR-055 source, command, selectors, safety
markers, expected counts, and nested historical projection. It does **not**
execute this Docker/Chromium full-stack harness. The command remains a manual
local pre-deployment gate, and no runtime PASS is claimed by this decision
until an actual run is recorded in `IMPLEMENTATION_LOG.md`.

## Verification plan

Run from the repository root on a machine with PowerShell 7, Java 21, Node.js
24, installed workspace dependencies, Playwright Chromium, and a local Docker
daemon:

```powershell
pwsh -NoProfile -File ./scripts/verify-local-full-stack.ps1
```

The run is accepted only if all 13 routes, five Chromium tests, 18 exact access
lines, exact database tuple, no-browser-private-origin assertion, disabled
live-SEC/operator boundaries, and owned-resource cleanup pass together. The
implementation log records the observed result only after the command actually
runs.

## Explicitly excluded

ADR-055 does not:

- add a production seed, mutation route, startup importer, scheduler, CLI, or
  raw-SQL fixture path;
- enable SEC network traffic, operator execution, retry, collection, or a
  current/latest/company manifest selector;
- make synthetic rows observed filings or grant permission to publish them as
  live, realtime, delayed, or complete SEC history;
- expose Spring through the browser or production Caddy boundary;
- execute the Docker harness in repository CI; or
- prove the future Ubuntu machine, DNS, TLS, router, capacity, or backup ready.

## Consequences and next work

- One offline command can now exercise the intended home-server API-mode SEC
  audit path without external credentials or a production backdoor.
- The exact identity pin keeps API transport evidence distinct from live-data
  status and prevents synthetic persisted rows from losing their DEMO label.
- The full-stack gate is longer because it runs one explicit Maven seed test and
  two additional Chromium checks.
- The exact local run completed on 2026-08-31 and is recorded in
  `IMPLEMENTATION_LOG.md`. Git/release handoff and later home-server rehearsal
  remain separate steps, and live collection still requires the operator's
  monitored contact email and separate approval.
