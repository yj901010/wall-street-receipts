python <<'PYTHON'
import hashlib
import json
import os
import re
import shutil
import subprocess
from pathlib import Path

BASE_REVISION = "20d70d73f53668a7f1bf2b5b1d70e4c1e9fbfca2"
WORKFLOW_SELF_DIGEST = "2082b673fd7f4d6eeed3715d18689b0806f61a518f70a5025b6c7c38d05f5fe0"  # ADR056_WORKFLOW_SELF_DIGEST

def require(condition, message):
    if not condition:
        raise ValueError(message)

def raw_digest(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()

def normalized_digest(path):
    return hashlib.sha256(
        path.read_bytes().replace(b"\r\n", b"\n")
    ).hexdigest()

def masked_workflow_digest(path):
    content = path.read_bytes().replace(b"\r\n", b"\n")
    patterns = (
        rb'(?m)^(          WORKFLOW_SELF_DIGEST = ")[0-9a-f]{64}("  # ADR056_WORKFLOW_SELF_DIGEST)$',
        rb'(?m)^(          CUSTODY_MANIFEST_DIGEST = ")[0-9a-f]{64}("  # ADR056_CUSTODY_MANIFEST_DIGEST)$',
    )
    for pattern in patterns:
        content, replacement_count = re.subn(
            pattern,
            rb'\1<ADR056-MASKED-SHA256>\2',
            content,
        )
        require(
            replacement_count == 1,
            "ADR-056 workflow self-hash slot changed",
        )
    return hashlib.sha256(content).hexdigest()

modified_paths = {
    Path(value)
    for value in (
        ".github/workflows/ci.yml",
        "IMPLEMENTATION_LOG.md",
        "README.md",
        "deploy/home-server/README.md",
    )
}
added_paths = {
    Path(value)
    for value in (
        "decisions/ADR-056-disposable-offline-git-flow-release-source-handoff-rehearsal.md",
        "scripts/verify-local-release-handoff.ps1",
    )
}
current_paths = modified_paths | added_paths
require(
    len(modified_paths) == 4
    and len(added_paths) == 2
    and len(current_paths) == 6,
    "ADR-056 source/script/document delta inventory changed",
)
require(
    Path("apps/web/next-env.d.ts") not in current_paths,
    "Generated Next.js declaration entered ADR-056 custody",
)

resolved_revision = subprocess.check_output(
    ["git", "rev-parse", f"{BASE_REVISION}^{{commit}}"],
    text=True,
).strip()
require(
    resolved_revision == BASE_REVISION,
    "ADR-056 base revision is unavailable or changed",
)
delta_lines = subprocess.check_output(
    [
        "git", "diff", "--name-status", "--no-renames",
        BASE_REVISION, "HEAD", "--",
    ],
    text=True,
).splitlines()
actual_delta = {}
for line in delta_lines:
    status, raw_path = line.split("\t", 1)
    actual_delta[Path(raw_path)] = status
expected_delta = {
    **{path: "M" for path in modified_paths},
    **{path: "A" for path in added_paths},
}
require(
    actual_delta == expected_delta,
    "ADR-056 exact 20d70d7-to-current delta inventory changed",
)

workflow_path = Path(".github/workflows/ci.yml")
current_files = {}
for path in current_paths:
    require(path.is_file(), f"ADR-056 current path is missing: {path}")
    if path == workflow_path:
        current_files[path] = {
            "kind": "self",
            "rawSha256": WORKFLOW_SELF_DIGEST,
            "normalizedSha256": None,
        }
    else:
        current_files[path] = {
            "kind": "text",
            "rawSha256": raw_digest(path),
            "normalizedSha256": normalized_digest(path),
        }
require(
    masked_workflow_digest(workflow_path) == WORKFLOW_SELF_DIGEST,
    "ADR-056 workflow self-custody bytes changed",
)

projection_root = (
    Path(os.environ["RUNNER_TEMP"]) / "wsr-adr056-current-view"
)
require(
    not projection_root.exists(),
    f"ADR-056 projection custody already exists: {projection_root}",
)
projection_root.mkdir(parents=True)
for path in current_paths:
    custody_path = projection_root / path
    custody_path.parent.mkdir(parents=True, exist_ok=True)
    shutil.copyfile(path, custody_path)
manifest = {
    path.as_posix(): metadata
    for path, metadata in current_files.items()
}
manifest_bytes = json.dumps(
    manifest,
    sort_keys=True,
).encode("utf-8")
custody_matches = re.findall(
    rb'(?m)^          CUSTODY_MANIFEST_DIGEST = "([0-9a-f]{64})"  # ADR056_CUSTODY_MANIFEST_DIGEST$',
    workflow_path.read_bytes().replace(b"\r\n", b"\n"),
)
require(
    len(custody_matches) == 1
    and hashlib.sha256(manifest_bytes).hexdigest()
    == custody_matches[0].decode("ascii"),
    "ADR-056 current-byte custody manifest changed",
)
(projection_root / "manifest.json").write_bytes(manifest_bytes)

next_env_path = Path("apps/web/next-env.d.ts")
next_env_status_line = " M apps/web/next-env.d.ts"
original_tracked_status = subprocess.check_output(
    ["git", "status", "--porcelain=v1", "--untracked-files=no"],
    text=True,
).splitlines()
require(
    original_tracked_status in ([], [next_env_status_line]),
    "ADR-056 projection requires a clean tracked checkout except the "
    "unstaged user-owned Next declaration",
)
next_env_dirty = original_tracked_status == [next_env_status_line]
next_env_custody_path = (
    projection_root / "excluded-user-owned" / next_env_path
)
next_env_state = {
    "dirty": next_env_dirty,
    "rawSha256": None,
    "normalizedSha256": None,
}
if next_env_dirty:
    require(
        next_env_path.is_file(),
        "User-owned Next declaration is missing before projection",
    )
    next_env_custody_path.parent.mkdir(parents=True, exist_ok=True)
    shutil.copyfile(next_env_path, next_env_custody_path)
    next_env_state = {
        "dirty": True,
        "rawSha256": raw_digest(next_env_path),
        "normalizedSha256": normalized_digest(next_env_path),
    }

original_head = subprocess.check_output(
    ["git", "rev-parse", "HEAD"],
    text=True,
).strip()
symbolic_head = subprocess.run(
    ["git", "symbolic-ref", "-q", "HEAD"],
    text=True,
    capture_output=True,
    check=False,
)
require(
    symbolic_head.returncode in {0, 1},
    "ADR-056 original HEAD state could not be resolved",
)
original_symbolic_ref = (
    symbolic_head.stdout.strip()
    if symbolic_head.returncode == 0
    else None
)
require(
    original_symbolic_ref is None
    or original_symbolic_ref.startswith("refs/heads/"),
    "ADR-056 original symbolic HEAD is not a local branch",
)
(projection_root / "head-state.json").write_text(
    json.dumps(
        {
            "commit": original_head,
            "excludedNextEnv": next_env_state,
            "symbolicRef": original_symbolic_ref,
        },
        sort_keys=True,
    ),
    encoding="utf-8",
)
(projection_root / ".prepared").write_text(
    "ADR-056 exact current bytes saved before historical projection\n",
    encoding="utf-8",
)
subprocess.run(
    ["git", "checkout", "--detach", BASE_REVISION],
    check=True,
)
projected_head = subprocess.check_output(
    ["git", "rev-parse", "HEAD"],
    text=True,
).strip()
projected_symbolic_head = subprocess.run(
    ["git", "symbolic-ref", "-q", "HEAD"],
    capture_output=True,
    check=False,
)
require(
    projected_head == BASE_REVISION
    and projected_symbolic_head.returncode == 1,
    "ADR-056 historical projection did not detach exact 20d70d7 HEAD",
)
require(
    all(not path.exists() for path in added_paths),
    "ADR-056 added file survived historical projection",
)
tracked_status = subprocess.check_output(
    ["git", "status", "--porcelain=v1", "--untracked-files=no"],
    text=True,
).splitlines()
require(
    tracked_status == ([next_env_status_line] if next_env_dirty else []),
    "ADR-056 historical projection is not tracked-clean outside the "
    "preserved user-owned Next declaration",
)
require(
    not next_env_dirty
    or (
        raw_digest(next_env_path) == next_env_state["rawSha256"]
        and normalized_digest(next_env_path)
        == next_env_state["normalizedSha256"]
    ),
    "User-owned Next declaration changed during ADR-056 projection",
)
print(
    "Detached the exact clean pre-ADR-056 20d70d7 repository view "
    "before the ADR-055 historical projection"
)
PYTHON
