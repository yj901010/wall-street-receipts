python <<'PYTHON'
import json
import re
from collections import defaultdict
from datetime import datetime
from pathlib import Path

from jsonschema import Draft202012Validator, FormatChecker, ValidationError

fixture_dir = Path("fixtures/v1")
schema_dir = Path("schemas")

def load_json(path):
    return json.loads(path.read_text(encoding="utf-8"))

def instant(value):
    return datetime.fromisoformat(value.replace("Z", "+00:00"))

methodology_schema = load_json(schema_dir / "scoring-methodology.schema.json")
outcome_schema = load_json(schema_dir / "call-outcome.schema.json")
for name, schema in (
    ("scoring methodology", methodology_schema),
    ("call outcome", outcome_schema),
):
    Draft202012Validator.check_schema(schema)
    if schema.get("$schema") != "https://json-schema.org/draft/2020-12/schema":
        raise SystemExit(f"{name} schema must use Draft 2020-12")
    if schema.get("additionalProperties") is not False:
        raise SystemExit(f"{name} schema must be closed")

methodology_fields = {
    "methodologyId", "methodologyVersion", "schemaVersion", "definitionHash",
    "status", "effectiveAt", "dataMode", "capturedAt", "provenanceId",
}
outcome_fields = {
    "outcomeId", "schemaVersion", "callId", "horizon", "basisRevisionId",
    "cancellationRevisionId", "snapshotId", "methodologyId", "methodologyVersion",
    "methodologyDefinitionHash", "inputFingerprint", "sequenceNumber",
    "supersedesOutcomeId", "evaluationStatus", "reasonCode", "eventTime",
    "processingTime", "assetReturn", "benchmarkReturn", "sectorReturn",
    "alpha", "sectorAlpha", "mfe", "mae", "targetHit", "directionalWin",
    "targetError", "dataComplete", "dataMode", "capturedAt", "provenanceId",
}
for name, schema, fields in (
    ("scoring methodology", methodology_schema, methodology_fields),
    ("call outcome", outcome_schema, outcome_fields),
):
    if set(schema.get("properties", {})) != fields:
        raise SystemExit(f"{name} schema fields are not exact")
    if set(schema.get("required", [])) != fields:
        raise SystemExit(f"every {name} field must be required")

utc_pattern = r"^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}(?:\.\d{1,6})?Z$"
for name, schema, timestamp_fields in (
    ("scoring methodology", methodology_schema, {"effectiveAt", "capturedAt"}),
    ("call outcome", outcome_schema, {"eventTime", "processingTime", "capturedAt"}),
):
    utc_instant = schema.get("$defs", {}).get("utcInstant", {})
    if (utc_instant.get("type") != "string"
            or utc_instant.get("format") != "date-time"
            or utc_instant.get("pattern") != utc_pattern):
        raise SystemExit(f"{name} utcInstant definition is not exact")
    if any(
        schema["properties"][field].get("$ref") != "#/$defs/utcInstant"
        for field in timestamp_fields
    ):
        raise SystemExit(f"Every {name} timestamp must use utcInstant")

decimal_quantum = 0.000000000001
numeric_magnitude = 100000000000000000000000000
nullable_ratio = outcome_schema.get("$defs", {}).get("nullableRatio", {})
if (nullable_ratio.get("exclusiveMinimum") != -numeric_magnitude
        or nullable_ratio.get("exclusiveMaximum") != numeric_magnitude
        or nullable_ratio.get("multipleOf") != decimal_quantum):
    raise SystemExit("nullableRatio must enforce ±1e26 exclusive bounds and multipleOf 1e-12")
ratio_fields = {
    "assetReturn", "benchmarkReturn", "sectorReturn", "alpha",
    "sectorAlpha", "mfe", "mae",
}
if any(
    outcome_schema["properties"][field].get("$ref") != "#/$defs/nullableRatio"
    for field in ratio_fields
):
    raise SystemExit("Every return/alpha/MFE/MAE field must use nullableRatio")
