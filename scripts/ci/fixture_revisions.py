"""Current-tree, read-only analyst-call revision fixture contract.

The substantive checks from historical step 84 remain in force. The current
contract additionally rejects ambiguous manifest/base identity collections and
non-DEMO fixture provenance before creating lookup dictionaries. It neither
changes the working directory nor reads Git, provider data, or the network.
"""
from __future__ import annotations

from collections import defaultdict
from pathlib import Path

from jsonschema import ValidationError

from fixture_contracts_common import instant, load_json, require, validator


FIXTURE_DIRECTORY = "fixtures/v1"
REVISION_SCHEMA = "schemas/analyst-call-revision.schema.json"
REVISION_FIELDS = {
    "revisionId", "schemaVersion", "callId", "supersedesRevisionId",
    "sequenceNumber", "provider", "providerEventId", "revisionType",
    "eventTime", "processingTime", "correctedTerms", "reason",
    "sourceReferenceId", "dataMode", "capturedAt", "provenanceId",
}
CORRECTED_TERM_FIELDS = {
    "direction", "originalRating", "previousTarget", "target", "currency", "targetDate",
}
REVISION_DOCUMENT_FIELDS = {
    "schemaVersion", "fixtureVersion", "dataMode", "generatedAt",
    "provenance", "revisions", "disclaimer",
}
PROVENANCE_FIELDS = {
    "id", "sourceType", "sourcePaths", "capturedAt", "synthetic", "licenseClass",
}


def _records(value, label: str) -> list:
    require(isinstance(value, list) and all(isinstance(item, dict) for item in value),
            f"{label} must be an array of records")
    return value


def _indexed(value, field: str, label: str) -> dict:
    records = _records(value, label)
    result = {}
    for item in records:
        identity = item.get(field)
        require(isinstance(identity, str) and bool(identity), f"Invalid {field} in {label}")
        require(identity not in result, f"Duplicate {field}")
        result[identity] = item
    return result


def _provider_events(records, label: str) -> set:
    result = set()
    for item in records:
        provider, event = item.get("provider"), item.get("providerEventId")
        require(isinstance(provider, str) and bool(provider)
                and isinstance(event, str) and bool(event), f"Invalid {label} provider event identity")
        key = (provider, event)
        require(key not in result, f"Duplicate {label} provider event identity")
        result.add(key)
    return result


def _validate_manifest(root: Path) -> None:
    manifest = load_json(root, f"{FIXTURE_DIRECTORY}/manifest.json")
    files = _records(manifest.get("files"), "Fixture manifest files")
    declared = set()
    for entry in files:
        name = entry.get("path")
        require(isinstance(name, str) and name.endswith(".json") and name != "manifest.json"
                and "/" not in name and "\\" not in name and ":" not in name
                and name not in {".", ".."}, "Invalid fixture manifest path")
        require(name not in declared, f"Duplicate fixture manifest path: {name}")
        declared.add(name)
    directory = root / FIXTURE_DIRECTORY
    actual_paths = list(directory.glob("*.json"))
    require(all(path.is_file() and not path.is_symlink() for path in actual_paths),
            "Fixture inventory contains a missing, nonregular, or linked JSON file")
    actual = {path.name for path in actual_paths} - {"manifest.json"}
    require(declared == actual, "Fixture manifest mismatch")


def _validate_revision_document(document: dict) -> dict:
    require(set(document) == REVISION_DOCUMENT_FIELDS, "Revision fixture envelope is not closed")
    require(document["schemaVersion"] == "1.0.0" and document["fixtureVersion"] == "v1"
            and document["dataMode"] == "DEMO", "Revision fixture must retain v1 DEMO metadata")
    require(isinstance(document["disclaimer"], str) and bool(document["disclaimer"]),
            "Revision fixture requires its DEMO disclaimer")
    provenance = document["provenance"]
    require(isinstance(provenance, dict) and set(provenance) == PROVENANCE_FIELDS,
            "Revision provenance envelope is not closed")
    require(isinstance(provenance["id"], str) and bool(provenance["id"])
            and provenance["sourceType"] == "LOCAL_SPECIFICATION"
            and provenance["synthetic"] is True and provenance["licenseClass"] == "INTERNAL_DEMO",
            "Revision fixture must retain synthetic DEMO provenance")
    require(isinstance(provenance["sourcePaths"], list) and bool(provenance["sourcePaths"])
            and all(isinstance(path, str) and bool(path) for path in provenance["sourcePaths"]),
            "Revision provenance source paths must be declared")
    require(instant(provenance["capturedAt"]) <= instant(document["generatedAt"]),
            "Revision provenance is later than fixture generation")
    return provenance


