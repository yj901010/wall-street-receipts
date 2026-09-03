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
    "outcome/favorableextreme"
)
test_dir = Path(
    "apps/api/src/test/java/com/wallstreetreceipts/api/domain/"
    "outcome/favorableextreme"
)
production_files = {
    "FavorableExtremePolicyVersion.java",
    "WindowPriceBinding.java",
    "FullWindowHighLowObservation.java",
    "FavorableExtremeRequest.java",
    "FavorableExtremeResolution.java",
    "FavorableExtremeSelector.java",
}
test_files = {"FavorableExtremeSelectorGoldenTest.java"}
require(
    {path.name for path in production_dir.glob("*.java")}
    == production_files,
    "Favorable-extreme production package must contain exactly six files",
)
require(
    {path.name for path in test_dir.glob("*.java")} == test_files,
    "Favorable-extreme test package must contain exactly one golden",
)

sources = {
    name: (production_dir / name).read_text(encoding="utf-8")
    for name in production_files
}
for name, source in sources.items():
    require(
        "package com.wallstreetreceipts.api.domain.outcome.favorableextreme;"
        in source,
        f"Favorable-extreme package changed: {name}",
    )
    require(
        re.search(r"\\u+[0-9a-fA-F]{4}", source) is None
        and '"""' not in source,
        f"Favorable-extreme source has unsafe lexical indirection: {name}",
    )

policy = sources["FavorableExtremePolicyVersion.java"]
definition = java_string_constant(policy, "CANONICAL_DEFINITION")
definition_bytes = definition.encode("utf-8")
policy_hash = (
    "e3a0e93030c8f09ae5398bf6df0f2e28eec14b0a31f5bea240fc78f2412c2463"
)
require(
    len(definition_bytes) == 4633
    and definition.isascii()
    and definition.startswith(
        '{"policyVersion":'
        '"POINT_IN_TIME_ATTESTED_CAUSAL_WINDOW_HIGH_LOW_V1",'
    )
    and definition.endswith('"fallbackBehavior":"ABSENT"}')
    and definition == definition.strip()
    and "\n" not in definition
    and "\r" not in definition
    and hashlib.sha256(definition_bytes).hexdigest() == policy_hash
    and java_string_constant(policy, "DEFINITION_HASH") == policy_hash,
    "Favorable-extreme canonical bytes, length, or SHA-256 changed",
)
adr = Path(
    "decisions/ADR-019-point-in-time-full-window-extreme-selection.md"
).read_text(encoding="utf-8")
adr_text_blocks = re.findall(
    r"```text\r?\n([^\r\n]+)\r?\n```", adr
)
require(
    adr_text_blocks == [definition, policy_hash],
    "ADR-019 canonical definition or digest drifted from Java",
)
for marker in (
    '"requiredEligibilityPolicyVersion":'
    '"POINT_IN_TIME_TARGET_HIT_INPUT_READINESS_V1"',
    '"requiredEligibilityPolicyDefinitionHash":'
    '"a6b4c9f4e4d29b5f1a9b0c300e2d7b9505318c708dfb0ad0e88f71324cf65465"',
    '"futureBindingRule":"IDENTICAL_TO_NULL_AND_INVISIBLE_TO_OUTPUT"',
    '"futureCandidateRule":"INVISIBLE_TO_ALL_OUTPUT_AND_REASONING"',
    '"economicObservationSet":"primary-venue regular-session '
    'observations belonging to horizon.window.sessions with '
    'observation.time>basis.eventTime&&observation.time<='
    'endpointSession.closesAt"',
    '"lowerBoundRule":"lowerBound==basis.eventTime&&'
    'lowerBoundType==EXCLUSIVE"',
    '"upperBoundRule":"upperBound==endpointSession.closesAt&&'
    'upperBoundType==INCLUSIVE"',
    '"sessionWindowRule":"orderedSessionIds=='
    'horizon.window.sessions.sessionId in exact order"',
    '"coverageRule":"EXACT_CAUSAL_WINDOW_SESSION_UNION"',
    '"priceFieldRule":'
    '"PRIMARY_VENUE_REGULAR_SESSION_CAUSAL_WINDOW_HIGH_LOW_PAIR"',
    '"knownInvalidCandidateRule":'
    '"POISONS_SELECTION_BEFORE_AMBIGUITY"',
    '"deduplication":"ABSENT"',
    '"favorableExtremeSelection":'
    '{"BULLISH":"WINDOW_HIGH","BEARISH":"WINDOW_LOW"}',
    '"attestationScope":'
    '"UPSTREAM_PROVIDER_SOURCE_ATTESTED_EXACT_WINDOW_HIGH_LOW_PAIR"',
    '"rawAggregation":"ABSENT"',
    '"rawObservationVerification":"ABSENT"',
    '"deferredRawSemantics":["NO_TRADE","HALT","AUCTION",'
    '"BAR_STRADDLE","CORRECTION_SEQUENCE","RAW_COVERAGE_PROOF"]',
    '"endpointPriceObservationInput":"ABSENT"',
    '"endpointCloseFallback":"ABSENT"',
    '"genericPriceField":"ABSENT"',
    '"calculatorInvocation":"ABSENT"',
    '"fallbackBehavior":"ABSENT"',
):
    require(marker in definition, f"Missing canonical boundary: {marker}")
