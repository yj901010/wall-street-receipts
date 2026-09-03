python <<'PYTHON'
import hashlib
import json
import re
from pathlib import Path

def require(condition, message):
    if not condition:
        raise ValueError(message)

def without_comments(source):
    source = re.sub(r"/\*.*?\*/", "", source, flags=re.DOTALL)
    return re.sub(r"//.*", "", source)

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

horizon_dir = Path(
    "apps/api/src/main/java/com/wallstreetreceipts/api/domain/outcome/horizon"
)
test_dir = Path(
    "apps/api/src/test/java/com/wallstreetreceipts/api/domain/outcome/horizon"
)
offset_files = {
    "SessionOffsetPolicyVersion.java", "TradingSession.java",
    "TradingSessionCatalog.java", "SessionOffsetRequest.java",
    "SessionOffsetResolution.java", "SessionOffsetResolver.java",
}
relation_files = {
    "EventSessionRelationPolicyVersion.java", "EventSessionRelationRequest.java",
    "EventSessionRelation.java", "EventSessionRelationClassifier.java",
}
close_policy_files = {
    "OutcomeBasis.java", "SessionCloseHorizonPolicyVersion.java",
    "SessionCloseHorizonRequest.java", "SessionCloseHorizonResolution.java",
    "SessionCloseHorizonResolver.java",
}
expected_test_files = {
    "SessionOffsetResolverGoldenTest.java",
    "EventSessionRelationClassifierGoldenTest.java",
    "SessionCloseHorizonResolverGoldenTest.java",
}
production_paths = sorted(horizon_dir.glob("*.java"))
test_paths = sorted(test_dir.glob("*.java"))
actual_production_files = {path.name for path in production_paths}
actual_test_files = {path.name for path in test_paths}
require(
    actual_production_files == offset_files | relation_files | close_policy_files,
    f"Unexpected strict-close horizon production files: {sorted(actual_production_files)}",
)
require(
    actual_test_files == expected_test_files,
    f"Unexpected strict-close horizon test files: {sorted(actual_test_files)}",
)

sources = {
    name: (horizon_dir / name).read_text(encoding="utf-8")
    for name in close_policy_files
}
basis_source = sources["OutcomeBasis.java"]
policy_source = sources["SessionCloseHorizonPolicyVersion.java"]
request_source = sources["SessionCloseHorizonRequest.java"]
result_source = sources["SessionCloseHorizonResolution.java"]
resolver_source = sources["SessionCloseHorizonResolver.java"]

expected_definition = (
    '{"policyVersion":"STRICTLY_AFTER_BASIS_EVENT_SESSION_CLOSE_V1",'
    '"lineageMode":"ORIGINAL_AND_EACH_VALID_CORRECTION",'
    '"originalEventField":"call.eventTime",'
    '"correctionEventField":"correction.eventTime",'
    '"cancellationBasisAllowed":false,'
    '"eligibleSessionPredicate":"session.closesAt>basis.eventTime",'
    '"eligibleSessionOrder":"SUPPLIED_CATALOG_ORDER",'
    '"windowSelection":"FIRST_N_ELIGIBLE",'
    '"endpointSelection":"NTH_ELIGIBLE",'
    '"firstEligibleMissingReason":"FIRST_ELIGIBLE_SESSION_MISSING",'
    '"horizonEndpointMissingReason":"HORIZON_ENDPOINT_SESSION_MISSING",'
    '"readinessState":"ABSENT",'
    '"sessionCounts":{"D1":1,"W1":5,"M1":21,"M3":63,'
    '"M6":126,"Y1":252}}'
)
expected_hash = "550087efe7ddf2ba31974c89c2740ab79df986eefef48919c32c56a3232f8dc1"
canonical_definition = java_string_constant(
    policy_source, "CANONICAL_DEFINITION"
)
definition_hash = java_string_constant(policy_source, "DEFINITION_HASH")
require(
    canonical_definition == expected_definition,
    "Canonical strict-close policy definition bytes or key order changed",
)
definition_bytes = canonical_definition.encode("utf-8")
require(
    len(definition_bytes) == 633
    and hashlib.sha256(definition_bytes).hexdigest() == expected_hash
    and definition_hash == expected_hash,
    "Canonical strict-close policy length/hash changed",
)

