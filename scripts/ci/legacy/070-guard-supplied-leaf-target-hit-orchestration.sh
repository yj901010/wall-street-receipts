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

def enum_values(source, enum_name):
    match = re.search(
        rf"enum\s+{enum_name}\s*\{{(?P<body>.*?)\}}",
        without_comments(source),
        flags=re.DOTALL,
    )
    require(match is not None, f"Missing enum {enum_name}")
    return re.findall(
        r"\b[A-Z][A-Z0-9_]+\b",
        match.group("body").split(";", 1)[0],
    )

production_dir = Path(
    "apps/api/src/main/java/com/wallstreetreceipts/api/domain/"
    "outcome/targethitorchestration"
)
test_dir = Path(
    "apps/api/src/test/java/com/wallstreetreceipts/api/domain/"
    "outcome/targethitorchestration"
)
production_files = {
    "TargetHitOrchestrationPolicyVersion.java",
    "TargetHitOrchestrationRequest.java",
    "TargetHitOrchestrationResolution.java",
    "TargetHitOrchestrator.java",
}
test_files = {"TargetHitOrchestratorGoldenTest.java"}
require(
    {path.name for path in production_dir.glob("*.java")}
    == production_files,
    "Target-hit orchestration package must contain exactly four files",
)
require(
    {path.name for path in test_dir.glob("*.java")} == test_files,
    "Target-hit orchestration test package must contain one golden",
)

sources = {
    name: (production_dir / name).read_text(encoding="utf-8")
    for name in production_files
}
for name, source in sources.items():
    require(
        "package com.wallstreetreceipts.api.domain.outcome."
        "targethitorchestration;" in source
        and re.search(r"\\u+[0-9a-fA-F]{4}", source) is None
        and '"""' not in source,
        f"Target-hit orchestration package or lexical boundary changed: {name}",
    )

policy = sources["TargetHitOrchestrationPolicyVersion.java"]
definition = java_string_constant(policy, "CANONICAL_DEFINITION")
definition_bytes = definition.encode("utf-8")
policy_hash = (
    "b91bf68958e42ad003b80973c74f9acc2dad8e4629f6a1905798df98aa8b5348"
)
require(
    len(definition_bytes) == 3082
    and definition.isascii()
    and definition.startswith(
        '{"policyVersion":"POINT_IN_TIME_TARGET_HIT_ORCHESTRATION_V1",'
    )
    and definition.endswith('"fallbackBehavior":"ABSENT"}')
    and definition == definition.strip()
    and "\n" not in definition
    and "\r" not in definition
    and hashlib.sha256(definition_bytes).hexdigest() == policy_hash
    and java_string_constant(policy, "DEFINITION_HASH") == policy_hash,
    "Target-hit orchestration canonical bytes, length, or hash changed",
)
adr = Path(
    "decisions/ADR-020-supplied-leaf-target-hit-orchestration.md"
).read_text(encoding="utf-8")
adr_text_blocks = re.findall(
    r"```text\r?\n([^\r\n]+)\r?\n```", adr
)
require(
    adr_text_blocks == [definition, policy_hash],
    "ADR-020 canonical definition or digest drifted from Java",
)
for marker in (
    '"requiredEligibilityPolicyDefinitionHash":'
    '"a6b4c9f4e4d29b5f1a9b0c300e2d7b9505318c708dfb0ad0e88f71324cf65465"',
    '"requiredFavorableExtremePolicyDefinitionHash":'
    '"e3a0e93030c8f09ae5398bf6df0f2e28eec14b0a31f5bea240fc78f2412c2463"',
    '"leafAttestationBoundary":"LOCAL_CONSISTENCY_ONLY_'
    'NO_REQUEST_MEMBERSHIP_PIT_FILTERING_OR_SELECTOR_PRODUCTION_CLAIM"',
    '"nonReadyFavorableExtremeRule":"MUST_BE_NULL_AND_NOT_EVALUATED"',
    '"readyFavorableExtremeRule":"MUST_BE_NON_NULL"',
    '"calculatorInvocation":"EXACTLY_ONCE_ONLY_FOR_READY_AND_RESOLVED"',
    '"calculatorUnavailableRule":'
    '"INVARIANT_VIOLATION_FAIL_CLOSED_WITHOUT_RESULT"',
    '"equalityRule":"HIT"',
    '"sourceTargetInput":"ABSENT"',
    '"highLowReselection":"ABSENT"',
    '"endpointCloseFallback":"ABSENT"',
    '"eligibilityResolverInvocation":"ABSENT"',
    '"favorableExtremeSelectorInvocation":"ABSENT"',
    '"methodologyActivation":"ABSENT"',
    '"inputFingerprint":"ABSENT"',
    '"publication":"ABSENT"',
    '"fallbackBehavior":"ABSENT"',
):
    require(marker in definition, f"Missing orchestration boundary: {marker}")
