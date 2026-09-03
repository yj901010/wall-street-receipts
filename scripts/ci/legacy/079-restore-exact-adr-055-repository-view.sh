python <<'PYTHON'
import hashlib
import json
import os
import re
import shutil
import subprocess
from pathlib import Path

BASE_REVISION = "a121eb143dfaea4ac4fb021e41d6904d1bb5300e"
CUSTODY_MANIFEST_DIGEST = "553e30a9bfddf5879b17220837a2f925ce13559d7ad985972b1ef104ffcc697e"  # ADR055_CUSTODY_MANIFEST_DIGEST

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
        rb'(?m)^(          WORKFLOW_SELF_DIGEST = ")[0-9a-f]{64}("  # ADR055_WORKFLOW_SELF_DIGEST)$',
        rb'(?m)^(          CUSTODY_MANIFEST_DIGEST = ")[0-9a-f]{64}("  # ADR055_CUSTODY_MANIFEST_DIGEST)$',
    )
    for pattern in patterns:
        content, replacement_count = re.subn(
            pattern,
            rb'\1<ADR055-MASKED-SHA256>\2',
            content,
        )
        require(
            replacement_count == 1,
            "ADR-055 workflow self-hash slot changed",
        )
    return hashlib.sha256(content).hexdigest()

projection_root = (
    Path(os.environ["RUNNER_TEMP"]) / "wsr-adr055-current-view"
)
prepared = projection_root / ".prepared"
if not prepared.is_file():
    print("ADR-055 historical projection did not mutate the repository")
    raise SystemExit(0)
require(
    prepared.read_text(encoding="utf-8")
    == "ADR-055 exact current bytes saved before historical projection\n",
    "ADR-055 projection custody marker changed",
)
head_state_path = projection_root / "head-state.json"
require(
    head_state_path.is_file(),
    "ADR-055 original HEAD custody is missing",
)
head_state = json.loads(head_state_path.read_text(encoding="utf-8"))
require(
    isinstance(head_state, dict)
    and set(head_state) == {
        "commit", "excludedNextEnv", "symbolicRef"
    }
    and isinstance(head_state["commit"], str)
    and re.fullmatch(r"[0-9a-f]{40}", head_state["commit"])
    and isinstance(head_state["excludedNextEnv"], dict)
    and set(head_state["excludedNextEnv"]) == {
        "dirty", "rawSha256", "normalizedSha256"
    }
    and isinstance(head_state["excludedNextEnv"]["dirty"], bool)
    and (
        head_state["symbolicRef"] is None
        or (
            isinstance(head_state["symbolicRef"], str)
            and head_state["symbolicRef"].startswith("refs/heads/")
        )
    ),
    "ADR-055 original HEAD custody is invalid",
)
original_head = head_state["commit"]
original_symbolic_ref = head_state["symbolicRef"]
next_env_path = Path("apps/web/next-env.d.ts")
next_env_status_line = " M apps/web/next-env.d.ts"
next_env_state = head_state["excludedNextEnv"]
next_env_dirty = next_env_state["dirty"]
require(
    (
        next_env_dirty
        and isinstance(next_env_state["rawSha256"], str)
        and re.fullmatch(
            r"[0-9a-f]{64}", next_env_state["rawSha256"]
        )
        and isinstance(next_env_state["normalizedSha256"], str)
        and re.fullmatch(
            r"[0-9a-f]{64}", next_env_state["normalizedSha256"]
        )
    )
    or (
        not next_env_dirty
        and next_env_state["rawSha256"] is None
        and next_env_state["normalizedSha256"] is None
    ),
    "ADR-055 excluded Next declaration custody is invalid",
)
next_env_custody_path = (
    projection_root / "excluded-user-owned" / next_env_path
)
if next_env_dirty:
    require(
        next_env_custody_path.is_file()
        and raw_digest(next_env_custody_path)
        == next_env_state["rawSha256"]
        and normalized_digest(next_env_custody_path)
        == next_env_state["normalizedSha256"],
        "User-owned Next declaration custody changed",
    )
