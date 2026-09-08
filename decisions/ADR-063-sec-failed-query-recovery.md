# ADR-063: Explicit recovery of failed SEC lookup inputs

Date: 2026-09-08

## Decision

Add a collapsed Korean/English input-recovery disclosure to the existing error
and not-found boundaries at `/research/sec/filing-history`. These boundaries do
not receive the page's `searchParams`, so use a narrow client island with
`useSearchParams` inside Suspense. It reads the current URL only. No provider
call, fetch, polling, persistence, new route, or default identity is introduced.

Preserve duplicate parameters as arrays and reuse the unchanged strict route
parser. Show recovery only for a complete valid exact query, including child
views. Invalid, unknown, duplicate, incomplete, and missing-router inputs select
nothing; retain the existing return-to-locator control. `Object.fromEntries`
preserves unknown own keys, including prototype names, for strict rejection.

Explicitly label the fields as attempted URL inputs, not verified evidence and
not a guarantee that the ID or cutoff has data. Reuse the existing native GET
locator with no DEMO example. Submission sends exactly `manifestId`, the original
UTC `evaluationAsOf`, and `view=summary`, dropping child pagination. Editing does
not retry. A full-query React key discards unsent fields when the URL changes.
Visible evidence timestamps remain KST; exact UTC identity is never reformatted.

Keep the existing error reset, return link, not-found/noindex handling, parser,
and provider behavior. No API/fixture/schema/dependency or live-data change.
The recovery form is single-column even on desktop because the shared failure
panel has a 760px maximum width. Use the existing visible keyboard focus styles.
This island requires JavaScript; the existing return link remains available
without it. Do not claim full no-JavaScript Next result support.

## Contract custody

Extend the exact two boundary transformations, three source/test hashes, and
two new component/test hashes in `navigation_contracts.py`. The cumulative
inventory is 23 paths. Pin five exact ADR-062 predecessor objects only for
pre-commit development; current working bytes remain mandatory. Mutation tests
reject stale/forged predecessors, duplicate selection, bypassed validation,
clock substitution, stale fields, DEMO substitution, misleading evidence copy,
and automatic reads. No general product allowlist, baseline, historical body,
workflow, or custody exception is relaxed.

## Verification and handoff

Unit tests cover all four views, both locales, malformed/duplicate/unknown
queries, exact native form entries, no fetch, dirty navigation, and the existing
failure controls. Browser tests cover keyboard opening, correction from absence
to exact evidence, discarded pagination, all responsive projects, and recovery
from an isolated closed-loopback API error without fake evidence. Actual local
results and limitations are recorded in `IMPLEMENTATION_LOG.md`.

No API key, account, contact email, domain, live provider request, or home-server
fact is needed. PR upload, hosted CI, merge, release, HTTPS acceptance, and
deployment remain separate handoff steps.
