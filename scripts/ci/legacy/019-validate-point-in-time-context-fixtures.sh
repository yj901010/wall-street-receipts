python <<'PYTHON'
import json
from datetime import date, datetime
from decimal import Decimal
from pathlib import Path

fixture_dir = Path("fixtures/v1")

def load_json(path):
    return json.loads(path.read_text(encoding="utf-8"), parse_float=Decimal)

def instant(value):
    return datetime.fromisoformat(value.replace("Z", "+00:00"))

context = load_json(fixture_dir / "call-contexts.json")
calls_document = load_json(fixture_dir / "analyst-calls.json")
calls = {item["callId"]: item for item in calls_document["calls"]}
generated_at = instant(context["generatedAt"])
provenance_id = context["provenance"]["id"]

if context["schemaVersion"] != "1.0.0" or context["dataMode"] != "DEMO":
    raise SystemExit("Context fixture must use schema 1.0.0 and DEMO mode")
if context["provenance"].get("synthetic") is not True:
    raise SystemExit("Context fixture provenance must be explicitly synthetic")
if "synthetic DEMO" not in context.get("disclaimer", ""):
    raise SystemExit("Context fixture must disclaim every numeric and schedule value as synthetic DEMO")

expected_counts = {
    "sourceDocuments": 5,
    "sourceReferences": 5,
    "macroObservations": 7,
    "macroSnapshots": 1,
    "eventContexts": 1,
}
for field, expected_count in expected_counts.items():
    if len(context[field]) != expected_count:
        raise SystemExit(
            f"Expected {expected_count} context {field}, found {len(context[field])}"
        )
if context["knownEmptyCallIds"] != ["demo-call-002", "demo-call-003"]:
    raise SystemExit(
        "The deterministic known-empty contexts must be demo-call-002 and demo-call-003"
    )

documents = {
    item["sourceDocumentId"]: item for item in context["sourceDocuments"]
}
references = {
    item["sourceReferenceId"]: item for item in context["sourceReferences"]
}
observations = {
    item["macroObservationId"]: item
    for item in context["macroObservations"]
}
if len(documents) != expected_counts["sourceDocuments"]:
    raise SystemExit("Duplicate context sourceDocumentId")
if len(references) != expected_counts["sourceReferences"]:
    raise SystemExit("Duplicate context sourceReferenceId")
if len(observations) != expected_counts["macroObservations"]:
    raise SystemExit("Duplicate macroObservationId")

for collection_name in (
    "sourceDocuments", "sourceReferences", "macroObservations",
    "macroSnapshots", "eventContexts",
):
    for item in context[collection_name]:
        if item["dataMode"] != "DEMO" or item["provenanceId"] != provenance_id:
            raise SystemExit(f"Context mode/provenance mismatch in {collection_name}")
        if instant(item["capturedAt"]) > generated_at:
            raise SystemExit(f"Context record captured after fixture generation: {collection_name}")

for reference_id, reference in references.items():
    document = documents.get(reference["sourceDocumentId"])
    if document is None:
        raise SystemExit(f"Orphan context source reference: {reference_id}")
    if instant(document["capturedAt"]) > instant(reference["capturedAt"]):
        raise SystemExit(f"Reference predates its document capture: {reference_id}")

