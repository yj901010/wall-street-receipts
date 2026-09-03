python <<'PYTHON'
import json
import re
from copy import deepcopy
from datetime import datetime
from pathlib import Path

from jsonschema import Draft202012Validator, FormatChecker, ValidationError

fixture_dir = Path("fixtures/v1")
fixture_path = fixture_dir / "market-board.json"
schema_path = Path("schemas/market-board.schema.json")
exact_root_fields = {
    "schemaVersion", "fixtureVersion", "dataMode", "generatedAt", "provenance",
    "scope", "publicationStatus", "publicationReasonCode", "marketAsOf",
    "missingDisplay", "quotes", "disclaimer",
}
exact_provenance_fields = {
    "id", "sourceType", "sourcePaths", "capturedAt", "synthetic", "licenseClass",
}
expected_disclaimer = (
    "Known-unavailable DEMO publication state only; no canonical global quote "
    "catalog or current, latest, delayed, or end-of-day market board is published. "
    "No price, change, session status, freshness, or coverage was observed, derived, "
    "inferred, or promoted from call-event snapshots, treemaps, or application "
    "literals. Not investment advice."
)
expected_document = {
    "schemaVersion": "1.0.0",
    "fixtureVersion": "v1",
    "dataMode": "DEMO",
    "generatedAt": "2026-08-19T02:00:00Z",
    "provenance": {
        "id": "fixture-market-board-v1",
        "sourceType": "LOCAL_SPECIFICATION",
        "sourcePaths": [
            "schemas/market-board.schema.json",
            "quality/P2_ACCEPTANCE.md",
        ],
        "capturedAt": "2026-08-19T02:00:00Z",
        "synthetic": True,
        "licenseClass": "INTERNAL_DEMO",
    },
    "scope": "GLOBAL_MARKET_OVERVIEW",
    "publicationStatus": "NOT_PUBLISHED",
    "publicationReasonCode": "NO_CANONICAL_GLOBAL_QUOTE_CATALOG",
    "marketAsOf": None,
    "missingDisplay": "NA",
    "quotes": [],
    "disclaimer": expected_disclaimer,
}

def load_json(path):
    return json.loads(path.read_text(encoding="utf-8"))

def instant(value):
    return datetime.fromisoformat(value.replace("Z", "+00:00"))

def require(condition, message):
    if not condition:
        raise ValueError(message)

schema = load_json(schema_path)
Draft202012Validator.check_schema(schema)
validator = Draft202012Validator(schema, format_checker=FormatChecker())
require(
    schema.get("$id") == "urn:wall-street-receipts:schema:market-board:1.0.0",
    "Market-board schema ID mismatch",
)
require(schema.get("additionalProperties") is False, "Market-board root must be closed")
require(set(schema.get("required", [])) == exact_root_fields, "Market-board required fields mismatch")
require(set(schema.get("properties", {})) == exact_root_fields, "Market-board schema fields mismatch")
provenance_schema = schema["$defs"]["provenance"]
require(provenance_schema.get("additionalProperties") is False, "Market-board provenance must be closed")
require(set(provenance_schema.get("required", [])) == exact_provenance_fields, "Market-board provenance required fields mismatch")
require(set(provenance_schema.get("properties", {})) == exact_provenance_fields, "Market-board provenance fields mismatch")
require(schema["properties"]["marketAsOf"] == {"type": "null"}, "Market-board v1 must not publish a market as-of")
require(schema["properties"]["quotes"].get("maxItems") == 0, "Market-board v1 must forbid quote rows")
require(schema["properties"]["quotes"].get("items") is False, "Market-board quote item schema must be closed")
require(
    schema["$defs"]["identifier"].get("pattern") == "^[a-z0-9]+(?:-[a-z0-9]+)*$",
    "Market-board identifier grammar mismatch",
)

manifest = load_json(fixture_dir / "manifest.json")

