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

current_hashes = {
    Path("contracts/openapi.yaml"):
        "6aed47152a974ed1de5557db8442d5ef5302380c528f2682f475e7dcaeb94426",
    Path("apps/api/src/main/java/com/wallstreetreceipts/api/config/FilingHistoryCollectionConfiguration.java"):
        "e92ca2637ba683203a7c6b9253ff182ff5f2197891e1ca73f7dddb574ff531bb",
    Path("apps/api/src/main/java/com/wallstreetreceipts/api/web/security/ApiRequestRejectedHandler.java"):
        "74fa3cd68f4f70a103537508305cbda60c361c215d944753ee2d774c018e307a",
    Path("apps/api/src/test/java/com/wallstreetreceipts/api/migration/FilingHistoryCollectionManifestPostgreSqlTest.java"):
        "618a413334062038d10d2f72863ab64ba5188f59b301dc53187ccd48f9b85f3d",
    Path("apps/api/src/main/java/com/wallstreetreceipts/api/application/filinghistory/InvalidSecFilingHistoryManifestAuditQueryException.java"):
        "fc47852d1c6b08f94700fdce017f8d510d07a908b51b3d51f52072e2e484cf1f",
    Path("apps/api/src/main/java/com/wallstreetreceipts/api/application/filinghistory/SecFilingHistoryManifestAuditNotFoundException.java"):
        "ece71eff2a7c29b5fb31e37008fb53444765fc00f5b5a7572f6fc6cc1a2a3ae8",
    Path("apps/api/src/main/java/com/wallstreetreceipts/api/application/filinghistory/SecFilingHistoryManifestAuditQueryService.java"):
        "94a9210f2e6b565325e13520536cf162d7eaabeb011d12c617d412c0cb78ccf3",
    Path("apps/api/src/main/java/com/wallstreetreceipts/api/web/filinghistory/SecFilingHistoryManifestAuditController.java"):
        "6954d4e7477af3b4ae48a1a7fc5d058ff10a9a6ce35324e32c73447459d2b325",
    Path("apps/api/src/main/java/com/wallstreetreceipts/api/web/filinghistory/SecFilingHistoryManifestAuditResponses.java"):
        "f13476ac48aa48803856a5d649a678ea876d8407bba189e0e916f3028ce29dea",
    Path("apps/api/src/main/java/com/wallstreetreceipts/api/web/filinghistory/SecFilingHistoryManifestAuditExceptionHandler.java"):
        "93f7a5c6b4a0dad8da521a4ce1bbf4e127641a3d492fbace900927a22576334f",
    Path("apps/api/src/main/java/com/wallstreetreceipts/api/web/filinghistory/SecFilingHistoryManifestAuditMethodNotAllowedExceptionHandler.java"):
        "56c4c1dced6beae69c3d6dd541289a5b4a0b5b8cb2c0f684ed5c63c5ee6939b5",
    Path("apps/api/src/test/java/com/wallstreetreceipts/api/application/filinghistory/SecFilingHistoryManifestAuditQueryServiceTest.java"):
        "0cc480ac5c461502b7cf4c0c85b326b2aa5c85ffb8f13d39cc14bb856e1a8fb9",
    Path("apps/api/src/test/java/com/wallstreetreceipts/api/web/filinghistory/SecFilingHistoryManifestAuditApiTest.java"):
        "1592877bb8c4da34ec7e65a6bcbaf2da7b8d0781390699427e59f7ec09c70aa8",
}
modified_paths = {
    Path("contracts/openapi.yaml"),
    Path("apps/api/src/main/java/com/wallstreetreceipts/api/config/FilingHistoryCollectionConfiguration.java"),
    Path("apps/api/src/main/java/com/wallstreetreceipts/api/web/security/ApiRequestRejectedHandler.java"),
    Path("apps/api/src/test/java/com/wallstreetreceipts/api/migration/FilingHistoryCollectionManifestPostgreSqlTest.java"),
}
added_paths = set(current_hashes) - modified_paths
expected_added_main = {
    path for path in added_paths
    if path.as_posix().startswith("apps/api/src/main/")
}
expected_added_tests = added_paths - expected_added_main
actual_added_main = {
    *Path("apps/api/src/main/java/com/wallstreetreceipts/api/application/filinghistory").glob("*ManifestAudit*.java"),
    *Path("apps/api/src/main/java/com/wallstreetreceipts/api/web/filinghistory").glob("*.java"),
}
actual_added_tests = {
    *Path("apps/api/src/test/java/com/wallstreetreceipts/api/application/filinghistory").glob("*ManifestAudit*.java"),
    *Path("apps/api/src/test/java/com/wallstreetreceipts/api/web/filinghistory").glob("*ManifestAudit*.java"),
}
require(
    len(current_hashes) == 13
    and len(modified_paths) == 4
    and len(expected_added_main) == 7
    and len(expected_added_tests) == 2
    and actual_added_main == expected_added_main
    and actual_added_tests == expected_added_tests,
    "ADR-052 source/test delta inventory changed",
)
for path, expected_hash in current_hashes.items():
    require(path.is_file(), f"ADR-052 current path is missing: {path}")
    require(
        digest(normalized_bytes(path)) == expected_hash,
        f"ADR-052 current bytes changed: {path}",
    )

