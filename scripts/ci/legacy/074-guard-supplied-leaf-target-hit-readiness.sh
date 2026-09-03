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
    "outcome/targethitreadiness"
)
test_dir = Path(
    "apps/api/src/test/java/com/wallstreetreceipts/api/domain/"
    "outcome/targethitreadiness"
)
production_files = {
    "TargetHitReadinessPolicyVersion.java",
    "TargetHitReadinessRequest.java",
    "TargetHitReadinessResolution.java",
    "TargetHitReadinessResolver.java",
}
test_files = {"TargetHitReadinessResolverGoldenTest.java"}
require(
    {path.name for path in production_dir.glob("*.java")}
    == production_files,
    "Target-hit readiness production package must contain four files",
)
require(
    {path.name for path in test_dir.glob("*.java")} == test_files,
    "Target-hit readiness test package must contain one golden",
)

sources = {
    name: (production_dir / name).read_text(encoding="utf-8")
    for name in production_files
}
for name, source in sources.items():
    require(
        "package com.wallstreetreceipts.api.domain.outcome."
        "targethitreadiness;" in source
        and re.search(r"\\u+[0-9a-fA-F]{4}", source) is None
        and '"""' not in source,
        f"Target-hit readiness package or lexical boundary changed: {name}",
    )

policy = sources["TargetHitReadinessPolicyVersion.java"]
definition = java_string_constant(policy, "CANONICAL_DEFINITION")
policy_hash = (
    "8f81dee5227370d82dd91cd2fb8448797c7028eaa485dc64cf4bdc3cbf2f31a3"
)
definition_bytes = definition.encode("utf-8")
require(
    len(definition_bytes) == 2042
    and definition.isascii()
    and definition.startswith(
        '{"policyVersion":'
        '"SUPPLIED_LEAF_TARGET_HIT_READINESS_V1",'
    )
    and definition.endswith('"publication":"ABSENT"}')
    and definition == definition.strip()
    and "\n" not in definition
    and "\r" not in definition
    and hashlib.sha256(definition_bytes).hexdigest() == policy_hash
    and java_string_constant(policy, "DEFINITION_HASH") == policy_hash,
    "Target-hit readiness canonical bytes, length, or hash changed",
)
adr = Path(
    "decisions/ADR-024-supplied-leaf-target-hit-readiness.md"
).read_text(encoding="utf-8")
require(
    re.findall(r"```json\r?\n([^\r\n]+)\r?\n```", adr)
    == [definition]
    and re.findall(r"`([a-f0-9]{64})`", adr).count(policy_hash) == 1,
    "ADR-024 canonical definition or digest drifted from Java",
)
for marker in (
    '"requiredTargetHitOrchestrationPolicyVersion":'
    '"POINT_IN_TIME_TARGET_HIT_ORCHESTRATION_V1"',
    '"requiredTargetHitOrchestrationPolicyDefinitionHash":'
    '"b91bf68958e42ad003b80973c74f9acc2dad8e4629f6a1905798df98aa8b5348"',
    '"requestFields":["policyVersion","sourceResult"]',
    '"requestPresence":"ALL_FIELDS_NON_NULL"',
    '"resultVariants":{"Settled":["context","sourceResult"],'
    '"AwaitingEndpoint":["context","sourceResult"],'
    '"EvidenceUnavailable":["context","sourceResult"]}',
    '"branchPrecedence":["Available","NotApplicable","Pending",'
    '"EligibilityUnavailable","FavorableExtremeUnavailable"]',
    '"branchMapping":{"Available":"SETTLED",'
    '"NotApplicable":"SETTLED","Pending":"AWAITING_ENDPOINT",'
    '"EligibilityUnavailable":"EVIDENCE_UNAVAILABLE",'
    '"FavorableExtremeUnavailable":"EVIDENCE_UNAVAILABLE"}',
    '"eligibilityPendingReason":"HORIZON_NOT_REACHED_AS_OF"',
    '"settledRule":'
    '"TARGET_HIT_AVAILABLE_OR_PERMANENTLY_NOT_APPLICABLE"',
    '"sourcePreservation":'
    '"PRESERVE_EXACT_WHOLE_TARGET_HIT_ORCHESTRATION_RESOLUTION"',
    '"canonicalOutcomeStatus":"ABSENT"',
    '"dataCompleteClaim":"ABSENT"',
    '"retry":"ABSENT"',
    '"freshness":"ABSENT"',
    '"cancellation":"ABSENT"',
    '"scheduling":"ABSENT"',
    '"producerReplay":"ABSENT"',
    '"selectorInvocation":"ABSENT"',
    '"calculatorInvocation":"ABSENT"',
    '"persistence":"ABSENT"',
    '"publication":"ABSENT"',
):
    require(marker in definition,
            f"Missing target-hit readiness boundary: {marker}")
