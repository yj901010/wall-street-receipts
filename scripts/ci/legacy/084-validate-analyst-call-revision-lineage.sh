python <<'PYTHON'
import json
from collections import defaultdict
from datetime import datetime
from pathlib import Path
from jsonschema import Draft202012Validator, FormatChecker

fixture_dir = Path("fixtures/v1")
manifest = json.loads((fixture_dir / "manifest.json").read_text(encoding="utf-8"))
declared = {entry["path"] for entry in manifest["files"]}
actual = {path.name for path in fixture_dir.glob("*.json")} - {"manifest.json"}
if declared != actual:
    raise SystemExit(f"Fixture manifest mismatch: declared={sorted(declared)}, actual={sorted(actual)}")

call_document = json.loads((fixture_dir / "analyst-calls.json").read_text(encoding="utf-8"))
revision_document = json.loads(
    (fixture_dir / "analyst-call-revisions.json").read_text(encoding="utf-8")
)
calls = {item["callId"]: item for item in call_document["calls"]}
source_references = {
    item["sourceReferenceId"] for item in call_document["sourceReferences"]
}
revisions = revision_document["revisions"]
revision_schema = json.loads(
    Path("schemas/analyst-call-revision.schema.json").read_text(encoding="utf-8")
)
revision_validator = Draft202012Validator(revision_schema, format_checker=FormatChecker())
revision_fields = {
    "revisionId", "schemaVersion", "callId", "supersedesRevisionId",
    "sequenceNumber", "provider", "providerEventId", "revisionType",
    "eventTime", "processingTime", "correctedTerms", "reason",
    "sourceReferenceId", "dataMode", "capturedAt", "provenanceId",
}
corrected_term_fields = {
    "direction", "originalRating", "previousTarget", "target", "currency", "targetDate",
}

if {item["revisionType"] for item in revisions} != {"CORRECTION", "CANCELLATION"}:
    raise SystemExit("DEMO revisions must exercise both correction and cancellation")
if len({item["revisionId"] for item in revisions}) != len(revisions):
    raise SystemExit("Duplicate revisionId")
provider_events = {(item["provider"], item["providerEventId"]) for item in revisions}
if len(provider_events) != len(revisions):
    raise SystemExit("Duplicate revision provider event identity")
base_provider_events = {(item["provider"], item["providerEventId"]) for item in calls.values()}
if provider_events & base_provider_events:
    raise SystemExit("Base call and revision provider event identities must be disjoint")

by_call = defaultdict(list)
for item in revisions:
    revision_validator.validate(item)
    if set(item) != revision_fields:
        raise SystemExit(f"Revision is not a closed canonical record: {item.get('revisionId')}")
    if item["schemaVersion"] != "1.0.0" or item["dataMode"] != "DEMO":
        raise SystemExit(f"Invalid revision version or data mode: {item['revisionId']}")
    if item["provenanceId"] != revision_document["provenance"]["id"]:
        raise SystemExit(f"Revision provenance mismatch: {item['revisionId']}")
    call = calls.get(item["callId"])
    if call is None:
        raise SystemExit(f"Orphan revision {item['revisionId']}")
    if item["sourceReferenceId"] not in source_references:
        raise SystemExit(f"Unknown source reference for {item['revisionId']}")
    if item["revisionType"] == "CORRECTION" and item["correctedTerms"] is None:
        raise SystemExit(f"Correction has no corrected terms: {item['revisionId']}")
    if item["correctedTerms"] is not None and set(item["correctedTerms"]) != corrected_term_fields:
        raise SystemExit(f"Corrected terms are not closed: {item['revisionId']}")
    if item["revisionType"] == "CANCELLATION" and item["correctedTerms"] is not None:
        raise SystemExit(f"Cancellation has corrected terms: {item['revisionId']}")

    original_time = datetime.fromisoformat(call["eventTime"].replace("Z", "+00:00"))
    event_time = datetime.fromisoformat(item["eventTime"].replace("Z", "+00:00"))
    processing_time = datetime.fromisoformat(item["processingTime"].replace("Z", "+00:00"))
    captured_at = datetime.fromisoformat(item["capturedAt"].replace("Z", "+00:00"))
    if not original_time <= event_time <= processing_time <= captured_at:
        raise SystemExit(f"Invalid revision time order: {item['revisionId']}")
    by_call[item["callId"]].append(item)

required_demo_chain_found = False
for call_id, lineage in by_call.items():
    lineage.sort(key=lambda item: item["sequenceNumber"])
    if [item["revisionType"] for item in lineage] == ["CORRECTION", "CANCELLATION"]:
        required_demo_chain_found = True
    previous_event_time = None
    for index, item in enumerate(lineage, start=1):
        expected_predecessor = None if index == 1 else lineage[index - 2]["revisionId"]
        if item["sequenceNumber"] != index or item["supersedesRevisionId"] != expected_predecessor:
            raise SystemExit(f"Broken revision chain for {call_id} at sequence {index}")
        if item["revisionType"] == "CANCELLATION" and index != len(lineage):
            raise SystemExit(f"Cancellation must terminate revision chain for {call_id}")
        event_time = datetime.fromisoformat(item["eventTime"].replace("Z", "+00:00"))
        if previous_event_time is not None and event_time < previous_event_time:
            raise SystemExit(f"Revision event time moved backwards for {call_id}")
        previous_event_time = event_time

if not required_demo_chain_found:
    raise SystemExit("At least one DEMO lineage must exercise correction then cancellation")

print(f"Validated {len(revisions)} analyst-call revisions across {len(by_call)} call lineage(s)")
PYTHON
