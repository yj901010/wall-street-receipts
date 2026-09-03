python <<'PYTHON'
import hashlib
import json
import re
from pathlib import Path

def require(condition, message):
    if not condition:
        raise ValueError(message)

def scrub_java(source, strip_literals):
    output = []
    index = 0
    state = "CODE"
    quote = None
    while index < len(source):
        current = source[index]
        following = source[index + 1] if index + 1 < len(source) else ""
        if state == "CODE":
            if current in {'"', "'"}:
                state = "STRING" if current == '"' else "CHAR"
                quote = current
                output.append(
                    ('""' if current == '"' else "''")
                    if strip_literals else current
                )
                index += 1
            elif current == "/" and following == "/":
                state = "LINE_COMMENT"
                index += 2
            elif current == "/" and following == "*":
                state = "BLOCK_COMMENT"
                index += 2
            else:
                output.append(current)
                index += 1
        elif state in {"STRING", "CHAR"}:
            if current == "\\" and following:
                if not strip_literals:
                    output.extend((current, following))
                index += 2
            elif current == quote:
                if not strip_literals:
                    output.append(current)
                state = "CODE"
                quote = None
                index += 1
            else:
                if not strip_literals:
                    output.append(current)
                index += 1
        elif state == "LINE_COMMENT":
            if current in "\r\n":
                output.append(current)
                state = "CODE"
            index += 1
        elif current == "*" and following == "/":
            state = "CODE"
            index += 2
        else:
            if current in "\r\n":
                output.append(current)
            index += 1
    require(
        state not in {"STRING", "CHAR", "BLOCK_COMMENT"},
        "Java source contains an unterminated lexical token",
    )
    return "".join(output)

def without_comments(source):
    return scrub_java(source, False)

def without_comments_or_strings(source):
    return scrub_java(source, True)

def compact(source):
    return re.sub(r"\s+", "", source)

def java_string_constant(source, name):
    match = re.search(
        rf"private\s+static\s+final\s+String\s+{name}\s*=\s*"
        r"(?P<body>.*?);",
        source,
        flags=re.DOTALL,
    )
    require(match is not None, f"Missing Java string constant {name}")
    tokens = re.findall(r'"(?:\\.|[^"\\])*"', match.group("body"))
    require(tokens, f"Java string constant {name} has no literal bytes")
    return "".join(json.loads(token) for token in tokens)

production_dir = Path(
    "apps/api/src/main/java/com/wallstreetreceipts/api/domain/"
    "outcome/directionalwinreadiness"
)
test_dir = Path(
    "apps/api/src/test/java/com/wallstreetreceipts/api/domain/"
    "outcome/directionalwinreadiness"
)
production_files = {
    "DirectionalWinReadinessPolicyVersion.java",
    "DirectionalWinReadinessRequest.java",
    "DirectionalWinReadinessResolution.java",
    "DirectionalWinReadinessResolver.java",
}
test_files = {"DirectionalWinReadinessResolverGoldenTest.java"}
require(
    {path.name for path in production_dir.glob("*.java")}
    == production_files,
    "Directional-win readiness production package must contain four files",
)
require(
    {path.name for path in test_dir.glob("*.java")} == test_files,
    "Directional-win readiness test package must contain one golden",
)

sources = {
    name: (production_dir / name).read_text(encoding="utf-8")
    for name in production_files
}
for name, source in sources.items():
    require(
        "package com.wallstreetreceipts.api.domain.outcome."
        "directionalwinreadiness;" in source
        and re.search(r"\\u+[0-9a-fA-F]{4}", source) is None
        and '"""' not in source,
        f"Directional-win readiness package or lexical boundary changed: {name}",
    )