policy_body = re.search(
    r"enum\s+TargetHitReadinessPolicyVersion\s*"
    r"\{(?P<body>.*?)\}",
    without_comments(policy),
    flags=re.DOTALL,
)
require(
    policy_body is not None
    and re.findall(
        r"\bSUPPLIED_LEAF_TARGET_HIT_READINESS_V1\b",
        policy_body.group("body").split(";", 1)[0],
    ) == ["SUPPLIED_LEAF_TARGET_HIT_READINESS_V1"],
    "Target-hit readiness policy enum changed",
)

executable = {
    name: without_comments_or_strings(source)
    for name, source in sources.items()
}
compact_sources = {
    name: compact(source) for name, source in executable.items()
}
request = compact_sources["TargetHitReadinessRequest.java"]
resolution = compact_sources["TargetHitReadinessResolution.java"]
resolver = compact_sources["TargetHitReadinessResolver.java"]
require(
    "publicrecordTargetHitReadinessRequest("
    "TargetHitReadinessPolicyVersionpolicyVersion,"
    "TargetHitOrchestrationResolutionsourceResult)" in request
    and request.count("Objects.requireNonNull(") == 2
    and "caseTargetHitOrchestrationResolution.Availableavailable"
    in request
    and "caseTargetHitOrchestrationResolution.Pendingpending" in request
    and "caseTargetHitOrchestrationResolution.NotApplicablenotApplicable"
    in request
    and "caseTargetHitOrchestrationResolution."
    "EligibilityUnavailableunavailable" in request
    and "caseTargetHitOrchestrationResolution."
    "FavorableExtremeUnavailableunavailable" in request
    and "TargetHitOrchestrationPolicyVersion."
    "POINT_IN_TIME_TARGET_HIT_ORCHESTRATION_V1" in request
    and "REQUIRED_SOURCE_HASH" in request,
    "Target-hit readiness request or source-policy gate changed",
)
for marker in (
    "permitsTargetHitReadinessResolution.Settled,"
    "TargetHitReadinessResolution.AwaitingEndpoint,"
    "TargetHitReadinessResolution.EvidenceUnavailable",
    "recordResolutionContext("
    "TargetHitReadinessPolicyVersionpolicyVersion,"
    "StringpolicyDefinitionHash)",
    "recordSettled(ResolutionContextcontext,"
    "TargetHitOrchestrationResolutionsourceResult)"
    "implementsTargetHitReadinessResolution",
    "recordAwaitingEndpoint(ResolutionContextcontext,"
    "TargetHitOrchestrationResolutionsourceResult)"
    "implementsTargetHitReadinessResolution",
    "recordEvidenceUnavailable(ResolutionContextcontext,"
    "TargetHitOrchestrationResolutionsourceResult)"
    "implementsTargetHitReadinessResolution",
    "TargetHitReadinessResolver.Classification.SETTLED",
    "TargetHitReadinessResolver.Classification.AWAITING_ENDPOINT",
    "TargetHitReadinessResolver.Classification.EVIDENCE_UNAVAILABLE",
    "newTargetHitReadinessRequest("
    "context.policyVersion(),sourceResult)",
    "TargetHitReadinessResolver.requireClassification("
    "sourceResult,expected)",
):
    require(marker in resolution,
            f"Target-hit readiness result changed: {marker}")
require(
    "publicfinalclassTargetHitReadinessResolver" in resolver
    and "privateTargetHitReadinessResolver(){}" in resolver
    and "publicstaticTargetHitReadinessResolutionresolve("
    "TargetHitReadinessRequestrequest)" in resolver
    and "enumClassification{SETTLED,AWAITING_ENDPOINT,"
    "EVIDENCE_UNAVAILABLE}" in resolver
    and "returnswitch(sourceResult){"
    "caseTargetHitOrchestrationResolution.Availableignored->"
    "Classification.SETTLED;"
    "caseTargetHitOrchestrationResolution.NotApplicableignored->"
    "Classification.SETTLED;"
    "caseTargetHitOrchestrationResolution.Pendingignored->"
    "Classification.AWAITING_ENDPOINT;"
    "caseTargetHitOrchestrationResolution.EligibilityUnavailableignored->"
    "Classification.EVIDENCE_UNAVAILABLE;"
    "caseTargetHitOrchestrationResolution."
    "FavorableExtremeUnavailableignored->"
    "Classification.EVIDENCE_UNAVAILABLE;}" in resolver,
    "Target-hit readiness typed-variant mapping changed",
)

