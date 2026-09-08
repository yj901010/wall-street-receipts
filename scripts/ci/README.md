# Size-bounded CI contracts

The entry point is `.github/workflows/ci.yml`. Its repository-contract job calls
small commands here; the web, API, and call-audit integration jobs still run
against the **current checkout**.

## Run the offline checks

Use Python 3.12 or newer (Actions selects 3.13):

```sh
python -m pip install -r scripts/ci/requirements.txt
python scripts/ci/validate_limits.py
python -m unittest discover -s scripts/ci -p 'test_*.py'
python scripts/ci/validate_current_fixtures.py
python scripts/ci/run_contracts.py validate
```

The size guard scans every `.yml`/`.yaml` workflow. It allows at most 500,000
UTF-8 bytes per file and 21,000 decoded Unicode characters per `run`. It rejects
duplicate YAML keys, aliases, and merge keys. It is not a full Actions schema
validator; hosted execution remains required.

## Frozen compatibility bridge (ADR-057)

The previous workflow was 2,438,119 bytes and included 36 oversized `run`
values. Its historical guards also inspect exact workflow bytes and historical
Git trees. Moving their source and running them on the current checkout would
invalidate those self-checks.

`legacy_steps.json` records all 86 original steps, including the 84 extracted
run bodies in `legacy/`. The baseline is the full commit
`3792100f49c496d751d1dd54a7fbdc1b7c2fd275`. Preparation checks the digest-pinned
original workflow, every body, metadata, shell, condition, and execution order.
Line endings are normalized to LF only for script custody comparisons; shell
semantics and original decoded-run hashes remain recorded in the manifest.

This is deliberately a **CI-only bridge, not permission to test stale code**:

- Current product path, mode, object type, and blob identity must equal the
  baseline. Only the explicitly enumerated CI and documentation paths in
  `run_contracts.py` may differ. ADR-058 separately checks one exact test-only
  datasource annotation migration byte-for-byte; it is not a general path
  exemption. Other added, deleted, or modified paths fail.
- An unstaged, generated `apps/web/next-env.d.ts` may remain locally but must not
  be staged and is never copied into the historical checkout. Its exact bytes
  are included in before/after custody checks.
- A complete, independent Git repository is initialized under a fixed owned
  child of `RUNNER_TEMP`, with local-file-only fetching, no persisted remotes,
  no alternates/replacements/shallow history, and strict object verification.
- Historical scripts run there in original order. All seven original
  `always()` restorations remain separate Actions steps. A failed script stops
  normal steps; later restoration steps must still run. Failure cannot turn
  into success because restoration passed.
- The final `always()` step verifies source custody, restored historical HEAD,
  clean historical checkout, all restoration markers, and complete successful
  execution before removing the owned directory. Ownership mismatch or a link
  refuses deletion. Windows read-only Git objects are handled only within that
  verified directory. Interrupted child process trees are stopped before
  recovery. Runner termination/power loss can still prevent final cleanup.

Do not edit extracted guards to bypass failures. Do not broaden the product
allowlist for a feature change. Before the next product change, replace the
affected legacy guards with current-tree contracts and explicit mutation tests,
then deliberately retire/update this frozen bridge. Preserve the original
commit objects; do not squash or rewrite the pinned historical chain.

No provider key, live market request, home-server access, deployment, release
tag, or merge is part of these checks. No `.env` content is read by the bridge.

Full historical execution needs Ubuntu runner tools (Bash, PowerShell, Ruby,
jq, Docker Compose, Git, Python) and installed CI dependencies. The Actions
workflow orchestrates `prepare`, the 84 ordered `run <index>` calls, and
`finish`; do not report only `validate` or a projection probe as a full run.

## Hosted corrections (ADR-058)

Historical step 12 alone receives a verified, temporary owner-execute fixture
on the isolated source-only generation-state module. Its content stays pinned;
the original physical mode and clean checkout must be restored even on failure.
All other historical steps keep their original execution environment.

Step 83 has one explicit in-memory guard migration: its exact digest-pinned
Python body gets a corrected next-step slice boundary so ADR-056 assertion text
cannot be mistaken for ADR-055 restoration code. Every original assertion and
the extracted artifact remain intact; mutation tests still reject violations
inside the actual restoration step. No other source transformation is allowed.

The current API job additionally runs the committed-evidence persistence test
before the three formerly contaminated classes using reverse-alphabetical
ordering. `current_contracts.py` permits only its exact dedicated-H2 annotation
and explanation comment, preserving all other source bytes and assertions.

The current call-audit job invokes `verify_call_audit_access.py`, with mutation
tests for all 13 exact GET/200 requests and the UTC range of the Korean day.
KST display/calendar semantics and UTC storage are unchanged.

## Current-checkout fixture gate (ADR-059)