compact_basis = compact(basis_source)
compact_policy = compact(policy_source)
compact_request = compact(request_source)
compact_result = compact(result_source)
compact_resolver = compact(resolver_source)
basis_permits = re.search(
    r"permits\s+(?P<body>.*?)\{", basis_source, flags=re.DOTALL
)
require(basis_permits is not None, "OutcomeBasis must be sealed")
require(
    set(re.findall(r"OutcomeBasis\.(\w+)", basis_permits.group("body")))
    == {"Original", "Correction"},
    "OutcomeBasis must permit exactly Original and Correction",
)
require(
    "StringcallId();" in compact_basis
    and "StringbasisRevisionId();" in compact_basis
    and "InstanteventTime();" in compact_basis
    and "recordOriginal(StringcallId,InstanteventTime)implementsOutcomeBasis"
    in compact_basis
    and "recordCorrection(StringcallId,StringbasisRevisionId,"
    "InstanteventTime)implementsOutcomeBasis" in compact_basis
    and "publicStringbasisRevisionId(){returnnull;}" in compact_basis,
    "OutcomeBasis exact accessors, variants, or null-original identity changed",
)
require(
    re.search(
        r"enum\s+SessionCloseHorizonPolicyVersion\s*\{\s*"
        r"STRICTLY_AFTER_BASIS_EVENT_SESSION_CLOSE_V1\s*;",
        policy_source,
    ) is not None,
    "Strict-close policy enum must contain exactly the approved v1 constant",
)
for marker in (
    "publicStringcanonicalDefinition()",
    "publicbyte[]canonicalDefinitionUtf8()",
    "publicStringdefinitionHash()",
    "publicintsessionCount(OutcomeHorizonhorizon)",
    "caseD1->1", "caseW1->5", "caseM1->21",
    "caseM3->63", "caseM6->126", "caseY1->252",
):
    require(marker in compact_policy, f"Missing strict-close policy marker: {marker}")
require(
    "getBytes(StandardCharsets.UTF_8)" in policy_source
    and "Objects.requireNonNull(horizon" in policy_source,
    "Policy must return exact UTF-8 bytes and reject a null horizon",
)
require(
    "publicrecordSessionCloseHorizonRequest("
    "SessionCloseHorizonPolicyVersionpolicyVersion,OutcomeBasisbasis,"
    "OutcomeHorizonhorizon,TradingSessionCatalogcatalog)" in compact_request,
    "SessionCloseHorizonRequest exact surface changed",
)
require(
    "recordResolutionContext(SessionCloseHorizonPolicyVersionpolicyVersion,"
    "StringpolicyDefinitionHash,OutcomeBasisbasis,OutcomeHorizonhorizon,"
    "intsessionCount,StringcalendarId,StringcatalogRevision)" in compact_result
    and "recordResolvedSessionWindow(ResolutionContextcontext,"
    "List<TradingSession>sessions,TradingSessionendpointSession)" in compact_result
    and "recordResolved(ResolvedSessionWindowwindow)" in compact_result
    and "recordIncomplete(ResolutionContextcontext,IncompleteReasonreason)"
    in compact_result,
    "Strict-close context/window/result record shape changed",
)
result_permits = re.search(
    r"permits\s+(?P<body>.*?)\{", result_source, flags=re.DOTALL
)
require(result_permits is not None, "SessionCloseHorizonResolution must be sealed")
require(
    set(re.findall(
        r"SessionCloseHorizonResolution\.(\w+)",
        result_permits.group("body"),
    )) == {"Resolved", "Incomplete"},
    "Strict-close result variants changed",
)
reasons = re.search(
    r"enum\s+IncompleteReason\s*\{(?P<body>.*?)\}",
    result_source,
    flags=re.DOTALL,
)
require(reasons is not None, "Missing strict-close incomplete reasons")
require(
    re.findall(r"\b[A-Z][A-Z_]+\b", reasons.group("body"))
    == ["FIRST_ELIGIBLE_SESSION_MISSING", "HORIZON_ENDPOINT_SESSION_MISSING"],
    "Strict-close incomplete reasons changed",
)
require(
    "policyVersion.definitionHash().equals(policyDefinitionHash)" in result_source
    and "sessionCount != policyVersion.sessionCount(horizon)" in result_source
    and "sessions=List.copyOf(sessions);" in compact_result
    and "endpointSession.equals(sessions.getLast())" in result_source
    and "closesAt().isAfter(context.basis().eventTime())" in result_source,
    "Resolution constructors no longer enforce policy/window invariants",
)

