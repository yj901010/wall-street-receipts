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
    "outcome/directionalwinorchestration"
)
test_dir = Path(
    "apps/api/src/test/java/com/wallstreetreceipts/api/domain/"
    "outcome/directionalwinorchestration"
)
production_files = {
    "DirectionalWinOrchestrationPolicyVersion.java",
    "DirectionalWinOrchestrationRequest.java",
    "DirectionalWinOrchestrationResolution.java",
    "DirectionalWinOrchestrator.java",
}
test_files = {"DirectionalWinOrchestratorGoldenTest.java"}
require(
    {path.name for path in production_dir.glob("*.java")}
    == production_files,
    "Directional-win orchestration package must contain four files",
)
require(
    {path.name for path in test_dir.glob("*.java")} == test_files,
    "Directional-win orchestration package must contain one golden",
)

sources = {
    name: (production_dir / name).read_text(encoding="utf-8")
    for name in production_files
}
for name, source in sources.items():
    require(
        "package com.wallstreetreceipts.api.domain.outcome."
        "directionalwinorchestration;" in source
        and re.search(r"\\u+[0-9a-fA-F]{4}", source) is None
        and '"""' not in source,
        f"Directional-win package or lexical boundary changed: {name}",
    )

policy = sources["DirectionalWinOrchestrationPolicyVersion.java"]
definition = java_string_constant(policy, "CANONICAL_DEFINITION")
policy_hash = (
    "51429c7601d4807162855f08c680d1e6bb7895f87fc108e141e5ad3a3ab25bcb"
)
definition_bytes = definition.encode("utf-8")
require(
    len(definition_bytes) == 3699
    and definition.isascii()
    and definition.startswith(
        '{"policyVersion":'
        '"SUPPLIED_LEAF_DIRECTIONAL_WIN_ORCHESTRATION_V1",'
    )
    and definition.endswith('"publication":"ABSENT"}')
    and definition == definition.strip()
    and "\n" not in definition
    and "\r" not in definition
    and hashlib.sha256(definition_bytes).hexdigest() == policy_hash
    and java_string_constant(policy, "DEFINITION_HASH") == policy_hash,
    "Directional-win canonical bytes, length, or hash changed",
)
adr = Path(
    "decisions/ADR-021-supplied-leaf-directional-win-orchestration.md"
).read_text(encoding="utf-8")
require(
    re.findall(r"```text\r?\n([^\r\n]+)\r?\n```", adr)
    == [definition, policy_hash],
    "ADR-021 canonical definition or digest drifted from Java",
)
for marker in (
    '"requestFields":["policyVersion","termsEvidence",'
    '"sideRouting","assetReturnResult"]',
    '"requestPresence":'
    '"ALL_FIELDS_NON_NULL_INCLUDING_NON_DIRECTIONAL_ASSET_RETURN"',
    '"directionCorrelation":'
    '"termsEvidence.direction==sideRouting.source.context.direction_'
    'EXACT_CANONICAL_DIRECTION"',
    '"basisCorrelation":',
    '"assetCorrelation":',
    '"termsVisibilityRule":'
    '"termsEvidence.availableAt<=evaluationAsOf&&'
    'termsEvidence.capturedAt<=evaluationAsOf"',
    '"nestedReasonRule":"PRESERVE_EXACT_TYPED_ASSET_RETURN_'
    'PRICE_PAIR_AND_ENDPOINT_RESOLUTIONS_WITHOUT_REASON_MAPPING"',
    '"calculatorInvocation":"EXACTLY_ONCE_ONLY_FOR_DIRECTIONAL_'
    'AND_ASSET_RETURN_AVAILABLE"',
    '"zeroRule":"MISS_FOR_BOTH_SIDES"',
    '"targetDispositionUse":"ABSENT"',
    '"polarityResolverInvocation":"ABSENT"',
    '"assetReturnCalculatorInvocation":"ABSENT"',
    '"pricePairSelectorInvocation":"ABSENT"',
    '"endpointSelectorInvocation":"ABSENT"',
    '"methodologyActivation":"ABSENT"',
    '"persistence":"ABSENT"',
    '"publication":"ABSENT"',
):
    require(marker in definition, f"Missing directional boundary: {marker}")
