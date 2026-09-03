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
offset_files = {
    "SessionOffsetPolicyVersion.java", "TradingSession.java",
    "TradingSessionCatalog.java", "SessionOffsetRequest.java",
    "SessionOffsetResolution.java", "SessionOffsetResolver.java",
}
relation_files = {
    "EventSessionRelationPolicyVersion.java",
    "EventSessionRelationRequest.java",
    "EventSessionRelation.java",
    "EventSessionRelationClassifier.java",
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
    actual_production_files == offset_files | relation_files | close_policy_files,
    f"Unexpected additive horizon files: {sorted(actual_production_files)}",
)
require(
    actual_test_files == expected_test_files,
    f"Unexpected horizon golden files: {sorted(actual_test_files)}",
)
all_sources = {
    path.relative_to(horizon_dir).as_posix(): path.read_text(encoding="utf-8")
    for path in production_paths
}
sources = {name: all_sources[name] for name in relation_files}
policy_source = sources["EventSessionRelationPolicyVersion.java"]
request_source = sources["EventSessionRelationRequest.java"]
result_source = sources["EventSessionRelation.java"]
classifier_source = sources["EventSessionRelationClassifier.java"]

require(
    re.search(
        r"enum\s+EventSessionRelationPolicyVersion\s*\{\s*"
        r"EXPLICIT_SESSION_BOUNDARY_RELATION_V1\s*\}",
        policy_source,
    ) is not None,
    "Event/session relation policy enum must contain exactly v1",
)
compact_request = re.sub(r"\s+", "", request_source)
compact_result = re.sub(r"\s+", "", result_source)
require(
    "publicrecordEventSessionRelationRequest("
    "EventSessionRelationPolicyVersionpolicyVersion,InstanteventTime,"
    "TradingSessionCatalogcatalog)" in compact_request,
    "EventSessionRelationRequest must expose only policyVersion/eventTime/catalog",
)
require(
    "recordRelationContext(EventSessionRelationPolicyVersionpolicyVersion,"
    "StringcalendarId,StringcatalogRevision,InstanteventTime)" in compact_result,
    "EventSessionRelation must preserve the exact relation context",
)
expected_records = (
    "recordEmptyCatalog(RelationContextcontext)",
    "recordBeforeCatalog(RelationContextcontext,TradingSessionfirstSession)",
    "recordAtOpen(RelationContextcontext,TradingSessionsession)",
    "recordInsideSession(RelationContextcontext,TradingSessionsession)",
    "recordAtClose(RelationContextcontext,TradingSessionsession)",
    "recordAtTouchingBoundary(RelationContextcontext,"
    "TradingSessionclosingSession,TradingSessionopeningSession)",
    "recordBetweenSessions(RelationContextcontext,"
    "TradingSessionpreviousSession,TradingSessionnextSession)",
    "recordAfterCatalog(RelationContextcontext,TradingSessionlastSession)",
)
for record_shape in expected_records:
    require(
        record_shape in compact_result,
        f"Missing exact event/session relation record: {record_shape}",
    )
permitted_match = re.search(
    r"permits\s+(?P<body>.*?)\{", result_source, flags=re.DOTALL
)
require(permitted_match is not None, "EventSessionRelation must be sealed")
permitted = {
    name.rsplit(".", 1)[-1]
    for name in re.findall(
        r"EventSessionRelation\.(\w+)", permitted_match.group("body")
    )
}
require(
    permitted
    == {
        "EmptyCatalog", "BeforeCatalog", "AtOpen", "InsideSession",
        "AtClose", "AtTouchingBoundary", "BetweenSessions", "AfterCatalog",
    },
    f"Unexpected event/session relation variants: {sorted(permitted)}",
)

