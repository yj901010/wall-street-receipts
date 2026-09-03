python <<'PYTHON'
import hashlib
import json
import os
import re
import shutil
from pathlib import Path

CUSTODY_MANIFEST_DIGEST = "fa03c29918073dea1bb9e870a6102ffc6d9fc7dae83f80c12acc0feff9bcdb87"  # ADR053_CUSTODY_MANIFEST_DIGEST

def require(condition, message):
    if not condition:
        raise ValueError(message)

def raw_digest(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()

def normalized_digest(path):
    content = path.read_bytes().replace(b"\r\n", b"\n")
    return hashlib.sha256(content).hexdigest()

def masked_workflow_digest(path):
    content = path.read_bytes().replace(b"\r\n", b"\n")
    patterns = (
        rb'(?m)^(          WORKFLOW_SELF_DIGEST = ")[0-9a-f]{64}("  # ADR053_WORKFLOW_SELF_DIGEST)$',
        rb'(?m)^(          CUSTODY_MANIFEST_DIGEST = ")[0-9a-f]{64}("  # ADR053_CUSTODY_MANIFEST_DIGEST)$',
    )
    for pattern in patterns:
        content, replacement_count = re.subn(
            pattern,
            rb'\1<ADR053-MASKED-SHA256>\2',
            content,
        )
        require(
            replacement_count == 1,
            "ADR-053 workflow self-hash slot changed",
        )
    return hashlib.sha256(content).hexdigest()

projection_root = (
    Path(os.environ["RUNNER_TEMP"]) / "wsr-adr053-current-view"
)
prepared = projection_root / ".prepared"
if not prepared.is_file():
    print("ADR-053 historical projection did not mutate the repository")
    raise SystemExit(0)
require(
    prepared.read_text(encoding="utf-8")
    == "ADR-053 exact current bytes saved before historical projection\n",
    "ADR-053 projection custody marker changed",
)
manifest_path = projection_root / "manifest.json"
require(
    manifest_path.is_file(),
    "ADR-053 current-byte custody manifest is missing",
)
manifest_bytes = manifest_path.read_bytes()
require(
    hashlib.sha256(manifest_bytes).hexdigest()
    == CUSTODY_MANIFEST_DIGEST,
    "ADR-053 current-byte custody manifest changed",
)
raw_manifest = json.loads(manifest_bytes)
require(
    isinstance(raw_manifest, dict) and len(raw_manifest) == 36,
    "ADR-053 current-byte custody inventory changed",
)
current_files = {
    Path(path): metadata
    for path, metadata in raw_manifest.items()
}
require(
    Path("apps/web/next-env.d.ts") not in current_files,
    "Generated Next.js declaration entered ADR-053 custody",
)
for path, metadata in current_files.items():
    require(
        isinstance(metadata, dict)
        and set(metadata) == {
            "kind", "rawSha256", "normalizedSha256"
        }
        and metadata["kind"] in {"text", "binary", "self"}
        and isinstance(metadata["rawSha256"], str)
        and len(metadata["rawSha256"]) == 64,
        f"ADR-053 custody metadata is invalid: {path}",
    )
    if metadata["kind"] == "text":
        require(
            isinstance(metadata["normalizedSha256"], str)
            and len(metadata["normalizedSha256"]) == 64,
            f"ADR-053 text custody hash is invalid: {path}",
        )
    elif metadata["kind"] == "binary":
        require(
            path == Path("apps/web/public/og.png")
            and metadata["normalizedSha256"] is None,
            f"ADR-053 binary custody is not the raw OG asset: {path}",
        )
    else:
        require(
            path == Path(".github/workflows/ci.yml")
            and metadata["normalizedSha256"] is None,
            f"ADR-053 workflow self-custody metadata changed: {path}",
        )
    custody_path = projection_root / path
    require(custody_path.is_file(), f"ADR-053 custody is missing: {path}")
    if metadata["kind"] == "self":
        require(
            masked_workflow_digest(custody_path)
            == metadata["rawSha256"],
            "ADR-053 workflow self-custody bytes changed",
        )
    else:
        require(
            raw_digest(custody_path) == metadata["rawSha256"],
            f"ADR-053 current-byte custody changed: {path}",
        )
        if metadata["kind"] == "text":
            require(
                normalized_digest(custody_path)
                == metadata["normalizedSha256"],
                f"ADR-053 normalized custody changed: {path}",
            )

historical_hashes = {
    Path(".env.example"):
        "513b03a1685870c7295fab836c0f345f5c74998850c59730045d5e098944f4eb",
    Path(".github/workflows/ci.yml"):
        "b2f716b7578ffc4bb8754fd4e266df2aec61e8c3af6d0fc2e29a72db1ec78c6c",
    Path("README.md"):
        "0d6f1d93ee6abbd8474081e1d135084d9353fbf144396c75e1a9cee3f2add31e",
    Path("apps/api/README.md"):
        "f845309b1b99493da4a0508243b0100ed98d45c4d73bc51c115bf2e0fadfe1bc",
    Path("deploy/home-server/README.md"):
        "fd887e14084b87d8bd8bacade9cb299d189fa5c542f2f32484222e1e9984b73d",
    Path("IMPLEMENTATION_LOG.md"):
        "a756acfa42b67e5fbc275e4c600a3633ceab8ffcf49aeacf66a7fcf211666aec",
    Path("apps/web/src/app/layout.test.tsx"):
        "e95a8a78ee4a8665096e5020eb9628e87dda1f28e92381b15f9a7b5ae5eb442e",
    Path("apps/web/src/app/layout.tsx"):
        "f80d40bd6dcb30efb746f0605308f907c6b0c425c34b250b76db02bd522cf3ff",
    Path("deploy/home-server/compose.yaml"):
        "684ab8310edcb8e40d3734f8382ca555742330636b871dc7dddb11f70c040a22",
    Path("deploy/home-server/web.Dockerfile"):
        "20e249476460bb3b6f54ecaf3d20c617f7ff236744ff12cd537ed40061b9e1a0",
    Path("scripts/verify-home-server-deployment.py"):
        "9ac71cb21fa21f85c8118eedcb4e91dd15a1d8cd99b0fd5637670ad5d15fa7d8",
}
added_paths = set(current_files) - set(historical_hashes)
projection_errors = []
for path in added_paths:
    if path.exists():
        projection_errors.append(
            f"ADR-053 added path reappeared during historical guards: {path}"
        )
for path, expected_hash in historical_hashes.items():
    if (
        not path.is_file()
        or raw_digest(path) != expected_hash
        or normalized_digest(path) != expected_hash
    ):
        projection_errors.append(
            f"ADR-053 historical bytes changed before restoration: {path}"
        )

for path in current_files:
    custody_path = projection_root / path
    path.parent.mkdir(parents=True, exist_ok=True)
    shutil.copyfile(custody_path, path)
for path, metadata in current_files.items():
    require(path.is_file(), f"ADR-053 current path was not restored: {path}")
    custody_path = projection_root / path
    if metadata["kind"] == "self":
        require(
            masked_workflow_digest(path) == metadata["rawSha256"]
            and raw_digest(path) == raw_digest(custody_path),
            "ADR-053 workflow bytes were not restored exactly",
        )
    else:
        require(
            raw_digest(path) == metadata["rawSha256"],
            f"ADR-053 current raw bytes were not restored exactly: {path}",
        )
        if metadata["kind"] == "text":
            require(
                normalized_digest(path)
                == metadata["normalizedSha256"],
                f"ADR-053 current normalized bytes were not restored: {path}",
            )
prepared.unlink()
(projection_root / ".restored").write_text(
    "ADR-053 exact current bytes restored after historical guards\n",
    encoding="utf-8",
)
require(
    not projection_errors,
    "\n".join(projection_errors),
)
print(
    "Restored and verified all 36 exact ADR-053 source/config/test/"
    "asset/document file surfaces"
)
PYTHON