expected_imports = {
    "TargetHitReadinessPolicyVersion.java": {
        "java.nio.charset.StandardCharsets",
    },
    "TargetHitReadinessRequest.java": {
        "java.util.Objects",
        "com.wallstreetreceipts.api.domain.outcome."
        "targethitorchestration.TargetHitOrchestrationPolicyVersion",
        "com.wallstreetreceipts.api.domain.outcome."
        "targethitorchestration.TargetHitOrchestrationResolution",
    },
    "TargetHitReadinessResolution.java": {
        "java.util.Objects",
        "com.wallstreetreceipts.api.domain.outcome."
        "targethitorchestration.TargetHitOrchestrationResolution",
    },
    "TargetHitReadinessResolver.java": {
        "java.util.Objects",
        "com.wallstreetreceipts.api.domain.outcome."
        "targethitorchestration.TargetHitOrchestrationResolution",
        "com.wallstreetreceipts.api.domain.outcome.targethitreadiness."
        "TargetHitReadinessResolution.ResolutionContext",
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
        f"Target-hit readiness import/runtime boundary changed: "
        f"{name} {imports}",
    )
combined_logic = "\n".join(executable.values())
for forbidden in (
    "TargetHitOrchestrator", "TargetHitOrchestrationRequest",
    "TargetHitCalculator", "TargetHitInput",
    "TargetEligibilityResolver", "TargetEligibilityRequest",
    "FavorableExtremeSelector", "FavorableExtremeRequest",
    "FullWindowHighLowObservation", "WindowPriceBinding",
    "CallOutcome", "OutcomeEvaluationStatus", "OutcomeReasonCode",
    "ScoringMethodology", "dataComplete", "fingerprint", "retry",
    "freshness", "schedule", ".reason()", "UnavailableReason",
    "@Service", "@Repository", "@Controller",
):
    require(
        forbidden not in combined_logic,
        f"Target-hit readiness crosses its boundary: {forbidden}",
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
        "domain.outcome.targethitreadiness" not in other_logic
        and not any(
            re.search(rf"\b{re.escape(marker)}\b", other_logic)
            for marker in new_markers
        ),
        f"Target-hit readiness must remain disconnected: {other_path}",
    )

golden = (
    test_dir / "TargetHitReadinessResolverGoldenTest.java"
).read_text(encoding="utf-8")
golden_logic = without_comments_or_strings(golden)
require(
    re.search(r"\\u+[0-9a-fA-F]{4}", golden) is None
    and '"""' not in golden
    and len(re.findall(r"@Test\b", golden_logic)) == 6
    and len(re.findall(r"@ParameterizedTest\b", golden_logic)) == 1
    and len(re.findall(r"@MethodSource\b", golden_logic)) == 1
    and "@Disabled" not in golden_logic
    and "Assumptions" not in golden_logic
    and "assumeTrue" not in golden_logic
    and "assumeFalse" not in golden_logic,
    "Target-hit readiness golden may not hide or skip vectors",
)
for marker in (
    "canonicalDefinitionHasExactBytesHashAdrAndDefensiveReads",
    "exactFileRecordSealedAndDisconnectedSurfacesAreStable",
    "nullRootsAndRequiredSourceShapeFailClosed",
    "directResultConstructorsShareExactFailClosedClassification",
    "equalButDistinctWholeSourceRecordsReplayEqually",
    "classificationIsIndependentOfLocaleTimezoneAndPriorCalls",
    "everyConstructibleSourceShapeIsClassified",
    "classificationSourceShapes",
    "classificationVectors",
):
    require(marker in golden_logic,
            f"Missing target-hit readiness golden coverage: {marker}")
golden_compact = compact(golden_logic)
require(
    '@MethodSource("classificationSourceShapes")' in golden
    and "assertThat(vectors).hasSize(41)" in golden_compact
    and "TargetEligibilityResolution.NotApplicableReason.values()"
    in golden_compact
    and "TargetEligibilityResolution.UnavailableReason.values()"
    in golden_compact
    and "FavorableExtremeResolution.UnavailableReason.values()"
    in golden_compact
    and "TargetHitReadinessResolution.Settled.class)).hasSize(4)"
    in golden_compact
    and "TargetHitReadinessResolution.AwaitingEndpoint.class)).hasSize(1)"
    in golden_compact
    and "TargetHitReadinessResolution.EvidenceUnavailable.class))"
    ".hasSize(36)" in golden_compact
    and "HORIZON_NOT_REACHED_AS_OF" in golden
    and "OBSERVATION_MISSING_AS_OF" in golden_logic
    and "isSameAs(source)" in golden_compact
    and "Locale.setDefault" in golden_logic
    and "TimeZone.setDefault" in golden_logic
    and "finally" in golden_logic
    and policy_hash in golden,
    "Target-hit readiness golden must preserve sources and replay",
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
    "Target-hit readiness must preserve schemas and fixtures",
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
    "Target-hit readiness must preserve OpenAPI and Flyway",
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
            "domain.outcome.targethitreadiness" not in web_source
            and not any(marker in web_source for marker in new_markers),
            f"Target-hit readiness must not expand web: {web_path}",
        )

print(
    "Validated exact supplied-leaf target-hit readiness, four settled "
    "source shapes, one awaiting endpoint, 36 evidence-unavailable "
    "shapes, whole-source preservation, and no lifecycle publication"
)
PYTHON
