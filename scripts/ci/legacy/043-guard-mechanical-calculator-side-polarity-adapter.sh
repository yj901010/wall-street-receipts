python <<'PYTHON'
import json
import re
from pathlib import Path

def require(condition, message):
    if not condition:
        raise ValueError(message)

def without_comments(source):
    source = re.sub(r"/\*.*?\*/", "", source, flags=re.DOTALL)
    return re.sub(r"//.*", "", source)

def without_comments_or_strings(source):
    source = without_comments(source)
    return re.sub(r'"(?:\\.|[^"\\])*"', '""', source)

def compact(source):
    return re.sub(r"\s+", "", source)

adapter_dir = Path(
    "apps/api/src/main/java/com/wallstreetreceipts/api/domain/outcome/adapter"
)
test_dir = Path(
    "apps/api/src/test/java/com/wallstreetreceipts/api/domain/outcome/adapter"
)
expected_production_files = {"CalculatorSideAdapter.java"}
expected_test_files = {"CalculatorSideAdapterGoldenTest.java"}
production_paths = sorted(
    path for path in adapter_dir.rglob("*.java") if path.is_file()
)
test_paths = sorted(
    path for path in test_dir.rglob("*.java") if path.is_file()
)
require(
    {path.relative_to(adapter_dir).as_posix() for path in production_paths}
    == expected_production_files,
    "Calculator-side adapter must contain exactly one production file",
)
require(
    {path.relative_to(test_dir).as_posix() for path in test_paths}
    == expected_test_files,
    "Calculator-side adapter must contain exactly one golden file",
)

adapter_path = adapter_dir / "CalculatorSideAdapter.java"
golden_path = test_dir / "CalculatorSideAdapterGoldenTest.java"
adapter_source = adapter_path.read_text(encoding="utf-8")
golden_source = golden_path.read_text(encoding="utf-8")
compact_adapter = compact(adapter_source)
compact_golden = compact(golden_source)
require(
    "package com.wallstreetreceipts.api.domain.outcome.adapter;"
    in adapter_source
    and "package com.wallstreetreceipts.api.domain.outcome.adapter;"
    in golden_source,
    "Calculator-side adapter and golden must remain in the exact package",
)
require(
    "publicfinalclassCalculatorSideAdapter{" in compact_adapter
    and "privateCalculatorSideAdapter(){}" in compact_adapter,
    "CalculatorSideAdapter must be final with one private constructor",
)

code_without_comments = without_comments(adapter_source)
public_methods = re.findall(
    r"public\s+static\s+(\w+)\s+(\w+)\s*\(\s*(\w+)\s+(\w+)\s*\)",
    code_without_comments,
)
require(
    public_methods
    == [
        ("TargetHitSide", "toTargetHitSide", "DirectionalSide", "side"),
        (
            "DirectionalWinSide", "toDirectionalWinSide",
            "DirectionalSide", "side",
        ),
    ],
    f"Unexpected calculator-side public static methods: {public_methods}",
)
require(
    len(re.findall(r"\bpublic\s+", code_without_comments)) == 3
    and code_without_comments.count("private CalculatorSideAdapter()") == 1,
    "Adapter may expose only its final class and two static methods",
)
for marker in (
    "publicstaticTargetHitSidetoTargetHitSide(DirectionalSideside)",
    "Objects.requireNonNull(side,\"sidemustnotbenull\")",
    "caseBULLISH->TargetHitSide.BULLISH",
    "caseBEARISH->TargetHitSide.BEARISH",
    "publicstaticDirectionalWinSidetoDirectionalWinSide("
    "DirectionalSideside)",
    "caseBULLISH->DirectionalWinSide.BULLISH",
    "caseBEARISH->DirectionalWinSide.BEARISH",
):
    require(marker in compact_adapter, f"Missing exact adapter mapping: {marker}")
require(
    compact_adapter.count(
        "Objects.requireNonNull(side,\"sidemustnotbenull\")"
    ) == 2,
    "Both adapter methods must reject null independently",
)