target_error_branches = outcome_schema["properties"]["targetError"].get("oneOf", [])
target_error_number = next(
    (branch for branch in target_error_branches if branch.get("type") == "number"), None
)
if (target_error_number is None
        or target_error_number.get("minimum") != 0
        or target_error_number.get("exclusiveMaximum") != numeric_magnitude
        or target_error_number.get("multipleOf") != decimal_quantum):
    raise SystemExit("targetError must enforce [0, 1e26) and multipleOf 1e-12")

fixture = load_json(fixture_dir / "call-outcomes.json")
fixture_fields = {
    "schemaVersion", "fixtureVersion", "dataMode", "generatedAt", "provenance",
    "methodologies", "outcomes", "disclaimer",
}
if set(fixture) != fixture_fields:
    raise SystemExit("call-outcomes fixture envelope is not exact")
if fixture["schemaVersion"] != "1.0.0" or fixture["fixtureVersion"] != "v1":
    raise SystemExit("call-outcomes fixture version mismatch")
if fixture["dataMode"] != "DEMO":
    raise SystemExit("call-outcomes fixture must use DEMO mode")
expected_disclaimer = (
    "Synthetic DEMO model records only; no outcome metric has been calculated or invented."
)
if fixture["disclaimer"] != expected_disclaimer:
    raise SystemExit("Methodology registry must retain the no-calculation disclaimer")

provenance = fixture["provenance"]
provenance_fields = {
    "id", "sourceType", "sourcePaths", "capturedAt", "synthetic", "licenseClass",
}
if set(provenance) != provenance_fields:
    raise SystemExit("Methodology registry provenance envelope is not exact")
if (
    provenance["id"] != "fixture-call-outcomes-v1"
    or provenance["sourceType"] != "LOCAL_SPECIFICATION"
    or provenance["synthetic"] is not True
    or provenance["licenseClass"] != "INTERNAL_DEMO"
):
    raise SystemExit("Methodology registry must retain synthetic DEMO provenance")
required_source_paths = {
    "schemas/scoring-methodology.schema.json",
    "schemas/call-outcome.schema.json",
}
if not required_source_paths.issubset(set(provenance["sourcePaths"])):
    raise SystemExit("Methodology registry provenance omits its canonical schemas")

provenance_id = provenance["id"]
fixture_generated_at = instant(fixture["generatedAt"])
provenance_captured_at = instant(provenance["capturedAt"])
if provenance_captured_at > fixture_generated_at:
    raise SystemExit("Methodology registry provenance is later than fixture generation")

manifest = load_json(fixture_dir / "manifest.json")
declared = {entry["path"] for entry in manifest["files"]}
actual = {path.name for path in fixture_dir.glob("*.json")} - {"manifest.json"}
if declared != actual or "call-outcomes.json" not in declared:
    raise SystemExit(
        f"Outcome fixture manifest mismatch: declared={sorted(declared)}, actual={sorted(actual)}"
    )

methodology_validator = Draft202012Validator(
    methodology_schema, format_checker=FormatChecker()
)
outcome_validator = Draft202012Validator(outcome_schema, format_checker=FormatChecker())
methodologies = fixture["methodologies"]
outcomes = fixture["outcomes"]
if len(methodologies) != 2 or len(outcomes) != 4:
    raise SystemExit("DEMO fixture must contain exactly 2 methodologies and 4 outcomes")

expected_methodology_evidence = [
    {
        "methodologyId": "standard-call-outcome",
        "methodologyVersion": "1.0.0",
        "schemaVersion": "1.0.0",
        "definitionHash": "03af803fd61c21b86e1897d006e6cf4f92f28ce627b06eda13b319ebfa8a07e2",
        "status": "MODEL_ONLY",
        "effectiveAt": "2026-08-10T00:00:00Z",
        "dataMode": "DEMO",
        "capturedAt": "2026-08-10T00:00:00Z",
        "provenanceId": "fixture-call-outcomes-v1",
    },
    {
        "methodologyId": "standard-call-outcome",
        "methodologyVersion": "2.0.0",
        "schemaVersion": "1.0.0",
        "definitionHash": "256056d7cb2b292a1ec0bd7b905f856134bb38851a65b8a2fceaca41489db3e8",
        "status": "MODEL_ONLY",
        "effectiveAt": "2026-08-18T00:00:00Z",
        "dataMode": "DEMO",
        "capturedAt": "2026-08-18T00:00:00Z",
        "provenanceId": "fixture-call-outcomes-v1",
    },
]
if methodologies != expected_methodology_evidence:
    raise SystemExit(
        "Methodology registry evidence or deterministic 1.0.0 -> 2.0.0 order changed"
    )

