python <<'PYTHON'
import hashlib
import json
import math
from collections import defaultdict
from copy import deepcopy
from datetime import datetime
from pathlib import Path

from jsonschema import Draft202012Validator, FormatChecker, ValidationError

fixture_dir = Path("fixtures/v1")
schema_path = Path("schemas/market-treemap.schema.json")
legacy_hashes = {
    Path("schemas/market-map.schema.json"): "0e6e3b6dfd924b4f800079c2cf6053bc7d3314564855af4374063e99d4b7b0ac",
    fixture_dir / "market-map.json": "58d7645230af11a7b784af8cecbd6e97d821d4ea0530c4003118cdb239ad2d90",
    fixture_dir / "market-map-nasdaq100.json": "823e95976251382bd4762ebae234eec59eeef378fb3332392df28e6b5ba5ec38",
}
expected_paths = [
    "market-treemap-sp500.json",
    "market-treemap-nasdaq100.json",
]
exact_top_fields = {
    "schemaVersion", "fixtureVersion", "dataMode", "generatedAt", "provenance",
    "universe", "mode", "asOf", "metric", "geometry", "coverage", "cells",
    "disclaimer",
}
exact_provenance_fields = {
    "id", "sourceType", "sourcePaths", "capturedAt", "synthetic", "licenseClass",
}
exact_metric_fields = {
    "name", "unit", "scaleMinimum", "scaleMaximum", "missingDisplay",
}
exact_geometry_fields = {
    "type", "groupBy", "unclassifiedDisplay", "areaField", "areaUnit",
}
exact_coverage_fields = {"kind", "completeUniverse", "cellCount", "weightBasis"}
exact_cell_fields = {
    "assetId", "ticker", "sector", "industry", "syntheticMarketCapProxy",
    "priceChangePercent", "timestamp", "dataMode", "provenanceId",
}
expected_metric = {
    "name": "priceChangePercent",
    "unit": "percent",
    "scaleMinimum": -5.0,
    "scaleMaximum": 5.0,
    "missingDisplay": "NA",
}
expected_geometry = {
    "type": "NESTED_TREEMAP",
    "groupBy": ["sector", "industry"],
    "unclassifiedDisplay": "Unclassified",
    "areaField": "syntheticMarketCapProxy",
    "areaUnit": "relative",
}
expected_coverage = {
    "kind": "SAMPLE",
    "completeUniverse": False,
    "cellCount": 3,
    "weightBasis": "SYNTHETIC_MARKET_CAP_PROXY",
}

def expected_cells(provenance_id):
    return [
        {
            "assetId": "asset-nvda",
            "ticker": "NVDA",
            "sector": "Technology",
            "industry": "Semiconductors",
            "syntheticMarketCapProxy": 144,
            "priceChangePercent": 1.25,
            "timestamp": "2026-08-19T00:30:00Z",
            "dataMode": "DEMO",
            "provenanceId": provenance_id,
        },
        {
            "assetId": "asset-msft",
            "ticker": "MSFT",
            "sector": "Technology",
            "industry": "Software",
            "syntheticMarketCapProxy": 121,
            "priceChangePercent": -0.75,
            "timestamp": "2026-08-19T00:30:00Z",
            "dataMode": "DEMO",
            "provenanceId": provenance_id,
        },
        {
            "assetId": "asset-aapl",
            "ticker": "AAPL",
            "sector": "Technology",
            "industry": "Consumer Electronics",
            "syntheticMarketCapProxy": 100,
            "priceChangePercent": None,
            "timestamp": "2026-08-19T00:30:00Z",
            "dataMode": "DEMO",
            "provenanceId": provenance_id,
        },
    ]

