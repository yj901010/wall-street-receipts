"""Synthetic document inventories only; no provider, tick or rights approval."""
import ast
import copy
import hashlib
import importlib.util
import io
import json
from pathlib import Path
import stat
import subprocess
import sys
import tempfile
from types import SimpleNamespace
import unittest
from contextlib import redirect_stderr, redirect_stdout
from unittest.mock import patch

SOURCE = Path(__file__).resolve().parents[2]
SCRIPT = SOURCE / "scripts/review_raw_feed_intake.py"
SPEC = importlib.util.spec_from_file_location("wsr_raw_feed_intake", SCRIPT)
intake = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(intake)


def blank():
    return {"format": intake.FORMAT,
            "candidate": {"provider": None, "product": None, "primaryVenue": None}, "evidence": []}


def citation():
    return {"url": "https://docs.example.invalid/synthetic-only", "section": "TEST DOCUMENT ONLY",
            "revision": "SYNTHETIC-REVISION"}


def supplied():
    return {"format": intake.FORMAT,
            "candidate": {"provider": "SYNTHETIC PROVIDER", "product": "TEST ONLY", "primaryVenue": "TEST VENUE"},
            "evidence": [{"requirement": key, "references": [citation()]} for key in intake.REQUIREMENTS]}


def encoded(document):
    return json.dumps(document).encode("utf-8")


