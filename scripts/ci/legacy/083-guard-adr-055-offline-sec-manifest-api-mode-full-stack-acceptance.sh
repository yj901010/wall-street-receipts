python <<'PYTHON'
import re
from pathlib import Path

def require(condition, message):
    if not condition:
        raise ValueError(message)

seed_path = Path(
    "apps/api/src/test/java/com/wallstreetreceipts/api/acceptance/"
    "SecManifestAuditAcceptanceSeedHarness.java"
)
shared_fixture_path = Path(
    "apps/api/src/test/java/com/wallstreetreceipts/api/support/"
    "SecManifestAuditDemoFixture.java"
)
parity_path = Path(
    "apps/api/src/test/java/com/wallstreetreceipts/api/web/filinghistory/"
    "SecAuditDemoFixtureParityTest.java"
)
harness_path = Path("scripts/verify-local-full-stack.ps1")
e2e_path = Path("apps/web/e2e/sec-manifest-audit.spec.ts")
page_path = Path(
    "apps/web/src/app/research/sec/filing-history/page.tsx"
)
api_provider_path = Path(
    "apps/web/src/lib/providers/"
    "api-sec-manifest-audit-provider.server.ts"
)
workflow_path = Path(".github/workflows/ci.yml")
documentation_paths = (
    Path("README.md"),
    Path("apps/api/README.md"),
    Path("quality/P2_ACCEPTANCE.md"),
    Path("IMPLEMENTATION_LOG.md"),
    Path(
        "decisions/ADR-055-disposable-offline-sec-manifest-audit-"
        "api-mode-full-stack-acceptance.md"
    ),
)
required_paths = (
    seed_path,
    shared_fixture_path,
    parity_path,
    harness_path,
    e2e_path,
    page_path,
    api_provider_path,
    workflow_path,
    *documentation_paths,
)
require(
    all(path.is_file() for path in required_paths),
    "ADR-055 source/test/document surface is incomplete",
)

seed = seed_path.read_text(encoding="utf-8")
shared_fixture = shared_fixture_path.read_text(encoding="utf-8")
parity = parity_path.read_text(encoding="utf-8")
harness = harness_path.read_text(encoding="utf-8")
e2e = e2e_path.read_text(encoding="utf-8")
page = page_path.read_text(encoding="utf-8")
api_provider = api_provider_path.read_text(encoding="utf-8")
workflow = workflow_path.read_text(encoding="utf-8")
documentation = {
    path: path.read_text(encoding="utf-8")
    for path in documentation_paths
}

adr055_project_marker = (
    "      - name: Project exact pre-ADR-055 repository view\n"
)
adr054_project_marker = (
    "      - name: Project exact pre-ADR-054 repository view\n"
)
adr054_restore_marker = (
    "      - name: Restore exact ADR-054 repository view\n"
)
adr055_restore_marker = (
    "      - name: Restore exact ADR-055 repository view\n"
)
adr055_guard_marker = (
    "      - name: Guard ADR-055 offline SEC manifest API-mode "
    "full-stack acceptance\n"
)
require(
    all(
        workflow.count(marker) == 1
        for marker in (
            adr055_project_marker,
            adr054_project_marker,
            adr054_restore_marker,
            adr055_restore_marker,
            adr055_guard_marker,
        )
    )
    and workflow.index(adr055_project_marker)
    < workflow.index(adr054_project_marker)
    < workflow.index(adr054_restore_marker)
    < workflow.index(adr055_restore_marker)
    < workflow.index(adr055_guard_marker),
    "ADR-055 nested projection/restore ordering changed",
)
adr055_project = workflow.split(
    adr055_project_marker, 1
)[1].split(adr054_project_marker, 1)[0]
adr055_restore = workflow.split(
    adr055_restore_marker, 1
)[1].split(adr055_guard_marker, 1)[0]
require(
    'projection_root / "head-state.json"' in adr055_project
    and '["git", "symbolic-ref", "-q", "HEAD"]'
    in adr055_project
    and (
        '["git", "checkout", "--detach", '
        'BASE_REVISION]'
    ) in adr055_project
    and 'projected_head == BASE_REVISION' in adr055_project
    and '"--untracked-files=no"' in adr055_project
    and 'next_env_status_line = " M apps/web/next-env.d.ts"'
    in adr055_project
    and 'projection_root / "excluded-user-owned" / next_env_path'
    in adr055_project
    and 'shutil.copyfile(next_env_path, next_env_custody_path)'
    in adr055_project
    and '"--force"' not in adr055_project
    and '["git", "show"' not in adr055_project,
    "ADR-055 outer projection must detach a canonical base without "
    "discarding the user-owned Next declaration",
)
require(
    'projection_root / "head-state.json"' in adr055_restore
    and 'observed_head == BASE_REVISION' in adr055_restore
    and 'observed_head == original_head' in adr055_restore
    and (
        '["git", "checkout", "--detach", '
        'original_head]'
    ) in adr055_restore
    and '["git", "symbolic-ref", "HEAD", original_symbolic_ref]'
    in adr055_restore
    and 'restored_symbolic_ref == original_symbolic_ref'
    in adr055_restore
    and 'shutil.copyfile(next_env_custody_path, next_env_path)'
    in adr055_restore
    and 'User-owned Next declaration was not restored byte-for-byte'
    in adr055_restore
    and '"--force"' not in adr055_restore
    and "head_state_path.unlink()" in adr055_restore
    and "prepared.unlink()" in adr055_restore,
    "ADR-055 outer restoration lost partial-failure HEAD recovery",
)

