# ADR-058: Hosted CI portability and test isolation

- Status: Accepted
- Date: 2026-09-03 (KST)
- Scope: PR #7 verification; user-authorized merge into `develop` only after CI passes

## Evidence

Hosted [run #23](https://github.com/yj901010/wall-street-receipts/actions/runs/33715969990)
at `a634993` proved the workflow-size fix and all 71 CI unit tests. Web lint,
643 unit tests, build, 78 desktop/mobile E2E tests, and one SEC API-failure
boundary passed. Three previously unexecuted hosted boundaries failed:

1. Historical generation-control test step 12 directly invoked a source-only
   file committed as `100644`. Linux returned 126 before its exit-64 guard.
2. A nontransactional H2 persistence test committed 7 catalog roots, 4 segments,
   and 3 manifests into the fixed shared `wsr-test` database. Later transactional
   test classes saw these rows, producing 11 count failures among 2,404 tests.
3. The browser integration's three tests passed, but the access-log guard still
   expected UTC midnight instead of the UTC bounds of a Korean calendar day.

## Decisions

### Preserve the historical tests with one explicit execution fixture

`legacy_environment.py` gives only historical step 12 a temporary owner-execute
bit on the exact `deploy/home-server/generation-state.sh` in the isolated
checkout. Verify the pinned HEAD, path, blob, Git mode and cleanliness before
execution; restore the original physical mode and verify unchanged bytes and
clean Git state afterward, including on failure. Windows needs no execute-bit
change. No extracted test body/hash changes, blanket chmod, `core.fileMode`
override, or acceptance of exit 126 is permitted.

### Isolate committed test evidence without weakening transaction semantics

Only `SecFilingHistoryCollectionAttemptPersistenceTest` gets a dedicated named
H2 database through its `@SpringBootTest` datasource override. It stays
nontransactional so cross-thread claims see committed evidence and rollback/
durability tests retain their meaning. No assertion is relaxed; no production
repository or database-deletion API changes.

`current_contracts.py` explicitly migrates this one current-tree test contract:
every byte other than the exact reviewed annotation/comment must still equal
the pinned baseline. Committed path/mode/type/blob and working bytes are
checked. The path is **not** admitted to the broad CI/documentation exemptions.
Mutation tests reject old shared-DB configuration, transactional shortcuts,
extra edits, deletion, changed modes/types, and unrelated product changes.
Current Maven verification runs this test, and an additional reverse-alphabetic
offender-before-victims gate proves all four affected classes together.

### Keep KST product behavior; correct the current integration expectation

`verify_call_audit_access.py` retains the 13 required exact successful Spring
requests. Only the filtered Korean day changes: `2026-08-11` means the UTC
half-open interval `2026-08-10T15:00:00.000Z` to `2026-08-11T15:00:00.000Z`.
Tests reject the former UTC-midnight range, missing endpoints, altered filters,
and non-200 responses as substitutes. Source UTC storage is unchanged.

Current workflow parity now permits exactly the replacement access-log command
and the added persistence-isolation Maven gate. Other current app-job commands,
environments, shells, conditions, and all historical step ordering remain
checked against the pinned workflow.

### Bound the ADR-055 restoration guard to its actual step

Hosted run #24 passed Web, API (2,404 tests with zero skips plus all focused
gates), and call-audit integration. It then exposed a pre-existing final
historical guard defect: step 83 sliced the ADR-055 restoration text through
the later ADR-055 guard marker. That range also contained newly nested ADR-056
steps, whose assertion text mentioned the forbidden `"--force"` token. Actual
ADR-055 restoration and all seven always-restores had already passed.

`historical_guard_migrations.py` verifies the exact digest-pinned original
step-83 heredoc, then replaces exactly one slice endpoint with the next workflow
step boundary in memory. All other source bytes and assertions remain intact;
the original extracted artifact and manifest are unchanged. Execute that one
Python body directly with identical failure propagation, still against the
isolated pinned checkout. Mutation tests require actual restoration violations
to fail and reject any unreviewed original body. This explicit check migration
does not bypass the guard or erase the historical evidence.

## Consequences

No product route, financial value, live provider, server setting, or production
runtime behavior changes. API keys/home-server access are unnecessary. These
are explicit test-contract migrations, not a general permission for future
product changes to run against stale historical tests.

Keep historical commit identities. PR #7 must merge with a merge commit, not
squash/rebase. The authorized target is `develop`; `main`, release tags and
actual deployment remain untouched. Hosted results are recorded in the PR and
`IMPLEMENTATION_LOG.md`; local Docker-dependent skips are never counted as
hosted passes.
