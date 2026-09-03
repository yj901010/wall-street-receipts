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
    "outcome/targeteligibility"
)
test_dir = Path(
    "apps/api/src/test/java/com/wallstreetreceipts/api/domain/"
    "outcome/targeteligibility"
)
production_files = {
    "BasisForecastTermsEvidence.java",
    "TargetEligibilityPolicyVersion.java",
    "TargetEligibilityRequest.java",
    "TargetEligibilityResolution.java",
    "TargetEligibilityResolver.java",
}
test_files = {"TargetEligibilityResolverGoldenTest.java"}
require(
    {path.name for path in production_dir.glob("*.java")}
    == production_files,
    "Target-eligibility production package must contain exactly five files",
)
require(
    {path.name for path in test_dir.glob("*.java")} == test_files,
    "Target-eligibility test package must contain exactly one golden",
)

sources = {
    name: (production_dir / name).read_text(encoding="utf-8")
    for name in production_files
}
for name, source in sources.items():
    require(
        "package com.wallstreetreceipts.api.domain.outcome.targeteligibility;"
        in source,
        f"Target-eligibility package changed: {name}",
    )
    require(
        re.search(r"\\u+[0-9a-fA-F]{4}", source) is None
        and '"""' not in source,
        f"Target-eligibility source has unsafe lexical indirection: {name}",
    )

policy = sources["TargetEligibilityPolicyVersion.java"]
definition = java_string_constant(policy, "CANONICAL_DEFINITION")
definition_bytes = definition.encode("utf-8")
policy_hash = (
    "a6b4c9f4e4d29b5f1a9b0c300e2d7b9505318c708dfb0ad0e88f71324cf65465"
)
require(
    len(definition_bytes) == 3862
    and definition.isascii()
    and definition.startswith(
        '{"policyVersion":"POINT_IN_TIME_TARGET_HIT_INPUT_READINESS_V1",'
    )
    and definition.endswith('"fallbackBehavior":"ABSENT"}')
    and definition == definition.strip()
    and "\n" not in definition
    and "\r" not in definition
    and hashlib.sha256(definition_bytes).hexdigest() == policy_hash
    and java_string_constant(policy, "DEFINITION_HASH") == policy_hash,
    "Target-eligibility canonical bytes, length, or SHA-256 changed",
)
for marker in (
    '"futureTermsRule":"IDENTICAL_TO_NULL_AND_INVISIBLE_TO_OUTPUT"',
    '"futureTargetRule":"IDENTICAL_TO_NULL_MISSING_AS_OF_NOT_ABSENT"',
    '"futureCatalogRule":"IDENTICAL_TO_NULL_AND_INVISIBLE_TO_OUTPUT"',
    '"sourceAndNormalizedTargetValues":'
    '"PRESERVED_SEPARATELY_NO_NUMERIC_EQUALITY_INFERENCE"',
    '"targetDateRule":'
    '"NON_NULL_UNSUPPORTED_FOR_DIRECTIONAL_PRESENT_TARGET"',
    '"nonDirectionalRule":"NOT_APPLICABLE_NOT_FALSE_OR_LOSS"',
    '"absentTargetRule":"NOT_APPLICABLE_NOT_MISSING"',
    '"absentVisibleTargetRule":'
    '"TARGET_STATE_CONFLICT_BEFORE_NOT_APPLICABLE"',
    '"calculatorInvocation":"ABSENT"',
    '"readyMeaning":"READY_FOR_LATER_FULL_WINDOW_EVIDENCE_ONLY"',
    '"branchClearingRule":'
    '"EVIDENCE_AFTER_DECIDING_PRECEDENCE_GATE_IS_NULL"',
    '"fallbackBehavior":"ABSENT"',
):
    require(marker in definition, f"Missing canonical boundary: {marker}")