seed_class = "SecManifestAuditAcceptanceSeedHarness"
require(
    seed_class in seed
    and "@Test" in seed
    and not re.match(r"^(?:Test.*|.*(?:Test|Tests|TestCase))$", seed_class),
    "ADR-055 seed must remain outside default Surefire discovery",
)
require(
    '"wsr.sec-manifest-acceptance-seed"' in seed
    and 'System.getProperty(ENABLE_PROPERTY)' in seed
    and "require(\"true\".equals(System.getProperty(ENABLE_PROPERTY)))"
    in seed
    and (
        "-Dtest=com.wallstreetreceipts.api.acceptance."
        "SecManifestAuditAcceptanceSeedHarness"
    ) in harness
    and "-Dwsr.sec-manifest-acceptance-seed=true" in harness,
    "ADR-055 seed is not exact opt-in only",
)
require(
    "jdbc:postgresql://127" in seed
    and "[0-9]{4,5}" in seed
    and seed.count('"wsr_full_stack_acceptance"') >= 5
    and 'System.getenv("SPRING_DATASOURCE_URL")' in seed
    and 'System.getenv("SPRING_FLYWAY_URL")' in seed
    and 'environment.getProperty("spring.datasource.url")' in seed
    and 'environment.getProperty("spring.flyway.url")' in seed
    and "value(datasourceUrl).equals(value(flywayUrl))" in seed
    and 'Pattern.compile("[a-f0-9]{32}")' in seed,
    "ADR-055 seed lost its supplied/effective loopback JDBC/Flyway guard",
)
require(
    'System.getenv("SEC_PROVIDER_ENABLED")' in seed
    and 'System.getenv("OPERATOR_API_ENABLED")' in seed
    and 'System.getenv("SEC_CONTACT_EMAIL")' in seed
    and '"http://127.0.0.1:1"' in seed
    and 'environment.getProperty("app.public-data.sec.enabled")' in seed
    and 'environment.getProperty("app.operator-api.enabled")' in seed,
    "ADR-055 seed lost its effective disabled provider/operator guard",
)
forbidden_seed_fragments = (
    "JdbcTemplate",
    "EntityManager",
    "createNativeQuery",
    "INSERT INTO",
    "UPDATE ",
    "DELETE FROM",
    "CREATE TABLE",
    "Runtime.getRuntime",
    "ProcessBuilder",
)
require(
    not any(fragment in seed for fragment in forbidden_seed_fragments)
    and "FilingCatalogCaptureRepository" in seed
    and "HistoricalFilingSegmentCaptureRepository" in seed
    and "PersistFilingHistoryCollectionManifestService" in seed
    and "SecFilingHistoryManifestAuditQueryService" in seed,
    "ADR-055 seed crossed the production repository/service boundary",
)

manifest_id = (
    "cda6762d385d4e889294d0fec1f7a2a7b20c5157cf67c832b7d7f4857550a1cd"
)
selection_sha = (
    "eadb0c3bf6efb9b3323be1342d0b17e63631b706f088b23fa78e784e1b547acd"
)
root_capture_id = (
    "c9bfc935b27e059397531a4dda1a1a0222e98528c33e85b886c91ca6b74f2fa8"
)
require(
    manifest_id in shared_fixture
    and selection_sha in shared_fixture
    and root_capture_id in shared_fixture
    and 'Instant.parse("2026-08-25T03:30:00.123456Z")'
    in shared_fixture
    and "SecManifestAuditDemoFixture.assembledManifest()" in parity,
    "ADR-055 shared synthetic fixture/parity identity changed",
)

