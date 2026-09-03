python <<'PYTHON'
import json
import re
from pathlib import Path

def require(condition, message):
    if not condition:
        raise ValueError(message)

def without_comments_or_strings(source):
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
                output.append('""' if current == '"' else "''")
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
                index += 2
            elif current == quote:
                state = "CODE"
                quote = None
                index += 1
            else:
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

calculation_dir = Path(
    "apps/api/src/main/java/com/wallstreetreceipts/api/domain/outcome/calculation"
)
test_dir = Path(
    "apps/api/src/test/java/com/wallstreetreceipts/api/domain/outcome/calculation"
)
expected_production_files = {
    "DirectionalWinSide.java",
    "DirectionalWinInput.java",
    "DirectionalWinResult.java",
    "DirectionalWinCalculator.java",
    "TargetHitSide.java",
    "TargetHitInput.java",
    "TargetHitResult.java",
    "TargetHitCalculator.java",
}
expected_test_files = {
    "DirectionalWinCalculatorGoldenTest.java",
    "TargetHitCalculatorGoldenTest.java",
}
production_paths = sorted(
    path for path in calculation_dir.rglob("*.java") if path.is_file()
)
test_paths = sorted(path for path in test_dir.rglob("*.java") if path.is_file())
actual_production_files = {
    path.relative_to(calculation_dir).as_posix() for path in production_paths
}
actual_test_files = {
    path.relative_to(test_dir).as_posix() for path in test_paths
}
require(
    actual_production_files == expected_production_files,
    f"Unexpected pure-calculation production files: {sorted(actual_production_files)}",
)
require(
    actual_test_files == expected_test_files,
    f"Unexpected pure-calculation test files: {sorted(actual_test_files)}",
)

production_sources = {
    path.relative_to(calculation_dir).as_posix(): path.read_text(encoding="utf-8")
    for path in production_paths
}
input_source = production_sources["TargetHitInput.java"]
side_source = production_sources["TargetHitSide.java"]
result_source = production_sources["TargetHitResult.java"]
calculator_source = production_sources["TargetHitCalculator.java"]
directional_input_source = production_sources["DirectionalWinInput.java"]
directional_side_source = production_sources["DirectionalWinSide.java"]
directional_result_source = production_sources["DirectionalWinResult.java"]
directional_calculator_source = production_sources["DirectionalWinCalculator.java"]
compact_input = re.sub(r"\s+", "", input_source)
require(
    "publicrecordTargetHitInput(TargetHitSideside,BigDecimaltarget,"
    "BigDecimalfavorableExtreme)" in compact_input,
    "TargetHitInput must contain only side, target, and favorableExtreme",
)
require(
    re.search(r"enum\s+TargetHitSide\s*\{\s*BULLISH\s*,\s*BEARISH\s*\}", side_source)
    is not None,
    "TargetHitSide must contain exactly BULLISH then BEARISH",
)
require(
    "permits TargetHitResult.Available, TargetHitResult.Unavailable" in result_source,
    "TargetHitResult must be a closed Available/Unavailable result",
)
reason_match = re.search(
    r"enum\s+UnavailableReason\s*\{(?P<body>.*?)\}",
    result_source,
    flags=re.DOTALL,
)
require(reason_match is not None, "Missing target-hit unavailable reasons")
reasons = re.findall(r"\b[A-Z][A-Z_]+\b", reason_match.group("body"))
require(
    reasons
    == [
        "TARGET_MISSING",
        "FAVORABLE_EXTREME_MISSING",
        "TARGET_AND_FAVORABLE_EXTREME_MISSING",
    ],
    f"Unexpected target-hit unavailable reasons: {reasons}",
)
require(
    "record Available(boolean targetHit)" in result_source
    and "record Unavailable(UnavailableReason reason)" in result_source,
    "TargetHitResult payloads must be exact",
)
require(
    "case BULLISH -> comparison >= 0;" in calculator_source
    and "case BEARISH -> comparison <= 0;" in calculator_source
    and "favorableExtreme.compareTo(input.target())" in calculator_source,
    "Target-hit comparison must be inclusive and use the preselected extreme",
)
calculator_forbidden = (
    ".add(",
    ".subtract(",
    ".multiply(",
    ".divide(",
    ".pow(",
    ".round(",
    ".setScale(",
    ".doubleValue(",
    ".floatValue(",
    "MathContext",
    "new BigDecimal",
    "BigDecimal.valueOf",
    ".parse",
)
for calculator_name, pure_calculator_source in {
    "TargetHitCalculator.java": calculator_source,
    "DirectionalWinCalculator.java": directional_calculator_source,
}.items():
    require(
        not any(
            marker in pure_calculator_source for marker in calculator_forbidden
        ),
        f"Pure calculator must not hide arithmetic, rounding, conversion, or parsing: {calculator_name}",
    )
