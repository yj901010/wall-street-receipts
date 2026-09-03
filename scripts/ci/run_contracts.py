#!/usr/bin/env python3
"""Run frozen pre-refactor contracts without mutating the current checkout.

This compatibility bridge deliberately rejects product changes. It is not a
substitute for adding current-tree contracts when the product next changes.
"""
from __future__ import annotations

import argparse
import copy
import hashlib
import json
import os
from pathlib import Path
import re
import secrets
import shutil
import signal
import stat
import subprocess
import sys

import yaml

from validate_limits import load_workflow
from current_contracts import TEST_PATH, verify_current_test
from navigation_contracts import NAVIGATION_PATHS, verify_navigation
from legacy_environment import legacy_step_environment
from historical_guard_migrations import migrated_python_body

BASELINE = "3792100f49c496d751d1dd54a7fbdc1b7c2fd275"
BASELINE_WORKFLOW_SHA256 = "ff079516f9524d19c51ea216d78489ba11b45eba81056d4c86ff9085fa415f20"
MANIFEST_SHA256 = "ab15ea44c90f5c41da68d181256208a38866aedabe91075f4321b95f3921f606"
WORKFLOW = ".github/workflows/ci.yml"
MANIFEST = "scripts/ci/legacy_steps.json"
JOB = "repository-contracts"
NEXT_ENV = "apps/web/next-env.d.ts"
OWNED_NAME = "wsr-ci-contracts-v1"
FIXED_CI_PATHS = frozenset({
    WORKFLOW, MANIFEST, "scripts/ci/run_contracts.py",
    "scripts/ci/test_contracts.py", "scripts/ci/validate_limits.py",
    "scripts/ci/test_limits.py", "scripts/ci/requirements.txt",
    "scripts/ci/README.md", "IMPLEMENTATION_LOG.md",
    "decisions/ADR-057-size-bounded-ci-with-isolated-legacy-contracts.md",
    "decisions/ADR-058-hosted-ci-portability-and-test-isolation.md",
    "scripts/ci/current_contracts.py", "scripts/ci/test_current_contracts.py",
    "scripts/ci/legacy_environment.py", "scripts/ci/test_legacy_environment.py",
    "scripts/ci/verify_call_audit_access.py", "scripts/ci/test_call_audit_access.py",
    "scripts/ci/historical_guard_migrations.py", "scripts/ci/test_historical_guard_migrations.py",
    "scripts/ci/fixture_contracts_common.py", "scripts/ci/test_fixture_contracts_common.py",
    "scripts/ci/fixture_revisions.py", "scripts/ci/test_fixture_revisions.py",
    "scripts/ci/fixture_outcomes.py", "scripts/ci/test_fixture_outcomes.py",
    "scripts/ci/validate_current_fixtures.py", "scripts/ci/test_current_fixtures.py",
    "decisions/ADR-059-current-checkout-demo-fixture-contracts.md",
    "scripts/ci/navigation_contracts.py", "scripts/ci/test_navigation_contracts.py",
    "decisions/ADR-060-sec-evidence-navigation.md",
})


def require(condition, message):
    if not condition:
        raise ValueError(message)


def digest(data):
    return hashlib.sha256(data).hexdigest()


def canonical_run(value):
    value = value.replace("\r\n", "\n")
    return (value if value.endswith("\n") else value + "\n").encode("utf-8")


class TypedWorkflowLoader(yaml.SafeLoader):
    pass


TypedWorkflowLoader.yaml_implicit_resolvers = copy.deepcopy(yaml.SafeLoader.yaml_implicit_resolvers)
for key, resolvers in TypedWorkflowLoader.yaml_implicit_resolvers.items():
    TypedWorkflowLoader.yaml_implicit_resolvers[key] = [
        value for value in resolvers if value[0] != "tag:yaml.org,2002:bool"
    ]
TypedWorkflowLoader.add_implicit_resolver(
    "tag:yaml.org,2002:bool", re.compile(r"^(?:true|false)$", re.I), list("tTfF")
)


