# ADR-062: Explicit SEC result query refinement

Date: 2026-09-07

## Decision

The existing `/research/sec/filing-history` result now contains a collapsed,
native `details` editor in Korean and English. Reuse the SSR GET locator with
the exact validated manifest ID and original UTC cutoff from the current query.
Do not copy result metadata, rounded KST text, server time, or a DEMO default
into the inputs. All four result views, including empty child pages, offer the
same editor. No provider read is added when opening or editing it.

Submission sends only `manifestId`, `evaluationAsOf`, and `view=summary` to the
existing same-origin route. The previous child view, page, and size are not
carried over. Copy explicitly separates unsent edits from the current result.
Malformed submissions retain the strict parser, zero-read error boundary, and
ADR-061 field recovery. Absence and transport errors retain their current states;
there is no fixture fallback, identity selection, or live-data claim.

Key the disclosure on the complete validated query so a new result closes it
and discards unsent edits. Key the locator form on its invalid flag and complete
feedback so navigation between different invalid URLs cannot retain old DOM
input values. Local edits do not mutate these server-provided keys.

No route, client island, dependency, API, fixture, schema, or provider change is
introduced. Human-readable results remain KST; exact UTC input identity retains
all supplied microseconds. The native form needs no custom submit handler, but
the existing Next/React streamed result rendering still requires JavaScript.
This change does not claim full JavaScript-disabled application support.

## Contract custody

Extend the existing exact page transformation and five reviewed source/test
hashes in `navigation_contracts.py`. The inventory remains 21 closed paths.
Six exact ADR-061 committed objects are admitted for pre-commit development;
working bytes must match ADR-062. Mutation tests reject stale working sources,
forged predecessors, automatic opening, clock substitution, example insertion,
stale form keys, and removed focus styles. No broad product exemption, baseline,
workflow, historical body, or source-custody rule is relaxed.

## Verification and next boundary

Page tests cover both locales, both transport presentations, all four views,
exact form entries, one selected read, unchanged KST evidence while editing,
and dirty-input reset after query navigation. Chromium tests cover keyboard
opening, bilingual correction, desktop/mobile layout, and a document GET from a
paginated child view to a new microsecond-preserving summary cutoff.
Actual run results and limitations are recorded in `IMPLEMENTATION_LOG.md`.

No API key, contact email, paid account, domain, server fact, or live request is
needed. PR upload, hosted CI, merge, release, and deployment remain separate
handoff steps. The home-server plan stays deferred until hardware is ready.