require(
    enum_values(policy, "TargetHitOrchestrationPolicyVersion")
    == ["POINT_IN_TIME_TARGET_HIT_ORCHESTRATION_V1"]
    and "return CANONICAL_DEFINITION;" in policy
    and "CANONICAL_DEFINITION.getBytes(StandardCharsets.UTF_8)" in policy
    and "return DEFINITION_HASH;" in policy,
    "Target-hit orchestration policy enum or UTF-8 surface changed",
)

compact_sources = {
    name: compact(without_comments(source))
    for name, source in sources.items()
}
request = compact_sources["TargetHitOrchestrationRequest.java"]
resolution = compact_sources["TargetHitOrchestrationResolution.java"]
orchestrator = compact_sources["TargetHitOrchestrator.java"]
require(
    "publicrecordTargetHitOrchestrationRequest("
    "TargetHitOrchestrationPolicyVersionpolicyVersion,"
    "TargetEligibilityResolutioneligibilityResolution,"
    "FavorableExtremeResolutionfavorableExtremeResolution)" in request
    and "instanceofTargetEligibilityResolution."
    "ReadyForWindowEvidenceready" in request
    and "Objects.requireNonNull(favorableExtremeResolution,"
    in request
    and "!favorableContext.readyEligibility().equals(ready)" in request
    and "elseif(favorableExtremeResolution!=null)" in request,
    "Target-hit orchestration request or conditional evidence truth table changed",
)
for marker in (
    "permitsTargetHitOrchestrationResolution.Available,"
    "TargetHitOrchestrationResolution.Pending,"
    "TargetHitOrchestrationResolution.NotApplicable,"
    "TargetHitOrchestrationResolution.EligibilityUnavailable,"
    "TargetHitOrchestrationResolution.FavorableExtremeUnavailable",
    "recordResolutionContext("
    "TargetHitOrchestrationPolicyVersionpolicyVersion,"
    "StringpolicyDefinitionHash)",
    "recordAvailable(ResolutionContextcontext,"
    "FavorableExtremeResolution.ResolvedfavorableExtremeResolution,"
    "TargetHitResult.AvailabletargetHitResult)"
    "implementsTargetHitOrchestrationResolution",
    "recordPending(ResolutionContextcontext,"
    "TargetEligibilityResolution.PendingeligibilityResolution)"
    "implementsTargetHitOrchestrationResolution",
    "recordNotApplicable(ResolutionContextcontext,"
    "TargetEligibilityResolution.NotApplicableeligibilityResolution)"
    "implementsTargetHitOrchestrationResolution",
    "recordEligibilityUnavailable(ResolutionContextcontext,"
    "TargetEligibilityResolution.UnavailableeligibilityResolution)"
    "implementsTargetHitOrchestrationResolution",
    "recordFavorableExtremeUnavailable(ResolutionContextcontext,"
    "FavorableExtremeResolution.UnavailablefavorableExtremeResolution)"
    "implementsTargetHitOrchestrationResolution",
):
    require(marker in resolution, f"Orchestration result changed: {marker}")