for observation_id, observation in observations.items():
    reference = references.get(observation["sourceReferenceId"])
    if reference is None:
        raise SystemExit(f"Macro observation has no context evidence: {observation_id}")
    document = documents[reference["sourceDocumentId"]]
    released_at = instant(observation["releasedAt"])
    processing_time = instant(observation["processingTime"])
    captured_at = instant(observation["capturedAt"])
    if not released_at <= processing_time <= captured_at:
        raise SystemExit(f"Invalid observation time order: {observation_id}")
    if instant(reference["capturedAt"]) > captured_at:
        raise SystemExit(f"Observation predates source-reference capture: {observation_id}")
    published_at = document["publishedAt"]
    if published_at is not None and instant(published_at) > released_at:
        raise SystemExit(f"Observation predates source publication: {observation_id}")

    vintage_start = observation["vintageStart"]
    vintage_end = observation["vintageEnd"]
    if (vintage_start is not None and vintage_end is not None
            and date.fromisoformat(vintage_start) > date.fromisoformat(vintage_end)):
        raise SystemExit(f"Invalid inclusive vintage interval: {observation_id}")

    value = observation["value"]
    if value is not None:
        if abs(value) >= Decimal("1e26"):
            raise SystemExit(f"Macro value exceeds decimal boundary: {observation_id}")
        if max(0, -value.as_tuple().exponent) > 12:
            raise SystemExit(f"Macro value exceeds scale 12: {observation_id}")

expected_series = [
    "FED_FUNDS_LOWER",
    "FED_FUNDS_UPPER",
    "CPI_YOY",
    "CORE_CPI_YOY",
    "PPI_YOY",
    "UNEMPLOYMENT_RATE",
]
snapshots_by_call = {}
selected_observation_ids = set()
snapshot_ids = set()
for snapshot in context["macroSnapshots"]:
    snapshot_id = snapshot["macroSnapshotId"]
    if snapshot_id in snapshot_ids:
        raise SystemExit(f"Duplicate macroSnapshotId: {snapshot_id}")
    snapshot_ids.add(snapshot_id)
    call = calls.get(snapshot["callId"])
    if call is None:
        raise SystemExit(f"Macro snapshot references an unknown call: {snapshot_id}")
    if snapshot["callId"] in snapshots_by_call:
        raise SystemExit(f"More than one macro snapshot for call: {snapshot['callId']}")
    snapshots_by_call[snapshot["callId"]] = snapshot

    event_time = instant(snapshot["eventTime"])
    processing_time = instant(snapshot["processingTime"])
    captured_at = instant(snapshot["capturedAt"])
    if snapshot["eventTime"] != call["eventTime"]:
        raise SystemExit(f"Macro snapshot eventTime differs from its call: {snapshot_id}")
    if not event_time <= processing_time <= captured_at:
        raise SystemExit(f"Invalid macro snapshot time order: {snapshot_id}")
    if snapshot["immutable"] is not True:
        raise SystemExit(f"Mutable macro snapshot fixture: {snapshot_id}")

    observation_ids = snapshot["observationIds"]
    if len(observation_ids) != len(set(observation_ids)):
        raise SystemExit(f"Duplicate observation in snapshot: {snapshot_id}")
    try:
        selected = [observations[item_id] for item_id in observation_ids]
    except KeyError as error:
        raise SystemExit(f"Unknown snapshot observation: {error.args[0]}") from error
    if [item["series"] for item in selected] != expected_series:
        raise SystemExit(f"Non-deterministic macro series order: {snapshot_id}")

    event_date = event_time.date()
    for observation in selected:
        observation_id = observation["macroObservationId"]
        selected_observation_ids.add(observation_id)
        if instant(observation["releasedAt"]) > event_time:
            raise SystemExit(f"Snapshot uses a post-event release: {observation_id}")
        if instant(observation["processingTime"]) > processing_time:
            raise SystemExit(f"Snapshot uses a later-processed observation: {observation_id}")
        if instant(observation["capturedAt"]) > captured_at:
            raise SystemExit(f"Snapshot uses a later-captured observation: {observation_id}")
        vintage_start = observation["vintageStart"]
        vintage_end = observation["vintageEnd"]
        if vintage_start is not None and event_date < date.fromisoformat(vintage_start):
            raise SystemExit(f"Snapshot predates observation vintage: {observation_id}")
        if vintage_end is not None and event_date > date.fromisoformat(vintage_end):
            raise SystemExit(f"Snapshot postdates observation vintage: {observation_id}")

original_cpi_id = "macro-observation-demo-cpi-original-001"
revised_cpi_id = "macro-observation-demo-cpi-revision-001"
if original_cpi_id not in selected_observation_ids:
    raise SystemExit("Call-001 snapshot must select the original CPI vintage")