policy = sources["DirectionalWinReadinessPolicyVersion.java"]
definition = java_string_constant(policy, "CANONICAL_DEFINITION")
policy_hash = (
    "1eca77c5b4d43de7657281c161a8c50356cd90e1a18c6e9fd7f5b2c0142b7ec7"
)
definition_bytes = definition.encode("utf-8")
require(
    len(definition_bytes) == 2353
    and definition.isascii()
    and definition.startswith(
        '{"policyVersion":'
        '"SUPPLIED_LEAF_DIRECTIONAL_WIN_READINESS_V1",'
    )
    and definition.endswith('"publication":"ABSENT"}')
    and definition == definition.strip()
    and "\n" not in definition
    and "\r" not in definition
    and hashlib.sha256(definition_bytes).hexdigest() == policy_hash
    and java_string_constant(policy, "DEFINITION_HASH") == policy_hash,
    "Directional-win readiness canonical bytes, length, or hash changed",
)
adr = Path(
    "decisions/ADR-022-supplied-leaf-directional-win-readiness.md"
).read_text(encoding="utf-8")
require(
    re.findall(r"```json\r?\n([^\r\n]+)\r?\n```", adr)
    == [definition]
    and re.findall(r"`([a-f0-9]{64})`", adr).count(policy_hash) == 1,
    "ADR-022 canonical definition or digest drifted from Java",
)
for marker in (
    '"requestFields":["policyVersion","sourceResolution"]',
    '"requestPresence":"ALL_FIELDS_NON_NULL"',
    '"resultVariants":{"Settled":["context","sourceResolution"],'
    '"AwaitingEndpoint":["context","sourceResolution"],'
    '"EvidenceUnavailable":["context","sourceResolution"]}',
    '"branchPrecedence":["AssetReturn.Available",'
    '"EXACT_AWAITING_ENDPOINT_CHAIN",'
    '"ALL_OTHER_ASSET_RETURN_UNAVAILABLE"]',
    '"assetReturnReason":"PRICE_PAIR_UNAVAILABLE"',
    '"pricePairReason":"ENDPOINT_PRICE_UNAVAILABLE"',
    '"endpointReason":"ENDPOINT_NOT_REACHED_AS_OF"',
    '"basisAndEndpointUnavailableRule":'
    '"EVIDENCE_UNAVAILABLE_EVEN_WHEN_ENDPOINT_NOT_REACHED"',
    '"sourcePreservation":'
    '"PRESERVE_EXACT_WHOLE_DIRECTIONAL_WIN_ORCHESTRATION_RESOLUTION"',
    '"canonicalOutcomeStatus":"ABSENT"',
    '"dataCompleteClaim":"ABSENT"',
    '"retry":"ABSENT"',
    '"cancellation":"ABSENT"',
    '"scheduling":"ABSENT"',
    '"producerReplay":"ABSENT"',
    '"calculatorInvocation":"ABSENT"',
    '"persistence":"ABSENT"',
    '"publication":"ABSENT"',
):
    require(marker in definition,
            f"Missing directional-win readiness boundary: {marker}")
policy_body = re.search(
    r"enum\s+DirectionalWinReadinessPolicyVersion\s*"
    r"\{(?P<body>.*?)\}",
    without_comments(policy),
    flags=re.DOTALL,
)
require(
    policy_body is not None
    and re.findall(
        r"\bSUPPLIED_LEAF_DIRECTIONAL_WIN_READINESS_V1\b",
        policy_body.group("body").split(";", 1)[0],
    ) == ["SUPPLIED_LEAF_DIRECTIONAL_WIN_READINESS_V1"],
    "Directional-win readiness policy enum changed",
)