require(
    re.findall(
        r"\bSUPPLIED_LEAF_DIRECTIONAL_WIN_ORCHESTRATION_V1\b",
        re.search(
            r"enum\s+DirectionalWinOrchestrationPolicyVersion\s*"
            r"\{(?P<body>.*?)\}",
            without_comments(policy),
            flags=re.DOTALL,
        ).group("body").split(";", 1)[0],
    ) == ["SUPPLIED_LEAF_DIRECTIONAL_WIN_ORCHESTRATION_V1"],
    "Directional-win orchestration policy enum changed",
)

request = compact(without_comments(
    sources["DirectionalWinOrchestrationRequest.java"]
))
resolution = compact(without_comments(
    sources["DirectionalWinOrchestrationResolution.java"]
))
orchestrator = compact(without_comments(
    sources["DirectionalWinOrchestrator.java"]
))
require(
    "publicrecordDirectionalWinOrchestrationRequest("
    "DirectionalWinOrchestrationPolicyVersionpolicyVersion,"
    "BasisForecastTermsEvidencetermsEvidence,"
    "CalculatorSideRouting.ResultsideRouting,"
    "AssetReturnResultassetReturnResult)" in request
    and request.count("Objects.requireNonNull(") == 4
    and "termsEvidence.direction()!=polarityContext.direction()" in request
    and "!termsEvidence.basis().equals(horizonBasis)" in request
    and "!termsEvidence.assetId().equals("
    "endpointContext.binding().assetId())" in request
    and "termsEvidence.availableAt().isAfter("
    "endpointContext.evaluationAsOf())" in request
    and "termsEvidence.capturedAt().isAfter("
    "endpointContext.evaluationAsOf())" in request
    and "REQUIRED_POLARITY_HASH" in request
    and "REQUIRED_ASSET_RETURN_HASH" in request,
    "Directional-win request shape or correlation changed",
)
for marker in (
    "permitsDirectionalWinOrchestrationResolution.Available,"
    "DirectionalWinOrchestrationResolution.NotApplicable,"
    "DirectionalWinOrchestrationResolution.AssetReturnUnavailable",
    "recordResolutionContext("
    "DirectionalWinOrchestrationPolicyVersionpolicyVersion,"
    "StringpolicyDefinitionHash)",
    "recordAvailable(ResolutionContextcontext,"
    "BasisForecastTermsEvidencetermsEvidence,"
    "DirectionalRoutesideRouting,"
    "AssetReturnResult.AvailableassetReturnResult,"
    "DirectionalWinResult.AvailabledirectionalWinResult)"
    "implementsDirectionalWinOrchestrationResolution",
    "recordNotApplicable(ResolutionContextcontext,"
    "BasisForecastTermsEvidencetermsEvidence,"
    "NonDirectionalRoutesideRouting,"
    "AssetReturnResultassetReturnResult)"
    "implementsDirectionalWinOrchestrationResolution",
    "recordAssetReturnUnavailable(ResolutionContextcontext,"
    "BasisForecastTermsEvidencetermsEvidence,"
    "DirectionalRoutesideRouting,"
    "AssetReturnResult.UnavailableassetReturnResult)"
    "implementsDirectionalWinOrchestrationResolution",
):
    require(marker in resolution, f"Directional result changed: {marker}")
