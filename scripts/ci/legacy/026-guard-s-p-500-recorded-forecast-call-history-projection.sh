python <<'PYTHON'
import json
from datetime import datetime
from pathlib import Path

fixture_dir = Path("fixtures/v1")
master = json.loads((fixture_dir / "master-data.json").read_text(encoding="utf-8"))
calls_document = json.loads(
    (fixture_dir / "analyst-calls.json").read_text(encoding="utf-8")
)
manifest = json.loads((fixture_dir / "manifest.json").read_text(encoding="utf-8"))

def require(condition, message):
    if not condition:
        raise ValueError(message)

def instant(value):
    return datetime.fromisoformat(value.replace("Z", "+00:00"))

expected_asset = {
    "assetId": "asset-spx",
    "assetType": "INDEX",
    "canonicalName": "S&P 500 Index",
    "ticker": "SPX",
    "primaryCurrency": "USD",
    "active": True,
    "dataMode": "DEMO",
    "effectiveAt": "2026-08-10T00:00:00Z",
    "capturedAt": "2026-08-18T00:00:00Z",
    "provenanceId": "fixture-master-data-v1",
}
assets_by_id = {asset["assetId"]: asset for asset in master["assets"]}
require(assets_by_id.get("asset-spx") == expected_asset, "Exact SPX master identity")

spx_calls = sorted(
    (
        call
        for call in calls_document["calls"]
        if call["assetId"] == "asset-spx"
    ),
    key=lambda call: call["callId"],
)
spx_calls.sort(key=lambda call: instant(call["eventTime"]), reverse=True)
require(
    [call["callId"] for call in spx_calls] == ["demo-call-001"],
    "Current SPX forecast-call projection",
)
current_call = spx_calls[0]
require(
    {
        "assetId": current_call["assetId"],
        "eventTime": current_call["eventTime"],
        "direction": current_call["direction"],
        "originalRating": current_call["originalRating"],
        "previousTarget": current_call["previousTarget"],
        "target": current_call["target"],
        "currency": current_call["currency"],
        "sourceReferenceId": current_call["sourceReferenceId"],
        "status": current_call["status"],
        "dataMode": current_call["dataMode"],
    }
    == {
        "assetId": "asset-spx",
        "eventTime": "2026-08-10T12:00:00Z",
        "direction": "BULLISH",
        "originalRating": "DEMO Bullish",
        "previousTarget": 7800.0,
        "target": 8000.0,
        "currency": "USD",
        "sourceReferenceId": "source-ref-demo-001",
        "status": "ACTIVE",
        "dataMode": "DEMO",
    },
    "Current SPX forecast-call evidence",
)

references = {
    reference["sourceReferenceId"]: reference
    for reference in calls_document["sourceReferences"]
}
documents = {
    document["sourceDocumentId"]: document
    for document in calls_document["sourceDocuments"]
}
reference = references.get(current_call["sourceReferenceId"])
require(reference is not None, "SPX call source reference")
document = documents.get(reference["sourceDocumentId"])
require(document is not None, "SPX call source document")
require(
    current_call["dataMode"]
    == reference["dataMode"]
    == document["dataMode"]
    == calls_document["dataMode"]
    == "DEMO",
    "SPX call evidence mode parity",
)
require(
    reference["provenanceId"]
    == document["provenanceId"]
    == current_call["provenanceId"]
    == calls_document["provenance"]["id"],
    "SPX call evidence provenance parity",
)
require(
    instant(current_call["eventTime"])
    <= instant(current_call["processingTime"])
    <= instant(current_call["capturedAt"])
    <= instant(calls_document["generatedAt"]),
    "SPX call chronology",
)

manifest_paths = [entry["path"] for entry in manifest["files"]]
require(manifest_paths.count("master-data.json") == 1, "Master manifest membership")
require(manifest_paths.count("analyst-calls.json") == 1, "Calls manifest membership")
require(len(manifest_paths) == len(set(manifest_paths)), "Duplicate manifest path")
require(
    instant(calls_document["generatedAt"]) <= instant(manifest["generatedAt"]),
    "Calls fixture is later than its manifest",
)

