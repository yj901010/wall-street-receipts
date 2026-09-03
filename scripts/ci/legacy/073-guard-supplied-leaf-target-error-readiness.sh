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
    "outcome/targeterrorreadiness"
)
test_dir = Path(
    "apps/api/src/test/java/com/wallstreetreceipts/api/domain/"
    "outcome/targeterrorreadiness"
)
production_files = {
    "TargetErrorReadinessPolicyVersion.java",
    "TargetErrorReadinessRequest.java",
    "TargetErrorReadinessResolution.java",
    "TargetErrorReadinessResolver.java",
}
test_files = {"TargetErrorReadinessResolverGoldenTest.java"}
require(
    {path.name for path in production_dir.glob("*.java")}
    == production_files,
    "Target-error readiness production package must contain four files",
)
require(
    {path.name for path in test_dir.glob("*.java")} == test_files,
    "Target-error readiness test package must contain one golden",
)

sources = {
    name: (production_dir / name).read_text(encoding="utf-8")
    for name in production_files
}
for name, source in sources.items():
    require(
        "package com.wallstreetreceipts.api.domain.outcome."
        "targeterrorreadiness;" in source
        and re.search(r"\\u+[0-9a-fA-F]{4}", source) is None
        and '"""' not in source,
        f"Target-error readiness package or lexical boundary changed: {name}",
    )

policy = sources["TargetErrorReadinessPolicyVersion.java"]
definition = java_string_constant(policy, "CANONICAL_DEFINITION")
policy_hash = (
    "0b8bfb22dccd4a494f568c44d06163f73af36462cf929bc83cf238019811c44a"
)
definition_bytes = definition.encode("utf-8")
require(
    len(definition_bytes) == 1979
    and definition.isascii()
    and definition.startswith(
        '{"policyVersion":'
        '"SUPPLIED_LEAF_TARGET_ERROR_READINESS_V1",'
    )
    and definition.endswith('"publication":"ABSENT"}')
    and definition == definition.strip()
    and "\n" not in definition
    and "\r" not in definition
    and hashlib.sha256(definition_bytes).hexdigest() == policy_hash
    and java_string_constant(policy, "DEFINITION_HASH") == policy_hash,
    "Target-error readiness canonical bytes, length, or hash changed",
)
adr = Path(
    "decisions/ADR-023-supplied-leaf-target-error-readiness.md"
).read_text(encoding="utf-8")
require(
    re.findall(r"```json\r?\n([^\r\n]+)\r?\n```", adr)
    == [definition]
    and re.findall(r"`([a-f0-9]{64})`", adr).count(policy_hash) == 1,
    "ADR-023 canonical definition or digest drifted from Java",
)
for marker in (
    '"requestFields":["policyVersion","sourceResult"]',
    '"requestPresence":"ALL_FIELDS_NON_NULL"',
    '"resultVariants":{"Settled":["context","sourceResult"],'
    '"AwaitingEndpoint":["context","sourceResult"],'
    '"EvidenceUnavailable":["context","sourceResult"]}',
    '"branchPrecedence":["TargetErrorResult.Available",'
    '"EXACT_AWAITING_ENDPOINT_CHAIN",'
    '"ALL_OTHER_TARGET_ERROR_UNAVAILABLE"]',
    '"targetErrorReason":"ENDPOINT_PRICE_UNAVAILABLE"',
    '"endpointReason":"ENDPOINT_NOT_REACHED_AS_OF"',
    '"targetAndEndpointUnavailableRule":'
    '"EVIDENCE_UNAVAILABLE_EVEN_WHEN_ENDPOINT_NOT_REACHED"',
    '"sourcePreservation":"PRESERVE_EXACT_WHOLE_TARGET_ERROR_RESULT"',
    '"canonicalOutcomeStatus":"ABSENT"',
    '"dataCompleteClaim":"ABSENT"',
    '"retry":"ABSENT"',
    '"freshness":"ABSENT"',
    '"cancellation":"ABSENT"',
    '"scheduling":"ABSENT"',
    '"producerReplay":"ABSENT"',
    '"calculatorInvocation":"ABSENT"',
    '"persistence":"ABSENT"',
    '"publication":"ABSENT"',
):
    require(marker in definition,
            f"Missing target-error readiness boundary: {marker}")
policy_body = re.search(
    r"enum\s+TargetErrorReadinessPolicyVersion\s*"
    r"\{(?P<body>.*?)\}",
    without_comments(policy),
    flags=re.DOTALL,
)
require(
    policy_body is not None
    and re.findall(
        r"\bSUPPLIED_LEAF_TARGET_ERROR_READINESS_V1\b",
        policy_body.group("body").split(";", 1)[0],
    ) == ["SUPPLIED_LEAF_TARGET_ERROR_READINESS_V1"],
    "Target-error readiness policy enum changed",
)

