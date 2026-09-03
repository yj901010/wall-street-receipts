python <<'PYTHON'
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

def validate_java_lexical_safety(source, label):
    require(
        re.search(r"\\u+[0-9a-fA-F]{4}", source) is None,
        f"{label} must not contain Java Unicode escapes",
    )
    require(
        '"""' not in source,
        f"{label} must not contain a Java text block",
    )
    state = "code"
    index = 0
    while index < len(source):
        current = source[index]
        following = source[index + 1] if index + 1 < len(source) else ""
        if state == "code":
            if current == "/" and following == "/":
                state = "line_comment"
                index += 2
                continue
            if current == "/" and following == "*":
                state = "block_comment"
                index += 2
                continue
            if current == '"':
                state = "string"
            elif current == "'":
                raise ValueError(
                    f"{label} must not contain Java character literals"
                )
        elif state == "line_comment":
            if source.startswith(("/*", "*/"), index):
                raise ValueError(
                    f"{label} line comment contains a block-comment delimiter"
                )
            if current in "\r\n":
                state = "code"
        elif state == "block_comment":
            if current == "*" and following == "/":
                state = "code"
                index += 2
                continue
        elif state == "string":
            if current == "\\":
                index += 2
                continue
            if source.startswith(("//", "/*", "*/"), index):
                raise ValueError(
                    f"{label} ordinary string contains a comment delimiter"
                )
            if current == '"':
                state = "code"
            elif current in "\r\n":
                raise ValueError(f"{label} contains an unterminated string")
        index += 1
    require(
        state in {"code", "line_comment"},
        f"{label} contains an unterminated lexical construct",
    )

def validate_exact_dotted_invocations(source):
    invocation_names = re.findall(
        r"\.\s*([A-Za-z_$][\w$]*)\s*\(", source
    )
    invocation_counts = {
        name: invocation_names.count(name)
        for name in set(invocation_names)
    }
    require(
        invocation_counts
        == {
            "requireNonNull": 5,
            "toTargetHitSide": 2,
            "toDirectionalWinSide": 2,
            "side": 4,
        },
        "Routing dotted invocation surface changed: "
        f"{invocation_counts}",
    )
    receiver_calls = [
        f"{receiver}.{method}"
        for receiver, method in re.findall(
            r"\b([A-Za-z_$][\w$]*)\s*\.\s*"
            r"([A-Za-z_$][\w$]*)\s*\(",
            source,
        )
    ]
    receiver_call_counts = {
        call: receiver_calls.count(call)
        for call in set(receiver_calls)
    }
    require(
        receiver_call_counts
        == {
            "Objects.requireNonNull": 5,
            "CalculatorSideAdapter.toTargetHitSide": 2,
            "CalculatorSideAdapter.toDirectionalWinSide": 2,
            "directional.side": 2,
            "source.side": 2,
        }
        and "::" not in source,
        "Routing receiver/method invocation surface changed: "
        f"{receiver_call_counts}",
    )

def require_no_routing_reverse_reference(source, path):
    require(
        re.search(r"\\u+[0-9a-fA-F]{4}", source) is None,
        f"API source must not hide routing through Unicode escapes: {path}",
    )
    require(
        "CalculatorSideRouting" not in source
        and "domain.outcome.routing" not in source,
        f"Routing must not be reverse-wired: {path}",
    )

routing_dir = Path(
    "apps/api/src/main/java/com/wallstreetreceipts/api/domain/outcome/routing"
)
test_dir = Path(
    "apps/api/src/test/java/com/wallstreetreceipts/api/domain/outcome/routing"
)
expected_production_files = {"CalculatorSideRouting.java"}
expected_test_files = {"CalculatorSideRoutingGoldenTest.java"}
production_paths = sorted(
    path for path in routing_dir.rglob("*.java") if path.is_file()
)
test_paths = sorted(
    path for path in test_dir.rglob("*.java") if path.is_file()
)
require(
    {path.relative_to(routing_dir).as_posix() for path in production_paths}
    == expected_production_files,
    "Calculator-side routing must contain exactly one production file",
)
require(
    {path.relative_to(test_dir).as_posix() for path in test_paths}
    == expected_test_files,
    "Calculator-side routing must contain exactly one golden file",
)
adapter_dir = Path(
    "apps/api/src/main/java/com/wallstreetreceipts/api/domain/outcome/adapter"
)
require(
    {path.name for path in adapter_dir.glob("*.java")}
    == {"CalculatorSideAdapter.java"},
    "ADR-013 must preserve ADR-012's exact-one-file adapter package",
)

