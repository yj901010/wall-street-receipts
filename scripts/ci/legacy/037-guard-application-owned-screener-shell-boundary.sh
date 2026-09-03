python <<'PYTHON'
import json
import re
from pathlib import Path

def require(condition, message):
    if not condition:
        raise ValueError(message)

fixture_dir = Path("fixtures/v1")
manifest = json.loads((fixture_dir / "manifest.json").read_text(encoding="utf-8"))
screener_contract_markers = (
    "screener",
    "historical_equity_screening",
    "p8_deferred",
    "no_canonical_historical_screening_feature_catalog",
)
canonical_json_paths = tuple(
    sorted(
        (
            path
            for root in (Path("schemas"), fixture_dir)
            for path in root.rglob("*.json")
        ),
        key=lambda path: path.as_posix(),
    )
)
screener_contracts = [
    path
    for path in canonical_json_paths
    if "screener" in path.name.lower()
    or any(
        marker in path.read_text(encoding="utf-8").lower()
        for marker in screener_contract_markers
    )
]
require(
    not screener_contracts,
    f"P2 screener shell must not add JSON/schema evidence: {screener_contracts}",
)
require(
    "screener" not in json.dumps(manifest, sort_keys=True).lower(),
    "P2 screener application state must not become a manifest member",
)

openapi_source = Path("contracts/openapi.yaml").read_text(encoding="utf-8").lower()
require(
    "screener" not in openapi_source,
    "P2 screener shell must not add an OpenAPI path or schema",
)

api_main = Path("apps/api/src/main")
api_sources = tuple(
    sorted(
        (
            path
            for path in api_main.rglob("*")
            if path.is_file() and path.suffix.lower() in {".java", ".sql", ".yml", ".yaml"}
        ),
        key=lambda path: path.as_posix(),
    )
)
for source_path in api_sources:
    require(
        "screener" not in source_path.name.lower()
        and "screener" not in source_path.read_text(encoding="utf-8").lower(),
        f"P2 screener shell must not expand API/Flyway production: {source_path}",
    )

state_path = Path("apps/web/src/lib/screener-shell-state.ts")
route_directory = Path("apps/web/src/app/screener")
header_path = Path("apps/web/src/components/site-header.tsx")
require(state_path.is_file(), f"Missing screener application state: {state_path}")
require(route_directory.is_dir(), f"Missing screener route: {route_directory}")
require(header_path.is_file(), f"Missing screener navigation boundary: {header_path}")

route_production_paths = tuple(
    sorted(
        (
            path
            for path in route_directory.rglob("*")
            if path.is_file()
            and path.suffix in {".ts", ".tsx"}
            and ".test." not in path.name
            and ".spec." not in path.name
        ),
        key=lambda path: path.as_posix(),
    )
)
required_route_paths = {
    "page.tsx",
    "screener-shell.tsx",
    "loading.tsx",
    "error.tsx",
    "not-found.tsx",
}
discovered_route_paths = {
    path.relative_to(route_directory).as_posix()
    for path in route_production_paths
}
require(
    required_route_paths <= discovered_route_paths,
    "Missing required screener production boundaries: "
    f"{sorted(required_route_paths - discovered_route_paths)}",
)

state_source = state_path.read_text(encoding="utf-8")
state_match = re.search(
    r"Object\.freeze\(\{(?P<body>.*?)\}\)",
    state_source,
    flags=re.DOTALL,
)
require(state_match is not None, "Screener state must be an application-owned frozen value")
state_properties = re.findall(
    r'^\s*([A-Za-z][A-Za-z0-9]*):\s*"([^"]+)",?\s*$',
    state_match.group("body"),
    flags=re.MULTILINE,
)
require(
    state_properties
    == [
        ("dataMode", "DEMO"),
        ("scope", "HISTORICAL_EQUITY_SCREENING"),
        ("status", "P8_DEFERRED"),
        (
            "reasonCode",
            "NO_CANONICAL_HISTORICAL_SCREENING_FEATURE_CATALOG",
        ),
        ("missingDisplay", "NA"),
    ],
    f"Unexpected screener application state: {state_properties}",
)
require(
    not re.findall(r'from\s+["\']([^"\']+)["\']', state_source),
    "Screener application state must not import fixture/provider evidence",
)

