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
        rf"private\s+static\s+final\s+String\s+{name}\s*=\s*(?P<body>.*?);",
        source,
        flags=re.DOTALL,
    )
    require(match is not None, f"Missing Java string constant {name}")
    tokens = re.findall(r'"(?:\\.|[^"\\])*"', match.group("body"))
    require(tokens, f"Java string constant {name} has no literal bytes")
    return "".join(json.loads(token) for token in tokens)

direction_dir = Path(
    "apps/api/src/main/java/com/wallstreetreceipts/api/domain/outcome/direction"
)
test_dir = Path(
    "apps/api/src/test/java/com/wallstreetreceipts/api/domain/outcome/direction"
)
expected_production_files = {
    "CallDirectionPolarityPolicyVersion.java",
    "CallDirectionPolarityRequest.java",
    "CallDirectionPolarityResolution.java",
    "CallDirectionPolarityResolver.java",
}
expected_test_files = {"CallDirectionPolarityResolverGoldenTest.java"}
production_paths = sorted(direction_dir.glob("*.java"))
test_paths = sorted(test_dir.glob("*.java"))
require(
    {path.name for path in production_paths} == expected_production_files,
    "Unexpected call-direction polarity production surface",
)
require(
    {path.name for path in test_paths} == expected_test_files,
    "Unexpected call-direction polarity test surface",
)
require(
    all(
        "package com.wallstreetreceipts.api.domain.outcome.direction;"
        in path.read_text(encoding="utf-8")
        for path in production_paths + test_paths
    ),
    "Call-direction polarity sources must remain in their isolated package",
)

sources = {
    path.name: path.read_text(encoding="utf-8")
    for path in production_paths
}
policy_source = sources["CallDirectionPolarityPolicyVersion.java"]
request_source = sources["CallDirectionPolarityRequest.java"]
result_source = sources["CallDirectionPolarityResolution.java"]
resolver_source = sources["CallDirectionPolarityResolver.java"]
compact_policy = compact(policy_source)
compact_request = compact(request_source)
compact_result = compact(result_source)
compact_resolver = compact(resolver_source)

expected_definition = (
    '{"policyVersion":'
    '"COLLAPSE_STRONG_DIRECTIONS_NEUTRAL_NON_DIRECTIONAL_V1",'
    '"inputType":"CallDirection",'
    '"mappings":{"STRONG_BULLISH":"BULLISH",'
    '"BULLISH":"BULLISH","NEUTRAL":"NON_DIRECTIONAL",'
    '"BEARISH":"BEARISH","STRONG_BEARISH":"BEARISH"},'
    '"resultVariants":["DIRECTIONAL","NON_DIRECTIONAL"],'
    '"directionalSides":["BULLISH","BEARISH"],'
    '"nonDirectionalReason":"NEUTRAL_DIRECTION",'
    '"directResultConsistency":"DIRECTION_MUST_MATCH_MAPPING",'
    '"nullDirectionBehavior":"REJECT",'
    '"fallbackBehavior":"ABSENT"}'
)
expected_hash = (
    "d83eccc92fedd7ba025745be2c8e78245bc308d0ff479467fa61afe543dc8a50"
)
canonical_definition = java_string_constant(
    policy_source, "CANONICAL_DEFINITION"
)
definition_hash = java_string_constant(policy_source, "DEFINITION_HASH")
definition_bytes = canonical_definition.encode("utf-8")
require(
    canonical_definition == expected_definition,
    "Canonical call-direction polarity definition bytes or key order changed",
)
require(
    len(definition_bytes) == 489
    and hashlib.sha256(definition_bytes).hexdigest() == expected_hash
    and definition_hash == expected_hash,
    "Canonical call-direction polarity length/hash changed",
)
require(
    re.search(
        r"enum\s+CallDirectionPolarityPolicyVersion\s*\{\s*"
        r"COLLAPSE_STRONG_DIRECTIONS_NEUTRAL_NON_DIRECTIONAL_V1\s*;",
        policy_source,
    ) is not None
    and "publicStringcanonicalDefinition()" in compact_policy
    and "publicbyte[]canonicalDefinitionUtf8()" in compact_policy
    and "publicStringdefinitionHash()" in compact_policy
    and "getBytes(StandardCharsets.UTF_8)" in policy_source,
    "Call-direction polarity policy enum or byte/hash accessors changed",
)