routing_path = routing_dir / "CalculatorSideRouting.java"
golden_path = test_dir / "CalculatorSideRoutingGoldenTest.java"
routing_source = routing_path.read_text(encoding="utf-8")
golden_source = golden_path.read_text(encoding="utf-8")
validate_java_lexical_safety(routing_source, "Routing production")
validate_java_lexical_safety(golden_source, "Routing golden")
unicode_escape = re.compile(r"\\u+[0-9a-fA-F]{4}")
require(
    unicode_escape.search(routing_source) is None
    and unicode_escape.search(golden_source) is None,
    "Routing production/golden must not contain Java Unicode escapes",
)
routing_code = without_comments(routing_source)
golden_code = without_comments(golden_source)
routing_logic = without_comments_or_strings(routing_source)
golden_logic = without_comments_or_strings(golden_source)
compact_logic = compact(routing_logic)
compact_golden = compact(golden_code)
compact_golden_logic = compact(golden_logic)
require(
    "package com.wallstreetreceipts.api.domain.outcome.routing;"
    in routing_logic
    and "package com.wallstreetreceipts.api.domain.outcome.routing;"
    in golden_logic,
    "Routing production and golden must remain in the exact package",
)
require(
    "publicfinalclassCalculatorSideRouting{" in compact_logic
    and "privateCalculatorSideRouting(){}" in compact_logic,
    "CalculatorSideRouting must be public final with one private constructor",
)
public_methods = re.findall(
    r"public\s+static\s+(\w+)\s+(\w+)\s*\(\s*"
    r"([\w.]+)\s+(\w+)\s*\)",
    routing_code,
)
require(
    public_methods
    == [
        (
            "Result", "route",
            "CallDirectionPolarityResolution", "resolution",
        )
    ],
    f"Unexpected calculator-side routing public methods: {public_methods}",
)
for marker in (
    "publicstaticResultroute("
    "CallDirectionPolarityResolutionresolution)",
    'Objects.requireNonNull(resolution,"")',
    "returnswitch(resolution){",
    "caseDirectionaldirectional->newDirectionalRoute(",
    "directional,CalculatorSideAdapter.toTargetHitSide("
    "directional.side()),CalculatorSideAdapter."
    "toDirectionalWinSide(directional.side()))",
    "caseNonDirectionalnonDirectional->newNonDirectionalRoute("
    "nonDirectional)",
    "publicsealedinterfaceResultpermitsDirectionalRoute,"
    "NonDirectionalRoute{}",
    "publicrecordDirectionalRoute(Directionalsource,"
    "TargetHitSidetargetHitSide,DirectionalWinSide"
    "directionalWinSide)implementsResult",
    "publicrecordNonDirectionalRoute(NonDirectionalsource)"
    "implementsResult",
    "CalculatorSideAdapter.toTargetHitSide(source.side())",
    "CalculatorSideAdapter.toDirectionalWinSide(source.side())",
    "if(targetHitSide!=expectedTargetHitSide)",
    "if(directionalWinSide!=expectedDirectionalWinSide)",
):
    require(marker in compact_logic, f"Missing exact routing contract: {marker}")
require(
    compact_logic.count('Objects.requireNonNull(source,"")') == 2
    and compact_logic.count(
        'Objects.requireNonNull(targetHitSide,"")'
    ) == 1
    and compact_logic.count(
        'Objects.requireNonNull(directionalWinSide,"")'
    ) == 1,
    "Every direct route component must reject null exactly",
)
require(
    compact_logic.count("switch(") == 1
    and compact_logic.count("caseDirectional") == 1
    and compact_logic.count("caseNonDirectional") == 1
    and compact_logic.count("if(") == 2
    and compact_logic.count(
        "CalculatorSideAdapter.toTargetHitSide"
    ) == 2
    and compact_logic.count(
        "CalculatorSideAdapter.toDirectionalWinSide"
    ) == 2
    and compact_logic.count("Objects.requireNonNull(") == 5
    and compact_logic.count("Objects.") == 5
    and len(re.findall(r"\bnew\b", routing_logic)) == 4
    and len(re.findall(r"\breturn\b", routing_logic)) == 1
    and compact_logic.count(
        "thrownewIllegalArgumentException("
    ) == 2,
    "Routing control flow must remain the exact switch and two constructor checks",
)
require(
    len(re.findall(r"\bclass\b", routing_logic)) == 1
    and len(re.findall(r"\binterface\b", routing_logic)) == 1
    and len(re.findall(r"\brecord\b", routing_logic)) == 2
    and re.search(r"\benum\b", routing_logic) is None,
    "Routing must contain exactly one class, one interface, two records, and no enum",
)
require(
    re.search(r"\b(?:try|catch|for|while|do)\b", routing_logic) is None
    and re.search(
        r"\bsynchronized\b|\.\s*(?:wait|notify|notifyAll)\s*\(|"
        r"\bassert\b",
        routing_logic,
    ) is None
    and "?" not in routing_logic
    and "&&" not in routing_logic
    and "||" not in routing_logic,
    "Routing must not add hidden branch, loop, retry, or conditional logic",
)

