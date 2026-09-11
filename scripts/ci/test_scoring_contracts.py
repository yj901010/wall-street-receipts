"""Current-checkout scoring custody; API tests execute these exact reviewed bytes."""
import hashlib
from pathlib import Path
import tempfile
import unittest
import yaml
from jsonschema import Draft202012Validator
import scoring_contracts as scoring
import run_contracts as bridge
from current_contracts import blob_record

SOURCE = Path(__file__).resolve().parents[2]


class ScoringCustodyTests(unittest.TestCase):
    def setUp(self):
        directory = tempfile.TemporaryDirectory(prefix="wsr-scoring-custody-")
        self.addCleanup(directory.cleanup)
        self.root = Path(directory.name)
        self.raw = {p: (SOURCE / p).read_bytes().replace(b"\r\n", b"\n") for p in scoring.SCORING_PATHS}
        self.current = {p: blob_record(raw) for p, raw in self.raw.items()}
        self.baseline = dict(scoring.EDITED_BASELINE_RECORDS)
        for relative, raw in self.raw.items():
            path = self.root / relative
            path.parent.mkdir(parents=True, exist_ok=True)
            path.write_bytes(raw)

    def test_exact_forty_seven_additions_and_one_closed_link_edit(self):
        self.assertEqual(len(scoring.SCORING_PATHS), 48)
        self.assertFalse(scoring.SCORING_PATHS & (bridge.FIXED_CI_PATHS | bridge.CPI_PATHS | bridge.NAVIGATION_PATHS))
        self.assertEqual(sum("/application/scoring/" in p for p in scoring.SCORING_PATHS), 25)
        self.assertIn("contracts/scoring-receipts.openapi.yaml", scoring.SCORING_PATHS)
        self.assertIn("apps/api/src/main/resources/db/migration/V12__demo_scoring_receipts.sql", scoring.SCORING_PATHS)
        self.assertEqual(scoring.verify_scoring(SOURCE, self.baseline, self.current), self.current)
        self.assertEqual(scoring.verify_scoring(self.root, self.baseline, self.baseline), self.baseline)
        self.assertEqual(set(self.baseline), {"apps/web/src/app/calls/[id]/page.tsx"})
        for path in ("SCORING_RECEIPTS.md", "apps/web/e2e/scoring-receipts.spec.ts",
                     "apps/web/scoring/tests/receipts.spec.ts", "apps/web/src/lib/scoring-receipts.server.ts"):
            self.assertIn(path, scoring.SCORING_PATHS)

    def test_each_current_byte_and_committed_mode_type_object_is_required(self):
        for relative, raw in self.raw.items():
            with self.subTest(path=relative):
                self.assertEqual(hashlib.sha256(raw).hexdigest(), scoring.CONTENT_SHA256[relative])
                for wrong in ("100644 blob " + "f" * 40, self.current[relative].replace("100644", "100755"),
                              self.current[relative].replace("100644", "120000")):
                    with self.assertRaisesRegex(ValueError, "Unreviewed committed"):
                        scoring.verify_scoring(self.root, self.baseline, {**self.current, relative: wrong})
                path = self.root / relative
                path.write_bytes(raw + b"// altered\n")
                with self.assertRaisesRegex(ValueError, "Unreviewed current"):
                    scoring.verify_scoring(self.root, self.baseline, self.current)
                path.unlink()
                with self.assertRaisesRegex(ValueError, "missing or linked"):
                    scoring.verify_scoring(self.root, self.baseline, self.current)
                path.write_bytes(raw)

    def test_additions_cannot_hide_existing_or_neighboring_product_paths(self):
        relative = next(iter(scoring.SCORING_PATHS - self.baseline.keys()))
        with self.assertRaisesRegex(ValueError, "frozen baseline"):
            scoring.verify_scoring(self.root, {**self.baseline, relative: self.current[relative]}, self.current)
        for neighbor in ("apps/api/src/main/java/com/wallstreetreceipts/api/application/scoring/Unreviewed.java",
                         "apps/api/src/main/java/com/wallstreetreceipts/api/domain/outcome/CallOutcome.java"):
            current = {**self.current, neighbor: "100644 blob " + "a" * 40}
            adjusted = scoring.verify_scoring(self.root, self.baseline, current)
            with self.assertRaisesRegex(ValueError, "Product tree differs"):
                bridge.compare_product_trees(adjusted, current, frozenset())

    def test_crlf_is_normalized_but_bom_is_not_accepted(self):
        for relative, raw in self.raw.items():
            (self.root / relative).write_bytes(raw.replace(b"\n", b"\r\n"))
        self.assertEqual(scoring.verify_scoring(self.root, self.baseline, self.current), self.current)
        relative = next(iter(self.raw))
        (self.root / relative).write_bytes(b"\xef\xbb\xbf" + self.raw[relative])
        with self.assertRaisesRegex(ValueError, "Unreviewed current"):
            scoring.verify_scoring(self.root, self.baseline, self.current)

    def test_existing_call_page_cannot_be_deleted_or_change_its_predecessor(self):
        relative = next(iter(self.baseline))
        deleted = dict(self.current)
        del deleted[relative]
        with self.assertRaisesRegex(ValueError, "Unreviewed committed"):
            scoring.verify_scoring(self.root, self.baseline, deleted)
        with self.assertRaisesRegex(ValueError, "frozen baseline"):
            scoring.verify_scoring(self.root, {relative: self.current[relative]}, self.current)
        with self.assertRaisesRegex(ValueError, "frozen baseline"):
            scoring.verify_scoring(self.root, {}, self.current)

    def test_existing_call_page_is_only_the_explicit_demo_receipt_link(self):
        relative = next(iter(self.baseline))
        old = bridge.git(SOURCE, "show", bridge.BASELINE + ":" + relative)
        self.assertEqual(blob_record(old), self.baseline[relative])
        new = self.raw[relative]
        added = b'''        {call.dataMode === "DEMO" && <p><Link href={`/calls/${encodeURIComponent(call.callId)}/scoring-receipts`} prefetch={false}>
          {locale === "ko" ? "DEMO \xed\x8f\x89\xea\xb0\x80 \xea\xb8\xb0\xeb\xa1\x9d" : "DEMO scoring receipts"}
        </Link></p>}
'''
        self.assertEqual(new.count(added), 1)
        self.assertEqual(new.replace(added, b""), old)

    def test_additive_contract_has_only_two_reads_and_explicit_partial_scope(self):
        contract = yaml.safe_load((SOURCE / "contracts/scoring-receipts.openapi.yaml").read_text(encoding="utf-8"))
        self.assertEqual(contract["openapi"], "3.1.0")
        self.assertEqual(set(contract["paths"]), {
            "/v1/calls/{callId}/scoring-receipts", "/v1/calls/{callId}/scoring-receipts/{receiptId}"})
        for operation in contract["paths"].values():
            self.assertEqual(set(operation), {"parameters", "get"})
            self.assertEqual(set(operation["get"]["responses"]), {"200", "400", "404", "503"})
        for schema in contract["components"]["schemas"].values():
            Draft202012Validator.check_schema(schema)
        receipt = contract["components"]["schemas"]["Receipt"]
        self.assertEqual(set(receipt["required"]), set(receipt["properties"]))
        self.assertFalse(receipt["additionalProperties"])
        for name, value in {"dataMode": "DEMO", "scope": "PARTIAL_ENDPOINT", "dataComplete": False,
                            "snapshotRole": "ORIGINAL_CALL_CONTEXT_ONLY_NOT_PRICE_SOURCE"}.items():
            self.assertEqual(receipt["properties"][name], {"const": value})

    def test_metric_contract_rejects_missing_as_zero_and_ambiguous_value_types(self):
        contract = yaml.safe_load((SOURCE / "contracts/scoring-receipts.openapi.yaml").read_text(encoding="utf-8"))
        validator = Draft202012Validator(contract["components"]["schemas"]["Metric"])
        available = {"state": "AVAILABLE", "decimalValue": "0.200000000000", "booleanValue": None, "reasons": []}
        validator.validate(available)
        validator.validate({**available, "decimalValue": None, "booleanValue": False})
        for state in ("PENDING", "UNAVAILABLE", "NOT_APPLICABLE"):
            missing = {"state": state, "decimalValue": None, "booleanValue": None, "reasons": ["EVIDENCE_MISSING"]}
            validator.validate(missing)
            self.assertFalse(validator.is_valid({**missing, "decimalValue": "0.000000000000"}))
            self.assertFalse(validator.is_valid({**missing, "booleanValue": False}))
        for mutation in ({"booleanValue": True}, {"decimalValue": None}, {"decimalValue": 0}, {"reasons": ["MISSING"]}):
            self.assertFalse(validator.is_valid({**available, **mutation}))
