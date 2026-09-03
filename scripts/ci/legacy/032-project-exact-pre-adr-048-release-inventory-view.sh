python <<'PYTHON'
import hashlib
import json
import os
import shutil
from pathlib import Path

def require(condition, message):
    if not condition:
        raise ValueError(message)

def normalized_bytes(path):
    return path.read_bytes().replace(b"\r\n", b"\n")

def digest(content):
    return hashlib.sha256(content).hexdigest()

pom_path = Path("apps/api/pom.xml")
application_path = Path(
    "apps/api/src/main/java/com/wallstreetreceipts/api/"
    "WallStreetReceiptsApiApplication.java"
)
command_path = Path(
    "apps/api/src/main/java/com/wallstreetreceipts/api/release/"
    "ReleaseSchemaInventoryCommand.java"
)
command_test_path = Path(
    "apps/api/src/test/java/com/wallstreetreceipts/api/release/"
    "ReleaseSchemaInventoryCommandTest.java"
)
current_hashes = {
    pom_path:
        "d56beba72e6b00f7228386faad75ca54b79e22ef037ccd3d6007cd1793c2543b",
    application_path:
        "d9c4c2b7e97eaf7b5344b33fd3838d270bd5f9171cf5dca42a5750726f248fc9",
    command_path:
        "c5c79ff1df41fe7376d6fb7e696fdcdad736d3bc89b470888a94d09b4ec349b3",
    command_test_path:
        "38c1897d5b963f965486dd79dd1f2fb7a059ba511d13346c2e19697c0aff05f3",
}
added_paths = {command_path, command_test_path}
require(
    len(current_hashes) == 4 and len(added_paths) == 2,
    "ADR-048 release-inventory delta inventory changed",
)
for path, expected_hash in current_hashes.items():
    require(path.is_file(), f"ADR-048 current path is missing: {path}")
    require(
        digest(normalized_bytes(path)) == expected_hash,
        f"ADR-048 current bytes changed: {path}",
    )

projection_root = (
    Path(os.environ["RUNNER_TEMP"]) / "wsr-adr048-current-view"
)
require(
    not projection_root.exists(),
    f"ADR-048 projection custody already exists: {projection_root}",
)
projection_root.mkdir(parents=True)
for path in current_hashes:
    custody_path = projection_root / path
    custody_path.parent.mkdir(parents=True, exist_ok=True)
    shutil.copyfile(path, custody_path)
(projection_root / "manifest.json").write_text(
    json.dumps(
        {path.as_posix(): value for path, value in current_hashes.items()},
        sort_keys=True,
    ),
    encoding="utf-8",
)
(projection_root / ".prepared").write_text(
    "ADR-048 exact current bytes saved before historical projection\n",
    encoding="utf-8",
)

for path in added_paths:
    path.unlink()

pom = normalized_bytes(pom_path)
flyway_version = b"        <flyway.version>11.7.2</flyway.version>\n"
require(
    pom.count(flyway_version) == 1,
    "ADR-048 Flyway version pin delta changed",
)
pom_path.write_bytes(pom.replace(flyway_version, b"", 1))

application = normalized_bytes(application_path)
command_import = (
    b"import com.wallstreetreceipts.api.release."
    b"ReleaseSchemaInventoryCommand;\n\n"
)
command_dispatch = (
    b"        var commandExitCode = ReleaseSchemaInventoryCommand."
    b"runIfRequested(args, System.out, System.err);\n"
    b"        if (commandExitCode.isPresent()) {\n"
    b"            if (commandExitCode.getAsInt() != 0) {\n"
    b"                System.exit(commandExitCode.getAsInt());\n"
    b"            }\n"
    b"            return;\n"
    b"        }\n\n"
)
require(
    application.count(command_import) == 1
    and application.count(command_dispatch) == 1,
    "ADR-048 application command-dispatch delta changed",
)
application_path.write_bytes(
    application.replace(command_import, b"", 1)
    .replace(command_dispatch, b"", 1)
)

historical_hashes = {
    pom_path:
        "35cb3a3bc7634d14ac5f63178b17c6934ee748ce0c9912b96ae2ce3beaea3393",
    application_path:
        "bf2a4d2025f3d455ca00ec9893212a03ee932ee5058e2414a727be72be8edac8",
}
require(
    all(not path.exists() for path in added_paths),
    "ADR-048 added file survived historical projection",
)
for path, expected_hash in historical_hashes.items():
    require(
        digest(normalized_bytes(path)) == expected_hash,
        f"ADR-048 historical reverse projection changed: {path}",
    )
print(
    "Projected exact pre-ADR-048 POM/application bytes and removed "
    "the exact release-inventory source/test pair"
)
PYTHON
