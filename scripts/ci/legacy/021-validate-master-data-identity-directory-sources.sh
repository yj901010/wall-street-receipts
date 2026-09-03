python <<'PYTHON'
import json
import re
from copy import deepcopy
from datetime import datetime
from pathlib import Path

fixture_dir = Path("fixtures/v1")
master_path = fixture_dir / "master-data.json"
master = json.loads(master_path.read_text(encoding="utf-8"))
manifest = json.loads((fixture_dir / "manifest.json").read_text(encoding="utf-8"))

envelope_fields = {
    "schemaVersion", "fixtureVersion", "dataMode", "generatedAt",
    "provenance", "institutions", "analysts", "analystEmployments", "assets",
}
provenance_fields = {
    "id", "sourceType", "sourcePaths", "capturedAt", "synthetic", "licenseClass",
}
institution_fields = {
    "institutionId", "canonicalName", "slug", "country", "active", "dataMode",
    "effectiveAt", "capturedAt", "provenanceId",
}
analyst_fields = {
    "analystId", "canonicalName", "active", "dataMode", "effectiveAt",
    "capturedAt", "provenanceId",
}
expected_source_paths = [
    "docs/fixtures/institutions.json",
    "docs/docs/DOMAIN_MODEL.md",
]
expected_institutions = {
    "inst-jpm": {
        "institutionId": "inst-jpm",
        "canonicalName": "JPMorgan",
        "slug": "jpmorgan",
        "country": "US",
        "active": True,
        "dataMode": "DEMO",
        "effectiveAt": "2026-08-10T00:00:00Z",
        "capturedAt": "2026-08-18T00:00:00Z",
        "provenanceId": "fixture-master-data-v1",
    },
    "inst-gs": {
        "institutionId": "inst-gs",
        "canonicalName": "Goldman Sachs",
        "slug": "goldman-sachs",
        "country": "US",
        "active": True,
        "dataMode": "DEMO",
        "effectiveAt": "2026-08-10T00:00:00Z",
        "capturedAt": "2026-08-18T00:00:00Z",
        "provenanceId": "fixture-master-data-v1",
    },
}
expected_analysts = {
    "analyst-demo-a": {
        "analystId": "analyst-demo-a",
        "canonicalName": "Demo Analyst A",
        "active": True,
        "dataMode": "DEMO",
        "effectiveAt": "2026-08-10T00:00:00Z",
        "capturedAt": "2026-08-18T00:00:00Z",
        "provenanceId": "fixture-master-data-v1",
    },
    "analyst-demo-b": {
        "analystId": "analyst-demo-b",
        "canonicalName": "Demo Analyst B",
        "active": True,
        "dataMode": "DEMO",
        "effectiveAt": "2026-08-10T00:00:00Z",
        "capturedAt": "2026-08-18T00:00:00Z",
        "provenanceId": "fixture-master-data-v1",
    },
}
utc_pattern = re.compile(
    r"^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}(?:\.\d{1,6})?Z$"
)
identifier_pattern = re.compile(r"^[a-z0-9]+(?:-[a-z0-9]+)*$")
slug_pattern = re.compile(r"^[a-z0-9]+(?:-[a-z0-9]+)*$")

def require(condition, message):
    if not condition:
        raise ValueError(message)

def instant(value, owner):
    require(isinstance(value, str) and utc_pattern.fullmatch(value), f"{owner} UTC instant")
    try:
        parsed = datetime.fromisoformat(value.replace("Z", "+00:00"))
    except ValueError as error:
        raise ValueError(f"{owner} UTC instant") from error
    require(parsed.isoformat().endswith("+00:00"), f"{owner} UTC instant")
    return parsed

def code_point_key(value):
    return tuple(ord(character) for character in value)