mapping_code = without_comments_or_strings(adapter_source)
require(
    re.search(
        r"\.\s*(?:ordinal|name)\s*\(|\bvalueOf\s*\(|"
        r"\bdefault\s*(?::|->)|\bgetOrDefault\s*\(|"
        r"\bto(?:Lower|Upper)Case\s*\(|\bMap\s*<|"
        r"\b(?:Class|Method)\b",
        mapping_code,
    ) is None,
    "Adapter must use exhaustive enum switches without reflection/fallback",
)
require(
    re.search(
        r"\b(?:System|Runtime|Thread|Process|ProcessBuilder|"
        r"Math|StrictMath)\b",
        mapping_code,
    ) is None,
    "Adapter must not depend on environment, time, process, thread, or random state",
)
imports = re.findall(
    r"^import\s+([^;]+);", code_without_comments, flags=re.MULTILINE
)
require(
    set(imports)
    == {
        "java.util.Objects",
        "com.wallstreetreceipts.api.domain.outcome.calculation.DirectionalWinSide",
        "com.wallstreetreceipts.api.domain.outcome.calculation.TargetHitSide",
        "com.wallstreetreceipts.api.domain.outcome.direction."
        "CallDirectionPolarityResolution.DirectionalSide",
    },
    f"Adapter import boundary changed: {imports}",
)
require(
    code_without_comments.count("CallDirectionPolarityResolution") == 1
    and code_without_comments.count(
        "CallDirectionPolarityResolution.DirectionalSide"
    ) == 1,
    "Adapter may reference the polarity resolution only through one nested-side import",
)
qualified_java_types = set(re.findall(
    r"\b(java(?:\.[A-Za-z_$][\w$]*)+)", code_without_comments
))
require(
    qualified_java_types == {"java.util.Objects"},
    f"Adapter uses a non-allowlisted Java type: {qualified_java_types}",
)
forbidden_tokens = (
    "CallDirectionPolarityRequest", "CallDirectionPolarityPolicyVersion",
    "CallDirectionPolarityResolver", "NonDirectional",
    "NonDirectionalReason", "ResolutionContext",
    "TargetHitCalculator", "TargetHitInput", "TargetHitResult",
    "DirectionalWinCalculator", "DirectionalWinInput",
    "DirectionalWinResult", ".calculate(", "BigDecimal", "double",
    "float", "policyVersion", "canonicalDefinition", "definitionHash",
    "methodology", "fingerprint", "provenance", "Clock", "Instant",
    "Locale", "TimeZone", "Provider", "Repository", "ObjectMapper",
    "JdbcTemplate", "HttpClient", "@Component", "@Service",
)
require(
    not any(token in code_without_comments for token in forbidden_tokens)
    and re.search(r"\bCallDirection\b", code_without_comments) is None
    and "org.springframework" not in code_without_comments
    and "java.math." not in code_without_comments
    and "java.time." not in code_without_comments
    and "java.net." not in code_without_comments
    and "java.sql." not in code_without_comments
    and "java.security." not in code_without_comments
    and "java.util.concurrent." not in code_without_comments,
    "Adapter crosses neutral/calculator/policy/runtime/data boundary",
)

api_main_dir = Path("apps/api/src/main/java")
calculator_side_routing_path = (
    api_main_dir
    / "com/wallstreetreceipts/api/domain/outcome/routing/CalculatorSideRouting.java"
).resolve()
for other_path in api_main_dir.rglob("*.java"):
    if other_path.resolve() == adapter_path.resolve():
        continue
    other_source = other_path.read_text(encoding="utf-8")
    if other_path.resolve() == calculator_side_routing_path:
        routing_code = without_comments_or_strings(other_source)
        require(
            routing_code.count("CalculatorSideAdapter") == 5
            and routing_code.count(
                "CalculatorSideAdapter.toTargetHitSide"
            ) == 2
            and routing_code.count(
                "CalculatorSideAdapter.toDirectionalWinSide"
            ) == 2
            and "import com.wallstreetreceipts.api.domain.outcome.adapter."
            "CalculatorSideAdapter;" in routing_code,
            "ADR-013 routing must be the sole exact adapter consumer",
        )
        continue
    require(
        "CalculatorSideAdapter" not in other_source
        and "domain.outcome.adapter" not in other_source,
        f"Calculator-side adapter must not be runtime-wired: {other_path}",
    )