if revised_cpi_id in selected_observation_ids:
    raise SystemExit("Call-001 snapshot must not select the later CPI revision")
original_cpi = observations[original_cpi_id]
revised_cpi = observations[revised_cpi_id]
if (original_cpi["series"] != revised_cpi["series"]
        or original_cpi["observationDate"] != revised_cpi["observationDate"]):
    raise SystemExit("CPI revision must preserve series and observation identity")
if date.fromisoformat(original_cpi["vintageEnd"]) >= date.fromisoformat(revised_cpi["vintageStart"]):
    raise SystemExit("Original and revised CPI vintage intervals must not overlap")
call_one_snapshot = snapshots_by_call.get("demo-call-001")
if call_one_snapshot is None:
    raise SystemExit("demo-call-001 must have the populated macro snapshot")
if instant(revised_cpi["releasedAt"]) <= instant(call_one_snapshot["eventTime"]):
    raise SystemExit("The DEMO CPI revision must be released after call-001")
ppi = observations["macro-observation-demo-ppi-original-001"]
if ppi["value"] is not None:
    raise SystemExit("The DEMO PPI value must remain explicit null")

event_contexts_by_call = {}
event_context_ids = set()
future_schedule_fields = (
    "nextCpiAt", "nextFomcAt", "nextNfpAt", "optionsExpirationAt",
)
for event_context in context["eventContexts"]:
    context_id = event_context["eventContextId"]
    if context_id in event_context_ids:
        raise SystemExit(f"Duplicate eventContextId: {context_id}")
    event_context_ids.add(context_id)
    call = calls.get(event_context["callId"])
    if call is None:
        raise SystemExit(f"Event context references an unknown call: {context_id}")
    if event_context["callId"] in event_contexts_by_call:
        raise SystemExit(f"More than one event context for call: {event_context['callId']}")
    event_contexts_by_call[event_context["callId"]] = event_context
    if event_context["eventTime"] != call["eventTime"]:
        raise SystemExit(f"Event context eventTime differs from its call: {context_id}")
    event_time = instant(event_context["eventTime"])
    processing_time = instant(event_context["processingTime"])
    captured_at = instant(event_context["capturedAt"])
    if not event_time <= processing_time <= captured_at:
        raise SystemExit(f"Invalid event context time order: {context_id}")
    if event_context["immutable"] is not True:
        raise SystemExit(f"Mutable event context fixture: {context_id}")
    reference = references.get(event_context["sourceReferenceId"])
    if reference is None:
        raise SystemExit(f"Event context has no evidence: {context_id}")
    if instant(reference["capturedAt"]) > event_time:
        raise SystemExit(f"Event schedule was not captured by event time: {context_id}")
    for field in future_schedule_fields:
        value = event_context[field]
        if value is not None and instant(value) < event_time:
            raise SystemExit(f"{field} precedes context eventTime: {context_id}")

if "demo-call-001" not in event_contexts_by_call:
    raise SystemExit("demo-call-001 must have the populated event context")
for empty_call_id in context["knownEmptyCallIds"]:
    if empty_call_id not in calls:
        raise SystemExit(f"Known-empty context references unknown call: {empty_call_id}")
    if empty_call_id in snapshots_by_call or empty_call_id in event_contexts_by_call:
        raise SystemExit(f"Known-empty call has a context record: {empty_call_id}")

identity_groups = [
    set(calls), set(documents), set(references), set(observations),
    snapshot_ids, event_context_ids,
]
for index, group in enumerate(identity_groups):
    for other in identity_groups[index + 1:]:
        if group & other:
            raise SystemExit(f"Canonical identity collision: {sorted(group & other)}")

print(
    "Validated 5 context evidence chains, 7 macro observations, "
    "1 immutable macro snapshot, 1 event context, and 2 known-empty calls"
)
PYTHON
