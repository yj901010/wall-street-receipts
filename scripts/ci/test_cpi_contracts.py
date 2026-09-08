"""Current CPI custody tests; the historical checkout is not CPI validation."""
import hashlib
from pathlib import Path
import tempfile
import unittest
from unittest.mock import Mock, patch
import cpi_contracts as cpi
import run_contracts as bridge
from current_contracts import blob_record

SOURCE = Path(__file__).resolve().parents[2]


class CpiCustodyTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.original = {path: bridge.git(SOURCE, "show", f"{cpi.BASELINE}:{path}") for path in cpi.BASELINE_RECORDS}
        cls.expected = {path: (SOURCE / path).read_bytes().replace(b"\r\n", b"\n") for path in cpi.CPI_PATHS}

    def setUp(self):
        directory = tempfile.TemporaryDirectory(prefix="wsr-cpi-custody-")
        self.addCleanup(directory.cleanup)
        self.root = Path(directory.name)
        for relative, raw in self.expected.items():
            path = self.root / relative
            path.parent.mkdir(parents=True, exist_ok=True)
            path.write_bytes(raw)
        self.baseline = dict(cpi.BASELINE_RECORDS)
        self.current = {path: blob_record(raw) for path, raw in self.expected.items()}
        self.git = Mock(side_effect=lambda root, command, spec: self.original[spec.split(":", 1)[1]])

    def verify(self, current=None, baseline=None):
        return cpi.verify_cpi(self.root, self.git, self.baseline if baseline is None else baseline,
                              self.current if current is None else current)

    def test_closed_inventory_without_general_product_exceptions(self):
        self.assertEqual(len(cpi.CPI_PATHS), 41)
        self.assertEqual(len(cpi.CPI_ADDED_PATHS), 35)
        self.assertFalse(cpi.CPI_PATHS & bridge.FIXED_CI_PATHS)
        self.assertFalse(cpi.CPI_PATHS & bridge.NAVIGATION_PATHS)
        self.assertNotIn(bridge.NEXT_ENV, cpi.CPI_PATHS)
        self.assertEqual(self.verify(self.baseline), self.baseline)
        self.assertEqual(self.verify(), self.current)

    def test_actual_sources_are_pinned_and_required(self):
        cpi.verify_cpi(SOURCE, self.git, self.baseline, self.current)
        for relative, raw in self.expected.items():
            self.assertEqual(hashlib.sha256(raw).hexdigest(), cpi.CONTENT_SHA256[relative])
            path = self.root / relative
            with self.subTest(path=relative):
                path.write_bytes(raw + b"\n// unreviewed change\n")
                with self.assertRaisesRegex(ValueError, "Unreviewed current CPI"):
                    self.verify()
                path.write_bytes(raw)
                with self.assertRaisesRegex(ValueError, "Unreviewed committed CPI"):
                    self.verify({**self.current, relative: "100755 blob " + "0" * 40})

    def test_only_exact_adr064_committed_predecessors_are_accepted(self):
        self.assertEqual(len(cpi.PREVIOUS_RECORDS), 2)
        previous = {**self.current, **cpi.PREVIOUS_RECORDS}
        self.assertEqual(self.verify(previous), previous)
        for relative, record in cpi.PREVIOUS_RECORDS.items():
            with self.subTest(path=relative):
                old = bridge.git(SOURCE, "show", "dc55eda73cacf437cde9a27417326df99f923687:" + relative)
                self.assertEqual(blob_record(old), record)
                with self.assertRaisesRegex(ValueError, "Unreviewed committed CPI"):
                    self.verify({**self.current, relative: "100644 blob " + "f" * 40})
                (self.root / relative).write_bytes(old)
                with self.assertRaisesRegex(ValueError, "Unreviewed current CPI"):
                    self.verify(previous)
                (self.root / relative).write_bytes(self.expected[relative])

    def test_missing_source_and_stale_working_bytes_never_fall_back(self):
        for relative in cpi.CPI_PATHS:
            path = self.root / relative
            path.unlink()
            with self.assertRaisesRegex(ValueError, "missing or linked"):
                self.verify(self.baseline)
            path.write_bytes(self.original.get(relative, b"stale"))
            with self.assertRaisesRegex(ValueError, "Unreviewed current CPI"):
                self.verify(self.baseline)
            path.write_bytes(self.expected[relative])

    def test_baseline_additions_and_mutations_are_rejected(self):
        for relative in cpi.CPI_PATHS:
            with self.assertRaisesRegex(ValueError, "CPI baseline"):
                self.verify(baseline={**self.baseline, relative: "120000 blob " + "1" * 40})

    def test_predecessor_support_does_not_allow_deleted_baseline_paths(self):
        for relative in cpi.BASELINE_RECORDS:
            current = dict(self.current)
            current.pop(relative)
            with self.assertRaisesRegex(ValueError, "Unreviewed committed CPI"):
                self.verify(current)

    def test_crlf_and_custody_snapshot(self):
        for relative, raw in self.expected.items():
            (self.root / relative).write_bytes(raw.replace(b"\n", b"\r\n"))
        self.verify()
        with patch.object(bridge, "git", return_value=b""):
            snapshot = bridge.snapshot(self.root, {"steps": []})
            self.assertTrue(cpi.CPI_PATHS <= snapshot["files"].keys())
            (self.root / next(iter(cpi.CPI_ADDED_PATHS))).write_bytes(b"changed during historical run")
            self.assertNotEqual(snapshot, bridge.snapshot(self.root, {"steps": []}))

    def test_verifier_is_mandatory_before_historical_comparison(self):
        with patch.object(bridge, "git", side_effect=[b"", b""]), \
                patch.object(bridge, "verify_current_test", return_value={}), \
                patch.object(bridge, "verify_navigation", return_value={}), \
                patch.object(bridge, "verify_cpi", side_effect=ValueError("CPI rejected")) as verify:
            with self.assertRaisesRegex(ValueError, "CPI rejected"):
                bridge.validate_product(SOURCE, {"steps": []})
            verify.assert_called_once()

    def test_untracked_exception_requires_verification_and_has_no_prefix_wildcard(self):
        for relative in [*cpi.CPI_ADDED_PATHS, "apps/web/src/lib/cpi-secret.ts"]:
            with patch.object(bridge, "git", side_effect=[b"", b"", b"", relative.encode() + b"\0", b""]), \
                    patch.object(bridge, "verify_current_test", return_value={}), \
                    patch.object(bridge, "verify_navigation", return_value={}), \
                    patch.object(bridge, "verify_cpi", return_value={}) as verify:
                if relative in cpi.CPI_ADDED_PATHS:
                    bridge.validate_product(SOURCE, {"steps": []})
                else:
                    with self.assertRaisesRegex(ValueError, "Unexpected uncommitted"):
                        bridge.validate_product(SOURCE, {"steps": []})
                verify.assert_called_once()


if __name__ == "__main__":
    unittest.main()
