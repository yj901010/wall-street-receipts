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

horizon_dir = Path(
    "apps/api/src/main/java/com/wallstreetreceipts/api/domain/outcome/horizon"
)
test_dir = Path(
    "apps/api/src/test/java/com/wallstreetreceipts/api/domain/outcome/horizon"
)
expected_offset_production_files = {
    "SessionOffsetPolicyVersion.java",
    "TradingSession.java",
    "TradingSessionCatalog.java",
    "SessionOffsetRequest.java",
    "SessionOffsetResolution.java",
    "SessionOffsetResolver.java",
}
expected_relation_production_files = {
    "EventSessionRelationPolicyVersion.java",
    "EventSessionRelationRequest.java",
    "EventSessionRelation.java",
    "EventSessionRelationClassifier.java",
}
expected_close_policy_production_files = {
    "OutcomeBasis.java",
    "SessionCloseHorizonPolicyVersion.java",
    "SessionCloseHorizonRequest.java",
    "SessionCloseHorizonResolution.java",
    "SessionCloseHorizonResolver.java",
}
expected_production_files = (
    expected_offset_production_files
    | expected_relation_production_files
    | expected_close_policy_production_files
)
expected_test_files = {
    "SessionOffsetResolverGoldenTest.java",
    "EventSessionRelationClassifierGoldenTest.java",
    "SessionCloseHorizonResolverGoldenTest.java",
}
production_paths = sorted(
    path for path in horizon_dir.rglob("*.java") if path.is_file()
)
test_paths = sorted(path for path in test_dir.rglob("*.java") if path.is_file())
actual_production_files = {
    path.relative_to(horizon_dir).as_posix() for path in production_paths
}
actual_test_files = {
    path.relative_to(test_dir).as_posix() for path in test_paths
}
require(
    actual_production_files == expected_production_files,
    f"Unexpected session-offset production files: {sorted(actual_production_files)}",
)
require(
    actual_test_files == expected_test_files,
    f"Unexpected session-offset test files: {sorted(actual_test_files)}",
)

all_production_sources = {
    path.relative_to(horizon_dir).as_posix(): path.read_text(encoding="utf-8")
    for path in production_paths
}
production_sources = {
    name: all_production_sources[name]
    for name in expected_offset_production_files
}
policy_source = production_sources["SessionOffsetPolicyVersion.java"]
session_source = production_sources["TradingSession.java"]
catalog_source = production_sources["TradingSessionCatalog.java"]
request_source = production_sources["SessionOffsetRequest.java"]
result_source = production_sources["SessionOffsetResolution.java"]
resolver_source = production_sources["SessionOffsetResolver.java"]