duplicate_history_contracts = [
    *Path("schemas").glob("*sp500*history*.json"),
    *Path("schemas").glob("*forecast*history*.json"),
    *fixture_dir.glob("*sp500*history*.json"),
    *fixture_dir.glob("*forecast*history*.json"),
]
require(
    not duplicate_history_contracts,
    "S&P history slice must not add a duplicate canonical schema or fixture",
)

port_path = Path("apps/web/src/lib/providers/sp500-history-provider.ts")
adapter_path = Path("apps/web/src/lib/providers/fixture-sp500-history-provider.ts")
route_path = Path("apps/web/src/app/markets/sp500/page.tsx")
route_directory = route_path.parent
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
    "sp500-call-history.tsx",
    "keyboard-scroll-region.tsx",
    "loading.tsx",
    "error.tsx",
}
discovered_route_paths = {
    path.relative_to(route_directory).as_posix()
    for path in route_production_paths
}
require(
    required_route_paths <= discovered_route_paths,
    "Missing required S&P history production boundaries: "
    f"{sorted(required_route_paths - discovered_route_paths)}",
)
market_view_path = Path("apps/web/src/app/market/market-board.tsx")
header_path = Path("apps/web/src/components/site-header.tsx")
provider_index_path = Path("apps/web/src/lib/providers/index.ts")
for source_path in (
    port_path,
    adapter_path,
    *route_production_paths,
    market_view_path,
    header_path,
    provider_index_path,
):
    require(source_path.is_file(), f"Missing S&P history boundary: {source_path}")

port_source = port_path.read_text(encoding="utf-8")
adapter_source = adapter_path.read_text(encoding="utf-8")
route_source = route_path.read_text(encoding="utf-8")
route_production_source = "\n".join(
    path.read_text(encoding="utf-8") for path in route_production_paths
)
market_view_source = market_view_path.read_text(encoding="utf-8")
header_source = header_path.read_text(encoding="utf-8")
provider_index_source = provider_index_path.read_text(encoding="utf-8")

for token in (
    "Sp500HistoryProvider", "Sp500HistorySnapshot", "dataMode", "asOf",
    "source", "disclaimer", "asset", "items", "page",
):
    require(token in port_source, f"S&P history port is missing {token}")
for token in (
    "CallsProvider", "metadata()", "asset-spx", "page: 0", "size: 25",
    'sort: "eventTime"', 'order: "desc"',
):
    require(token in adapter_source, f"S&P history adapter is missing {token}")

forbidden_boundary_fragments = (
    ".json", "fixtures/v1", "market-snapshots", "call-outcomes",
    "call-contexts", "analyst-call-revisions", "market-board",
    "market-map", "market-treemap", "latestQuote", "MarketDataProvider",
    "MarketSnapshot", "CallContext", "EventContext", "CallOutcome",
    "demo-call-001", "JPMorgan", "Demo Analyst A", "DEMO index outlook",
    '"S&P 500 Index"', '"INDEX"', '"SPX"', "7758.42", "7800", "8000",
)
boundary_source = "\n".join((port_source, adapter_source, route_production_source))
for fragment in forbidden_boundary_fragments:
    require(
        fragment not in boundary_source,
        f"S&P history production boundary duplicates or crosses evidence: {fragment}",
    )
require(
    ".findById(" not in adapter_source
    and ".findContextByCallId(" not in adapter_source,
    "S&P history adapter must not reach through call detail or context",
)
require(
    "export function sp500HistoryProvider()" in provider_index_source
    and "return new FixtureSp500HistoryProvider(createCallsProvider());"
    in provider_index_source,
    "S&P history factory must compose only the calls provider",
)
require(
    "sp500HistoryProvider" in route_source,
    "S&P history route must resolve the dedicated provider",
)
require(
    "/markets/sp500" in market_view_source,
    "Market route must expose the S&P recorded-history link",
)
require(
    "/markets/sp500" not in header_source,
    "S&P history must not add a primary navigation item",
)

openapi_source = Path("contracts/openapi.yaml").read_text(encoding="utf-8")
require(
    "/v1/markets/sp500" not in openapi_source
    and "/v1/sp500" not in openapi_source,
    "P2 S&P call history must not add an API path",
)

print(
    "Validated the exact one-row DEMO SPX call projection, injected provider "
    "boundary, route discovery, and no schema/fixture/API expansion"
)
PYTHON