build_start = harness.index("$buildEnvironment = @{")
web_start = harness.index("$webEnvironment = @{")
browser_start = harness.index("$playwrightEnvironment = @{")
build_block = harness[build_start:web_start]
web_block = harness[web_start:browser_start]
browser_block = harness[browser_start:]
for label, block in (
    ("build", build_block),
    ("web", web_block),
    ("browser", browser_block),
):
    require(
        'SEC_MANIFEST_AUDIT_PROVIDER = "api"' in block
        and (
            "SEC_MANIFEST_AUDIT_SYNTHETIC_DEMO_MANIFEST_ID"
            in block
        )
        and manifest_id in block,
        f"ADR-055 {label} child is not pinned to synthetic API mode",
    )
require(
    'PLAYWRIGHT_SEC_MANIFEST_API_SUCCESS = "true"' in browser_block
    and "NEXT_PUBLIC_API_BASE_URL = \"\"" in build_block
    and "NEXT_PUBLIC_API_BASE_URL = \"\"" in web_block
    and "NEXT_PUBLIC_API_BASE_URL = \"\"" in browser_block,
    "ADR-055 private API-mode browser boundary changed",
)
require(
    "const API_SUCCESS_FLAG = process.env.PLAYWRIGHT_SEC_MANIFEST_API_SUCCESS"
    in e2e
    and 'API_SUCCESS_FLAG !== undefined && API_SUCCESS_FLAG !== "true"'
    in e2e
    and 'API_SUCCESS_FLAG === "true"' in e2e
    and 'process.env.SEC_MANIFEST_AUDIT_PROVIDER !== "api"' in e2e
    and (
        "process.env.SEC_MANIFEST_AUDIT_SYNTHETIC_DEMO_MANIFEST_ID "
        "!== MANIFEST_ID"
    ) in e2e,
    "ADR-055 browser success opt-in is not fail-closed",
)

require(
    '/research/sec/filing-history"' in harness
    and harness.count('/research/sec/filing-history"') == 1,
    "ADR-055 primary SEC route smoke changed",
)
route_block = harness[
    harness.index("$primaryRoutes = @("):
    harness.index("foreach ($route in $primaryRoutes)")
]
require(
    len(re.findall(r'^\s+"/[^"]*"[,]?$', route_block, re.MULTILINE)) == 13,
    "ADR-055 must smoke exactly 13 production routes",
)
expected_specs = (
    "call-revisions.spec.ts",
    "call-outcomes.spec.ts",
    "call-list-api.spec.ts",
    "sec-manifest-audit.spec.ts",
)
require(
    all(browser_block.count(spec) == 1 for spec in expected_specs)
    and 'Write-Host "PASS: 5/5 focused Chromium checks' in harness
    and e2e.count('test("') == 2,
    "ADR-055 focused browser matrix changed",
)

access_block = harness[
    harness.index("$requiredAccessLines = @("):
    harness.index("$accessLogDirectory =")
]
access_lines = re.findall(
    r'^\s+"(GET [^"]+)"[,]?$', access_block, re.MULTILINE
)
require(
    len(access_lines) == 18 and len(set(access_lines)) == 18,
    "ADR-055 must require 18 unique exact Tomcat access lines",
)
sec_prefix = (
    "GET /v1/sec/filing-history/manifests/" + manifest_id
)
require(
    sum(line.startswith(sec_prefix) for line in access_lines) == 5
    and sum(" 200" in line for line in access_lines if line.startswith(sec_prefix))
    == 4
    and sum(" 404" in line for line in access_lines if line.startswith(sec_prefix))
    == 1
    and any("/descriptors?" in line for line in access_lines)
    and any("/accessions?" in line for line in access_lines)
    and any("/occurrences?" in line for line in access_lines)
    and any("2026-08-25T03%3A30%3A00.123455Z 404" in line for line in access_lines),
    "ADR-055 four-view/pre-cutoff exact Spring evidence changed",
)
require(
    any(
        "from=2026-08-10T15%3A00%3A00.000Z"
        "&to=2026-08-11T15%3A00%3A00.000Z" in line
        for line in access_lines
    ),
    "ADR-055 lost the ADR-054 Korean civil-day UTC interval",
)