require(
    re.search(
        r"enum\s+SessionOffsetPolicyVersion\s*\{\s*"
        r"EXPLICIT_ANCHOR_SESSION_COUNT_V1\s*\}",
        policy_source,
    ) is not None,
    "Session-offset policy enum must contain exactly the code-only v1 policy",
)
compact_session = re.sub(r"\s+", "", session_source)
compact_catalog = re.sub(r"\s+", "", catalog_source)
compact_request = re.sub(r"\s+", "", request_source)
compact_result = re.sub(r"\s+", "", result_source)
require(
    "publicrecordTradingSession(StringsessionId,InstantopensAt,InstantclosesAt)"
    in compact_session,
    "TradingSession must contain exactly sessionId, opensAt, and closesAt",
)
require(
    "publicrecordTradingSessionCatalog(StringcalendarId,Stringrevision,"
    "List<TradingSession>orderedSessions)" in compact_catalog,
    "TradingSessionCatalog must contain exactly calendarId, revision, orderedSessions",
)
require(
    "publicrecordSessionOffsetRequest(SessionOffsetPolicyVersionpolicyVersion,"
    "StringanchorSessionId,intsessionCount,InstantevaluationAsOf,"
    "TradingSessionCatalogcatalog)" in compact_request,
    "SessionOffsetRequest must expose only the locked schedule inputs",
)
require(
    "recordResolutionContext(SessionOffsetPolicyVersionpolicyVersion,"
    "StringcalendarId,StringcatalogRevision,StringanchorSessionId,"
    "intsessionCount,InstantevaluationAsOf)" in compact_result,
    "SessionOffsetResolution must retain the exact resolution context",
)
require(
    "recordResolvedSessionWindow(ResolutionContextcontext,"
    "TradingSessionanchorSession,List<TradingSession>sessions,"
    "TradingSessionendpointSession)" in compact_result,
    "SessionOffsetResolution must expose the exact resolved window",
)
require(
    "recordReady(ResolvedSessionWindowwindow)" in compact_result
    and "recordPending(ResolvedSessionWindowwindow,PendingReasonreason)"
    in compact_result
    and "recordIncomplete(ResolutionContextcontext,IncompleteReasonreason)"
    in compact_result,
    "Session-offset result variants must be exact",
)
pending_match = re.search(
    r"enum\s+PendingReason\s*\{(?P<body>.*?)\}", result_source, re.DOTALL
)
incomplete_match = re.search(
    r"enum\s+IncompleteReason\s*\{(?P<body>.*?)\}", result_source, re.DOTALL
)
require(pending_match is not None, "Missing session-offset pending reason")
require(incomplete_match is not None, "Missing session-offset incomplete reasons")
pending_reasons = re.findall(r"\b[A-Z][A-Z_]+\b", pending_match.group("body"))
incomplete_reasons = re.findall(
    r"\b[A-Z][A-Z_]+\b", incomplete_match.group("body")
)
require(
    pending_reasons == ["ENDPOINT_NOT_REACHED"],
    f"Unexpected session-offset pending reasons: {pending_reasons}",
)
require(
    incomplete_reasons
    == ["ANCHOR_SESSION_MISSING", "ENDPOINT_SESSION_MISSING"],
    f"Unexpected session-offset incomplete reasons: {incomplete_reasons}",
)
require(
    "List.copyOf" in catalog_source and "List.copyOf" in result_source,
    "Catalog and resolved-window sessions must be defensively copied",
)

allowed_internal_imports = {
    "com.wallstreetreceipts.api.domain.PersistentInstant",
}
allowed_internal_prefix = (
    "com.wallstreetreceipts.api.domain.outcome.horizon."
)
forbidden_code = re.compile(
    r"\b(?:OutcomeHorizon|CallOutcome|OutcomeEvaluationStatus|OutcomeReasonCode|"
    r"TargetHit|Clock|LocalDate|LocalDateTime|OffsetDateTime|ZonedDateTime|"
    r"ZoneId|ZoneOffset|Locale|TimeZone|DayOfWeek|Month|Period|Duration|"
    r"ChronoUnit|TemporalAdjuster|"
    r"Provider|Repository|ObjectMapper|JdbcTemplate|Random|double|float|"
    r"eventTime|observation|capturedAt|provenanceId|sourceReferenceId)\b"
)
forbidden_calendar_calls = (
    ".plusDays(", ".plusWeeks(", ".plusMonths(", ".plusYears(",
    ".atZone(", "ZoneOffset.systemDefault(", "Calendar.getInstance(",
)
for source_name, source in production_sources.items():
    code = without_comments(source)
    imports = re.findall(r"^import\s+([^;]+);", code, flags=re.MULTILINE)
    require(
        all(
            imported.startswith("java.")
            or imported in allowed_internal_imports
            or imported.startswith(allowed_internal_prefix)
            for imported in imports
        ),
        f"Session-offset source crosses the pure boundary: {source_name} {imports}",
    )
    require(
        "org.springframework" not in code
        and forbidden_code.search(code) is None
        and not any(marker in code for marker in forbidden_calendar_calls)
        and "@Component" not in code
        and "@Service" not in code,
        f"Session-offset source contains forbidden inference/wiring: {source_name}",
    )