def require_schema_rejection(candidate, label):
    try:
        outcome_validator.validate(candidate)
    except ValidationError:
        return
    raise SystemExit(f"CallOutcome schema accepted invalid cancellation evidence: {label}")

cancellation_id = "demo-call-revision-002"
non_excluded_with_cancellation = dict(outcomes[0])
non_excluded_with_cancellation["cancellationRevisionId"] = cancellation_id
require_schema_rejection(non_excluded_with_cancellation, "non-EXCLUDED evidence")

excluded_without_cancellation = dict(outcomes[0])
excluded_without_cancellation.update({
    "evaluationStatus": "EXCLUDED",
    "reasonCode": "CALL_CANCELLED",
    "cancellationRevisionId": None,
})
require_schema_rejection(excluded_without_cancellation, "EXCLUDED without evidence")

excluded_with_cancellation = dict(excluded_without_cancellation)
excluded_with_cancellation["cancellationRevisionId"] = cancellation_id
outcome_validator.validate(excluded_with_cancellation)

methodology_by_key = {}
for item in methodologies:
    methodology_validator.validate(item)
    if set(item) != methodology_fields:
        raise SystemExit(f"Methodology is not closed: {item.get('methodologyId')}")
    if item["schemaVersion"] != "1.0.0" or item["dataMode"] != "DEMO":
        raise SystemExit("Methodology schema version or data mode mismatch")
    if item["provenanceId"] != provenance_id or item["status"] != "MODEL_ONLY":
        raise SystemExit("Methodology must retain DEMO model-only provenance")
    if re.fullmatch(r"[0-9a-f]{64}", item["definitionHash"]) is None:
        raise SystemExit("Methodology definition hash must be lowercase SHA-256")
    if instant(item["effectiveAt"]) > instant(item["capturedAt"]):
        raise SystemExit("Methodology capture precedes its effective time")
    if instant(item["capturedAt"]) > provenance_captured_at:
        raise SystemExit("Methodology capture is later than provenance capture")
    if instant(item["capturedAt"]) > fixture_generated_at:
        raise SystemExit("Methodology capture is later than fixture generation")
    key = (item["methodologyId"], item["methodologyVersion"])
    if key in methodology_by_key:
        raise SystemExit(f"Duplicate methodology version: {key}")
    methodology_by_key[key] = item

methodology_ids = {key[0] for key in methodology_by_key}
methodology_versions = {key[1] for key in methodology_by_key}
definition_hashes = {item["definitionHash"] for item in methodologies}
if len(methodology_ids) != 1 or methodology_versions != {"1.0.0", "2.0.0"}:
    raise SystemExit("DEMO fixture must coexist with methodology versions 1.0.0 and 2.0.0")
if len(definition_hashes) != len(methodologies):
    raise SystemExit("Distinct methodology versions must retain distinct definition hashes")

calls_document = load_json(fixture_dir / "analyst-calls.json")
snapshots_document = load_json(fixture_dir / "market-snapshots.json")
revisions_document = load_json(fixture_dir / "analyst-call-revisions.json")
calls = {item["callId"]: item for item in calls_document["calls"]}
snapshots = {item["snapshotId"]: item for item in snapshots_document["snapshots"]}
revisions = {item["revisionId"]: item for item in revisions_document["revisions"]}

metric_fields = {
    "assetReturn", "benchmarkReturn", "sectorReturn", "alpha", "sectorAlpha",
    "mfe", "mae", "targetHit", "directionalWin", "targetError",
}
status_rules = {
    "PENDING": ("HORIZON_NOT_REACHED", False),
    "INCOMPLETE": ("HORIZON_DATA_MISSING", False),
    "EXCLUDED": ("CALL_CANCELLED", False),
    "CALCULATED": (None, True),
}
outcome_ids = set()
natural_inputs = set()
by_lineage = defaultdict(list)
fixture_statuses = set()
referenced_methodology_keys = set()