executable = {
    name: without_comments_or_strings(source)
    for name, source in sources.items()
}
compact_sources = {
    name: compact(source) for name, source in executable.items()
}
request = compact_sources["DirectionalWinReadinessRequest.java"]
resolution = compact_sources["DirectionalWinReadinessResolution.java"]
resolver = compact_sources["DirectionalWinReadinessResolver.java"]
require(
    "publicrecordDirectionalWinReadinessRequest("
    "DirectionalWinReadinessPolicyVersionpolicyVersion,"
    "DirectionalWinOrchestrationResolutionsourceResolution)" in request
    and request.count("Objects.requireNonNull(") == 2
    and "caseDirectionalWinOrchestrationResolution.Available" in request
    and "caseDirectionalWinOrchestrationResolution.NotApplicable" in request
    and "caseDirectionalWinOrchestrationResolution."
    "AssetReturnUnavailable" in request
    and "DirectionalWinOrchestrationPolicyVersion."
    "SUPPLIED_LEAF_DIRECTIONAL_WIN_ORCHESTRATION_V1" in request
    and "REQUIRED_SOURCE_HASH" in request,
    "Directional-win readiness request or source-policy gate changed",
)
for marker in (
    "permitsDirectionalWinReadinessResolution.Settled,"
    "DirectionalWinReadinessResolution.AwaitingEndpoint,"
    "DirectionalWinReadinessResolution.EvidenceUnavailable",
    "recordResolutionContext("
    "DirectionalWinReadinessPolicyVersionpolicyVersion,"
    "StringpolicyDefinitionHash)",
    "recordSettled(ResolutionContextcontext,"
    "DirectionalWinOrchestrationResolutionsourceResolution)"
    "implementsDirectionalWinReadinessResolution",
    "recordAwaitingEndpoint(ResolutionContextcontext,"
    "DirectionalWinOrchestrationResolutionsourceResolution)"
    "implementsDirectionalWinReadinessResolution",
    "recordEvidenceUnavailable(ResolutionContextcontext,"
    "DirectionalWinOrchestrationResolutionsourceResolution)"
    "implementsDirectionalWinReadinessResolution",
    "DirectionalWinReadinessResolver.Classification.SETTLED",
    "DirectionalWinReadinessResolver.Classification.AWAITING_ENDPOINT",
    "DirectionalWinReadinessResolver.Classification.EVIDENCE_UNAVAILABLE",
    "newDirectionalWinReadinessRequest("
    "context.policyVersion(),sourceResolution)",
    "DirectionalWinReadinessResolver.requireClassification("
    "sourceResolution,expected)",
):
    require(marker in resolution,
            f"Directional-win readiness result changed: {marker}")
require(
    "publicfinalclassDirectionalWinReadinessResolver" in resolver
    and "privateDirectionalWinReadinessResolver(){}" in resolver
    and "publicstaticDirectionalWinReadinessResolutionresolve("
    "DirectionalWinReadinessRequestrequest)" in resolver
    and "enumClassification{SETTLED,AWAITING_ENDPOINT,"
    "EVIDENCE_UNAVAILABLE}" in resolver
    and "caseDirectionalWinOrchestrationResolution.Availableavailable->"
    "available.assetReturnResult()" in resolver
    and "caseDirectionalWinOrchestrationResolution."
    "NotApplicablenotApplicable->notApplicable.assetReturnResult()" in resolver
    and "caseDirectionalWinOrchestrationResolution."
    "AssetReturnUnavailableunavailable->unavailable.assetReturnResult()"
    in resolver
    and "returnswitch(assetReturn){"
    "caseAssetReturnResult.Availableignored->Classification.SETTLED;"
    "caseAssetReturnResult.Unavailableunavailable->"
    "isExactAwaitingEndpointChain(unavailable)?"
    "Classification.AWAITING_ENDPOINT:"
    "Classification.EVIDENCE_UNAVAILABLE;};" in resolver
    and resolver.count(".reason()") == 3
    and resolver.count(".pricePairReason()") == 1
    and resolver.count(".endpointReason()") == 1
    and "AssetReturnResult.UnavailableReason.PRICE_PAIR_UNAVAILABLE"
    in resolver
    and "AssetReturnPricePairResolution.UnavailableReason."
    "ENDPOINT_PRICE_UNAVAILABLE" in resolver
    and "EndpointPriceResolution.UnavailableReason."
    "ENDPOINT_NOT_REACHED_AS_OF" in resolver
    and resolver.index("caseAssetReturnResult.Availableignored")
    < resolver.index("caseAssetReturnResult.Unavailableunavailable"),
    "Directional-win readiness precedence or exact nested chain changed",
)

