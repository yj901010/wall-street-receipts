python <<'PYTHON'
import hashlib
import json
import os
import shutil
from pathlib import Path

def require(condition, message):
    if not condition:
        raise ValueError(message)

def normalized_digest(path):
    content = path.read_bytes().replace(b"\r\n", b"\n")
    return hashlib.sha256(content).hexdigest()

projection_root = (
    Path(os.environ["RUNNER_TEMP"]) / "wsr-adr048-current-view"
)
prepared = projection_root / ".prepared"
if not prepared.is_file():
    print("ADR-048 historical projection did not mutate the repository")
    raise SystemExit(0)
manifest_path = projection_root / "manifest.json"
require(
    manifest_path.is_file(),
    "ADR-048 current-byte custody manifest is missing",
)
raw_manifest = json.loads(manifest_path.read_text(encoding="utf-8"))
require(
    isinstance(raw_manifest, dict) and len(raw_manifest) == 4,
    "ADR-048 current-byte custody manifest changed",
)
current_hashes = {
    Path(path): expected_hash
    for path, expected_hash in raw_manifest.items()
}
for path, expected_hash in current_hashes.items():
    custody_path = projection_root / path
    require(
        custody_path.is_file()
        and normalized_digest(custody_path) == expected_hash,
        f"ADR-048 current-byte custody changed: {path}",
    )
for path in current_hashes:
    custody_path = projection_root / path
    path.parent.mkdir(parents=True, exist_ok=True)
    shutil.copyfile(custody_path, path)
for path, expected_hash in current_hashes.items():
    require(
        path.is_file() and normalized_digest(path) == expected_hash,
        f"ADR-048 current bytes were not restored exactly: {path}",
    )
prepared.unlink()
(projection_root / ".restored").write_text(
    "ADR-048 exact current bytes restored after historical guards\n",
    encoding="utf-8",
)
print("Restored and verified all four exact ADR-048 current file surfaces")
PYTHON