expected_documents = {
    "market-treemap-sp500.json": {
        "schemaVersion": "1.0.0",
        "fixtureVersion": "v1",
        "dataMode": "DEMO",
        "generatedAt": "2026-08-19T01:00:00Z",
        "provenance": {
            "id": "fixture-market-treemap-sp500-v1",
            "sourceType": "LOCAL_SPECIFICATION",
            "sourcePaths": [
                "schemas/market-treemap.schema.json",
                "quality/P2_ACCEPTANCE.md",
            ],
            "capturedAt": "2026-08-19T01:00:00Z",
            "synthetic": True,
            "licenseClass": "INTERNAL_DEMO",
        },
        "universe": "sp500",
        "mode": "PRICE_CHANGE",
        "asOf": "2026-08-19T00:30:00Z",
        "metric": expected_metric,
        "geometry": expected_geometry,
        "coverage": expected_coverage,
        "cells": expected_cells("fixture-market-treemap-sp500-v1"),
        "disclaimer": (
            "Illustrative three-cell S&P 500 DEMO SAMPLE only; it does not assert "
            "official index membership, composition, sector or industry classification, "
            "market capitalization, or observed price change. Every grouping label, "
            "market-cap proxy, and non-null price-change value is synthetic; null "
            "remains NA."
        ),
    },
    "market-treemap-nasdaq100.json": {
        "schemaVersion": "1.0.0",
        "fixtureVersion": "v1",
        "dataMode": "DEMO",
        "generatedAt": "2026-08-19T01:00:00Z",
        "provenance": {
            "id": "fixture-market-treemap-nasdaq100-v1",
            "sourceType": "LOCAL_SPECIFICATION",
            "sourcePaths": [
                "schemas/market-treemap.schema.json",
                "quality/P2_ACCEPTANCE.md",
            ],
            "capturedAt": "2026-08-19T01:00:00Z",
            "synthetic": True,
            "licenseClass": "INTERNAL_DEMO",
        },
        "universe": "nasdaq100",
        "mode": "PRICE_CHANGE",
        "asOf": "2026-08-19T00:30:00Z",
        "metric": expected_metric,
        "geometry": expected_geometry,
        "coverage": expected_coverage,
        "cells": expected_cells("fixture-market-treemap-nasdaq100-v1"),
        "disclaimer": (
            "Illustrative three-cell Nasdaq 100 DEMO SAMPLE only; it does not assert "
            "official index membership, composition, sector or industry classification, "
            "market capitalization, or observed price change. Every grouping label, "
            "market-cap proxy, and non-null price-change value is synthetic; null "
            "remains NA."
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

def classification_label(value, unclassified_display):
    return unclassified_display if value is None else value

def canonical_cell_order(document):
    unclassified = document["geometry"]["unclassifiedDisplay"]
    sector_totals = defaultdict(int)
    industry_totals = defaultdict(int)
    for cell in document["cells"]:
        sector = classification_label(cell["sector"], unclassified)
        industry = classification_label(cell["industry"], unclassified)
        proxy = cell["syntheticMarketCapProxy"]
        sector_totals[sector] += proxy
        industry_totals[(sector, industry)] += proxy
    return sorted(
        document["cells"],
        key=lambda cell: (
            -sector_totals[classification_label(cell["sector"], unclassified)],
            classification_label(cell["sector"], unclassified),
            -industry_totals[(
                classification_label(cell["sector"], unclassified),
                classification_label(cell["industry"], unclassified),
            )],
            classification_label(cell["industry"], unclassified),
            -cell["syntheticMarketCapProxy"],
            cell["assetId"],
        ),
    )

schema = load_json(schema_path)
for legacy_path, expected_hash in legacy_hashes.items():
    actual_hash = hashlib.sha256(legacy_path.read_bytes()).hexdigest()
    require(actual_hash == expected_hash, f"Legacy market-map contract changed: {legacy_path}")
Draft202012Validator.check_schema(schema)
require(schema.get("$schema") == "https://json-schema.org/draft/2020-12/schema", "Treemap schema must use Draft 2020-12")
require(schema.get("$id") == "urn:wall-street-receipts:schema:market-treemap:1.0.0", "Treemap schema ID mismatch")
require(schema.get("additionalProperties") is False, "Treemap document must be closed")
require(set(schema.get("required", [])) == exact_top_fields, "Treemap required fields are not exact")
require(set(schema.get("properties", {})) == exact_top_fields, "Treemap properties are not exact")
closed_definitions = {
    "provenance": exact_provenance_fields,
    "metricDefinition": exact_metric_fields,
    "geometry": exact_geometry_fields,
    "coverage": exact_coverage_fields,
    "cell": exact_cell_fields,
}
for definition_name, exact_fields in closed_definitions.items():
    definition = schema["$defs"][definition_name]
    require(definition.get("additionalProperties") is False, f"{definition_name} must be closed")
    require(set(definition.get("required", [])) == exact_fields, f"{definition_name} required fields are not exact")
    require(set(definition.get("properties", {})) == exact_fields, f"{definition_name} properties are not exact")
require(schema["properties"]["mode"].get("const") == "PRICE_CHANGE", "Treemap mode must remain PRICE_CHANGE")
require(schema["properties"]["cells"].get("maxItems") == 1000, "Treemap cell-count safety bound mismatch")
proxy_schema = schema["$defs"]["cell"]["properties"]["syntheticMarketCapProxy"]
require(proxy_schema == {"type": "integer", "minimum": 1, "maximum": 1000000000000}, "Synthetic proxy integer bounds mismatch")
change_schema = schema["$defs"]["cell"]["properties"]["priceChangePercent"]["oneOf"][0]
require(change_schema.get("minimum") == -100 and change_schema.get("maximum") == 1000000, "Raw price-change safety bounds must be independent of palette stops")

validator = Draft202012Validator(schema, format_checker=FormatChecker())
master_document = load_json(fixture_dir / "master-data.json")
expected_master_ids = {"asset-spx", "asset-nvda", "asset-msft", "asset-aapl"}
require({asset["assetId"] for asset in master_document["assets"]} == expected_master_ids, "Treemap slice must not expand master assets")
assets_by_id = {asset["assetId"]: asset for asset in master_document["assets"]}

actual_paths = {path.name for path in fixture_dir.glob("market-treemap*.json")}
require(actual_paths == set(expected_paths), f"Unexpected treemap fixture set: {sorted(actual_paths)}")
documents = {path: load_json(fixture_dir / path) for path in expected_paths}

def validate_catalog(candidate_documents, require_exact=True):
    require(list(candidate_documents) == expected_paths, "Treemap provider order must remain sp500 then nasdaq100")
    natural_keys = set()
    provenance_ids = set()
    for path, document in candidate_documents.items():
        validator.validate(document)
        require(set(document) == exact_top_fields, f"Treemap envelope is not exact: {path}")
        require(set(document["provenance"]) == exact_provenance_fields, f"Treemap provenance is not exact: {path}")
        require(set(document["metric"]) == exact_metric_fields, f"Treemap metric is not exact: {path}")
        require(set(document["geometry"]) == exact_geometry_fields, f"Treemap geometry is not exact: {path}")
        require(set(document["coverage"]) == exact_coverage_fields, f"Treemap coverage is not exact: {path}")
        require(document["metric"] == expected_metric, f"Treemap metric mismatch: {path}")
        require(document["geometry"] == expected_geometry, f"Treemap geometry mismatch: {path}")
        require(document["coverage"] == expected_coverage, f"Treemap coverage mismatch: {path}")
        require(document["coverage"]["cellCount"] == len(document["cells"]), f"Treemap cellCount mismatch: {path}")
        require(document["coverage"]["completeUniverse"] is False, f"Complete-universe claim is forbidden: {path}")

        provenance = document["provenance"]
        require(provenance["id"] not in provenance_ids, f"Duplicate treemap provenance ID: {provenance['id']}")
        provenance_ids.add(provenance["id"])
        for source_path in provenance["sourcePaths"]:
            require(Path(source_path).is_file(), f"Missing tracked treemap source path: {source_path}")

        as_of = instant(document["asOf"])
        captured_at = instant(provenance["capturedAt"])
        generated_at = instant(document["generatedAt"])
        require(as_of <= captured_at <= generated_at, f"Treemap time bounds are invalid: {path}")
        natural_key = (document["universe"], document["mode"], document["asOf"])
        require(natural_key not in natural_keys, f"Duplicate treemap natural identity: {natural_key}")
        natural_keys.add(natural_key)

        cell_asset_ids = set()
        cell_tickers = set()
        total_proxy = 0
        for cell in document["cells"]:
            require(set(cell) == exact_cell_fields, f"Treemap cell is not exact: {cell.get('assetId')}")
            require(cell["assetId"] not in cell_asset_ids, f"Duplicate treemap asset: {cell['assetId']}")
            require(cell["ticker"] not in cell_tickers, f"Duplicate treemap ticker: {cell['ticker']}")
            cell_asset_ids.add(cell["assetId"])
            cell_tickers.add(cell["ticker"])
            master_asset = assets_by_id.get(cell["assetId"])
            require(master_asset is not None, f"Unknown treemap asset: {cell['assetId']}")
            require(master_asset["assetType"] == "EQUITY", f"Treemap cell must resolve to an equity: {cell['assetId']}")
            require(master_asset["ticker"] == cell["ticker"], f"Treemap asset/ticker mismatch: {cell['assetId']}")
            require(cell["dataMode"] == document["dataMode"] == "DEMO", f"Treemap data mode mismatch: {cell['assetId']}")
            require(cell["provenanceId"] == provenance["id"], f"Treemap provenance mismatch: {cell['assetId']}")
            require(instant(cell["timestamp"]) <= as_of, f"Treemap cell is later than asOf: {cell['assetId']}")
            proxy = cell["syntheticMarketCapProxy"]
            require(type(proxy) is int and 1 <= proxy <= 1000000000000, f"Unsafe synthetic proxy: {cell['assetId']}")
            total_proxy += proxy
            change = cell["priceChangePercent"]
            require(change is None or (isinstance(change, (int, float)) and not isinstance(change, bool) and math.isfinite(change) and change >= -100), f"Invalid raw price change: {cell['assetId']}")
            require(cell["sector"] != "Unclassified" and cell["industry"] != "Unclassified", f"Reserved classification label used: {cell['assetId']}")
            require(cell["sector"] is not None or cell["industry"] is None, f"Industry cannot be classified under a null sector: {cell['assetId']}")
        require(total_proxy <= 1000 * 1000000000000 < 2 ** 53, f"Treemap proxy sum is not JavaScript-safe: {path}")
        require(document["cells"] == canonical_cell_order(document), f"Treemap hierarchy order is not deterministic: {path}")

        sectors = {cell["sector"] for cell in document["cells"]}
        industries = {cell["industry"] for cell in document["cells"]}
        require(sectors == {"Technology"}, f"P2 DEMO fixture must remain an explicit single-sector sample: {path}")
        require(industries == {"Semiconductors", "Software", "Consumer Electronics"}, f"Treemap industry projection mismatch: {path}")

    sp500_cells = {cell["assetId"]: cell for cell in candidate_documents[expected_paths[0]]["cells"]}
    nasdaq_cells = {cell["assetId"]: cell for cell in candidate_documents[expected_paths[1]]["cells"]}
    require(set(sp500_cells) == set(nasdaq_cells), "Equal-asOf DEMO treemaps must share the locked sample identities")
    consistency_fields = {
        "assetId", "ticker", "sector", "industry", "syntheticMarketCapProxy",
        "priceChangePercent", "timestamp", "dataMode",
    }
    for asset_id in sp500_cells:
        sp500_projection = {field: sp500_cells[asset_id][field] for field in consistency_fields}
        nasdaq_projection = {field: nasdaq_cells[asset_id][field] for field in consistency_fields}
        require(sp500_projection == nasdaq_projection, f"Cross-universe shared-cell divergence: {asset_id}")
    require(sp500_cells["asset-aapl"]["priceChangePercent"] is None, "AAPL missing raw change must remain null")

    legacy_documents = [
        load_json(fixture_dir / "market-map.json"),
        load_json(fixture_dir / "market-map-nasdaq100.json"),
    ]
    all_documents = legacy_documents + list(candidate_documents.values())
    all_provenance_ids = [document["provenance"]["id"] for document in all_documents]
    require(len(all_provenance_ids) == len(set(all_provenance_ids)), "Map and treemap provenance IDs must be globally unique")
    all_natural_keys = [
        (document["universe"], document["mode"], document["asOf"])
        for document in all_documents
    ]
    require(len(all_natural_keys) == len(set(all_natural_keys)), "Map and treemap natural identities must be globally unique")
    if require_exact:
        require(candidate_documents == expected_documents, "Treemap fixture evidence changed from the locked projection")

validate_catalog(documents)

out_of_scale = deepcopy(documents[expected_paths[0]])
out_of_scale["cells"][0]["priceChangePercent"] = -7.25
validator.validate(out_of_scale)
large_positive = deepcopy(documents[expected_paths[0]])
large_positive["cells"][0]["priceChangePercent"] = 250.0
validator.validate(large_positive)
nullable_classification = deepcopy(documents[expected_paths[0]])
nullable_classification["cells"][2]["sector"] = None
nullable_classification["cells"][2]["industry"] = None
validator.validate(nullable_classification)

manifest = load_json(fixture_dir / "manifest.json")
require(manifest["generatedAt"] == "2026-08-19T02:10:00Z", "Fixture manifest generation time mismatch")
require(manifest["provenance"]["capturedAt"] == "2026-08-19T02:00:00Z", "Fixture manifest capture time mismatch")
manifest_generated_at = instant(manifest["generatedAt"])
manifest_captured_at = instant(manifest["provenance"]["capturedAt"])
require(manifest_captured_at <= manifest_generated_at, "Fixture manifest capture is later than generation")
require(all(instant(document["generatedAt"]) <= manifest_generated_at for document in documents.values()), "Treemap fixture is later than its manifest")
manifest_paths = [entry["path"] for entry in manifest["files"]]
require(len(manifest_paths) == len(set(manifest_paths)), "Fixture manifest contains a duplicate path")
actual_fixture_paths = {
    path.name for path in fixture_dir.glob("*.json") if path.name != "manifest.json"
}
require(set(manifest_paths) == actual_fixture_paths, "Fixture manifest path set mismatch")
for member_path in manifest_paths:
    member = load_json(fixture_dir / member_path)
    require(
        instant(member["generatedAt"]) <= manifest_captured_at,
        f"Fixture manifest predates declared member generation: {member_path}",
    )
for expected_path in expected_paths:
    require(manifest_paths.count(expected_path) == 1, f"Treemap manifest path must occur exactly once: {expected_path}")
legacy_nasdaq_index = manifest_paths.index("market-map-nasdaq100.json")
sp500_index = manifest_paths.index("market-treemap-sp500.json")
nasdaq_index = manifest_paths.index("market-treemap-nasdaq100.json")
require(sp500_index == legacy_nasdaq_index + 1 and nasdaq_index == sp500_index + 1, "Treemap fixtures must append after the legacy map documents")
expected_manifest_entries = [
    {
        "path": "market-treemap-sp500.json",
        "description": "Limited S&P 500 PRICE_CHANGE nested-treemap DEMO SAMPLE data",
    },
    {
        "path": "market-treemap-nasdaq100.json",
        "description": "Limited Nasdaq 100 PRICE_CHANGE nested-treemap DEMO SAMPLE data",
    },
]
require(manifest["files"][sp500_index:nasdaq_index + 1] == expected_manifest_entries, "Treemap manifest evidence mismatch")

schema_negative_cases = []
extra_field = deepcopy(documents[expected_paths[0]])
extra_field["officialMarketCap"] = True
schema_negative_cases.append(("unexpected official field", extra_field))
wrong_mode = deepcopy(documents[expected_paths[0]])
wrong_mode["mode"] = "ANALYST_CONSENSUS"
schema_negative_cases.append(("legacy read-model mode", wrong_mode))
wrong_grouping = deepcopy(documents[expected_paths[0]])
wrong_grouping["geometry"]["groupBy"] = ["industry", "sector"]
schema_negative_cases.append(("reversed hierarchy", wrong_grouping))
zero_proxy = deepcopy(documents[expected_paths[0]])
zero_proxy["cells"][0]["syntheticMarketCapProxy"] = 0
schema_negative_cases.append(("zero proxy", zero_proxy))
fractional_proxy = deepcopy(documents[expected_paths[0]])
fractional_proxy["cells"][0]["syntheticMarketCapProxy"] = 1.5
schema_negative_cases.append(("fractional proxy", fractional_proxy))
overflow_proxy = deepcopy(documents[expected_paths[0]])
overflow_proxy["cells"][0]["syntheticMarketCapProxy"] = 1000000000001
schema_negative_cases.append(("proxy overflow", overflow_proxy))
impossible_loss = deepcopy(documents[expected_paths[0]])
impossible_loss["cells"][0]["priceChangePercent"] = -100.01
schema_negative_cases.append(("price change below -100 percent", impossible_loss))
overflow_change = deepcopy(documents[expected_paths[0]])
overflow_change["cells"][0]["priceChangePercent"] = 1000000.01
schema_negative_cases.append(("non-finite-risk price change overflow", overflow_change))
orphan_industry = deepcopy(documents[expected_paths[0]])
orphan_industry["cells"][0]["sector"] = None
schema_negative_cases.append(("classified industry under null sector", orphan_industry))
reserved_label = deepcopy(documents[expected_paths[0]])
reserved_label["cells"][0]["industry"] = "Unclassified"
schema_negative_cases.append(("reserved Unclassified literal", reserved_label))
too_many_cells = deepcopy(documents[expected_paths[0]])
too_many_cells["cells"] = [deepcopy(too_many_cells["cells"][0]) for _ in range(1001)]
schema_negative_cases.append(("more than 1000 cells", too_many_cells))
offset_time = deepcopy(documents[expected_paths[0]])
offset_time["asOf"] = "2026-08-19T09:30:00+09:00"
schema_negative_cases.append(("non-canonical UTC instant", offset_time))
for label, candidate in schema_negative_cases:
    try:
        validator.validate(candidate)
    except ValidationError:
        continue
    raise SystemExit(f"Treemap schema accepted {label}")

semantic_negative_cases = []
null_to_zero = deepcopy(documents)
null_to_zero[expected_paths[0]]["cells"][2]["priceChangePercent"] = 0
null_to_zero[expected_paths[1]]["cells"][2]["priceChangePercent"] = 0
semantic_negative_cases.append(("missing raw change rewritten as zero", null_to_zero, "AAPL missing raw change"))
bad_count = deepcopy(documents)
bad_count[expected_paths[0]]["coverage"]["cellCount"] = 2
semantic_negative_cases.append(("coverage count mismatch", bad_count, "Treemap coverage mismatch"))
bad_provenance = deepcopy(documents)
bad_provenance[expected_paths[0]]["cells"][0]["provenanceId"] = "fixture-other"
semantic_negative_cases.append(("cell provenance mismatch", bad_provenance, "Treemap provenance mismatch"))
unknown_asset = deepcopy(documents)
unknown_asset[expected_paths[0]]["cells"][0]["assetId"] = "asset-unknown"
semantic_negative_cases.append(("unknown master asset", unknown_asset, "Unknown treemap asset"))
future_cell = deepcopy(documents)
future_cell[expected_paths[0]]["cells"][0]["timestamp"] = "2026-08-19T00:30:01Z"
semantic_negative_cases.append(("cell later than asOf", future_cell, "Treemap cell is later than asOf"))
duplicate_asset = deepcopy(documents)
duplicate_asset[expected_paths[0]]["cells"][1]["assetId"] = "asset-nvda"
semantic_negative_cases.append(("duplicate asset", duplicate_asset, "Duplicate treemap asset"))
reordered = deepcopy(documents)
reordered[expected_paths[0]]["cells"][0], reordered[expected_paths[0]]["cells"][1] = reordered[expected_paths[0]]["cells"][1], reordered[expected_paths[0]]["cells"][0]
semantic_negative_cases.append(("non-canonical hierarchy order", reordered, "Treemap hierarchy order"))
cross_universe_divergence = deepcopy(documents)
cross_universe_divergence[expected_paths[1]]["cells"][0]["priceChangePercent"] = 1.5
semantic_negative_cases.append(("cross-universe shared-cell divergence", cross_universe_divergence, "Cross-universe shared-cell divergence"))
for label, candidate, expected_error in semantic_negative_cases:
    try:
        validate_catalog(candidate, require_exact=False)
    except ValueError as error:
        if expected_error not in str(error):
            raise SystemExit(
                f"Treemap semantic gate rejected {label} for the wrong invariant: {error}"
            ) from error
        continue
    raise SystemExit(f"Treemap semantic gate accepted {label}")

print(
    "Validated 2 closed PRICE_CHANGE treemap fixtures with 3 shared DEMO cells, "
    "1 explicit sector, 3 industries, raw percent preservation, and synthetic geometry"
)
PYTHON
