# ADR-060: SEC evidence navigation with current-source contracts

- Status: Accepted
- Date: 2026-09-03 (KST)
- Prerequisites: ADR-053, ADR-054, ADR-057, ADR-058, ADR-059

## User-facing change

Expose the existing `/research/sec/filing-history` exact evidence-audit page as
the ninth primary navigation entry. Preserve the previous eight entries and
their order. The new typed ID is `secEvidence`, with Korean `SEC 증거` and
English `SEC evidence`. It links to the bare locator URL, with prefetch disabled.
It does not select a manifest, a cutoff, a company, or a latest record.

The normal locator/result page and its loading, error, and not-found states pass
`current="secEvidence"` to the shared header. Only that primary-navigation link
is current; the separate evidence-view tabs retain their own active state. Keep
all data-mode conditions, fixture disclosures, provider calls, query parsing,
source identity, and KST display / UTC query-key semantics unchanged.

Keep the existing visual design. Move the header-only two-row breakpoint from
1120px to 1280px to accommodate nine entries in both languages. Retain the mobile
horizontal navigation scroller and put the existing focus outline inside its
link so overflow clipping does not hide the outline. No new UI dependency,
framework, imagery, route, authentication, or external hosting is introduced.

## Current-source contract migration

The frozen legacy guards are not adequate proof for the new menu. In particular,
legacy step 34 extracts IDs using `[a-z]+`, which would silently omit camelCase
`secEvidence`. Step 26 also prohibits a primary `/markets/sp500` link, and step
37 protects the maps/screener/methodology ordering. ADR-053 custody covers the
SEC route states. All of those unchanged historical artifacts continue to run
only as historical evidence.

`scripts/ci/navigation_contracts.py` owns the reviewed current-source delta:

- Seven runtime files are reconstructed from the pinned baseline using explicit
  single-occurrence text edits. All other bytes remain unchanged, including SEC
  provider/query/data-mode logic and non-header stylesheet rules.
- Nine existing unit/E2E test files have exact reviewed content hashes. Their
  previous assertions are retained with the nine-item navigation expectations;
  new assertions cover both languages, all current-item choices, unselected
  locator behavior, route states, and keyboard entry through the actual menu.
- Baseline and current Git mode/type/blob records must match their respective
  exact source. Local pre-commit HEAD may still contain baseline bytes, but the
  working source must already match the reviewed migration. Missing, linked,
  reparse, executable, deleted, or unrelated changes fail closed.
- The bridge calls this verification before comparing frozen product trees and
  includes all 16 exact paths in source custody. No product path is added to
  `FIXED_CI_PATHS`; these are conditional exact deltas, not broad exemptions.

Python mutation tests check the complete nine IDs/labels/destinations rather
than the historical lower-case-only regex. Missing, duplicated, reordered,
renamed, query-bearing, outbound, prefetched, wrongly current, or otherwise
modified entries fail. Unrelated bytes and weakened test assertions also fail.
The existing CI Vitest and Playwright jobs execute the current test files,
including the isolated SEC API-failure case. No application job is replaced
with a frozen-checkout test or weakened command.

This deliberately updates the compatibility bridge for one bounded UI change;
it does not retire the bridge or authorize additional product changes. The
pinned baseline, manifest, all 84 legacy bodies, and seven restoration steps
remain unchanged. The prior ADR-058 dedicated-database test migration and
ADR-059 current-fixture gate remain mandatory.

## Verification and external inputs

Record actual commands, counts, omissions, and viewport results in
`IMPLEMENTATION_LOG.md`. Runtime verification must include both locales,
1440/1280/390px, keyboard Methodology → SEC → locale buttons, no page overflow,
explicit DEMO selection, and unchanged KST evidence. Check the intermediate
1121/1200px widths as well. The bare locator must never call `findExact`, and
an unavailable API must not turn into DEMO evidence.

No new API key, provider request, paid service, domain, home-server fact, or
deployment is required. Use secret-free owned mirrors for local Next build/dev
verification to preserve the user's uncommitted generated Next declaration.
This work stays local pending a separately authorized PR upload and review.