def validate(document, require_exact):
    require(set(document) == envelope_fields, "Master envelope fields")
    require(document["schemaVersion"] == "1.0.0", "Master schema version")
    require(document["fixtureVersion"] == "v1", "Master fixture version")
    require(document["dataMode"] == "DEMO", "Master data mode")
    for collection in ("institutions", "analysts", "analystEmployments", "assets"):
        require(isinstance(document[collection], list), f"Master {collection} collection")

    provenance = document["provenance"]
    require(isinstance(provenance, dict) and set(provenance) == provenance_fields,
            "Master provenance fields")
    require(provenance["id"] == "fixture-master-data-v1", "Master provenance ID")
    require(provenance["sourceType"] == "LOCAL_SPECIFICATION", "Master source type")
    require(provenance["sourcePaths"] == expected_source_paths, "Master source paths")
    require(provenance["synthetic"] is True, "Master synthetic classification")
    require(provenance["licenseClass"] == "INTERNAL_DEMO", "Master license class")

    generated_at = instant(document["generatedAt"], "Master generatedAt")
    provenance_captured_at = instant(provenance["capturedAt"], "Master provenance capturedAt")
    require(provenance_captured_at <= generated_at, "Master provenance chronology")

    ids = set()
    slugs = set()
    names = set()
    for record in document["institutions"]:
        require(isinstance(record, dict) and set(record) == institution_fields,
                "Institution fields")
        institution_id = record["institutionId"]
        canonical_name = record["canonicalName"]
        slug = record["slug"]
        require(isinstance(institution_id, str) and len(institution_id) <= 128
                and identifier_pattern.fullmatch(institution_id),
                "Institution identifier")
        require(isinstance(canonical_name, str) and canonical_name.strip() == canonical_name
                and canonical_name, "Institution canonical name")
        require(isinstance(slug, str) and len(slug) <= 128
                and slug_pattern.fullmatch(slug), "Institution slug")
        require(isinstance(record["country"], str)
                and re.fullmatch(r"[A-Z]{2}", record["country"]), "Institution country")
        require(type(record["active"]) is bool, "Institution active state")
        require(record["dataMode"] == document["dataMode"], "Institution data mode")
        require(record["provenanceId"] == provenance["id"], "Institution provenance")
        require(institution_id not in ids, "Duplicate institution ID")
        require(slug not in slugs, "Duplicate institution slug")
        require(canonical_name not in names, "Duplicate institution name")
        ids.add(institution_id)
        slugs.add(slug)
        names.add(canonical_name)

        effective_at = instant(record["effectiveAt"], f"Institution {institution_id} effectiveAt")
        captured_at = instant(record["capturedAt"], f"Institution {institution_id} capturedAt")
        require(effective_at <= captured_at <= provenance_captured_at,
                "Institution chronology")

    analyst_ids = set()
    for record in document["analysts"]:
        require(isinstance(record, dict) and set(record) == analyst_fields,
                "Analyst fields")
        analyst_id = record["analystId"]
        canonical_name = record["canonicalName"]
        require(isinstance(analyst_id, str) and len(analyst_id) <= 128
                and identifier_pattern.fullmatch(analyst_id),
                "Analyst identifier")
        require(isinstance(canonical_name, str)
                and canonical_name.strip() == canonical_name
                and canonical_name, "Analyst canonical name")
        require(type(record["active"]) is bool, "Analyst active state")
        require(record["dataMode"] == document["dataMode"], "Analyst data mode")
        require(record["provenanceId"] == provenance["id"], "Analyst provenance")
        require(analyst_id not in analyst_ids, "Duplicate analyst ID")
        analyst_ids.add(analyst_id)

        effective_at = instant(record["effectiveAt"], f"Analyst {analyst_id} effectiveAt")
        captured_at = instant(record["capturedAt"], f"Analyst {analyst_id} capturedAt")
        require(effective_at <= captured_at <= provenance_captured_at,
                "Analyst chronology")

    if require_exact:
        require(len(document["institutions"]) == 2, "Exact institution count")
        actual_by_id = {
            record["institutionId"]: record for record in document["institutions"]
        }
        require(actual_by_id == expected_institutions, "Exact institution records")
        projected = sorted(
            document["institutions"],
            key=lambda record: (
                code_point_key(record["canonicalName"]),
                code_point_key(record["institutionId"]),
            ),
        )
        require(
            [record["institutionId"] for record in projected] == ["inst-gs", "inst-jpm"],
            "Institution projection order",
        )
        require(len(document["analysts"]) == 2, "Exact analyst count")
        actual_analysts_by_id = {
            record["analystId"]: record for record in document["analysts"]
        }
        require(actual_analysts_by_id == expected_analysts, "Exact analyst records")
        projected_analysts = sorted(
            document["analysts"],
            key=lambda record: (
                code_point_key(record["canonicalName"]),
                code_point_key(record["analystId"]),
            ),
        )
        require(
            [record["analystId"] for record in projected_analysts]
            == ["analyst-demo-a", "analyst-demo-b"],
            "Analyst projection order",
        )

    return generated_at