def validate_document(candidate, catalog=manifest, require_exact=True):
    validator.validate(candidate)
    require(set(candidate) == exact_root_fields, "Market-board envelope is not exact")
    require(set(candidate["provenance"]) == exact_provenance_fields, "Market-board provenance is not exact")
    require(candidate["scope"] == "GLOBAL_MARKET_OVERVIEW", "Market-board scope mismatch")
    require(candidate["publicationStatus"] == "NOT_PUBLISHED", "Market-board publication status mismatch")
    require(
        candidate["publicationReasonCode"] == "NO_CANONICAL_GLOBAL_QUOTE_CATALOG",
        "Market-board publication reason mismatch",
    )
    require(candidate["marketAsOf"] is None, "Market-board must not invent a market as-of")
    require(candidate["missingDisplay"] == "NA", "Market-board missing display mismatch")
    require(candidate["quotes"] == [], "Market-board must contain zero quote rows")
    require(candidate["disclaimer"] == expected_disclaimer, "Market-board disclaimer mismatch")

    provenance = candidate["provenance"]
    for source_path in provenance["sourcePaths"]:
        require(Path(source_path).is_file(), f"Missing tracked market-board source: {source_path}")

    captured_at = instant(provenance["capturedAt"])
    generated_at = instant(candidate["generatedAt"])
    manifest_captured_at = instant(catalog["provenance"]["capturedAt"])
    manifest_generated_at = instant(catalog["generatedAt"])
    require(captured_at <= generated_at, "Market-board provenance capture is later than generation")
    require(generated_at <= manifest_captured_at, "Fixture manifest capture predates market-board generation")
    require(manifest_captured_at <= manifest_generated_at, "Fixture manifest capture is later than generation")
    require(
        provenance == expected_document["provenance"],
        "Market-board provenance mismatch",
    )
    if require_exact:
        require(candidate == expected_document, "Market-board fixture evidence changed from the locked projection")

document = load_json(fixture_path)
validate_document(document)

require(manifest["generatedAt"] == "2026-08-19T02:10:00Z", "Fixture manifest generation time mismatch")
require(manifest["provenance"]["capturedAt"] == "2026-08-19T02:00:00Z", "Fixture manifest capture time mismatch")
manifest_paths = [entry["path"] for entry in manifest["files"]]
require(len(manifest_paths) == len(set(manifest_paths)), "Fixture manifest contains a duplicate path")
require(manifest_paths.count("market-board.json") == 1, "Market-board manifest membership mismatch")
require(
    manifest["files"][-1] == {
        "path": "market-board.json",
        "description": "Known-unavailable DEMO global market-board publication state with no quote rows",
    },
    "Market-board must be the final append-only manifest member",
)
actual_fixture_paths = {
    path.name for path in fixture_dir.glob("*.json") if path.name != "manifest.json"
}
require(set(manifest_paths) == actual_fixture_paths, "Fixture manifest path set mismatch")
require(
    {path.name for path in fixture_dir.glob("market-board*.json")} == {"market-board.json"},
    "Unexpected market-board fixture version or duplicate document",
)

schema_negative_cases = []
missing_field = deepcopy(document)
del missing_field["publicationReasonCode"]
schema_negative_cases.append(("missing root field", missing_field))
extra_field = deepcopy(document)
extra_field["lastPrice"] = 0
schema_negative_cases.append(("extra quote fact", extra_field))
missing_provenance = deepcopy(document)
del missing_provenance["provenance"]["capturedAt"]
schema_negative_cases.append(("missing provenance field", missing_provenance))
extra_provenance = deepcopy(document)
extra_provenance["provenance"]["provider"] = "fixture"
schema_negative_cases.append(("extra provenance field", extra_provenance))
published = deepcopy(document)
published["publicationStatus"] = "PUBLISHED"
schema_negative_cases.append(("published status", published))
market_as_of = deepcopy(document)
market_as_of["marketAsOf"] = "2026-08-19T02:00:00Z"
schema_negative_cases.append(("invented market as-of", market_as_of))
quote_row = deepcopy(document)
quote_row["quotes"] = [{"symbol": "SPX", "price": 0}]
schema_negative_cases.append(("quote row", quote_row))
offset_time = deepcopy(document)
offset_time["generatedAt"] = "2026-08-19T11:00:00+09:00"
schema_negative_cases.append(("offset timestamp", offset_time))
fine_time = deepcopy(document)
fine_time["provenance"]["capturedAt"] = "2026-08-19T02:00:00.0000001Z"
schema_negative_cases.append(("finer-than-microsecond timestamp", fine_time))
for label, candidate in schema_negative_cases:
    try:
        validator.validate(candidate)
    except ValidationError:
        continue
    raise SystemExit(f"Market-board schema accepted {label}")

