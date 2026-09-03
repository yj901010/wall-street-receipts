# ADR-057: Size-bounded CI with isolated legacy contracts

- Status: Accepted for CI-only catch-up PR verification
- Date: 2026-09-03 (KST)
- Baseline: `3792100f49c496d751d1dd54a7fbdc1b7c2fd275`

## Context

Draft PR #7 integrates the accumulated feature work into `develop`; it is not
merged or released. The existing CI workflow is 2,438,119 bytes, beyond
GitHub's 500 KB workflow limit. Thirty-six `run` scalars also exceed the
21,000-character limit; the largest has 96,535 decoded characters. Syntax
parsing alone did not establish that Actions could start the workflow.

Historical contract guards embed exact historical tree, file, workflow-self,
and projection-custody checks. A mechanical move that ran those bodies on the
new checkout would break legitimate self-checks. Weakening/removing the guards
would lose evidence.

Sources:

- [GitHub workflow file size limit](https://docs.github.com/en/actions/reference/limits#workflow-file-size)
- [GitHub run character limit](https://docs.github.com/en/actions/reference/workflows-and-actions/workflow-syntax#jobsjob_idstepsrun)

## Decision

Extract all 84 repository-contract `run` bodies without changing their parsed
content. Record their source metadata, decoded character counts, and SHA-256
digests in `scripts/ci/legacy_steps.json`. Preserve the seven independent
`always()` restoration conditions and the Bash/PowerShell exit semantics.

Keep the web, API, and call-audit integration jobs semantically unchanged and
running on the current PR checkout. Add a workflow-size regression guard and
offline unit tests before historical preparation.

Execute historical repository guards in a complete, independent, local-only
Git checkout of the pinned baseline, never in the current checkout. Reject
product-tree differences, including additions/deletions and mode/type changes,
outside a closed list of CI/documentation paths. This establishes that the
frozen contracts still cover the unchanged current product in this CI-only PR.
An unstaged generated Next declaration is preserved separately and never
included in the baseline. Do not add feature paths to the exemption list.

Verify original workflow digest, extraction inventory, metadata, and current
workflow job/step parity before preparation and after execution. Pin the small
manifest digest for each individual invocation instead of reparsing a 2.4 MB
workflow 84 times. Record ordered results and propagate failure through all
remaining restorations. Apply bounded child-process execution and terminate
the child process tree on timeout or interruption before restoration.

Own cleanup through an exact `RUNNER_TEMP` child and a 48-hex marker. Reject
links/reparse points and unexpected ownership. Verify source HEAD, symbolic
ref, refs, status, index/config, allowed-source bytes, and the user-owned Next
declaration before deletion. Keep the marker until other children are removed;
Windows read-only Git-object retries stay within the verified owned directory.

## Consequences and boundaries

- The entry-point workflow becomes small enough for Actions to start, while
  preserving the original contract implementation and current app test jobs.
- Extraction is not a claim that the legacy design is maintainable indefinitely.
  The next product change must replace affected historical assertions with
  explicit current-tree contracts and mutation tests before deliberately
  retiring/updating this bridge. The guard fails closed until that work occurs.
- Original historical commit identities remain required. Do not rewrite their
  chain via squash, rebase, or cherry-picking into a disconnected history.
- Pinned PyYAML and jsonschema are CI dependencies only, not product runtime
  services. No paid/live provider, deployment, release, or Git Flow merge is
  authorized by this change.
- Local parsing/unit checks and historical projection probes are not a hosted
  CI pass. PR readiness depends on actual Actions results for this revision.
- A runner crash or forced termination may prevent final cleanup; no claim of
  cleanup after host failure is made. Current-checkout isolation still limits
  the historical mutation scope.

Implementation and verification evidence is recorded in `IMPLEMENTATION_LOG.md`.