require(
    "setScale(STORAGE_SCALE, RoundingMode.UNNECESSARY)" in input_source
    and "STORAGE_SCALE = 12" in input_source
    and "STORAGE_PRECISION = 38" in input_source,
    "Target-hit input must prove exact NUMERIC(38,12) representability",
)

compact_directional_input = re.sub(r"\s+", "", directional_input_source)
require(
    "publicrecordDirectionalWinInput(DirectionalWinSideside,"
    "BigDecimalassetReturn)" in compact_directional_input,
    "DirectionalWinInput must contain only side and nullable assetReturn",
)
require(
    re.search(
        r"enum\s+DirectionalWinSide\s*\{\s*BULLISH\s*,\s*BEARISH\s*\}",
        directional_side_source,
    ) is not None,
    "DirectionalWinSide must contain exactly BULLISH then BEARISH",
)
require(
    "permits DirectionalWinResult.Available, DirectionalWinResult.Unavailable"
    in directional_result_source,
    "DirectionalWinResult must be a closed Available/Unavailable result",
)
directional_reason_match = re.search(
    r"enum\s+UnavailableReason\s*\{(?P<body>.*?)\}",
    directional_result_source,
    flags=re.DOTALL,
)
require(
    directional_reason_match is not None,
    "Missing directional-win unavailable reason",
)
directional_reasons = re.findall(
    r"\b[A-Z][A-Z_]+\b", directional_reason_match.group("body")
)
require(
    directional_reasons == ["ASSET_RETURN_MISSING"],
    f"Unexpected directional-win unavailable reasons: {directional_reasons}",
)
require(
    "record Available(boolean directionalWin)" in directional_result_source
    and "record Unavailable(UnavailableReason reason)"
    in directional_result_source,
    "DirectionalWinResult payloads must be exact",
)
require(
    "input.assetReturn().compareTo(BigDecimal.ZERO)"
    in directional_calculator_source
    and "case BULLISH -> comparison > 0;" in directional_calculator_source
    and "case BEARISH -> comparison < 0;" in directional_calculator_source,
    "Directional-win comparison must use strict sign semantics",
)
require(
    "setScale(STORAGE_SCALE, RoundingMode.UNNECESSARY)"
    in directional_input_source
    and "STORAGE_SCALE = 12" in directional_input_source
    and "STORAGE_PRECISION = 38" in directional_input_source,
    "Directional-win input must prove exact signed NUMERIC(38,12) representability",
)
require(
    "signum" not in directional_input_source
    and "must be positive" not in directional_input_source.lower()
    and "must be negative" not in directional_input_source.lower(),
    "Directional-win input must accept negative, zero, and positive returns",
)

allowed_internal_prefix = (
    "com.wallstreetreceipts.api.domain.outcome.calculation."
)
allowed_java_imports = {
    "java.math.BigDecimal",
    "java.math.RoundingMode",
    "java.util.Objects",
}
forbidden_words = re.compile(
    r"\b(?:CallOutcome|AnalystCall|Clock|Instant|Provider|Repository|"
    r"ObjectMapper|JdbcTemplate|HttpClient|DataSource|DriverManager|"
    r"Connection|PreparedStatement|ResultSet|Locale|TimeZone|Random|"
    r"SecureRandom|ThreadLocalRandom|ScheduledExecutorService|"
    r"ScheduledThreadPoolExecutor|Timer|double|float)\b"
)
for source_name, source in production_sources.items():
    imports = re.findall(r"^import\s+([^;]+);", source, flags=re.MULTILINE)
    require(
        all(
            imported in allowed_java_imports
            or imported.startswith(allowed_internal_prefix)
            for imported in imports
        ),
        f"Calculation source crosses the pure domain boundary: {source_name} {imports}",
    )
    require(
        "org.springframework" not in source
        and forbidden_words.search(source) is None
        and "Math.random" not in source
        and "System.currentTimeMillis" not in source
        and "System.nanoTime" not in source
        and "java.net." not in source
        and "java.sql." not in source
        and "java.time." not in source
        and "java.security." not in source
        and "java.util.concurrent." not in source
        and "@Component" not in source
        and "@Service" not in source,
        f"Calculation source contains a forbidden runtime dependency: {source_name}",
    )