def parse_workflow(raw):
    text = raw.decode("utf-8")
    load_workflow(text, source="workflow parity input")
    return yaml.load(text, Loader=TypedWorkflowLoader)


def git_environment():
    env = {key: value for key, value in os.environ.items()
           if not re.match(r"(?i)^(GIT|GITHUB|GH|GCM|SSH|GITLAB|BITBUCKET)_|^(HTTP|HTTPS|ALL|NO)_PROXY$", key)}
    env.update({"GIT_CONFIG_NOSYSTEM": "1", "GIT_CONFIG_GLOBAL": os.devnull,
                "GIT_TERMINAL_PROMPT": "0", "GIT_ASKPASS": os.devnull,
                "GIT_ALLOW_PROTOCOL": "file", "GIT_NO_LAZY_FETCH": "1",
                "GIT_OPTIONAL_LOCKS": "0", "LC_ALL": "C", "LANG": "C"})
    return env


def git(root, *args, codes=(0,)):
    command = ["git", "-c", "core.fsmonitor=false", "-c", f"core.hooksPath={os.devnull}",
               "-c", "gc.auto=0", "-c", "maintenance.auto=0", "-c", "credential.helper=",
               "-c", "protocol.file.allow=always", *args]
    result = subprocess.run(command, cwd=root, env=git_environment(), capture_output=True, timeout=120)
    require(result.returncode in codes, f"Local git {args[0]} failed ({result.returncode})")
    require(len(result.stdout) <= 8_000_000 and len(result.stderr) <= 1_000_000,
            "Local Git output exceeded the accepted output limit")
    return result.stdout


def baseline_workflow(root):
    raw = git(root, "show", f"{BASELINE}:{WORKFLOW}")
    require(digest(raw) == BASELINE_WORKFLOW_SHA256, "Pinned historical workflow bytes changed")
    return parse_workflow(raw)


def script_path(index, step):
    slug = re.sub(r"[^a-z0-9]+", "-", step["name"].lower()).strip("-")[:70].rstrip("-")
    suffix = "ps1" if step.get("shell") == "pwsh" else "sh"
    return f"scripts/ci/legacy/{index:03d}-{slug}.{suffix}"


def expected_manifest(baseline):
    job = baseline["jobs"][JOB]
    entries = []
    for index, step in enumerate(job["steps"]):
        entry = {"index": index, **{key: value for key, value in step.items() if key != "run"}}
        if "run" in step:
            require(step.get("shell") in (None, "bash", "pwsh"), "Unexpected historical shell")
            require(step.get("if") in (None, "always()"), "Unexpected historical condition")
            require("env" not in step and "working-directory" not in step, "Unexpected historical step environment")
            entry.update(script=script_path(index, step), sha256=digest(canonical_run(step["run"])),
                         originalRunSha256=digest(step["run"].encode("utf-8")), characters=len(step["run"]))
        entries.append(entry)
    return {"schemaVersion": 1, "baselineCommit": BASELINE,
            "baselineWorkflowSha256": BASELINE_WORKFLOW_SHA256, "jobId": JOB,
            "jobMetadata": {key: value for key, value in job.items() if key != "steps"}, "steps": entries}


