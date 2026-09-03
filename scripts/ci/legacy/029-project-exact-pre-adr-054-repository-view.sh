python <<'PYTHON'
import hashlib
import json
import os
import re
import shutil
import subprocess
from pathlib import Path

BASE_REVISION = "8fc373279a4c479a9786a9e0250bc33a0767e9ec"
WORKFLOW_SELF_DIGEST = "ad31cc66f1f46bc192e3da8b3d22b198a72ed20689e3655d5e70bcbbeb116a7a"  # ADR054_WORKFLOW_SELF_DIGEST

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

modified_paths = {
    Path(value)
    for value in (
        ".github/workflows/ci.yml",
        "IMPLEMENTATION_LOG.md",
        "README.md",
        "apps/web/e2e/analysts.spec.ts",
        "apps/web/e2e/call-list-api.spec.ts",
        "apps/web/e2e/dashboard.spec.ts",
        "apps/web/e2e/institutions.spec.ts",
        "apps/web/e2e/market.spec.ts",
        "apps/web/e2e/sec-manifest-audit.spec.ts",
        "apps/web/e2e/sp500-history.spec.ts",
        "apps/web/src/app/analysts/analyst-directory.tsx",
        "apps/web/src/app/analysts/messages.ts",
        "apps/web/src/app/analysts/page.test.tsx",
        "apps/web/src/app/analysts/page.tsx",
        "apps/web/src/app/calls/[id]/call-context-sections.tsx",
        "apps/web/src/app/calls/[id]/page.test.tsx",
        "apps/web/src/app/calls/[id]/page.tsx",
        "apps/web/src/app/calls/messages.ts",
        "apps/web/src/app/calls/page.test.tsx",
        "apps/web/src/app/calls/page.tsx",
        "apps/web/src/app/institutions/institution-directory.tsx",
        "apps/web/src/app/institutions/messages.ts",
        "apps/web/src/app/institutions/page.test.tsx",
        "apps/web/src/app/institutions/page.tsx",
        "apps/web/src/app/maps/[universe]/page.test.tsx",
        "apps/web/src/app/maps/[universe]/page.tsx",
        "apps/web/src/app/market/market-board.tsx",
        "apps/web/src/app/market/messages.ts",
        "apps/web/src/app/market/page.test.tsx",
        "apps/web/src/app/market/page.tsx",
        "apps/web/src/app/markets/sp500/page.test.tsx",
        "apps/web/src/app/markets/sp500/page.tsx",
        "apps/web/src/app/markets/sp500/sp500-call-history.tsx",
        "apps/web/src/app/methodology/messages.ts",
        "apps/web/src/app/methodology/methodology-registry.tsx",
        "apps/web/src/app/methodology/page.test.tsx",
        "apps/web/src/app/methodology/page.tsx",
        "apps/web/src/app/page.test.tsx",
        "apps/web/src/app/research/sec/filing-history/messages.ts",
        "apps/web/src/app/research/sec/filing-history/page.test.tsx",
        "apps/web/src/app/research/sec/filing-history/sec-manifest-audit-view.tsx",
        "apps/web/src/components/dashboard-messages.ts",
        "apps/web/src/components/dashboard-view.tsx",
        "apps/web/src/components/market-map-messages.ts",
        "apps/web/src/components/market-map.tsx",
        "apps/web/src/components/market-treemap.tsx",
        "apps/web/src/lib/providers/call-list-query.test.ts",
        "apps/web/src/lib/providers/call-list-query.ts",
        "quality/P2_ACCEPTANCE.md",
    )
}
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
current_paths = modified_paths | added_paths
require(
    len(modified_paths) == 49
    and len(added_paths) == 5
    and len(current_paths) == 54,
    "ADR-054 source/test/document delta inventory changed",
)
require(
    Path("apps/web/next-env.d.ts") not in current_paths,
    "Generated Next.js declaration entered ADR-054 custody",
)

resolved_revision = subprocess.check_output(
    ["git", "rev-parse", f"{BASE_REVISION}^{{commit}}"],
    text=True,
).strip()
require(
    resolved_revision == BASE_REVISION,
    "ADR-054 base revision is unavailable or changed",
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
    "ADR-054 exact 8fc3732-to-current delta inventory changed",
)

workflow_path = Path(".github/workflows/ci.yml")
current_files = {}
for path in current_paths:
    require(path.is_file(), f"ADR-054 current path is missing: {path}")
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
    "ADR-054 workflow self-custody bytes changed",
)

projection_root = (
    Path(os.environ["RUNNER_TEMP"]) / "wsr-adr054-current-view"
)
require(
    not projection_root.exists(),
    f"ADR-054 projection custody already exists: {projection_root}",
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
    rb'(?m)^          CUSTODY_MANIFEST_DIGEST = "([0-9a-f]{64})"  # ADR054_CUSTODY_MANIFEST_DIGEST$',
    workflow_path.read_bytes().replace(b"\r\n", b"\n"),
)
require(
    len(custody_matches) == 1
    and hashlib.sha256(manifest_bytes).hexdigest()
    == custody_matches[0].decode("ascii"),
    "ADR-054 current-byte custody manifest changed",
)
(projection_root / "manifest.json").write_bytes(manifest_bytes)
(projection_root / ".prepared").write_text(
    "ADR-054 exact current bytes saved before historical projection\n",
    encoding="utf-8",
)

def historical_bytes(path):
    return subprocess.check_output(
        ["git", "show", f"{BASE_REVISION}:{path.as_posix()}"],
    )

historical_files = {
    path: historical_bytes(path)
    for path in modified_paths
}
for path in added_paths:
    path.unlink()
for path, content in historical_files.items():
    path.write_bytes(content)
require(
    all(not path.exists() for path in added_paths),
    "ADR-054 added file survived historical projection",
)
for path, expected_bytes in historical_files.items():
    require(
        path.is_file() and path.read_bytes() == expected_bytes,
        f"ADR-054 exact 8fc3732 bytes were not projected: {path}",
    )
print(
    "Projected the exact pre-ADR-054 8fc3732 source/test/document "
    "view before the ADR-053 historical projection"
)
PYTHON