require(
    "publicfinalclassTargetHitOrchestrator" in orchestrator
    and "privateTargetHitOrchestrator(){}" in orchestrator
    and "publicstaticTargetHitOrchestrationResolutionorchestrate("
    "TargetHitOrchestrationRequestrequest)" in orchestrator,
    "Target-hit orchestrator public surface changed",
)

expected_imports = {
    "TargetHitOrchestrationPolicyVersion.java": {
        "java.nio.charset.StandardCharsets",
    },
    "TargetHitOrchestrationRequest.java": {
        "java.util.Objects",
        "com.wallstreetreceipts.api.domain.outcome.favorableextreme."
        "FavorableExtremePolicyVersion",
        "com.wallstreetreceipts.api.domain.outcome.favorableextreme."
        "FavorableExtremeResolution",
        "com.wallstreetreceipts.api.domain.outcome.targeteligibility."
        "TargetEligibilityPolicyVersion",
        "com.wallstreetreceipts.api.domain.outcome.targeteligibility."
        "TargetEligibilityResolution",
    },
    "TargetHitOrchestrationResolution.java": {
        "java.util.Objects",
        "com.wallstreetreceipts.api.domain.outcome.calculation."
        "TargetHitResult",
        "com.wallstreetreceipts.api.domain.outcome.favorableextreme."
        "FavorableExtremeResolution",
        "com.wallstreetreceipts.api.domain.outcome.targeteligibility."
        "TargetEligibilityResolution",
    },
    "TargetHitOrchestrator.java": {
        "java.util.Objects",
        "com.wallstreetreceipts.api.domain.outcome.calculation."
        "TargetHitCalculator",
        "com.wallstreetreceipts.api.domain.outcome.calculation."
        "TargetHitInput",
        "com.wallstreetreceipts.api.domain.outcome.calculation."
        "TargetHitResult",
        "com.wallstreetreceipts.api.domain.outcome.favorableextreme."
        "FavorableExtremeResolution",
        "com.wallstreetreceipts.api.domain.outcome.routing."
        "CalculatorSideRouting.DirectionalRoute",
        "com.wallstreetreceipts.api.domain.outcome.targeteligibility."
        "TargetEligibilityResolution",
        "com.wallstreetreceipts.api.domain.outcome."
        "targethitorchestration.TargetHitOrchestrationResolution."
        "ResolutionContext",
    },
}
forbidden_runtime = re.compile(
    r"\b(?:Clock|Locale|TimeZone|ZoneId|UUID|Random|SecureRandom|"
    r"System|Runtime|Thread|Process|ProcessBuilder|ClassLoader)\b|"
    r"\bClass\s*\.\s*forName\s*\(|\.\s*now\s*\(|"
    r"\b(?:getenv|getProperty|setProperty)\s*\(|"
    r"@(?:Component|Service|Repository|Controller)\b"
)
for name, source in sources.items():
    logic = without_comments_or_strings(source)
    imports = set(re.findall(
        r"^import\s+([^;]+);", logic, flags=re.MULTILINE
    ))
    require(
        imports == expected_imports[name]
        and forbidden_runtime.search(logic) is None
        and "double" not in logic
        and "float" not in logic
        and "ObjectMapper" not in logic
        and "HttpClient" not in logic
        and "DataSource" not in logic
        and "org.springframework" not in logic,
        f"Target-hit orchestration import/runtime boundary changed: "
        f"{name} {imports}",
    )