route_source_by_path = {
    path: path.read_text(encoding="utf-8") for path in route_production_paths
}
route_source = route_source_by_path[route_directory / "page.tsx"]
not_found_source = route_source_by_path[route_directory / "not-found.tsx"]
messages_source = route_source_by_path[route_directory / "messages.ts"]
normalized_route_source = re.sub(r"\s+", "", route_source)
normalized_not_found_source = re.sub(r"\s+", "", not_found_source)
query_rejection_call_sites = (
    "if(!isQueryFreeScreenerRequest(awaitsearchParams))notFound();",
    "if(!isQueryFreeScreenerRequest(awaitsearchParams)){notFound();}",
)
require(
    "Object.keys(searchParams).length === 0" in route_source
    and any(
        call_site in normalized_route_source
        for call_site in query_rejection_call_sites
    ),
    "Screener route must reject every request containing a query key",
)
require(
    "getScreenerMessages(awaitgetLocale())" in normalized_not_found_source
    and "messages.notFound.eyebrow" in not_found_source
    and "messages.notFound.title" in not_found_source
    and "messages.notFound.body" in not_found_source
    and "messages.notFound.calls" in not_found_source
    and "messages.notFound.methodology" in not_found_source,
    "Screener unsupported boundary must resolve only the typed locale catalog",
)
not_found_message_blocks = re.findall(
    r"notFound:\s*\{(?P<body>.*?)\n\s*\},",
    messages_source,
    flags=re.DOTALL,
)
require(
    len(not_found_message_blocks) == 2,
    "Screener catalog must contain exact Korean and English not-found blocks",
)
expected_not_found_keys = {"eyebrow", "title", "body", "calls", "methodology"}
for message_block in not_found_message_blocks:
    message_keys = set(
        re.findall(r"^\s*([a-zA-Z][a-zA-Z0-9]*):", message_block, flags=re.MULTILINE)
    )
    require(
        message_keys == expected_not_found_keys,
        f"Screener not-found catalog shape changed: {sorted(message_keys)}",
    )
require(
    "Unsupported screener request" in messages_source
    and "No query was executed" in messages_source
    and "지원하지 않는 스크리너 요청" in messages_source
    and "쿼리를 실행하지 않았으며" in messages_source
    and "Record<Locale, ScreenerMessages>" in messages_source,
    "Screener catalog must preserve exact ko/en closed-query meaning",
)
forbidden_not_found_tokens = (
    "SiteHeader",
    "SCREENER_SHELL_STATE",
    "dataMode",
    "scope",
    "status",
    "reasonCode",
    "missingDisplay",
    "HISTORICAL_EQUITY_SCREENING",
    "P8_DEFERRED",
    "NO_CANONICAL_HISTORICAL_SCREENING_FEATURE_CATALOG",
)
require(
    not any(token in not_found_source for token in forbidden_not_found_tokens)
    and re.search(r"\b(?:DEMO|NA)\b", not_found_source) is None,
    "Screener unsupported-request boundary must remain mode-neutral and state-free",
)
for message_block in not_found_message_blocks:
    require(
        not any(token in message_block for token in forbidden_not_found_tokens)
        and re.search(r"\b(?:DEMO|NA)\b", message_block) is None,
        "Localized Screener unsupported copy must remain mode-neutral and state-free",
    )

production_sources = {state_path: state_source, **route_source_by_path}
forbidden_import_fragments = (
    ".json",
    "fixtures",
    "/providers",
    "calls-provider",
    "outcome",
    "market-board",
    "market-map",
    "market-treemap",
    "market-snapshot",
    "call-context",
)
for source_path, source in production_sources.items():
    imports = re.findall(r'from\s+["\']([^"\']+)["\']', source)
    for imported in imports:
        require(
            not any(fragment in imported.lower() for fragment in forbidden_import_fragments),
            f"Screener source crosses an evidence boundary: {source_path} -> {imported}",
        )
    compact = source.replace(" ", "").lower()
    require(
        "fetch(" not in compact
        and "axios" not in compact
        and "websocket" not in compact
        and "eventsource" not in compact,
        f"Screener application state must not use provider transport: {source_path}",
    )

header_source = header_path.read_text(encoding="utf-8")
map_position = header_source.find('href="/maps/sp500"')
screener_position = header_source.find('href="/screener"')
methodology_position = header_source.find('href="/methodology"')
require(
    min(map_position, screener_position, methodology_position) >= 0
    and map_position < screener_position < methodology_position,
    "Primary navigation must place Screener after Maps and before Methodology",
)
require(
    header_source.count('href="/screener"') == 1
    and 'current === "screener"' in header_source,
    "Primary navigation must expose one current-aware Screener route",
)

print(
    "Validated the exact application-owned five-key screener state, query-free "
    "route and navigation, source isolation, and no JSON/manifest/API/Flyway expansion"
)
PYTHON