require(
    "publicfinalclassDirectionalWinOrchestrator" in orchestrator
    and "privateDirectionalWinOrchestrator(){}" in orchestrator
    and "publicstaticDirectionalWinOrchestrationResolutionorchestrate("
    "DirectionalWinOrchestrationRequestrequest)" in orchestrator
    and orchestrator.count("DirectionalWinCalculator.calculate(") == 1
    and orchestrator.count("newDirectionalWinInput(") == 1
    and "newDirectionalWinInput(directional.directionalWinSide(),"
    "available.assetReturn())" in orchestrator
    and "caseNonDirectionalRoutenonDirectional->"
    "newDirectionalWinOrchestrationResolution.NotApplicable("
    "context,request.termsEvidence(),nonDirectional,"
    "request.assetReturnResult())" in orchestrator
    and "caseAssetReturnResult.Unavailableunavailable->"
    "newDirectionalWinOrchestrationResolution."
    "AssetReturnUnavailable(context,request.termsEvidence(),"
    "directional,unavailable)" in orchestrator
    and "instanceofDirectionalWinResult.AvailabledirectionalWin"
    in orchestrator
    and "thrownewIllegalStateException(" in orchestrator,
    "Directional branch preservation or primitive input changed",
)

expected_imports = {
    "DirectionalWinOrchestrationPolicyVersion.java": {
        "java.nio.charset.StandardCharsets",
    },
    "DirectionalWinOrchestrationRequest.java": {
        "java.util.Objects",
        "com.wallstreetreceipts.api.domain.outcome.assetreturn."
        "AssetReturnPolicyVersion",
        "com.wallstreetreceipts.api.domain.outcome.assetreturn."
        "AssetReturnResult",
        "com.wallstreetreceipts.api.domain.outcome.direction."
        "CallDirectionPolarityPolicyVersion",
        "com.wallstreetreceipts.api.domain.outcome.observation."
        "EndpointPriceResolution",
        "com.wallstreetreceipts.api.domain.outcome.pricepair."
        "AssetReturnPricePairResolution",
        "com.wallstreetreceipts.api.domain.outcome.routing."
        "CalculatorSideRouting",
        "com.wallstreetreceipts.api.domain.outcome.routing."
        "CalculatorSideRouting.DirectionalRoute",
        "com.wallstreetreceipts.api.domain.outcome.routing."
        "CalculatorSideRouting.NonDirectionalRoute",
        "com.wallstreetreceipts.api.domain.outcome.targeteligibility."
        "BasisForecastTermsEvidence",
    },
    "DirectionalWinOrchestrationResolution.java": {
        "java.util.Objects",
        "com.wallstreetreceipts.api.domain.outcome.assetreturn."
        "AssetReturnResult",
        "com.wallstreetreceipts.api.domain.outcome.calculation."
        "DirectionalWinResult",
        "com.wallstreetreceipts.api.domain.outcome.routing."
        "CalculatorSideRouting.DirectionalRoute",
        "com.wallstreetreceipts.api.domain.outcome.routing."
        "CalculatorSideRouting.NonDirectionalRoute",
        "com.wallstreetreceipts.api.domain.outcome.targeteligibility."
        "BasisForecastTermsEvidence",
    },
    "DirectionalWinOrchestrator.java": {
        "java.util.Objects",
        "com.wallstreetreceipts.api.domain.outcome.assetreturn."
        "AssetReturnResult",
        "com.wallstreetreceipts.api.domain.outcome.calculation."
        "DirectionalWinCalculator",
        "com.wallstreetreceipts.api.domain.outcome.calculation."
        "DirectionalWinInput",
        "com.wallstreetreceipts.api.domain.outcome.calculation."
        "DirectionalWinResult",
        "com.wallstreetreceipts.api.domain.outcome."
        "directionalwinorchestration."
        "DirectionalWinOrchestrationResolution.ResolutionContext",
        "com.wallstreetreceipts.api.domain.outcome.routing."
        "CalculatorSideRouting.DirectionalRoute",
        "com.wallstreetreceipts.api.domain.outcome.routing."
        "CalculatorSideRouting.NonDirectionalRoute",
    },
}
forbidden_runtime = re.compile(
    r"\b(?:Clock|UUID|Random|SecureRandom|System|Runtime|Thread|"
    r"Process|ProcessBuilder|ClassLoader)\b|"
    r"\bClass\s*\.\s*forName\s*\(|\.\s*now\s*\(|"
    r"@(?:Component|Service|Repository|Controller)\b"
)
for name, source in sources.items():
    logic = without_comments_or_strings(source)
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
        f"Directional orchestration import/runtime boundary changed: "
        f"{name} {imports}",
    )

