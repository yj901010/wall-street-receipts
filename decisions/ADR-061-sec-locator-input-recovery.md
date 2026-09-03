# ADR-061: Exact SEC locator input recovery

## Status and starting point

Accepted for local implementation. PR #8 was merged into `develop` as
`aee500774e5915d6931f6a658e22b69dc11e74c2`. Its PR run #27 and merged run #28
passed all four jobs. Those are predecessor results, not this candidate's CI.
This feature starts from that merged commit on `feature/p5-sec-locator-feedback`.

## Problem

The existing SSR GET form at `/research/sec/filing-history` rejects invalid URL
state correctly, but discards all entered values and shows only a general error.
Native form submission and recovery were not exercised by the existing browser
suite. This slice improves correction without expanding accepted query grammar.

## Decision

- Keep the existing page, native GET form, provider selection, strict query
  parser, exact summary request, and separate explicit synthetic DEMO link.
  No client island, API, dependency, schema, fixture, or provider is introduced.
- The page calls the presentation-only `locatorFeedback` helper with raw values
  only for an invalid route. The untouched locator receives `null` and has empty
  inputs without field errors. This helper cannot authorize a provider request.
- Retain only single strings of at most 64 printable ASCII characters, exactly
  as supplied. Never trim, select an array member, truncate, case-fold, change
  timezone, or round a fraction. Arrays, overlong strings, controls, and other
  unsupported characters are not echoed; their input is empty with a reason.
  Printable ASCII avoids native text-input CR/LF stripping and ambiguous display.
- Reuse the existing manifest and real-calendar UTC validators for field errors.
  Missing, duplicate, unretained, and malformed values receive localized messages,
  `aria-invalid`, and associated `aria-describedby` text. React escapes retained
  strings. Unknown URL fields are never reflected into the form.
- A rejected URL with valid ID/cutoff keeps those inputs without marking them
  invalid. The general error remains. Only an explicit new submission sends
  `manifestId`, `evaluationAsOf`, and `view=summary`; explanatory copy makes clear
  that other old URL settings are not sent. There is no automatic repair/read.
- Clearing the lookup returns to the bare route. The form key changes when
  leaving invalid state so even dirty uncontrolled inputs are remounted empty.
  A real browser regression reproduced stale edited values before this fix.
- Human-readable results remain KST. The explicitly labelled original UTC API
  lookup key and its microseconds remain unchanged. This adds no current-time
  default, latest/company selector, live-data claim, or fixture fallback.

## Current-source verification boundary

Extend the closed ADR-060 migration instead of unfreezing product paths.
Seven runtime sources still have exact baseline-relative edits (the page now
also passes feedback); nine existing test files are content-pinned. Three
additional existing locator sources and two new helper/test files have reviewed
content hashes. All 21 paths participate in source custody.

New files must be absent from the frozen baseline, present as regular unlinked
nonexecutable working files, and have exact reviewed bytes. Only those two
validated paths may also be untracked before commit. Three overlapping ADR-060
HEAD objects are explicitly recognized for local pre-commit validation, while
working bytes must always be ADR-061. This is not an index or arbitrary ancestor
exemption. Parser/provider files remain frozen.

The pinned baseline, historical manifest, 84 original run bodies, seven restore
steps, and workflow/application jobs do not change. Mutation tests reject source
drift, unsafe modes/links, missing new files, forged predecessors, normalization,
provider activation, validation bypass, unrelated untracked files, and custody
changes. Current Vitest/Playwright jobs execute the actual changed product.

## Verification and remaining boundary

Actual local results and any corrected test/UX failures are recorded in
`IMPLEMENTATION_LOG.md`. Hosted CI belongs to a separately authorized PR upload.
No API key, account, domain, server specification, or live request is needed.
Home-server setup, deployment, SEC collection automation, P3 scoring integration,
and the historical screener remain outside this local UX slice.
