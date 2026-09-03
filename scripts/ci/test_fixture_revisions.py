#!/usr/bin/env python3
"""Current-tree revision fixture mutation tests; source fixtures are never edited."""
from __future__ import annotations

import copy
import hashlib
import json
from pathlib import Path
import shutil
import tempfile
import unittest

from fixture_revisions import validate_revisions


SOURCE = Path(__file__).resolve().parents[2]
REVISION_FILE = "analyst-call-revisions.json"
CALL_FILE = "analyst-calls.json"


class RevisionFixtureTests(unittest.TestCase):
    def setUp(self):
        directory = tempfile.TemporaryDirectory(prefix="wsr-current-revisions-")
        self.addCleanup(directory.cleanup)
        self.root = Path(directory.name).resolve()
        shutil.copytree(SOURCE / "fixtures/v1", self.root / "fixtures/v1")
        (self.root / "schemas").mkdir()
        shutil.copyfile(SOURCE / "schemas/analyst-call-revision.schema.json",
                        self.root / "schemas/analyst-call-revision.schema.json")

    def document(self, name=REVISION_FILE):
        return json.loads((self.root / "fixtures/v1" / name).read_text(encoding="utf-8"))

    def save(self, document, name=REVISION_FILE):
        (self.root / "fixtures/v1" / name).write_text(json.dumps(document, indent=2), encoding="utf-8")

    def reject_revision(self, mutate, message):
        document = self.document()
        mutate(document)
        self.save(document)
        with self.assertRaisesRegex(ValueError, message):
            validate_revisions(self.root)

    def test_current_fixture_passes_without_any_file_mutation(self):
        before = {path.relative_to(self.root): hashlib.sha256(path.read_bytes()).hexdigest()
                  for path in self.root.rglob("*") if path.is_file()}
        self.assertEqual(validate_revisions(self.root), {"revisionCount": 2, "lineageCount": 1})
        after = {path.relative_to(self.root): hashlib.sha256(path.read_bytes()).hexdigest()
                 for path in self.root.rglob("*") if path.is_file()}
        self.assertEqual(before, after)

    def test_sequence_identity_not_input_array_order_defines_the_chain(self):
        document = self.document()
        document["revisions"].reverse()
        self.save(document)
        self.assertEqual(validate_revisions(self.root)["revisionCount"], 2)

    def test_missing_targets_remain_null_and_zero_is_rejected(self):
        document = self.document()
        terms = document["revisions"][0]["correctedTerms"]
        terms.update(previousTarget=None, target=None, currency=None)
        self.save(document)
        validate_revisions(self.root)
        terms.update(target=0, currency="USD")
        self.save(document)
        with self.assertRaisesRegex(ValueError, "Revision schema rejected"):
            validate_revisions(self.root)

    def test_manifest_membership_and_duplicate_entries_are_checked(self):
        original = self.document("manifest.json")
        for changed, message in (
            ({**original, "files": original["files"][:-1]}, "Fixture manifest mismatch"),
            ({**original, "files": original["files"] + [original["files"][0]]}, "Duplicate fixture manifest path"),
            ({**original, "files": [{"path": "../outside.json"}]}, "Invalid fixture manifest path"),
            ({**original, "files": [{"path": "nested\\outside.json"}]}, "Invalid fixture manifest path"),
            ({**original, "files": [{"path": "manifest.json"}]}, "Invalid fixture manifest path"),
        ):
            with self.subTest(message=message):
                self.save(changed, "manifest.json")
                with self.assertRaisesRegex(ValueError, message):
                    validate_revisions(self.root)

    def test_unlisted_extra_fixture_is_rejected(self):
        (self.root / "fixtures/v1/unlisted.json").write_text("{}")
        with self.assertRaisesRegex(ValueError, "Fixture manifest mismatch"):
            validate_revisions(self.root)

    def test_duplicate_base_call_ids_are_not_overwritten(self):
        document = self.document(CALL_FILE)
        document["calls"].append(copy.deepcopy(document["calls"][1]))
        self.save(document, CALL_FILE)
        with self.assertRaisesRegex(ValueError, "Duplicate callId"):
            validate_revisions(self.root)

    def test_duplicate_source_reference_ids_are_not_collapsed(self):
        document = self.document(CALL_FILE)
        document["sourceReferences"].append(copy.deepcopy(document["sourceReferences"][0]))
        self.save(document, CALL_FILE)
        with self.assertRaisesRegex(ValueError, "Duplicate sourceReferenceId"):
            validate_revisions(self.root)

    def test_duplicate_base_provider_events_are_rejected(self):
        document = self.document(CALL_FILE)
        document["calls"][1]["providerEventId"] = document["calls"][0]["providerEventId"]
        self.save(document, CALL_FILE)
        with self.assertRaisesRegex(ValueError, "Duplicate base call provider event identity"):
            validate_revisions(self.root)

    def test_duplicate_revision_and_provider_event_ids_are_rejected(self):
        original = self.document()
        for field, message in (("revisionId", "Duplicate revisionId"),
                               ("providerEventId", "Duplicate revision provider event identity")):
            with self.subTest(field=field):
                document = copy.deepcopy(original)
                document["revisions"][1][field] = document["revisions"][0][field]
                self.save(document)
                with self.assertRaisesRegex(ValueError, message):
                    validate_revisions(self.root)

    def test_base_and_revision_provider_identities_must_be_disjoint(self):
        call = self.document(CALL_FILE)["calls"][0]
        self.reject_revision(lambda doc: doc["revisions"][0].update(
            provider=call["provider"], providerEventId=call["providerEventId"]), "must be disjoint")

    def test_orphan_call_unknown_source_and_provenance_drift_are_rejected(self):
        original = self.document()
        for field, value, message in (("callId", "missing-call", "Orphan revision"),
                                       ("sourceReferenceId", "missing-source", "Unknown source reference"),
                                       ("provenanceId", "wrong-provenance", "Revision provenance mismatch")):
            with self.subTest(field=field):
                document = copy.deepcopy(original)
                document["revisions"][0][field] = value
                self.save(document)
                with self.assertRaisesRegex(ValueError, message):
                    validate_revisions(self.root)

    def test_closed_fields_types_and_corrected_terms_are_enforced(self):
        original = self.document()
        mutations = (
            lambda doc: doc["revisions"][0].update(unknown="extra"),
            lambda doc: doc["revisions"][0].pop("reason"),
            lambda doc: doc["revisions"][0].update(sequenceNumber="1"),
            lambda doc: doc["revisions"][0].update(sequenceNumber=True),
            lambda doc: doc["revisions"][0].update(correctedTerms=None),
            lambda doc: doc["revisions"][1].update(correctedTerms=copy.deepcopy(doc["revisions"][0]["correctedTerms"])),
            lambda doc: doc["revisions"][0]["correctedTerms"].update(unknown=1),
            lambda doc: doc["revisions"][0]["correctedTerms"].pop("currency"),
            lambda doc: doc["revisions"][0].update(schemaVersion="2.0.0"),
        )
        for number, mutate in enumerate(mutations):
            with self.subTest(mutation=number):
                document = copy.deepcopy(original)
                mutate(document)
                self.save(document)
                with self.assertRaisesRegex(ValueError, "Revision schema rejected"):
                    validate_revisions(self.root)

    def test_non_demo_records_and_fixture_provenance_are_rejected(self):
        original = self.document()
        mutations = (
            lambda doc: doc.update(dataMode="REALTIME"),
            lambda doc: doc["revisions"][0].update(dataMode="REALTIME"),
            lambda doc: doc["provenance"].update(synthetic=False),
            lambda doc: doc["provenance"].update(licenseClass="UNVERIFIED"),
            lambda doc: doc["provenance"].update(sourceType="PROVIDER"),
            lambda doc: doc.update(unknown="metadata"),
        )
        for number, mutate in enumerate(mutations):
            with self.subTest(mutation=number):
                document = copy.deepcopy(original)
                mutate(document)
                self.save(document)
                with self.assertRaises(ValueError):
                    validate_revisions(self.root)

    def test_revision_time_order_and_fixture_capture_boundary_are_enforced(self):
        original = self.document()
        cases = (("eventTime", "2026-08-11T14:19:59Z", "Invalid revision time order"),
                 ("processingTime", "2026-08-11T14:39:59Z", "Invalid revision time order"),
                 ("capturedAt", "2026-08-11T14:41:59Z", "Invalid revision time order"),
                 ("capturedAt", "2026-08-19T00:00:00Z", "later than fixture provenance"),
                 ("eventTime", "2026-08-11T23:40:00+09:00", "Revision schema rejected"),
                 ("eventTime", "2026-08-11T14:40:00.1234567Z", "Revision schema rejected"))
        for field, value, message in cases:
            with self.subTest(field=field, value=value):
                document = copy.deepcopy(original)
                document["revisions"][0][field] = value
                self.save(document)
                with self.assertRaisesRegex(ValueError, message):
                    validate_revisions(self.root)

    def test_fixture_provenance_cannot_postdate_generation(self):
        self.reject_revision(lambda doc: doc["provenance"].update(capturedAt="2026-08-19T00:00:00Z"),
                             "provenance is later than fixture generation")

    def test_sequence_gaps_and_wrong_predecessor_are_rejected(self):
        original = self.document()
        for field, value in (("sequenceNumber", 3), ("supersedesRevisionId", "foreign-revision")):
            with self.subTest(field=field):
                document = copy.deepcopy(original)
                document["revisions"][1][field] = value
                self.save(document)
                with self.assertRaisesRegex(ValueError, "Broken revision chain"):
                    validate_revisions(self.root)

    def test_cancellation_must_be_terminal(self):
        document = self.document()
        later = copy.deepcopy(document["revisions"][0])
        later.update(revisionId="demo-revision-third", providerEventId="fixture-revision-third",
                     sequenceNumber=3, supersedesRevisionId=document["revisions"][1]["revisionId"],
                     eventTime="2026-08-11T15:10:00Z", processingTime="2026-08-11T15:12:00Z",
                     capturedAt="2026-08-11T15:12:00Z")
        document["revisions"].append(later)
        self.save(document)
        with self.assertRaisesRegex(ValueError, "Cancellation must terminate"):
            validate_revisions(self.root)

    def test_event_time_cannot_move_backwards_between_revisions(self):
        self.reject_revision(lambda doc: doc["revisions"][1].update(eventTime="2026-08-11T14:30:00Z"),
                             "Revision event time moved backwards")

    def test_required_demo_correction_cancellation_coverage_is_retained(self):
        self.reject_revision(lambda doc: doc["revisions"].pop(), "must exercise both correction and cancellation")

    def test_required_demo_types_must_occur_in_one_correction_then_cancellation_chain(self):
        self.reject_revision(lambda doc: doc["revisions"][1].update(
            callId="demo-call-001", sequenceNumber=1, supersedesRevisionId=None),
            "At least one DEMO lineage must exercise correction then cancellation")

    def test_explicit_closed_fields_survive_schema_additional_properties_weakening(self):
        schema_path = self.root / "schemas/analyst-call-revision.schema.json"
        schema = json.loads(schema_path.read_text(encoding="utf-8"))
        schema["additionalProperties"] = True
        schema_path.write_text(json.dumps(schema), encoding="utf-8")
        self.reject_revision(lambda doc: doc["revisions"][0].update(unreviewed="field"),
                             "Revision is not a closed canonical record")

    def test_corrected_terms_remain_closed_even_if_schema_is_weakened(self):
        schema_path = self.root / "schemas/analyst-call-revision.schema.json"
        schema = json.loads(schema_path.read_text(encoding="utf-8"))
        schema["$defs"]["correctedTerms"]["additionalProperties"] = True
        schema_path.write_text(json.dumps(schema), encoding="utf-8")
        self.reject_revision(lambda doc: doc["revisions"][0]["correctedTerms"].update(unreviewed="field"),
                             "Corrected terms are not closed")

    def test_malformed_record_collections_are_rejected_before_indexing(self):
        original = self.document(CALL_FILE)
        for field, value in (("calls", {}), ("calls", [None]), ("sourceReferences", "not an array")):
            with self.subTest(field=field, value=value):
                document = copy.deepcopy(original)
                document[field] = value
                self.save(document, CALL_FILE)
                with self.assertRaisesRegex(ValueError, "array of records"):
                    validate_revisions(self.root)

    def test_linked_revision_fixture_is_not_followed(self):
        target = self.root / "fixtures/v1" / REVISION_FILE
        outside = self.root / "outside.json"
        target.replace(outside)
        try:
            target.symlink_to(outside)
        except OSError as error:
            self.skipTest(f"Symlink creation unavailable: {type(error).__name__}")
        with self.assertRaises(ValueError):
            validate_revisions(self.root)


if __name__ == "__main__":
    unittest.main()