require(
    re.search(
        r"\.\s*(?:ordinal|name)\s*\(|\bvalueOf\s*\(|"
        r"\bdefault\s*(?::|->)|\bgetOrDefault\s*\(|"
        r"\bto(?:Lower|Upper)Case\s*\(|\bMap\s*<|"
        r"\b(?:Class|ClassLoader|Method|Module|Package)\b|"
        r"\bgetClass\s*\(|\binstanceof\b|\.toString\s*\(|"
        r"\b(?:String|CharSequence)\b",
        routing_logic,
    ) is None,
    "Routing must use the exact sealed switch without fallback/reflection",
)
require(
    re.search(
        r"\b(?:System|Runtime|Thread|Process|ProcessBuilder|"
        r"Math|StrictMath|Boolean|Integer|Long)\b",
        routing_logic,
    ) is None,
    "Routing must not depend on environment, process, thread, time, or random state",
)
imports = re.findall(
    r"^import\s+([^;]+);", routing_code, flags=re.MULTILINE
)
require(
    set(imports)
    == {
        "java.util.Objects",
        "com.wallstreetreceipts.api.domain.outcome.adapter."
        "CalculatorSideAdapter",
        "com.wallstreetreceipts.api.domain.outcome.calculation."
        "DirectionalWinSide",
        "com.wallstreetreceipts.api.domain.outcome.calculation."
        "TargetHitSide",
        "com.wallstreetreceipts.api.domain.outcome.direction."
        "CallDirectionPolarityResolution",
        "com.wallstreetreceipts.api.domain.outcome.direction."
        "CallDirectionPolarityResolution.Directional",
        "com.wallstreetreceipts.api.domain.outcome.direction."
        "CallDirectionPolarityResolution.NonDirectional",
    },
    f"Routing import boundary changed: {imports}",
)
qualified_java_types = set(re.findall(
    r"\b(java(?:\.[A-Za-z_$][\w$]*)+)", routing_code
))
require(
    qualified_java_types == {"java.util.Objects"},
    f"Routing uses a non-allowlisted Java type: {qualified_java_types}",
)
routing_body = re.sub(
    r"^\s*(?:package|import)\s+[^;]+;\s*",
    "",
    routing_logic,
    flags=re.MULTILINE,
)
validate_exact_dotted_invocations(routing_body)
require(
    re.search(
        r"\b(?:com|org|net|io|java|javax|jakarta|jdk|sun)\s*\.",
        routing_body,
    ) is None,
    "Routing body must not bypass imports with a qualified type",
)
capitalized_type_tokens = set(re.findall(
    r"\b[A-Z][A-Za-z0-9_$]*\b", routing_body
))
require(
    capitalized_type_tokens
    == {
        "CalculatorSideRouting", "Objects", "CalculatorSideAdapter",
        "DirectionalWinSide", "TargetHitSide",
        "CallDirectionPolarityResolution", "Directional",
        "NonDirectional", "Result", "DirectionalRoute",
        "NonDirectionalRoute", "IllegalArgumentException",
    },
    f"Routing body type surface changed: {sorted(capitalized_type_tokens)}",
)
forbidden_tokens = (
    "CallDirectionPolarityRequest", "CallDirectionPolarityPolicyVersion",
    "CallDirectionPolarityResolver", "ResolutionContext",
    "DirectionalSide", "NonDirectionalReason", "TargetHitCalculator",
    "TargetHitInput", "TargetHitResult", "DirectionalWinCalculator",
    "DirectionalWinInput", "DirectionalWinResult", ".calculate(",
    "BigDecimal", "double", "float", "policyVersion",
    "canonicalDefinition", "definitionHash", "methodology",
    "fingerprint", "provenance", "Clock", "Instant", "Locale",
    "TimeZone", "Provider", "Repository", "ObjectMapper",
    "JdbcTemplate", "HttpClient", "@Component", "@Service",
    ".context(", ".reason(", ".direction(", ".definitionHash(",
)
require(
    not any(token in routing_code for token in forbidden_tokens)
    and re.search(r"\bCallDirection\b", routing_code) is None
    and re.search(r"\b(?:boolean|Boolean)\b", routing_logic) is None
    and "org.springframework" not in routing_code
    and "java.math." not in routing_code
    and "java.time." not in routing_code
    and "java.net." not in routing_code
    and "java.sql." not in routing_code
    and "java.security." not in routing_code
    and "java.util.concurrent." not in routing_code,
    "Routing crosses policy/calculator/runtime/data boundary",
)