semantic_negative_cases = []
capture_after_generation = deepcopy(document)
capture_after_generation["provenance"]["capturedAt"] = "2026-08-19T02:00:01Z"
semantic_negative_cases.append((
    "capture after generation",
    capture_after_generation,
    manifest,
    "provenance capture is later",
))
after_catalog_capture = deepcopy(document)
after_catalog_capture["generatedAt"] = "2026-08-19T02:00:01Z"
semantic_negative_cases.append((
    "fixture after manifest capture",
    after_catalog_capture,
    manifest,
    "manifest capture predates",
))
changed_disclaimer = deepcopy(document)
changed_disclaimer["disclaimer"] = "Not investment advice."
semantic_negative_cases.append((
    "weakened disclaimer",
    changed_disclaimer,
    manifest,
    "disclaimer mismatch",
))
changed_provenance = deepcopy(document)
changed_provenance["provenance"]["id"] = "fixture-market-board-v2"
semantic_negative_cases.append((
    "divergent provenance",
    changed_provenance,
    manifest,
    "provenance mismatch",
))
for label, candidate, catalog, expected_error in semantic_negative_cases:
    try:
        validate_document(candidate, catalog=catalog, require_exact=False)
    except ValueError as error:
        if expected_error not in str(error):
            raise SystemExit(
                f"Market-board semantic gate rejected {label} for the wrong invariant: {error}"
            ) from error
        continue
    raise SystemExit(f"Market-board semantic gate accepted {label}")

port_path = Path("apps/web/src/lib/providers/market-board-provider.ts")
adapter_path = Path("apps/web/src/lib/providers/fixture-market-board-provider.ts")
dashboard_path = Path("apps/web/src/lib/providers/fixture-market-provider.ts")
for source_path in (port_path, adapter_path, dashboard_path):
    require(source_path.is_file(), f"Missing market-board provider boundary: {source_path}")

port_source = port_path.read_text(encoding="utf-8")
adapter_source = adapter_path.read_text(encoding="utf-8")
dashboard_source = dashboard_path.read_text(encoding="utf-8")
require("MarketBoardProvider" in port_source, "Market-board port is missing its provider interface")
json_imports = re.findall(r'''from\s+["']([^"']+\.json)["']''', adapter_source)
require(
    len(json_imports) == 1 and json_imports[0].endswith("fixtures/v1/market-board.json"),
    "Market-board adapter must import only the canonical market-board fixture",
)
require("MarketBoardProvider" in dashboard_source, "Dashboard must inject the market-board provider")
require(".json" not in dashboard_source, "Dashboard composer must not import raw fixture JSON")
forbidden_board_sources = (
    "analyst-calls", "call-contexts", "market-snapshots", "market-map",
    "market-treemap", "latestQuote", "MarketDataProvider",
)
for fragment in forbidden_board_sources:
    require(
        fragment not in adapter_source,
        f"Market-board adapter crosses a forbidden evidence boundary: {fragment}",
    )

openapi_source = Path("contracts/openapi.yaml").read_text(encoding="utf-8")
require("/v1/market" not in openapi_source, "P2 market-board must not add an API path")

print(
    "Validated closed known-unavailable DEMO market-board state, zero quotes, "
    "point-in-time catalog bounds, provider isolation, and API defer"
)
PYTHON
