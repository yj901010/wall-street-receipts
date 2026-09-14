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

    def test_exact_ninety_seven_additions_and_one_closed_link_edit(self):
        self.assertEqual(len(scoring.SCORING_PATHS), 98)
        self.assertFalse(scoring.SCORING_PATHS & (bridge.FIXED_CI_PATHS | bridge.CPI_PATHS | bridge.NAVIGATION_PATHS))
        self.assertEqual(sum("/application/scoring/" in p for p in scoring.SCORING_PATHS), 46)
        self.assertIn("contracts/scoring-receipts.openapi.yaml", scoring.SCORING_PATHS)
        self.assertIn("apps/api/src/main/resources/db/migration/V12__demo_scoring_receipts.sql", scoring.SCORING_PATHS)
        self.assertIn("apps/api/src/main/resources/db/migration/V13__demo_comparative_scoring_receipts.sql", scoring.SCORING_PATHS)
        self.assertIn("contracts/comparative-scoring-receipts.openapi.yaml", scoring.SCORING_PATHS)
        self.assertEqual(scoring.verify_scoring(SOURCE, self.baseline, self.current), self.current)
        self.assertEqual(scoring.verify_scoring(self.root, self.baseline, self.baseline), self.baseline)
        self.assertEqual(set(self.baseline), {"apps/web/src/app/calls/[id]/page.tsx"})
        for path in ("SCORING_RECEIPTS.md", "apps/web/e2e/scoring-receipts.spec.ts",
                     "apps/web/scoring/tests/receipts.spec.ts", "apps/web/src/lib/scoring-receipts.server.ts"):
            self.assertIn(path, scoring.SCORING_PATHS)

    def test_target_hit_adds_eight_closed_paths_and_preserves_every_previous_scoring_byte(self):
        base = "20477b72a822926fbcb009f770cca1bb5c44e7af"
        prefix = "apps/api/src/"
        suffix = "/java/com/wallstreetreceipts/api/application/scoring/"
        added = {prefix + kind + suffix + name + ".java" for kind, names in (
            ("main", ("TargetHitScoringInput", "TargetHitScoringInputCodec", "TargetHitScoringEvaluator", "TargetHitScoringMethodology")),
            ("test", ("TargetHitScoringFixture", "TargetHitScoringInputTest", "TargetHitScoringEvaluatorTest", "TargetHitScoringInputCodecTest")),
        ) for name in names}
        self.assertEqual(len(added), 8)
        self.assertTrue(added <= scoring.SCORING_PATHS)
        previous = scoring.SCORING_PATHS - added - scoring.TARGET_HIT_RECEIPT_ADDITIONS
        self.assertEqual(len(previous), 79)
        for relative in previous:
            with self.subTest(path=relative):
                actual = self.raw[relative]
                if relative in scoring.TARGET_HIT_RECEIPT_PREVIOUS_RECORDS:
                    actual = bridge.git(SOURCE, "show", scoring.TARGET_HIT_RECEIPT_BASE + ":" + relative)
                    self.assertEqual(blob_record(actual), scoring.TARGET_HIT_RECEIPT_PREVIOUS_RECORDS[relative])
                self.assertEqual(actual, bridge.git(SOURCE, "show", base + ":" + relative).replace(b"\r\n", b"\n"))
        for relative in added:
            self.assertEqual(bridge.git(SOURCE, "ls-tree", base, "--", relative), b"")

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
        new = bridge.git(SOURCE, "show", scoring.COMPARATIVE_AUDIT_BASE + ":" + relative)
        self.assertEqual(blob_record(new), scoring.COMPARATIVE_AUDIT_PREVIOUS_RECORDS[relative])
        added = b'''        {call.dataMode === "DEMO" && <p><Link href={`/calls/${encodeURIComponent(call.callId)}/scoring-receipts`} prefetch={false}>
          {locale === "ko" ? "DEMO \xed\x8f\x89\xea\xb0\x80 \xea\xb8\xb0\xeb\xa1\x9d" : "DEMO scoring receipts"}
        </Link></p>}
'''
        self.assertEqual(new.count(added), 1)
        self.assertEqual(new.replace(added, b""), old)

    def test_comparative_link_is_the_only_new_edit_and_requires_exact_predecessor(self):
        relative = next(iter(self.baseline))
        self.assertEqual(set(scoring.COMPARATIVE_AUDIT_PREVIOUS_RECORDS), {relative})
        old = bridge.git(SOURCE, "show", scoring.COMPARATIVE_AUDIT_BASE + ":" + relative)
        self.assertEqual(blob_record(old), scoring.COMPARATIVE_AUDIT_PREVIOUS_RECORDS[relative])
        added = "        {call.dataMode === \"DEMO\" && <p><Link href={`/calls/${encodeURIComponent(call.callId)}/comparative-scoring-receipts`} prefetch={false}>\n          {locale === \"ko\" ? \"DEMO 비교 평가 기록\" : \"DEMO comparative scoring receipts\"}\n        </Link></p>}\n".encode("utf-8")
        self.assertEqual(self.raw[relative].count(added), 1)
        self.assertEqual(self.raw[relative].replace(added, b""), old)
        previous = {**self.current, **scoring.COMPARATIVE_AUDIT_PREVIOUS_RECORDS}
        self.assertEqual(scoring.verify_scoring(self.root, self.baseline, previous), previous)
        (self.root / relative).write_bytes(old)
        with self.assertRaisesRegex(ValueError, "Unreviewed current"):
            scoring.verify_scoring(self.root, self.baseline, previous)

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

    def test_only_old_upgrade_target_is_pinned_without_weakening_its_assertions(self):
        self.assertEqual(len(scoring.COMPARATIVE_RECEIPT_PREVIOUS_RECORDS), 1)
        previous = {**self.current, **scoring.COMPARATIVE_RECEIPT_PREVIOUS_RECORDS}
        self.assertEqual(scoring.verify_scoring(self.root, self.baseline, previous), previous)
        for relative, record in scoring.COMPARATIVE_RECEIPT_PREVIOUS_RECORDS.items():
            old = bridge.git(SOURCE, "show", scoring.COMPARATIVE_RECEIPT_BASE + ":" + relative)
            self.assertEqual(blob_record(old), record)
            exact = b'var flyway = Flyway.configure().dataSource(db.getJdbcUrl(), db.getUsername(), db.getPassword()).load();'
            self.assertEqual(old.count(exact), 1)
            self.assertEqual(self.raw[relative], old.replace(exact, exact.replace(b'.load();', b'.target("12").load();')))
            (self.root / relative).write_bytes(old)
            with self.assertRaisesRegex(ValueError, "Unreviewed current"):
                scoring.verify_scoring(self.root, self.baseline, previous)

    def test_comparative_contract_is_additive_read_only_and_explicitly_incomplete(self):
        contract = yaml.safe_load((SOURCE / "contracts/comparative-scoring-receipts.openapi.yaml").read_text(encoding="utf-8"))
        self.assertEqual(contract["openapi"], "3.1.0")
        self.assertEqual(set(contract["paths"]), {"/v1/calls/{callId}/comparative-scoring-receipts",
                         "/v1/calls/{callId}/comparative-scoring-receipts/{receiptId}"})
        for route in contract["paths"].values():
            self.assertEqual(set(route), {"parameters", "get"})
            self.assertEqual(set(route["get"]["responses"]), {"200", "400", "404", "503"})
        schemas = contract["components"]["schemas"]
        for schema in schemas.values():
            Draft202012Validator.check_schema(schema)
        for name in ("Receipt", "ReferenceEvidence", "Level"):
            self.assertFalse(schemas[name]["additionalProperties"])
            self.assertEqual(set(schemas[name]["required"]), set(schemas[name]["properties"]))
        receipt = schemas["Receipt"]
        for name, value in {"scope": "PARTIAL_COMPARATIVE", "dataMode": "DEMO", "dataComplete": False,
                            "methodologyId": "wsr-demo-comparative-preview", "methodologyVersion": "1.0.0",
                            "methodologyDefinitionHash": "6fb2d737d177662ec072277f1e345ca10a2c9447d35353bc46c1f886669ad6d2"}.items():
            self.assertEqual(receipt["properties"][name], {"const": value})
        for name in ("benchmarkReturn", "sectorReturn"):
            self.assertEqual(receipt["properties"][name], {"$ref": "#/components/schemas/DecimalMetric"})
        for name in ("benchmarkEvidence", "sectorEvidence"):
            self.assertEqual(receipt["properties"][name]["oneOf"], [
                {"$ref": "#/components/schemas/ReferenceEvidence"}, {"type": "null"}])
        self.assertEqual(schemas["DecimalMetric"]["allOf"][1], {"properties": {"booleanValue": {"type": "null"}}})
        level = Draft202012Validator(schemas["Level"]["properties"]["value"])
        for value in ("4000", "0.000000000001", "99999999999999999999999999.999999999999"):
            level.validate(value)
        for value in (0, None, "-1", "4e3", "1x123", "1.0000000000001"):
            self.assertFalse(level.is_valid(value))
        old = yaml.safe_load((SOURCE / "contracts/scoring-receipts.openapi.yaml").read_text(encoding="utf-8"))
        self.assertEqual(schemas["Metric"], old["components"]["schemas"]["Metric"])

    def test_target_hit_storage_additions_and_closed_v13_upgrade_target(self):
        added = scoring.TARGET_HIT_RECEIPT_ADDITIONS
        self.assertEqual(len(added), 11)
        self.assertTrue(added <= scoring.SCORING_PATHS)
        self.assertEqual(sum(path.endswith(".java") for path in added), 9)
        self.assertIn("contracts/target-hit-scoring-receipts.openapi.yaml", added)
        self.assertIn("apps/api/src/main/resources/db/migration/V14__demo_target_hit_scoring_receipts.sql", added)
        for path in added:
            self.assertEqual(bridge.git(SOURCE, "ls-tree", scoring.TARGET_HIT_RECEIPT_BASE, "--", path), b"")
        previous = scoring.SCORING_PATHS - added
        self.assertEqual(len(previous), 87)
        self.assertEqual(len(scoring.TARGET_HIT_RECEIPT_PREVIOUS_RECORDS), 1)
        for path in previous:
            old = bridge.git(SOURCE, "show", scoring.TARGET_HIT_RECEIPT_BASE + ":" + path)
            if path in scoring.TARGET_HIT_RECEIPT_PREVIOUS_RECORDS:
                self.assertEqual(blob_record(old), scoring.TARGET_HIT_RECEIPT_PREVIOUS_RECORDS[path])
                exact = b'var flyway = Flyway.configure().dataSource(db.getJdbcUrl(), db.getUsername(), db.getPassword()).load();'
                self.assertEqual(old.count(exact), 1)
                self.assertEqual(self.raw[path], old.replace(exact, exact.replace(b'.load();', b'.target("13").load();')))
            else:
                self.assertEqual(self.raw[path], old)
        committed = {**self.current, **scoring.TARGET_HIT_RECEIPT_PREVIOUS_RECORDS}
        self.assertEqual(scoring.verify_scoring(self.root, self.baseline, committed), committed)
        path = next(iter(scoring.TARGET_HIT_RECEIPT_PREVIOUS_RECORDS))
        (self.root / path).write_bytes(bridge.git(SOURCE, "show", scoring.TARGET_HIT_RECEIPT_BASE + ":" + path))
        with self.assertRaisesRegex(ValueError, "Unreviewed current"):
            scoring.verify_scoring(self.root, self.baseline, committed)

    def test_target_hit_contract_exposes_only_partial_reads_and_selected_evidence(self):
        contract = yaml.safe_load((SOURCE / "contracts/target-hit-scoring-receipts.openapi.yaml").read_text(encoding="utf-8"))
        self.assertEqual(set(contract["paths"]), {"/v1/calls/{callId}/target-hit-scoring-receipts",
                         "/v1/calls/{callId}/target-hit-scoring-receipts/{receiptId}"})
        for route in contract["paths"].values():
            self.assertEqual(set(route), {"parameters", "get"})
            self.assertEqual(set(route["get"]["responses"]), {"200", "400", "404", "503"})
        schemas = contract["components"]["schemas"]
        for name, schema in schemas.items():
            Draft202012Validator.check_schema(schema)
            if schema.get("type") == "object":
                self.assertFalse(schema["additionalProperties"])
                self.assertEqual(set(schema["required"]), set(schema["properties"]))
        receipt = schemas["Receipt"]["properties"]
        for name, value in {"dataMode": "DEMO", "scope": "PARTIAL_TARGET_HIT", "dataComplete": False,
                            "methodologyId": "wsr-demo-target-hit-preview", "methodologyVersion": "1.0.0",
                            "methodologyDefinitionHash": "aaa8684e12a5d83df36eff5a15107828931526b36ccfcca71ac8f968804b1b1e"}.items():
            self.assertEqual(receipt[name], {"const": value})
        for name in ("assetReturn", "targetError", "benchmarkReturn", "sectorReturn"):
            self.assertEqual(receipt[name], {"$ref": "#/components/schemas/DecimalMetric"})
        for name in ("directionalWin", "targetHit"):
            self.assertEqual(receipt[name], {"$ref": "#/components/schemas/BooleanMetric"})
        self.assertNotIn("inputBytes", receipt)
        self.assertNotIn("windowCandidates", receipt)
        self.assertEqual(schemas["WindowEvidence"]["properties"]["attestationScope"],
                         {"const": "CALLER_ATTESTED_DEMO_CAUSAL_WINDOW_NOT_RAW_TRADE_VERIFICATION"})
        boolean = Draft202012Validator({**contract, "$ref": "#/components/schemas/BooleanMetric"})
        available = {"state": "AVAILABLE", "decimalValue": None, "booleanValue": False, "reasons": []}
        boolean.validate(available)
        self.assertFalse(boolean.is_valid({**available, "decimalValue": "0.000000000000", "booleanValue": None}))
        for state in ("PENDING", "UNAVAILABLE", "NOT_APPLICABLE"):
            absent = {**available, "state": state, "booleanValue": None, "reasons": ["MISSING"]}
            boolean.validate(absent)
            self.assertFalse(boolean.is_valid({**absent, "booleanValue": False}))
        level = Draft202012Validator(schemas["PositiveLevel"])
        for value in ("160", "0.000000000001", "99999999999999999999999999.999999999999"):
            level.validate(value)
        for value in (None, 160, "0", "0.000", "-1", "1e2", "01", "100000000000000000000000000"):
            self.assertFalse(level.is_valid(value))