require(
    enum_values(policy, "TargetEligibilityPolicyVersion")
    == ["POINT_IN_TIME_TARGET_HIT_INPUT_READINESS_V1"]
    and "return CANONICAL_DEFINITION;" in policy
    and "CANONICAL_DEFINITION.getBytes(StandardCharsets.UTF_8)" in policy
    and "return DEFINITION_HASH;" in policy,
    "Target-eligibility policy enum or defensive UTF-8 surface changed",
)

result = sources["TargetEligibilityResolution.java"]
pending_reasons = ["HORIZON_NOT_REACHED_AS_OF"]
not_applicable_reasons = [
    "TARGET_ABSENT",
    "NON_DIRECTIONAL",
    "TARGET_ABSENT_AND_NON_DIRECTIONAL",
]
unavailable_reasons = [
    "BASIS_TERMS_NOT_KNOWN_AS_OF",
    "HORIZON_BASIS_MISMATCH",
    "ROUTE_MISSING",
    "ROUTE_DIRECTION_MISMATCH",
    "TARGET_STATE_CONFLICT",
    "TARGET_DATE_SEMANTICS_UNSUPPORTED",
    "TARGET_EVIDENCE_NOT_KNOWN_AS_OF",
    "TARGET_EVIDENCE_BASIS_MISMATCH",
    "TARGET_ASSET_MISMATCH",
    "TARGET_CURRENCY_MISMATCH",
    "CATALOG_NOT_KNOWN_AS_OF",
    "CATALOG_EVIDENCE_MISMATCH",
    "FIRST_ELIGIBLE_SESSION_MISSING",
    "HORIZON_ENDPOINT_SESSION_MISSING",
]
require(
    enum_values(result, "PendingReason") == pending_reasons
    and enum_values(result, "NotApplicableReason")
    == not_applicable_reasons
    and enum_values(result, "UnavailableReason")
    == unavailable_reasons,
    "Target-eligibility reason order changed",
)

compact_sources = {
    name: compact(without_comments(source))
    for name, source in sources.items()
}
terms = compact_sources["BasisForecastTermsEvidence.java"]
request = compact_sources["TargetEligibilityRequest.java"]
result_compact = compact_sources["TargetEligibilityResolution.java"]
resolver = compact_sources["TargetEligibilityResolver.java"]
require(
    "publicrecordBasisForecastTermsEvidence(StringtermsEvidenceId,"
    "OutcomeBasisbasis,StringassetId,CallDirectiondirection,"
    "TargetDispositiontargetDisposition,Stringprovider,"
    "StringproviderEventId,InstantavailableAt,InstantcapturedAt,"
    "StringprovenanceId)" in terms
    and "publicsealedinterfaceTargetDispositionpermits"
    "TargetDisposition.Present,TargetDisposition.Absent" in terms
    and "recordPresent(BigDecimalsourceTarget,"
    "CurrencysourceTargetCurrency,LocalDatetargetDate)"
    "implementsTargetDisposition" in terms
    and "recordAbsent()implementsTargetDisposition" in terms,
    "Basis forecast-terms or explicit target-disposition surface changed",
)
require(
    "publicrecordTargetEligibilityRequest("
    "TargetEligibilityPolicyVersionpolicyVersion,"
    "SessionCloseHorizonResolutionhorizonResolution,"
    "BasisForecastTermsEvidencetermsEvidence,"
    "CalculatorSideRouting.ResultsideRouting,"
    "TargetPriceEvidencetargetEvidence,"
    "CatalogPointInTimeEvidencecatalogEvidence,"
    "InstantevaluationAsOf)" in request,
    "Target-eligibility request surface changed",
)
for marker in (
    "permitsTargetEligibilityResolution.ReadyForWindowEvidence,"
    "TargetEligibilityResolution.Pending,"
    "TargetEligibilityResolution.NotApplicable,"
    "TargetEligibilityResolution.Unavailable",
    "recordResolutionContext("
    "TargetEligibilityPolicyVersionpolicyVersion,"
    "StringpolicyDefinitionHash,"
    "SessionCloseHorizonResolutionhorizonResolution,"
    "InstantevaluationAsOf)",
    "recordEligibilityEvidence("
    "BasisForecastTermsEvidencetermsEvidence,"
    "CalculatorSideRouting.ResultsideRouting,"
    "TargetPriceEvidencetargetEvidence,"
    "CatalogPointInTimeEvidencecatalogEvidence)",
    "recordReadyForWindowEvidence(ResolutionContextcontext,"
    "EligibilityEvidenceevidence)implementsTargetEligibilityResolution",
    "recordPending(ResolutionContextcontext,"
    "EligibilityEvidenceevidence,PendingReasonreason)"
    "implementsTargetEligibilityResolution",
    "recordNotApplicable(ResolutionContextcontext,"
    "EligibilityEvidenceevidence,NotApplicableReasonreason)"
    "implementsTargetEligibilityResolution",
    "recordUnavailable(ResolutionContextcontext,"
    "EligibilityEvidenceevidence,UnavailableReasonreason,"
    "SessionCloseHorizonResolution.IncompleteReasonhorizonReason)"
    "implementsTargetEligibilityResolution",
):
    require(marker in result_compact, f"Result surface changed: {marker}")