executable = {
    name: without_comments_or_strings(source)
    for name, source in sources.items()
}
compact_sources = {
    name: compact(source) for name, source in executable.items()
}
request = compact_sources["TargetErrorReadinessRequest.java"]
resolution = compact_sources["TargetErrorReadinessResolution.java"]
resolver = compact_sources["TargetErrorReadinessResolver.java"]
require(
    "publicrecordTargetErrorReadinessRequest("
    "TargetErrorReadinessPolicyVersionpolicyVersion,"
    "TargetErrorResultsourceResult)" in request
    and request.count("Objects.requireNonNull(") == 2
    and "caseTargetErrorResult.Availableavailable" in request
    and "caseTargetErrorResult.Unavailableunavailable" in request
    and "TargetErrorPolicyVersion."
    "ACTUAL_DENOMINATOR_SCALE_12_HALF_EVEN_V1" in request
    and "REQUIRED_SOURCE_HASH" in request,
    "Target-error readiness request or source-policy gate changed",
)
for marker in (
    "permitsTargetErrorReadinessResolution.Settled,"
    "TargetErrorReadinessResolution.AwaitingEndpoint,"
    "TargetErrorReadinessResolution.EvidenceUnavailable",
    "recordResolutionContext("
    "TargetErrorReadinessPolicyVersionpolicyVersion,"
    "StringpolicyDefinitionHash)",
    "recordSettled(ResolutionContextcontext,"
    "TargetErrorResultsourceResult)"
    "implementsTargetErrorReadinessResolution",
    "recordAwaitingEndpoint(ResolutionContextcontext,"
    "TargetErrorResultsourceResult)"
    "implementsTargetErrorReadinessResolution",
    "recordEvidenceUnavailable(ResolutionContextcontext,"
    "TargetErrorResultsourceResult)"
    "implementsTargetErrorReadinessResolution",
    "TargetErrorReadinessResolver.Classification.SETTLED",
    "TargetErrorReadinessResolver.Classification.AWAITING_ENDPOINT",
    "TargetErrorReadinessResolver.Classification.EVIDENCE_UNAVAILABLE",
    "newTargetErrorReadinessRequest("
    "context.policyVersion(),sourceResult)",
    "TargetErrorReadinessResolver.requireClassification("
    "sourceResult,expected)",
):
    require(marker in resolution,
            f"Target-error readiness result changed: {marker}")
require(
    "publicfinalclassTargetErrorReadinessResolver" in resolver
    and "privateTargetErrorReadinessResolver(){}" in resolver
    and "publicstaticTargetErrorReadinessResolutionresolve("
    "TargetErrorReadinessRequestrequest)" in resolver
    and "enumClassification{SETTLED,AWAITING_ENDPOINT,"
    "EVIDENCE_UNAVAILABLE}" in resolver
    and "returnswitch(sourceResult){"
    "caseTargetErrorResult.Availableignored->Classification.SETTLED;"
    "caseTargetErrorResult.Unavailableunavailable->"
    "isExactAwaitingEndpointChain(unavailable)?"
    "Classification.AWAITING_ENDPOINT:"
    "Classification.EVIDENCE_UNAVAILABLE;};" in resolver
    and resolver.count(".reason()") == 2
    and resolver.count(".endpointReason()") == 1
    and "TargetErrorResult.UnavailableReason."
    "ENDPOINT_PRICE_UNAVAILABLE" in resolver
    and "EndpointPriceResolution.UnavailableReason."
    "ENDPOINT_NOT_REACHED_AS_OF" in resolver
    and "unavailable.context().endpointPriceResolution()"
    in resolver
    and "instanceofEndpointPriceResolution.Unavailableendpoint"
    in resolver
    and "TARGET_AND_ENDPOINT_PRICE_UNAVAILABLE" not in resolver
    and resolver.index("caseTargetErrorResult.Availableignored")
    < resolver.index("caseTargetErrorResult.Unavailableunavailable"),
    "Target-error readiness precedence or exact nested chain changed",
)