generated_at = validate(master, require_exact=True)
manifest_paths = [entry["path"] for entry in manifest["files"]]
require(manifest_paths.count("master-data.json") == 1, "Master manifest membership")
require(generated_at <= instant(manifest["generatedAt"], "Manifest generatedAt"),
        "Master manifest chronology")
institution_specific_contracts = [
    *Path("schemas").glob("*institution*.json"),
    *fixture_dir.glob("*institution*.json"),
]
require(not institution_specific_contracts,
        "Institution slice must not add a duplicate schema or fixture")
allowed_analyst_contract_paths = {
    Path("schemas/analyst-call.schema.json"),
    Path("schemas/analyst-call-revision.schema.json"),
    fixture_dir / "analyst-calls.json",
    fixture_dir / "analyst-call-revisions.json",
}
analyst_specific_contracts = [
    path
    for directory in (Path("schemas"), fixture_dir)
    for path in directory.glob("*analyst*.json")
    if path not in allowed_analyst_contract_paths
]
require(not analyst_specific_contracts,
        "Analyst slice must not add a duplicate schema or fixture")

adapter_paths = {
    "Institution": Path(
        "apps/web/src/lib/providers/fixture-institution-directory-provider.ts"
    ),
    "Analyst": Path(
        "apps/web/src/lib/providers/fixture-analyst-directory-provider.ts"
    ),
}
for owner, adapter_path in adapter_paths.items():
    require(adapter_path.is_file(), f"{owner} fixture provider exists")
    adapter_source = adapter_path.read_text(encoding="utf-8")
    fixture_imports = re.findall(r"fixtures/v1/[^\"']+\.json", adapter_source)
    require(fixture_imports == ["fixtures/v1/master-data.json"],
            f"{owner} provider source isolation")
    for forbidden_source in (
        "analyst-calls.json",
        "call-outcomes.json",
        "analyst-call-revisions.json",
        "calls-provider",
        "callsProvider",
        "CallsProvider",
    ):
        require(forbidden_source not in adapter_source,
                f"{owner} provider source isolation")