request_logic = compact(without_comments_or_strings(
    sources["TargetHitOrchestrationRequest.java"]
))
resolution_logic = compact(without_comments_or_strings(
    sources["TargetHitOrchestrationResolution.java"]
))
orchestrator_logic = compact(without_comments_or_strings(
    sources["TargetHitOrchestrator.java"]
))
require(
    request_logic.count("switch(resolution)") == 2
    and "caseTargetEligibilityResolution.ReadyForWindowEvidence"
    in request_logic
    and "caseTargetEligibilityResolution.Pending" in request_logic
    and "caseTargetEligibilityResolution.NotApplicable" in request_logic
    and "caseTargetEligibilityResolution.Unavailable" in request_logic
    and "caseFavorableExtremeResolution.Resolved" in request_logic
    and "caseFavorableExtremeResolution.Unavailable" in request_logic,
    "Request must correlate every closed supplied leaf variant",
)
require(
    orchestrator_logic.count("TargetHitCalculator.calculate(") == 1
    and orchestrator_logic.count("newTargetHitInput(") == 1
    and "newTargetHitInput(route.targetHitSide(),"
    "ready.evidence().targetEvidence().target(),"
    "resolved.favorableExtreme().value())" in orchestrator_logic
    and "caseTargetEligibilityResolution.Pendingpending->"
    "newTargetHitOrchestrationResolution.Pending(context,pending)"
    in orchestrator_logic
    and "caseTargetEligibilityResolution.NotApplicablenotApplicable->"
    "newTargetHitOrchestrationResolution.NotApplicable("
    "context,notApplicable)" in orchestrator_logic
    and "caseTargetEligibilityResolution.Unavailableunavailable->"
    "newTargetHitOrchestrationResolution.EligibilityUnavailable("
    "context,unavailable)" in orchestrator_logic
    and "caseFavorableExtremeResolution.Unavailableunavailable->"
    "newTargetHitOrchestrationResolution.FavorableExtremeUnavailable("
    "context,unavailable)" in orchestrator_logic
    and "instanceofTargetHitResult.Availableavailable" in orchestrator_logic
    and "thrownewIllegalStateException(" in orchestrator_logic,
    "Orchestrator branch preservation or exact calculator input changed",
)
combined_logic = "\n".join(
    without_comments_or_strings(source) for source in sources.values()
)
require(
    "TargetEligibilityResolver" not in combined_logic
    and "TargetEligibilityRequest" not in combined_logic
    and "FavorableExtremeSelector" not in combined_logic
    and "FavorableExtremeRequest" not in combined_logic
    and "FullWindowHighLowObservation" not in combined_logic
    and "WindowPriceBinding" not in combined_logic
    and "CallDirection" not in combined_logic
    and "sourceTarget" not in combined_logic
    and "FavorableExtremeField" not in combined_logic
    and "EndpointPrice" not in combined_logic
    and "CallOutcome" not in combined_logic
    and "ScoringMethodology" not in combined_logic
    and ".reason()" not in combined_logic
    and ".setScale(" not in combined_logic
    and ".max(" not in combined_logic
    and ".min(" not in combined_logic,
    "Orchestration must not recompute, flatten, reselect, or publish evidence",
)