api_main_dir = Path("apps/api/src/main/java")
calculator_side_adapter_path = (
    api_main_dir
    / "com/wallstreetreceipts/api/domain/outcome/adapter/CalculatorSideAdapter.java"
).resolve()
calculator_side_routing_path = (
    api_main_dir
    / "com/wallstreetreceipts/api/domain/outcome/routing/CalculatorSideRouting.java"
).resolve()
favorable_extreme_side_consumers = {
    (
        api_main_dir
        / "com/wallstreetreceipts/api/domain/outcome/favorableextreme/"
        "FavorableExtremeResolution.java"
    ).resolve(),
    (
        api_main_dir
        / "com/wallstreetreceipts/api/domain/outcome/favorableextreme/"
        "FavorableExtremeSelector.java"
    ).resolve(),
}
target_hit_orchestration_consumers = {
    (
        api_main_dir
        / "com/wallstreetreceipts/api/domain/outcome/"
        "targethitorchestration/TargetHitOrchestrationPolicyVersion.java"
    ).resolve(): set(),
    (
        api_main_dir
        / "com/wallstreetreceipts/api/domain/outcome/"
        "targethitorchestration/TargetHitOrchestrationRequest.java"
    ).resolve(): set(),
    (
        api_main_dir
        / "com/wallstreetreceipts/api/domain/outcome/"
        "targethitorchestration/TargetHitOrchestrationResolution.java"
    ).resolve(): {
        "com.wallstreetreceipts.api.domain.outcome.calculation."
        "TargetHitResult",
    },
    (
        api_main_dir
        / "com/wallstreetreceipts/api/domain/outcome/"
        "targethitorchestration/TargetHitOrchestrator.java"
    ).resolve(): {
        "com.wallstreetreceipts.api.domain.outcome.calculation."
        "TargetHitCalculator",
        "com.wallstreetreceipts.api.domain.outcome.calculation."
        "TargetHitInput",
        "com.wallstreetreceipts.api.domain.outcome.calculation."
        "TargetHitResult",
    },
}
directional_win_orchestration_consumers = {
    (
        api_main_dir
        / "com/wallstreetreceipts/api/domain/outcome/"
        "directionalwinorchestration/"
        "DirectionalWinOrchestrationPolicyVersion.java"
    ).resolve(): set(),
    (
        api_main_dir
        / "com/wallstreetreceipts/api/domain/outcome/"
        "directionalwinorchestration/"
        "DirectionalWinOrchestrationRequest.java"
    ).resolve(): set(),
    (
        api_main_dir
        / "com/wallstreetreceipts/api/domain/outcome/"
        "directionalwinorchestration/"
        "DirectionalWinOrchestrationResolution.java"
    ).resolve(): {
        "com.wallstreetreceipts.api.domain.outcome.calculation."
        "DirectionalWinResult",
    },
    (
        api_main_dir
        / "com/wallstreetreceipts/api/domain/outcome/"
        "directionalwinorchestration/DirectionalWinOrchestrator.java"
    ).resolve(): {
        "com.wallstreetreceipts.api.domain.outcome.calculation."
        "DirectionalWinCalculator",
        "com.wallstreetreceipts.api.domain.outcome.calculation."
        "DirectionalWinInput",
        "com.wallstreetreceipts.api.domain.outcome.calculation."
        "DirectionalWinResult",
    },
}
calculation_markers = tuple(
    path.removesuffix(".java") for path in expected_production_files
)
for other_path in api_main_dir.rglob("*.java"):
    if calculation_dir in other_path.parents:
        continue
    if other_path.resolve() == calculator_side_adapter_path:
        continue
    other_source = other_path.read_text(encoding="utf-8")
    if other_path.resolve() == calculator_side_routing_path:
        routing_code = without_comments_or_strings(other_source)
        routing_imports = set(re.findall(
            r"^import\s+([^;]+);", routing_code, flags=re.MULTILINE
        ))
        routing_calculation_imports = {
            imported
            for imported in routing_imports
            if ".domain.outcome.calculation." in imported
        }
        require(
            routing_calculation_imports
            == {
                "com.wallstreetreceipts.api.domain.outcome.calculation."
                "TargetHitSide",
                "com.wallstreetreceipts.api.domain.outcome.calculation."
                "DirectionalWinSide",
            }
            and "TargetHitCalculator" not in routing_code
            and "TargetHitInput" not in routing_code
            and "TargetHitResult" not in routing_code
            and "DirectionalWinCalculator" not in routing_code
            and "DirectionalWinInput" not in routing_code
            and "DirectionalWinResult" not in routing_code
            and ".calculate(" not in routing_code,
            "ADR-013 routing may consume only calculation side enums",
        )
        continue
    if other_path.resolve() in favorable_extreme_side_consumers:
        consumer_code = without_comments_or_strings(other_source)
        calculation_imports = {
            imported
            for imported in re.findall(
                r"^import\s+([^;]+);",
                consumer_code,
                flags=re.MULTILINE,
            )
            if ".domain.outcome.calculation." in imported
        }
        require(
            calculation_imports
            == {
                "com.wallstreetreceipts.api.domain.outcome.calculation."
                "TargetHitSide"
            }
            and "TargetHitCalculator" not in consumer_code
            and "TargetHitInput" not in consumer_code
            and "TargetHitResult" not in consumer_code
            and "DirectionalWinCalculator" not in consumer_code
            and "DirectionalWinInput" not in consumer_code
            and "DirectionalWinResult" not in consumer_code
            and ".calculate(" not in consumer_code,
            "ADR-019 favorable-extreme selection may consume only "
            f"TargetHitSide without invoking a calculator: {other_path}",
        )
        continue
    if other_path.resolve() in target_hit_orchestration_consumers:
        consumer_code = without_comments_or_strings(other_source)
        calculation_imports = {
            imported
            for imported in re.findall(
                r"^import\s+([^;]+);",
                consumer_code,
                flags=re.MULTILINE,
            )
            if ".domain.outcome.calculation." in imported
        }
        expected_imports = target_hit_orchestration_consumers[
            other_path.resolve()
        ]
        is_orchestrator = other_path.name == "TargetHitOrchestrator.java"
        require(
            calculation_imports == expected_imports
            and (
                consumer_code.count("TargetHitCalculator.calculate(") == 1
                if is_orchestrator
                else "TargetHitCalculator.calculate(" not in consumer_code
            )
            and "DirectionalWinCalculator" not in consumer_code
            and "DirectionalWinInput" not in consumer_code
            and "DirectionalWinResult" not in consumer_code,
            "Target-hit orchestration may consume only the exact primitive "
            f"surface and invoke it only once in its orchestrator: {other_path}",
        )
        continue
    if other_path.resolve() in directional_win_orchestration_consumers:
        consumer_code = without_comments_or_strings(other_source)
        calculation_imports = {
            imported
            for imported in re.findall(
                r"^import\s+([^;]+);",
                consumer_code,
                flags=re.MULTILINE,
            )
            if ".domain.outcome.calculation." in imported
        }
        expected_imports = directional_win_orchestration_consumers[
            other_path.resolve()
        ]
        is_orchestrator = (
            other_path.name == "DirectionalWinOrchestrator.java"
        )
        require(
            calculation_imports == expected_imports
            and (
                consumer_code.count(
                    "DirectionalWinCalculator.calculate("
                ) == 1
                if is_orchestrator
                else "DirectionalWinCalculator.calculate("
                    not in consumer_code
            )
            and "TargetHitCalculator" not in consumer_code
            and "TargetHitInput" not in consumer_code
            and "TargetHitResult" not in consumer_code,
            "Directional-win orchestration may consume only the exact "
            "primitive surface and invoke it only once in its "
            f"orchestrator: {other_path}",
        )
        continue
    require(
        "domain.outcome.calculation" not in other_source
        and not any(marker in other_source for marker in calculation_markers),
        f"Pure calculation core must not be wired into production yet: {other_path}",
    )