call_direction_source = Path(
    "apps/api/src/main/java/com/wallstreetreceipts/api/domain/call/CallDirection.java"
).read_text(encoding="utf-8")
call_direction_body = re.search(
    r"enum\s+CallDirection\s*\{(?P<body>.*?)\}",
    call_direction_source,
    flags=re.DOTALL,
)
require(call_direction_body is not None, "Missing canonical CallDirection enum")
require(
    re.findall(r"\b[A-Z][A-Z_]+\b", call_direction_body.group("body"))
    == [
        "STRONG_BULLISH", "BULLISH", "NEUTRAL", "BEARISH",
        "STRONG_BEARISH",
    ],
    "Canonical CallDirection vocabulary/order changed",
)
require(
    "publicrecordCallDirectionPolarityRequest("
    "CallDirectionPolarityPolicyVersionpolicyVersion,"
    "CallDirectiondirection)" in compact_request
    and "Objects.requireNonNull(policyVersion" in request_source
    and "Objects.requireNonNull(direction" in request_source,
    "CallDirectionPolarityRequest exact closed surface changed",
)

result_permits = re.search(
    r"permits\s+(?P<body>.*?)\{", result_source, flags=re.DOTALL
)
require(result_permits is not None, "Polarity result must be sealed")
require(
    set(re.findall(
        r"CallDirectionPolarityResolution\.(\w+)",
        result_permits.group("body"),
    )) == {"Directional", "NonDirectional"},
    "Call-direction polarity result variants changed",
)
directional_sides = re.search(
    r"enum\s+DirectionalSide\s*\{(?P<body>.*?)\}",
    result_source,
    flags=re.DOTALL,
)
non_directional_reasons = re.search(
    r"enum\s+NonDirectionalReason\s*\{(?P<body>.*?)\}",
    result_source,
    flags=re.DOTALL,
)
require(
    directional_sides is not None
    and re.findall(r"\b[A-Z][A-Z_]+\b", directional_sides.group("body"))
    == ["BULLISH", "BEARISH"],
    "DirectionalSide must remain exactly BULLISH, BEARISH",
)
require(
    non_directional_reasons is not None
    and re.findall(
        r"\b[A-Z][A-Z_]+\b", non_directional_reasons.group("body")
    ) == ["NEUTRAL_DIRECTION"],
    "NonDirectionalReason must remain exactly NEUTRAL_DIRECTION",
)
for marker in (
    "recordResolutionContext("
    "CallDirectionPolarityPolicyVersionpolicyVersion,"
    "StringpolicyDefinitionHash,CallDirectiondirection)",
    "recordDirectional(ResolutionContextcontext,DirectionalSideside)"
    "implementsCallDirectionPolarityResolution",
    "recordNonDirectional(ResolutionContextcontext,"
    "NonDirectionalReasonreason)implementsCallDirectionPolarityResolution",
    "policyVersion.definitionHash().equals(policyDefinitionHash)",
    "DirectionalSideexpectedSide=expectedDirectionalSide(context.direction())",
    "if(expectedSide==null)",
    "if(side!=expectedSide)",
    "if(context.direction()!=CallDirection.NEUTRAL)",
    "caseSTRONG_BULLISH,BULLISH->DirectionalSide.BULLISH",
    "caseNEUTRAL->null",
    "caseBEARISH,STRONG_BEARISH->DirectionalSide.BEARISH",
):
    require(marker in compact_result, f"Missing polarity result invariant: {marker}")
require(
    re.search(r"\b(?:boolean|Boolean)\b", result_source) is None,
    "Neutral polarity must not be represented by a Boolean",
)

for marker in (
    "publicstaticCallDirectionPolarityResolutionresolve("
    "CallDirectionPolarityRequestrequest)",
    "Objects.requireNonNull(request",
    "newResolutionContext(request.policyVersion(),"
    "request.policyVersion().definitionHash(),request.direction())",
    "caseSTRONG_BULLISH,BULLISH->newDirectional("
    "context,DirectionalSide.BULLISH)",
    "caseNEUTRAL->newNonDirectional("
    "context,NonDirectionalReason.NEUTRAL_DIRECTION)",
    "caseBEARISH,STRONG_BEARISH->newDirectional("
    "context,DirectionalSide.BEARISH)",
):
    require(marker in compact_resolver, f"Missing exact polarity mapping: {marker}")
mapping_implementation = without_comments_or_strings(
    result_source + "\n" + resolver_source
)
require(
    re.search(
        r"\.\s*(?:ordinal|name)\s*\(|\bvalueOf\s*\(|"
        r"\bdefault\s*(?::|->)|\bgetOrDefault\s*\(|"
        r"\bto(?:Lower|Upper)Case\s*\(|\bMap\s*<",
        mapping_implementation,
    ) is None,
    "Polarity mapping must use only exhaustive enum switches without fallback",
)