require(
    "sessions.get(index).closesAt().isAfter(basisEventTime)" in resolver_source
    and "orderedSessions.subList(firstEligibleIndex,endExclusive)"
    in compact_resolver
    and "FIRST_ELIGIBLE_SESSION_MISSING" in resolver_source
    and "HORIZON_ENDPOINT_SESSION_MISSING" in resolver_source,
    "Resolver must use the direct strictly-after-close state machine",
)
forbidden_resolver_markers = (
    "EventSessionRelation", "SessionOffset", "anchorSession",
    "evaluationAsOf", "Ready", "Pending", ".sort(", ".sorted(",
    ".plusDays(", ".plusWeeks(", ".plusMonths(", ".plusYears(",
)
require(
    not any(marker in resolver_source for marker in forbidden_resolver_markers),
    "Strict-close resolver inferred an anchor/calendar/readiness state",
)

allowed_internal_imports = {
    "com.wallstreetreceipts.api.domain.PersistentInstant",
    "com.wallstreetreceipts.api.domain.outcome.OutcomeHorizon",
}
allowed_java_imports = {
    "java.time.Instant",
    "java.nio.charset.StandardCharsets",
    "java.util.Objects",
    "java.util.List",
    "java.util.HashSet",
    "java.util.Set",
}
allowed_internal_prefix = "com.wallstreetreceipts.api.domain.outcome.horizon."
forbidden_code = re.compile(
    r"\b(?:AnalystCall|AnalystCallRevision|CallOutcome|CallDirection|"
    r"TargetHit\w*|DirectionalWin\w*|EventSessionRelation\w*|"
    r"SessionOffset\w*|\w*Provider|\w*Repository|ObjectMapper|JdbcTemplate|"
    r"Clock|LocalDate|LocalDateTime|OffsetDateTime|ZonedDateTime|ZoneId|"
    r"ZoneOffset|Locale|TimeZone|DayOfWeek|Month|Period|Duration|"
    r"ChronoUnit|TemporalAdjuster|"
    r"Random|BigDecimal|BigInteger|MathContext|RoundingMode|double|float|"
    r"evaluationAsOf|processingTime|capturedAt|provenanceId|"
    r"sourceReferenceId|price\w*|observation\w*)\b",
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
        f"Strict-close source crosses the pure boundary: {source_name} {imports}",
    )
    require(
        qualified_java_types <= allowed_java_imports,
        f"Strict-close source uses a non-allowlisted Java type: "
        f"{source_name} {sorted(qualified_java_types)}",
    )
    require(
        "org.springframework" not in code
        and forbidden_code.search(code) is None
        and forbidden_runtime_code.search(code) is None
        and "@Component" not in code
        and "@Service" not in code,
        f"Strict-close source contains provider/runtime/observation wiring: {source_name}",
    )

