python <<'PYTHON'
import json
from pathlib import Path
from jsonschema import Draft202012Validator, FormatChecker
from referencing import Registry, Resource

paths = sorted(Path("schemas").glob("*.schema.json"))
if not paths:
    raise SystemExit("No canonical JSON Schemas found")

utc_pattern = r"^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}(?:\.\d{1,6})?Z$"
persisted_timestamps = {
    "analyst-call.schema.json": {"eventTime", "processingTime", "capturedAt"},
    "analyst-call-revision.schema.json": {"eventTime", "processingTime", "capturedAt"},
    "market-snapshot.schema.json": {"eventTime", "processingTime", "capturedAt"},
    "source-document.schema.json": {"capturedAt"},
    "source-reference.schema.json": {"capturedAt"},
    "scoring-methodology.schema.json": {"effectiveAt", "capturedAt"},
    "call-outcome.schema.json": {"eventTime", "processingTime", "capturedAt"},
    "macro-observation.schema.json": {"releasedAt", "processingTime", "capturedAt"},
    "macro-snapshot.schema.json": {"eventTime", "processingTime", "capturedAt"},
    "event-context.schema.json": {"eventTime", "processingTime", "capturedAt"},
}
schema_ids = set()
schema_documents = {}
for path in paths:
    with path.open(encoding="utf-8") as handle:
        schema = json.load(handle)
    Draft202012Validator.check_schema(schema)
    if schema.get("$schema") != "https://json-schema.org/draft/2020-12/schema":
        raise SystemExit(f"{path}: Draft 2020-12 marker is required")
    schema_id = schema.get("$id")
    if not isinstance(schema_id, str) or not schema_id:
        raise SystemExit(f"{path}: non-empty $id is required")
    if schema_id in schema_ids:
        raise SystemExit(f"{path}: duplicate $id {schema_id}")
    schema_ids.add(schema_id)
    schema_documents[path.name] = schema

    timestamp_fields = persisted_timestamps.get(path.name)
    if timestamp_fields is not None:
        utc_instant = schema.get("$defs", {}).get("utcInstant", {})
        if (utc_instant.get("type") != "string"
                or utc_instant.get("format") != "date-time"
                or utc_instant.get("pattern") != utc_pattern):
            raise SystemExit(f"{path}: utcInstant definition is not exact")
        if any(
            schema["properties"][field].get("$ref") != "#/$defs/utcInstant"
            for field in timestamp_fields
        ):
            raise SystemExit(f"{path}: persisted timestamps must use utcInstant")

    if path.name == "source-document.schema.json":
        published_at = schema["properties"].get("publishedAt", {}).get("oneOf", [])
        expected_published_at = [
            {"$ref": "#/$defs/utcInstant"},
            {"type": "null"},
        ]
        if published_at != expected_published_at:
            raise SystemExit(f"{path}: publishedAt must be utcInstant or null")

registry = Registry()
for schema in schema_documents.values():
    registry = registry.with_resource(
        schema["$id"], Resource.from_contents(schema)
    )

fixture_dir = Path("fixtures/v1")
call_document = json.loads(
    (fixture_dir / "analyst-calls.json").read_text(encoding="utf-8")
)
source_documents = {
    item["sourceDocumentId"]: item
    for item in call_document["sourceDocuments"]
}
preserved_source = source_documents.get("source-demo-article-001")
expected_preserved_source = {
    "publisher": "DEMO Publisher",
    "canonicalUrl": "https://example.invalid/demo-call-001",
    "publishedAt": "2026-08-10T12:00:00Z",
    "externalId": "demo-source-001",
}
if preserved_source is None or any(
    preserved_source.get(field) != value
    for field, value in expected_preserved_source.items()
):
    raise SystemExit(
        "source-demo-article-001 must retain its pre-existing populated metadata"
    )

nullable_source = source_documents.get("source-demo-article-003")
expected_nullable_source = {
    "sourceType": "ARTICLE",
    "title": "DEMO unattributed neutral outlook",
    "provider": "fixture",
    "licenseClass": "INTERNAL_DEMO",
    "dataMode": "DEMO",
    "capturedAt": "2026-08-10T10:02:00Z",
    "provenanceId": "fixture-analyst-calls-v1",
}
nullable_source_fields = {
    "publisher", "canonicalUrl", "publishedAt", "externalId", "contentHash",
}
if (nullable_source is None
        or any(
            nullable_source.get(field) != value
            for field, value in expected_nullable_source.items()
        )
        or any(
            field not in nullable_source or nullable_source[field] is not None
            for field in nullable_source_fields
        )):
    raise SystemExit(
        "source-demo-article-003 must explicitly null every optional source-document field"
    )

source_references = {
    item["sourceReferenceId"]: item
    for item in call_document["sourceReferences"]
}
nullable_reference = source_references.get("source-ref-demo-003")
if (nullable_reference is None
        or nullable_reference.get("sourceDocumentId") != "source-demo-article-003"):
    raise SystemExit(
        "source-ref-demo-003 must reference the append-only nullable document"
    )