for marker in (
    "calculatorSideVectors",
    "translatesBothCommonSidesToBothCalculatorVocabularies",
    "nullInputVectors",
    "rejectsNullSideBeforeTranslation",
    "exposesOnlyTwoDirectionalSideTranslationMethodsAndNoPublicConstructor",
    "translationsDoNotDependOnJvmDefaultLocaleOrTimeZone",
):
    require(marker in golden_source, f"Missing adapter golden coverage: {marker}")
for marker in (
    "Arguments.of(DirectionalSide.BULLISH,TargetHitSide.BULLISH,"
    "DirectionalWinSide.BULLISH)",
    "Arguments.of(DirectionalSide.BEARISH,TargetHitSide.BEARISH,"
    "DirectionalWinSide.BEARISH)",
    "CalculatorSideAdapter.toTargetHitSide(null)",
    "CalculatorSideAdapter.toDirectionalWinSide(null)",
    "containsExactly(DirectionalSide.class)",
    "\"toTargetHitSide\",TargetHitSide.class",
    "\"toDirectionalWinSide\",DirectionalWinSide.class",
    "Modifier.isStatic", "Modifier.isFinal", "Modifier.isPrivate",
    "Locale.setDefault", "TimeZone.setDefault", "finally",
):
    require(marker in compact_golden, f"Missing mutation-sensitive adapter golden: {marker}")
require(
    compact_golden.index(
        "Arguments.of(DirectionalSide.BULLISH,TargetHitSide.BULLISH,"
        "DirectionalWinSide.BULLISH)"
    )
    < compact_golden.index(
        "Arguments.of(DirectionalSide.BEARISH,TargetHitSide.BEARISH,"
        "DirectionalWinSide.BEARISH)"
    )
    and "ObjectMapper" not in golden_source
    and "ClassPathResource" not in golden_source,
    "Adapter goldens must preserve source order and remain source-local",
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
    "Adapter must preserve the exact 14 schemas",
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
    "Adapter must not add canonical fixtures",
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
    "Adapter must preserve manifest membership/order",
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
    "Adapter must not activate methodology or publish a calculation",
)
openapi_source = Path("contracts/openapi.yaml").read_text(encoding="utf-8")
require(
    set(re.findall(r"^  (/[^\n]+):\s*$", openapi_source, re.MULTILINE))
    == {
        "/v1/calls", "/v1/calls/{id}", "/v1/calls/{id}/revisions",
        "/v1/calls/{id}/outcomes", "/v1/calls/{id}/context",
    },
    "Adapter must preserve the exact five OpenAPI paths",
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
    "Adapter must preserve the exact nine Flyway migrations",
)
for resource_path in Path("apps/api/src/test/resources").rglob("*.json"):
    require(
        "CalculatorSideAdapter" not in resource_path.read_text(encoding="utf-8"),
        f"Adapter must not add a JSON golden: {resource_path}",
    )
for web_path in Path("apps/web/src").rglob("*"):
    if web_path.is_file() and web_path.suffix in {".ts", ".tsx", ".js", ".jsx"}:
        web_source = web_path.read_text(encoding="utf-8")
        require(
            "CalculatorSideAdapter" not in web_source
            and "domain.outcome.adapter" not in web_source,
            f"Adapter must not expand the web surface: {web_path}",
        )

print(
    "Validated one mechanical calculator-side adapter, exact BULLISH/BEARISH "
    "translations, null/neutral closure, two public static methods, source-local "
    "goldens, and no calculator/provider/product publication"
)
PYTHON