def validate_revisions(root: Path) -> dict:
    """Validate the supplied current tree and report observed fixture counts."""
    _validate_manifest(root)
    calls_document = load_json(root, f"{FIXTURE_DIRECTORY}/analyst-calls.json")
    revision_document = load_json(root, f"{FIXTURE_DIRECTORY}/analyst-call-revisions.json")
    provenance = _validate_revision_document(revision_document)
    calls = _indexed(calls_document.get("calls"), "callId", "Analyst calls")
    source_references = _indexed(calls_document.get("sourceReferences"), "sourceReferenceId", "Source references")
    revisions = _records(revision_document["revisions"], "Analyst-call revisions")
    revision_validator = validator(root, REVISION_SCHEMA)

    # Validate before indexing so malformed values cannot be masked or coerced.
    for item in revisions:
        try:
            revision_validator.validate(item)
        except ValidationError as error:
            raise ValueError("Revision schema rejected a canonical record: " + error.message[:200]) from error
        require(set(item) == REVISION_FIELDS, "Revision is not a closed canonical record")
        require(item["schemaVersion"] == "1.0.0" and item["dataMode"] == "DEMO",
                "Invalid revision version or data mode")
        require(type(item["sequenceNumber"]) is int, "Revision sequence must be a canonical integer")

    require({item["revisionType"] for item in revisions} == {"CORRECTION", "CANCELLATION"},
            "DEMO revisions must exercise both correction and cancellation")
    _indexed(revisions, "revisionId", "Analyst-call revisions")
    provider_events = _provider_events(revisions, "revision")
    base_provider_events = _provider_events(calls.values(), "base call")
    require(not (provider_events & base_provider_events),
            "Base call and revision provider event identities must be disjoint")

    by_call = defaultdict(list)
    for item in revisions:
        identity = item["revisionId"]
        require(item["provenanceId"] == provenance["id"], f"Revision provenance mismatch: {identity}")
        call = calls.get(item["callId"])
        require(call is not None, f"Orphan revision: {identity}")
        require(item["sourceReferenceId"] in source_references, f"Unknown source reference: {identity}")
        terms = item["correctedTerms"]
        require(item["revisionType"] != "CORRECTION" or terms is not None,
                f"Correction has no corrected terms: {identity}")
        require(terms is None or isinstance(terms, dict) and set(terms) == CORRECTED_TERM_FIELDS,
                f"Corrected terms are not closed: {identity}")
        require(item["revisionType"] != "CANCELLATION" or terms is None,
                f"Cancellation has corrected terms: {identity}")
        original_time = instant(call.get("eventTime"))
        event_time = instant(item["eventTime"])
        processing_time = instant(item["processingTime"])
        captured_at = instant(item["capturedAt"])
        require(original_time <= event_time <= processing_time <= captured_at,
                f"Invalid revision time order: {identity}")
        require(captured_at <= instant(provenance["capturedAt"]),
                f"Revision capture is later than fixture provenance: {identity}")
        by_call[item["callId"]].append(item)

    required_demo_chain_found = False
    for call_id, lineage in by_call.items():
        lineage.sort(key=lambda item: item["sequenceNumber"])
        if [item["revisionType"] for item in lineage] == ["CORRECTION", "CANCELLATION"]:
            required_demo_chain_found = True
        previous_event_time = None
        for index, item in enumerate(lineage, start=1):
            expected_predecessor = None if index == 1 else lineage[index - 2]["revisionId"]
            require(item["sequenceNumber"] == index and item["supersedesRevisionId"] == expected_predecessor,
                    f"Broken revision chain for {call_id} at sequence {index}")
            require(item["revisionType"] != "CANCELLATION" or index == len(lineage),
                    f"Cancellation must terminate revision chain for {call_id}")
            event_time = instant(item["eventTime"])
            require(previous_event_time is None or event_time >= previous_event_time,
                    f"Revision event time moved backwards for {call_id}")
            previous_event_time = event_time
    require(required_demo_chain_found, "At least one DEMO lineage must exercise correction then cancellation")
    return {"revisionCount": len(revisions), "lineageCount": len(by_call)}