expected_imports = {
    "DirectionalWinReadinessPolicyVersion.java": {
        "java.nio.charset.StandardCharsets",
    },
    "DirectionalWinReadinessRequest.java": {
        "java.util.Objects",
        "com.wallstreetreceipts.api.domain.outcome."
        "directionalwinorchestration."
        "DirectionalWinOrchestrationPolicyVersion",
        "com.wallstreetreceipts.api.domain.outcome."
        "directionalwinorchestration."
        "DirectionalWinOrchestrationResolution",
    },
    "DirectionalWinReadinessResolution.java": {
        "java.util.Objects",
        "com.wallstreetreceipts.api.domain.outcome."
        "directionalwinorchestration."
        "DirectionalWinOrchestrationResolution",
    },
    "DirectionalWinReadinessResolver.java": {
        "java.util.Objects",
        "com.wallstreetreceipts.api.domain.outcome.assetreturn."
        "AssetReturnResult",
        "com.wallstreetreceipts.api.domain.outcome."
        "directionalwinorchestration."
        "DirectionalWinOrchestrationResolution",
        "com.wallstreetreceipts.api.domain.outcome."
        "directionalwinreadiness."
        "DirectionalWinReadinessResolution.ResolutionContext",
        "com.wallstreetreceipts.api.domain.outcome.observation."
        "EndpointPriceResolution",
        "com.wallstreetreceipts.api.domain.outcome.pricepair."
        "AssetReturnPricePairResolution",
    },
}
forbidden_runtime = re.compile(
    r"\b(?:Clock|UUID|Random|SecureRandom|System|Runtime|Thread|"
    r"Process|ProcessBuilder|ClassLoader)\b|"
    r"\bClass\s*\.\s*forName\s*\(|\.\s*now\s*\(|"
    r"@(?:Component|Service|Repository|Controller)\b"
)
for name, logic in executable.items():
    imports = {
        re.sub(r"\s+", "", imported)
        for imported in re.findall(
            r"^import\s+([^;]+);", logic, flags=re.MULTILINE
        )
    }
    require(
        imports == expected_imports[name]
        and forbidden_runtime.search(logic) is None
        and "double" not in logic
        and "float" not in logic
        and "ObjectMapper" not in logic
        and "HttpClient" not in logic
        and "DataSource" not in logic
        and "org.springframework" not in logic,
        f"Directional-win readiness import/runtime boundary changed: "
        f"{name} {imports}",
    )
for name in (
    "DirectionalWinReadinessPolicyVersion.java",
    "DirectionalWinReadinessRequest.java",
    "DirectionalWinReadinessResolution.java",
):
    require(
        ".reason()" not in executable[name]
        and "UnavailableReason" not in executable[name],
        f"Only the readiness resolver may inspect nested reasons: {name}",
    )
combined_logic = "\n".join(executable.values())
for forbidden in (
    "DirectionalWinOrchestrator", "DirectionalWinOrchestrationRequest",
    "AssetReturnCalculator", "AssetReturnInput",
    "AssetReturnPricePairSelector", "AssetReturnPricePairRequest",
    "EndpointPriceSelector", "EndpointPriceRequest",
    "DirectionalWinCalculator", "DirectionalWinInput",
    "TargetHitCalculator", "TargetHitInput", "CallOutcome",
    "OutcomeEvaluationStatus", "OutcomeReasonCode", "ScoringMethodology",
    "dataComplete", "fingerprint", "retry", "schedule",
    "@Service", "@Repository", "@Controller",
):
    require(
        forbidden not in combined_logic,
        f"Directional-win readiness crosses its boundary: {forbidden}",
    )

