python <<'PYTHON'
import hashlib
import json
import os
import re
import shutil
import subprocess
from pathlib import Path

BASE_REVISION = "8fc373279a4c479a9786a9e0250bc33a0767e9ec"
CUSTODY_MANIFEST_DIGEST = "4ebf51be9879032bb29816dad724c367a299958d2a2248405596cfec2d061947"  # ADR054_CUSTODY_MANIFEST_DIGEST

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
        rb'(?m)^(          WORKFLOW_SELF_DIGEST = ")[0-9a-f]{64}("  # ADR054_WORKFLOW_SELF_DIGEST)$',
        rb'(?m)^(          CUSTODY_MANIFEST_DIGEST = ")[0-9a-f]{64}("  # ADR054_CUSTODY_MANIFEST_DIGEST)$',
    )
    for pattern in patterns:
        content, replacement_count = re.subn(
            pattern,
            rb'\1<ADR054-MASKED-SHA256>\2',
            content,
        )
        require(
            replacement_count == 1,
            "ADR-054 workflow self-hash slot changed",
        )
    return hashlib.sha256(content).hexdigest()

projection_root = (
    Path(os.environ["RUNNER_TEMP"]) / "wsr-adr054-current-view"
)
prepared = projection_root / ".prepared"
if not prepared.is_file():
    print("ADR-054 historical projection did not mutate the repository")
    raise SystemExit(0)
require(
    prepared.read_text(encoding="utf-8")
    == "ADR-054 exact current bytes saved before historical projection\n",
    "ADR-054 projection custody marker changed",
)
manifest_path = projection_root / "manifest.json"
require(
    manifest_path.is_file(),
    "ADR-054 current-byte custody manifest is missing",
)
manifest_bytes = manifest_path.read_bytes()
require(
    hashlib.sha256(manifest_bytes).hexdigest()
    == CUSTODY_MANIFEST_DIGEST,
    "ADR-054 current-byte custody manifest changed",
)
raw_manifest = json.loads(manifest_bytes)
require(
    isinstance(raw_manifest, dict) and len(raw_manifest) == 54,
    "ADR-054 current-byte custody inventory changed",
)
current_files = {
    Path(raw_path): metadata
    for raw_path, metadata in raw_manifest.items()
}
workflow_path = Path(".github/workflows/ci.yml")
added_paths = {
    Path(value)
    for value in (
        "apps/web/src/components/kst-timestamp.tsx",
        "apps/web/src/lib/kst-time-boundary.test.ts",
        "apps/web/src/lib/kst-time.test.ts",
        "apps/web/src/lib/kst-time.ts",
        "decisions/ADR-054-site-wide-kst-display-time-policy.md",
    )
}
modified_paths = set(current_files) - added_paths
require(
    len(modified_paths) == 49
    and workflow_path in modified_paths
    and added_paths <= set(current_files),
    "ADR-054 modified/added custody partition changed",
)
require(
    Path("apps/web/next-env.d.ts") not in current_files,
    "Generated Next.js declaration entered ADR-054 custody",
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
        f"ADR-054 custody metadata is invalid: {path}",
    )
    if metadata["kind"] == "self":
        require(
            path == workflow_path
            and metadata["normalizedSha256"] is None,
            f"ADR-054 workflow self-custody metadata changed: {path}",
        )
    else:
        require(
            path != workflow_path
            and isinstance(metadata["normalizedSha256"], str)
            and len(metadata["normalizedSha256"]) == 64,
            f"ADR-054 text custody hash is invalid: {path}",
        )
    custody_path = projection_root / path
    require(
        custody_path.is_file(),
        f"ADR-054 custody is missing: {path}",
    )
    if metadata["kind"] == "self":
        require(
            masked_workflow_digest(custody_path)
            == metadata["rawSha256"],
            "ADR-054 workflow self-custody bytes changed",
        )
    else:
        require(
            raw_digest(custody_path) == metadata["rawSha256"]
            and normalized_digest(custody_path)
            == metadata["normalizedSha256"],
            f"ADR-054 current-byte custody changed: {path}",
        )

resolved_revision = subprocess.check_output(
    ["git", "rev-parse", f"{BASE_REVISION}^{{commit}}"],
    text=True,
).strip()
require(
    resolved_revision == BASE_REVISION,
    "ADR-054 base revision is unavailable or changed",
)

def historical_bytes(path):
    return subprocess.check_output(
        ["git", "show", f"{BASE_REVISION}:{path.as_posix()}"],
    )

historical_files = {
    path: historical_bytes(path)
    for path in modified_paths
}
projection_errors = []
for path in added_paths:
    if path.exists():
        projection_errors.append(
            f"ADR-054 added path reappeared during historical guards: {path}"
        )
for path, expected_bytes in historical_files.items():
    if not path.is_file() or path.read_bytes() != expected_bytes:
        projection_errors.append(
            f"ADR-054 historical bytes changed before restoration: {path}"
        )

for path in current_files:
    custody_path = projection_root / path
    path.parent.mkdir(parents=True, exist_ok=True)
    shutil.copyfile(custody_path, path)
for path, metadata in current_files.items():
    require(
        path.is_file(),
        f"ADR-054 current path was not restored: {path}",
    )
    custody_path = projection_root / path
    if metadata["kind"] == "self":
        require(
            masked_workflow_digest(path) == metadata["rawSha256"]
            and raw_digest(path) == raw_digest(custody_path),
            "ADR-054 workflow bytes were not restored exactly",
        )
    else:
        require(
            raw_digest(path) == metadata["rawSha256"]
            and normalized_digest(path)
            == metadata["normalizedSha256"],
            f"ADR-054 current bytes were not restored exactly: {path}",
        )
prepared.unlink()
(projection_root / ".restored").write_text(
    "ADR-054 exact current bytes restored after historical guards\n",
    encoding="utf-8",
)
require(not projection_errors, "\n".join(projection_errors))
print(
    "Restored and verified all 54 exact ADR-054 source/test/document "
    "file surfaces"
)
PYTHON
