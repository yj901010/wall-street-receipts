python <<'PYTHON'
import json
from copy import deepcopy
from datetime import datetime
from pathlib import Path

from jsonschema import Draft202012Validator, FormatChecker, ValidationError

fixture_dir = Path("fixtures/v1")
schema_path = Path("schemas/market-map.schema.json")
expected_paths = ["market-map.json", "market-map-nasdaq100.json"]
exact_top_fields = {
    "schemaVersion", "fixtureVersion", "dataMode", "generatedAt", "provenance",
    "universe", "mode", "asOf", "metric", "coverage", "cells", "disclaimer",
}
exact_provenance_fields = {
    "id", "sourceType", "sourcePaths", "capturedAt", "synthetic", "licenseClass",
}
exact_metric_fields = {"name", "unit", "minimum", "maximum", "missingDisplay"}
exact_coverage_fields = {"kind", "completeUniverse", "cellCount", "weightBasis"}
exact_cell_fields = {
    "assetId", "ticker", "sector", "weight", "metric", "callCount",
    "timestamp", "dataMode", "provenanceId",
}
expected_metric = {
    "name": "analystConsensus",
    "unit": "score",
    "minimum": -1.0,
    "maximum": 1.0,
    "missingDisplay": "NA",
}
expected_documents = {
    "market-map.json": {
        "schemaVersion": "1.0.0",
        "fixtureVersion": "v1",
        "dataMode": "DEMO",
        "generatedAt": "2026-08-19T00:00:00Z",
        "provenance": {
            "id": "fixture-market-map-v1",
            "sourceType": "LOCAL_SPECIFICATION",
            "sourcePaths": [
                "schemas/market-map.schema.json",
                "quality/P2_ACCEPTANCE.md",
            ],
            "capturedAt": "2026-08-19T00:00:00Z",
            "synthetic": True,
            "licenseClass": "INTERNAL_DEMO",
        },
        "universe": "sp500",
        "mode": "ANALYST_CONSENSUS",
        "asOf": "2026-08-12T06:00:00Z",
        "metric": expected_metric,
        "coverage": {
            "kind": "SAMPLE",
            "completeUniverse": False,
            "cellCount": 3,
            "weightBasis": "SYNTHETIC_RELATIVE",
        },
        "cells": [
            {
                "assetId": "asset-nvda",
                "ticker": "NVDA",
                "sector": "Technology",
                "weight": 8.1,
                "metric": 0.82,
                "callCount": 18,
                "timestamp": "2026-08-12T06:00:00Z",
                "dataMode": "DEMO",
                "provenanceId": "fixture-market-map-v1",
            },
            {
                "assetId": "asset-msft",
                "ticker": "MSFT",
                "sector": "Technology",
                "weight": 7.0,
                "metric": 0.71,
                "callCount": 14,
                "timestamp": "2026-08-12T06:00:00Z",
                "dataMode": "DEMO",
                "provenanceId": "fixture-market-map-v1",
            },
            {
                "assetId": "asset-aapl",
                "ticker": "AAPL",
                "sector": "Technology",
                "weight": 6.5,
                "metric": None,
                "callCount": 0,
                "timestamp": "2026-08-12T06:00:00Z",
                "dataMode": "DEMO",
                "provenanceId": "fixture-market-map-v1",
            },
        ],
        "disclaimer": (
            "Limited three-cell DEMO SAMPLE only; it is not the full S&P 500 "
            "composition. Sector labels, weights, analyst-consensus metrics, and call "
            "counts are synthetic fixture values. Weights are not official index "
            "weights, and no metric or call count was observed or derived from canonical "
            "calls. A null metric must render as NA, not zero or bearish."
        ),
    },
    "market-map-nasdaq100.json": {
        "schemaVersion": "1.0.0",
        "fixtureVersion": "v1",
        "dataMode": "DEMO",
        "generatedAt": "2026-08-19T00:00:00Z",
        "provenance": {
            "id": "fixture-market-map-nasdaq100-v1",
            "sourceType": "LOCAL_SPECIFICATION",
            "sourcePaths": [
                "schemas/market-map.schema.json",
                "quality/P2_ACCEPTANCE.md",
            ],
            "capturedAt": "2026-08-19T00:00:00Z",
            "synthetic": True,
            "licenseClass": "INTERNAL_DEMO",
        },
        "universe": "nasdaq100",
        "mode": "ANALYST_CONSENSUS",
        "asOf": "2026-08-19T00:00:00Z",
        "metric": expected_metric,
        "coverage": {
            "kind": "SAMPLE",
            "completeUniverse": False,
            "cellCount": 0,
            "weightBasis": "SYNTHETIC_RELATIVE",
        },
        "cells": [],
        "disclaimer": (
            "Known-empty Nasdaq 100 DEMO SAMPLE only; no full-index composition, "
            "official weight, analyst-consensus metric, or call count was observed, "
            "derived, or inferred."
        ),
    },
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
require(schema.get("$schema") == "https://json-schema.org/draft/2020-12/schema", "Market-map schema must use Draft 2020-12")
require(schema.get("$id") == "urn:wall-street-receipts:schema:market-map:1.0.0", "Market-map schema ID mismatch")
require(schema.get("additionalProperties") is False, "Market-map document must be closed")
require(set(schema.get("required", [])) == exact_top_fields, "Market-map required fields are not exact")
require(set(schema.get("properties", {})) == exact_top_fields, "Market-map properties are not exact")

closed_definitions = {
    "provenance": exact_provenance_fields,
    "metricDefinition": exact_metric_fields,
    "coverage": exact_coverage_fields,
    "cell": exact_cell_fields,
}
for definition_name, exact_fields in closed_definitions.items():
    definition = schema["$defs"][definition_name]
    require(definition.get("additionalProperties") is False, f"{definition_name} must be closed")
    require(set(definition.get("required", [])) == exact_fields, f"{definition_name} required fields are not exact")
    require(set(definition.get("properties", {})) == exact_fields, f"{definition_name} properties are not exact")
require(schema["properties"]["mode"].get("const") == "ANALYST_CONSENSUS", "P2 schema must not pre-contract P7 modes")
require(schema["$defs"]["coverage"]["properties"]["kind"].get("const") == "SAMPLE", "Coverage kind must remain SAMPLE")
require(schema["$defs"]["coverage"]["properties"]["completeUniverse"].get("const") is False, "P2 maps must not claim complete universes")
require(schema["$defs"]["coverage"]["properties"]["weightBasis"].get("const") == "SYNTHETIC_RELATIVE", "Weight basis must remain synthetic-relative")

validator = Draft202012Validator(schema, format_checker=FormatChecker())
master_document = load_json(fixture_dir / "master-data.json")
assets_by_id = {asset["assetId"]: asset for asset in master_document["assets"]}
require(len(assets_by_id) == len(master_document["assets"]), "Duplicate master asset ID")
master_tickers = [asset["ticker"] for asset in master_document["assets"]]
require(len(set(master_tickers)) == len(master_tickers), "Duplicate master asset ticker")

actual_map_paths = {path.name for path in fixture_dir.glob("market-map*.json")}
require(actual_map_paths == set(expected_paths), f"Unexpected market-map fixture set: {sorted(actual_map_paths)}")
documents = {path: load_json(fixture_dir / path) for path in expected_paths}

def validate_catalog(candidate_documents, require_exact=True):
    require(list(candidate_documents) == expected_paths, "Market-map provider order must remain sp500 then nasdaq100")
    natural_keys = set()
    provenance_ids = set()
    for path, document in candidate_documents.items():
        validator.validate(document)
        require(set(document) == exact_top_fields, f"Market-map envelope is not exact: {path}")
        require(set(document["provenance"]) == exact_provenance_fields, f"Market-map provenance is not exact: {path}")
        require(set(document["metric"]) == exact_metric_fields, f"Market-map metric is not exact: {path}")
        require(set(document["coverage"]) == exact_coverage_fields, f"Market-map coverage is not exact: {path}")
        require(document["metric"] == expected_metric, f"Metric definition mismatch: {path}")
        require(document["coverage"]["cellCount"] == len(document["cells"]), f"Coverage cellCount mismatch: {path}")
        require(document["coverage"]["kind"] == "SAMPLE", f"Coverage kind mismatch: {path}")
        require(document["coverage"]["completeUniverse"] is False, f"Complete-universe claim is forbidden: {path}")
        require(document["coverage"]["weightBasis"] == "SYNTHETIC_RELATIVE", f"Weight basis mismatch: {path}")

        provenance = document["provenance"]
        require(provenance["id"] not in provenance_ids, f"Duplicate map provenance ID: {provenance['id']}")
        provenance_ids.add(provenance["id"])
        for source_path in provenance["sourcePaths"]:
            require(Path(source_path).is_file(), f"Untracked or missing map source path: {source_path}")

        as_of = instant(document["asOf"])
        captured_at = instant(provenance["capturedAt"])
        generated_at = instant(document["generatedAt"])
        require(as_of <= captured_at <= generated_at, f"Map time bounds are invalid: {path}")
        natural_key = (document["universe"], document["mode"], document["asOf"])
        require(natural_key not in natural_keys, f"Duplicate market-map natural identity: {natural_key}")
        natural_keys.add(natural_key)

        cell_asset_ids = set()
        cell_tickers = set()
        for cell in document["cells"]:
            require(set(cell) == exact_cell_fields, f"Market-map cell is not exact: {cell.get('assetId')}")
            require(cell["assetId"] not in cell_asset_ids, f"Duplicate asset in universe: {cell['assetId']}")
            require(cell["ticker"] not in cell_tickers, f"Duplicate ticker in universe: {cell['ticker']}")
            cell_asset_ids.add(cell["assetId"])
            cell_tickers.add(cell["ticker"])
            master_asset = assets_by_id.get(cell["assetId"])
            require(master_asset is not None, f"Unknown market-map asset: {cell['assetId']}")
            require(master_asset["ticker"] == cell["ticker"], f"Market-map asset/ticker mismatch: {cell['assetId']}")
            require(cell["dataMode"] == document["dataMode"] == "DEMO", f"Market-map mode mismatch: {cell['assetId']}")
            require(cell["provenanceId"] == provenance["id"], f"Market-map provenance mismatch: {cell['assetId']}")
            require(instant(cell["timestamp"]) <= as_of, f"Market-map cell is later than asOf: {cell['assetId']}")
            require(cell["weight"] > 0, f"Market-map weight must be positive: {cell['assetId']}")
            require(cell["callCount"] >= 0, f"Market-map call count must be non-negative: {cell['assetId']}")
            if cell["metric"] is not None:
                require(expected_metric["minimum"] <= cell["metric"] <= expected_metric["maximum"], f"Market-map metric is out of range: {cell['assetId']}")

        expected_order = sorted(document["cells"], key=lambda cell: (-cell["weight"], cell["assetId"]))
        require(document["cells"] == expected_order, f"Market-map cells are not deterministically ordered: {path}")

    require(candidate_documents["market-map-nasdaq100.json"]["cells"] == [], "Nasdaq fixture must remain explicitly known-empty")
    require(candidate_documents["market-map.json"]["cells"][2]["metric"] is None, "AAPL missing metric must remain null")
    if require_exact:
        require(candidate_documents == expected_documents, "Market-map fixture evidence changed from the locked projection")

validate_catalog(documents)

manifest = load_json(fixture_dir / "manifest.json")
require(manifest["generatedAt"] == "2026-08-19T02:10:00Z", "Fixture manifest generation time mismatch")
require(manifest["provenance"]["capturedAt"] == "2026-08-19T02:00:00Z", "Fixture manifest capture time mismatch")
manifest_generated_at = instant(manifest["generatedAt"])
require(all(instant(document["generatedAt"]) <= manifest_generated_at for document in documents.values()), "Market-map fixture is later than its manifest")
manifest_paths = [entry["path"] for entry in manifest["files"]]
sp500_index = manifest_paths.index("market-map.json")
nasdaq_index = manifest_paths.index("market-map-nasdaq100.json")
require(nasdaq_index == sp500_index + 1, "Nasdaq fixture must be appended immediately after the existing market-map entry")
expected_manifest_entries = [
    {
        "path": "market-map.json",
        "description": "Limited S&P 500 analyst-consensus DEMO SAMPLE map shell data",
    },
    {
        "path": "market-map-nasdaq100.json",
        "description": "Known-empty Nasdaq 100 analyst-consensus DEMO SAMPLE map shell data",
    },
]
require(manifest["files"][sp500_index:nasdaq_index + 1] == expected_manifest_entries, "Market-map manifest evidence mismatch")

schema_negative_cases = []
extra_field = deepcopy(documents["market-map.json"])
extra_field["officialIndexWeight"] = True
schema_negative_cases.append(("unexpected field", extra_field))
future_mode = deepcopy(documents["market-map.json"])
future_mode["mode"] = "PRICE_CHANGE"
schema_negative_cases.append(("mode owned by the separate treemap read model", future_mode))
zero_weight = deepcopy(documents["market-map.json"])
zero_weight["cells"][0]["weight"] = 0
schema_negative_cases.append(("zero weight", zero_weight))
out_of_range = deepcopy(documents["market-map.json"])
out_of_range["cells"][0]["metric"] = 1.01
schema_negative_cases.append(("out-of-range metric", out_of_range))
offset_time = deepcopy(documents["market-map.json"])
offset_time["asOf"] = "2026-08-12T15:00:00+09:00"
schema_negative_cases.append(("non-canonical UTC instant", offset_time))
for label, candidate in schema_negative_cases:
    try:
        validator.validate(candidate)
    except ValidationError:
        continue
    raise SystemExit(f"Market-map schema accepted {label}")

semantic_negative_cases = []
null_to_zero = deepcopy(documents)
null_to_zero["market-map.json"]["cells"][2]["metric"] = 0
semantic_negative_cases.append(("missing metric rewritten as zero", null_to_zero))
bad_count = deepcopy(documents)
bad_count["market-map.json"]["coverage"]["cellCount"] = 2
semantic_negative_cases.append(("coverage count mismatch", bad_count))
bad_provenance = deepcopy(documents)
bad_provenance["market-map.json"]["cells"][0]["provenanceId"] = "fixture-other"
semantic_negative_cases.append(("cell provenance mismatch", bad_provenance))
unknown_asset = deepcopy(documents)
unknown_asset["market-map.json"]["cells"][0]["assetId"] = "asset-unknown"
semantic_negative_cases.append(("unknown master asset", unknown_asset))
future_cell = deepcopy(documents)
future_cell["market-map.json"]["cells"][0]["timestamp"] = "2026-08-12T06:00:01Z"
semantic_negative_cases.append(("cell later than asOf", future_cell))
invented_nasdaq = deepcopy(documents)
invented_nasdaq["market-map-nasdaq100.json"]["cells"] = [deepcopy(documents["market-map.json"]["cells"][0])]
invented_nasdaq["market-map-nasdaq100.json"]["coverage"]["cellCount"] = 1
invented_nasdaq["market-map-nasdaq100.json"]["cells"][0]["provenanceId"] = "fixture-market-map-nasdaq100-v1"
invented_nasdaq["market-map-nasdaq100.json"]["cells"][0]["timestamp"] = "2026-08-19T00:00:00Z"
semantic_negative_cases.append(("invented Nasdaq cell", invented_nasdaq))
for label, candidate in semantic_negative_cases:
    try:
        validate_catalog(candidate, require_exact=False)
    except (ValidationError, ValueError):
        continue
    raise SystemExit(f"Market-map semantic gate accepted {label}")

print("Validated 2 closed DEMO market-map shells: 3-cell S&P SAMPLE and known-empty Nasdaq SAMPLE")
PYTHON