require(
    enum_values(policy, "FavorableExtremePolicyVersion")
    == ["POINT_IN_TIME_ATTESTED_CAUSAL_WINDOW_HIGH_LOW_V1"]
    and "return CANONICAL_DEFINITION;" in policy
    and "CANONICAL_DEFINITION.getBytes(StandardCharsets.UTF_8)" in policy
    and "return DEFINITION_HASH;" in policy,
    "Favorable-extreme policy enum or defensive UTF-8 surface changed",
)

observation = sources["FullWindowHighLowObservation.java"]
resolution = sources["FavorableExtremeResolution.java"]
unavailable_reasons = [
    "TARGET_ADJUSTMENT_BASIS_UNSUPPORTED",
    "BINDING_NOT_KNOWN_AS_OF",
    "BINDING_ASSET_MISMATCH",
    "BINDING_PRIMARY_VENUE_MISMATCH",
    "BINDING_CURRENCY_MISMATCH",
    "OBSERVATION_MISSING_AS_OF",
    "BASIS_MISMATCH",
    "HORIZON_MISMATCH",
    "ASSET_MISMATCH",
    "PRIMARY_VENUE_MISMATCH",
    "CURRENCY_MISMATCH",
    "SOURCE_MISMATCH",
    "CATALOG_MISMATCH",
    "SESSION_WINDOW_MISMATCH",
    "LOWER_BOUND_MISMATCH",
    "UPPER_BOUND_MISMATCH",
    "BOUNDARY_CONVENTION_MISMATCH",
    "PRICE_FIELD_MISMATCH",
    "WINDOW_COMPLETENESS_UNAVAILABLE",
    "ADJUSTMENT_BASIS_MISMATCH",
    "CORPORATE_ACTION_CONTINUITY_UNAVAILABLE",
    "OBSERVATION_AMBIGUOUS",
]
require(
    enum_values(observation, "BoundaryType")
    == ["EXCLUSIVE", "INCLUSIVE", "UNKNOWN"]
    and enum_values(observation, "WindowPriceField")
    == [
        "PRIMARY_VENUE_REGULAR_SESSION_CAUSAL_WINDOW_HIGH_LOW_PAIR",
        "INDICATIVE_OR_OTHER",
    ]
    and enum_values(observation, "WindowCoverageCompleteness")
    == ["EXACT_CAUSAL_WINDOW_SESSION_UNION", "PARTIAL_OR_UNKNOWN"]
    and enum_values(resolution, "UnavailableReason")
    == unavailable_reasons
    and enum_values(resolution, "FavorableExtremeField")
    == ["HIGH", "LOW"],
    "Favorable-extreme closed enum values or reason order changed",
)