allowed_internal_imports = {
    "com.wallstreetreceipts.api.domain.PersistentInstant",
}
allowed_internal_prefix = "com.wallstreetreceipts.api.domain.outcome.horizon."
forbidden_code = re.compile(
    r"\b(?:AnalystCall|AnalystCallRevision|CallOutcome|OutcomeHorizon|"
    r"SessionOffset\w*|TargetHit\w*|Clock|LocalDate|LocalDateTime|"
    r"OffsetDateTime|ZonedDateTime|ZoneId|ZoneOffset|Locale|TimeZone|"
    r"DayOfWeek|Month|Period|Duration|ChronoUnit|TemporalAdjuster|"
    r"Provider|Repository|ObjectMapper|JdbcTemplate|Random|double|float|"
    r"anchor\w*|sessionCount|evaluationAsOf|asOf|processingTime|"
    r"observation\w*|price\w*|capturedAt|provenance\w*|sourceReferenceId)\b",
    flags=re.IGNORECASE,
)
forbidden_calendar_calls = (
    ".plusDays(", ".plusWeeks(", ".plusMonths(", ".plusYears(",
    ".atZone(", "systemDefault(", "Calendar.getInstance(",
)
for source_name, source in sources.items():
    code = without_comments(source)
    imports = re.findall(r"^import\s+([^;]+);", code, flags=re.MULTILINE)
    require(
        all(
            imported.startswith("java.")
            or imported in allowed_internal_imports
            or imported.startswith(allowed_internal_prefix)
            for imported in imports
        ),
        f"Event/session source crosses the pure boundary: {source_name} {imports}",
    )
    require(
        "org.springframework" not in code
        and forbidden_code.search(code) is None
        and not any(marker in code for marker in forbidden_calendar_calls)
        and "@Component" not in code
        and "@Service" not in code,
        f"Event/session source contains anchor inference/runtime wiring: {source_name}",
    )

for marker in (
    "new EmptyCatalog", "new BeforeCatalog", "new AtOpen",
    "new InsideSession", "new AtClose", "new AtTouchingBoundary",
    "new BetweenSessions", "new AfterCatalog",
):
    require(marker in classifier_source, f"Classifier does not emit {marker}")
touching_return = classifier_source.find("return new AtTouchingBoundary")
close_return = classifier_source.find("return new AtClose")
require(
    touching_return >= 0
    and close_return >= 0
    and touching_return < close_return
    and "followingSession.opensAt()"
    in classifier_source[max(0, touching_return - 700):touching_return],
    "Classifier must resolve an adjacent shared close/open as touching "
    "before falling back to AtClose",
)
require(
    "request.eventTime()" in classifier_source
    and "catalog.orderedSessions()" in classifier_source
    and "isBefore" in classifier_source
    and "equals" in classifier_source,
    "Classifier must compare only eventTime with explicit session intervals",
)

api_main_dir = Path("apps/api/src/main/java")
relation_type_markers = tuple(
    path.removesuffix(".java") for path in relation_files
)
relation_source_paths = {
    (horizon_dir / file_name).resolve() for file_name in relation_files
} | {
    (horizon_dir / file_name).resolve() for file_name in close_policy_files
}
for other_path in api_main_dir.rglob("*.java"):
    if other_path.resolve() in relation_source_paths:
        continue
    other_source = other_path.read_text(encoding="utf-8")
    require(
        not any(marker in other_source for marker in relation_type_markers),
        f"Event/session relation must not be wired into product runtime: {other_path}",
    )

golden_path = test_dir / "EventSessionRelationClassifierGoldenTest.java"
golden_source = golden_path.read_text(encoding="utf-8")
compact_golden = re.sub(r"\s+", "", golden_source)
for marker in (
    "EXPLICIT_SESSION_BOUNDARY_RELATION_V1", "EmptyCatalog",
    "BeforeCatalog", "AtOpen", "InsideSession", "AtClose",
    "AtTouchingBoundary", "BetweenSessions", "AfterCatalog",
):
    require(marker in golden_source, f"Missing event/session golden: {marker}")