api_main_dir = Path("apps/api/src/main/java")
direction_dir = (
    api_main_dir
    / "com/wallstreetreceipts/api/domain/outcome/direction"
).resolve()
adapter_path = (adapter_dir / "CalculatorSideAdapter.java").resolve()
target_eligibility_routing_references = {
    (api_main_dir / "com/wallstreetreceipts/api/domain/outcome/"
     "targeteligibility/TargetEligibilityRequest.java").resolve(): {
        "com.wallstreetreceipts.api.domain.outcome.routing."
        "CalculatorSideRouting",
    },
    (api_main_dir / "com/wallstreetreceipts/api/domain/outcome/"
     "targeteligibility/TargetEligibilityResolution.java").resolve(): {
        "com.wallstreetreceipts.api.domain.outcome.routing."
        "CalculatorSideRouting",
        "com.wallstreetreceipts.api.domain.outcome.routing."
        "CalculatorSideRouting.DirectionalRoute",
        "com.wallstreetreceipts.api.domain.outcome.routing."
        "CalculatorSideRouting.NonDirectionalRoute",
    },
    (api_main_dir / "com/wallstreetreceipts/api/domain/outcome/"
     "targeteligibility/TargetEligibilityResolver.java").resolve(): {
        "com.wallstreetreceipts.api.domain.outcome.routing."
        "CalculatorSideRouting",
        "com.wallstreetreceipts.api.domain.outcome.routing."
        "CalculatorSideRouting.DirectionalRoute",
        "com.wallstreetreceipts.api.domain.outcome.routing."
        "CalculatorSideRouting.NonDirectionalRoute",
    },
}
target_eligibility_routing_references.update({
    (api_main_dir / "com/wallstreetreceipts/api/domain/outcome/"
     "favorableextreme/FavorableExtremeResolution.java").resolve(): {
        "com.wallstreetreceipts.api.domain.outcome.routing."
        "CalculatorSideRouting.DirectionalRoute",
    },
    (api_main_dir / "com/wallstreetreceipts/api/domain/outcome/"
     "favorableextreme/FavorableExtremeSelector.java").resolve(): {
        "com.wallstreetreceipts.api.domain.outcome.routing."
        "CalculatorSideRouting.DirectionalRoute",
    },
    (api_main_dir / "com/wallstreetreceipts/api/domain/outcome/"
     "targethitorchestration/TargetHitOrchestrator.java").resolve(): {
        "com.wallstreetreceipts.api.domain.outcome.routing."
        "CalculatorSideRouting.DirectionalRoute",
    },
    (api_main_dir / "com/wallstreetreceipts/api/domain/outcome/"
     "directionalwinorchestration/"
     "DirectionalWinOrchestrationPolicyVersion.java").resolve(): set(),
    (api_main_dir / "com/wallstreetreceipts/api/domain/outcome/"
     "directionalwinorchestration/"
     "DirectionalWinOrchestrationRequest.java").resolve(): {
        "com.wallstreetreceipts.api.domain.outcome.routing."
        "CalculatorSideRouting",
        "com.wallstreetreceipts.api.domain.outcome.routing."
        "CalculatorSideRouting.DirectionalRoute",
        "com.wallstreetreceipts.api.domain.outcome.routing."
        "CalculatorSideRouting.NonDirectionalRoute",
    },
    (api_main_dir / "com/wallstreetreceipts/api/domain/outcome/"
     "directionalwinorchestration/"
     "DirectionalWinOrchestrationResolution.java").resolve(): {
        "com.wallstreetreceipts.api.domain.outcome.routing."
        "CalculatorSideRouting.DirectionalRoute",
        "com.wallstreetreceipts.api.domain.outcome.routing."
        "CalculatorSideRouting.NonDirectionalRoute",
    },
    (api_main_dir / "com/wallstreetreceipts/api/domain/outcome/"
     "directionalwinorchestration/"
     "DirectionalWinOrchestrator.java").resolve(): {
        "com.wallstreetreceipts.api.domain.outcome.routing."
        "CalculatorSideRouting.DirectionalRoute",
        "com.wallstreetreceipts.api.domain.outcome.routing."
        "CalculatorSideRouting.NonDirectionalRoute",
    },
})
for other_path in api_main_dir.rglob("*.java"):
    resolved = other_path.resolve()
    if resolved == routing_path.resolve():
        continue
    other_source = other_path.read_text(encoding="utf-8")
    other_code = without_comments(other_source)
    if resolved in target_eligibility_routing_references:
        other_logic = without_comments_or_strings(other_source)
        routing_imports = {
            imported
            for imported in re.findall(
                r"^import\s+([^;]+);", other_logic, flags=re.MULTILINE
            )
            if ".domain.outcome.routing." in imported
        }
        is_target_hit_orchestrator = (
            other_path.name == "TargetHitOrchestrator.java"
        )
        is_directional_win_orchestrator = (
            other_path.name == "DirectionalWinOrchestrator.java"
        )
        is_directional_win_resolution = (
            other_path.name
            == "DirectionalWinOrchestrationResolution.java"
        )
        calculation_boundary_ok = (
            (
                other_logic.count("TargetHitCalculator.calculate(") == 1
                and "TargetHitInput" in other_logic
                and "TargetHitResult" in other_logic
                and "DirectionalWinCalculator" not in other_logic
                and "DirectionalWinInput" not in other_logic
                and "DirectionalWinResult" not in other_logic
            )
            if is_target_hit_orchestrator
            else (
                other_logic.count(
                    "DirectionalWinCalculator.calculate("
                ) == 1
                and "DirectionalWinInput" in other_logic
                and "DirectionalWinResult" in other_logic
                and "TargetHitCalculator" not in other_logic
                and "TargetHitInput" not in other_logic
                and "TargetHitResult" not in other_logic
            )
            if is_directional_win_orchestrator
            else (
                "DirectionalWinResult" in other_logic
                and "DirectionalWinCalculator" not in other_logic
                and "DirectionalWinInput" not in other_logic
                and "TargetHitCalculator" not in other_logic
                and "TargetHitInput" not in other_logic
                and "TargetHitResult" not in other_logic
                and ".calculate(" not in other_logic
            )
            if is_directional_win_resolution
            else not any(marker in other_logic for marker in (
                "TargetHitCalculator", "TargetHitInput", "TargetHitResult",
                "DirectionalWinCalculator", "DirectionalWinInput",
                "DirectionalWinResult", ".calculate(",
            ))
        )
        require(
            routing_imports
            == target_eligibility_routing_references[resolved]
            and "CalculatorSideRouting.route(" not in other_logic
            and "CalculatorSideAdapter" not in other_logic
            and calculation_boundary_ok,
            "Approved composition policies may preserve only "
            "the exact closed routing evidence "
            f"without invoking a calculator: {other_path}",
        )
        continue
    require_no_routing_reverse_reference(other_source, other_path)
    if resolved != adapter_path:
        require(
            "CalculatorSideAdapter" not in other_code
            and "domain.outcome.adapter" not in other_code,
            f"Only routing may consume CalculatorSideAdapter: {other_path}",
        )
    if direction_dir not in resolved.parents and resolved != adapter_path:
        require(
            "CallDirectionPolarityResolution" not in other_code,
            f"Only routing may consume full polarity outside its owner: {other_path}",
        )