compact_sources = {
    name: compact(without_comments(source))
    for name, source in sources.items()
}
binding = compact_sources["WindowPriceBinding.java"]
observation_compact = compact_sources["FullWindowHighLowObservation.java"]
request = compact_sources["FavorableExtremeRequest.java"]
result = compact_sources["FavorableExtremeResolution.java"]
selector = compact_sources["FavorableExtremeSelector.java"]
require(
    "publicrecordWindowPriceBinding(StringbindingId,"
    "StringbindingRevision,StringassetId,StringprimaryVenueId,"
    "Currencycurrency,StringpriceSourceId,StringpriceSourceRevision,"
    "InstantavailableAt,InstantcapturedAt,StringprovenanceId)" in binding,
    "Window binding record surface changed",
)
require(
    "publicrecordFullWindowHighLowObservation(StringobservationId,"
    "StringproviderEventId,OutcomeBasisbasis,OutcomeHorizonhorizon,"
    "StringassetId,StringvenueId,Currencycurrency,StringpriceSourceId,"
    "StringpriceSourceRevision,StringprovenanceId,StringcalendarId,"
    "StringcatalogRevision,List<String>orderedSessionIds,"
    "InstantlowerBound,BoundaryTypelowerBoundType,InstantupperBound,"
    "BoundaryTypeupperBoundType,WindowPriceFieldpriceField,"
    "WindowCoverageCompletenesscoverageCompleteness,"
    "EndpointPriceAdjustmentBasisadjustmentBasis,"
    "CorporateActionContinuitycorporateActionContinuity,"
    "InstantavailableAt,InstantcapturedAt,BigDecimalwindowHigh,"
    "BigDecimalwindowLow)" in observation_compact,
    "Full-window attested HIGH+LOW pair surface changed",
)
require(
    "publicrecordFavorableExtremeRequest("
    "FavorableExtremePolicyVersionpolicyVersion,"
    "TargetEligibilityResolution.ReadyForWindowEvidencereadyEligibility,"
    "WindowPriceBindingbinding,"
    "List<FullWindowHighLowObservation>candidates)" in request
    and "List.copyOf(candidates)" in request
    and "POINT_IN_TIME_TARGET_HIT_INPUT_READINESS_V1" in request
    and "REQUIRED_ELIGIBILITY_HASH" in request,
    "Favorable-extreme request or exact ready-input boundary changed",
)
for marker in (
    "permitsFavorableExtremeResolution.Resolved,"
    "FavorableExtremeResolution.Unavailable",
    "recordFavorableExtreme(FavorableExtremeFieldfield,BigDecimalvalue)",
    "recordResolutionContext(FavorableExtremePolicyVersionpolicyVersion,"
    "StringpolicyDefinitionHash,"
    "TargetEligibilityResolution.ReadyForWindowEvidencereadyEligibility)",
    "recordSelectionEvidence(WindowPriceBindingbinding,"
    "List<FullWindowHighLowObservation>knownCandidates)",
    "recordResolved(ResolutionContextcontext,SelectionEvidenceevidence,"
    "FavorableExtremefavorableExtreme)implementsFavorableExtremeResolution",
    "recordUnavailable(ResolutionContextcontext,SelectionEvidenceevidence,"
    "UnavailableReasonreason)implementsFavorableExtremeResolution",
):
    require(marker in result, f"Favorable-extreme result changed: {marker}")
require(
    "publicfinalclassFavorableExtremeSelector" in selector
    and "privateFavorableExtremeSelector(){}" in selector
    and "publicstaticFavorableExtremeResolutionselect("
    "FavorableExtremeRequestrequest)" in selector,
    "Favorable-extreme selector public surface changed",
)