api_main_dir = Path("apps/api/src/main/java")
new_paths = {
    (production_dir / name).resolve() for name in production_files
}
new_markers = tuple(name.removesuffix(".java") for name in production_files)
for other_path in api_main_dir.rglob("*.java"):
    if other_path.resolve() in new_paths:
        continue
    other_logic = without_comments_or_strings(
        other_path.read_text(encoding="utf-8")
    )
    require(
        "domain.outcome.directionalwinreadiness" not in other_logic
        and not any(
            re.search(rf"\b{re.escape(marker)}\b", other_logic)
            for marker in new_markers
        ),
        f"Directional-win readiness must remain disconnected: {other_path}",
    )

golden = (
    test_dir / "DirectionalWinReadinessResolverGoldenTest.java"
).read_text(encoding="utf-8")
golden_logic = without_comments_or_strings(golden)
require(
    re.search(r"\\u+[0-9a-fA-F]{4}", golden) is None
    and '"""' not in golden
    and len(re.findall(r"@Test\b", golden_logic)) == 6
    and len(re.findall(r"@ParameterizedTest\b", golden_logic)) == 2
    and len(re.findall(r"@MethodSource\b", golden_logic)) == 2
    and "@Disabled" not in golden_logic
    and "Assumptions" not in golden_logic
    and "assumeTrue" not in golden_logic
    and "assumeFalse" not in golden_logic,
    "Directional-win readiness golden may not hide or skip vectors",
)
for marker in (
    "canonicalDefinitionHasExactBytesHashAdrAndDefensiveReads",
    "exactFileRecordSealedAndDisconnectedSurfacesAreStable",
    "nullRootsAndRequiredFieldsFailClosed",
    "directResultConstructorsShareExactFailClosedClassification",
    "equalButDistinctWholeSourceRecordsReplayEqually",
    "classificationIsIndependentOfLocaleTimezoneAndPriorCalls",
    "everyUnavailableChainIsClassifiedForDirectionalAndNeutralSources",
    "availableReturnSettlesDirectionalAndNeutralSources",
):
    require(marker in golden_logic,
            f"Missing readiness golden coverage: {marker}")
golden_compact = compact(golden_logic)
require(
    "Arrays.stream(AssetReturnPricePairResolution."
    "UnavailableReason.values())" in golden_compact
    and "Arrays.stream(EndpointPriceResolution."
    "UnavailableReason.values())" in golden_compact
    and "Stream.of(AssetReturnPricePairResolution.UnavailableReason."
    "BASIS_AND_ENDPOINT_PRICE_UNAVAILABLE,"
    "AssetReturnPricePairResolution.UnavailableReason."
    "ENDPOINT_PRICE_UNAVAILABLE)" in golden_compact
    and "unavailableVectors().flatMap(vector->Stream.of(" in golden_compact
    and "Arguments.of(vector.label()+" in golden_compact
    and "CallDirection.BULLISH" in golden_logic
    and "CallDirection.NEUTRAL" in golden_logic
    and "OUTPUT_NOT_REPRESENTABLE" in golden_logic
    and "ENDPOINT_NOT_REACHED_AS_OF" in golden_logic
    and "isSameAs(source)" in golden_compact
    and "Locale.setDefault" in golden_logic
    and "TimeZone.setDefault" in golden_logic
    and "finally" in golden_logic
    and policy_hash in golden,
    "Goldens must lock all 55 chains in both source shapes and replay",
)