api_main_dir = Path("apps/api/src/main/java")
close_policy_paths = {
    (horizon_dir / file_name).resolve() for file_name in close_policy_files
}
close_policy_markers = tuple(
    file_name.removesuffix(".java") for file_name in close_policy_files
)
target_eligibility_horizon_references = {
    (api_main_dir / "com/wallstreetreceipts/api/domain/outcome/"
     "targeteligibility/BasisForecastTermsEvidence.java").resolve(): {
        "com.wallstreetreceipts.api.domain.outcome.horizon.OutcomeBasis",
    },
    (api_main_dir / "com/wallstreetreceipts/api/domain/outcome/"
     "targeteligibility/TargetEligibilityRequest.java").resolve(): {
        "com.wallstreetreceipts.api.domain.outcome.horizon."
        "SessionCloseHorizonPolicyVersion",
        "com.wallstreetreceipts.api.domain.outcome.horizon."
        "SessionCloseHorizonResolution",
    },
    (api_main_dir / "com/wallstreetreceipts/api/domain/outcome/"
     "targeteligibility/TargetEligibilityResolution.java").resolve(): {
        "com.wallstreetreceipts.api.domain.outcome.horizon."
        "SessionCloseHorizonResolution",
    },
    (api_main_dir / "com/wallstreetreceipts/api/domain/outcome/"
     "targeteligibility/TargetEligibilityResolver.java").resolve(): {
        "com.wallstreetreceipts.api.domain.outcome.horizon."
        "SessionCloseHorizonResolution",
    },
}
target_eligibility_horizon_references.update({
    (api_main_dir / "com/wallstreetreceipts/api/domain/outcome/"
     "favorableextreme/FullWindowHighLowObservation.java").resolve(): {
        "com.wallstreetreceipts.api.domain.outcome.horizon.OutcomeBasis",
    },
    (api_main_dir / "com/wallstreetreceipts/api/domain/outcome/"
     "favorableextreme/FavorableExtremeResolution.java").resolve(): {
        "com.wallstreetreceipts.api.domain.outcome.horizon."
        "SessionCloseHorizonResolution",
    },
    (api_main_dir / "com/wallstreetreceipts/api/domain/outcome/"
     "favorableextreme/FavorableExtremeSelector.java").resolve(): {
        "com.wallstreetreceipts.api.domain.outcome.horizon."
        "SessionCloseHorizonResolution",
    },
})
target_eligibility_horizon_references.update({
    (
        api_main_dir
        / "com/wallstreetreceipts/api/domain/outcome/benchmarkassignment/"
        "BenchmarkAssetClassificationEvidence.java"
    ).resolve(): {
        "com.wallstreetreceipts.api.domain.outcome.horizon.OutcomeBasis",
    },
    (
        api_main_dir
        / "com/wallstreetreceipts/api/domain/outcome/benchmarkassignment/"
        "BenchmarkAssignmentEvidence.java"
    ).resolve(): {
        "com.wallstreetreceipts.api.domain.outcome.horizon.OutcomeBasis",
    },
    (
        api_main_dir
        / "com/wallstreetreceipts/api/domain/outcome/benchmarkassignment/"
        "BenchmarkAssignmentRequest.java"
    ).resolve(): {
        "com.wallstreetreceipts.api.domain.outcome.horizon.OutcomeBasis",
    },
    (
        api_main_dir
        / "com/wallstreetreceipts/api/domain/outcome/benchmarkassignment/"
        "BenchmarkAssignmentResolution.java"
    ).resolve(): {
        "com.wallstreetreceipts.api.domain.outcome.horizon.OutcomeBasis",
    },
    (
        api_main_dir
        / "com/wallstreetreceipts/api/domain/outcome/sectorassignment/"
        "SectorAssetClassificationEvidence.java"
    ).resolve(): {
        "com.wallstreetreceipts.api.domain.outcome.horizon.OutcomeBasis",
    },
    (
        api_main_dir
        / "com/wallstreetreceipts/api/domain/outcome/sectorassignment/"
        "SectorMembershipEvidence.java"
    ).resolve(): {
        "com.wallstreetreceipts.api.domain.outcome.horizon.OutcomeBasis",
    },
    (
        api_main_dir
        / "com/wallstreetreceipts/api/domain/outcome/sectorassignment/"
        "SectorAssignmentRequest.java"
    ).resolve(): {
        "com.wallstreetreceipts.api.domain.outcome.horizon.OutcomeBasis",
    },
    (
        api_main_dir
        / "com/wallstreetreceipts/api/domain/outcome/sectorassignment/"
        "SectorAssignmentResolution.java"
    ).resolve(): {
        "com.wallstreetreceipts.api.domain.outcome.horizon.OutcomeBasis",
    },
})
endpoint_request_path = (
    api_main_dir / "com/wallstreetreceipts/api/domain/outcome/observation/"
    "EndpointPriceRequest.java"
).resolve()
endpoint_resolution_path = (
    api_main_dir / "com/wallstreetreceipts/api/domain/outcome/observation/"
    "EndpointPriceResolution.java"
).resolve()
target_evidence_path = (
    api_main_dir / "com/wallstreetreceipts/api/domain/outcome/targeterror/"
    "TargetPriceEvidence.java"
).resolve()
price_pair_evidence_paths = {
    (
        api_main_dir
        / "com/wallstreetreceipts/api/domain/outcome/pricepair/"
        "BasisPriceObservation.java"
    ).resolve(),
    (
        api_main_dir
        / "com/wallstreetreceipts/api/domain/outcome/pricepair/"
        "PricePairAdjustmentEvidence.java"
    ).resolve(),
}
all_horizon_type_markers = {
    path.stem for path in horizon_dir.glob("*.java")
}
for other_path in api_main_dir.rglob("*.java"):
    if other_path.resolve() in close_policy_paths:
        continue
    other_source = other_path.read_text(encoding="utf-8")
    other_code = without_comments(other_source)
    other_logic = re.sub(
        r'"(?:\\.|[^"\\])*"', '""', other_code
    )
    horizon_imports = {
        imported
        for imported in re.findall(
            r"^import\s+([^;]+);", other_code, flags=re.MULTILINE
        )
        if ".domain.outcome.horizon." in imported
    }
    used_horizon_types = {
        marker for marker in all_horizon_type_markers
        if re.search(rf"\b{re.escape(marker)}\b", other_code)
    }
    qualified_horizon_refs = sorted(re.findall(
        r"com\.wallstreetreceipts\.api\.domain\.outcome\.horizon\."
        r"([A-Za-z_$][\w$]*(?:\.[A-Za-z_$][\w$]*)*)",
        other_code,
    ))
    if other_path.resolve() in target_eligibility_horizon_references:
        expected_imports = target_eligibility_horizon_references[
            other_path.resolve()
        ]
        expected_types = {
            imported.rsplit(".", 1)[-1]
            for imported in expected_imports
        }
        expected_qualified = sorted(
            imported.removeprefix(
                "com.wallstreetreceipts.api.domain.outcome.horizon."
            )
            for imported in expected_imports
        )
        require(
            horizon_imports == expected_imports
            and used_horizon_types == expected_types
            and qualified_horizon_refs == expected_qualified,
            "ADR-018 target eligibility may consume only exact strict-close "
            f"V1 evidence: {other_path}",
        )
        continue
    if other_path.resolve() == endpoint_request_path:
        require(
            horizon_imports == {
                "com.wallstreetreceipts.api.domain.outcome.horizon."
                "SessionCloseHorizonResolution.Resolved"
            }
            and other_code.count("SessionCloseHorizonResolution") == 1
            and used_horizon_types == {"SessionCloseHorizonResolution"}
            and qualified_horizon_refs
            == ["SessionCloseHorizonResolution.Resolved"],
            "ADR-014 request may consume only strict-close Resolved",
        )
        continue
    if other_path.resolve() == endpoint_resolution_path:
        require(
            horizon_imports == {
                "com.wallstreetreceipts.api.domain.outcome.horizon."
                "SessionCloseHorizonPolicyVersion",
                "com.wallstreetreceipts.api.domain.outcome.horizon."
                "SessionCloseHorizonResolution.ResolvedSessionWindow",
            }
            and other_code.count("SessionCloseHorizonPolicyVersion") == 2
            and other_code.count("SessionCloseHorizonResolution") == 2
            and other_code.count("ResolvedSessionWindow") == 2
            and used_horizon_types == {
                "SessionCloseHorizonPolicyVersion",
                "SessionCloseHorizonResolution",
            }
            and qualified_horizon_refs == [
                "SessionCloseHorizonPolicyVersion",
                "SessionCloseHorizonResolution.Resolved",
                "SessionCloseHorizonResolution.ResolvedSessionWindow",
            ],
            "ADR-014 resolution may consume only exact strict-close V1 evidence",
        )
        continue
    if other_path.resolve() == target_evidence_path:
        require(
            horizon_imports == {
                "com.wallstreetreceipts.api.domain.outcome.horizon.OutcomeBasis"
            }
            and other_code.count("OutcomeBasis") == 2
            and used_horizon_types == {"OutcomeBasis"}
            and qualified_horizon_refs == ["OutcomeBasis"],
            "ADR-015 target evidence may consume only OutcomeBasis",
        )
        continue
    if other_path.resolve() in price_pair_evidence_paths:
        require(
            horizon_imports == {
                "com.wallstreetreceipts.api.domain.outcome.horizon.OutcomeBasis"
            }
            and other_code.count("OutcomeBasis") == 2
            and used_horizon_types == {"OutcomeBasis"}
            and qualified_horizon_refs == ["OutcomeBasis"],
            "ADR-016 price-pair evidence may consume only OutcomeBasis",
        )
        continue
    require(
        not any(marker in other_logic for marker in close_policy_markers),
        f"Strict-close policy must not be wired into product runtime: {other_path}",
    )