for marker in (
    "routesEveryCanonicalDirectionWithoutReinterpretingNeutral",
    "rejectsNullResolutionBeforeRouting",
    "directDirectionalRouteConstructionRecomputesBothExpectedSides",
    "directRouteConstructionRejectsMissingEvidence",
    "exposesOneRoutingMethodAndExactlyTwoClosedResultRecords",
    "replayDoesNotDependOnJvmDefaultLocaleOrTimeZone",
    "canonicalDirectionVectors",
    "directionalConstructorVectors",
):
    require(marker in golden_logic, f"Missing routing golden coverage: {marker}")
require(
    len(re.findall(r"@Test\b", golden_logic)) == 4
    and len(re.findall(r"@ParameterizedTest\b", golden_logic)) == 2
    and len(re.findall(r"@MethodSource\b", golden_logic)) == 2
    and '@ParameterizedTest(name="")@MethodSource("")'
    'voidroutesEveryCanonicalDirectionWithoutReinterpretingNeutral('
    in compact_golden_logic
    and '@ParameterizedTest(name="")@MethodSource("")'
    'voiddirectDirectionalRouteConstructionRecomputesBothExpectedSides('
    in compact_golden_logic
    and "@TestvoidrejectsNullResolutionBeforeRouting()"
    in compact_golden_logic
    and "@TestvoiddirectRouteConstructionRejectsMissingEvidence()"
    in compact_golden_logic
    and "@TestvoidexposesOneRoutingMethodAndExactlyTwoClosedResultRecords()"
    in compact_golden_logic
    and "@TestvoidreplayDoesNotDependOnJvmDefaultLocaleOrTimeZone()"
    in compact_golden_logic,
    "Routing golden annotations must remain active on the exact six tests",
)
require(
    re.search(r"@(?:Disabled|Enabled)\w*\b|@Tag\b", golden_logic)
    is None
    and not any(
        token in golden_logic
        for token in (
            "Assumptions", "assumeTrue", "assumeFalse",
            "assumingThat", "TestAbortedException",
        )
    )
    and len(re.findall(r"\btry\b", golden_logic)) == 1
    and re.search(r"\bcatch\b", golden_logic) is None
    and len(re.findall(r"\bfinally\b", golden_logic)) == 1,
    "Routing goldens must not disable, condition, assume, or swallow tests",
)
for marker in (
    "Arguments.of(CallDirection.STRONG_BULLISH,"
    "TargetHitSide.BULLISH,DirectionalWinSide.BULLISH)",
    "Arguments.of(CallDirection.BULLISH,TargetHitSide.BULLISH,"
    "DirectionalWinSide.BULLISH)",
    "Arguments.of(CallDirection.NEUTRAL,null,null)",
    "Arguments.of(CallDirection.BEARISH,TargetHitSide.BEARISH,"
    "DirectionalWinSide.BEARISH)",
    "Arguments.of(CallDirection.STRONG_BEARISH,"
    "TargetHitSide.BEARISH,DirectionalWinSide.BEARISH)",
    "Arguments.of(CallDirection.BULLISH,TargetHitSide.BULLISH,"
    "DirectionalWinSide.BULLISH,TargetHitSide.BEARISH,"
    "DirectionalWinSide.BEARISH)",
    "Arguments.of(CallDirection.BEARISH,TargetHitSide.BEARISH,"
    "DirectionalWinSide.BEARISH,TargetHitSide.BULLISH,"
    "DirectionalWinSide.BULLISH)",
    "assertThatThrownBy(()->newDirectionalRoute(source,"
    "contradictoryTargetHitSide,expectedDirectionalWinSide))",
    "assertThatThrownBy(()->newDirectionalRoute(source,"
    "expectedTargetHitSide,contradictoryDirectionalWinSide))",
    'hasMessageContaining("")',
    "CalculatorSideRouting.route(null)",
    "newDirectionalRoute(null,TargetHitSide.BEARISH,"
    "DirectionalWinSide.BEARISH)",
    "newDirectionalRoute(bearish,null,DirectionalWinSide.BEARISH)",
    "newDirectionalRoute(bearish,TargetHitSide.BEARISH,null)",
    "newNonDirectionalRoute(null)",
    "isSameAs(directional)", "isSameAs(nonDirectional)",
    "Modifier.isPublic(outerModifiers)",
    "Modifier.isFinal(outerModifiers)",
    "CalculatorSideRouting.class.getDeclaredFields()).isEmpty()",
    "Method[]declaredMethods=CalculatorSideRouting.class."
    "getDeclaredMethods()",
    "assertThat(declaredMethods).singleElement()",
    'assertThat(method.getName()).isEqualTo("")',
    "Modifier.isPublic(method.getModifiers())",
    "Modifier.isPrivate(constructor.getModifiers())",
    "constructor.getParameterTypes()).isEmpty()",
    "method.getTypeParameters()).isEmpty()",
    "method.getExceptionTypes()).isEmpty()",
    "Modifier.isPublic(resultModifiers)",
    "Modifier.isStatic(resultModifiers)",
    "Result.class.isInterface()",
    "Result.class.isSealed()",
    "Result.class.getDeclaredFields()).isEmpty()",
    "Result.class.getDeclaredMethods()).isEmpty()",
    "Result.class.getPermittedSubclasses())."
    "containsExactlyInAnyOrder(",
    "CalculatorSideRouting.class.getDeclaredClasses())."
    "containsExactlyInAnyOrder(",
    'tuple("",Directional.class)',
    'tuple("",TargetHitSide.class)',
    'tuple("",DirectionalWinSide.class)',
    'tuple("",NonDirectional.class)',
    "assertClosedRecordSurface(DirectionalRoute.class,",
    "assertClosedRecordSurface(NonDirectionalRoute.class,",
    "recordType.getDeclaredConstructors()).singleElement()",
    "Modifier.isPublic(constructor.getModifiers())",
    "constructor.getParameterTypes()).containsExactly(componentTypes)",
    "recordType.getDeclaredFields()).hasSize(components.length)",
    "Modifier.isPrivate(field.getModifiers())",
    "Modifier.isFinal(field.getModifiers())",
    "Modifier.isStatic(field.getModifiers())).isFalse()",
    "recordType.getDeclaredMethods()).allSatisfy(method->",
    "Modifier.isStatic(method.getModifiers())).isFalse()",
    "method.getTypeParameters()).isEmpty()",
    "method.getExceptionTypes()).isEmpty()",
    "containsExactlyInAnyOrder(expectedMethods)",
    "Locale.setDefault", "TimeZone.setDefault", "finally",
):
    require(
        marker in compact_golden_logic,
        f"Missing executable mutation-sensitive routing golden: {marker}",
    )