golden_path = test_dir / "TargetHitCalculatorGoldenTest.java"
golden_source = golden_path.read_text(encoding="utf-8")
require(
    golden_source.count('@MethodSource("') >= 3
    and '"T1 bullish window high crosses target"' in golden_source
    and 'input(TargetHitSide.BULLISH, "110", "112")' in golden_source
    and 'input(TargetHitSide.BULLISH, "120", "103")' in golden_source
    and 'input(TargetHitSide.BEARISH, "170", "168")' in golden_source,
    "Target-hit documented source-local golden vectors are missing",
)
for reason in reasons:
    require(
        f"UnavailableReason.{reason}" in golden_source,
        f"Target-hit golden tests do not cover {reason}",
    )
require(
    "bullish equality is inclusive" in golden_source
    and "bearish equality is inclusive" in golden_source
    and "MAX_NUMERIC_38_12" in golden_source
    and "exceeds storage scale" in golden_source
    and "exceeds storage precision" in golden_source
    and "TargetHitCalculator.calculate(null)" in golden_source,
    "Target-hit boundary and negative golden coverage is incomplete",
)
require(
    "ObjectMapper" not in golden_source
    and "ClassPathResource" not in golden_source,
    "Target-hit goldens must remain source-local test vectors",
)
directional_golden_path = test_dir / "DirectionalWinCalculatorGoldenTest.java"
directional_golden_source = directional_golden_path.read_text(encoding="utf-8")
require(
    '@MethodSource("availableGoldenVectors")' in directional_golden_source
    and '"T1 bullish positive return wins"' in directional_golden_source
    and 'input(DirectionalWinSide.BULLISH, "0.08")'
    in directional_golden_source
    and '"T2 bullish negative return loses"' in directional_golden_source
    and 'input(DirectionalWinSide.BULLISH, "-0.05")'
    in directional_golden_source
    and '"T3 bearish negative return wins"' in directional_golden_source
    and 'input(DirectionalWinSide.BEARISH, "-0.10")'
    in directional_golden_source,
    "Directional-win documented source-local golden vectors are missing",
)
require(
    "bullish exact zero is a miss" in directional_golden_source
    and "bearish exact zero is a miss" in directional_golden_source
    and "bullish scale-equivalent zero is a miss" in directional_golden_source
    and "bearish scale-equivalent zero is a miss" in directional_golden_source
    and "minimum positive ratio wins for bullish" in directional_golden_source
    and "minimum negative ratio wins for bearish" in directional_golden_source
    and "minimum positive ratio loses for bearish" in directional_golden_source
    and "minimum negative ratio loses for bullish" in directional_golden_source
    and "MAX_NUMERIC_38_12" in directional_golden_source
    and "positive return exceeds storage scale" in directional_golden_source
    and "negative return exceeds storage scale" in directional_golden_source
    and "positive return exceeds storage precision" in directional_golden_source
    and "negative return exceeds storage precision" in directional_golden_source,
    "Directional-win signed boundary and negative coverage is incomplete",
)
require(
    "UnavailableReason.ASSET_RETURN_MISSING" in directional_golden_source
    and "DirectionalWinCalculator.calculate(null)" in directional_golden_source
    and "reportsMissingReturnWithoutInventingFalse" in directional_golden_source
    and '@MethodSource("directionalWinSides")' in directional_golden_source
    and "new DirectionalWinInput(side, null)" in directional_golden_source
    and "Stream.of(DirectionalWinSide.values())" in directional_golden_source
    and "isNotEqualTo(new Available(false))" in directional_golden_source
    and "Locale.setDefault(Locale.KOREA)" in directional_golden_source
    and 'TimeZone.getTimeZone("Asia/Seoul")' in directional_golden_source
    and "Locale.setDefault(Locale.US)" in directional_golden_source
    and 'TimeZone.getTimeZone("America/New_York")'
    in directional_golden_source
    and "finally" in directional_golden_source
    and "Locale.setDefault(originalLocale)" in directional_golden_source
    and "TimeZone.setDefault(originalTimeZone)" in directional_golden_source,
    "Directional-win unavailable and Locale/TimeZone replay coverage is incomplete",
)
require(
    "ObjectMapper" not in directional_golden_source
    and "ClassPathResource" not in directional_golden_source,
    "Directional-win goldens must remain source-local test vectors",
)
for resource_path in Path("apps/api/src/test/resources").rglob("*.json"):
    resource_source = resource_path.read_text(encoding="utf-8")
    require(
        "TargetHit" not in resource_source
        and "targetHit" not in resource_source
        and "target-hit" not in resource_source.lower(),
        f"Target-hit must not add a JSON golden resource: {resource_path}",
    )
    require(
        "DirectionalWin" not in resource_source
        and "directionalWin" not in resource_source
        and "directional-win" not in resource_source.lower(),
        f"Directional-win must not add a JSON golden resource: {resource_path}",
    )