else:
    require(
        not next_env_custody_path.exists(),
        "Unexpected user-owned Next declaration custody exists",
    )
manifest_path = projection_root / "manifest.json"
require(
    manifest_path.is_file(),
    "ADR-055 current-byte custody manifest is missing",
)
manifest_bytes = manifest_path.read_bytes()
require(
    hashlib.sha256(manifest_bytes).hexdigest()
    == CUSTODY_MANIFEST_DIGEST,
    "ADR-055 current-byte custody manifest changed",
)
raw_manifest = json.loads(manifest_bytes)
require(
    isinstance(raw_manifest, dict) and len(raw_manifest) == 18,
    "ADR-055 current-byte custody inventory changed",
)
current_files = {
    Path(raw_path): metadata
    for raw_path, metadata in raw_manifest.items()
}
workflow_path = Path(".github/workflows/ci.yml")
added_paths = {
    Path(value)
    for value in (
        "apps/api/src/test/java/com/wallstreetreceipts/api/acceptance/SecManifestAuditAcceptanceSeedHarness.java",
        "apps/api/src/test/java/com/wallstreetreceipts/api/support/SecManifestAuditDemoFixture.java",
        "decisions/ADR-055-disposable-offline-sec-manifest-audit-api-mode-full-stack-acceptance.md",
    )
}
modified_paths = set(current_files) - added_paths
require(
    len(modified_paths) == 15
    and workflow_path in modified_paths
    and added_paths <= set(current_files),
    "ADR-055 modified/added custody partition changed",
)
require(
    Path("apps/web/next-env.d.ts") not in current_files,
    "Generated Next.js declaration entered ADR-055 custody",
)

for path, metadata in current_files.items():
    require(
        isinstance(metadata, dict)
        and set(metadata) == {
            "kind", "rawSha256", "normalizedSha256"
        }
        and metadata["kind"] in {"text", "self"}
        and isinstance(metadata["rawSha256"], str)
        and len(metadata["rawSha256"]) == 64,
        f"ADR-055 custody metadata is invalid: {path}",
    )
    if metadata["kind"] == "self":
        require(
            path == workflow_path
            and metadata["normalizedSha256"] is None,
            f"ADR-055 workflow self-custody metadata changed: {path}",
        )
    else:
        require(
            path != workflow_path
            and isinstance(metadata["normalizedSha256"], str)
            and len(metadata["normalizedSha256"]) == 64,
            f"ADR-055 text custody hash is invalid: {path}",
        )
    custody_path = projection_root / path
    require(
        custody_path.is_file(),
        f"ADR-055 custody is missing: {path}",
    )
    if metadata["kind"] == "self":
        require(
            masked_workflow_digest(custody_path)
            == metadata["rawSha256"],
            "ADR-055 workflow self-custody bytes changed",
        )
    else:
        require(
            raw_digest(custody_path) == metadata["rawSha256"]
            and normalized_digest(custody_path)
            == metadata["normalizedSha256"],
            f"ADR-055 current-byte custody changed: {path}",
        )

resolved_revision = subprocess.check_output(
    ["git", "rev-parse", f"{BASE_REVISION}^{{commit}}"],
    text=True,
).strip()
require(
    resolved_revision == BASE_REVISION,
    "ADR-055 base revision is unavailable or changed",
)
projection_errors = []
observed_head = subprocess.check_output(
    ["git", "rev-parse", "HEAD"],
    text=True,
).strip()
observed_symbolic_head = subprocess.run(
    ["git", "symbolic-ref", "-q", "HEAD"],
    text=True,
    capture_output=True,
    check=False,
)
require(
    observed_symbolic_head.returncode in {0, 1},
    "ADR-055 projected HEAD state could not be resolved",
)
if observed_head == BASE_REVISION:
    if observed_symbolic_head.returncode != 1:
        projection_errors.append(
            "ADR-055 historical HEAD was not detached after nested guards"
        )
    tracked_status = subprocess.check_output(
        ["git", "status", "--porcelain=v1", "--untracked-files=no"],
        text=True,
    ).splitlines()
    if tracked_status != (
        [next_env_status_line] if next_env_dirty else []
    ):
        projection_errors.append(
            "ADR-055 historical tracked checkout changed during nested guards"
        )
    for path in added_paths:
        if path.exists():
            projection_errors.append(
                f"ADR-055 added path reappeared during historical guards: {path}"
            )