projection_root = (
    Path(os.environ["RUNNER_TEMP"]) / "wsr-adr052-current-view"
)
require(
    not projection_root.exists(),
    f"ADR-052 projection custody already exists: {projection_root}",
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
    "ADR-052 exact current bytes saved before historical projection\n",
    encoding="utf-8",
)

for path in added_paths:
    path.unlink()

openapi_path = Path("contracts/openapi.yaml")
openapi = normalized_bytes(openapi_path)

def replace_region(content, start, end, replacement):
    require(
        start in content,
        f"ADR-052 OpenAPI projection start changed: {start!r}",
    )
    start_index = content.index(start)
    end_index = content.index(end, start_index)
    return content[:start_index] + replacement + content[end_index:]

openapi = replace_region(
    openapi,
    b"info:\n",
    b"servers:\n",
    b"""info:\n  title: Wall Street Receipts API\n  version: 0.4.0\n  summary: Point-in-time analyst-call read API\n  description: |\n    P1 read contract for canonical analyst-call events. All timestamps are UTC\n    RFC 3339 instants. Filters are combined with logical AND. Missing numeric\n    values remain JSON null and must not be interpreted as zero.\n  license:\n    name: Proprietary\n""",
)
openapi = replace_region(
    openapi,
    b"tags:\n",
    b"paths:\n",
    b"""tags:\n  - name: Analyst Calls\n    description: Immutable analyst-call event queries\n\n""",
)
openapi = replace_region(
    openapi,
    b"  /v1/sec/filing-history/manifests/{manifestId}:\n",
    b"components:\n",
    b"",
)
for start, end in (
    (b"    SecManifestId:\n", b"  headers:\n"),
    (b"    NoStore:\n", b"  responses:\n"),
    (b"    SecManifestSummaryOk:\n", b"  schemas:\n"),
):
    openapi = replace_region(openapi, start, end, b"\n")
openapi = replace_region(
    openapi,
    b"    LowercaseSha256:\n",
    b"    Identifier:\n",
    b"",
)
openapi_path.write_bytes(openapi)

config_path = Path(
    "apps/api/src/main/java/com/wallstreetreceipts/api/config/"
    "FilingHistoryCollectionConfiguration.java"
)
config = normalized_bytes(config_path)
config_import = (
    b"import com.wallstreetreceipts.api.application.filinghistory."
    b"SecFilingHistoryManifestAuditQueryService;\n"
)
config_bean = b"""    @Bean\n    SecFilingHistoryManifestAuditQueryService\n            secFilingHistoryManifestAuditQueryService(\n                    FilingHistoryCollectionManifestRepository manifestRepository) {\n        return new SecFilingHistoryManifestAuditQueryService(manifestRepository);\n    }\n\n"""
require(
    config.count(config_import) == 1 and config.count(config_bean) == 1,
    "ADR-052 configuration delta changed",
)
config_path.write_bytes(
    config.replace(config_import, b"", 1).replace(config_bean, b"", 1)
)