class RawFeedIntakeTests(unittest.TestCase):
    def assert_not_authorized(self, report):
        self.assertEqual(report["scope"], "DOCUMENT_INVENTORY_ONLY")
        for field in ("providerApproved", "rawCoverageVerified", "ingestionAllowed", "scoringEnabled"):
            self.assertIs(report[field], False)
        self.assertNotIn("dataComplete", report)

    def test_blank_example_reports_every_missing_item_without_price(self):
        raw = (SOURCE / "examples/raw-feed-intake/blank.json").read_bytes()
        report = intake.inspect_bytes(raw)
        self.assertEqual(json.loads(raw), blank())
        self.assertEqual(report["status"], "DOCUMENTS_MISSING")
        self.assertFalse(report["candidateIdentified"])
        self.assertEqual(report["missingRequirements"], ["CANDIDATE_IDENTITY", *intake.REQUIREMENTS])
        self.assertEqual(len(report["requirements"]), 15)
        self.assertTrue(all(r["referenceCount"] == 0 and r["status"] == "NOT_SUPPLIED" for r in report["requirements"]))
        self.assert_not_authorized(report)

    def test_all_references_only_prepare_human_review_never_approval(self):
        raw = encoded(supplied())
        report = intake.inspect_bytes(raw)
        self.assertEqual(report["status"], "READY_FOR_HUMAN_REVIEW")
        self.assertTrue(report["candidateIdentified"])
        self.assertEqual(report["missingRequirements"], [])
        self.assertEqual(report["inputSha256"], hashlib.sha256(raw).hexdigest())
        self.assert_not_authorized(report)
        output = json.dumps(report)
        for private_value in ("SYNTHETIC PROVIDER", "TEST ONLY", "TEST VENUE", "docs.example.invalid", "TEST DOCUMENT ONLY", "SYNTHETIC-REVISION"):
            self.assertNotIn(private_value, output)

    def test_each_missing_reference_and_empty_list_remain_missing(self):
        for key in intake.REQUIREMENTS:
            for empty in (True, False):
                with self.subTest(key=key, empty=empty):
                    value = supplied()
                    if empty:
                        next(v for v in value["evidence"] if v["requirement"] == key)["references"] = []
                    else:
                        value["evidence"] = [v for v in value["evidence"] if v["requirement"] != key]
                    report = intake.inspect_bytes(encoded(value))
                    self.assertEqual(report["status"], "DOCUMENTS_MISSING")
                    self.assertEqual(report["missingRequirements"], [key])
                    self.assert_not_authorized(report)

    def test_partial_candidate_is_not_identified(self):
        for key in supplied()["candidate"]:
            value = supplied()
            value["candidate"][key] = None
            report = intake.inspect_bytes(encoded(value))
            self.assertEqual(report["missingRequirements"], ["CANDIDATE_IDENTITY"])
            self.assert_not_authorized(report)

    def test_report_order_is_fixed_and_exact_bytes_are_fingerprinted(self):
        value = supplied()
        first = intake.inspect_bytes(encoded(value))
        value["evidence"].reverse()
        raw = encoded(value)
        second = intake.inspect_bytes(raw)
        self.assertNotEqual(first["inputSha256"], second["inputSha256"])
        first.pop("inputSha256"); second.pop("inputSha256")
        self.assertEqual(first, second)
        self.assertEqual(intake.inspect_bytes(raw), intake.inspect_bytes(raw))
        self.assertEqual(raw, encoded(value))

    def test_closed_object_shapes_at_every_level(self):
        for select in (lambda v: v, lambda v: v["candidate"], lambda v: v["evidence"][0],
                       lambda v: v["evidence"][0]["references"][0]):
            for operation in ("extra", "missing"):
                value = supplied()
                target = select(value)
                if operation == "extra": target["apiKey"] = "PRIVATE_SENTINEL"
                else: target.pop(next(iter(target)))
                with self.subTest(operation=operation), self.assertRaises(intake.IntakeError):
                    intake.inspect_bytes(encoded(value))

    def test_approval_or_market_values_cannot_be_submitted(self):
        for field in ("providerApproved", "rawCoverageVerified", "ingestionAllowed", "scoringEnabled", "ticks", "price", "dataComplete"):
            value = supplied(); value[field] = True
            with self.subTest(field=field), self.assertRaises(intake.IntakeError):
                intake.inspect_bytes(encoded(value))

    def test_wrong_root_candidate_and_collection_types_are_rejected(self):
        for wrong in (None, False, 0, "text", []):
            with self.subTest(root=wrong), self.assertRaises(intake.IntakeError):
                intake.inspect_bytes(encoded(wrong))
        for field in ("candidate", "evidence"):
            for wrong in (None, False, 1, "text"):
                value = supplied(); value[field] = wrong
                with self.subTest(field=field, value=wrong), self.assertRaises(intake.IntakeError):
                    intake.inspect_bytes(encoded(value))

    def test_unknown_format_requirement_and_duplicates_reject(self):
        mutations = []
        value = supplied(); value["format"] += "-unreviewed"; mutations.append(value)
        value = supplied(); value["evidence"][0]["requirement"] = "ANYTHING"; mutations.append(value)
        value = supplied(); value["evidence"][0]["requirement"] = []; mutations.append(value)
        value = supplied(); value["evidence"][1] = copy.deepcopy(value["evidence"][0]); mutations.append(value)
        value = supplied(); value["evidence"][0]["references"].append(citation()); mutations.append(value)
        for value in mutations:
            with self.subTest(value=value), self.assertRaises(intake.IntakeError):
                intake.inspect_bytes(encoded(value))

    def test_duplicate_json_members_reject_before_structural_review(self):
        for raw in (b'{"format":1,"format":2}', b'{"outer":{"url":1,"url":2}}'):
            with self.assertRaisesRegex(intake.IntakeError, "DUPLICATE_JSON_KEY"):
                intake.inspect_bytes(raw)

    def test_malformed_unicode_nonfinite_numbers_and_deep_json_fail_closed(self):
        for raw in (b"", b"\xff", b"\xef\xbb\xbf{}", b"{} trailing", b"NaN", b"Infinity", b"-Infinity",
                    b"[" * 2000 + b"]" * 2000, b"1" * 5000):
            with self.subTest(size=len(raw)), self.assertRaises(intake.IntakeError):
                intake.inspect_bytes(raw)

    def test_text_validation_has_no_silent_trimming_or_control_characters(self):
        for text in ("", " ", " provider", "provider ", "private\nvalue", "a\x00b", "a\x85b", "\ud800", "x" * 129, 1, False, []):
            value = supplied(); value["candidate"]["provider"] = text
            with self.subTest(text=repr(text)), self.assertRaises(intake.IntakeError):
                intake.inspect_bytes(encoded(value))

    def test_urls_cannot_carry_credentials_queries_or_local_paths(self):
        for url in ("http://example.invalid/a", "file:///private", "data:text/plain,secret", "https://u:p@example.invalid/a",
                    "https://example.invalid/a?token=PRIVATE", "https://example.invalid/a#PRIVATE",
                    "https://example.invalid:443/a", "https://example.invalid:wrong/a", "https://localhost/a",
                    "https://example..invalid/a", "https://-bad.invalid/a", "https://example.invalid\\a",
                    "https://example.invalid/a b", "https://example.invalid/한글", "https:///a", "https://%65xample.invalid/a"):
            value = supplied(); value["evidence"][0]["references"][0]["url"] = url
            with self.subTest(url=url), self.assertRaises(intake.IntakeError):
                intake.inspect_bytes(encoded(value))

    def test_bounded_lists_and_fields(self):
        for field, limit in (("url", 2048), ("section", 256), ("revision", 128)):
            value = supplied(); value["evidence"][0]["references"][0][field] = "x" * (limit + 1)
            with self.assertRaises(intake.IntakeError): intake.inspect_bytes(encoded(value))
        value = supplied(); value["evidence"] += [{"requirement": "TOO_MANY", "references": []}]
        with self.assertRaises(intake.IntakeError): intake.inspect_bytes(encoded(value))
        value = supplied()
        value["evidence"][0]["references"] = [{**citation(), "section": str(n)} for n in range(intake.MAX_REFERENCES)]
        self.assertEqual(intake.inspect_bytes(encoded(value))["requirements"][0]["referenceCount"], 5)
        value["evidence"][0]["references"].append({**citation(), "section": "sixth"})
        with self.assertRaises(intake.IntakeError): intake.inspect_bytes(encoded(value))

    def test_byte_limit_includes_whitespace(self):
        raw = encoded(blank())
        exact = raw + b" " * (intake.MAX_BYTES - len(raw))
        self.assertEqual(intake.inspect_bytes(exact)["inputSha256"], hashlib.sha256(exact).hexdigest())
        with self.assertRaisesRegex(intake.IntakeError, "INPUT_SIZE_LIMIT"):
            intake.inspect_bytes(exact + b" ")

    def test_stdlib_only_source_has_no_network_process_or_provider_import(self):
        tree = ast.parse(SCRIPT.read_text(encoding="utf-8"))
        imports = set()
        for node in ast.walk(tree):
            if isinstance(node, ast.Import): imports.update(alias.name for alias in node.names)
            if isinstance(node, ast.ImportFrom): imports.add(node.module)
        self.assertEqual(imports, {"__future__", "argparse", "hashlib", "json", "os", "pathlib", "re", "stat", "sys", "urllib.parse"})


