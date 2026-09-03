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
  `run_contracts.py` may differ. Other added, deleted, or modified paths fail.
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