def expected_workflow(baseline):
    result = copy.deepcopy(baseline)
    original = baseline["jobs"][JOB]["steps"]
    require([index for index, step in enumerate(original) if "uses" in step] == [0, 2],
            "Historical setup actions changed")
    steps = [copy.deepcopy(original[0]), copy.deepcopy(original[2]),
             {"name": "Install CI validation dependencies",
              "run": "python -m pip install --disable-pip-version-check -r scripts/ci/requirements.txt"},
             {"name": "Validate CI platform limits and refactor tests",
              "run": "python scripts/ci/validate_limits.py\npython -m unittest discover -s scripts/ci -p 'test_*.py'\n"},
             {"name": "Validate current-checkout DEMO revision and outcome contracts",
              "run": "python scripts/ci/validate_current_fixtures.py"},
             {"name": "Prepare isolated historical contract checkout",
              "run": "python scripts/ci/run_contracts.py prepare"}]
    for index, original_step in enumerate(original):
        if "run" not in original_step:
            continue
        step = {key: value for key, value in original_step.items() if key != "run"}
        step["run"] = f"python scripts/ci/run_contracts.py run {index}"
        steps.append(step)
    steps.append({"name": "Verify custody and remove isolated contract checkout", "if": "always()",
                  "run": "python scripts/ci/run_contracts.py finish"})
    result["jobs"][JOB]["steps"] = steps
    access_steps = [step for step in result["jobs"]["call-audit-integration"]["steps"]
                    if step.get("name") == "Verify Next requested the real list and all audit resources"]
    require(len(access_steps) == 1, "Historical integration access step changed")
    access_steps[0]["run"] = "python scripts/ci/verify_call_audit_access.py"
    api_steps = result["jobs"]["api"]["steps"]
    verify_indexes = [index for index, step in enumerate(api_steps) if step.get("name") == "Verify"]
    require(len(verify_indexes) == 1, "Historical API verify step changed")
    api_steps.insert(verify_indexes[0] + 1, {
        "name": "Verify SEC persistence test isolation",
        "working-directory": "apps/api",
        "run": "./mvnw -B -ntp -Dtest=SecFilingHistoryCollectionAttemptPersistenceTest,HistoricalFilingSegmentCapturePersistenceTest,FilingHistoryCollectionManifestPersistenceTest,FilingCatalogCapturePersistenceTest -Dsurefire.runOrder=reversealphabetical test",
    })
    return result


def validate_extraction(root, baseline):
    expected = expected_manifest(baseline)
    actual = json.loads((root / MANIFEST).read_text(encoding="utf-8"))
    require(actual == expected, "Extracted step metadata/order differs from the pinned workflow")
    paths = set()
    for entry in expected["steps"]:
        if "script" not in entry:
            continue
        path = root / entry["script"]
        require(path.is_file() and not path.is_symlink(), "Extracted script missing or linked")
        require(digest(path.read_bytes().replace(b"\r\n", b"\n")) == entry["sha256"],
                f"Extracted run body changed: {entry['script']}")
        paths.add(entry["script"])
    actual_paths = {path.relative_to(root).as_posix() for path in (root / "scripts/ci/legacy").iterdir()}
    require(actual_paths == paths, "Unexpected or missing extracted script")
    return expected


def pinned_manifest(root):
    raw = (root / MANIFEST).read_bytes().replace(b"\r\n", b"\n")
    require(digest(raw) == MANIFEST_SHA256, "Pinned contract manifest changed")
    return json.loads(raw)


def validate_workflow_parity(root, baseline):
    current = parse_workflow((root / WORKFLOW).read_bytes())
    require(current == expected_workflow(baseline),
            "CI metadata, current app jobs, historical step order/shell/conditions changed")


def tree_records(raw):
    result = {}
    for record in raw.split(b"\0"):
        if not record:
            continue
        metadata, path = record.split(b"\t", 1)
        result[path.decode("utf-8")] = metadata.decode("ascii")
    return result


def compare_product_trees(baseline, current, allowed):
    changed = {path for path in baseline.keys() | current.keys() if baseline.get(path) != current.get(path)}
    require(changed <= allowed, "Product tree differs from frozen contracts: " + ", ".join(sorted(changed - allowed)))


def permitted_paths(manifest):
    return FIXED_CI_PATHS | {entry["script"] for entry in manifest["steps"] if "script" in entry}


def validate_product(root, manifest):
    allowed = permitted_paths(manifest)
    baseline = tree_records(git(root, "ls-tree", "-rz", BASELINE))
    current = tree_records(git(root, "ls-tree", "-rz", "HEAD"))
    adjusted = verify_current_test(root, git, baseline, current)
    adjusted = verify_navigation(root, git, adjusted, current)
    compare_product_trees(adjusted, current, allowed)
    changed = set(filter(None, git(root, "diff", "--name-only", "-z", "HEAD").decode().split("\0")))
    untracked = set(filter(None, git(root, "ls-files", "--others", "--exclude-standard", "-z").decode().split("\0")))
    require(changed <= allowed | {NEXT_ENV, TEST_PATH} | NAVIGATION_PATHS and untracked <= allowed,
            "Unexpected uncommitted product or untracked file")
    require(not git(root, "diff", "--cached", "--name-only", "--", NEXT_ENV),
            "User-owned Next declaration must not be staged")