golden_source = (
    test_dir / "SessionCloseHorizonResolverGoldenTest.java"
).read_text(encoding="utf-8")
compact_golden = compact(golden_source)
for marker in (
    "namedHorizonVectors", "namedHorizonShortageVectors",
    "reportsTheExactShortageReasonForEveryNamedHorizon",
    "strictBoundaryVectors",
    "firstEligibleMissingVectors",
    "touchingCloseAndOpenAndItsFirstInteriorMicrosecondResolveTheOpeningSession",
    "countsOnlyPublishedSessionsAcrossWeekendLikeGapsSaturdayAndEarlyClose",
    "originalAndEachCallerValidatedCorrectionResolveAsSeparatePreservedLineages",
    "canonicalPolicyDefinitionHasStableExactUtf8BytesAndIndependentSha256",
    "defensivelyCopiesCatalogAndResolvedWindowAndReplaysDeterministically",
    "resultDoesNotDependOnJvmDefaultLocaleOrTimeZone",
    "invalidBasisVectors", "invalidRequestVectors",
    "publicResolutionConstructorsEnforcePolicyAndWindowInvariants",
    "policyBasisAndResultSurfacesRemainClosedAndScheduleOnly",
):
    require(marker in golden_source, f"Missing strict-close golden coverage: {marker}")