require(
    golden_source.count("@MethodSource") >= 3
    and "one microsecond" in golden_source.lower()
    and "Locale.setDefault" in golden_source
    and "TimeZone.setDefault" in golden_source
    and "originalLocale" in golden_source
    and "originalTimeZone" in golden_source
    and "finally" in golden_source,
    "Event/session temporal and default-environment goldens are incomplete",
)
boundary_request_markers = {
    "before first open":
        'EventSessionRelationRequestbeforeRequest=request('
        '"2026-03-06T14:29:59.999999Z",single);',
    "exact open":
        'EventSessionRelationRequestopenRequest=request('
        '"2026-03-06T14:30:00Z",single);',
    "one microsecond after open":
        'EventSessionRelationRequestopenPlusMicroRequest=request('
        '"2026-03-06T14:30:00.000001Z",single);',
    "strict interior":
        'EventSessionRelationRequestinsideRequest=request('
        '"2026-03-06T18:00:00Z",single);',
    "one microsecond before close":
        'EventSessionRelationRequestcloseMinusMicroRequest=request('
        '"2026-03-06T20:59:59.999999Z",single);',
    "exact close":
        'EventSessionRelationRequestcloseRequest=request('
        '"2026-03-06T21:00:00Z",single);',
    "one microsecond after close gap":
        'EventSessionRelationRequestclosePlusMicroGapRequest=request('
        '"2026-03-06T21:00:00.000001Z",fridayMonday);',
    "exact next open":
        'EventSessionRelationRequestnextOpenRequest=request('
        '"2026-03-09T13:30:00Z",fridayMonday);',
    "one microsecond after catalog":
        'EventSessionRelationRequestafterRequest=request('
        '"2026-03-06T21:00:00.000001Z",single);',
    "touching minus one microsecond":
        'EventSessionRelationRequesttouchingMinusMicroRequest=request('
        '"2026-03-11T13:59:59.999999Z",touching);',
    "exact touching boundary":
        'EventSessionRelationRequesttouchingRequest=request('
        '"2026-03-11T14:00:00Z",touching);',
    "touching plus one microsecond":
        'EventSessionRelationRequesttouchingPlusMicroRequest=request('
        '"2026-03-11T14:00:00.000001Z",touching);',
    "explicit Saturday":
        'EventSessionRelationRequestsaturdayRequest=request('
        '"2026-03-07T15:30:00Z",withSaturday);',
    "explicit early close":
        'EventSessionRelationRequestearlyCloseRequest=request('
        '"2026-03-10T17:00:00Z",earlyClose);',
}
for scenario, marker in boundary_request_markers.items():
    require(marker in compact_golden, f"Missing relation request vector: {scenario}")
boundary_result_markers = {
    "before first open":
        "newBeforeCatalog(context(beforeRequest),FRIDAY)",
    "exact open": "newAtOpen(context(openRequest),FRIDAY)",
    "one microsecond after open":
        "newInsideSession(context(openPlusMicroRequest),FRIDAY)",
    "strict interior":
        "newInsideSession(context(insideRequest),FRIDAY)",
    "one microsecond before close":
        "newInsideSession(context(closeMinusMicroRequest),FRIDAY)",
    "exact close": "newAtClose(context(closeRequest),FRIDAY)",
    "one microsecond after close gap":
        "newBetweenSessions(context(closePlusMicroGapRequest),FRIDAY,DST_MONDAY)",
    "exact next open":
        "newAtOpen(context(nextOpenRequest),DST_MONDAY)",
    "one microsecond after catalog":
        "newAfterCatalog(context(afterRequest),FRIDAY)",
    "touching minus one microsecond":
        "newInsideSession(context(touchingMinusMicroRequest),TOUCHING_FIRST)",
    "exact touching boundary":
        "newAtTouchingBoundary(context(touchingRequest),"
        "TOUCHING_FIRST,TOUCHING_SECOND)",
    "touching plus one microsecond":
        "newInsideSession(context(touchingPlusMicroRequest),TOUCHING_SECOND)",
    "explicit Saturday":
        "newInsideSession(context(saturdayRequest),EXPLICIT_SATURDAY)",
    "explicit early close":
        "newAtClose(context(earlyCloseRequest),EARLY_CLOSE_TUESDAY)",
}
for scenario, marker in boundary_result_markers.items():
    require(marker in compact_golden, f"Missing relation expected result: {scenario}")