require(
    "publicfinalclassTargetEligibilityResolver" in resolver
    and "privateTargetEligibilityResolver(){}" in resolver
    and "publicstaticTargetEligibilityResolutionresolve("
    "TargetEligibilityRequestrequest)" in resolver,
    "Target-eligibility resolver surface changed",
)

expected_imports = {
    "TargetEligibilityPolicyVersion.java": {
        "java.nio.charset.StandardCharsets",
    },
    "BasisForecastTermsEvidence.java": {
        "java.math.BigDecimal", "java.math.RoundingMode",
        "java.time.Instant", "java.time.LocalDate",
        "java.util.Currency", "java.util.Objects",
        "com.wallstreetreceipts.api.domain.PersistentInstant",
        "com.wallstreetreceipts.api.domain.call.CallDirection",
        "com.wallstreetreceipts.api.domain.outcome.horizon.OutcomeBasis",
    },
    "TargetEligibilityRequest.java": {
        "java.time.Instant", "java.util.Objects",
        "com.wallstreetreceipts.api.domain.PersistentInstant",
        "com.wallstreetreceipts.api.domain.outcome.horizon."
        "SessionCloseHorizonPolicyVersion",
        "com.wallstreetreceipts.api.domain.outcome.horizon."
        "SessionCloseHorizonResolution",
        "com.wallstreetreceipts.api.domain.outcome.observation."
        "CatalogPointInTimeEvidence",
        "com.wallstreetreceipts.api.domain.outcome.routing."
        "CalculatorSideRouting",
        "com.wallstreetreceipts.api.domain.outcome.targeterror."
        "TargetPriceEvidence",
    },
    "TargetEligibilityResolution.java": {
        "java.time.Instant", "java.util.Objects",
        "com.wallstreetreceipts.api.domain.PersistentInstant",
        "com.wallstreetreceipts.api.domain.call.CallDirection",
        "com.wallstreetreceipts.api.domain.outcome.direction."
        "CallDirectionPolarityPolicyVersion",
        "com.wallstreetreceipts.api.domain.outcome.horizon."
        "SessionCloseHorizonResolution",
        "com.wallstreetreceipts.api.domain.outcome.observation."
        "CatalogPointInTimeEvidence",
        "com.wallstreetreceipts.api.domain.outcome.routing."
        "CalculatorSideRouting",
        "com.wallstreetreceipts.api.domain.outcome.routing."
        "CalculatorSideRouting.DirectionalRoute",
        "com.wallstreetreceipts.api.domain.outcome.routing."
        "CalculatorSideRouting.NonDirectionalRoute",
        "com.wallstreetreceipts.api.domain.outcome.targeterror."
        "TargetPriceEvidence",
        "com.wallstreetreceipts.api.domain.outcome.targeteligibility."
        "BasisForecastTermsEvidence.TargetDisposition.Present",
    },
    "TargetEligibilityResolver.java": {
        "java.util.Objects",
        "com.wallstreetreceipts.api.domain.call.CallDirection",
        "com.wallstreetreceipts.api.domain.outcome.horizon."
        "SessionCloseHorizonResolution",
        "com.wallstreetreceipts.api.domain.outcome.routing."
        "CalculatorSideRouting",
        "com.wallstreetreceipts.api.domain.outcome.routing."
        "CalculatorSideRouting.DirectionalRoute",
        "com.wallstreetreceipts.api.domain.outcome.routing."
        "CalculatorSideRouting.NonDirectionalRoute",
        "com.wallstreetreceipts.api.domain.outcome.targeteligibility."
        "BasisForecastTermsEvidence.TargetDisposition.Absent",
        "com.wallstreetreceipts.api.domain.outcome.targeteligibility."
        "BasisForecastTermsEvidence.TargetDisposition.Present",
        "com.wallstreetreceipts.api.domain.outcome.targeteligibility."
        "TargetEligibilityResolution.EligibilityEvidence",
        "com.wallstreetreceipts.api.domain.outcome.targeteligibility."
        "TargetEligibilityResolution.NotApplicableReason",
        "com.wallstreetreceipts.api.domain.outcome.targeteligibility."
        "TargetEligibilityResolution.PendingReason",
        "com.wallstreetreceipts.api.domain.outcome.targeteligibility."
        "TargetEligibilityResolution.ResolutionContext",
        "com.wallstreetreceipts.api.domain.outcome.targeteligibility."
        "TargetEligibilityResolution.UnavailableReason",
    },
}
permitted_qualified_body_references = {
    "TargetEligibilityResolver.java": {
        "com.wallstreetreceipts.api.domain.outcome.direction",
        "com.wallstreetreceipts.api.domain.outcome.observation."
        "CatalogPointInTimeEvidence",
        "com.wallstreetreceipts.api.domain.outcome.targeterror."
        "TargetPriceEvidence",
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
    require(imports == expected_imports[name],
            f"Target-eligibility imports changed: {name} {imports}")
    body = re.sub(
        r"^\s*(?:package|import)\s+[^;]+;\s*",
        "",
        logic,
        flags=re.MULTILINE,
    )
    qualified = set(re.findall(
        r"\b(?:com|org|net|io|java|javax|jakarta|jdk|sun)"
        r"(?:\.[A-Za-z_$][\w$]*){2,}",
        body,
    ))
    require(
        qualified == permitted_qualified_body_references.get(name, set())
        and forbidden_runtime.search(logic) is None
        and "double" not in logic
        and "float" not in logic
        and "ObjectMapper" not in logic
        and "HttpClient" not in logic
        and "DataSource" not in logic,
        f"Target eligibility crosses its deterministic boundary: "
        f"{name} {qualified}",
    )

resolver_logic = compact(
    without_comments_or_strings(
        sources["TargetEligibilityResolver.java"]
    )
)
reason_positions = [
    resolver_logic.index(f"UnavailableReason.{reason}")
    for reason in unavailable_reasons
]
require(
    reason_positions == sorted(reason_positions)
    and resolver_logic.count(
        "availableAt().isAfter(request.evaluationAsOf())"
    ) == 3
    and resolver_logic.count(
        "capturedAt().isAfter(request.evaluationAsOf())"
    ) == 3
    and "terms.basis().equals(TargetEligibilityRequest."
    "horizonContext(request.horizonResolution()).basis())"
    in resolver_logic
    and "direction==terms.direction()" in resolver_logic
    and "target.basis().equals(terms.basis())" in resolver_logic
    and "target.assetId().equals(terms.assetId())" in resolver_logic
    and "target.currency().equals(present.sourceTargetCurrency())"
    in resolver_logic
    and "catalog.calendarId().equals(horizonContext.calendarId())"
    in resolver_logic
    and "catalog.catalogRevision().equals("
    "horizonContext.catalogRevision())" in resolver_logic
    and "resolved.window().endpointSession().closesAt()."
    "isAfter(request.evaluationAsOf())" in resolver_logic,
    "Target-eligibility PIT filtering, precedence, identity, or maturity changed",
)
require(
    "absent&&nonDirectional?NotApplicableReason."
    "TARGET_ABSENT_AND_NON_DIRECTIONAL" in resolver_logic
    and ":absent?NotApplicableReason.TARGET_ABSENT"
    in resolver_logic
    and ":NotApplicableReason.NON_DIRECTIONAL" in resolver_logic
    and "if(absent&&visibleTarget!=null){returnunavailable("
    "context,terms,request.sideRouting(),visibleTarget,null,"
    "UnavailableReason.TARGET_STATE_CONFLICT,null);}" in resolver_logic
    and resolver_logic.index(
        "UnavailableReason.TARGET_STATE_CONFLICT"
    ) < resolver_logic.index("if(absent||nonDirectional)")
    and "TARGET_DATE_SEMANTICS_UNSUPPORTED" in resolver_logic
    and "newEligibilityEvidence(terms,request.sideRouting(),null,null)"
    in resolver_logic
    and "returnunavailable(context,null,null,null,null,reason,null)"
    in resolver_logic,
    "Target absence, neutral truth table, or branch clearing changed",
)

combined_logic = "\n".join(
    without_comments_or_strings(source) for source in sources.values()
)
forbidden_calculation = (
    "TargetHitCalculator", "TargetHitInput", "TargetHitResult",
    "DirectionalWinCalculator", "DirectionalWinInput",
    "DirectionalWinResult", "AssetReturnCalculator",
    "AssetReturnInput", "AssetReturnResult", "TargetErrorCalculator",
    "TargetErrorInput", "TargetErrorResult", ".calculate(",
)
require(
    not any(marker in combined_logic for marker in forbidden_calculation)
    and "favorableExtreme" not in combined_logic
    and "previousTarget" not in combined_logic
    and "AnalystCallRevision" not in combined_logic
    and "CallOutcome" not in combined_logic
    and "ScoringMethodology" not in combined_logic,
    "Target eligibility must not invoke calculators or infer lifecycle/methodology",
)

api_main_dir = Path("apps/api/src/main/java")
new_paths = {
    (production_dir / name).resolve() for name in production_files
}
new_markers = tuple(name.removesuffix(".java") for name in production_files)
favorable_extreme_consumers = {
    (api_main_dir / "com/wallstreetreceipts/api/domain/outcome/"
     "favorableextreme/FavorableExtremeRequest.java").resolve(): {
        "com.wallstreetreceipts.api.domain.outcome.targeteligibility."
        "TargetEligibilityPolicyVersion",
        "com.wallstreetreceipts.api.domain.outcome.targeteligibility."
        "TargetEligibilityResolution",
    },
    (api_main_dir / "com/wallstreetreceipts/api/domain/outcome/"
     "favorableextreme/FavorableExtremeResolution.java").resolve(): {
        "com.wallstreetreceipts.api.domain.outcome.targeteligibility."
        "TargetEligibilityResolution",
    },
}
favorable_extreme_consumers.update({
    (api_main_dir / "com/wallstreetreceipts/api/domain/outcome/"
     "targethitorchestration/"
     "TargetHitOrchestrationPolicyVersion.java").resolve(): set(),
    (api_main_dir / "com/wallstreetreceipts/api/domain/outcome/"
     "targethitorchestration/"
     "TargetHitOrchestrationRequest.java").resolve(): {
        "com.wallstreetreceipts.api.domain.outcome.targeteligibility."
        "TargetEligibilityPolicyVersion",
        "com.wallstreetreceipts.api.domain.outcome.targeteligibility."
        "TargetEligibilityResolution",
    },
    (api_main_dir / "com/wallstreetreceipts/api/domain/outcome/"
     "targethitorchestration/"
     "TargetHitOrchestrationResolution.java").resolve(): {
        "com.wallstreetreceipts.api.domain.outcome.targeteligibility."
        "TargetEligibilityResolution",
    },
    (api_main_dir / "com/wallstreetreceipts/api/domain/outcome/"
     "targethitorchestration/TargetHitOrchestrator.java").resolve(): {
        "com.wallstreetreceipts.api.domain.outcome.targeteligibility."
        "TargetEligibilityResolution",
    },
    (api_main_dir / "com/wallstreetreceipts/api/domain/outcome/"
     "directionalwinorchestration/"
     "DirectionalWinOrchestrationRequest.java").resolve(): {
        "com.wallstreetreceipts.api.domain.outcome.targeteligibility."
        "BasisForecastTermsEvidence",
    },
    (api_main_dir / "com/wallstreetreceipts/api/domain/outcome/"
     "directionalwinorchestration/"
     "DirectionalWinOrchestrationResolution.java").resolve(): {
        "com.wallstreetreceipts.api.domain.outcome.targeteligibility."
        "BasisForecastTermsEvidence",
    },
})
for other_path in api_main_dir.rglob("*.java"):
    if other_path.resolve() in new_paths:
        continue
    other_source = other_path.read_text(encoding="utf-8")
    if other_path.resolve() in favorable_extreme_consumers:
        other_logic = without_comments_or_strings(other_source)
        eligibility_imports = {
            imported
            for imported in re.findall(
                r"^import\s+([^;]+);",
                other_logic,
                flags=re.MULTILINE,
            )
            if ".domain.outcome.targeteligibility." in imported
        }
        expected_imports = favorable_extreme_consumers[
            other_path.resolve()
        ]
        expected_types = {
            imported.rsplit(".", 1)[-1]
            for imported in expected_imports
        }
        used_types = {
            marker for marker in new_markers
            if re.search(rf"\b{re.escape(marker)}\b", other_logic)
        }
        require(
            eligibility_imports == expected_imports
            and used_types == expected_types
            and "TargetEligibilityResolver" not in other_logic
            and "TargetEligibilityRequest" not in other_logic
            and (
                "BasisForecastTermsEvidence" not in other_logic
                or expected_types == {"BasisForecastTermsEvidence"}
            ),
            "Approved composition may consume only the exact target-eligibility "
            f"V1 evidence: {other_path}",
        )
        continue
    require(
        "domain.outcome.targeteligibility" not in other_source
        and not any(marker in other_source for marker in new_markers),
        f"Target eligibility must not be wired into product runtime: {other_path}",
    )

golden = (
    test_dir / "TargetEligibilityResolverGoldenTest.java"
).read_text(encoding="utf-8")
golden_logic = without_comments_or_strings(golden)
golden_compact = compact(golden_logic)
require(
    re.search(r"\\u+[0-9a-fA-F]{4}", golden) is None
    and len(re.findall(r"@Test\b", golden_logic)) == 32
    and len(re.findall(r"@ParameterizedTest\b", golden_logic)) == 2
    and len(re.findall(r"@EnumSource\b", golden_logic)) == 2,
    "Target-eligibility golden count or lexical boundary changed",
)
for marker in (
    "canonicalDefinitionHasFixedBytesLengthAndIndependentHash",
    "exactClosedEnumOrdersAreStable",
    "exactProductionFileRecordAndSealedSurfacesAreStable",
    "everyCanonicalDirectionUsesItsExactClosedRoute",
    "exactEndpointEqualityIsReadyForWindowEvidenceOnly",
    "endpointAfterEvaluationIsPending",
    "absentTargetIsKnownNotApplicableNotMissing",
    "neutralIsKnownNotApplicableEvenWithPresentTarget",
    "absentNeutralPreservesCombinedReason",
    "visibleExactTargetConflictsWithAbsentDirectionalSourceTerms",
    "visibleWrongTargetConflictsWithAbsentNeutralBeforeCombinedNotApplicable",
    "futureTargetRemainsInvisibleToAbsentTermsAndEqualsNullNotApplicable",
    "nullAndFutureTermsAreWholeResultEqualAndLeakNothing",
    "horizonBasisMismatchPrecedesRoutingAndClearsLaterEvidence",
    "missingAndMismatchedRoutesAreExplicit",
    "datedDirectionalTargetFailsClosedBeforeTargetOrCatalogEvidence",
    "nullAndFutureTargetEvidenceAreWholeResultEqual",
    "targetMismatchPrecedenceIsBasisThenAssetThenCurrency",
    "nullAndFutureCatalogAreWholeResultEqual",
    "knownWrongCatalogIsExplicitMismatch",
    "bothHorizonCoverageReasonsArePreservedExactly",
    "correctionUsesItsOwnExactBasisAndEventClock",
    "evidenceRecordMakesPresentAndAbsentStructurallyDistinct",
    "directUnavailableCannotLeakFutureTargetEvidence",
    "termsAvailableAndCapturedTimestampsHaveIndependentInclusivePitBoundaries",
    "targetAvailableAndCapturedTimestampsHaveIndependentInclusivePitBoundaries",
    "catalogAvailableAndCapturedTimestampsHaveIndependentInclusivePitBoundaries",
    "sourceAndNormalizedTargetValuesRemainSeparateWithoutEqualityInference",
    "sourceTermsRejectNullBlankUntrimmedAndInvalidTimes",
    "presentSourceTargetEnforcesExactNumeric38Scale12Boundary",
    "requestAndContextRejectNullsFinerInstantsAndWrongHash",
    "directBranchConstructorsRejectMissingContradictoryAndFutureComponents",
    "directTargetStateConflictRequiresAbsentTermsVisibleTargetAndClearedCatalog",
    "localeAndDefaultTimezoneCannotChangeReplay",
):
    require(marker in golden_logic,
            f"Missing target-eligibility golden coverage: {marker}")
require(
    "hasSize(3862)" in golden_compact
    and policy_hash in golden
    and golden_compact.count("isEqualTo(nullResult)") == 2
    and golden_compact.count("isEqualTo(noTarget)") == 1
    and golden_compact.count("isEqualTo(noCatalog)") == 1
    and all(f"UnavailableReason.{reason}" in golden
            for reason in unavailable_reasons)
    and all(f"NotApplicableReason.{reason}" in golden
            for reason in not_applicable_reasons)
    and "PendingReason.HORIZON_NOT_REACHED_AS_OF" in golden,
    "Target-eligibility goldens must lock PIT equality and every reason",
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
    "market-treemap-sp500.json", "master-data.json",
    "timeline-nvda.json",
}
require(
    {path.name for path in Path("schemas").glob("*.json")}
    == expected_schemas
    and {path.name for path in Path("fixtures/v1").glob("*.json")}
    == expected_fixtures,
    "Target eligibility must preserve exact schemas and fixtures",
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
    "Target eligibility must preserve fixture manifest order",
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
    "Target eligibility must not publish or activate outcomes",
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
    "Target eligibility must preserve exact OpenAPI and Flyway surfaces",
)
for resource_path in Path("apps/api/src/test/resources").rglob("*.json"):
    resource = resource_path.read_text(encoding="utf-8")
    require(
        not any(marker in resource for marker in new_markers),
        f"Target eligibility must not add JSON goldens: {resource_path}",
    )
for web_path in Path("apps/web/src").rglob("*"):
    if web_path.is_file() and web_path.suffix in {".ts", ".tsx", ".js", ".jsx"}:
        web_source = web_path.read_text(encoding="utf-8")
        require(
            "domain.outcome.targeteligibility" not in web_source
            and not any(marker in web_source for marker in new_markers),
            f"Target eligibility must not expand web: {web_path}",
        )

print(
    "Validated exact point-in-time target-hit input eligibility, future "
    "evidence invisibility, closed readiness/N/A/state-conflict/"
    "unavailability, and no calculator, provider, persistence, or product "
    "publication"
)
PYTHON