allowed_java_imports = {
    "java.nio.charset.StandardCharsets",
    "java.util.Objects",
}
allowed_internal_imports = {
    "com.wallstreetreceipts.api.domain.call.CallDirection",
}
allowed_internal_prefix = (
    "com.wallstreetreceipts.api.domain.outcome.direction."
)
forbidden_code = re.compile(
    r"\b(?:AnalystCall\w*|CorrectedCallTerms|CallOutcome\w*|"
    r"TargetHit\w*|DirectionalWin\w*|OutcomeHorizon|OutcomeBasis|"
    r"TradingSession\w*|SessionOffset\w*|EventSessionRelation\w*|"
    r"SessionCloseHorizon\w*|ScoringMethodology|"
    r"\w*Provider|\w*Repository|ObjectMapper|JdbcTemplate|"
    r"BigDecimal|BigInteger|MathContext|RoundingMode|double|float|"
    r"Clock|Instant|LocalDate\w*|OffsetDateTime|ZonedDateTime|"
    r"ZoneId|ZoneOffset|Locale|TimeZone|Random|"
    r"price\w*|assetReturn|benchmarkReturn|sectorReturn|"
    r"observation\w*|snapshot\w*|methodology\w*|fingerprint\w*)\b",
    flags=re.IGNORECASE,
)
forbidden_runtime_code = re.compile(
    r"\b(?:System|Runtime|Thread|Process|ProcessBuilder)\b|"
    r"\bClass\s*\.\s*forName\s*\(",
)
for source_name, source in sources.items():
    code = without_comments(source)
    imports = re.findall(r"^import\s+([^;]+);", code, flags=re.MULTILINE)
    qualified_java_types = set(re.findall(
        r"\b(java(?:\.[A-Za-z_$][\w$]*)+)", code
    ))
    require(
        all(
            imported in allowed_java_imports
            or imported in allowed_internal_imports
            or imported.startswith(allowed_internal_prefix)
            for imported in imports
        ),
        f"Polarity source crosses its pure boundary: {source_name} {imports}",
    )
    require(
        qualified_java_types <= allowed_java_imports,
        f"Polarity source uses a non-allowlisted Java type: "
        f"{source_name} {sorted(qualified_java_types)}",
    )
    require(
        "org.springframework" not in code
        and forbidden_code.search(code) is None
        and forbidden_runtime_code.search(code) is None
        and "@Component" not in code
        and "@Service" not in code,
        f"Polarity source contains calculator/runtime/data wiring: {source_name}",
    )