Before preparing the historical checkout, CI independently validates the current
DEMO revision and outcome documents with `validate_current_fixtures.py`. The CLI
uses its own checkout root, reads no Git or `.env`, makes no network requests,
and does not fall back to historical fixtures. It reports fixture counts, not
observed financial results. Invalid evidence produces a nonzero exit without
dumping the failed document.

The importable `fixture_revisions` and `fixture_outcomes` modules preserve the
substantive historical step-84/85 checks. `fixture_contracts_common` supplies
strict duplicate-key/non-finite rejection, bounded local JSON loading, strict
UTC instants, a closed schema-reference registry, and exact Decimal `multipleOf`
checks. The 38-digit numeric boundary is checked without Decimal-context or
binary-float rounding. Mutation tests corrupt only disposable copies.

This is the first migration slice, not retirement of the frozen bridge. All 84
historical bodies still run, and **no product path is unfrozen**. Other overlapping
legacy contracts must be migrated before a related product change is admitted.

## Exact SEC navigation migration (ADR-060)

The bridge now also verifies `navigation_contracts.py` before the frozen-tree
comparison. Seven runtime files must equal their exact baseline plus the reviewed
navigation edits; nine current unit/E2E test updates are separately content-pinned.
All 16 paths participate in custody. They are not general product exemptions.

The current tests require exactly nine typed navigation IDs, both locale labels,
bare locator URLs, active route states, no automatic evidence selection, and real
keyboard navigation. Unlike the historical lower-case-only extraction, these
checks cannot silently omit `secEvidence`. Existing Vitest and Playwright jobs
continue to execute against the current checkout. Further changes to these files
require another explicit contract review, not a broad path allowlist.

## Exact locator recovery migration (ADR-061)

The cumulative navigation migration now covers 21 paths: seven exact-edited
runtime files, nine current test hashes, three additional locator source hashes,
and two new helper/test hashes. New files must be absent from the baseline and
present with exact reviewed working bytes. Only those two verified new paths may
be untracked before commit; no app path enters `FIXED_CI_PATHS`.

Only the three overlapping ADR-060 HEAD objects are accepted as intermediate
committed versions; working files must always match ADR-061. This recognizes
pre-commit development, not arbitrary old code or staged content. All 21 files
remain in the before/after custody snapshot. Parser/provider and historical
guards remain unchanged. Current tests cover explicit native GET correction,
bounded verbatim inputs, no request on invalid state, bilingual errors, and
clearing edited fields without stale uncontrolled input state.

## Exact result refinement migration (ADR-062)

The same 21-path inventory now admits the exact result-page disclosure and five
updated source/test hashes. Six additional predecessor objects are pinned to
ADR-061 for pre-commit development only; current working bytes remain mandatory.
Mutation tests reject automatic selection, server-clock defaults, stale input
keys, missing focus styling, forged predecessors, and old working files. The
provider/parser and all historical bodies remain unchanged.

Current web tests verify a collapsed, keyboard-operable bilingual editor,
unchanged evidence while editing, and a same-origin native document GET that
discards child pagination and preserves exact UTC keys. The application retains
its existing JavaScript-dependent Next/React result streaming; native GET does
not imply a fully JavaScript-disabled site.

## Exact failed-query recovery migration (ADR-063)

The cumulative inventory is now 23 paths: the same seven exact runtime edits,
nine test hashes, three locator source hashes, and four added helper/test hashes.
Error and not-found boundaries add only the reviewed recovery island. Five exact
ADR-062 predecessor objects are admitted for development, never stale working
bytes. New mutation tests reject duplicate selection, parser bypass, stale keys,
clock defaults, DEMO substitution, verified-evidence copy, and automatic fetch.
The historical baseline, 84 bodies, workflow, and general product exclusions
remain unchanged. Current Vitest/Playwright exercise the new boundary behavior;
the historical checkout is not evidence that this feature works.

## Exact CPI retrieval migration (ADR-064)

`cpi_contracts.py` adds a separate closed inventory of 31 paths: six exact
baseline-file replacements and 25 additions. It pins the collector, parser,
decimal calculation, repository, read API, V10 migration, web route and tests.
No CPI path overlaps the SEC navigation inventory or enters `FIXED_CI_PATHS`.
Current bytes are mandatory even before commit; only exact baseline or reviewed
committed blobs are accepted. Missing/stale/modified/renamed/executable content
cannot become an exemption. All paths participate in custody snapshots.

Current Java and Vitest tests validate missing-month behavior, fixed-origin
bounded transport, conservative 429 backoff, atomic PostgreSQL cooldown/replay,
non-PIT metadata, server-only reads and explicit disabled/empty/error states.
The new browser test preserves the existing market evidence keyboard order.
Synthetic CPI data is test-only; CI does not call BLS or need a BLS key.