expected_schemas = {
    "analyst-call-revision.schema.json", "analyst-call.schema.json",
    "call-context.schema.json", "call-outcome.schema.json",
    "event-context.schema.json", "macro-observation.schema.json",
    "macro-snapshot.schema.json", "market-board.schema.json",
    "market-map.schema.json", "market-snapshot.schema.json",
    "market-treemap.schema.json", "scoring-methodology.schema.json",
    "source-document.schema.json", "source-reference.schema.json",
}
expected_fixtures = {
    "analyst-call-revisions.json", "analyst-calls.json",
    "call-contexts.json", "call-outcomes.json", "manifest.json",
    "market-board.json", "market-map-nasdaq100.json", "market-map.json",
    "market-snapshots.json", "market-treemap-nasdaq100.json",
    "market-treemap-sp500.json", "master-data.json", "timeline-nvda.json",
}
require(
    {path.name for path in Path("schemas").glob("*.json")}
    == expected_schemas
    and {path.name for path in Path("fixtures/v1").glob("*.json")}
    == expected_fixtures,
    "Directional-win readiness must preserve schemas and fixtures",
)
fixture_dir = Path("fixtures/v1")
manifest = json.loads(
    (fixture_dir / "manifest.json").read_text(encoding="utf-8")
)
require(
    [entry["path"] for entry in manifest["files"]] == [
        "master-data.json", "analyst-calls.json",
        "analyst-call-revisions.json", "call-outcomes.json",
        "call-contexts.json", "market-snapshots.json", "market-map.json",
        "market-map-nasdaq100.json", "market-treemap-sp500.json",
        "market-treemap-nasdaq100.json", "timeline-nvda.json",
        "market-board.json",
    ],
    "Directional-win readiness must preserve fixture manifest order",
)
outcomes = json.loads(
    (fixture_dir / "call-outcomes.json").read_text(encoding="utf-8")
)
metrics = (
    "assetReturn", "benchmarkReturn", "sectorReturn", "alpha",
    "sectorAlpha", "mfe", "mae", "targetHit", "directionalWin",
    "targetError",
)
require(
    len(outcomes["methodologies"]) == 2
    and all(item["status"] == "MODEL_ONLY"
            for item in outcomes["methodologies"])
    and len(outcomes["outcomes"]) == 4
    and {item["evaluationStatus"] for item in outcomes["outcomes"]}
    == {"PENDING", "INCOMPLETE"}
    and all(not item["dataComplete"] for item in outcomes["outcomes"])
    and all(item[field] is None
            for item in outcomes["outcomes"] for field in metrics),
    "Readiness names must not activate or publish a canonical outcome",
)
openapi = Path("contracts/openapi.yaml").read_text(encoding="utf-8")
require(
    set(re.findall(r"^  (/[^\n]+):\s*$", openapi, re.MULTILINE))
    == {
        "/v1/calls", "/v1/calls/{id}",
        "/v1/calls/{id}/revisions", "/v1/calls/{id}/outcomes",
        "/v1/calls/{id}/context",
    }
    and {path.name for path in Path(
        "apps/api/src/main/resources/db/migration"
    ).glob("*.sql")} == {
        "V1__baseline.sql", "V2__analyst_calls.sql",
        "V3__analyst_call_revisions.sql", "V4__call_outcomes.sql",
        "V5__call_contexts.sql",
        "V6__sec_filing_catalog_captures.sql",
        "V7__sec_historical_filing_segment_captures.sql",
        "V8__sec_filing_history_collection_manifests.sql",
        "V9__sec_filing_collection_attempts.sql",
    },
    "Directional-win readiness must preserve OpenAPI and Flyway",
)
for resource_path in Path("apps/api/src/test/resources").rglob("*.json"):
    resource_logic = resource_path.read_text(encoding="utf-8")
    require(
        not any(marker in resource_logic for marker in new_markers),
        f"Readiness must not add JSON goldens: {resource_path}",
    )
for web_path in Path("apps/web/src").rglob("*"):
    if web_path.is_file() and web_path.suffix in {
        ".ts", ".tsx", ".js", ".jsx"
    }:
        web_source = web_path.read_text(encoding="utf-8")
        require(
            "domain.outcome.directionalwinreadiness" not in web_source
            and not any(marker in web_source for marker in new_markers),
            f"Directional-win readiness must not expand web: {web_path}",
        )