for item in outcomes:
    outcome_validator.validate(item)
    if set(item) != outcome_fields:
        raise SystemExit(f"Outcome is not a closed canonical record: {item.get('outcomeId')}")
    outcome_id = item["outcomeId"]
    if outcome_id in outcome_ids:
        raise SystemExit(f"Duplicate outcomeId: {outcome_id}")
    outcome_ids.add(outcome_id)
    if item["schemaVersion"] != "1.0.0" or item["dataMode"] != "DEMO":
        raise SystemExit(f"Outcome version or data mode mismatch: {outcome_id}")
    if item["provenanceId"] != provenance_id:
        raise SystemExit(f"Outcome provenance mismatch: {outcome_id}")
    call = calls.get(item["callId"])
    if call is None:
        raise SystemExit(f"Outcome references an unknown call: {outcome_id}")
    outcome_processing_time = instant(item["processingTime"])
    call_event_time = instant(call["eventTime"])
    call_processing_time = instant(call["processingTime"])
    call_captured_at = instant(call["capturedAt"])
    if not call_event_time <= call_processing_time <= call_captured_at:
        raise SystemExit(f"Outcome references a call with invalid time order: {outcome_id}")
    if call_processing_time > outcome_processing_time or call_captured_at > outcome_processing_time:
        raise SystemExit(f"Outcome uses a future-captured call: {outcome_id}")

    snapshot_id = item["snapshotId"]
    if snapshot_id is not None:
        snapshot = snapshots.get(snapshot_id)
        if snapshot is None or snapshot["callId"] != item["callId"]:
            raise SystemExit(f"Outcome snapshot reference is invalid: {outcome_id}")
        if snapshot.get("immutable") is not True:
            raise SystemExit(f"Outcome references a mutable snapshot: {outcome_id}")
        snapshot_event_time = instant(snapshot["eventTime"])
        snapshot_processing_time = instant(snapshot["processingTime"])
        snapshot_captured_at = instant(snapshot["capturedAt"])
        if not snapshot_event_time <= snapshot_processing_time <= snapshot_captured_at:
            raise SystemExit(f"Outcome references a snapshot with invalid time order: {outcome_id}")
        if (snapshot_processing_time > outcome_processing_time
                or snapshot_captured_at > outcome_processing_time):
            raise SystemExit(f"Outcome uses a future-captured snapshot: {outcome_id}")
    basis_revision_id = item["basisRevisionId"]
    if basis_revision_id is not None:
        revision = revisions.get(basis_revision_id)
        if revision is None or revision["callId"] != item["callId"]:
            raise SystemExit(f"Outcome revision reference is invalid: {outcome_id}")
        if revision["revisionType"] != "CORRECTION":
            raise SystemExit(f"Outcome basis must be a correction revision: {outcome_id}")
        if (instant(revision["processingTime"]) > outcome_processing_time
                or instant(revision["capturedAt"]) > outcome_processing_time):
            raise SystemExit(f"Outcome uses a future-captured revision: {outcome_id}")
    cancellation_revision_id = item["cancellationRevisionId"]
    if cancellation_revision_id is not None:
        cancellation = revisions.get(cancellation_revision_id)
        if cancellation is None or cancellation["callId"] != item["callId"]:
            raise SystemExit(f"Outcome cancellation evidence is invalid: {outcome_id}")
        if cancellation["revisionType"] != "CANCELLATION":
            raise SystemExit(f"Outcome cancellation evidence must be a cancellation: {outcome_id}")
        if (instant(cancellation["processingTime"]) > outcome_processing_time
                or instant(cancellation["capturedAt"]) > outcome_processing_time):
            raise SystemExit(f"Outcome uses future-captured cancellation evidence: {outcome_id}")

    methodology_key = (item["methodologyId"], item["methodologyVersion"])
    methodology = methodology_by_key.get(methodology_key)
    if methodology is None:
        raise SystemExit(f"Outcome references an unknown methodology: {outcome_id}")
    referenced_methodology_keys.add(methodology_key)
    if item["methodologyDefinitionHash"] != methodology["definitionHash"]:
        raise SystemExit(f"Outcome methodology hash mismatch: {outcome_id}")
    if instant(methodology["effectiveAt"]) > outcome_processing_time:
        raise SystemExit(f"Outcome predates its methodology effective time: {outcome_id}")
    if instant(methodology["capturedAt"]) > outcome_processing_time:
        raise SystemExit(f"Outcome uses a future-captured methodology: {outcome_id}")

    if any(item[field] is not None for field in metric_fields):
        raise SystemExit(f"P1 DEMO outcome invents a metric or result: {outcome_id}")
    fixture_statuses.add(item["evaluationStatus"])
    expected_reason, expected_complete = status_rules[item["evaluationStatus"]]
    if item["reasonCode"] != expected_reason or item["dataComplete"] is not expected_complete:
        raise SystemExit(f"Invalid outcome status/reason/completeness: {outcome_id}")
    if item["evaluationStatus"] == "EXCLUDED":
        if cancellation_revision_id is None:
            raise SystemExit(f"Excluded outcome has no cancellation evidence: {outcome_id}")
    elif cancellation_revision_id is not None:
        raise SystemExit(f"Non-excluded outcome has cancellation evidence: {outcome_id}")

    event_time = instant(item["eventTime"])
    processing_time = outcome_processing_time
    captured_at = instant(item["capturedAt"])
    if not event_time <= processing_time <= captured_at:
        raise SystemExit(f"Invalid outcome time order: {outcome_id}")
    if processing_time > fixture_generated_at or captured_at > fixture_generated_at:
        raise SystemExit(f"Outcome is later than fixture generation: {outcome_id}")

    natural_key = (
        item["callId"], item["basisRevisionId"], item["horizon"],
        item["methodologyId"], item["methodologyVersion"], item["inputFingerprint"],
    )
    if natural_key in natural_inputs:
        raise SystemExit(f"Duplicate natural outcome input: {outcome_id}")
    natural_inputs.add(natural_key)

    lineage_key = (
        item["callId"], item["basisRevisionId"], item["horizon"],
        item["methodologyId"], item["methodologyVersion"],
    )
    by_lineage[lineage_key].append(item)