calls = {item["callId"]: item for item in call_document["calls"]}
nullable_call = calls.get("demo-call-003")
expected_nullable_call = {
    "provider": "fixture",
    "providerEventId": "fixture-call-003",
    "institutionId": "inst-gs",
    "analystId": None,
    "assetId": "asset-msft",
    "eventTime": "2026-08-10T10:00:00Z",
    "processingTime": "2026-08-10T10:02:00Z",
    "direction": "NEUTRAL",
    "originalRating": None,
    "previousTarget": None,
    "target": None,
    "currency": None,
    "targetDate": None,
    "sourceReferenceId": "source-ref-demo-003",
    "status": "ACTIVE",
    "dataMode": "DEMO",
    "capturedAt": "2026-08-10T10:02:00Z",
    "provenanceId": "fixture-analyst-calls-v1",
}
if nullable_call is None or any(
    nullable_call.get(field) != value
    for field, value in expected_nullable_call.items()
):
    raise SystemExit(
        "demo-call-003 must retain the exact upgrade-safe nullable evidence linkage"
    )

positive_source = source_documents.get("source-demo-video-002")
expected_positive_source = {
    "publisher": "DEMO Channel",
    "canonicalUrl": "https://example.invalid/demo-call-002",
    "publishedAt": "2026-08-11T14:20:00Z",
    "externalId": "demo-source-002",
}
if positive_source is None or any(
    positive_source.get(field) != value
    for field, value in expected_positive_source.items()
):
    raise SystemExit(
        "source-demo-video-002 must preserve the populated source-document path"
    )

snapshot_document = json.loads(
    (fixture_dir / "market-snapshots.json").read_text(encoding="utf-8")
)
revision_document = json.loads(
    (fixture_dir / "analyst-call-revisions.json").read_text(encoding="utf-8")
)
context_document = json.loads(
    (fixture_dir / "call-contexts.json").read_text(encoding="utf-8")
)
fixture_cases = [
    ("analyst-call.schema.json", call_document["calls"], call_document["schemaVersion"]),
    ("source-document.schema.json", call_document["sourceDocuments"], call_document["schemaVersion"]),
    ("source-reference.schema.json", call_document["sourceReferences"], call_document["schemaVersion"]),
    ("market-snapshot.schema.json", snapshot_document["snapshots"], snapshot_document["schemaVersion"]),
    ("analyst-call-revision.schema.json", revision_document["revisions"], revision_document["schemaVersion"]),
    ("source-document.schema.json", context_document["sourceDocuments"], context_document["schemaVersion"]),
    ("source-reference.schema.json", context_document["sourceReferences"], context_document["schemaVersion"]),
    ("macro-observation.schema.json", context_document["macroObservations"], context_document["schemaVersion"]),
    ("event-context.schema.json", context_document["eventContexts"], context_document["schemaVersion"]),
]
validated_records = 0
for schema_name, records, schema_version in fixture_cases:
    schema = schema_documents[schema_name]
    validator = Draft202012Validator(
        schema, registry=registry, format_checker=FormatChecker()
    )
    for record in records:
        canonical_record = {"schemaVersion": schema_version, **record}
        validator.validate(canonical_record)
        validated_records += 1

observations_by_id = {
    item["macroObservationId"]: item
    for item in context_document["macroObservations"]
}
canonical_snapshots = []
macro_snapshot_validator = Draft202012Validator(
    schema_documents["macro-snapshot.schema.json"],
    registry=registry,
    format_checker=FormatChecker(),
)
for snapshot in context_document["macroSnapshots"]:
    observation_ids = snapshot["observationIds"]
    canonical_snapshot = {
        "schemaVersion": context_document["schemaVersion"],
        **{
            key: value
            for key, value in snapshot.items()
            if key != "observationIds"
        },
        "observations": [
            {
                "schemaVersion": context_document["schemaVersion"],
                **observations_by_id[observation_id],
            }
            for observation_id in observation_ids
        ],
    }
    macro_snapshot_validator.validate(canonical_snapshot)
    canonical_snapshots.append(canonical_snapshot)
    validated_records += 1

event_contexts_by_call = {
    item["callId"]: {
        "schemaVersion": context_document["schemaVersion"],
        **item,
    }
    for item in context_document["eventContexts"]
}
snapshots_by_call = {
    item["callId"]: item for item in canonical_snapshots
}
call_context_validator = Draft202012Validator(
    schema_documents["call-context.schema.json"],
    registry=registry,
    format_checker=FormatChecker(),
)
call_context_validator.validate({
    "macroSnapshot": snapshots_by_call["demo-call-001"],
    "eventContext": event_contexts_by_call["demo-call-001"],
})
call_context_validator.validate({
    "macroSnapshot": None,
    "eventContext": None,
})

print(
    f"Parsed {len(paths)} canonical JSON Schemas and validated "
    f"{validated_records} canonical fixture records"
)
PYTHON