expected_imports = {
    "TargetErrorReadinessPolicyVersion.java": {
        "java.nio.charset.StandardCharsets",
    },
    "TargetErrorReadinessRequest.java": {
        "java.util.Objects",
        "com.wallstreetreceipts.api.domain.outcome.targeterror."
        "TargetErrorPolicyVersion",
        "com.wallstreetreceipts.api.domain.outcome.targeterror."
        "TargetErrorResult",
    },
    "TargetErrorReadinessResolution.java": {
        "java.util.Objects",
        "com.wallstreetreceipts.api.domain.outcome.targeterror."
        "TargetErrorResult",
    },
    "TargetErrorReadinessResolver.java": {
        "java.util.Objects",
        "com.wallstreetreceipts.api.domain.outcome.observation."
        "EndpointPriceResolution",
        "com.wallstreetreceipts.api.domain.outcome.targeterror."
        "TargetErrorResult",
        "com.wallstreetreceipts.api.domain.outcome."
        "targeterrorreadiness."
        "TargetErrorReadinessResolution.ResolutionContext",
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
        f"Target-error readiness import/runtime boundary changed: "
        f"{name} {imports}",
    )
for name in (
    "TargetErrorReadinessPolicyVersion.java",
    "TargetErrorReadinessRequest.java",
    "TargetErrorReadinessResolution.java",
):
    require(
        ".reason()" not in executable[name]
        and "UnavailableReason" not in executable[name],
        f"Only the readiness resolver may inspect nested reasons: {name}",
    )
combined_logic = "\n".join(executable.values())
for forbidden in (
    "TargetErrorCalculator", "TargetErrorInput",
    "EndpointPriceSelector", "EndpointPriceRequest",
    "TargetHitCalculator", "TargetHitInput",
    "DirectionalWinCalculator", "DirectionalWinInput",
    "CallOutcome", "OutcomeEvaluationStatus", "OutcomeReasonCode",
    "ScoringMethodology", "dataComplete", "fingerprint", "retry",
    "freshness", "schedule", "@Service", "@Repository", "@Controller",
):
    require(
        forbidden not in combined_logic,
        f"Target-error readiness crosses its boundary: {forbidden}",
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
        "domain.outcome.targeterrorreadiness" not in other_logic
        and not any(
            re.search(rf"\b{re.escape(marker)}\b", other_logic)
            for marker in new_markers
        ),
        f"Target-error readiness must remain disconnected: {other_path}",
    )

golden = (
    test_dir / "TargetErrorReadinessResolverGoldenTest.java"
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
    "Target-error readiness golden may not hide or skip vectors",
)
for marker in (
    "canonicalDefinitionHasExactBytesHashAdrAndDefensiveReads",
    "exactFileRecordSealedAndDisconnectedSurfacesAreStable",
    "nullRootsAndRequiredFieldsFailClosed",
    "directResultConstructorsShareExactFailClosedClassification",
    "equalButDistinctWholeSourceRecordsReplayEqually",
    "classificationIsIndependentOfLocaleTimezoneAndPriorCalls",
    "everyConstructibleUnavailableShapeIsClassified",
    "availableTargetErrorSettles",
):
    require(marker in golden_logic,
            f"Missing readiness golden coverage: {marker}")
golden_compact = compact(golden_logic)
require(
    "Arrays.stream(TargetErrorResult.UnavailableReason.values())"
    in golden_compact
    and ".filter(reason->!carriesEndpointReason(reason))"
    in golden_compact
    and "Stream.of(TargetErrorResult.UnavailableReason."
    "ENDPOINT_PRICE_UNAVAILABLE,TargetErrorResult.UnavailableReason."
    "TARGET_AND_ENDPOINT_PRICE_UNAVAILABLE)" in golden_compact
    and ".flatMap(reason->Arrays.stream(EndpointPriceResolution."
    "UnavailableReason.values())" in golden_compact
    and "unavailableVectors().count()).isEqualTo(39)" in golden_compact
    and "filter(UnavailableVector::awaiting).count()).isEqualTo(1)"
    in golden_compact
    and "ENDPOINT_NOT_REACHED_AS_OF" in golden_logic
    and "isSameAs(source)" in golden_compact
    and "Locale.setDefault" in golden_logic
    and "TimeZone.setDefault" in golden_logic
    and "finally" in golden_logic
    and policy_hash in golden,
    "Goldens must lock all 39 unavailable shapes and one settled shape",
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
    "market-board.json", "market-map-nasdaq100.json",
    "market-map.json", "market-snapshots.json",
    "market-treemap-nasdaq100.json", "market-treemap-sp500.json",
    "master-data.json", "timeline-nvda.json",
}
require(
    {path.name for path in Path("schemas").glob("*.json")}
    == expected_schemas
    and {path.name for path in Path("fixtures/v1").glob("*.json")}
    == expected_fixtures,
    "Target-error readiness must preserve schemas and fixtures",
)
fixture_dir = Path("fixtures/v1")
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
    "Target-error readiness must preserve OpenAPI and Flyway",
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
            "domain.outcome.targeterrorreadiness" not in web_source
            and not any(marker in web_source for marker in new_markers),
            f"Target-error readiness must not expand web: {web_path}",
        )

print(
    "Validated exact supplied-leaf target-error readiness, the one "
    "endpoint-only temporal chain, missing-target compound firewall, "
    "whole-source preservation, and no canonical lifecycle publication"
)
PYTHON