if fixture_statuses != {"PENDING", "INCOMPLETE"}:
    raise SystemExit("DEMO model fixtures must exercise only PENDING and INCOMPLETE outcomes")
if referenced_methodology_keys != set(methodology_by_key):
    raise SystemExit("Every methodology registry version must retain outcome hash evidence")
foreign_ids = set(calls) | set(snapshots) | set(revisions) | methodology_ids
if outcome_ids & foreign_ids:
    raise SystemExit("Outcome identity collides with another canonical identity kind")

version_one_d1_lineages = []
for lineage_key, lineage in by_lineage.items():
    lineage.sort(key=lambda item: item["sequenceNumber"])
    if lineage_key[2] == "D1" and lineage_key[4] == "1.0.0":
        version_one_d1_lineages.append(lineage)
    previous_times = None
    for sequence, item in enumerate(lineage, start=1):
        expected_parent = None if sequence == 1 else lineage[sequence - 2]["outcomeId"]
        if item["sequenceNumber"] != sequence:
            raise SystemExit(f"Non-contiguous outcome sequence: {lineage_key}")
        if item["supersedesOutcomeId"] != expected_parent:
            raise SystemExit(f"Broken or cross-lineage outcome parent: {item['outcomeId']}")
        current_times = tuple(
            instant(item[field]) for field in ("eventTime", "processingTime", "capturedAt")
        )
        if previous_times is not None and any(
            current < previous for current, previous in zip(current_times, previous_times)
        ):
            raise SystemExit(f"Outcome lineage time moved backwards: {item['outcomeId']}")
        previous_times = current_times

if len(version_one_d1_lineages) != 1:
    raise SystemExit("Expected exactly one methodology-v1 D1 lineage")
demo_chain = version_one_d1_lineages[0]
if [item["sequenceNumber"] for item in demo_chain] != [1, 2]:
    raise SystemExit("Methodology-v1 D1 lineage must be sequence 1 -> 2")
if any(lineage is not demo_chain and len(lineage) != 1 for lineage in by_lineage.values()):
    raise SystemExit("Every non-demo outcome lineage must be a single root")

print(
    f"Validated {len(methodologies)} methodologies and {len(outcomes)} outcomes "
    f"across {len(by_lineage)} append-only lineage(s)"
)
PYTHON