semantic_negative_cases = []
missing_envelope = deepcopy(master)
del missing_envelope["assets"]
semantic_negative_cases.append(("missing envelope field", missing_envelope, "Master envelope fields"))
extra_envelope = deepcopy(master)
extra_envelope["rank"] = None
semantic_negative_cases.append(("extra envelope field", extra_envelope, "Master envelope fields"))
missing_provenance = deepcopy(master)
del missing_provenance["provenance"]["licenseClass"]
semantic_negative_cases.append(("missing provenance field", missing_provenance, "Master provenance fields"))
extra_provenance = deepcopy(master)
extra_provenance["provenance"]["score"] = None
semantic_negative_cases.append(("extra provenance field", extra_provenance, "Master provenance fields"))
reordered_source_paths = deepcopy(master)
reordered_source_paths["provenance"]["sourcePaths"].reverse()
semantic_negative_cases.append(("reordered source paths", reordered_source_paths, "Master source paths"))
missing_record = deepcopy(master)
del missing_record["institutions"][0]["country"]
semantic_negative_cases.append(("missing institution field", missing_record, "Institution fields"))
extra_record = deepcopy(master)
extra_record["institutions"][0]["accuracy"] = None
semantic_negative_cases.append(("extra institution field", extra_record, "Institution fields"))
duplicate_id = deepcopy(master)
duplicate_id["institutions"][1]["institutionId"] = "inst-jpm"
semantic_negative_cases.append(("duplicate institution ID", duplicate_id, "Duplicate institution ID"))
duplicate_slug = deepcopy(master)
duplicate_slug["institutions"][1]["slug"] = "jpmorgan"
semantic_negative_cases.append(("duplicate institution slug", duplicate_slug, "Duplicate institution slug"))
duplicate_name = deepcopy(master)
duplicate_name["institutions"][1]["canonicalName"] = "JPMorgan"
semantic_negative_cases.append(("duplicate institution name", duplicate_name, "Duplicate institution name"))
invalid_identifier = deepcopy(master)
invalid_identifier["institutions"][0]["institutionId"] = "INST_JPM"
semantic_negative_cases.append(("invalid institution identifier", invalid_identifier, "Institution identifier"))
wrong_mode = deepcopy(master)
wrong_mode["institutions"][0]["dataMode"] = "REALTIME"
semantic_negative_cases.append(("mixed data mode", wrong_mode, "Institution data mode"))
wrong_provenance = deepcopy(master)
wrong_provenance["institutions"][0]["provenanceId"] = "fixture-other"
semantic_negative_cases.append(("provenance divergence", wrong_provenance, "Institution provenance"))
future_effective = deepcopy(master)
future_effective["institutions"][0]["effectiveAt"] = "2026-08-18T00:00:00.000001Z"
semantic_negative_cases.append(("effective time after capture", future_effective, "Institution chronology"))
captured_after_provenance = deepcopy(master)
captured_after_provenance["institutions"][0]["capturedAt"] = "2026-08-18T00:00:00.000001Z"
semantic_negative_cases.append(("record capture after provenance", captured_after_provenance, "Institution chronology"))
excessive_precision = deepcopy(master)
excessive_precision["generatedAt"] = "2026-08-18T00:00:00.0000001Z"
semantic_negative_cases.append(("excessive UTC precision", excessive_precision, "Master generatedAt UTC instant"))
bad_collection = deepcopy(master)
bad_collection["analystEmployments"] = {}
semantic_negative_cases.append(("non-array excluded collection", bad_collection, "Master analystEmployments collection"))