api_main_dir = Path("apps/api/src/main/java")
new_paths = {
    (production_dir / name).resolve() for name in production_files
}
new_markers = tuple(name.removesuffix(".java") for name in production_files)
approved_readiness_consumers = {
    (api_main_dir / "com/wallstreetreceipts/api/domain/outcome/"
     "targethitreadiness/"
     "TargetHitReadinessPolicyVersion.java").resolve(): set(),
    (api_main_dir / "com/wallstreetreceipts/api/domain/outcome/"
     "targethitreadiness/"
     "TargetHitReadinessRequest.java").resolve(): {
        "TargetHitOrchestrationPolicyVersion",
        "TargetHitOrchestrationResolution",
    },
    (api_main_dir / "com/wallstreetreceipts/api/domain/outcome/"
     "targethitreadiness/"
     "TargetHitReadinessResolution.java").resolve(): {
        "TargetHitOrchestrationResolution",
    },
    (api_main_dir / "com/wallstreetreceipts/api/domain/outcome/"
     "targethitreadiness/"
     "TargetHitReadinessResolver.java").resolve(): {
        "TargetHitOrchestrationResolution",
    },
}
for other_path in api_main_dir.rglob("*.java"):
    if other_path.resolve() in new_paths:
        continue
    other_source = other_path.read_text(encoding="utf-8")
    other_logic = without_comments_or_strings(other_source)
    if other_path.resolve() in approved_readiness_consumers:
        actual_references = {
            marker for marker in new_markers
            if re.search(rf"\b{re.escape(marker)}\b", other_logic)
        }
        orchestration_imports = {
            re.sub(r"\s+", "", imported)
            for imported in re.findall(
                r"^import\s+([^;]+);", other_logic, flags=re.MULTILINE
            )
            if "domain.outcome.targethitorchestration" in imported
        }
        expected_imports = {
            "com.wallstreetreceipts.api.domain.outcome."
            "targethitorchestration." + marker
            for marker in approved_readiness_consumers[
                other_path.resolve()
            ]
        }
        require(
            actual_references
            == approved_readiness_consumers[other_path.resolve()]
            and orchestration_imports == expected_imports
            and "TargetHitOrchestrator" not in other_logic
            and "TargetHitOrchestrationRequest" not in other_logic,
            "Target-hit readiness may consume only the exact supplied "
            f"orchestration policy/result surface: {other_path}",
        )
        continue
    require(
        "domain.outcome.targethitorchestration" not in other_logic
        and not any(
            re.search(rf"\b{re.escape(marker)}\b", other_logic)
            for marker in new_markers
        ),
        f"Target-hit orchestration must remain disconnected: {other_path}",
    )

golden = (test_dir / "TargetHitOrchestratorGoldenTest.java").read_text(
    encoding="utf-8"
)
golden_logic = without_comments_or_strings(golden)
golden_compact = compact(golden_logic)
direction_source = re.search(
    r"@EnumSource\s*\(\s*value\s*=\s*CallDirection\.class\s*,"
    r"\s*names\s*=\s*\{(?P<body>.*?)\}\s*\)",
    golden,
    flags=re.DOTALL,
)
require(direction_source is not None, "Directional source changed")
direction_names = re.findall(
    r'"([A-Z][A-Z0-9_]*)"', direction_source.group("body")
)
require(
    re.search(r"\\u+[0-9a-fA-F]{4}", golden) is None
    and len(re.findall(r"@Test\b", golden_logic)) == 12
    and len(re.findall(r"@ParameterizedTest\b", golden_logic)) == 4
    and len(re.findall(r"@EnumSource\b", golden_logic)) == 1
    and len(re.findall(r"@MethodSource\b", golden_logic)) == 3
    and direction_names == [
        "STRONG_BULLISH", "BULLISH", "BEARISH", "STRONG_BEARISH"
    ]
    and "@Disabled" not in golden_logic
    and "Assumptions" not in golden_logic
    and "assumeTrue" not in golden_logic
    and "assumeFalse" not in golden_logic,
    "Target-hit orchestration golden annotation boundary changed",
)
for marker in (
    "canonicalDefinitionHasExactBytesHashAndDefensiveReads",
    "exactFileRecordSealedAndDisconnectedSurfacesAreStable",
    "bullishAndBearishMissesUseNoToleranceOrFallback",
    "equalityIsAHitForBothSidesAndResolvedLeafIsPreserved",
    "pendingEligibilityIsPreservedWithoutBoolean",
    "requestRejectsMissingReadyEvidenceAndEvidenceOnNonReadyBranches",
    "wholeRecordEqualityAllowsReplayButRejectsAnotherReadyContext",
    "directResultConstructorsEnforceLocalPolicyAndBranchShape",
    "normalizedTargetEvidenceWinsOverDifferentSourceTarget",
    "correctionBasisIsComposedIndependentlyWithoutLatestInference",
    "compositionIsIndependentOfLocaleTimezoneAndPriorInvocations",
    "nullRootsAndCompetingPolicyInputsFailClosed",
    "allDirectionalRoutesUseOnlyTheirPreservedSideAndSelectedField",
    "everyNotApplicableReasonPreservesTheExactTypedLeaf",
    "everyEligibilityUnavailableReasonAndNestedHorizonReasonIsPreserved",
    "everyFavorableExtremeUnavailableReasonPreservesTheExactTypedLeaf",
):
    require(marker in golden_logic,
            f"Missing orchestration golden coverage: {marker}")