expected_imports = {
    "FavorableExtremePolicyVersion.java": {
        "java.nio.charset.StandardCharsets",
    },
    "WindowPriceBinding.java": {
        "java.time.Instant", "java.util.Currency", "java.util.Objects",
        "com.wallstreetreceipts.api.domain.PersistentInstant",
    },
    "FullWindowHighLowObservation.java": {
        "java.math.BigDecimal", "java.math.RoundingMode",
        "java.time.Instant", "java.util.Currency", "java.util.HashSet",
        "java.util.List", "java.util.Objects", "java.util.Set",
        "com.wallstreetreceipts.api.domain.PersistentInstant",
        "com.wallstreetreceipts.api.domain.outcome.OutcomeHorizon",
        "com.wallstreetreceipts.api.domain.outcome.horizon.OutcomeBasis",
        "com.wallstreetreceipts.api.domain.outcome.observation."
        "CorporateActionContinuity",
        "com.wallstreetreceipts.api.domain.outcome.observation."
        "EndpointPriceAdjustmentBasis",
    },
    "FavorableExtremeRequest.java": {
        "java.util.List", "java.util.Objects",
        "com.wallstreetreceipts.api.domain.outcome.targeteligibility."
        "TargetEligibilityPolicyVersion",
        "com.wallstreetreceipts.api.domain.outcome.targeteligibility."
        "TargetEligibilityResolution",
    },
    "FavorableExtremeResolution.java": {
        "java.math.BigDecimal", "java.math.RoundingMode",
        "java.time.Instant", "java.util.List", "java.util.Objects",
        "com.wallstreetreceipts.api.domain.outcome.calculation.TargetHitSide",
        "com.wallstreetreceipts.api.domain.outcome.horizon."
        "SessionCloseHorizonResolution",
        "com.wallstreetreceipts.api.domain.outcome.observation."
        "CorporateActionContinuity",
        "com.wallstreetreceipts.api.domain.outcome.observation."
        "EndpointPriceAdjustmentBasis",
        "com.wallstreetreceipts.api.domain.outcome.routing."
        "CalculatorSideRouting.DirectionalRoute",
        "com.wallstreetreceipts.api.domain.outcome.targeteligibility."
        "TargetEligibilityResolution",
    },
    "FavorableExtremeSelector.java": {
        "java.time.Instant", "java.util.List", "java.util.Objects",
        "com.wallstreetreceipts.api.domain.outcome.calculation.TargetHitSide",
        "com.wallstreetreceipts.api.domain.outcome.favorableextreme."
        "FavorableExtremeResolution.FavorableExtreme",
        "com.wallstreetreceipts.api.domain.outcome.favorableextreme."
        "FavorableExtremeResolution.FavorableExtremeField",
        "com.wallstreetreceipts.api.domain.outcome.favorableextreme."
        "FavorableExtremeResolution.ResolutionContext",
        "com.wallstreetreceipts.api.domain.outcome.favorableextreme."
        "FavorableExtremeResolution.SelectionEvidence",
        "com.wallstreetreceipts.api.domain.outcome.favorableextreme."
        "FavorableExtremeResolution.UnavailableReason",
        "com.wallstreetreceipts.api.domain.outcome.horizon."
        "SessionCloseHorizonResolution",
        "com.wallstreetreceipts.api.domain.outcome.observation."
        "CorporateActionContinuity",
        "com.wallstreetreceipts.api.domain.outcome.observation."
        "EndpointPriceAdjustmentBasis",
        "com.wallstreetreceipts.api.domain.outcome.routing."
        "CalculatorSideRouting.DirectionalRoute",
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
        f"Favorable-extreme deterministic import/runtime boundary changed: "
        f"{name} {imports}",
    )

selector_logic = compact(
    without_comments_or_strings(sources["FavorableExtremeSelector.java"])
)
reason_positions = [
    selector_logic.index(f"UnavailableReason.{reason}")
    for reason in unavailable_reasons
]
require(
    reason_positions == sorted(reason_positions)
    and all(
        selector_logic.count(f"UnavailableReason.{reason}") == 1
        for reason in unavailable_reasons
    )
    and "known(binding.availableAt(),binding.capturedAt(),evaluationAsOf)"
    in selector_logic
    and "request.candidates().stream().filter(candidate->known("
    "candidate.availableAt(),candidate.capturedAt(),evaluationAsOf))"
    in selector_logic
    and "window.sessions().stream().map(session->session.sessionId()).toList()"
    in selector_logic
    and "candidate.lowerBound().equals(horizonContext.basis().eventTime())"
    in selector_logic
    and "candidate.upperBound().equals(window.endpointSession().closesAt())"
    in selector_logic
    and "BoundaryType.EXCLUSIVE" in selector_logic
    and "BoundaryType.INCLUSIVE" in selector_logic
    and "PRIMARY_VENUE_REGULAR_SESSION_CAUSAL_WINDOW_HIGH_LOW_PAIR"
    in selector_logic
    and "EXACT_CAUSAL_WINDOW_SESSION_UNION" in selector_logic
    and selector_logic.index("CORPORATE_ACTION_CONTINUITY_UNAVAILABLE")
    < selector_logic.index("if(knownCandidates.size()>1)")
    < selector_logic.index("OBSERVATION_AMBIGUOUS"),
    "Favorable-extreme PIT filtering, causal window, or precedence changed",
)
require(
    "route.targetHitSide()==TargetHitSide.BULLISH?"
    "newFavorableExtreme(FavorableExtremeField.HIGH,"
    "observation.windowHigh()):newFavorableExtreme("
    "FavorableExtremeField.LOW,observation.windowLow())"
    in selector_logic
    and "knownCandidates.getFirst()" in selector_logic
    and "knownCandidates.size()>1" in selector_logic
    and ".max(" not in selector_logic
    and ".min(" not in selector_logic
    and ".reduce(" not in selector_logic
    and "Comparator" not in selector_logic,
    "Selector must choose one field from one attested pair, not aggregate raw bars",
)

observation_logic = compact(
    without_comments_or_strings(
        sources["FullWindowHighLowObservation.java"]
    )
)
require(
    "availableAt.isBefore(upperBound)" in observation_logic
    and "capturedAt.isBefore(availableAt)" in observation_logic
    and "windowLow.compareTo(windowHigh)>0" in observation_logic
    and observation_logic.count("setScale(STORAGE_SCALE,RoundingMode.UNNECESSARY)")
    == 1
    and "STORAGE_SCALE=12" in observation_logic
    and "STORAGE_PRECISION=38" in observation_logic
    and "List.copyOf(orderedSessionIds)" in observation_logic,
    "Attested HIGH+LOW pair time/decimal/completeness shape changed",
)

combined_logic = "\n".join(
    without_comments_or_strings(source) for source in sources.values()
)
forbidden_endpoint_or_calculation = (
    "EndpointPriceObservation", "EndpointPriceResolution",
    "EndpointPriceRequest", "EndpointPriceSelector", "EndpointPriceField",
    "OFFICIAL_REGULAR_SESSION_CLOSE", "TargetHitCalculator",
    "TargetHitInput", "TargetHitResult", "DirectionalWinCalculator",
    "DirectionalWinInput", "DirectionalWinResult", "AssetReturnCalculator",
    "AssetReturnInput", "AssetReturnResult", "TargetErrorCalculator",
    "TargetErrorInput", "TargetErrorResult", ".calculate(",
)
require(
    not any(marker in combined_logic
            for marker in forbidden_endpoint_or_calculation)
    and "rawAggregation" not in combined_logic
    and "rawObservationVerification" not in combined_logic
    and "CallOutcome" not in combined_logic
    and "ScoringMethodology" not in combined_logic,
    "Favorable-extreme source must preserve endpoint/calculator/runtime firewall",
)

api_main_dir = Path("apps/api/src/main/java")
new_paths = {
    (production_dir / name).resolve() for name in production_files
}
new_markers = tuple(name.removesuffix(".java") for name in production_files)
orchestration_consumers = {
    (api_main_dir / "com/wallstreetreceipts/api/domain/outcome/"
     "targethitorchestration/"
     "TargetHitOrchestrationPolicyVersion.java").resolve(): set(),
    (api_main_dir / "com/wallstreetreceipts/api/domain/outcome/"
     "targethitorchestration/"
     "TargetHitOrchestrationRequest.java").resolve(): {
        "com.wallstreetreceipts.api.domain.outcome.favorableextreme."
        "FavorableExtremePolicyVersion",
        "com.wallstreetreceipts.api.domain.outcome.favorableextreme."
        "FavorableExtremeResolution",
    },
    (api_main_dir / "com/wallstreetreceipts/api/domain/outcome/"
     "targethitorchestration/"
     "TargetHitOrchestrationResolution.java").resolve(): {
        "com.wallstreetreceipts.api.domain.outcome.favorableextreme."
        "FavorableExtremeResolution",
    },
    (api_main_dir / "com/wallstreetreceipts/api/domain/outcome/"
     "targethitorchestration/TargetHitOrchestrator.java").resolve(): {
        "com.wallstreetreceipts.api.domain.outcome.favorableextreme."
        "FavorableExtremeResolution",
    },
}
for other_path in api_main_dir.rglob("*.java"):
    if other_path.resolve() in new_paths:
        continue
    other_source = other_path.read_text(encoding="utf-8")
    if other_path.resolve() in orchestration_consumers:
        other_logic = without_comments_or_strings(other_source)
        favorable_imports = {
            imported
            for imported in re.findall(
                r"^import\s+([^;]+);", other_logic,
                flags=re.MULTILINE,
            )
            if ".domain.outcome.favorableextreme." in imported
        }
        expected_imports = orchestration_consumers[other_path.resolve()]
        expected_types = {
            imported.rsplit(".", 1)[-1]
            for imported in expected_imports
        }
        used_types = {
            marker for marker in new_markers
            if re.search(rf"\b{re.escape(marker)}\b", other_logic)
        }
        require(
            favorable_imports == expected_imports
            and used_types == expected_types
            and "FavorableExtremeSelector" not in other_logic
            and "FavorableExtremeRequest" not in other_logic
            and "WindowPriceBinding" not in other_logic
            and "FullWindowHighLowObservation" not in other_logic,
            "Target-hit orchestration may consume only supplied "
            f"favorable-extreme resolutions: {other_path}",
        )
        continue
    require(
        "domain.outcome.favorableextreme" not in other_source
        and not any(marker in other_source for marker in new_markers),
        f"Favorable-extreme leaf must not be wired into runtime: {other_path}",
    )

golden = (
    test_dir / "FavorableExtremeSelectorGoldenTest.java"
).read_text(encoding="utf-8")
golden_logic = without_comments_or_strings(golden)
golden_compact = compact(golden_logic)
call_direction_source = re.search(
    r"@EnumSource\s*\(\s*value\s*=\s*CallDirection\.class\s*,"
    r"\s*names\s*=\s*\{(?P<body>.*?)\}\s*\)",
    golden,
    flags=re.DOTALL,
)
binding_fault_source = re.search(
    r"@EnumSource\s*\(\s*value\s*=\s*BindingFault\.class\s*,"
    r"\s*names\s*=\s*\{(?P<body>.*?)\}\s*\)",
    golden,
    flags=re.DOTALL,
)
candidate_fault_source = re.search(
    r"private\s+enum\s+CandidateFault\s*\{(?P<body>.*?);",
    golden,
    flags=re.DOTALL,
)
require(
    call_direction_source is not None
    and binding_fault_source is not None
    and candidate_fault_source is not None,
    "Favorable-extreme parameter sources changed",
)
call_direction_names = re.findall(
    r'"([A-Z][A-Z0-9_]*)"', call_direction_source.group("body")
)
binding_fault_names = re.findall(
    r'"([A-Z][A-Z0-9_]*)"', binding_fault_source.group("body")
)
candidate_fault_names = re.findall(
    r"^\s*([A-Z][A-Z0-9_]*)\s*\(",
    candidate_fault_source.group("body"),
    flags=re.MULTILINE,
)
parameterized_invocations = (
    len(call_direction_names)
    + golden_logic.count("Arguments.of(")
    + len(binding_fault_names)
    + len(candidate_fault_names)
)
require(
    re.search(r"\\u+[0-9a-fA-F]{4}", golden) is None
    and len(re.findall(r"@Test\b", golden_logic)) == 15
    and len(re.findall(r"@ParameterizedTest\b", golden_logic)) == 4
    and len(re.findall(r"@EnumSource\b", golden_logic)) == 3
    and len(re.findall(r"@MethodSource\b", golden_logic)) == 1
    and golden_logic.count("Arguments.of(") == 5
    and call_direction_names == [
        "STRONG_BULLISH", "BULLISH", "BEARISH", "STRONG_BEARISH"
    ]
    and binding_fault_names == ["ASSET", "VENUE", "CURRENCY"]
    and candidate_fault_names == [
        "BASIS", "HORIZON", "ASSET", "VENUE", "CURRENCY",
        "SOURCE", "CATALOG", "SESSION", "LOWER", "UPPER",
        "BOUNDARY", "FIELD", "COMPLETENESS", "ADJUSTMENT",
        "CONTINUITY",
    ]
    and 15 + parameterized_invocations == 42
    and "@Disabled" not in golden_logic
    and "Assumptions" not in golden_logic
    and "assumeTrue" not in golden_logic
    and "assumeFalse" not in golden_logic,
    "Favorable-extreme golden count or lexical boundary changed",
)
for marker in (
    "canonicalDefinitionHasExactBytesIndependentHashAndDefensiveReads",
    "exactFileRecordEnumAndEndpointFirewallSurfacesAreStable",
    "selectsHighForBullishAndLowForBearishWithoutCallingCalculator",
    "correctionBasisRemainsAnIndependentExactWindowIdentity",
    "exactCausalWindowAlwaysUsesBasisExclusiveAndEndpointInclusive",
    "everyNamedHorizonRequiresEveryExactSessionIdInSourceOrder",
    "nullAndEitherFutureBindingTimestampProduceEqualCompleteResults",
    "futureExactWrongAndDuplicateCandidatesAreInvisibleToAllReasoning",
    "exactPitTimestampEqualityIsVisible",
    "bindingIdentityGatesAreExactAndClearLaterCandidates",
    "everyKnownCandidateMismatchUsesItsExactReason",
    "mismatchPrecedenceIsCandidateOrderIndependentAndPoisonsValidEvidence",
    "duplicateExactCandidateIsAmbiguousWithoutDeduplication",
    "unsupportedTargetAdjustmentWinsBeforeBindingAndObservationEvidence",
    "inputCollectionsAreDefensivelyCopied",
    "observationAndBindingConstructorsRejectMalformedEvidence",
    "directResultConstructorsRejectContradictoryOrFutureEvidence",
    "equalHighAndLowIsValidAndOriginalDecimalScaleIsPreserved",
    "selectionIsIndependentOfLocaleTimezoneAndPriorCalls",
):
    require(marker in golden_logic,
            f"Missing favorable-extreme golden coverage: {marker}")
require(
    "hasSize(4633)" in golden_compact
    and policy_hash in golden
    and "EnumSource(CandidateFault.class)" in golden_compact
    and "List.of(exact,exact)" in golden_compact
    and "EnumSet.allOf(CandidateFault.class)" in golden_compact
    and "OutcomeHorizon.values()" in golden_compact
    and "isNotSameAs(exact)" in golden_compact
    and "99999999999999999999999999.999999999999" in golden
    and "100000000000000000000000000.000000000000" in golden
    and "exact.availableAt().plusNanos(1)" in golden_compact
    and all(f"UnavailableReason.{reason}" in golden
            for reason in unavailable_reasons)
    and "Locale.setDefault" in golden_logic
    and "TimeZone.setDefault" in golden_logic
    and "finally" in golden_logic,
    "Favorable-extreme goldens must lock 42 vectors, every reason, and replay",
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
    "Favorable-extreme slice must preserve exact schemas and fixtures",
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
    "Favorable-extreme slice must preserve fixture manifest order",
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
    and all(item[field] is None
            for item in outcomes["outcomes"] for field in metrics),
    "Favorable-extreme slice must not publish or activate outcomes",
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
    "Favorable-extreme slice must preserve OpenAPI and Flyway surfaces",
)
for resource_path in Path("apps/api/src/test/resources").rglob("*.json"):
    resource = resource_path.read_text(encoding="utf-8")
    require(
        not any(marker in resource for marker in new_markers),
        f"Favorable-extreme slice must not add JSON goldens: {resource_path}",
    )
for web_path in Path("apps/web/src").rglob("*"):
    if web_path.is_file() and web_path.suffix in {".ts", ".tsx", ".js", ".jsx"}:
        web_source = web_path.read_text(encoding="utf-8")
        require(
            "domain.outcome.favorableextreme" not in web_source
            and not any(marker in web_source for marker in new_markers),
            f"Favorable-extreme slice must not expand web: {web_path}",
        )

print(
    "Validated exact PIT provider-attested causal-window HIGH+LOW pair "
    "selection, basis-exclusive/endpoint-inclusive identity, closed "
    "precedence, bullish-high/bearish-low routing, endpoint-close firewall, "
    "and no raw aggregation, calculator, runtime, or publication"
)
PYTHON