require(
    "publicRelationRecordsRejectContradictoryTemporalClaims"
    in golden_source,
    "Event/session goldens must preserve public record-constructor invariants",
)
direct_record_negative_markers = (
    "newBeforeCatalog(atOpen,FRIDAY)",
    "newAtOpen(inside,FRIDAY)",
    "newInsideSession(atOpen,FRIDAY)",
    "newAtClose(inside,FRIDAY)",
    "newAtTouchingBoundary(touching,FRIDAY,TOUCHING_SECOND)",
    "newBetweenSessions(atClose,FRIDAY,DST_MONDAY)",
    "newAfterCatalog(atClose,FRIDAY)",
)
require(
    all(marker in compact_golden for marker in direct_record_negative_markers),
    "Event/session goldens must reject locally contradictory direct records",
)
require(
    re.search(
        r'sub-microsecond event time.*?2026-03-06T14:30:00\.000000001Z'
        r'.*?eventTime must not exceed microsecond precision',
        golden_source,
        flags=re.DOTALL,
    ) is not None,
    "Event/session goldens must reject finer-than-microsecond eventTime",
)
require(
    "2026-03-07" in golden_source
    and "2026-03-09" in golden_source
    and "AtTouchingBoundary" in compact_golden,
    "Event/session goldens must cover explicit Saturday/gap/touching schedules",
)
require(
    "ObjectMapper" not in golden_source
    and "ClassPathResource" not in golden_source,
    "Event/session goldens must remain source-local Java schedules",
)
for resource_path in Path("apps/api/src/test/resources").rglob("*.json"):
    resource_source = resource_path.read_text(encoding="utf-8")
    require(
        "EventSessionRelation" not in resource_source
        and "eventSessionRelation" not in resource_source,
        f"Event/session relation must not add a JSON golden: {resource_path}",
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
    "Event/session relation must not add a schema",
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
    "Event/session relation must not add a calendar fixture",
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
    "Event/session relation must not change manifest membership/order",
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
    and all(item["status"] == "MODEL_ONLY" for item in outcome_document["methodologies"])
    and len(outcome_document["outcomes"]) == 4
    and all(
        item[field] is None
        for item in outcome_document["outcomes"]
        for field in metric_fields
    ),
    "Event/session relation must not activate methodology/outcome facts",
)
openapi_source = Path("contracts/openapi.yaml").read_text(encoding="utf-8")
require(
    set(re.findall(r"^  (/[^\n]+):\s*$", openapi_source, re.MULTILINE))
    == {
        "/v1/calls", "/v1/calls/{id}", "/v1/calls/{id}/revisions",
        "/v1/calls/{id}/outcomes", "/v1/calls/{id}/context",
    },
    "Event/session relation must not change OpenAPI paths",
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
    "Event/session relation must not add a Flyway migration",
)
web_markers = relation_type_markers + (
    "AtTouchingBoundary", "BetweenSessions", "InsideSession",
)
for web_path in Path("apps/web/src").rglob("*"):
    if web_path.is_file() and web_path.suffix in {".ts", ".tsx", ".js", ".jsx"}:
        web_source = web_path.read_text(encoding="utf-8")
        require(
            not any(marker in web_source for marker in web_markers),
            f"Event/session relation must not expand web: {web_path}",
        )

print(
    "Validated eight policy-neutral event/session relations, exact boundaries and "
    "touching/gap preservation, source-local goldens, and no anchor, calendar, "
    "fixture, API, persistence, provider, or web publication"
)
PYTHON