for marker in (
    "Arguments.of(OutcomeHorizon.D1,1)",
    "Arguments.of(OutcomeHorizon.W1,5)",
    "Arguments.of(OutcomeHorizon.M1,21)",
    "Arguments.of(OutcomeHorizon.M3,63)",
    "Arguments.of(OutcomeHorizon.M6,126)",
    "Arguments.of(OutcomeHorizon.Y1,252)",
    "Arguments.of(OutcomeHorizon.D1,0,"
    "IncompleteReason.FIRST_ELIGIBLE_SESSION_MISSING)",
    "Arguments.of(OutcomeHorizon.W1,4,"
    "IncompleteReason.HORIZON_ENDPOINT_SESSION_MISSING)",
    "Arguments.of(OutcomeHorizon.M1,20,"
    "IncompleteReason.HORIZON_ENDPOINT_SESSION_MISSING)",
    "Arguments.of(OutcomeHorizon.M3,62,"
    "IncompleteReason.HORIZON_ENDPOINT_SESSION_MISSING)",
    "Arguments.of(OutcomeHorizon.M6,125,"
    "IncompleteReason.HORIZON_ENDPOINT_SESSION_MISSING)",
    "Arguments.of(OutcomeHorizon.Y1,251,"
    "IncompleteReason.HORIZON_ENDPOINT_SESSION_MISSING)",
    "FIRST_OPEN.minusNanos(1_000)",
    "firstClose.minusNanos(1_000)",
    "firstClose.plusNanos(1_000)",
    "closing.closesAt().plusNanos(1_000)",
    "call-weekend-gap", "session-saturday", "session-early-close",
    "irregularEarlyClose.closesAt().minusNanos(1_000)",
    "newOriginal(\"call-at-early-close\",irregularEarlyClose.closesAt())",
    "newCorrection(\"call-lineage\",\"revision-1\"",
    "newCorrection(\"call-lineage\",\"revision-2\"",
    "MessageDigest.getInstance(\"SHA-256\")",
    "firstRead[0]=(byte)'!'",
    "duplicateIdAtEndpoint", "exactFirstCloseBasis",
    "policy().sessionCount(null)",
    "Locale.setDefault", "TimeZone.setDefault", "finally",
):
    require(marker in compact_golden, f"Missing mutation-sensitive golden: {marker}")