require(
    "request.anchorSessionId()" in resolver_source
    and "request.sessionCount()" in resolver_source
    and "request.evaluationAsOf()" in resolver_source
    and "ENDPOINT_NOT_REACHED" in resolver_source
    and "ANCHOR_SESSION_MISSING" in resolver_source
    and "ENDPOINT_SESSION_MISSING" in resolver_source,
    "Resolver must use only the explicit anchor/count/as-of state machine",
)
require(
    "isBefore" in resolver_source and "isAfter" not in resolver_source,
    "Ready must use the inclusive endpoint-close boundary",
)

api_main_dir = Path("apps/api/src/main/java")
type_markers = tuple(
    path.removesuffix(".java") for path in expected_offset_production_files
)
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
all_horizon_type_markers = {
    path.stem for path in horizon_dir.glob("*.java")
}
for other_path in api_main_dir.rglob("*.java"):
    if horizon_dir in other_path.parents:
        continue
    other_source = other_path.read_text(encoding="utf-8")
    other_code = without_comments(other_source)
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
            and qualified_horizon_refs == expected_qualified
            and not any(marker in other_code for marker in type_markers),
            "ADR-018 target eligibility may consume only its exact "
            f"horizon evidence: {other_path}",
        )
        continue
    if other_path.resolve() == endpoint_request_path:
        require(
            horizon_imports == {
                "com.wallstreetreceipts.api.domain.outcome.horizon."
                "SessionCloseHorizonResolution.Resolved"
            }
            and used_horizon_types == {"SessionCloseHorizonResolution"}
            and qualified_horizon_refs
            == ["SessionCloseHorizonResolution.Resolved"]
            and not any(marker in other_code for marker in type_markers),
            "Endpoint request may consume only strict-close Resolved",
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
            and len(re.findall(
                r"com\.wallstreetreceipts\.api\.domain\.outcome\.horizon\."
                r"SessionCloseHorizonResolution\.Resolved\b",
                other_code,
            )) == 1
            and used_horizon_types == {
                "SessionCloseHorizonPolicyVersion",
                "SessionCloseHorizonResolution",
            }
            and qualified_horizon_refs == [
                "SessionCloseHorizonPolicyVersion",
                "SessionCloseHorizonResolution.Resolved",
                "SessionCloseHorizonResolution.ResolvedSessionWindow",
            ]
            and not any(marker in other_code for marker in type_markers),
            "Endpoint resolution may consume only exact strict-close V1 evidence",
        )
        continue
    if other_path.resolve() == target_evidence_path:
        require(
            horizon_imports == {
                "com.wallstreetreceipts.api.domain.outcome.horizon.OutcomeBasis"
            }
            and used_horizon_types == {"OutcomeBasis"}
            and qualified_horizon_refs == ["OutcomeBasis"]
            and not any(marker in other_code for marker in type_markers),
            "Target evidence may consume only OutcomeBasis",
        )
        continue
    if other_path.resolve() in price_pair_evidence_paths:
        require(
            horizon_imports == {
                "com.wallstreetreceipts.api.domain.outcome.horizon.OutcomeBasis"
            }
            and used_horizon_types == {"OutcomeBasis"}
            and qualified_horizon_refs == ["OutcomeBasis"]
            and not any(marker in other_code for marker in type_markers),
            "ADR-016 price-pair evidence may consume only OutcomeBasis",
        )
        continue
    require(
        "domain.outcome.horizon" not in other_source
        and not any(marker in other_source for marker in type_markers),
        f"Session-offset leaf must not be wired into production: {other_path}",
    )

golden_path = test_dir / "SessionOffsetResolverGoldenTest.java"
golden_source = golden_path.read_text(encoding="utf-8")
compact_golden = re.sub(r"\s+", "", golden_source)
for marker in (
    "EXPLICIT_ANCHOR_SESSION_COUNT_V1",
    "ENDPOINT_NOT_REACHED",
    "ANCHOR_SESSION_MISSING",
    "ENDPOINT_SESSION_MISSING",
    "Integer.MAX_VALUE",
):
    require(marker in golden_source, f"Missing session-offset golden coverage: {marker}")