expected_schemas = {
    "analyst-call-revision.schema.json",
    "analyst-call.schema.json",
    "call-context.schema.json",
    "call-outcome.schema.json",
    "event-context.schema.json",
    "macro-observation.schema.json",
    "macro-snapshot.schema.json",
    "market-board.schema.json",
    "market-map.schema.json",
    "market-snapshot.schema.json",
    "market-treemap.schema.json",
    "scoring-methodology.schema.json",
    "source-document.schema.json",
    "source-reference.schema.json",
}
actual_schemas = {path.name for path in Path("schemas").glob("*.json")}
require(
    actual_schemas == expected_schemas,
    "P3 pure calculations must preserve the exact 14 schemas",
)

expected_fixture_files = {
    "analyst-call-revisions.json",
    "analyst-calls.json",
    "call-contexts.json",
    "call-outcomes.json",
    "manifest.json",
    "market-board.json",
    "market-map-nasdaq100.json",
    "market-map.json",
    "market-snapshots.json",
    "market-treemap-nasdaq100.json",
    "market-treemap-sp500.json",
    "master-data.json",
    "timeline-nvda.json",
}
fixture_dir = Path("fixtures/v1")
actual_fixture_files = {path.name for path in fixture_dir.glob("*.json")}
require(
    actual_fixture_files == expected_fixture_files,
    "P3 pure calculations must preserve the exact 13 canonical fixture files",
)
manifest = json.loads((fixture_dir / "manifest.json").read_text(encoding="utf-8"))
expected_manifest_paths = [
    "master-data.json",
    "analyst-calls.json",
    "analyst-call-revisions.json",
    "call-outcomes.json",
    "call-contexts.json",
    "market-snapshots.json",
    "market-map.json",
    "market-map-nasdaq100.json",
    "market-treemap-sp500.json",
    "market-treemap-nasdaq100.json",
    "timeline-nvda.json",
    "market-board.json",
]
require(
    [entry["path"] for entry in manifest["files"]] == expected_manifest_paths,
    "P3 pure calculations must not change fixture manifest membership/order",
)