require(
    "ObjectMapper" not in golden_source
    and "ClassPathResource" not in golden_source,
    "Strict-close goldens must remain source-local Java values",
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
    "Strict-close policy must preserve the exact 14 schemas",
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
    {path.name for path in fixture_dir.glob("*.json")} == expected_fixture_files,
    "Strict-close policy must not add a canonical calendar/basis fixture",
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
    "Strict-close policy must preserve manifest membership/order",
)
outcomes = json.loads(
    (fixture_dir / "call-outcomes.json").read_text(encoding="utf-8")
)
metrics = (
    "assetReturn", "benchmarkReturn", "sectorReturn", "alpha", "sectorAlpha",
    "mfe", "mae", "targetHit", "directionalWin", "targetError",
)
require(
    len(outcomes["methodologies"]) == 2
    and all(item["status"] == "MODEL_ONLY" for item in outcomes["methodologies"])
    and len(outcomes["outcomes"]) == 4
    and {item["evaluationStatus"] for item in outcomes["outcomes"]}
    == {"PENDING", "INCOMPLETE"}
    and all(
        item[field] is None
        for item in outcomes["outcomes"]
        for field in metrics
    ),
    "Strict-close policy must not activate/publish methodology outcome facts",
)
openapi_source = Path("contracts/openapi.yaml").read_text(encoding="utf-8")
require(
    set(re.findall(r"^  (/[^\n]+):\s*$", openapi_source, re.MULTILINE))
    == {
        "/v1/calls", "/v1/calls/{id}", "/v1/calls/{id}/revisions",
        "/v1/calls/{id}/outcomes", "/v1/calls/{id}/context",
    },
    "Strict-close policy must preserve the exact five OpenAPI paths",
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
    "Strict-close policy must preserve the exact nine Flyway migrations",
)
for resource_path in Path("apps/api/src/test/resources").rglob("*.json"):
    resource_source = resource_path.read_text(encoding="utf-8")
    require(
        not any(marker in resource_source for marker in close_policy_markers),
        f"Strict-close policy must not add a JSON golden: {resource_path}",
    )
for web_path in Path("apps/web/src").rglob("*"):
    if web_path.is_file() and web_path.suffix in {".ts", ".tsx", ".js", ".jsx"}:
        web_source = web_path.read_text(encoding="utf-8")
        require(
            not any(marker in web_source for marker in close_policy_markers),
            f"Strict-close policy must not expand the web surface: {web_path}",
        )

print(
    "Validated exact original/correction basis, 633-byte hashed strict-close "
    "definition, 1/5/21/63/126/252 named horizons, missing coverage, "
    "source-local goldens, and no observation/provider/product publication"
)
PYTHON