api_main_dir = Path("apps/api/src/main/java")
policy_paths = {path.resolve() for path in production_paths}
policy_markers = tuple(
    file_name.removesuffix(".java")
    for file_name in expected_production_files
)
calculator_side_adapter_path = (
    api_main_dir
    / "com/wallstreetreceipts/api/domain/outcome/adapter/CalculatorSideAdapter.java"
).resolve()
calculator_side_routing_path = (
    api_main_dir
    / "com/wallstreetreceipts/api/domain/outcome/routing/CalculatorSideRouting.java"
).resolve()
target_eligibility_direction_references = {
    (api_main_dir / "com/wallstreetreceipts/api/domain/outcome/"
     "targeteligibility/TargetEligibilityResolution.java").resolve(): {
        "com.wallstreetreceipts.api.domain.outcome.direction."
        "CallDirectionPolarityPolicyVersion",
    },
    (api_main_dir / "com/wallstreetreceipts/api/domain/outcome/"
     "targeteligibility/TargetEligibilityResolver.java").resolve(): set(),
    (api_main_dir / "com/wallstreetreceipts/api/domain/outcome/"
     "directionalwinorchestration/"
     "DirectionalWinOrchestrationRequest.java").resolve(): {
        "com.wallstreetreceipts.api.domain.outcome.direction."
        "CallDirectionPolarityPolicyVersion",
    },
}
for other_path in api_main_dir.rglob("*.java"):
    if other_path.resolve() in policy_paths:
        continue
    other_source = other_path.read_text(encoding="utf-8")
    if other_path.resolve() in target_eligibility_direction_references:
        other_code = without_comments_or_strings(other_source)
        direction_imports = {
            imported
            for imported in re.findall(
                r"^import\s+([^;]+);", other_code, flags=re.MULTILINE
            )
            if ".domain.outcome.direction." in imported
        }
        normalized_direction_code = re.sub(
            r"\s*\.\s*", ".", other_code
        )
        qualified_refs = re.findall(
            r"com\.wallstreetreceipts\.api\.domain\.outcome\.direction\."
            r"([A-Za-z_$][\w$]*)",
            normalized_direction_code,
        )
        require(
            direction_imports
            == target_eligibility_direction_references[
                other_path.resolve()
            ]
            and set(qualified_refs)
            == {"CallDirectionPolarityPolicyVersion"}
            and set(
                marker for marker in policy_markers
                if re.search(rf"\b{re.escape(marker)}\b", other_code)
            ) == {"CallDirectionPolarityPolicyVersion"},
            "Approved PIT composition may consume only the exact "
            f"direction-policy V1 identity: {other_path}",
        )
        continue
    if other_path.resolve() == calculator_side_adapter_path:
        require(
            other_source.count(
                "CallDirectionPolarityResolution.DirectionalSide"
            ) == 1
            and other_source.count("CallDirectionPolarityResolution") == 1
            and "CallDirectionPolarityRequest" not in other_source
            and "CallDirectionPolarityPolicyVersion" not in other_source
            and "CallDirectionPolarityResolver" not in other_source
            and "NonDirectional" not in other_source
            and "ResolutionContext" not in other_source,
            "ADR-012 adapter may import only the nested common DirectionalSide",
        )
        continue
    if other_path.resolve() == calculator_side_routing_path:
        routing_code = without_comments_or_strings(other_source)
        routing_imports = set(re.findall(
            r"^import\s+([^;]+);", routing_code, flags=re.MULTILINE
        ))
        routing_polarity_imports = {
            imported
            for imported in routing_imports
            if ".domain.outcome.direction." in imported
        }
        require(
            routing_polarity_imports
            == {
                "com.wallstreetreceipts.api.domain.outcome.direction."
                "CallDirectionPolarityResolution",
                "com.wallstreetreceipts.api.domain.outcome.direction."
                "CallDirectionPolarityResolution.Directional",
                "com.wallstreetreceipts.api.domain.outcome.direction."
                "CallDirectionPolarityResolution.NonDirectional",
            }
            and routing_code.count(
                "CallDirectionPolarityResolution"
            ) == 4
            and "CallDirectionPolarityRequest" not in routing_code
            and "CallDirectionPolarityPolicyVersion" not in routing_code
            and "CallDirectionPolarityResolver" not in routing_code
            and "ResolutionContext" not in routing_code
            and "DirectionalSide" not in routing_code
            and "NonDirectionalReason" not in routing_code
            and re.search(r"\bCallDirection\b", routing_code) is None,
            "ADR-013 routing may consume only the closed polarity result and its two variants",
        )
        continue
    require(
        not any(marker in other_source for marker in policy_markers),
        f"Call-direction polarity policy must not be runtime-wired: {other_path}",
    )

golden_source = (
    test_dir / "CallDirectionPolarityResolverGoldenTest.java"
).read_text(encoding="utf-8")
compact_golden = compact(golden_source)
for marker in (
    "directionalMappingVectors",
    "resolvesEveryCanonicalDirectionInExactSourceOrderWithoutFallback",
    "preservesNeutralAsExplicitNonDirectionalEvidenceRatherThanFalseOrLoss",
    "canonicalPolicyDefinitionHasStableExactUtf8BytesAndIndependentSha256",
    "resultDoesNotDependOnJvmDefaultLocaleOrTimeZone",
    "invalidRequestVectors",
    "publicResolutionConstructorsRejectContradictoryDirectionEvidence",
    "contradictoryResolutionVectors",
    "invalidPublicResultVectors",
    "policyRequestAndResultSurfacesRemainExactlyClosed",
):
    require(marker in golden_source, f"Missing polarity golden coverage: {marker}")