outcome_document = json.loads(
    (fixture_dir / "call-outcomes.json").read_text(encoding="utf-8")
)
methodologies = outcome_document["methodologies"]
outcomes = outcome_document["outcomes"]
metric_fields = (
    "assetReturn", "benchmarkReturn", "sectorReturn", "alpha", "sectorAlpha",
    "mfe", "mae", "targetHit", "directionalWin", "targetError",
)
require(
    len(methodologies) == 2
    and all(item["status"] == "MODEL_ONLY" for item in methodologies),
    "P3 pure calculations must not activate or append a methodology",
)
require(
    len(outcomes) == 4
    and {item["evaluationStatus"] for item in outcomes} == {"PENDING", "INCOMPLETE"}
    and all(not item["dataComplete"] for item in outcomes)
    and all(item[field] is None for item in outcomes for field in metric_fields),
    "P3 pure calculations must not publish or persist a calculated fixture outcome",
)

openapi_source = Path("contracts/openapi.yaml").read_text(encoding="utf-8")
openapi_paths = set(
    re.findall(r"^  (/[^\n]+):\s*$", openapi_source, flags=re.MULTILINE)
)
require(
    openapi_paths
    == {
        "/v1/calls",
        "/v1/calls/{id}",
        "/v1/calls/{id}/revisions",
        "/v1/calls/{id}/outcomes",
        "/v1/calls/{id}/context",
    },
    f"P3 pure calculations must preserve the exact five OpenAPI paths: {sorted(openapi_paths)}",
)
migration_names = {
    path.name
    for path in Path("apps/api/src/main/resources/db/migration").glob("*.sql")
}
require(
    migration_names
    == {
        "V1__baseline.sql",
        "V2__analyst_calls.sql",
        "V3__analyst_call_revisions.sql",
        "V4__call_outcomes.sql",
        "V5__call_contexts.sql",
        "V6__sec_filing_catalog_captures.sql",
        "V7__sec_historical_filing_segment_captures.sql",
        "V8__sec_filing_history_collection_manifests.sql",
        "V9__sec_filing_collection_attempts.sql",
    },
    f"P3 pure calculations must preserve the exact nine Flyway migrations: {sorted(migration_names)}",
)
web_markers = (
    tuple(expected_production_files)
    + tuple(reasons)
    + tuple(directional_reasons)
)
for web_path in Path("apps/web/src").rglob("*"):
    if web_path.is_file() and web_path.suffix in {".ts", ".tsx", ".js", ".jsx"}:
        web_source = web_path.read_text(encoding="utf-8")
        require(
            not any(marker.removesuffix(".java") in web_source for marker in web_markers),
            f"P3 pure calculations must not expand the web surface: {web_path}",
        )

print(
    "Validated pure target-hit and directional-win cores, source-local golden vectors, "
    "unchanged 2 MODEL_ONLY methodologies/4 all-null outcomes, and no contract, "
    "fixture, API, Flyway, persistence, or web expansion"
)
PYTHON