class RawFeedIntakeCliTests(unittest.TestCase):
    def setUp(self):
        directory = tempfile.TemporaryDirectory(prefix="wsr-raw-intake-")
        self.addCleanup(directory.cleanup)
        self.root = Path(directory.name)
        self.path = self.root / "manifest.json"

    def invoke(self, args):
        output, error = io.StringIO(), io.StringIO()
        with redirect_stdout(output), redirect_stderr(error):
            code = intake.main(args)
        return code, output.getvalue(), error.getvalue()

    def test_real_cli_exits_distinguish_missing_and_submitted_but_never_authorize(self):
        for value, expected_code in ((blank(), 2), (supplied(), 0)):
            raw = encoded(value); self.path.write_bytes(raw)
            result = subprocess.run([sys.executable, str(SCRIPT), str(self.path)], capture_output=True, timeout=10)
            self.assertEqual(result.returncode, expected_code, result.stderr)
            self.assertEqual(result.stderr, b"")
            report = json.loads(result.stdout)
            self.assertFalse(report["providerApproved"] or report["rawCoverageVerified"] or report["ingestionAllowed"] or report["scoringEnabled"])
            self.assertEqual(self.path.read_bytes(), raw)
            self.assertEqual(list(self.root.iterdir()), [self.path])

    def test_invalid_input_is_sanitized_and_does_not_create_report(self):
        self.path.write_text('{"private":"PRIVATE_SENTINEL"}', encoding="utf-8")
        code, output, error = self.invoke([str(self.path)])
        self.assertEqual((code, output, error), (1, "", "raw-feed-intake: INVALID_DOCUMENT\n"))

    def test_path_and_unknown_argument_errors_never_echo_private_values(self):
        for args in ([], ["--token=PRIVATE_SENTINEL"], [str(self.root / "PRIVATE_SENTINEL.json")],
                     [str(self.path), "PRIVATE_SENTINEL"], ["--help", "PRIVATE_SENTINEL"]):
            code, output, error = self.invoke(args)
            self.assertEqual((code, output), (1, ""))
            self.assertNotIn("PRIVATE_SENTINEL", error)

    def test_help_is_available_without_an_input(self):
        code, output, error = self.invoke(["--help"])
        self.assertEqual((code, error), (0, ""))
        self.assertIn("never feed approval", output)

    def test_file_size_directory_and_remote_path_rejected(self):
        for raw in (b"", b" " * (intake.MAX_BYTES + 1)):
            self.path.write_bytes(raw)
            with self.assertRaisesRegex(intake.IntakeError, "INPUT_SIZE_LIMIT"): intake.read_local(self.path)
        with self.assertRaisesRegex(intake.IntakeError, "LOCAL_REGULAR_FILE_REQUIRED"): intake.read_local(self.root)
        for name in ("//remote/share/private.json", "\\\\remote\\share\\private.json"):
            with self.assertRaisesRegex(intake.IntakeError, "LOCAL_REGULAR_FILE_REQUIRED"): intake.read_local(Path(name))

    def test_parent_reparse_or_symlink_is_rejected_without_open(self):
        self.path.write_bytes(encoded(blank()))
        original = Path.lstat
        for mode, attributes in ((stat.S_IFLNK, 0), (stat.S_IFDIR, 0x400)):
            def fake_lstat(path, **kwargs):
                return (SimpleNamespace(st_mode=mode, st_file_attributes=attributes)
                        if path == self.root else original(path, **kwargs))
            with patch.object(Path, "lstat", fake_lstat), patch.object(intake.os, "open") as opened:
                with self.assertRaisesRegex(intake.IntakeError, "LOCAL_REGULAR_FILE_REQUIRED"):
                    intake.read_local(self.path)
                opened.assert_not_called()

    def test_replaced_file_identity_is_rejected(self):
        self.path.write_bytes(encoded(blank()))
        with patch.object(intake.os, "fstat", return_value=SimpleNamespace(st_mode=stat.S_IFREG, st_dev=-1, st_ino=-1)):
            with self.assertRaisesRegex(intake.IntakeError, "LOCAL_REGULAR_FILE_REQUIRED"):
                intake.read_local(self.path)


if __name__ == "__main__":
    unittest.main()