for marker in (
    "Arguments.of(CallDirection.STRONG_BULLISH,newDirectional("
    "context(CallDirection.STRONG_BULLISH),DirectionalSide.BULLISH))",
    "Arguments.of(CallDirection.BULLISH,newDirectional("
    "context(CallDirection.BULLISH),DirectionalSide.BULLISH))",
    "Arguments.of(CallDirection.NEUTRAL,newNonDirectional("
    "context(CallDirection.NEUTRAL),"
    "NonDirectionalReason.NEUTRAL_DIRECTION))",
    "Arguments.of(CallDirection.BEARISH,newDirectional("
    "context(CallDirection.BEARISH),DirectionalSide.BEARISH))",
    "Arguments.of(CallDirection.STRONG_BEARISH,newDirectional("
    "context(CallDirection.STRONG_BEARISH),DirectionalSide.BEARISH))",
    "request(CallDirection.NEUTRAL)",
    "NonDirectionalReason.NEUTRAL_DIRECTION",
    "newDirectional(context(CallDirection.NEUTRAL),"
    "DirectionalSide.BULLISH)",
    "newDirectional(context(CallDirection.NEUTRAL),"
    "DirectionalSide.BEARISH)",
    "newNonDirectional(context(CallDirection.STRONG_BULLISH),"
    "NonDirectionalReason.NEUTRAL_DIRECTION)",
    "newNonDirectional(context(CallDirection.BULLISH),"
    "NonDirectionalReason.NEUTRAL_DIRECTION)",
    "newNonDirectional(context(CallDirection.BEARISH),"
    "NonDirectionalReason.NEUTRAL_DIRECTION)",
    "newNonDirectional(context(CallDirection.STRONG_BEARISH),"
    "NonDirectionalReason.NEUTRAL_DIRECTION)",
    "newDirectional(context(CallDirection.STRONG_BULLISH),"
    "DirectionalSide.BEARISH)",
    "newDirectional(context(CallDirection.BULLISH),"
    "DirectionalSide.BEARISH)",
    "newDirectional(context(CallDirection.BEARISH),"
    "DirectionalSide.BULLISH)",
    "newDirectional(context(CallDirection.STRONG_BEARISH),"
    "DirectionalSide.BULLISH)",
    "\"neutralcannotbedirectlyconstructedasbullish\"",
    "\"neutralcannotbedirectlyconstructedasbearish\"",
    "\"strongbullishcannotbedirectlyconstructedasnon-directional\"",
    "\"bullishcannotbedirectlyconstructedasnon-directional\"",
    "\"bearishcannotbedirectlyconstructedasnon-directional\"",
    "\"strongbearishcannotbedirectlyconstructedasnon-directional\"",
    "\"strongbullishcannotbedirectlyconstructedasbearish\"",
    "\"bullishcannotbedirectlyconstructedasbearish\"",
    "\"bearishcannotbedirectlyconstructedasbullish\"",
    "\"strongbearishcannotbedirectlyconstructedasbullish\"",
    "hasSize(489)",
    "MessageDigest.getInstance(\"SHA-256\")",
    "firstRead[0]=(byte)'!'",
    "newCallDirectionPolarityRequest(null,CallDirection.BULLISH)",
    "newCallDirectionPolarityRequest(policy(),null)",
    "CallDirectionPolarityResolver.resolve(null)",
    "Locale.setDefault", "TimeZone.setDefault", "finally",
    "doesNotContain(\"boolean\",\"Boolean\")",
):
    require(marker in compact_golden, f"Missing mutation-sensitive golden: {marker}")
require(
    "ObjectMapper" not in golden_source
    and "ClassPathResource" not in golden_source,
    "Polarity goldens must remain source-local Java values",
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
    "Polarity policy must preserve the exact 14 schemas",
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
    "Polarity policy must not add canonical direction/output fixtures",
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
    "Polarity policy must preserve manifest membership/order",
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
    "Polarity policy must not activate a methodology or publish a result",
)
openapi_source = Path("contracts/openapi.yaml").read_text(encoding="utf-8")
require(
    set(re.findall(r"^  (/[^\n]+):\s*$", openapi_source, re.MULTILINE))
    == {
        "/v1/calls", "/v1/calls/{id}", "/v1/calls/{id}/revisions",
        "/v1/calls/{id}/outcomes", "/v1/calls/{id}/context",
    },
    "Polarity policy must preserve the exact five OpenAPI paths",
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
    "Polarity policy must preserve the exact nine Flyway migrations",
)
for resource_path in Path("apps/api/src/test/resources").rglob("*.json"):
    resource_source = resource_path.read_text(encoding="utf-8")
    require(
        not any(marker in resource_source for marker in policy_markers),
        f"Polarity policy must not add a JSON golden: {resource_path}",
    )
for web_path in Path("apps/web/src").rglob("*"):
    if web_path.is_file() and web_path.suffix in {".ts", ".tsx", ".js", ".jsx"}:
        web_source = web_path.read_text(encoding="utf-8")
        require(
            not any(marker in web_source for marker in policy_markers),
            f"Polarity policy must not expand the web surface: {web_path}",
        )

print(
    "Validated exact five-direction polarity, explicit neutral evidence, "
    "489-byte hashed policy identity, constructor consistency, source-local "
    "goldens, and no calculator/provider/product publication"
)
PYTHON