require(
    golden_source.count("@MethodSource") >= 3
    and "evaluationAsOf" in golden_source
    and "sessionCount" in golden_source
    and "anchorSessionId" in golden_source
    and "sessionCountFiveCountsAnExplicitSaturdayButDoesNotInventAnOmittedWeekday"
    in golden_source
    and "FRIDAY.sessionId(),5,FRIDAY_AFTER_GAP.closesAt(),catalog"
    in compact_golden
    and '.contains("session-2026-03-07")' in compact_golden
    and '.doesNotContain("session-2026-03-11")' in compact_golden
    and "Locale.setDefault" in golden_source
    and "TimeZone.setDefault" in golden_source
    and "originalLocale" in golden_source
    and "originalTimeZone" in golden_source
    and "finally" in golden_source,
    "Session-offset parameterized golden coverage is incomplete",
)
require(
    "ObjectMapper" not in golden_source
    and "ClassPathResource" not in golden_source,
    "Session-offset goldens must remain source-local Java schedules",
)
for resource_path in Path("apps/api/src/test/resources").rglob("*.json"):
    resource_source = resource_path.read_text(encoding="utf-8")
    require(
        "SessionOffset" not in resource_source
        and "sessionOffset" not in resource_source
        and "TradingSessionCatalog" not in resource_source,
        f"Session-offset must not add a JSON golden resource: {resource_path}",
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
    "P3 session-offset mechanics must not expand schemas",
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
    "P3 session-offset mechanics must not add a canonical calendar fixture",
)
manifest = json.loads((fixture_dir / "manifest.json").read_text(encoding="utf-8"))
expected_manifest_paths = [
    "master-data.json", "analyst-calls.json", "analyst-call-revisions.json",
    "call-outcomes.json", "call-contexts.json", "market-snapshots.json",
    "market-map.json", "market-map-nasdaq100.json",
    "market-treemap-sp500.json", "market-treemap-nasdaq100.json",
    "timeline-nvda.json", "market-board.json",
]
require(
    [entry["path"] for entry in manifest["files"]] == expected_manifest_paths,
    "P3 session-offset mechanics must not change manifest membership/order",
)
outcome_document = json.loads(
    (fixture_dir / "call-outcomes.json").read_text(encoding="utf-8")
)
metric_fields = (
    "assetReturn", "benchmarkReturn", "sectorReturn", "alpha", "sectorAlpha",
    "mfe", "mae", "targetHit", "directionalWin", "targetError",
)
require(
    len(outcome_document["methodologies"]) == 2
    and all(item["status"] == "MODEL_ONLY" for item in outcome_document["methodologies"]),
    "Session-offset mechanics must not activate a methodology",
)
require(
    len(outcome_document["outcomes"]) == 4
    and {item["evaluationStatus"] for item in outcome_document["outcomes"]}
    == {"PENDING", "INCOMPLETE"}
    and all(
        item[field] is None
        for item in outcome_document["outcomes"]
        for field in metric_fields
    ),
    "Session-offset mechanics must not publish a calculated outcome",
)
openapi_source = Path("contracts/openapi.yaml").read_text(encoding="utf-8")
require(
    set(re.findall(r"^  (/[^\n]+):\s*$", openapi_source, re.MULTILINE))
    == {
        "/v1/calls", "/v1/calls/{id}", "/v1/calls/{id}/revisions",
        "/v1/calls/{id}/outcomes", "/v1/calls/{id}/context",
    },
    "Session-offset mechanics must not change OpenAPI paths",
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
    "Session-offset mechanics must not add a Flyway migration",
)
web_markers = (
    type_markers + tuple(pending_reasons) + tuple(incomplete_reasons)
)
for web_path in Path("apps/web/src").rglob("*"):
    if web_path.is_file() and web_path.suffix in {".ts", ".tsx", ".js", ".jsx"}:
        web_source = web_path.read_text(encoding="utf-8")
        require(
            not any(marker in web_source for marker in web_markers),
            f"Session-offset mechanics must not expand the web surface: {web_path}",
        )

print(
    "Validated explicit-anchor next-N session mechanics, closed readiness/coverage "
    "states, source-local golden schedules, and no horizon, fixture, API, "
    "persistence, provider, or web publication"
)
PYTHON