combined_logic = "\n".join(
    without_comments_or_strings(source) for source in sources.values()
)
for forbidden in (
    "CallDirectionPolarityResolver", "CallDirectionPolarityRequest",
    "CalculatorSideRouting.route(", "CalculatorSideAdapter",
    "AssetReturnCalculator", "AssetReturnInput",
    "AssetReturnPricePairSelector", "AssetReturnPricePairRequest",
    "EndpointPriceSelector", "EndpointPriceRequest",
    "BasisPriceObservation", "PricePairAdjustmentEvidence",
    "TargetEligibilityResolver", "TargetEligibilityRequest",
    "AssetReturnResult.UnavailableReason",
    "AssetReturnPricePairResolution.UnavailableReason",
    "EndpointPriceResolution.UnavailableReason", ".reason()",
    ".targetDisposition()", ".compareTo(", ".signum(", ".abs(",
    ".setScale(", ".divide(", "CallOutcome", "ScoringMethodology",
    "fingerprint", "@Service", "@Repository", "@Controller",
):
    require(
        forbidden not in combined_logic,
        f"Directional orchestration crosses its boundary: {forbidden}",
    )

api_main_dir = Path("apps/api/src/main/java")
new_paths = {
    (production_dir / name).resolve() for name in production_files
}
new_markers = tuple(name.removesuffix(".java") for name in production_files)
approved_readiness_consumers = {
    (api_main_dir / "com/wallstreetreceipts/api/domain/outcome/"
     "directionalwinreadiness/"
     "DirectionalWinReadinessPolicyVersion.java").resolve(): set(),
    (api_main_dir / "com/wallstreetreceipts/api/domain/outcome/"
     "directionalwinreadiness/"
     "DirectionalWinReadinessRequest.java").resolve(): {
        "DirectionalWinOrchestrationPolicyVersion",
        "DirectionalWinOrchestrationResolution",
    },
    (api_main_dir / "com/wallstreetreceipts/api/domain/outcome/"
     "directionalwinreadiness/"
     "DirectionalWinReadinessResolution.java").resolve(): {
        "DirectionalWinOrchestrationResolution",
    },
    (api_main_dir / "com/wallstreetreceipts/api/domain/outcome/"
     "directionalwinreadiness/"
     "DirectionalWinReadinessResolver.java").resolve(): {
        "DirectionalWinOrchestrationResolution",
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
            if "domain.outcome.directionalwinorchestration" in imported
        }
        expected_imports = {
            "com.wallstreetreceipts.api.domain.outcome."
            "directionalwinorchestration." + marker
            for marker in approved_readiness_consumers[
                other_path.resolve()
            ]
        }
        require(
            actual_references
            == approved_readiness_consumers[other_path.resolve()]
            and orchestration_imports == expected_imports
            and "DirectionalWinOrchestrator" not in other_logic
            and "DirectionalWinOrchestrationRequest" not in other_logic,
            "Directional-win readiness may consume only the exact supplied "
            f"orchestration policy/result surface: {other_path}",
        )
        continue
    require(
        "domain.outcome.directionalwinorchestration" not in other_logic
        and not any(
            re.search(rf"\b{re.escape(marker)}\b", other_logic)
            for marker in new_markers
        ),
        f"Directional-win orchestration must remain disconnected: {other_path}",
    )

golden = (
    test_dir / "DirectionalWinOrchestratorGoldenTest.java"
).read_text(encoding="utf-8")
golden_logic = without_comments_or_strings(golden)
direction_source = re.search(
    r"@EnumSource\s*\(\s*value\s*=\s*CallDirection\.class\s*,"
    r"\s*names\s*=\s*\{(?P<body>.*?)\}\s*\)",
    golden,
    flags=re.DOTALL,
)
require(direction_source is not None, "Directional enum source changed")
require(
    re.search(r"\\u+[0-9a-fA-F]{4}", golden) is None
    and len(re.findall(r"@Test\b", golden_logic)) == 15
    and len(re.findall(r"@ParameterizedTest\b", golden_logic)) == 4
    and len(re.findall(r"@EnumSource\b", golden_logic)) == 1
    and len(re.findall(r"@MethodSource\b", golden_logic)) == 3
    and re.findall(
        r'"([A-Z][A-Z0-9_]*)"', direction_source.group("body")
    ) == [
        "STRONG_BULLISH", "BULLISH", "BEARISH", "STRONG_BEARISH"
    ]
    and "@Disabled" not in golden_logic
    and "Assumptions" not in golden_logic,
    "Directional-win golden annotation boundary changed",
)
for marker in (
    "canonicalDefinitionHasExactBytesHashAdrAndDefensiveReads",
    "exactFileRecordSealedAndDisconnectedSurfacesAreStable",
    "nullRootsAndRequiredFieldsFailClosed",
    "exactCanonicalDirectionMustMatchNotOnlyItsPolarity",
    "wholeBasisRevisionIdentityMustMatch",
    "wholeBasisCallIdentityMustMatch",
    "exactAssetIdentityMustMatch",
    "futureTermsAvailabilityIsRejectedBeforeBranching",
    "futureTermsCaptureIsRejectedBeforeBranching",
    "visibilityEqualityAtEvaluationAsOfIsAccepted",
    "equalButDistinctReplayRecordsAreAccepted",
    "directResultConstructorsEnforceOnlyLocalPolicyAndTypedShape",
    "originalAndCorrectionBasesComposeIndependently",
    "sourceTargetDispositionIsIgnoredAndPreserved",
    "compositionIsIndependentOfLocaleTimezoneAndPriorCalls",
    "allCanonicalDirectionalRoutesUseTheirExactPreservedSide",
    "strictSignComparisonMakesExactZeroAMissForBothSides",
    "everyNestedUnavailableLeafIsPreservedWithoutReasonMapping",
    "neutralPrecedencePreservesAnyCorrelatedReturnWithoutABoolean",
    "allUnavailableLeaves", "neutralAssetReturnLeaves",
):
    require(marker in golden_logic, f"Missing directional golden: {marker}")
require(
    "Arrays.stream(" in golden_logic
    and "AssetReturnPricePairResolution.UnavailableReason.values()"
    in golden_logic
    and "EndpointPriceResolution.UnavailableReason.values()"
    in golden_logic
    and "OUTPUT_NOT_REPRESENTABLE" in golden_logic
    and "ENDPOINT_NOT_REACHED_AS_OF" in golden_logic
    and "isSameAs(assetReturn)" in compact(golden_logic)
    and "Locale.setDefault" in golden_logic
    and "TimeZone.setDefault" in golden_logic
    and "finally" in golden_logic,
    "Goldens must lock nested reasons, identity, neutral, and replay",
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
    "Directional orchestration must preserve schemas and fixtures",
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
    "Directional orchestration must preserve OpenAPI and Flyway",
)
for web_path in Path("apps/web/src").rglob("*"):
    if web_path.is_file() and web_path.suffix in {
        ".ts", ".tsx", ".js", ".jsx"
    }:
        web_source = web_path.read_text(encoding="utf-8")
        require(
            "domain.outcome.directionalwinorchestration"
            not in web_source
            and not any(marker in web_source for marker in new_markers),
            f"Directional-win orchestration must not expand web: {web_path}",
        )

print(
    "Validated exact correlated supplied-leaf directional-win "
    "orchestration, all nested unavailable leaves, neutral precedence, "
    "strict sign input, and no lifecycle or product publication"
)
PYTHON