def snapshot(root, manifest):
    files = {}
    for relative in sorted(permitted_paths(manifest) | {NEXT_ENV, TEST_PATH} | NAVIGATION_PATHS):
        path = root / relative
        require(not path.is_symlink(), "Source custody path must not be linked")
        files[relative] = digest(path.read_bytes()) if path.is_file() else None
    metadata = {}
    for name in ("index", "config"):
        path = Path(git(root, "rev-parse", "--path-format=absolute", "--git-path", name).decode().strip())
        metadata[name] = digest(path.read_bytes()) if path.is_file() else None
    return {"head": git(root, "rev-parse", "HEAD").decode().strip(),
            "symbolic": git(root, "symbolic-ref", "-q", "HEAD", codes=(0, 1)).decode(),
            "status": git(root, "status", "--porcelain=v1", "--untracked-files=all").decode(),
            "refs": git(root, "for-each-ref", "--format=%(refname) %(objectname)",
                        "refs/heads", "refs/remotes", "refs/tags", "refs/replace").decode(),
            "metadata": metadata, "files": files}


def require_complete(root):
    require(git(root, "rev-parse", "--is-shallow-repository").strip() == b"false", "Shallow repository rejected")
    forbidden = git(root, "config", "--local", "--get-regexp",
                    r"^(extensions\.partialclone|remote\..*\.(promisor|partialclonefilter)|core\.fsmonitor|uploadpack\.packobjectshook|filter\..*\.(clean|smudge|process))$",
                    codes=(0, 1))
    require(not forbidden, "Unsupported partial repository or executable local Git configuration")
    for name in ("objects/info/alternates", "objects/info/http-alternates", "info/grafts"):
        path = Path(git(root, "rev-parse", "--path-format=absolute", "--git-path", name).decode().strip())
        require(not path.exists(), "Alternate or graft repository rejected")
    require(not git(root, "for-each-ref", "--format=%(refname)", "refs/replace"), "Replacement refs rejected")
    git(root, "fsck", "--full", "--strict", "--no-dangling")


def owned_root():
    parent = Path(os.environ["RUNNER_TEMP"]).resolve(strict=True)
    require(parent.is_dir(), "RUNNER_TEMP is not a directory")
    return parent / OWNED_NAME


def assert_owned(root, token):
    require(root.name == OWNED_NAME and root.parent == Path(os.environ["RUNNER_TEMP"]).resolve(strict=True),
            "Owned root escaped RUNNER_TEMP")
    require(re.fullmatch(r"[0-9a-f]{48}", token) is not None, "Invalid owner token")
    require(root.is_dir() and not root.is_symlink(), "Owned root missing or linked")
    marker = root / ".owner"
    require(marker.is_file() and not marker.is_symlink() and marker.read_text() == token, "Owner marker mismatch")
    for current, directories, files in os.walk(root, followlinks=False):
        for name in directories + files:
            path = Path(current) / name
            info = path.lstat()
            require(not path.is_symlink() and not (getattr(info, "st_file_attributes", 0) & getattr(stat, "FILE_ATTRIBUTE_REPARSE_POINT", 0)),
                    "Unexpected link/reparse point in owned checkout")


def remove_owned(root, token):
    assert_owned(root, token)

    def retry_readonly(function, path, error):
        target = Path(path).resolve(strict=True)
        require(target.is_relative_to(root.resolve()) and not Path(path).is_symlink(),
                "Read-only cleanup target escaped owned root")
        if not isinstance(error, PermissionError):
            raise error
        target.chmod(stat.S_IWRITE | stat.S_IREAD)
        function(path)

    # Keep the ownership proof until all other children have been removed.
    for child in root.iterdir():
        if child.name == ".owner":
            continue
        if child.is_dir():
            shutil.rmtree(child, onexc=retry_readonly)
        else:
            child.unlink()
    (root / ".owner").unlink()
    root.rmdir()