missing_analyst_field = deepcopy(master)
del missing_analyst_field["analysts"][0]["active"]
semantic_negative_cases.append(("missing analyst field", missing_analyst_field, "Analyst fields"))
extra_analyst_field = deepcopy(master)
extra_analyst_field["analysts"][0]["rank"] = None
semantic_negative_cases.append(("extra analyst field", extra_analyst_field, "Analyst fields"))
duplicate_analyst_id = deepcopy(master)
duplicate_analyst_id["analysts"][1]["analystId"] = "analyst-demo-a"
semantic_negative_cases.append(("duplicate analyst ID", duplicate_analyst_id, "Duplicate analyst ID"))
invalid_analyst_id = deepcopy(master)
invalid_analyst_id["analysts"][0]["analystId"] = "Analyst_Demo_A"
semantic_negative_cases.append(("invalid analyst ID", invalid_analyst_id, "Analyst identifier"))
padded_analyst_name = deepcopy(master)
padded_analyst_name["analysts"][0]["canonicalName"] = " Demo Analyst A"
semantic_negative_cases.append(("padded analyst name", padded_analyst_name, "Analyst canonical name"))
non_boolean_analyst_active = deepcopy(master)
non_boolean_analyst_active["analysts"][0]["active"] = "true"
semantic_negative_cases.append(("non-boolean analyst active", non_boolean_analyst_active, "Analyst active state"))
wrong_analyst_mode = deepcopy(master)
wrong_analyst_mode["analysts"][0]["dataMode"] = "REALTIME"
semantic_negative_cases.append(("mixed analyst data mode", wrong_analyst_mode, "Analyst data mode"))
wrong_analyst_provenance = deepcopy(master)
wrong_analyst_provenance["analysts"][0]["provenanceId"] = "fixture-other"
semantic_negative_cases.append(("analyst provenance divergence", wrong_analyst_provenance, "Analyst provenance"))
future_analyst_effective = deepcopy(master)
future_analyst_effective["analysts"][0]["effectiveAt"] = "2026-08-18T00:00:00.000001Z"
semantic_negative_cases.append(("analyst effective time after capture", future_analyst_effective, "Analyst chronology"))
analyst_capture_after_provenance = deepcopy(master)
analyst_capture_after_provenance["analysts"][0]["capturedAt"] = "2026-08-18T00:00:00.000001Z"
semantic_negative_cases.append(("analyst capture after provenance", analyst_capture_after_provenance, "Analyst chronology"))
analyst_excessive_precision = deepcopy(master)
analyst_excessive_precision["analysts"][0]["effectiveAt"] = "2026-08-10T00:00:00.0000001Z"
semantic_negative_cases.append(("analyst excessive UTC precision", analyst_excessive_precision, "Analyst analyst-demo-a effectiveAt UTC instant"))

for label, candidate, expected_error in semantic_negative_cases:
    try:
        validate(candidate, require_exact=False)
    except ValueError as error:
        if expected_error not in str(error):
            raise SystemExit(
                f"Master identity semantic gate rejected {label} for the wrong invariant: {error}"
            ) from error
        continue
    raise SystemExit(f"Master identity semantic gate accepted {label}")

empty_analysts = deepcopy(master)
empty_analysts["analysts"] = []
validate(empty_analysts, require_exact=False)

appended_analysts = deepcopy(master)
appended_analysts["analysts"].append({
    "analystId": "analyst-demo-c",
    "canonicalName": "Demo Analyst C",
    "active": False,
    "dataMode": "DEMO",
    "effectiveAt": "2026-08-11T00:00:00Z",
    "capturedAt": "2026-08-18T00:00:00Z",
    "provenanceId": "fixture-master-data-v1",
})
appended_source_order = deepcopy(appended_analysts["analysts"])
validate(appended_analysts, require_exact=False)
appended_projection = sorted(
    appended_analysts["analysts"],
    key=lambda record: (
        code_point_key(record["canonicalName"]),
        code_point_key(record["analystId"]),
    ),
)
require(
    [record["analystId"] for record in appended_projection]
    == ["analyst-demo-a", "analyst-demo-b", "analyst-demo-c"],
    "Append-safe analyst projection order",
)
require(appended_analysts["analysts"] == appended_source_order,
        "Analyst source order mutation")

same_name_analysts = deepcopy(master)
same_name_analysts["analysts"].append({
    "analystId": "analyst-demo-c",
    "canonicalName": "Demo Analyst A",
    "active": True,
    "dataMode": "DEMO",
    "effectiveAt": "2026-08-10T00:00:00Z",
    "capturedAt": "2026-08-18T00:00:00Z",
    "provenanceId": "fixture-master-data-v1",
})
validate(same_name_analysts, require_exact=False)
same_name_projection = sorted(
    same_name_analysts["analysts"],
    key=lambda record: (
        code_point_key(record["canonicalName"]),
        code_point_key(record["analystId"]),
    ),
)
require(
    [record["analystId"] for record in same_name_projection]
    == ["analyst-demo-a", "analyst-demo-c", "analyst-demo-b"],
    "Same-name analyst ID tie-break",
)

print(
    "Validated 2 institution and 2 analyst DEMO identities with exact root "
    "provenance, append-safe code-point projection, source isolation, and no "
    "directory-specific schema or fixture"
)
PYTHON