rejection_handler_path = Path(
    "apps/api/src/main/java/com/wallstreetreceipts/api/web/security/"
    "ApiRequestRejectedHandler.java"
)
rejection_handler = normalized_bytes(rejection_handler_path)
audit_prefix = b"""    private static final String MANIFEST_AUDIT_API_PREFIX =\n            "/v1/sec/filing-history/manifests/";\n"""
audit_branch = b"        if (requiresNoStore(request)) {\n"
operator_branch = b"        if (isOperatorApiRequest(request)) {\n"
current_predicate = b"""    private static boolean requiresNoStore(HttpServletRequest request) {\n        String requestUri = request.getRequestURI();\n        return requestUri != null\n                && (requestUri.startsWith(OPERATOR_API_PREFIX)\n                || requestUri.startsWith(MANIFEST_AUDIT_API_PREFIX));\n    }\n"""
historical_predicate = b"""    private static boolean isOperatorApiRequest(HttpServletRequest request) {\n        String requestUri = request.getRequestURI();\n        return requestUri != null && requestUri.startsWith(OPERATOR_API_PREFIX);\n    }\n"""
require(
    rejection_handler.count(audit_prefix) == 1
    and rejection_handler.count(audit_branch) == 1
    and rejection_handler.count(current_predicate) == 1,
    "ADR-052 request-rejection no-store delta changed",
)
rejection_handler_path.write_bytes(
    rejection_handler.replace(audit_prefix, b"", 1)
    .replace(audit_branch, operator_branch, 1)
    .replace(current_predicate, historical_predicate, 1)
)

postgres_test_path = Path(
    "apps/api/src/test/java/com/wallstreetreceipts/api/migration/"
    "FilingHistoryCollectionManifestPostgreSqlTest.java"
)
postgres_test = normalized_bytes(postgres_test_path)
for added_import in (
    b"import static org.assertj.core.api.Assertions.assertThatThrownBy;\n",
    b"import com.wallstreetreceipts.api.application.filinghistory.SecFilingHistoryManifestAuditNotFoundException;\n",
    b"import com.wallstreetreceipts.api.application.filinghistory.SecFilingHistoryManifestAuditQueryService;\n",
):
    require(
        postgres_test.count(added_import) == 1,
        "ADR-052 PostgreSQL import delta changed",
    )
    postgres_test = postgres_test.replace(added_import, b"", 1)
postgres_method_start = (
    b"    @Test\n"
    b"    void exactAuditQueryUsesTheInclusivePostgreSqlMicrosecondVisibilityBoundary() {"
)
postgres_next_method = (
    b"    @Test\n"
    b"    void concurrentIdenticalSelectionReturnsOneWinnerObservationToBothCallers()"
)
require(
    postgres_test.count(postgres_method_start) == 1
    and postgres_test.count(postgres_next_method) == 1,
    "ADR-052 PostgreSQL method delta changed",
)
method_start = postgres_test.index(postgres_method_start)
method_end = postgres_test.index(postgres_next_method, method_start)
postgres_test_path.write_bytes(
    postgres_test[:method_start] + postgres_test[method_end:]
)

historical_hashes = {
    openapi_path:
        "f103489524ac14b0078a2e324dfded58395ec0fede1cf38f24f2c224bdf0bd8d",
    config_path:
        "f1093c2c43b63286a6f590aa655c3aae3d7768898ebd37a66fa20af80a3519cf",
    rejection_handler_path:
        "bcdfab301e9a8d2f9bb96d4951f206e86e535366d8dbba8c65d8f7691bbadf14",
    postgres_test_path:
        "247e47a164e148311931117cc77662b1ea12ae43bb69a07912eeaf8ec6fbc3e4",
}
require(
    all(not path.exists() for path in added_paths),
    "ADR-052 added file survived historical projection",
)
for path, expected_hash in historical_hashes.items():
    require(
        digest(normalized_bytes(path)) == expected_hash,
        f"ADR-052 historical reverse projection changed: {path}",
    )
print(
    "Projected exact pre-ADR-052 OpenAPI/config/source/test bytes for "
    "historical invariant guards"
)
PYTHON