def write_state(root, state):
    temporary = root / "state.pending"
    temporary.write_text(json.dumps(state, sort_keys=True), encoding="utf-8")
    temporary.replace(root / "state.json")


def read_state(root, source):
    require(not (root / "state.json").is_symlink(), "State path is linked")
    state = json.loads((root / "state.json").read_text(encoding="utf-8"))
    require(set(state) == {"version", "source", "baseline", "token", "snapshot", "results", "failed"}, "Invalid state fields")
    require(state["version"] == 1 and state["baseline"] == BASELINE and state["source"] == str(source), "State identity mismatch")
    assert_owned(root, state["token"])
    return state


def validate_source(root):
    baseline = baseline_workflow(root)
    manifest = validate_extraction(root, baseline)
    validate_workflow_parity(root, baseline)
    validate_product(root, manifest)
    return manifest


def prepare(source):
    manifest = validate_source(source)
    require_complete(source)
    before = snapshot(source, manifest)
    root = owned_root()
    require(not root.exists(), "Refusing to adopt an existing contract checkout")
    token = secrets.token_hex(24)
    root.mkdir()
    (root / ".owner").write_text(token)
    try:
        legacy = root / "legacy"
        (root / "runner-temp").mkdir()
        git(root, "init", str(legacy))
        git(legacy, "fetch", "--no-tags", "--no-write-fetch-head", "--no-recurse-submodules", str(source), BASELINE)
        git(legacy, "checkout", "--detach", BASELINE)
        require_complete(legacy)
        require(not git(legacy, "remote"), "Isolated checkout must have no remote")
        require(snapshot(source, manifest) == before, "Source changed during preparation")
        write_state(root, {"version": 1, "source": str(source), "baseline": BASELINE, "token": token,
                           "snapshot": before, "results": [], "failed": False})
    except BaseException:
        remove_owned(root, token)
        raise
    print("Prepared independent, complete historical checkout; current product tree matches pinned baseline.")


def next_allowed_step(entries, results, failed, index):
    pending = [entry for entry in entries if "script" in entry and entry["index"] > (results[-1]["index"] if results else -1)]
    if failed:
        pending = [entry for entry in pending if entry.get("if") == "always()"]
    require(bool(pending) and pending[0]["index"] == index, "Historical step skipped, repeated, reordered, or ran after failure")
    return pending[0]


def shell_command(entry, path):
    if entry.get("shell") == "pwsh":
        quoted = str(path).replace("'", "''")
        return ["pwsh", "-NoLogo", "-NoProfile", "-NonInteractive", "-Command",
                f"$ErrorActionPreference = 'Stop'; . '{quoted}'; if (Test-Path variable:LASTEXITCODE) {{ exit $LASTEXITCODE }}"]
    if entry.get("shell") == "bash":
        return ["bash", "--noprofile", "--norc", "-e", "-o", "pipefail", str(path)]
    return ["bash", "--noprofile", "--norc", "-e", str(path)]


def stop_process_tree(process):
    if os.name == "nt":
        subprocess.run(["taskkill", "/PID", str(process.pid), "/T", "/F"], capture_output=True, timeout=10)
    else:
        try:
            os.killpg(process.pid, signal.SIGKILL)
        except ProcessLookupError:
            pass
    process.wait(timeout=10)


def execute(command, cwd, env):
    options = {"start_new_session": True} if os.name != "nt" else {"creationflags": subprocess.CREATE_NEW_PROCESS_GROUP}
    process = subprocess.Popen(command, cwd=cwd, env=env, **options)
    try:
        return process.wait(timeout=120)
    except subprocess.TimeoutExpired:
        stop_process_tree(process)
        return 124
    except BaseException:
        stop_process_tree(process)
        raise


