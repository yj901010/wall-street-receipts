#!/usr/bin/env python3
"""Mutation tests over disposable copies of current outcome evidence only."""

from __future__ import annotations

from copy import deepcopy
from decimal import Decimal, localcontext
import hashlib
import json
from pathlib import Path
import shutil
import tempfile
import unittest
from unittest.mock import patch

from jsonschema import SchemaError, ValidationError

from fixture_contracts_common import validator
from fixture_outcomes import validate_outcomes


REPOSITORY = Path(__file__).resolve().parents[2]
OUTCOMES = "fixtures/v1/call-outcomes.json"
OUTCOME_SCHEMA = "schemas/call-outcome.schema.json"
METHODOLOGY_SCHEMA = "schemas/scoring-methodology.schema.json"
EXPECTED = {"methodologyCount": 2, "outcomeCount": 4, "lineageCount": 3}
REJECTION = (ValueError, ValidationError, SchemaError)


class OutcomeFixtureTests(unittest.TestCase):
    def setUp(self):
        self.temporary = tempfile.TemporaryDirectory(prefix="wsr-outcome-contract-")
        self.addCleanup(self.temporary.cleanup)
        self.root = Path(self.temporary.name)
        for directory in ("fixtures/v1", "schemas"):
            shutil.copytree(REPOSITORY / directory, self.root / directory)

    def document(self, relative=OUTCOMES):
        return json.loads((self.root / relative).read_text(encoding="utf-8"))

    def write(self, document, relative=OUTCOMES):
        (self.root / relative).write_text(json.dumps(document, indent=2) + "\n", encoding="utf-8")

    def rejects(self, mutation, relative=OUTCOMES):
        destination = self.root / relative
        original = destination.read_bytes()
        document = self.document(relative)
        mutation(document)
        self.write(document, relative)
        try:
            with self.assertRaises(REJECTION):
                validate_outcomes(self.root)
        finally:
            destination.write_bytes(original)

    def test_current_evidence_passes_read_only_without_process_or_cwd_changes(self):
        files = [path for directory in ("fixtures/v1", "schemas")
                 for path in (self.root / directory).rglob("*") if path.is_file()]
        before = {path: hashlib.sha256(path.read_bytes()).digest() for path in files}
        with patch("os.chdir", side_effect=AssertionError("cwd mutation forbidden")), \
                patch("subprocess.run", side_effect=AssertionError("process forbidden")), \
                patch("socket.create_connection", side_effect=AssertionError("network forbidden")):
            self.assertEqual(validate_outcomes(self.root), EXPECTED)
        self.assertEqual(before, {path: hashlib.sha256(path.read_bytes()).digest() for path in files})

    def test_malformed_json_duplicate_keys_and_nonfinite_are_rejected(self):
        original = (self.root / OUTCOMES).read_text(encoding="utf-8")
        for corrupted in ("{", original.replace('"schemaVersion": "1.0.0",',
                                                '"schemaVersion": "1.0.0", "schemaVersion": "1.0.0",', 1),
                          original.replace('"assetReturn": null', '"assetReturn": NaN', 1)):
            with self.subTest(corrupted=corrupted[:30]):
                (self.root / OUTCOMES).write_text(corrupted, encoding="utf-8")
                with self.assertRaises(REJECTION):
                    validate_outcomes(self.root)
        (self.root / OUTCOMES).write_text(original, encoding="utf-8")

    def test_fixture_envelope_versions_mode_and_disclaimer_are_exact(self):
        for mutation in (
            lambda item: item.update(extra=True),
            lambda item: item.pop("disclaimer"),
            lambda item: item.update(schemaVersion="2.0.0"),
            lambda item: item.update(fixtureVersion="v2"),
            lambda item: item.update(dataMode="REALTIME"),
            lambda item: item.update(disclaimer="Metrics are calculated"),
            lambda item: item.update(methodologies=None),
            lambda item: item.update(outcomes={}),
        ):
            with self.subTest(mutation=mutation):
                self.rejects(mutation)

    def test_provenance_identity_type_synthetic_license_and_sources_are_exact(self):
        mutations = [
            lambda item: item["provenance"].update(id="other"),
            lambda item: item["provenance"].update(sourceType="OBSERVED"),
            lambda item: item["provenance"].update(synthetic=False),
            lambda item: item["provenance"].update(licenseClass="PUBLIC"),
            lambda item: item["provenance"].update(extra=True),
            lambda item: item["provenance"].update(sourcePaths=[]),
            lambda item: item["provenance"].update(sourcePaths="schemas/call-outcome.schema.json"),
            lambda item: item["provenance"]["sourcePaths"].append(item["provenance"]["sourcePaths"][0]),
            lambda item: item["outcomes"][0].update(provenanceId="other"),
        ]
        for mutation in mutations:
            with self.subTest(mutation=mutation):
                self.rejects(mutation)

    def test_provenance_and_outcome_future_timestamps_fail(self):
        for mutation in (
            lambda item: item["provenance"].update(capturedAt="2026-08-19T00:00:00Z"),
            lambda item: item["outcomes"][0].update(processingTime="2026-08-19T00:00:00Z", capturedAt="2026-08-19T00:00:00Z"),
            lambda item: item["outcomes"][0].update(capturedAt="2026-08-19T00:00:00Z"),
            lambda item: item["outcomes"][0].update(eventTime="2026-08-12T00:00:00Z"),
            lambda item: item["outcomes"][0].update(capturedAt="2026-08-11T20:00:00Z"),
        ):
            with self.subTest(mutation=mutation):
                self.rejects(mutation)

    def test_noncanonical_timestamp_offsets_precision_and_invalid_dates_fail(self):
        for value in ("2026-08-11T20:01:00+00:00", "2026-08-11T20:01:00.0000001Z",
                      "2026-02-30T20:01:00Z", "2026-08-11", "2026-08-11T20:01:00"):
            with self.subTest(value=value):
                self.rejects(lambda item: item["outcomes"][0].update(capturedAt=value))

    def test_every_missing_metric_remains_null_including_invented_zero_and_false(self):
        metrics = ("assetReturn", "benchmarkReturn", "sectorReturn", "alpha", "sectorAlpha",
                   "mfe", "mae", "targetHit", "directionalWin", "targetError")
        for metric in metrics:
            with self.subTest(metric=metric):
                value = False if metric in ("targetHit", "directionalWin") else 0
                self.rejects(lambda item: item["outcomes"][0].update({metric: value}))

    def test_methodology_count_order_identity_and_hash_evidence_are_fixed(self):
        for mutation in (
            lambda item: item["methodologies"].pop(),
            lambda item: item["methodologies"].reverse(),
            lambda item: item["methodologies"].__setitem__(1, deepcopy(item["methodologies"][0])),
            lambda item: item["methodologies"][0].update(definitionHash="a" * 64),
            lambda item: item["methodologies"][0].update(status="ACTIVE"),
            lambda item: item["methodologies"][0].update(capturedAt="2026-08-19T00:00:00Z"),
        ):
            with self.subTest(mutation=mutation):
                self.rejects(mutation)

    def test_outcome_methodology_hash_and_registry_reference_fail_closed(self):
        for mutation in (
            lambda item: item["outcomes"][0].update(methodologyDefinitionHash="a" * 64),
            lambda item: item["outcomes"][0].update(methodologyVersion="3.0.0"),
            lambda item: item["outcomes"][0].update(methodologyId="missing"),
            lambda item: item["outcomes"][3].update(processingTime="2026-08-17T00:00:00Z"),
        ):
            with self.subTest(mutation=mutation):
                self.rejects(mutation)

    def test_duplicate_outcome_ids_and_natural_inputs_fail(self):
        self.rejects(lambda item: item["outcomes"][1].update(outcomeId=item["outcomes"][0]["outcomeId"]))
        self.rejects(lambda item: item["outcomes"][1].update(inputFingerprint=item["outcomes"][0]["inputFingerprint"]))

    def test_foreign_identity_collision_and_unknown_call_fail(self):
        self.rejects(lambda item: item["outcomes"][0].update(outcomeId="demo-call-001"))
        self.rejects(lambda item: item["outcomes"][0].update(callId="missing"))

    def test_duplicate_referenced_identities_do_not_silently_overwrite(self):
        for relative, collection in (("fixtures/v1/analyst-calls.json", "calls"),
                                     ("fixtures/v1/market-snapshots.json", "snapshots"),
                                     ("fixtures/v1/analyst-call-revisions.json", "revisions")):
            with self.subTest(collection=collection):
                self.rejects(lambda item: item[collection].append(deepcopy(item[collection][0])), relative)

    def test_cross_lineage_parent_missing_parent_and_sequence_gap_fail(self):
        for mutation in (
            lambda item: item["outcomes"][1].update(supersedesOutcomeId=item["outcomes"][2]["outcomeId"]),
            lambda item: item["outcomes"][1].update(supersedesOutcomeId="missing"),
            lambda item: item["outcomes"][1].update(supersedesOutcomeId=None),
            lambda item: item["outcomes"][1].update(sequenceNumber=3),
            lambda item: item["outcomes"][0].update(supersedesOutcomeId=item["outcomes"][1]["outcomeId"]),
            lambda item: item["outcomes"][1].update(horizon="W1"),
        ):
            with self.subTest(mutation=mutation):
                self.rejects(mutation)

    def test_lineage_time_cannot_move_backwards(self):
        self.rejects(lambda item: item["outcomes"][1].update(
            processingTime="2026-08-11T20:00:30Z", capturedAt="2026-08-11T20:00:30Z"))

    def test_only_pending_incomplete_statuses_with_exact_reason_completeness(self):
        for mutation in (
            lambda item: item["outcomes"][0].update(reasonCode="HORIZON_NOT_REACHED"),
            lambda item: item["outcomes"][0].update(dataComplete=True),
            lambda item: item["outcomes"][0].update(evaluationStatus="CALCULATED", reasonCode=None, dataComplete=True),
            lambda item: item["outcomes"][2].update(evaluationStatus="INCOMPLETE", reasonCode="HORIZON_DATA_MISSING"),
            lambda item: item["outcomes"].pop(),
        ):
            with self.subTest(mutation=mutation):
                self.rejects(mutation)

    def test_future_captured_call_and_snapshot_fail(self):
        for relative, collection in (("fixtures/v1/analyst-calls.json", "calls"),
                                     ("fixtures/v1/market-snapshots.json", "snapshots")):
            with self.subTest(collection=collection):
                self.rejects(lambda item: item[collection][0].update(
                    processingTime="2026-08-12T00:00:00Z", capturedAt="2026-08-12T00:00:00Z"), relative)

    def test_snapshot_identity_immutability_and_time_order_fail_closed(self):
        self.rejects(lambda item: item["outcomes"][0].update(snapshotId="missing"))
        for mutation in (
            lambda item: item["snapshots"][0].update(callId="demo-call-002"),
            lambda item: item["snapshots"][0].update(immutable=False),
            lambda item: item["snapshots"][0].update(eventTime="2026-08-18T00:00:00Z"),
        ):
            self.rejects(mutation, "fixtures/v1/market-snapshots.json")

    def test_revision_and_cancellation_identity_or_wrong_type_fail(self):
        self.rejects(lambda item: item["outcomes"][0].update(basisRevisionId="missing"))
        self.rejects(lambda item: item["outcomes"][0].update(basisRevisionId="demo-call-revision-001"))
        self.rejects(lambda item: item["outcomes"][0].update(cancellationRevisionId="demo-call-revision-002"))
        self.rejects(lambda item: item["outcomes"][0].update(
            evaluationStatus="EXCLUDED", reasonCode="CALL_CANCELLED", cancellationRevisionId=None))

    def test_future_same_call_basis_revision_fails(self):
        document = self.document()
        document["outcomes"][0]["basisRevisionId"] = "demo-call-revision-001"
        self.write(document)
        revisions = self.document("fixtures/v1/analyst-call-revisions.json")
        revisions["revisions"][0].update(callId="demo-call-001", processingTime="2026-08-12T00:00:00Z", capturedAt="2026-08-12T00:00:00Z")
        self.write(revisions, "fixtures/v1/analyst-call-revisions.json")
        with self.assertRaisesRegex(ValueError, "future-captured revision"):
            validate_outcomes(self.root)

    def test_manifest_missing_extra_and_duplicate_paths_fail(self):
        self.rejects(lambda item: item["files"].pop(), "fixtures/v1/manifest.json")
        self.rejects(lambda item: item["files"].append({"path": "missing.json"}), "fixtures/v1/manifest.json")
        self.rejects(lambda item: item["files"].append(deepcopy(item["files"][0])), "fixtures/v1/manifest.json")

    def test_schema_draft_closure_required_fields_and_timestamp_weakening_fail(self):
        for relative in (OUTCOME_SCHEMA, METHODOLOGY_SCHEMA):
            for mutation in (
                lambda item: item.update({"$schema": "http://json-schema.org/draft-07/schema#"}),
                lambda item: item.update(additionalProperties=True),
                lambda item: item["required"].pop(),
                lambda item: item["properties"].update(extra={}),
                lambda item: item["$defs"]["utcInstant"].pop("format"),
                lambda item: item["$defs"]["utcInstant"].update(pattern=".*"),
                lambda item: item["properties"]["capturedAt"].update({"$ref": "#/$defs/identifier"}),
            ):
                with self.subTest(relative=relative, mutation=mutation):
                    self.rejects(mutation, relative)

    def test_schema_cancellation_evidence_condition_cannot_be_removed(self):
        self.rejects(lambda item: item["allOf"].__delitem__(1), OUTCOME_SCHEMA)

    def test_ratio_and_target_error_schema_precision_bounds_and_refs_cannot_weaken(self):
        for mutation in (
            lambda item: item["$defs"]["nullableRatio"].update(multipleOf=1e-13),
            lambda item: item["$defs"]["nullableRatio"].pop("exclusiveMinimum"),
            lambda item: item["$defs"]["nullableRatio"].pop("exclusiveMaximum"),
            lambda item: item["$defs"]["nullableRatio"].pop("type"),
            lambda item: item["properties"]["assetReturn"].update({"$ref": "#/$defs/identifier"}),
            lambda item: item["properties"]["targetError"]["oneOf"][0].update(minimum=-1),
            lambda item: item["properties"]["targetError"]["oneOf"][0].pop("multipleOf"),
            lambda item: item["properties"]["targetError"]["oneOf"].append({}),
        ):
            with self.subTest(mutation=mutation):
                self.rejects(mutation, OUTCOME_SCHEMA)

    def test_quantum_changes_smaller_than_float_resolution_are_rejected(self):
        path = self.root / OUTCOME_SCHEMA
        original = path.read_text(encoding="utf-8")
        self.assertIn('"multipleOf": 0.000000000001', original)
        path.write_text(original.replace('"multipleOf": 0.000000000001',
                                        '"multipleOf": 0.0000000000010000000000000001'), encoding="utf-8")
        with self.assertRaisesRegex(ValueError, "multipleOf 1e-12"):
            validate_outcomes(self.root)

    def test_schema_multiple_of_is_exact_near_magnitude_limit_and_low_context(self):
        outcome_validator = validator(self.root, OUTCOME_SCHEMA)
        ratio_validator = outcome_validator.evolve(schema=outcome_validator.schema["$defs"]["nullableRatio"])
        target_validator = outcome_validator.evolve(schema=outcome_validator.schema["properties"]["targetError"])
        for precision in (6, 28):
            with self.subTest(precision=precision), localcontext() as context:
                context.prec = precision
                for value in (Decimal("0.07"), Decimal("1E-12"),
                              Decimal("99999999999999999999999999.000000000001")):
                    ratio_validator.validate(value)
                    target_validator.validate(value)
                for value in (Decimal("1E-13"), Decimal("0.0700000000001"),
                              Decimal("99999999999999999999999999.0000000000001"),
                              Decimal("100000000000000000000000000")):
                    with self.assertRaises(ValidationError):
                        ratio_validator.validate(value)
                    with self.assertRaises(ValidationError):
                        target_validator.validate(value)
                ratio_validator.validate(Decimal("-99999999999999999999999999.000000000001"))
                with self.assertRaises(ValidationError):
                    ratio_validator.validate(Decimal("-100000000000000000000000000"))
                with self.assertRaises(ValidationError):
                    target_validator.validate(Decimal("-0.000000000001"))


if __name__ == "__main__":
    unittest.main()