require(
    compact_golden.count(
        'assertThat(method.getName()).isEqualTo("route")'
    ) == 1
    and compact_golden.count(
        'hasMessageContaining("resolution")'
    ) == 1
    and compact_golden.count(
        'hasMessageContaining("targetHitSide")'
    ) == 2
    and compact_golden.count(
        'hasMessageContaining("directionalWinSide")'
    ) == 2
    and compact_golden.count(
        'hasMessageContaining("source")'
    ) == 2
    and compact_golden.count(
        'tuple("source",Directional.class)'
    ) == 1
    and compact_golden.count(
        'tuple("targetHitSide",TargetHitSide.class)'
    ) == 1
    and compact_golden.count(
        'tuple("directionalWinSide",DirectionalWinSide.class)'
    ) == 1
    and compact_golden.count(
        'tuple("source",NonDirectional.class)'
    ) == 1
    and compact_golden.count(
        'tuple("equals",List.of(Object.class),boolean.class)'
    ) == 2
    and compact_golden.count(
        'tuple("toString",List.of(),String.class)'
    ) == 2
    and compact_golden.count(
        'tuple("hashCode",List.of(),int.class)'
    ) == 2
    and compact_golden.count(
        'tuple("source",List.of(),Directional.class)'
    ) == 1
    and compact_golden.count(
        'tuple("targetHitSide",List.of(),TargetHitSide.class)'
    ) == 1
    and compact_golden.count(
        'tuple("directionalWinSide",List.of(),DirectionalWinSide.class)'
    ) == 1
    and compact_golden.count(
        'tuple("source",List.of(),NonDirectional.class)'
    ) == 1,
    "Routing golden literal expectations changed",
)
require(
    compact_golden_logic.count(
        'tuple("",List.of(Object.class),boolean.class)'
    ) == 2
    and compact_golden_logic.count(
        'tuple("",List.of(),String.class)'
    ) == 2
    and compact_golden_logic.count(
        'tuple("",List.of(),int.class)'
    ) == 2
    and 'tuple("",List.of(),Directional.class)'
    in compact_golden_logic
    and 'tuple("",List.of(),TargetHitSide.class)'
    in compact_golden_logic
    and 'tuple("",List.of(),DirectionalWinSide.class)'
    in compact_golden_logic
    and 'tuple("",List.of(),NonDirectional.class)'
    in compact_golden_logic,
    "Routing record goldens must lock exact generated method surfaces",
)
require(
    compact_golden_logic.index(
        "Arguments.of(CallDirection.STRONG_BULLISH,"
        "TargetHitSide.BULLISH,DirectionalWinSide.BULLISH)"
    )
    < compact_golden_logic.index(
        "Arguments.of(CallDirection.BULLISH,TargetHitSide.BULLISH,"
        "DirectionalWinSide.BULLISH)"
    )
    < compact_golden_logic.index(
        "Arguments.of(CallDirection.NEUTRAL,null,null)"
    )
    < compact_golden_logic.index(
        "Arguments.of(CallDirection.BEARISH,TargetHitSide.BEARISH,"
        "DirectionalWinSide.BEARISH)"
    )
    < compact_golden_logic.index(
        "Arguments.of(CallDirection.STRONG_BEARISH,"
        "TargetHitSide.BEARISH,DirectionalWinSide.BEARISH)"
    )
    and "ObjectMapper" not in golden_logic
    and "ClassPathResource" not in golden_logic,
    "Routing goldens must preserve canonical order and remain source-local",
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
require(
    {path.name for path in Path("schemas").glob("*.json")} == expected_schemas,
    "Routing must preserve the exact 14 schemas",
)
fixture_dir = Path("fixtures/v1")
expected_fixture_files = {
    "analyst-call-revisions.json", "analyst-calls.json",
    "call-contexts.json", "call-outcomes.json", "manifest.json",
    "market-board.json", "market-map-nasdaq100.json", "market-map.json",
    "market-snapshots.json", "market-treemap-nasdaq100.json",
    "market-treemap-sp500.json", "master-data.json", "timeline-nvda.json",
}
require(
    {path.name for path in fixture_dir.glob("*.json")}
    == expected_fixture_files,
    "Routing must not add canonical fixtures",
)
manifest = json.loads((fixture_dir / "manifest.json").read_text(encoding="utf-8"))
require(
    [entry["path"] for entry in manifest["files"]]
    == [
        "master-data.json", "analyst-calls.json",
        "analyst-call-revisions.json", "call-outcomes.json",
        "call-contexts.json", "market-snapshots.json", "market-map.json",
        "market-map-nasdaq100.json", "market-treemap-sp500.json",
        "market-treemap-nasdaq100.json", "timeline-nvda.json",
        "market-board.json",
    ],
    "Routing must preserve manifest membership/order",
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
    and all(item["status"] == "MODEL_ONLY" for item in outcomes["methodologies"])
    and len(outcomes["outcomes"]) == 4
    and {item["evaluationStatus"] for item in outcomes["outcomes"]}
    == {"PENDING", "INCOMPLETE"}
    and all(item[field] is None for item in outcomes["outcomes"] for field in metrics),
    "Routing must not activate methodology or publish a calculation",
)
openapi_source = Path("contracts/openapi.yaml").read_text(encoding="utf-8")
require(
    set(re.findall(r"^  (/[^\n]+):\s*$", openapi_source, re.MULTILINE))
    == {
        "/v1/calls", "/v1/calls/{id}", "/v1/calls/{id}/revisions",
        "/v1/calls/{id}/outcomes", "/v1/calls/{id}/context",
    },
    "Routing must preserve the exact five OpenAPI paths",
)
require(
    {
        path.name
        for path in Path("apps/api/src/main/resources/db/migration").glob("*.sql")
    }
    == {
        "V1__baseline.sql", "V2__analyst_calls.sql",
        "V3__analyst_call_revisions.sql", "V4__call_outcomes.sql",
        "V5__call_contexts.sql",
        "V6__sec_filing_catalog_captures.sql",
        "V7__sec_historical_filing_segment_captures.sql",
        "V8__sec_filing_history_collection_manifests.sql",
        "V9__sec_filing_collection_attempts.sql",
    },
    "Routing must preserve the exact nine Flyway migrations",
)
for resource_path in Path("apps/api/src/test/resources").rglob("*.json"):
    require(
        "CalculatorSideRouting" not in resource_path.read_text(encoding="utf-8"),
        f"Routing must not add a JSON golden: {resource_path}",
    )
for web_path in Path("apps/web/src").rglob("*"):
    if web_path.is_file() and web_path.suffix in {".ts", ".tsx", ".js", ".jsx"}:
        web_source = web_path.read_text(encoding="utf-8")
        require(
            "CalculatorSideRouting" not in web_source
            and "domain.outcome.routing" not in web_source,
            f"Routing must not expand the web surface: {web_path}",
        )

print(
    "Validated one closed calculator-side routing leaf, exact five-direction "
    "source preservation, explicit neutral evidence, constructor consistency, "
    "sole reverse wiring, and no calculator/provider/product publication"
)
PYTHON