def run_step(source, index):
    root = owned_root()
    manifest = pinned_manifest(source)
    if not root.exists():
        require(any(entry["index"] == index and entry.get("if") == "always()" for entry in manifest["steps"]),
                "Contract checkout was not prepared")
        print("Preparation did not complete; no historical custody to restore.")
        return 0
    state = read_state(root, source)
    entry = next_allowed_step(manifest["steps"], state["results"], state["failed"], index)
    print(f"Historical step {index}: {entry['name']}", flush=True)
    try:
        path = source / entry["script"]
        require(not path.is_symlink() and digest(path.read_bytes().replace(b"\r\n", b"\n")) == entry["sha256"],
                "Run body changed after preparation")
        env = git_environment()
        env["RUNNER_TEMP"] = str(root / "runner-temp")
        # Historical Python guards use 'python'; select the exact setup-python runtime.
        env["PATH"] = str(Path(sys.executable).parent) + os.pathsep + env.get("PATH", "")
        migrated = migrated_python_body(index, path.read_bytes())
        command = [sys.executable, "-c", migrated] if migrated is not None else shell_command(entry, path)
        with legacy_step_environment(index, root / "legacy", git):
            code = execute(command, root / "legacy", env)
    except BaseException:
        state["results"].append({"index": index, "code": 125})
        state["failed"] = True
        write_state(root, state)
        raise
    state["results"].append({"index": index, "code": code})
    state["failed"] = state["failed"] or code != 0
    write_state(root, state)
    return code


def assert_complete_run(manifest, state):
    observed = [result["index"] for result in state["results"]]
    expected = [entry["index"] for entry in manifest["steps"] if "script" in entry]
    restores = [entry["index"] for entry in manifest["steps"] if entry.get("if") == "always()"]
    require(set(restores) <= set(observed), "A mandatory always-restore step was not executed")
    require(not state["failed"] and all(result["code"] == 0 for result in state["results"]), "Historical contract execution failed")
    require(observed == expected, "A historical run step was skipped")


def require_restored_projections(directory):
    expected = {f"wsr-adr{number}-current-view" for number in ("045", "048", "052", "053", "054", "055", "056")}
    require({path.name for path in directory.iterdir()} == expected, "Historical projection inventory changed")
    for name in expected:
        projection = directory / name
        require((projection / ".restored").is_file() and not (projection / ".prepared").exists(),
                f"Historical projection not restored: {name}")


def finish(source):
    root = owned_root()
    if not root.exists():
        print("No owned historical checkout remains.")
        return
    state = read_state(root, source)
    try:
        manifest = validate_source(source)
        require(snapshot(source, manifest) == state["snapshot"], "Current source custody changed")
        legacy = root / "legacy"
        require(git(legacy, "rev-parse", "HEAD").decode().strip() == BASELINE, "Historical HEAD was not restored")
        require(not git(legacy, "status", "--porcelain=v1", "--untracked-files=all"), "Historical checkout is not clean")
        assert_complete_run(manifest, state)
        require_restored_projections(root / "runner-temp")
    finally:
        remove_owned(root, state["token"])
    print("All 84 historical run steps passed; current source unchanged; owned checkout removed.")


def main(argv=None):
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("action", choices=("validate", "prepare", "run", "finish"))
    parser.add_argument("index", nargs="?", type=int)
    args = parser.parse_args(argv)
    source = Path(__file__).resolve().parents[2]
    require((args.action == "run") == (args.index is not None), "Only run accepts a step index")
    if args.action == "validate":
        validate_source(source)
        print("Current workflow, extracted contracts, and baseline product-tree parity passed.")
    elif args.action == "prepare":
        prepare(source)
    elif args.action == "run":
        return run_step(source, args.index)
    else:
        finish(source)
    return 0


if __name__ == "__main__":
    def interrupted(signum, frame):
        raise KeyboardInterrupt(f"Contract runner interrupted ({signum})")

    signal.signal(signal.SIGTERM, interrupted)
    try:
        sys.exit(main())
    except (ValueError, OSError, subprocess.SubprocessError) as error:
        print(f"CI contract bridge failed: {error}", file=sys.stderr)
        sys.exit(1)