database_identity = (
    "3|2|4|3|1|2|2|2|4|1|2|4|6|0|0|0|0|"
    + manifest_id + "|" + selection_sha + "|" + root_capture_id
    + "|2026-08-25T03:30:00.123456Z"
)
require(
    "$expectedDatabaseIdentity =" in harness
    and all(part in harness for part in (
        "3|2|4|3|1|2|2|2|4|1|2|4|6|0|0|0|0|",
        manifest_id,
        selection_sha,
        root_capture_id,
        "2026-08-25T03:30:00.123456Z",
    ))
    and database_identity
    in documentation[Path("quality/P2_ACCEPTANCE.md")]
    and database_identity in documentation[Path("IMPLEMENTATION_LOG.md")]
    and database_identity in documentation[documentation_paths[-1]],
    "ADR-055 complete PostgreSQL count/identity tuple changed",
)
require(
    harness.count('SEC_PROVIDER_ENABLED          = "false"') == 1
    and harness.count('OPERATOR_API_ENABLED          = "false"') == 1
    and 'SEC_BASE_URL                  = "http://127.0.0.1:1"'
    in harness
    and 'SEC_CONTACT_EMAIL             = ""' in harness
    and "live SEC collection and operator boundaries remained disabled"
    in harness,
    "ADR-055 live SEC/operator isolation changed",
)
require(
    "collectBrowserApiRequests" in e2e
    and e2e.count("expect(browserApiRequests).toEqual([])") == 3
    and 'cache: "no-store"' in api_provider
    and "await fetcher(" in api_provider
    and "fetch(" not in page,
    "ADR-055 browser-to-private-origin zero-request proof changed",
)

required_document_markers = {
    Path("README.md"): (
        "ADR-055",
        "13 primary routes",
        "five focused Chromium tests",
        "18 exact Spring access",
    ),
    Path("apps/api/README.md"): (
        "ADR-055",
        "SecManifestAuditAcceptanceSeedHarness",
        "wsr.sec-manifest-acceptance-seed=true",
    ),
    Path("quality/P2_ACCEPTANCE.md"): (
        "## Exact SEC manifest-audit API-mode full-stack acceptance boundary",
        "Repository CI parses and guards ADR-055",
        "does not run the Docker/Chromium harness",
    ),
    Path("IMPLEMENTATION_LOG.md"): (
        "## 2026-08-31 — ADR-055 disposable offline SEC manifest-audit API-mode full-stack acceptance",
        "Production Next built successfully",
        "production routes rendered",
        "focused Chromium passed **5/5**",
        "all **18 exact",
        "Spring reads** were observed",
    ),
    documentation_paths[-1]: (
        "# ADR-055: Disposable offline SEC manifest-audit API-mode full-stack acceptance",
        "Repository CI parses and guards the ADR-055 source",
        "It does **not**",
        "execute this Docker/Chromium full-stack harness",
    ),
}
for path, markers in required_document_markers.items():
    require(
        all(marker in documentation[path] for marker in markers),
        f"ADR-055 documentation/log parity changed: {path}",
    )

parse_marker = (
    "      - name: Parse disposable offline local full-stack acceptance harness\n"
)
require(
    workflow.count(parse_marker) == 1,
    "ADR-055 requires exactly one CI parse-only harness step",
)
parse_block = workflow.split(parse_marker, 1)[1].split(
    "\n      - name:", 1
)[0]
require(
    "[System.Management.Automation.Language.Parser]::ParseFile"
    in parse_block
    and "scripts/verify-local-full-stack.ps1" in parse_block
    and not re.search(
        r"(?m)^          pwsh(?:\.exe)? -NoProfile -File "
        r"\.?/?scripts/verify-local-full-stack\.ps1\s*$",
        workflow,
    )
    and not re.search(
        r"(?m)^\s+run:\s*pwsh(?:\.exe)? -NoProfile -File "
        r"\.?/?scripts/verify-local-full-stack\.ps1\s*$",
        workflow,
    ),
    "Repository CI must parse, not execute, the Docker/Chromium harness",
)

print(
    "Validated ADR-055 opt-in synthetic seeding, exact API-mode/KST/"
    "PostgreSQL evidence, 13-route/5-check/18-read acceptance, private-"
    "origin isolation, documentation parity, and CI parse-only boundary"
)
PYTHON