elif observed_head == original_head:
    print(
        "ADR-055 projection stopped after custody but before the base "
        "checkout; restoring the original repository state"
    )
else:
    projection_errors.append(
        "ADR-055 nested guards left HEAD at an unexpected commit: "
        + observed_head
    )

if next_env_dirty:
    next_env_path.parent.mkdir(parents=True, exist_ok=True)
    shutil.copyfile(next_env_custody_path, next_env_path)
subprocess.run(
    ["git", "checkout", "--detach", original_head],
    check=True,
)
if original_symbolic_ref is not None:
    original_ref_head = subprocess.check_output(
        ["git", "rev-parse", f"{original_symbolic_ref}^{{commit}}"],
        text=True,
    ).strip()
    require(
        original_ref_head == original_head,
        "ADR-055 original branch moved during historical guards",
    )
    subprocess.run(
        ["git", "symbolic-ref", "HEAD", original_symbolic_ref],
        check=True,
    )
restored_head = subprocess.check_output(
    ["git", "rev-parse", "HEAD"],
    text=True,
).strip()
restored_symbolic_head = subprocess.run(
    ["git", "symbolic-ref", "-q", "HEAD"],
    text=True,
    capture_output=True,
    check=False,
)
restored_symbolic_ref = (
    restored_symbolic_head.stdout.strip()
    if restored_symbolic_head.returncode == 0
    else None
)
require(
    restored_head == original_head
    and restored_symbolic_ref == original_symbolic_ref,
    "ADR-055 original commit/symbolic HEAD state was not restored",
)

for path in current_files:
    custody_path = projection_root / path
    path.parent.mkdir(parents=True, exist_ok=True)
    shutil.copyfile(custody_path, path)
for path, metadata in current_files.items():
    require(
        path.is_file(),
        f"ADR-055 current path was not restored: {path}",
    )
    custody_path = projection_root / path
    if metadata["kind"] == "self":
        require(
            masked_workflow_digest(path) == metadata["rawSha256"]
            and raw_digest(path) == raw_digest(custody_path),
            "ADR-055 workflow bytes were not restored exactly",
        )
    else:
        require(
            raw_digest(path) == metadata["rawSha256"]
            and normalized_digest(path)
            == metadata["normalizedSha256"],
            f"ADR-055 current bytes were not restored exactly: {path}",
        )
if next_env_dirty:
    shutil.copyfile(next_env_custody_path, next_env_path)
restored_status = subprocess.check_output(
    ["git", "status", "--porcelain=v1", "--untracked-files=no"],
    text=True,
).splitlines()
require(
    restored_status == (
        [next_env_status_line] if next_env_dirty else []
    ),
    "ADR-055 restored checkout is not tracked-clean outside the "
    "preserved user-owned Next declaration",
)
require(
    not next_env_dirty
    or (
        raw_digest(next_env_path) == next_env_state["rawSha256"]
        and normalized_digest(next_env_path)
        == next_env_state["normalizedSha256"]
    ),
    "User-owned Next declaration was not restored byte-for-byte",
)
if next_env_dirty:
    next_env_custody_path.unlink()
head_state_path.unlink()
prepared.unlink()
(projection_root / ".restored").write_text(
    "ADR-055 exact current bytes restored after historical guards\n",
    encoding="utf-8",
)
require(not projection_errors, "\n".join(projection_errors))
print(
    "Restored and verified all 18 exact ADR-055 source/test/document "
    "file surfaces"
)
PYTHON