ownership_adr_path = Path(
    "decisions/ADR-025-shared-asset-return-directional-win-"
    "readiness-ownership.md"
)
ownership_marker = (
    "ADR-022 remains the sole shared receipt for asset-return and "
    "directional-win readiness."
)
ownership_doc_paths = (
    ownership_adr_path,
    Path("README.md"),
    Path("quality/P3_ACCEPTANCE.md"),
    Path("IMPLEMENTATION_LOG.md"),
)
ownership_docs = {}
for doc_path in ownership_doc_paths:
    require(doc_path.is_file(), f"Missing ADR-025 ownership doc: {doc_path}")
    doc_source = doc_path.read_text(encoding="utf-8")
    ownership_docs[doc_path] = doc_source
    require(
        doc_source.count(ownership_marker) == 1
        and "ADR-025" in doc_source,
        f"ADR-025 ownership marker must occur exactly once: {doc_path}",
    )
    normalized_doc = re.sub(r"\s+", " ", doc_source)
    require(
        "whether ADR-022 is the combined" not in normalized_doc
        and "whether a separate asset-return receipt"
        not in normalized_doc
        and "separate asset-return receipt is required"
        not in normalized_doc
        and "separate asset-return receipt is needed"
        not in normalized_doc,
        f"Removed asset-return readiness question returned: {doc_path}",
    )

ownership_adr = ownership_docs[ownership_adr_path]
for marker in (
    "- Status: Accepted",
    "`DirectionalWinReadinessResolution` supplied by ADR-022 owns both",
    "No standalone `assetreturnreadiness` package, policy, request,",
    "2353-byte ASCII/UTF-8",
    policy_hash,
    "ten metric meanings",
    "nine readiness ownership inputs",
    "No ADR-022 variant maps directly to `OutcomeEvaluationStatus`.",
    "`dataComplete=true`",
    "`CallOutcome`",
    "ADR-025 adds no Java production file, test, policy enum",
):
    require(marker in ownership_adr,
            f"Missing ADR-025 ownership boundary: {marker}")

competing_type = re.compile(r"\bAssetReturnReadiness\w*\b")
competing_policy = re.compile(
    r"\b[A-Z0-9_]*ASSET_RETURN_READINESS[A-Z0-9_]*\b"
)
source_text_suffixes = {
    ".java", ".json", ".md", ".properties", ".sql", ".txt",
    ".xml", ".yaml", ".yml",
}
for source_root in (
    Path("apps/api/src/main"),
    Path("apps/api/src/test"),
):
    for source_path in source_root.rglob("*"):
        if not source_path.is_file():
            continue
        normalized_path = re.sub(
            r"[^a-z0-9]", "", source_path.as_posix().lower()
        )
        require(
            "assetreturnreadiness" not in normalized_path,
            f"Standalone asset-return readiness path is forbidden: "
            f"{source_path}",
        )
        if source_path.suffix.lower() in source_text_suffixes:
            source = source_path.read_text(encoding="utf-8")
            if source_path.suffix.lower() == ".java":
                source = without_comments(source)
            require(
                competing_type.search(source) is None
                and competing_policy.search(source) is None,
                f"Standalone asset-return readiness type or policy is "
                f"forbidden: {source_path}",
            )

for decision_path in Path("decisions").glob("ADR-*.md"):
    if decision_path == ownership_adr_path:
        continue
    normalized_name = re.sub(
        r"[^a-z0-9]", "", decision_path.name.lower()
    )
    decision_source = decision_path.read_text(encoding="utf-8")
    require(
        not (
            "assetreturn" in normalized_name
            and "readiness" in normalized_name
        )
        and competing_type.search(decision_source) is None
        and competing_policy.search(decision_source) is None,
        f"ADR-025 forbids a competing asset-return readiness ADR or "
        f"policy: {decision_path}",
    )

print(
    "Validated ADR-022 as the sole shared asset-return/directional-win "
    "readiness receipt, its exact endpoint-only temporal chain and "
    "whole-source preservation, no competing receipt, and no canonical "
    "lifecycle publication"
)
PYTHON