eligibility_reasons = [
    "BASIS_TERMS_NOT_KNOWN_AS_OF", "HORIZON_BASIS_MISMATCH",
    "ROUTE_MISSING", "ROUTE_DIRECTION_MISMATCH",
    "TARGET_STATE_CONFLICT", "TARGET_DATE_SEMANTICS_UNSUPPORTED",
    "TARGET_EVIDENCE_NOT_KNOWN_AS_OF",
    "TARGET_EVIDENCE_BASIS_MISMATCH", "TARGET_ASSET_MISMATCH",
    "TARGET_CURRENCY_MISMATCH", "CATALOG_NOT_KNOWN_AS_OF",
    "CATALOG_EVIDENCE_MISMATCH", "FIRST_ELIGIBLE_SESSION_MISSING",
    "HORIZON_ENDPOINT_SESSION_MISSING",
]
favorable_reasons = [
    "TARGET_ADJUSTMENT_BASIS_UNSUPPORTED", "BINDING_NOT_KNOWN_AS_OF",
    "BINDING_ASSET_MISMATCH", "BINDING_PRIMARY_VENUE_MISMATCH",
    "BINDING_CURRENCY_MISMATCH", "OBSERVATION_MISSING_AS_OF",
    "BASIS_MISMATCH", "HORIZON_MISMATCH", "ASSET_MISMATCH",
    "PRIMARY_VENUE_MISMATCH", "CURRENCY_MISMATCH", "SOURCE_MISMATCH",
    "CATALOG_MISMATCH", "SESSION_WINDOW_MISMATCH",
    "LOWER_BOUND_MISMATCH", "UPPER_BOUND_MISMATCH",
    "BOUNDARY_CONVENTION_MISMATCH", "PRICE_FIELD_MISMATCH",
    "WINDOW_COMPLETENESS_UNAVAILABLE", "ADJUSTMENT_BASIS_MISMATCH",
    "CORPORATE_ACTION_CONTINUITY_UNAVAILABLE",
    "OBSERVATION_AMBIGUOUS",
]
require(
    "Arrays.stream(TargetEligibilityResolution.UnavailableReason.values())"
    in golden_compact
    and "Arrays.stream(FavorableExtremeResolution.UnavailableReason.values())"
    in golden_compact
    and all(reason in golden for reason in eligibility_reasons)
    and all(reason in golden for reason in favorable_reasons)
    and "isSameAs(leaf)" in golden_compact
    and 'new BigDecimal("999")' in golden
    and 'new BigDecimal("235.0000")' in golden
    and "Locale.setDefault" in golden_logic
    and "TimeZone.setDefault" in golden_logic
    and "finally" in golden_logic,
    "Goldens must lock every nested reason, normalized target, and replay",
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
    "Orchestration must preserve exact schema and fixture sets",
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
    "Orchestration must preserve fixture manifest order",
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
    "Available target hit must not activate or publish an outcome",
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
    "Orchestration must preserve exact OpenAPI and Flyway surfaces",
)
for web_path in Path("apps/web/src").rglob("*"):
    if web_path.is_file() and web_path.suffix in {".ts", ".tsx", ".js", ".jsx"}:
        web_source = web_path.read_text(encoding="utf-8")
        require(
            "domain.outcome.targethitorchestration" not in web_source
            and not any(marker in web_source for marker in new_markers),
            f"Target-hit orchestration must not expand web: {web_path}",
        )

print(
    "Validated exact supplied-leaf target-hit orchestration, all nested "
    "eligibility/extreme branches, exact calculator input and single call, "
    "local-attestation boundary, and no lifecycle or product publication"
)
PYTHON
